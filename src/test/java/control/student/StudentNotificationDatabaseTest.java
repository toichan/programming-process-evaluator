package control.student;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import control.auth.AuthenticatedUser;
import control.student.StudentNotificationControl.OpenStatus;
import control.teacher.TeacherReviewFixture;
import dao.StudentNotificationDao;
import entity.StudentNotification;
import entity.StudentNotificationPage;
import entity.UserCredential.UserType;
import lib.mysql.Client;

/**
 * Real MySQL checks against the isolated teacher-review fixture database.
 * Run only with TEACHER_REVIEW_DB_TEST=true and the fixture's isolated DB_HOST/DB_NAME.
 */
class StudentNotificationDatabaseTest {
	private static final String MARKER = "notification-test";

	private long student;
	private long other;
	private long assignment;
	private long submission;
	private long secondSubmission;
	private long completedEvaluation;
	private long pendingEvaluation;
	private long otherSubmission;
	private long otherEvaluation;
	private long rubric;
	private AuthenticatedUser studentUser;
	private AuthenticatedUser otherUser;
	private final StudentNotificationDao dao = new StudentNotificationDao();

	@BeforeEach
	void setUp() throws SQLException {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_REVIEW_DB_TEST")),
				"Set TEACHER_REVIEW_DB_TEST=true with the isolated review DB to run notification DB tests.");
		TeacherReviewFixture.guard(System.getenv());
		try (Connection c = Client.createConnection()) {
			if (number(c, "SELECT COUNT(*) FROM users") == 0) {
				TeacherReviewFixture.install(c);
				c.setAutoCommit(true);
			}
			student = number(c, "SELECT user_id FROM users WHERE login_id='review_student'");
			other = number(c, "SELECT user_id FROM users WHERE login_id='review_withdrawn'");
			completedEvaluation = number(c, "SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-complete'");
			pendingEvaluation = number(c, "SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-pending'");
			otherEvaluation = number(c, "SELECT evaluation_id FROM evaluations WHERE evaluation_code='review-withdrawn'");
			submission = number(c, "SELECT submission_id FROM evaluations WHERE evaluation_id=" + completedEvaluation);
			secondSubmission = number(c, "SELECT submission_id FROM evaluations WHERE evaluation_id=" + pendingEvaluation);
			otherSubmission = number(c, "SELECT submission_id FROM evaluations WHERE evaluation_id=" + otherEvaluation);
			rubric = number(c, "SELECT rubric_id FROM evaluations WHERE evaluation_id=" + completedEvaluation);
			assignment = number(c, """
					SELECT tp.task_class_assignment_id FROM submissions s
					JOIN task_participations tp ON tp.participation_id=s.participation_id
					WHERE s.submission_id=""" + submission);
			cleanup(c);
		}
		studentUser = new AuthenticatedUser(student, "review_student", "review_student", UserType.STUDENT, false, "test");
		otherUser = new AuthenticatedUser(other, "review_withdrawn", "review_withdrawn", UserType.STUDENT, false, "test");
	}

	@AfterEach
	void tearDown() throws SQLException {
		if (studentUser == null) return;
		try (Connection c = Client.createConnection()) {
			cleanup(c);
		}
	}

	private void cleanup(Connection c) throws SQLException {
		update(c, "DELETE FROM student_notifications WHERE student_user_id IN (?,?)", student, other);
		update(c, "DELETE FROM evaluation_requests WHERE model_id=?", MARKER);
		update(c, "DELETE FROM evaluations WHERE evaluation_code LIKE ?", MARKER + "-%");
		update(c, "UPDATE student_class_memberships SET membership_status='active' WHERE student_user_id=?", student);
		update(c, "UPDATE users SET account_status='active' WHERE user_id=?", student);
	}

	@Test void studentInitialCompletionCreatesOneNotificationForThePreciseOlderSubmission() throws SQLException {
		try (Connection c = Client.createConnection()) {
			long request = request(c, completedEvaluation, "student");
			c.setAutoCommit(false);
			StudentNotificationDao.insertEvaluationCompleted(c, request, completedEvaluation);
			StudentNotificationDao.insertEvaluationCompleted(c, request, completedEvaluation);
			c.commit();
			c.setAutoCommit(true);
			StudentNotificationDao.insertEvaluationCompleted(c, request, completedEvaluation);
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE evaluation_id=" + completedEvaluation));
			try (var s = c.prepareStatement("""
					SELECT student_user_id, notification_type, submission_id, task_class_assignment_id, title, message, read_at
					FROM student_notifications WHERE evaluation_id=?""")) {
				s.setLong(1, completedEvaluation);
				try (ResultSet r = s.executeQuery()) {
					assertTrue(r.next());
					assertEquals(student, r.getLong(1));
					assertEquals(StudentNotification.TYPE_EVALUATION_COMPLETED, r.getString(2));
					assertEquals(submission, r.getLong(3), "must link the evaluated (older) submission, not the latest");
					assertNotEquals(secondSubmission, r.getLong(3));
					assertEquals(assignment, r.getLong(4));
					assertEquals("評価が完了しました", r.getString(5));
					assertEquals("「Synthetic Review Task」第1回提出の評価結果を確認できます。", r.getString(6));
					assertNull(r.getObject(7));
				}
			}
		}
	}

	@Test void teacherPreviewReevaluationAndIncompleteEvaluationsCreateNothing() throws SQLException {
		try (Connection c = Client.createConnection()) {
			StudentNotificationDao.insertEvaluationCompleted(c, request(c, completedEvaluation, "teacher"), completedEvaluation);
			StudentNotificationDao.insertEvaluationCompleted(c, request(c, pendingEvaluation, "student"), pendingEvaluation);
			long reevaluation = insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,
					  auto_save_count,execution_count,created_at,completed_at)
					VALUES (?,?,?,'completed','reevaluation',0,0,'2000-01-01','2000-01-01')""",
					MARKER + "-reevaluation", submission, rubric);
			StudentNotificationDao.insertEvaluationCompleted(c, request(c, reevaluation, "student"), reevaluation);
			long mismatched = request(c, otherEvaluation, "student");
			StudentNotificationDao.insertEvaluationCompleted(c, mismatched, completedEvaluation);
			assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id IN (" + student + "," + other + ")"));
		}
	}

	@Test void rolledBackTransactionLeavesNoNotification() throws SQLException {
		try (Connection c = Client.createConnection()) {
			long request = request(c, completedEvaluation, "student");
			c.setAutoCommit(false);
			StudentNotificationDao.insertEvaluationCompleted(c, request, completedEvaluation);
			c.rollback();
			c.setAutoCommit(true);
			assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE evaluation_id=" + completedEvaluation));
		}
	}

	@Test void unreadIndividualAndBulkReadAreScopedToTheOwner() throws SQLException {
		long own1;
		long own2;
		long foreign;
		try (Connection c = Client.createConnection()) {
			own1 = raw(c, student, submission, "NOW(6) - INTERVAL 2 MINUTE");
			own2 = raw(c, student, submission, "NOW(6) - INTERVAL 1 MINUTE");
			foreign = raw(c, other, otherSubmission, "NOW(6)");
		}
		assertEquals(2, dao.countUnread(student));
		assertEquals(List.of(own2, own1), dao.findRecent(student).stream().map(StudentNotification::getNotificationId).toList());
		assertTrue(dao.findRetained(student, foreign).isEmpty());
		assertFalse(dao.markRead(student, foreign), "another student's notification must not be marked");
		assertTrue(dao.markRead(student, own1));
		assertFalse(dao.markRead(student, own1), "already read");
		assertEquals(1, dao.countUnread(student));
		assertTrue(dao.findRetained(student, own1).orElseThrow().isRead());
		assertEquals(1, dao.markAllRead(student));
		assertEquals(0, dao.countUnread(student));
		assertEquals(1, dao.countUnread(other));
		assertFalse(dao.findRetained(other, foreign).orElseThrow().isRead());
	}

	@Test void recentListIsLimitedToFiveNewest() throws SQLException {
		var ids = new ArrayList<Long>();
		try (Connection c = Client.createConnection()) {
			for (int i = 0; i < 7; i++) ids.add(raw(c, student, submission, "NOW(6) - INTERVAL " + (10 - i) + " MINUTE"));
		}
		var recent = dao.findRecent(student).stream().map(StudentNotification::getNotificationId).toList();
		assertEquals(List.of(ids.get(6), ids.get(5), ids.get(4), ids.get(3), ids.get(2)), recent);
	}

	@Test void ninetyDayBoundaryHidesAndListPurgesOnlyTheCurrentStudentsExpiredRows() throws SQLException {
		long retained;
		long exactBoundary;
		long older;
		long foreignExpired;
		try (Connection c = Client.createConnection()) {
			retained = raw(c, student, submission, "NOW(6) - INTERVAL 90 DAY + INTERVAL 60 SECOND");
			exactBoundary = raw(c, student, submission, "NOW(6) - INTERVAL 90 DAY");
			older = raw(c, student, submission, "NOW(6) - INTERVAL 90 DAY - INTERVAL 1 SECOND");
			foreignExpired = raw(c, other, otherSubmission, "NOW(6) - INTERVAL 91 DAY");
		}
		assertTrue(dao.findRetained(student, retained).isPresent());
		assertTrue(dao.findRetained(student, exactBoundary).isEmpty(), "exactly 90 days old is expired");
		assertTrue(dao.findRetained(student, older).isEmpty());
		assertFalse(dao.markRead(student, exactBoundary));
		assertEquals(1, dao.countUnread(student));
		assertEquals(List.of(retained), dao.findRecent(student).stream().map(StudentNotification::getNotificationId).toList());

		StudentNotificationPage page = dao.findPage(student, 1);
		assertEquals(List.of(retained), page.getNotifications().stream().map(StudentNotification::getNotificationId).toList());
		assertEquals(1, page.getUnreadCount());
		try (Connection c = Client.createConnection()) {
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + student));
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE notification_id=" + foreignExpired),
					"purge is limited to the current student");
			assertEquals(1, number(c, "SELECT COUNT(*) FROM evaluations WHERE evaluation_id=" + completedEvaluation),
					"retention purge must not delete business data");
		}
	}

	@Test void pagesAreStableWithIdenticalTimestampsAndOneLookahead() throws SQLException {
		var expected = new ArrayList<Long>();
		try (Connection c = Client.createConnection()) {
			expected.add(raw(c, student, submission, "NOW(6)"));
			String sameInstant = "'" + text(c, "SELECT DATE_FORMAT(NOW(6) - INTERVAL 1 HOUR, '%Y-%m-%d %H:%i:%s.%f')") + "'";
			var sameTime = new ArrayList<Long>();
			for (int i = 0; i < 44; i++) sameTime.add(raw(c, student, submission, sameInstant));
			sameTime.sort((a, b) -> Long.compare(b, a));
			expected.addAll(sameTime);
			assertEquals(1, number(c, "SELECT COUNT(DISTINCT created_at) FROM student_notifications WHERE notification_id IN ("
					+ sameTime.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow() + ")"));
		}
		StudentNotificationPage first = dao.findPage(student, 1);
		StudentNotificationPage second = dao.findPage(student, 2);
		StudentNotificationPage third = dao.findPage(student, 3);
		StudentNotificationPage beyond = dao.findPage(student, 4);
		assertEquals(20, first.getNotifications().size());
		assertTrue(first.isHasNext());
		assertEquals(20, second.getNotifications().size());
		assertTrue(second.isHasNext());
		assertEquals(5, third.getNotifications().size());
		assertFalse(third.isHasNext());
		assertTrue(beyond.getNotifications().isEmpty());
		assertFalse(beyond.isHasNext());
		var seen = new ArrayList<Long>();
		for (var page : List.of(first, second, third)) {
			assertEquals(45, page.getUnreadCount());
			page.getNotifications().forEach(n -> seen.add(n.getNotificationId()));
		}
		assertEquals(expected, seen, "created_at DESC, notification_id DESC without overlap");
		assertEquals(45, new HashSet<>(seen).size());
		assertEquals(seen.subList(0, 20), dao.findPage(student, 1).getNotifications().stream()
				.map(StudentNotification::getNotificationId).toList(), "repeat reads are deterministic");
	}

	@Test void openWithRealAccessChecksMarksOnlyWhenTheEvaluationIsReadable() throws SQLException {
		long notification;
		try (Connection c = Client.createConnection()) {
			StudentNotificationDao.insertEvaluationCompleted(c, request(c, completedEvaluation, "student"), completedEvaluation);
			notification = number(c, "SELECT notification_id FROM student_notifications WHERE evaluation_id=" + completedEvaluation);
		}
		var control = new StudentNotificationControl();

		assertEquals(OpenStatus.NOT_FOUND, control.open(otherUser, notification).status());
		assertFalse(dao.findRetained(student, notification).orElseThrow().isRead());

		try (Connection c = Client.createConnection()) {
			update(c, "UPDATE student_class_memberships SET membership_status='inactive' WHERE student_user_id=?", student);
		}
		assertEquals(OpenStatus.TARGET_UNAVAILABLE, control.open(studentUser, notification).status());
		assertFalse(dao.findRetained(student, notification).orElseThrow().isRead());

		try (Connection c = Client.createConnection()) {
			update(c, "UPDATE student_class_memberships SET membership_status='active' WHERE student_user_id=?", student);
			update(c, "UPDATE users SET account_status='suspended' WHERE user_id=?", student);
		}
		assertThrows(SecurityException.class, () -> control.open(studentUser, notification));
		assertFalse(dao.findRetained(student, notification).orElseThrow().isRead());

		try (Connection c = Client.createConnection()) {
			update(c, "UPDATE users SET account_status='active' WHERE user_id=?", student);
		}
		var opened = control.open(studentUser, notification);
		assertEquals(OpenStatus.OPENED, opened.status());
		assertEquals(assignment, opened.assignmentId());
		assertEquals(submission, opened.submissionId());
		assertTrue(dao.findRetained(student, notification).orElseThrow().isRead());
	}

	@Test void deletingBusinessDataIsNotRequiredForReadState() throws SQLException {
		long id;
		try (Connection c = Client.createConnection()) {
			id = raw(c, student, submission, "NOW(6)");
		}
		assertTrue(dao.markRead(student, id));
		try (Connection c = Client.createConnection()) {
			assertEquals(1, number(c, "SELECT COUNT(*) FROM submissions WHERE submission_id=" + submission));
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE notification_id=" + id + " AND read_at IS NOT NULL"));
		}
	}

	private long request(Connection c, long evaluationId, String actorRole) throws SQLException {
		return insert(c, """
				INSERT INTO evaluation_requests (evaluation_id,model_id,prompt_version,rubric_version,actor_role,
				  consent_status_at_request,request_payload,request_status,retry_count,requested_at)
				VALUES (?,?,'synthetic','synthetic',?,'agreed','{}','in_progress',0,NOW())""",
				evaluationId, MARKER, actorRole);
	}

	private long raw(Connection c, long owner, long submissionId, String createdAtExpression) throws SQLException {
		long assignmentId = number(c, """
				SELECT tp.task_class_assignment_id FROM submissions s
				JOIN task_participations tp ON tp.participation_id=s.participation_id WHERE s.submission_id=""" + submissionId);
		return insert(c, """
				INSERT INTO student_notifications (student_user_id,notification_type,submission_id,task_class_assignment_id,
				  title,message,created_at)
				VALUES (?,'evaluation_completed',?,?,'Synthetic','<script>synthetic</script>',""" + createdAtExpression + ")",
				owner, submissionId, assignmentId);
	}

	private static long number(Connection c, String sql) throws SQLException {
		try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
			assertTrue(r.next(), sql);
			return r.getLong(1);
		}
	}

	private static String text(Connection c, String sql) throws SQLException {
		try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
			assertTrue(r.next(), sql);
			return r.getString(1);
		}
	}

	private static long insert(Connection c, String sql, Object... values) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			bind(s, values);
			s.executeUpdate();
			try (ResultSet keys = s.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private static int update(Connection c, String sql, Object... values) throws SQLException {
		try (PreparedStatement s = c.prepareStatement(sql)) {
			bind(s, values);
			return s.executeUpdate();
		}
	}

	private static void bind(PreparedStatement s, Object... values) throws SQLException {
		for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
	}
}
