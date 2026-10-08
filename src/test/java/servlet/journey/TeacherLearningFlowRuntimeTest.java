package servlet.journey;

import static org.junit.jupiter.api.Assertions.*;
import static servlet.journey.LearningFlowSupport.*;

import java.util.List;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeacherLearningFlowRuntimeTest {
	@Test @Order(1)
	void createsTaskConfiguresRealPromptAndSavesExamplesWithReadBack() throws Exception {
		guard();
		assertEquals(0, number("SELECT COUNT(*) FROM tasks"));
		redirect(new Browser("teacher").get("/teacher/task"), "/teacher/account/login");
		Browser teacher = new Browser("teacher");
		teacher.login("synthetic-learning-teacher", "teacher");
		var page = teacher.get("/teacher/task");
		assertEquals(200, page.statusCode());
		var fields = fields("action", "createDraft", "csrfToken", input(page.body(), "csrfToken"),
				"requestToken", input(page.body(), "requestToken"), "taskId", "0", "expectedVersion", "0",
				"schoolTargets", "1", "classTargets", "1", "lateSubmissionPolicy", "allow",
				"taskName", TITLE, "theme", "Integer input and arithmetic", "difficulty", "beginner",
				"description", "Read one integer n from standard input and print twice n.",
				"features", "Read integer;Multiply by two;Print result", "inputConstraints", "0 <= n <= 100",
				"creationRules", "", "initialCode", "# Write your code here\r\n",
				"testCaseIds", "0", "testCaseTitles", "Double 3", "testCaseInputs", "3\r\n", "testCaseOutputs", "6\r\n");
		redirect(teacher.post("/teacher/task", fields), "/teacher/task");
		assertEquals(TITLE, text("SELECT title FROM tasks"));
		long taskId = number("SELECT task_id FROM tasks");
		assertTrue(teacher.get("/teacher/task?taskId=" + taskId).body().contains(TITLE));
		String promptPath = "/teacher/prompt?taskId=" + taskId;
		String prompt = "You are a Japanese high-school Information I teacher. Evaluate fairly using the supplied "
				+ "rubric, code snapshots and execution evidence. State concrete evidence, distinguish unfinished code "
				+ "from syntax errors, and do not infer copying without evidence. Explain feedback in Japanese.";
		String html = teacher.get(promptPath).body();
		var draft = promptFields(html, "promptDraftForm", "saveDraft");
		draft.put("aiModel", List.of("gemini-3.1-pro-preview"));
		draft.put("commonPrompt", List.of(prompt));
		draft.put("additionalInstruction", List.of(""));
		redirect(teacher.post("/teacher/prompt", draft), "/teacher/prompt");
		assertEquals(prompt, text("SELECT common_prompt FROM prompt_versions"));
		html = teacher.get(promptPath).body();
		draft = promptFields(html, "promptDraftForm", "generateFluctuations");
		draft.put("aiModel", List.of("gemini-3.1-pro-preview"));
		draft.put("commonPrompt", List.of(prompt));
		draft.put("additionalInstruction", List.of(""));
		redirect(teacher.post("/teacher/prompt", draft), "/teacher/prompt");
		assertEquals("completed", text("SELECT fluctuation_generation_status FROM prompt_versions"));
		html = teacher.get(promptPath).body();
		String resolution = form(html, "promptResolutionForm");
		var resolved = promptFields(html, "promptResolutionForm", "generateEvaluationExamples");
		List<String> ids = inputs(resolution, "fluctuationIds");
		assertFalse(ids.isEmpty());
		resolved.put("fluctuationIds", ids);
		resolved.put("resolutionStatuses", ids.stream().map(id -> "resolved").toList());
		resolved.put("teacherResolutions", ids.stream().map(id ->
				"Use the supplied code and execution evidence only; explain missing evidence explicitly.").toList());
		resolved.put("additionalInstruction", List.of("Correct output for input 3 is 6."));
		redirect(teacher.post("/teacher/prompt", resolved), "/teacher/prompt");
		assertTrue(number("SELECT COUNT(*) FROM evaluation_examples") > 0);
		html = teacher.get(promptPath).body();
		var save = promptFields(html, "reevaluationStartForm", "saveEvaluationExamples");
		redirect(teacher.post("/teacher/prompt", save), "/teacher/prompt");
		assertEquals("configured", text("SELECT prompt_status FROM prompt_versions"));
		assertTrue(teacher.get(promptPath).body().contains("未公開課題に適用"));
		teacher.logout("teacher");
		redirect(teacher.get("/teacher/prompt"), "/teacher/account/login");
	}

	@Test @Order(2)
	void appliesConfiguredUnpublishedPromptAndPublishesWithoutCreatingReevaluation() throws Exception {
		guard();
		long taskId = number("SELECT task_id FROM tasks");
		redirect(new Browser("teacher").get("/teacher/prompt?taskId=" + taskId), "/teacher/account/login");
		Browser teacher = new Browser("teacher");
		teacher.login("synthetic-learning-teacher", "teacher");
		String promptPath = "/teacher/prompt?taskId=" + taskId;
		String html = teacher.get(promptPath).body();
		var apply = promptFields(html, "unpublishedPromptApplyForm", "applyUnpublishedPrompt");
		apply.put("expectedTaskVersion", List.of(input(form(html, "unpublishedPromptApplyForm"), "expectedTaskVersion")));
		apply.put("applyConfirmed", List.of("no"));
		assertEquals(400, teacher.post("/teacher/prompt", apply).statusCode());
		apply.put("applyConfirmed", List.of("yes"));
		var invalid = new java.util.HashMap<>(apply);
		invalid.put("csrfToken", List.of("invalid"));
		assertEquals(403, teacher.post("/teacher/prompt", invalid).statusCode());
		invalid = new java.util.HashMap<>(apply);
		invalid.put("expectedTaskVersion", List.of("999"));
		assertEquals(409, teacher.post("/teacher/prompt", invalid).statusCode());
		invalid = new java.util.HashMap<>(apply);
		invalid.put("expectedRowVersion", List.of("999"));
		assertEquals(409, teacher.post("/teacher/prompt", invalid).statusCode());
		assertEquals(0, number("SELECT COUNT(*) FROM tasks WHERE active_prompt_version_id IS NOT NULL"));
		redirect(teacher.post("/teacher/prompt", apply), "/teacher/prompt");
		assertEquals(number("SELECT prompt_version_id FROM prompt_versions"),
				number("SELECT active_prompt_version_id FROM tasks"));
		assertTrue(teacher.get(promptPath).body().contains("適用中"));
		assertEquals(0, number("SELECT COUNT(*) FROM evaluations"));
		assertEquals(0, number("SELECT COUNT(*) FROM reevaluation_jobs"));
		assertEquals(1, number("SELECT COUNT(*) FROM audit_logs WHERE action_type='apply_unpublished_prompt'"));
		var page = teacher.get("/teacher/task?taskId=" + taskId);
		var fields = fields("action", "publishTask", "schoolTargets", "1", "classTargets", "1",
				"lateSubmissionPolicy", "allow", "taskName", TITLE, "theme", "Integer input and arithmetic",
				"difficulty", "beginner", "description", "Read one integer n from standard input and print twice n.",
				"features", "Read integer;Multiply by two;Print result", "inputConstraints", "0 <= n <= 100",
				"creationRules", "", "initialCode", "# Write your code here\r\n",
				"testCaseTitles", "Double 3", "testCaseInputs", "3\r\n", "testCaseOutputs", "6\r\n");
		for (String name : List.of("csrfToken", "requestToken", "taskId", "expectedVersion")) {
			fields.put(name, List.of(input(page.body(), name)));
		}
		fields.put("assignmentIds", List.of(Long.toString(number("SELECT task_class_assignment_id FROM task_class_assignments"))));
		fields.put("testCaseIds", List.of(Long.toString(number("SELECT test_case_id FROM task_test_cases"))));
		redirect(teacher.post("/teacher/task", fields), "/teacher/task");
		assertEquals("published", text("SELECT publication_status FROM tasks"));
		assertEquals("published", text("SELECT assignment_status FROM task_class_assignments"));
		assertTrue(teacher.get("/teacher/task?taskId=" + taskId).body().contains(TITLE));
		apply.put("expectedTaskVersion", List.of(Long.toString(number("SELECT version FROM tasks"))));
		assertEquals(400, teacher.post("/teacher/prompt", apply).statusCode(),
				"Published tasks must not bypass reevaluation.");
		teacher.logout("teacher");
		redirect(teacher.get("/teacher/task"), "/teacher/account/login");
	}

	private static java.util.Map<String, List<String>> promptFields(String html, String id, String action) {
		String form = form(html, id);
		return fields("action", action, "csrfToken", input(form, "csrfToken"), "taskId", input(form, "taskId"),
				"promptVersionId", input(form, "promptVersionId"), "expectedRowVersion", input(form, "expectedRowVersion"));
	}
}
