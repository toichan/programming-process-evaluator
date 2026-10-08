package control.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticationControl.LoginPortal;
import entity.UserCredential.UserType;

class LoginPortalTest {
	@Test
	void administratorRequiresExactReservedIdAndStaffPortal() {
		assertTrue(LoginPortal.STAFF.allows(UserType.ADMIN, "admin", "admin"));
		assertFalse(LoginPortal.STUDENT.allows(UserType.ADMIN, "admin", "admin"));
		assertFalse(LoginPortal.STAFF.allows(UserType.ADMIN, "admin", "Admin"));
		assertFalse(LoginPortal.STAFF.allows(UserType.ADMIN, "admin", "ADMIN"));
		assertFalse(LoginPortal.STAFF.allows(UserType.ADMIN, "legacy-admin", "legacy-admin"));
		assertFalse(LoginPortal.STAFF.allows(UserType.ADMIN, "legacy-admin", "admin"));
	}

	@Test
	void reservedIdNeverElevatesTeachersOrStudents() {
		for (UserType type : new UserType[] {UserType.TEACHER, UserType.STUDENT}) {
			for (String id : new String[] {"admin", "Admin", "ADMIN"}) {
				assertFalse(LoginPortal.STAFF.allows(type, id, id));
				assertFalse(LoginPortal.STUDENT.allows(type, id, id));
			}
		}
		assertTrue(LoginPortal.STAFF.allows(UserType.TEACHER, "teacher-demo", "teacher-demo"));
		assertFalse(LoginPortal.STUDENT.allows(UserType.TEACHER, "teacher-demo", "teacher-demo"));
		assertTrue(LoginPortal.STUDENT.allows(UserType.STUDENT, "s001", "s001"));
		assertFalse(LoginPortal.STAFF.allows(UserType.STUDENT, "s001", "s001"));
	}
}
