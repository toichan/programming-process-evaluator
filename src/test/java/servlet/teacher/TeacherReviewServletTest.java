package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;
import org.junit.jupiter.api.Test;

class TeacherReviewServletTest {
	@Test void interactiveMutationsRequireTeacherRouteAndCsrfBeforeDatabaseOrRunner() throws Exception {
		for (String action : new String[] { "preview-start", "preview-input", "preview-cancel" }) {
			assertEquals(403, deniedPost(action, "/teacher/submissions", UserType.STUDENT, -1));
			assertEquals(403, deniedPost(action, "/teacher/submissions", UserType.ADMIN, -1));
			assertEquals(403, deniedPost(action, "/teacher/submissions", UserType.TEACHER, -1));
			assertEquals(405, deniedPost(action, "/teacher/evaluations", UserType.TEACHER, -1));
			assertEquals(413, deniedPost(action, "/teacher/submissions", UserType.TEACHER, 262145));
		}
	}
	private static int deniedPost(String action, String route, UserType role, long bytes) throws Exception {
		var user = new AuthenticatedUser(1, "synthetic", "synthetic", role, false, "test");
		var request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> switch (method.getName()) {
					case "getAttribute" -> "authenticatedUser".equals(args[0]) ? user : null;
					case "getServletPath" -> route;
					case "getContentLengthLong" -> bytes;
					case "getParameter" -> "action".equals(args[0]) ? action : null;
					default -> null;
				});
		int[] status = { 200 };
		var response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
				new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> {
					if ("sendError".equals(method.getName())) status[0] = (int) args[0];
					return null;
				});
		new TeacherReviewServlet().doPost(request, response);
		return status[0];
	}
	@Test void validatesClientFiltersAndIds() {
		assertThrows(IllegalArgumentException.class, () -> TeacherReviewServlet.optionalId(request(Map.of("submissionId", "1 OR 1=1")), "submissionId"));
		assertThrows(IllegalArgumentException.class, () -> TeacherReviewServlet.optionalId(request(Map.of("submissionId", "9999999999999999999")), "submissionId"));
		assertThrows(IllegalArgumentException.class, () -> TeacherReviewServlet.filter(request(Map.of("level", "4294967297"))));
		assertEquals("not_agreed", TeacherReviewServlet.filter(request(Map.of("consent", "not_agreed"))).consent());
		assertEquals(12L, TeacherReviewServlet.optionalId(request(Map.of("submissionId", "12")), "submissionId"));
	}
	@Test void uiUsesSharedTemplatesFeedbackAndTextOnlyDom() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/review/review.jsp"));
		String js = Files.readString(Path.of("src/main/webapp/js/teacher/review/review.js"));
		assertTrue(jsp.contains("/WEB-INF/template/page-start.jspf"));
		assertTrue(jsp.contains("/WEB-INF/template/page-end.jspf"));
		assertTrue(js.contains("createPageFeedback"));
		assertFalse(js.contains("innerHTML"));
		assertFalse(js.contains("alert("));
		assertFalse(js.contains("confirm("));
		assertTrue(js.contains("csrfToken"));
		assertTrue(jsp.contains("読取専用"));
		String home = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/home.jsp"));
		assertTrue(home.contains("features.contains('submission-review')"));
		assertTrue(home.contains("features.contains('evaluation-review')"));
	}
	@Test void submissionSurfacePreservesPrototypeAndUsesRealEndpoints() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/submission/submission.jsp"));
		String js = Files.readString(Path.of("src/main/webapp/js/teacher/submission/submission.js"));
		String css = Files.readString(Path.of("src/main/webapp/css/teacher/submission/submission.css"));
		for (String id : new String[] { "visibleCount", "fullMatchCount", "avgMatchRate", "submissionTable",
				"ioResultTableBody", "submittedCodeView", "workCodeEditor", "detailPagerText" }) assertTrue(jsp.contains("id=\"" + id + "\""), id);
		assertTrue(jsp.contains("modal-fullscreen"));
		assertTrue(jsp.contains("detail-grid"));
		assertTrue(jsp.contains("readonly"));
		assertTrue(jsp.contains("CSVエクスポート"));
		assertTrue(jsp.contains("ファイルを一括ダウンロード"));
		assertTrue(js.contains("CodeMirror.fromTextArea"));
		assertTrue(js.contains("csrfToken"));
		assertTrue(js.contains("previewFeedback"));
		assertTrue(jsp.contains("/WEB-INF/student/shared/execution-terminal.jspf"));
		assertTrue(Files.readString(Path.of("src/main/webapp/css/teacher/submission/submission.css")).startsWith("@import url(\"../../student/shared/execution-terminal.css\");"));
		assertFalse(jsp.contains("id=\"previewInput\""));
		for (String action : new String[] { "preview-start", "preview-session", "preview-input", "preview-cancel", "pagehide", "keepalive" })
			assertTrue(js.contains(action), action);
		assertTrue(js.contains("copy.segments"));
		assertTrue(js.contains("await stopExecution()"));
		assertFalse(js.contains("innerHTML"));
		assertFalse(js.contains("simulateExecution"));
		assertFalse(js.contains("alert("));
		assertTrue(css.contains("grid-template-columns: 1.2fr 1fr"));
		for (String sort : new String[] { "student", "school", "class", "task", "difficulty", "match", "submitted", "consent" })
			assertEquals(sort, TeacherReviewServlet.filter(request(Map.of("sort", sort))).sort());
	}
	@Test void evaluationSurfacePreservesPrototypeAndUsesSavedData() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/evaluation/evaluation.jsp"));
		String js = Files.readString(Path.of("src/main/webapp/js/teacher/evaluation/evaluation.js"));
		for (String id : new String[] { "visibleCount", "avgThinkingTop", "avgAttitudeTop", "summaryTotal",
				"summaryEvaluated", "summaryUnevaluated", "evaluationTable", "evaluationDetailModal",
				"evaluationVersions", "codeLogTimes", "previousLog", "nextLog", "evaluationSubmission",
				"openLogsBottom", "logReasonFilters" })
			assertTrue(jsp.contains("id=\"" + id + "\""), id);
		assertTrue(jsp.contains("modal-fullscreen"));
		assertTrue(jsp.contains("/WEB-INF/template/page-start.jspf"));
		assertTrue(jsp.contains("読取専用"));
		assertTrue(js.contains("createPageFeedback"));
		assertFalse(js.contains("innerHTML"));
		assertFalse(js.contains("alert("));
		assertFalse(js.contains("confirm("));
		assertFalse(js.contains("Math.round(evaluated * 0.35)"));
		for (String sort : new String[] { "student", "school", "class", "task", "difficulty", "thinking", "attitude", "evaluated", "consent" })
			assertEquals(sort, TeacherReviewServlet.filter(request(Map.of("sort", sort))).sort());
		assertEquals("=4, >3", TeacherReviewServlet.filter(request(Map.of("thinking", "=4, >3"))).thinking());
		for (String expression : new String[] { "=0", ">6", "4", "=4,", "NaN", ">=Infinity" })
			assertThrows(IllegalArgumentException.class, () -> TeacherReviewServlet.filter(request(Map.of("attitude", expression))));
	}
	private static HttpServletRequest request(Map<String, String> parameters) {
		return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
					if (method.getName().equals("getParameter")) return parameters.get(args[0]);
					return null;
				});
	}
}
