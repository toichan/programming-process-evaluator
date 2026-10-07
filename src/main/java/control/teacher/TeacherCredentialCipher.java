package control.teacher;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class TeacherCredentialCipher {
	private final SecretKeySpec key;
	private static final SecureRandom RANDOM = new SecureRandom();

	public TeacherCredentialCipher(byte[] bytes) {
		if (bytes == null || bytes.length != 32) throw new IllegalStateException("Student credential key must be 256 bits.");
		key = new SecretKeySpec(bytes, "AES");
	}

	public static TeacherCredentialCipher configured() {
		String encoded = System.getenv("STUDENT_CREDENTIAL_KEY");
		if (encoded == null || encoded.isBlank()) throw new IllegalStateException("Student credential key is not configured.");
		byte[] bytes;
		try { bytes = Base64.getDecoder().decode(encoded); }
		catch (IllegalArgumentException failure) { throw new IllegalStateException("Student credential key is invalid.", failure); }
		try { return new TeacherCredentialCipher(bytes); }
		finally { Arrays.fill(bytes, (byte) 0); }
	}

	public String encrypt(long studentId, char[] password) {
		byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
		byte[] plain = new String(password).getBytes(StandardCharsets.UTF_8);
		try {
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, studentId, nonce);
			byte[] encrypted = cipher.doFinal(plain);
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
					.put(nonce).put(encrypted).array());
		} catch (GeneralSecurityException failure) {
			throw new IllegalStateException("Student credential encryption failed.", failure);
		} finally { Arrays.fill(plain, (byte) 0); }
	}

	public String decrypt(long studentId, String encoded) {
		byte[] plain = null;
		try {
			byte[] payload = Base64.getDecoder().decode(encoded);
			if (payload.length < 28) throw new IllegalArgumentException("Invalid credential payload.");
			plain = cipher(Cipher.DECRYPT_MODE, studentId, Arrays.copyOf(payload, 12))
					.doFinal(payload, 12, payload.length - 12);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException failure) {
			throw new IllegalStateException("Student credential decryption failed.", failure);
		} finally { if (plain != null) Arrays.fill(plain, (byte) 0); }
	}

	private Cipher cipher(int mode, long studentId, byte[] nonce) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, key, new GCMParameterSpec(128, nonce));
		cipher.updateAAD(Long.toString(studentId).getBytes(StandardCharsets.US_ASCII));
		return cipher;
	}
}
