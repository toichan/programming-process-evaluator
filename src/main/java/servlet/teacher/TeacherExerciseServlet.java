package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import control.auth.AuthenticatedUser;
import control.teacher.TeacherExerciseControl;
import control.teacher.TeacherNavigationControl;
import entity.*;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet("/teacher/exercises")
public final class TeacherExerciseServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Gson JSON = new GsonBuilder().registerTypeAdapter(java.time.LocalDateTime.class,
			(JsonSerializer<java.time.LocalDateTime>) (value, type, context) -> new JsonPrimitive(value.toString())).create();
	private final TeacherExerciseControl exercises = new TeacherExerciseControl();
	@Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("X-Content-Type-Options", "nosniff");
		try {
			var user = teacher(request);
			var filter = filter(request);
			String view = single(request, "view");
			if ("list".equals(view)) {
				response.setContentType("application/json; charset=UTF-8");
				JSON.toJson(exercises.list(user, filter), response.getWriter());
			} else if ("detail".equals(view)) {
				var detail = exercises.detail(user, requiredId(request, "studentId"), requiredId(request, "classroomId"));
				response.setContentType("application/json; charset=UTF-8");
				JSON.toJson(detail, response.getWriter());
			} else if ("csv".equals(view)) {
				String csv = exercises.csv(user, filter);
				response.setContentType("text/csv; charset=UTF-8");
				response.setHeader("Content-Disposition", "attachment; filename=\"exercise_list.csv\"");
				response.getWriter().write(csv);
			} else if ("download".equals(view) || "zip".equals(view) || "bulk".equals(view)) {
				ExerciseDownload download = "bulk".equals(view) ? exercises.bulk(user, filter)
						: exercises.download(user, requiredId(request, "studentId"), requiredId(request, "classroomId"),
								"download".equals(view) ? requiredId(request, "entryId") : null);
				response.setContentType(download.contentType());
				response.setHeader("Content-Disposition", download.contentDisposition());
				download.write(response.getOutputStream());
			} else if (view == null) {
				exercises.list(user, filter);
				request.setAttribute("teacherNavigationSummary", new TeacherNavigationControl().load(user));
				request.setAttribute("teacherNavigationActiveItem", "exercise-review");
				request.setAttribute("teacherId", user.loginId());
				request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
				request.setAttribute("screenDesign", "teacher");
				request.setAttribute("screenBodyClass", "teacher-exercise-review-screen");
				request.setAttribute("screenUsesCodeMirror", true);
				request.setAttribute("screenPageTitle", "授業演習コード確認");
				request.setAttribute("screenStylesheet", "/css/teacher/exercise/exercise.css");
				request.setAttribute("screenScript", "/js/teacher/exercise/exercise.js");
				request.getRequestDispatcher("/WEB-INF/teacher/exercise/exercise.jsp").forward(request, response);
			} else throw new IllegalArgumentException("操作が不正です。");
		} catch (PythonExecutionInput.TooLargeException failure) { error(response, 413, failure.getMessage()); }
		catch (IllegalArgumentException failure) { error(response, 400, failure.getMessage()); }
		catch (ExerciseNotFoundException failure) { error(response, 404, "対象の授業演習を参照できません。"); }
		catch (SecurityException failure) { error(response, 403, "この授業演習を確認する権限がありません。"); }
		catch (SQLException failure) {
			getServletContext().log("Teacher exercise review read failed.", failure);
			error(response, 503, "授業演習データを取得できませんでした。時間をおいて再試行してください。");
		}
	}
	private static void error(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json; charset=UTF-8");
		JSON.toJson(java.util.Map.of("message", message), response.getWriter());
	}
	private static AuthenticatedUser teacher(HttpServletRequest request) {
		if (request.getAttribute("authenticatedUser") instanceof AuthenticatedUser user
				&& user.userType() == UserType.TEACHER) return user;
		throw new SecurityException("Teacher authentication required.");
	}
	static String single(HttpServletRequest request, String name) {
		var values = request.getParameterValues(name);
		if (values == null) return null;
		if (values.length != 1) throw new IllegalArgumentException("同じ項目を複数指定できません。");
		return values[0];
	}
	static Long optionalId(HttpServletRequest request, String name) {
		String value = single(request, name);
		if (value == null || value.isEmpty()) return null;
		if (!value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("対象IDが不正です。");
		try { return Long.valueOf(value); }
		catch (NumberFormatException failure) { throw new IllegalArgumentException("対象IDが不正です。", failure); }
	}
	private static long requiredId(HttpServletRequest request, String name) {
		var value = optionalId(request, name);
		if (value == null) throw new IllegalArgumentException("対象IDが必要です。");
		return value;
	}
	static TeacherExerciseFilter filter(HttpServletRequest request) {
		return new TeacherExerciseFilter(optionalId(request, "schoolId"), optionalId(request, "classroomId"),
				single(request, "consent"), single(request, "search"), single(request, "sort"), single(request, "direction"));
	}
}
