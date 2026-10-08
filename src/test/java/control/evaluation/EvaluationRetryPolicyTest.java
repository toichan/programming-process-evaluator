package control.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class EvaluationRetryPolicyTest {
	@Test
	void parsesSecondsAndHttpDatesWithoutShorteningTheRequestedDelay() {
		Instant now = Instant.parse("2026-10-07T20:00:00Z");
		assertEquals(Duration.ofSeconds(30), EvaluationRetryPolicy.parseRetryAfter("30", now));
		assertEquals(Duration.ofSeconds(30),
				EvaluationRetryPolicy.parseRetryAfter("Wed, 7 Oct 2026 20:00:30 GMT", now));
		assertEquals(Duration.ZERO,
				EvaluationRetryPolicy.parseRetryAfter("Wed, 7 Oct 2026 19:00:00 GMT", now));
		assertNull(EvaluationRetryPolicy.parseRetryAfter("-1", now));
		assertNull(EvaluationRetryPolicy.parseRetryAfter("invalid", now));
		assertNull(EvaluationRetryPolicy.parseRetryAfter(null, now));
	}

	@Test
	void honorsRetryAfterAndStopsRatherThanViolatingDeadline() throws Exception {
		AtomicLong clock = new AtomicLong();
		List<Long> waits = new ArrayList<>();
		EvaluationRetryPolicy policy = new EvaluationRetryPolicy(clock::get, waits::add);
		EvaluationProviderException busy = new EvaluationProviderException("Busy", true, 503)
				.withRetryAfter(Duration.ofSeconds(90));
		policy.awaitNext(0, busy);
		assertEquals(List.of(90_000L), waits);
		clock.set(631_000);
		assertThrows(EvaluationProviderException.class, () -> policy.awaitNext(1, busy));
		assertEquals(1, waits.size());
	}

	@Test
	void detectsDelayedWakeupBeforeSendingAnotherRequest() {
		AtomicLong clock = new AtomicLong();
		EvaluationRetryPolicy policy = new EvaluationRetryPolicy(clock::get, millis -> clock.set(541_000));
		assertThrows(EvaluationProviderException.class,
				() -> policy.awaitNext(0, new IllegalArgumentException()));
	}

	@Test
	void reservesThreeMinuteRequestsWithinApprovedTwelveMinuteBudget() throws Exception {
		assertEquals(Duration.ofSeconds(180), EvaluationRetryPolicy.REQUEST_TIMEOUT);
		assertEquals(Duration.ofMinutes(12), EvaluationRetryPolicy.PROCESSING_BUDGET);
		assertTrue(EvaluationRetryPolicy.INTERRUPTED_AFTER.compareTo(EvaluationRetryPolicy.PROCESSING_BUDGET) > 0);
		AtomicLong clock = new AtomicLong();
		List<Long> waits = new ArrayList<>();
		EvaluationRetryPolicy policy = new EvaluationRetryPolicy(clock::get, waits::add);
		clock.set(480_000);
		policy.awaitNext(1, new IllegalArgumentException("Invalid structured output"));
		assertEquals(1, waits.size());
		clock.set(541_000);
		assertThrows(EvaluationProviderException.class,
				() -> policy.awaitNext(1, new IllegalArgumentException("Invalid structured output")));
	}

	@Test
	void spacesProviderRetriesWithoutShorteningRetryAfter() throws Exception {
		List<Long> waits = new ArrayList<>();
		EvaluationRetryPolicy policy = new EvaluationRetryPolicy(() -> 0, waits::add);
		policy.awaitNext(0, new EvaluationProviderException("Busy", true, 503).withRetryAfter(Duration.ofSeconds(30)));
		policy.awaitNext(1, new EvaluationProviderException("Busy", true, 503));
		assertTrue(waits.get(0) >= 30_000 && waits.get(0) <= 60_000);
		assertTrue(waits.get(1) >= 60_000 && waits.get(1) <= 120_000);
	}

	@Test
	void boundsJitterAndPreservesInterrupt() throws Exception {
		List<Long> waits = new ArrayList<>();
		EvaluationRetryPolicy policy = new EvaluationRetryPolicy(() -> 0, waits::add);
		policy.awaitNext(0, new IllegalArgumentException("Invalid output"));
		policy.awaitNext(1, new IllegalArgumentException("Invalid output"));
		assertTrue(waits.get(0) >= 250 && waits.get(0) <= 1_000);
		assertTrue(waits.get(1) >= 250 && waits.get(1) <= 2_000);
		EvaluationRetryPolicy interrupted = new EvaluationRetryPolicy(() -> 0, millis -> {
			throw new InterruptedException();
		});
		try {
			assertThrows(EvaluationProviderException.class,
					() -> interrupted.awaitNext(0, new IllegalArgumentException()));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}
}
