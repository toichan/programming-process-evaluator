package control.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import dao.TeacherPermissionDao;
import dao.TeacherTaskDao;
import entity.TeacherTaskInput;
import entity.UserCredential.UserType;

class TeacherTaskControlTest {
	@Test
	void rejectsAdminBeforeOpeningDatabaseConnection() {
		AtomicBoolean connectionOpened = new AtomicBoolean();
		TeacherTaskControl control = new TeacherTaskControl(
				new TeacherPermissionDao(),
				new TeacherTaskDao(),
				() -> {
					connectionOpened.set(true);
					throw new SQLException("Database must not be reached.");
				});
		AuthenticatedUser admin = new AuthenticatedUser(7, "synthetic", "Synthetic", UserType.ADMIN, false, "test");

		assertThrows(SecurityException.class, () -> control.createDraft(admin, emptyInput(), "request-1"));
		assertFalse(connectionOpened.get());
	}

	@Test
	void rejectsStudentBeforeOpeningDatabaseConnection() {
		AtomicBoolean connectionOpened = new AtomicBoolean();
		TeacherTaskControl control = new TeacherTaskControl(
				new TeacherPermissionDao(),
				new TeacherTaskDao(),
				() -> {
					connectionOpened.set(true);
					throw new SQLException("Database must not be reached.");
				});
		AuthenticatedUser student = new AuthenticatedUser(
				7, "synthetic", "Synthetic", UserType.STUDENT, false, "test");

		assertThrows(SecurityException.class, () -> control.createDraft(student, emptyInput(), "request-1"));
		assertFalse(connectionOpened.get());
	}

	@Test
	void rejectsInvalidUpdateVersionBeforeOpeningDatabaseConnection() {
		AtomicBoolean connectionOpened = new AtomicBoolean();
		TeacherTaskControl control = new TeacherTaskControl(
				new TeacherPermissionDao(),
				new TeacherTaskDao(),
				() -> {
					connectionOpened.set(true);
					throw new SQLException("Database must not be reached.");
				});
		AuthenticatedUser teacher = new AuthenticatedUser(7, "synthetic", "Synthetic", UserType.TEACHER, false, "test");

		assertThrows(IllegalArgumentException.class,
				() -> control.updateDraft(
						teacher, 12, 0, emptyInput(), "19db2fd2-1d73-4f51-88d7-222222222222"));
		assertFalse(connectionOpened.get());
	}

	private static TeacherTaskInput emptyInput() {
		return new TeacherTaskInput("Task", null, null, "Description", null, null, null,
				List.of(), List.of(), List.of(), List.of());
	}
}
