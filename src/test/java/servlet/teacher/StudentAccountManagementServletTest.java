package servlet.teacher;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import lib.web.CsvCells;
import servlet.auth.CsrfTokens;

class StudentAccountManagementServletTest {
	@Test void validatesTargetsAndProtectsCsvCells() {
		assertEquals(2, StudentAccountManagementServlet.targets(new String[]{"1","2"}, new String[]{"1","3"}).size());
		assertThrows(IllegalArgumentException.class, () -> StudentAccountManagementServlet.targets(new String[]{"1"}, new String[]{}));
		assertThrows(IllegalArgumentException.class, () -> StudentAccountManagementServlet.targets(new String[]{"0"}, new String[]{"1"}));
		assertThrows(IllegalArgumentException.class, () -> StudentAccountManagementServlet.number("-1"));
		assertEquals("\"'=SUM(A1)\"", CsvCells.encode("=SUM(A1)"));
		assertEquals("\"a\"\"b,c\"", CsvCells.encode("a\"b,c"));
	}

	@Test void rejectsManuallyProvidedPasswordWhenCreatingStudentAccounts() throws Exception {
		Map<String, Object> sessionValues = new HashMap<>();
		HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
				new Class<?>[] { HttpSession.class }, (proxy, method, args) -> switch (method.getName()) {
					case "getAttribute" -> sessionValues.get(args[0]);
					case "setAttribute" -> sessionValues.put((String) args[0], args[1]);
					case "removeAttribute" -> sessionValues.remove(args[0]);
					default -> null;
				});
		String csrfToken = CsrfTokens.getOrCreate(session);
		Map<String, String> parameters = Map.of("action", "create", "csrfToken", csrfToken,
				"changeConfirmed", "yes", "password", "Synthetic12!Aa");
		HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
				HttpServletRequest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
				(proxy, method, args) -> {
					if ("getParameter".equals(method.getName())) return parameters.get(args[0]);
					if ("getSession".equals(method.getName())) return session;
					return null;
				});
		AtomicInteger status = new AtomicInteger();
		StringWriter body = new StringWriter();
		PrintWriter writer = new PrintWriter(body);
		HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
				HttpServletResponse.class.getClassLoader(), new Class<?>[] { HttpServletResponse.class },
				(proxy, method, args) -> {
					if ("setStatus".equals(method.getName())) status.set((int) args[0]);
					if ("getWriter".equals(method.getName())) return writer;
					return null;
				});

		new StudentAccountManagementServlet().doPost(request, response);

		assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
		assertTrue(body.toString().contains("初期パスワードはシステムが自動生成します。"));
	}
}
