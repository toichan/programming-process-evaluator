package entity;

import java.util.Objects;

public record TeacherProgressActivity(String occurredAt, String type, String result, String note) {
	public TeacherProgressActivity {
		Objects.requireNonNull(occurredAt);
		Objects.requireNonNull(type);
		Objects.requireNonNull(result);
		Objects.requireNonNull(note);
	}
}
