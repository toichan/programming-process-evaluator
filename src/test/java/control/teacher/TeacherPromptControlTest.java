package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import dao.StandardRubricDao;
import dao.TeacherPermissionDao;
import dao.TeacherPromptDao;
import dao.TeacherTaskDao;
import entity.UserCredential.UserType;

class TeacherPromptControlTest {
	@Test
	void rejectsInitialApplicationWithoutRoleConfirmationAndVersionsBeforeOpeningConnection() {
		AtomicInteger openedConnections = new AtomicInteger();
		TeacherPromptControl control = control(openedConnections);
		assertThrows(SecurityException.class, () -> control.applyUnpublishedPrompt(
				user(UserType.STUDENT, false), 1, 1, 1, 1, true));
		assertThrows(IllegalArgumentException.class, () -> control.applyUnpublishedPrompt(
				user(UserType.TEACHER, false), 1, 1, 1, 1, false));
		assertThrows(IllegalArgumentException.class, () -> control.applyUnpublishedPrompt(
				user(UserType.TEACHER, false), 1, 1, 0, 1, true));
		assertThrows(IllegalArgumentException.class, () -> control.applyUnpublishedPrompt(
				user(UserType.TEACHER, false), 1, 1, 1, 0, true));
		assertEquals(0, openedConnections.get());
	}
	@Test
	void rejectsNonTeacherBeforeOpeningDatabaseConnection() {
		AtomicInteger openedConnections = new AtomicInteger();
		TeacherPromptControl control = control(openedConnections);
		AuthenticatedUser student = user(UserType.STUDENT, false);

		assertThrows(SecurityException.class, () -> control.loadPage(student, null, null));
		assertEquals(0, openedConnections.get());
	}

	@Test
	void rejectsPromptInputBeforeOpeningDatabaseConnection() {
		AtomicInteger openedConnections = new AtomicInteger();
		TeacherPromptControl control = control(openedConnections);

		assertThrows(IllegalArgumentException.class, () -> control.saveDraft(
				user(UserType.TEACHER, false),
				1,
				null,
				0,
				"gemini-3.7-flash",
				"  ",
				""));
		assertEquals(0, openedConnections.get());
	}

	@Test
	void rejectsLegacyModelBeforeOpeningDatabaseConnection() {
		AtomicInteger openedConnections = new AtomicInteger();
		TeacherPromptControl control = control(openedConnections);
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> control.saveDraft(
				user(UserType.TEACHER, false), 1, null, 0, "gemini-2.5-pro", "Synthetic prompt", ""));
		org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("モデル"));
		assertEquals(0, openedConnections.get());
	}

	private static TeacherPromptControl control(AtomicInteger openedConnections) {
		TeacherPromptAiClient aiClient = new TeacherPromptAiClient(
				(model, system, input, schema) -> {
					throw new AssertionError("The AI provider must not be called by validation tests.");
				});
		return new TeacherPromptControl(
				new TeacherPermissionDao(),
				new TeacherTaskDao(),
				new TeacherPromptDao(),
				new StandardRubricDao(),
				aiClient,
				() -> {
					openedConnections.incrementAndGet();
					throw new AssertionError("Validation must happen before opening a connection.");
				});
	}

	private static AuthenticatedUser user(UserType userType, boolean passwordChangeRequired) {
		return new AuthenticatedUser(17, "teacher-test", "Synthetic teacher", userType,
				passwordChangeRequired, "synthetic-session");
	}
}
