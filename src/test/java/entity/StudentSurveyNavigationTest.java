package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class StudentSurveyNavigationTest {
	private StudentHomePage home(ConsentStatus consent, Long surveyId, String responseStatus) {
		var task = new StudentTaskSummary(13, 14, "Demo", "", "beginner", "", null,
				"completed", "completed", "saved", "completed", responseStatus,
				surveyId, 3L, consent, null, 8L);
		return new StudentHomePage(null, consent, List.of(task));
	}

	@Test void consentAndConfigurationHaveDifferentReasons() {
		assertTrue(StudentSurveyNavigation.forEvaluation(home(ConsentStatus.DECLINED, 4L, "not_answered"),
				13, 3L, true).getUnavailableReason().contains("研究同意"));
		assertTrue(StudentSurveyNavigation.forEvaluation(home(ConsentStatus.AGREED, null, "not_applicable"),
				13, 3L, true).getUnavailableReason().contains("設定されていません"));
	}

	@Test void requiresCompletedMatchingEvaluationAndAssignment() {
		var home = home(ConsentStatus.AGREED, 4L, "not_answered");
		assertFalse(StudentSurveyNavigation.forEvaluation(home, 13, null, false).isAvailable());
		assertFalse(StudentSurveyNavigation.forEvaluation(home, 13, 2L, true).isAvailable());
		assertFalse(StudentSurveyNavigation.forEvaluation(home, 99, 3L, true).isAvailable());
		assertFalse(StudentSurveyNavigation.forEvaluation(null, 13, 3L, true).isAvailable());
	}

	@Test void unansweredAndSubmittedTargetsRemainNavigable() {
		for (String response : List.of("not_answered", "in_progress", "submitted")) {
			var navigation = StudentSurveyNavigation.forEvaluation(home(ConsentStatus.AGREED, 4L, response), 13, 3L, true);
			assertTrue(navigation.isAvailable());
			assertEquals(4L, navigation.getSurveyId());
			assertEquals(3L, navigation.getEvaluationId());
		}
	}
}
