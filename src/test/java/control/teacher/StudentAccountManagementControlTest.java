package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import control.teacher.StudentAccountManagementControl.Target;

class StudentAccountManagementControlTest {
	private final StudentAccountManagementControl control = new StudentAccountManagementControl();
	private final Target first = new Target(1, 1);
	private final Target second = new Target(2, 1);

	@Test void rejectsMissingAndUnknownActionsBeforeDatabaseAccess() {
		for (String action : new String[] { null, "", "unknown" }) {
			assertThrows(IllegalArgumentException.class,
					() -> control.change(null, List.of(first), action, null));
		}
	}

	@Test void rejectsInvalidTargetSelectionsBeforeDatabaseAccess() {
		assertThrows(IllegalArgumentException.class, () -> control.change(null, null, "delete", null));
		assertThrows(IllegalArgumentException.class, () -> control.change(null, List.of(), "delete", null));
		assertThrows(IllegalArgumentException.class, () -> control.change(null, List.of(first, first), "delete", null));
		assertThrows(IllegalArgumentException.class, () -> control.change(null, List.of(first, second), "suspend", null));
	}
}
