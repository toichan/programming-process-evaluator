package dao;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import lib.mysql.Client;

public final class StudentTaskNavigationDao {
	public Map<Long, String> findLatestScores(long studentId) throws SQLException {
		Map<Long, Map<String, String>> scores = new HashMap<>();
		try (var connection = Client.createConnection(); var statement = connection.prepareStatement("""
				SELECT s.submission_id, d.dimension_code, r.score_value, r.score_text, d.scale_maximum
				FROM task_participations tp
				JOIN submissions s ON s.participation_id = tp.participation_id
				  AND s.revision_number = (SELECT MAX(s2.revision_number) FROM submissions s2
				    WHERE s2.participation_id = tp.participation_id)
				JOIN evaluations e ON e.submission_id = s.submission_id AND e.evaluation_status = 'completed'
				  AND e.evaluation_kind IN ('initial','reevaluation')
				  AND e.evaluation_id = (SELECT e2.evaluation_id FROM evaluations e2
				    WHERE e2.submission_id = s.submission_id AND e2.evaluation_kind IN ('initial','reevaluation')
				    ORDER BY e2.created_at DESC, e2.evaluation_id DESC LIMIT 1)
				JOIN evaluation_dimension_results r ON r.evaluation_id = e.evaluation_id
				JOIN rubric_dimensions d ON d.dimension_id = r.dimension_id
				WHERE tp.student_user_id = ? AND d.dimension_code IN ('thinking','attitude')
				""")) {
			statement.setLong(1, studentId);
			try (var results = statement.executeQuery()) {
				while (results.next()) {
					String score = results.getString("score_text");
					if (score == null || score.isBlank()) {
						var value = results.getBigDecimal("score_value");
						score = value == null ? "未" : value.stripTrailingZeros().toPlainString();
					}
					if ("未".equals(score) || score.matches("[0-9]+(?:\\.[0-9]+)?")) {
						score += "/" + results.getBigDecimal("scale_maximum").stripTrailingZeros().toPlainString();
					}
					scores.computeIfAbsent(results.getLong("submission_id"), ignored -> new HashMap<>())
							.put(results.getString("dimension_code"), score);
				}
			}
		}
		Map<Long, String> labels = new HashMap<>();
		scores.forEach((id, values) -> labels.put(id, "思: " + values.getOrDefault("thinking", "未")
				+ " 態: " + values.getOrDefault("attitude", "未")));
		return Map.copyOf(labels);
	}
}
