package servlet.student;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.DispatcherType;
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

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import control.student.StudentNotificationControl;
import entity.StudentHomePage;
import entity.StudentTaskShortcut;
import entity.UserCredential.UserType;

@WebFilter(urlPatterns = "/WEB-INF/student/*", dispatcherTypes = DispatcherType.FORWARD)
public final class StudentNavigationFilter implements Filter {
	private static final StudentControl STUDENTS = new StudentControl();
	private static final StudentNotificationControl NOTIFICATIONS = new StudentNotificationControl();

	@Override
	public void init(FilterConfig config) { }

	@Override
	public void destroy() { }

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		if (!(request.getAttribute("authenticatedUser") instanceof AuthenticatedUser user)
				|| user.userType() != UserType.STUDENT) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		try {
			StudentHomePage home = request.getAttribute("studentHome") instanceof StudentHomePage existing
					? existing : STUDENTS.loadHome(user).orElse(null);
			if (home == null) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			request.setAttribute("studentHome", home);
			request.setAttribute("taskCount", home.getTasks().size());
			var scores = new dao.StudentTaskNavigationDao().findLatestScores(user.userId());
			request.setAttribute("studentTaskShortcuts", home.getTasks().stream()
					.map(task -> new StudentTaskShortcut(home, task, task.getLatestSubmissionId() == null
							? null : scores.get(task.getLatestSubmissionId()))).toList());
			request.setAttribute("studentRecentNotifications", NOTIFICATIONS.loadRecent(user));
			request.setAttribute("studentUnreadNotificationCount", NOTIFICATIONS.countUnread(user));
			HttpSession session = request.getSession(false);
			if (request.getAttribute("csrfToken") == null && session != null) {
				request.setAttribute("csrfToken", servlet.auth.CsrfTokens.getOrCreate(session));
			}
			chain.doFilter(request, response);
		} catch (SQLException failure) {
			request.getServletContext().log("Student task navigation could not be loaded.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}
}
