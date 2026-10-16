package entity;

import java.time.LocalDateTime;

public record SchoolCommonPromptVersion(
		long commonPromptVersionId,
		long schoolId,
		int versionNumber,
		String commonPrompt,
		String createdByLoginId,
		LocalDateTime createdAt) {

	public long getCommonPromptVersionId() { return commonPromptVersionId; }
	public long getSchoolId() { return schoolId; }
	public int getVersionNumber() { return versionNumber; }
	public String getVersion() { return "ver." + versionNumber; }
	public String getCommonPrompt() { return commonPrompt; }
	public String getCreatedByLoginId() { return createdByLoginId; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
