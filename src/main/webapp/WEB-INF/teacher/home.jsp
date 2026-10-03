<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="${isAdmin ? '管理者ホーム' : 'ログイン確認'}"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
	<div>
		<h1 class="h3 mb-1">${isAdmin ? '管理者ホーム' : 'ログイン確認'}</h1>
		<c:choose>
			<c:when test="${isAdmin}"><p class="text-secondary mb-0">利用する管理機能を選択してください。</p></c:when>
			<c:otherwise><p class="text-secondary mb-0">認証・セッション確認用の仮ページです。教師向け画面は後続工程で実装します。</p></c:otherwise>
		</c:choose>
	</div>
	<form method="post" action="<c:url value='/auth/logout'/>">
		<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
		<button class="btn btn-outline-secondary" type="submit">ログアウト</button>
	</form>
</div>
<section class="card">
	<div class="card-body">
		<c:choose>
			<c:when test="${isAdmin}"><p class="mb-0">ようこそ、<c:out value="${displayName}"/> さん。</p></c:when>
			<c:otherwise><p class="mb-0">教師ID：<c:out value="${teacherId}"/></p></c:otherwise>
		</c:choose>
		<c:if test="${isAdmin}">
			<nav class="mt-4" aria-label="管理機能">
				<h2 class="h5">管理メニュー</h2>
				<a class="list-group-item list-group-item-action border rounded p-3 d-flex align-items-center justify-content-between gap-3"
					href="<c:url value='/admin/schools'/>">
					<span><span class="fw-bold d-block">学校管理</span>
						<span class="text-secondary small">学校情報・セキュリティレベルの登録と確認</span></span>
					<span aria-hidden="true">&rarr;</span>
				</a>
			</nav>
		</c:if>
	</div>
</section>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
