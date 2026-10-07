package servlet.auth;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.auth.AuthenticationControl;
import control.auth.AuthenticationControl.PasswordChangeResult;
import control.auth.RequestMetadata;
import control.auth.PasswordPolicy;
import entity.UserCredential.UserType;

@WebServlet(ApplicationUrls.STUDENT_PASSWORD)
public final class StudentPasswordChangeServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String USER_ATTRIBUTE = AuthenticatedUser.class.getName();
	private static final AuthenticationControl AUTHENTICATION = new AuthenticationControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setHeader("Cache-Control", "no-store");
		HttpSession session = request.getSession(false);
		AuthenticatedUser user = authenticatedUser(session);
		if (user == null || user.userType() != UserType.STUDENT) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(session));
		request.setAttribute("passwordChangeRequired", user.passwordChangeRequired());
		request.getRequestDispatcher("/WEB-INF/student/account/change-password.jsp").forward(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		HttpSession session = request.getSession(false);
		AuthenticatedUser user = authenticatedUser(session);
		if (user == null || user.userType() != UserType.STUDENT) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		request.setAttribute("passwordChangeRequired", user.passwordChangeRequired());
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String currentValue = request.getParameter("currentPassword");
		String newValue = request.getParameter("newPassword");
		String confirmationValue = request.getParameter("confirmPassword");
		char[] currentPassword = currentValue == null ? new char[0] : currentValue.toCharArray();
		char[] newPassword = newValue == null ? new char[0] : newValue.toCharArray();
		try {
			if (newPassword.length > 256) {
				forwardWithError(request, response, String.join(" ", PasswordPolicy.violations(newPassword)));
				return;
			}
			if (confirmationValue == null || confirmationValue.length() > 256
					|| !constantTimeEquals(newValue, confirmationValue)) {
				forwardWithError(request, response, "新しいパスワードが一致しません。");
				return;
			}

			RequestMetadata metadata = RequestMetadata.from(request.getRemoteAddr(), request.getHeader("User-Agent"))
					.withSessionId(user.sessionAuditId());
			PasswordChangeResult result = AUTHENTICATION.changeStudentPassword(
					user.userId(), currentPassword, newPassword, metadata,
					(Long) session.getAttribute("studentAccountVersion"));
			switch (result) {
				case SUCCESS -> {
					Long version = new control.auth.StudentSessionControl().afterPasswordChange(user, newPassword).orElse(null);
					if (version == null) {
						session.invalidate();
						response.sendRedirect(request.getContextPath() + "/student/account/login");
						return;
					}
					AuthenticatedUser updatedUser = user.withPasswordChangeRequired(false);
					session.setAttribute(USER_ATTRIBUTE, updatedUser);
					session.setAttribute("studentAccountVersion", version);
					CsrfTokens.rotate(session);
					session.setAttribute("passwordChangeNotice", Boolean.TRUE);
					String destination = user.passwordChangeRequired()
							? "/student/home" : ApplicationUrls.STUDENT_ACCOUNT;
					response.sendRedirect(request.getContextPath() + destination);
				}
				case CURRENT_PASSWORD_INVALID ->
					forwardWithError(request, response, "現在のパスワードを確認してください。");
				case PASSWORD_POLICY ->
					forwardWithError(request, response, String.join(" ", PasswordPolicy.violations(newPassword)));
				case PASSWORD_REUSED ->
					forwardWithError(request, response, "現在と異なるパスワードを設定してください。");
				case NOT_ALLOWED -> response.sendError(HttpServletResponse.SC_FORBIDDEN);
			}
		} catch (SQLException e) {
			getServletContext().log("Password change request failed.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} finally {
			Arrays.fill(currentPassword, '\0');
			Arrays.fill(newPassword, '\0');
		}
	}

	private void forwardWithError(HttpServletRequest request, HttpServletResponse response, String message)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(session));
		request.setAttribute("passwordChangeError", message);
		request.getRequestDispatcher("/WEB-INF/student/account/change-password.jsp").forward(request, response);
	}

	private static AuthenticatedUser authenticatedUser(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(USER_ATTRIBUTE);
		return value instanceof AuthenticatedUser user ? user : null;
	}

	private static boolean constantTimeEquals(String first, String second) {
		if (first == null || second == null) {
			return false;
		}
		return java.security.MessageDigest.isEqual(
				first.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				second.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
