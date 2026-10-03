package entity;

import java.io.Serializable;
import java.util.List;

public final class EditorSubmissionCheck implements Serializable {
	private static final long serialVersionUID = 1L;

	private final long userId;
	private final long assignmentId;
	private final long participationId;
	private final String draftUpdatedAt;
	private final String sourceCode;
	private final long createdAtEpochMillis;
	private final List<EditorSubmissionCheckCase> results;

	public EditorSubmissionCheck(
			long userId,
			long assignmentId,
			long participationId,
			String draftUpdatedAt,
			String sourceCode,
			long createdAtEpochMillis,
			List<EditorSubmissionCheckCase> results) {
		this.userId = userId;
		this.assignmentId = assignmentId;
		this.participationId = participationId;
		this.draftUpdatedAt = draftUpdatedAt;
		this.sourceCode = sourceCode;
		this.createdAtEpochMillis = createdAtEpochMillis;
		this.results = List.copyOf(results);
	}

	public long getUserId() { return userId; }
	public long getAssignmentId() { return assignmentId; }
	public long getParticipationId() { return participationId; }
	public String getDraftUpdatedAt() { return draftUpdatedAt; }
	public String getSourceCode() { return sourceCode; }
	public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
	public List<EditorSubmissionCheckCase> getResults() { return results; }
}
