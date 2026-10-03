package entity;

public final class StudentAffiliation {
	private final String schoolName;
	private final String gradeName;
	private final String classroomName;

	public StudentAffiliation(String schoolName, String gradeName, String classroomName) {
		this.schoolName = schoolName;
		this.gradeName = gradeName;
		this.classroomName = classroomName;
	}

	public String getSchoolName() {
		return schoolName;
	}

	public String getGradeName() {
		return gradeName;
	}

	public String getClassroomName() {
		return classroomName;
	}
}
