package entity;

import java.util.List;
import java.util.Objects;

public record TeacherProgressDetail(
		TeacherProgressRow row, List<TeacherProgressActivity> activities, TeacherProgressCode latestCode) {
	public TeacherProgressDetail {
		Objects.requireNonNull(row);
		activities = List.copyOf(Objects.requireNonNull(activities));
	}
}
