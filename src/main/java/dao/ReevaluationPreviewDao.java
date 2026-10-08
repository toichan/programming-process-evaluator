package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import entity.ReevaluationPreview;
import lib.mysql.Client;

public final class ReevaluationPreviewDao implements ReevaluationPreviewWorkRepository {
	public long insertPreview(
			Connection connection,
			String previewCode,
			long taskId,
			long promptVersionId,
			long rubricId,
			long requestedByUserId,
			long taskVersion,
			long promptRowVersion,
			Long currentPromptVersionId,
			int participantCount,
			int targetCount,
			byte[] scopeFingerprint,
			byte[] configurationFingerprint) throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO reevaluation_previews (
				  preview_code, task_id, prompt_version_id, rubric_id, requested_by_user_id,
				  task_version, prompt_row_version, current_prompt_version_id,
				  participant_count, target_count, scope_fingerprint, configuration_fingerprint,
				  preview_status, created_at, expires_at, updated_at, row_version
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'generating',
				        CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 30 MINUTE),
				        CURRENT_TIMESTAMP(6), 1)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, previewCode);
			statement.setLong(2, taskId);
			statement.setLong(3, promptVersionId);
			statement.setLong(4, rubricId);
			statement.setLong(5, requestedByUserId);
			statement.setLong(6, taskVersion);
			statement.setLong(7, promptRowVersion);
			if (currentPromptVersionId == null) {
				statement.setNull(8, java.sql.Types.BIGINT);
			} else {
				statement.setLong(8, currentPromptVersionId);
			}
			statement.setInt(9, participantCount);
			statement.setInt(10, targetCount);
			statement.setBytes(11, scopeFingerprint);
			statement.setBytes(12, configurationFingerprint);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The reevaluation preview ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	public void insertTarget(
			Connection connection,
			long previewId,
			Participant participant,
			String status,
			JsonObject payload,
			byte[] inputFingerprint,
			String requestId) throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO reevaluation_preview_targets (
				  reevaluation_preview_id, participation_id, submission_id, revision_number,
				  target_status, evaluation_payload, input_fingerprint, request_id,
				  retry_count, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
				""")) {
			statement.setLong(1, previewId);
			statement.setLong(2, participant.participationId());
			if (participant.submissionId() == null) {
				statement.setNull(3, java.sql.Types.BIGINT);
				statement.setNull(4, java.sql.Types.INTEGER);
			} else {
				statement.setLong(3, participant.submissionId());
				statement.setInt(4, participant.revisionNumber());
			}
			statement.setString(5, status);
			if (payload == null) {
				statement.setNull(6, java.sql.Types.VARCHAR);
				statement.setNull(7, java.sql.Types.BINARY);
				statement.setNull(8, java.sql.Types.CHAR);
			} else {
				statement.setString(6, payload.toString());
				statement.setBytes(7, inputFingerprint);
				statement.setString(8, requestId);
			}
			statement.executeUpdate();
		}
	}

	public ReevaluationPreview findPreview(Connection connection, String previewCode, long teacherUserId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT reevaluation_preview_id, preview_code, task_id, prompt_version_id, preview_status,
				       participant_count, target_count, expires_at, row_version,
				       (expires_at <= CURRENT_TIMESTAMP(6)) AS is_expired
				FROM reevaluation_previews
				WHERE preview_code = ? AND requested_by_user_id = ?
				""")) {
			statement.setString(1, previewCode);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherPromptDao.TeacherTaskNotFoundException();
				}
				String status = rows.getBoolean("is_expired") ? "expired" : rows.getString("preview_status");
				return new ReevaluationPreview(
						rows.getLong("reevaluation_preview_id"),
						rows.getString("preview_code"),
						rows.getLong("task_id"),
						rows.getLong("prompt_version_id"),
						status,
						rows.getInt("participant_count"),
						rows.getInt("target_count"),
						rows.getTimestamp("expires_at").toLocalDateTime(),
						rows.getLong("row_version"),
						findTargets(connection, rows.getLong("reevaluation_preview_id")));
			}
		}
	}

	public PreviewSnapshot lockPreview(Connection connection, String previewCode, long teacherUserId)
			throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT reevaluation_preview_id, task_id, prompt_version_id, rubric_id,
				       requested_by_user_id, task_version, prompt_row_version,
				       current_prompt_version_id, participant_count, target_count,
				       scope_fingerprint, configuration_fingerprint, preview_status,
				       expires_at, row_version
				FROM reevaluation_previews
				WHERE preview_code = ? AND requested_by_user_id = ?
				FOR UPDATE
				""")) {
			statement.setString(1, previewCode);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherPromptDao.TeacherTaskNotFoundException();
				}
				long currentPromptId = rows.getLong("current_prompt_version_id");
				Long currentPromptVersionId = rows.wasNull() ? null : currentPromptId;
				return new PreviewSnapshot(
						rows.getLong("reevaluation_preview_id"),
						rows.getLong("task_id"),
						rows.getLong("prompt_version_id"),
						rows.getLong("rubric_id"),
						rows.getLong("requested_by_user_id"),
						rows.getLong("task_version"),
						rows.getLong("prompt_row_version"),
						currentPromptVersionId,
						rows.getInt("participant_count"),
						rows.getInt("target_count"),
						rows.getBytes("scope_fingerprint"),
						rows.getBytes("configuration_fingerprint"),
						rows.getString("preview_status"),
						rows.getTimestamp("expires_at").toLocalDateTime(),
						rows.getLong("row_version"),
						findTargetSnapshots(connection, rows.getLong("reevaluation_preview_id")));
			}
		}
	}

	public boolean markStale(Connection connection, long previewId, long rowVersion) throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE reevaluation_previews
				SET preview_status = 'stale', row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_preview_id = ? AND row_version = ?
				  AND preview_status IN ('generating','retryable','ready')
				""")) {
			statement.setLong(1, previewId);
			statement.setLong(2, rowVersion);
			return statement.executeUpdate() == 1;
		}
	}

	public void deleteTargets(Connection connection, long previewId) throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement(
				"DELETE FROM reevaluation_preview_targets WHERE reevaluation_preview_id = ?")) {
			statement.setLong(1, previewId);
			statement.executeUpdate();
		}
	}

	public void markConfirmed(Connection connection, long previewId) throws SQLException {
		updateTerminalStatus(connection, previewId, "confirmed");
	}

	public void cancel(Connection connection, long previewId, long rowVersion) throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE reevaluation_previews
				SET preview_status = 'cancelled', row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_preview_id = ? AND row_version = ?
				  AND preview_status IN ('generating','retryable','ready')
				  AND expires_at > CURRENT_TIMESTAMP(6)
				""")) {
			statement.setLong(1, previewId);
			statement.setLong(2, rowVersion);
			if (statement.executeUpdate() != 1) {
				throw new IllegalStateException("このプレビューは取消できません。再読み込みしてください。");
			}
		}
		deleteTargets(connection, previewId);
	}

	public void retryFailed(Connection connection, long previewId, long rowVersion) throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement targets = connection.prepareStatement("""
				UPDATE reevaluation_preview_targets
				SET target_status = 'pending', safe_error_code = NULL, safe_error_message = NULL,
				    request_id = UUID(), updated_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_preview_id = ? AND target_status = 'failed'
				""")) {
			targets.setLong(1, previewId);
			if (targets.executeUpdate() == 0) {
				throw new IllegalStateException("再試行する失敗対象がありません。");
			}
		}
		try (PreparedStatement parent = connection.prepareStatement("""
				UPDATE reevaluation_previews
				SET preview_status = 'generating', row_version = row_version + 1,
				    updated_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_preview_id = ? AND row_version = ?
				  AND preview_status = 'retryable' AND expires_at > CURRENT_TIMESTAMP(6)
				""")) {
			parent.setLong(1, previewId);
			parent.setLong(2, rowVersion);
			if (parent.executeUpdate() != 1) {
				throw new IllegalStateException("このプレビューは再試行できません。再読み込みしてください。");
			}
		}
	}

	public Optional<PreviewWorkItem> claimNextTarget() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement recover = connection.prepareStatement("""
						UPDATE reevaluation_preview_targets pt
						JOIN reevaluation_previews p
						  ON p.reevaluation_preview_id = pt.reevaluation_preview_id
						SET pt.target_status = 'failed',
						    pt.safe_error_code = 'worker_interrupted',
						    pt.safe_error_message = '予測処理が中断されました。失敗した対象を再試行してください。',
						    pt.updated_at = CURRENT_TIMESTAMP(6)
						WHERE pt.target_status = 'in_progress'
						  AND pt.updated_at < DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL %d MINUTE)
						  AND p.expires_at > CURRENT_TIMESTAMP(6)
						""".formatted(control.evaluation.EvaluationRetryPolicy.INTERRUPTED_AFTER.toMinutes()))) {
					recover.executeUpdate();
				}
				try (PreparedStatement recoverParents = connection.prepareStatement("""
						SELECT p.reevaluation_preview_id
						FROM reevaluation_previews p
						WHERE p.preview_status = 'generating' AND p.expires_at > CURRENT_TIMESTAMP(6)
						  AND EXISTS (SELECT 1 FROM reevaluation_preview_targets pt
						    WHERE pt.reevaluation_preview_id = p.reevaluation_preview_id
						      AND pt.target_status = 'failed' AND pt.safe_error_code = 'worker_interrupted')
						FOR UPDATE
						"""); ResultSet parents = recoverParents.executeQuery()) {
					while (parents.next()) {
						updatePreviewStatus(connection, parents.getLong("reevaluation_preview_id"));
					}
				}
				try (PreparedStatement select = connection.prepareStatement("""
						SELECT pt.reevaluation_preview_target_id, pt.reevaluation_preview_id,
						       p.preview_code, p.prompt_version_id, pv.ai_model, pt.participation_id,
						       pt.submission_id, tp.student_user_id, ca.task_id,
						       t.rubric_id, r.version AS rubric_version, pv.version AS prompt_version,
						       pt.evaluation_payload, pt.request_id, pt.retry_count
						FROM reevaluation_preview_targets pt
						JOIN reevaluation_previews p
						  ON p.reevaluation_preview_id = pt.reevaluation_preview_id
						JOIN prompt_versions pv ON pv.prompt_version_id = p.prompt_version_id
						JOIN tasks t ON t.task_id = p.task_id
						JOIN rubrics r ON r.rubric_id = p.rubric_id
						JOIN task_participations tp ON tp.participation_id = pt.participation_id
						JOIN task_class_assignments ca
						  ON ca.task_class_assignment_id = tp.task_class_assignment_id
						WHERE p.preview_status IN ('generating','retryable')
						  AND p.expires_at > CURRENT_TIMESTAMP(6)
						  AND pt.target_status = 'pending'
						ORDER BY p.created_at, pt.reevaluation_preview_target_id
						LIMIT 1 FOR UPDATE SKIP LOCKED
						""")) {
					try (ResultSet rows = select.executeQuery()) {
						if (!rows.next()) {
							connection.commit();
							return Optional.empty();
						}
						long targetId = rows.getLong("reevaluation_preview_target_id");
						String requestId = rows.getString("request_id");
						if (requestId == null) {
							throw new SQLException("A pending reevaluation target is missing its request ID.");
						}
						try (PreparedStatement update = connection.prepareStatement("""
								UPDATE reevaluation_preview_targets
								SET target_status = 'in_progress', updated_at = CURRENT_TIMESTAMP(6)
								WHERE reevaluation_preview_target_id = ? AND target_status = 'pending'
								""")) {
							update.setLong(1, targetId);
							if (update.executeUpdate() != 1) {
								throw new SQLException("The preview target could not be claimed.");
							}
						}
						try (PreparedStatement update = connection.prepareStatement("""
								UPDATE reevaluation_previews
								SET preview_status = 'generating', updated_at = CURRENT_TIMESTAMP(6)
								WHERE reevaluation_preview_id = ?
								""")) {
							update.setLong(1, rows.getLong("reevaluation_preview_id"));
							update.executeUpdate();
						}
						PreviewWorkItem item = new PreviewWorkItem(
								targetId,
								rows.getLong("reevaluation_preview_id"),
								rows.getString("preview_code"),
								rows.getLong("prompt_version_id"),
								rows.getString("ai_model"),
								rows.getLong("participation_id"),
								rows.getLong("submission_id"),
								rows.getLong("student_user_id"),
								rows.getLong("task_id"),
								rows.getLong("rubric_id"),
								rows.getString("rubric_version"),
								rows.getString("prompt_version"),
								JsonParser.parseString(rows.getString("evaluation_payload")).getAsJsonObject(),
								requestId,
								rows.getInt("retry_count"));
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

	public void markSucceeded(
			long targetId, long previewId, int attempts, JsonObject rawResponse, JsonObject result)
			throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE reevaluation_preview_targets
						SET target_status = 'succeeded', provider_response = ?, validated_result = ?,
						    retry_count = retry_count + ?, safe_error_code = NULL, safe_error_message = NULL,
						    updated_at = CURRENT_TIMESTAMP(6)
						WHERE reevaluation_preview_target_id = ? AND target_status = 'in_progress'
						""")) {
					update.setString(1, rawResponse.toString());
					update.setString(2, result.toString());
					update.setInt(3, attempts);
					update.setLong(4, targetId);
					if (update.executeUpdate() != 1) {
						throw new SQLException("The preview result could not be saved.");
					}
				}
				updatePreviewStatus(connection, previewId);
				connection.commit();
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public void markFailed(long targetId, long previewId, int attempts, String safeCode, String safeMessage)
			throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE reevaluation_preview_targets
						SET target_status = 'failed', retry_count = retry_count + ?,
						    safe_error_code = ?, safe_error_message = ?, updated_at = CURRENT_TIMESTAMP(6)
						WHERE reevaluation_preview_target_id = ? AND target_status = 'in_progress'
						""")) {
					update.setInt(1, attempts);
					update.setString(2, safeCode);
					update.setString(3, safeMessage);
					update.setLong(4, targetId);
					if (update.executeUpdate() != 1) {
						throw new SQLException("The preview failure could not be saved.");
					}
				}
				updatePreviewStatus(connection, previewId);
				connection.commit();
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public int cleanupExpired() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement expire = connection.prepareStatement("""
						UPDATE reevaluation_previews
						SET preview_status = 'expired', row_version = row_version + 1,
						    updated_at = CURRENT_TIMESTAMP(6)
						WHERE expires_at <= CURRENT_TIMESTAMP(6)
						  AND preview_status IN ('generating','retryable','ready')
						""")) {
					expire.executeUpdate();
				}
				int deleted;
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM reevaluation_previews
						WHERE expires_at <= CURRENT_TIMESTAMP(6)
						   OR preview_status IN ('confirmed','cancelled','stale','expired')
						""")) {
					deleted = statement.executeUpdate();
				}
				connection.commit();
				return deleted;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private List<ReevaluationPreview.Target> findTargets(Connection connection, long previewId) throws SQLException {
		List<ReevaluationPreview.Target> targets = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT pt.participation_id, u.display_name, pt.revision_number, pt.target_status,
				       pt.validated_result, pt.safe_error_message,
				       (SELECT ROUND(edr.score_value)
				        FROM evaluation_dimension_results edr
				        JOIN rubric_dimensions d ON d.dimension_id = edr.dimension_id
				        WHERE edr.evaluation_id = (
				          SELECT e.evaluation_id FROM evaluations e
				          WHERE e.submission_id = pt.submission_id
				            AND e.evaluation_status = 'completed'
				          ORDER BY e.created_at DESC, e.evaluation_id DESC LIMIT 1
				        )
				          AND (LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%thinking%'
				            OR LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%思考%'
				            OR LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%表現%'
				            OR LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%判断%')
				        LIMIT 1) AS previous_thinking_score,
				       (SELECT ROUND(edr.score_value)
				        FROM evaluation_dimension_results edr
				        JOIN rubric_dimensions d ON d.dimension_id = edr.dimension_id
				        WHERE edr.evaluation_id = (
				          SELECT e.evaluation_id FROM evaluations e
				          WHERE e.submission_id = pt.submission_id
				            AND e.evaluation_status = 'completed'
				          ORDER BY e.created_at DESC, e.evaluation_id DESC LIMIT 1
				        )
				          AND (LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%attitude%'
				            OR LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%proactive%'
				            OR LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%主体%'
				            OR LOWER(CONCAT(d.dimension_code, ' ', d.label)) LIKE '%態度%')
				        LIMIT 1) AS previous_attitude_score
				FROM reevaluation_preview_targets pt
				JOIN task_participations tp ON tp.participation_id = pt.participation_id
				JOIN users u ON u.user_id = tp.student_user_id
				WHERE pt.reevaluation_preview_id = ?
				ORDER BY u.display_name, pt.participation_id
				""")) {
			statement.setLong(1, previewId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					JsonObject result = parseObject(rows.getString("validated_result"));
					targets.add(new ReevaluationPreview.Target(
							rows.getLong("participation_id"),
							rows.getString("display_name"),
							nullableInteger(rows, "revision_number"),
							rows.getString("target_status"),
							integerAt(result, "scores", "thinking_expression_level"),
							integerAt(result, "scores", "proactive_attitude_level"),
							nullableInteger(rows, "previous_thinking_score"),
							nullableInteger(rows, "previous_attitude_score"),
							stringAt(result, "reasons", "thinking_expression_reason"),
							stringAt(result, "reasons", "proactive_attitude_reason"),
							rows.getString("safe_error_message")));
				}
			}
		}
		return List.copyOf(targets);
	}

	private List<TargetSnapshot> findTargetSnapshots(Connection connection, long previewId) throws SQLException {
		List<TargetSnapshot> targets = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT participation_id, submission_id, revision_number, target_status,
				       evaluation_payload, input_fingerprint, provider_response, validated_result,
				       request_id, retry_count
				FROM reevaluation_preview_targets
				WHERE reevaluation_preview_id = ?
				ORDER BY participation_id
				""")) {
			statement.setLong(1, previewId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					long submissionId = rows.getLong("submission_id");
					Long nullableSubmissionId = rows.wasNull() ? null : submissionId;
					int revision = rows.getInt("revision_number");
					Integer nullableRevision = rows.wasNull() ? null : revision;
					targets.add(new TargetSnapshot(
							rows.getLong("participation_id"),
							nullableSubmissionId,
							nullableRevision,
							rows.getString("target_status"),
							parseObject(rows.getString("evaluation_payload")),
							rows.getBytes("input_fingerprint"),
							parseObject(rows.getString("provider_response")),
							parseObject(rows.getString("validated_result")),
							rows.getString("request_id"),
							rows.getInt("retry_count")));
				}
			}
		}
		return List.copyOf(targets);
	}

	private static void updatePreviewStatus(Connection connection, long previewId) throws SQLException {
		String status;
		try (PreparedStatement counts = connection.prepareStatement("""
				SELECT
				  SUM(target_status = 'succeeded') AS succeeded_count,
				  SUM(target_status = 'failed') AS failed_count,
				  SUM(target_status IN ('pending','in_progress')) AS remaining_count
				FROM reevaluation_preview_targets
				WHERE reevaluation_preview_id = ?
				""")) {
			counts.setLong(1, previewId);
			try (ResultSet rows = counts.executeQuery()) {
				rows.next();
				if (rows.getInt("remaining_count") > 0) {
					status = "generating";
				} else if (rows.getInt("failed_count") > 0) {
					status = "retryable";
				} else {
					status = "ready";
				}
			}
		}
		try (PreparedStatement update = connection.prepareStatement("""
				UPDATE reevaluation_previews
				SET preview_status = ?, row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_preview_id = ? AND expires_at > CURRENT_TIMESTAMP(6)
				  AND preview_status IN ('generating','retryable')
				""")) {
			update.setString(1, status);
			update.setLong(2, previewId);
			update.executeUpdate();
		}
	}

	private static void updateTerminalStatus(Connection connection, long previewId, String status)
			throws SQLException {
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE reevaluation_previews
				SET preview_status = ?, row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP(6)
				WHERE reevaluation_preview_id = ?
				""")) {
			statement.setString(1, status);
			statement.setLong(2, previewId);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("The preview state could not be updated.");
			}
		}
	}

	private static JsonObject parseObject(String json) throws SQLException {
		if (json == null) {
			return null;
		}
		try {
			return JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException failure) {
			throw new SQLException("A stored reevaluation preview value is invalid.", failure);
		}
	}

	private static Integer nullableInteger(ResultSet rows, String field) throws SQLException {
		int value = rows.getInt(field);
		return rows.wasNull() ? null : value;
	}

	private static Integer integerAt(JsonObject root, String object, String field) {
		return root == null || !root.has(object) || !root.getAsJsonObject(object).has(field)
				? null : root.getAsJsonObject(object).get(field).getAsInt();
	}

	private static String stringAt(JsonObject root, String object, String field) {
		return root == null || !root.has(object) || !root.getAsJsonObject(object).has(field)
				? null : root.getAsJsonObject(object).get(field).getAsString();
	}

	private static void requireTransaction(Connection connection) throws SQLException {
		if (connection == null || connection.getAutoCommit()) {
			throw new IllegalStateException("A database transaction is required.");
		}
	}

	private static void rollback(Connection connection, Exception failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
	}

	public record Participant(
			long participationId,
			long studentUserId,
			String displayName,
			Long submissionId,
			Integer revisionNumber,
			String submissionStatus,
			String consentStatus) {
	}

	public record TargetSnapshot(
			long participationId,
			Long submissionId,
			Integer revisionNumber,
			String status,
			JsonObject payload,
			byte[] inputFingerprint,
			JsonObject providerResponse,
			JsonObject validatedResult,
			String requestId,
			int retryCount) {
	}

	public record PreviewSnapshot(
			long previewId,
			long taskId,
			long promptVersionId,
			long rubricId,
			long requestedByUserId,
			long taskVersion,
			long promptRowVersion,
			Long currentPromptVersionId,
			int participantCount,
			int targetCount,
			byte[] scopeFingerprint,
			byte[] configurationFingerprint,
			String status,
			LocalDateTime expiresAt,
			long rowVersion,
			List<TargetSnapshot> targets) {
	}

	public record PreviewWorkItem(
			long targetId,
			long previewId,
			String previewCode,
			long promptVersionId,
			String modelId,
			long participationId,
			long submissionId,
			long studentUserId,
			long taskId,
			long rubricId,
			String rubricVersion,
			String promptVersion,
			JsonObject payload,
			String requestId,
			int retryCount) {
	}
}
