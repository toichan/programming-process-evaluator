package entity;

import java.util.Objects;

import lib.time.DateTimeContract;

public record ExerciseExecutionResult(long executionId, PythonExecutionResult result, String standardInput,
		java.time.LocalDateTime executedAt) {
	public ExerciseExecutionResult(long executionId, PythonExecutionResult result) {
		this(executionId, result, "", null);
	}
	public long getExecutionId() { return executionId; }
	public PythonExecutionResult getResult() { return result; }
	public String getStandardInput() { return standardInput; }
	public java.time.LocalDateTime getExecutedAt() { return executedAt; }
	public String getExecutedAtDisplay() {
		return executedAt == null ? null : DateTimeContract.toUserDisplay(executedAt).toString();
	}

	public ExerciseExecutionResult {
		StudentExerciseInput.requireId(executionId);
		Objects.requireNonNull(result);
	}
}
