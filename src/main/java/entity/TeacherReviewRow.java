package entity;

public record TeacherReviewRow(
		long submissionId, long participationId, long assignmentId, int revision,
		String studentLoginId, long schoolId, String schoolName, long classroomId, String className,
		long taskId, String taskName, String difficulty, String submittedAt,
		int matchedCases, int totalCases, String consent, String historyLabel,
		Long evaluationId, String evaluationStatus, String evaluatedAt, Double overallScore,
		Double thinkingScore, Double attitudeScore, boolean latestEvaluation) {

	public double matchRate() { return totalCases == 0 ? -1 : (double) matchedCases / totalCases; }
	public String difficultyLabel() {
		return switch (difficulty) {
			case "beginner" -> "初級";
			case "intermediate" -> "中級";
			case "advanced" -> "上級";
			default -> "未設定";
		};
	}
	public String consentLabel() {
		return switch (consent) {
			case "agreed" -> "同意";
			case "declined", "withdrawn" -> "未同意";
			default -> "未確認";
		};
	}
}
