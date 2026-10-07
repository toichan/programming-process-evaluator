package control.teacher;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import control.auth.AuthenticatedUser;
import control.student.PythonRunnerClient;
import dao.TeacherDistributionDao;
import entity.ExerciseInteractiveResult;
import entity.InteractiveExecutionUpdate;
import entity.PythonExecutionResult;
import entity.StudentExerciseInput;
import entity.TeacherDistributionInput;
import entity.TeacherDistributionPage;
import entity.UserCredential.UserType;
import lib.mysql.Client;

public final class TeacherDistributionControl {
	private static final Logger LOGGER = Logger.getLogger(TeacherDistributionControl.class.getName());
	private final TeacherDistributionDao dao;
	private final ConnectionFactory connectionFactory;
	private final ConcurrentHashMap<String, PreviewSession> previewSessions = new ConcurrentHashMap<>();
	private static final long PREVIEW_SESSION_TTL_MILLIS = 180_000;
	private static final ScheduledExecutorService PREVIEW_SESSION_CLEANER =
			Executors.newSingleThreadScheduledExecutor(task -> {
				Thread thread = new Thread(task, "teacher-preview-session-cleaner");
				thread.setDaemon(true);
				return thread;
			});

	public TeacherDistributionControl() {
		this(new TeacherDistributionDao(), Client::createConnection);
	}

	TeacherDistributionControl(TeacherDistributionDao dao, ConnectionFactory connectionFactory) {
		this.dao = Objects.requireNonNull(dao);
		this.connectionFactory = Objects.requireNonNull(connectionFactory);
	}

	public record ScheduleResult(long distributionId, int expectedImmediateTargets,
			int completedImmediateTargets, boolean processingUnavailable) {}

	public TeacherDistributionPage loadPage(AuthenticatedUser user) throws SQLException {
		requireTeacher(user);
		return transact(connection -> dao.loadPage(connection, user.userId()));
	}

	public TeacherDistributionDao.TemplateDefinition loadTemplate(AuthenticatedUser user, long templateId)
			throws SQLException {
		requireTeacher(user);
		requirePositiveId(templateId);
		return transact(connection -> dao.findTemplate(connection, user.userId(), templateId));
	}

	public void saveDraft(AuthenticatedUser user, TeacherDistributionInput input) throws SQLException {
		requireTeacher(user);
		Objects.requireNonNull(input);
		transact(connection -> dao.saveDraft(connection, user.userId(), input));
	}

	public ScheduleResult schedule(AuthenticatedUser user, TeacherDistributionInput input) throws SQLException {
		requireTeacher(user);
		Objects.requireNonNull(input);
		TeacherDistributionDao.ScheduledDistribution scheduled =
				transact(connection -> dao.schedule(connection, user.userId(), input));
		int completed = 0;
		boolean unavailable = false;
		if (!scheduled.immediateTargetIds().isEmpty()) {
			try {
				completed = dao.processDueTargets(scheduled.distributionId());
			} catch (SQLException failure) {
				unavailable = true;
				LOGGER.log(Level.SEVERE, "Immediate teacher exercise distribution could not be processed.", failure);
			}
		}
		return new ScheduleResult(scheduled.distributionId(), scheduled.immediateTargetIds().size(),
				completed, unavailable);
	}

	public PythonExecutionResult runPreview(AuthenticatedUser user, String sourceCode)
			throws SQLException, IOException, InterruptedException {
		requireCodeDistributionAccess(user);
		return new control.student.PythonRunnerClient().executePreview(sourceCode);
	}

	public ExerciseInteractiveResult startPreview(AuthenticatedUser user, String sourceCode)
			throws SQLException, IOException, InterruptedException {
		requireCodeDistributionAccess(user);
		String code = StudentExerciseInput.validateCode(sourceCode);
		removeExpiredPreviewSessions();
		PythonRunnerClient client = new PythonRunnerClient();
		String sessionId = client.startSession(code);
		PreviewSession session = new PreviewSession(user.userId(), client, System.currentTimeMillis());
		previewSessions.put(sessionId, session);
		PREVIEW_SESSION_CLEANER.schedule(() -> cancelExpiredPreviewSession(sessionId, session),
				PREVIEW_SESSION_TTL_MILLIS, TimeUnit.MILLISECONDS);
		return pollPreview(user, sessionId, 0);
	}

	public ExerciseInteractiveResult pollPreview(AuthenticatedUser user, String sessionId, long cursor)
			throws SQLException, IOException, InterruptedException {
		requireCodeDistributionAccess(user);
		PreviewSession session = requirePreviewSession(user, sessionId);
		PythonRunnerClient.RunnerSessionResponse result = session.client().pollSession(sessionId, cursor);
		if (!"running".equals(result.status)) {
			previewSessions.remove(sessionId, session);
		}
		List<InteractiveExecutionUpdate.Event> events = result.events == null ? List.of()
				: result.events.stream().map(event -> new InteractiveExecutionUpdate.Event(
						event.id, event.stream, event.text)).toList();
		return new ExerciseInteractiveResult(new InteractiveExecutionUpdate(sessionId, result.status,
				result.exitCode, result.errorCode, events, result.nextCursor, text(result.standardInput),
				text(result.standardOutput), text(result.standardError), result.standardOutputTruncated,
				result.standardErrorTruncated, result.durationMilliseconds), null);
	}

	public void sendPreviewInput(AuthenticatedUser user, String sessionId, String line)
			throws SQLException, IOException, InterruptedException {
		requireCodeDistributionAccess(user);
		StudentExerciseInput.validateStandardInput(line);
		if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
			throw new IllegalArgumentException("入力は1行ずつ送信してください。");
		}
		StudentExerciseInput.validateStandardInput(line + "\n");
		requirePreviewSession(user, sessionId).client().sendSessionInput(sessionId, line);
	}

	public void cancelPreview(AuthenticatedUser user, String sessionId)
			throws SQLException, IOException, InterruptedException {
		requireCodeDistributionAccess(user);
		requirePreviewSession(user, sessionId).client().cancelSession(sessionId);
	}

	private void requireCodeDistributionAccess(AuthenticatedUser user) throws SQLException {
		requireTeacher(user);
		transact(connection -> {
			dao.requireCodeDistributionAccess(connection, user.userId());
			return null;
		});
	}

	private PreviewSession requirePreviewSession(AuthenticatedUser user, String sessionId) {
		if (sessionId == null || !sessionId.matches("[0-9a-f]{32}")) {
			throw new IllegalArgumentException("実行セッションを特定できません。");
		}
		PreviewSession session = previewSessions.get(sessionId);
		if (session == null || session.teacherId() != user.userId()
				|| session.createdAt() < System.currentTimeMillis() - PREVIEW_SESSION_TTL_MILLIS) {
			throw new SecurityException("The teacher preview session is not available to this user.");
		}
		return session;
	}

	private void removeExpiredPreviewSessions() {
		long cutoff = System.currentTimeMillis() - PREVIEW_SESSION_TTL_MILLIS;
		previewSessions.forEach((id, session) -> {
			if (session.createdAt() < cutoff) cancelExpiredPreviewSession(id, session);
		});
	}

	private void cancelExpiredPreviewSession(String sessionId, PreviewSession session) {
		if (!previewSessions.remove(sessionId, session)) return;
		try {
			session.client().cancelSession(sessionId);
		} catch (IOException failure) {
			LOGGER.log(Level.WARNING, "Expired teacher preview session could not be cancelled.", failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			LOGGER.log(Level.WARNING, "Expired teacher preview cleanup was interrupted.", failure);
		}
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	private record PreviewSession(long teacherId, PythonRunnerClient client, long createdAt) {}

	public void archive(AuthenticatedUser user, long templateId, int expectedVersion) throws SQLException {
		requireTeacher(user);
		requirePositiveId(templateId);
		transact(connection -> {
			dao.archive(connection, user.userId(), templateId, expectedVersion);
			return null;
		});
	}

	public void stop(AuthenticatedUser user, long distributionId) throws SQLException {
		requireTeacher(user);
		requirePositiveId(distributionId);
		transact(connection -> {
			dao.stop(connection, user.userId(), distributionId);
			return null;
		});
	}

	public void resume(AuthenticatedUser user, long distributionId) throws SQLException {
		requireTeacher(user);
		requirePositiveId(distributionId);
		transact(connection -> {
			dao.resume(connection, user.userId(), distributionId);
			return null;
		});
	}

	public void stopTarget(AuthenticatedUser user, long targetId) throws SQLException {
		requireTeacher(user);
		requirePositiveId(targetId);
		transact(connection -> {
			dao.stopTarget(connection, user.userId(), targetId);
			return null;
		});
	}

	public void resumeTarget(AuthenticatedUser user, long targetId) throws SQLException {
		requireTeacher(user);
		requirePositiveId(targetId);
		transact(connection -> {
			dao.resumeTarget(connection, user.userId(), targetId);
			return null;
		});
	}

	public void rescheduleTarget(AuthenticatedUser user, long targetId, LocalDateTime scheduledAt)
			throws SQLException {
		requireTeacher(user);
		requirePositiveId(targetId);
		transact(connection -> {
			dao.rescheduleTarget(connection, user.userId(), targetId, scheduledAt);
			return null;
		});
	}

	public int processDueTargets() throws SQLException {
		return dao.processDueTargets();
	}

	private <T> T transact(SqlOperation<T> operation) throws SQLException {
		try (Connection connection = connectionFactory.open()) {
			connection.setAutoCommit(false);
			try {
				T result = operation.run(connection);
				connection.commit();
				return result;
			} catch (SQLException | RuntimeException failure) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
					LOGGER.log(Level.SEVERE, "Failed to rollback teacher distribution operation.", rollbackFailure);
				}
				throw failure;
			}
		}
	}

	private static void requireTeacher(AuthenticatedUser user) {
		if (user == null || user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher access is required.");
		}
	}

	private static void requirePositiveId(long id) {
		if (id < 1) throw new IllegalArgumentException("対象を正しく指定してください。");
	}

	@FunctionalInterface
	interface ConnectionFactory {
		Connection open() throws SQLException;
	}

	@FunctionalInterface
	private interface SqlOperation<T> {
		T run(Connection connection) throws SQLException;
	}
}
