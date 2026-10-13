package servlet.student;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import control.student.StudentNotificationControl;
import control.student.StudentNotificationControl.OpenResult;
import control.student.StudentNotificationControl.OpenStatus;
import entity.StudentHomePage;
import entity.StudentNotificationPage;
import servlet.auth.CsrfTokens;

@WebServlet(urlPatterns = {
		StudentNotificationServlet.LIST_PATH,
		StudentNotificationServlet.OPEN_PATH,
		StudentNotificationServlet.READ_ALL_PATH
})
public final class StudentNotificationServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	static final String LIST_PATH = "/student/notifications";
	static final String OPEN_PATH = "/student/notifications/open";
	static final String READ_ALL_PATH = "/student/notifications/read-all";
	static final String TARGET_UNAVAILABLE_MESSAGE =
			"通知の対象の評価結果を表示できません。課題の公開状態や所属が変更された可能性があります。";

	private final transient StudentNotificationControl notifications;
	private final transient HomeLoader homes;

	@FunctionalInterface
	interface HomeLoader {
		StudentHomePage load(AuthenticatedUser user) throws SQLException;
	}

	public StudentNotificationServlet() {
		this(new StudentNotificationControl(), defaultHomeLoader(new StudentControl()));
	}

	StudentNotificationServlet(StudentNotificationControl notifications, HomeLoader homes) {
		this.notifications = notifications;
		this.homes = homes;
	}

	private static HomeLoader defaultHomeLoader(StudentControl students) {
		return user -> students.loadHome(user).orElse(null);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		if (!LIST_PATH.equals(request.getServletPath())) {
			response.setHeader("Allow", "POST");
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return;
		}
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		Integer pageNumber = pageNumber(request.getParameter("page"));
		if (pageNumber == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			StudentNotificationPage page = notifications.loadPage(user, pageNumber);
			StudentHomePage home = homes.load(user);
			if (home == null) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			request.setAttribute("studentLoginId", user.loginId());
			request.setAttribute("screenDesign", "student");
			request.setAttribute("screenPageTitle", "通知");
			request.setAttribute("screenStylesheet", "/css/student/notifications/notifications.css");
			request.setAttribute("studentHome", home);
			request.setAttribute("taskCount", home.getTasks().size());
			request.setAttribute("notificationPage", page);
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession()));
			request.getRequestDispatcher("/WEB-INF/student/notifications/notifications.jsp")
					.forward(request, response);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (SQLException e) {
			getServletContext().log("Student notifications could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		String path = request.getServletPath();
		try {
			if (OPEN_PATH.equals(path)) {
				open(request, response, user);
			} else if (READ_ALL_PATH.equals(path)) {
				notifications.markAllRead(user);
				seeOther(request, response, LIST_PATH);
			} else {
				response.setHeader("Allow", "GET");
				response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			}
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Student notification could not be updated.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private void open(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user)
			throws SQLException, IOException {
		Long notificationId = positiveLong(request.getParameter("notificationId"));
		if (notificationId == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		OpenResult result = notifications.open(user, notificationId);
		if (result.status() == OpenStatus.NOT_FOUND) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} else if (result.status() == OpenStatus.TARGET_UNAVAILABLE) {
			response.sendError(HttpServletResponse.SC_CONFLICT, TARGET_UNAVAILABLE_MESSAGE);
		} else {
			seeOther(request, response, result.redirectPath());
		}
	}

	private static void seeOther(HttpServletRequest request, HttpServletResponse response, String path) {
		response.setStatus(HttpServletResponse.SC_SEE_OTHER);
		response.setHeader("Location", response.encodeRedirectURL(request.getContextPath() + path));
	}

	static Integer pageNumber(String value) {
		if (value == null) {
			return 1;
		}
		if (value.isEmpty() || value.length() > 6 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
			return null;
		}
		int parsed = Integer.parseInt(value);
		return parsed >= 1 ? parsed : null;
	}

	private static Long positiveLong(String value) {
		if (value == null || value.isEmpty() || value.length() > 18
				|| !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
			return null;
		}
		long parsed = Long.parseLong(value);
		return parsed > 0 ? parsed : null;
	}
}
