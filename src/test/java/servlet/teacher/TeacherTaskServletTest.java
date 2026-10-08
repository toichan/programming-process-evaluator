package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.TeacherTaskInput;
import entity.UserCredential.UserType;

class TeacherTaskServletTest {
	@Test
	void parsesDuplicateTaskOperation() {
		var operation = TeacherTaskServlet.parseTaskStateOperation(Map.of(
				"action", List.of("duplicateTask"),
				"csrfToken", List.of("csrf"),
				"requestToken", List.of("request"),
				"taskId", List.of("14"),
				"expectedVersion", List.of("3")));

		assertEquals("duplicateTask", operation.action());
		assertEquals(14, operation.taskId());
		assertEquals(3, operation.expectedVersion());
	}

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

	@Test
	void parsesDeadlineExtensionAsAnAssignmentScopedChange() {
		var operation = TeacherTaskServlet.parsePublishedAssignmentOperation(Map.of(
				"action", List.of("extendTaskAssignmentDeadline"),
				"csrfToken", List.of("csrf"),
				"requestToken", List.of("request"),
				"taskId", List.of("14"),
				"expectedVersion", List.of("3"),
				"assignmentId", List.of("22"),
				"newDueAt", List.of("2030-05-01T12:30")));

		assertEquals("extendTaskAssignmentDeadline", operation.action());
		assertEquals(14, operation.taskId());
		assertEquals(3, operation.expectedVersion());
		assertEquals(22, operation.assignmentId());
		assertEquals(0, operation.classroomId());
		assertEquals(LocalDateTime.of(2030, 5, 1, 12, 30), operation.dueAt());
	}

	@Test
	void parsesOptionalPublicationAndDeadlineForAClassAssignment() {
		var operation = TeacherTaskServlet.parsePublishedAssignmentOperation(Map.of(
				"action", List.of("addTaskClassAssignment"),
				"csrfToken", List.of("csrf"),
				"requestToken", List.of("request"),
				"taskId", List.of("14"),
				"expectedVersion", List.of("3"),
				"classroomId", List.of("22"),
				"publishAt", List.of(""),
				"dueAt", List.of(""),
				"lateSubmissionPolicy", List.of("deny")));

		assertEquals(0, operation.assignmentId());
		assertEquals(22, operation.classroomId());
		assertNull(operation.publishAt());
		assertNull(operation.dueAt());
		assertEquals(TeacherTaskInput.LateSubmissionPolicy.DENY, operation.lateSubmissionPolicy());
	}

	@Test
	void rejectsUnexpectedFieldsInPublishedAssignmentOperations() {
		assertThrows(IllegalArgumentException.class,
				() -> TeacherTaskServlet.parsePublishedAssignmentOperation(Map.of(
						"action", List.of("extendTaskAssignmentDeadline"),
						"csrfToken", List.of("csrf"),
						"requestToken", List.of("request"),
						"taskId", List.of("14"),
						"expectedVersion", List.of("3"),
						"assignmentId", List.of("22"),
						"newDueAt", List.of("2030-05-01T12:30"),
						"unexpected", List.of("value"))));
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
