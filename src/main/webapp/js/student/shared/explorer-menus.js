window.PPEExplorerMenus = (() => {
  const instances = new Map();
  // Bootstrap's delegated capture handler expects the menu beside its toggle.
  window.addEventListener('keydown', (event) => {
    if (!['ArrowUp', 'ArrowDown', 'Escape'].includes(event.key)) return;
    const menu = event.target.closest('[data-exercise-menu-overlay]');
    if (!menu) return;
    const instance = [...instances.values()].find((item) => item.menu === menu);
    if (!instance) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.key === 'Escape') {
      instance.dropdown.hide();
      instance.toggle.focus();
      return;
    }
    const items = [...menu.querySelectorAll('.dropdown-item:not(.disabled):not(:disabled)')]
      .filter((item) => item.getClientRects().length > 0);
    if (!items.length) return;
    const index = items.indexOf(document.activeElement);
    const next = index < 0
      ? (event.key === 'ArrowDown' ? 0 : items.length - 1)
      : Math.max(0, Math.min(items.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1)));
    items[next].focus();
  }, true);
  function bind(container) {
    container.querySelectorAll('.lesson-tree-menu-toggle').forEach((toggle) => {
      const menu = toggle.parentElement.querySelector('.dropdown-menu');
      const home = menu.parentElement;
      const dropdown = bootstrap.Dropdown.getOrCreateInstance(toggle, {
        popperConfig: (config) => ({ ...config, strategy: 'fixed' })
      });
      toggle.addEventListener('show.bs.dropdown', () => {
        menu.dataset.exerciseMenuOverlay = 'true';
        document.body.append(menu);
      });
      toggle.addEventListener('hidden.bs.dropdown', () => {
        delete menu.dataset.exerciseMenuOverlay;
        home.append(menu);
      });
      instances.set(toggle, { dropdown, menu, home, toggle });
    });
  }
  function clear(container) {
    for (const [toggle, { dropdown, menu, home }] of instances) {
      if (!container.contains(toggle)) continue;
      dropdown.hide();
      dropdown.dispose();
      delete menu.dataset.exerciseMenuOverlay;
      home.append(menu);
      instances.delete(toggle);
    }
  }
  return { bind, clear };
})();
