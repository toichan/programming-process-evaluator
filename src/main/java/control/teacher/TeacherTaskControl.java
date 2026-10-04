package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.UUID;

import control.auth.AuthenticatedUser;
import dao.TeacherPermissionDao;
import dao.TeacherTaskDao;
import entity.EditorHint;
import entity.EditorTestCase;
import entity.TeacherClassOption;
import entity.TeacherHintOption;
import entity.TeacherSchoolOption;
import entity.TeacherTaskAuditEntry;
import entity.TeacherTaskDetails;
import entity.TeacherTaskInput;
import entity.TeacherTaskInput.ClassAssignmentInput;
import entity.TeacherTaskInput.HintInput;
import entity.TeacherTaskPage;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherTaskControl {
	private static final Logger LOGGER = Logger.getLogger(TeacherTaskControl.class.getName());
	private final TeacherPermissionDao permissionDao;
	private final TeacherTaskDao taskDao;
	private final TeacherTaskInputValidator inputValidator;
	private final ConnectionFactory connectionFactory;

	public TeacherTaskControl() {
		this(new TeacherPermissionDao(), new TeacherTaskDao(), Client::createConnection);
	}

	TeacherTaskControl(
			TeacherPermissionDao permissionDao,
			TeacherTaskDao taskDao,
			ConnectionFactory connectionFactory) {
		this.permissionDao = java.util.Objects.requireNonNull(permissionDao);
		this.taskDao = java.util.Objects.requireNonNull(taskDao);
		this.inputValidator = new TeacherTaskInputValidator();
		this.connectionFactory = java.util.Objects.requireNonNull(connectionFactory);
	}

	public TeacherTaskPage loadPage(
			AuthenticatedUser user,
			Long selectedTaskId,
			Long selectedSchoolId) throws SQLException {
		requireTeacher(user);
		requireOptionalPositiveId(selectedTaskId, "課題");
		requireOptionalPositiveId(selectedSchoolId, "学校");

		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				List<TeacherSchoolOption> schools = permissionDao.findAuthorizedSchools(connection, user.userId());
				List<TeacherClassOption> classes = new ArrayList<>();
				for (TeacherSchoolOption school : schools) {
					if (selectedSchoolId == null || selectedSchoolId == school.schoolId()) {
						classes.addAll(permissionDao.findAuthorizedClasses(
								connection, user.userId(), school.schoolId()));
					}
				}
				List<TeacherTaskDetails> tasks = taskDao.findDrafts(connection, user.userId());
				requireAuthorizedDraftClasses(connection, user.userId(), tasks);
				List<TeacherHintOption> reusableHints = taskDao.findReusableHints(connection, user.userId());
				TeacherTaskDetails selectedTask = null;
				List<TeacherTaskAuditEntry> auditEntries = List.of();
				if (selectedTaskId != null) {
					selectedTask = taskDao.findDraft(connection, user.userId(), selectedTaskId, false)
							.orElseThrow(TaskDraftNotFoundException::new);
					auditEntries = taskDao.findAuditEntries(connection, user.userId(), selectedTaskId);
				}
				connection.commit();
				return new TeacherTaskPage(tasks, schools, classes, reusableHints, selectedTask, auditEntries);
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public long createDraft(AuthenticatedUser user, TeacherTaskInput input, String requestId) throws SQLException {
		return saveDraft(user, 0, 0, input, requestId);
	}

	public void updateDraft(
			AuthenticatedUser user,
			long taskId,
			long expectedVersion,
			TeacherTaskInput input,
			String requestId) throws SQLException {
		saveDraft(user, taskId, expectedVersion, input, requestId);
	}

	public List<TeacherTaskAuditEntry> loadAuditEntries(AuthenticatedUser user, long taskId) throws SQLException {
		requireTeacher(user);
		requirePositiveId(taskId, "課題");
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				permissionDao.findAuthorizedSchools(connection, user.userId());
				TeacherTaskDetails task = taskDao.findDraft(connection, user.userId(), taskId, false)
						.orElseThrow(TaskDraftNotFoundException::new);
				requireAuthorizedAssignments(connection, user.userId(), task, task.input());
				List<TeacherTaskAuditEntry> entries = taskDao.findAuditEntries(connection, user.userId(), taskId);
				connection.commit();
				return entries;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private long saveDraft(
			AuthenticatedUser user,
			long taskId,
			long expectedVersion,
			TeacherTaskInput input,
			String requestId) throws SQLException {
		requireTeacher(user);
		input = inputValidator.validateAndNormalize(input);
		validateRequestId(requestId);
		if (taskId < 0 || (taskId == 0 && expectedVersion != 0)
				|| (taskId > 0 && expectedVersion < 1)) {
			throw new IllegalArgumentException("課題または更新情報が不正です。画面を読み込み直してください。");
		}

		String action = taskId == 0 ? "create_draft" : "update_draft";
		Long auditTargetId = taskId == 0 ? null : taskId;
		Connection connection = null;
		boolean committed = false;
		boolean commitAttempted = false;
		long savedTaskId = 0;
		Exception operationFailure = null;
		try {
			connection = connectionFactory.open();
			connection.setAutoCommit(false);
			permissionDao.requireTaskManagementAccess(connection, user.userId());
			if (taskId == 0) {
				Optional<Long> priorCreation = taskDao.findSuccessfulDraftCreation(
						connection, user.userId(), requestId);
				if (priorCreation.isPresent()) {
					savedTaskId = priorCreation.get();
					TeacherTaskDetails existing = taskDao.findDraft(connection, user.userId(), savedTaskId, true)
							.orElseThrow(TaskDraftNotFoundException::new);
					requireAuthorizedAssignments(connection, user.userId(), existing, existing.input());
					auditTargetId = savedTaskId;
					commitAttempted = true;
					connection.commit();
					committed = true;
				}
			}
			if (!committed) {
			TeacherTaskDetails before = taskId == 0
					? null
					: taskDao.findDraft(connection, user.userId(), taskId, true)
							.orElseThrow(TaskDraftNotFoundException::new);
			requireAuthorizedAssignments(connection, user.userId(), before, input);

			if (before == null) {
				savedTaskId = taskDao.insertDraft(connection, user.userId(), input);
			} else {
				if (before.version() != expectedVersion) {
					throw new TaskDraftConflictException();
				}
				if (before.version() == Long.MAX_VALUE) {
					throw new IllegalStateException("Task draft version has reached its maximum value.");
				}
				taskDao.updateDraft(connection, user.userId(), taskId, expectedVersion, input);
				savedTaskId = taskId;
			}
			auditTargetId = savedTaskId;
			List<String> changedFields = before == null
					? List.of("課題", "実装機能", "テストケース", "ヒント", "クラス割当")
					: changedFields(before.input(), input);
			taskDao.recordAudit(
					connection,
					user.userId(),
					savedTaskId,
					action,
					requestId,
					before == null ? "課題下書きを作成" : String.join("、", changedFields),
					before == null ? null : versionJson(before.version()),
					versionJson(before == null ? 1 : before.version() + 1));
			commitAttempted = true;
			connection.commit();
			committed = true;
			}
		} catch (SQLException | RuntimeException failure) {
			operationFailure = failure;
			if (connection != null) {
				rollback(connection, failure);
			}
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException | RuntimeException closeFailure) {
					if (committed) {
						LOGGER.log(Level.SEVERE, "Task draft committed, but closing its connection failed.", closeFailure);
					} else if (operationFailure == null) {
						operationFailure = closeFailure;
					} else {
						operationFailure.addSuppressed(closeFailure);
						LOGGER.log(Level.SEVERE, "Unable to close the failed task draft transaction.", closeFailure);
					}
				}
			}
		}
		if (operationFailure != null) {
			if (!committed) {
				if (commitAttempted) {
					LOGGER.log(Level.SEVERE,
							"Task draft commit outcome is uncertain; a success audit may already exist for this request.",
							operationFailure);
				} else {
					recordFailureAudit(user.userId(), auditTargetId, action, requestId, operationFailure);
				}
			}
			if (operationFailure instanceof SQLException sqlFailure) {
				throw sqlFailure;
			}
			throw (RuntimeException) operationFailure;
		}
		return savedTaskId;
	}

	private void requireAuthorizedAssignments(
			Connection connection,
			long teacherUserId,
			TeacherTaskDetails before,
			TeacherTaskInput input) throws SQLException {
		Set<Long> classroomIds = new HashSet<>();
		if (before != null) {
			for (ClassAssignmentInput assignment : before.input().classAssignments()) {
				classroomIds.add(assignment.classroomId());
			}
		}
		for (ClassAssignmentInput assignment : input.classAssignments()) {
			classroomIds.add(assignment.classroomId());
		}
		for (long classroomId : classroomIds) {
			permissionDao.requireAuthorizedClass(connection, teacherUserId, classroomId);
		}
	}

	private void requireAuthorizedDraftClasses(
			Connection connection,
			long teacherUserId,
			List<TeacherTaskDetails> drafts) throws SQLException {
		Set<Long> classroomIds = new HashSet<>();
		for (TeacherTaskDetails draft : drafts) {
			for (ClassAssignmentInput assignment : draft.input().classAssignments()) {
				classroomIds.add(assignment.classroomId());
			}
		}
		for (long classroomId : classroomIds) {
			permissionDao.requireAuthorizedClass(connection, teacherUserId, classroomId);
		}
	}

	private void recordFailureAudit(
			long teacherUserId,
			Long taskId,
			String action,
			String requestId,
			Exception originalFailure) {
		FailureDescription description = describeFailure(originalFailure);
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				taskDao.recordFailureAudit(
						connection,
						teacherUserId,
						taskId,
						action,
						requestId,
						description.code(),
						description.message());
				connection.commit();
			} catch (SQLException | RuntimeException auditFailure) {
				rollback(connection, auditFailure);
				LOGGER.log(Level.SEVERE, "Unable to persist task draft failure audit.", auditFailure);
				originalFailure.addSuppressed(auditFailure);
			}
		} catch (SQLException | RuntimeException auditFailure) {
			LOGGER.log(Level.SEVERE, "Unable to open or close the task draft failure-audit transaction.", auditFailure);
			originalFailure.addSuppressed(auditFailure);
		}
	}

	private static List<String> changedFields(TeacherTaskInput before, TeacherTaskInput after) {
		List<String> changed = new ArrayList<>();
		if (!java.util.Objects.equals(before.title(), after.title())) changed.add("課題名");
		if (!java.util.Objects.equals(before.theme(), after.theme())) changed.add("テーマ");
		if (before.difficulty() != after.difficulty()) changed.add("難易度");
		if (!java.util.Objects.equals(before.description(), after.description())) changed.add("説明");
		if (!java.util.Objects.equals(before.inputConstraints(), after.inputConstraints())) changed.add("入力制約");
		if (!java.util.Objects.equals(before.creationRules(), after.creationRules())) changed.add("作成時のルール");
		if (!java.util.Objects.equals(before.initialCode(), after.initialCode())) changed.add("初期コード");
		if (!before.features().equals(after.features())) changed.add("実装機能");
		if (!testCases(before.testCases()).equals(testCases(after.testCases()))) changed.add("テストケース");
		if (!hints(before.hints()).equals(hints(after.hints()))) changed.add("ヒント");
		if (!assignments(before.classAssignments()).equals(assignments(after.classAssignments()))) changed.add("クラス割当");
		return changed.isEmpty() ? List.of("変更なし") : List.copyOf(changed);
	}

	private static List<TestCaseSnapshot> testCases(List<EditorTestCase> cases) {
		return cases.stream().map(testCase -> new TestCaseSnapshot(
				testCase.getTitle(), testCase.getInput(), testCase.getExpectedOutput())).toList();
	}

	private static List<HintSnapshot> hints(List<HintInput> hints) {
		return hints.stream().map(hint -> new HintSnapshot(
				hint.hint().getTitle(),
				hint.hint().getContent(),
				hint.hint().getUsageSyntax(),
				hint.hint().getCode())).toList();
	}

	private static List<AssignmentSnapshot> assignments(List<ClassAssignmentInput> assignments) {
		return assignments.stream().map(assignment -> new AssignmentSnapshot(
				assignment.classroomId(),
				assignment.publishAt(),
				assignment.dueAt(),
				assignment.lateSubmissionPolicy())).toList();
	}

	private static FailureDescription describeFailure(Exception failure) {
		if (failure instanceof TaskDraftConflictException) {
			return new FailureDescription("VERSION_CONFLICT", "課題が更新されています。再読み込みしてください。");
		}
		if (failure instanceof SecurityException) {
			return new FailureDescription("ACCESS_DENIED", "課題操作の権限を確認できませんでした。");
		}
		if (failure instanceof IllegalArgumentException) {
			return new FailureDescription("VALIDATION_FAILED", "入力または課題状態を確認してください。");
		}
		if (failure instanceof SQLException) {
			return new FailureDescription("DATABASE_ERROR", "データベース処理に失敗しました。");
		}
		return new FailureDescription("OPERATION_FAILED", "課題の保存に失敗しました。");
	}

	private static String versionJson(long version) {
		return "{\"version\":" + version + "}";
	}

	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userId() < 1 || user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication is required.");
		}
	}

	private static void requirePositiveId(long id, String label) {
		if (id < 1) {
			throw new IllegalArgumentException("有効な" + label + "IDを指定してください。");
		}
	}

	private static void requireOptionalPositiveId(Long id, String label) {
		if (id != null) {
			requirePositiveId(id, label);
		}
	}

	private static void validateRequestId(String requestId) {
		if (requestId == null || !requestId.matches(
				"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			throw new IllegalArgumentException("保存tokenが不正です。画面を読み込み直してください。");
		}
		UUID.fromString(requestId);
	}

	private static void rollback(Connection connection, Exception originalFailure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			originalFailure.addSuppressed(rollbackFailure);
			LOGGER.log(Level.SEVERE, "Unable to roll back the task draft transaction.", rollbackFailure);
		}
	}

	@FunctionalInterface
	interface ConnectionFactory {
		Connection open() throws SQLException;
	}

	private record FailureDescription(String code, String message) {
	}

	private record TestCaseSnapshot(String title, String input, String expectedOutput) {
	}

	private record HintSnapshot(String title, String content, String usageSyntax, String code) {
	}

	private record AssignmentSnapshot(
			long classroomId,
			java.time.LocalDateTime publishAt,
			java.time.LocalDateTime dueAt,
			TeacherTaskInput.LateSubmissionPolicy lateSubmissionPolicy) {
	}
}
