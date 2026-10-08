<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="研究協力同意確認"/>
<c:set var="screenStylesheet" value="/css/student/survey/consent.css"/>
<c:set var="screenScript" value="/js/student/survey/consent.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div class="container">
	<section class="sample-section consent-hero mb-4">
		<div class="hero-copy">
			<span class="hero-kicker">Research Consent</span>
			<h1 class="section-title mb-3">研究協力同意確認</h1>
			<p class="hero-description mb-0">
				研究協力への参加は任意です。同意しない場合も、通常の課題学習や授業演習に不利益はありません。
				回答後も、この画面で回答を変更できます。
			</p>
		</div>
		<div class="hero-status-card">
			<div class="status-label">同意状況</div>
			<div class="status-value">
				<c:choose>
					<c:when test="${consentPage.status == 'UNCONFIRMED'}">未回答</c:when>
					<c:when test="${consentPage.status == 'AGREED'}">同意</c:when>
					<c:when test="${consentPage.status == 'DECLINED'}">同意しない</c:when>
					<c:otherwise>撤回済み</c:otherwise>
				</c:choose>
			</div>
			<div class="status-meta">
				<c:choose>
					<c:when test="${not empty consentPage.respondedAt}">
						回答日時: <c:out value="${consentPage.respondedAtDisplay}"/>
					</c:when>
					<c:otherwise>説明を確認し、「同意する」または「同意しない」を選んで回答してください。</c:otherwise>
				</c:choose>
			</div>
		</div>
	</section>

	<c:if test="${not empty consentError}">
		<div class="alert alert-warning" role="alert"><c:out value="${consentError}"/></div>
	</c:if>

	<section class="sample-section consent-document-section mb-4">
		<div class="section-heading-row">
			<div>
				<h2 class="card-title mb-2">研究協力のお願い</h2>
				<p class="text-muted mb-0">登録されている有効な同意文書を確認してください。</p>
			</div>
			<c:if test="${not empty consentDocument}">
				<span class="document-pill">文書版 <c:out value="${consentDocument.versionCode}"/></span>
			</c:if>
		</div>

		<c:choose>
			<c:when test="${empty consentDocument}">
				<div class="notice-card mt-4" role="status">
					<div class="notice-title">回答できません</div>
					<p class="mb-0">現在、有効な承認済み同意文書が登録されていないため回答を受け付けていません。文書が設定されるまでお待ちください。</p>
				</div>
			</c:when>
			<c:otherwise>
				<article class="document-card mt-4">
					<h3 class="h4 mb-3"><c:out value="${consentDocument.title}"/></h3>
					<div style="white-space: pre-wrap"><c:out value="${consentDocument.body}"/></div>
					<div class="record-preview-card in-document-card mt-4">
						<div class="preview-label">この回答に記録される内容</div>
						<ul class="preview-list mb-0">
							<li>回答内容</li>
							<li>回答日時</li>
							<li>確認した同意文書のバージョン</li>
						</ul>
					</div>
				</article>
			</c:otherwise>
		</c:choose>
	</section>

	<c:if test="${not empty consentDocument}">
		<section class="sample-section consent-form-section">
			<div class="section-heading-row">
				<div>
					<h2 class="card-title mb-2">同意の選択</h2>
					<p class="text-muted mb-0">選択した回答は、確認した同意文書のバージョンとともに記録されます。</p>
				</div>
				<span class="required-badge">必須</span>
			</div>

					<noscript><div class="alert alert-warning">回答変更の確認ダイアログを表示するため、JavaScriptを有効にしてください。</div></noscript>
					<form id="consentForm" class="consent-form" method="post"
						data-current-status="${consentPage.status}"
						action="<c:url value='/student/consent'/>">
						<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
						<input type="hidden" name="documentVersionId" value="<c:out value='${consentDocument.id}'/>">
						<input type="hidden" name="responseId" value="${consentPage.responseId}">
						<input type="hidden" name="changeConfirmed" value="no">
						<div id="consentFeedback" aria-live="polite"></div>
						<div class="confirmation-check">
							<label class="check-card" for="confirmRead">
								<input id="confirmRead" type="checkbox" name="confirmRead" value="yes" required>
								<span>上記の研究協力に関する説明を読み、内容を確認しました</span>
							</label>
						</div>
						<div class="consent-choice-grid">
							<label class="choice-card agree-card" for="consentAgree">
								<input id="consentAgree" type="radio" name="consentDecision" value="agree" required
									${consentPage.status == 'AGREED' ? 'checked' : ''}>
								<span class="choice-title">同意する</span>
								<span class="choice-text">この同意文書に記載された研究目的で、対象となるデータを利用することに同意します。</span>
							</label>
							<label class="choice-card decline-card" for="consentDecline">
								<input id="consentDecline" type="radio" name="consentDecision" value="decline" required
									${consentPage.status == 'DECLINED' or consentPage.status == 'WITHDRAWN' ? 'checked' : ''}>
								<span class="choice-title">同意しない</span>
								<span class="choice-text">研究目的でのデータ利用に同意しません。回答後も、通常の課題学習を利用できます。</span>
							</label>
						</div>
						<div class="form-actions">
							<button class="btn btn-primary" type="submit">
								<c:choose><c:when test="${consentPage.status == 'UNCONFIRMED'}">回答を確定する</c:when><c:otherwise>回答を変更する</c:otherwise></c:choose>
							</button>
						</div>
					</form>
		</section>
	</c:if>

	<div class="account-footer-action text-center mt-4">
		<a class="btn btn-outline-secondary" href="<c:url value='/student/home'/>">ホームへ戻る</a>
	</div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
