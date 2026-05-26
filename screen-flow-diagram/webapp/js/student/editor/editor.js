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
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const runOutputButton = document.querySelector('#runOutputButton');
  const submitButton = document.querySelector('#submitButton');
  const infoTabs = document.querySelectorAll('[data-panel-target]');
  let codeMirrorEditor = null;
  let outputConsoleEditor = null;
  const codeLogEditors = [];
  const ioExampleEditors = [];
  const hintEditors = [];
  let hintEditorsInitialized = false;
  const pageFeedback = feedback.createPageFeedback({ title: 'エディター' });

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

  if (outputConsole && typeof CodeMirror !== 'undefined') {
    const outputTextarea = document.createElement('textarea');
    outputTextarea.id = 'outputConsoleEditor';
    outputTextarea.value = outputConsole.textContent || '';
    outputConsole.replaceWith(outputTextarea);

    outputConsoleEditor = CodeMirror.fromTextArea(outputTextarea, {
      mode: 'shell',
      lineNumbers: false,
      lineWrapping: true,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      viewportMargin: Infinity
    });
  }

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

  function setOutputValue(value) {
    if (outputConsoleEditor) {
      outputConsoleEditor.setValue(value);
      outputConsoleEditor.refresh();
      return;
    }

    if (outputConsole) {
      outputConsole.textContent = value;
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
      editorMessage.textContent = `${prefix}しました。コードログへ記録しています。`;
    }
  }

  function runCode() {
    const hasInput = getEditorValue().includes('input(');
    setOutputValue(hasInput
      ? '$ python main.py\n入力待ちの処理が含まれています。\nテスト用入力: パー\n\n実行結果:\nあなたの勝ち'
      : '$ python main.py\n実行しました。\n\n標準出力:\n(ここに結果が表示されます)');
    if (editorMessage) {
      editorMessage.textContent = '実行が完了しました。エラーがあればこの下に表示されます。';
    }
    prependLog('実行', '実行結果を出力パネルへ反映しました。');
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

  if (runButton) {
    runButton.addEventListener('click', runCode);
  }

  if (runOutputButton) {
    runOutputButton.addEventListener('click', runCode);
  }

  if (submitButton) {
    submitButton.addEventListener('click', async () => {
      const confirmed = await pageFeedback.confirm({
        title: '課題を提出しますか？',
        message: '次のデータを送信します。',
        detailTitle: '',
        details: [
          '現在入力しているコード',
          '提出日時'
        ],
        confirmLabel: '提出する',
        cancelLabel: '戻る',
        variant: 'warning'
      });

      if (!confirmed) {
        return;
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