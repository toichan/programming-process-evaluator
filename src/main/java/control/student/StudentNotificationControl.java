package control.student;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import control.auth.AuthenticatedUser;
import dao.StudentEvaluationDao;
import dao.StudentNotificationDao;
import entity.StudentNotification;
import entity.StudentNotificationPage;
import entity.UserCredential.UserType;

public final class StudentNotificationControl {
	public enum OpenStatus {
		OPENED,
		NOT_FOUND,
		TARGET_UNAVAILABLE
	}

	public record OpenResult(OpenStatus status, long assignmentId, long submissionId) {
		static OpenResult of(OpenStatus status) {
			return new OpenResult(status, 0, 0);
		}
	}

	enum TargetAccess {
		READABLE,
		ACCOUNT_INVALID,
		UNAVAILABLE
	}

	@FunctionalInterface
	interface TargetAccessCheck {
		TargetAccess check(AuthenticatedUser user, long assignmentId, long submissionId) throws SQLException;
	}

	private final StudentNotificationDao notificationDao;
	private final TargetAccessCheck targetAccess;

	public StudentNotificationControl() {
		this(new StudentNotificationDao(), defaultTargetAccess(new StudentControl(), new StudentEvaluationDao()));
	}

	StudentNotificationControl(StudentNotificationDao notificationDao, TargetAccessCheck targetAccess) {
		this.notificationDao = notificationDao;
		this.targetAccess = targetAccess;
	}

	public StudentNotificationPage loadPage(AuthenticatedUser user, int pageNumber) throws SQLException {
		requireStudent(user);
		if (pageNumber < 1) {
			throw new IllegalArgumentException("The page number must be positive.");
		}
		return notificationDao.findPage(user.userId(), pageNumber);
	}

	public List<StudentNotification> loadRecent(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		return notificationDao.findRecent(user.userId());
	}

	public int countUnread(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		return notificationDao.countUnread(user.userId());
	}

	/**
	 * Marks the notification read only after its target evaluation is confirmed readable.
	 *
	 * @throws SecurityException when the user is not a valid active student
	 */
	public OpenResult open(AuthenticatedUser user, long notificationId) throws SQLException {
		requireStudent(user);
		if (notificationId <= 0) {
			return OpenResult.of(OpenStatus.NOT_FOUND);
		}
		Optional<StudentNotification> found = notificationDao.findRetained(user.userId(), notificationId);
		if (found.isEmpty()) {
			return OpenResult.of(OpenStatus.NOT_FOUND);
		}
		StudentNotification notification = found.get();
		if (!StudentNotification.TYPE_EVALUATION_COMPLETED.equals(notification.getNotificationType())
				|| notification.getAssignmentId() == null || notification.getSubmissionId() == null) {
			return OpenResult.of(OpenStatus.TARGET_UNAVAILABLE);
		}
		long assignmentId = notification.getAssignmentId();
		long submissionId = notification.getSubmissionId();
		TargetAccess access = targetAccess.check(user, assignmentId, submissionId);
		if (access == TargetAccess.ACCOUNT_INVALID) {
			throw new SecurityException("A valid student account is required.");
		}
		if (access != TargetAccess.READABLE) {
			return OpenResult.of(OpenStatus.TARGET_UNAVAILABLE);
		}
		notificationDao.markRead(user.userId(), notificationId);
		return new OpenResult(OpenStatus.OPENED, assignmentId, submissionId);
	}

	public int markAllRead(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		return notificationDao.markAllRead(user.userId());
	}

	static TargetAccessCheck defaultTargetAccess(StudentControl students, StudentEvaluationDao evaluations) {
		return (user, assignmentId, submissionId) -> {
			if (students.loadAccount(user).isEmpty()) {
				return TargetAccess.ACCOUNT_INVALID;
			}
			if (students.loadAffiliations(user).isEmpty()) {
				return TargetAccess.UNAVAILABLE;
			}
			return evaluations.findEvaluationPage(user.userId(), assignmentId, submissionId).isPresent()
					? TargetAccess.READABLE
					: TargetAccess.UNAVAILABLE;
		};
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) {
			throw new SecurityException("A student account is required.");
		}
	}
}
