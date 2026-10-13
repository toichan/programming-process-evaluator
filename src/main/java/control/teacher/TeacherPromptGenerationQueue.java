package control.teacher;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TeacherPromptGenerationQueue {
	public static final String SERVLET_CONTEXT_ATTRIBUTE =
			TeacherPromptGenerationQueue.class.getName() + ".instance";
	private static final Logger LOGGER = Logger.getLogger(TeacherPromptGenerationQueue.class.getName());
	private static final int MAX_QUEUED_GENERATIONS = 20;
	private static final long GENERATION_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(12);

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
			1,
			1,
			0,
			TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(MAX_QUEUED_GENERATIONS),
			task -> {
				Thread thread = new Thread(task, "teacher-prompt-generation-worker");
				thread.setDaemon(true);
				return thread;
			},
			new ThreadPoolExecutor.AbortPolicy());

	public TeacherPromptGenerationQueue() {
		executor.prestartCoreThread();
	}

	public boolean enqueue(Runnable generation) {
		Objects.requireNonNull(generation);
		try {
			executor.execute(() -> {
				try {
					generation.run();
				} catch (RuntimeException failure) {
					LOGGER.log(Level.SEVERE, "Queued prompt generation failed.", failure);
				}
			});
			return true;
		} catch (java.util.concurrent.RejectedExecutionException rejected) {
			return false;
		}
	}

	public void stop() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(GENERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
				LOGGER.warning("Prompt generation queue did not drain before shutdown.");
				executor.shutdownNow();
			}
		} catch (InterruptedException interrupted) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
