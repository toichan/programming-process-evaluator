package entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class StudentSurveyHistoryEntry {
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

	private final long surveyId;
	private final long evaluationId;
	private final int revisionNumber;
	private final String responseStatus;
	private final LocalDateTime submittedAt;

	public StudentSurveyHistoryEntry(
			long surveyId,
			long evaluationId,
			int revisionNumber,
			String responseStatus,
			LocalDateTime submittedAt) {
		this.surveyId = surveyId;
		this.evaluationId = evaluationId;
		this.revisionNumber = revisionNumber;
		this.responseStatus = responseStatus;
		this.submittedAt = submittedAt;
	}

	public long getSurveyId() { return surveyId; }
	public long getEvaluationId() { return evaluationId; }
	public int getRevisionNumber() { return revisionNumber; }
	public String getResponseStatus() { return responseStatus; }
	public LocalDateTime getSubmittedAt() { return submittedAt; }
	public String getSubmittedAtDisplay() {
		return submittedAt == null ? "下書き保存" : submittedAt.format(DATE_TIME_FORMAT);
	}
}
