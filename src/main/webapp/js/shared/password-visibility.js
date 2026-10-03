document.querySelectorAll('[data-password-toggle]').forEach((button) => {
	const inputId = button.getAttribute('data-password-toggle');
	const input = document.getElementById(inputId);
	if (!(input instanceof HTMLInputElement)) {
		throw new Error(`Password visibility control targets a missing input: ${inputId}`);
	}
	const label = (button.getAttribute('aria-label') || 'パスワードを表示').replace(/を表示$/, '');
	button.setAttribute('aria-controls', inputId);
	button.setAttribute('aria-pressed', String(input.type === 'text'));

	button.addEventListener('click', () => {
		const isVisible = input.type === 'password';
		input.type = isVisible ? 'text' : 'password';
		button.classList.toggle('is-visible', isVisible);
		button.setAttribute('aria-label', `${label}を${isVisible ? '隠す' : '表示'}`);
		button.setAttribute('aria-pressed', String(isVisible));
	});
});
