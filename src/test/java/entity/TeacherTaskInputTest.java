package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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

	private static TeacherTaskInput input() {
		return new TeacherTaskInput(
				"Title", null, TeacherTaskInput.Difficulty.BEGINNER, "",
				null, null, "  Python code  \n",
				List.of(), List.of(), List.of(), List.of(), 1);
	}
}
