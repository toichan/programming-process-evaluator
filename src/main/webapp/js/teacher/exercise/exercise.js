document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('teacherExerciseReview');
  if (!root) return;
  const form = document.getElementById('exerciseFilters');
  const feedback = window.PPEFeedback.createPageFeedback({ title: '授業演習コード確認', alertTarget: '#exerciseAlert' });
  const detailFeedback = window.PPEFeedback.createPageFeedback({ title: '授業演習詳細', alertTarget: '#exerciseDetailAlert' });
  const modalElement = document.getElementById('exerciseDetailModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const preview = window.PPETeacherPreview.create({ endpoint: root.dataset.endpoint, csrfToken: root.dataset.csrfToken,
    modal: modalElement });
  const source = document.getElementById('exerciseCodeViewer');
  let preferences = window.PPECodeEditor.defaults();
  const viewer = typeof CodeMirror === 'undefined' ? null : window.PPECodeEditor.create({
    textarea: source, preferences,
    options: { readOnly: true, cursorBlinkRate: -1, viewportMargin: 20 }
  });
  if (viewer) source.classList.add('has-code-mirror');
  let allRows = [], rows = [], selected = null, file = null;
  let sort = 'updatedAt', direction = 'desc', listSequence = 0, detailSequence = 0, detailAbort = null;
  let refreshBusy = false, downloadBusy = false, modalClosing = false;
  let activeRow = null;
  let selectedTreeId = null, selectionMode = false, selectionAnchor = null;
  const checkedEntries = new Set(), expandedFolders = new Set(['root']);
  const treeElement = document.getElementById('exerciseTree');
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
  async function selectFile(entry) {
    const target = entry?.type === 'FILE' && selected ? { studentId: selected.row.studentId,
      classroomId: selected.row.classroomId, entryId: entry.entryId } : null;
    if (!await preview.select(target, entry?.content || '')) return;
    selectedTreeId = entry?.entryId || null;
    file = entry?.type === 'FILE' ? entry : null;
    text('detailFileName', entry?.name || '-'); text('detailFileUpdatedAt', date(entry?.updatedAt));
    text('detailFilePath', entry?.path || '-');
    const code = file?.content || '';
    source.value = code;
    if (viewer) { viewer.setValue(code); viewer.refresh(); } else source.value = code;
    document.getElementById('downloadExerciseFile').disabled = !file || downloadBusy;
    document.getElementById('exerciseRevealFile').disabled = !file;
    renderTree();
  }
  function entries() { return selected?.scopes.flatMap(scope => scope.entries) || []; }
  function covers(parent, entry) {
    return parent.exerciseId === entry.exerciseId && (parent.entryId === entry.entryId
      || parent.type === 'FOLDER' && entry.path.startsWith(parent.path + '/'));
  }
  function selectedRoots() {
    const chosen = entries().filter(entry => checkedEntries.has(entry.entryId));
    return chosen.filter(entry => !chosen.some(parent => parent !== entry && covers(parent, entry)));
  }
  function updateSelection() {
    treeElement.classList.toggle('is-selection-mode', selectionMode);
    document.getElementById('exerciseSelectMode').hidden = selectionMode;
    document.getElementById('exerciseSelectMode').setAttribute('aria-pressed', String(selectionMode));
    document.getElementById('exerciseSelectionFinish').hidden = !selectionMode;
    document.getElementById('exerciseSelectAll').hidden = !selectionMode;
    document.getElementById('exerciseSelectionBar').hidden = !selectionMode && !checkedEntries.size;
    text('exerciseSelectionCount', `選択${selectedRoots().length}件（フォルダ配下を含む）`);
    text('exerciseSelectionStatus', checkedEntries.size ? ` — ${selectedRoots().length}件` : '');
    document.getElementById('downloadExerciseSelected').disabled = !selectedRoots().length || downloadBusy;
    document.getElementById('exerciseSelectAll').disabled = !entries().length || downloadBusy;
    document.getElementById('exerciseClearSelected').disabled = !checkedEntries.size || downloadBusy;
  }
  function setCheck(entry, checked, shift) {
    selectionMode = true;
    const visible = [...treeElement.querySelectorAll('[data-entry-selection]')]
      .filter(button => !button.closest('.is-collapsed')).map(button => Number(button.dataset.entryId));
    const first = visible.indexOf(selectionAnchor), last = visible.indexOf(entry.entryId);
    const ids = shift && first >= 0 && last >= 0
      ? visible.slice(Math.min(first, last), Math.max(first, last) + 1) : [entry.entryId];
    ids.forEach(id => checked ? checkedEntries.add(id) : checkedEntries.delete(id));
    selectionAnchor = entry.entryId;
    renderTree();
  }
  function treeIcon(folder) {
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    icon.setAttribute('class', folder ? 'lesson-tree-folder-icon' : 'lesson-tree-file-icon');
    icon.setAttribute('width', '18'); icon.setAttribute('height', '18');
    icon.setAttribute('viewBox', '0 0 16 16'); icon.setAttribute('fill', 'currentColor');
    icon.setAttribute('aria-hidden', 'true');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', folder
      ? 'M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z'
      : 'M3.75 1.5A1.75 1.75 0 0 0 2 3.25v9.5c0 .966.784 1.75 1.75 1.75h8.5A1.75 1.75 0 0 0 14 12.75V5.56a1.75 1.75 0 0 0-.513-1.237L10.677 1.513A1.75 1.75 0 0 0 9.44 1H3.75zm5.5 1.06c.133.035.255.103.354.202l2.634 2.634a.75.75 0 0 1 .202.354H9.75a.5.5 0 0 1-.5-.5V2.56z');
    icon.append(path); return icon;
  }
  function renderTree() {
    if (!selected) { updateSelection(); return; }
    const previous = document.activeElement;
    const focusKey = treeElement.contains(previous) ? previous.dataset.focusKey : null;
    treeElement.replaceChildren();
    const query = document.getElementById('exerciseSearch').value.trim().toLowerCase();
    const all = entries(), matches = all.filter(entry => entry.name.toLowerCase().includes(query));
    const visible = entry => !query || matches.some(match => covers(entry, match) || covers(match, entry));
    function toggleFolder(key) {
      if (expandedFolders.has(key)) expandedFolders.delete(key);
      else expandedFolders.add(key);
      if (query) document.getElementById('exerciseSearch').value = '';
      renderTree();
    }
    function appendEntry(entry, container, virtualKey = null, nested = []) {
      const folder = entry.type === 'FOLDER', key = virtualKey || String(entry.entryId);
      const wrapper = node('div', null, folder ? 'lesson-tree-folder' : 'lesson-tree-file');
      const row = node('div', null, folder ? 'lesson-tree-folder-header' : 'lesson-tree-entry-row');
      if (virtualKey === 'root' || !virtualKey) {
        const check = node('input', null, 'form-check-input lesson-entry-check'); check.type = 'checkbox';
        check.dataset.focusKey = `check-${key}`;
        check.disabled = downloadBusy || !all.length;
        const top = all.filter(item => !item.parentEntryId);
        check.checked = virtualKey ? top.length > 0 && top.every(item => checkedEntries.has(item.entryId)) : checkedEntries.has(entry.entryId);
        check.indeterminate = virtualKey ? !check.checked && checkedEntries.size > 0
          : !check.checked && entry.type === 'FOLDER' && all.some(item => checkedEntries.has(item.entryId) && covers(entry, item));
        check.setAttribute('aria-label', virtualKey ? '有効なトップレベル項目を全選択' : `${entry.name}を選択${folder ? '（配下を含む）' : ''}`);
        check.addEventListener('click', event => {
          if (!virtualKey) setCheck(entry, check.checked, event.shiftKey);
          else { checkedEntries.clear(); if (check.checked) top.forEach(item => checkedEntries.add(item.entryId)); renderTree(); }
        });
        row.append(check);
      }
      const title = folder ? node('div', null, 'lesson-tree-folder-title') : row;
      if (folder) row.append(title);
      const label = node('button', null, folder ? 'lesson-tree-folder-select' : 'lesson-tree-file-button');
      label.type = 'button'; label.title = entry.name; label.setAttribute('aria-label', entry.name);
      label.dataset.focusKey = `label-${key}`;
      if (!virtualKey) { label.dataset.entrySelection = 'true'; label.dataset.entryId = entry.entryId; }
      const active = virtualKey === 'root' ? selectedTreeId === null : !virtualKey && selectedTreeId === entry.entryId;
      label.setAttribute('aria-pressed', String(active));
      row.classList.toggle(folder ? 'is-selected-folder' : 'is-selected-row', active);
      label.classList.toggle('is-active', active && !folder);
      label.append(treeIcon(folder), node('span', entry.name, folder ? 'lesson-tree-folder-name' : 'lesson-tree-file-name lesson-tree-file-copy'));
      label.addEventListener('click', event => {
        if (!virtualKey && (event.ctrlKey || event.metaKey || event.shiftKey)) {
          setCheck(entry, !checkedEntries.has(entry.entryId), event.shiftKey); return;
        }
        selectFile(virtualKey ? null : entry);
        if (folder) toggleFolder(key);
      });
      if (folder) row.addEventListener('click', event => {
        if (event.target.closest('button, input')) return;
        if (event.target.closest('.lesson-tree-folder-header') !== row) return;
        toggleFolder(key);
      });
      const children = node('div', null, 'lesson-tree-folder-children');
      if (folder) {
        const open = query || expandedFolders.has(key);
        children.classList.toggle('is-collapsed', !open);
        const toggle = node('button', null, 'lesson-tree-folder-toggle'); toggle.type = 'button';
        toggle.dataset.focusKey = `toggle-${key}`; toggle.setAttribute('aria-expanded', String(!!open));
        toggle.setAttribute('aria-label', `${entry.name}の展開・折りたたみ`);
        toggle.classList.toggle('is-collapsed', !open); toggle.append(node('span', '▾', 'lesson-tree-folder-toggle-icon'));
        toggle.addEventListener('click', () => toggleFolder(key));
        title.append(toggle); title.append(label);
        nested.forEach(item => appendEntry(item.entry, children, item.key, item.children));
        wrapper.append(row, children);
      } else { row.append(label); wrapper.append(row); }
      container.append(wrapper);
    }
    function branch(scope, parent = null) {
      return scope.entries.filter(entry => (entry.parentEntryId || null) === parent && visible(entry))
        .sort((a, b) => (a.type === b.type ? 0 : a.type === 'FOLDER' ? -1 : 1)
          || (document.getElementById('exerciseSort').value === 'updated' ? compareText(b.updatedAt || '', a.updatedAt || '') : 0)
          || compareText(a.name, b.name))
        .map(entry => ({ entry, children: entry.type === 'FOLDER' ? branch(scope, entry.entryId) : [] }));
    }
    const nested = selected.scopes.flatMap(scope => selected.scopes.length > 1
      ? [{ entry: { name: scope.name, type: 'FOLDER' }, key: `scope-${scope.exerciseId}`, children: branch(scope) }]
      : branch(scope));
    appendEntry({ name: selected.row.studentLoginId, type: 'FOLDER' }, treeElement, 'root', nested);
    if (!all.length || !nested.length) treeElement.append(node('p', query ? '検索条件に一致する項目がありません。' : '保存済みのフォルダ・ファイルがありません。', 'exercise-tree-empty'));
    updateSelection();
    if (focusKey) [...treeElement.querySelectorAll('[data-focus-key]')].find(item => item.dataset.focusKey === focusKey)?.focus();
  }
  function revealFile() {
    if (!file) return;
    document.getElementById('exerciseSearch').value = '';
    expandedFolders.add('root'); expandedFolders.add(`scope-${file.exerciseId}`);
    for (const entry of entries()) if (entry.type === 'FOLDER' && covers(entry, file)) expandedFolders.add(String(entry.entryId));
    renderTree();
    [...treeElement.querySelectorAll('[data-entry-selection]')].find(button => Number(button.dataset.entryId) === file.entryId)?.scrollIntoView({ block: 'nearest' });
  }
  async function openDetail(row) {
    if (modalClosing) await new Promise(resolve => modalElement.addEventListener('hidden.bs.modal', resolve, { once: true }));
    activeRow = row; render();
    const sequence = ++detailSequence;
    detailAbort?.abort(); detailAbort = new AbortController();
    checkedEntries.clear(); expandedFolders.clear(); expandedFolders.add('root');
    selectionMode = false; selectionAnchor = null;
    document.getElementById('exerciseSearch').value = '';
    if (!await preview.select(null, '')) return;
    selected = null; file = null; await selectFile(null);
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
      const files = entries().filter(entry => entry.type === 'FILE');
      data.scopes.forEach(scope => expandedFolders.add(`scope-${scope.exerciseId}`));
      text('detailFileCountChip', `${files.length}ファイル`);
      await selectFile(files[0] || null); revealFile();
      document.getElementById('downloadExerciseZip').disabled = !entries().length || downloadBusy;
    } catch (error) {
      if (sequence !== detailSequence || error.name === 'AbortError') return;
      document.getElementById('exerciseTree').replaceChildren();
      detailFeedback.inlineAlert(error.message, 'warning');
    }
  }
  async function download(view) {
    if (downloadBusy) return;
    const detail = ['download', 'zip', 'selected'].includes(view), notice = detail ? detailFeedback : feedback;
    if (detail && (!selected || view === 'download' && !file || view === 'selected' && !selectedRoots().length)) return;
    downloadBusy = true; render();
    document.getElementById('downloadExerciseFile').disabled = true; document.getElementById('downloadExerciseZip').disabled = true;
    renderTree(); updateSelection();
    const snapshot = selected;
    try {
      const extra = detail ? { studentId: selected.row.studentId, classroomId: selected.row.classroomId,
        ...(view === 'download' ? { entryId: file.entryId } : {}),
        ...(view === 'selected' ? { entryIds: selectedRoots().map(entry => entry.entryId).join(',') } : {}) } : {};
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
      renderTree(); updateSelection();
      if (snapshot !== selected) detailFeedback.clearInlineAlert();
    }
  }
  modalElement.addEventListener('shown.bs.modal', () => viewer?.refresh());
  modalElement.addEventListener('hide.bs.modal', event => {
    if (event.defaultPrevented) return;
    modalClosing = true; ++detailSequence; detailAbort?.abort();
    if (modalElement.contains(document.activeElement) && document.activeElement instanceof HTMLElement) document.activeElement.blur();
  });
  modalElement.addEventListener('hidden.bs.modal', () => {
    modalClosing = false; selected = null; file = null; selectFile(null);
    checkedEntries.clear(); updateSelection();
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
  document.getElementById('downloadExerciseSelected').addEventListener('click', () => download('selected'));
  document.getElementById('exerciseClearSelected').addEventListener('click', () => { checkedEntries.clear(); selectionAnchor = null; renderTree(); });
  document.getElementById('exerciseSelectMode').addEventListener('click', () => { selectionMode = true; updateSelection(); });
  document.getElementById('exerciseSelectionFinish').addEventListener('click', () => {
    selectionMode = false; checkedEntries.clear(); selectionAnchor = null; renderTree();
  });
  document.getElementById('exerciseSelectAll').addEventListener('click', () => {
    checkedEntries.clear(); entries().filter(entry => !entry.parentEntryId).forEach(entry => checkedEntries.add(entry.entryId)); renderTree();
  });
  document.getElementById('exerciseSearch').addEventListener('input', renderTree);
  document.getElementById('exerciseSort').addEventListener('change', renderTree);
  document.getElementById('exerciseExpandAll').addEventListener('click', () => {
    expandedFolders.add('root'); selected?.scopes.forEach(scope => expandedFolders.add(`scope-${scope.exerciseId}`));
    entries().filter(entry => entry.type === 'FOLDER').forEach(entry => expandedFolders.add(String(entry.entryId))); renderTree();
  });
  document.getElementById('exerciseCollapseAll').addEventListener('click', () => {
    document.getElementById('exerciseSearch').value = ''; expandedFolders.clear(); renderTree();
  });
  document.getElementById('exerciseRevealFile').addEventListener('click', revealFile);
  function applySettings() {
    window.PPECodeEditor.validatePreferences(preferences);
    document.getElementById('editorFontSize').value = preferences.fontSizePx;
    text('editorFontSizeValue', `${preferences.fontSizePx}px`);
    document.getElementById('editorLineWrapping').checked = preferences.lineWrapping;
    document.getElementById('editorIndentWidth').value = preferences.indentWidth;
    document.getElementById('editorTheme').value = preferences.theme;
    if (viewer) window.PPECodeEditor.applyPreferences(viewer, preferences);
    else {
      source.style.fontSize = `${preferences.fontSizePx}px`;
      source.style.whiteSpace = preferences.lineWrapping ? 'pre-wrap' : 'pre';
      source.style.tabSize = preferences.indentWidth;
      source.dataset.theme = preferences.theme;
    }
    preview.applyPreferences(preferences);
  }
  document.getElementById('exerciseEditorSettings').addEventListener('input', () => {
    preferences = { fontSizePx: Number(document.getElementById('editorFontSize').value),
      lineWrapping: document.getElementById('editorLineWrapping').checked,
      indentWidth: Number(document.getElementById('editorIndentWidth').value),
      theme: document.getElementById('editorTheme').value };
    applySettings();
  });
  document.getElementById('resetEditorPreferences').addEventListener('click', () => {
    preferences = window.PPECodeEditor.defaults(); applySettings();
  });
  applySettings(); updateSelection();
  refresh();
});
