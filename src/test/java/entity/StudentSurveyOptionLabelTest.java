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
	@Test void processResistanceUsesPrototypeLabelsWithoutChangingStoredValues() {
		assertLabels("q3ProcessResistanceScore", new String[] {
				"とても感じる", "やや感じる", "どちらともいえない", "あまり感じない", "全く感じない"
		});
	}
	@Test void usabilityUsesPrototypeLabelsWithoutChangingStoredValues() {
		assertLabels("q4UsabilityScore", new String[] {
				"とても使いにくい", "やや使いにくい", "どちらともいえない", "やや使いやすい", "とても使いやすい"
		});
	}
	private void assertLabels(String code, String[] labels) {
		var question = question(code);
		for (int i = 1; i <= 5; i++) {
			String value = Integer.toString(i);
			var option = new StudentSurveyQuestion.Option(value, "Synthetic " + i);
			assertEquals(labels[i - 1], question.getOptionLabel(option));
			assertEquals(value, option.getValue());
		}
	}
	private StudentSurveyQuestion question(String code) {
		return new StudentSurveyQuestion(1, code, "rating", "Prompt", "", true, false, 1,
				List.of(), List.of(), "");
	}
}
