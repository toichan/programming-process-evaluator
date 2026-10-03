package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class EditorCodeLog {
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private final String eventType;
	private final String executionStatus;
	private final LocalDateTime observedAt;
	private final String snapshot;

	public EditorCodeLog(String eventType, String executionStatus, LocalDateTime observedAt, String snapshot) {
		this.eventType = eventType;
		this.executionStatus = executionStatus;
		this.observedAt = observedAt;
		this.snapshot = snapshot;
	}

	public String getEventType() {
		return eventType;
	}

	public String getTitle() {
		return switch (eventType) {
			case "manual_save" -> "手動保存";
			case "periodic_snapshot" -> "自動保存";
			case "submission" -> "提出";
			default -> "コード実行";
		};
	}

	public String getExecutionStatus() {
		return executionStatus;
	}

	public String getExecutionStatusDisplay() {
		return switch (executionStatus == null ? "" : executionStatus) {
			case "succeeded" -> "成功";
			case "failed" -> "失敗";
			case "timed_out" -> "タイムアウト";
			case "not_run" -> "未実行";
			default -> "";
		};
	}

	public LocalDateTime getObservedAt() {
		return observedAt;
	}

	public String getObservedAtDisplay() {
		return observedAt.format(TIME_FORMAT);
	}

	public String getSnapshot() {
		return snapshot;
	}
}
