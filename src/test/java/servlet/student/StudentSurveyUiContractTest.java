package servlet.student;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentSurveyUiContractTest {
	private static final Path WEBAPP = Path.of("src/main/webapp");

	@Test
	void surveyUsesDynamicGroupedQuestionsAndKeepsAnswerFieldsAndReadOnlyRendering() throws Exception {
		String jsp = read("WEB-INF/student/survey/survey.jsp");
		String script = read("js/student/survey/survey.js");
		String css = read("css/student/survey/survey.css");
		String groups = Files.readString(Path.of("src/main/java/entity/StudentSurveyQuestionGroup.java"));

		assertTrue(jsp.contains("${surveyPage.questionGroups}"));
		assertTrue(jsp.contains("${questionGroup.questions}"));
		assertTrue(jsp.contains("name=\"answer_${question.questionId}\""));
		assertTrue(jsp.contains("name=\"reason_${question.questionId}\""));
		assertTrue(jsp.contains("question.standardMetric"));
		assertTrue(jsp.contains("${question.displayReasonPrompt}"));
		assertTrue(jsp.contains("surveyPage.readOnly"));
		assertTrue(groups.contains("\"q1ThinkingValidity\", \"q1ThinkingScore\""));
		assertTrue(groups.contains("\"q2AttitudeValidity\", \"q2AttitudeScore\""));
		assertTrue(groups.contains("\"q3ProcessResistanceScore\""));
		assertTrue(groups.contains("\"q4UsabilityScore\""));
		assertTrue(groups.contains("additionalQuestions"));
		assertTrue(script.contains("const invalid = readOnly ? [] : validateStep(steps[active])"));
		assertTrue(script.contains("for (let index = 0; index < steps.length; index += 1)"));
		assertTrue(script.contains("submit.hidden = readOnly || active !== steps.length - 1"));
		assertTrue(script.contains("form.requestSubmit(submit)"));
		assertTrue(css.contains(".question-block.is-invalid"));
	}

	@Test
	void returnLinksUseSubmissionIdsAndSurveyEntriesUseAllRequiredIdentifiers() throws Exception {
		String survey = read("WEB-INF/student/survey/survey.jsp");
		String home = read("WEB-INF/student/home.jsp");
		String evaluation = read("WEB-INF/student/evaluation/evaluation.jsp");
		String log = read("WEB-INF/student/evaluation/log.jsp");

		assertTrue(survey.contains("<c:param name=\"assignmentId\" value=\"${surveyPage.assignmentId}\"/>"));
		assertTrue(survey.contains("<c:param name=\"submissionId\" value=\"${surveyPage.evaluationSubmissionId}\"/>"));
		assertTrue(survey.contains("<c:url var=\"surveyCodeLogUrl\" value=\"/student/evaluation/log\">"));
		assertTrue(survey.contains("<c:param name=\"submissionId\" value=\"${surveyPage.evaluationSubmissionId}\"/>"));
		assertTrue(home.contains("<c:url value='/student/survey'>"));
		assertTrue(home.contains("<c:param name='assignmentId' value='${task.taskClassAssignmentId}'/>"));
		assertTrue(home.contains("<c:param name='evaluationId' value='${task.latestCompletedEvaluationId}'/>"));
		assertTrue(home.contains("<c:param name='surveyId' value='${task.activeSurveyId}'/>"));
		assertSurveyNavigation(evaluation, "evaluationPage.assignmentId");
		assertSurveyNavigation(log, "codeLogPage.assignmentId");
		assertTrue(evaluation.contains("<c:param name='submissionId' value='${evaluationPage.selectedSubmissionId}'/>"));
		assertTrue(log.contains("<c:param name='submissionId' value='${codeLogPage.submissionId}'/>"));
	}

	@Test
	void feedbackFlashNameAndHomeReuseMatchTheSurveyForwardContract() throws Exception {
		String survey = read("WEB-INF/student/survey/survey.jsp");
		String servlet = Files.readString(Path.of("src/main/java/servlet/student/StudentSurveyServlet.java"));

		assertTrue(servlet.contains("\"studentSurveyNotice\""));
		assertTrue(servlet.contains("request.setAttribute(\"studentHome\", home)"));
		assertTrue(survey.contains("${studentSurveyNotice == 'draft-saved'}"));
		assertTrue(survey.contains("${studentSurveyNotice == 'submitted'}"));
		assertTrue(survey.contains("${studentSurveyNotice == 'already-submitted'}"));
		assertTrue(servlet.contains("request.setAttribute(\"screenAssetVersion\", \"164\")"));
		assertTrue(survey.contains(">アンケート</h1>"));
		assertTrue(survey.contains("id=\"editSurveyButton\""));
		assertTrue(survey.contains("id=\"surveyAnswers\""));
	}

	@Test
	void surveyKeepsPrototypeCardsSpacingAndResponsiveChoices() throws Exception {
		String css = read("css/student/survey/survey.css");
		String jsp = read("WEB-INF/student/survey/survey.jsp");
		assertTrue(css.contains("radial-gradient(circle at top left"));
		assertTrue(css.contains(".thinking-card"));
		assertTrue(css.contains(".attitude-card"));
		assertTrue(jsp.contains("thinking-card"));
		assertTrue(jsp.contains("attitude-card"));
		assertTrue(jsp.contains("field-header-row"));
		assertTrue(jsp.contains("<c:set var=\"studentMainClass\" value=\"pt-2 pb-5\"/>"));
		assertTrue(jsp.contains("rows=\"6\" maxlength=\"5000\""));
		assertTrue(jsp.contains("'scale-grid' : 'likert-grid'"));
		assertTrue(css.contains("min-height: 4.1rem"));
		assertTrue(css.contains("min-height: 4.4rem"));
		assertTrue(css.contains("grid-template-columns: repeat(5, minmax(0, 1fr))"));
		assertTrue(css.contains("min-height: 5.5rem"));
		assertTrue(css.contains("min-height: 9rem"));
		assertTrue(css.contains(".likert-option input:focus-visible + span"));
		assertTrue(css.contains("@media (max-width: 991px)"));
		assertTrue(css.contains("justify-content: space-between"));
		assertTrue(css.contains("justify-content: center"));
	}

	private static void assertSurveyNavigation(String jsp, String assignmentExpression) {
		assertTrue(jsp.contains("<c:url var=\"surveyUrl\" value=\"/student/survey\">"));
		assertTrue(jsp.contains("<c:param name=\"assignmentId\" value=\"${" + assignmentExpression + "}\"/>"));
		assertTrue(jsp.contains("<c:param name=\"evaluationId\" value=\"${surveyNavigation.evaluationId}\"/>"));
		assertTrue(jsp.contains("<c:param name=\"surveyId\" value=\"${surveyNavigation.surveyId}\"/>"));
		assertTrue(jsp.contains("/student/survey"));
	}

	private static String read(String relativePath) throws Exception {
		return Files.readString(WEBAPP.resolve(relativePath));
	}
}
