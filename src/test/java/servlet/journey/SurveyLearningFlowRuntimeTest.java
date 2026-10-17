package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;
import static servlet.journey.LearningFlowSupport.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import lib.mysql.Client;

class SurveyLearningFlowRuntimeTest {
	@Test
	void usesApprovedSurveyTemplateToSaveSubmitAndReadBackRealEvaluationsSurvey() throws Exception {
		guard();
		assertEquals("completed", text("SELECT evaluation_status FROM evaluations"));
		assertEquals(1, number("SELECT COUNT(*) FROM surveys WHERE survey_status='active'"));
		long assignmentId = number("SELECT task_class_assignment_id FROM task_class_assignments");
		long evaluationId = number("SELECT evaluation_id FROM evaluations");
		long surveyId = number("SELECT survey_id FROM surveys");
		List<Long> questionIds = numbers("SELECT question_id FROM survey_questions ORDER BY sort_order,question_id");
		assertEquals(6, questionIds.size());
		assertEquals(30, number("SELECT COUNT(*) FROM survey_question_options"));
		String path = "/student/survey?assignmentId=" + assignmentId + "&evaluationId=" + evaluationId;
		redirect(new Browser("student").get(path), "/student/account/login");
		Browser student = new Browser("student");
		student.login("synthetic-learning-student", "student", STUDENT_PASSWORD);
		assertTrue(student.get("/student/home").body().contains("アンケート"));
		var page = student.get(path);
		assertEquals(200, page.statusCode());
		assertTrue(page.body().contains(text("""
				SELECT summary_description FROM evaluation_dimension_results
				WHERE evaluation_id=%d ORDER BY evaluation_dimension_result_id LIMIT 1
				""".formatted(evaluationId))));
		long submissionId = number("SELECT submission_id FROM evaluations");
		assertNotEquals(submissionId, evaluationId, "Fixture must detect confusion between evaluation and submission IDs.");
		String evaluationLink = "/student/evaluation?assignmentId=" + assignmentId + "&submissionId=" + submissionId;
		assertTrue(page.body().replace("&amp;", "&").contains(evaluationLink));
		assertEquals(200, student.get(evaluationLink).statusCode());
		assertEquals(0, number("SELECT COUNT(*) FROM survey_responses"));
		var answer = fields("csrfToken", input(page.body(), "csrfToken"), "assignmentId", Long.toString(assignmentId),
				"evaluationId", Long.toString(evaluationId), "surveyId", Long.toString(surveyId), "action", "submit");
		for (long questionId : questionIds) {
			answer.put("answer_" + questionId, List.of("4"));
		}
		answer.put("answer_" + questionIds.getFirst(), List.of(""));
		assertEquals(400, student.post("/student/survey", answer).statusCode());
		assertEquals(0, number("SELECT COUNT(*) FROM survey_responses"));
		var invalid = new java.util.HashMap<>(answer);
		invalid.put("csrfToken", List.of("invalid"));
		assertEquals(403, student.post("/student/survey", invalid).statusCode());
		answer.put("answer_" + questionIds.getFirst(), List.of("4"));
		answer.put("action", List.of("draft"));
		answer.put("answer_" + questionIds.getFirst(), List.of("4"));
		redirect(student.post("/student/survey", answer), "/student/survey");
		assertEquals("in_progress", text("SELECT response_status FROM survey_responses"));
		assertEquals(6, number("SELECT COUNT(*) FROM survey_answers"));
		assertEquals(6, number("SELECT COUNT(*) FROM survey_answers WHERE answer_value='4'"));
		answer.put("action", List.of("submit"));
		redirect(student.post("/student/survey", answer), "/student/home");
		assertEquals("submitted", text("SELECT response_status FROM survey_responses"));
		assertEquals(evaluationId, number("SELECT evaluation_id FROM survey_responses"));
		page = student.get(path);
		assertTrue(page.body().contains("提出済み"));
		assertTrue(page.body().contains("data-read-only=\"true\""));
		assertTrue(page.body().contains("4"));
		assertTrue(page.body().contains("disabled"));
		answer.put("answer_" + questionIds.getFirst(), List.of("5"));
		redirect(student.post("/student/survey", answer), "/student/home");
		assertEquals(1, number("SELECT COUNT(*) FROM survey_responses"));
		assertEquals(6, number("SELECT COUNT(*) FROM survey_answers"));
		assertEquals("4", text("SELECT answer_value FROM survey_answers ORDER BY survey_answer_id LIMIT 1"));
		Browser declined = new Browser("student");
		declined.login("synthetic-learning-declined", "student", STUDENT_PASSWORD);
		assertEquals(403, declined.get(path).statusCode());
		student.logout("student");
		redirect(student.get(path), "/student/account/login");
	}

	private static List<Long> numbers(String sql) throws Exception {
		List<Long> values = new ArrayList<>();
		try (var connection = Client.createConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery(sql)) {
			while (rows.next()) values.add(rows.getLong(1));
		}
		return values;
	}
}
