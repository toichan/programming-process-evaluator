package servlet.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TeacherUi163ContractTest {
	@Test
	void assignmentFormsAreOutsideTheTableInAccessibleTaskScopedModals() throws Exception {
		String jsp = read("WEB-INF/teacher/task/task.jsp");
		String modal = read("WEB-INF/teacher/task/assignment-settings.jspf");
		assertTrue(modal.contains("pageEncoding=\"UTF-8\""));
		String table = jsp.substring(jsp.indexOf("id=\"taskTable\""), jsp.indexOf("</table>", jsp.indexOf("id=\"taskTable\"")));
		assertTrue(table.contains("data-bs-target=\"#taskAssignmentModal-${task.taskId}\""));
		assertFalse(table.contains("name=\"newDueAt\""));
		assertFalse(table.contains("name=\"lateSubmissionPolicy\""));
		assertTrue(jsp.indexOf("assignment-settings.jspf") > jsp.indexOf("</table>", jsp.indexOf("id=\"taskTable\"")));
		assertTrue(modal.contains("modal-dialog-scrollable"));
		assertTrue(modal.contains("aria-labelledby=\"taskAssignmentTitle-${task.taskId}\""));
		assertTrue(modal.contains("value=\"extendTaskAssignmentDeadline\""));
		assertTrue(modal.contains("value=\"addTaskClassAssignment\""));
		for (String name : new String[] { "csrfToken", "requestToken", "taskId", "expectedVersion", "assignmentId", "newDueAt",
				"classroomId", "publishAt", "dueAt", "lateSubmissionPolicy" }) {
			assertTrue(modal.contains("name=\"" + name + "\""), name);
		}
		assertTrue(modal.contains("assignment.assignmentStatus == 'expired'"));
		assertTrue(read("js/teacher/task/task.js").contains("hidden.bs.modal"));
	}

	@Test
	void progressUsesSemanticRoundedStateBadgesWithoutBootstrapSolidColors() throws Exception {
		String jsp = read("WEB-INF/teacher/progress/progress.jsp");
		String css = read("css/teacher/progress/progress.css");
		assertTrue(jsp.contains("class=\"badge progress-status-badge\""));
		assertTrue(jsp.contains("data-status=\"<c:out value='${row.status}'/>\""));
		assertFalse(jsp.contains("bg-warning text-dark"));
		assertTrue(css.contains("border-radius: 999px"));
		for (String status : new String[] { "未着手", "編集中", "提出済み", "評価待ち", "完了", "要対応" }) {
			assertTrue(css.contains("[data-status=\"" + status + "\"]"), status);
		}
	}

	@Test
	void surveyPreservesPostSubmitRedirectAndUsesUpdatedHomeAction() throws Exception {
		assertTrue(read("WEB-INF/student/survey/survey.jsp").contains("home-action.jspf"));
		String servlet = Files.readString(Path.of("src/main/java/servlet/student/StudentSurveyServlet.java"));
		assertTrue(servlet.contains("/student/home"));
	}

	private static String read(String file) throws Exception {
		return Files.readString(Path.of("src/main/webapp").resolve(file));
	}
}
