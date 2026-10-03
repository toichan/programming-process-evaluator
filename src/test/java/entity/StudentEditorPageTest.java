package entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class StudentEditorPageTest {
	@Test
	void firstSubmissionAfterDeadlineRequiresLateSubmissionPermission() {
		LocalDateTime pastDue = LocalDateTime.now(Clock.systemUTC()).minusDays(1);

		assertTrue(page(pastDue, "allow", null, null, "not_started").isCanSubmit());
		assertFalse(page(pastDue, "deny", null, null, "not_started").isCanSubmit());
	}

	@Test
	void resubmissionIsAllowedRegardlessOfAssignmentPolicyBeforeDeadline() {
		LocalDateTime futureDue = LocalDateTime.now(Clock.systemUTC()).plusDays(1);
		StudentEditorPage submitted = page(futureDue, "deny", 13L, null, "completed");
		assertTrue(submitted.isCanStartResubmission());

		StudentEditorPage editing = page(futureDue, "deny", 13L, 13L, "in_progress");
		assertTrue(editing.isEditable());
		assertTrue(editing.isResubmissionActive());
		assertTrue(editing.isCanSubmit());

		StudentEditorPage expired = page(
				LocalDateTime.now(Clock.systemUTC()).minusDays(1), "deny", 13L, 13L, "in_progress");
		assertFalse(expired.isCanSubmit());
		assertFalse(expired.isCanStartResubmission());
	}

	private static StudentEditorPage page(
			LocalDateTime dueAt,
			String latePolicy,
			Long latestSubmissionId,
			Long draftBaseSubmissionId,
			String learningStatus) {
		return new StudentEditorPage(
				1, 2, 3, "Test task", null, "beginner", "Description", null, null, null, "print('hello')",
				learningStatus, "not_started", "unsaved", latePolicy,
				dueAt, null, draftBaseSubmissionId, latestSubmissionId,
				latestSubmissionId == null ? null : 1, latestSubmissionId == null ? null : "accepted",
				latestSubmissionId == null ? null : "print('hello')", null,
				List.of(), List.of(), List.of(), List.of());
	}
}
