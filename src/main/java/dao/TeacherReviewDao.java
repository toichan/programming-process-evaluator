package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import entity.TeacherReviewDetail;
import entity.TeacherReviewDetail.*;
import entity.TeacherReviewRow;

public final class TeacherReviewDao {
	private final TeacherPermissionDao permissions = new TeacherPermissionDao();

	// Historical authorization follows the assignment's school, not today's student membership.
	private static final String SCOPE = """
			FROM submissions sub
			JOIN task_participations p ON p.participation_id = sub.participation_id
			JOIN task_class_assignments a ON a.task_class_assignment_id = p.task_class_assignment_id
			JOIN classrooms c ON c.classroom_id = a.classroom_id
			JOIN schools school ON school.school_id = c.school_id
			JOIN tasks t ON t.task_id = a.task_id AND t.school_id = school.school_id
			JOIN users u ON u.user_id = p.student_user_id AND u.user_type = 'student'
			JOIN teacher_school_permissions access
			  ON access.school_id = school.school_id
			 AND access.teacher_user_id = ? AND access.access_status = 'enabled'
			""";
	private static final String LATEST_EVALUATION = """
			SELECT last_e.evaluation_id FROM evaluations last_e
			WHERE last_e.submission_id = sub.submission_id AND last_e.evaluation_kind <> 'manual_review'
			ORDER BY last_e.created_at DESC, last_e.evaluation_id DESC LIMIT 1
			""";

	public List<TeacherReviewRow> rows(Connection connection, long teacherId, boolean evaluations) throws SQLException {
		permissions.requireReviewAccess(connection, teacherId, evaluations);
		String evaluationJoin = evaluations
				? "LEFT JOIN evaluations e ON e.submission_id = sub.submission_id AND e.evaluation_kind <> 'manual_review'"
				: "LEFT JOIN evaluations e ON e.evaluation_id = (" + LATEST_EVALUATION + ")";
		String sql = """
				SELECT sub.submission_id, p.participation_id, a.task_class_assignment_id,
				       sub.revision_number, sub.submitted_at, u.login_id,
				       school.school_id, school.name AS school_name,
				       c.classroom_id, CONCAT_WS(' ', NULLIF(c.grade_name, ''), c.name) AS class_name,
				       t.task_id, t.title, COALESCE(t.difficulty, 'none') AS difficulty,
				       CONCAT_WS(' / ',
				         IF(t.deleted_at IS NOT NULL, '削除済み課題', NULL),
				         IF(EXISTS (SELECT 1 FROM tasks newer WHERE newer.task_code = t.task_code
				           AND newer.revision_number > t.revision_number), '旧改訂', NULL),
				         IF(a.assignment_status = 'archived', '割当終了', NULL),
				         IF(c.classroom_status <> 'active', '非アクティブクラス', NULL),
				         IF(u.account_status <> 'active' OR u.deleted_at IS NOT NULL, '停止/削除生徒', NULL),
				         IF(NOT EXISTS (SELECT 1 FROM student_class_memberships m
				             WHERE m.student_user_id = u.user_id AND m.classroom_id = c.classroom_id
				               AND m.membership_status = 'active'), '非在籍', NULL)) AS history_label,
				       COALESCE((SELECT cr.consent_status FROM consent_records cr
				         WHERE cr.user_id = u.user_id ORDER BY cr.consent_id DESC LIMIT 1), 'unconfirmed') AS consent,
				       (SELECT COUNT(*) FROM code_execution_test_results tr
				         JOIN code_executions ce ON ce.execution_id = tr.execution_id
				         WHERE ce.submission_id = sub.submission_id AND ce.execution_context = 'submission_check'
				           AND tr.result_status = 'matched') AS matched_cases,
				       (SELECT COUNT(*) FROM code_execution_test_results tr
				         JOIN code_executions ce ON ce.execution_id = tr.execution_id
				         WHERE ce.submission_id = sub.submission_id AND ce.execution_context = 'submission_check') AS total_cases,
				       e.evaluation_id, COALESCE(e.evaluation_status, 'not_started') AS evaluation_status,
				       COALESCE(e.completed_at, e.final_evaluated_at) AS evaluated_at, e.overall_score,
				       (SELECT dr.score_value FROM evaluation_dimension_results dr
				         JOIN rubric_dimensions d ON d.dimension_id = dr.dimension_id
				         WHERE dr.evaluation_id = e.evaluation_id AND d.dimension_code = 'thinking' LIMIT 1) AS thinking_score,
				       (SELECT dr.score_value FROM evaluation_dimension_results dr
				         JOIN rubric_dimensions d ON d.dimension_id = dr.dimension_id
				         WHERE dr.evaluation_id = e.evaluation_id AND d.dimension_code = 'attitude' LIMIT 1) AS attitude_score,
				""" + "(e.evaluation_id = (" + LATEST_EVALUATION + ")) AS latest_evaluation "
				+ SCOPE + evaluationJoin + " ORDER BY sub.submitted_at DESC, sub.submission_id DESC, e.created_at DESC, e.evaluation_id DESC";
		var rows = new ArrayList<TeacherReviewRow>();
		try (var statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherId);
			try (var r = statement.executeQuery()) {
				while (r.next()) {
					rows.add(new TeacherReviewRow(r.getLong("submission_id"), r.getLong("participation_id"),
							r.getLong("task_class_assignment_id"), r.getInt("revision_number"), r.getString("login_id"),
							r.getLong("school_id"), r.getString("school_name"), r.getLong("classroom_id"), r.getString("class_name"),
							r.getLong("task_id"), r.getString("title"), r.getString("difficulty"), date(r, "submitted_at"),
							r.getInt("matched_cases"), r.getInt("total_cases"), r.getString("consent"), r.getString("history_label"),
							(Long) r.getObject("evaluation_id"), r.getString("evaluation_status"), date(r, "evaluated_at"),
							number(r, "overall_score"), number(r, "thinking_score"), number(r, "attitude_score"),
							r.getBoolean("latest_evaluation")));
				}
			}
		}
		return List.copyOf(rows);
	}

	public TeacherReviewDetail detail(Connection connection, long teacherId, boolean evaluations,
			long submissionId, Long evaluationId) throws SQLException {
		List<TeacherReviewRow> authorized = rows(connection, teacherId, evaluations);
		var row = authorized.stream().filter(r -> r.submissionId() == submissionId
				&& (evaluationId == null || evaluationId.equals(r.evaluationId())))
				.findFirst().orElseThrow(NotFoundException::new);
		String code, publishedAt, dueAt;
		try (var statement = connection.prepareStatement("""
				SELECT sub.submitted_code, COALESCE(a.publish_at, t.published_at) AS published_at, a.due_at
				""" + SCOPE + " WHERE sub.submission_id = ?")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, submissionId);
			try (var r = statement.executeQuery()) {
				if (!r.next()) throw new NotFoundException();
				code = r.getString("submitted_code");
				publishedAt = date(r, "published_at");
				dueAt = date(r, "due_at");
			}
		}
		var checks = new ArrayList<Check>();
		try (var statement = connection.prepareStatement("""
				SELECT tr.case_order, tr.input_snapshot, tr.expected_output_snapshot, tr.actual_output,
				       tr.result_status, tr.actual_output_truncated, tr.error_code
				FROM code_execution_test_results tr
				JOIN code_executions ce ON ce.execution_id = tr.execution_id
				WHERE ce.submission_id = ? AND ce.execution_context = 'submission_check'
				ORDER BY tr.case_order, tr.execution_test_result_id
				""")) {
			statement.setLong(1, submissionId);
			try (var r = statement.executeQuery()) {
				while (r.next()) checks.add(new Check(r.getInt(1), r.getString(2), r.getString(3),
						r.getString(4), r.getString(5), r.getBoolean(6), r.getString(7)));
			}
		}
		if (!evaluations) return new TeacherReviewDetail(row, code, publishedAt, dueAt, checks, null, null, List.of(), List.of());
		var versions = new ArrayList<EvaluationVersion>();
		try (var statement = connection.prepareStatement("""
				SELECT evaluation_id, evaluation_status, created_at, evaluation_kind
				FROM evaluations WHERE submission_id = ? AND evaluation_kind <> 'manual_review'
				ORDER BY created_at DESC, evaluation_id DESC
				""")) {
			statement.setLong(1, submissionId);
			try (var r = statement.executeQuery()) {
				while (r.next()) versions.add(new EvaluationVersion(r.getLong(1), r.getString(2), date(r, "created_at"), r.getString(4)));
			}
		}
		Evaluation evaluation = row.evaluationId() == null ? null : evaluation(connection, row.evaluationId());
		Evaluation previous = null;
		if ("in_progress".equals(row.evaluationStatus())) {
			try (var statement = connection.prepareStatement("""
					SELECT e.evaluation_id FROM evaluations e
					JOIN submissions s ON s.submission_id = e.submission_id
					WHERE s.participation_id = ? AND e.evaluation_status = 'completed'
					  AND e.evaluation_kind <> 'manual_review' AND e.evaluation_id <> ?
					  AND s.revision_number <= ? AND e.created_at <=
					    (SELECT created_at FROM evaluations WHERE evaluation_id = ?)
					ORDER BY s.revision_number DESC, e.created_at DESC, e.evaluation_id DESC LIMIT 1
					""")) {
				statement.setLong(1, row.participationId());
				statement.setLong(2, row.evaluationId());
				statement.setInt(3, row.revision());
				statement.setLong(4, row.evaluationId());
				try (var r = statement.executeQuery()) { if (r.next()) previous = evaluation(connection, r.getLong(1)); }
			}
		}
		var logs = new ArrayList<Log>();
		try (var statement = connection.prepareStatement("""
				SELECT l.code_log_id, l.event_type, l.observed_at, l.snapshot_text,
				       ce.execution_status, ce.standard_input, ce.standard_output, ce.standard_error
				FROM code_logs l LEFT JOIN code_executions ce ON ce.execution_id = l.code_execution_id
				WHERE l.participation_id = ? AND (l.submission_id = ? OR
				  (l.submission_id IS NULL AND l.observed_at <= (SELECT submitted_at FROM submissions WHERE submission_id = ?)
				   AND l.observed_at > COALESCE((SELECT MAX(s.submitted_at) FROM submissions s
				     WHERE s.participation_id = ? AND s.revision_number < ?), '1000-01-01')))
				ORDER BY l.observed_at, l.code_log_id
				""")) {
			statement.setLong(1, row.participationId());
			statement.setLong(2, submissionId);
			statement.setLong(3, submissionId);
			statement.setLong(4, row.participationId());
			statement.setInt(5, row.revision());
			try (var r = statement.executeQuery()) {
				while (r.next()) logs.add(new Log(r.getLong(1), r.getString(2), date(r, "observed_at"),
						r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8)));
			}
		}
		return new TeacherReviewDetail(row, code, publishedAt, dueAt, checks, evaluation, previous, versions, logs);
	}

	private static Evaluation evaluation(Connection connection, long evaluationId) throws SQLException {
		try (var statement = connection.prepareStatement("""
				SELECT e.*, s.revision_number, r.version AS rubric_version, pv.version AS prompt_version
				FROM evaluations e JOIN submissions s ON s.submission_id = e.submission_id
				JOIN rubrics r ON r.rubric_id = e.rubric_id
				LEFT JOIN prompt_versions pv ON pv.prompt_version_id = e.prompt_version_id
				WHERE e.evaluation_id = ?
				""")) {
			statement.setLong(1, evaluationId);
			try (var r = statement.executeQuery()) {
				if (!r.next()) throw new NotFoundException();
				var result = StudentEvaluationDao.findEvaluationResult(connection, evaluationId, r.getLong("submission_id"),
						r.getInt("revision_number"), r.getString("evaluation_status"),
						r.getTimestamp("completed_at") == null ? null : r.getTimestamp("completed_at").toLocalDateTime(),
						r.getString("feedback_summary"), r.getString("process_analysis"));
				return new Evaluation(evaluationId, result.getSubmissionId(), result.getRevisionNumber(), result.getStatus(),
						date(r, "completed_at"), r.getString("rubric_version"), r.getString("prompt_version"),
						result.getFeedbackSummary(), result.getProcessAnalysis(), result.getDimensions(), result.getScores(), result.getReasons());
			}
		}
	}

	public String submittedCode(Connection connection, long teacherId, long submissionId) throws SQLException {
		permissions.requireReviewAccess(connection, teacherId, false);
		try (var statement = connection.prepareStatement("SELECT sub.submitted_code " + SCOPE + " WHERE sub.submission_id = ?")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, submissionId);
			try (var r = statement.executeQuery()) {
				if (!r.next()) throw new NotFoundException();
				return r.getString(1);
			}
		}
	}
	public void auditCsv(Connection connection, long teacherId, int count) throws SQLException {
		auditCsv(connection, teacherId, count, true);
	}
	public void auditCsv(Connection connection, long teacherId, int count, boolean evaluations) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO audit_logs (actor_user_id, actor_role, feature_code, target_type,
				  action_type, result_status, detail, occurred_at)
				VALUES (?, 'teacher', ?, ?, 'csv_export', 'success', ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherId);
			statement.setString(2, evaluations ? "evaluation-review" : "submission-review");
			statement.setString(3, evaluations ? "evaluations" : "submissions");
			statement.setString(4, "Teacher review CSV; latest consent granted only; rows=" + count);
			statement.executeUpdate();
		}
	}
	private static String date(ResultSet r, String column) throws SQLException {
		Timestamp value = r.getTimestamp(column);
		return value == null ? "" : value.toLocalDateTime().toString().replace('T', ' ');
	}
	private static Double number(ResultSet r, String column) throws SQLException {
		double value = r.getDouble(column);
		return r.wasNull() ? null : value;
	}
	public static final class NotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
