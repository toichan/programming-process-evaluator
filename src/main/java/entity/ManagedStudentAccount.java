package entity;

import java.util.List;

public record ManagedStudentAccount(long userId, String loginId, long version, String status,
		long schoolId, String schoolName, int securityLevel, List<TeacherClassOption> classrooms,
		String firstLoginStatus, boolean mustChangePassword, String consent, boolean passwordAvailable,
		boolean loginLocked, String createdAt, String createdBy, String updatedBy) {
	public ManagedStudentAccount {
		classrooms = List.copyOf(classrooms);
	}
}
