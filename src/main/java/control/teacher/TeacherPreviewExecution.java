package control.teacher;

import java.io.IOException;
import java.sql.SQLException;
import entity.InteractiveExecutionUpdate;
import entity.StudentExerciseInput;
import control.teacher.TeacherSubmissionExecutionRegistry.Target;

final class TeacherPreviewExecution {
	private static final TeacherSubmissionExecutionRegistry SHARED = new TeacherSubmissionExecutionRegistry();
	private final TeacherSubmissionExecutionRegistry registry;
	TeacherPreviewExecution() { this(SHARED); }
	TeacherPreviewExecution(TeacherSubmissionExecutionRegistry registry) { this.registry = registry; }

	InteractiveExecutionUpdate start(long teacher, Target target, String code, Access access)
			throws SQLException, IOException, InterruptedException {
		StudentExerciseInput.validateCode(code);
		access.check();
		String id = registry.start(teacher, target, code);
		try { return poll(teacher, target, id, 0, access); }
		catch (SQLException | IOException | InterruptedException | RuntimeException failure) {
			discard(teacher, target, id);
			throw failure;
		}
	}
	InteractiveExecutionUpdate poll(long teacher, Target target, String id, long cursor, Access access)
			throws SQLException, IOException, InterruptedException {
		check(teacher, target, id, access);
		return registry.poll(teacher, target, id, cursor);
	}
	void input(long teacher, Target target, String id, String line, Access access)
			throws SQLException, IOException, InterruptedException {
		check(teacher, target, id, access);
		registry.input(teacher, target, id, line);
	}
	InteractiveExecutionUpdate cancel(long teacher, Target target, String id, long cursor, Access access)
			throws SQLException, IOException, InterruptedException {
		check(teacher, target, id, access);
		return registry.cancel(teacher, target, id, cursor);
	}
	private void check(long teacher, Target target, String id, Access access) throws SQLException {
		try { access.check(); }
		catch (SQLException | RuntimeException failure) {
			discard(teacher, target, id);
			throw failure;
		}
	}
	private void discard(long teacher, Target target, String id) {
		try { registry.discard(teacher, target, id); }
		catch (IllegalArgumentException | SecurityException ignored) { /* Do not release another target's session. */ }
	}
	@FunctionalInterface interface Access { void check() throws SQLException; }
}
