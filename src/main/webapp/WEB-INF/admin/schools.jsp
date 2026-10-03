<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="学校管理"/>
<c:set var="screenStylesheet" value="/css/admin/schools.css"/>
<c:set var="screenScript" value="/js/admin/schools.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<nav aria-label="パンくず">
	<ol class="breadcrumb">
		<li class="breadcrumb-item"><a href="<c:url value='/teacher/home'/>">管理者ホーム</a></li>
		<c:choose>
			<c:when test="${empty selectedSchool}"><li class="breadcrumb-item active" aria-current="page">学校管理</li></c:when>
			<c:otherwise>
				<li class="breadcrumb-item"><a href="<c:url value='/admin/schools'/>">学校管理</a></li>
				<li class="breadcrumb-item active" aria-current="page">学校詳細・編集</li>
			</c:otherwise>
		</c:choose>
	</ol>
</nav>
<a class="d-inline-block mb-3" href="<c:url value='/teacher/home'/>"><span aria-hidden="true">&larr; </span>管理者ホームに戻る</a>
<div class="admin-hero mb-4">
	<div>
		<span class="hero-kicker">Admin Console</span>
		<h1 class="section-title mb-2">学校管理</h1>
		<p class="hero-description mb-0">学校情報と、生徒に適用するセキュリティレベルを管理します。</p>
	</div>
	<div class="d-flex flex-wrap align-items-center gap-2">
		<span class="admin-id">管理者: <c:out value="${displayName}"/></span>
		<form method="post" action="<c:url value='/auth/logout'/>">
			<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
			<button class="btn btn-outline-secondary" type="submit">ログアウト</button>
		</form>
	</div>
</div>
<div id="schoolFeedback" aria-live="polite">
	<c:if test="${not empty schoolError}"><div class="alert alert-warning" role="alert"><c:out value="${schoolError}"/></div></c:if>
	<c:if test="${param.notice == 'saved' and empty schoolError}"><div class="alert alert-success" role="status">学校情報を保存しました。</div></c:if>
</div>
<section class="panel-card mb-4">
	<div class="card-body">
		<h2 class="h5"><c:choose><c:when test="${empty selectedSchool}">学校登録</c:when><c:otherwise>学校詳細・編集</c:otherwise></c:choose></h2>
		<c:if test="${not empty selectedSchool}"><p>学校コード: <c:out value="${selectedSchool.code}"/></p></c:if>
		<form id="schoolForm" method="post" action="<c:url value='/admin/schools'/>"
			data-locked="${not empty selectedSchool and selectedSchool.securityLevelLocked}">
			<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
			<input type="hidden" name="schoolId" value="${empty selectedSchool ? 0 : selectedSchool.id}">
			<input type="hidden" name="version" value="${empty selectedSchool ? 0 : selectedSchool.version}">
			<input type="hidden" name="changeConfirmed" value="no">
			<label class="form-label" for="schoolName">学校名</label>
			<input class="form-control mb-3" id="schoolName" name="name" maxlength="200" required
				value="<c:out value='${selectedSchool.name}'/>">
			<div class="form-label">セキュリティレベル</div>
			<c:choose>
				<c:when test="${not empty selectedSchool and selectedSchool.securityLevelLocked}">
					<input type="hidden" name="securityLevel" value="${selectedSchool.securityLevel}">
					<p id="schoolLevel" class="fw-bold">レベル<c:out value="${selectedSchool.securityLevel}"/>（生徒登録済み・変更不可）</p>
				</c:when>
				<c:otherwise>
					<select class="form-select mb-3" id="schoolLevel" name="securityLevel" aria-label="セキュリティレベル" required>
						<option value="">選択してください</option>
						<option value="1" <c:if test="${selectedSchool.securityLevel == 1}">selected</c:if>>レベル1：教師が設定したパスワードを継続利用</option>
						<option value="2" <c:if test="${selectedSchool.securityLevel == 2}">selected</c:if>>レベル2：初回ログイン時に生徒自身が変更</option>
					</select>
				</c:otherwise>
			</c:choose>
			<p class="text-secondary">一度でも生徒を登録するとレベルは変更できません。学校名は変更できます。</p>
			<button class="btn btn-primary" type="submit">${empty selectedSchool ? '登録する' : '変更を保存する'}</button>
			<c:if test="${not empty selectedSchool}"><a class="btn btn-outline-secondary" href="<c:url value='/admin/schools'/>">学校管理に戻る</a></c:if>
			<noscript><p class="text-danger mt-2">確認ダイアログを表示するため、JavaScriptを有効にしてください。</p></noscript>
		</form>
	</div>
</section>
<section class="panel-card">
	<div class="card-body">
		<h2 class="h5">学校一覧</h2>
		<div class="table-responsive">
			<table class="table align-middle">
				<thead><tr><th>学校名</th><th>学校コード</th><th>セキュリティレベル</th><th>変更可否</th><th>操作</th></tr></thead>
				<tbody>
					<c:forEach var="school" items="${schools}">
						<tr>
							<td class="text-break"><c:out value="${school.name}"/></td>
							<td class="text-break"><c:out value="${school.code}"/></td>
							<td><c:choose><c:when test="${empty school.securityLevel}">未設定（生徒登録不可）</c:when><c:otherwise>レベル<c:out value="${school.securityLevel}"/></c:otherwise></c:choose></td>
							<td>${school.securityLevelLocked ? '生徒登録済み・レベル変更不可' : 'レベル変更可'}</td>
							<td><a class="btn btn-outline-primary btn-sm" href="<c:url value='/admin/schools'><c:param name='schoolId' value='${school.id}'/></c:url>">詳細・編集</a></td>
						</tr>
					</c:forEach>
					<c:if test="${empty schools}"><tr><td colspan="5">学校はまだ登録されていません。</td></tr></c:if>
				</tbody>
			</table>
		</div>
	</div>
</section>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
