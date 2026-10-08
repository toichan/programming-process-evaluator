<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-4" id="teacherReview"
	data-endpoint="<c:choose><c:when test='${reviewEvaluations}'><c:url value='/teacher/evaluations'/></c:when><c:otherwise><c:url value='/teacher/submissions'/></c:otherwise></c:choose>"
	data-evaluations="<c:out value='${reviewEvaluations}'/>"
	data-selected-submission="<c:out value='${reviewSelectedSubmission}'/>"
	data-selected-evaluation="<c:out value='${reviewSelectedEvaluation}'/>"
	data-csrf-token="<c:out value='${csrfToken}'/>">
	<div class="container-fluid px-4">
		<h1 class="section-title"><c:out value="${screenPageTitle}"/></h1>
		<p>学校の閲覧権限内の履歴を確認します。提出・評価・コードログは変更しません。研究同意の有無だけで授業上の閲覧は制限しません。</p>
		<div id="reviewAlert" role="alert"></div>
		<section id="reviewList" class="panel-card">
			<form id="reviewFilters" class="review-filters">
				<label>検索<input name="search" class="form-control" maxlength="100" placeholder="生徒ID / 課題名"></label>
				<label>学校<select name="schoolId" class="form-select"><option value="">すべて</option></select></label>
				<label>クラス<select name="classroomId" class="form-select"><option value="">すべて</option></select></label>
				<label>課題<select name="taskId" class="form-select"><option value="">すべて</option></select></label>
				<label>難易度<select name="difficulty" class="form-select"><option value="">すべて</option><option value="beginner">初級</option><option value="intermediate">中級</option><option value="advanced">上級</option><option value="none">未設定</option></select></label>
				<label>研究同意<select name="consent" class="form-select"><option value="">すべて</option><option value="agreed">同意</option><option value="not_agreed">未同意</option><option value="unconfirmed">未確認</option></select></label>
				<c:if test="${reviewEvaluations}">
					<label>総合評価段階<select name="level" class="form-select"><option value="">すべて</option><c:forEach begin="1" end="5" var="level"><option value="${level}">${level}</option></c:forEach></select></label>
				</c:if>
				<label>並び順<select name="sort" class="form-select"><option value="student">生徒ID</option><option value="task">課題名</option><option value="submitted">提出日時</option><c:choose><c:when test="${reviewEvaluations}"><option value="level">総合評価段階</option><option value="evaluated">評価実施日時</option></c:when><c:otherwise><option value="match">入出力チェック一致率</option></c:otherwise></c:choose></select></label>
				<label>方向<select name="direction" class="form-select"><option value="desc">降順</option><option value="asc">昇順</option></select></label>
				<button class="btn btn-primary" type="submit">絞り込み / 更新</button>
			</form>
			<c:if test="${reviewEvaluations}">
				<p class="small mt-3">CSVは出力時の最新同意が「同意」の行のみです。生徒ログインIDを含む教師確認用データで、研究用匿名化出力ではありません。</p>
				<a id="reviewCsv" class="btn btn-outline-primary mb-3" href="#">評価CSVをダウンロード</a>
			</c:if>
			<p id="reviewCount" aria-live="polite"></p>
			<div class="table-responsive"><table class="table table-hover align-middle">
				<thead><tr><th>生徒ID</th><th>学校 / クラス</th><th>課題 / 難易度</th><th>提出版 / 日時</th>
				<c:choose><c:when test="${reviewEvaluations}"><th>思考力・判断力・表現力</th><th>主体的に学習に取り組む態度</th><th>総合評価</th><th>評価状態 / 実施日時</th></c:when><c:otherwise><th>入出力チェック</th></c:otherwise></c:choose>
				<th>研究同意</th><th>履歴区分</th><th>詳細</th></tr></thead>
				<tbody id="reviewRows"></tbody>
			</table></div>
		</section>
		<section id="evaluationDetail" class="panel-card" hidden>
			<a id="backToReviews" href="#">一覧へ戻る</a>
			<h2 id="evaluationTitle" class="mt-3"></h2>
			<p id="evaluationMetadata"></p>
			<label>評価履歴<select id="evaluationVersions" class="form-select"></select></label>
			<p id="evaluationState" class="mt-3" aria-live="polite"></p>
			<div id="previousEvaluation"></div>
			<h3>評価結果</h3><div id="evaluationResults"></div>
			<h3>評価理由</h3><div id="evaluationReasons"></div>
			<h3>コードログ</h3>
			<label>時点を選択<select id="codeLogTimes" class="form-select"></select></label>
			<p id="codeLogState"></p><pre id="codeLogSnapshot" class="review-code"></pre>
			<h4>提出時スナップショット（読取専用）</h4><pre id="evaluationSubmission" class="review-code"></pre>
		</section>
	</div>
</div>
<div class="modal fade" id="submissionModal" tabindex="-1" aria-labelledby="submissionTitle" aria-hidden="true">
	<div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title fs-5" id="submissionTitle">提出詳細</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body">
			<p id="submissionMetadata"></p>
			<h3>提出時スナップショット（読取専用）</h3><pre id="submittedSnapshot" class="review-code"></pre>
			<h3>提出時の入出力チェック</h3><p id="submissionCheckSummary"></p><div id="submissionChecks"></div>
			<h3>作業用コピー</h3><p>編集・実行内容は提出データ、評価、コードログへ保存しません。</p>
			<label class="w-100">Pythonコード<textarea id="previewCode" class="form-control review-code" rows="10" spellcheck="false"></textarea></label>
			<label class="w-100 mt-2">標準入力<textarea id="previewInput" class="form-control" rows="3"></textarea></label>
			<button id="previewRun" type="button" class="btn btn-primary mt-2">作業用コードを実行</button>
			<div id="previewAlert" role="alert"></div><pre id="previewOutput" class="review-code mt-2" aria-live="polite"></pre>
		</div>
		<div class="modal-footer"><button id="previousSubmission" type="button" class="btn btn-outline-primary">戻る</button><button id="nextSubmission" type="button" class="btn btn-outline-primary">次へ</button><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button></div>
	</div></div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
