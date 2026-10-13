package servlet.student;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import control.teacher.TeacherReviewFixture;
import entity.TeacherSurveyResponse;
import lib.mysql.Client;

class SurveyFourStepRuntimeTest {
	@Test void fourGroupsPreserveSixAnswersAndReasonsAcrossSaveSubmitAndReadOnly() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("UI160_RUNTIME_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		long assignment;
		long evaluation;
		long survey;
		Map<String, String> answers = new HashMap<>();
		try (var c = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), c.getCatalog());
			try (var s = c.createStatement(); var r = s.executeQuery("SELECT task_class_assignment_id FROM task_class_assignments")) {
				assertTrue(r.next()); assignment = r.getLong(1);
			}
			try (var s = c.createStatement(); var r = s.executeQuery("SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-complete'")) {
				assertTrue(r.next()); evaluation = r.getLong(1);
			}
			survey = insert(c, """
					INSERT INTO surveys(task_id,title,survey_status,created_at)
					SELECT task_id,'Synthetic UI160 Four-Step Survey','active',NOW() FROM task_class_assignments
					""");
			int order = 0;
			for (String code : TeacherSurveyResponse.METRICS) {
				long question = insert(c, """
						INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status)
						VALUES (?,?,'rating',?,TRUE,?,'active')
						""", survey, code, "Synthetic " + code, ++order);
				for (int value = 1; value <= 5; value++) insert(c, """
						INSERT INTO survey_question_options(question_id,option_code,label,value,sort_order,option_status)
						VALUES (?,?,?,?,?,'active')
						""", question, "score-" + value, Integer.toString(value), Integer.toString(value), value);
				answers.put("answer_" + question, "4");
				answers.put("reason_" + question, "Synthetic persisted reason " + order);
			}
		}
		String path = "/student/survey?assignmentId=" + assignment + "&evaluationId=" + evaluation + "&surveyId=" + survey;
		var browser = new Browser();
		assertEquals(302, browser.get(path).statusCode());
		browser.login("review_student");
		var page = browser.get(path);
		assertEquals(200, page.statusCode());
		assertEquals(4, Pattern.compile("class=\"question-step").matcher(page.body()).results().count());
		var token = Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(page.body());
		assertTrue(token.find());
		Map<String, String> fields = new HashMap<>(Map.of("assignmentId", Long.toString(assignment),
				"evaluationId", Long.toString(evaluation), "surveyId", Long.toString(survey),
				"csrfToken", token.group(1), "action", "submit"));
		assertEquals(400, browser.post(fields).statusCode());
		fields.putAll(answers); fields.put("action", "draft");
		assertEquals(302, browser.post(fields).statusCode());
		assertTrue(browser.get(path).body().contains("Synthetic persisted reason 6"));
		fields.put("action", "submit");
		var submittedResponse = browser.post(fields);
		assertEquals(302, submittedResponse.statusCode());
		assertTrue(submittedResponse.headers().firstValue("location").orElseThrow().endsWith("/student/home"));
		var submitted = browser.get(path);
		assertTrue(submitted.body().contains("提出済み（「回答を修正」"));
		assertTrue(submitted.body().contains("id=\"editSurveyButton\""));
		assertTrue(submitted.body().contains("id=\"surveyAnswers\" class=\"survey-answer-fields\" disabled"));
		assertTrue(submitted.body().contains("おおむね妥当"));
		fields.put("action", "revise");
		fields.put("responseRevision", "1");
		String answerKey = answers.keySet().stream().filter(k -> k.startsWith("answer_")).findFirst().orElseThrow();
		fields.put(answerKey, "bad");
		assertEquals(400, browser.post(fields).statusCode());
		fields.put(answerKey, "5");
		assertEquals(302, browser.post(fields).statusCode());
		assertEquals(409, browser.post(fields).statusCode());
		assertTrue(browser.get(path).body().contains("name=\"responseRevision\" value=\"2\""));
		try (var c = Client.createConnection(); var s = c.prepareStatement("""
				SELECT COUNT(*), SUM(a.answer_value='4'), SUM(a.answer_reason LIKE 'Synthetic persisted reason %')
				FROM survey_answers a JOIN survey_responses r ON r.survey_response_id=a.survey_response_id
				WHERE r.survey_id=? AND r.evaluation_id=? AND r.response_status='submitted'
				""")) {
			s.setLong(1, survey); s.setLong(2, evaluation);
			try (var r = s.executeQuery()) { assertTrue(r.next()); assertEquals(6, r.getInt(1)); assertEquals(5, r.getInt(2)); assertEquals(6, r.getInt(3)); }
			try (var history = c.prepareStatement("""
					SELECT COUNT(*), MAX(JSON_LENGTH(v.answers_snapshot))
					FROM survey_response_revisions v JOIN survey_responses r
					  ON r.survey_response_id=v.survey_response_id
					WHERE r.survey_id=? AND r.evaluation_id=?
					""")) {
				history.setLong(1, survey); history.setLong(2, evaluation);
				try (var r = history.executeQuery()) {
					assertTrue(r.next()); assertEquals(1, r.getInt(1)); assertEquals(6, r.getInt(2));
				}
			}
		}
		var other = new Browser(); other.login("review_withdrawn");
		assertEquals(403, other.get(path).statusCode());
		var loginPage = other.get("/student/home");
		var otherToken = Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(loginPage.body());
		assertTrue(otherToken.find()); fields.put("csrfToken", otherToken.group(1)); fields.put("responseRevision", "2");
		assertEquals(403, other.post(fields).statusCode());
		try (var c = Client.createConnection(); var s = c.prepareStatement("""
				SELECT v.answers_snapshot FROM survey_response_revisions v
				JOIN survey_responses r ON r.survey_response_id=v.survey_response_id WHERE r.survey_id=?
				""")) {
			s.setLong(1, survey);
			try (var r = s.executeQuery()) {
				assertTrue(r.next());
				var snapshot = com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonArray();
				assertEquals(6, snapshot.size());
				for (var answer : snapshot) {
					assertEquals("4", answer.getAsJsonObject().get("value").getAsString());
					assertEquals(5, answer.getAsJsonObject().getAsJsonArray("options").size());
				}
			}
			try (var closed = c.prepareStatement("UPDATE surveys SET survey_status='archived' WHERE survey_id=?")) {
				closed.setLong(1, survey); assertEquals(1, closed.executeUpdate());
			}
		}
		assertFalse(browser.get(path).body().contains("id=\"editSurveyButton\""));
		fields.put("csrfToken", token.group(1));
		assertEquals(403, browser.post(fields).statusCode());
	}
	private long insert(java.sql.Connection c, String sql, Object... values) throws Exception {
		try (var s = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
			try (var r = s.getGeneratedKeys()) { assertTrue(r.next()); return r.getLong(1); }
		}
	}
	private static final class Browser {
		final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
		HttpRequest.Builder request(String path) {
			String base = System.getenv("EVALUATION_SURVEY_HTTP_BASE");
			assertEquals("http://ppe-159-runtime:8080", base);
			return HttpRequest.newBuilder(URI.create(base + path)).header("Host", "student.localhost:18092");
		}
		HttpResponse<String> get(String path) throws Exception {
			return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
		}
		HttpResponse<String> post(Map<String, String> values) throws Exception { return post("/student/survey", values); }
		HttpResponse<String> post(String path, Map<String, String> values) throws Exception {
			String body = values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
					+ "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
		}
		void login(String id) throws Exception {
			var page = get("/student/account/login");
			var token = Pattern.compile("name=\"csrfToken\"\\s+value=\"([^\"]+)\"").matcher(page.body());
			assertTrue(token.find());
			assertEquals(302, post("/student/account/login", Map.of("loginId", id,
					"password", TeacherReviewFixture.PASSWORD, "csrfToken", token.group(1))).statusCode());
		}
	}
}
