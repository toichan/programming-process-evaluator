package entity;

import java.util.List;

public final class StudentHomePage {
	private final StudentAccountDetails account;
	private final ConsentStatus consentStatus;
	private final List<StudentTaskSummary> tasks;

	public StudentHomePage(
			StudentAccountDetails account,
			ConsentStatus consentStatus,
			List<StudentTaskSummary> tasks) {
		this.account = account;
		this.consentStatus = consentStatus;
		this.tasks = List.copyOf(tasks);
	}

	public StudentAccountDetails getAccount() {
		return account;
	}

	public ConsentStatus getConsentStatus() {
		return consentStatus;
	}

	public List<StudentTaskSummary> getTasks() {
		return tasks;
	}

	public int getSubmittedTaskCount() {
		return (int) tasks.stream()
				.filter(task -> switch (task.getProgressStatus()) {
					case "submitted", "awaiting_evaluation", "completed", "needs_action" -> true;
					default -> false;
				})
				.count();
	}
}
