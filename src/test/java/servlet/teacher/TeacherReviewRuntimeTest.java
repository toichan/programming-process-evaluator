package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import control.teacher.TeacherReviewFixture;
import lib.mysql.Client;

class TeacherReviewRuntimeTest {
	@Test void coreTeacherReadbackUsesRealHttpDatabaseAndAuthenticatedPreview() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_REVIEW_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		assertEquals("http://ppe-teacher-review-runtime:8080", System.getenv("TEACHER_REVIEW_HTTP_BASE"),
				"Runtime test must not target a shared or production application.");
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		try (var c = Client.createConnection()) { assertEquals(System.getenv("DB_NAME"), c.getCatalog()); }
		long submission = number("SELECT submission_id FROM evaluations WHERE evaluation_code='review-complete'");
		long pending = number("SELECT submission_id FROM evaluations WHERE evaluation_code='review-pending'");
		var teacher = new Browser("teacher");
		teacher.login("review_teacher", "teacher");
		var home = teacher.get("/teacher/home");
		assertEquals(200, home.statusCode());
		assertFalse(home.body().contains("現在、利用できる実装済み機能の権限がありません"));
		for (String path : new String[] { "/teacher/submissions", "/teacher/evaluations" }) {
			var page = teacher.get(path);
			assertEquals(200, page.statusCode());
			assertTrue(page.body().contains("id=\"teacherReview\""));
			assertTrue(page.body().contains("href=\"/teacher/submissions\""));
			assertTrue(page.body().contains("href=\"/teacher/evaluations\""));
			assertEquals("no-store", page.headers().firstValue("Cache-Control").orElseThrow());
		}
		var submissions = teacher.json("/teacher/submissions?view=list").getAsJsonArray();
		assertEquals(3, submissions.size());
		var detail = teacher.json("/teacher/submissions?view=detail&submissionId=" + submission).getAsJsonObject();
		assertEquals("print(input())", detail.get("code").getAsString());
		assertEquals(2, detail.getAsJsonArray("checks").size());
		var evaluation = teacher.json("/teacher/evaluations?view=detail&submissionId=" + submission).getAsJsonObject();
		assertEquals("review-fixed-prompt-v1", evaluation.getAsJsonObject("evaluation").get("promptVersion").getAsString());
		assertEquals(2, evaluation.getAsJsonObject("evaluation").getAsJsonArray("dimensions").size());
		assertEquals(2, evaluation.getAsJsonObject("evaluation").getAsJsonArray("scores").size());
		assertEquals(2, evaluation.getAsJsonArray("logs").size());
		assertEquals("<script>dummy only</script>",
				evaluation.getAsJsonObject("evaluation").getAsJsonArray("reasons").get(0).getAsJsonObject().get("body").getAsString());
		assertEquals(submission, teacher.json("/teacher/evaluations?view=detail&submissionId=" + pending)
				.getAsJsonObject().getAsJsonObject("previousCompletedEvaluation").get("submissionId").getAsLong());
		assertEquals(1, teacher.json("/teacher/evaluations?view=list&consent=not_agreed").getAsJsonArray().size());
		assertEquals(0, teacher.json("/teacher/submissions?view=list&schoolId=999999").getAsJsonArray().size());
		assertEquals(400, teacher.get("/teacher/evaluations?view=list&sort=invalid").statusCode());
		assertEquals(400, teacher.get("/teacher/submissions?view=detail&submissionId=-1").statusCode());
		long auditCount = number("SELECT COUNT(*) FROM audit_logs WHERE feature_code='evaluation-review' AND action_type='csv_export'");
		var csv = teacher.get("/teacher/evaluations?view=csv");
		assertEquals(200, csv.statusCode());
		assertTrue(csv.body().contains("review_student"));
		assertFalse(csv.body().contains("review_withdrawn"));
		assertEquals(auditCount + 1, number("SELECT COUNT(*) FROM audit_logs WHERE feature_code='evaluation-review' AND action_type='csv_export'"));
		long logs = number("SELECT COUNT(*) FROM code_logs"), executions = number("SELECT COUNT(*) FROM code_executions");
		String csrf = field(teacher.get("/teacher/submissions").body(), "data-csrf-token");
		assertEquals(403, teacher.post("/teacher/submissions", Map.of("action", "preview", "submissionId", "" + submission, "code", "print(1)")).statusCode());
		var preview = teacher.post("/teacher/submissions", Map.of("action", "preview", "submissionId", "" + submission,
				"code", "print(input())", "standardInput", "dummy runtime", "csrfToken", csrf));
		assertEquals(200, preview.statusCode(), preview.body());
		var result = JsonParser.parseString(preview.body()).getAsJsonObject();
		assertEquals("succeeded", result.get("status").getAsString());
		assertEquals("dummy runtime\n", result.get("standardOutput").getAsString());
		assertEquals(413, teacher.post("/teacher/submissions", Map.of("action", "preview", "submissionId", "" + submission,
				"code", "x".repeat(65537), "csrfToken", csrf)).statusCode());
		assertEquals(logs, number("SELECT COUNT(*) FROM code_logs"));
		assertEquals(executions, number("SELECT COUNT(*) FROM code_executions"));
		assertEquals("print(input())", teacher.json("/teacher/submissions?view=detail&submissionId=" + submission).getAsJsonObject().get("code").getAsString());
		var outsider = new Browser("teacher"); outsider.login("review_outsider", "teacher");
		assertEquals(404, outsider.get("/teacher/submissions?view=detail&submissionId=" + submission).statusCode());
		assertEquals(404, outsider.get("/teacher/evaluations?view=detail&submissionId=" + submission).statusCode());
		var denied = new Browser("teacher"); denied.login("review_denied", "teacher");
		assertEquals(403, denied.get("/teacher/submissions").statusCode());
		assertEquals(403, denied.get("/teacher/evaluations?view=csv").statusCode());
		var student = new Browser("student"); student.login("review_student", "student"); student.host = "teacher";
		assertEquals(403, student.get("/teacher/submissions?view=detail&submissionId=" + submission).statusCode());
		var admin = new Browser("teacher"); admin.login("admin", "teacher");
		assertEquals(403, admin.get("/teacher/evaluations?view=detail&submissionId=" + submission).statusCode());
	}
	private static long number(String sql) throws Exception {
		try (var c = Client.createConnection(); var s = c.createStatement(); var r = s.executeQuery(sql)) {
			assertTrue(r.next(), "Expected existing guarded dummy fixture."); return r.getLong(1);
		}
	}
	private static String field(String html, String attribute) {
		var pattern = Pattern.compile(Pattern.quote(attribute) + "=\"([^\"]+)\"");
		var match = pattern.matcher(html); assertTrue(match.find(), "Missing " + attribute);
		return match.group(1);
	}
	private static final class Browser {
		String host;
		final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
		Browser(String host) { this.host = host; }
		HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create(System.getenv("TEACHER_REVIEW_HTTP_BASE") + path))
					.header("Host", host + ".localhost:18089").timeout(Duration.ofSeconds(75));
		}
		HttpResponse<String> get(String path) throws Exception { return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString()); }
		HttpResponse<String> post(String path, Map<String, String> values) throws Exception {
			String body = values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
					+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		}
		JsonElement json(String path) throws Exception {
			var response = get(path); assertEquals(200, response.statusCode(), path);
			return JsonParser.parseString(response.body());
		}
		void login(String login, String portal) throws Exception {
			String path = "/" + portal + "/account/login";
			var page = get(path); assertEquals(200, page.statusCode(), path);
			var match = Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(page.body());
			assertTrue(match.find());
			var result = post(path, Map.of("loginId", login, "password", TeacherReviewFixture.PASSWORD, "csrfToken", match.group(1)));
			assertEquals(302, result.statusCode(), "Dummy fixture login must succeed.");
		}
	}
}
