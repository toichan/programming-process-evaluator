package servlet.auth;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PortalHostRouting {
	private static final Pattern DNS_HOST = Pattern.compile(
			"(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
	private static final String STUDENT_LOGIN_PATH = "/student/account/login";
	private static final String TEACHER_LOGIN_PATH = "/teacher/account/login";

	private final String studentHost;
	private final String teacherHost;

	PortalHostRouting(String studentHost, String teacherHost) {
		this.studentHost = validateHost(studentHost, "student portal");
		this.teacherHost = validateHost(teacherHost, "teacher portal");
		if (this.studentHost.equals(this.teacherHost)) {
			throw new IllegalStateException("Student and teacher portal hosts must be different.");
		}
	}

	public static PortalHostRouting fromEnvironment() {
		return new PortalHostRouting(
				configuredHost("STUDENT_PORTAL_HOST", "student.ppeval.net"),
				configuredHost("TEACHER_PORTAL_HOST", "teacher.ppeval.net"));
	}

	Optional<String> redirectLocation(String requestHost, int requestPort, String path, String contextPath) {
		String expectedHost;
		String loginPath;
		if (ApplicationUrls.isPortalPath(path, "/student")) {
			expectedHost = studentHost;
			loginPath = STUDENT_LOGIN_PATH;
		} else if (ApplicationUrls.isPortalPath(path, "/teacher") || ApplicationUrls.isPortalPath(path, "/admin")) {
			expectedHost = teacherHost;
			loginPath = TEACHER_LOGIN_PATH;
		} else {
			return Optional.empty();
		}

		if (expectedHost.equals(normalizeRequestHost(requestHost))) {
			return Optional.empty();
		}
		String scheme = isLocalHost(expectedHost) ? "http" : "https";
		String port = isLocalHost(expectedHost) && requestPort != 80 ? ":" + requestPort : "";
		return Optional.of(scheme + "://" + expectedHost + port + contextPath + loginPath);
	}

	public String loginPathForHost(String requestHost) {
		return teacherHost.equals(normalizeRequestHost(requestHost))
				? TEACHER_LOGIN_PATH : STUDENT_LOGIN_PATH;
	}

	private static String configuredHost(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null ? defaultValue : value;
	}

	private static String validateHost(String host, String portalName) {
		if (host == null) {
			throw new IllegalStateException("Host is required for the " + portalName + ".");
		}
		String normalized = host.trim().toLowerCase(Locale.ROOT);
		if (!normalized.equals("localhost")
				&& !normalized.equals("127.0.0.1")
				&& !normalized.endsWith(".localhost")
				&& !DNS_HOST.matcher(normalized).matches()) {
			throw new IllegalStateException("Invalid host configured for the " + portalName + ".");
		}
		return normalized;
	}

	private static String normalizeRequestHost(String host) {
		return host == null ? "" : host.trim().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
	}

	private static boolean isLocalHost(String host) {
		return host.equals("localhost")
				|| host.equals("127.0.0.1")
				|| host.endsWith(".localhost");
	}
}
