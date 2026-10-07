package control.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import control.auth.AuthenticatedUser;
import control.auth.AuthenticationControl;
import control.auth.AuthenticationControl.LoginPortal;
import control.auth.PasswordHasher;
import control.auth.PasswordPolicy;
import control.auth.RequestMetadata;
import control.auth.TeacherSessionControl;
import dao.TeacherAccountDao;
import dao.TeacherPermissionDao;
import entity.TeacherAccountDetails;
import entity.TeacherAccountInput;
import entity.SchoolInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class TeacherAccountDatabaseTest {
	private final TeacherAccountControl control = new TeacherAccountControl();
	private AuthenticatedUser admin;
	private final List<Long> schools = new ArrayList<>();

	@BeforeEach void setUp() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TEACHER_ACCOUNT_DB_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_test_[a-z0-9_]+"));
		try (Connection connection = Client.createConnection()) {
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			try (var statement = connection.prepareStatement("""
					INSERT INTO users (user_type,login_id,password_hash,display_name,account_status,created_at)
					VALUES ('admin','synthetic-admin','unused','Synthetic','active',CURRENT_TIMESTAMP)
					""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
				statement.executeUpdate();
				try (var keys = statement.getGeneratedKeys()) {
					assertTrue(keys.next());
					admin = new AuthenticatedUser(keys.getLong(1), "synthetic-admin", "Synthetic", UserType.ADMIN, false, "test");
				}
			}
		}
	}

	@Test void roundTripsCredentialsPermissionsVersionsStatusAndRetainedHistory() throws SQLException {
		long school = new SchoolControl().save(admin, 0, 0, new SchoolInput("Synthetic teacher school", 1));
		schools.add(school);
		var input = new TeacherAccountInput("synthetic-teacher", Set.of(school), Set.of("teacher-prompt-design"));
		String password = control.create(admin, input);
		assertTrue(PasswordPolicy.isValid(password.toCharArray()));
		TeacherAccountDetails account = control.list(admin).getFirst();
		assertEquals(input.loginId(), account.loginId());
		assertEquals(1, account.version());
		assertEquals(admin.loginId(), account.createdBy());
		assertEquals(Set.of("teacher-prompt-design"), account.features());
		assertEquals(school, account.schools().getFirst().schoolId());
		verifyPassword(account.userId(), password);
		var auth = new AuthenticationControl();
		var authenticated = auth.authenticate(account.loginId(), password.toCharArray(), LoginPortal.STAFF,
				RequestMetadata.from("127.0.0.1", "synthetic"));
		assertTrue(authenticated.authenticated());
		var sessionControl = new TeacherSessionControl();
		var sessionUser = new AuthenticatedUser(account.userId(), account.loginId(), account.loginId(),
				UserType.TEACHER, false, authenticated.sessionAuditId());
		assertEquals(1, sessionControl.loginVersion(authenticated.user()).orElseThrow());
		assertTrue(sessionControl.isCurrent(sessionUser, 1L));
		assertFalse(sessionControl.isCurrent(sessionUser, "1"));
		assertEquals(1, control.history(admin, account.userId(), true).size());
		assertThrows(IllegalArgumentException.class, () -> control.create(admin, input));
		assertEquals(1, control.list(admin).size());
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			var permissions = new TeacherPermissionDao();
			assertDoesNotThrow(() -> permissions.requirePromptDesignAccess(connection, account.userId()));
			assertThrows(SecurityException.class, () -> permissions.requireTaskManagementAccess(connection, account.userId()));
			var navigation = permissions.findNavigationSummary(connection, account.userId());
			assertTrue(navigation.promptDesignEnabled()); assertFalse(navigation.taskManagementEnabled());
			connection.rollback();
		}
		try (Connection connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE users SET consecutive_login_failures=5,
						  login_locked_until=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 MINUTE) WHERE user_id=?
						""")) {
			statement.setLong(1, account.userId()); statement.executeUpdate();
		}
		control.change(admin, account.userId(), 1, "permissions",
				new TeacherAccountInput(account.loginId(), Set.of(school), Set.copyOf(TeacherAccountInput.FEATURE_ORDER)));
		assertEquals(2, control.list(admin).getFirst().version());
		assertFalse(sessionControl.isCurrent(sessionUser, 1L));
		assertTrue(sessionControl.isCurrent(sessionUser, 2L));
		assertThrows(IllegalArgumentException.class, () -> control.change(admin, account.userId(), 1, "delete", null));
		try (Connection connection = Client.createConnection()) {
			assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM teacher_school_permissions WHERE access_status='enabled'"));
			assertEquals(2, new TeacherAccountDao().sessionVersion(connection, account.userId(), null).orElseThrow());
			assertEquals(5, scalar(connection, "SELECT consecutive_login_failures FROM users WHERE user_type='teacher'"));
		}
		String replacement = control.change(admin, account.userId(), 2, "reset", null);
		assertTrue(sessionControl.loginVersion(authenticated.user()).isEmpty());
		assertFalse(sessionControl.isCurrent(sessionUser, 2L));
		try (Connection connection = Client.createConnection()) {
			assertEquals(0, scalar(connection, "SELECT consecutive_login_failures FROM users WHERE user_type='teacher'"));
			assertEquals(1, scalar(connection, "SELECT login_locked_until IS NULL FROM users WHERE user_type='teacher'"));
		}
		assertNotEquals(password, replacement); verifyPassword(account.userId(), replacement);
		assertFalse(auth.authenticate(account.loginId(), password.toCharArray(), LoginPortal.STAFF,
				RequestMetadata.from("127.0.0.1", "synthetic")).authenticated());
		assertTrue(auth.authenticate(account.loginId(), replacement.toCharArray(), LoginPortal.STAFF,
				RequestMetadata.from("127.0.0.1", "synthetic")).authenticated());
		control.change(admin, account.userId(), 3, "suspend", null);
		assertFalse(sessionControl.isCurrent(sessionUser, 3L));
		try (Connection connection = Client.createConnection()) {
			assertTrue(new TeacherAccountDao().sessionVersion(connection, account.userId(), null).isEmpty());
			assertEquals(0, scalar(connection, "SELECT must_change_at_next_login FROM password_reset_records"));
		}
		assertFalse(auth.authenticate(account.loginId(), replacement.toCharArray(), LoginPortal.STAFF,
				RequestMetadata.from("127.0.0.1", "synthetic")).authenticated());
		control.change(admin, account.userId(), 4, "activate", null);
		assertTrue(auth.authenticate(account.loginId(), replacement.toCharArray(), LoginPortal.STAFF,
				RequestMetadata.from("127.0.0.1", "synthetic")).authenticated());
		control.change(admin, account.userId(), 5, "delete", null);
		assertEquals("deleted", control.list(admin).getFirst().status());
		assertThrows(IllegalArgumentException.class, () -> control.change(admin, account.userId(), 6, "activate", null));
		var history = control.history(admin, account.userId(), false);
		assertTrue(history.stream().anyMatch(row -> row.action().equals("delete") && row.result().equals("success")));
		assertTrue(history.stream().anyMatch(row -> row.result().equals("failure")));
		assertTrue(history.stream().noneMatch(row -> row.detail().contains(password) || row.detail().contains(replacement)
				|| row.detail().contains("pbkdf2")));
		try (Connection connection = Client.createConnection()) {
			assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM credential_history"));
			assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM users WHERE user_type='teacher' AND deleted_at IS NOT NULL"));
		}
	}

	@Test void rollsBackInvalidSchoolAndBlocksInactiveAdmin() throws SQLException {
		assertThrows(IllegalArgumentException.class, () -> control.create(admin,
				new TeacherAccountInput("invalid-school", Set.of(Long.MAX_VALUE), Set.of("task-management"))));
		assertTrue(control.list(admin).isEmpty());
		try (Connection connection = Client.createConnection()) {
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM credential_history"));
			assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE result_status='failure'"));
			try (var statement = connection.prepareStatement("UPDATE users SET account_status='suspended' WHERE user_id=?")) {
				statement.setLong(1, admin.userId()); statement.executeUpdate();
			}
		}
		assertThrows(SecurityException.class, () -> control.list(admin));
	}

	private void verifyPassword(long id, String password) throws SQLException {
		try (Connection connection = Client.createConnection();
				var statement = connection.prepareStatement("SELECT password_hash FROM users WHERE user_id=?")) {
			statement.setLong(1, id);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next()); assertNotEquals(password, rows.getString(1));
				assertTrue(new PasswordHasher().matches(password.toCharArray(), rows.getString(1)));
			}
		}
	}

	@AfterEach void cleanUp() throws SQLException {
		if (admin == null) return;
		try (Connection connection = Client.createConnection(); var statement = connection.createStatement()) {
			for (String table : List.of("login_history", "audit_logs", "credential_history", "password_reset_records",
					"teacher_feature_permissions", "teacher_school_permissions")) statement.executeUpdate("DELETE FROM " + table);
			statement.executeUpdate("DELETE FROM users WHERE user_type='teacher'");
			statement.executeUpdate("DELETE FROM users WHERE user_type='admin'");
			for (long id : schools) statement.executeUpdate("DELETE FROM schools WHERE school_id=" + id);
		}
	}
	private static long scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next()); return rows.getLong(1);
		}
	}
}
