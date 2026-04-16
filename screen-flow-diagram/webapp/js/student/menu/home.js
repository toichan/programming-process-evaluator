window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	const headerPlaceholder = document.querySelector('#header-placeholder');
	const footerPlaceholder = document.querySelector('#footer-placeholder');
	const homeMessage = document.querySelector('#homeMessage');
	const homeMessageTime = document.querySelector('#homeMessageTime');
	const submittedMessage = window.sessionStorage.getItem('ppe-home-message');
	const submittedTime = window.sessionStorage.getItem('ppe-home-message-time');

	if (headerPlaceholder && header) {
		headerPlaceholder.innerHTML = header;
	}

	if (footerPlaceholder && footer) {
		footerPlaceholder.innerHTML = footer;
	}

	if (homeMessage && submittedMessage) {
		homeMessage.classList.remove('d-none');
		homeMessage.firstElementChild.textContent = submittedMessage;

		if (homeMessageTime && submittedTime) {
			homeMessageTime.textContent = `提出日時: ${submittedTime}`;
		}

		window.sessionStorage.removeItem('ppe-home-message');
		window.sessionStorage.removeItem('ppe-home-message-time');
	}
});