package entity;

import java.util.List;

public record TeacherPromptPage(
		List<TeacherTaskDetails> tasks,
		TeacherTaskDetails selectedTask,
		Long activePromptVersionId,
		List<TeacherPromptVersion> versions,
		TeacherPromptVersion selectedVersion,
		List<SchoolCommonPromptVersion> commonPromptVersions,
		StandardRubric standardRubric,
		String targetSummary,
		List<TeacherPromptAuditEntry> auditEntries,
		List<ReevaluationJobHistory> reevaluationJobs) {

	public TeacherPromptPage {
		tasks = List.copyOf(tasks);
		versions = List.copyOf(versions);
		commonPromptVersions = List.copyOf(commonPromptVersions);
		auditEntries = List.copyOf(auditEntries);
		reevaluationJobs = List.copyOf(reevaluationJobs);
	}

	public List<TeacherTaskDetails> getTasks() { return tasks; }
	public TeacherTaskDetails getSelectedTask() { return selectedTask; }
	public Long getActivePromptVersionId() { return activePromptVersionId; }
	public List<TeacherPromptVersion> getVersions() { return versions; }
	public TeacherPromptVersion getSelectedVersion() { return selectedVersion; }
	public List<SchoolCommonPromptVersion> getCommonPromptVersions() { return commonPromptVersions; }
	public StandardRubric getStandardRubric() { return standardRubric; }
	public String getTargetSummary() { return targetSummary; }
	public List<TeacherPromptAuditEntry> getAuditEntries() { return auditEntries; }
	public List<ReevaluationJobHistory> getReevaluationJobs() { return reevaluationJobs; }
}
