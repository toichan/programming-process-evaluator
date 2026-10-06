package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class LoginServletTest {
	@Test
	void teachersLandOnTaskManagementUntilAccountManagementIsAvailable() {
		AuthenticatedUser teacher = new AuthenticatedUser(1, "teacher", "Synthetic", UserType.TEACHER, false, "test");

		assertEquals("/teacher/task", LoginServlet.destinationFor(teacher));
	}

	@Test
	void administratorsKeepTheirExistingHome() {
		AuthenticatedUser admin = new AuthenticatedUser(2, "admin", "Synthetic", UserType.ADMIN, false, "test");

		assertEquals("/admin/home", LoginServlet.destinationFor(admin));
	}

	@Test
	void studentsKeepTheirExistingHome() {
		AuthenticatedUser student = new AuthenticatedUser(3, "student", "Synthetic", UserType.STUDENT, false, "test");

		assertEquals("/student/home", LoginServlet.destinationFor(student));
	}

	@Test
	void requiredPasswordChangesTakePrecedenceOverTheUserTypeLandingPage() {
		AuthenticatedUser student = new AuthenticatedUser(4, "student", "Synthetic", UserType.STUDENT, true, "test");

		assertEquals("/student/account/change-password", LoginServlet.destinationFor(student));
	}
}
