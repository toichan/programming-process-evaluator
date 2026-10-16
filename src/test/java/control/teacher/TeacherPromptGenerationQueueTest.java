package control.teacher;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TeacherPromptGenerationQueueTest {
	@Test
	void executesGenerationOnBackgroundWorker() throws InterruptedException {
		TeacherPromptGenerationQueue queue = new TeacherPromptGenerationQueue();
		CountDownLatch completed = new CountDownLatch(1);
		AtomicReference<Thread> workerThread = new AtomicReference<>();
		try {
			assertTrue(queue.enqueue(() -> {
				workerThread.set(Thread.currentThread());
				completed.countDown();
			}));
			assertTrue(completed.await(2, TimeUnit.SECONDS));
			assertNotEquals(Thread.currentThread(), workerThread.get());
		} finally {
			queue.stop();
		}
	}
}
