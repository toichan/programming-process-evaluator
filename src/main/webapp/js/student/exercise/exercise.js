window.addEventListener('DOMContentLoaded', () => {
  function controlIcon(action) {
    const paths = {
      folder: 'M3 7V4h6l2 3h10v13H3zM12 11v6m-3-3h6',
      file: 'M5 3h9l5 5v13H5zM14 3v5h5M12 11v6m-3-3h6',
      expand: 'm4 7 4 4 4-4m-8 6 4 4 4-4M17 4v16m-3-3 3 3 3-3',
      collapse: 'm4 11 4-4 4 4m-8 6 4-4 4 4M17 20V4m-3 3 3-3 3 3',
      reveal: 'M10 4H4v6m10 10h6v-6M4 4l6 6m10 10-6-6M9 12l3 3 3-3-3-3z',
      select: 'M4 4h16v16H4zM8 12l3 3 5-6',
      finish: 'M4 4h16v16H4zM9 9l6 6m0-6-6 6',
      download: 'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',
      move: 'M3 7V4h6l2 3h10v13H3zM7 13h10m-3-3 3 3-3 3',
      trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7',
      restore: 'M4 10a8 8 0 1 1 1 8M4 4v6h6M12 8v5l3 2',
      clear: 'M4 4h16v16H4zM8 12h8'
    };
    return `<svg class="lesson-control-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[action]}"/></svg>`;
  }
  const controlIcons = {
    newFolderButton: 'folder', newFileButton: 'file', exerciseExpandAll: 'expand',
    exerciseCollapseAll: 'collapse', exerciseRevealFile: 'reveal',
    exerciseSelectMode: 'select', exerciseSelectionFinish: 'finish', exerciseSelectAll: 'select',
    exerciseSelectResults: 'select', downloadAllButton: 'download', exerciseMoveSelected: 'move',
    exerciseTrashSelected: 'trash', exerciseClearSelected: 'clear', exerciseTrashSelectMode: 'select',
    exerciseTrashSelectionFinish: 'finish', exerciseRestoreSelected: 'restore', exerciseTrashClearSelected: 'clear'
  };
  for (const [id, action] of Object.entries(controlIcons)) {
    const button = document.getElementById(id);
    const label = document.createElement('span');
    label.className = 'lesson-control-label'; label.textContent = button.textContent;
    button.replaceChildren(label); button.insertAdjacentHTML('afterbegin', controlIcon(action));
  }
  const page = document.querySelector('#studentExercisePage');
  const feedback = window.PPEFeedback.createPageFeedback({ title: '授業演習', alertTarget: '#exerciseFeedback' });
  const createFeedback = window.PPEFeedback.createPageFeedback({ title: '項目作成', alertTarget: '#createEntryFeedback' });
  const uploadFeedback = window.PPEFeedback.createPageFeedback({ title: 'アップロード', alertTarget: '#uploadFeedback' });
  let editable = page.dataset.editable === 'true';
  let fileSelected = page.dataset.fileSelected === 'true';
  const rootName = page.dataset.rootName;
  const preferences = window.PPECodeEditor.readPreferences(page.dataset,
    (message) => feedback.inlineAlert(message, 'danger'));
  const editor = window.PPECodeEditor.create({
    textarea: document.querySelector('#codeEditor'), preferences,
    options: { readOnly: !editable || !fileSelected }
  });
  window.PPEEditorSettings.bind({
    root: document.querySelector('#editorSettingsModal'), preferences,
    preferencesUrl: page.dataset.preferencesUrl, csrfToken: document.querySelector('#csrfToken').value,
    onChange: (settings) => window.PPECodeEditor.applyPreferences(editor, settings),
    onError: (message) => feedback.inlineAlert(message, 'danger')
  });
  let version = page.dataset.version;
  let savedCode = editor.getValue();
  let busy = false;
  let leaving = false;
  let entryType = 'file';
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const downloadButton = document.querySelector('#downloadButton');
  const downloadAllButton = document.querySelector('#downloadAllButton');
  const input = document.querySelector('#terminalInput');
  const terminal = document.querySelector('#terminalOutput');
  const sendButton = document.querySelector('#terminalSendButton');
  const cancelButton = document.querySelector('#terminalCancelButton');
  let sessionId = null;
  let cursor = 0;
  let truncated = false;
  let inputSending = false;
  const modalElement = document.querySelector('#createEntryModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  let entries = [...document.querySelectorAll('#exerciseEntries > div')].map((node) => ({ ...node.dataset }));
  const index = new Map(entries.map((entry) => [entry.entryId, entry]));
  const expandedFolders = new Set(['root']);
  const expandedTrashFolders = new Set();
  const checkedEntries = new Set();
  const checkedTrash = new Set();
  const selectionModes = { normal: false, trash: false };
  const selectionAnchors = { normal: null, trash: null };
  let trashLimit = 10;
  const nameSearch = document.querySelector('#exerciseSearch');
  const trashSearch = document.querySelector('#exerciseTrashSearch');
  const sortControl = document.querySelector('#exerciseSort');
  const batchElement = document.querySelector('#exerciseBatchModal');
  const batchModal = bootstrap.Modal.getOrCreateInstance(batchElement);
  const batchFeedback = window.PPEFeedback.createPageFeedback({
    title: '選択した項目の操作', alertTarget: '#exerciseBatchFeedback'
  });
  let batchMode = null;
  let batchTargets = [];
  let refreshFailed = false;
  let confirmingCreate = false;
  const uploadElement = document.querySelector('#uploadEntryModal');
  const uploadModal = bootstrap.Modal.getOrCreateInstance(uploadElement);
  const uploadConfirm = document.querySelector('#uploadEntryConfirmButton');
  let uploadFiles = [];
  let uploadPreview = null;
  let uploadPayload = null;
  let readingUpload = false;
  let allowModalHide = false;
  let selectedTreeId = page.dataset.entryId || '';
  let selectionPending = false;
  let historyPosition = 0;
  let restoringHistory = false;
  const organizeElement = document.querySelector('#organizeEntryModal');
  const organizeModal = bootstrap.Modal.getOrCreateInstance(organizeElement);
  const organizeFeedback = window.PPEFeedback.createPageFeedback({
    title: 'ファイル・フォルダ管理', alertTarget: '#organizeEntryFeedback'
  });
  let organizeEntryId = null;
  let organizeMode = 'rename';
  let restoreDestinationRequired = false;
  const unifyElement = document.querySelector('#unifyExerciseModal');
  const unifyModal = bootstrap.Modal.getOrCreateInstance(unifyElement);
  const unifyFeedback = window.PPEFeedback.createPageFeedback({
    title: '作業場所の統合', alertTarget: '#unifyExerciseFeedback'
  });
  let unificationPreview = null;
  history.replaceState({ exercisePosition: historyPosition }, '', window.location.href);
  function isDirty() { return editor.getValue() !== savedCode; }
  function active(entry) {
    if (!entry) return false;
    for (let current = entry; current; current = index.get(current.parentId)) {
      if (current.status !== 'active' || current.trashRootEntryId) return false;
    }
    return true;
  }
  function buttons() {
    const locked = busy || selectionPending || !!sessionId;
    document.querySelector('#exerciseScope').disabled = locked || !page.dataset.exerciseId;
    document.querySelector('#previewUnificationButton').disabled = locked || refreshFailed;
    unifyElement.querySelectorAll('button').forEach((button) => {
      button.disabled = locked || (button.id === 'unifyExerciseConfirmButton' && !unificationPreview);
    });
    saveButton.disabled = locked || !editable || !fileSelected;
    runButton.disabled = locked || !editable || !fileSelected;
    downloadButton.disabled = locked || !fileSelected;
    downloadAllButton.disabled = locked || !normalTargets().length || !page.dataset.exerciseId;
    for (const id of ['exerciseMoveSelected', 'exerciseTrashSelected']) {
      document.querySelector(`#${id}`).disabled = locked || !editable || refreshFailed || !normalTargets().length;
    }
    document.querySelector('#exerciseRestoreSelected').disabled = locked || !editable || refreshFailed || !checkedTrash.size;
    document.querySelector('#exerciseRevealFile').disabled = locked || !fileSelected || refreshFailed;
    batchElement.querySelectorAll('button, input, select').forEach((control) => { control.disabled = locked; });
    document.querySelector('#exerciseBatchConfirm').disabled = locked
      || (batchMode === 'move' && document.querySelector('#exerciseBatchParent').value === '__required__');
    document.querySelectorAll('[data-explorer-check]').forEach((control) => {
      control.disabled = locked || (control.dataset.explorerCheck === 'normal' && !!selectedParent(control.value));
    });
    document.querySelector('#exerciseClearSelected').disabled = locked || !checkedEntries.size;
    document.querySelector('#exerciseTrashClearSelected').disabled = locked || !checkedTrash.size;
    document.querySelectorAll('[data-explorer-root-check]').forEach((control) => {
      control.disabled = locked || !entries.some((entry) => active(entry) && !entry.parentId);
    });
    document.querySelectorAll('[data-entry-menu]').forEach((control) => { control.disabled = locked; });
    for (const id of ['exerciseSelectMode', 'exerciseSelectionFinish', 'exerciseTrashSelectMode', 'exerciseTrashSelectionFinish']) {
      document.querySelector(`#${id}`).disabled = locked;
    }
    document.querySelectorAll('[data-mutation], #newFolderButton, #newFileButton, #createEntryButton, #uploadButton')
      .forEach((button) => { button.disabled = locked || !editable || refreshFailed; });
    document.querySelectorAll('[data-entry-selection]').forEach((button) => {
      button.disabled = locked;
    });
    if (refreshFailed) { saveButton.disabled = true; runButton.disabled = true; }
    uploadConfirm.disabled = locked || readingUpload || !editable || refreshFailed || !uploadFiles.length;
    uploadElement.querySelectorAll('input, select, #uploadFolderTree button, #uploadSelectFilesButton, #uploadSelectFolderButton')
      .forEach((control) => { control.disabled = locked || readingUpload; });
    document.querySelectorAll('#createEntryModal [data-bs-dismiss], #uploadEntryModal [data-bs-dismiss]')
      .forEach((button) => { button.disabled = busy || readingUpload; });
    organizeElement.querySelectorAll('input, button')
      .forEach((control) => { control.disabled = locked || !editable || refreshFailed; });
    input.disabled = !sessionId || inputSending;
    sendButton.disabled = !sessionId || inputSending;
    cancelButton.hidden = !sessionId;
  }
  function updateStatus() {
    document.querySelector('#exerciseSaveStatus').textContent = !fileSelected ? 'ファイル未選択'
      : isDirty() ? '未保存の変更あり' : '最終保存を表示中';
    document.querySelector('#exerciseEditorMessage').textContent = isDirty()
      ? '保存状態: 未保存の変更があります。'
      : fileSelected ? '保存状態: 保存済みです。' : 'ツリーからファイルを選択してください。';
  }
  async function mayLeave(selecting = false) {
    if (busy || (selectionPending && !selecting)) {
      feedback.inlineAlert('処理中です。完了してから移動してください。', 'warning');
      return false;
    }
    if (!isDirty()) return true;
    return feedback.confirm({
      title: '未保存の変更', message: '保存していないコードを破棄して移動しますか？',
      confirmLabel: '破棄して移動', cancelLabel: '編集を続ける',
      details: ['現在のファイルの未保存コードは失われます。']
    });
  }
  async function navigate(url) {
    if (!(await mayLeave())) return;
    leaving = true;
    window.location.assign(url);
  }
  function fileUrl(exerciseId, entryId) {
    const url = new URL(page.dataset.baseUrl, window.location.origin);
    if (exerciseId) url.searchParams.set('exerciseId', exerciseId);
    if (entryId) url.searchParams.set('entryId', entryId);
    return url;
  }
  async function post(operation, values) {
    const body = new URLSearchParams({ csrfToken: document.querySelector('#csrfToken').value, ...values });
    if (page.dataset.exerciseId) body.set('exerciseId', page.dataset.exerciseId);
    const response = await fetch(`${page.dataset.baseUrl}/${operation}`, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body
    });
    if (!(response.headers.get('content-type') || '').includes('application/json')) {
      throw new Error('ログイン状態またはサーバーの応答を確認してください。入力は残しています。');
    }
    const result = await response.json();
    if (!response.ok) {
      throw new Error(`${result.message || '処理に失敗しました。'}${response.status === 409 && result.errorCode !== 'name_conflict'
        ? ' 入力は残しています。必要なコードを控えてから再読み込みしてください。' : ''}`);
    }
    return result;
  }
  function fillParents(select) {
    select.replaceChildren(new Option(`${rootName}（ルート）`, ''));
    entries.filter((entry) => entry.type === 'folder' && active(entry))
      .forEach((entry) => select.add(new Option(`${rootName}/${entry.path}`, entry.entryId)));
    const selected = index.get(selectedTreeId);
    if (selected) select.value = selected.type === 'folder' ? selected.entryId : selected.parentId || '';
  }
  async function readTree(exerciseId, entryId = null) {
    const url = new URL(`${page.dataset.baseUrl}/tree`, window.location.origin);
    if (exerciseId) url.searchParams.set('exerciseId', exerciseId);
    if (entryId) url.searchParams.set('entryId', entryId);
    const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' });
    if (!(response.headers.get('content-type') || '').includes('application/json')) {
      throw new Error('ログイン状態またはサーバーの応答を確認してください。');
    }
    const tree = await response.json();
    if (!response.ok) throw new Error(tree.message || '最新の項目を取得できませんでした。');
    return tree;
  }
  function treeScope(tree, exerciseId) {
    const scope = tree.scopes.find((item) => String(item.exerciseId) === String(exerciseId));
    if (!scope) throw new Error('演習領域を取得できませんでした。');
    return scope;
  }
  function applyTree(tree, scope) {
    if (page.dataset.exerciseId && page.dataset.exerciseId !== String(scope.exerciseId)) {
      checkedEntries.clear(); checkedTrash.clear();
      selectionModes.normal = false; selectionModes.trash = false;
    }
    entries = tree.entries.map((entry) => ({
      ...entry, entryId: String(entry.entryId), parentId: entry.parentId == null ? '' : String(entry.parentId)
    }));
    index.clear(); entries.forEach((entry) => index.set(entry.entryId, entry));
    version = String(scope.version); page.dataset.version = version;
    page.dataset.exerciseId = String(scope.exerciseId);
    editable = scope.editable; page.dataset.editable = String(editable);
    const select = document.querySelector('#exerciseScope');
    select.replaceChildren(...tree.scopes.map((item) => new Option(item.name, item.exerciseId)));
    select.disabled = !tree.scopes.length; select.value = String(scope.exerciseId);
    page.dataset.multipleScopes = String(tree.scopes.length > 1);
    document.querySelector('#exerciseUnificationPanel').hidden = tree.scopes.length < 2;
    downloadAllButton.title = 'チェックした項目の保存済み内容を取得します';
    reconcileChecks();
  }
  function displayFile(file, execution = null) {
    page.dataset.entryId = file ? file.entryId : '';
    fileSelected = file?.type === 'file'; page.dataset.fileSelected = String(fileSelected);
    selectedTreeId = file ? file.entryId : '';
    savedCode = fileSelected ? file.content : ''; editor.setValue(savedCode);
    editor.clearHistory();
    editor.setOption('readOnly', !editable || !fileSelected);
    document.querySelector('#currentPath').textContent = file ? `Path: ${rootName}/${file.path}`
      : 'ファイルを選択してください';
    const tab = document.querySelector('.lesson-file-tab');
    tab.textContent = file ? file.name : '未選択'; fullName(tab, file ? file.name : '');
    showSavedExecution(execution);
    updateStatus();
  }
  async function selectFile(exerciseId, entryId, fromHistory = false) {
    if (busy || selectionPending || sessionId) {
      feedback.inlineAlert('処理中です。完了してから選択してください。', 'warning');
      return false;
    }
    if (String(exerciseId) === page.dataset.exerciseId && (entryId || '') === page.dataset.entryId) {
      selectedTreeId = entryId || '';
      const file = index.get(selectedTreeId);
      document.querySelector('#currentPath').textContent = file ? `Path: ${rootName}/${file.path}`
        : 'ファイルを選択してください';
      renderTree();
      return true;
    }
    selectionPending = true; buttons();
    try {
      if (!(await mayLeave(true))) return false;
      busy = true; buttons();
      editor.setOption('readOnly', true);
      const tree = await readTree(exerciseId, entryId);
      const scope = treeScope(tree, tree.exerciseId);
      const file = entryId ? tree.entries.find((entry) => String(entry.entryId) === entryId) : null;
      if (entryId && (!file || (!fromHistory && file.type !== 'file') || file.status !== 'active'
        || String(tree.selectedEntryId) !== entryId)) throw new Error('このファイルは利用できません。');
      applyTree(tree, scope);
      displayFile(file ? index.get(entryId) : null, tree.latestExecution);
      revealAncestors(entryId);
      refreshFailed = false;
      renderTree(); feedback.clearInlineAlert();
      if (!fromHistory) {
        historyPosition += 1;
        history.pushState({ exercisePosition: historyPosition }, '', fileUrl(scope.exerciseId, entryId));
      }
      return true;
    } catch (error) {
      feedback.inlineAlert(`${error.message} 切り替える前のコードと実行結果は残しています。`, 'danger');
      return false;
    } finally {
      busy = false; selectionPending = false;
      editor.setOption('readOnly', !editable || !fileSelected);
      buttons();
    }
  }
  function selectFolder(entry) {
    if (busy || selectionPending || sessionId) {
      feedback.inlineAlert('処理中です。完了してから選択してください。', 'warning');
      return;
    }
    selectedTreeId = entry.virtualRoot ? '' : entry.entryId;
    if (nameSearch.value) revealAncestors(entry.entryId);
    document.querySelector('#currentPath').textContent = `Path: ${rootName}${entry.virtualRoot ? '' : `/${entry.path}`}`;
    renderTree();
  }
  function revealAncestors(id) {
    nameSearch.value = '';
    for (let entry = index.get(id); entry; entry = index.get(entry.parentId)) {
      if (entry.type === 'folder') expandedFolders.add(entry.entryId);
    }
    expandedFolders.add('root');
  }
  async function refreshAfterMutation(result, selectFileId, message, updateLocation = false, clearEditor = false) {
    // Retain the committed version even if the subsequent read fails; never encourage resubmission.
    version = String(result.version);
    page.dataset.exerciseId = String(result.exerciseId);
    try {
      const tree = await readTree(result.exerciseId, selectFileId);
      const scope = treeScope(tree, result.exerciseId);
      if (String(scope.version) !== String(result.version)) {
        throw new Error('別の画面で演習領域が更新されました。');
      }
      applyTree(tree, scope);
      if (clearEditor) displayFile(null);
      if (selectFileId) {
        const file = index.get(selectFileId);
        if (!file || file.type !== 'file' || !active(file)) {
          throw new Error('作成したファイルを取得できませんでした。');
        }
        displayFile(file, tree.latestExecution);
      }
      if (updateLocation) {
        if (selectedTreeId && !active(index.get(selectedTreeId))) selectedTreeId = '';
        const selected = index.get(selectedTreeId);
        document.querySelector('#currentPath').textContent = selected
          ? `Path: ${rootName}/${selected.path}` : `Path: ${rootName}`;
        const file = index.get(page.dataset.entryId);
        if (file) {
          const tab = document.querySelector('.lesson-file-tab');
          tab.textContent = file.name; fullName(tab, file.name);
        }
      }
      editor.setOption('readOnly', !editable || !fileSelected);
      history.replaceState({ exercisePosition: historyPosition }, '', fileUrl(result.exerciseId, page.dataset.entryId));
      renderTree(); updateStatus();
      feedback.toast({ message, variant: 'success' });
    } catch (error) {
      refreshFailed = true; buttons();
      feedback.inlineAlert(`${message} ただし画面の更新に失敗しました。${error.message} 再送せず、編集中のコードを控えてから再読み込みしてください。`, 'warning');
    }
  }
  async function mutation(operation, values) {
    if (busy) return null;
    busy = true; buttons(); feedback.clearInlineAlert();
    try { return await post(operation, values); }
    catch (error) { feedback.inlineAlert(error.message, 'danger'); return null; }
    finally { busy = false; buttons(); }
  }
  function downloadBlob(blob, name) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url; link.download = name;
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  downloadButton.addEventListener('click', () => {
    if (busy || !fileSelected) return;
    try {
      downloadBlob(new Blob([editor.getValue()], { type: 'text/plain;charset=utf-8' }),
        index.get(page.dataset.entryId).name);
      feedback.toast({ message: '編集中のコードのダウンロードを開始しました。保存状態は変更しません。', variant: 'success' });
    } catch (error) { feedback.inlineAlert(`ダウンロードを開始できませんでした。${error.message}`, 'danger'); }
  });
  async function downloadEntries(targets) {
    if (busy || selectionPending || sessionId || !page.dataset.exerciseId || !targets.length) return;
    busy = true; buttons(); feedback.clearInlineAlert();
    try {
      const url = new URL(`${page.dataset.baseUrl}/download`, window.location.origin);
      url.searchParams.set('exerciseId', page.dataset.exerciseId);
      url.searchParams.set('entryIds', targets.map((entry) => entry.entryId).join(','));
      const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' });
      const type = response.headers.get('content-type') || '';
      if (!response.ok) {
        const result = type.includes('application/json') ? await response.json() : null;
        throw new Error(result ? result.message : 'ログイン状態またはサーバーの応答を確認してください。');
      }
      const mediaType = type.split(';', 1)[0].trim().toLowerCase();
      if (!['application/zip', 'text/plain', 'text/x-python'].includes(mediaType)) {
        throw new Error('ファイルを取得できませんでした。ログイン状態を確認してください。');
      }
      const disposition = response.headers.get('content-disposition') || '';
      const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
      const plain = disposition.match(/filename="((?:\\.|[^"])*)"/);
      if (!encoded && !plain) throw new Error('取得したファイル名を確認できませんでした。');
      const name = encoded ? decodeURIComponent(encoded[1]) : plain[1].replace(/\\(.)/g, '$1');
      downloadBlob(await response.blob(), name);
      const includesEditor = targets.some((entry) => covers(entry, page.dataset.entryId));
      feedback.toast({ message: includesEditor && isDirty()
        ? '保存済み内容を取得しました。編集中の未保存コードは含まれていません。'
        : '保存済み内容のダウンロードを開始しました。', variant: 'success' });
    } catch (error) { feedback.inlineAlert(`${error.message} 編集中のコードは残しています。`, 'danger'); }
    finally { busy = false; buttons(); }
  }
  downloadAllButton.addEventListener('click', () => downloadEntries(normalTargets()));
  function covers(parent, id) {
    const visited = new Set();
    for (let entry = index.get(id); entry && !visited.has(entry.entryId); entry = index.get(entry.parentId)) {
      if (entry.entryId === parent.entryId) return true;
      visited.add(entry.entryId);
    }
    return false;
  }
  function normalTargets() {
    const selected = entries.filter((entry) => checkedEntries.has(entry.entryId) && active(entry));
    return selected.filter((entry) => !selected.some((parent) => parent !== entry && covers(parent, entry.entryId)));
  }
  function selectedParent(id) {
    return entries.find((entry) => entry.entryId !== id && entry.type === 'folder'
      && active(entry) && checkedEntries.has(entry.entryId) && covers(entry, id));
  }
  function reconcileChecks() {
    let removed = 0;
    for (const [selected, valid] of [[checkedEntries, active], [checkedTrash, (entry) => entry?.status === 'trashed']]) {
      for (const id of selected) {
        if (!valid(index.get(id))) { selected.delete(id); removed += 1; }
      }
    }
    if (removed) feedback.inlineAlert(`${removed}件の項目が利用できなくなったため選択から外しました。`, 'warning');
  }
  function visibleChecks(group) {
    return [...document.querySelectorAll(`[data-explorer-check="${group}"]`)]
      .filter((control) => control.getClientRects().length && !selectedParent(control.value));
  }
  function updateSelectionCounts() {
    if (checkedEntries.size) selectionModes.normal = true;
    if (checkedTrash.size) selectionModes.trash = true;
    document.querySelector('#exerciseTree').classList.toggle('is-selection-mode', selectionModes.normal);
    document.querySelector('#exerciseTrash').classList.toggle('is-selection-mode', selectionModes.trash);
    document.querySelector('#exerciseSelectionTools').hidden = !selectionModes.normal;
    document.querySelector('#exerciseSelectionBar').hidden = !selectionModes.normal;
    document.querySelector('#exerciseTrashSelectionBar').hidden = !selectionModes.trash;
    document.querySelectorAll('[data-explorer-check="normal"]').forEach((control) => {
      const parent = selectedParent(control.value);
      control.checked = checkedEntries.has(control.value) || !!parent;
      control.title = parent ? `「${parent.name}」の選択に含まれます。個別に選ぶには親の選択を解除してください。` : '';
    });
    const rootCheck = document.querySelector('[data-explorer-root-check]');
    if (rootCheck) {
      const top = entries.filter((entry) => active(entry) && !entry.parentId);
      rootCheck.checked = top.length > 0 && top.every((entry) => checkedEntries.has(entry.entryId));
      rootCheck.indeterminate = !rootCheck.checked && checkedEntries.size > 0;
    }
    for (const [group, prefix] of [['normal', 'exercise'], ['trash', 'exerciseTrash']]) {
      document.querySelector(`#${prefix}SelectMode`).hidden = selectionModes[group];
      document.querySelector(`#${prefix}SelectMode`).setAttribute('aria-pressed', String(selectionModes[group]));
      document.querySelector(`#${prefix}SelectionFinish`).hidden = !selectionModes[group];
      const count = group === 'trash' ? checkedTrash.size : normalTargets().length;
      document.querySelector(`#${prefix}SelectionStatus`).textContent = count
        ? ` · 選択${count}件` : selectionModes[group] ? ' · 選択中' : '';
    }
    const states = [];
    if (nameSearch.value.trim()) states.push('検索中');
    if (sortControl.value === 'updated') states.push('更新日時順');
    document.querySelector('#exerciseDisplayStatus').textContent = states.length ? ` · ${states.join(' / ')}` : '';
    document.querySelector('#exerciseSearchStatus').hidden = !nameSearch.value.trim();
    document.querySelector('#exerciseSearchStatus').textContent = nameSearch.value.trim()
      ? `検索: ${nameSearch.value.trim()}` : '';
    const visible = new Set(visibleChecks('normal').map((control) => control.value));
    const hidden = normalTargets().filter((entry) => !visible.has(entry.entryId)).length;
    document.querySelector('#exerciseSelectionCount').textContent =
      `選択${normalTargets().length}件${hidden ? `（非表示${hidden}件）` : ''}`;
    const trashVisible = new Set(visibleChecks('trash').map((control) => control.value));
    const hiddenTrash = [...checkedTrash].filter((id) => !trashVisible.has(id)).length;
    document.querySelector('#exerciseTrashSelectionCount').textContent =
      `選択${checkedTrash.size}件${hiddenTrash ? `（非表示${hiddenTrash}件）` : ''}`;
  }
  function setCheck(group, id, selected, range = false) {
    if (group === 'normal' && selectedParent(id)) return;
    selectionModes[group] = true;
    document.querySelector(group === 'trash' ? '#exerciseTrash' : '#exerciseTree').classList.add('is-selection-mode');
    const checked = group === 'trash' ? checkedTrash : checkedEntries;
    if (range && selectionAnchors[group]) {
      const order = visibleChecks(group).map((control) => control.value);
      const from = order.indexOf(selectionAnchors[group]); const to = order.indexOf(id);
      if (from >= 0 && to >= 0) {
        order.slice(Math.min(from, to), Math.max(from, to) + 1)
          .forEach((key) => selected ? checked.add(key) : checked.delete(key));
      } else if (selected) checked.add(id); else checked.delete(id);
    } else if (selected) checked.add(id); else checked.delete(id);
    selectionAnchors[group] = id;
    document.querySelectorAll(`[data-explorer-check="${group}"]`)
      .forEach((control) => { control.checked = checked.has(control.value); });
    updateSelectionCounts(); buttons();
  }
  const foldName = (text) => text.normalize('NFKD').replace(/\p{M}/gu, '').toLocaleLowerCase('ja');
  function matchesName(entry, query) { return !query || foldName(entry.name).includes(foldName(query)); }
  function sorted(items) {
    return [...items].sort((left, right) => {
      const type = Number(right.type === 'folder') - Number(left.type === 'folder');
      if (type) return type;
      if (sortControl.value === 'updated') {
        const time = (right.updatedAt || '').localeCompare(left.updatedAt || '');
        if (time) return time;
      }
      return left.name.localeCompare(right.name, 'ja', { numeric: true })
        || Number(left.entryId) - Number(right.entryId);
    });
  }
  function fullName(target, text) {
    target.dataset.exerciseFullName = text;
    target.title = text;
  }
  function clearNameTooltips(container) {
    container.querySelectorAll('[data-exercise-full-name]').forEach((target) => {
      bootstrap.Tooltip.getInstance(target)?.dispose();
    });
  }
  new bootstrap.Tooltip(document.body, {
    selector: '[data-exercise-full-name]', container: 'body', trigger: 'hover focus',
    animation: false,
    customClass: 'exercise-name-tooltip', title() { return this.dataset.exerciseFullName; }
  });
  new bootstrap.Tooltip(document.querySelector('#uploadButton'), { container: 'body', animation: false });
  function renderTree() {
    const tree = document.querySelector('#exerciseTree');
    const trash = document.querySelector('#exerciseTrash');
    window.PPEExplorerMenus.clear(tree); window.PPEExplorerMenus.clear(trash);
    clearNameTooltips(tree); clearNameTooltips(trash);
    tree.replaceChildren(); trash.replaceChildren();
    const appendEntry = (entry, container, trashChild = false) => {
      const folder = entry.type === 'folder';
      const wrapper = document.createElement('div');
      wrapper.className = folder ? 'lesson-tree-folder' : 'lesson-tree-file';
      const row = document.createElement('div');
      row.className = folder ? 'lesson-tree-folder-header' : 'lesson-tree-entry-row';
      if (entry.virtualRoot) {
        const check = document.createElement('input');
        check.type = 'checkbox'; check.className = 'form-check-input lesson-entry-check';
        check.dataset.explorerRootCheck = 'true';
        check.setAttribute('aria-label', '有効なトップレベル項目を全選択');
        const top = entries.filter((item) => active(item) && !item.parentId);
        check.checked = top.length > 0 && top.every((item) => checkedEntries.has(item.entryId));
        check.indeterminate = !check.checked && checkedEntries.size > 0;
        check.disabled = busy || selectionPending || !!sessionId || !top.length;
        check.addEventListener('click', () => {
          checkedEntries.clear();
          if (check.checked) top.forEach((item) => checkedEntries.add(item.entryId));
          renderTree();
        });
        row.append(check);
      }
      if (!entry.virtualRoot && !trashChild) {
        const group = entry.status === 'trashed' ? 'trash' : 'normal';
        const check = document.createElement('input');
        check.type = 'checkbox'; check.className = 'form-check-input lesson-entry-check';
        check.value = entry.entryId; check.dataset.explorerCheck = group;
        check.setAttribute('aria-label', `${entry.name}を選択${folder ? '（配下を含む）' : ''}`);
        check.checked = (group === 'trash' ? checkedTrash : checkedEntries).has(entry.entryId);
        check.addEventListener('click', (event) => {
          setCheck(group, entry.entryId, check.checked, event.shiftKey);
        });
        row.append(check);
      }
      const title = document.createElement('div');
      title.className = 'lesson-tree-folder-title';
      if (folder) row.append(title);
      const selected = entry.virtualRoot ? !selectedTreeId : entry.entryId === selectedTreeId;
      if (selected) row.classList.add(folder ? 'is-selected-folder' : 'is-selected-row');
      const label = document.createElement('button');
      label.dataset.entryId = entry.entryId;
      label.type = 'button';
      label.className = folder ? 'lesson-tree-folder-select' : `lesson-tree-file-button${selected ? ' is-active' : ''}`;
      const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      icon.setAttribute('class', folder ? 'lesson-tree-folder-icon' : 'lesson-tree-file-icon');
      icon.setAttribute('width', '18'); icon.setAttribute('height', '18');
      icon.setAttribute('viewBox', '0 0 16 16'); icon.setAttribute('fill', 'currentColor');
      icon.setAttribute('aria-hidden', 'true');
      const shape = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      shape.setAttribute('d', folder
        ? 'M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z'
        : 'M3.75 1.5A1.75 1.75 0 0 0 2 3.25v9.5c0 .966.784 1.75 1.75 1.75h8.5A1.75 1.75 0 0 0 14 12.75V5.56a1.75 1.75 0 0 0-.513-1.237L10.677 1.513A1.75 1.75 0 0 0 9.44 1H3.75zm5.5 1.06c.133.035.255.103.354.202l2.634 2.634a.75.75 0 0 1 .202.354H9.75a.5.5 0 0 1-.5-.5V2.56z');
      icon.append(shape);
      const name = document.createElement('span');
      name.className = folder ? 'lesson-tree-folder-name' : 'lesson-tree-file-name lesson-tree-file-copy';
      name.textContent = entry.name;
      label.append(icon, name);
      fullName(label, entry.name);
      label.setAttribute('aria-label', entry.name);
      if (active(entry)) {
        label.dataset.entrySelection = 'true';
        label.addEventListener('click', (event) => {
          if (!entry.virtualRoot && (event.ctrlKey || event.metaKey || event.shiftKey)) {
            setCheck('normal', entry.entryId, !checkedEntries.has(entry.entryId), event.shiftKey); return;
          }
          if (folder) selectFolder(entry); else selectFile(page.dataset.exerciseId, entry.entryId);
        });
      } else {
        label.setAttribute('aria-disabled', 'true');
      }
      const children = document.createElement('div');
      children.className = 'lesson-tree-folder-children';
      const folderKey = entry.virtualRoot ? 'root' : entry.entryId;
      const inTrash = trashChild || entry.status === 'trashed';
      const isCollapsed = inTrash ? !expandedTrashFolders.has(folderKey) : !expandedFolders.has(folderKey);
      if (isCollapsed) children.classList.add('is-collapsed');
      if (folder) {
        const toggle = document.createElement('button');
        toggle.type = 'button'; toggle.className = 'lesson-tree-folder-toggle';
        toggle.setAttribute('aria-label', `${entry.name}の展開・折りたたみ`);
        toggle.setAttribute('aria-expanded', String(!isCollapsed));
        toggle.classList.toggle('is-collapsed', isCollapsed);
        const chevron = document.createElement('span');
        chevron.className = 'lesson-tree-folder-toggle-icon'; chevron.textContent = '▾';
        toggle.append(chevron);
        toggle.addEventListener('click', () => {
          const collapsed = children.classList.toggle('is-collapsed');
          if (inTrash) {
            if (collapsed) expandedTrashFolders.delete(folderKey); else expandedTrashFolders.add(folderKey);
          } else {
            if (collapsed) expandedFolders.delete(folderKey); else expandedFolders.add(folderKey);
          }
          toggle.classList.toggle('is-collapsed', collapsed);
          toggle.setAttribute('aria-expanded', String(!collapsed));
          updateSelectionCounts();
        });
        title.append(toggle);
      }
      (folder ? title : row).append(label);
      if (!entry.virtualRoot && !trashChild && (active(entry) || entry.status === 'trashed')) {
        const menu = document.createElement('div');
        menu.className = 'dropdown lesson-tree-entry-menu';
        const toggle = document.createElement('button');
        toggle.type = 'button'; toggle.className = 'btn btn-outline-secondary lesson-tree-menu-toggle';
        toggle.dataset.bsToggle = 'dropdown'; toggle.dataset.entryMenu = 'true';
        toggle.textContent = '⋮'; toggle.setAttribute('aria-label', `${entry.name}の操作`);
        toggle.setAttribute('aria-expanded', 'false');
        const dropdown = document.createElement('div');
        dropdown.className = 'dropdown-menu dropdown-menu-end lesson-tree-menu-dropdown';
        if (active(entry)) {
          if (!entry.virtualRoot) {
            for (const [operation, text] of [['rename', '名前変更'], ['duplicate', '複製'], ['move', '移動'], ['download', 'ダウンロード']]) {
              const button = document.createElement('button');
              button.type = 'button'; button.className = 'dropdown-item lesson-tree-menu-item';
              if (operation !== 'download') button.dataset.mutation = 'true';
              button.textContent = text;
              button.addEventListener('click', () => {
                if (operation === 'download') downloadEntries([entry]);
                else if (operation === 'duplicate') openDuplicate(entry);
                else if (operation === 'move') openBatch([entry], 'move');
                else openOrganize(entry, operation);
              });
              dropdown.append(button);
            }
          }
        }
        if (!entry.virtualRoot) {
          const action = document.createElement('button');
          action.type = 'button'; action.className = 'dropdown-item lesson-tree-menu-item';
          action.dataset.mutation = 'true';
          const operation = entry.status === 'trashed' ? 'restore' : 'trash';
          action.textContent = operation === 'restore' ? '復元' : 'ごみ箱へ';
          action.setAttribute('aria-label', `${entry.name}を${action.textContent}`);
          action.addEventListener('click', () => openBatch([entry], operation));
          if (operation === 'trash') action.classList.add('text-danger');
          dropdown.append(action);
        }
        menu.append(toggle, dropdown); row.append(menu);
      }
      wrapper.append(row);
      if (folder) wrapper.append(children);
      if (!trashChild && entry.status === 'trashed') {
        const details = document.createElement('p');
        details.className = 'lesson-trash-entry-details';
        const date = entry.trashedAt ? ` / 削除日時: ${entry.trashedAt.replace('T', ' ')}` : '';
        details.textContent = `元の場所: ${rootName}/${entry.path}${date}`;
        if (entry.parentId && !active(index.get(entry.parentId))) {
          let parentInTrash = false;
          for (let current = index.get(entry.parentId); current; current = index.get(current.parentId)) {
            if (current.status === 'trashed') parentInTrash = true;
          }
          details.textContent += parentInTrash ? ' / 先に親フォルダの復元が必要です'
            : ' / 元の親フォルダは復元できません。復元先を選んでください';
        }
        wrapper.append(details);
      }
      container.append(wrapper);
      return children;
    };
    // Iterate rather than recurse: the server deliberately has no fixed depth limit.
    const rootChildren = appendEntry({
      virtualRoot: true, entryId: '', parentId: '', type: 'folder', status: 'active', name: rootName
    }, tree);
    const query = nameSearch.value.trim();
    const pending = sorted(entries.filter((entry) => active(entry)
      && (query ? matchesName(entry, query) : !entry.parentId)))
      .map((entry) => ({ entry, container: rootChildren })).reverse();
    while (pending.length) {
      const { entry, container } = pending.pop();
      const children = appendEntry(entry, container);
      if (query) {
        const path = document.createElement('p');
        path.className = 'small lesson-explorer-path'; path.textContent = `${rootName}/${entry.path}`;
        container.lastElementChild.append(path);
        continue;
      }
      sorted(entries.filter((child) => child.parentId === entry.entryId && active(child))).reverse()
        .forEach((child) => pending.push({ entry: child, container: children }));
    }
    if (query && !rootChildren.children.length) {
      const empty = document.createElement('p'); empty.textContent = '一致する項目がありません。';
      rootChildren.append(empty);
    }
    const trashQuery = trashSearch.value.trim();
    const units = entries.filter((entry) => entry.status === 'trashed').sort((left, right) =>
      (right.trashedAt || '').localeCompare(left.trashedAt || '') || Number(right.entryId) - Number(left.entryId));
    const filteredUnits = units.filter((entry) => matchesName(entry, trashQuery)
      || entries.some((child) => String(child.trashRootEntryId) === entry.entryId && matchesName(child, trashQuery)));
    filteredUnits.slice(0, trashLimit)
      .forEach((entry) => {
        const pending = [{ entry, container: trash, root: true }];
        while (pending.length) {
          const { entry: current, container, root } = pending.pop();
          const children = appendEntry(current, container, !root);
          if (root && trashQuery) {
            const matches = entries.filter((child) => String(child.trashRootEntryId) === entry.entryId
              && child.entryId !== entry.entryId && matchesName(child, trashQuery));
            if (matches.length) {
              const summary = document.createElement('p'); summary.className = 'small lesson-explorer-path';
              summary.textContent = `一致した項目: ${matches.map((child) => child.path).join('、')}`;
              container.lastElementChild.append(summary);
            }
          }
          if (current.type !== 'folder') continue;
          const members = entries.filter((child) => child.parentId === current.entryId
            && String(child.trashRootEntryId) === entry.entryId && child.status !== 'deleted');
          if (!members.length) {
            const empty = document.createElement('p');
            empty.className = 'lesson-trash-empty'; empty.textContent = 'このフォルダは空です';
            children.append(empty);
          }
          members.reverse().forEach((child) => pending.push({ entry: child, container: children, root: false }));
        }
      });
    document.querySelector('#exerciseTrashCount').textContent = String(units.length);
    document.querySelector('#exerciseTrashVisibleCount').textContent =
      `${Math.min(trashLimit, filteredUnits.length)}件表示 / ${filteredUnits.length}件${trashQuery ? '（検索結果）' : ''}`;
    const more = document.querySelector('#exerciseTrashMore');
    more.hidden = filteredUnits.length <= trashLimit;
    more.textContent = `さらに表示（残り${Math.max(0, filteredUnits.length - trashLimit)}件）`;
    if (!trash.children.length) {
      const empty = document.createElement('p');
      empty.className = 'lesson-trash-empty'; empty.textContent = trashQuery ? '一致する項目がありません' : 'ごみ箱は空です';
      trash.append(empty);
    }
    document.querySelector('#emptyExercise').hidden = entries.some((entry) => active(entry));
    document.querySelector('#exerciseSelectResults').hidden = !query;
    const creation = index.get(selectedTreeId);
    const parent = creation?.type === 'file' ? index.get(creation.parentId) : creation;
    document.querySelector('#exerciseCreationTarget').textContent = `追加先: ${rootName}${parent ? `/${parent.path}` : ''}`;
    window.PPEExplorerMenus.bind(tree); window.PPEExplorerMenus.bind(trash);
    updateSelectionCounts(); buttons();
  }
  nameSearch.addEventListener('input', renderTree);
  sortControl.addEventListener('change', renderTree);
  trashSearch.addEventListener('input', () => { trashLimit = 10; renderTree(); });
  document.querySelector('#exerciseTrashMore').addEventListener('click', () => { trashLimit += 10; renderTree(); });
  document.querySelector('#exerciseSelectAll').addEventListener('click', () => {
    entries.filter((entry) => active(entry) && !entry.parentId).forEach((entry) => checkedEntries.add(entry.entryId));
    renderTree();
  });
  document.querySelector('#exerciseSelectResults').addEventListener('click', () => {
    entries.filter((entry) => active(entry) && matchesName(entry, nameSearch.value.trim()))
      .forEach((entry) => checkedEntries.add(entry.entryId));
    renderTree();
  });
  document.querySelector('#exerciseClearSelected').addEventListener('click', () => { checkedEntries.clear(); renderTree(); });
  document.querySelector('#exerciseTrashClearSelected').addEventListener('click', () => { checkedTrash.clear(); renderTree(); });
  for (const [group, prefix, selected] of [['normal', 'exercise', checkedEntries], ['trash', 'exerciseTrash', checkedTrash]]) {
    document.querySelector(`#${prefix}SelectMode`).addEventListener('click', () => {
      selectionModes[group] = true; updateSelectionCounts();
      document.querySelector(group === 'normal' ? '#exerciseTree input' : '#exerciseTrash input')?.focus();
    });
    document.querySelector(`#${prefix}SelectionFinish`).addEventListener('click', () => {
      selected.clear(); selectionAnchors[group] = null; selectionModes[group] = false;
      renderTree(); document.querySelector(`#${prefix}SelectMode`).focus();
    });
  }
  document.querySelector('#exerciseCollapseAll').addEventListener('click', () => {
    expandedFolders.clear(); expandedFolders.add('root'); renderTree();
  });
  document.querySelector('#exerciseExpandAll').addEventListener('click', () => {
    entries.filter((entry) => active(entry) && entry.type === 'folder')
      .forEach((entry) => expandedFolders.add(entry.entryId));
    expandedFolders.add('root'); renderTree();
  });
  document.querySelector('#exerciseRevealFile').addEventListener('click', () => {
    nameSearch.value = '';
    for (let entry = index.get(page.dataset.entryId); entry; entry = index.get(entry.parentId)) {
      if (entry.type === 'folder') expandedFolders.add(entry.entryId);
    }
    expandedFolders.add('root'); renderTree();
    const target = [...document.querySelectorAll('#exerciseTree [data-entry-selection]')]
      .find((label) => label.dataset.entryId === page.dataset.entryId);
    target?.scrollIntoView({ block: 'nearest' }); target?.focus();
  });
  for (const [id, group] of [['exerciseTree', 'normal'], ['exerciseTrash', 'trash']]) {
    document.querySelector(`#${id}`).addEventListener('keydown', (event) => {
      if (event.target.matches('input:not([type="checkbox"]), textarea, select')
        || document.querySelector('.modal.show')) return;
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
        event.preventDefault();
        selectionModes[group] = true; updateSelectionCounts();
        const selected = group === 'trash' ? checkedTrash : checkedEntries;
        visibleChecks(group).forEach((control) => selected.add(control.value));
        document.querySelectorAll(`[data-explorer-check="${group}"]`)
          .forEach((control) => { control.checked = selected.has(control.value); });
        updateSelectionCounts(); buttons();
      } else if (event.key === 'Escape') {
        event.preventDefault();
        (group === 'trash' ? checkedTrash : checkedEntries).clear();
        document.querySelectorAll(`[data-explorer-check="${group}"]`).forEach((control) => { control.checked = false; });
        updateSelectionCounts(); buttons();
      }
    });
  }
  document.querySelector('#exerciseMoveSelected').addEventListener('click', () => openBatch(normalTargets(), 'move'));
  document.querySelector('#exerciseTrashSelected').addEventListener('click', () => openBatch(normalTargets(), 'trash'));
  document.querySelector('#exerciseRestoreSelected').addEventListener('click', () =>
    openBatch(entries.filter((entry) => checkedTrash.has(entry.entryId)), 'restore'));
  function openBatch(targets, operation) {
    if (busy || selectionPending || sessionId || refreshFailed || !editable || !targets.length) return;
    batchTargets = targets; batchMode = operation;
    batchFeedback.clearInlineAlert();
    const title = { move: '移動', trash: 'ごみ箱へ移動', restore: 'ごみ箱から復元', duplicate: '複製' }[operation];
    document.querySelector('#exerciseBatchTitle').textContent = `${targets.length}件を${title}`;
    const confirm = document.querySelector('#exerciseBatchConfirm');
    confirm.textContent = { move: '移動', trash: '削除', restore: '復元', duplicate: '複製' }[operation];
    confirm.classList.toggle('btn-danger', operation === 'trash');
    confirm.classList.toggle('btn-primary', operation !== 'trash');
    document.querySelector('#exerciseBatchDestination').hidden = operation !== 'move';
    if (operation === 'move') {
      const parent = document.querySelector('#exerciseBatchParent');
      parent.value = '__required__';
      renderFolderTargets(document.querySelector('#exerciseBatchFolderTree'), parent,
        targets.map((entry) => entry.entryId), document.querySelector('#exerciseBatchDestinationPath'), '移動先');
    }
    const affectsEditor = targets.some((entry) => covers(entry, page.dataset.entryId));
    document.querySelector('#exerciseBatchHelp').textContent = operation === 'trash'
      ? `以下のファイルとフォルダをごみ箱へ移します。完全には削除しません。${affectsEditor && isDirty()
        ? '編集中ファイルの未保存コードは失われます。残す場合はキャンセルして保存してください。' : ''}`
      : operation === 'restore' ? '元の場所へ配下もまとめて復元します。先に個別削除した項目は選択しない限り戻りません。復元先に同じ名前のファイルやフォルダがある場合は、下の名前欄で別の名前に変更してください。既存の項目は上書きしません。'
        : operation === 'duplicate' ? '保存済み内容を複製します。未保存のコードや実行履歴はコピーしません。複製先に同じ名前の項目がある場合は、下の「複製後の名前」欄で別の名前に変更してください。既存の項目は上書きしません。'
          : '移動先に同じ名前のファイルやフォルダがある場合は、下の名前欄で別の名前に変更してください。既存の項目は上書きしません。';
    const container = document.querySelector('#exerciseBatchItems'); container.replaceChildren();
    for (const entry of targets) {
      const row = document.createElement('div'); row.className = 'lesson-batch-item'; row.dataset.entryId = entry.entryId;
      const path = document.createElement('p'); path.textContent = `${rootName}/${entry.path}`; row.append(path);
      if (operation === 'trash') {
        const list = document.createElement('ul'); list.className = 'lesson-delete-preview';
        list.setAttribute('aria-label', `${entry.name}の削除対象`);
        function appendItem(item, parent) {
          const node = document.createElement('li');
          const name = document.createElement('span');
          name.textContent = `${item.type === 'folder' ? 'フォルダ' : 'ファイル'}: ${item.name}`;
          name.title = `${rootName}/${item.path}`; node.append(name); parent.append(node);
          const children = sorted(entries.filter((child) => active(child) && child.parentId === item.entryId));
          if (children.length) {
            const nested = document.createElement('ul'); node.append(nested);
            children.forEach((child) => appendItem(child, nested));
          }
        }
        appendItem(entry, list); row.append(list);
      }
      if (operation !== 'trash') {
        const label = document.createElement('label'); label.textContent = operation === 'duplicate' ? '複製後の名前' : '名前';
        const input = document.createElement('input'); input.className = 'form-control';
        input.dataset.batchName = 'true'; input.required = true; input.value = entry.name;
        input.setAttribute('aria-label', `${entry.name}の${label.textContent}`); label.append(input); row.append(label);
      }
      if (operation === 'restore') {
        let requiresDestination = false;
        for (let id = entry.parentId; id;) {
          const parent = index.get(id);
          if (!parent || parent.status === 'deleted') { requiresDestination = true; break; }
          if (parent.status === 'trashed' && !targets.some((target) => target.entryId === parent.entryId)) {
            batchFeedback.inlineAlert(`${entry.path}: 元の親がごみ箱にあります。親も選ぶか、先に親を復元してください。`, 'warning');
          }
          id = parent.parentId;
        }
        const label = document.createElement('label'); label.textContent = '復元先';
        const select = document.createElement('select'); select.className = 'form-select'; select.dataset.batchParent = 'true';
        select.add(new Option('別の場所をツリーで選ぶ', '__required__'));
        if (requiresDestination) {
          select.value = '__required__';
        } else {
          select.insertBefore(new Option('元の場所（選択した親の復元後を含む）', '__original__'), select.firstChild);
          select.value = '__original__';
        }
        label.append(select); row.append(label);
        const destination = document.createElement('div');
        destination.className = 'move-entry-tree mt-2'; destination.setAttribute('role', 'group');
        destination.setAttribute('aria-label', `${entry.name}の復元先フォルダ`);
        const selectedPath = document.createElement('p'); selectedPath.className = 'small lesson-explorer-path';
        const updateDestination = () => {
          destination.hidden = select.value === '__original__';
          selectedPath.hidden = destination.hidden;
          if (!destination.hidden) renderFolderTargets(destination, select, [], selectedPath, '復元先');
        };
        select.addEventListener('change', updateDestination);
        row.append(selectedPath, destination); updateDestination();
      }
      container.append(row);
    }
    buttons(); batchModal.show();
  }
  let duplicatePreview = null;
  async function openDuplicate(entry) {
    if (busy || selectionPending || sessionId || refreshFailed || !editable) return;
    busy = true; buttons(); feedback.clearInlineAlert();
    try {
      duplicatePreview = await post('duplicate-preview', { entryId: entry.entryId, expectedVersion: version });
      busy = false;
      openBatch([entry], 'duplicate');
      document.querySelector('[data-batch-name]').value = duplicatePreview.items[0].suggestedName;
    } catch (error) { feedback.inlineAlert(error.message, 'danger'); }
    finally { busy = false; buttons(); }
  }
  document.querySelector('#exerciseBatchForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || selectionPending || sessionId || refreshFailed || !editable || !batchTargets.length) return;
    if (batchMode === 'move' && document.querySelector('#exerciseBatchParent').value === '__required__') {
      batchFeedback.inlineAlert('移動先をツリーで選んでください。', 'warning'); return;
    }
    const rows = [...document.querySelectorAll('#exerciseBatchItems > [data-entry-id]')];
    const items = rows.map((row) => {
      const item = { entryId: Number(row.dataset.entryId) };
      const name = row.querySelector('[data-batch-name]'); if (name) item.name = name.value;
      const parent = row.querySelector('[data-batch-parent]');
      if (parent && parent.value !== '__original__') {
        item.parentEntryId = parent.value === '' || parent.value === '__required__' ? parent.value : Number(parent.value);
      }
      return item;
    });
    if (items.some((item) => item.parentEntryId === '__required__')) {
      batchFeedback.inlineAlert('元の親がない項目の復元先を選んでください。', 'warning'); return;
    }
    if (items.some((item) => !Number.isSafeInteger(item.entryId)
      || (typeof item.parentEntryId === 'number' && !Number.isSafeInteger(item.parentEntryId)))) {
      batchFeedback.inlineAlert('対象IDを安全に処理できません。管理者へお問い合わせください。', 'danger'); return;
    }
    busy = true; buttons();
    try {
      const values = { expectedVersion: version, items: JSON.stringify(items) };
      if (batchMode === 'move') values.parentEntryId = document.querySelector('#exerciseBatchParent').value;
      if (batchMode === 'duplicate') {
        delete values.items;
        values.entryId = batchTargets[0].entryId; values.name = items[0].name;
      }
      const operation = batchMode === 'duplicate' ? 'duplicate' : `batch-${batchMode}`;
      const result = await post(operation, values);
      const clearEditor = batchMode === 'trash' && batchTargets.some((entry) => covers(entry, page.dataset.entryId));
      const selected = batchMode === 'restore' ? checkedTrash : checkedEntries;
      for (const id of selected) {
        if (batchTargets.some((entry) => entry.entryId === id || (batchMode !== 'restore' && covers(entry, id)))) selected.delete(id);
      }
      closeCommittedModal(batchModal);
      await refreshAfterMutation(result, null, result.status === 'unchanged' ? '変更はありません。' : {
        move: '移動しました。', trash: 'ごみ箱へ移動しました。', restore: '復元しました。', duplicate: '複製しました。'
      }[batchMode], true, clearEditor);
    } catch (error) { batchFeedback.inlineAlert(error.message, 'danger'); }
    finally { busy = false; buttons(); }
  });
  document.querySelector('#exerciseBatchParent').addEventListener('change', buttons);
  function appendTerminal(text, stream) {
    if (!text) return;
    if (stream === 'input') stream = 'stdin';
    const span = document.createElement('span');
    span.className = { stdin: 'terminal-input-echo', stderr: 'terminal-stderr', system: 'terminal-system-message' }[stream] || '';
    span.textContent = stream === 'stdin' ? `>>> ${text}` : stream === 'stderr' ? `[エラー] ${text}` : text;
    terminal.append(span); terminal.scrollTop = terminal.scrollHeight;
  }
  function executionStatus(result, restored = false) {
    const status = result.errorCode === 'execution_cancelled' ? 'cancelled' : result.status;
    const labels = { running: '実行中', succeeded: '実行完了', cancelled: '実行停止', failed: '実行エラー', timed_out: '時間切れ' };
    const badge = document.querySelector('#runResultStatus');
    badge.textContent = `${restored ? '保存済み: ' : ''}${labels[status] || status}`;
    badge.className = `badge ${status === 'succeeded' ? 'text-bg-success' : status === 'running' ? 'text-bg-primary' : 'text-bg-danger'}`;
    document.querySelector('#runResultTime').textContent = status === 'running' ? '実行中…'
      : restored ? `記録時刻（サーバー）: ${restored.replace('T', ' ')}` : `最終実行: ${new Date().toLocaleTimeString('ja-JP')}`;
  }
  function finishExecution(result) {
    if (result.errorCode === 'input_timeout') appendTerminal('\n30秒間入力がなかったため停止しました。\n', 'system');
    else if (result.status === 'timed_out') appendTerminal('\n実行時間の上限に達したため停止しました。\n', 'system');
    else if (result.status === 'cancelled' || result.errorCode === 'execution_cancelled') appendTerminal('\n実行を停止しました。\n', 'system');
    else if (result.errorCode) appendTerminal(`\n${result.errorCode}\n`, 'system');
    if (!terminal.textContent) terminal.textContent = '出力はありません。';
    sessionId = null; busy = false; buttons();
  }
  function showSavedExecution(execution) {
    terminal.replaceChildren();
    if (!execution) {
      terminal.textContent = 'まだ実行していません。';
      document.querySelector('#runResultStatus').textContent = '実行待ち';
      document.querySelector('#runResultStatus').className = 'badge text-bg-secondary';
      document.querySelector('#runResultTime').textContent = '実行すると結果がここに表示されます。';
      return;
    }
    appendTerminal('保存済み結果（入出力・エラー別の記録です。実行時の交互順序は復元できません。）\n', 'system');
    appendTerminal(execution.standardInput, 'stdin');
    appendTerminal(execution.result.standardOutput, 'stdout');
    appendTerminal(execution.result.standardError, 'stderr');
    if (execution.result.standardOutputTruncated || execution.result.standardErrorTruncated) {
      appendTerminal('\n出力を省略しています。\n', 'system');
    }
    executionStatus(execution.result, execution.executedAt);
    finishExecution(execution.result);
  }
  function consume(envelope) {
    const result = envelope.update;
    result.events.forEach((event) => {
      if (event.id > cursor) { appendTerminal(event.text, event.stream); cursor = event.id; }
    });
    cursor = Math.max(cursor, result.nextCursor);
    if (!truncated && (result.standardOutputTruncated || result.standardErrorTruncated)) {
      truncated = true; appendTerminal('\n出力を省略しています。\n', 'system');
    }
    executionStatus(result);
    if (result.status !== 'running') finishExecution(result);
  }
  async function poll() {
    let failures = 0;
    while (sessionId) {
      await new Promise((resolve) => setTimeout(resolve, failures ? 1000 : 250));
      if (!sessionId) break;
      try {
        const url = new URL(`${page.dataset.baseUrl}/session`, window.location.origin);
        url.search = new URLSearchParams({ exerciseId: page.dataset.exerciseId, entryId: page.dataset.entryId,
          sessionId, after: cursor }).toString();
        const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' });
        if (!(response.headers.get('content-type') || '').includes('application/json')) throw new Error('ログイン状態を確認してください。');
        const result = await response.json();
        if (!response.ok) {
          if ([400, 403, 404].includes(response.status)
            || ['runner_cleanup_failed', 'runner_unavailable'].includes(result.errorCode)) {
            feedback.inlineAlert(result.message, 'danger');
            finishExecution({ status: 'failed', errorCode: result.errorCode });
            executionStatus({ status: 'failed' });
            document.querySelector('#runResultStatus').textContent = '実行状態の取得失敗';
            break;
          }
          throw new Error(result.message || '実行結果を取得できませんでした。');
        }
        if (failures) feedback.clearInlineAlert();
        failures = 0; consume(result);
      } catch (error) {
        feedback.inlineAlert(`${error.message} 再取得を試みます。`, 'danger');
        if (++failures >= 90) {
          appendTerminal('\n実行状態を確認できません。再読み込みして保存済み履歴を確認してください。\n', 'system');
          finishExecution({ status: 'failed' }); executionStatus({ status: 'failed' });
        }
      }
    }
  }
  saveButton.addEventListener('click', async () => {
    const snapshot = editor.getValue();
    const result = await mutation('save', { entryId: page.dataset.entryId, expectedVersion: version, code: snapshot });
    if (!result) return;
    version = String(result.version); savedCode = snapshot; updateStatus();
    feedback.toast({ message: '保存しました。', variant: 'success' });
  });
  runButton.addEventListener('click', async () => {
    if (busy || !editable || !fileSelected) return;
    busy = true; buttons(); feedback.clearInlineAlert();
    terminal.replaceChildren(); cursor = 0; truncated = false; input.value = '';
    executionStatus({ status: 'running' });
    document.querySelector('.execution-result-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
    try {
      const result = await post('run', { entryId: page.dataset.entryId, code: editor.getValue() });
      sessionId = result.update.sessionId; consume(result); buttons();
      if (sessionId) { input.focus({ preventScroll: true }); await poll(); }
    } catch (error) {
      feedback.inlineAlert(error.message, 'danger');
      finishExecution({ status: 'failed' }); executionStatus({ status: 'failed' });
    }
  });
  document.querySelector('#terminalInputForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!sessionId || inputSending) return;
    inputSending = true; buttons();
    try {
      await post('session/input', { entryId: page.dataset.entryId, sessionId, line: input.value });
      input.value = '';
    } catch (error) { feedback.inlineAlert(error.message, 'danger'); }
    finally { inputSending = false; buttons(); if (sessionId) input.focus({ preventScroll: true }); }
  });
  cancelButton.addEventListener('click', async () => {
    if (!sessionId) return;
    cancelButton.disabled = true;
    try { await post('session/cancel', { entryId: page.dataset.entryId, sessionId }); }
    catch (error) { feedback.inlineAlert(error.message, 'danger'); }
    finally { cancelButton.disabled = false; }
  });
  async function openCreate(type, parentId) {
    if (busy || selectionPending || sessionId || !editable || refreshFailed) return;
    entryType = type; createFeedback.clearInlineAlert();
    document.querySelector('#createEntryTitle').textContent = type === 'file' ? '新しいファイル' : '新しいフォルダ';
    document.querySelector('#entryNameHelp').textContent = type === 'file'
      ? '1〜255文字。使えない文字：/（スラッシュ）、\\（バックスラッシュ）、改行、タブなど。空白だけの名前も使えません。「.」だけ・「..」だけの名前は使えません。'
      : '1〜255文字で入力してください。使えない文字：. / \\ 。名前の途中で改行したり、タブを入れたりしないでください。空白だけの名前も使えません。';
    const parent = document.querySelector('#parentEntry');
    fillParents(parent);
    if (parentId !== undefined) parent.value = parentId;
    document.querySelector('#entryName').value = '';
    updateNameExtension();
    modal.show();
  }
  function updateNameExtension() {
    const showExtension = entryType === 'file' && !/\.py$/i.test(document.querySelector('#entryName').value);
    document.querySelector('#entryNameExtension').hidden = !showExtension;
    document.querySelector('#entryNameExtension').classList.toggle('d-none', !showExtension);
    document.querySelector('#entryName').setAttribute('aria-describedby', showExtension
      ? 'entryNameHelp entryNameExtension' : 'entryNameHelp');
  }
  document.querySelector('#entryName').addEventListener('input', updateNameExtension);
  for (const element of [modalElement, uploadElement, organizeElement, unifyElement, batchElement]) {
    element.addEventListener('hide.bs.modal', (event) => {
      if ((busy || readingUpload) && !allowModalHide) event.preventDefault();
      if (!event.defaultPrevented && element.contains(document.activeElement)) document.activeElement.blur();
    });
    element.addEventListener('hidden.bs.modal', () => {
      if (element === batchElement) {
        const prefix = batchMode === 'restore' ? 'exerciseTrash' : 'exercise';
        const panel = document.querySelector(`#${prefix}SelectionPanel`);
        if (!panel.open) {
          panel.querySelector('summary').focus({ preventScroll: true });
          return;
        }
      }
      if (!confirmingCreate) document.querySelector(element === batchElement
        ? (batchMode === 'restore'
          ? (selectionModes.trash ? '#exerciseTrashSelectionFinish' : '#exerciseTrashSelectMode')
          : (selectionModes.normal ? '#exerciseSelectionFinish' : '#exerciseSelectMode'))
        : element === unifyElement ? '#newFileButton'
        : element === organizeElement ? '#newFileButton'
        : element === uploadElement ? '#uploadButton'
        : entryType === 'file' ? '#newFileButton' : '#newFolderButton').focus({ preventScroll: true });
    });
  }
  function closeCommittedModal(dialog) {
    allowModalHide = true; dialog.hide(); allowModalHide = false;
  }
  document.querySelector('#previewUnificationButton').addEventListener('click', async () => {
    if (busy || selectionPending || sessionId || refreshFailed) return;
    unificationPreview = null;
    busy = true; buttons(); unifyFeedback.clearInlineAlert();
    document.querySelector('#unifyExercisePreview').replaceChildren();
    document.querySelector('#unifyExerciseSummary').textContent = '統合内容を取得しています。';
    unifyModal.show();
    try {
      const response = await fetch(`${page.dataset.baseUrl}/unification`, {
        credentials: 'same-origin', cache: 'no-store'
      });
      if (!(response.headers.get('content-type') || '').includes('application/json')) {
        throw new Error('ログイン状態またはサーバーの応答を確認してください。');
      }
      const preview = await response.json();
      if (!response.ok) throw new Error(preview.message || '統合内容を取得できませんでした。');
      if (!preview.required) throw new Error('統合対象が更新されています。編集中のコードを控えてから再読み込みしてください。');
      unificationPreview = preview;
      document.querySelector('#unifyExerciseSummary').textContent = `フォルダ・ファイル計${preview.entryCount}件（ごみ箱・削除済み項目を含む）を保持します。`;
      document.querySelector('#unifyExercisePreview').replaceChildren(...preview.items.map((item) => {
        const row = document.createElement('li');
        row.textContent = `${item.sourceName}: ${item.oldPath} → ${rootName}/${item.newPath}${item.status === 'trashed' ? '（ごみ箱を維持）' : item.status === 'deleted' ? '（削除済みを維持）' : ''}`;
        return row;
      }));
    } catch (error) {
      document.querySelector('#unifyExerciseSummary').textContent = '';
      unifyFeedback.inlineAlert(error.message, 'danger');
    } finally { busy = false; buttons(); }
  });
  document.querySelector('#unifyExerciseConfirmButton').addEventListener('click', async () => {
    if (busy || selectionPending || sessionId || refreshFailed || !unificationPreview) return;
    busy = true; buttons(); unifyFeedback.clearInlineAlert();
    try {
      const result = await post('unify', { token: unificationPreview.token, expectedVersion: version });
      closeCommittedModal(unifyModal);
      unificationPreview = null;
      await refreshAfterMutation(result, null, '一つのルートへ統合しました。', true);
    } catch (error) {
      unificationPreview = null;
      unifyFeedback.inlineAlert(`${error.message} この画面を閉じて「統合内容を確認」からもう一度確認してください。`, 'danger');
    } finally { busy = false; buttons(); }
  });
  document.querySelector('#newFileButton').addEventListener('click', () => openCreate('file'));
  document.querySelector('#newFolderButton').addEventListener('click', () => openCreate('folder'));
  modalElement.addEventListener('shown.bs.modal', () => document.querySelector('#entryName').focus());
  document.querySelector('#createEntryForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || refreshFailed || confirmingCreate) return;
    if (entryType === 'file' && isDirty()) {
      confirmingCreate = true;
      await new Promise((resolve) => {
        modalElement.addEventListener('hidden.bs.modal', resolve, { once: true });
        document.activeElement?.blur();
        modal.hide();
      });
      const discard = await mayLeave();
      confirmingCreate = false;
      if (!discard) { modal.show(); return; }
    }
    const values = { type: entryType, name: document.querySelector('#entryName').value, expectedVersion: version };
    const parent = document.querySelector('#parentEntry').value;
    if (parent) values.parentEntryId = parent;
    busy = true; buttons(); createFeedback.clearInlineAlert();
    try {
      const result = await post('create', values);
      closeCommittedModal(modal);
      await refreshAfterMutation(result, entryType === 'file' ? String(result.entryId) : null,
        `${entryType === 'file' ? 'ファイル' : 'フォルダ'}を作成しました。`);
    } catch (error) { createFeedback.inlineAlert(error.message, 'danger'); modal.show(); }
    finally { busy = false; buttons(); }
  });
  function renderFolderTargets(tree, parent, excludedIds = [], selectedPath = null, pathLabel = '追加先') {
    if (!Array.isArray(excludedIds)) excludedIds = [excludedIds];
    clearNameTooltips(tree);
    tree.replaceChildren();
    const folders = entries.filter((entry) => {
      if (entry.type !== 'folder' || !active(entry)) return false;
      for (let ancestor = entry; ancestor; ancestor = index.get(ancestor.parentId)) {
        if (excludedIds.includes(ancestor.entryId)) return false;
      }
      return true;
    });
    const updatePath = () => {
      if (!selectedPath) return;
      const target = index.get(parent.value);
      selectedPath.textContent = parent.value === '__required__'
        ? `${pathLabel}をツリーで選んでください。`
        : `${pathLabel}: ${rootName}${target ? `/${target.path}` : ''}`;
    };
    updatePath();
    const pending = [{ folder: { entryId: '', name: rootName, path: '' }, container: tree, root: true }];
    while (pending.length) {
      const { folder, container, root } = pending.pop();
      const wrapper = document.createElement('div');
      wrapper.className = `move-entry-folder${root ? ' is-root-node' : ''}`;
      const isSelected = parent.value === folder.entryId;
      wrapper.classList.toggle('is-selected-target', isSelected);
      const button = document.createElement('button');
      button.type = 'button'; button.className = 'move-entry-folder-button';
      fullName(button, root ? rootName : `${rootName}/${folder.path}`);
      const main = document.createElement('div'); main.className = 'move-entry-folder-main';
      const icon = document.querySelector('#exerciseTree .lesson-tree-folder-icon').cloneNode(true);
      icon.setAttribute('class', 'move-entry-folder-icon');
      const label = document.createElement('span');
      label.className = 'move-entry-folder-name'; label.textContent = folder.name;
      main.append(icon, label); button.append(main);
      button.dataset.folderId = folder.entryId;
      button.setAttribute('aria-pressed', String(isSelected));
      button.addEventListener('click', () => {
        if (parent.tagName === 'SELECT' && ![...parent.options].some((option) => option.value === folder.entryId)) {
          parent.querySelectorAll('[data-tree-destination]').forEach((option) => option.remove());
          const option = new Option(`${rootName}${folder.path ? `/${folder.path}` : '（ルート）'}`, folder.entryId);
          option.dataset.treeDestination = 'true'; parent.add(option);
        }
        parent.value = folder.entryId;
        updatePath();
        parent.dispatchEvent(new Event('change'));
        tree.querySelectorAll('[data-folder-id]').forEach((target) => {
          const selected = target.dataset.folderId === folder.entryId;
          target.setAttribute('aria-pressed', String(selected));
          target.parentElement.classList.toggle('is-selected-target', selected);
        });
      });
      wrapper.append(button); container.append(wrapper);
      const childFolders = folders.filter((child) => root ? !child.parentId : child.parentId === folder.entryId);
      if (childFolders.length) {
        const children = document.createElement('div'); children.className = 'move-entry-children';
        const toggle = document.createElement('button'); toggle.type = 'button';
        toggle.className = 'lesson-tree-folder-toggle move-entry-toggle'; toggle.textContent = '▾';
        toggle.setAttribute('aria-label', `${folder.name}の展開・折りたたみ`);
        toggle.setAttribute('aria-expanded', 'true');
        toggle.addEventListener('click', () => {
          children.hidden = !children.hidden;
          toggle.setAttribute('aria-expanded', String(!children.hidden));
          toggle.textContent = children.hidden ? '▸' : '▾';
        });
        wrapper.insertBefore(toggle, button);
        wrapper.append(children);
        childFolders.reverse().forEach((child) => pending.push({ folder: child, container: children, root: false }));
      }
    }
  }
  function updateOrganizeSuffix() {
    const entry = index.get(organizeEntryId);
    const input = document.querySelector('#organizeEntryName');
    const suffix = document.querySelector('#organizeNameExtension');
    const shown = entry?.type === 'file' && !/\.py$/i.test(input.value);
    suffix.hidden = !shown; suffix.classList.toggle('d-none', !shown);
    input.setAttribute('aria-describedby', shown ? 'organizeEntryHelp organizeNameExtension' : 'organizeEntryHelp');
  }
  function openOrganize(entry, operation) {
    if (busy || selectionPending || !editable || refreshFailed
      || (operation === 'restore' ? entry.status !== 'trashed' : !active(entry))) return;
    restoreDestinationRequired = false;
    if (operation === 'restore') {
      for (let parentId = entry.parentId; parentId;) {
        const ancestor = index.get(parentId);
        if (!ancestor) { restoreDestinationRequired = true; break; }
        if (ancestor.status === 'trashed') {
          feedback.inlineAlert('元の親フォルダがごみ箱にあります。先に親フォルダを復元してください。', 'warning');
          return;
        }
        if (ancestor.status === 'deleted') restoreDestinationRequired = true;
        parentId = ancestor.parentId;
      }
    }
    organizeEntryId = entry.entryId; organizeMode = operation;
    organizeFeedback.clearInlineAlert();
    document.querySelector('#organizeEntryTitle').textContent = operation === 'restore' ? 'ごみ箱から復元'
      : operation === 'move' ? '移動' : '名前変更';
    document.querySelector('#organizeEntryConfirmButton').textContent = operation === 'restore' ? '復元する'
      : operation === 'move' ? '移動する' : '変更する';
    document.querySelector('#organizeEntryPath').textContent = `対象: ${rootName}/${entry.path}`;
    document.querySelector('#organizeEntryName').value = entry.name;
    document.querySelector('#organizeEntryHelp').textContent = entry.type === 'folder'
      ? '1〜255文字で入力してください。使えない文字：. / \\ 。名前の途中で改行したり、タブを入れたりしないでください。空白だけの名前も使えません。'
      : '1〜255文字。使えない文字：/、\\、改行、タブなど。空白だけ・「.」だけ・「..」だけの名前は使えません。';
    const restoreHelp = document.querySelector('#organizeRestoreHelp');
    restoreHelp.hidden = operation !== 'restore';
    restoreHelp.textContent = restoreDestinationRequired
      ? '元の親フォルダは復元できません。復元先を選んでください。'
      : '元の場所へ復元します。同名がある場合は別の名前を入力すると両方を残せます。フォルダは配下もまとめて復元し、先に個別削除した項目はごみ箱に残します。';
    const chooseDestination = operation === 'move' || restoreDestinationRequired;
    document.querySelector('#organizeDestination').hidden = !chooseDestination;
    document.querySelector('#organizeFolderLabel').textContent = operation === 'restore' ? '復元先' : '移動先';
    const parent = document.querySelector('#organizeParentEntry');
    parent.value = restoreDestinationRequired ? '' : entry.parentId || '';
    if (chooseDestination) {
      renderFolderTargets(document.querySelector('#organizeFolderTree'), parent,
        entry.type === 'folder' ? entry.entryId : null);
    }
    updateOrganizeSuffix(); buttons(); organizeModal.show();
  }
  document.querySelector('#organizeEntryName').addEventListener('input', updateOrganizeSuffix);
  organizeElement.addEventListener('shown.bs.modal', () => document.querySelector('#organizeEntryName').focus());
  document.querySelector('#organizeEntryForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || selectionPending || !editable || refreshFailed) return;
    busy = true; buttons(); organizeFeedback.clearInlineAlert();
    try {
      const values = { entryId: organizeEntryId, expectedVersion: version,
        name: document.querySelector('#organizeEntryName').value };
      if (organizeMode === 'move' || restoreDestinationRequired) {
        values.parentEntryId = document.querySelector('#organizeParentEntry').value;
      }
      const result = await post(organizeMode, values);
      closeCommittedModal(organizeModal);
      if (String(result.version) === version) {
        feedback.toast({ message: '変更はありません。', variant: 'info' });
      } else {
        await refreshAfterMutation(result, null, organizeMode === 'restore' ? '復元しました。'
          : organizeMode === 'move' ? '移動しました。' : '名前を変更しました。', true);
      }
    } catch (error) { organizeFeedback.inlineAlert(error.message, 'danger'); }
    finally { busy = false; buttons(); }
  });
  document.querySelector('#uploadButton').addEventListener('click', () => {
    if (busy || selectionPending || sessionId || !editable || refreshFailed) return;
    const parent = document.querySelector('#uploadParentEntry');
    const selected = index.get(selectedTreeId);
    parent.value = selected ? selected.type === 'folder' ? selected.entryId : selected.parentId || '' : '';
    renderFolderTargets(document.querySelector('#uploadFolderTree'), parent);
    uploadFiles = [];
    resetUploadPreview();
    document.querySelector('#uploadSourceFile').value = '';
    document.querySelector('#uploadSourceDirectory').value = '';
    document.querySelector('#uploadFileName').textContent = '未選択';
    document.querySelector('#uploadFilePreviewList').replaceChildren();
    uploadFeedback.clearInlineAlert(); buttons(); uploadModal.show();
  });
  for (const [button, inputId, directory] of [
    ['uploadSelectFilesButton', 'uploadSourceFile', false],
    ['uploadSelectFolderButton', 'uploadSourceDirectory', true]
  ]) {
    const source = document.querySelector(`#${inputId}`);
    document.querySelector(`#${button}`).addEventListener('click', () => source.click());
    source.addEventListener('change', () => {
      const all = [...source.files];
      uploadFiles = all.filter((file) => /\.py$/i.test(file.name))
        .map((file) => ({ file, path: directory ? file.webkitRelativePath : file.name }));
      resetUploadPreview();
      const excluded = all.length - uploadFiles.length;
      document.querySelector('#uploadFileName').textContent = `${uploadFiles.length}件の.py${excluded ? `（.py以外 ${excluded}件は除外）` : ''}`;
      const preview = document.querySelector('#uploadFilePreviewList');
      preview.replaceChildren(...uploadFiles.map(({ path }) => {
        const item = document.createElement('li'); item.textContent = path; return item;
      }));
      uploadFeedback.clearInlineAlert(); buttons();
    });
  }
  function resetUploadPreview() {
    uploadPreview = null; uploadPayload = null;
    document.querySelector('#uploadResolutionPanel').hidden = true;
    document.querySelector('#uploadResolutionItems').replaceChildren();
    uploadConfirm.textContent = '追加内容を確認';
  }
  document.querySelector('#uploadParentEntry').addEventListener('change', resetUploadPreview);
  function renderUploadConflicts(conflicts) {
    const container = document.querySelector('#uploadResolutionItems');
    container.replaceChildren();
    for (const conflict of conflicts) {
      const row = document.createElement('div'); row.className = 'lesson-batch-item'; row.dataset.path = conflict.path;
      const label = document.createElement('label');
      label.textContent = `${conflict.path}（追加する${conflict.uploadType === 'folder' ? 'フォルダ' : 'ファイル'}と同名の${conflict.existingType === 'folder' ? 'フォルダ' : 'ファイル'}があります）`;
      const select = document.createElement('select'); select.className = 'form-select'; select.dataset.uploadAction = 'true';
      select.add(new Option('解決方法を選んでください', ''));
      for (const action of conflict.actions) {
        select.add(new Option({ merge: '既存フォルダに中身を追加（上書きしません）',
          rename: '別の名前で追加', skip: '追加しない（フォルダは配下も除外）' }[action], action));
      }
      const input = document.createElement('input'); input.className = 'form-control mt-2';
      input.dataset.uploadName = 'true'; input.value = conflict.suggestedName; input.hidden = true;
      input.setAttribute('aria-label', `${conflict.path}の別名`);
      select.addEventListener('change', () => { input.hidden = select.value !== 'rename'; });
      label.append(select); row.append(label, input); container.append(row);
    }
    document.querySelector('#uploadResolutionPanel').hidden = false;
  }
  uploadConfirm.addEventListener('click', async () => {
    if (busy || selectionPending || sessionId || readingUpload || refreshFailed || !editable || !uploadFiles.length) return;
    busy = true; readingUpload = true; buttons(); uploadFeedback.clearInlineAlert();
    let uploaded = false;
    try {
      if (uploadFiles.length > 100 || uploadFiles.some(({ file }) => file.size > 65536)
        || uploadFiles.reduce((sum, { file }) => sum + file.size, 0) > 1048576) {
        throw new Error('1ファイル64 KiB・100ファイル・コード合計1 MiB以内にしてください。');
      }
      if (!uploadPreview) {
        const files = [];
        for (const { file, path } of uploadFiles) {
          const bytes = new Uint8Array(await file.arrayBuffer());
          try { new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
          catch (error) { throw new Error(`${path}: UTF-8のファイルを選択してください。`); }
          let binary = '';
          for (const byte of bytes) binary += String.fromCharCode(byte);
          files.push({ path, content: btoa(binary) });
        }
        uploadPayload = { files: JSON.stringify(files), expectedVersion: version,
          parentEntryId: document.querySelector('#uploadParentEntry').value };
        uploadPreview = await post('upload-preview', uploadPayload);
        renderUploadConflicts(uploadPreview.conflicts);
        if (!uploadPreview.conflicts.length) {
          document.querySelector('#uploadResolutionItems').textContent = `${files.length}ファイルを追加します。同名の項目はありません。`;
        }
        uploadConfirm.textContent = '確認して追加';
        return;
      }
      const resolutions = [];
      for (const row of document.querySelectorAll('#uploadResolutionItems [data-path]')) {
        const covered = resolutions.some((resolution) => (resolution.action === 'skip' || resolution.action === 'rename')
          && row.dataset.path.startsWith(`${resolution.path}/`));
        if (covered) continue;
        const action = row.querySelector('[data-upload-action]').value;
        if (!action) throw new Error(`${row.dataset.path}: 解決方法を選んでください。`);
        const resolution = { path: row.dataset.path, action };
        if (action === 'rename') resolution.name = row.querySelector('[data-upload-name]').value;
        resolutions.push(resolution);
      }
      const values = { ...uploadPayload, resolutions: JSON.stringify(resolutions) };
      const result = await post('upload', values);
      uploaded = result.status !== 'skipped';
      readingUpload = false; buttons(); closeCommittedModal(uploadModal);
      if (result.status === 'skipped') {
        feedback.toast({ message: 'すべて追加しない指定のため、変更はありません。', variant: 'warning' });
      } else {
        await refreshAfterMutation(result, null,
          `${result.addedFileCount}ファイルを追加しました。${result.skippedFileCount ? ` ${result.skippedFileCount}ファイルは追加しませんでした。` : ''}`);
      }
      uploadFiles = [];
      resetUploadPreview();
    } catch (error) {
      uploadFeedback.inlineAlert(`${error.message}${uploaded ? ' 保存は完了しています。再送しないでください。' : ' ファイルの選択は残しています。'}`, 'danger');
    } finally { busy = false; readingUpload = false; buttons(); }
  });
  document.querySelector('#exerciseScope').addEventListener('change', async (event) => {
    const selection = event.target.value;
    event.target.value = page.dataset.exerciseId;
    await navigate(fileUrl(selection));
  });
  window.addEventListener('popstate', async (event) => {
    if (restoringHistory) { restoringHistory = false; return; }
    const target = event.state?.exercisePosition;
    if (!Number.isInteger(target)) return;
    const previous = historyPosition;
    const url = new URL(window.location.href);
    const success = await selectFile(url.searchParams.get('exerciseId'),
      url.searchParams.get('entryId'), true);
    if (success) {
      historyPosition = target;
    } else if (target !== previous) {
      restoringHistory = true;
      history.go(previous - target);
    }
  });
  document.querySelectorAll('a[href]').forEach((link) => link.addEventListener('click', (event) => {
    if ((!isDirty() && !busy) || link.target === '_blank' || link.getAttribute('href').startsWith('#')) return;
    event.preventDefault(); navigate(link.href);
  }));
  document.querySelectorAll('nav form[action]').forEach((form) => form.addEventListener('submit', async (event) => {
    if (!isDirty() && !busy) return;
    event.preventDefault();
    if (await mayLeave()) { leaving = true; form.submit(); }
  }));
  window.addEventListener('beforeunload', (event) => {
    if (!leaving && (isDirty() || busy)) { event.preventDefault(); event.returnValue = ''; }
  });
  window.addEventListener('pagehide', () => {
    if (sessionId) navigator.sendBeacon(`${page.dataset.baseUrl}/session/cancel`, new URLSearchParams({
      csrfToken: document.querySelector('#csrfToken').value, exerciseId: page.dataset.exerciseId,
      entryId: page.dataset.entryId, sessionId
    }));
  });
  editor.on('change', updateStatus);
  const restored = document.querySelector('#savedExecution');
  if (restored) {
    const result = {
      status: restored.dataset.status, standardOutputTruncated: restored.dataset.outputTruncated === 'true',
      standardErrorTruncated: restored.dataset.errorTruncated === 'true', errorCode: restored.dataset.errorCode,
      standardOutput: restored.querySelector('[data-stream="stdout"]').textContent,
      standardError: restored.querySelector('[data-stream="stderr"]').textContent
    };
    showSavedExecution({ result, executedAt: restored.dataset.executedAt,
      standardInput: restored.querySelector('[data-stream="stdin"]').textContent });
  }
  const fileTab = document.querySelector('.lesson-file-tab');
  fileTab.tabIndex = 0;
  fullName(fileTab, index.get(page.dataset.entryId)?.name || '');
  renderTree(); updateStatus();
});
