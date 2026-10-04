package servlet.student;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExerciseBatchFormTest {
	@Test
	void parsesTypedSelectionsAliasesAndRestoreDestinations() {
		var input = ExerciseBatchForm.read(Map.of(
				"expectedVersion", "7",
				"entryIds", "[11,12]",
				"parentEntryId", "",
				"names", "{\"11\":\"別名\"}",
				"restorations", "[{\"entryId\":11,\"name\":\"復元名\"},{\"entryId\":12,\"destinationSpecified\":true,\"parentEntryId\":\"\"}]"));
		assertEquals(7, input.expectedVersion());
		assertEquals(java.util.List.of(11L, 12L), input.entryIds());
		assertNull(input.targetParentId());
		assertEquals("別名", input.names().get(11L));
		assertTrue(input.restorations().get(1).destinationSpecified());
		assertNull(input.restorations().get(1).parentEntryId());
	}

	@Test
	void parsesPerItemBatchPayloadWithoutASeparateSelectionList() {
		var restore = ExerciseBatchForm.read(Map.of(
				"expectedVersion", "2",
				"items", "[{\"entryId\":31,\"name\":\"renamed\"},{\"entryId\":32,\"parentEntryId\":\"\"}]"),
				true);
		assertEquals(java.util.List.of(31L, 32L), restore.entryIds());
		assertEquals("renamed", restore.names().get(31L));
		assertFalse(restore.restorations().get(0).destinationSpecified());
		assertTrue(restore.restorations().get(1).destinationSpecified());
		assertNull(restore.restorations().get(1).parentEntryId());

		var duplicate = ExerciseBatchForm.read(Map.of(
				"expectedVersion", "2",
				"entryId", "31",
				"name", "copy"));
		assertEquals("copy", duplicate.names().get(31L));
		assertTrue(duplicate.restorations().isEmpty());
		assertEquals(java.util.List.of(31L), duplicate.entryIds());
	}

	@Test
	void rejectsMalformedOrUnboundedSelectionAndResolutionPayloads() {
		for (String ids : new String[] {null, "{}", "[0]", "[1.5]", "[\"1\"]", "["}) {
			assertThrows(IllegalArgumentException.class, () -> ExerciseBatchForm.ids(ids));
		}
		assertThrows(IllegalArgumentException.class, () -> ExerciseBatchForm.csvIds("1,1"));
		assertThrows(IllegalArgumentException.class,
				() -> ExerciseBatchForm.uploadResolutions("[{\"path\":\"a.py\",\"action\":\"overwrite\"}]"));
		assertThrows(IllegalArgumentException.class,
				() -> ExerciseBatchForm.read(Map.of("expectedVersion", "0", "entryIds", "[1]",
						"names", "{\"2\":\"not selected\"}")));
	}
}
