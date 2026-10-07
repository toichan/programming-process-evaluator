package entity;

import java.util.List;

public record TeacherDistributionPage(
		List<TeacherSchoolOption> schools,
		List<TeacherClassOption> classes,
		List<TemplateSummary> templates,
		List<DistributionSummary> distributions) {

	public TeacherDistributionPage {
		schools = List.copyOf(schools);
		classes = List.copyOf(classes);
		templates = List.copyOf(templates);
		distributions = List.copyOf(distributions);
	}

	public record TemplateSummary(long templateId, String name, String rootName, int version,
			String saveStatus, String templateStatus) {
		public long getTemplateId() { return templateId; }
		public String getName() { return name; }
		public String getRootName() { return rootName; }
		public int getVersion() { return version; }
		public String getSaveStatus() { return saveStatus; }
		public String getTemplateStatus() { return templateStatus; }
	}

	public record DistributionSummary(long distributionId, String templateName, String status,
			long templateId, String createdAt, String schoolName, String classroomNames,
			List<TargetSummary> targets, List<HistorySummary> history) {
		public DistributionSummary {
			targets = List.copyOf(targets);
			history = List.copyOf(history);
		}
		public long getDistributionId() { return distributionId; }
		public String getTemplateName() { return templateName; }
		public String getStatus() { return status; }
		public long getTemplateId() { return templateId; }
		public String getCreatedAt() { return createdAt; }
		public String getSchoolName() { return schoolName; }
		public String getClassroomNames() { return classroomNames; }
		public List<TargetSummary> getTargets() { return targets; }
		public List<HistorySummary> getHistory() { return history; }
	}

	public record TargetSummary(long targetId, long classroomId, String schoolName, String classroomName, String status,
			String scheduledAt, String scheduledInput, String distributedAt, String result) {
		public long getTargetId() { return targetId; }
		public long getClassroomId() { return classroomId; }
		public String getSchoolName() { return schoolName; }
		public String getClassroomName() { return classroomName; }
		public String getStatus() { return status; }
		public String getScheduledAt() { return scheduledAt; }
		public String getScheduledInput() { return scheduledInput; }
		public String getDistributedAt() { return distributedAt; }
		public String getResult() { return result; }
	}

	public record HistorySummary(String action, long actorId, String actor, String occurredAt,
			String result, String detail) {
		public String getAction() { return action; }
		public long getActorId() { return actorId; }
		public String getActor() { return actor; }
		public String getOccurredAt() { return occurredAt; }
		public String getResult() { return result; }
		public String getDetail() { return detail; }
	}

	public List<TeacherSchoolOption> getSchools() { return schools; }
	public List<TeacherClassOption> getClasses() { return classes; }
	public List<TemplateSummary> getTemplates() { return templates; }
	public List<DistributionSummary> getDistributions() { return distributions; }

	public long getScheduledCount() {
		return distributions.stream().filter(distribution -> "scheduled".equals(distribution.status())
				|| "in_progress".equals(distribution.status())).count();
	}
}
