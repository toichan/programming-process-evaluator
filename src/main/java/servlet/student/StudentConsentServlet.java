package servlet.student;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import entity.ConsentSaveResult;
import entity.StudentConsentPage;
import servlet.auth.CsrfTokens;

@WebServlet("/student/survey/consent")
public final class StudentConsentServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String NOTICE_ATTRIBUTE = "studentHomeNotice";
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		try {
			if (!renderConsentPage(request, response, user)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
			}
		} catch (SQLException e) {
			getServletContext().log("Student consent data could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		if (!"yes".equals(request.getParameter("confirmRead"))) {
			forwardWithError(request, response, user,
					"説明を確認するチェックを付けてから回答してください。", HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		long documentId;
		long expectedResponseId;
		try {
			documentId = Long.parseLong(request.getParameter("documentVersionId"));
			expectedResponseId = Long.parseLong(request.getParameter("responseId"));
			if (documentId <= 0 || expectedResponseId < 0) {
				throw new NumberFormatException("Consent document id must be positive.");
			}
		} catch (NumberFormatException e) {
			forwardWithError(request, response, user,
					"同意文書を確認できません。画面を再読み込みしてください。", HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		try {
			ConsentSaveResult result = STUDENTS.saveConsent(
					user, documentId, request.getParameter("consentDecision"), expectedResponseId,
					"yes".equals(request.getParameter("changeConfirmed")));
			switch (result) {
				case RECORDED -> redirectHome(request, response, "saved");
				case ALREADY_RECORDED -> redirectHome(request, response, "already");
				case NO_ACTIVE_DOCUMENT, DOCUMENT_CHANGED ->
					forwardWithError(request, response, user,
							"同意文書が更新されました。現在の文書を確認してから操作してください。",
							HttpServletResponse.SC_CONFLICT);
				case ACCOUNT_UNAVAILABLE -> response.sendError(HttpServletResponse.SC_FORBIDDEN);
				case RESPONSE_CHANGED ->
					forwardWithError(request, response, user,
							"別の画面で回答が変更されています。現在の回答を確認してから操作してください。",
							HttpServletResponse.SC_CONFLICT);
				case CONFIRMATION_REQUIRED ->
					forwardWithError(request, response, user,
							"確認ダイアログで変更を確定してください。",
							HttpServletResponse.SC_BAD_REQUEST);
				case INVALID_DECISION ->
					forwardWithError(request, response, user,
							"同意するか、同意しないかを選択してください。",
							HttpServletResponse.SC_BAD_REQUEST);
			}
		} catch (SQLException e) {
			getServletContext().log("Student consent response could not be saved.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		}
	}

	private boolean renderConsentPage(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user) throws SQLException, ServletException, IOException {
		StudentConsentPage page = STUDENTS.loadConsentPage(user).orElse(null);
		if (page == null) {
			return false;
		}
		request.setAttribute("consentPage", page);
		request.setAttribute("consentDocument", page.getDocument().orElse(null));
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.getRequestDispatcher("/WEB-INF/student/survey/consent.jsp").forward(request, response);
		return true;
	}

	private void forwardWithError(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			String message,
			int status) throws ServletException, IOException {
		response.setStatus(status);
		request.setAttribute("consentError", message);
		try {
			if (!renderConsentPage(request, response, user)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
			}
		} catch (SQLException e) {
			getServletContext().log("Student consent page could not be reloaded after a rejected response.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static void redirectHome(HttpServletRequest request, HttpServletResponse response, String notice)
			throws IOException {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.setAttribute(NOTICE_ATTRIBUTE, notice);
		}
		response.sendRedirect(request.getContextPath() + "/student/home");
	}
}
