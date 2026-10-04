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
