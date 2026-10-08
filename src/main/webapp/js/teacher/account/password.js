(() => {
	const form = document.getElementById('passwordChangeForm');
	const submit = document.getElementById('savePasswordButton');
	const confirmed = form.elements.namedItem('changeConfirmed');
	confirmed.name = 'changeConfirmed';
	const feedback = window.PPEFeedback.createPageFeedback({ title: 'パスワード変更', alertTarget: '#auth-feedback' });
	let pending = false;
	let submitted = false;
	form.addEventListener('submit', async event => {
		event.preventDefault();
		if (pending || submit.disabled || !form.reportValidity()) return;
		pending = true;
		try {
			const values = new FormData(form);
			const accepted = await feedback.confirm({
				title: 'パスワードを変更しますか？',
				message: '変更すると、現在のログインを含むすべての端末で再ログインが必要になります。',
				confirmLabel: '変更する', cancelLabel: 'キャンセル'
			});
			if (!accepted) return;
			if (['currentPassword', 'newPassword', 'confirmPassword'].some(name => form.elements.namedItem(name).value !== values.get(name))) {
				feedback.inlineAlert('入力が変更されています。内容を確認してもう一度実行してください。', 'warning');
				return;
			}
			confirmed.value = 'yes';
			submit.disabled = true;
			submitted = true;
			HTMLFormElement.prototype.submit.call(form);
		} finally {
			if (!submitted) pending = false;
		}
	});
	window.addEventListener('pagehide', () => {
		form.reset();
		confirmed.value = 'no';
		form.querySelectorAll('input[autocomplete$="password"]').forEach(input => {
			const toggle = document.querySelector(`[data-password-toggle="${input.id}"]`);
			if (input.type === 'text') toggle.click();
		});
	});
	window.addEventListener('pageshow', () => { pending = false; submitted = false; });
})();
