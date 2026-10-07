package control.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {
	@Test void generatedPasswordsMeetPolicyAndAreUnique() {
		var seen = new HashSet<String>();
		for (int index = 0; index < 100; index++) {
			char[] password = PasswordGenerator.generate();
			try {
				assertEquals(24, password.length);
				assertTrue(PasswordPolicy.isValid(password));
				assertTrue(seen.add(new String(password)));
			} finally { Arrays.fill(password, '\0'); }
		}
	}
}
