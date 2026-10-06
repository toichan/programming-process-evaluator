package entity;

import java.time.LocalDateTime;
import java.util.Objects;

public record TeacherTaskDetails(
		long taskId,
		String taskCode,
		String taskRevisionCode,
		int revisionNumber,
		long version,
		long createdByUserId,
		String createdByLoginId,
		Long updatedByUserId,
		String updatedByLoginId,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		String saveStatus,
		String publicationStatus,
		String rubricStatus,
		String promptStatus,
		boolean learningStarted,
		TeacherTaskInput input) {

	public TeacherTaskDetails {
		Objects.requireNonNull(taskCode);
		Objects.requireNonNull(taskRevisionCode);
		Objects.requireNonNull(createdByLoginId);
		Objects.requireNonNull(createdAt);
		Objects.requireNonNull(saveStatus);
		Objects.requireNonNull(publicationStatus);
		Objects.requireNonNull(input);
	}

	public long getTaskId() {
		return taskId;
	}

	public String getTaskCode() {
		return taskCode;
	}

	public String getTaskRevisionCode() {
		return taskRevisionCode;
	}

	public int getRevisionNumber() {
		return revisionNumber;
	}

	public long getVersion() {
		return version;
	}

	public long getCreatedByUserId() {
		return createdByUserId;
	}

	public String getCreatedByLoginId() {
		return createdByLoginId;
	}

	public Long getUpdatedByUserId() {
		return updatedByUserId;
	}

	public String getUpdatedByLoginId() {
		return updatedByLoginId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public String getSaveStatus() {
		return saveStatus;
	}

	public String getPublicationStatus() {
		return publicationStatus;
	}

	public String getRubricStatus() {
		return rubricStatus;
	}

	public String getPromptStatus() {
		return promptStatus;
	}

	public boolean isLearningStarted() {
		return learningStarted;
	}

	public TeacherTaskInput getInput() {
		return input;
	}
}
