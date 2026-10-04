package entity;

public record ExerciseUploadResult(String status, Long exerciseId, long version,
		int addedFileCount, int skippedFileCount) {
	public ExerciseUploadResult {
		if (status == null || status.isBlank() || addedFileCount < 0 || skippedFileCount < 0) {
			throw new IllegalArgumentException("アップロード結果が正しくありません。");
		}
		if (exerciseId != null) StudentExerciseInput.requireId(exerciseId);
		StudentExerciseInput.requireVersion(version);
	}
}
