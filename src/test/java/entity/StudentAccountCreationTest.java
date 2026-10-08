package entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StudentAccountCreationTest {
	@Test void validatesBatchAndClassChoice() {
		assertDoesNotThrow(() -> new StudentAccountCreation(1, 2, null, 200));
		assertDoesNotThrow(() -> new StudentAccountCreation(1, 0, "1年A組", 1));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountCreation(0, 1, null, 1));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountCreation(1, 1, null, 201));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountCreation(1, 1, null, 0));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountCreation(1, 0, " ", 1));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountCreation(1, 0, "\u0000", 1));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountCreation(1, -1, "Class", 1));
	}
}
