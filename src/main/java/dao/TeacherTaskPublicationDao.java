package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lib.mysql.Client;

public final class TeacherTaskPublicationDao {
	public int processDueAssignments() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				int published = transition(
						connection, "scheduled", "published", "publish_at <= CURRENT_TIMESTAMP", "publish_scheduled");
				int expired = transition(
						connection, "published", "expired", "due_at IS NOT NULL AND due_at <= CURRENT_TIMESTAMP",
						"expire_assignment");
				connection.commit();
				return published + expired;
			} catch (SQLException | RuntimeException failure) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
				throw failure;
			}
		}
	}

	private static int transition(
			Connection connection,
			String fromStatus,
			String toStatus,
			String duePredicate,
			String action) throws SQLException {
		List<Long> taskIds = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT DISTINCT t.task_id
				FROM tasks t
				JOIN task_class_assignments ca ON ca.task_id = t.task_id
				WHERE ca.assignment_status = ? AND %s
				  AND t.publication_status = 'published' AND t.deleted_at IS NULL
				ORDER BY t.task_id
				""".formatted(duePredicate))) {
			statement.setString(1, fromStatus);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					taskIds.add(rows.getLong("task_id"));
				}
			}
		}

		int transitioned = 0;
		for (long taskId : taskIds) {
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT task_id
					FROM tasks
					WHERE task_id = ? AND publication_status = 'published' AND deleted_at IS NULL
					FOR UPDATE
					""")) {
				statement.setLong(1, taskId);
				try (ResultSet rows = statement.executeQuery()) {
					if (!rows.next()) {
						continue;
					}
				}
			}

			List<Long> assignmentIds = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("""
					SELECT task_class_assignment_id
					FROM task_class_assignments
					WHERE task_id = ? AND assignment_status = ? AND %s
					ORDER BY task_class_assignment_id
					FOR UPDATE
					""".formatted(duePredicate))) {
				statement.setLong(1, taskId);
				statement.setString(2, fromStatus);
				try (ResultSet rows = statement.executeQuery()) {
					while (rows.next()) {
						assignmentIds.add(rows.getLong("task_class_assignment_id"));
					}
				}
			}

			for (long assignmentId : assignmentIds) {
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE task_class_assignments
						SET assignment_status = ?, updated_at = CURRENT_TIMESTAMP
						WHERE task_class_assignment_id = ? AND assignment_status = ?
						""")) {
					statement.setString(1, toStatus);
					statement.setLong(2, assignmentId);
					statement.setString(3, fromStatus);
					if (statement.executeUpdate() == 1) {
						recordSystemAudit(connection, assignmentId, action, fromStatus, toStatus);
						transitioned++;
					}
				}
			}
		}
		return transitioned;
	}

	private static void recordSystemAudit(
			Connection connection,
			long assignmentId,
			String action,
			String beforeStatus,
			String afterStatus) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO audit_logs (
				  actor_user_id, actor_role, feature_code, target_type, target_id, action_type,
				  result_status, detail, before_data, after_data, occurred_at
				) VALUES (NULL, 'system', 'task-management', 'task_class_assignment', ?, ?,
				  'success', ?, CAST(? AS JSON), CAST(? AS JSON), CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, assignmentId);
			statement.setString(2, action);
			statement.setString(3, "課題割当状態を " + beforeStatus + " から " + afterStatus + " へ遷移");
			statement.setString(4, "{\"assignmentStatus\":\"" + beforeStatus + "\"}");
			statement.setString(5, "{\"assignmentStatus\":\"" + afterStatus + "\"}");
			statement.executeUpdate();
		}
	}
}
