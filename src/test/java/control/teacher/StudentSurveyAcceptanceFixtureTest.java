package control.teacher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import lib.mysql.Client;

class StudentSurveyAcceptanceFixtureTest {
	@Test
	void installsSyntheticStudentSurveyRuntimeFixtureForPlaywright() throws Exception {
		Assumptions.assumeTrue("true".equals(System.getenv("TEACHER_REVIEW_DB_TEST")));
		TeacherReviewFixture.guard(System.getenv());
		TeacherReviewFixture.Data data;
		try (var connection = Client.createConnection()) {
			data = TeacherReviewFixture.installOrFind(connection);
		}
		assertTrue(data.student() > 0);
		assertTrue(data.assignment() > 0);
		assertTrue(data.completedEvaluation() > 0);
	}
}
