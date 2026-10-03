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
import control.student.StudentEvaluationControl;
import entity.StudentCodeLogPage;
import entity.StudentEvaluationPage;
import servlet.auth.CsrfTokens;

@WebServlet(urlPatterns = {
		"/student/evaluation",
		"/student/evaluation/log",
		"/student/evaluation/retry"
})
public final class StudentEvaluationServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final StudentEvaluationControl EVALUATIONS = new StudentEvaluationControl();
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		try {
			if ("/student/evaluation/log".equals(request.getServletPath())) {
				showCodeLogs(request, response, user);
			} else {
				showEvaluation(request, response, user);
			}
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Student evaluation data could not be loaded.", e);
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
		Long assignmentId = positiveLong(request.getParameter("assignmentId"));
		Long submissionId = positiveLong(request.getParameter("submissionId"));
		if (assignmentId == null || submissionId == null
				|| !"/student/evaluation/retry".equals(request.getServletPath())) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			EVALUATIONS.retryFailedEvaluation(user, assignmentId, submissionId);
			response.sendRedirect(response.encodeRedirectURL(request.getContextPath()
					+ "/student/evaluation?assignmentId=" + assignmentId + "&submissionId=" + submissionId));
		} catch (SQLException e) {
			getServletContext().log("Student evaluation retry could not be queued.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	private static void showEvaluation(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user) throws SQLException, ServletException, IOException {
		Long assignmentId = positiveLong(request.getParameter("assignmentId"));
		Long submissionId = optionalPositiveLong(request.getParameter("submissionId"));
		if (assignmentId == null || (request.getParameter("submissionId") != null && submissionId == null)) {
			throw new IllegalArgumentException("The requested assignment is invalid.");
		}
		StudentEvaluationPage page = EVALUATIONS.loadEvaluation(user, assignmentId, submissionId).orElse(null);
		if (page == null) {
			response.sendRedirect(request.getContextPath() + "/student/home");
			return;
		}
		prepareStudentPage(request, user, "評価結果", "/css/student/evaluation/evaluation.css");
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.setAttribute("evaluationPage", page);
		request.getRequestDispatcher("/WEB-INF/student/evaluation/evaluation.jsp").forward(request, response);
	}

	private static void showCodeLogs(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user) throws SQLException, ServletException, IOException {
		Long submissionId = positiveLong(request.getParameter("submissionId"));
		if (submissionId == null) {
			throw new IllegalArgumentException("The requested submission is invalid.");
		}
		StudentCodeLogPage page = EVALUATIONS.loadCodeLogs(user, submissionId).orElse(null);
		if (page == null) {
			response.sendRedirect(request.getContextPath() + "/student/home");
			return;
		}
		prepareStudentPage(request, user, "コードログ", "/css/student/evaluation/log.css");
		request.setAttribute("codeLogPage", page);
		request.getRequestDispatcher("/WEB-INF/student/evaluation/log.jsp").forward(request, response);
	}

	private static void prepareStudentPage(
			HttpServletRequest request,
			AuthenticatedUser user,
			String title,
			String stylesheet) throws SQLException {
		request.setAttribute("studentDisplayName", user.displayName());
		request.setAttribute("studentLoginId", user.loginId());
		request.setAttribute("screenDesign", "student");
		request.setAttribute("screenPageTitle", title);
		request.setAttribute("screenStylesheet", stylesheet);
		request.setAttribute("taskCount", STUDENTS.loadHome(user).map(home -> home.getTasks().size()).orElse(0));
	}

	private static Long positiveLong(String value) {
		Long parsed = optionalPositiveLong(value);
		return parsed == null ? null : parsed;
	}

	private static Long optionalPositiveLong(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
