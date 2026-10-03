document.querySelectorAll('[data-password-toggle]').forEach((button) => {
	const inputId = button.getAttribute('data-password-toggle');
	const input = document.getElementById(inputId);
	if (!(input instanceof HTMLInputElement)) {
		throw new Error(`Password visibility control targets a missing input: ${inputId}`);
	}

	button.addEventListener('click', () => {
		const isVisible = input.type === 'password';
		input.type = isVisible ? 'text' : 'password';
		button.classList.toggle('is-visible', isVisible);
		button.setAttribute('aria-label', isVisible ? 'パスワードを隠す' : 'パスワードを表示');
	});
});
