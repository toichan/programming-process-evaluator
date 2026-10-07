package servlet.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

class ApplicationUrlsTest {
	private static final Map<String, String> LEGACY_PATHS = Map.of(
			"/teacher/account/account", "/teacher/students",
			"/teacher/accounts", "/teacher/students",
			"/student/account/account", "/student/account",
			"/teacher/account/profile", "/teacher/account",
			"/student/account/change-password", "/student/account/password",
			"/student/survey/consent", "/student/consent",
			"/admin/management", "/admin/teachers");

	@Test
	void canonicalizesEveryLegacyUrlWithoutChangingCanonicalOrUnknownPaths() {
		LEGACY_PATHS.forEach((legacy, canonical) -> {
			assertEquals(canonical, ApplicationUrls.canonicalPath(legacy));
			assertEquals(canonical, ApplicationUrls.canonicalPath(canonical));
		});
		for (String path : new String[] { "/teacher/task", "/student/editor/run",
				"/teacher/account/account/unknown", "//example.org", "/unknown" }) {
			assertEquals(path, ApplicationUrls.canonicalPath(path));
		}
	}

	@Test
	void redirectsGetAndHeadForEveryAliasPreservingContextAndRawQuery() throws Exception {
		for (String method : new String[] { "GET", "HEAD" }) {
			for (var alias : LEGACY_PATHS.entrySet()) {
				Probe probe = new Probe(method, "view=list&q=%E9%AB%98%E6%A0%A1&userId=1&userId=2&return=https%3A%2F%2Fevil.example");
				assertTrue(ApplicationUrls.redirectLegacy(probe.request(), probe.response(), alias.getKey()));
				assertEquals(302, probe.status.get());
				assertEquals("/ppe" + alias.getValue() + "?" + probe.query, probe.location.get());
				assertFalse(probe.bodyRead.get());
			}
		}
	}

	@Test
	void redirectsWriteMethodsWithoutReadingOrDiscardingFormBodies() throws Exception {
		for (String method : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
			for (var alias : LEGACY_PATHS.entrySet()) {
				Probe probe = new Probe(method, "view=detail&userId=42");
				assertTrue(ApplicationUrls.redirectLegacy(probe.request(), probe.response(), alias.getKey()));
				assertEquals(307, probe.status.get());
				assertEquals("/ppe" + alias.getValue() + "?" + probe.query, probe.location.get());
				assertFalse(probe.bodyRead.get());
			}
		}
	}

	@Test
	void doesNotRedirectCanonicalOrUnrelatedUrlsAndSupportsMissingQuery() throws Exception {
		Probe probe = new Probe("GET", null);
		assertFalse(ApplicationUrls.redirectLegacy(probe.request(), probe.response(), "/teacher/students"));
		assertFalse(ApplicationUrls.redirectLegacy(probe.request(), probe.response(), "/unknown"));
		assertEquals(0, probe.status.get());
		assertTrue(ApplicationUrls.redirectLegacy(probe.request(), probe.response(), "/teacher/accounts"));
		assertEquals("/ppe/teacher/students", probe.location.get());
	}

	@Test
	void portalBoundaryIncludesBareRootsButNotSimilarPrefixes() {
		for (String portal : new String[] { "/student", "/teacher", "/admin" }) {
			assertTrue(ApplicationUrls.isPortalPath(portal, portal));
			assertTrue(ApplicationUrls.isPortalPath(portal + "/", portal));
			assertTrue(ApplicationUrls.isPortalPath(portal + "/home", portal));
			assertFalse(ApplicationUrls.isPortalPath(portal + "-other/home", portal));
		}
	}

	private static final class Probe {
		private final String method;
		private final String query;
		private final AtomicInteger status = new AtomicInteger();
		private final AtomicReference<String> location = new AtomicReference<>();
		private final AtomicBoolean bodyRead = new AtomicBoolean();

		private Probe(String method, String query) {
			this.method = method;
			this.query = query;
		}

		private HttpServletRequest request() {
			return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
					new Class<?>[] { HttpServletRequest.class }, (proxy, invoked, args) -> switch (invoked.getName()) {
						case "getMethod" -> method;
						case "getContextPath" -> "/ppe";
						case "getQueryString" -> query;
						case "getParameter", "getInputStream", "getReader" -> {
							bodyRead.set(true);
							yield null;
						}
						default -> null;
					});
		}

		private HttpServletResponse response() {
			return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
					new Class<?>[] { HttpServletResponse.class }, (proxy, invoked, args) -> {
						switch (invoked.getName()) {
							case "sendRedirect" -> { status.set(302); location.set((String) args[0]); }
							case "setStatus" -> status.set((int) args[0]);
							case "setHeader" -> { if ("Location".equals(args[0])) location.set((String) args[1]); }
							case "encodeRedirectURL" -> { return args[0]; }
							default -> { }
						}
						return null;
					});
		}
	}
}
