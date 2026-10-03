<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="コードログ"/>
<c:set var="screenStylesheet" value="/css/student/evaluation/log.css"/>
<c:set var="screenScript" value="/js/student/evaluation/log.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<main class="pt-2 pb-5">
	<div class="container">
		<nav class="learning-flow-nav mb-2" aria-label="学習フロー">
			<ol class="learning-flow-steps">
				<li class="learning-flow-step is-done">
					<span class="learning-flow-link is-red">課題に取り組む</span>
				</li>
				<li class="learning-flow-step">
					<a class="learning-flow-link is-yellow"
						href="<c:url value='/student/evaluation'><c:param name='assignmentId' value='${codeLogPage.assignmentId}'/><c:param name='submissionId' value='${codeLogPage.submissionId}'/></c:url>">評価の確認</a>
				</li>
				<li class="learning-flow-step">
					<span class="learning-flow-link is-green" aria-disabled="true">アンケート</span>
				</li>
			</ol>
			<div class="learning-flow-task-title">
				<span class="learning-flow-task-name">
					<span class="learning-flow-level-badge is-beginner"><c:out value="${codeLogPage.difficultyLabel}"/></span>
					<span><c:out value="${codeLogPage.taskTitle}"/></span>
				</span>
			</div>
		</nav>

		<section class="sample-section log-hero mb-4">
			<div class="hero-copy">
				<span class="hero-kicker">Student Log</span>
				<h1 class="section-title mb-3">コードログ</h1>
				<p class="hero-description mb-0">
					自動保存されたコードの履歴を時系列で確認できます。前後の状態を見比べて、試行錯誤の流れを振り返るためのページです。
				</p>
				<div class="hero-action-row mt-4">
					<a class="btn btn-warning btn-lg"
						href="<c:url value='/student/evaluation'><c:param name='assignmentId' value='${codeLogPage.assignmentId}'/><c:param name='submissionId' value='${codeLogPage.submissionId}'/></c:url>">評価結果に戻る</a>
					<span class="btn btn-outline-secondary btn-lg is-disabled-action" aria-disabled="true"
						title="アンケート機能は準備中です">アンケートに進む</span>
				</div>
			</div>
			<div class="hero-meta-card">
				<div class="meta-label">対象課題</div>
				<div class="meta-value"><c:out value="${codeLogPage.taskTitle}"/></div>
				<div class="meta-subtext"><c:out value="${codeLogPage.difficultyLabel}"/> / 提出版 v<c:out value="${codeLogPage.revisionNumber}"/></div>
				<div class="hero-status-list mt-3">
					<div class="hero-status-item">
						<span class="hero-status-name">保存回数</span>
						<span class="hero-status-pill"><c:out value="${codeLogPage.saveCount}"/>回</span>
					</div>
					<div class="hero-status-item">
						<span class="hero-status-name">実行記録</span>
						<span class="hero-status-pill accent"><c:out value="${codeLogPage.executionCount}"/>回</span>
					</div>
				</div>
			</div>
		</section>

		<div class="row g-4 align-items-start">
			<div class="col-xl-8">
				<section class="sample-section log-viewer-section">
					<div class="section-heading-row mb-3">
						<div>
							<h2 class="card-title mb-2">差分ビューアー</h2>
							<p class="text-muted mb-0">各保存時点のコードを時系列で確認できます。</p>
						</div>
					</div>
					<c:choose>
						<c:when test="${empty codeLogPage.entries}">
							<div class="alert alert-info mb-0" role="status">この提出版に表示できるコードログはありません。</div>
						</c:when>
						<c:otherwise>
							<div class="viewer-controls">
								<button id="prevCodeButton" class="btn btn-outline-secondary" type="button">← 前へ</button>
								<div id="stepIndicator" class="viewer-step-indicator" aria-live="polite"></div>
								<button id="nextCodeButton" class="btn btn-primary" type="button">次へ →</button>
							</div>
							<div class="viewer-id-row mt-3">
								<span id="sourceCodeId" class="viewer-id-pill"></span>
								<span id="executionId" class="viewer-id-pill"></span>
							</div>
							<div class="code-shell mt-4">
								<pre id="codeBlock" class="code-block" aria-label="保存されたソースコード"></pre>
							</div>
							<div class="viewer-meta-row mt-3">
								<p id="timestamp" class="timestamp mb-0"></p>
								<p id="boundaryMessage" class="boundary-message mb-0" aria-live="polite"></p>
							</div>
							<div class="mt-4">
								<h3 class="h5">実行時の入出力</h3>
								<div id="executionOutput" class="border rounded-3 p-3 bg-light">
									<p class="text-muted mb-0">選択したログに実行記録はありません。</p>
								</div>
							</div>
						</c:otherwise>
					</c:choose>
				</section>
			</div>

			<div class="col-xl-4">
				<div class="sticky-xl-top sidebar-stack">
					<section class="sample-section sidebar-section">
						<div class="sidebar-switcher mb-3" role="tablist" aria-label="ログ表示切り替え">
							<button class="sidebar-switch-button is-active" type="button" data-sidebar-panel="timeline">ログ一覧</button>
							<button class="sidebar-switch-button" type="button" data-sidebar-panel="reasons">評価の理由</button>
						</div>
						<div id="timelinePanel" class="sidebar-panel is-active">
							<div class="section-heading-row mb-3">
								<div>
									<h2 class="card-title mb-2">ログ一覧</h2>
									<p class="text-muted mb-0">各保存時点の概要を確認できます。</p>
								</div>
							</div>
							<div class="log-timeline-scroll">
								<div id="logTimeline" class="log-timeline">
									<c:forEach var="entry" items="${codeLogPage.entries}" varStatus="status">
										<button type="button" class="timeline-item text-start w-100"
											data-log-index="${status.index}"
											data-log-id="<c:out value='${entry.logId}'/>">
											<span class="timeline-time d-block"><c:out value="${entry.observedAtDisplay}"/></span>
											<span class="timeline-label d-block"><c:out value="${entry.eventLabel}"/></span>
										</button>
									</c:forEach>
								</div>
							</div>
							<div id="logData" hidden>
								<c:forEach var="entry" items="${codeLogPage.entries}" varStatus="status">
									<div class="log-data-entry"
										data-log-index="${status.index}"
										data-log-id="<c:out value='${entry.logId}'/>"
										data-event-label="<c:out value='${entry.eventLabel}'/>"
										data-observed-at="<c:out value='${entry.observedAtDisplay}'/>"
										data-execution-id="<c:out value='${entry.executionId}'/>"
										data-execution-status="<c:out value='${entry.executionStatus}'/>">
										<pre class="log-snapshot"><c:out value="${entry.snapshot}"/></pre>
										<pre class="log-standard-input"><c:out value="${entry.standardInput}"/></pre>
										<pre class="log-standard-output"><c:out value="${entry.standardOutput}"/></pre>
										<pre class="log-standard-error"><c:out value="${entry.standardError}"/></pre>
									</div>
								</c:forEach>
							</div>
						</div>
						<div id="reasonsPanel" class="sidebar-panel">
							<div class="section-heading-row mb-3">
								<div>
									<h2 class="card-title mb-2">評価の理由</h2>
									<p class="text-muted mb-0">この提出版に登録された評価根拠です。</p>
								</div>
							</div>
							<c:choose>
								<c:when test="${empty codeLogPage.evaluation or empty codeLogPage.evaluation.reasons}">
									<p class="text-muted mb-0">この提出版には評価理由がありません。</p>
								</c:when>
								<c:otherwise>
									<div class="log-timeline-scroll reason-list">
										<c:forEach var="reason" items="${codeLogPage.evaluation.reasons}">
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
						</div>
					</section>
				</div>
			</div>
		</div>
	</div>
</main>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
