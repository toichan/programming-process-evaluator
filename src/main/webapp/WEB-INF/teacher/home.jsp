<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="教師メニュー"/>
<c:set var="screenDesign" value="teacher"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div class="container-fluid py-4 px-4">
	<h1 class="h3">教師メニュー</h1>
	<c:choose>
		<c:when test="${teacherNavigationSummary.taskManagementEnabled or teacherNavigationSummary.promptDesignEnabled}">
			<p>左のメニューから利用する機能を選択してください。</p>
		</c:when>
		<c:otherwise><div class="alert alert-info" role="status">現在、利用できる実装済み機能の権限がありません。管理者に利用権限を確認してください。</div></c:otherwise>
	</c:choose>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
