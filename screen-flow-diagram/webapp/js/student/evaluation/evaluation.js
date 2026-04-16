window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const reasonFilters = document.querySelectorAll('[data-filter]');
  const reasonCards = document.querySelectorAll('.reason-card');
  const rubricTabs = document.querySelectorAll('[data-rubric-target]');
  const rubricPanels = document.querySelectorAll('.rubric-panel');

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  reasonFilters.forEach((button) => {
    button.addEventListener('click', () => {
      const filter = button.getAttribute('data-filter');

      reasonFilters.forEach((item) => item.classList.remove('is-active'));
      button.classList.add('is-active');

      reasonCards.forEach((card) => {
        const category = card.getAttribute('data-category');
        const hidden = filter !== 'all' && category !== filter;
        card.classList.toggle('is-hidden', hidden);
      });
    });
  });

  rubricTabs.forEach((button) => {
    button.addEventListener('click', () => {
      const targetId = button.getAttribute('data-rubric-target');

      rubricTabs.forEach((item) => item.classList.remove('is-active'));
      rubricPanels.forEach((panel) => panel.classList.remove('is-active'));

      button.classList.add('is-active');
      document.getElementById(targetId)?.classList.add('is-active');
    });
  });
});