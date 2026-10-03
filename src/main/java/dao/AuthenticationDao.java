package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import entity.UserCredential;
import entity.UserCredential.UserType;

public final class AuthenticationDao {
	private final UserDao userDao = new UserDao();

	public Optional<UserCredential> findByLoginIdForUpdate(Connection connection, String loginId)
			throws SQLException {
		return userDao.findByLoginIdForUpdate(connection, loginId);
	}

	public Optional<UserCredential> findByUserIdForUpdate(Connection connection, long userId) throws SQLException {
		return userDao.findByUserIdForUpdate(connection, userId);
	}

	public boolean hasActiveUserRole(Connection connection, long userId, UserType userType) throws SQLException {
		String sql = """
				SELECT 1
				FROM users
				WHERE user_id = ? AND user_type = ? AND account_status = 'active'
				FOR UPDATE
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			statement.setString(2, userType.name().toLowerCase(java.util.Locale.ROOT));
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	public boolean teacherCanManageStudent(Connection connection, long teacherUserId, long studentUserId)
			throws SQLException {
		String sql = """
				SELECT 1
				FROM teacher_feature_permissions tfp
				JOIN teacher_school_permissions tsp ON tsp.teacher_user_id = tfp.teacher_user_id
				JOIN student_class_memberships scm ON scm.student_user_id = ?
				JOIN classrooms c ON c.classroom_id = scm.classroom_id AND c.school_id = tsp.school_id
				WHERE tfp.teacher_user_id = ?
				  AND tfp.feature_code = 'account-management'
				  AND tfp.is_enabled = 1
				  AND tsp.access_status = 'enabled'
				  AND scm.membership_status = 'active'
				LIMIT 1
				FOR UPDATE
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, teacherUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	public void updateLoginLockout(Connection connection, long userId, int consecutiveFailures,
			LocalDateTime lockedUntil) throws SQLException {
		String sql = """
				UPDATE users
				SET consecutive_login_failures = ?, login_locked_until = ?
				WHERE user_id = ?
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, consecutiveFailures);
			if (lockedUntil == null) {
				statement.setNull(2, java.sql.Types.TIMESTAMP);
			} else {
				statement.setTimestamp(2, Timestamp.valueOf(lockedUntil));
			}
			statement.setLong(3, userId);
			statement.executeUpdate();
		}
	}

	public void insertLoginHistory(Connection connection, Long userId, String attemptedLoginId,
			String operationType, String resultStatus, String errorCode, String errorMessage, String ipAddress,
			String userAgent, String sessionId, LocalDateTime occurredAt) throws SQLException {
		String sql = """
				INSERT INTO login_history (
					user_id, attempted_login_id, operation_type, result_status, error_code, error_message,
					ip_address, user_agent, session_id, occurred_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			setNullableLong(statement, 1, userId);
			setNullableString(statement, 2, limit(attemptedLoginId, 64));
			statement.setString(3, operationType);
			statement.setString(4, resultStatus);
			setNullableString(statement, 5, errorCode);
			setNullableString(statement, 6, errorMessage);
			setNullableString(statement, 7, limit(ipAddress, 45));
			setNullableString(statement, 8, limit(userAgent, 512));
			setNullableString(statement, 9, sessionId);
			statement.setTimestamp(10, Timestamp.valueOf(occurredAt));
			statement.executeUpdate();
		}
	}

	public void insertUnlockAudit(Connection connection, long actorUserId, UserType actorRole, long targetUserId,
			String requestId, LocalDateTime occurredAt) throws SQLException {
		String sql = """
				INSERT INTO audit_logs (
					actor_user_id, actor_role, feature_code, target_type, target_id, action_type,
					result_status, detail, request_id, occurred_at
				) VALUES (?, ?, 'account-management', 'user', ?, 'login-unlock', 'success', ?, ?, ?)
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, actorUserId);
			statement.setString(2, actorRole.name().toLowerCase(java.util.Locale.ROOT));
			statement.setLong(3, targetUserId);
			statement.setString(4, "Login lockout was cleared.");
			statement.setString(5, requestId);
			statement.setTimestamp(6, Timestamp.valueOf(occurredAt));
			statement.executeUpdate();
		}
	}

	public boolean hasAdmin(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT 1 FROM users WHERE user_type = 'admin' LIMIT 1 FOR UPDATE");
				ResultSet resultSet = statement.executeQuery()) {
			return resultSet.next();
		}
	}

	public long createAdmin(Connection connection, String loginId, String passwordHash, String displayName,
			LocalDateTime createdAt) throws SQLException {
		String sql = """
				INSERT INTO users (
					user_type, login_id, password_hash, display_name, account_status, created_at
				) VALUES ('admin', ?, ?, ?, 'active', ?)
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, loginId);
			statement.setString(2, passwordHash);
			statement.setString(3, displayName);
			statement.setTimestamp(4, Timestamp.valueOf(createdAt));
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Admin account insert did not return a user ID.");
				}
				return keys.getLong(1);
			}
		}
	}

	public void updatePassword(Connection connection, long userId, String passwordHash) throws SQLException {
		String sql = """
				UPDATE users
				SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
				WHERE user_id = ?
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, passwordHash);
			statement.setLong(2, userId);
			statement.executeUpdate();
		}
	}

	public void completeStudentPasswordChange(Connection connection, long userId) throws SQLException {
		String sql = """
				UPDATE student_profiles
				SET first_login_status = 'completed', must_change_password = 0
				WHERE user_id = ?
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Student profile was not updated after password change.");
			}
		}
	}

	private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
		if (value == null) {
			statement.setNull(index, java.sql.Types.BIGINT);
		} else {
			statement.setLong(index, value);
		}
	}

	private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
		if (value == null) {
			statement.setNull(index, java.sql.Types.VARCHAR);
		} else {
			statement.setString(index, value);
		}
	}

	private static String limit(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}
}
