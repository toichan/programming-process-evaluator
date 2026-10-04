package servlet.admin;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import control.auth.AuthenticatedUser;
import servlet.auth.CsrfTokens;
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

		request.setAttribute("displayName", user.displayName());
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.setAttribute("screenPageTitle", "管理者ホーム");
		request.getRequestDispatcher("/WEB-INF/admin/home.jsp").forward(request, response);
	}
}
