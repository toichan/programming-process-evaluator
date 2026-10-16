package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.student.StudentNotificationControl.ExerciseTargetAccessCheck;
import control.student.StudentNotificationControl.OpenStatus;
import control.student.StudentNotificationControl.TargetAccess;
import control.student.StudentNotificationControl.TaskTargetAccessCheck;
import dao.StudentNotificationDao;
import entity.StudentNotification;
import entity.StudentNotificationPage;
import entity.UserCredential.UserType;

class StudentNotificationControlTest {
	private static final AuthenticatedUser STUDENT =
			new AuthenticatedUser(5, "synthetic-student", "synthetic", UserType.STUDENT, false, "test");
	private static final AuthenticatedUser TEACHER =
			new AuthenticatedUser(6, "synthetic-teacher", "synthetic", UserType.TEACHER, false, "test");
	private static final TaskTargetAccessCheck FAIL_TASK_ACCESS =
			(u, a) -> fail("task target must not be checked");

	private static final class FakeDao extends StudentNotificationDao {
		final List<String> calls = new ArrayList<>();
		StudentNotification stored;
		boolean markResult = true;
		boolean expiresDuringMark;

		@Override public Optional<StudentNotification> findRetained(long studentUserId, long notificationId) {
			calls.add("find:" + studentUserId + ":" + notificationId);
			return stored != null && studentUserId == 5 && notificationId == stored.getNotificationId()
					? Optional.of(stored) : Optional.empty();
		}
		@Override public boolean markRead(long studentUserId, long notificationId) {
			calls.add("mark:" + studentUserId + ":" + notificationId);
			if (expiresDuringMark) stored = null;
			return markResult;
		}
		@Override public int markAllRead(long studentUserId) {
			calls.add("markAll:" + studentUserId);
			return 2;
		}
		@Override public StudentNotificationPage findPage(long studentUserId, int pageNumber) {
			calls.add("page:" + studentUserId + ":" + pageNumber);
			return new StudentNotificationPage(List.of(), 0, pageNumber, false);
		}
		@Override public List<StudentNotification> findRecent(long studentUserId) {
			calls.add("recent:" + studentUserId);
			return List.of();
		}
		@Override public int countUnread(long studentUserId) {
			calls.add("unread:" + studentUserId);
			return 4;
		}
	}

	private static StudentNotification notification(Long assignmentId, Long submissionId) {
		return new StudentNotification(9, StudentNotification.TYPE_EVALUATION_COMPLETED, "t", "m",
				LocalDateTime.now(), false, assignmentId, submissionId);
	}

	private static StudentNotification taskPublishedNotification(Long assignmentId) {
		return new StudentNotification(9, StudentNotification.TYPE_TASK_PUBLISHED, "t", "m",
				LocalDateTime.now(), false, assignmentId, null);
	}

	private static StudentNotification dueSoonNotification(Long assignmentId) {
		return new StudentNotification(9, StudentNotification.TYPE_DUE_SOON, "t", "m",
				LocalDateTime.now(), false, assignmentId, null);
	}

	private static StudentNotification assignmentExpiredNotification(Long assignmentId) {
		return new StudentNotification(9, StudentNotification.TYPE_ASSIGNMENT_EXPIRED, "t", "m",
				LocalDateTime.now(), false, assignmentId, null);
	}

	private static StudentNotification exerciseDistributedNotification(Long targetId, Long exerciseId) {
		return new StudentNotification(9, StudentNotification.TYPE_EXERCISE_DISTRIBUTED, "t", "m",
				LocalDateTime.now(), false, null, null, targetId, exerciseId);
	}

	private static StudentNotification surveyAvailableNotification(
			Long assignmentId, Long evaluationId, Long surveyId) {
		return new StudentNotification(9, StudentNotification.TYPE_SURVEY_AVAILABLE, "t", "m",
				LocalDateTime.now(), false, assignmentId, null, null, null, evaluationId, surveyId);
	}

	private static StudentNotification surveyInProgressNotification(
			Long assignmentId, Long evaluationId, Long surveyId) {
		return new StudentNotification(9, StudentNotification.TYPE_SURVEY_IN_PROGRESS, "t", "m",
				LocalDateTime.now(), false, assignmentId, null, null, null, evaluationId, surveyId);
	}

	@Test void surveyAvailableNotificationOpensExactSurveyOnlyAfterAccessCheck() throws Exception {
		var dao = new FakeDao();
		dao.stored = surveyAvailableNotification(31L, 41L, 51L);
		var checked = new ArrayList<String>();
		var control = new StudentNotificationControl(
				dao, (u, a, s) -> fail("unexpected"), FAIL_TASK_ACCESS,
				(u, target, exercise) -> fail("unexpected"),
				(u, assignment) -> fail("unexpected"),
				(user, assignment, evaluation, survey) -> {
					checked.add(user.userId() + ":" + assignment + ":" + evaluation + ":" + survey);
					assertEquals(List.of("find:5:9"), dao.calls,
							"must validate the exact survey before marking the notification read");
					return TargetAccess.READABLE;
				});
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/survey?assignmentId=31&evaluationId=41&surveyId=51", result.redirectPath());
		assertEquals(List.of("5:31:41:51"), checked);
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void unavailableSurveyNotificationIsNotMarkedRead() throws Exception {
		var dao = new FakeDao();
		dao.stored = surveyAvailableNotification(31L, 41L, 51L);
		var control = new StudentNotificationControl(
				dao, (u, a, s) -> fail("unexpected"), FAIL_TASK_ACCESS,
				(u, target, exercise) -> fail("unexpected"),
				(u, assignment) -> fail("unexpected"),
				(user, assignment, evaluation, survey) -> TargetAccess.UNAVAILABLE);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		assertEquals(List.of("find:5:9"), dao.calls);
	}

	@Test void surveyInProgressNotificationOpensExactSurveyOnlyAfterAccessCheck() throws Exception {
		var dao = new FakeDao();
		dao.stored = surveyInProgressNotification(31L, 41L, 51L);
		var control = new StudentNotificationControl(
				dao, (u, a, s) -> fail("unexpected"), FAIL_TASK_ACCESS,
				(u, target, exercise) -> fail("unexpected"),
				(u, assignment) -> fail("unexpected"),
				(user, assignment, evaluation, survey) -> TargetAccess.READABLE);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/survey?assignmentId=31&evaluationId=41&surveyId=51", result.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void opensReadableTargetAndMarksOnlyAfterAccessCheck() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var checked = new ArrayList<String>();
		var control = new StudentNotificationControl(dao, (user, assignment, submission) -> {
			checked.add(user.userId() + ":" + assignment + ":" + submission);
			assertEquals(List.of("find:5:9"), dao.calls, "must not mark before the target is verified");
			return TargetAccess.READABLE;
		}, FAIL_TASK_ACCESS);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/evaluation?assignmentId=21&submissionId=22", result.redirectPath());
		assertEquals(List.of("5:21:22"), checked);
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void missingNonOwnedOrExpiredNotificationIsNotFoundWithoutMarking() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("target must not be checked"),
				FAIL_TASK_ACCESS);
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, 10).status());
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, 0).status());
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, -3).status());
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void expiryDuringTargetCheckDoesNotRedirect() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		dao.markResult = false;
		dao.expiresDuringMark = true;
		var control = new StudentNotificationControl(dao, (u, a, s) -> TargetAccess.READABLE, FAIL_TASK_ACCESS);
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, 9).status());
		assertEquals(List.of("find:5:9", "mark:5:9", "find:5:9"), dao.calls);
	}

	@Test void alreadyReadRetainedNotificationCanStillOpen() throws Exception {
		var dao = new FakeDao();
		dao.stored = new StudentNotification(9, StudentNotification.TYPE_EVALUATION_COMPLETED, "t", "m",
				LocalDateTime.now(), true, 21L, 22L);
		dao.markResult = false;
		var control = new StudentNotificationControl(dao, (u, a, s) -> TargetAccess.READABLE, FAIL_TASK_ACCESS);
		assertEquals(OpenStatus.OPENED, control.open(STUDENT, 9).status());
	}

	@Test void failureNotificationOpensTheExactSubmissionThroughTheSameAccessGuard() throws Exception {
		var dao = new FakeDao();
		dao.stored = new StudentNotification(9, StudentNotification.TYPE_EVALUATION_FAILED, "t", "m",
				LocalDateTime.now(), false, 21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> {
			assertEquals(21, a);
			assertEquals(22, s);
			return TargetAccess.READABLE;
		}, FAIL_TASK_ACCESS);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/evaluation?assignmentId=21&submissionId=22", result.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void taskPublishedNotificationOpensTheEditorThroughTheTaskAccessGuard() throws Exception {
		var dao = new FakeDao();
		dao.stored = taskPublishedNotification(31L);
		var checked = new ArrayList<String>();
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"), (user, assignment) -> {
			checked.add(user.userId() + ":" + assignment);
			return TargetAccess.READABLE;
		});
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/editor?assignmentId=31", result.redirectPath());
		assertEquals(List.of("5:31"), checked);
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void dueSoonNotificationOpensTheEditorThroughTheTaskAccessGuard() throws Exception {
		var dao = new FakeDao();
		dao.stored = dueSoonNotification(31L);
		var checked = new ArrayList<String>();
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"), (user, assignment) -> {
			checked.add(user.userId() + ":" + assignment);
			return TargetAccess.READABLE;
		});
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/editor?assignmentId=31", result.redirectPath());
		assertEquals(List.of("5:31"), checked);
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void expiredAllowedAssignmentOpensEditorWhileDeniedAssignmentFallsBackToHome() throws Exception {
		var dao = new FakeDao();
		dao.stored = assignmentExpiredNotification(31L);
		var control = new StudentNotificationControl(
				dao,
				(u, a, s) -> fail("unexpected"),
				(user, assignment) -> TargetAccess.READABLE,
				(user, targetId, exerciseId) -> fail("unexpected"),
				(user, assignment) -> fail("fallback must not be checked for an accessible editor"));
		var allowed = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, allowed.status());
		assertEquals("/student/editor?assignmentId=31", allowed.redirectPath());

		dao.calls.clear();
		dao.stored = assignmentExpiredNotification(32L);
		control = new StudentNotificationControl(
				dao,
				(u, a, s) -> fail("unexpected"),
				(user, assignment) -> TargetAccess.UNAVAILABLE,
				(user, targetId, exerciseId) -> fail("unexpected"),
				(user, assignment) -> {
					assertEquals(5, user.userId());
					assertEquals(32, assignment);
					return TargetAccess.READABLE;
				});
		var denied = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, denied.status());
		assertEquals("/student/home#taskListSection", denied.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void expiredAssignmentUnavailableToStudentUsesSameSafeOutcomeAndMarksRead() throws Exception {
		var dao = new FakeDao();
		dao.stored = assignmentExpiredNotification(31L);
		var control = new StudentNotificationControl(
				dao,
				(u, a, s) -> fail("unexpected"),
				(user, assignment) -> TargetAccess.UNAVAILABLE,
				(user, targetId, exerciseId) -> fail("unexpected"),
				(user, assignment) -> TargetAccess.UNAVAILABLE);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, result.status());
		assertEquals("/student/notifications", result.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void exerciseDistributionOpensOnlyTheOwnedDeliveryScopeAndMarksAfterAccessCheck() throws Exception {
		var dao = new FakeDao();
		dao.stored = exerciseDistributedNotification(31L, 42L);
		var checked = new ArrayList<String>();
		ExerciseTargetAccessCheck access = (user, targetId, exerciseId) -> {
			checked.add(user.userId() + ":" + targetId + ":" + exerciseId);
			assertEquals(List.of("find:5:9"), dao.calls, "must verify ownership before marking read");
			return TargetAccess.READABLE;
		};
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"),
				FAIL_TASK_ACCESS, access);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals("/student/exercise?exerciseId=42", result.redirectPath());
		assertEquals(List.of("5:31:42"), checked);
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void unavailableOrMalformedExerciseDistributionDoesNotMarkRead() throws Exception {
		var dao = new FakeDao();
		dao.stored = exerciseDistributedNotification(31L, 42L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"),
				FAIL_TASK_ACCESS, (user, target, exercise) -> TargetAccess.UNAVAILABLE);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));

		dao.calls.clear();
		dao.stored = exerciseDistributedNotification(null, 42L);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void dueSoonNotificationWithUnavailableAssignmentIsConflictWithoutMarking() throws Exception {
		var dao = new FakeDao();
		dao.stored = dueSoonNotification(31L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"),
				(u, a) -> TargetAccess.UNAVAILABLE);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, result.status());
		assertEquals("/student/notifications", result.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void taskPublishedNotificationWithUnavailableAssignmentUsesSafeOutcomeAndMarksRead() throws Exception {
		var dao = new FakeDao();
		dao.stored = taskPublishedNotification(31L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"),
				(u, a) -> TargetAccess.UNAVAILABLE);
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, result.status());
		assertEquals("/student/notifications", result.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void malformedTaskNotificationUsesTheSameSafeOutcomeAndMarksRead() throws Exception {
		var dao = new FakeDao();
		dao.stored = taskPublishedNotification(null);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"),
				(u, a) -> fail("malformed target must not be checked"));
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, result.status());
		assertEquals("/student/notifications", result.redirectPath());
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void taskPublishedNotificationWithInvalidAccountIsRejectedWithoutMarking() {
		var dao = new FakeDao();
		dao.stored = taskPublishedNotification(31L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"),
				(u, a) -> TargetAccess.ACCOUNT_INVALID);
		assertThrows(SecurityException.class, () -> control.open(STUDENT, 9));
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void inaccessibleTargetIsConflictWithoutMarking() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> TargetAccess.UNAVAILABLE, FAIL_TASK_ACCESS);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		dao.stored = notification(null, 22L);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void invalidAccountIsRejectedWithoutMarking() {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> TargetAccess.ACCOUNT_INVALID, FAIL_TASK_ACCESS);
		assertThrows(SecurityException.class, () -> control.open(STUDENT, 9));
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void nonStudentsAreRejectedBeforeAnyDataAccess() {
		var dao = new FakeDao();
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"), FAIL_TASK_ACCESS);
		for (AuthenticatedUser user : new AuthenticatedUser[] { null, TEACHER }) {
			assertThrows(SecurityException.class, () -> control.open(user, 9));
			assertThrows(SecurityException.class, () -> control.markAllRead(user));
			assertThrows(SecurityException.class, () -> control.loadPage(user, 1));
			assertThrows(SecurityException.class, () -> control.loadRecent(user));
			assertThrows(SecurityException.class, () -> control.countUnread(user));
		}
		assertEquals(List.of(), dao.calls);
	}

	@Test void scopesListAndBulkOperationsToCurrentStudent() throws Exception {
		var dao = new FakeDao();
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"), FAIL_TASK_ACCESS);
		assertEquals(2, control.markAllRead(STUDENT));
		assertEquals(3, control.loadPage(STUDENT, 3).getPageNumber());
		assertEquals(4, control.countUnread(STUDENT));
		control.loadRecent(STUDENT);
		assertThrows(IllegalArgumentException.class, () -> control.loadPage(STUDENT, 0));
		assertEquals(List.of("markAll:5", "page:5:3", "unread:5", "recent:5"), dao.calls);
	}
}
