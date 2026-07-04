window.addEventListener('DOMContentLoaded', () => {
  const TEACHER_SESSION_STORAGE_KEY = 'ppeTeacherSession';
  const teacherLoginForm = document.querySelector('#teacher-login');
  const teacherIdInput = document.querySelector('#teacherId');
  const teacherPasswordInput = document.querySelector('#teacherPassword');
  const togglePasswordButton = document.querySelector('#togglePassword');

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

    // ログイン成功時に教師アカウント管理画面へ遷移
    window.location.href = './account.html';
  });
});
