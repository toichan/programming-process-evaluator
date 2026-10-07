package servlet.admin;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import control.auth.AuthenticatedUser;
import entity.UserCredential.UserType;

@WebServlet("/admin/home")
public final class AdminHomeServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AuthenticatedUser user = (AuthenticatedUser) request.getAttribute("authenticatedUser");
		if (user == null || user.userType() != UserType.ADMIN) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		response.sendRedirect(request.getContextPath() + "/admin/management");
	}
}
