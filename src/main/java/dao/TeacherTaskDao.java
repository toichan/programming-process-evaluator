package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import entity.EditorHint;
import entity.EditorTestCase;
import entity.TeacherTaskAuditEntry;
import entity.TeacherTaskDetails;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.HintInput;
import entity.TeacherHintOption;

public final class TeacherTaskDao {
	@FunctionalInterface
	interface StandardRubricIdProvider {
		long requireActiveId(Connection connection) throws SQLException;
	}

	private static final String TASK_FEATURE_CODE = "task-management";
	private static final String TASK_TARGET_TYPE = "task";
	private final StandardRubricIdProvider standardRubricIdProvider;

	public TeacherTaskDao() {
		this(new StandardRubricDao()::requireActiveId);
	}

	TeacherTaskDao(StandardRubricIdProvider standardRubricIdProvider) {
		this.standardRubricIdProvider = java.util.Objects.requireNonNull(standardRubricIdProvider);
	}

	public List<TeacherTaskDetails> findDrafts(Connection connection, long teacherUserId) throws SQLException {
		return findTasks(connection, teacherUserId, "t.publication_status = 'draft'");
	}

	public List<TeacherTaskDetails> findManageableTasks(
			Connection connection, List<Long> authorizedSchoolIds) throws SQLException {
		return findTasksForSchools(connection, authorizedSchoolIds,
				"t.publication_status IN ('draft','published','requires_update')", false);
	}

	public Map<Long, Set<Long>> findAssignmentClassHistory(Connection connection, List<Long> taskIds)
			throws SQLException {
		List<Long> uniqueTaskIds = taskIds.stream().distinct().toList();
		if (uniqueTaskIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Set<Long>> history = new LinkedHashMap<>();
		uniqueTaskIds.forEach(taskId -> history.put(taskId, new HashSet<>()));
		String placeholders = String.join(",", java.util.Collections.nCopies(uniqueTaskIds.size(), "?"));
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_id, classroom_id
				FROM task_class_assignments
				WHERE task_id IN (%s)
				""".formatted(placeholders))) {
			for (int index = 0; index < uniqueTaskIds.size(); index++) {
				statement.setLong(index + 1, uniqueTaskIds.get(index));
			}
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					history.get(rows.getLong("task_id")).add(rows.getLong("classroom_id"));
				}
			}
		}
		return history.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
	}

	public List<TeacherTaskDetails> findDeletedTasks(
			Connection connection, List<Long> authorizedSchoolIds) throws SQLException {
		return findTasksForSchools(connection, authorizedSchoolIds,
				"t.publication_status = 'archived' AND t.deleted_at IS NOT NULL", true);
	}

	public List<TeacherTaskDetails> findPromptTasks(Connection connection, long teacherUserId) throws SQLException {
		return findTasks(connection, teacherUserId,
				"t.publication_status IN ('draft','published','requires_update')");
	}

	private List<TeacherTaskDetails> findTasks(
			Connection connection, long teacherUserId, String publicationPredicate) throws SQLException {
		requireTeacherId(teacherUserId);
		List<Long> taskIds = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.task_id
				FROM tasks t
				WHERE t.created_by_user_id = ? AND %s AND t.deleted_at IS NULL
				ORDER BY t.updated_at DESC, t.created_at DESC, t.task_id DESC
				""".formatted(publicationPredicate))) {
			statement.setLong(1, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					taskIds.add(rows.getLong("task_id"));
				}
			}

		}
		List<TeacherTaskDetails> tasks = new ArrayList<>(taskIds.size());
		for (long taskId : taskIds) {
			findTask(connection, teacherUserId, taskId, false, publicationPredicate).ifPresent(tasks::add);
		}
		return List.copyOf(tasks);
	}

	private List<TeacherTaskDetails> findTasksForSchools(
			Connection connection,
			List<Long> authorizedSchoolIds,
			String publicationPredicate,
			boolean includeDeleted) throws SQLException {
		if (authorizedSchoolIds.isEmpty()) {
			return List.of();
		}
		List<Long> taskIds = new ArrayList<>();
		String schoolPlaceholders = String.join(",", java.util.Collections.nCopies(authorizedSchoolIds.size(), "?"));
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.task_id
				FROM tasks t
				WHERE t.school_id IN (%s) AND %s %s
				ORDER BY t.updated_at DESC, t.created_at DESC, t.task_id DESC
				""".formatted(
						schoolPlaceholders,
						publicationPredicate,
						includeDeleted ? "" : "AND t.deleted_at IS NULL"))) {
			for (int index = 0; index < authorizedSchoolIds.size(); index++) {
				statement.setLong(index + 1, authorizedSchoolIds.get(index));
			}
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					taskIds.add(rows.getLong("task_id"));
				}
			}
		}
		List<TeacherTaskDetails> tasks = new ArrayList<>(taskIds.size());
		for (long taskId : taskIds) {
			findTask(connection, null, taskId, false, publicationPredicate, includeDeleted).ifPresent(tasks::add);
		}
		return List.copyOf(tasks);
	}

	public Optional<TeacherTaskDetails> findTaskForManagement(
			Connection connection, long taskId, boolean forUpdate) throws SQLException {
		return findTask(connection, null, taskId, forUpdate, "1 = 1", true);
	}

	public boolean isTaskRevisionOf(
			Connection connection,
			long teacherUserId,
			long revisionTaskId,
			long sourceTaskId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM tasks revision
				JOIN tasks source ON source.task_code = revision.task_code
				WHERE revision.task_id = ? AND revision.supersedes_task_id = ?
				  AND revision.created_by_user_id = ? AND source.created_by_user_id = ?
				""")) {
			statement.setLong(1, revisionTaskId);
			statement.setLong(2, sourceTaskId);
			statement.setLong(3, teacherUserId);
			statement.setLong(4, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	public boolean isIndependentCopyOf(
			Connection connection,
			long teacherUserId,
			long copiedTaskId,
			long sourceTaskId,
			String requestId) throws SQLException {
		requireTeacherId(teacherUserId);
		requireTaskId(copiedTaskId);
		requireTaskId(sourceTaskId);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM audit_logs
				WHERE actor_user_id = ? AND actor_role = 'teacher' AND feature_code = ?
				  AND target_type = ? AND target_id = ? AND action_type = ?
				  AND result_status = 'success' AND request_id = ?
				  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(after_data, '$.source_task_id')) AS UNSIGNED) = ?
				LIMIT 1
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, TASK_FEATURE_CODE);
			statement.setString(3, TASK_TARGET_TYPE);
			statement.setLong(4, copiedTaskId);
			statement.setString(5, "create_independent_task_copy");
			statement.setString(6, requestId);
			statement.setLong(7, sourceTaskId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	public Optional<TeacherTaskDetails> findDraft(
			Connection connection,
			long teacherUserId,
			long taskId,
			boolean forUpdate) throws SQLException {
		return findTask(connection, teacherUserId, taskId, forUpdate, "t.publication_status = 'draft'");
	}

	public Optional<TeacherTaskDetails> findTaskForEdit(
			Connection connection,
			long teacherUserId,
			long taskId,
			boolean forUpdate) throws SQLException {
		return findTask(connection, teacherUserId, taskId, forUpdate,
				"t.publication_status IN ('draft','published','requires_update')");
	}

	public boolean hasStartedLearning(Connection connection, long taskId, boolean forUpdate) throws SQLException {
		requireTaskId(taskId);
		String lockClause = forUpdate ? " FOR UPDATE" : "";
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_class_assignment_id
				FROM task_class_assignments
				WHERE task_id = ? AND assignment_status <> 'archived'
				ORDER BY task_class_assignment_id
				""" + lockClause)) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					rows.getLong(1);
				}
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT tp.participation_id
				FROM task_participations tp
				JOIN task_class_assignments a
				  ON a.task_class_assignment_id = tp.task_class_assignment_id
				WHERE a.task_id = ? AND a.assignment_status <> 'archived'
				  AND (
				    tp.learning_status <> 'not_started'
				    OR tp.progress_status <> 'not_started'
				    OR tp.save_status <> 'unsaved'
				    OR tp.evaluation_status <> 'not_started'
				    OR tp.active_duration_seconds > 0
				    OR EXISTS (
				      SELECT 1 FROM submissions s WHERE s.participation_id = tp.participation_id
				    )
				  )
				ORDER BY tp.participation_id
				LIMIT 1
				""" + lockClause)) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	public Optional<TeacherTaskDetails> findPromptTask(
			Connection connection,
			long teacherUserId,
			long taskId,
			boolean forUpdate) throws SQLException {
		return findTask(connection, teacherUserId, taskId, forUpdate,
				"t.publication_status IN ('draft','published','requires_update')");
	}

	private Optional<TeacherTaskDetails> findTask(
			Connection connection,
			long teacherUserId,
			long taskId,
			boolean forUpdate,
			String publicationPredicate) throws SQLException {
		return findTask(connection, Long.valueOf(teacherUserId), taskId, forUpdate, publicationPredicate, false);
	}

	private Optional<TeacherTaskDetails> findTask(
			Connection connection,
			Long teacherUserId,
			long taskId,
			boolean forUpdate,
			String publicationPredicate,
			boolean includeDeleted) throws SQLException {
		if (teacherUserId != null) {
			requireTeacherId(teacherUserId);
		}
		requireTaskId(taskId);
		String ownerPredicate = teacherUserId == null ? "" : " AND t.created_by_user_id = ?";
		String sql = """
				SELECT t.task_id, t.task_code, t.task_revision_code, t.revision_number, t.version,
				       t.school_id,
				       t.created_by_user_id, creator.login_id AS created_by_login_id,
				       t.updated_by_user_id, updater.login_id AS updated_by_login_id,
				       t.created_at, t.updated_at, t.save_status, t.publication_status,
				       t.title, t.theme, t.difficulty, t.description, t.input_constraints,
				       t.creation_rules, t.initial_code,
				       r.rubric_status,
				       COALESCE(p.prompt_status, (
				         SELECT latest.prompt_status
				         FROM prompt_versions latest
				         WHERE latest.task_id = t.task_id
				         ORDER BY latest.created_at DESC, latest.prompt_version_id DESC
				         LIMIT 1
				       )) AS prompt_status
				FROM tasks t
				JOIN users creator ON creator.user_id = t.created_by_user_id
				LEFT JOIN users updater ON updater.user_id = t.updated_by_user_id
				LEFT JOIN rubrics r ON r.rubric_id = t.rubric_id
				LEFT JOIN prompt_versions p
				  ON p.prompt_version_id = t.active_prompt_version_id AND p.task_id = t.task_id
				WHERE t.task_id = ? %s AND %s %s
				""".formatted(
						ownerPredicate,
						publicationPredicate,
						includeDeleted ? "" : "AND t.deleted_at IS NULL")
				+ (forUpdate ? " FOR UPDATE" : "");
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, taskId);
			if (teacherUserId != null) {
				statement.setLong(2, teacherUserId);
			}
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return Optional.empty();
				}
				long updatedById = rows.getLong("updated_by_user_id");
				Long nullableUpdatedById = rows.wasNull() ? null : updatedById;
				boolean learningStarted = hasStartedLearning(connection, taskId, false);
				TeacherTaskDetails details = new TeacherTaskDetails(
						rows.getLong("task_id"),
						rows.getString("task_code"),
						rows.getString("task_revision_code"),
						rows.getInt("revision_number"),
						rows.getLong("version"),
						rows.getLong("created_by_user_id"),
						rows.getString("created_by_login_id"),
						nullableUpdatedById,
						rows.getString("updated_by_login_id"),
						toLocalDateTime(rows.getTimestamp("created_at")),
						toLocalDateTime(rows.getTimestamp("updated_at")),
						rows.getString("save_status"),
						rows.getString("publication_status"),
						rows.getString("rubric_status"),
						rows.getString("prompt_status"),
						learningStarted,
						loadInput(connection, taskId,
								rows.getString("title"),
								rows.getString("theme"),
								rows.getString("difficulty"),
								rows.getString("description"),
								rows.getString("input_constraints"),
								rows.getString("creation_rules"),
								rows.getString("initial_code"),
								rows.getLong("school_id")));
				return Optional.of(details);
			}
		}
	}

	public List<TeacherHintOption> findReusableHints(Connection connection, long teacherUserId) throws SQLException {
		requireTeacherId(teacherUserId);
		List<TeacherHintOption> hints = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT t.task_id, t.title AS task_title, h.hint_id, h.hint_order,
				       h.hint_title, h.hint_content, h.usage_syntax, h.hint_code
				FROM tasks t
				JOIN task_hints h ON h.task_id = t.task_id AND h.record_status = 'active'
				WHERE t.created_by_user_id = ? AND t.publication_status = 'draft'
				  AND t.deleted_at IS NULL
				ORDER BY t.title, t.task_id, h.hint_order, h.hint_id
				""")) {
			statement.setLong(1, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					hints.add(new TeacherHintOption(
							rows.getLong("task_id"),
							rows.getString("task_title"),
							rows.getLong("hint_id"),
							rows.getInt("hint_order"),
							new EditorHint(
									rows.getString("hint_title"),
									rows.getString("hint_content"),
									rows.getString("usage_syntax"),
									rows.getString("hint_code"))));
				}
			}
		}
		return List.copyOf(hints);
	}

	public long insertDraft(Connection connection, long teacherUserId, TeacherTaskInput input)
			throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		long rubricId = standardRubricIdProvider.requireActiveId(connection);
		UUID series = UUID.randomUUID();
		String taskCode = "TASK-" + series.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
		String revisionCode = taskCode + "-v1";
		long taskId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO tasks (
				  school_id, task_code, task_revision_code, revision_number, supersedes_task_id,
				  created_by_user_id, updated_by_user_id, rubric_id,
				  title, theme, difficulty, language,
				  description, input_constraints, creation_rules, initial_code, save_status,
				  publication_status, created_at, version
				) VALUES (?, ?, ?, 1, NULL, ?, NULL, ?, ?, ?, ?, 'Python', ?, ?, ?, ?, 'draft',
				  'draft', CURRENT_TIMESTAMP, 1)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, input.schoolId());
			statement.setString(2, taskCode);
			statement.setString(3, revisionCode);
			statement.setLong(4, teacherUserId);
			statement.setLong(5, rubricId);
			statement.setString(6, input.title());
			statement.setString(7, input.theme());
			statement.setString(8, databaseDifficulty(input.difficulty()));
			statement.setString(9, input.description());
			statement.setString(10, input.inputConstraints());
			statement.setString(11, input.creationRules());
			statement.setString(12, input.initialCode());
			statement.executeUpdate();
			taskId = generatedId(statement);
		}
		insertFeatures(connection, taskId, input.features());
		insertTestCases(connection, taskId, input.testCases());
		insertHints(connection, taskId, input.hints());
		insertAssignments(connection, taskId, input.classAssignments());
		return taskId;
	}

	public long insertRevision(
			Connection connection,
			long teacherUserId,
			long sourceTaskId,
			long expectedVersion,
			TeacherTaskInput input) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		requireTaskId(sourceTaskId);
		if (expectedVersion < 1 || input.classAssignments().isEmpty()) {
			throw new IllegalArgumentException("課題改訂には有効な版と対象クラスが必要です。");
		}
		TeacherTaskDetails source = findTask(connection, teacherUserId, sourceTaskId, true,
				"t.publication_status = 'published'")
				.orElseThrow(() -> new IllegalArgumentException("公開中の課題が見つかりません。"));
		if (source.version() != expectedVersion) {
			throw new IllegalArgumentException("課題が更新されています。再読み込みしてください。");
		}
		if (hasStartedLearning(connection, sourceTaskId, true)) {
			throw new IllegalArgumentException(
					"学習開始済みの生徒がいるため、同一系列の課題改訂は作成できません。");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_id
				FROM tasks
				WHERE supersedes_task_id = ? AND deleted_at IS NULL
				  AND publication_status = 'requires_update'
				LIMIT 1
				FOR UPDATE
				""")) {
			statement.setLong(1, sourceTaskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					throw new IllegalArgumentException("この課題には既に改訂案があります。既存の改訂案を編集してください。");
				}
			}
		}

		String taskCode;
		int nextRevisionNumber;
		long schoolId;
		long rubricId;
		String rubricVersion;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_code, revision_number, school_id, rubric_id, rubric_version
				FROM tasks
				WHERE task_id = ? AND created_by_user_id = ?
				  AND publication_status = 'published' AND deleted_at IS NULL AND version = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, sourceTaskId);
			statement.setLong(2, teacherUserId);
			statement.setLong(3, expectedVersion);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new IllegalArgumentException("課題の状態が更新されています。再読み込みしてください。");
				}
				taskCode = rows.getString("task_code");
				schoolId = rows.getLong("school_id");
				rubricId = rows.getLong("rubric_id");
				rubricVersion = rows.getString("rubric_version");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COALESCE(MAX(revision_number), 0) + 1
				FROM tasks
				WHERE task_code = ?
				""")) {
			statement.setString(1, taskCode);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("The next task revision number could not be determined.");
				}
				nextRevisionNumber = rows.getInt(1);
			}
		}

		long revisionTaskId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO tasks (
				  school_id, task_code, task_revision_code, revision_number, supersedes_task_id,
				  created_by_user_id, updated_by_user_id, rubric_id, rubric_version,
				  active_prompt_version_id, title, theme, difficulty, language, description,
				  input_constraints, creation_rules, initial_code, save_status, publication_status,
				  created_at, version
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, 'Python', ?, ?, ?, ?, 'draft',
				  'requires_update', CURRENT_TIMESTAMP, 1)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, schoolId);
			statement.setString(2, taskCode);
			statement.setString(3, taskCode + "-v" + nextRevisionNumber);
			statement.setInt(4, nextRevisionNumber);
			statement.setLong(5, sourceTaskId);
			statement.setLong(6, teacherUserId);
			statement.setLong(7, teacherUserId);
			statement.setLong(8, rubricId);
			statement.setString(9, rubricVersion);
			statement.setString(10, input.title());
			statement.setString(11, input.theme());
			statement.setString(12, databaseDifficulty(input.difficulty()));
			statement.setString(13, input.description());
			statement.setString(14, input.inputConstraints());
			statement.setString(15, input.creationRules());
			statement.setString(16, input.initialCode());
			statement.executeUpdate();
			revisionTaskId = generatedId(statement);
		}
		insertFeatures(connection, revisionTaskId, input.features());
		insertTestCases(connection, revisionTaskId, input.testCases());
		insertHints(connection, revisionTaskId, input.hints());
		List<ClassAssignmentInput> assignments = input.classAssignments().stream()
				.map(assignment -> new ClassAssignmentInput(
						0, assignment.classroomId(), assignment.publishAt(), assignment.dueAt(),
						assignment.lateSubmissionPolicy()))
				.toList();
		insertAssignments(connection, revisionTaskId, assignments);
		return revisionTaskId;
	}

	public void archiveTask(
			Connection connection,
			long actorUserId,
			long taskId,
			long expectedVersion) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(actorUserId);
		requireTaskId(taskId);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET publication_status = 'archived', deleted_at = CURRENT_TIMESTAMP,
				    deleted_by_user_id = ?, updated_by_user_id = ?, version = version + 1,
				    updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND publication_status IN ('draft','published','requires_update')
				  AND deleted_at IS NULL AND version = ?
				""")) {
			statement.setLong(1, actorUserId);
			statement.setLong(2, actorUserId);
			statement.setLong(3, taskId);
			statement.setLong(4, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new IllegalStateException("Task state or version changed during deletion.");
			}
		}
	}

	public void restoreTask(
			Connection connection,
			long actorUserId,
			long taskId,
			long expectedVersion) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(actorUserId);
		requireTaskId(taskId);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE task_class_assignments ca
				JOIN tasks t ON t.task_id = ca.task_id
				SET ca.assignment_status = 'archived', ca.updated_at = CURRENT_TIMESTAMP
				WHERE ca.task_id = ? AND t.publication_status = 'archived'
				  AND ca.assignment_status IN ('scheduled','published','requires_update','expired')
				""")) {
			statement.setLong(1, taskId);
			statement.executeUpdate();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET publication_status = 'draft', deleted_at = NULL, deleted_by_user_id = NULL,
				    updated_by_user_id = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND publication_status = 'archived'
				  AND deleted_at IS NOT NULL AND version = ?
				""")) {
			statement.setLong(1, actorUserId);
			statement.setLong(2, taskId);
			statement.setLong(3, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new IllegalStateException("Task state or version changed during restoration.");
			}
		}
	}

	public void updateDraft(
			Connection connection,
			long teacherUserId,
			long taskId,
			long expectedVersion,
			TeacherTaskInput input) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		if (expectedVersion < 1) {
			throw new IllegalArgumentException("A valid task version is required.");
		}
		TeacherTaskDetails current = requireEditableForUpdate(connection, teacherUserId, taskId, expectedVersion);
		if ("requires_update".equals(current.publicationStatus()) && hasStartedLearning(connection, taskId, true)) {
			throw new IllegalArgumentException("学習開始済みの課題内容は更新できません。");
		}
		long rubricId = standardRubricIdProvider.requireActiveId(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET updated_by_user_id = ?, rubric_id = COALESCE(rubric_id, ?),
				    title = ?, theme = ?, difficulty = ?, language = 'Python',
				    description = ?, input_constraints = ?, creation_rules = ?, initial_code = ?,
				    save_status = 'draft', version = version + 1, updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND created_by_user_id = ?
				  AND publication_status = ? AND deleted_at IS NULL AND version = ?
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, rubricId);
			statement.setString(3, input.title());
			statement.setString(4, input.theme());
			statement.setString(5, databaseDifficulty(input.difficulty()));
			statement.setString(6, input.description());
			statement.setString(7, input.inputConstraints());
			statement.setString(8, input.creationRules());
			statement.setString(9, input.initialCode());
			statement.setLong(10, taskId);
			statement.setLong(11, teacherUserId);
			statement.setString(12, current.publicationStatus());
			statement.setLong(13, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Task draft update lost its version or ownership condition.");
			}
		}
		updateFeatures(connection, taskId, input.features());
		updateTestCases(connection, taskId, input.testCases());
		updateHints(connection, taskId, input.hints());
		updateAssignments(connection, taskId, input.classAssignments());
	}

	public void clearActivePromptVersion(
			Connection connection,
			long teacherUserId,
			long taskId,
			long expectedVersion) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET active_prompt_version_id = NULL
				WHERE task_id = ? AND created_by_user_id = ?
				  AND publication_status IN ('draft','requires_update')
				  AND deleted_at IS NULL AND version = ?
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherUserId);
			statement.setLong(3, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Task prompt invalidation lost its version or ownership condition.");
			}
		}
	}

	public PublicationResult publishDraft(
			Connection connection,
			long teacherUserId,
			long taskId,
			long expectedVersion,
			int expectedAssignmentCount) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		if (expectedVersion < 1 || expectedAssignmentCount < 1) {
			throw new IllegalArgumentException("課題の公開条件が不足しています。");
		}

		Long supersedesTaskId = null;
		String currentTaskStatus;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT publication_status, supersedes_task_id
				FROM tasks
				WHERE task_id = ? AND created_by_user_id = ? AND deleted_at IS NULL
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new IllegalArgumentException("課題が見つからないか、公開できません。");
				}
				currentTaskStatus = rows.getString("publication_status");
				long value = rows.getLong("supersedes_task_id");
				if (!rows.wasNull()) {
					supersedesTaskId = value;
				}
			}
		}
		if (!"draft".equals(currentTaskStatus) && !"requires_update".equals(currentTaskStatus)) {
			throw new IllegalArgumentException("課題の状態が更新されています。再読み込みしてください。");
		}
		if ("requires_update".equals(currentTaskStatus)) {
			if (supersedesTaskId == null) {
				throw new IllegalArgumentException("公開元の課題改訂を確認できません。");
			}
			findTask(connection, teacherUserId, supersedesTaskId, true,
					"t.publication_status = 'published'")
					.orElseThrow(() -> new IllegalArgumentException(
							"公開元の課題状態が更新されています。再読み込みしてください。"));
			if (hasStartedLearning(connection, supersedesTaskId, true)) {
				throw new IllegalArgumentException(
						"改訂案の作成後に学習を開始した生徒がいるため、再公開できません。既存の課題を維持してください。");
			}
		}

		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT r.rubric_status, p.prompt_status, p.evaluation_examples_status,
				       t.publication_status, t.supersedes_task_id
				FROM tasks t
				LEFT JOIN rubrics r ON r.rubric_id = t.rubric_id
				LEFT JOIN prompt_versions p
				  ON p.prompt_version_id = t.active_prompt_version_id AND p.task_id = t.task_id
				WHERE t.task_id = ? AND t.created_by_user_id = ?
				  AND t.publication_status IN ('draft','requires_update') AND t.deleted_at IS NULL
				  AND t.version = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherUserId);
			statement.setLong(3, expectedVersion);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new IllegalArgumentException("課題の状態が更新されています。再読み込みしてください。");
				}
				if (!"active".equals(rows.getString("rubric_status"))
						|| !List.of("configured", "versioned").contains(rows.getString("prompt_status"))
						|| !"completed".equals(rows.getString("evaluation_examples_status"))) {
					throw new IllegalArgumentException(
							"公開前に有効な標準ルーブリックと、評価例の生成が完了したプロンプト版を設定してください。");
				}
				if (!currentTaskStatus.equals(rows.getString("publication_status"))) {
					throw new IllegalArgumentException("課題の状態が更新されています。再読み込みしてください。");
				}
			}
		}

		int immediateCount = 0;
		int scheduledCount = 0;
		Timestamp databaseNow = currentDatabaseTimestamp(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_class_assignment_id, publish_at, due_at
				FROM task_class_assignments
				WHERE task_id = ? AND assignment_status = 'not_published'
				ORDER BY task_class_assignment_id
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				int assignmentCount = 0;
				while (rows.next()) {
					assignmentCount++;
					Timestamp publishAt = rows.getTimestamp("publish_at");
					Timestamp dueAt = rows.getTimestamp("due_at");
					if (dueAt != null && !dueAt.after(databaseNow)) {
						throw new IllegalArgumentException("提出期限は公開処理時刻より後に設定してください。");
					}
					if (publishAt == null || !publishAt.after(databaseNow)) {
						immediateCount++;
					} else {
						scheduledCount++;
					}
				}
				if (assignmentCount != expectedAssignmentCount) {
					throw new IllegalArgumentException("公開対象のクラス割当を確認できません。課題を再読み込みしてください。");
				}
			}
		}

		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE task_class_assignments
				SET assignment_status = CASE
				      WHEN publish_at IS NULL OR publish_at <= CURRENT_TIMESTAMP THEN 'published'
				      ELSE 'scheduled'
				    END,
				    updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND assignment_status = 'not_published'
				  AND (due_at IS NULL OR due_at > CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, taskId);
			if (statement.executeUpdate() != expectedAssignmentCount) {
				throw new SQLException("Task assignment publication did not update the expected rows.");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET save_status = 'saved', publication_status = 'published', published_at = CURRENT_TIMESTAMP,
				    updated_by_user_id = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND created_by_user_id = ? AND publication_status = ?
				  AND deleted_at IS NULL AND version = ?
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, taskId);
			statement.setLong(3, teacherUserId);
			statement.setString(4, currentTaskStatus);
			statement.setLong(5, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Task publication lost its version, ownership, or state condition.");
			}
		}
		if (supersedesTaskId != null) {
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE task_class_assignments
					SET assignment_status = 'archived', updated_at = CURRENT_TIMESTAMP
					WHERE task_id = ? AND assignment_status <> 'archived'
					""")) {
				statement.setLong(1, supersedesTaskId);
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE tasks
					SET publication_status = 'requires_update', updated_by_user_id = ?,
					    version = version + 1, updated_at = CURRENT_TIMESTAMP
					WHERE task_id = ? AND created_by_user_id = ?
					  AND publication_status = 'published' AND deleted_at IS NULL
					""")) {
				statement.setLong(1, teacherUserId);
				statement.setLong(2, supersedesTaskId);
				statement.setLong(3, teacherUserId);
				if (statement.executeUpdate() != 1) {
					throw new SQLException("The previous task revision could not be retired.");
				}
			}
		}
		return new PublicationResult(immediateCount, scheduledCount, expectedVersion + 1);
	}

	private static Timestamp currentDatabaseTimestamp(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
				ResultSet rows = statement.executeQuery()) {
			if (!rows.next()) {
				throw new SQLException("The database did not return its current timestamp.");
			}
			return rows.getTimestamp(1);
		}
	}

	public void recordAudit(
			Connection connection,
			long teacherUserId,
			long taskId,
			String actionType,
			String requestId,
			String detail,
			String beforeJson,
			String afterJson) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		if (!List.of(
				"create_draft", "update_draft", "publish_task", "create_task_revision",
				"create_independent_task_copy", "delete_task", "restore_task",
				"extend_task_assignment_deadline", "add_task_class_assignment")
				.contains(actionType)) {
			throw new IllegalArgumentException("Unsupported task audit action.");
		}
		if (requestId == null || requestId.isBlank()) {
			throw new IllegalArgumentException("A request identifier is required for task auditing.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id, action_type,
				  result_status, detail, before_data, after_data, request_id, occurred_at
				) VALUES (?, 'teacher', ?, ?, ?, ?, 'success', ?, CAST(? AS JSON), CAST(? AS JSON), ?,
				  CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, TASK_FEATURE_CODE);
			statement.setString(3, TASK_TARGET_TYPE);
			statement.setLong(4, taskId);
			statement.setString(5, actionType);
			statement.setString(6, detail);
			statement.setString(7, beforeJson);
			statement.setString(8, afterJson);
			statement.setString(9, requestId);
			statement.executeUpdate();
		}
	}

	public void recordFailureAudit(
			Connection connection,
			long teacherUserId,
			Long taskId,
			String actionType,
			String requestId,
			String errorCode,
			String errorMessage) throws SQLException {
		requireWriteTransaction(connection);
		requireTeacherId(teacherUserId);
		if (!List.of(
				"create_draft", "update_draft", "publish_task", "create_task_revision",
				"create_independent_task_copy", "delete_task", "restore_task",
				"extend_task_assignment_deadline", "add_task_class_assignment")
				.contains(actionType)) {
			throw new IllegalArgumentException("Unsupported task audit action.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id, action_type,
				  result_status, error_code, error_message, request_id, occurred_at
				) VALUES (?, 'teacher', ?, ?, ?, ?, 'failure', ?, ?, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, TASK_FEATURE_CODE);
			statement.setString(3, TASK_TARGET_TYPE);
			if (taskId == null || taskId < 1) {
				statement.setNull(4, java.sql.Types.BIGINT);
			} else {
				statement.setLong(4, taskId);
			}
			statement.setString(5, actionType);
			statement.setString(6, errorCode);
			statement.setString(7, errorMessage);
			statement.setString(8, requestId);
			statement.executeUpdate();
		}
	}

	public Optional<Long> findSuccessfulDraftCreation(
			Connection connection,
			long teacherUserId,
			String requestId) throws SQLException {
		return findSuccessfulTaskRequest(connection, teacherUserId, "create_draft", requestId);
	}

	public Optional<Long> findSuccessfulTaskRequest(
			Connection connection,
			long teacherUserId,
			String actionType,
			String requestId) throws SQLException {
		requireTeacherId(teacherUserId);
		if (!List.of(
				"create_draft", "publish_task", "create_task_revision",
				"create_independent_task_copy", "extend_task_assignment_deadline",
				"add_task_class_assignment").contains(actionType)) {
			throw new IllegalArgumentException("Unsupported task request action.");
		}
		if (requestId == null || requestId.isBlank()) {
			throw new IllegalArgumentException("A request identifier is required for task creation.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT target_id
				FROM audit_logs
				WHERE actor_user_id = ? AND actor_role = 'teacher' AND feature_code = ?
				  AND target_type = ? AND action_type = ?
				  AND result_status = 'success' AND request_id = ? AND target_id IS NOT NULL
				ORDER BY audit_log_id DESC
				LIMIT 1
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, TASK_FEATURE_CODE);
			statement.setString(3, TASK_TARGET_TYPE);
			statement.setString(4, actionType);
			statement.setString(5, requestId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? Optional.of(rows.getLong("target_id")) : Optional.empty();
			}
		}
	}

	public record PublicationResult(
			int immediatelyPublishedAssignments,
			int scheduledAssignments,
			long taskVersion) {
	}

	public record PublishedTaskState(long schoolId, String publicationStatus, long version, boolean deleted) {
	}

	public record AssignmentState(long classroomId, String assignmentStatus, LocalDateTime dueAt) {
	}

	public Optional<PublishedTaskState> lockPublishedTaskState(Connection connection, long taskId)
			throws SQLException {
		requireTaskId(taskId);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT school_id, publication_status, version, deleted_at
				FROM tasks WHERE task_id = ? FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return Optional.empty();
				}
				return Optional.of(new PublishedTaskState(
						rows.getLong("school_id"),
						rows.getString("publication_status"),
						rows.getLong("version"),
						rows.getTimestamp("deleted_at") != null));
			}
		}
	}

	public Optional<AssignmentState> lockTaskAssignment(
			Connection connection, long taskId, long assignmentId) throws SQLException {
		requireTaskId(taskId);
		if (assignmentId < 1) {
			throw new IllegalArgumentException("A valid task assignment is required.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT classroom_id, assignment_status, due_at
				FROM task_class_assignments
				WHERE task_id = ? AND task_class_assignment_id = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, assignmentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return Optional.empty();
				}
				Timestamp dueAt = rows.getTimestamp("due_at");
				return Optional.of(new AssignmentState(
						rows.getLong("classroom_id"),
						rows.getString("assignment_status"),
						dueAt == null ? null : dueAt.toLocalDateTime()));
			}
		}
	}

	public boolean hasAnyTaskAssignmentForClass(Connection connection, long taskId, long classroomId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1 FROM task_class_assignments
				WHERE task_id = ? AND classroom_id = ?
				LIMIT 1
				""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, classroomId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	public LocalDateTime databaseCurrentDateTime(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
				ResultSet rows = statement.executeQuery()) {
			if (!rows.next()) {
				throw new SQLException("The database did not return its current timestamp.");
			}
			return rows.getTimestamp(1).toLocalDateTime();
		}
	}

	public void extendAssignmentDeadline(
			Connection connection, long taskId, long assignmentId, LocalDateTime dueAt) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE task_class_assignments
				SET due_at = ?,
				    assignment_status = CASE WHEN assignment_status = 'expired' THEN 'published'
				                             ELSE assignment_status END,
				    updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND task_class_assignment_id = ?
				  AND assignment_status IN ('scheduled','published','expired')
				""")) {
			statement.setTimestamp(1, Timestamp.valueOf(dueAt));
			statement.setLong(2, taskId);
			statement.setLong(3, assignmentId);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Task assignment deadline update affected an unexpected row count.");
			}
		}
	}

	public long insertPublishedTaskAssignment(
			Connection connection,
			long taskId,
			long classroomId,
			String assignmentStatus,
			LocalDateTime publishAt,
			LocalDateTime dueAt,
			TeacherTaskInput.LateSubmissionPolicy lateSubmissionPolicy) throws SQLException {
		if (!Set.of("published", "scheduled").contains(assignmentStatus)) {
			throw new IllegalArgumentException("Unsupported published task assignment state.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_class_assignments (
				  task_id, classroom_id, assignment_status, publish_at, due_at,
				  late_submission_policy, resubmission_policy, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, 'allow', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, taskId);
			statement.setLong(2, classroomId);
			statement.setString(3, assignmentStatus);
			statement.setTimestamp(4, toTimestamp(publishAt));
			statement.setTimestamp(5, toTimestamp(dueAt));
			statement.setString(6, lateSubmissionPolicy.databaseValue());
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Task class assignment was not inserted.");
			}
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The new task class assignment ID was not returned.");
				}
				return keys.getLong(1);
			}
		}
	}

	public long advancePublishedTaskVersion(
			Connection connection, long teacherUserId, long taskId, long expectedVersion) throws SQLException {
		if (expectedVersion == Long.MAX_VALUE) {
			throw new IllegalStateException("Task version has reached its maximum value.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET version = version + 1, updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND publication_status = 'published'
				  AND deleted_at IS NULL AND version = ?
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, taskId);
			statement.setLong(3, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Published task version update affected an unexpected row count.");
			}
		}
		return expectedVersion + 1;
	}

	public List<TeacherTaskAuditEntry> findAuditEntries(
			Connection connection,
			long teacherUserId,
			long taskId) throws SQLException {
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		if (findTaskForManagement(connection, taskId, false)
				.filter(task -> task.createdByUserId() == teacherUserId)
				.isEmpty()) {
			return List.of();
		}
		List<TeacherTaskAuditEntry> entries = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT a.audit_log_id, a.actor_user_id, u.login_id, a.action_type, a.result_status,
				       a.error_code, a.error_message, a.detail, a.occurred_at
				FROM audit_logs a
				JOIN users u ON u.user_id = a.actor_user_id
				WHERE a.actor_role = 'teacher' AND a.feature_code = ?
				  AND a.target_type = ? AND a.target_id = ?
				  AND a.actor_user_id = ?
				ORDER BY a.occurred_at DESC, a.audit_log_id DESC
				""")) {
			statement.setString(1, TASK_FEATURE_CODE);
			statement.setString(2, TASK_TARGET_TYPE);
			statement.setLong(3, taskId);
			statement.setLong(4, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					entries.add(new TeacherTaskAuditEntry(
							rows.getLong("audit_log_id"),
							rows.getLong("actor_user_id"),
							rows.getString("login_id"),
							rows.getString("action_type"),
							rows.getString("result_status"),
							rows.getString("error_code"),
							rows.getString("error_message"),
							rows.getString("detail"),
							toLocalDateTime(rows.getTimestamp("occurred_at"))));
				}
			}
		}
		return List.copyOf(entries);
	}

	private TeacherTaskDetails requireEditableForUpdate(
			Connection connection,
			long teacherUserId,
			long taskId,
			long expectedVersion) throws SQLException {
		TeacherTaskDetails observed = findTaskForEdit(connection, teacherUserId, taskId, false)
				.filter(task -> "draft".equals(task.publicationStatus())
						|| "requires_update".equals(task.publicationStatus()))
				.orElseThrow(() -> new IllegalArgumentException("課題が見つからないか、編集できません。"));
		Long sourceTaskId = null;
		if ("requires_update".equals(observed.publicationStatus())) {
			sourceTaskId = findSupersededTaskId(connection, taskId);
			if (sourceTaskId == null
					|| findTask(connection, teacherUserId, sourceTaskId, true,
							"t.publication_status = 'published'").isEmpty()
					|| hasStartedLearning(connection, sourceTaskId, true)) {
				throw new IllegalArgumentException(
						"公開元の課題に学習開始済みの生徒がいるか、状態が更新されているため編集できません。");
			}
		}
		TeacherTaskDetails current = findTaskForEdit(connection, teacherUserId, taskId, true)
				.filter(task -> "draft".equals(task.publicationStatus())
						|| "requires_update".equals(task.publicationStatus()))
				.orElseThrow(() -> new IllegalArgumentException("課題が見つからないか、編集できません。"));
		if (current.version() != expectedVersion) {
			throw new IllegalArgumentException("課題が更新されています。画面を読み込み直してください。");
		}
		if (!current.publicationStatus().equals(observed.publicationStatus())
				|| ("requires_update".equals(current.publicationStatus())
						&& !java.util.Objects.equals(sourceTaskId, findSupersededTaskId(connection, taskId)))) {
			throw new IllegalArgumentException("課題の状態が更新されています。再読み込みしてください。");
		}
		if ("requires_update".equals(current.publicationStatus())) {
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT task_id
					FROM tasks
					WHERE task_code = ? AND revision_number > ? AND deleted_at IS NULL
					  AND publication_status IN ('published','requires_update')
					ORDER BY revision_number
					LIMIT 1
					FOR UPDATE
					""")) {
				statement.setString(1, current.taskCode());
				statement.setInt(2, current.revisionNumber());
				try (ResultSet rows = statement.executeQuery()) {
					if (rows.next()) {
						throw new IllegalArgumentException("この課題改訂は既に後続版へ置き換えられています。");
					}
				}
			}
		}
		return current;
	}

	private Long findSupersededTaskId(Connection connection, long taskId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT supersedes_task_id FROM tasks WHERE task_id = ?")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				long value = rows.getLong(1);
				return rows.wasNull() ? null : value;
			}
		}
	}

	private TeacherTaskInput loadInput(
			Connection connection,
			long taskId,
			String title,
			String theme,
			String difficulty,
			String description,
			String inputConstraints,
			String creationRules,
			String initialCode,
			long schoolId) throws SQLException {
		List<String> features = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT feature_text
				FROM task_features
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY sort_order, feature_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					features.add(rows.getString("feature_text"));
				}
			}
		}

		List<EditorTestCase> testCases = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT test_case_id, title, test_case_input, test_case_output, test_case_order
				FROM task_test_cases
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY test_case_order, test_case_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					testCases.add(new EditorTestCase(
							rows.getLong("test_case_id"),
							rows.getString("title"),
							rows.getString("test_case_input"),
							rows.getString("test_case_output"),
							rows.getInt("test_case_order")));
				}
			}
		}

		List<HintInput> hints = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT hint_id, hint_order, hint_title, hint_content, usage_syntax, hint_code
				FROM task_hints
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY hint_order, hint_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					hints.add(new HintInput(
							rows.getLong("hint_id"),
							rows.getInt("hint_order"),
							new EditorHint(
									rows.getString("hint_title"),
									rows.getString("hint_content"),
									rows.getString("usage_syntax"),
									rows.getString("hint_code"))));
				}
			}
		}

		List<ClassAssignmentInput> assignments = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_class_assignment_id, classroom_id, assignment_status, publish_at, due_at,
				       late_submission_policy
				FROM task_class_assignments
				WHERE task_id = ? AND assignment_status <> 'archived'
				ORDER BY classroom_id, task_class_assignment_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					assignments.add(new ClassAssignmentInput(
							rows.getLong("task_class_assignment_id"),
							rows.getLong("classroom_id"),
							toLocalDateTime(rows.getTimestamp("publish_at")),
							toLocalDateTime(rows.getTimestamp("due_at")),
							TeacherTaskInput.LateSubmissionPolicy.fromDatabaseValue(
									rows.getString("late_submission_policy")),
							rows.getString("assignment_status")));
				}
			}
		}

		return new TeacherTaskInput(
				title,
				theme,
				difficulty == null ? null : TeacherTaskInput.Difficulty.fromDatabaseValue(difficulty),
				description,
				inputConstraints,
				creationRules,
				initialCode,
				features, testCases, hints, assignments, schoolId);
	}

	private void insertFeatures(Connection connection, long taskId, List<String> features) throws SQLException {
		if (features.isEmpty()) {
			return;
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_features (task_id, feature_text, sort_order, record_status)
				VALUES (?, ?, ?, 'active')
				""")) {
			for (int index = 0; index < features.size(); index++) {
				statement.setLong(1, taskId);
				statement.setString(2, features.get(index));
				statement.setInt(3, index + 1);
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void insertTestCases(Connection connection, long taskId, List<EditorTestCase> testCases)
			throws SQLException {
		for (int index = 0; index < testCases.size(); index++) {
			insertTestCase(connection, taskId, testCases.get(index), index + 1);
		}
	}

	private void insertHints(Connection connection, long taskId, List<HintInput> hints) throws SQLException {
		for (int index = 0; index < hints.size(); index++) {
			insertHint(connection, taskId, hints.get(index), index + 1);
		}
	}

	private void insertTestCase(Connection connection, long taskId, EditorTestCase testCase, int order)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_test_cases (
				  task_id, title, test_case_input, test_case_output, test_case_order, record_status
				) VALUES (?, ?, ?, ?, ?, 'active')
				""")) {
			bindTestCase(statement, taskId, testCase, order);
			statement.executeUpdate();
		}
	}

	private void insertHint(Connection connection, long taskId, HintInput hint, int order) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_hints (
				  task_id, hint_order, hint_title, hint_content, usage_syntax, hint_code,
				  created_at, record_status
				) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'active')
				""")) {
			bindHint(statement, taskId, hint, order);
			statement.executeUpdate();
		}
	}

	private void insertAssignments(Connection connection, long taskId, List<ClassAssignmentInput> assignments)
			throws SQLException {
		Set<Long> classroomIds = validateUniqueClassrooms(assignments);
		if (classroomIds.isEmpty()) {
			return;
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_class_assignments (
				  task_id, classroom_id, assignment_status, publish_at, due_at,
				  late_submission_policy, resubmission_policy, created_at
				) VALUES (?, ?, 'not_published', ?, ?, ?, 'allow', CURRENT_TIMESTAMP)
				""")) {
			for (ClassAssignmentInput assignment : assignments) {
				if (assignment.assignmentId() != 0) {
					throw new IllegalArgumentException("New task assignments cannot include database identifiers.");
				}
				bindAssignment(statement, taskId, assignment);
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void updateFeatures(Connection connection, long taskId, List<String> features) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE task_features SET record_status = 'inactive'
				WHERE task_id = ? AND record_status = 'active'
				""")) {
			statement.setLong(1, taskId);
			statement.executeUpdate();
		}
		insertFeatures(connection, taskId, features);
	}

	private void updateTestCases(Connection connection, long taskId, List<EditorTestCase> testCases)
			throws SQLException {
		Map<Long, EditorTestCase> existing = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT test_case_id
				FROM task_test_cases
				WHERE task_id = ? AND record_status = 'active'
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					existing.put(rows.getLong("test_case_id"), null);
				}
			}
		}

		Set<Long> retained = new HashSet<>();
		for (int index = 0; index < testCases.size(); index++) {
			EditorTestCase testCase = testCases.get(index);
			if (testCase.getTestCaseId() <= 0) {
				insertTestCase(connection, taskId, testCase, index + 1);
			} else {
				if (!existing.containsKey(testCase.getTestCaseId()) || !retained.add(testCase.getTestCaseId())) {
					throw new IllegalArgumentException("A test case does not belong to this draft.");
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_test_cases
						SET title = ?, test_case_input = ?, test_case_output = ?,
						    test_case_order = ?, record_status = 'active'
						WHERE task_id = ? AND test_case_id = ? AND record_status = 'active'
						""")) {
					statement.setString(1, testCase.getTitle());
					statement.setString(2, testCase.getInput());
					statement.setString(3, testCase.getExpectedOutput());
					statement.setInt(4, index + 1);
					statement.setLong(5, taskId);
					statement.setLong(6, testCase.getTestCaseId());
					if (statement.executeUpdate() != 1) {
						throw new SQLException("Task test case update affected an unexpected row count.");
					}
				}
			}
		}
		inactivateMissingRows(connection, "task_test_cases", "test_case_id", taskId, existing.keySet(), retained);
	}

	private void updateHints(Connection connection, long taskId, List<HintInput> hints) throws SQLException {
		Map<Long, Boolean> existing = findActiveChildIds(connection, "task_hints", "hint_id", taskId);
		Set<Long> retained = new HashSet<>();
		for (int index = 0; index < hints.size(); index++) {
			HintInput hint = hints.get(index);
			if (hint.hintId() <= 0) {
				insertHint(connection, taskId, hint, index + 1);
			} else {
				if (!existing.containsKey(hint.hintId()) || !retained.add(hint.hintId())) {
					throw new IllegalArgumentException("A hint does not belong to this draft.");
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_hints
						SET hint_order = ?, hint_title = ?, hint_content = ?, usage_syntax = ?,
						    hint_code = ?, updated_at = CURRENT_TIMESTAMP
						WHERE task_id = ? AND hint_id = ? AND record_status = 'active'
						""")) {
					bindHintFields(statement, hint, index + 1);
					statement.setLong(6, taskId);
					statement.setLong(7, hint.hintId());
					if (statement.executeUpdate() != 1) {
						throw new SQLException("Task hint update affected an unexpected row count.");
					}
				}
			}
		}
		inactivateMissingRows(connection, "task_hints", "hint_id", taskId, existing.keySet(), retained);
	}

	private void updateAssignments(
			Connection connection,
			long taskId,
			List<ClassAssignmentInput> assignments) throws SQLException {
		validateUniqueClassrooms(assignments);
		Map<Long, Long> assignmentIdsByClassroom = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_class_assignment_id, classroom_id
				FROM task_class_assignments
				WHERE task_id = ? AND assignment_status = 'not_published'
				FOR UPDATE
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					assignmentIdsByClassroom.put(rows.getLong("classroom_id"),
							rows.getLong("task_class_assignment_id"));
				}
			}
		}

		Set<Long> retainedClassrooms = new HashSet<>();
		for (ClassAssignmentInput assignment : assignments) {
			if (!retainedClassrooms.add(assignment.classroomId())) {
				throw new IllegalArgumentException("A classroom can only be assigned once.");
			}
			Long existingId = assignmentIdsByClassroom.get(assignment.classroomId());
			if (assignment.assignmentId() > 0
					&& (existingId == null || assignment.assignmentId() != existingId.longValue())) {
				throw new IllegalArgumentException("A class assignment does not belong to this draft.");
			}
			if (existingId == null) {
				if (assignment.assignmentId() != 0) {
					throw new IllegalArgumentException("A class assignment does not belong to this draft.");
				}
				insertAssignments(connection, taskId, List.of(assignment));
			} else {
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET assignment_status = 'not_published', publish_at = ?, due_at = ?,
						    late_submission_policy = ?, updated_at = CURRENT_TIMESTAMP
						WHERE task_id = ? AND task_class_assignment_id = ?
						  AND classroom_id = ? AND assignment_status = 'not_published'
						""")) {
					statement.setTimestamp(1, toTimestamp(assignment.publishAt()));
					statement.setTimestamp(2, toTimestamp(assignment.dueAt()));
					statement.setString(3, assignment.lateSubmissionPolicy().databaseValue());
					statement.setLong(4, taskId);
					statement.setLong(5, existingId);
					statement.setLong(6, assignment.classroomId());
					if (statement.executeUpdate() != 1) {
						throw new SQLException("Task class assignment update affected an unexpected row count.");
					}
				}
			}
		}

		for (Map.Entry<Long, Long> entry : assignmentIdsByClassroom.entrySet()) {
			if (!retainedClassrooms.contains(entry.getKey())) {
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET assignment_status = 'archived', updated_at = CURRENT_TIMESTAMP
						WHERE task_id = ? AND task_class_assignment_id = ? AND assignment_status = 'not_published'
						""")) {
					statement.setLong(1, taskId);
					statement.setLong(2, entry.getValue());
					statement.executeUpdate();
				}
			}
		}
	}

	private void inactivateMissingRows(
			Connection connection,
			String table,
			String idColumn,
			long taskId,
			Set<Long> existingIds,
			Set<Long> retained) throws SQLException {
		for (long id : existingIds) {
			if (!retained.contains(id)) {
				try (PreparedStatement statement = connection.prepareStatement(
						"UPDATE " + table + " SET record_status = 'inactive' WHERE task_id = ? AND "
								+ idColumn + " = ? AND record_status = 'active'")) {
					statement.setLong(1, taskId);
					statement.setLong(2, id);
					statement.executeUpdate();
				}
			}
		}
	}

	private Map<Long, Boolean> findActiveChildIds(Connection connection, String table, String idColumn, long taskId)
			throws SQLException {
		Map<Long, Boolean> ids = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT " + idColumn + " FROM " + table
						+ " WHERE task_id = ? AND record_status = 'active' FOR UPDATE")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					ids.put(rows.getLong(1), Boolean.TRUE);
				}
			}
		}
		return ids;
	}

	private void bindTestCase(PreparedStatement statement, long taskId, EditorTestCase testCase, int order)
			throws SQLException {
		statement.setLong(1, taskId);
		statement.setString(2, testCase.getTitle());
		statement.setString(3, testCase.getInput());
		statement.setString(4, testCase.getExpectedOutput());
		statement.setInt(5, order);
	}

	private void bindHint(PreparedStatement statement, long taskId, HintInput hint, int order) throws SQLException {
		statement.setLong(1, taskId);
		EditorHint value = hint.hint();
		statement.setInt(2, order);
		statement.setString(3, value.getTitle());
		statement.setString(4, value.getContent());
		statement.setString(5, value.getUsageSyntax());
		statement.setString(6, value.getCode());
	}

	private void bindHintFields(PreparedStatement statement, HintInput hint, int order) throws SQLException {
		EditorHint value = hint.hint();
		statement.setInt(1, order);
		statement.setString(2, value.getTitle());
		statement.setString(3, value.getContent());
		statement.setString(4, value.getUsageSyntax());
		statement.setString(5, value.getCode());
	}

	private void bindAssignment(
			PreparedStatement statement,
			long taskId,
			ClassAssignmentInput assignment) throws SQLException {
		statement.setLong(1, taskId);
		statement.setLong(2, assignment.classroomId());
		statement.setTimestamp(3, toTimestamp(assignment.publishAt()));
		statement.setTimestamp(4, toTimestamp(assignment.dueAt()));
		statement.setString(5, assignment.lateSubmissionPolicy().databaseValue());
	}

	private Set<Long> validateUniqueClassrooms(List<ClassAssignmentInput> assignments) {
		Set<Long> classroomIds = new HashSet<>();
		for (ClassAssignmentInput assignment : assignments) {
			if (assignment.classroomId() < 1 || !classroomIds.add(assignment.classroomId())) {
				throw new IllegalArgumentException("Each task assignment must use a unique valid classroom.");
			}
		}
		return classroomIds;
	}

	private static String databaseDifficulty(TeacherTaskInput.Difficulty difficulty) {
		return difficulty == null ? null : difficulty.databaseValue();
	}

	private static long generatedId(PreparedStatement statement) throws SQLException {
		try (ResultSet keys = statement.getGeneratedKeys()) {
			if (!keys.next()) {
				throw new SQLException("Task insert did not return an identifier.");
			}
			return keys.getLong(1);
		}
	}

	private static Timestamp toTimestamp(LocalDateTime value) {
		return value == null ? null : Timestamp.valueOf(value);
	}

	private static LocalDateTime toLocalDateTime(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private static void requireWriteTransaction(Connection connection) throws SQLException {
		if (connection == null) {
			throw new IllegalArgumentException("A database connection is required.");
		}
		if (connection.getAutoCommit()) {
			throw new IllegalStateException("Task draft writes require an active transaction.");
		}
	}

	private static void requireTeacherId(long teacherUserId) {
		if (teacherUserId < 1) {
			throw new SecurityException("Active teacher access is required.");
		}
	}

	private static void requireTaskId(long taskId) {
		if (taskId < 1) {
			throw new IllegalArgumentException("A valid task identifier is required.");
		}
	}
}
