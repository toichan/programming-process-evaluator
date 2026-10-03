<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
	<div>
		<h1 class="h3 mb-1">ログイン確認</h1>
		<p class="text-secondary mb-0">認証・セッション確認用の仮ページです。教師向け画面は後続工程で実装します。</p>
	</div>
	<form method="post" action="<c:url value='/auth/logout'/>">
		<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
		<button class="btn btn-outline-secondary" type="submit">ログアウト</button>
	</form>
</div>
<section class="card">
	<div class="card-body">
		<p class="mb-0">ようこそ、<c:out value="${displayName}"/> さん。</p>
	</div>
</section>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
