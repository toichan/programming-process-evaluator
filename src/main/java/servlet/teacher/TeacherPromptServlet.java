package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.evaluation.EvaluationProviderException;
import control.evaluation.GeminiModelCatalog;
import control.teacher.TeacherNavigationControl;
import control.teacher.TeacherPromptControl;
import control.teacher.ReevaluationPreviewControl;
import dao.TeacherPromptDao;
import entity.ReevaluationPreview;
import entity.TeacherNavigationSummary;
import entity.TeacherPromptPage;
import entity.TeacherPromptVersion;
import entity.TeacherPromptVersion.FluctuationItem;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet("/teacher/prompt")
public final class TeacherPromptServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
	private static final DateTimeFormatter HERO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final String NOTICE_ATTRIBUTE = TeacherPromptServlet.class.getName() + ".notice";
	private static final Set<String> ALLOWED_FIELDS = Set.of(
			"action", "csrfToken", "taskId", "promptVersionId", "expectedRowVersion",
			"aiModel", "commonPrompt", "additionalInstruction",
			"fluctuationIds", "teacherResolutions", "resolutionStatuses", "previewCode");
	private static final TeacherPromptControl PROMPTS = new TeacherPromptControl();
	private static final ReevaluationPreviewControl REEVALUATIONS = new ReevaluationPreviewControl();
	private static final TeacherNavigationControl NAVIGATION = new TeacherNavigationControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		try {
			AuthenticatedUser user = authenticatedUser(request);
			render(request, response, user, optionalId(request, "taskId"),
					optionalId(request, "promptVersionId"), optionalPreviewCode(request),
					optionalId(request, "jobId"), null, null, null);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (TeacherPromptDao.TeacherTaskNotFoundException
				| TeacherPromptDao.PromptVersionNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (SQLException e) {
			getServletContext().log("Teacher prompt page could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		AuthenticatedUser user;
		try {
			user = authenticatedUser(request);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Map<String, List<String>> values = Map.of();
		Long taskId = null;
		Long promptVersionId = null;
		String previewCode = null;
		try {
			if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
				throw new TeacherTaskForm.RequestTooLargeException();
			}
			String contentType = request.getContentType();
			if (contentType == null || !contentType.split(";", 2)[0].trim()
					.equalsIgnoreCase("application/x-www-form-urlencoded")) {
				throw new IllegalArgumentException("フォーム形式で送信してください。");
			}
			values = TeacherTaskForm.read(request.getInputStream(), MAX_REQUEST_BYTES);
			for (String name : values.keySet()) {
				if (!ALLOWED_FIELDS.contains(name)) {
					throw new IllegalArgumentException("使用できない入力項目が含まれています。");
				}
			}
			String csrfToken = requiredScalar(values, "csrfToken");
			if (!CsrfTokens.isValid(csrfRequest(request, csrfToken))) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "画面を再読み込みしてください。");
				return;
			}
			String action = requiredScalar(values, "action");
			taskId = positiveId(requiredScalar(values, "taskId"), "課題");
			promptVersionId = optionalId(values, "promptVersionId", "プロンプト版");
			previewCode = optionalScalar(values, "previewCode");
			long expectedRowVersion = nonNegativeLong(requiredScalar(values, "expectedRowVersion"));
			if (isReevaluationAction(action)) {
				ReevaluationPreview preview = null;
				if (!"startReevaluationPreview".equals(action)) {
					if (previewCode == null) {
						throw new IllegalArgumentException("再評価プレビューIDがありません。");
					}
					preview = REEVALUATIONS.loadPreview(user, previewCode);
					if (preview.taskId() != taskId || !Objects.equals(preview.promptVersionId(), promptVersionId)) {
						throw new TeacherPromptDao.TeacherTaskNotFoundException();
					}
				}
				switch (action) {
					case "startReevaluationPreview" -> previewCode = REEVALUATIONS.startPreview(
							user, taskId, requireVersionValue(promptVersionId), expectedRowVersion);
					case "retryReevaluationPreview" -> REEVALUATIONS.retryFailed(user, previewCode);
					case "cancelReevaluationPreview" -> REEVALUATIONS.cancelPreview(user, previewCode);
					case "confirmReevaluationPreview" -> {
						long jobId = REEVALUATIONS.confirmPreview(user, previewCode);
						setNotice(request.getSession(false), "再評価jobを登録しました（ID: " + jobId + "）。");
						redirectToPrompt(response, request.getContextPath(), taskId, promptVersionId, null, jobId);
						return;
					}
					default -> throw new IllegalArgumentException("この操作は現在利用できません。");
				}
				setNotice(request.getSession(false), noticeFor(action));
				redirectToPrompt(response, request.getContextPath(), taskId, promptVersionId, previewCode);
				return;
			}
			long resultVersionId = executeAction(
					user, action, taskId, promptVersionId, expectedRowVersion, values);
			setNotice(request.getSession(false), noticeFor(action));
			redirectToPrompt(response, request.getContextPath(), taskId, resultVersionId, null);
		} catch (TeacherTaskForm.RequestTooLargeException e) {
			response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
		} catch (TeacherPromptDao.TeacherTaskNotFoundException
				| TeacherPromptDao.PromptVersionNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (TeacherPromptDao.PromptVersionConflictException
				| TeacherPromptDao.PromptGenerationInProgressException e) {
			renderError(request, response, user, taskId, promptVersionId, previewCode, values,
					HttpServletResponse.SC_CONFLICT, "別の更新が行われました。画面を再読み込みしてください。");
		} catch (IllegalArgumentException | IllegalStateException e) {
			renderError(request, response, user, taskId, promptVersionId, previewCode, values,
					HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (EvaluationProviderException e) {
			getServletContext().log("Teacher prompt AI generation failed.", e);
			renderError(request, response, user, taskId, promptVersionId, previewCode, values,
					HttpServletResponse.SC_BAD_GATEWAY, "AI生成に失敗しました。入力と接続設定を確認して再試行してください。");
		} catch (SQLException e) {
			getServletContext().log("Teacher prompt operation failed.", e);
			renderError(request, response, user, taskId, promptVersionId, previewCode, values,
					HttpServletResponse.SC_SERVICE_UNAVAILABLE, "保存できませんでした。時間をおいて再度お試しください。");
		}
	}

	private long executeAction(
			AuthenticatedUser user,
			String action,
			long taskId,
			Long promptVersionId,
			long expectedRowVersion,
			Map<String, List<String>> values) throws SQLException, EvaluationProviderException {
		return switch (action) {
			case "saveDraft" -> PROMPTS.saveDraft(
					user, taskId, promptVersionId, expectedRowVersion,
					requiredScalar(values, "aiModel"),
					requiredScalar(values, "commonPrompt"),
					scalar(values, "additionalInstruction"));
			case "duplicateDraft" -> {
				requireVersion(promptVersionId);
				TeacherPromptVersion source = reloadSelectedVersion(user, taskId, promptVersionId);
				yield PROMPTS.saveDraft(user, taskId, null, 0,
						GeminiModelCatalog.DEFAULT_MODEL, source.commonPrompt(), source.additionalInstruction());
			}
			case "generateFluctuations" -> {
				long savedId = saveDraft(user, taskId, promptVersionId, expectedRowVersion, values);
				TeacherPromptVersion saved = reloadSelectedVersion(user, taskId, savedId);
				yield PROMPTS.generateFluctuations(user, taskId, savedId, saved.rowVersion())
						.promptVersionId();
			}
			case "saveResolutions" -> {
				requireVersion(promptVersionId);
				PROMPTS.saveResolutions(user, taskId, promptVersionId, expectedRowVersion,
						resolutions(values), scalar(values, "additionalInstruction"));
				yield promptVersionId;
			}
			case "generateEvaluationExamples" -> {
				requireVersion(promptVersionId);
				PROMPTS.saveResolutions(user, taskId, promptVersionId, expectedRowVersion,
						resolutions(values), scalar(values, "additionalInstruction"));
				TeacherPromptVersion resolved = reloadSelectedVersion(user, taskId, promptVersionId);
				yield PROMPTS.generateEvaluationExamples(
						user, taskId, promptVersionId, resolved.rowVersion()).promptVersionId();
			}
			case "saveEvaluationExamples" -> {
				requireVersion(promptVersionId);
				yield PROMPTS.saveEvaluationExamples(user, taskId, promptVersionId, expectedRowVersion)
						.promptVersionId();
			}
			default -> throw new IllegalArgumentException("この操作は現在利用できません。");
		};
	}

	private long saveDraft(
			AuthenticatedUser user,
			long taskId,
			Long promptVersionId,
			long expectedRowVersion,
			Map<String, List<String>> values) throws SQLException {
		return PROMPTS.saveDraft(
				user, taskId, promptVersionId, expectedRowVersion,
				requiredScalar(values, "aiModel"),
				requiredScalar(values, "commonPrompt"),
				scalar(values, "additionalInstruction"));
	}

	private TeacherPromptVersion reloadSelectedVersion(AuthenticatedUser user, long taskId, long versionId)
			throws SQLException {
		TeacherPromptPage page = PROMPTS.loadPage(user, taskId, versionId);
		if (page.selectedVersion() == null) {
			throw new TeacherPromptDao.PromptVersionNotFoundException();
		}
		return page.selectedVersion();
	}

	private void renderError(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			Long taskId,
			Long promptVersionId,
			String previewCode,
			Map<String, List<String>> values,
			int status,
			String message) throws ServletException, IOException {
		response.setStatus(status);
		try {
			render(request, response, user, taskId, promptVersionId, previewCode, null, values, message, null);
		} catch (TeacherPromptDao.TeacherTaskNotFoundException
				| TeacherPromptDao.PromptVersionNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (SQLException | SecurityException failure) {
			getServletContext().log("Teacher prompt page could not be reloaded after an operation failed.", failure);
			if (!response.isCommitted()) {
				response.sendError(failure instanceof SecurityException
						? HttpServletResponse.SC_FORBIDDEN
						: HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			}
		}
	}

	private void render(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			Long taskId,
			Long promptVersionId,
			String previewCode,
			Long jobId,
			Map<String, List<String>> submittedValues,
			String error,
			String notice) throws SQLException, ServletException, IOException {
		if (previewCode != null && jobId != null) {
			throw new IllegalArgumentException("プレビューと再評価jobは同時に表示できません。");
		}
		ReevaluationPreview preview = previewCode == null ? null : REEVALUATIONS.loadPreview(user, previewCode);
		if (preview != null) {
			if (taskId != null && taskId != preview.taskId()
					|| promptVersionId != null && promptVersionId != preview.promptVersionId()) {
				throw new TeacherPromptDao.TeacherTaskNotFoundException();
			}
			taskId = preview.taskId();
			promptVersionId = preview.promptVersionId();
		}
		if (jobId != null && taskId == null) {
			throw new IllegalArgumentException("再評価jobの表示には課題IDが必要です。");
		}
		var reevaluationJob = jobId == null ? null : REEVALUATIONS.loadJobStatus(user, taskId, jobId);
		if (reevaluationJob != null) {
			if (promptVersionId != null && promptVersionId != reevaluationJob.promptVersionId()) {
				throw new TeacherPromptDao.TeacherTaskNotFoundException();
			}
			promptVersionId = reevaluationJob.promptVersionId();
		}
		TeacherPromptPage page = PROMPTS.loadPage(user, taskId, promptVersionId);
		TeacherNavigationSummary navigation = NAVIGATION.load(user);
		request.setAttribute("teacherPromptPage", page);
		request.setAttribute("teacherPromptPendingCount", page.selectedTask() == null ? "—"
				: page.reevaluationJobs().stream()
						.filter(job -> "queued".equals(job.status()) || "in_progress".equals(job.status()))
						.count());
		var selectedVersion = page.selectedVersion();
		var lastUpdated = selectedVersion == null
				? page.selectedTask() == null ? null : page.selectedTask().updatedAt()
				: Objects.requireNonNullElse(selectedVersion.updatedAt(), selectedVersion.createdAt());
		request.setAttribute("teacherPromptLastUpdated",
				lastUpdated == null ? "—" : HERO_DATE_FORMAT.format(lastUpdated));
		request.setAttribute("teacherNavigationSummary", navigation);
		request.setAttribute("teacherNavigationActiveItem", "prompt");
		request.setAttribute("teacherPromptError", error);
		request.setAttribute("teacherPromptNotice", notice == null ? consumeNotice(request.getSession(false)) : notice);
		request.setAttribute("teacherPromptSubmittedValues", submittedValues == null ? Map.of() : submittedValues);
		request.setAttribute("teacherPromptTaskId", taskId == null ? "" : taskId);
		TeacherPromptVersion selected = page.selectedVersion();
		Long effectiveVersionId = effectiveVersionId(promptVersionId, selected);
		request.setAttribute("teacherPromptVersionId", effectiveVersionId == null ? "" : effectiveVersionId);
		String model = submittedValue(submittedValues, "aiModel",
				selected == null ? GeminiModelCatalog.DEFAULT_MODEL : selected.aiModel());
		request.setAttribute("teacherPromptModel", model);
		request.setAttribute("teacherPromptModels", GeminiModelCatalog.selectableModels());
		request.setAttribute("teacherPromptLegacyModel", !GeminiModelCatalog.isSelectable(model));
		request.setAttribute("teacherPromptText",
				submittedValue(submittedValues, "commonPrompt", selected == null ? "" : selected.commonPrompt()));
		request.setAttribute("teacherPromptAdditionalInstruction",
				submittedValue(submittedValues, "additionalInstruction",
						selected == null ? "" : emptyIfNull(selected.additionalInstruction())));
		boolean editableDraft = isEditableDraft(page.selectedTask() != null, selected);
		request.setAttribute("teacherPromptEditableDraft", editableDraft);
		boolean fluctuationsComplete = selected != null
				&& "completed".equals(selected.fluctuationGenerationStatus())
				&& !selected.fluctuationItems().isEmpty();
		boolean allFluctuationsResolved = fluctuationsComplete
				&& selected.fluctuationItems().stream()
						.noneMatch(item -> "pending".equals(item.resolutionStatus()));
		request.setAttribute("teacherPromptCanSaveResolutions", editableDraft && fluctuationsComplete);
		request.setAttribute("teacherPromptCanGenerateExamples", editableDraft && allFluctuationsResolved);
		request.setAttribute("teacherPromptCanSaveExamples", editableDraft
				&& selected != null && "completed".equals(selected.evaluationExamplesStatus()));
		boolean canStartReevaluation = selected != null
				&& ("configured".equals(selected.promptStatus()) || "versioned".equals(selected.promptStatus()))
				&& "completed".equals(selected.fluctuationGenerationStatus())
				&& "completed".equals(selected.evaluationExamplesStatus())
				&& selected.fluctuationItems().stream()
						.noneMatch(item -> "pending".equals(item.resolutionStatus()));
		request.setAttribute("teacherPromptCanStartReevaluation", canStartReevaluation);
		request.setAttribute("teacherReevaluationPreview", preview);
		request.setAttribute("teacherReevaluationJob", reevaluationJob);
		request.setAttribute("teacherId", user.loginId());
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.setAttribute("screenDesign", "teacher");
		request.setAttribute("screenPageTitle", "プロンプト設計");
		request.setAttribute("screenStylesheet", "/css/teacher/prompt/prompt.css");
		request.setAttribute("screenScript", "/js/teacher/prompt/prompt.js");
		request.setAttribute("screenUsesCodeMirror", Boolean.TRUE);
		request.setAttribute("screenUsesMarkdown", Boolean.TRUE);
		request.getRequestDispatcher("/WEB-INF/teacher/prompt/prompt.jsp").forward(request, response);
	}

	static Long effectiveVersionId(Long requestedVersionId, TeacherPromptVersion selected) {
		if (selected == null) {
			return requestedVersionId;
		}
		return selected.promptVersionId();
	}

	static boolean isEditableDraft(boolean hasSelectedTask, TeacherPromptVersion selected) {
		return hasSelectedTask
				&& (selected == null || "draft".equals(selected.promptStatus()))
				&& (selected == null || !selected.hasActiveGeneration() || selected.isGenerationStale());
	}

	private static List<FluctuationItem> resolutions(Map<String, List<String>> values) {
		List<String> ids = values.getOrDefault("fluctuationIds", List.of());
		List<String> teacherResolutions = values.getOrDefault("teacherResolutions", List.of());
		List<String> statuses = values.getOrDefault("resolutionStatuses", List.of());
		if (ids.size() != teacherResolutions.size() || ids.size() != statuses.size() || ids.size() > 20) {
			throw new IllegalArgumentException("揺らぎ項目の入力を確認してください。");
		}
		List<FluctuationItem> updates = new ArrayList<>(ids.size());
		for (int index = 0; index < ids.size(); index++) {
			updates.add(new FluctuationItem(
					positiveId(ids.get(index), "揺らぎ項目"),
					"", "", "", "", teacherResolutions.get(index), statuses.get(index), index + 1));
		}
		return List.copyOf(updates);
	}

	private static String noticeFor(String action) {
		return switch (action) {
			case "saveDraft" -> "共通プロンプトを保存しました。";
			case "generateFluctuations" -> "揺らぎ項目を生成しました。";
			case "saveResolutions" -> "教師対応を保存しました。";
			case "generateEvaluationExamples" -> "合成評価例を生成しました。";
			case "saveEvaluationExamples" -> "評価例を保存しました。";
			case "duplicateDraft" -> "プロンプトを新しい下書きとして複製しました。";
			case "startReevaluationPreview" -> "再評価プレビューを作成しました。対象ごとの予測が完了するまでお待ちください。";
			case "retryReevaluationPreview" -> "失敗した対象の予測を再試行しています。";
			case "cancelReevaluationPreview" -> "再評価プレビューを取り消しました。";
			default -> throw new IllegalArgumentException("Unsupported prompt action.");
		};
	}

	private static boolean isReevaluationAction(String action) {
		return Set.of(
				"startReevaluationPreview",
				"retryReevaluationPreview",
				"cancelReevaluationPreview",
				"confirmReevaluationPreview").contains(action);
	}

	private static long requireVersionValue(Long promptVersionId) {
		requireVersion(promptVersionId);
		return promptVersionId;
	}

	private static String optionalScalar(Map<String, List<String>> values, String name) {
		String value = scalar(values, name);
		return value == null || value.isBlank() ? null : value;
	}

	private static String optionalPreviewCode(HttpServletRequest request) {
		String value = request.getParameter("previewCode");
		if (value == null || value.isBlank()) {
			return null;
		}
		if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			throw new IllegalArgumentException("再評価プレビューIDが不正です。");
		}
		return value;
	}

	private static void redirectToPrompt(
			HttpServletResponse response,
			String contextPath,
			long taskId,
			long promptVersionId,
			String previewCode) throws IOException {
		redirectToPrompt(response, contextPath, taskId, promptVersionId, previewCode, null);
	}

	private static void redirectToPrompt(
			HttpServletResponse response,
			String contextPath,
			long taskId,
			long promptVersionId,
			String previewCode,
			Long jobId) throws IOException {
		String target = contextPath + "/teacher/prompt?taskId=" + taskId
				+ "&promptVersionId=" + promptVersionId;
		if (previewCode != null) {
			target += "&previewCode=" + previewCode;
		}
		if (jobId != null) {
			target += "&jobId=" + jobId;
		}
		response.sendRedirect(response.encodeRedirectURL(target));
	}

	private static void setNotice(HttpSession session, String notice) {
		if (session == null) {
			throw new SecurityException("An authenticated session is required.");
		}
		session.setAttribute(NOTICE_ATTRIBUTE, notice);
	}

	private static String consumeNotice(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object notice = session.getAttribute(NOTICE_ATTRIBUTE);
		if (notice == null) {
			return null;
		}
		session.removeAttribute(NOTICE_ATTRIBUTE);
		if (!(notice instanceof String message) || message.isBlank()) {
			throw new IllegalStateException("Teacher prompt success notice is invalid.");
		}
		return message;
	}

	private static AuthenticatedUser authenticatedUser(HttpServletRequest request) {
		Object value = request.getAttribute("authenticatedUser");
		if (!(value instanceof AuthenticatedUser user) || user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication is required.");
		}
		return user;
	}

	private static Long optionalId(HttpServletRequest request, String name) {
		String value = request.getParameter(name);
		return value == null || value.isEmpty() ? null : positiveId(value, name);
	}

	private static Long optionalId(Map<String, List<String>> values, String name, String label) {
		String value = scalar(values, name);
		return value == null || value.isEmpty() ? null : positiveId(value, label);
	}

	private static long positiveId(String value, String label) {
		if (value == null || !value.matches("[0-9]{1,18}")) {
			throw new IllegalArgumentException(label + "IDが不正です。");
		}
		try {
			long id = Long.parseLong(value);
			if (id < 1) {
				throw new NumberFormatException();
			}
			return id;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(label + "IDが不正です。", e);
		}
	}

	private static long nonNegativeLong(String value) {
		if (value == null || !value.matches("[0-9]{1,18}")) {
			throw new IllegalArgumentException("プロンプト更新情報が不正です。");
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("プロンプト更新情報が不正です。", e);
		}
	}

	private static String requiredScalar(Map<String, List<String>> values, String name) {
		String value = scalar(values, name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("必要な入力がありません: " + name);
		}
		return value;
	}

	private static String scalar(Map<String, List<String>> values, String name) {
		List<String> entries = values.get(name);
		if (entries == null) {
			return null;
		}
		if (entries.size() != 1) {
			throw new IllegalArgumentException("入力形式が不正です: " + name);
		}
		return entries.getFirst();
	}

	private static String submittedValue(Map<String, List<String>> values, String name, String fallback) {
		if (values == null || !values.containsKey(name)) {
			return fallback;
		}
		String value = scalar(values, name);
		return value == null ? fallback : value;
	}

	private static String emptyIfNull(String value) {
		return value == null ? "" : value;
	}

	private static void requireVersion(Long promptVersionId) {
		if (promptVersionId == null) {
			throw new IllegalArgumentException("プロンプト版を選択してください。");
		}
	}

	private static HttpServletRequest csrfRequest(HttpServletRequest request, String token) {
		return new HttpServletRequestWrapper(request) {
			@Override
			public String getParameter(String name) {
				return "csrfToken".equals(name) ? token : super.getParameter(name);
			}
		};
	}
}
