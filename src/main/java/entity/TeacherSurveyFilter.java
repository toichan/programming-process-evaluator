package entity;

import java.util.*;
import java.util.regex.Pattern;

public record TeacherSurveyFilter(Long schoolId, Long classroomId, Long taskId, String difficulty,
		String completion, String consent, String search, String sort, String direction, Map<String, String> conditions) {
	private static final Pattern CONDITION = Pattern.compile("(<=|>=|=|<|>)\\s*(\\d+(?:\\.\\d+)?)");
	public static final Set<String> SORTS = Set.of("studentId", "school", "className", "taskTitle", "difficulty",
			"thinkingValidity", "thinkingScore", "attitudeValidity", "attitudeScore", "resistanceScore",
			"usabilityScore", "submittedAt", "completionStatus", "consentStatus");
	public TeacherSurveyFilter {
		for (Long id : new Long[]{schoolId, classroomId, taskId}) if (id != null && id < 1) throw invalid();
		difficulty = empty(difficulty); completion = empty(completion); consent = empty(consent); search = empty(search).strip();
		sort = empty(sort).isEmpty() ? "submittedAt" : sort;
		direction = empty(direction).isEmpty() ? "desc" : direction;
		conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
		if (!Set.of("", "beginner", "intermediate", "advanced", "none").contains(difficulty)
				|| !Set.of("", "submitted", "in_progress").contains(completion)
				|| !Set.of("", "agreed", "not_agreed", "unconfirmed").contains(consent)
				|| search.length() > 100 || !SORTS.contains(sort) || !Set.of("asc","desc").contains(direction)) throw invalid();
		for (var entry : conditions.entrySet()) {
			if (!TeacherSurveyResponse.METRICS.contains(entry.getKey())) throw invalid();
			validateCondition(entry.getValue());
		}
	}
	private static String empty(String value) { return value == null ? "" : value; }
	private static IllegalArgumentException invalid() { return new IllegalArgumentException("絞り込み・条件式・並び順が不正です。"); }
	public static TeacherSurveyFilter empty() { return new TeacherSurveyFilter(null,null,null,"","","","","","",Map.of()); }
	public static void validateCondition(String expression) {
		if (expression.isBlank()) return;
		if (expression.length() > 100) throw invalid();
		for (String part : expression.split(",", -1)) {
			var match = CONDITION.matcher(part.strip());
			if (!match.matches()) throw invalid();
			double value = Double.parseDouble(match.group(2));
			if (!Double.isFinite(value) || value < 1 || value > 5) throw invalid();
		}
	}
	public static boolean matches(String expression, Double value) {
		validateCondition(expression);
		if (expression.isBlank()) return true;
		if (value == null) return false;
		for (String part : expression.split(",")) {
			var match = CONDITION.matcher(part.strip()); match.matches();
			double target = Double.parseDouble(match.group(2));
			boolean valid = switch(match.group(1)) {
				case "=" -> value == target; case "<" -> value < target; case ">" -> value > target;
				case "<=" -> value <= target; default -> value >= target;
			};
			if (valid) return true;
		}
		return false;
	}
	public List<TeacherSurveyResponse> apply(List<TeacherSurveyResponse> rows) {
		Comparator<TeacherSurveyResponse> order = (a,b) -> compare(sortValue(a),sortValue(b));
		if ("desc".equals(direction)) order = order.reversed();
		String query = search.toLowerCase(Locale.ROOT);
		return rows.stream().filter(row -> (schoolId == null || schoolId == row.schoolId())
				&& (classroomId == null || classroomId == row.classroomId()) && (taskId == null || taskId == row.taskId())
				&& (difficulty.isEmpty() || difficulty.equals(row.difficulty()))
				&& (completion.isEmpty() || completion.equals(row.completionStatus()))
				&& (consent.isEmpty() || consent.equals(row.consentStatus()))
				&& (row.studentId().toLowerCase(Locale.ROOT).contains(query) || row.taskTitle().toLowerCase(Locale.ROOT).contains(query))
				&& conditions.entrySet().stream().allMatch(entry -> matches(entry.getValue(),row.score(entry.getKey()))))
				.sorted(order.thenComparingLong(TeacherSurveyResponse::responseId)).toList();
	}
	private Object sortValue(TeacherSurveyResponse row) {
		return switch(sort) {
			case "studentId" -> row.studentId(); case "school" -> row.school(); case "className" -> row.className();
			case "taskTitle" -> row.taskTitle(); case "difficulty" -> switch(row.difficulty()) {case "beginner" -> 1; case "intermediate" -> 2; case "advanced" -> 3; default -> 4;};
			case "completionStatus" -> "submitted".equals(row.completionStatus()) ? 2 : 1;
			case "consentStatus" -> row.consentStatus();
			case "thinkingValidity" -> row.score("q1ThinkingValidity"); case "thinkingScore" -> row.score("q1ThinkingScore");
			case "attitudeValidity" -> row.score("q2AttitudeValidity"); case "attitudeScore" -> row.score("q2AttitudeScore");
			case "resistanceScore" -> row.score("q3ProcessResistanceScore"); case "usabilityScore" -> row.score("q4UsabilityScore");
			default -> row.submittedAt();
		};
	}
	private static int compare(Object a, Object b) {
		if (a == null) return b == null ? 0 : -1; if (b == null) return 1;
		if (a instanceof Number x && b instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue());
		return a.toString().compareTo(b.toString());
	}
}
