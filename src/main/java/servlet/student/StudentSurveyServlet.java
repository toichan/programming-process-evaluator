package servlet.student;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import control.student.StudentSurveyControl;
import dao.StudentSurveyDao.SaveResult;
import entity.StudentHomePage;
import entity.StudentSurveyPage;
import servlet.auth.CsrfTokens;

@WebServlet("/student/survey")
public final class StudentSurveyServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String NOTICE_ATTRIBUTE = "studentSurveyNotice";
	private static final StudentSurveyControl SURVEYS = new StudentSurveyControl();
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		Long assignmentId = positiveLong(request.getParameter("assignmentId"));
		Long evaluationId = positiveLong(request.getParameter("evaluationId"));
		Long surveyId = optionalPositiveLong(request.getParameter("surveyId"));
		if (assignmentId == null || evaluationId == null
				|| (request.getParameter("surveyId") != null && surveyId == null)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			if (!renderPage(request, response, user, assignmentId, evaluationId, surveyId)) {
				response.sendRedirect(request.getContextPath() + "/student/home");
			}
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Student survey data could not be loaded.", e);
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
		Long surveyId = positiveLong(request.getParameter("surveyId"));
		Long evaluationId = positiveLong(request.getParameter("evaluationId"));
		String action = request.getParameter("action");
		if (assignmentId == null || surveyId == null || evaluationId == null
				|| (!"draft".equals(action) && !"submit".equals(action))) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		try {
			SaveResult result = SURVEYS.saveResponse(
					user,
					assignmentId,
					surveyId,
					evaluationId,
					request.getParameterMap(),
					"submit".equals(action));
			redirectAfterSave(request, response, assignmentId, surveyId, evaluationId, result);
		} catch (IllegalArgumentException e) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			request.setAttribute("surveyError", e.getMessage());
			setSubmittedFormValues(request);
			renderAfterRejectedPost(request, response, user, assignmentId, evaluationId, surveyId);
		} catch (IllegalStateException e) {
			response.setStatus(HttpServletResponse.SC_CONFLICT);
			request.setAttribute("surveyError", "このアンケートは現在保存できません。画面を再読み込みしてください。");
			setSubmittedFormValues(request);
			renderAfterRejectedPost(request, response, user, assignmentId, evaluationId, surveyId);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Student survey answers could not be saved.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private boolean renderPage(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId,
			long evaluationId,
			Long surveyId) throws SQLException, ServletException, IOException {
		StudentSurveyPage page = SURVEYS.loadSurvey(user, assignmentId, evaluationId, surveyId).orElse(null);
		if (page == null) {
			return false;
		}
		request.setAttribute("surveyPage", page);
		request.setAttribute("studentLoginId", user.loginId());
		request.setAttribute("screenDesign", "student");
		request.setAttribute("screenPageTitle", "アンケート");
		request.setAttribute("screenStylesheet", "/css/student/survey/survey.css");
		request.setAttribute("screenScript", "/js/student/survey/survey.js");
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		StudentHomePage home = STUDENTS.loadHome(user).orElse(null);
		request.setAttribute("taskCount", home == null ? 0 : home.getTasks().size());
		HttpSession session = request.getSession(false);
		if (session != null) {
			request.setAttribute(NOTICE_ATTRIBUTE, session.getAttribute(NOTICE_ATTRIBUTE));
			session.removeAttribute(NOTICE_ATTRIBUTE);
		}
		request.getRequestDispatcher("/WEB-INF/student/survey/survey.jsp").forward(request, response);
		return true;
	}

	private void renderAfterRejectedPost(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId,
			long evaluationId,
			Long surveyId) throws ServletException, IOException {
		try {
			if (!renderPage(request, response, user, assignmentId, evaluationId, surveyId)) {
				response.sendError(HttpServletResponse.SC_CONFLICT);
			}
		} catch (SQLException e) {
			getServletContext().log("Student survey page could not be reloaded after a rejected response.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static void setSubmittedFormValues(HttpServletRequest request) {
		Map<Long, List<String>> values = new LinkedHashMap<>();
		Map<Long, String> reasons = new LinkedHashMap<>();
		request.getParameterMap().forEach((key, rawValues) -> {
			if (key.startsWith("answer_")) {
				long id = parseQuestionId(key.substring("answer_".length()));
				if (id > 0 && rawValues != null) {
					values.put(id, List.of(rawValues));
				}
			} else if (key.startsWith("reason_")) {
				long id = parseQuestionId(key.substring("reason_".length()));
				if (id > 0 && rawValues != null && rawValues.length > 0) {
					reasons.put(id, rawValues[0]);
				}
			}
		});
		request.setAttribute("surveySubmittedValues", values);
		request.setAttribute("surveySubmittedReasons", reasons);
	}

	private static long parseQuestionId(String value) {
		try {
			long id = Long.parseLong(value);
			return id > 0 ? id : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void redirectAfterSave(
			HttpServletRequest request,
			HttpServletResponse response,
			long assignmentId,
			long surveyId,
			long evaluationId,
			SaveResult result) throws IOException {
		String notice = switch (result) {
			case SAVED -> "draft-saved";
			case SUBMITTED -> "submitted";
			case ALREADY_SUBMITTED -> "already-submitted";
		};
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.setAttribute(NOTICE_ATTRIBUTE, notice);
		}
		response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/student/survey?assignmentId="
				+ assignmentId + "&evaluationId=" + evaluationId + "&surveyId=" + surveyId));
	}

	private static Long positiveLong(String value) {
		return optionalPositiveLong(value);
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
