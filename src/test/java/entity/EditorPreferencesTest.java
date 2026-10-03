package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EditorPreferencesTest {
	@Test
	void defaultsMatchTheInitialEditorConfiguration() {
		EditorPreferences preferences = EditorPreferences.defaults();

		assertEquals(16, preferences.fontSizePx());
		assertFalse(preferences.lineWrapping());
		assertEquals(4, preferences.indentWidth());
		assertEquals(EditorPreferences.Theme.DARK, preferences.theme());
	}

	@Test
	void acceptsSupportedPreferenceValues() {
		EditorPreferences minimum = new EditorPreferences(
				10, true, 2, EditorPreferences.Theme.HIGH_CONTRAST);
		EditorPreferences maximum = new EditorPreferences(
				24, false, 4, EditorPreferences.Theme.LIGHT);

		assertEquals(10, minimum.fontSizePx());
		assertEquals(2, minimum.indentWidth());
		assertEquals("high_contrast", minimum.getThemeValue());
		assertEquals(24, maximum.fontSizePx());
	}

	@Test
	void rejectsValuesOutsideSupportedRanges() {
		assertThrows(IllegalArgumentException.class,
				() -> new EditorPreferences(9, false, 4, EditorPreferences.Theme.DARK));
		assertThrows(IllegalArgumentException.class,
				() -> new EditorPreferences(25, false, 4, EditorPreferences.Theme.DARK));
		assertThrows(IllegalArgumentException.class,
				() -> new EditorPreferences(16, false, 3, EditorPreferences.Theme.DARK));
	}

	@Test
	void parsesOnlySupportedThemeNames() {
		assertEquals(EditorPreferences.Theme.LIGHT, EditorPreferences.Theme.fromValue("light"));
		assertThrows(IllegalArgumentException.class, () -> EditorPreferences.Theme.fromValue("unknown"));
	}
}
