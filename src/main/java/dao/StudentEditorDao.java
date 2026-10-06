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

import entity.EditorCodeLog;
import entity.EditorHint;
import entity.EditorSaveResult;
import entity.EditorSubmissionCheck;
import entity.EditorSubmissionCheckCase;
import entity.EditorSubmissionResult;
import entity.EditorTestCase;
import entity.PythonExecutionResult;
import entity.StudentEditorPage;
import lib.mysql.Client;

public final class StudentEditorDao {
	private static final String FIND_ASSIGNMENT = """
			SELECT ca.task_class_assignment_id, ca.due_at, ca.late_submission_policy,
			       t.task_id, t.title, t.theme, t.difficulty, t.description, t.input_constraints,
			       t.creation_rules, t.initial_code,
			       tp.participation_id, tp.learning_status, tp.progress_status, tp.save_status,
			       tp.current_draft_code, tp.draft_base_submission_id, tp.draft_updated_at
			FROM users u
			JOIN student_profiles sp ON sp.user_id = u.user_id
			JOIN student_class_memberships scm ON scm.student_user_id = u.user_id
			  AND scm.membership_status = 'active'
			JOIN classrooms c ON c.classroom_id = scm.classroom_id AND c.classroom_status = 'active'
			JOIN schools s ON s.school_id = c.school_id AND s.school_status = 'active'
			JOIN task_class_assignments ca ON ca.classroom_id = c.classroom_id
			  AND (
			    ca.assignment_status = 'published'
			    OR (ca.assignment_status = 'expired' AND (
			      ca.late_submission_policy = 'allow'
			      OR EXISTS (
			        SELECT 1
			        FROM task_participations tp_expired
			        JOIN submissions s_expired ON s_expired.participation_id = tp_expired.participation_id
			        WHERE tp_expired.task_class_assignment_id = ca.task_class_assignment_id
			          AND tp_expired.student_user_id = u.user_id
			      )
			    ))
			  )
			  AND (ca.publish_at IS NULL OR ca.publish_at <= CURRENT_TIMESTAMP)
			JOIN tasks t ON t.task_id = ca.task_id
			  AND t.publication_status = 'published' AND t.deleted_at IS NULL
			  AND LOWER(t.language) = 'python'
			LEFT JOIN task_participations tp
			  ON tp.student_user_id = u.user_id
			  AND tp.task_class_assignment_id = ca.task_class_assignment_id
			WHERE u.user_id = ? AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			  AND ca.task_class_assignment_id = ?
			""";

	private static final String FIND_LATEST_SUBMISSION = """
			SELECT submission_id, revision_number, submission_status, submitted_code, submitted_at
			FROM submissions
			WHERE participation_id = ?
			ORDER BY revision_number DESC
			LIMIT 1
			""";

	public Optional<StudentEditorPage> findEditorPage(long studentUserId, long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				AssignmentRow assignment = findAssignment(connection, studentUserId, assignmentId);
				if (assignment == null) {
					connection.commit();
					return Optional.empty();
				}
				if (assignment.participationId == null) {
					createParticipation(connection, studentUserId, assignmentId, assignment.initialCode);
				}
				StudentEditorPage page = readPage(connection, studentUserId, assignmentId);
				connection.commit();
				return Optional.ofNullable(page);
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public EditorSaveResult saveDraft(
			long studentUserId,
			long assignmentId,
			String code,
			LocalDateTime expectedUpdatedAt,
			boolean periodicSnapshot) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				StudentEditorPage page = lockAndReadPage(connection, studentUserId, assignmentId);
				if (page == null) {
					connection.commit();
					return new EditorSaveResult(EditorSaveResult.Status.NOT_FOUND, null);
				}
				if (!page.isEditable()) {
					connection.commit();
					return new EditorSaveResult(EditorSaveResult.Status.READ_ONLY, page.getDraftUpdatedAt());
				}
				if (!sameTimestamp(page.getDraftUpdatedAt(), expectedUpdatedAt)) {
					connection.commit();
					return new EditorSaveResult(EditorSaveResult.Status.CONFLICT, page.getDraftUpdatedAt());
				}

				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_participations
						SET current_draft_code = ?,
						    learning_status = 'in_progress',
						    progress_status = 'in_progress',
						    save_status = 'saved',
						    draft_updated_at = CURRENT_TIMESTAMP(6),
						    last_activity_at = CURRENT_TIMESTAMP(6)
						WHERE participation_id = ? AND draft_updated_at <=> ?
						""")) {
					statement.setString(1, code);
					statement.setLong(2, page.getParticipationId());
					statement.setObject(3, expectedUpdatedAt);
					if (statement.executeUpdate() != 1) {
						LocalDateTime latest = findDraftUpdatedAt(connection, page.getParticipationId());
						connection.commit();
						return new EditorSaveResult(EditorSaveResult.Status.CONFLICT, latest);
					}
				}

				LocalDateTime updatedAt = findDraftUpdatedAt(connection, page.getParticipationId());
				insertCodeLog(
						connection,
						page.getParticipationId(),
						null,
						null,
						periodicSnapshot ? "periodic_snapshot" : "manual_save",
						code,
						null);
				connection.commit();
				return new EditorSaveResult(EditorSaveResult.Status.SAVED, updatedAt);
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public void recordExecution(
			long studentUserId,
			long assignmentId,
			String code,
			String standardInput,
			PythonExecutionResult result,
			int durationMilliseconds) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				StudentEditorPage page = lockAndReadPage(connection, studentUserId, assignmentId);
				if (page == null) {
					throw new SQLException("The student editor assignment is no longer available.");
				}
				long executionId = insertExecution(
						connection,
						page.getParticipationId(),
						null,
						studentUserId,
						"student_task",
						code,
						standardInput,
						result,
						durationMilliseconds);
				insertCodeLog(
						connection,
						page.getParticipationId(),
						null,
						executionId,
						"edit",
						code,
						toLogExecutionStatus(result.getStatus()));
				connection.commit();
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public EditorSubmissionResult submit(
			long studentUserId,
			long assignmentId,
			String requestKey,
			EditorSubmissionCheck check) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				StudentEditorPage page = lockAndReadPage(connection, studentUserId, assignmentId);
				if (page == null) {
					connection.commit();
					return new EditorSubmissionResult(EditorSubmissionResult.Status.NOT_FOUND, 0, 0);
				}

				EditorSubmissionResult existing = findSubmissionByRequestKey(
						connection, page.getParticipationId(), requestKey);
				if (existing != null) {
					connection.commit();
					return existing;
				}
				if (check == null
						|| check.getUserId() != studentUserId
						|| check.getAssignmentId() != assignmentId
						|| check.getParticipationId() != page.getParticipationId()
						|| System.currentTimeMillis() - check.getCreatedAtEpochMillis() > 10 * 60 * 1000L) {
					connection.commit();
					return new EditorSubmissionResult(EditorSubmissionResult.Status.CHECK_EXPIRED, 0, 0);
				}
				if (!sameTimestamp(page.getDraftUpdatedAt(), parseDraftToken(check.getDraftUpdatedAt()))
						|| !page.getCode().equals(check.getSourceCode())
						|| !sameTestCases(page.getTestCases(), check.getResults())) {
					connection.commit();
					return new EditorSubmissionResult(EditorSubmissionResult.Status.CONFLICT, 0, 0);
				}
				if (!page.isCanSubmit(currentDatabaseTime(connection))) {
					connection.commit();
					return new EditorSubmissionResult(EditorSubmissionResult.Status.NOT_ALLOWED, 0, 0);
				}

				int revisionNumber = nextRevisionNumber(connection, page.getParticipationId());
				long submissionId = insertSubmission(
						connection,
						page.getParticipationId(),
						revisionNumber,
						check.getSourceCode(),
						requestKey);

				boolean allMatched = true;
				for (EditorSubmissionCheckCase result : check.getResults()) {
					PythonExecutionResult execution = new PythonExecutionResult(
							result.getExecutionStatus(),
							result.getExitCode(),
							result.getActualOutput(),
							result.getStandardError(),
							result.isOutputTruncated(),
							result.isStandardErrorTruncated(),
							result.getErrorCode());
					long executionId = insertExecution(
							connection,
							page.getParticipationId(),
							submissionId,
							studentUserId,
							"submission_check",
							check.getSourceCode(),
							result.getInput(),
							execution,
							result.getDurationMilliseconds());
					insertTestResult(connection, executionId, result);
					allMatched &= result.isPassed();
				}

				insertCodeLog(
						connection,
						page.getParticipationId(),
						submissionId,
						null,
						"submission",
						check.getSourceCode(),
						allMatched ? "succeeded" : "failed");
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_participations
						SET learning_status = 'completed',
						    progress_status = 'awaiting_evaluation',
						    save_status = 'saved',
						    evaluation_status = 'not_started',
						    draft_base_submission_id = ?,
						    draft_updated_at = CURRENT_TIMESTAMP(6),
						    last_activity_at = CURRENT_TIMESTAMP(6),
						    completed_at = CURRENT_TIMESTAMP(6)
						WHERE participation_id = ?
						""")) {
					statement.setLong(1, submissionId);
					statement.setLong(2, page.getParticipationId());
					statement.executeUpdate();
				}
				EvaluationQueueDao.enqueueIfConfigured(
						connection, page.getTaskId(), submissionId, studentUserId);

				connection.commit();
				return new EditorSubmissionResult(
						EditorSubmissionResult.Status.SUBMITTED, submissionId, revisionNumber);
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public EditorSaveResult startResubmission(long studentUserId, long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				StudentEditorPage page = lockAndReadPage(connection, studentUserId, assignmentId);
				if (page == null) {
					connection.commit();
					return new EditorSaveResult(EditorSaveResult.Status.NOT_FOUND, null);
				}
				if (!page.isCanStartResubmission(currentDatabaseTime(connection))
						|| page.getLatestSubmittedCode() == null) {
					connection.commit();
					return new EditorSaveResult(EditorSaveResult.Status.READ_ONLY, page.getDraftUpdatedAt());
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_participations
						SET current_draft_code = ?,
						    draft_base_submission_id = ?,
						    learning_status = 'in_progress',
						    progress_status = 'in_progress',
						    save_status = 'saved',
						    draft_updated_at = CURRENT_TIMESTAMP(6),
						    last_activity_at = CURRENT_TIMESTAMP(6),
						    completed_at = NULL
						WHERE participation_id = ?
						""")) {
					statement.setString(1, page.getLatestSubmittedCode());
					statement.setLong(2, page.getLatestSubmissionId());
					statement.setLong(3, page.getParticipationId());
					statement.executeUpdate();
				}
				insertCodeLog(
						connection,
						page.getParticipationId(),
						null,
						null,
						"edit",
						page.getLatestSubmittedCode(),
						null);
				LocalDateTime updatedAt = findDraftUpdatedAt(connection, page.getParticipationId());
				connection.commit();
				return new EditorSaveResult(EditorSaveResult.Status.SAVED, updatedAt);
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	private static StudentEditorPage lockAndReadPage(
			Connection connection,
			long studentUserId,
			long assignmentId) throws SQLException {
		AssignmentRow assignment = findAssignment(connection, studentUserId, assignmentId);
		if (assignment == null) {
			return null;
		}
		if (assignment.participationId == null) {
			createParticipation(connection, studentUserId, assignmentId, assignment.initialCode);
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT participation_id FROM task_participations
				WHERE student_user_id = ? AND task_class_assignment_id = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
			}
		}
		return readPage(connection, studentUserId, assignmentId);
	}

	private static StudentEditorPage readPage(
			Connection connection,
			long studentUserId,
			long assignmentId) throws SQLException {
		AssignmentRow row = findAssignment(connection, studentUserId, assignmentId);
		if (row == null || row.participationId == null) {
			return null;
		}

		LatestSubmission latest = findLatestSubmission(connection, row.participationId);
		return new StudentEditorPage(
				row.assignmentId,
				row.taskId,
				row.participationId,
				row.title,
				row.theme,
				row.difficulty,
				row.description,
				row.inputConstraints,
				row.creationRules,
				row.initialCode,
				row.currentDraftCode == null ? "" : row.currentDraftCode,
				row.learningStatus,
				row.progressStatus,
				row.saveStatus,
				row.lateSubmissionPolicy,
				row.dueAt,
				row.draftUpdatedAt,
				row.draftBaseSubmissionId,
				latest == null ? null : latest.submissionId,
				latest == null ? null : latest.revisionNumber,
				latest == null ? null : latest.status,
				latest == null ? null : latest.code,
				latest == null ? null : latest.submittedAt,
				findFeatures(connection, row.taskId),
				findTestCases(connection, row.taskId),
				findHints(connection, row.taskId),
				findCodeLogs(connection, row.participationId),
				currentDatabaseTime(connection));
	}

	private static AssignmentRow findAssignment(Connection connection, long studentUserId, long assignmentId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_ASSIGNMENT)) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				return new AssignmentRow(
						resultSet.getLong("task_class_assignment_id"),
						resultSet.getLong("task_id"),
						resultSet.getString("title"),
						resultSet.getString("theme"),
						resultSet.getString("difficulty"),
						resultSet.getString("description"),
						resultSet.getString("input_constraints"),
						resultSet.getString("creation_rules"),
						resultSet.getString("initial_code"),
						toLocalDateTime(resultSet.getTimestamp("due_at")),
						resultSet.getString("late_submission_policy"),
						getNullableLong(resultSet, "participation_id"),
						resultSet.getString("learning_status"),
						resultSet.getString("progress_status"),
						resultSet.getString("save_status"),
						resultSet.getString("current_draft_code"),
						getNullableLong(resultSet, "draft_base_submission_id"),
						toLocalDateTime(resultSet.getTimestamp("draft_updated_at")));
			}
		}
	}

	private static void createParticipation(
			Connection connection,
			long studentUserId,
			long assignmentId,
			String initialCode) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_participations (
				  student_user_id, task_class_assignment_id, learning_status, progress_status,
				  save_status, current_draft_code, evaluation_status, active_duration_seconds, last_activity_at
				)
				VALUES (?, ?, 'not_started', 'not_started', 'unsaved', ?, 'not_started', 0, CURRENT_TIMESTAMP(6))
				ON DUPLICATE KEY UPDATE participation_id = LAST_INSERT_ID(participation_id)
				""")) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			statement.setString(3, initialCode == null ? "" : initialCode);
			statement.executeUpdate();
		}
	}

	private static LatestSubmission findLatestSubmission(Connection connection, long participationId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_LATEST_SUBMISSION)) {
			statement.setLong(1, participationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				return new LatestSubmission(
						resultSet.getLong("submission_id"),
						resultSet.getInt("revision_number"),
						resultSet.getString("submission_status"),
						resultSet.getString("submitted_code"),
						toLocalDateTime(resultSet.getTimestamp("submitted_at")));
			}
		}
	}

	private static List<String> findFeatures(Connection connection, long taskId) throws SQLException {
		List<String> features = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT feature_text FROM task_features
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY sort_order, feature_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					features.add(resultSet.getString("feature_text"));
				}
			}
		}
		return List.copyOf(features);
	}

	private static List<EditorTestCase> findTestCases(Connection connection, long taskId) throws SQLException {
		List<EditorTestCase> cases = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT test_case_id, title, test_case_input, test_case_output, test_case_order
				FROM task_test_cases
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY test_case_order, test_case_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					cases.add(new EditorTestCase(
							resultSet.getLong("test_case_id"),
							resultSet.getString("title"),
							resultSet.getString("test_case_input"),
							resultSet.getString("test_case_output"),
							resultSet.getInt("test_case_order")));
				}
			}
		}
		return List.copyOf(cases);
	}

	private static List<EditorHint> findHints(Connection connection, long taskId) throws SQLException {
		List<EditorHint> hints = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT hint_title, hint_content, usage_syntax, hint_code
				FROM task_hints
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY hint_order, hint_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					hints.add(new EditorHint(
							resultSet.getString("hint_title"),
							resultSet.getString("hint_content"),
							resultSet.getString("usage_syntax"),
							resultSet.getString("hint_code")));
				}
			}
		}
		return List.copyOf(hints);
	}

	private static List<EditorCodeLog> findCodeLogs(Connection connection, long participationId)
			throws SQLException {
		List<EditorCodeLog> logs = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT event_type, execution_status_at_capture, observed_at, snapshot_text
				FROM code_logs
				WHERE participation_id = ?
				ORDER BY observed_at DESC, code_log_id DESC
				LIMIT 50
				""")) {
			statement.setLong(1, participationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					logs.add(new EditorCodeLog(
							resultSet.getString("event_type"),
							resultSet.getString("execution_status_at_capture"),
							toLocalDateTime(resultSet.getTimestamp("observed_at")),
							resultSet.getString("snapshot_text")));
				}
			}
		}
		return List.copyOf(logs);
	}

	private static void insertTestResult(
			Connection connection,
			long executionId,
			EditorSubmissionCheckCase result) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO code_execution_test_results (
				  execution_id, test_case_id, case_order, input_snapshot, expected_output_snapshot,
				  actual_output, actual_output_truncated, result_status, error_code, error_message, checked_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
				""")) {
			statement.setLong(1, executionId);
			statement.setLong(2, result.getTestCaseId());
			statement.setInt(3, result.getOrder());
			statement.setString(4, result.getInput());
			statement.setString(5, result.getExpectedOutput());
			statement.setString(6, result.getActualOutput());
			statement.setBoolean(7, result.isOutputTruncated());
			statement.setString(8, result.getResultStatus());
			statement.setString(9, result.getErrorCode());
			statement.setString(10, safeExecutionMessage(result.getErrorCode()));
			statement.executeUpdate();
		}
	}

	private static long insertExecution(
			Connection connection,
			long participationId,
			Long submissionId,
			long actorUserId,
			String context,
			String sourceCode,
			String standardInput,
			PythonExecutionResult result,
			int durationMilliseconds) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO code_executions (
				  participation_id, submission_id, actor_user_id, execution_context, source_code,
				  standard_input, execution_status, exit_code, standard_output, standard_output_truncated,
				  standard_error, standard_error_truncated, error_code, error_message,
				  duration_milliseconds, executed_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, participationId);
			if (submissionId == null) {
				statement.setNull(2, java.sql.Types.BIGINT);
			} else {
				statement.setLong(2, submissionId);
			}
			statement.setLong(3, actorUserId);
			statement.setString(4, context);
			statement.setString(5, sourceCode);
			statement.setString(6, standardInput);
			statement.setString(7, normalizeExecutionStatus(result.getStatus()));
			if (result.getExitCode() == null) {
				statement.setNull(8, java.sql.Types.INTEGER);
			} else {
				statement.setInt(8, result.getExitCode());
			}
			statement.setString(9, result.getStandardOutput());
			statement.setBoolean(10, result.isStandardOutputTruncated());
			statement.setString(11, result.getStandardError());
			statement.setBoolean(12, result.isStandardErrorTruncated());
			statement.setString(13, result.getErrorCode());
			statement.setString(14, safeExecutionMessage(result.getErrorCode()));
			statement.setInt(15, Math.max(0, durationMilliseconds));
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The code execution ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertSubmission(
			Connection connection,
			long participationId,
			int revisionNumber,
			String code,
			String requestKey) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO submissions (
				  participation_id, revision_number, submitted_code, submission_status,
				  submission_request_key, submitted_at, created_at
				)
				VALUES (?, ?, ?, 'accepted', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, participationId);
			statement.setInt(2, revisionNumber);
			statement.setString(3, code);
			statement.setString(4, requestKey);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The submission ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void insertCodeLog(
			Connection connection,
			long participationId,
			Long submissionId,
			Long executionId,
			String eventType,
			String code,
			String executionStatus) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO code_logs (
				  participation_id, submission_id, code_execution_id, event_type, snapshot_text,
				  execution_status_at_capture, observed_at, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
				""")) {
			statement.setLong(1, participationId);
			setNullableLong(statement, 2, submissionId);
			setNullableLong(statement, 3, executionId);
			statement.setString(4, eventType);
			statement.setString(5, code);
			if (executionStatus == null) {
				statement.setNull(6, java.sql.Types.VARCHAR);
			} else {
				statement.setString(6, executionStatus);
			}
			statement.executeUpdate();
		}
	}

	private static EditorSubmissionResult findSubmissionByRequestKey(
			Connection connection,
			long participationId,
			String requestKey) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT submission_id, revision_number
				FROM submissions
				WHERE participation_id = ? AND submission_request_key = ?
				""")) {
			statement.setLong(1, participationId);
			statement.setString(2, requestKey);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next()
						? new EditorSubmissionResult(
								EditorSubmissionResult.Status.DUPLICATE,
								resultSet.getLong("submission_id"),
								resultSet.getInt("revision_number"))
						: null;
			}
		}
	}

	private static int nextRevisionNumber(Connection connection, long participationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COALESCE(MAX(revision_number), 0) + 1 AS next_revision
				FROM submissions WHERE participation_id = ?
				""")) {
			statement.setLong(1, participationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt("next_revision");
			}
		}
	}

	private static LocalDateTime findDraftUpdatedAt(Connection connection, long participationId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT draft_updated_at FROM task_participations WHERE participation_id = ?
				""")) {
			statement.setLong(1, participationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The student participation was not found after its update.");
				}
				return toLocalDateTime(resultSet.getTimestamp("draft_updated_at"));
			}
		}
	}

	private static boolean sameTestCases(List<EditorTestCase> pageCases, List<EditorSubmissionCheckCase> checkedCases) {
		if (pageCases.size() != checkedCases.size()) {
			return false;
		}
		for (int index = 0; index < pageCases.size(); index++) {
			EditorTestCase expected = pageCases.get(index);
			EditorSubmissionCheckCase checked = checkedCases.get(index);
			if (expected.getTestCaseId() != checked.getTestCaseId()
					|| !expected.getInput().equals(checked.getInput())
					|| !expected.getExpectedOutput().equals(checked.getExpectedOutput())) {
				return false;
			}
		}
		return true;
	}

	private static String normalizeExecutionStatus(String status) {
		return switch (status) {
			case "succeeded", "timed_out" -> status;
			default -> "failed";
		};
	}

	private static String toLogExecutionStatus(String status) {
		return switch (status) {
			case "succeeded", "timed_out" -> status;
			default -> "failed";
		};
	}

	private static String safeExecutionMessage(String errorCode) {
		if (errorCode == null) {
			return null;
		}
		return switch (errorCode) {
			case "execution_timeout" -> "実行時間の上限を超えました。";
			case "output_limit" -> "出力が上限を超えたため、実行を停止しました。";
			case "runner_unavailable" -> "コード実行サービスを利用できません。";
			default -> "コードの実行中にエラーが発生しました。";
		};
	}

	private static boolean sameTimestamp(LocalDateTime left, LocalDateTime right) {
		return left == null ? right == null : left.equals(right);
	}

	private static LocalDateTime parseDraftToken(String value) throws SQLException {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value);
		} catch (java.time.format.DateTimeParseException e) {
			throw new SQLException("The draft timestamp is invalid.", e);
		}
	}

	private static LocalDateTime toLocalDateTime(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private static LocalDateTime currentDatabaseTime(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
				ResultSet resultSet = statement.executeQuery()) {
			if (!resultSet.next()) {
				throw new SQLException("The database did not return its current timestamp.");
			}
			return toLocalDateTime(resultSet.getTimestamp(1));
		}
	}

	private static Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
		if (value == null) {
			statement.setNull(index, java.sql.Types.BIGINT);
		} else {
			statement.setLong(index, value);
		}
	}

	private static void rollback(Connection connection, Exception original) {
		try {
			connection.rollback();
		} catch (SQLException rollbackError) {
			original.addSuppressed(rollbackError);
		}
	}

	private record AssignmentRow(
			long assignmentId,
			long taskId,
			String title,
			String theme,
			String difficulty,
			String description,
			String inputConstraints,
			String creationRules,
			String initialCode,
			LocalDateTime dueAt,
			String lateSubmissionPolicy,
			Long participationId,
			String learningStatus,
			String progressStatus,
			String saveStatus,
			String currentDraftCode,
			Long draftBaseSubmissionId,
			LocalDateTime draftUpdatedAt) {
	}

	private record LatestSubmission(
			long submissionId,
			int revisionNumber,
			String status,
			String code,
			LocalDateTime submittedAt) {
	}
}
