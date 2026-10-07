package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import control.auth.AuthenticatedUser;
import control.teacher.TeacherDistributionControl;
import control.teacher.TeacherNavigationControl;
import entity.StudentExerciseEntry;
import entity.TeacherDistributionInput;
import entity.TeacherDistributionInput.Target;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet("/teacher/distribution")
public final class TeacherDistributionServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAX_REQUEST_BYTES = 4 * 1024 * 1024;
	private static final String FLASH_NOTICE = TeacherDistributionServlet.class.getName() + ".notice";
	private static final TeacherDistributionControl CONTROL = new TeacherDistributionControl();
	private static final Gson JSON = new Gson();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		try {
			AuthenticatedUser user = teacher(request);
			if ("template".equals(request.getParameter("view"))) {
				long id = positiveId(request.getParameter("id"));
				writeJson(response, CONTROL.loadTemplate(user, id));
				return;
			}
			if (request.getParameter("view") != null) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "表示操作が不正です。");
				return;
			}
			render(request, response, user);
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (IllegalArgumentException failure) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, failure.getMessage());
		} catch (SQLException failure) {
			getServletContext().log("Teacher distribution page could not be loaded.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		AuthenticatedUser user;
		try {
			user = teacher(request);
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		try {
			if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
				throw new IllegalArgumentException("送信内容は4MB以内にしてください。");
			}
			String contentType = request.getContentType();
			if (contentType == null || !contentType.split(";", 2)[0].trim()
					.equalsIgnoreCase("application/x-www-form-urlencoded")) {
				throw new IllegalArgumentException("フォーム形式で送信してください。");
			}
			if (!CsrfTokens.isValid(request)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "画面を再読み込みしてください。");
				return;
			}
			String action = requiredParameter(request, "action");
			switch (action) {
			case "runPreview" -> {
				writeJson(response, CONTROL.startPreview(user, requiredParameter(request, "code")));
				return;
			}
			case "pollPreview" -> {
				writeJson(response, CONTROL.pollPreview(user, requiredParameter(request, "sessionId"),
						nonNegativeLong(request.getParameter("cursor"))));
				return;
			}
			case "sendPreviewInput" -> {
				CONTROL.sendPreviewInput(user, requiredParameter(request, "sessionId"),
						requiredParameter(request, "line"));
				writeJson(response, Map.of("accepted", true));
				return;
			}
			case "cancelPreview" -> {
				CONTROL.cancelPreview(user, requiredParameter(request, "sessionId"));
				writeJson(response, Map.of("cancelled", true));
				return;
			}
			case "saveDraft" -> {
				CONTROL.saveDraft(user, input(request, false));
				flash(request, "下書きを保存しました。");
			}
			case "distribute" -> {
				TeacherDistributionControl.ScheduleResult result = CONTROL.schedule(user, input(request, true));
				String notice = result.processingUnavailable()
						? "配信設定を保存しましたが、即時配信の状態を更新できませんでした。配信履歴を確認してください。"
						: result.completedImmediateTargets() < result.expectedImmediateTargets()
								? "配信設定を保存しました。処理できなかったクラスは状態と履歴を確認してください。"
								: "配信設定を保存しました。";
				flash(request, notice);
			}
			case "archive" -> {
				CONTROL.archive(user, positiveId(request.getParameter("templateId")),
						nonNegativeInt(request.getParameter("expectedVersion")));
				flash(request, "テンプレートをアーカイブしました。");
			}
			case "stop" -> {
				CONTROL.stop(user, positiveId(request.getParameter("distributionId")));
				flash(request, "未配信・予約中のクラスを停止しました。配信済みの演習データは保持されています。");
			}
			case "resume" -> {
				CONTROL.resume(user, positiveId(request.getParameter("distributionId")));
				flash(request, "停止中のクラスを再開しました。");
			}
			case "stopTarget" -> {
				CONTROL.stopTarget(user, positiveId(request.getParameter("targetId")));
				flash(request, "クラスへの未配信・予約配信を停止しました。配信済みコードは保持されています。");
			}
			case "resumeTarget" -> {
				CONTROL.resumeTarget(user, positiveId(request.getParameter("targetId")));
				flash(request, "クラスへの配信を再開しました。");
			}
			case "rescheduleTarget" -> {
				CONTROL.rescheduleTarget(user, positiveId(request.getParameter("targetId")),
						requiredDateTime(request.getParameter("scheduledAt")));
				flash(request, "クラスの配信日時を変更しました。");
			}
			default -> throw new IllegalArgumentException("操作が不正です。");
			}
			response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/teacher/distribution"));
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			getServletContext().log("Teacher distribution preview execution was interrupted.", failure);
			writeApiError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"コード実行を完了できませんでした。");
		} catch (IOException failure) {
			getServletContext().log("Teacher distribution preview execution failed.", failure);
			writeApiError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"コード実行サービスを利用できません。時間をおいて再度お試しください。");
		} catch (IllegalArgumentException failure) {
			if (acceptsJson(request)) {
				writeApiError(response, HttpServletResponse.SC_BAD_REQUEST, failure.getMessage());
			} else {
				forwardError(request, response, user, HttpServletResponse.SC_BAD_REQUEST, failure.getMessage());
			}
		} catch (SQLException failure) {
			getServletContext().log("Teacher distribution operation failed.", failure);
			if (acceptsJson(request)) {
				writeApiError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "操作できませんでした。");
			} else {
				forwardError(request, response, user, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
						"操作できませんでした。入力と配信履歴を確認してください。");
			}
		}
	}

	private void render(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user)
			throws SQLException, ServletException, IOException {
		request.setAttribute("teacherDistributionPage", CONTROL.loadPage(user));
		request.setAttribute("teacherNavigationSummary", new TeacherNavigationControl().load(user));
		request.setAttribute("teacherNavigationActiveItem", "distribution");
		request.setAttribute("teacherId", user.loginId());
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.setAttribute("distributionRequestToken", UUID.randomUUID().toString());
		HttpSession session = request.getSession(false);
		if (session != null) {
			Object notice = session.getAttribute(FLASH_NOTICE);
			if (notice instanceof String text) {
				request.setAttribute("distributionNotice", text);
				session.removeAttribute(FLASH_NOTICE);
			}
		}
		request.getRequestDispatcher("/WEB-INF/teacher/distribution/distribution.jsp").forward(request, response);
	}

	private void forwardError(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user,
			int status, String message) throws IOException, ServletException {
		request.setAttribute("distributionFormError", message);
		request.setAttribute("distributionFormName", request.getParameter("name"));
		request.setAttribute("distributionFormRoot", request.getParameter("rootName"));
		request.setAttribute("distributionFormId", request.getParameter("templateId"));
		request.setAttribute("distributionFormVersion", request.getParameter("expectedVersion"));
		request.setAttribute("distributionFormItems", request.getParameter("itemsJson"));
		request.setAttribute("distributionFormTargets", request.getParameter("targetsJson"));
		request.setAttribute("distributionRequestToken", request.getParameter("requestToken"));
		response.setStatus(status);
		try {
			render(request, response, user);
		} catch (SQLException failure) {
			getServletContext().log("Teacher distribution error page could not be loaded.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static TeacherDistributionInput input(HttpServletRequest request, boolean requireTargets) {
		long templateId = optionalNonNegativeId(request.getParameter("templateId"));
		int version = nonNegativeInt(request.getParameter("expectedVersion"));
		String name = requiredParameter(request, "name");
		String rootName = requiredParameter(request, "rootName");
		List<TeacherDistributionInput.TemplateItem> items = parseItems(request.getParameter("itemsJson"));
		List<Target> targets = parseTargets(request.getParameter("targetsJson"), requireTargets);
		if (requireTargets && targets.isEmpty()) {
			throw new IllegalArgumentException("配信先のクラスを選択してください。");
		}
		return new TeacherDistributionInput(templateId, version, name, rootName, items, targets,
				requiredParameter(request, "requestToken"));
	}

	private static List<TeacherDistributionInput.TemplateItem> parseItems(String value) {
		JsonArray array = parseArray(value, "テンプレート項目");
		List<TeacherDistributionInput.TemplateItem> items = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) throw new IllegalArgumentException("テンプレート項目が不正です。");
			JsonObject item = element.getAsJsonObject();
			String path = jsonString(item, "path");
			String type = jsonString(item, "type");
			String content = item.has("content") && !item.get("content").isJsonNull()
					? item.get("content").getAsString() : null;
			items.add(new TeacherDistributionInput.TemplateItem(path, StudentExerciseEntry.Type.fromValue(type), content));
		}
		return List.copyOf(items);
	}

	private static List<Target> parseTargets(String value, boolean requireExplicitSchedule) {
		JsonArray array = parseArray(value, "配信先");
		List<Target> targets = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) throw new IllegalArgumentException("配信先が不正です。");
			JsonObject target = element.getAsJsonObject();
			long classroomId = target.has("classroomId") ? target.get("classroomId").getAsLong() : 0;
			boolean hasImmediate = target.has("immediate") && target.get("immediate").isJsonPrimitive()
					&& target.get("immediate").getAsJsonPrimitive().isBoolean();
			boolean immediate = hasImmediate && target.get("immediate").getAsBoolean();
			LocalDateTime scheduledAt = null;
			if (target.has("scheduledAt") && !target.get("scheduledAt").isJsonNull()
					&& !target.get("scheduledAt").getAsString().isBlank()) {
				try {
					scheduledAt = LocalDateTime.parse(target.get("scheduledAt").getAsString());
				} catch (DateTimeParseException failure) {
					throw new IllegalArgumentException("配信日時が不正です。", failure);
				}
			}
			if (requireExplicitSchedule && (!hasImmediate || (immediate && scheduledAt != null)
					|| (!immediate && scheduledAt == null))) {
				throw new IllegalArgumentException("各クラスの即時配信または配信日時を指定してください。");
			}
			targets.add(new Target(classroomId, scheduledAt));
		}
		return List.copyOf(targets);
	}

	private static JsonArray parseArray(String value, String label) {
		if (value == null || value.isBlank() || value.length() > MAX_REQUEST_BYTES) {
			throw new IllegalArgumentException(label + "の入力が不正です。");
		}
		try {
			JsonElement parsed = JsonParser.parseString(value);
			if (!parsed.isJsonArray()) throw new IllegalArgumentException(label + "の入力が不正です。");
			return parsed.getAsJsonArray();
		} catch (com.google.gson.JsonParseException failure) {
			throw new IllegalArgumentException(label + "のJSON形式が不正です。", failure);
		}
	}

	private static String jsonString(JsonObject object, String key) {
		if (!object.has(key) || object.get(key).isJsonNull()) {
			throw new IllegalArgumentException("テンプレート項目が不正です。");
		}
		return object.get(key).getAsString();
	}

	private static String requiredParameter(HttpServletRequest request, String name) {
		String value = request.getParameter(name);
		if (value == null) throw new IllegalArgumentException("必要な入力が不足しています。");
		return value;
	}

	private static long positiveId(String value) {
		long id = optionalNonNegativeId(value);
		if (id == 0) throw new IllegalArgumentException("対象を正しく指定してください。");
		return id;
	}

	private static long optionalNonNegativeId(String value) {
		if (value == null || value.isBlank()) return 0;
		try {
			long id = Long.parseLong(value);
			if (id < 0) throw new NumberFormatException();
			return id;
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("対象・設定値が不正です。", failure);
		}
	}

	private static int nonNegativeInt(String value) {
		long parsed = optionalNonNegativeId(value);
		if (parsed > Integer.MAX_VALUE) throw new IllegalArgumentException("更新情報が不正です。");
		return (int) parsed;
	}

	private static long nonNegativeLong(String value) {
		if (value == null || value.isBlank()) return 0;
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 0) throw new NumberFormatException();
			return parsed;
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("実行結果の位置が不正です。", failure);
		}
	}

	private static LocalDateTime requiredDateTime(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("配信日時を指定してください。");
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException failure) {
			throw new IllegalArgumentException("配信日時が不正です。", failure);
		}
	}

	private static AuthenticatedUser teacher(HttpServletRequest request) {
		if (!(request.getAttribute("authenticatedUser") instanceof AuthenticatedUser user)
				|| user.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher access is required.");
		}
		return user;
	}

	private static void flash(HttpServletRequest request, String message) {
		request.getSession().setAttribute(FLASH_NOTICE, message);
	}

	private static void writeJson(HttpServletResponse response, Object value) throws IOException {
		response.setContentType("application/json; charset=UTF-8");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(JSON.toJson(value));
	}

	private static boolean acceptsJson(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		return accept != null && accept.contains("application/json");
	}

	private static void writeApiError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		writeJson(response, Map.of("message", message));
	}
}
