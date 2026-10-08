(function() {
  document.addEventListener('DOMContentLoaded', function() {
    const errorElement = document.querySelector('[data-auth-error]');
    const noticeElement = document.querySelector('[data-auth-notice]');
    const feedbackTarget = document.getElementById('auth-feedback');
    if ((!errorElement && !noticeElement) || !feedbackTarget || !window.PPEFeedback) {
      return;
    }

    const feedback = window.PPEFeedback.createPageFeedback({
      title: '認証',
      alertTarget: feedbackTarget
    });
    if (errorElement) feedback.inlineAlert(errorElement.dataset.authError, 'danger');
    else feedback.inlineAlert(noticeElement.dataset.authNotice, 'success');
  });
})();
