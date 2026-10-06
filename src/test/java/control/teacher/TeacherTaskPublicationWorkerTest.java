package control.teacher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TeacherTaskPublicationWorkerTest {
	@Test
	void processesDueAssignmentsImmediatelyAndStopsOnInterruption() throws Exception {
		CountDownLatch processed = new CountDownLatch(1);
		AtomicInteger processCount = new AtomicInteger();
		TeacherTaskPublicationWorker worker = new TeacherTaskPublicationWorker(() -> {
			processCount.incrementAndGet();
			processed.countDown();
			return 0;
		});

		worker.start();
		try {
			assertTrue(processed.await(3, TimeUnit.SECONDS));
			assertTrue(processCount.get() >= 1);
		} finally {
			worker.stop();
		}
	}
}
