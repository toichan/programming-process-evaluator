package entity;

import java.util.Objects;

public record ExerciseExecutionResult(long executionId, PythonExecutionResult result, String standardInput,
		java.time.LocalDateTime executedAt) {
	public ExerciseExecutionResult(long executionId, PythonExecutionResult result) {
		this(executionId, result, "", null);
	}
	public long getExecutionId() { return executionId; }
	public PythonExecutionResult getResult() { return result; }
	public String getStandardInput() { return standardInput; }
	public java.time.LocalDateTime getExecutedAt() { return executedAt; }

	public ExerciseExecutionResult {
		StudentExerciseInput.requireId(executionId);
		Objects.requireNonNull(result);
	}
}
