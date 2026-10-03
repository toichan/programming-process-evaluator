package entity;

public record EditorSubmissionResult(Status status, long submissionId, int revisionNumber) {
	public enum Status {
		SUBMITTED,
		DUPLICATE,
		CONFLICT,
		NOT_FOUND,
		NOT_ALLOWED,
		CHECK_EXPIRED
	}
}
