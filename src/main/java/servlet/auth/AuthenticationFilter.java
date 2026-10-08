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
import entity.UserCredential;
import control.auth.AuthenticatedUser;

@WebFilter("/*")
public final class AuthenticationFilter implements Filter {
	private static final String USER_ATTRIBUTE = AuthenticatedUser.class.getName();
	private static final StudentControl STUDENTS = new StudentControl();
	private FilterConfig filterConfig;
	private PortalHostRouting portalHostRouting;
	private final boolean requireHttps = ProductionTransportPolicy.requiresHttps(System.getenv());

	@Override
	public void init(FilterConfig filterConfig) {
		this.filterConfig = filterConfig;
		this.portalHostRouting = PortalHostRouting.fromEnvironment();
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		String requestedPath = request.getRequestURI().substring(request.getContextPath().length());
		String path = ApplicationUrls.canonicalPath(requestedPath);
		if (requireHttps && !ProductionTransportPolicy.permitted(true, request.isSecure(), path)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "HTTPS is required.");
			return;
		}
		java.util.Optional<String> portalRedirect = portalHostRouting.redirectLocation(
				request.getServerName(), request.getServerPort(), path, request.getContextPath());
		if (portalRedirect.isPresent()) {
			response.sendRedirect(portalRedirect.get());
			return;
		}
		boolean studentArea = ApplicationUrls.isPortalPath(path, "/student") && !path.equals("/student/account/login");
		boolean staffArea = ApplicationUrls.isPortalPath(path, "/teacher") && !path.equals("/teacher/account/login");
		boolean adminArea = ApplicationUrls.isPortalPath(path, "/admin");
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
					+ (staffArea || adminArea ? "/teacher/account/login"
							: studentArea ? "/student/account/login"
							: portalHostRouting.loginPathForHost(request.getServerName())));
			return;
		}

		if ((studentArea && user.userType() != UserType.STUDENT)
				|| (staffArea && user.userType() != UserType.TEACHER && user.userType() != UserType.ADMIN)
				|| (adminArea && (user.userType() != UserType.ADMIN
						|| !UserCredential.ADMIN_LOGIN_ID.equals(user.loginId())))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String passwordChangePath = user.userType() == UserType.TEACHER
				? ApplicationUrls.TEACHER_PASSWORD : ApplicationUrls.STUDENT_PASSWORD;
		if (user.userType() == UserType.STUDENT) {
			try {
				if (!new control.auth.StudentSessionControl().isCurrent(user, session.getAttribute("studentAccountVersion"))) {
					session.invalidate();
					response.sendRedirect(request.getContextPath() + "/student/account/login");
					return;
				}
			} catch (SQLException failure) {
				filterConfig.getServletContext().log("Student session validation failed.", failure);
				response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				return;
			}
		}
		if (user.userType() == UserType.TEACHER) {
			try {
				if (!new control.auth.TeacherSessionControl().isCurrent(user, session.getAttribute("teacherAccountVersion"))) {
					session.invalidate();
					response.sendRedirect(request.getContextPath() + "/teacher/account/login");
					return;
				}
			} catch (SQLException failure) {
				filterConfig.getServletContext().log("Teacher session validation failed.", failure);
				response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				return;
			}
		}
		if (user.passwordChangeRequired()
				&& !path.equals(passwordChangePath)
				&& !path.equals("/auth/logout")) {
			response.sendRedirect(request.getContextPath() + passwordChangePath);
			return;
		}

		if (ApplicationUrls.redirectLegacy(request, response, requestedPath)) return;

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
