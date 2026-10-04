package dao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import entity.StudentSurveyPage;
import lib.mysql.Client;

class StudentSurveyDatabaseTest {
	private final StudentSurveyDao surveys = new StudentSurveyDao();
	private boolean fixtureInstalled;

	@BeforeEach
	void installOnlyInEmptyDedicatedDatabase() throws SQLException, IOException {
		Assumptions.assumeTrue("true".equals(System.getenv("SURVEY_DB_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_debug_test_[a-z0-9_]+"));
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM users"));
			connection.setAutoCommit(false);
			try (var resource = getClass().getResourceAsStream("/student-survey-fixture.sql");
					Statement statement = connection.createStatement()) {
				assertNotNull(resource);
				String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
						.replace("@PASSWORD_HASH@", "synthetic-not-a-login-credential");
				for (String command : sql.split(";")) {
					if (!command.isBlank()) {
						statement.executeUpdate(command);
					}
				}
				connection.commit();
				fixtureInstalled = true;
			} catch (SQLException | IOException | RuntimeException | Error e) {
				try {
					connection.rollback();
				} catch (SQLException rollbackError) {
					e.addSuppressed(rollbackError);
				}
				throw e;
			}
		}
	}

	@Test
	void readsFeedbackSummaryWithAnExistingCompletedEvaluation() throws SQLException {
		StudentSurveyPage page = surveys.findSurveyPage(1, 1, 1, null).orElseThrow();
		assertEquals("Synthetic feedback summary", page.getEvaluationFeedback());
		assertEquals("not_answered", page.getResponseStatus());
		assertEquals(1, page.getQuestions().size());
	}

	@Test
	void reloadsDraftAndSubmissionWithoutOverwritingADuplicateSubmit() throws SQLException {
		assertEquals(StudentSurveyDao.SaveResult.SAVED,
				surveys.saveResponse(1, 1, 1, 1, Map.of("answer_1", new String[] {"draft answer"}), false));
		StudentSurveyPage draft = surveys.findSurveyPage(1, 1, 1, 1L).orElseThrow();
		assertTrue(draft.isInProgress());
		assertEquals("draft answer", draft.getQuestions().getFirst().getAnswerValue());
		assertEquals(StudentSurveyDao.SaveResult.SUBMITTED,
				surveys.saveResponse(1, 1, 1, 1, Map.of("answer_1", new String[] {"final answer"}), true));
		assertEquals(StudentSurveyDao.SaveResult.ALREADY_SUBMITTED,
				surveys.saveResponse(1, 1, 1, 1, Map.of("answer_1", new String[] {"overwrite"}), true));
		StudentSurveyPage submitted = surveys.findSurveyPage(1, 1, 1, 1L).orElseThrow();
		assertTrue(submitted.isReadOnly());
		assertEquals("final answer", submitted.getQuestions().getFirst().getAnswerValue());
	}

	@Test
	void rejectsMissingConsentAndOtherStudentsEvaluation() throws SQLException {
		assertTrue(surveys.findSurveyPage(1, 1, 4, null).isEmpty());
		assertThrows(SecurityException.class,
				() -> surveys.saveResponse(1, 1, 1, 4, Map.of(), false));
		update("UPDATE consent_records SET consent_status = 'withdrawn' WHERE user_id = 1");
		assertThrows(SecurityException.class, () -> surveys.findSurveyPage(1, 1, 1, null));
		assertThrows(SecurityException.class,
				() -> surveys.saveResponse(1, 1, 1, 1, Map.of(), false));
	}

	@Test
	void rejectsInactiveSurveyAndIncompleteEvaluation() throws SQLException {
		update("UPDATE surveys SET survey_status = 'closed' WHERE survey_id = 1");
		assertTrue(surveys.findSurveyPage(1, 1, 1, null).isEmpty());
		assertThrows(SecurityException.class,
				() -> surveys.saveResponse(1, 1, 1, 1, Map.of(), false));
		update("UPDATE surveys SET survey_status = 'active' WHERE survey_id = 1");
		update("UPDATE evaluations SET evaluation_status = 'failed' WHERE evaluation_id = 1");
		assertTrue(surveys.findSurveyPage(1, 1, 1, null).isEmpty());
		assertThrows(SecurityException.class,
				() -> surveys.saveResponse(1, 1, 1, 1, Map.of(), false));
	}

	@AfterEach
	void removeOnlySyntheticFixture() throws SQLException {
		if (!fixtureInstalled) return;
		try (Connection connection = Client.createConnection(); Statement statement = connection.createStatement()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try {
				for (String sql : new String[] {
						"DELETE a FROM survey_answers a JOIN survey_responses r USING (survey_response_id) WHERE r.student_user_id IN (1,2)",
						"DELETE FROM survey_responses WHERE student_user_id IN (1,2)",
						"DELETE FROM survey_questions WHERE survey_id = 1",
						"DELETE FROM surveys WHERE survey_id = 1",
						"DELETE FROM evaluations WHERE evaluation_id IN (1,2,3,4)",
						"DELETE FROM code_logs WHERE participation_id = 1",
						"DELETE FROM code_executions WHERE execution_id = 9001",
						"DELETE FROM submissions WHERE participation_id IN (1,2)",
						"DELETE FROM task_participations WHERE participation_id IN (1,2)",
						"DELETE FROM task_class_assignments WHERE task_class_assignment_id = 1",
						"DELETE FROM tasks WHERE task_id = 1",
						"DELETE FROM rubrics WHERE rubric_id = 1",
						"DELETE FROM consent_records WHERE user_id IN (1,2)",
						"DELETE FROM consent_document_versions WHERE consent_document_version_id = 9001",
						"DELETE FROM student_class_memberships WHERE student_user_id IN (1,2)",
						"DELETE FROM classrooms WHERE classroom_id = 1",
						"DELETE FROM student_profiles WHERE user_id IN (1,2)",
						"DELETE FROM schools WHERE school_id = 1",
						"DELETE FROM users WHERE user_id IN (1,2,3) AND login_id IN ('debug-student','debug-other','debug-admin')"
				}) {
					statement.executeUpdate(sql);
				}
				connection.commit();
				assertEquals(0, count(connection, "SELECT COUNT(*) FROM users"));
			} catch (SQLException | RuntimeException | Error e) {
				try {
					connection.rollback();
				} catch (SQLException rollbackError) {
					e.addSuppressed(rollbackError);
				}
				throw e;
			}
		}
	}

	private static void update(String sql) throws SQLException {
		try (Connection connection = Client.createConnection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

	private static int count(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getInt(1);
		}
	}
}
