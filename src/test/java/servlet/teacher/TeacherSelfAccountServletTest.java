package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TeacherSelfAccountServletTest {
	@Test void acceptsOnlyPositiveVersions() {
		assertEquals(2L, TeacherSelfAccountServlet.version("2"));
		for (String value : new String[] {null, "", "0", "-1", "x", "9223372036854775808"})
			assertThrows(IllegalArgumentException.class, () -> TeacherSelfAccountServlet.version(value));
	}

	@Test void rejectsAllExplicitAccountTargets() {
		assertDoesNotThrow(() -> TeacherSelfAccountServlet.requireSelfTarget(null, null));
		assertThrows(IllegalArgumentException.class, () -> TeacherSelfAccountServlet.requireSelfTarget("2", null));
		assertThrows(IllegalArgumentException.class, () -> TeacherSelfAccountServlet.requireSelfTarget(null, "another"));
	}
}
