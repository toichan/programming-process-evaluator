<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenAssetVersion" value="166"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<%@ include file="/WEB-INF/student/shared/navigation.jspf" %>
<div class="container notification-page">
	<section class="sample-section mb-4">
		<h1 class="section-title">通知</h1>
		<p class="text-secondary mb-0">通知を選ぶと、関連する評価結果や課題へ移動します。通知は作成から90日間表示されます。</p>
	</section>
	<div id="notificationFeedback"
		data-task-target-unavailable="<c:out value='${taskTargetUnavailable}'/>" aria-live="polite"></div>
	<div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
		<p class="mb-0">未読 <strong><c:out value="${notificationPage.unreadCount}"/></strong> 件</p>
		<form method="post" action="<c:url value='/student/notifications/read-all'/>">
			<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
			<button type="submit" class="btn btn-outline-secondary"
				${notificationPage.unreadCount == 0 ? 'disabled' : ''}>すべて既読にする</button>
		</form>
	</div>
	<div class="notification-list">
		<c:forEach var="notification" items="${notificationPage.notifications}">
			<form method="post" action="<c:url value='/student/notifications/open'/>"
				data-notification-open class="notification-list-form">
				<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
				<input type="hidden" name="notificationId" value="<c:out value='${notification.notificationId}'/>">
				<button type="submit" class="notification-item ${notification.read ? 'is-read' : 'is-unread'}">
					<span class="notification-item-heading">
						<strong><c:out value="${notification.title}"/></strong>
						<span class="notification-read-label">${notification.read ? '既読' : '未読'}</span>
					</span>
					<span class="notification-message"><c:out value="${notification.message}"/></span>
					<span class="notification-time"><c:out value="${notification.createdAtLabel}"/></span>
					<span class="notification-destination">関連する画面を開く →</span>
				</button>
			</form>
		</c:forEach>
		<c:if test="${empty notificationPage.notifications}">
			<p class="sample-card text-secondary mb-0">通知はありません。</p>
		</c:if>
	</div>
	<nav class="d-flex flex-wrap justify-content-center align-items-center gap-3 mt-4" aria-label="通知一覧のページ">
		<c:if test="${notificationPage.pageNumber > 1}">
			<a class="btn btn-outline-secondary" href="<c:url value='/student/notifications'><c:param name='page' value='${notificationPage.pageNumber - 1}'/></c:url>">前へ</a>
		</c:if>
		<span><c:out value="${notificationPage.pageNumber}"/> ページ</span>
		<c:if test="${notificationPage.hasNext}">
			<a class="btn btn-outline-secondary" href="<c:url value='/student/notifications'><c:param name='page' value='${notificationPage.pageNumber + 1}'/></c:url>">次へ</a>
		</c:if>
	</nav>
	<%@ include file="/WEB-INF/student/shared/home-action.jspf" %>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
