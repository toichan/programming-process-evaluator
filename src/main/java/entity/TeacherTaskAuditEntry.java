package entity;

import java.time.LocalDateTime;
import java.util.Objects;

public record TeacherTaskAuditEntry(
		long auditLogId,
		long actorUserId,
		String actorLoginId,
		String actionType,
		String resultStatus,
		String errorCode,
		String errorMessage,
		String detail,
		LocalDateTime occurredAt) {

	public TeacherTaskAuditEntry {
		Objects.requireNonNull(actorLoginId);
		Objects.requireNonNull(actionType);
		Objects.requireNonNull(resultStatus);
		Objects.requireNonNull(occurredAt);
	}

	public long getAuditLogId() {
		return auditLogId;
	}

	public long getActorUserId() {
		return actorUserId;
	}

	public String getActorLoginId() {
		return actorLoginId;
	}

	public String getActionType() {
		return actionType;
	}

	public String getResultStatus() {
		return resultStatus;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getDetail() {
		return detail;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}
}
