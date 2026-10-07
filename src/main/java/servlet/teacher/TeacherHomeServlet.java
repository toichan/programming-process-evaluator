package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import control.auth.AuthenticatedUser;
import control.teacher.TeacherNavigationControl;
import servlet.auth.CsrfTokens;

@WebServlet("/teacher/home")
public final class TeacherHomeServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		try {
			var user = (AuthenticatedUser) request.getAttribute("authenticatedUser");
			var summary = new TeacherNavigationControl().load(user);
			request.setAttribute("teacherNavigationSummary", summary);
			request.setAttribute("teacherId", user.loginId());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.getRequestDispatcher("/WEB-INF/teacher/home.jsp").forward(request, response);
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException failure) {
			getServletContext().log("Teacher menu could not be loaded.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}
}
