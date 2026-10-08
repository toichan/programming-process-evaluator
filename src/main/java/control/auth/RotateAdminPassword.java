package control.auth;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;

import dao.AuthenticationDao;
import entity.UserCredential;
import entity.UserCredential.AccountStatus;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class RotateAdminPassword {
	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	private RotateAdminPassword() {
	}

	public static void main(String[] args) throws SQLException {
		char[] password = generatePassword();
		try {
			AuthenticationDao dao = new AuthenticationDao();
			LocalDateTime now = LocalDateTime.now();
			try (Connection connection = Client.createConnection()) {
				connection.setAutoCommit(false);
				try {
					UserCredential admin = dao.findByLoginIdForUpdate(connection, UserCredential.ADMIN_LOGIN_ID)
							.filter(user -> user.userType() == UserType.ADMIN
									&& user.accountStatus() == AccountStatus.ACTIVE)
							.orElseThrow(() -> new IllegalStateException(
									"An active administrator account was not found."));
					dao.updatePassword(connection, admin.userId(), new PasswordHasher().hash(password));
					dao.updateLoginLockout(connection, admin.userId(), 0, null);
					dao.insertPasswordChangeHistory(connection, admin.userId(), false, now);
					dao.insertLoginHistory(connection, admin.userId(), UserCredential.ADMIN_LOGIN_ID,
							"password_change", "success", null, null, null, null, null, now);
					connection.commit();
				} catch (SQLException | RuntimeException e) {
					connection.rollback();
					throw e;
				}
			}

			System.out.println("Administrator password updated. Save this password; it is shown only once:");
			System.out.println(password);
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private static char[] generatePassword() {
		byte[] randomBytes = new byte[24];
		try {
			char[] password;
			do {
				RANDOM.nextBytes(randomBytes);
				password = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes).toCharArray();
			} while (!PasswordPolicy.isValid(password));
			return password;
		} finally {
			Arrays.fill(randomBytes, (byte) 0);
		}
	}
}
