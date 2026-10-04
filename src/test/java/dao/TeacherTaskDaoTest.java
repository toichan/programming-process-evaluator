package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.TeacherTaskInput;

class TeacherTaskDaoTest {
	private final TeacherTaskDao dao = new TeacherTaskDao();

	@Test
	void requiresTransactionBeforeInsertingDraft() {
		assertThrows(IllegalStateException.class,
				() -> dao.insertDraft(connection(true, new ArrayList<>()), 1, emptyInput()));
	}

	@Test
	void rejectsInvalidTeacherBeforeReadingDrafts() {
		assertThrows(SecurityException.class,
				() -> dao.findDrafts(connection(false, new ArrayList<>()), 0));
	}

	@Test
	void rejectsInvalidTaskBeforeReadingDraft() {
		assertThrows(IllegalArgumentException.class,
				() -> dao.findDraft(connection(false, new ArrayList<>()), 1, 0, false));
	}

	@Test
	void insertsDraftWithInitialOptimisticLockVersion() throws SQLException {
		List<String> preparedSql = new ArrayList<>();
		Connection connection = insertConnection(preparedSql, 41);
		TeacherTaskDao taskDao = new TeacherTaskDao(ignored -> 17L);

		assertEquals(41, taskDao.insertDraft(connection, 7, emptyInput()));
		assertEquals(1, preparedSql.size());
		String normalizedSql = preparedSql.get(0).replaceAll("\\s+", " ");
		assertTrue(normalizedSql.contains("publication_status, created_at, version"));
		assertTrue(normalizedSql.contains("'draft', 'draft', CURRENT_TIMESTAMP, 1)"));
		assertTrue(normalizedSql.contains("created_by_user_id, updated_by_user_id, rubric_id,"));
	}

	private static TeacherTaskInput emptyInput() {
		return new TeacherTaskInput(
				"Task", null, null, "Description", null, null, null,
				List.of(), List.of(), List.of(), List.of());
	}

	private static Connection connection(boolean autoCommit, List<String> preparedSql) {
		return (Connection) Proxy.newProxyInstance(
				Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, arguments) -> {
					if ("getAutoCommit".equals(method.getName())) {
						return autoCommit;
					}
					if ("prepareStatement".equals(method.getName())) {
						preparedSql.add((String) arguments[0]);
						throw new AssertionError("The database must not be queried in this test.");
					}
					if ("toString".equals(method.getName())) {
						return "TestConnection";
					}
					throw new SQLException("Unexpected connection method: " + method.getName());
				});
	}

	private static Connection insertConnection(List<String> preparedSql, long generatedId) {
		return (Connection) Proxy.newProxyInstance(
				Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, arguments) -> {
					if ("getAutoCommit".equals(method.getName())) {
						return false;
					}
					if ("prepareStatement".equals(method.getName())) {
						preparedSql.add((String) arguments[0]);
						return insertStatement(generatedId);
					}
					if ("toString".equals(method.getName())) {
						return "InsertTestConnection";
					}
					throw new SQLException("Unexpected connection method: " + method.getName());
				});
	}

	private static PreparedStatement insertStatement(long generatedId) {
		return (PreparedStatement) Proxy.newProxyInstance(
				PreparedStatement.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class },
				(proxy, method, arguments) -> {
					if ("executeUpdate".equals(method.getName())) {
						return 1;
					}
					if ("getGeneratedKeys".equals(method.getName())) {
						return generatedKeys(generatedId);
					}
					if ("close".equals(method.getName()) || method.getName().startsWith("set")) {
						return null;
					}
					if ("toString".equals(method.getName())) {
						return "InsertTestStatement";
					}
					throw new SQLException("Unexpected statement method: " + method.getName());
				});
	}

	private static ResultSet generatedKeys(long generatedId) {
		boolean[] hasNext = { true };
		return (ResultSet) Proxy.newProxyInstance(
				ResultSet.class.getClassLoader(),
				new Class<?>[] { ResultSet.class },
				(proxy, method, arguments) -> {
					if ("next".equals(method.getName())) {
						boolean result = hasNext[0];
						hasNext[0] = false;
						return result;
					}
					if ("getLong".equals(method.getName())) {
						return generatedId;
					}
					if ("close".equals(method.getName())) {
						return null;
					}
					if ("toString".equals(method.getName())) {
						return "GeneratedKeys";
					}
					throw new SQLException("Unexpected result-set method: " + method.getName());
				});
	}
}
