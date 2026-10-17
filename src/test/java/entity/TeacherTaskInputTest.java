package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.LateSubmissionPolicy;
import lib.time.DateTimeContract;

class TeacherTaskInputTest {
	@Test
	void preservesDatabaseDifficultyValuesWithoutChangingInputText() {
		assertEquals("beginner", TeacherTaskInput.Difficulty.fromDatabaseValue("beginner").databaseValue());
		assertEquals("none", TeacherTaskInput.Difficulty.NONE.databaseValue());
		assertEquals("  Python code  \n", input().initialCode());
		assertThrows(IllegalArgumentException.class,
				() -> TeacherTaskInput.Difficulty.fromDatabaseValue("unknown"));
	}

	@Test
	void copiesChildCollectionsForImmutableRequestData() {
		List<String> features = new ArrayList<>(List.of("Feature"));
		TeacherTaskInput input = new TeacherTaskInput(
				"Title", null, null, "", null, null, null,
				features, List.of(), List.of(), List.of(), 1);

		features.add("Changed outside the input");

		assertEquals(List.of("Feature"), input.features());
		assertThrows(UnsupportedOperationException.class, () -> input.features().add("Mutation"));
	}

	@Test
	void rejectsUnknownLateSubmissionPolicies() {
		assertEquals("deny",
				TeacherTaskInput.LateSubmissionPolicy.fromDatabaseValue("deny").databaseValue());
		assertThrows(IllegalArgumentException.class,
				() -> TeacherTaskInput.LateSubmissionPolicy.fromDatabaseValue("sometimes"));
	}

	@Test
	void preservesSecondsWhenFormattingExistingAssignmentTimesForEditing() {
		LocalDateTime publishAt = LocalDateTime.of(2026, 10, 17, 15, 30, 45);
		LocalDateTime dueAt = LocalDateTime.of(2026, 10, 17, 16, 40, 17);
		ClassAssignmentInput assignment = new ClassAssignmentInput(
				12, 34, publishAt, dueAt, LateSubmissionPolicy.DENY);

		assertEquals("2026-10-18T00:30:45", assignment.getPublishAtDisplay());
		assertEquals(publishAt,
				DateTimeContract.fromUserInput(LocalDateTime.parse(assignment.getPublishAtDisplay())));
		assertEquals("2026-10-18T01:40:17", assignment.getDueAtDisplay());
		assertEquals(dueAt,
				DateTimeContract.fromUserInput(LocalDateTime.parse(assignment.getDueAtDisplay())));
	}

	private static TeacherTaskInput input() {
		return new TeacherTaskInput(
				"Title", null, TeacherTaskInput.Difficulty.BEGINNER, "",
				null, null, "  Python code  \n",
				List.of(), List.of(), List.of(), List.of(), 1);
	}
}
