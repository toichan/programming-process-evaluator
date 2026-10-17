package lib.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.Objects;

public final class DateTimeContract {
	private static final ZoneId USER_ZONE = ZoneId.of("Asia/Tokyo");

	private DateTimeContract() {}

	public static LocalDateTime fromUserInput(LocalDateTime userDateTime) {
		return Objects.requireNonNull(userDateTime)
				.atZone(USER_ZONE)
				.withZoneSameInstant(ZoneOffset.UTC)
				.toLocalDateTime();
	}

	public static LocalDateTime toUserDisplay(LocalDateTime utcDateTime) {
		return Objects.requireNonNull(utcDateTime)
				.atZone(ZoneOffset.UTC)
				.withZoneSameInstant(USER_ZONE)
				.toLocalDateTime();
	}

	public static LocalDateTime fromDatabaseTimestamp(Timestamp timestamp) {
		return Objects.requireNonNull(timestamp).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
	}

	public static LocalDateTime utcNow() {
		return LocalDateTime.now(Clock.systemUTC());
	}
}
