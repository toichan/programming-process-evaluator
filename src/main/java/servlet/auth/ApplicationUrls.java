package servlet.auth;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class ApplicationUrls {
	public static final String STUDENT_ACCOUNT = "/student/account";
	public static final String STUDENT_PASSWORD = "/student/account/password";
	public static final String STUDENT_CONSENT = "/student/consent";
	public static final String TEACHER_ACCOUNT = "/teacher/account";
	public static final String TEACHER_PASSWORD = "/teacher/account/password";
	public static final String TEACHER_STUDENTS = "/teacher/students";
	public static final String ADMIN_TEACHERS = "/admin/teachers";

	private static final Map<String, String> LEGACY_PATHS = Map.of(
			"/teacher/account/account", TEACHER_STUDENTS,
			"/teacher/accounts", TEACHER_STUDENTS,
			"/student/account/account", STUDENT_ACCOUNT,
			"/teacher/account/profile", TEACHER_ACCOUNT,
			"/student/account/change-password", STUDENT_PASSWORD,
			"/student/survey/consent", STUDENT_CONSENT,
			"/admin/management", ADMIN_TEACHERS);

	private ApplicationUrls() {
	}

	public static String canonicalPath(String path) {
		return LEGACY_PATHS.getOrDefault(path, path);
	}

	static boolean isPortalPath(String path, String portal) {
		return path.equals(portal) || path.startsWith(portal + "/");
	}

	static boolean redirectLegacy(HttpServletRequest request, HttpServletResponse response, String path)
			throws IOException {
		String canonical = canonicalPath(path);
		if (path.equals(canonical)) return false;
		String query = request.getQueryString();
		String destination = request.getContextPath() + canonical + (query == null ? "" : "?" + query);
		String method = request.getMethod();
		if ("GET".equals(method) || "HEAD".equals(method)) {
			response.sendRedirect(response.encodeRedirectURL(destination));
		} else {
			response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
			response.setHeader("Location", response.encodeRedirectURL(destination));
		}
		return true;
	}
}
