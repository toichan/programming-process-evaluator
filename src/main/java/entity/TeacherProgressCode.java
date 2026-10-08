package entity;

import java.util.Objects;

public record TeacherProgressCode(String code, String updatedAt, String source) {
	public TeacherProgressCode {
		Objects.requireNonNull(code);
		Objects.requireNonNull(updatedAt);
		if (!"draft".equals(source) && !"submission".equals(source)) {
			throw new IllegalArgumentException("Invalid progress code source.");
		}
	}
}
