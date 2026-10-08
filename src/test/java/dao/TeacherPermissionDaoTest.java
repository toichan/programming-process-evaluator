package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TeacherPermissionDaoTest {
	private final TeacherPermissionDao dao = new TeacherPermissionDao();

	@Test
	void requiresTransactionBeforePerformingLockingPermissionQueries() {
		Connection connection = connection(true);

		assertThrows(IllegalStateException.class, () -> dao.requireActiveTeacher(connection, 1));
	}

	@Test
	void rejectsInvalidTeacherBeforeQueryingTheDatabase() {
		Connection connection = connection(false);

		assertThrows(SecurityException.class, () -> dao.requireActiveTeacher(connection, 0));
	}

	@Test
	void rejectsInvalidSchoolBeforeQueryingTheDatabase() {
		Connection connection = connection(false);

		assertThrows(IllegalArgumentException.class,
				() -> dao.requireAuthorizedSchool(connection, 1, 0));
	}

	@Test
	void rejectsInvalidClassBeforeQueryingTheDatabase() {
		Connection connection = connection(false);

		assertThrows(IllegalArgumentException.class,
				() -> dao.requireAuthorizedClass(connection, 1, 0));
	}

	@Test
	void loadsTaskPermissionAndActiveSchoolNamesForNavigation() throws SQLException {
		Connection connection = navigationConnection(List.of(
				Map.of("task_management_enabled", true, "school_id", 12L,
						"school_code", "SCH-12", "name", "北中学校"),
				Map.of("task_management_enabled", true, "school_id", 13L,
						"school_code", "SCH-13", "name", "南高校")), List.of("task-management", "code-distribution"));

		var summary = dao.findNavigationSummary(connection, 42);

		assertTrue(summary.taskManagementEnabled());
		assertTrue(summary.features().contains("code-distribution"));
		assertFalse(summary.features().contains("account-management"));
		assertEquals(List.of(
				new entity.TeacherSchoolOption(12, "SCH-12", "北中学校"),
				new entity.TeacherSchoolOption(13, "SCH-13", "南高校")), summary.schools());
	}

	@Test
	void returnsNoSchoolsAndDisabledPermissionWhenNoneAreAssigned() throws SQLException {
		Connection connection = navigationConnection(List.of(
				Map.of("task_management_enabled", false)));

		var summary = dao.findNavigationSummary(connection, 42);

		assertFalse(summary.taskManagementEnabled());
		assertTrue(summary.schools().isEmpty());
	}

	@Test
	void rejectsInactiveTeacherWhenLoadingNavigation() {
		Connection connection = navigationConnection(List.of());

		assertThrows(SecurityException.class, () -> dao.findNavigationSummary(connection, 42));
	}

	private static Connection navigationConnection(List<Map<String, Object>> rows) {
		return navigationConnection(rows, List.of());
	}

	private static Connection navigationConnection(List<Map<String, Object>> rows, List<String> features) {
		return (Connection) Proxy.newProxyInstance(
				Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, arguments) -> {
					if ("getAutoCommit".equals(method.getName())) return false;
					if ("prepareStatement".equals(method.getName())) {
						List<Map<String, Object>> selected = arguments[0].toString().contains("SELECT feature_code")
								? features.stream().map(code -> Map.<String, Object>of("feature_code", code)).toList() : rows;
						return navigationStatement(selected);
					}
					throw new SQLException("Unexpected connection method: " + method.getName());
				});
	}

	private static PreparedStatement navigationStatement(List<Map<String, Object>> rows) {
		return (PreparedStatement) Proxy.newProxyInstance(
				PreparedStatement.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class },
				(proxy, method, arguments) -> {
					if ("executeQuery".equals(method.getName())) {
						return resultSet(rows);
					}
					if ("setString".equals(method.getName()) || "setLong".equals(method.getName())
							|| "close".equals(method.getName())) {
						return null;
					}
					throw new SQLException("Unexpected statement method: " + method.getName());
				});
	}

	private static ResultSet resultSet(List<Map<String, Object>> rows) {
		List<Map<String, Object>> copiedRows = new ArrayList<>(rows);
		int[] index = { -1 };
		boolean[] wasNull = { false };
		return (ResultSet) Proxy.newProxyInstance(
				ResultSet.class.getClassLoader(),
				new Class<?>[] { ResultSet.class },
				(proxy, method, arguments) -> {
					if ("next".equals(method.getName())) {
						index[0]++;
						return index[0] < copiedRows.size();
					}
					if ("getBoolean".equals(method.getName())) {
						Object value = copiedRows.get(index[0]).get(arguments[0]);
						wasNull[0] = value == null;
						return Boolean.TRUE.equals(value);
					}
					if ("getLong".equals(method.getName())) {
						Object value = copiedRows.get(index[0]).get(arguments[0]);
						wasNull[0] = value == null;
						return value instanceof Number number ? number.longValue() : 0L;
					}
					if ("getString".equals(method.getName())) {
						Object value = copiedRows.get(index[0]).get(arguments[0]);
						wasNull[0] = value == null;
						return value == null ? null : value.toString();
					}
					if ("wasNull".equals(method.getName())) {
						return wasNull[0];
					}
					if ("close".equals(method.getName())) {
						return null;
					}
					throw new SQLException("Unexpected result set method: " + method.getName());
				});
	}

	private static Connection connection(boolean autoCommit) {
		return (Connection) Proxy.newProxyInstance(
				Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, arguments) -> {
					if ("getAutoCommit".equals(method.getName())) {
						return autoCommit;
					}
					if ("prepareStatement".equals(method.getName())) {
						throw new AssertionError("The database must not be queried in this test.");
					}
					if ("toString".equals(method.getName())) {
						return "TestConnection";
					}
					throw new SQLException("Unexpected connection method: " + method.getName());
				});
	}
}
