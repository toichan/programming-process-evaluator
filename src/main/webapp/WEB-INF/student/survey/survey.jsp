<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<c:set var="studentMainClass" value="pt-2 pb-5"/>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<c:url var="surveyEvaluationUrl" value="/student/evaluation">
	<c:param name="assignmentId" value="${surveyPage.assignmentId}"/>
	<c:param name="submissionId" value="${surveyPage.evaluationSubmissionId}"/>
</c:url>
<c:url var="surveyCodeLogUrl" value="/student/evaluation/log">
	<c:param name="submissionId" value="${surveyPage.evaluationSubmissionId}"/>
</c:url>
	<div class="container">
		<nav class="learning-flow-nav mb-2" aria-label="学習フロー">
			<ol class="learning-flow-steps">
				<li class="learning-flow-step is-done">
					<a class="learning-flow-link is-red"
						href="<c:url value='/student/editor'><c:param name='assignmentId' value='${surveyPage.assignmentId}'/></c:url>">課題に取り組む</a>
				</li>
				<li class="learning-flow-step is-done">
					<a class="learning-flow-link is-yellow"
						href="<c:url value='/student/evaluation'><c:param name='assignmentId' value='${surveyPage.assignmentId}'/><c:param name='submissionId' value='${surveyPage.evaluationSubmissionId}'/></c:url>">評価の確認</a>
				</li>
				<li class="learning-flow-step">
					<span class="learning-flow-current is-green" aria-current="page">アンケート</span>
				</li>
			</ol>
			<div class="learning-flow-task-title">
				<span class="learning-flow-task-name">
					<span class="learning-flow-level-badge is-beginner"><c:out value="${surveyPage.difficultyLabel}"/></span>
					<span><c:out value="${surveyPage.taskTitle}"/></span>
				</span>
			</div>
		</nav>

		<section class="sample-section survey-hero mb-4">
			<div class="hero-copy">
				<span class="hero-kicker">Survey</span>
				<h1 class="section-title mb-3">アンケート</h1>
				<p class="hero-description mb-0">
					システムの評価の妥当性と自分自身の評価、試行錯誤の過程が評価されることへの考え、
					システムの操作性について回答してください。各設問では選択した理由も記入できます。
				</p>
			</div>
			<div class="hero-meta-card">
				<div class="meta-label">対象課題</div>
				<div class="meta-value"><c:out value="${surveyPage.taskTitle}"/></div>
				<div class="meta-subtext"><c:out value="${surveyPage.difficultyLabel}"/> / 研究協力同意済み</div>
				<div class="hero-meta-note">回答状況:
					<c:choose>
						<c:when test="${surveyPage.canRevise}">提出済み（「回答を修正」から変更できます）</c:when>
						<c:when test="${surveyPage.readOnly}">提出済み（回答受付終了・確認のみ）</c:when>
						<c:when test="${surveyPage.responseStatus == 'in_progress'}">回答中</c:when>
						<c:otherwise>未回答</c:otherwise>
					</c:choose>
				</div>
			</div>
		</section>

		<c:if test="${studentSurveyNotice == 'draft-saved'}">
			<div class="alert alert-success" role="status">下書きを保存しました。次回は保存したところから再開できます。</div>
		</c:if>
		<c:if test="${studentSurveyNotice == 'submitted'}">
			<div class="alert alert-success" role="status">アンケートを提出しました。ご協力ありがとうございます。</div>
		</c:if>
		<c:if test="${studentSurveyNotice == 'already-submitted'}">
			<div class="alert alert-info" role="status">この評価へのアンケートは提出済みです。回答内容を表示しています。</div>
		</c:if>
		<c:if test="${not empty surveyError}">
			<div class="alert alert-danger" role="alert"><c:out value="${surveyError}"/></div>
		</c:if>

		<section class="sample-section system-evaluation-section mb-4">
			<div class="section-heading-row">
				<div>
					<h2 class="card-title mb-2">システムが出した評価</h2>
					<p class="text-muted mb-0">この内容を見たうえで、設問に回答してください。</p>
				</div>
				<span class="summary-pill">評価提示</span>
			</div>
			<c:if test="${not empty surveyPage.evaluationFeedback}">
				<p class="mt-3"><c:out value="${surveyPage.evaluationFeedback}"/></p>
			</c:if>
			<div class="evaluation-summary-grid">
				<c:forEach var="dimension" items="${surveyPage.evaluationDimensions}">
					<article class="summary-card ${dimension.label == '主体的に学習に取り組む態度' ? 'attitude-card' : dimension.label == '思考力・判断力・表現力' ? 'thinking-card' : ''}">
						<div class="summary-card-header">
							<span class="summary-label"><c:out value="${dimension.label}"/></span>
							<strong class="summary-score">
								<c:choose>
									<c:when test="${not empty dimension.scoreText}"><c:out value="${dimension.scoreText}"/></c:when>
									<c:otherwise><c:out value="${dimension.score}"/></c:otherwise>
								</c:choose>
							</strong>
						</div>
						<c:if test="${not empty dimension.summaryDescription}">
							<p class="mb-0"><c:out value="${dimension.summaryDescription}"/></p>
						</c:if>
					</article>
				</c:forEach>
				<c:if test="${empty surveyPage.evaluationDimensions}">
					<p class="text-muted mb-0">観点別の評価結果は登録されていません。</p>
				</c:if>
			</div>
			<div class="evaluation-link-row">
				<a class="btn btn-outline-secondary" href="<c:out value='${surveyEvaluationUrl}'/>">評価を確認する</a>
				<a class="btn btn-outline-secondary" href="<c:out value='${surveyCodeLogUrl}'/>">ソースコード編集ログを確認する</a>
			</div>
		</section>

		<c:if test="${not empty surveyPage.history}">
			<section class="sample-section mb-4">
				<h2 class="card-title mb-3">以前の回答</h2>
				<ul class="survey-history-list">
					<c:forEach var="history" items="${surveyPage.history}">
					<li>
						<span>提出版 v<c:out value="${history.revisionNumber}"/> / <c:out value="${history.submittedAtDisplay}"/></span>
						<a href="<c:url value='/student/survey'><c:param name='assignmentId' value='${surveyPage.assignmentId}'/><c:param name='evaluationId' value='${history.evaluationId}'/><c:param name='surveyId' value='${history.surveyId}'/></c:url>">
							<c:choose><c:when test="${history.responseStatus == 'submitted'}">回答を確認</c:when><c:otherwise>下書きを再開</c:otherwise></c:choose>
						</a>
					</li>
					</c:forEach>
				</ul>
			</section>
		</c:if>

		<section class="sample-section questionnaire-section">
			<div class="section-heading-row questionnaire-heading">
				<div>
					<h2 class="card-title mb-2">回答フォーム</h2>
					<p class="text-muted mb-0">設問は順番に確認できます。前後の移動も可能です。</p>
				</div>
				<div id="stepIndicator" class="step-indicator" aria-live="polite"></div>
			</div>
			<div class="step-progress" id="stepProgress" aria-hidden="true"></div>

			<c:choose>
				<c:when test="${empty surveyPage.questions}">
					<div class="alert alert-info mt-4" role="status">現在回答できる設問はありません。先生に確認してください。</div>
				</c:when>
				<c:otherwise>
					<form class="survey-form" data-read-only="${surveyPage.readOnly}" data-unsaved-response="${not empty surveyError}" method="post" action="<c:url value='/student/survey'/>">
						<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
						<input type="hidden" name="assignmentId" value="<c:out value='${surveyPage.assignmentId}'/>">
						<input type="hidden" name="surveyId" value="<c:out value='${surveyPage.surveyId}'/>">
						<input type="hidden" name="evaluationId" value="<c:out value='${surveyPage.evaluationId}'/>">
						<input type="hidden" name="responseRevision" value="${surveyPage.responseRevision}">
						<c:if test="${surveyPage.canRevise}">
							<div class="survey-revision-actions mb-4">
								<p class="text-muted">提出した回答を表示しています。修正内容は再提出するまで保存されません。</p>
								<button id="editSurveyButton" class="btn btn-outline-primary" type="button">回答を修正</button>
								<button id="cancelSurveyEditButton" class="btn btn-outline-secondary" type="button" hidden>修正をやめる</button>
							</div>
						</c:if>
						<fieldset id="surveyAnswers" class="survey-answer-fields" ${surveyPage.readOnly ? 'disabled' : ''}>
						<c:forEach var="questionGroup" items="${surveyPage.questionGroups}" varStatus="groupLoop">
							<section class="question-step${groupLoop.first ? ' is-active' : ''}" data-step="${groupLoop.count}">
								<div class="question-header">
									<span class="question-kicker">設問<c:out value="${groupLoop.count}"/></span>
									<h3 tabindex="-1"><c:out value="${questionGroup.label}"/></h3>
								</div>
								<c:forEach var="question" items="${questionGroup.questions}">
									<c:set var="surveyScaleQuestion" value="${question.code == 'q1ThinkingScore' or question.code == 'q2AttitudeScore' or question.code == 'q4UsabilityScore'}"/>
									<c:set var="submittedValues" value="${surveySubmittedValues[question.questionId]}"/>
									<c:set var="selectedValues" value="${empty submittedValues ? question.selectedValues : submittedValues}"/>
									<c:set var="submittedReason" value="${surveySubmittedReasons[question.questionId]}"/>
									<article class="survey-question">
												<fieldset class="question-block survey-answer-block" data-required="${question.required}">
													<legend class="field-header-row">
														<span class="field-label survey-question-prompt"><c:out value="${question.prompt}"/></span>
														<c:choose>
															<c:when test="${question.required}"><span class="required-badge">必須</span></c:when>
															<c:otherwise><span class="optional-badge">任意</span></c:otherwise>
														</c:choose>
													</legend>
													<c:choose>
														<c:when test="${question.text}">
															<textarea class="form-control survey-textarea" name="answer_${question.questionId}"
																rows="5" maxlength="5000"><c:choose><c:when test="${not empty submittedValues}"><c:out value="${submittedValues[0]}"/></c:when><c:otherwise><c:out value="${question.answerValue}"/></c:otherwise></c:choose></textarea>
														</c:when>
														<c:when test="${empty question.options}">
															<p class="text-danger mb-0">この設問の選択肢が設定されていません。先生に確認してください。</p>
														</c:when>
														<c:otherwise>
															<div class="${question.multipleChoice ? 'survey-choice-list' : surveyScaleQuestion ? 'scale-grid' : 'likert-grid'}">
																<c:forEach var="option" items="${question.displayOptions}">
																	<label class="${question.multipleChoice ? 'survey-choice-option' : surveyScaleQuestion ? 'scale-option' : 'likert-option'}${question.code == 'q4UsabilityScore' ? ' wide-option' : ''}">
																		<input type="${question.multipleChoice ? 'checkbox' : 'radio'}"
																			name="answer_${question.questionId}" value="<c:out value='${option.value}'/>"
																			<c:forEach var="selectedValue" items="${selectedValues}"><c:if test="${selectedValue == option.value}">checked</c:if></c:forEach>>
																		<span><c:out value="${option.label}"/></span>
																	</label>
																</c:forEach>
															</div>
														</c:otherwise>
													</c:choose>
												</fieldset>
												<c:if test="${question.standardMetric or question.reasonRequired or not empty question.reasonPrompt or not empty question.answerReason}">
													<div class="question-block survey-reason-block" data-required="${question.reasonRequired}">
														<div class="field-header-row">
															<label class="field-label" for="reason-${question.questionId}"><c:out value="${question.displayReasonPrompt}"/></label>
															<c:choose>
																<c:when test="${question.reasonRequired}"><span class="required-badge">必須</span></c:when>
																<c:otherwise><span class="optional-badge">任意</span></c:otherwise>
															</c:choose>
														</div>
														<textarea id="reason-${question.questionId}" class="form-control survey-textarea"
															name="reason_${question.questionId}" rows="6" maxlength="5000"
															aria-required="${question.reasonRequired}"><c:choose><c:when test="${not empty submittedReason}"><c:out value="${submittedReason}"/></c:when><c:otherwise><c:out value="${question.answerReason}"/></c:otherwise></c:choose></textarea>
													</div>
												</c:if>
									</article>
								</c:forEach>
							</section>
						</c:forEach>
						</fieldset>

						<c:if test="${not surveyPage.readOnly}">
							<div class="form-save-actions">
								<button class="btn btn-outline-success" type="submit" name="action" value="draft"><svg class="me-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 3h13l4 4v14H3V3h1zm2 0v6h11V3M6 21v-8h12v8"/></svg>下書きを保存する</button>
							</div>
						</c:if>
						<div class="form-actions">
							<button id="prevStepButton" class="btn btn-secondary" type="button">前の設問へ</button>
							<button id="nextStepButton" class="btn btn-primary" type="button">次の設問へ</button>
							<button id="submitSurveyButton" class="btn btn-success" type="submit" name="action"
								value="${surveyPage.readOnly ? 'revise' : 'submit'}" ${surveyPage.readOnly ? 'hidden' : ''}>${surveyPage.readOnly ? '修正した回答を再提出' : 'アンケートを提出'}</button>
						</div>
					</form>
				</c:otherwise>
			</c:choose>
		</section>
	</div>
<%@ include file="/WEB-INF/student/shared/home-action.jspf" %>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
