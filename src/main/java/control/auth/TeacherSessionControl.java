package control.auth;

import java.sql.SQLException;
import java.util.Optional;
import dao.TeacherAccountDao;
import entity.UserCredential;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherSessionControl {
	public Optional<Long> loginVersion(UserCredential credential) throws SQLException {
		if (credential == null || credential.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication required.");
		}
		try (var connection = Client.createConnection()) {
			return new TeacherAccountDao().sessionVersion(connection, credential.userId(), credential.passwordHash());
		}
	}

	public boolean isCurrent(AuthenticatedUser user, Object expectedVersion) throws SQLException {
		if (user == null || user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication required.");
		}
		if (!(expectedVersion instanceof Long version)) return false;
		try (var connection = Client.createConnection()) {
			return new TeacherAccountDao().sessionVersion(connection, user.userId(), null)
					.filter(version::equals).isPresent();
		}
	}
}
