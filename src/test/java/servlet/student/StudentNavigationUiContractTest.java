package servlet.student;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentNavigationUiContractTest {
	@Test void taskMenuUsesRealCardsAndTypedTargets() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/student/shared/navigation.jspf"));
		assertTrue(jsp.contains("items=\"${studentTaskShortcuts}\""));
		assertTrue(jsp.contains("task-shortcut-card"));
		assertTrue(jsp.contains("value='${shortcut.task.latestSubmissionId}'"));
		assertTrue(jsp.contains("value='${shortcut.survey.evaluationId}'"));
		assertTrue(jsp.contains("value='${shortcut.survey.surveyId}'"));
		assertTrue(jsp.contains("shortcut.survey.unavailableReason"));
		assertFalse(jsp.contains("課題画面の実装後"));
	}
}
