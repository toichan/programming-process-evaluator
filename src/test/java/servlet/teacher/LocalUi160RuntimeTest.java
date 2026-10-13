package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.teacher.StudentAccountManagementControl;
import control.teacher.TeacherDistributionControl;
import control.teacher.TeacherReviewFixture;
import entity.StudentAccountCreation;
import entity.StudentExerciseEntry;
import entity.TeacherDistributionInput;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class LocalUi160RuntimeTest {
	@Test void accountCredentialsRemainProtectedAndResetPersistsOnlyForSyntheticStudents() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("UI160_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		assertEquals("http://ppe-159-runtime:8080", System.getenv("EVALUATION_SURVEY_HTTP_BASE"));
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		long teacherId = number("SELECT user_id FROM users WHERE login_id='review_teacher'");
		try (var c = Client.createConnection(); var s = c.prepareStatement("""
				INSERT INTO teacher_feature_permissions(teacher_user_id,feature_code,is_enabled,updated_by_user_id,updated_at)
				VALUES (?, ?,TRUE,?,NOW())
				ON DUPLICATE KEY UPDATE is_enabled=TRUE,updated_by_user_id=VALUES(updated_by_user_id),updated_at=NOW()
				""")) {
			for (String feature : List.of("account-management", "code-distribution")) {
				s.setLong(1, teacherId); s.setString(2, feature); s.setLong(3, teacherId); s.executeUpdate();
			}
		}
		long school;
		long classroom;
		try (var c = Client.createConnection()) {
			school = insert(c, """
					INSERT INTO schools(school_code,name,school_status,security_level,created_at)
					VALUES (?,'Synthetic UI160 Level2','active',2,NOW())
					""", "ui160-" + java.util.UUID.randomUUID());
			classroom = insert(c, """
					INSERT INTO classrooms(school_id,name,grade_name,classroom_status,created_at)
					VALUES (?,'Synthetic UI160 Class','1','active',NOW())
					""", school);
			insert(c, """
					INSERT INTO teacher_school_permissions(teacher_user_id,school_id,access_status,updated_by_user_id,updated_at)
					VALUES (?,?,'enabled',?,NOW())
					""", teacherId, school, teacherId);
		}
		var teacher = new AuthenticatedUser(teacherId, "review_teacher", "review_teacher",
				UserType.TEACHER, false, "ui160-synthetic");
		var control = new StudentAccountManagementControl();
		var created = control.create(teacher, new StudentAccountCreation(school, classroom, "", 2));
		assertEquals(2, created.size());
		var target = control.list(teacher).stream().filter(a -> a.loginId().equals(created.getFirst().loginId())).findFirst().orElseThrow();
		var browser = new Browser("teacher");
		assertEquals(302, browser.get("/teacher/students").statusCode());
		browser.login("review_teacher");
		var page = browser.get("/teacher/students");
		assertEquals(200, page.statusCode());
		assertTrue(page.body().contains("<th>パスワード</th>"));
		var fields = new java.util.HashMap<>(Map.of("action", "reveal", "changeConfirmed", "yes",
				"userId", Long.toString(target.userId()), "version", Long.toString(target.version()),
				"csrfToken", input(page.body(), "csrfToken")));
		var invalid = new java.util.HashMap<>(fields); invalid.put("csrfToken", "invalid");
		assertEquals(403, browser.post("/teacher/students", invalid).statusCode());
		var reveal = browser.post("/teacher/students", fields);
		assertEquals(200, reveal.statusCode());
		assertEquals(created.getFirst().password(), new com.google.gson.Gson().fromJson(reveal.body(), com.google.gson.JsonObject.class).get("password").getAsString());
		fields.put("action", "reset"); fields.put("password", TeacherReviewFixture.PASSWORD);
		assertEquals(200, browser.post("/teacher/students", fields).statusCode());
		var reread = control.list(teacher).stream().filter(a -> a.userId() == target.userId()).findFirst().orElseThrow();
		assertTrue(reread.mustChangePassword());
		assertTrue(reread.passwordAvailable());
		assertTrue(reread.version() > target.version());
		assertEquals(200, browser.get("/teacher/students?view=detail&userId=" + target.userId()).statusCode());
		var outsider = new Browser("teacher"); outsider.login("review_outsider");
		var outsiderPage = outsider.get("/teacher/submissions");
		assertEquals(200, outsiderPage.statusCode());
		fields.put("csrfToken", input(outsiderPage.body(), "csrfToken"));
		assertEquals(403, outsider.post("/teacher/students", fields).statusCode());

		var distribution = new TeacherDistributionControl().schedule(teacher, new TeacherDistributionInput(
				0, 0, "Synthetic UI160 Completed", "Synthetic UI160 Root",
				List.of(new TeacherDistributionInput.TemplateItem("main.py", StudentExerciseEntry.Type.FILE, "print('synthetic')")),
				List.of(new TeacherDistributionInput.Target(classroom, null)), UUID.randomUUID().toString()));
		assertFalse(distribution.processingUnavailable());
		assertEquals(1, distribution.completedImmediateTargets());
		var history = browser.get("/teacher/distribution");
		assertEquals(200, history.statusCode());
		assertTrue(history.body().contains("data-status=\"completed\""));
		assertTrue(history.body().contains("配信完了"));
		assertTrue(history.body().contains("data-status=\"distributed\""));
		assertTrue(new dao.StudentTaskNavigationDao().findLatestScores(
				number("SELECT user_id FROM users WHERE login_id='review_student'")).isEmpty(),
				"An older completed evaluation must not replace the pending latest submission.");
	}

	static long number(String sql) throws Exception {
		try (var c = Client.createConnection(); var s = c.createStatement(); var r = s.executeQuery(sql)) {
			assertTrue(r.next()); return r.getLong(1);
		}
	}
	private static long insert(java.sql.Connection c, String sql, Object... values) throws Exception {
		try (var s = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
			try (var r = s.getGeneratedKeys()) { assertTrue(r.next()); return r.getLong(1); }
		}
	}
	static String input(String html, String name) {
		var match = Pattern.compile("name=\"" + name + "\"\\s+value=\"([^\"]+)\"").matcher(html);
		assertTrue(match.find()); return match.group(1);
	}
	static final class Browser {
		private final String portal;
		private final HttpClient client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
		Browser(String portal) { this.portal = portal; }
		private HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create(System.getenv("EVALUATION_SURVEY_HTTP_BASE") + path))
					.header("Host", portal + ".localhost:18092").timeout(Duration.ofSeconds(30));
		}
		HttpResponse<String> get(String path) throws Exception {
			return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
		}
		HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
			String body = fields.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
					+ "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		}
		void login(String login) throws Exception {
			String path = "/" + portal + "/account/login";
			var page = get(path);
			assertEquals(200, page.statusCode());
			assertEquals(302, post(path, Map.of("loginId", login, "password", TeacherReviewFixture.PASSWORD,
					"csrfToken", input(page.body(), "csrfToken"))).statusCode());
		}
	}
}
