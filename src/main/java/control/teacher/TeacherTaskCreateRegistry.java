package control.teacher;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.UUID;

public final class TeacherTaskCreateRegistry implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final int MAX_RETAINED_TOKENS = 32;

	private final LinkedHashMap<String, Long> outcomes = new LinkedHashMap<>(16, 0.75f, true);

	public synchronized String issueToken() {
		String token = UUID.randomUUID().toString();
		outcomes.put(token, null);
		while (outcomes.size() > MAX_RETAINED_TOKENS) {
			String oldest = outcomes.keySet().iterator().next();
			outcomes.remove(oldest);
		}
		return token;
	}

	public synchronized long createOrReuse(String token, DraftCreation creation) throws SQLException {
		if (!isUuid(token) || !outcomes.containsKey(token)) {
			throw new SecurityException("The task creation token is invalid or expired.");
		}
		Long existingTaskId = outcomes.get(token);
		if (existingTaskId != null) {
			return existingTaskId;
		}
		long taskId = creation.create();
		if (taskId < 1) {
			throw new IllegalStateException("Task creation did not return a valid identifier.");
		}
		outcomes.put(token, taskId);
		return taskId;
	}

	private static boolean isUuid(String token) {
		if (token == null || !token.matches(
				"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			return false;
		}
		return UUID.fromString(token).toString().equalsIgnoreCase(token);
	}

	@FunctionalInterface
	public interface DraftCreation {
		long create() throws SQLException;
	}
}
