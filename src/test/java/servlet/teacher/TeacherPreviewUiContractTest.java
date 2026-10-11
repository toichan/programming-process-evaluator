package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TeacherPreviewUiContractTest {
	@Test void progressAndExerciseUseSharedEditableCopyAndTerminal() throws Exception {
		for (String surface : new String[]{"progress", "exercise"}) {
			String page = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/" + surface + "/" + surface + ".jsp"));
			assertTrue(page.contains("/WEB-INF/teacher/shared/preview-execution.jspf"));
			String script = Files.readString(Path.of("src/main/webapp/js/teacher/" + surface + "/" + surface + ".js"));
			assertTrue(script.contains("PPETeacherPreview.create"));
		}
		String css = Files.readString(Path.of("src/main/webapp/css/teacher/exercise/exercise.css"));
		assertFalse(css.contains("max-height: 32rem"));
		assertTrue(css.contains("overflow-x: auto"));
	}
}
