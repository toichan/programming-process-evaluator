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
		if ("needs_action".equals(progressStatus)
				|| "failed".equals(latestEvaluationStatus) || "needs_revision".equals(latestEvaluationStatus)) {
			return "要対応";
		}
		if ("in_progress".equals(latestEvaluationStatus)) {
			return "評価待ち";
		}
		if ("completed".equals(latestEvaluationStatus)) {
			return "完了";
		}
		if ("awaiting_evaluation".equals(progressStatus)) {
			return "評価待ち";
		}
		if ("completed".equals(progressStatus)) {
			return "完了";
		}
		if ("submitted".equals(progressStatus)) {
			return "提出済み";
		}
		return "未着手";
	}

	private static boolean isLateAndBlocked(Timestamp dueAt, String assignmentStatus, String lateSubmissionPolicy) {
		return "deny".equals(lateSubmissionPolicy)
				&& (dueAt != null && dueAt.toLocalDateTime().isBefore(LocalDateTime.now())
						|| "expired".equals(assignmentStatus));
	}
}
