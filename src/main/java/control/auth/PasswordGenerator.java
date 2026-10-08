package control.auth;

import java.security.SecureRandom;

public final class PasswordGenerator {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int PASSWORD_LENGTH = 8;
	private static final char[] ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();

	private PasswordGenerator() { }

	public static char[] generate() {
		char[] password = new char[PASSWORD_LENGTH];
		do {
			for (int index = 0; index < password.length; index++) {
				password[index] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
			}
		} while (!PasswordPolicy.isValid(password));
		return password;
	}
}
