<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5" id="teacherExerciseReview" data-endpoint="<c:url value='/teacher/exercises'/>">
  <div class="container-fluid px-4">
    <section class="exercise-hero mb-4">
      <div>
        <span class="hero-kicker">Exercise Monitor</span>
        <h1 class="section-title mb-2">授業演習コード確認</h1>
        <p class="hero-description mb-0">学校・クラスごとに授業演習データを一覧表示し、生徒ごとのフォルダ構成、代表Path、最終保存コードを確認できます。</p>
      </div>
      <div class="hero-stats">
        <div class="stat-card"><div class="stat-label">表示件数</div><div class="stat-value" id="visibleCount">0</div></div>
        <div class="stat-card"><div class="stat-label">ファイル数</div><div class="stat-value" id="visibleFileCount">0</div></div>
        <div class="stat-card"><div class="stat-label">最終更新</div><div class="stat-value" id="latestUpdatedAt">-</div></div>
      </div>
    </section>
    <div id="exerciseAlert" role="alert"></div>
    <section class="panel-card mb-4">
      <div class="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
        <div><span class="hero-kicker mb-1 d-inline-block">Exercise Filters</span><h2 class="summary-heading mb-0">授業演習フィルタ</h2></div>
        <div class="action-buttons">
          <button class="btn btn-outline-secondary btn-sm" type="button" id="refreshExercise">更新</button>
          <button class="btn btn-outline-primary btn-sm" type="button" id="bulkExercise">ファイルを一括ダウンロード</button>
          <button class="btn btn-primary btn-sm" type="button" id="csvExercise">CSVエクスポート</button>
        </div>
      </div>
      <form class="filter-grid" id="exerciseFilters">
        <div><label class="form-label" for="searchInput">生徒ID</label><input type="text" class="form-control form-control-sm" id="searchInput" name="search" maxlength="100" placeholder="生徒IDで検索"></div>
        <div><label class="form-label" for="filterSchool">学校</label><select class="form-select form-select-sm" id="filterSchool" name="schoolId"><option value="">すべて</option></select></div>
        <div><label class="form-label" for="filterClass">クラス</label><select class="form-select form-select-sm" id="filterClass" name="classroomId"><option value="">すべて</option></select></div>
        <div><label class="form-label" for="filterConsent">研究同意</label><select class="form-select form-select-sm" id="filterConsent" name="consent"><option value="">すべて</option><option value="agreed">同意</option><option value="unconfirmed">未確認</option><option value="not_agreed">不同意</option></select></div>
      </form>
      <p class="small text-muted mt-2 mb-0">CSVは出力時の最新同意が「同意」の行だけです。生徒IDを含む教師確認用で、研究用匿名化出力ではありません。</p>
    </section>
    <div class="row g-4 align-items-start"><div class="col-12">
      <section class="panel-card h-100">
        <div class="table-responsive exercise-table-wrap">
          <table class="table table-hover align-middle" id="exerciseTable">
            <thead class="table-light"><tr>
              <th class="sortable" data-sort-key="studentId" tabindex="0" aria-sort="none">生徒ID</th>
              <th class="sortable" data-sort-key="school" tabindex="0" aria-sort="none">学校</th>
              <th class="sortable" data-sort-key="className" tabindex="0" aria-sort="none">クラス</th>
              <th class="sortable" data-sort-key="fileCount" tabindex="0" aria-sort="none">ファイル数</th>
              <th class="sortable" data-sort-key="updatedAt" tabindex="0" aria-sort="descending">最終更新</th>
              <th class="sortable" data-sort-key="consent" tabindex="0" aria-sort="none">研究同意</th><th>詳細</th>
            </tr></thead>
            <tbody id="exerciseTableBody"><tr><td colspan="7">読み込み中です。</td></tr></tbody>
          </table>
        </div>
      </section>
    </div></div>
  </div>
</div>
<div class="modal fade exercise-detail-modal" id="exerciseDetailModal" tabindex="-1" aria-labelledby="exerciseDetailModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-fullscreen modal-dialog-scrollable"><div class="modal-content">
    <div class="modal-header">
      <h5 class="modal-title" id="exerciseDetailModalLabel">授業演習詳細</h5>
      <div class="modal-header-tools"><div class="modal-header-actions">
        <button class="btn btn-outline-primary btn-sm" type="button" id="downloadExerciseFile" disabled>ファイルをダウンロード</button>
        <button class="btn btn-primary btn-sm" type="button" id="downloadExerciseZip" disabled>フォルダをZIPダウンロード</button>
      </div><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
    </div>
    <div class="modal-body">
      <div id="exerciseDetailAlert" role="alert"></div>
      <div class="exercise-detail-top-row mb-3"><p class="detail-meta mb-0" id="detailMeta">-</p><div class="detail-chip-group"><span class="detail-chip detail-chip-muted" id="detailFileCountChip">-</span></div></div>
      <section class="exercise-summary-card mb-3">
        <div class="exercise-summary-main"><div class="exercise-summary-kicker">Selected File</div><div class="exercise-summary-title" id="detailStudentId">-</div><div class="exercise-summary-subtitle" id="detailStudentMeta">-</div></div>
        <div class="exercise-summary-stats">
          <article class="exercise-summary-stat"><div class="exercise-summary-label">ファイル名</div><div class="exercise-summary-value" id="detailFileName">-</div></article>
          <article class="exercise-summary-stat"><div class="exercise-summary-label">保存日時</div><div class="exercise-summary-value" id="detailFileUpdatedAt">-</div></article>
          <article class="exercise-summary-stat exercise-summary-stat-wide"><div class="exercise-summary-label">Path</div><div class="exercise-summary-value exercise-summary-path" id="detailFilePath">-</div></article>
        </div>
      </section>
      <div class="exercise-detail-grid">
        <section class="detail-card">
          <div class="d-flex justify-content-between align-items-center mb-3 gap-2 flex-wrap"><h3 class="detail-title mb-0">フォルダ・ファイル構成</h3><span class="small text-muted">最終保存時点のみ表示</span></div>
          <div id="exerciseTree" class="exercise-tree" aria-label="授業演習フォルダツリー"></div>
        </section>
        <section class="detail-card exercise-code-card">
          <div class="d-flex justify-content-between align-items-center mb-3 gap-2 flex-wrap"><h3 class="detail-title mb-0">最終保存コード</h3></div>
          <textarea id="exerciseCodeViewer" class="code-source" spellcheck="false" readonly aria-label="最終保存コード"></textarea>
        </section>
      </div>
    </div>
  </div></div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
