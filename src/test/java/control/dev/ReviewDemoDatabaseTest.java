package control.dev;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import dao.TeacherReviewDao;
import dao.TeacherSurveyDao;
import lib.mysql.Client;

class ReviewDemoDatabaseTest {
	@Test void persistedSyntheticResultsAreReadableThroughAuthorizedDaos() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("PPE_SEED_REVIEW_DEMO")));
		ReviewDemoBootstrap.requireLocalTarget(System.getenv());
		try (var c = Client.createConnection()) {
			c.setAutoCommit(false);
			long teacher;
			try (var s = c.prepareStatement("SELECT user_id FROM users WHERE login_id='test-teacher' AND user_type='teacher' AND account_status='active'");
					var r = s.executeQuery()) {
				assertTrue(r.next());
				teacher = r.getLong(1);
			}
			var evaluations = new TeacherReviewDao().rows(c, teacher, true).stream()
					.filter(row -> row.taskName().startsWith("【デモ・仮データ】")
							&& !row.taskName().contains("s008") && row.evaluationId() != null).toList();
			assertEquals(2, evaluations.size());
			for (var row : evaluations) {
				var detail = new TeacherReviewDao().detail(c, teacher, true, row.submissionId(), row.evaluationId());
				assertFalse(detail.logs().isEmpty());
			}
			var surveys = new TeacherSurveyDao().rows(c, teacher).stream()
					.filter(row -> row.responseCode().startsWith("LOCAL-REVIEW-DEMO-")).toList();
			assertEquals(2, surveys.size());
			for (var row : surveys) {
				assertEquals("submitted", row.completionStatus());
				assertEquals(6, row.answers().size());
				assertNotNull(row.systemThinkingScore());
				assertNotNull(row.systemAttitudeScore());
			}
			try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
				assertTrue(r.next());
				assertEquals(23, r.getInt(1));
			}
			c.rollback();
		}
	}
}
