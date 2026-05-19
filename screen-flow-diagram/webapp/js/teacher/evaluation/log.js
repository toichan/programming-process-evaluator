// teacher/log.js
// student/log.jsの内容をそのままコピーして利用できます。
// 必要に応じてimportパスやteacher用の微調整を行ってください。

window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const codeBlock = document.querySelector('#codeBlock');
  const timestamp = document.querySelector('#timestamp');
  const boundaryMessage = document.querySelector('#boundaryMessage');
  const stepIndicator = document.querySelector('#stepIndicator');
  const sourceCodeId = document.querySelector('#sourceCodeId');
  const executionId = document.querySelector('#executionId');
  const prevCodeButton = document.querySelector('#prevCodeButton');
  const nextCodeButton = document.querySelector('#nextCodeButton');
  const logTimeline = document.querySelector('#logTimeline');
  const sidebarSwitchButtons = document.querySelectorAll('[data-sidebar-panel]');
  const timelinePanel = document.querySelector('#timelinePanel');
  const reasonsPanel = document.querySelector('#reasonsPanel');
  const reasonFilterGroup = document.querySelector('#reasonFilterGroup');
  const reasonList = document.querySelector('#reasonList');
  const logs = JSON.parse(document.querySelector('#log-data')?.textContent || '[]');
  const reasonData = JSON.parse(document.querySelector('#evaluation-reason-data')?.textContent || '{}');
  const reasonFilters = Array.isArray(reasonData.reasonFilters) ? reasonData.reasonFilters : [];
  const reasons = Array.isArray(reasonData.reasons) ? reasonData.reasons : [];

  let index = 0;
  let currentReasonFilter = 'all';

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  function escapeHTML(text) {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function updateButtons() {
    if (prevCodeButton) {
      prevCodeButton.disabled = index === 0;
    }

    if (nextCodeButton) {
      nextCodeButton.disabled = index === logs.length - 1;
    }
  }

  function updateTimeline() {
    const items = logTimeline?.querySelectorAll('.timeline-item') || [];
    items.forEach((item, itemIndex) => {
      item.classList.toggle('is-active', itemIndex === index);
    });
  }

  // ...existing code (student/log.jsのロジックをそのままコピー)
});
