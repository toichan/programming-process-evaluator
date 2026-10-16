package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import control.auth.PasswordHasher;
import dao.StudentNotificationDao;
import lib.mysql.Client;

class Phase20BrowserFixtureTest {
	private static final String STUDENT_LOGIN = "phase20_expired_deny";

	@Test
	void createsSyntheticTeacherReviewAndExpiredDenyBrowserFixture() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("PPE_P20_BROWSER_FIXTURE")));
		TeacherReviewFixture.guard(Map.copyOf(System.getenv()));

		TeacherReviewFixture.Data review;
		try (Connection connection = Client.createConnection()) {
			TeacherSurveyDatabaseTest.seed(connection);
			review = TeacherReviewFixture.installOrFind(connection);
			connection.setAutoCommit(false);
			try {
				enableFeature(connection, review.teacher(), review.teacher(), "task-progress");
				long student = ensureStudent(connection, review);
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET assignment_status = 'expired', due_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY),
						    late_submission_policy = 'deny'
						WHERE task_class_assignment_id = ?
						""")) {
					statement.setLong(1, review.assignment());
					assertEquals(1, statement.executeUpdate());
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM student_notifications
						WHERE student_user_id = ? AND task_class_assignment_id = ?
						  AND notification_type = 'assignment_expired'
						""")) {
					statement.setLong(1, student);
					statement.setLong(2, review.assignment());
					statement.executeUpdate();
				}
				StudentNotificationDao.insertAssignmentExpired(connection, review.assignment());
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				connection.rollback();
				throw failure;
			} finally {
				connection.setAutoCommit(true);
			}
		}

		Path fixtureFile = Path.of("build", "phase20-browser-fixture.properties");
		Files.createDirectories(fixtureFile.getParent());
		Files.writeString(fixtureFile, "assignmentId=" + review.assignment() + System.lineSeparator());
	}

	private static long ensureStudent(Connection connection, TeacherReviewFixture.Data review) throws SQLException {
		long studentId;
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT user_id FROM users WHERE login_id = ?")) {
			statement.setString(1, STUDENT_LOGIN);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					studentId = rows.getLong(1);
				} else {
					String passwordHash = new PasswordHasher().hash(TeacherReviewFixture.PASSWORD.toCharArray());
					studentId = TeacherReviewFixture.insert(connection, """
							INSERT INTO users (login_id, password_hash, display_name, user_type, account_status, created_at)
							VALUES (?, ?, 'Synthetic expired-task student', 'student', 'active', CURRENT_TIMESTAMP)
							""", STUDENT_LOGIN, passwordHash);
				}
			}
		}

		if (count(connection, "SELECT COUNT(*) FROM student_profiles WHERE user_id = ?", studentId) == 0) {
			TeacherReviewFixture.insert(connection, """
					INSERT INTO student_profiles
					  (user_id, school_id, student_code, security_level, first_login_status, must_change_password)
					VALUES (?, ?, 'phase20-expired-deny', 1, 'completed', FALSE)
					""", studentId, review.school());
		}
		if (count(connection, """
				SELECT COUNT(*) FROM student_class_memberships
				WHERE student_user_id = ? AND classroom_id = ? AND membership_status = 'active'
				""", studentId, review.classroom()) == 0) {
			TeacherReviewFixture.insert(connection, """
					INSERT INTO student_class_memberships
					  (student_user_id, classroom_id, membership_status, joined_at)
					VALUES (?, ?, 'active', CURRENT_TIMESTAMP)
					""", studentId, review.classroom());
		}
		if (count(connection, "SELECT COUNT(*) FROM consent_records WHERE user_id = ?", studentId) == 0) {
			long consentDocument = scalar(connection, """
					SELECT consent_document_version_id FROM consent_document_versions
					WHERE document_status = 'active'
					ORDER BY effective_at DESC, consent_document_version_id DESC
					LIMIT 1
					""");
			TeacherReviewFixture.insert(connection, """
					INSERT INTO consent_records
					  (user_id, consent_document_version_id, consent_status, consented_at, updated_at)
					VALUES (?, ?, 'agreed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
					""", studentId, consentDocument);
		}
		return studentId;
	}

	private static void enableFeature(
			Connection connection, long teacherId, long updatedBy, String featureCode) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE teacher_feature_permissions
				SET is_enabled = TRUE, updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP
				WHERE teacher_user_id = ? AND feature_code = ?
				""")) {
			statement.setLong(1, updatedBy);
			statement.setLong(2, teacherId);
			statement.setString(3, featureCode);
			if (statement.executeUpdate() != 0) {
				return;
			}
		}
		TeacherReviewFixture.insert(connection, """
				INSERT INTO teacher_feature_permissions
				  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
				VALUES (?, ?, TRUE, ?, CURRENT_TIMESTAMP)
				""", teacherId, featureCode, updatedBy);
	}

	private static long count(Connection connection, String sql, long... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < parameters.length; index++) {
				statement.setLong(index + 1, parameters[index]);
			}
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				return rows.getLong(1);
			}
		}
	}

	private static long scalar(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet rows = statement.executeQuery()) {
			if (!rows.next()) {
				throw new SQLException("Required synthetic fixture row is missing.");
			}
			return rows.getLong(1);
		}
	}
}
