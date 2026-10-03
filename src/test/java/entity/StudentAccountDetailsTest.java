package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import entity.UserCredential.FirstLoginStatus;

class StudentAccountDetailsTest {
	@Test
	void exposesCanonicalStudentIdWithoutSeparateDisplayName() {
		var details = new StudentAccountDetails("test-student", 1, FirstLoginStatus.NOT_LOGGED_IN,
				false, List.of(), List.of());
		assertEquals("test-student", details.getStudentId());
	}
}
