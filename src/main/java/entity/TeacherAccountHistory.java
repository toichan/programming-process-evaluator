package entity;

import java.time.LocalDateTime;

public record TeacherAccountHistory(LocalDateTime occurredAt, String actor, String teacher,
		String action, String result, String detail) {
	public LocalDateTime getOccurredAt() { return occurredAt; }
	public String getActor() { return actor; }
	public String getTeacher() { return teacher; }
	public String getAction() { return action; }
	public String getResult() { return result; }
	public String getDetail() { return detail; }
}
