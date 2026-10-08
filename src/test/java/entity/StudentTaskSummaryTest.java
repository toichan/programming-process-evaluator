package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StudentTaskSummaryTest {
	@Test
	void agreedStudentCanSeeSurveyStatus() {
		assertEquals("in_progress", summary(ConsentStatus.AGREED).getSurveyStatus());
	}

	@Test
	void surveyStatusIsHiddenUntilConsentIsAgreed() {
		assertEquals("not_applicable", summary(ConsentStatus.UNCONFIRMED).getSurveyStatus());
		assertEquals("not_applicable", summary(ConsentStatus.DECLINED).getSurveyStatus());
		assertEquals("not_applicable", summary(ConsentStatus.WITHDRAWN).getSurveyStatus());
		assertEquals("not_applicable", summary(null).getSurveyStatus());
	}

	@Test
	void completedEvaluationIsNotDisplayedAsWaitingOnStudentHome() {
		StudentTaskSummary task = new StudentTaskSummary(1, 1, "Task", "", "", "", null,
				"submitted", "awaiting_evaluation", "saved", "completed", "submitted",
				1L, 100L, ConsentStatus.AGREED, null, 1L);
		assertEquals("completed", task.getProgressStatus());
		assertEquals(1, new StudentHomePage(null, ConsentStatus.AGREED, java.util.List.of(task)).getSubmittedTaskCount());
	}

	private static StudentTaskSummary summary(ConsentStatus consentStatus) {
		return new StudentTaskSummary(
				1,
				1,
				"Task",
				"",
				"",
				"",
				null,
				"not_started",
				"not_started",
				"unsaved",
				"not_started",
				"in_progress",
				1L,
				1L,
				consentStatus,
				null,
				null);
	}
}
