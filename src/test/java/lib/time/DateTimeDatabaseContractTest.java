package lib.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import lib.mysql.Client;

class DateTimeDatabaseContractTest {
	@Test
	void jdbcSessionAndDatetimeRoundTripUseUtc() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("DATETIME_DB_TEST")));

		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT @@session.time_zone")) {
				assertTrue(result.next());
				assertTrue(Set.of("UTC", "+00:00").contains(result.getString(1)),
						"JDBC session timezone must be UTC.");
			}

			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TEMPORARY TABLE ppe_datetime_contract_probe (utc_value DATETIME NOT NULL)");
			}

			LocalDateTime expectedUtc = LocalDateTime.of(2026, 10, 17, 15, 30);
			try (PreparedStatement insert = connection.prepareStatement(
					"INSERT INTO ppe_datetime_contract_probe (utc_value) VALUES (?)")) {
				insert.setObject(1, expectedUtc);
				assertEquals(1, insert.executeUpdate());
			}

			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT utc_value FROM ppe_datetime_contract_probe")) {
				assertTrue(result.next());
				assertEquals(expectedUtc, DateTimeContract.fromDatabaseTimestamp(result.getTimestamp(1)));
				assertFalse(result.next());
			}
		}
	}
}
