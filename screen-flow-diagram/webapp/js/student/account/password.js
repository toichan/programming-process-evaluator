window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	document.getElementById('header-placeholder').innerHTML = header;
	document.getElementById('footer-placeholder').innerHTML = footer;
	const required = new URLSearchParams(window.location.search).get('required') === '1';
	document.querySelector('.status-meta').textContent = required
		? '変更が完了すると、ホーム画面へ進みます。'
		: '変更が完了すると、アカウント情報画面へ戻ります。';
	if (required) {
		const link = document.querySelector('.form-actions a');
		const disabled = document.createElement('button');
		disabled.type = 'button';
		disabled.className = link.className;
		disabled.textContent = link.textContent;
		disabled.disabled = true;
		disabled.setAttribute('aria-describedby', 'passwordReturnNotice');
		link.replaceWith(disabled);
		const notice = document.createElement('p');
		notice.id = 'passwordReturnNotice';
		notice.className = 'password-return-notice';
		notice.textContent = 'パスワード変更が完了するまで戻れません。';
		document.querySelector('.password-return-action').appendChild(notice);
		document.querySelector('.status-value').textContent = '変更が必要です';
	}
	const pageFeedback = window.PPEFeedback.createPageFeedback({
		title: 'パスワード変更',
		alertTarget: document.getElementById('passwordInlineFeedback')
	});
	const button = document.getElementById('savePasswordButton');
	document.getElementById('passwordChangeForm').addEventListener('submit', event => {
		event.preventDefault();
		if (!button.disabled) button.click();
	});
	button.addEventListener('click', async () => {
		if (button.disabled) return;
		const confirmed = await pageFeedback.confirm({
			title: 'パスワードを変更しますか？',
			message: '次の内容でパスワードを更新します。',
			detailTitle: '',
			details: ['新しいパスワードを登録', '次回以降は新しいパスワードでログイン'],
			confirmLabel: '変更する',
			cancelLabel: '戻る',
			variant: 'success'
		});
		if (confirmed) {
			window.sessionStorage.setItem('ppe-home-message-title', 'パスワード変更');
			window.sessionStorage.setItem('ppe-home-message', 'パスワードを変更しました');
			window.sessionStorage.setItem('ppe-home-message-time', new Date().toLocaleString('ja-JP'));
			window.location.href = required ? '../home/home.html' : './account.html';
		}
	});
});
