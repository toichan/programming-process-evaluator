package control.student;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import control.auth.AuthenticatedUser;
import dao.StudentEditorPreferencesDao;
import dao.StudentExerciseDao;
import entity.ExerciseSaveResult;
import entity.ExerciseConflictException;
import entity.ExerciseBatchInput;
import entity.ExerciseBatchResult;
import entity.ExerciseExecutionResult;
import entity.ExerciseInteractiveResult;
import entity.ExerciseNotFoundException;
import entity.InteractiveExecutionUpdate;
import entity.PythonExecutionResult;
import entity.StudentExerciseEntry;
import entity.StudentExerciseInput;
import entity.StudentExercisePage;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class StudentExerciseControl {
	private static final Logger LOGGER = Logger.getLogger(StudentExerciseControl.class.getName());
	private final ConcurrentHashMap<String, InteractiveExecution> sessions = new ConcurrentHashMap<>();
	private final StudentExerciseDao dao = new StudentExerciseDao();
	private final dao.StudentExerciseUnificationDao unification = new dao.StudentExerciseUnificationDao();
	private final StudentEditorPreferencesDao preferences = new StudentEditorPreferencesDao();
	private final ExecutionRunner runner;

	public StudentExerciseControl() {
		this((code, input) -> new PythonRunnerClient().executeTimed(code, input));
	}

	StudentExerciseControl(ExecutionRunner runner) {
		this.runner = java.util.Objects.requireNonNull(runner);
	}

	public ExerciseInteractiveResult startInteractiveExecution(AuthenticatedUser user, long exerciseId,
			long entryId, String code) throws SQLException, IOException, InterruptedException {
		StudentExerciseInput.validateCode(code);
		validateExecutionTarget(user, exerciseId, entryId);
		sessions.entrySet().removeIf(entry -> entry.getValue().createdAt < System.currentTimeMillis() - 180_000);
		PythonRunnerClient client = new PythonRunnerClient();
		String sessionId = client.startSession(code);
		InteractiveExecution execution = new InteractiveExecution(user, exerciseId, entryId, code, client);
		sessions.put(sessionId, execution);
		startCompletionMonitor(sessionId, execution);
		return pollInteractiveExecution(user, exerciseId, entryId, sessionId, 0);
	}

	public ExerciseInteractiveResult pollInteractiveExecution(AuthenticatedUser user, long exerciseId,
			long entryId, String sessionId, long cursor) throws SQLException, IOException, InterruptedException {
		InteractiveExecution execution = requireSession(user, exerciseId, entryId, sessionId);
		validateExecutionTarget(user, exerciseId, entryId);
		var result = execution.client.pollSession(sessionId, cursor);
		Long executionId = "running".equals(result.status) ? null : recordInteractiveExecution(execution, result);
		var events = result.events == null ? List.<InteractiveExecutionUpdate.Event>of()
				: result.events.stream().map(event -> new InteractiveExecutionUpdate.Event(
						event.id, event.stream, event.text)).toList();
		return new ExerciseInteractiveResult(new InteractiveExecutionUpdate(sessionId, result.status,
				result.exitCode, result.errorCode, events, result.nextCursor, text(result.standardInput),
				text(result.standardOutput), text(result.standardError), result.standardOutputTruncated,
				result.standardErrorTruncated, result.durationMilliseconds), executionId);
	}

	public void sendInteractiveInput(AuthenticatedUser user, long exerciseId, long entryId,
			String sessionId, String line) throws SQLException, IOException, InterruptedException {
		InteractiveExecution execution = requireSession(user, exerciseId, entryId, sessionId);
		StudentExerciseInput.validateStandardInput(line);
		if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
			throw new IllegalArgumentException("入力は1行ずつ送信してください。");
		}
		StudentExerciseInput.validateStandardInput(line + "\n");
		validateExecutionTarget(user, exerciseId, entryId);
		execution.client.sendSessionInput(sessionId, line);
	}

	public void cancelInteractiveExecution(AuthenticatedUser user, long exerciseId, long entryId,
			String sessionId) throws SQLException, IOException, InterruptedException {
		InteractiveExecution execution = requireSession(user, exerciseId, entryId, sessionId);
		validateExecutionTarget(user, exerciseId, entryId);
		execution.client.cancelSession(sessionId);
	}

	private InteractiveExecution requireSession(AuthenticatedUser user, long exerciseId, long entryId, String id) {
		requireStudent(user);
		if (id == null || !id.matches("[0-9a-f]{32}")) {
			throw new IllegalArgumentException("実行セッションを特定できません。");
		}
		InteractiveExecution execution = sessions.get(id);
		if (execution == null || execution.user.userId() != user.userId()
				|| execution.exerciseId != exerciseId || execution.entryId != entryId
				|| execution.createdAt < System.currentTimeMillis() - 180_000) {
			throw new SecurityException("The exercise execution session is not available to this user.");
		}
		return execution;
	}

	private void validateExecutionTarget(AuthenticatedUser user, long exerciseId, long entryId) throws SQLException {
		requireStudent(user);
		StudentExerciseInput.requireId(exerciseId);
		StudentExerciseInput.requireId(entryId);
		transact(user, connection -> {
			dao.requireExecutableFile(connection, user.userId(), exerciseId, entryId);
			return null;
		});
	}

	private long recordInteractiveExecution(InteractiveExecution execution, PythonRunnerClient.RunnerSessionResponse result)
			throws SQLException {
		synchronized (execution) {
			if (execution.executionId != null) return execution.executionId;
			String status = "cancelled".equals(result.status) ? "failed" : result.status;
			PythonExecutionResult stored = new PythonExecutionResult(status, result.exitCode,
					text(result.standardOutput), text(result.standardError), result.standardOutputTruncated,
					result.standardErrorTruncated, result.errorCode);
			execution.executionId = transact(execution.user, connection -> dao.recordExecution(connection,
					execution.user.userId(), execution.exerciseId, execution.entryId, execution.code,
					text(result.standardInput), stored,
					(int) Math.min(Integer.MAX_VALUE, Math.max(0, result.durationMilliseconds))));
			return execution.executionId;
		}
	}

	private void startCompletionMonitor(String sessionId, InteractiveExecution execution) {
		Thread monitor = new Thread(() -> {
			long deadline = System.currentTimeMillis() + 90_000;
			while (System.currentTimeMillis() < deadline) {
				try {
					var result = execution.client.pollSession(sessionId, Long.MAX_VALUE);
					if (!"running".equals(result.status)) {
						recordInteractiveExecution(execution, result);
						return;
					}
				} catch (PythonRunnerRequestException e) {
					LOGGER.log(Level.WARNING, "Exercise runner rejected session polling.", e);
					if (e.statusCode() == 404 || "runner_cleanup_failed".equals(e.errorCode())
							|| "runner_unavailable".equals(e.errorCode())) return;
				} catch (IOException | SQLException e) {
					LOGGER.log(Level.WARNING, "Exercise execution could not be polled or stored.", e);
				} catch (ExerciseNotFoundException | SecurityException | IllegalArgumentException e) {
					LOGGER.log(Level.WARNING, "Exercise execution target is no longer available.", e);
					return;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			LOGGER.warning("Exercise execution completion monitoring exceeded its deadline: " + sessionId);
		}, "student-exercise-session-" + sessionId);
		monitor.setDaemon(true);
		monitor.start();
	}

	private static String text(String value) { return value == null ? "" : value; }

	private static final class InteractiveExecution {
		final AuthenticatedUser user;
		final long exerciseId;
		final long entryId;
		final String code;
		final PythonRunnerClient client;
		final long createdAt = System.currentTimeMillis();
		Long executionId;

		InteractiveExecution(AuthenticatedUser user, long exerciseId, long entryId, String code,
				PythonRunnerClient client) {
			this.user = user;
			this.exerciseId = exerciseId;
			this.entryId = entryId;
			this.code = code;
			this.client = client;
		}
	}

	public ExerciseExecutionResult runCode(AuthenticatedUser user, long exerciseId, long entryId,
			String code, String standardInput) throws SQLException, IOException, InterruptedException {
		requireStudent(user);
		StudentExerciseInput.requireId(exerciseId);
		StudentExerciseInput.requireId(entryId);
		StudentExerciseInput.validateCode(code);
		StudentExerciseInput.validateStandardInput(standardInput);
		transact(user, connection -> {
			dao.requireExecutableFile(connection, user.userId(), exerciseId, entryId);
			return null;
		});
		// Do not hold database locks while the isolated runner is executing.
		var executed = runner.execute(code, standardInput);
		long executionId = transact(user, connection -> dao.recordExecution(connection, user.userId(),
				exerciseId, entryId, code, standardInput, executed.result(), executed.durationMilliseconds()));
		return new ExerciseExecutionResult(executionId, executed.result());
	}

	@FunctionalInterface
	interface ExecutionRunner {
		PythonRunnerClient.TimedResult execute(String code, String input) throws IOException, InterruptedException;
	}

	public StudentExercisePage loadPage(AuthenticatedUser user, Long exerciseId, Long entryId)
			throws SQLException {
		requireStudent(user);
		if (exerciseId != null) StudentExerciseInput.requireId(exerciseId);
		if (entryId != null) {
			StudentExerciseInput.requireId(entryId);
			if (exerciseId == null) throw new IllegalArgumentException("演習領域を指定してください。");
		}
		return transact(user, connection -> {
			var scopes = dao.findScopes(connection, user.userId());
			Long selected = exerciseId == null ? null : dao.resolveScope(connection, user.userId(), exerciseId);
			if (selected == null && !scopes.isEmpty()) {
				selected = scopes.stream().filter(scope -> "student_created".equals(scope.origin()))
						.findFirst().orElse(scopes.get(0)).exerciseId();
			}
			var entries = selected == null ? List.<StudentExerciseEntry>of()
					: entriesForScope(connection, user.userId(), selected);
			if (entryId != null) dao.requireActiveEntry(entries, entryId);
			return new StudentExercisePage(scopes, selected, entryId, dao.visibleEntries(entries),
					preferences.findByUserId(connection, user.userId()),
					entryId == null ? null : dao.findLatestExecution(connection, user.userId(), selected, entryId));
		});
	}

	public entity.ExerciseDownload loadDownload(AuthenticatedUser user, long exerciseId) throws SQLException {
		return loadDownload(user, exerciseId, null);
	}

	public entity.ExerciseDownload loadDownload(AuthenticatedUser user, long exerciseId,
			List<Long> selectedIds) throws SQLException {
		requireStudent(user);
		StudentExerciseInput.requireId(exerciseId);
		return transact(user, connection -> dao.selectedDownload(connection, user.userId(),
				dao.resolveScope(connection, user.userId(), exerciseId), user.loginId(), selectedIds));
	}

	public entity.ExerciseUnification previewUnification(AuthenticatedUser user) throws SQLException {
		requireStudent(user);
		return transact(user, connection -> unification.preview(connection, user.userId()));
	}

	public entity.ExerciseUnificationResult unify(AuthenticatedUser user, String token) throws SQLException {
		return unify(user, token, null, 0);
	}

	public entity.ExerciseUnificationResult unify(AuthenticatedUser user, String token,
			Long currentExerciseId, long currentVersion) throws SQLException {
		requireStudent(user);
		if (currentExerciseId != null) StudentExerciseInput.requireId(currentExerciseId);
		StudentExerciseInput.requireVersion(currentVersion);
		if (sessions.values().stream().anyMatch(s -> s.user.userId() == user.userId() && s.executionId == null
				&& s.createdAt >= System.currentTimeMillis() - 180_000)) {
			throw new ExerciseConflictException("実行中または結果の記録待ちです。完了してから統合してください。");
		}
		return transact(user, connection -> {
			if (currentExerciseId != null && dao.requireScope(connection, user.userId(), currentExerciseId, true)
					.version() != currentVersion) {
				throw new ExerciseConflictException("編集中の作業場所が更新されています。未保存コードを控えてから再読み込みしてください。統合は行っていません。");
			}
			return unification.unify(connection, user.userId(), token);
		});
	}

	public ExerciseSaveResult create(AuthenticatedUser user, Long exerciseId, Long parentId,
			long expectedVersion, StudentExerciseInput input) throws SQLException {
		requireStudent(user);
		if (input == null) throw new IllegalArgumentException("作成内容を指定してください。");
		if (exerciseId != null) StudentExerciseInput.requireId(exerciseId);
		if (parentId != null) StudentExerciseInput.requireId(parentId);
		StudentExerciseInput.requireVersion(expectedVersion);
		return transact(user, connection ->
				dao.create(connection, user.userId(), exerciseId, parentId, expectedVersion, input));
	}

	public ExerciseSaveResult save(AuthenticatedUser user, long exerciseId, long entryId,
			long expectedVersion, String code) throws SQLException {
		requireMutation(user, exerciseId, entryId, expectedVersion);
		StudentExerciseInput.validateCode(code);
		return transact(user, connection ->
				dao.save(connection, user.userId(), exerciseId, entryId, expectedVersion, code));
	}

	public ExerciseSaveResult upload(AuthenticatedUser user, Long exerciseId, Long parentId,
			long expectedVersion, entity.ExerciseUpload upload) throws SQLException {
		requireStudent(user);
		if (upload == null) throw new IllegalArgumentException("アップロード内容を指定してください。");
		if (exerciseId != null) StudentExerciseInput.requireId(exerciseId);
		if (parentId != null) StudentExerciseInput.requireId(parentId);
		StudentExerciseInput.requireVersion(expectedVersion);
		return transact(user, connection ->
				dao.upload(connection, user.userId(), exerciseId, parentId, expectedVersion, upload));
	}

	public entity.ExerciseUploadPreview previewUpload(AuthenticatedUser user, Long exerciseId, Long parentId,
			long expectedVersion, entity.ExerciseUpload upload) throws SQLException {
		requireStudent(user);
		if (exerciseId != null) StudentExerciseInput.requireId(exerciseId);
		if (parentId != null) StudentExerciseInput.requireId(parentId);
		StudentExerciseInput.requireVersion(expectedVersion);
		return transact(user, connection -> dao.previewUpload(connection, user.userId(), exerciseId,
				parentId, expectedVersion, upload));
	}

	public entity.ExerciseUploadResult upload(AuthenticatedUser user, Long exerciseId, Long parentId,
			long expectedVersion, entity.ExerciseUpload upload,
			List<entity.ExerciseUploadResolution> resolutions) throws SQLException {
		requireStudent(user);
		if (exerciseId != null) StudentExerciseInput.requireId(exerciseId);
		if (parentId != null) StudentExerciseInput.requireId(parentId);
		StudentExerciseInput.requireVersion(expectedVersion);
		requireNoActiveExecution(user);
		return transact(user, connection -> dao.uploadResolved(connection, user.userId(), exerciseId,
				parentId, expectedVersion, upload, resolutions));
	}

	public ExerciseBatchResult batchMove(AuthenticatedUser user, long exerciseId, ExerciseBatchInput input)
			throws SQLException {
		requireBatchMutation(user, exerciseId, input);
		return transact(user, connection -> dao.batchMove(connection, user.userId(), exerciseId, input));
	}

	public ExerciseBatchResult batchTrash(AuthenticatedUser user, long exerciseId, ExerciseBatchInput input)
			throws SQLException {
		requireBatchMutation(user, exerciseId, input);
		return transact(user, connection -> dao.batchTrash(connection, user.userId(), exerciseId, input));
	}

	public ExerciseBatchResult batchRestore(AuthenticatedUser user, long exerciseId, ExerciseBatchInput input)
			throws SQLException {
		requireBatchMutation(user, exerciseId, input);
		return transact(user, connection -> dao.batchRestore(connection, user.userId(), exerciseId, input));
	}

	public entity.ExerciseDuplicatePreview duplicatePreview(AuthenticatedUser user, long exerciseId,
			List<Long> entryIds) throws SQLException {
		return duplicatePreview(user, exerciseId, entryIds, null);
	}

	public entity.ExerciseDuplicatePreview duplicatePreview(AuthenticatedUser user, long exerciseId,
			List<Long> entryIds, Long expectedVersion) throws SQLException {
		requireStudent(user);
		StudentExerciseInput.requireId(exerciseId);
		if (expectedVersion != null) StudentExerciseInput.requireVersion(expectedVersion);
		return transact(user, connection -> dao.previewDuplicate(connection, user.userId(),
				dao.resolveScope(connection, user.userId(), exerciseId), entryIds, expectedVersion));
	}

	public ExerciseBatchResult duplicate(AuthenticatedUser user, long exerciseId, ExerciseBatchInput input)
			throws SQLException {
		requireBatchMutation(user, exerciseId, input);
		return transact(user, connection -> dao.duplicate(connection, user.userId(), exerciseId, input));
	}

	public ExerciseSaveResult trash(AuthenticatedUser user, long exerciseId, long entryId, long version)
			throws SQLException {
		requireMutation(user, exerciseId, entryId, version);
		return transact(user, connection ->
				dao.changeTrash(connection, user.userId(), exerciseId, entryId, version, false));
	}

	public ExerciseSaveResult rename(AuthenticatedUser user, long exerciseId, long entryId,
			long version, String name) throws SQLException {
		requireMutation(user, exerciseId, entryId, version);
		return transact(user, connection ->
				dao.relocate(connection, user.userId(), exerciseId, entryId, version, false, null, name));
	}

	public ExerciseSaveResult move(AuthenticatedUser user, long exerciseId, long entryId,
			long version, Long parentId, String name) throws SQLException {
		requireMutation(user, exerciseId, entryId, version);
		if (parentId != null) StudentExerciseInput.requireId(parentId);
		return transact(user, connection ->
				dao.relocate(connection, user.userId(), exerciseId, entryId, version, true, parentId, name));
	}

	public ExerciseSaveResult restore(AuthenticatedUser user, long exerciseId, long entryId, long version)
			throws SQLException {
		return restore(user, exerciseId, entryId, version, false, null, null);
	}

	public ExerciseSaveResult restore(AuthenticatedUser user, long exerciseId, long entryId, long version,
			boolean destinationSpecified, Long parentId, String name) throws SQLException {
		requireMutation(user, exerciseId, entryId, version);
		if (parentId != null) StudentExerciseInput.requireId(parentId);
		return transact(user, connection ->
				dao.restore(connection, user.userId(), exerciseId, entryId, version, destinationSpecified, parentId, name));
	}

	private List<StudentExerciseEntry> entriesForScope(Connection connection, long userId, long id)
			throws SQLException {
		dao.requireScope(connection, userId, id, false);
		return dao.findEntries(connection, id);
	}

	private <T> T transact(AuthenticatedUser user, Transaction<T> operation) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				dao.requireActiveStudent(connection, user.userId());
				T result = operation.execute(connection);
				connection.commit();
				return result;
			} catch (SQLException | RuntimeException e) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					e.addSuppressed(rollbackFailure);
				}
				throw e;
			}
		}
	}

	private static void requireMutation(AuthenticatedUser user, long scope, long entry, long version) {
		requireStudent(user);
		StudentExerciseInput.requireId(scope);
		StudentExerciseInput.requireId(entry);
		StudentExerciseInput.requireVersion(version);
	}

	private void requireBatchMutation(AuthenticatedUser user, long exerciseId, ExerciseBatchInput input) {
		requireStudent(user);
		StudentExerciseInput.requireId(exerciseId);
		if (input == null) throw new IllegalArgumentException("一括操作の内容を指定してください。");
		StudentExerciseInput.requireVersion(input.expectedVersion());
		requireNoActiveExecution(user);
	}

	private void requireNoActiveExecution(AuthenticatedUser user) {
		if (sessions.values().stream().anyMatch(s -> s.user.userId() == user.userId()
				&& s.executionId == null && s.createdAt >= System.currentTimeMillis() - 180_000)) {
			throw new ExerciseConflictException("実行中または結果の記録待ちです。完了してから変更してください。");
		}
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT || user.passwordChangeRequired()) {
			throw new SecurityException("Student authentication without a pending password change is required.");
		}
	}

	@FunctionalInterface
	private interface Transaction<T> {
		T execute(Connection connection) throws SQLException;
	}
}
