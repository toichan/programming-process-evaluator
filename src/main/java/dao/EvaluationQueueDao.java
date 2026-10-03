package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.google.gson.JsonObject;

public final class EvaluationQueueDao {
	private EvaluationQueueDao() {
	}

	public static boolean enqueueIfConfigured(
			Connection connection,
			long taskId,
			long submissionId,
			long actorUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.rubric_id, t.active_prompt_version_id, r.version AS rubric_version,
				       pv.version AS prompt_version, pv.ai_model
				FROM tasks t
				JOIN rubrics r ON r.rubric_id = t.rubric_id AND r.rubric_status = 'active'
				JOIN prompt_versions pv ON pv.prompt_version_id = t.active_prompt_version_id
				  AND pv.task_id = t.task_id
				  AND pv.prompt_status IN ('configured','versioned')
				  AND pv.fluctuation_generation_status = 'completed'
				  AND pv.evaluation_examples_status = 'completed'
				WHERE t.task_id = ?
				  AND (SELECT COUNT(*) FROM rubric_dimensions d WHERE d.rubric_id = r.rubric_id) = 2
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return false;
				}
				long rubricId = resultSet.getLong("rubric_id");
				long promptVersionId = resultSet.getLong("active_prompt_version_id");
				String rubricVersion = resultSet.getString("rubric_version");
				String promptVersion = resultSet.getString("prompt_version");
				String modelId = resultSet.getString("ai_model");
				long evaluationId = insertEvaluation(
						connection, submissionId, rubricId, promptVersionId, "initial",
						UUID.randomUUID().toString());
				insertRequest(
						connection,
						evaluationId,
						modelId,
						promptVersion,
						rubricVersion,
						actorUserId,
						"submission",
						UUID.randomUUID().toString());
				return true;
			}
		}
	}

	public static boolean retryFailedEvaluation(
			long studentUserId,
			long assignmentId,
			long submissionId) throws SQLException {
		try (Connection connection = lib.mysql.Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement statement = connection.prepareStatement("""
						SELECT s.submission_id, e.rubric_id, e.prompt_version_id, r.version AS rubric_version,
						       pv.version AS prompt_version, pv.ai_model, tp.participation_id, ca.task_id
						FROM submissions s
						JOIN task_participations tp ON tp.participation_id = s.participation_id
						JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
						JOIN evaluations e ON e.evaluation_id = (
						  SELECT latest.evaluation_id
						  FROM evaluations latest
						  WHERE latest.submission_id = s.submission_id
						  ORDER BY latest.created_at DESC, latest.evaluation_id DESC
						  LIMIT 1
						)
						JOIN rubrics r ON r.rubric_id = e.rubric_id
						JOIN prompt_versions pv ON pv.prompt_version_id = e.prompt_version_id
						WHERE s.submission_id = ?
						  AND tp.student_user_id = ?
						  AND ca.task_class_assignment_id = ?
						  AND e.evaluation_status = 'failed'
						FOR UPDATE
						""")) {
					statement.setLong(1, submissionId);
					statement.setLong(2, studentUserId);
					statement.setLong(3, assignmentId);
					try (ResultSet resultSet = statement.executeQuery()) {
						if (!resultSet.next()) {
							connection.commit();
							return false;
						}
						long failedSubmissionId = resultSet.getLong("submission_id");
						long rubricId = resultSet.getLong("rubric_id");
						long promptVersionId = resultSet.getLong("prompt_version_id");
						String rubricVersion = resultSet.getString("rubric_version");
						String promptVersion = resultSet.getString("prompt_version");
						String modelId = resultSet.getString("ai_model");
						long participationId = resultSet.getLong("participation_id");

						long evaluationId = insertEvaluation(
								connection,
								failedSubmissionId,
								rubricId,
								promptVersionId,
								"reevaluation",
								UUID.randomUUID().toString());
						insertRequest(
								connection,
								evaluationId,
								modelId,
								promptVersion,
								rubricVersion,
								studentUserId,
								"retry",
								UUID.randomUUID().toString());
						try (PreparedStatement update = connection.prepareStatement("""
								UPDATE task_participations
								SET evaluation_status = 'not_started'
								WHERE participation_id = ? AND draft_base_submission_id = ?
								""")) {
							update.setLong(1, participationId);
							update.setLong(2, failedSubmissionId);
							update.executeUpdate();
						}
						connection.commit();
						return true;
					}
				}
			} catch (SQLException | RuntimeException e) {
				try {
					connection.rollback();
				} catch (SQLException rollbackError) {
					e.addSuppressed(rollbackError);
				}
				throw e;
			}
		}
	}

	private static long insertEvaluation(
			Connection connection,
			long submissionId,
			long rubricId,
			long promptVersionId,
			String evaluationKind,
			String evaluationCode) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluations (
				  evaluation_code, submission_id, rubric_id, prompt_version_id, evaluation_status,
				  evaluation_kind, format_version, locale, auto_save_count, execution_count, created_at
				)
				VALUES (?, ?, ?, ?, 'not_started', ?, '1.0.0', 'ja-JP', 0, 0, CURRENT_TIMESTAMP(6))
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, evaluationCode);
			statement.setLong(2, submissionId);
			statement.setLong(3, rubricId);
			statement.setLong(4, promptVersionId);
			statement.setString(5, evaluationKind);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The evaluation ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void insertRequest(
			Connection connection,
			long evaluationId,
			String modelId,
			String promptVersion,
			String rubricVersion,
			long actorUserId,
			String trigger,
			String requestId) throws SQLException {
		String consentStatus = latestConsentStatus(connection, actorUserId);
		JsonObject payload = new JsonObject();
		payload.addProperty("request_id", requestId);
		payload.addProperty("evaluation_id", evaluationId);
		payload.addProperty("trigger", trigger);

		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluation_requests (
				  evaluation_id, model_id, prompt_version, rubric_version, actor_role,
				  consent_status_at_request, locale, request_payload, request_status, retry_count, requested_at
				)
				VALUES (?, ?, ?, ?, 'student', ?, 'ja-JP', ?, 'queued', 0, CURRENT_TIMESTAMP(6))
				""")) {
			statement.setLong(1, evaluationId);
			statement.setString(2, modelId);
			statement.setString(3, promptVersion);
			statement.setString(4, rubricVersion);
			statement.setString(5, consentStatus);
			statement.setString(6, payload.toString());
			statement.executeUpdate();
		}

		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id,
				  action_type, result_status, request_id, occurred_at
				)
				VALUES (?, 'student', 'student_evaluation', 'evaluation', ?,
				        ?, 'success', ?, CURRENT_TIMESTAMP(6))
				""")) {
			statement.setLong(1, actorUserId);
			statement.setLong(2, evaluationId);
			statement.setString(3, "retry".equals(trigger) ? "retry_requested" : "evaluation_queued");
			statement.setString(4, requestId);
			statement.executeUpdate();
		}
	}

	private static String latestConsentStatus(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT consent_status
				FROM consent_records
				WHERE user_id = ?
				ORDER BY consent_id DESC
				LIMIT 1
				""")) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getString("consent_status") : "unconfirmed";
			}
		}
	}
}
