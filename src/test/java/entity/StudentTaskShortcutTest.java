package entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentTaskShortcutTest {
	private StudentTaskShortcut shortcut(ConsentStatus consent, String progress, String evaluation,
			Long surveyId, Long evaluationId, Long submissionId, String response) {
		var task = new StudentTaskSummary(13, 14, "Dummy", "", "intermediate", "", null,
				"completed", progress, "saved", evaluation, response, surveyId, evaluationId,
				consent, null, submissionId);
		return new StudentTaskShortcut(new StudentHomePage(null, consent, List.of(task)), task);
	}
	@Test void linksRetainDistinctAssignmentSubmissionAndEvaluationIds() {
		var item = shortcut(ConsentStatus.AGREED, "submitted", "completed", 4L, 3L, 8L, "submitted");
		assertEquals(13, item.getTask().getTaskClassAssignmentId());
		assertEquals(8L, item.getTask().getLatestSubmissionId());
		assertEquals(3L, item.getSurvey().getEvaluationId());
		assertEquals(4L, item.getSurvey().getSurveyId());
		assertEquals("回答済み", item.getSurveyLabel());
		assertEquals("中級", item.getDifficultyLabel());
		assertEquals("is-intermediate", item.getDifficultyClass());
	}
	@Test void unavailableSurveyDoesNotInventALinkOrAnEvaluation() {
		for (ConsentStatus consent : List.of(ConsentStatus.DECLINED, ConsentStatus.WITHDRAWN)) {
			var item = shortcut(consent, "not_started", "not_started", 4L, null, null, "not_answered");
			assertFalse(item.getSurvey().isAvailable());
			assertEquals("回答不可", item.getSurveyLabel());
			assertEquals("未評価", item.getEvaluationLabel());
			assertEquals("未着手", item.getProgressLabel());
			assertNull(item.getTask().getLatestSubmissionId());
		}
		assertFalse(shortcut(ConsentStatus.AGREED, "completed", "completed",
				null, 3L, 8L, "not_answered").getSurvey().isAvailable());
	}
	@Test void latestPendingEvaluationDoesNotPreventPreviousCompletedSurvey() {
		var item = shortcut(ConsentStatus.AGREED, "submitted", "in_progress", 4L, 3L, 9L, "in_progress");
		assertEquals("評価待ち", item.getProgressLabel());
		assertEquals("評価中", item.getEvaluationLabel());
		assertTrue(item.getSurvey().isAvailable());
		assertEquals("回答中", item.getSurveyLabel());
	}
	@Test void displaysOnlyStoredScoresForCompletedLatestEvaluations() {
		var task = shortcut(ConsentStatus.AGREED, "completed", "completed", 4L, 3L, 8L, "submitted").getTask();
		var home = new StudentHomePage(null, ConsentStatus.AGREED, List.of(task));
		assertEquals("思: 4/5 態: 3/5", new StudentTaskShortcut(home, task, "思: 4/5 態: 3/5").getEvaluationLabel());
		var pending = shortcut(ConsentStatus.AGREED, "submitted", "in_progress", 4L, 3L, 9L, "submitted").getTask();
		assertEquals("評価中", new StudentTaskShortcut(home, pending, "思: 4/5 態: 3/5").getEvaluationLabel());
	}
}
