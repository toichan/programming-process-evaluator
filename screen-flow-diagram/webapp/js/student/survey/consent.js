window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const confirmRead = document.querySelector('#confirmRead');
  const saveConsentButton = document.querySelector('#saveConsentButton');
  const consentInputs = document.querySelectorAll('input[name="consentDecision"]');

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
  }

  confirmRead?.addEventListener('change', syncButtonState);
  consentInputs.forEach((input) => input.addEventListener('change', syncButtonState));

  saveConsentButton?.addEventListener('click', () => {
    const decision = selectedDecision();
    if (!confirmRead?.checked || !decision) {
      return;
    }

    window.sessionStorage.setItem('ppe-home-message', '研究同意確認が提出されました');
    window.sessionStorage.setItem('ppe-home-message-time', nowLabel());
    window.location.href = '../home/home.html';
  });

  syncButtonState();
});