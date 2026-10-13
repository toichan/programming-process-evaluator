package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentSurveyOptionLabelTest {
	@Test void validityUsesPrototypeLabelsWithoutChangingStoredValues() {
		var question = question("q1ThinkingValidity");
		String[] labels = {"妥当でない", "あまり妥当でない", "どちらともいえない", "おおむね妥当", "妥当"};
		for (int i = 1; i <= 5; i++) {
			var option = new StudentSurveyQuestion.Option(Integer.toString(i), "Synthetic " + i);
			assertEquals(labels[i - 1], question.getOptionLabel(option));
			assertEquals(Integer.toString(i), option.getValue());
		}
	}
	@Test void additionalAndSelfRatingLabelsStayConfigured() {
		var option = new StudentSurveyQuestion.Option("4", "Configured label");
		assertEquals("Configured label", question("additional").getOptionLabel(option));
		assertEquals("Configured label", question("q1ThinkingScore").getOptionLabel(option));
	}
	private StudentSurveyQuestion question(String code) {
		return new StudentSurveyQuestion(1, code, "rating", "Prompt", "", true, false, 1,
				List.of(), List.of(), "");
	}
}
