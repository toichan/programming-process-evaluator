package dao;

import java.sql.Connection;
import java.sql.SQLException;

import lib.mysql.Client;

/**
 * Scans published class assignments whose deadline is within 24 hours and records a one-time
 * due-soon reminder notification for each active, not-yet-submitted student. Invoked by
 * {@link control.student.StudentDueSoonReminderWorker} on its polling schedule.
 */
public final class StudentDueSoonReminderDao {
	public int processDueSoonReminders() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				int inserted = StudentNotificationDao.insertDueSoonReminders(connection);
				connection.commit();
				return inserted;
			} catch (SQLException | RuntimeException failure) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
				throw failure;
			}
		}
	}
}
