package servlet.student;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import control.student.StudentRubricControl;

@WebServlet("/student/rubric")
public final class StudentRubricServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Gson GSON = new Gson();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		response.setContentType("application/json; charset=UTF-8");
		try {
			response.getWriter().write(GSON.toJson(
					new StudentRubricControl().load(StudentHomeServlet.authenticatedUser(request))));
		} catch (SecurityException e) {
			error(response, 403, "forbidden", "ログイン状態と利用権限を確認してください。");
		} catch (IllegalArgumentException | IllegalStateException e) {
			getServletContext().log("Standard rubric is missing or incomplete.", e);
			error(response, 503, "rubric_unavailable", "標準ルーブリックが未登録、または内容が不完全です。管理者に確認してください。");
		} catch (SQLException e) {
			getServletContext().log("Standard rubric could not be loaded.", e);
			error(response, 503, "storage_unavailable", "ルーブリックを取得できませんでした。時間をおいて再度お試しください。");
		}
	}

	private static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.getWriter().write(GSON.toJson(Map.of("errorCode", code, "message", message)));
	}
}
