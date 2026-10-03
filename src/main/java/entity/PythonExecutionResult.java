package entity;

public final class PythonExecutionResult {
	private final String status;
	private final Integer exitCode;
	private final String standardOutput;
	private final String standardError;
	private final boolean standardOutputTruncated;
	private final boolean standardErrorTruncated;
	private final String errorCode;

	public PythonExecutionResult(
			String status,
			Integer exitCode,
			String standardOutput,
			String standardError,
			boolean standardOutputTruncated,
			boolean standardErrorTruncated,
			String errorCode) {
		this.status = status;
		this.exitCode = exitCode;
		this.standardOutput = standardOutput;
		this.standardError = standardError;
		this.standardOutputTruncated = standardOutputTruncated;
		this.standardErrorTruncated = standardErrorTruncated;
		this.errorCode = errorCode;
	}

	public String getStatus() { return status; }
	public Integer getExitCode() { return exitCode; }
	public String getStandardOutput() { return standardOutput; }
	public String getStandardError() { return standardError; }
	public boolean isStandardOutputTruncated() { return standardOutputTruncated; }
	public boolean isStandardErrorTruncated() { return standardErrorTruncated; }
	public String getErrorCode() { return errorCode; }
}
