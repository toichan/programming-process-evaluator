package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TeacherTaskCreateRegistryTest {
	@Test
	void reusesTheSuccessfulIdForRepeatedToken() throws SQLException {
		TeacherTaskCreateRegistry registry = new TeacherTaskCreateRegistry();
		String token = registry.issueToken();
		AtomicInteger calls = new AtomicInteger();

		assertEquals(81, registry.createOrReuse(token, () -> {
			calls.incrementAndGet();
			return 81;
		}));
		assertEquals(81, registry.createOrReuse(token, () -> {
			calls.incrementAndGet();
			return 82;
		}));
		assertEquals(1, calls.get());
	}

	@Test
	void serializesConcurrentRequestsForOneToken() throws Exception {
		TeacherTaskCreateRegistry registry = new TeacherTaskCreateRegistry();
		String token = registry.issueToken();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Long> first = executor.submit(() -> createAfterGate(registry, token, ready, start, calls));
			Future<Long> second = executor.submit(() -> createAfterGate(registry, token, ready, start, calls));
			ready.await();
			start.countDown();

			assertEquals(82, first.get());
			assertEquals(82, second.get());
			assertEquals(1, calls.get());
		}
	}

	@Test
	void permitsRetryAfterAnUnsuccessfulCreation() throws SQLException {
		TeacherTaskCreateRegistry registry = new TeacherTaskCreateRegistry();
		String token = registry.issueToken();

		assertThrows(SQLException.class,
				() -> registry.createOrReuse(token, () -> { throw new SQLException("synthetic failure"); }));
		assertEquals(83, registry.createOrReuse(token, () -> 83));
	}

	@Test
	void rejectsUnknownTokenWithoutCallingCreator() {
		TeacherTaskCreateRegistry registry = new TeacherTaskCreateRegistry();
		AtomicInteger calls = new AtomicInteger();

		assertThrows(SecurityException.class,
				() -> registry.createOrReuse("00000000-0000-0000-0000-000000000001", calls::incrementAndGet));
		assertEquals(0, calls.get());
	}

	private static long createAfterGate(
			TeacherTaskCreateRegistry registry,
			String token,
			CountDownLatch ready,
			CountDownLatch start,
			AtomicInteger calls) throws Exception {
		ready.countDown();
		start.await();
		return registry.createOrReuse(token, () -> {
			calls.incrementAndGet();
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new SQLException("Interrupted while simulating concurrent creation.", e);
			}
			return 82;
		});
	}
}
