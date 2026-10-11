package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import servlet.auth.CsrfTokens;

final class TeacherPreviewHttp {
	private static final Gson JSON = new Gson();
	private TeacherPreviewHttp() {}
	static boolean allowPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("X-Content-Type-Options", "nosniff");
		if (request.getContentLengthLong() > 512 * 1024) {
			error(response, 413, "実行コードは64 KiB、入力は合計8 KiB以下にしてください。");
			return false;
		}
		if (!CsrfTokens.isValid(request)) {
			error(response, 403, "操作の認証期限が切れました。画面を再読み込みしてください。");
			return false;
		}
		return true;
	}
	static void dispatch(HttpServletRequest request, HttpServletResponse response, boolean poll, Operation operation)
			throws SQLException, IOException, InterruptedException {
		String action = poll ? "preview-session" : TeacherExerciseServlet.single(request, "action");
		if (!java.util.List.of("preview-session", "preview-start", "preview-input", "preview-cancel").contains(action == null ? "" : action))
			throw new IllegalArgumentException("実行操作が不正です。");
		String after = TeacherExerciseServlet.single(request, "after");
		long cursor = 0;
		if (after != null) {
			if (!after.matches("[0-9]{1,19}")) throw new IllegalArgumentException("実行イベント位置が不正です。");
			try { cursor = Long.parseLong(after); }
			catch (NumberFormatException failure) { throw new IllegalArgumentException("実行イベント位置が不正です。", failure); }
		}
		String value = TeacherExerciseServlet.single(request, "preview-input".equals(action) ? "line" : "code");
		Object result = operation.execute(action, TeacherExerciseServlet.single(request, "sessionId"), cursor, value);
		response.setContentType("application/json; charset=UTF-8");
		JSON.toJson(result, response.getWriter());
	}
	static void error(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json; charset=UTF-8");
		JSON.toJson(java.util.Map.of("message", message), response.getWriter());
	}
	@FunctionalInterface interface Operation {
		Object execute(String action, String id, long cursor, String value) throws SQLException, IOException, InterruptedException;
	}
}
