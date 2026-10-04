package servlet.student;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import control.auth.AuthenticatedUser;
import control.student.StudentControl;
import control.student.StudentExerciseControl;
import control.student.PythonRunnerRequestException;
import entity.ExerciseConflictException;
import entity.ExerciseNotFoundException;
import entity.ExerciseSaveResult;
import entity.PythonExecutionInput;
import entity.StudentExerciseEntry;
import entity.StudentExerciseInput;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet(urlPatterns = {"/student/exercise", "/student/exercise/create",
		"/student/exercise/upload", "/student/exercise/upload-preview", "/student/exercise/tree",
		"/student/exercise/save", "/student/exercise/trash", "/student/exercise/restore",
		"/student/exercise/rename", "/student/exercise/move",
		"/student/exercise/batch-move", "/student/exercise/batch-trash",
		"/student/exercise/batch-restore", "/student/exercise/duplicate-preview",
		"/student/exercise/duplicate",
		"/student/exercise/unification", "/student/exercise/unify",
		"/student/exercise/run", "/student/exercise/session",
		"/student/exercise/session/input", "/student/exercise/session/cancel", "/student/exercise/download"})
public final class StudentExerciseServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Gson GSON = new Gson();
	private static final StudentExerciseControl EXERCISES = new StudentExerciseControl();
	private static final StudentControl STUDENTS = new StudentControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		if ("/student/exercise/unification".equals(request.getServletPath())) {
			response.setContentType("application/json; charset=UTF-8");
			try {
				json(response, 200, EXERCISES.previewUnification(requireStudent(request)));
			} catch (ExerciseNotFoundException e) {
				error(response, 404, "not_found", "統合対象が見つかりません。");
			} catch (ExerciseConflictException e) {
				error(response, 409, "exercise_conflict", e.getMessage());
			} catch (IllegalArgumentException e) {
				error(response, 400, "invalid_request", e.getMessage());
			} catch (SecurityException e) {
				error(response, 403, "forbidden", "統合内容を取得する権限がありません。");
			} catch (SQLException e) {
				getServletContext().log("Exercise unification preview could not be loaded.", e);
				error(response, 503, "storage_unavailable", "統合内容を取得できませんでした。元のデータは残しています。");
			}
			return;
		}
		if ("/student/exercise/tree".equals(request.getServletPath())) {
			response.setContentType("application/json; charset=UTF-8");
			try {
				json(response, 200, entity.ExerciseTree.from(EXERCISES.loadPage(
						requireStudent(request), optionalId(request, "exerciseId"), optionalId(request, "entryId"))));
			} catch (ExerciseNotFoundException e) {
				error(response, 404, "not_found", "この演習領域は利用できません。");
			} catch (IllegalArgumentException e) {
				error(response, 400, "invalid_request", e.getMessage());
			} catch (SecurityException e) {
				error(response, 403, "forbidden", "この演習領域を取得する権限がありません。");
			} catch (SQLException e) {
				getServletContext().log("Exercise tree could not be loaded.", e);
				error(response, 503, "storage_unavailable", "最新の項目を取得できませんでした。編集中の内容は残しています。");
			}
			return;
		}
		if ("/student/exercise/duplicate-preview".equals(request.getServletPath())) {
			response.setContentType("application/json; charset=UTF-8");
			try {
				json(response, 200, EXERCISES.duplicatePreview(requireStudent(request), id(request, "exerciseId"),
						ExerciseBatchForm.csvIds(request.getParameter("entryIds"))));
			} catch (ExerciseNotFoundException e) {
				error(response, 404, "not_found", "複製対象が見つかりません。");
			} catch (IllegalArgumentException e) {
				error(response, 400, "invalid_request", e.getMessage());
			} catch (SecurityException e) {
				error(response, 403, "forbidden", "複製内容を取得する権限がありません。");
			} catch (SQLException e) {
				getServletContext().log("Exercise duplicate preview could not be loaded.", e);
				error(response, 503, "storage_unavailable", "複製候補を取得できませんでした。");
			}
			return;
		}
		if ("/student/exercise/download".equals(request.getServletPath())) {
			download(request, response);
			return;
		}
		if ("/student/exercise/session".equals(request.getServletPath())) {
			response.setContentType("application/json; charset=UTF-8");
			try {
				json(response, 200, EXERCISES.pollInteractiveExecution(requireStudent(request),
						id(request, "exerciseId"), id(request, "entryId"), request.getParameter("sessionId"),
						ExerciseForm.number(request.getParameter("after"), false)));
			} catch (ExerciseNotFoundException e) {
				error(response, 404, "not_found", "この演習ファイルは利用できません。");
			} catch (IllegalArgumentException e) {
				error(response, 400, "invalid_request", e.getMessage());
			} catch (SecurityException e) {
				error(response, 403, "forbidden", "この実行セッションを利用する権限がありません。");
			} catch (SQLException e) {
				getServletContext().log("Exercise execution result could not be stored.", e);
				error(response, 503, "storage_unavailable", "実行履歴を保存できませんでした。");
			} catch (PythonRunnerRequestException e) {
				runnerError(response, e);
			} catch (IOException | InterruptedException e) {
				if (e instanceof InterruptedException) Thread.currentThread().interrupt();
				getServletContext().log("Exercise execution could not be polled.", e);
				error(response, 503, "execution_unavailable", "実行状態を取得できませんでした。");
			}
			return;
		}
		if (!"/student/exercise".equals(request.getServletPath())) {
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return;
		}
		try {
			AuthenticatedUser user = requireStudent(request);
			var page = EXERCISES.loadPage(user, optionalId(request, "exerciseId"),
					optionalId(request, "entryId"));
			request.setAttribute("exercisePage", page);
			request.setAttribute("exerciseRootName", user.loginId());
			request.setAttribute("editorPreferences", page.preferences());
			request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
			var home = STUDENTS.loadHome(user).orElse(null);
			request.setAttribute("taskCount", home == null ? 0 : home.getTasks().size());
			request.getRequestDispatcher("/WEB-INF/student/exercise/exercise.jsp").forward(request, response);
		} catch (ExerciseNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Student exercise data could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setCharacterEncoding("UTF-8");
		response.setContentType("application/json; charset=UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		try {
			AuthenticatedUser user = requireStudent(request);
			int limit = "/student/exercise/upload".equals(request.getServletPath())
					|| "/student/exercise/upload-preview".equals(request.getServletPath())
					? ExerciseUploadForm.MAX_BYTES : ExerciseForm.MAX_BYTES;
			if (request.getContentLengthLong() > limit) throw new ExerciseForm.TooLargeException();
			String contentType = request.getContentType();
			if (contentType == null || !contentType.split(";", 2)[0].trim()
					.equalsIgnoreCase("application/x-www-form-urlencoded")) {
				throw new IllegalArgumentException("フォーム形式で送信してください。");
			}
			Map<String, String> form = ExerciseForm.read(request.getInputStream(), limit);
			HttpServletRequest parsed = new HttpServletRequestWrapper(request) {
				@Override public String getParameter(String name) { return form.get(name); }
			};
			if (!CsrfTokens.isValid(parsed)) {
				error(response, 403, "csrf_invalid", "画面を再読み込みしてから、もう一度お試しください。");
				return;
			}
			String operation = request.getServletPath();
			if ("/student/exercise/unify".equals(operation)) {
				var unified = EXERCISES.unify(user, form.get("token"), id(parsed, "exerciseId"), version(parsed));
				json(response, 200, Map.of("status", "unified", "exerciseId", unified.exerciseId(),
						"version", unified.version()));
			} else if ("/student/exercise/create".equals(operation)) {
				var saved = EXERCISES.create(user, optionalId(parsed, "exerciseId"),
						optionalId(parsed, "parentEntryId"), version(parsed),
						new StudentExerciseInput(StudentExerciseEntry.Type.fromValue(form.get("type")), form.get("name")));
				success(response, 201, "created", saved);
			} else if ("/student/exercise/upload-preview".equals(operation)) {
				json(response, 200, EXERCISES.previewUpload(user, nullableId(parsed, "exerciseId"),
						nullableId(parsed, "parentEntryId"), version(parsed), ExerciseUploadForm.read(form.get("files"))));
			} else if ("/student/exercise/upload".equals(operation)) {
				json(response, 200, EXERCISES.upload(user, nullableId(parsed, "exerciseId"),
						nullableId(parsed, "parentEntryId"), version(parsed), ExerciseUploadForm.read(form.get("files")),
						ExerciseBatchForm.uploadResolutions(form.get("resolutions"))));
			} else if ("/student/exercise/duplicate-preview".equals(operation)) {
				var preview = EXERCISES.duplicatePreview(user, id(parsed, "exerciseId"),
						java.util.List.of(id(parsed, "entryId")), version(parsed));
				json(response, 200, preview);
			} else if ("/student/exercise/batch-move".equals(operation)
					|| "/student/exercise/batch-trash".equals(operation)
					|| "/student/exercise/batch-restore".equals(operation)
					|| "/student/exercise/duplicate".equals(operation)) {
				var batch = ExerciseBatchForm.read(form, "/student/exercise/batch-restore".equals(operation));
				if ("/student/exercise/batch-move".equals(operation) && !form.containsKey("parentEntryId")) {
					throw new IllegalArgumentException("移動先を指定してください。");
				}
				var result = switch (operation) {
					case "/student/exercise/batch-move" ->
						EXERCISES.batchMove(user, id(parsed, "exerciseId"), batch);
					case "/student/exercise/batch-trash" ->
						EXERCISES.batchTrash(user, id(parsed, "exerciseId"), batch);
					case "/student/exercise/batch-restore" ->
						EXERCISES.batchRestore(user, id(parsed, "exerciseId"), batch);
					default -> EXERCISES.duplicate(user, id(parsed, "exerciseId"), batch);
				};
				json(response, 200, result);
			} else if ("/student/exercise/run".equals(operation)) {
				json(response, 200, EXERCISES.startInteractiveExecution(user, id(parsed, "exerciseId"),
						id(parsed, "entryId"), form.get("code")));
			} else if ("/student/exercise/session/input".equals(operation)) {
				EXERCISES.sendInteractiveInput(user, id(parsed, "exerciseId"), id(parsed, "entryId"),
						form.get("sessionId"), form.get("line"));
				json(response, 200, Map.of("status", "accepted"));
			} else if ("/student/exercise/session/cancel".equals(operation)) {
				EXERCISES.cancelInteractiveExecution(user, id(parsed, "exerciseId"), id(parsed, "entryId"),
						form.get("sessionId"));
				json(response, 200, Map.of("status", "cancel_requested"));
			} else {
				ExerciseSaveResult saved;
				String status;
				switch (operation) {
					case "/student/exercise/save" -> {
						saved = EXERCISES.save(user, id(parsed, "exerciseId"), id(parsed, "entryId"),
								version(parsed), form.get("code"));
						status = "saved";
					}
					case "/student/exercise/trash" -> {
						saved = EXERCISES.trash(user, id(parsed, "exerciseId"), id(parsed, "entryId"), version(parsed));
						status = "trashed";
					}
					case "/student/exercise/restore" -> {
						saved = EXERCISES.restore(user, id(parsed, "exerciseId"), id(parsed, "entryId"), version(parsed),
								form.containsKey("parentEntryId"), !form.containsKey("parentEntryId")
										|| form.get("parentEntryId").isEmpty() ? null : optionalId(parsed, "parentEntryId"),
								form.get("name"));
						status = "restored";
					}
					case "/student/exercise/rename" -> {
						saved = EXERCISES.rename(user, id(parsed, "exerciseId"), id(parsed, "entryId"),
								version(parsed), form.get("name"));
						status = "renamed";
					}
					case "/student/exercise/move" -> {
						if (!form.containsKey("parentEntryId")) {
							throw new IllegalArgumentException("移動先を指定してください。");
						}
						saved = EXERCISES.move(user, id(parsed, "exerciseId"), id(parsed, "entryId"),
								version(parsed), form.get("parentEntryId").isEmpty() ? null
										: optionalId(parsed, "parentEntryId"), form.get("name"));
						status = "moved";
					}
					default -> {
						error(response, 404, "not_found", "要求された操作が見つかりません。");
						return;
					}
				}
				success(response, 200, status, saved);
			}
		} catch (ExerciseForm.TooLargeException | PythonExecutionInput.TooLargeException e) {
			error(response, 413, "request_too_large", e.getMessage());
		} catch (ExerciseNotFoundException e) {
			error(response, 404, "not_found", "この演習領域または項目は利用できません。");
		} catch (ExerciseConflictException e) {
			error(response, 409, e.reason() == ExerciseConflictException.Reason.NAME
					? "name_conflict" : "exercise_conflict", e.getMessage());
		} catch (IllegalArgumentException e) {
			error(response, 400, "invalid_request", e.getMessage());
		} catch (SecurityException e) {
			error(response, 403, "forbidden", "この操作を実行する権限がありません。");
		} catch (SQLException e) {
			getServletContext().log("Student exercise operation could not be stored.", e);
			error(response, 503, "storage_unavailable", "データを保存できませんでした。入力を残して、時間をおいて再度お試しください。");
		} catch (PythonRunnerRequestException e) {
			runnerError(response, e);
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) Thread.currentThread().interrupt();
			getServletContext().log("Student exercise request or Python execution could not be completed.", e);
			error(response, 503, "execution_unavailable", "処理を完了できませんでした。入力を残して、時間をおいて再度お試しください。");
		}
	}

	private void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("application/json; charset=UTF-8");
		try {
			long exerciseId = id(request, "exerciseId");
			var user = requireStudent(request);
			String selected = request.getParameter("entryIds");
			var download = EXERCISES.loadDownload(user, exerciseId,
					selected == null ? null : ExerciseBatchForm.csvIds(selected));
			if (download.entries().isEmpty()) {
				error(response, 400, "download_empty", "ダウンロード対象のフォルダ・ファイルがありません。");
				return;
			}
			response.setContentType(download.contentType());
			response.setHeader("X-Content-Type-Options", "nosniff");
			response.setHeader("Content-Disposition", download.contentDisposition());
			download.write(response.getOutputStream());
		} catch (ExerciseNotFoundException e) {
			error(response, 404, "not_found", "この演習領域は利用できません。");
		} catch (IllegalArgumentException e) {
			error(response, 400, "invalid_request", e.getMessage());
		} catch (SecurityException e) {
			error(response, 403, "forbidden", "このダウンロードを行う権限がありません。");
		} catch (SQLException e) {
			getServletContext().log("Exercise download could not be loaded.", e);
			error(response, 503, "storage_unavailable", "保存済みファイルを取得できませんでした。");
		} catch (IOException e) {
			getServletContext().log("Exercise ZIP could not be sent.", e);
			throw e;
		}
	}

	private void runnerError(HttpServletResponse response, PythonRunnerRequestException e) throws IOException {
		getServletContext().log("Exercise runner request was rejected.", e);
		int status = switch (e.errorCode()) {
			case "input_too_large" -> 413;
			case "execution_not_running", "execution_not_waiting" -> 409;
			case "session_not_found" -> 404;
			default -> 503;
		};
		error(response, status, e.errorCode(), switch (status) {
			case 413 -> "この実行の入力合計が8 KiBを超えています。入力は残しています。";
			case 409 -> "この実行には入力を送信できません。入力は残しています。";
			case 404 -> "実行セッションは利用できません。再読み込みして保存済み結果を確認してください。";
			default -> "実行サービスでエラーが発生しました。再読み込みして保存済み履歴を確認してください。";
		});
	}

	private static AuthenticatedUser requireStudent(HttpServletRequest request) {
		var user = StudentHomeServlet.authenticatedUser(request);
		if (user == null || user.userType() != UserType.STUDENT || user.passwordChangeRequired()) {
			throw new SecurityException("Student authentication is required.");
		}
		return user;
	}

	private static Long optionalId(HttpServletRequest request, String name) {
		String value = request.getParameter(name);
		return value == null ? null : ExerciseForm.number(value, true);
	}

	private static Long nullableId(HttpServletRequest request, String name) {
		String value = request.getParameter(name);
		return value == null || value.isEmpty() ? null : ExerciseForm.number(value, true);
	}

	private static long id(HttpServletRequest request, String name) {
		return ExerciseForm.number(request.getParameter(name), true);
	}

	private static long version(HttpServletRequest request) {
		return StudentExerciseInput.requireVersion(ExerciseForm.number(request.getParameter("expectedVersion"), false));
	}

	private static void success(HttpServletResponse response, int code, String status, ExerciseSaveResult saved)
			throws IOException {
		json(response, code, Map.of("status", status, "exerciseId", saved.exerciseId(),
				"entryId", saved.entryId(), "version", saved.version()));
	}

	private static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
		json(response, status, Map.of("errorCode", code, "message", message));
	}

	private static void json(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.getWriter().write(GSON.toJson(body));
	}
}
