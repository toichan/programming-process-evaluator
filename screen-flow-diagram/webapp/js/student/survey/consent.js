window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const feedback = window.PPEFeedback || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const consentFormSection = document.querySelector('.consent-form-section');
  const confirmRead = document.querySelector('#confirmRead');
  const saveConsentButton = document.querySelector('#saveConsentButton');
  const consentInputs = document.querySelectorAll('input[name="consentDecision"]');
  const pageFeedback = feedback.createPageFeedback({
    title: '研究同意確認',
    alertTarget: consentFormSection
  });

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  function selectedDecision() {
    const checked = document.querySelector('input[name="consentDecision"]:checked');
    return checked ? checked.value : '';
  }

  function nowLabel() {
    return new Date().toLocaleString('ja-JP', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function syncButtonState() {
    if (!saveConsentButton) {
      return;
    }

    saveConsentButton.disabled = !(confirmRead?.checked && selectedDecision());

    if (saveConsentButton.disabled) {
      return;
    }

    pageFeedback.clearInlineAlert();
  }

  confirmRead?.addEventListener('change', syncButtonState);
  consentInputs.forEach((input) => input.addEventListener('change', syncButtonState));

  saveConsentButton?.addEventListener('click', async () => {
    const decision = selectedDecision();
    if (!confirmRead?.checked || !decision) {
      pageFeedback.inlineAlert('説明を確認し、同意するかどうかを選択してください。', 'warning');
      return;
    }

    pageFeedback.clearInlineAlert();

    const confirmed = await pageFeedback.confirm({
      title: '同意内容を確定しますか？',
      message: '次のデータを送信します。',
      detailTitle: '',
      details: [
        decision === 'agree' ? '選択した研究協力への同意' : '選択した研究協力への不同意',
        '回答日時'
      ],
      confirmLabel: '確定する',
      cancelLabel: '戻る',
      variant: decision === 'agree' ? 'success' : 'warning'
    });

    if (!confirmed) {
      return;
    }

    window.sessionStorage.setItem('ppe-home-message', '研究同意確認が提出されました');
    window.sessionStorage.setItem('ppe-home-message-time', nowLabel());
    window.location.href = '../home/home.html';
  });

  syncButtonState();
});