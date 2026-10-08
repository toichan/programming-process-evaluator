package servlet.admin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TeacherAccountServletTest {
	@Test void validatesIdsWithoutOverflowOrNegativeValues() {
		assertEquals(0, TeacherAccountServlet.number("0"));
		assertEquals(42, TeacherAccountServlet.number("42"));
		for (String value : new String[] {null, "-1", "", "1.5", "99999999999999999999999"}) {
			assertThrows(IllegalArgumentException.class, () -> TeacherAccountServlet.number(value));
		}
	}
	@Test void quotesCsvAndNeutralizesSpreadsheetFormulasEvenAcrossNewlines() {
		assertEquals("\"a\"\"b,c\"", TeacherAccountServlet.csvCell("a\"b,c"));
		for (String value : new String[] {"=1+1", "+1\n2", "-1", "@x", "\t=1", "\r=1", "\n=1"}) {
			assertTrue(TeacherAccountServlet.csvCell(value).startsWith("\"'"));
		}
		assertEquals("\"\"", TeacherAccountServlet.csvCell(null));
	}
}
