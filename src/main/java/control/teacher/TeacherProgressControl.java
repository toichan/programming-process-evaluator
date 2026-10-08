package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import control.auth.AuthenticatedUser;
import dao.TeacherProgressDao;
import entity.TeacherProgressActivity;
import entity.TeacherProgressCode;
import entity.TeacherProgressDetail;
import entity.TeacherProgressRow;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherProgressControl {
	private static final Logger LOGGER = Logger.getLogger(TeacherProgressControl.class.getName());
	private final TeacherProgressDao progressDao;
	private final ConnectionFactory connectionFactory;

	public TeacherProgressControl() {
		this(new TeacherProgressDao(), Client::createConnection);
	}

	TeacherProgressControl(TeacherProgressDao progressDao, ConnectionFactory connectionFactory) {
		this.progressDao = Objects.requireNonNull(progressDao);
		this.connectionFactory = Objects.requireNonNull(connectionFactory);
	}

	public List<TeacherProgressRow> loadRows(AuthenticatedUser user) throws SQLException {
		requireTeacher(user);
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				List<TeacherProgressRow> rows = progressDao.findRows(connection, user.userId());
				connection.commit();
				return rows;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public TeacherProgressDetail loadDetail(
			AuthenticatedUser user, long assignmentId, long studentUserId) throws SQLException {
		requireTeacher(user);
		requirePositiveId(assignmentId, "課題割当");
		requirePositiveId(studentUserId, "生徒");
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				List<TeacherProgressRow> rows = progressDao.findRows(connection, user.userId());
				TeacherProgressRow row = rows.stream()
						.filter(candidate -> candidate.assignmentId() == assignmentId
								&& candidate.studentUserId() == studentUserId)
						.findFirst()
						.orElseThrow(ProgressRecordNotFoundException::new);
				List<TeacherProgressActivity> activities = row.participationId() == null
						? List.of()
						: progressDao.findActivities(connection, row.participationId());
				TeacherProgressCode latestCode = row.participationId() == null
						? null : progressDao.findLatestCode(connection, row.participationId());
				connection.commit();
				return new TeacherProgressDetail(row, activities, latestCode);
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication is required.");
		}
	}

	private static void requirePositiveId(long id, String label) {
		if (id < 1) {
			throw new IllegalArgumentException(label + " ID must be positive.");
		}
	}

	private static void rollback(Connection connection, Exception failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
			LOGGER.log(Level.SEVERE, "Failed to rollback teacher progress read.", rollbackFailure);
		}
	}

	@FunctionalInterface
	interface ConnectionFactory {
		Connection open() throws SQLException;
	}
}
