package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import control.auth.AuthenticatedUser;
import dao.TeacherPermissionDao;
import entity.TeacherNavigationSummary;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherNavigationControl {
	private static final Logger LOGGER = Logger.getLogger(TeacherNavigationControl.class.getName());
	private final TeacherPermissionDao permissionDao;
	private final ConnectionFactory connectionFactory;

	public TeacherNavigationControl() {
		this(new TeacherPermissionDao(), Client::createConnection);
	}

	TeacherNavigationControl(TeacherPermissionDao permissionDao, ConnectionFactory connectionFactory) {
		this.permissionDao = Objects.requireNonNull(permissionDao);
		this.connectionFactory = Objects.requireNonNull(connectionFactory);
	}

	public TeacherNavigationSummary load(AuthenticatedUser user) throws SQLException {
		if (user == null || user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher navigation is only available to teachers.");
		}
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				TeacherNavigationSummary summary = permissionDao.findNavigationSummary(connection, user.userId());
				connection.commit();
				return summary;
			} catch (SQLException | RuntimeException failure) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
					LOGGER.log(Level.SEVERE, "Failed to rollback teacher navigation lookup.", rollbackFailure);
				}
				throw failure;
			}
		}
	}

	@FunctionalInterface
	interface ConnectionFactory {
		Connection open() throws SQLException;
	}
}
