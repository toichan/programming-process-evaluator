package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TaskProgressStatusTest {
	@Test
	void submittedProgressFollowsLatestEvaluation() {
		assertEquals("completed", TaskProgressStatus.forLatestEvaluation("awaiting_evaluation", "completed"));
		assertEquals("needs_action", TaskProgressStatus.forLatestEvaluation("awaiting_evaluation", "failed"));
		assertEquals("needs_action", TaskProgressStatus.forLatestEvaluation("submitted", "needs_revision"));
		assertEquals("awaiting_evaluation", TaskProgressStatus.forLatestEvaluation("completed", "in_progress"));
		assertEquals("submitted", TaskProgressStatus.forLatestEvaluation("submitted", "not_started"));
	}

	@Test
	void currentEditingAndExplicitActionAreNotHiddenByPreviousEvaluation() {
		assertEquals("in_progress", TaskProgressStatus.forLatestEvaluation("in_progress", "completed"));
		assertEquals("not_started", TaskProgressStatus.forLatestEvaluation("not_started", "completed"));
		assertEquals(null, TaskProgressStatus.forLatestEvaluation(null, "completed"));
		assertEquals("needs_action", TaskProgressStatus.forLatestEvaluation("needs_action", "completed"));
	}
}
