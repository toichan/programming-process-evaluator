package control.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
	@Test
	void acceptsEightToThirtyTwoAsciiCharactersWithThreeCategories() {
		assertTrue(PasswordPolicy.isValid("Abcdef1!".toCharArray()));
		assertTrue(PasswordPolicy.isValid("Abcdefghijklmnopqrstuvwxyz1234".toCharArray()));
	}

	@Test
	void rejectsShortLongAndInsufficientlyDiversePasswords() {
		assertFalse(PasswordPolicy.isValid("Abc1!".toCharArray()));
		assertFalse(PasswordPolicy.isValid("Abcdefghijklmnopqrstuvwxyz123456789!".toCharArray()));
		assertFalse(PasswordPolicy.isValid("abcdefgh12345678".toCharArray()));
	}

	@Test
	void rejectsWhitespaceAndNonAsciiCharacters() {
		assertFalse(PasswordPolicy.isValid("Abcdef1 !".toCharArray()));
		assertFalse(PasswordPolicy.isValid("Abcdef1é".toCharArray()));
	}
}
