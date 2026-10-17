package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReevaluationTimestampDisplayTest {
	@Test
	void exposesPreviewExpiryAndJobRequestTimesAsTokyoDisplayValuesForJsp() {
		var expiresAtUtc = LocalDateTime.of(2026, 10, 4, 6, 0);
		var requestedAtUtc = LocalDateTime.of(2026, 10, 4, 7, 0);
		var preview = new ReevaluationPreview(1, "preview", 2, 3, "ready",
				1, 1, expiresAtUtc, 1, List.of());
		var job = new ReevaluationJobHistory(1, 3, "v1", "completed", 1, 1, 0,
				BigDecimal.valueOf(100), "teacher", requestedAtUtc, null, null);

		assertEquals("2026-10-04T15:00", preview.getExpiresAtDisplay());
		assertEquals("2026-10-04T16:00", job.getRequestedAtDisplay());
	}
}
