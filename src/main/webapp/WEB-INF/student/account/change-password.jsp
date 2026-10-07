<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="パスワード変更"/>
<c:set var="screenStylesheet" value="/css/student/account/password.css"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div class="container">
	<section class="sample-section password-hero mb-4">
		<div class="hero-copy">
			<span class="hero-kicker">Password Update</span>
			<h1 class="section-title mb-3">パスワード変更</h1>
			<p class="hero-description mb-0">
				現在のパスワードを確認し、新しいパスワードを設定してください。パスワードを忘れた場合は担当者へご相談ください。
			</p>
		</div>
		<div class="hero-status-card">
			<div class="status-label">パスワード更新状況</div>
			<div class="status-value">
				<c:choose>
					<c:when test="${passwordChangeRequired}">変更が必要です</c:when>
					<c:otherwise>変更できます</c:otherwise>
				</c:choose>
			</div>
			<div class="status-meta">
				<c:choose>
					<c:when test="${passwordChangeRequired}">変更が完了すると、ホーム画面へ進みます。</c:when>
					<c:otherwise>変更が完了すると、アカウント情報画面へ戻ります。</c:otherwise>
				</c:choose>
			</div>
		</div>
	</section>

	<div class="row g-4 align-items-start password-content-row">
		<div class="col-12 col-xl-7">
			<section class="sample-section password-form-section">
				<div class="section-heading-row mb-4">
					<div>
						<h2 class="card-title mb-2">変更フォーム</h2>
						<p class="text-muted mb-0">現在のパスワードと新しいパスワードを入力してください。</p>
					</div>
				</div>
				<div id="auth-feedback" aria-live="assertive"></div>
				<c:if test="${not empty passwordChangeError}">
					<span class="d-none" data-auth-error="<c:out value='${passwordChangeError}'/>"></span>
				</c:if>
				<form id="passwordChangeForm" class="password-form" method="post"
					action="<c:url value='/student/account/password'/>">
					<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
					<div class="field-block">
						<div class="field-header-row">
							<label class="field-label" for="currentPassword">現在のパスワード</label>
							<span class="required-badge">必須</span>
						</div>
						<div class="password-input-wrap">
							<input id="currentPassword" class="form-control form-control-lg" name="currentPassword"
								type="password" autocomplete="current-password" maxlength="256" required>
							<button class="password-visibility-toggle" type="button" data-password-toggle="currentPassword"
								aria-label="現在のパスワードを表示">
								<%@ include file="/WEB-INF/student/account/password-icons.jspf" %>
							</button>
						</div>
					</div>
					<div class="field-block">
						<div class="field-header-row">
							<label class="field-label" for="newPassword">新しいパスワード</label>
							<span class="required-badge">必須</span>
						</div>
						<div class="password-input-wrap">
							<input id="newPassword" class="form-control form-control-lg" name="newPassword"
								type="password" autocomplete="new-password" maxlength="256" aria-describedby="passwordRuleSummary" required>
							<button class="password-visibility-toggle" type="button" data-password-toggle="newPassword"
								aria-label="新しいパスワードを表示">
								<%@ include file="/WEB-INF/student/account/password-icons.jspf" %>
							</button>
						</div>
					</div>
					<div class="field-block">
						<div class="field-header-row">
							<label class="field-label" for="confirmPassword">新しいパスワード（確認用）</label>
							<span class="required-badge">必須</span>
						</div>
						<div class="password-input-wrap">
							<input id="confirmPassword" class="form-control form-control-lg" name="confirmPassword"
								type="password" autocomplete="new-password" maxlength="256" aria-describedby="passwordRuleSummary" required>
							<button class="password-visibility-toggle" type="button" data-password-toggle="confirmPassword"
								aria-label="確認用パスワードを表示">
								<%@ include file="/WEB-INF/student/account/password-icons.jspf" %>
							</button>
						</div>
					</div>
					<div class="form-actions mt-4">
						<div class="password-return-action">
							<c:choose>
								<c:when test="${passwordChangeRequired}">
									<button class="btn btn-outline-secondary" type="button" disabled aria-describedby="passwordReturnNotice">アカウント情報へ戻る</button>
									<p id="passwordReturnNotice" class="password-return-notice">パスワード変更が完了するまで戻れません。</p>
								</c:when>
								<c:otherwise><a class="btn btn-outline-secondary" href="<c:url value='/student/account'/>">アカウント情報へ戻る</a></c:otherwise>
							</c:choose>
						</div>
						<button id="savePasswordButton" class="btn btn-primary" type="submit">パスワードを更新</button>
					</div>
					<noscript><p class="text-muted small">入力内容は更新時に確認します。</p></noscript>
				</form>
			</section>
		</div>
		<div class="col-12 col-xl-5">
			<aside class="rule-card">
				<h2 class="rule-title mb-3">パスワード要件</h2>
				<p id="passwordRuleSummary" class="rule-summary" role="status" aria-live="polite">入力すると条件を確認できます。</p>
				<ul class="rule-list mb-0">
					<li data-password-rule="length"><span class="rule-indicator" aria-hidden="true">○</span><div>8文字以上32文字以下<small class="rule-status">入力待ち</small></div></li>
					<li data-password-rule="characters"><span class="rule-indicator" aria-hidden="true">○</span><div>半角英数字・記号のみ<small class="rule-status">空白・全角文字・制御文字は使用できません。</small></div></li>
					<li data-password-rule="categories"><span class="rule-indicator" aria-hidden="true">○</span><div>英大文字・英小文字・数字・記号のうち3種類以上<small class="rule-status">入力待ち</small></div></li>
					<li data-password-rule="different"><span class="rule-indicator" aria-hidden="true">○</span><div>現在のパスワードと異なる<small class="rule-status">入力待ち</small></div></li>
					<li data-password-rule="match"><span class="rule-indicator" aria-hidden="true">○</span><div>確認用パスワードと一致<small class="rule-status">入力待ち</small></div></li>
				</ul>
			</aside>
		</div>
	</div>
</div>
<script src="${pageContext.request.contextPath}/js/shared/auth-feedback.js" defer></script>
<script src="${pageContext.request.contextPath}/js/shared/password-visibility.js" defer></script>
<script src="${pageContext.request.contextPath}/js/student/account/password.js" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
