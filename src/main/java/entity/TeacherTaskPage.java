package entity;

import java.util.List;
import java.util.Objects;

public record TeacherTaskPage(
		List<TeacherTaskDetails> tasks,
		List<TeacherSchoolOption> schools,
		List<TeacherClassOption> classes,
		List<TeacherHintOption> reusableHints,
		List<TeacherTaskDetails> deletedTasks,
		TeacherTaskDetails selectedTask,
		List<TeacherTaskAuditEntry> auditEntries) {

	public TeacherTaskPage {
		tasks = List.copyOf(Objects.requireNonNull(tasks));
		schools = List.copyOf(Objects.requireNonNull(schools));
		classes = List.copyOf(Objects.requireNonNull(classes));
		reusableHints = List.copyOf(Objects.requireNonNull(reusableHints));
		deletedTasks = List.copyOf(Objects.requireNonNull(deletedTasks));
		auditEntries = List.copyOf(Objects.requireNonNull(auditEntries));
	}

	public List<TeacherTaskDetails> getTasks() {
		return tasks;
	}

	public List<TeacherSchoolOption> getSchools() {
		return schools;
	}

	public List<TeacherClassOption> getClasses() {
		return classes;
	}

	public List<TeacherHintOption> getReusableHints() {
		return reusableHints;
	}

	public List<TeacherTaskDetails> getDeletedTasks() {
		return deletedTasks;
	}

	public TeacherTaskDetails getSelectedTask() {
		return selectedTask;
	}

	public List<TeacherTaskAuditEntry> getAuditEntries() {
		return auditEntries;
	}
}
