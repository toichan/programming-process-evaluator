package control.teacher;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import entity.TeacherReviewFilter;
import entity.UserCredential.UserType;

class TeacherReviewControlTest {
	@Test
	void rejectsStudentAdminAndMissingLoginBeforeOpeningDatabaseOrRunner() {
		var control = new TeacherReviewControl();
		for (UserType role : List.of(UserType.STUDENT, UserType.ADMIN)) {
			var user = new AuthenticatedUser(1, "dummy", "dummy", role, false, "test");
			assertThrows(SecurityException.class, () -> control.list(user, false, TeacherReviewFilter.empty()));
			assertThrows(SecurityException.class, () -> control.detail(user, false, 1, null));
			assertThrows(SecurityException.class, () -> control.preview(user, 1, "print(1)", ""));
			assertThrows(SecurityException.class, () -> control.exportCsv(user, TeacherReviewFilter.empty()));
			assertThrows(SecurityException.class, () -> control.exportSubmissionCsv(user, TeacherReviewFilter.empty()));
			assertThrows(SecurityException.class, () -> control.submissionFile(user, 1));
			assertThrows(SecurityException.class, () -> control.submissionZip(user, TeacherReviewFilter.empty()));
		}
		assertThrows(SecurityException.class, () -> control.detail(null, true, 1, null));
	}

	@Test
	void rejectsInvalidFilterValuesInsteadOfInterpolatingSqlOrSilentlyBroadeningScope() {
		assertThrows(IllegalArgumentException.class,
				() -> new TeacherReviewFilter(-1L, null, null, "", "", null, "", "submitted", "desc"));
		assertThrows(IllegalArgumentException.class,
				() -> new TeacherReviewFilter(null, null, null, "expert", "", null, "", "submitted", "desc"));
		assertThrows(IllegalArgumentException.class,
				() -> new TeacherReviewFilter(null, null, null, "", "withdrawn", null, "", "submitted", "desc"));
		assertThrows(IllegalArgumentException.class,
				() -> new TeacherReviewFilter(null, null, null, "", "", 6, "", "submitted", "desc"));
		assertThrows(IllegalArgumentException.class,
				() -> new TeacherReviewFilter(null, null, null, "", "", null, "", "DROP TABLE", "desc"));
	}

	@Test
	void previewValidatesCapsBeforeDatabaseAccess() {
		var teacher = new AuthenticatedUser(1, "dummy", "dummy", UserType.TEACHER, false, "test");
		var control = new TeacherReviewControl();
		assertThrows(IllegalArgumentException.class, () -> control.preview(teacher, 0, "", ""));
		assertThrows(IllegalArgumentException.class, () -> control.preview(teacher, 1, "x".repeat(65537), ""));
		assertThrows(IllegalArgumentException.class, () -> control.preview(teacher, 1, "", "x".repeat(8193)));
	}
	@Test void exportLimitIncludesExact32MiBBoundary() {
		assertDoesNotThrow(() -> TeacherReviewControl.requireExportSize(32L * 1024 * 1024));
		assertThrows(entity.PythonExecutionInput.TooLargeException.class,
				() -> TeacherReviewControl.requireExportSize(32L * 1024 * 1024 + 1));
	}
}
