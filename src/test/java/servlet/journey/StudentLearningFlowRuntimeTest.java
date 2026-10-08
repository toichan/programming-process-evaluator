package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;
import static servlet.journey.LearningFlowSupport.*;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentLearningFlowRuntimeTest {
	@Test
	void acceptsConsentSolvesExecutesSubmitsAndReadsRealEvaluation() throws Exception {
		guard();
		assertEquals("published", text("SELECT publication_status FROM tasks"));
		redirect(new Browser("student").get("/student/home"), "/student/account/login");
		Browser declined = new Browser("student");
		declined.completeStudentInitialPasswordChange("synthetic-learning-declined");
		var consent = declined.get("/student/consent");
		var choice = fields("csrfToken", input(consent.body(), "csrfToken"), "confirmRead", "yes",
				"documentVersionId", input(consent.body(), "documentVersionId"),
				"responseId", input(consent.body(), "responseId"), "consentDecision", "decline");
		redirect(declined.post("/student/consent", choice), "/student/home");
		String home = declined.get("/student/home").body();
		assertFalse(home.contains("研究協力には同意していません。"));
		assertTrue(home.contains(TITLE));
		assertEquals("declined", text("SELECT consent_status FROM consent_records WHERE user_id=3"));
		declined.logout("student");
		Browser student = new Browser("student");
		student.completeStudentInitialPasswordChange("synthetic-learning-student");
		consent = student.get("/student/consent");
		redirect(student.post("/student/consent", fields("csrfToken", input(consent.body(), "csrfToken"),
				"confirmRead", "yes", "documentVersionId", input(consent.body(), "documentVersionId"),
				"responseId", input(consent.body(), "responseId"), "consentDecision", "agree")), "/student/home");
		assertEquals("agreed", text("SELECT consent_status FROM consent_records WHERE user_id=2"));
		long assignmentId = number("SELECT task_class_assignment_id FROM task_class_assignments");
		String editorPath = "/student/editor?assignmentId=" + assignmentId;
		var editor = student.get(editorPath);
		assertEquals(200, editor.statusCode());
		String csrf = attribute(editor.body(), "id=\"csrfToken\"", "value");
		String token = attribute(editor.body(), "id=\"studentEditorPage\"", "data-draft-updated-at");
		String code = "n = int(input())\nprint(n * 2)\n";
		var invalid = fields("csrfToken", "invalid", "assignmentId", Long.toString(assignmentId),
				"code", code, "draftUpdatedAt", token, "eventType", "manual_save");
		assertEquals(403, student.post("/student/editor/draft", invalid).statusCode());
		var saved = json(student.post("/student/editor/draft", fields("csrfToken", csrf, "assignmentId",
				Long.toString(assignmentId), "code", "n = int(input())\n", "draftUpdatedAt", token,
				"eventType", "manual_save")));
		saved = json(student.post("/student/editor/draft", fields("csrfToken", csrf, "assignmentId",
				Long.toString(assignmentId), "code", code, "draftUpdatedAt", saved.get("updatedAt").getAsString(),
				"eventType", "manual_save")));
		token = saved.get("updatedAt").getAsString();
		assertTrue(student.get(editorPath).body().contains("print(n * 2)"));
		var run = json(student.post("/student/editor/run", fields("csrfToken", csrf, "assignmentId",
				Long.toString(assignmentId), "code", code)));
		String sessionId = run.get("sessionId").getAsString();
		assertEquals(202, student.post("/student/editor/session/input", fields("csrfToken", csrf, "assignmentId",
				Long.toString(assignmentId), "sessionId", sessionId, "line", "3")).statusCode());
		long deadline = System.nanoTime() + Duration.ofSeconds(70).toNanos();
		while ("running".equals(run.get("status").getAsString()) && System.nanoTime() < deadline) {
			Thread.sleep(250);
			run = json(student.get("/student/editor/session?assignmentId=" + assignmentId + "&sessionId=" + sessionId));
		}
		assertEquals("succeeded", run.get("status").getAsString());
		assertEquals("6\n", run.get("standardOutput").getAsString());
		assertEquals("3\n", run.get("standardInput").getAsString());
		var check = json(student.post("/student/editor/submission/check", fields("csrfToken", csrf,
				"assignmentId", Long.toString(assignmentId), "code", code, "draftUpdatedAt", token)));
		assertEquals(1, check.get("totalCount").getAsInt());
		assertEquals(1, check.get("passedCount").getAsInt());
		var submitted = json(student.post("/student/editor/submission/submit", fields("csrfToken", csrf,
				"assignmentId", Long.toString(assignmentId), "checkId", check.get("checkId").getAsString(),
				"requestKey", UUID.randomUUID().toString())));
		long submissionId = submitted.get("submissionId").getAsLong();
		assertEquals(code, text("SELECT submitted_code FROM submissions WHERE submission_id=" + submissionId));
		String evaluationPath = "/student/evaluation?assignmentId=" + assignmentId + "&submissionId=" + submissionId;
		deadline = System.nanoTime() + Duration.ofMinutes(14).toNanos();
		String status;
		do {
			status = text("SELECT evaluation_status FROM evaluations WHERE submission_id=" + submissionId);
			if ("completed".equals(status) || "failed".equals(status)) break;
			Thread.sleep(1000);
		} while (System.nanoTime() < deadline);
		assertEquals("completed", status);
		assertEquals("gemini-3.1-pro-preview", text("SELECT model_id FROM evaluation_requests"));
		assertEquals("succeeded", text("SELECT request_status FROM evaluation_requests"));
		assertEquals("validated", text("SELECT response_status FROM evaluation_responses"));
		assertEquals(number("SELECT active_prompt_version_id FROM tasks"),
				number("SELECT prompt_version_id FROM evaluations"));
		assertEquals(2, number("SELECT COUNT(*) FROM evaluation_dimension_results"));
		assertTrue(number("SELECT COUNT(*) FROM code_executions WHERE standard_output='6\n'") > 0);
		var evaluation = student.get(evaluationPath);
		assertEquals(200, evaluation.statusCode());
		assertTrue(evaluation.body().contains("思考力・判断力・表現力"));
		assertTrue(evaluation.body().contains("主体的に学習に取り組む態度"));
		assertTrue(evaluation.body().contains(text("SELECT feedback_summary FROM evaluations")));
		assertTrue(student.get(evaluationPath).body().contains("評価日時"));
		assertFalse(student.get(editorPath).body().contains("data-editable=\"true\""));
		assertTrue(student.get("/student/evaluation/log?submissionId=" + submissionId).body().contains("print(n * 2)"));
		declined.login("synthetic-learning-declined", "student", STUDENT_PASSWORD);
		redirect(declined.get(evaluationPath), "/student/home");
		student.logout("student");
		redirect(student.get(evaluationPath), "/student/account/login");
	}

	private static String attribute(String html, String anchor, String name) {
		int start = html.indexOf(anchor);
		assertTrue(start >= 0);
		String tag = html.substring(start, html.indexOf('>', start));
		var matcher = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(name) + "=\"([^\"]*)\"").matcher(tag);
		assertTrue(matcher.find());
		return decode(matcher.group(1));
	}
}
