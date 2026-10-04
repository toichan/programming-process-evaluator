<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="プロンプト設計"/>
<c:set var="screenDesign" value="teacher"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<c:set var="promptPage" value="${teacherPromptPage}"/>
<c:set var="promptVersion" value="${promptPage.selectedVersion}"/>
<div class="page-content py-5">
	<div class="container-fluid px-4">
		<section class="prompt-hero mb-4">
			<div>
				<span class="hero-kicker">Prompt Tuning</span>
				<h1 class="section-title mb-2">プロンプト設計</h1>
				<p class="hero-description mb-0">課題内容と共通プロンプトを調整し、揺らぎ項目の比較と評価例を確認します。</p>
			</div>
			<div class="hero-stats">
				<div class="stat-card">
					<div class="stat-label">対象課題</div>
					<div class="stat-value"><c:out value="${promptPage.tasks.size()}"/></div>
				</div>
				<div class="stat-card">
					<div class="stat-label">選択中の版</div>
					<div class="stat-value stat-value-small">
						<c:choose><c:when test="${not empty promptVersion}"><c:out value="${promptVersion.version}"/></c:when><c:otherwise>未選択</c:otherwise></c:choose>
					</div>
				</div>
				<div class="stat-card">
					<div class="stat-label">共通標準ルーブリック</div>
					<div class="stat-value stat-value-small">
						<c:choose><c:when test="${not empty promptPage.standardRubric}"><c:out value="${promptPage.standardRubric.version}"/></c:when><c:otherwise>課題選択後に表示</c:otherwise></c:choose>
					</div>
				</div>
			</div>
		</section>

		<c:if test="${not empty teacherPromptNotice}">
			<div class="alert alert-success" id="promptSuccessNotice" role="status"><c:out value="${teacherPromptNotice}"/></div>
		</c:if>
		<c:if test="${not empty teacherPromptError}">
			<div class="alert alert-danger" id="promptErrorNotice" role="alert"><c:out value="${teacherPromptError}"/></div>
		</c:if>

		<section class="panel-card mb-4">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div>
					<span class="hero-kicker mb-1 d-inline-block">Step 1</span>
					<h2 class="subheading mb-0">共通プロンプト設計</h2>
				</div>
				<div class="d-flex align-items-center gap-2">
					<c:if test="${not empty promptPage.selectedTask}">
						<span class="badge text-bg-light border"><c:out value="${promptPage.targetSummary}"/></span>
						<button class="btn btn-outline-secondary btn-sm" type="reset" form="promptDraftForm"
							<c:if test="${not teacherPromptEditableDraft}">disabled</c:if>>
							入力をリセット
						</button>
					</c:if>
				</div>
			</div>

			<div class="row g-3 mb-3">
				<div class="col-lg-4">
					<label for="taskSelect" class="form-label">課題を選択</label>
					<form id="taskSelectForm" method="get" action="<c:url value='/teacher/prompt'/>">
						<select class="form-select" id="taskSelect" name="taskId" onchange="this.form.submit()">
							<option value="">選択してください</option>
							<c:forEach items="${promptPage.tasks}" var="task">
								<option value="${task.taskId}" <c:if test="${task.taskId == promptPage.selectedTask.taskId}">selected</c:if>>
									<c:out value="${task.input.title}"/>
								</option>
							</c:forEach>
						</select>
					</form>
				</div>
				<div class="col-lg-4">
					<label for="modelSelect" class="form-label">AIモデル</label>
					<select class="form-select" id="modelSelect" form="promptDraftForm" name="aiModel"
						<c:if test="${not teacherPromptEditableDraft}">disabled</c:if>>
						<option value="gemini-2.5-pro" <c:if test="${teacherPromptModel == 'gemini-2.5-pro'}">selected</c:if>>Gemini 2.5 Pro</option>
						<option value="gemini-2.5-flash" <c:if test="${teacherPromptModel == 'gemini-2.5-flash'}">selected</c:if>>Gemini 2.5 Flash</option>
					</select>
				</div>
				<div class="col-lg-4">
					<label for="versionSelect" class="form-label">バージョン</label>
					<form id="versionSelectForm" method="get" action="<c:url value='/teacher/prompt'/>">
						<input type="hidden" name="taskId" value="<c:out value='${teacherPromptTaskId}'/>">
						<select class="form-select" id="versionSelect" name="promptVersionId"
							<c:if test="${empty promptPage.selectedTask or empty promptPage.versions}">disabled</c:if>
							onchange="this.form.submit()">
							<c:choose>
								<c:when test="${empty promptPage.versions}"><option value="">課題を選択してください</option></c:when>
								<c:otherwise>
									<c:forEach items="${promptPage.versions}" var="version">
										<option value="${version.promptVersionId}" <c:if test="${version.promptVersionId == promptVersion.promptVersionId}">selected</c:if>>
											<c:out value="${version.version}"/> (
											<c:choose>
												<c:when test="${version.promptStatus == 'draft'}">下書き</c:when>
												<c:when test="${version.promptStatus == 'configured'}">設定済み</c:when>
												<c:when test="${version.promptStatus == 'active'}">適用中</c:when>
												<c:otherwise>版管理済み</c:otherwise>
											</c:choose>)
										</option>
									</c:forEach>
								</c:otherwise>
							</c:choose>
						</select>
					</form>
				</div>
			</div>

			<c:if test="${empty promptPage.selectedTask}">
				<div class="empty-state mb-3">教師が作成した課題下書きを選択してください。公開済み課題はこの画面では変更できません。</div>
			</c:if>
			<c:if test="${not empty promptPage.selectedTask}">
				<c:if test="${not empty promptPage.standardRubric}">
					<div class="alert alert-light border mb-3" role="status">
						評価用ルーブリック: <strong><c:out value="${promptPage.standardRubric.title}"/> / <c:out value="${promptPage.standardRubric.version}"/></strong>
						（課題共通。画面からの選択・変更はできません）
					</div>
				</c:if>
				<form id="promptDraftForm" method="post" action="<c:url value='/teacher/prompt'/>">
					<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
					<input type="hidden" name="taskId" value="<c:out value='${teacherPromptTaskId}'/>">
					<input type="hidden" name="promptVersionId" value="<c:out value='${teacherPromptVersionId}'/>">
					<input type="hidden" name="expectedRowVersion" value="<c:out value='${empty promptVersion ? 0 : promptVersion.rowVersion}'/>">
					<input type="hidden" id="promptAdditionalInstructionForDraft" name="additionalInstruction"
						value="<c:out value='${teacherPromptAdditionalInstruction}'/>">
					<div class="mb-3">
						<label for="evaluationPromptInput" class="form-label">共通プロンプト</label>
						<textarea id="evaluationPromptInput" name="commonPrompt" class="form-control code-editor-input"
							rows="16" maxlength="120000" placeholder="AI評価用の指示文を入力"
							<c:if test="${not teacherPromptEditableDraft}">readonly</c:if>><c:out value="${teacherPromptText}"/></textarea>
					</div>
					<div class="d-flex gap-2 flex-wrap justify-content-center">
						<button class="btn btn-primary" type="submit" name="action" value="generateFluctuations"
							<c:if test="${not teacherPromptEditableDraft}">disabled</c:if>>
							確定して揺らぎ項目を生成する
						</button>
						<button class="btn btn-outline-primary" type="submit" name="action" value="saveDraft"
							<c:if test="${not teacherPromptEditableDraft}">disabled</c:if>>
							共通プロンプトを保存
						</button>
					</div>
				</form>
			</c:if>
		</section>

		<section class="panel-card mb-4" id="fluctuationSection">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div>
					<span class="hero-kicker mb-1 d-inline-block">Step 2</span>
					<h2 class="subheading mb-0">揺らぎ項目指示</h2>
				</div>
				<span class="badge text-bg-light border" id="fluctuationStatus">
					<c:choose><c:when test="${promptVersion.fluctuationGenerationStatus == 'completed'}">生成済み</c:when><c:when test="${promptVersion.fluctuationGenerationStatus == 'in_progress'}">生成中</c:when><c:when test="${promptVersion.fluctuationGenerationStatus == 'failed'}">生成失敗</c:when><c:otherwise>未生成</c:otherwise></c:choose>
				</span>
			</div>

			<c:choose>
				<c:when test="${empty promptPage.selectedTask}">
					<div class="empty-state">課題を選択すると揺らぎ項目が表示されます。</div>
				</c:when>
				<c:when test="${empty promptVersion.fluctuationItems}">
					<div class="empty-state">「確定して揺らぎ項目を生成する」を押すと、AIが揺らぎ候補を生成します。</div>
				</c:when>
				<c:otherwise>
					<form method="post" action="<c:url value='/teacher/prompt'/>" id="promptResolutionForm">
						<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
						<input type="hidden" name="taskId" value="<c:out value='${teacherPromptTaskId}'/>">
						<input type="hidden" name="promptVersionId" value="<c:out value='${promptVersion.promptVersionId}'/>">
						<input type="hidden" name="expectedRowVersion" value="<c:out value='${promptVersion.rowVersion}'/>">
						<div class="fluctuation-list">
							<c:forEach items="${promptVersion.fluctuationItems}" var="item" varStatus="loop">
								<article class="fluctuation-item">
									<div class="fluctuation-head">
										<h3 class="fluctuation-title"><c:out value="${item.title}"/></h3>
										<span class="fluctuation-meta">リスク: <c:out value="${item.level}"/></span>
									</div>
									<p class="fluctuation-description"><c:out value="${item.description}"/></p>
									<p class="fluctuation-example"><c:out value="${item.example}"/></p>
									<input type="hidden" name="fluctuationIds" value="<c:out value='${item.id}'/>">
									<div class="row g-3">
										<div class="col-md-4">
											<label class="form-label" for="resolutionStatus-${item.id}">対応状態</label>
											<select class="form-select" id="resolutionStatus-${item.id}" name="resolutionStatuses"
												<c:if test="${not teacherPromptEditableDraft}">disabled</c:if>>
												<c:set var="resolutionStatusValue" value="${teacherPromptSubmittedValues.resolutionStatuses[loop.index]}"/>
												<c:if test="${empty resolutionStatusValue}"><c:set var="resolutionStatusValue" value="${item.resolutionStatus}"/></c:if>
												<option value="pending" <c:if test="${resolutionStatusValue == 'pending'}">selected</c:if>>未対応</option>
												<option value="resolved" <c:if test="${resolutionStatusValue == 'resolved'}">selected</c:if>>対応済み</option>
												<option value="not_applicable" <c:if test="${resolutionStatusValue == 'not_applicable'}">selected</c:if>>該当なし</option>
											</select>
										</div>
										<div class="col-md-8">
											<label class="form-label" for="teacherResolution-${item.id}">教師対応</label>
											<textarea class="form-control" id="teacherResolution-${item.id}" name="teacherResolutions"
												rows="2" maxlength="4000" <c:if test="${not teacherPromptEditableDraft}">readonly</c:if>><c:choose><c:when test="${not empty teacherPromptSubmittedValues.teacherResolutions[loop.index]}"><c:out value="${teacherPromptSubmittedValues.teacherResolutions[loop.index]}"/></c:when><c:otherwise><c:out value="${item.teacherResolution}"/></c:otherwise></c:choose></textarea>
										</div>
									</div>
								</article>
							</c:forEach>
						</div>
						<div class="mt-3">
							<label for="additionalInstructionInput" class="form-label">課題ごとの追加評価指示</label>
							<textarea id="additionalInstructionInput" name="additionalInstruction" class="form-control"
								rows="3" maxlength="4000" <c:if test="${not teacherPromptEditableDraft}">readonly</c:if>><c:out value="${teacherPromptAdditionalInstruction}"/></textarea>
						</div>
						<div class="d-flex gap-2 mt-3 flex-wrap justify-content-center">
							<button class="btn btn-outline-primary" type="submit" name="action" value="saveResolutions"
								<c:if test="${not teacherPromptCanSaveResolutions}">disabled</c:if>>
								教師対応を保存
							</button>
							<button class="btn btn-primary" type="submit" name="action" value="generateEvaluationExamples"
								<c:if test="${not teacherPromptCanGenerateExamples}">disabled</c:if>>
								確定して評価例を生成する
							</button>
						</div>
					</form>
				</c:otherwise>
			</c:choose>
		</section>

		<section class="panel-card mb-4" id="evaluationExamplesSection">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div>
					<span class="hero-kicker mb-1 d-inline-block">Step 3</span>
					<h2 class="subheading mb-0">評価例確認</h2>
				</div>
				<span class="badge text-bg-light border" id="evaluationExamplesStatus">
					<c:choose><c:when test="${promptVersion.evaluationExamplesStatus == 'completed'}">生成済み</c:when><c:when test="${promptVersion.evaluationExamplesStatus == 'in_progress'}">生成中</c:when><c:when test="${promptVersion.evaluationExamplesStatus == 'failed'}">生成失敗</c:when><c:otherwise>未生成</c:otherwise></c:choose>
				</span>
			</div>
			<c:choose>
				<c:when test="${empty promptVersion.evaluationExamples}">
					<div class="empty-state">教師対応を完了してから評価例を生成すると、合成データによる結果がここに表示されます。</div>
				</c:when>
				<c:otherwise>
					<div class="evaluation-examples-list">
						<c:forEach items="${promptVersion.evaluationExamples}" var="example" varStatus="exampleStatus">
							<c:forEach items="${example.simulatedResults}" var="result">
								<article class="evaluation-example-item">
									<div class="example-scores">
										<div>
											<strong>合成評価例 <c:out value="${exampleStatus.count}"/> ・ <c:out value="${result.sampleId}"/></strong>
											<span class="badge text-bg-secondary"><c:out value="${example.status}"/></span>
										</div>
										<div class="score-item">
											<span class="score-label">思考力・判断力・表現力</span>
											<span class="score-value"><c:out value="${result.thinkingExpressionLevel}"/> / 5</span>
										</div>
										<div class="score-item">
											<span class="score-label">主体的に学習に取り組む態度</span>
											<span class="score-value"><c:out value="${result.proactiveAttitudeLevel}"/> / 5</span>
										</div>
									</div>
									<details class="w-100">
										<summary class="btn btn-sm btn-outline-primary">評価例の詳細</summary>
										<div class="evaluation-example-detail-wrapper">
											<p><strong>想定ケース:</strong> <c:out value="${result.scenario}"/></p>
											<h3 class="reason-title">合成コード例</h3>
											<pre class="mt-2 p-3 bg-dark text-light border rounded text-wrap"><code><c:out value="${result.syntheticCode}"/></code></pre>
											<p class="text-secondary"><strong>テスト結果の推定:</strong> <c:out value="${result.predictedTestcaseResult}"/></p>
											<div class="score-reasons-container">
												<div class="score-reason-item">
													<h4 class="reason-title">思考力・判断力・表現力 ・ <c:out value="${result.thinkingExpressionLevel}"/> / 5</h4>
													<p class="reason-body"><c:out value="${result.thinkingExpressionReason}"/></p>
												</div>
												<div class="score-reason-item">
													<h4 class="reason-title">主体的に学習に取り組む態度 ・ <c:out value="${result.proactiveAttitudeLevel}"/> / 5</h4>
													<p class="reason-body"><c:out value="${result.proactiveAttitudeReason}"/></p>
												</div>
											</div>
										</div>
									</details>
								</article>
							</c:forEach>
							<c:if test="${not empty example.varianceAlerts}">
								<div class="alert alert-warning">
									<strong>評価の揺らぎ候補</strong>
									<ul class="mb-0">
										<c:forEach items="${example.varianceAlerts}" var="alert"><li><c:out value="${alert}"/></li></c:forEach>
									</ul>
								</div>
							</c:if>
							<c:if test="${not empty example.preReevalChecklist}">
								<div class="alert alert-light border">
									<strong>再評価前の確認事項</strong>
									<ul class="mb-0">
										<c:forEach items="${example.preReevalChecklist}" var="check"><li><c:out value="${check}"/></li></c:forEach>
									</ul>
								</div>
							</c:if>
						</c:forEach>
					</div>
				</c:otherwise>
			</c:choose>
			<div class="d-flex gap-2 mt-3 flex-wrap justify-content-center">
				<button class="btn btn-primary" type="button" disabled
					title="再評価対象の差分プレビュー、提出固定、通知の契約が未確定です。">
					設定して再評価する
				</button>
				<form method="post" action="<c:url value='/teacher/prompt'/>">
					<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
					<input type="hidden" name="taskId" value="<c:out value='${teacherPromptTaskId}'/>">
					<input type="hidden" name="promptVersionId" value="<c:out value='${promptVersion.promptVersionId}'/>">
					<input type="hidden" name="expectedRowVersion" value="<c:out value='${promptVersion.rowVersion}'/>">
					<button class="btn btn-outline-primary" type="submit" name="action" value="saveEvaluationExamples"
						<c:if test="${not teacherPromptCanSaveExamples}">disabled</c:if>>
						評価例を保存
					</button>
				</form>
			</div>
			<p class="text-secondary small text-center mt-2 mb-0">
				再評価は、対象提出の固定方法・差分プレビュー・通知経路が確定するまで実行できません。
			</p>
		</section>

		<section class="panel-card">
			<div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
				<div>
					<span class="hero-kicker mb-1 d-inline-block">History</span>
					<h2 class="subheading mb-0">更新履歴</h2>
				</div>
			</div>
			<div class="table-responsive">
				<table class="table table-hover align-middle mb-0" id="historyTable">
					<thead class="table-light">
						<tr><th>課題名</th><th>対象</th><th>バージョン</th><th>状態</th><th>作成日時</th><th>作成者</th><th>更新日時</th><th>更新者</th><th>操作</th></tr>
					</thead>
					<tbody>
						<c:choose>
							<c:when test="${empty promptPage.versions}">
								<tr><td colspan="9" class="text-secondary">この課題に保存済みのプロンプト版はありません。</td></tr>
							</c:when>
							<c:otherwise>
								<c:forEach items="${promptPage.versions}" var="version">
									<tr>
										<td><c:out value="${promptPage.selectedTask.input.title}"/></td>
										<td><c:out value="${promptPage.targetSummary}"/></td>
										<td><c:out value="${version.version}"/></td>
										<td>
											<span class="badge text-bg-secondary">
												<c:choose>
													<c:when test="${version.promptStatus == 'draft'}">下書き</c:when>
													<c:when test="${version.promptStatus == 'configured'}">設定済み</c:when>
													<c:when test="${version.promptStatus == 'active'}">適用中</c:when>
													<c:otherwise>版管理済み</c:otherwise>
												</c:choose>
											</span>
										</td>
										<td><c:out value="${version.createdAt}"/></td>
										<td><code class="history-creator-id"><c:out value="${version.createdByLoginId}"/></code></td>
										<td><c:out value="${version.updatedAt}"/></td>
										<td><c:out value="${version.updatedByLoginId}"/></td>
										<td>
											<form method="post" action="<c:url value='/teacher/prompt'/>">
												<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
												<input type="hidden" name="taskId" value="<c:out value='${teacherPromptTaskId}'/>">
												<input type="hidden" name="promptVersionId" value="<c:out value='${version.promptVersionId}'/>">
												<input type="hidden" name="expectedRowVersion" value="<c:out value='${version.rowVersion}'/>">
												<button class="btn btn-sm btn-outline-secondary" type="submit" name="action" value="duplicateDraft">複製</button>
											</form>
										</td>
									</tr>
								</c:forEach>
							</c:otherwise>
						</c:choose>
					</tbody>
				</table>
			</div>
			<c:if test="${not empty promptPage.auditEntries}">
				<h3 class="subheading mt-4">監査履歴</h3>
				<ul class="list-group list-group-flush">
					<c:forEach items="${promptPage.auditEntries}" var="entry">
						<li class="list-group-item px-0">
							<time><c:out value="${entry.occurredAt}"/></time>
							<strong class="ms-2"><c:out value="${entry.detail}"/></strong>
							<span class="text-secondary ms-2"><c:out value="${entry.actorLoginId}"/> ・ <c:out value="${entry.resultStatus}"/></span>
						</li>
					</c:forEach>
				</ul>
			</c:if>
		</section>
	</div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
