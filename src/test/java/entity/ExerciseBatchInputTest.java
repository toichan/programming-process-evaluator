package entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExerciseBatchInputTest {
	@Test
	void requiresUniquePositiveIdsAndValidVersion() {
		assertThrows(IllegalArgumentException.class,
				() -> new ExerciseBatchInput(List.of(), 0, null, Map.of(), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new ExerciseBatchInput(List.of(1L, 1L), 0, null, Map.of(), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new ExerciseBatchInput(List.of(0L), 0, null, Map.of(), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new ExerciseBatchInput(List.of(1L), -1, null, Map.of(), List.of()));
	}

	@Test
	void copiesInputCollectionsAndRestrictsAliasesToSelectedRoots() {
		var ids = new java.util.ArrayList<>(List.of(2L));
		var input = new ExerciseBatchInput(ids, 4, null, Map.of(2L, "copy"), List.of());
		ids.add(3L);
		assertEquals(List.of(2L), input.entryIds());
		assertThrows(IllegalArgumentException.class,
				() -> new ExerciseBatchInput(List.of(2L), 4, null, Map.of(3L, "copy"), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new ExerciseBatchInput(List.of(2L), 4, null, Map.of(2L, "../copy"), List.of()));
	}
}
