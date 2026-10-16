package servlet.student;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentNotificationUiContractTest {
	@Test void headerUsesRealNotificationsAndPostOnlyReadUpdates() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/student/shared/navigation.jspf"));
		assertTrue(jsp.contains("${studentRecentNotifications}"));
		assertTrue(jsp.contains("${studentUnreadNotificationCount}"));
		assertTrue(jsp.contains("/student/notifications/open"));
		assertTrue(jsp.contains("data-notification-open"));
		assertTrue(jsp.contains("name=\"notificationId\""));
		assertTrue(jsp.contains("すべて見る"));
		assertFalse(jsp.contains("通知（準備中）"));
		assertFalse(jsp.contains("localStorage"));
	}

	@Test void listProvidesEscapedContentBulkReadAndPaging() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/student/notifications/notifications.jsp"));
		assertTrue(jsp.contains("<c:out value=\"${notification.title}\""));
		assertTrue(jsp.contains("<c:out value=\"${notification.message}\""));
		assertTrue(jsp.contains("/student/notifications/read-all"));
		assertTrue(jsp.contains("通知はありません"));
		assertTrue(jsp.contains("notificationPage.hasNext"));
		assertTrue(jsp.contains("notificationPage.pageNumber - 1"));
		assertTrue(jsp.contains("data-task-target-unavailable"));
		assertTrue(jsp.contains("home-action.jspf"));
	}

	@Test void notificationNavigationWaitsForUnsavedConfirmation() throws Exception {
		String js = Files.readString(Path.of("src/main/webapp/js/student/shared/notifications.js"));
		assertTrue(js.contains("await window.PPEConfirmLeave()"));
		assertTrue(js.contains("window.PPEAllowNavigation?.()"));
		assertTrue(js.contains("PPEFeedback.createPageFeedback"));
		assertTrue(js.contains("この課題は削除済みです。通知を既読にしました。"));
		assertTrue(js.indexOf("await window.PPEConfirmLeave()")
				< js.indexOf("HTMLFormElement.prototype.submit.call(form)"));
		assertTrue(js.contains("pending.has(form)"));
		assertFalse(js.contains("window.confirm("));
		String css = Files.readString(Path.of("src/main/webapp/css/student/notifications/notifications.css"));
		assertTrue(css.contains("overflow-wrap: anywhere"));
		assertTrue(css.contains("max-width: 100%"));
	}
}
