package control.teacher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import control.auth.AuthenticatedUser;
import control.evaluation.EvaluationProviderException;
import dao.StandardRubricDao;
import dao.TeacherPermissionDao;
import dao.TeacherPromptDao;
import dao.TeacherTaskDao;
import entity.StandardRubric;
import entity.TeacherPromptPage;
import entity.TeacherPromptVersion;
import entity.TeacherPromptVersion.FluctuationItem;
import entity.TeacherTaskDetails;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherPromptControl {
	private static final int MAX_COMMON_PROMPT_LENGTH = 120_000;
	private static final int MAX_ADDITIONAL_INSTRUCTION_LENGTH = 4_000;
	private final TeacherPermissionDao permissionDao;
	private final TeacherTaskDao taskDao;
	private final TeacherPromptDao promptDao;
	private final StandardRubricDao rubricDao;
	private final TeacherPromptAiClient aiClient;
	private final ConnectionFactory connectionFactory;

	public TeacherPromptControl() {
		this(new TeacherPermissionDao(), new TeacherTaskDao(), new TeacherPromptDao(),
				new StandardRubricDao(), new TeacherPromptAiClient(), Client::createConnection);
	}

	TeacherPromptControl(
			TeacherPermissionDao permissionDao,
			TeacherTaskDao taskDao,
			TeacherPromptDao promptDao,
			StandardRubricDao rubricDao,
			TeacherPromptAiClient aiClient,
			ConnectionFactory connectionFactory) {
		this.permissionDao = Objects.requireNonNull(permissionDao);
		this.taskDao = Objects.requireNonNull(taskDao);
		this.promptDao = Objects.requireNonNull(promptDao);
		this.rubricDao = Objects.requireNonNull(rubricDao);
		this.aiClient = Objects.requireNonNull(aiClient);
		this.connectionFactory = Objects.requireNonNull(connectionFactory);
	}

	public TeacherPromptPage loadPage(AuthenticatedUser user, Long selectedTaskId, Long selectedVersionId)
			throws SQLException {
		requireTeacher(user);
		requireOptionalPositiveId(selectedTaskId, "課題");
		requireOptionalPositiveId(selectedVersionId, "プロンプト版");
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				permissionDao.requireTaskManagementAccess(connection, user.userId());
				List<TeacherTaskDetails> tasks = taskDao.findDrafts(connection, user.userId());
				requireAuthorizedClasses(connection, user.userId(), tasks);
				if (selectedTaskId == null) {
					connection.commit();
					return new TeacherPromptPage(tasks, null, null, List.of(), null, null, "", List.of());
				}
				TeacherTaskDetails selected = taskDao.findDraft(connection, user.userId(), selectedTaskId, true)
						.orElseThrow(TeacherPromptDao.TeacherTaskNotFoundException::new);
				requireAuthorizedClasses(connection, user.userId(), List.of(selected));
				promptDao.ensureStandardRubric(connection, user.userId(), selectedTaskId);
				selected = taskDao.findDraft(connection, user.userId(), selectedTaskId, false)
						.orElseThrow(TeacherPromptDao.TeacherTaskNotFoundException::new);
				StandardRubric rubric = rubricDao.find(connection)
						.orElseThrow(() -> new SQLException("The active standard rubric is not available."));
				Long activeVersionId = promptDao.findActivePromptVersionId(connection, selectedTaskId);
				List<TeacherPromptVersion> versions = promptDao.findVersions(connection, selectedTaskId);
				TeacherPromptVersion selectedVersion = chooseVersion(versions, selectedVersionId, activeVersionId);
				String targets = promptDao.findTargetSummary(connection, selectedTaskId);
				var auditEntries = promptDao.findAuditEntries(connection, user.userId(), selectedTaskId);
				connection.commit();
				return new TeacherPromptPage(
						tasks, selected, activeVersionId, versions, selectedVersion, rubric, targets, auditEntries);
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public long saveDraft(
			AuthenticatedUser user,
			long taskId,
			Long promptVersionId,
			long expectedRowVersion,
			String modelId,
			String commonPrompt,
			String additionalInstruction) throws SQLException {
		requireTeacher(user);
		validatePositiveId(taskId, "課題");
		if (promptVersionId != null) {
			validatePositiveId(promptVersionId, "プロンプト版");
		}
		validatePrompt(modelId, commonPrompt, additionalInstruction);
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				permissionDao.requireTaskManagementAccess(connection, user.userId());
				TeacherTaskDetails task = taskDao.findDraft(connection, user.userId(), taskId, true)
						.orElseThrow(TeacherPromptDao.TeacherTaskNotFoundException::new);
				requireAuthorizedClasses(connection, user.userId(), List.of(task));
				long savedId = promptDao.saveDraft(connection, user.userId(), taskId, promptVersionId,
						expectedRowVersion, modelId, commonPrompt, additionalInstruction);
				connection.commit();
				return savedId;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public TeacherPromptVersion saveResolutions(
			AuthenticatedUser user,
			long taskId,
			long promptVersionId,
			long expectedRowVersion,
			List<FluctuationItem> updates,
			String additionalInstruction) throws SQLException {
		requireTeacher(user);
		validatePositiveId(taskId, "課題");
		validatePositiveId(promptVersionId, "プロンプト版");
		if (updates == null || updates.size() > 20
				|| additionalInstruction != null && additionalInstruction.length() > MAX_ADDITIONAL_INSTRUCTION_LENGTH) {
			throw new IllegalArgumentException("揺らぎ項目の入力を確認してください。");
		}
		for (FluctuationItem item : updates) {
			if (item == null || item.teacherResolution() != null && item.teacherResolution().length() > 4_000) {
				throw new IllegalArgumentException("教師対応の入力が長すぎます。");
			}
		}
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				requireTaskAccess(connection, user, taskId);
				promptDao.saveResolutions(connection, user.userId(), taskId, promptVersionId,
						expectedRowVersion, updates, additionalInstruction);
				TeacherPromptVersion saved = promptDao.findVersion(connection, taskId, promptVersionId);
				connection.commit();
				return saved;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	public TeacherPromptVersion generateFluctuations(
			AuthenticatedUser user,
			long taskId,
			long promptVersionId,
			long expectedRowVersion) throws SQLException, EvaluationProviderException {
		requireTeacher(user);
		validatePositiveId(taskId, "課題");
		validatePositiveId(promptVersionId, "プロンプト版");
		GenerationContext context = beginGeneration(user, taskId, promptVersionId, expectedRowVersion, "fluctuation");
		try {
			List<FluctuationItem> items = aiClient.generateFluctuations(
					context.version().aiModel(),
					taskContext(context.task()),
					context.version().commonPrompt(),
					context.version().additionalInstruction(),
					context.rubric());
			try (Connection connection = connectionFactory.open()) {
				connection.setAutoCommit(false);
				try {
					requireTaskAccess(connection, user, taskId);
					promptDao.completeFluctuations(connection, user.userId(), taskId, promptVersionId,
							context.startedRowVersion(), items);
					TeacherPromptVersion completed = promptDao.findVersion(connection, taskId, promptVersionId);
					connection.commit();
					return completed;
				} catch (SQLException | RuntimeException failure) {
					rollback(connection, failure);
					throw failure;
				}
			}
		} catch (EvaluationProviderException | IllegalArgumentException generationFailure) {
			markGenerationFailed(user.userId(), taskId, promptVersionId, context.startedRowVersion(), "fluctuation",
					generationFailure);
			throw generationFailure;
		}
	}

	public TeacherPromptVersion generateEvaluationExamples(
			AuthenticatedUser user,
			long taskId,
			long promptVersionId,
			long expectedRowVersion) throws SQLException, EvaluationProviderException {
		requireTeacher(user);
		validatePositiveId(taskId, "課題");
		validatePositiveId(promptVersionId, "プロンプト版");
		GenerationContext context = beginGeneration(user, taskId, promptVersionId, expectedRowVersion, "examples");
		JsonObject contextJson = taskContext(context.task());
		JsonArray syntheticSamples = TeacherPromptExampleFactory.createSyntheticSamples(contextJson);
		JsonObject syntheticInput = new JsonObject();
		syntheticInput.add("task_context", contextJson);
		syntheticInput.add("synthetic_submission_samples", syntheticSamples.deepCopy());
		syntheticInput.addProperty("sample_origin", "synthetic_teacher_prompt_fixture");
		try {
			JsonObject output = aiClient.generateEvaluationExamples(
					context.version().aiModel(),
					contextJson,
					context.version().commonPrompt(),
					context.version().additionalInstruction(),
					context.rubric(),
					context.version().fluctuationItems(),
					syntheticSamples);
			try (Connection connection = connectionFactory.open()) {
				connection.setAutoCommit(false);
				try {
					requireTaskAccess(connection, user, taskId);
					promptDao.completeExamples(connection, user.userId(), taskId, promptVersionId,
							context.startedRowVersion(), syntheticInput, output);
					TeacherPromptVersion generated = promptDao.findVersion(connection, taskId, promptVersionId);
					connection.commit();
					return generated;
				} catch (SQLException | RuntimeException failure) {
					rollback(connection, failure);
					throw failure;
				}
			}
		} catch (EvaluationProviderException | IllegalArgumentException generationFailure) {
			markGenerationFailed(user.userId(), taskId, promptVersionId, context.startedRowVersion(), "examples",
					generationFailure);
			throw generationFailure;
		}
	}

	public TeacherPromptVersion saveEvaluationExamples(
			AuthenticatedUser user,
			long taskId,
			long promptVersionId,
			long expectedRowVersion) throws SQLException {
		requireTeacher(user);
		validatePositiveId(taskId, "課題");
		validatePositiveId(promptVersionId, "プロンプト版");
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				requireTaskAccess(connection, user, taskId);
				promptDao.saveEvaluationExamples(connection, user.userId(), taskId, promptVersionId,
						expectedRowVersion);
				TeacherPromptVersion saved = promptDao.findVersion(connection, taskId, promptVersionId);
				connection.commit();
				return saved;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private GenerationContext beginGeneration(
			AuthenticatedUser user,
			long taskId,
			long promptVersionId,
			long expectedRowVersion,
			String stage) throws SQLException {
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				requireTaskAccess(connection, user, taskId);
				promptDao.ensureStandardRubric(connection, user.userId(), taskId);
				TeacherTaskDetails task = taskDao.findDraft(connection, user.userId(), taskId, true)
						.orElseThrow(TeacherPromptDao.TeacherTaskNotFoundException::new);
				rubricDao.requireActiveId(connection);
				StandardRubric rubric = rubricDao.find(connection)
						.orElseThrow(() -> new SQLException("The active standard rubric is not available."));
				TeacherPromptVersion version = promptDao.findVersion(connection, taskId, promptVersionId);
				if (!"draft".equals(version.promptStatus())) {
					throw new TeacherPromptDao.PromptVersionConflictException();
				}
				if ("examples".equals(stage)
						&& (!"completed".equals(version.fluctuationGenerationStatus())
								|| version.fluctuationItems().isEmpty()
								|| version.fluctuationItems().stream()
										.anyMatch(item -> "pending".equals(item.resolutionStatus())))) {
					throw new IllegalStateException("揺らぎ項目への教師対応を完了してから評価例を生成してください。");
				}
				long startedRowVersion = promptDao.beginGeneration(connection, user.userId(), taskId,
						promptVersionId, expectedRowVersion, stage);
				connection.commit();
				return new GenerationContext(task, version, rubric, startedRowVersion);
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				throw failure;
			}
		}
	}

	private void requireTaskAccess(Connection connection, AuthenticatedUser user, long taskId) throws SQLException {
		permissionDao.requireTaskManagementAccess(connection, user.userId());
		TeacherTaskDetails task = taskDao.findDraft(connection, user.userId(), taskId, true)
				.orElseThrow(TeacherPromptDao.TeacherTaskNotFoundException::new);
		requireAuthorizedClasses(connection, user.userId(), List.of(task));
	}

	private void markGenerationFailed(
			long teacherUserId,
			long taskId,
			long promptVersionId,
			long startedRowVersion,
			String stage,
			Exception originalFailure) throws SQLException {
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				promptDao.failGeneration(connection, teacherUserId, taskId, promptVersionId,
						startedRowVersion, stage);
				connection.commit();
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				failure.addSuppressed(originalFailure);
				throw failure;
			}
		}
	}

	private void requireAuthorizedClasses(
			Connection connection,
			long teacherUserId,
			List<TeacherTaskDetails> tasks) throws SQLException {
		for (TeacherTaskDetails task : tasks) {
			for (var assignment : task.input().classAssignments()) {
				permissionDao.requireAuthorizedClass(connection, teacherUserId, assignment.classroomId());
			}
		}
	}

	private static TeacherPromptVersion chooseVersion(
			List<TeacherPromptVersion> versions,
			Long selectedVersionId,
			Long activeVersionId) {
		if (selectedVersionId != null) {
			return versions.stream()
					.filter(version -> version.promptVersionId() == selectedVersionId)
					.findFirst()
					.orElseThrow(TeacherPromptDao.PromptVersionNotFoundException::new);
		}
		if (activeVersionId != null) {
			var active = versions.stream()
					.filter(version -> version.promptVersionId() == activeVersionId)
					.findFirst();
			if (active.isPresent()) {
				return active.get();
			}
		}
		return versions.stream().filter(version -> "draft".equals(version.promptStatus())).findFirst()
				.orElse(versions.isEmpty() ? null : versions.getFirst());
	}

	private static JsonObject taskContext(TeacherTaskDetails task) {
		JsonObject context = new JsonObject();
		context.addProperty("title", task.input().title());
		context.addProperty("theme", task.input().theme());
		context.addProperty("difficulty", task.input().difficulty() == null
				? "" : task.input().difficulty().databaseValue());
		context.addProperty("description", task.input().description());
		context.addProperty("input_constraints", task.input().inputConstraints());
		context.addProperty("creation_rules", task.input().creationRules());
		context.addProperty("initial_code", task.input().initialCode());
		JsonArray features = new JsonArray();
		task.input().features().forEach(features::add);
		context.add("features", features);
		JsonArray tests = new JsonArray();
		task.input().testCases().forEach(test -> {
			JsonObject testCase = new JsonObject();
			testCase.addProperty("title", test.getTitle());
			testCase.addProperty("input", test.getInput());
			testCase.addProperty("expected_output", test.getExpectedOutput());
			tests.add(testCase);
		});
		context.add("test_cases", tests);
		return context;
	}

	private static void validatePrompt(String modelId, String commonPrompt, String additionalInstruction) {
		if (modelId == null
				|| !List.of("gemini-2.5-pro", "gemini-2.5-flash").contains(modelId)
				|| commonPrompt == null || commonPrompt.isBlank() || commonPrompt.length() > MAX_COMMON_PROMPT_LENGTH
				|| additionalInstruction != null
						&& additionalInstruction.length() > MAX_ADDITIONAL_INSTRUCTION_LENGTH) {
			throw new IllegalArgumentException("プロンプトとAIモデルの入力を確認してください。");
		}
	}

	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.TEACHER || user.passwordChangeRequired()) {
			throw new SecurityException("Active teacher access is required.");
		}
	}

	private static void validatePositiveId(Long id, String label) {
		if (id == null || id < 1) {
			throw new IllegalArgumentException(label + "IDが不正です。");
		}
	}

	private static void requireOptionalPositiveId(Long id, String label) {
		if (id != null) {
			validatePositiveId(id, label);
		}
	}

	private static void rollback(Connection connection, Exception failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
	}

	private record GenerationContext(
			TeacherTaskDetails task,
			TeacherPromptVersion version,
			StandardRubric rubric,
			long startedRowVersion) {}

	@FunctionalInterface
	interface ConnectionFactory {
		Connection open() throws SQLException;
	}
}
