package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import entity.StudentNotification;
import entity.StudentNotificationPage;
import lib.mysql.Client;

public class StudentNotificationDao {
	public static final int RETENTION_DAYS = 90;
	public static final int RECENT_LIMIT = 5;

	static final String RETAINED = "created_at > CURRENT_TIMESTAMP(6) - INTERVAL " + RETENTION_DAYS + " DAY";

	private static final String COLUMNS = """
			SELECT notification_id, notification_type, title, message, created_at, read_at,
			       task_class_assignment_id, submission_id
			FROM student_notifications
			""";

	private static final String FIND_PAGE = COLUMNS + """
			WHERE student_user_id = ? AND %s
			ORDER BY created_at DESC, notification_id DESC
			LIMIT ? OFFSET ?
			""".formatted(RETAINED);

	private static final String FIND_ONE = COLUMNS + """
			WHERE notification_id = ? AND student_user_id = ? AND %s
			""".formatted(RETAINED);

	private static final String COUNT_UNREAD = """
			SELECT COUNT(*) FROM student_notifications
			WHERE student_user_id = ? AND read_at IS NULL AND %s
			""".formatted(RETAINED);

	private static final String MARK_READ = """
			UPDATE student_notifications SET read_at = CURRENT_TIMESTAMP(6)
			WHERE notification_id = ? AND student_user_id = ? AND read_at IS NULL AND %s
			""".formatted(RETAINED);

	private static final String MARK_ALL_READ = """
			UPDATE student_notifications SET read_at = CURRENT_TIMESTAMP(6)
			WHERE student_user_id = ? AND read_at IS NULL AND %s
			""".formatted(RETAINED);

	private static final String PURGE_EXPIRED = """
			DELETE FROM student_notifications
			WHERE student_user_id = ? AND created_at <= CURRENT_TIMESTAMP(6) - INTERVAL %d DAY
			""".formatted(RETENTION_DAYS);

	/*
	 * Only a normal student-initiated initial evaluation produces a notification.
	 * Reevaluation results and teacher previews are excluded by the joined conditions.
	 */
	private static final String INSERT_EVALUATION_COMPLETED = """
			INSERT INTO student_notifications
			  (student_user_id, notification_type, evaluation_id, submission_id,
			   task_class_assignment_id, title, message)
			SELECT tp.student_user_id, 'evaluation_completed', e.evaluation_id, s.submission_id,
			       tp.task_class_assignment_id, '評価が完了しました',
			       LEFT(CONCAT('「', t.title, '」第', s.revision_number, '回提出の評価結果を確認できます。'), 500)
			FROM evaluations e
			JOIN evaluation_requests r ON r.evaluation_id = e.evaluation_id
			  AND r.evaluation_request_id = ? AND r.actor_role = 'student'
			JOIN submissions s ON s.submission_id = e.submission_id
			JOIN task_participations tp ON tp.participation_id = s.participation_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			JOIN tasks t ON t.task_id = ca.task_id
			WHERE e.evaluation_id = ? AND e.evaluation_kind = 'initial'
			  AND e.reevaluation_job_id IS NULL AND e.evaluation_status = 'completed'
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	/** Records the completion notification inside the caller's transaction; duplicates are ignored. */
	public static void insertEvaluationCompleted(
			Connection connection,
			long evaluationRequestId,
			long evaluationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_EVALUATION_COMPLETED)) {
			statement.setLong(1, evaluationRequestId);
			statement.setLong(2, evaluationId);
			statement.executeUpdate();
		}
	}

	public StudentNotificationPage findPage(long studentUserId, int pageNumber) throws SQLException {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("The page number must be positive.");
		}
		long offset = (long) (pageNumber - 1) * StudentNotificationPage.PAGE_SIZE;
		if (offset > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("The page number is too large.");
		}
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED)) {
					purge.setLong(1, studentUserId);
					purge.executeUpdate();
				}
				List<StudentNotification> rows = find(connection, studentUserId,
						StudentNotificationPage.PAGE_SIZE + 1, (int) offset);
				int unread = countUnread(connection, studentUserId);
				connection.commit();
				boolean hasNext = rows.size() > StudentNotificationPage.PAGE_SIZE;
				return new StudentNotificationPage(
						hasNext ? rows.subList(0, StudentNotificationPage.PAGE_SIZE) : rows,
						unread, pageNumber, hasNext);
			} catch (SQLException | RuntimeException failure) {
				connection.rollback();
				throw failure;
			}
		}
	}

	public List<StudentNotification> findRecent(long studentUserId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			return find(connection, studentUserId, RECENT_LIMIT, 0);
		}
	}

	public int countUnread(long studentUserId) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			return countUnread(connection, studentUserId);
		}
	}

	public Optional<StudentNotification> findRetained(long studentUserId, long notificationId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(FIND_ONE)) {
			statement.setLong(1, notificationId);
			statement.setLong(2, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
			}
		}
	}

	public boolean markRead(long studentUserId, long notificationId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(MARK_READ)) {
			statement.setLong(1, notificationId);
			statement.setLong(2, studentUserId);
			return statement.executeUpdate() == 1;
		}
	}

	public int markAllRead(long studentUserId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement(MARK_ALL_READ)) {
			statement.setLong(1, studentUserId);
			return statement.executeUpdate();
		}
	}

	private static List<StudentNotification> find(
			Connection connection, long studentUserId, int limit, int offset) throws SQLException {
		List<StudentNotification> notifications = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(FIND_PAGE)) {
			statement.setLong(1, studentUserId);
			statement.setInt(2, limit);
			statement.setInt(3, offset);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					notifications.add(map(resultSet));
				}
			}
		}
		return notifications;
	}

	private static int countUnread(Connection connection, long studentUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(COUNT_UNREAD)) {
			statement.setLong(1, studentUserId);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		}
	}

	private static StudentNotification map(ResultSet resultSet) throws SQLException {
		Timestamp createdAt = resultSet.getTimestamp("created_at");
		long assignmentId = resultSet.getLong("task_class_assignment_id");
		Long assignment = resultSet.wasNull() ? null : assignmentId;
		long submissionId = resultSet.getLong("submission_id");
		Long submission = resultSet.wasNull() ? null : submissionId;
		return new StudentNotification(
				resultSet.getLong("notification_id"),
				resultSet.getString("notification_type"),
				resultSet.getString("title"),
				resultSet.getString("message"),
				createdAt.toLocalDateTime(),
				resultSet.getTimestamp("read_at") != null,
				assignment,
				submission);
	}
}
