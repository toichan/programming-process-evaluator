package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class StudentCodeLogPage {
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private final long assignmentId;
	private final long submissionId;
	private final int revisionNumber;
	private final String taskTitle;
	private final String difficulty;
	private final List<LogEntry> entries;
	private final StudentEvaluationPage.EvaluationResult evaluation;

	public StudentCodeLogPage(
			long assignmentId,
			long submissionId,
			int revisionNumber,
			String taskTitle,
			String difficulty,
			List<LogEntry> entries,
			StudentEvaluationPage.EvaluationResult evaluation) {
		this.assignmentId = assignmentId;
		this.submissionId = submissionId;
		this.revisionNumber = revisionNumber;
		this.taskTitle = taskTitle;
		this.difficulty = difficulty;
		this.entries = List.copyOf(entries);
		this.evaluation = evaluation;
	}

	public long getAssignmentId() { return assignmentId; }
	public long getSubmissionId() { return submissionId; }
	public int getRevisionNumber() { return revisionNumber; }
	public String getTaskTitle() { return taskTitle; }
	public String getDifficulty() { return difficulty; }
	public String getDifficultyLabel() {
		return switch (difficulty == null ? "" : difficulty) {
			case "beginner" -> "初級";
			case "intermediate" -> "中級";
			case "advanced" -> "上級";
			default -> "難易度未設定";
		};
	}
	public List<LogEntry> getEntries() { return entries; }
	public StudentEvaluationPage.EvaluationResult getEvaluation() { return evaluation; }
	public long getSaveCount() {
		return entries.stream()
				.filter(entry -> "periodic_snapshot".equals(entry.eventType)
						|| "manual_save".equals(entry.eventType))
				.count();
	}
	public long getExecutionCount() {
		return entries.stream().filter(entry -> entry.executionStatus != null).count();
	}

	public static final class LogEntry {
		private final long logId;
		private final String eventType;
		private final LocalDateTime observedAt;
		private final Long executionId;
		private final String snapshot;
		private final String executionStatus;
		private final String standardInput;
		private final String standardOutput;
		private final String standardError;

		public LogEntry(
				long logId,
				String eventType,
				LocalDateTime observedAt,
				Long executionId,
				String snapshot,
				String executionStatus,
				String standardInput,
				String standardOutput,
				String standardError) {
			this.logId = logId;
			this.eventType = eventType;
			this.observedAt = observedAt;
			this.executionId = executionId;
			this.snapshot = snapshot;
			this.executionStatus = executionStatus;
			this.standardInput = standardInput;
			this.standardOutput = standardOutput;
			this.standardError = standardError;
		}

		public long getLogId() { return logId; }
		public String getEventType() { return eventType; }
		public String getEventLabel() {
			return switch (eventType) {
				case "periodic_snapshot" -> "自動保存";
				case "manual_save" -> "手動保存";
				case "submission" -> "提出";
				case "edit" -> "編集";
				default -> "コードログ";
			};
		}
		public LocalDateTime getObservedAt() { return observedAt; }
		public String getObservedAtDisplay() { return observedAt.format(DATE_TIME_FORMAT); }
		public Long getExecutionId() { return executionId; }
		public String getSnapshot() { return snapshot; }
		public String getExecutionStatus() { return executionStatus; }
		public String getStandardInput() { return standardInput; }
		public String getStandardOutput() { return standardOutput; }
		public String getStandardError() { return standardError; }
	}
}
