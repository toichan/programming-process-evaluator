<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="授業演習"/>
<c:set var="screenStylesheet" value="/css/student/exercise/exercise.css?v=104-menu-fix"/>
<c:set var="screenScript" value="/js/student/exercise/exercise.js?v=104-download-fix"/>
<c:set var="screenUsesCodeMirror" value="true"/>
<c:forEach var="scope" items="${exercisePage.scopes}">
	<c:if test="${scope.exerciseId == exercisePage.selectedExerciseId}"><c:set var="selectedScope" value="${scope}"/></c:if>
</c:forEach>
<c:forEach var="entry" items="${exercisePage.entries}">
	<c:if test="${entry.entryId == exercisePage.selectedEntryId}"><c:set var="selectedEntry" value="${entry}"/></c:if>
</c:forEach>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div id="studentExercisePage" class="container"
	data-exercise-id="<c:out value='${exercisePage.selectedExerciseId}'/>"
	data-root-name="<c:out value='${exerciseRootName}'/>"
	data-multiple-scopes="<c:out value='${exercisePage.scopes.size() > 1}'/>"
	data-entry-id="<c:out value='${exercisePage.selectedEntryId}'/>"
	data-version="<c:out value='${empty selectedScope ? 0 : selectedScope.version}'/>"
	data-editable="<c:out value='${empty selectedScope or selectedScope.state.editable}'/>"
	data-file-selected="<c:out value='${not empty selectedEntry and selectedEntry.type.value == \"file\"}'/>"
	data-base-url="<c:url value='/student/exercise'/>"
	data-preferences-url="<c:url value='/student/editor/preferences'/>"
	data-editor-font-size-px="<c:out value='${editorPreferences.fontSizePx}'/>"
	data-editor-line-wrapping="<c:out value='${editorPreferences.lineWrapping}'/>"
	data-editor-indent-width="<c:out value='${editorPreferences.indentWidth}'/>"
	data-editor-theme="<c:out value='${editorPreferences.themeValue}'/>">
	<input id="csrfToken" type="hidden" value="<c:out value='${csrfToken}'/>">
	<div id="exerciseFeedback" class="mb-3" aria-live="polite"></div>
	<noscript><p class="alert alert-warning">編集・保存・実行・ダウンロードにはJavaScriptを有効にしてください。</p></noscript>
	<section class="sample-section lesson-hero-section mb-4">
		<div class="hero-copy">
			<span class="hero-kicker">Class Exercises</span>
			<h1 class="section-title mb-3">授業演習</h1>
			<p class="hero-description mb-0">授業演習では、提出課題とは別に、練習用のコードをファイルごとに管理しながら学習できます。</p>
		</div>
		<div class="hero-status-card">
			<div class="status-label">ファイル保存状態</div>
			<div id="exerciseSaveStatus" class="status-value"><c:out value="${empty selectedEntry ? 'ファイル未選択' : '最終保存を表示中'}"/></div>
			<p class="status-meta mb-0">最終保存のみ保持します。30秒ごとのコードログ収集・提出・評価は行いません。実行だけではコードを保存しません。</p>
		</div>
	</section>
	<div class="row g-4 align-items-start">
		<div class="col-xl-4">
			<section class="sample-section lesson-tree-section sticky-xl-top">
				<div class="section-header-row mb-3"><h2 class="card-title mb-2">演習ファイル</h2></div>
				<div id="exerciseUnificationPanel" <c:if test="${exercisePage.scopes.size() < 2}">hidden</c:if>>
					<p class="small text-muted">以前の作業場所が複数あります。内容を確認して、生徒IDの一つのルートへまとめてください。統合するまでは元の内容を切り替えて確認できます。</p>
					<button id="previewUnificationButton" type="button" class="btn btn-outline-primary mb-3">統合内容を確認</button>
					<label for="exerciseScope" class="form-label">統合前の内容を確認</label>
				<select id="exerciseScope" class="form-select mb-3" <c:if test="${empty exercisePage.scopes}">disabled</c:if>>
					<c:if test="${empty exercisePage.scopes}"><option value="">まだ作成されていません</option></c:if>
					<c:forEach var="scope" items="${exercisePage.scopes}">
						<option value="<c:out value='${scope.exerciseId}'/>" <c:if test="${scope.exerciseId == exercisePage.selectedExerciseId}">selected</c:if>><c:out value="${scope.name}"/></option>
					</c:forEach>
				</select>
				</div>
				<details class="lesson-tool-panel" open>
				<summary>追加</summary>
				<div class="lesson-tree-actions">
					<button id="newFolderButton" type="button" class="btn btn-outline-secondary" <c:if test="${not empty selectedScope and not selectedScope.state.editable}">disabled</c:if>>新しいフォルダ</button>
					<button id="newFileButton" type="button" class="btn btn-outline-primary" <c:if test="${not empty selectedScope and not selectedScope.state.editable}">disabled</c:if>>新しいファイル</button>
					<button id="uploadButton" type="button" class="btn btn-outline-info" aria-label="アップロード" title="アップロード" data-bs-toggle="tooltip" data-bs-animation="false" <c:if test="${not empty selectedScope and not selectedScope.state.editable}">disabled</c:if>><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6"/></svg></button>
				</div>
				</details>
				<p id="exerciseCreationTarget" class="small lesson-explorer-path"></p>
				<details class="lesson-tool-panel" id="exerciseDisplayTools">
				<summary>表示設定<span id="exerciseDisplayStatus" class="lesson-tool-status"></span></summary>
				<div class="lesson-tool-body">
				<div class="lesson-explorer-filters mb-2">
					<label for="exerciseSearch" class="form-label">名前で検索</label>
					<input id="exerciseSearch" type="search" class="form-control" autocomplete="off">
					<p id="exerciseSearchStatus" class="small text-muted mt-1 mb-0" aria-live="polite" hidden></p>
					<label for="exerciseSort" class="form-label mt-2">並び順</label>
					<select id="exerciseSort" class="form-select"><option value="name">名前順</option><option value="updated">更新日時の新しい順</option></select>
				</div>
				<div class="lesson-folder-controls">
					<button id="exerciseExpandAll" class="btn btn-outline-secondary btn-sm" type="button">すべて展開する</button>
					<button id="exerciseCollapseAll" class="btn btn-outline-secondary btn-sm" type="button">すべて折りたたむ</button>
					<button id="exerciseRevealFile" class="btn btn-outline-secondary btn-sm" type="button">編集中のファイルを表示</button>
				</div>
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
						<button id="downloadAllButton" type="button" class="btn btn-outline-secondary btn-sm" disabled title="チェックした項目の保存済み内容を取得します">ダウンロード</button>
						<button id="exerciseMoveSelected" type="button" class="btn btn-outline-secondary btn-sm" disabled>移動</button>
						<button id="exerciseTrashSelected" type="button" class="btn btn-outline-danger btn-sm" disabled>ごみ箱へ</button>
						<button id="exerciseClearSelected" type="button" class="btn btn-outline-secondary btn-sm">選択解除</button>
					</div>
				</div>
				</div>
				</details>
				<p id="emptyExercise" class="small text-muted" <c:if test="${not empty exercisePage.entries}">hidden</c:if>>最初のフォルダまたはファイルを作成してください。</p>
				<div id="exerciseTree" class="lesson-file-tree" aria-label="演習ファイル"></div>
				<details class="lesson-trash mt-3">
					<summary class="lesson-trash-summary">
						<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7"/></svg>
						<span>ごみ箱</span><span id="exerciseTrashCount" class="lesson-trash-count">0</span>
					</summary>
					<div class="lesson-trash-tools">
						<details class="lesson-tool-panel" id="exerciseTrashSelectionPanel">
						<summary>選択操作<span id="exerciseTrashSelectionStatus" class="lesson-tool-status" aria-live="polite"></span></summary>
						<div class="lesson-tool-body">
						<button id="exerciseTrashSelectMode" class="btn btn-outline-secondary btn-sm mb-2" type="button" aria-pressed="false">選択</button>
						<button id="exerciseTrashSelectionFinish" class="btn btn-outline-secondary btn-sm mb-2" type="button" hidden>選択を終了</button>
						<div id="exerciseTrashSelectionBar" hidden>
						<p id="exerciseTrashSelectionCount" class="small mb-1" aria-live="polite">選択0件</p>
						<div class="lesson-selection-actions"><button id="exerciseRestoreSelected" class="btn btn-outline-secondary btn-sm" type="button" disabled>復元</button><button id="exerciseTrashClearSelected" class="btn btn-outline-secondary btn-sm" type="button">選択解除</button></div>
						</div>
						</div>
						</details>
						<label for="exerciseTrashSearch" class="form-label">ごみ箱を名前で検索</label>
						<input id="exerciseTrashSearch" type="search" class="form-control mb-2" autocomplete="off">
						<p id="exerciseTrashVisibleCount" class="small mt-2 mb-0"></p>
					</div>
					<div id="exerciseTrash" class="lesson-file-tree lesson-trash-content"></div>
					<button id="exerciseTrashMore" class="btn btn-outline-secondary btn-sm m-2" type="button" hidden>さらに表示</button>
				</details>
				<c:if test="${not empty selectedScope and not selectedScope.state.editable}">
					<p class="alert alert-warning mt-3 mb-0">この演習領域は閲覧のみ可能です。</p>
				</c:if>
				<div id="exerciseEntries" hidden>
					<c:forEach var="entry" items="${exercisePage.entries}">
						<div data-entry-id="<c:out value='${entry.entryId}'/>" data-parent-id="<c:out value='${entry.parentEntryId}'/>"
							data-type="<c:out value='${entry.type.value}'/>" data-status="<c:out value='${entry.status.value}'/>"
							data-trash-root-entry-id="<c:out value='${entry.trashRootEntryId}'/>" data-trashed-at="<c:out value='${entry.trashedAt}'/>"
							data-updated-at="<c:out value='${entry.updatedAt}'/>"
							data-name="<c:out value='${entry.name}'/>" data-path="<c:out value='${entry.path}'/>"></div>
					</c:forEach>
				</div>
			</section>
		</div>
		<div class="col-xl-8">
			<section class="sample-section lesson-workspace-section">
				<div class="lesson-toolbar">
					<div class="toolbar-left"><span id="currentPath" class="task-badge"><c:choose><c:when test="${not empty selectedEntry}">Path: <c:out value="${exerciseRootName}"/>/<c:out value="${selectedEntry.path}"/></c:when><c:otherwise>ファイルを選択してください</c:otherwise></c:choose></span></div>
					<div class="button-group">
						<button id="downloadButton" class="btn btn-outline-secondary" type="button" disabled title="編集中のコード（未保存の変更を含む）を取得します"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/></svg>ダウンロード</button>
						<button id="saveButton" class="btn btn-secondary" type="button" disabled><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 3h13l4 4v14H3V3h1zm2 0v6h11V3M6 21v-8h12v8"/></svg>保存</button>
						<button id="runButton" class="btn btn-primary" type="button" disabled><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m8 5 11 7-11 7V5z"/></svg>実行</button>
					</div>
				</div>
				<div class="lesson-file-tab-row">
					<span class="lesson-file-tab is-active"><c:out value="${empty selectedEntry ? '未選択' : selectedEntry.name}"/></span>
					<button class="btn btn-outline-secondary btn-sm lesson-settings-button" type="button" aria-label="エディター設定"
						data-bs-toggle="modal" data-bs-target="#editorSettingsModal" aria-haspopup="dialog"><span class="lesson-settings-icon" aria-hidden="true">⚙</span></button>
				</div>
				<div class="editor-shell lesson-editor-shell">
					<textarea id="codeEditor" class="editor-textarea" spellcheck="false"><c:if test="${selectedEntry.type.value == 'file'}"><c:out value="${selectedEntry.content}"/></c:if></textarea>
				</div>
				<div class="lesson-status-row">
					<p id="exerciseEditorMessage" class="editor-message mb-0" role="status"><c:out value="${not empty selectedEntry and selectedEntry.type.value == 'file' ? '保存状態: 保存済みです。' : 'ツリーからファイルを選択してください。'}"/></p>
					<div class="lesson-status-right"><span class="lesson-language-chip">Python 3.12</span></div>
				</div>
			</section>
			<%@ include file="/WEB-INF/student/shared/execution-terminal.jspf" %>
				<c:if test="${not empty exercisePage.latestExecution}">
					<div id="savedExecution" hidden data-id="<c:out value='${exercisePage.latestExecution.executionId}'/>"
						data-executed-at="<c:out value='${exercisePage.latestExecution.executedAt}'/>"
						data-status="<c:out value='${exercisePage.latestExecution.result.status}'/>"
						data-output-truncated="<c:out value='${exercisePage.latestExecution.result.standardOutputTruncated}'/>"
						data-error-truncated="<c:out value='${exercisePage.latestExecution.result.standardErrorTruncated}'/>"
						data-error-code="<c:out value='${exercisePage.latestExecution.result.errorCode}'/>">
						<pre data-stream="stdin"><c:out value="${exercisePage.latestExecution.standardInput}"/></pre>
						<pre data-stream="stdout"><c:out value="${exercisePage.latestExecution.result.standardOutput}"/></pre>
						<pre data-stream="stderr"><c:out value="${exercisePage.latestExecution.result.standardError}"/></pre>
					</div>
				</c:if>
		</div>
	</div>
</div>
<div class="modal fade" id="unifyExerciseModal" tabindex="-1" aria-labelledby="unifyExerciseTitle" aria-hidden="true">
	<div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 id="unifyExerciseTitle" class="modal-title h5 mb-0">一つのルートへ統合</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div id="unifyExerciseFeedback" aria-live="polite"></div>
			<p>コード・ごみ箱・実行履歴・配信元を保持してまとめます。同名の項目は以下の別名で両方を残し、フォルダ配下もまとめて移します。上書きや結合はしません。</p>
			<p id="unifyExerciseSummary"></p>
			<ul id="unifyExercisePreview" class="unify-exercise-preview"></ul>
			<p class="small text-muted">編集中の未保存コードと実行結果は保持します。統合内容が更新された場合は、もう一度確認が必要です。</p>
		</div>
		<div class="modal-footer"><button class="btn btn-outline-secondary" type="button" data-bs-dismiss="modal">キャンセル</button><button id="unifyExerciseConfirmButton" class="btn btn-primary" type="button" disabled>確認した内容で統合</button></div>
	</div></div>
</div>
<div class="modal fade" id="createEntryModal" tabindex="-1" aria-labelledby="createEntryTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-centered"><form id="createEntryForm" class="modal-content">
		<div class="modal-header"><h2 id="createEntryTitle" class="modal-title h5 mb-0">新しいファイル</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div id="createEntryFeedback" aria-live="polite"></div>
			<label for="parentEntry" class="form-label">作成先</label><select id="parentEntry" class="form-select mb-3"></select>
			<label for="entryName" class="form-label">名前</label>
			<div class="input-group"><input id="entryName" class="form-control" required autocomplete="off" aria-describedby="entryNameHelp"><span id="entryNameExtension" class="input-group-text d-none" hidden>.py</span></div>
			<p id="entryNameHelp" class="small text-muted mt-2 mb-0">1〜255文字。使えない文字：/（スラッシュ）、\（バックスラッシュ）、改行、タブなど。空白だけの名前も使えません。「.」だけ・「..」だけの名前は使えません。</p>
		</div>
		<div class="modal-footer"><button class="btn btn-outline-secondary" type="button" data-bs-dismiss="modal">戻る</button><button id="createEntryButton" class="btn btn-primary" type="submit">作成</button></div>
	</form></div>
</div>
<div class="modal fade" id="organizeEntryModal" tabindex="-1" aria-labelledby="organizeEntryTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-centered"><form id="organizeEntryForm" class="modal-content">
		<div class="modal-header"><h2 id="organizeEntryTitle" class="modal-title h5 mb-0">名前変更</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div id="organizeEntryFeedback" aria-live="polite"></div>
			<p id="organizeEntryPath" class="small text-muted"></p>
			<p id="organizeRestoreHelp" class="small text-muted" hidden></p>
			<div id="organizeDestination">
				<label class="form-label" id="organizeFolderLabel">移動先</label>
				<input id="organizeParentEntry" type="hidden">
				<div id="organizeFolderTree" class="upload-folder-tree mb-3" role="group" aria-labelledby="organizeFolderLabel"></div>
			</div>
			<label for="organizeEntryName" class="form-label">名前</label>
			<div class="input-group"><input id="organizeEntryName" class="form-control" required autocomplete="off" aria-describedby="organizeEntryHelp"><span id="organizeNameExtension" class="input-group-text d-none" hidden>.py</span></div>
			<p id="organizeEntryHelp" class="small text-muted mt-2 mb-0"></p>
			<p class="small text-muted mt-2 mb-0">同名の項目は上書きしません。重なる場合は別の名前を入力してください。</p>
		</div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button id="organizeEntryConfirmButton" type="submit" class="btn btn-primary">変更する</button></div>
	</form></div>
</div>
<div class="modal fade" id="exerciseBatchModal" tabindex="-1" aria-labelledby="exerciseBatchTitle" aria-hidden="true">
	<div class="modal-dialog modal-lg modal-dialog-scrollable"><form id="exerciseBatchForm" class="modal-content">
		<div class="modal-header"><h2 id="exerciseBatchTitle" class="modal-title h5 mb-0">選択した項目の操作</h2><button class="btn-close" type="button" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body"><div id="exerciseBatchFeedback" aria-live="polite"></div><p id="exerciseBatchHelp" class="small"></p>
			<div id="exerciseBatchDestination"><input id="exerciseBatchParent" type="hidden"><p id="exerciseBatchDestinationPath" class="small lesson-explorer-path" aria-live="polite"></p><div id="exerciseBatchFolderTree" class="move-entry-tree" role="group" aria-label="移動先フォルダ"></div></div>
			<div id="exerciseBatchItems"></div>
		</div>
		<div class="modal-footer"><button class="btn btn-outline-secondary" type="button" data-bs-dismiss="modal">キャンセル</button><button id="exerciseBatchConfirm" class="btn btn-primary" type="submit">移動</button></div>
	</form></div>
</div>
<div class="modal fade upload-entry-modal" id="uploadEntryModal" tabindex="-1" aria-labelledby="uploadEntryModalLabel" aria-hidden="true">
	<div class="modal-dialog modal-xl upload-entry-modal-dialog modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="uploadEntryModalLabel">ファイル・フォルダをアップロード</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<div id="uploadFeedback" aria-live="polite"></div>
			<p>複数ファイルまたはフォルダを選択し、追加先フォルダへ取り込みます。</p>
			<div class="upload-entry-layout">
				<section class="upload-entry-file-panel">
					<h3 class="upload-entry-heading">1. ファイル/フォルダ選択</h3>
					<div class="upload-entry-source-actions"><button id="uploadSelectFilesButton" class="btn btn-outline-primary" type="button"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 3h9l5 5v13H5zM14 3v5h5M12 12v6m-3-3h6"/></svg>ファイルを選択</button><button id="uploadSelectFolderButton" class="btn btn-outline-secondary" type="button"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6V4h7l2 3h9v13H3zM14 12v6m-3-3h6"/></svg>フォルダを選択</button></div>
					<input id="uploadSourceFile" class="d-none" type="file" accept=".py" multiple>
					<input id="uploadSourceDirectory" class="d-none" type="file" multiple webkitdirectory directory>
					<p id="uploadFileName" class="upload-entry-file-name" role="status">未選択</p>
					<ul id="uploadFilePreviewList" class="upload-file-preview-list" aria-label="選択したファイル一覧"></ul>
					<p class="small text-muted mt-2">UTF-8の.pyのみ。1ファイル64 KiB・100ファイル・合計1 MiB以内。同名は上書きしません。新規フォルダ名にドットは使用できません。</p>
				</section>
				<section class="upload-entry-folder-panel"><h3 class="upload-entry-heading">2. 追加先フォルダ</h3><p class="upload-entry-help">追加先のフォルダをクリックしてください。</p><input id="uploadParentEntry" type="hidden"><div id="uploadFolderTree" class="upload-folder-tree" role="group" aria-label="追加先フォルダ"></div></section>
			</div>
			<section id="uploadResolutionPanel" class="mt-3" hidden><h3 class="upload-entry-heading">3. 追加内容の確認</h3><div id="uploadResolutionItems"></div></section>
		</div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button id="uploadEntryConfirmButton" type="button" class="btn btn-primary" disabled>アップロードして追加</button></div>
	</div></div>
</div>
<%@ include file="/WEB-INF/student/shared/editor-settings.jspf" %>
<script src="https://cdn.jsdelivr.net/npm/codemirror@5.65.16/lib/codemirror.js"></script>
<script src="https://cdn.jsdelivr.net/npm/codemirror@5.65.16/mode/python/python.js"></script>
<script src="<c:url value='/js/student/shared/code-editor.js'/>" defer></script>
<script src="<c:url value='/js/student/shared/editor-settings.js'/>" defer></script>
<script src="<c:url value='/js/student/shared/explorer-menus.js'/>?v=104-menu-fix" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
