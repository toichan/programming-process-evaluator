package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;
import static servlet.journey.LearningFlowSupport.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import lib.mysql.Client;

class SurveyLearningFlowRuntimeTest {
	@Test
	void configuresOnlySurveyQuestionsThenSavesSubmitsAndReadsBackRealEvaluationsSurvey() throws Exception {
		guard();
		assertEquals("completed", text("SELECT evaluation_status FROM evaluations"));
		assertEquals(0, number("SELECT COUNT(*) FROM surveys"));
		try (var connection = Client.createConnection(); var statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO surveys(task_id,title,survey_status,created_at)
					SELECT task_id,'Synthetic learning feedback survey','active',NOW() FROM tasks
					""");
			statement.executeUpdate("""
					INSERT INTO survey_questions(survey_id,question_code,question_type,prompt_text,required,sort_order,question_status)
					SELECT survey_id,'SYNTHETIC-REFLECTION','text','Explain what you learned from this evaluation.',TRUE,1,'active'
					FROM surveys
					""");
		}
		long assignmentId = number("SELECT task_class_assignment_id FROM task_class_assignments");
		long evaluationId = number("SELECT evaluation_id FROM evaluations");
		long surveyId = number("SELECT survey_id FROM surveys");
		long questionId = number("SELECT question_id FROM survey_questions");
		String path = "/student/survey?assignmentId=" + assignmentId + "&evaluationId=" + evaluationId;
		redirect(new Browser("student").get(path), "/student/account/login");
		Browser student = new Browser("student");
		student.login("synthetic-learning-student", "student", STUDENT_PASSWORD);
		assertTrue(student.get("/student/home").body().contains("アンケート"));
		var page = student.get(path);
		assertEquals(200, page.statusCode());
		assertTrue(page.body().contains(text("SELECT feedback_summary FROM evaluations")));
		long submissionId = number("SELECT submission_id FROM evaluations");
		assertNotEquals(submissionId, evaluationId, "Fixture must detect confusion between evaluation and submission IDs.");
		String evaluationLink = "/student/evaluation?assignmentId=" + assignmentId + "&submissionId=" + submissionId;
		assertTrue(page.body().replace("&amp;", "&").contains(evaluationLink));
		assertEquals(200, student.get(evaluationLink).statusCode());
		assertEquals(0, number("SELECT COUNT(*) FROM survey_responses"));
		var answer = fields("csrfToken", input(page.body(), "csrfToken"), "assignmentId", Long.toString(assignmentId),
				"evaluationId", Long.toString(evaluationId), "surveyId", Long.toString(surveyId),
				"action", "submit", "answer_" + questionId, "");
		assertEquals(400, student.post("/student/survey", answer).statusCode());
		assertEquals(0, number("SELECT COUNT(*) FROM survey_responses"));
		var invalid = new java.util.HashMap<>(answer);
		invalid.put("csrfToken", List.of("invalid"));
		assertEquals(403, student.post("/student/survey", invalid).statusCode());
		answer.put("action", List.of("draft"));
		answer.put("answer_" + questionId, List.of("Synthetic draft: I checked the output."));
		redirect(student.post("/student/survey", answer), "/student/survey");
		assertEquals("in_progress", text("SELECT response_status FROM survey_responses"));
		assertTrue(student.get(path).body().contains("Synthetic draft: I checked the output."));
		answer.put("action", List.of("submit"));
		answer.put("answer_" + questionId, List.of("Synthetic final: I learned to multiply the input by two."));
		redirect(student.post("/student/survey", answer), "/student/survey");
		assertEquals("submitted", text("SELECT response_status FROM survey_responses"));
		assertEquals(evaluationId, number("SELECT evaluation_id FROM survey_responses"));
		page = student.get(path);
		assertTrue(page.body().contains("提出済み（確認のみ）"));
		assertTrue(page.body().contains("Synthetic final: I learned to multiply the input by two."));
		assertTrue(page.body().contains("readonly") || page.body().contains("disabled"));
		answer.put("answer_" + questionId, List.of("Duplicate must not overwrite."));
		redirect(student.post("/student/survey", answer), "/student/survey");
		assertEquals(1, number("SELECT COUNT(*) FROM survey_responses"));
		assertEquals("Synthetic final: I learned to multiply the input by two.", text("SELECT answer_value FROM survey_answers"));
		Browser declined = new Browser("student");
		declined.login("synthetic-learning-declined", "student", STUDENT_PASSWORD);
		assertEquals(403, declined.get(path).statusCode());
		student.logout("student");
		redirect(student.get(path), "/student/account/login");
	}
}
