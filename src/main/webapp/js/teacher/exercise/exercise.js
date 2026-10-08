document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('teacherExerciseReview');
  if (!root) return;
  const form = document.getElementById('exerciseFilters');
  const feedback = window.PPEFeedback.createPageFeedback({ title: '授業演習コード確認', alertTarget: '#exerciseAlert' });
  const detailFeedback = window.PPEFeedback.createPageFeedback({ title: '授業演習詳細', alertTarget: '#exerciseDetailAlert' });
  const modalElement = document.getElementById('exerciseDetailModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const source = document.getElementById('exerciseCodeViewer');
  const viewer = typeof CodeMirror === 'undefined' ? null : CodeMirror.fromTextArea(source, {
    mode: 'python', lineNumbers: true, lineWrapping: false, readOnly: true,
    cursorBlinkRate: -1, theme: 'material-darker', viewportMargin: 20
  });
  if (viewer) source.classList.add('has-code-mirror');
  let allRows = [], rows = [], selected = null, file = null;
  let sort = 'updatedAt', direction = 'desc', listSequence = 0, detailSequence = 0, detailAbort = null;
  let refreshBusy = false, downloadBusy = false, modalClosing = false;
  let activeRow = null;
  const compareText = (a, b) => a < b ? -1 : a > b ? 1 : 0;
  const consentLabel = value => ({ agreed: '同意', declined: '不同意', withdrawn: '不同意', unconfirmed: '未確認' }[value] || '未確認');
  const date = value => value ? value.replace('T', ' ').replace(/-/g, '/').replace(/\.\d+$/, '') : '-';
  const text = (id, value) => { document.getElementById(id).textContent = value; };
  function node(tag, value, className) {
    const element = document.createElement(tag);
    if (value != null) element.textContent = String(value);
    if (className) element.className = className;
    return element;
  }
  function params() {
    const result = new URLSearchParams(new FormData(form));
    result.set('sort', sort); result.set('direction', direction);
    return result;
  }
  function url(view, extra = {}, filters = params()) {
    const result = new URL(root.dataset.endpoint, location.origin);
    result.search = filters.toString();
    result.searchParams.set('view', view);
    for (const [key, value] of Object.entries(extra)) result.searchParams.set(key, value);
    return result;
  }
  async function response(address, options = {}) {
    const result = await fetch(address, { credentials: 'same-origin', ...options });
    if (!result.ok || result.redirected) {
      let message = '取得に失敗しました。ログイン状態・権限を確認して再試行してください。';
      if (result.headers.get('Content-Type')?.includes('application/json')) message = (await result.json()).message || message;
      throw new Error(message);
    }
    return result;
  }
  async function json(address, options) {
    const result = await response(address, options);
    if (!result.headers.get('Content-Type')?.includes('application/json')) throw new Error('ログインし直してください。');
    return result.json();
  }
  function populate(select, entries) {
    const previous = select.value;
    select.replaceChildren(new Option('すべて', ''));
    for (const [id, name] of entries) select.add(new Option(name, id));
    if ([...select.options].some(option => option.value === previous)) select.value = previous;
  }
  function populateClasses() {
    const school = form.elements.schoolId.value;
    populate(form.elements.classroomId, new Map(allRows.filter(row => !school || String(row.schoolId) === school)
      .map(row => [String(row.classroomId), row.className])));
  }
  function filteredRows() {
    const values = Object.fromEntries(params()), order = { declined: 1, withdrawn: 1, unconfirmed: 2, agreed: 3 };
    const result = allRows.filter(row => (!values.schoolId || String(row.schoolId) === values.schoolId)
      && (!values.classroomId || String(row.classroomId) === values.classroomId)
      && (!values.consent || (values.consent === 'not_agreed' ? ['declined', 'withdrawn'].includes(row.consent) : row.consent === values.consent))
      && row.studentLoginId.toLowerCase().includes(values.search.trim().toLowerCase()));
    const key = row => ({ studentId: row.studentLoginId, school: row.schoolName, className: row.className,
      fileCount: row.fileCount, updatedAt: row.updatedAt, consent: order[row.consent] || 2 }[sort]);
    return result.sort((a, b) => {
      const first = key(a), second = key(b);
      const compared = typeof first === 'number' ? first - second : compareText(first, second);
      return compared * (direction === 'asc' ? 1 : -1) || compareText(a.studentLoginId, b.studentLoginId) || a.classroomId - b.classroomId;
    });
  }
  function render() {
    rows = filteredRows();
    const body = document.getElementById('exerciseTableBody');
    body.replaceChildren();
    for (const row of rows) {
      const tr = node('tr', null, 'exercise-row');
      tr.classList.toggle('is-active-row', row.studentId === activeRow?.studentId && row.classroomId === activeRow?.classroomId);
      const id = node('td'); id.append(node('code', row.studentLoginId)); tr.append(id);
      for (const value of [row.schoolName, row.className, row.fileCount, date(row.updatedAt)]) tr.append(node('td', value));
      const consent = node('td'); consent.append(node('span', consentLabel(row.consent),
        `consent consent-${row.consent === 'agreed' ? 'ok' : ['declined', 'withdrawn'].includes(row.consent) ? 'no' : 'pending'}`)); tr.append(consent);
      const detail = node('td'), button = node('button', '表示', 'btn btn-sm btn-outline-primary');
      button.dataset.exerciseStudent = row.studentId; button.dataset.exerciseClassroom = row.classroomId;
      button.type = 'button'; button.addEventListener('click', () => openDetail(row));
      detail.append(button); tr.append(detail); body.append(tr);
    }
    if (!rows.length) { const tr = node('tr'), td = node('td', '表示対象の授業演習データがありません。'); td.colSpan = 7; tr.append(td); body.append(tr); }
    text('visibleCount', rows.length);
    text('visibleFileCount', rows.reduce((sum, row) => sum + row.fileCount, 0));
    text('latestUpdatedAt', date(rows.reduce((latest, row) => row.updatedAt > latest ? row.updatedAt : latest, '')));
    for (const header of document.querySelectorAll('[data-sort-key]')) {
      const active = header.dataset.sortKey === sort;
      header.classList.toggle('sorted-asc', active && direction === 'asc');
      header.classList.toggle('sorted-desc', active && direction === 'desc');
      header.setAttribute('aria-sort', active ? direction === 'asc' ? 'ascending' : 'descending' : 'none');
    }
    document.getElementById('bulkExercise').disabled = !rows.some(row => row.entryCount) || refreshBusy || downloadBusy;
    document.getElementById('csvExercise').disabled = !rows.length || refreshBusy || downloadBusy;
  }
  async function refresh(notify = false) {
    const sequence = ++listSequence;
    refreshBusy = true; render();
    document.getElementById('refreshExercise').disabled = true;
    try {
      const data = await json(url('list', {}, new URLSearchParams()));
      if (sequence !== listSequence) return;
      allRows = data;
      populate(form.elements.schoolId, new Map(allRows.map(row => [String(row.schoolId), row.schoolName])));
      populateClasses();
      feedback.clearInlineAlert();
      if (notify) feedback.toast({ message: '一覧を更新しました。', variant: 'success' });
    } catch (error) {
      allRows = []; feedback.inlineAlert(error.message, 'warning');
    } finally {
      if (sequence === listSequence) { refreshBusy = false; document.getElementById('refreshExercise').disabled = false; render(); }
    }
  }
  function selectFile(entry, button) {
    file = entry;
    for (const item of document.querySelectorAll('.exercise-tree-file')) {
      const active = item === button; item.classList.toggle('is-selected', active); item.setAttribute('aria-pressed', String(active));
    }
    text('detailFileName', entry?.name || '-'); text('detailFileUpdatedAt', date(entry?.updatedAt));
    text('detailFilePath', entry?.path || '-');
    const code = entry?.content || '';
    if (viewer) { viewer.setValue(code); viewer.refresh(); } else source.value = code;
    document.getElementById('downloadExerciseFile').disabled = !entry || downloadBusy;
  }
  function folder(name, children) {
    const details = node('details', null, 'exercise-tree-folder'); details.open = true;
    const summary = node('summary', null, 'exercise-tree-folder-summary');
    summary.append(node('span', '📁', 'exercise-tree-folder-icon'), node('span', name, 'exercise-tree-folder-name'),
      node('span', `${children.length} 件`, 'exercise-tree-entry-meta'));
    const content = node('div', null, 'exercise-tree-folder-children'); content.append(...children); details.append(summary, content); return details;
  }
  function tree(scope) {
    const nodes = new Map(), children = new Map();
    for (const entry of scope.entries) {
      if (entry.type === 'FILE') {
        const button = node('button', null, 'exercise-tree-file'); button.type = 'button';
        button.append(node('span', '📄', 'exercise-tree-file-icon'));
        const copy = node('span', null, 'exercise-tree-file-copy');
        copy.append(node('span', entry.name, 'exercise-tree-file-name'), node('span', entry.path, 'exercise-tree-entry-meta')); button.append(copy);
        button.addEventListener('click', () => selectFile(entry, button)); nodes.set(entry.entryId, button);
      }
      const parent = entry.parentEntryId || 0;
      if (!children.has(parent)) children.set(parent, []);
      children.get(parent).push(entry);
    }
    function branch(parent) {
      return (children.get(parent) || []).map(entry => entry.type === 'FILE' ? nodes.get(entry.entryId) : folder(entry.name, branch(entry.entryId)));
    }
    return { children: branch(0), nodes };
  }
  async function openDetail(row) {
    if (modalClosing) await new Promise(resolve => modalElement.addEventListener('hidden.bs.modal', resolve, { once: true }));
    activeRow = row; render();
    const sequence = ++detailSequence;
    detailAbort?.abort(); detailAbort = new AbortController();
    selected = null; file = null; selectFile(null);
    document.getElementById('downloadExerciseZip').disabled = true;
    document.getElementById('exerciseTree').replaceChildren(node('p', '読み込み中です。', 'exercise-tree-empty'));
    text('detailStudentId', row.studentLoginId); text('detailStudentMeta', `${row.schoolName} / ${row.className}`);
    text('detailMeta', `${row.studentLoginId} / ${row.schoolName} / ${row.className}`);
    text('detailFileCountChip', '-'); detailFeedback.clearInlineAlert();
    modal.show();
    try {
      const data = await json(url('detail', { studentId: row.studentId, classroomId: row.classroomId }), { signal: detailAbort.signal });
      if (sequence !== detailSequence) return;
      selected = data;
      const children = [], buttons = new Map(), files = [];
      for (const scope of data.scopes) {
        const result = tree(scope); for (const [id, button] of result.nodes) buttons.set(id, button);
        children.push(...(data.scopes.length > 1 ? [folder(scope.name, result.children)] : result.children));
        files.push(...scope.entries.filter(entry => entry.type === 'FILE'));
      }
      const rootFolder = folder(row.studentLoginId, children);
      document.getElementById('exerciseTree').replaceChildren(rootFolder);
      if (!children.length) rootFolder.append(node('p', '保存済みのフォルダ・ファイルがありません。', 'exercise-tree-empty'));
      text('detailFileCountChip', `${files.length}ファイル`);
      selectFile(files[0] || null, files[0] ? buttons.get(files[0].entryId) : null);
      document.getElementById('downloadExerciseZip').disabled = !children.length || downloadBusy;
    } catch (error) {
      if (sequence !== detailSequence || error.name === 'AbortError') return;
      document.getElementById('exerciseTree').replaceChildren();
      detailFeedback.inlineAlert(error.message, 'warning');
    }
  }
  async function download(view) {
    if (downloadBusy) return;
    const detail = ['download', 'zip'].includes(view), notice = detail ? detailFeedback : feedback;
    if (detail && (!selected || view === 'download' && !file)) return;
    downloadBusy = true; render();
    document.getElementById('downloadExerciseFile').disabled = true; document.getElementById('downloadExerciseZip').disabled = true;
    const snapshot = selected;
    try {
      const extra = detail ? { studentId: selected.row.studentId, classroomId: selected.row.classroomId, ...(view === 'download' ? { entryId: file.entryId } : {}) } : {};
      const result = await response(url(view, extra));
      const disposition = result.headers.get('Content-Disposition');
      if (!disposition?.startsWith('attachment;')) throw new Error('ファイル応答を取得できませんでした。');
      const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/);
      const name = encoded ? decodeURIComponent(encoded[1]) : disposition.match(/filename="([^"]+)"/)?.[1];
      if (!name) throw new Error('ファイル名を取得できませんでした。');
      const blob = await result.blob(), link = node('a');
      const objectUrl = URL.createObjectURL(blob); link.href = objectUrl; link.download = name;
      document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
      notice.toast({ message: 'ダウンロードを開始しました。', variant: 'success' });
    } catch (error) { notice.inlineAlert(error.message, 'warning'); }
    finally {
      downloadBusy = false; render();
      document.getElementById('downloadExerciseFile').disabled = !file;
      document.getElementById('downloadExerciseZip').disabled = !selected || !selected.scopes.some(scope => scope.entries.length);
      if (snapshot !== selected) detailFeedback.clearInlineAlert();
    }
  }
  modalElement.addEventListener('shown.bs.modal', () => viewer?.refresh());
  modalElement.addEventListener('hide.bs.modal', () => {
    modalClosing = true; ++detailSequence; detailAbort?.abort();
    if (modalElement.contains(document.activeElement) && document.activeElement instanceof HTMLElement) document.activeElement.blur();
  });
  modalElement.addEventListener('hidden.bs.modal', () => {
    modalClosing = false; selected = null; file = null; selectFile(null);
    const opener = [...root.querySelectorAll('[data-exercise-student]')].find(button =>
      Number(button.dataset.exerciseStudent) === activeRow?.studentId && Number(button.dataset.exerciseClassroom) === activeRow?.classroomId);
    (opener || form.elements.search).focus();
  });
  form.addEventListener('submit', event => event.preventDefault());
  form.addEventListener('input', event => { if (event.target === form.elements.search) render(); });
  form.addEventListener('change', event => {
    if (!(event.target instanceof HTMLSelectElement)) return;
    if (event.target === form.elements.schoolId) populateClasses();
    render();
  });
  for (const header of document.querySelectorAll('[data-sort-key]')) {
    function changeSort() { direction = sort === header.dataset.sortKey && direction === 'asc' ? 'desc' : 'asc'; sort = header.dataset.sortKey; render(); }
    header.addEventListener('click', changeSort);
    header.addEventListener('keydown', event => { if (['Enter', ' '].includes(event.key)) { event.preventDefault(); changeSort(); } });
  }
  document.getElementById('refreshExercise').addEventListener('click', () => refresh(true));
  document.getElementById('bulkExercise').addEventListener('click', () => download('bulk'));
  document.getElementById('csvExercise').addEventListener('click', () => download('csv'));
  document.getElementById('downloadExerciseFile').addEventListener('click', () => download('download'));
  document.getElementById('downloadExerciseZip').addEventListener('click', () => download('zip'));
  refresh();
});
