package control.student;

import dao.StudentNotificationDao;

/** Exposes the package-private test seam to servlet tests without widening production API. */
public final class StudentNotificationControlTestSupport {
	public enum Access { READABLE, ACCOUNT_INVALID, UNAVAILABLE }

	private StudentNotificationControlTestSupport() {
	}

	public static StudentNotificationControl create(StudentNotificationDao dao, Access access) {
		return new StudentNotificationControl(dao, (user, assignment, submission) ->
				StudentNotificationControl.TargetAccess.valueOf(access.name()));
	}
}
