<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="student"/>
<c:set var="screenPageTitle" value="アカウント情報"/>
<c:set var="screenStylesheet" value="/css/student/account/account.css"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div class="container">
	<section class="sample-section account-hero mb-4">
		<div class="account-hero-copy">
			<span class="account-kicker">Account Information</span>
			<h1 class="section-title mb-3">アカウント情報</h1>
			<p class="account-hero-description mb-0">
				登録されている生徒ID・所属情報と、パスワード変更の可否を確認できます。
			</p>
		</div>
		<div class="account-status-card">
			<div class="status-label">アカウント状況</div>
			<div class="status-value">${account.securityLevel == 2 ? 'パスワード変更可' : 'パスワード変更不可'}</div>
			<div class="status-meta">
				<c:choose>
					<c:when test="${account.securityLevel == 1}">パスワード変更が許可されていないアカウントです。</c:when>
					<c:when test="${account.firstLoginStatus == 'COMPLETED' and not account.mustChangePassword}">初回パスワード変更は完了済みです。</c:when>
					<c:otherwise>初回パスワード変更が必要です。</c:otherwise>
				</c:choose>
			</div>
		</div>
	</section>

	<div class="row g-4 mb-4 account-card-row">
		<div class="col-12 col-xl-7">
			<article class="sample-card info-card h-100">
				<div class="info-card-header">
					<div><h2 class="card-title mb-0">登録情報</h2></div>
				</div>

				<dl class="account-detail-grid mb-0">
					<div class="detail-item">
						<dt>生徒ID</dt>
						<dd><c:out value="${account.studentId}"/></dd>
					</div>
					<div class="detail-item">
						<dt>学校</dt>
						<dd>
							<c:choose>
								<c:when test="${empty account.affiliations}">所属情報は登録されていません。</c:when>
								<c:otherwise>
									<ul class="list-unstyled mb-0">
										<c:forEach var="affiliation" items="${account.affiliations}">
											<li><c:out value="${affiliation.schoolName}"/></li>
										</c:forEach>
									</ul>
								</c:otherwise>
							</c:choose>
						</dd>
					</div>
					<div class="detail-item">
						<dt>クラス</dt>
						<dd>
							<c:choose>
								<c:when test="${empty account.affiliations}">所属情報は登録されていません。</c:when>
								<c:otherwise>
									<ul class="list-unstyled mb-0">
										<c:forEach var="affiliation" items="${account.affiliations}">
											<li>
												<c:if test="${not empty affiliation.gradeName}"><c:out value="${affiliation.gradeName}"/></c:if>
												<c:out value="${affiliation.classroomName}"/>
											</li>
										</c:forEach>
									</ul>
								</c:otherwise>
							</c:choose>
						</dd>
					</div>
					<div class="detail-item">
						<dt>初回パスワード変更</dt>
						<dd>
							<c:choose>
								<c:when test="${account.securityLevel == 1}">不要</c:when>
								<c:when test="${account.firstLoginStatus == 'COMPLETED' and not account.mustChangePassword}">完了</c:when>
								<c:otherwise>必要</c:otherwise>
							</c:choose>
						</dd>
					</div>
				</dl>
			</article>
		</div>

		<div class="col-12 col-xl-5">
			<article class="sample-card policy-card h-100">
				<h2 class="card-title mb-3">パスワード変更</h2>
				<div class="button-group account-password-action mb-4">
					<c:choose>
						<c:when test="${account.securityLevel == 2}">
							<a class="btn btn-primary account-password-button" href="<c:url value='/student/account/change-password'/>">パスワード変更へ</a>
						</c:when>
						<c:otherwise>
							<button class="btn btn-secondary account-password-button" type="button" disabled
								aria-describedby="passwordChangeUnavailable">パスワード変更へ</button>
						</c:otherwise>
					</c:choose>
				</div>
				<div class="password-rule-card">
					<div class="password-rule-title">パスワード要件</div>
					<div class="password-rule-text">英大文字・英小文字・数字・記号のうち3種類以上を含む、8〜32文字</div>
				</div>
				<c:if test="${account.securityLevel == 1}">
					<p id="passwordChangeUnavailable" class="small text-secondary mt-3 mb-0">あなたのアカウントでは、パスワード変更が許可されていません。必要な場合は担当者へご相談ください。</p>
				</c:if>
			</article>
		</div>
	</div>

	<article class="sample-card history-card h-100">
		<h2 class="card-title mb-3">パスワード変更履歴</h2>
		<div class="account-history-table-wrap">
			<table class="table table-sm align-middle mb-0 account-history-table">
				<thead>
					<tr>
						<th scope="col" style="width: 34%;">日時</th>
						<th scope="col" style="width: 28%;">操作</th>
						<th scope="col" style="width: 38%;">実行者</th>
					</tr>
				</thead>
				<tbody>
					<c:choose>
						<c:when test="${empty account.credentialHistory}">
							<tr><td colspan="3" class="text-center text-muted py-4">パスワード変更履歴はありません。</td></tr>
						</c:when>
						<c:otherwise>
							<c:forEach var="entry" items="${account.credentialHistory}">
								<c:choose>
									<c:when test="${entry.resultStatus == 'success'}"><c:set var="historyBadgeClass" value="is-success"/></c:when>
									<c:otherwise><c:set var="historyBadgeClass" value="is-failure"/></c:otherwise>
								</c:choose>
								<tr>
									<td><c:out value="${entry.occurredAtDisplay}"/></td>
									<td>
										<span class="badge account-history-badge ${historyBadgeClass}">
											<c:out value="${entry.actionLabel}"/>
											<c:if test="${entry.resultStatus == 'failure'}">（失敗）</c:if>
										</span>
									</td>
									<td><c:out value="${entry.actorLabel}"/></td>
								</tr>
							</c:forEach>
						</c:otherwise>
					</c:choose>
				</tbody>
			</table>
		</div>
	</article>

	<div class="account-footer-action text-center mt-4">
		<a class="btn btn-outline-secondary" href="<c:url value='/student/home'/>">ホームへ戻る</a>
	</div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
