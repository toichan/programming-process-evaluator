window.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('#consentForm');
  if (!form) return;
  const feedback = window.PPEFeedback.createPageFeedback({
    title: '研究同意確認',
    alertTarget: '#consentFeedback'
  });
  const current = form.dataset.currentStatus;
  const button = form.querySelector('button[type="submit"]');
  let pending = false;

  form.addEventListener('submit', async (event) => {
    if (current === 'UNCONFIRMED') return;
    event.preventDefault();
    if (pending || !form.reportValidity()) return;
    const decision = form.querySelector('input[name="consentDecision"]:checked').value;
    const wasAgreed = current === 'AGREED';
    const willAgree = decision === 'agree';
    if (wasAgreed === willAgree) {
      feedback.inlineAlert('現在の回答と同じです。変更する回答を選んでください。', 'warning');
      return;
    }
    pending = true;
    button.disabled = true;
    try {
      const confirmed = await feedback.confirm({
        title: '研究協力への回答を変更しますか？',
        message: willAgree ? '研究協力に同意する回答へ変更します。' : '研究協力への同意を撤回します。',
        detailTitle: '',
        details: [
          {
            label: '回答の変更',
            text: `${wasAgreed ? '同意する' : current === 'WITHDRAWN' ? '撤回済み' : '同意しない'} → ${willAgree ? '同意する' : '同意しない（撤回）'}`
          },
          {
            label: '研究でのデータ利用',
            text: willAgree ? '同意変更前のデータを含め、研究に利用されます。'
              : '同意を取り消すと、これまでのデータも含め、今後の研究には使われません。',
            emphasis: true
          },
          {
            label: '学習・アンケートへの影響',
            text: '課題学習・提出・AI評価は引き続き利用できます。' +
              (willAgree ? '対象のアンケートに回答できるようになります。'
                : '研究用のアンケートには回答できなくなります。')
          },
          {
            label: '記録される内容',
            text: '回答・変更日時・確認した同意文書の版を履歴として記録します。'
          }
        ],
        confirmLabel: '変更する',
        cancelLabel: 'キャンセル',
        variant: willAgree ? 'success' : 'warning'
      });
      if (confirmed) {
        form.elements.namedItem('changeConfirmed').value = 'yes';
        HTMLFormElement.prototype.submit.call(form);
      }
    } finally {
      pending = false;
      button.disabled = false;
    }
  });
});
