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
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import control.teacher.TeacherReviewFixture;
import lib.mysql.Client;

class StudentEvaluationSurveyRuntimeTest {
	@Test void linksExplainUnavailableSurveyAndPersistAnswersWithoutOverwriting() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("EVALUATION_SURVEY_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		assertEquals("http://ppe-159-runtime:8080", System.getenv("EVALUATION_SURVEY_HTTP_BASE"));
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		long assignment = number("SELECT task_class_assignment_id FROM task_class_assignments");
		long evaluation = number("SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-complete'");
		long submission = number("SELECT submission_id FROM evaluations WHERE evaluation_code='review-complete'");
		var student = new Browser();
		student.login("review_student");
		String resultPath = "/student/evaluation?assignmentId=" + assignment + "&submissionId=" + submission;
		assertTrue(student.get(resultPath).body().contains("アンケートが設定されていません"));
		long survey;
		long question;
		try (var c = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), c.getCatalog());
			c.setAutoCommit(false);
			try {
				survey = insert(c, """
						INSERT INTO surveys(task_id,title,survey_status,created_at)
						SELECT task_id,'Synthetic UI acceptance survey','active',NOW() FROM task_class_assignments
						""");
				question = insert(c, """
						INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status)
						VALUES (?,'UI-REFLECTION','text','Synthetic: explain what you learned.',TRUE,1,'active')
						""", survey);
				c.commit();
			} catch (Exception failure) { c.rollback(); throw failure; }
		}
		String surveyPath = "/student/survey?assignmentId=" + assignment + "&evaluationId=" + evaluation + "&surveyId=" + survey;
		assertEquals(302, new Browser().get(surveyPath).statusCode());
		for (String path : new String[] {resultPath, "/student/evaluation/log?submissionId=" + submission}) {
			var page = student.get(path);
			assertEquals(200, page.statusCode());
			assertTrue(page.body().replace("&amp;", "&").contains(surveyPath));
			assertFalse(page.body().contains("アンケート機能は準備中"));
		}
		assertTrue(student.get("/student/evaluation?assignmentId=" + assignment + "&submissionId=2")
				.body().contains("この提出版の評価完了後にアンケートへ回答できます"));
		var page = student.get(surveyPath);
		assertEquals(200, page.statusCode());
		assertTrue(page.body().contains("id=\"stepIndicator\""));
		String surveyBody = page.body().replace("&amp;", "&");
		assertTrue(surveyBody.contains("href=\"/student/evaluation?assignmentId=" + assignment
				+ "&submissionId=" + submission + "\""));
		assertTrue(surveyBody.contains("href=\"/student/evaluation/log?submissionId=" + submission + "\""));
		var fields = new java.util.HashMap<>(Map.of("csrfToken", input(page.body(), "csrfToken"),
				"assignmentId", Long.toString(assignment), "evaluationId", Long.toString(evaluation),
				"surveyId", Long.toString(survey), "action", "submit", "answer_" + question, ""));
		assertEquals(400, student.post("/student/survey", fields).statusCode());
		fields.put("csrfToken", "invalid");
		assertEquals(403, student.post("/student/survey", fields).statusCode());
		fields.put("csrfToken", input(page.body(), "csrfToken"));
		fields.put("action", "draft"); fields.put("answer_" + question, "Synthetic saved reflection");
		assertEquals(302, student.post("/student/survey", fields).statusCode());
		var draftPage = student.get(surveyPath);
		assertTrue(draftPage.body().contains("下書きを保存しました"));
		assertTrue(draftPage.body().contains("Synthetic saved reflection"));
		fields.put("action", "submit");
		assertEquals(302, student.post("/student/survey", fields).statusCode());
		var submittedPage = student.get(surveyPath);
		assertTrue(student.get("/student/home").body().contains("アンケートの回答を保存しました"));
		assertTrue(submittedPage.body().contains("提出済み（「回答を修正」"));
		fields.put("answer_" + question, "Must not overwrite");
		assertEquals(302, student.post("/student/survey", fields).statusCode());
		assertTrue(student.get(surveyPath).body().contains("Synthetic saved reflection"));
		assertFalse(student.get(surveyPath).body().contains("Must not overwrite"));
		var other = new Browser(); other.login("review_withdrawn");
		assertEquals(403, other.get(surveyPath).statusCode());
	}

	private static long number(String sql) throws Exception {
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
	private static String input(String html, String name) {
		var match = Pattern.compile("name=\"" + name + "\"\\s+value=\"([^\"]+)\"").matcher(html);
		assertTrue(match.find()); return match.group(1);
	}
	private static final class Browser {
		final HttpClient client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
		HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create(System.getenv("EVALUATION_SURVEY_HTTP_BASE") + path))
					.header("Host", "student.localhost:18092").timeout(Duration.ofSeconds(30));
		}
		HttpResponse<String> get(String path) throws Exception {
			return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
		}
		HttpResponse<String> post(String path, Map<String, String> values) throws Exception {
			String body = values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
					+ "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
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
