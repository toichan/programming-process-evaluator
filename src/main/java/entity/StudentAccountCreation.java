package entity;

public record StudentAccountCreation(long schoolId, long classroomId, String classroomName, int count) {
	public StudentAccountCreation {
		classroomName = classroomName == null ? "" : classroomName.strip();
		if (schoolId < 1 || classroomId < 0 || count < 1 || count > 200)
			throw new IllegalArgumentException("学校・クラスを選択し、人数は1〜200人で指定してください。");
		if (classroomId == 0 && (classroomName.isBlank() || classroomName.length() > 100
				|| classroomName.chars().anyMatch(Character::isISOControl)))
			throw new IllegalArgumentException("新しいクラス名を100文字以内で入力してください。");
	}
}
