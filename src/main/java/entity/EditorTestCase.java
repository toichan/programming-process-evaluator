package entity;

public final class EditorTestCase {
	private final long testCaseId;
	private final String title;
	private final String input;
	private final String expectedOutput;
	private final int order;

	public EditorTestCase(long testCaseId, String title, String input, String expectedOutput, int order) {
		this.testCaseId = testCaseId;
		this.title = title;
		this.input = input;
		this.expectedOutput = expectedOutput;
		this.order = order;
	}

	public long getTestCaseId() {
		return testCaseId;
	}

	public String getTitle() {
		return title;
	}

	public String getInput() {
		return input;
	}

	public String getExpectedOutput() {
		return expectedOutput;
	}

	public int getOrder() {
		return order;
	}
}
