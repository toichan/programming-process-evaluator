package control.teacher;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.admin.TeacherAccountControl;
import control.auth.AuthenticatedUser;
import control.auth.AuthenticationControl;
import control.auth.AuthenticationControl.LoginPortal;
import control.auth.PasswordHasher;
import control.auth.RequestMetadata;
import control.auth.TeacherSessionControl;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class TeacherSelfAccountDatabaseTest {
	private final TeacherAccountControl management = new TeacherAccountControl();
	private final TeacherSelfAccountControl self = new TeacherSelfAccountControl();
	private final AuthenticationControl auth = new AuthenticationControl();
	private final RequestMetadata metadata = RequestMetadata.from("127.0.0.1", "synthetic-self-test");
	private AuthenticatedUser admin;
	private AuthenticatedUser teacher;
	private String initial;

	@BeforeEach void setUp() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TEACHER_SELF_DB_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_self_test_[a-z0-9_]+"));
		try (Connection connection = Client.createConnection(); var statement = connection.createStatement()) {
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			statement.executeUpdate("""
					INSERT INTO users (user_type,login_id,password_hash,display_name,account_status,created_at)
					VALUES ('admin','admin','unused','Synthetic','active',CURRENT_TIMESTAMP)
					""");
			admin = new AuthenticatedUser(scalar(connection, "SELECT user_id FROM users"), "admin", "Synthetic", UserType.ADMIN, false, "test");
		}
		initial = management.create(admin, new TeacherAccountInput("synthetic-self", Set.of(), Set.of()));
		var account = management.list(admin).getFirst();
		teacher = new AuthenticatedUser(account.userId(), account.loginId(), account.loginId(), UserType.TEACHER, true, "test");
	}

	@Test void roundTripsInitialOptionalAndResetChangesWithoutBusinessPermissions() throws SQLException {
		assertTrue(self.load(teacher, 1).mustChangePassword());
		assertTrue(self.load(teacher, 1).features().isEmpty());
		assertTrue(self.load(teacher, 1).schools().isEmpty());
		var firstLogin = auth.authenticate(teacher.loginId(), initial.toCharArray(), LoginPortal.STAFF, metadata);
		assertTrue(firstLogin.authenticated());
		assertTrue(firstLogin.user().teacherMustChangePassword());
		assertEquals(TeacherSelfAccountControl.ChangeResult.SUCCESS,
				self.changePassword(teacher, 1, initial.toCharArray(), chars("Changed2026!Aa"), chars("Changed2026!Aa"), metadata));
		verify(2, false, "Changed2026!Aa");
		assertFalse(new TeacherSessionControl().isCurrent(teacher, 1L));
		assertFalse(auth.authenticate(teacher.loginId(), initial.toCharArray(), LoginPortal.STAFF, metadata).authenticated());
		assertFalse(auth.authenticate(teacher.loginId(), chars("Changed2026!Aa"), LoginPortal.STAFF, metadata).user().teacherMustChangePassword());
		assertEquals(TeacherSelfAccountControl.ChangeResult.SUCCESS,
				self.changePassword(teacher, 2, chars("Changed2026!Aa"), chars("Optional2026!Aa"), chars("Optional2026!Aa"), metadata));
		verify(3, false, "Optional2026!Aa");
		String reset = management.change(admin, teacher.userId(), 3, "reset", null);
		verify(4, true, reset);
		assertThrows(TeacherSelfAccountControl.StaleAccountException.class,
				() -> self.changePassword(teacher, 3, chars("Optional2026!Aa"), chars("Rejected2026!Aa"), chars("Rejected2026!Aa"), metadata));
		assertEquals(TeacherSelfAccountControl.ChangeResult.SUCCESS,
				self.changePassword(teacher, 4, reset.toCharArray(), chars("Final2026!Aa"), chars("Final2026!Aa"), metadata));
		verify(5, false, "Final2026!Aa");
		try (Connection connection = Client.createConnection()) {
			assertEquals(1, scalar(connection, "SELECT must_change_at_next_login FROM password_reset_records"));
			assertEquals(5, scalar(connection, "SELECT COUNT(*) FROM credential_history"));
			assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE actor_role='teacher' AND action_type='password_change' AND result_status='success'"));
			try (var statement = connection.createStatement(); var rows = statement.executeQuery(
					"SELECT detail, before_data, after_data FROM audit_logs")) {
				while (rows.next()) for (int column = 1; column <= 3; column++) {
					String value = rows.getString(column);
					if (value != null) assertFalse(value.contains(initial) || value.contains(reset)
							|| value.contains("Changed2026!Aa") || value.contains("pbkdf2"));
				}
			}
		}
	}

	@Test void rejectedInputsAndWriteFailureLeaveCredentialsVersionAndRequirementUnchanged() throws SQLException {
		assertEquals(TeacherSelfAccountControl.ChangeResult.CURRENT_PASSWORD_INVALID,
				self.changePassword(teacher, 1, chars("Wrong2026!Aa"), chars("Changed2026!Aa"), chars("Changed2026!Aa"), metadata));
		assertEquals(TeacherSelfAccountControl.ChangeResult.PASSWORD_POLICY,
				self.changePassword(teacher, 1, initial.toCharArray(), chars("short"), chars("short"), metadata));
		assertEquals(TeacherSelfAccountControl.ChangeResult.CONFIRMATION_MISMATCH,
				self.changePassword(teacher, 1, initial.toCharArray(), chars("Changed2026!Aa"), chars("Different2026!Aa"), metadata));
		assertEquals(TeacherSelfAccountControl.ChangeResult.PASSWORD_REUSED,
				self.changePassword(teacher, 1, initial.toCharArray(), initial.toCharArray(), initial.toCharArray(), metadata));
		verify(1, true, initial);
		var failing = new TeacherSelfAccountControl(() -> {
			Connection actual = Client.createConnection();
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
					(proxy, method, arguments) -> {
						if ("prepareStatement".equals(method.getName())
								&& arguments[0].toString().contains("INSERT INTO credential_history"))
							throw new SQLException("Synthetic credential history failure.");
						try { return method.invoke(actual, arguments); }
						catch (InvocationTargetException failure) { throw failure.getCause(); }
					});
		});
		assertThrows(SQLException.class, () -> failing.changePassword(teacher, 1, initial.toCharArray(),
				chars("Changed2026!Aa"), chars("Changed2026!Aa"), metadata));
		verify(1, true, initial);
		try (Connection connection = Client.createConnection()) {
			assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM credential_history"));
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE actor_role='teacher' AND result_status='success'"));
			assertEquals(4, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE actor_role='teacher' AND result_status='failure'"));
		}
	}

	@Test void rejectsOldVersionsInactiveTeachersAndForgedRoles() throws SQLException {
		management.change(admin, teacher.userId(), 1, "permissions",
				new TeacherAccountInput(teacher.loginId(), Set.of(), Set.of("code-distribution")));
		assertThrows(TeacherSelfAccountControl.StaleAccountException.class, () -> self.load(teacher, 1));
		assertTrue(self.load(teacher, 2).features().contains("code-distribution"));
		management.change(admin, teacher.userId(), 2, "suspend", null);
		assertThrows(SecurityException.class, () -> self.load(teacher, 3));
		var forged = new AuthenticatedUser(admin.userId(), admin.loginId(), "Synthetic", UserType.TEACHER, false, "test");
		assertThrows(SecurityException.class, () -> self.load(forged, 1));
		assertThrows(SecurityException.class, () -> self.load(admin, 1));
	}

	@Test void simultaneousChangesAcceptExactlyOneExpectedVersion() throws Exception {
		Callable<Boolean> change = () -> {
			try {
				return self.changePassword(teacher, 1, initial.toCharArray(), chars("Changed2026!Aa"),
						chars("Changed2026!Aa"), metadata) == TeacherSelfAccountControl.ChangeResult.SUCCESS;
			} catch (TeacherSelfAccountControl.StaleAccountException expected) {
				return false;
			}
		};
		try (var executor = Executors.newFixedThreadPool(2)) {
			var results = executor.invokeAll(List.of(change, change));
			int successes = 0;
			for (var result : results) if (result.get()) successes++;
			assertEquals(1, successes);
		}
		verify(2, false, "Changed2026!Aa");
		try (Connection connection = Client.createConnection()) {
			assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE actor_role='teacher' AND result_status='success'"));
		}
	}

	private void verify(long version, boolean required, String password) throws SQLException {
		var account = self.load(teacher, version);
		assertEquals(required, account.mustChangePassword());
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement(
				"SELECT password_hash FROM users WHERE user_id=?")) {
			statement.setLong(1, teacher.userId());
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				assertTrue(new PasswordHasher().matches(password.toCharArray(), rows.getString(1)));
			}
		}
	}

	private static long scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next()); return rows.getLong(1);
		}
	}
	private static char[] chars(String value) { return value.toCharArray(); }

	@AfterEach void cleanUp() throws SQLException {
		if (admin == null) return;
		try (Connection connection = Client.createConnection(); var statement = connection.createStatement()) {
			for (String table : List.of("audit_logs", "credential_history", "password_reset_records", "login_history",
					"teacher_feature_permissions", "teacher_school_permissions")) statement.executeUpdate("DELETE FROM " + table);
			statement.executeUpdate("UPDATE users SET created_by_user_id=NULL, updated_by_user_id=NULL");
			statement.executeUpdate("DELETE FROM users");
		}
	}
}
