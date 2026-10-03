package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import entity.StudentAccountProfile;
import entity.UserCredential;
import entity.UserCredential.AccountStatus;
import entity.UserCredential.FirstLoginStatus;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class UserDao {
	private static final String FIND_BY_LOGIN_ID = """
			SELECT u.user_id, u.user_type, u.login_id, u.password_hash, u.display_name, u.account_status,
			       u.consecutive_login_failures, u.login_locked_until,
			       sp.user_id AS student_profile_user_id,
			       CASE WHEN sp.school_id IS NULL THEN sp.security_level ELSE school.security_level END AS security_level,
			       sp.first_login_status, sp.must_change_password
			FROM users u
			LEFT JOIN student_profiles sp ON sp.user_id = u.user_id
			LEFT JOIN schools school ON school.school_id = sp.school_id
			WHERE u.login_id = 
			""";

	private static final String FIND_BY_USER_ID = """
			SELECT u.user_id, u.user_type, u.login_id, u.password_hash, u.display_name, u.account_status,
			       u.consecutive_login_failures, u.login_locked_until,
			       sp.user_id AS student_profile_user_id,
			       CASE WHEN sp.school_id IS NULL THEN sp.security_level ELSE school.security_level END AS security_level,
			       sp.first_login_status, sp.must_change_password
			FROM users u
			LEFT JOIN student_profiles sp ON sp.user_id = u.user_id
			LEFT JOIN schools school ON school.school_id = sp.school_id
			WHERE u.user_id = ?
			""";

	public Optional<UserCredential> findByLoginId(String loginId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			return findByLoginId(connection, loginId);
		}
	}

	public Optional<UserCredential> findByLoginId(Connection connection, String loginId) throws SQLException {
		return executeQuery(connection, FIND_BY_LOGIN_ID + "?", loginId);
	}

	public Optional<UserCredential> findByLoginIdForUpdate(Connection connection, String loginId) throws SQLException {
		return executeQuery(connection, FIND_BY_LOGIN_ID + "? FOR UPDATE", loginId);
	}

	public Optional<UserCredential> findByUserIdForUpdate(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_BY_USER_ID + " FOR UPDATE")) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(mapUser(resultSet));
			}
		}
	}

	private static Optional<UserCredential> executeQuery(Connection connection, String sql, String value)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, value);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(mapUser(resultSet));
			}
		}
	}

	private static UserCredential mapUser(ResultSet resultSet) throws SQLException {
		UserType userType = enumValue(UserType.class, resultSet.getString("user_type"));
		AccountStatus accountStatus = enumValue(AccountStatus.class, resultSet.getString("account_status"));
		int consecutiveLoginFailures = resultSet.getInt("consecutive_login_failures");
		Timestamp loginLockedUntilTimestamp = resultSet.getTimestamp("login_locked_until");
		Optional<LocalDateTime> loginLockedUntil = loginLockedUntilTimestamp == null
				? Optional.empty()
				: Optional.of(loginLockedUntilTimestamp.toLocalDateTime());
		boolean hasStudentProfile = resultSet.getObject("student_profile_user_id") != null;

		Optional<StudentAccountProfile> studentProfile;
		if (userType == UserType.STUDENT) {
			if (!hasStudentProfile) {
				throw new SQLException("Student account is missing its student profile.");
			}
			int level = resultSet.getInt("security_level");
			if (resultSet.wasNull() || (level != 1 && level != 2)) {
				throw new SQLException("Student account has no valid school security policy.");
			}
			studentProfile = Optional.of(new StudentAccountProfile(
					level,
					enumValue(FirstLoginStatus.class, resultSet.getString("first_login_status")),
					resultSet.getBoolean("must_change_password")));
		} else {
			if (hasStudentProfile) {
				throw new SQLException("Non-student account has a student profile.");
			}
			studentProfile = Optional.empty();
		}

		return new UserCredential(
				resultSet.getLong("user_id"),
				userType,
				resultSet.getString("login_id"),
				resultSet.getString("password_hash"),
				resultSet.getString("display_name"),
				accountStatus,
				consecutiveLoginFailures,
				loginLockedUntil,
				studentProfile);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) throws SQLException {
		try {
			return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new SQLException("Invalid stored value for " + enumType.getSimpleName() + ".", e);
		}
	}
}
