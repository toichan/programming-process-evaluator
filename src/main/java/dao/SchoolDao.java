package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import entity.SchoolDetails;
import entity.SchoolInput;

public final class SchoolDao {
	public void requireActiveAdmin(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT user_id FROM users WHERE user_id = ? AND user_type = 'admin'
				  AND account_status = 'active' AND deleted_at IS NULL FOR UPDATE
				""")) {
			statement.setLong(1, userId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new SecurityException("Active admin authentication is required.");
			}
		}
	}

	public List<SchoolDetails> findAll(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT school_id, school_code, name, security_level, security_level_locked, version "
						+ "FROM schools ORDER BY name, school_id");
				ResultSet rows = statement.executeQuery()) {
			List<SchoolDetails> schools = new ArrayList<>();
			while (rows.next()) schools.add(map(rows));
			return List.copyOf(schools);
		}
	}

	public Optional<SchoolDetails> findForUpdate(Connection connection, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT school_id, school_code, name, security_level, security_level_locked, version
				FROM schools WHERE school_id = ? FOR UPDATE
				""")) {
			statement.setLong(1, id);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? Optional.of(map(rows)) : Optional.empty();
			}
		}
	}

	public long insert(Connection connection, SchoolInput input) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO schools (school_code, name, security_level, school_status, created_at)
				VALUES (?, ?, ?, 'active', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, "school-" + UUID.randomUUID());
			statement.setString(2, input.name());
			statement.setInt(3, input.securityLevel());
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("School insert did not return an identifier.");
				return keys.getLong(1);
			}
		}
	}

	public void update(Connection connection, long id, SchoolInput input) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE schools SET name = ?, security_level = ?, version = version + 1,
				  updated_at = CURRENT_TIMESTAMP WHERE school_id = ?
				""")) {
			statement.setString(1, input.name());
			statement.setInt(2, input.securityLevel());
			statement.setLong(3, id);
			if (statement.executeUpdate() != 1) throw new SQLException("School update affected an unexpected row count.");
		}
	}

	public void audit(Connection connection, long actorId, long schoolId, String action,
			SchoolDetails before, SchoolInput input) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (actor_user_id, actor_role, feature_code, target_type, target_id,
				  action_type, result_status, detail, request_id, occurred_at)
				VALUES (?, 'admin', 'school-management', 'school', ?, ?, 'success', ?, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, actorId);
			statement.setLong(2, schoolId);
			statement.setString(3, action);
			statement.setString(4, "Before: " + (before == null ? "(new)" : before.name()
					+ " / level=" + before.securityLevel()) + "; After: " + input.name()
					+ " / level=" + input.securityLevel());
			statement.setString(5, UUID.randomUUID().toString());
			statement.executeUpdate();
		}
	}

	private static SchoolDetails map(ResultSet rows) throws SQLException {
		int level = rows.getInt("security_level");
		Integer nullableLevel = rows.wasNull() ? null : level;
		return new SchoolDetails(rows.getLong("school_id"), rows.getString("school_code"),
				rows.getString("name"), nullableLevel, rows.getBoolean("security_level_locked"),
				rows.getLong("version"));
	}
}
