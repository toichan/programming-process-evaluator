package entity;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record TeacherExerciseFilter(Long schoolId, Long classroomId, String consent,
		String search, String sort, String direction) {
	public TeacherExerciseFilter {
		if (schoolId != null && schoolId < 1 || classroomId != null && classroomId < 1)
			throw new IllegalArgumentException("絞り込みIDが不正です。");
		consent = consent == null ? "" : consent;
		search = search == null ? "" : search.strip();
		sort = sort == null || sort.isEmpty() ? "updatedAt" : sort;
		direction = direction == null || direction.isEmpty() ? "desc" : direction;
		if (!Set.of("", "agreed", "not_agreed", "unconfirmed").contains(consent)
				|| search.length() > 100
				|| !Set.of("studentId", "school", "className", "fileCount", "updatedAt", "consent").contains(sort)
				|| !Set.of("asc", "desc").contains(direction))
			throw new IllegalArgumentException("検索・並び順が不正です。");
	}
	public static TeacherExerciseFilter empty() {
		return new TeacherExerciseFilter(null, null, "", "", "", "");
	}
	public List<TeacherExerciseRow> apply(List<TeacherExerciseRow> rows) {
		Comparator<TeacherExerciseRow> comparator = switch (sort) {
			case "studentId" -> Comparator.comparing(TeacherExerciseRow::studentLoginId);
			case "school" -> Comparator.comparing(TeacherExerciseRow::schoolName);
			case "className" -> Comparator.comparing(TeacherExerciseRow::className);
			case "fileCount" -> Comparator.comparingInt(TeacherExerciseRow::fileCount);
			case "consent" -> Comparator.comparingInt(row -> switch (row.consent()) {
				case "declined", "withdrawn" -> 1;
				case "agreed" -> 3;
				default -> 2;
			});
			default -> Comparator.comparing(TeacherExerciseRow::updatedAt);
		};
		if ("desc".equals(direction)) comparator = comparator.reversed();
		return rows.stream().filter(row -> (schoolId == null || schoolId == row.schoolId())
				&& (classroomId == null || classroomId == row.classroomId())
				&& (consent.isEmpty() || consent.equals(row.consent())
					|| "not_agreed".equals(consent) && Set.of("declined", "withdrawn").contains(row.consent()))
				&& row.studentLoginId().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
				.sorted(comparator.thenComparing(TeacherExerciseRow::studentLoginId)
						.thenComparingLong(TeacherExerciseRow::classroomId)).toList();
	}
}
