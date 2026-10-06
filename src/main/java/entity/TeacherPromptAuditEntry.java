package entity;

import java.time.LocalDateTime;

public record TeacherPromptAuditEntry(
		long auditLogId,
		String actorLoginId,
		String actionType,
		String resultStatus,
		String detail,
		LocalDateTime occurredAt) {
	public long getAuditLogId() { return auditLogId; }
	public String getActorLoginId() { return actorLoginId; }
	public String getActionType() { return actionType; }
	public String getResultStatus() { return resultStatus; }
	public String getDetail() { return detail; }
	public LocalDateTime getOccurredAt() { return occurredAt; }
}
