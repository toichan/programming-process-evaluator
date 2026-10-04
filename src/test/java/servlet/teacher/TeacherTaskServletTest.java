package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class TeacherTaskServletTest {
	@Test
	void readsTheAuthenticatedUserAttributeProvidedByTheFilter() {
		AuthenticatedUser user = new AuthenticatedUser(1, "teacher", "Synthetic", UserType.TEACHER, false, "test");
		HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
				HttpServletRequest.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class },
				(proxy, method, args) -> {
					if ("getAttribute".equals(method.getName()) && "authenticatedUser".equals(args[0])) {
						return user;
					}
					throw new UnsupportedOperationException(method.getName());
				});

		assertSame(user, TeacherTaskServlet.authenticatedUser(request));
	}

	@Test
	void savedNoticeIsConsumedOnlyOnce() {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(TeacherTaskServlet.SAVED_NOTICE_ATTRIBUTE, Boolean.TRUE);
		HttpSession session = session(attributes);

		assertTrue(TeacherTaskServlet.consumeSavedNotice(session));
		assertNull(attributes.get(TeacherTaskServlet.SAVED_NOTICE_ATTRIBUTE));
		assertFalse(TeacherTaskServlet.consumeSavedNotice(session));
	}

	@Test
	void invalidSavedNoticeIsRemovedAndReported() {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(TeacherTaskServlet.SAVED_NOTICE_ATTRIBUTE, "saved");

		assertThrows(IllegalStateException.class,
				() -> TeacherTaskServlet.consumeSavedNotice(session(attributes)));
		assertNull(attributes.get(TeacherTaskServlet.SAVED_NOTICE_ATTRIBUTE));
	}

	private static HttpSession session(Map<String, Object> attributes) {
		return (HttpSession) Proxy.newProxyInstance(
				HttpSession.class.getClassLoader(),
				new Class<?>[] { HttpSession.class },
				(proxy, method, args) -> switch (method.getName()) {
					case "getAttribute" -> attributes.get(args[0]);
					case "removeAttribute" -> {
						attributes.remove(args[0]);
						yield null;
					}
					default -> throw new UnsupportedOperationException(method.getName());
				});
	}
}
