window.addEventListener('DOMContentLoaded', () => {
  const RECYCLE_BIN_ID = 'folder-deleted';
  const ROOT_FOLDER_ID = 'folder-root';
  const ROOT_FOLDER_NAME = 's001';
  const ROOT_FOLDER_LABEL = '最上位';
  const MAX_ENTRY_NAME_LENGTH = 100;
  const MAX_ENTRY_LABEL_LENGTH = 20;
  const ENTRY_LABEL_ELLIPSIS = '…';
  const MAX_FOLDER_DEPTH = 5;
  const { header, footer } = window.PPEComponents || {};
  const feedback = window.PPEFeedback || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const lessonFileTree = document.querySelector('#lessonFileTree');
  const pathBadge = document.querySelector('#currentPathBadge');
  const currentFileTab = document.querySelector('#currentFileTab');
  const lessonCodeEditor = document.querySelector('#lessonCodeEditor');
  const lessonEditorMessage = document.querySelector('#lessonEditorMessage');
  const lastSavedAt = document.querySelector('#lastSavedAt');
  const newFolderButton = document.querySelector('#newFolderButton');
  const newFileButton = document.querySelector('#newFileButton');
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const runResultModalElement = document.querySelector('#runResultModal');
  const moveEntryModalElement = document.querySelector('#moveEntryModal');
  const moveEntryModalTree = document.querySelector('#moveEntryModalTree');
  const moveEntryModalEmpty = document.querySelector('#moveEntryModalEmpty');
  const moveEntryModalCurrentPath = document.querySelector('#moveEntryModalCurrentPath');
  const moveEntryConfirmButton = document.querySelector('#moveEntryConfirmButton');
  const outputConsole = document.querySelector('#outputConsole');
  const errorConsole = document.querySelector('#errorConsole');
  const pageFeedback = typeof feedback.createPageFeedback === 'function'
    ? feedback.createPageFeedback({ title: '授業演習' })
    : null;
  const runResultModal = (typeof bootstrap !== 'undefined' && runResultModalElement)
    ? bootstrap.Modal.getOrCreateInstance(runResultModalElement)
    : null;
  const moveEntryModal = (typeof bootstrap !== 'undefined' && moveEntryModalElement)
    ? bootstrap.Modal.getOrCreateInstance(moveEntryModalElement)
    : null;

  const lessonState = [
    {
      id: ROOT_FOLDER_ID,
      type: 'folder',
      name: ROOT_FOLDER_NAME,
      isRoot: true,
      children: [
        {
          id: 'folder-warmup',
          type: 'folder',
          name: 'ウォームアップ',
          children: [
            {
              id: 'file-greeting',
              type: 'file',
              name: 'greeting.py',
              path: 'ウォームアップ/greeting.py',
              content: 'name = input("名前を入力してください: ")\nprint(f"こんにちは、{name}さん")'
            },
            {
              id: 'file-repeat',
              type: 'file',
              name: 'repeat.py',
              path: 'ウォームアップ/repeat.py',
              content: 'for count in range(3):\n    print("practice", count + 1)'
            }
          ]
        },
        {
          id: 'folder-class',
          type: 'folder',
          name: '授業メモ',
          children: [
            {
              id: 'file-notes',
              type: 'file',
              name: 'notes.py',
              path: '授業メモ/notes.py',
              content: '# 今日の気づきをメモ\nkeywords = ["input", "for", "if"]\nprint(keywords)'
            }
          ]
        },
        {
          id: RECYCLE_BIN_ID,
          type: 'folder',
          name: '削除済み',
          isSystem: true,
          children: []
        }
      ]
    }
  ];

  let codeMirrorEditor = null;
  let outputEditor = null;
  let errorEditor = null;
  let currentFileId = 'file-greeting';
  let currentSelectionId = currentFileId;
  let pendingMoveDestinationPath = null;
  const collapsedFolderIds = new Set([RECYCLE_BIN_ID]);

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  if (lessonCodeEditor && typeof CodeMirror !== 'undefined') {
    codeMirrorEditor = CodeMirror.fromTextArea(lessonCodeEditor, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      theme: 'material-darker',
      indentUnit: 4,
      tabSize: 4,
      viewportMargin: Infinity
    });
  }

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

  outputEditor = createReadOnlyConsole(outputConsole);
  errorEditor = createReadOnlyConsole(errorConsole);

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

  function nowTimeLabel() {
    return new Date().toLocaleTimeString('ja-JP', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function shortenEntryLabel(name) {
    if (name.length <= MAX_ENTRY_LABEL_LENGTH) {
      return name;
    }

    const extensionIndex = name.lastIndexOf('.');
    const hasShortExtension = extensionIndex > 0 && (name.length - extensionIndex) <= 6;

    if (!hasShortExtension) {
      return `${name.slice(0, MAX_ENTRY_LABEL_LENGTH - ENTRY_LABEL_ELLIPSIS.length)}${ENTRY_LABEL_ELLIPSIS}`;
    }

    const extension = name.slice(extensionIndex);
    const availableLength = Math.max(4, MAX_ENTRY_LABEL_LENGTH - extension.length - ENTRY_LABEL_ELLIPSIS.length);
    return `${name.slice(0, availableLength)}${ENTRY_LABEL_ELLIPSIS}${extension}`;
  }

  function promptEntryName(message, entryLabel) {
    const value = window.prompt(`${message}\n${entryLabel}は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。`);
    if (value === null) {
      return null;
    }

    const normalized = value.trim();
    if (!normalized) {
      setMessage(`${entryLabel}名を入力してください。`, 'warning');
      return '';
    }

    if (normalized.length > MAX_ENTRY_NAME_LENGTH) {
      setMessage(`${entryLabel}名は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。`, 'warning');
      return '';
    }

    return normalized;
  }

  function hasNameConflict(folder, name, ignoreEntryId = null) {
    if (!folder || !Array.isArray(folder.children)) {
      return false;
    }

    return folder.children.some((child) => child.name === name && child.id !== ignoreEntryId);
  }

  function getEntryDepth(entry) {
    if (!entry || !entry.path) {
      return 0;
    }

    return entry.path.split('/').length;
  }

  function getFolderRelativeDepth(entry) {
    if (!isFolder(entry) || !Array.isArray(entry.children) || entry.children.length === 0) {
      return 1;
    }

    const childDepths = entry.children.map((child) => {
      if (isFolder(child)) {
        return getFolderRelativeDepth(child) + 1;
      }

      return 1;
    });

    return Math.max(1, ...childDepths);
  }

  function canPlaceFolderUnder(parentFolder, folderEntry) {
    const parentDepth = parentFolder ? getEntryDepth(parentFolder) : 1;
    const allowedDepth = parentDepth + getFolderRelativeDepth(folderEntry);
    return allowedDepth <= MAX_FOLDER_DEPTH;
  }

  function formatDeletedAt(timestamp) {
    return new Date(timestamp).toLocaleString('ja-JP', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function createId(prefix) {
    return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
  }

  function isFolder(entry) {
    return entry?.type === 'folder';
  }

  function isFile(entry) {
    return entry?.type === 'file';
  }

  function isRecycleBin(entry) {
    return entry?.id === RECYCLE_BIN_ID;
  }

  function isRootFolder(entry) {
    return entry?.id === ROOT_FOLDER_ID;
  }

  function findEntryById(entries, entryId, parentFolder = null, parentEntries = entries) {
    for (const entry of entries) {
      if (entry.id === entryId) {
        return { entry, parentFolder, parentEntries };
      }

      if (entry.type === 'folder' && Array.isArray(entry.children)) {
        const found = findEntryById(entry.children, entryId, entry, entry.children);
        if (found) {
          return found;
        }
      }
    }

    return null;
  }

  function getAllFiles(entries, files = []) {
    entries.forEach((entry) => {
      if (isFile(entry)) {
        files.push(entry);
      }

      if (isFolder(entry) && Array.isArray(entry.children)) {
        getAllFiles(entry.children, files);
      }
    });

    return files;
  }

  function getAllFolders(entries, folders = []) {
    entries.forEach((entry) => {
      if (isFolder(entry)) {
        folders.push(entry);
      }

      if (isFolder(entry) && Array.isArray(entry.children)) {
        getAllFolders(entry.children, folders);
      }
    });

    return folders;
  }

  function updatePaths(entries, parentPath = '') {
    entries.forEach((entry) => {
      entry.path = parentPath ? `${parentPath}/${entry.name}` : entry.name;

      if (isFolder(entry) && Array.isArray(entry.children)) {
        updatePaths(entry.children, entry.path);
      }
    });
  }

  function compareEntries(left, right) {
    if (isRecycleBin(left)) {
      return 1;
    }

    if (isRecycleBin(right)) {
      return -1;
    }

    if (isFolder(left) !== isFolder(right)) {
      return isFolder(left) ? -1 : 1;
    }

    return left.name.localeCompare(right.name, 'ja');
  }

  function sortEntries(entries) {
    entries.sort(compareEntries);

    entries.forEach((entry) => {
      if (isFolder(entry) && Array.isArray(entry.children)) {
        if (isRecycleBin(entry)) {
          entry.children.sort((left, right) => (right.deletedAt || 0) - (left.deletedAt || 0));
          return;
        }

        sortEntries(entry.children);
      }
    });
  }

  function sortRecycleBin() {
    const recycleBin = findEntryById(lessonState, RECYCLE_BIN_ID)?.entry;
    if (!recycleBin || !Array.isArray(recycleBin.children)) {
      return;
    }

    recycleBin.children.sort((left, right) => (right.deletedAt || 0) - (left.deletedAt || 0));
  }

  function refreshState() {
    sortEntries(lessonState);
    updatePaths(lessonState);
    sortRecycleBin();
  }

  function getRecycleBin() {
    return findEntryById(lessonState, RECYCLE_BIN_ID)?.entry || null;
  }

  function getRootFolder() {
    return findEntryById(lessonState, ROOT_FOLDER_ID)?.entry || null;
  }

  function collectMoveTargets(entryToMove) {
    return getAllFolders(lessonState).filter((folder) => {
      if (isRecycleBin(folder)) {
        return false;
      }

      if (folder.id === entryToMove.id) {
        return false;
      }

      if (!isFolder(entryToMove)) {
        return true;
      }

      const found = findEntryById(entryToMove.children || [], folder.id, entryToMove);
      return !found;
    });
  }

  function renderMoveFolderTree(entry, validTargetIds, isRootNode = false) {
    if (!entry || !isFolder(entry)) {
      return '';
    }

    const childFolders = (entry.children || []).filter((child) => isFolder(child) && validTargetIds.has(child.id));
    const escapedName = escapeHtml(isRootNode ? ROOT_FOLDER_NAME : entry.name);
    const escapedPath = escapeHtml(isRootNode ? ROOT_FOLDER_NAME : entry.path);
    const canSelect = isRootNode || validTargetIds.has(entry.id);
    const isSelectedTarget = pendingMoveDestinationPath === (isRootNode ? ROOT_FOLDER_LABEL : entry.path);

    return `
      <div class="move-entry-folder${isRootNode ? ' is-root-node' : ''}${isSelectedTarget ? ' is-selected-target' : ''}">
        <button class="move-entry-folder-button" type="button" ${isRootNode ? 'data-move-root-target' : `data-move-target-id="${entry.id}"`} ${canSelect ? '' : 'disabled'}>
          <div class="move-entry-folder-main">
            <svg class="move-entry-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
              <path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>
            </svg>
            <span class="move-entry-folder-name">${escapedName}</span>
          </div>
          <span class="move-entry-folder-path">${escapedPath}</span>
        </button>
        ${childFolders.length > 0 ? `<div class="move-entry-children">${childFolders.map((child) => renderMoveFolderTree(child, validTargetIds)).join('')}</div>` : ''}
      </div>
    `;
  }

  function renderMoveEntryModal(entryId) {
    const entry = findEntryById(lessonState, entryId)?.entry;
    if (!entry) {
      return;
    }

    const validTargets = collectMoveTargets(entry);
    const validTargetIds = new Set(validTargets.map((folder) => folder.id));
    const rootFolder = getRootFolder();
    const canMoveToRoot = isFolder(entry) ? canPlaceFolderUnder(rootFolder, entry) : true;

    if (moveEntryModalCurrentPath) {
      moveEntryModalCurrentPath.textContent = `現在: ${entry.path}`;
    }

    if (moveEntryModalTree) {
      moveEntryModalTree.innerHTML = rootFolder ? renderMoveFolderTree(rootFolder, validTargetIds, true) : '';
    }

    if (moveEntryModalEmpty) {
      moveEntryModalEmpty.classList.toggle('d-none', Boolean(validTargetIds.size || canMoveToRoot));
    }

    if (moveEntryConfirmButton) {
      moveEntryConfirmButton.disabled = !pendingMoveDestinationPath;
    }
  }

  function openMoveEntryModal(entryId) {
    const found = findEntryById(lessonState, entryId);
    if (!found) {
      return;
    }

    pendingMoveEntryId = entryId;
    pendingMoveDestinationPath = null;
    renderMoveEntryModal(entryId);

    if (moveEntryModal) {
      moveEntryModal.show();
      return;
    }

    setMessage('移動先モーダルを表示できませんでした。', 'warning');
  }

  function closeMoveEntryModal() {
    pendingMoveEntryId = null;
    pendingMoveDestinationPath = null;

    if (moveEntryModalTree) {
      moveEntryModalTree.innerHTML = '';
    }

    if (moveEntryModalEmpty) {
      moveEntryModalEmpty.classList.add('d-none');
    }
  }

  function insertEntryAtRoot(entry) {
    const rootFolder = getRootFolder();
    if (!rootFolder || !Array.isArray(rootFolder.children)) {
      return;
    }

    const recycleBinIndex = rootFolder.children.findIndex((item) => item.id === RECYCLE_BIN_ID);
    if (recycleBinIndex === -1) {
      rootFolder.children.push(entry);
      return;
    }

    rootFolder.children.splice(recycleBinIndex, 0, entry);
  }

  function ensureCurrentFileSelection() {
    const currentFile = getCurrentFile();
    if (currentFile) {
      return;
    }

    const activeFiles = getAllFiles(lessonState).filter((file) => !file.path.startsWith('削除済み/'));
    const fallbackFile = activeFiles[0] || getAllFiles(lessonState)[0] || null;
    currentFileId = fallbackFile?.id || null;
    if (!currentSelectionId) {
      currentSelectionId = currentFileId || ROOT_FOLDER_ID;
    }
  }

  function getSelectedEntry() {
    return currentSelectionId ? findEntryById(lessonState, currentSelectionId)?.entry || null : null;
  }

  function getSelectedFolderTarget() {
    const selectedEntry = getSelectedEntry();
    if (isFolder(selectedEntry) && !isRecycleBin(selectedEntry)) {
      return selectedEntry;
    }

    if (isFile(selectedEntry)) {
      return findEntryById(lessonState, selectedEntry.id)?.parentFolder || getRootFolder();
    }

    return getRootFolder();
  }

  function getCurrentFile() {
    const found = findEntryById(lessonState, currentFileId);
    return found?.entry || null;
  }

  function getEditorValue() {
    return codeMirrorEditor ? codeMirrorEditor.getValue() : (lessonCodeEditor?.value || '');
  }

  function setEditorValue(value) {
    if (codeMirrorEditor) {
      codeMirrorEditor.setValue(value);
      codeMirrorEditor.refresh();
      return;
    }

    if (lessonCodeEditor) {
      lessonCodeEditor.value = value;
    }
  }

  function setMessage(message, variant = 'info') {
    if (lessonEditorMessage) {
      lessonEditorMessage.textContent = message;
    }

    if (variant === 'warning' || variant === 'danger') {
      const toastVariant = variant === 'danger' ? 'danger' : 'warning';
      const toastTitle = variant === 'danger' ? 'エラー' : '警告';

      if (pageFeedback && typeof pageFeedback.toast === 'function') {
        pageFeedback.toast({
          title: toastTitle,
          message,
          variant: toastVariant
        });
      } else if (typeof feedback.showToast === 'function') {
        feedback.showToast({
          title: toastTitle,
          message,
          variant: toastVariant
        });
      }
    }
  }

  if (runResultModalElement) {
    runResultModalElement.addEventListener('shown.bs.modal', () => {
      if (outputEditor) {
        outputEditor.refresh();
      }

      if (errorEditor) {
        errorEditor.refresh();
      }
    });
  }

  if (moveEntryModalElement) {
    moveEntryModalElement.addEventListener('hidden.bs.modal', () => {
      closeMoveEntryModal();
    });

    moveEntryModalElement.addEventListener('click', (event) => {
      const rootButton = event.target.closest('[data-move-root-target]');
      if (rootButton && pendingMoveEntryId) {
        pendingMoveDestinationPath = ROOT_FOLDER_LABEL;
        renderMoveEntryModal(pendingMoveEntryId);
        return;
      }

      const targetButton = event.target.closest('[data-move-target-id]');
      if (!targetButton || !pendingMoveEntryId) {
        return;
      }

      const targetFolderId = targetButton.getAttribute('data-move-target-id');
      const targetFolder = findEntryById(lessonState, targetFolderId)?.entry;
      if (!targetFolder) {
        return;
      }

      pendingMoveDestinationPath = targetFolder.path;
      renderMoveEntryModal(pendingMoveEntryId);
    });
  }

  if (moveEntryConfirmButton) {
    moveEntryConfirmButton.addEventListener('click', () => {
      if (!pendingMoveEntryId || !pendingMoveDestinationPath) {
        return;
      }

      const movingEntry = findEntryById(lessonState, pendingMoveEntryId)?.entry;
      const moved = moveEntry(pendingMoveEntryId, pendingMoveDestinationPath);
      if (!moved) {
        setMessage('移動先に移動できませんでした。', 'warning');
        return;
      }

      ensureCurrentFileSelection();
      renderFileTree(lessonState);
      updateActiveFileUI();
      if (movingEntry) {
        setMessage(`「${movingEntry.name}」を移動しました。`, 'success');
      }
      if (moveEntryModal) {
        moveEntryModal.hide();
      }
    });
  }

  function saveCurrentFile(showMessage = true) {
    const file = getCurrentFile();
    if (!file || !isFile(file)) {
      return;
    }

    file.content = getEditorValue();
    const time = nowTimeLabel();

    if (lastSavedAt) {
      lastSavedAt.textContent = time;
    }

    if (showMessage) {
      setMessage(`「${file.name}」を保存しました。`, 'success');
    }
  }

  function updateActiveFileUI() {
    const file = getCurrentFile();
    if (!file || !isFile(file)) {
      if (pathBadge) {
        pathBadge.textContent = 'Path: -';
      }

      if (currentFileTab) {
        currentFileTab.textContent = 'ファイル未選択';
      }

      setEditorValue('');
      return;
    }

    if (pathBadge) {
      pathBadge.textContent = `Path: ${file.path}`;
    }

    if (currentFileTab) {
      currentFileTab.textContent = file.name;
    }

    setEditorValue(file.content);
  }

  function renderFileButton(file) {
    const isActive = file.id === currentFileId;
    const isSelected = file.id === currentSelectionId;
    const escapedName = escapeHtml(file.name);
    const escapedDisplayName = escapeHtml(shortenEntryLabel(file.name));
    const deletedMeta = file.deletedAt
      ? `<span class="lesson-tree-entry-meta">削除: ${formatDeletedAt(file.deletedAt)}</span>`
      : '';
    const menuItems = file.deletedAt
      ? `<li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="move" data-entry-id="${file.id}">移動</button></li>`
      : `
          <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="move" data-entry-id="${file.id}">移動</button></li>
          <li><button class="dropdown-item lesson-tree-menu-item text-danger" type="button" data-action="delete" data-entry-id="${file.id}">削除</button></li>
        `;

    return `
      <div class="lesson-tree-entry-row${isActive ? ' is-active-row' : ''}${isSelected ? ' is-selected-row' : ''}">
        <button class="lesson-tree-file-button${isActive ? ' is-active' : ''}" type="button" data-file-id="${file.id}">
          <svg class="lesson-tree-file-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path d="M3.75 1.5A1.75 1.75 0 0 0 2 3.25v9.5c0 .966.784 1.75 1.75 1.75h8.5A1.75 1.75 0 0 0 14 12.75V5.56a1.75 1.75 0 0 0-.513-1.237L10.677 1.513A1.75 1.75 0 0 0 9.44 1H3.75zm5.5 1.06c.133.035.255.103.354.202l2.634 2.634a.75.75 0 0 1 .202.354H9.75a.5.5 0 0 1-.5-.5V2.56z"/>
          </svg>
          <span class="lesson-tree-file-copy">
            <span class="lesson-tree-file-name" title="${escapedName}">${escapedDisplayName}</span>
            ${deletedMeta}
          </span>
        </button>
        <div class="dropdown lesson-tree-entry-menu">
          <button class="btn btn-outline-secondary btn-sm lesson-tree-menu-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="${escapedName} の操作メニュー">
            ⋮
          </button>
          <ul class="dropdown-menu dropdown-menu-end lesson-tree-menu-dropdown">
            ${menuItems}
          </ul>
        </div>
      </div>
    `;
  }

  function renderFolder(entry) {
    const escapedName = escapeHtml(entry.name);
    const escapedDisplayName = escapeHtml(shortenEntryLabel(entry.name));
    const isCollapsed = collapsedFolderIds.has(entry.id);
    const isSelected = entry.id === currentSelectionId;
    const isSystemFolder = Boolean(entry.isSystem);
    const isRoot = isRootFolder(entry);
    const deletedMeta = entry.deletedAt
      ? `<span class="lesson-tree-entry-meta">削除: ${formatDeletedAt(entry.deletedAt)}</span>`
      : '';
    const menuItems = isSystemFolder
      ? ''
      : entry.deletedAt
        ? `<li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="move" data-entry-id="${entry.id}">移動</button></li>`
        : `
            <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="add-folder" data-entry-id="${entry.id}">フォルダ追加</button></li>
            <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="add-file" data-entry-id="${entry.id}">ファイル追加</button></li>
            ${isRoot ? '' : `<li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="move" data-entry-id="${entry.id}">移動</button></li>`}
            ${isRoot ? '' : `<li><button class="dropdown-item lesson-tree-menu-item text-danger" type="button" data-action="delete" data-entry-id="${entry.id}">削除</button></li>`}
          `;
    const actionMenu = isSystemFolder
      ? ''
      : `
          <div class="dropdown lesson-tree-entry-menu">
            <button class="btn btn-outline-secondary btn-sm lesson-tree-menu-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="${entry.name} の操作メニュー">
              ⋮
            </button>
            <ul class="dropdown-menu dropdown-menu-end lesson-tree-menu-dropdown">
              ${menuItems}
            </ul>
          </div>
        `;

    return `
      <div class="lesson-tree-folder">
        <div class="lesson-tree-folder-header${isSelected ? ' is-selected-folder' : ''}">
          <div class="lesson-tree-folder-title" data-select-folder="${entry.id}" role="button" tabindex="0" aria-label="${escapedName} を選択">
            <button class="lesson-tree-folder-toggle${isCollapsed ? ' is-collapsed' : ''}" type="button" data-toggle-folder="${entry.id}" aria-expanded="${isCollapsed ? 'false' : 'true'}" aria-label="${escapedName} を${isCollapsed ? '展開' : '折りたたみ'}">
              <span class="lesson-tree-folder-toggle-icon">▾</span>
            </button>
            <svg class="lesson-tree-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
              <path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>
            </svg>
            <span class="lesson-tree-folder-name" title="${escapedName}">${escapedDisplayName}</span>
            ${deletedMeta}
          </div>
          ${actionMenu}
        </div>
        <div class="lesson-tree-folder-children${isCollapsed ? ' is-collapsed' : ''}">
          ${(entry.children || []).map((child) => isFolder(child) ? renderFolder(child) : renderFileButton(child)).join('')}
        </div>
      </div>
    `;
  }

  function renderFileTree(entries) {
    if (!lessonFileTree) {
      return;
    }

    lessonFileTree.innerHTML = entries.map((entry) => isFolder(entry) ? renderFolder(entry) : renderFileButton(entry)).join('');
  }

  function appendFolder(name, parentFolderId = null) {
    const folderName = name.trim();
    const parentFolder = parentFolderId
      ? findEntryById(lessonState, parentFolderId)?.entry
      : getRootFolder();

    if (!canPlaceFolderUnder(parentFolder, { type: 'folder', children: [] })) {
      setMessage(`フォルダは ${MAX_FOLDER_DEPTH} 階層まで作成できます。`, 'warning');
      return false;
    }

    if (hasNameConflict(parentFolder, folderName)) {
      setMessage(`「${folderName}」はこの場所にすでに存在します。別の名前を入力してください。`, 'warning');
      return false;
    }

    const folderEntry = {
      id: createId('folder'),
      type: 'folder',
      name: folderName,
      children: []
    };

    if (parentFolder && isFolder(parentFolder)) {
      parentFolder.children.push(folderEntry);
    } else {
      insertEntryAtRoot(folderEntry);
    }

    refreshState();
    renderFileTree(lessonState);
    return true;
  }

  function appendFile(name, parentFolderId = null) {
    const fileName = name.endsWith('.py') ? name : `${name}.py`;
    const defaultParent = findEntryById(lessonState, currentFileId)?.parentFolder || lessonState.find((entry) => isFolder(entry) && !isRecycleBin(entry)) || null;
    const parentFolder = parentFolderId
      ? findEntryById(lessonState, parentFolderId)?.entry
      : defaultParent;

    if (hasNameConflict(parentFolder, fileName)) {
      setMessage(`「${fileName}」はこの場所にすでに存在します。別の名前を入力してください。`, 'warning');
      return false;
    }

    const newFile = {
      id: createId('file'),
      type: 'file',
      name: fileName,
      content: 'print("new lesson file")'
    };

    if (parentFolder && isFolder(parentFolder)) {
      parentFolder.children.push(newFile);
    } else {
      insertEntryAtRoot(newFile);
    }

    currentFileId = newFile.id;
    refreshState();
    renderFileTree(lessonState);
    updateActiveFileUI();
    return true;
  }

  function buildMovePromptMessage(entry) {
    const candidates = collectMoveTargets(entry);
    const optionLines = candidates.map((folder) => `- ${folder.path}`).join('\n');
    const rootLine = isFolder(entry) ? `- ${ROOT_FOLDER_LABEL}\n` : '';

    return [
      `移動先を入力してください。`,
      `現在: ${entry.path}`,
      '',
      '選択可能な移動先:',
      `${rootLine}${optionLines}`
    ].join('\n');
  }

  function moveEntry(entryId, destinationPath) {
    const found = findEntryById(lessonState, entryId);
    if (!found) {
      return false;
    }

    const { entry, parentEntries } = found;
    const currentIndex = parentEntries.findIndex((item) => item.id === entry.id);
    if (currentIndex === -1) {
      return false;
    }

    parentEntries.splice(currentIndex, 1);

    if (destinationPath === ROOT_FOLDER_LABEL) {
      const rootFolder = getRootFolder();
      if (hasNameConflict(rootFolder, entry.name, entry.id)) {
        setMessage(`「${entry.name}」はこの場所にすでに存在します。別の名前にしてから移動してください。`, 'warning');
        parentEntries.splice(currentIndex, 0, entry);
        return false;
      }

      if (isFolder(entry) && !canPlaceFolderUnder(rootFolder, entry)) {
        setMessage(`フォルダは ${MAX_FOLDER_DEPTH} 階層までです。`, 'warning');
        parentEntries.splice(currentIndex, 0, entry);
        return false;
      }

      entry.deletedAt = null;
      insertEntryAtRoot(entry);
      refreshState();
      return true;
    }

    const destinationFolder = getAllFolders(lessonState).find((folder) => folder.path === destinationPath);
    if (!destinationFolder) {
      parentEntries.splice(currentIndex, 0, entry);
      return false;
    }

    if (hasNameConflict(destinationFolder, entry.name, entry.id)) {
      setMessage(`「${entry.name}」は移動先にすでに存在します。別の名前にしてから移動してください。`, 'warning');
      parentEntries.splice(currentIndex, 0, entry);
      return false;
    }

    if (isFolder(entry) && !canPlaceFolderUnder(destinationFolder, entry)) {
      setMessage(`フォルダは ${MAX_FOLDER_DEPTH} 階層までです。`, 'warning');
      parentEntries.splice(currentIndex, 0, entry);
      return false;
    }

    entry.deletedAt = null;
    destinationFolder.children.push(entry);
    refreshState();
    return true;
  }

  async function deleteEntry(entryId) {
    const found = findEntryById(lessonState, entryId);
    const recycleBin = getRecycleBin();

    if (!found || !recycleBin || found.entry.deletedAt || isRecycleBin(found.entry)) {
      return;
    }

    const confirmed = pageFeedback
      ? await pageFeedback.danger({
          title: '削除しますか？',
          message: '削除した項目は「削除済み」フォルダへ移動します。',
          detailTitle: '',
          details: [found.entry.path],
          confirmLabel: '削除する',
          cancelLabel: '戻る'
        })
      : window.confirm(`「${found.entry.name}」を削除しますか？`);

    if (!confirmed) {
      return;
    }

    const index = found.parentEntries.findIndex((item) => item.id === found.entry.id);
    if (index === -1) {
      return;
    }

    found.parentEntries.splice(index, 1);
    found.entry.deletedAt = Date.now();
    recycleBin.children.push(found.entry);
    refreshState();
    ensureCurrentFileSelection();
    renderFileTree(lessonState);
    updateActiveFileUI();
    setMessage(`「${found.entry.name}」を削除済みフォルダへ移動しました。`, 'warning');
  }

  async function handleTreeAction(action, entryId) {
    const found = findEntryById(lessonState, entryId);
    if (!found) {
      return;
    }

    if (action === 'add-folder') {
      const name = window.prompt('作成するフォルダ名を入力してください。');
      if (!name) {
        return;
      }

      if (appendFolder(name.trim(), entryId)) {
        setMessage(`フォルダ「${name.trim()}」を追加しました。`, 'success');
      }
      return;
    }

    if (action === 'add-file') {
      const name = window.prompt('作成するファイル名を入力してください。\n拡張子 .py は自動的に追加されます。');
      if (!name) {
        return;
      }

      if (appendFile(name.trim(), entryId)) {
        setMessage(`ファイル「${name.trim()}」を追加しました。`, 'success');
      }
      return;
    }

    if (action === 'move') {
      openMoveEntryModal(entryId);
      return;
    }

    if (action === 'delete') {
      await deleteEntry(entryId);
    }
  }

  function runCurrentCode() {
    saveCurrentFile(false);
    const code = getEditorValue();
    const file = getCurrentFile();

    let stdout = '';
    let stderr = 'エラーはありません。';

    if (/syntaxerror/i.test(code) || /raise\s+SyntaxError/.test(code)) {
      stderr = 'Traceback (most recent call last):\n  File "exercise.py", line 1\nSyntaxError: invalid syntax';
      stdout = '';
    } else if (/input\(/.test(code)) {
      stdout = `${file?.name || 'exercise.py'} を実行しました。\n入力待ちのコードを含むため、ここではサンプル出力を表示しています。`;
    } else if (/print\(/.test(code)) {
      stdout = `${file?.name || 'exercise.py'} を実行しました。\nprint 文を含むコードのため、標準出力のサンプルを表示しています。`;
    } else {
      stdout = `${file?.name || 'exercise.py'} を実行しました。\n標準出力はありません。`;
    }

    setConsoleValue(outputEditor, outputConsole, stdout || '標準出力はありません。');
    setConsoleValue(errorEditor, errorConsole, stderr);

    if (lessonEditorMessage) {
      lessonEditorMessage.textContent = '実行が完了しました。実行結果モーダルを確認してください。';
    }

    if (runResultModal) {
      runResultModal.show();
    }
  }

  lessonFileTree?.addEventListener('click', async (event) => {
    const toggleButton = event.target.closest('[data-toggle-folder]');
    if (toggleButton) {
      event.preventDefault();
      event.stopPropagation();
      const folderId = toggleButton.getAttribute('data-toggle-folder');
      if (collapsedFolderIds.has(folderId)) {
        collapsedFolderIds.delete(folderId);
      } else {
        collapsedFolderIds.add(folderId);
      }
      renderFileTree(lessonState);
      return;
    }

    const actionButton = event.target.closest('[data-action]');
    if (actionButton) {
      event.preventDefault();
      event.stopPropagation();
      await handleTreeAction(actionButton.getAttribute('data-action'), actionButton.getAttribute('data-entry-id'));
      return;
    }

    const folderSelector = event.target.closest('[data-select-folder]');
    if (folderSelector) {
      event.preventDefault();
      currentSelectionId = folderSelector.getAttribute('data-select-folder');
      renderFileTree(lessonState);
      setMessage('フォルダを選択しました。新しいファイルやフォルダはこの配下に作成されます。', 'info');
      return;
    }

    const fileButton = event.target.closest('[data-file-id]');
    if (!fileButton) {
      return;
    }

    saveCurrentFile(false);
    currentFileId = fileButton.getAttribute('data-file-id');
    currentSelectionId = currentFileId;
    renderFileTree(lessonState);
    updateActiveFileUI();
    setMessage('ファイルを切り替えました。', 'info');
  });

  if (newFolderButton) {
    newFolderButton.addEventListener('click', () => {
      const name = promptEntryName('作成するフォルダ名を入力してください。', 'フォルダ');
      if (!name) {
        return;
      }

      const targetFolder = getSelectedFolderTarget();
      if (appendFolder(name, targetFolder?.id || null)) {
        setMessage(`フォルダ「${name}」を追加しました。`, 'success');
      }
    });
  }

  if (newFileButton) {
    newFileButton.addEventListener('click', () => {
      const name = promptEntryName('作成するファイル名を入力してください。\n拡張子 .py は自動的に追加されます。', 'ファイル');
      if (!name) {
        return;
      }

      const targetFolder = getSelectedFolderTarget();
      if (appendFile(name, targetFolder?.id || null)) {
        setMessage(`ファイル「${name}」を追加しました。`, 'success');
      }
    });
  }

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      saveCurrentFile(true);
    });
  }

  if (runButton) {
    runButton.addEventListener('click', () => {
      runCurrentCode();
    });
  }

  refreshState();
  renderFileTree(lessonState);
  ensureCurrentFileSelection();
  currentSelectionId = currentSelectionId || currentFileId || ROOT_FOLDER_ID;
  updateActiveFileUI();
  setConsoleValue(outputEditor, outputConsole, '実行待ちです。「実行」を押すと結果を表示します。');
  setConsoleValue(errorEditor, errorConsole, 'エラーはありません。');
});
