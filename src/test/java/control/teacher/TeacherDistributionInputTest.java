package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import entity.StudentExerciseEntry;
import entity.TeacherDistributionInput;
import entity.TeacherDistributionInput.TemplateItem;

class TeacherDistributionInputTest {
	@Test
	void createsParentFoldersAndNormalizesPythonFileNames() {
		var input = new TeacherDistributionInput(0, 0, "Warm-up", "Lesson",
				List.of(new TemplateItem("week1/answer", StudentExerciseEntry.Type.FILE, "print(1)")),
				List.of(), UUID.randomUUID().toString());

		assertEquals(List.of("week1", "week1/answer.py"),
				input.items().stream().map(TemplateItem::path).toList());
	}

	@Test
	void rejectsConflictingPathsAndTraversal() {
		assertThrows(IllegalArgumentException.class, () -> new TeacherDistributionInput(0, 0, "Template", "Lesson",
				List.of(new TemplateItem("lesson.py", StudentExerciseEntry.Type.FILE, "print(1)"),
						new TemplateItem("lesson.py", StudentExerciseEntry.Type.FILE, "print(2)")),
				List.of(), UUID.randomUUID().toString()));
		assertThrows(IllegalArgumentException.class, () -> new TeacherDistributionInput(0, 0, "Template", "Lesson",
				List.of(new TemplateItem("../main.py", StudentExerciseEntry.Type.FILE, "")),
				List.of(), UUID.randomUUID().toString()));
	}

	@Test
	void rejectsPathsThatWouldExceedTheStudentExercisePathLimitAfterDelivery() {
		String relativePath = "a".repeat(200) + "/" + "b".repeat(200) + "/"
				+ "c".repeat(200) + "/" + "d".repeat(200) + "/main.py";
		assertThrows(IllegalArgumentException.class, () -> new TeacherDistributionInput(0, 0, "Template",
				"r".repeat(230),
				List.of(new TemplateItem(relativePath, StudentExerciseEntry.Type.FILE, "")),
				List.of(), UUID.randomUUID().toString()));
	}

	@Test
	void rejectsDuplicateTargetsAndInvalidRequestTokens() {
		assertThrows(IllegalArgumentException.class, () -> new TeacherDistributionInput(0, 0, "Template", "Lesson",
				List.of(new TemplateItem("main.py", StudentExerciseEntry.Type.FILE, "")),
				List.of(new TeacherDistributionInput.Target(4, null), new TeacherDistributionInput.Target(4, null)),
				UUID.randomUUID().toString()));
		assertThrows(IllegalArgumentException.class, () -> new TeacherDistributionInput(0, 0, "Template", "Lesson",
				List.of(new TemplateItem("main.py", StudentExerciseEntry.Type.FILE, "")),
				List.of(), "bad-token"));
	}
}
