<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenDesign" value="admin"/>
<c:set var="screenPageTitle" value="管理者画面"/>
<c:set var="screenStylesheet" value="/css/admin/management.css"/>
<c:set var="screenScript" value="/js/admin/management.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div id="adminConsole" data-endpoint="<c:url value='/admin/management'/>" data-csrf="<c:out value='${csrfToken}'/>">
	<section class="admin-hero mb-4">
		<div><span class="hero-kicker">Admin Console</span><h1 class="section-title mb-2">管理者画面</h1>
			<p class="hero-description mb-0">教師アカウントと学校情報を管理します。</p></div>
		<div class="hero-actions">
			<span class="admin-id">管理者: <c:out value="${adminId}"/></span>
			<form method="post" action="<c:url value='/auth/logout'/>">
				<input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
				<button class="btn btn-outline-secondary btn-sm" type="submit">ログアウト</button>
			</form>
		</div>
	</section>
	<div id="adminFeedback" aria-live="polite"></div>
	<ul class="nav nav-tabs mb-4" role="tablist" aria-label="管理対象">
		<li class="nav-item" role="presentation"><button class="nav-link active" id="teachers-tab" data-bs-toggle="tab" data-bs-target="#teachers-panel" type="button" role="tab" aria-controls="teachers-panel" aria-selected="true">教師アカウント管理</button></li>
		<li class="nav-item" role="presentation"><button class="nav-link" id="schools-tab" data-bs-toggle="tab" data-bs-target="#schools-panel" type="button" role="tab" aria-controls="schools-panel" aria-selected="false">学校管理</button></li>
	</ul>
	<div class="tab-content">
		<div class="tab-pane fade show active" id="teachers-panel" role="tabpanel" aria-labelledby="teachers-tab">
			<section class="panel-card mb-4">
				<div class="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
					<div><span class="hero-kicker">Teacher Accounts</span><h2 class="summary-heading">教師アカウント一覧</h2></div>
					<div class="action-buttons">
						<button class="btn btn-primary btn-sm" type="button" id="createTeacherButton">新規作成</button>
						<button class="btn btn-outline-secondary btn-sm" type="button" data-history="operations">操作履歴を確認</button>
						<a class="btn btn-outline-secondary btn-sm" href="<c:url value='/admin/management'/>">更新</a>
						<a class="btn btn-primary btn-sm" href="<c:url value='/admin/management'><c:param name='view' value='csv'/><c:param name='q' value='${param.q}'/></c:url>">CSVエクスポート</a>
					</div>
				</div>
				<form method="get" class="mb-3 d-flex align-items-end gap-2">
					<div class="flex-grow-1"><label class="form-label" for="teacherSearch">教師IDで検索</label>
						<input class="form-control" id="teacherSearch" type="search" name="q" maxlength="64" placeholder="教師IDを入力" value="<c:out value='${param.q}'/>"></div>
					<button class="btn btn-outline-primary" type="submit">検索</button>
				</form>
				<div class="table-responsive"><table class="table table-hover align-middle" id="teacherAccountTable">
					<thead class="table-light"><tr><th>教師ID</th><th>状態</th><th>閲覧可能な学校</th><th>利用可能な機能</th><th>作成日時</th><th>作成者</th><th>操作</th><th>履歴</th></tr></thead>
					<tbody>
						<c:forEach var="teacher" items="${teachers}">
							<tr data-user-id="${teacher.userId}" data-version="${teacher.version}" data-login-id="<c:out value='${teacher.loginId}'/>">
								<td><c:out value="${teacher.loginId}"/></td><td><span class="badge ${teacher.status == 'active' ? 'text-bg-success' : 'text-bg-secondary'}"><c:out value="${teacher.statusLabel}"/></span></td>
								<td><c:forEach var="school" items="${teacher.schools}"><span class="permission-tag"><c:out value="${school.name}"/></span></c:forEach><c:if test="${empty teacher.schools}">なし</c:if></td>
								<td><c:forEach var="feature" items="${featureOrder}"><c:if test="${teacher.features.contains(feature)}"><span class="permission-tag"><c:out value="${featureLabels[feature]}"/></span></c:if></c:forEach><c:if test="${empty teacher.features}">なし</c:if></td>
								<td><c:out value="${teacher.createdAt}"/></td><td><c:out value="${teacher.createdBy}"/></td>
								<td><div class="action-buttons"><c:if test="${teacher.status != 'deleted'}">
									<button class="btn btn-outline-primary btn-sm" type="button" data-action="edit">編集</button>
									<button class="btn btn-outline-secondary btn-sm" type="button" data-action="reset">パスワード再設定</button>
									<button class="btn btn-outline-secondary btn-sm" type="button" data-action="${teacher.status == 'active' ? 'suspend' : 'activate'}">${teacher.status == 'active' ? '停止' : '停止解除'}</button>
									<button class="btn btn-outline-danger btn-sm" type="button" data-action="delete">削除</button>
								</c:if></div></td>
								<td><div class="action-buttons"><button class="btn btn-outline-secondary btn-sm" type="button" data-history="login">ログイン履歴</button><button class="btn btn-outline-secondary btn-sm" type="button" data-history="operations">操作・削除履歴</button></div></td>
							</tr>
						</c:forEach>
						<c:if test="${empty teachers}"><tr><td colspan="8">対象の教師アカウントはありません。</td></tr></c:if>
					</tbody>
				</table></div>
			</section>
		</div>
		<div class="tab-pane fade" id="schools-panel" role="tabpanel" aria-labelledby="schools-tab">
			<section class="panel-card">
				<div class="d-flex align-items-center justify-content-between mb-3"><div><span class="hero-kicker">Schools</span><h2 class="summary-heading">学校一覧</h2></div>
					<button class="btn btn-primary btn-sm" type="button" id="createSchoolButton">学校登録</button></div>
				<div class="table-responsive"><table class="table align-middle">
					<thead class="table-light"><tr><th>学校名</th><th>学校コード</th><th>セキュリティレベル</th><th>変更可否</th><th>操作</th></tr></thead>
					<tbody><c:forEach var="school" items="${schools}">
						<tr data-school-id="${school.id}" data-version="${school.version}" data-name="<c:out value='${school.name}'/>" data-level="${school.securityLevel}" data-locked="${school.securityLevelLocked}">
							<td><c:out value="${school.name}"/></td><td><c:out value="${school.code}"/></td><td>${empty school.securityLevel ? '未設定' : school.securityLevel}</td><td>${school.securityLevelLocked ? '生徒登録済み・レベル変更不可' : 'レベル変更可'}</td>
							<td><button class="btn btn-outline-primary btn-sm" type="button" data-school-edit>詳細・編集</button></td>
						</tr>
					</c:forEach><c:if test="${empty schools}"><tr><td colspan="5">学校はまだ登録されていません。</td></tr></c:if></tbody>
				</table></div>
			</section>
		</div>
	</div>
</div>
<div class="modal fade" id="teacherEditModal" tabindex="-1" aria-labelledby="teacherEditTitle" aria-hidden="true">
	<div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="teacherEditTitle">教師アカウント作成</h2><button class="btn-close" type="button" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body create-panel-card">
			<span class="hero-kicker" id="teacherEditorKicker">Create Account</span><p class="create-panel-description">対象学校と利用機能を選択して、教師アカウントを設定します。</p>
			<form id="teacherForm">
				<input type="hidden" name="action" value="create"><input type="hidden" name="userId"><input type="hidden" name="version">
				<div class="create-field create-row"><label class="form-label" for="teacherIdInput">教師ID</label><div>
					<input class="form-control form-control-sm" id="teacherIdInput" name="loginId" maxlength="64" placeholder="name" required>
					<p class="small text-secondary mt-1">空白を除く1〜64文字。作成後は変更できません。</p></div></div>
				<div class="create-field create-row"><div class="form-label">閲覧可能な学校</div><div class="chip-group">
					<c:forEach var="school" items="${schools}"><label class="form-check"><input class="form-check-input" type="checkbox" name="schoolIds" value="${school.id}"><span><c:out value="${school.name}"/></span></label></c:forEach>
					<c:if test="${empty schools}"><span class="text-secondary">先に学校管理で学校を登録してください。</span></c:if>
				</div></div>
				<div class="create-field create-row"><div class="form-label">機能利用制限<br>（利用可のみチェック）</div><div class="chip-group">
					<c:forEach var="feature" items="${featureOrder}"><label class="form-check"><input class="form-check-input" type="checkbox" name="features" value="${feature}" checked><span><c:out value="${featureLabels[feature]}"/></span></label></c:forEach>
				</div></div>
				<p class="small text-secondary mt-3">初期パスワードはシステムが8文字で自動生成し、作成後に一度だけ確認できます。権限が有効でも未実装の教師機能は利用できません。変更は教師の既存ログインにも反映します。</p>
				<div class="d-flex justify-content-end gap-2 mt-3"><button class="btn btn-outline-secondary btn-sm" type="reset">リセット</button><button class="btn btn-primary btn-sm" type="submit">保存</button></div>
			</form>
		</div>
	</div></div>
</div>
<div class="modal fade" id="schoolEditModal" tabindex="-1" aria-labelledby="schoolEditTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="schoolEditTitle">学校登録</h2><button class="btn-close" type="button" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body"><form id="schoolForm">
			<input type="hidden" name="action" value="school"><input type="hidden" name="schoolId" value="0"><input type="hidden" name="version" value="0">
			<label class="form-label" for="schoolName">学校名</label><input class="form-control mb-3" id="schoolName" name="name" maxlength="200" required>
			<label class="form-label" for="schoolLevel">セキュリティレベル</label>
			<select class="form-select mb-3" id="schoolLevel" name="securityLevel" required>
				<option value="">選択してください</option><option value="1">レベル1：教師が設定したパスワードを継続利用</option><option value="2">レベル2：初回ログイン時に生徒自身が変更</option>
			</select>
			<p class="text-secondary" id="schoolPolicyNotice">一度でも生徒を登録するとレベルは変更できません。学校名は変更できます。</p>
			<div class="d-flex justify-content-end"><button class="btn btn-primary btn-sm" type="submit">保存</button></div>
		</form></div>
	</div></div>
</div>
<div class="modal fade" id="teacherAuditModal" tabindex="-1" aria-labelledby="teacherAuditTitle" aria-hidden="true">
	<div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="teacherAuditTitle">操作履歴</h2><button class="btn-close" type="button" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body"><p class="small text-secondary">最新200件を表示します。</p><div class="table-responsive"><table class="table">
			<thead><tr><th>日時</th><th>実行者ID</th><th>対象教師ID</th><th>操作</th><th>結果</th><th>詳細</th></tr></thead><tbody id="historyRows"></tbody>
		</table></div></div>
	</div></div>
</div>
<div class="modal fade" id="credentialModal" tabindex="-1" aria-labelledby="credentialTitle" aria-hidden="true" data-bs-backdrop="static" data-bs-keyboard="false">
	<div class="modal-dialog"><div class="modal-content">
		<div class="modal-header"><h2 class="modal-title h5" id="credentialTitle">教師のログイン情報</h2><button class="btn-close" type="button" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body"><p class="text-warning-emphasis">この画面を閉じるとパスワードは再表示できません。控えを失った場合は再設定してください。</p>
			<label class="form-label" for="credentialId">教師ID</label><input class="form-control mb-3" id="credentialId" readonly>
			<label class="form-label" for="credentialPassword">パスワード</label><input class="form-control mb-3" id="credentialPassword" readonly autocomplete="off">
			<button class="btn btn-outline-primary" type="button" id="copyCredentials">IDとパスワードをコピー</button></div>
		<div class="modal-footer"><button class="btn btn-primary" type="button" data-bs-dismiss="modal">閉じる</button></div>
	</div></div>
</div>
<noscript><p class="text-danger">管理操作・確認ダイアログにはJavaScriptを有効にしてください。</p></noscript>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
