<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5" id="teacherSurveyReview" data-endpoint="<c:url value='/teacher/surveys'/>">
  <div class="container-fluid px-4">
    <section class="history-hero mb-4">
      <div><span class="hero-kicker">Survey Results</span><h1 class="section-title mb-2">アンケート結果確認</h1><p class="hero-description mb-0">研究に同意した生徒のアンケート回答結果を確認できます。</p></div>
      <div class="hero-stats">
        <div class="stat-card"><div class="stat-label">回答数</div><div class="stat-value" id="totalResponses">0</div></div>
        <div class="stat-card"><div class="stat-label">回答完了</div><div class="stat-value" id="completedResponses">0</div></div>
        <div class="stat-card"><div class="stat-label">研究同意</div><div class="stat-value" id="consentedResponses">0</div></div>
      </div>
    </section>
    <div id="surveyAlert" role="alert"></div>
    <section class="panel-card mb-4 summary-section">
      <div class="d-flex align-items-center mb-3 summary-header-row"><span class="hero-kicker me-2 summary-kicker">Survey</span><h2 class="summary-heading mb-0">アンケートサマリー</h2></div>
      <div class="summary-layout">
        <div class="summary-controls">
          <div class="mb-3"><label class="form-label" for="summarySchool">学校</label><select class="form-select form-select-sm" id="summarySchool"><option value="">すべて</option></select></div>
          <div class="mb-3"><label class="form-label" for="summaryClass">クラス</label><select class="form-select form-select-sm" id="summaryClass"><option value="">すべて</option></select></div>
          <div class="mb-4"><label class="form-label" for="summaryTask">課題</label><select class="form-select form-select-sm" id="summaryTask"><option value="">すべて</option></select></div>
          <div class="summary-stat-row mb-3">
            <div class="summary-stat-box summary-stat-total"><div class="ssb-count" id="summaryTotal">0</div><div class="ssb-label">対象件数</div></div>
            <div class="summary-stat-box summary-stat-pending"><div class="ssb-count" id="summaryPending">0</div><div class="ssb-label">未完了</div></div>
            <div class="summary-stat-box summary-stat-complete"><div class="ssb-count" id="summaryComplete">0</div><div class="ssb-label">回答完了</div></div>
          </div>
          <div class="d-flex justify-content-between mb-1"><span class="completion-label">完了率</span><span class="completion-pct" id="summaryCompletionPct">0%</span></div>
          <div class="progress"><div class="progress-bar" id="summaryCompletionBar" role="progressbar" aria-label="回答完了率" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div></div>
        </div>
        <div class="summary-chart-wrap"><div class="summary-text-card">
          <div class="summary-text-kicker">Survey Averages</div>
          <div class="summary-metric-bars">
            <div class="summary-bar-row"><div class="summary-bar-head"><span class="metric-label">思考力・判断力・表現力（妥当性）</span><strong class="metric-value" id="summaryThinkingValidityAvg">-</strong></div><div class="summary-bar-track"><div class="summary-bar-fill is-thinking-validity" id="summaryThinkingValidityBar"></div></div></div>
            <div class="summary-bar-row"><div class="summary-bar-head"><span class="metric-label">学習に取り組む態度（妥当性）</span><strong class="metric-value" id="summaryAttitudeValidityAvg">-</strong></div><div class="summary-bar-track"><div class="summary-bar-fill is-attitude-validity" id="summaryAttitudeValidityBar"></div></div></div>
            <div class="summary-bar-row"><div class="summary-bar-head"><span class="metric-label">AI評価への抵抗感</span><strong class="metric-value" id="summaryResistanceAvg">-</strong></div><div class="summary-bar-track"><div class="summary-bar-fill is-resistance" id="summaryResistanceBar"></div></div></div>
            <div class="summary-bar-row"><div class="summary-bar-head"><span class="metric-label">システムの操作性</span><strong class="metric-value" id="summaryUsabilityAvg">-</strong></div><div class="summary-bar-track"><div class="summary-bar-fill is-usability" id="summaryUsabilityBar"></div></div></div>
          </div>
          <p class="summary-text-description mb-0">設問1～4の平均値を棒グラフで表示しています。妥当性、AI評価への抵抗感、システムの操作性はいずれも5点満点です（設問3は 1: 抵抗が強い / 5: 抵抗がない）。</p>
        </div></div>
      </div>
    </section>
    <section class="panel-card mb-4">
      <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2"><h2 class="summary-heading mb-0">アンケート一覧フィルタ</h2><div class="action-buttons">
        <button type="button" class="btn btn-outline-secondary btn-sm" id="refreshSurvey">更新</button><button type="button" class="btn btn-primary btn-sm" id="csvSurvey">CSVエクスポート</button><button type="button" class="btn btn-outline-primary btn-sm" id="textSurvey">記述内容ダウンロード</button>
      </div></div>
      <form class="filter-grid" id="surveyFilters">
        <div><label class="form-label" for="searchInput">検索</label><input type="text" class="form-control form-control-sm" id="searchInput" name="search" maxlength="100" placeholder="生徒ID / 課題名で検索"></div>
        <div><label class="form-label" for="schoolFilter">学校</label><select class="form-select form-select-sm" id="schoolFilter" name="schoolId"><option value="">すべて</option></select></div>
        <div><label class="form-label" for="classFilter">クラス</label><select class="form-select form-select-sm" id="classFilter" name="classroomId"><option value="">すべて</option></select></div>
        <div><label class="form-label" for="taskFilter">課題名</label><select class="form-select form-select-sm" id="taskFilter" name="taskId"><option value="">すべて</option></select></div>
        <div><label class="form-label" for="difficultyFilter">難易度</label><select class="form-select form-select-sm" id="difficultyFilter" name="difficulty"><option value="">すべて</option><option value="beginner">初級</option><option value="intermediate">中級</option><option value="advanced">上級</option><option value="none">未設定</option></select></div>
        <div><label class="form-label" for="completionFilter">状態</label><select class="form-select form-select-sm" id="completionFilter" name="completion"><option value="">すべて</option><option value="submitted">完了</option><option value="in_progress">下書き</option></select></div>
        <div><label class="form-label" for="consentFilter">研究同意</label><select class="form-select form-select-sm" id="consentFilter" name="consent"><option value="">すべて</option><option value="agreed">同意</option><option value="not_agreed">未同意</option><option value="unconfirmed">未確認</option></select></div>
        <div><label class="form-label" for="thinkingValidityExprFilter">思判表・妥当性条件</label><input class="form-control form-control-sm" id="thinkingValidityExprFilter" name="q1ThinkingValidity" maxlength="100" placeholder="例: =4, >3"></div>
        <div><label class="form-label" for="thinkingExprFilter">思判表・自己評価条件</label><input class="form-control form-control-sm" id="thinkingExprFilter" name="q1ThinkingScore" maxlength="100" placeholder="例: =4, >3"></div>
        <div><label class="form-label" for="attitudeValidityExprFilter">態度・妥当性条件</label><input class="form-control form-control-sm" id="attitudeValidityExprFilter" name="q2AttitudeValidity" maxlength="100" placeholder="例: =4, >3"></div>
        <div><label class="form-label" for="attitudeExprFilter">態度・自己評価条件</label><input class="form-control form-control-sm" id="attitudeExprFilter" name="q2AttitudeScore" maxlength="100" placeholder="例: =4, >3"></div>
        <div><label class="form-label" for="resistanceExprFilter">過程抵抗条件</label><input class="form-control form-control-sm" id="resistanceExprFilter" name="q3ProcessResistanceScore" maxlength="100" placeholder="例: =4, >3"></div>
        <div><label class="form-label" for="usabilityExprFilter">操作性条件</label><input class="form-control form-control-sm" id="usabilityExprFilter" name="q4UsabilityScore" maxlength="100" placeholder="例: =4, >3"></div>
      </form>
      <p class="small text-muted mt-2 mb-0">現在研究に同意している生徒の保存済み回答のみです。研究用出力は内部IDによる仮名化データです。自由記述に個人情報が含まれていないか確認してください。</p>
    </section>
    <section class="panel-card"><div class="table-responsive" role="region" aria-label="アンケート結果一覧（横にスクロールできます）" tabindex="0">
      <table class="table table-hover align-middle" id="surveyTable"><thead class="table-light"><tr>
        <th class="sortable" data-sort-key="studentId">生徒ID</th><th class="sortable" data-sort-key="school">学校</th><th class="sortable" data-sort-key="className">クラス</th><th class="sortable" data-sort-key="taskTitle">課題名</th><th class="sortable" data-sort-key="difficulty">難易度</th>
        <th class="sortable text-center" data-sort-key="thinkingValidity">思判表<br><small class="text-muted fw-normal">妥当性</small></th><th class="sortable text-center" data-sort-key="thinkingScore">思判表<br><small class="text-muted fw-normal">自己</small></th>
        <th class="sortable text-center" data-sort-key="attitudeValidity">態度<br><small class="text-muted fw-normal">妥当性</small></th><th class="sortable text-center" data-sort-key="attitudeScore">態度<br><small class="text-muted fw-normal">自己</small></th>
        <th class="sortable text-center" data-sort-key="resistanceScore">過程抵抗</th><th class="sortable text-center" data-sort-key="usabilityScore">操作性</th><th class="sortable" data-sort-key="submittedAt">回答日時</th><th class="sortable" data-sort-key="completionStatus">状態</th><th class="sortable" data-sort-key="consentStatus">研究同意</th><th>詳細</th>
      </tr></thead><tbody id="surveyTableBody"></tbody></table>
    </div></section>
  </div>
</div>
<div class="modal fade" id="surveyDetailModal" tabindex="-1" aria-labelledby="surveyDetailLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg modal-dialog-scrollable"><div class="modal-content">
    <div class="modal-header"><h2 class="modal-title fs-5" id="surveyDetailLabel">アンケート結果詳細</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
    <div class="modal-body"><div id="surveyDetailAlert" role="alert"></div><p class="detail-meta mb-4" id="surveyDetailMeta"></p><div id="modalResponseContent"></div></div>
    <div class="modal-footer"><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button></div>
  </div></div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
