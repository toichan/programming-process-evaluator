document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('teacherReview');
  if (!root) return;
  const filters = document.getElementById('reviewFilters');
  const feedback = window.PPEFeedback.createPageFeedback({ title: '提出課題確認', alertTarget: '#reviewAlert' });
  const previewFeedback = window.PPEFeedback.createPageFeedback({ title: '動作確認', alertTarget: '#previewAlert' });
  const modalElement = document.getElementById('submissionDetailModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const original = document.getElementById('submittedCodeView');
  const work = document.getElementById('workCodeEditor');
  let allRows = [], rows = [], index = -1, selected = null, busy = false, loading = false, downloading = false;
  let sequence = 0, returnFocus = null, originalEditor = null, workEditor = null;
  let sort = 'submitted', direction = 'desc';
  const copies = new Map();
  const terminal = document.getElementById('terminalOutput');
  const terminalInput = document.getElementById('terminalInput');
  const terminalSend = document.getElementById('terminalSendButton');
  const terminalCancel = document.getElementById('terminalCancelButton');
  let execution = null, closing = false;
  const difficulty = value => ({ beginner: '初級', intermediate: '中級', advanced: '上級' }[value] || '未設定');
  const consent = value => ({ agreed: '同意', declined: '不同意', withdrawn: '不同意（撤回）' }[value] || '未確認');
  const rate = row => row.totalCases ? row.matchedCases / row.totalCases : 0;
  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  }
  function text(id, value) { document.getElementById(id).textContent = value; }
  function params() {
    const values = new URLSearchParams(new FormData(filters));
    for (const [key, value] of [...values]) if (!value) values.delete(key);
    values.set('sort', sort); values.set('direction', direction);
    return values;
  }
  function url(extra, values = params()) {
    const address = new URL(root.dataset.endpoint, location.origin);
    for (const [key, value] of values) address.searchParams.set(key, value);
    for (const [key, value] of Object.entries(extra)) if (value != null) address.searchParams.set(key, value);
    return address;
  }
  async function request(address, options, type = 'application/json') {
    const response = await fetch(address, { credentials: 'same-origin', ...options });
    if (!response.ok || !response.headers.get('Content-Type')?.includes(type)) {
      throw new Error(response.status === 400 ? '検索条件または取得対象を確認してください。' :
        response.status === 403 ? '権限がありません。画面を再読み込みしてください。' :
        response.status === 404 ? '対象の提出を参照できません。' :
        response.status === 409 ? '実行は終了しています。結果を確認してください。' :
        response.status === 429 ? '実行セッション数の上限です。実行を停止してから再試行してください。' :
        response.status === 413 ? '取得対象は32 MiB以下、実行コードは64 KiB、標準入力は合計8 KiB以下にしてください。' :
        '読み込み・実行・取得に失敗しました。時間をおいて再試行してください。');
    }
    return response;
  }
  async function json(address, options) { return (await request(address, options)).json(); }
  function options(key, label, source) {
    const select = filters.elements[key], value = select.value;
    const choices = new Map(source.map(row => [String(row[key]), row[label]]));
    select.replaceChildren(element('option', 'すべて'));
    select.firstChild.value = '';
    for (const [id, name] of choices) {
      const option = element('option', name); option.value = id; select.append(option);
    }
    select.value = choices.has(value) ? value : '';
  }
  function classes() {
    const school = filters.elements.schoolId.value;
    options('classroomId', 'className', allRows.filter(row => !school || String(row.schoolId) === school));
  }
  const ranks = {
    difficulty: { beginner: 1, intermediate: 2, advanced: 3, none: 99 },
    consent: { declined: 1, withdrawn: 1, unconfirmed: 2, agreed: 3 }
  };
  const keys = { student: 'studentLoginId', school: 'schoolName', class: 'className', task: 'taskName', submitted: 'submittedAt' };
  function sortValue(row) {
    return sort === 'match' ? rate(row) : ranks[sort] ? ranks[sort][row[sort]] ?? 99 : row[keys[sort]];
  }
  function apply() {
    const p = params(), search = (p.get('search') || '').trim().toLowerCase();
    rows = allRows.filter(row => ['schoolId', 'classroomId', 'taskId'].every(key => !p.has(key) || p.get(key) === String(row[key]))
      && (!p.has('difficulty') || p.get('difficulty') === row.difficulty)
      && (!p.has('consent') || (p.get('consent') === 'not_agreed' ? ['declined', 'withdrawn'].includes(row.consent) : p.get('consent') === row.consent))
      && (!search || row.studentLoginId.toLowerCase().includes(search) || row.taskName.toLowerCase().includes(search)));
    rows.sort((a, b) => {
      const av = sortValue(a), bv = sortValue(b);
      return (av < bv ? -1 : av > bv ? 1 : 0) * (direction === 'asc' ? 1 : -1) || a.submissionId - b.submissionId;
    });
    render();
    history.replaceState(null, '', url({}));
  }
  function render() {
    const body = document.getElementById('submissionTableBody');
    body.replaceChildren();
    rows.forEach((row, position) => {
      const tr = element('tr'), student = element('td'), task = element('td'), level = element('td'), match = element('td'), agreement = element('td'), action = element('td');
      student.append(element('code', row.studentLoginId));
      task.append(element('span', row.taskName), element('div', `第${row.revision}版${row.historyLabel ? ` / ${row.historyLabel}` : ''}`, 'small text-muted'));
      level.append(element('span', difficulty(row.difficulty), `badge difficulty-${row.difficulty}`));
      match.append(element('span', row.totalCases ? `${row.matchedCases}/${row.totalCases}件一致` : 'チェック結果なし',
        `match-pill ${row.totalCases && row.matchedCases === row.totalCases ? 'is-pass' : 'is-partial'}`));
      agreement.append(element('span', consent(row.consent), `consent ${row.consent === 'agreed' ? 'consent-ok' : row.consent === 'unconfirmed' ? 'consent-pending' : 'consent-no'}`));
      const button = element('button', '表示', 'btn btn-sm btn-outline-primary'); button.type = 'button';
      button.addEventListener('click', () => { returnFocus = button; openDetail(position); });
      action.append(button);
      tr.append(student, element('td', row.schoolName), element('td', row.className), task, level, match, element('td', row.submittedAt), agreement, action);
      body.append(tr);
    });
    if (!rows.length) {
      const tr = element('tr'), td = element('td', '条件に一致する提出はありません。', 'text-center text-muted py-4');
      td.colSpan = 9; tr.append(td); body.append(tr);
    }
    text('visibleCount', rows.length);
    text('fullMatchCount', rows.filter(row => row.totalCases > 0 && row.matchedCases === row.totalCases).length);
    text('avgMatchRate', `${rows.length ? Math.round(rows.reduce((sum, row) => sum + rate(row), 0) / rows.length * 100) : 0}%`);
    document.querySelectorAll('#submissionTable th.sortable').forEach(header => {
      const active = header.dataset.sortKey === sort;
      header.classList.toggle('sorted-asc', active && direction === 'asc');
      header.classList.toggle('sorted-desc', active && direction === 'desc');
      header.setAttribute('aria-sort', active ? direction === 'asc' ? 'ascending' : 'descending' : 'none');
    });
    exportButtons();
  }
  function exportButtons() {
    document.getElementById('submissionZip').disabled = loading || downloading || !rows.length;
    document.getElementById('submissionCsv').disabled = loading || downloading || !rows.some(row => row.consent === 'agreed');
  }
  async function refresh(initial = false) {
    if (loading || busy) return;
    loading = true; render(); copies.clear(); feedback.clearInlineAlert();
    try {
      allRows = await json(url({ view: 'list' }, new URLSearchParams()));
      options('schoolId', 'schoolName', allRows); options('taskId', 'taskName', allRows); classes();
      if (initial) {
        const query = new URLSearchParams(location.search);
        for (const key of ['schoolId', 'taskId', 'difficulty', 'consent', 'search']) {
          filters.elements[key].value = query.get(key) || '';
        }
        classes(); filters.elements.classroomId.value = query.get('classroomId') || '';
        sort = ['student', 'school', 'class', 'task', 'difficulty', 'consent', 'submitted', 'match'].includes(query.get('sort')) ? query.get('sort') : 'submitted';
        direction = query.get('direction') === 'asc' ? 'asc' : 'desc';
      }
      apply();
      if (!initial) feedback.toast({ message: '提出一覧を更新しました。', variant: 'success' });
    } catch (error) {
      allRows = []; rows = []; selected = null; render(); feedback.inlineAlert(error.message, 'warning');
    } finally { loading = false; render(); }
  }
  function persist() {
    if (!selected) return;
    const copy = copyFor(selected);
    copy.code = workEditor ? workEditor.getValue() : work.value;
    copy.input = terminalInput.value;
  }
  function copyFor(detail) {
    const id = detail.row.submissionId;
    if (!copies.has(id)) copies.set(id, { submissionId: id, code: detail.code, input: '', segments: [],
      cursor: 0, sessionId: null, status: null, time: '', truncated: false, sending: false, stopping: false });
    return copies.get(id);
  }
  function renderExecution() {
    terminal.replaceChildren();
    if (!execution?.status) terminal.textContent = 'まだ実行していません。';
    else for (const segment of execution.segments) terminal.append(element('span', segment.text, segment.style));
    terminal.scrollTop = terminal.scrollHeight;
    const running = Boolean(execution?.sessionId);
    const labels = { running: '実行中（入力を送信できます）', succeeded: '実行完了', cancelled: '実行停止', timed_out: '時間切れ', failed: '実行エラー', unavailable: '実行エラー' };
    text('runResultStatus', labels[execution?.status] || '実行待ち');
    document.getElementById('runResultStatus').className = `badge ${!execution?.status ? 'text-bg-light border' : execution.status === 'succeeded' ? 'text-bg-success' : running ? 'text-bg-primary' : 'text-bg-danger'}`;
    text('runResultTime', execution?.time || '実行すると結果がここに表示されます。');
    terminalInput.disabled = !running || execution.sending || execution.stopping || busy;
    terminalSend.disabled = terminalInput.disabled;
    terminalCancel.hidden = !running;
    terminalCancel.disabled = !running || execution.stopping || busy;
    terminalCancel.textContent = execution?.stopping ? '停止中' : '停止';
    navigation();
  }
  function consume(copy, result) {
    for (const event of result.events || []) if (event.id > copy.cursor) {
      copy.segments.push({ text: `${event.stream === 'input' ? '>>> ' : event.stream === 'stderr' ? '[エラー] ' : ''}${event.text}`,
        style: event.stream === 'input' ? 'terminal-input-echo' : event.stream === 'stderr' ? 'terminal-stderr' : '' });
    }
    copy.cursor = Math.max(copy.cursor, Number(result.nextCursor) || 0);
    if ((result.standardOutputTruncated || result.standardErrorTruncated) && !copy.truncated) {
      copy.segments.push({ text: '\n[出力は上限で切り詰められました]\n', style: 'terminal-system-message' });
      copy.truncated = true;
    }
    copy.status = result.status;
    copy.time = result.status === 'running' ? '実行中...' : `最終実行: ${new Date().toLocaleTimeString('ja-JP', { timeZone: 'Asia/Tokyo' })}`;
    if (result.status !== 'running') {
      copy.sessionId = null;
      if (!copy.segments.length) copy.segments.push({ text: '（出力なし）\n', style: '' });
      const message = result.errorCode === 'input_timeout' ? '30秒間無通信のため実行を終了しました' :
        result.status === 'timed_out' ? '実行時間の上限に達しました' : result.status === 'cancelled' ? '実行を停止しました' : result.errorCode;
      if (message) copy.segments.push({ text: `\n[${message}]\n`, style: 'terminal-system-message' });
    }
    if (execution === copy) renderExecution();
  }
  function postExecution(action, copy, extra = {}) {
    return json(root.dataset.endpoint, { method: 'POST', body: new URLSearchParams({
      action, submissionId: copy.submissionId, sessionId: copy.sessionId || '', after: copy.cursor,
      csrfToken: root.dataset.csrfToken, ...extra
    }) });
  }
  async function pollExecution(copy) {
    while (copy.sessionId) {
      await new Promise(resolve => setTimeout(resolve, 250));
      if (!copy.sessionId || copy.stopping) continue;
      const sessionId = copy.sessionId;
      try {
        const result = await json(url({ view: 'preview-session', submissionId: copy.submissionId, sessionId, after: copy.cursor }, new URLSearchParams()));
        if (copy.sessionId !== sessionId || copy.stopping) continue;
        consume(copy, result);
      } catch (error) {
        if (copy.sessionId !== sessionId || copy.stopping) continue;
        previewFeedback.inlineAlert(error.message, 'warning');
        // Keep the handle so stopping or closing can still release a live runner.
        return;
      }
    }
  }
  async function stopExecution(copy = execution) {
    if (!copy?.sessionId || copy.stopping) return;
    copy.stopping = true; renderExecution();
    try { consume(copy, await postExecution('preview-cancel', copy)); }
    finally { copy.stopping = false; renderExecution(); }
  }
  function navigation() {
    document.getElementById('detailPrevButton').disabled = busy || execution?.stopping || index <= 0;
    document.getElementById('detailNextButton').disabled = busy || execution?.stopping || index < 0 || index >= rows.length - 1;
    document.getElementById('runWorkCodeButton').disabled = busy || !selected || Boolean(execution?.sessionId);
    document.getElementById('submissionFile').disabled = busy || downloading || !selected;
    modalElement.querySelectorAll('[data-bs-dismiss]').forEach(button => { button.disabled = busy; });
  }
  async function openDetail(position) {
    if (busy || execution?.stopping || position < 0 || position >= rows.length) return;
    if (execution?.sessionId) {
      busy = true; navigation();
      try { await stopExecution(); }
      catch (error) { previewFeedback.inlineAlert(error.message, 'warning'); return; }
      finally { busy = false; navigation(); }
    }
    persist(); selected = null; busy = true; navigation();
    const requestSequence = ++sequence;
    try {
      const detail = await json(url({ view: 'detail', submissionId: rows[position].submissionId }));
      if (requestSequence !== sequence) return;
      index = position; selected = detail;
      text('detailMeta', `${detail.row.studentLoginId} / ${detail.row.schoolName} ${detail.row.className} / ${detail.row.taskName}（${difficulty(detail.row.difficulty)}） / 提出: ${detail.row.submittedAt} / 第${detail.row.revision}版${detail.row.historyLabel ? ` / ${detail.row.historyLabel}` : ''}`);
      text('detailPagerText', `${index + 1} / ${rows.length}`);
      const copy = copyFor(detail); execution = copy;
      if (originalEditor) originalEditor.setValue(detail.code); else original.value = detail.code;
      if (workEditor) workEditor.setValue(copy?.code ?? detail.code); else work.value = copy?.code ?? detail.code;
      terminalInput.value = copy.input;
      renderExecution(); previewFeedback.clearInlineAlert();
      const summary = document.getElementById('ioMatchSummary');
      summary.textContent = detail.row.totalCases ? `${detail.row.matchedCases}/${detail.row.totalCases}件一致` : 'チェック結果なし';
      summary.className = `match-summary ${detail.row.totalCases && detail.row.matchedCases === detail.row.totalCases ? 'is-pass' : 'is-partial'}`;
      const body = document.getElementById('ioResultTableBody'); body.replaceChildren();
      detail.checks.forEach(check => {
        const tr = element('tr', null, check.status === 'matched' ? 'is-pass' : 'is-fail');
        const actual = element('td', check.actualOutput || '');
        if (check.truncated || check.errorCode) actual.append(element('div', `${check.truncated ? '出力上限で切り詰め ' : ''}${check.errorCode || ''}`, 'small'));
        tr.append(element('td', check.input || ''), element('td', check.expectedOutput || ''), actual,
          element('td', check.status === 'matched' ? '○' : check.status === 'not_run' ? '未実行' : '×', 'text-center result-mark'));
        body.append(tr);
      });
      if (!detail.checks.length) {
        const tr = element('tr'), td = element('td', '入出力チェック結果はありません。', 'text-center text-muted');
        td.colSpan = 4; tr.append(td); body.append(tr);
      }
      modal.show();
      if (modalElement.classList.contains('show')) { originalEditor?.refresh(); workEditor?.refresh(); }
    } catch (error) {
      index = -1; originalEditor?.setValue(''); workEditor?.setValue(''); original.value = ''; work.value = '';
      execution = null; renderExecution();
      ['detailMeta', 'detailPagerText', 'ioMatchSummary', 'ioResultTableBody'].forEach(id => document.getElementById(id).replaceChildren());
      (modalElement.classList.contains('show') ? previewFeedback : feedback).inlineAlert(error.message, 'warning');
    } finally { busy = false; renderExecution(); }
  }
  async function download(view, detail = false) {
    const target = detail ? previewFeedback : feedback;
    if (downloading || detail && (!selected || busy)) return;
    downloading = true;
    const buttons = ['submissionCsv', 'submissionZip', 'submissionFile'].map(id => document.getElementById(id));
    buttons.forEach(button => { button.disabled = true; });
    try {
      const response = await request(url({ view, submissionId: detail ? selected.row.submissionId : null }), null,
        view === 'csv' ? 'text/csv' : view === 'zip' ? 'application/zip' : 'text/x-python');
      const blob = await response.blob();
      const disposition = response.headers.get('Content-Disposition') || '';
      const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
      const filename = encoded ? decodeURIComponent(encoded[1]) : disposition.match(/filename="([^"]+)"/)?.[1];
      if (!filename) throw new Error('取得ファイル名を確認できません。');
      const link = element('a'); const objectUrl = URL.createObjectURL(blob);
      link.href = objectUrl; link.download = filename; document.body.append(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      target.toast({ message: 'ダウンロードを開始しました。', variant: 'success' });
    } catch (error) { target.inlineAlert(error.message, 'warning'); }
    finally { downloading = false; exportButtons(); navigation(); }
  }
  filters.addEventListener('submit', event => { event.preventDefault(); apply(); });
  filters.addEventListener('change', event => { if (event.target.name === 'schoolId') classes(); if (event.target.name !== 'search') apply(); });
  filters.elements.search.addEventListener('input', apply);
  document.querySelectorAll('#submissionTable th.sortable').forEach(header => {
    function toggle() { direction = sort === header.dataset.sortKey && direction === 'asc' ? 'desc' : 'asc'; sort = header.dataset.sortKey; apply(); }
    header.addEventListener('click', toggle);
    header.addEventListener('keydown', event => { if (['Enter', ' '].includes(event.key)) { event.preventDefault(); toggle(); } });
  });
  document.getElementById('refreshSubmissions').addEventListener('click', () => refresh());
  document.getElementById('submissionCsv').addEventListener('click', () => download('csv'));
  document.getElementById('submissionZip').addEventListener('click', () => download('zip'));
  document.getElementById('submissionFile').addEventListener('click', () => download('file', true));
  document.getElementById('detailPrevButton').addEventListener('click', () => openDetail(index - 1));
  document.getElementById('detailNextButton').addEventListener('click', () => openDetail(index + 1));
  modalElement.addEventListener('shown.bs.modal', () => { originalEditor?.refresh(); workEditor?.refresh(); });
  modalElement.addEventListener('hide.bs.modal', event => {
    if (busy) { event.preventDefault(); return; }
    if (execution?.sessionId) {
      event.preventDefault();
      if (closing || execution.stopping) return;
      closing = true; busy = true; navigation();
      stopExecution().then(() => {
        busy = false; closing = false; modal.hide();
      }).catch(error => {
        busy = false; closing = false; renderExecution();
        previewFeedback.inlineAlert(error.message, 'warning');
      });
      return;
    }
    persist();
    if (modalElement.contains(document.activeElement)) document.activeElement.blur();
  });
  modalElement.addEventListener('hidden.bs.modal', () => {
    selected = null; index = -1; ++sequence;
    originalEditor?.setValue(''); workEditor?.setValue(''); original.value = ''; work.value = '';
    execution = null; terminalInput.value = ''; renderExecution();
    ['detailMeta', 'ioResultTableBody'].forEach(id => document.getElementById(id).replaceChildren());
    if (returnFocus?.isConnected) returnFocus.focus();
    navigation();
  });
  document.getElementById('runWorkCodeButton').addEventListener('click', async () => {
    if (!selected || busy || execution?.sessionId) return;
    const code = workEditor ? workEditor.getValue() : work.value;
    previewFeedback.clearInlineAlert();
    if (new TextEncoder().encode(code).length > 65536) {
      previewFeedback.inlineAlert('コードは64 KiB以下にしてください。', 'warning'); return;
    }
    const copy = execution;
    copy.segments = []; copy.cursor = 0; copy.truncated = false; copy.status = 'running'; copy.time = '実行開始中...';
    busy = true; renderExecution();
    try {
      const result = await postExecution('preview-start', copy, { code });
      copy.sessionId = result.sessionId; consume(copy, result);
      if (copy.sessionId) pollExecution(copy);
    } catch (error) {
      copy.status = 'unavailable'; copy.time = '実行を開始できませんでした。';
      previewFeedback.inlineAlert(error.message, 'warning');
    } finally { busy = false; renderExecution(); if (copy.sessionId) terminalInput.focus(); }
  });
  document.getElementById('terminalInputForm').addEventListener('submit', async event => {
    event.preventDefault();
    const copy = execution;
    if (!copy?.sessionId || terminalInput.disabled) return;
    const line = terminalInput.value;
    if (new TextEncoder().encode(line + '\n').length > 8192) {
      previewFeedback.inlineAlert('標準入力は合計8 KiB以下にしてください。', 'warning'); return;
    }
    copy.sending = true; renderExecution();
    try { await postExecution('preview-input', copy, { line }); terminalInput.value = ''; copy.input = ''; }
    catch (error) { previewFeedback.inlineAlert(error.message, 'warning'); }
    finally { copy.sending = false; renderExecution(); if (copy.sessionId) terminalInput.focus(); }
  });
  terminalCancel.addEventListener('click', () => stopExecution().catch(error => previewFeedback.inlineAlert(error.message, 'warning')));
  window.addEventListener('pagehide', () => {
    for (const copy of copies.values()) if (copy.sessionId) {
      const body = new URLSearchParams({ action: 'preview-cancel', submissionId: copy.submissionId,
        sessionId: copy.sessionId, after: copy.cursor, csrfToken: root.dataset.csrfToken });
      fetch(root.dataset.endpoint, { method: 'POST', body, credentials: 'same-origin', keepalive: true }).catch(() => {});
    }
  });
  if (typeof CodeMirror !== 'undefined') {
    originalEditor = CodeMirror.fromTextArea(original, { mode: 'python', lineNumbers: true, readOnly: true, cursorBlinkRate: -1, theme: 'material-darker' });
    workEditor = CodeMirror.fromTextArea(work, { mode: 'python', lineNumbers: true, theme: 'material-darker', indentUnit: 4, tabSize: 4 });
    originalEditor.getWrapperElement().setAttribute('aria-label', '提出コード（読み取り専用）');
    workEditor.getWrapperElement().setAttribute('aria-label', '動作確認用コード');
  } else feedback.inlineAlert('コードエディターの読み込みに失敗したため通常の入力欄を使用します。', 'warning');
  navigation();
  refresh(true).then(() => {
    if (root.dataset.selectedSubmission) {
      const position = rows.findIndex(row => String(row.submissionId) === root.dataset.selectedSubmission);
      if (position >= 0) openDetail(position);
    }
    if (!originalEditor) feedback.inlineAlert('コードエディターの読み込みに失敗したため通常の入力欄を使用します。', 'warning');
  });
});
