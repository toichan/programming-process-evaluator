window.PPETeacherPreview = {
  create({ endpoint, csrfToken, modal }) {
    const feedback = window.PPEFeedback.createPageFeedback({ title: '作業コピーの動作確認', alertTarget: '#previewAlert' });
    const source = document.getElementById('workCodeEditor');
    const editor = window.CodeMirror && window.PPECodeEditor
      ? window.PPECodeEditor.create({ textarea: source, preferences: window.PPECodeEditor.defaults() }) : null;
    const run = document.getElementById('runWorkCodeButton'), output = document.getElementById('terminalOutput');
    const input = document.getElementById('terminalInput'), send = document.getElementById('terminalSendButton');
    const cancel = document.getElementById('terminalCancelButton');
    const copies = new Map();
    let current = null, busy = false, selection = 0, closing = false, disposed = false;
    const getCode = () => editor ? editor.getValue() : source.value;
    const delay = () => new Promise(resolve => setTimeout(resolve, 250));
    function body(action, copy, extra = {}) {
      return new URLSearchParams({ action, ...copy.target, sessionId: copy.sessionId || '', after: copy.cursor,
        csrfToken, ...extra });
    }
    async function request(address, options = {}) {
      const response = await fetch(address, { credentials: 'same-origin', cache: 'no-store', ...options });
      if (!response.ok || response.redirected || !response.headers.get('Content-Type')?.includes('application/json')) {
        let message = response.status === 403 ? '閲覧権限または認証期限を確認して画面を再読み込みしてください。'
          : response.status === 404 ? '対象の保存済みコードを参照できません。'
          : response.status === 409 ? '実行は終了しています。'
          : response.status === 429 ? '実行数の上限です。他の実行を停止してください。'
          : '実行サービス・データの確認に失敗しました。再試行してください。';
        if (response.headers.get('Content-Type')?.includes('application/json')) message = (await response.json()).message || message;
        throw new Error(message);
      }
      return response.json();
    }
    const post = (action, copy, extra = {}) => request(endpoint, { method: 'POST', body: body(action, copy, extra) });
    function render() {
      output.replaceChildren();
      if (!current?.status) output.textContent = 'まだ実行していません。';
      else for (const segment of current.segments) {
        const span = document.createElement('span'); span.textContent = segment.text; span.className = segment.style; output.append(span);
      }
      output.scrollTop = output.scrollHeight;
      const running = Boolean(current?.sessionId);
      const labels = { running: '実行中（入力を送信できます）', succeeded: '実行完了', failed: '実行エラー',
        cancelled: '実行停止', timed_out: '時間切れ', unavailable: '実行エラー' };
      document.getElementById('runResultStatus').textContent = labels[current?.status] || '実行待ち';
      document.getElementById('runResultTime').textContent = current?.time || '実行すると結果がここに表示されます。';
      source.disabled = !current || busy || running;
      editor?.setOption('readOnly', !current || busy || running);
      run.disabled = !current || busy || running;
      input.disabled = !running || busy || current.sending || current.stopping;
      send.disabled = input.disabled;
      cancel.hidden = !running; cancel.disabled = !running || busy || current.stopping;
      cancel.textContent = current?.stopping ? '停止中' : '停止';
    }
    function consume(copy, result) {
      for (const event of result.events || []) if (event.id > copy.cursor) {
        copy.segments.push({ text: `${event.stream === 'input' ? '>>> ' : event.stream === 'stderr' ? '[エラー] ' : ''}${event.text}`,
          style: event.stream === 'input' ? 'terminal-input-echo' : event.stream === 'stderr' ? 'terminal-stderr' : '' });
      }
      copy.cursor = Math.max(copy.cursor, Number(result.nextCursor) || 0);
      if ((result.standardOutputTruncated || result.standardErrorTruncated) && !copy.truncated) {
        copy.segments.push({ text: '\n[出力は上限で切り詰められました]\n', style: 'terminal-system-message' }); copy.truncated = true;
      }
      copy.status = result.status; copy.time = result.status === 'running' ? '実行中...' : `最終実行: ${new Date().toLocaleTimeString('ja-JP')}`;
      if (result.status !== 'running') {
        copy.sessionId = null;
        if (!copy.segments.length) copy.segments.push({ text: '（出力なし）\n', style: '' });
        if (result.errorCode) {
          const message = result.errorCode === 'input_timeout' ? '30秒間無通信のため実行を終了しました' : result.errorCode;
          copy.segments.push({ text: `\n[${message}]\n`, style: 'terminal-system-message' });
          feedback.inlineAlert(message, 'warning');
        } else if (result.status === 'failed' || result.status === 'unavailable') {
          feedback.inlineAlert('実行に失敗しました。terminalのエラーを確認してください。', 'warning');
        }
      }
      if (copy === current) render();
    }
    async function poll(copy) {
      while (copy.sessionId && !disposed) {
        await delay();
        if (!copy.sessionId || copy.stopping) continue;
        const id = copy.sessionId;
        const url = new URL(endpoint, location.origin);
        url.search = new URLSearchParams({ view: 'preview-session', ...copy.target, sessionId: id, after: copy.cursor });
        try {
          const result = await request(url);
          if (copy.sessionId === id && !copy.stopping) consume(copy, result);
        } catch (error) {
          if (copy.sessionId !== id || copy.stopping) continue;
          feedback.inlineAlert(error.message, 'warning');
          return; // Retain the handle for an explicit stop/retry or modal cleanup.
        }
      }
    }
    async function stop(copy = current) {
      if (!copy?.sessionId) return;
      if (busy || copy.stopping) throw new Error('実行操作の完了を待ってから再試行してください。');
      copy.stopping = true; render();
      try { consume(copy, await post('preview-cancel', copy)); }
      finally { copy.stopping = false; render(); }
    }
    async function select(target, code) {
      const sequence = ++selection;
      if (busy || current?.stopping) return false;
      if (current) { current.code = getCode(); current.input = input.value; }
      try { await stop(); } catch (error) { feedback.inlineAlert(error.message, 'warning'); return false; }
      if (sequence !== selection) return false;
      const key = target ? JSON.stringify(target) : null;
      if (key && !copies.has(key)) copies.set(key, { target, code, input: '', segments: [], cursor: 0, sessionId: null, status: null });
      current = key ? copies.get(key) : null;
      source.value = current?.code || ''; editor?.setValue(source.value); editor?.refresh();
      input.value = current?.input || ''; feedback.clearInlineAlert(); render();
      return true;
    }
    run.addEventListener('click', async () => {
      if (!current || busy || current.sessionId) return;
      const copy = current; copy.code = getCode(); busy = true; render(); feedback.clearInlineAlert();
      copy.segments = []; copy.cursor = 0; copy.truncated = false; copy.status = null;
      try {
        const result = await post('preview-start', copy, { code: copy.code });
        copy.sessionId = result.sessionId; consume(copy, result);
        if (disposed && copy.sessionId) fetch(endpoint, { method: 'POST', body: body('preview-cancel', copy), keepalive: true }).catch(() => {});
        else if (copy.sessionId) poll(copy);
      } catch (error) { feedback.inlineAlert(error.message, 'warning'); }
      finally { busy = false; render(); if (copy.sessionId) input.focus(); }
    });
    async function submitInput() {
      if (!current?.sessionId || input.disabled) return;
      const copy = current; copy.sending = true; render();
      try { await post('preview-input', copy, { line: input.value }); input.value = ''; copy.input = ''; }
      catch (error) { feedback.inlineAlert(error.message, 'warning'); }
      finally { copy.sending = false; render(); if (copy.sessionId) input.focus(); }
    }
    send.addEventListener('click', submitInput);
    input.addEventListener('keydown', event => { if (event.key === 'Enter' && !event.isComposing) { event.preventDefault(); submitInput(); } });
    cancel.addEventListener('click', () => stop().catch(error => feedback.inlineAlert(error.message, 'warning')));
    modal.addEventListener('hide.bs.modal', event => {
      if (busy || current?.stopping) { event.preventDefault(); return; }
      if (current?.sessionId) {
        event.preventDefault();
        if (closing) return;
        closing = true;
        stop().then(() => bootstrap.Modal.getOrCreateInstance(modal).hide())
          .catch(error => feedback.inlineAlert(error.message, 'warning')).finally(() => { closing = false; });
      }
    });
    modal.addEventListener('shown.bs.modal', () => editor?.refresh());
    modal.addEventListener('hidden.bs.modal', () => { copies.clear(); current = null; ++selection; source.value = ''; editor?.setValue(''); input.value = ''; render(); });
    window.addEventListener('pagehide', () => {
      disposed = true;
      for (const copy of copies.values()) if (copy.sessionId) {
        fetch(endpoint, { method: 'POST', body: body('preview-cancel', copy), credentials: 'same-origin', keepalive: true }).catch(() => {});
      }
    });
    render();
    return { select, refresh: () => editor?.refresh(), applyPreferences: preferences => editor && window.PPECodeEditor.applyPreferences(editor, preferences) };
  }
};
