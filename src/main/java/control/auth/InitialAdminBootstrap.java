package control.auth;

import java.io.Console;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;

import dao.AuthenticationDao;
import entity.UserCredential;
import lib.mysql.Client;

public final class InitialAdminBootstrap {
	private InitialAdminBootstrap() {
	}

	public static void main(String[] args) throws SQLException {
		Console console = System.console();
		if (console == null) {
			throw new IllegalStateException(
					"Run this task interactively so the initial administrator password is not exposed.");
		}

		String loginId = UserCredential.ADMIN_LOGIN_ID;
		console.printf("Initial administrator login ID: %s%n", loginId);
		String displayName = console.readLine("Initial administrator display name: ");
		char[] password = console.readPassword("Initial administrator password: ");
		char[] confirmation = console.readPassword("Confirm password: ");
		try {
			if (displayName == null || displayName.isBlank() || displayName.length() > 100) {
				throw new IllegalArgumentException("The display name must be 1-100 characters.");
			}
			if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
				throw new IllegalArgumentException("The passwords do not match.");
			}
			if (!PasswordPolicy.isValid(password)) {
				throw new IllegalArgumentException(
						"The password must be 8-32 ASCII characters and use at least three character categories.");
			}

			AuthenticationDao dao = new AuthenticationDao();
			try (Connection connection = Client.createConnection()) {
				connection.setAutoCommit(false);
				try {
					if (dao.hasAdmin(connection)) {
						throw new IllegalStateException("An administrator account already exists.");
					}
					dao.createAdmin(connection, loginId, new PasswordHasher().hash(password), displayName,
							LocalDateTime.now());
					connection.commit();
				} catch (SQLException | RuntimeException e) {
					connection.rollback();
					throw e;
				}
			}
			console.printf("Initial administrator account created.%n");
		} finally {
			if (password != null) {
				Arrays.fill(password, '\0');
			}
			if (confirmation != null) {
				Arrays.fill(confirmation, '\0');
			}
		}
	}
}
