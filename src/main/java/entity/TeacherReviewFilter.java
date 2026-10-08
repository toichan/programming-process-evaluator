package entity;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public record TeacherReviewFilter(Long schoolId, Long classroomId, Long taskId, String difficulty,
		String consent, Integer level, String search, String sort, String direction, String thinking, String attitude) {
	public TeacherReviewFilter(Long schoolId, Long classroomId, Long taskId, String difficulty,
			String consent, Integer level, String search, String sort, String direction) {
		this(schoolId, classroomId, taskId, difficulty, consent, level, search, sort, direction, "", "");
	}
	public TeacherReviewFilter {
		thinking = thinking == null ? "" : thinking.strip();
		attitude = attitude == null ? "" : attitude.strip();
		TeacherSurveyFilter.validateCondition(thinking);
		TeacherSurveyFilter.validateCondition(attitude);
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
				|| !Set.of("student", "school", "class", "task", "difficulty", "consent", "submitted", "match", "level", "evaluated", "thinking", "attitude").contains(sort)
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
			case "school" -> Comparator.comparing(TeacherReviewRow::schoolName);
			case "class" -> Comparator.comparing(TeacherReviewRow::className);
			case "task" -> Comparator.comparing(TeacherReviewRow::taskName);
			case "difficulty" -> Comparator.comparingInt(row -> switch (row.difficulty()) {
				case "beginner" -> 1;
				case "intermediate" -> 2;
				case "advanced" -> 3;
				default -> 99;
			});
			case "consent" -> Comparator.comparingInt(row -> switch (row.consent()) {
				case "declined", "withdrawn" -> 1;
				case "unconfirmed" -> 2;
				default -> 3;
			});
			case "match" -> Comparator.comparingDouble(row -> Math.max(0, row.matchRate()));
			case "level" -> Comparator.comparingDouble(row -> row.overallScore() == null ? -1 : row.overallScore());
			case "thinking" -> Comparator.comparingDouble(row -> row.thinkingScore() == null ? -1 : row.thinkingScore());
			case "attitude" -> Comparator.comparingDouble(row -> row.attitudeScore() == null ? -1 : row.attitudeScore());
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
				&& TeacherSurveyFilter.matches(thinking, row.thinkingScore())
				&& TeacherSurveyFilter.matches(attitude, row.attitudeScore())
				&& (search.isEmpty() || row.studentLoginId().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))
						|| row.taskName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))))
				.sorted(comparator.thenComparingLong(TeacherReviewRow::submissionId)
						.thenComparing(row -> row.evaluationId() == null ? 0 : row.evaluationId()))
				.toList();
	}
}
