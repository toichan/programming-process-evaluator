package entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lib.time.DateTimeContract;

public record ReevaluationJobHistory(
		long jobId,
		long promptVersionId,
		String promptVersion,
		String status,
		int targetCount,
		int completedCount,
		int failedCount,
		BigDecimal progressPercent,
		String requestedByLoginId,
		LocalDateTime requestedAt,
		LocalDateTime startedAt,
		LocalDateTime completedAt) {
	public long getJobId() { return jobId; }
	public long getPromptVersionId() { return promptVersionId; }
	public String getPromptVersion() { return promptVersion; }
	public String getStatus() { return status; }
	public int getTargetCount() { return targetCount; }
	public int getCompletedCount() { return completedCount; }
	public int getFailedCount() { return failedCount; }
	public BigDecimal getProgressPercent() { return progressPercent; }
	public String getRequestedByLoginId() { return requestedByLoginId; }
	public LocalDateTime getRequestedAt() { return requestedAt; }
	public String getRequestedAtDisplay() {
		return requestedAt == null ? null : DateTimeContract.toUserDisplay(requestedAt).toString();
	}
	public LocalDateTime getStartedAt() { return startedAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
}
