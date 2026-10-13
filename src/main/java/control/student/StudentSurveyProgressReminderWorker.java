package control.student;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import dao.StudentSurveyProgressReminderDao;

public final class StudentSurveyProgressReminderWorker implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(StudentSurveyProgressReminderWorker.class.getName());
	private static final long POLL_INTERVAL_MILLIS = 60_000;

	private final WorkRepository repository;
	private volatile boolean running;
	private Thread thread;

	public StudentSurveyProgressReminderWorker() {
		this(new StudentSurveyProgressReminderDao()::processSurveyProgressReminders);
	}

	StudentSurveyProgressReminderWorker(WorkRepository repository) {
		this.repository = Objects.requireNonNull(repository);
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "student-survey-progress-reminder-worker");
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
				repository.processSurveyProgressReminders();
				Thread.sleep(POLL_INTERVAL_MILLIS);
			} catch (SQLException | RuntimeException failure) {
				LOGGER.log(Level.SEVERE, "Student survey progress reminder processing failed.", failure);
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
		int processSurveyProgressReminders() throws SQLException;
	}
}
