package control.teacher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.JsonObject;

import control.auth.AuthenticatedUser;
import control.evaluation.EvaluationInputBuilder;
import dao.ReevaluationPreviewDao;
import dao.ReevaluationPreviewDao.Participant;
import dao.ReevaluationPreviewDao.PreviewSnapshot;
import dao.ReevaluationPreviewDao.TargetSnapshot;
import dao.ReevaluationJobDao;
import dao.TeacherPermissionDao;
import dao.TeacherPromptDao;
import entity.ReevaluationPreview;
import entity.ReevaluationJobStatus;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class ReevaluationPreviewControl {
	private final TeacherPermissionDao permissionDao;
	private final ReevaluationPreviewDao previewDao;
	private final ReevaluationJobDao jobDao;
	private final ConnectionFactory connectionFactory;

	public ReevaluationPreviewControl() {
		this(new TeacherPermissionDao(), new ReevaluationPreviewDao(), Client::createConnection);
	}

	ReevaluationPreviewControl(
			TeacherPermissionDao permissionDao,
			ReevaluationPreviewDao previewDao,
			ConnectionFactory connectionFactory) {
		this.permissionDao = Objects.requireNonNull(permissionDao);
		this.previewDao = Objects.requireNonNull(previewDao);
		this.jobDao = new ReevaluationJobDao();
		this.connectionFactory = Objects.requireNonNull(connectionFactory);
	}

	public String startPreview(
			AuthenticatedUser user, long taskId, long promptVersionId, long expectedPromptRowVersion)
			throws SQLException {
		requireTeacher(user);
		requirePositive(taskId);
		requirePositive(promptVersionId);
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				permissionDao.requirePromptDesignAccess(connection, user.userId());
				requireTaskAccess(connection, user.userId(), taskId);
				PreviewConfiguration configuration = loadConfiguration(connection, taskId, promptVersionId, true);
				if (configuration.promptRowVersion() != expectedPromptRowVersion) {
					throw new TeacherPromptDao.PromptVersionConflictException();
				}
				lockParticipants(connection, taskId);
				List<Participant> participants = findParticipants(connection, taskId);
				List<Participant> ordered = participants.stream()
						.sorted(Comparator.comparingLong(Participant::participationId)).toList();
				int targetCount = (int) ordered.stream().filter(ReevaluationPreviewControl::isEligible).count();
				if (targetCount == 0) {
					connection.commit();
					throw new NoReevaluationTargetsException();
				}
				byte[] scopeFingerprint = scopeFingerprint(ordered);
				byte[] configurationFingerprint = configurationFingerprint(configuration);
				String previewCode = UUID.randomUUID().toString();
				long previewId = previewDao.insertPreview(
						connection, previewCode, taskId, promptVersionId, configuration.rubricId(),
						user.userId(), configuration.taskVersion(), configuration.promptRowVersion(),
						configuration.currentPromptVersionId(), ordered.size(), targetCount,
						scopeFingerprint, configurationFingerprint);
				insertPreviewAudit(connection, user.userId(), taskId, promptVersionId,
						"start_reevaluation_preview", previewCode, targetCount);
				for (Participant participant : ordered) {
					if (participant.submissionId() == null) {
						previewDao.insertTarget(connection, previewId, participant,
								"excluded_no_submission", null, null, null);
					} else if ("submitted".equals(participant.submissionStatus())) {
						previewDao.insertTarget(connection, previewId, participant,
								"excluded_submission_processing", null, null, null);
					} else if (isEligible(participant)) {
						String requestId = UUID.randomUUID().toString();
						var payload = EvaluationInputBuilder.build(
								connection,
								taskId,
								participant.submissionId(),
								participant.studentUserId(),
								participant.participationId(),
								configuration.rubricId(),
								promptVersionId,
								configuration.modelId(),
								configuration.promptVersion(),
								configuration.rubricVersion(),
								participant.consentStatus(),
								requestId,
								"teacher");
						previewDao.insertTarget(connection, previewId, participant, "pending",
								payload.payload(), inputFingerprint(payload.payload()), requestId);
					} else {
						throw new SQLException("The latest submission has an unsupported state.");
					}
				}
				connection.commit();
				return previewCode;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public ReevaluationPreview loadPreview(AuthenticatedUser user, String previewCode) throws SQLException {
		requireTeacher(user);
		validatePreviewCode(previewCode);
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				long taskId = findTaskId(connection, previewCode, user.userId());
				permissionDao.requirePromptDesignAccess(connection, user.userId());
				requireTaskAccess(connection, user.userId(), taskId);
				ReevaluationPreview preview = previewDao.findPreview(connection, previewCode, user.userId());
				connection.commit();
				return preview;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public ReevaluationJobStatus loadJobStatus(AuthenticatedUser user, long taskId, long jobId)
			throws SQLException {
		requireTeacher(user);
		requirePositive(taskId);
		requirePositive(jobId);
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				permissionDao.requirePromptDesignAccess(connection, user.userId());
				requireTaskAccess(connection, user.userId(), taskId);
				ReevaluationJobStatus status = jobDao.loadStatus(connection, jobId, taskId, user.userId());
				connection.commit();
				return status;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public void retryFailed(AuthenticatedUser user, String previewCode) throws SQLException {
		requireTeacher(user);
		validatePreviewCode(previewCode);
		withPreview(user, previewCode, (connection, preview) -> {
			previewDao.retryFailed(connection, preview.previewId(), preview.rowVersion());
			insertPreviewAudit(connection, user.userId(), preview.taskId(), preview.promptVersionId(),
					"retry_reevaluation_preview", previewCode, preview.targetCount());
			return null;
		});
	}

	public void cancelPreview(AuthenticatedUser user, String previewCode) throws SQLException {
		requireTeacher(user);
		validatePreviewCode(previewCode);
		withPreview(user, previewCode, (connection, preview) -> {
			previewDao.cancel(connection, preview.previewId(), preview.rowVersion());
			insertPreviewAudit(connection, user.userId(), preview.taskId(), preview.promptVersionId(),
					"cancel_reevaluation_preview", previewCode, preview.targetCount());
			return null;
		});
	}

	public long confirmPreview(AuthenticatedUser user, String previewCode) throws SQLException {
		requireTeacher(user);
		validatePreviewCode(previewCode);
		return withPreview(user, previewCode, (connection, ignored) -> confirmLocked(connection, user, previewCode));
	}

	private long confirmLocked(Connection connection, AuthenticatedUser user, String previewCode)
			throws SQLException {
		permissionDao.requirePromptDesignAccess(connection, user.userId());
		long taskId = findTaskId(connection, previewCode, user.userId());
		requireTaskAccess(connection, user.userId(), taskId);
		PreviewSnapshot preview = previewDao.lockPreview(connection, previewCode, user.userId());
		if (!"ready".equals(preview.status())) {
			throw new IllegalStateException("全対象の予測が完了していないか、プレビューを確定できません。");
		}
		try (PreparedStatement expiry = connection.prepareStatement(
				"SELECT CURRENT_TIMESTAMP(6) < ? AS is_valid")) {
			expiry.setTimestamp(1, java.sql.Timestamp.valueOf(preview.expiresAt()));
			try (ResultSet rows = expiry.executeQuery()) {
				rows.next();
				if (!rows.getBoolean("is_valid")) {
					throw new PreviewExpiredException();
				}
			}
		}
		if (preview.targets().stream().anyMatch(target ->
				"excluded_submission_processing".equals(target.status()))) {
			throw new IllegalStateException("提出の受付処理中です。処理完了後にプレビューを作り直してください。");
		}
		PreviewConfiguration current = loadConfiguration(connection, preview.taskId(), preview.promptVersionId(), true);
		lockParticipants(connection, preview.taskId());
		List<Participant> participants = findParticipants(connection, preview.taskId()).stream()
				.sorted(Comparator.comparingLong(Participant::participationId)).toList();
		if (!same(preview.scopeFingerprint(), scopeFingerprint(participants))
				|| !same(preview.configurationFingerprint(), configurationFingerprint(current))
				|| participants.size() != preview.participantCount()
				|| participants.stream().filter(ReevaluationPreviewControl::isEligible).count()
						!= preview.targetCount()
				|| hasInputMismatch(connection, preview, participants, current)) {
			if (!previewDao.markStale(connection, preview.previewId(), preview.rowVersion())) {
				throw new IllegalStateException("プレビューの状態が更新されました。再読み込みしてください。");
			}
			previewDao.deleteTargets(connection, preview.previewId());
			throw new StalePreviewException();
		}
		if (preview.targets().stream().anyMatch(target -> !"succeeded".equals(target.status())
				&& !"excluded_no_submission".equals(target.status()))) {
			throw new IllegalStateException("全対象の予測が成功するまで確定できません。");
		}
		long jobId = insertJob(connection, user.userId(), preview);
		updateActivePrompt(connection, user.userId(), preview.taskId(), preview.promptVersionId(),
				preview.promptRowVersion(), preview.currentPromptVersionId());
		insertJobTargets(connection, jobId, preview.targets());
		insertAudit(connection, user.userId(), preview.taskId(), preview.promptVersionId(), jobId, previewCode);
		previewDao.markConfirmed(connection, preview.previewId());
		previewDao.deleteTargets(connection, preview.previewId());
		return jobId;
	}

	private <T> T withPreview(AuthenticatedUser user, String previewCode, SqlPreviewOperation<T> operation)
			throws SQLException {
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				long taskId = findTaskId(connection, previewCode, user.userId());
				permissionDao.requirePromptDesignAccess(connection, user.userId());
				requireTaskAccess(connection, user.userId(), taskId);
				ReevaluationPreview preview = previewDao.findPreview(connection, previewCode, user.userId());
				T result = operation.run(connection, preview);
				connection.commit();
				return result;
			} catch (StalePreviewException stale) {
				connection.commit();
				throw stale;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private boolean hasInputMismatch(
			Connection connection,
			PreviewSnapshot preview,
			List<Participant> participants,
			PreviewConfiguration configuration) throws SQLException {
		List<TargetSnapshot> targets = preview.targets().stream()
				.filter(target -> target.submissionId() != null)
				.sorted(Comparator.comparingLong(TargetSnapshot::participationId)).toList();
		List<Participant> eligible = participants.stream()
				.filter(ReevaluationPreviewControl::isEligible)
				.sorted(Comparator.comparingLong(Participant::participationId)).toList();
		if (targets.size() != eligible.size()) {
			return true;
		}
		for (int index = 0; index < targets.size(); index++) {
			TargetSnapshot target = targets.get(index);
			Participant participant = eligible.get(index);
			if (target.participationId() != participant.participationId()
					|| !Objects.equals(target.submissionId(), participant.submissionId())) {
				return true;
			}
			var payload = EvaluationInputBuilder.build(
					connection,
					preview.taskId(),
					participant.submissionId(),
					participant.studentUserId(),
					participant.participationId(),
					configuration.rubricId(),
					preview.promptVersionId(),
					configuration.modelId(),
					configuration.promptVersion(),
					configuration.rubricVersion(),
					participant.consentStatus(),
					target.requestId(),
					"teacher");
			if (!same(target.inputFingerprint(), inputFingerprint(payload.payload()))) {
				return true;
			}
		}
		return false;
	}

	private void requireTaskAccess(Connection connection, long teacherUserId, long taskId) throws SQLException {
		try (PreparedStatement task = connection.prepareStatement("""
				SELECT task_id, school_id FROM tasks
				WHERE task_id = ? AND created_by_user_id = ? AND deleted_at IS NULL
				  AND publication_status IN ('draft','published','requires_update')
				FOR UPDATE
				""")) {
			task.setLong(1, taskId);
			task.setLong(2, teacherUserId);
			try (ResultSet rows = task.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherPromptDao.TeacherTaskNotFoundException();
				}
				permissionDao.requirePromptAuthorizedSchool(connection, teacherUserId, rows.getLong("school_id"));
			}
		}
		try (PreparedStatement classes = connection.prepareStatement("""
				SELECT DISTINCT classroom_id FROM task_class_assignments
				WHERE task_id = ?
				ORDER BY classroom_id
				""")) {
			classes.setLong(1, taskId);
			try (ResultSet rows = classes.executeQuery()) {
				while (rows.next()) {
					permissionDao.requirePromptAuthorizedClass(connection, teacherUserId, rows.getLong("classroom_id"));
				}
			}
		}
	}

	private static PreviewConfiguration loadConfiguration(
			Connection connection, long taskId, long promptVersionId, boolean forUpdate) throws SQLException {
		String sql = """
				SELECT t.version AS task_version, t.rubric_id, t.active_prompt_version_id,
				       r.version AS rubric_version, r.rubric_status,
				       pv.version AS prompt_version, pv.ai_model, pv.row_version AS prompt_row_version,
				       pv.prompt_status, pv.fluctuation_generation_status, pv.evaluation_examples_status,
				       (SELECT COUNT(*) FROM rubric_dimensions d WHERE d.rubric_id = r.rubric_id) AS dimension_count,
				       (SELECT COUNT(*) FROM prompt_fluctuation_items i
				        WHERE i.prompt_version_id = pv.prompt_version_id
				          AND i.resolution_status = 'pending') AS pending_fluctuations
				FROM tasks t
				JOIN rubrics r ON r.rubric_id = t.rubric_id
				JOIN prompt_versions pv ON pv.prompt_version_id = ?
				  AND pv.task_id = t.task_id
				WHERE t.task_id = ? AND t.deleted_at IS NULL
				""" + (forUpdate ? " FOR UPDATE" : "");
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, promptVersionId);
			statement.setLong(2, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherPromptDao.TeacherTaskNotFoundException();
				}
				if (!"active".equals(rows.getString("rubric_status"))
						|| rows.getInt("dimension_count") != 2
						|| !List.of("configured", "versioned").contains(rows.getString("prompt_status"))
						|| !"completed".equals(rows.getString("fluctuation_generation_status"))
						|| !"completed".equals(rows.getString("evaluation_examples_status"))
						|| rows.getInt("pending_fluctuations") != 0) {
					throw new IllegalStateException("再評価には検証済みのプロンプトと有効な標準ルーブリックが必要です。");
				}
				long activeId = rows.getLong("active_prompt_version_id");
				Long currentPromptId = rows.wasNull() ? null : activeId;
				long rubricId = rows.getLong("rubric_id");
				if (rows.wasNull()) {
					throw new SQLException("The task has no evaluation rubric.");
				}
				return new PreviewConfiguration(
						rows.getLong("task_version"),
						rubricId,
						promptVersionId,
						rows.getString("rubric_version"),
						rows.getString("prompt_version"),
						rows.getString("ai_model"),
						rows.getLong("prompt_row_version"),
						currentPromptId);
			}
		}
	}

	private static List<Participant> findParticipants(Connection connection, long taskId) throws SQLException {
		List<Participant> participants = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT tp.participation_id, tp.student_user_id, u.display_name,
				       s.submission_id, s.revision_number, s.submission_status,
				       COALESCE(
				         (SELECT cr.consent_status FROM consent_records cr
				          WHERE cr.user_id = tp.student_user_id
				          ORDER BY cr.consent_id DESC LIMIT 1),
				         'unconfirmed') AS consent_status
				FROM task_class_assignments ca
				JOIN task_participations tp
				  ON tp.task_class_assignment_id = ca.task_class_assignment_id
				JOIN users u ON u.user_id = tp.student_user_id
				LEFT JOIN submissions s ON s.submission_id = (
				  SELECT latest.submission_id
				  FROM submissions latest
				  WHERE latest.participation_id = tp.participation_id
				  ORDER BY latest.revision_number DESC, latest.submission_id DESC
				  LIMIT 1
				)
				WHERE ca.task_id = ?
				ORDER BY tp.participation_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					long submissionId = rows.getLong("submission_id");
					Long nullableSubmissionId = rows.wasNull() ? null : submissionId;
					int revision = rows.getInt("revision_number");
					Integer nullableRevision = rows.wasNull() ? null : revision;
					participants.add(new Participant(
							rows.getLong("participation_id"),
							rows.getLong("student_user_id"),
							rows.getString("display_name"),
							nullableSubmissionId,
							nullableRevision,
							rows.getString("submission_status"),
							rows.getString("consent_status")));
				}
			}
		}
		return List.copyOf(participants);
	}

	private static void lockParticipants(Connection connection, long taskId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT tp.participation_id
				FROM task_class_assignments ca
				JOIN task_participations tp
				  ON tp.task_class_assignment_id = ca.task_class_assignment_id
				WHERE ca.task_id = ?
				ORDER BY tp.participation_id
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					rows.getLong("participation_id");
				}
			}
		}
	}

	private static boolean isEligible(Participant participant) {
		return participant.submissionId() != null
				&& ("accepted".equals(participant.submissionStatus())
						|| "locked".equals(participant.submissionStatus()));
	}

	private static byte[] scopeFingerprint(List<Participant> participants) {
		StringBuilder canonical = new StringBuilder();
		for (Participant participant : participants) {
			canonical.append(participant.participationId()).append(':')
					.append(participant.submissionId() == null ? "-" : participant.submissionId()).append(':')
					.append(participant.revisionNumber() == null ? "-" : participant.revisionNumber()).append(':')
					.append(participant.submissionStatus() == null ? "none" : participant.submissionStatus())
					.append(';');
		}
		return sha256(canonical.toString());
	}

	private static byte[] configurationFingerprint(PreviewConfiguration configuration) {
		return sha256(configuration.taskVersion() + "|" + configuration.rubricId() + "|"
				+ configuration.rubricVersion() + "|" + configuration.promptVersionId() + "|"
				+ configuration.promptVersion() + "|" + configuration.modelId()
				+ "|" + configuration.promptRowVersion() + "|" + configuration.currentPromptVersionId());
	}

	private static byte[] inputFingerprint(JsonObject payload) {
		JsonObject canonical = payload.deepCopy();
		JsonObject metadata = canonical.getAsJsonObject("metadata");
		metadata.remove("request_id");
		metadata.remove("requested_at");
		return sha256(metadata.toString() + "|" + canonical.toString());
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is not available.", impossible);
		}
	}

	private static boolean same(byte[] left, byte[] right) {
		return left != null && right != null && MessageDigest.isEqual(left, right);
	}

	private static long insertJob(Connection connection, long userId, PreviewSnapshot preview) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO reevaluation_jobs (
				  task_id, prompt_version_id, requested_by_user_id, reevaluation_status,
				  target_count, completed_count, progress_percent, result_summary, started_at, completed_at
				)
				VALUES (?, ?, ?, 'queued', ?, 0, 0, JSON_OBJECT('failed_count', 0),
				        NULL, NULL)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, preview.taskId());
			statement.setLong(2, preview.promptVersionId());
			statement.setLong(3, userId);
			statement.setInt(4, preview.targetCount());
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The reevaluation job ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void updateActivePrompt(
			Connection connection,
			long teacherUserId,
			long taskId,
			long promptVersionId,
			long expectedPromptRowVersion,
			Long expectedCurrentPromptId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks SET active_prompt_version_id = ?
				WHERE task_id = ? AND active_prompt_version_id <=> ?
				""")) {
			statement.setLong(1, promptVersionId);
			statement.setLong(2, taskId);
			if (expectedCurrentPromptId == null) {
				statement.setNull(3, java.sql.Types.BIGINT);
			} else {
				statement.setLong(3, expectedCurrentPromptId);
			}
			if (statement.executeUpdate() != 1) {
				throw new IllegalStateException("適用中のプロンプトが変更されています。プレビューを作り直してください。");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET prompt_status = 'versioned', updated_by_user_id = ?,
				    updated_at = CURRENT_TIMESTAMP, row_version = row_version + 1
				WHERE task_id = ? AND prompt_version_id = ? AND row_version = ?
				  AND prompt_status IN ('configured','versioned')
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, taskId);
			statement.setLong(3, promptVersionId);
			statement.setLong(4, expectedPromptRowVersion);
			if (statement.executeUpdate() != 1) {
				throw new IllegalStateException("プロンプト設定が変更されています。プレビューを作り直してください。");
			}
		}
	}

	private static void insertJobTargets(Connection connection, long jobId, List<TargetSnapshot> targets)
			throws SQLException {
		for (TargetSnapshot target : targets) {
			if (!"succeeded".equals(target.status())) {
				continue;
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO reevaluation_job_targets (
					  reevaluation_job_id, participation_id, submission_id, target_status,
					  evaluation_payload, provider_response, validated_result,
					  materialization_attempts, created_at, updated_at
					)
					VALUES (?, ?, ?, 'queued', ?, ?, ?, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
					""")) {
				JsonObject payload = target.payload().deepCopy();
				payload.getAsJsonObject("metadata").addProperty("request_id", target.requestId());
				statement.setLong(1, jobId);
				statement.setLong(2, target.participationId());
				statement.setLong(3, target.submissionId());
				statement.setString(4, payload.toString());
				statement.setString(5, target.providerResponse().toString());
				statement.setString(6, target.validatedResult().toString());
				statement.executeUpdate();
			}
		}
	}

	private static void insertAudit(
			Connection connection, long userId, long taskId, long promptVersionId, long jobId, String requestId)
			throws SQLException {
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		details.addProperty("reevaluation_job_id", jobId);
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id,
				  action_type, result_status, detail, after_data, request_id, occurred_at
				)
				VALUES (?, 'teacher', 'teacher-prompt-design', 'task', ?,
				        'confirm_reevaluation', 'success', ?, ?, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, userId);
			statement.setLong(2, taskId);
			statement.setString(3, details.toString());
			statement.setString(4, details.toString());
			statement.setString(5, requestId);
			statement.executeUpdate();
		}
	}

	private static void insertPreviewAudit(
			Connection connection,
			long userId,
			long taskId,
			long promptVersionId,
			String action,
			String previewCode,
			int targetCount) throws SQLException {
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		details.addProperty("preview_code", previewCode);
		details.addProperty("target_count", targetCount);
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id,
				  action_type, result_status, detail, after_data, request_id, occurred_at
				)
				VALUES (?, 'teacher', 'teacher-prompt-design', 'task', ?,
				        ?, 'success', ?, ?, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, userId);
			statement.setLong(2, taskId);
			statement.setString(3, action);
			statement.setString(4, details.toString());
			statement.setString(5, details.toString());
			statement.setString(6, previewCode);
			statement.executeUpdate();
		}
	}

	private static long findTaskId(Connection connection, String previewCode, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_id FROM reevaluation_previews
				WHERE preview_code = ? AND requested_by_user_id = ?
				""")) {
			statement.setString(1, previewCode);
			statement.setLong(2, userId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherPromptDao.TeacherTaskNotFoundException();
				}
				return rows.getLong("task_id");
			}
		}
	}

	private static void validatePreviewCode(String value) {
		if (value == null || !value.matches(
				"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			throw new IllegalArgumentException("プレビューIDが不正です。");
		}
	}

	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.TEACHER || user.passwordChangeRequired()) {
			throw new SecurityException("Active teacher access is required.");
		}
	}

	private static void requirePositive(long value) {
		if (value < 1) {
			throw new IllegalArgumentException("課題IDまたはプロンプト版IDが不正です。");
		}
	}

	private static void rollback(Connection connection, Exception failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
	}

	private interface SqlPreviewOperation<T> {
		T run(Connection connection, ReevaluationPreview preview) throws SQLException;
	}

	private record PreviewConfiguration(
			long taskVersion,
			long rubricId,
			long promptVersionId,
			String rubricVersion,
			String promptVersion,
			String modelId,
			long promptRowVersion,
			Long currentPromptVersionId) {}

	public static final class NoReevaluationTargetsException extends IllegalStateException {
		private static final long serialVersionUID = 1L;

		public NoReevaluationTargetsException() {
			super("再評価対象なし");
		}
	}

	public static final class PreviewExpiredException extends IllegalStateException {
		private static final long serialVersionUID = 1L;

		public PreviewExpiredException() {
			super("プレビューの有効期限が切れました。新しく生成してください。");
		}
	}

	public static final class StalePreviewException extends IllegalStateException {
		private static final long serialVersionUID = 1L;

		public StalePreviewException() {
			super("提出または評価条件が変更されました。プレビューを作り直してください。");
		}
	}

	@FunctionalInterface
	interface ConnectionFactory {
		Connection open() throws SQLException;
	}
}
