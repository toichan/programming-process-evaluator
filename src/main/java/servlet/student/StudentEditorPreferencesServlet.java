package servlet.student;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import control.auth.AuthenticatedUser;
import control.student.StudentEditorPreferencesControl;
import entity.EditorPreferences;
import entity.EditorPreferences.Theme;
import servlet.auth.CsrfTokens;

@WebServlet("/student/editor/preferences")
public final class StudentEditorPreferencesServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAX_FORM_BYTES = 4 * 1024;
	private static final Gson GSON = new Gson();
	private static final StudentEditorPreferencesControl PREFERENCES = new StudentEditorPreferencesControl();

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

		try {
			EditorPreferences preferences = new EditorPreferences(
					parseInteger(request.getParameter("fontSizePx"), "文字サイズ"),
					parseBoolean(request.getParameter("lineWrapping")),
					parseInteger(request.getParameter("indentWidth"), "インデント幅"),
					Theme.fromValue(request.getParameter("theme")));
			PREFERENCES.save(user, preferences);
			writeJson(response, HttpServletResponse.SC_OK, Map.of("status", "saved"));
		} catch (IllegalArgumentException e) {
			writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
					error("invalid_preferences", e.getMessage()));
		} catch (SecurityException e) {
			writeJson(response, HttpServletResponse.SC_FORBIDDEN,
					error("forbidden", "この操作を実行する権限がありません。"));
		} catch (SQLException e) {
			getServletContext().log("Student editor preferences could not be saved.", e);
			writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					error("storage_unavailable", "設定を保存できませんでした。時間をおいて再度お試しください。"));
		}
	}

	private static int parseInteger(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "を指定してください。");
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(label + "の指定が正しくありません。", e);
		}
	}

	private static boolean parseBoolean(String value) {
		if ("true".equals(value)) {
			return true;
		}
		if ("false".equals(value)) {
			return false;
		}
		throw new IllegalArgumentException("コードの折り返し設定が正しくありません。");
	}

	private static Map<String, Object> error(String code, String message) {
		return Map.of("errorCode", code, "message", message);
	}

	private static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.getWriter().write(GSON.toJson(body));
	}
}
