package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TeacherProgressStatusTest {
	@Test
	void mapsLearningAndEvaluationStatesWithoutCollapsingSubmissionAndCompletion() {
		assertEquals("未着手", TeacherProgressStatus.label("not_started", null, null, "published", "allow"));
		assertEquals("編集中", TeacherProgressStatus.label("in_progress", null, null, "published", "allow"));
		assertEquals("提出済み", TeacherProgressStatus.label("submitted", null, null, "published", "allow"));
		assertEquals("評価待ち", TeacherProgressStatus.label("awaiting_evaluation", "in_progress", null, "published", "allow"));
		assertEquals("完了", TeacherProgressStatus.label("completed", "completed", null, "published", "allow"));
	}

	@Test
	void currentDraftAndActionableEvaluationTakePrecedenceOverOlderEvaluation() {
		assertEquals("編集中", TeacherProgressStatus.label(
				"in_progress", "needs_revision", null, "published", "allow"));
		assertEquals("要対応", TeacherProgressStatus.label(
				"submitted", "failed", null, "published", "allow"));
		assertEquals("要対応", TeacherProgressStatus.label(
				"needs_action", "completed", null, "published", "allow"));
	}

	@Test
	void deadlineOnlyRequiresActionWhenLateSubmissionIsDenied() {
		Timestamp pastDue = Timestamp.valueOf(LocalDateTime.now().minusMinutes(1));
		assertEquals("要対応", TeacherProgressStatus.label(
				"not_started", null, pastDue, "expired", "deny"));
		assertEquals("未着手", TeacherProgressStatus.label(
				"not_started", null, pastDue, "expired", "allow"));
	}
}
