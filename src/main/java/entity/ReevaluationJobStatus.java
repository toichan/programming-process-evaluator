package entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReevaluationJobStatus(
		long jobId,
		long taskId,
		long promptVersionId,
		String status,
		int targetCount,
		int completedCount,
		int failedCount,
		BigDecimal progressPercent,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		List<ReevaluationJobTargetResult> targetResults) {
	public ReevaluationJobStatus {
		targetResults = List.copyOf(targetResults);
	}
	public long getJobId() { return jobId; }
	public long getTaskId() { return taskId; }
	public long getPromptVersionId() { return promptVersionId; }
	public String getStatus() { return status; }
	public int getTargetCount() { return targetCount; }
	public int getCompletedCount() { return completedCount; }
	public int getFailedCount() { return failedCount; }
	public BigDecimal getProgressPercent() { return progressPercent; }
	public LocalDateTime getStartedAt() { return startedAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
	public List<ReevaluationJobTargetResult> getTargetResults() { return targetResults; }
}
