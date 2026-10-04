package entity;

public record ExerciseUnificationResult(long exerciseId, long version) {
	public ExerciseUnificationResult {
		StudentExerciseInput.requireId(exerciseId);
		StudentExerciseInput.requireVersion(version);
	}
}
