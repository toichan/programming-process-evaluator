package control.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import dao.UserDao;
import dao.StudentDao;
import entity.SchoolDetails;
import entity.SchoolInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class SchoolDatabaseTest {
	private final SchoolControl control = new SchoolControl();
	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> schoolIds = new ArrayList<>();
	private AuthenticatedUser admin;

	@BeforeEach
	void createFixture() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("SCHOOL_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_school_test_[a-z0-9_]+"),
				"Use only a dedicated migrated school test database.");
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			long id = addUser(connection, "admin");
			admin = new AuthenticatedUser(id, "synthetic", "Synthetic", UserType.ADMIN, false, "synthetic");
		}
	}

	@Test
	void persistsSchoolPolicyAndBlocksOverridesWithAuditAndVersionChecks() throws SQLException {
		long id = control.save(admin, 0, 0, new SchoolInput("Synthetic school", 1));
		schoolIds.add(id);
		SchoolDetails first = school(id);
		assertEquals(1, first.securityLevel());
		assertFalse(first.securityLevelLocked());
		control.save(admin, id, first.version(), new SchoolInput("Synthetic renamed", 2));
		SchoolDetails second = school(id);
		assertEquals(2, second.securityLevel());
		assertEquals(first.version() + 1, second.version());
		assertThrows(IllegalArgumentException.class,
				() -> control.save(admin, id, first.version(), new SchoolInput("Stale", 1)));
		control.save(admin, id, second.version(), new SchoolInput("Synthetic level1", 1));

		long studentId;
		try (Connection connection = Client.createConnection()) {
			studentId = addUser(connection, "student");
			execute(connection, """
					INSERT INTO student_profiles (user_id, student_code, school_id, security_level,
					  first_login_status, must_change_password)
					VALUES (?, ?, ?, 2, 'password_change_required', TRUE)
					""", studentId, UUID.randomUUID().toString().substring(0, 32), id);
			assertEquals(1, scalar(connection, "SELECT security_level FROM student_profiles WHERE user_id = ?", studentId));
			assertEquals(0, scalar(connection, "SELECT must_change_password FROM student_profiles WHERE user_id = ?", studentId));
			assertThrows(SQLException.class, () -> execute(connection,
					"UPDATE student_profiles SET security_level = 2 WHERE user_id = ?", studentId));
			assertThrows(SQLException.class, () -> execute(connection,
					"UPDATE schools SET security_level = 2 WHERE school_id = ?", id));
			assertThrows(SQLException.class, () -> execute(connection,
					"UPDATE schools SET security_level_locked = FALSE WHERE school_id = ?", id));
		}
		SchoolDetails locked = school(id);
		assertTrue(locked.securityLevelLocked());
		assertThrows(IllegalArgumentException.class,
				() -> control.save(admin, id, locked.version(), new SchoolInput("Forbidden", 2)));
		control.save(admin, id, locked.version(), new SchoolInput("Name remains editable", 1));
		try (Connection connection = Client.createConnection()) {
			execute(connection, "UPDATE users SET account_status = 'deleted', deleted_at = CURRENT_TIMESTAMP WHERE user_id = ?", studentId);
			execute(connection, "DELETE FROM student_profiles WHERE user_id = ?", studentId);
			assertTrue(school(id).securityLevelLocked());
			assertEquals(4, scalar(connection, "SELECT COUNT(*) FROM audit_logs WHERE feature_code = 'school-management' AND target_id = ?", id));
		}

		long level2 = control.save(admin, 0, 0, new SchoolInput("Synthetic level2", 2));
		schoolIds.add(level2);
		try (Connection connection = Client.createConnection()) {
			long student2 = addUser(connection, "student");
			execute(connection, """
					INSERT INTO student_profiles (user_id, student_code, school_id, security_level,
					  first_login_status, must_change_password) VALUES (?, ?, ?, 1, 'completed', FALSE)
					""", student2, UUID.randomUUID().toString().substring(0, 32), level2);
			var profile = new UserDao().findByUserIdForUpdate(connection, student2).orElseThrow().studentProfile().orElseThrow();
			assertEquals(2, profile.securityLevel());
			assertTrue(profile.mustChangePassword());
			assertEquals(2, new StudentDao().findAccountDetails(student2).orElseThrow().getSecurityLevel());
			assertThrows(SQLException.class, () -> execute(connection,
					"UPDATE student_profiles SET school_id = ? WHERE user_id = ?", id, student2));
		}
	}

	@Test
	void readsLoginIdInsteadOfLegacyCodeOrDisplayNameForAccountAndHistory() throws SQLException {
		long schoolId = control.save(admin, 0, 0, new SchoolInput("Synthetic identity school", 1));
		schoolIds.add(schoolId);
		String loginId = "student-" + "a".repeat(56);
		long student;
		try (Connection connection = Client.createConnection()) {
			student = addUser(connection, "student");
			execute(connection, "UPDATE users SET login_id = ? WHERE user_id = ?", loginId, student);
			execute(connection, """
					INSERT INTO student_profiles (user_id, student_code, school_id, security_level,
					  first_login_status, must_change_password) VALUES (?, 'legacy-id', ?, 1, 'not_logged_in', FALSE)
					""", student, schoolId);
			execute(connection, """
					INSERT INTO credential_history (target_user_id, actor_user_id, action_type, result_status, occurred_at)
					VALUES (?, ?, 'password_changed', 'success', CURRENT_TIMESTAMP)
					""", student, student);
		}
		var account = new StudentDao().findAccountDetails(student).orElseThrow();
		assertEquals(loginId, account.getStudentId());
		assertEquals("生徒（" + loginId + "）", account.getCredentialHistory().getFirst().getActorLabel());
	}

	@Test
	void rejectsTeachersStudentsAndSuspendedAdmin() throws SQLException {
		for (UserType role : new UserType[] {UserType.TEACHER, UserType.STUDENT}) {
			var user = new AuthenticatedUser(admin.userId(), "synthetic", "Synthetic", role, false, "synthetic");
			assertThrows(SecurityException.class, () -> control.loadSchools(user));
			assertThrows(SecurityException.class, () -> control.save(user, 0, 0, new SchoolInput("Forbidden", 1)));
		}
		try (Connection connection = Client.createConnection()) {
			execute(connection, "UPDATE users SET account_status = 'suspended' WHERE user_id = ?", admin.userId());
		}
		assertThrows(SecurityException.class, () -> control.loadSchools(admin));
		assertThrows(SecurityException.class, () -> control.save(admin, 0, 0, new SchoolInput("Forbidden", 1)));
	}

	@Test
	void requiresSchoolForRegistrationAndConfiguredSchoolForMembership() throws SQLException {
		long id = control.save(admin, 0, 0, new SchoolInput("Synthetic membership school", 1));
		schoolIds.add(id);
		try (Connection connection = Client.createConnection()) {
			long student = addUser(connection, "student");
			assertThrows(SQLException.class, () -> execute(connection, """
					INSERT INTO student_profiles (user_id, student_code, security_level,
					  first_login_status, must_change_password) VALUES (?, ?, 1, 'not_logged_in', FALSE)
					""", student, UUID.randomUUID().toString().substring(0, 32)));
			execute(connection, """
					INSERT INTO student_profiles (user_id, student_code, school_id, security_level,
					  first_login_status, must_change_password) VALUES (?, ?, ?, 1, 'not_logged_in', FALSE)
					""", student, UUID.randomUUID().toString().substring(0, 32), id);
			execute(connection, """
					INSERT INTO classrooms (school_id, name, classroom_status, created_at)
					VALUES (?, 'Synthetic class', 'active', CURRENT_TIMESTAMP)
					""", id);
			long classroom = scalar(connection, "SELECT classroom_id FROM classrooms WHERE school_id = ?", id);
			try {
				execute(connection, """
						INSERT INTO student_class_memberships (student_user_id, classroom_id, membership_status, joined_at)
						VALUES (?, ?, 'active', CURRENT_TIMESTAMP)
						""", student, classroom);
				execute(connection, "UPDATE student_class_memberships SET membership_status = 'inactive' WHERE student_user_id = ?", student);
				assertTrue(school(id).securityLevelLocked());
				assertThrows(SQLException.class, () -> execute(connection,
						"UPDATE schools SET security_level = 2 WHERE school_id = ?", id));
				assertThrows(SQLException.class, () -> execute(connection, """
						INSERT INTO student_class_memberships (student_user_id, classroom_id, membership_status, joined_at)
						VALUES (?, ?, 'active', CURRENT_TIMESTAMP)
						""", admin.userId(), classroom));
			} finally {
				execute(connection, "DELETE FROM student_class_memberships WHERE classroom_id = ?", classroom);
				execute(connection, "DELETE FROM classrooms WHERE classroom_id = ?", classroom);
			}
		}
	}

	private SchoolDetails school(long id) throws SQLException {
		return control.loadSchools(admin).stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}

	private long addUser(Connection connection, String role) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
				VALUES (?, ?, 'synthetic-not-a-password', 'Synthetic school fixture', 'active', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, role);
			statement.setString(2, "school-" + UUID.randomUUID());
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				long id = keys.getLong(1);
				userIds.add(id);
				return id;
			}
		}
	}

	@AfterEach
	void cleanup() throws SQLException {
		if (userIds.isEmpty()) return;
		try (Connection connection = Client.createConnection()) {
			for (long id : userIds) {
				execute(connection, "DELETE FROM audit_logs WHERE actor_user_id = ?", id);
				execute(connection, "DELETE FROM credential_history WHERE target_user_id = ?", id);
				execute(connection, "DELETE FROM student_profiles WHERE user_id = ?", id);
			}
			for (long id : schoolIds) execute(connection, "DELETE FROM schools WHERE school_id = ?", id);
			for (long id : userIds) execute(connection, "DELETE FROM users WHERE user_id = ?", id);
		}
	}

	private static void execute(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
			statement.executeUpdate();
		}
	}

	private static long scalar(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
			try (ResultSet rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getLong(1);
			}
		}
	}
}
