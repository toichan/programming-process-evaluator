<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="課題編集"/>
<c:set var="screenDesign" value="teacher"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<c:set var="formTaskName" value="${teacherTaskFieldValues.taskName}"/>
<c:set var="formTheme" value="${teacherTaskFieldValues.theme}"/>
<c:set var="formDifficulty" value="${teacherTaskFieldValues.difficulty}"/>
<c:set var="formDescription" value="${teacherTaskFieldValues.description}"/>
<c:set var="formFeatures" value="${teacherTaskFieldValues.features}"/>
<c:set var="formConstraints" value="${teacherTaskFieldValues.inputConstraints}"/>
<c:set var="formRules" value="${teacherTaskFieldValues.creationRules}"/>
<c:set var="formInitialCode" value="${teacherTaskFieldValues.initialCode}"/>
<c:set var="formLatePolicy" value="${teacherTaskFieldValues.lateSubmissionPolicy}"/>
<c:set var="selectedTask" value="${teacherTaskPage.selectedTask}"/>
<div class="page-content py-5">
	<div class="container-fluid px-4">
		<section class="task-hero mb-4">
			<div>
				<span class="hero-kicker">Task Management</span>
				<h1 class="section-title mb-2">課題編集</h1>
				<p class="hero-description mb-0">課題の新規作成と、保存済み下書きの編集を行います。</p>
			</div>
			<div class="hero-stats">
				<div class="stat-card">
					<div class="stat-label">下書き</div>
					<div class="stat-value"><c:out value="${teacherTaskDraftCount}"/></div>
				</div>
				<div class="stat-card">
					<div class="stat-label">担当学校</div>
					<div class="stat-value"><c:out value="${teacherTaskSchoolCount}"/></div>
				</div>
				<div class="stat-card">
					<div class="stat-label">ヒント候補</div>
					<div class="stat-value"><c:out value="${teacherTaskReusableHintCount}"/></div>
				</div>
			</div>
		</section>

		<c:if test="${teacherTaskSaved}">
			<div class="alert alert-success" id="taskSaveNotice" role="status">課題の下書きを保存しました。</div>
		</c:if>
		<c:if test="${not empty teacherTaskError}">
			<div class="alert alert-danger" role="alert"><c:out value="${teacherTaskError}"/></div>
		</c:if>

		<section class="panel-card mb-4">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div>
					<span class="hero-kicker mb-1 d-inline-block">Create</span>
					<h2 class="subheading mb-0" id="createSectionHeading">
						<c:choose>
							<c:when test="${teacherTaskId > 0}">下書きの編集</c:when>
							<c:otherwise>新規課題の作成</c:otherwise>
						</c:choose>
					</h2>
				</div>
				<div class="d-flex gap-2">
					<c:if test="${teacherTaskId > 0}">
						<a class="btn btn-outline-secondary btn-sm" href="<c:url value='/teacher/task'/>">新規作成へ</a>
					</c:if>
					<button class="btn btn-outline-secondary btn-sm" type="button" id="resetFormButton">入力をリセット</button>
				</div>
			</div>

			<div class="row g-4">
				<div class="col-xl-7">
					<form id="taskCreateForm" class="task-form" method="post" action="<c:url value='/teacher/task'/>">
						<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
						<input type="hidden" name="requestToken" value="<c:out value='${teacherTaskCreateToken}'/>">
						<input type="hidden" name="action" id="taskAction" value="<c:out value='${teacherTaskAction}'/>">
						<input type="hidden" name="taskId" value="<c:out value='${teacherTaskId}'/>">
						<input type="hidden" name="expectedVersion" value="<c:out value='${teacherTaskExpectedVersion}'/>">
						<h3 class="form-section-title">課題情報</h3>
						<div class="row g-3">
							<div class="col-md-6">
								<label class="form-label d-block">学校</label>
								<div class="dropdown class-dropdown" id="schoolCheckboxGroup" aria-describedby="schoolSelectHelp">
									<button id="schoolDropdownButton" class="btn btn-outline-secondary form-control text-start dropdown-toggle"
										type="button" data-bs-toggle="dropdown" data-bs-auto-close="outside" aria-expanded="false">学校を選択</button>
									<div class="dropdown-menu class-dropdown-menu p-2 w-100">
										<c:forEach items="${teacherTaskPage.schools}" var="school">
											<div class="form-check">
												<input class="form-check-input" type="checkbox" name="schoolTargets"
													id="school-${school.schoolId}" value="${school.schoolId}"
													<c:if test="${teacherTaskSelectedSchoolIds.contains(school.schoolId)}">checked</c:if>>
												<label class="form-check-label" for="school-${school.schoolId}"><c:out value="${school.name}"/></label>
											</div>
										</c:forEach>
									</div>
								</div>
								<div id="schoolSelectHelp" class="form-text">担当する学校を選択してください。複数選択できます。</div>
							</div>
							<div class="col-md-6">
								<label class="form-label d-block">クラス</label>
								<div class="dropdown class-dropdown" id="classCheckboxGroup" aria-describedby="classSelectHelp">
									<button id="classDropdownButton" class="btn btn-outline-secondary form-control text-start dropdown-toggle"
										type="button" data-bs-toggle="dropdown" data-bs-auto-close="outside" aria-expanded="false">クラスを選択</button>
									<div class="dropdown-menu class-dropdown-menu p-2 w-100">
										<c:forEach items="${teacherTaskPage.classes}" var="classroom">
											<c:set var="assignment" value="${teacherTaskAssignments[classroom.classroomId]}"/>
											<div class="form-check class-option" data-school-id="${classroom.schoolId}">
												<input class="form-check-input" type="checkbox" name="classTargets"
													id="class-${classroom.classroomId}" value="${classroom.classroomId}"
													data-assignment-id="<c:out value='${assignment.assignmentId}' default='0'/>"
													data-publish-at="<c:out value='${assignment.publishAt}'/>"
													data-due-at="<c:out value='${assignment.dueAt}'/>"
													<c:if test="${teacherTaskSelectedClassIds.contains(classroom.classroomId)}">checked</c:if>>
												<label class="form-check-label" for="class-${classroom.classroomId}">
													<c:if test="${not empty classroom.gradeName}"><c:out value="${classroom.gradeName}"/></c:if>
													<c:out value="${classroom.name}"/>
												</label>
											</div>
										</c:forEach>
									</div>
								</div>
								<div id="classSelectHelp" class="form-text">担当学校を選択してから、対象クラスを選びます。</div>
							</div>
							<div class="col-md-6">
								<label for="levelSelect" class="form-label">難易度</label>
								<select id="levelSelect" name="difficulty" class="form-select">
									<option value="">選択</option>
									<option value="none" <c:if test="${formDifficulty == 'none'}">selected</c:if>>なし</option>
									<option value="beginner" <c:if test="${formDifficulty == 'beginner'}">selected</c:if>>初級</option>
									<option value="intermediate" <c:if test="${formDifficulty == 'intermediate'}">selected</c:if>>中級</option>
									<option value="advanced" <c:if test="${formDifficulty == 'advanced'}">selected</c:if>>上級</option>
								</select>
							</div>
							<div class="col-md-6">
								<label for="lateSubmissionPolicy" class="form-label">期限後の提出</label>
								<select id="lateSubmissionPolicy" name="lateSubmissionPolicy" class="form-select">
									<option value="">選択</option>
									<option value="allow" <c:if test="${formLatePolicy == 'allow'}">selected</c:if>>許可する</option>
									<option value="deny" <c:if test="${formLatePolicy == 'deny'}">selected</c:if>>許可しない</option>
								</select>
								<div class="form-text">下書き保存では生徒への公開は行われません。</div>
							</div>
							<div class="col-12">
								<label class="form-label">クラス別の公開期間・提出期限</label>
								<div id="classScheduleList" class="class-schedule-list" aria-live="polite">
									<p class="class-schedule-empty mb-0 text-muted">クラスを選択すると、クラスごとの設定欄が表示されます。</p>
								</div>
								<div class="form-text">保存した課題は下書きです。公開操作はまだ利用できません。</div>
							</div>
							<div class="col-12">
								<label for="taskNameInput" class="form-label">課題名・授業</label>
								<input id="taskNameInput" name="taskName" class="form-control" type="text" maxlength="255"
									placeholder="例: じゃんけん, Lesson1" required value="<c:out value='${formTaskName}'/>">
							</div>
							<div class="col-12">
								<label for="themeInput" class="form-label">テーマ</label>
								<input id="themeInput" name="theme" class="form-control" type="text"
									placeholder="例: 条件分岐 / 入力処理" value="<c:out value='${formTheme}'/>">
							</div>
							<div class="col-12">
								<label for="taskDescriptionInput" class="form-label">課題説明</label>
								<textarea id="taskDescriptionInput" name="description" class="form-control" rows="3"
									placeholder="課題の目的と完成条件を入力"><c:out value="${formDescription}"/></textarea>
							</div>
							<div class="col-12">
								<label for="featureInput" class="form-label">実装機能</label>
								<textarea id="featureInput" name="features" class="form-control" rows="2"
									placeholder="セミコロン区切りで入力"><c:out value="${formFeatures}"/></textarea>
							</div>
							<div class="col-12">
								<label for="taskConstraintInput" class="form-label">制約・注意事項</label>
								<textarea id="taskConstraintInput" name="inputConstraints" class="form-control" rows="2"
									placeholder="入力制限、禁止事項などを入力"><c:out value="${formConstraints}"/></textarea>
							</div>
							<div class="col-12">
								<label for="taskCreationRulesInput" class="form-label">作成時のルール</label>
								<textarea id="taskCreationRulesInput" name="creationRules" class="form-control" rows="3"
									placeholder="任意です。ルールがない課題では空欄のままにしてください。"><c:out value="${formRules}"/></textarea>
							</div>
							<div class="col-12">
								<label for="initialCodeInput" class="form-label">初期コード</label>
								<textarea id="initialCodeInput" name="initialCode" class="form-control code-editor-input" rows="7"
									placeholder="初期表示するPythonコードを入力"><c:out value="${formInitialCode}"/></textarea>
							</div>
							<div class="col-12">
								<div class="d-flex justify-content-between align-items-center mb-2">
									<label class="form-label mb-0">テストケース</label>
									<button class="btn btn-sm btn-outline-primary" type="button" id="addTestCaseButton">テストケース追加</button>
								</div>
								<div id="testCaseList" class="test-case-dynamic-list">
									<c:choose>
										<c:when test="${not empty teacherTaskInput}">
											<c:forEach items="${teacherTaskInput.testCases}" var="testCase">
												<div class="test-case-row">
													<input type="hidden" name="testCaseIds" value="<c:out value='${testCase.testCaseId}'/>">
													<div class="field-block"><label class="mini-label">説明</label><input class="form-control" name="testCaseTitles" value="<c:out value='${testCase.title}'/>"></div>
													<div class="field-block"><label class="mini-label">入力例</label><textarea class="form-control" name="testCaseInputs" rows="3"><c:out value="${testCase.input}"/></textarea></div>
													<div class="field-block"><label class="mini-label">期待する出力</label><textarea class="form-control" name="testCaseOutputs" rows="3"><c:out value="${testCase.expectedOutput}"/></textarea></div>
													<button class="btn btn-sm btn-outline-danger remove-row" type="button" aria-label="テストケースを削除">削除</button>
												</div>
											</c:forEach>
										</c:when>
										<c:otherwise>
											<c:forEach items="${submittedTeacherTaskValues['testCaseInputs']}" var="testInput" varStatus="status">
												<div class="test-case-row">
													<input type="hidden" name="testCaseIds" value="<c:out value='${submittedTeacherTaskValues["testCaseIds"][status.index]}' default='0'/>">
													<div class="field-block"><label class="mini-label">説明</label><input class="form-control" name="testCaseTitles" value="<c:out value='${submittedTeacherTaskValues["testCaseTitles"][status.index]}'/>"></div>
													<div class="field-block"><label class="mini-label">入力例</label><textarea class="form-control" name="testCaseInputs" rows="3"><c:out value="${testInput}"/></textarea></div>
													<div class="field-block"><label class="mini-label">期待する出力</label><textarea class="form-control" name="testCaseOutputs" rows="3"><c:out value='${submittedTeacherTaskValues["testCaseOutputs"][status.index]}'/></textarea></div>
													<button class="btn btn-sm btn-outline-danger remove-row" type="button" aria-label="テストケースを削除">削除</button>
												</div>
											</c:forEach>
										</c:otherwise>
									</c:choose>
								</div>
							</div>
						</div>

						<h3 class="form-section-title mt-4">ヒント情報</h3>
						<div class="row g-3">
							<div class="col-12">
								<div class="d-flex justify-content-end align-items-center mb-2">
									<div class="d-flex gap-2">
										<button class="btn btn-sm btn-outline-secondary" type="button" id="openHintLibraryButton">他課題から追加</button>
										<button class="btn btn-sm btn-outline-primary" type="button" id="addHintButton">ヒント追加</button>
									</div>
								</div>
								<div id="hintList" class="test-case-list">
									<c:choose>
										<c:when test="${not empty teacherTaskInput}">
											<c:forEach items="${teacherTaskInput.hints}" var="hintInput">
												<div class="hint-row">
													<input type="hidden" name="hintIds" value="<c:out value='${hintInput.hintId}'/>">
													<div class="field-block"><label class="mini-label">コマンド名</label><input class="form-control" name="hintTitles" value="<c:out value='${hintInput.hint.title}'/>"></div>
													<div class="field-block"><label class="mini-label">説明</label><textarea class="form-control" name="hintContents" rows="2"><c:out value="${hintInput.hint.content}"/></textarea></div>
													<div class="field-block"><label class="mini-label">使用方法</label><input class="form-control" name="hintUsageSyntaxes" value="<c:out value='${hintInput.hint.usageSyntax}'/>"></div>
													<div class="field-block"><label class="mini-label">サンプルコード</label><textarea class="form-control hint-code" name="hintCodes" rows="2"><c:out value="${hintInput.hint.code}"/></textarea></div>
													<button class="btn btn-sm btn-outline-danger remove-row" type="button" aria-label="ヒントを削除">削除</button>
												</div>
											</c:forEach>
										</c:when>
										<c:otherwise>
											<c:forEach items="${submittedTeacherTaskValues['hintTitles']}" var="hintTitle" varStatus="status">
												<div class="hint-row">
													<input type="hidden" name="hintIds" value="<c:out value='${submittedTeacherTaskValues["hintIds"][status.index]}' default='0'/>">
													<div class="field-block"><label class="mini-label">コマンド名</label><input class="form-control" name="hintTitles" value="<c:out value='${hintTitle}'/>"></div>
													<div class="field-block"><label class="mini-label">説明</label><textarea class="form-control" name="hintContents" rows="2"><c:out value="${submittedTeacherTaskValues['hintContents'][status.index]}"/></textarea></div>
													<div class="field-block"><label class="mini-label">使用方法</label><input class="form-control" name="hintUsageSyntaxes" value="<c:out value='${submittedTeacherTaskValues["hintUsageSyntaxes"][status.index]}'/>"></div>
													<div class="field-block"><label class="mini-label">サンプルコード</label><textarea class="form-control hint-code" name="hintCodes" rows="2"><c:out value="${submittedTeacherTaskValues['hintCodes'][status.index]}"/></textarea></div>
													<button class="btn btn-sm btn-outline-danger remove-row" type="button" aria-label="ヒントを削除">削除</button>
												</div>
											</c:forEach>
										</c:otherwise>
									</c:choose>
								</div>
							</div>
						</div>
						<div class="d-flex gap-2 mt-4 justify-content-center flex-wrap">
							<button class="btn btn-primary" type="button" id="publishTaskButton" disabled
								title="公開機能は後続工程で実装します。">保存・公開（未対応）</button>
							<button class="btn btn-outline-secondary" type="submit" id="saveDraftButton">下書き保存</button>
						</div>
					</form>
				</div>

				<div class="col-xl-5">
					<div class="preview-card">
						<div class="preview-header">
							<span class="preview-kicker">Preview</span>
							<h3 class="preview-title">課題プレビュー</h3>
						</div>
						<article id="previewCard" class="task-list-card task-card task-card-beginner mt-2">
							<div class="task-header-row"><div><span id="previewLevel" class="task-level d-none"></span><h3 id="previewName" class="mt-2 mb-0" style="font-size:1.15rem;">課題名未設定</h3></div></div>
							<p id="previewDescription" class="task-description mb-2">説明未設定</p>
							<div><span class="meta-label">学習テーマ</span> <span id="previewTheme" class="meta-value">―</span></div>
						</article>
						<section class="sample-section preview-side-panel-section mt-3">
							<div class="section-header-row mb-3"><h2 class="card-title mb-2">課題情報</h2></div>
							<div class="info-panels mt-4">
								<div id="previewTaskPanel" class="info-panel is-active">
									<h3 id="previewEditorName">課題名未設定</h3>
									<p id="previewEditorDescription">説明未設定</p>
									<div class="info-block"><div class="info-label">実装する機能</div><ul id="previewEditorFeatures" class="mb-0"><li>未設定</li></ul></div>
									<div class="info-block"><div class="info-label">入力制限</div><p id="previewEditorConstraint" class="mb-0">未設定</p></div>
									<div class="info-block" id="previewEditorRulesBlock" hidden><div class="info-label">作成時のルール</div><ul id="previewEditorRules" class="mb-0"></ul></div>
									<div class="info-block io-block"><div class="info-label">想定入出力</div><div id="previewEditorCases"><p class="mb-0 text-muted">未設定</p></div></div>
									<div id="previewHintCards"></div>
								</div>
							</div>
						</section>
					</div>
				</div>
			</div>
		</section>

		<section class="panel-card">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div><span class="hero-kicker mb-1 d-inline-block">Drafts</span><h2 class="subheading mb-0">保存済み下書き</h2></div>
				<a class="btn btn-outline-secondary btn-sm" href="<c:url value='/teacher/task'/>">一覧を更新</a>
			</div>
			<div class="alert alert-light border task-edit-policy" id="taskListHelp">
				<strong>現在利用できる操作</strong>
				<ul class="mb-0 mt-2 small">
					<li>この一覧には自分が作成した下書きだけが表示されます。</li>
					<li>公開・削除・復元・プロンプト設定は未対応です。</li>
					<li>下書き保存だけでは生徒に課題は公開されません。</li>
				</ul>
			</div>
			<c:if test="${empty teacherTaskPage.tasks}"><p class="text-muted" role="status">保存済み下書きはありません。</p></c:if>
			<div class="table-responsive">
				<table class="table table-hover align-middle mb-0" id="taskTable" aria-describedby="taskListHelp">
					<thead class="table-light"><tr><th>課題名</th><th>難易度</th><th>対象クラス</th><th>状態</th><th>プロンプト</th><th>作成者</th><th>最終更新者</th><th>最終更新日時</th><th>操作</th></tr></thead>
					<tbody>
						<c:forEach items="${teacherTaskPage.tasks}" var="task">
							<tr>
								<td><c:out value="${task.input.title}"/></td>
								<td>
									<c:choose>
										<c:when test="${empty task.input.difficulty}">―</c:when>
										<c:when test="${task.input.difficultyValue == 'beginner'}">初級</c:when>
										<c:when test="${task.input.difficultyValue == 'intermediate'}">中級</c:when>
										<c:when test="${task.input.difficultyValue == 'advanced'}">上級</c:when>
										<c:otherwise>なし</c:otherwise>
									</c:choose>
								</td>
								<td>
									<c:forEach items="${task.input.classAssignments}" var="assignment" varStatus="status">
										<c:if test="${status.index > 0}">、</c:if>
										<c:set var="matchedClass" value=""/>
										<c:forEach items="${teacherTaskPage.classes}" var="classroom">
											<c:if test="${classroom.classroomId == assignment.classroomId}"><c:set var="matchedClass" value="${classroom}"/></c:if>
										</c:forEach>
										<c:if test="${not empty matchedClass}">
											<c:forEach items="${teacherTaskPage.schools}" var="school">
												<c:if test="${school.schoolId == matchedClass.schoolId}"><c:out value="${school.name}"/> / </c:if>
											</c:forEach>
											<c:if test="${not empty matchedClass.gradeName}"><c:out value="${matchedClass.gradeName}"/></c:if><c:out value="${matchedClass.name}"/>
										</c:if>
									</c:forEach>
									<c:if test="${empty task.input.classAssignments}">未割当</c:if>
								</td>
								<td><span class="badge text-bg-secondary">下書き</span></td>
								<td><button class="btn btn-sm btn-outline-secondary" type="button" disabled title="後続工程で実装します。">未設定</button></td>
								<td><code class="history-creator-id"><c:out value="${task.createdByLoginId}"/></code></td>
								<td><code class="history-creator-id"><c:out value="${task.updatedByLoginId}" default="-"/></code></td>
								<td><c:out value="${task.updatedAt}"/></td>
								<td>
									<div class="d-flex gap-2">
										<a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/task'><c:param name='taskId' value='${task.taskId}'/></c:url>">編集</a>
										<a class="btn btn-sm btn-outline-secondary" href="<c:url value='/teacher/task'><c:param name='taskId' value='${task.taskId}'/><c:param name='view' value='history'/></c:url>">履歴</a>
									</div>
								</td>
							</tr>
						</c:forEach>
					</tbody>
				</table>
			</div>
		</section>
	</div>
</div>

<div class="modal fade" id="hintLibraryModal" tabindex="-1" aria-hidden="true">
	<div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h5 class="modal-title">他課題のヒントを追加</h5><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<p class="text-muted small mb-3">追加したいヒントにチェックを入れてください。</p>
			<div class="table-responsive"><table class="table table-sm align-middle hint-library-table mb-0">
				<thead class="table-light"><tr><th>選択</th><th>課題名</th><th>順序</th><th>タイトル</th><th>内容</th></tr></thead>
				<tbody id="hintLibraryBody">
					<c:forEach items="${teacherTaskPage.reusableHints}" var="hint">
						<tr>
							<td><input class="form-check-input reusable-hint" type="checkbox" value="${hint.hintId}"
								data-title="<c:out value='${hint.hint.title}'/>" data-content="<c:out value='${hint.hint.content}'/>"
								data-syntax="<c:out value='${hint.hint.usageSyntax}'/>" data-code="<c:out value='${hint.hint.code}'/>"></td>
							<td><c:out value="${hint.taskTitle}"/></td><td><c:out value="${hint.order}"/></td>
							<td><c:out value="${hint.hint.title}"/></td><td><c:out value="${hint.hint.content}"/></td>
						</tr>
					</c:forEach>
					<c:if test="${empty teacherTaskPage.reusableHints}"><tr><td colspan="5" class="text-muted">再利用できるヒントはありません。</td></tr></c:if>
				</tbody>
			</table></div>
		</div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button type="button" class="btn btn-primary" id="importHintsButton">選択したヒントを追加</button></div>
	</div></div>
</div>

<div class="modal fade" id="taskAuditDetailModal" tabindex="-1" aria-hidden="true">
	<div class="modal-dialog modal-lg modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h5 class="modal-title">課題変更履歴</h5><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div class="mb-3 text-dark fw-semibold"><c:out value="${selectedTask.input.title}"/></div>
			<div class="table-responsive"><table class="table table-sm align-middle mb-0">
				<thead class="table-light"><tr><th>日時</th><th>実行者</th><th>操作</th><th>結果</th><th>詳細</th></tr></thead>
				<tbody>
					<c:forEach items="${teacherTaskPage.auditEntries}" var="entry">
						<tr><td><c:out value="${entry.occurredAt}"/></td><td><c:out value="${entry.actorLoginId}"/></td>
							<td><c:out value="${entry.actionType}"/></td><td><c:out value="${entry.resultStatus}"/></td>
							<td><c:out value="${entry.detail}"/></td></tr>
					</c:forEach>
					<c:if test="${empty teacherTaskPage.auditEntries}"><tr><td colspan="5" class="text-muted">変更履歴はありません。</td></tr></c:if>
				</tbody>
			</table></div>
		</div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">閉じる</button></div>
	</div></div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
