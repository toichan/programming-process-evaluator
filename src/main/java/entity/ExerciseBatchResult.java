package entity;

public record ExerciseBatchResult(String status, long exerciseId, long version, int affectedCount) {
	public ExerciseBatchResult {
		if (status == null || status.isBlank() || affectedCount < 0) {
			throw new IllegalArgumentException("一括操作結果が正しくありません。");
		}
		StudentExerciseInput.requireId(exerciseId);
		StudentExerciseInput.requireVersion(version);
	}
}
