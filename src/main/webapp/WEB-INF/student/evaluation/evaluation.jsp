<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="評価結果"/>
<c:set var="screenStylesheet" value="/css/student/evaluation/evaluation.css"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<main class="pt-2 pb-5">
	<div class="container">
		<nav class="learning-flow-nav mb-2" aria-label="学習フロー">
			<ol class="learning-flow-steps">
				<li class="learning-flow-step is-done">
					<a class="learning-flow-link is-red"
						href="<c:url value='/student/editor'><c:param name='assignmentId' value='${evaluationPage.assignmentId}'/></c:url>">課題に取り組む</a>
				</li>
				<li class="learning-flow-step">
					<span class="learning-flow-current is-yellow" aria-current="page">評価の確認</span>
				</li>
				<li class="learning-flow-step">
					<span class="learning-flow-link is-green" aria-disabled="true">アンケート</span>
				</li>
			</ol>
			<div class="learning-flow-task-title">
				<span class="learning-flow-task-name">
					<span class="learning-flow-level-badge is-beginner"><c:out value="${evaluationPage.difficultyLabel}"/></span>
					<span><c:out value="${evaluationPage.taskTitle}"/></span>
				</span>
			</div>
		</nav>

		<section class="sample-section evaluation-hero mb-4">
			<div class="hero-copy">
				<span class="hero-kicker">Evaluation Results</span>
				<h1 class="section-title mb-3">評価結果</h1>
				<p class="hero-description mb-0">
					この評価は学習の振り返りのための参考情報です。<strong>成績評価には使われず</strong>、最終的な成績は先生が確認します。<br>
					なお、<strong>最終的なコードと入出力チェック</strong>については担当の先生に渡して、<strong>通常どおり成績評価の対象となります。</strong>
				</p>
				<div class="hero-action-row mt-4">
					<a class="btn btn-warning btn-lg hero-log-button"
						href="<c:url value='/student/evaluation/log'><c:param name='submissionId' value='${evaluationPage.selectedSubmissionId}'/></c:url>">ログを見る</a>
					<span class="btn btn-success btn-lg is-disabled-action" aria-disabled="true"
						title="アンケート機能は準備中です">アンケートに答える</span>
				</div>
			</div>
			<div class="hero-meta-card">
				<div class="meta-label">対象課題</div>
				<div class="meta-value"><c:out value="${evaluationPage.taskTitle}"/></div>
				<div class="meta-subtext">
					<c:out value="${evaluationPage.difficultyLabel}"/> / 提出版 v<c:out value="${evaluationPage.selectedRevision}"/>
					/ 提出日時 <c:out value="${evaluationPage.submittedAtDisplay}"/>
				</div>
				<div class="hero-score-row">
					<span class="hero-score-pill thinking">状態: <c:out value="${evaluationPage.evaluationStatusLabel}"/></span>
					<c:if test="${not empty evaluationPage.rubricVersion}">
						<span class="hero-score-pill attitude">ルーブリック <c:out value="${evaluationPage.rubricVersion}"/></span>
					</c:if>
					<c:if test="${not empty evaluationPage.promptVersion}">
						<span class="hero-score-pill attitude">プロンプト <c:out value="${evaluationPage.promptVersion}"/></span>
					</c:if>
				</div>
			</div>
		</section>

		<c:choose>
			<c:when test="${evaluationPage.evaluationStatus == 'in_progress'}">
				<div class="alert alert-info" role="status">
					現在この提出版を評価しています。評価が完了すると、この画面に結果が表示されます。
				</div>
				<c:if test="${evaluationPage.showingPreviousCompletedEvaluation}">
					<section class="sample-section overview-section mb-4">
						<h2 class="card-title mb-2">前回提出分の評価</h2>
						<p class="text-muted">以下は今回の提出ではなく、提出版 v<c:out value="${evaluationPage.previousCompletedEvaluation.revisionNumber}"/> の評価です。</p>
						<c:if test="${not empty evaluationPage.previousCompletedEvaluation.feedbackSummary}">
							<p class="mb-0"><c:out value="${evaluationPage.previousCompletedEvaluation.feedbackSummary}"/></p>
						</c:if>
						<c:if test="${not empty evaluationPage.previousCompletedEvaluation.dimensions}">
							<ul class="mt-3 mb-0">
								<c:forEach var="dimension" items="${evaluationPage.previousCompletedEvaluation.dimensions}">
									<li><c:out value="${dimension.label}"/>:
										<c:choose>
											<c:when test="${not empty dimension.scoreText}"><c:out value="${dimension.scoreText}"/></c:when>
											<c:otherwise><c:out value="${dimension.score}"/></c:otherwise>
										</c:choose>
									</li>
								</c:forEach>
							</ul>
						</c:if>
						<a class="btn btn-outline-primary btn-sm mt-3"
							href="<c:url value='/student/evaluation'><c:param name='assignmentId' value='${evaluationPage.assignmentId}'/><c:param name='submissionId' value='${evaluationPage.previousCompletedEvaluation.submissionId}'/></c:url>">
							前回提出分の詳細を見る
						</a>
					</section>
				</c:if>
			</c:when>
			<c:when test="${evaluationPage.evaluationStatus == 'failed'}">
				<div class="alert alert-danger" role="alert">
					この提出版の評価に失敗しました。評価結果は生成されていません。もう一度評価を実行できます。
				</div>
				<form method="post" action="<c:url value='/student/evaluation/retry'/>" class="mt-3">
					<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
					<input type="hidden" name="assignmentId" value="<c:out value='${evaluationPage.assignmentId}'/>">
					<input type="hidden" name="submissionId" value="<c:out value='${evaluationPage.selectedSubmissionId}'/>">
					<button type="submit" class="btn btn-primary">この提出版を再評価する</button>
				</form>
			</c:when>
			<c:when test="${evaluationPage.evaluationStatus == 'needs_revision'}">
				<div class="alert alert-warning" role="status">この評価は確認・修正が必要な状態です。先生による確認をお待ちください。</div>
			</c:when>
			<c:when test="${not evaluationPage.evaluationConfigured and evaluationPage.evaluationStatus == 'not_started'}">
				<div class="alert alert-warning" role="status">
					評価に必要なルーブリックまたはプロンプトがまだ有効化されていないため、この提出版の評価結果はありません。
				</div>
			</c:when>
			<c:when test="${evaluationPage.evaluationStatus == 'completed' and empty evaluationPage.evaluation}">
				<div class="alert alert-warning" role="status">
					評価状態は完了ですが、結果データを読み込めませんでした。点数を表示せず、先生による確認が必要です。
				</div>
			</c:when>
			<c:when test="${evaluationPage.evaluationStatus == 'not_started'}">
				<div class="alert alert-info" role="status">この提出版の評価はまだ開始されていません。</div>
			</c:when>
		</c:choose>

		<c:if test="${(evaluationPage.evaluationStatus == 'completed' or evaluationPage.evaluationStatus == 'needs_revision') and not empty evaluationPage.evaluation}">
			<section class="sample-section overview-section mb-4">
				<div class="section-heading-row">
					<div>
						<h2 class="card-title mb-2">総合評価</h2>
						<p class="text-muted mb-0">
							提出版 v<c:out value="${evaluationPage.evaluation.revisionNumber}"/>
							<c:if test="${not empty evaluationPage.evaluation.completedAtDisplay}">
								/ 評価日時 <c:out value="${evaluationPage.evaluation.completedAtDisplay}"/>
							</c:if>
						</p>
					</div>
					<span class="score-caption">ルーブリックに基づく評価</span>
				</div>
				<c:choose>
					<c:when test="${empty evaluationPage.evaluation.dimensions}">
						<p class="text-muted mt-3 mb-0">観点別の評価結果は登録されていません。</p>
					</c:when>
					<c:otherwise>
						<div class="row g-4 mt-1">
							<c:forEach var="dimension" items="${evaluationPage.evaluation.dimensions}">
								<div class="col-lg-6">
									<article class="score-panel">
										<div class="panel-kicker"><c:out value="${dimension.label}"/></div>
										<div class="panel-score-row mt-3">
											<div class="panel-score">
												<c:choose>
													<c:when test="${not empty dimension.scoreText}"><c:out value="${dimension.scoreText}"/></c:when>
													<c:otherwise><c:out value="${dimension.score}"/></c:otherwise>
												</c:choose>
											</div>
											<div>
												<h3 class="panel-title"><c:out value="${dimension.summaryTitle}"/></h3>
												<p class="panel-description mb-0"><c:out value="${dimension.summaryDescription}"/></p>
											</div>
										</div>
									</article>
								</div>
							</c:forEach>
						</div>
					</c:otherwise>
				</c:choose>
				<c:if test="${not empty evaluationPage.evaluation.feedbackSummary}">
					<div class="mt-4">
						<h3 class="h5">フィードバック</h3>
						<p class="mb-0"><c:out value="${evaluationPage.evaluation.feedbackSummary}"/></p>
					</div>
				</c:if>
				<c:if test="${not empty evaluationPage.evaluation.processAnalysis}">
					<div class="mt-4">
						<h3 class="h5">学習過程の分析</h3>
						<p class="mb-0"><c:out value="${evaluationPage.evaluation.processAnalysis}"/></p>
					</div>
				</c:if>
			</section>

			<section class="sample-section breakdown-section mb-4">
				<div class="section-heading-row">
					<div>
						<h2 class="card-title mb-2">観点別スコア</h2>
						<p class="text-muted mb-0">細かな評価項目ごとの点数です。</p>
					</div>
				</div>
				<c:choose>
					<c:when test="${empty evaluationPage.evaluation.scores}">
						<p class="text-muted mt-3 mb-0">評価項目のスコアは登録されていません。</p>
					</c:when>
					<c:otherwise>
						<div class="breakdown-grid mt-3">
							<c:forEach var="score" items="${evaluationPage.evaluation.scores}">
								<article class="metric-card">
									<div class="metric-header">
										<span class="metric-group"><c:out value="${score.dimensionLabel}"/></span>
										<span class="metric-score"><c:out value="${score.score}"/></span>
									</div>
									<h3><c:out value="${score.title}"/></h3>
									<p><c:out value="${score.description}"/></p>
									<c:if test="${not empty score.rationale}">
										<p class="mb-0"><c:out value="${score.rationale}"/></p>
									</c:if>
								</article>
							</c:forEach>
						</div>
					</c:otherwise>
				</c:choose>
			</section>

			<section class="sample-section mb-4">
				<div class="section-heading-row mb-3">
					<div>
						<h2 class="card-title mb-2">評価の理由</h2>
						<p class="text-muted mb-0">登録された評価根拠を表示しています。</p>
					</div>
				</div>
				<c:choose>
					<c:when test="${empty evaluationPage.evaluation.reasons}">
						<p class="text-muted mb-0">評価理由は登録されていません。</p>
					</c:when>
					<c:otherwise>
						<div class="reason-list">
							<c:forEach var="reason" items="${evaluationPage.evaluation.reasons}">
								<article class="reason-card">
									<div class="reason-title-row">
										<h3><c:out value="${reason.title}"/></h3>
										<c:if test="${not empty reason.dimensionLabel}">
											<span class="reason-chip"><c:out value="${reason.dimensionLabel}"/></span>
										</c:if>
									</div>
									<p class="reason-body"><c:out value="${reason.body}"/></p>
									<c:if test="${not empty reason.details}">
										<ul class="reason-detail-list">
											<c:forEach var="detail" items="${reason.details}">
												<li><c:out value="${detail}"/></li>
											</c:forEach>
										</ul>
									</c:if>
								</article>
							</c:forEach>
						</div>
					</c:otherwise>
				</c:choose>
			</section>
		</c:if>

		<section class="sample-section">
			<h2 class="card-title mb-3">提出履歴</h2>
			<c:choose>
				<c:when test="${empty evaluationPage.submissions}">
					<p class="text-muted mb-0">提出履歴はありません。</p>
				</c:when>
				<c:otherwise>
					<div class="list-group">
						<c:forEach var="submission" items="${evaluationPage.submissions}">
							<a class="list-group-item list-group-item-action d-flex flex-wrap justify-content-between gap-2 ${submission.selected ? 'active' : ''}"
								href="<c:url value='/student/evaluation'><c:param name='assignmentId' value='${evaluationPage.assignmentId}'/><c:param name='submissionId' value='${submission.submissionId}'/></c:url>"
								<c:if test="${submission.selected}">aria-current="page"</c:if>>
								<span>提出版 v<c:out value="${submission.revisionNumber}"/> / <c:out value="${submission.submittedAtDisplay}"/></span>
								<span><c:out value="${submission.evaluationStatusLabel}"/></span>
							</a>
						</c:forEach>
					</div>
				</c:otherwise>
			</c:choose>
		</section>
	</div>
</main>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
