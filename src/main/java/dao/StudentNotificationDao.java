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
			       task_class_assignment_id, submission_id, distribution_target_id, student_exercise_id,
			       evaluation_id, survey_id
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

	/* Removes only the expired notifications of the student who owns the completed evaluation. */
	private static final String PURGE_EXPIRED_FOR_EVALUATION_OWNER = """
			DELETE n FROM student_notifications n
			JOIN task_participations tp ON tp.student_user_id = n.student_user_id
			JOIN submissions s ON s.participation_id = tp.participation_id
			JOIN evaluations e ON e.submission_id = s.submission_id
			WHERE e.evaluation_id = ? AND n.created_at <= CURRENT_TIMESTAMP(6) - INTERVAL %d DAY
			""".formatted(RETENTION_DAYS);

	/* Removes only the expired notifications of students who belong to the assignment's class. */
	private static final String PURGE_EXPIRED_FOR_ASSIGNMENT_CLASS = """
			DELETE n FROM student_notifications n
			JOIN student_class_memberships scm ON scm.student_user_id = n.student_user_id
			JOIN task_class_assignments ca ON ca.classroom_id = scm.classroom_id
			WHERE ca.task_class_assignment_id = ? AND n.created_at <= CURRENT_TIMESTAMP(6) - INTERVAL %d DAY
			""".formatted(RETENTION_DAYS);

	/*
	 * Only a normal student-initiated initial evaluation produces a notification.
	 * Reevaluation results and teacher previews are excluded by the joined conditions.
	 */
	private static final String INSERT_EVALUATION = """
			INSERT INTO student_notifications
			  (student_user_id, notification_type, evaluation_id, submission_id,
			   task_class_assignment_id, title, message)
			SELECT tp.student_user_id, ?, e.evaluation_id, s.submission_id,
			       tp.task_class_assignment_id, ?,
			       LEFT(CONCAT('「', t.title, '」第', s.revision_number, ?), 500)
			FROM evaluations e
			JOIN evaluation_requests r ON r.evaluation_id = e.evaluation_id
			  AND r.evaluation_request_id = ? AND r.actor_role = 'student'
			JOIN submissions s ON s.submission_id = e.submission_id
			JOIN task_participations tp ON tp.participation_id = s.participation_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			JOIN tasks t ON t.task_id = ca.task_id
			WHERE e.evaluation_id = ?
			  AND (e.evaluation_kind = 'initial'
			    OR (e.evaluation_kind = 'reevaluation' AND EXISTS (
			      SELECT 1 FROM audit_logs retry
			      WHERE retry.target_type = 'evaluation' AND retry.target_id = e.evaluation_id
			        AND retry.actor_role = 'student' AND retry.action_type = 'retry_requested'
			        AND retry.result_status = 'success')))
			  AND e.reevaluation_job_id IS NULL AND e.evaluation_status = ?
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	private static final String INSERT_SURVEY_AVAILABLE = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, evaluation_id, survey_id, task_class_assignment_id, title, message
			)
			SELECT u.user_id, ?, e.evaluation_id, sv.survey_id, ca.task_class_assignment_id,
			       'アンケートに回答できます',
			       LEFT(CONCAT('課題「', t.title, '」の評価についてアンケートに回答できます。'), 500)
			FROM evaluations e
			JOIN evaluation_requests r ON r.evaluation_id = e.evaluation_id
			  AND r.evaluation_request_id = ? AND r.actor_role = 'student'
			JOIN submissions s ON s.submission_id = e.submission_id
			JOIN task_participations tp ON tp.participation_id = s.participation_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			  AND ca.assignment_status = 'published'
			  AND (ca.publish_at IS NULL OR ca.publish_at <= CURRENT_TIMESTAMP)
			JOIN student_class_memberships scm ON scm.student_user_id = tp.student_user_id
			  AND scm.classroom_id = ca.classroom_id AND scm.membership_status = 'active'
			JOIN classrooms c ON c.classroom_id = ca.classroom_id AND c.classroom_status = 'active'
			JOIN users u ON u.user_id = tp.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			JOIN tasks t ON t.task_id = ca.task_id
			  AND t.publication_status = 'published' AND t.deleted_at IS NULL
			JOIN surveys sv ON sv.survey_id = (
			  SELECT MAX(active_survey.survey_id)
			  FROM surveys active_survey
			  WHERE active_survey.task_id = t.task_id AND active_survey.survey_status = 'active'
			)
			WHERE e.evaluation_id = ? AND e.evaluation_status = 'completed'
			  AND e.reevaluation_job_id IS NULL
			  AND (e.evaluation_kind = 'initial'
			    OR (e.evaluation_kind = 'reevaluation' AND EXISTS (
			      SELECT 1 FROM audit_logs retry
			      WHERE retry.target_type = 'evaluation' AND retry.target_id = e.evaluation_id
			        AND retry.actor_role = 'student' AND retry.action_type = 'retry_requested'
			        AND retry.result_status = 'success')))
			  AND EXISTS (
			    SELECT 1 FROM consent_records cr
			    WHERE cr.consent_id = (
			      SELECT latest.consent_id FROM consent_records latest
			      WHERE latest.user_id = u.user_id
			      ORDER BY latest.consent_id DESC LIMIT 1
			    )
			      AND cr.consent_status = 'agreed'
			  )
			  AND EXISTS (
			    SELECT 1 FROM survey_questions sq
			    WHERE sq.survey_id = sv.survey_id AND sq.question_status = 'active'
			  )
			  AND NOT EXISTS (
			    SELECT 1 FROM survey_responses sr
			    WHERE sr.student_user_id = u.user_id AND sr.survey_id = sv.survey_id
			      AND sr.evaluation_id = e.evaluation_id
			  )
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	private static final String INSERT_SURVEY_IN_PROGRESS = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, evaluation_id, survey_id, task_class_assignment_id, title, message
			)
			SELECT u.user_id, ?, e.evaluation_id, sv.survey_id, ca.task_class_assignment_id,
			       'アンケートの回答を再開できます',
			       LEFT(CONCAT('課題「', t.title, '」のアンケート回答が保存されています。続きから回答できます。'), 500)
			FROM survey_responses sr
			JOIN users u ON u.user_id = sr.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			JOIN surveys sv ON sv.survey_id = sr.survey_id AND sv.survey_status = 'active'
			JOIN evaluations e ON e.evaluation_id = sr.evaluation_id AND e.evaluation_status = 'completed'
			JOIN submissions s ON s.submission_id = e.submission_id
			JOIN task_participations tp ON tp.participation_id = s.participation_id
			  AND tp.student_user_id = u.user_id
			JOIN task_class_assignments ca ON ca.task_class_assignment_id = tp.task_class_assignment_id
			  AND ca.assignment_status = 'published'
			  AND (ca.publish_at IS NULL OR ca.publish_at <= CURRENT_TIMESTAMP)
			JOIN student_class_memberships scm ON scm.student_user_id = u.user_id
			  AND scm.classroom_id = ca.classroom_id AND scm.membership_status = 'active'
			JOIN classrooms c ON c.classroom_id = ca.classroom_id AND c.classroom_status = 'active'
			JOIN tasks t ON t.task_id = ca.task_id AND t.publication_status = 'published'
			  AND t.deleted_at IS NULL AND t.task_id = sv.task_id
			WHERE sr.response_status = 'in_progress'
			  AND sr.started_at <= CURRENT_TIMESTAMP - INTERVAL 24 HOUR
			  AND EXISTS (
			    SELECT 1 FROM consent_records cr
			    WHERE cr.consent_id = (
			      SELECT latest.consent_id FROM consent_records latest
			      WHERE latest.user_id = u.user_id
			      ORDER BY latest.consent_id DESC LIMIT 1
			    ) AND cr.consent_status = 'agreed'
			  )
			  AND EXISTS (
			    SELECT 1 FROM survey_questions sq
			    WHERE sq.survey_id = sv.survey_id AND sq.question_status = 'active'
			  )
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	/*
	 * Notifies every active student when an assignment is actually published. A task that supersedes
	 * another revision gets distinct copy while retaining the same assignment-scoped notification
	 * type and deduplication. Scheduled revisions reach this only from the publication worker.
	 */
	private static final String INSERT_TASK_PUBLISHED = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, task_class_assignment_id, title, message
			)
			SELECT u.user_id, ?, ca.task_class_assignment_id,
			       CASE WHEN t.supersedes_task_id IS NOT NULL
			            THEN LEFT(CONCAT('課題「', t.title, '」の改訂版が公開されました'), 255)
			            ELSE LEFT(CONCAT('課題「', t.title, '」が公開されました'), 255)
			       END,
			       CASE WHEN t.supersedes_task_id IS NOT NULL
			            THEN LEFT(CONCAT('クラス「', c.name, '」に課題「', t.title,
			                 '」の新しい改訂版が公開されました。内容を確認してください。'), 500)
			            ELSE LEFT(CONCAT('クラス「', c.name, '」に課題「', t.title,
			                 '」が公開されました。取り組みを開始できます。'), 500)
			       END
			FROM task_class_assignments ca
			JOIN tasks t ON t.task_id = ca.task_id
			JOIN classrooms c ON c.classroom_id = ca.classroom_id
			JOIN student_class_memberships scm ON scm.classroom_id = ca.classroom_id
			  AND scm.membership_status = 'active'
			JOIN users u ON u.user_id = scm.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			WHERE ca.task_class_assignment_id = ?
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	/**
	 * Records the task-published notification for every active student in the assignment's class,
	 * inside the caller's transaction; duplicates per student/assignment are ignored.
	 * The same write also purges the class's students' notifications past the retention period.
	 */
	public static void insertTaskPublished(Connection connection, long assignmentId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_TASK_PUBLISHED)) {
			statement.setString(1, StudentNotification.TYPE_TASK_PUBLISHED);
			statement.setLong(2, assignmentId);
			statement.executeUpdate();
		}
		try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED_FOR_ASSIGNMENT_CLASS)) {
			purge.setLong(1, assignmentId);
			purge.executeUpdate();
		}
	}

	private static final String INSERT_ASSIGNMENT_EXPIRED = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, task_class_assignment_id, title, message
			)
			SELECT u.user_id, ?, ca.task_class_assignment_id,
			       LEFT(CONCAT('課題「', t.title, '」の提出期限を過ぎました'), 255),
			       LEFT(CONCAT('クラス「', c.name, '」の課題「', t.title, '」は提出期限を過ぎました。',
			            CASE WHEN ca.late_submission_policy = 'allow'
			              THEN '期限後の初回提出は可能です。'
			              ELSE '期限後の提出はできません。'
			            END), 500)
			FROM task_class_assignments ca
			JOIN tasks t ON t.task_id = ca.task_id
			JOIN classrooms c ON c.classroom_id = ca.classroom_id
			JOIN student_class_memberships scm ON scm.classroom_id = ca.classroom_id
			  AND scm.membership_status = 'active'
			JOIN users u ON u.user_id = scm.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			LEFT JOIN task_participations tp ON tp.task_class_assignment_id = ca.task_class_assignment_id
			  AND tp.student_user_id = u.user_id
			WHERE ca.task_class_assignment_id = ? AND ca.assignment_status = 'expired'
			  AND (tp.progress_status IS NULL
			    OR tp.progress_status NOT IN ('submitted', 'awaiting_evaluation', 'completed'))
			""";

	/**
	 * Records an expiry notification for each active, unsubmitted student after the assignment state
	 * has transitioned to expired. The caller's state transition and these inserts share a transaction,
	 * so worker retries cannot duplicate the same transition's notifications.
	 */
	public static void insertAssignmentExpired(Connection connection, long assignmentId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_ASSIGNMENT_EXPIRED)) {
			statement.setString(1, StudentNotification.TYPE_ASSIGNMENT_EXPIRED);
			statement.setLong(2, assignmentId);
			statement.executeUpdate();
		}
		try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED_FOR_ASSIGNMENT_CLASS)) {
			purge.setLong(1, assignmentId);
			purge.executeUpdate();
		}
	}

	/*
	 * Notifies every active student of the class whose assignment just had its deadline extended
	 * (this also covers an expired assignment reopened by the same extension). Reads ca.due_at from
	 * the row the caller already updated in this transaction, so the message always reflects the new
	 * deadline. Unlike task_published/due_soon, no dedupe UNIQUE guards this insert: a student may
	 * legitimately receive one of these per distinct extension of the same assignment.
	 */
	private static final String INSERT_DEADLINE_EXTENDED = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, task_class_assignment_id, title, message
			)
			SELECT u.user_id, ?, ca.task_class_assignment_id,
			       LEFT(CONCAT('課題「', t.title, '」の提出期限が延長されました'), 255),
			       LEFT(CONCAT('クラス「', c.name, '」の課題「', t.title, '」の提出期限は ',
			            DATE_FORMAT(ca.due_at, '%Y/%m/%d %H:%i'), ' に延長されました。引き続き提出できます。'), 500)
			FROM task_class_assignments ca
			JOIN tasks t ON t.task_id = ca.task_id
			JOIN classrooms c ON c.classroom_id = ca.classroom_id
			JOIN student_class_memberships scm ON scm.classroom_id = ca.classroom_id
			  AND scm.membership_status = 'active'
			JOIN users u ON u.user_id = scm.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			WHERE ca.task_class_assignment_id = ?
			""";

	private static final String INSERT_EXERCISE_DISTRIBUTED = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, distribution_target_id, student_exercise_id, title, message
			)
			SELECT se.student_user_id, ?, dt.distribution_target_id, se.student_exercise_id,
			       '授業演習にコードが配布されました',
			       LEFT(CONCAT('授業演習「', se.scope_name, '」にコードが配布されました。演習を開いて内容を確認できます。'), 500)
			FROM student_exercises se
			JOIN distribution_targets dt ON dt.distribution_target_id = se.distribution_target_id
			JOIN users u ON u.user_id = se.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			WHERE se.student_user_id = ? AND se.distribution_target_id = ?
			  AND se.student_exercise_id = ? AND se.deleted_at IS NULL
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	/**
	 * Records a deadline-extended notification for every active student in the assignment's class,
	 * inside the caller's transaction (called right after the assignment's due_at is updated).
	 * The same write also purges the class's students' notifications past the retention period.
	 */
	public static void insertDeadlineExtended(Connection connection, long assignmentId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_DEADLINE_EXTENDED)) {
			statement.setString(1, StudentNotification.TYPE_DEADLINE_EXTENDED);
			statement.setLong(2, assignmentId);
			statement.executeUpdate();
		}
		try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED_FOR_ASSIGNMENT_CLASS)) {
			purge.setLong(1, assignmentId);
			purge.executeUpdate();
		}
	}

	/**
	 * Records a notification only for the successfully created, owned delivery scope, in the
	 * distribution transaction. A student/target pair can be notified only once.
	 */
	public static void insertExerciseDistributed(
			Connection connection, long studentUserId, long distributionTargetId, long studentExerciseId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_EXERCISE_DISTRIBUTED)) {
			statement.setString(1, StudentNotification.TYPE_EXERCISE_DISTRIBUTED);
			statement.setLong(2, studentUserId);
			statement.setLong(3, distributionTargetId);
			statement.setLong(4, studentExerciseId);
			statement.executeUpdate();
		}
		try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED)) {
			purge.setLong(1, studentUserId);
			purge.executeUpdate();
		}
	}

	/* Removes only the expired notifications of students who belong to a class with a due-soon-eligible
	 * assignment; this bulk sweep has no single assignment/class to scope by, so it is scoped the same
	 * way the SELECT below targets its recipients. */
	private static final String PURGE_EXPIRED_FOR_DUE_SOON_CANDIDATES = """
			DELETE n FROM student_notifications n
			JOIN student_class_memberships scm ON scm.student_user_id = n.student_user_id
			  AND scm.membership_status = 'active'
			JOIN task_class_assignments ca ON ca.classroom_id = scm.classroom_id
			WHERE ca.assignment_status = 'published' AND ca.due_at IS NOT NULL
			  AND ca.due_at > CURRENT_TIMESTAMP AND ca.due_at <= CURRENT_TIMESTAMP + INTERVAL 24 HOUR
			  AND n.created_at <= CURRENT_TIMESTAMP(6) - INTERVAL %d DAY
			""".formatted(RETENTION_DAYS);

	/*
	 * Reminds every active, not-yet-submitted student once per class assignment that its deadline is
	 * within 24 hours. Assignments published less than 24 hours before their deadline never qualify
	 * (TIMESTAMPDIFF below), and the dedupe UNIQUE on (student_user_id, due_soon_dedupe_key) stops a
	 * later worker pass from inserting a second reminder for the same assignment. Submitted/awaiting
	 * evaluation/completed participations are excluded so a student who already submitted is not reminded.
	 */
	private static final String INSERT_DUE_SOON = """
			INSERT INTO student_notifications (
			  student_user_id, notification_type, task_class_assignment_id, title, message
			)
			SELECT u.user_id, ?, ca.task_class_assignment_id,
			       LEFT(CONCAT('課題「', t.title, '」の提出期限が近づいています'), 255),
			       LEFT(CONCAT('クラス「', c.name, '」の課題「', t.title, '」の提出期限は ',
			            DATE_FORMAT(ca.due_at, '%Y/%m/%d %H:%i'), ' です。期限までに提出してください。'), 500)
			FROM task_class_assignments ca
			JOIN tasks t ON t.task_id = ca.task_id
			JOIN classrooms c ON c.classroom_id = ca.classroom_id
			JOIN student_class_memberships scm ON scm.classroom_id = ca.classroom_id
			  AND scm.membership_status = 'active'
			JOIN users u ON u.user_id = scm.student_user_id AND u.user_type = 'student'
			  AND u.account_status = 'active' AND u.deleted_at IS NULL
			LEFT JOIN task_participations tp ON tp.task_class_assignment_id = ca.task_class_assignment_id
			  AND tp.student_user_id = u.user_id
			WHERE ca.assignment_status = 'published'
			  AND ca.due_at IS NOT NULL
			  AND ca.due_at > CURRENT_TIMESTAMP
			  AND ca.due_at <= CURRENT_TIMESTAMP + INTERVAL 24 HOUR
			  AND TIMESTAMPDIFF(MINUTE, ca.updated_at, ca.due_at) >= 1440
			  AND (tp.progress_status IS NULL
			    OR tp.progress_status NOT IN ('submitted', 'awaiting_evaluation', 'completed'))
			ON DUPLICATE KEY UPDATE notification_id = notification_id
			""";

	/**
	 * Scans every published, due-within-24-hours class assignment and records a one-time due-soon
	 * reminder for each active student who has not yet submitted, inside the caller's transaction.
	 * The same write also purges due-soon candidates' expired notifications. Returns the number of
	 * reminders inserted (ignoring rows skipped by the dedupe UNIQUE).
	 */
	public static int insertDueSoonReminders(Connection connection) throws SQLException {
		int inserted;
		try (PreparedStatement statement = connection.prepareStatement(INSERT_DUE_SOON)) {
			statement.setString(1, StudentNotification.TYPE_DUE_SOON);
			inserted = statement.executeUpdate();
		}
		try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED_FOR_DUE_SOON_CANDIDATES)) {
			purge.executeUpdate();
		}
		return inserted;
	}

	/**
	 * Records the completion notification inside the caller's transaction; duplicates are ignored.
	 * The same write also purges the owning student's notifications past the retention period.
	 */
	public static void insertEvaluationCompleted(
			Connection connection,
			long evaluationRequestId,
			long evaluationId) throws SQLException {
		insertEvaluationNotification(connection, evaluationRequestId, evaluationId,
				StudentNotification.TYPE_EVALUATION_COMPLETED, "completed", "評価が完了しました",
				"回提出の評価結果を確認できます。");
	}

	public static void insertEvaluationFailed(
			Connection connection, long evaluationRequestId, long evaluationId) throws SQLException {
		insertEvaluationNotification(connection, evaluationRequestId, evaluationId,
				StudentNotification.TYPE_EVALUATION_FAILED, "failed", "評価に失敗しました",
				"回提出の評価処理に失敗しました。評価画面で現在の状態と再試行可否を確認してください。");
	}

	/**
	 * Notifies a consented student when their own completed evaluation has an active, answerable survey.
	 * The caller's evaluation transaction owns this write; existing evaluations are never backfilled.
	 */
	public static void insertSurveyAvailable(
			Connection connection, long evaluationRequestId, long evaluationId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_SURVEY_AVAILABLE)) {
			statement.setString(1, StudentNotification.TYPE_SURVEY_AVAILABLE);
			statement.setLong(2, evaluationRequestId);
			statement.setLong(3, evaluationId);
			statement.executeUpdate();
		}
	}

	/**
	 * Creates one reminder for a saved, incomplete survey response after 24 hours. The database
	 * uniqueness constraint makes repeated worker passes idempotent per student and evaluation.
	 */
	public static int insertSurveyInProgressReminders(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_SURVEY_IN_PROGRESS)) {
			statement.setString(1, StudentNotification.TYPE_SURVEY_IN_PROGRESS);
			return statement.executeUpdate();
		}
	}

	private static void insertEvaluationNotification(
			Connection connection, long evaluationRequestId, long evaluationId,
			String type, String status, String title, String suffix) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_EVALUATION)) {
			statement.setString(1, type);
			statement.setString(2, title);
			statement.setString(3, suffix);
			statement.setLong(4, evaluationRequestId);
			statement.setLong(5, evaluationId);
			statement.setString(6, status);
			statement.executeUpdate();
		}
		try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED_FOR_EVALUATION_OWNER)) {
			purge.setLong(1, evaluationId);
			purge.executeUpdate();
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
			List<StudentNotification> rows = find(connection, studentUserId,
					StudentNotificationPage.PAGE_SIZE + 1, (int) offset);
			int unread = countUnread(connection, studentUserId);
			boolean hasNext = rows.size() > StudentNotificationPage.PAGE_SIZE;
			return new StudentNotificationPage(
					hasNext ? rows.subList(0, StudentNotificationPage.PAGE_SIZE) : rows,
					unread, pageNumber, hasNext);
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

	/** Marks one retained notification read and purges the same student's expired rows atomically. */
	public boolean markRead(long studentUserId, long notificationId) throws SQLException {
		return inPurgingTransaction(studentUserId, connection -> {
			try (PreparedStatement statement = connection.prepareStatement(MARK_READ)) {
				statement.setLong(1, notificationId);
				statement.setLong(2, studentUserId);
				return statement.executeUpdate();
			}
		}) == 1;
	}

	/** Marks the student's retained notifications read and purges the same student's expired rows atomically. */
	public int markAllRead(long studentUserId) throws SQLException {
		return inPurgingTransaction(studentUserId, connection -> {
			try (PreparedStatement statement = connection.prepareStatement(MARK_ALL_READ)) {
				statement.setLong(1, studentUserId);
				return statement.executeUpdate();
			}
		});
	}

	private interface Write {
		int apply(Connection connection) throws SQLException;
	}

	private static int inPurgingTransaction(long studentUserId, Write write) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				int updated = write.apply(connection);
				try (PreparedStatement purge = connection.prepareStatement(PURGE_EXPIRED)) {
					purge.setLong(1, studentUserId);
					purge.executeUpdate();
				}
				connection.commit();
				return updated;
			} catch (SQLException | RuntimeException failure) {
				connection.rollback();
				throw failure;
			}
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
		long distributionTargetId = resultSet.getLong("distribution_target_id");
		Long distributionTarget = resultSet.wasNull() ? null : distributionTargetId;
		long studentExerciseId = resultSet.getLong("student_exercise_id");
		Long studentExercise = resultSet.wasNull() ? null : studentExerciseId;
		long evaluationId = resultSet.getLong("evaluation_id");
		Long evaluation = resultSet.wasNull() ? null : evaluationId;
		long surveyId = resultSet.getLong("survey_id");
		Long survey = resultSet.wasNull() ? null : surveyId;
		return new StudentNotification(
				resultSet.getLong("notification_id"),
				resultSet.getString("notification_type"),
				resultSet.getString("title"),
				resultSet.getString("message"),
				createdAt.toLocalDateTime(),
				resultSet.getTimestamp("read_at") != null,
				assignment,
				submission,
				distributionTarget,
				studentExercise,
				evaluation,
				survey);
	}
}
