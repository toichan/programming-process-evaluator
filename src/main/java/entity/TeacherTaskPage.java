package entity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record TeacherTaskPage(
		List<TeacherTaskDetails> tasks,
		List<TeacherSchoolOption> schools,
		List<TeacherClassOption> classes,
		List<TeacherHintOption> reusableHints,
		List<TeacherTaskDetails> deletedTasks,
		TeacherTaskDetails selectedTask,
		List<TeacherTaskAuditEntry> auditEntries,
		Map<Long, Set<Long>> assignmentClassHistory) {

	public TeacherTaskPage {
		tasks = List.copyOf(Objects.requireNonNull(tasks));
		schools = List.copyOf(Objects.requireNonNull(schools));
		classes = List.copyOf(Objects.requireNonNull(classes));
		reusableHints = List.copyOf(Objects.requireNonNull(reusableHints));
		deletedTasks = List.copyOf(Objects.requireNonNull(deletedTasks));
		auditEntries = List.copyOf(Objects.requireNonNull(auditEntries));
		assignmentClassHistory = Objects.requireNonNull(assignmentClassHistory).entrySet().stream()
				.collect(java.util.stream.Collectors.toUnmodifiableMap(
						Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
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

	public Map<Long, Set<Long>> getAssignmentClassHistory() {
		return assignmentClassHistory;
	}
}
