package control.student;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import dao.StudentDueSoonReminderDao;

public final class StudentDueSoonReminderWorker implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(StudentDueSoonReminderWorker.class.getName());
	private static final long POLL_INTERVAL_MILLIS = 60_000;

	private final WorkRepository repository;
	private volatile boolean running;
	private Thread thread;

	public StudentDueSoonReminderWorker() {
		this(new StudentDueSoonReminderDao()::processDueSoonReminders);
	}

	StudentDueSoonReminderWorker(WorkRepository repository) {
		this.repository = Objects.requireNonNull(repository);
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "student-due-soon-reminder-worker");
		thread.setDaemon(true);
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(65_000);
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
				repository.processDueSoonReminders();
				Thread.sleep(POLL_INTERVAL_MILLIS);
			} catch (SQLException | RuntimeException failure) {
				LOGGER.log(Level.SEVERE, "Student due-soon reminder processing failed.", failure);
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
		int processDueSoonReminders() throws SQLException;
	}
}
