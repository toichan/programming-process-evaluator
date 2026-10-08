package entity;

import java.util.List;

public record TeacherSurveyResponse(long responseId, String responseCode, String recordCode, long studentUserId,
		String studentId, long schoolId, String school, long classroomId, String className,
		long taskId, String taskCode, String taskTitle, String difficulty, long surveyId, long evaluationId,
		String submittedAt, String completionStatus, String consentStatus, Double systemThinkingScore,
		Double systemAttitudeScore, List<Answer> answers) {
	public static final List<String> METRICS = List.of("q1ThinkingValidity", "q1ThinkingScore",
			"q2AttitudeValidity", "q2AttitudeScore", "q3ProcessResistanceScore", "q4UsabilityScore");
	public TeacherSurveyResponse { answers = List.copyOf(answers); }
	public Double score(String code) {
		return answers.stream().filter(answer -> code.equals(answer.code())).map(Answer::score)
				.filter(java.util.Objects::nonNull).findFirst().orElse(null);
	}
	public String reason(String code) {
		return answers.stream().filter(answer -> code.equals(answer.code())).map(Answer::reason).findFirst().orElse("");
	}
	public String difficultyLabel() {
		return switch (difficulty) { case "beginner" -> "初級"; case "intermediate" -> "中級"; case "advanced" -> "上級"; default -> "未設定"; };
	}
	public record Answer(String code, String prompt, String type, String value, String reason, Double score,
			List<Option> options) {
		public Answer { options = List.copyOf(options); }
	}
	public record Option(String value, String label) {}
}
