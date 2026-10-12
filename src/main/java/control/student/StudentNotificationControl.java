package control.student;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import control.auth.AuthenticatedUser;
import dao.StudentExerciseDao;
import dao.StudentEditorDao;
import dao.StudentEvaluationDao;
import dao.StudentNotificationDao;
import dao.StudentSurveyDao;
import entity.StudentNotification;
import entity.StudentNotificationPage;
import entity.UserCredential.UserType;

public final class StudentNotificationControl {
	public enum OpenStatus {
		OPENED,
		NOT_FOUND,
		TARGET_UNAVAILABLE
	}

	public record OpenResult(OpenStatus status, String redirectPath) {
		static OpenResult of(OpenStatus status) {
			return new OpenResult(status, null);
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

	@FunctionalInterface
	interface TaskTargetAccessCheck {
		TargetAccess check(AuthenticatedUser user, long assignmentId) throws SQLException;
	}

	@FunctionalInterface
	interface ExerciseTargetAccessCheck {
		TargetAccess check(AuthenticatedUser user, long distributionTargetId, long exerciseId) throws SQLException;
	}

	@FunctionalInterface
	interface ExpiredTaskTargetAccessCheck {
		TargetAccess check(AuthenticatedUser user, long assignmentId) throws SQLException;
	}

	@FunctionalInterface
	interface SurveyTargetAccessCheck {
		TargetAccess check(
				AuthenticatedUser user, long assignmentId, long evaluationId, long surveyId) throws SQLException;
	}

	private final StudentNotificationDao notificationDao;
	private final TargetAccessCheck targetAccess;
	private final TaskTargetAccessCheck taskTargetAccess;
	private final ExerciseTargetAccessCheck exerciseTargetAccess;
	private final ExpiredTaskTargetAccessCheck expiredTaskTargetAccess;
	private final SurveyTargetAccessCheck surveyTargetAccess;

	public StudentNotificationControl() {
		this(new StudentNotificationDao(), defaultTargetAccess(new StudentControl(), new StudentEvaluationDao()),
				defaultTaskTargetAccess(new StudentControl(), new StudentEditorDao()),
				defaultExerciseTargetAccess(new StudentControl(), new StudentExerciseDao()),
				defaultExpiredTaskTargetAccess(new StudentControl(), new StudentEditorDao()),
				defaultSurveyTargetAccess(new StudentControl(), new StudentSurveyDao()));
	}

	StudentNotificationControl(
			StudentNotificationDao notificationDao,
			TargetAccessCheck targetAccess,
			TaskTargetAccessCheck taskTargetAccess) {
		this(notificationDao, targetAccess, taskTargetAccess,
				defaultExerciseTargetAccess(new StudentControl(), new StudentExerciseDao()),
				defaultExpiredTaskTargetAccess(new StudentControl(), new StudentEditorDao()));
	}

	StudentNotificationControl(
			StudentNotificationDao notificationDao,
			TargetAccessCheck targetAccess,
			TaskTargetAccessCheck taskTargetAccess,
			ExerciseTargetAccessCheck exerciseTargetAccess) {
		this(notificationDao, targetAccess, taskTargetAccess, exerciseTargetAccess,
				defaultExpiredTaskTargetAccess(new StudentControl(), new StudentEditorDao()),
				defaultSurveyTargetAccess(new StudentControl(), new StudentSurveyDao()));
	}

	StudentNotificationControl(
			StudentNotificationDao notificationDao,
			TargetAccessCheck targetAccess,
			TaskTargetAccessCheck taskTargetAccess,
			ExerciseTargetAccessCheck exerciseTargetAccess,
			ExpiredTaskTargetAccessCheck expiredTaskTargetAccess) {
		this(notificationDao, targetAccess, taskTargetAccess, exerciseTargetAccess, expiredTaskTargetAccess,
				defaultSurveyTargetAccess(new StudentControl(), new StudentSurveyDao()));
	}

	StudentNotificationControl(
			StudentNotificationDao notificationDao,
			TargetAccessCheck targetAccess,
			TaskTargetAccessCheck taskTargetAccess,
			ExerciseTargetAccessCheck exerciseTargetAccess,
			ExpiredTaskTargetAccessCheck expiredTaskTargetAccess,
			SurveyTargetAccessCheck surveyTargetAccess) {
		this.notificationDao = notificationDao;
		this.targetAccess = targetAccess;
		this.taskTargetAccess = taskTargetAccess;
		this.exerciseTargetAccess = exerciseTargetAccess;
		this.expiredTaskTargetAccess = expiredTaskTargetAccess;
		this.surveyTargetAccess = surveyTargetAccess;
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
	 * Marks the notification read only after its target (evaluation result or published task) is
	 * confirmed accessible, then returns the path the caller should redirect to.
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
		String redirectPath = resolveRedirectPath(user, notification);
		if (redirectPath == null) {
			return OpenResult.of(OpenStatus.TARGET_UNAVAILABLE);
		}
		if (!notificationDao.markRead(user.userId(), notificationId)
				&& notificationDao.findRetained(user.userId(), notificationId).isEmpty()) {
			return OpenResult.of(OpenStatus.NOT_FOUND);
		}
		return new OpenResult(OpenStatus.OPENED, redirectPath);
	}

	private String resolveRedirectPath(AuthenticatedUser user, StudentNotification notification) throws SQLException {
		String type = notification.getNotificationType();
		if (StudentNotification.TYPE_SURVEY_AVAILABLE.equals(type)
				|| StudentNotification.TYPE_SURVEY_IN_PROGRESS.equals(type)) {
			if (notification.getAssignmentId() == null || notification.getEvaluationId() == null
					|| notification.getSurveyId() == null) {
				return null;
			}
			long assignmentId = notification.getAssignmentId();
			long evaluationId = notification.getEvaluationId();
			long surveyId = notification.getSurveyId();
			TargetAccess access = surveyTargetAccess.check(user, assignmentId, evaluationId, surveyId);
			if (access == TargetAccess.ACCOUNT_INVALID) {
				throw new SecurityException("A valid student account is required.");
			}
			return access == TargetAccess.READABLE
					? "/student/survey?assignmentId=" + assignmentId
							+ "&evaluationId=" + evaluationId + "&surveyId=" + surveyId
					: null;
		}
		if (StudentNotification.TYPE_EXERCISE_DISTRIBUTED.equals(type)) {
			if (notification.getDistributionTargetId() == null || notification.getStudentExerciseId() == null) {
				return null;
			}
			TargetAccess access = exerciseTargetAccess.check(user,
					notification.getDistributionTargetId(), notification.getStudentExerciseId());
			if (access == TargetAccess.ACCOUNT_INVALID) {
				throw new SecurityException("A valid student account is required.");
			}
			return access == TargetAccess.READABLE
					? "/student/exercise?exerciseId=" + notification.getStudentExerciseId()
					: null;
		}
		if (StudentNotification.TYPE_TASK_PUBLISHED.equals(type) || StudentNotification.TYPE_DUE_SOON.equals(type)
				|| StudentNotification.TYPE_DEADLINE_EXTENDED.equals(type)) {
			if (notification.getAssignmentId() == null) {
				return null;
			}
			long assignmentId = notification.getAssignmentId();
			TargetAccess access = taskTargetAccess.check(user, assignmentId);
			if (access == TargetAccess.ACCOUNT_INVALID) {
				throw new SecurityException("A valid student account is required.");
			}
			return access == TargetAccess.READABLE ? "/student/editor?assignmentId=" + assignmentId : null;
		}
		if (StudentNotification.TYPE_ASSIGNMENT_EXPIRED.equals(type)) {
			if (notification.getAssignmentId() == null) {
				return null;
			}
			long assignmentId = notification.getAssignmentId();
			TargetAccess access = taskTargetAccess.check(user, assignmentId);
			if (access == TargetAccess.ACCOUNT_INVALID) {
				throw new SecurityException("A valid student account is required.");
			}
			if (access == TargetAccess.READABLE) {
				return "/student/editor?assignmentId=" + assignmentId;
			}
			TargetAccess fallback = expiredTaskTargetAccess.check(user, assignmentId);
			if (fallback == TargetAccess.ACCOUNT_INVALID) {
				throw new SecurityException("A valid student account is required.");
			}
			return fallback == TargetAccess.READABLE ? "/student/home#taskListSection" : null;
		}
		if ((StudentNotification.TYPE_EVALUATION_COMPLETED.equals(type)
				|| StudentNotification.TYPE_EVALUATION_FAILED.equals(type))
				&& notification.getAssignmentId() != null && notification.getSubmissionId() != null) {
			long assignmentId = notification.getAssignmentId();
			long submissionId = notification.getSubmissionId();
			TargetAccess access = targetAccess.check(user, assignmentId, submissionId);
			if (access == TargetAccess.ACCOUNT_INVALID) {
				throw new SecurityException("A valid student account is required.");
			}
			return access == TargetAccess.READABLE
					? "/student/evaluation?assignmentId=" + assignmentId + "&submissionId=" + submissionId
					: null;
		}
		return null;
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

	static TaskTargetAccessCheck defaultTaskTargetAccess(StudentControl students, StudentEditorDao editorDao) {
		return (user, assignmentId) -> {
			if (students.loadAccount(user).isEmpty()) {
				return TargetAccess.ACCOUNT_INVALID;
			}
			if (students.loadAffiliations(user).isEmpty()) {
				return TargetAccess.UNAVAILABLE;
			}
			return editorDao.findEditorPage(user.userId(), assignmentId).isPresent() ? TargetAccess.READABLE
					: TargetAccess.UNAVAILABLE;
		};
	}

	static ExpiredTaskTargetAccessCheck defaultExpiredTaskTargetAccess(
			StudentControl students, StudentEditorDao editorDao) {
		return (user, assignmentId) -> {
			if (students.loadAccount(user).isEmpty()) {
				return TargetAccess.ACCOUNT_INVALID;
			}
			if (students.loadAffiliations(user).isEmpty()) {
				return TargetAccess.UNAVAILABLE;
			}
			return editorDao.isExpiredDeniedAssignmentForStudent(user.userId(), assignmentId)
					? TargetAccess.READABLE : TargetAccess.UNAVAILABLE;
		};
	}

	static ExerciseTargetAccessCheck defaultExerciseTargetAccess(
			StudentControl students, StudentExerciseDao exercises) {
		return (user, targetId, exerciseId) -> {
			if (students.loadAccount(user).isEmpty()) {
				return TargetAccess.ACCOUNT_INVALID;
			}
			return exercises.isAccessibleDistributedExercise(user.userId(), targetId, exerciseId)
					? TargetAccess.READABLE : TargetAccess.UNAVAILABLE;
		};
	}

	static SurveyTargetAccessCheck defaultSurveyTargetAccess(
			StudentControl students, StudentSurveyDao surveys) {
		return (user, assignmentId, evaluationId, surveyId) -> {
			if (students.loadAccount(user).isEmpty()) {
				return TargetAccess.ACCOUNT_INVALID;
			}
			if (students.loadAffiliations(user).isEmpty()) {
				return TargetAccess.UNAVAILABLE;
			}
			return surveys.isAccessibleForNotification(user.userId(), assignmentId, evaluationId, surveyId)
					? TargetAccess.READABLE : TargetAccess.UNAVAILABLE;
		};
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) {
			throw new SecurityException("A student account is required.");
		}
	}
}
