package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import entity.TeacherProgressActivity;
import entity.TeacherProgressCode;
import entity.TeacherProgressRow;
import entity.TeacherProgressStatus;

public final class TeacherProgressDao {
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private final TeacherPermissionDao permissionDao;

	public TeacherProgressDao() {
		this(new TeacherPermissionDao());
	}

	TeacherProgressDao(TeacherPermissionDao permissionDao) {
		this.permissionDao = java.util.Objects.requireNonNull(permissionDao);
	}

	public List<TeacherProgressRow> findRows(Connection connection, long teacherUserId) throws SQLException {
		permissionDao.requireTaskProgressAccess(connection, teacherUserId);
		String sql = """
				SELECT tca.task_class_assignment_id, u.user_id AS student_user_id, u.login_id,
				       s.school_id, s.name AS school_name, c.classroom_id,
				       CONCAT_WS(' ', NULLIF(c.grade_name, ''), c.name) AS class_name,
				       t.task_id, t.title AS task_name, COALESCE(t.difficulty, 'none') AS difficulty,
				       p.participation_id, p.progress_status, p.learning_status, p.evaluation_status,
				       p.active_duration_seconds, p.last_activity_at, p.draft_updated_at,
				       tca.assignment_status, tca.late_submission_policy, tca.due_at,
				       latest_submission.submitted_at,
				       latest_evaluation.evaluation_status AS latest_evaluation_status,
				       latest_evaluation.created_at AS latest_evaluation_created_at,
				       latest_evaluation.final_evaluated_at AS latest_evaluation_completed_at,
				       NULLIF(GREATEST(
				           COALESCE(p.last_activity_at, '1000-01-01 00:00:00'),
				           COALESCE(p.draft_updated_at, '1000-01-01 00:00:00'),
				           COALESCE(latest_submission.submitted_at, '1000-01-01 00:00:00'),
				           COALESCE(latest_evaluation.created_at, '1000-01-01 00:00:00'),
				           COALESCE(latest_evaluation.final_evaluated_at, '1000-01-01 00:00:00')
				       ), '1000-01-01 00:00:00') AS last_updated_at,
				       current_consent.consent_status
				FROM task_class_assignments tca
				JOIN tasks t ON t.task_id = tca.task_id
				JOIN classrooms c ON c.classroom_id = tca.classroom_id
				JOIN schools s ON s.school_id = c.school_id AND s.school_id = t.school_id
				JOIN teacher_school_permissions tsp
				  ON tsp.school_id = s.school_id AND tsp.teacher_user_id = ? AND tsp.access_status = 'enabled'
				JOIN student_class_memberships scm
				  ON scm.classroom_id = c.classroom_id AND scm.membership_status = 'active'
				JOIN student_profiles sp ON sp.user_id = scm.student_user_id AND sp.school_id = s.school_id
				JOIN users u ON u.user_id = scm.student_user_id AND u.user_type = 'student'
				  AND u.account_status = 'active' AND u.deleted_at IS NULL
				LEFT JOIN task_participations p
				  ON p.student_user_id = u.user_id
				 AND p.task_class_assignment_id = tca.task_class_assignment_id
				LEFT JOIN (
				    SELECT s1.participation_id, s1.submission_id, s1.revision_number, s1.submitted_at
				    FROM submissions s1
				    JOIN (
				        SELECT participation_id, MAX(revision_number) AS revision_number
				        FROM submissions
				        GROUP BY participation_id
				    ) latest
				      ON latest.participation_id = s1.participation_id
				     AND latest.revision_number = s1.revision_number
				) latest_submission ON latest_submission.participation_id = p.participation_id
				LEFT JOIN (
				    SELECT e1.submission_id, e1.evaluation_status, e1.created_at, e1.final_evaluated_at
				    FROM (
				        SELECT e.*,
				               ROW_NUMBER() OVER (
				                   PARTITION BY e.submission_id
				                   ORDER BY e.created_at DESC, e.evaluation_id DESC
				               ) AS evaluation_rank
				        FROM evaluations e
				    ) e1
				    WHERE e1.evaluation_rank = 1
				) latest_evaluation ON latest_evaluation.submission_id = latest_submission.submission_id
				LEFT JOIN (
				    SELECT cr1.user_id, cr1.consent_status
				    FROM consent_records cr1
				    JOIN (
				        SELECT user_id, MAX(consent_id) AS consent_id
				        FROM consent_records
				        GROUP BY user_id
				    ) current_record
				      ON current_record.consent_id = cr1.consent_id
				) current_consent ON current_consent.user_id = u.user_id
				WHERE c.classroom_status = 'active' AND s.school_status = 'active'
				  AND (tca.assignment_status IN ('published', 'requires_update', 'expired')
				       OR (tca.assignment_status = 'archived' AND p.participation_id IS NOT NULL))
				  AND (t.publication_status IN ('published', 'requires_update')
				       OR p.participation_id IS NOT NULL)
				  AND (t.deleted_at IS NULL OR p.participation_id IS NOT NULL)
				ORDER BY u.login_id, t.title, t.task_id, tca.task_class_assignment_id
				""";
		List<TeacherProgressRow> rows = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherUserId);
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					Long participationId = nullableLong(result, "participation_id");
					long seconds = result.getLong("active_duration_seconds");
					String difficulty = result.getString("difficulty");
					String status = TeacherProgressStatus.label(
							result.getString("progress_status"),
							result.getString("latest_evaluation_status"),
							result.getTimestamp("due_at"),
							result.getString("assignment_status"),
							result.getString("late_submission_policy"));
					rows.add(new TeacherProgressRow(
							result.getLong("task_class_assignment_id"),
							result.getLong("student_user_id"),
							participationId,
							result.getString("login_id"),
							result.getString("school_name"),
							result.getLong("school_id"),
							result.getString("class_name"),
							result.getLong("classroom_id"),
							result.getString("task_name"),
							result.getLong("task_id"),
							difficulty,
							difficultyLabel(difficulty),
							status,
							seconds,
							formatDuration(seconds),
							formatDateTime(result.getTimestamp("last_updated_at")),
							consentLabel(result.getString("consent_status"))));
				}
			}
		}
		return List.copyOf(rows);
	}

	public TeacherProgressCode findLatestCode(Connection connection, long participationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT current_draft_code, draft_updated_at
				FROM task_participations
				WHERE participation_id = ? AND learning_status <> 'completed'
				  AND current_draft_code IS NOT NULL AND draft_updated_at IS NOT NULL
				""")) {
			statement.setLong(1, participationId);
			try (ResultSet result = statement.executeQuery()) {
				if (result.next()) {
					return new TeacherProgressCode(result.getString("current_draft_code"),
							formatDateTime(result.getTimestamp("draft_updated_at")), "draft");
				}
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT submitted_code, submitted_at FROM submissions
				WHERE participation_id = ? ORDER BY revision_number DESC LIMIT 1
				""")) {
			statement.setLong(1, participationId);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? new TeacherProgressCode(result.getString("submitted_code"),
						formatDateTime(result.getTimestamp("submitted_at")), "submission") : null;
			}
		}
	}

	public List<TeacherProgressActivity> findActivities(Connection connection, long participationId)
			throws SQLException {
		String sql = """
				SELECT activity_time, activity_type, activity_result, activity_note
				FROM (
				    SELECT cl.observed_at AS activity_time,
				           CASE
				               WHEN cl.code_execution_id IS NOT NULL THEN '実行'
				               WHEN cl.event_type = 'manual_save' THEN '手動保存'
				               WHEN cl.event_type = 'submission' THEN '提出'
				               ELSE 'コード記録'
				           END AS activity_type,
				           CASE
				               WHEN ce.execution_status = 'succeeded' THEN '成功'
				               WHEN ce.execution_status = 'failed' THEN '失敗'
				               WHEN ce.execution_status = 'timed_out' THEN '時間切れ'
				               WHEN cl.code_execution_id IS NOT NULL THEN '実行中'
				               ELSE '完了'
				           END AS activity_result,
				           CASE
				               WHEN cl.code_execution_id IS NOT NULL
				                   THEN COALESCE(ce.error_message, 'コード実行')
				               WHEN cl.event_type = 'manual_save' THEN 'コードを手動保存'
				               WHEN cl.event_type = 'submission' THEN '課題を提出'
				               WHEN cl.event_type = 'periodic_snapshot' THEN '定期コード記録'
				               ELSE 'コード変更を記録'
				           END AS activity_note
				    FROM code_logs cl
				    LEFT JOIN code_executions ce ON ce.execution_id = cl.code_execution_id
				    WHERE cl.participation_id = ?
				    UNION ALL
				    SELECT ce.executed_at, '実行',
				           CASE
				               WHEN ce.execution_status = 'succeeded' THEN '成功'
				               WHEN ce.execution_status = 'failed' THEN '失敗'
				               WHEN ce.execution_status = 'timed_out' THEN '時間切れ'
				               ELSE '実行中'
				           END,
				           COALESCE(ce.error_message, 'コード実行')
				    FROM code_executions ce
				    WHERE ce.participation_id = ? AND ce.execution_context = 'student_task'
				      AND NOT EXISTS (
				          SELECT 1 FROM code_logs cl WHERE cl.code_execution_id = ce.execution_id
				      )
				    UNION ALL
				    SELECT e.created_at, '評価',
				           CASE
				               WHEN e.evaluation_status = 'completed' THEN '完了'
				               WHEN e.evaluation_status = 'needs_revision' THEN '要対応'
				               ELSE e.evaluation_status
				           END,
				           CONCAT('提出版 #', s.revision_number, ' の評価')
				    FROM evaluations e
				    JOIN submissions s ON s.submission_id = e.submission_id
				    WHERE s.participation_id = ?
				) activity
				ORDER BY activity_time DESC
				LIMIT 100
				""";
		List<TeacherProgressActivity> activities = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, participationId);
			statement.setLong(2, participationId);
			statement.setLong(3, participationId);
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					activities.add(new TeacherProgressActivity(
							formatDateTime(result.getTimestamp("activity_time")),
							result.getString("activity_type"),
							result.getString("activity_result"),
							result.getString("activity_note")));
				}
			}
		}
		return List.copyOf(activities);
	}

	private static Long nullableLong(ResultSet result, String column) throws SQLException {
		long value = result.getLong(column);
		return result.wasNull() ? null : value;
	}

	private static String formatDateTime(Timestamp timestamp) {
		if (timestamp == null) {
			return "";
		}
		LocalDateTime value = timestamp.toLocalDateTime();
		return DATE_TIME_FORMAT.format(value);
	}

	static String formatDuration(long seconds) {
		long safeSeconds = Math.max(0, seconds);
		return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
				safeSeconds / 3600, (safeSeconds % 3600) / 60, safeSeconds % 60);
	}

	static String difficultyLabel(String difficulty) {
		return switch (difficulty) {
			case "beginner" -> "初級";
			case "intermediate" -> "中級";
			case "advanced" -> "上級";
			case "none" -> "なし";
			default -> "未設定";
		};
	}

	static String consentLabel(String consentStatus) {
		return switch (consentStatus == null ? "unconfirmed" : consentStatus) {
			case "agreed" -> "同意";
			case "declined", "withdrawn" -> "不同意";
			default -> "未確認";
		};
	}
}
