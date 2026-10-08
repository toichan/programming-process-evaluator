(() => {
  const form = document.getElementById('passwordChangeForm');
  const submit = document.getElementById('savePasswordButton');
  const feedback = window.PPEFeedback.createPageFeedback({
    title: 'パスワード変更',
    alertTarget: document.getElementById('passwordInlineFeedback')
  });
  let confirming = false;

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (submit.disabled || confirming) return;
    confirming = true;
    try {
      const confirmed = await feedback.confirm({
        title: 'パスワードを変更しますか？',
        message: '変更後は、すべての端末で再ログインが必要です。',
        detailTitle: '',
        details: [
          '現在のログインを含む本人の全セッションを失効',
          '新しいパスワードで教師ログインへ',
          'このプロトタイプでは実際の更新・全セッション失効は行いません'
        ],
        confirmLabel: '変更する',
        cancelLabel: '戻る',
        variant: 'success'
      });
      if (confirmed) {
        window.sessionStorage.setItem('ppeTeacherPasswordChangePreview', '1');
        window.localStorage.removeItem('ppeTeacherSession');
        form.reset();
        window.location.href = './login.html';
      }
    } finally {
      confirming = false;
    }
  });
})();
