package entity;

import java.util.Objects;

public record TeacherClassOption(long classroomId, long schoolId, String name, String gradeName) {
	public TeacherClassOption {
		Objects.requireNonNull(name);
	}

	public long getClassroomId() {
		return classroomId;
	}

	public long getSchoolId() {
		return schoolId;
	}

	public String getName() {
		return name;
	}

	public String getGradeName() {
		return gradeName;
	}
}
