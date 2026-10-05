package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.EditorHint;
import entity.EditorTestCase;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.HintInput;
import entity.TeacherTaskInput.LateSubmissionPolicy;

class TeacherTaskInputValidatorTest {
	private final TeacherTaskInputValidator validator = new TeacherTaskInputValidator();

	@Test
	void rejectsMissingTaskTitle() {
		assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize(input(" \t", "ok", List.of(),
				List.of(), List.of())));
	}

	@Test
	void enforcesDatabaseCharacterAndUtf8ByteCapacities() {
		assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize(input(
				"あ".repeat(256), "ok", List.of(), List.of(), List.of())));
		assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize(input(
				"Task", "あ".repeat(21_846), List.of(), List.of(), List.of())));
	}

	@Test
	void normalizesNullOptionalBodyFieldsAndPreservesWhitespace() {
		TeacherTaskInput raw = new TeacherTaskInput(
				" Task\n",
				null,
				null,
				null,
				null,
				null,
				"print('x')\n",
				List.of(" feature "),
				List.of(new EditorTestCase(0, null, null, null, 0)),
				List.of(new HintInput(0, 99, new EditorHint(null, null, null, null))),
				List.of(),
				1);

		TeacherTaskInput normalized = validator.validateAndNormalize(raw);

		assertEquals(" Task\n", normalized.title());
		assertEquals("", normalized.description());
		assertEquals("print('x')\n", normalized.initialCode());
		assertEquals(" feature ", normalized.features().get(0));
		assertEquals("", normalized.testCases().get(0).getInput());
		assertEquals(1, normalized.testCases().get(0).getOrder());
		assertEquals("", normalized.hints().get(0).hint().getTitle());
		assertEquals("", normalized.hints().get(0).hint().getContent());
		assertEquals(1, normalized.hints().get(0).order());
	}

	@Test
	void rejectsNulAndUnpairedSurrogateCharacters() {
		assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalize(input("Ta" + (char) 0 + "sk", "", List.of(), List.of(), List.of())));
		assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalize(input("Task" + (char) 0xD800, "", List.of(), List.of(), List.of())));
	}

	@Test
	void rejectsMissingSchool() {
		TeacherTaskInput missingSchool = new TeacherTaskInput(
				"Task", null, null, "", null, null, null,
				List.of(), List.of(), List.of(), List.of(), 0);

		assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize(missingSchool));
	}

	@Test
	void rejectsDueDateThatIsNotAfterScheduledPublication() {
		LocalDateTime publishAt = LocalDateTime.of(2026, 10, 5, 12, 0);
		ClassAssignmentInput assignment = new ClassAssignmentInput(
				0, 10, publishAt, publishAt, LateSubmissionPolicy.ALLOW);

		assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndNormalize(input("Task", "", List.of(), List.of(), List.of(assignment))));
	}

	private static TeacherTaskInput input(
			String title,
			String description,
			List<EditorTestCase> testCases,
			List<HintInput> hints,
			List<ClassAssignmentInput> assignments) {
		return new TeacherTaskInput(
				title, null, null, description, null, null, null,
				List.of(), testCases, hints, assignments, 1);
	}
}
