package entity;

import java.util.List;

public final class StudentSurveyPage {
	private final long assignmentId;
	private final long surveyId;
	private final long evaluationId;
	private final long evaluationSubmissionId;
	private final String taskTitle;
	private final String difficulty;
	private final String surveyTitle;
	private final String responseStatus;
	private final boolean responseExists;
	private final String evaluationFeedback;
	private final List<StudentEvaluationPage.DimensionResult> evaluationDimensions;
	private final List<StudentSurveyQuestion> questions;
	private final List<StudentSurveyHistoryEntry> history;

	public StudentSurveyPage(
			long assignmentId,
			long surveyId,
			long evaluationId,
			long evaluationSubmissionId,
			String taskTitle,
			String difficulty,
			String surveyTitle,
			String responseStatus,
			boolean responseExists,
			String evaluationFeedback,
			List<StudentEvaluationPage.DimensionResult> evaluationDimensions,
			List<StudentSurveyQuestion> questions,
			List<StudentSurveyHistoryEntry> history) {
		this.assignmentId = assignmentId;
		this.surveyId = surveyId;
		this.evaluationId = evaluationId;
		this.evaluationSubmissionId = evaluationSubmissionId;
		this.taskTitle = taskTitle;
		this.difficulty = difficulty;
		this.surveyTitle = surveyTitle;
		this.responseStatus = responseStatus;
		this.responseExists = responseExists;
		this.evaluationFeedback = evaluationFeedback;
		this.evaluationDimensions = List.copyOf(evaluationDimensions);
		this.questions = List.copyOf(questions);
		this.history = List.copyOf(history);
	}

	public long getAssignmentId() { return assignmentId; }
	public long getSurveyId() { return surveyId; }
	public long getEvaluationId() { return evaluationId; }
	public long getEvaluationSubmissionId() { return evaluationSubmissionId; }
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
	public String getSurveyTitle() { return surveyTitle; }
	public String getResponseStatus() { return responseStatus; }
	public boolean isResponseExists() { return responseExists; }
	public boolean isReadOnly() { return "submitted".equals(responseStatus); }
	public boolean isInProgress() { return "in_progress".equals(responseStatus); }
	public String getEvaluationFeedback() { return evaluationFeedback; }
	public List<StudentEvaluationPage.DimensionResult> getEvaluationDimensions() { return evaluationDimensions; }
	public List<StudentSurveyQuestion> getQuestions() { return questions; }
	public List<StudentSurveyHistoryEntry> getHistory() { return history; }
}
