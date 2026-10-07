package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import control.auth.PasswordPolicy;
import control.auth.RequestMetadata;
import dao.AuthenticationDao;
import dao.TeacherAccountDao;
import entity.TeacherAccountDetails;
import entity.UserCredential;
import entity.UserCredential.AccountStatus;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherSelfAccountControl {
	private static final Logger LOGGER = Logger.getLogger(TeacherSelfAccountControl.class.getName());
	private final TeacherAccountDao teachers = new TeacherAccountDao();
	private final AuthenticationDao authentication = new AuthenticationDao();
	private final PasswordHasher hasher = new PasswordHasher();
	private final ConnectionFactory connections;

	public TeacherSelfAccountControl() {
		this(Client::createConnection);
	}

	TeacherSelfAccountControl(ConnectionFactory connections) {
		this.connections = Objects.requireNonNull(connections);
	}

	public TeacherAccountDetails load(AuthenticatedUser user, long expectedVersion) throws SQLException {
		return transaction(user, expectedVersion, (connection, credential) -> teachers.lock(connection, user.userId()));
	}

	public ChangeResult changePassword(AuthenticatedUser user, long expectedVersion, char[] current,
			char[] next, char[] confirmation, RequestMetadata metadata) throws SQLException {
		Objects.requireNonNull(metadata);
		return transaction(user, expectedVersion, (connection, credential) -> {
			ChangeResult result;
			if (current == null || current.length > 256 || !hasher.matches(current, credential.passwordHash())) {
				result = ChangeResult.CURRENT_PASSWORD_INVALID;
			} else if (!PasswordPolicy.isValid(next)) {
				result = ChangeResult.PASSWORD_POLICY;
			} else if (confirmation == null || !Arrays.equals(next, confirmation)) {
				result = ChangeResult.CONFIRMATION_MISMATCH;
			} else if (hasher.matches(next, credential.passwordHash())) {
				result = ChangeResult.PASSWORD_REUSED;
			} else {
				teachers.changeOwnPassword(connection, user.userId(), hasher.hash(next));
				authentication.insertPasswordChangeHistory(connection, user.userId(),
						credential.teacherMustChangePassword(), LocalDateTime.now(ZoneOffset.UTC));
				result = ChangeResult.SUCCESS;
			}
			boolean success = result == ChangeResult.SUCCESS;
			authentication.insertLoginHistory(connection, user.userId(), credential.loginId(), "password_change",
					success ? "success" : "failure", success ? null : result.name(),
					success ? null : "Teacher password change was rejected.", metadata.ipAddress(),
					metadata.userAgent(), metadata.sessionId(), LocalDateTime.now(ZoneOffset.UTC));
			teachers.ownPasswordAudit(connection, user.userId(), success ? "success" : "failure",
					success ? "Teacher changed their own password." : result.name(), UUID.randomUUID().toString());
			return result;
		});
	}

	private <T> T transaction(AuthenticatedUser user, long version, Operation<T> operation) throws SQLException {
		if (user == null || user.userType() != UserType.TEACHER || user.userId() < 1)
			throw new SecurityException("Active teacher authentication is required.");
		if (version < 1) throw new IllegalArgumentException("更新情報が不正です。再ログインしてください。");
		try (Connection connection = connections.open()) {
			connection.setAutoCommit(false);
			try {
				UserCredential credential = authentication.findByUserIdForUpdate(connection, user.userId())
						.orElseThrow(() -> new SecurityException("Teacher account is unavailable."));
				if (credential.userType() != UserType.TEACHER || credential.accountStatus() != AccountStatus.ACTIVE)
					throw new SecurityException("Active teacher authentication is required.");
				long actualVersion = teachers.sessionVersion(connection, user.userId(), null)
						.orElseThrow(() -> new SecurityException("Teacher account is unavailable."));
				if (version != actualVersion) throw new StaleAccountException();
				T result = operation.run(connection, credential);
				connection.commit();
				return result;
			} catch (SQLException | RuntimeException failure) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
					LOGGER.log(Level.SEVERE, "Teacher self account rollback failed.", rollbackFailure);
				}
				throw failure;
			}
		}
	}

	public enum ChangeResult {
		SUCCESS, CURRENT_PASSWORD_INVALID, PASSWORD_POLICY, PASSWORD_REUSED, CONFIRMATION_MISMATCH
	}

	public static final class StaleAccountException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		public StaleAccountException() {
			super("アカウント情報が更新されています。再ログインして確認してください。");
		}
	}

	@FunctionalInterface interface ConnectionFactory {
		Connection open() throws SQLException;
	}
	@FunctionalInterface private interface Operation<T> {
		T run(Connection connection, UserCredential credential) throws SQLException;
	}
}
