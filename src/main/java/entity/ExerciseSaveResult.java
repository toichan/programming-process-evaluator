package entity;

public record ExerciseSaveResult(long exerciseId, long entryId, long version) {
	public ExerciseSaveResult {
		StudentExerciseInput.requireId(exerciseId);
		StudentExerciseInput.requireId(entryId);
		StudentExerciseInput.requireVersion(version);
	}
}
