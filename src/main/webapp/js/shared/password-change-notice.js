window.addEventListener('DOMContentLoaded', () => {
	window.PPEFeedback.createPageFeedback({ title: 'パスワード変更' }).toast({
		message: 'パスワードを変更しました。',
		variant: 'success',
		delay: 2500
	});
});
