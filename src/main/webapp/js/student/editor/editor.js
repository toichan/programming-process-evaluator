window.addEventListener('DOMContentLoaded', () => {
  const page = document.querySelector('#studentEditorPage');
  const codeTextarea = document.querySelector('#codeEditor');
  const editorSettingsModalElement = document.querySelector('#editorSettingsModal');
  const editorFontSizeInput = document.querySelector('#editorFontSize');
  const editorFontSizeValue = document.querySelector('#editorFontSizeValue');
  const editorLineWrappingInput = document.querySelector('#editorLineWrapping');
  const editorIndentWidthInput = document.querySelector('#editorIndentWidth');
  const editorThemeInput = document.querySelector('#editorTheme');
  const editorPreferencesStatus = document.querySelector('#editorPreferencesStatus');
  const resetEditorPreferencesButton = document.querySelector('#resetEditorPreferences');
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const submitButton = document.querySelector('#submitButton');
  const confirmSubmitButton = document.querySelector('#confirmSubmitAfterCheck');
  const editorMessage = document.querySelector('#editorMessage');
  const runResultStatus = document.querySelector('#runResultStatus');
  const runResultTime = document.querySelector('#runResultTime');
  const terminalOutput = document.querySelector('#terminalOutput');
  const terminalInputForm = document.querySelector('#terminalInputForm');
  const terminalInput = document.querySelector('#terminalInput');
  const terminalSendButton = document.querySelector('#terminalSendButton');
  const terminalCancelButton = document.querySelector('#terminalCancelButton');
  const logList = document.querySelector('#codeLogList');
  const feedback = window.PPEFeedback.createPageFeedback({
    title: 'エディター',
    alertTarget: '#editorFeedback'
  });
  const submitModalElement = document.querySelector('#submitCheckModal');
  const submitModal = bootstrap.Modal.getOrCreateInstance(submitModalElement);
  const defaultEditorPreferences = {
    fontSizePx: 16,
    lineWrapping: false,
    indentWidth: 4,
    theme: 'dark'
  };
  let editorPreferences = {
    fontSizePx: Number(page.dataset.editorFontSizePx),
    lineWrapping: page.dataset.editorLineWrapping === 'true',
    indentWidth: Number(page.dataset.editorIndentWidth),
    theme: page.dataset.editorTheme
  };
  if (!Number.isInteger(editorPreferences.fontSizePx)
    || editorPreferences.fontSizePx < 10 || editorPreferences.fontSizePx > 24
    || ![2, 4].includes(editorPreferences.indentWidth)
    || !['dark', 'light', 'high_contrast'].includes(editorPreferences.theme)) {
    editorPreferences = { ...defaultEditorPreferences };
    if (editorPreferencesStatus) {
      editorPreferencesStatus.textContent = '保存済み設定を読み取れません。初期設定で表示しています。';
    }
  }
  const themeForEditor = (theme) => ({
    dark: 'material-darker',
    light: 'default',
    high_contrast: 'ppe-high-contrast'
  })[theme];
  let codeEditor = CodeMirror.fromTextArea(codeTextarea, {
    mode: 'python',
    lineNumbers: true,
    lineWrapping: editorPreferences.lineWrapping,
    theme: themeForEditor(editorPreferences.theme),
    indentUnit: editorPreferences.indentWidth,
    tabSize: editorPreferences.indentWidth,
    readOnly: page.dataset.editable !== 'true'
  });
  codeEditor.getWrapperElement().style.fontSize = `${editorPreferences.fontSizePx}px`;
  const assignmentId = page.dataset.assignmentId;
  const csrfToken = document.querySelector('#csrfToken').value;
  const preferencesUrl = page.dataset.preferencesUrl;
  let draftUpdatedAt = page.dataset.draftUpdatedAt;
  let lastSavedCode = codeEditor.getValue();
  let saveInProgress = false;
  let runInProgress = false;
  let activeSessionId = null;
  let eventCursor = 0;
  let submitInProgress = false;
  let activeCheckId = null;
  let submissionRequestKey = null;
  let preferencesSaveTimer = null;
  let preferencesSaveQueue = Promise.resolve();
  let preferencesSaveSequence = 0;

  function isDirty() {
    return codeEditor.getValue() !== lastSavedCode;
  }

  function updateEditorStatus(message) {
    if (editorMessage) {
      editorMessage.textContent = message;
    }
  }

  function showError(message) {
    feedback.clearInlineAlert();
    feedback.inlineAlert(message, 'danger');
  }

  function clearError() {
    feedback.clearInlineAlert();
  }

  async function postForm(url, values) {
    const body = new URLSearchParams();
    body.set('csrfToken', csrfToken);
    body.set('assignmentId', assignmentId);
    Object.entries(values).forEach(([key, value]) => {
      body.set(key, value == null ? '' : String(value));
    });
    const response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body
    });
    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
      throw new Error('サーバーから予期しない応答が返されました。画面を再読み込みしてください。');
    }
    const result = await response.json();
    if (!response.ok) {
      const error = new Error(result.message || '処理に失敗しました。');
      error.status = response.status;
      error.errorCode = result.errorCode;
      throw error;
    }
    return result;
  }

  function syncEditorPreferencesControls() {
    editorFontSizeInput.value = String(editorPreferences.fontSizePx);
    editorFontSizeValue.textContent = `${editorPreferences.fontSizePx}px`;
    editorLineWrappingInput.checked = editorPreferences.lineWrapping;
    editorIndentWidthInput.value = String(editorPreferences.indentWidth);
    editorThemeInput.value = editorPreferences.theme;
  }

  function applyEditorPreferences() {
    codeEditor.setOption('lineWrapping', editorPreferences.lineWrapping);
    codeEditor.setOption('indentUnit', editorPreferences.indentWidth);
    codeEditor.setOption('tabSize', editorPreferences.indentWidth);
    codeEditor.setOption('theme', themeForEditor(editorPreferences.theme));
    codeEditor.getWrapperElement().style.fontSize = `${editorPreferences.fontSizePx}px`;
    codeEditor.refresh();
  }

  async function sendEditorPreferences(sequence, preferences) {
    try {
      const body = new URLSearchParams({
        csrfToken,
        fontSizePx: String(preferences.fontSizePx),
        lineWrapping: String(preferences.lineWrapping),
        indentWidth: String(preferences.indentWidth),
        theme: preferences.theme
      });
      const response = await fetch(preferencesUrl, {
        method: 'POST',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
        body
      });
      const contentType = response.headers.get('content-type') || '';
      if (!contentType.includes('application/json')) {
        throw new Error('設定の保存時に予期しない応答が返されました。');
      }
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.message || '設定を保存できませんでした。');
      }
      if (sequence === preferencesSaveSequence && editorPreferencesStatus) {
        editorPreferencesStatus.textContent = '設定を保存しました。別の端末でも使用できます。';
      }
    } catch (error) {
      if (sequence === preferencesSaveSequence) {
        editorPreferencesStatus.textContent = '設定を保存できませんでした。';
        showError(error.message);
      }
    }
  }

  function persistEditorPreferences() {
    const sequence = ++preferencesSaveSequence;
    const preferences = { ...editorPreferences };
    const save = () => sendEditorPreferences(sequence, preferences);
    preferencesSaveQueue = preferencesSaveQueue.then(save, save);
    return preferencesSaveQueue;
  }

  function scheduleEditorPreferencesSave() {
    editorPreferencesStatus.textContent = '設定を保存しています…';
    window.clearTimeout(preferencesSaveTimer);
    preferencesSaveTimer = window.setTimeout(() => {
      preferencesSaveTimer = null;
      persistEditorPreferences();
    }, 300);
  }

  function updateEditorPreferences() {
    applyEditorPreferences();
    syncEditorPreferencesControls();
    scheduleEditorPreferencesSave();
  }

  syncEditorPreferencesControls();
  editorFontSizeInput.addEventListener('input', () => {
    editorPreferences.fontSizePx = Number(editorFontSizeInput.value);
    updateEditorPreferences();
  });
  editorLineWrappingInput.addEventListener('change', () => {
    editorPreferences.lineWrapping = editorLineWrappingInput.checked;
    updateEditorPreferences();
  });
  editorIndentWidthInput.addEventListener('change', () => {
    editorPreferences.indentWidth = Number(editorIndentWidthInput.value);
    updateEditorPreferences();
  });
  editorThemeInput.addEventListener('change', () => {
    editorPreferences.theme = editorThemeInput.value;
    updateEditorPreferences();
  });
  resetEditorPreferencesButton.addEventListener('click', () => {
    editorPreferences = { ...defaultEditorPreferences };
    applyEditorPreferences();
    syncEditorPreferencesControls();
    scheduleEditorPreferencesSave();
  });
  editorSettingsModalElement.addEventListener('hide.bs.modal', () => {
    if (preferencesSaveTimer !== null) {
      window.clearTimeout(preferencesSaveTimer);
      preferencesSaveTimer = null;
      persistEditorPreferences();
    }
  });

  function formatNow() {
    return new Date().toLocaleString('ja-JP', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  function addCodeLog(title, snapshot, status) {
    const emptyLogs = document.querySelector('#emptyCodeLogs');
    if (emptyLogs) {
      emptyLogs.remove();
    }
    const item = document.createElement('article');
    item.className = 'code-log-item';
    const time = document.createElement('div');
    time.className = 'log-time';
    time.textContent = formatNow();
    const body = document.createElement('div');
    body.className = 'log-body';
    const header = document.createElement('div');
    header.className = 'log-header-row';
    const label = document.createElement('div');
    label.className = 'log-title';
    label.textContent = title;
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'btn btn-outline-secondary btn-sm log-code-toggle';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.textContent = 'コードを表示';
    const details = document.createElement('p');
    details.className = 'log-text mb-0';
    details.textContent = status ? `実行状態: ${status}` : 'コードを保存しました。';
    const panel = document.createElement('div');
    panel.className = 'log-code-panel';
    panel.hidden = true;
    const source = document.createElement('textarea');
    source.className = 'log-code-source';
    source.spellcheck = false;
    source.value = snapshot;
    panel.append(source);
    header.append(label, toggle);
    body.append(header, details, panel);
    item.append(time, body);
    logList.prepend(item);
    initializeLogItem(item);
  }

  function initializeLogItem(item) {
    const textarea = item.querySelector('.log-code-source');
    const button = item.querySelector('.log-code-toggle');
    const panel = item.querySelector('.log-code-panel');
    const editor = CodeMirror.fromTextArea(textarea, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      indentUnit: 4,
      tabSize: 4
    });
    button.addEventListener('click', () => {
      const expanded = button.getAttribute('aria-expanded') === 'true';
      button.setAttribute('aria-expanded', String(!expanded));
      button.textContent = expanded ? 'コードを表示' : 'コードを隠す';
      panel.hidden = expanded;
      if (!expanded) {
        editor.refresh();
      }
    });
  }

  async function saveDraft(eventType) {
    if (saveInProgress || page.dataset.editable !== 'true') {
      return false;
    }
    saveInProgress = true;
    saveButton.disabled = true;
    try {
      const source = codeEditor.getValue();
      const result = await postForm(page.dataset.saveUrl, {
        code: source,
        draftUpdatedAt,
        eventType
      });
      draftUpdatedAt = result.updatedAt;
      page.dataset.draftUpdatedAt = result.updatedAt;
      lastSavedCode = source;
      updateEditorStatus(`保存状態: 保存済み（${formatNow()}）。コードログへ記録しました。`);
      clearError();
      addCodeLog(eventType === 'periodic_snapshot' ? '自動保存' : '手動保存', source, '');
      return true;
    } catch (error) {
      showError(error.message);
      if (error.errorCode === 'draft_conflict') {
        saveButton.disabled = true;
        codeEditor.setOption('readOnly', true);
      }
      return false;
    } finally {
      saveInProgress = false;
      saveButton.disabled = page.dataset.editable !== 'true';
    }
  }

  function appendTerminalText(text, className = '') {
    const line = document.createElement('span');
    if (className) {
      line.className = className;
    }
    line.textContent = text;
    terminalOutput.append(line);
    terminalOutput.scrollTop = terminalOutput.scrollHeight;
  }

  function consumeExecutionUpdate(result) {
    (result.events || []).forEach((event) => {
      if (event.id > eventCursor) {
        const prefix = event.stream === 'input' ? '>>> ' : event.stream === 'stderr' ? '[エラー] ' : '';
        const style = event.stream === 'input'
          ? 'terminal-input-echo'
          : event.stream === 'stderr' ? 'terminal-stderr' : '';
        appendTerminalText(`${prefix}${event.text}`, style);
      }
    });
    eventCursor = Math.max(eventCursor, Number(result.nextCursor) || 0);
    if ((result.standardOutputTruncated || result.standardErrorTruncated)
      && !terminalOutput.dataset.truncationShown) {
      appendTerminalText('\n[出力は上限で切り詰められました]\n', 'terminal-system-message');
      terminalOutput.dataset.truncationShown = 'true';
    }
  }

  function setExecutionStatus(status) {
    const labels = {
      running: '実行中',
      succeeded: '実行完了',
      cancelled: '実行停止',
      timed_out: '時間切れ',
      failed: '実行エラー',
      unavailable: '実行エラー'
    };
    const successful = status === 'succeeded';
    if (runResultStatus) {
      runResultStatus.textContent = labels[status] || '実行エラー';
      runResultStatus.className = successful
        ? 'badge text-bg-success'
        : status === 'running' ? 'badge text-bg-primary' : 'badge text-bg-danger';
    }
    if (runResultTime) {
      runResultTime.textContent = status === 'running'
        ? '実行中...'
        : `最終実行: ${new Date().toLocaleTimeString('ja-JP')}`;
    }
  }

  function finishExecution(result) {
    consumeExecutionUpdate(result);
    if (!terminalOutput.textContent.trim()) {
      appendTerminalText('（出力なし）\n');
    }
    setExecutionStatus(result.status);
    if (result.errorCode === 'input_timeout') {
      appendTerminalText('\n[30秒間入力がなかったため実行を終了しました]\n', 'terminal-system-message');
    } else if (result.status === 'timed_out') {
      appendTerminalText('\n[実行時間の上限に達しました]\n', 'terminal-system-message');
    } else if (result.status === 'cancelled') {
      appendTerminalText('\n[実行を停止しました]\n', 'terminal-system-message');
    }
    runInProgress = false;
    activeSessionId = null;
    terminalInput.disabled = true;
    terminalSendButton.disabled = true;
    terminalCancelButton.hidden = true;
    terminalCancelButton.disabled = false;
    terminalCancelButton.textContent = '停止';
    runButton.disabled = false;
    updateEditorStatus(result.status === 'succeeded'
      ? '実行が完了しました。'
      : `実行状態: ${result.status}。結果を確認してください。`);
    addCodeLog('コード実行', codeEditor.getValue(), result.status);
  }

  async function pollExecutionSession() {
    while (runInProgress && activeSessionId) {
      await new Promise((resolve) => window.setTimeout(resolve, 250));
      const url = new URL(page.dataset.sessionUrl, window.location.href);
      url.searchParams.set('assignmentId', assignmentId);
      url.searchParams.set('sessionId', activeSessionId);
      url.searchParams.set('after', eventCursor);
      const response = await fetch(url, {
        credentials: 'same-origin',
        cache: 'no-store'
      });
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.message || '実行状態を取得できませんでした。');
      }
      consumeExecutionUpdate(result);
      setExecutionStatus(result.status);
      if (result.status !== 'running') {
        finishExecution(result);
        return;
      }
    }
  }

  async function executeCode() {
    if (runInProgress) {
      return;
    }
    document.querySelector('.execution-result-section')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    });
    runInProgress = true;
    runButton.disabled = true;
    eventCursor = 0;
    terminalOutput.replaceChildren();
    delete terminalOutput.dataset.truncationShown;
    terminalInput.disabled = true;
    terminalSendButton.disabled = true;
    terminalCancelButton.hidden = true;
    setExecutionStatus('running');
    try {
      const result = await postForm(page.dataset.runUrl, {
        code: codeEditor.getValue()
      });
      activeSessionId = result.sessionId;
      consumeExecutionUpdate(result);
      clearError();
      if (result.status === 'running') {
        terminalInput.disabled = false;
        terminalSendButton.disabled = false;
        terminalCancelButton.hidden = false;
        terminalInput.focus();
        await pollExecutionSession();
      } else {
        finishExecution(result);
      }
    } catch (error) {
      showError(error.message);
      appendTerminalText(`${error.message}\n`, 'terminal-stderr');
      runInProgress = false;
      activeSessionId = null;
      terminalInput.disabled = true;
      terminalSendButton.disabled = true;
      terminalCancelButton.hidden = true;
      runButton.disabled = false;
      setExecutionStatus('failed');
    } finally {
      if (!runInProgress) {
        runButton.disabled = false;
      }
    }
  }

  async function sendTerminalInput(event) {
    event.preventDefault();
    if (!runInProgress || !activeSessionId || terminalInput.disabled) {
      return;
    }
    const line = terminalInput.value;
    terminalInput.disabled = true;
    terminalSendButton.disabled = true;
    try {
      await postForm(page.dataset.sessionInputUrl, {
        sessionId: activeSessionId,
        line
      });
      terminalInput.value = '';
    } catch (error) {
      showError(error.message);
    } finally {
      if (runInProgress && activeSessionId) {
        terminalInput.disabled = false;
        terminalSendButton.disabled = false;
        terminalInput.focus();
      }
    }
  }

  async function cancelExecution() {
    if (!activeSessionId || !runInProgress) {
      return;
    }
    terminalCancelButton.disabled = true;
    terminalCancelButton.textContent = '停止中';
    try {
      await postForm(page.dataset.sessionCancelUrl, { sessionId: activeSessionId });
    } catch (error) {
      showError(error.message);
      terminalCancelButton.disabled = false;
      terminalCancelButton.textContent = '停止';
    }
  }

  function appendCell(row, value) {
    const cell = document.createElement('td');
    cell.textContent = value || '（なし）';
    row.append(cell);
  }

  function renderCheckResults(result) {
    const summary = document.querySelector('#submitCheckSummary');
    const tableBody = document.querySelector('#submitCheckTableBody');
    tableBody.replaceChildren();
    summary.classList.remove('is-ok', 'is-ng');

    if (result.totalCount === 0) {
      summary.textContent = '想定入出力はありません。提出内容を確認してください。';
      const row = document.createElement('tr');
      const cell = document.createElement('td');
      cell.colSpan = 4;
      cell.className = 'text-muted';
      cell.textContent = 'この課題には想定入出力が登録されていません。';
      row.append(cell);
      tableBody.append(row);
    } else {
      summary.textContent = `${result.passedCount}/${result.totalCount} 件一致`;
      summary.classList.add(result.passedCount === result.totalCount ? 'is-ok' : 'is-ng');
      result.results.forEach((item) => {
        const row = document.createElement('tr');
        const matched = item.resultStatus === 'matched';
        row.className = matched ? 'is-pass' : 'is-fail';
        appendCell(row, item.input);
        appendCell(row, item.expectedOutput);
        appendCell(row, item.actualOutput || item.standardError || item.errorCode);
        const status = document.createElement('td');
        status.className = 'text-center';
        const badge = document.createElement('span');
        badge.className = `check-result-mark ${matched ? 'is-pass' : 'is-fail'}`;
        badge.textContent = matched ? '一致' : item.resultStatus === 'mismatched' ? '不一致' : '実行エラー';
        status.append(badge);
        row.append(status);
        tableBody.append(row);
      });
    }
    activeCheckId = result.checkId;
    submissionRequestKey = null;
    confirmSubmitButton.disabled = false;
  }

  async function prepareSubmission() {
    if (submitInProgress) {
      return;
    }
    submitInProgress = true;
    submitButton.disabled = true;
    try {
      if (isDirty() && !(await saveDraft('manual_save'))) {
        return;
      }
      const result = await postForm(page.dataset.checkUrl, {
        code: codeEditor.getValue(),
        draftUpdatedAt
      });
      renderCheckResults(result);
      clearError();
      submitModal.show();
    } catch (error) {
      showError(error.message);
      if (error.errorCode === 'draft_conflict') {
        submitButton.disabled = true;
        codeEditor.setOption('readOnly', true);
      }
    } finally {
      submitInProgress = false;
      submitButton.disabled = page.dataset.canSubmit !== 'true';
    }
  }

  async function confirmSubmission() {
    if (!activeCheckId || submitInProgress) {
      return;
    }
    submitInProgress = true;
    confirmSubmitButton.disabled = true;
    try {
      if (!submissionRequestKey) {
        submissionRequestKey = crypto.randomUUID();
      }
      await postForm(page.dataset.submitUrl, {
        checkId: activeCheckId,
        requestKey: submissionRequestKey
      });
      updateEditorStatus('提出を受け付けました。提出状態を読み込み直しています。');
      window.location.reload();
    } catch (error) {
      showError(error.message);
      if (error.errorCode === 'submission_check_expired') {
        activeCheckId = null;
        confirmSubmitButton.disabled = true;
      } else {
        confirmSubmitButton.disabled = false;
      }
    } finally {
      submitInProgress = false;
    }
  }

  async function startResubmission() {
    const button = document.querySelector('#startResubmissionButton');
    if (!button) {
      return;
    }
    button.disabled = true;
    try {
      await postForm(page.dataset.resubmissionUrl, {});
      window.location.reload();
    } catch (error) {
      showError(error.message);
      button.disabled = false;
    }
  }

  codeEditor.on('change', () => {
    if (isDirty()) {
      updateEditorStatus('保存状態: 未保存の変更があります。保存するとコードログへ記録されます。');
    } else {
      updateEditorStatus('保存状態: 保存済みです。');
    }
  });

  saveButton.addEventListener('click', () => saveDraft('manual_save'));
  runButton.addEventListener('click', executeCode);
  submitButton.addEventListener('click', prepareSubmission);
  confirmSubmitButton.addEventListener('click', confirmSubmission);
  document.querySelector('#startResubmissionButton')?.addEventListener('click', startResubmission);

  document.querySelector('#toggleLogButton')?.addEventListener('click', (event) => {
    const button = event.currentTarget;
    const next = button.getAttribute('aria-expanded') === 'true';
    button.textContent = next ? '表示する' : '折りたたむ';
  });

  document.querySelectorAll('.code-log-item').forEach(initializeLogItem);

  document.querySelectorAll('.io-case-source, .hint-code-source').forEach((textarea) => {
    CodeMirror.fromTextArea(textarea, {
      mode: textarea.classList.contains('hint-code-source') ? 'python' : 'shell',
      lineNumbers: false,
      lineWrapping: true,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      indentUnit: 4,
      tabSize: 4
    });
  });

  document.querySelectorAll('[data-panel-target]').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('[data-panel-target]').forEach((item) => item.classList.remove('active'));
      document.querySelectorAll('.info-panel').forEach((item) => item.classList.remove('is-active'));
      button.classList.add('active');
      document.getElementById(button.dataset.panelTarget)?.classList.add('is-active');
    });
  });

  terminalInputForm.addEventListener('submit', sendTerminalInput);
  terminalCancelButton.addEventListener('click', cancelExecution);

  window.setInterval(() => {
    if (page.dataset.editable === 'true' && isDirty() && !saveInProgress) {
      saveDraft('periodic_snapshot');
    }
  }, 30_000);

  window.addEventListener('beforeunload', (event) => {
    if (page.dataset.editable === 'true' && isDirty()) {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  window.addEventListener('pagehide', () => {
    if (!activeSessionId) {
      return;
    }
    const body = new URLSearchParams({
      csrfToken,
      assignmentId,
      sessionId: activeSessionId
    });
    if (!navigator.sendBeacon(page.dataset.sessionCancelUrl, body)) {
      fetch(page.dataset.sessionCancelUrl, {
        method: 'POST',
        credentials: 'same-origin',
        keepalive: true,
        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
        body
      }).catch((error) => console.warn('Unable to cancel the active Python execution.', error));
    }
  });
});
