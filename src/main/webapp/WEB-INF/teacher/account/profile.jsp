<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="teacher"/>
<c:set var="screenBodyClass" value="teacher-self-account-screen"/>
<c:set var="screenPageTitle" value="教師アカウント情報"/>
<c:set var="screenStylesheet" value="/css/teacher/account/profile.css"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="page-content py-5">
	<div class="container-fluid px-4">
		<section class="sample-section profile-hero mb-4">
			<span class="profile-kicker">My Account</span>
			<h1 class="section-title mb-3">アカウント情報</h1>
			<p class="text-muted mb-0">ご自身の登録情報と利用可能な機能を確認し、パスワードを変更できます。</p>
		</section>
		<div class="row g-4">
			<div class="col-12 col-xl-7">
				<section class="sample-card h-100">
					<h2 class="card-title mb-4">登録情報</h2>
					<dl class="profile-details mb-0">
						<div><dt>教師ID</dt><dd><c:out value="${teacherAccount.loginId}"/></dd></div>
						<div><dt>アカウント状態</dt><dd><span class="badge text-bg-success"><c:out value="${teacherAccount.statusLabel}"/></span></dd></div>
						<div class="profile-detail-wide"><dt>閲覧可能な学校</dt><dd>
							<c:choose><c:when test="${not empty teacherNavigationSummary.schools}">
								<ul class="mb-0"><c:forEach var="school" items="${teacherNavigationSummary.schools}"><li><c:out value="${school.name}"/></li></c:forEach></ul>
							</c:when><c:otherwise>割り当てなし</c:otherwise></c:choose>
						</dd></div>
					</dl>
					<p class="text-muted small mt-3 mb-0">教師ID・学校・状態の変更が必要な場合は、管理者へご相談ください。</p>
				</section>
			</div>
			<div class="col-12 col-xl-5">
				<section class="sample-card h-100">
					<h2 class="card-title mb-3">パスワード変更</h2>
					<p class="text-muted">パスワードは任意に変更できます。現在のパスワードは表示できません。</p>
					<a class="btn btn-primary mb-4" href="<c:url value='/teacher/account/password'/>">パスワード変更へ</a>
					<div class="profile-policy"><h3 class="h6 fw-bold">パスワード要件</h3><p class="mb-0">半角8〜32文字。英大文字・英小文字・数字・記号のうち3種類以上を含めてください。</p></div>
					<p class="text-muted small mt-3 mb-0">現在のパスワードを忘れた場合は、管理者へ再設定を依頼してください。</p>
				</section>
			</div>
			<div class="col-12">
				<section class="sample-card">
					<h2 class="card-title mb-3">利用可能な業務機能</h2>
					<c:choose><c:when test="${not empty teacherFeatureLabels}">
						<ul class="profile-features mb-3"><c:forEach var="label" items="${teacherFeatureLabels}"><li><c:out value="${label}"/></li></c:forEach></ul>
					</c:when><c:otherwise><p class="text-muted">許可されている業務機能はありません。</p></c:otherwise></c:choose>
					<p class="text-muted small mb-0">権限の変更は管理者が行います。学校・業務機能の権限がない場合も、ご自身のアカウント確認とパスワード変更は利用できます。未実装の業務機能は準備中です。</p>
				</section>
			</div>
		</div>
	</div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
