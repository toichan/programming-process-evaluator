package servlet.student;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentHomeNavigationContractTest {
	@Test
	void allFourScreensUseTheSharedAccountStyleHomeButton() throws Exception {
		for (String file : new String[] { "editor.jsp", "exercise/exercise.jsp",
				"evaluation/evaluation.jsp", "survey/survey.jsp" }) {
			assertTrue(read("WEB-INF/student/" + file).contains("home-action.jspf"), file);
		}
		String action = read("WEB-INF/student/shared/home-action.jspf");
		assertTrue(action.contains("text-center mt-4"));
		assertTrue(action.contains("btn btn-outline-secondary"));
		assertTrue(action.contains("value='/student/home'"));
		assertTrue(action.contains("pageEncoding=\"UTF-8\""));
	}

	@Test
	void unsavedCodeAndAnswersProtectNavigationLogoutAndRevisionCancellation() throws Exception {
		assertTrue(read("js/student/editor/editor.js").contains("PPEUnsavedNavigation.bind"));
		assertTrue(read("js/student/exercise/exercise.js").contains("window.PPEConfirmLeave = mayLeave"));
		String survey = read("js/student/survey/survey.js");
		assertTrue(survey.contains("answerSnapshot() !== initialAnswers"));
		assertTrue(survey.contains("await navigation.confirmLeave({"));
		assertTrue(survey.contains("navigation.allowNavigation()"));
		assertTrue(read("js/shared/logout-confirmation.js").contains("await window.PPEConfirmLeave()"));
		String guard = read("js/student/shared/unsaved-navigation.js");
		assertTrue(guard.contains("beforeunload"));
		assertTrue(guard.contains("await feedback.confirm"));
		assertTrue(guard.contains("event.preventDefault()"));
	}

	private static String read(String file) throws Exception {
		return Files.readString(Path.of("src/main/webapp").resolve(file));
	}
}
