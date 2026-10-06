<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="${editorPage.title}"/>
<c:set var="screenStylesheet" value="/css/student/editor/editor.css"/>
<c:set var="screenScript" value="/js/student/editor/editor.js"/>
<c:set var="screenUsesCodeMirror" value="true"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div class="container"
	 id="studentEditorPage"
	 data-assignment-id="<c:out value='${editorPage.assignmentId}'/>"
	 data-task-title="<c:out value='${editorPage.title}'/>"
	 data-draft-updated-at="<c:out value='${editorPage.draftUpdatedAtToken}'/>"
	 data-editor-font-size-px="<c:out value='${editorPreferences.fontSizePx}'/>"
	 data-editor-line-wrapping="<c:out value='${editorPreferences.lineWrapping}'/>"
	 data-editor-indent-width="<c:out value='${editorPreferences.indentWidth}'/>"
	 data-editor-theme="<c:out value='${editorPreferences.themeValue}'/>"
	 data-editable="<c:out value='${editorPage.editable}'/>"
	 data-can-submit="<c:out value='${editorPage.canSubmit}'/>"
	 data-save-url="<c:url value='/student/editor/draft'/>"
	 data-run-url="<c:url value='/student/editor/run'/>"
	 data-session-url="<c:url value='/student/editor/session'/>"
	 data-session-input-url="<c:url value='/student/editor/session/input'/>"
	 data-session-cancel-url="<c:url value='/student/editor/session/cancel'/>"
	 data-preferences-url="<c:url value='/student/editor/preferences'/>"
	 data-check-url="<c:url value='/student/editor/submission/check'/>"
	 data-submit-url="<c:url value='/student/editor/submission/submit'/>"
	 data-evaluation-url="<c:url value='/student/evaluation'/>"
	 data-resubmission-url="<c:url value='/student/editor/resubmission'/>">
	<input id="csrfToken" type="hidden" value="<c:out value='${csrfToken}'/>">
	<div id="editorFeedback" class="mb-3" aria-live="polite"></div>

	<c:choose>
		<c:when test="${editorPage.difficulty == 'beginner'}">
			<c:set var="difficultyClass" value="beginner"/>
			<c:set var="difficultyLabel" value="初級"/>
		</c:when>
		<c:when test="${editorPage.difficulty == 'intermediate'}">
			<c:set var="difficultyClass" value="intermediate"/>
			<c:set var="difficultyLabel" value="中級"/>
		</c:when>
		<c:when test="${editorPage.difficulty == 'advanced'}">
			<c:set var="difficultyClass" value="advanced"/>
			<c:set var="difficultyLabel" value="上級"/>
		</c:when>
		<c:otherwise>
			<c:set var="difficultyClass" value="unrated"/>
			<c:set var="difficultyLabel" value="難易度未設定"/>
		</c:otherwise>
	</c:choose>

	<nav class="learning-flow-nav mb-2" aria-label="学習フロー">
		<ol class="learning-flow-steps">
			<li class="learning-flow-step">
				<span class="learning-flow-current is-red" aria-current="page">課題に取り組む</span>
			</li>
			<li class="learning-flow-step"><span class="learning-flow-link is-yellow">評価の確認</span></li>
			<li class="learning-flow-step"><span class="learning-flow-link is-green">アンケート</span></li>
		</ol>
		<div class="learning-flow-task-title">
			<span class="learning-flow-task-name">
				<span class="learning-flow-level-badge is-<c:out value='${difficultyClass}'/>"><c:out value="${difficultyLabel}"/></span>
				<span><c:out value="${editorPage.title}"/></span>
			</span>
		</div>
	</nav>

	<section class="sample-section editor-hero-section mb-4">
		<div class="hero-copy">
			<span class="hero-kicker">Editor</span>
			<h1 class="section-title mb-3">エディター</h1>
			<p class="hero-description mb-0">課題を確認し、コードを記述してください。ヒントも適宜確認してください。</p>
		</div>
		<div class="hero-status-card">
			<div class="status-label">課題状況</div>
			<div class="status-value"><c:out value="${editorPage.taskStatusLabel}"/></div>
			<div class="status-meta">
				<c:choose>
					<c:when test="${editorPage.submitted}">
						第<c:out value="${editorPage.latestSubmissionRevision}"/>版を<c:out value="${editorPage.latestSubmittedAtDisplay}"/>に提出しました。
					</c:when>
					<c:otherwise>
						提出期限: <c:out value="${editorPage.dueAtDisplay}"/>
					</c:otherwise>
				</c:choose>
			</div>
			<c:if test="${editorPage.canStartResubmission}">
				<button id="startResubmissionButton" class="btn btn-outline-primary mt-3" type="button">
					再提出用の編集を開始
				</button>
			</c:if>
			<c:if test="${editorPage.submitted and not editorPage.editable and not editorPage.canStartResubmission}">
				<p class="status-note mb-0">提出済みのため、現在は編集できません。</p>
			</c:if>
		</div>
	</section>

	<div class="row g-4 align-items-start">
		<div class="col-xl-8">
			<section class="sample-section editor-workspace-section">
				<div class="editor-toolbar editor-task-toolbar">
					<div class="toolbar-left">
						<span class="task-badge"><c:out value="${difficultyLabel}"/>：<c:out value="${editorPage.title}"/></span>
					</div>
					<div class="button-group">
						<button id="downloadButton" class="btn btn-outline-secondary editor-download-button" type="button" aria-label="コードをダウンロード" title="コードをダウンロード（未保存の変更を含む）"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/></svg></button>
						<button id="saveButton" class="btn btn-secondary" type="button"
							<c:if test="${not editorPage.editable}">disabled</c:if>><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 3h13l4 4v14H3V3h1zm2 0v6h11V3M6 21v-8h12v8"/></svg>保存</button>
						<button id="runButton" class="btn btn-primary" type="button"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m8 5 11 7-11 7V5z"/></svg>実行</button>
						<button id="submitButton" class="btn btn-warning" type="button"
							<c:if test="${not editorPage.canSubmit}">disabled</c:if>>提出する</button>
					</div>
				</div>

				<div class="editor-file-tab-row">
					<span class="editor-file-tab is-active"><c:out value="${editorPage.title}"/>.py</span>
					<button id="editorSettingsButton" class="btn btn-outline-secondary btn-sm editor-settings-button" type="button"
						title="エディター設定" aria-label="エディター設定"
						data-bs-toggle="modal" data-bs-target="#editorSettingsModal" aria-haspopup="dialog">
						<span class="editor-settings-icon" aria-hidden="true">⚙</span>
					</button>
				</div>
				<div class="editor-shell">
					<textarea id="codeEditor" class="editor-textarea" spellcheck="false"
						<c:if test="${not editorPage.editable}">readonly</c:if>><c:out value="${editorPage.code}"/></textarea>
				</div>
				<div class="editor-status-row">
					<div id="editorMessage" class="editor-message">
						保存状態: <c:out value="${editorPage.saveStatusLabel}"/>。保存したコードはサーバーに記録されます。
					</div>
					<span class="editor-language-chip">Python 3.12</span>
				</div>
				<c:if test="${not editorPage.canSubmit and not editorPage.canStartResubmission}">
					<p class="status-note mb-0 mt-3">
						<c:choose>
							<c:when test="${editorPage.submitted}">
								提出期限を過ぎているため、再提出できません。
							</c:when>
							<c:when test="${editorPage.lateSubmissionPolicy != 'allow'}">
								提出期限を過ぎているため、提出できません。
							</c:when>
						</c:choose>
					</p>
				</c:if>
			</section>

			<%@ include file="/WEB-INF/student/shared/execution-terminal.jspf" %>

			<section class="sample-section log-section mt-4">
				<div class="section-header-row">
					<div>
						<h2 class="card-title mb-2">コードログ</h2>
						<div class="log-status-row">
							<div class="status-chip"><span class="status-dot"></span>編集内容を30秒間隔で記録</div>
						</div>
					</div>
					<div class="log-header-actions">
						<button id="toggleLogButton" class="btn btn-outline-secondary btn-sm log-toggle-button"
							type="button" data-bs-toggle="collapse" data-bs-target="#codeLogContent"
							aria-expanded="false" aria-controls="codeLogContent">表示する</button>
					</div>
				</div>
				<div id="codeLogContent" class="log-content collapse">
					<div id="codeLogList" class="code-log-list">
						<c:choose>
							<c:when test="${empty editorPage.codeLogs}">
								<p id="emptyCodeLogs" class="text-muted mb-0">コードログはまだありません。</p>
							</c:when>
							<c:otherwise>
								<c:forEach var="log" items="${editorPage.codeLogs}">
									<article class="code-log-item">
										<div class="log-time"><c:out value="${log.observedAtDisplay}"/></div>
										<div class="log-body">
											<div class="log-header-row">
												<div class="log-title"><c:out value="${log.title}"/></div>
												<button class="btn btn-outline-secondary btn-sm log-code-toggle" type="button"
													aria-expanded="false">コードを表示</button>
											</div>
											<c:if test="${not empty log.executionStatus}">
												<p class="log-text mb-0">実行状態: <c:out value="${log.executionStatusDisplay}"/></p>
											</c:if>
											<div class="log-code-panel" hidden>
												<textarea class="log-code-source" spellcheck="false"><c:out value="${log.snapshot}"/></textarea>
											</div>
										</div>
									</article>
								</c:forEach>
							</c:otherwise>
						</c:choose>
					</div>
				</div>
			</section>
		</div>

		<div class="col-xl-4">
			<section class="sample-section side-panel-section sticky-xl-top">
				<div class="section-header-row mb-3">
					<h2 class="card-title mb-2">課題情報</h2>
				</div>
				<div class="nav nav-pills info-tabs" role="tablist" aria-label="課題情報">
					<button class="nav-link active" type="button" data-panel-target="taskPanel">課題</button>
					<button class="nav-link" type="button" data-panel-target="hintPanel">ヒント</button>
				</div>
				<div class="info-panels mt-4">
					<div id="taskPanel" class="info-panel is-active">
						<h3><c:out value="${editorPage.title}"/></h3>
						<p><c:out value="${editorPage.description}"/></p>
						<div class="info-block">
							<div class="info-label">実装する機能</div>
							<c:choose>
								<c:when test="${empty editorPage.features}"><p class="mb-0">設定されていません。</p></c:when>
								<c:otherwise>
									<ul class="mb-0">
										<c:forEach var="feature" items="${editorPage.features}">
											<li><c:out value="${feature}"/></li>
										</c:forEach>
									</ul>
								</c:otherwise>
							</c:choose>
						</div>
						<div class="info-block">
							<div class="info-label">入力制限</div>
							<p class="mb-0">
								<c:choose>
									<c:when test="${not empty editorPage.inputConstraints}"><c:out value="${editorPage.inputConstraints}"/></c:when>
									<c:otherwise>設定されていません。</c:otherwise>
								</c:choose>
							</p>
						</div>
						<c:if test="${not empty editorPage.creationRules}">
							<div class="info-block">
								<div class="info-label">作成時のルール</div>
								<pre class="creation-rules mb-0"><c:out value="${editorPage.creationRules}"/></pre>
							</div>
						</c:if>
						<div class="info-block io-block">
							<div class="info-label">想定入出力</div>
							<c:choose>
								<c:when test="${empty editorPage.testCases}">
									<p class="mb-0">この課題に想定入出力は設定されていません。</p>
								</c:when>
								<c:otherwise>
									<c:forEach var="testCase" items="${editorPage.testCases}">
										<div class="case-card io-case-card">
											<div class="io-case-label mt-2">入力</div>
											<textarea class="io-case-source" spellcheck="false"><c:out value="${testCase.input}"/></textarea>
											<div class="io-case-label mt-3">出力</div>
											<textarea class="io-case-source" spellcheck="false"><c:out value="${testCase.expectedOutput}"/></textarea>
										</div>
									</c:forEach>
								</c:otherwise>
							</c:choose>
						</div>
					</div>

					<div id="hintPanel" class="info-panel">
						<c:choose>
							<c:when test="${empty editorPage.hints}">
								<p class="text-muted mb-0">この課題にヒントはありません。</p>
							</c:when>
							<c:otherwise>
								<c:forEach var="hint" items="${editorPage.hints}">
									<div class="hint-card">
										<div class="info-label"><c:out value="${hint.title}"/></div>
										<p><c:out value="${hint.content}"/></p>
										<c:if test="${not empty hint.usageSyntax}">
											<p class="mb-2"><c:out value="${hint.usageSyntax}"/></p>
										</c:if>
										<c:if test="${not empty hint.code}">
											<textarea class="hint-code-source" spellcheck="false"><c:out value="${hint.code}"/></textarea>
										</c:if>
									</div>
								</c:forEach>
							</c:otherwise>
						</c:choose>
					</div>
				</div>
			</section>
		</div>
	</div>
</div>

<%@ include file="/WEB-INF/student/shared/editor-settings.jspf" %>

<div class="modal fade submit-check-modal" id="submitCheckModal" tabindex="-1"
	 aria-labelledby="submitCheckModalLabel" aria-hidden="true">
	<div class="modal-dialog modal-xl modal-dialog-scrollable">
		<div class="modal-content">
			<div class="modal-header">
				<h2 class="modal-title h5 mb-0" id="submitCheckModalLabel">入出力チェック</h2>
				<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
			</div>
			<div class="modal-body">
				<p class="text-muted mb-2">想定入出力と実行結果を確認してください。不一致があっても提出できます。</p>
				<div id="submitCheckSummary" class="check-result-summary">確認前</div>
				<div class="table-responsive mt-3">
					<table class="table check-result-table mb-0">
						<thead>
							<tr><th>入力</th><th>期待する出力</th><th>実際の出力</th><th class="text-center">結果</th></tr>
						</thead>
						<tbody id="submitCheckTableBody">
							<tr><td colspan="4" class="text-muted">提出時にチェック結果を表示します。</td></tr>
						</tbody>
					</table>
				</div>
			</div>
			<div class="modal-footer justify-content-between">
				<button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">戻る</button>
				<div class="submit-confirmation-actions">
					<span class="submit-resubmission-note">期限内の再提出は可能です。</span>
					<button type="button" class="btn btn-warning" id="confirmSubmitAfterCheck">提出する</button>
				</div>
			</div>
		</div>
	</div>
</div>

<script src="https://cdn.jsdelivr.net/npm/codemirror@5.65.16/lib/codemirror.js"></script>
<script src="https://cdn.jsdelivr.net/npm/codemirror@5.65.16/mode/python/python.js"></script>
<script src="https://cdn.jsdelivr.net/npm/codemirror@5.65.16/mode/shell/shell.js"></script>
<script src="<c:url value='/js/student/shared/code-editor.js'/>" defer></script>
<script src="<c:url value='/js/student/shared/editor-settings.js'/>" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
