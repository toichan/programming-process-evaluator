package servlet.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedUiContractTest {
	private String read(String path) throws Exception { return Files.readString(Path.of(path)); }
	@Test void studentLogoutUsesTheExistingSharedConfirmation() throws Exception {
		String footer = read("src/main/webapp/WEB-INF/template/page-end.jspf");
		assertTrue(footer.contains("screenDesign == 'student' or screenDesign == 'teacher' or screenDesign == 'admin'"));
		assertTrue(footer.contains("/js/shared/logout-confirmation.js"));
		String confirmation = read("src/main/webapp/js/shared/logout-confirmation.js");
		assertTrue(confirmation.contains("feedback.confirm"));
		assertTrue(confirmation.contains("HTMLFormElement.prototype.submit"));
	}
	@Test void teacherTablesAreContentSizedAndDoNotEnlargeSelectionCheckboxes() throws Exception {
		String header = read("src/main/webapp/WEB-INF/template/page-start.jspf");
		assertTrue(header.indexOf("/css/teacher/shared/tables.css") > header.indexOf("screenStylesheet"));
		String css = read("src/main/webapp/css/teacher/shared/tables.css");
		assertTrue(css.contains("width: max-content !important"));
		assertTrue(css.contains("min-width: 100% !important"));
		assertTrue(css.contains("overflow-x: auto"));
		assertTrue(css.contains("input:not([type=\"checkbox\"]):not([type=\"radio\"])"));
	}
	@Test void initialAdministratorUsesItsLoginIdWithoutASeparateDisplayName() throws Exception {
		String bootstrap = read("src/main/java/control/auth/InitialAdminBootstrap.java");
		assertFalse(bootstrap.contains("表示名"));
		assertTrue(bootstrap.contains("new PasswordHasher().hash(password), loginId,"));
		String login = read("src/main/java/servlet/auth/LoginServlet.java");
		assertFalse(login.contains("credential.displayName()"));
	}
}
