package servlet.student;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StudentHomeViewTest {
	@Test
	void surveyNavigationUsesSubmissionIdRatherThanEvaluationId() throws Exception {
		String survey = Files.readString(Path.of("src/main/webapp/WEB-INF/student/survey/survey.jsp"));
		assertFalse(survey.contains("name='submissionId' value='${surveyPage.evaluationId}'"));
		assertTrue(survey.contains("name='submissionId' value='${surveyPage.evaluationSubmissionId}'"));
	}

	@Test
	void omitsPersistentDeclinedNoticeButKeepsUnansweredAndSaveNotifications() throws Exception {
		String home = Files.readString(Path.of("src/main/webapp/WEB-INF/student/home.jsp"));
		assertFalse(home.contains("研究協力には同意していません。"));
		assertFalse(home.contains("studentHome.consentStatus == 'DECLINED'"));
		assertTrue(home.contains("studentHome.consentStatus == 'UNCONFIRMED'"));
		assertTrue(home.contains("studentHomeNotice == 'saved'"));
		assertTrue(home.contains("studentHomeNotice == 'already'"));
		assertTrue(home.contains("value='/student/consent'"));
	}
}
