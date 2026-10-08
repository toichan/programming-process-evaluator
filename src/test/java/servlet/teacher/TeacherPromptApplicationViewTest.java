package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TeacherPromptApplicationViewTest {
	@Test
	void exposesConfirmedVersionedApplicationAndUsesActualActiveReference() throws Exception {
		String view = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/prompt/prompt.jsp"));
		String script = Files.readString(Path.of("src/main/webapp/js/teacher/prompt/prompt.js"));
		assertTrue(view.contains("id=\"unpublishedPromptApplyForm\""));
		assertTrue(view.contains("name=\"expectedTaskVersion\""));
		assertTrue(view.contains("name=\"applyConfirmed\" value=\"no\""));
		assertTrue(view.contains("version.promptVersionId == promptPage.activePromptVersionId"));
		assertFalse(view.contains("version.promptStatus == 'active'"));
		assertTrue(script.contains("applyUnpublishedPrompt:"));
		assertTrue(script.contains("form.elements.namedItem('applyConfirmed').value = 'yes'"));
		assertTrue(script.contains("pageFeedback.confirm"));
	}
}
