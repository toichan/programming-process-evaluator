package servlet.auth;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.auth.AuthenticationControl;
import control.auth.RequestMetadata;
import entity.UserCredential.UserType;

@WebServlet("/auth/logout")
public final class LogoutServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String USER_ATTRIBUTE = AuthenticatedUser.class.getName();
	private static final AuthenticationControl AUTHENTICATION = new AuthenticationControl();

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8");
		HttpSession session = request.getSession(false);
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Object sessionValue = session.getAttribute(USER_ATTRIBUTE);
		if (!(sessionValue instanceof AuthenticatedUser user)) {
			session.invalidate();
			response.sendRedirect(request.getContextPath()
					+ PortalHostRouting.fromEnvironment().loginPathForHost(request.getServerName()));
			return;
		}

		RequestMetadata metadata = RequestMetadata.from(request.getRemoteAddr(), request.getHeader("User-Agent"))
				.withSessionId(user.sessionAuditId());
		session.invalidate();
		try {
			AUTHENTICATION.recordLogout(user.userId(), user.loginId(), metadata);
		} catch (SQLException e) {
			getServletContext().log("Logout history could not be recorded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}

		String loginPath = user.userType() == UserType.STUDENT
				? "/student/account/login"
				: "/teacher/account/login";
		response.sendRedirect(request.getContextPath() + loginPath);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}
}
