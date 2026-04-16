async function loadComponent(selector, path) {
  try {
    const response = await fetch(path);
    if (!response.ok) {
      throw new Error(`Failed to load ${path}: ${response.status}`);
    }
    const html = await response.text();
    document.querySelector(selector).innerHTML = html;
  } catch (error) {
    console.error(error);
  }
}

window.addEventListener('DOMContentLoaded', () => {
  const studentLoginForm = document.querySelector('#student-login');
  const studentIdInput = document.querySelector('#studentId');
  const studentPasswordInput = document.querySelector('#studentPassword');
  const togglePasswordButton = document.querySelector('#togglePassword');

  loadComponent('#header-placeholder', '../../template/header.html');
  loadComponent('#footer-placeholder', '../../template/footer.html');

  togglePasswordButton?.addEventListener('click', () => {
    const isHidden = studentPasswordInput?.type === 'password';

    if (!studentPasswordInput) {
      return;
    }

    studentPasswordInput.type = isHidden ? 'text' : 'password';
    togglePasswordButton.classList.toggle('is-visible', isHidden);
    togglePasswordButton.setAttribute('aria-label', isHidden ? 'パスワードを隠す' : 'パスワードを表示');
  });

  studentLoginForm?.addEventListener('submit', (event) => {
    event.preventDefault();

    const hasStudentId = Boolean(studentIdInput?.value.trim());
    const hasPassword = Boolean(studentPasswordInput?.value.trim());

    if (!hasStudentId || !hasPassword) {
      studentLoginForm.reportValidity();
      return;
    }

    window.location.href = '../menu/home.html';
  });
});
