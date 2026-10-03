package entity;

import java.io.Serializable;

public final class EditorSubmissionCheckCase implements Serializable {
	private static final long serialVersionUID = 1L;

	private final long testCaseId;
	private final int order;
	private final String title;
	private final String input;
	private final String expectedOutput;
	private final String actualOutput;
	private final String standardError;
	private final String executionStatus;
	private final String resultStatus;
	private final String errorCode;
	private final Integer exitCode;
	private final int durationMilliseconds;
	private final boolean outputTruncated;
	private final boolean standardErrorTruncated;

	public EditorSubmissionCheckCase(
			long testCaseId,
			int order,
			String title,
			String input,
			String expectedOutput,
			String actualOutput,
			String standardError,
			String executionStatus,
			String resultStatus,
			String errorCode,
			Integer exitCode,
			int durationMilliseconds,
			boolean outputTruncated,
			boolean standardErrorTruncated) {
		this.testCaseId = testCaseId;
		this.order = order;
		this.title = title;
		this.input = input;
		this.expectedOutput = expectedOutput;
		this.actualOutput = actualOutput;
		this.standardError = standardError;
		this.executionStatus = executionStatus;
		this.resultStatus = resultStatus;
		this.errorCode = errorCode;
		this.exitCode = exitCode;
		this.durationMilliseconds = durationMilliseconds;
		this.outputTruncated = outputTruncated;
		this.standardErrorTruncated = standardErrorTruncated;
	}

	public long getTestCaseId() { return testCaseId; }
	public int getOrder() { return order; }
	public String getTitle() { return title; }
	public String getInput() { return input; }
	public String getExpectedOutput() { return expectedOutput; }
	public String getActualOutput() { return actualOutput; }
	public String getStandardError() { return standardError; }
	public String getExecutionStatus() { return executionStatus; }
	public String getResultStatus() { return resultStatus; }
	public String getErrorCode() { return errorCode; }
	public Integer getExitCode() { return exitCode; }
	public int getDurationMilliseconds() { return durationMilliseconds; }
	public boolean isOutputTruncated() { return outputTruncated; }
	public boolean isStandardErrorTruncated() { return standardErrorTruncated; }
	public boolean isPassed() { return "matched".equals(resultStatus); }
}
