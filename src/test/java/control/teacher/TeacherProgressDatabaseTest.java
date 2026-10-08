package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import entity.TeacherProgressRow;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class TeacherProgressDatabaseTest {
	private final TeacherProgressControl progress = new TeacherProgressControl();
	private long teacherId;
	private long studentId;
	private long schoolId;
	private long taskId;
	private long assignmentId;
	private long participationId;
	private AuthenticatedUser teacher;
	private boolean fixtureInstalled;

	@BeforeEach
	void setUp() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TEACHER_PROGRESS_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_teacher_progress_test_[a-z0-9_]+"),
				"Progress integration tests require an isolated ppe_teacher_progress_test_* database.");
		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"),
					"The isolated progress test database must be empty.");
			connection.setAutoCommit(false);
			try {
				teacherId = insertUser(connection, "teacher");
				teacher = new AuthenticatedUser(teacherId, "teacher", "Synthetic teacher",
						UserType.TEACHER, false, "progress-test");
				schoolId = insertSchool(connection);
				long classroomId = insertClassroom(connection, schoolId);
				insertTeacherPermissions(connection, schoolId);
				studentId = insertUser(connection, "student");
				insertStudentProfile(connection, studentId, schoolId);
				insertMembership(connection, classroomId, studentId);
				taskId = insertPublishedTask(connection, schoolId, teacherId);
				assignmentId = insertAssignment(connection, taskId, classroomId);
				participationId = insertParticipation(connection, studentId, assignmentId);
				insertActivity(connection, participationId);
				connection.commit();
				fixtureInstalled = true;
			} catch (SQLException | RuntimeException | Error failure) {
				connection.rollback();
				throw failure;
			}
		}
	}

	@Test
	void readsDatabaseProgressAndActivityThenEnforcesPermissionRevocation() throws SQLException {
		List<TeacherProgressRow> rows = progress.loadRows(teacher);
		assertEquals(1, rows.size());
		TeacherProgressRow row = rows.getFirst();
		assertEquals("student", row.studentLoginId());
		assertEquals("Synthetic Progress Task", row.taskName());
		assertEquals("提出済み", row.status());
		assertEquals("未確認", row.consent());
		assertEquals(3723, row.activeDurationSeconds());
		assertEquals("01:02:03", row.elapsedTime());

		var detail = progress.loadDetail(teacher, assignmentId, studentId);
		assertEquals(row, detail.row());
		assertEquals(1, detail.activities().size());
		assertEquals("手動保存", detail.activities().getFirst().type());
		assertEquals("完了", detail.activities().getFirst().result());

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions SET is_enabled = FALSE
						WHERE teacher_user_id = ? AND feature_code = 'task-progress'
						""")) {
			statement.setLong(1, teacherId);
			assertEquals(1, statement.executeUpdate());
		}
		assertThrows(SecurityException.class, () -> progress.loadRows(teacher));
		assertThrows(SecurityException.class, () -> progress.loadDetail(teacher, assignmentId, studentId));
	}

	@Test
	void readsSavedDraftOrLatestSubmissionIncludingResubmissionAndEmptyCode() throws SQLException {
		assertNull(progress.loadDetail(teacher, assignmentId, studentId).latestCode());
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_participations
						SET current_draft_code = ?, draft_updated_at = CURRENT_TIMESTAMP(6),
						    learning_status = ?, progress_status = ?
						WHERE participation_id = ?
						""")) {
			statement.setString(1, "print('保存済み')\n");
			statement.setString(2, "in_progress");
			statement.setString(3, "in_progress");
			statement.setLong(4, participationId);
			statement.executeUpdate();
			var code = progress.loadDetail(teacher, assignmentId, studentId).latestCode();
			assertEquals("print('保存済み')\n", code.code());
			assertEquals("draft", code.source());
			assertTrue(!code.updatedAt().isEmpty());

			statement.setString(2, "completed");
			statement.setString(3, "submitted");
			statement.executeUpdate();
			assertNull(progress.loadDetail(teacher, assignmentId, studentId).latestCode(),
					"A retained draft must not masquerade as a submission.");
			try (PreparedStatement submission = connection.prepareStatement("""
					INSERT INTO submissions (participation_id, revision_number, submitted_code,
					  submission_status, submitted_at, created_at)
					VALUES (?, ?, ?, 'submitted', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
					""")) {
				submission.setLong(1, participationId);
				submission.setInt(2, 1);
				submission.setString(3, "print('旧提出版')");
				submission.executeUpdate();
				submission.setInt(2, 2);
				submission.setString(3, "print('最新提出版')");
				submission.executeUpdate();
			}
			var submitted = progress.loadDetail(teacher, assignmentId, studentId).latestCode();
			assertEquals("print('最新提出版')", submitted.code());
			assertEquals("submission", submitted.source());

			statement.setString(1, "");
			statement.setString(2, "in_progress");
			statement.setString(3, "in_progress");
			statement.executeUpdate();
			assertEquals("", progress.loadDetail(teacher, assignmentId, studentId).latestCode().code(),
					"An intentionally saved empty draft is still a saved draft.");
			assertEquals("draft", progress.loadDetail(teacher, assignmentId, studentId).latestCode().source());

			statement.setString(1, "<script>alert('x')</script>");
			statement.setString(2, "needs_revision");
			statement.setString(3, "needs_action");
			statement.executeUpdate();
			assertEquals("<script>alert('x')</script>",
					progress.loadDetail(teacher, assignmentId, studentId).latestCode().code());
		}
	}

	@Test
	void filtersInactiveSchoolAccessAndRejectsAnUnlistedDetail() throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE teacher_school_permissions SET access_status = 'disabled'
						WHERE teacher_user_id = ? AND school_id = ?
						""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, schoolId);
			assertEquals(1, statement.executeUpdate());
		}
		assertTrue(progress.loadRows(teacher).isEmpty());
		assertThrows(ProgressRecordNotFoundException.class,
				() -> progress.loadDetail(teacher, assignmentId, studentId));
	}

	@Test
	void rejectsInvalidRolesAndIdentifiersBeforeReturningProgress() {
		AuthenticatedUser student = new AuthenticatedUser(
				studentId, "student", "Synthetic student", UserType.STUDENT, false, "progress-test");
		assertThrows(SecurityException.class, () -> progress.loadRows(student));
		assertThrows(IllegalArgumentException.class, () -> progress.loadDetail(teacher, 0, studentId));
		assertThrows(IllegalArgumentException.class, () -> progress.loadDetail(teacher, assignmentId, 0));
	}

	@AfterEach
	void cleanUp() throws SQLException {
		if (!fixtureInstalled) return;
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				for (String sql : List.of(
						"DELETE FROM code_logs WHERE participation_id = " + participationId,
						"DELETE FROM task_activity_sessions WHERE participation_id = " + participationId,
						"DELETE FROM submissions WHERE participation_id = " + participationId,
						"DELETE FROM task_participations WHERE participation_id = " + participationId,
						"DELETE FROM task_class_assignments WHERE task_class_assignment_id = " + assignmentId,
						"DELETE FROM tasks WHERE task_id = " + taskId,
						"DELETE FROM student_class_memberships WHERE student_user_id = " + studentId,
						"DELETE FROM student_profiles WHERE user_id = " + studentId,
						"DELETE FROM teacher_school_permissions WHERE teacher_user_id = " + teacherId,
						"DELETE FROM teacher_feature_permissions WHERE teacher_user_id = " + teacherId,
						"DELETE FROM users WHERE user_id IN (" + teacherId + ", " + studentId + ")",
						"DELETE FROM classrooms WHERE school_id = " + schoolId,
						"DELETE FROM schools WHERE school_id = " + schoolId)) {
					try (Statement statement = connection.createStatement()) {
						statement.executeUpdate(sql);
					}
				}
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				connection.rollback();
				throw failure;
			}
		}
	}

	private static long insertUser(Connection connection, String type) throws SQLException {
		String loginId = type.equals("teacher") ? "teacher" : "student";
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
				VALUES (?, ?, ?, ?, 'active', UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, type);
			statement.setString(2, loginId);
			statement.setString(3, new PasswordHasher().hash("Synthetic12!Aa".toCharArray()));
			statement.setString(4, "Synthetic " + type);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long insertSchool(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO schools (school_code, name, school_status, security_level, created_at)
				VALUES (?, 'Synthetic Progress School', 'active', 1, UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, "progress-" + UUID.randomUUID());
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long insertClassroom(Connection connection, long schoolId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO classrooms (school_id, name, classroom_status, created_at)
				VALUES (?, 'Progress Class', 'active', UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, schoolId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private void insertTeacherPermissions(Connection connection, long permittedSchoolId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_feature_permissions
				  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
				VALUES (?, 'task-progress', TRUE, ?, UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, teacherId);
			assertEquals(1, statement.executeUpdate());
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_school_permissions
				  (teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
				VALUES (?, ?, 'enabled', ?, UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, permittedSchoolId);
			statement.setLong(3, teacherId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private static void insertStudentProfile(Connection connection, long userId, long schoolId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_profiles
				  (user_id, school_id, student_code, security_level, first_login_status, must_change_password)
				VALUES (?, ?, ?, 1, 'completed', FALSE)
				""")) {
			statement.setLong(1, userId);
			statement.setLong(2, schoolId);
			statement.setString(3, "pg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
			assertEquals(1, statement.executeUpdate());
		}
	}

	private static void insertMembership(Connection connection, long classroomId, long studentId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_class_memberships
				  (student_user_id, classroom_id, membership_status, joined_at)
				VALUES (?, ?, 'active', UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, studentId);
			statement.setLong(2, classroomId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private static long insertPublishedTask(Connection connection, long schoolId, long teacherId)
			throws SQLException {
		String code = "PROGRESS-" + UUID.randomUUID();
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO tasks
				  (school_id, task_code, task_revision_code, revision_number, created_by_user_id,
				   title, difficulty, language, description, save_status, publication_status,
				   created_at, published_at)
				VALUES (?, ?, ?, 1, ?, 'Synthetic Progress Task', 'beginner', 'Python 3.x',
				        'Integration test task', 'saved', 'published', UTC_TIMESTAMP(), UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, schoolId);
			statement.setString(2, code);
			statement.setString(3, code + "-v1");
			statement.setLong(4, teacherId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long insertAssignment(Connection connection, long taskId, long classroomId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_class_assignments
				  (task_id, classroom_id, assignment_status, publish_at, late_submission_policy,
				   resubmission_policy, created_at)
				VALUES (?, ?, 'published', UTC_TIMESTAMP(), 'allow', 'allow', UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, taskId);
			statement.setLong(2, classroomId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long insertParticipation(Connection connection, long studentId, long assignmentId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_participations
				  (student_user_id, task_class_assignment_id, learning_status, progress_status,
				   save_status, evaluation_status, active_duration_seconds, last_activity_at)
				VALUES (?, ?, 'completed', 'submitted', 'saved', 'not_started', 3723, UTC_TIMESTAMP())
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, studentId);
			statement.setLong(2, assignmentId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static void insertActivity(Connection connection, long participationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO code_logs
				  (participation_id, event_type, snapshot_text, observed_at, created_at)
				VALUES (?, 'manual_save', 'print(1)', UTC_TIMESTAMP(), UTC_TIMESTAMP())
				""")) {
			statement.setLong(1, participationId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private static long scalar(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet result = statement.executeQuery()) {
			assertTrue(result.next());
			return result.getLong(1);
		}
	}
}
