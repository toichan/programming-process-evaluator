package entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TeacherAccountInputTest {
	@Test void acceptsUnicodeIdsAndCopiesAllNineIndependentPermissions() {
		var schools = new HashSet<>(Set.of(1L));
		var input = new TeacherAccountInput("教師-test", schools, Set.of("teacher-prompt-design"));
		schools.clear();
		assertEquals(Set.of(1L), input.schoolIds());
		assertFalse(input.features().contains("task-management"));
		assertEquals(9, TeacherAccountInput.FEATURE_ORDER.size());
		assertEquals(TeacherAccountInput.FEATURE_LABELS.keySet(), Set.copyOf(TeacherAccountInput.FEATURE_ORDER));
		assertThrows(UnsupportedOperationException.class, () -> input.features().add("task-management"));
	}
	@Test void rejectsInvalidIdsSchoolsAndUnknownFeatures() {
		for (String id : new String[] {null, "", " ", "a b", "a\nb", "x".repeat(65), "admin", "Admin", "ADMIN"}) {
			assertThrows(IllegalArgumentException.class, () -> new TeacherAccountInput(id, Set.of(), Set.of()));
		}
		assertThrows(IllegalArgumentException.class, () -> new TeacherAccountInput("teacher", Set.of(0L), Set.of()));
		assertThrows(IllegalArgumentException.class, () -> new TeacherAccountInput("teacher", Set.of(), Set.of("unknown")));
		assertDoesNotThrow(() -> new TeacherAccountInput("x".repeat(64), Set.of(), Set.of()));
	}
}
