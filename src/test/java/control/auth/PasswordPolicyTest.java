package control.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
	@Test
	void explainsAllViolatedRequirementsWithoutEchoingPassword() {
		var errors = PasswordPolicy.violations("ab あ".toCharArray());
		assertEquals(3, errors.size());
		assertTrue(errors.get(0).contains("8文字以上32文字以下"));
		assertTrue(errors.get(1).contains("空白・全角文字"));
		assertTrue(errors.get(2).contains("現在1種類"));
		assertTrue(errors.stream().noneMatch(error -> error.contains("ab あ")));
		assertTrue(PasswordPolicy.violations("Abcdef1!".toCharArray()).isEmpty());
	}
	@Test
	void acceptsEightToThirtyTwoAsciiCharactersWithThreeCategories() {
		assertTrue(PasswordPolicy.isValid("Abcdef1!".toCharArray()));
		assertTrue(PasswordPolicy.isValid(("Ab1!" + "a".repeat(28)).toCharArray()));
		assertFalse(PasswordPolicy.isValid(("Ab1!" + "a".repeat(29)).toCharArray()));
	}

	@Test
	void checksEveryAsciiCharacterAgainstAllowedRangeAndCategoryRules() {
		for (int code = 0; code < 128; code++) {
			char character = (char) code;
			boolean allowed = code >= 0x21 && code <= 0x7e;
			assertEquals(allowed, PasswordPolicy.isValid(("Abcdef1" + character).toCharArray()),
					"Allowed range: " + code);
			boolean thirdCategory = allowed && !(character >= 'A' && character <= 'Z')
					&& !(character >= '0' && character <= '9');
			assertEquals(thirdCategory, PasswordPolicy.isValid(("AAAAAA1" + character).toCharArray()),
					"Third category: " + code);
		}
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
