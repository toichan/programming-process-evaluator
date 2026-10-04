package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import dao.StandardRubricDao;
import lib.mysql.Client;

class StandardRubricDatabaseTest {
	@Test
	void registersExactImmutableStandardAndDoesNotAssignTasks() throws Exception {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("EXERCISE_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_exercise_test_[a-z0-9_]+"));
		boolean registered = false;
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertEquals(0, count(connection, "users"));
			var dao = new StandardRubricDao();
			assertTrue(dao.find().isEmpty());
			var expected = StandardRubricSourceTest.source();
			long tasksBefore = count(connection, "tasks");
			assertTrue(dao.register(expected));
			registered = true;
			assertEquals(expected, dao.find().orElseThrow());
			assertFalse(dao.register(expected));
			assertEquals(0, count(connection, "users"));
			try (Statement statement = connection.createStatement();
					ResultSet result = statement.executeQuery("SELECT created_by_user_id FROM rubrics")) {
				assertTrue(result.next());
				assertNull(result.getObject(1));
			}
			assertEquals(1, count(connection, "rubrics"));
			assertEquals(2, count(connection, "rubric_dimensions"));
			assertEquals(6, count(connection, "rubric_criteria"));
			assertEquals(30, count(connection, "criterion_levels"));
			assertEquals(tasksBefore, count(connection, "tasks"));
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("UPDATE criterion_levels SET level_description='Synthetic changed' "
						+ "ORDER BY criterion_level_id LIMIT 1");
			}
			assertThrows(IllegalStateException.class, () -> dao.register(expected));
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("DELETE FROM criterion_levels ORDER BY criterion_level_id LIMIT 1");
			}
			assertThrows(IllegalArgumentException.class, dao::find);
		} finally {
			if (registered) {
				try (Connection connection = Client.createConnection(); Statement statement = connection.createStatement()) {
					statement.executeUpdate("DELETE l FROM criterion_levels l JOIN rubric_criteria c "
							+ "ON c.criterion_id=l.criterion_id JOIN rubrics r ON r.rubric_id=c.rubric_id "
							+ "WHERE r.title='生徒共通標準ルーブリック' AND r.version='0805-2026-v1'");
					statement.executeUpdate("DELETE c FROM rubric_criteria c JOIN rubrics r ON r.rubric_id=c.rubric_id "
							+ "WHERE r.title='生徒共通標準ルーブリック' AND r.version='0805-2026-v1'");
					statement.executeUpdate("DELETE d FROM rubric_dimensions d JOIN rubrics r ON r.rubric_id=d.rubric_id "
							+ "WHERE r.title='生徒共通標準ルーブリック' AND r.version='0805-2026-v1'");
					statement.executeUpdate("DELETE FROM rubrics WHERE title='生徒共通標準ルーブリック' AND version='0805-2026-v1'");
				}
			}
		}
	}

	private static long count(Connection connection, String table) throws Exception {
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			result.next(); return result.getLong(1);
		}
	}
}
