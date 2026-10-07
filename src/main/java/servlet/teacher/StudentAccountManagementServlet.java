package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import control.auth.AuthenticatedUser;
import control.teacher.StudentAccountManagementControl;
import control.teacher.StudentAccountManagementControl.Target;
import control.teacher.TeacherNavigationControl;
import entity.StudentAccountCreation;
import entity.StudentAccountFilter;
import entity.UserCredential.UserType;
import lib.web.CsvCells;
import servlet.auth.CsrfTokens;

@WebServlet({"/teacher/account/account", "/teacher/accounts"})
public final class StudentAccountManagementServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final StudentAccountManagementControl CONTROL = new StudentAccountManagementControl();
	private static final Gson JSON = new Gson();

	@Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		request.setCharacterEncoding("UTF-8"); response.setHeader("Cache-Control", "no-store");
		String view = request.getParameter("view");
		try {
			AuthenticatedUser teacher = teacher(request);
			if ("detail".equals(view)) {
				json(response, CONTROL.detail(teacher, number(request.getParameter("userId")))); return;
			}
			var filter = filter(request);
			if ("csv".equals(view)) {
				var rows = CONTROL.export(teacher, filter);
				StringBuilder csv = new StringBuilder("\uFEFFID,パスワード,セキュリティレベル,学校,クラス,初回パスワード変更,研究同意,作成日時\r\n");
				for (var row : rows) {
					var account = row.account();
					String classes = account.classrooms().stream().map(value -> value.getDisplayName()).collect(java.util.stream.Collectors.joining(" / "));
					List<String> cells = List.of(account.loginId(), row.password(), "レベル" + account.securityLevel(),
							account.schoolName(), classes, firstLoginLabel(account.securityLevel(), account.mustChangePassword(), account.firstLoginStatus()),
							consentLabel(account.consent()), account.createdAt());
					csv.append(cells.stream().map(CsvCells::encode).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
				}
				response.setContentType("text/csv; charset=UTF-8");
				response.setHeader("Content-Disposition", "attachment; filename=\"student-accounts.csv\"");
				response.getWriter().write(csv.toString()); return;
			}
			var accounts = CONTROL.list(teacher);
			var options = CONTROL.options(teacher);
			if ("list".equals(view)) {
				json(response, Map.of("accounts", accounts.stream().filter(filter).toList(), "options", options, "total", accounts.size())); return;
			}
			if (view != null) throw new IllegalArgumentException("表示操作が不正です。");
			request.setAttribute("teacherNavigationSummary", new TeacherNavigationControl().load(teacher));
			request.setAttribute("teacherNavigationActiveItem", "accounts");
			request.setAttribute("teacherId", teacher.loginId());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.getRequestDispatcher("/WEB-INF/teacher/account/account.jsp").forward(request, response);
		} catch (SecurityException failure) {
			getError(response, view, 403, "この生徒アカウントを確認する権限がありません。");
		} catch (IllegalArgumentException failure) {
			getError(response, view, 400, failure.getMessage());
		} catch (SQLException | IllegalStateException failure) {
			getServletContext().log("Student account management load failed.", failure);
			getError(response, view, 503, "読み込めませんでした。資格情報の設定と接続状態を確認してください。");
		}
	}

	@Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8"); response.setHeader("Cache-Control", "no-store");
		if (!CsrfTokens.isValid(request)) { error(response, 403, "画面を読み込み直してから操作してください。"); return; }
		String value = request.getParameter("password");
		char[] password = value == null ? new char[0] : value.toCharArray();
		try {
			AuthenticatedUser teacher = teacher(request);
			if (!"yes".equals(request.getParameter("changeConfirmed"))) throw new IllegalArgumentException("確認ダイアログで確定してください。");
			String action = request.getParameter("action");
			if ("create".equals(action)) {
				var input = new StudentAccountCreation(number(request.getParameter("schoolId")), number(request.getParameter("classroomId")),
						request.getParameter("classroomName"), Math.toIntExact(number(request.getParameter("count"))));
				json(response, Map.of("message", "生徒アカウントを作成しました。", "ids", CONTROL.create(teacher, input, password))); return;
			}
			List<Target> targets = targets(request.getParameterValues("userId"), request.getParameterValues("version"));
			if ("reveal".equals(action)) {
				if (targets.size() != 1) throw new IllegalArgumentException("生徒を1人選択してください。");
				json(response, Map.of("password", CONTROL.reveal(teacher, targets.getFirst()))); return;
			}
			CONTROL.change(teacher, targets, action, password);
			json(response, Map.of("message", "生徒アカウントを更新しました。"));
		} catch (SecurityException failure) {
			error(response, 403, "この学校・生徒を操作する権限がありません。");
		} catch (IllegalArgumentException | ArithmeticException failure) {
			error(response, 400, failure instanceof ArithmeticException ? "人数が不正です。" : failure.getMessage());
		} catch (SQLException | IllegalStateException failure) {
			getServletContext().log("Student account management save failed.", failure);
			error(response, 503, "操作できませんでした。資格情報の設定と接続状態を確認してください。");
		} finally { Arrays.fill(password, '\0'); }
	}

	static List<Target> targets(String[] ids, String[] versions) {
		if (ids == null || versions == null || ids.length == 0 || ids.length > 200 || ids.length != versions.length)
			throw new IllegalArgumentException("選択対象・更新版が不正です。");
		List<Target> result = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) result.add(new Target(number(ids[i]), number(versions[i])));
		return List.copyOf(result);
	}
	static long number(String value) {
		try { long number = Long.parseLong(value); if (number < 0) throw new NumberFormatException(); return number; }
		catch (NumberFormatException failure) { throw new IllegalArgumentException("対象・設定値が不正です。", failure); }
	}
	private static StudentAccountFilter filter(HttpServletRequest request) {
		return new StudentAccountFilter(request.getParameter("q"), optionalNumber(request.getParameter("schoolId")),
				optionalNumber(request.getParameter("classroomId")), request.getParameter("security"),
				request.getParameter("firstLogin"), request.getParameter("consent"), request.getParameter("status"));
	}
	private static long optionalNumber(String value) { return value == null || value.isEmpty() ? 0 : number(value); }
	private static AuthenticatedUser teacher(HttpServletRequest request) {
		if (!(request.getAttribute("authenticatedUser") instanceof AuthenticatedUser user) || user.userType() != UserType.TEACHER)
			throw new SecurityException("Teacher required.");
		return user;
	}
	private static String firstLoginLabel(int level, boolean required, String status) {
		return level == 1 ? "対象外" : required || !"completed".equals(status) ? "未完了" : "完了";
	}
	private static String consentLabel(String value) {
		return switch (value) { case "agreed" -> "同意"; case "declined" -> "不同意"; case "withdrawn" -> "撤回"; default -> "未確認"; };
	}
	private static void getError(HttpServletResponse response, String view, int status, String message) throws IOException {
		if ("list".equals(view) || "detail".equals(view)) error(response, status, message);
		else response.sendError(status, message);
	}
	private static void error(HttpServletResponse response, int status, String message) throws IOException { response.setStatus(status); json(response, Map.of("error", message)); }
	private static void json(HttpServletResponse response, Object value) throws IOException {
		response.setContentType("application/json; charset=UTF-8"); response.getWriter().write(JSON.toJson(value));
	}
}
