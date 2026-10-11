window.PPEUnsavedNavigation = {
	bind({ isDirty, feedback, message }) {
		let leaving = false;
		let pending = false;
		const confirmLeave = async (options = {}) => {
			if (!isDirty()) return true;
			if (pending) return false;
			pending = true;
			try {
				return await feedback.confirm({
					title: '未保存の変更',
					message,
					confirmLabel: '破棄して移動',
					cancelLabel: '編集を続ける',
					details: ['保存していない入力内容は失われます。'],
					...options
				});
			} finally {
				pending = false;
			}
		};
		window.PPEConfirmLeave = confirmLeave;
		window.PPEAllowNavigation = () => { leaving = true; };
		document.addEventListener('click', async (event) => {
			const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
			if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey
					|| link.target === '_blank' || link.hasAttribute('download')
					|| link.getAttribute('href').startsWith('#') || !isDirty()) return;
			event.preventDefault();
			if (await confirmLeave()) {
				leaving = true;
				window.location.assign(link.href);
			}
		});
		window.addEventListener('beforeunload', (event) => {
			if (!leaving && isDirty()) {
				event.preventDefault();
				event.returnValue = '';
			}
		});
		return { confirmLeave, allowNavigation: () => { leaving = true; } };
	}
};
