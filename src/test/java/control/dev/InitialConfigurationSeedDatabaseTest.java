package control.dev;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import lib.mysql.Client;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class InitialConfigurationSeedDatabaseTest {
	@Test
	void reproducesApprovedLocalConfigurationAtomicallyAndFailsClosed() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("INITIAL_SEED_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertNotNull(database);
		assertTrue(database.matches("ppe_initial_seed_[a-z0-9_]+"));
		var expected = InitialConfigurationSeed.readPrivate(Path.of(System.getenv("INITIAL_SEED_SNAPSHOT")));
		try (Connection c = Client.createConnection()) {
			assertEquals(database, c.getCatalog());
			try (var statement = c.createStatement()) {
				statement.executeUpdate("""
						CREATE TRIGGER fail_initial_seed_option BEFORE INSERT ON survey_question_options
						FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic initial seed rollback probe'
						""");
			}
			assertThrows(java.sql.SQLException.class, () -> InitialConfigurationSeed.apply(c, expected));
			for (String table : InitialConfigurationSeed.TABLES) {
				assertEquals("student_login_sequence".equals(table) ? 1 : 0, count(c, table), table);
			}
			try (var statement = c.createStatement()) {
				statement.executeUpdate("DROP TRIGGER fail_initial_seed_option");
			}
			assertTrue(InitialConfigurationSeed.apply(c, expected));
			InitialConfigurationSeed.requireEqual(expected, InitialConfigurationSeed.capture(c));
			assertFalse(InitialConfigurationSeed.apply(c, expected));
			InitialConfigurationSeed.requireEqual(expected, InitialConfigurationSeed.capture(c));
			try (var statement = c.createStatement()) {
				statement.executeUpdate("""
						INSERT INTO login_history (attempted_login_id,operation_type,result_status,occurred_at)
						VALUES ('synthetic-seed-history','login','failure',UTC_TIMESTAMP())
						""");
			}
			assertThrows(IllegalStateException.class, () -> InitialConfigurationSeed.apply(c, expected));
			try (var statement = c.createStatement()) {
				statement.executeUpdate("DELETE FROM login_history WHERE attempted_login_id='synthetic-seed-history'");
			}
			for (String table : java.util.List.of("submissions", "code_logs", "evaluations", "survey_responses",
					"consent_records", "research_subject_identifiers", "login_history", "audit_logs")) {
				assertEquals(0, count(c, table), table);
			}
			try (var other = Client.createConnection(); var statement = other.prepareStatement("SELECT GET_LOCK(?,0)")) {
				statement.setString(1, "initial-seed:" + database);
				try (var rows = statement.executeQuery()) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				assertThrows(IllegalStateException.class, () -> InitialConfigurationSeed.apply(c, expected));
				try (var release = other.prepareStatement("SELECT RELEASE_LOCK(?)")) {
					release.setString(1, "initial-seed:" + database);
					release.executeQuery().close();
				}
			}
			try (var statement = c.createStatement()) {
				statement.executeUpdate("UPDATE criterion_levels SET level_description='Synthetic mismatch' "
						+ "ORDER BY criterion_level_id LIMIT 1");
			}
			var before = InitialConfigurationSeed.capture(c);
			assertThrows(IllegalStateException.class, () -> InitialConfigurationSeed.apply(c, expected));
			InitialConfigurationSeed.requireEqual(before, InitialConfigurationSeed.capture(c));
			var levels = expected.getAsJsonObject("data").getAsJsonObject("criterion_levels");
			var columns = levels.getAsJsonArray("columns");
			int description = -1;
			for (int i = 0; i < columns.size(); i++) {
				if ("level_description".equals(columns.get(i).getAsString())) description = i;
			}
			try (var statement = c.prepareStatement("UPDATE criterion_levels SET level_description=? WHERE criterion_level_id=?")) {
				for (var row : levels.getAsJsonArray("rows")) {
					statement.setString(1, row.getAsJsonArray().get(description).getAsString());
					statement.setString(2, row.getAsJsonArray().get(0).getAsString());
					statement.executeUpdate();
				}
			}
			InitialConfigurationSeed.requireEqual(expected, InitialConfigurationSeed.capture(c));
			try (var reopened = Client.createConnection()) {
				InitialConfigurationSeed.requireEqual(expected, InitialConfigurationSeed.capture(reopened));
				assertFalse(InitialConfigurationSeed.apply(reopened, expected));
			}
		}
	}

	private static long count(Connection c, String table) throws Exception {
		try (var statement = c.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
			rows.next();
			return rows.getLong(1);
		}
	}
}
