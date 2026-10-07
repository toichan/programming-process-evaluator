package entity;

import java.util.List;
import java.util.Objects;

public record TeacherNavigationSummary(
		boolean taskManagementEnabled,
		boolean promptDesignEnabled,
		List<TeacherSchoolOption> schools) {

	public TeacherNavigationSummary(boolean taskManagementEnabled, List<TeacherSchoolOption> schools) {
		this(taskManagementEnabled, taskManagementEnabled, schools);
	}

	public TeacherNavigationSummary {
		schools = List.copyOf(Objects.requireNonNull(schools));
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
}
