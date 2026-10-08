<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5" id="teacherReview" data-endpoint="<c:url value='/teacher/submissions'/>"
	data-selected-submission="<c:out value='${reviewSelectedSubmission}'/>" data-csrf-token="<c:out value='${csrfToken}'/>">
	<div class="container-fluid px-4">
		<section class="submission-hero mb-4">
			<div><span class="hero-kicker">Submission Review</span>
				<h1 class="section-title mb-2">提出課題確認</h1>
				<p class="hero-description mb-0">生徒の提出課題一覧を確認し、提出コードと入出力チェック結果を詳細モーダルで連続確認できます。</p>
			</div>
			<div class="hero-stats">
				<div class="stat-card"><div class="stat-label">表示件数</div><div class="stat-value" id="visibleCount">0</div></div>
				<div class="stat-card"><div class="stat-label">全件一致</div><div class="stat-value" id="fullMatchCount">0</div></div>
				<div class="stat-card"><div class="stat-label">平均一致率</div><div class="stat-value" id="avgMatchRate">0%</div></div>
			</div>
		</section>
		<div id="reviewAlert" role="alert"></div>
		<section class="panel-card mb-4">
			<div class="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
				<div><span class="hero-kicker mb-1 d-inline-block">Submission Filters</span><h2 class="summary-heading mb-0">提出一覧フィルタ</h2></div>
				<div class="action-buttons">
					<button id="refreshSubmissions" class="btn btn-outline-secondary btn-sm" type="button">更新</button>
					<button id="submissionZip" class="btn btn-outline-primary btn-sm" type="button" disabled>ファイルを一括ダウンロード</button>
					<button id="submissionCsv" class="btn btn-primary btn-sm" type="button" disabled title="最新同意が同意の行のみ。教師確認用で研究用匿名化出力ではありません。">CSVエクスポート</button>
				</div>
			</div>
			<form id="reviewFilters" class="filter-grid">
				<div><label class="form-label" for="searchInput">検索</label><input name="search" id="searchInput" class="form-control form-control-sm" maxlength="100" placeholder="生徒ID / 課題名で検索"></div>
				<div><label class="form-label" for="filterSchool">学校</label><select name="schoolId" id="filterSchool" class="form-select form-select-sm"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="filterClass">クラス</label><select name="classroomId" id="filterClass" class="form-select form-select-sm"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="filterTask">課題</label><select name="taskId" id="filterTask" class="form-select form-select-sm"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="filterLevel">難易度</label><select name="difficulty" id="filterLevel" class="form-select form-select-sm"><option value="">すべて</option><option value="beginner">初級</option><option value="intermediate">中級</option><option value="advanced">上級</option><option value="none">未設定</option></select></div>
				<div><label class="form-label" for="filterConsent">研究同意</label><select name="consent" id="filterConsent" class="form-select form-select-sm"><option value="">すべて</option><option value="agreed">同意</option><option value="unconfirmed">未確認</option><option value="not_agreed">不同意</option></select></div>
			</form>
		</section>
		<section class="panel-card">
			<div class="table-responsive">
				<table class="table table-hover align-middle" id="submissionTable">
					<thead class="table-light"><tr>
						<th class="sortable" data-sort-key="student" tabindex="0" aria-sort="none">生徒ID</th>
						<th class="sortable" data-sort-key="school" tabindex="0" aria-sort="none">学校</th>
						<th class="sortable" data-sort-key="class" tabindex="0" aria-sort="none">クラス</th>
						<th class="sortable" data-sort-key="task" tabindex="0" aria-sort="none">課題名</th>
						<th class="sortable" data-sort-key="difficulty" tabindex="0" aria-sort="none">難易度</th>
						<th class="sortable" data-sort-key="match" tabindex="0" aria-sort="none">一致件数</th>
						<th class="sortable" data-sort-key="submitted" tabindex="0" aria-sort="descending">提出日時</th>
						<th class="sortable" data-sort-key="consent" tabindex="0" aria-sort="none">研究同意</th><th>詳細</th>
					</tr></thead><tbody id="submissionTableBody"></tbody>
				</table>
			</div>
		</section>
	</div>
</div>
<div class="modal fade" id="submissionDetailModal" tabindex="-1" aria-labelledby="submissionDetailModalLabel" aria-hidden="true">
	<div class="modal-dialog modal-fullscreen modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header">
			<h2 class="modal-title fs-5" id="submissionDetailModalLabel">提出課題詳細</h2>
			<div class="d-flex align-items-center gap-2 ms-auto"><button type="button" id="submissionFile" class="btn btn-outline-primary btn-sm">ファイルをダウンロード</button><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		</div>
		<div class="modal-body">
			<div class="mb-3"><p class="detail-meta mb-0" id="detailMeta">-</p></div>
			<section class="detail-card mb-3">
				<div class="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-2"><h3 class="detail-title mb-0">入出力チェック結果</h3><div class="match-summary" id="ioMatchSummary"></div></div>
				<div class="table-responsive"><table class="table io-result-table mb-0"><thead><tr><th>入力</th><th>期待する出力</th><th>実際の出力</th><th class="text-center">結果</th></tr></thead><tbody id="ioResultTableBody"></tbody></table></div>
			</section>
			<div class="detail-grid">
				<section class="detail-card">
					<div class="d-flex justify-content-between align-items-center mb-2 gap-2 flex-wrap"><h3 class="detail-title mb-0">提出コード（読み取り専用）</h3></div>
					<textarea id="submittedCodeView" class="form-control code-source" aria-label="提出コード（読取専用）" readonly spellcheck="false" rows="10"></textarea>
				</section>
				<section class="detail-card">
					<div class="d-flex justify-content-between align-items-center mb-2 gap-2 flex-wrap"><h3 class="detail-title mb-0">動作確認用コード（編集・実行可）</h3><span class="badge text-bg-warning">提出データは変更されません</span></div>
					<textarea id="workCodeEditor" class="form-control code-source" aria-label="動作確認用コード" spellcheck="false" rows="10"></textarea>
					<label class="form-label mt-2" for="previewInput">標準入力</label><textarea id="previewInput" class="form-control form-control-sm" rows="2"></textarea>
					<div class="d-flex align-items-center gap-2 mt-2"><button type="button" class="btn btn-primary btn-sm" id="runWorkCodeButton">実行</button><span class="small text-muted">確認用コードのみ実行されます</span></div>
					<div id="previewAlert" role="alert"></div>
					<div class="run-output-wrap mt-2"><div class="output-label">実行結果</div><pre id="workCodeOutput" aria-live="polite">未実行です。</pre></div>
				</section>
			</div>
		</div>
		<div class="modal-footer submission-detail-footer"><div class="small text-muted" id="detailPagerText"></div><div class="d-flex gap-2 detail-footer-actions"><button type="button" class="btn btn-outline-secondary" id="detailPrevButton">戻る</button><button type="button" class="btn btn-outline-secondary" id="detailNextButton">次へ</button></div></div>
	</div></div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
