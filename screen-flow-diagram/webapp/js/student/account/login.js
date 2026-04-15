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
  loadComponent('#header-placeholder', '../../template/header.html');
  loadComponent('#footer-placeholder', '../../template/footer.html');
});
