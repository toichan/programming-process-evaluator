package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class LoginServletTest {
	@Test
	void teachersLandOnAnEnabledImplementedFeatureOrTheirMenu() {
		assertEquals("/teacher/task", LoginServlet.teacherDestinationFor(new entity.TeacherNavigationSummary(true, true, java.util.List.of())));
		assertEquals("/teacher/prompt", LoginServlet.teacherDestinationFor(new entity.TeacherNavigationSummary(false, true, java.util.List.of())));
		assertEquals("/teacher/home", LoginServlet.teacherDestinationFor(new entity.TeacherNavigationSummary(false, false, java.util.List.of())));
	}

	@Test
	void teachersPrioritizeAuthorizedStudentAccountManagement() {
		assertEquals("/teacher/students", LoginServlet.teacherDestinationFor(
				new entity.TeacherNavigationSummary(true, true, true, java.util.List.of())));
		assertEquals("/teacher/students", LoginServlet.teacherDestinationFor(
				new entity.TeacherNavigationSummary(false, true, true, java.util.List.of())));
		assertEquals("/teacher/students", LoginServlet.teacherDestinationFor(
				new entity.TeacherNavigationSummary(false, false, true, java.util.List.of())));
	}

	@Test
	void teachersWithoutAnAuthorizedLandingUseTheirMenu() {
		AuthenticatedUser teacher = new AuthenticatedUser(1, "teacher", "Synthetic", UserType.TEACHER, false, "test");

		assertEquals("/teacher/home", LoginServlet.destinationFor(teacher));
	}

	@Test
	void savedTeacherLandingsAreCanonicalizedAndStillRestrictedToTheAllowList() {
		assertEquals("/teacher/students", LoginServlet.teacherLandingFor("/teacher/account/account"));
		assertEquals("/teacher/students", LoginServlet.teacherLandingFor("/teacher/accounts"));
		assertEquals("/teacher/students", LoginServlet.teacherLandingFor("/teacher/students"));
		assertEquals("/teacher/task", LoginServlet.teacherLandingFor("/teacher/task"));
		assertEquals("/teacher/prompt", LoginServlet.teacherLandingFor("/teacher/prompt"));
		assertEquals("/teacher/home", LoginServlet.teacherLandingFor("/admin/teachers"));
		assertEquals("/teacher/home", LoginServlet.teacherLandingFor("https://evil.example"));
		assertEquals("/teacher/home", LoginServlet.teacherLandingFor(null));
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

		assertEquals("/student/account/password", LoginServlet.destinationFor(student));
	}

	@Test
	void teachersWithRequiredChangesLandOnTheirOwnPasswordScreen() {
		AuthenticatedUser teacher = new AuthenticatedUser(5, "teacher", "Synthetic", UserType.TEACHER, true, "test");
		assertEquals("/teacher/account/password", LoginServlet.destinationFor(teacher));
	}

	@Test void requiredTeacherFlagIsIndependentOfStudentSchoolPolicyAndAdmin() {
		var teacher = new entity.UserCredential(5, UserType.TEACHER, "teacher", "unused", "Synthetic",
				entity.UserCredential.AccountStatus.ACTIVE, 0, java.util.Optional.empty(), java.util.Optional.empty(), true);
		assertEquals(true, LoginServlet.passwordChangeRequired(teacher));
		var admin = new entity.UserCredential(6, UserType.ADMIN, "admin", "unused", "Synthetic",
				entity.UserCredential.AccountStatus.ACTIVE, 0, java.util.Optional.empty(), java.util.Optional.empty(), true);
		assertEquals(false, LoginServlet.passwordChangeRequired(admin));
	}
}
