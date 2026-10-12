package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class StudentNotification {
	public static final String TYPE_EVALUATION_COMPLETED = "evaluation_completed";
	public static final String TYPE_EVALUATION_FAILED = "evaluation_failed";
	public static final String TYPE_TASK_PUBLISHED = "task_published";
	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

	private final long notificationId;
	private final String notificationType;
	private final String title;
	private final String message;
	private final LocalDateTime createdAt;
	private final boolean read;
	private final Long assignmentId;
	private final Long submissionId;

	public StudentNotification(
			long notificationId,
			String notificationType,
			String title,
			String message,
			LocalDateTime createdAt,
			boolean read,
			Long assignmentId,
			Long submissionId) {
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
		return createdAt.format(LABEL);
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
}
