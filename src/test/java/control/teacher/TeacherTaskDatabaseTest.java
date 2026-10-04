package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import entity.EditorHint;
import entity.EditorTestCase;
import entity.TeacherNavigationSummary;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.Difficulty;
import entity.TeacherTaskInput.HintInput;
import entity.TeacherTaskInput.LateSubmissionPolicy;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import dao.StudentEditorDao;

class TeacherTaskDatabaseTest {
	private static final TeacherTaskControl TASKS = new TeacherTaskControl();
	private static final TeacherNavigationControl NAVIGATION = new TeacherNavigationControl();
	private long teacherId;
	private long schoolId;
	private long foreignSchoolId;
	private long classroomId;
	private long secondClassroomId;
	private long foreignClassroomId;
	private String teacherLoginId;
	private final List<Long> fixtureUserIds = new ArrayList<>();
	private boolean fixtureInstalled;

	@BeforeEach
	void installFixtureOnlyInDedicatedDatabase() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_TASK_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_teacher_task_test_[a-z0-9_]+"),
				"Teacher task integration tests require an isolated ppe_teacher_task_test_* database.");

		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertTrue(hasSuccessfulMigration(connection, "16"),
					"V16 must be applied to the isolated test database before running integration tests.");
			connection.setAutoCommit(false);
			try {
				teacherLoginId = "task-test-" + UUID.randomUUID().toString().replace("-", "");
				teacherId = insertUser(connection, teacherLoginId, "Synthetic task teacher");
				fixtureUserIds.add(teacherId);
				schoolId = insertSchool(connection, "task-test-" + UUID.randomUUID(), "Synthetic task school");
				foreignSchoolId = insertSchool(connection, "task-test-" + UUID.randomUUID(), "Unassigned task school");
				classroomId = insertClassroom(connection, schoolId);
				secondClassroomId = insertClassroom(connection, schoolId);
				foreignClassroomId = insertClassroom(connection, foreignSchoolId);
				insertPermissionFixtures(connection, teacherId, schoolId);
				connection.commit();
				fixtureInstalled = true;
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	@Test
	void savesReloadsAndUpdatesDraftWithClassAssignmentAndAudit() throws SQLException {
		AuthenticatedUser teacher = teacher();
		TeacherNavigationSummary navigation = NAVIGATION.load(teacher);
		assertTrue(navigation.taskManagementEnabled());
		assertEquals("Synthetic task school", navigation.schools().getFirst().name());

		long taskId = TASKS.createDraft(teacher, input("Initial draft"), UUID.randomUUID().toString());
		var saved = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		assertEquals("Initial draft", saved.input().title());
		assertEquals(1, saved.version());
		assertEquals("test output", saved.input().testCases().getFirst().getExpectedOutput());
		assertEquals(classroomId, saved.input().classAssignments().getFirst().classroomId());
		assertEquals(1, TASKS.loadAuditEntries(teacher, taskId).size());

		TASKS.updateDraft(teacher, taskId, saved.version(),
				inputWithAllFields("Updated draft", List.of(classroomId, secondClassroomId)),
				UUID.randomUUID().toString());
		var reloaded = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		assertEquals("Updated draft", reloaded.input().title());
		assertEquals(2, reloaded.version());
		assertEquals(List.of("updated feature one", "updated feature two"), reloaded.input().features());
		assertEquals(2, reloaded.input().testCases().size());
		assertEquals("updated output two", reloaded.input().testCases().get(1).getExpectedOutput());
		assertEquals(1, reloaded.input().hints().size());
		assertEquals("Updated hint", reloaded.input().hints().getFirst().hint().getTitle());
		assertEquals(Set.of(classroomId, secondClassroomId),
				reloaded.input().classAssignments().stream()
						.map(ClassAssignmentInput::classroomId).collect(java.util.stream.Collectors.toSet()));
		assertTrue(reloaded.input().classAssignments().stream()
				.allMatch(assignment -> assignment.lateSubmissionPolicy() == LateSubmissionPolicy.DENY));
		assertEquals(2, countUnpublishedAssignments(taskId));
		assertEquals(2, TASKS.loadAuditEntries(teacher, taskId).size());
	}

	@Test
	void rollsBackParentAndChildChangesWhenAChildDoesNotBelongToTheTask() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Before rollback"), UUID.randomUUID().toString());
		var before = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		TeacherTaskInput invalidChildUpdate = new TeacherTaskInput(
				"Uncommitted title",
				"changed theme",
				Difficulty.INTERMEDIATE,
				"changed description",
				"changed constraints",
				"changed rules",
				"print('changed')",
				List.of("changed feature"),
				List.of(new EditorTestCase(0, "replacement", "changed input", "changed output", 1)),
				List.of(new HintInput(987654321, 1,
						new EditorHint("Foreign hint", "content", "syntax", "code"))),
				List.of(new ClassAssignmentInput(
						0, secondClassroomId, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(4),
						LateSubmissionPolicy.DENY)));

		assertThrows(IllegalArgumentException.class,
				() -> TASKS.updateDraft(teacher, taskId, before.version(), invalidChildUpdate,
						UUID.randomUUID().toString()));

		var after = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		assertEquals(before.version(), after.version());
		assertEquals(before.input().title(), after.input().title());
		assertEquals(before.input().features(), after.input().features());
		assertEquals(before.input().testCases().getFirst().getExpectedOutput(),
				after.input().testCases().getFirst().getExpectedOutput());
		assertEquals(List.of(classroomId),
				after.input().classAssignments().stream().map(ClassAssignmentInput::classroomId).toList());
		assertEquals(1, countUnpublishedAssignments(taskId));
	}

	@Test
	void rollsBackAllDraftChangesWhenMySqlRejectsHintInsert() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Before database failure"), UUID.randomUUID().toString());
		var before = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		String triggerName = "ppe_teacher_task_fail_hint_" + UUID.randomUUID().toString().replace("-", "");
		createHintInsertFailureTrigger(triggerName);

		try {
			assertThrows(SQLException.class,
					() -> TASKS.updateDraft(
							teacher,
							taskId,
							before.version(),
							inputWithAllFields("Rejected database update", List.of(classroomId)),
							UUID.randomUUID().toString()));
		} finally {
			dropTrigger(triggerName);
		}

		var after = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		assertEquals(before.version(), after.version());
		assertEquals(before.input().title(), after.input().title());
		assertEquals(before.input().features(), after.input().features());
		assertEquals(before.input().testCases().getFirst().getExpectedOutput(),
				after.input().testCases().getFirst().getExpectedOutput());
		assertTrue(after.input().hints().isEmpty());
		assertEquals(1, countUnpublishedAssignments(taskId));
	}

	@Test
	void concurrentUpdatesUsingTheSameVersionAllowOnlyOneCommit() throws Exception {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Concurrent draft"), UUID.randomUUID().toString());
		long version = TASKS.loadPage(teacher, taskId, schoolId).selectedTask().version();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = executor.submit(() ->
					updateConcurrently(teacher, taskId, version, "Concurrent update A", ready, start));
			Future<String> second = executor.submit(() ->
					updateConcurrently(teacher, taskId, version, "Concurrent update B", ready, start));
			assertTrue(ready.await(10, TimeUnit.SECONDS), "Both update attempts should be ready.");
			start.countDown();

			String firstResult = first.get(20, TimeUnit.SECONDS);
			String secondResult = second.get(20, TimeUnit.SECONDS);
			assertTrue(
					("saved".equals(firstResult) && "conflict".equals(secondResult))
							|| ("conflict".equals(firstResult) && "saved".equals(secondResult)),
					"Exactly one update should save and the other should detect the stale version.");
			var finalDraft = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
			assertEquals(2, finalDraft.version());
			assertTrue(Set.of("Concurrent update A", "Concurrent update B").contains(finalDraft.input().title()));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void rejectsTeacherWithoutTaskPermissionAndAfterPermissionRevocation() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Permission boundary"), UUID.randomUUID().toString());
		long unprivilegedTeacherId;
		try (Connection connection = Client.createConnection()) {
			unprivilegedTeacherId = insertUser(connection,
					"task-test-" + UUID.randomUUID().toString().replace("-", ""), "Unprivileged teacher");
		}
		fixtureUserIds.add(unprivilegedTeacherId);
		AuthenticatedUser unprivileged = new AuthenticatedUser(
				unprivilegedTeacherId, "synthetic", "Unprivileged teacher", UserType.TEACHER, false, "test");
		assertThrows(SecurityException.class, () -> TASKS.loadPage(unprivileged, null, null));

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE teacher_feature_permissions SET is_enabled = 0
						WHERE teacher_user_id = ? AND feature_code = 'task-management'
						""")) {
			statement.setLong(1, teacherId);
			assertEquals(1, statement.executeUpdate());
		}
		assertThrows(SecurityException.class, () -> TASKS.loadPage(teacher, taskId, schoolId));
		assertThrows(SecurityException.class, () -> TASKS.loadAuditEntries(teacher, taskId));
		assertThrows(SecurityException.class,
				() -> TASKS.updateDraft(
						teacher, taskId, 1, input("Feature disabled"), UUID.randomUUID().toString()));
		assertThrows(SecurityException.class,
				() -> TASKS.createDraft(teacher, input("Feature disabled"), UUID.randomUUID().toString()));
		assertEquals("Permission boundary", findTaskTitle(taskId));
	}

	@Test
	void rejectsDraftReadUpdateAndAuditAfterSchoolPermissionIsDisabled() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Revoked school permission"), UUID.randomUUID().toString());
		var before = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE teacher_school_permissions SET access_status = 'disabled'
						WHERE teacher_user_id = ? AND school_id = ?
						""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, schoolId);
			assertEquals(1, statement.executeUpdate());
		}

		assertThrows(SecurityException.class, () -> TASKS.loadPage(teacher, taskId, schoolId));
		assertThrows(SecurityException.class, () -> TASKS.loadAuditEntries(teacher, taskId));
		assertThrows(SecurityException.class,
				() -> TASKS.updateDraft(
						teacher, taskId, before.version(), input("Must remain unchanged"), UUID.randomUUID().toString()));
		assertEquals("Revoked school permission", findTaskTitle(taskId));
	}

	@Test
	void rejectsTaskOperationsForAnInactiveTeacherAccount() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Inactive teacher"), UUID.randomUUID().toString());
		var before = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"UPDATE users SET account_status = 'suspended' WHERE user_id = ?")) {
			statement.setLong(1, teacherId);
			assertEquals(1, statement.executeUpdate());
		}

		assertThrows(SecurityException.class, () -> TASKS.loadPage(teacher, taskId, schoolId));
		assertThrows(SecurityException.class, () -> TASKS.loadAuditEntries(teacher, taskId));
		assertThrows(SecurityException.class,
				() -> TASKS.updateDraft(
						teacher, taskId, before.version(), input("Must remain unchanged"), UUID.randomUUID().toString()));
		assertEquals("Inactive teacher", findTaskTitle(taskId));
	}

	@Test
	void studentCannotLoadAnUnpublishedClassAssignment() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Private draft"), UUID.randomUUID().toString());
		long studentId = insertStudentFixture();
		long assignmentId = findAssignmentId(taskId, classroomId);

		assertTrue(new StudentEditorDao().findEditorPage(studentId, assignmentId).isEmpty());
		assertEquals(0, countStudentParticipations(studentId));
		assertEquals(1, countUnpublishedAssignments(taskId));
	}

	@Test
	void teacherWithSchoolPermissionCannotLoadAnotherTeachersDraft() throws SQLException {
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(owner, input("Owner-only draft"), UUID.randomUUID().toString());
		long otherTeacherId;
		String loginId = "task-test-" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				otherTeacherId = insertUser(connection, loginId, "Other authorized teacher");
				fixtureUserIds.add(otherTeacherId);
				insertPermissionFixtures(connection, otherTeacherId, schoolId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
		AuthenticatedUser otherTeacher = new AuthenticatedUser(
				otherTeacherId, loginId, "Other authorized teacher", UserType.TEACHER, false, "test");

		assertThrows(TaskDraftNotFoundException.class,
				() -> TASKS.loadPage(otherTeacher, taskId, schoolId));
		assertThrows(TaskDraftNotFoundException.class,
				() -> TASKS.loadAuditEntries(otherTeacher, taskId));
		assertThrows(TaskDraftNotFoundException.class,
				() -> TASKS.loadPage(owner, 987654321L, schoolId));
	}

	@Test
	void rejectsClassAssignmentOutsideTeachersAuthorizedSchools() throws SQLException {
		AuthenticatedUser teacher = teacher();

		assertThrows(SecurityException.class,
				() -> TASKS.createDraft(
						teacher,
						inputWithAllFields(
								"Partially unauthorized school assignment",
								List.of(classroomId, foreignClassroomId)),
						UUID.randomUUID().toString()));
		assertEquals(0, countTasksOwnedByTeacher());
	}

	@AfterEach
	void removeOnlySyntheticRows() throws SQLException {
		if (!fixtureInstalled) {
			return;
		}
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM audit_logs WHERE actor_user_id = ? AND feature_code = 'task-management'
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				deleteTaskChildren(connection, "task_features");
				deleteTaskChildren(connection, "task_test_cases");
				deleteTaskChildren(connection, "task_hints");
				deleteTaskChildren(connection, "task_class_assignments");
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM tasks WHERE created_by_user_id = ?")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM teacher_feature_permissions WHERE teacher_user_id = ?")) {
					for (long fixtureUserId : fixtureUserIds) {
						statement.setLong(1, fixtureUserId);
						statement.addBatch();
					}
					statement.executeBatch();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM teacher_school_permissions WHERE teacher_user_id = ?")) {
					for (long fixtureUserId : fixtureUserIds) {
						statement.setLong(1, fixtureUserId);
						statement.addBatch();
					}
					statement.executeBatch();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM student_class_memberships WHERE student_user_id = ?
						""")) {
					for (long fixtureUserId : fixtureUserIds) {
						statement.setLong(1, fixtureUserId);
						statement.addBatch();
					}
					statement.executeBatch();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM student_profiles WHERE user_id = ?")) {
					for (long fixtureUserId : fixtureUserIds) {
						statement.setLong(1, fixtureUserId);
						statement.addBatch();
					}
					statement.executeBatch();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM classrooms WHERE classroom_id IN (?, ?, ?)")) {
					statement.setLong(1, classroomId);
					statement.setLong(2, secondClassroomId);
					statement.setLong(3, foreignClassroomId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM schools WHERE school_id IN (?, ?)")) {
					statement.setLong(1, schoolId);
					statement.setLong(2, foreignSchoolId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM users WHERE user_id = ?")) {
					for (long fixtureUserId : fixtureUserIds) {
						statement.setLong(1, fixtureUserId);
						statement.addBatch();
					}
					statement.executeBatch();
				}
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private AuthenticatedUser teacher() {
		return new AuthenticatedUser(teacherId, teacherLoginId, "Synthetic task teacher",
				UserType.TEACHER, false, "teacher-task-db-test");
	}

	private TeacherTaskInput input(String title) {
		return new TeacherTaskInput(
				title,
				"synthetic theme",
				Difficulty.BEGINNER,
				"synthetic description",
				"synthetic input constraints",
				"synthetic creation rules",
				"print('synthetic')",
				List.of("synthetic feature"),
				List.of(new EditorTestCase(0, "sample", "test input", "test output", 1)),
				List.of(),
				List.of(new ClassAssignmentInput(
						0, classroomId, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
						LateSubmissionPolicy.ALLOW)));
	}

	private TeacherTaskInput inputWithAllFields(String title, List<Long> classroomIds) {
		List<ClassAssignmentInput> assignments = classroomIds.stream()
				.map(id -> new ClassAssignmentInput(
						0, id, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(4),
						LateSubmissionPolicy.DENY))
				.toList();
		return new TeacherTaskInput(
				title,
				"updated theme",
				Difficulty.INTERMEDIATE,
				"updated description",
				"updated constraints",
				"updated rules",
				"print('updated')",
				List.of("updated feature one", "updated feature two"),
				List.of(
						new EditorTestCase(0, "updated one", "updated input one", "updated output one", 1),
						new EditorTestCase(0, "updated two", "updated input two", "updated output two", 2)),
				List.of(new HintInput(0, 1,
						new EditorHint("Updated hint", "Updated hint content", "updated syntax", "updated code"))),
				assignments);
	}

	private int countUnpublishedAssignments(long taskId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT COUNT(*) FROM task_class_assignments
						WHERE task_id = ? AND assignment_status = 'not_published'
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Assignment count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private String updateConcurrently(
			AuthenticatedUser teacher,
			long taskId,
			long version,
			String title,
			CountDownLatch ready,
			CountDownLatch start) throws SQLException, InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent update did not receive its start signal.");
		}
		try {
			TASKS.updateDraft(teacher, taskId, version, input(title), UUID.randomUUID().toString());
			return "saved";
		} catch (TaskDraftConflictException expected) {
			return "conflict";
		}
	}

	private long insertStudentFixture() throws SQLException {
		String loginId = "task-student-" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				long studentId = insertUser(connection, loginId, "Synthetic task student", "student");
				fixtureUserIds.add(studentId);
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO student_profiles (
						  user_id, student_code, school_id, security_level,
						  first_login_status, must_change_password
						) VALUES (?, ?, ?, 1, 'completed', FALSE)
						""")) {
					statement.setLong(1, studentId);
					statement.setString(2, UUID.randomUUID().toString().replace("-", ""));
					statement.setLong(3, schoolId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO student_class_memberships (
						  student_user_id, classroom_id, membership_status, joined_at
						) VALUES (?, ?, 'active', CURRENT_TIMESTAMP)
						""")) {
					statement.setLong(1, studentId);
					statement.setLong(2, classroomId);
					statement.executeUpdate();
				}
				connection.commit();
				return studentId;
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private long findAssignmentId(long taskId, long targetClassroomId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT task_class_assignment_id FROM task_class_assignments
						WHERE task_id = ? AND classroom_id = ?
						""")) {
			statement.setLong(1, taskId);
			statement.setLong(2, targetClassroomId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Task class assignment fixture was not found.");
				}
				return rows.getLong(1);
			}
		}
	}

	private int countStudentParticipations(long studentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM task_participations WHERE student_user_id = ?")) {
			statement.setLong(1, studentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Student participation count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private int countTasksOwnedByTeacher() throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM tasks WHERE created_by_user_id = ?")) {
			statement.setLong(1, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Task count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private String findTaskTitle(long taskId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT title FROM tasks WHERE task_id = ? AND created_by_user_id = ?")) {
			statement.setLong(1, taskId);
			statement.setLong(2, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic task was not found.");
				}
				return rows.getString(1);
			}
		}
	}

	private void createHintInsertFailureTrigger(String triggerName) throws SQLException {
		assertTrue(triggerName.matches("ppe_teacher_task_fail_hint_[a-f0-9]+"));
		try (Connection connection = Client.createConnection();
				Statement statement = connection.createStatement()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.execute("""
					CREATE TRIGGER %s BEFORE INSERT ON task_hints
					FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Synthetic hint insert failure'
					""".formatted(triggerName));
		}
	}

	private void dropTrigger(String triggerName) throws SQLException {
		try (Connection connection = Client.createConnection();
				Statement statement = connection.createStatement()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.execute("DROP TRIGGER IF EXISTS " + triggerName);
		}
	}

	private static boolean hasSuccessfulMigration(Connection connection, String version) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT 1 FROM flyway_schema_history WHERE version = ? AND success = 1")) {
			statement.setString(1, version);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	private static long insertUser(Connection connection, String loginId, String displayName) throws SQLException {
		return insertUser(connection, loginId, displayName, "teacher");
	}

	private static long insertUser(Connection connection, String loginId, String displayName, String userType)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
				VALUES (?, ?, 'synthetic-not-a-login-credential', ?, 'active', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, userType);
			statement.setString(2, loginId);
			statement.setString(3, displayName);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Teacher fixture ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertSchool(Connection connection, String schoolCode, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO schools (school_code, name, school_status, security_level, created_at)
				VALUES (?, ?, 'active', 1, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, schoolCode);
			statement.setString(2, name);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("School fixture ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertClassroom(Connection connection, long schoolId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO classrooms (school_id, name, grade_name, classroom_status, created_at)
				VALUES (?, 'Synthetic class', '1年', 'active', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, schoolId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Classroom fixture ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static void insertPermissionFixtures(Connection connection, long teacherId, long schoolId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_school_permissions
					(teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
				VALUES (?, ?, 'enabled', ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, schoolId);
			statement.setLong(3, teacherId);
			statement.executeUpdate();
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO teacher_feature_permissions
					(teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
				VALUES (?, 'task-management', 1, ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, teacherId);
			statement.executeUpdate();
		}
	}

	private void deleteTaskChildren(Connection connection, String table) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"DELETE FROM " + table + " WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)")) {
			statement.setLong(1, teacherId);
			statement.executeUpdate();
		}
	}

	private static void rollback(Connection connection, Throwable failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
	}
}
