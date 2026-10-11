package servlet.student;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

class StudentNavigationFilterTest {
	@Test void rejectsMissingIdentityAndOtherRolesBeforeReadingStudentData() throws Exception {
		for (AuthenticatedUser user : new AuthenticatedUser[] { null,
				new AuthenticatedUser(1, "synthetic-teacher", "synthetic-teacher", UserType.TEACHER, false, "test") }) {
			var status = new AtomicInteger();
			var chained = new AtomicInteger();
			var request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
						if ("getAttribute".equals(method.getName())) return user;
						throw new AssertionError("Unexpected request operation: " + method.getName());
					});
			var response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> {
						if ("sendError".equals(method.getName())) { status.set((Integer) args[0]); return null; }
						throw new AssertionError("Unexpected response operation: " + method.getName());
					});
			new StudentNavigationFilter().doFilter(request, response, (r, s) -> chained.incrementAndGet());
			assertEquals(403, status.get());
			assertEquals(0, chained.get());
		}
	}
}
