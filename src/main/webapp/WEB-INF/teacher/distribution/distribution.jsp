<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="screenPageTitle" value="コード配信"/>
<c:set var="screenDesign" value="teacher"/>
<c:set var="screenBodyClass" value="teacher-distribution-screen"/>
<c:set var="screenStylesheet" value="/css/teacher/distribution/distribution.css"/>
<c:set var="screenScript" value="/js/teacher/distribution/distribution.js"/>
<c:set var="screenUsesCodeMirror" value="true"/>
<c:set var="screenUsesCodeEditor" value="true"/>
<c:set var="initialDistributionItems" value="[]"/>
<c:set var="initialDistributionTargets" value="[]"/>
<c:if test="${not empty distributionFormItems}"><c:set var="initialDistributionItems" value="${distributionFormItems}"/></c:if>
<c:if test="${not empty distributionFormTargets}"><c:set var="initialDistributionTargets" value="${distributionFormTargets}"/></c:if>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<main class="distribution-page page-content py-5">
	<div class="container-fluid px-4">
		<div id="distributionFeedback" aria-live="polite"></div>
		<c:if test="${not empty distributionFormError}">
			<div class="alert alert-danger" role="alert"><c:out value="${distributionFormError}"/></div>
		</c:if>
		<c:if test="${not empty distributionNotice}">
			<div id="distributionNotice" class="d-none" data-message="<c:out value='${distributionNotice}'/>"></div>
		</c:if>

		<section class="distribution-hero mb-4">
			<div class="hero-copy">
				<span class="hero-kicker">Code Distribution</span>
				<h1 class="section-title mb-2">コード配信</h1>
				<p class="hero-description mb-0">授業演習用のテンプレートを作成し、担当クラスへ配信します。</p>
			</div>
			<div class="hero-stats" aria-label="配信状況">
				<div class="stat-card"><div class="stat-label">公開テンプレート</div><div class="stat-value"><c:out value="${fn:length(teacherDistributionPage.templates)}"/></div></div>
				<div class="stat-card"><div class="stat-label">配信中</div><div class="stat-value"><c:out value="${teacherDistributionPage.scheduledCount}"/></div></div>
				<div class="stat-card"><div class="stat-label">配信履歴</div><div class="stat-value"><c:out value="${fn:length(teacherDistributionPage.distributions)}"/></div></div>
			</div>
		</section>

		<section class="panel-card distribution-create-panel mb-4" aria-labelledby="distributionFormTitle">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div><span class="hero-kicker mb-1 d-inline-block">Create</span><h2 id="distributionFormTitle" class="subheading mb-0">新規配信テンプレートの作成</h2></div>
				<button type="button" class="btn btn-outline-secondary btn-sm" id="distributionNewTemplate">入力をリセット</button>
			</div>
			<form id="distributionForm" method="post" action="<c:url value='/teacher/distribution'/>">
				<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
				<input type="hidden" name="action" id="distributionAction" value="saveDraft">
				<input type="hidden" name="templateId" id="distributionTemplateId" value="<c:out value='${empty distributionFormId ? 0 : distributionFormId}'/>">
				<input type="hidden" name="expectedVersion" id="distributionExpectedVersion" value="<c:out value='${empty distributionFormVersion ? 0 : distributionFormVersion}'/>">
				<input type="hidden" name="requestToken" id="distributionRequestToken" value="<c:out value='${distributionRequestToken}'/>">
				<input type="hidden" name="itemsJson" id="distributionItemsJson" value="<c:out value='${initialDistributionItems}'/>">
				<input type="hidden" name="targetsJson" id="distributionTargetsJson" value="<c:out value='${initialDistributionTargets}'/>">

				<h3 class="form-section-title">配信対象・スケジュール</h3>
				<div class="row g-3">
					<div class="col-md-6">
						<label class="form-label" for="distributionSchoolSelect">学校</label>
						<select class="form-select" id="distributionSchoolSelect">
							<option value="">学校を選択</option>
							<c:forEach var="school" items="${teacherDistributionPage.schools}">
								<option value="<c:out value='${school.schoolId}'/>"><c:out value="${school.name}"/></option>
							</c:forEach>
						</select>
					</div>
					<div class="col-md-6">
						<label class="form-label" for="distributionClassDropdownButton">クラス</label>
						<div class="dropdown class-dropdown" id="distributionClassCheckboxGroup">
							<button id="distributionClassDropdownButton" class="btn btn-outline-secondary form-control text-start dropdown-toggle"
								type="button" data-bs-toggle="dropdown" data-bs-auto-close="outside" aria-expanded="false" disabled>学校を選択</button>
							<div class="dropdown-menu class-dropdown-menu p-2 w-100" id="distributionClassOptions">
								<c:forEach var="classroom" items="${teacherDistributionPage.classes}">
									<div class="form-check distribution-class-option" data-school-id="<c:out value='${classroom.schoolId}'/>" hidden>
										<input class="form-check-input" type="checkbox" name="distributionClassTargets"
											id="distributionClass<c:out value='${classroom.classroomId}'/>"
											data-classroom-id="<c:out value='${classroom.classroomId}'/>">
										<label class="form-check-label" for="distributionClass<c:out value='${classroom.classroomId}'/>"><c:out value="${classroom.displayName}"/></label>
									</div>
								</c:forEach>
								<p class="small text-secondary mb-0" id="distributionNoClasses" hidden>この学校に選択できるクラスはありません。</p>
							</div>
						</div>
					</div>
					<div class="col-12">
						<label class="form-label">クラス別の配信日時</label>
						<div id="distributionClassScheduleList" class="class-schedule-list" aria-live="polite"></div>
					</div>
				</div>

				<h3 class="form-section-title mt-4">テンプレート内容</h3>
				<div class="row g-3">
					<div class="col-md-6">
						<label class="form-label" for="distributionName">テンプレート名</label>
						<input class="form-control" type="text" id="distributionName" name="name" maxlength="255" required
							placeholder="例: 条件分岐セット" value="<c:out value='${distributionFormName}'/>">
					</div>
					<div class="col-md-6">
						<label class="form-label" for="distributionRootName">root</label>
						<input class="form-control" type="text" id="distributionRootName" name="rootName" maxlength="230" required
							placeholder="例: lesson01/task01" aria-describedby="distributionRootHelp"
							value="<c:out value='${distributionFormRoot}'/>">
						<div id="distributionRootHelp" class="form-text">フォルダが存在しない場合はルート配下に配信されます。</div>
					</div>
					<div class="col-12">
						<label class="form-label">配信構成（フォルダ・ファイル）</label>
						<div id="distributionExercisePage" class="distribution-exercise-workspace"
							data-root-name="<c:out value='${distributionFormRoot}'/>">
							<div class="row g-4 align-items-start">
								<div class="col-xl-4">
									<section class="sample-section lesson-tree-section sticky-xl-top">
										<div class="section-header-row mb-3"><h3 class="card-title mb-2">演習ファイル</h3></div>
										<details class="lesson-tool-panel" open>
											<summary>追加</summary>
											<div class="lesson-tree-actions">
												<button id="newFolderButton" type="button" class="btn btn-outline-secondary" data-control-icon="folder">新しいフォルダ</button>
												<button id="newFileButton" type="button" class="btn btn-outline-primary" data-control-icon="file">新しいファイル</button>
												<button id="uploadButton" type="button" class="btn btn-outline-info" aria-label="アップロード" title="アップロード" data-control-icon="upload"></button>
											</div>
										</details>
										<p id="exerciseCreationTarget" class="small lesson-explorer-path"></p>
										<details class="lesson-tool-panel" id="exerciseDisplayTools">
											<summary>表示設定<span id="exerciseDisplayStatus" class="lesson-tool-status" aria-live="polite"></span></summary>
											<div class="lesson-tool-body">
												<div class="lesson-explorer-filters mb-2">
													<label for="exerciseSearch" class="form-label">名前で検索</label>
													<input id="exerciseSearch" type="search" class="form-control" autocomplete="off">
													<p id="exerciseSearchStatus" class="small text-muted mt-1 mb-0" aria-live="polite" hidden></p>
													<label for="exerciseSort" class="form-label mt-2">並び順</label>
													<select id="exerciseSort" class="form-select"><option value="name">名前順</option><option value="updated">更新日時の新しい順</option></select>
												</div>
												<div class="lesson-folder-controls">
													<button id="exerciseExpandAll" class="btn btn-outline-secondary btn-sm" type="button" data-control-icon="expand">すべて展開する</button>
													<button id="exerciseCollapseAll" class="btn btn-outline-secondary btn-sm" type="button" data-control-icon="collapse">すべて折りたたむ</button>
													<button id="exerciseRevealFile" class="btn btn-outline-secondary btn-sm" type="button" data-control-icon="reveal">編集中のファイルを表示</button>
												</div>
												<button id="distributionDownloadAllButton" class="btn btn-outline-secondary btn-sm mt-2" type="button" data-control-icon="download">構成全体をZIPでダウンロード</button>
											</div>
										</details>
										<details class="lesson-tool-panel" id="exerciseSelectionPanel">
											<summary>選択操作<span id="exerciseSelectionStatus" class="lesson-tool-status" aria-live="polite"></span></summary>
											<div class="lesson-tool-body">
												<div class="lesson-selection-mode-controls">
													<div class="lesson-explorer-entrances">
														<button id="exerciseSelectMode" class="btn btn-outline-secondary btn-sm" type="button" aria-pressed="false">選択</button>
														<button id="exerciseSelectionFinish" class="btn btn-outline-secondary btn-sm" type="button" hidden>選択を終了</button>
													</div>
													<div id="exerciseSelectionTools" class="lesson-explorer-controls my-2" hidden>
														<button id="exerciseSelectAll" class="btn btn-outline-secondary btn-sm" type="button">全選択</button>
														<button id="exerciseSelectResults" class="btn btn-outline-secondary btn-sm" type="button" hidden>表示結果を選択</button>
													</div>
												</div>
												<div id="exerciseSelectionBar" class="lesson-selection-bar my-2" role="group" aria-label="チェックした項目の操作" hidden>
													<p id="exerciseSelectionCount" class="small mb-1" aria-live="polite">選択0件</p>
													<div class="lesson-selection-actions">
														<button id="downloadAllButton" type="button" class="btn btn-outline-secondary btn-sm" disabled data-control-icon="download">ダウンロード</button>
														<button id="exerciseMoveSelected" type="button" class="btn btn-outline-secondary btn-sm" disabled data-control-icon="move">移動</button>
														<button id="exerciseDeleteSelected" type="button" class="btn btn-outline-danger btn-sm" disabled data-control-icon="trash">完全削除</button>
														<button id="exerciseClearSelected" type="button" class="btn btn-outline-secondary btn-sm" data-control-icon="clear">選択解除</button>
													</div>
												</div>
											</div>
										</details>
										<p id="emptyExercise" class="small text-muted" hidden>最初のフォルダまたはファイルを作成してください。</p>
										<div id="exerciseTree" class="lesson-file-tree" aria-label="演習ファイル"></div>
									</section>
								</div>
								<div class="col-xl-8">
									<section class="sample-section lesson-workspace-section">
										<div class="lesson-toolbar">
											<div class="toolbar-left"><span id="currentPath" class="task-badge">ファイルを選択してください</span></div>
											<div class="button-group">
												<button id="downloadButton" class="btn btn-outline-secondary" type="button" disabled data-control-icon="download">ダウンロード</button>
												<button id="saveButton" class="btn btn-secondary" type="button" disabled data-control-icon="save">保存</button>
												<button id="runButton" class="btn btn-primary" type="button" disabled data-control-icon="run">実行</button>
											</div>
										</div>
										<div class="lesson-file-tab-row"><span id="distributionCurrentFileTab" class="lesson-file-tab">ファイルを選択してください</span></div>
										<div class="editor-shell lesson-editor-shell">
											<textarea id="codeEditor" class="editor-textarea" spellcheck="false" disabled></textarea>
										</div>
										<div class="lesson-status-row">
											<p id="exerciseEditorMessage" class="editor-message mb-0" role="status">ツリーからファイルを選択してください。</p>
											<div class="lesson-status-right"><span class="lesson-language-chip">Python 3.12</span></div>
										</div>
									</section>
									<c:set var="terminalInputMode" value="inline" scope="request" />
									<%@ include file="/WEB-INF/student/shared/execution-terminal.jspf" %>
								</div>
							</div>
						</div>
					</div>
				</div>
				<div class="d-flex gap-2 mt-4 flex-wrap justify-content-center">
					<button class="btn btn-primary" type="submit" id="scheduleDistributionButton" data-submit-action="distribute"
						<c:if test="${empty teacherDistributionPage.classes}">disabled</c:if>>配信</button>
					<button class="btn btn-outline-secondary" type="submit" data-submit-action="saveDraft">下書き保存</button>
				</div>
			</form>
		</section>

		<section class="panel-card mb-3" aria-labelledby="distributionHistoryTitle">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div><span class="hero-kicker mb-1 d-inline-block">Distribution History</span><h2 id="distributionHistoryTitle" class="subheading mb-0">配信履歴</h2></div>
				<div class="distribution-history-actions">
					<button class="btn btn-outline-secondary btn-sm" type="button" id="distributionHistoryRefreshButton">更新</button>
					<button class="btn btn-primary btn-sm" type="button" id="downloadHistoryCsvButton">CSVエクスポート</button>
				</div>
			</div>
			<div class="history-filter-grid">
				<div><label class="form-label" for="distributionHistorySearch">検索</label><input type="search" class="form-control form-control-sm" id="distributionHistorySearch" placeholder="テンプレート / 作成者で検索"></div>
				<div><label class="form-label" for="distributionHistoryFilterSchool">学校</label><select class="form-select form-select-sm" id="distributionHistoryFilterSchool"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="distributionHistoryFilterClass">クラス</label><select class="form-select form-select-sm" id="distributionHistoryFilterClass"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="distributionHistoryFilterStatus">状態</label><select class="form-select form-select-sm" id="distributionHistoryFilterStatus">
					<option value="">すべて</option><option value="draft">下書き</option><option value="scheduled">配信予定</option><option value="in_progress">配信中</option><option value="completed">配信完了</option><option value="stopped">停止</option>
				</select></div>
				<div><label class="form-label" for="distributionHistoryFilterOperator">作成者</label><select class="form-select form-select-sm" id="distributionHistoryFilterOperator"><option value="">すべて</option><option value="<c:out value='${teacherId}'/>"><c:out value="${teacherId}"/></option></select></div>
			</div>
		</section>

		<section class="panel-card" aria-label="配信履歴一覧">
			<div class="table-responsive">
				<table class="table table-hover align-middle mb-0 distribution-history-table" id="distributionHistoryTable">
					<thead class="table-light"><tr>
						<th class="sortable" data-sort-key="datetime">作成日時</th><th class="sortable" data-sort-key="template">テンプレート</th>
						<th class="sortable" data-sort-key="school">学校</th><th class="sortable" data-sort-key="class">クラス</th>
						<th class="sortable" data-sort-key="status">状態</th><th class="sortable" data-sort-key="operator">作成者</th><th>操作</th><th>詳細</th>
					</tr></thead>
					<tbody id="distributionHistoryBody">
						<c:forEach var="distribution" items="${teacherDistributionPage.distributions}">
							<tr data-history-row data-distribution-id="<c:out value='${distribution.distributionId}'/>"
								data-date="<c:out value='${distribution.createdAt}'/>" data-template="<c:out value='${distribution.templateName}'/>"
								data-school="<c:out value='${distribution.schoolName}'/>" data-class="<c:out value='${distribution.classroomNames}'/>"
								data-status="<c:out value='${distribution.status}'/>" data-operator="<c:out value='${teacherId}'/>">
								<td><c:out value="${distribution.createdAt}"/></td><td><c:out value="${distribution.templateName}"/></td>
								<td><c:out value="${empty distribution.schoolName ? '—' : distribution.schoolName}"/></td>
								<td><c:out value="${empty distribution.classroomNames ? '—' : distribution.classroomNames}"/></td>
								<td><span class="badge distribution-status-badge" data-status="<c:out value='${distribution.status}'/>">
									<c:choose><c:when test="${distribution.status == 'draft'}">下書き</c:when><c:when test="${distribution.status == 'scheduled'}">配信予定</c:when><c:when test="${distribution.status == 'in_progress'}">配信中</c:when><c:when test="${distribution.status == 'completed'}">配信完了</c:when><c:when test="${distribution.status == 'stopped'}">停止</c:when><c:otherwise><c:out value="${distribution.status}"/></c:otherwise></c:choose>
								</span></td>
								<td><code class="history-creator-id"><c:out value="${teacherId}"/></code></td>
								<td class="distribution-actions">
									<button class="btn btn-sm btn-outline-primary" type="button" data-edit-template="<c:out value='${distribution.templateId}'/>">編集</button>
									<button class="btn btn-sm btn-outline-secondary" type="button" data-duplicate-template="<c:out value='${distribution.templateId}'/>">複製</button>
									<c:if test="${distribution.status == 'scheduled' or distribution.status == 'in_progress'}">
										<form method="post" action="<c:url value='/teacher/distribution'/>" class="d-inline" data-confirm="stop">
											<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
											<input type="hidden" name="action" value="stop">
											<input type="hidden" name="distributionId" value="<c:out value='${distribution.distributionId}'/>">
											<button class="btn btn-sm btn-outline-danger" type="submit">停止</button>
										</form>
									</c:if>
								</td>
								<td><button type="button" class="btn btn-sm btn-outline-primary distribution-history-detail" data-detail-template="distributionDetail<c:out value='${distribution.distributionId}'/>">表示</button></td>
							</tr>
							<template id="distributionDetail<c:out value='${distribution.distributionId}'/>">
								<div class="mb-3 fw-semibold"><c:out value="${distribution.templateName}"/> / <c:out value="${empty distribution.schoolName ? '—' : distribution.schoolName}"/> / <c:out value="${empty distribution.classroomNames ? '—' : distribution.classroomNames}"/></div>
								<div class="distribution-actions mb-3">
									<c:if test="${distribution.status == 'scheduled' or distribution.status == 'in_progress'}">
										<form method="post" action="<c:url value='/teacher/distribution'/>" data-confirm="stop"><input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"><input type="hidden" name="action" value="stop"><input type="hidden" name="distributionId" value="<c:out value='${distribution.distributionId}'/>"><button class="btn btn-sm btn-outline-danger" type="submit">全体停止</button></form>
									</c:if>
									<c:if test="${distribution.status == 'stopped'}">
										<form method="post" action="<c:url value='/teacher/distribution'/>" data-confirm="resume"><input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"><input type="hidden" name="action" value="resume"><input type="hidden" name="distributionId" value="<c:out value='${distribution.distributionId}'/>"><button class="btn btn-sm btn-outline-primary" type="submit">全体再開</button></form>
									</c:if>
								</div>
								<div class="table-responsive"><table class="table table-sm align-middle">
									<thead class="table-light"><tr><th>クラス</th><th>状態</th><th>予約日時</th><th>実配信日時</th><th>操作</th></tr></thead><tbody>
										<c:forEach var="target" items="${distribution.targets}">
											<tr><td><c:out value="${target.classroomName}"/></td>
												<td><c:choose><c:when test="${target.status == 'not_distributed'}">未配信</c:when><c:when test="${target.status == 'scheduled'}">配信予約</c:when><c:when test="${target.status == 'distributed'}">配信済み</c:when><c:when test="${target.status == 'stopped'}">停止中</c:when><c:otherwise><c:out value="${target.status}"/></c:otherwise></c:choose></td>
												<td><c:out value="${target.scheduledAt}"/></td><td><c:out value="${target.distributedAt}"/></td>
												<td class="distribution-actions">
													<c:if test="${target.status == 'scheduled'}">
														<form method="post" action="<c:url value='/teacher/distribution'/>" class="distribution-reschedule-form" data-target-action="reschedule">
															<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"><input type="hidden" name="action" value="rescheduleTarget"><input type="hidden" name="targetId" value="<c:out value='${target.targetId}'/>">
															<input class="form-control form-control-sm" type="datetime-local" name="scheduledAt" value="<c:out value='${target.scheduledInput}'/>" required aria-label="配信日時を変更">
															<button class="btn btn-sm btn-outline-primary" type="submit">日時変更</button>
														</form>
													</c:if>
													<c:if test="${target.status == 'not_distributed' or target.status == 'scheduled'}">
														<form method="post" action="<c:url value='/teacher/distribution'/>" data-target-action="stop"><input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"><input type="hidden" name="action" value="stopTarget"><input type="hidden" name="targetId" value="<c:out value='${target.targetId}'/>"><button class="btn btn-sm btn-outline-danger" type="submit">停止</button></form>
													</c:if>
													<c:if test="${target.status == 'stopped'}">
														<form method="post" action="<c:url value='/teacher/distribution'/>" data-target-action="resume"><input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"><input type="hidden" name="action" value="resumeTarget"><input type="hidden" name="targetId" value="<c:out value='${target.targetId}'/>"><button class="btn btn-sm btn-outline-primary" type="submit">再開</button></form>
													</c:if>
												</td>
											</tr>
											<c:if test="${not empty target.result}"><tr><td colspan="5"><details><summary>実行結果</summary><pre class="distribution-json"><c:out value="${target.result}"/></pre></details></td></tr></c:if>
										</c:forEach>
										<c:if test="${empty distribution.targets}"><tr><td colspan="5" class="text-secondary">配信対象のクラスはありません。</td></tr></c:if>
									</tbody></table></div>
								<h3 class="h6 mt-3">操作履歴</h3>
								<ul class="distribution-history-list">
									<c:forEach var="entry" items="${distribution.history}"><li><strong><c:out value="${entry.action}"/></strong><span><c:out value="${entry.occurredAt}"/> / <c:out value="${entry.actor}"/> (ID: <c:out value="${entry.actorId}"/>) / <c:out value="${entry.result}"/></span><pre class="distribution-json"><c:out value="${entry.detail}"/></pre></li></c:forEach>
								</ul>
							</template>
						</c:forEach>
						<c:if test="${empty teacherDistributionPage.distributions}"><tr id="distributionHistoryEmpty"><td colspan="8" class="text-center text-secondary py-4">配信履歴はありません。</td></tr></c:if>
					</tbody>
				</table>
			</div>
		</section>
	</div>
</main>

<div class="modal fade" id="createEntryModal" tabindex="-1" aria-labelledby="createEntryTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-centered"><form id="createEntryForm" class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5 mb-0" id="createEntryTitle">新しいファイル</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div id="createEntryFeedback" aria-live="polite"></div>
			<label for="parentEntry" class="form-label">作成先</label><select id="parentEntry" class="form-select mb-3"></select>
			<label for="entryName" class="form-label">名前</label>
			<div class="input-group"><input id="entryName" class="form-control" maxlength="255" required autocomplete="off" aria-describedby="entryNameHelp"><span id="entryNameExtension" class="input-group-text d-none" hidden>.py</span></div>
			<p id="entryNameHelp" class="small text-muted mt-2 mb-0">1〜255文字。使えない文字：/（スラッシュ）、\（バックスラッシュ）、改行、タブなど。空白だけの名前も使えません。「.」だけ・「..」だけの名前は使えません。</p>
		</div>
		<div class="modal-footer"><button class="btn btn-outline-secondary" type="button" data-bs-dismiss="modal">戻る</button><button id="createEntryConfirm" class="btn btn-primary" type="submit">作成</button></div>
	</form></div>
</div>
<div class="modal fade upload-entry-modal" id="uploadModal" tabindex="-1" aria-labelledby="uploadModalTitle" aria-hidden="true">
	<div class="modal-dialog modal-xl upload-entry-modal-dialog modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="uploadModalTitle">ファイル・フォルダをアップロード</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div id="uploadFeedback" aria-live="polite"></div>
			<p>複数ファイルまたはフォルダを選択し、追加先フォルダへ取り込みます。</p>
			<div class="upload-entry-layout">
				<section class="upload-entry-file-panel">
					<h3 class="upload-entry-heading">1. ファイル/フォルダ選択</h3>
					<div class="upload-entry-source-actions">
						<button id="uploadSelectFilesButton" class="btn btn-outline-primary" type="button"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 3h9l5 5v13H5zM14 3v5h5M12 12v6m-3-3h6"/></svg>ファイルを選択</button>
						<button id="uploadSelectFolderButton" class="btn btn-outline-secondary" type="button"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6V4h7l2 3h9v13H3zM14 12v6m-3-3h6"/></svg>フォルダを選択</button>
					</div>
					<input id="uploadSourceFile" class="d-none" type="file" accept=".py" multiple>
					<input id="uploadSourceDirectory" class="d-none" type="file" accept=".py" multiple webkitdirectory directory>
					<p id="uploadFileName" class="upload-entry-file-name" role="status">未選択</p>
					<ul id="uploadFilePreviewList" class="upload-file-preview-list" aria-label="選択したファイル一覧"></ul>
					<p class="small text-muted mt-2">UTF-8の.pyのみ。1ファイル64 KiB・100ファイル・合計1 MiB以内。同名の項目は上書きしません。</p>
				</section>
				<section class="upload-entry-folder-panel">
					<h3 class="upload-entry-heading">2. 追加先フォルダ</h3>
					<p class="upload-entry-help">追加先のフォルダをクリックしてください。</p>
					<div id="uploadFolderTree" class="upload-folder-tree" role="group" aria-label="追加先フォルダ"></div>
				</section>
			</div>
			<section id="uploadResolutionPanel" class="mt-3" hidden>
				<h3 class="upload-entry-heading">3. 追加内容の確認</h3>
				<div id="uploadResolutionItems"></div>
			</section>
		</div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button id="uploadEntryConfirmButton" type="button" class="btn btn-primary" disabled>追加内容を確認</button></div>
	</div></div>
</div>
<div class="modal fade" id="batchOperationModal" tabindex="-1" aria-labelledby="batchOperationTitle" aria-hidden="true">
	<div class="modal-dialog"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="batchOperationTitle">選択項目の操作</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<p id="batchOperationDescription" class="small text-secondary"></p>
			<ul id="batchOperationItems" class="small"></ul>
			<div id="batchDestinationGroup" hidden><label for="batchDestination" class="form-label">移動先フォルダ</label><select id="batchDestination" class="form-select"></select></div>
			<p id="batchOperationError" class="text-danger small mt-2 mb-0" role="alert"></p>
		</div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button id="batchOperationConfirm" type="button" class="btn btn-primary">実行</button></div>
	</div></div>
</div>
<div class="modal fade" id="distributionHistoryDetailModal" tabindex="-1" aria-labelledby="distributionHistoryDetailTitle" aria-hidden="true">
	<div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="distributionHistoryDetailTitle">配信履歴の詳細</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body" id="distributionHistoryDetailBody"></div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">閉じる</button></div>
	</div></div>
</div>
<script src="<c:url value='/js/student/shared/explorer-menus.js'/>" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
