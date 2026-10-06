package servlet.teacher;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import entity.TeacherClassOption;
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
	private static final String TASK_STATE_NOTICE_ATTRIBUTE = TeacherTaskServlet.class.getName() + ".taskStateNotice";
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
			if (isPublishedAssignmentAction(first(rawValues, "action"))) {
				processPublishedAssignmentOperation(request, response, user, rawValues);
				return;
			}
			if (isTaskStateAction(first(rawValues, "action"))) {
				processTaskStateOperation(request, response, user, rawValues);
				return;
			}
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
			} else if ("createRevision".equals(form.action())) {
				long taskId = TASKS.createRevision(
						user, form.taskId(), form.expectedVersion(), form.input(), form.requestToken());
				redirectSaved(response, request, taskId);
			} else if ("publishTask".equals(form.action())) {
				TeacherTaskForm submittedForm = form;
				if (submittedForm.taskId() == 0) {
					TeacherTaskCreateRegistry registry = existingRegistry(request.getSession(false));
					registry.createOrReuse(submittedForm.requestToken(),
							() -> TASKS.publishTask(
									user, 0, 0, submittedForm.input(), submittedForm.requestToken()));
				} else {
					TASKS.publishTask(
							user,
							submittedForm.taskId(),
							submittedForm.expectedVersion(),
							submittedForm.input(),
							submittedForm.requestToken());
				}
				redirectPublished(response, request);
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
		boolean historyView = "history".equals(request.getParameter("view"));
		var page = TASKS.loadPage(user, historyView ? null : selectedTaskId, selectedSchoolId);
		if (historyView && selectedTaskId != null) {
			request.setAttribute("teacherTaskAuditEntries", TASKS.loadAuditEntries(user, selectedTaskId));
			String historyTitle = page.tasks().stream()
					.filter(task -> task.taskId() == selectedTaskId)
					.map(task -> task.input().title())
					.findFirst()
					.orElseThrow(TaskDraftNotFoundException::new);
			request.setAttribute("teacherTaskHistoryTitle", historyTitle);
		}
		request.setAttribute("teacherTaskPage", page);
		request.setAttribute("teacherNavigationSummary", new TeacherNavigationSummary(true, page.schools()));
		request.setAttribute("teacherNavigationActiveItem", "task");
		request.setAttribute("teacherTaskSaved", consumeSavedNotice(request.getSession(false)));
		request.setAttribute("teacherTaskStateNotice", consumeTaskStateNotice(request.getSession(false)));
		request.setAttribute("teacherTaskDraftCount", page.tasks().stream()
				.filter(task -> "draft".equals(task.publicationStatus())).count());
		request.setAttribute("teacherTaskPublishedCount", page.tasks().stream()
				.filter(task -> "published".equals(task.publicationStatus())).count());
		request.setAttribute("teacherTaskRequiresUpdateCount", page.tasks().stream()
				.filter(task -> "requires_update".equals(task.publicationStatus())).count());
		Map<Long, String> deleteTokens = new HashMap<>();
		Map<Long, String> independentCopyTokens = new HashMap<>();
		for (var task : page.tasks()) {
			deleteTokens.put(task.taskId(), UUID.randomUUID().toString());
			independentCopyTokens.put(task.taskId(), UUID.randomUUID().toString());
		}
		Map<Long, String> restoreTokens = new HashMap<>();
		for (var task : page.deletedTasks()) {
			restoreTokens.put(task.taskId(), UUID.randomUUID().toString());
		}
		Map<Long, String> assignmentDeadlineTokens = new HashMap<>();
		Map<Long, String> classAssignmentTokens = new HashMap<>();
		Map<Long, String> assignmentDeadlineValues = new HashMap<>();
		Map<Long, List<TeacherClassOption>> eligibleClassOptions = new HashMap<>();
		for (var task : page.tasks()) {
			if ("published".equals(task.publicationStatus())) {
				classAssignmentTokens.put(task.taskId(), UUID.randomUUID().toString());
				Set<Long> assignedClassIds = new HashSet<>(
						page.assignmentClassHistory().getOrDefault(task.taskId(), Set.of()));
				for (var assignment : task.input().classAssignments()) {
					assignedClassIds.add(assignment.classroomId());
					assignmentDeadlineTokens.put(assignment.assignmentId(), UUID.randomUUID().toString());
					if (assignment.dueAt() != null) {
						assignmentDeadlineValues.put(assignment.assignmentId(),
								assignment.dueAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
					}
				}
				eligibleClassOptions.put(task.taskId(), page.classes().stream()
						.filter(classroom -> classroom.schoolId() == task.input().schoolId())
						.filter(classroom -> !assignedClassIds.contains(classroom.classroomId()))
						.toList());
			}
		}
		request.setAttribute("teacherTaskDeleteTokens", deleteTokens);
		request.setAttribute("teacherTaskIndependentCopyTokens", independentCopyTokens);
		request.setAttribute("teacherTaskRestoreTokens", restoreTokens);
		request.setAttribute("teacherTaskDeadlineTokens", assignmentDeadlineTokens);
		request.setAttribute("teacherTaskClassAssignmentTokens", classAssignmentTokens);
		request.setAttribute("teacherTaskDeadlineValues", assignmentDeadlineValues);
		request.setAttribute("teacherTaskEligibleClassOptions", eligibleClassOptions);
		request.setAttribute("teacherTaskUser", user);
		request.setAttribute("teacherTaskUserId", user.userId());
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
			selectedSchoolIds.add(input.schoolId());
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
		String selectedStatus = selectedTask == null ? null : selectedTask.publicationStatus();
		request.setAttribute("teacherTaskPublicationStatus", selectedStatus);
		request.setAttribute("teacherTaskAction", "published".equals(selectedStatus)
				? "createRevision" : taskId > 0 ? "updateDraft" : "createDraft");
		request.setAttribute("teacherTaskSelectedClassIds", selectedClassIds);
		request.setAttribute("teacherTaskSelectedSchoolIds", selectedSchoolIds);
		request.setAttribute("teacherTaskAssignments", assignments);
		request.setAttribute("teacherTaskFieldValues", fieldValues(input, rawValues));
		TeacherTaskCreateRegistry registry = registry(request.getSession(false), true);
		String createToken = submittedForm != null
				&& ("createDraft".equals(submittedForm.action())
						|| "createRevision".equals(submittedForm.action()))
				? submittedForm.requestToken()
				: registry.issueToken();
		request.setAttribute("teacherTaskCreateToken", createToken);
		request.getRequestDispatcher("/WEB-INF/teacher/task/task.jsp").forward(request, response);
	}

	private void processTaskStateOperation(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			Map<String, List<String>> values) throws IOException {
		try {
			TaskStateOperation operation = parseTaskStateOperation(values);
			HttpServletRequest parsedRequest = csrfRequest(request, operation.csrfToken());
			if (!CsrfTokens.isValid(parsedRequest)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "画面を再読み込みしてください。");
				return;
			}
			if ("copyToNewTaskSeries".equals(operation.action())) {
				long copiedTaskId = TASKS.createIndependentCopy(
						user, operation.taskId(), operation.expectedVersion(), operation.requestToken());
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.setAttribute(
							TASK_STATE_NOTICE_ATTRIBUTE,
							"新しい課題系列の下書きを作成しました。対象クラス・公開日時・提出期限を選び、プロンプトを確認してください。");
				}
				response.sendRedirect(response.encodeRedirectURL(
						request.getContextPath() + "/teacher/task?taskId=" + copiedTaskId));
				return;
			} else if ("deleteTask".equals(operation.action())) {
				TASKS.deleteTask(user, operation.taskId(), operation.expectedVersion(), operation.requestToken());
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.setAttribute(TASK_STATE_NOTICE_ATTRIBUTE, "課題を削除しました。");
				}
			} else {
				TASKS.restoreTask(user, operation.taskId(), operation.expectedVersion(), operation.requestToken());
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.setAttribute(TASK_STATE_NOTICE_ATTRIBUTE, "課題を下書きへ復元しました。");
				}
			}
			response.sendRedirect(request.getContextPath() + "/teacher/task");
		} catch (TaskDraftConflictException e) {
			response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (TaskDraftNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Teacher task state change failed.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static boolean isTaskStateAction(String action) {
		return "deleteTask".equals(action)
				|| "restoreTask".equals(action)
				|| "copyToNewTaskSeries".equals(action);
	}

	private void processPublishedAssignmentOperation(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticatedUser user,
			Map<String, List<String>> values) throws IOException {
		try {
			PublishedAssignmentOperation operation = parsePublishedAssignmentOperation(values);
			HttpServletRequest parsedRequest = csrfRequest(request, operation.csrfToken());
			if (!CsrfTokens.isValid(parsedRequest)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "画面を再読み込みしてください。");
				return;
			}
			TASKS.managePublishedTaskAssignment(
					user,
					operation.taskId(),
					operation.expectedVersion(),
					operation.assignmentId(),
					operation.classroomId(),
					operation.publishAt(),
					operation.dueAt(),
					operation.lateSubmissionPolicy(),
					operation.requestToken());
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.setAttribute(
						TASK_STATE_NOTICE_ATTRIBUTE,
						operation.assignmentId() > 0
								? "クラス別の提出期限を延長しました。"
								: "対象クラスを追加しました。");
			}
			response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/teacher/task"));
		} catch (TaskDraftConflictException e) {
			response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (TaskDraftNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (IllegalArgumentException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (SQLException e) {
			getServletContext().log("Published task assignment change failed.", e);
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private static boolean isPublishedAssignmentAction(String action) {
		return "extendTaskAssignmentDeadline".equals(action)
				|| "addTaskClassAssignment".equals(action);
	}

	static PublishedAssignmentOperation parsePublishedAssignmentOperation(
			Map<String, List<String>> values) {
		String action = requiredSingle(values, "action");
		if ("extendTaskAssignmentDeadline".equals(action)) {
			if (!Set.of("action", "csrfToken", "requestToken", "taskId", "expectedVersion",
					"assignmentId", "newDueAt").equals(values.keySet())) {
				throw new IllegalArgumentException("提出期限延長の入力が不正です。");
			}
			return new PublishedAssignmentOperation(
					action,
					requiredSingle(values, "csrfToken"),
					requiredSingle(values, "requestToken"),
					positiveLong(requiredSingle(values, "taskId"), "課題"),
					positiveLong(requiredSingle(values, "expectedVersion"), "課題更新情報"),
					positiveLong(requiredSingle(values, "assignmentId"), "クラス割当"),
					0,
					null,
					parsedDateTime(requiredSingle(values, "newDueAt"), false),
					null);
		}
		if ("addTaskClassAssignment".equals(action)) {
			if (!Set.of("action", "csrfToken", "requestToken", "taskId", "expectedVersion",
					"classroomId", "publishAt", "dueAt", "lateSubmissionPolicy").equals(values.keySet())) {
				throw new IllegalArgumentException("対象クラス追加の入力が不正です。");
			}
			return new PublishedAssignmentOperation(
					action,
					requiredSingle(values, "csrfToken"),
					requiredSingle(values, "requestToken"),
					positiveLong(requiredSingle(values, "taskId"), "課題"),
					positiveLong(requiredSingle(values, "expectedVersion"), "課題更新情報"),
					0,
					positiveLong(requiredSingle(values, "classroomId"), "クラス"),
					parsedDateTime(optionalSingle(values, "publishAt"), true),
					parsedDateTime(optionalSingle(values, "dueAt"), true),
					TeacherTaskInput.LateSubmissionPolicy.fromDatabaseValue(
							requiredSingle(values, "lateSubmissionPolicy")));
		}
		throw new IllegalArgumentException("利用できないクラス割当操作です。");
	}

	private static LocalDateTime parsedDateTime(String value, boolean optional) {
		if (optional && value.isEmpty()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("日時の形式が正しくありません。", e);
		}
	}

	private static String optionalSingle(Map<String, List<String>> values, String name) {
		List<String> entries = values.get(name);
		if (entries == null || entries.size() != 1) {
			throw new IllegalArgumentException("課題操作の入力が不正です。");
		}
		return entries.getFirst();
	}

	private static TaskStateOperation parseTaskStateOperation(Map<String, List<String>> values) {
		if (!Set.of("action", "csrfToken", "requestToken", "taskId", "expectedVersion").equals(values.keySet())) {
			throw new IllegalArgumentException("課題操作の入力が不正です。");
		}
		String action = requiredSingle(values, "action");
		if (!isTaskStateAction(action)) {
			throw new IllegalArgumentException("利用できない課題操作です。");
		}
		return new TaskStateOperation(
				action,
				requiredSingle(values, "csrfToken"),
				requiredSingle(values, "requestToken"),
				positiveLong(requiredSingle(values, "taskId"), "課題"),
				positiveLong(requiredSingle(values, "expectedVersion"), "課題更新情報"));
	}

	private static String requiredSingle(Map<String, List<String>> values, String name) {
		List<String> entries = values.get(name);
		if (entries == null || entries.size() != 1 || entries.getFirst().isBlank()) {
			throw new IllegalArgumentException("課題操作の入力が不正です。");
		}
		return entries.getFirst();
	}

	private static long positiveLong(String value, String label) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 1) {
				throw new NumberFormatException("non-positive");
			}
			return parsed;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("有効な" + label + "を指定してください。", e);
		}
	}

	private record TaskStateOperation(
			String action, String csrfToken, String requestToken, long taskId, long expectedVersion) {
	}

	record PublishedAssignmentOperation(
			String action,
			String csrfToken,
			String requestToken,
			long taskId,
			long expectedVersion,
			long assignmentId,
			long classroomId,
			LocalDateTime publishAt,
			LocalDateTime dueAt,
			TeacherTaskInput.LateSubmissionPolicy lateSubmissionPolicy) {
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

	private void redirectPublished(HttpServletResponse response, HttpServletRequest request)
			throws IOException {
		HttpSession session = request.getSession(false);
		if (session == null) {
			getServletContext().log("Task publication succeeded, but its success notice could not be stored.");
		} else {
			session.setAttribute(TASK_STATE_NOTICE_ATTRIBUTE, "課題を保存し、公開設定を反映しました。");
		}
		response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/teacher/task"));
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

	private static String consumeTaskStateNotice(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object notice = session.getAttribute(TASK_STATE_NOTICE_ATTRIBUTE);
		if (notice == null) {
			return null;
		}
		session.removeAttribute(TASK_STATE_NOTICE_ATTRIBUTE);
		if (!(notice instanceof String message)) {
			throw new IllegalStateException("Task state notice has an invalid session value.");
		}
		return message;
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
