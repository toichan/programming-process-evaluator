package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class StudentTaskSummary {
	private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

	private final long taskClassAssignmentId;
	private final long taskId;
	private final String title;
	private final String theme;
	private final String difficulty;
	private final String description;
	private final LocalDateTime dueAt;
	private final String learningStatus;
	private final String progressStatus;
	private final String saveStatus;
	private final String evaluationStatus;
	private final String surveyStatus;
	private final Long activeSurveyId;
	private final Long latestCompletedEvaluationId;
	private final Long secondsUntilDue;
	private final Long latestSubmissionId;

	public StudentTaskSummary(
			long taskClassAssignmentId,
			long taskId,
			String title,
			String theme,
			String difficulty,
			String description,
			LocalDateTime dueAt,
			String learningStatus,
			String progressStatus,
			String saveStatus,
			String evaluationStatus,
			String surveyStatus,
			Long activeSurveyId,
			Long latestCompletedEvaluationId,
			ConsentStatus consentStatus,
			Long secondsUntilDue,
			Long latestSubmissionId) {
		this.taskClassAssignmentId = taskClassAssignmentId;
		this.taskId = taskId;
		this.title = title;
		this.theme = theme;
		this.difficulty = difficulty;
		this.description = description;
		this.dueAt = dueAt;
		this.learningStatus = learningStatus;
		this.progressStatus = progressStatus;
		this.saveStatus = saveStatus;
		this.evaluationStatus = evaluationStatus;
		this.surveyStatus = consentStatus == ConsentStatus.AGREED ? surveyStatus : "not_applicable";
		this.activeSurveyId = activeSurveyId;
		this.latestCompletedEvaluationId = latestCompletedEvaluationId;
		this.secondsUntilDue = secondsUntilDue;
		this.latestSubmissionId = latestSubmissionId;
	}

	public long getTaskClassAssignmentId() {
		return taskClassAssignmentId;
	}

	public long getTaskId() {
		return taskId;
	}

	public String getTitle() {
		return title;
	}

	public String getTheme() {
		return theme;
	}

	public String getDifficulty() {
		return difficulty;
	}

	public String getDescription() {
		return description;
	}

	public LocalDateTime getDueAt() {
		return dueAt;
	}

	public String getDueAtDisplay() {
		return dueAt == null ? "" : dueAt.format(DEADLINE_FORMAT);
	}

	public Long getSecondsUntilDue() {
		return secondsUntilDue;
	}

	public String getDueRemainingDisplay() {
		if (secondsUntilDue == null) {
			return "期限なし";
		}
		if (secondsUntilDue < 0) {
			return "期限終了";
		}
		long remainingDays = (secondsUntilDue + 86_399) / 86_400;
		return "残り" + remainingDays + "日";
	}

	public String getDueUrgencyClass() {
		if (secondsUntilDue == null || secondsUntilDue < 0) {
			return "";
		}
		if (secondsUntilDue <= 86_400) {
			return "is-urgent";
		}
		if (secondsUntilDue <= 259_200) {
			return "is-soon";
		}
		return "";
	}

	public String getLearningStatus() {
		return learningStatus;
	}

	public String getProgressStatus() {
		return TaskProgressStatus.forLatestEvaluation(progressStatus, evaluationStatus);
	}

	public String getSaveStatus() {
		return saveStatus;
	}

	public String getEvaluationStatus() {
		return evaluationStatus;
	}

	public String getSurveyStatus() {
		return surveyStatus;
	}

	public Long getActiveSurveyId() {
		return activeSurveyId;
	}

	public Long getLatestCompletedEvaluationId() {
		return latestCompletedEvaluationId;
	}

	public Long getLatestSubmissionId() {
		return latestSubmissionId;
	}
}
