package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TeacherTaskPromptRevision162ViewTest {
	@Test
	void taskEditorSavesBeforePromptConfigurationAndKeepsPublicationOutOfTheEditor() throws Exception {
		String taskView = read("WEB-INF/teacher/task/task.jsp");
		String taskScript = read("js/teacher/task/task.js");

		assertTrue(taskView.contains("保存してプロンプト設定"));
		assertTrue(taskView.contains("下書き保存"));
		assertTrue(taskView.contains("value=\"publishTaskFromList\""));
		assertFalse(taskView.contains("id=\"publishTaskButton\""));
		assertTrue(taskScript.contains("'saveAndConfigurePrompt'"));
		assertTrue(taskScript.contains("previewEditorConstraint"));
	}

	@Test
	void hintsKeepLegacyUsageValuesWithoutDisplayingTheUsageField() throws Exception {
		String taskView = read("WEB-INF/teacher/task/task.jsp");
		String taskScript = read("js/teacher/task/task.js");
		String studentView = read("WEB-INF/student/editor.jsp");

		assertTrue(taskView.contains("name=\"hintUsageSyntaxes\""));
		assertFalse(taskView.contains("<label class=\"mini-label\">使用方法</label>"));
		assertFalse(taskScript.contains("createField('使用方法'"));
		assertFalse(taskScript.contains("syntaxValue"));
		assertFalse(studentView.contains("hint.usageSyntax"));
		assertTrue(studentView.contains("fn:split(editorPage.inputConstraints, ';')"));
		assertTrue(studentView.contains("fn:split(editorPage.creationRules, ';')"));
	}

	@Test
	void schoolCommonPromptVersionsAreSharedWhilePreviewsCanBeRediscovered() throws Exception {
		String promptView = read("WEB-INF/teacher/prompt/prompt.jsp");
		String promptScript = read("js/teacher/prompt/prompt.js");
		String servlet = Files.readString(Path.of("src/main/java/servlet/teacher/TeacherPromptServlet.java"));

		assertTrue(promptView.contains("学校共通プロンプトの版履歴"));
		assertTrue(promptView.contains("promptPage.commonPromptVersions"));
		assertFalse(promptView.contains("value=\"copyPromptFromTask\""));
		assertTrue(promptScript.contains("syncDraftFieldsTo(form)"));
		assertTrue(servlet.contains("page.commonPromptVersions().get(0).commonPrompt()"));
		assertTrue(servlet.contains("findOpenPreviewCode(user, taskId)"));
	}

	private static String read(String file) throws Exception {
		return Files.readString(Path.of("src/main/webapp").resolve(file));
	}
}
