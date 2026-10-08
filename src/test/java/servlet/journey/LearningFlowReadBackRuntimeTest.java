package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;
import static servlet.journey.LearningFlowSupport.*;

import org.junit.jupiter.api.Test;

class LearningFlowReadBackRuntimeTest {
	@Test
	void completedJourneyRemainsConsistentAfterApplicationRestart() throws Exception {
		guard();
		assertEquals("published", text("SELECT publication_status FROM tasks"));
		assertEquals("completed", text("SELECT evaluation_status FROM evaluations"));
		assertEquals("submitted", text("SELECT response_status FROM survey_responses"));
		assertEquals(0, number("SELECT COUNT(*) FROM student_profiles WHERE must_change_password=TRUE"));
		assertEquals(2, number("SELECT COUNT(*) FROM student_profiles WHERE first_login_status='completed'"));
		Browser student = new Browser("student");
		student.login("synthetic-learning-student", "student", STUDENT_PASSWORD);
		String home = student.get("/student/home").body();
		assertTrue(home.contains("status-completed"));
		assertFalse(home.contains("評価待ち"));
		long assignmentId = number("SELECT task_class_assignment_id FROM task_class_assignments");
		long submissionId = number("SELECT submission_id FROM evaluations");
		long evaluationId = number("SELECT evaluation_id FROM evaluations");
		assertNotEquals(submissionId, evaluationId);
		assertEquals(200, student.get("/student/evaluation?assignmentId=" + assignmentId
				+ "&submissionId=" + submissionId).statusCode());
		String survey = student.get("/student/survey?assignmentId=" + assignmentId
				+ "&evaluationId=" + evaluationId).body();
		assertTrue(survey.contains("提出済み（確認のみ）"));
		assertTrue(survey.contains("Synthetic final: I learned to multiply the input by two."));
		student.logout("student");
		Browser declined = new Browser("student");
		declined.login("synthetic-learning-declined", "student", STUDENT_PASSWORD);
		assertFalse(declined.get("/student/home").body().contains("研究協力には同意していません。"));
		declined.logout("student");
	}
}
