package servlet.student;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import control.teacher.TeacherReviewFixture;
import lib.mysql.Client;

/** Real HTTP acceptance for student notifications against the isolated synthetic review fixture. */
class StudentNotificationRuntimeTest {
	private static final String BASE = "http://ppe-159-runtime:8080";
	private static final String FIXTURE_USERS =
			"SELECT user_id FROM users WHERE login_id IN ('review_student','review_withdrawn')";

	@Test void listOpenAndBulkReadFollowTheHttpContract() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("EVALUATION_SURVEY_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		assertEquals(BASE, System.getenv("EVALUATION_SURVEY_HTTP_BASE"));
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");

		long student = number("SELECT user_id FROM users WHERE login_id='review_student'");
		long withdrawn = number("SELECT user_id FROM users WHERE login_id='review_withdrawn'");
		long assignment = number("SELECT tp.task_class_assignment_id FROM evaluations e JOIN submissions s ON s.submission_id=e.submission_id JOIN task_participations tp ON tp.participation_id=s.participation_id WHERE e.evaluation_code='review-complete'");
		long evaluation = number("SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-complete'");
		long submission = number("SELECT submission_id FROM evaluations WHERE evaluation_code='review-complete'");
		long foreignEvaluation = number("SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-withdrawn'");
		long foreignSubmission = number("SELECT submission_id FROM evaluations WHERE evaluation_code='review-withdrawn'");
		long own;
		long expired;
		long foreign;
		long second;
		try (Connection c = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), c.getCatalog());
			c.setAutoCommit(false);
			try {
				reset(c);
				own = seed(c, student, evaluation, submission, assignment, "NOW(6) - INTERVAL 1 MINUTE");
				try (var statement = c.prepareStatement("""
						UPDATE student_notifications SET notification_type='evaluation_failed' WHERE notification_id=?
						""")) {
					statement.setLong(1, own);
					statement.executeUpdate();
				}
				expired = seed(c, student, syntheticEvaluation(c, submission, "expired"), submission, assignment, "NOW(6) - INTERVAL 91 DAY");
				second = seed(c, student, syntheticEvaluation(c, submission, "second"), submission, assignment, "NOW(6) - INTERVAL 2 MINUTE");
				foreign = seed(c, withdrawn, foreignEvaluation, foreignSubmission, assignment, "NOW(6) - INTERVAL 1 MINUTE");
				c.commit();
			} catch (Exception failure) {
				c.rollback();
				throw failure;
			}
		}
		try {
			assertEquals(302, new Browser().get("/student/notifications").statusCode(), "login is required");

			var browser = new Browser();
			browser.login("review_student");
			var list = browser.get("/student/notifications");
			assertEquals(200, list.statusCode());
			assertTrue(list.body().contains("name=\"notificationId\" value=\"" + own + "\""));
			assertTrue(list.body().contains("name=\"notificationId\" value=\"" + second + "\""));
			assertFalse(list.body().contains("name=\"notificationId\" value=\"" + expired + "\""), "expired is hidden");
			assertFalse(list.body().contains("name=\"notificationId\" value=\"" + foreign + "\""), "other student's row");
			assertFalse(list.body().contains("<script>synthetic</script>"), "title/message must be escaped");
			assertEquals(400, browser.get("/student/notifications?page=0").statusCode());
			assertEquals(200, browser.get("/student/notifications?page=2").statusCode());
			assertEquals(405, browser.get("/student/notifications/open?notificationId=" + own).statusCode());
			assertEquals(405, browser.get("/student/notifications/read-all").statusCode());
			assertEquals(1, count("notification_id=" + expired), "GET never purges");
			assertEquals(3, count("student_user_id=" + student + " AND read_at IS NULL"),
					"GET leaves both retained notifications and the expired unread row unchanged");

			String csrf = input(list.body(), "csrfToken");
			assertEquals(403, browser.post("/student/notifications/open",
					Map.of("csrfToken", "invalid", "notificationId", Long.toString(own))).statusCode());
			assertEquals(403, browser.post("/student/notifications/read-all", Map.of()).statusCode());
			assertEquals(404, browser.post("/student/notifications/open",
					Map.of("csrfToken", csrf, "notificationId", Long.toString(foreign))).statusCode());
			assertEquals(404, browser.post("/student/notifications/open",
					Map.of("csrfToken", csrf, "notificationId", Long.toString(expired))).statusCode());
			assertEquals(0, count("notification_id IN (" + own + "," + foreign + ") AND read_at IS NOT NULL"));

			Map<Long, String> memberships = memberships(student);
			assertFalse(memberships.isEmpty());
			try {
				setMemberships(memberships.keySet(), null);
				assertEquals(409, browser.post("/student/notifications/open",
						Map.of("csrfToken", csrf, "notificationId", Long.toString(own))).statusCode());
			} finally {
				setMemberships(memberships.keySet(), memberships);
			}
			assertEquals(0, count("notification_id=" + own + " AND read_at IS NOT NULL"), "409 does not mark read");

			var opened = browser.post("/student/notifications/open",
					Map.of("csrfToken", csrf, "notificationId", Long.toString(own)));
			assertEquals(303, opened.statusCode());
			String target = "/student/evaluation?assignmentId=" + assignment + "&submissionId=" + submission;
			assertTrue(opened.headers().firstValue("Location").orElseThrow().endsWith(target));
			assertEquals(200, browser.get(target).statusCode());
			assertEquals(1, count("notification_id=" + own + " AND read_at IS NOT NULL"));
			assertEquals(1, count("notification_id=" + second + " AND read_at IS NULL"), "only the clicked row is read");

			var readAll = browser.post("/student/notifications/read-all", Map.of("csrfToken", csrf));
			assertEquals(303, readAll.statusCode());
			assertTrue(readAll.headers().firstValue("Location").orElseThrow().endsWith("/student/notifications"));
			assertEquals(0, count("student_user_id=" + student + " AND read_at IS NULL"));
			assertEquals(0, count("notification_id=" + expired), "write purges the student's expired rows");
			assertEquals(1, count("notification_id=" + foreign + " AND read_at IS NULL"), "other student untouched");

			var other = new Browser();
			other.login("review_withdrawn");
			var otherList = other.get("/student/notifications");
			assertEquals(200, otherList.statusCode());
			assertEquals(404, other.post("/student/notifications/open",
					Map.of("csrfToken", input(otherList.body(), "csrfToken"), "notificationId", Long.toString(own))).statusCode());
		} finally {
			try (Connection c = Client.createConnection()) {
				reset(c);
			}
		}
	}

	/** N165-03: a task_published notice must redirect to the student's editor for that assignment. */
	@Test void taskPublishedNoticeRedirectsToTheEditor() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("EVALUATION_SURVEY_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		assertEquals(BASE, System.getenv("EVALUATION_SURVEY_HTTP_BASE"));
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");

		long assignment = number("SELECT tp.task_class_assignment_id FROM evaluations e JOIN submissions s ON s.submission_id=e.submission_id JOIN task_participations tp ON tp.participation_id=s.participation_id WHERE e.evaluation_code='review-complete'");
		long notificationId;
		try (Connection c = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), c.getCatalog());
			c.setAutoCommit(false);
			try {
				reset(c);
				try (var s = c.prepareStatement("""
						INSERT INTO student_notifications (student_user_id,notification_type,task_class_assignment_id,
						  title,message,created_at)
						SELECT user_id,'task_published',?,'Synthetic task published','Synthetic task published notice',
						  NOW(6) - INTERVAL 1 MINUTE
						FROM users WHERE login_id='review_student'""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
					s.setLong(1, assignment);
					assertEquals(1, s.executeUpdate());
					try (var keys = s.getGeneratedKeys()) {
						assertTrue(keys.next());
						notificationId = keys.getLong(1);
					}
				}
				c.commit();
			} catch (Exception failure) {
				c.rollback();
				throw failure;
			}
		}
		try {
			var browser = new Browser();
			browser.login("review_student");
			var list = browser.get("/student/notifications");
			assertEquals(200, list.statusCode());
			assertTrue(list.body().contains("name=\"notificationId\" value=\"" + notificationId + "\""));
			String csrf = input(list.body(), "csrfToken");

			var opened = browser.post("/student/notifications/open",
					Map.of("csrfToken", csrf, "notificationId", Long.toString(notificationId)));
			assertEquals(303, opened.statusCode());
			String target = "/student/editor?assignmentId=" + assignment;
			assertTrue(opened.headers().firstValue("Location").orElseThrow().endsWith(target));
			assertEquals(200, browser.get(target).statusCode());
			assertEquals(1, count("notification_id=" + notificationId + " AND read_at IS NOT NULL"));
		} finally {
			try (Connection c = Client.createConnection()) {
				reset(c);
			}
		}
	}

	private static void reset(Connection c) throws Exception {
		try (var s = c.createStatement()) {
			s.executeUpdate("DELETE FROM student_notifications WHERE student_user_id IN (" + FIXTURE_USERS + ")");
			s.executeUpdate("DELETE FROM evaluations WHERE evaluation_code LIKE 'notification-runtime-%'");
		}
	}

	/** Separate completed evaluation per extra notice: V25 requires real refs and one notice per evaluation. */
	private static long syntheticEvaluation(Connection c, long submissionId, String suffix) throws Exception {
		try (var s = c.prepareStatement("""
				INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,
				  auto_save_count,execution_count,created_at,completed_at)
				SELECT ?,?,rubric_id,'completed','initial',0,0,'2000-01-01','2000-01-01'
				FROM evaluations WHERE evaluation_code='review-complete'""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
			s.setString(1, "notification-runtime-" + suffix);
			s.setLong(2, submissionId);
			assertEquals(1, s.executeUpdate());
			try (var keys = s.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long seed(Connection c, long owner, long evaluationId, long submissionId, long assignmentId,
			String createdAt) throws Exception {
		try (var s = c.prepareStatement("""
				INSERT INTO student_notifications (student_user_id,notification_type,evaluation_id,submission_id,
				  task_class_assignment_id,title,message,created_at)
				VALUES (?,'evaluation_completed',?,?,?,'Synthetic notice','<script>synthetic</script>',""" + createdAt + ")",
				java.sql.Statement.RETURN_GENERATED_KEYS)) {
			s.setLong(1, owner);
			s.setLong(2, evaluationId);
			s.setLong(3, submissionId);
			s.setLong(4, assignmentId);
			s.executeUpdate();
			try (var keys = s.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static Map<Long, String> memberships(long student) throws Exception {
		var result = new java.util.LinkedHashMap<Long, String>();
		try (var c = Client.createConnection();
				var s = c.prepareStatement(
						"SELECT membership_id, membership_status FROM student_class_memberships WHERE student_user_id=?")) {
			s.setLong(1, student);
			try (var r = s.executeQuery()) {
				while (r.next()) {
					result.put(r.getLong(1), r.getString(2));
				}
			}
		}
		return result;
	}

	/** Sets the rows inactive, or restores each row's original status when {@code original} is given. */
	private static void setMemberships(java.util.Set<Long> ids, Map<Long, String> original) throws Exception {
		try (var c = Client.createConnection();
				var s = c.prepareStatement("UPDATE student_class_memberships SET membership_status=? WHERE membership_id=?")) {
			for (long id : ids) {
				s.setString(1, original == null ? "inactive" : original.get(id));
				s.setLong(2, id);
				assertEquals(1, s.executeUpdate());
			}
		}
	}

	private static long count(String where) throws Exception {
		return number("SELECT COUNT(*) FROM student_notifications WHERE " + where);
	}

	private static long number(String sql) throws Exception {
		try (var c = Client.createConnection(); var s = c.createStatement(); var r = s.executeQuery(sql)) {
			assertTrue(r.next(), sql);
			return r.getLong(1);
		}
	}

	private static String input(String html, String name) {
		var match = Pattern.compile("name=\"" + name + "\"\\s+value=\"([^\"]+)\"").matcher(html);
		assertTrue(match.find(), name);
		return match.group(1);
	}

	private static final class Browser {
		final HttpClient client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

		HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create(BASE + path))
					.header("Host", "student.localhost:18092").timeout(Duration.ofSeconds(30));
		}

		HttpResponse<String> get(String path) throws Exception {
			return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
		}

		HttpResponse<String> post(String path, Map<String, String> values) throws Exception {
			String body = new LinkedHashMap<>(values).entrySet().stream()
					.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
							+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
					.collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		}

		void login(String login) throws Exception {
			var page = get("/student/account/login");
			assertEquals(200, page.statusCode());
			assertEquals(302, post("/student/account/login", Map.of("loginId", login,
					"password", TeacherReviewFixture.PASSWORD, "csrfToken", input(page.body(), "csrfToken"))).statusCode());
		}
	}
}
