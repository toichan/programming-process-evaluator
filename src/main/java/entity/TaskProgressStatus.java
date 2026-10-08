package entity;

public final class TaskProgressStatus {
	private TaskProgressStatus() {
	}

	public static String forLatestEvaluation(String progressStatus, String latestEvaluationStatus) {
		if (progressStatus == null || "not_started".equals(progressStatus) || "in_progress".equals(progressStatus)) {
			return progressStatus;
		}
		if ("needs_action".equals(progressStatus)
				|| "failed".equals(latestEvaluationStatus) || "needs_revision".equals(latestEvaluationStatus)) {
			return "needs_action";
		}
		if ("in_progress".equals(latestEvaluationStatus)) {
			return "awaiting_evaluation";
		}
		if ("completed".equals(latestEvaluationStatus)) {
			return "completed";
		}
		return progressStatus;
	}
}
