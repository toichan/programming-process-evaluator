package control.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import dao.StudentEvaluationDao;
import dao.TeacherReviewDao.NotFoundException;
import entity.*;
import entity.UserCredential.UserType;
import lib.mysql.Client;

class TeacherReviewDatabaseTest {
	@Test void fixtureCannotTargetDevelopmentOrParentProductionDatabase() {
		for (String database : new String[] { "ppe", "programming_process_evaluator", "ppe_learning_flow_test_dummy" }) {
			assertThrows(IllegalStateException.class, () -> TeacherReviewFixture.guard(Map.of(
					"TEACHER_REVIEW_DB_TEST", "true", "DB_HOST", "ppe-preparation-20261008-db-1", "DB_NAME", database)));
		}
		assertThrows(IllegalStateException.class, () -> TeacherReviewFixture.guard(Map.of(
				"TEACHER_REVIEW_DB_TEST", "true", "DB_HOST", "localhost", "DB_NAME", "ppe_teacher_review_test_dummy")));
		assertThrows(IllegalStateException.class, () -> TeacherReviewFixture.guard(Map.of()));
	}
	@Test void readsPersistedAutomaticEvaluationsAndSnapshotsWithImmutablePreviewAndCurrentScope() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_REVIEW_DB_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		TeacherReviewFixture.Data data;
		try (var c = Client.createConnection()) { data = TeacherReviewFixture.install(c); }
		var teacher = login(data.teacher(), "review_teacher", UserType.TEACHER);
		var outsider = login(data.outsider(), "review_outsider", UserType.TEACHER);
		var denied = login(data.deniedTeacher(), "review_denied", UserType.TEACHER);
		var control = new TeacherReviewControl((code, input) -> {
			assertEquals("print('preview only')", code);
			assertEquals("dummy stdin", input);
			return new PythonExecutionResult("succeeded", 0, "preview only", "", false, false, null);
		});
		var submissions = control.list(teacher, false, TeacherReviewFilter.empty());
		assertEquals(3, submissions.size());
		assertEquals(4, control.list(teacher, true, TeacherReviewFilter.empty()).size());
		var snapshot = control.detail(teacher, false, data.submission(), null);
		assertEquals("print(input())", snapshot.code());
		assertEquals(1, snapshot.row().matchedCases());
		assertEquals(2, snapshot.row().totalCases());
		assertEquals("dummy", snapshot.checks().getFirst().input());
		assertEquals("different", snapshot.checks().getLast().actualOutput());
		var evaluated = control.detail(teacher, true, data.submission(), data.completedEvaluation());
		assertEquals("review-fixed-v1", evaluated.evaluation().rubricVersion());
		assertEquals("review-fixed-prompt-v1", evaluated.evaluation().promptVersion());
		assertEquals(2, evaluated.evaluation().dimensions().size());
		assertEquals(2, evaluated.evaluation().scores().size());
		assertEquals("Synthetic metric rationale", evaluated.evaluation().scores().getFirst().getRationale());
		assertEquals(4, evaluated.row().thinkingScore());
		assertEquals("<script>dummy only</script>", evaluated.evaluation().reasons().getFirst().getBody());
		assertEquals("Synthetic detail", evaluated.evaluation().reasons().getFirst().getDetails().getFirst());
		assertEquals(2, evaluated.logs().size());
		assertEquals("print(0)", evaluated.logs().getFirst().snapshot());
		assertEquals("print(input())", evaluated.logs().getLast().snapshot());
		var pending = control.detail(teacher, true, data.secondSubmission(), null);
		assertEquals("in_progress", pending.row().evaluationStatus());
		assertEquals(2, pending.evaluations().size());
		assertEquals("failed", pending.evaluations().getLast().status());
		assertEquals(data.submission(), pending.previousCompletedEvaluation().submissionId());
		assertEquals("manual_save", pending.logs().getFirst().eventType());
		assertThrows(NotFoundException.class, () -> control.detail(teacher, true, data.submission(), data.pendingEvaluation()));
		assertThrows(NotFoundException.class, () -> control.detail(outsider, false, data.submission(), null));
		assertThrows(NotFoundException.class, () -> control.detail(outsider, true, data.submission(), null));
		assertTrue(control.list(outsider, true, TeacherReviewFilter.empty()).isEmpty());
		assertThrows(SecurityException.class, () -> control.list(denied, false, TeacherReviewFilter.empty()));
		assertThrows(SecurityException.class, () -> control.detail(denied, true, data.submission(), null));
		assertThrows(SecurityException.class, () -> control.exportCsv(denied, TeacherReviewFilter.empty()));
		assertThrows(NotFoundException.class, () -> control.preview(outsider, data.submission(), "", ""));
		for (var user : new AuthenticatedUser[] { login(data.student(), "review_student", UserType.STUDENT), login(data.admin(), "review_admin", UserType.ADMIN) }) {
			assertThrows(SecurityException.class, () -> control.detail(user, false, data.submission(), null));
			assertThrows(SecurityException.class, () -> control.detail(user, true, data.submission(), null));
		}
		var studentDao = new StudentEvaluationDao();
		assertTrue(studentDao.findEvaluationPage(data.withdrawnStudent(), data.assignment(), data.submission()).isEmpty());
		assertTrue(studentDao.findCodeLogPage(data.withdrawnStudent(), data.submission()).isEmpty());
		assertEquals(2, studentDao.findEvaluationPage(data.student(), data.assignment(), data.submission()).orElseThrow().getEvaluation().getDimensions().size());

		long executions = count("code_executions"), logs = count("code_logs"), evaluations = count("evaluations");
		assertEquals("preview only", control.preview(teacher, data.submission(), "print('preview only')", "dummy stdin").getStandardOutput());
		assertEquals(executions, count("code_executions"));
		assertEquals(logs, count("code_logs"));
		assertEquals(evaluations, count("evaluations"));
		assertEquals(snapshot.code(), control.detail(teacher, false, data.submission(), null).code());

		String csv = control.exportCsv(teacher, TeacherReviewFilter.empty());
		assertTrue(csv.contains("review_student"));
		assertFalse(csv.contains("review_withdrawn"), "Latest withdrawal overrides the earlier agreement.");
		assertEquals(1, count("audit_logs"));
		var filtered = new TeacherReviewFilter(data.school(), data.classroom(), null, "beginner", "not_agreed", null, "", "student", "asc");
		assertEquals(1, control.list(teacher, true, filtered).size());
		assertFalse(control.exportCsv(teacher, filtered).contains("review_withdrawn"));

		try (var c = Client.createConnection()) {
			update(c, "UPDATE student_class_memberships SET membership_status='inactive' WHERE student_user_id=?", data.student());
			update(c, "UPDATE classrooms SET classroom_status='inactive' WHERE classroom_id=?", data.classroom());
			update(c, "UPDATE tasks SET deleted_at=NOW(),publication_status='archived' WHERE task_id=(SELECT task_id FROM task_class_assignments WHERE task_class_assignment_id=?)", data.assignment());
			update(c, "UPDATE task_class_assignments SET assignment_status='archived' WHERE task_class_assignment_id=?", data.assignment());
			try {
				var history = control.detail(teacher, true, data.submission(), null);
				assertTrue(history.row().historyLabel().contains("非在籍"));
				assertTrue(history.row().historyLabel().contains("削除済み課題"));
				assertTrue(history.row().historyLabel().contains("割当終了"));
				assertEquals(3, control.list(teacher, false, TeacherReviewFilter.empty()).size());
			} finally {
				update(c, "UPDATE student_class_memberships SET membership_status='active' WHERE student_user_id=?", data.student());
				update(c, "UPDATE classrooms SET classroom_status='active' WHERE classroom_id=?", data.classroom());
				update(c, "UPDATE tasks SET deleted_at=NULL,publication_status='published' WHERE task_id=(SELECT task_id FROM task_class_assignments WHERE task_class_assignment_id=?)", data.assignment());
				update(c, "UPDATE task_class_assignments SET assignment_status='published' WHERE task_class_assignment_id=?", data.assignment());
			}
			update(c, "UPDATE teacher_school_permissions SET access_status='disabled' WHERE teacher_user_id=?", data.teacher());
			try {
				assertThrows(NotFoundException.class, () -> control.detail(teacher, true, data.submission(), null));
				assertThrows(NotFoundException.class, () -> control.detail(teacher, false, data.submission(), null));
				assertTrue(control.list(teacher, false, TeacherReviewFilter.empty()).isEmpty());
				assertThrows(NotFoundException.class, () -> control.preview(teacher, data.submission(), "", ""));
			} finally { update(c, "UPDATE teacher_school_permissions SET access_status='enabled' WHERE teacher_user_id=?", data.teacher()); }
			var revoking = new TeacherReviewControl((code, input) -> {
				try { update(c, "UPDATE teacher_feature_permissions SET is_enabled=FALSE WHERE teacher_user_id=?", data.teacher()); }
				catch (SQLException failure) { throw new java.io.IOException(failure); }
				return new PythonExecutionResult("succeeded", 0, "not returned", "", false, false, null);
			});
			try {
				assertThrows(SecurityException.class, () -> revoking.preview(teacher, data.submission(), "", ""));
				assertThrows(SecurityException.class, () -> control.exportCsv(teacher, TeacherReviewFilter.empty()));
			} finally { update(c, "UPDATE teacher_feature_permissions SET is_enabled=TRUE WHERE teacher_user_id=?", data.teacher()); }
		}
	}
	private static AuthenticatedUser login(long id, String login, UserType role) { return new AuthenticatedUser(id, login, login, role, false, "dummy-test"); }
	private static long count(String table) throws SQLException {
		try (var c = Client.createConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) { r.next(); return r.getLong(1); }
	}
	private static void update(Connection c, String sql, long id) throws SQLException {
		try (var s = c.prepareStatement(sql)) { s.setLong(1, id); assertTrue(s.executeUpdate() > 0); }
	}
}
