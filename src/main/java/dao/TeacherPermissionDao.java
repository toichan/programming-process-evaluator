package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import entity.TeacherClassOption;
import entity.TeacherNavigationSummary;
import entity.TeacherSchoolOption;

public final class TeacherPermissionDao {
	private static final String TASK_MANAGEMENT_FEATURE = "task-management";

	public void requireActiveTeacher(Connection connection, long teacherUserId) throws SQLException {
		requireTeacherId(teacherUserId);
		requireTransaction(connection);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT user_id
				FROM users
				WHERE user_id = ? AND user_type = 'teacher'
				  AND account_status = 'active' AND deleted_at IS NULL
				FOR UPDATE
				""")) {
			statement.setLong(1, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SecurityException("Active teacher access is required.");
				}
			}
		}
	}

	public void requireTaskManagementAccess(Connection connection, long teacherUserId) throws SQLException {
		requireActiveTeacher(connection, teacherUserId);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT is_enabled
				FROM teacher_feature_permissions
				WHERE teacher_user_id = ? AND feature_code = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, teacherUserId);
			statement.setString(2, TASK_MANAGEMENT_FEATURE);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next() || !rows.getBoolean("is_enabled")) {
					throw new SecurityException("Task management access is not enabled.");
				}
			}
		}
	}

	public List<TeacherSchoolOption> findAuthorizedSchools(Connection connection, long teacherUserId)
			throws SQLException {
		requireTaskManagementAccess(connection, teacherUserId);
		return selectAuthorizedSchools(connection, teacherUserId);
	}

	public TeacherNavigationSummary findNavigationSummary(Connection connection, long teacherUserId)
			throws SQLException {
		requireTeacherId(teacherUserId);
		requireTransaction(connection);
		String sql = """
				SELECT COALESCE(tfp.is_enabled, 0) AS task_management_enabled,
				       s.school_id, s.school_code, s.name
				FROM users u
				LEFT JOIN teacher_feature_permissions tfp
				  ON tfp.teacher_user_id = u.user_id AND tfp.feature_code = ?
				LEFT JOIN teacher_school_permissions tsp
				  ON tsp.teacher_user_id = u.user_id AND tsp.access_status = 'enabled'
				LEFT JOIN schools s
				  ON s.school_id = tsp.school_id AND s.school_status = 'active'
				WHERE u.user_id = ? AND u.user_type = 'teacher'
				  AND u.account_status = 'active' AND u.deleted_at IS NULL
				ORDER BY s.name, s.school_id, tsp.teacher_school_permission_id
				FOR UPDATE
				""";
		Map<Long, TeacherSchoolOption> schools = new LinkedHashMap<>();
		boolean taskManagementEnabled;
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, TASK_MANAGEMENT_FEATURE);
			statement.setLong(2, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SecurityException("Active teacher access is required.");
				}
				taskManagementEnabled = rows.getBoolean("task_management_enabled");
				do {
					long schoolId = rows.getLong("school_id");
					if (!rows.wasNull()) {
						schools.putIfAbsent(schoolId,
								new TeacherSchoolOption(schoolId, rows.getString("school_code"), rows.getString("name")));
					}
				} while (rows.next());
			}
		}
		return new TeacherNavigationSummary(taskManagementEnabled, List.copyOf(schools.values()));
	}

	private List<TeacherSchoolOption> selectAuthorizedSchools(Connection connection, long teacherUserId)
			throws SQLException {
		String sql = """
				SELECT s.school_id, s.school_code, s.name
				FROM teacher_school_permissions tsp
				JOIN schools s ON s.school_id = tsp.school_id
				WHERE tsp.teacher_user_id = ? AND tsp.access_status = 'enabled'
				  AND s.school_status = 'active'
				ORDER BY s.name, s.school_id, tsp.teacher_school_permission_id
				FOR UPDATE
				""";
		Map<Long, TeacherSchoolOption> schools = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherUserId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					long schoolId = rows.getLong("school_id");
					schools.putIfAbsent(schoolId,
							new TeacherSchoolOption(schoolId, rows.getString("school_code"), rows.getString("name")));
				}
			}
		}
		return List.copyOf(schools.values());
	}

	public void requireAuthorizedSchool(Connection connection, long teacherUserId, long schoolId)
			throws SQLException {
		requireTeacherId(teacherUserId);
		requireSchoolId(schoolId);
		requireTaskManagementAccess(connection, teacherUserId);
		String sql = """
				SELECT s.school_status, tsp.access_status
				FROM schools s
				LEFT JOIN teacher_school_permissions tsp
				  ON tsp.school_id = s.school_id AND tsp.teacher_user_id = ?
				WHERE s.school_id = ?
				ORDER BY tsp.teacher_school_permission_id
				FOR UPDATE
				""";
		boolean activeSchool = false;
		boolean enabledPermission = false;
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, schoolId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					activeSchool |= "active".equals(rows.getString("school_status"));
					enabledPermission |= "enabled".equals(rows.getString("access_status"));
				}
			}
		}
		if (!activeSchool || !enabledPermission) {
			throw new SecurityException("School access is not enabled.");
		}
	}

	public void requireAuthorizedClass(Connection connection, long teacherUserId, long classroomId)
			throws SQLException {
		requireTeacherId(teacherUserId);
		if (classroomId < 1) {
			throw new IllegalArgumentException("A valid class is required.");
		}
		requireTaskManagementAccess(connection, teacherUserId);
		String sql = """
				SELECT c.classroom_status, s.school_status, tsp.access_status
				FROM classrooms c
				JOIN schools s ON s.school_id = c.school_id
				LEFT JOIN teacher_school_permissions tsp
				  ON tsp.school_id = s.school_id AND tsp.teacher_user_id = ?
				WHERE c.classroom_id = ?
				ORDER BY tsp.teacher_school_permission_id
				FOR UPDATE
				""";
		boolean activeClass = false;
		boolean activeSchool = false;
		boolean enabledPermission = false;
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, teacherUserId);
			statement.setLong(2, classroomId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					activeClass |= "active".equals(rows.getString("classroom_status"));
					activeSchool |= "active".equals(rows.getString("school_status"));
					enabledPermission |= "enabled".equals(rows.getString("access_status"));
				}
			}
		}
		if (!activeClass || !activeSchool || !enabledPermission) {
			throw new SecurityException("Class access is not enabled.");
		}
	}

	public List<TeacherClassOption> findAuthorizedClasses(
			Connection connection,
			long teacherUserId,
			long schoolId) throws SQLException {
		requireAuthorizedSchool(connection, teacherUserId, schoolId);
		String sql = """
				SELECT c.classroom_id, c.school_id, c.name, c.grade_name
				FROM classrooms c
				JOIN schools s ON s.school_id = c.school_id
				WHERE c.school_id = ? AND c.classroom_status = 'active'
				  AND s.school_status = 'active'
				ORDER BY c.grade_name, c.name, c.classroom_id
				FOR UPDATE
				""";
		List<TeacherClassOption> classes = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, schoolId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					classes.add(new TeacherClassOption(
							rows.getLong("classroom_id"),
							rows.getLong("school_id"),
							rows.getString("name"),
							rows.getString("grade_name")));
				}
			}
		}
		return List.copyOf(classes);
	}

	private static void requireTransaction(Connection connection) throws SQLException {
		if (connection == null) {
			throw new IllegalArgumentException("A database connection is required.");
		}
		if (connection.getAutoCommit()) {
			throw new IllegalStateException("Teacher permission checks require an active transaction.");
		}
	}

	private static void requireTeacherId(long teacherUserId) {
		if (teacherUserId < 1) {
			throw new SecurityException("Active teacher access is required.");
		}
	}

	private static void requireSchoolId(long schoolId) {
		if (schoolId < 1) {
			throw new IllegalArgumentException("A valid school is required.");
		}
	}
}
