package entity;

import java.util.List;
import java.util.Objects;

public record TeacherNavigationSummary(
		boolean taskManagementEnabled,
		List<TeacherSchoolOption> schools) {

	public TeacherNavigationSummary {
		schools = List.copyOf(Objects.requireNonNull(schools));
	}

	public boolean isTaskManagementEnabled() {
		return taskManagementEnabled;
	}

	public List<TeacherSchoolOption> getSchools() {
		return schools;
	}
}
