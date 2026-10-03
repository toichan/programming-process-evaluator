<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="ホーム"/>
<c:set var="screenStylesheet" value="/css/student/home/home.css"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div class="container">
	<c:if test="${studentHomeNotice == 'saved'}">
		<div class="alert alert-success" role="status">研究協力への回答を記録しました。学習機能は通常どおり利用できます。</div>
	</c:if>
	<c:if test="${studentHomeNotice == 'already'}">
		<div class="alert alert-info" role="status">研究協力への回答は既に記録されています。</div>
	</c:if>
	<c:if test="${studentHome.consentStatus == 'UNCONFIRMED'}">
		<div class="alert alert-warning d-flex flex-wrap align-items-center justify-content-between gap-2" role="status">
			<span>研究協力への同意確認が未回答です。課題を行う前に回答してください。</span>
			<a class="btn btn-sm btn-outline-dark" href="<c:url value='/student/survey/consent'/>">回答する</a>
		</div>
	</c:if>
	<c:if test="${studentHome.consentStatus == 'DECLINED'}">
		<div class="alert alert-secondary" role="status">研究協力には同意していません。学習機能は通常どおり利用できます。</div>
	</c:if>
	<section class="sample-section hero-section mb-4">
		<div class="hero-copy">
			<span class="hero-kicker">Student Dashboard</span>
			<h1 class="section-title mb-3">ホーム</h1>
			<p class="hero-description mb-0">
				公開中の課題を選び、エディターでコードの作成・実行・提出を行えます。授業演習は準備中です。
			</p>
			<div class="button-group hero-action-group mt-4" aria-label="学習導線">
				<span class="btn btn-primary is-disabled-action" aria-disabled="true" title="後続工程で利用可能になります">授業演習へ進む</span>
				<a class="btn btn-outline-primary" href="#taskListSection">課題一覧を見る</a>
			</div>
		</div>
		<div class="hero-status-card">
			<div class="status-label">課題の状況</div>
			<div class="status-value"><c:out value="${studentHome.submittedTaskCount}"/>/<c:out value="${taskCount}"/> 提出済み</div>
			<div class="status-meta">在籍クラスに公開されている<c:out value="${taskCount}"/>件の課題のうち、提出済みまたは提出後の状態です。</div>
		</div>
	</section>

	<section id="taskListSection" class="sample-section">
		<div class="d-flex flex-column flex-lg-row align-items-lg-center justify-content-between gap-3 mb-4">
			<div>
				<h2 class="card-title mb-2">課題演習一覧</h2>
				<p class="text-muted mb-0">現在の在籍クラスに公開されている課題を表示しています。</p>
			</div>
			<div class="task-summary-pill"><c:out value="${taskCount}"/> ${taskCount == 1 ? 'Task' : 'Tasks'}</div>
		</div>

		<c:choose>
			<c:when test="${empty studentHome.tasks}">
				<div class="empty-state text-center py-5">
					<h3 class="h5">現在取り組める課題はありません</h3>
					<p class="text-muted mb-0">課題が公開されると、この一覧に表示されます。</p>
				</div>
			</c:when>
			<c:otherwise>
				<div class="row g-4">
					<c:forEach var="task" items="${studentHome.tasks}">
						<c:choose>
							<c:when test="${task.difficulty == 'beginner'}">
								<c:set var="difficultyClass" value="beginner"/>
								<c:set var="difficultyLabel" value="初級"/>
							</c:when>
							<c:when test="${task.difficulty == 'intermediate'}">
								<c:set var="difficultyClass" value="intermediate"/>
								<c:set var="difficultyLabel" value="中級"/>
							</c:when>
							<c:when test="${task.difficulty == 'advanced'}">
								<c:set var="difficultyClass" value="advanced"/>
								<c:set var="difficultyLabel" value="上級"/>
							</c:when>
							<c:otherwise>
								<c:set var="difficultyClass" value="unrated"/>
								<c:set var="difficultyLabel" value="難易度未設定"/>
							</c:otherwise>
						</c:choose>
						<c:choose>
							<c:when test="${task.progressStatus == 'not_started'}"><c:set var="progressBadgeClass" value="status-unstarted"/></c:when>
							<c:when test="${task.progressStatus == 'in_progress'}"><c:set var="progressBadgeClass" value="status-editing"/></c:when>
							<c:when test="${task.progressStatus == 'submitted' or task.progressStatus == 'awaiting_evaluation'}"><c:set var="progressBadgeClass" value="status-submitted"/></c:when>
							<c:when test="${task.progressStatus == 'completed'}"><c:set var="progressBadgeClass" value="status-completed"/></c:when>
							<c:otherwise><c:set var="progressBadgeClass" value="status-pending"/></c:otherwise>
						</c:choose>
						<c:choose>
							<c:when test="${task.evaluationStatus == 'completed'}"><c:set var="evaluationBadgeClass" value="status-rated"/></c:when>
							<c:when test="${task.evaluationStatus == 'in_progress'}"><c:set var="evaluationBadgeClass" value="status-editing"/></c:when>
							<c:when test="${task.evaluationStatus == 'failed' or task.evaluationStatus == 'needs_revision'}"><c:set var="evaluationBadgeClass" value="status-pending"/></c:when>
							<c:otherwise><c:set var="evaluationBadgeClass" value="status-unrated"/></c:otherwise>
						</c:choose>
						<c:choose>
							<c:when test="${task.surveyStatus == 'submitted'}"><c:set var="surveyBadgeClass" value="status-completed"/></c:when>
							<c:when test="${task.surveyStatus == 'in_progress'}"><c:set var="surveyBadgeClass" value="status-editing"/></c:when>
							<c:when test="${task.surveyStatus == 'not_answered'}"><c:set var="surveyBadgeClass" value="status-pending"/></c:when>
							<c:otherwise><c:set var="surveyBadgeClass" value="status-unrated"/></c:otherwise>
						</c:choose>
						<div class="col-12">
							<article class="task-list-card task-card task-card-${difficultyClass}">
								<div class="task-header-row">
									<div>
										<span class="task-level"><c:out value="${difficultyLabel}"/></span>
										<h3><c:out value="${task.title}"/></h3>
									</div>
									<div class="task-deadline-chip-wrap" aria-label="提出期限">
										<span class="task-deadline-label">提出期限</span>
										<span class="task-deadline-date">
											<c:choose>
												<c:when test="${not empty task.dueAt}"><c:out value="${task.dueAtDisplay}"/></c:when>
												<c:otherwise>設定なし</c:otherwise>
											</c:choose>
										</span>
										<span class="task-deadline-remaining ${task.dueUrgencyClass}">
											<c:out value="${task.dueRemainingDisplay}"/>
										</span>
									</div>
								</div>
								<p class="task-description"><c:out value="${task.description}"/></p>
								<div class="task-content-grid">
									<div class="task-main-column">
										<span class="meta-label">学習テーマ</span>
										<span class="meta-value">
											<c:choose>
												<c:when test="${not empty task.theme}"><c:out value="${task.theme}"/></c:when>
												<c:otherwise>設定なし</c:otherwise>
											</c:choose>
										</span>
										<div class="button-group task-button-group" aria-label="課題操作">
											<a class="btn btn-primary task-action-btn"
												href="<c:url value='/student/editor'><c:param name='assignmentId' value='${task.taskClassAssignmentId}'/></c:url>">課題に取り組む</a>
											<c:choose>
												<c:when test="${not empty task.latestSubmissionId}">
													<a class="btn btn-warning"
														href="<c:url value='/student/evaluation'><c:param name='assignmentId' value='${task.taskClassAssignmentId}'/><c:param name='submissionId' value='${task.latestSubmissionId}'/></c:url>">評価の確認</a>
												</c:when>
												<c:otherwise>
													<span class="btn btn-warning is-disabled-action" aria-disabled="true" title="提出後に確認できます">評価の確認</span>
												</c:otherwise>
											</c:choose>
											<c:choose>
												<c:when test="${task.surveyStatus != 'not_applicable' and not empty task.latestCompletedEvaluationId and not empty task.activeSurveyId}">
													<a class="btn btn-success"
														href="<c:url value='/student/survey'><c:param name='assignmentId' value='${task.taskClassAssignmentId}'/><c:param name='evaluationId' value='${task.latestCompletedEvaluationId}'/><c:param name='surveyId' value='${task.activeSurveyId}'/></c:url>">アンケート</a>
												</c:when>
												<c:otherwise>
													<span class="btn btn-success is-disabled-action" aria-disabled="true" title="評価完了後、対象アンケートがある場合に回答できます">アンケート</span>
												</c:otherwise>
											</c:choose>
										</div>
									</div>
									<div class="task-status-column">
										<span class="meta-label">課題状況</span>
										<div class="task-status-list">
											<div class="task-status-item">
												<span class="task-status-name">課題</span>
												<span class="task-status-badge ${progressBadgeClass}">
													<c:choose>
														<c:when test="${task.progressStatus == 'not_started'}">未提出</c:when>
														<c:when test="${task.progressStatus == 'in_progress'}">編集中</c:when>
														<c:when test="${task.progressStatus == 'submitted'}">提出済み</c:when>
														<c:when test="${task.progressStatus == 'awaiting_evaluation'}">評価待ち</c:when>
														<c:when test="${task.progressStatus == 'completed'}">完了</c:when>
														<c:when test="${task.progressStatus == 'needs_action'}">要対応</c:when>
														<c:otherwise>状況確認中</c:otherwise>
													</c:choose>
												</span>
											</div>
											<div class="task-status-item">
												<span class="task-status-name">評価</span>
												<span class="task-status-badge ${evaluationBadgeClass}">
													<c:choose>
														<c:when test="${task.evaluationStatus == 'completed'}">評価済み</c:when>
														<c:when test="${task.evaluationStatus == 'in_progress'}">評価中</c:when>
														<c:when test="${task.evaluationStatus == 'failed'}">評価に失敗</c:when>
														<c:when test="${task.evaluationStatus == 'needs_revision'}">要修正</c:when>
														<c:otherwise>未評価</c:otherwise>
													</c:choose>
												</span>
											</div>
											<div class="task-status-item">
												<span class="task-status-name">アンケート</span>
												<span class="task-status-badge ${surveyBadgeClass}">
													<c:choose>
														<c:when test="${task.surveyStatus == 'submitted'}">回答済み</c:when>
														<c:when test="${task.surveyStatus == 'in_progress'}">回答中</c:when>
														<c:when test="${task.surveyStatus == 'not_answered'}">未回答</c:when>
														<c:otherwise>対象なし</c:otherwise>
													</c:choose>
												</span>
											</div>
										</div>
									</div>
								</div>
							</article>
						</div>
					</c:forEach>
				</div>
			</c:otherwise>
		</c:choose>
	</section>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
