package entity;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;

class ExerciseTreeTest {
	@Test
	void usesRealUpdatedTimeAsStringForExplorerSorting() {
		var time = LocalDateTime.of(2026, 10, 4, 9, 0);
		var entry = new StudentExerciseEntry(2, 1, null, StudentExerciseEntry.Type.FILE,
				"code.py", "code.py", null, "", StudentExerciseEntry.Status.ACTIVE, time);
		var tree = ExerciseTree.from(new StudentExercisePage(List.of(), 1L, null, List.of(entry),
				EditorPreferences.defaults(), null));
		assertEquals("2026-10-04T09:00", tree.entries().get(0).updatedAt());
		assertTrue(new Gson().toJson(tree).contains("\"updatedAt\":\"2026-10-04T09:00\""));
	}

	@Test
	void serializesSelectedCodeAndSavedExecutionWithoutJavaTimeReflection() {
		var result = new PythonExecutionResult("succeeded", 0, "学\n", "", false, false, null);
		var page = new StudentExercisePage(List.of(), 1L, 2L, List.of(), EditorPreferences.defaults(),
				new ExerciseExecutionResult(3, result, "入力\n", LocalDateTime.of(2026, 10, 4, 6, 0)));
		var tree = ExerciseTree.from(page);
		assertEquals(2L, tree.selectedEntryId());
		assertEquals("2026-10-04T06:00", tree.latestExecution().executedAt());
		assertEquals("学\n", tree.latestExecution().result().getStandardOutput());
		assertTrue(new Gson().toJson(tree).contains("\"selectedEntryId\":2"));
	}

	@Test
	void representsNoSelectedExecutionExplicitly() {
		var tree = ExerciseTree.from(new StudentExercisePage(List.of(), null, null, List.of(),
				EditorPreferences.defaults(), null));
		assertNull(tree.latestExecution());
		assertNull(tree.selectedEntryId());
	}
}
