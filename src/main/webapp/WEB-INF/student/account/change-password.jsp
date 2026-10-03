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
			<div class="status-meta">新しいパスワードを設定すると、通常の画面へ進めます。</div>
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
					action="<c:url value='/student/account/change-password'/>">
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
								<svg class="password-toggle-icon password-toggle-show" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
									<path d="M16 8s-3-5.5-8-5.5S0 8 0 8s3.5 5.5 8 5.5S16 8 16 8z"/>
									<path d="M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"/>
								</svg>
								<span class="password-toggle-hide" aria-hidden="true">隠す</span>
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
								type="password" autocomplete="new-password" maxlength="256" required>
							<button class="password-visibility-toggle" type="button" data-password-toggle="newPassword"
								aria-label="新しいパスワードを表示">
								<svg class="password-toggle-icon password-toggle-show" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
									<path d="M16 8s-3-5.5-8-5.5S0 8 0 8s3.5 5.5 8 5.5S16 8 16 8z"/>
									<path d="M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"/>
								</svg>
								<span class="password-toggle-hide" aria-hidden="true">隠す</span>
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
								type="password" autocomplete="new-password" maxlength="256" required>
							<button class="password-visibility-toggle" type="button" data-password-toggle="confirmPassword"
								aria-label="確認用パスワードを表示">
								<svg class="password-toggle-icon password-toggle-show" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
									<path d="M16 8s-3-5.5-8-5.5S0 8 0 8s3.5 5.5 8 5.5S16 8 16 8z"/>
									<path d="M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"/>
								</svg>
								<span class="password-toggle-hide" aria-hidden="true">隠す</span>
							</button>
						</div>
					</div>
					<div class="form-actions mt-4">
						<button class="btn btn-primary btn-lg" type="submit">パスワードを更新</button>
						<a class="btn btn-outline-secondary" href="<c:url value='/student/account/account'/>">アカウント情報へ戻る</a>
					</div>
				</form>
			</section>
		</div>
		<div class="col-12 col-xl-5">
			<aside class="rule-card">
				<h2 class="rule-title h5 mb-3">パスワード要件</h2>
				<p class="rule-summary">新しいパスワードは次の条件を満たしてください。</p>
				<ul class="rule-list mb-0">
					<li>8文字以上32文字以下</li>
					<li>英大文字・英小文字・数字・記号のうち3種類以上を含む</li>
					<li>現在のパスワードと異なる</li>
				</ul>
			</aside>
		</div>
	</div>
</div>
<script src="${pageContext.request.contextPath}/js/shared/auth-feedback.js" defer></script>
<script src="${pageContext.request.contextPath}/js/shared/password-visibility.js" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
