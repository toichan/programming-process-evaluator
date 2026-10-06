<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:if test="${studentPortal}">
	<c:set var="screenDesign" value="student"/>
	<c:set var="screenPageTitle" value="ログイン"/>
	<c:set var="screenStylesheet" value="/css/student/account/login.css?v=help-modal"/>
</c:if>
<c:if test="${not studentPortal}">
	<c:set var="screenDesign" value="teacher-login"/>
	<c:set var="screenPageTitle" value="教師ログイン"/>
</c:if>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<c:choose>
	<c:when test="${studentPortal}">
		<main class="py-5">
		<div class="container">
			<section class="login-hero-section mx-auto" style="max-width: 760px;">
				<div class="hero-card p-5 rounded-4">
					<button class="login-help-icon" type="button"
						data-bs-toggle="modal" data-bs-target="#loginHelpModal"
						aria-label="ログインのヘルプを開く" title="ヘルプ">
						<svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
							<path d="M8 16A8 8 0 1 0 8 0a8 8 0 0 0 0 16zm0-14.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13z"/>
							<path d="M5.255 5.786a.237.237 0 0 0 .241.247h.825c.138 0 .247-.113.266-.25.09-.656.54-1.134 1.342-1.134.686 0 1.314.343 1.314 1.168 0 .635-.374.927-.965 1.371-.673.503-1.206.94-1.168 1.987l.003.217a.25.25 0 0 0 .25.246h.811a.25.25 0 0 0 .25-.25v-.105c0-.718.273-.927 1.01-1.486.609-.463 1.244-.977 1.244-2.056 0-1.511-1.276-2.241-2.673-2.241-1.267 0-2.655.59-2.75 2.286z"/>
							<circle cx="8" cy="12.2" r="1"/>
						</svg>
						<span class="login-help-label">ヘルプ</span>
					</button>
					<div class="hero-brand text-center mb-4">
						<div class="navbar-brand d-flex align-items-center justify-content-center py-5">
							<svg class="me-3" width="96" height="96" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
								<path d="M4 4.5C4 3.67 4.67 3 5.5 3h13c.83 0 1.5.67 1.5 1.5v15c0 .83-.67 1.5-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5v-15zM5.5 4a.5.5 0 0 0-.5.5V8h15V4.5a.5.5 0 0 0-.5-.5h-14zM5 9v10.5c0 .28.22.5.5.5h13a.5.5 0 0 0 .5-.5V9H5z"/>
								<path d="M8.75 13.5L7.25 12l1.5-1.5m6.5 3L16.75 12l-1.5-1.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
								<path d="M11 13.5h2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
							</svg>
							<div class="brand-text text-start">
								<span class="brand-line d-block">Programming</span>
								<span class="brand-line d-block">Process</span>
								<span class="brand-line d-block">Evaluator</span>
							</div>
						</div>
						<p class="hero-subtitle text-muted mt-3 mb-0">配布されたアカウントにログインしてください。</p>
					</div>

					<div class="sample-card login-panel p-4">
						<h1 class="card-title h3">生徒ログイン</h1>
						<div id="auth-feedback" aria-live="assertive"></div>
						<c:if test="${not empty loginError}">
							<span class="d-none" data-auth-error="<c:out value='${loginError}'/>"></span>
						</c:if>
						<form id="student-login" class="login-form" method="post"
							action="<c:url value='/student/account/login'/>">
							<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
							<div>
								<label for="loginId" class="form-label">ID</label>
								<input type="text" class="form-control" id="loginId" name="loginId"
									placeholder="s000" autocomplete="username" maxlength="64" required>
							</div>
							<div>
								<label for="password" class="form-label">パスワード</label>
								<div class="password-field">
									<input type="password" class="form-control" id="password" name="password"
										placeholder="••••••••" autocomplete="current-password" maxlength="256" required>
									<button type="button" class="password-toggle" data-password-toggle="password"
										aria-label="パスワードを表示">
										<svg class="password-toggle-icon password-toggle-show" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
											<path d="M16 8s-3-5.5-8-5.5S0 8 0 8s3.5 5.5 8 5.5S16 8 16 8zM1.173 8a13.133 13.133 0 0 1 1.66-2.043C4.12 4.668 5.88 3.5 8 3.5c2.12 0 3.879 1.168 5.168 2.457A13.133 13.133 0 0 1 14.828 8c-.058.087-.122.183-.195.288-.335.48-.83 1.12-1.465 1.755C11.879 11.332 10.12 12.5 8 12.5c-2.12 0-3.879-1.168-5.168-2.457A13.134 13.134 0 0 1 1.172 8z"/>
											<path d="M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM6.5 8a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0z"/>
										</svg>
										<svg class="password-toggle-icon password-toggle-hide" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
											<path d="M13.359 11.238C14.428 10.141 15.141 8.99 15.562 8c-.74-1.739-2.878-4.5-7.562-4.5-1.153 0-2.188.167-3.118.468l.787.787A8.99 8.99 0 0 1 8 4.5c3.866 0 5.79 2.14 6.59 3.5-.278.473-.709 1.12-1.317 1.778l.086.086z"/>
											<path d="M11.297 9.176a3.5 3.5 0 0 0-4.473-4.473l.823.823a2.5 2.5 0 0 1 2.827 2.827l.823.823z"/>
											<path d="M4.11 5.696A13.346 13.346 0 0 0 1.613 8c.795 1.36 2.719 3.5 6.387 3.5 1.294 0 2.397-.266 3.334-.697l.77.77C11.013 12.181 9.64 12.5 8 12.5 3.316 12.5 1.178 9.739.438 8c.45-1.053 1.282-2.336 2.466-3.462l1.206 1.158z"/>
											<path d="M3.354 1.646a.5.5 0 1 0-.708.708l10 10a.5.5 0 0 0 .708-.708l-10-10z"/>
										</svg>
									</button>
								</div>
							</div>
							<button type="submit" class="btn btn-primary w-100 btn-lg">ログイン</button>
						</form>
						<div class="mt-4 text-center">
							<a class="btn btn-link text-decoration-none" href="<c:url value='/teacher/account/login'/>">教師用ログインはこちら</a>
						</div>
					</div>
				</div>
			</section>
		</div>
		</main>
		<div class="modal fade" id="loginHelpModal" tabindex="-1" aria-labelledby="loginHelpModalLabel" aria-hidden="true">
			<div class="modal-dialog modal-dialog-centered">
				<div class="modal-content">
					<div class="modal-header">
						<h2 class="modal-title h5" id="loginHelpModalLabel">ログインでお困りの場合</h2>
						<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
					</div>
					<div class="modal-body">
						<p>ログインIDを忘れた場合や、パスワードを忘れてログインできない場合は、<strong>担当者にお問い合わせください。</strong></p>
						<p class="mb-0">この画面ではパスワードを再設定できません。</p>
					</div>
					<div class="modal-footer">
						<button type="button" class="btn btn-primary" data-bs-dismiss="modal">閉じる</button>
					</div>
				</div>
			</div>
		</div>
	</c:when>
	<c:otherwise>
		<main class="py-5">
			<div class="container">
				<section class="login-hero-section mx-auto">
					<div class="hero-card p-5 rounded-4">
						<div class="hero-brand text-center mb-4">
							<a class="navbar-brand d-flex align-items-center justify-content-center py-5"
								href="<c:url value='/teacher/account/login'/>">
								<svg class="me-3" width="96" height="96" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
									<path d="M4 4.5C4 3.67 4.67 3 5.5 3h13c.83 0 1.5.67 1.5 1.5v15c0 .83-.67 1.5-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5v-15zM5.5 4a.5.5 0 0 0-.5.5V8h15V4.5a.5.5 0 0 0-.5-.5h-14zM5 9v10.5c0 .28.22.5.5.5h13a.5.5 0 0 0 .5-.5V9H5z"/>
									<path d="M8.75 13.5L7.25 12l1.5-1.5m6.5 3L16.75 12l-1.5-1.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
									<path d="M11 13.5h2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
								</svg>
								<span class="brand-text text-start">
									<span class="brand-line d-block">Programming</span>
									<span class="brand-line d-block">Process</span>
									<span class="brand-line d-block">Evaluator</span>
								</span>
							</a>
						</div>

						<div class="role-info alert alert-info mb-4">
							<p class="mb-0">このページは教師向けです。生徒の場合は<a class="text-decoration-none"
								href="<c:url value='/student/account/login'/>">こちら</a>からログインしてください。</p>
						</div>

						<div class="sample-card login-panel p-4">
							<h1 class="card-title h3">教師ログイン</h1>
							<div id="auth-feedback" aria-live="assertive"></div>
							<c:if test="${not empty loginError}">
								<span class="d-none" data-auth-error="<c:out value='${loginError}'/>"></span>
							</c:if>
							<form id="teacher-login" class="login-form" method="post"
								action="<c:url value='/teacher/account/login'/>">
								<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
								<div>
									<label for="teacherId" class="form-label">ID</label>
									<input type="text" class="form-control" id="teacherId" name="loginId"
										placeholder="name" autocomplete="username" maxlength="64" required>
								</div>
								<div>
									<label for="teacherPassword" class="form-label">パスワード</label>
									<div class="password-field">
										<input type="password" class="form-control" id="teacherPassword" name="password"
											placeholder="••••••••" autocomplete="current-password" maxlength="256" required>
										<button type="button" class="password-toggle" data-password-toggle="teacherPassword"
											aria-label="パスワードを表示">
											<svg class="password-toggle-icon password-toggle-show" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
												<path d="M16 8s-3-5.5-8-5.5S0 8 0 8s3.5 5.5 8 5.5S16 8 16 8zM1.173 8a13.133 13.133 0 0 1 1.66-2.043C4.12 4.668 5.88 3.5 8 3.5c2.12 0 3.879 1.168 5.168 2.457A13.133 13.133 0 0 1 14.828 8c-.058.087-.122.183-.195.288-.335.48-.83 1.12-1.465 1.755C11.879 11.332 10.12 12.5 8 12.5c-2.12 0-3.879-1.168-5.168-2.457A13.134 13.134 0 0 1 1.172 8z"/>
												<path d="M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM6.5 8a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0z"/>
											</svg>
											<svg class="password-toggle-icon password-toggle-hide" width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
												<path d="M13.359 11.238C14.428 10.141 15.141 8.99 15.562 8c-.74-1.739-2.878-4.5-7.562-4.5-1.153 0-2.188.167-3.118.468l.787.787A8.99 8.99 0 0 1 8 4.5c3.866 0 5.79 2.14 6.59 3.5-.278.473-.709 1.12-1.317 1.778l.086.086z"/>
												<path d="M11.297 9.176a3.5 3.5 0 0 0-4.473-4.473l.823.823a2.5 2.5 0 0 1 2.827 2.827l.823.823z"/>
												<path d="M4.11 5.696A13.346 13.346 0 0 0 1.613 8c.795 1.36 2.719 3.5 6.387 3.5 1.294 0 2.397-.266 3.334-.697l.77.77C11.013 12.181 9.64 12.5 8 12.5 3.316 12.5 1.178 9.739.438 8c.45-1.053 1.282-2.336 2.466-3.462l1.206 1.158z"/>
												<path d="M3.354 1.646a.5.5 0 1 0-.708.708l10 10a.5.5 0 0 0 .708-.708l-10-10z"/>
											</svg>
										</button>
									</div>
								</div>
								<button type="submit" class="btn btn-primary w-100 btn-lg">ログイン</button>
							</form>
							<div class="mt-4 text-center">
								<a class="btn btn-link text-decoration-none" href="<c:url value='/student/account/login'/>">生徒用ログインはこちら</a>
							</div>
						</div>
					</div>
				</section>
			</div>
		</main>
		<footer class="bg-light py-4 mt-5 border-top">
			<div class="container text-center">
				<p class="text-muted mb-0">&copy; 2026 Takumi Toida. All rights reserved.</p>
			</div>
		</footer>
	</c:otherwise>
</c:choose>
<script src="${pageContext.request.contextPath}/js/shared/auth-feedback.js" defer></script>
<c:if test="${studentPortal}">
	<script src="${pageContext.request.contextPath}/js/shared/password-visibility.js" defer></script>
</c:if>
<c:if test="${not studentPortal}">
	<script src="${pageContext.request.contextPath}/js/shared/password-visibility.js" defer></script>
</c:if>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
