<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="teacher"/>
<c:set var="screenBodyClass" value="teacher-self-account-screen"/>
<c:set var="screenPageTitle" value="教師パスワード変更"/>
<c:set var="screenStylesheet" value="/css/student/account/password.css"/>
<c:set var="screenScript" value="/js/teacher/account/password.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5">
	<div class="container-fluid px-4">
		<section class="sample-section password-hero mb-4">
			<div>
				<span class="hero-kicker">Password Settings</span>
				<h1 class="section-title mb-3">パスワード変更</h1>
				<p class="hero-description mb-0"><c:choose><c:when test="${passwordChangeRequired}">初期または再設定されたパスワードを変更してください。変更完了まで通常の機能は利用できません。</c:when><c:otherwise>現在のパスワードを確認して、新しいパスワードを設定します。</c:otherwise></c:choose></p>
			</div>
			<div class="hero-status-card">
				<div class="status-label">変更後のログイン</div>
				<div class="status-value">再ログインが必要です</div>
				<p class="status-meta mb-0">変更すると、現在のログインを含むすべての端末のログインが失効します。</p>
			</div>
		</section>
		<div class="row g-4 align-items-start password-content-row">
			<div class="col-12 col-xl-7">
				<section class="sample-section password-form-section">
					<h2 class="card-title mb-4">変更フォーム</h2>
					<div id="auth-feedback" aria-live="assertive"></div>
					<c:if test="${not empty passwordChangeError}"><span class="d-none" data-auth-error="<c:out value='${passwordChangeError}'/>"></span></c:if>
					<form id="passwordChangeForm" class="password-form" method="post" action="<c:url value='/teacher/account/password'/>">
						<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
						<input type="hidden" name="version" value="<c:out value='${teacherAccount.version}'/>">
						<input type="hidden" id="changeConfirmed" value="no">
						<div class="field-block">
							<div class="field-header-row"><label class="field-label" for="currentPassword">現在のパスワード</label><span class="required-badge">必須</span></div>
							<div class="password-input-wrap">
								<input id="currentPassword" name="currentPassword" class="form-control form-control-lg" type="password" autocomplete="current-password" maxlength="256" required>
								<button class="password-visibility-toggle" type="button" data-password-toggle="currentPassword" aria-label="現在のパスワードを表示"><%@ include file="/WEB-INF/student/account/password-icons.jspf" %></button>
							</div>
						</div>
						<div class="field-block">
							<div class="field-header-row"><label class="field-label" for="newPassword">新しいパスワード</label><span class="required-badge">必須</span></div>
							<div class="password-input-wrap">
								<input id="newPassword" name="newPassword" class="form-control form-control-lg" type="password" autocomplete="new-password" aria-describedby="passwordRuleSummary" maxlength="256" required>
								<button class="password-visibility-toggle" type="button" data-password-toggle="newPassword" aria-label="新しいパスワードを表示"><%@ include file="/WEB-INF/student/account/password-icons.jspf" %></button>
							</div>
						</div>
						<div class="field-block">
							<div class="field-header-row"><label class="field-label" for="confirmPassword">新しいパスワード（確認用）</label><span class="required-badge">必須</span></div>
							<div class="password-input-wrap">
								<input id="confirmPassword" name="confirmPassword" class="form-control form-control-lg" type="password" autocomplete="new-password" aria-describedby="passwordRuleSummary" maxlength="256" required>
								<button class="password-visibility-toggle" type="button" data-password-toggle="confirmPassword" aria-label="確認用パスワードを表示"><%@ include file="/WEB-INF/student/account/password-icons.jspf" %></button>
							</div>
						</div>
						<div class="form-actions mt-4">
							<div class="password-return-action">
								<c:choose><c:when test="${passwordChangeRequired}">
									<button class="btn btn-outline-secondary" type="button" disabled aria-describedby="passwordReturnNotice">アカウント情報へ戻る</button>
									<p id="passwordReturnNotice" class="password-return-notice">パスワード変更が完了するまで戻れません。</p>
								</c:when><c:otherwise><a class="btn btn-outline-secondary" href="<c:url value='/teacher/account/profile'/>">アカウント情報へ戻る</a></c:otherwise></c:choose>
							</div>
							<button id="savePasswordButton" class="btn btn-primary" type="submit">パスワードを変更する</button>
						</div>
						<noscript><label class="mt-3"><input type="checkbox" name="changeConfirmed" value="yes" required> 変更後にすべての端末で再ログインが必要になることを確認しました。</label></noscript>
					</form>
					<p class="text-muted small mt-4 mb-0">現在のパスワードを忘れた場合は、管理者へ再設定を依頼してください。</p>
				</section>
			</div>
			<div class="col-12 col-xl-5">
				<aside class="rule-card">
					<h2 class="rule-title mb-3">パスワード要件</h2>
					<p id="passwordRuleSummary" class="rule-summary" role="status" aria-live="polite">入力すると条件を確認できます。</p>
					<ul class="rule-list mb-0">
						<li data-password-rule="length"><span class="rule-indicator" aria-hidden="true">○</span><div>8文字以上32文字以下<small class="rule-status">入力待ち</small></div></li>
						<li data-password-rule="characters"><span class="rule-indicator" aria-hidden="true">○</span><div>半角英数字・記号のみ<small class="rule-status">入力待ち</small></div></li>
						<li data-password-rule="categories"><span class="rule-indicator" aria-hidden="true">○</span><div>英大文字・英小文字・数字・記号のうち3種類以上<small class="rule-status">入力待ち</small></div></li>
						<li data-password-rule="different"><span class="rule-indicator" aria-hidden="true">○</span><div>現在のパスワードと異なる<small class="rule-status">入力待ち</small></div></li>
						<li data-password-rule="match"><span class="rule-indicator" aria-hidden="true">○</span><div>確認用パスワードと一致<small class="rule-status">入力待ち</small></div></li>
					</ul>
				</aside>
			</div>
		</div>
	</div>
</div>
<script src="${pageContext.request.contextPath}/js/shared/auth-feedback.js" defer></script>
<script src="${pageContext.request.contextPath}/js/shared/password-visibility.js" defer></script>
<script src="${pageContext.request.contextPath}/js/student/account/password.js" defer></script>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
