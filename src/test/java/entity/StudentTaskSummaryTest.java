package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

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

	@Test
	void displaysUtcDeadlineInJapanTime() {
		StudentTaskSummary task = new StudentTaskSummary(1, 1, "Task", "", "", "",
				LocalDateTime.of(2026, 10, 17, 15, 30), "not_started", "not_started", "unsaved",
				"not_started", "not_applicable", null, null, ConsentStatus.AGREED, 5400L, null);

		assertEquals("10月18日 00:30", task.getDueAtDisplay());
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
