package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import lib.mysql.Client;

class StudentExerciseSchemaTest {
	@Test
	void preservesLegacyContextsAndEnforcesExerciseReferencesAndVersion() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("EXERCISE_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_exercise_test_[a-z0-9_]+"),
				"Use a dedicated migrated exercise database.");
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			connection.setAutoCommit(false);
			try {
				long user = insert(connection, """
						INSERT INTO users (user_type, login_id, password_hash, display_name,
						  account_status, created_at)
						VALUES ('student', ?, 'synthetic-unusable', 'Synthetic', 'active', CURRENT_TIMESTAMP)
						""", "exercise-" + UUID.randomUUID());
				long school = insert(connection, """
						INSERT INTO schools (school_code, name, security_level, school_status, created_at)
						VALUES (?, 'Synthetic exercise school', 1, 'active', CURRENT_TIMESTAMP)
						""", "exercise-" + UUID.randomUUID());
				execute(connection, """
						INSERT INTO student_profiles (user_id, student_code, school_id, security_level,
						  first_login_status, must_change_password)
						VALUES (?, ?, ?, 1, 'completed', FALSE)
						""", user, UUID.randomUUID().toString().substring(0, 32), school);
				long exercise = insert(connection, """
						INSERT INTO student_exercises (student_user_id, exercise_origin, scope_name,
						  exercise_status, save_status, created_at)
						VALUES (?, 'student_created', 'Synthetic', 'in_progress', 'unsaved', CURRENT_TIMESTAMP)
						""", user);
				assertEquals(0, scalar(connection,
						"SELECT version FROM student_exercises WHERE student_exercise_id = ?", exercise));
				execute(connection, "UPDATE student_exercises SET version = version + 1 WHERE student_exercise_id = ?", exercise);
				assertEquals(1, scalar(connection,
						"SELECT version FROM student_exercises WHERE student_exercise_id = ?", exercise));
				assertThrows(SQLException.class, () -> execute(connection,
						"UPDATE student_exercises SET version = -1 WHERE student_exercise_id = ?", exercise));
				long entry = insert(connection, """
						INSERT INTO student_exercise_entries (student_exercise_id, entry_type, name,
						  path, current_content, entry_status, created_at)
						VALUES (?, 'file', 'test.py', 'test.py', 'print(1)', 'active', CURRENT_TIMESTAMP)
						""", exercise);
				for (String context : new String[] {"student_task", "submission_check",
						"teacher_review_copy", "distribution_template"}) {
					insertExecution(connection, context, null, user);
					assertThrows(SQLException.class, () -> insertExecution(connection, context, entry, user));
				}
				long execution = insertExecution(connection, "student_exercise", entry, user);
				assertEquals(entry, scalar(connection,
						"SELECT exercise_entry_id FROM code_executions WHERE execution_id = ?", execution));
				assertThrows(SQLException.class, () -> insertExecution(connection, "student_exercise", null, user));
				assertThrows(SQLException.class, () -> insertExecution(connection, "student_exercise", entry, null));
				assertThrows(SQLException.class,
						() -> insertExecution(connection, "student_exercise", Long.MAX_VALUE, user));
				assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM code_logs"));
				assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM submissions"));
				execute(connection, "UPDATE student_exercise_entries SET entry_status = 'trashed' WHERE exercise_entry_id = ?", entry);
				assertEquals(1, scalar(connection,
						"SELECT COUNT(*) FROM code_executions WHERE exercise_entry_id = ?", entry));
			} finally {
				connection.rollback();
			}
		}
	}

	private static long insertExecution(Connection connection, String context, Long entry, Long actor)
			throws SQLException {
		return insert(connection, """
				INSERT INTO code_executions (execution_context, exercise_entry_id, actor_user_id,
				  source_code, execution_status, standard_output_truncated, standard_error_truncated, executed_at)
				VALUES (?, ?, ?, 'print(1)', 'succeeded', FALSE, FALSE, CURRENT_TIMESTAMP)
				""", context, entry, actor);
	}

	private static long insert(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
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
