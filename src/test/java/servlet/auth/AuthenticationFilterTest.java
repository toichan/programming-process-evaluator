package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class AuthenticationFilterTest {
	@Test
	void anonymousLegacyRequestsRequireLoginBeforeAnyCanonicalRedirect() throws Exception {
		for (String path : new String[] { "/teacher/account/account", "/teacher/accounts", "/admin/management",
				"/teacher/account/profile", "/student/account/account", "/student/account/change-password",
				"/student/survey/consent" }) {
			Probe probe = new Probe(path, "POST", null);
			probe.run();
			assertEquals(302, probe.status.get());
			assertEquals("/ppe/" + (path.startsWith("/student") ? "student" : "teacher") + "/account/login",
					probe.location.get());
			assertFalse(probe.chained.get());
		}
	}

	@Test
	void barePortalRootsAndTrailingSlashesRequireAuthentication() throws Exception {
		for (String path : new String[] { "/student", "/student/", "/teacher", "/teacher/", "/admin", "/admin/" }) {
			Probe probe = new Probe(path, "GET", null);
			probe.run();
			assertEquals(302, probe.status.get());
			assertEquals("/ppe/" + (path.startsWith("/student") ? "student" : "teacher") + "/account/login",
					probe.location.get());
			assertEquals("no-store", probe.headers.get("Cache-Control"));
			assertFalse(probe.chained.get());
		}
	}

	@Test
	void wrongRolesAreForbiddenAtLegacyAndBarePortalUrls() throws Exception {
		AuthenticatedUser student = user(UserType.STUDENT, "synthetic-student");
		AuthenticatedUser teacher = user(UserType.TEACHER, "synthetic-teacher");
		for (String path : new String[] { "/teacher", "/teacher/", "/teacher/account/account", "/teacher/accounts" }) {
			Probe probe = new Probe(path, "GET", student);
			probe.run();
			assertEquals(403, probe.status.get());
			assertFalse(probe.chained.get());
		}
		for (String path : new String[] { "/student", "/admin", "/admin/management" }) {
			Probe probe = new Probe(path, "POST", teacher);
			probe.run();
			assertEquals(403, probe.status.get());
			assertFalse(probe.chained.get());
		}
	}

	@Test
	void authorizedAdminLegacyGetAndPostRedirectWithoutExecutingTheHandler() throws Exception {
		for (String method : new String[] { "GET", "HEAD", "POST" }) {
			Probe probe = new Probe("/admin/management", method, user(UserType.ADMIN, "admin"));
			probe.run();
			assertEquals("POST".equals(method) ? 307 : 302, probe.status.get());
			assertEquals("/ppe/admin/teachers?view=list&q=%E9%AB%98%E6%A0%A1", probe.location.get());
			assertFalse(probe.chained.get());
		}
	}

	@Test
	void adminIdentityAndCanonicalHandlerBoundariesRemainEnforced() throws Exception {
		Probe invalid = new Probe("/admin/management", "GET", user(UserType.ADMIN, "not-admin"));
		invalid.run();
		assertEquals(403, invalid.status.get());
		assertFalse(invalid.chained.get());

		Probe valid = new Probe("/admin/teachers", "POST", user(UserType.ADMIN, "admin"));
		valid.run();
		assertTrue(valid.chained.get());
		assertEquals(0, valid.status.get());
		assertEquals(UserType.ADMIN, ((AuthenticatedUser) valid.attributes.get("authenticatedUser")).userType());
	}

	@Test
	void anonymousLogoutUsesTheCurrentPortalAndOtherSharedUrlsRemainPublic() throws Exception {
		Probe teacher = new Probe("/auth/logout", "POST", null);
		teacher.run();
		assertEquals("/ppe/teacher/account/login", teacher.location.get());
		Probe student = new Probe("/auth/logout", "POST", null);
		student.host = configuredHost("STUDENT_PORTAL_HOST", "student.ppeval.net");
		student.run();
		assertEquals("/ppe/student/account/login", student.location.get());
		Probe shared = new Probe("/css/common.css", "GET", null);
		shared.run();
		assertTrue(shared.chained.get());
	}

	private static AuthenticatedUser user(UserType type, String loginId) {
		return new AuthenticatedUser(42, loginId, "Synthetic", type, false, "test");
	}

	private static String configuredHost(String name, String fallback) {
		String value = System.getenv(name);
		return value == null ? fallback : value;
	}

	private static final class Probe {
		private final String path;
		private final String method;
		private final AuthenticatedUser user;
		private String host;
		private final AtomicInteger status = new AtomicInteger();
		private final AtomicReference<String> location = new AtomicReference<>();
		private final AtomicBoolean chained = new AtomicBoolean();
		private final Map<String, String> headers = new HashMap<>();
		private final Map<String, Object> attributes = new HashMap<>();

		private Probe(String path, String method, AuthenticatedUser user) {
			this.path = path;
			this.method = method;
			this.user = user;
			host = configuredHost(path.startsWith("/student") ? "STUDENT_PORTAL_HOST" : "TEACHER_PORTAL_HOST",
					path.startsWith("/student") ? "student.ppeval.net" : "teacher.ppeval.net");
		}

		private void run() throws Exception {
			HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
					new Class<?>[] { HttpSession.class }, (proxy, invoked, args) ->
							"getAttribute".equals(invoked.getName()) && AuthenticatedUser.class.getName().equals(args[0])
									? user : null);
			HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
					new Class<?>[] { HttpServletRequest.class }, (proxy, invoked, args) -> {
						return switch (invoked.getName()) {
							case "getContextPath" -> "/ppe";
							case "getRequestURI" -> "/ppe" + path;
							case "getServerName" -> host;
							case "getServerPort" -> 8080;
							case "isSecure" -> true;
							case "getMethod" -> method;
							case "getQueryString" -> "view=list&q=%E9%AB%98%E6%A0%A1";
							case "getSession" -> user == null ? null : session;
							case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
							default -> null;
						};
					});
			HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
					new Class<?>[] { HttpServletResponse.class }, (proxy, invoked, args) -> {
						switch (invoked.getName()) {
							case "sendRedirect" -> { status.set(302); location.set((String) args[0]); }
							case "sendError", "setStatus" -> status.set((int) args[0]);
							case "setHeader" -> {
								headers.put((String) args[0], (String) args[1]);
								if ("Location".equals(args[0])) location.set((String) args[1]);
							}
							case "encodeRedirectURL" -> { return args[0]; }
							default -> { }
						}
						return null;
					});
			AuthenticationFilter filter = new AuthenticationFilter();
			filter.init(null);
			filter.doFilter(request, response, (req, res) -> chained.set(true));
		}
	}
}
