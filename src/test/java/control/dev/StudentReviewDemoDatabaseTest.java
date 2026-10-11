package control.dev;

import static org.junit.jupiter.api.Assertions.*;
import static control.dev.ReviewDemoBootstrap.number;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import dao.StudentEvaluationDao;
import dao.TeacherReviewDao;
import dao.StudentSurveyDao;
import lib.mysql.Client;

class StudentReviewDemoDatabaseTest {
	@Test void demoIsIdempotentReadableAndPreservesAccountAndConsent() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("PPE_SEED_REVIEW_DEMO")));
		ReviewDemoBootstrap.requireLocalTarget(System.getenv());
		List<String> before;
		try (var c = Client.createConnection()) { before = accountSnapshot(c); }
		StudentReviewDemoBootstrap.main(new String[0]);
		StudentReviewDemoBootstrap.main(new String[0]);
		try (var c = Client.createConnection()) {
			assertTrue(before.equals(accountSnapshot(c)), "Existing accounts, profiles and consent must stay unchanged.");
			long student = number(c, "SELECT user_id FROM users WHERE login_id='s008'");
			long assignment = number(c, """
					SELECT a.task_class_assignment_id FROM task_class_assignments a JOIN tasks t ON t.task_id=a.task_id
					WHERE t.task_code='LOCAL-S008-REVIEW-20261011'
					""");
			long submission = number(c, """
					SELECT s.submission_id FROM submissions s JOIN evaluations e ON e.submission_id=s.submission_id
					WHERE e.evaluation_code='LOCAL-S008-REVIEW-EVALUATION'
					""");
			assertEquals(1, number(c, "SELECT COUNT(*) FROM tasks WHERE task_code='LOCAL-S008-REVIEW-20261011'"));
			assertEquals(1, number(c, "SELECT COUNT(*) FROM evaluations WHERE evaluation_code='LOCAL-S008-REVIEW-EVALUATION'"));
			long survey = number(c, """
					SELECT sv.survey_id FROM surveys sv JOIN tasks t ON t.task_id=sv.task_id
					WHERE t.task_code='LOCAL-S008-REVIEW-20261011'
					  AND sv.title='【デモ・仮データ】s008 評価確認アンケート' AND sv.survey_status='active'
					""");
			assertEquals(6, number(c, "SELECT COUNT(*) FROM survey_questions WHERE survey_id=? AND question_status='active'", survey));
			assertEquals(30, number(c, """
					SELECT COUNT(*) FROM survey_question_options o JOIN survey_questions q ON q.question_id=o.question_id
					WHERE q.survey_id=?
					""", survey));
			if (number(c, """
					SELECT COUNT(*) FROM consent_records WHERE user_id=? AND consent_status='agreed'
					  AND consent_id=(SELECT MAX(consent_id) FROM consent_records WHERE user_id=?)
					""", student, student) == 1) {
				long evaluationId = number(c, "SELECT evaluation_id FROM evaluations WHERE evaluation_code='LOCAL-S008-REVIEW-EVALUATION'");
				var surveyPage = new StudentSurveyDao().findSurveyPage(student, assignment, evaluationId, survey).orElseThrow();
				assertEquals(6, surveyPage.getQuestions().size());
			}
			var evaluationDao = new StudentEvaluationDao();
			var page = evaluationDao.findEvaluationPage(student, assignment, submission).orElseThrow();
			assertTrue(page.getTaskTitle().contains("【デモ・仮データ】"));
			assertEquals("completed", page.getEvaluationStatus());
			assertEquals(2, page.getEvaluation().getDimensions().size());
			assertEquals(6, page.getEvaluation().getScores().size());
			var logs = evaluationDao.findCodeLogPage(student, submission).orElseThrow();
			assertTrue(logs.getEntries().stream().anyMatch(entry -> entry.getSnapshot().contains("print(a - b)")));
			assertTrue(logs.getEntries().stream().anyMatch(entry -> entry.getSnapshot().contains("print(a + b)")));
			assertTrue(evaluationDao.findEvaluationPage(student + 1, assignment, submission).isEmpty());
			assertTrue(evaluationDao.findCodeLogPage(student + 1, submission).isEmpty());
			c.setAutoCommit(false);
			long teacher = number(c, "SELECT user_id FROM users WHERE login_id='test-teacher'");
			assertTrue(new TeacherReviewDao().rows(c, teacher, true).stream()
					.anyMatch(row -> row.submissionId() == submission));
			c.rollback();
		}
	}

	private List<String> accountSnapshot(Connection c) throws Exception {
		var result = new ArrayList<String>();
		for (String table : List.of("users", "student_profiles", "consent_records", "survey_responses", "survey_answers")) {
			try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
				while (r.next()) {
					var row = new StringBuilder(table);
					for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
						row.append('|').append(r.getString(i));
					result.add(row.toString());
				}
			}
		}
		return result;
	}
}
