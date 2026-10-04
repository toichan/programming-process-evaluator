package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

class TeacherPromptServletTest {
	@Test
	void keepsMissingPromptVersionNullableWhenNoVersionIsSelected() {
		assertNull(TeacherPromptServlet.effectiveVersionId(null, null));
		assertEquals(12L, TeacherPromptServlet.effectiveVersionId(12L, null));
	}

	@Test
	void rejectsUnauthenticatedGet() throws Exception {
		AtomicInteger status = new AtomicInteger();
		HttpServletRequest request = request(new AtomicBoolean());

		new TeacherPromptServlet().doGet(request, response(status));

		assertEquals(HttpServletResponse.SC_FORBIDDEN, status.get());
	}

	@Test
	void rejectsUnauthenticatedPostBeforeReadingFormBody() throws Exception {
		AtomicInteger status = new AtomicInteger();
		AtomicBoolean readForm = new AtomicBoolean();
		HttpServletRequest request = request(readForm);

		new TeacherPromptServlet().doPost(request, response(status));

		assertEquals(HttpServletResponse.SC_FORBIDDEN, status.get());
		assertFalse(readForm.get());
	}

	private static HttpServletRequest request(AtomicBoolean readForm) {
		return (HttpServletRequest) Proxy.newProxyInstance(
				HttpServletRequest.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class },
				(proxy, method, args) -> {
					if ("getContentType".equals(method.getName())
							|| "getContentLengthLong".equals(method.getName())
							|| "getInputStream".equals(method.getName())) {
						readForm.set(true);
					}
					return null;
				});
	}

	private static HttpServletResponse response(AtomicInteger status) {
		return (HttpServletResponse) Proxy.newProxyInstance(
				HttpServletResponse.class.getClassLoader(),
				new Class<?>[] { HttpServletResponse.class },
				(proxy, method, args) -> {
					if ("sendError".equals(method.getName())) {
						status.set((int) args[0]);
					}
					return null;
				});
	}
}
