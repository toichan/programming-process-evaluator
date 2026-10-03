package entity;

import java.util.Objects;

public record EditorPreferences(int fontSizePx, boolean lineWrapping, int indentWidth, Theme theme) {
	public EditorPreferences {
		if (fontSizePx < 10 || fontSizePx > 24) {
			throw new IllegalArgumentException("文字サイズは10〜24pxの範囲で指定してください。");
		}
		if (indentWidth != 2 && indentWidth != 4) {
			throw new IllegalArgumentException("インデント幅は2または4スペースを指定してください。");
		}
		Objects.requireNonNull(theme, "theme");
	}

	public static EditorPreferences defaults() {
		return new EditorPreferences(16, false, 4, Theme.DARK);
	}

	public int getFontSizePx() {
		return fontSizePx;
	}

	public boolean isLineWrapping() {
		return lineWrapping;
	}

	public int getIndentWidth() {
		return indentWidth;
	}

	public String getThemeValue() {
		return theme.value();
	}

	public enum Theme {
		DARK("dark"),
		LIGHT("light"),
		HIGH_CONTRAST("high_contrast");

		private final String value;

		Theme(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}

		public static Theme fromValue(String value) {
			for (Theme theme : values()) {
				if (theme.value.equals(value)) {
					return theme;
				}
			}
			throw new IllegalArgumentException("配色を選択してください。");
		}
	}
}
