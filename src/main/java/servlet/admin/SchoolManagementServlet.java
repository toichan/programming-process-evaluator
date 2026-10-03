package servlet.admin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import control.admin.SchoolControl;
import control.auth.AuthenticatedUser;
import entity.SchoolDetails;
import entity.SchoolInput;
import servlet.auth.CsrfTokens;

@WebServlet("/admin/schools")
public final class SchoolManagementServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final SchoolControl SCHOOLS = new SchoolControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		render(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		if (!CsrfTokens.isValid(request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "画面を読み込み直してください。");
			return;
		}
		try {
			long id = number(request.getParameter("schoolId"));
			long version = number(request.getParameter("version"));
			if (!"yes".equals(request.getParameter("changeConfirmed"))) {
				throw new IllegalArgumentException("確認ダイアログで確定してから保存してください。");
			}
			SchoolInput input = new SchoolInput(request.getParameter("name"),
					Math.toIntExact(number(request.getParameter("securityLevel"))));
			long savedId = SCHOOLS.save(user(request), id, version, input);
			response.sendRedirect(response.encodeRedirectURL(request.getContextPath()
					+ "/admin/schools?schoolId=" + savedId + "&notice=saved"));
		} catch (IllegalArgumentException | ArithmeticException e) {
			request.setAttribute("schoolError", e instanceof ArithmeticException
					? "セキュリティレベル1または2を選択してください。" : e.getMessage());
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			render(request, response);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("School management save failed.", e);
			request.setAttribute("schoolError", "学校情報を保存できませんでした。時間をおいて再度お試しください。");
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			render(request, response);
		}
	}

	private void render(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			List<SchoolDetails> schools = SCHOOLS.loadSchools(user(request));
			String selectedId = request.getParameter("schoolId");
			if (selectedId != null) {
				long id = number(selectedId);
				if (id > 0) {
					SchoolDetails selected = schools.stream().filter(school -> school.id() == id).findFirst()
							.orElseThrow(() -> new IllegalArgumentException("学校が見つかりません。"));
					request.setAttribute("selectedSchool", selected);
				}
			}
			request.setAttribute("schools", schools);
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			request.setAttribute("displayName", user(request).displayName());
			request.getRequestDispatcher("/WEB-INF/admin/schools.jsp").forward(request, response);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("School management load failed.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static AuthenticatedUser user(HttpServletRequest request) {
		return (AuthenticatedUser) request.getAttribute("authenticatedUser");
	}

	private static long number(String value) {
		try {
			long result = Long.parseLong(value);
			if (result < 0) throw new NumberFormatException();
			return result;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("学校または設定情報が不正です。画面を読み込み直してください。", e);
		}
	}
}
