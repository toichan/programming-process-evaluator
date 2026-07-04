window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const feedback = window.PPEFeedback || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const codeEditor = document.querySelector('#codeEditor');
  const outputConsole = document.querySelector('#outputConsole');
  const editorMessage = document.querySelector('#editorMessage');
  const lastSavedAt = document.querySelector('#lastSavedAt');
  const codeLogList = document.querySelector('#codeLogList');
  const toggleLogButton = document.querySelector('#toggleLogButton');
  const codeLogContent = document.querySelector('#codeLogContent');
  const downloadButton = document.querySelector('#downloadButton');
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const runResultModalElement = document.querySelector('#runResultModal');
  const submitButton = document.querySelector('#submitButton');
  const submitCheckModalElement = document.querySelector('#submitCheckModal');
  const submitCheckSummary = document.querySelector('#submitCheckSummary');
  const submitCheckTableBody = document.querySelector('#submitCheckTableBody');
  const confirmSubmitAfterCheck = document.querySelector('#confirmSubmitAfterCheck');
  const infoTabs = document.querySelectorAll('[data-panel-target]');
  let codeMirrorEditor = null;
  let outputConsoleEditor = null;
  let errorConsoleEditor = null;
  const EDITOR_HEIGHT_DESKTOP = 620;
  const EDITOR_HEIGHT_MOBILE = 460;
  const codeLogEditors = [];
  const ioExampleEditors = [];
  const hintEditors = [];
  let hintEditorsInitialized = false;
  const pageFeedback = feedback.createPageFeedback({ title: 'エディター' });
  const submitCheckModal = (typeof bootstrap !== 'undefined' && submitCheckModalElement)
    ? bootstrap.Modal.getOrCreateInstance(submitCheckModalElement)
    : null;
  const runResultModal = (typeof bootstrap !== 'undefined' && runResultModalElement)
    ? bootstrap.Modal.getOrCreateInstance(runResultModalElement)
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
      lineWrapping: false,
      theme: 'material-darker',
      indentUnit: 4,
      tabSize: 4,
      viewportMargin: Infinity
    });
  }

  function syncMainEditorLayout() {
    if (!codeMirrorEditor) {
      return;
    }

    const targetHeight = window.matchMedia('(max-width: 767px)').matches
      ? EDITOR_HEIGHT_MOBILE
      : EDITOR_HEIGHT_DESKTOP;

    codeMirrorEditor.setSize(null, targetHeight);
    codeMirrorEditor.refresh();
  }

  syncMainEditorLayout();
  window.addEventListener('resize', syncMainEditorLayout);

  function createReadOnlyConsole(sourceTextarea) {
    if (!sourceTextarea || typeof CodeMirror === 'undefined') {
      return null;
    }

    return CodeMirror.fromTextArea(sourceTextarea, {
      mode: 'shell',
      lineNumbers: false,
      lineWrapping: true,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      viewportMargin: Infinity
    });
  }

  outputConsoleEditor = createReadOnlyConsole(outputConsole);
  errorConsoleEditor = createReadOnlyConsole(document.querySelector('#errorConsole'));

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

  function syncLogToggleButton(isExpanded) {
    if (!toggleLogButton) {
      return;
    }

    toggleLogButton.textContent = isExpanded ? '折りたたむ' : '表示する';
  }

  if (toggleLogButton && codeLogContent) {
    syncLogToggleButton(toggleLogButton.getAttribute('aria-expanded') === 'true');
    codeLogContent.addEventListener('shown.bs.collapse', () => {
      syncLogToggleButton(true);
    });
    codeLogContent.addEventListener('hidden.bs.collapse', () => {
      syncLogToggleButton(false);
    });
  }

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
  }

  function setConsoleValue(editor, textarea, value) {
    if (editor) {
      editor.setValue(value);
      editor.refresh();
      return;
    }

    if (textarea) {
      textarea.value = value;
    }
  }

  function buildDownloadFileName() {
    const titleText = document.querySelector('.learning-flow-task-name')?.textContent || '';
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
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(objectUrl);
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
    const hasInput = getEditorValue().includes('input(');
    const stdout = hasInput
      ? '$ python main.py\n入力待ちの処理が含まれています。\nテスト用入力: パー\n\n実行結果:\nあなたの勝ち'
      : '$ python main.py\n実行しました。\n\n標準入出力:\n(ここに結果が表示されます)';
    const stderr = hasInput
      ? 'エラーはありません。'
      : 'エラーはありません。';

    setConsoleValue(outputConsoleEditor, outputConsole, stdout);
    setConsoleValue(errorConsoleEditor, document.querySelector('#errorConsole'), stderr);

    if (editorMessage) {
      editorMessage.textContent = '実行が完了しました。実行結果モーダルを確認してください。';
    }

    prependLog('実行', '実行結果をモーダルへ表示しました。');
    if (runResultModal) {
      runResultModal.show();
    }
  }

  if (runResultModalElement) {
    runResultModalElement.addEventListener('shown.bs.modal', () => {
      if (outputConsoleEditor) {
        outputConsoleEditor.refresh();
      }

      if (errorConsoleEditor) {
        errorConsoleEditor.refresh();
      }
    });
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
      infoTabs.forEach((button) => button.classList.remove('active'));
      document.querySelectorAll('.info-panel').forEach((panel) => panel.classList.remove('is-active'));
      tab.classList.add('active');
      document.getElementById(targetId)?.classList.add('is-active');

      if (targetId === 'hintPanel') {
        initializeHintEditors();
        hintEditors.forEach((editor) => editor.refresh());
      }
    });
  });

  window.setInterval(() => {
    markSaved('自動保存');
    prependLog('自動保存', '30秒経過によりコードを自動保存しました。');
  }, 30000);
});