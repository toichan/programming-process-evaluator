package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class TeacherExerciseServletTest {
	@Test void rejectsDuplicateMalformedAndOverflowIds() {
		for (String value : new String[] {"-1","0","1x"," 1","9223372036854775808"}) {
			assertThrows(IllegalArgumentException.class, () -> TeacherExerciseServlet.optionalId(request(Map.of("studentId",new String[]{value})), "studentId"));
		}
		assertThrows(IllegalArgumentException.class, () -> TeacherExerciseServlet.optionalId(request(Map.of("studentId",new String[]{"1","2"})), "studentId"));
		assertEquals(1L, TeacherExerciseServlet.optionalId(request(Map.of("studentId",new String[]{"1"})), "studentId"));
		assertNull(TeacherExerciseServlet.optionalId(request(Map.of()), "studentId"));
	}
	@Test void preservesPrototypeControlsAndReadOnlySafeRendering() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/exercise/exercise.jsp"));
		for (String id : new String[]{"visibleCount","visibleFileCount","latestUpdatedAt","searchInput","filterSchool",
				"filterClass","filterConsent","exerciseTable","exerciseDetailModal","detailFilePath","exerciseTree","exerciseCodeViewer"})
			assertTrue(jsp.contains("id=\""+id+"\""), id);
		assertTrue(jsp.contains("modal-fullscreen")); assertTrue(jsp.contains("readonly"));
		assertTrue(jsp.contains("page-start.jspf")); assertTrue(jsp.contains("page-end.jspf"));
		String js = Files.readString(Path.of("src/main/webapp/js/teacher/exercise/exercise.js"));
		assertTrue(js.contains("readOnly: true")); assertTrue(js.contains("createPageFeedback"));
		assertTrue(js.contains("AbortController")); assertTrue(js.contains("detailSequence"));
		assertFalse(js.contains("innerHTML")); assertFalse(js.contains("alert(")); assertFalse(js.contains("confirm("));
	}
	private static HttpServletRequest request(Map<String,String[]> values) {
		return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
				new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
					if (method.getName().equals("getParameterValues")) return values.get(args[0]);
					throw new UnsupportedOperationException(method.getName());
				});
	}
}
