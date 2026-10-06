package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class TeacherPromptServletTest {
	@Test
	void keepsMissingPromptVersionNullableWhenNoVersionIsSelected() {
		assertNull(TeacherPromptServlet.effectiveVersionId(null, null));
		assertEquals(12L, TeacherPromptServlet.effectiveVersionId(12L, null));
	}

	@Test
	void allowsCreatingTheFirstPromptDraftForASelectedTask() {
		assertTrue(TeacherPromptServlet.isEditableDraft(true, null));
		assertFalse(TeacherPromptServlet.isEditableDraft(false, null));
	}

	@Test
	void rejectsUnauthenticatedGet() throws Exception {
		AtomicInteger status = new AtomicInteger();
		HttpServletRequest request = request(new AtomicBoolean(), Map.of(), null);

		new TeacherPromptServlet().doGet(request, response(status));

		assertEquals(HttpServletResponse.SC_FORBIDDEN, status.get());
	}

	@Test
	void rejectsNonTeacherBeforeParsingJobStatusParameters() throws Exception {
		AtomicInteger status = new AtomicInteger();
		AuthenticatedUser student = new AuthenticatedUser(
				42, "synthetic-student", "Synthetic student", UserType.STUDENT, false, "test");

		new TeacherPromptServlet().doGet(
				request(new AtomicBoolean(), Map.of("jobId", "invalid"), student), response(status));

		assertEquals(HttpServletResponse.SC_FORBIDDEN, status.get());
	}

	@Test
	void rejectsInvalidJobIdAndMissingTaskIdWithBadRequest() throws Exception {
		AtomicInteger status = new AtomicInteger();
		AuthenticatedUser teacher = teacher();

		new TeacherPromptServlet().doGet(
				request(new AtomicBoolean(), Map.of("taskId", "12", "jobId", "0"), teacher),
				response(status));
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());

		status.set(0);
		new TeacherPromptServlet().doGet(
				request(new AtomicBoolean(), Map.of("taskId", "12", "jobId", "not-a-number"), teacher),
				response(status));
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());

		status.set(0);
		new TeacherPromptServlet().doGet(
				request(new AtomicBoolean(), Map.of("jobId", "12"), teacher), response(status));
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
	}

	@Test
	void rejectsUnauthenticatedPostBeforeReadingFormBody() throws Exception {
		AtomicInteger status = new AtomicInteger();
		AtomicBoolean readForm = new AtomicBoolean();
		HttpServletRequest request = request(readForm, Map.of(), null);

		new TeacherPromptServlet().doPost(request, response(status));

		assertEquals(HttpServletResponse.SC_FORBIDDEN, status.get());
		assertFalse(readForm.get());
	}

	private static AuthenticatedUser teacher() {
		return new AuthenticatedUser(7, "synthetic-teacher", "Synthetic teacher",
				UserType.TEACHER, false, "test");
	}

	private static HttpServletRequest request(
			AtomicBoolean readForm,
			Map<String, String> parameters,
			AuthenticatedUser authenticatedUser) {
		return (HttpServletRequest) Proxy.newProxyInstance(
				HttpServletRequest.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class },
				(proxy, method, args) -> {
					if ("getParameter".equals(method.getName())) {
						return parameters.get(args[0]);
					}
					if ("getAttribute".equals(method.getName())
							&& "authenticatedUser".equals(args[0])) {
						return authenticatedUser;
					}
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
