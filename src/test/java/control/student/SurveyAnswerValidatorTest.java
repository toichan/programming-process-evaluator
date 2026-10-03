package control.student;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import entity.StudentSurveyQuestion;
import entity.SurveyAnswerValidator;

class SurveyAnswerValidatorTest {
	@Test
	void requiresValuesOnlyWhenSubmitting() {
		StudentSurveyQuestion question = question("rating", true, List.of("1", "2", "3", "4", "5"));

		assertTrue(SurveyAnswerValidator.validate(question, List.of(), "", false).isEmpty());
		assertTrue(SurveyAnswerValidator.validate(question, List.of(), "", true).isPresent());
	}

	@Test
	void requiresReasonOnlyWhenConfiguredAndSubmitting() {
		StudentSurveyQuestion requiredReason = question("rating", true, true, List.of("1", "2", "3", "4", "5"));
		StudentSurveyQuestion optionalReason = question("rating", true, false, List.of("1", "2", "3", "4", "5"));

		assertTrue(SurveyAnswerValidator.validate(requiredReason, List.of("4"), "", false).isEmpty());
		assertTrue(SurveyAnswerValidator.validate(requiredReason, List.of("4"), "", true).isPresent());
		assertTrue(SurveyAnswerValidator.validate(requiredReason, List.of("4"), "理由", true).isEmpty());
		assertTrue(SurveyAnswerValidator.validate(optionalReason, List.of("4"), "", true).isEmpty());
	}

	@Test
	void rejectsValuesNotDefinedByActiveOptions() {
		StudentSurveyQuestion question = question("rating", true, List.of("1", "2", "3", "4", "5"));

		assertTrue(SurveyAnswerValidator.validate(question, List.of("4"), "", true).isEmpty());
		assertTrue(SurveyAnswerValidator.validate(question, List.of("6"), "", true).isPresent());
	}

	@Test
	void rejectsMultipleValuesForSingleChoiceQuestions() {
		StudentSurveyQuestion question = question("single_choice", false, List.of("yes", "no"));

		assertTrue(SurveyAnswerValidator.validate(question, List.of("yes", "no"), "", false).isPresent());
		assertTrue(SurveyAnswerValidator.validate(question, List.of("yes", "yes"), "", false).isPresent());
	}

	@Test
	void acceptsDistinctValuesForMultipleChoiceQuestions() {
		StudentSurveyQuestion question = question("multiple_choice", false, List.of("a", "b", "c"));

		assertTrue(SurveyAnswerValidator.validate(question, List.of("a", "c"), "", false).isEmpty());
		assertTrue(SurveyAnswerValidator.validate(question, List.of("a", "a"), "", false).isPresent());
	}

	@Test
	void requiresTextForRequiredQuestionsAndLimitsAnswerAndReasonLength() {
		StudentSurveyQuestion question = question("text", true, List.of());

		assertTrue(SurveyAnswerValidator.validate(question, List.of(" "), "reason", true).isPresent());
		assertTrue(SurveyAnswerValidator.validate(question, List.of("x".repeat(5001)), "reason", false).isPresent());
		assertTrue(SurveyAnswerValidator.validate(question, List.of("ok"), "x".repeat(5001), false).isPresent());
		assertFalse(SurveyAnswerValidator.validate(question, List.of("answer"), "reason", true).isPresent());
	}

	private static StudentSurveyQuestion question(String type, boolean required, List<String> values) {
		return question(type, required, false, values);
	}

	private static StudentSurveyQuestion question(
			String type,
			boolean required,
			boolean reasonRequired,
			List<String> values) {
		List<StudentSurveyQuestion.Option> options = values.stream()
				.map(value -> new StudentSurveyQuestion.Option(value, value))
				.toList();
		return new StudentSurveyQuestion(
				1, "q1", type, "Question", "理由・補足", required, reasonRequired, 1, options, List.of(), "");
	}
}
