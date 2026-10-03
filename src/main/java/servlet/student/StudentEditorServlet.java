package servlet.student;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import control.student.StudentEditorControl;
import control.student.StudentEditorPreferencesControl;
import entity.EditorSaveResult;
import entity.EditorPreferences;
import entity.EditorSubmissionCheck;
import entity.EditorSubmissionCheckCase;
import entity.EditorSubmissionResult;
import entity.InteractiveExecutionUpdate;
import entity.StudentEditorPage;
import entity.StudentHomePage;
import servlet.auth.CsrfTokens;

@WebServlet(urlPatterns = {
		"/student/editor",
		"/student/editor/draft",
		"/student/editor/run",
		"/student/editor/session",
		"/student/editor/session/input",
		"/student/editor/session/cancel",
		"/student/editor/submission/check",
		"/student/editor/submission/submit",
		"/student/editor/resubmission"
})
public final class StudentEditorServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String CHECK_ATTRIBUTE = StudentEditorServlet.class.getName() + ".submissionCheck";
	private static final int MAX_FORM_BYTES = 256 * 1024;
	private static final Gson GSON = new Gson();
	private static final StudentEditorControl EDITOR = new StudentEditorControl();
	private static final StudentEditorPreferencesControl PREFERENCES = new StudentEditorPreferencesControl();
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		Long assignmentId = assignmentId(request);
		if (assignmentId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		if ("/student/editor/session".equals(request.getServletPath())) {
			response.setContentType("application/json; charset=UTF-8");
			response.setHeader("Cache-Control", "no-store");
			try {
				pollInteractiveExecution(request, response, user, assignmentId);
			} catch (SQLException e) {
				getServletContext().log("Interactive execution history could not be saved.", e);
				writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
						error("storage_unavailable", "実行履歴を保存できませんでした。"));
			} catch (IllegalArgumentException e) {
				writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
						error("invalid_request", e.getMessage()));
			} catch (SecurityException e) {
				writeJson(response, HttpServletResponse.SC_FORBIDDEN,
						error("forbidden", "この実行セッションを利用する権限がありません。"));
			} catch (IOException | InterruptedException e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				getServletContext().log("The interactive Python execution service could not be reached.", e);
				writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
						error("execution_unavailable", "コード実行サービスを利用できません。"));
			}
			return;
		}

		try {
			StudentEditorPage page = EDITOR.loadPage(user, assignmentId).orElse(null);
			if (page == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			EditorPreferences preferences = PREFERENCES.load(user);
			request.setAttribute("editorPage", page);
			request.setAttribute("editorPreferences", preferences);
			request.setAttribute("studentDisplayName", user.displayName());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.setAttribute("screenDesign", "student");
			request.setAttribute("screenPageTitle", page.getTitle());
			request.setAttribute("screenStylesheet", "/css/student/editor/editor.css");
			StudentHomePage home = STUDENTS.loadHome(user).orElse(null);
			request.setAttribute("taskCount", home == null ? 0 : home.getTasks().size());
			request.getRequestDispatcher("/WEB-INF/student/editor.jsp").forward(request, response);
		} catch (SQLException e) {
			getServletContext().log("Student editor data could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		response.setContentType("application/json; charset=UTF-8");
		if (request.getContentLengthLong() > MAX_FORM_BYTES) {
			writeJson(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
					error("request_too_large", "送信内容が上限を超えています。"));
			return;
		}
		if (!CsrfTokens.isValid(request)) {
			writeJson(response, HttpServletResponse.SC_FORBIDDEN,
					error("csrf_invalid", "画面を再読み込みしてから、もう一度お試しください。"));
			return;
		}
		AuthenticatedUser user = StudentHomeServlet.authenticatedUser(request);
		if (user == null) {
			writeJson(response, HttpServletResponse.SC_FORBIDDEN,
					error("authentication_required", "ログインし直してください。"));
			return;
		}

		Long assignmentId = assignmentId(request);
		if (assignmentId == null) {
			writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
					error("invalid_assignment", "課題を特定できません。"));
			return;
		}

		try {
			switch (request.getServletPath()) {
				case "/student/editor/draft" -> saveDraft(request, response, user, assignmentId);
				case "/student/editor/run" -> runCode(request, response, user, assignmentId);
				case "/student/editor/session/input" -> sendInteractiveInput(request, response, user, assignmentId);
				case "/student/editor/session/cancel" -> cancelInteractiveExecution(request, response, user, assignmentId);
				case "/student/editor/submission/check" -> checkSubmission(request, response, user, assignmentId);
				case "/student/editor/submission/submit" -> submit(request, response, user, assignmentId);
				case "/student/editor/resubmission" -> startResubmission(response, user, assignmentId);
				default -> writeJson(response, HttpServletResponse.SC_NOT_FOUND,
						error("not_found", "要求された操作が見つかりません。"));
			}
		} catch (StudentEditorControl.DraftConflictException e) {
			writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("draft_conflict", "他の画面で下書きが更新されています。最新の内容を読み込み直してください。"));
		} catch (IllegalArgumentException e) {
			writeJson(response, HttpServletResponse.SC_BAD_REQUEST, error("invalid_request", e.getMessage()));
		} catch (IllegalStateException e) {
			writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("operation_not_allowed", e.getMessage()));
		} catch (SecurityException e) {
			writeJson(response, HttpServletResponse.SC_FORBIDDEN,
					error("forbidden", "この操作を実行する権限がありません。"));
		} catch (SQLException e) {
			getServletContext().log("Student editor operation could not be saved.", e);
			writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					error("storage_unavailable", "データを保存できませんでした。時間をおいて再度お試しください。"));
		} catch (IOException e) {
			getServletContext().log("The isolated Python execution service could not be reached.", e);
			writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					error("execution_unavailable", "コード実行サービスを利用できません。時間をおいて再度お試しください。"));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			getServletContext().log("Student editor execution was interrupted.", e);
			writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					error("execution_interrupted", "実行を完了できませんでした。もう一度お試しください。"));
		}
	}

	private void saveDraft(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws SQLException, IOException {
		String code = request.getParameter("code");
		String version = request.getParameter("draftUpdatedAt");
		String eventType = request.getParameter("eventType");
		if (!"manual_save".equals(eventType) && !"periodic_snapshot".equals(eventType)) {
			throw new IllegalArgumentException("保存種別が正しくありません。");
		}
		EditorSaveResult result = EDITOR.saveDraft(
				user, assignmentId, code, parseDraftTimestamp(version), "periodic_snapshot".equals(eventType));
		switch (result.status()) {
			case SAVED -> writeJson(response, HttpServletResponse.SC_OK,
					Map.of("status", "saved", "updatedAt", result.updatedAt().toString()));
			case CONFLICT -> writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("draft_conflict", "他の画面で下書きが更新されています。最新の内容を読み込み直してください。"));
			case READ_ONLY -> writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("editor_read_only", "提出済みのため、現在はコードを保存できません。"));
			case NOT_FOUND -> writeJson(response, HttpServletResponse.SC_NOT_FOUND,
					error("task_not_found", "課題が見つからないか、現在利用できません。"));
		}
	}

	private void runCode(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws SQLException, IOException, InterruptedException {
		InteractiveExecutionUpdate result = EDITOR.startInteractiveExecution(
				user, assignmentId, request.getParameter("code"));
		writeJson(response, HttpServletResponse.SC_OK, result);
	}

	private void pollInteractiveExecution(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws SQLException, IOException, InterruptedException {
		String cursorValue = request.getParameter("after");
		long cursor;
		try {
			cursor = cursorValue == null ? 0 : Long.parseLong(cursorValue);
			if (cursor < 0) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("実行結果の位置を読み取れません。", e);
		}
		InteractiveExecutionUpdate result = EDITOR.pollInteractiveExecution(
				user, assignmentId, request.getParameter("sessionId"), cursor);
		writeJson(response, HttpServletResponse.SC_OK, result);
	}

	private void sendInteractiveInput(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws IOException, InterruptedException {
		EDITOR.sendInteractiveInput(
				user,
				assignmentId,
				request.getParameter("sessionId"),
				request.getParameter("line"));
		writeJson(response, HttpServletResponse.SC_ACCEPTED, Map.of("accepted", true));
	}

	private void cancelInteractiveExecution(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws IOException, InterruptedException {
		EDITOR.cancelInteractiveExecution(
				user, assignmentId, request.getParameter("sessionId"));
		writeJson(response, HttpServletResponse.SC_OK, Map.of("accepted", true));
	}

	private void checkSubmission(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws SQLException, IOException, InterruptedException {
		EditorSubmissionCheck check = EDITOR.checkSubmission(
				user,
				assignmentId,
				request.getParameter("code"),
				valueOrEmpty(request.getParameter("draftUpdatedAt")));
		String checkId = UUID.randomUUID().toString();
		request.getSession().setAttribute(CHECK_ATTRIBUTE, new SubmissionCheckTicket(checkId, check));

		List<Map<String, Object>> results = new ArrayList<>();
		int passed = 0;
		for (EditorSubmissionCheckCase item : check.getResults()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("title", item.getTitle());
			row.put("input", item.getInput());
			row.put("expectedOutput", item.getExpectedOutput());
			row.put("actualOutput", item.getActualOutput());
			row.put("standardError", item.getStandardError());
			row.put("executionStatus", item.getExecutionStatus());
			row.put("resultStatus", item.getResultStatus());
			row.put("errorCode", item.getErrorCode());
			row.put("durationMilliseconds", item.getDurationMilliseconds());
			row.put("outputTruncated", item.isOutputTruncated());
			results.add(row);
			if (item.isPassed()) {
				passed++;
			}
		}
		writeJson(response, HttpServletResponse.SC_OK,
				Map.of("checkId", checkId, "totalCount", results.size(), "passedCount", passed, "results", results));
	}

	private void submit(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws SQLException, IOException {
		HttpSession session = request.getSession(false);
		Object value = session == null ? null : session.getAttribute(CHECK_ATTRIBUTE);
		String checkId = request.getParameter("checkId");
		if (!(value instanceof SubmissionCheckTicket ticket) || !ticket.checkId.equals(checkId)) {
			writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("submission_check_expired", "確認結果が期限切れです。もう一度提出確認を行ってください。"));
			return;
		}
		EditorSubmissionResult result = EDITOR.submit(
				user, assignmentId, request.getParameter("requestKey"), ticket.check);
		switch (result.status()) {
			case SUBMITTED, DUPLICATE -> {
				session.removeAttribute(CHECK_ATTRIBUTE);
				writeJson(response, HttpServletResponse.SC_OK,
						Map.of("status", "submitted", "revisionNumber", result.revisionNumber()));
			}
			case CONFLICT -> writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("draft_conflict", "下書きまたは課題の入出力条件が更新されています。最新の内容を読み込み直してください。"));
			case NOT_ALLOWED -> writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("submission_not_allowed", "提出期限または再提出条件を満たしていません。"));
			case CHECK_EXPIRED -> writeJson(response, HttpServletResponse.SC_CONFLICT,
					error("submission_check_expired", "確認結果が期限切れです。もう一度提出確認を行ってください。"));
			case NOT_FOUND -> writeJson(response, HttpServletResponse.SC_NOT_FOUND,
					error("task_not_found", "課題が見つからないか、現在利用できません。"));
		}
	}

	private void startResubmission(
			HttpServletResponse response,
			AuthenticatedUser user,
			long assignmentId) throws SQLException, IOException {
		EditorSaveResult result = EDITOR.startResubmission(user, assignmentId);
		if (result.status() == EditorSaveResult.Status.SAVED) {
			writeJson(response, HttpServletResponse.SC_OK,
					Map.of("status", "editing", "updatedAt", result.updatedAt().toString()));
			return;
		}
		if (result.status() == EditorSaveResult.Status.NOT_FOUND) {
			writeJson(response, HttpServletResponse.SC_NOT_FOUND,
					error("task_not_found", "課題が見つからないか、現在利用できません。"));
			return;
		}
		writeJson(response, HttpServletResponse.SC_CONFLICT,
				error("resubmission_not_allowed", "現在は再提出を開始できません。"));
	}

	private static Long assignmentId(HttpServletRequest request) {
		String value = request.getParameter("assignmentId");
		if (value == null) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static java.time.LocalDateTime parseDraftTimestamp(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return java.time.LocalDateTime.parse(value);
		} catch (java.time.format.DateTimeParseException e) {
			throw new IllegalArgumentException("下書き更新日時を読み取れません。", e);
		}
	}

	private static String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	private static Map<String, Object> error(String code, String message) {
		return Map.of("errorCode", code, "message", message == null ? "処理に失敗しました。" : message);
	}

	private static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.getWriter().write(GSON.toJson(body));
	}

	private static final class SubmissionCheckTicket implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		private final String checkId;
		private final EditorSubmissionCheck check;

		private SubmissionCheckTicket(String checkId, EditorSubmissionCheck check) {
			this.checkId = checkId;
			this.check = check;
		}
	}
}
