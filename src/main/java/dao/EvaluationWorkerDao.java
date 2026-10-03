package dao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import control.evaluation.EvaluationPrivacyRedactor;
import lib.mysql.Client;

public final class EvaluationWorkerDao implements EvaluationWorkRepository {
	private static final SecureRandom RANDOM = new SecureRandom();

	public Optional<EvaluationJob> claimNext() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement select = connection.prepareStatement("""
					SELECT er.evaluation_request_id, er.evaluation_id, er.model_id,
					       er.prompt_version, er.rubric_version, er.consent_status_at_request,
					       er.request_payload, e.submission_id, tp.student_user_id, tp.participation_id,
					       ca.task_id
					FROM evaluation_requests er
					JOIN evaluations e ON e.evaluation_id = er.evaluation_id
					JOIN submissions s ON s.submission_id = e.submission_id
					JOIN task_participations tp ON tp.participation_id = s.participation_id
					JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
					WHERE er.request_status = 'queued' AND e.evaluation_status = 'not_started'
					ORDER BY er.requested_at, er.evaluation_request_id
					LIMIT 1
					FOR UPDATE SKIP LOCKED
					""")) {
				try (ResultSet resultSet = select.executeQuery()) {
					if (!resultSet.next()) {
						connection.commit();
						return Optional.empty();
					}
					long requestId = resultSet.getLong("evaluation_request_id");
					long evaluationId = resultSet.getLong("evaluation_id");
					long participationId = resultSet.getLong("participation_id");
					long submissionId = resultSet.getLong("submission_id");
					try (PreparedStatement update = connection.prepareStatement("""
							UPDATE evaluation_requests
							SET request_status = 'in_progress', requested_at = CURRENT_TIMESTAMP(6)
							WHERE evaluation_request_id = ? AND request_status = 'queued'
							""")) {
						update.setLong(1, requestId);
						if (update.executeUpdate() != 1) {
							throw new SQLException("The evaluation request could not be claimed.");
						}
					}
					try (PreparedStatement update = connection.prepareStatement("""
							UPDATE evaluations SET evaluation_status = 'in_progress', generated_at = CURRENT_TIMESTAMP(6)
							WHERE evaluation_id = ? AND evaluation_status = 'not_started'
							""")) {
						update.setLong(1, evaluationId);
						if (update.executeUpdate() != 1) {
							throw new SQLException("The evaluation could not be moved to in-progress.");
						}
					}
					try (PreparedStatement update = connection.prepareStatement("""
							UPDATE task_participations SET evaluation_status = 'in_progress'
							WHERE participation_id = ? AND draft_base_submission_id = ?
							""")) {
						update.setLong(1, participationId);
						update.setLong(2, submissionId);
						update.executeUpdate();
					}
					JsonObject requestPayload = JsonParser.parseString(resultSet.getString("request_payload"))
							.getAsJsonObject();
					EvaluationJob job = new EvaluationJob(
							requestId,
							evaluationId,
							submissionId,
							resultSet.getLong("student_user_id"),
							participationId,
							resultSet.getLong("task_id"),
							resultSet.getString("model_id"),
							resultSet.getString("prompt_version"),
							resultSet.getString("rubric_version"),
							resultSet.getString("consent_status_at_request"),
							requestPayload.get("request_id").getAsString());
					connection.commit();
					return Optional.of(job);
				}
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public EvaluationInput loadAndPersistInput(EvaluationJob job) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				InputMetadata metadata = loadMetadata(connection, job);
				String subjectCode = ensureResearchSubjectCode(connection, job.studentUserId());
				JsonObject task = buildTask(connection, job.taskId());
				JsonObject rubric = buildRubric(connection, evaluationRubricId(connection, job.evaluationId()));
				JsonObject prompt = buildPrompt(connection, evaluationPromptVersionId(connection, job.evaluationId()));
				JsonObject submission = buildSubmission(connection, job.submissionId());
				JsonArray logs = buildLogs(connection, job.participationId(), job.submissionId(), metadata);
				JsonArray testResults = buildSubmissionTestResults(connection, job.submissionId());

				JsonObject payload = new JsonObject();
				JsonObject requestMetadata = new JsonObject();
				requestMetadata.addProperty("request_id", job.externalRequestId());
				requestMetadata.addProperty("requested_at", LocalDateTime.now().toString());
				requestMetadata.addProperty("model_id", job.modelId());
				requestMetadata.addProperty("feature_name", "student_task_evaluation");
				requestMetadata.addProperty("task_id", job.taskId());
				requestMetadata.addProperty("prompt_version", job.promptVersion());
				requestMetadata.addProperty("rubric_version", job.rubricVersion());
				requestMetadata.addProperty("actor_role", "student");
				requestMetadata.addProperty("consent_status", job.consentStatus());
				requestMetadata.addProperty("locale", "ja-JP");
				requestMetadata.addProperty("anonymized_subject_id", subjectCode);
				payload.add("metadata", requestMetadata);
				payload.add("task", task);
				payload.add("rubric", rubric);
				payload.add("timeline_logs", logs);
				submission.add("testcase_check_results", testResults);
				payload.add("submission", submission);
				payload.add("prompt_evaluation_settings", prompt);

				JsonObject redactedPayload = EvaluationPrivacyRedactor.redact(
						payload,
						List.of(metadata.displayName(), metadata.loginId(), metadata.studentCode()));
				JsonObject taskSnapshot = new JsonObject();
				taskSnapshot.add("task", redactedPayload.get("task").deepCopy());
				taskSnapshot.add("rubric", redactedPayload.get("rubric").deepCopy());
				taskSnapshot.add("timeline_logs", redactedPayload.get("timeline_logs").deepCopy());
				taskSnapshot.add("prompt_evaluation_settings",
						redactedPayload.get("prompt_evaluation_settings").deepCopy());
				JsonObject submissionSnapshot = redactedPayload.getAsJsonObject("submission").deepCopy();
				Set<Long> knownLogIds = extractLogIds(logs);
				Set<Long> knownExecutionIds = extractExecutionIds(logs);
				int autoSaveCount = countAutoSaves(logs);
				int executionCount = countExecutions(logs);

				try (PreparedStatement insert = connection.prepareStatement("""
						INSERT INTO evaluation_input_snapshots (
						  evaluation_request_id, anonymized_subject_id, task_snapshot, submission_snapshot,
						  log_range_start, log_range_end, created_at
						)
						VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
						""")) {
					insert.setLong(1, job.requestId());
					insert.setString(2, subjectCode);
					insert.setString(3, taskSnapshot.toString());
					insert.setString(4, submissionSnapshot.toString());
					insert.setTimestamp(5, metadata.logRangeStart() == null
							? null : Timestamp.valueOf(metadata.logRangeStart()));
					insert.setTimestamp(6, metadata.submittedAt() == null
							? null : Timestamp.valueOf(metadata.submittedAt()));
					insert.executeUpdate();
				}
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE evaluation_requests SET request_payload = ?
						WHERE evaluation_request_id = ? AND request_status = 'in_progress'
						""")) {
					update.setString(1, redactedPayload.toString());
					update.setLong(2, job.requestId());
					if (update.executeUpdate() != 1) {
						throw new SQLException("The claimed evaluation request is no longer active.");
					}
				}
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE evaluations SET auto_save_count = ?, execution_count = ?
						WHERE evaluation_id = ? AND evaluation_status = 'in_progress'
						""")) {
					update.setInt(1, autoSaveCount);
					update.setInt(2, executionCount);
					update.setLong(3, job.evaluationId());
					if (update.executeUpdate() != 1) {
						throw new SQLException("The claimed evaluation is no longer active.");
					}
				}
				connection.commit();
				return new EvaluationInput(redactedPayload, knownLogIds, knownExecutionIds);
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public void recordResponse(long requestId, JsonObject rawResponse, JsonObject validatedOutput) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO evaluation_responses (
						  evaluation_request_id, raw_response, response_status, confidence, warnings, received_at
						)
						VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
						""")) {
			statement.setLong(1, requestId);
			statement.setString(2, rawResponse.toString());
			statement.setString(3, validatedOutput == null ? "invalid" : "validated");
			if (validatedOutput != null) {
				statement.setBigDecimal(4,
						java.math.BigDecimal.valueOf(validatedOutput.get("confidence").getAsDouble()));
				statement.setString(5, validatedOutput.getAsJsonArray("warnings").toString());
			} else {
				statement.setNull(4, java.sql.Types.DECIMAL);
				statement.setNull(5, java.sql.Types.LONGVARCHAR);
			}
			statement.executeUpdate();
		}
	}

	public void complete(EvaluationJob job, JsonObject result) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				long[] dimensions = findDimensionIds(connection, job.evaluationId());
				int thinkingScore = result.getAsJsonObject("scores").get("thinking_expression_level").getAsInt();
				int attitudeScore = result.getAsJsonObject("scores").get("proactive_attitude_level").getAsInt();
				String thinkingReason = result.getAsJsonObject("reasons")
						.get("thinking_expression_reason").getAsString();
				String attitudeReason = result.getAsJsonObject("reasons")
						.get("proactive_attitude_reason").getAsString();
				insertDimensionResult(connection, job.evaluationId(), dimensions[0], thinkingScore, thinkingReason);
				insertDimensionResult(connection, job.evaluationId(), dimensions[1], attitudeScore, attitudeReason);
				long thinkingReasonId = insertReason(
						connection, job.evaluationId(), dimensions[0], "thinking_expression", "思考力・判断力・表現力",
						thinkingScore, thinkingReason);
				long attitudeReasonId = insertReason(
						connection, job.evaluationId(), dimensions[1], "proactive_attitude",
						"主体的に学習に取り組む態度", attitudeScore, attitudeReason);
				insertReasonDetails(connection, thinkingReasonId, "turning_point",
						result.getAsJsonObject("process_analysis").getAsJsonArray("turning_points"));
				insertReasonDetails(connection, attitudeReasonId, "stagnation_point",
						result.getAsJsonObject("process_analysis").getAsJsonArray("stagnation_points"));
				insertReasonDetails(connection, attitudeReasonId, "teacher_support_suggestion",
						result.getAsJsonObject("process_analysis").getAsJsonArray("teacher_support_suggestions"));
				insertEvidence(connection, job.evaluationId(), result, thinkingReasonId, attitudeReasonId);

				JsonObject processAnalysis = result.getAsJsonObject("process_analysis");
				double overall = (thinkingScore + attitudeScore) / 2.0;
				String feedback = "思考力・判断力・表現力: " + thinkingReason
						+ "\n主体的に学習に取り組む態度: " + attitudeReason;
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE evaluations
						SET evaluation_status = 'completed', overall_score = ?, process_analysis = ?,
						    feedback_summary = ?, final_evaluated_at = CURRENT_TIMESTAMP(6),
						    completed_at = CURRENT_TIMESTAMP(6)
						WHERE evaluation_id = ? AND evaluation_status = 'in_progress'
						""")) {
					update.setBigDecimal(1, java.math.BigDecimal.valueOf(overall));
					update.setString(2, processAnalysis.toString());
					update.setString(3, feedback);
					update.setLong(4, job.evaluationId());
					if (update.executeUpdate() != 1) {
						throw new SQLException("The evaluation could not be completed from its current state.");
					}
				}
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE evaluation_requests
						SET request_status = 'succeeded', completed_at = CURRENT_TIMESTAMP(6), error_detail = NULL
						WHERE evaluation_request_id = ? AND request_status = 'in_progress'
						""")) {
					update.setLong(1, job.requestId());
					if (update.executeUpdate() != 1) {
						throw new SQLException("The evaluation request could not be completed from its current state.");
					}
				}
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE task_participations
						SET evaluation_status = 'completed'
						WHERE participation_id = ?
						  AND draft_base_submission_id = ?
						""")) {
					update.setLong(1, job.participationId());
					update.setLong(2, job.submissionId());
					update.executeUpdate();
				}
				connection.commit();
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public void fail(EvaluationJob job, String safeErrorDetail, int retryCount) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE evaluations SET evaluation_status = 'failed', completed_at = CURRENT_TIMESTAMP(6)
						WHERE evaluation_id = ? AND evaluation_status = 'in_progress'
						""")) {
					update.setLong(1, job.evaluationId());
					update.executeUpdate();
				}
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE evaluation_requests
						SET request_status = 'failed', retry_count = ?,
						    completed_at = CURRENT_TIMESTAMP(6), error_detail = ?
						WHERE evaluation_request_id = ? AND request_status = 'in_progress'
						""")) {
					update.setInt(1, retryCount);
					update.setString(2, safeErrorDetail);
					update.setLong(3, job.requestId());
					update.executeUpdate();
				}
				try (PreparedStatement update = connection.prepareStatement("""
						UPDATE task_participations
						SET evaluation_status = 'failed'
						WHERE participation_id = ? AND draft_base_submission_id = ?
						""")) {
					update.setLong(1, job.participationId());
					update.setLong(2, job.submissionId());
					update.executeUpdate();
				}
				try (PreparedStatement audit = connection.prepareStatement("""
						INSERT INTO audit_logs (
						  actor_role, feature_code, target_type, target_id, action_type, result_status,
						  error_code, error_message, request_id, occurred_at
						)
						VALUES ('system', 'student_evaluation', 'evaluation', ?,
						        'evaluation_failed', 'failure', 'evaluation_failed', ?, ?, CURRENT_TIMESTAMP(6))
						""")) {
					audit.setLong(1, job.evaluationId());
					audit.setString(2, safeErrorDetail);
					audit.setString(3, job.externalRequestId());
					audit.executeUpdate();
				}
				connection.commit();
			} catch (SQLException | RuntimeException e) {
				rollback(connection, e);
				throw e;
			}
		}
	}

	public void setRetryCount(long requestId, int retryCount) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE evaluation_requests SET retry_count = ?
						WHERE evaluation_request_id = ? AND request_status = 'in_progress'
						""")) {
			statement.setInt(1, retryCount);
			statement.setLong(2, requestId);
			statement.executeUpdate();
		}
	}

	private static InputMetadata loadMetadata(Connection connection, EvaluationJob job) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT u.display_name, u.login_id, sp.student_code, s.submitted_at,
				       (SELECT MAX(previous.submitted_at)
				        FROM submissions previous
				        WHERE previous.participation_id = s.participation_id
				          AND previous.revision_number < s.revision_number) AS previous_submitted_at
				FROM submissions s
				JOIN task_participations tp ON tp.participation_id = s.participation_id
				JOIN users u ON u.user_id = tp.student_user_id
				JOIN student_profiles sp ON sp.user_id = u.user_id
				WHERE s.submission_id = ? AND tp.student_user_id = ?
				""")) {
			statement.setLong(1, job.submissionId());
			statement.setLong(2, job.studentUserId());
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The submitted work is no longer available to its owner.");
				}
				LocalDateTime submittedAt = resultSet.getTimestamp("submitted_at").toLocalDateTime();
				Timestamp previous = resultSet.getTimestamp("previous_submitted_at");
				return new InputMetadata(
						resultSet.getString("display_name"),
						resultSet.getString("login_id"),
						resultSet.getString("student_code"),
						submittedAt,
						previous == null ? null : previous.toLocalDateTime());
			}
		}
	}

	private static String ensureResearchSubjectCode(Connection connection, long studentUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT research_subject_code
				FROM research_subject_identifiers
				WHERE student_user_id = ?
				""")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return resultSet.getString("research_subject_code");
				}
			}
		}
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		String subjectCode = "RS-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO research_subject_identifiers (
				  student_user_id, research_subject_code, generated_at, generated_by_user_id
				)
				VALUES (?, ?, CURRENT_TIMESTAMP(6), NULL)
				ON DUPLICATE KEY UPDATE research_subject_code = VALUES(research_subject_code)
				""")) {
			statement.setLong(1, studentUserId);
			statement.setString(2, subjectCode);
			statement.executeUpdate();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT research_subject_code
				FROM research_subject_identifiers
				WHERE student_user_id = ?
				""")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The research subject identifier could not be established.");
				}
				return resultSet.getString("research_subject_code");
			}
		}
	}

	private static JsonObject buildTask(Connection connection, long taskId) throws SQLException {
		JsonObject task = new JsonObject();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT title, description, input_constraints, creation_rules, difficulty, language
				FROM tasks WHERE task_id = ?
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The task snapshot could not be loaded.");
				}
				task.addProperty("task_id", taskId);
				task.addProperty("task_title", resultSet.getString("title"));
				task.addProperty("task_description", resultSet.getString("description"));
				task.addProperty("constraints", resultSet.getString("input_constraints"));
				task.addProperty("creation_rules", resultSet.getString("creation_rules"));
				task.addProperty("difficulty", resultSet.getString("difficulty"));
				task.addProperty("language", resultSet.getString("language"));
			}
		}
		JsonArray testCases = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT title, test_case_input, test_case_output, test_case_order
				FROM task_test_cases
				WHERE task_id = ? AND record_status = 'active'
				ORDER BY test_case_order, test_case_id
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					JsonObject testCase = new JsonObject();
					testCase.addProperty("title", resultSet.getString("title"));
					testCase.addProperty("input", resultSet.getString("test_case_input"));
					testCase.addProperty("expected_output", resultSet.getString("test_case_output"));
					testCase.addProperty("order", resultSet.getInt("test_case_order"));
					testCases.add(testCase);
				}
			}
		}
		task.add("testcases", testCases);
		return task;
	}

	private static JsonObject buildRubric(Connection connection, long rubricId) throws SQLException {
		JsonObject rubric = new JsonObject();
		rubric.addProperty("rubric_id", rubricId);
		JsonArray dimensions = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT dimension_id, dimension_code, label, scale_minimum, scale_maximum, scale_label
				FROM rubric_dimensions
				WHERE rubric_id = ?
				ORDER BY sort_order, dimension_id
				""")) {
			statement.setLong(1, rubricId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					long dimensionId = resultSet.getLong("dimension_id");
					JsonObject dimension = new JsonObject();
					dimension.addProperty("dimension_code", resultSet.getString("dimension_code"));
					dimension.addProperty("label", resultSet.getString("label"));
					dimension.addProperty("scale_minimum", resultSet.getInt("scale_minimum"));
					dimension.addProperty("scale_maximum", resultSet.getInt("scale_maximum"));
					dimension.addProperty("scale_label", resultSet.getString("scale_label"));
					dimension.add("criteria", buildCriteria(connection, rubricId, dimensionId));
					dimensions.add(dimension);
				}
			}
		}
		rubric.add("viewpoint_definitions", dimensions);
		return rubric;
	}

	private static JsonArray buildCriteria(Connection connection, long rubricId, long dimensionId)
			throws SQLException {
		JsonArray criteria = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT criterion_id, criterion_code, name, description, weight
				FROM rubric_criteria
				WHERE rubric_id = ? AND dimension_id = ?
				ORDER BY sort_order, criterion_id
				""")) {
			statement.setLong(1, rubricId);
			statement.setLong(2, dimensionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					long criterionId = resultSet.getLong("criterion_id");
					JsonObject criterion = new JsonObject();
					criterion.addProperty("criterion_code", resultSet.getString("criterion_code"));
					criterion.addProperty("name", resultSet.getString("name"));
					criterion.addProperty("description", resultSet.getString("description"));
					criterion.addProperty("weight", resultSet.getBigDecimal("weight"));
					criterion.add("levels", buildLevels(connection, criterionId));
					criteria.add(criterion);
				}
			}
		}
		return criteria;
	}

	private static JsonArray buildLevels(Connection connection, long criterionId) throws SQLException {
		JsonArray levels = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT level_value, level_label, level_description
				FROM criterion_levels
				WHERE criterion_id = ?
				ORDER BY level_value
				""")) {
			statement.setLong(1, criterionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					JsonObject level = new JsonObject();
					level.addProperty("level", resultSet.getInt("level_value"));
					level.addProperty("label", resultSet.getString("level_label"));
					level.addProperty("description", resultSet.getString("level_description"));
					levels.add(level);
				}
			}
		}
		return levels;
	}

	private static JsonObject buildPrompt(Connection connection, long promptVersionId) throws SQLException {
		JsonObject prompt = new JsonObject();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT common_prompt, additional_evaluation_instruction
				FROM prompt_versions
				WHERE prompt_version_id = ?
				""")) {
			statement.setLong(1, promptVersionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The pinned prompt version could not be loaded.");
				}
				prompt.addProperty("common_prompt", resultSet.getString("common_prompt"));
				prompt.addProperty("additional_evaluation_instruction",
						resultSet.getString("additional_evaluation_instruction"));
			}
		}
		JsonArray ambiguityInstructions = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT item_text, teacher_resolution
				FROM prompt_fluctuation_items
				WHERE prompt_version_id = ? AND resolution_status IN ('resolved','not_applicable')
				ORDER BY sort_order, fluctuation_item_id
				""")) {
			statement.setLong(1, promptVersionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					JsonObject item = new JsonObject();
					item.addProperty("item", resultSet.getString("item_text"));
					item.addProperty("teacher_resolution", resultSet.getString("teacher_resolution"));
					ambiguityInstructions.add(item);
				}
			}
		}
		prompt.add("ambiguity_item_instructions", ambiguityInstructions);
		JsonArray examples = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT example_input, example_output
				FROM evaluation_examples
				WHERE prompt_version_id = ? AND example_status = 'active'
				ORDER BY evaluation_example_id
				""")) {
			statement.setLong(1, promptVersionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					JsonObject example = new JsonObject();
					example.add("input", parseStoredJson(resultSet.getString("example_input")));
					example.add("output", parseStoredJson(resultSet.getString("example_output")));
					examples.add(example);
				}
			}
		}
		prompt.add("evaluation_examples", examples);
		return prompt;
	}

	private static JsonObject buildSubmission(Connection connection, long submissionId) throws SQLException {
		JsonObject submission = new JsonObject();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT submitted_code, submitted_at
				FROM submissions WHERE submission_id = ?
				""")) {
			statement.setLong(1, submissionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The submission snapshot could not be loaded.");
				}
				submission.addProperty("submitted_code", resultSet.getString("submitted_code"));
				submission.addProperty("submitted_at", resultSet.getTimestamp("submitted_at").toLocalDateTime().toString());
			}
		}
		return submission;
	}

	private static JsonArray buildLogs(
			Connection connection,
			long participationId,
			long submissionId,
			InputMetadata metadata) throws SQLException {
		JsonArray logs = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT l.code_log_id, l.event_type, l.observed_at, l.snapshot_text, l.code_execution_id,
				       e.execution_status, e.error_code, e.standard_input, e.standard_output, e.standard_error
				FROM code_logs l
				LEFT JOIN code_executions e ON e.execution_id = l.code_execution_id
				WHERE l.participation_id = ?
				  AND (l.submission_id = ? OR (
				    l.submission_id IS NULL AND l.observed_at <= ?
				    AND (? IS NULL OR l.observed_at > ?)
				  ))
				ORDER BY l.observed_at, l.code_log_id
				LIMIT 100
				""")) {
			statement.setLong(1, participationId);
			statement.setLong(2, submissionId);
			statement.setTimestamp(3, Timestamp.valueOf(metadata.submittedAt()));
			if (metadata.logRangeStart() == null) {
				statement.setNull(4, java.sql.Types.TIMESTAMP);
				statement.setNull(5, java.sql.Types.TIMESTAMP);
			} else {
				statement.setTimestamp(4, Timestamp.valueOf(metadata.logRangeStart()));
				statement.setTimestamp(5, Timestamp.valueOf(metadata.logRangeStart()));
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					JsonObject log = new JsonObject();
					log.addProperty("log_id", resultSet.getLong("code_log_id"));
					log.addProperty("event_type", resultSet.getString("event_type"));
					log.addProperty("observed_at", resultSet.getTimestamp("observed_at").toLocalDateTime().toString());
					log.addProperty("snapshot", resultSet.getString("snapshot_text"));
					long executionId = resultSet.getLong("code_execution_id");
					if (!resultSet.wasNull()) {
						log.addProperty("execution_id", executionId);
						log.addProperty("execution_status", resultSet.getString("execution_status"));
						log.addProperty("error_type", resultSet.getString("error_code"));
						log.addProperty("standard_input", resultSet.getString("standard_input"));
						log.addProperty("standard_output", resultSet.getString("standard_output"));
						log.addProperty("standard_error", resultSet.getString("standard_error"));
					}
					logs.add(log);
				}
			}
		}
		return logs;
	}

	private static JsonArray buildSubmissionTestResults(Connection connection, long submissionId) throws SQLException {
		JsonArray results = new JsonArray();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT tr.case_order, tr.input_snapshot, tr.expected_output_snapshot, tr.actual_output,
				       tr.result_status, tr.error_code
				FROM code_execution_test_results tr
				JOIN code_executions e ON e.execution_id = tr.execution_id
				WHERE e.submission_id = ? AND e.execution_context = 'submission_check'
				ORDER BY tr.case_order, tr.execution_test_result_id
				""")) {
			statement.setLong(1, submissionId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					JsonObject result = new JsonObject();
					result.addProperty("case_order", resultSet.getInt("case_order"));
					result.addProperty("input", resultSet.getString("input_snapshot"));
					result.addProperty("expected_output", resultSet.getString("expected_output_snapshot"));
					result.addProperty("actual_output", resultSet.getString("actual_output"));
					result.addProperty("status", resultSet.getString("result_status"));
					result.addProperty("error_type", resultSet.getString("error_code"));
					results.add(result);
				}
			}
		}
		return results;
	}

	private static long evaluationRubricId(Connection connection, long evaluationId) throws SQLException {
		return findPinnedId(connection, evaluationId, "rubric_id");
	}

	private static long evaluationPromptVersionId(Connection connection, long evaluationId) throws SQLException {
		return findPinnedId(connection, evaluationId, "prompt_version_id");
	}

	private static long findPinnedId(Connection connection, long evaluationId, String column) throws SQLException {
		if (!"rubric_id".equals(column) && !"prompt_version_id".equals(column)) {
			throw new IllegalArgumentException("Unsupported pinned evaluation field.");
		}
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT " + column + " FROM evaluations WHERE evaluation_id = ?")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("The evaluation version references could not be loaded.");
				}
				long value = resultSet.getLong(column);
				if (resultSet.wasNull()) {
					throw new SQLException("The evaluation version reference is missing.");
				}
				return value;
			}
		}
	}

	private static JsonElement parseStoredJson(String json) throws SQLException {
		try {
			return JsonParser.parseString(json);
		} catch (RuntimeException e) {
			throw new SQLException("A stored prompt example is not valid JSON.", e);
		}
	}

	private static Set<Long> extractLogIds(JsonArray logs) {
		Set<Long> ids = new HashSet<>();
		for (JsonElement element : logs) {
			ids.add(parseLogId(element.getAsJsonObject().get("log_id").getAsString()));
		}
		return Set.copyOf(ids);
	}

	private static Set<Long> extractExecutionIds(JsonArray logs) {
		Set<Long> ids = new HashSet<>();
		for (JsonElement element : logs) {
			JsonElement executionId = element.getAsJsonObject().get("execution_id");
			if (executionId != null && executionId.isJsonPrimitive()) {
				ids.add(executionId.getAsLong());
			}
		}
		return Set.copyOf(ids);
	}

	private static int countAutoSaves(JsonArray logs) {
		int count = 0;
		for (JsonElement element : logs) {
			String type = element.getAsJsonObject().get("event_type").getAsString();
			if ("periodic_snapshot".equals(type) || "manual_save".equals(type)) {
				count++;
			}
		}
		return count;
	}

	private static int countExecutions(JsonArray logs) {
		Set<Long> executionIds = new HashSet<>();
		for (JsonElement element : logs) {
			JsonElement id = element.getAsJsonObject().get("execution_id");
			if (id != null && id.isJsonPrimitive()) {
				executionIds.add(id.getAsLong());
			}
		}
		return executionIds.size();
	}

	private static long[] findDimensionIds(Connection connection, long evaluationId) throws SQLException {
		Long thinkingId = null;
		Long attitudeId = null;
		List<Long> orderedIds = new ArrayList<>(2);
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT d.dimension_id, d.dimension_code, d.label
				FROM evaluations e
				JOIN rubric_dimensions d ON d.rubric_id = e.rubric_id
				WHERE e.evaluation_id = ?
				ORDER BY d.sort_order, d.dimension_id
				""")) {
			statement.setLong(1, evaluationId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					long id = resultSet.getLong("dimension_id");
					orderedIds.add(id);
					String identifier = (resultSet.getString("dimension_code") + " "
							+ resultSet.getString("label")).toLowerCase(java.util.Locale.ROOT);
					if (identifier.contains("thinking") || identifier.contains("思考")
							|| identifier.contains("表現") || identifier.contains("判断")) {
						thinkingId = id;
					}
					if (identifier.contains("attitude") || identifier.contains("proactive")
							|| identifier.contains("主体") || identifier.contains("態度")) {
						attitudeId = id;
					}
				}
			}
		}
		if (orderedIds.size() != 2) {
			throw new SQLException("The pinned rubric must define exactly two evaluation dimensions.");
		}
		if (thinkingId == null || attitudeId == null || thinkingId.equals(attitudeId)) {
			throw new SQLException("The pinned rubric dimensions do not match the supported evaluation criteria.");
		}
		return new long[] { thinkingId, attitudeId };
	}

	private static void insertReasonDetails(
			Connection connection,
			long reasonId,
			String detailCode,
			JsonArray details) throws SQLException {
		for (int i = 0; i < details.size(); i++) {
			JsonElement item = details.get(i);
			String text;
			if (item.isJsonObject()) {
				text = item.getAsJsonObject().get("summary").getAsString();
			} else {
				text = item.getAsString();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO evaluation_reason_details (
					  evaluation_reason_id, detail_code, detail_text, sort_order
					)
					VALUES (?, ?, ?, ?)
					""")) {
				statement.setLong(1, reasonId);
				statement.setString(2, detailCode);
				statement.setString(3, text);
				statement.setInt(4, i + 1);
				statement.executeUpdate();
			}
		}
	}

	private static void insertDimensionResult(
			Connection connection,
			long evaluationId,
			long dimensionId,
			int score,
			String reason) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluation_dimension_results (
				  evaluation_id, dimension_id, score_value, score_text, summary_title, summary_description
				)
				VALUES (?, ?, ?, ?, ?, ?)
				""")) {
			statement.setLong(1, evaluationId);
			statement.setLong(2, dimensionId);
			statement.setBigDecimal(3, java.math.BigDecimal.valueOf(score));
			statement.setString(4, String.format(java.util.Locale.ROOT, "%.1f", (double) score));
			statement.setString(5, "評価結果");
			statement.setString(6, reason);
			statement.executeUpdate();
		}
	}

	private static long insertReason(
			Connection connection,
			long evaluationId,
			long dimensionId,
			String code,
			String category,
			int score,
			String body) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluation_reasons (
				  evaluation_id, reason_code, dimension_id, category_label, title, score_value, body, sort_order
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, evaluationId);
			statement.setString(2, code);
			statement.setLong(3, dimensionId);
			statement.setString(4, category);
			statement.setString(5, category);
			statement.setBigDecimal(6, java.math.BigDecimal.valueOf(score));
			statement.setString(7, body);
			statement.setInt(8, "thinking_expression".equals(code) ? 1 : 2);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("The evaluation reason ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void insertEvidence(
			Connection connection,
			long evaluationId,
			JsonObject result,
			long thinkingReasonId,
			long attitudeReasonId) throws SQLException {
		JsonArray refs = result.getAsJsonArray("evidence_refs");
		JsonArray turningPoints = result.getAsJsonObject("process_analysis").getAsJsonArray("turning_points");
		JsonArray stagnationPoints = result.getAsJsonObject("process_analysis").getAsJsonArray("stagnation_points");
		int sortOrder = 0;
		for (JsonElement refElement : refs) {
			String reference = refElement.getAsString();
			boolean executionReference = reference.startsWith("run_");
			long referenceId = executionReference
					? Long.parseLong(reference.substring(4)) : parseLogId(reference);
			long reasonId = executionReference
					? attitudeReasonId
					: reasonForLog(referenceId, turningPoints, thinkingReasonId, attitudeReasonId);
			String summary = executionReference
					? "評価根拠として参照されたコード実行。"
					: evidenceSummary(referenceId, turningPoints, stagnationPoints);
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO evaluation_evidence (
					  evaluation_reason_id, code_log_id, code_execution_id, evidence_type, evidence_label,
					  source_record_type, source_record_id, evidence_summary, sort_order
					)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""")) {
				statement.setLong(1, reasonId);
				if (executionReference) {
					statement.setNull(2, java.sql.Types.BIGINT);
					statement.setLong(3, referenceId);
					statement.setString(4, "code_execution");
					statement.setString(5, "コード実行");
					statement.setString(6, "code_execution");
				} else {
					statement.setLong(2, referenceId);
					statement.setNull(3, java.sql.Types.BIGINT);
					statement.setString(4, "code_log");
					statement.setString(5, "コードログ");
					statement.setString(6, "code_log");
				}
				statement.setString(7, Long.toString(referenceId));
				statement.setString(8, summary);
				statement.setInt(9, sortOrder++);
				statement.executeUpdate();
			}
		}
	}

	private static long reasonForLog(
			long logId,
			JsonArray turningPoints,
			long thinkingReasonId,
			long attitudeReasonId) {
		for (JsonElement point : turningPoints) {
			if (parseLogId(point.getAsJsonObject().get("log_id").getAsString()) == logId) {
				return thinkingReasonId;
			}
		}
		return attitudeReasonId;
	}

	private static String evidenceSummary(long logId, JsonArray turningPoints, JsonArray stagnationPoints) {
		for (JsonElement point : turningPoints) {
			if (parseLogId(point.getAsJsonObject().get("log_id").getAsString()) == logId) {
				return point.getAsJsonObject().get("summary").getAsString();
			}
		}
		for (JsonElement point : stagnationPoints) {
			if (parseLogId(point.getAsJsonObject().get("log_id").getAsString()) == logId) {
				return point.getAsJsonObject().get("summary").getAsString();
			}
		}
		return "評価根拠として参照されたコードログ。";
	}

	private static long parseLogId(String value) {
		String numericId = value.startsWith("log_") ? value.substring(4) : value;
		return Long.parseLong(numericId);
	}

	private static void rollback(Connection connection, Exception original) {
		try {
			connection.rollback();
		} catch (SQLException rollbackError) {
			original.addSuppressed(rollbackError);
		}
	}

	private record InputMetadata(
			String displayName,
			String loginId,
			String studentCode,
			LocalDateTime submittedAt,
			LocalDateTime logRangeStart) {
	}

	public record EvaluationJob(
			long requestId,
			long evaluationId,
			long submissionId,
			long studentUserId,
			long participationId,
			long taskId,
			String modelId,
			String promptVersion,
			String rubricVersion,
			String consentStatus,
			String externalRequestId) {
	}

	public record EvaluationInput(
			JsonObject payload,
			Set<Long> knownLogIds,
			Set<Long> knownExecutionIds) {
	}
}
