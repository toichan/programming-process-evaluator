package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import control.auth.AuthenticatedUser;
import control.auth.AuthenticationControl;
import control.auth.AuthenticationControl.LoginPortal;
import control.auth.PasswordHasher;
import control.auth.RequestMetadata;
import control.auth.StudentSessionControl;
import entity.StudentAccountCreation;
import entity.ManagedStudentAccount;
import entity.StudentAccountFilter;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import control.teacher.StudentAccountManagementControl.Target;

class StudentAccountManagementDatabaseTest {
	private final StudentAccountManagementControl control = new StudentAccountManagementControl();
	private final AuthenticationControl authentication = new AuthenticationControl();
	private final RequestMetadata metadata = RequestMetadata.from("127.0.0.1", "synthetic");
	private AuthenticatedUser teacher;
	private AuthenticatedUser other;
	private long school1, school2, outside, class1, class2, outsideClass;
	private boolean initialized;
	private static final char[] INITIAL = "Synthetic12!Aa".toCharArray();

	@BeforeEach void setup() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("STUDENT_ACCOUNT_DB_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_student_accounts_test_[a-z0-9_]+"));
		try (var connection = Client.createConnection()) {
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			try (var statement = connection.prepareStatement("UPDATE student_login_sequence SET next_number=1 WHERE singleton_id=1")) {
				statement.executeUpdate();
			}
			teacher = teacher(connection, "synthetic-one"); other = teacher(connection, "synthetic-two");
			initialized = true;
			school1 = insert(connection, "INSERT INTO schools (school_code,name,school_status,security_level,created_at) VALUES ('one','One','active',1,UTC_TIMESTAMP())");
			school2 = insert(connection, "INSERT INTO schools (school_code,name,school_status,security_level,created_at) VALUES ('two','Two','active',2,UTC_TIMESTAMP())");
			outside = insert(connection, "INSERT INTO schools (school_code,name,school_status,security_level,created_at) VALUES ('outside','Outside','active',2,UTC_TIMESTAMP())");
			class1 = classroom(connection, school1); class2 = classroom(connection, school2); outsideClass = classroom(connection, outside);
			for (long school : List.of(school1, school2)) grant(connection, teacher.userId(), school);
			for (long school : List.of(school1, school2, outside)) grant(connection, other.userId(), school);
		}
	}

	@Test void verifiesBothLevelsAuthorizationVersionedOperationsAndRetainedHistory() throws Exception {
		var initial1 = control.create(teacher, new StudentAccountCreation(school1, class1, null, 2), INITIAL);
		var initial2 = control.create(teacher, new StudentAccountCreation(school2, 0, "New class", 1), INITIAL);
		assertEquals(3, new HashSet<>(java.util.stream.Stream.concat(initial1.stream(), initial2.stream()).toList()).size());
		assertTrue(initial1.stream().allMatch(id -> id.matches("s[0-9]{3,}")));
		var level1 = account(initial1.getFirst()); var level2 = account(initial2.getFirst());
		assertEquals(1, level1.securityLevel()); assertEquals(2, level2.securityLevel());
		assertFalse(level1.mustChangePassword()); assertTrue(level2.mustChangePassword());
		assertEquals(new String(INITIAL), control.reveal(teacher, target(level1)));
		assertEquals(3, control.export(teacher, account -> true).size());
		assertEquals(1, control.export(teacher, new StudentAccountFilter(initial2.getFirst(), school2, 0, "2", "pending", "unconfirmed", "active")).size());
		try (var connection = Client.createConnection()) {
			assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM research_subject_identifiers"));
			assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM student_teacher_credentials"));
			assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM schools WHERE security_level_locked"));
			try (var statement = connection.prepareStatement("SELECT encrypted_password FROM student_teacher_credentials"); var rows = statement.executeQuery()) {
				while (rows.next()) assertFalse(rows.getString(1).contains(new String(INITIAL)));
			}
		}
		var login1 = authentication.authenticate(level1.loginId(), INITIAL, LoginPortal.STUDENT, metadata);
		var login2 = authentication.authenticate(level2.loginId(), INITIAL, LoginPortal.STUDENT, metadata);
		assertTrue(login1.authenticated()); assertTrue(login2.authenticated());
		var session = new StudentSessionControl();
		var user1 = user(level1, false); var user2 = user(level2, true);
		assertEquals(1L, session.loginVersion(login2.user()).orElseThrow()); assertTrue(session.isCurrent(user2, 1L));
		assertEquals(AuthenticationControl.PasswordChangeResult.NOT_ALLOWED, authentication.changeStudentPassword(level1.userId(), INITIAL, "Personal34!Aa".toCharArray(), metadata));
		assertEquals(AuthenticationControl.PasswordChangeResult.SUCCESS,
				authentication.changeStudentPassword(level2.userId(), INITIAL, "Personal34!Aa".toCharArray(), metadata, 1L));
		assertFalse(session.isCurrent(user2, 1L));
		assertEquals(2L, session.afterPasswordChange(user2, "Personal34!Aa".toCharArray()).orElseThrow());
		level2 = account(level2.loginId());
		assertFalse(level2.passwordAvailable()); assertFalse(level2.mustChangePassword());
		assertThrows(IllegalArgumentException.class, () -> control.reveal(teacher, target(account(initial2.getFirst()))));
		assertEquals("", control.export(teacher, account -> account.loginId().equals(initial2.getFirst())).getFirst().password());
		char[] reset = "Reset56!Aa".toCharArray();
		control.change(teacher, List.of(target(level2)), "reset", reset);
		assertFalse(session.isCurrent(user(level2, false), 2L));
		level2 = account(initial2.getFirst()); assertTrue(level2.mustChangePassword()); assertEquals(3, level2.version());
		assertEquals(new String(reset), control.reveal(teacher, target(level2)));
		assertFalse(authentication.authenticate(level2.loginId(), "Personal34!Aa".toCharArray(), LoginPortal.STUDENT, metadata).authenticated());
		assertTrue(authentication.authenticate(level2.loginId(), reset, LoginPortal.STUDENT, metadata).authenticated());
		assertEquals(AuthenticationControl.PasswordChangeResult.NOT_ALLOWED,
				authentication.changeStudentPassword(level2.userId(), reset, "New78!Aa".toCharArray(), metadata, 2L));
		assertThrows(IllegalArgumentException.class, () -> control.change(teacher, List.of(target(account(initial1.getFirst()))), "reset", reset));

		var foreign = control.create(other, new StudentAccountCreation(outside, outsideClass, null, 1), INITIAL).getFirst();
		long foreignId = control.list(other).stream().filter(a -> a.loginId().equals(foreign)).findFirst().orElseThrow().userId();
		assertThrows(SecurityException.class, () -> control.detail(teacher, foreignId));
		assertThrows(SecurityException.class, () -> control.create(teacher, new StudentAccountCreation(outside, outsideClass, null, 1), INITIAL));
		assertThrows(SecurityException.class, () -> control.create(teacher, new StudentAccountCreation(school1, outsideClass, null, 1), INITIAL));
		assertEquals(3, control.list(teacher).size());
		assertThrows(SecurityException.class, () -> control.change(teacher, List.of(target(account(initial1.getFirst())), new Target(foreignId, 1)), "delete", null));
		assertEquals("active", account(initial1.getFirst()).status());
		assertThrows(IllegalArgumentException.class, () -> control.change(teacher,
				List.of(new Target(account(initial1.getFirst()).userId(), 100), target(account(initial1.get(1)))), "delete", null));
		assertEquals("active", account(initial1.get(1)).status());
		control.change(teacher, List.of(target(level1)), "suspend", null);
		assertFalse(session.isCurrent(user1, 1L));
		assertFalse(authentication.authenticate(level1.loginId(), INITIAL, LoginPortal.STUDENT, metadata).authenticated());
		control.change(teacher, List.of(target(account(initial1.getFirst()))), "activate", null);
		assertTrue(authentication.authenticate(level1.loginId(), INITIAL, LoginPortal.STUDENT, metadata).authenticated());
		for (int i = 0; i < 5; i++) authentication.authenticate(level1.loginId(), "incorrect".toCharArray(), LoginPortal.STUDENT, metadata);
		assertTrue(account(level1.loginId()).loginLocked());
		control.change(teacher, List.of(target(account(level1.loginId()))), "unlock", null);
		assertFalse(account(level1.loginId()).loginLocked());
		assertTrue(authentication.authenticate(level1.loginId(), INITIAL, LoginPortal.STUDENT, metadata).authenticated());
		long id = level1.userId();
		control.change(teacher, List.of(target(account(initial1.getFirst())), target(account(initial1.get(1)))), "delete", null);
		assertEquals("deleted", account(initial1.getFirst()).status());
		assertFalse(authentication.authenticate(level1.loginId(), INITIAL, LoginPortal.STUDENT, metadata).authenticated());
		assertFalse(account(initial1.getFirst()).passwordAvailable());
		assertThrows(IllegalArgumentException.class, () -> control.change(teacher, List.of(target(account(initial1.getFirst()))), "activate", null));
		assertFalse(((List<?>) control.detail(teacher, id).get("login")).isEmpty());
		assertFalse(((List<?>) control.detail(teacher, id).get("credentials")).isEmpty());
		try (var connection = Client.createConnection()) {
			assertEquals(4, scalar(connection, "SELECT COUNT(*) FROM research_subject_identifiers"));
			assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM users WHERE account_status='deleted'"));
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE detail LIKE '%Synthetic12%' OR detail LIKE '%Reset56%'"));
			try (var statement = connection.prepareStatement("UPDATE teacher_feature_permissions SET is_enabled=0 WHERE teacher_user_id=?")) {
				statement.setLong(1, teacher.userId()); statement.executeUpdate();
			}
		}
		assertThrows(SecurityException.class, () -> control.list(teacher));
	}

	@Test void serializesConcurrentIdAllocationAndRollsBackFailedClassCreation() throws Exception {
		try (var connection = Client.createConnection()) {
			teacher(connection, "s400");
		}
		try (var executor = Executors.newFixedThreadPool(2)) {
			var one = executor.submit(() -> control.create(teacher, new StudentAccountCreation(school1, class1, null, 2), INITIAL));
			var two = executor.submit(() -> control.create(other, new StudentAccountCreation(school2, class2, null, 2), INITIAL));
			var ids = new HashSet<>(one.get()); assertTrue(ids.addAll(two.get())); assertEquals(4, ids.size());
			assertEquals(java.util.Set.of("s401", "s402", "s403", "s404"), ids);
		}
		try (var connection = Client.createConnection(); var statement = connection.prepareStatement("UPDATE schools SET security_level=NULL WHERE school_id=?")) {
			statement.setLong(1, outside); statement.executeUpdate();
		}
		assertThrows(SQLException.class, () -> control.create(other, new StudentAccountCreation(outside, 0, "Must rollback", 1), INITIAL));
		try (var connection = Client.createConnection()) {
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM classrooms WHERE name='Must rollback'"));
			assertEquals(4, scalar(connection, "SELECT COUNT(*) FROM users WHERE user_type='student'"));
		}
	}

	private ManagedStudentAccount account(String login) throws SQLException {
		return control.list(teacher).stream().filter(account -> account.loginId().equals(login)).findFirst().orElseThrow();
	}
	private static Target target(ManagedStudentAccount account) { return new Target(account.userId(), account.version()); }
	private static AuthenticatedUser user(ManagedStudentAccount account, boolean required) {
		return new AuthenticatedUser(account.userId(), account.loginId(), account.loginId(), UserType.STUDENT, required, "synthetic");
	}
	private static AuthenticatedUser teacher(Connection connection, String login) throws SQLException {
		long id;
		try (var statement = connection.prepareStatement("""
				INSERT INTO users (user_type,login_id,password_hash,display_name,account_status,created_at)
				VALUES ('teacher',?,?,'Synthetic','active',UTC_TIMESTAMP())
				""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, login); statement.setString(2, new PasswordHasher().hash(INITIAL));
			statement.executeUpdate(); try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); id = keys.getLong(1); }
		}
		try (var statement = connection.prepareStatement("""
				INSERT INTO teacher_feature_permissions (teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at)
				VALUES (?,'account-management',true,?,UTC_TIMESTAMP())
				""")) { statement.setLong(1, id); statement.setLong(2, id); statement.executeUpdate(); }
		return new AuthenticatedUser(id, login, login, UserType.TEACHER, false, "synthetic");
	}
	private static long classroom(Connection connection, long school) throws SQLException {
		return insert(connection, "INSERT INTO classrooms (school_id,name,classroom_status,created_at) VALUES (" + school + ",'Class','active',UTC_TIMESTAMP())");
	}
	private static void grant(Connection connection, long teacher, long school) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO teacher_school_permissions (teacher_user_id,school_id,access_status,updated_by_user_id,updated_at)
				VALUES (?,?,'enabled',?,UTC_TIMESTAMP())
				""")) { statement.setLong(1, teacher); statement.setLong(2, school); statement.setLong(3, teacher); statement.executeUpdate(); }
	}
	private static long insert(Connection connection, String sql) throws SQLException {
		try (var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
			statement.executeUpdate(); try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); return keys.getLong(1); }
		}
	}
	private static long scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getLong(1); }
	}
	@AfterEach void cleanup() throws SQLException {
		if (!initialized) return;
		try (var connection = Client.createConnection(); var statement = connection.createStatement()) {
			for (String table : List.of("student_teacher_credentials","credential_history","password_reset_records","login_history","audit_logs",
					"research_subject_identifiers","student_class_memberships","student_profiles","teacher_feature_permissions","teacher_school_permissions"))
				statement.executeUpdate("DELETE FROM " + table);
			statement.executeUpdate("UPDATE users SET created_by_user_id=NULL,updated_by_user_id=NULL");
			for (String table : List.of("users","classrooms","schools")) statement.executeUpdate("DELETE FROM " + table);
		}
	}
}
