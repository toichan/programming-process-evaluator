package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import entity.ConsentStatus;
import entity.SurveyAnswerValidator;
import entity.StudentEvaluationPage.DimensionResult;
import entity.StudentSurveyPage;
import entity.StudentSurveyQuestion;
import entity.StudentSurveyQuestion.Option;
import entity.StudentSurveyQuestion.SavedAnswer;
import lib.mysql.Client;

public final class StudentSurveyDao {
	private static final String FIND_PAGE_TARGET = """
			SELECT ca.task_class_assignment_id, t.task_id, t.title AS task_title, t.difficulty,
			       sv.survey_id, sv.title AS survey_title, e.evaluation_id,
			       s.submission_id,
			       e.feedback_summary, e.completed_at,
			       sr.survey_response_id, sr.response_status
			FROM users u
			JOIN student_profiles sp ON sp.user_id = u.user_id
			JOIN task_participations tp ON tp.student_user_id = u.user_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			JOIN student_class_memberships scm ON scm.student_user_id = u.user_id
			  AND scm.classroom_id = ca.classroom_id AND scm.membership_status = 'active'
			JOIN classrooms c ON c.classroom_id = ca.classroom_id AND c.classroom_status = 'active'
			JOIN tasks t ON t.task_id = ca.task_id
			  AND t.publication_status = 'published' AND t.deleted_at IS NULL
			JOIN submissions s ON s.participation_id = tp.participation_id
			JOIN evaluations e ON e.submission_id = s.submission_id
			  AND e.evaluation_id = ? AND e.evaluation_status = 'completed'
			  AND e.evaluation_kind IN ('initial','reevaluation')
			JOIN surveys sv ON sv.task_id = t.task_id
			LEFT JOIN survey_responses sr ON sr.student_user_id = u.user_id
			  AND sr.survey_id = sv.survey_id AND sr.evaluation_id = e.evaluation_id
			WHERE u.user_id = ? AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			  AND ca.task_class_assignment_id = ? AND ca.assignment_status = 'published'
			  AND (? IS NULL OR sv.survey_id = ?)
			  AND (ca.publish_at IS NULL OR ca.publish_at <= CURRENT_TIMESTAMP)
			  AND (sv.survey_status = 'active' OR sr.survey_response_id IS NOT NULL)
			  AND EXISTS (
			    SELECT 1 FROM consent_records cr
			    WHERE cr.consent_id = (
			      SELECT latest.consent_id FROM consent_records latest
			      WHERE latest.user_id = u.user_id
			      ORDER BY latest.consent_id DESC LIMIT 1
			    )
			      AND cr.consent_status = 'agreed'
			  )
			ORDER BY (sv.survey_status = 'active') DESC, sv.survey_id DESC
			LIMIT 1
			""";

	private static final String FIND_ACTIVE_TARGET_FOR_UPDATE = """
			SELECT ca.task_id, sv.survey_id, e.evaluation_id, s.submission_id
			FROM users u
			JOIN student_profiles sp ON sp.user_id = u.user_id
			JOIN task_participations tp ON tp.student_user_id = u.user_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			JOIN student_class_memberships scm ON scm.student_user_id = u.user_id
			  AND scm.classroom_id = ca.classroom_id AND scm.membership_status = 'active'
			JOIN classrooms c ON c.classroom_id = ca.classroom_id AND c.classroom_status = 'active'
			JOIN tasks t ON t.task_id = ca.task_id
			  AND t.publication_status = 'published' AND t.deleted_at IS NULL
			JOIN submissions s ON s.participation_id = tp.participation_id
			JOIN evaluations e ON e.submission_id = s.submission_id
			  AND e.evaluation_id = ? AND e.evaluation_status = 'completed'
			  AND e.evaluation_kind IN ('initial','reevaluation')
			JOIN surveys sv ON sv.task_id = t.task_id AND sv.survey_status = 'active'
			WHERE u.user_id = ? AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			  AND ca.task_class_assignment_id = ? AND ca.assignment_status = 'published'
			  AND sv.survey_id = ?
			  AND (ca.publish_at IS NULL OR ca.publish_at <= CURRENT_TIMESTAMP)
			  AND EXISTS (
			    SELECT 1 FROM consent_records cr
			    WHERE cr.consent_id = (
			      SELECT latest.consent_id FROM consent_records latest
			      WHERE latest.user_id = u.user_id
			      ORDER BY latest.consent_id DESC LIMIT 1
			    )
			      AND cr.consent_status = 'agreed'
			  )
			LIMIT 1
			FOR UPDATE
			""";

	public Optional<StudentSurveyPage> findSurveyPage(
			long studentUserId,
			long assignmentId,
			long evaluationId,
			Long requestedSurveyId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			if (findConsentStatus(connection, studentUserId) != ConsentStatus.AGREED) {
				throw new SecurityException("Research consent is required to access surveys.");
			}
			SurveyTarget target = findPageTarget(
					connection, studentUserId, assignmentId, evaluationId, requestedSurveyId);
			if (target == null) {
				return Optional.empty();
			}
			List<StudentSurveyQuestion> questions = findQuestions(connection, target.surveyId);
			Map<Long, SavedAnswer> answers = target.responseId == null
					? Map.of()
					: findAnswers(connection, target.responseId);
			List<StudentSurveyQuestion> populated = questions.stream()
					.map(question -> question.withAnswer(answers.get(question.getQuestionId())))
					.toList();
			return Optional.of(new StudentSurveyPage(
					assignmentId,
					target.surveyId,
					evaluationId,
					target.submissionId,
					target.taskTitle,
					target.difficulty,
					target.surveyTitle,
					target.responseStatus == null ? "not_answered" : target.responseStatus,
					target.responseId != null,
					target.evaluationFeedback,
					findEvaluationDimensions(connection, evaluationId),
					populated,
					findHistory(connection, studentUserId, assignmentId, target.surveyId, evaluationId)));
		}
	}

	public SaveResult saveResponse(
			long studentUserId,
			long assignmentId,
			long surveyId,
			long evaluationId,
			Map<String, String[]> parameters,
			boolean submit) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				SurveyTarget target = findActiveTargetForUpdate(
						connection, studentUserId, assignmentId, surveyId, evaluationId);
				if (target == null) {
					throw new SecurityException(
							"An active, consented survey for this completed evaluation was not found.");
				}

				Response response = findResponseForUpdate(
						connection, studentUserId, surveyId, evaluationId);
				if (response != null && "submitted".equals(response.status)) {
					connection.commit();
					return SaveResult.ALREADY_SUBMITTED;
				}

				List<StudentSurveyQuestion> questions = findQuestions(connection, surveyId);
				if (questions.isEmpty()) {
					throw new IllegalStateException("The active survey has no active questions.");
				}
				Map<Long, SubmittedAnswer> submittedAnswers = new LinkedHashMap<>();
				for (StudentSurveyQuestion question : questions) {
					String[] rawValues = parameters.get("answer_" + question.getQuestionId());
					List<String> values = rawValues == null ? List.of() : List.of(rawValues);
					String reason = firstValue(parameters.get("reason_" + question.getQuestionId()));
					Optional<String> invalid = SurveyAnswerValidator.validate(question, values, reason, submit);
					if (invalid.isPresent()) {
						throw new IllegalArgumentException(invalid.get());
					}
					submittedAnswers.put(question.getQuestionId(), new SubmittedAnswer(values, reason));
				}

				if (response == null) {
					response = insertResponse(connection, studentUserId, target, surveyId, evaluationId);
				}
				saveAnswers(connection, response.responseId, questions, submittedAnswers);
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE survey_responses
						SET response_status = ?, consent_status_at_submission = 'agreed',
						    submitted_at = CASE WHEN ? = 'submitted' THEN CURRENT_TIMESTAMP ELSE NULL END
						WHERE survey_response_id = ?
						  AND response_status = 'in_progress'
						""")) {
					String status = submit ? "submitted" : "in_progress";
					statement.setString(1, status);
					statement.setString(2, status);
					statement.setLong(3, response.responseId);
					if (statement.executeUpdate() != 1) {
						throw new SQLException("Survey response state changed during save.");
					}
				}
				connection.commit();
				return submit ? SaveResult.SUBMITTED : SaveResult.SAVED;
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	private static ConsentStatus findConsentStatus(Connection connection, long studentUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT cr.consent_status
				FROM users u
				JOIN student_profiles sp ON sp.user_id = u.user_id
				LEFT JOIN consent_records cr ON cr.consent_id = (
				  SELECT latest.consent_id FROM consent_records latest
				  WHERE latest.user_id = u.user_id
				  ORDER BY latest.consent_id DESC LIMIT 1
				)
				WHERE u.user_id = ? AND u.user_type = 'student'
				  AND u.account_status = 'active' AND u.deleted_at IS NULL
				""")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SecurityException("An active student account is required.");
				}
				String status = resultSet.getString("consent_status");
				return status == null ? ConsentStatus.UNCONFIRMED : ConsentStatus.fromDatabase(status);
			}
		}
	}

	private static SurveyTarget findPageTarget(
			Connection connection,
			long studentUserId,
			long assignmentId,
			long evaluationId,
			Long requestedSurveyId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_PAGE_TARGET)) {
			statement.setLong(1, evaluationId);
			statement.setLong(2, studentUserId);
			statement.setLong(3, assignmentId);
			if (requestedSurveyId == null) {
				statement.setNull(4, Types.BIGINT);
				statement.setNull(5, Types.BIGINT);
			} else {
				statement.setLong(4, requestedSurveyId);
				statement.setLong(5, requestedSurveyId);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				long responseId = resultSet.getLong("survey_response_id");
				boolean hasResponse = !resultSet.wasNull();
				return new SurveyTarget(
						resultSet.getLong("task_id"),
						resultSet.getLong("survey_id"),
						resultSet.getString("task_title"),
						resultSet.getString("difficulty"),
						resultSet.getString("survey_title"),
						resultSet.getString("evaluation_feedback"),
						hasResponse ? responseId : null,
						resultSet.getString("response_status"),
						resultSet.getLong("submission_id"));
			}
		}
	}

	private static SurveyTarget findActiveTargetForUpdate(
			Connection connection,
			long studentUserId,
			long assignmentId,
			long surveyId,
			long evaluationId) throws SQLException {
		if (lockConsentStatus(connection, studentUserId) != ConsentStatus.AGREED) {
			throw new SecurityException("Research consent is required to save survey answers.");
		}
		try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_TARGET_FOR_UPDATE)) {
			statement.setLong(1, evaluationId);
			statement.setLong(2, studentUserId);
			statement.setLong(3, assignmentId);
			statement.setLong(4, surveyId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return null;
				}
				return new SurveyTarget(
						resultSet.getLong("task_id"),
						resultSet.getLong("survey_id"),
						null,
						null,
						null,
						null,
						null,
						null,
						resultSet.getLong("submission_id"));
			}
		}
	}

	private static List<entity.StudentSurveyHistoryEntry> findHistory(
			Connection connection,
			long studentUserId,
			long assignmentId,
			long surveyId,
			long currentEvaluationId) throws SQLException {
		List<entity.StudentSurveyHistoryEntry> history = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT sr.survey_id, sr.evaluation_id, s.revision_number,
				       sr.response_status, sr.submitted_at
				FROM survey_responses sr
				JOIN evaluations e ON e.evaluation_id = sr.evaluation_id
				  AND e.evaluation_status = 'completed'
				JOIN submissions s ON s.submission_id = e.submission_id
				JOIN task_participations tp ON tp.participation_id = s.participation_id
				  AND tp.student_user_id = sr.student_user_id
				WHERE sr.student_user_id = ?
				  AND tp.task_class_assignment_id = ?
				  AND NOT (sr.survey_id = ? AND sr.evaluation_id = ?)
				  AND sr.response_status IN ('in_progress','submitted')
				ORDER BY e.completed_at DESC, e.evaluation_id DESC
				""")) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, assignmentId);
			statement.setLong(3, surveyId);
			statement.setLong(4, currentEvaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					java.sql.Timestamp submittedAt = resultSet.getTimestamp("submitted_at");
					history.add(new entity.StudentSurveyHistoryEntry(
							resultSet.getLong("survey_id"),
							resultSet.getLong("evaluation_id"),
							resultSet.getInt("revision_number"),
							resultSet.getString("response_status"),
							submittedAt == null ? null : submittedAt.toLocalDateTime()));
				}
			}
		}
		return List.copyOf(history);
	}

	private static List<StudentSurveyQuestion> findQuestions(Connection connection, long surveyId)
			throws SQLException {
		List<StudentSurveyQuestion> questions = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT question_id, question_code, question_type, prompt_text,
				       reason_prompt_text, required, reason_required, sort_order
				FROM survey_questions
				WHERE survey_id = ? AND question_status = 'active'
				ORDER BY sort_order, question_id
				""")) {
			statement.setLong(1, surveyId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					long questionId = resultSet.getLong("question_id");
					questions.add(new StudentSurveyQuestion(
							questionId,
							resultSet.getString("question_code"),
							resultSet.getString("question_type"),
							resultSet.getString("prompt_text"),
							resultSet.getString("reason_prompt_text"),
							resultSet.getBoolean("required"),
							resultSet.getBoolean("reason_required"),
							resultSet.getInt("sort_order"),
							findOptions(connection, questionId),
							List.of(),
							""));
				}
			}
		}
		return List.copyOf(questions);
	}

	private static List<Option> findOptions(Connection connection, long questionId) throws SQLException {
		List<Option> options = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT value, label
				FROM survey_question_options
				WHERE question_id = ? AND option_status = 'active'
				ORDER BY sort_order, option_id
				""")) {
			statement.setLong(1, questionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					options.add(new Option(resultSet.getString("value"), resultSet.getString("label")));
				}
			}
		}
		return List.copyOf(options);
	}

	private static Map<Long, SavedAnswer> findAnswers(Connection connection, long responseId) throws SQLException {
		Map<Long, SavedAnswer> answers = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT a.question_id, q.question_type, a.answer_value, a.answer_reason
				FROM survey_answers a
				JOIN survey_questions q ON q.question_id = a.question_id
				WHERE a.survey_response_id = ?
				""")) {
			statement.setLong(1, responseId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					String stored = resultSet.getString("answer_value");
					List<String> values = stored == null
							? List.of()
							: parseStoredValues(stored, resultSet.getString("question_type"));
					answers.put(resultSet.getLong("question_id"),
							new SavedAnswer(values, resultSet.getString("answer_reason")));
				}
			}
		}
		return answers;
	}

	private static List<String> parseStoredValues(String stored, String questionType) throws SQLException {
		if (!"multiple_choice".equals(questionType)) {
			return List.of(stored);
		}
		try {
			var parsed = JsonParser.parseString(stored);
			if (!parsed.isJsonArray()) {
				throw new SQLException("Stored multiple-choice survey answer is not an array.");
			}
			return parsed.getAsJsonArray().asList().stream()
					.map(element -> element.getAsString())
					.toList();
		} catch (JsonParseException e) {
			throw new SQLException("Stored multiple-choice survey answer is malformed.", e);
		}
	}

	private static Response findResponseForUpdate(
			Connection connection,
			long studentUserId,
			long surveyId,
			long evaluationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT survey_response_id, response_status
				FROM survey_responses
				WHERE student_user_id = ? AND survey_id = ? AND evaluation_id = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, studentUserId);
			statement.setLong(2, surveyId);
			statement.setLong(3, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next()
						? new Response(resultSet.getLong("survey_response_id"), resultSet.getString("response_status"))
						: null;
			}
		}
	}

	private static Response insertResponse(
			Connection connection,
			long studentUserId,
			SurveyTarget target,
			long surveyId,
			long evaluationId) throws SQLException {
		String code = UUID.randomUUID().toString();
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO survey_responses
					(response_code, record_code, student_user_id, survey_id, task_id, evaluation_id,
					 consent_status_at_submission, response_status, started_at, submitted_at)
				VALUES (?, ?, ?, ?, ?, ?, 'agreed', 'in_progress', CURRENT_TIMESTAMP, NULL)
				""", PreparedStatement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, "SRV-" + code);
			statement.setString(2, "REC-" + code);
			statement.setLong(3, studentUserId);
			statement.setLong(4, surveyId);
			statement.setLong(5, target.taskId);
			statement.setLong(6, evaluationId);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Survey response could not be created.");
			}
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Survey response identifier was not returned.");
				}
				return new Response(keys.getLong(1), "in_progress");
			}
		}
	}

	private static void saveAnswers(
			Connection connection,
			long responseId,
			List<StudentSurveyQuestion> questions,
			Map<Long, SubmittedAnswer> answers) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO survey_answers
					(survey_response_id, question_id, answer_value, answer_reason)
				VALUES (?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE
					answer_value = VALUES(answer_value),
					answer_reason = VALUES(answer_reason)
				""")) {
			for (StudentSurveyQuestion question : questions) {
				SubmittedAnswer answer = answers.get(question.getQuestionId());
				List<String> values = answer.values;
				String value = values.isEmpty()
						? null
						: question.isMultipleChoice() ? toJsonArray(values) : values.get(0);
				statement.setLong(1, responseId);
				statement.setLong(2, question.getQuestionId());
				if (value == null) {
					statement.setNull(3, Types.LONGVARCHAR);
				} else {
					statement.setString(3, value);
				}
				if (answer.reason.isBlank()) {
					statement.setNull(4, Types.LONGVARCHAR);
				} else {
					statement.setString(4, answer.reason);
				}
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private static String toJsonArray(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array.toString();
	}

	private static List<DimensionResult> findEvaluationDimensions(Connection connection, long evaluationId)
			throws SQLException {
		List<DimensionResult> dimensions = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT d.label, r.score_value, r.score_text, r.summary_title, r.summary_description
				FROM evaluation_dimension_results r
				JOIN rubric_dimensions d ON d.dimension_id = r.dimension_id
				WHERE r.evaluation_id = ?
				ORDER BY d.sort_order, r.evaluation_dimension_result_id
				""")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					dimensions.add(new DimensionResult(
							resultSet.getString("label"),
							resultSet.getDouble("score_value"),
							resultSet.getString("score_text"),
							resultSet.getString("summary_title"),
							resultSet.getString("summary_description")));
				}
			}
		}
		return List.copyOf(dimensions);
	}

	private static ConsentStatus lockConsentStatus(Connection connection, long studentUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT consent_status
				FROM consent_records
				WHERE user_id = ?
				ORDER BY consent_id DESC
				LIMIT 1
				FOR UPDATE
				""")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return ConsentStatus.UNCONFIRMED;
				}
				return ConsentStatus.fromDatabase(resultSet.getString("consent_status"));
			}
		}
	}

	private static String firstValue(String[] values) {
		return values == null || values.length == 0 || values[0] == null ? "" : values[0];
	}

	private static void rollback(Connection connection, Exception failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackError) {
			failure.addSuppressed(rollbackError);
		}
	}

	public enum SaveResult {
		SAVED,
		SUBMITTED,
		ALREADY_SUBMITTED
	}

	private record SurveyTarget(
			long taskId,
			long surveyId,
			String taskTitle,
			String difficulty,
			String surveyTitle,
			String evaluationFeedback,
			Long responseId,
			String responseStatus,
			long submissionId) {
	}

	private record Response(long responseId, String status) {
	}

	private record SubmittedAnswer(List<String> values, String reason) {
		private SubmittedAnswer {
			values = List.copyOf(values);
			reason = reason == null ? "" : reason;
		}
	}

}
