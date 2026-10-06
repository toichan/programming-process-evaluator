package entity;

import java.util.Objects;

public record TeacherSchoolOption(long schoolId, String schoolCode, String name) {
	public TeacherSchoolOption {
		Objects.requireNonNull(schoolCode);
		Objects.requireNonNull(name);
	}

	public long getSchoolId() {
		return schoolId;
	}

	public String getSchoolCode() {
		return schoolCode;
	}

	public String getName() {
		return name;
	}
}
