package servlet.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class CsrfTokens {
	private static final String SESSION_ATTRIBUTE = CsrfTokens.class.getName() + ".token";
	private static final SecureRandom RANDOM = new SecureRandom();

	private CsrfTokens() {
	}

	public static String getOrCreate(HttpSession session) {
		Object existing = session.getAttribute(SESSION_ATTRIBUTE);
		if (existing instanceof String token) {
			return token;
		}
		byte[] tokenBytes = new byte[32];
		RANDOM.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		session.setAttribute(SESSION_ATTRIBUTE, token);
		return token;
	}

	public static void rotate(HttpSession session) {
		session.removeAttribute(SESSION_ATTRIBUTE);
		getOrCreate(session);
	}

	public static boolean isValid(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return false;
		}
		Object expected = session.getAttribute(SESSION_ATTRIBUTE);
		String submitted = request.getParameter("csrfToken");
		return expected instanceof String token
				&& submitted != null
				&& MessageDigest.isEqual(token.getBytes(java.nio.charset.StandardCharsets.UTF_8),
						submitted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
