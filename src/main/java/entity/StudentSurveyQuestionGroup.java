package entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StudentSurveyQuestionGroup {
	private static final List<GroupDefinition> STANDARD_GROUPS = List.of(
			new GroupDefinition("thinking", "思考力・判断力・表現力",
					List.of("q1ThinkingValidity", "q1ThinkingScore")),
			new GroupDefinition("attitude", "主体的に取り組む態度",
					List.of("q2AttitudeValidity", "q2AttitudeScore")),
			new GroupDefinition("process", "AIによって試行錯誤の過程が評価されることへの抵抗感",
					List.of("q3ProcessResistanceScore")),
			new GroupDefinition("usability", "システムの操作性",
					List.of("q4UsabilityScore")));

	private final String code;
	private final String label;
	private final List<StudentSurveyQuestion> questions;

	private StudentSurveyQuestionGroup(String code, String label, List<StudentSurveyQuestion> questions) {
		this.code = code;
		this.label = label;
		this.questions = List.copyOf(questions);
	}

	public static List<StudentSurveyQuestionGroup> fromQuestions(List<StudentSurveyQuestion> questions) {
		Map<String, List<StudentSurveyQuestion>> grouped = new LinkedHashMap<>();
		for (GroupDefinition definition : STANDARD_GROUPS) {
			grouped.put(definition.code(), new ArrayList<>());
		}
		List<StudentSurveyQuestion> additionalQuestions = new ArrayList<>();
		for (StudentSurveyQuestion question : questions) {
			GroupDefinition definition = STANDARD_GROUPS.stream()
					.filter(candidate -> candidate.questionCodes().contains(question.getCode()))
					.findFirst()
					.orElse(null);
			if (definition == null) {
				additionalQuestions.add(question);
			} else {
				grouped.get(definition.code()).add(question);
			}
		}

		List<StudentSurveyQuestionGroup> result = new ArrayList<>();
		for (GroupDefinition definition : STANDARD_GROUPS) {
			List<StudentSurveyQuestion> matchingQuestions = grouped.get(definition.code());
			if (!matchingQuestions.isEmpty()) {
				result.add(new StudentSurveyQuestionGroup(
						definition.code(), definition.label(), matchingQuestions));
			}
		}
		if (!additionalQuestions.isEmpty()) {
			result.add(new StudentSurveyQuestionGroup(
					"additional",
					result.isEmpty() ? "設問" : "追加設問",
					additionalQuestions));
		}
		return List.copyOf(result);
	}

	public String getCode() { return code; }
	public String getLabel() { return label; }
	public List<StudentSurveyQuestion> getQuestions() { return questions; }

	private record GroupDefinition(String code, String label, List<String> questionCodes) {
	}
}
