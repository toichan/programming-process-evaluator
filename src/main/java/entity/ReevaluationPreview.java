package entity;

import java.time.LocalDateTime;
import java.util.List;

public record ReevaluationPreview(
		long previewId,
		String previewCode,
		long taskId,
		long promptVersionId,
		String status,
		int participantCount,
		int targetCount,
		LocalDateTime expiresAt,
		long rowVersion,
		List<Target> targets) {

	public ReevaluationPreview {
		targets = List.copyOf(targets);
	}

	public String getPreviewCode() { return previewCode; }
	public String getStatus() { return status; }
	public int getParticipantCount() { return participantCount; }
	public int getTargetCount() { return targetCount; }
	public LocalDateTime getExpiresAt() { return expiresAt; }
	public List<Target> getTargets() { return targets; }

	public record Target(
			long participationId,
			String displayName,
			Integer revisionNumber,
			String status,
			Integer thinkingScore,
			Integer attitudeScore,
			Integer previousThinkingScore,
			Integer previousAttitudeScore,
			String thinkingReason,
			String attitudeReason,
			String safeErrorMessage) {
		public String getDisplayName() { return displayName; }
		public Integer getRevisionNumber() { return revisionNumber; }
		public String getStatus() { return status; }
		public Integer getThinkingScore() { return thinkingScore; }
		public Integer getAttitudeScore() { return attitudeScore; }
		public Integer getPreviousThinkingScore() { return previousThinkingScore; }
		public Integer getPreviousAttitudeScore() { return previousAttitudeScore; }
		public String getThinkingReason() { return thinkingReason; }
		public String getAttitudeReason() { return attitudeReason; }
		public String getSafeErrorMessage() { return safeErrorMessage; }
	}
}
