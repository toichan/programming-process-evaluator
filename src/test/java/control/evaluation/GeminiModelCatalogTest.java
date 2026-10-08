package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeminiModelCatalogTest {
	@Test
	void usesApprovedModelForDefaultAndSelection() {
		assertEquals("gemini-3.1-pro-preview", GeminiModelCatalog.DEFAULT_MODEL);
		assertEquals("Gemini 3.1 Pro（Preview）",
				GeminiModelCatalog.selectableModels().get(GeminiModelCatalog.DEFAULT_MODEL));
		assertEquals(3, GeminiModelCatalog.selectableModels().size());
		assertTrue(GeminiModelCatalog.isSelectable(GeminiModelCatalog.DEFAULT_MODEL));
		assertTrue(GeminiModelCatalog.isSelectable("gemini-3.7-flash"));
		assertFalse(GeminiModelCatalog.isSelectable("gemini-3.5-flash-lite"));
		assertTrue(GeminiModelCatalog.isSelectable("gemini-3.1-pro-preview"));
		assertTrue(GeminiModelCatalog.selectableModels().get("gemini-3.1-pro-preview").contains("Preview"));
	}

	@Test
	void rejectsMissingLegacyAndUnapprovedModelsWithExplicitGuidance() {
		for (String model : new String[] { null, "", "gemini-2.5-pro", "gemini-2.5-flash", "unknown" }) {
			assertFalse(GeminiModelCatalog.isSelectable(model));
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> GeminiModelCatalog.requireSelectable(model));
			assertTrue(error.getMessage().contains("下書き"));
		}
		assertThrows(UnsupportedOperationException.class,
				() -> GeminiModelCatalog.selectableModels().put("unknown", "Unknown"));
	}
}
