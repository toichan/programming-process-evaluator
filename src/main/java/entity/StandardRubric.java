package entity;

import java.util.List;

public record StandardRubric(String title, String version, List<Dimension> dimensions) {
	public static final String TITLE = "生徒共通標準ルーブリック";
	public static final String VERSION = "0805-2026-v1";

	public StandardRubric {
		dimensions = List.copyOf(dimensions);
		if (!TITLE.equals(title) || !VERSION.equals(version) || dimensions.size() != 2
				|| !validDimension(dimensions.get(0), "thinking", 4)
				|| !validDimension(dimensions.get(1), "attitude", 2)) {
			throw new IllegalArgumentException("標準ルーブリックの構成が正しくありません。");
		}
	}

	public String getTitle() { return title; }
	public String getVersion() { return version; }
	public List<Dimension> getDimensions() { return dimensions; }

	private static boolean validDimension(Dimension dimension, String code, int count) {
		if (!code.equals(dimension.code()) || dimension.label().isBlank()
				|| dimension.criteria().size() != count) return false;
		for (Criterion criterion : dimension.criteria()) {
			if (criterion.name().isBlank() || criterion.levels().size() != 5) return false;
			for (int i = 0; i < 5; i++) {
				Level level = criterion.levels().get(i);
				if (level.value() != 5 - i || !("レベル" + level.value()).equals(level.label())
						|| level.description().isBlank()) return false;
			}
		}
		return true;
	}

	public record Dimension(String code, String label, List<Criterion> criteria) {
		public Dimension { criteria = List.copyOf(criteria); }
	}
	public record Criterion(String name, List<Level> levels) {
		public Criterion { levels = List.copyOf(levels); }
	}
	public record Level(int value, String label, String description) {}
}
