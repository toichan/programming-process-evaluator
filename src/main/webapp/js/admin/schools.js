window.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('#schoolForm');
  const feedback = window.PPEFeedback.createPageFeedback({
    title: '学校管理',
    alertTarget: '#schoolFeedback'
  });
  const button = form.querySelector('button[type="submit"]');
  let pending = false;
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (pending || !form.reportValidity()) return;
    pending = true;
    button.disabled = true;
    try {
      const isNew = form.elements.schoolId.value === '0';
      const confirmed = await feedback.confirm({
        title: isNew ? '学校を登録しますか？' : '学校情報を変更しますか？',
        message: isNew ? '学校情報を登録します。' : '学校情報を更新します。',
        details: [
          { label: '学校名', text: form.elements.name.value },
          { label: 'セキュリティレベル', text: `レベル${form.elements.securityLevel.value}` },
          { label: '変更可能な範囲', text: form.dataset.locked === 'true'
            ? '生徒登録済みのため、学校名だけ変更します。'
            : '生徒を登録した後は、セキュリティレベルを変更できません。', emphasis: true },
          '操作の実行者・日時・変更内容を監査記録に保存します。'
        ],
        confirmLabel: isNew ? '登録する' : '保存する',
        cancelLabel: 'キャンセル'
      });
      if (confirmed) {
        form.elements.changeConfirmed.value = 'yes';
        HTMLFormElement.prototype.submit.call(form);
      }
    } finally {
      pending = false;
      button.disabled = false;
    }
  });
});
