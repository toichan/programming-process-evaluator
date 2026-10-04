package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import control.auth.AuthenticatedUser;
import control.teacher.TaskDraftConflictException;
import control.teacher.TaskDraftNotFoundException;
import control.teacher.TeacherTaskControl;
import control.teacher.TeacherTaskCreateRegistry;
import entity.TeacherNavigationSummary;
import entity.TeacherTaskInput;
import entity.UserCredential.UserType;
import servlet.auth.CsrfTokens;

@WebServlet("/teacher/task")
public final class TeacherTaskServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAX_REQUEST_BYTES = 4 * 1024 * 1024;
	private static final String CREATE_REGISTRY_ATTRIBUTE = TeacherTaskServlet.class.getName() + ".createRegistry";
	static final String SAVED_NOTICE_ATTRIBUTE = TeacherTaskServlet.class.getName() + ".savedNotice";
	private static final TeacherTaskControl TASKS = new TeacherTaskControl();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		try {
			AuthenticatedUser user = authenticatedUser(request);
			render(request, response, user, optionalId(request, "taskId"), optionalId(request, "schoolId"), null);
		} catch (TaskDraftNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Teacher task page could not be loaded.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Pragma", "no-cache");
		AuthenticatedUser user;
		try {
			user = authenticatedUser(request);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		TeacherTaskForm form = null;
		Map<String, List<String>> rawValues = Map.of();
		try {
			if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
				throw new TeacherTaskForm.RequestTooLargeException();
			}
			String contentType = request.getContentType();
			if (contentType == null || !contentType.split(";", 2)[0].trim()
					.equalsIgnoreCase("application/x-www-form-urlencoded")) {
				throw new IllegalArgumentException("フォーム形式で送信してください。");
			}
			rawValues = TeacherTaskForm.read(request.getInputStream(), MAX_REQUEST_BYTES);
			request.setAttribute("submittedTeacherTaskValues", rawValues);
			form = TeacherTaskForm.parse(rawValues);
			request.setAttribute("teacherTaskForm", form);
			HttpServletRequest parsedRequest = csrfRequest(request, form.csrfToken());
			if (!CsrfTokens.isValid(parsedRequest)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "画面を再読み込みしてください。");
				return;
			}

			if ("createDraft".equals(form.action())) {
				TeacherTaskCreateRegistry registry = existingRegistry(request.getSession(false));
				TeacherTaskForm submittedForm = form;
				long taskId = registry.createOrReuse(submittedForm.requestToken(),
						() -> TASKS.createDraft(user, submittedForm.input(), submittedForm.requestToken()));
				redirectSaved(response, request, taskId);
			} else {
				TASKS.updateDraft(user, form.taskId(), form.expectedVersion(), form.input(), form.requestToken());
				redirectSaved(response, request, form.taskId());
			}
		} catch (TeacherTaskForm.RequestTooLargeException e) {
			response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
		} catch (TaskDraftConflictException e) {
			renderError(request, response, user, form, rawValues, HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (TaskDraftNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (IllegalArgumentException e) {
			renderError(request, response, user, form, rawValues, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Teacher task save failed.", e);
			renderError(request, response, user, form, rawValues, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"課題を保存できませんでした。入力は保持しています。");
		}
	}

	private void renderError(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			TeacherTaskForm form,
			Map<String, List<String>> rawValues,
			int status,
			String message) throws ServletException, IOException {
		request.setAttribute("teacherTaskError", message);
		request.setAttribute("submittedTeacherTaskValues", rawValues);
		request.setAttribute("teacherTaskForm", form);
		long rawTaskId = safePositiveLong(first(rawValues, "taskId"), 0);
		Long selectedTaskId = null;
		if (form != null && form.taskId() > 0) {
			selectedTaskId = form.taskId();
		} else if (rawTaskId > 0) {
			selectedTaskId = rawTaskId;
		}
		response.setStatus(status);
		try {
			render(request, response, user, selectedTaskId, null, form);
		} catch (TaskDraftNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException | RuntimeException renderFailure) {
			getServletContext().log("Teacher task form could not be reloaded after a save failure.", renderFailure);
			if (!response.isCommitted()) {
				response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			}
		}
	}

	private void render(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			Long selectedTaskId,
			Long selectedSchoolId,
			TeacherTaskForm submittedForm) throws SQLException, ServletException, IOException {
		var page = TASKS.loadPage(user, selectedTaskId, selectedSchoolId);
		request.setAttribute("teacherTaskPage", page);
		request.setAttribute("teacherNavigationSummary", new TeacherNavigationSummary(true, page.schools()));
		request.setAttribute("teacherNavigationActiveItem", "task");
		request.setAttribute("teacherTaskSaved", consumeSavedNotice(request.getSession(false)));
		request.setAttribute("teacherTaskDraftCount", page.tasks().size());
		request.setAttribute("teacherTaskSchoolCount", page.schools().size());
		request.setAttribute("teacherTaskReusableHintCount", page.reusableHints().size());
		request.setAttribute("teacherTaskUser", user);
		request.setAttribute("displayName", user.displayName());
		request.setAttribute("teacherId", user.loginId());
		request.setAttribute("csrfToken", CsrfTokens.getOrCreate(request.getSession(false)));
		request.setAttribute("screenDesign", "teacher");
		request.setAttribute("screenPageTitle", "課題編集");
		request.setAttribute("screenStylesheet", "/css/teacher/task/task.css");
		request.setAttribute("screenScript", "/js/teacher/task/task.js");
		request.setAttribute("screenUsesCodeMirror", Boolean.TRUE);
		Map<String, List<String>> rawValues = rawValues(request);
		boolean hasSubmittedValues = !rawValues.isEmpty();
		var selectedTask = page.selectedTask();
		var input = submittedForm != null
				? submittedForm.input()
				: hasSubmittedValues || selectedTask == null ? null : selectedTask.input();
		long taskId = submittedForm != null
				? submittedForm.taskId()
				: selectedTask == null ? safePositiveLong(first(rawValues, "taskId"), 0) : selectedTask.taskId();
		long version = submittedForm != null
				? submittedForm.expectedVersion()
				: selectedTask == null ? safePositiveLong(first(rawValues, "expectedVersion"), 0) : selectedTask.version();
		Set<Long> selectedClassIds = new HashSet<>();
		Set<Long> selectedSchoolIds = new HashSet<>();
		Map<Long, Map<String, String>> assignments = new HashMap<>();
		if (submittedForm != null) {
			selectedSchoolIds.addAll(submittedForm.selectedSchoolIds());
			for (var assignment : input.classAssignments()) {
				selectedClassIds.add(assignment.classroomId());
				assignments.put(assignment.classroomId(), assignmentValues(assignment));
			}
		} else if (hasSubmittedValues) {
			selectedSchoolIds.addAll(safePositiveIds(rawValues.get("schoolTargets")));
			selectedClassIds.addAll(safePositiveIds(rawValues.get("classTargets")));
			List<String> classroomIds = rawValues.getOrDefault("classTargets", List.of());
			List<String> assignmentIds = rawValues.getOrDefault("assignmentIds", List.of());
			List<String> publishAtValues = rawValues.getOrDefault("publishAts", List.of());
			List<String> dueAtValues = rawValues.getOrDefault("dueAts", List.of());
			for (int index = 0; index < classroomIds.size(); index++) {
				long classroomId = safePositiveLong(classroomIds.get(index), 0);
				if (classroomId > 0) {
					long assignmentId = index < assignmentIds.size()
							? safePositiveLong(assignmentIds.get(index), 0)
							: 0;
					assignments.put(classroomId, Map.of(
							"assignmentId", Long.toString(assignmentId),
							"publishAt", index < publishAtValues.size() ? publishAtValues.get(index) : "",
							"dueAt", index < dueAtValues.size() ? dueAtValues.get(index) : ""));
				}
			}
		} else if (input != null) {
			for (var assignment : input.classAssignments()) {
				selectedClassIds.add(assignment.classroomId());
				assignments.put(assignment.classroomId(), assignmentValues(assignment));
			}
		}
		for (var classroom : page.classes()) {
			if (selectedClassIds.contains(classroom.classroomId())) {
				selectedSchoolIds.add(classroom.schoolId());
			}
		}
		request.setAttribute("teacherTaskInput", input);
		request.setAttribute("teacherTaskId", taskId);
		request.setAttribute("teacherTaskExpectedVersion", version);
		request.setAttribute("teacherTaskAction", taskId > 0 ? "updateDraft" : "createDraft");
		request.setAttribute("teacherTaskSelectedClassIds", selectedClassIds);
		request.setAttribute("teacherTaskSelectedSchoolIds", selectedSchoolIds);
		request.setAttribute("teacherTaskAssignments", assignments);
		request.setAttribute("teacherTaskFieldValues", fieldValues(input, rawValues));
		TeacherTaskCreateRegistry registry = registry(request.getSession(false), true);
		String createToken = submittedForm != null && "createDraft".equals(submittedForm.action())
				? submittedForm.requestToken()
				: registry.issueToken();
		request.setAttribute("teacherTaskCreateToken", createToken);
		request.getRequestDispatcher("/WEB-INF/teacher/task/task.jsp").forward(request, response);
	}

	private static Map<String, List<String>> rawValues(HttpServletRequest request) {
		Object values = request.getAttribute("submittedTeacherTaskValues");
		if (values == null) {
			return Map.of();
		}
		if (!(values instanceof Map<?, ?> map)) {
			throw new IllegalStateException("Submitted teacher task values have an invalid type.");
		}
		Map<String, List<String>> copied = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof List<?> list)) {
				throw new IllegalStateException("Submitted teacher task values have an invalid shape.");
			}
			List<String> strings = new java.util.ArrayList<>(list.size());
			for (Object value : list) {
				if (!(value instanceof String string)) {
					throw new IllegalStateException("Submitted teacher task value is not text.");
				}
				strings.add(string);
			}
			copied.put(name, List.copyOf(strings));
		}
		return Map.copyOf(copied);
	}

	private static Map<String, String> assignmentValues(TeacherTaskInput.ClassAssignmentInput assignment) {
		return Map.of(
				"assignmentId", Long.toString(assignment.assignmentId()),
				"publishAt", assignment.publishAt() == null ? "" : assignment.publishAt().toString(),
				"dueAt", assignment.dueAt() == null ? "" : assignment.dueAt().toString());
	}

	private static Map<String, String> fieldValues(
			TeacherTaskInput input,
			Map<String, List<String>> rawValues) {
		Map<String, String> values = new LinkedHashMap<>();
		if (input != null) {
			values.put("taskName", input.title());
			values.put("theme", input.theme());
			values.put("difficulty", input.difficulty() == null ? "" : input.difficulty().databaseValue());
			values.put("description", input.description());
			values.put("features", String.join(";", input.features()));
			values.put("inputConstraints", input.inputConstraints());
			values.put("creationRules", input.creationRules());
			values.put("initialCode", input.initialCode());
			values.put("lateSubmissionPolicy", input.classAssignments().isEmpty()
					? "allow"
					: input.classAssignments().get(0).lateSubmissionPolicy().databaseValue());
		}
		for (String name : List.of(
				"taskName", "theme", "difficulty", "description", "features",
				"inputConstraints", "creationRules", "initialCode", "lateSubmissionPolicy")) {
			List<String> submitted = rawValues.get(name);
			if (submitted != null && !submitted.isEmpty()) {
				values.put(name, submitted.get(0));
			}
		}
		return values;
	}

	private static String first(Map<String, List<String>> values, String name) {
		List<String> entries = values.get(name);
		return entries == null || entries.isEmpty() ? null : entries.get(0);
	}

	private static Set<Long> safePositiveIds(List<String> values) {
		Set<Long> ids = new HashSet<>();
		if (values != null) {
			for (String value : values) {
				long id = safePositiveLong(value, 0);
				if (id > 0) {
					ids.add(id);
				}
			}
		}
		return ids;
	}

	private static long safePositiveLong(String value, long fallback) {
		if (value == null || !value.matches("[0-9]+")) {
			return fallback;
		}
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static HttpServletRequest csrfRequest(HttpServletRequest request, String csrfToken) {
		return new HttpServletRequestWrapper(request) {
			@Override
			public String getParameter(String name) {
				return "csrfToken".equals(name) ? csrfToken : super.getParameter(name);
			}
		};
	}

	private void redirectSaved(HttpServletResponse response, HttpServletRequest request, long taskId)
			throws IOException {
		HttpSession session = request.getSession(false);
		if (session == null) {
			getServletContext().log("Task draft was saved, but its session success notice could not be stored.");
		} else {
			session.setAttribute(SAVED_NOTICE_ATTRIBUTE, Boolean.TRUE);
		}
		response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/teacher/task?taskId=" + taskId));
	}

	static boolean consumeSavedNotice(HttpSession session) {
		if (session == null) {
			return false;
		}
		Object notice = session.getAttribute(SAVED_NOTICE_ATTRIBUTE);
		if (notice == null) {
			return false;
		}
		session.removeAttribute(SAVED_NOTICE_ATTRIBUTE);
		if (!(notice instanceof Boolean saved) || !saved) {
			throw new IllegalStateException("Task draft success notice has an invalid session value.");
		}
		return true;
	}

	private static Long optionalId(HttpServletRequest request, String name) {
		String value = request.getParameter(name);
		if (value == null) {
			return null;
		}
		if (!value.matches("[0-9]+")) {
			throw new IllegalArgumentException("課題または学校IDが不正です。");
		}
		try {
			long id = Long.parseLong(value);
			if (id < 1) {
				throw new NumberFormatException();
			}
			return id;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("課題または学校IDが不正です。", e);
		}
	}

	static AuthenticatedUser authenticatedUser(HttpServletRequest request) {
		Object user = request.getAttribute("authenticatedUser");
		if (!(user instanceof AuthenticatedUser authenticatedUser)
				|| authenticatedUser.userType() != UserType.TEACHER) {
			throw new SecurityException("Teacher authentication is required.");
		}
		return authenticatedUser;
	}

	private static TeacherTaskCreateRegistry existingRegistry(HttpSession session) {
		TeacherTaskCreateRegistry registry = registry(session, false);
		if (registry == null) {
			throw new SecurityException("A task creation form token is not available in this session.");
		}
		return registry;
	}

	private static TeacherTaskCreateRegistry registry(HttpSession session, boolean create) {
		if (session == null) {
			throw new SecurityException("An authenticated session is required.");
		}
		synchronized (session) {
			Object existing = session.getAttribute(CREATE_REGISTRY_ATTRIBUTE);
			if (existing instanceof TeacherTaskCreateRegistry registry) {
				return registry;
			}
			if (!create) {
				return null;
			}
			TeacherTaskCreateRegistry registry = new TeacherTaskCreateRegistry();
			session.setAttribute(CREATE_REGISTRY_ATTRIBUTE, registry);
			return registry;
		}
	}
}
