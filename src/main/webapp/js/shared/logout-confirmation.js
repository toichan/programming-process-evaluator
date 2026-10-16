(() => {
	const feedback = window.PPEFeedback.createPageFeedback({ title: 'ログアウト' });
	const pending = new WeakSet();

	document.addEventListener('submit', async event => {
		const form = event.target;
		if (!(form instanceof HTMLFormElement)
				|| !new URL(form.action, document.baseURI).pathname.endsWith('/auth/logout')) return;

		event.preventDefault();
		if (pending.has(form)) return;
		pending.add(form);
		const submitButton = form.querySelector('button[type="submit"], input[type="submit"]');
		const wasDisabled = submitButton?.disabled ?? false;

		try {
			if (window.PPEConfirmLeave && !(await window.PPEConfirmLeave())) return;
			const confirmed = await feedback.confirm({
				title: 'ログアウトしますか？',
				message: 'ログアウトしてログイン画面へ戻ります。',
				confirmLabel: 'ログアウト',
				cancelLabel: 'キャンセル'
			});
			if (confirmed) {
				if (submitButton) submitButton.disabled = true;
				window.PPEAllowNavigation?.();
				HTMLFormElement.prototype.submit.call(form);
			}
		} finally {
			pending.delete(form);
			if (submitButton?.isConnected) submitButton.disabled = wasDisabled;
		}
	});
})();
