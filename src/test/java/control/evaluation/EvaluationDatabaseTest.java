package control.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dao.EvaluationQueueDao;
import dao.EvaluationWorkerDao;
import dao.ReevaluationJobDao;
import lib.mysql.Client;

class EvaluationDatabaseTest {
	private long userId;
	private long schoolId;
	private long classroomId;
	private long taskId;
	private long rubricId;
	private long promptId;
	private long assignmentId;
	private long participationId;
	private long submissionId;
	private long logId;

	@BeforeEach
	void createSyntheticFixtureInDedicatedDatabase() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("EVALUATION_DB_TEST")),
				"Enable EVALUATION_DB_TEST only with a dedicated migrated test database.");
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_evaluation_test_[a-z0-9_]+"),
				"Evaluation integration tests must never use the development or production database.");
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM users"));
			String token = UUID.randomUUID().toString();
			userId = insert(connection, """
					INSERT INTO users (user_type, login_id, password_hash, display_name,
					  account_status, created_at)
					VALUES ('student', ?, 'synthetic-not-a-login-credential',
					  'Synthetic fixture student', 'active', CURRENT_TIMESTAMP)
					""", "synthetic-" + token);
			schoolId = insert(connection, """
					INSERT INTO schools (school_code, name, school_status, security_level, created_at)
					VALUES (?, 'Synthetic fixture school', 'active', 1, CURRENT_TIMESTAMP)
					""", token);
			update(connection, """
					INSERT INTO student_profiles (user_id, student_code, security_level,
					  first_login_status, must_change_password, school_id)
					VALUES (?, ?, 1, 'completed', FALSE, ?)
					""", userId, token.substring(0, 32), schoolId);
			classroomId = insert(connection, """
					INSERT INTO classrooms (school_id, name, classroom_status, created_at)
					VALUES (?, 'Synthetic fixture class', 'active', CURRENT_TIMESTAMP)
					""", schoolId);
			rubricId = insert(connection, """
					INSERT INTO rubrics (created_by_user_id, title, version, rubric_status, created_at)
					VALUES (?, ?, '1', 'active', CURRENT_TIMESTAMP)
					""", userId, token);
			for (String code : new String[] { "thinking_expression", "proactive_attitude" }) {
				insert(connection, """
						INSERT INTO rubric_dimensions (rubric_id, dimension_code, label,
						  scale_minimum, scale_maximum, scale_label, sort_order)
						VALUES (?, ?, ?, 1, 5, '1-5', ?)
						""", rubricId, code, code, "thinking_expression".equals(code) ? 1 : 2);
			}
			taskId = insert(connection, """
					INSERT INTO tasks (task_code, task_revision_code, revision_number, school_id,
					  created_by_user_id, rubric_id, title, language, description,
					  save_status, publication_status, created_at)
					VALUES (?, ?, 1, ?, ?, ?, 'Synthetic echo exercise', 'python',
					  'Print the input integer. This fixture contains no real student data.',
					  'saved', 'published', CURRENT_TIMESTAMP)
					""", token, token + "-v1", schoolId, userId, rubricId);
			promptId = insert(connection, """
					INSERT INTO prompt_versions (task_id, version, ai_model, common_prompt,
					  prompt_status, fluctuation_generation_status, evaluation_examples_status,
					  created_by_user_id, created_at)
					VALUES (?, '1', ?,
					  'Evaluate only the synthetic evidence. Give concise reasons in Japanese.',
					  'configured', 'completed', 'completed', ?, CURRENT_TIMESTAMP)
					""", taskId, GeminiModelCatalog.DEFAULT_MODEL, userId);
			update(connection, "UPDATE tasks SET active_prompt_version_id = ? WHERE task_id = ?",
					promptId, taskId);
			assignmentId = insert(connection, """
					INSERT INTO task_class_assignments (task_id, classroom_id, assignment_status,
					  late_submission_policy, resubmission_policy, created_at)
					VALUES (?, ?, 'published', 'deny', 'allow', CURRENT_TIMESTAMP)
					""", taskId, classroomId);
			participationId = insert(connection, """
					INSERT INTO task_participations (student_user_id, task_class_assignment_id,
					  learning_status, progress_status, save_status, evaluation_status,
					  active_duration_seconds)
					VALUES (?, ?, 'completed', 'submitted', 'saved', 'not_started', 0)
					""", userId, assignmentId);
			submissionId = insert(connection, """
					INSERT INTO submissions (participation_id, revision_number, submitted_code,
					  submission_status, submitted_at, created_at)
					VALUES (?, 1, 'print(input())', 'submitted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
					""", participationId);
			update(connection, """
					UPDATE task_participations SET draft_base_submission_id = ?
					WHERE participation_id = ?
					""", submissionId, participationId);
			logId = insert(connection, """
					INSERT INTO code_logs (participation_id, submission_id, event_type,
					  snapshot_text, observed_at, created_at)
					VALUES (?, ?, 'manual_save', 'print(input())', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
					""", participationId, submissionId);
			assertTrue(EvaluationQueueDao.enqueueIfConfigured(connection, taskId, submissionId, userId));
		}
	}

	@Test
	void recoversInterruptedEvaluationWithoutCallingProviderOrLosingHistory() throws SQLException {
		EvaluationWorkerDao repository = new EvaluationWorkerDao();
		var job = repository.claimNext().orElseThrow();
		try (Connection connection = Client.createConnection()) {
			update(connection, """
					UPDATE evaluation_requests
					SET requested_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 6 MINUTE)
					WHERE evaluation_request_id = ?
					""", job.requestId());
		}
		assertTrue(repository.claimNext().isEmpty());
		try (Connection connection = Client.createConnection()) {
			assertEquals("in_progress", text(connection,
					"SELECT request_status FROM evaluation_requests WHERE evaluation_request_id = ?", job.requestId()));
			update(connection, """
					UPDATE evaluation_requests
					SET requested_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 16 MINUTE)
					WHERE evaluation_request_id = ?
					""", job.requestId());
		}
		assertTrue(repository.claimNext().isEmpty());
		assertThrows(SQLException.class, () -> repository.fail(job, "Late worker failure", 2));
		try (Connection connection = Client.createConnection()) {
			assertEquals("failed", text(connection,
					"SELECT evaluation_status FROM evaluations WHERE evaluation_id = ?", job.evaluationId()));
			assertEquals("failed", text(connection,
					"SELECT request_status FROM evaluation_requests WHERE evaluation_request_id = ?", job.requestId()));
			assertEquals("failed", text(connection,
					"SELECT evaluation_status FROM task_participations WHERE participation_id = ?", participationId));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM audit_logs WHERE error_code='worker_interrupted'"));
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM evaluation_responses"));
		}
		assertTrue(EvaluationQueueDao.retryFailedEvaluation(userId, assignmentId, submissionId));
		assertTrue(new EvaluationWorker(repository, validProvider()).processNext());
		try (Connection connection = Client.createConnection()) {
			assertEquals("failed", text(connection,
					"SELECT evaluation_status FROM evaluations WHERE evaluation_id = ?", job.evaluationId()));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluations"));
		}
	}

	@Test
	void savesValidatedResultAndReadsItBackWithoutResearchConsent() throws SQLException {
		assertTrue(new EvaluationWorker(new EvaluationWorkerDao(), validProvider()).processNext());
		try (Connection connection = Client.createConnection()) {
			assertEquals("completed", text(connection,
					"SELECT evaluation_status FROM evaluations WHERE submission_id = ?", submissionId));
			assertEquals("completed", text(connection,
					"SELECT evaluation_status FROM task_participations WHERE participation_id = ?", participationId));
			assertEquals("succeeded", text(connection, """
					SELECT request_status FROM evaluation_requests r
					JOIN evaluations e ON e.evaluation_id = r.evaluation_id WHERE e.submission_id = ?
					""", submissionId));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluation_dimension_results"));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluation_reasons"));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM evaluation_evidence"));
			assertEquals(1, count(connection, """
					SELECT COUNT(*) FROM evaluation_input_snapshots
					WHERE anonymized_subject_id LIKE 'RS-%'
					"""));
			assertEquals(1, count(connection, """
					SELECT COUNT(*) FROM evaluation_responses WHERE response_status = 'validated'
					"""));
			assertEquals("unconfirmed", text(connection,
					"SELECT consent_status_at_request FROM evaluation_requests"));
			assertFalse(new EvaluationWorker(new EvaluationWorkerDao(), validProvider()).processNext());
		}
	}

	@Test
	void preservesFailedEvaluationWhenQueueingANewRetry() throws SQLException {
		EvaluationProvider unavailable = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) throws EvaluationProviderException {
				throw new EvaluationProviderException("Synthetic unavailable provider.", true);
			}

			@Override
			public String extractOutputText(JsonObject response) {
				throw new AssertionError("No response is available.");
			}
		};
		assertTrue(new EvaluationWorker(new EvaluationWorkerDao(), unavailable).processNext());
		try (Connection connection = Client.createConnection()) {
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM evaluations WHERE evaluation_status = 'failed'"));
			assertEquals(2, count(connection, "SELECT retry_count FROM evaluation_requests"));
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM evaluation_dimension_results"));
			assertEquals("failed", text(connection,
					"SELECT evaluation_status FROM task_participations WHERE participation_id = ?", participationId));
		}
		assertFalse(EvaluationQueueDao.retryFailedEvaluation(userId, assignmentId, submissionId + 1));
		assertTrue(EvaluationQueueDao.retryFailedEvaluation(userId, assignmentId, submissionId));
		assertFalse(EvaluationQueueDao.retryFailedEvaluation(userId, assignmentId, submissionId));
		assertTrue(new EvaluationWorker(new EvaluationWorkerDao(), validProvider()).processNext());
		try (Connection connection = Client.createConnection()) {
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM evaluations WHERE evaluation_status = 'failed'"));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM evaluations WHERE evaluation_status = 'completed'"));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluation_input_snapshots"));
		}
	}

	@Test
	void invalidOutputNeverCreatesScoresAndRecordsEachResponse() throws SQLException {
		EvaluationProvider invalid = new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				return new JsonObject();
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return "{}";
			}
		};
		assertTrue(new EvaluationWorker(new EvaluationWorkerDao(), invalid).processNext());
		try (Connection connection = Client.createConnection()) {
			assertEquals("failed", text(connection, "SELECT evaluation_status FROM evaluations"));
			assertEquals(3, count(connection,
					"SELECT COUNT(*) FROM evaluation_responses WHERE response_status = 'invalid'"));
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM evaluation_dimension_results"));
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM evaluation_reasons"));
		}
	}

	@Test
	void olderSubmissionCompletionDoesNotReplaceCurrentParticipationState() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			update(connection, """
					UPDATE task_participations SET draft_base_submission_id = NULL,
					  evaluation_status = 'not_started' WHERE participation_id = ?
					""", participationId);
		}
		assertTrue(new EvaluationWorker(new EvaluationWorkerDao(), validProvider()).processNext());
		try (Connection connection = Client.createConnection()) {
			assertEquals("completed", text(connection, "SELECT evaluation_status FROM evaluations"));
			assertEquals("not_started", text(connection,
					"SELECT evaluation_status FROM task_participations WHERE participation_id = ?", participationId));
		}
	}

	@Test
	void duplicateCompletionRollsBackWithoutDuplicatingResultRows() throws SQLException, EvaluationProviderException {
		EvaluationWorkerDao repository = new EvaluationWorkerDao();
		var job = repository.claimNext().orElseThrow();
		var input = repository.loadAndPersistInput(job);
		JsonObject result = EvaluationResponseValidator.parseAndValidate(
				validProvider().extractOutputText(new JsonObject()), input.knownLogIds(), input.knownExecutionIds());
		repository.complete(job, result);
		assertThrows(SQLException.class, () -> repository.complete(job, result));
		try (Connection connection = Client.createConnection()) {
			assertEquals("completed", text(connection, "SELECT evaluation_status FROM evaluations"));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluation_dimension_results"));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluation_reasons"));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM evaluation_evidence"));
		}
	}

	@Test
	void materializesConfirmedPreviewWithoutCallingProviderOrChangingPriorEvaluation() throws SQLException {
		long jobId;
		long jobTargetId;
		JsonObject payload = new JsonObject();
		JsonObject metadata = new JsonObject();
		metadata.addProperty("request_id", UUID.randomUUID().toString());
		metadata.addProperty("model_id", "gemini-3.7-flash");
		metadata.addProperty("prompt_version", "1");
		metadata.addProperty("rubric_version", "1");
		metadata.addProperty("consent_status", "unconfirmed");
		metadata.addProperty("anonymized_subject_id", "RS-synthetic-preview");
		payload.add("metadata", metadata);
		payload.add("task", new JsonObject());
		payload.add("rubric", new JsonObject());
		JsonObject submission = new JsonObject();
		submission.addProperty("submitted_at", java.time.LocalDateTime.now().toString());
		submission.addProperty("submitted_code", "print(input())");
		payload.add("submission", submission);
		JsonArray logs = new JsonArray();
		JsonObject log = new JsonObject();
		log.addProperty("log_id", logId);
		log.addProperty("event_type", "manual_save");
		log.addProperty("observed_at", java.time.LocalDateTime.now().toString());
		logs.add(log);
		payload.add("timeline_logs", logs);
		payload.add("prompt_evaluation_settings", new JsonObject());
		JsonObject rawResponse = new JsonObject();
		rawResponse.addProperty("provider", "synthetic");
		JsonObject validatedResult;
		try {
			validatedResult = EvaluationResponseValidator.parseAndValidate(
					validProvider().extractOutputText(rawResponse), Set.of(logId), Set.of());
		} catch (EvaluationProviderException impossible) {
			throw new SQLException("The synthetic provider output could not be prepared.", impossible);
		}

		try (Connection connection = Client.createConnection()) {
			update(connection, "UPDATE submissions SET submission_status = 'accepted' WHERE submission_id = ?",
					submissionId);
			jobId = insert(connection, """
					INSERT INTO reevaluation_jobs (
					  task_id, prompt_version_id, requested_by_user_id, reevaluation_status,
					  target_count, completed_count, progress_percent, result_summary
					)
					VALUES (?, ?, ?, 'queued', 1, 0, 0, JSON_OBJECT('failed_count', 0))
					""", taskId, promptId, userId);
			jobTargetId = insert(connection, """
					INSERT INTO reevaluation_job_targets (
					  reevaluation_job_id, participation_id, submission_id, target_status,
					  evaluation_payload, provider_response, validated_result,
					  materialization_attempts, created_at, updated_at
					)
					VALUES (?, ?, ?, 'queued', ?, ?, ?, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
					""", jobId, participationId, submissionId,
					payload.toString(), rawResponse.toString(), validatedResult.toString());
		}

		ReevaluationJobDao.JobWorkItem workItem = new ReevaluationJobDao().claimNextTarget().orElseThrow();
		assertEquals(jobTargetId, workItem.targetId());
		new EvaluationWorkerDao().materializeReevaluationTarget(workItem);

		try (Connection connection = Client.createConnection()) {
			assertEquals("completed", text(connection,
					"SELECT reevaluation_status FROM reevaluation_jobs WHERE reevaluation_job_id = ?", jobId));
			assertEquals("completed", text(connection,
					"SELECT target_status FROM reevaluation_job_targets WHERE reevaluation_job_target_id = ?",
					jobTargetId));
			assertEquals("completed", text(connection, """
					SELECT evaluation_status FROM evaluations
					WHERE reevaluation_job_id = ?
					""", jobId));
			assertEquals("reevaluation", text(connection,
					"SELECT evaluation_kind FROM evaluations WHERE reevaluation_job_id = ?", jobId));
			assertEquals("teacher", text(connection, """
					SELECT actor_role FROM evaluation_requests r
					JOIN evaluations e ON e.evaluation_id = r.evaluation_id
					WHERE e.reevaluation_job_id = ?
					""", jobId));
			assertEquals("succeeded", text(connection, """
					SELECT request_status FROM evaluation_requests r
					JOIN evaluations e ON e.evaluation_id = r.evaluation_id
					WHERE e.reevaluation_job_id = ?
					""", jobId));
			assertEquals(1, count(connection, """
					SELECT COUNT(*) FROM evaluation_responses r
					JOIN evaluation_requests q ON q.evaluation_request_id = r.evaluation_request_id
					JOIN evaluations e ON e.evaluation_id = q.evaluation_id
					WHERE e.reevaluation_job_id = ? AND r.response_status = 'validated'
					""", jobId));
			assertEquals(1, count(connection, """
					SELECT COUNT(*) FROM evaluation_input_snapshots s
					JOIN evaluation_requests q ON q.evaluation_request_id = s.evaluation_request_id
					JOIN evaluations e ON e.evaluation_id = q.evaluation_id
					WHERE e.reevaluation_job_id = ?
					""", jobId));
			assertEquals(2, count(connection, """
					SELECT COUNT(*) FROM evaluation_dimension_results d
					JOIN evaluations e ON e.evaluation_id = d.evaluation_id
					WHERE e.reevaluation_job_id = ?
					""", jobId));
			assertEquals(1, count(connection, """
					SELECT COUNT(*) FROM evaluation_evidence ev
					JOIN evaluation_reasons r ON r.evaluation_reason_id = ev.evaluation_reason_id
					JOIN evaluations e ON e.evaluation_id = r.evaluation_id
					WHERE e.reevaluation_job_id = ?
					""", jobId));
			assertEquals(1, count(connection, """
					SELECT COUNT(*) FROM reevaluation_job_targets
					WHERE reevaluation_job_target_id = ? AND evaluation_payload IS NULL
					  AND provider_response IS NULL AND validated_result IS NULL AND evaluation_id IS NOT NULL
					""", jobTargetId));
			assertEquals("not_started", text(connection, """
					SELECT evaluation_status FROM evaluations
					WHERE reevaluation_job_id IS NULL AND submission_id = ?
					""", submissionId));
		}
	}

	@Test
	void savesLiveGeminiResultOnlyWhenExplicitlyEnabled() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("GEMINI_API_SMOKE_TEST")),
				"Enable GEMINI_API_SMOKE_TEST to make up to three billable synthetic requests.");
		assertTrue(new EvaluationWorker(new EvaluationWorkerDao(), new GeminiEvaluationClient()).processNext());
		try (Connection connection = Client.createConnection()) {
			String detail = text(connection, "SELECT error_detail FROM evaluation_requests");
			assertEquals("completed", text(connection,
					"SELECT evaluation_status FROM evaluations WHERE submission_id = ?", submissionId),
					"Live synthetic evaluation failed: " + detail);
			assertEquals("succeeded", text(connection, "SELECT request_status FROM evaluation_requests"));
			assertEquals(2, count(connection, "SELECT COUNT(*) FROM evaluation_dimension_results"));
			assertEquals(1, count(connection,
					"SELECT COUNT(*) FROM evaluation_responses WHERE response_status = 'validated'"));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM evaluation_input_snapshots"));
		}
	}

	private EvaluationProvider validProvider() {
		return new EvaluationProvider() {
			@Override
			public JsonObject generate(String modelId, JsonObject payload) {
				assertEquals(GeminiModelCatalog.DEFAULT_MODEL, modelId);
				assertTrue(payload.getAsJsonObject("metadata").get("anonymized_subject_id")
						.getAsString().startsWith("RS-"));
				assertFalse(payload.toString().contains("Synthetic fixture student"));
				return new JsonObject();
			}

			@Override
			public String extractOutputText(JsonObject response) {
				return """
						{
						  "scores":{"thinking_expression_level":4,"proactive_attitude_level":3},
						  "reasons":{"thinking_expression_reason":"合成根拠A","proactive_attitude_reason":"合成根拠B"},
						  "process_analysis":{"pattern_label":"synthetic","turning_points":[],
						    "stagnation_points":[],"teacher_support_suggestions":[]},
						  "confidence":0.8,"evidence_refs":["%d"],"warnings":[]
						}
						""".formatted(logId);
			}
		};
	}

	@AfterEach
	void removeOnlySyntheticFixtureRows() throws SQLException {
		if (userId == 0) {
			return;
		}
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				update(connection, """
						DELETE jt FROM reevaluation_job_targets jt
						JOIN reevaluation_jobs j ON j.reevaluation_job_id = jt.reevaluation_job_id
						WHERE j.task_id = ?
						""", taskId);
				update(connection, """
						DELETE ev FROM evaluation_evidence ev
						JOIN evaluation_reasons r ON r.evaluation_reason_id = ev.evaluation_reason_id
						JOIN evaluations e ON e.evaluation_id = r.evaluation_id WHERE e.submission_id = ?
						""", submissionId);
				update(connection, """
						DELETE d FROM evaluation_reason_details d
						JOIN evaluation_reasons r ON r.evaluation_reason_id = d.evaluation_reason_id
						JOIN evaluations e ON e.evaluation_id = r.evaluation_id WHERE e.submission_id = ?
						""", submissionId);
				for (String table : new String[] { "evaluation_reasons", "evaluation_dimension_results" }) {
					update(connection, "DELETE r FROM " + table
							+ " r JOIN evaluations e ON e.evaluation_id = r.evaluation_id WHERE e.submission_id = ?",
							submissionId);
				}
				for (String table : new String[] { "evaluation_responses", "evaluation_input_snapshots" }) {
					update(connection, "DELETE s FROM " + table
							+ " s JOIN evaluation_requests r ON r.evaluation_request_id = s.evaluation_request_id"
							+ " JOIN evaluations e ON e.evaluation_id = r.evaluation_id WHERE e.submission_id = ?",
							submissionId);
				}
				update(connection, """
						DELETE a FROM audit_logs a JOIN evaluations e
						ON e.evaluation_id = a.target_id
						WHERE a.target_type = 'evaluation' AND e.submission_id = ?
						""", submissionId);
				update(connection, """
						DELETE r FROM evaluation_requests r JOIN evaluations e
						ON e.evaluation_id = r.evaluation_id WHERE e.submission_id = ?
						""", submissionId);
				update(connection, "DELETE FROM evaluations WHERE submission_id = ?", submissionId);
				update(connection, "DELETE FROM reevaluation_jobs WHERE task_id = ?", taskId);
				update(connection, "DELETE FROM code_logs WHERE participation_id = ?", participationId);
				update(connection, "UPDATE task_participations SET draft_base_submission_id = NULL WHERE participation_id = ?",
						participationId);
				update(connection, "DELETE FROM submissions WHERE participation_id = ?", participationId);
				update(connection, "DELETE FROM task_participations WHERE participation_id = ?", participationId);
				update(connection, "DELETE FROM task_class_assignments WHERE task_class_assignment_id = ?", assignmentId);
				update(connection, "UPDATE tasks SET active_prompt_version_id = NULL WHERE task_id = ?", taskId);
				update(connection, "DELETE FROM prompt_versions WHERE prompt_version_id = ?", promptId);
				update(connection, "DELETE FROM tasks WHERE task_id = ?", taskId);
				update(connection, "DELETE FROM rubric_dimensions WHERE rubric_id = ?", rubricId);
				update(connection, "DELETE FROM rubrics WHERE rubric_id = ?", rubricId);
				update(connection, "DELETE FROM classrooms WHERE classroom_id = ?", classroomId);
				update(connection, "DELETE FROM research_subject_identifiers WHERE student_user_id = ?", userId);
				update(connection, "DELETE FROM student_profiles WHERE user_id = ?", userId);
				update(connection, "DELETE FROM schools WHERE school_id = ?", schoolId);
				update(connection, "DELETE FROM users WHERE user_id = ?", userId);
				connection.commit();
			} catch (SQLException | RuntimeException e) {
				try {
					connection.rollback();
				} catch (SQLException rollbackError) {
					e.addSuppressed(rollbackError);
				}
				throw e;
			}
		}
	}

	private static long insert(Connection connection, String sql, Object... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			bind(statement, parameters);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic fixture key was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void update(Connection connection, String sql, Object... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			statement.executeUpdate();
		}
	}

	private static int count(Connection connection, String sql, Object... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			try (ResultSet rows = statement.executeQuery()) {
			assertTrue(rows.next());
			return rows.getInt(1);
			}
		}
	}

	private static String text(Connection connection, String sql, Object... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			try (ResultSet rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getString(1);
			}
		}
	}

	private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			statement.setObject(i + 1, parameters[i]);
		}
	}
}
