package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import control.auth.AuthenticatedUser;
import control.teacher.ProgressRecordNotFoundException;
import control.teacher.TeacherNavigationControl;
import control.teacher.TeacherProgressControl;
import entity.TeacherProgressRow;
import entity.TeacherProgressDetail;
import entity.UserCredential.UserType;

@WebServlet("/teacher/progress")
public final class TeacherProgressServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final TeacherProgressControl PROGRESS = new TeacherProgressControl();
	private static final TeacherNavigationControl NAVIGATION = new TeacherNavigationControl();
	private static final Gson JSON = new Gson();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		AuthenticatedUser user;
		try {
			user = authenticatedTeacher(request);
			if ("preview-session".equals(TeacherExerciseServlet.single(request, "view"))) {
				preview(request, response, user, true);
				return;
			}
			if ("detail".equals(request.getParameter("view"))) {
				writeDetail(request, response, user);
				return;
			}
			if ("download".equals(request.getParameter("view"))) {
				writeCodeDownload(request, response, user);
				return;
			}
			if (request.getParameter("view") != null) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
			render(request, response, user);
		} catch (entity.PythonExecutionInput.TooLargeException failure) {
			TeacherPreviewHttp.error(response, 413, failure.getMessage());
		} catch (IllegalArgumentException failure) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, failure.getMessage());
		} catch (ProgressRecordNotFoundException failure) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (SecurityException failure) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException failure) {
			getServletContext().log("Teacher progress could not be loaded.", failure);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (control.student.PythonRunnerRequestException failure) {
			TeacherPreviewHttp.error(response, failure.statusCode(), "実行サービスが操作を受け付けませんでした。");
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			TeacherPreviewHttp.error(response, 503, "実行サービスに接続できません。");
		} catch (IOException | IllegalStateException failure) {
			getServletContext().log("Teacher progress preview service failed.", failure);
			TeacherPreviewHttp.error(response, 503, "実行サービスに接続できません。");
		}
	}

	@Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			if (!TeacherPreviewHttp.allowPost(request, response)) return;
			preview(request, response, authenticatedTeacher(request), false);
		} catch (entity.PythonExecutionInput.TooLargeException failure) { TeacherPreviewHttp.error(response, 413, failure.getMessage()); }
		catch (IllegalArgumentException failure) { TeacherPreviewHttp.error(response, 400, failure.getMessage()); }
		catch (ProgressRecordNotFoundException failure) { TeacherPreviewHttp.error(response, 404, "対象の最新コードを参照できません。"); }
		catch (SecurityException failure) { TeacherPreviewHttp.error(response, 403, "実行対象を操作する権限がありません。"); }
		catch (control.student.PythonRunnerRequestException failure) { TeacherPreviewHttp.error(response, failure.statusCode(), "実行サービスが操作を受け付けませんでした。"); }
		catch (InterruptedException failure) { Thread.currentThread().interrupt(); TeacherPreviewHttp.error(response, 503, "実行サービスに接続できません。"); }
		catch (SQLException | IOException | IllegalStateException failure) {
			getServletContext().log("Teacher progress preview failed.", failure);
			TeacherPreviewHttp.error(response, 503, "実行・対象の確認に失敗しました。再試行してください。");
		}
	}
	private void preview(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user, boolean poll)
			throws SQLException, IOException, InterruptedException {
		long assignment = requiredPositiveId(request, "assignmentId"), student = requiredPositiveId(request, "studentUserId");
		TeacherPreviewHttp.dispatch(request, response, poll, (action, id, cursor, value) -> switch (action) {
			case "preview-start" -> PROGRESS.startPreview(user, assignment, student, value);
			case "preview-session" -> PROGRESS.pollPreview(user, assignment, student, id, cursor);
			case "preview-cancel" -> PROGRESS.cancelPreview(user, assignment, student, id, cursor);
			default -> {
				PROGRESS.sendPreviewInput(user, assignment, student, id, value);
				yield java.util.Map.of("accepted", true);
			}
		});
	}

	private static AuthenticatedUser authenticatedTeacher(HttpServletRequest request) {
		Object user = request.getAttribute("authenticatedUser");
		if (!(user instanceof AuthenticatedUser authenticatedUser)
				|| authenticatedUser.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication is required.");
		}
		return authenticatedUser;
	}

	private void render(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user)
			throws SQLException, ServletException, IOException {
		List<TeacherProgressRow> rows = PROGRESS.loadRows(user);
		request.setAttribute("teacherProgressRows", rows);
		request.setAttribute("teacherNavigationSummary", NAVIGATION.load(user));
		request.setAttribute("teacherNavigationActiveItem", "progress");
		request.setAttribute("teacherId", user.loginId());
		request.setAttribute("screenPageTitle", "課題進捗確認機能");
		request.setAttribute("screenDesign", "teacher");
		request.setAttribute("screenUsesCodeMirror", Boolean.TRUE);
		request.setAttribute("screenUsesCodeEditor", Boolean.TRUE);
		request.setAttribute("csrfToken", servlet.auth.CsrfTokens.getOrCreate(request.getSession(false)));
		request.setAttribute("screenStylesheet", "/css/teacher/progress/progress.css");
		request.setAttribute("screenAssetVersion", "163");
		request.setAttribute("screenScript", "/js/teacher/progress/progress.js");
		request.getRequestDispatcher("/WEB-INF/teacher/progress/progress.jsp").forward(request, response);
	}

	private void writeDetail(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user)
			throws IOException, SQLException {
		long assignmentId = requiredPositiveId(request, "assignmentId");
		long studentUserId = requiredPositiveId(request, "studentUserId");
		response.setContentType("application/json; charset=UTF-8");
		JSON.toJson(PROGRESS.loadDetail(user, assignmentId, studentUserId), response.getWriter());
	}

	private static long requiredPositiveId(HttpServletRequest request, String parameterName) {
		String value = TeacherExerciseServlet.single(request, parameterName);
		if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
			throw new IllegalArgumentException("課題または生徒IDが不正です。");
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("課題または生徒IDが不正です。", failure);
		}
	}

	private void writeCodeDownload(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user)
			throws IOException, SQLException {
		long assignmentId = requiredPositiveId(request, "assignmentId");
		long studentUserId = requiredPositiveId(request, "studentUserId");
		TeacherProgressDetail detail = PROGRESS.loadDetail(user, assignmentId, studentUserId);
		if (detail.latestCode() == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "保存済み・提出済みのコードはありません。");
			return;
		}
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Content-Disposition", "attachment; filename=\"student-" + studentUserId
				+ "_assignment-" + assignmentId + "_latest_code.py\"");
		response.setContentType("text/plain; charset=UTF-8");
		response.getWriter().write(detail.latestCode().code());
	}
}
