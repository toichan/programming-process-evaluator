package control.teacher;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TeacherDistributionWorker implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(TeacherDistributionWorker.class.getName());
	private static final long POLL_INTERVAL_MILLIS = 30_000;

	private final WorkRepository repository;
	private volatile boolean running;
	private Thread thread;

	public TeacherDistributionWorker() {
		this(new TeacherDistributionControl()::processDueTargets);
	}

	TeacherDistributionWorker(WorkRepository repository) {
		this.repository = Objects.requireNonNull(repository);
	}

	public synchronized void start() {
		if (running) return;
		running = true;
		thread = new Thread(this, "teacher-exercise-distribution-worker");
		thread.setDaemon(true);
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(POLL_INTERVAL_MILLIS + 5_000);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
	}

	@Override
	public void run() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				repository.processDueTargets();
				Thread.sleep(POLL_INTERVAL_MILLIS);
			} catch (SQLException | RuntimeException failure) {
				LOGGER.log(Level.SEVERE, "Teacher exercise distribution schedule processing failed.", failure);
				try {
					Thread.sleep(POLL_INTERVAL_MILLIS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@FunctionalInterface
	interface WorkRepository {
		int processDueTargets() throws SQLException;
	}
}
