package entity;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record TeacherReviewFilter(Long schoolId, Long classroomId, Long taskId, String difficulty,
		String consent, Integer level, String search, String sort, String direction) {
	public TeacherReviewFilter {
		for (Long id : new Long[] { schoolId, classroomId, taskId }) {
			if (id != null && id < 1) throw new IllegalArgumentException("絞り込みIDが不正です。");
		}
		difficulty = difficulty == null ? "" : difficulty;
		consent = consent == null ? "" : consent;
		search = search == null ? "" : search.strip();
		sort = sort == null || sort.isEmpty() ? "submitted" : sort;
		direction = direction == null || direction.isEmpty() ? "desc" : direction;
		if (!Set.of("", "beginner", "intermediate", "advanced", "none").contains(difficulty)
				|| !Set.of("", "agreed", "not_agreed", "unconfirmed").contains(consent)
				|| level != null && (level < 1 || level > 5)
				|| search.length() > 100
				|| !Set.of("student", "task", "submitted", "match", "level", "evaluated").contains(sort)
				|| !Set.of("asc", "desc").contains(direction)) {
			throw new IllegalArgumentException("絞り込み・並び順が不正です。");
		}
	}
	public static TeacherReviewFilter empty() {
		return new TeacherReviewFilter(null, null, null, "", "", null, "", "submitted", "desc");
	}
	public List<TeacherReviewRow> apply(List<TeacherReviewRow> rows) {
		Comparator<TeacherReviewRow> comparator = switch (sort) {
			case "student" -> Comparator.comparing(TeacherReviewRow::studentLoginId);
			case "task" -> Comparator.comparing(TeacherReviewRow::taskName);
			case "match" -> Comparator.comparingDouble(TeacherReviewRow::matchRate);
			case "level" -> Comparator.comparingDouble(row -> row.overallScore() == null ? -1 : row.overallScore());
			case "evaluated" -> Comparator.comparing(TeacherReviewRow::evaluatedAt);
			default -> Comparator.comparing(TeacherReviewRow::submittedAt);
		};
		if ("desc".equals(direction)) comparator = comparator.reversed();
		return rows.stream().filter(row ->
				(schoolId == null || schoolId == row.schoolId())
				&& (classroomId == null || classroomId == row.classroomId())
				&& (taskId == null || taskId == row.taskId())
				&& (difficulty.isEmpty() || difficulty.equals(row.difficulty()))
				&& (consent.isEmpty()
						|| "agreed".equals(consent) && "agreed".equals(row.consent())
						|| "not_agreed".equals(consent) && Set.of("declined", "withdrawn").contains(row.consent())
						|| "unconfirmed".equals(consent) && "unconfirmed".equals(row.consent()))
				&& (level == null || row.overallScore() != null && Math.round(row.overallScore()) == level)
				&& (search.isEmpty() || row.studentLoginId().contains(search) || row.taskName().contains(search)))
				.sorted(comparator.thenComparingLong(TeacherReviewRow::submissionId)
						.thenComparing(row -> row.evaluationId() == null ? 0 : row.evaluationId()))
				.toList();
	}
}
