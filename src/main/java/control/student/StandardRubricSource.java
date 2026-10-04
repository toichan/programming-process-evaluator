package control.student;

import java.util.ArrayList;
import java.util.List;

import entity.StandardRubric;
import entity.StandardRubric.Criterion;
import entity.StandardRubric.Dimension;
import entity.StandardRubric.Level;

public final class StandardRubricSource {
	private StandardRubricSource() {}

	public static StandardRubric parse(String thinking, String attitude) {
		return new StandardRubric(StandardRubric.TITLE, StandardRubric.VERSION, List.of(
				parseDimension(thinking, "thinking", "思考力・判断力・表現力", 4),
				parseDimension(attitude, "attitude", "主体的に学習に取り組む態度", 2)));
	}

	private static Dimension parseDimension(String source, String code, String label, int count) {
		List<String[]> rows = source.lines().map(String::trim).filter(line -> line.startsWith("|"))
				.map(line -> line.split("\\|", -1)).toList();
		if (rows.size() != 7 || rows.stream().anyMatch(row -> row.length != count + 3)) {
			throw new IllegalArgumentException(label + "の資料は、観点列と5段階の表が必要です。");
		}
		List<Criterion> criteria = new ArrayList<>();
		for (int column = 2; column < count + 2; column++) {
			List<Level> levels = new ArrayList<>();
			for (int row = 2; row < 7; row++) {
				int value = 7 - row;
				String levelLabel = rows.get(row)[1].trim().replace("**", "");
				levels.add(new Level(value, levelLabel, rows.get(row)[column].trim()));
			}
			criteria.add(new Criterion(rows.get(0)[column].trim(), levels));
		}
		return new Dimension(code, label, criteria);
	}
}
