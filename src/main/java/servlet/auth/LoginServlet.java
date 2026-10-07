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
import control.auth.AuthenticationControl.LoginPortal;
import control.auth.LoginResult;
import control.auth.RequestMetadata;
import entity.StudentAccountProfile;
import entity.UserCredential;
import entity.UserCredential.FirstLoginStatus;
import entity.UserCredential.UserType;

@WebServlet({ "/student/account/login", "/teacher/account/login" })
public final class LoginServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String USER_ATTRIBUTE = AuthenticatedUser.class.getName();
	private static final String CSRF_ATTRIBUTE = "csrfToken";
	private static final String ERROR_ATTRIBUTE = "loginError";
	private static final AuthenticationControl AUTHENTICATION = new AuthenticationControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		disableCaching(response);
		AuthenticatedUser user = authenticatedUser(request.getSession(false));
		if (user != null && belongsToPortal(user, isStudentPortal(request))) {
			redirectAuthenticatedUser(request, response, user);
			return;
		}

		HttpSession session = request.getSession(true);
		request.setAttribute(CSRF_ATTRIBUTE, CsrfTokens.getOrCreate(session));
		forwardToLoginPage(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		disableCaching(response);
		HttpSession anonymousSession = request.getSession(false);
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String loginId = request.getParameter("loginId");
		String submittedPassword = request.getParameter("password");
		char[] password = submittedPassword == null ? new char[0] : submittedPassword.toCharArray();
		try {
			if (loginId == null || loginId.length() > 64 || password.length > 256) {
				forwardWithError(request, response, "ログインできません。IDとパスワードを確認してください。");
				return;
			}

			RequestMetadata metadata = RequestMetadata.from(request.getRemoteAddr(), request.getHeader("User-Agent"))
					.withSessionId(anonymousSession == null ? null : anonymousSession.getId());
			LoginResult result = AUTHENTICATION.authenticate(loginId, password,
					isStudentPortal(request) ? LoginPortal.STUDENT : LoginPortal.STAFF, metadata);
			if (!result.authenticated()) {
				forwardWithError(request, response, loginFailureMessage());
				return;
			}

			UserCredential credential = result.user();
			Long teacherVersion = null;
			if (credential.userType() == UserType.TEACHER) {
				teacherVersion = new control.auth.TeacherSessionControl().loginVersion(credential).orElse(null);
				if (teacherVersion == null) {
					forwardWithError(request, response, loginFailureMessage());
					return;
				}
			}
			boolean passwordChangeRequired = passwordChangeRequired(credential);
			AuthenticatedUser authenticatedUser = new AuthenticatedUser(
					credential.userId(),
					credential.loginId(),
					credential.displayName(),
					credential.userType(),
					passwordChangeRequired,
					result.sessionAuditId());
			String teacherDestination = null;
			if (credential.userType() == UserType.TEACHER) {
				teacherDestination = teacherDestinationFor(new control.teacher.TeacherNavigationControl().load(authenticatedUser));
			}
			if (anonymousSession != null) anonymousSession.invalidate();
			HttpSession authenticatedSession = request.getSession(true);
			authenticatedSession.setMaxInactiveInterval(30 * 60);
			authenticatedSession.setAttribute(USER_ATTRIBUTE, authenticatedUser);
			if (teacherVersion != null) authenticatedSession.setAttribute("teacherAccountVersion", teacherVersion);
			if (teacherDestination != null) authenticatedSession.setAttribute("teacherLandingPath", teacherDestination);
			CsrfTokens.rotate(authenticatedSession);
			redirectAuthenticatedUser(request, response, authenticatedUser);
		} catch (SecurityException e) {
			forwardWithError(request, response, loginFailureMessage());
		} catch (SQLException e) {
			getServletContext().log("Authentication request failed.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private void forwardToLoginPage(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setAttribute("studentPortal", isStudentPortal(request));
		request.getRequestDispatcher("/WEB-INF/auth/login.jsp").forward(request, response);
	}

	private void forwardWithError(HttpServletRequest request, HttpServletResponse response, String message)
			throws ServletException, IOException {
		request.setAttribute(ERROR_ATTRIBUTE, message);
		HttpSession session = request.getSession(true);
		request.setAttribute(CSRF_ATTRIBUTE, CsrfTokens.getOrCreate(session));
		forwardToLoginPage(request, response);
	}

	private static boolean isStudentPortal(HttpServletRequest request) {
		return request.getServletPath().startsWith("/student/");
	}

	private static boolean belongsToPortal(AuthenticatedUser user, boolean studentPortal) {
		return studentPortal
				? user.userType() == UserType.STUDENT
				: user.userType() == UserType.TEACHER || user.userType() == UserType.ADMIN;
	}

	private static AuthenticatedUser authenticatedUser(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(USER_ATTRIBUTE);
		return value instanceof AuthenticatedUser user ? user : null;
	}

	private static boolean passwordChangeRequired(UserCredential user) {
		if (user.userType() != UserType.STUDENT || user.studentProfile().isEmpty()) {
			return false;
		}
		StudentAccountProfile profile = user.studentProfile().get();
		return profile.securityLevel() == 2
				&& (profile.mustChangePassword()
						|| profile.firstLoginStatus() != FirstLoginStatus.COMPLETED);
	}

	private static String loginFailureMessage() {
		return "ログインできません。IDとパスワードを確認してください。必要な場合は担当者へお問い合わせください。";
	}

	private static void redirectAuthenticatedUser(HttpServletRequest request, HttpServletResponse response,
			AuthenticatedUser user) throws IOException {
		String destination = destinationFor(user);
		if (user.userType() == UserType.TEACHER) {
			Object landing = request.getSession(false).getAttribute("teacherLandingPath");
			if (landing instanceof String path && java.util.List.of("/teacher/task", "/teacher/prompt", "/teacher/home").contains(path)) {
				destination = path;
			}
		}
		response.sendRedirect(request.getContextPath() + destination);
	}

	static String teacherDestinationFor(entity.TeacherNavigationSummary permissions) {
		if (permissions.taskManagementEnabled()) return "/teacher/task";
		if (permissions.promptDesignEnabled()) return "/teacher/prompt";
		return "/teacher/home";
	}

	static String destinationFor(AuthenticatedUser user) {
		if (user.passwordChangeRequired()) {
			return "/student/account/change-password";
		}
		return switch (user.userType()) {
			case STUDENT -> "/student/home";
			case ADMIN -> "/admin/home";
			case TEACHER -> "/teacher/task";
		};
	}

	private static void disableCaching(HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
	}
}
