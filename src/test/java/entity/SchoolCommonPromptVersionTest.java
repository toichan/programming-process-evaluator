package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SchoolCommonPromptVersionTest {
	@Test
	void displaysCreationTimeInJapanAcrossUtcDateBoundary() {
		var version = new SchoolCommonPromptVersion(
				1, 2, 3, "Synthetic prompt", "teacher", LocalDateTime.of(2026, 10, 17, 15, 30));

		assertEquals("2026-10-18T00:30", version.getCreatedAtDisplay());
	}
}
