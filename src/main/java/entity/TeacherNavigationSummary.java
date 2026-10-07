package entity;

import java.util.List;
import java.util.Objects;

public record TeacherNavigationSummary(
		boolean taskManagementEnabled,
		boolean promptDesignEnabled,
		boolean accountManagementEnabled,
		List<TeacherSchoolOption> schools,
		java.util.Set<String> features) {

	public TeacherNavigationSummary(boolean taskManagementEnabled, boolean promptDesignEnabled,
			boolean accountManagementEnabled, List<TeacherSchoolOption> schools) {
		this(taskManagementEnabled, promptDesignEnabled, accountManagementEnabled, schools,
				enabledFeatures(taskManagementEnabled, promptDesignEnabled, accountManagementEnabled));
	}

	public TeacherNavigationSummary(boolean taskManagementEnabled, boolean promptDesignEnabled, List<TeacherSchoolOption> schools) {
		this(taskManagementEnabled, promptDesignEnabled, false, schools);
	}

	public TeacherNavigationSummary(boolean taskManagementEnabled, List<TeacherSchoolOption> schools) {
		this(taskManagementEnabled, taskManagementEnabled, false, schools);
	}

	public TeacherNavigationSummary {
		schools = List.copyOf(Objects.requireNonNull(schools));
		features = java.util.Set.copyOf(Objects.requireNonNull(features));
	}

	public boolean isTaskManagementEnabled() {
		return taskManagementEnabled;
	}

	public boolean isPromptDesignEnabled() {
		return promptDesignEnabled;
	}

	public List<TeacherSchoolOption> getSchools() {
		return schools;
	}

	public boolean isAccountManagementEnabled() { return accountManagementEnabled; }
	public java.util.Set<String> getFeatures() { return features; }

	private static java.util.Set<String> enabledFeatures(boolean task, boolean prompt, boolean accounts) {
		var features = new java.util.HashSet<String>();
		if (task) features.add("task-management");
		if (prompt) features.add("teacher-prompt-design");
		if (accounts) features.add("account-management");
		return features;
	}
}
