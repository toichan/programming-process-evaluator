package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import entity.StudentExerciseEntry;
import entity.TeacherDistributionInput;
import entity.TeacherDistributionInput.Target;
import entity.TeacherDistributionInput.TemplateItem;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class TeacherDistributionDatabaseTest {
	private final TeacherDistributionControl control = new TeacherDistributionControl();
	private AuthenticatedUser teacher;
	private long schoolId;
	private long classroomId;
	private long outsideClassroomId;
	private long sameSchoolClassroomId;
	private long crossSchoolClassroomId;
	private boolean initialized;

	@BeforeEach
	void setUp() throws SQLException {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TEACHER_DISTRIBUTION_DB_TEST")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_distribution_test_[a-z0-9_]+"));
		try (Connection connection = Client.createConnection()) {
			assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM users"));
			long teacherId = insertUser(connection, "teacher-distribution-test", "teacher");
			teacher = new AuthenticatedUser(teacherId, "teacher-distribution-test", "Synthetic",
					UserType.TEACHER, false, "distribution-test");
			try (var statement = connection.prepareStatement("""
					INSERT INTO teacher_feature_permissions
					  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
					VALUES (?, 'code-distribution', TRUE, ?, UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, teacherId);
				statement.setLong(2, teacherId);
				statement.executeUpdate();
			}
			initialized = true;
			schoolId = insert(connection, """
					INSERT INTO schools (school_code, name, school_status, security_level, created_at)
					VALUES ('distribution-test', 'Distribution Test', 'active', 1, UTC_TIMESTAMP())
					""");
			long outsideSchoolId = insert(connection, """
					INSERT INTO schools (school_code, name, school_status, security_level, created_at)
					VALUES ('distribution-outside-test', 'Outside Test', 'active', 1, UTC_TIMESTAMP())
					""");
			long crossSchoolId = insert(connection, """
					INSERT INTO schools (school_code, name, school_status, security_level, created_at)
					VALUES ('distribution-cross-school-test', 'Cross School Test', 'active', 1, UTC_TIMESTAMP())
					""");
			classroomId = insertClassroom(connection, schoolId, "Target");
			outsideClassroomId = insertClassroom(connection, outsideSchoolId, "Outside");
			sameSchoolClassroomId = insertClassroom(connection, schoolId, "Target2");
			crossSchoolClassroomId = insertClassroom(connection, crossSchoolId, "CrossSchool");
			try (var statement = connection.prepareStatement("""
					INSERT INTO teacher_school_permissions
					  (teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
					VALUES (?, ?, 'enabled', ?, UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, teacherId);
				statement.setLong(2, schoolId);
				statement.setLong(3, teacherId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO teacher_school_permissions
					  (teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
					VALUES (?, ?, 'enabled', ?, UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, teacherId);
				statement.setLong(2, crossSchoolId);
				statement.setLong(3, teacherId);
				statement.executeUpdate();
			}
			long studentId = insertUser(connection, "student-distribution-test", "student");
			try (var statement = connection.prepareStatement("""
					INSERT INTO student_profiles
					  (user_id, school_id, student_code, security_level, first_login_status, must_change_password)
					VALUES (?, ?, 'distribution-test-code', 1, 'completed', FALSE)
					""")) {
				statement.setLong(1, studentId);
				statement.setLong(2, schoolId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO student_class_memberships
					  (student_user_id, classroom_id, membership_status, joined_at)
					VALUES (?, ?, 'active', UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, studentId);
				statement.setLong(2, classroomId);
				statement.executeUpdate();
			}
		}
	}

	@Test
	void savesDistributesAppendsIdempotentlyAndResumesScheduledTargets() throws Exception {
		var items = List.of(
				new TemplateItem("week1", StudentExerciseEntry.Type.FOLDER, null),
				new TemplateItem("week1/solution.py", StudentExerciseEntry.Type.FILE, "print('initial')"));
		var draftInput = input(0, 0, items, List.of(), UUID.randomUUID().toString());
		control.saveDraft(teacher, draftInput);
		var template = control.loadPage(teacher).templates().getFirst();
		assertEquals(items, control.loadTemplate(teacher, template.templateId()).items());
		var draftHistory = control.loadPage(teacher).distributions().stream()
				.filter(distribution -> distribution.status().equals("draft")).findFirst().orElseThrow().history();
		assertTrue(draftHistory.stream().anyMatch(entry -> entry.actorId() == teacher.userId()
				&& entry.action().equals("テンプレート作成")));
		String createDetail = historyDetail("テンプレート作成");
		assertTrue(createDetail.contains("week1/solution.py"));
		assertTrue(createDetail.contains("print('initial')"));

		String firstToken = UUID.randomUUID().toString();
		var first = control.schedule(teacher,
				input(template.templateId(), template.version(), items,
						List.of(new Target(classroomId, null)), firstToken));
		assertEquals(1, first.expectedImmediateTargets());
		assertEquals(1, first.completedImmediateTargets());
		assertFalse(first.processingUnavailable());
		assertEquals("completed", distributionStatus(first.distributionId()));
		assertEquals("print('initial')", entryContent("Lesson/week1/solution.py"));

		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement("""
				UPDATE student_exercise_entries
				SET current_content = 'student-edited'
				WHERE path = 'Lesson/week1/solution.py'
				""")) {
			assertEquals(1, statement.executeUpdate());
		}
		int secondVersion = templateVersion(template.templateId());
		String secondToken = UUID.randomUUID().toString();
		var second = control.schedule(teacher,
				input(template.templateId(), secondVersion, items,
						List.of(new Target(classroomId, null)), secondToken));
		assertEquals(1, second.completedImmediateTargets());
		assertEquals("student-edited", entryContent("Lesson/week1/solution.py"));
		assertEquals("print('initial')", entryContent("Lesson（再配信）/week1/solution.py"));

		var retry = control.schedule(teacher,
				input(template.templateId(), secondVersion, items,
						List.of(new Target(classroomId, null)), secondToken));
		assertEquals(second.distributionId(), retry.distributionId());
		assertEquals(0, retry.expectedImmediateTargets());
		assertEquals(2, scalar("SELECT COUNT(*) FROM student_exercises"));
		assertThrows(SecurityException.class, () -> control.schedule(teacher,
				input(0, 0, items, List.of(new Target(outsideClassroomId, null)),
						UUID.randomUUID().toString())));
		assertThrows(IllegalArgumentException.class, () -> control.schedule(teacher,
				input(0, 0, items, List.of(
						new Target(classroomId, LocalDateTime.now().plusMinutes(5)),
						new Target(crossSchoolClassroomId, LocalDateTime.now().plusMinutes(5))),
						UUID.randomUUID().toString())));
		var sameSchoolSchedule = control.schedule(teacher,
				input(0, 0, items, List.of(
						new Target(classroomId, LocalDateTime.now().plusMinutes(5)),
						new Target(sameSchoolClassroomId, LocalDateTime.now().plusMinutes(5))),
						UUID.randomUUID().toString()));
		assertEquals(2, scalar("SELECT COUNT(*) FROM distribution_targets WHERE distribution_id = "
				+ sameSchoolSchedule.distributionId()));

		int thirdVersion = templateVersion(template.templateId());
		var scheduled = control.schedule(teacher,
				input(template.templateId(), thirdVersion, items,
						List.of(new Target(classroomId, LocalDateTime.now().plusMinutes(5))),
						UUID.randomUUID().toString()));
		assertEquals(0, scheduled.expectedImmediateTargets());
		long targetId = scalar("SELECT distribution_target_id FROM distribution_targets WHERE distribution_id = "
				+ scheduled.distributionId());
		LocalDateTime rescheduledAt = LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0);
		assertThrows(IllegalArgumentException.class,
				() -> control.rescheduleTarget(teacher, targetId, LocalDateTime.now().minusMinutes(1)));
		control.rescheduleTarget(teacher, targetId, rescheduledAt);
		assertEquals(rescheduledAt, targetScheduledAt(targetId));
		assertTrue(historyDetail("配信日時変更").contains(rescheduledAt.toString()));
		control.stopTarget(teacher, targetId);
		assertEquals("stopped", targetStatus(targetId));
		control.resumeTarget(teacher, targetId);
		assertEquals("scheduled", targetStatus(targetId));
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement("""
				UPDATE distribution_targets
				SET scheduled_at = UTC_TIMESTAMP() - INTERVAL 1 MINUTE
				WHERE distribution_target_id = ?
				""")) {
			statement.setLong(1, targetId);
			statement.executeUpdate();
		}
		control.processDueTargets();
		assertEquals("distributed", targetStatus(targetId));
		assertEquals("print('initial')", entryContent("Lesson（再配信2）/week1/solution.py"));
		assertEquals(3, scalar("SELECT COUNT(*) FROM student_exercises"));

		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement("""
				UPDATE teacher_school_permissions SET access_status = 'disabled'
				WHERE teacher_user_id = ? AND school_id = ?
				""")) {
			statement.setLong(1, teacher.userId());
			statement.setLong(2, schoolId);
			statement.executeUpdate();
		}
		assertTrue(control.loadPage(teacher).distributions().stream()
				.noneMatch(distribution -> !distribution.targets().isEmpty()));
	}

	@AfterEach
	void cleanUp() throws SQLException {
		if (!initialized) return;
		try (Connection connection = Client.createConnection(); var statement = connection.createStatement()) {
			for (String sql : List.of(
					"DELETE FROM student_exercise_entries WHERE entry_type = 'file'",
					"DELETE FROM student_exercise_entries WHERE parent_entry_id IS NOT NULL",
					"DELETE FROM student_exercise_entries WHERE parent_entry_id IS NULL",
					"DELETE FROM student_exercises",
					"DELETE FROM distribution_histories",
					"DELETE FROM distribution_snapshot_files",
					"DELETE FROM distribution_targets",
					"DELETE FROM distributions",
					"DELETE FROM distribution_template_files",
					"DELETE FROM distribution_templates",
					"DELETE FROM student_class_memberships",
					"DELETE FROM student_profiles",
					"DELETE FROM teacher_feature_permissions",
					"DELETE FROM teacher_school_permissions",
					"DELETE FROM users",
					"DELETE FROM classrooms",
					"DELETE FROM schools")) {
				statement.executeUpdate(sql);
			}
		}
	}

	private static TeacherDistributionInput input(long templateId, int version,
			List<TemplateItem> items, List<Target> targets, String token) {
		return new TeacherDistributionInput(templateId, version, "Template", "Lesson", items, targets, token);
	}

	private int templateVersion(long templateId) throws SQLException {
		return control.loadPage(teacher).templates().stream()
				.filter(template -> template.templateId() == templateId)
				.findFirst()
				.orElseThrow()
				.version();
	}

	private static long insertUser(Connection connection, String loginId, String type) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO users (user_type, login_id, password_hash, display_name, account_status, created_at)
				VALUES (?, ?, ?, 'Synthetic', 'active', UTC_TIMESTAMP())
				""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, type);
			statement.setString(2, loginId);
			statement.setString(3, new PasswordHasher().hash("Synthetic12!Aa".toCharArray()));
			statement.executeUpdate();
			try (var keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long insertClassroom(Connection connection, long schoolId, String name) throws SQLException {
		return insert(connection, """
				INSERT INTO classrooms (school_id, name, classroom_status, created_at)
				VALUES (%d, '%s', 'active', UTC_TIMESTAMP())
				""".formatted(schoolId, name));
	}

	private static long insert(Connection connection, String sql) throws SQLException {
		try (var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
			statement.executeUpdate();
			try (var keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static long scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	private static long scalar(String sql) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			return scalar(connection, sql);
		}
	}

	private static String distributionStatus(long distributionId) throws SQLException {
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement(
				"SELECT distribution_status FROM distributions WHERE distribution_id = ?")) {
			statement.setLong(1, distributionId);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getString(1);
			}
		}
	}

	private static String targetStatus(long targetId) throws SQLException {
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement(
				"SELECT target_status FROM distribution_targets WHERE distribution_target_id = ?")) {
			statement.setLong(1, targetId);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getString(1);
			}
		}
	}

	private static LocalDateTime targetScheduledAt(long targetId) throws SQLException {
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement(
				"SELECT scheduled_at FROM distribution_targets WHERE distribution_target_id = ?")) {
			statement.setLong(1, targetId);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getObject(1, LocalDateTime.class);
			}
		}
	}

	private static String entryContent(String path) throws SQLException {
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement(
				"SELECT current_content FROM student_exercise_entries WHERE path = ?")) {
			statement.setString(1, path);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getString(1);
			}
		}
	}

	private static String historyDetail(String action) throws SQLException {
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement(
				"SELECT change_detail FROM distribution_histories WHERE action_type = ?")) {
			statement.setString(1, action);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getString(1);
			}
		}
	}
}
