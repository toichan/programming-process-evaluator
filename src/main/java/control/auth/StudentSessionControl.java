package control.auth;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import entity.UserCredential;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class StudentSessionControl {
	private static final String SELECT = """
			SELECT u.account_version,u.password_hash,
			  (COALESCE(s.security_level,p.security_level)=2 AND
			    (p.must_change_password OR p.first_login_status<>'completed')) AS change_required
			FROM users u JOIN student_profiles p ON p.user_id=u.user_id
			LEFT JOIN schools s ON s.school_id=p.school_id
			WHERE u.user_id=? AND u.user_type='student' AND u.account_status='active' AND u.deleted_at IS NULL
			""";
	public Optional<Long> loginVersion(UserCredential credential) throws SQLException {
		if (credential == null || credential.userType() != UserType.STUDENT) throw new SecurityException("Student authentication required.");
		try (var connection = Client.createConnection(); var statement = connection.prepareStatement(SELECT + " AND u.password_hash=?")) {
			statement.setLong(1, credential.userId()); statement.setString(2, credential.passwordHash());
			try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty(); }
		}
	}
	public boolean isCurrent(AuthenticatedUser user, Object expectedVersion) throws SQLException {
		requireStudent(user);
		if (!(expectedVersion instanceof Long version)) return false;
		try (var connection = Client.createConnection(); var statement = connection.prepareStatement(SELECT)) {
			statement.setLong(1, user.userId());
			try (var rows = statement.executeQuery()) {
				return rows.next() && rows.getLong(1) == version && rows.getBoolean(3) == user.passwordChangeRequired();
			}
		}
	}
	public Optional<Long> afterPasswordChange(AuthenticatedUser user, char[] newPassword) throws SQLException {
		requireStudent(user);
		try (var connection = Client.createConnection(); var statement = connection.prepareStatement(SELECT)) {
			statement.setLong(1, user.userId());
			try (var rows = statement.executeQuery()) {
				if (!rows.next() || rows.getBoolean(3) || !new PasswordHasher().matches(newPassword, rows.getString(2))) return Optional.empty();
				return Optional.of(rows.getLong(1));
			}
		}
	}
	public static boolean matchesVersion(Connection connection, long id, Long expected) throws SQLException {
		if (expected == null) return true;
		try (var statement = connection.prepareStatement("SELECT account_version FROM users WHERE user_id=?")) {
			statement.setLong(1, id);
			try (var rows = statement.executeQuery()) { return rows.next() && rows.getLong(1) == expected; }
		}
	}
	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) throw new SecurityException("Student authentication required.");
	}
}
