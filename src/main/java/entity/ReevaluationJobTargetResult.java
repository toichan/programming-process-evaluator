package entity;

import java.math.BigDecimal;

public record ReevaluationJobTargetResult(
		String studentLoginId,
		String classroomLabel,
		int submissionRevision,
		String status,
		BigDecimal overallScore,
		BigDecimal thinkingScore,
		BigDecimal attitudeScore,
		String safeErrorMessage) {
	public String getStudentLoginId() { return studentLoginId; }
	public String getClassroomLabel() { return classroomLabel; }
	public int getSubmissionRevision() { return submissionRevision; }
	public String getStatus() { return status; }
	public BigDecimal getOverallScore() { return overallScore; }
	public BigDecimal getThinkingScore() { return thinkingScore; }
	public BigDecimal getAttitudeScore() { return attitudeScore; }
	public String getSafeErrorMessage() { return safeErrorMessage; }
}
