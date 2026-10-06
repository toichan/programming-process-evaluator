package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import entity.ConsentDocument;
import entity.ConsentSaveResult;
import entity.ConsentStatus;
import entity.StudentAccountDetails;
import entity.StudentAffiliation;
import entity.StudentConsentPage;
import entity.StudentCredentialHistoryEntry;
import entity.StudentTaskSummary;
import entity.UserCredential.FirstLoginStatus;
import lib.mysql.Client;

public final class StudentDao {
	private static final String FIND_ACCOUNT = """
			SELECT u.login_id AS student_id,
			       CASE WHEN sp.school_id IS NULL THEN sp.security_level ELSE school.security_level END AS security_level,
			       sp.first_login_status, sp.must_change_password
			FROM users u
			JOIN student_profiles sp ON sp.user_id = u.user_id
			LEFT JOIN schools school ON school.school_id = sp.school_id
			WHERE u.user_id = ? AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			""";

	private static final String FIND_AFFILIATIONS = """
			SELECT DISTINCT s.name AS school_name, c.grade_name, c.name AS classroom_name
			FROM student_class_memberships scm
			JOIN users u ON u.user_id = scm.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			JOIN classrooms c ON c.classroom_id = scm.classroom_id AND c.classroom_status = 'active'
			JOIN schools s ON s.school_id = c.school_id AND s.school_status = 'active'
			WHERE scm.student_user_id = ? AND scm.membership_status = 'active'
			ORDER BY s.name, c.grade_name, c.name
			""";

	private static final String FIND_TASKS = """
			SELECT DISTINCT ca.task_class_assignment_id, t.task_id, t.title, t.theme, t.difficulty,
			       t.description, ca.due_at,
			       CASE WHEN ca.due_at IS NULL THEN NULL
			            ELSE TIMESTAMPDIFF(SECOND, CURRENT_TIMESTAMP, ca.due_at) END AS seconds_until_due,
			       COALESCE(tp.learning_status, 'not_started') AS learning_status,
			       COALESCE(tp.progress_status, 'not_started') AS progress_status,
			       COALESCE(tp.save_status, 'unsaved') AS save_status,
			       COALESCE(tp.evaluation_status, 'not_started') AS evaluation_status,
			       (SELECT s_latest.submission_id
			        FROM submissions s_latest
			        WHERE s_latest.participation_id = tp.participation_id
			        ORDER BY s_latest.revision_number DESC
			        LIMIT 1) AS latest_submission_id,
			       (SELECT sv.survey_id
			        FROM surveys sv
			        WHERE sv.task_id = t.task_id AND sv.survey_status = 'active'
			        ORDER BY sv.survey_id DESC
			        LIMIT 1) AS active_survey_id,
			       (SELECT e_done.evaluation_id
			        FROM task_participations tp_done
			        JOIN submissions s_done ON s_done.participation_id = tp_done.participation_id
			        JOIN evaluations e_done ON e_done.submission_id = s_done.submission_id
			        WHERE tp_done.student_user_id = scm.student_user_id
			          AND tp_done.task_class_assignment_id = ca.task_class_assignment_id
			          AND e_done.evaluation_status = 'completed'
			          AND e_done.evaluation_kind IN ('initial','reevaluation')
			        ORDER BY s_done.revision_number DESC, e_done.completed_at DESC, e_done.evaluation_id DESC
			        LIMIT 1) AS latest_completed_evaluation_id,
			       (SELECT sr.response_status
			        FROM survey_responses sr
			        WHERE sr.student_user_id = scm.student_user_id
			          AND sr.survey_id = (
			            SELECT sv.survey_id FROM surveys sv
			            WHERE sv.task_id = t.task_id AND sv.survey_status = 'active'
			            ORDER BY sv.survey_id DESC LIMIT 1
			          )
			          AND sr.evaluation_id = (
			            SELECT e_done.evaluation_id
			            FROM task_participations tp_done
			            JOIN submissions s_done ON s_done.participation_id = tp_done.participation_id
			            JOIN evaluations e_done ON e_done.submission_id = s_done.submission_id
			            WHERE tp_done.student_user_id = scm.student_user_id
			              AND tp_done.task_class_assignment_id = ca.task_class_assignment_id
			              AND e_done.evaluation_status = 'completed'
			              AND e_done.evaluation_kind IN ('initial','reevaluation')
			            ORDER BY s_done.revision_number DESC, e_done.completed_at DESC, e_done.evaluation_id DESC
			            LIMIT 1
			          )
			        LIMIT 1) AS target_survey_response_status
			FROM student_class_memberships scm
			JOIN classrooms c ON c.classroom_id = scm.classroom_id AND c.classroom_status = 'active'
			JOIN schools s ON s.school_id = c.school_id AND s.school_status = 'active'
			JOIN task_class_assignments ca ON ca.classroom_id = c.classroom_id
			  AND (
			    ca.assignment_status = 'published'
			    OR (ca.assignment_status = 'expired' AND (
			      ca.late_submission_policy = 'allow'
			      OR EXISTS (
			        SELECT 1
			        FROM task_participations tp_expired
			        JOIN submissions s_expired ON s_expired.participation_id = tp_expired.participation_id
			        WHERE tp_expired.task_class_assignment_id = ca.task_class_assignment_id
			          AND tp_expired.student_user_id = scm.student_user_id
			      )
			    ))
			  )
			  AND (ca.publish_at IS NULL OR ca.publish_at <= CURRENT_TIMESTAMP)
			JOIN tasks t ON t.task_id = ca.task_id
			  AND t.publication_status = 'published' AND t.deleted_at IS NULL
			LEFT JOIN task_participations tp
			  ON tp.task_class_assignment_id = ca.task_class_assignment_id
			  AND tp.student_user_id = scm.student_user_id
			WHERE scm.student_user_id = ? AND scm.membership_status = 'active'
			ORDER BY ca.due_at, t.title, ca.task_class_assignment_id
			""";

	private static final String FIND_LATEST_CONSENT = """
			SELECT consent_id, consent_status, updated_at
			FROM consent_records
			WHERE user_id = ?
			ORDER BY consent_id DESC
			LIMIT 1
			""";

	private static final String FIND_ACTIVE_DOCUMENT = """
			SELECT consent_document_version_id, version_code, title, body
			FROM consent_document_versions
			WHERE document_status = 'active'
			  AND (effective_at IS NULL OR effective_at <= CURRENT_TIMESTAMP)
			ORDER BY COALESCE(effective_at, created_at) DESC, consent_document_version_id DESC
			LIMIT 1
			""";

	public Optional<StudentAccountDetails> findAccountDetails(long studentUserId) throws SQLException {
		String studentId;
		int securityLevel;
		FirstLoginStatus firstLoginStatus;
		boolean mustChangePassword;
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(FIND_ACCOUNT)) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				String firstLoginStatusValue = resultSet.getString("first_login_status");
				try {
					firstLoginStatus = FirstLoginStatus.valueOf(firstLoginStatusValue.toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new SQLException("Invalid stored first-login status.", e);
				}
				studentId = resultSet.getString("student_id");
				securityLevel = resultSet.getInt("security_level");
				if (resultSet.wasNull() || (securityLevel != 1 && securityLevel != 2)) {
					throw new SQLException("Student account has no valid school security policy.");
				}
				mustChangePassword = resultSet.getBoolean("must_change_password");
			}
			return Optional.of(new StudentAccountDetails(
					studentId,
					securityLevel,
					firstLoginStatus,
					mustChangePassword,
					findAffiliations(connection, studentUserId),
					findCredentialHistory(connection, studentUserId, studentId)));
		}
	}

	public List<StudentAffiliation> findAffiliations(long studentUserId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			return findAffiliations(connection, studentUserId);
		}
	}

	public List<StudentTaskSummary> findPublishedTasks(long studentUserId, ConsentStatus consentStatus) throws SQLException {
		List<StudentTaskSummary> tasks = new ArrayList<>();
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(FIND_TASKS)) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					Timestamp dueAt = resultSet.getTimestamp("due_at");
					long secondsUntilDueValue = resultSet.getLong("seconds_until_due");
					Long secondsUntilDue = resultSet.wasNull() ? null : secondsUntilDueValue;
					long latestSubmissionValue = resultSet.getLong("latest_submission_id");
					Long latestSubmissionId = resultSet.wasNull() ? null : latestSubmissionValue;
					long activeSurveyValue = resultSet.getLong("active_survey_id");
					Long activeSurveyId = resultSet.wasNull() ? null : activeSurveyValue;
					long latestEvaluationValue = resultSet.getLong("latest_completed_evaluation_id");
					Long latestCompletedEvaluationId = resultSet.wasNull() ? null : latestEvaluationValue;
					String targetResponseStatus = resultSet.getString("target_survey_response_status");
					String surveyStatus = activeSurveyId == null || latestCompletedEvaluationId == null
							? "not_applicable"
							: targetResponseStatus == null ? "not_answered" : targetResponseStatus;
					tasks.add(new StudentTaskSummary(
							resultSet.getLong("task_class_assignment_id"),
							resultSet.getLong("task_id"),
							resultSet.getString("title"),
							resultSet.getString("theme"),
							resultSet.getString("difficulty"),
							resultSet.getString("description"),
							dueAt == null ? null : dueAt.toLocalDateTime(),
							resultSet.getString("learning_status"),
							resultSet.getString("progress_status"),
							resultSet.getString("save_status"),
							resultSet.getString("evaluation_status"),
							surveyStatus,
							activeSurveyId,
							latestCompletedEvaluationId,
							consentStatus,
							secondsUntilDue,
							latestSubmissionId));
				}
			}
		}
		return List.copyOf(tasks);
	}

	public StudentConsentPage findConsentPage(long studentUserId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			Optional<ConsentDocument> document = findActiveDocument(connection);
			Optional<ConsentResponse> response = findLatestConsentResponse(connection, studentUserId);
			return new StudentConsentPage(
					document,
					response.map(ConsentResponse::status).orElse(ConsentStatus.UNCONFIRMED),
					response.map(ConsentResponse::respondedAt).orElse(null),
					response.map(ConsentResponse::id).orElse(0L));
		}
	}

	public ConsentStatus findConsentStatus(long studentUserId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			return findLatestConsentResponse(connection, studentUserId)
					.map(ConsentResponse::status)
					.orElse(ConsentStatus.UNCONFIRMED);
		}
	}

	public ConsentSaveResult saveInitialConsent(
			long studentUserId,
			long submittedDocumentId,
			ConsentStatus decision) throws SQLException {
		return saveConsent(studentUserId, submittedDocumentId, decision, 0, false);
	}

	public ConsentSaveResult saveConsent(
			long studentUserId,
			long submittedDocumentId,
			ConsentStatus decision,
			long expectedResponseId,
			boolean changeConfirmed) throws SQLException {
		if (decision != ConsentStatus.AGREED && decision != ConsentStatus.DECLINED) {
			return ConsentSaveResult.INVALID_DECISION;
		}
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				if (!lockActiveStudent(connection, studentUserId)) {
					connection.rollback();
					return ConsentSaveResult.ACCOUNT_UNAVAILABLE;
				}

				Optional<ConsentDocument> activeDocument = findActiveDocument(connection, true);
				if (activeDocument.isEmpty()) {
					connection.rollback();
					return ConsentSaveResult.NO_ACTIVE_DOCUMENT;
				}
				if (activeDocument.get().getId() != submittedDocumentId) {
					connection.rollback();
					return ConsentSaveResult.DOCUMENT_CHANGED;
				}
				Optional<ConsentResponse> previous = findLatestConsentResponse(connection, studentUserId);
				ConsentStatus current = previous.map(ConsentResponse::status).orElse(ConsentStatus.UNCONFIRMED);
				ConsentStatus target = entity.ConsentDecision.resolve(current, decision);
				if (previous.map(ConsentResponse::id).orElse(0L) != expectedResponseId) {
					connection.rollback();
					return ConsentSaveResult.RESPONSE_CHANGED;
				}
				if (current == target) {
					connection.rollback();
					return ConsentSaveResult.ALREADY_RECORDED;
				}
				if (current != ConsentStatus.UNCONFIRMED && !changeConfirmed) {
					connection.rollback();
					return ConsentSaveResult.CONFIRMATION_REQUIRED;
				}

				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO consent_records
							(user_id, consent_status, consent_document_version_id, consented_at, withdrawn_at, updated_at)
						VALUES (?, ?, ?, CASE WHEN ? = 'agreed' THEN CURRENT_TIMESTAMP ELSE NULL END,
						  CASE WHEN ? = 'withdrawn' THEN CURRENT_TIMESTAMP ELSE NULL END, CURRENT_TIMESTAMP)
						""")) {
					statement.setLong(1, studentUserId);
					statement.setString(2, target.getDatabaseValue());
					statement.setLong(3, submittedDocumentId);
					statement.setString(4, target.getDatabaseValue());
					statement.setString(5, target.getDatabaseValue());
					statement.executeUpdate();
				}
				connection.commit();
				return ConsentSaveResult.RECORDED;
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

	private static List<StudentAffiliation> findAffiliations(Connection connection, long studentUserId)
			throws SQLException {
		List<StudentAffiliation> affiliations = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(FIND_AFFILIATIONS)) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					affiliations.add(new StudentAffiliation(
							resultSet.getString("school_name"),
							resultSet.getString("grade_name"),
							resultSet.getString("classroom_name")));
				}
			}
		}
		return List.copyOf(affiliations);
	}

	private static List<StudentCredentialHistoryEntry> findCredentialHistory(
			Connection connection,
			long studentUserId,
			String studentId) throws SQLException {
		List<StudentCredentialHistoryEntry> history = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT ch.action_type, ch.result_status, ch.actor_user_id,
				       actor.user_type AS actor_user_type, actor.login_id AS actor_login_id,
				       ch.occurred_at
				FROM credential_history ch
				LEFT JOIN users actor ON actor.user_id = ch.actor_user_id
				WHERE ch.target_user_id = ?
				ORDER BY ch.occurred_at DESC, ch.credential_history_id DESC
				""")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					long actorUserId = resultSet.getLong("actor_user_id");
					boolean hasActor = !resultSet.wasNull();
					String actorType = resultSet.getString("actor_user_type");
					String actorLoginId = resultSet.getString("actor_login_id");
					String actorLabel = !hasActor
							? "システム"
							: actorUserId == studentUserId
									? "生徒（" + studentId + "）"
									: "teacher".equals(actorType)
											? "教師（" + actorLoginId + "）"
											: "admin".equals(actorType)
													? "管理者（" + actorLoginId + "）"
													: "利用者";
					Timestamp occurredAt = resultSet.getTimestamp("occurred_at");
					history.add(new StudentCredentialHistoryEntry(
							resultSet.getString("action_type"),
							resultSet.getString("result_status"),
							actorLabel,
							occurredAt.toLocalDateTime()));
				}
			}
		}
		return List.copyOf(history);
	}

	private static Optional<ConsentDocument> findActiveDocument(Connection connection) throws SQLException {
		return findActiveDocument(connection, false);
	}

	private static Optional<ConsentDocument> findActiveDocument(Connection connection, boolean lock)
			throws SQLException {
		String sql = lock ? FIND_ACTIVE_DOCUMENT + " FOR SHARE" : FIND_ACTIVE_DOCUMENT;
		try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet resultSet = statement.executeQuery()) {
			if (!resultSet.next()) {
				return Optional.empty();
			}
			return Optional.of(new ConsentDocument(
					resultSet.getLong("consent_document_version_id"),
					resultSet.getString("version_code"),
					resultSet.getString("title"),
					resultSet.getString("body")));
		}
	}

	private static Optional<ConsentResponse> findLatestConsentResponse(Connection connection, long studentUserId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_LATEST_CONSENT)) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				ConsentStatus status;
				try {
					status = ConsentStatus.fromDatabase(resultSet.getString("consent_status"));
				} catch (IllegalArgumentException e) {
					throw new SQLException("Invalid stored consent status.", e);
				}
				Timestamp respondedAt = resultSet.getTimestamp("updated_at");
				return Optional.of(new ConsentResponse(
						resultSet.getLong("consent_id"),
						status,
						respondedAt == null ? null : respondedAt.toLocalDateTime()));
			}
		}
	}

	private static boolean lockActiveStudent(Connection connection, long studentUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT user_id
				FROM users
				WHERE user_id = ? AND user_type = 'student'
				  AND account_status = 'active' AND deleted_at IS NULL
				FOR UPDATE
				""")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return false;
				}
			}
		}
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT user_id FROM student_profiles WHERE user_id = ?")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private record ConsentResponse(long id, ConsentStatus status, java.time.LocalDateTime respondedAt) {
	}
}
