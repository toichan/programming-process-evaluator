package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TeacherTaskFormTest {
	@Test
	void readsStrictUtf8AndPreservesRepeatedFormValues() throws Exception {
		String body = "taskName=%E8%AA%B2%E9%A1%8C&testCaseInputs=%E3%81%82&testCaseInputs=%E3%81%84";

		Map<String, List<String>> values = TeacherTaskForm.read(
				new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), 1024);

		assertEquals(List.of("課題"), values.get("taskName"));
		assertEquals(List.of("あ", "い"), values.get("testCaseInputs"));
	}

	@Test
	void buildsTypedDraftInputFromAlignedArrays() {
		TeacherTaskForm form = TeacherTaskForm.parse(validCreateValues());

		assertEquals("createDraft", form.action());
		assertEquals(0, form.taskId());
		assertEquals("課題", form.input().title());
		assertEquals(List.of("条件分岐", "入力"), form.input().features());
		assertEquals(List.of("a", "b"), form.input().testCases().stream().map(c -> c.getInput()).toList());
		assertEquals("ヒント", form.input().hints().get(0).hint().getTitle());
		assertEquals(9, form.input().classAssignments().get(0).classroomId());
		assertNull(form.input().classAssignments().get(0).publishAt());
	}

	@Test
	void rejectsDuplicateScalarFields() {
		Map<String, List<String>> values = new java.util.HashMap<>(validCreateValues());
		values.put("taskName", List.of("first", "second"));

		assertThrows(IllegalArgumentException.class, () -> TeacherTaskForm.parse(values));
	}

	@Test
	void rejectsMismatchedRepeatedInputCounts() {
		Map<String, List<String>> values = new java.util.HashMap<>(validCreateValues());
		values.put("testCaseOutputs", List.of("only one"));

		assertThrows(IllegalArgumentException.class, () -> TeacherTaskForm.parse(values));
	}

	@Test
	void rejectsUnsupportedActionAndOversizedBody() throws Exception {
		Map<String, List<String>> values = new java.util.HashMap<>(validCreateValues());
		values.put("action", List.of("publish"));
		assertThrows(IllegalArgumentException.class, () -> TeacherTaskForm.parse(values));

		assertThrows(TeacherTaskForm.RequestTooLargeException.class,
				() -> TeacherTaskForm.read(new ByteArrayInputStream(new byte[11]), 10));
	}

	private static Map<String, List<String>> validCreateValues() {
		return Map.ofEntries(
				Map.entry("action", List.of("createDraft")),
				Map.entry("csrfToken", List.of("csrf")),
				Map.entry("requestToken", List.of("19db2fd2-1d73-4f51-88d7-222222222222")),
				Map.entry("taskId", List.of("0")),
				Map.entry("expectedVersion", List.of("0")),
				Map.entry("schoolTargets", List.of("5")),
				Map.entry("classTargets", List.of("9")),
				Map.entry("lateSubmissionPolicy", List.of("allow")),
				Map.entry("taskName", List.of("課題")),
				Map.entry("features", List.of("条件分岐;入力")),
				Map.entry("testCaseIds", List.of("0", "0")),
				Map.entry("testCaseInputs", List.of("a", "b")),
				Map.entry("testCaseOutputs", List.of("x", "y")),
				Map.entry("hintIds", List.of("0")),
				Map.entry("hintTitles", List.of("ヒント")),
				Map.entry("hintContents", List.of("説明")));
	}
}
