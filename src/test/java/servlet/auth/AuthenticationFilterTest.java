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
	void everyRegisteredProtectedServletRejectsAnonymousAndWrongRoleRequests() throws Exception {
		var root = java.nio.file.Path.of("src/main/java/servlet");
		var inventory = new java.util.TreeMap<String, Map<String, Object>>();
		try (var sources = java.nio.file.Files.walk(root)) {
			for (var source : sources.filter(path -> path.toString().endsWith("Servlet.java")).toList()) {
				String name = "servlet." + root.relativize(source).toString()
						.replace(java.io.File.separatorChar, '.').replaceFirst("\\.java$", "");
				var type = Class.forName(name, false, getClass().getClassLoader());
				var annotation = type.getAnnotation(javax.servlet.annotation.WebServlet.class);
				assertTrue(annotation != null, "Servlet source must declare its route: " + name);
				var routes = new java.util.TreeSet<String>();
				routes.addAll(java.util.List.of(annotation.value()));
				routes.addAll(java.util.List.of(annotation.urlPatterns()));
				for (String path : routes) {
					UserType role = path.equals("/student") || path.startsWith("/student/") ? UserType.STUDENT
							: path.equals("/teacher") || path.startsWith("/teacher/") ? UserType.TEACHER
							: path.equals("/admin") || path.startsWith("/admin/") ? UserType.ADMIN : null;
					boolean protectedRoute = (role != null && !path.endsWith("/account/login"))
							|| path.equals("/auth/logout");
					var methods = java.util.Arrays.stream(type.getDeclaredMethods()).map(java.lang.reflect.Method::getName)
							.filter(method -> method.matches("do(Get|Post|Put|Delete|Head|Options|Trace)"))
							.map(method -> method.substring(2).toUpperCase(java.util.Locale.ROOT)).sorted().toList();
					assertFalse(inventory.containsKey(path), "Duplicate registered route: " + path);
					var filterRoles = !protectedRoute ? java.util.List.of()
							: role == UserType.TEACHER ? java.util.List.of("TEACHER", "ADMIN")
							: role == null ? java.util.List.of("STUDENT", "TEACHER", "ADMIN")
							: java.util.List.of(role.name());
					inventory.put(path, Map.of("servlet", name, "declared_methods", methods,
							"protected", protectedRoute, "filter_allowed_roles", filterRoles));
					if (!protectedRoute) continue;
					for (String method : new String[] { "GET", "POST" }) {
						Probe anonymous = new Probe(path, method, null);
						anonymous.run();
						assertEquals(302, anonymous.status.get(), method + " " + path);
						assertFalse(anonymous.chained.get());
						if (role == null) continue;
						for (UserType candidate : UserType.values()) {
							if (candidate == role || (role == UserType.TEACHER && candidate == UserType.ADMIN)) continue;
							Probe wrongRole = new Probe(path, method,
									user(candidate, candidate == UserType.ADMIN ? "admin" : "synthetic-role"));
							wrongRole.run();
							assertEquals(403, wrongRole.status.get(), method + " " + path + " " + candidate);
							assertFalse(wrongRole.chained.get());
						}
					}
				}
			}
		}
		assertFalse(inventory.isEmpty());
		var output = java.nio.file.Path.of("build/phase20/servlet-surface.json");
		java.nio.file.Files.createDirectories(output.getParent());
		java.nio.file.Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(inventory));
	}

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
