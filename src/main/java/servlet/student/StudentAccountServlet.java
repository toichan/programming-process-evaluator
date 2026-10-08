package servlet.student;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import entity.StudentAccountDetails;
import servlet.auth.CsrfTokens;

import servlet.auth.ApplicationUrls;

@WebServlet(ApplicationUrls.STUDENT_ACCOUNT)
public final class StudentAccountServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		try {
			StudentAccountDetails account = STUDENTS.loadAccount(user).orElse(null);
			if (account == null) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			request.setAttribute("account", account);
			request.setAttribute("passwordChangeNotice", request.getSession(false).getAttribute("passwordChangeNotice"));
			request.getSession(false).removeAttribute("passwordChangeNotice");
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.getRequestDispatcher("/WEB-INF/student/account/account.jsp").forward(request, response);
		} catch (SQLException e) {
			getServletContext().log("Student account details could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		}
	}
}
