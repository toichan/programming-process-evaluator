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
		var file = teacher.get("/teacher/submissions?view=file&submissionId=" + submission);
		assertEquals(200, file.statusCode());
		assertEquals("print(input())", file.body());
		assertTrue(file.headers().firstValue("Content-Disposition").orElseThrow().contains("filename*=UTF-8''"));
		assertEquals(400, teacher.get("/teacher/submissions?view=file").statusCode());
		assertEquals(400, teacher.get("/teacher/submissions?view=zip&schoolId=999999").statusCode());
		for (String sort : new String[] { "student", "school", "class", "task", "difficulty", "match", "submitted", "consent" }) {
			for (String direction : new String[] { "asc", "desc" }) {
				String suffix = "&sort=" + sort + "&direction=" + direction;
				var list = teacher.json("/teacher/submissions?view=list" + suffix).getAsJsonArray();
				var submissionCsv = teacher.get("/teacher/submissions?view=csv" + suffix);
				assertEquals(200, submissionCsv.statusCode());
				assertTrue(submissionCsv.body().startsWith("\uFEFF"));
				assertFalse(submissionCsv.body().contains("review_withdrawn"));
				var csvLines = submissionCsv.body().split("\r\n");
				var agreedRows = new java.util.ArrayList<JsonObject>();
				for (var item : list) if (item.getAsJsonObject().get("consent").getAsString().equals("agreed")) agreedRows.add(item.getAsJsonObject());
				assertEquals(agreedRows.size() + 1, csvLines.length);
				for (int i = 0; i < agreedRows.size(); i++) {
					assertTrue(csvLines[i + 1].contains(",\"" + agreedRows.get(i).get("submissionId").getAsLong() + "\",\"" + agreedRows.get(i).get("revision").getAsInt() + "\","));
				}
			}
		}
		var archive = teacher.client.send(teacher.request("/teacher/submissions?view=zip").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(200, archive.statusCode());
		int files = 0;
		try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive.body()), StandardCharsets.UTF_8)) {
			while (zip.getNextEntry() != null) { files++; assertTrue(zip.readAllBytes().length > 0); }
		}
		assertEquals(3, files);
		var evaluation = teacher.json("/teacher/evaluations?view=detail&submissionId=" + submission).getAsJsonObject();
		assertEquals("review-fixed-prompt-v1", evaluation.getAsJsonObject("evaluation").get("promptVersion").getAsString());
		assertEquals(2, evaluation.getAsJsonObject("evaluation").getAsJsonArray("dimensions").size());
		assertEquals(2, evaluation.getAsJsonObject("evaluation").getAsJsonArray("scores").size());
		assertEquals(2, evaluation.getAsJsonArray("logs").size());
		for (String view : new String[] { "file", "logs" }) {
			var output = teacher.get("/teacher/evaluations?view=" + view + "&submissionId=" + submission);
			assertEquals(200, output.statusCode(), output.body());
			assertTrue(output.headers().firstValue("Content-Disposition").orElseThrow().contains("filename*=UTF-8''"));
			var payload = JsonParser.parseString(output.body()).getAsJsonObject();
			assertEquals(submission, payload.getAsJsonObject("row").get("submissionId").getAsLong());
			assertEquals(2, payload.getAsJsonArray("logs").size());
		}
		for (String view : new String[] { "zip", "logs-zip" }) {
			var output = teacher.client.send(teacher.request("/teacher/evaluations?view=" + view).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			assertEquals(200, output.statusCode());
			var names = new java.util.HashSet<String>();
			try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(output.body()), StandardCharsets.UTF_8)) {
				java.util.zip.ZipEntry entry;
				while ((entry = zip.getNextEntry()) != null) {
					assertTrue(names.add(entry.getName()), "Evaluation versions must not overwrite one another");
					assertTrue(entry.getName().endsWith(".json"));
					assertTrue(JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isJsonObject());
				}
			}
			assertEquals(teacher.json("/teacher/evaluations?view=list").getAsJsonArray().size(), names.size());
			assertEquals(400, teacher.get("/teacher/evaluations?view=" + view + "&schoolId=999999").statusCode());
		}
		for (String sort : new String[] { "student", "school", "class", "task", "difficulty", "thinking", "attitude", "evaluated", "consent" }) {
			for (String direction : new String[] { "asc", "desc" }) {
				String suffix = "&sort=" + sort + "&direction=" + direction;
				var list = teacher.json("/teacher/evaluations?view=list" + suffix).getAsJsonArray();
				var output = teacher.get("/teacher/evaluations?view=csv" + suffix);
				assertEquals(200, output.statusCode());
				var lines = output.body().split("\r\n");
				int index = 1;
				for (var item : list) {
					var row = item.getAsJsonObject();
					if (row.get("consent").getAsString().equals("agreed"))
						assertTrue(lines[index++].contains(",\"" + row.get("evaluationId").getAsLong() + "\","));
				}
				assertEquals(index, lines.length);
			}
		}
		for (String expression : new String[] { "=4", ">3", ">=3, <2", "<=5" }) {
			String suffix = "&thinking=" + URLEncoder.encode(expression, StandardCharsets.UTF_8);
			var list = teacher.json("/teacher/evaluations?view=list" + suffix).getAsJsonArray();
			for (var item : list) assertTrue(entity.TeacherSurveyFilter.matches(expression, item.getAsJsonObject().get("thinkingScore").getAsDouble()));
			assertEquals(200, teacher.get("/teacher/evaluations?view=csv" + suffix).statusCode());
		}
		assertEquals(400, teacher.get("/teacher/evaluations?view=list&thinking=invalid").statusCode());
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

		long progressAssignment = number("""
				SELECT tca.task_class_assignment_id
				FROM task_class_assignments tca
				JOIN tasks t ON t.task_id=tca.task_id
				WHERE t.task_code='review-task'
				""");
		long progressStudent = number("SELECT user_id FROM users WHERE login_id='review_student'");
		var progressDetailPath = "/teacher/progress?view=detail&assignmentId=" + progressAssignment
				+ "&studentUserId=" + progressStudent;
		var progressOwner = teacher.json(progressDetailPath).getAsJsonObject();
		assertEquals("review_student", progressOwner.getAsJsonObject("row").get("studentLoginId").getAsString());
		assertNotNull(progressOwner.getAsJsonObject("latestCode"));
		var progressPage = teacher.get("/teacher/progress");
		assertEquals(200, progressPage.statusCode());
		assertTrue(progressPage.body().contains("data-progress-row"));
		assertTrue(progressPage.body().contains("review_student"));
		var progressDownload = teacher.get("/teacher/progress?view=download&assignmentId=" + progressAssignment
				+ "&studentUserId=" + progressStudent);
		assertEquals(200, progressDownload.statusCode());
		assertEquals(progressOwner.getAsJsonObject("latestCode").get("code").getAsString(),
				progressDownload.body());

		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions SET is_enabled=FALSE
						WHERE teacher_user_id=? AND feature_code='task-progress'
						""")) {
			statement.setLong(1, number("SELECT user_id FROM users WHERE login_id='review_teacher'"));
			assertEquals(1, statement.executeUpdate());
		}
		assertEquals(403, teacher.get("/teacher/progress").statusCode());
		var revokedProgressDetail = teacher.get(progressDetailPath);
		assertEquals(403, revokedProgressDetail.statusCode());
		assertFalse(revokedProgressDetail.body().contains("review_student"));
		var revokedProgressDownload = teacher.get("/teacher/progress?view=download&assignmentId="
				+ progressAssignment + "&studentUserId=" + progressStudent);
		assertEquals(403, revokedProgressDownload.statusCode());
		assertFalse(revokedProgressDownload.body().contains(
				progressOwner.getAsJsonObject("latestCode").get("code").getAsString()));
		assertEquals(200, teacher.get("/teacher/submissions").statusCode(),
				"Revoking progress access must not revoke separate review permissions.");

		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions SET is_enabled=TRUE
						WHERE teacher_user_id=? AND feature_code='task-progress'
						""")) {
			statement.setLong(1, number("SELECT user_id FROM users WHERE login_id='review_teacher'"));
			assertEquals(1, statement.executeUpdate());
		}
		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_school_permissions SET access_status='disabled'
						WHERE teacher_user_id=? AND school_id=(
							SELECT school_id FROM tasks WHERE task_id=(
								SELECT task_id FROM task_class_assignments
								WHERE task_class_assignment_id=?
							)
						)
						""")) {
			statement.setLong(1, number("SELECT user_id FROM users WHERE login_id='review_teacher'"));
			statement.setLong(2, progressAssignment);
			assertEquals(1, statement.executeUpdate());
		}
		var revokedSchoolProgressPage = teacher.get("/teacher/progress");
		assertEquals(200, revokedSchoolProgressPage.statusCode());
		assertFalse(revokedSchoolProgressPage.body().contains("data-progress-row"));
		assertFalse(revokedSchoolProgressPage.body().contains("review_student"));
		assertEquals(404, teacher.get(progressDetailPath).statusCode());
		var revokedSchoolProgressDownload = teacher.get("/teacher/progress?view=download&assignmentId="
				+ progressAssignment + "&studentUserId=" + progressStudent);
		assertEquals(404, revokedSchoolProgressDownload.statusCode());
		assertFalse(revokedSchoolProgressDownload.body().contains(
				progressOwner.getAsJsonObject("latestCode").get("code").getAsString()));
		assertEquals(200, teacher.get("/teacher/submissions").statusCode(),
				"Revoking school access must not revoke separate review permissions.");

		var outsider = new Browser("teacher"); outsider.login("review_outsider", "teacher");
		var outsiderProgressPage = outsider.get("/teacher/progress");
		assertEquals(200, outsiderProgressPage.statusCode(),
				"Outsider has the progress feature enabled for a different school.");
		assertFalse(outsiderProgressPage.body().contains("data-progress-row"));
		assertFalse(outsiderProgressPage.body().contains("review_student"));
		assertEquals(404, outsider.get(progressDetailPath).statusCode());
		var outsiderProgressDownload = outsider.get("/teacher/progress?view=download&assignmentId="
				+ progressAssignment + "&studentUserId=" + progressStudent);
		assertEquals(404, outsiderProgressDownload.statusCode());
		assertFalse(outsiderProgressDownload.body().contains("print(input())"));
		assertTrue(outsider.json("/teacher/submissions?view=list").getAsJsonArray().isEmpty());
		assertEquals(404, outsider.get("/teacher/submissions?view=detail&submissionId=" + submission).statusCode());
		assertEquals(404, outsider.get("/teacher/submissions?view=file&submissionId=" + submission).statusCode());
		long submissionExportAudits = number("SELECT COUNT(*) FROM audit_logs WHERE feature_code='submission-review' AND action_type='csv_export'");
		long zeroRowSubmissionExportAudits = number("""
				SELECT COUNT(*) FROM audit_logs
				WHERE feature_code='submission-review' AND action_type='csv_export'
				  AND detail='Teacher review CSV; latest consent granted only; rows=0'
				""");
		var outsiderSubmissionCsv = outsider.get("/teacher/submissions?view=csv");
		assertEquals(200, outsiderSubmissionCsv.statusCode());
		assertFalse(outsiderSubmissionCsv.body().contains("review_student"));
		assertFalse(outsiderSubmissionCsv.body().contains("print(input())"));
		assertEquals(submissionExportAudits + 1, number(
				"SELECT COUNT(*) FROM audit_logs WHERE feature_code='submission-review' AND action_type='csv_export'"));
		assertEquals(zeroRowSubmissionExportAudits + 1, number("""
				SELECT COUNT(*) FROM audit_logs
				WHERE feature_code='submission-review' AND action_type='csv_export'
				  AND detail='Teacher review CSV; latest consent granted only; rows=0'
				"""));
		var outsiderSubmissionZip = outsider.client.send(outsider.request("/teacher/submissions?view=zip")
				.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		assertEquals(400, outsiderSubmissionZip.statusCode());
		var deniedSubmissionArchive = new String(outsiderSubmissionZip.body(), StandardCharsets.UTF_8);
		assertFalse(deniedSubmissionArchive.contains("review_student"));
		assertFalse(deniedSubmissionArchive.contains("print(input())"));
		assertEquals(404, outsider.get("/teacher/evaluations?view=detail&submissionId=" + submission).statusCode());
		assertEquals(404, outsider.get("/teacher/evaluations?view=file&submissionId=" + submission).statusCode());
		assertEquals(404, outsider.get("/teacher/evaluations?view=logs&submissionId=" + submission).statusCode());
		assertTrue(outsider.json("/teacher/evaluations?view=list").getAsJsonArray().isEmpty());
		long evaluationExportAudits = number("SELECT COUNT(*) FROM audit_logs WHERE feature_code='evaluation-review' AND action_type='csv_export'");
		long zeroRowExportAudits = number("""
				SELECT COUNT(*) FROM audit_logs
				WHERE feature_code='evaluation-review' AND action_type='csv_export'
				  AND detail='Teacher review CSV; latest consent granted only; rows=0'
				""");
		var outsiderEvaluationCsv = outsider.get("/teacher/evaluations?view=csv");
		assertEquals(200, outsiderEvaluationCsv.statusCode());
		assertFalse(outsiderEvaluationCsv.body().contains("review_student"));
		assertFalse(outsiderEvaluationCsv.body().contains("print(input())"));
		for (String view : new String[] { "zip", "logs-zip" }) {
			var outsiderArchive = outsider.client.send(outsider.request("/teacher/evaluations?view=" + view)
					.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			assertEquals(400, outsiderArchive.statusCode(), view);
			var deniedArchive = new String(outsiderArchive.body(), StandardCharsets.UTF_8);
			assertFalse(deniedArchive.contains("review_student"), view);
			assertFalse(deniedArchive.contains("print(input())"), view);
		}
		assertEquals(evaluationExportAudits + 1, number(
				"SELECT COUNT(*) FROM audit_logs WHERE feature_code='evaluation-review' AND action_type='csv_export'"),
				"The empty CSV export attempt should be audited without exporting out-of-scope data.");
		assertEquals(zeroRowExportAudits + 1, number("""
				SELECT COUNT(*) FROM audit_logs
				WHERE feature_code='evaluation-review' AND action_type='csv_export'
				  AND detail='Teacher review CSV; latest consent granted only; rows=0'
				"""));
		var denied = new Browser("teacher"); denied.login("review_denied", "teacher");
		assertEquals(403, denied.get("/teacher/submissions").statusCode());
		assertEquals(403, denied.get("/teacher/submissions?view=zip").statusCode());
		assertEquals(403, denied.get("/teacher/submissions?view=csv").statusCode());
		assertEquals(403, denied.get("/teacher/evaluations?view=csv").statusCode());
		var student = new Browser("student"); student.login("review_student", "student"); student.host = "teacher";
		assertEquals(403, student.get("/teacher/submissions?view=detail&submissionId=" + submission).statusCode());
		var admin = new Browser("teacher"); admin.login("admin", "teacher");
		assertEquals(403, admin.get("/teacher/evaluations?view=detail&submissionId=" + submission).statusCode());

		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions SET is_enabled=FALSE
						WHERE teacher_user_id=? AND feature_code='evaluation-review'
						""")) {
			statement.setLong(1, number("SELECT user_id FROM users WHERE login_id='review_teacher'"));
			assertEquals(1, statement.executeUpdate());
		}
		for (String path : new String[] {
				"/teacher/evaluations",
				"/teacher/evaluations?view=list",
				"/teacher/evaluations?view=detail&submissionId=" + submission,
				"/teacher/evaluations?view=file&submissionId=" + submission,
				"/teacher/evaluations?view=logs&submissionId=" + submission,
				"/teacher/evaluations?view=csv",
				"/teacher/evaluations?view=zip",
				"/teacher/evaluations?view=logs-zip" }) {
			var response = teacher.get(path);
			assertEquals(403, response.statusCode(), path);
			assertFalse(response.body().contains("review_student"), path);
			assertFalse(response.body().contains("print(input())"), path);
		}
		assertEquals(200, teacher.get("/teacher/submissions").statusCode(),
				"Revoking evaluation access must not revoke separate submission review permissions.");

		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions SET is_enabled=FALSE
						WHERE teacher_user_id=? AND feature_code='submission-review'
						""")) {
			statement.setLong(1, number("SELECT user_id FROM users WHERE login_id='review_teacher'"));
			assertEquals(1, statement.executeUpdate());
		}
		logs = number("SELECT COUNT(*) FROM code_logs");
		executions = number("SELECT COUNT(*) FROM code_executions");
		for (String path : new String[] {
				"/teacher/submissions",
				"/teacher/submissions?view=list",
				"/teacher/submissions?view=detail&submissionId=" + submission,
				"/teacher/submissions?view=file&submissionId=" + submission,
				"/teacher/submissions?view=csv",
				"/teacher/submissions?view=zip" }) {
			var response = teacher.get(path);
			assertEquals(403, response.statusCode(), path);
			assertFalse(response.body().contains("review_student"), path);
			assertFalse(response.body().contains("print(input())"), path);
		}
		assertEquals(403, teacher.post("/teacher/submissions", Map.of(
				"action", "preview",
				"submissionId", Long.toString(submission),
				"code", "print('must not run')",
				"standardInput", "",
				"csrfToken", csrf)).statusCode());
		assertEquals(logs, number("SELECT COUNT(*) FROM code_logs"));
		assertEquals(executions, number("SELECT COUNT(*) FROM code_executions"));
		restoreReviewOwnerAccess();
		assertEquals(3, teacher.json("/teacher/submissions?view=list").getAsJsonArray().size(),
				"Successful runtime verification must leave the disposable browser fixture usable.");
	}
	private static void restoreReviewOwnerAccess() throws Exception {
		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_school_permissions
						SET access_status='enabled', updated_by_user_id=teacher_user_id, updated_at=NOW()
						WHERE teacher_user_id=(SELECT user_id FROM users WHERE login_id='review_teacher')
						  AND school_id=(SELECT school_id FROM schools WHERE school_code='review-dummy')
						""")) {
			assertEquals(1, statement.executeUpdate());
		}
		try (var connection = Client.createConnection();
				var statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions
						SET is_enabled=TRUE, updated_by_user_id=teacher_user_id, updated_at=NOW()
						WHERE teacher_user_id=(SELECT user_id FROM users WHERE login_id='review_teacher')
						  AND feature_code IN ('submission-review','evaluation-review','task-progress')
						""")) {
			assertEquals(3, statement.executeUpdate());
		}
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
