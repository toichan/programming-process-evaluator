package servlet.student;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.student.StudentNotificationControlTestSupport;
import control.student.StudentNotificationControlTestSupport.Access;
import dao.StudentNotificationDao;
import entity.StudentHomePage;
import entity.StudentNotification;
import entity.StudentNotificationPage;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

class StudentNotificationServletTest {
	private static final AuthenticatedUser STUDENT =
			new AuthenticatedUser(5, "synthetic-student", "synthetic", UserType.STUDENT, false, "test");
	private static final AuthenticatedUser TEACHER =
			new AuthenticatedUser(6, "synthetic-teacher", "synthetic", UserType.TEACHER, false, "test");

	private static final class FakeDao extends StudentNotificationDao {
		final List<String> calls = new ArrayList<>();

		@Override public Optional<StudentNotification> findRetained(long studentUserId, long notificationId) {
			calls.add("find:" + studentUserId + ":" + notificationId);
			return studentUserId == 5 && notificationId == 9
					? Optional.of(new StudentNotification(9, StudentNotification.TYPE_EVALUATION_COMPLETED,
							"t", "m", LocalDateTime.now(), false, 31L, 32L))
					: Optional.empty();
		}
		@Override public boolean markRead(long studentUserId, long notificationId) {
			calls.add("mark:" + studentUserId + ":" + notificationId);
			return true;
		}
		@Override public int markAllRead(long studentUserId) {
			calls.add("markAll:" + studentUserId);
			return 1;
		}
		@Override public StudentNotificationPage findPage(long studentUserId, int pageNumber) {
			calls.add("page:" + studentUserId + ":" + pageNumber);
			return new StudentNotificationPage(List.of(), 0, pageNumber, false);
		}
	}

	private static final class Exchange {
		final Map<String, Object> attributes = new HashMap<>();
		final Map<String, String> parameters = new HashMap<>();
		final Map<String, Object> sessionAttributes = new HashMap<>();
		final Map<String, String> headers = new HashMap<>();
		String servletPath;
		String forwarded;
		int status;
		String errorMessage;
		HttpSession session;
		HttpServletRequest request;
		HttpServletResponse response;

		Exchange(String servletPath, AuthenticatedUser user) {
			this.servletPath = servletPath;
			attributes.put("authenticatedUser", user);
			ClassLoader loader = getClass().getClassLoader();
			session = (HttpSession) Proxy.newProxyInstance(loader, new Class<?>[] { HttpSession.class },
					(proxy, method, args) -> switch (method.getName()) {
						case "getAttribute" -> sessionAttributes.get(args[0]);
						case "setAttribute" -> { sessionAttributes.put((String) args[0], args[1]); yield null; }
						case "removeAttribute" -> { sessionAttributes.remove(args[0]); yield null; }
						default -> throw new AssertionError("Unexpected session operation: " + method.getName());
					});
			request = (HttpServletRequest) Proxy.newProxyInstance(loader, new Class<?>[] { HttpServletRequest.class },
					(proxy, method, args) -> switch (method.getName()) {
						case "setCharacterEncoding" -> null;
						case "getServletPath" -> this.servletPath;
						case "getContextPath" -> "/app";
						case "getAttribute" -> attributes.get(args[0]);
						case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
						case "getParameter" -> parameters.get(args[0]);
						case "getSession" -> session;
						case "getRequestDispatcher" -> (RequestDispatcher) Proxy.newProxyInstance(loader,
								new Class<?>[] { RequestDispatcher.class }, (p, m, a) -> {
									if ("forward".equals(m.getName())) { forwarded = (String) args[0]; return null; }
									throw new AssertionError("Unexpected dispatcher operation: " + m.getName());
								});
						default -> throw new AssertionError("Unexpected request operation: " + method.getName());
					});
			response = (HttpServletResponse) Proxy.newProxyInstance(loader, new Class<?>[] { HttpServletResponse.class },
					(proxy, method, args) -> switch (method.getName()) {
						case "setHeader" -> { headers.put((String) args[0], (String) args[1]); yield null; }
						case "setStatus" -> { status = (Integer) args[0]; yield null; }
						case "sendError" -> {
							status = (Integer) args[0];
							errorMessage = args.length > 1 ? (String) args[1] : null;
							yield null;
						}
						case "encodeRedirectURL" -> args[0];
						default -> throw new AssertionError("Unexpected response operation: " + method.getName());
					});
		}

		Exchange withValidCsrf() {
			parameters.put("csrfToken", CsrfTokens.getOrCreate(session));
			return this;
		}
	}

	private static StudentNotificationServlet servlet(FakeDao dao, Access access) {
		StudentNotificationServlet.HomeLoader homes = user -> new StudentHomePage(null, null, List.of());
		return new StudentNotificationServlet(StudentNotificationControlTestSupport.create(dao, access), homes);
	}

	@Test void openRedirectsToPreciseSubmissionAfterMarkingOwnNotification() throws Exception {
		var dao = new FakeDao();
		var exchange = new Exchange(StudentNotificationServlet.OPEN_PATH, STUDENT).withValidCsrf();
		exchange.parameters.put("notificationId", "9");
		servlet(dao, Access.READABLE).doPost(exchange.request, exchange.response);
		assertEquals(303, exchange.status);
		assertEquals("/app/student/evaluation?assignmentId=31&submissionId=32", exchange.headers.get("Location"));
		assertEquals("no-store", exchange.headers.get("Cache-Control"));
		assertEquals(List.of("find:5:9", "mark:5:9"), dao.calls);
	}

	@Test void openOfMissingOrForeignNotificationIs404WithoutMarking() throws Exception {
		for (String id : new String[] { "10", "0", "-1", "abc", "", null, "99999999999999999999" }) {
			var dao = new FakeDao();
			var exchange = new Exchange(StudentNotificationServlet.OPEN_PATH, STUDENT).withValidCsrf();
			if (id != null) exchange.parameters.put("notificationId", id);
			servlet(dao, Access.READABLE).doPost(exchange.request, exchange.response);
			assertEquals(404, exchange.status, "notificationId=" + id);
			assertTrue(dao.calls.stream().noneMatch(call -> call.startsWith("mark")));
		}
	}

	@Test void inaccessibleTargetIsExplicitConflictWithoutMarking() throws Exception {
		var dao = new FakeDao();
		var exchange = new Exchange(StudentNotificationServlet.OPEN_PATH, STUDENT).withValidCsrf();
		exchange.parameters.put("notificationId", "9");
		servlet(dao, Access.UNAVAILABLE).doPost(exchange.request, exchange.response);
		assertEquals(409, exchange.status);
		assertEquals(StudentNotificationServlet.TARGET_UNAVAILABLE_MESSAGE, exchange.errorMessage);
		assertNull(exchange.headers.get("Location"));
		assertEquals(List.of("find:5:9"), dao.calls);
	}

	@Test void invalidAccountIsForbiddenWithoutMarking() throws Exception {
		var dao = new FakeDao();
		var exchange = new Exchange(StudentNotificationServlet.OPEN_PATH, STUDENT).withValidCsrf();
		exchange.parameters.put("notificationId", "9");
		servlet(dao, Access.ACCOUNT_INVALID).doPost(exchange.request, exchange.response);
		assertEquals(403, exchange.status);
		assertEquals(List.of("find:5:9"), dao.calls);
	}

	@Test void bulkReadMarksCurrentStudentAndRedirectsToList() throws Exception {
		var dao = new FakeDao();
		var exchange = new Exchange(StudentNotificationServlet.READ_ALL_PATH, STUDENT).withValidCsrf();
		servlet(dao, Access.READABLE).doPost(exchange.request, exchange.response);
		assertEquals(303, exchange.status);
		assertEquals("/app/student/notifications", exchange.headers.get("Location"));
		assertEquals(List.of("markAll:5"), dao.calls);
	}

	@Test void mutationsRequireCsrfAndStudentRole() throws Exception {
		for (String path : new String[] { StudentNotificationServlet.OPEN_PATH, StudentNotificationServlet.READ_ALL_PATH }) {
			var dao = new FakeDao();
			var missing = new Exchange(path, STUDENT);
			missing.parameters.put("notificationId", "9");
			servlet(dao, Access.READABLE).doPost(missing.request, missing.response);
			assertEquals(403, missing.status);

			var wrong = new Exchange(path, STUDENT);
			CsrfTokens.getOrCreate(wrong.session);
			wrong.parameters.put("csrfToken", "forged");
			wrong.parameters.put("notificationId", "9");
			servlet(dao, Access.READABLE).doPost(wrong.request, wrong.response);
			assertEquals(403, wrong.status);

			var teacher = new Exchange(path, TEACHER).withValidCsrf();
			teacher.parameters.put("notificationId", "9");
			servlet(dao, Access.READABLE).doPost(teacher.request, teacher.response);
			assertEquals(403, teacher.status);

			var anonymous = new Exchange(path, null).withValidCsrf();
			servlet(dao, Access.READABLE).doPost(anonymous.request, anonymous.response);
			assertEquals(403, anonymous.status);
			assertEquals(List.of(), dao.calls);
		}
	}

	@Test void getNeverMutatesAndPostToListIsNotAllowed() throws Exception {
		var dao = new FakeDao();
		for (String path : new String[] { StudentNotificationServlet.OPEN_PATH, StudentNotificationServlet.READ_ALL_PATH }) {
			var exchange = new Exchange(path, STUDENT).withValidCsrf();
			exchange.parameters.put("notificationId", "9");
			servlet(dao, Access.READABLE).doGet(exchange.request, exchange.response);
			assertEquals(405, exchange.status);
			assertEquals("POST", exchange.headers.get("Allow"));
		}
		var list = new Exchange(StudentNotificationServlet.LIST_PATH, STUDENT).withValidCsrf();
		servlet(dao, Access.READABLE).doPost(list.request, list.response);
		assertEquals(405, list.status);
		assertEquals(List.of(), dao.calls);
	}

	@Test void listForwardsAgreedAttributes() throws Exception {
		var dao = new FakeDao();
		var exchange = new Exchange(StudentNotificationServlet.LIST_PATH, STUDENT);
		exchange.parameters.put("page", "2");
		servlet(dao, Access.READABLE).doGet(exchange.request, exchange.response);
		assertEquals("/WEB-INF/student/notifications/notifications.jsp", exchange.forwarded);
		assertEquals(2, ((StudentNotificationPage) exchange.attributes.get("notificationPage")).getPageNumber());
		assertEquals("student", exchange.attributes.get("screenDesign"));
		assertEquals("通知", exchange.attributes.get("screenPageTitle"));
		assertEquals("/css/student/notifications/notifications.css", exchange.attributes.get("screenStylesheet"));
		assertEquals("synthetic-student", exchange.attributes.get("studentLoginId"));
		assertEquals(CsrfTokens.getOrCreate(exchange.session), exchange.attributes.get("csrfToken"));
		assertEquals(List.of("page:5:2"), dao.calls);
	}

	@Test void listRejectsInvalidPageAndNonStudents() throws Exception {
		for (String page : new String[] { "0", "-1", "x", "", "1.5", "1234567" }) {
			var dao = new FakeDao();
			var exchange = new Exchange(StudentNotificationServlet.LIST_PATH, STUDENT);
			exchange.parameters.put("page", page);
			servlet(dao, Access.READABLE).doGet(exchange.request, exchange.response);
			assertEquals(400, exchange.status, "page=" + page);
			assertEquals(List.of(), dao.calls);
		}
		var dao = new FakeDao();
		var teacher = new Exchange(StudentNotificationServlet.LIST_PATH, TEACHER);
		servlet(dao, Access.READABLE).doGet(teacher.request, teacher.response);
		assertEquals(403, teacher.status);
		assertEquals(List.of(), dao.calls);
	}

	@Test void parsesPageNumbers() {
		assertEquals(1, StudentNotificationServlet.pageNumber(null));
		assertEquals(1, StudentNotificationServlet.pageNumber("1"));
		assertEquals(42, StudentNotificationServlet.pageNumber("042"));
		assertNull(StudentNotificationServlet.pageNumber("0"));
		assertNull(StudentNotificationServlet.pageNumber("+1"));
	}
}
