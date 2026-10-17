package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import lib.time.DateTimeContract;

public final class StudentNotification {
	public static final String TYPE_EVALUATION_COMPLETED = "evaluation_completed";
	public static final String TYPE_EVALUATION_FAILED = "evaluation_failed";
	public static final String TYPE_TASK_PUBLISHED = "task_published";
	public static final String TYPE_DUE_SOON = "due_soon";
	public static final String TYPE_ASSIGNMENT_EXPIRED = "assignment_expired";
	public static final String TYPE_DEADLINE_EXTENDED = "deadline_extended";
	public static final String TYPE_EXERCISE_DISTRIBUTED = "exercise_distributed";
	public static final String TYPE_SURVEY_AVAILABLE = "survey_available";
	public static final String TYPE_SURVEY_IN_PROGRESS = "survey_in_progress";
	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

	private final long notificationId;
	private final String notificationType;
	private final String title;
	private final String message;
	private final LocalDateTime createdAt;
	private final boolean read;
	private final Long assignmentId;
	private final Long submissionId;
	private final Long distributionTargetId;
	private final Long studentExerciseId;
	private final Long evaluationId;
	private final Long surveyId;

	public StudentNotification(
			long notificationId,
			String notificationType,
			String title,
			String message,
			LocalDateTime createdAt,
			boolean read,
			Long assignmentId,
			Long submissionId) {
		this(notificationId, notificationType, title, message, createdAt, read,
				assignmentId, submissionId, null, null, null, null);
	}

	public StudentNotification(
			long notificationId,
			String notificationType,
			String title,
			String message,
			LocalDateTime createdAt,
			boolean read,
			Long assignmentId,
			Long submissionId,
			Long distributionTargetId,
			Long studentExerciseId) {
		this(notificationId, notificationType, title, message, createdAt, read,
				assignmentId, submissionId, distributionTargetId, studentExerciseId, null, null);
	}

	public StudentNotification(
			long notificationId,
			String notificationType,
			String title,
			String message,
			LocalDateTime createdAt,
			boolean read,
			Long assignmentId,
			Long submissionId,
			Long distributionTargetId,
			Long studentExerciseId,
			Long evaluationId,
			Long surveyId) {
		if (notificationId <= 0) {
			throw new IllegalArgumentException("The notification ID must be positive.");
		}
		this.notificationId = notificationId;
		this.notificationType = Objects.requireNonNull(notificationType);
		this.title = Objects.requireNonNull(title);
		this.message = Objects.requireNonNull(message);
		this.createdAt = Objects.requireNonNull(createdAt);
		this.read = read;
		this.assignmentId = assignmentId;
		this.submissionId = submissionId;
		this.distributionTargetId = distributionTargetId;
		this.studentExerciseId = studentExerciseId;
		this.evaluationId = evaluationId;
		this.surveyId = surveyId;
	}

	public long getNotificationId() {
		return notificationId;
	}

	public String getNotificationType() {
		return notificationType;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public String getCreatedAtLabel() {
		return DateTimeContract.toUserDisplay(createdAt).format(LABEL);
	}

	public boolean isRead() {
		return read;
	}

	public Long getAssignmentId() {
		return assignmentId;
	}

	public Long getSubmissionId() {
		return submissionId;
	}

	public Long getDistributionTargetId() {
		return distributionTargetId;
	}

	public Long getStudentExerciseId() {
		return studentExerciseId;
	}

	public Long getEvaluationId() {
		return evaluationId;
	}

	public Long getSurveyId() {
		return surveyId;
	}
}
