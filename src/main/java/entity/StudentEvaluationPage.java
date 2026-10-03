package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class StudentEvaluationPage {
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

	private final long assignmentId;
	private final String taskTitle;
	private final String difficulty;
	private final long selectedSubmissionId;
	private final int selectedRevision;
	private final LocalDateTime submittedAt;
	private final String evaluationStatus;
	private final boolean evaluationConfigured;
	private final String rubricVersion;
	private final String promptVersion;
	private final EvaluationResult evaluation;
	private final EvaluationResult previousCompletedEvaluation;
	private final List<SubmissionSummary> submissions;

	public StudentEvaluationPage(
			long assignmentId,
			String taskTitle,
			String difficulty,
			long selectedSubmissionId,
			int selectedRevision,
			LocalDateTime submittedAt,
			String evaluationStatus,
			boolean evaluationConfigured,
			String rubricVersion,
			String promptVersion,
			EvaluationResult evaluation,
			EvaluationResult previousCompletedEvaluation,
			List<SubmissionSummary> submissions) {
		this.assignmentId = assignmentId;
		this.taskTitle = taskTitle;
		this.difficulty = difficulty;
		this.selectedSubmissionId = selectedSubmissionId;
		this.selectedRevision = selectedRevision;
		this.submittedAt = submittedAt;
		this.evaluationStatus = evaluationStatus;
		this.evaluationConfigured = evaluationConfigured;
		this.rubricVersion = rubricVersion;
		this.promptVersion = promptVersion;
		this.evaluation = evaluation;
		this.previousCompletedEvaluation = previousCompletedEvaluation;
		this.submissions = List.copyOf(submissions);
	}

	public long getAssignmentId() { return assignmentId; }
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
	public long getSelectedSubmissionId() { return selectedSubmissionId; }
	public int getSelectedRevision() { return selectedRevision; }
	public LocalDateTime getSubmittedAt() { return submittedAt; }
	public String getSubmittedAtDisplay() { return submittedAt.format(DATE_TIME_FORMAT); }
	public String getEvaluationStatus() { return evaluationStatus; }
	public boolean isEvaluationConfigured() { return evaluationConfigured; }
	public String getRubricVersion() { return rubricVersion; }
	public String getPromptVersion() { return promptVersion; }
	public EvaluationResult getEvaluation() { return evaluation; }
	public EvaluationResult getPreviousCompletedEvaluation() { return previousCompletedEvaluation; }
	public List<SubmissionSummary> getSubmissions() { return submissions; }

	public String getEvaluationStatusLabel() {
		return switch (evaluationStatus) {
			case "not_started" -> evaluationConfigured ? "評価待ち" : "評価設定待ち";
			case "in_progress" -> "評価中";
			case "completed" -> "評価完了";
			case "failed" -> "評価に失敗";
			case "needs_revision" -> "要修正";
			default -> "評価状態を確認できません";
		};
	}

	public boolean isShowingPreviousCompletedEvaluation() {
		return "in_progress".equals(evaluationStatus)
				&& previousCompletedEvaluation != null
				&& previousCompletedEvaluation.getSubmissionId() != selectedSubmissionId;
	}

	public static final class SubmissionSummary {
		private final long submissionId;
		private final int revisionNumber;
		private final LocalDateTime submittedAt;
		private final String evaluationStatus;
		private final boolean selected;

		public SubmissionSummary(
				long submissionId,
				int revisionNumber,
				LocalDateTime submittedAt,
				String evaluationStatus,
				boolean selected) {
			this.submissionId = submissionId;
			this.revisionNumber = revisionNumber;
			this.submittedAt = submittedAt;
			this.evaluationStatus = evaluationStatus;
			this.selected = selected;
		}

		public long getSubmissionId() { return submissionId; }
		public int getRevisionNumber() { return revisionNumber; }
		public LocalDateTime getSubmittedAt() { return submittedAt; }
		public String getSubmittedAtDisplay() { return submittedAt.format(DATE_TIME_FORMAT); }
		public String getEvaluationStatus() { return evaluationStatus; }
		public String getEvaluationStatusLabel() {
			return switch (evaluationStatus) {
				case "not_started" -> "評価待ち";
				case "in_progress" -> "評価中";
				case "completed" -> "評価完了";
				case "failed" -> "評価に失敗";
				case "needs_revision" -> "要修正";
				default -> "状態確認中";
			};
		}
		public boolean isSelected() { return selected; }
	}

	public static final class EvaluationResult {
		private final long evaluationId;
		private final long submissionId;
		private final int revisionNumber;
		private final String status;
		private final LocalDateTime completedAt;
		private final String feedbackSummary;
		private final String processAnalysis;
		private final List<DimensionResult> dimensions;
		private final List<ScoreResult> scores;
		private final List<ReasonResult> reasons;

		public EvaluationResult(
				long evaluationId,
				long submissionId,
				int revisionNumber,
				String status,
				LocalDateTime completedAt,
				String feedbackSummary,
				String processAnalysis,
				List<DimensionResult> dimensions,
				List<ScoreResult> scores,
				List<ReasonResult> reasons) {
			this.evaluationId = evaluationId;
			this.submissionId = submissionId;
			this.revisionNumber = revisionNumber;
			this.status = status;
			this.completedAt = completedAt;
			this.feedbackSummary = feedbackSummary;
			this.processAnalysis = processAnalysis;
			this.dimensions = List.copyOf(dimensions);
			this.scores = List.copyOf(scores);
			this.reasons = List.copyOf(reasons);
		}

		public long getEvaluationId() { return evaluationId; }
		public long getSubmissionId() { return submissionId; }
		public int getRevisionNumber() { return revisionNumber; }
		public String getStatus() { return status; }
		public LocalDateTime getCompletedAt() { return completedAt; }
		public String getCompletedAtDisplay() { return completedAt == null ? "" : completedAt.format(DATE_TIME_FORMAT); }
		public String getFeedbackSummary() { return feedbackSummary; }
		public String getProcessAnalysis() { return processAnalysis; }
		public List<DimensionResult> getDimensions() { return dimensions; }
		public List<ScoreResult> getScores() { return scores; }
		public List<ReasonResult> getReasons() { return reasons; }
	}

	public static final class DimensionResult {
		private final String label;
		private final double score;
		private final String scoreText;
		private final String summaryTitle;
		private final String summaryDescription;

		public DimensionResult(String label, double score, String scoreText, String summaryTitle, String summaryDescription) {
			this.label = label;
			this.score = score;
			this.scoreText = scoreText;
			this.summaryTitle = summaryTitle;
			this.summaryDescription = summaryDescription;
		}

		public String getLabel() { return label; }
		public double getScore() { return score; }
		public String getScoreText() { return scoreText; }
		public String getSummaryTitle() { return summaryTitle; }
		public String getSummaryDescription() { return summaryDescription; }
	}

	public static final class ScoreResult {
		private final String dimensionLabel;
		private final String title;
		private final int score;
		private final String description;
		private final String rationale;

		public ScoreResult(String dimensionLabel, String title, int score, String description, String rationale) {
			this.dimensionLabel = dimensionLabel;
			this.title = title;
			this.score = score;
			this.description = description;
			this.rationale = rationale;
		}

		public String getDimensionLabel() { return dimensionLabel; }
		public String getTitle() { return title; }
		public int getScore() { return score; }
		public String getDescription() { return description; }
		public String getRationale() { return rationale; }
	}

	public static final class ReasonResult {
		private final String dimensionLabel;
		private final String title;
		private final String body;
		private final List<String> details;

		public ReasonResult(String dimensionLabel, String title, String body, List<String> details) {
			this.dimensionLabel = dimensionLabel;
			this.title = title;
			this.body = body;
			this.details = List.copyOf(details);
		}

		public String getDimensionLabel() { return dimensionLabel; }
		public String getTitle() { return title; }
		public String getBody() { return body; }
		public List<String> getDetails() { return details; }
	}
}
