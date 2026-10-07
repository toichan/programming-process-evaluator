(() => {
	const sidebar = document.getElementById('teacherSidebar');
	const toggle = document.querySelector('.teacher-nav-toggle');
	if (!sidebar || !toggle) {
		return;
	}

	const mobileViewport = window.matchMedia('(max-width: 768px)');
	const setOpen = (open) => {
		if (!open && mobileViewport.matches && sidebar.contains(document.activeElement)) {
			toggle.focus();
		}
		sidebar.classList.toggle('show', open);
		document.body.classList.toggle('teacher-nav-open', open && mobileViewport.matches);
		toggle.setAttribute('aria-expanded', String(open));
		toggle.setAttribute('aria-label', open ? 'メニューを閉じる' : 'メニューを開く');
		if (mobileViewport.matches) {
			sidebar.inert = !open;
			sidebar.setAttribute('aria-hidden', String(!open));
		} else {
			sidebar.inert = false;
			sidebar.removeAttribute('aria-hidden');
		}
	};

	toggle.addEventListener('click', () => setOpen(!sidebar.classList.contains('show')));
	sidebar.addEventListener('click', (event) => {
		const unavailable = event.target instanceof Element
			? event.target.closest('button[data-feature-allowed]') : null;
		if (unavailable) {
			event.preventDefault();
			setOpen(false);
			window.PPEFeedback.createPageFeedback({ title: '機能の利用', variant: 'warning' }).toast({
				message: unavailable.dataset.featureAllowed === 'true'
					? 'この機能は現在準備中です。'
					: 'あなたのアカウントではこの機能の利用が許可されていません。システム管理者へお問い合わせください。',
				variant: 'warning'
			});
			return;
		}
		if (event.target instanceof Element && event.target.closest('a')) {
			setOpen(false);
		}
	});
	document.addEventListener('click', (event) => {
		if (sidebar.classList.contains('show')
				&& !sidebar.contains(event.target)
				&& !toggle.contains(event.target)) {
			setOpen(false);
		}
	});
	document.addEventListener('keydown', (event) => {
		if (event.key === 'Escape' && sidebar.classList.contains('show')) {
			setOpen(false);
			toggle.focus();
		}
	});
	mobileViewport.addEventListener('change', () => setOpen(false));
	setOpen(false);
})();
