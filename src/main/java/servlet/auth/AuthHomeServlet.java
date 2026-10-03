package servlet.auth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import control.auth.AuthenticatedUser;

@WebServlet("/teacher/home")
public final class AuthHomeServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AuthenticatedUser user = (AuthenticatedUser) request.getAttribute("authenticatedUser");
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		request.setAttribute("displayName", user.displayName());
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.getRequestDispatcher("/WEB-INF/teacher/home.jsp").forward(request, response);
	}
}
