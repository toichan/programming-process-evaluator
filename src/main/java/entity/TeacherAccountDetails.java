package entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record TeacherAccountDetails(long userId, String loginId, String status, long version,
		LocalDateTime createdAt, String createdBy, List<TeacherSchoolOption> schools, Set<String> features) {
	public TeacherAccountDetails {
		schools = List.copyOf(schools);
		features = Set.copyOf(features);
	}
	public long getUserId() { return userId; }
	public String getLoginId() { return loginId; }
	public String getStatus() { return status; }
	public long getVersion() { return version; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public String getCreatedBy() { return createdBy; }
	public List<TeacherSchoolOption> getSchools() { return schools; }
	public Set<String> getFeatures() { return features; }
	public String getStatusLabel() {
		return switch (status) {
			case "active" -> "利用中";
			case "suspended" -> "停止中";
			case "deleted" -> "削除済み";
			default -> throw new IllegalStateException("Unknown teacher account state.");
		};
	}
}
