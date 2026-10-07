package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import lib.web.CsvCells;

class StudentAccountManagementServletTest {
	@Test void validatesTargetsAndProtectsCsvCells() {
		assertEquals(2, StudentAccountManagementServlet.targets(new String[]{"1","2"}, new String[]{"1","3"}).size());
		assertThrows(IllegalArgumentException.class, () -> StudentAccountManagementServlet.targets(new String[]{"1"}, new String[]{}));
		assertThrows(IllegalArgumentException.class, () -> StudentAccountManagementServlet.targets(new String[]{"0"}, new String[]{"1"}));
		assertThrows(IllegalArgumentException.class, () -> StudentAccountManagementServlet.number("-1"));
		assertEquals("\"'=SUM(A1)\"", CsvCells.encode("=SUM(A1)"));
		assertEquals("\"a\"\"b,c\"", CsvCells.encode("a\"b,c"));
	}
}
