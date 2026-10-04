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
	private static final String TASK_FEATURE_CODE = "task-management";
	private static final String TASK_TARGET_TYPE = "task";

	public List<TeacherTaskDetails> findDrafts(Connection connection, long teacherUserId) throws SQLException {
		requireTeacherId(teacherUserId);
		List<Long> taskIds = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT task_id
				FROM tasks
				WHERE created_by_user_id = ? AND publication_status = 'draft'
				  AND deleted_at IS NULL
				ORDER BY updated_at DESC, created_at DESC, task_id DESC
				""")) {
			statement.setLong(1, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					taskIds.add(rows.getLong("task_id"));
				}
			}
		}
		List<TeacherTaskDetails> tasks = new ArrayList<>(taskIds.size());
		for (long taskId : taskIds) {
			findTask(connection, teacherUserId, taskId, false).ifPresent(tasks::add);
		}
		return List.copyOf(tasks);
	}

	public Optional<TeacherTaskDetails> findDraft(
			Connection connection,
			long teacherUserId,
			long taskId,
			boolean forUpdate) throws SQLException {
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		String sql = """
				SELECT t.task_id, t.task_code, t.task_revision_code, t.revision_number, t.version,
				       t.created_by_user_id, creator.login_id AS created_by_login_id,
				       t.updated_by_user_id, updater.login_id AS updated_by_login_id,
				       t.created_at, t.updated_at, t.save_status, t.publication_status,
				       t.title, t.theme, t.difficulty, t.description, t.input_constraints,
				       t.creation_rules, t.initial_code,
				       r.rubric_status, p.prompt_status
				FROM tasks t
				JOIN users creator ON creator.user_id = t.created_by_user_id
				LEFT JOIN users updater ON updater.user_id = t.updated_by_user_id
				LEFT JOIN rubrics r ON r.rubric_id = t.rubric_id
				LEFT JOIN prompt_versions p
				  ON p.prompt_version_id = t.active_prompt_version_id AND p.task_id = t.task_id
				WHERE t.task_id = ? AND t.created_by_user_id = ?
				  AND t.publication_status = 'draft' AND t.deleted_at IS NULL
				""" + (forUpdate ? " FOR UPDATE" : "");
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					return Optional.empty();
				}
				long updatedById = rows.getLong("updated_by_user_id");
				Long nullableUpdatedById = rows.wasNull() ? null : updatedById;
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
						loadInput(connection, taskId,
								rows.getString("title"),
								rows.getString("theme"),
								rows.getString("difficulty"),
								rows.getString("description"),
								rows.getString("input_constraints"),
								rows.getString("creation_rules"),
								rows.getString("initial_code")));
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
		UUID series = UUID.randomUUID();
		String taskCode = "TASK-" + series.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
		String revisionCode = taskCode + "-v1";
		long taskId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO tasks (
				  task_code, task_revision_code, revision_number, supersedes_task_id,
				  created_by_user_id, updated_by_user_id, title, theme, difficulty, language,
				  description, input_constraints, creation_rules, initial_code, save_status,
				  publication_status, created_at, version
				) VALUES (?, ?, 1, NULL, ?, NULL, ?, ?, ?, 'Python', ?, ?, ?, ?, 'draft',
				  'draft', CURRENT_TIMESTAMP, 1)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, taskCode);
			statement.setString(2, revisionCode);
			statement.setLong(3, teacherUserId);
			statement.setString(4, input.title());
			statement.setString(5, input.theme());
			statement.setString(6, databaseDifficulty(input.difficulty()));
			statement.setString(7, input.description());
			statement.setString(8, input.inputConstraints());
			statement.setString(9, input.creationRules());
			statement.setString(10, input.initialCode());
			statement.executeUpdate();
			taskId = generatedId(statement);
		}
		insertFeatures(connection, taskId, input.features());
		insertTestCases(connection, taskId, input.testCases());
		insertHints(connection, taskId, input.hints());
		insertAssignments(connection, taskId, input.classAssignments());
		return taskId;
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
		requireDraftForUpdate(connection, teacherUserId, taskId, expectedVersion);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE tasks
				SET updated_by_user_id = ?, title = ?, theme = ?, difficulty = ?, language = 'Python',
				    description = ?, input_constraints = ?, creation_rules = ?, initial_code = ?,
				    save_status = 'draft', version = version + 1, updated_at = CURRENT_TIMESTAMP
				WHERE task_id = ? AND created_by_user_id = ?
				  AND publication_status = 'draft' AND deleted_at IS NULL AND version = ?
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, input.title());
			statement.setString(3, input.theme());
			statement.setString(4, databaseDifficulty(input.difficulty()));
			statement.setString(5, input.description());
			statement.setString(6, input.inputConstraints());
			statement.setString(7, input.creationRules());
			statement.setString(8, input.initialCode());
			statement.setLong(9, taskId);
			statement.setLong(10, teacherUserId);
			statement.setLong(11, expectedVersion);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Task draft update lost its version or ownership condition.");
			}
		}
		updateFeatures(connection, taskId, input.features());
		updateTestCases(connection, taskId, input.testCases());
		updateHints(connection, taskId, input.hints());
		updateAssignments(connection, taskId, input.classAssignments());
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
		if (!List.of("create_draft", "update_draft").contains(actionType)) {
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
		if (!List.of("create_draft", "update_draft").contains(actionType)) {
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
		requireTeacherId(teacherUserId);
		if (requestId == null || requestId.isBlank()) {
			throw new IllegalArgumentException("A request identifier is required for task creation.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT target_id
				FROM audit_logs
				WHERE actor_user_id = ? AND actor_role = 'teacher' AND feature_code = ?
				  AND target_type = ? AND action_type = 'create_draft'
				  AND result_status = 'success' AND request_id = ? AND target_id IS NOT NULL
				ORDER BY audit_log_id DESC
				LIMIT 1
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, TASK_FEATURE_CODE);
			statement.setString(3, TASK_TARGET_TYPE);
			statement.setString(4, requestId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? Optional.of(rows.getLong("target_id")) : Optional.empty();
			}
		}
	}

	public List<TeacherTaskAuditEntry> findAuditEntries(
			Connection connection,
			long teacherUserId,
			long taskId) throws SQLException {
		requireTeacherId(teacherUserId);
		requireTaskId(taskId);
		if (findDraft(connection, teacherUserId, taskId, false).isEmpty()) {
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

	private Optional<TeacherTaskDetails> findTask(
			Connection connection,
			long teacherUserId,
			long taskId,
			boolean forUpdate) throws SQLException {
		return findDraft(connection, teacherUserId, taskId, forUpdate);
	}

	private TeacherTaskDetails requireDraftForUpdate(
			Connection connection,
			long teacherUserId,
			long taskId,
			long expectedVersion) throws SQLException {
		TeacherTaskDetails current = findDraft(connection, teacherUserId, taskId, true)
				.orElseThrow(() -> new IllegalArgumentException("課題が見つからないか、編集できません。"));
		if (current.version() != expectedVersion) {
			throw new IllegalArgumentException("課題が更新されています。画面を読み込み直してください。");
		}
		return current;
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
			String initialCode) throws SQLException {
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
				SELECT task_class_assignment_id, classroom_id, publish_at, due_at, late_submission_policy
				FROM task_class_assignments
				WHERE task_id = ? AND assignment_status = 'not_published'
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
									rows.getString("late_submission_policy"))));
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
				features, testCases, hints, assignments);
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
