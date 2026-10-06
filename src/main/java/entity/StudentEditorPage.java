package entity;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class StudentEditorPage {
	private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

	private final long assignmentId;
	private final long taskId;
	private final long participationId;
	private final String title;
	private final String theme;
	private final String difficulty;
	private final String description;
	private final String inputConstraints;
	private final String creationRules;
	private final String initialCode;
	private final String code;
	private final String learningStatus;
	private final String progressStatus;
	private final String saveStatus;
	private final String lateSubmissionPolicy;
	private final LocalDateTime dueAt;
	private final LocalDateTime draftUpdatedAt;
	private final Long draftBaseSubmissionId;
	private final Long latestSubmissionId;
	private final Integer latestSubmissionRevision;
	private final String latestSubmissionStatus;
	private final String latestSubmittedCode;
	private final LocalDateTime latestSubmittedAt;
	private final List<String> features;
	private final List<EditorTestCase> testCases;
	private final List<EditorHint> hints;
	private final List<EditorCodeLog> codeLogs;
	private final LocalDateTime databaseNow;

	public StudentEditorPage(
			long assignmentId,
			long taskId,
			long participationId,
			String title,
			String theme,
			String difficulty,
			String description,
			String inputConstraints,
			String creationRules,
			String initialCode,
			String code,
			String learningStatus,
			String progressStatus,
			String saveStatus,
			String lateSubmissionPolicy,
			LocalDateTime dueAt,
			LocalDateTime draftUpdatedAt,
			Long draftBaseSubmissionId,
			Long latestSubmissionId,
			Integer latestSubmissionRevision,
			String latestSubmissionStatus,
			String latestSubmittedCode,
			LocalDateTime latestSubmittedAt,
			List<String> features,
			List<EditorTestCase> testCases,
			List<EditorHint> hints,
			List<EditorCodeLog> codeLogs) {
		this(
				assignmentId, taskId, participationId, title, theme, difficulty, description, inputConstraints,
				creationRules, initialCode, code, learningStatus, progressStatus, saveStatus, lateSubmissionPolicy,
				dueAt, draftUpdatedAt, draftBaseSubmissionId, latestSubmissionId, latestSubmissionRevision,
				latestSubmissionStatus, latestSubmittedCode, latestSubmittedAt, features, testCases, hints, codeLogs,
				LocalDateTime.now(Clock.systemUTC()));
	}

	public StudentEditorPage(
			long assignmentId,
			long taskId,
			long participationId,
			String title,
			String theme,
			String difficulty,
			String description,
			String inputConstraints,
			String creationRules,
			String initialCode,
			String code,
			String learningStatus,
			String progressStatus,
			String saveStatus,
			String lateSubmissionPolicy,
			LocalDateTime dueAt,
			LocalDateTime draftUpdatedAt,
			Long draftBaseSubmissionId,
			Long latestSubmissionId,
			Integer latestSubmissionRevision,
			String latestSubmissionStatus,
			String latestSubmittedCode,
			LocalDateTime latestSubmittedAt,
			List<String> features,
			List<EditorTestCase> testCases,
			List<EditorHint> hints,
			List<EditorCodeLog> codeLogs,
			LocalDateTime databaseNow) {
		this.assignmentId = assignmentId;
		this.taskId = taskId;
		this.participationId = participationId;
		this.title = title;
		this.theme = theme;
		this.difficulty = difficulty;
		this.description = description;
		this.inputConstraints = inputConstraints;
		this.creationRules = creationRules;
		this.initialCode = initialCode;
		this.code = code;
		this.learningStatus = learningStatus;
		this.progressStatus = progressStatus;
		this.saveStatus = saveStatus;
		this.lateSubmissionPolicy = lateSubmissionPolicy;
		this.dueAt = dueAt;
		this.draftUpdatedAt = draftUpdatedAt;
		this.draftBaseSubmissionId = draftBaseSubmissionId;
		this.latestSubmissionId = latestSubmissionId;
		this.latestSubmissionRevision = latestSubmissionRevision;
		this.latestSubmissionStatus = latestSubmissionStatus;
		this.latestSubmittedCode = latestSubmittedCode;
		this.latestSubmittedAt = latestSubmittedAt;
		this.features = List.copyOf(features);
		this.testCases = List.copyOf(testCases);
		this.hints = List.copyOf(hints);
		this.codeLogs = List.copyOf(codeLogs);
		this.databaseNow = databaseNow;
	}

	public long getAssignmentId() { return assignmentId; }
	public long getTaskId() { return taskId; }
	public long getParticipationId() { return participationId; }
	public String getTitle() { return title; }
	public String getTheme() { return theme; }
	public String getDifficulty() { return difficulty; }
	public String getDescription() { return description; }
	public String getInputConstraints() { return inputConstraints; }
	public String getCreationRules() { return creationRules; }
	public String getInitialCode() { return initialCode; }
	public String getCode() { return code; }
	public String getLearningStatus() { return learningStatus; }
	public String getProgressStatus() { return progressStatus; }
	public String getSaveStatus() { return saveStatus; }
	public String getLateSubmissionPolicy() { return lateSubmissionPolicy; }
	public LocalDateTime getDueAt() { return dueAt; }
	public LocalDateTime getDraftUpdatedAt() { return draftUpdatedAt; }
	public Long getDraftBaseSubmissionId() { return draftBaseSubmissionId; }
	public Long getLatestSubmissionId() { return latestSubmissionId; }
	public Integer getLatestSubmissionRevision() { return latestSubmissionRevision; }
	public String getLatestSubmissionStatus() { return latestSubmissionStatus; }
	public String getLatestSubmittedCode() { return latestSubmittedCode; }
	public LocalDateTime getLatestSubmittedAt() { return latestSubmittedAt; }
	public List<String> getFeatures() { return features; }
	public List<EditorTestCase> getTestCases() { return testCases; }
	public List<EditorHint> getHints() { return hints; }
	public List<EditorCodeLog> getCodeLogs() { return codeLogs; }

	public String getDraftUpdatedAtToken() {
		return draftUpdatedAt == null ? "" : draftUpdatedAt.toString();
	}

	public boolean isSubmitted() {
		return latestSubmissionId != null;
	}

	public boolean isResubmissionActive() {
		return latestSubmissionId != null
				&& latestSubmissionId.equals(draftBaseSubmissionId)
				&& "in_progress".equals(learningStatus);
	}

	public boolean isEditable() {
		return !isSubmitted() || isResubmissionActive();
	}

	public boolean isCanStartResubmission() {
		return isCanStartResubmission(databaseNow);
	}

	public boolean isCanStartResubmission(LocalDateTime now) {
		return isSubmitted()
				&& !isResubmissionActive()
				&& (dueAt == null || dueAt.isAfter(now));
	}

	public boolean isCanSubmit() {
		return isCanSubmit(databaseNow);
	}

	public boolean isCanSubmit(LocalDateTime now) {
		if (!isEditable()) {
			return false;
		}
		if (isSubmitted()) {
			return dueAt == null || dueAt.isAfter(now);
		}
		return dueAt == null || dueAt.isAfter(now) || "allow".equals(lateSubmissionPolicy);
	}

	public String getTaskStatusLabel() {
		if (isResubmissionActive()) {
			return "再提出用に編集中";
		}
		if (isSubmitted()) {
			return "提出済み";
		}
		return "in_progress".equals(learningStatus) ? "編集中" : "未着手";
	}

	public String getSaveStatusLabel() {
		return switch (saveStatus) {
			case "saved" -> "保存済み";
			case "draft" -> "下書き保存済み";
			default -> "未保存";
		};
	}

	public String getDueAtDisplay() {
		return dueAt == null ? "期限なし" : dueAt.format(DEADLINE_FORMAT);
	}

	public String getLatestSubmittedAtDisplay() {
		return latestSubmittedAt == null ? "" : latestSubmittedAt.format(DEADLINE_FORMAT);
	}
}
