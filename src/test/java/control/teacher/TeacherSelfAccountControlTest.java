package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class TeacherSelfAccountControlTest {
	private final TeacherSelfAccountControl control = new TeacherSelfAccountControl(
			() -> { throw new AssertionError("Invalid callers must not open a DB connection."); });

	@Test void rejectsUnauthenticatedAndOtherRolesBeforeDatabaseAccess() {
		assertThrows(SecurityException.class, () -> control.load(null, 1));
		for (UserType type : new UserType[] {UserType.STUDENT, UserType.ADMIN}) {
			var user = new AuthenticatedUser(1, "synthetic", "Synthetic", type, false, "test");
			assertThrows(SecurityException.class, () -> control.load(user, 1));
		}
	}

	@Test void rejectsMissingVersionBeforeDatabaseAccess() {
		var teacher = new AuthenticatedUser(1, "synthetic", "Synthetic", UserType.TEACHER, false, "test");
		assertThrows(IllegalArgumentException.class, () -> control.load(teacher, 0));
	}
}
