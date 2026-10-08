package entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentAccountFilterTest {
	private final ManagedStudentAccount account = new ManagedStudentAccount(1,"s001",1,"active",1,"School",2,
			List.of(new TeacherClassOption(10,1,"A","1年")),"password_change_required",true,"unconfirmed",false,false,
			"2026-10-07T12:00:00","teacher","teacher");
	@Test void matchesAllSpecifiedFiltersAndRejectsInvalidValues() {
		assertTrue(new StudentAccountFilter("S001",1,10,"2","pending","unconfirmed","active").test(account));
		assertFalse(new StudentAccountFilter("",2,0,"","","","").test(account));
		assertFalse(new StudentAccountFilter("",0,11,"","","","").test(account));
		assertFalse(new StudentAccountFilter("",0,0,"","completed","","").test(account));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountFilter("",0,0,"3","","",""));
		assertThrows(IllegalArgumentException.class, () -> new StudentAccountFilter("",0,0,"","","ignored",""));
	}
}
