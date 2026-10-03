package control.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
	private final PasswordHasher passwordHasher = new PasswordHasher();

	@Test
	void hashesPasswordsWithIndependentSaltsAndVerifiesThem() {
		char[] password = "CorrectHorse1!".toCharArray();
		String firstHash = passwordHasher.hash(password);
		String secondHash = passwordHasher.hash(password);

		assertNotEquals(firstHash, secondHash);
		assertTrue(passwordHasher.matches(password, firstHash));
		assertFalse(passwordHasher.matches("WrongHorse1!".toCharArray(), firstHash));
		assertFalse(firstHash.contains("CorrectHorse1!"));
	}

	@Test
	void rejectsMalformedOrUnsupportedHashValues() {
		assertThrows(IllegalArgumentException.class,
				() -> passwordHasher.matches("password".toCharArray(), "unsupported"));
		assertThrows(IllegalArgumentException.class,
				() -> passwordHasher.matches("password".toCharArray(), "pbkdf2-sha256$1$AQ$AQ"));
	}
}
