package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import entity.TeacherAccountDetails;
import entity.TeacherAccountHistory;
import entity.TeacherAccountInput;
import entity.TeacherSchoolOption;

public final class TeacherAccountDao {
	public List<TeacherAccountDetails> findAll(Connection connection) throws SQLException {
		List<TeacherAccountDetails> accounts = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT u.*, actor.login_id AS created_by FROM users u
				LEFT JOIN users actor ON actor.user_id = u.created_by_user_id
				WHERE u.user_type = 'teacher' ORDER BY u.login_id
				"""); ResultSet rows = statement.executeQuery()) {
			while (rows.next()) accounts.add(map(connection, rows));
		}
		return List.copyOf(accounts);
	}

	public TeacherAccountDetails lock(Connection connection, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT u.*, actor.login_id AS created_by FROM users u
				LEFT JOIN users actor ON actor.user_id = u.created_by_user_id
				WHERE u.user_id = ? AND u.user_type = 'teacher' FOR UPDATE
				""")) {
			statement.setLong(1, id);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new IllegalArgumentException("教師アカウントが見つかりません。");
				return map(connection, rows);
			}
		}
	}

	private TeacherAccountDetails map(Connection connection, ResultSet rows) throws SQLException {
		long id = rows.getLong("user_id");
		List<TeacherSchoolOption> schools = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT DISTINCT s.school_id, s.school_code, s.name
				FROM teacher_school_permissions p JOIN schools s ON s.school_id = p.school_id
				WHERE p.teacher_user_id = ? AND p.access_status = 'enabled' ORDER BY s.name, s.school_id
				""")) {
			statement.setLong(1, id);
			try (ResultSet schoolRows = statement.executeQuery()) {
				while (schoolRows.next()) schools.add(new TeacherSchoolOption(schoolRows.getLong(1),
						schoolRows.getString(2), schoolRows.getString(3)));
			}
		}
		var features = new LinkedHashSet<String>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT feature_code FROM teacher_feature_permissions WHERE teacher_user_id = ? AND is_enabled = TRUE")) {
			statement.setLong(1, id);
			try (ResultSet featureRows = statement.executeQuery()) {
				while (featureRows.next()) features.add(featureRows.getString(1));
			}
		}
		return new TeacherAccountDetails(id, rows.getString("login_id"), rows.getString("account_status"),
				rows.getLong("account_version"), rows.getTimestamp("created_at").toLocalDateTime(),
				rows.getString("created_by"), schools, features);
	}

	public long create(Connection connection, long actor, TeacherAccountInput input, String hash) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status,
				  created_by_user_id, created_at)
				VALUES ('teacher', ?, ?, ?, 'active', ?, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, input.loginId());
			statement.setString(2, hash);
			statement.setString(3, input.loginId());
			statement.setLong(4, actor);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("Teacher insert did not return an ID.");
				return keys.getLong(1);
			}
		}
	}

	public void permissions(Connection connection, long actor, long id, TeacherAccountInput input) throws SQLException {
		for (long school : input.schoolIds()) {
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT school_id FROM schools WHERE school_id = ? AND school_status = 'active' FOR UPDATE")) {
				statement.setLong(1, school);
				try (ResultSet rows = statement.executeQuery()) {
					if (!rows.next()) throw new IllegalArgumentException("有効な学校を選択してください。");
				}
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE teacher_school_permissions SET access_status = 'disabled',
				  updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE teacher_user_id = ?
				""")) {
			statement.setLong(1, actor); statement.setLong(2, id); statement.executeUpdate();
		}
		for (long school : input.schoolIds()) {
			int updated;
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE teacher_school_permissions SET access_status = 'enabled',
					  updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP
					WHERE teacher_user_id = ? AND school_id = ?
					""")) {
				statement.setLong(1, actor); statement.setLong(2, id); statement.setLong(3, school);
				updated = statement.executeUpdate();
			}
			if (updated > 0) continue;
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO teacher_school_permissions
					  (teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
					VALUES (?, ?, 'enabled', ?, CURRENT_TIMESTAMP)
					""")) {
				statement.setLong(1, id); statement.setLong(2, school); statement.setLong(3, actor);
				statement.executeUpdate();
			}
		}
		for (String feature : TeacherAccountInput.FEATURE_ORDER) {
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO teacher_feature_permissions
					  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
					VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
					ON DUPLICATE KEY UPDATE is_enabled = VALUES(is_enabled),
					  updated_by_user_id = VALUES(updated_by_user_id), updated_at = VALUES(updated_at)
					""")) {
				statement.setLong(1, id); statement.setString(2, feature);
				statement.setBoolean(3, input.features().contains(feature)); statement.setLong(4, actor);
				statement.executeUpdate();
			}
		}
	}

	public void update(Connection connection, long actor, long id, String status, String hash) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE users SET account_status = ?, password_hash = COALESCE(?, password_hash),
				  account_version = account_version + 1, updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP,
				  deleted_at = CASE WHEN ? = 'deleted' THEN CURRENT_TIMESTAMP ELSE deleted_at END,
				  consecutive_login_failures = CASE WHEN ? IS NULL THEN consecutive_login_failures ELSE 0 END,
				  login_locked_until = CASE WHEN ? IS NULL THEN login_locked_until ELSE NULL END WHERE user_id = ?
				""")) {
			statement.setString(1, status); statement.setString(2, hash); statement.setLong(3, actor);
			statement.setString(4, status); statement.setString(5, hash);
			statement.setString(6, hash); statement.setLong(7, id);
			if (statement.executeUpdate() != 1) throw new SQLException("Teacher update row count mismatch.");
		}
	}

	public void credentialHistory(Connection connection, long actor, long id, boolean reset) throws SQLException {
		Long resetId = null;
		if (reset) {
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO password_reset_records (target_user_id, requested_by_user_id, reset_status,
					  must_change_at_next_login, requested_at, completed_at)
					VALUES (?, ?, 'completed', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
					""", Statement.RETURN_GENERATED_KEYS)) {
				statement.setLong(1, id); statement.setLong(2, actor); statement.executeUpdate();
				try (ResultSet keys = statement.getGeneratedKeys()) {
					if (!keys.next()) throw new SQLException("Password reset ID not generated.");
					resetId = keys.getLong(1);
				}
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO credential_history (target_user_id, actor_user_id, password_reset_record_id,
				  action_type, result_status, occurred_at)
				VALUES (?, ?, ?, ?, 'success', CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, id); statement.setLong(2, actor);
			if (resetId == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, resetId);
			statement.setString(4, reset ? "password_reset" : "initial_credential_issued");
			statement.executeUpdate();
		}
	}

	public void audit(Connection connection, long actor, long id, String action, String before, String after)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (actor_user_id, actor_role, feature_code, target_type, target_id,
				  action_type, result_status, before_data, after_data, request_id, occurred_at)
				VALUES (?, 'admin', 'teacher-account-management', 'teacher', ?, ?, 'success', ?, ?, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, actor); statement.setLong(2, id); statement.setString(3, action);
			statement.setString(4, before); statement.setString(5, after);
			statement.setString(6, UUID.randomUUID().toString()); statement.executeUpdate();
		}
	}

	public List<TeacherAccountHistory> history(Connection connection, Long id, boolean login) throws SQLException {
		String sql = login ? """
				SELECT h.occurred_at, u.login_id AS actor, u.login_id AS teacher,
				  h.operation_type AS action, h.result_status AS result,
				  CONCAT_WS(' / ', h.ip_address, h.error_code) AS detail
				FROM login_history h JOIN users u ON u.user_id = h.user_id
				WHERE u.user_type = 'teacher' AND (? IS NULL OR u.user_id = ?)
				ORDER BY h.occurred_at DESC, h.login_history_id DESC LIMIT 200
				""" : """
				SELECT h.occurred_at, actor.login_id AS actor,
				  COALESCE(u.login_id, JSON_UNQUOTE(JSON_EXTRACT(h.after_data, '$.teacherId'))) AS teacher,
				  h.action_type AS action, h.result_status AS result,
				  CONCAT_WS(' / ', h.detail, h.before_data, h.after_data) AS detail
				FROM audit_logs h LEFT JOIN users u ON u.user_id = h.target_id AND u.user_type = 'teacher'
				LEFT JOIN users actor ON actor.user_id = h.actor_user_id
				WHERE h.feature_code = 'teacher-account-management' AND h.target_type = 'teacher'
				  AND (? IS NULL OR h.target_id = ?)
				ORDER BY h.occurred_at DESC, h.audit_log_id DESC LIMIT 200
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			if (id == null) { statement.setNull(1, java.sql.Types.BIGINT); statement.setNull(2, java.sql.Types.BIGINT); }
			else { statement.setLong(1, id); statement.setLong(2, id); }
			List<TeacherAccountHistory> history = new ArrayList<>();
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) history.add(new TeacherAccountHistory(
						rows.getTimestamp("occurred_at").toLocalDateTime(), rows.getString("actor"),
						rows.getString("teacher"), rows.getString("action"), rows.getString("result"),
						rows.getString("detail")));
			}

			return List.copyOf(history);
		}
	}

	public void failureAudit(Connection connection, long actor, Long id, String action, String loginId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (actor_user_id, actor_role, feature_code, target_type, target_id,
				  action_type, result_status, error_code, detail, after_data, request_id, occurred_at)
				VALUES (?, 'admin', 'teacher-account-management', 'teacher', ?, ?, 'failure',
				  'teacher_account_operation_rejected', '操作失敗（変更はロールバック済み）', ?, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, actor);
			if (id == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, id);
			statement.setString(3, action);
			var data = new com.google.gson.JsonObject();
			if (loginId != null) data.addProperty("teacherId", loginId);
			statement.setString(4, data.toString()); statement.setString(5, UUID.randomUUID().toString());
			statement.executeUpdate();
		}
	}

	public Optional<Long> sessionVersion(Connection connection, long id, String passwordHash) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT account_version FROM users WHERE user_id = ? AND user_type = 'teacher'
				  AND account_status = 'active' AND deleted_at IS NULL
				  AND (? IS NULL OR password_hash = ?)
				""")) {
			statement.setLong(1, id); statement.setString(2, passwordHash); statement.setString(3, passwordHash);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
			}
		}
	}
}
