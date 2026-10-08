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
        response.status === 413 ? '取得対象は32 MiB以下、実行コードは64 KiB、標準入力は8 KiB以下にしてください。' :
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
    if (selected) copies.set(selected.row.submissionId, { code: workEditor ? workEditor.getValue() : work.value, input: document.getElementById('previewInput').value });
  }
  function navigation() {
    document.getElementById('detailPrevButton').disabled = busy || index <= 0;
    document.getElementById('detailNextButton').disabled = busy || index < 0 || index >= rows.length - 1;
    document.getElementById('runWorkCodeButton').disabled = busy || !selected;
    document.getElementById('submissionFile').disabled = busy || downloading || !selected;
    modalElement.querySelectorAll('[data-bs-dismiss]').forEach(button => { button.disabled = busy; });
  }
  async function openDetail(position) {
    if (busy || position < 0 || position >= rows.length) return;
    persist(); selected = null; busy = true; navigation();
    const requestSequence = ++sequence;
    try {
      const detail = await json(url({ view: 'detail', submissionId: rows[position].submissionId }));
      if (requestSequence !== sequence) return;
      index = position; selected = detail;
      text('detailMeta', `${detail.row.studentLoginId} / ${detail.row.schoolName} ${detail.row.className} / ${detail.row.taskName}（${difficulty(detail.row.difficulty)}） / 提出: ${detail.row.submittedAt} / 第${detail.row.revision}版${detail.row.historyLabel ? ` / ${detail.row.historyLabel}` : ''}`);
      text('detailPagerText', `${index + 1} / ${rows.length}`);
      const copy = copies.get(detail.row.submissionId);
      if (originalEditor) originalEditor.setValue(detail.code); else original.value = detail.code;
      if (workEditor) workEditor.setValue(copy?.code ?? detail.code); else work.value = copy?.code ?? detail.code;
      document.getElementById('previewInput').value = copy?.input ?? '';
      text('workCodeOutput', '未実行です。'); previewFeedback.clearInlineAlert();
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
      ['detailMeta', 'detailPagerText', 'ioMatchSummary', 'ioResultTableBody', 'workCodeOutput'].forEach(id => document.getElementById(id).replaceChildren());
      (modalElement.classList.contains('show') ? previewFeedback : feedback).inlineAlert(error.message, 'warning');
    } finally { busy = false; navigation(); }
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
    persist();
    if (modalElement.contains(document.activeElement)) document.activeElement.blur();
  });
  modalElement.addEventListener('hidden.bs.modal', () => {
    selected = null; index = -1; ++sequence;
    originalEditor?.setValue(''); workEditor?.setValue(''); original.value = ''; work.value = '';
    document.getElementById('previewInput').value = '';
    ['detailMeta', 'ioResultTableBody', 'workCodeOutput'].forEach(id => document.getElementById(id).replaceChildren());
    if (returnFocus?.isConnected) returnFocus.focus();
    navigation();
  });
  document.getElementById('runWorkCodeButton').addEventListener('click', async () => {
    if (!selected || busy) return;
    const code = workEditor ? workEditor.getValue() : work.value, input = document.getElementById('previewInput').value;
    previewFeedback.clearInlineAlert();
    if (new TextEncoder().encode(code).length > 65536 || new TextEncoder().encode(input).length > 8192) {
      previewFeedback.inlineAlert('コードは64 KiB、標準入力は8 KiB以下にしてください。', 'warning'); return;
    }
    busy = true; navigation(); text('workCodeOutput', '実行中…');
    try {
      const result = await json(root.dataset.endpoint, { method: 'POST', body: new URLSearchParams({
        action: 'preview', submissionId: selected.row.submissionId, code, standardInput: input, csrfToken: root.dataset.csrfToken
      }) });
      text('workCodeOutput', `${result.status} / 終了コード ${result.exitCode ?? '—'}\n標準出力${result.standardOutputTruncated ? '（上限で切り詰め）' : ''}\n${result.standardOutput}\n標準エラー${result.standardErrorTruncated ? '（上限で切り詰め）' : ''}\n${result.standardError}\n${result.errorCode || ''}`);
    } catch (error) { text('workCodeOutput', '実行結果を取得できませんでした。'); previewFeedback.inlineAlert(error.message, 'warning'); }
    finally { busy = false; navigation(); }
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
