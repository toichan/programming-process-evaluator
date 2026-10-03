package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SchoolInputTest {
	@Test
	void acceptsExplicitLevelsAndTrimsName() {
		assertEquals("学校", new SchoolInput(" 学校 ", 1).name());
		assertEquals(2, new SchoolInput("School", 2).securityLevel());
	}

	@Test
	void rejectsMissingNameLongNameAndInvalidLevel() {
		assertThrows(IllegalArgumentException.class, () -> new SchoolInput(null, 1));
		assertThrows(IllegalArgumentException.class, () -> new SchoolInput("  ", 1));
		assertThrows(IllegalArgumentException.class, () -> new SchoolInput("a".repeat(201), 1));
		assertThrows(IllegalArgumentException.class, () -> new SchoolInput("School", 0));
		assertThrows(IllegalArgumentException.class, () -> new SchoolInput("School", 3));
		assertEquals(200, new SchoolInput("学".repeat(200), 1).name().length());
	}
}
