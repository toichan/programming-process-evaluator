package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;
import control.auth.AuthenticatedUser;
import control.teacher.TeacherNavigationControl;
import control.teacher.TeacherReviewControl;
import dao.TeacherReviewDao.NotFoundException;
import entity.TeacherReviewFilter;
import entity.PythonExecutionInput.TooLargeException;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet(urlPatterns = { "/teacher/submissions", "/teacher/evaluations" })
public final class TeacherReviewServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAX_FORM_BYTES = 256 * 1024;
	private static final Gson JSON = new Gson();
	private final TeacherReviewControl review = new TeacherReviewControl();
	private final TeacherNavigationControl navigation = new TeacherNavigationControl();

	@Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		headers(request, response);
		try {
			var user = teacher(request);
			boolean evaluations = "/teacher/evaluations".equals(request.getServletPath());
			String view = request.getParameter("view");
			var filter = filter(request);
			if ("list".equals(view)) {
				json(response, review.list(user, evaluations, filter));
			} else if ("detail".equals(view)) {
				Long evaluationId = optionalId(request, "evaluationId");
				if (!evaluations && evaluationId != null) throw new IllegalArgumentException("評価IDはこの操作では指定できません。");
				json(response, review.detail(user, evaluations, requiredId(request, "submissionId"), evaluationId));
			} else if ("csv".equals(view) && evaluations) {
				String csv = review.exportCsv(user, filter);
				response.setContentType("text/csv; charset=UTF-8");
				response.setHeader("Content-Disposition", "attachment; filename=\"teacher-evaluations.csv\"");
				response.getWriter().write(csv);
			} else if (view == null) {
				review.list(user, evaluations, filter);
				Long selected = optionalId(request, "submissionId");
				if (selected != null) review.detail(user, evaluations, selected, optionalId(request, "evaluationId"));
				request.setAttribute("teacherNavigationSummary", navigation.load(user));
				request.setAttribute("teacherNavigationActiveItem", evaluations ? "evaluations" : "submissions");
				request.setAttribute("teacherId", user.loginId());
				request.setAttribute("reviewEvaluations", evaluations);
				request.setAttribute("reviewSelectedSubmission", selected);
				request.setAttribute("reviewSelectedEvaluation", optionalId(request, "evaluationId"));
				request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
				request.setAttribute("screenDesign", "teacher");
				request.setAttribute("screenPageTitle", evaluations ? "評価確認" : "提出課題確認");
				request.setAttribute("screenStylesheet", "/css/teacher/review/review.css");
				request.setAttribute("screenScript", "/js/teacher/review/review.js");
				request.getRequestDispatcher("/WEB-INF/teacher/review/review.jsp").forward(request, response);
			} else response.sendError(400);
		} catch (IllegalArgumentException failure) { response.sendError(400); }
		catch (NotFoundException failure) { response.sendError(404); }
		catch (SecurityException failure) { response.sendError(403); }
		catch (SQLException failure) {
			getServletContext().log("Teacher review read failed.", failure);
			response.sendError(503);
		}
	}
	@Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		headers(request, response);
		try {
			var user = teacher(request);
			if (!"/teacher/submissions".equals(request.getServletPath())) { response.sendError(405); return; }
			if (request.getContentLengthLong() > MAX_FORM_BYTES) { response.sendError(413); return; }
			if (!CsrfTokens.isValid(request)) { response.sendError(403); return; }
			if (!"preview".equals(request.getParameter("action"))) { response.sendError(400); return; }
			json(response, review.preview(user, requiredId(request, "submissionId"), request.getParameter("code"),
					request.getParameter("standardInput") == null ? "" : request.getParameter("standardInput")));
		} catch (TooLargeException failure) { response.sendError(413); }
		catch (IllegalArgumentException failure) { response.sendError(400); }
		catch (NotFoundException failure) { response.sendError(404); }
		catch (SecurityException failure) { response.sendError(403); }
		catch (SQLException | IOException | IllegalStateException failure) {
			getServletContext().log("Teacher preview service unavailable.", failure);
			response.sendError(503);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			response.sendError(503);
		}
	}
	private static void headers(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("X-Content-Type-Options", "nosniff");
	}
	private static AuthenticatedUser teacher(HttpServletRequest request) {
		if (request.getAttribute("authenticatedUser") instanceof AuthenticatedUser user && user.userType() == UserType.TEACHER) return user;
		throw new SecurityException("Teacher authentication required.");
	}
	private static void json(HttpServletResponse response, Object data) throws IOException {
		response.setContentType("application/json; charset=UTF-8");
		JSON.toJson(data, response.getWriter());
	}
	static TeacherReviewFilter filter(HttpServletRequest request) {
		Long level = optionalId(request, "level");
		if (level != null && level > 5) throw new IllegalArgumentException("評価段階が不正です。");
		return new TeacherReviewFilter(optionalId(request, "schoolId"), optionalId(request, "classroomId"), optionalId(request, "taskId"),
				request.getParameter("difficulty"), request.getParameter("consent"), level == null ? null : level.intValue(),
				request.getParameter("search"), request.getParameter("sort"), request.getParameter("direction"));
	}
	private static long requiredId(HttpServletRequest request, String name) {
		Long id = optionalId(request, name);
		if (id == null) throw new IllegalArgumentException("対象IDが必要です。");
		return id;
	}
	static Long optionalId(HttpServletRequest request, String name) {
		String value = request.getParameter(name);
		if (value == null || value.isEmpty()) return null;
		if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("対象IDが不正です。");
		try { return Long.valueOf(value); }
		catch (NumberFormatException failure) { throw new IllegalArgumentException("対象IDが不正です。", failure); }
	}
}
