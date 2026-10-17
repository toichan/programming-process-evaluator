package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.auth.PasswordHasher;
import control.student.StudentNotificationControl;
import control.student.StudentNotificationControl.OpenStatus;
import entity.StudentExerciseEntry;
import entity.TeacherDistributionInput;
import entity.TeacherDistributionInput.Target;
import entity.TeacherDistributionInput.TemplateItem;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import lib.time.DateTimeContract;

class TeacherDistributionDatabaseTest {
	private final TeacherDistributionControl control = new TeacherDistributionControl();
	private AuthenticatedUser teacher;
	private AuthenticatedUser peerTeacher;
	private AuthenticatedUser foreignTeacher;
	private long schoolId;
	private long classroomId;
	private long outsideClassroomId;
	private long sameSchoolClassroomId;
	private long crossSchoolClassroomId;
	private long studentUserId;
	private AuthenticatedUser student;
	private boolean initialized;
	private boolean retainBrowserFixture;

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
			long peerTeacherId = insertUser(connection, "teacher-distribution-peer", "teacher");
			peerTeacher = new AuthenticatedUser(peerTeacherId, "teacher-distribution-peer", "Synthetic",
					UserType.TEACHER, false, "distribution-test");
			try (var statement = connection.prepareStatement("""
					INSERT INTO teacher_feature_permissions
					  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
					VALUES (?, 'code-distribution', TRUE, ?, UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, peerTeacherId);
				statement.setLong(2, peerTeacherId);
				statement.executeUpdate();
			}
			long foreignTeacherId = insertUser(connection, "teacher-distribution-foreign", "teacher");
			foreignTeacher = new AuthenticatedUser(foreignTeacherId, "teacher-distribution-foreign", "Synthetic",
					UserType.TEACHER, false, "distribution-test");
			try (var statement = connection.prepareStatement("""
					INSERT INTO teacher_feature_permissions
					  (teacher_user_id, feature_code, is_enabled, updated_by_user_id, updated_at)
					VALUES (?, 'code-distribution', TRUE, ?, UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, foreignTeacherId);
				statement.setLong(2, foreignTeacherId);
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
				statement.setLong(1, peerTeacherId);
				statement.setLong(2, schoolId);
				statement.setLong(3, peerTeacherId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO teacher_school_permissions
					  (teacher_user_id, school_id, access_status, updated_by_user_id, updated_at)
					VALUES (?, ?, 'enabled', ?, UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, foreignTeacherId);
				statement.setLong(2, crossSchoolId);
				statement.setLong(3, foreignTeacherId);
				statement.executeUpdate();
			}
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
			studentUserId = insertUser(connection, "student-distribution-test", "student");
			student = new AuthenticatedUser(studentUserId, "student-distribution-test", "Synthetic",
					UserType.STUDENT, false, "distribution-test");
			try (var statement = connection.prepareStatement("""
					INSERT INTO student_profiles
					  (user_id, school_id, student_code, security_level, first_login_status, must_change_password)
					VALUES (?, ?, 'distribution-test-code', 1, 'completed', FALSE)
					""")) {
				statement.setLong(1, studentUserId);
				statement.setLong(2, schoolId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO student_class_memberships
					  (student_user_id, classroom_id, membership_status, joined_at)
					VALUES (?, ?, 'active', UTC_TIMESTAMP())
					""")) {
				statement.setLong(1, studentUserId);
				statement.setLong(2, classroomId);
				statement.executeUpdate();
			}
		}
	}

	@Test
	void sharesOnlySchoolScopedTemplatesAndPreservesEditorAndExecutorAttribution() throws Exception {
		var items = List.of(new TemplateItem("week1/main.py", StudentExerciseEntry.Type.FILE, "print('shared')"));
		control.saveDraft(teacher, input(0, 0, items, List.of(), UUID.randomUUID().toString()));
		var unscoped = control.loadPage(teacher).templates().getFirst();
		assertTrue(control.loadPage(peerTeacher).templates().isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> control.loadTemplate(peerTeacher, unscoped.templateId()));
		assertTrue(control.loadPage(foreignTeacher).templates().isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> control.loadTemplate(foreignTeacher, unscoped.templateId()));

		var firstRun = control.schedule(teacher, input(unscoped.templateId(), unscoped.version(), items,
				List.of(new Target(classroomId, null)), UUID.randomUUID().toString()));
		var sharedPage = control.loadPage(peerTeacher);
		assertTrue(sharedPage.templates().stream().anyMatch(item -> item.templateId() == unscoped.templateId()));
		assertEquals(List.of(
				new TemplateItem("week1", StudentExerciseEntry.Type.FOLDER, null),
				items.getFirst()), control.loadTemplate(peerTeacher, unscoped.templateId()).items());
		assertTrue(control.loadPage(foreignTeacher).templates().isEmpty());
		assertThrows(SecurityException.class,
				() -> control.loadTemplate(foreignTeacher, unscoped.templateId()));
		assertTrue(sharedPage.distributions().stream().flatMap(distribution -> distribution.history().stream())
				.anyMatch(entry -> entry.actorId() == teacher.userId() && entry.action().equals("配信設定")));
		setDistributionFeature(peerTeacher.userId(), false);
		assertThrows(SecurityException.class, () -> control.loadPage(peerTeacher));
		assertThrows(SecurityException.class, () -> control.loadTemplate(peerTeacher, unscoped.templateId()));
		setDistributionFeature(peerTeacher.userId(), true);

		var current = sharedPage.templates().stream()
				.filter(item -> item.templateId() == unscoped.templateId()).findFirst().orElseThrow();
		control.saveDraft(peerTeacher, new TeacherDistributionInput(current.templateId(), current.version(),
				"Shared update", "Lesson", items, List.of(), UUID.randomUUID().toString()));
		assertEquals(teacher.userId(), scalar("SELECT created_by_user_id FROM distribution_templates WHERE distribution_template_id="
				+ unscoped.templateId()));
		assertEquals(peerTeacher.userId(), scalar("SELECT updated_by_user_id FROM distribution_templates WHERE distribution_template_id="
				+ unscoped.templateId()));
		assertTrue(control.loadPage(peerTeacher).distributions().stream()
				.flatMap(distribution -> distribution.history().stream())
				.anyMatch(entry -> entry.actorId() == peerTeacher.userId() && entry.action().equals("テンプレート編集")));

		var afterEdit = control.loadPage(peerTeacher).templates().stream()
				.filter(item -> item.templateId() == unscoped.templateId()).findFirst().orElseThrow();
		var peerRun = control.schedule(peerTeacher, input(afterEdit.templateId(), afterEdit.version(), items,
				List.of(new Target(sameSchoolClassroomId, null)), UUID.randomUUID().toString()));
		var peerRunRow = control.loadPage(peerTeacher).distributions().stream()
				.filter(distribution -> distribution.distributionId() == peerRun.distributionId())
				.findFirst().orElseThrow();
		assertEquals(peerTeacher.loginId(), peerRunRow.executedBy());
		assertTrue(peerRunRow.history().stream().anyMatch(entry -> entry.actorId() == peerTeacher.userId()));

		var latest = control.loadPage(teacher).templates().stream()
				.filter(item -> item.templateId() == unscoped.templateId()).findFirst().orElseThrow();
		var crossSchoolRun = control.schedule(teacher, input(latest.templateId(), latest.version(), items,
				List.of(new Target(crossSchoolClassroomId, null)), UUID.randomUUID().toString()));
		assertTrue(control.loadPage(peerTeacher).templates().stream()
				.noneMatch(item -> item.templateId() == unscoped.templateId()));
		var peerDistributions = control.loadPage(peerTeacher).distributions();
		assertTrue(peerDistributions.stream()
				.noneMatch(distribution -> distribution.distributionId() == crossSchoolRun.distributionId()));
		assertTrue(peerDistributions.stream()
				.anyMatch(distribution -> distribution.distributionId() == peerRun.distributionId()));
		assertTrue(control.loadPage(foreignTeacher).templates().stream()
				.noneMatch(item -> item.templateId() == unscoped.templateId()));
		assertTrue(control.loadPage(foreignTeacher).distributions().stream()
				.noneMatch(distribution -> distribution.templateId() == unscoped.templateId()));
		assertThrows(IllegalArgumentException.class,
				() -> control.loadTemplate(peerTeacher, unscoped.templateId()));
		assertThrows(IllegalArgumentException.class, () -> control.stop(peerTeacher, firstRun.distributionId()));
	}

	@Test
	void createsRetainedPeerTemplateForPhase20BrowserAcceptance() throws Exception {
		Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TEACHER_DISTRIBUTION_BROWSER_FIXTURE")));
		var items = List.of(new TemplateItem(
				"week1/main.py", StudentExerciseEntry.Type.FILE, "print('original peer template')"));
		var draft = new TeacherDistributionInput(0, 0, "P20 Peer Shared Template", "Shared Lesson",
				items, List.of(), UUID.randomUUID().toString());
		control.saveDraft(teacher, draft);
		var template = control.loadPage(teacher).templates().stream()
				.filter(item -> item.name().equals(draft.name())).findFirst().orElseThrow();

		var distribution = control.schedule(teacher, new TeacherDistributionInput(template.templateId(),
				template.version(), draft.name(), draft.rootName(), items,
				List.of(new Target(classroomId, null)), UUID.randomUUID().toString()));
		assertEquals(1, distribution.expectedImmediateTargets());
		assertEquals(1, distribution.completedImmediateTargets());
		assertTrue(control.loadPage(peerTeacher).templates().stream()
				.anyMatch(item -> item.templateId() == template.templateId()));
		assertTrue(control.loadPage(foreignTeacher).templates().stream()
				.noneMatch(item -> item.templateId() == template.templateId()));

		int currentTemplateVersion = templateVersion(template.templateId());
		LocalDateTime scheduledAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(2)
				.withSecond(0).withNano(0);
		var scheduledDistribution = control.schedule(teacher, new TeacherDistributionInput(template.templateId(),
				currentTemplateVersion, draft.name(), draft.rootName(), items,
				List.of(new Target(sameSchoolClassroomId, scheduledAt)), UUID.randomUUID().toString()));
		assertEquals(0, scheduledDistribution.expectedImmediateTargets());
		long targetId = scalar("SELECT distribution_target_id FROM distribution_targets WHERE distribution_id = "
				+ scheduledDistribution.distributionId());
		assertEquals(scheduledAt, targetScheduledAt(targetId));

		retainBrowserFixture = true;
		Path fixture = Path.of("build", "phase20-distribution-fixture.properties");
		Files.createDirectories(fixture.getParent());
		String scheduledAtDisplay = DateTimeContract.toUserDisplay(scheduledAt)
				.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
		Files.writeString(fixture, "templateId=" + template.templateId() + System.lineSeparator()
				+ "distributionId=" + scheduledDistribution.distributionId() + System.lineSeparator()
				+ "targetId=" + targetId + System.lineSeparator()
				+ "scheduledAtDisplay=" + scheduledAtDisplay + System.lineSeparator());
		System.out.printf("P20_DISTRIBUTION_SHARED_FIXTURE templateId=%d distributionId=%d targetId=%d%n",
				template.templateId(), scheduledDistribution.distributionId(), targetId);
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
		long firstTargetId = scalar("SELECT distribution_target_id FROM student_exercises WHERE student_user_id = "
				+ studentUserId);
		long firstExerciseId = scalar("SELECT student_exercise_id FROM student_exercises WHERE student_user_id = "
				+ studentUserId);
		assertEquals(1, notificationCount(firstTargetId));
		var firstNotification = new StudentNotificationControl().open(student, notificationId(firstTargetId));
		assertEquals(OpenStatus.OPENED, firstNotification.status());
		assertEquals("/student/exercise?exerciseId=" + firstExerciseId, firstNotification.redirectPath());
		assertEquals(1, scalar("SELECT COUNT(*) FROM student_notifications WHERE notification_id="
				+ notificationId(firstTargetId) + " AND read_at IS NOT NULL"));

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
		long secondTargetId = scalar("SELECT distribution_target_id FROM student_exercises WHERE student_user_id = "
				+ studentUserId + " AND distribution_target_id <> " + firstTargetId);
		assertEquals(1, notificationCount(secondTargetId));

		var retry = control.schedule(teacher,
				input(template.templateId(), secondVersion, items,
						List.of(new Target(classroomId, null)), secondToken));
		assertEquals(second.distributionId(), retry.distributionId());
		assertEquals(0, retry.expectedImmediateTargets());
		assertEquals(2, scalar("SELECT COUNT(*) FROM student_exercises"));
		assertEquals(2, scalar("SELECT COUNT(*) FROM student_notifications WHERE student_user_id="
				+ studentUserId + " AND notification_type='exercise_distributed'"),
				"retrying the same distribution must not create duplicate delivery notifications");
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
		assertTrue(historyDetail("配信日時変更")
				.contains(DateTimeContract.toUserDisplay(rescheduledAt).toString()));
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
		assertEquals(1, notificationCount(targetId), "scheduled delivery is notified only after it succeeds");

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
		if (!initialized || retainBrowserFixture) return;
		try (Connection connection = Client.createConnection(); var statement = connection.createStatement()) {
			for (String sql : List.of(
					"DELETE FROM student_notifications",
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

	private void setDistributionFeature(long teacherId, boolean enabled) throws SQLException {
		try (Connection connection = Client.createConnection(); var statement = connection.prepareStatement("""
				UPDATE teacher_feature_permissions SET is_enabled = ?
				WHERE teacher_user_id = ? AND feature_code = 'code-distribution'
				""")) {
			statement.setBoolean(1, enabled);
			statement.setLong(2, teacherId);
			assertEquals(1, statement.executeUpdate());
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

	private static long notificationId(long targetId) throws SQLException {
		return scalar("SELECT notification_id FROM student_notifications WHERE notification_type="
				+ "'exercise_distributed' AND distribution_target_id=" + targetId);
	}

	private long notificationCount(long targetId) throws SQLException {
		return scalar("SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + studentUserId
				+ " AND notification_type='exercise_distributed' AND distribution_target_id=" + targetId);
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
