(() => {
	const form = document.getElementById('passwordChangeForm');
	const current = document.getElementById('currentPassword');
	const next = document.getElementById('newPassword');
	const confirmation = document.getElementById('confirmPassword');
	const submit = document.getElementById('savePasswordButton');
	const summary = document.getElementById('passwordRuleSummary');
	const rules = Array.from(document.querySelectorAll('[data-password-rule]'));
	if (!form || !current || !next || !confirmation || !submit || !summary || rules.length !== 5) {
		throw new Error('Password change controls are incomplete.');
	}

	function update() {
		const value = next.value;
		const count = [/[A-Z]/, /[a-z]/, /[0-9]/, /[!-\/:-@\[-`{-~]/]
			.filter(pattern => pattern.test(value)).length;
		const checks = {
			length: [value.length > 0, value.length >= 8 && value.length <= 32, `現在${value.length}文字です。8〜32文字で入力してください。`],
			characters: [value.length > 0, /^[\x21-\x7e]+$/.test(value), '空白・全角文字・制御文字は使用できません。'],
			categories: [value.length > 0, count >= 3, `現在${count}種類です。4種類のうち3種類以上を含めてください。`],
			different: [Boolean(value && current.value), value !== current.value, '現在と異なるパスワードを入力してください。'],
			match: [Boolean(value && confirmation.value), value === confirmation.value, '新しいパスワードと確認用パスワードが一致しません。']
		};
		let passed = 0;
		for (const rule of rules) {
			const [ready, valid, reason] = checks[rule.dataset.passwordRule];
			const state = !ready ? 'pending' : valid ? 'passed' : 'failed';
			rule.dataset.state = state;
			rule.querySelector('.rule-indicator').textContent = state === 'passed' ? '✓' : state === 'failed' ? '!' : '○';
			rule.querySelector('.rule-status').textContent = state === 'passed' ? '満たしています' : state === 'failed' ? reason : '入力待ち';
			if (ready && valid) passed++;
		}
		submit.disabled = !current.value || passed !== rules.length;
		summary.textContent = !value && !current.value && !confirmation.value
			? '入力すると条件を確認できます。すべての条件を満たすと更新できます。'
			: passed === rules.length
			? '入力条件を満たしています。現在のパスワードの正しさは更新時に確認します。'
			: `${passed} / ${rules.length}項目を満たしています。未達成の条件をご確認ください。`;
	}
	for (const input of [current, next, confirmation]) {
		input.addEventListener('input', update);
		input.addEventListener('change', update);
	}
	form.addEventListener('submit', event => {
		update();
		if (submit.disabled) event.preventDefault();
	});
	window.addEventListener('pageshow', update);
	update();
})();
