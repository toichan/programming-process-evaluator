package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import entity.StudentCodeLogPage;
import entity.StudentEvaluationPage;
import entity.StudentEvaluationPage.DimensionResult;
import entity.StudentEvaluationPage.EvaluationResult;
import entity.StudentEvaluationPage.ReasonResult;
import entity.StudentEvaluationPage.ScoreResult;
import entity.StudentEvaluationPage.SubmissionSummary;
import lib.mysql.Client;

public final class StudentEvaluationDao {
	private static final String FIND_SELECTED_SUBMISSION = """
			SELECT ca.task_class_assignment_id, t.task_id, t.title, t.difficulty,
			       t.rubric_id, r.version AS rubric_version, r.rubric_status,
			       t.active_prompt_version_id, pv.version AS prompt_version, pv.prompt_status,
			       pv.fluctuation_generation_status, pv.evaluation_examples_status,
			       s.submission_id, s.revision_number, s.submitted_at,
			       e.evaluation_id, e.evaluation_status, e.completed_at,
			       e.feedback_summary, e.process_analysis
			FROM task_participations tp
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			JOIN tasks t ON t.task_id = ca.task_id
			LEFT JOIN rubrics r ON r.rubric_id = t.rubric_id
			LEFT JOIN prompt_versions pv ON pv.prompt_version_id = t.active_prompt_version_id
			JOIN submissions s ON s.participation_id = tp.participation_id
			LEFT JOIN evaluations e ON e.evaluation_id = (
			  SELECT e_latest.evaluation_id
			  FROM evaluations e_latest
			  WHERE e_latest.submission_id = s.submission_id
			  ORDER BY e_latest.created_at DESC, e_latest.evaluation_id DESC
			  LIMIT 1
			)
			WHERE tp.student_user_id = ?
			  AND ca.task_class_assignment_id = ?
			  AND (? IS NULL OR s.submission_id = ?)
			ORDER BY s.revision_number DESC
			LIMIT 1
			""";

	private static final String FIND_SUBMISSIONS = """
			SELECT s.submission_id, s.revision_number, s.submitted_at,
			       COALESCE(e.evaluation_status, 'not_started') AS evaluation_status
			FROM task_participations tp
			JOIN submissions s ON s.participation_id = tp.participation_id
			LEFT JOIN evaluations e ON e.evaluation_id = (
			  SELECT e_latest.evaluation_id
			  FROM evaluations e_latest
			  WHERE e_latest.submission_id = s.submission_id
			  ORDER BY e_latest.created_at DESC, e_latest.evaluation_id DESC
			  LIMIT 1
			)
			WHERE tp.student_user_id = ? AND tp.task_class_assignment_id = ?
			ORDER BY s.revision_number DESC
			""";

	private static final String FIND_PREVIOUS_COMPLETED = """
			SELECT e.evaluation_id, s.submission_id, s.revision_number, e.evaluation_status,
			       e.completed_at, e.feedback_summary, e.process_analysis
			FROM task_participations tp
			JOIN submissions s ON s.participation_id = tp.participation_id
			JOIN evaluations e ON e.evaluation_id = (
			  SELECT e_latest.evaluation_id
			  FROM evaluations e_latest
			  WHERE e_latest.submission_id = s.submission_id
			  ORDER BY e_latest.created_at DESC, e_latest.evaluation_id DESC
			  LIMIT 1
			)
			WHERE tp.student_user_id = ?
			  AND tp.task_class_assignment_id = ?
			  AND s.submission_id <> ?
			  AND e.evaluation_status = 'completed'
			ORDER BY s.revision_number DESC, e.created_at DESC
			LIMIT 1
			""";

	private static final String FIND_LOG_SUBMISSION = """
			SELECT ca.task_class_assignment_id, t.title, t.difficulty,
			       tp.participation_id, s.submission_id, s.revision_number, s.submitted_at,
			       (SELECT MAX(previous.submitted_at)
			        FROM submissions previous
			        WHERE previous.participation_id = s.participation_id
			          AND previous.revision_number < s.revision_number) AS previous_submitted_at,
			       e.evaluation_id, e.evaluation_status, e.completed_at,
			       e.feedback_summary, e.process_analysis
			FROM submissions s
			JOIN task_participations tp ON tp.participation_id = s.participation_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			JOIN tasks t ON t.task_id = ca.task_id
			LEFT JOIN evaluations e ON e.evaluation_id = (
			  SELECT e_latest.evaluation_id
			  FROM evaluations e_latest
			  WHERE e_latest.submission_id = s.submission_id
			  ORDER BY e_latest.created_at DESC, e_latest.evaluation_id DESC
			  LIMIT 1
			)
			WHERE s.submission_id = ? AND tp.student_user_id = ?
			""";

	public Optional<StudentEvaluationPage> findEvaluationPage(
			long studentUserId,
			long assignmentId,
			Long selectedSubmissionId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				SelectedSubmission selected = findSelectedSubmission(
						connection, studentUserId, assignmentId, selectedSubmissionId);
				if (selected == null) {
					connection.commit();
					return Optional.empty();
				}
				List<SubmissionSummary> submissions = findSubmissions(connection, studentUserId, assignmentId,
						selected.submissionId);
				EvaluationResult selectedEvaluation = selected.evaluationId == null
						? null
						: findEvaluationResult(connection, selected.evaluationId, selected.submissionId,
								selected.revisionNumber, selected.evaluationStatus, selected.completedAt,
								selected.feedbackSummary, selected.processAnalysis);
				EvaluationResult previousCompleted = "in_progress".equals(selected.evaluationStatus)
						? findPreviousCompleted(connection, studentUserId, assignmentId, selected.submissionId)
						: null;
				connection.commit();
				return Optional.of(new StudentEvaluationPage(
						selected.assignmentId,
						selected.taskTitle,
						selected.difficulty,
						selected.submissionId,
						selected.revisionNumber,
						selected.submittedAt,
						selected.evaluationStatus,
						selected.evaluationConfigured,
						selected.rubricVersion,
						selected.promptVersion,
						selectedEvaluation,
						previousCompleted,
						submissions));
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public Optional<StudentCodeLogPage> findCodeLogPage(long studentUserId, long submissionId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				SelectedLogSubmission selected = findLogSubmission(connection, studentUserId, submissionId);
				if (selected == null) {
					connection.commit();
					return Optional.empty();
				}
				List<StudentCodeLogPage.LogEntry> entries = findCodeLogs(connection, selected);
				EvaluationResult evaluation = selected.evaluationId == null
						? null
						: findEvaluationResult(connection, selected.evaluationId, selected.submissionId,
								selected.revisionNumber, selected.evaluationStatus, selected.completedAt,
								selected.feedbackSummary, selected.processAnalysis);
				connection.commit();
				return Optional.of(new StudentCodeLogPage(
						selected.assignmentId,
						selected.submissionId,
						selected.revisionNumber,
						selected.taskTitle,
						selected.difficulty,
						entries,
						evaluation));
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	private static SelectedSubmission findSelectedSubmission(
			Connection connection,
			long studentUserId,
			long assignmentId,
			Long selectedSubmissionId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_SELECTED_SUBMISSION)) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			if (selectedSubmissionId == null) {
				statement.setNull(3, java.sql.Types.BIGINT);
				statement.setNull(4, java.sql.Types.BIGINT);
			} else {
				statement.setLong(3, selectedSubmissionId);
				statement.setLong(4, selectedSubmissionId);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				Long evaluationId = nullableLong(resultSet, "evaluation_id");
				return new SelectedSubmission(
						resultSet.getLong("task_class_assignment_id"),
						resultSet.getString("title"),
						resultSet.getString("difficulty"),
						nullableLong(resultSet, "rubric_id"),
						resultSet.getString("rubric_version"),
						nullableLong(resultSet, "active_prompt_version_id"),
						resultSet.getString("prompt_version"),
						"active".equals(resultSet.getString("rubric_status"))
								&& ("configured".equals(resultSet.getString("prompt_status"))
										|| "versioned".equals(resultSet.getString("prompt_status")))
								&& "completed".equals(resultSet.getString("fluctuation_generation_status"))
								&& "completed".equals(resultSet.getString("evaluation_examples_status")),
						resultSet.getLong("submission_id"),
						resultSet.getInt("revision_number"),
						localDateTime(resultSet.getTimestamp("submitted_at")),
						evaluationId,
						evaluationId == null ? "not_started" : resultSet.getString("evaluation_status"),
						localDateTime(resultSet.getTimestamp("completed_at")),
						resultSet.getString("feedback_summary"),
						resultSet.getString("process_analysis"));
			}
		}
	}

	private static List<SubmissionSummary> findSubmissions(
			Connection connection,
			long studentUserId,
			long assignmentId,
			long selectedSubmissionId) throws SQLException {
		List<SubmissionSummary> submissions = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(FIND_SUBMISSIONS)) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					long submissionId = resultSet.getLong("submission_id");
					submissions.add(new SubmissionSummary(
							submissionId,
							resultSet.getInt("revision_number"),
							localDateTime(resultSet.getTimestamp("submitted_at")),
							resultSet.getString("evaluation_status"),
							submissionId == selectedSubmissionId));
				}
			}
		}
		return List.copyOf(submissions);
	}

	private static EvaluationResult findPreviousCompleted(
			Connection connection,
			long studentUserId,
			long assignmentId,
			long selectedSubmissionId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_PREVIOUS_COMPLETED)) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			statement.setLong(3, selectedSubmissionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				return findEvaluationResult(
						connection,
						resultSet.getLong("evaluation_id"),
						resultSet.getLong("submission_id"),
						resultSet.getInt("revision_number"),
						resultSet.getString("evaluation_status"),
						localDateTime(resultSet.getTimestamp("completed_at")),
						resultSet.getString("feedback_summary"),
						resultSet.getString("process_analysis"));
			}
		}
	}

	static EvaluationResult findEvaluationResult(
			Connection connection,
			long evaluationId,
			long submissionId,
			int revisionNumber,
			String status,
			java.time.LocalDateTime completedAt,
			String feedbackSummary,
			String processAnalysis) throws SQLException {
		return new EvaluationResult(
				evaluationId,
				submissionId,
				revisionNumber,
				status,
				completedAt,
				feedbackSummary,
				processAnalysis,
				findDimensionResults(connection, evaluationId),
				findScoreResults(connection, evaluationId),
				findReasonResults(connection, evaluationId));
	}

	private static List<DimensionResult> findDimensionResults(Connection connection, long evaluationId)
			throws SQLException {
		List<DimensionResult> results = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT d.label, r.score_value, r.score_text, r.summary_title, r.summary_description
				FROM evaluation_dimension_results r
				JOIN rubric_dimensions d ON d.dimension_id = r.dimension_id
				WHERE r.evaluation_id = ?
				ORDER BY d.sort_order, r.evaluation_dimension_result_id
				""")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					results.add(new DimensionResult(
							resultSet.getString("label"),
							resultSet.getDouble("score_value"),
							resultSet.getString("score_text"),
							resultSet.getString("summary_title"),
							resultSet.getString("summary_description")));
				}
			}
		}
		return List.copyOf(results);
	}

	private static List<ScoreResult> findScoreResults(Connection connection, long evaluationId)
			throws SQLException {
		List<ScoreResult> results = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT d.label, s.metric_title, s.score_value, s.metric_description, s.rationale
				FROM evaluation_scores s
				JOIN rubric_dimensions d ON d.dimension_id = s.dimension_id
				WHERE s.evaluation_id = ?
				ORDER BY d.sort_order, s.evaluation_score_id
				""")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					results.add(new ScoreResult(
							resultSet.getString("label"),
							resultSet.getString("metric_title"),
							resultSet.getInt("score_value"),
							resultSet.getString("metric_description"),
							resultSet.getString("rationale")));
				}
			}
		}
		return List.copyOf(results);
	}

	private static List<ReasonResult> findReasonResults(Connection connection, long evaluationId)
			throws SQLException {
		Map<Long, ReasonBuilder> reasons = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT r.evaluation_reason_id, d.label AS dimension_label, r.title, r.body
				FROM evaluation_reasons r
				LEFT JOIN rubric_dimensions d ON d.dimension_id = r.dimension_id
				WHERE r.evaluation_id = ?
				ORDER BY r.sort_order, r.evaluation_reason_id
				""")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					reasons.put(resultSet.getLong("evaluation_reason_id"), new ReasonBuilder(
							resultSet.getString("dimension_label"),
							resultSet.getString("title"),
							resultSet.getString("body")));
				}
			}
		}
		if (reasons.isEmpty()) {
			return List.of();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT reason_detail_id, evaluation_reason_id, detail_text
				FROM evaluation_reason_details
				WHERE evaluation_reason_id IN (
				  SELECT evaluation_reason_id FROM evaluation_reasons WHERE evaluation_id = ?
				)
				ORDER BY sort_order, reason_detail_id
				""")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					ReasonBuilder reason = reasons.get(resultSet.getLong("evaluation_reason_id"));
					if (reason != null) {
						reason.details.add(resultSet.getString("detail_text"));
					}
				}
			}
		}
		return reasons.values().stream()
				.map(reason -> new ReasonResult(reason.dimensionLabel, reason.title, reason.body, reason.details))
				.toList();
	}

	private static SelectedLogSubmission findLogSubmission(
			Connection connection,
			long studentUserId,
			long submissionId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_LOG_SUBMISSION)) {
			statement.setLong(1, submissionId);
			statement.setLong(2, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				Long evaluationId = nullableLong(resultSet, "evaluation_id");
				return new SelectedLogSubmission(
						resultSet.getLong("task_class_assignment_id"),
						resultSet.getString("title"),
						resultSet.getString("difficulty"),
						resultSet.getLong("participation_id"),
						resultSet.getLong("submission_id"),
						resultSet.getInt("revision_number"),
						localDateTime(resultSet.getTimestamp("submitted_at")),
						localDateTime(resultSet.getTimestamp("previous_submitted_at")),
						evaluationId,
						evaluationId == null ? "not_started" : resultSet.getString("evaluation_status"),
						localDateTime(resultSet.getTimestamp("completed_at")),
						resultSet.getString("feedback_summary"),
						resultSet.getString("process_analysis"));
			}
		}
	}

	private static List<StudentCodeLogPage.LogEntry> findCodeLogs(
			Connection connection,
			SelectedLogSubmission selected) throws SQLException {
		List<StudentCodeLogPage.LogEntry> entries = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT l.code_log_id, l.event_type, l.observed_at, l.code_execution_id, l.snapshot_text,
				       e.execution_status, e.standard_input, e.standard_output, e.standard_error
				FROM code_logs l
				LEFT JOIN code_executions e ON e.execution_id = l.code_execution_id
				WHERE l.participation_id = ?
				  AND (
				    l.submission_id = ?
				    OR (
				      l.submission_id IS NULL
				      AND l.observed_at <= ?
				      AND (? IS NULL OR l.observed_at > ?)
				    )
				  )
				ORDER BY l.observed_at, l.code_log_id
				""")) {
			statement.setLong(1, selected.participationId);
			statement.setLong(2, selected.submissionId);
			statement.setTimestamp(3, Timestamp.valueOf(selected.submittedAt));
			if (selected.previousSubmittedAt == null) {
				statement.setNull(4, java.sql.Types.TIMESTAMP);
				statement.setNull(5, java.sql.Types.TIMESTAMP);
			} else {
				statement.setTimestamp(4, Timestamp.valueOf(selected.previousSubmittedAt));
				statement.setTimestamp(5, Timestamp.valueOf(selected.previousSubmittedAt));
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					entries.add(new StudentCodeLogPage.LogEntry(
							resultSet.getLong("code_log_id"),
							resultSet.getString("event_type"),
							localDateTime(resultSet.getTimestamp("observed_at")),
							nullableLong(resultSet, "code_execution_id"),
							resultSet.getString("snapshot_text"),
							resultSet.getString("execution_status"),
							resultSet.getString("standard_input"),
							resultSet.getString("standard_output"),
							resultSet.getString("standard_error")));
				}
			}
		}
		return List.copyOf(entries);
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private static java.time.LocalDateTime localDateTime(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private static void rollback(Connection connection, Exception original) {
		try {
			connection.rollback();
		} catch (SQLException rollbackError) {
			original.addSuppressed(rollbackError);
		}
	}

	private record SelectedSubmission(
			long assignmentId,
			String taskTitle,
			String difficulty,
			Long rubricId,
			String rubricVersion,
			Long promptVersionId,
			String promptVersion,
			boolean evaluationConfigured,
			long submissionId,
			int revisionNumber,
			java.time.LocalDateTime submittedAt,
			Long evaluationId,
			String evaluationStatus,
			java.time.LocalDateTime completedAt,
			String feedbackSummary,
			String processAnalysis) {
	}

	private record SelectedLogSubmission(
			long assignmentId,
			String taskTitle,
			String difficulty,
			long participationId,
			long submissionId,
			int revisionNumber,
			java.time.LocalDateTime submittedAt,
			java.time.LocalDateTime previousSubmittedAt,
			Long evaluationId,
			String evaluationStatus,
			java.time.LocalDateTime completedAt,
			String feedbackSummary,
			String processAnalysis) {
	}

	private static final class ReasonBuilder {
		private final String dimensionLabel;
		private final String title;
		private final String body;
		private final List<String> details = new ArrayList<>();

		private ReasonBuilder(String dimensionLabel, String title, String body) {
			this.dimensionLabel = dimensionLabel;
			this.title = title;
			this.body = body;
		}
	}
}
