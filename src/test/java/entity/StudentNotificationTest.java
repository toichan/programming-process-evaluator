package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class StudentNotificationTest {
	@Test void exposesAgreedGettersAndFormatsDatabaseTimestamp() {
		var notification = new StudentNotification(7, StudentNotification.TYPE_EVALUATION_COMPLETED,
				"評価が完了しました", "<b>raw</b>", LocalDateTime.of(2026, 10, 7, 9, 5, 59, 999_000_000),
				false, 11L, 13L);
		assertEquals(7, notification.getNotificationId());
		assertEquals("評価が完了しました", notification.getTitle());
		assertEquals("<b>raw</b>", notification.getMessage(), "escaping belongs to the JSP output");
		assertEquals("2026/10/07 09:05", notification.getCreatedAtLabel());
		assertFalse(notification.isRead());
		assertEquals(11L, notification.getAssignmentId());
		assertEquals(13L, notification.getSubmissionId());
	}

	@Test void rejectsInvalidIdentifiersAndMissingFields() {
		assertThrows(IllegalArgumentException.class, () -> new StudentNotification(0, "evaluation_completed",
				"t", "m", LocalDateTime.now(), false, 1L, 1L));
		assertThrows(NullPointerException.class, () -> new StudentNotification(1, "evaluation_completed",
				null, "m", LocalDateTime.now(), false, 1L, 1L));
		assertThrows(NullPointerException.class, () -> new StudentNotification(1, "evaluation_completed",
				"t", "m", null, false, 1L, 1L));
	}

	@Test void exposesDistributionTargetAndOwnedExerciseReferences() {
		var notification = new StudentNotification(12, StudentNotification.TYPE_EXERCISE_DISTRIBUTED,
				"配布完了", "授業演習へ移動できます。", LocalDateTime.now(), false,
				null, null, 21L, 34L);
		assertEquals(21L, notification.getDistributionTargetId());
		assertEquals(34L, notification.getStudentExerciseId());
		assertNull(notification.getAssignmentId());
		assertNull(notification.getSubmissionId());
	}

	@Test void exposesSurveyAvailableTargetReferences() {
		var notification = new StudentNotification(13, StudentNotification.TYPE_SURVEY_AVAILABLE,
				"アンケートに回答できます", "課題の評価についてアンケートに回答できます。",
				LocalDateTime.now(), false, 21L, null, null, null, 34L, 55L);
		assertEquals(21L, notification.getAssignmentId());
		assertEquals(34L, notification.getEvaluationId());
		assertEquals(55L, notification.getSurveyId());
		assertNull(notification.getSubmissionId());
		assertNull(notification.getDistributionTargetId());
		assertNull(notification.getStudentExerciseId());
	}

	@Test void exposesSurveyInProgressTargetReferences() {
		var notification = new StudentNotification(14, StudentNotification.TYPE_SURVEY_IN_PROGRESS,
				"アンケートの回答を再開できます", "保存済みの回答から再開できます。",
				LocalDateTime.now(), false, 21L, null, null, null, 34L, 55L);
		assertEquals(21L, notification.getAssignmentId());
		assertEquals(34L, notification.getEvaluationId());
		assertEquals(55L, notification.getSurveyId());
		assertNull(notification.getSubmissionId());
	}

	@Test void pageIsImmutableAndReportsNavigation() {
		var items = new java.util.ArrayList<StudentNotification>();
		items.add(new StudentNotification(1, "evaluation_completed", "t", "m", LocalDateTime.now(), true, 1L, 2L));
		var page = new StudentNotificationPage(items, 3, 2, true);
		items.clear();
		assertEquals(1, page.getNotifications().size());
		assertThrows(UnsupportedOperationException.class, () -> page.getNotifications().clear());
		assertEquals(3, page.getUnreadCount());
		assertEquals(2, page.getPageNumber());
		assertTrue(page.isHasNext());
		assertTrue(page.getHasNext());
		assertTrue(page.isHasPrevious());
		assertFalse(new StudentNotificationPage(List.of(), 0, 1, false).isHasPrevious());
		assertThrows(IllegalArgumentException.class, () -> new StudentNotificationPage(List.of(), 0, 0, false));
		assertThrows(IllegalArgumentException.class, () -> new StudentNotificationPage(List.of(), -1, 1, false));
	}
}
