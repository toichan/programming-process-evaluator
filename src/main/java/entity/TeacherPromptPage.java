package entity;

import java.util.List;

public record TeacherPromptPage(
		List<TeacherTaskDetails> tasks,
		TeacherTaskDetails selectedTask,
		Long activePromptVersionId,
		List<TeacherPromptVersion> versions,
		TeacherPromptVersion selectedVersion,
		StandardRubric standardRubric,
		String targetSummary,
		List<TeacherPromptAuditEntry> auditEntries) {

	public TeacherPromptPage {
		tasks = List.copyOf(tasks);
		versions = List.copyOf(versions);
		auditEntries = List.copyOf(auditEntries);
	}

	public List<TeacherTaskDetails> getTasks() { return tasks; }
	public TeacherTaskDetails getSelectedTask() { return selectedTask; }
	public Long getActivePromptVersionId() { return activePromptVersionId; }
	public List<TeacherPromptVersion> getVersions() { return versions; }
	public TeacherPromptVersion getSelectedVersion() { return selectedVersion; }
	public StandardRubric getStandardRubric() { return standardRubric; }
	public String getTargetSummary() { return targetSummary; }
	public List<TeacherPromptAuditEntry> getAuditEntries() { return auditEntries; }
}
