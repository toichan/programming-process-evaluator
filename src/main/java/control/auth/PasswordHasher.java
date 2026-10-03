package control.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final String FORMAT_ID = "pbkdf2-sha256";
	private static final int ITERATIONS = 600_000;
	private static final int SALT_BYTES = 16;
	private static final int HASH_BYTES = 32;
	private static final int MAX_ACCEPTED_ITERATIONS = 2_000_000;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String DUMMY_HASH = createDummyHash();

	public String hash(char[] password) {
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] derived = derive(password, salt, ITERATIONS);
		try {
			return FORMAT_ID + "$" + ITERATIONS + "$" + Base64.getEncoder().withoutPadding().encodeToString(salt)
					+ "$" + Base64.getEncoder().withoutPadding().encodeToString(derived);
		} finally {
			java.util.Arrays.fill(derived, (byte) 0);
			java.util.Arrays.fill(salt, (byte) 0);
		}
	}

	public boolean matches(char[] password, String encodedHash) {
		String[] parts = encodedHash.split("\\$", -1);
		if (parts.length != 4 || !FORMAT_ID.equals(parts[0])) {
			throw new IllegalArgumentException("Unsupported password hash format.");
		}

		int iterations;
		byte[] salt;
		byte[] expected;
		try {
			iterations = Integer.parseInt(parts[1]);
			salt = Base64.getDecoder().decode(parts[2]);
			expected = Base64.getDecoder().decode(parts[3]);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Malformed password hash.", e);
		}
		if (iterations < ITERATIONS || iterations > MAX_ACCEPTED_ITERATIONS
				|| salt.length != SALT_BYTES || expected.length != HASH_BYTES) {
			throw new IllegalArgumentException("Password hash parameters are outside the supported range.");
		}

		byte[] actual = derive(password, salt, iterations);
		try {
			return MessageDigest.isEqual(expected, actual);
		} finally {
			java.util.Arrays.fill(actual, (byte) 0);
			java.util.Arrays.fill(expected, (byte) 0);
			java.util.Arrays.fill(salt, (byte) 0);
		}
	}

	public boolean matchesDummy(char[] password) {
		return matches(password, DUMMY_HASH);
	}

	private static byte[] derive(char[] password, byte[] salt, int iterations) {
		PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BYTES * 8);
		try {
			return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable.", e);
		} finally {
			spec.clearPassword();
		}
	}

	private static String createDummyHash() {
		byte[] salt = new byte[SALT_BYTES];
		byte[] derived = derive("not-a-real-account-password".toCharArray(), salt, ITERATIONS);
		try {
			return FORMAT_ID + "$" + ITERATIONS + "$" + Base64.getEncoder().withoutPadding().encodeToString(salt)
					+ "$" + Base64.getEncoder().withoutPadding().encodeToString(derived);
		} finally {
			java.util.Arrays.fill(derived, (byte) 0);
		}
	}
}
