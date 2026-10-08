package entity;

public record TeacherExerciseRow(long studentId, String studentLoginId, long schoolId, String schoolName,
		long classroomId, String className, int fileCount, int entryCount, String updatedAt, String consent) {
	public String consentLabel() {
		return switch (consent) {
			case "agreed" -> "同意";
			case "declined", "withdrawn" -> "不同意";
			default -> "未確認";
		};
	}
}
