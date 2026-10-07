<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="screenPageTitle" value="生徒アカウント管理"/>
<c:set var="screenDesign" value="teacher"/>
<c:set var="screenStylesheet" value="/css/teacher/account/account.css"/>
<c:set var="screenScript" value="/js/teacher/account/account.js"/>
<%@ include file="/WEB-INF/template/page-start.jspf" %>
<div id="studentAccountConsole" class="page-content py-5" data-endpoint="<c:url value='/teacher/students'/>" data-csrf="<c:out value='${csrfToken}'/>">
	<div class="container-fluid px-4">
		<section class="history-hero mb-4">
			<div><span class="hero-kicker">Account Management</span><h1 class="section-title">生徒アカウント管理</h1>
				<p class="text-secondary">生徒アカウントの一元管理、作成、確認、エクスポートができます。</p></div>
			<div class="hero-stats">
				<div class="stat-card"><div>対象学校</div><strong id="schoolCount">—</strong></div>
				<div class="stat-card"><div>対象クラス</div><strong id="classCount">—</strong></div>
				<div class="stat-card"><div>登録アカウント数</div><strong id="studentCount">—</strong></div>
			</div>
		</section>
		<div id="accountFeedback" aria-live="polite"></div>
		<section class="panel-card mb-4">
			<div class="d-flex justify-content-between align-items-center gap-3 flex-wrap mb-3">
				<div><span class="hero-kicker">Account List</span><h2 class="summary-heading">アカウント一覧</h2></div>
				<div class="action-buttons">
					<button id="bulkDeleteButton" class="btn btn-outline-danger btn-sm" type="button" disabled>選択削除</button>
					<button id="createAccountButton" class="btn btn-primary" type="button" disabled>＋ 新規作成</button>
					<a id="exportAccounts" class="btn btn-primary btn-sm disabled" aria-disabled="true" href="<c:url value='/teacher/students?view=csv'/>">CSVエクスポート</a>
				</div>
			</div>
			<form id="accountFilters" class="filter-grid" role="search">
				<div><label class="form-label" for="searchInput">検索</label><input id="searchInput" name="q" type="search" maxlength="64" class="form-control form-control-sm" placeholder="生徒IDで検索"></div>
				<div><label class="form-label" for="filterSecurityLevel">セキュリティレベル</label><select id="filterSecurityLevel" name="security" class="form-select form-select-sm"><option value="">すべて</option><option value="1">レベル1</option><option value="2">レベル2</option></select></div>
				<div><label class="form-label" for="filterSchool">学校</label><select id="filterSchool" name="schoolId" class="form-select form-select-sm"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="filterClass">クラス</label><select id="filterClass" name="classroomId" class="form-select form-select-sm"><option value="">すべて</option></select></div>
				<div><label class="form-label" for="filterFirstLogin">初回パスワード変更</label><select id="filterFirstLogin" name="firstLogin" class="form-select form-select-sm"><option value="">すべて</option><option value="completed">完了</option><option value="pending">未完了</option><option value="not_required">対象外</option></select></div>
				<div><label class="form-label" for="filterConsent">研究同意</label><select id="filterConsent" name="consent" class="form-select form-select-sm"><option value="">すべて</option><option value="agreed">同意</option><option value="declined">不同意</option><option value="withdrawn">撤回</option><option value="unconfirmed">未確認</option></select></div>
				<div><label class="form-label" for="filterStatus">状態</label><select id="filterStatus" name="status" class="form-select form-select-sm"><option value="">すべて</option><option value="active">利用中</option><option value="suspended">停止中</option><option value="deleted">削除済み</option></select></div>
			</form>
		</section>
		<section class="panel-card">
			<p id="listStatus" class="small text-secondary" role="status">読み込み中…</p>
			<div class="table-responsive"><table id="accountTable" class="table table-hover align-middle">
				<thead class="table-light"><tr>
					<th><input id="selectAllAccounts" type="checkbox" class="form-check-input" aria-label="表示中の生徒を全選択"></th>
					<th><button type="button" class="sort-button" data-sort="loginId">生徒ID</button></th><th>パスワード</th>
					<th><button type="button" class="sort-button" data-sort="securityLevel">セキュリティ</button></th>
					<th><button type="button" class="sort-button" data-sort="schoolName">学校</button></th><th>クラス</th>
					<th>初回変更</th><th>研究同意</th><th>状態</th>
					<th><button type="button" class="sort-button" data-sort="createdAt">作成日時</button></th><th>操作</th><th>詳細</th>
				</tr></thead><tbody id="accountRows"></tbody>
			</table></div>
		</section>
	</div>
</div>
<div class="modal fade" id="createAccountModal" tabindex="-1" aria-labelledby="createAccountTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-centered"><div class="modal-content">
		<form id="createAccountForm">
			<div class="modal-header"><h2 class="modal-title fs-5" id="createAccountTitle">生徒アカウント作成</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
			<div class="modal-body">
				<div class="mb-3"><label for="schoolSelect" class="form-label">学校</label><select id="schoolSelect" name="schoolId" class="form-select" required><option value="">学校を選択</option></select></div>
				<div class="mb-3"><label for="classSelect" class="form-label">クラス</label><select id="classSelect" name="classroomId" class="form-select" required><option value="">クラスを選択</option></select></div>
				<div id="newClassFields" class="mb-3" hidden><label for="newClassName" class="form-label">新しいクラス名</label><input id="newClassName" name="classroomName" class="form-control" maxlength="100" placeholder="例：1年A組"></div>
				<div class="mb-3"><label for="accountCount" class="form-label">作成件数</label><input id="accountCount" name="count" type="number" class="form-control" min="1" max="200" value="1" required></div>
				<div class="mb-3"><label for="schoolSecurityLevel" class="form-label">セキュリティレベル</label><input id="schoolSecurityLevel" class="form-control" value="学校を選択してください" readonly><div class="form-text">学校の設定を適用します。生徒個別には変更できません。</div></div>
				<div class="form-text">初期パスワードは生徒ごとにシステムが8文字で自動生成します。作成後にIDとあわせて確認・コピーしてください。</div>
			</div>
			<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button type="submit" class="btn btn-primary">作成する</button></div>
		</form>
	</div></div>
</div>
<div class="modal fade" id="resetPasswordModal" tabindex="-1" aria-labelledby="resetPasswordTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-centered"><div class="modal-content"><form id="resetPasswordForm">
		<div class="modal-header"><h2 class="modal-title fs-5" id="resetPasswordTitle">パスワード再設定</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body"><p id="resetStudentId"></p><label class="form-label" for="resetPassword">新しい一時パスワード</label>
			<div class="input-group"><input id="resetPassword" name="password" type="password" class="form-control" minlength="8" maxlength="32" autocomplete="new-password" required><button type="button" class="btn btn-outline-secondary" data-toggle-password="resetPassword">表示</button></div>
			<div class="form-text">8〜32文字、半角4種類中3種類以上。次回ログイン時に生徒本人の変更が必要です。</div></div>
		<div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">キャンセル</button><button type="submit" class="btn btn-primary">再設定する</button></div>
	</form></div></div>
</div>
<div class="modal fade" id="studentCredentialModal" tabindex="-1" aria-labelledby="credentialTitle" aria-hidden="true">
	<div class="modal-dialog modal-dialog-centered"><div class="modal-content">
		<div class="modal-header"><h2 id="credentialTitle" class="modal-title fs-5">資格情報</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div class="modal-body"><p class="small text-secondary">作成時は生徒ごとに8文字の初期パスワードを自動生成します。配布用の情報です。閉じると画面から消去します。本人変更後のパスワードは確認できません。</p><label for="credentialText" class="form-label">生徒ID・パスワード</label><textarea id="credentialText" class="form-control font-monospace" rows="4" readonly></textarea><button id="copyCredentialButton" type="button" class="btn btn-outline-secondary mt-2">コピー</button><p id="copyCredentialStatus" class="small mt-2" role="status"></p></div>
	</div></div>
</div>
<div class="modal fade" id="accountDetailModal" tabindex="-1" aria-labelledby="accountDetailTitle" aria-hidden="true">
	<div class="modal-dialog modal-lg modal-dialog-scrollable"><div class="modal-content">
		<div class="modal-header"><h2 id="accountDetailTitle" class="modal-title fs-5">アカウント詳細</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button></div>
		<div id="accountDetailBody" class="modal-body"></div>
	</div></div>
</div>
<%@ include file="/WEB-INF/template/page-end.jspf" %>
