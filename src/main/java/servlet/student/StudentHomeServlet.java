package servlet.student;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import entity.StudentHomePage;
import servlet.auth.CsrfTokens;

@WebServlet("/student/home")
public final class StudentHomeServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String NOTICE_ATTRIBUTE = "studentHomeNotice";
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		AuthenticatedUser user = authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		try {
			StudentHomePage page = STUDENTS.loadHome(user).orElse(null);
			if (page == null) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			request.setAttribute("studentHome", page);
			request.setAttribute("taskCount", page.getTasks().size());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			HttpSession session = request.getSession(false);
			if (session != null) {
				request.setAttribute("passwordChangeNotice", session.getAttribute("passwordChangeNotice"));
				session.removeAttribute("passwordChangeNotice");
				request.setAttribute("studentHomeNotice", session.getAttribute(NOTICE_ATTRIBUTE));
				session.removeAttribute(NOTICE_ATTRIBUTE);
			}
			request.getRequestDispatcher("/WEB-INF/student/home.jsp").forward(request, response);
		} catch (SQLException e) {
			getServletContext().log("Student home data could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		}
	}

	static AuthenticatedUser authenticatedUser(HttpServletRequest request) {
		Object value = request.getAttribute("authenticatedUser");
		return value instanceof AuthenticatedUser user ? user : null;
	}
}
