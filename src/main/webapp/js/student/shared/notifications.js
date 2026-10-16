(() => {
	const feedbackHost = document.querySelector('#notificationFeedback');
	if (feedbackHost) {
		const feedback = window.PPEFeedback.createPageFeedback({
			title: '通知',
			alertTarget: feedbackHost
		});
		if (feedbackHost.dataset.taskTargetUnavailable === 'true') {
			feedback.inlineAlert('この課題は削除済みです。通知を既読にしました。', 'warning');
		}
	}

	const pending = new WeakSet();
	document.addEventListener('submit', async (event) => {
		const form = event.target;
		if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-notification-open')) return;
		event.preventDefault();
		if (pending.has(form)) return;
		pending.add(form);
		const button = form.querySelector('button[type="submit"]');
		if (button) button.disabled = true;
		try {
			if (window.PPEConfirmLeave && !(await window.PPEConfirmLeave())) return;
			window.PPEAllowNavigation?.();
			HTMLFormElement.prototype.submit.call(form);
		} finally {
			pending.delete(form);
			if (button?.isConnected) button.disabled = false;
		}
	});
})();
