const taskData = {
  title: 'じゃんけん判定プログラム',
  description: 'ユーザーの入力に応じて、勝敗または入力エラーを表示するプログラムを作成します。',
  features: [
    '入力値を受け取る',
    '条件分岐で勝敗を判定する',
    '不正な入力時に案内を表示する'
  ],
  constraints: '入力は「グー」「チョキ」「パー」のいずれかを想定します。',
  creationRules: [],
  examples: [
    { input: 'パー', output: 'あなたの勝ち' },
    { input: 'ぐー', output: 'グー・チョキ・パーを入力してください' }
  ]
};

function normalizeCreationRules(source) {
  if (Array.isArray(source)) {
    return source
      .map((item) => String(item || '').trim())
      .filter(Boolean);
  }

  if (typeof source !== 'string') {
    return [];
  }

  return source
    .split(/\r?\n|;/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderTaskPanel() {
  const container = document.querySelector('#taskPanelContent');
  if (!container) {
    return;
  }

  const features = Array.isArray(taskData.features) ? taskData.features : [];
  const rules = normalizeCreationRules(taskData.creationRules);
  const examples = Array.isArray(taskData.examples) ? taskData.examples : [];

  const ruleMarkup = rules.length > 0
    ? `<div class="info-block"><div class="info-label">作成時のルール</div><ul class="mb-0">${rules.map((rule) => `<li>${escapeHtml(rule)}</li>`).join('')}</ul></div>`
    : '';

  const examplesMarkup = examples.length > 0
    ? `<div class="info-block io-block"><div class="info-label">想定入出力</div>${examples.map((example) => `
        <div class="case-card io-case-card">
          <div class="io-case-label">入力</div>
          <textarea class="io-case-source" spellcheck="false">${escapeHtml(example.input || '')}</textarea>
          <div class="io-case-label mt-3">出力</div>
          <textarea class="io-case-source" spellcheck="false">${escapeHtml(example.output || '')}</textarea>
        </div>`).join('')}</div>`
    : '';

  container.innerHTML = `
    <h3>${escapeHtml(taskData.title || '課題名未設定')}</h3>
    <p>${escapeHtml(taskData.description || '説明未設定')}</p>
    <div class="info-block">
      <div class="info-label">実装する機能</div>
      <ul class="mb-0">${features.length > 0 ? features.map((feature) => `<li>${escapeHtml(feature)}</li>`).join('') : '<li>未設定</li>'}</ul>
    </div>
    <div class="info-block">
      <div class="info-label">入力制限</div>
      <p class="mb-0">${escapeHtml(taskData.constraints || '未設定')}</p>
    </div>
    ${ruleMarkup}
    ${examplesMarkup}
  `;
}

window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const feedback = window.PPEFeedback || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const codeEditor = document.querySelector('#codeEditor');
  const terminalOutput = document.querySelector('#terminalOutput');
  const terminalInputForm = document.querySelector('#terminalInputForm');
  const terminalInput = document.querySelector('#terminalInput');
  const terminalSendButton = document.querySelector('#terminalSendButton');
  const terminalCancelButton = document.querySelector('#terminalCancelButton');
  const editorMessage = document.querySelector('#editorMessage');
  const lastSavedAt = document.querySelector('#lastSavedAt');
  const codeLogList = document.querySelector('#codeLogList');
  const toggleLogButton = document.querySelector('#toggleLogButton');
  const codeLogContent = document.querySelector('#codeLogContent');
  const downloadButton = document.querySelector('#downloadButton');
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const runResultStatus = document.querySelector('#runResultStatus');
  const runResultTime = document.querySelector('#runResultTime');
  const submitButton = document.querySelector('#submitButton');
  const submitCheckModalElement = document.querySelector('#submitCheckModal');
  const submitCheckSummary = document.querySelector('#submitCheckSummary');
  const submitCheckTableBody = document.querySelector('#submitCheckTableBody');
  const confirmSubmitAfterCheck = document.querySelector('#confirmSubmitAfterCheck');
  const infoTabs = document.querySelectorAll('[data-panel-target]');
  const editorFontSizeInput = document.querySelector('#editorFontSize');
  const editorFontSizeValue = document.querySelector('#editorFontSizeValue');
  const editorLineWrappingInput = document.querySelector('#editorLineWrapping');
  const editorIndentWidthInput = document.querySelector('#editorIndentWidth');
  const editorThemeInput = document.querySelector('#editorTheme');
  const editorPreferencesStatus = document.querySelector('#editorPreferencesStatus');
  const resetEditorPreferencesButton = document.querySelector('#resetEditorPreferences');
  let codeMirrorEditor = null;
  const codeLogEditors = [];
  const ioExampleEditors = [];
  const hintEditors = [];
  let hintEditorsInitialized = false;
  let mockInputsRemaining = 0;
  let mockInputValues = [];
  const defaultEditorPreferences = {
    fontSizePx: 16,
    lineWrapping: false,
    indentWidth: 4,
    theme: 'dark'
  };
  const validThemes = ['dark', 'light', 'high_contrast'];
  const preferenceStorageKey = 'ppe.editorPreferences.demo-student';
  let editorPreferences = { ...defaultEditorPreferences };
  let preferenceStorageError = false;
  try {
    const storedPreferences = JSON.parse(localStorage.getItem(preferenceStorageKey) || 'null');
    if (storedPreferences && Number.isInteger(storedPreferences.fontSizePx)
      && storedPreferences.fontSizePx >= 10 && storedPreferences.fontSizePx <= 24
      && storedPreferences.fontSizePx % 2 === 0
      && typeof storedPreferences.lineWrapping === 'boolean'
      && [2, 4].includes(storedPreferences.indentWidth)
      && validThemes.includes(storedPreferences.theme)) {
      editorPreferences = storedPreferences;
    }
  } catch {
    preferenceStorageError = true;
  }
  const pageFeedback = feedback.createPageFeedback({ title: 'エディター' });
  const submitCheckModal = (typeof bootstrap !== 'undefined' && submitCheckModalElement)
    ? bootstrap.Modal.getOrCreateInstance(submitCheckModalElement)
    : null;
  let latestCheckSummary = null;

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  if (codeEditor && typeof CodeMirror !== 'undefined') {
    codeMirrorEditor = CodeMirror.fromTextArea(codeEditor, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: editorPreferences.lineWrapping,
      theme: {
        dark: 'material-darker',
        light: 'default',
        high_contrast: 'ppe-high-contrast'
      }[editorPreferences.theme],
      indentUnit: editorPreferences.indentWidth,
      tabSize: editorPreferences.indentWidth,
      viewportMargin: Infinity
    });
    codeMirrorEditor.getWrapperElement().style.fontSize = `${editorPreferences.fontSizePx}px`;
  }

  function syncMainEditorLayout() {
    if (!codeMirrorEditor) {
      return;
    }

    codeMirrorEditor.setSize(null, 'auto');
    codeMirrorEditor.refresh();
  }

  syncMainEditorLayout();
  window.addEventListener('resize', syncMainEditorLayout);

  function applyEditorPreferences() {
    codeMirrorEditor?.setOption('lineWrapping', editorPreferences.lineWrapping);
    codeMirrorEditor?.setOption('indentUnit', editorPreferences.indentWidth);
    codeMirrorEditor?.setOption('tabSize', editorPreferences.indentWidth);
    codeMirrorEditor?.setOption('theme', {
      dark: 'material-darker',
      light: 'default',
      high_contrast: 'ppe-high-contrast'
    }[editorPreferences.theme]);
    if (codeMirrorEditor) {
      codeMirrorEditor.getWrapperElement().style.fontSize = `${editorPreferences.fontSizePx}px`;
      codeMirrorEditor.refresh();
    }
  }

  function syncEditorPreferencesControls() {
    if (editorFontSizeInput) {
      editorFontSizeInput.value = String(editorPreferences.fontSizePx);
    }
    if (editorFontSizeValue) {
      editorFontSizeValue.textContent = `${editorPreferences.fontSizePx}px`;
    }
    if (editorLineWrappingInput) {
      editorLineWrappingInput.checked = editorPreferences.lineWrapping;
    }
    if (editorIndentWidthInput) {
      editorIndentWidthInput.value = String(editorPreferences.indentWidth);
    }
    if (editorThemeInput) {
      editorThemeInput.value = editorPreferences.theme;
    }
  }

  function saveEditorPreferences() {
    try {
      localStorage.setItem(preferenceStorageKey, JSON.stringify(editorPreferences));
      preferenceStorageError = false;
      if (editorPreferencesStatus) {
        editorPreferencesStatus.textContent = '設定を保存しました。';
      }
    } catch {
      preferenceStorageError = true;
      if (editorPreferencesStatus) {
        editorPreferencesStatus.textContent = '設定を保存できませんでした。このブラウザーの保存領域を確認してください。';
      }
    }
  }

  function updateEditorPreferences() {
    applyEditorPreferences();
    syncEditorPreferencesControls();
    saveEditorPreferences();
  }

  syncEditorPreferencesControls();
  if (preferenceStorageError && editorPreferencesStatus) {
    editorPreferencesStatus.textContent = '保存済みの設定を読み込めませんでした。初期設定で表示しています。';
  }

  editorFontSizeInput?.addEventListener('input', () => {
    editorPreferences.fontSizePx = Number(editorFontSizeInput.value);
    updateEditorPreferences();
  });
  editorLineWrappingInput?.addEventListener('change', () => {
    editorPreferences.lineWrapping = editorLineWrappingInput.checked;
    updateEditorPreferences();
  });
  editorIndentWidthInput?.addEventListener('change', () => {
    editorPreferences.indentWidth = Number(editorIndentWidthInput.value);
    updateEditorPreferences();
  });
  editorThemeInput?.addEventListener('change', () => {
    editorPreferences.theme = editorThemeInput.value;
    updateEditorPreferences();
  });
  resetEditorPreferencesButton?.addEventListener('click', () => {
    editorPreferences = { ...defaultEditorPreferences };
    updateEditorPreferences();
    if (editorPreferencesStatus) {
      editorPreferencesStatus.textContent = '初期設定に戻しました。';
    }
  });

  applyEditorPreferences();

  function getEditorValue() {
    return codeMirrorEditor ? codeMirrorEditor.getValue() : (codeEditor?.value || '');
  }

  function initializeLogEditor(textarea) {
    if (!textarea || typeof CodeMirror === 'undefined') {
      return null;
    }

    const editor = CodeMirror.fromTextArea(textarea, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      viewportMargin: Infinity,
      indentUnit: 4,
      tabSize: 4
    });

    codeLogEditors.push(editor);
    return editor;
  }

  function toggleLogCode(button, panel, editor) {
    if (!button || !panel) {
      return;
    }

    const isExpanded = button.getAttribute('aria-expanded') === 'true';
    const nextExpanded = !isExpanded;

    button.setAttribute('aria-expanded', String(nextExpanded));
    button.textContent = nextExpanded ? 'コードを隠す' : 'コードを表示';
    panel.hidden = !nextExpanded;

    if (nextExpanded && editor) {
      editor.refresh();
    }
  }

  function initializeLogItem(item) {
    if (!item) {
      return;
    }

    const textarea = item.querySelector('.log-code-source');
    const toggleButton = item.querySelector('.log-code-toggle');
    const codePanel = item.querySelector('.log-code-panel');
    const editor = initializeLogEditor(textarea);

    if (toggleButton && codePanel) {
      toggleButton.addEventListener('click', () => {
        toggleLogCode(toggleButton, codePanel, editor);
      });
    }
  }

  document.querySelectorAll('.code-log-item').forEach((item) => {
    initializeLogItem(item);
  });

  function syncCollapseToggleButton(button, isExpanded) {
    if (!button) {
      return;
    }

    button.textContent = isExpanded ? '折りたたむ' : '表示する';
  }

  if (toggleLogButton && codeLogContent) {
    syncCollapseToggleButton(toggleLogButton, toggleLogButton.getAttribute('aria-expanded') === 'true');
    codeLogContent.addEventListener('shown.bs.collapse', () => {
      syncCollapseToggleButton(toggleLogButton, true);
    });
    codeLogContent.addEventListener('hidden.bs.collapse', () => {
      syncCollapseToggleButton(toggleLogButton, false);
    });
  }

  renderTaskPanel();

  document.querySelectorAll('.io-case-source').forEach((textarea) => {
    if (typeof CodeMirror === 'undefined') {
      return;
    }

    const editor = CodeMirror.fromTextArea(textarea, {
      mode: 'shell',
      lineNumbers: false,
      lineWrapping: true,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      viewportMargin: Infinity
    });

    ioExampleEditors.push(editor);
  });

  function initializeHintEditors() {
    if (hintEditorsInitialized || typeof CodeMirror === 'undefined') {
      return;
    }

    document.querySelectorAll('.hint-code-source').forEach((textarea) => {
      const editor = CodeMirror.fromTextArea(textarea, {
        mode: 'python',
        lineNumbers: false,
        lineWrapping: true,
        readOnly: true,
        cursorBlinkRate: -1,
        theme: 'material-darker',
        indentUnit: 4,
        tabSize: 4
      });

      hintEditors.push(editor);
    });

    hintEditorsInitialized = true;
    applyEditorPreferences();
  }

  function showInfoPanel(targetId) {
    infoTabs.forEach((tab) => {
      const selected = tab.getAttribute('data-panel-target') === targetId;
      tab.classList.toggle('active', selected);
      tab.setAttribute('aria-selected', String(selected));
    });
    document.querySelectorAll('.info-panel').forEach((panel) => {
      panel.classList.toggle('is-active', panel.id === targetId);
    });

    if (targetId === 'hintPanel') {
      initializeHintEditors();
      hintEditors.forEach((editor) => editor.refresh());
    }
  }

  function appendTerminalText(text, className = '') {
    const output = document.createElement('span');
    if (className) {
      output.className = className;
    }
    output.textContent = text;
    terminalOutput.append(output);
    terminalOutput.scrollTop = terminalOutput.scrollHeight;
  }

  function finishMockExecution(status) {
    const succeeded = status === 'succeeded';
    runResultStatus.textContent = succeeded ? '実行完了' : '実行エラー';
    runResultStatus.className = succeeded ? 'badge text-bg-success' : 'badge text-bg-danger';
    runResultTime.textContent = `最終実行: ${nowTimeLabel()}`;
    terminalInput.disabled = true;
    terminalSendButton.disabled = true;
    terminalCancelButton.hidden = true;
    runButton.disabled = false;
    editorMessage.textContent = succeeded ? '実行が完了しました。' : '実行に失敗しました。';
    prependLog('実行', succeeded ? '実行結果を更新しました。' : '実行時にエラーが発生しました。');
  }

  function buildDownloadFileName() {
    const titleText = document.querySelector('#editorLayout')?.dataset.taskTitle || '';
    const normalized = titleText
      .replace(/[\\/:*?"<>|]/g, '_')
      .replace(/\s+/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_+|_+$/g, '');

    return (normalized || 'editor_code') + '.py';
  }

  function downloadCurrentCode() {
    const code = getEditorValue();
    const blob = new Blob([code], { type: 'text/x-python;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = buildDownloadFileName();
    document.body.appendChild(anchor);
    try {
      anchor.click();
    } finally {
      document.body.removeChild(anchor);
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    }
  }

  function nowTimeLabel() {
    return new Date().toLocaleTimeString('ja-JP', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  function prependLog(title, text) {
    if (!codeLogList) {
      return;
    }

    const item = document.createElement('article');
    item.className = 'code-log-item';
    item.innerHTML = `
      <div class="log-time">${nowTimeLabel()}</div>
      <div class="log-body">
        <div class="log-header-row">
          <div class="log-title">${title}</div>
          <button class="btn btn-outline-secondary btn-sm log-code-toggle" type="button" aria-expanded="false">コードを表示</button>
        </div>
        <p class="log-text mb-0">${text}</p>
        <div class="log-code-panel" hidden>
          <textarea class="log-code-source" spellcheck="false">${getEditorValue()}</textarea>
        </div>
      </div>
    `;
    codeLogList.prepend(item);
    initializeLogItem(item);
  }

  function markSaved(prefix) {
    const time = nowTimeLabel();
    if (lastSavedAt) {
      lastSavedAt.textContent = time;
    }
    if (editorMessage) {
      editorMessage.textContent = `保存状態: 保存済みです（${prefix}）。コードログへ反映済みです。`;
    }
  }

  function runCode() {
    document.querySelector('.execution-result-card')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    });
    const code = getEditorValue();
    const inputCalls = code.match(/\binput\s*\(/g) || [];
    mockInputsRemaining = inputCalls.length;
    mockInputValues = [];
    const hasError = code.includes('raise');
    terminalOutput.replaceChildren();
    appendTerminalText('$ python main.py\n');
    if (runResultStatus) {
      runResultStatus.textContent = '実行中';
      runResultStatus.className = 'badge text-bg-primary';
    }
    runButton.disabled = true;
    if (mockInputsRemaining > 0) {
      appendTerminalText('入力を待っています。\n', 'terminal-system-message');
      terminalInput.disabled = false;
      terminalSendButton.disabled = false;
      terminalCancelButton.hidden = false;
      terminalInput.focus();
      editorMessage.textContent = '実行中です。ターミナルから入力してください。';
      return;
    }
    window.setTimeout(() => {
      if (hasError) {
        appendTerminalText('Traceback (most recent call last):\n  File "main.py", line 1, in <module>\nRuntimeError: サンプルエラー\n', 'terminal-stderr');
        finishMockExecution('failed');
      } else {
        appendTerminalText('実行しました。\n');
        finishMockExecution('succeeded');
      }
    }, 250);
  }

  function sendMockInput(event) {
    event.preventDefault();
    if (mockInputsRemaining <= 0 || terminalInput.disabled) {
      return;
    }
    const value = terminalInput.value;
    appendTerminalText(`>>> ${value}\n`, 'terminal-input-echo');
    mockInputValues.push(value);
    terminalInput.value = '';
    mockInputsRemaining -= 1;
    if (mockInputsRemaining > 0) {
      appendTerminalText('次の入力を待っています。\n', 'terminal-system-message');
      return;
    }
    appendTerminalText(`入力を受け取りました: ${mockInputValues.join(' / ')}\n`);
    finishMockExecution('succeeded');
  }

  function cancelMockExecution() {
    appendTerminalText('[実行を停止しました]\n', 'terminal-system-message');
    mockInputsRemaining = 0;
    finishMockExecution('failed');
  }

  function normalizeValue(value) {
    return String(value || '').replace(/\r\n/g, '\n').trim();
  }

  function collectExpectedIoCases() {
    return Array.from(document.querySelectorAll('.io-block .io-case-card')).map((card, index) => {
      const sources = card.querySelectorAll('.io-case-source');
      const inputText = normalizeValue(sources[0]?.value || '');
      const expectedOutput = normalizeValue(sources[1]?.value || '');

      return {
        index: index + 1,
        inputText,
        expectedOutput
      };
    });
  }

  function runExpectedIoCheck() {
    const sourceCode = getEditorValue();
    const ioCases = collectExpectedIoCases();

    const emulateActualOutput = (inputText) => {
      const normalizedInput = normalizeValue(inputText);

      if (!sourceCode.includes('print(')) {
        return '(出力なし)';
      }

      if (normalizedInput === 'パー') {
        return 'あなたの勝ち';
      }
      if (normalizedInput === 'チョキ') {
        return 'あなたの負け';
      }
      if (normalizedInput === 'グー') {
        return 'あいこ';
      }

      return 'グー・チョキ・パーを入力してください';
    };

    const results = ioCases.map((ioCase) => {
      const actualOutput = emulateActualOutput(ioCase.inputText);
      const passed = normalizeValue(actualOutput) === normalizeValue(ioCase.expectedOutput);

      return {
        ...ioCase,
        actualOutput,
        passed
      };
    });

    const passedCount = results.filter((result) => result.passed).length;

    return {
      totalCount: results.length,
      passedCount,
      failedCount: results.length - passedCount,
      results
    };
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function renderExpectedIoCheck(summary) {
    if (!submitCheckSummary || !submitCheckTableBody) {
      return;
    }

    submitCheckSummary.classList.remove('is-ok', 'is-ng');

    if (!summary || summary.totalCount === 0) {
      submitCheckSummary.textContent = '未チェック';
      submitCheckTableBody.innerHTML = '<tr><td colspan="4" class="text-muted">想定入出力が設定されていません。</td></tr>';
      return;
    }

    submitCheckSummary.textContent = `${summary.passedCount}/${summary.totalCount}件一致`;
    submitCheckSummary.classList.add(summary.failedCount === 0 ? 'is-ok' : 'is-ng');

    submitCheckTableBody.innerHTML = summary.results.map((result) => {
      const expectedOutput = result.expectedOutput || '(未設定)';
      const actualOutput = result.actualOutput || '(出力なし)';
      const markClass = result.passed ? 'is-pass' : 'is-fail';
      const mark = result.passed ? '○' : '×';
      return `
        <tr class="${markClass}">
          <td>${escapeHtml(result.inputText || '(未設定)')}</td>
          <td>${escapeHtml(expectedOutput)}</td>
          <td>${escapeHtml(actualOutput)}</td>
          <td class="text-center"><span class="check-result-mark ${markClass}">${mark}</span></td>
        </tr>
      `;
    }).join('');
  }

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      markSaved('保存');
      prependLog('手動保存', '現在のコードスナップショットを保存しました。');
      pageFeedback.toast({
        title: 'エディター',
        message: 'コードを保存しました。',
        variant: 'success'
      });
    });
  }

  if (downloadButton) {
    downloadButton.addEventListener('click', () => {
      downloadCurrentCode();
      pageFeedback.toast({
        title: 'エディター',
        message: 'コードをダウンロードしました。',
        variant: 'success'
      });
    });
  }

  if (runButton) {
    runButton.addEventListener('click', runCode);
  }

  if (submitButton) {
    submitButton.addEventListener('click', () => {
      latestCheckSummary = runExpectedIoCheck();
      renderExpectedIoCheck(latestCheckSummary);

      if (submitCheckModal) {
        submitCheckModal.show();
        return;
      }

      window.location.href = '../evaluation/evaluation.html';
    });
  }

  if (confirmSubmitAfterCheck) {
    confirmSubmitAfterCheck.addEventListener('click', () => {
      const checkSummary = latestCheckSummary || runExpectedIoCheck();

      if (editorMessage) {
        editorMessage.textContent = `入出力チェックを実行しました（${checkSummary.passedCount}/${checkSummary.totalCount}件一致）。不一致があっても提出可能です。`;
      }
      prependLog('提出前チェック', `入出力チェックを実行しました（${checkSummary.passedCount}/${checkSummary.totalCount}件一致）。`);

      if (submitCheckModal) {
        submitCheckModal.hide();
      }

      window.location.href = '../evaluation/evaluation.html';
    });
  }

  infoTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const targetId = tab.getAttribute('data-panel-target');
      showInfoPanel(targetId);
    });
  });

  terminalInputForm?.addEventListener('submit', sendMockInput);
  terminalCancelButton?.addEventListener('click', cancelMockExecution);

  window.setInterval(() => {
    markSaved('自動保存');
    prependLog('自動保存', '30秒経過によりコードを自動保存しました。');
  }, 30000);
});