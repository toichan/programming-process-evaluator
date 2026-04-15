window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	const headerPlaceholder = document.querySelector('#header-placeholder');
	const footerPlaceholder = document.querySelector('#footer-placeholder');

	if (headerPlaceholder && header) {
		headerPlaceholder.innerHTML = header;
	}

	if (footerPlaceholder && footer) {
		footerPlaceholder.innerHTML = footer;
	}
});