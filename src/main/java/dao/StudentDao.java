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
			SELECT sp.student_code, sp.security_level, sp.first_login_status, sp.must_change_password
			FROM users u
			JOIN student_profiles sp ON sp.user_id = u.user_id
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
			       CASE
			         WHEN NOT EXISTS (
			           SELECT 1 FROM surveys sv
			           WHERE sv.task_id = t.task_id AND sv.survey_status = 'active'
			         ) THEN 'not_applicable'
			         WHEN EXISTS (
			           SELECT 1 FROM surveys sv
			           WHERE sv.task_id = t.task_id AND sv.survey_status = 'active'
			             AND NOT EXISTS (
			               SELECT 1 FROM survey_responses sr
			               WHERE sr.survey_id = sv.survey_id
			                 AND sr.student_user_id = scm.student_user_id
			                 AND sr.response_status = 'submitted'
			             )
			             AND EXISTS (
			               SELECT 1 FROM survey_responses sr
			               WHERE sr.survey_id = sv.survey_id
			                 AND sr.student_user_id = scm.student_user_id
			                 AND sr.response_status = 'in_progress'
			             )
			         ) THEN 'in_progress'
			         WHEN EXISTS (
			           SELECT 1 FROM surveys sv
			           WHERE sv.task_id = t.task_id AND sv.survey_status = 'active'
			             AND NOT EXISTS (
			               SELECT 1 FROM survey_responses sr
			               WHERE sr.survey_id = sv.survey_id
			                 AND sr.student_user_id = scm.student_user_id
			                 AND sr.response_status = 'submitted'
			             )
			         ) THEN 'not_answered'
			         ELSE 'submitted'
			       END AS survey_status
			FROM student_class_memberships scm
			JOIN classrooms c ON c.classroom_id = scm.classroom_id AND c.classroom_status = 'active'
			JOIN schools s ON s.school_id = c.school_id AND s.school_status = 'active'
			JOIN task_class_assignments ca ON ca.classroom_id = c.classroom_id
			  AND ca.assignment_status = 'published'
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
			SELECT consent_status, updated_at
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
		String studentCode;
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
				studentCode = resultSet.getString("student_code");
				securityLevel = resultSet.getInt("security_level");
				mustChangePassword = resultSet.getBoolean("must_change_password");
			}
			return Optional.of(new StudentAccountDetails(
					studentCode,
					securityLevel,
					firstLoginStatus,
					mustChangePassword,
					findAffiliations(connection, studentUserId),
					findCredentialHistory(connection, studentUserId, studentCode)));
		}
	}

	public List<StudentTaskSummary> findPublishedTasks(long studentUserId) throws SQLException {
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
							resultSet.getString("survey_status"),
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
					response.map(ConsentResponse::respondedAt).orElse(null));
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
				if (hasConsentRecord(connection, studentUserId)) {
					connection.rollback();
					return ConsentSaveResult.ALREADY_RECORDED;
				}

				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO consent_records
							(user_id, consent_status, consent_document_version_id, consented_at, withdrawn_at, updated_at)
						VALUES (?, ?, ?, CASE WHEN ? = 'agreed' THEN CURRENT_TIMESTAMP ELSE NULL END, NULL, CURRENT_TIMESTAMP)
						""")) {
					statement.setLong(1, studentUserId);
					statement.setString(2, decision.getDatabaseValue());
					statement.setLong(3, submittedDocumentId);
					statement.setString(4, decision.getDatabaseValue());
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
			String studentCode) throws SQLException {
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
									? "生徒（" + studentCode + "）"
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

	private static boolean hasConsentRecord(Connection connection, long studentUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT consent_id FROM consent_records WHERE user_id = ? ORDER BY consent_id DESC LIMIT 1 FOR UPDATE")) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private record ConsentResponse(ConsentStatus status, java.time.LocalDateTime respondedAt) {
	}
}
