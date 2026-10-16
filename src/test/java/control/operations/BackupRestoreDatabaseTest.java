package control.operations;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.teacher.TeacherProgressControl;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class BackupRestoreDatabaseTest {
	@Test
	void readsRestoredBusinessRecordsThroughApplicationControlsWithoutChangingTheSnapshot() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("PPE_RESTORE_DB_TEST")));
		assertTrue(System.getenv("DB_HOST").matches("ppe-restore-phase20-[0-9]{8}-db-1"));
		assertEquals("ppe", System.getenv("DB_NAME"));
		try (Connection connection = Client.createConnection()) {
			assertEquals("ppe", connection.getCatalog());
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
			for (String table : new String[] { "users", "tasks", "submissions", "evaluations" }) {
				assertTrue(count(connection, "SELECT COUNT(*) FROM `" + table + "`") > 0,
						"Restore must contain business records: " + table);
			}
			Map<String, String> before = fingerprints(connection);
			int teachersWithRows = 0;
			int rowsRead = 0;
			var control = new TeacherProgressControl();
			try (var statement = connection.createStatement();
					var teachers = statement.executeQuery("""
							SELECT u.user_id, u.login_id, u.display_name FROM users u
							JOIN teacher_feature_permissions fp ON fp.teacher_user_id=u.user_id
							  AND fp.feature_code='task-progress' AND fp.is_enabled=TRUE
							WHERE u.user_type='teacher' AND u.account_status='active' AND u.deleted_at IS NULL
							""")) {
				while (teachers.next()) {
					var teacher = new AuthenticatedUser(teachers.getLong(1), teachers.getString(2),
							teachers.getString(3), UserType.TEACHER, false, "isolated-backup-readback");
					var rows = control.loadRows(teacher);
					if (rows.isEmpty()) continue;
					teachersWithRows++;
					rowsRead += rows.size();
					for (var row : rows) {
						var detail = control.loadDetail(teacher, row.assignmentId(), row.studentUserId());
						assertEquals(row.assignmentId(), detail.row().assignmentId());
						assertEquals(row.studentUserId(), detail.row().studentUserId());
						assertEquals(row.participationId(), detail.row().participationId());
					}
				}
			}
			assertTrue(teachersWithRows > 0, "At least one restored teacher must read populated progress");
			assertTrue(rowsRead > 0);
			assertThrows(SecurityException.class, () -> control.loadRows(null));
			assertEquals(before, fingerprints(connection), "Application readback must not change restored records");
			System.out.printf("Restored tables=%d, teachers with progress=%d, application rows=%d; fingerprints unchanged.%n",
					before.size(), teachersWithRows, rowsRead);
		}
	}

	private static long count(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	private static Map<String, String> fingerprints(Connection connection) throws Exception {
		var result = new LinkedHashMap<String, String>();
		var tables = new ArrayList<String>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name
				""")) {
			while (rows.next()) tables.add(rows.getString(1));
		}
		for (String table : tables) {
			assertTrue(table.matches("[A-Za-z0-9_]+"));
			var hashes = new ArrayList<String>();
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT * FROM `" + table + "`")) {
				int columns = rows.getMetaData().getColumnCount();
				while (rows.next()) {
					var hash = MessageDigest.getInstance("SHA-256");
					for (int column = 1; column <= columns; column++) {
						byte[] value = rows.getBytes(column);
						hash.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
						if (value != null) hash.update(value);
					}
					hashes.add(HexFormat.of().formatHex(hash.digest()));
				}
			}
			hashes.sort(String::compareTo);
			var hash = MessageDigest.getInstance("SHA-256");
			for (String row : hashes) hash.update(row.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			result.put(table, hashes.size() + ":" + HexFormat.of().formatHex(hash.digest()));
		}
		return result;
	}
}
