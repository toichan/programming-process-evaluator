window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	const feedback = window.PPEFeedback || {};
	const headerPlaceholder = document.querySelector('#header-placeholder');
	const footerPlaceholder = document.querySelector('#footer-placeholder');
	const submittedMessage = window.sessionStorage.getItem('ppe-home-message');
	const submittedTime = window.sessionStorage.getItem('ppe-home-message-time');
	const submittedTitle = window.sessionStorage.getItem('ppe-home-message-title');

	if (headerPlaceholder && header) {
		headerPlaceholder.innerHTML = header;
	}

	if (footerPlaceholder && footer) {
		footerPlaceholder.innerHTML = footer;
	}

	if (submittedMessage && typeof feedback.showToast === 'function') {
		const toastMessage = submittedTime
			? `${submittedMessage} (${submittedTime})`
			: submittedMessage;

		feedback.showToast({
			title: submittedTitle || 'お知らせ',
			message: toastMessage,
			variant: 'success',
			delay: 3200
		});
	}

	if (submittedMessage) {
		window.sessionStorage.removeItem('ppe-home-message');
		window.sessionStorage.removeItem('ppe-home-message-time');
		window.sessionStorage.removeItem('ppe-home-message-title');
	}
});