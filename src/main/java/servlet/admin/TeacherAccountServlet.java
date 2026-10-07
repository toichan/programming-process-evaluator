package servlet.admin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import control.admin.SchoolControl;
import control.admin.TeacherAccountControl;
import control.auth.AuthenticatedUser;
import entity.SchoolDetails;
import entity.SchoolInput;
import entity.TeacherAccountDetails;
import entity.TeacherAccountInput;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet({"/admin/management", "/admin/teachers"})
public final class TeacherAccountServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final TeacherAccountControl TEACHERS = new TeacherAccountControl();
	private static final SchoolControl SCHOOLS = new SchoolControl();
	private static final Gson JSON = new Gson();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		try {
			AuthenticatedUser admin = admin(request);
			String view = request.getParameter("view");
			if ("history".equals(view)) {
				Long id = request.getParameter("userId") == null ? null : number(request.getParameter("userId"));
				var history = TEACHERS.history(admin, id, "login".equals(request.getParameter("kind")));
				json(response, Map.of("history", history.stream().map(row -> Map.of(
						"occurredAt", row.occurredAt().toString(), "actor", text(row.actor()), "teacher", text(row.teacher()),
						"action", row.action(), "result", row.result(), "detail", text(row.detail()))).toList()));
				return;
			}
			List<TeacherAccountDetails> accounts = TEACHERS.list(admin);
			String query = text(request.getParameter("q"));
			if (query.length() > 64) throw new IllegalArgumentException("検索条件は64文字以内にしてください。");
			List<TeacherAccountDetails> filtered = accounts.stream()
					.filter(account -> account.loginId().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
			List<SchoolDetails> schools = SCHOOLS.loadSchools(admin);
			if ("csv".equals(view)) { csv(response, filtered, schools); return; }
			if ("teacher".equals(view)) {
				long id = number(request.getParameter("userId"));
				TeacherAccountDetails account = accounts.stream().filter(row -> row.userId() == id).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("教師が見つかりません。"));
				json(response, Map.of("userId", account.userId(), "loginId", account.loginId(), "version", account.version(),
						"schools", account.schools().stream().map(school -> school.schoolId()).toList(), "features", account.features()));
				return;
			}
			if (view != null) throw new IllegalArgumentException("表示操作が不正です。");
			request.setAttribute("teachers", filtered);
			request.setAttribute("schools", schools);
			request.setAttribute("featureOrder", TeacherAccountInput.FEATURE_ORDER);
			request.setAttribute("featureLabels", TeacherAccountInput.FEATURE_LABELS);
			request.setAttribute("teacherCount", accounts.size());
			request.setAttribute("adminId", admin.loginId());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.getRequestDispatcher("/WEB-INF/admin/management.jsp").forward(request, response);
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (IllegalArgumentException failure) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, failure.getMessage());
		} catch (SQLException failure) {
			getServletContext().log("Teacher account management load failed.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		if (!CsrfTokens.isValid(request)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			json(response, Map.of("error", "画面を読み込み直してから操作してください。")); return;
		}
		try {
			AuthenticatedUser admin = admin(request);
			if (!"yes".equals(request.getParameter("changeConfirmed")))
				throw new IllegalArgumentException("確認ダイアログで確定してください。");
			String action = request.getParameter("action");
			String password;
			if ("school".equals(action)) {
				SCHOOLS.save(admin, number(request.getParameter("schoolId")), number(request.getParameter("version")),
						new SchoolInput(request.getParameter("name"), Math.toIntExact(number(request.getParameter("securityLevel")))));
				json(response, Map.of("message", "学校情報を保存しました。")); return;
			}
			TeacherAccountInput input = "create".equals(action) || "permissions".equals(action)
					? new TeacherAccountInput(request.getParameter("loginId"), schoolIds(request), features(request)) : null;
			if ("create".equals(action)) password = TEACHERS.create(admin, input);
			else password = TEACHERS.change(admin, number(request.getParameter("userId")),
					number(request.getParameter("version")), action, input);
			if (password == null) json(response, Map.of("message", "教師情報を更新しました。"));
			else json(response, Map.of("message", "パスワードを発行しました。", "password", password));
		} catch (SecurityException failure) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			json(response, Map.of("error", "この操作は管理者だけが利用できます。"));
		} catch (IllegalArgumentException | ArithmeticException failure) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			json(response, Map.of("error", failure instanceof ArithmeticException ? "設定値が不正です。" : failure.getMessage()));
		} catch (SQLException failure) {
			getServletContext().log("Teacher account management save failed.", failure);
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			json(response, Map.of("error", "保存できませんでした。時間をおいて再度お試しください。"));
		}
	}

	private static AuthenticatedUser admin(HttpServletRequest request) {
		Object value = request.getAttribute("authenticatedUser");
		if (!(value instanceof AuthenticatedUser user) || user.userType() != UserType.ADMIN)
			throw new SecurityException("Admin required.");
		return user;
	}
	static long number(String value) {
		try {
			long result = Long.parseLong(value);
			if (result < 0) throw new NumberFormatException();
			return result;
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("対象または更新情報が不正です。画面を読み込み直してください。", failure);
		}
	}
	private static Set<Long> schoolIds(HttpServletRequest request) {
		String[] values = request.getParameterValues("schoolIds");
		return values == null ? Set.of() : Arrays.stream(values).map(TeacherAccountServlet::number)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
	private static Set<String> features(HttpServletRequest request) {
		String[] values = request.getParameterValues("features");
		return values == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(values));
	}
	private static String text(String value) { return value == null ? "" : value; }
	private static void json(HttpServletResponse response, Object value) throws IOException {
		response.setContentType("application/json; charset=UTF-8");
		response.getWriter().write(JSON.toJson(value));
	}
	static String csvCell(String value) {
		return lib.web.CsvCells.encode(value);
	}
	private static void csv(HttpServletResponse response, List<TeacherAccountDetails> accounts, List<SchoolDetails> schools)
			throws IOException {
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"teachers.csv\"");
		var writer = response.getWriter();
		writer.write("\ufeff");
		var headers = new java.util.ArrayList<String>(List.of("教師ID", "状態"));
		schools.forEach(school -> headers.add("学校:" + school.name()));
		TeacherAccountInput.FEATURE_ORDER.forEach(feature -> headers.add(TeacherAccountInput.FEATURE_LABELS.get(feature)));
		headers.addAll(List.of("作成日時", "作成者"));
		writer.write(headers.stream().map(TeacherAccountServlet::csvCell).collect(Collectors.joining(",")) + "\r\n");
		for (TeacherAccountDetails account : accounts) {
			var cells = new java.util.ArrayList<String>(List.of(account.loginId(), account.getStatusLabel()));
			schools.forEach(school -> cells.add(account.schools().stream().anyMatch(option -> option.schoolId() == school.id()) ? "1" : "0"));
			TeacherAccountInput.FEATURE_ORDER.forEach(feature -> cells.add(account.features().contains(feature) ? "1" : "0"));
			cells.add(account.createdAt().toString()); cells.add(text(account.createdBy()));
			writer.write(cells.stream().map(TeacherAccountServlet::csvCell).collect(Collectors.joining(",")) + "\r\n");
		}
	}
}
