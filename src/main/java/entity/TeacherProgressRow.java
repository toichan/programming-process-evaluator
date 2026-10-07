package entity;

import java.util.Objects;

public record TeacherProgressRow(
		long assignmentId,
		long studentUserId,
		Long participationId,
		String studentLoginId,
		String schoolName,
		long schoolId,
		String className,
		long classroomId,
		String taskName,
		long taskId,
		String difficulty,
		String difficultyLabel,
		String status,
		long activeDurationSeconds,
		String elapsedTime,
		String lastUpdated,
		String consent) {

	public TeacherProgressRow {
		Objects.requireNonNull(studentLoginId);
		Objects.requireNonNull(schoolName);
		Objects.requireNonNull(className);
		Objects.requireNonNull(taskName);
		Objects.requireNonNull(difficulty);
		Objects.requireNonNull(difficultyLabel);
		Objects.requireNonNull(status);
		Objects.requireNonNull(elapsedTime);
		Objects.requireNonNull(lastUpdated);
		Objects.requireNonNull(consent);
	}

	public boolean hasParticipation() {
		return participationId != null;
	}

	public long getAssignmentId() {
		return assignmentId;
	}

	public long getStudentUserId() {
		return studentUserId;
	}

	public String getStudentLoginId() {
		return studentLoginId;
	}

	public String getSchoolName() {
		return schoolName;
	}

	public long getSchoolId() {
		return schoolId;
	}

	public String getClassName() {
		return className;
	}

	public long getClassroomId() {
		return classroomId;
	}

	public String getTaskName() {
		return taskName;
	}

	public long getTaskId() {
		return taskId;
	}

	public String getDifficulty() {
		return difficulty;
	}

	public String getDifficultyLabel() {
		return difficultyLabel;
	}

	public String getStatus() {
		return status;
	}

	public long getActiveDurationSeconds() {
		return activeDurationSeconds;
	}

	public String getElapsedTime() {
		return elapsedTime;
	}

	public String getLastUpdated() {
		return lastUpdated;
	}

	public String getConsent() {
		return consent;
	}
}
