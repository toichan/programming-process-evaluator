<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="評価結果"/>
<c:set var="screenStylesheet" value="/css/student/evaluation/evaluation.css"/>
<c:set var="screenScript" value="/js/student/evaluation/evaluation.js"/>
<c:set var="screenAssetVersion" value="159"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<c:url var="surveyUrl" value="/student/survey"><c:param name="assignmentId" value="${evaluationPage.assignmentId}"/><c:param name="evaluationId" value="${surveyNavigation.evaluationId}"/><c:param name="surveyId" value="${surveyNavigation.surveyId}"/></c:url>
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
					<c:choose>
						<c:when test="${surveyNavigation.available}">
							<a class="learning-flow-link is-green" href="<c:out value='${surveyUrl}'/>">アンケート</a>
						</c:when>
						<c:otherwise><span class="learning-flow-link is-green" aria-disabled="true">アンケート</span></c:otherwise>
					</c:choose>
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
						href="<c:url value='/student/evaluation/log'><c:param name='submissionId' value='${evaluationPage.selectedSubmissionId}'/></c:url>">ソースコード編集ログを見る</a>
					<c:choose>
						<c:when test="${surveyNavigation.available}">
							<a class="btn btn-success btn-lg" href="<c:out value='${surveyUrl}'/>">アンケートに答える</a>
						</c:when>
						<c:otherwise>
							<span class="btn btn-success btn-lg is-disabled-action" aria-disabled="true"
								title="<c:out value='${surveyNavigation.unavailableReason}'/>">アンケートに答える</span>
						</c:otherwise>
					</c:choose>
				</div>
				<c:if test="${not surveyNavigation.available}"><p class="text-muted mt-2 mb-0" role="status"><c:out value="${surveyNavigation.unavailableReason}"/></p></c:if>
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
									<article class="score-panel ${dimension.label == '主体的に学習に取り組む態度' ? 'attitude-panel' : 'thinking-panel'}">
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
										<div class="score-meter" aria-hidden="true">
											<c:forEach begin="1" end="5" var="level">
												<span class="${level <= dimension.score + 0.5 ? 'is-filled' : ''}"></span>
											</c:forEach>
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
						<div id="evaluationReasonFilters" class="reason-filter-group mb-3" role="group" aria-label="評価理由の観点">
							<button class="reason-filter is-active" type="button" data-reason-filter="all" aria-pressed="true">すべて</button>
							<button class="reason-filter" type="button" data-reason-filter="thinking" aria-pressed="false">思考・判断・表現</button>
							<button class="reason-filter" type="button" data-reason-filter="attitude" aria-pressed="false">態度</button>
							<button class="reason-filter" type="button" data-reason-filter="other" aria-pressed="false">その他</button>
						</div>
						<div class="reason-list">
							<c:forEach var="reason" items="${evaluationPage.evaluation.reasons}">
								<c:set var="reasonCategory" value="other"/>
								<c:choose>
									<c:when test="${reason.dimensionLabel == '主体的に学習に取り組む態度'}"><c:set var="reasonCategory" value="attitude"/></c:when>
									<c:when test="${reason.dimensionLabel == '思考力・判断力・表現力'}"><c:set var="reasonCategory" value="thinking"/></c:when>
								</c:choose>
								<article class="reason-card ${reasonCategory == 'attitude' ? 'accent-reason' : ''}" data-category="<c:out value='${reasonCategory}'/>">
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
									<div class="reason-evidence" aria-label="保存済み根拠">
										<c:choose>
											<c:when test="${empty reason.evidence}">
												<p class="small text-muted mb-0">この理由に紐づく保存済み根拠はありません。</p>
											</c:when>
											<c:otherwise>
												<ul class="list-unstyled mb-0">
													<c:forEach var="evidence" items="${reason.evidence}">
														<li class="mt-2">
															<c:if test="${not empty evidence.label}"><strong><c:out value="${evidence.label}"/></strong></c:if>
															<c:if test="${not empty evidence.detailText}"><span class="small text-muted">（詳細: <c:out value="${evidence.detailText}"/>）</span></c:if>
															<c:if test="${not empty evidence.summary}"><p class="small mb-1"><c:out value="${evidence.summary}"/></p></c:if>
															<c:choose>
																<c:when test="${not empty evidence.codeLogId}">
																	<a class="btn btn-outline-primary btn-sm" href="<c:url value='/student/evaluation/log'><c:param name='submissionId' value='${evaluationPage.selectedSubmissionId}'/><c:param name='logId' value='${evidence.codeLogId}'/></c:url>">対応する編集ログ <c:out value="${evidence.codeLogId}"/> を開く</a>
																</c:when>
																<c:when test="${evidence.hasCodeLogReference}">
																	<p class="small text-muted mb-0">編集ログ根拠は保存されていますが、この提出版の認可済みログ一覧で照合できません。</p>
																</c:when>
																<c:otherwise>
																	<p class="small text-muted mb-0">この根拠に対応する編集ログIDは保存されていません。</p>
																</c:otherwise>
															</c:choose>
														</li>
													</c:forEach>
												</ul>
											</c:otherwise>
										</c:choose>
									</div>
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
<%@ include file="/WEB-INF/student/shared/home-action.jspf" %>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
