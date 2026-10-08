package dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import entity.TeacherExerciseDetail;
import entity.TeacherExerciseRow;
import entity.ExerciseNotFoundException;

public final class TeacherExerciseDao {
	private final TeacherPermissionDao permissions = new TeacherPermissionDao();
	private final StudentExerciseDao exercises = new StudentExerciseDao();
	private static final String MEMBERS = """
			FROM users u
			JOIN student_profiles p ON p.user_id = u.user_id
			JOIN student_class_memberships m ON m.student_user_id = u.user_id AND m.membership_status = 'active'
			JOIN classrooms c ON c.classroom_id = m.classroom_id AND c.school_id = p.school_id
			JOIN schools s ON s.school_id = c.school_id
			JOIN teacher_school_permissions access ON access.school_id = s.school_id
			  AND access.teacher_user_id = ? AND access.access_status = 'enabled'
			WHERE u.user_type = 'student' AND u.account_status = 'active' AND u.deleted_at IS NULL
			  AND c.classroom_status = 'active' AND s.school_status = 'active'
			""";
	public List<TeacherExerciseRow> rows(Connection connection, long teacherId) throws SQLException {
		permissions.requireExerciseReviewAccess(connection, teacherId);
		String sql = """
				WITH RECURSIVE visible AS (
				  SELECT e.exercise_entry_id, e.student_exercise_id, e.entry_type, e.updated_at
				  FROM student_exercise_entries e JOIN student_exercises x
				    ON x.student_exercise_id = e.student_exercise_id
				  WHERE e.parent_entry_id IS NULL AND e.entry_status = 'active'
				    AND e.trash_root_entry_id IS NULL AND x.deleted_at IS NULL
				    AND x.exercise_status <> 'archived' AND x.merged_into_exercise_id IS NULL
				  UNION ALL
				  SELECT e.exercise_entry_id, e.student_exercise_id, e.entry_type, e.updated_at
				  FROM student_exercise_entries e JOIN visible parent ON parent.exercise_entry_id = e.parent_entry_id
				    AND parent.student_exercise_id = e.student_exercise_id AND parent.entry_type = 'folder'
				  WHERE e.entry_status = 'active' AND e.trash_root_entry_id IS NULL
				), totals AS (
				  SELECT x.student_user_id, SUM(v.entry_type = 'file') AS file_count, COUNT(*) AS entry_count,
				    MAX(v.updated_at) AS updated_at
				  FROM visible v JOIN student_exercises x ON x.student_exercise_id = v.student_exercise_id
				  GROUP BY x.student_user_id
				)
				SELECT u.user_id, u.login_id, s.school_id, s.name AS school_name, c.classroom_id,
				  CONCAT_WS(' ', NULLIF(c.grade_name,''), c.name) AS class_name,
				  COALESCE((SELECT file_count FROM totals WHERE student_user_id=u.user_id),0) AS file_count,
				  COALESCE((SELECT entry_count FROM totals WHERE student_user_id=u.user_id),0) AS entry_count,
				  (SELECT updated_at FROM totals WHERE student_user_id=u.user_id) AS updated_at,
				  COALESCE((SELECT cr.consent_status FROM consent_records cr WHERE cr.user_id=u.user_id
				    ORDER BY cr.consent_id DESC LIMIT 1),'unconfirmed') AS consent
				""" + MEMBERS + " ORDER BY u.login_id, c.classroom_id";
		var result = new ArrayList<TeacherExerciseRow>();
		try (var statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherId);
			try (var r = statement.executeQuery()) {
				while (r.next()) {
					var updated = r.getTimestamp("updated_at");
					result.add(new TeacherExerciseRow(r.getLong("user_id"), r.getString("login_id"),
							r.getLong("school_id"), r.getString("school_name"), r.getLong("classroom_id"),
							r.getString("class_name"), r.getInt("file_count"), r.getInt("entry_count"),
							updated == null ? "" : updated.toLocalDateTime().toString(), r.getString("consent")));
				}
			}
		}
		return List.copyOf(result);
	}
	public TeacherExerciseDetail detail(Connection connection, long teacherId, long studentId, long classroomId)
			throws SQLException {
		var row = rows(connection, teacherId).stream()
				.filter(item -> item.studentId() == studentId && item.classroomId() == classroomId)
				.findFirst().orElseThrow(ExerciseNotFoundException::new);
		var scopes = new ArrayList<TeacherExerciseDetail.Scope>();
		for (var scope : exercises.findScopes(connection, studentId)) {
			scopes.add(new TeacherExerciseDetail.Scope(scope.exerciseId(), scope.name(),
					exercises.downloadableEntries(exercises.findEntries(connection, scope.exerciseId()))));
		}
		return new TeacherExerciseDetail(row, scopes);
	}
	public void auditCsv(Connection connection, long teacherId, int count) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO audit_logs (actor_user_id, actor_role, feature_code, target_type,
				  action_type, result_status, detail, occurred_at)
				VALUES (?, 'teacher', 'exercise-code-review', 'student_exercises',
				  'csv_export', 'success', ?, NOW())
				""")) {
			statement.setLong(1, teacherId);
			statement.setString(2, "{\"rowCount\":" + count + "}");
			statement.executeUpdate();
		}
	}
}
