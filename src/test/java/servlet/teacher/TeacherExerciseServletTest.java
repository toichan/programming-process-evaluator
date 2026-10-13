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
		assertTrue(js.contains("event.target === form.elements.search"));
		assertTrue(js.contains("event.target instanceof HTMLSelectElement"));
		assertFalse(js.contains("innerHTML")); assertFalse(js.contains("alert(")); assertFalse(js.contains("confirm("));
		String servlet = Files.readString(Path.of("src/main/java/servlet/teacher/TeacherExerciseServlet.java"));
		String css = Files.readString(Path.of("src/main/webapp/css/teacher/exercise/exercise.css"));
		assertTrue(servlet.contains("\"screenBodyClass\", \"teacher-exercise-review-screen\""));
		assertTrue(css.contains(".teacher-exercise-review-screen .header-actions"));
	}
	@Test void selectedDownloadInputsAreBoundedAndStrict() {
		for (String value : new String[] {"", "1,", ",1", "0", "-1", "1, 2", "1x", "9223372036854775808"}) {
			assertThrows(IllegalArgumentException.class, () -> TeacherExerciseServlet.selectedIds(
					request(Map.of("entryIds", new String[]{value}))), value);
		}
		assertThrows(IllegalArgumentException.class, () -> TeacherExerciseServlet.selectedIds(request(Map.of())));
		assertThrows(IllegalArgumentException.class, () -> TeacherExerciseServlet.selectedIds(
				request(Map.of("entryIds", new String[]{"1","2"}))));
		assertEquals(java.util.List.of(1L, 2L, 1L), TeacherExerciseServlet.selectedIds(
				request(Map.of("entryIds", new String[]{"1,2,1"}))));
	}
	@Test void studentStyleExplorerAndSettingsNeverSendStudentMutations() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/exercise/exercise.jsp"));
		for (String id : new String[]{"exerciseSelectMode", "exerciseSelectionFinish", "exerciseClearSelected",
				"downloadExerciseSelected", "exerciseEditorSettings", "editorFontSize", "editorTheme"})
			assertTrue(jsp.contains("id=\""+id+"\""), id);
		for (String id : new String[]{"newFolderButton", "newFileButton", "exerciseMoveSelected", "exerciseTrashSelected"})
			assertFalse(jsp.contains("id=\""+id+"\""), id);
		String js = Files.readString(Path.of("src/main/webapp/js/teacher/exercise/exercise.js"));
		assertTrue(js.contains("window.PPECodeEditor.create"));
		assertTrue(js.contains("window.PPECodeEditor.applyPreferences"));
		assertTrue(js.contains("lesson-tree-folder-toggle"));
		assertTrue(js.contains("function toggleFolder(key)"));
		assertTrue(js.contains("if (folder) toggleFolder(key)"));
		assertTrue(js.contains("row.addEventListener('click'"));
		assertTrue(js.contains("event.target.closest('.lesson-tree-folder-header') !== row"));
		assertTrue(js.contains("toggle.addEventListener('click', () => toggleFolder(key))"));
		assertTrue(js.contains("lesson-entry-check"));
		assertTrue(js.contains("selectedRoots()"));
		assertFalse(js.contains("method: 'POST'")); assertFalse(js.contains("/student/editor/preferences"));
	}
	@Test void progressPopulatesCodeBeforeShowingModalAndRefreshesBeforePaint() throws Exception {
		String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/teacher/progress/progress.jsp"));
		assertTrue(jsp.contains("detail-table-wrap table-responsive"));
		String css = Files.readString(Path.of("src/main/webapp/css/teacher/progress/progress.css"));
		int wrapper = css.indexOf("#historyDetailModal .detail-table-wrap");
		assertTrue(css.substring(wrapper, css.indexOf("}", wrapper)).contains("overflow-x: auto"));
		assertFalse(css.contains("repeat(3, minmax(120px, 1fr))"));
		assertTrue(css.contains(".teacher-progress-screen .history-hero > *"));
		String js = Files.readString(Path.of("src/main/webapp/js/teacher/progress/progress.js"));
		int start = js.indexOf("async function openHistoryDetail");
		int populated = js.indexOf("setLatestCode(detail.latestCode.code)", start);
		int shown = js.indexOf("bootstrap.Modal.getOrCreateInstance(modal).show()", start);
		assertTrue(populated > start && shown > populated);
		assertTrue(js.indexOf("latestCodeEditor?.refresh()", shown) > shown);
		assertTrue(js.contains("button.textContent = '読み込み中…'"));
		assertTrue(js.contains("modal.classList.remove('fade')"));
	}
	private static HttpServletRequest request(Map<String,String[]> values) {
		return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
				new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
					if (method.getName().equals("getParameterValues")) return values.get(args[0]);
					throw new UnsupportedOperationException(method.getName());
				});
	}
}
