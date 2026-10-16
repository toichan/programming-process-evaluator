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
			TeacherReviewFixture.installOrFind(c);
			c.setAutoCommit(true);
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
		update(c, "DELETE FROM survey_responses WHERE response_code LIKE ?", MARKER + "-progress-%");
		update(c, "DELETE FROM survey_questions WHERE survey_id IN (SELECT survey_id FROM surveys WHERE title=?)",
				MARKER + " survey");
		update(c, "DELETE FROM surveys WHERE title=?", MARKER + " survey");
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

	@Test void surveyAvailableNotificationRequiresAnAnswerableConsentedStudentEvaluationAndOpensThatSurvey()
			throws SQLException {
		long surveyId;
		try (Connection c = Client.createConnection()) {
			c.setAutoCommit(false);
			long taskId = number(c, "SELECT task_id FROM task_class_assignments WHERE task_class_assignment_id=" + assignment);
			surveyId = insert(c, """
					INSERT INTO surveys (task_id,title,survey_status,created_at)
					VALUES (?,?,'active',NOW())
					""", taskId, MARKER + " survey");
			long questionId = insert(c, """
					INSERT INTO survey_questions (survey_id,question_code,question_type,prompt_text,
					  reason_prompt_text,required,reason_required,sort_order,question_status)
					VALUES (?,'notice-question','text','Synthetic question','',FALSE,FALSE,1,'inactive')
					""", surveyId);

			long request = request(c, completedEvaluation, "student");
			StudentNotificationDao.insertSurveyAvailable(c, request, completedEvaluation);
			assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE notification_type='survey_available'"));

			update(c, "UPDATE survey_questions SET question_status='active' WHERE question_id=?", questionId);
			StudentNotificationDao.insertSurveyAvailable(c, request, completedEvaluation);
			StudentNotificationDao.insertSurveyAvailable(c, request, completedEvaluation);
			StudentNotificationDao.insertSurveyAvailable(
					c, request(c, completedEvaluation, "teacher"), completedEvaluation);
			StudentNotificationDao.insertSurveyAvailable(
					c, request(c, otherEvaluation, "student"), otherEvaluation);
			StudentNotificationDao.insertSurveyAvailable(
					c, request(c, pendingEvaluation, "student"), pendingEvaluation);
			c.commit();
			c.setAutoCommit(true);

			assertEquals(1, number(c, """
					SELECT COUNT(*) FROM student_notifications
					WHERE student_user_id=""" + student + " AND notification_type='survey_available'"));
		}

		StudentNotification notice = dao.findRecent(student).stream()
				.filter(item -> StudentNotification.TYPE_SURVEY_AVAILABLE.equals(item.getNotificationType()))
				.findFirst().orElseThrow();
		assertEquals("アンケートに回答できます", notice.getTitle());
		assertTrue(notice.getMessage().contains("Synthetic Review Task"));
		assertEquals(assignment, notice.getAssignmentId());
		assertEquals(completedEvaluation, notice.getEvaluationId());
		assertEquals(surveyId, notice.getSurveyId());
		assertNull(notice.getSubmissionId());

		var opened = new StudentNotificationControl().open(studentUser, notice.getNotificationId());
		assertEquals(OpenStatus.OPENED, opened.status());
		assertEquals("/student/survey?assignmentId=" + assignment
				+ "&evaluationId=" + completedEvaluation + "&surveyId=" + surveyId, opened.redirectPath());
		assertTrue(dao.findRetained(student, notice.getNotificationId()).orElseThrow().isRead());
	}

	@Test void surveyProgressReminderWaitsTwentyFourHoursRequiresCurrentConsentAndDeduplicates()
			throws SQLException {
		long surveyId;
		long responseId;
		try (Connection c = Client.createConnection()) {
			c.setAutoCommit(false);
			long taskId = number(c, "SELECT task_id FROM task_class_assignments WHERE task_class_assignment_id=" + assignment);
			surveyId = insert(c, """
					INSERT INTO surveys (task_id,title,survey_status,created_at)
					VALUES (?,?,'active',NOW())
					""", taskId, MARKER + " progress survey");
			insert(c, """
					INSERT INTO survey_questions (survey_id,question_code,question_type,prompt_text,
					  reason_prompt_text,required,reason_required,sort_order,question_status)
					VALUES (?,'progress-question','text','Synthetic question','',FALSE,FALSE,1,'active')
					""", surveyId);
			responseId = insert(c, """
					INSERT INTO survey_responses (response_code,record_code,student_user_id,survey_id,task_id,
					  evaluation_id,consent_status_at_submission,response_status,started_at)
					VALUES (?,?, ?,?,?,?,'agreed','in_progress',CURRENT_TIMESTAMP - INTERVAL 20 HOUR)
					""", MARKER + "-progress-student", MARKER + "-progress-student-record",
					student, surveyId, taskId, completedEvaluation);
			insert(c, """
					INSERT INTO survey_responses (response_code,record_code,student_user_id,survey_id,task_id,
					  evaluation_id,consent_status_at_submission,response_status,started_at)
					VALUES (?,?, ?,?,?,?,'withdrawn','in_progress',CURRENT_TIMESTAMP - INTERVAL 25 HOUR)
					""", MARKER + "-progress-withdrawn", MARKER + "-progress-withdrawn-record",
					other, surveyId, taskId, otherEvaluation);
			c.commit();
			assertEquals(0, StudentNotificationDao.insertSurveyInProgressReminders(c),
					"a saved response must not be reminded before 24 hours");
			update(c, "UPDATE survey_responses SET started_at=CURRENT_TIMESTAMP - INTERVAL 25 HOUR WHERE survey_response_id=?",
					responseId);
			assertEquals(1, StudentNotificationDao.insertSurveyInProgressReminders(c));
			StudentNotificationDao.insertSurveyInProgressReminders(c);
			assertEquals(1, number(c, """
					SELECT COUNT(*) FROM student_notifications
					WHERE student_user_id=""" + student + " AND notification_type='survey_in_progress'"),
					"repeated worker passes must not duplicate a student's reminder");
			c.commit();
			assertEquals(0, number(c, """
					SELECT COUNT(*) FROM student_notifications
					WHERE student_user_id=""" + other + " AND notification_type='survey_in_progress'"));
		}

		StudentNotification notice = dao.findRecent(student).stream()
				.filter(item -> StudentNotification.TYPE_SURVEY_IN_PROGRESS.equals(item.getNotificationType()))
				.findFirst().orElseThrow();
		assertEquals("アンケートの回答を再開できます", notice.getTitle());
		assertTrue(notice.getMessage().contains("Synthetic Review Task"));
		assertEquals(assignment, notice.getAssignmentId());
		assertEquals(completedEvaluation, notice.getEvaluationId());
		assertEquals(surveyId, notice.getSurveyId());
		var opened = new StudentNotificationControl().open(studentUser, notice.getNotificationId());
		assertEquals(OpenStatus.OPENED, opened.status());
		assertEquals("/student/survey?assignmentId=" + assignment
				+ "&evaluationId=" + completedEvaluation + "&surveyId=" + surveyId, opened.redirectPath());
		assertTrue(dao.findRetained(student, notice.getNotificationId()).orElseThrow().isRead());
	}

	/*
	 * due_soon rows are seeded through a dedicated task_class_assignment/participation created and rolled back
	 * inside this single transaction, so the shared fixture's own assignment (due_at=NOW()+1 day, updated_at
	 * left NULL by the fixture) never satisfies TIMESTAMPDIFF(...) >= 1440 and is naturally excluded.
	 */
	@Test void dueSoonRemindersCreateOneRowPerEligibleAssignmentAndDedupeOnRerun() throws SQLException {
		try (Connection c = Client.createConnection()) {
			c.setAutoCommit(false);
			long taskId = number(c, "SELECT task_id FROM tasks WHERE task_code='review-task'");
			long classroomId = number(c, "SELECT classroom_id FROM classrooms WHERE name='Synthetic Class'");
			long dueSoonAssignment = insert(c, """
					INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
					  late_submission_policy,resubmission_policy,created_at,updated_at)
					VALUES (?,?,'published',NOW() - INTERVAL 2 DAY,NOW() + INTERVAL 2 HOUR,
					  'deny','allow',NOW() - INTERVAL 2 DAY,NOW() - INTERVAL 2 DAY)""", taskId, classroomId);
			insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,learning_status,
					  progress_status,save_status,evaluation_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'in_progress','in_progress','draft','not_started',NOW(),30)""", student, dueSoonAssignment);

			int inserted = StudentNotificationDao.insertDueSoonReminders(c);
			assertTrue(inserted >= 1);
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + student
					+ " AND notification_type='due_soon' AND task_class_assignment_id=" + dueSoonAssignment));
			var notification = text(c, "SELECT title FROM student_notifications WHERE task_class_assignment_id="
					+ dueSoonAssignment);
			assertEquals("課題「Synthetic Review Task」の提出期限が近づいています", notification);
			String message = text(c, "SELECT message FROM student_notifications WHERE task_class_assignment_id="
					+ dueSoonAssignment);
			assertTrue(message.startsWith("クラス「Synthetic Class」の課題「Synthetic Review Task」の提出期限は "));
			assertTrue(message.endsWith(" です。期限までに提出してください。"));
			assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE task_class_assignment_id="
					+ dueSoonAssignment + " AND (evaluation_id IS NOT NULL OR submission_id IS NOT NULL)"));

			StudentNotificationDao.insertDueSoonReminders(c);
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + student
					+ " AND notification_type='due_soon' AND task_class_assignment_id=" + dueSoonAssignment),
					"a second worker pass must not duplicate the reminder");

			c.rollback();
			c.setAutoCommit(true);
			assertEquals(0, number(c, "SELECT COUNT(*) FROM task_class_assignments WHERE task_class_assignment_id="
					+ dueSoonAssignment), "rollback must leave no trace of the synthetic assignment");
		}
	}

	@Test void dueSoonRemindersExcludeSubmittedAndOutOfWindowAssignments() throws SQLException {
		try (Connection c = Client.createConnection()) {
			c.setAutoCommit(false);
			long taskId = number(c, "SELECT task_id FROM tasks WHERE task_code='review-task'");
			long classroomId = number(c, "SELECT classroom_id FROM classrooms WHERE name='Synthetic Class'");

			long submittedAssignment = assignmentDueSoon(c, taskId, classroomId, "NOW() - INTERVAL 2 DAY");
			insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,learning_status,
					  progress_status,save_status,evaluation_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'completed','submitted','saved','not_started',NOW(),30)""", student, submittedAssignment);

			long tooFarAssignment = insert(c, """
					INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
					  late_submission_policy,resubmission_policy,created_at,updated_at)
					VALUES (?,?,'published',NOW() - INTERVAL 2 DAY,NOW() + INTERVAL 30 HOUR,
					  'deny','allow',NOW() - INTERVAL 2 DAY,NOW() - INTERVAL 2 DAY)""", taskId, classroomId);
			insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,learning_status,
					  progress_status,save_status,evaluation_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'in_progress','in_progress','draft','not_started',NOW(),30)""", student, tooFarAssignment);

			long pastDueAssignment = insert(c, """
					INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
					  late_submission_policy,resubmission_policy,created_at,updated_at)
					VALUES (?,?,'published',NOW() - INTERVAL 2 DAY,NOW() - INTERVAL 1 HOUR,
					  'deny','allow',NOW() - INTERVAL 2 DAY,NOW() - INTERVAL 2 DAY)""", taskId, classroomId);
			insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,learning_status,
					  progress_status,save_status,evaluation_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'in_progress','in_progress','draft','not_started',NOW(),30)""", student, pastDueAssignment);

			long recentlyPublishedAssignment = insert(c, """
					INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
					  late_submission_policy,resubmission_policy,created_at,updated_at)
					VALUES (?,?,'published',NOW() - INTERVAL 10 HOUR,NOW() + INTERVAL 2 HOUR,
					  'deny','allow',NOW() - INTERVAL 10 HOUR,NOW() - INTERVAL 10 HOUR)""", taskId, classroomId);
			insert(c, """
					INSERT INTO task_participations (student_user_id,task_class_assignment_id,learning_status,
					  progress_status,save_status,evaluation_status,last_activity_at,active_duration_seconds)
					VALUES (?,?,'in_progress','in_progress','draft','not_started',NOW(),30)""", student, recentlyPublishedAssignment);

			StudentNotificationDao.insertDueSoonReminders(c);
			for (long excluded : List.of(submittedAssignment, tooFarAssignment, pastDueAssignment, recentlyPublishedAssignment)) {
				assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + student
						+ " AND task_class_assignment_id=" + excluded),
						"assignment " + excluded + " must not receive a due_soon reminder for this student");
			}

			c.rollback();
			c.setAutoCommit(true);
		}
	}

	private long assignmentDueSoon(Connection c, long taskId, long classroomId, String publishAndUpdatedAtExpression)
			throws SQLException {
		return insert(c, """
				INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
				  late_submission_policy,resubmission_policy,created_at,updated_at)
				VALUES (?,?,'published',""" + publishAndUpdatedAtExpression + ",NOW() + INTERVAL 2 HOUR,"
				+ "'deny','allow'," + publishAndUpdatedAtExpression + "," + publishAndUpdatedAtExpression + ")",
				taskId, classroomId);
	}

	/*
	 * deadline_extended intentionally has no dedupe UNIQUE guard: unlike task_published/due_soon, every
	 * distinct extension of the same assignment is newsworthy and must produce its own notification.
	 * A withdrawn classmate must never receive one, and the message must reflect the assignment's
	 * current (already-updated) due_at.
	 */
	@Test void deadlineExtendedNotifiesOnlyActiveClassmatesAndAllowsRepeatExtensions() throws SQLException {
		try (Connection c = Client.createConnection()) {
			c.setAutoCommit(false);
			long taskId = number(c, "SELECT task_id FROM tasks WHERE task_code='review-task'");
			long classroomId = number(c, "SELECT classroom_id FROM classrooms WHERE name='Synthetic Class'");
			long extendedAssignment = insert(c, """
					INSERT INTO task_class_assignments (task_id,classroom_id,assignment_status,publish_at,due_at,
					  late_submission_policy,resubmission_policy,created_at,updated_at)
					VALUES (?,?,'expired',NOW() - INTERVAL 3 DAY,NOW() - INTERVAL 1 HOUR,
					  'deny','allow',NOW() - INTERVAL 3 DAY,NOW() - INTERVAL 3 DAY)""", taskId, classroomId);
			update(c, "UPDATE student_class_memberships SET membership_status='inactive' WHERE student_user_id=? AND classroom_id=?",
					other, classroomId);

			update(c, "UPDATE task_class_assignments SET assignment_status='published', due_at=NOW() + INTERVAL 3 DAY"
					+ " WHERE task_class_assignment_id=" + extendedAssignment);
			StudentNotificationDao.insertDeadlineExtended(c, extendedAssignment);
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE task_class_assignment_id="
					+ extendedAssignment + " AND notification_type='deadline_extended'"));
			assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE task_class_assignment_id="
					+ extendedAssignment + " AND student_user_id=" + other), "an inactive classmate must not be notified");
			var title = text(c, "SELECT title FROM student_notifications WHERE task_class_assignment_id="
					+ extendedAssignment);
			assertEquals("課題「Synthetic Review Task」の提出期限が延長されました", title);
			String message = text(c, "SELECT message FROM student_notifications WHERE task_class_assignment_id="
					+ extendedAssignment);
			assertTrue(message.startsWith("クラス「Synthetic Class」の課題「Synthetic Review Task」の提出期限は "));
			assertTrue(message.endsWith(" に延長されました。引き続き提出できます。"));
			assertEquals(0, number(c, "SELECT COUNT(*) FROM student_notifications WHERE task_class_assignment_id="
					+ extendedAssignment + " AND (evaluation_id IS NOT NULL OR submission_id IS NOT NULL)"));

			update(c, "UPDATE task_class_assignments SET due_at=NOW() + INTERVAL 5 DAY"
					+ " WHERE task_class_assignment_id=" + extendedAssignment);
			StudentNotificationDao.insertDeadlineExtended(c, extendedAssignment);
			assertEquals(2, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + student
					+ " AND task_class_assignment_id=" + extendedAssignment
					+ " AND notification_type='deadline_extended'"),
					"a second, distinct extension must add another notification rather than being deduped");

			c.rollback();
			c.setAutoCommit(true);
			assertEquals(0, number(c, "SELECT COUNT(*) FROM task_class_assignments WHERE task_class_assignment_id="
					+ extendedAssignment), "rollback must leave no trace of the synthetic assignment");
		}
	}

	@Test void failureNotificationIsDeduplicatedAndExcludesTeacherAndReevaluation() throws SQLException {
		try (Connection c = Client.createConnection()) {
			long failed = insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,
					  auto_save_count,execution_count,created_at,completed_at)
					VALUES (?,?,?,'failed','initial',0,0,NOW(6),NOW(6))
					""", MARKER + "-failed", submission, rubric);
			long studentRequest = request(c, failed, "student");
			StudentNotificationDao.insertEvaluationFailed(c, request(c, failed, "teacher"), failed);
			assertEquals(0, dao.countUnread(student));
			StudentNotificationDao.insertEvaluationFailed(c, studentRequest, failed);
			StudentNotificationDao.insertEvaluationFailed(c, studentRequest, failed);
			var notification = dao.findRecent(student).get(0);
			assertEquals(1, dao.countUnread(student));
			assertEquals("evaluation_failed", notification.getNotificationType());
			assertEquals("評価に失敗しました", notification.getTitle());
			assertEquals("「Synthetic Review Task」第1回提出の評価処理に失敗しました。評価画面で現在の状態と再試行可否を確認してください。",
					notification.getMessage());
			assertEquals(submission, notification.getSubmissionId());
			assertEquals(OpenStatus.OPENED, new StudentNotificationControl().open(studentUser,
					notification.getNotificationId()).status());
			long reevaluation = insert(c, """
					INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,
					  auto_save_count,execution_count,created_at,completed_at)
					VALUES (?,?,?,'failed','reevaluation',0,0,NOW(6),NOW(6))
					""", MARKER + "-failed-reevaluation", submission, rubric);
			StudentNotificationDao.insertEvaluationFailed(c, request(c, reevaluation, "student"), reevaluation);
			StudentNotificationDao.insertEvaluationFailed(c, request(c, completedEvaluation, "student"), completedEvaluation);
			assertEquals(1, dao.findRecent(student).size());
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

	@Test void ninetyDayBoundaryHidesExpiredRowsAndReadsNeverDelete() throws SQLException {
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
		assertEquals(1, dao.countUnread(student));
		assertEquals(List.of(retained), dao.findRecent(student).stream().map(StudentNotification::getNotificationId).toList());
		StudentNotificationPage page = dao.findPage(student, 1);
		assertEquals(List.of(retained), page.getNotifications().stream().map(StudentNotification::getNotificationId).toList());
		assertEquals(1, page.getUnreadCount());
		try (Connection c = Client.createConnection()) {
			assertEquals(4, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id IN ("
					+ student + "," + other + ")"), "GET reads are state-free and never purge");
		}
	}

	@Test void individualReadPurgesOnlyTheCurrentStudentsExpiredRowsWithoutBusinessData() throws SQLException {
		long retained;
		long exactBoundary;
		long foreignExpired;
		try (Connection c = Client.createConnection()) {
			retained = raw(c, student, submission, "NOW(6) - INTERVAL 1 DAY");
			exactBoundary = raw(c, student, submission, "NOW(6) - INTERVAL 90 DAY");
			foreignExpired = raw(c, other, otherSubmission, "NOW(6) - INTERVAL 91 DAY");
		}
		assertFalse(dao.markRead(student, exactBoundary), "an expired notification is never marked read");
		try (Connection c = Client.createConnection()) {
			assertEquals(List.of(retained), ids(c, student), "the write purged the student's expired row");
			assertEquals(List.of(foreignExpired), ids(c, other), "purge is limited to the current student");
			assertEquals(1, number(c, "SELECT COUNT(*) FROM evaluations WHERE evaluation_id=" + completedEvaluation));
			assertEquals(1, number(c, "SELECT COUNT(*) FROM submissions WHERE submission_id=" + submission),
					"retention purge must not delete business data");
		}
		assertFalse(dao.findRetained(student, retained).orElseThrow().isRead());
	}

	@Test void bulkReadPurgesOnlyTheCurrentStudentsExpiredRows() throws SQLException {
		long retained;
		long foreignExpired;
		try (Connection c = Client.createConnection()) {
			retained = raw(c, student, submission, "NOW(6) - INTERVAL 89 DAY");
			raw(c, student, submission, "NOW(6) - INTERVAL 120 DAY");
			foreignExpired = raw(c, other, otherSubmission, "NOW(6) - INTERVAL 91 DAY");
		}
		assertEquals(1, dao.markAllRead(student), "only the retained notification is marked");
		try (Connection c = Client.createConnection()) {
			assertEquals(List.of(retained), ids(c, student));
			assertEquals(List.of(foreignExpired), ids(c, other));
		}
	}

	@Test void workerCompletionPurgesOnlyTheEvaluationOwnersExpiredRows() throws SQLException {
		long foreignExpired;
		try (Connection c = Client.createConnection()) {
			raw(c, student, submission, "NOW(6) - INTERVAL 90 DAY");
			foreignExpired = raw(c, other, otherSubmission, "NOW(6) - INTERVAL 91 DAY");
			long request = request(c, completedEvaluation, "student");
			c.setAutoCommit(false);
			StudentNotificationDao.insertEvaluationCompleted(c, request, completedEvaluation);
			c.rollback();
			c.setAutoCommit(true);
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE student_user_id=" + student),
					"a rolled back completion keeps the expired row and adds nothing");
			StudentNotificationDao.insertEvaluationCompleted(c, request, completedEvaluation);
			assertEquals(List.of(number(c, "SELECT notification_id FROM student_notifications WHERE evaluation_id="
					+ completedEvaluation)), ids(c, student));
			assertEquals(List.of(foreignExpired), ids(c, other));
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
		assertEquals("/student/evaluation?assignmentId=" + assignment + "&submissionId=" + submission,
				opened.redirectPath());
		assertTrue(dao.findRetained(student, notification).orElseThrow().isRead());
		var repeatedOpen = control.open(studentUser, notification);
		assertEquals(OpenStatus.OPENED, repeatedOpen.status());
		assertTrue(dao.findPage(student, 1).getNotifications().stream()
				.filter(item -> item.getNotificationId() == notification).findFirst().orElseThrow().isRead());
		try (Connection c = Client.createConnection()) {
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE notification_id=" + notification));
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE notification_id="
					+ notification + " AND read_at IS NOT NULL"));
		}
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

	private int rawSequence;

	/** Seeds a notification with its own synthetic completed evaluation, as the schema requires real refs. */
	private long raw(Connection c, long owner, long submissionId, String createdAtExpression) throws SQLException {
		long assignmentId = number(c, """
				SELECT tp.task_class_assignment_id FROM submissions s
				JOIN task_participations tp ON tp.participation_id=s.participation_id WHERE s.submission_id=""" + submissionId);
		long evaluationId = insert(c, """
				INSERT INTO evaluations (evaluation_code,submission_id,rubric_id,evaluation_status,evaluation_kind,
				  auto_save_count,execution_count,created_at,completed_at)
				VALUES (?,?,?,'completed','initial',0,0,'2000-01-01','2000-01-01')""",
				MARKER + "-raw-" + (++rawSequence), submissionId, rubric);
		return insert(c, """
				INSERT INTO student_notifications (student_user_id,notification_type,evaluation_id,submission_id,
				  task_class_assignment_id,title,message,created_at)
				VALUES (?,'evaluation_completed',?,?,?,'Synthetic','<script>synthetic</script>',""" + createdAtExpression + ")",
				owner, evaluationId, submissionId, assignmentId);
	}

	@Test void schemaRejectsEvaluationNoticeWithoutTargetsAndRestrictsBusinessDeletes() throws SQLException {
		try (Connection c = Client.createConnection()) {
			for (String nullColumn : List.of("evaluation_id", "submission_id", "task_class_assignment_id")) {
				String values = "?,'evaluation_completed',"
						+ (nullColumn.equals("evaluation_id") ? "NULL" : String.valueOf(completedEvaluation)) + ","
						+ (nullColumn.equals("submission_id") ? "NULL" : String.valueOf(submission)) + ","
						+ (nullColumn.equals("task_class_assignment_id") ? "NULL" : String.valueOf(assignment));
				assertThrows(SQLException.class, () -> update(c, """
						INSERT INTO student_notifications (student_user_id,notification_type,evaluation_id,submission_id,
						  task_class_assignment_id,title,message) VALUES (""" + values + ",'t','m')", student),
						nullColumn + " is required for evaluation_completed");
			}
			assertThrows(SQLException.class, () -> update(c, """
					INSERT INTO student_notifications (student_user_id,notification_type,evaluation_id,submission_id,
					  task_class_assignment_id,title,message) VALUES (?,'other',?,?,?,'t','m')""",
					student, completedEvaluation, submission, assignment), "only evaluation_completed is allowed");
			long seededEvaluation = number(c, "SELECT evaluation_id FROM student_notifications WHERE notification_id="
					+ raw(c, student, submission, "NOW(6)"));
			assertThrows(SQLException.class, () -> update(c, "DELETE FROM evaluations WHERE evaluation_id=?", seededEvaluation),
					"business rows are RESTRICTed, never cascaded");
			assertEquals(1, number(c, "SELECT COUNT(*) FROM student_notifications WHERE evaluation_id=" + seededEvaluation));
		}
	}

	private static List<Long> ids(Connection c, long owner) throws SQLException {
		var ids = new ArrayList<Long>();
		try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
				"SELECT notification_id FROM student_notifications WHERE student_user_id=" + owner + " ORDER BY notification_id")) {
			while (r.next()) ids.add(r.getLong(1));
		}
		return ids;
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
