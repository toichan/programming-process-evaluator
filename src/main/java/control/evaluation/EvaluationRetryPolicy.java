package control.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

public final class EvaluationRetryPolicy {
	private static final java.util.logging.Logger LOGGER =
			java.util.logging.Logger.getLogger(EvaluationRetryPolicy.class.getName());
	public static final int MAX_ATTEMPTS = 3;
	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);
	public static final Duration PROCESSING_BUDGET = Duration.ofMinutes(12);
	public static final Duration INTERRUPTED_AFTER = Duration.ofMinutes(15);
	private static final long BUDGET_MILLIS = PROCESSING_BUDGET.toMillis();
	private static final long REQUEST_MILLIS = REQUEST_TIMEOUT.toMillis();
	private final LongSupplier elapsedMillis;
	private final Sleeper sleeper;
	private final long startedAt;

	public EvaluationRetryPolicy() {
		this(() -> System.nanoTime() / 1_000_000, Thread::sleep);
	}

	EvaluationRetryPolicy(LongSupplier elapsedMillis, Sleeper sleeper) {
		this.elapsedMillis = elapsedMillis;
		this.sleeper = sleeper;
		this.startedAt = elapsedMillis.getAsLong();
	}

	public void awaitNext(int failedAttempt, Exception failure) throws EvaluationProviderException {
		if (Thread.currentThread().isInterrupted()) {
			throw new EvaluationProviderException("AI processing was interrupted.", false);
		}
		Duration retryAfter = failure instanceof EvaluationProviderException provider
				? provider.getRetryAfter() : null;
		long backoff = failure instanceof EvaluationProviderException
				? ThreadLocalRandom.current().nextLong(30_000L << failedAttempt, (60_000L << failedAttempt) + 1)
				: ThreadLocalRandom.current().nextLong(250, (1_000L << failedAttempt) + 1);
		long delay = retryAfter == null ? backoff : Math.max(backoff, retryAfter.toMillis());
		long remaining = BUDGET_MILLIS - (elapsedMillis.getAsLong() - startedAt);
		if (delay > remaining - REQUEST_MILLIS) {
			throw new EvaluationProviderException("AI retry deadline was exceeded.", false);
		}
		try {
			LOGGER.info("AI retry scheduled: nextAttempt=" + (failedAttempt + 2) + ", delayMs=" + delay
					+ ", retryAfter=" + (retryAfter != null));
			sleeper.sleep(Math.max(0, delay));
			if (elapsedMillis.getAsLong() - startedAt > BUDGET_MILLIS - REQUEST_MILLIS) {
				throw new EvaluationProviderException("AI retry deadline was exceeded after waiting.", false);
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new EvaluationProviderException("AI retry was interrupted.", false, interrupted);
		}
	}

	public static Duration parseRetryAfter(String value, Instant now) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			long seconds = Long.parseLong(value.trim());
			return seconds < 0 ? null : Duration.ofSeconds(Math.min(seconds, 86_400));
		} catch (NumberFormatException ignored) {
			try {
				Duration delay = Duration.between(now,
						ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
				return delay.isNegative() ? Duration.ZERO
						: delay.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : delay;
			} catch (DateTimeParseException invalidHeader) {
				return null;
			}
		}
	}

	@FunctionalInterface
	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}
}
