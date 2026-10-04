(() => {
  const modalIds = new Set(['loginHelpModal', 'studentHelpModal']);
  const triggers = new WeakMap();

  document.addEventListener('show.bs.modal', (event) => {
    const modal = event.target;
    if (!(modal instanceof HTMLElement) || !modalIds.has(modal.id)) return;
    const trigger = event.relatedTarget;
    if (trigger instanceof HTMLElement) triggers.set(modal, trigger);
  });

  document.addEventListener('hide.bs.modal', (event) => {
    const modal = event.target;
    if (modal instanceof HTMLElement && modalIds.has(modal.id)
      && modal.contains(document.activeElement)) document.activeElement.blur();
  });

  document.addEventListener('hidden.bs.modal', (event) => {
    const modal = event.target;
    if (!(modal instanceof HTMLElement) || !modalIds.has(modal.id)) return;
    const trigger = triggers.get(modal);
    if (trigger?.isConnected) trigger.focus({ preventScroll: true });
  });
})();
