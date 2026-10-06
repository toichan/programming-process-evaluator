package entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record TeacherTaskInput(
		String title,
		String theme,
		Difficulty difficulty,
		String description,
		String inputConstraints,
		String creationRules,
		String initialCode,
		List<String> features,
		List<EditorTestCase> testCases,
		List<HintInput> hints,
		List<ClassAssignmentInput> classAssignments,
		long schoolId) {

	public TeacherTaskInput {
		features = List.copyOf(Objects.requireNonNull(features));
		testCases = List.copyOf(Objects.requireNonNull(testCases));
		hints = List.copyOf(Objects.requireNonNull(hints));
		classAssignments = List.copyOf(Objects.requireNonNull(classAssignments));
	}

	public String getTitle() {
		return title;
	}

	public long getSchoolId() {
		return schoolId;
	}

	public String getTheme() {
		return theme;
	}

	public Difficulty getDifficulty() {
		return difficulty;
	}

	public String getDifficultyValue() {
		return difficulty == null ? "" : difficulty.databaseValue();
	}

	public String getDescription() {
		return description;
	}

	public String getInputConstraints() {
		return inputConstraints;
	}

	public String getCreationRules() {
		return creationRules;
	}

	public String getInitialCode() {
		return initialCode;
	}

	public List<String> getFeatures() {
		return features;
	}

	public List<EditorTestCase> getTestCases() {
		return testCases;
	}

	public List<HintInput> getHints() {
		return hints;
	}

	public List<ClassAssignmentInput> getClassAssignments() {
		return classAssignments;
	}

	public enum Difficulty {
		BEGINNER("beginner"),
		INTERMEDIATE("intermediate"),
		ADVANCED("advanced"),
		NONE("none");

		private final String databaseValue;

		Difficulty(String databaseValue) {
			this.databaseValue = databaseValue;
		}

		public String databaseValue() {
			return databaseValue;
		}

		public static Difficulty fromDatabaseValue(String value) {
			for (Difficulty difficulty : values()) {
				if (difficulty.databaseValue.equals(value)) {
					return difficulty;
				}
			}
			throw new IllegalArgumentException("Unknown task difficulty: " + value);
		}
	}

	public enum LateSubmissionPolicy {
		ALLOW("allow"),
		DENY("deny");

		private final String databaseValue;

		LateSubmissionPolicy(String databaseValue) {
			this.databaseValue = databaseValue;
		}

		public String databaseValue() {
			return databaseValue;
		}

		public static LateSubmissionPolicy fromDatabaseValue(String value) {
			for (LateSubmissionPolicy policy : values()) {
				if (policy.databaseValue.equals(value)) {
					return policy;
				}
			}
			throw new IllegalArgumentException("Unknown late submission policy: " + value);
		}
	}

	public record HintInput(long hintId, int order, EditorHint hint) {
		public HintInput {
			Objects.requireNonNull(hint);
		}

		public long getHintId() {
			return hintId;
		}

		public int getOrder() {
			return order;
		}

		public EditorHint getHint() {
			return hint;
		}
	}

	public record ClassAssignmentInput(
			long assignmentId,
			long classroomId,
			LocalDateTime publishAt,
			LocalDateTime dueAt,
			LateSubmissionPolicy lateSubmissionPolicy,
			String assignmentStatus) {
		public ClassAssignmentInput {
			Objects.requireNonNull(lateSubmissionPolicy);
			Objects.requireNonNull(assignmentStatus);
		}

		public ClassAssignmentInput(
				long assignmentId,
				long classroomId,
				LocalDateTime publishAt,
				LocalDateTime dueAt,
				LateSubmissionPolicy lateSubmissionPolicy) {
			this(assignmentId, classroomId, publishAt, dueAt, lateSubmissionPolicy, "not_published");
		}

		public long getAssignmentId() {
			return assignmentId;
		}

		public long getClassroomId() {
			return classroomId;
		}

		public LocalDateTime getPublishAt() {
			return publishAt;
		}

		public LocalDateTime getDueAt() {
			return dueAt;
		}

		public LateSubmissionPolicy getLateSubmissionPolicy() {
			return lateSubmissionPolicy;
		}

		public String getAssignmentStatus() {
			return assignmentStatus;
		}
	}
}
