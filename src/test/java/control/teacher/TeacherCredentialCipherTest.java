package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TeacherCredentialCipherTest {
	@Test void bindsRandomizedCiphertextToStudentAndRejectsTampering() {
		var cipher = new TeacherCredentialCipher(new byte[32]);
		String first = cipher.encrypt(10, "Synthetic12!".toCharArray());
		assertEquals("Synthetic12!", cipher.decrypt(10, first));
		assertNotEquals(first, cipher.encrypt(10, "Synthetic12!".toCharArray()));
		assertFalse(first.contains("Synthetic"));
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(11, first));
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(10, first.substring(0, first.length() - 8)));
		assertThrows(IllegalStateException.class, () -> new TeacherCredentialCipher(new byte[16]));
	}
}
