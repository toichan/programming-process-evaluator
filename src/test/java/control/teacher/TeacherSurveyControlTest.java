package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import entity.TeacherSurveyFilterTest;

class TeacherSurveyControlTest {
	@Test void exportsCanonicalColumnsAndPseudonymousIdentityWithGenericAnswers() {
		var rows=List.of(TeacherSurveyFilterTest.row(1,4.0));
		String csv=TeacherSurveyControl.csv(rows), text=TeacherSurveyControl.text(rows);
		assertTrue(csv.startsWith("\uFEFF\"recordId\",\"responseId\",\"submittedAt\",\"studentId\",\"taskCode\",\"taskTitle\",\"difficulty\","));
		assertTrue(csv.contains(",\"7\",\"TASK\",\"Title\",\"初級\",\"4.0\","));
		assertFalse(csv.contains("private-login"));assertFalse(text.contains("private-login"));
		assertTrue(csv.contains("additionalAnswers"));assertTrue(csv.contains("Arbitrary question"));
		assertTrue(csv.contains("'=formula"));assertTrue(csv.contains("\"\"reason\"\""));
		assertTrue(text.contains("7_Title_extra"));assertTrue(text.contains("Extra reason"));
		assertTrue(TeacherSurveyControl.csv(List.of()).endsWith("\r\n"));
	}
}
