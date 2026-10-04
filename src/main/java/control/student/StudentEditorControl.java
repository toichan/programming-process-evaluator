package control.student;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import control.auth.AuthenticatedUser;
import dao.StudentEditorDao;
import entity.EditorSaveResult;
import entity.EditorSubmissionCheck;
import entity.EditorSubmissionCheckCase;
import entity.EditorSubmissionResult;
import entity.EditorTestCase;
import entity.InteractiveExecutionUpdate;
import entity.PythonExecutionResult;
import entity.PythonExecutionInput;
import entity.StudentEditorPage;
import entity.UserCredential.UserType;

public final class StudentEditorControl {
	private static final Logger LOGGER = Logger.getLogger(StudentEditorControl.class.getName());
	private static final int MAX_INPUT_BYTES = PythonExecutionInput.MAX_INPUT_BYTES;
	private static final long SESSION_RETENTION_MILLISECONDS = 180_000;
	private static final long SESSION_MONITOR_MILLISECONDS = 90_000;
	private static final ConcurrentHashMap<String, InteractiveExecution> INTERACTIVE_EXECUTIONS =
			new ConcurrentHashMap<>();

	private final StudentEditorDao editorDao;
	private final PythonRunnerClient runnerClient;

	public StudentEditorControl() {
		this(new StudentEditorDao(), new PythonRunnerClient());
	}

	StudentEditorControl(StudentEditorDao editorDao, PythonRunnerClient runnerClient) {
		this.editorDao = editorDao;
		this.runnerClient = runnerClient;
	}

	public Optional<StudentEditorPage> loadPage(AuthenticatedUser user, long assignmentId) throws SQLException {
		requireStudent(user);
		return editorDao.findEditorPage(user.userId(), assignmentId);
	}

	public EditorSaveResult saveDraft(
			AuthenticatedUser user,
			long assignmentId,
			String sourceCode,
			LocalDateTime expectedUpdatedAt,
			boolean periodicSnapshot) throws SQLException {
		requireStudent(user);
		validateSource(sourceCode);
		return editorDao.saveDraft(
				user.userId(), assignmentId, sourceCode, expectedUpdatedAt, periodicSnapshot);
	}

	public PythonExecutionResult runCode(
			AuthenticatedUser user,
			long assignmentId,
			String sourceCode,
			String standardInput) throws SQLException, IOException, InterruptedException {
		requireStudent(user);
		validateSource(sourceCode);
		validateInput(standardInput);
		if (editorDao.findEditorPage(user.userId(), assignmentId).isEmpty()) {
			throw new IllegalArgumentException("The requested task is not available.");
		}
		var execution = runnerClient.executeTimed(sourceCode, standardInput);
		editorDao.recordExecution(user.userId(), assignmentId, sourceCode, standardInput,
				execution.result(), execution.durationMilliseconds());
		return execution.result();
	}

	public InteractiveExecutionUpdate startInteractiveExecution(
			AuthenticatedUser user, long assignmentId, String sourceCode)
			throws SQLException, IOException, InterruptedException {
		requireStudent(user);
		validateSource(sourceCode);
		if (editorDao.findEditorPage(user.userId(), assignmentId).isEmpty()) {
			throw new IllegalArgumentException("The requested task is not available.");
		}
		pruneInteractiveExecutions();
		String sessionId = runnerClient.startSession(sourceCode);
		InteractiveExecution execution = new InteractiveExecution(user.userId(), assignmentId, sourceCode);
		INTERACTIVE_EXECUTIONS.put(sessionId, execution);
		startCompletionMonitor(sessionId, execution);
		return pollInteractiveExecution(user, assignmentId, sessionId, 0);
	}

	public InteractiveExecutionUpdate pollInteractiveExecution(
			AuthenticatedUser user, long assignmentId, String sessionId, long cursor)
			throws SQLException, IOException, InterruptedException {
		InteractiveExecution execution = findInteractiveExecution(user, assignmentId, sessionId);
		PythonRunnerClient.RunnerSessionResponse result = runnerClient.pollSession(sessionId, cursor);
		if (!"running".equals(result.status)) {
			recordInteractiveExecution(execution, result);
		}
		List<InteractiveExecutionUpdate.Event> events = result.events == null
				? List.of()
				: result.events.stream()
						.map(event -> new InteractiveExecutionUpdate.Event(event.id, event.stream, event.text))
						.toList();
		return new InteractiveExecutionUpdate(
				sessionId,
				result.status,
				result.exitCode,
				result.errorCode,
				events,
				result.nextCursor,
				valueOrEmpty(result.standardInput),
				valueOrEmpty(result.standardOutput),
				valueOrEmpty(result.standardError),
				result.standardOutputTruncated,
				result.standardErrorTruncated,
				result.durationMilliseconds);
	}

	public void sendInteractiveInput(
			AuthenticatedUser user, long assignmentId, String sessionId, String line)
			throws IOException, InterruptedException {
		findInteractiveExecution(user, assignmentId, sessionId);
		if (line == null || line.indexOf('\0') >= 0 || line.indexOf('\n') >= 0
				|| line.indexOf('\r') >= 0
				|| (line + "\n").getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
			throw new IllegalArgumentException("入力は1行8 KiB以下で入力してください。");
		}
		runnerClient.sendSessionInput(sessionId, line);
	}

	public void cancelInteractiveExecution(
			AuthenticatedUser user, long assignmentId, String sessionId)
			throws IOException, InterruptedException {
		findInteractiveExecution(user, assignmentId, sessionId);
		runnerClient.cancelSession(sessionId);
	}

	private InteractiveExecution findInteractiveExecution(
			AuthenticatedUser user, long assignmentId, String sessionId) {
		requireStudent(user);
		if (sessionId == null || !sessionId.matches("[0-9a-f]{32}")) {
			throw new IllegalArgumentException("実行セッションを特定できません。");
		}
		pruneInteractiveExecutions();
		InteractiveExecution execution = INTERACTIVE_EXECUTIONS.get(sessionId);
		if (execution == null
				|| execution.userId != user.userId()
				|| execution.assignmentId != assignmentId) {
			throw new SecurityException("The execution session is not available to this user.");
		}
		return execution;
	}

	private void recordInteractiveExecution(
			InteractiveExecution execution, PythonRunnerClient.RunnerSessionResponse result)
			throws SQLException {
		synchronized (execution) {
			if (execution.recorded) {
				return;
			}
			PythonExecutionResult recordedResult = new PythonExecutionResult(
					result.status,
					result.exitCode,
					valueOrEmpty(result.standardOutput),
					valueOrEmpty(result.standardError),
					result.standardOutputTruncated,
					result.standardErrorTruncated,
					result.errorCode);
			int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(0, result.durationMilliseconds));
			editorDao.recordExecution(
					execution.userId,
					execution.assignmentId,
					execution.sourceCode,
					valueOrEmpty(result.standardInput),
					recordedResult,
					duration);
			execution.recorded = true;
		}
	}

	private void startCompletionMonitor(String sessionId, InteractiveExecution execution) {
		Thread monitor = new Thread(() -> {
			long deadline = System.currentTimeMillis() + SESSION_MONITOR_MILLISECONDS;
			long cursor = 0;
			while (System.currentTimeMillis() < deadline) {
				try {
					PythonRunnerClient.RunnerSessionResponse result = runnerClient.pollSession(sessionId, cursor);
					cursor = result.nextCursor;
					if (!"running".equals(result.status)) {
						recordInteractiveExecution(execution, result);
						return;
					}
					Thread.sleep(500);
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Interactive execution status could not be polled.", e);
					if (!pauseExecutionMonitor()) {
						return;
					}
				} catch (SQLException e) {
					LOGGER.log(Level.WARNING, "Interactive execution history could not be saved.", e);
					if (!pauseExecutionMonitor()) {
						return;
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}, "student-editor-session-" + sessionId);
		monitor.setDaemon(true);
		monitor.start();
	}

	private static boolean pauseExecutionMonitor() {
		try {
			Thread.sleep(500);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static void pruneInteractiveExecutions() {
		long cutoff = System.currentTimeMillis() - SESSION_RETENTION_MILLISECONDS;
		INTERACTIVE_EXECUTIONS.entrySet().removeIf(entry -> entry.getValue().createdAtMillis < cutoff);
	}

	public EditorSubmissionCheck checkSubmission(
			AuthenticatedUser user,
			long assignmentId,
			String sourceCode,
			String draftUpdatedAt) throws SQLException, IOException, InterruptedException {
		requireStudent(user);
		validateSource(sourceCode);
		StudentEditorPage page = editorDao.findEditorPage(user.userId(), assignmentId).orElse(null);
		if (page == null) {
			throw new IllegalArgumentException("The requested task is not available.");
		}
		if (!page.isCanSubmit()) {
			throw new IllegalStateException("The task cannot be submitted in its current state.");
		}
		if (!page.getCode().equals(sourceCode)
				|| !page.getDraftUpdatedAtToken().equals(draftUpdatedAt == null ? "" : draftUpdatedAt)) {
			throw new DraftConflictException();
		}

		List<EditorSubmissionCheckCase> results = new ArrayList<>();
		for (EditorTestCase testCase : page.getTestCases()) {
			var timed = runnerClient.executeTimed(sourceCode, testCase.getInput());
			PythonExecutionResult execution = timed.result();
			int duration = timed.durationMilliseconds();
			String resultStatus = "error";
			if ("succeeded".equals(execution.getStatus())) {
				resultStatus = normalizeOutput(testCase.getExpectedOutput())
						.equals(normalizeOutput(execution.getStandardOutput()))
								? "matched" : "mismatched";
			}
			results.add(new EditorSubmissionCheckCase(
					testCase.getTestCaseId(),
					testCase.getOrder(),
					testCase.getTitle(),
					testCase.getInput(),
					testCase.getExpectedOutput(),
					execution.getStandardOutput(),
					execution.getStandardError(),
					execution.getStatus(),
					resultStatus,
					execution.getErrorCode(),
					execution.getExitCode(),
					duration,
					execution.isStandardOutputTruncated(),
					execution.isStandardErrorTruncated()));
		}

		return new EditorSubmissionCheck(
				user.userId(),
				assignmentId,
				page.getParticipationId(),
				page.getDraftUpdatedAtToken(),
				sourceCode,
				System.currentTimeMillis(),
				results);
	}

	public EditorSubmissionResult submit(
			AuthenticatedUser user,
			long assignmentId,
			String requestKey,
			EditorSubmissionCheck check) throws SQLException {
		requireStudent(user);
		if (requestKey == null || !requestKey.matches(
				"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")) {
			throw new IllegalArgumentException("The submission request key is invalid.");
		}
		return editorDao.submit(user.userId(), assignmentId, requestKey, check);
	}

	public EditorSaveResult startResubmission(AuthenticatedUser user, long assignmentId) throws SQLException {
		requireStudent(user);
		return editorDao.startResubmission(user.userId(), assignmentId);
	}

	private static String normalizeOutput(String value) {
		return value.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	private static String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	private static void validateSource(String sourceCode) {
		PythonExecutionInput.validateSource(sourceCode);
	}

	private static void validateInput(String standardInput) {
		PythonExecutionInput.validateStandardInput(standardInput);

	}

	private static final class InteractiveExecution {
		private final long userId;
		private final long assignmentId;
		private final String sourceCode;
		private final long createdAtMillis;
		private boolean recorded;

		private InteractiveExecution(long userId, long assignmentId, String sourceCode) {
			this.userId = userId;
			this.assignmentId = assignmentId;
			this.sourceCode = sourceCode;
			this.createdAtMillis = System.currentTimeMillis();
		}
	}

	private static void requireStudent(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.STUDENT) {
			throw new SecurityException("Student authentication is required.");
		}
	}

	public static final class DraftConflictException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public DraftConflictException() {
			super("The draft has been updated by another editor.");
		}
	}
}
