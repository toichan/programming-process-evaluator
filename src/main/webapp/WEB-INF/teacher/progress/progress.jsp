<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="課題進捗確認機能"/>
<c:set var="screenDesign" value="teacher"/>
<c:set var="screenStylesheet" value="/css/teacher/progress/progress.css"/>
<c:set var="screenScript" value="/js/teacher/progress/progress.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5 teacher-progress-screen" id="teacherProgress"
	data-detail-endpoint="<c:url value='/teacher/progress'/>" data-refresh-interval="20000">
	<div class="container-fluid px-4">
		<section class="history-hero mb-4">
			<div>
				<span class="hero-kicker">Assignment Progress Monitor</span>
				<h1 class="section-title mb-2">課題進捗確認機能</h1>
				<p class="hero-description mb-0">クラス別の進捗、課題別の実施状況、研究同意状態を一覧で確認できます。</p>
			</div>
			<div class="hero-stats">
				<div class="stat-card">
					<div class="stat-label">表示件数</div>
					<div class="stat-value" id="visibleCount">0</div>
				</div>
				<div class="stat-card">
					<div class="stat-label">進行中</div>
					<div class="stat-value" id="activeCount">0</div>
				</div>
				<div class="stat-card">
					<div class="stat-label">提出済み</div>
					<div class="stat-value" id="submittedCount">0</div>
				</div>
			</div>
		</section>

		<section class="panel-card mb-4 summary-section">
			<div class="d-flex align-items-center mb-3 summary-header-row">
				<span class="hero-kicker me-2 summary-kicker">Progress</span>
				<h2 class="summary-heading mb-0">進捗サマリー</h2>
			</div>
			<div class="summary-layout">
				<div class="summary-controls">
					<div class="mb-3">
						<label class="form-label" for="summarySchool">学校</label>
						<select class="form-select form-select-sm" id="summarySchool">
							<option value="すべて" selected>すべて</option>
						</select>
					</div>
					<div class="mb-3">
						<label class="form-label" for="summaryClass">クラス</label>
						<select class="form-select form-select-sm" id="summaryClass">
							<option value="すべて" selected>すべて</option>
						</select>
					</div>
					<div class="mb-4">
						<label class="form-label" for="summaryTask">課題</label>
						<select class="form-select form-select-sm" id="summaryTask">
							<option value="すべて" selected>すべて</option>
						</select>
					</div>
					<div class="summary-stat-row mb-3">
						<div class="summary-stat-box summary-stat-todo">
							<div class="ssb-count" id="summaryTodo">0</div>
							<div class="ssb-label">未着手</div>
						</div>
						<div class="summary-stat-box summary-stat-active">
							<div class="ssb-count" id="summaryActive">0</div>
							<div class="ssb-label">編集中</div>
						</div>
						<div class="summary-stat-box summary-stat-done">
							<div class="ssb-count" id="summaryDone">0</div>
							<div class="ssb-label">提出済み</div>
						</div>
					</div>
					<div>
						<div class="d-flex justify-content-between mb-1">
							<span class="completion-label">完了率</span>
							<span class="completion-pct" id="completionPct">0%</span>
						</div>
						<div class="progress" style="height: 8px; border-radius: 4px;">
							<div class="progress-bar bg-success" id="completionBar" style="width: 0%;"
								role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div>
						</div>
					</div>
				</div>
				<div class="summary-chart-wrap">
					<canvas id="summaryDonut" aria-label="進捗サマリー" role="img"></canvas>
				</div>
			</div>
		</section>

		<section class="panel-card mb-4">
			<div class="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
				<div>
					<span class="hero-kicker mb-1 d-inline-block">Assignment Progress List</span>
					<h2 class="summary-heading mb-0">課題進捗一覧</h2>
				</div>
				<div class="action-buttons">
					<button class="btn btn-outline-secondary btn-sm" type="button" id="refreshProgress">更新</button>
					<button class="btn btn-primary btn-sm" type="button" id="exportProgressCsv">CSVエクスポート</button>
				</div>
			</div>
			<div class="filter-grid">
				<div>
					<label class="form-label" for="searchInput">検索</label>
					<input type="text" class="form-control form-control-sm" id="searchInput"
						placeholder="生徒ID / 課題名で検索">
				</div>
				<div>
					<label class="form-label" for="filterSchool">学校</label>
					<select class="form-select form-select-sm" id="filterSchool">
						<option value="すべて" selected>すべて</option>
					</select>
				</div>
				<div>
					<label class="form-label" for="filterClass">クラス</label>
					<select class="form-select form-select-sm" id="filterClass">
						<option value="すべて" selected>すべて</option>
					</select>
				</div>
				<div>
					<label class="form-label" for="filterTask">課題</label>
					<select class="form-select form-select-sm" id="filterTask">
						<option value="すべて" selected>すべて</option>
					</select>
				</div>
				<div>
					<label class="form-label" for="filterLevel">難易度</label>
					<select class="form-select form-select-sm" id="filterLevel">
						<option value="すべて" selected>すべて</option>
					</select>
				</div>
				<div>
					<label class="form-label" for="filterStatus">状態</label>
					<select class="form-select form-select-sm" id="filterStatus">
						<option value="すべて" selected>すべて</option>
					</select>
				</div>
				<div>
					<label class="form-label" for="filterConsent">研究同意</label>
					<select class="form-select form-select-sm" id="filterConsent">
						<option value="すべて" selected>すべて</option>
						<option value="同意">同意</option>
						<option value="不同意">不同意</option>
						<option value="未確認">未確認</option>
					</select>
				</div>
			</div>
		</section>

		<section class="panel-card">
			<div class="table-responsive">
				<table class="table table-hover align-middle" id="historyTable">
					<thead class="table-light">
						<tr>
							<th style="width: 8%;" class="sortable" data-sort-key="id" scope="col">生徒ID</th>
							<th style="width: 11%;" class="sortable" data-sort-key="school" scope="col">学校</th>
							<th style="width: 10%;" class="sortable" data-sort-key="class" scope="col">クラス</th>
							<th style="width: 18%;" class="sortable" data-sort-key="task" scope="col">課題</th>
							<th style="width: 8%;" class="sortable" data-sort-key="level" scope="col">難易度</th>
							<th style="width: 9%;" class="sortable" data-sort-key="status" scope="col">状態</th>
							<th style="width: 11%;" class="sortable" data-sort-key="elapsed" scope="col">取り組み時間</th>
							<th style="width: 11%;" class="sortable" data-sort-key="updated" scope="col">最終更新</th>
							<th style="width: 8%;" class="sortable" data-sort-key="consent" scope="col">同意</th>
							<th style="width: 6%;" scope="col">詳細</th>
						</tr>
					</thead>
					<tbody>
						<c:forEach items="${teacherProgressRows}" var="row">
							<tr data-progress-row
								data-assignment-id="<c:out value='${row.assignmentId}'/>"
								data-student-user-id="<c:out value='${row.studentUserId}'/>"
								data-id="<c:out value='${row.studentLoginId}'/>"
								data-school="<c:out value='${row.schoolName}'/>"
								data-school-id="<c:out value='${row.schoolId}'/>"
								data-class="<c:out value='${row.className}'/>"
								data-class-id="<c:out value='${row.classroomId}'/>"
								data-task="<c:out value='${row.taskName}'/>"
								data-task-id="<c:out value='${row.taskId}'/>"
								data-level="<c:out value='${row.difficultyLabel}'/>"
								data-status="<c:out value='${row.status}'/>"
								data-duration-seconds="<c:out value='${row.activeDurationSeconds}'/>"
								data-elapsed="<c:out value='${row.elapsedTime}'/>"
								data-updated="<c:out value='${row.lastUpdated}'/>"
								data-consent="<c:out value='${row.consent}'/>">
								<td><code><c:out value="${row.studentLoginId}"/></code></td>
								<td><c:out value="${row.schoolName}"/></td>
								<td><c:out value="${row.className}"/></td>
								<td><c:out value="${row.taskName}"/></td>
								<td><span class="badge difficulty-${row.difficulty}"><c:out value="${row.difficultyLabel}"/></span></td>
								<td><span class="badge
									<c:choose>
										<c:when test="${row.status == '編集中'}">bg-warning text-dark</c:when>
										<c:when test="${row.status == '提出済み' or row.status == '完了'}">bg-success</c:when>
										<c:when test="${row.status == '評価待ち'}">bg-info text-dark</c:when>
										<c:when test="${row.status == '要対応'}">bg-danger</c:when>
										<c:otherwise>bg-secondary</c:otherwise>
									</c:choose>"><c:out value="${row.status}"/></span></td>
								<td><c:out value="${row.elapsedTime}"/></td>
								<td><c:choose><c:when test="${not empty row.lastUpdated}"><c:out value="${row.lastUpdated}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
								<td><span class="consent
									<c:choose>
										<c:when test="${row.consent == '同意'}">consent-ok</c:when>
										<c:when test="${row.consent == '不同意'}">consent-no</c:when>
										<c:otherwise>consent-pending</c:otherwise>
									</c:choose>"><c:out value="${row.consent}"/></span></td>
								<td><button class="btn btn-sm btn-outline-primary" type="button" data-open-progress-detail>表示</button></td>
							</tr>
						</c:forEach>
						<c:if test="${empty teacherProgressRows}">
							<tr class="empty-progress-row"><td colspan="10" class="text-center text-muted py-4">表示できる課題進捗はありません。</td></tr>
						</c:if>
					</tbody>
				</table>
				<p class="text-muted small mt-2 mb-0" id="emptyFilterMessage" hidden>条件に一致する課題進捗はありません。</p>
			</div>
		</section>
	</div>
</div>

<div class="modal fade" id="historyDetailModal" tabindex="-1" aria-hidden="true"
	aria-labelledby="historyDetailTitle">
	<div class="modal-dialog modal-lg modal-dialog-scrollable">
		<div class="modal-content">
			<div class="modal-header">
				<h5 class="modal-title" id="historyDetailTitle">課題進捗詳細</h5>
				<div class="modal-header-actions">
					<button class="btn btn-outline-primary btn-sm" type="button" id="downloadLatestCode" disabled>ファイルをダウンロード</button>
					<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
				</div>
			</div>
			<div class="modal-body">
				<div class="mb-3">
					<p class="detail-meta mb-0" id="historyDetailMeta">-</p>
				</div>
				<div class="timeline-card mt-3">
					<div class="d-flex justify-content-between align-items-start gap-2 mb-2 flex-wrap">
						<div>
							<h6 class="mb-1">最新コード</h6>
							<p class="text-muted small mb-0" id="detailCodeDescription">最終保存時点のコードを確認できます。</p>
						</div>
						<div class="code-meta-group">
							<span class="code-meta-chip" id="detailCodeUpdatedAt">更新: -</span>
						</div>
					</div>
					<textarea id="latestCodeViewer" class="code-source" spellcheck="false" readonly aria-label="最終保存または最新提出のコード"></textarea>
				</div>

				<div class="timeline-card mt-3">
					<div class="detail-section-head">
						<h6 class="detail-section-title mb-1">アクティビティ履歴</h6>
						<p class="detail-section-description mb-0">手動保存と実行の履歴を表示します。</p>
					</div>
					<div class="detail-table-wrap mt-2">
						<table class="table table-sm align-middle mb-0">
							<thead class="table-light">
								<tr>
									<th style="width: 34%;" scope="col">日時</th>
									<th style="width: 22%;" scope="col">操作</th>
									<th style="width: 16%;" scope="col">結果</th>
									<th style="width: 28%;" scope="col">備考</th>
								</tr>
							</thead>
							<tbody id="detailTimelineTableBody" aria-live="polite">
								<tr><td colspan="4" class="text-center text-muted py-3">詳細を表示すると履歴を読み込みます。</td></tr>
							</tbody>
						</table>
					</div>
				</div>
			</div>
		</div>
	</div>
</div>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
