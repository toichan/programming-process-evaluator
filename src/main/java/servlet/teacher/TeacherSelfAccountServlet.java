package servlet.teacher;

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
import control.auth.PasswordPolicy;
import control.auth.RequestMetadata;
import control.teacher.TeacherNavigationControl;
import control.teacher.TeacherSelfAccountControl;
import control.teacher.TeacherSelfAccountControl.StaleAccountException;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

import servlet.auth.ApplicationUrls;

@WebServlet({ ApplicationUrls.TEACHER_ACCOUNT, ApplicationUrls.TEACHER_PASSWORD })
public final class TeacherSelfAccountServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final TeacherSelfAccountControl accounts = new TeacherSelfAccountControl();

	@Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		try {
			teacher(request);
			requireSelfTarget(request.getParameter("userId"), request.getParameter("teacherId"));
			render(request, response, null);
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (IllegalArgumentException failure) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, failure.getMessage());
		}
	}

	@Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		if (!request.getServletPath().equals("/teacher/account/password")) {
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return;
		}
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		char[] current = value(request.getParameter("currentPassword"));
		char[] next = value(request.getParameter("newPassword"));
		char[] confirmation = value(request.getParameter("confirmPassword"));
		try {
			AuthenticatedUser user = teacher(request);
			requireSelfTarget(request.getParameter("userId"), request.getParameter("teacherId"));
			if (!"yes".equals(request.getParameter("changeConfirmed")))
				throw new IllegalArgumentException("変更後にすべての端末で再ログインが必要になることを確認してください。");
			long version = version(request.getParameter("version"));
			if (version != sessionVersion(request)) throw new StaleAccountException();
			var result = accounts.changePassword(user, version, current, next, confirmation,
					RequestMetadata.from(request.getRemoteAddr(), request.getHeader("User-Agent"))
							.withSessionId(user.sessionAuditId()));
			switch (result) {
				case SUCCESS -> {
					request.getSession(false).invalidate();
					HttpSession notice = request.getSession(true);
					notice.setAttribute("teacherPasswordChangeNotice", Boolean.TRUE);
					CsrfTokens.rotate(notice);
					response.setStatus(HttpServletResponse.SC_SEE_OTHER);
					response.setHeader("Location", request.getContextPath() + "/teacher/account/login");
				}
				case CURRENT_PASSWORD_INVALID -> invalid(request, response, "現在のパスワードを確認してください。");
				case PASSWORD_POLICY -> invalid(request, response, String.join(" ", PasswordPolicy.violations(next)));
				case PASSWORD_REUSED -> invalid(request, response, "現在と異なるパスワードを設定してください。");
				case CONFIRMATION_MISMATCH -> invalid(request, response, "新しいパスワードが一致しません。");
			}
		} catch (StaleAccountException failure) {
			response.sendError(HttpServletResponse.SC_CONFLICT, failure.getMessage());
		} catch (IllegalArgumentException failure) {
			invalid(request, response, failure.getMessage());
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException failure) {
			getServletContext().log("Teacher password change failed.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} finally {
			Arrays.fill(current, '\0');
			Arrays.fill(next, '\0');
			Arrays.fill(confirmation, '\0');
		}
	}

	private void invalid(HttpServletRequest request, HttpServletResponse response, String message)
			throws ServletException, IOException {
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		render(request, response, message);
	}

	private void render(HttpServletRequest request, HttpServletResponse response, String error)
			throws ServletException, IOException {
		try {
			AuthenticatedUser user = teacher(request);
			var account = accounts.load(user, sessionVersion(request));
			request.setAttribute("teacherAccount", account);
			request.setAttribute("teacherFeatureLabels", TeacherAccountInput.FEATURE_ORDER.stream()
					.filter(account.features()::contains).map(TeacherAccountInput.FEATURE_LABELS::get).toList());
			request.setAttribute("teacherNavigationSummary", new TeacherNavigationControl().load(user));
			request.setAttribute("teacherId", user.loginId());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.setAttribute("passwordChangeRequired", account.mustChangePassword());
			request.setAttribute("passwordChangeError", error);
			String page = request.getServletPath().endsWith("/password") ? "password" : "profile";
			request.getRequestDispatcher("/WEB-INF/teacher/account/" + page + ".jsp").forward(request, response);
		} catch (StaleAccountException failure) {
			response.sendError(HttpServletResponse.SC_CONFLICT, failure.getMessage());
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException failure) {
			getServletContext().log("Teacher self account could not be loaded.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static AuthenticatedUser teacher(HttpServletRequest request) {
		Object value = request.getAttribute("authenticatedUser");
		if (!(value instanceof AuthenticatedUser user) || user.userType() != UserType.TEACHER)
			throw new SecurityException("Teacher authentication is required.");
		return user;
	}

	private static long sessionVersion(HttpServletRequest request) {
		Object value = request.getSession(false).getAttribute("teacherAccountVersion");
		if (!(value instanceof Long version) || version < 1) throw new SecurityException("Teacher session is invalid.");
		return version;
	}

	static void requireSelfTarget(String userId, String teacherId) {
		if (userId != null || teacherId != null)
			throw new IllegalArgumentException("他のアカウントを指定することはできません。");
	}

	static long version(String value) {
		try {
			long version = Long.parseLong(value);
			if (version < 1) throw new NumberFormatException();
			return version;
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("更新情報が不正です。画面を再読み込みしてください。");
		}
	}

	private static char[] value(String value) { return value == null ? new char[0] : value.toCharArray(); }
}
