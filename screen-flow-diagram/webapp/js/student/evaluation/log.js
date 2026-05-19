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

  function getLogLabel(log) {
    if (log.submitted_at) {
      return '手動提出';
    }

    return log.auto_save ? '自動保存' : '手動保存';
  }

  function getLogPreview(log) {
    const parts = [];
    parts.push(`ID: ${log.id}`);
    parts.push(`残作業量: ${log.remaining_work}`);
    if (log.remaining_proportion !== null && log.remaining_proportion !== undefined) {
      parts.push(`進捗差分: ${log.remaining_proportion}`);
    }
    return parts.join(' / ');
  }

  function splitLines(text) {
    return text.match(/.*(?:\n|$)/g)?.filter((line) => line.length > 0) || [];
  }

  function normalizeLineText(text) {
    return text.endsWith('\n') ? text.slice(0, -1) : text;
  }

  function renderCodeLines(lines) {
    return lines
      .map((line, lineIndex) => {
        const safeText = escapeHTML(normalizeLineText(line)) || '&nbsp;';
        return `<span class="code-line"><span class="line-number">${lineIndex + 1}</span><span class="line-content">${safeText}</span></span>`;
      })
      .join('');
  }

  function renderEvidenceItems(evidenceItems) {
    if (!Array.isArray(evidenceItems) || evidenceItems.length === 0) {
      return '';
    }

    const chips = evidenceItems
      .flatMap((item) => {
        if (Array.isArray(item.ids)) {
          return item.ids.map((id) => `<span class="evidence-pill">${escapeHTML(item.label || 'ID')}: ${escapeHTML(String(id))}</span>`);
        }

        if (item.id !== undefined && item.id !== null) {
          return [`<span class="evidence-pill">${escapeHTML(item.label || 'ID')}: ${escapeHTML(String(item.id))}</span>`];
        }

        if (item.value !== undefined && item.value !== null) {
          const unit = item.unit ? String(item.unit) : '';
          return [`<span class="evidence-pill">${escapeHTML(item.label || '値')}: ${escapeHTML(String(item.value))}${escapeHTML(unit)}</span>`];
        }

        return [];
      })
      .join('');

    if (!chips) {
      return '';
    }

    return `<div class="evidence-row">${chips}</div>`;
  }

  function renderReasonCard(reason) {
    const details = Array.isArray(reason.details) ? reason.details : [];

    const detailHTML = details.length > 0
      ? `<ul class="reason-detail-list">${details.map((detail) => {
        const evidenceHTML = renderEvidenceItems(detail.evidence);
        return `<li><span>${escapeHTML(detail.text || '')}</span>${evidenceHTML}</li>`;
      }).join('')}</ul>`
      : '';

    const reasonEvidenceHTML = renderEvidenceItems(reason.evidence);

    return `
      <article class="reason-card" data-category="${escapeHTML(reason.category || '')}">
        <div class="reason-title-row">
          <h3>${escapeHTML(reason.title || '-')} : ${escapeHTML(String(reason.score ?? '-'))}</h3>
          <span class="reason-chip">${escapeHTML(reason.categoryLabel || '')}</span>
        </div>
        <p class="reason-body">${escapeHTML(reason.body || '')}</p>
        ${detailHTML}
        ${reasonEvidenceHTML}
      </article>
    `;
  }

  function updateReasonList() {
    if (!reasonList) {
      return;
    }

    const filteredReasons = reasons.filter((reason) => currentReasonFilter === 'all' || reason.category === currentReasonFilter);
    reasonList.innerHTML = filteredReasons.map(renderReasonCard).join('');
  }

  function createReasonFilters() {
    if (!reasonFilterGroup) {
      return;
    }

    reasonFilterGroup.innerHTML = '';
    reasonFilters.forEach((filter, filterIndex) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `reason-filter${filterIndex === 0 ? ' is-active' : ''}`;
      button.textContent = filter.label;
      button.setAttribute('data-reason-filter', filter.key);
      button.addEventListener('click', () => {
        currentReasonFilter = filter.key;
        reasonFilterGroup.querySelectorAll('.reason-filter').forEach((element) => {
          element.classList.toggle('is-active', element === button);
        });
        updateReasonList();
      });
      reasonFilterGroup.appendChild(button);
    });

    if (reasonFilters.length > 0) {
      currentReasonFilter = reasonFilters[0].key;
    }
  }

  function switchSidebarPanel(targetPanel) {
    const showTimeline = targetPanel === 'timeline';
    timelinePanel?.classList.toggle('is-active', showTimeline);
    reasonsPanel?.classList.toggle('is-active', !showTimeline);

    sidebarSwitchButtons.forEach((button) => {
      button.classList.toggle('is-active', button.getAttribute('data-sidebar-panel') === targetPanel);
    });
  }

  function getExecutionId(log, currentIndex) {
    if (log.execution_id !== undefined && log.execution_id !== null && log.execution_id !== '') {
      return log.execution_id;
    }

    return 171311 + currentIndex;
  }

  function renderCode(currentIndex) {
    const currentLog = logs[currentIndex];
    if (!currentLog || !codeBlock || !timestamp || !boundaryMessage || !stepIndicator) {
      return;
    }

    codeBlock.innerHTML = renderCodeLines(splitLines(currentLog.content));

    if (sourceCodeId) {
      sourceCodeId.textContent = `ソースコードID: ${currentLog.id ?? '-'}`;
    }

    if (executionId) {
      executionId.textContent = `実行ID: ${getExecutionId(currentLog, currentIndex)}`;
    }

    timestamp.textContent = `保存日時: ${currentLog.created_at}`;
    stepIndicator.textContent = `${currentIndex + 1} / ${logs.length}`;

    if (currentIndex === 0) {
      boundaryMessage.textContent = '最初の変更です。';
    } else if (currentIndex === logs.length - 1) {
      boundaryMessage.textContent = '最後の変更です。';
    } else {
      boundaryMessage.textContent = '';
    }

    updateButtons();
    updateTimeline();
  }

  function createTimeline() {
    if (!logTimeline) {
      return;
    }

    logTimeline.innerHTML = '';
    logs.forEach((log, logIndex) => {
      const item = document.createElement('button');
      item.type = 'button';
      item.className = 'timeline-item';
      item.innerHTML = `
        <div class="timeline-time">${log.created_at}</div>
        <div class="timeline-label">${getLogLabel(log)}</div>
        <div class="timeline-preview">${escapeHTML(getLogPreview(log))}</div>
      `;
      item.addEventListener('click', () => {
        index = logIndex;
        renderCode(index);
      });
      logTimeline.appendChild(item);
    });
  }

  prevCodeButton?.addEventListener('click', () => {
    if (index > 0) {
      index -= 1;
      renderCode(index);
    }
  });

  nextCodeButton?.addEventListener('click', () => {
    if (index < logs.length - 1) {
      index += 1;
      renderCode(index);
    }
  });

  sidebarSwitchButtons.forEach((button) => {
    button.addEventListener('click', () => {
      const targetPanel = button.getAttribute('data-sidebar-panel') || 'timeline';
      switchSidebarPanel(targetPanel);
    });
  });

  createTimeline();
  createReasonFilters();
  updateReasonList();
  switchSidebarPanel('timeline');
  renderCode(index);
});