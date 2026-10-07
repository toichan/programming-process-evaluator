window.addEventListener('DOMContentLoaded', () => {
  const TEACHER_SESSION_STORAGE_KEY = 'ppeTeacherSession';
  const teacherLoginForm = document.querySelector('#teacher-login');
  const teacherIdInput = document.querySelector('#teacherId');
  const teacherPasswordInput = document.querySelector('#teacherPassword');
  const togglePasswordButton = document.querySelector('#togglePassword');

  if (window.sessionStorage.getItem('ppeTeacherPasswordChangePreview') === '1') {
    window.sessionStorage.removeItem('ppeTeacherPasswordChangePreview');
    window.PPEFeedback.createPageFeedback({
      title: 'パスワード変更',
      alertTarget: document.getElementById('teacherLoginFeedback')
    }).inlineAlert('パスワード変更後の表示例です。新しいパスワードで再ログインしてください。このプロトタイプでは実際のパスワードは変更していません。', 'success');
  }

  togglePasswordButton?.addEventListener('click', () => {
    const isHidden = teacherPasswordInput?.type === 'password';

    if (!teacherPasswordInput) {
      return;
    }

    teacherPasswordInput.type = isHidden ? 'text' : 'password';
    togglePasswordButton.classList.toggle('is-visible', isHidden);
    togglePasswordButton.setAttribute('aria-label', isHidden ? 'パスワードを隠す' : 'パスワードを表示');
  });

  teacherLoginForm?.addEventListener('submit', (event) => {
    event.preventDefault();

    const hasTeacherId = Boolean(teacherIdInput?.value.trim());
    const hasPassword = Boolean(teacherPasswordInput?.value.trim());

    if (!hasTeacherId || !hasPassword) {
      teacherLoginForm.reportValidity();
      return;
    }

    const session = {
      teacherId: teacherIdInput.value.trim(),
      school: 'all'
    };
    window.localStorage.setItem(TEACHER_SESSION_STORAGE_KEY, JSON.stringify(session));

    if (session.teacherId.toLowerCase() === 'admin') {
      window.location.href = '../../admin/management.html';
      return;
    }

    // ログイン成功時に生徒アカウント管理画面へ遷移
    window.location.href = './account.html';
  });
});
