package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import control.teacher.TeacherReviewFixture;
import lib.mysql.Client;

class TeacherUi163RuntimeTest {
	@Test
	void modalFormsRetainAuthorizationVersionChecksAndPersistAssignmentChanges() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("UI163_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		assertEquals("http://ppe-159-runtime:8080", System.getenv("EVALUATION_SURVEY_HTTP_BASE"));
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		long teacher = LocalUi160RuntimeTest.number("SELECT user_id FROM users WHERE login_id='review_teacher'");
		long task = LocalUi160RuntimeTest.number("SELECT task_id FROM tasks WHERE task_code='review-task'");
		long school = LocalUi160RuntimeTest.number("SELECT school_id FROM tasks WHERE task_code='review-task'");
		long assignment = LocalUi160RuntimeTest.number("SELECT task_class_assignment_id FROM task_class_assignments WHERE task_id=" + task);
		long classroom;
		try (var c = Client.createConnection(); var s = c.prepareStatement("""
				INSERT INTO teacher_feature_permissions(teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at)
				VALUES (?,?,TRUE,?,NOW()) ON DUPLICATE KEY UPDATE is_enabled=TRUE
				""")) {
			for (String feature : List.of("task-management", "task-progress")) {
				s.setLong(1, teacher); s.setString(2, feature); s.setLong(3, teacher); s.executeUpdate();
			}
			try (var insert = c.prepareStatement("""
					INSERT INTO classrooms(school_id,name,grade_name,classroom_status,created_at)
					VALUES (?,?,'1','active',NOW())
					""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
				insert.setLong(1, school); insert.setString(2, "Synthetic UI163 " + UUID.randomUUID()); insert.executeUpdate();
				try (var r = insert.getGeneratedKeys()) { assertTrue(r.next()); classroom = r.getLong(1); }
			}
		}
		var teacherBrowser = new LocalUi160RuntimeTest.Browser("teacher");
		assertEquals(302, teacherBrowser.get("/teacher/task").statusCode());
		teacherBrowser.login("review_teacher");
		String html = teacherBrowser.get("/teacher/task").body();
		assertTrue(html.contains("data-bs-target=\"#taskAssignmentModal-" + task + "\""));
		String table = html.substring(html.indexOf("id=\"taskTable\""), html.indexOf("</table>", html.indexOf("id=\"taskTable\"")));
		assertFalse(table.contains("name=\"newDueAt\""));
		String modal = modal(html, task);
		assertTrue(modal.contains(">期限・クラス設定</h2>"));
		assertTrue(modal.contains(">期限後の提出</label>"));
		var deadline = fields(modal, "extendTaskAssignmentDeadline");
		String due = scalar("SELECT DATE_FORMAT(DATE_ADD(due_at,INTERVAL 1 DAY),'%Y-%m-%dT%H:%i') FROM task_class_assignments WHERE task_class_assignment_id=" + assignment);
		deadline.put("newDueAt", due);
		var invalidCsrf = new HashMap<>(deadline); invalidCsrf.put("csrfToken", "invalid");
		assertEquals(403, teacherBrowser.post("/teacher/task", invalidCsrf).statusCode());
		var denied = new LocalUi160RuntimeTest.Browser("teacher"); denied.login("review_denied");
		assertEquals(403, denied.post("/teacher/task", deadline).statusCode());
		assertEquals(302, teacherBrowser.post("/teacher/task", deadline).statusCode());
		assertEquals(due, scalar("SELECT DATE_FORMAT(due_at,'%Y-%m-%dT%H:%i') FROM task_class_assignments WHERE task_class_assignment_id=" + assignment));
		var stale = new HashMap<>(deadline); stale.put("requestToken", UUID.randomUUID().toString());
		assertEquals(409, teacherBrowser.post("/teacher/task", stale).statusCode());
		String reloaded = teacherBrowser.get("/teacher/task").body();
		assertTrue(reloaded.contains("value=\"" + due + "\""));
		var add = fields(modal(reloaded, task), "addTaskClassAssignment");
		add.put("classroomId", Long.toString(classroom)); add.put("publishAt", ""); add.put("dueAt", ""); add.put("lateSubmissionPolicy", "deny");
		assertEquals(302, teacherBrowser.post("/teacher/task", add).statusCode());
		assertEquals("deny", scalar("SELECT late_submission_policy FROM task_class_assignments WHERE task_id=" + task + " AND classroom_id=" + classroom));
		assertTrue(teacherBrowser.get("/teacher/task").body().contains("対象クラスを追加しました"));
		String progress = teacherBrowser.get("/teacher/progress").body();
		assertTrue(progress.contains("class=\"badge progress-status-badge\""));
		assertFalse(progress.contains("bg-warning text-dark"));
		assertEquals(302, new LocalUi160RuntimeTest.Browser("teacher").get("/teacher/progress").statusCode());
		// The owner-labelled runtime teardown removes this synthetic schema after browser acceptance.
	}

	private static String modal(String html, long task) {
		return html.substring(html.indexOf("id=\"taskAssignmentModal-" + task + "\""));
	}

	private static Map<String, String> fields(String modal, String action) {
		var form = Pattern.compile("<form[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(modal);
		while (form.find()) {
			String body = form.group(1);
			if (!body.contains("value=\"" + action + "\"")) continue;
			var result = new HashMap<String, String>();
			var inputs = Pattern.compile("type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]*)\"").matcher(body);
			while (inputs.find()) result.put(inputs.group(1), inputs.group(2));
			return result;
		}
		throw new AssertionError("Missing modal form: " + action);
	}

	private static String scalar(String sql) throws Exception {
		try (var c = Client.createConnection(); var s = c.createStatement(); var r = s.executeQuery(sql)) {
			assertTrue(r.next()); return r.getString(1);
		}
	}
}
