package control.admin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;

class TeacherAccountControlTest {
	@Test void rejectsUnauthenticatedTeachersAndStudentsBeforeOpeningConnection() {
		var control = new TeacherAccountControl();
		for (UserType role : new UserType[] {UserType.TEACHER, UserType.STUDENT}) {
			var user = new AuthenticatedUser(1, "test", "test", role, false, "test");
			assertThrows(SecurityException.class, () -> control.list(user));
			assertThrows(SecurityException.class, () -> control.create(user, new TeacherAccountInput("test", Set.of(), Set.of())));
			assertThrows(SecurityException.class, () -> control.change(user, 2, 1, "delete", null));
			assertThrows(SecurityException.class, () -> control.history(user, 1L, false));
		}
		assertThrows(SecurityException.class, () -> control.list(null));
	}
	@Test void rejectsUnknownOperationsAndVersions() {
		var control = new TeacherAccountControl();
		var admin = new AuthenticatedUser(1, "admin", "admin", UserType.ADMIN, false, "test");
		assertThrows(IllegalArgumentException.class, () -> control.change(admin, 1, 1, "restore", null));
		assertThrows(IllegalArgumentException.class, () -> control.change(admin, 0, 1, "delete", null));
		assertThrows(IllegalArgumentException.class, () -> control.change(admin, 1, 0, "delete", null));
	}
}
