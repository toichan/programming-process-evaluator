package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import entity.ReevaluationJobStatus;
import entity.ReevaluationJobTargetResult;
import dao.TeacherPromptDao.TeacherTaskNotFoundException;
import lib.mysql.Client;

public final class ReevaluationJobDao {
	public ReevaluationJobStatus loadStatus(
			Connection connection, long jobId, long taskId, long teacherUserId) throws SQLException {
		long promptVersionId;
		String status;
		int targetCount;
		int completedCount;
		int failedCount;
		java.math.BigDecimal progressPercent;
		java.time.LocalDateTime startedAt;
		java.time.LocalDateTime completedAt;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT j.reevaluation_job_id, j.task_id, j.prompt_version_id, j.reevaluation_status,
				       j.target_count, j.completed_count, j.progress_percent,
				       COALESCE(
				         (SELECT COUNT(*) FROM reevaluation_job_targets jt
				          WHERE jt.reevaluation_job_id = j.reevaluation_job_id
				            AND jt.target_status = 'failed'),
				         0) AS failed_count,
				       j.started_at, j.completed_at
				FROM reevaluation_jobs j
				JOIN tasks t ON t.task_id = j.task_id
				WHERE j.reevaluation_job_id = ? AND j.task_id = ?
				  AND t.created_by_user_id = ? AND t.deleted_at IS NULL
				""")) {
			statement.setLong(1, jobId);
			statement.setLong(2, taskId);
			statement.setLong(3, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherTaskNotFoundException();
				}
				java.sql.Timestamp started = rows.getTimestamp("started_at");
				java.sql.Timestamp completed = rows.getTimestamp("completed_at");
				promptVersionId = rows.getLong("prompt_version_id");
				status = rows.getString("reevaluation_status");
				targetCount = rows.getInt("target_count");
				completedCount = rows.getInt("completed_count");
				failedCount = rows.getInt("failed_count");
				progressPercent = rows.getBigDecimal("progress_percent");
				startedAt = started == null ? null : started.toLocalDateTime();
				completedAt = completed == null ? null : completed.toLocalDateTime();
			}
		}
		return new ReevaluationJobStatus(
				jobId, taskId, promptVersionId, status, targetCount, completedCount, failedCount,
				progressPercent, startedAt, completedAt, loadTargetResults(connection, jobId, taskId, teacherUserId));
	}

	private static List<ReevaluationJobTargetResult> loadTargetResults(
			Connection connection, long jobId, long taskId, long teacherUserId) throws SQLException {
		List<ReevaluationJobTargetResult> results = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT u.login_id AS student_login_id,
				       CONCAT_WS(' ', NULLIF(c.grade_name, ''), c.name) AS classroom_label,
				       s.revision_number, jt.target_status, e.overall_score,
				       scores.thinking_score, scores.attitude_score, jt.safe_error_message
				FROM reevaluation_job_targets jt
				JOIN reevaluation_jobs j ON j.reevaluation_job_id = jt.reevaluation_job_id
				JOIN tasks t ON t.task_id = j.task_id
				JOIN task_participations tp ON tp.participation_id = jt.participation_id
				JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
				JOIN classrooms c ON c.classroom_id = ca.classroom_id
				JOIN users u ON u.user_id = tp.student_user_id
				JOIN submissions s ON s.submission_id = jt.submission_id
				LEFT JOIN evaluations e ON e.evaluation_id = jt.evaluation_id
				  AND e.reevaluation_job_id = j.reevaluation_job_id
				LEFT JOIN (
				  SELECT dr.evaluation_id,
				         MAX(CASE WHEN d.dimension_code = 'thinking' THEN dr.score_value END) AS thinking_score,
				         MAX(CASE WHEN d.dimension_code = 'attitude' THEN dr.score_value END) AS attitude_score
				  FROM evaluation_dimension_results dr
				  JOIN rubric_dimensions d ON d.dimension_id = dr.dimension_id
				  GROUP BY dr.evaluation_id
				) scores ON scores.evaluation_id = e.evaluation_id
				WHERE jt.reevaluation_job_id = ? AND j.task_id = ?
				  AND t.created_by_user_id = ? AND t.deleted_at IS NULL
				ORDER BY c.grade_name, c.name, u.login_id, jt.reevaluation_job_target_id
				""")) {
			statement.setLong(1, jobId);
			statement.setLong(2, taskId);
			statement.setLong(3, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					results.add(new ReevaluationJobTargetResult(
							rows.getString("student_login_id"),
							rows.getString("classroom_label"),
							rows.getInt("revision_number"),
							rows.getString("target_status"),
							rows.getBigDecimal("overall_score"),
							rows.getBigDecimal("thinking_score"),
							rows.getBigDecimal("attitude_score"),
							rows.getString("safe_error_message")));
				}
			}
		}
		return List.copyOf(results);
	}

	public Optional<JobWorkItem> claimNextTarget() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement recover = connection.prepareStatement("""
						UPDATE reevaluation_job_targets
						SET target_status = 'queued', updated_at = CURRENT_TIMESTAMP(6)
						WHERE target_status = 'in_progress'
						  AND updated_at < DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 5 MINUTE)
						""")) {
					recover.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						SELECT jt.reevaluation_job_target_id, jt.reevaluation_job_id,
						       jt.participation_id, jt.submission_id, jt.evaluation_payload,
						       jt.provider_response, jt.validated_result, jt.materialization_attempts,
						       j.task_id, j.prompt_version_id, j.requested_by_user_id,
						       p.ai_model, p.version AS prompt_version,
						       r.version AS rubric_version, t.rubric_id,
						       tp.student_user_id
						FROM reevaluation_job_targets jt
						JOIN reevaluation_jobs j ON j.reevaluation_job_id = jt.reevaluation_job_id
						JOIN tasks t ON t.task_id = j.task_id
						JOIN rubrics r ON r.rubric_id = t.rubric_id
						JOIN prompt_versions p ON p.prompt_version_id = j.prompt_version_id
						JOIN task_participations tp ON tp.participation_id = jt.participation_id
						WHERE jt.target_status = 'queued'
						  AND j.reevaluation_status IN ('queued','in_progress')
						ORDER BY j.reevaluation_job_id, jt.reevaluation_job_target_id
						LIMIT 1 FOR UPDATE SKIP LOCKED
						""")) {
					try (ResultSet rows = statement.executeQuery()) {
						if (!rows.next()) {
							connection.commit();
							return Optional.empty();
						}
						long targetId = rows.getLong("reevaluation_job_target_id");
						long jobId = rows.getLong("reevaluation_job_id");
						try (PreparedStatement update = connection.prepareStatement("""
								UPDATE reevaluation_job_targets
								SET target_status = 'in_progress', materialization_attempts =
								    materialization_attempts + 1, updated_at = CURRENT_TIMESTAMP(6)
								WHERE reevaluation_job_target_id = ? AND target_status = 'queued'
								""")) {
							update.setLong(1, targetId);
							if (update.executeUpdate() != 1) {
								throw new SQLException("The reevaluation target could not be claimed.");
							}
						}
						try (PreparedStatement update = connection.prepareStatement("""
								UPDATE reevaluation_jobs
								SET reevaluation_status = 'in_progress',
								    started_at = COALESCE(started_at, CURRENT_TIMESTAMP)
								WHERE reevaluation_job_id = ? AND reevaluation_status = 'queued'
								""")) {
							update.setLong(1, jobId);
							update.executeUpdate();
						}
						JobWorkItem item = new JobWorkItem(
								targetId,
								jobId,
								rows.getLong("task_id"),
								rows.getLong("prompt_version_id"),
								rows.getLong("requested_by_user_id"),
								rows.getLong("participation_id"),
								rows.getLong("submission_id"),
								rows.getLong("student_user_id"),
								rows.getLong("rubric_id"),
								rows.getString("ai_model"),
								rows.getString("prompt_version"),
								rows.getString("rubric_version"),
								parseObject(rows.getString("evaluation_payload")),
								parseObject(rows.getString("provider_response")),
								parseObject(rows.getString("validated_result")),
								rows.getInt("materialization_attempts") + 1);
						connection.commit();
						return Optional.of(item);
					}
				}
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public void releaseForRetry(long targetId, String safeCode, String safeMessage) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE reevaluation_job_targets
						SET target_status = 'queued', safe_error_code = ?, safe_error_message = ?,
						    updated_at = CURRENT_TIMESTAMP(6)
						WHERE reevaluation_job_target_id = ? AND target_status = 'in_progress'
						""")) {
			statement.setString(1, safeCode);
			statement.setString(2, safeMessage);
			statement.setLong(3, targetId);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("The reevaluation target could not be returned to the queue.");
			}
		}
	}

	public void markFailed(long targetId, long jobId, String safeCode, String safeMessage) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement update = connection.prepareStatement("""
					UPDATE reevaluation_job_targets
					SET target_status = 'failed', safe_error_code = ?, safe_error_message = ?,
					    updated_at = CURRENT_TIMESTAMP(6), completed_at = CURRENT_TIMESTAMP(6)
					WHERE reevaluation_job_target_id = ? AND target_status = 'in_progress'
					""")) {
				update.setString(1, safeCode);
				update.setString(2, safeMessage);
				update.setLong(3, targetId);
				if (update.executeUpdate() != 1) {
					throw new SQLException("The reevaluation target failure could not be recorded.");
				}
				updateJobProgress(connection, jobId);
				connection.commit();
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	static void markCompleted(Connection connection, long targetId, long jobId, long evaluationId)
			throws SQLException {
		try (PreparedStatement update = connection.prepareStatement("""
				UPDATE reevaluation_job_targets
				SET target_status = 'completed', evaluation_id = ?,
				    evaluation_payload = NULL, provider_response = NULL, validated_result = NULL,
				    safe_error_code = NULL, safe_error_message = NULL,
				    updated_at = CURRENT_TIMESTAMP(6), completed_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_job_target_id = ? AND target_status = 'in_progress'
				""")) {
			update.setLong(1, evaluationId);
			update.setLong(2, targetId);
			if (update.executeUpdate() != 1) {
				throw new SQLException("The materialized reevaluation target could not be completed.");
			}
		}
		updateJobProgress(connection, jobId);
	}

	private static void updateJobProgress(Connection connection, long jobId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE reevaluation_jobs j
				SET completed_count = (
				      SELECT COUNT(*) FROM reevaluation_job_targets
				      WHERE reevaluation_job_id = j.reevaluation_job_id AND target_status = 'completed'
				    ),
				    progress_percent = (
				      SELECT ROUND(100.0 * COUNT(*) / NULLIF(j.target_count, 0), 2)
				      FROM reevaluation_job_targets
				      WHERE reevaluation_job_id = j.reevaluation_job_id
				        AND target_status IN ('completed','failed')
				    ),
				    result_summary = JSON_OBJECT(
				      'failed_count', (
				        SELECT COUNT(*) FROM reevaluation_job_targets
				        WHERE reevaluation_job_id = j.reevaluation_job_id AND target_status = 'failed'
				      )
				    ),
				    reevaluation_status = CASE
				      WHEN EXISTS (SELECT 1 FROM reevaluation_job_targets
				                   WHERE reevaluation_job_id = j.reevaluation_job_id
				                     AND target_status IN ('queued','in_progress'))
				        THEN 'in_progress'
				      WHEN EXISTS (SELECT 1 FROM reevaluation_job_targets
				                   WHERE reevaluation_job_id = j.reevaluation_job_id
				                     AND target_status = 'failed')
				        THEN 'failed'
				      ELSE 'completed'
				    END,
				    completed_at = CASE
				      WHEN EXISTS (SELECT 1 FROM reevaluation_job_targets
				                   WHERE reevaluation_job_id = j.reevaluation_job_id
				                     AND target_status IN ('queued','in_progress'))
				        THEN NULL ELSE CURRENT_TIMESTAMP END
				WHERE j.reevaluation_job_id = ?
				""")) {
			statement.setLong(1, jobId);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("The reevaluation job progress could not be updated.");
			}
		}
	}

	private static JsonObject parseObject(String json) throws SQLException {
		try {
			return JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException failure) {
			throw new SQLException("A stored reevaluation job target is invalid.", failure);
		}
	}

	private static void rollback(Connection connection, Exception failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
	}

	public record JobWorkItem(
			long targetId,
			long jobId,
			long taskId,
			long promptVersionId,
			long requestedByUserId,
			long participationId,
			long submissionId,
			long studentUserId,
			long rubricId,
			String modelId,
			String promptVersion,
			String rubricVersion,
			JsonObject payload,
			JsonObject providerResponse,
			JsonObject validatedResult,
			int attempts) {
	}
}
