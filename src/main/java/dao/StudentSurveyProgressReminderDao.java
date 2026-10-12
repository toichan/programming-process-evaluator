package dao;

import java.sql.Connection;
import java.sql.SQLException;

import lib.mysql.Client;

public final class StudentSurveyProgressReminderDao {
	public int processSurveyProgressReminders() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				int inserted = StudentNotificationDao.insertSurveyInProgressReminders(connection);
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
