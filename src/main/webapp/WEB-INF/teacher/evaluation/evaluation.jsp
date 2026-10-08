<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5" id="teacherReview" data-endpoint="<c:url value='/teacher/evaluations'/>"
	data-selected-submission="<c:out value='${reviewSelectedSubmission}'/>" data-selected-evaluation="<c:out value='${reviewSelectedEvaluation}'/>">
	<div class="container-fluid px-4">
		<section class="evaluation-hero mb-4">
			<div><span class="hero-kicker">Evaluation Monitor</span><h1 class="section-title mb-2">評価確認</h1><p class="hero-description mb-0">生徒ごとの評価結果、評価理由、コードログを確認できます。</p></div>
			<div class="hero-stats">
				<div class="stat-card"><div class="stat-label">表示件数</div><div class="stat-value" id="visibleCount">0</div></div>
				<div class="stat-card"><div class="stat-label">思判表 平均</div><div class="stat-value" id="avgThinkingTop">-</div></div>
				<div class="stat-card"><div class="stat-label">態度 平均</div><div class="stat-value" id="avgAttitudeTop">-</div></div>
			</div>
		</section>
		<div id="reviewAlert" role="alert"></div>
		<section class="panel-card mb-4 summary-section">
			<div class="d-flex align-items-center mb-3 summary-header-row"><span class="hero-kicker me-2 summary-kicker">Evaluation</span><h2 class="summary-heading mb-0">評価サマリー</h2></div>
			<div class="summary-layout">
				<div class="summary-controls">
					<c:forEach items="School,Class,Task" var="key"><div class="mb-3"><label class="form-label" for="summary${key}"><c:choose><c:when test="${key == 'School'}">学校</c:when><c:when test="${key == 'Class'}">クラス</c:when><c:otherwise>課題</c:otherwise></c:choose></label><select class="form-select form-select-sm" id="summary${key}"><option value="">すべて</option></select></div></c:forEach>
					<div class="summary-stat-row mb-3">
						<div class="summary-stat-box summary-stat-total"><div class="ssb-count" id="summaryTotal">0</div><div class="ssb-label">対象件数</div></div>
						<div class="summary-stat-box summary-stat-unevaluated"><div class="ssb-count" id="summaryUnevaluated">0</div><div class="ssb-label">未評価</div></div>
						<div class="summary-stat-box summary-stat-evaluated"><div class="ssb-count" id="summaryEvaluated">0</div><div class="ssb-label">評価済み</div></div>
					</div>
					<div class="d-flex justify-content-between mb-1"><span class="completion-label">完了率</span><span class="completion-pct" id="completionPct">0%</span></div>
					<div class="progress"><div class="progress-bar" id="completionBar" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div></div>
				</div>
				<div class="summary-chart-wrap"><div class="summary-text-card"><div class="summary-text-kicker">Score Distribution</div>
					<c:forEach items="thinking,attitude" var="metric"><div class="summary-metric-section summary-metric-${metric}">
						<div class="summary-text-main"><c:choose><c:when test="${metric == 'thinking'}">思考力・判断力・表現力</c:when><c:otherwise>主体的に学習に取り組む態度</c:otherwise></c:choose>（平均：<span id="${metric}Average">-</span>）</div>
						<div class="score-distribution"><c:forEach begin="1" end="5" var="i"><div class="score-row"><span class="score-label">${6-i}</span><div class="score-bar-track"><div class="score-bar-fill" id="${metric}Bar${6-i}"></div></div><span class="score-count" id="${metric}Count${6-i}">0</span></div></c:forEach></div>
					</div></c:forEach>
				</div></div>
			</div>
		</section>
		<section class="panel-card mb-4">
			<div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2"><div class="d-flex align-items-center"><span class="hero-kicker me-2 mb-0">List</span><h2 class="summary-heading mb-0">評価一覧</h2></div>
				<div class="action-buttons"><button class="btn btn-outline-secondary btn-sm" id="refreshEvaluations">更新</button><button class="btn btn-outline-primary btn-sm" id="bulkResults">評価結果を一括ダウンロード</button><button class="btn btn-outline-primary btn-sm" id="bulkLogs">ログを一括ダウンロード</button><button class="btn btn-primary btn-sm" id="exportCsv">CSVエクスポート</button></div>
			</div>
			<form id="evaluationFilters" class="filter-grid">
				<label class="form-label">検索<input class="form-control form-control-sm" name="search" maxlength="100" placeholder="生徒ID / 課題名で検索"></label>
				<label class="form-label">学校<select class="form-select form-select-sm" name="schoolId"><option value="">すべて</option></select></label>
				<label class="form-label">クラス<select class="form-select form-select-sm" name="classroomId"><option value="">すべて</option></select></label>
				<label class="form-label">課題<select class="form-select form-select-sm" name="taskId"><option value="">すべて</option></select></label>
				<label class="form-label">難易度<select class="form-select form-select-sm" name="difficulty"><option value="">すべて</option><option value="beginner">初級</option><option value="intermediate">中級</option><option value="advanced">上級</option><option value="none">未設定</option></select></label>
				<label class="form-label">思判表条件<input class="form-control form-control-sm" name="thinking" maxlength="100" placeholder="例: =4, &gt;3"></label>
				<label class="form-label">態度条件<input class="form-control form-control-sm" name="attitude" maxlength="100" placeholder="例: =4, &gt;3"></label>
				<label class="form-label">研究同意<select class="form-select form-select-sm" name="consent"><option value="">すべて</option><option value="agreed">同意</option><option value="not_agreed">不同意</option><option value="unconfirmed">未確認</option></select></label>
			</form>
			<p class="small text-muted mb-0 mt-2">CSVは最新同意が「同意」の行のみです。生徒ログインIDを含む教師確認用で、研究用匿名化出力ではありません。</p>
		</section>
		<section class="panel-card"><div class="table-responsive"><table class="table table-hover align-middle" id="evaluationTable"><thead class="table-light"><tr>
			<th class="sortable" data-sort="student" tabindex="0">生徒ID</th><th class="sortable" data-sort="school" tabindex="0">学校</th><th class="sortable" data-sort="class" tabindex="0">クラス</th><th class="sortable" data-sort="task" tabindex="0">課題</th><th class="sortable" data-sort="difficulty" tabindex="0">難易度</th><th class="sortable" data-sort="thinking" tabindex="0">思判表</th><th class="sortable" data-sort="attitude" tabindex="0">態度</th><th class="sortable" data-sort="evaluated" tabindex="0">評価日時</th><th class="sortable" data-sort="consent" tabindex="0">研究同意</th><th>詳細</th>
		</tr></thead><tbody id="evaluationRows"></tbody></table></div></section>
	</div>
</div>
<div class="modal fade" id="evaluationDetailModal" tabindex="-1" aria-labelledby="evaluationDetailTitle" aria-hidden="true"><div class="modal-dialog modal-fullscreen modal-dialog-scrollable"><div class="modal-content">
	<div class="modal-header"><h2 class="modal-title fs-5" id="evaluationDetailTitle">評価詳細</h2><div class="evaluation-detail-action-row"><button class="btn btn-outline-primary btn-sm" id="downloadEvaluation">評価結果をダウンロード</button><button class="btn btn-outline-secondary btn-sm" id="downloadLogs">ログをダウンロード</button></div><button class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
	<div class="modal-body"><p class="detail-meta" id="evaluationDetailMeta"></p><div id="detailAlert" role="alert"></div>
		<label class="form-label">評価履歴<select id="evaluationVersions" class="form-select"></select></label><p id="evaluationState"></p><div id="previousEvaluation"></div>
		<div class="teacher-evaluation-detail-section container" id="teacherEvaluationDetailContent">
			<section class="sample-section detail-hero mb-4"><div><span class="hero-kicker">Student Evaluation</span><h1 class="section-title mb-3">評価結果</h1><p class="hero-description">保存済みコードログと実行履歴をもとにした評価結果と根拠を確認できます。</p><button class="btn btn-warning btn-lg" id="openLogs">ログを見る</button></div><div class="hero-meta-card"><div class="meta-label">対象課題</div><div class="meta-value" id="detailTask"></div><p id="detailTaskMetadata"></p><div class="hero-score-row" id="detailScorePills"></div></div></section>
			<section class="sample-section mb-4"><div class="section-heading-row"><div><h2 class="card-title">総合評価</h2><p class="text-muted">2つの観点ごとに、ルーブリックをもとにした総合点を表示します。</p></div><span>5段階評価</span></div><div class="score-panels" id="evaluationResults"></div><p id="feedbackSummary"></p></section>
			<section class="sample-section mb-4"><h2 class="card-title">観点別スコア</h2><p class="text-muted">細かな評価項目ごとの点数です。</p><div class="breakdown-grid" id="evaluationMetrics"></div></section>
			<section class="sample-section"><h2 class="card-title">評価理由</h2><p id="processAnalysis"></p><div class="reason-filters" id="reasonFilters"></div><div id="evaluationReasons"></div></section>
			<div class="d-flex justify-content-center flex-wrap gap-3 mt-4"><button class="btn btn-warning btn-lg px-5" id="openLogsBottom">ログを見る</button></div>
		</div>
		<section id="evaluationLogView" class="container" hidden>
			<section class="sample-section log-hero mb-4"><div><span class="hero-kicker">Student Log</span><h1 class="section-title mb-3">コードログ</h1><p class="hero-description mb-0">保存されたコードの履歴を、変更行を強調しながら時系列で確認できます。前後の状態を見比べて、試行錯誤の流れを確認できます（読取専用）。</p><div class="mt-4"><button class="btn btn-warning btn-lg" id="backToEvaluation">評価結果に戻る</button></div></div>
				<div class="hero-meta-card"><div class="meta-label">対象課題</div><div class="meta-value" id="logTask"></div><p id="logTaskMetadata"></p><div class="hero-status-list"><div class="hero-status-item"><span>保存回数</span><span class="hero-status-pill" id="logSaveCount"></span></div><div class="hero-status-item"><span>実行記録数</span><span class="hero-status-pill accent" id="logRunCount"></span></div></div></div>
			</section>
			<div class="log-layout"><section class="sample-section"><h2 class="card-title">差分ビューアー</h2><p class="text-muted">各保存時点のコードを時系列で確認できます。</p><div class="log-navigation"><button class="btn btn-outline-primary" id="previousLog">前へ</button><span id="logStep"></span><button class="btn btn-outline-primary" id="nextLog">次へ</button></div><p id="codeLogState" class="mt-3"></p><pre class="code-block" id="codeLogSnapshot"></pre><h3 class="mt-3">実行入出力・エラー</h3><pre class="code-block" id="logExecution"></pre></section>
				<aside class="sample-section"><div class="sidebar-switcher mb-3"><button class="sidebar-switch-button is-active" id="showLogTimeline">ログ一覧</button><button class="sidebar-switch-button" id="showLogReasons">評価の理由</button></div>
					<div id="timelinePanel"><h2 class="card-title">ログ一覧</h2><label class="form-label">時点を選択<select id="codeLogTimes" class="form-select"></select></label><div id="logTimeline"></div></div>
					<div id="logReasonsPanel" hidden><h2 class="card-title">評価の理由</h2><div class="reason-filters" id="logReasonFilters"></div><div id="logReasons"></div></div>
				</aside>
			</div>
			<section class="sample-section mt-3"><h3>提出時スナップショット（読取専用）</h3><pre class="code-block" id="evaluationSubmission"></pre></section>
		</section>
	</div>
</div></div></div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
