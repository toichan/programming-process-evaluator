(function() {
  document.addEventListener('DOMContentLoaded', function() {
    const errorElement = document.querySelector('[data-auth-error]');
    const feedbackTarget = document.getElementById('auth-feedback');
    if (!errorElement || !feedbackTarget || !window.PPEFeedback) {
      return;
    }

    const feedback = window.PPEFeedback.createPageFeedback({
      title: '認証',
      alertTarget: feedbackTarget
    });
    feedback.inlineAlert(errorElement.dataset.authError, 'danger');
  });
})();
