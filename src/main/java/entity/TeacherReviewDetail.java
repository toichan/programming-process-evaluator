package entity;

import java.util.List;
import entity.StudentEvaluationPage.*;

public record TeacherReviewDetail(TeacherReviewRow row, String code, String publishedAt, String dueAt,
		List<Check> checks, Evaluation evaluation, Evaluation previousCompletedEvaluation,
		List<EvaluationVersion> evaluations, List<Log> logs) {
	public TeacherReviewDetail {
		checks = List.copyOf(checks);
		evaluations = List.copyOf(evaluations);
		logs = List.copyOf(logs);
	}
	public record Check(int order, String input, String expectedOutput, String actualOutput,
			String status, boolean truncated, String errorCode) {}
	public record Evaluation(long evaluationId, long submissionId, int revision, String status,
			String completedAt, String rubricVersion, String promptVersion, String feedbackSummary,
			String processAnalysis, List<DimensionResult> dimensions, List<ScoreResult> scores,
			List<ReasonResult> reasons) {}
	public record EvaluationVersion(long evaluationId, String status, String createdAt, String kind) {}
	public record Log(long logId, String eventType, String observedAt, String snapshot,
			String executionStatus, String standardInput, String standardOutput, String standardError) {}
}
