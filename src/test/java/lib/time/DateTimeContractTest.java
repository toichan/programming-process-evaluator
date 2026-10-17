package lib.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

class DateTimeContractTest {
	@Test
	void convertsJapanInputAcrossUtcDateBoundaryAndBack() {
		LocalDateTime userInput = LocalDateTime.of(2026, 10, 18, 0, 30);

		LocalDateTime utc = DateTimeContract.fromUserInput(userInput);

		assertEquals(LocalDateTime.of(2026, 10, 17, 15, 30), utc);
		assertEquals(userInput, DateTimeContract.toUserDisplay(utc));
	}

	@Test
	void displaysUtcValuesInJapanTimeAcrossDateBoundary() {
		assertEquals(
				LocalDateTime.of(2026, 10, 18, 0, 30),
				DateTimeContract.toUserDisplay(LocalDateTime.of(2026, 10, 17, 15, 30)));
	}

	@Test
	void readsDatabaseTimestampsAsUtc() {
		assertEquals(
				LocalDateTime.of(2026, 10, 17, 15, 30),
				DateTimeContract.fromDatabaseTimestamp(Timestamp.from(Instant.parse("2026-10-17T15:30:00Z"))));
	}
}
