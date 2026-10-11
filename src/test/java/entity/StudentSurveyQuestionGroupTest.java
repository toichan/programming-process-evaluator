package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentSurveyQuestionGroupTest {
	@Test
	void groupsSixSavedMetricsIntoFourPrototypeStepsWithoutDroppingQuestions() {
		List<StudentSurveyQuestion> questions = List.of(
				question("q1ThinkingValidity"),
				question("q1ThinkingScore"),
				question("q2AttitudeValidity"),
				question("q2AttitudeScore"),
				question("q3ProcessResistanceScore"),
				question("q4UsabilityScore"));

		var groups = StudentSurveyQuestionGroup.fromQuestions(questions);

		assertEquals(List.of("thinking", "attitude", "process", "usability"),
				groups.stream().map(StudentSurveyQuestionGroup::getCode).toList());
		assertEquals(List.of(
				"思考力・判断力・表現力",
				"主体的に取り組む態度",
				"AIによって試行錯誤の過程が評価されることへの抵抗感",
				"システムの操作性"),
				groups.stream().map(StudentSurveyQuestionGroup::getLabel).toList());
		assertEquals(List.of(2, 2, 1, 1),
				groups.stream().map(group -> group.getQuestions().size()).toList());
		assertEquals(questions, groups.stream().flatMap(group -> group.getQuestions().stream()).toList());
	}

	@Test
	void retainsAdditionalQuestionsAfterThePrototypeGroups() {
		List<StudentSurveyQuestion> questions = new ArrayList<>(List.of(
				question("q1ThinkingValidity"),
				question("q1ThinkingScore"),
				question("q2AttitudeValidity"),
				question("q2AttitudeScore"),
				question("q3ProcessResistanceScore"),
				question("q4UsabilityScore")));
		questions.add(question("teacher-added-text"));

		var groups = StudentSurveyQuestionGroup.fromQuestions(questions);

		assertEquals("追加設問", groups.get(groups.size() - 1).getLabel());
		assertEquals(List.of("teacher-added-text"),
				groups.get(groups.size() - 1).getQuestions().stream().map(StudentSurveyQuestion::getCode).toList());
		assertEquals(questions.size(),
				groups.stream().mapToInt(group -> group.getQuestions().size()).sum());
	}

	@Test
	void keepsGenericSurveysUsableAsOneStep() {
		var groups = StudentSurveyQuestionGroup.fromQuestions(List.of(question("custom-question")));

		assertEquals(1, groups.size());
		assertEquals("設問", groups.get(0).getLabel());
		assertTrue(groups.get(0).getQuestions().get(0).isRequired());
	}

	@Test
	void standardMetricQuestionsKeepReasonFieldsWhenThePromptWasNotConfigured() {
		var question = new StudentSurveyQuestion(
				1, "q1ThinkingValidity", "rating", "妥当性", null, true, false, 1,
				List.of(), List.of(), "");

		assertTrue(question.isStandardMetric());
		assertEquals("システムの評価を妥当だと考えた理由", question.getDisplayReasonPrompt());
	}

	private static StudentSurveyQuestion question(String code) {
		return new StudentSurveyQuestion(
				code.hashCode(), code, "rating", code, "理由", true, true, 1,
				List.of(), List.of(), "");
	}
}
