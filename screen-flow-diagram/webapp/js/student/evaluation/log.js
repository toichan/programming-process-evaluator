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
  const logs = JSON.parse(document.querySelector('#log-data')?.textContent || '[]');

  let index = 0;

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

  createTimeline();
  renderCode(index);
});