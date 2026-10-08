package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import entity.ManagedStudentAccount;
import entity.StudentAccountCreation;
import entity.TeacherClassOption;

public final class StudentAccountManagementDao {
	private static final String ACCOUNTS = """
			SELECT u.user_id, u.login_id, u.account_version, u.account_status, u.created_at,
			  p.school_id, s.name AS school_name, s.security_level, p.first_login_status, p.must_change_password,
			  creator.login_id AS created_by, updater.login_id AS updated_by,
			  u.login_locked_until > UTC_TIMESTAMP() AS login_locked,
			  (tc.student_user_id IS NOT NULL AND u.account_status <> 'deleted'
			    AND (s.security_level = 1 OR p.must_change_password OR p.first_login_status <> 'completed')) AS password_available,
			  COALESCE((SELECT cr.consent_status FROM consent_records cr WHERE cr.user_id=u.user_id
			    ORDER BY cr.consent_id DESC LIMIT 1), 'unconfirmed') AS consent
			FROM users u JOIN student_profiles p ON p.user_id=u.user_id
			JOIN schools s ON s.school_id=p.school_id AND s.school_status='active'
			LEFT JOIN users creator ON creator.user_id=u.created_by_user_id
			LEFT JOIN users updater ON updater.user_id=u.updated_by_user_id
			LEFT JOIN student_teacher_credentials tc ON tc.student_user_id=u.user_id
			WHERE u.user_type='student' AND EXISTS (
			  SELECT 1 FROM teacher_school_permissions tsp WHERE tsp.teacher_user_id=?
			    AND tsp.school_id=p.school_id AND tsp.access_status='enabled')
			""";

	public List<ManagedStudentAccount> list(Connection connection, long teacher) throws SQLException {
		List<ManagedStudentAccount> result = new ArrayList<>();
		try (var statement = connection.prepareStatement(ACCOUNTS + " ORDER BY u.login_id, u.user_id")) {
			statement.setLong(1, teacher);
			try (var rows = statement.executeQuery()) { while (rows.next()) result.add(account(connection, rows)); }
		}
		return List.copyOf(result);
	}

	public ManagedStudentAccount lock(Connection connection, long teacher, long id) throws SQLException {
		try (var statement = connection.prepareStatement(ACCOUNTS + " AND u.user_id=? FOR UPDATE")) {
			statement.setLong(1, teacher); statement.setLong(2, id);
			try (var rows = statement.executeQuery()) {
				if (!rows.next()) throw new SecurityException("Student school access is not enabled.");
				return account(connection, rows);
			}
		}
	}

	private ManagedStudentAccount account(Connection connection, ResultSet rows) throws SQLException {
		long id = rows.getLong("user_id");
		List<TeacherClassOption> classes = new ArrayList<>();
		try (var statement = connection.prepareStatement("""
				SELECT c.classroom_id,c.school_id,c.name,c.grade_name FROM student_class_memberships m
				JOIN classrooms c ON c.classroom_id=m.classroom_id
				WHERE m.student_user_id=? AND m.membership_status='active' ORDER BY c.classroom_id
				""")) {
			statement.setLong(1, id);
			try (var memberships = statement.executeQuery()) {
				while (memberships.next()) classes.add(new TeacherClassOption(memberships.getLong(1), memberships.getLong(2),
						memberships.getString(3), memberships.getString(4)));
			}
		}
		return new ManagedStudentAccount(id, rows.getString("login_id"), rows.getLong("account_version"),
				rows.getString("account_status"), rows.getLong("school_id"), rows.getString("school_name"),
				rows.getInt("security_level"), classes, rows.getString("first_login_status"),
				rows.getBoolean("must_change_password"), rows.getString("consent"), rows.getBoolean("password_available"),
				rows.getBoolean("login_locked"), rows.getTimestamp("created_at").toLocalDateTime().toString(),
				text(rows.getString("created_by")), text(rows.getString("updated_by")));
	}

	public List<Map<String, Object>> options(Connection connection, long teacher) throws SQLException {
		List<Map<String, Object>> result = new ArrayList<>();
		try (var statement = connection.prepareStatement("""
				SELECT s.school_id,s.name,s.security_level,c.classroom_id,c.name AS class_name,c.grade_name
				FROM schools s LEFT JOIN classrooms c ON c.school_id=s.school_id AND c.classroom_status='active'
				WHERE s.school_status='active' AND s.security_level IN (1,2) AND EXISTS (
				  SELECT 1 FROM teacher_school_permissions tsp WHERE tsp.teacher_user_id=?
				  AND tsp.school_id=s.school_id AND tsp.access_status='enabled')
				ORDER BY s.name,s.school_id,c.classroom_id
				""")) {
			statement.setLong(1, teacher);
			try (var rows = statement.executeQuery()) {
				while (rows.next()) result.add(Map.of("schoolId", rows.getLong("school_id"), "schoolName", rows.getString("name"),
						"securityLevel", rows.getInt("security_level"), "classroomId", rows.getLong("classroom_id"),
						"classroomName", text(rows.getString("class_name")), "gradeName", text(rows.getString("grade_name"))));
			}
		}
		return List.copyOf(result);
	}

	public long resolveClass(Connection connection, StudentAccountCreation input) throws SQLException {
		if (input.classroomId() > 0) return input.classroomId();
		try (var statement = connection.prepareStatement("""
				SELECT classroom_id FROM classrooms WHERE school_id=? AND name=? AND classroom_status='active'
				ORDER BY classroom_id LIMIT 1 FOR UPDATE
				""")) {
			statement.setLong(1, input.schoolId()); statement.setString(2, input.classroomName());
			try (var rows = statement.executeQuery()) { if (rows.next()) return rows.getLong(1); }
		}
		try (var statement = connection.prepareStatement("""
				INSERT INTO classrooms (school_id,name,classroom_status,created_at) VALUES (?,?,'active',UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, input.schoolId()); statement.setString(2, input.classroomName());
			return insertedId(statement);
		}
	}

	public String nextLoginId(Connection connection) throws SQLException {
		long next;
		try (var statement = connection.prepareStatement("SELECT next_number FROM student_login_sequence WHERE singleton_id=1 FOR UPDATE");
				var rows = statement.executeQuery()) {
			if (!rows.next()) throw new SQLException("Student login sequence is missing.");
			next = rows.getLong(1);
		}
		try (var statement = connection.prepareStatement("""
				SELECT COALESCE(MAX(CAST(SUBSTRING(login_id,2) AS UNSIGNED)),0)+1
				FROM users WHERE login_id REGEXP '^s[0-9]{1,15}$'
				"""); var rows = statement.executeQuery()) {
			if (!rows.next()) throw new SQLException("Student login ID maximum could not be read.");
			next = Math.max(next, rows.getLong(1));
		}
		String id;
		do {
			if (next >= 1_000_000_000_000_000L) throw new IllegalArgumentException("生徒IDの採番上限に達しました。");
			id = "s" + String.format(java.util.Locale.ROOT, "%03d", next++);
		} while (loginIdExists(connection, id));
		try (var statement = connection.prepareStatement("UPDATE student_login_sequence SET next_number=? WHERE singleton_id=1")) {
			statement.setLong(1, next); statement.executeUpdate();
		}
		return id;
	}

	private boolean loginIdExists(Connection connection, String id) throws SQLException {
		try (var statement = connection.prepareStatement("SELECT 1 FROM users WHERE login_id=?")) {
			statement.setString(1, id); try (var rows = statement.executeQuery()) { return rows.next(); }
		}
	}

	public long create(Connection connection, long actor, String loginId, String hash, long school, long classroom) throws SQLException {
		long id;
		try (var statement = connection.prepareStatement("""
				INSERT INTO users (user_type,login_id,password_hash,display_name,account_status,
				  created_by_user_id,updated_by_user_id,created_at)
				VALUES ('student',?,?,?,'active',?,?,UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, loginId); statement.setString(2, hash); statement.setString(3, loginId);
			statement.setLong(4, actor); statement.setLong(5, actor); id = insertedId(statement);
		}
		try (var statement = connection.prepareStatement("""
				INSERT INTO student_profiles (user_id,student_code,school_id,security_level,first_login_status,must_change_password)
				VALUES (?,?,?,1,'not_logged_in',false)
				""")) {
			statement.setLong(1, id); statement.setString(2, loginId); statement.setLong(3, school); statement.executeUpdate();
		}
		try (var statement = connection.prepareStatement("""
				INSERT INTO student_class_memberships (student_user_id,classroom_id,membership_status,joined_at)
				VALUES (?,?,'active',UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, id); statement.setLong(2, classroom); statement.executeUpdate();
		}
		try (var statement = connection.prepareStatement("""
				INSERT INTO research_subject_identifiers (student_user_id,research_subject_code,generated_at,generated_by_user_id)
				VALUES (?,?,UTC_TIMESTAMP(),?)
				""")) {
			statement.setLong(1, id); statement.setString(2, UUID.randomUUID().toString()); statement.setLong(3, actor); statement.executeUpdate();
		}
		credentialHistory(connection, actor, id, false);
		return id;
	}

	public void saveCredential(Connection connection, long id, String encrypted) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO student_teacher_credentials (student_user_id,encrypted_password,updated_at)
				VALUES (?,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE encrypted_password=VALUES(encrypted_password),updated_at=VALUES(updated_at)
				""")) {
			statement.setLong(1, id); statement.setString(2, encrypted); statement.executeUpdate();
		}
	}

	public String encryptedCredential(Connection connection, long id) throws SQLException {
		try (var statement = connection.prepareStatement("SELECT encrypted_password FROM student_teacher_credentials WHERE student_user_id=?")) {
			statement.setLong(1, id);
			try (var rows = statement.executeQuery()) {
				if (!rows.next()) throw new IllegalArgumentException("このパスワードは確認できません。");
				return rows.getString(1);
			}
		}
	}

	public void change(Connection connection, long actor, long id, String status, String hash, boolean unlock) throws SQLException {
		try (var statement = connection.prepareStatement("""
				UPDATE users SET account_status=?, password_hash=COALESCE(?,password_hash), account_version=account_version+1,
				  updated_by_user_id=?,updated_at=UTC_TIMESTAMP(),deleted_at=IF(?='deleted',UTC_TIMESTAMP(),deleted_at),
				  consecutive_login_failures=IF(?,0,consecutive_login_failures),
				  login_locked_until=IF(?,NULL,login_locked_until) WHERE user_id=? AND user_type='student'
				""")) {
			statement.setString(1, status); statement.setString(2, hash); statement.setLong(3, actor);
			statement.setString(4, status); statement.setBoolean(5, unlock); statement.setBoolean(6, unlock); statement.setLong(7, id);
			if (statement.executeUpdate() != 1) throw new SQLException("Student account update failed.");
		}
		if (hash != null) {
			try (var statement = connection.prepareStatement("""
					UPDATE student_profiles SET first_login_status='password_change_required',must_change_password=true WHERE user_id=?
					""")) { statement.setLong(1, id); statement.executeUpdate(); }
			credentialHistory(connection, actor, id, true);
		}
	}

	private void credentialHistory(Connection connection, long actor, long id, boolean reset) throws SQLException {
		Long resetId = null;
		if (reset) {
			try (var statement = connection.prepareStatement("""
					INSERT INTO password_reset_records (target_user_id,requested_by_user_id,reset_status,
					  must_change_at_next_login,requested_at,completed_at)
					VALUES (?,?,'completed',true,UTC_TIMESTAMP(),UTC_TIMESTAMP())
					""", Statement.RETURN_GENERATED_KEYS)) {
				statement.setLong(1, id); statement.setLong(2, actor); resetId = insertedId(statement);
			}
		}
		try (var statement = connection.prepareStatement("""
				INSERT INTO credential_history (target_user_id,actor_user_id,password_reset_record_id,action_type,result_status,occurred_at)
				VALUES (?,?,?,?,'success',UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, id); statement.setLong(2, actor);
			if (resetId == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, resetId);
			statement.setString(4, reset ? "password_reset" : "initial_credential_issued"); statement.executeUpdate();
		}
	}

	public List<Map<String, String>> history(Connection connection, long id, String kind) throws SQLException {
		String sql = switch (kind) {
			case "login" -> """
				SELECT h.occurred_at,u.login_id AS actor,h.operation_type AS action,h.result_status AS result,
				  COALESCE(h.error_code,'') AS detail FROM login_history h LEFT JOIN users u ON u.user_id=h.user_id
				WHERE h.user_id=? ORDER BY h.login_history_id DESC LIMIT 200
				""";
			case "credentials" -> """
				SELECT h.occurred_at,u.login_id AS actor,h.action_type AS action,h.result_status AS result,
				  COALESCE(h.error_code,'') AS detail FROM credential_history h LEFT JOIN users u ON u.user_id=h.actor_user_id
				WHERE h.target_user_id=? ORDER BY h.credential_history_id DESC LIMIT 200
				""";
			case "operations" -> """
				SELECT h.occurred_at,u.login_id AS actor,h.action_type AS action,h.result_status AS result,
				  COALESCE(h.detail,'') AS detail FROM audit_logs h LEFT JOIN users u ON u.user_id=h.actor_user_id
				WHERE h.target_id=? AND h.feature_code='account-management' AND h.target_type='student'
				ORDER BY h.audit_log_id DESC LIMIT 200
				""";
			default -> throw new IllegalArgumentException("Invalid history kind.");
		};
		List<Map<String, String>> result = new ArrayList<>();
		try (var statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id); try (var rows = statement.executeQuery()) {
				while (rows.next()) result.add(Map.of("occurredAt", rows.getTimestamp(1).toLocalDateTime().toString(),
						"actor", text(rows.getString(2)), "action", rows.getString(3), "result", rows.getString(4), "detail", rows.getString(5)));
			}
		}
		return List.copyOf(result);
	}

	public void audit(Connection connection, long actor, Long id, String action, boolean success, String detail) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO audit_logs (actor_user_id,actor_role,feature_code,target_type,target_id,action_type,result_status,detail,request_id,occurred_at)
				VALUES (?,'teacher','account-management','student',?,?,?,?,?,UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, actor);
			if (id == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, id);
			statement.setString(3, action); statement.setString(4, success ? "success" : "failure");
			statement.setString(5, detail); statement.setString(6, UUID.randomUUID().toString()); statement.executeUpdate();
		}
	}

	private static long insertedId(PreparedStatement statement) throws SQLException {
		statement.executeUpdate();
		try (var keys = statement.getGeneratedKeys()) {
			if (!keys.next()) throw new SQLException("Generated ID missing.");
			return keys.getLong(1);
		}
	}
	private static String text(String value) { return value == null ? "" : value; }
}
