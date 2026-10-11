package entity;

public final class StudentTaskShortcut {
	private final StudentTaskSummary task;
	private final StudentSurveyNavigation survey;
	private final String scoreLabel;

	public StudentTaskShortcut(StudentHomePage home, StudentTaskSummary task) {
		this(home, task, null);
	}

	public StudentTaskShortcut(StudentHomePage home, StudentTaskSummary task, String scoreLabel) {
		this.task = task;
		this.scoreLabel = scoreLabel;
		this.survey = StudentSurveyNavigation.forEvaluation(home, task.getTaskClassAssignmentId(),
				task.getLatestCompletedEvaluationId(), task.getLatestCompletedEvaluationId() != null);
	}

	public StudentTaskSummary getTask() { return task; }
	public StudentSurveyNavigation getSurvey() { return survey; }
	public String getDifficultyLabel() {
		return switch (task.getDifficulty()) {
			case "beginner" -> "初級";
			case "intermediate" -> "中級";
			case "advanced" -> "上級";
			default -> task.getDifficulty();
		};
	}
	public String getDifficultyClass() {
		return switch (task.getDifficulty()) {
			case "intermediate" -> "is-intermediate";
			case "advanced" -> "is-advanced";
			default -> "is-beginner";
		};
	}
	public String getProgressLabel() {
		return switch (task.getProgressStatus()) {
			case "in_progress" -> "編集中";
			case "submitted" -> "提出済み";
			case "awaiting_evaluation" -> "評価待ち";
			case "completed" -> "完了";
			case "needs_action" -> "要確認";
			default -> "未着手";
		};
	}
	public String getProgressClass() {
		return switch (task.getProgressStatus()) {
			case "in_progress", "needs_action", "awaiting_evaluation" -> "is-editing";
			case "submitted", "completed" -> "is-submitted";
			default -> "is-unstarted";
		};
	}
	public String getEvaluationLabel() {
		if (scoreLabel != null && "completed".equals(task.getEvaluationStatus())) return scoreLabel;
		return switch (task.getEvaluationStatus()) {
			case "completed" -> "評価完了";
			case "in_progress" -> "評価中";
			case "failed", "needs_revision" -> "要確認";
			default -> "未評価";
		};
	}
	public String getSurveyLabel() {
		if (!survey.isAvailable()) return "回答不可";
		return switch (task.getSurveyStatus()) {
			case "submitted" -> "回答済み";
			case "in_progress" -> "回答中";
			default -> "未回答";
		};
	}
}
