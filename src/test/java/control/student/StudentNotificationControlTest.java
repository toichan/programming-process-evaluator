package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.student.StudentNotificationControl.OpenStatus;
import control.student.StudentNotificationControl.TargetAccess;
import dao.StudentNotificationDao;
import entity.StudentNotification;
import entity.StudentNotificationPage;
import entity.UserCredential.UserType;

class StudentNotificationControlTest {
	private static final AuthenticatedUser STUDENT =
			new AuthenticatedUser(5, "synthetic-student", "synthetic", UserType.STUDENT, false, "test");
	private static final AuthenticatedUser TEACHER =
			new AuthenticatedUser(6, "synthetic-teacher", "synthetic", UserType.TEACHER, false, "test");

	private static final class FakeDao extends StudentNotificationDao {
		final List<String> calls = new ArrayList<>();
		StudentNotification stored;

		@Override public Optional<StudentNotification> findRetained(long studentUserId, long notificationId) {
			calls.add("find:" + studentUserId + ":" + notificationId);
			return stored != null && studentUserId == 5 && notificationId == stored.getNotificationId()
					? Optional.of(stored) : Optional.empty();
		}
		@Override public boolean markRead(long studentUserId, long notificationId) {
			calls.add("mark:" + studentUserId + ":" + notificationId);
			return true;
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

	@Test void opensReadableTargetAndMarksOnlyAfterAccessCheck() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var checked = new ArrayList<String>();
		var control = new StudentNotificationControl(dao, (user, assignment, submission) -> {
			checked.add(user.userId() + ":" + assignment + ":" + submission);
			assertEquals(List.of("find:5:9"), dao.calls, "must not mark before the target is verified");
			return TargetAccess.READABLE;
		});
		var result = control.open(STUDENT, 9);
		assertEquals(OpenStatus.OPENED, result.status());
		assertEquals(21, result.assignmentId());
		assertEquals(22, result.submissionId());
		assertEquals(List.of("5:21:22"), checked);
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void missingNonOwnedOrExpiredNotificationIsNotFoundWithoutMarking() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("target must not be checked"));
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, 10).status());
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, 0).status());
		assertEquals(OpenStatus.NOT_FOUND, control.open(STUDENT, -3).status());
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void inaccessibleTargetIsConflictWithoutMarking() throws Exception {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> TargetAccess.UNAVAILABLE);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		dao.stored = notification(null, 22L);
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(STUDENT, 9).status());
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void invalidAccountIsRejectedWithoutMarking() {
		var dao = new FakeDao();
		dao.stored = notification(21L, 22L);
		var control = new StudentNotificationControl(dao, (u, a, s) -> TargetAccess.ACCOUNT_INVALID);
		assertThrows(SecurityException.class, () -> control.open(STUDENT, 9));
		assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
	}

	@Test void nonStudentsAreRejectedBeforeAnyDataAccess() {
		var dao = new FakeDao();
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"));
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
		var control = new StudentNotificationControl(dao, (u, a, s) -> fail("unexpected"));
		assertEquals(2, control.markAllRead(STUDENT));
		assertEquals(3, control.loadPage(STUDENT, 3).getPageNumber());
		assertEquals(4, control.countUnread(STUDENT));
		control.loadRecent(STUDENT);
		assertThrows(IllegalArgumentException.class, () -> control.loadPage(STUDENT, 0));
		assertEquals(List.of("markAll:5", "page:5:3", "unread:5", "recent:5"), dao.calls);
	}
}
