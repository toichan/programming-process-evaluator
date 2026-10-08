package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class TeacherReviewServletTest {
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
