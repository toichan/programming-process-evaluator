package servlet.auth;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.student.StudentControl;
import entity.UserCredential.UserType;
import control.auth.AuthenticatedUser;

@WebFilter("/*")
public final class AuthenticationFilter implements Filter {
	private static final String USER_ATTRIBUTE = AuthenticatedUser.class.getName();
	private static final StudentControl STUDENTS = new StudentControl();
	private FilterConfig filterConfig;

	@Override
	public void init(FilterConfig filterConfig) {
		this.filterConfig = filterConfig;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		String path = request.getRequestURI().substring(request.getContextPath().length());
		boolean studentArea = path.startsWith("/student/") && !path.equals("/student/account/login");
		boolean staffArea = path.startsWith("/teacher/") && !path.equals("/teacher/account/login");
		boolean adminArea = path.startsWith("/admin/");
		boolean authenticatedArea = studentArea || staffArea || adminArea || path.equals("/auth/logout");
		if (!authenticatedArea) {
			chain.doFilter(request, response);
			return;
		}
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");

		HttpSession session = request.getSession(false);
		Object sessionUser = session == null ? null : session.getAttribute(USER_ATTRIBUTE);
		if (!(sessionUser instanceof AuthenticatedUser user)) {
			response.sendRedirect(request.getContextPath()
					+ (staffArea || adminArea ? "/teacher/account/login" : "/student/account/login"));
			return;
		}

		if ((studentArea && user.userType() != UserType.STUDENT)
				|| (staffArea && user.userType() != UserType.TEACHER && user.userType() != UserType.ADMIN)
				|| (adminArea && user.userType() != UserType.ADMIN)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String passwordChangePath = "/student/account/change-password";
		if (user.passwordChangeRequired()
				&& !path.equals(passwordChangePath)
				&& !path.equals("/auth/logout")) {
			response.sendRedirect(request.getContextPath() + passwordChangePath);
			return;
		}

		request.setAttribute("authenticatedUser", user);
		if (user.userType() == UserType.STUDENT) {
			request.setAttribute("studentLoginId", user.loginId());
			if (studentArea) {
				try {
					request.setAttribute("studentAffiliations", STUDENTS.loadAffiliations(user));
				} catch (SQLException e) {
					filterConfig.getServletContext().log("Student affiliations could not be loaded.", e);
					response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
					return;
				}
			}
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}
}
