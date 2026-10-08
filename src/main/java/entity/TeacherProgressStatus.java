package entity;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public final class TeacherProgressStatus {
	private TeacherProgressStatus() {
	}

	public static String label(
			String progressStatus,
			String latestEvaluationStatus,
			Timestamp dueAt,
			String assignmentStatus,
			String lateSubmissionPolicy) {
		if ("in_progress".equals(progressStatus)) {
			return isLateAndBlocked(dueAt, assignmentStatus, lateSubmissionPolicy) ? "要対応" : "編集中";
		}
		if (progressStatus == null || "not_started".equals(progressStatus)) {
			return isLateAndBlocked(dueAt, assignmentStatus, lateSubmissionPolicy) ? "要対応" : "未着手";
		}
		return switch (TaskProgressStatus.forLatestEvaluation(progressStatus, latestEvaluationStatus)) {
			case "needs_action" -> "要対応";
			case "awaiting_evaluation" -> "評価待ち";
			case "completed" -> "完了";
			case "submitted" -> "提出済み";
			default -> "未着手";
		};
	}

	private static boolean isLateAndBlocked(Timestamp dueAt, String assignmentStatus, String lateSubmissionPolicy) {
		return "deny".equals(lateSubmissionPolicy)
				&& (dueAt != null && dueAt.toLocalDateTime().isBefore(LocalDateTime.now())
						|| "expired".equals(assignmentStatus));
	}
}
