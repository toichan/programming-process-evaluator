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
