package control.auth;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import dao.AuthenticationDao;
import entity.StudentAccountProfile;
import entity.UserCredential;
import entity.UserCredential.AccountStatus;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class AuthenticationControl {
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final int LOCK_MINUTES = 30;

	private final AuthenticationDao authenticationDao;
	private final PasswordHasher passwordHasher;
	private final Clock clock;

	public AuthenticationControl() {
		this(new AuthenticationDao(), new PasswordHasher(), Clock.systemUTC());
	}

	AuthenticationControl(AuthenticationDao authenticationDao, PasswordHasher passwordHasher, Clock clock) {
		this.authenticationDao = authenticationDao;
		this.passwordHasher = passwordHasher;
		this.clock = clock;
	}

	public LoginResult authenticate(String loginId, char[] password, LoginPortal portal, RequestMetadata metadata)
			throws SQLException {
		LocalDateTime now = LocalDateTime.now(clock);
		char[] submittedPassword = password == null ? new char[0] : password;
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				Optional<UserCredential> found = loginId == null || loginId.isBlank()
						? Optional.empty()
						: authenticationDao.findByLoginIdForUpdate(connection, loginId);
				if (found.isEmpty()) {
					passwordHasher.matchesDummy(submittedPassword);
					recordFailure(connection, null, loginId, "UNKNOWN_LOGIN_ID", metadata, now);
					connection.commit();
					return LoginResult.failure("INVALID_CREDENTIALS");
				}

				UserCredential user = found.get();
				boolean passwordMatches = passwordHasher.matches(submittedPassword, user.passwordHash());
				if (user.accountStatus() != AccountStatus.ACTIVE) {
					String reason = user.accountStatus() == AccountStatus.SUSPENDED
							? "ACCOUNT_SUSPENDED"
							: "ACCOUNT_DELETED";
					recordFailure(connection, user.userId(), loginId, reason, metadata, now);
					connection.commit();
					return LoginResult.failure("ACCOUNT_UNAVAILABLE");
				}

				if (!portal.allows(user.userType())) {
					recordFailure(connection, user.userId(), loginId, "WRONG_LOGIN_PORTAL", metadata, now);
					connection.commit();
					return LoginResult.failure("INVALID_CREDENTIALS");
				}

				LocalDateTime lockedUntil = user.loginLockedUntil().orElse(null);
				int failures = user.consecutiveLoginFailures();
				if (lockedUntil != null && lockedUntil.isAfter(now)) {
					recordFailure(connection, user.userId(), loginId, "ACCOUNT_LOCKED", metadata, now);
					connection.commit();
					return LoginResult.failure("ACCOUNT_LOCKED");
				}
				if (lockedUntil != null) {
					failures = 0;
					authenticationDao.updateLoginLockout(connection, user.userId(), 0, null);
				}

				if (!passwordMatches) {
					int nextFailures = Math.min(MAX_FAILED_ATTEMPTS, failures + 1);
					LocalDateTime nextLockedUntil = nextFailures == MAX_FAILED_ATTEMPTS
							? now.plusMinutes(LOCK_MINUTES)
							: null;
					authenticationDao.updateLoginLockout(connection, user.userId(), nextFailures, nextLockedUntil);
					recordFailure(connection, user.userId(), loginId,
							nextLockedUntil == null ? "INVALID_PASSWORD" : "TOO_MANY_FAILED_ATTEMPTS", metadata, now);
					connection.commit();
					return LoginResult.failure("INVALID_CREDENTIALS");
				}

				authenticationDao.updateLoginLockout(connection, user.userId(), 0, null);
				String sessionAuditId = UUID.randomUUID().toString();
				authenticationDao.insertLoginHistory(connection, user.userId(), loginId, "login", "success", null,
						null, metadata.ipAddress(), metadata.userAgent(), sessionAuditId, now);
				connection.commit();
				return LoginResult.success(user, sessionAuditId);
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	public void recordLogout(long userId, String loginId, RequestMetadata metadata) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			authenticationDao.insertLoginHistory(connection, userId, loginId, "logout", "success", null, null,
					metadata.ipAddress(), metadata.userAgent(), metadata.sessionId(), LocalDateTime.now(clock));
		}
	}

	public boolean unlockLogin(long actorUserId, UserType actorRole, long targetUserId, String requestId)
			throws SQLException {
		if (actorRole != UserType.TEACHER && actorRole != UserType.ADMIN) {
			throw new SecurityException("Only a teacher or admin can unlock a login.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				if (!authenticationDao.hasActiveUserRole(connection, actorUserId, actorRole)) {
					throw new SecurityException("The actor is not an active user with the stated role.");
				}
				Optional<UserCredential> found = authenticationDao.findByUserIdForUpdate(connection, targetUserId);
				if (found.isEmpty()) {
					connection.commit();
					return false;
				}
				UserCredential target = found.get();
				if (actorRole == UserType.TEACHER && target.userType() != UserType.STUDENT) {
					throw new SecurityException("Teachers can unlock student accounts only.");
				}
				if (actorRole == UserType.TEACHER
						&& !authenticationDao.teacherCanManageStudent(connection, actorUserId, targetUserId)) {
					throw new SecurityException("The teacher is not authorized to manage this student account.");
				}
				if (target.consecutiveLoginFailures() == 0 && target.loginLockedUntil().isEmpty()) {
					connection.commit();
					return false;
				}
				authenticationDao.updateLoginLockout(connection, targetUserId, 0, null);
				authenticationDao.insertUnlockAudit(connection, actorUserId, actorRole, targetUserId, requestId, now);
				connection.commit();
				return true;
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	public PasswordChangeResult changeStudentPassword(long userId, char[] currentPassword, char[] newPassword,
			RequestMetadata metadata) throws SQLException {
		LocalDateTime now = LocalDateTime.now(clock);
		char[] submittedCurrentPassword = currentPassword == null ? new char[0] : currentPassword;
		char[] submittedNewPassword = newPassword == null ? new char[0] : newPassword;
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				Optional<UserCredential> found = authenticationDao.findByUserIdForUpdate(connection, userId);
				if (found.isEmpty()) {
					connection.commit();
					return PasswordChangeResult.NOT_ALLOWED;
				}
				UserCredential user = found.get();
				Optional<StudentAccountProfile> profile = user.studentProfile();
				if (user.accountStatus() != AccountStatus.ACTIVE || user.userType() != UserType.STUDENT
						|| profile.isEmpty() || profile.get().securityLevel() != 2) {
					connection.commit();
					return PasswordChangeResult.NOT_ALLOWED;
				}

				if (!passwordHasher.matches(submittedCurrentPassword, user.passwordHash())) {
					authenticationDao.insertLoginHistory(connection, user.userId(), user.loginId(), "password_change",
							"failure", "CURRENT_PASSWORD_INVALID", "Password change was rejected.", metadata.ipAddress(),
							metadata.userAgent(), metadata.sessionId(), now);
					connection.commit();
					return PasswordChangeResult.CURRENT_PASSWORD_INVALID;
				}
				if (!PasswordPolicy.isValid(submittedNewPassword)) {
					authenticationDao.insertLoginHistory(connection, user.userId(), user.loginId(), "password_change",
							"failure", "PASSWORD_POLICY", "Password change was rejected.", metadata.ipAddress(),
							metadata.userAgent(), metadata.sessionId(), now);
					connection.commit();
					return PasswordChangeResult.PASSWORD_POLICY;
				}
				if (passwordHasher.matches(submittedNewPassword, user.passwordHash())) {
					authenticationDao.insertLoginHistory(connection, user.userId(), user.loginId(), "password_change",
							"failure", "PASSWORD_REUSED", "Password change was rejected.", metadata.ipAddress(),
							metadata.userAgent(), metadata.sessionId(), now);
					connection.commit();
					return PasswordChangeResult.PASSWORD_REUSED;
				}

				authenticationDao.updatePassword(connection, user.userId(), passwordHasher.hash(submittedNewPassword));
				authenticationDao.completeStudentPasswordChange(connection, user.userId());
				authenticationDao.updateLoginLockout(connection, user.userId(), 0, null);
				authenticationDao.insertLoginHistory(connection, user.userId(), user.loginId(), "password_change",
						"success", null, null, metadata.ipAddress(), metadata.userAgent(), metadata.sessionId(), now);
				connection.commit();
				return PasswordChangeResult.SUCCESS;
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	private void recordFailure(Connection connection, Long userId, String loginId, String reason,
			RequestMetadata metadata, LocalDateTime now) throws SQLException {
		authenticationDao.insertLoginHistory(connection, userId, loginId, "login", "failure", reason,
				"Login attempt was rejected.", metadata.ipAddress(), metadata.userAgent(), metadata.sessionId(), now);
	}

	public enum LoginPortal {
		STUDENT,
		STAFF;

		boolean allows(UserType userType) {
			return this == STUDENT
					? userType == UserType.STUDENT
					: userType == UserType.TEACHER || userType == UserType.ADMIN;
		}
	}

	public enum PasswordChangeResult {
		SUCCESS,
		CURRENT_PASSWORD_INVALID,
		PASSWORD_POLICY,
		PASSWORD_REUSED,
		NOT_ALLOWED
	}
}
