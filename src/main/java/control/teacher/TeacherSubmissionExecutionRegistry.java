package control.teacher;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import control.student.PythonRunnerClient;
import entity.InteractiveExecutionUpdate;
import entity.StudentExerciseInput;

final class TeacherSubmissionExecutionRegistry {
	private static final Logger LOGGER = Logger.getLogger(TeacherSubmissionExecutionRegistry.class.getName());
	private static final long RETENTION_MILLIS = 180_000;
	private static final java.util.concurrent.ScheduledExecutorService CLEANER =
			Executors.newSingleThreadScheduledExecutor(task -> {
				var thread = new Thread(task, "teacher-submission-preview-cleaner");
				thread.setDaemon(true);
				return thread;
			});
	private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

	String start(long teacherId, long submissionId, String code) throws IOException, InterruptedException {
		return start(teacherId, Target.submission(submissionId), code);
	}

	String start(long teacherId, Target target, String code) throws IOException, InterruptedException {
		StudentExerciseInput.validateCode(code);
		var client = new PythonRunnerClient();
		String id = client.startSession(code);
		var session = new Session(teacherId, target, client);
		sessions.put(id, session);
		CLEANER.schedule(() -> cleanup(id, session), RETENTION_MILLIS, TimeUnit.MILLISECONDS);
		return id;
	}

	InteractiveExecutionUpdate poll(long teacherId, long submissionId, String id, long cursor)
			throws IOException, InterruptedException {
		return poll(teacherId, Target.submission(submissionId), id, cursor);
	}

	InteractiveExecutionUpdate poll(long teacherId, Target target, String id, long cursor)
			throws IOException, InterruptedException {
		if (cursor < 0) throw new IllegalArgumentException("実行イベント位置が不正です。");
		var session = require(teacherId, target, id);
		var result = session.client().pollSession(id, cursor);
		if (!"running".equals(result.status)) sessions.remove(id, session);
		return update(id, result);
	}

	void input(long teacherId, long submissionId, String id, String line) throws IOException, InterruptedException {
		input(teacherId, Target.submission(submissionId), id, line);
	}

	void input(long teacherId, Target target, String id, String line) throws IOException, InterruptedException {
		StudentExerciseInput.validateStandardInput(line);
		if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0)
			throw new IllegalArgumentException("入力は1行ずつ送信してください。");
		StudentExerciseInput.validateStandardInput(line + "\n");
		require(teacherId, target, id).client().sendSessionInput(id, line);
	}

	InteractiveExecutionUpdate cancel(long teacherId, long submissionId, String id, long cursor)
			throws IOException, InterruptedException {
		return cancel(teacherId, Target.submission(submissionId), id, cursor);
	}

	InteractiveExecutionUpdate cancel(long teacherId, Target target, String id, long cursor)
			throws IOException, InterruptedException {
		if (cursor < 0) throw new IllegalArgumentException("実行イベント位置が不正です。");
		var session = require(teacherId, target, id);
		session.client().cancelSession(id);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		do {
			var result = session.client().pollSession(id, cursor);
			if (!"running".equals(result.status)) {
				sessions.remove(id, session);
				return update(id, result);
			}
			Thread.sleep(50);
		} while (System.nanoTime() < deadline);
		throw new IOException("The teacher preview is still stopping.");
	}

	void discard(long teacherId, long submissionId, String id) {
		discard(teacherId, Target.submission(submissionId), id);
	}

	void discard(long teacherId, Target target, String id) {
		var session = require(teacherId, target, id);
		cleanup(id, session);
	}

	private Session require(long teacherId, Target target, String id) {
		if (id == null || !id.matches("[0-9a-f]{32}"))
			throw new IllegalArgumentException("実行セッションを特定できません。");
		var session = sessions.get(id);
		if (session == null || session.teacherId() != teacherId || !session.target().equals(target))
			throw new SecurityException("The teacher preview session is not available to this user.");
		return session;
	}

	private void cleanup(String id, Session session) {
		if (!sessions.remove(id, session)) return;
		try {
			session.client().cancelSession(id);
		} catch (IOException failure) {
			LOGGER.log(Level.WARNING, "Submission preview cleanup could not reach the runner.", failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			LOGGER.log(Level.WARNING, "Submission preview cleanup was interrupted.", failure);
		}
	}

	private static InteractiveExecutionUpdate update(String id, PythonRunnerClient.RunnerSessionResponse result) {
		var events = result.events == null ? List.<InteractiveExecutionUpdate.Event>of()
				: result.events.stream().map(event -> new InteractiveExecutionUpdate.Event(event.id, event.stream, event.text)).toList();
		return new InteractiveExecutionUpdate(id, result.status, result.exitCode, result.errorCode, events,
				result.nextCursor, text(result.standardInput), text(result.standardOutput), text(result.standardError),
				result.standardOutputTruncated, result.standardErrorTruncated, result.durationMilliseconds);
	}

	private static String text(String value) { return value == null ? "" : value; }
	record Target(String surface, long first, long second, long third) {
		Target {
			StudentExerciseInput.requireId(first);
			if ("progress".equals(surface)) StudentExerciseInput.requireId(second);
			else if ("exercise".equals(surface)) {
				StudentExerciseInput.requireId(second);
				StudentExerciseInput.requireId(third);
			} else if (!"submission".equals(surface)) throw new IllegalArgumentException("実行対象が不正です。");
		}
		static Target progress(long assignment, long student) { return new Target("progress", assignment, student, 0); }
		static Target exercise(long student, long classroom, long entry) { return new Target("exercise", student, classroom, entry); }
		static Target submission(long submission) { return new Target("submission", submission, 0, 0); }
	}
	private record Session(long teacherId, Target target, PythonRunnerClient client) {}
}
