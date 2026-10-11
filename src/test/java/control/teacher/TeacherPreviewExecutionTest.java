package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TeacherPreviewExecutionTest {
	@Test void targetsIncludeSurfaceAndEveryTargetComponent() {
		var progress = TeacherSubmissionExecutionRegistry.Target.progress(10, 20);
		assertNotEquals(progress, TeacherSubmissionExecutionRegistry.Target.exercise(10, 20, 30));
		assertNotEquals(progress, TeacherSubmissionExecutionRegistry.Target.progress(11, 20));
		assertNotEquals(progress, TeacherSubmissionExecutionRegistry.Target.progress(10, 21));
		assertNotEquals(progress, TeacherSubmissionExecutionRegistry.Target.submission(10));
		assertThrows(IllegalArgumentException.class, () -> TeacherSubmissionExecutionRegistry.Target.exercise(1, 2, 0));
	}

	@Test void realRunnerSeparatesSurfacesAndRechecksAccessBeforeEveryOperation() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_SUBMISSION_RUNNER_TEST")));
		var registry = new TeacherSubmissionExecutionRegistry();
		var target = TeacherSubmissionExecutionRegistry.Target.progress(10, 20);
		String id = registry.start(1, target, "input()\n");
		try {
			assertThrows(SecurityException.class, () -> registry.poll(1,
					TeacherSubmissionExecutionRegistry.Target.exercise(10, 20, 30), id, 0));
			assertThrows(SecurityException.class, () -> registry.input(1,
					TeacherSubmissionExecutionRegistry.Target.progress(10, 21), id, "no"));
			assertThrows(SecurityException.class, () -> registry.cancel(2, target, id, 0));
			assertEquals("cancelled", registry.cancel(1, target, id, 0).status());
		} finally {
			try { registry.discard(1, target, id); } catch (SecurityException ignored) {}
		}
		int[] checks = {0};
		var execution = new TeacherPreviewExecution(registry);
		var running = execution.start(1, target, "input()\n", () -> checks[0]++);
		assertEquals(2, checks[0], "Check current DB scope before and after runner start.");
		execution.poll(1, target, running.sessionId(), 0, () -> checks[0]++);
		execution.input(1, target, running.sessionId(), "hello", () -> checks[0]++);
		assertEquals(4, checks[0]);
		assertThrows(SecurityException.class, () -> execution.poll(1, target, running.sessionId(), 0,
				() -> { throw new SecurityException("revoked"); }));
		assertThrows(SecurityException.class, () -> registry.poll(1, target, running.sessionId(), 0));
	}
}
