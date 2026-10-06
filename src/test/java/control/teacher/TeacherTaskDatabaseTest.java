package control.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import control.auth.PasswordHasher;
import control.evaluation.EvaluationProvider;
import control.evaluation.ReevaluationPreviewWorker;
import control.student.StandardRubricSource;
import entity.EditorHint;
import entity.EditorSubmissionCheck;
import entity.EditorSubmissionCheckCase;
import entity.EditorSubmissionResult;
import entity.EditorSaveResult;
import entity.EditorTestCase;
import entity.ReevaluationPreview;
import entity.ReevaluationJobStatus;
import entity.StudentEditorPage;
import entity.TeacherNavigationSummary;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.Difficulty;
import entity.TeacherTaskInput.HintInput;
import entity.TeacherTaskInput.LateSubmissionPolicy;
import entity.UserCredential.UserType;
import lib.mysql.Client;
import dao.ReevaluationPreviewDao;
import dao.StudentEditorDao;
import dao.StandardRubricDao;
import dao.TeacherPromptDao;
import dao.TeacherTaskPublicationDao;

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
	private boolean retainFixtureAfterTest;

	@Test
	void createsRetainedSyntheticTeacherFixtureForIsolatedBrowserAcceptance() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_TASK_BROWSER_FIXTURE")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_task_test_[a-z0-9_]+"));
		String password = System.getenv("TEACHER_TASK_BROWSER_PASSWORD");
		assertTrue(password != null && password.length() >= 12,
				"A throwaway password must be supplied for the isolated browser fixture.");

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE users SET password_hash = ? WHERE user_id = ? AND login_id = 'teacher-demo'
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setString(1, new PasswordHasher().hash(password.toCharArray()));
			statement.setLong(2, teacherId);
			assertEquals(1, statement.executeUpdate());
		}

		long taskId = TASKS.createDraft(
				teacher(), input("Browser publication acceptance"), UUID.randomUUID().toString());
		activateConfiguredPrompt(teacher(), taskId);
		retainFixtureAfterTest = true;
	}

	@Test
	void createsRetainedPublishedAssignmentManagementFixtureForBrowserAcceptance() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_TASK_ASSIGNMENT_BROWSER_FIXTURE")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_task_test_[a-z0-9_]+"));
		String password = System.getenv("TEACHER_TASK_BROWSER_PASSWORD");
		assertTrue(password != null && password.length() >= 12,
				"A throwaway password must be supplied for the browser fixture.");
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"UPDATE users SET password_hash = ? WHERE user_id = ? AND login_id = 'teacher-demo'")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setString(1, new PasswordHasher().hash(password.toCharArray()));
			statement.setLong(2, teacherId);
			assertEquals(1, statement.executeUpdate());
		}

		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(
				owner,
				inputWithAssignments(
						"Published assignment management browser acceptance",
						List.of(new ClassAssignmentInput(
								0, classroomId, null, LocalDateTime.now().plusDays(1), LateSubmissionPolicy.ALLOW))),
				UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);
		retainFixtureAfterTest = true;
		System.out.printf("TASK_ASSIGNMENT_BROWSER_FIXTURE loginId=%s taskId=%d assignmentId=%d "
				+ "classroomId=%d addableClassroomId=%d%n",
				teacherLoginId, taskId, assignmentId, classroomId, secondClassroomId);
	}

	@Test
	void createsRetainedIndependentCopyFixtureForIsolatedBrowserAcceptance() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_TASK_BROWSER_FIXTURE")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_task_test_[a-z0-9_]+"));
		String password = System.getenv("TEACHER_TASK_BROWSER_PASSWORD");
		assertTrue(password != null && password.length() >= 12,
				"A throwaway password must be supplied for the isolated browser fixture.");
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE users SET password_hash = ? WHERE user_id = ? AND login_id = 'teacher-demo'
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setString(1, new PasswordHasher().hash(password.toCharArray()));
			statement.setLong(2, teacherId);
			assertEquals(1, statement.executeUpdate());
		}

		AuthenticatedUser owner = teacher();
		TeacherTaskInput sourceInput = inputWithAssignments(
				"Independent copy browser source",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, null, LateSubmissionPolicy.ALLOW)));
		long sourceTaskId = TASKS.createDraft(owner, sourceInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, sourceTaskId);
		long sourcePromptVersionId = findActivePromptVersionId(sourceTaskId);
		assertNotNull(sourcePromptVersionId);
		setActivePromptCopyContent(sourceTaskId, sourcePromptVersionId);
		publishDraft(owner, sourceTaskId);
		long assignmentId = findAssignmentId(sourceTaskId, classroomId);
		long studentId = insertStudentFixture();
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try {
				insertParticipationAndAcceptedSubmission(connection, studentId, assignmentId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}

		retainFixtureAfterTest = true;
		System.out.printf("INDEPENDENT_COPY_BROWSER_FIXTURE loginId=%s sourceTaskId=%d%n",
				teacherLoginId, sourceTaskId);
	}

	@Test
	void createsRetainedSyntheticExpiryHistoryFixtureForRuntimeAcceptance() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_TASK_EXPIRY_HISTORY_FIXTURE")));
		assertTrue(System.getenv("DB_NAME").matches("ppe_teacher_task_test_[a-z0-9_]+"));

		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(
				owner,
				inputWithAssignments(
						"Expiry history retention acceptance",
						List.of(new ClassAssignmentInput(
								0, classroomId, null, LocalDateTime.now().plusDays(1),
								LateSubmissionPolicy.ALLOW))),
				UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);
		long studentId = insertStudentFixture();

		long participationId;
		long submissionId;
		long evaluationId;
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try {
				participationId = insertParticipationAndAcceptedSubmission(connection, studentId, assignmentId);
				try (PreparedStatement statement = connection.prepareStatement(
						"SELECT submission_id FROM submissions WHERE participation_id = ?")) {
					statement.setLong(1, participationId);
					try (ResultSet rows = statement.executeQuery()) {
						if (!rows.next()) {
							throw new SQLException("Synthetic submission was not created.");
						}
						submissionId = rows.getLong("submission_id");
					}
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO code_logs (
						  participation_id, submission_id, event_type, snapshot_text,
						  execution_status_at_capture, observed_at, created_at
						) VALUES (?, ?, 'manual_save', 'synthetic expiry history', 'not_run',
						  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
						""")) {
					statement.setLong(1, participationId);
					statement.setLong(2, submissionId);
					assertEquals(1, statement.executeUpdate());
				}
				evaluationId = insertSyntheticInitialEvaluation(connection, taskId, submissionId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}

		assertEquals(1, countHistoryRows("task_participations", "participation_id", participationId));
		assertEquals(1, countHistoryRows("submissions", "submission_id", submissionId));
		assertEquals(1, countHistoryRows("evaluations", "evaluation_id", evaluationId));
		assertEquals(1, countHistoryRows("code_logs", "participation_id", participationId));
		retainFixtureAfterTest = true;
		System.out.printf(
				"EXPIRY_HISTORY_FIXTURE taskId=%d assignmentId=%d studentId=%d participationId=%d "
						+ "submissionId=%d evaluationId=%d%n",
				taskId, assignmentId, studentId, participationId, submissionId, evaluationId);
	}

	@Test
	void publicationWorkerTransitionsDueAssignmentsOnceWithSystemAudit() throws Exception {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput taskInput = inputWithAssignments(
				"Scheduled publication acceptance",
				List.of(new ClassAssignmentInput(
						0, classroomId, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
						LateSubmissionPolicy.ALLOW)));
		long taskId = TASKS.createDraft(owner, taskInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);
		assertEquals("scheduled", findAssignmentStatus(assignmentId));

		setAssignmentPublishTimeDue(assignmentId);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
			Future<Integer> first = workers.submit(() -> processWhenSignaled(ready, start));
			Future<Integer> second = workers.submit(() -> processWhenSignaled(ready, start));
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
		}
		assertEquals("published", findAssignmentStatus(assignmentId));
		assertEquals(1, countAssignmentAudits(assignmentId, "publish_scheduled"));

		setAssignmentDueTimePast(assignmentId);
		TeacherTaskPublicationDao publicationDao = new TeacherTaskPublicationDao();
		assertEquals(1, publicationDao.processDueAssignments());
		assertEquals("expired", findAssignmentStatus(assignmentId));
		assertEquals(1, countAssignmentAudits(assignmentId, "expire_assignment"));
		assertEquals(0, publicationDao.processDueAssignments());
		assertEquals(1, countAssignmentAudits(assignmentId, "publish_scheduled"));
		assertEquals(1, countAssignmentAudits(assignmentId, "expire_assignment"));
	}

	@Test
	void publicationWorkerDoesNotExpireBeforeDeadlineAndExpiresAtTheBoundary()
			throws SQLException, InterruptedException {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput taskInput = inputWithAssignments(
				"Deadline boundary acceptance",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, LocalDateTime.now().plusDays(1), LateSubmissionPolicy.ALLOW)));
		long taskId = TASKS.createDraft(owner, taskInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);

		setAssignmentDueTimeAfter(assignmentId, 2);
		TeacherTaskPublicationDao publicationDao = new TeacherTaskPublicationDao();
		assertEquals(0, publicationDao.processDueAssignments());
		assertEquals("published", findAssignmentStatus(assignmentId));

		awaitAssignmentDeadline(assignmentId);
		assertEquals(1, publicationDao.processDueAssignments());
		assertEquals("expired", findAssignmentStatus(assignmentId));
		assertEquals(1, countAssignmentAudits(assignmentId, "expire_assignment"));
		assertEquals(0, publicationDao.processDueAssignments());
		assertEquals(1, countAssignmentAudits(assignmentId, "expire_assignment"));
	}

	@Test
	void assignmentClassHistoryIncludesArchivedAssignmentsForCandidateFiltering() throws SQLException {
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(
				owner,
				inputWithAssignments(
						"Archived assignment candidate filtering",
						List.of(new ClassAssignmentInput(
								0, classroomId, null, null, LateSubmissionPolicy.ALLOW))),
				UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET assignment_status = 'archived'
						WHERE task_id = ? AND classroom_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, taskId);
			statement.setLong(2, classroomId);
			assertEquals(1, statement.executeUpdate());
		}

		var page = TASKS.loadPage(owner, null, schoolId);
		assertTrue(page.classes().stream().anyMatch(option -> option.classroomId() == classroomId));
		assertTrue(page.assignmentClassHistory().get(taskId).contains(classroomId));
	}

	@Test
	void extendsExpiredAssignmentAndAddsAClassWithoutChangingExistingHistory() throws SQLException {
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(
				owner,
				inputWithAssignments(
						"Published assignment management acceptance",
						List.of(new ClassAssignmentInput(
								0, classroomId, null, LocalDateTime.now().plusDays(1), LateSubmissionPolicy.ALLOW))),
				UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);
		long studentId = insertStudentFixture();
		long participationId;
		long submissionId;
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				participationId = insertParticipationAndAcceptedSubmission(connection, studentId, assignmentId);
				submissionId = findSubmissionId(connection, participationId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
		LocalDateTime originalPublishAt = findAssignmentPublishAt(assignmentId);
		assertEquals(1, countHistoryRows("submissions", "submission_id", submissionId));

		setAssignmentDueTimePast(assignmentId);
		assertEquals(1, new TeacherTaskPublicationDao().processDueAssignments());
		assertEquals("expired", findAssignmentStatus(assignmentId));
		LocalDateTime expiredDueAt = findAssignmentDueAt(assignmentId);
		var expiredTask = TASKS.loadPage(owner, taskId, schoolId).selectedTask();
		LocalDateTime extendedDueAt = LocalDateTime.now().plusDays(3).withNano(0);
		String extendRequest = UUID.randomUUID().toString();
		TASKS.managePublishedTaskAssignment(
				owner, taskId, expiredTask.version(), assignmentId, 0,
				null, extendedDueAt, null, extendRequest);

		assertEquals("published", findAssignmentStatus(assignmentId));
		assertEquals(extendedDueAt, findAssignmentDueAt(assignmentId));
		assertTrue(findAssignmentDueAt(assignmentId).isAfter(expiredDueAt));
		assertEquals(originalPublishAt, findAssignmentPublishAt(assignmentId));
		assertEquals(1, countHistoryRows("submissions", "submission_id", submissionId));
		assertThrows(TaskDraftConflictException.class, () -> TASKS.managePublishedTaskAssignment(
				owner, taskId, expiredTask.version(), assignmentId, 0,
				null, extendedDueAt.plusDays(1), null, UUID.randomUUID().toString()));
		assertEquals(extendedDueAt, findAssignmentDueAt(assignmentId));
		assertEquals(1, countTaskAuditActions(taskId, "extend_task_assignment_deadline"));

		var extendedTask = TASKS.loadPage(owner, taskId, schoolId).selectedTask();
		assertThrows(IllegalArgumentException.class, () -> TASKS.managePublishedTaskAssignment(
				owner, taskId, extendedTask.version(), assignmentId, 0,
				null, extendedDueAt.minusDays(1), null, UUID.randomUUID().toString()));
		String addClassRequest = UUID.randomUUID().toString();
		TASKS.managePublishedTaskAssignment(
				owner, taskId, extendedTask.version(), 0, secondClassroomId,
				null, null, LateSubmissionPolicy.DENY, addClassRequest);
		long addedAssignmentId = findAssignmentId(taskId, secondClassroomId);
		assertEquals("published", findAssignmentStatus(addedAssignmentId));
		assertNull(findAssignmentDueAtOrNull(addedAssignmentId));
		assertEquals("deny", findAssignmentLatePolicy(addedAssignmentId));
		assertEquals(1, countTaskAuditActions(taskId, "add_task_class_assignment"));

		assertEquals(2, countTaskAssignmentRows(taskId));
		long versionAfterAdd = TASKS.loadPage(owner, taskId, schoolId).selectedTask().version();
		assertThrows(IllegalArgumentException.class, () -> TASKS.managePublishedTaskAssignment(
				owner, taskId, versionAfterAdd, 0, secondClassroomId,
				null, null, LateSubmissionPolicy.DENY, UUID.randomUUID().toString()));
		assertThrows(SecurityException.class, () -> TASKS.managePublishedTaskAssignment(
				owner, taskId, versionAfterAdd, 0, foreignClassroomId,
				null, null, LateSubmissionPolicy.DENY, UUID.randomUUID().toString()));
		TASKS.managePublishedTaskAssignment(
				owner, taskId, extendedTask.version(), 0, secondClassroomId,
				null, null, LateSubmissionPolicy.DENY, addClassRequest);
		assertEquals(versionAfterAdd, TASKS.loadPage(owner, taskId, schoolId).selectedTask().version());
		assertEquals(2, countTaskAssignmentRows(taskId));
	}

	@Test
	void expiredAllowAssignmentAcceptsOnlyTheInitialSubmission() throws SQLException {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput taskInput = inputWithAssignments(
				"Late first submission acceptance",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, LocalDateTime.now().plusDays(1), LateSubmissionPolicy.ALLOW)));
		long taskId = TASKS.createDraft(owner, taskInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);
		setAssignmentDueTimePast(assignmentId);
		assertEquals(1, new TeacherTaskPublicationDao().processDueAssignments());
		assertEquals("expired", findAssignmentStatus(assignmentId));

		long studentId = insertStudentFixture();
		StudentEditorDao editorDao = new StudentEditorDao();
		StudentEditorPage initialPage = editorDao.findEditorPage(studentId, assignmentId).orElseThrow();
		assertTrue(initialPage.isCanSubmit());
		EditorSubmissionResult submitted = editorDao.submit(
				studentId, assignmentId, UUID.randomUUID().toString(), submissionCheck(initialPage, studentId));
		assertEquals(EditorSubmissionResult.Status.SUBMITTED, submitted.status());
		assertEquals(1, submitted.revisionNumber());

		StudentEditorPage submittedPage = editorDao.findEditorPage(studentId, assignmentId).orElseThrow();
		assertFalse(submittedPage.isCanSubmit());
		assertEquals(EditorSaveResult.Status.READ_ONLY, editorDao.startResubmission(studentId, assignmentId).status());
		EditorSubmissionResult lateResubmission = editorDao.submit(
				studentId, assignmentId, UUID.randomUUID().toString(), submissionCheck(submittedPage, studentId));
		assertEquals(EditorSubmissionResult.Status.NOT_ALLOWED, lateResubmission.status());
		assertEquals(1, countStudentSubmissions(studentId, assignmentId));
	}

	@Test
	void expiredDenyAssignmentDoesNotCreateStudentParticipation() throws SQLException {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput taskInput = inputWithAssignments(
				"Late deny acceptance",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, LocalDateTime.now().plusDays(1), LateSubmissionPolicy.DENY)));
		long taskId = TASKS.createDraft(owner, taskInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, taskId);
		publishDraft(owner, taskId);
		long assignmentId = findAssignmentId(taskId, classroomId);
		setAssignmentDueTimePast(assignmentId);
		assertEquals(1, new TeacherTaskPublicationDao().processDueAssignments());

		long studentId = insertStudentFixture();
		assertTrue(new StudentEditorDao().findEditorPage(studentId, assignmentId).isEmpty());
		assertEquals(0, countStudentParticipations(studentId));
	}

	@Test
	void createsAndPublishesNewRevisionWhilePreservingThePublishedRevisionUntilReplacement() throws SQLException {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput sourceInput = inputWithAssignments(
				"Task revision source",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, LocalDateTime.now().plusDays(2), LateSubmissionPolicy.ALLOW)));
		long sourceTaskId = TASKS.createDraft(owner, sourceInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, sourceTaskId);
		publishDraft(owner, sourceTaskId);
		var source = TASKS.loadPage(owner, sourceTaskId, schoolId).selectedTask();
		long oldAssignmentId = findAssignmentId(sourceTaskId, classroomId);
		long studentId = insertStudentFixture();
		assertTrue(new StudentEditorDao().findEditorPage(studentId, oldAssignmentId).isPresent());

		TeacherTaskInput revisedInput = inputWithAssignments(
				"Task revision updated",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, LocalDateTime.now().plusDays(3), LateSubmissionPolicy.ALLOW)));
		long revisionTaskId = TASKS.createRevision(
				owner, sourceTaskId, source.version(), revisedInput, UUID.randomUUID().toString());
		var revision = TASKS.loadPage(owner, revisionTaskId, schoolId).selectedTask();
		var original = TASKS.loadPage(owner, sourceTaskId, schoolId).selectedTask();

		assertEquals("requires_update", revision.publicationStatus());
		assertEquals(source.revisionNumber() + 1, revision.revisionNumber());
		assertEquals(original.taskCode(), revision.taskCode());
		assertEquals("published", original.publicationStatus());
		assertEquals("published", findAssignmentStatus(oldAssignmentId));
		assertEquals(null, revision.promptStatus());
		assertEquals("not_published", revision.input().classAssignments().getFirst().assignmentStatus());

		activateConfiguredPrompt(owner, revisionTaskId);
		TASKS.publishTask(owner, revisionTaskId, revision.version(), revision.input(), UUID.randomUUID().toString());

		assertEquals("requires_update", TASKS.loadPage(owner, sourceTaskId, schoolId)
				.selectedTask().publicationStatus());
		assertEquals("published", TASKS.loadPage(owner, revisionTaskId, schoolId)
				.selectedTask().publicationStatus());
		assertEquals("archived", findAssignmentStatus(oldAssignmentId));
		assertEquals("published", findAssignmentStatus(findAssignmentId(revisionTaskId, classroomId)));
	}

	@Test
	void refusesRevisionWhenAnyStudentHasStartedThePublishedTask() throws SQLException {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput sourceInput = inputWithAssignments(
				"Started task revision source",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, LocalDateTime.now().plusDays(2), LateSubmissionPolicy.ALLOW)));
		long sourceTaskId = TASKS.createDraft(owner, sourceInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, sourceTaskId);
		publishDraft(owner, sourceTaskId);
		var source = TASKS.loadPage(owner, sourceTaskId, schoolId).selectedTask();
		long assignmentId = findAssignmentId(sourceTaskId, classroomId);
		long studentId = insertStudentFixture();
		assertTrue(new StudentEditorDao().findEditorPage(studentId, assignmentId).isPresent());

		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_participations
						SET learning_status = 'in_progress', progress_status = 'in_progress'
						WHERE student_user_id = ? AND task_class_assignment_id = ?
						""")) {
			statement.setLong(1, studentId);
			statement.setLong(2, assignmentId);
			assertEquals(1, statement.executeUpdate());
		}

		assertThrows(IllegalArgumentException.class, () -> TASKS.createRevision(
				owner, sourceTaskId, source.version(), input("Rejected task revision"), UUID.randomUUID().toString()));
		assertEquals("published", TASKS.loadPage(owner, sourceTaskId, schoolId)
				.selectedTask().publicationStatus());
		assertEquals("published", findAssignmentStatus(assignmentId));
	}

	@BeforeEach
	void installFixtureOnlyInDedicatedDatabase() throws SQLException, IOException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_TASK_DB_TEST")));
		String database = System.getenv("DB_NAME");
		assertTrue(database != null && database.matches("ppe_teacher_task_test_[a-z0-9_]+"),
				"Teacher task integration tests require an isolated ppe_teacher_task_test_* database.");

		try (Connection connection = Client.createConnection()) {
			assertEquals(database, connection.getCatalog());
			assertTrue(hasSuccessfulMigration(connection, "19"),
					"V19 must be applied to the isolated test database before running integration tests.");
			new StandardRubricDao().register(StandardRubricSource.parse(
					java.nio.file.Files.readString(java.nio.file.Path.of(
							"docs/rubric/思考力・判断力・表現力_ルーブリック_0805.md")),
					java.nio.file.Files.readString(java.nio.file.Path.of(
							"docs/rubric/主体的に学習に取り組む態度_ルーブリック_0805.md"))));
			connection.setAutoCommit(false);
			try {
				teacherLoginId = "true".equals(System.getenv("TEACHER_TASK_BROWSER_FIXTURE"))
						|| "true".equals(System.getenv("TEACHER_TASK_ASSIGNMENT_BROWSER_FIXTURE"))
						? "teacher-demo"
						: "task-test-" + UUID.randomUUID().toString().replace("-", "");
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
		assertEquals(1, countTaskRubric(taskId));
		var saved = TASKS.loadPage(teacher, taskId, schoolId).selectedTask();
		assertEquals("Initial draft", saved.input().title());
		assertEquals(1, saved.version());
		assertEquals("test output", saved.input().testCases().getFirst().getExpectedOutput());
		assertEquals(classroomId, saved.input().classAssignments().getFirst().classroomId());
		assertEquals(1, TASKS.loadAuditEntries(teacher, taskId).size());
	}

	@Test
	void promptDraftUsesSharedRubricAndOptimisticVersioning() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Prompt draft task"), UUID.randomUUID().toString());
		TeacherPromptControl prompts = new TeacherPromptControl();

		var initialPage = prompts.loadPage(teacher, taskId, null);
		assertEquals(entity.StandardRubric.VERSION, initialPage.standardRubric().version());
		assertTrue(initialPage.selectedVersion() == null);
		assertEquals(1, countTaskRubric(taskId));
	}

	@Test
	void reevaluationJobStatusIsScopedToTeacherAndTaskAndReportsProgress() throws SQLException {
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(owner, input("Reevaluation status task"), UUID.randomUUID().toString());
		long promptVersionId = new TeacherPromptControl().saveDraft(
				owner, taskId, null, 0, "gemini-2.5-flash", "Synthetic prompt", null);
		long studentId = insertStudentFixture();
		long assignmentId = findAssignmentId(taskId, classroomId);
		List<Long> jobIds = new ArrayList<>();
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				long participationId;
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO task_participations
							(student_user_id, task_class_assignment_id, learning_status, progress_status,
							 save_status, evaluation_status, active_duration_seconds)
						VALUES (?, ?, 'completed', 'submitted', 'saved', 'not_started', 0)
						""", Statement.RETURN_GENERATED_KEYS)) {
					statement.setLong(1, studentId);
					statement.setLong(2, assignmentId);
					statement.executeUpdate();
					try (ResultSet keys = statement.getGeneratedKeys()) {
						if (!keys.next()) {
							throw new SQLException("Synthetic participation ID was not generated.");
						}

						participationId = keys.getLong(1);
					}
				}
				long submissionId;
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO submissions
							(participation_id, revision_number, submitted_code, submission_status,
							 submitted_at, created_at)
						VALUES (?, 1, 'print(1)', 'submitted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
						""", Statement.RETURN_GENERATED_KEYS)) {
					statement.setLong(1, participationId);
					statement.executeUpdate();
					try (ResultSet keys = statement.getGeneratedKeys()) {
						if (!keys.next()) {
							throw new SQLException("Synthetic submission ID was not generated.");
						}
						submissionId = keys.getLong(1);
					}
				}
				jobIds.add(insertReevaluationJob(connection, taskId, promptVersionId, owner.userId(),
						"queued", 4, 0, 0));
				jobIds.add(insertReevaluationJob(connection, taskId, promptVersionId, owner.userId(),
						"in_progress", 4, 2, 50));
				long completedJobId = insertReevaluationJob(
						connection, taskId, promptVersionId, owner.userId(), "completed", 1, 1, 100);
				jobIds.add(completedJobId);
				long failedJobId = insertReevaluationJob(connection, taskId, promptVersionId, owner.userId(),
						"failed", 4, 3, 75);
				jobIds.add(failedJobId);
				long evaluationId = insertSyntheticReevaluationResult(
						connection, taskId, promptVersionId, completedJobId, submissionId);
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO reevaluation_job_targets
							(reevaluation_job_id, participation_id, submission_id, target_status, evaluation_id,
							 materialization_attempts, created_at, updated_at, completed_at)
						VALUES (?, ?, ?, 'completed', ?, 1, CURRENT_TIMESTAMP(6),
						        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
						""")) {
					statement.setLong(1, completedJobId);
					statement.setLong(2, participationId);
					statement.setLong(3, submissionId);
					statement.setLong(4, evaluationId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO reevaluation_job_targets
							(reevaluation_job_id, participation_id, submission_id, target_status,
							 materialization_attempts, created_at, updated_at)
						VALUES (?, ?, ?, 'failed', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
						""")) {
					statement.setLong(1, failedJobId);
					statement.setLong(2, participationId);
					statement.setLong(3, submissionId);
					statement.executeUpdate();
				}
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}

		ReevaluationPreviewControl reevaluations = new ReevaluationPreviewControl();
		List<String> expectedStatuses = List.of("queued", "in_progress", "completed", "failed");
		List<Integer> expectedTargetCounts = List.of(4, 4, 1, 4);
		List<Integer> expectedCompletedCounts = List.of(0, 2, 1, 3);
		List<Integer> expectedFailedCounts = List.of(0, 0, 0, 1);
		List<Integer> expectedProgress = List.of(0, 50, 100, 75);
		List<String> expectedJobStatuses = List.of("queued", "in_progress", "completed", "failed");
		for (int index = 0; index < jobIds.size(); index++) {
			ReevaluationJobStatus status = reevaluations.loadJobStatus(owner, taskId, jobIds.get(index));
			assertEquals(expectedStatuses.get(index), status.status());
			assertEquals(expectedTargetCounts.get(index), status.targetCount());
			assertEquals(expectedCompletedCounts.get(index), status.completedCount());
			assertEquals(expectedFailedCounts.get(index), status.failedCount());
			assertEquals(expectedProgress.get(index), status.progressPercent().intValueExact());
			if (index == 2) {
				assertEquals(1, status.targetResults().size());
				assertEquals("completed", status.targetResults().getFirst().status());
				assertEquals(1, status.targetResults().getFirst().submissionRevision());
				assertEquals(new java.math.BigDecimal("3.500"),
						status.targetResults().getFirst().overallScore());
				assertEquals(new java.math.BigDecimal("4.000"),
						status.targetResults().getFirst().thinkingScore());
				assertEquals(new java.math.BigDecimal("3.000"),
						status.targetResults().getFirst().attitudeScore());
			}
			if (index == 3) {
				assertEquals(1, status.targetResults().size());
				assertEquals("failed", status.targetResults().getFirst().status());
				assertEquals(studentLoginId(studentId), status.targetResults().getFirst().studentLoginId());
			}
		}
		var page = new TeacherPromptControl().loadPage(owner, taskId, promptVersionId);
		assertEquals(jobIds.size(), page.reevaluationJobs().size());
		assertEquals(jobIds.getLast(), page.reevaluationJobs().getFirst().jobId());
		assertEquals(expectedJobStatuses.getLast(), page.reevaluationJobs().getFirst().status());
		assertEquals(owner.loginId(), page.reevaluationJobs().getFirst().requestedByLoginId());

		long otherTeacherId;
		String otherLoginId = "task-test-" + UUID.randomUUID().toString().replace("-", "");
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				otherTeacherId = insertUser(connection, otherLoginId, "Other authorized teacher");
				fixtureUserIds.add(otherTeacherId);
				insertPermissionFixtures(connection, otherTeacherId, schoolId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
		AuthenticatedUser otherTeacher = new AuthenticatedUser(
				otherTeacherId, otherLoginId, "Other authorized teacher", UserType.TEACHER, false, "test");
		AuthenticatedUser student = new AuthenticatedUser(
				987654321L, "synthetic-student", "Synthetic student", UserType.STUDENT, false, "test");

		assertThrows(dao.TeacherPromptDao.TeacherTaskNotFoundException.class,
				() -> reevaluations.loadJobStatus(owner, taskId + 1, jobIds.getFirst()));
		assertThrows(dao.TeacherPromptDao.TeacherTaskNotFoundException.class,
				() -> reevaluations.loadJobStatus(owner, taskId, Long.MAX_VALUE));
		assertThrows(dao.TeacherPromptDao.TeacherTaskNotFoundException.class,
				() -> reevaluations.loadJobStatus(otherTeacher, taskId, jobIds.getFirst()));
		assertThrows(SecurityException.class,
				() -> reevaluations.loadJobStatus(student, taskId, jobIds.getFirst()));
		if ("true".equals(System.getenv("T014_RETAIN_HISTORY_FIXTURE"))) {
			assertEquals("ppe_teacher_task_test_t014", System.getenv("DB_NAME"));
			retainFixtureAfterTest = true;
			setAcceptanceFixturePassword();
			long completedJobId = page.reevaluationJobs().stream()
					.filter(job -> "completed".equals(job.status()))
					.findFirst().orElseThrow().jobId();
			System.out.printf(
					"T014 synthetic history fixture: taskId=%d promptVersionId=%d jobId=%d loginId=%s%n",
					taskId, promptVersionId, completedJobId, teacherLoginId);
		}
	}

	@Test
	void syntheticProviderGeneratesPreviewThatCanBeConfirmed() throws Exception {
		if ("true".equals(System.getenv("T031_RETAIN_PREVIEW_FIXTURE"))) {
			assertEquals("ppe_teacher_task_test_t031", System.getenv("DB_NAME"));
		}
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(owner, input("Synthetic preview acceptance"), UUID.randomUUID().toString());
		long promptVersionId = new TeacherPromptControl().saveDraft(
				owner, taskId, null, 0, "gemini-2.5-flash", "Synthetic preview prompt", null);
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE prompt_versions
						SET prompt_status = 'configured', fluctuation_generation_status = 'completed',
						    evaluation_examples_status = 'completed'
						WHERE prompt_version_id = ?
						""")) {
			statement.setLong(1, promptVersionId);
			assertEquals(1, statement.executeUpdate());
		}
		long studentId = insertStudentFixture();
		long assignmentId = findAssignmentId(taskId, classroomId);
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				insertParticipationAndAcceptedSubmission(connection, studentId, assignmentId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}

		var selectedPrompt = new TeacherPromptControl().loadPage(owner, taskId, promptVersionId).selectedVersion();
		assertTrue(selectedPrompt != null);
		ReevaluationPreviewControl reevaluations = new ReevaluationPreviewControl();
		String previewCode = reevaluations.startPreview(
				owner, taskId, promptVersionId, selectedPrompt.rowVersion());
		ReevaluationPreviewWorker worker = new ReevaluationPreviewWorker(
				new ReevaluationPreviewDao(), syntheticProvider());
		worker.start();
		ReevaluationPreview preview;
		try {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			do {
				preview = reevaluations.loadPreview(owner, previewCode);
				if ("ready".equals(preview.status()) || "failed".equals(preview.status())) {
					break;
				}
				Thread.sleep(100);
			} while (System.nanoTime() < deadline);
		} finally {
			worker.stop();
		}

		assertEquals("ready", preview.status());
		assertEquals(1, preview.targetCount());
		assertEquals("succeeded", preview.targets().getFirst().status());
		assertEquals(4, preview.targets().getFirst().thinkingScore());
		assertEquals(3, preview.targets().getFirst().attitudeScore());
		if ("true".equals(System.getenv("T031_RETAIN_PREVIEW_FIXTURE"))) {
			setAcceptanceFixturePassword();
			System.out.printf(
					"T031 synthetic preview fixture: taskId=%d promptVersionId=%d previewCode=%s loginId=%s%n",
					taskId, promptVersionId, previewCode, teacherLoginId);
			return;
		}

		long jobId = reevaluations.confirmPreview(owner, previewCode);
		var status = reevaluations.loadJobStatus(owner, taskId, jobId);
		assertEquals("queued", status.status());
		assertEquals(1, status.targetCount());
		assertEquals(1, countJobTargets(jobId));
	}

	@Test
	void publishedTaskAllowsASeparatePromptDraftWithoutChangingTheActiveVersion() throws SQLException {
		AuthenticatedUser teacher = teacher();
		long taskId = TASKS.createDraft(teacher, input("Published prompt task"), UUID.randomUUID().toString());
		TeacherPromptControl prompts = new TeacherPromptControl();
		long activePromptId = prompts.saveDraft(
				teacher, taskId, null, 0, "gemini-2.5-pro", "Active prompt instructions", null);
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE prompt_versions
						SET prompt_status = 'configured', fluctuation_generation_status = 'completed',
						    evaluation_examples_status = 'completed'
						WHERE prompt_version_id = ?
						""")) {
			statement.setLong(1, activePromptId);
			assertEquals(1, statement.executeUpdate());
		}
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE tasks
						SET active_prompt_version_id = ?, publication_status = 'published'
						WHERE task_id = ?
						""")) {
			statement.setLong(1, activePromptId);
			statement.setLong(2, taskId);
			assertEquals(1, statement.executeUpdate());
		}

		var publishedPage = prompts.loadPage(teacher, taskId, activePromptId);
		assertTrue(publishedPage.tasks().stream().anyMatch(task -> task.taskId() == taskId
				&& "published".equals(task.publicationStatus())));
		assertEquals(activePromptId, publishedPage.activePromptVersionId());

		long nextPromptId = prompts.saveDraft(
				teacher, taskId, null, 0, "gemini-2.5-flash", "Revised prompt draft", "Extra guidance");
		var revisedPage = prompts.loadPage(teacher, taskId, nextPromptId);
		assertEquals("published", revisedPage.selectedTask().publicationStatus());
		assertEquals(activePromptId, revisedPage.activePromptVersionId());
		assertEquals("configured", revisedPage.versions().stream()
				.filter(version -> version.promptVersionId() == activePromptId)
				.findFirst().orElseThrow().promptStatus());
		assertEquals("draft", revisedPage.selectedVersion().promptStatus());
		assertEquals("Revised prompt draft", revisedPage.selectedVersion().commonPrompt());

		long promptVersionId = prompts.saveDraft(
				teacher, taskId, null, 0, "gemini-2.5-pro", "Initial evaluation instructions", null);
		var created = prompts.loadPage(teacher, taskId, promptVersionId).selectedVersion();
		assertEquals("v3", created.version());
		assertEquals("draft", created.promptStatus());
		assertEquals(1, created.rowVersion());

		assertEquals(promptVersionId, prompts.saveDraft(
				teacher, taskId, promptVersionId, created.rowVersion(),
				"gemini-2.5-flash", "Updated evaluation instructions", "Check input validation."));
		var updatedPage = prompts.loadPage(teacher, taskId, promptVersionId);
		assertEquals("Updated evaluation instructions", updatedPage.selectedVersion().commonPrompt());
		assertEquals("Check input validation.", updatedPage.selectedVersion().additionalInstruction());
		assertEquals(2, updatedPage.selectedVersion().rowVersion());
		assertEquals(3, updatedPage.versions().size());
		assertFalse(updatedPage.auditEntries().isEmpty());
		assertThrows(dao.TeacherPromptDao.PromptVersionConflictException.class,
				() -> prompts.saveDraft(
						teacher, taskId, promptVersionId, created.rowVersion(),
						"gemini-2.5-pro", "Stale update", null));
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
						LateSubmissionPolicy.DENY)),
				schoolId);

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
	void sameSchoolTaskManagerCanDeleteAndRestoreWithoutEditingAnotherTeachersTask() throws SQLException {
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(owner, input("Managed task"), UUID.randomUUID().toString());
		String otherLoginId = "task-test-" + UUID.randomUUID().toString().replace("-", "");
		long otherTeacherId;
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				otherTeacherId = insertUser(connection, otherLoginId, "Same-school task manager");
				fixtureUserIds.add(otherTeacherId);
				insertPermissionFixtures(connection, otherTeacherId, schoolId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
		AuthenticatedUser manager = new AuthenticatedUser(
				otherTeacherId, otherLoginId, "Same-school task manager", UserType.TEACHER, false, "test");
		var created = TASKS.loadPage(owner, taskId, schoolId).selectedTask();

		assertThrows(TaskDraftNotFoundException.class, () -> TASKS.loadPage(manager, taskId, schoolId));
		TASKS.deleteTask(manager, taskId, created.version(), UUID.randomUUID().toString());
		var deleted = TASKS.loadPage(manager, null, null).deletedTasks().stream()
				.filter(task -> task.taskId() == taskId)
				.findFirst()
				.orElseThrow();
		assertEquals("archived", deleted.publicationStatus());
		assertEquals(1, countUnpublishedAssignments(taskId));

		TASKS.restoreTask(manager, taskId, deleted.version(), UUID.randomUUID().toString());
		var restored = TASKS.loadPage(owner, taskId, schoolId).selectedTask();
		assertEquals("draft", restored.publicationStatus());
		assertEquals(created.input().schoolId(), restored.input().schoolId());
		assertEquals(1, countUnpublishedAssignments(taskId));
		assertEquals(List.of("create_draft", "delete_task", "restore_task"), findTaskAuditActions(taskId));
	}

	@Test
	void copiesLearningStartedTaskIntoIndependentDraftWithoutCopyingHistory() throws SQLException {
		AuthenticatedUser owner = teacher();
		TeacherTaskInput base = inputWithAssignments(
				"Independent source task",
				List.of(new ClassAssignmentInput(
						0, classroomId, null, null, LateSubmissionPolicy.ALLOW)));
		TeacherTaskInput sourceInput = new TeacherTaskInput(
				base.title(), base.theme(), base.difficulty(), base.description(), base.inputConstraints(),
				base.creationRules(), base.initialCode(), base.features(), base.testCases(),
				List.of(new HintInput(0, 1,
						new EditorHint("Source hint", "Keep this hint", "hint()", "print('hint')"))),
				base.classAssignments(), base.schoolId());
		long sourceTaskId = TASKS.createDraft(owner, sourceInput, UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, sourceTaskId);
		Long sourcePromptVersionValue = findActivePromptVersionId(sourceTaskId);
		assertNotNull(sourcePromptVersionValue);
		long sourcePromptVersionId = sourcePromptVersionValue;
		setActivePromptCopyContent(sourceTaskId, sourcePromptVersionId);
		publishDraft(owner, sourceTaskId);

		long sourceAssignmentId = findAssignmentId(sourceTaskId, classroomId);
		long studentId = insertStudentFixture();
		long participationId;
		long submissionId;
		long evaluationId;
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try {
				participationId = insertParticipationAndAcceptedSubmission(
						connection, studentId, sourceAssignmentId);
				submissionId = findSubmissionId(connection, participationId);
				try (PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO code_logs (
						  participation_id, submission_id, event_type, snapshot_text,
						  execution_status_at_capture, observed_at, created_at
						) VALUES (?, ?, 'manual_save', 'source history', 'not_run',
						  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
						""")) {
					statement.setLong(1, participationId);
					statement.setLong(2, submissionId);
					assertEquals(1, statement.executeUpdate());
				}
				evaluationId = insertSyntheticInitialEvaluation(connection, sourceTaskId, submissionId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}

		var sourceBeforeCopy = TASKS.loadPage(owner, sourceTaskId, schoolId).selectedTask();
		assertTrue(sourceBeforeCopy.learningStarted());
		String requestId = UUID.randomUUID().toString();
		long copiedTaskId = TASKS.createIndependentCopy(
				owner, sourceTaskId, sourceBeforeCopy.version(), requestId);
		assertEquals(copiedTaskId, TASKS.createIndependentCopy(
				owner, sourceTaskId, sourceBeforeCopy.version(), requestId));

		var copied = TASKS.loadPage(owner, copiedTaskId, schoolId).selectedTask();
		assertEquals("draft", copied.publicationStatus());
		assertEquals(1, copied.revisionNumber());
		assertEquals(copied.taskCode() + "-v1", copied.taskRevisionCode());
		assertNotEquals(sourceBeforeCopy.taskCode(), copied.taskCode());
		assertEquals(sourceInput.schoolId(), copied.input().schoolId());
		assertEquals(sourceInput.title(), copied.input().title());
		assertEquals(sourceInput.theme(), copied.input().theme());
		assertEquals(sourceInput.difficulty(), copied.input().difficulty());
		assertEquals(sourceInput.description(), copied.input().description());
		assertEquals(sourceInput.inputConstraints(), copied.input().inputConstraints());
		assertEquals(sourceInput.creationRules(), copied.input().creationRules());
		assertEquals(sourceInput.initialCode(), copied.input().initialCode());
		assertEquals(sourceInput.features(), copied.input().features());
		assertEquals(sourceInput.testCases().getFirst().getInput(),
				copied.input().testCases().getFirst().getInput());
		assertEquals(sourceInput.hints().getFirst().hint().getContent(),
				copied.input().hints().getFirst().hint().getContent());
		assertTrue(copied.input().classAssignments().isEmpty());
		assertFalse(copied.learningStarted());
		assertEquals(0, countTaskAssignmentRows(copiedTaskId));
		assertEquals(1, countTaskRubric(copiedTaskId));

		var copiedPrompt = new TeacherPromptControl().loadPage(owner, copiedTaskId, null).selectedVersion();
		assertNotNull(copiedPrompt);
		assertTrue(copiedPrompt.promptVersionId() != sourcePromptVersionId);
		assertEquals("draft", copiedPrompt.promptStatus());
		assertEquals("gemini-2.5-pro", copiedPrompt.aiModel());
		assertEquals("Copied common prompt", copiedPrompt.commonPrompt());
		assertEquals("Copied task-specific instruction", copiedPrompt.additionalInstruction());
		assertEquals("not_generated", copiedPrompt.fluctuationGenerationStatus());
		assertEquals("not_generated", copiedPrompt.evaluationExamplesStatus());
		assertTrue(copiedPrompt.fluctuationItems().isEmpty());
		assertTrue(copiedPrompt.evaluationExamples().isEmpty());
		assertEquals(0, countHistoryRows(
				"prompt_fluctuation_items", "prompt_version_id", copiedPrompt.promptVersionId()));
		assertEquals(0, countHistoryRows(
				"evaluation_examples", "prompt_version_id", copiedPrompt.promptVersionId()));
		assertNull(findActivePromptVersionId(copiedTaskId));

		var sourceAfterCopy = TASKS.loadPage(owner, sourceTaskId, schoolId).selectedTask();
		assertEquals(sourceBeforeCopy.version(), sourceAfterCopy.version());
		assertEquals("published", sourceAfterCopy.publicationStatus());
		assertEquals(sourcePromptVersionId, findActivePromptVersionId(sourceTaskId));
		assertEquals("published", findAssignmentStatus(sourceAssignmentId));
		assertEquals(1, countTaskAssignmentRows(sourceTaskId));
		assertEquals(1, countHistoryRows("task_participations", "participation_id", participationId));
		assertEquals(1, countHistoryRows("submissions", "submission_id", submissionId));
		assertEquals(1, countHistoryRows("evaluations", "evaluation_id", evaluationId));
		assertEquals(1, countHistoryRows("code_logs", "participation_id", participationId));
		assertEquals(1, countTaskAuditActions(copiedTaskId, "create_independent_task_copy"));
	}

	@Test
	void independentCopyRejectsPublishedTaskBeforeLearningStarts() throws SQLException {
		AuthenticatedUser owner = teacher();
		long sourceTaskId = TASKS.createDraft(
				owner,
				inputWithAssignments(
						"Not-started source task",
						List.of(new ClassAssignmentInput(
								0, classroomId, null, null, LateSubmissionPolicy.ALLOW))),
				UUID.randomUUID().toString());
		activateConfiguredPrompt(owner, sourceTaskId);
		publishDraft(owner, sourceTaskId);
		var source = TASKS.loadPage(owner, sourceTaskId, schoolId).selectedTask();
		assertFalse(source.learningStarted());
		int taskCount = countTasksOwnedByTeacher();

		assertThrows(IllegalArgumentException.class,
				() -> TASKS.createIndependentCopy(
						owner, sourceTaskId, source.version(), UUID.randomUUID().toString()));
		assertEquals(taskCount, countTasksOwnedByTeacher());
		assertEquals("published", TASKS.loadPage(owner, sourceTaskId, schoolId)
				.selectedTask().publicationStatus());
	}

	@Test
	void taskManagerFromAnotherSchoolCannotDeleteOrRestoreTask() throws SQLException {
		AuthenticatedUser owner = teacher();
		long taskId = TASKS.createDraft(owner, input("School boundary task"), UUID.randomUUID().toString());
		String otherLoginId = "task-test-" + UUID.randomUUID().toString().replace("-", "");
		long otherTeacherId;
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				otherTeacherId = insertUser(connection, otherLoginId, "Foreign-school task manager");
				fixtureUserIds.add(otherTeacherId);
				insertPermissionFixtures(connection, otherTeacherId, foreignSchoolId);
				connection.commit();
			} catch (SQLException | RuntimeException | Error failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
		AuthenticatedUser foreignTeacher = new AuthenticatedUser(
				otherTeacherId, otherLoginId, "Foreign-school task manager", UserType.TEACHER, false, "test");
		long version = TASKS.loadPage(owner, taskId, schoolId).selectedTask().version();

		assertThrows(SecurityException.class,
				() -> TASKS.deleteTask(foreignTeacher, taskId, version, UUID.randomUUID().toString()));
		var unchanged = TASKS.loadPage(owner, taskId, schoolId).selectedTask();
		assertEquals("draft", unchanged.publicationStatus());
		assertEquals(version, unchanged.version());
	}

	@Test
	void rejectsClassOutsideTheSelectedTaskSchool() {
		TeacherTaskInput valid = input("Wrong-school class");
		TeacherTaskInput invalid = new TeacherTaskInput(
				valid.title(), valid.theme(), valid.difficulty(), valid.description(),
				valid.inputConstraints(), valid.creationRules(), valid.initialCode(),
				valid.features(), valid.testCases(), valid.hints(),
				List.of(new ClassAssignmentInput(
						0, foreignClassroomId, null, null, LateSubmissionPolicy.ALLOW)),
				schoolId);

		assertThrows(SecurityException.class,
				() -> TASKS.createDraft(teacher(), invalid, UUID.randomUUID().toString()));
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
		if ("true".equals(System.getenv("T031_RETAIN_PREVIEW_FIXTURE"))) {
			assertEquals("ppe_teacher_task_test_t031", System.getenv("DB_NAME"));
			return;
		}
		if (retainFixtureAfterTest) {
			return;
		}
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM audit_logs WHERE actor_user_id = ?
						""")) {
					for (long fixtureUserId : fixtureUserIds) {
						statement.setLong(1, fixtureUserId);
						statement.addBatch();
					}
					statement.executeBatch();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM audit_logs
						WHERE actor_role = 'system' AND target_type = 'task_class_assignment'
						  AND target_id IN (
						    SELECT task_class_assignment_id FROM task_class_assignments
						    WHERE task_id IN (
						      SELECT task_id FROM tasks WHERE created_by_user_id = ?
						    )
						  )
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM audit_logs WHERE actor_user_id = ? AND feature_code = 'teacher-prompt-design'
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM evaluation_examples
						WHERE prompt_version_id IN (
						  SELECT prompt_version_id FROM prompt_versions
						  WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM prompt_fluctuation_items
						WHERE prompt_version_id IN (
						  SELECT prompt_version_id FROM prompt_versions
						  WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE pt FROM reevaluation_preview_targets pt
						JOIN reevaluation_previews p
						  ON p.reevaluation_preview_id = pt.reevaluation_preview_id
						WHERE p.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM reevaluation_previews
						WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE jt FROM reevaluation_job_targets jt
						JOIN reevaluation_jobs j ON j.reevaluation_job_id = jt.reevaluation_job_id
						WHERE j.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE dr FROM evaluation_dimension_results dr
						JOIN evaluations e ON e.evaluation_id = dr.evaluation_id
						WHERE e.reevaluation_job_id IN (
						  SELECT reevaluation_job_id FROM reevaluation_jobs
						  WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE er FROM evaluation_responses er
						JOIN evaluation_requests eq ON eq.evaluation_request_id = er.evaluation_request_id
						JOIN evaluations e ON e.evaluation_id = eq.evaluation_id
						WHERE e.reevaluation_job_id IN (
						  SELECT reevaluation_job_id FROM reevaluation_jobs
						  WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						) OR e.submission_id IN (
						  SELECT s.submission_id
						  FROM submissions s
						  JOIN task_participations tp ON tp.participation_id = s.participation_id
						  JOIN task_class_assignments a
						    ON a.task_class_assignment_id = tp.task_class_assignment_id
						  WHERE a.task_id IN (
						    SELECT task_id FROM tasks WHERE created_by_user_id = ?
						  )
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.setLong(2, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM evaluation_input_snapshots
						WHERE evaluation_request_id IN (
						  SELECT eq.evaluation_request_id
						  FROM evaluation_requests eq
						  JOIN evaluations e ON e.evaluation_id = eq.evaluation_id
						  WHERE e.reevaluation_job_id IN (
						    SELECT reevaluation_job_id FROM reevaluation_jobs
						    WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						  ) OR e.submission_id IN (
						    SELECT s.submission_id
						    FROM submissions s
						    JOIN task_participations tp ON tp.participation_id = s.participation_id
						    JOIN task_class_assignments a
						      ON a.task_class_assignment_id = tp.task_class_assignment_id
						    WHERE a.task_id IN (
						      SELECT task_id FROM tasks WHERE created_by_user_id = ?
						    )
						  )
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.setLong(2, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE eq FROM evaluation_requests eq
						JOIN evaluations e ON e.evaluation_id = eq.evaluation_id
						WHERE e.reevaluation_job_id IN (
						  SELECT reevaluation_job_id FROM reevaluation_jobs
						  WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						) OR e.submission_id IN (
						  SELECT s.submission_id
						  FROM submissions s
						  JOIN task_participations tp ON tp.participation_id = s.participation_id
						  JOIN task_class_assignments a
						    ON a.task_class_assignment_id = tp.task_class_assignment_id
						  WHERE a.task_id IN (
						    SELECT task_id FROM tasks WHERE created_by_user_id = ?
						  )
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.setLong(2, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE e FROM evaluations e
						WHERE e.reevaluation_job_id IN (
						  SELECT reevaluation_job_id FROM reevaluation_jobs
						  WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						) OR e.submission_id IN (
						  SELECT s.submission_id
						  FROM submissions s
						  JOIN task_participations tp ON tp.participation_id = s.participation_id
						  JOIN task_class_assignments a
						    ON a.task_class_assignment_id = tp.task_class_assignment_id
						  WHERE a.task_id IN (
						    SELECT task_id FROM tasks WHERE created_by_user_id = ?
						  )
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.setLong(2, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM reevaluation_jobs
						WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_participations tp
						JOIN task_class_assignments a
						  ON a.task_class_assignment_id = tp.task_class_assignment_id
						SET tp.draft_base_submission_id = NULL
						WHERE a.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE tr FROM code_execution_test_results tr
						JOIN code_executions ce ON ce.execution_id = tr.execution_id
						JOIN task_participations tp ON tp.participation_id = ce.participation_id
						JOIN task_class_assignments a
						  ON a.task_class_assignment_id = tp.task_class_assignment_id
						WHERE a.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE cl FROM code_logs cl
						JOIN task_participations tp ON tp.participation_id = cl.participation_id
						JOIN task_class_assignments a
						  ON a.task_class_assignment_id = tp.task_class_assignment_id
						WHERE a.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE ce FROM code_executions ce
						JOIN task_participations tp ON tp.participation_id = ce.participation_id
						JOIN task_class_assignments a
						  ON a.task_class_assignment_id = tp.task_class_assignment_id
						WHERE a.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE s FROM submissions s
						JOIN task_participations tp ON tp.participation_id = s.participation_id
						JOIN task_class_assignments a
						  ON a.task_class_assignment_id = tp.task_class_assignment_id
						WHERE a.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE tp FROM task_participations tp
						JOIN task_class_assignments a
						  ON a.task_class_assignment_id = tp.task_class_assignment_id
						WHERE a.task_id IN (
						  SELECT task_id FROM tasks WHERE created_by_user_id = ?
						)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE tasks SET active_prompt_version_id = NULL WHERE created_by_user_id = ?
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM prompt_versions
						WHERE task_id IN (SELECT task_id FROM tasks WHERE created_by_user_id = ?)
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
				deleteTaskChildren(connection, "task_features");
				deleteTaskChildren(connection, "task_test_cases");
				deleteTaskChildren(connection, "task_hints");
				deleteTaskChildren(connection, "task_class_assignments");
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE tasks SET supersedes_task_id = NULL WHERE created_by_user_id = ?
						""")) {
					statement.setLong(1, teacherId);
					statement.executeUpdate();
				}
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
				try (PreparedStatement statement = connection.prepareStatement("""
						DELETE FROM research_subject_identifiers WHERE student_user_id = ?
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
						LateSubmissionPolicy.ALLOW)),
				schoolId);
	}

	private TeacherTaskInput inputWithAssignments(
			String title, List<ClassAssignmentInput> assignments) {
		TeacherTaskInput base = input(title);
		return new TeacherTaskInput(
				base.title(),
				base.theme(),
				base.difficulty(),
				base.description(),
				base.inputConstraints(),
				base.creationRules(),
				base.initialCode(),
				base.features(),
				base.testCases(),
				base.hints(),
				assignments,
				base.schoolId());
	}

	private void activateConfiguredPrompt(AuthenticatedUser owner, long taskId) throws SQLException {
		long promptVersionId = new TeacherPromptControl().saveDraft(
				owner, taskId, null, 0, "gemini-2.5-flash", "Synthetic publication prompt", null);
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE prompt_versions
					SET prompt_status = 'configured', fluctuation_generation_status = 'completed',
					    evaluation_examples_status = 'completed'
					WHERE prompt_version_id = ? AND task_id = ?
					""")) {
				statement.setLong(1, promptVersionId);
				statement.setLong(2, taskId);
				assertEquals(1, statement.executeUpdate());
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE tasks SET active_prompt_version_id = ? WHERE task_id = ?
					""")) {
				statement.setLong(1, promptVersionId);
				statement.setLong(2, taskId);
				assertEquals(1, statement.executeUpdate());
			}
			connection.commit();
		}
	}

	private void publishDraft(AuthenticatedUser owner, long taskId) throws SQLException {
		var draft = TASKS.loadPage(owner, taskId, schoolId).selectedTask();
		TASKS.publishTask(owner, taskId, draft.version(), draft.input(), UUID.randomUUID().toString());
	}

	private int processWhenSignaled(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Publication workers did not receive their start signal.");
		}
		return new TeacherTaskPublicationDao().processDueAssignments();
	}

	private void setAssignmentPublishTimeDue(long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET publish_at = CURRENT_TIMESTAMP - INTERVAL 1 SECOND
						WHERE task_class_assignment_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, assignmentId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private void setAssignmentDueTimePast(long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET due_at = CURRENT_TIMESTAMP - INTERVAL 1 SECOND
						WHERE task_class_assignment_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, assignmentId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private void setAssignmentDueTimeAfter(long assignmentId, int seconds) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET due_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP)
						WHERE task_class_assignment_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setInt(1, seconds);
			statement.setLong(2, assignmentId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private void awaitAssignmentDeadline(long assignmentId) throws SQLException, InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			try (Connection connection = Client.createConnection();
					PreparedStatement statement = connection.prepareStatement("""
							SELECT due_at <= CURRENT_TIMESTAMP
							FROM task_class_assignments WHERE task_class_assignment_id = ?
							""")) {
				assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
				statement.setLong(1, assignmentId);
				try (ResultSet rows = statement.executeQuery()) {
					if (!rows.next()) {
						throw new SQLException("Synthetic assignment deadline was not found.");
					}
					if (rows.getBoolean(1)) {
						return;
					}
				}
			}
			Thread.sleep(25);
		}
		throw new SQLException("Synthetic assignment deadline did not arrive within 10 seconds.");
	}

	private String findAssignmentStatus(long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT assignment_status FROM task_class_assignments
						WHERE task_class_assignment_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, assignmentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic task assignment was not found.");
				}
				return rows.getString(1);
			}
		}
	}

	private LocalDateTime findAssignmentDueAt(long assignmentId) throws SQLException {
		LocalDateTime dueAt = findAssignmentDateTime(assignmentId, "due_at");
		if (dueAt == null) {
			throw new SQLException("Synthetic task assignment has no due date.");
		}
		return dueAt;
	}

	private LocalDateTime findAssignmentDueAtOrNull(long assignmentId) throws SQLException {
		return findAssignmentDateTime(assignmentId, "due_at");
	}

	private LocalDateTime findAssignmentPublishAt(long assignmentId) throws SQLException {
		return findAssignmentDateTime(assignmentId, "publish_at");
	}

	private LocalDateTime findAssignmentDateTime(long assignmentId, String columnName) throws SQLException {
		if (!Set.of("due_at", "publish_at").contains(columnName)) {
			throw new IllegalArgumentException("Unsupported task assignment timestamp column.");
		}
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT " + columnName + " FROM task_class_assignments WHERE task_class_assignment_id = ?")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, assignmentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic task assignment was not found.");
				}
				java.sql.Timestamp value = rows.getTimestamp(1);
				return value == null ? null : value.toLocalDateTime();
			}
		}
	}

	private String findAssignmentLatePolicy(long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT late_submission_policy FROM task_class_assignments
						WHERE task_class_assignment_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, assignmentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic task assignment was not found.");
				}
				return rows.getString(1);
			}
		}
	}

	private int countHistoryRows(String tableName, String idColumn, long id) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM " + tableName + " WHERE " + idColumn + " = ?")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, id);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic history count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private int countTaskAssignmentRows(long taskId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM task_class_assignments WHERE task_id = ?")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Task assignment count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private int countTaskAuditActions(long taskId, String actionType) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT COUNT(*) FROM audit_logs
						WHERE feature_code = 'task-management' AND target_type = 'task'
						  AND target_id = ? AND action_type = ? AND result_status = 'success'
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, taskId);
			statement.setString(2, actionType);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Task audit count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private Long findActivePromptVersionId(long taskId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			return new TeacherPromptDao().findActivePromptVersionId(connection, taskId);
		}
	}

	private void setActivePromptCopyContent(long taskId, long promptVersionId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE prompt_versions
					SET common_prompt = 'Copied common prompt',
					    additional_evaluation_instruction = 'Copied task-specific instruction'
					WHERE task_id = ? AND prompt_version_id = ?
					""")) {
				statement.setLong(1, taskId);
				statement.setLong(2, promptVersionId);
				assertEquals(1, statement.executeUpdate());
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO prompt_fluctuation_items (
					  prompt_version_id, item_text, teacher_resolution, resolution_status, sort_order
					) VALUES (?, 'source-only fluctuation', 'source resolution', 'resolved', 1)
					""")) {
				statement.setLong(1, promptVersionId);
				assertEquals(1, statement.executeUpdate());
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO evaluation_examples (
					  prompt_version_id, example_input, example_output, example_status, created_at
					) VALUES (?, CAST(? AS JSON), CAST(? AS JSON), 'active', CURRENT_TIMESTAMP)
					""")) {
				statement.setLong(1, promptVersionId);
				statement.setString(2, "{}");
				statement.setString(3, "{}");
				assertEquals(1, statement.executeUpdate());
			}
			connection.commit();
		}
	}

	private static long findSubmissionId(Connection connection, long participationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT submission_id FROM submissions WHERE participation_id = ?")) {
			statement.setLong(1, participationId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic submission was not found.");
				}
				return rows.getLong("submission_id");
			}
		}
	}

	private int countAssignmentAudits(long assignmentId, String actionType) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT COUNT(*) FROM audit_logs
						WHERE actor_role = 'system' AND target_type = 'task_class_assignment'
						  AND target_id = ? AND action_type = ? AND result_status = 'success'
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, assignmentId);
			statement.setString(2, actionType);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic assignment audit count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private EditorSubmissionCheck submissionCheck(StudentEditorPage page, long studentId) {
		List<EditorSubmissionCheckCase> results = page.getTestCases().stream()
				.map(testCase -> new EditorSubmissionCheckCase(
						testCase.getTestCaseId(),
						testCase.getOrder(),
						testCase.getTitle(),
						testCase.getInput(),
						testCase.getExpectedOutput(),
						testCase.getExpectedOutput(),
						"",
						"succeeded",
						"matched",
						null,
						0,
						1,
						false,
						false))
				.toList();
		return new EditorSubmissionCheck(
				studentId,
				page.getAssignmentId(),
				page.getParticipationId(),
				page.getDraftUpdatedAtToken(),
				page.getCode(),
				System.currentTimeMillis(),
				results);
	}

	private int countStudentSubmissions(long studentId, long assignmentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT COUNT(*)
						FROM submissions s
						JOIN task_participations tp ON tp.participation_id = s.participation_id
						WHERE tp.student_user_id = ? AND tp.task_class_assignment_id = ?
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, studentId);
			statement.setLong(2, assignmentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic student submission count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
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
				assignments,
				schoolId);
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

	private List<String> findTaskAuditActions(long taskId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT action_type FROM audit_logs
						WHERE feature_code = 'task-management' AND target_type = 'task'
						  AND target_id = ? AND result_status = 'success'
						ORDER BY audit_log_id
						""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				List<String> actions = new ArrayList<>();
				while (rows.next()) {
					actions.add(rows.getString("action_type"));
				}
				return List.copyOf(actions);
			}
		}
	}

	private int countTaskRubric(long taskId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT COUNT(*)
						FROM tasks t
						JOIN rubrics r ON r.rubric_id = t.rubric_id
						WHERE t.task_id = ? AND r.title = ? AND r.version = ?
						  AND r.rubric_status = 'active'
						""")) {
			assertEquals(System.getenv("DB_NAME"), connection.getCatalog());
			statement.setLong(1, taskId);
			statement.setString(2, entity.StandardRubric.TITLE);
			statement.setString(3, entity.StandardRubric.VERSION);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Task rubric lookup returned no row.");
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

	private static long insertReevaluationJob(
			Connection connection,
			long taskId,
			long promptVersionId,
			long teacherId,
			String status,
			int targetCount,
			int completedCount,
			int progressPercent) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO reevaluation_jobs
					(task_id, prompt_version_id, requested_by_user_id, reevaluation_status,
					 target_count, completed_count, progress_percent)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, taskId);
			statement.setLong(2, promptVersionId);
			statement.setLong(3, teacherId);
			statement.setString(4, status);
			statement.setInt(5, targetCount);
			statement.setInt(6, completedCount);
			statement.setInt(7, progressPercent);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Reevaluation job fixture ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertSyntheticInitialEvaluation(
			Connection connection, long taskId, long submissionId) throws SQLException {
		long rubricId;
		long promptVersionId;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT rubric_id, active_prompt_version_id
				FROM tasks WHERE task_id = ?
				""")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic evaluation task was not found.");
				}
				rubricId = rows.getLong("rubric_id");
				promptVersionId = rows.getLong("active_prompt_version_id");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluations (
				  evaluation_code, submission_id, rubric_id, prompt_version_id,
				  evaluation_status, evaluation_kind, format_version, locale,
				  auto_save_count, execution_count, overall_score, created_at, completed_at
				)
				VALUES (?, ?, ?, ?, 'completed', 'initial', '1.0.0', 'ja-JP',
				        0, 0, 3.500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, UUID.randomUUID().toString());
			statement.setLong(2, submissionId);
			statement.setLong(3, rubricId);
			statement.setLong(4, promptVersionId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic evaluation ID was not generated.");
				}
				return keys.getLong(1);
			}
		}
	}

	private static long insertSyntheticReevaluationResult(
			Connection connection, long taskId, long promptVersionId, long jobId, long submissionId)
			throws SQLException {
		long rubricId;
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT rubric_id FROM tasks WHERE task_id = ?")) {
			statement.setLong(1, taskId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic evaluation task was not found.");
				}
				rubricId = rows.getLong("rubric_id");
			}
		}
		long evaluationId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO evaluations (
				  evaluation_code, submission_id, reevaluation_job_id, rubric_id, prompt_version_id,
				  evaluation_status, evaluation_kind, format_version, locale, overall_score,
				  auto_save_count, execution_count, created_at, completed_at
				)
				VALUES (?, ?, ?, ?, ?, 'completed', 'reevaluation', '1.0.0', 'ja-JP', 3.500,
				        0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, UUID.randomUUID().toString());
			statement.setLong(2, submissionId);
			statement.setLong(3, jobId);
			statement.setLong(4, rubricId);
			statement.setLong(5, promptVersionId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic evaluation ID was not generated.");
				}
				evaluationId = keys.getLong(1);
			}
		}
		for (var score : List.of(
				new DimensionScore("thinking", new java.math.BigDecimal("4.000")),
				new DimensionScore("attitude", new java.math.BigDecimal("3.000")))) {
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO evaluation_dimension_results (evaluation_id, dimension_id, score_value)
					SELECT ?, dimension_id, ? FROM rubric_dimensions
					WHERE rubric_id = ? AND dimension_code = ?
					""")) {
				statement.setLong(1, evaluationId);
				statement.setBigDecimal(2, score.value());
				statement.setLong(3, rubricId);
				statement.setString(4, score.code());
				assertEquals(1, statement.executeUpdate());
			}
		}
		return evaluationId;
	}

	private static String studentLoginId(long studentId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT login_id FROM users WHERE user_id = ?")) {
			statement.setLong(1, studentId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Synthetic student was not found.");
				}
				return rows.getString("login_id");
			}
		}
	}

	private record DimensionScore(String code, java.math.BigDecimal value) {}

	private long insertParticipationAndAcceptedSubmission(
			Connection connection, long studentId, long assignmentId) throws SQLException {
		long participationId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO task_participations
					(student_user_id, task_class_assignment_id, learning_status, progress_status,
					 save_status, evaluation_status, active_duration_seconds)
				VALUES (?, ?, 'completed', 'submitted', 'saved', 'not_started', 0)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, studentId);
			statement.setLong(2, assignmentId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) {
					throw new SQLException("Synthetic participation ID was not generated.");
				}
				participationId = keys.getLong(1);
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO submissions
					(participation_id, revision_number, submitted_code, submission_status,
					 submitted_at, created_at)
				VALUES (?, 1, 'print(1)', 'accepted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, participationId);
			assertEquals(1, statement.executeUpdate());
		}
		return participationId;
	}

	private void setAcceptanceFixturePassword() throws SQLException {
		String passwordHash = new PasswordHasher().hash("T031-preview-only".toCharArray());
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"UPDATE users SET password_hash = ? WHERE user_id = ?")) {
			statement.setString(1, passwordHash);
			statement.setLong(2, teacherId);
			assertEquals(1, statement.executeUpdate());
		}
	}

	private static int countJobTargets(long jobId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM reevaluation_job_targets WHERE reevaluation_job_id = ?")) {
			statement.setLong(1, jobId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) {
					throw new SQLException("Reevaluation target count query returned no row.");
				}
				return rows.getInt(1);
			}
		}
	}

	private static EvaluationProvider syntheticProvider() {
		return new EvaluationProvider() {
			@Override
			public com.google.gson.JsonObject generate(String modelId, com.google.gson.JsonObject payload) {
				com.google.gson.JsonObject response = new com.google.gson.JsonObject();
				response.addProperty("synthetic", true);
				return response;
			}

			@Override
			public String extractOutputText(com.google.gson.JsonObject response) {
				return """
						{
						  "scores": {
						    "thinking_expression_level": 4,
						    "proactive_attitude_level": 3
						  },
						  "reasons": {
						    "thinking_expression_reason": "Synthetic evidence supports the score.",
						    "proactive_attitude_reason": "Synthetic evidence supports the score."
						  },
						  "process_analysis": {
						    "pattern_label": "iterative",
						    "turning_points": [],
						    "stagnation_points": [],
						    "teacher_support_suggestions": []
						  },
						  "confidence": 0.9,
						  "evidence_refs": [],
						  "warnings": []
						}
						""";
			}
		};
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
