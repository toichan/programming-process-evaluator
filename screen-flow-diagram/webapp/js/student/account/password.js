window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	const feedback = window.PPEFeedback || {};
	const headerPlaceholder = document.querySelector('#header-placeholder');
	const footerPlaceholder = document.querySelector('#footer-placeholder');
	const formSection = document.querySelector('.password-form-section');
	const currentPassword = document.querySelector('#currentPassword');
	const newPassword = document.querySelector('#newPassword');
	const confirmPassword = document.querySelector('#confirmPassword');
	const savePasswordButton = document.querySelector('#savePasswordButton');
	const passwordToggleButtons = document.querySelectorAll('[data-password-toggle]');
	const pageFeedback = feedback.createPageFeedback({
		title: 'パスワード変更',
		alertTarget: formSection
	});

	if (headerPlaceholder && header) {
		headerPlaceholder.innerHTML = header;
	}

	if (footerPlaceholder && footer) {
		footerPlaceholder.innerHTML = footer;
	}

	passwordToggleButtons.forEach((button) => {
		button.addEventListener('click', () => {
			const targetId = button.getAttribute('data-password-toggle');
			if (!targetId) {
				return;
			}

			const targetInput = document.getElementById(targetId);
			if (!targetInput) {
				return;
			}

			const isHidden = targetInput.type === 'password';
			targetInput.type = isHidden ? 'text' : 'password';
			button.classList.toggle('is-visible', isHidden);
			button.setAttribute('aria-label', isHidden ? 'パスワードを隠す' : 'パスワードを表示');
		});
	});

	function validatePassword(value) {
		const checks = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/];
		const count = checks.reduce((sum, pattern) => sum + (pattern.test(value) ? 1 : 0), 0);
		return value.length >= 8 && value.length <= 32 && count >= 3;
	}

	function syncButtonState() {
		if (!savePasswordButton) {
			return;
		}

		const hasAllValues = currentPassword?.value && newPassword?.value && confirmPassword?.value;
		savePasswordButton.disabled = !hasAllValues;

		if (!savePasswordButton.disabled) {
			pageFeedback.clearInlineAlert();
		}
	}

	[currentPassword, newPassword, confirmPassword].forEach((input) => {
		input?.addEventListener('input', syncButtonState);
	});

	savePasswordButton?.addEventListener('click', async () => {
		const currentValue = currentPassword?.value || '';
		const newValue = newPassword?.value || '';
		const confirmValue = confirmPassword?.value || '';

		if (!currentValue || !newValue || !confirmValue) {
			pageFeedback.inlineAlert('すべての必須項目を入力してください。', 'warning');
			return;
		}

		if (!validatePassword(newValue)) {
			pageFeedback.inlineAlert('新しいパスワードが要件を満たしていません。', 'warning');
			return;
		}

		if (newValue !== confirmValue) {
			pageFeedback.inlineAlert('新しいパスワードと確認用パスワードが一致していません。', 'warning');
			return;
		}

		if (currentValue === newValue) {
			pageFeedback.inlineAlert('現在のパスワードとは異なる新しいパスワードを設定してください。', 'warning');
			return;
		}

		const confirmed = await pageFeedback.confirm({
			title: 'パスワードを変更しますか？',
			message: '次の内容でパスワードを更新します。',
			detailTitle: '',
			details: [
				'新しいパスワードを登録',
				'次回以降は新しいパスワードでログイン'
			],
			confirmLabel: '変更する',
			cancelLabel: '戻る',
			variant: 'success'
		});

		if (!confirmed) {
			return;
		}

		window.sessionStorage.setItem('ppe-home-message-title', 'パスワード変更');
		window.sessionStorage.setItem('ppe-home-message', 'パスワードを変更しました');
		window.sessionStorage.setItem('ppe-home-message-time', new Date().toLocaleString('ja-JP', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit'
		}));
		window.location.href = './account.html';
	});

	syncButtonState();
});