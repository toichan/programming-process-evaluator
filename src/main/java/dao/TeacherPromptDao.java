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
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import control.evaluation.GeminiModelCatalog;
import entity.StandardRubric;
import entity.ReevaluationJobHistory;
import entity.TeacherPromptAuditEntry;
import entity.TeacherPromptVersion;
import entity.TeacherPromptVersion.EvaluationExample;
import entity.TeacherPromptVersion.FluctuationItem;
import entity.TeacherTaskDetails;

public final class TeacherPromptDao {
	private static final String FEATURE_CODE = "teacher-prompt-design";
	private static final String TARGET_TYPE = "task";
	private final StandardRubricDao standardRubricDao;
	private final TeacherTaskDao taskDao;

	public TeacherPromptDao() {
		this(new StandardRubricDao(), new TeacherTaskDao());
	}

	TeacherPromptDao(StandardRubricDao standardRubricDao, TeacherTaskDao taskDao) {
		this.standardRubricDao = java.util.Objects.requireNonNull(standardRubricDao);
		this.taskDao = java.util.Objects.requireNonNull(taskDao);
	}

	public long ensureStandardRubric(Connection connection, long teacherUserId, long taskId) throws SQLException {
		requireWriteTransaction(connection);
		taskDao.findDraft(connection, teacherUserId, taskId, true)
				.orElseThrow(TeacherTaskNotFoundException::new);
		long standardRubricId = standardRubricDao.requireActiveId(connection);
		Long currentRubricId = null;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT rubric_id FROM tasks
				WHERE task_id = ? AND created_by_user_id = ?
				  AND publication_status = 'draft' AND deleted_at IS NULL
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherTaskNotFoundException();
				}
				long value = rows.getLong("rubric_id");
				if (!rows.wasNull()) {
					currentRubricId = value;
				}
			}
		}
		if (currentRubricId == null || currentRubricId != standardRubricId) {
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE tasks SET rubric_id = ?
					WHERE task_id = ? AND created_by_user_id = ?
					  AND publication_status = 'draft' AND deleted_at IS NULL
					""")) {
				statement.setLong(1, standardRubricId);
				statement.setLong(2, taskId);
				statement.setLong(3, teacherUserId);
				if (statement.executeUpdate() != 1) {
					throw new SQLException("The standard rubric could not be assigned to the task.");
				}
			}
			JsonObject before = new JsonObject();
			if (currentRubricId != null) {
				before.addProperty("rubric_id", currentRubricId);
			}
			JsonObject after = new JsonObject();
			after.addProperty("rubric_id", standardRubricId);
			after.addProperty("rubric_title", StandardRubric.TITLE);
			after.addProperty("rubric_version", StandardRubric.VERSION);
			recordAudit(connection, teacherUserId, taskId, "assign_standard_rubric",
					"共通標準ルーブリックを課題へ関連付け", before, after);
		}
		return standardRubricId;
	}

	public Long findActivePromptVersionId(Connection connection, long taskId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT active_prompt_version_id FROM tasks WHERE task_id = ?")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new TeacherTaskNotFoundException();
				}
				long value = rows.getLong("active_prompt_version_id");
				return rows.wasNull() ? null : value;
			}
		}
	}

	public List<TeacherPromptVersion> findVersions(Connection connection, long taskId) throws SQLException {
		List<Long> ids = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT prompt_version_id FROM prompt_versions
				WHERE task_id = ?
				ORDER BY created_at DESC, prompt_version_id DESC
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					ids.add(rows.getLong("prompt_version_id"));
				}
			}
		}
		List<TeacherPromptVersion> versions = new ArrayList<>(ids.size());
		for (long id : ids) {
			versions.add(findVersion(connection, taskId, id));
		}
		return List.copyOf(versions);
	}

	public TeacherPromptVersion findVersion(Connection connection, long taskId, long promptVersionId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT pv.prompt_version_id, pv.task_id, pv.version, pv.ai_model, pv.common_prompt,
				       pv.additional_evaluation_instruction, pv.prompt_status,
				       pv.fluctuation_generation_status, pv.evaluation_examples_status, pv.row_version,
				       creator.login_id AS created_by_login_id, pv.created_at,
				       updater.login_id AS updated_by_login_id, pv.updated_at
				FROM prompt_versions pv
				JOIN users creator ON creator.user_id = pv.created_by_user_id
				LEFT JOIN users updater ON updater.user_id = pv.updated_by_user_id
				WHERE pv.task_id = ? AND pv.prompt_version_id = ?
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, promptVersionId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new PromptVersionNotFoundException();
				}
				return new TeacherPromptVersion(
						rows.getLong("prompt_version_id"),
						rows.getLong("task_id"),
						rows.getString("version"),
						rows.getString("ai_model"),
						rows.getString("common_prompt"),
						rows.getString("additional_evaluation_instruction"),
						rows.getString("prompt_status"),
						rows.getString("fluctuation_generation_status"),
						rows.getString("evaluation_examples_status"),
						rows.getLong("row_version"),
						rows.getString("created_by_login_id"),
						toLocalDateTime(rows.getTimestamp("created_at")),
						rows.getString("updated_by_login_id"),
						toLocalDateTime(rows.getTimestamp("updated_at")),
						findFluctuationItems(connection, promptVersionId),
						findEvaluationExamples(connection, promptVersionId));
			}
		}
	}

	public List<TeacherPromptAuditEntry> findAuditEntries(Connection connection, long teacherUserId, long taskId)
			throws SQLException {
		List<TeacherPromptAuditEntry> entries = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT a.audit_log_id, u.login_id, a.action_type, a.result_status,
				       a.detail, a.occurred_at
				FROM audit_logs a
				JOIN users u ON u.user_id = a.actor_user_id
				WHERE a.actor_role = 'teacher' AND a.feature_code = ?
				  AND a.target_type = ? AND a.target_id = ? AND a.actor_user_id = ?
				ORDER BY a.occurred_at DESC, a.audit_log_id DESC
				""")) {
			statement.setString(1, FEATURE_CODE);
			statement.setString(2, TARGET_TYPE);
			statement.setLong(3, taskId);
			statement.setLong(4, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					entries.add(new TeacherPromptAuditEntry(
							rows.getLong("audit_log_id"),
							rows.getString("login_id"),
							rows.getString("action_type"),
							rows.getString("result_status"),
							rows.getString("detail"),
							toLocalDateTime(rows.getTimestamp("occurred_at"))));
				}
			}
		}
		return List.copyOf(entries);
	}

	public List<ReevaluationJobHistory> findReevaluationJobHistory(
			Connection connection, long teacherUserId, long taskId) throws SQLException {
		List<ReevaluationJobHistory> jobs = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT j.reevaluation_job_id, j.prompt_version_id, pv.version AS prompt_version,
				       j.reevaluation_status, j.target_count, j.completed_count, j.progress_percent,
				       COALESCE(
				         (SELECT COUNT(*) FROM reevaluation_job_targets jt
				          WHERE jt.reevaluation_job_id = j.reevaluation_job_id
				            AND jt.target_status = 'failed'),
				         0) AS failed_count,
				       requester.login_id AS requested_by_login_id,
				       COALESCE(
				         (SELECT a.occurred_at
				          FROM audit_logs a
				          WHERE a.actor_role = 'teacher'
				            AND a.feature_code = 'teacher-prompt-design'
				            AND a.target_type = 'task' AND a.target_id = j.task_id
				            AND a.action_type = 'confirm_reevaluation'
				            AND JSON_UNQUOTE(JSON_EXTRACT(a.after_data, '$.reevaluation_job_id'))
				                = CAST(j.reevaluation_job_id AS CHAR)
				          ORDER BY a.occurred_at DESC, a.audit_log_id DESC
				          LIMIT 1),
				         j.started_at, j.completed_at) AS requested_at,
				       j.started_at, j.completed_at
				FROM reevaluation_jobs j
				JOIN tasks t ON t.task_id = j.task_id
				JOIN prompt_versions pv ON pv.prompt_version_id = j.prompt_version_id
				JOIN users requester ON requester.user_id = j.requested_by_user_id
				WHERE j.task_id = ? AND t.created_by_user_id = ? AND t.deleted_at IS NULL
				ORDER BY requested_at DESC, j.reevaluation_job_id DESC
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					jobs.add(new ReevaluationJobHistory(
							rows.getLong("reevaluation_job_id"),
							rows.getLong("prompt_version_id"),
							rows.getString("prompt_version"),
							rows.getString("reevaluation_status"),
							rows.getInt("target_count"),
							rows.getInt("completed_count"),
							rows.getInt("failed_count"),
							rows.getBigDecimal("progress_percent"),
							rows.getString("requested_by_login_id"),
							toLocalDateTime(rows.getTimestamp("requested_at")),
							toLocalDateTime(rows.getTimestamp("started_at")),
							toLocalDateTime(rows.getTimestamp("completed_at"))));
				}
			}
		}
		return List.copyOf(jobs);
	}

	public String findTargetSummary(Connection connection, long taskId) throws SQLException {
		List<String> targets = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT DISTINCT s.name AS school_name, c.grade_name, c.name AS classroom_name
				FROM task_class_assignments a
				JOIN classrooms c ON c.classroom_id = a.classroom_id
				JOIN schools s ON s.school_id = c.school_id
				WHERE a.task_id = ?
				ORDER BY s.name, c.grade_name, c.name
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					String grade = rows.getString("grade_name");
					targets.add(rows.getString("school_name") + " / "
							+ (grade == null ? "" : grade) + rows.getString("classroom_name"));
				}
			}
		}
		return targets.isEmpty() ? "対象クラス未設定" : String.join("、", targets);
	}

	public long saveDraft(
			Connection connection,
			long teacherUserId,
			long taskId,
			Long promptVersionId,
			long expectedRowVersion,
			String modelId,
			String commonPrompt,
			String additionalInstruction) throws SQLException {
		requireWriteTransaction(connection);
			var task = taskDao.findPromptTask(connection, teacherUserId, taskId, true)
				.orElseThrow(TeacherTaskNotFoundException::new);
			if ("draft".equals(task.publicationStatus())) {
				ensureStandardRubric(connection, teacherUserId, taskId);
			}
		if (promptVersionId == null) {
			String version = nextVersion(connection, taskId);
			long id;
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO prompt_versions (
					  task_id, version, ai_model, common_prompt, additional_evaluation_instruction,
					  prompt_status, fluctuation_generation_status, evaluation_examples_status,
					  created_by_user_id, updated_by_user_id, created_at, updated_at, row_version
					) VALUES (?, ?, ?, ?, ?, 'draft', 'not_generated', 'not_generated',
					  ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
					""", Statement.RETURN_GENERATED_KEYS)) {
				statement.setLong(1, taskId);
				statement.setString(2, version);
				statement.setString(3, modelId);
				statement.setString(4, commonPrompt);
				statement.setString(5, nullableText(additionalInstruction));
				statement.setLong(6, teacherUserId);
				statement.setLong(7, teacherUserId);
				statement.executeUpdate();
				try (ResultSet keys = statement.getGeneratedKeys()) {
					if (!keys.next()) {
						throw new SQLException("The prompt version insert did not return an identifier.");
					}
					id = keys.getLong(1);
				}
			}
			JsonObject after = new JsonObject();
			after.addProperty("prompt_version_id", id);
			after.addProperty("version", version);
			after.addProperty("ai_model", modelId);
			after.addProperty("common_prompt", commonPrompt);
			recordAudit(connection, teacherUserId, taskId, "save_prompt_draft", "プロンプト下書きを作成", null, after);
			return id;
		}

		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		if (!"draft".equals(current.promptStatus())) {
			throw new PromptVersionConflictException();
		}
		if (current.rowVersion() != expectedRowVersion) {
			throw new PromptVersionConflictException();
		}
		boolean changed = !current.aiModel().equals(modelId)
				|| !current.commonPrompt().equals(commonPrompt)
				|| !java.util.Objects.equals(current.additionalInstruction(), nullableText(additionalInstruction));
		recoverOrRejectGeneration(connection, teacherUserId, current);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET ai_model = ?, common_prompt = ?, additional_evaluation_instruction = ?,
				    updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP,
				    row_version = row_version + 1,
				    fluctuation_generation_status = CASE WHEN ? THEN 'not_generated'
				      ELSE fluctuation_generation_status END,
				    evaluation_examples_status = CASE WHEN ? THEN 'not_generated'
				      ELSE evaluation_examples_status END
				WHERE prompt_version_id = ? AND task_id = ? AND row_version = ?
				  AND prompt_status = 'draft'
				""")) {
			statement.setString(1, modelId);
			statement.setString(2, commonPrompt);
			statement.setString(3, nullableText(additionalInstruction));
			statement.setLong(4, teacherUserId);
			statement.setBoolean(5, changed);
			statement.setBoolean(6, changed);
			statement.setLong(7, promptVersionId);
			statement.setLong(8, taskId);
			statement.setLong(9, expectedRowVersion);
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
		if (changed) {
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM prompt_fluctuation_items WHERE prompt_version_id = ?")) {
				statement.setLong(1, promptVersionId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE evaluation_examples SET example_status = 'inactive'
					WHERE prompt_version_id = ? AND example_status = 'active'
					""")) {
				statement.setLong(1, promptVersionId);
				statement.executeUpdate();
			}
		}
		JsonObject before = new JsonObject();
		before.addProperty("row_version", expectedRowVersion);
		JsonObject after = new JsonObject();
		after.addProperty("row_version", expectedRowVersion + 1);
		after.addProperty("changed", changed);
		recordAudit(connection, teacherUserId, taskId, "save_prompt_draft", "プロンプト下書きを更新", before, after);
		return promptVersionId;
	}

	public long copyCurrentPromptToDraft(
			Connection connection,
			long teacherUserId,
			long sourceTaskId,
			long copiedTaskId) throws SQLException {
		requireWriteTransaction(connection);
		String commonPrompt;
		String additionalInstruction;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT pv.common_prompt, pv.additional_evaluation_instruction
				FROM tasks source
				JOIN prompt_versions pv
				  ON pv.prompt_version_id = source.active_prompt_version_id
				 AND pv.task_id = source.task_id
				WHERE source.task_id = ? AND source.publication_status = 'published'
				  AND source.deleted_at IS NULL AND pv.prompt_status IN ('configured','versioned')
				FOR UPDATE
				""")) {
			statement.setLong(1, sourceTaskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new IllegalArgumentException("複製元に現在適用中のプロンプトがありません。");
				}
				commonPrompt = rows.getString("common_prompt");
				additionalInstruction = rows.getString("additional_evaluation_instruction");
			}
		}
		return saveDraft(
				connection,
				teacherUserId,
				copiedTaskId,
				null,
				0,
				GeminiModelCatalog.DEFAULT_MODEL,
				commonPrompt,
				additionalInstruction);
	}

	public long beginGeneration(Connection connection, long teacherUserId, long taskId, long promptVersionId,
			long expectedRowVersion, String stage) throws SQLException {
		requireWriteTransaction(connection);
		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		if (!"draft".equals(current.promptStatus()) || current.rowVersion() != expectedRowVersion) {
			throw new PromptVersionConflictException();
		}
		recoverOrRejectGeneration(connection, teacherUserId, current);
		String statusColumn = stageColumn(stage);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET %s = 'in_progress', updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP,
				    row_version = row_version + 1
				WHERE task_id = ? AND prompt_version_id = ? AND row_version = ? AND prompt_status = 'draft'
				""".formatted(statusColumn))) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, taskId);
			statement.setLong(3, promptVersionId);
			statement.setLong(4, expectedRowVersion);
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		details.addProperty("stage", stage);
		recordAudit(connection, teacherUserId, taskId, "generate_" + stage, "AI生成を開始", null, details);
		return expectedRowVersion + 1;
	}

	public void completeFluctuations(
			Connection connection,
			long teacherUserId,
			long taskId,
			long promptVersionId,
			long expectedRowVersion,
			List<FluctuationItem> items) throws SQLException {
		requireWriteTransaction(connection);
		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		requireInProgress(current, expectedRowVersion, current.fluctuationGenerationStatus());
		try (PreparedStatement delete = connection.prepareStatement(
				"DELETE FROM prompt_fluctuation_items WHERE prompt_version_id = ?")) {
			delete.setLong(1, promptVersionId);
			delete.executeUpdate();
		}
		try (PreparedStatement insert = connection.prepareStatement("""
				INSERT INTO prompt_fluctuation_items (
				  prompt_version_id, item_text, teacher_resolution, resolution_status, sort_order
				) VALUES (?, ?, NULL, 'pending', ?)
				""")) {
			for (int index = 0; index < items.size(); index++) {
				FluctuationItem item = items.get(index);
				insert.setLong(1, promptVersionId);
				insert.setString(2, fluctuationJson(item));
				insert.setInt(3, index + 1);
				insert.addBatch();
			}
			insert.executeBatch();
		}
		updateGenerationStatus(connection, teacherUserId, taskId, promptVersionId, expectedRowVersion,
				"fluctuation_generation_status", "completed");
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		details.addProperty("item_count", items.size());
		recordAudit(connection, teacherUserId, taskId, "generate_fluctuation", "揺らぎ項目を生成", null, details);
	}

	public void completeExamples(
			Connection connection,
			long teacherUserId,
			long taskId,
			long promptVersionId,
			long expectedRowVersion,
			JsonObject input,
			JsonObject output) throws SQLException {
		requireWriteTransaction(connection);
		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		requireInProgress(current, expectedRowVersion, current.evaluationExamplesStatus());
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE evaluation_examples SET example_status = 'inactive'
				WHERE prompt_version_id = ? AND example_status = 'active'
				""")) {
			statement.setLong(1, promptVersionId);
			statement.executeUpdate();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluation_examples (
				  prompt_version_id, example_input, example_output, example_status, created_at
				) VALUES (?, CAST(? AS JSON), CAST(? AS JSON), 'inactive', CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, promptVersionId);
			statement.setString(2, input.toString());
			statement.setString(3, output.toString());
			statement.executeUpdate();
		}
		updateGenerationStatus(connection, teacherUserId, taskId, promptVersionId, expectedRowVersion,
				"evaluation_examples_status", "completed");
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		details.addProperty("example_count", output.getAsJsonArray("simulated_results").size());
		recordAudit(connection, teacherUserId, taskId, "generate_evaluation_examples", "合成評価例を生成", null,
				details);
	}

	public void saveEvaluationExamples(Connection connection, long teacherUserId, long taskId, long promptVersionId,
			long expectedRowVersion) throws SQLException {
		requireWriteTransaction(connection);
		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		if (!"draft".equals(current.promptStatus()) || current.rowVersion() != expectedRowVersion
				|| !"completed".equals(current.evaluationExamplesStatus())
				|| current.evaluationExamples().isEmpty()) {
			throw new PromptVersionConflictException();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE evaluation_examples SET example_status = 'active'
				WHERE prompt_version_id = ? AND example_status = 'inactive'
				""")) {
			statement.setLong(1, promptVersionId);
			if (statement.executeUpdate() == 0) {
				throw new SQLException("No generated evaluation examples were available to save.");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET prompt_status = 'configured', updated_by_user_id = ?,
				    updated_at = CURRENT_TIMESTAMP, row_version = row_version + 1
				WHERE task_id = ? AND prompt_version_id = ? AND row_version = ? AND prompt_status = 'draft'
				  AND evaluation_examples_status = 'completed'
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, taskId);
			statement.setLong(3, promptVersionId);
			statement.setLong(4, expectedRowVersion);
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
		recordAudit(connection, teacherUserId, taskId, "save_evaluation_examples", "評価例を保存",
				null, promptVersionDetails(promptVersionId));
	}

	public void applyUnpublishedPrompt(
			Connection connection, long teacherUserId, long taskId, long promptVersionId,
			long expectedTaskVersion, long expectedPromptRowVersion) throws SQLException {
		requireWriteTransaction(connection);
		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		if (current.rowVersion() != expectedPromptRowVersion) {
			throw new PromptVersionConflictException();
		}
		if (!current.isReadyForApplication()) {
			throw new IllegalStateException("揺らぎへの教師対応と評価例の生成・保存を完了してください。");
		}
		Long previousId = findActivePromptVersionId(connection, taskId);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET active_prompt_version_id = ?, updated_by_user_id = ?,
				    updated_at = CURRENT_TIMESTAMP, version = version + 1
				WHERE task_id = ? AND created_by_user_id = ? AND version = ?
				  AND publication_status IN ('draft','requires_update') AND deleted_at IS NULL
				""")) {
			statement.setLong(1, promptVersionId);
			statement.setLong(2, teacherUserId);
			statement.setLong(3, taskId);
			statement.setLong(4, teacherUserId);
			statement.setLong(5, expectedTaskVersion);
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
		JsonObject before = new JsonObject();
		if (previousId == null) before.add("active_prompt_version_id", com.google.gson.JsonNull.INSTANCE);
		else before.addProperty("active_prompt_version_id", previousId);
		JsonObject after = new JsonObject();
		after.addProperty("active_prompt_version_id", promptVersionId);
		recordAudit(connection, teacherUserId, taskId, "apply_unpublished_prompt",
				"未公開・学習開始前の課題へプロンプトを適用", before, after);
	}

	public void saveResolutions(
			Connection connection,
			long teacherUserId,
			long taskId,
			long promptVersionId,
			long expectedRowVersion,
			List<FluctuationItem> updates,
			String additionalInstruction) throws SQLException {
		requireWriteTransaction(connection);
		TeacherPromptVersion current = findVersionForUpdate(connection, taskId, promptVersionId);
		if (!"draft".equals(current.promptStatus()) || current.rowVersion() != expectedRowVersion
				|| !"completed".equals(current.fluctuationGenerationStatus())
				|| current.fluctuationItems().size() != updates.size()) {
			throw new PromptVersionConflictException();
		}
		recoverOrRejectGeneration(connection, teacherUserId, current);
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (FluctuationItem update : updates) {
			if (!seen.add(update.id())
					|| current.fluctuationItems().stream().noneMatch(item -> item.id() == update.id())
					|| !List.of("pending", "resolved", "not_applicable").contains(update.resolutionStatus())) {
				throw new IllegalArgumentException("A fluctuation resolution does not belong to this prompt version.");
			}
			if ("resolved".equals(update.resolutionStatus())
					&& (update.teacherResolution() == null || update.teacherResolution().isBlank())) {
				throw new IllegalArgumentException("解決方針を入力してください。");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_fluctuation_items
				SET teacher_resolution = ?, resolution_status = ?
				WHERE fluctuation_item_id = ? AND prompt_version_id = ?
				""")) {
			for (FluctuationItem update : updates) {
				statement.setString(1, nullableText(update.teacherResolution()));
				statement.setString(2, update.resolutionStatus());
				statement.setLong(3, update.id());
				statement.setLong(4, promptVersionId);
				statement.addBatch();
			}
			statement.executeBatch();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET additional_evaluation_instruction = ?, updated_by_user_id = ?,
				    updated_at = CURRENT_TIMESTAMP, row_version = row_version + 1
				WHERE task_id = ? AND prompt_version_id = ? AND row_version = ? AND prompt_status = 'draft'
				""")) {
			statement.setString(1, nullableText(additionalInstruction));
			statement.setLong(2, teacherUserId);
			statement.setLong(3, taskId);
			statement.setLong(4, promptVersionId);
			statement.setLong(5, expectedRowVersion);
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
		JsonObject after = new JsonObject();
		after.addProperty("resolved_items", updates.size());
		after.addProperty("additional_instruction", nullableText(additionalInstruction));
		recordAudit(connection, teacherUserId, taskId, "save_fluctuation_resolutions", "揺らぎ項目への教師対応を保存",
				null, after);
	}

	private void recoverOrRejectGeneration(Connection connection, long teacherUserId, TeacherPromptVersion current)
			throws SQLException {
		if (!current.hasActiveGeneration()) {
			return;
		}
		if (!current.isGenerationStale()) {
			throw new PromptGenerationInProgressException();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET fluctuation_generation_status = CASE WHEN fluctuation_generation_status = 'in_progress'
				      THEN 'failed' ELSE fluctuation_generation_status END,
				    evaluation_examples_status = CASE WHEN evaluation_examples_status = 'in_progress'
				      THEN 'failed' ELSE evaluation_examples_status END
				WHERE prompt_version_id = ? AND row_version = ? AND prompt_status = 'draft'
				""")) {
			statement.setLong(1, current.promptVersionId());
			statement.setLong(2, current.rowVersion());
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
		recordAudit(connection, teacherUserId, current.taskId(), "recover_generation",
				"中断したAI生成を復旧", null, promptVersionDetails(current.promptVersionId()));
	}

	public void failGeneration(Connection connection, long teacherUserId, long taskId, long promptVersionId,
			long expectedRowVersion, String stage) throws SQLException {
		requireWriteTransaction(connection);
		String statusColumn = stageColumn(stage);
		updateGenerationStatus(connection, teacherUserId, taskId, promptVersionId, expectedRowVersion,
				statusColumn, "failed");
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		details.addProperty("stage", stage);
		recordAudit(connection, teacherUserId, taskId, "fail_" + stage, "AI生成に失敗", null, details);
	}

	private List<FluctuationItem> findFluctuationItems(Connection connection, long promptVersionId)
			throws SQLException {
		List<FluctuationItem> items = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT fluctuation_item_id, item_text, teacher_resolution, resolution_status, sort_order
				FROM prompt_fluctuation_items
				WHERE prompt_version_id = ?
				ORDER BY sort_order, fluctuation_item_id
				""")) {
			statement.setLong(1, promptVersionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					try {
						JsonObject item = JsonParser.parseString(rows.getString("item_text")).getAsJsonObject();
						items.add(new FluctuationItem(
								rows.getLong("fluctuation_item_id"),
								item.get("title").getAsString(),
								item.get("level").getAsString(),
								item.get("description").getAsString(),
								item.get("example").getAsString(),
								rows.getString("teacher_resolution"),
								rows.getString("resolution_status"),
								rows.getInt("sort_order")));
					} catch (RuntimeException malformedItem) {
						throw new SQLException("Stored prompt fluctuation data is invalid.", malformedItem);
					}
				}
			}
		}
		return List.copyOf(items);
	}

	private List<EvaluationExample> findEvaluationExamples(Connection connection, long promptVersionId)
			throws SQLException {
		List<EvaluationExample> examples = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT evaluation_example_id, example_input, example_output, example_status
				FROM evaluation_examples
				WHERE prompt_version_id = ?
				ORDER BY created_at DESC, evaluation_example_id DESC
				""")) {
			statement.setLong(1, promptVersionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					try {
						examples.add(new EvaluationExample(
								rows.getLong("evaluation_example_id"),
								JsonParser.parseString(rows.getString("example_input")).getAsJsonObject(),
								JsonParser.parseString(rows.getString("example_output")).getAsJsonObject(),
								rows.getString("example_status")));
					} catch (RuntimeException malformedExample) {
						throw new SQLException("Stored prompt evaluation example is invalid.", malformedExample);
					}
				}
			}
		}
		return List.copyOf(examples);
	}

	private TeacherPromptVersion findVersionForUpdate(Connection connection, long taskId, long promptVersionId)
			throws SQLException {
		TeacherPromptVersion current = findVersion(connection, taskId, promptVersionId);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT prompt_version_id FROM prompt_versions
				WHERE task_id = ? AND prompt_version_id = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, promptVersionId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new PromptVersionNotFoundException();
				}
			}
		}
		return current;
	}

	private String nextVersion(Connection connection, long taskId) throws SQLException {
		int greatest = 0;
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT version FROM prompt_versions WHERE task_id = ? FOR UPDATE")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					String version = rows.getString("version");
					if (version != null && version.matches("v[1-9][0-9]*")) {
						greatest = Math.max(greatest, Integer.parseInt(version.substring(1)));
					}
				}
			}
		}
		if (greatest == Integer.MAX_VALUE) {
			throw new SQLException("Prompt version sequence is exhausted.");
		}
		return "v" + (greatest + 1);
	}

	private void updateGenerationStatus(
			Connection connection,
			long teacherUserId,
			long taskId,
			long promptVersionId,
			long expectedRowVersion,
			String statusColumn,
			String status) throws SQLException {
		if (!List.of("fluctuation_generation_status", "evaluation_examples_status").contains(statusColumn)) {
			throw new IllegalArgumentException("Unsupported prompt generation status.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE prompt_versions
				SET %s = ?, updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP,
				    row_version = row_version + 1
				WHERE task_id = ? AND prompt_version_id = ? AND row_version = ?
				  AND prompt_status = 'draft'
				""".formatted(statusColumn))) {
			statement.setString(1, status);
			statement.setLong(2, teacherUserId);
			statement.setLong(3, taskId);
			statement.setLong(4, promptVersionId);
			statement.setLong(5, expectedRowVersion);
			if (statement.executeUpdate() != 1) {
				throw new PromptVersionConflictException();
			}
		}
	}

	private void recordAudit(
			Connection connection,
			long teacherUserId,
			long taskId,
			String action,
			String detail,
			JsonObject before,
			JsonObject after) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id, action_type,
				  result_status, detail, before_data, after_data, request_id, occurred_at
				) VALUES (?, 'teacher', ?, ?, ?, ?, 'success', ?, CAST(? AS JSON), CAST(? AS JSON), ?,
				  CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, FEATURE_CODE);
			statement.setString(3, TARGET_TYPE);
			statement.setLong(4, taskId);
			statement.setString(5, action);
			statement.setString(6, detail);
			statement.setString(7, before == null ? null : before.toString());
			statement.setString(8, after == null ? null : after.toString());
			statement.setString(9, UUID.randomUUID().toString());
			statement.executeUpdate();
		}
	}

	private static String stageColumn(String stage) {
		return switch (stage) {
			case "fluctuation" -> "fluctuation_generation_status";
			case "examples" -> "evaluation_examples_status";
			default -> throw new IllegalArgumentException("Unsupported prompt generation stage.");
		};
	}

	private static void requireInProgress(TeacherPromptVersion current, long expectedRowVersion, String status) {
		if (!"draft".equals(current.promptStatus()) || current.rowVersion() != expectedRowVersion
				|| !"in_progress".equals(status)) {
			throw new PromptVersionConflictException();
		}
	}

	private static JsonObject promptVersionDetails(long promptVersionId) {
		JsonObject details = new JsonObject();
		details.addProperty("prompt_version_id", promptVersionId);
		return details;
	}

	private static String fluctuationJson(FluctuationItem item) {
		JsonObject json = new JsonObject();
		json.addProperty("title", item.title());
		json.addProperty("level", item.level());
		json.addProperty("description", item.description());
		json.addProperty("example", item.example());
		return json.toString();
	}

	private static String nullableText(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private static void requireWriteTransaction(Connection connection) throws SQLException {
		if (connection == null || connection.getAutoCommit()) {
			throw new IllegalStateException("Teacher prompt writes require an active transaction.");
		}
	}

	public static final class TeacherTaskNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	public static final class PromptVersionNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	public static final class PromptVersionConflictException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	public static final class PromptGenerationInProgressException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
