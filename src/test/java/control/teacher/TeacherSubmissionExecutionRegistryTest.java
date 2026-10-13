package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import entity.InteractiveExecutionUpdate;

class TeacherSubmissionExecutionRegistryTest {
	@Test void rejectsInvalidSessionsAndInputBeforeContactingRunner() {
		var sessions = new TeacherSubmissionExecutionRegistry();
		assertThrows(IllegalArgumentException.class, () -> sessions.poll(1, 2, "../sessions", 0));
		assertThrows(IllegalArgumentException.class, () -> sessions.poll(1, 2, "a".repeat(32), -1));
		assertThrows(SecurityException.class, () -> sessions.poll(1, 2, "a".repeat(32), 0));
		assertThrows(IllegalArgumentException.class, () -> sessions.input(1, 2, "a".repeat(32), "one\ntwo"));
		assertThrows(IllegalArgumentException.class, () -> sessions.input(1, 2, "a".repeat(32), "one\rtwo"));
		assertThrows(IllegalArgumentException.class, () -> sessions.input(1, 2, "a".repeat(32), "x".repeat(8192)));
		assertThrows(IllegalArgumentException.class, () -> sessions.start(1, 2, "x".repeat(65537)));
	}

	@Test void realRunnerWaitsForInputOrdersOutputAndReleasesCompletedOwnerBoundSession() throws Exception {
		requireRunner();
		var sessions = new TeacherSubmissionExecutionRegistry();
		String id = sessions.start(1, 2, "print('ready', flush=True)\nname = input()\nprint('hello ' + name)\n");
		try {
			var waiting = awaitOutput(sessions, id, "ready", 0);
			assertEquals("running", waiting.status());
			assertThrows(SecurityException.class, () -> sessions.poll(3, 2, id, 0));
			assertThrows(SecurityException.class, () -> sessions.input(1, 4, id, "forbidden"));
			assertThrows(SecurityException.class, () -> sessions.cancel(3, 2, id, 0));
			sessions.input(1, 2, id, "synthetic");
			var completed = awaitCompletion(sessions, id, waiting.nextCursor());
			assertEquals("succeeded", completed.status());
			assertEquals("synthetic\n", completed.standardInput());
			assertEquals("ready\nhello synthetic\n", completed.standardOutput());
			assertTrue(completed.events().stream().anyMatch(event -> event.stream().equals("input")));
			assertTrue(completed.events().stream().allMatch(event -> event.id() > waiting.nextCursor()));
			assertThrows(SecurityException.class, () -> sessions.poll(1, 2, id, 0));
			assertThrows(SecurityException.class, () -> sessions.input(1, 2, id, "after completion"));
		} finally { discard(sessions, id); }
	}

	@Test void realRunnerCancellationAndDiscardCleanLiveSessions() throws Exception {
		requireRunner();
		var sessions = new TeacherSubmissionExecutionRegistry();
		String id = sessions.start(1, 2, "print('waiting', flush=True)\ninput()\n");
		try {
			var waiting = awaitOutput(sessions, id, "waiting", 0);
			var stopped = sessions.cancel(1, 2, id, waiting.nextCursor());
			assertEquals("cancelled", stopped.status());
			assertThrows(SecurityException.class, () -> sessions.poll(1, 2, id, 0));
		} finally { discard(sessions, id); }
		String discarded = sessions.start(1, 2, "input()\n");
		sessions.discard(1, 2, discarded);
		assertThrows(SecurityException.class, () -> sessions.poll(1, 2, discarded, 0));
	}

	private static InteractiveExecutionUpdate awaitOutput(TeacherSubmissionExecutionRegistry sessions, String id,
			String expected, long cursor) {
		return assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
			while (true) {
				var update = sessions.poll(1, 2, id, cursor);
				if (update.events().stream().anyMatch(event -> event.text().contains(expected))) return update;
				assertEquals("running", update.status());
				Thread.sleep(50);
			}
		});
	}
	private static InteractiveExecutionUpdate awaitCompletion(TeacherSubmissionExecutionRegistry sessions, String id,
			long cursor) {
		return assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
			while (true) {
				var update = sessions.poll(1, 2, id, cursor);
				if (!"running".equals(update.status())) return update;
				Thread.sleep(50);
			}
		});
	}
	private static void discard(TeacherSubmissionExecutionRegistry sessions, String id) {
		try { sessions.discard(1, 2, id); } catch (SecurityException ignored) {}
	}
	private static void requireRunner() {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_SUBMISSION_RUNNER_TEST")),
				"Real runner testing is opt-in.");
	}
}
