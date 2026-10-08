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
	private static HttpServletRequest request(Map<String, String> parameters) {
		return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
					if (method.getName().equals("getParameter")) return parameters.get(args[0]);
					return null;
				});
	}
}
