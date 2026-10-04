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
  function labelControl(button, action, text) {
    const label = document.createElement('span');
    label.className = 'lesson-control-label'; label.textContent = text;
    button.replaceChildren(label); button.insertAdjacentHTML('afterbegin', controlIcon(action));
  }
  const controlIcons = {
    newFolderButton: 'folder', newFileButton: 'file', expandAllFoldersButton: 'expand',
    collapseAllFoldersButton: 'collapse', revealActiveFileButton: 'reveal',
    lessonSelectionModeButton: 'select', lessonSelectAll: 'select', lessonSelectSearchResults: 'select',
    downloadSelectedButton: 'download', moveSelectedButton: 'move',
    trashSelectedButton: 'trash', clearSelectedButton: 'clear'
  };
  for (const [id, action] of Object.entries(controlIcons)) {
    const button = document.getElementById(id);
    labelControl(button, action, button.textContent);
  }
  const RECYCLE_BIN_ID = 'folder-deleted';
  const ROOT_FOLDER_ID = 'folder-root';
  const ROOT_FOLDER_NAME = 's001';
  const ROOT_FOLDER_LABEL = 'ルート';
  const MAX_ENTRY_NAME_LENGTH = 255;
  const MAX_ENTRY_PATH_LENGTH = 1000;
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
  new bootstrap.Tooltip(document.body, {
    selector: '[data-exercise-full-name]', container: 'body', trigger: 'hover focus', animation: false,
    customClass: 'exercise-name-tooltip', title() { return this.dataset.exerciseFullName; }
  });
  new bootstrap.Tooltip(document.querySelector('#uploadButton'), { container: 'body', animation: false });
  function clearNameTooltips(container) {
    container.querySelectorAll('[data-exercise-full-name]').forEach((target) => {
      bootstrap.Tooltip.getInstance(target)?.dispose();
    });
  }
  const lessonCodeEditor = document.querySelector('#lessonCodeEditor');
  const lessonEditorMessage = document.querySelector('#lessonEditorMessage');
  const editorFontSizeInput = document.querySelector('#editorFontSize');
  const editorFontSizeValue = document.querySelector('#editorFontSizeValue');
  const editorLineWrappingInput = document.querySelector('#editorLineWrapping');
  const editorIndentWidthInput = document.querySelector('#editorIndentWidth');
  const editorThemeInput = document.querySelector('#editorTheme');
  const editorPreferencesStatus = document.querySelector('#editorPreferencesStatus');
  const resetEditorPreferencesButton = document.querySelector('#resetEditorPreferences');
  const lastSavedAt = document.querySelector('#lastSavedAt');
  const newFolderButton = document.querySelector('#newFolderButton');
  const newFileButton = document.querySelector('#newFileButton');
  const uploadButton = document.querySelector('#uploadButton');
  const downloadButton = document.querySelector('#downloadButton');
  const addTargetPath = document.querySelector('#lessonAddTargetPath');
  const treeSearchInput = document.querySelector('#lessonTreeSearch');
  const treeSortSelect = document.querySelector('#lessonTreeSort');
  const displaySettingsStatus = document.querySelector('#lessonDisplaySettingsStatus');
  const searchResults = document.querySelector('#lessonTreeSearchResults');
  const selectionModeButton = document.querySelector('#lessonSelectionModeButton');
  const selectionToolbar = document.querySelector('#lessonSelectionToolbar');
  const selectionCount = document.querySelector('#lessonSelectionCount');
  const hiddenSelectionCount = document.querySelector('#lessonHiddenSelectionCount');
  const downloadSelectedButton = document.querySelector('#downloadSelectedButton');
  const moveSelectedButton = document.querySelector('#moveSelectedButton');
  const trashSelectedButton = document.querySelector('#trashSelectedButton');
  const clearSelectedButton = document.querySelector('#clearSelectedButton');
  const revealActiveFileButton = document.querySelector('#revealActiveFileButton');
  const collapseAllFoldersButton = document.querySelector('#collapseAllFoldersButton');
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const runResultStatus = document.querySelector('#lessonRunResultStatus');
  const runResultTime = document.querySelector('#lessonRunResultTime');
  const outputTabs = document.querySelectorAll('#lessonExecutionResultContent [data-output-target]');
  const stderrTab = document.querySelector('#lessonStderrTab');
  const stderrIndicator = document.querySelector('#lessonStderrIndicator');
  const uploadEntryModalElement = document.querySelector('#uploadEntryModal');
  const uploadSelectFilesButton = document.querySelector('#uploadSelectFilesButton');
  const uploadSelectFolderButton = document.querySelector('#uploadSelectFolderButton');
  const uploadSourceFile = document.querySelector('#uploadSourceFile');
  const uploadSourceDirectory = document.querySelector('#uploadSourceDirectory');
  const uploadFileName = document.querySelector('#uploadFileName');
  const uploadFilePreviewList = document.querySelector('#uploadFilePreviewList');
  const uploadFolderTree = document.querySelector('#uploadFolderTree');
  const uploadEntryConfirmButton = document.querySelector('#uploadEntryConfirmButton');
  const moveEntryModalElement = document.querySelector('#moveEntryModal');
  const moveEntryModalTree = document.querySelector('#moveEntryModalTree');
  const moveEntryModalEmpty = document.querySelector('#moveEntryModalEmpty');
  const moveEntryModalCurrentPath = document.querySelector('#moveEntryModalCurrentPath');
  const moveEntryModalSelectedPath = document.querySelector('#moveEntryModalSelectedPath');
  const moveEntryConfirmButton = document.querySelector('#moveEntryConfirmButton');
  const outputConsole = document.querySelector('#outputConsole');
  const errorConsole = document.querySelector('#errorConsole');
  const defaultEditorPreferences = {
    fontSizePx: 16,
    lineWrapping: false,
    indentWidth: 4,
    theme: 'dark'
  };
  const validThemes = ['dark', 'light', 'high_contrast'];
  const preferenceStorageKey = 'ppe.editorPreferences.demo-student';
  let editorPreferences = { ...defaultEditorPreferences };
  let preferenceStorageError = false;
  try {
    const storedPreferences = JSON.parse(localStorage.getItem(preferenceStorageKey) || 'null');
    if (storedPreferences && Number.isInteger(storedPreferences.fontSizePx)
      && storedPreferences.fontSizePx >= 10 && storedPreferences.fontSizePx <= 24
      && storedPreferences.fontSizePx % 2 === 0
      && typeof storedPreferences.lineWrapping === 'boolean'
      && [2, 4].includes(storedPreferences.indentWidth)
      && validThemes.includes(storedPreferences.theme)) {
      editorPreferences = storedPreferences;
    }
  } catch {
    preferenceStorageError = true;
  }
  const pageFeedback = typeof feedback.createPageFeedback === 'function'
    ? feedback.createPageFeedback({ title: '授業演習' })
    : null;
  const uploadEntryModal = (typeof bootstrap !== 'undefined' && uploadEntryModalElement)
    ? bootstrap.Modal.getOrCreateInstance(uploadEntryModalElement)
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
          updatedAt: '2026-10-03T13:40:00+09:00',
          children: [
            {
              id: 'file-greeting',
              type: 'file',
              name: 'greeting.py',
              path: 'ウォームアップ/greeting.py',
              updatedAt: '2026-10-02T09:15:00+09:00',
              content: 'name = input("名前を入力してください: ")\nprint(f"こんにちは、{name}さん")'
            },
            {
              id: 'file-repeat',
              type: 'file',
              name: 'repeat.py',
              path: 'ウォームアップ/repeat.py',
              updatedAt: '2026-10-03T13:40:00+09:00',
              content: 'for count in range(3):\n    print("practice", count + 1)'
            }
          ]
        },
        {
          id: 'folder-class',
          type: 'folder',
          name: '授業メモ',
          updatedAt: '2026-10-01T16:20:00+09:00',
          children: [
            {
              id: 'file-notes',
              type: 'file',
              name: 'notes.py',
              path: '授業メモ/notes.py',
              updatedAt: '2026-10-01T16:20:00+09:00',
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
  let currentSelectionId = ROOT_FOLDER_ID;
  let pendingMoveEntryId = null;
  let pendingMoveEntryIds = [];
  let pendingMoveDestinationPath = null;
  const expandedFolderIds = new Set([ROOT_FOLDER_ID]);
  const expandedTrashFolderIds = new Set();
  const expandedMoveFolderIds = new Set();
  const selectedEntryIds = new Set();
  const selectedTrashEntryIds = new Set();
  let selectionMode = false;
  let trashSelectionMode = false;
  let trashSelectionToolsOpen = false;
  let selectionAnchorId = null;
  let trashSelectionAnchorId = null;
  let treeSearchTerm = '';
  let trashSearchTerm = '';
  let treeSortMode = 'name';
  let trashDisplayLimit = 10;
  let pendingUploadFolderId = ROOT_FOLDER_ID;
  let pendingUploadItems = [];
  let pendingUploadSourceMode = '';
  let pendingUploadExcludedCount = 0;
  const MAX_UPLOAD_PREVIEW_ITEMS = 12;

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
      lineWrapping: editorPreferences.lineWrapping,
      theme: {
        dark: 'material-darker',
        light: 'default',
        high_contrast: 'ppe-high-contrast'
      }[editorPreferences.theme],
      indentUnit: editorPreferences.indentWidth,
      tabSize: editorPreferences.indentWidth,
      viewportMargin: Infinity
    });
    codeMirrorEditor.getWrapperElement().style.fontSize = `${editorPreferences.fontSizePx}px`;
  }

  function applyEditorPreferences() {
    codeMirrorEditor?.setOption('lineWrapping', editorPreferences.lineWrapping);
    codeMirrorEditor?.setOption('indentUnit', editorPreferences.indentWidth);
    codeMirrorEditor?.setOption('tabSize', editorPreferences.indentWidth);
    codeMirrorEditor?.setOption('theme', {
      dark: 'material-darker',
      light: 'default',
      high_contrast: 'ppe-high-contrast'
    }[editorPreferences.theme]);
    if (codeMirrorEditor) {
      codeMirrorEditor.getWrapperElement().style.fontSize = `${editorPreferences.fontSizePx}px`;
      codeMirrorEditor.refresh();
    }
  }

  function syncMainEditorLayout() {
    if (!codeMirrorEditor) {
      return;
    }

    codeMirrorEditor.setSize(null, 'auto');
    codeMirrorEditor.refresh();
  }

  function syncEditorPreferencesControls() {
    if (editorFontSizeInput) {
      editorFontSizeInput.value = String(editorPreferences.fontSizePx);
    }
    if (editorFontSizeValue) {
      editorFontSizeValue.textContent = `${editorPreferences.fontSizePx}px`;
    }
    if (editorLineWrappingInput) {
      editorLineWrappingInput.checked = editorPreferences.lineWrapping;
    }
    if (editorIndentWidthInput) {
      editorIndentWidthInput.value = String(editorPreferences.indentWidth);
    }
    if (editorThemeInput) {
      editorThemeInput.value = editorPreferences.theme;
    }
  }

  function saveEditorPreferences() {
    try {
      localStorage.setItem(preferenceStorageKey, JSON.stringify(editorPreferences));
      preferenceStorageError = false;
      if (editorPreferencesStatus) {
        editorPreferencesStatus.textContent = '設定を保存しました。';
      }
    } catch {
      preferenceStorageError = true;
      if (editorPreferencesStatus) {
        editorPreferencesStatus.textContent = '設定を保存できませんでした。このブラウザーの保存領域を確認してください。';
      }
    }
  }

  function updateEditorPreferences() {
    applyEditorPreferences();
    syncEditorPreferencesControls();
    saveEditorPreferences();
  }

  syncMainEditorLayout();
  window.addEventListener('resize', syncMainEditorLayout);
  syncEditorPreferencesControls();
  if (preferenceStorageError && editorPreferencesStatus) {
    editorPreferencesStatus.textContent = '保存済みの設定を読み込めませんでした。初期設定で表示しています。';
  }

  editorFontSizeInput?.addEventListener('input', () => {
    editorPreferences.fontSizePx = Number(editorFontSizeInput.value);
    updateEditorPreferences();
  });
  editorLineWrappingInput?.addEventListener('change', () => {
    editorPreferences.lineWrapping = editorLineWrappingInput.checked;
    updateEditorPreferences();
  });
  editorIndentWidthInput?.addEventListener('change', () => {
    editorPreferences.indentWidth = Number(editorIndentWidthInput.value);
    updateEditorPreferences();
  });
  editorThemeInput?.addEventListener('change', () => {
    editorPreferences.theme = editorThemeInput.value;
    updateEditorPreferences();
  });
  resetEditorPreferencesButton?.addEventListener('click', () => {
    editorPreferences = { ...defaultEditorPreferences };
    updateEditorPreferences();
    if (editorPreferencesStatus) {
      editorPreferencesStatus.textContent = '初期設定に戻しました。';
    }
  });

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

  function showOutputPanel(targetId) {
    outputTabs.forEach((tab) => {
      const selected = tab.getAttribute('data-output-target') === targetId;
      tab.classList.toggle('active', selected);
      tab.setAttribute('aria-selected', String(selected));
    });
    document.querySelectorAll('#lessonExecutionResultContent .output-view').forEach((panel) => {
      const selected = panel.id === targetId;
      panel.classList.toggle('is-active', selected);
      panel.hidden = !selected;
    });

    if (targetId === 'lessonStdoutOutputPanel') {
      outputEditor?.refresh();
    } else if (targetId === 'lessonStderrOutputPanel') {
      errorEditor?.refresh();
    }
  }

  outputTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      showOutputPanel(tab.getAttribute('data-output-target'));
    });
  });

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

  function promptEntryName(message, entryLabel, defaultValue = '') {
    const forbidden = entryLabel === 'フォルダ' ? './\\' : '/\\';
    const value = window.prompt(`${message}\n${entryLabel}は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。\n使えない文字：${forbidden}/ \\ 。名前の途中で改行したり、タブを入れたりしないでください。空白だけ・「.」だけ・「..」だけの名前も使えません。`, defaultValue);
    if (value === null) {
      return null;
    }

    const normalized = value.trim();
    if (!normalized) {
      setMessage(`${entryLabel}名を入力してください。`, 'warning');
      return '';
    }

    if ([...normalized].length > MAX_ENTRY_NAME_LENGTH) {
      setMessage(`${entryLabel}名は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。`, 'warning');
      return '';
    }

    if (normalized === '.' || normalized === '..' || normalized.includes('/')
      || normalized.includes('\\') || /[\r\n\t]/.test(normalized)
      || (entryLabel === 'フォルダ' && normalized.includes('.'))) {
      setMessage(`${entryLabel}名に使用できない文字が含まれています。`, 'warning');
      return '';
    }

    return normalized;
  }

  function subtreeFitsPath(entry, basePath) {
    if ([...entry.name].length > MAX_ENTRY_NAME_LENGTH || [...basePath].length > MAX_ENTRY_PATH_LENGTH) return false;
    return !isFolder(entry) || (entry.children || []).every((child) =>
      subtreeFitsPath(child, `${basePath}/${child.name}`)
    );
  }

  function hasNameConflict(folder, name, ignoreEntryId = null) {
    if (!folder || !Array.isArray(folder.children)) {
      return false;
    }

    const normalizedName = String(name).toLocaleLowerCase('ja');
    return folder.children.some((child) =>
      child.name.toLocaleLowerCase('ja') === normalizedName && child.id !== ignoreEntryId
    );
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

  function getAllActiveEntries(entries = getSelectableRootEntries(), items = []) {
    entries.forEach((entry) => {
      if (entry.deletedAt || isRecycleBin(entry)) return;
      items.push(entry);
      if (isFolder(entry)) getAllActiveEntries(entry.children || [], items);
    });
    return items;
  }

  function isEntryInTrash(entry) {
    let found = entry ? findEntryById(lessonState, entry.id) : null;
    while (found?.parentFolder) {
      if (isRecycleBin(found.parentFolder)) return true;
      found = findEntryById(lessonState, found.parentFolder.id);
    }
    return false;
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

    if (treeSortMode === 'updated') {
      const updatedOrder = new Date(right.updatedAt || 0).getTime() - new Date(left.updatedAt || 0).getTime();
      if (updatedOrder) {
        return updatedOrder;
      }
    }

    return left.name.localeCompare(right.name, 'ja') || left.id.localeCompare(right.id);
  }

  function sortEntries(entries) {
    entries.sort(compareEntries);

    entries.forEach((entry) => {
      if (isFolder(entry) && Array.isArray(entry.children)) {
        if (isRecycleBin(entry)) {
          entry.children.sort((left, right) =>
            (right.deletedAt || 0) - (left.deletedAt || 0)
            || left.name.localeCompare(right.name, 'ja')
            || left.id.localeCompare(right.id));
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

    recycleBin.children.sort((left, right) =>
      (right.deletedAt || 0) - (left.deletedAt || 0)
      || left.name.localeCompare(right.name, 'ja')
      || left.id.localeCompare(right.id));
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

  function getSelectableRootEntries() {
    return (getRootFolder()?.children || []).filter((entry) => !isRecycleBin(entry) && !entry.deletedAt);
  }

  function isDescendantPath(path, parentPath) {
    return Boolean(path && parentPath && path.startsWith(`${parentPath}/`));
  }

  function getNormalizedSelection(selection) {
    const entries = [...selection]
      .map((id) => findEntryById(lessonState, id)?.entry)
      .filter((entry) => entry && !isRootFolder(entry) && !isRecycleBin(entry)
        && !entry.deletedAt && !isEntryInTrash(entry));
    return entries.filter((entry) => !entries.some((parent) =>
      parent.id !== entry.id && isFolder(parent) && isDescendantPath(entry.path, parent.path)
    ));
  }

  function getVisibleCheckIds(trash = false) {
    if (!trash && treeSearchTerm.trim() && searchResults) {
      return [...searchResults.querySelectorAll('[data-search-result-id]')]
        .filter((result) => result.getClientRects().length > 0)
        .map((result) => result.getAttribute('data-search-result-id'));
    }
    const selector = trash ? '[data-trash-select-entry]' : '[data-file-id], [data-select-folder]';
    return [...lessonFileTree.querySelectorAll(selector)]
      .filter((entry) => entry.getClientRects().length > 0)
      .map((entry) => trash
        ? entry.getAttribute('data-trash-select-entry')
        : entry.getAttribute('data-file-id') || entry.getAttribute('data-select-folder'))
      .filter((id) => id && id !== ROOT_FOLDER_ID);
  }

  function updateExplorerStatus() {
    const target = getSelectedFolderTarget();
    if (addTargetPath) {
      addTargetPath.textContent = target?.path || ROOT_FOLDER_NAME;
    }

    const normalized = getNormalizedSelection(selectedEntryIds);
    const visible = new Set(getVisibleCheckIds());
    const hiddenCount = normalized.filter((entry) => !visible.has(entry.id)).length;
    if (selectionCount) selectionCount.textContent = `選択 ${normalized.length} 件`;
    if (hiddenSelectionCount) {
      hiddenSelectionCount.textContent = hiddenCount ? `（画面外に ${hiddenCount} 件）` : '';
    }
    [downloadSelectedButton, moveSelectedButton, trashSelectedButton, clearSelectedButton]
      .filter(Boolean).forEach((button) => { button.disabled = normalized.length === 0; });
    if (selectionToolbar) {
      selectionToolbar.classList.toggle('d-none', !selectionMode);
    }
    if (selectionModeButton) {
      labelControl(selectionModeButton, selectionMode ? 'finish' : 'select', selectionMode ? '選択を終了' : '選択');
      selectionModeButton.setAttribute('aria-pressed', String(selectionMode));
    }
    if (revealActiveFileButton) revealActiveFileButton.disabled = !getCurrentFile();
    if (displaySettingsStatus) {
      const activeSettings = [];
      if (treeSearchTerm.trim()) activeSettings.push('検索中');
      if (treeSortMode === 'updated') activeSettings.push('更新日時順');
      displaySettingsStatus.textContent = activeSettings.join(' ・ ') || '名前順';
    }
    const searchStatus = document.querySelector('#lessonTreeSearchStatus');
    searchStatus.hidden = !treeSearchTerm.trim();
    searchStatus.textContent = treeSearchTerm.trim() ? `検索: ${treeSearchTerm.trim()}` : '';
    document.querySelector('#lessonSelectionStatus').textContent = normalized.length
      ? `選択 ${normalized.length} 件` : selectionMode ? '選択中' : '';
    document.querySelector('#lessonSelectSearchResults').hidden = !selectionMode || !treeSearchTerm.trim();
    document.querySelector('#lessonSelectAll').hidden = !selectionMode;
  }

  function renderSearchResults() {
    if (!searchResults) return;
    const term = treeSearchTerm.trim().toLocaleLowerCase('ja');
    if (!term) {
      searchResults.innerHTML = '';
      return;
    }

    const matches = getAllActiveEntries()
      .filter((entry) => entry.name.toLocaleLowerCase('ja').includes(term))
      .sort(compareEntries);
    if (!matches.length) {
      searchResults.innerHTML = '<p class="small text-secondary mb-0">該当する名前はありません。</p>';
      return;
    }

    searchResults.innerHTML = `<p class="small text-secondary mb-1">検索結果 ${matches.length} 件（名前のみ）</p>
      <div class="lesson-search-result-list">${matches.map((entry) => `
        <div class="lesson-search-result-row">
          <button class="lesson-search-result" type="button" data-search-result-id="${entry.id}">
            <span>${isFolder(entry) ? '📁' : '📄'} ${escapeHtml(entry.name)}
              <span class="lesson-search-result-path">${escapeHtml(entry.path)}</span>
            </span>
          </button>
          ${selectionMode ? `<input class="lesson-entry-checkbox" type="checkbox" aria-label="${escapeHtml(entry.path)}を操作対象に選択"
            data-search-result-check="${entry.id}" ${selectedEntryIds.has(entry.id) || selectedParent(entry.id) ? 'checked' : ''}
            ${selectedParent(entry.id) ? 'disabled title="親フォルダの選択に含まれます。個別に選ぶには親の選択を解除してください。"' : ''}>` : ''}
        </div>`).join('')}</div>
      `;
  }

  function renderTrashControls(recycleBin, units) {
    const matchingUnits = trashSearchTerm.trim()
      ? units.filter((unit) => {
          const term = trashSearchTerm.trim().toLocaleLowerCase('ja');
          return [unit, ...getAllFiles([unit]), ...getAllFolders([unit])]
            .some((entry) => entry.name.toLocaleLowerCase('ja').includes(term));
        })
      : units;
    const shown = matchingUnits.slice(0, trashDisplayLimit);
    const selectedCount = selectedTrashEntryIds.size;
    return `<details class="lesson-explorer-disclosure lesson-trash-selection-controls" data-trash-selection-tools${trashSelectionToolsOpen ? ' open' : ''}>
      <summary class="lesson-explorer-summary"><span class="lesson-explorer-summary-title">選択操作</span><span class="lesson-explorer-summary-status">${selectedCount ? `選択 ${selectedCount} 件` : trashSelectionMode ? '選択中' : ''}</span></summary>
      <div class="lesson-explorer-tools">
      <button class="btn btn-sm btn-outline-primary" type="button" data-toggle-trash-selection
        aria-pressed="${trashSelectionMode}">${controlIcon(trashSelectionMode ? 'finish' : 'select')}${trashSelectionMode ? '選択を終了' : '選択'}</button>
      <div class="lesson-trash-selection-toolbar${trashSelectionMode ? '' : ' d-none'}">
        <strong>ごみ箱の選択 ${selectedCount} 件</strong>
        <div class="lesson-selection-actions">
          <button class="btn btn-sm btn-outline-primary" type="button" data-restore-selected ${selectedCount ? '' : 'disabled'}>${controlIcon('restore')}復元</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" data-clear-trash-selection ${selectedCount ? '' : 'disabled'}>${controlIcon('clear')}選択解除</button>
        </div>
      </div>
      </div>
    </details>
    <div class="px-3 pb-2">
      <label class="form-label small fw-semibold mb-1" for="lessonTrashSearch">ごみ箱内を名前で検索</label>
      <input id="lessonTrashSearch" class="form-control form-control-sm" type="search"
      value="${escapeHtml(trashSearchTerm)}" placeholder="削除単位・配下の名前">
      <p class="small text-secondary mt-1 mb-0">削除単位 ${matchingUnits.length} 件 / 表示 ${shown.length} 件</p>
    </div>`;
  }

  function toggleSelection(selection, id, checked) {
    if (selection === selectedEntryIds && selectedParent(id)) return;
    if (checked) selection.add(id);
    else selection.delete(id);
  }
  function selectedParent(id) {
    const entry = findEntryById(lessonState, id)?.entry;
    if (!entry || entry.deletedAt) return null;
    return getAllFolders(lessonState).find((folder) => folder.id !== id
      && selectedEntryIds.has(folder.id) && !folder.deletedAt && !isEntryInTrash(folder)
      && isDescendantPath(entry.path, folder.path)) || null;
  }

  function applyRangeSelection(selection, anchorId, targetId, visibleIds, checked) {
    const start = visibleIds.indexOf(anchorId);
    const end = visibleIds.indexOf(targetId);
    if (start < 0 || end < 0) {
      toggleSelection(selection, targetId, checked);
      return;
    }
    visibleIds.slice(Math.min(start, end), Math.max(start, end) + 1)
      .forEach((id) => toggleSelection(selection, id, checked));
  }

  function revealEntry(entry) {
    if (!entry) return;
    expandedFolderIds.add(ROOT_FOLDER_ID);
    const ancestors = getAllFolders(lessonState)
      .filter((folder) => !isRecycleBin(folder) && !folder.deletedAt
        && (folder.id === entry.id || isDescendantPath(entry.path, folder.path)))
      .sort((left, right) => left.path.length - right.path.length);
    ancestors.forEach((folder) => expandedFolderIds.add(folder.id));
    renderFileTree(lessonState);
    const target = lessonFileTree.querySelector(`[data-file-id="${entry.id}"], [data-select-folder="${entry.id}"]`);
    target?.classList.add('lesson-highlighted-entry');
    target?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function collectMoveTargets(entryToMove) {
    const candidatesForEntry = (movingEntry) => getAllFolders(lessonState).filter((folder) => {
      if (isRecycleBin(folder) || folder.deletedAt || isEntryInTrash(folder)) {
        return false;
      }

      if (folder.id === movingEntry.id) {
        return false;
      }

      if (isFolder(movingEntry) && !canPlaceFolderUnder(folder, movingEntry)) {
        return false;
      }

      if (!isFolder(movingEntry)) {
        return true;
      }

      const found = findEntryById(movingEntry.children || [], folder.id, movingEntry);
      return !found;
    });
    const pendingEntries = pendingMoveEntryIds
      .map((id) => findEntryById(lessonState, id)?.entry)
      .filter(Boolean);
    if (pendingEntries.length <= 1 || !pendingEntries.some((entry) => entry.id === entryToMove.id)) {
      return candidatesForEntry(entryToMove);
    }

    const sharedIds = new Set(candidatesForEntry(pendingEntries[0]).map((folder) => folder.id));
    pendingEntries.slice(1).forEach((entry) => {
      const ids = new Set(candidatesForEntry(entry).map((folder) => folder.id));
      [...sharedIds].forEach((id) => { if (!ids.has(id)) sharedIds.delete(id); });
    });
    return getAllFolders(lessonState).filter((folder) => sharedIds.has(folder.id));
  }

  function renderMoveFolderTree(entry, validTargetIds, isRootNode = false) {
    if (!entry || !isFolder(entry)) {
      return '';
    }

    const childFolders = (entry.children || []).filter((child) => isFolder(child) && validTargetIds.has(child.id));
    const escapedName = escapeHtml(isRootNode ? ROOT_FOLDER_NAME : entry.name);
    const canSelect = validTargetIds.has(entry.id);
    const isSelectedTarget = pendingMoveDestinationPath === (isRootNode ? ROOT_FOLDER_LABEL : entry.path);
    const isExpanded = expandedMoveFolderIds.has(entry.id);
    const targetLabel = isRootNode ? ROOT_FOLDER_NAME : entry.name;

    return `
      <div class="move-entry-folder${isRootNode ? ' is-root-node' : ''}${isSelectedTarget ? ' is-selected-target' : ''}">
        <div class="move-entry-folder-row">
          ${childFolders.length > 0 ? `<button class="move-entry-folder-toggle${isExpanded ? '' : ' is-collapsed'}"
            type="button" data-toggle-move-folder="${entry.id}" aria-expanded="${isExpanded}"
            aria-label="${escapeHtml(targetLabel)} を${isExpanded ? '折りたたみ' : '展開'}"><span aria-hidden="true">▾</span></button>` : ''}
          <button class="move-entry-folder-button" data-exercise-full-name="${escapedName}" type="button"
            aria-pressed="${isSelectedTarget}" ${isRootNode ? 'data-move-root-target' : `data-move-target-id="${entry.id}"`}
            ${canSelect ? '' : 'disabled'}>
            <div class="move-entry-folder-main">
              <svg class="move-entry-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                <path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>
              </svg>
              <span class="move-entry-folder-name">${escapedName}</span>
            </div>
          </button>
        </div>
        ${childFolders.length && isExpanded ? `<div class="move-entry-children">${childFolders.map((child) => renderMoveFolderTree(child, validTargetIds)).join('')}</div>` : ''}
      </div>
    `;
  }

  function renderMoveEntryModal(entryId) {
    const entries = pendingMoveEntryIds
      .map((id) => findEntryById(lessonState, id)?.entry)
      .filter(Boolean);
    const entry = entries.find((item) => item.id === entryId) || entries[0];
    if (!entry || !entries.length) {
      return;
    }

    const validTargets = collectMoveTargets(entry);
    const validTargetIds = new Set(validTargets.map((folder) => folder.id));
    const rootFolder = getRootFolder();
    const canMoveToRoot = entries.every((movingEntry) =>
      !isFolder(movingEntry) || canPlaceFolderUnder(rootFolder, movingEntry)
    );

    if (moveEntryModalCurrentPath) {
      moveEntryModalCurrentPath.textContent = entries.length === 1
        ? `現在: ${entry.path}`
        : `${entries.length} 件を移動（${entries.map((item) => item.path).join('、')}）`;
    }
    if (moveEntryModalSelectedPath) {
      const destinationLabel = !pendingMoveDestinationPath
        ? '未選択'
        : pendingMoveDestinationPath === ROOT_FOLDER_LABEL
          ? ROOT_FOLDER_NAME
          : pendingMoveDestinationPath;
      moveEntryModalSelectedPath.textContent = `移動先: ${destinationLabel}`;
    }

    if (moveEntryModalTree) {
      clearNameTooltips(moveEntryModalTree);
      moveEntryModalTree.innerHTML = rootFolder ? renderMoveFolderTree(rootFolder, validTargetIds, true) : '';
    }

    if (moveEntryModalEmpty) {
      moveEntryModalEmpty.classList.toggle('d-none', Boolean(validTargetIds.size || canMoveToRoot));
    }

    if (moveEntryConfirmButton) {
      moveEntryConfirmButton.disabled = !pendingMoveDestinationPath;
    }
  }

  function openMoveEntryModal(entryId, entryIds = [entryId]) {
    const found = findEntryById(lessonState, entryId);
    if (!found) {
      return;
    }

    pendingMoveEntryId = entryId;
    pendingMoveEntryIds = [...entryIds];
    pendingMoveDestinationPath = null;
    expandedMoveFolderIds.clear();
    expandedMoveFolderIds.add(ROOT_FOLDER_ID);
    renderMoveEntryModal(entryId);

    if (moveEntryModal) {
      moveEntryModal.show();
      return;
    }

    setMessage('移動先モーダルを表示できませんでした。', 'warning');
  }

  function closeMoveEntryModal() {
    pendingMoveEntryId = null;
    pendingMoveEntryIds = [];
    pendingMoveDestinationPath = null;
    expandedMoveFolderIds.clear();

    if (moveEntryModalTree) {
      moveEntryModalTree.innerHTML = '';
    }

    if (moveEntryModalEmpty) {
      moveEntryModalEmpty.classList.add('d-none');
    }
  }

  function resetUploadState() {
    pendingUploadFolderId = ROOT_FOLDER_ID;
    pendingUploadItems = [];
    pendingUploadSourceMode = '';
    pendingUploadExcludedCount = 0;
    if (uploadSourceFile) {
      uploadSourceFile.value = '';
    }
    if (uploadSourceDirectory) {
      uploadSourceDirectory.value = '';
    }
    if (uploadFileName) {
      uploadFileName.textContent = '未選択';
    }
    if (uploadFilePreviewList) {
      uploadFilePreviewList.innerHTML = '<li class="upload-file-preview-item upload-file-preview-empty">選択したファイル名がここに表示されます。</li>';
    }
    if (uploadEntryConfirmButton) {
      uploadEntryConfirmButton.disabled = true;
    }
  }

  function isPythonUploadFile(file) {
    const rawPath = String(file?.webkitRelativePath || file?.name || '').trim();
    return /\.py$/i.test(rawPath);
  }

  function isValidUploadTarget(folder) {
    return isFolder(folder) && !isRecycleBin(folder) && !folder.deletedAt;
  }

  function renderUploadFolderTreeNode(entry, isRootNode = false) {
    if (!isValidUploadTarget(entry)) {
      return '';
    }

    const isSelected = entry.id === pendingUploadFolderId;
    const childFolders = (entry.children || []).filter((child) => isValidUploadTarget(child));
    const escapedName = escapeHtml(isRootNode ? ROOT_FOLDER_NAME : entry.name);

    return `
      <div class="move-entry-folder${isRootNode ? ' is-root-node' : ''}${isSelected ? ' is-selected-target' : ''}">
        <button class="move-entry-folder-button" data-exercise-full-name="${escapedName}" type="button" data-upload-folder-id="${entry.id}">
          <div class="move-entry-folder-main">
            <svg class="move-entry-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
              <path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>
            </svg>
            <span class="move-entry-folder-name">${escapedName}</span>
          </div>
        </button>
        ${childFolders.length > 0 ? `<div class="move-entry-children">${childFolders.map((child) => renderUploadFolderTreeNode(child)).join('')}</div>` : ''}
      </div>
    `;
  }

  function renderUploadFolderTree() {
    if (!uploadFolderTree) {
      return;
    }
    clearNameTooltips(uploadFolderTree);

    const rootFolder = getRootFolder();
    if (!rootFolder || !isValidUploadTarget(rootFolder)) {
      uploadFolderTree.innerHTML = '';
      pendingUploadFolderId = null;
      return;
    }

    if (!pendingUploadFolderId || !isValidUploadTarget(findEntryById(lessonState, pendingUploadFolderId)?.entry)) {
      pendingUploadFolderId = rootFolder.id;
    }

    uploadFolderTree.innerHTML = renderUploadFolderTreeNode(rootFolder, true);
  }

  function setPendingUploadItems(files, mode) {
    const selectedItems = Array.from(files || []).filter((file) => file && file.name);
    pendingUploadItems = selectedItems.filter((file) => isPythonUploadFile(file));
    pendingUploadExcludedCount = Math.max(0, selectedItems.length - pendingUploadItems.length);
    pendingUploadSourceMode = pendingUploadItems.length ? mode : '';

    if (uploadFileName) {
      if (!pendingUploadItems.length) {
        uploadFileName.textContent = pendingUploadExcludedCount > 0
          ? '.py ファイルが選択されていません'
          : '未選択';
      } else if (mode === 'folder') {
        const firstPath = pendingUploadItems[0].webkitRelativePath || pendingUploadItems[0].name;
        const rootName = firstPath.split('/').filter(Boolean)[0] || 'フォルダ';
        uploadFileName.textContent = `${rootName}（${pendingUploadItems.length}件の .py）`;
      } else if (pendingUploadItems.length === 1) {
        uploadFileName.textContent = pendingUploadItems[0].name;
      } else {
        uploadFileName.textContent = `${pendingUploadItems.length}件の .py ファイルを選択中`;
      }

      if (pendingUploadExcludedCount > 0 && pendingUploadItems.length > 0) {
        uploadFileName.textContent += `（.py以外 ${pendingUploadExcludedCount}件は除外）`;
      }
    }

    if (uploadFilePreviewList) {
      if (!pendingUploadItems.length) {
        uploadFilePreviewList.innerHTML = '<li class="upload-file-preview-item upload-file-preview-empty">選択したファイル名がここに表示されます。</li>';
      } else {
        const previewItems = pendingUploadItems.slice(0, MAX_UPLOAD_PREVIEW_ITEMS).map((file) => {
          const rawPath = pendingUploadSourceMode === 'folder'
            ? (file.webkitRelativePath || file.name)
            : file.name;
          return `<li class="upload-file-preview-item">${escapeHtml(rawPath)}</li>`;
        });

        const remaining = pendingUploadItems.length - previewItems.length;
        if (remaining > 0) {
          previewItems.push(`<li class="upload-file-preview-item upload-file-preview-empty">...ほか ${remaining} 件</li>`);
        }

        if (pendingUploadExcludedCount > 0) {
          previewItems.push(`<li class="upload-file-preview-item upload-file-preview-empty">.py以外 ${pendingUploadExcludedCount} 件はアップロード対象外です。</li>`);
        }

        uploadFilePreviewList.innerHTML = previewItems.join('');
      }
    }

    updateUploadConfirmState();
  }

  function updateUploadConfirmState() {
    if (!uploadEntryConfirmButton) {
      return;
    }

    const hasFile = pendingUploadItems.length > 0;
    const hasTarget = Boolean(pendingUploadFolderId);
    uploadEntryConfirmButton.disabled = !(hasFile && hasTarget);
  }

  function openUploadEntryModal() {
    resetUploadState();
    pendingUploadFolderId = getSelectedFolderTarget()?.id || ROOT_FOLDER_ID;
    renderUploadFolderTree();
    updateUploadConfirmState();

    if (uploadEntryModal) {
      uploadEntryModal.show();
      return;
    }

    setMessage('アップロードモーダルを表示できませんでした。', 'warning');
  }

  async function uploadFileToSelectedFolder() {
    const sourceFiles = pendingUploadItems.slice();
    if (!sourceFiles.length) {
      setMessage('アップロードするファイルまたはフォルダを選択してください。', 'warning');
      return;
    }

    const foundTarget = pendingUploadFolderId
      ? findEntryById(lessonState, pendingUploadFolderId)
      : null;
    const originalTarget = foundTarget?.entry;
    const targetFolder = originalTarget ? structuredClone(originalTarget) : null;

    if (!targetFolder || !isFolder(targetFolder) || isRecycleBin(targetFolder)) {
      setMessage('追加先フォルダを選択してください。', 'warning');
      return;
    }

    if (sourceFiles.length > 100 || sourceFiles.some((file) => file.size > 65536)
      || sourceFiles.reduce((sum, file) => sum + file.size, 0) > 1048576) {
      setMessage('1ファイル64 KiB・100ファイル・合計1 MiB以内にしてください。', 'warning');
      return;
    }
    const contents = new Map();
    try {
      for (const file of sourceFiles) {
        contents.set(file, new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()));
      }
    } catch (error) {
      setMessage('UTF-8のファイルを選択してください。追加は行っていません。', 'warning');
      return;
    }
    let addedCount = 0;
    const skippedNames = [];

    for (const sourceFile of sourceFiles) {
      const rawPath = pendingUploadSourceMode === 'folder'
        ? (sourceFile.webkitRelativePath || sourceFile.name)
        : sourceFile.name;
      const pathSegments = String(rawPath || '').split('/').filter((segment) => segment);
      if (!pathSegments.length) {
        skippedNames.push(sourceFile.name || '不明なファイル');
        continue;
      }

      const effectiveSegments = pathSegments;
      if (!effectiveSegments.length) {
        skippedNames.push(sourceFile.name || '不明なファイル');
        continue;
      }

      let cursor = targetFolder;
      const folderSegments = effectiveSegments.slice(0, -1);
      let hasInvalidPath = false;

      for (const rawFolderName of folderSegments) {
        const folderName = String(rawFolderName || '').trim();
        if (!folderName || folderName.includes('.') || [...folderName].length > 255) {
          hasInvalidPath = true;
          break;
        }

        const duplicateByName = (cursor.children || []).find((child) => child.name === folderName);
        if (duplicateByName && !isFolder(duplicateByName)) {
          hasInvalidPath = true;
          break;
        }

        let nextFolder = (cursor.children || []).find((child) => isFolder(child) && child.name === folderName);
        if (!nextFolder) {

          nextFolder = {
            id: createId('folder'),
            type: 'folder',
            name: folderName,
            updatedAt: new Date().toISOString(),
            children: []
          };
          cursor.children.push(nextFolder);
          cursor.updatedAt = nextFolder.updatedAt;
        }

        cursor = nextFolder;
      }

      if (hasInvalidPath) {
        skippedNames.push(rawPath);
        continue;
      }

      const normalizedName = String(effectiveSegments[effectiveSegments.length - 1] || '').trim();
      if (!normalizedName || [...normalizedName].length > MAX_ENTRY_NAME_LENGTH
        || [...`${originalTarget.path}/${effectiveSegments.join('/')}`].length > MAX_ENTRY_PATH_LENGTH) {
        skippedNames.push(rawPath);
        continue;
      }

      if (!/\.py$/i.test(normalizedName)) {
        skippedNames.push(rawPath);
        continue;
      }

      const fileName = normalizedName;
      if (hasNameConflict(cursor, fileName)) {
        skippedNames.push(rawPath);
        continue;
      }

      const content = contents.get(sourceFile);
      const newFile = {
        id: createId('file'),
        type: 'file',
        name: fileName,
        updatedAt: new Date().toISOString(),
        content
      };
      cursor.children.push(newFile);
      cursor.updatedAt = newFile.updatedAt;

      addedCount += 1;
    }

    if (!addedCount || skippedNames.length) {
      setMessage('名前重複または無効な項目があります。追加は行っていません。', 'warning');
      return;
    }

    originalTarget.children = targetFolder.children;
    originalTarget.updatedAt = new Date().toISOString();
    refreshState();
    renderFileTree(lessonState);

    const skippedCount = skippedNames.length;
    const suffix = skippedCount > 0 ? `（${skippedCount}件は重複または無効のため未追加）` : '';
    setMessage(`${addedCount}件のファイルを「${targetFolder.path}」に追加しました。${suffix}`, 'success');
    if (pageFeedback && typeof pageFeedback.toast === 'function') {
      pageFeedback.toast({
        title: '授業演習',
        message: `${addedCount}件のファイルをアップロードしました。${suffix}`,
        variant: 'success'
      });
    }

    if (uploadEntryModal) {
      uploadEntryModal.hide();
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
    if (currentFile && !currentFile.deletedAt && !isEntryInTrash(currentFile)) {
      return;
    }

    const activeFiles = getAllActiveEntries().filter(isFile);
    const fallbackFile = activeFiles[0] || null;
    currentFileId = fallbackFile?.id || null;
    currentSelectionId = fallbackFile
      ? findEntryById(lessonState, fallbackFile.id)?.parentFolder?.id || ROOT_FOLDER_ID
      : ROOT_FOLDER_ID;
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

  if (moveEntryModalElement) {
    moveEntryModalElement.addEventListener('hide.bs.modal', () => {
      if (moveEntryModalElement.contains(document.activeElement)) document.activeElement.blur();
    });
    moveEntryModalElement.addEventListener('hidden.bs.modal', () => {
      closeMoveEntryModal();
      const tools = document.querySelector('#lessonSelectionTools');
      (tools.open ? selectionModeButton : tools.querySelector('summary')).focus({ preventScroll: true });
    });

    moveEntryModalElement.addEventListener('click', (event) => {
      const moveFolderToggle = event.target.closest('[data-toggle-move-folder]');
      if (moveFolderToggle) {
        const folderId = moveFolderToggle.getAttribute('data-toggle-move-folder');
        if (expandedMoveFolderIds.has(folderId)) expandedMoveFolderIds.delete(folderId);
        else expandedMoveFolderIds.add(folderId);
        renderMoveEntryModal(pendingMoveEntryId);
        return;
      }

      const rootButton = event.target.closest('[data-move-root-target]');
      if (rootButton && pendingMoveEntryIds.length) {
        pendingMoveDestinationPath = ROOT_FOLDER_LABEL;
        renderMoveEntryModal(pendingMoveEntryId);
        return;
      }

      const targetButton = event.target.closest('[data-move-target-id]');
      if (!targetButton || !pendingMoveEntryIds.length) {
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

  if (uploadEntryModalElement) {
    uploadEntryModalElement.addEventListener('hidden.bs.modal', () => {
      resetUploadState();
    });

    uploadEntryModalElement.addEventListener('click', (event) => {
      const folderButton = event.target.closest('[data-upload-folder-id]');
      if (!folderButton) {
        return;
      }

      pendingUploadFolderId = folderButton.getAttribute('data-upload-folder-id');
      renderUploadFolderTree();
      updateUploadConfirmState();
    });
  }

  if (uploadSourceFile) {
    uploadSourceFile.addEventListener('change', () => {
      if (uploadSourceDirectory) {
        uploadSourceDirectory.value = '';
      }
      setPendingUploadItems(uploadSourceFile.files, 'files');
    });
  }

  if (uploadSourceDirectory) {
    uploadSourceDirectory.addEventListener('change', () => {
      if (uploadSourceFile) {
        uploadSourceFile.value = '';
      }
      setPendingUploadItems(uploadSourceDirectory.files, 'folder');
    });
  }

  if (uploadSelectFilesButton && uploadSourceFile) {
    uploadSelectFilesButton.addEventListener('click', () => {
      uploadSourceFile.click();
    });
  }

  if (uploadSelectFolderButton && uploadSourceDirectory) {
    uploadSelectFolderButton.addEventListener('click', () => {
      uploadSourceDirectory.click();
    });
  }

  if (uploadEntryConfirmButton) {
    uploadEntryConfirmButton.addEventListener('click', async () => {
      try {
        await uploadFileToSelectedFolder();
      } catch (error) {
        setMessage('ファイルの追加に失敗しました。', 'danger');
      }
    });
  }

  if (moveEntryConfirmButton) {
    moveEntryConfirmButton.addEventListener('click', async () => {
      if (!pendingMoveEntryIds.length || !pendingMoveDestinationPath) {
        return;
      }
      const moveIds = [...pendingMoveEntryIds];
      const moveDestination = pendingMoveDestinationPath;
      const movingEntries = moveIds
        .map((id) => findEntryById(lessonState, id)?.entry)
        .filter(Boolean);
      const modalClosed = moveEntryModal
        ? new Promise((resolve) => moveEntryModalElement.addEventListener('hidden.bs.modal', resolve, { once: true }))
        : Promise.resolve();
      moveEntryModal?.hide();
      await modalClosed;
      const moved = await moveEntriesToDestination(moveIds, moveDestination);
      if (!moved) {
        setMessage('移動先に移動できませんでした。', 'warning');
        return;
      }
      setMessage(`${movingEntries.length} 件を移動しました。`, 'success');
    });
  }

  function saveCurrentFile(showMessage = true) {
    const file = getCurrentFile();
    if (!file || !isFile(file)) {
      return;
    }

    file.content = getEditorValue();
    const savedAt = new Date().toISOString();
    file.updatedAt = savedAt;
    const parentFolder = findEntryById(lessonState, file.id)?.parentFolder;
    if (parentFolder) parentFolder.updatedAt = savedAt;
    const time = nowTimeLabel();

    if (lastSavedAt) {
      lastSavedAt.textContent = time;
    }

    if (showMessage) {
      setMessage('保存状態: 保存済みです。', 'success');
    }
  }

  function buildDownloadFileName() {
    const file = getCurrentFile();
    const baseName = file?.name || 'lesson_code.py';
    const sanitized = baseName
      .replace(/[\\/:*?"<>|]/g, '_')
      .replace(/\s+/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_+|_+$/g, '');
    const withExtension = sanitized || 'lesson_code.py';
    return /\.py$/i.test(withExtension) ? withExtension : `${withExtension}.py`;
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

  function updateActiveFileUI() {
    const file = getCurrentFile();
    if (!file || !isFile(file)) {
      if (pathBadge) {
        pathBadge.textContent = 'Path: -';
      }

      if (currentFileTab) {
        currentFileTab.textContent = 'ファイル未選択';
        currentFileTab.dataset.exerciseFullName = '';
      }

      setEditorValue('');
      return;
    }

    if (pathBadge) {
      pathBadge.textContent = `Path: ${file.path}`;
    }

    if (currentFileTab) {
      currentFileTab.textContent = file.name;
      currentFileTab.tabIndex = 0;
      currentFileTab.dataset.exerciseFullName = file.name;
    }

    currentSelectionId = findEntryById(lessonState, file.id)?.parentFolder?.id || ROOT_FOLDER_ID;
    setEditorValue(file.content);
    updateExplorerStatus();
  }

  function updateCurrentFilePathUI() {
    const file = getCurrentFile();
    if (!file) return;
    if (pathBadge) pathBadge.textContent = `Path: ${file.path}`;
    if (currentFileTab) {
      currentFileTab.textContent = file.name;
      currentFileTab.dataset.exerciseFullName = file.name;
    }
    updateExplorerStatus();
  }

  function clearOperatedSelection(entries, snapshots = entries.map((entry) => ({
    id: entry.id,
    path: entry.path,
    folder: isFolder(entry)
  }))) {
    selectedEntryIds.forEach((id) => {
      const entry = findEntryById(lessonState, id)?.entry;
      if (entry && snapshots.some((operated) =>
        entry.id === operated.id
        || (operated.folder && (
          isDescendantPath(entry.path, operated.path)
          || isDescendantPath(entry.path, findEntryById(lessonState, operated.id)?.entry?.path)
        ))
      )) selectedEntryIds.delete(id);
    });
  }

  function renderEntryCheckbox(entry, trashChild = false) {
    if (trashChild || isRecycleBin(entry)) return '';
    if (isRootFolder(entry)) {
      if (!selectionMode) return '';
      const topLevel = getSelectableRootEntries();
      const checkedCount = topLevel.filter((item) => selectedEntryIds.has(item.id)).length;
      return `<input class="lesson-entry-checkbox" type="checkbox" data-select-all
        aria-label="有効なトップレベル項目を全選択"
        ${topLevel.length && checkedCount === topLevel.length ? 'checked' : ''}
        ${checkedCount && checkedCount < topLevel.length ? 'data-indeterminate="true"' : ''}>`;
    }
    const trashItem = Boolean(entry.deletedAt);
    if (trashItem ? !trashSelectionMode : !selectionMode) return '';
    const selection = trashItem ? selectedTrashEntryIds : selectedEntryIds;
    const attribute = trashItem ? 'data-check-trash' : 'data-check-entry';
    const scopeLabel = isFolder(entry) ? '（配下を含む）' : '';
    const parent = trashItem ? null : selectedParent(entry.id);
    return `<input class="lesson-entry-checkbox" type="checkbox" ${attribute}="${entry.id}"
      aria-label="${escapeHtml(entry.path || entry.name)}${scopeLabel}を${trashItem ? '復元' : '操作'}対象に選択"
      ${selection.has(entry.id) || parent ? 'checked' : ''}
      ${parent ? `disabled title="「${escapeHtml(parent.name)}」の選択に含まれます。個別に選ぶには親の選択を解除してください。"` : ''}>`;
  }

  function renderEntryMenu(entry, isRoot = false, trashChild = false) {
    if (isRoot || trashChild || isRecycleBin(entry) || entry.isSystem) return '';
    const menuItems = entry.deletedAt
      ? `<li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="restore" data-entry-id="${entry.id}">復元</button></li>`
      : `
          <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="rename" data-entry-id="${entry.id}">名前変更</button></li>
          <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="duplicate" data-entry-id="${entry.id}">複製</button></li>
          <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="move" data-entry-id="${entry.id}">移動</button></li>
          <li><button class="dropdown-item lesson-tree-menu-item" type="button" data-action="download" data-entry-id="${entry.id}">ダウンロード</button></li>
          <li><button class="dropdown-item lesson-tree-menu-item text-danger" type="button" data-action="trash" data-entry-id="${entry.id}">ごみ箱へ</button></li>`;
    return `<div class="dropdown lesson-tree-entry-menu">
      <button class="btn btn-outline-secondary btn-sm lesson-tree-menu-toggle" type="button"
        data-bs-toggle="dropdown" aria-expanded="false" aria-label="${escapeHtml(entry.name)} の操作メニュー">⋮</button>
      <ul class="dropdown-menu dropdown-menu-end lesson-tree-menu-dropdown">${menuItems}</ul>
    </div>`;
  }

  function renderFileButton(file, trashChild = false) {
    const isActive = file.id === currentFileId;
    const escapedName = escapeHtml(file.name);
    const escapedDisplayName = escapeHtml(shortenEntryLabel(file.name));
    const deletedMeta = file.deletedAt
      ? `<span class="lesson-tree-entry-meta">削除: ${formatDeletedAt(file.deletedAt)}</span>`
      : '';
    return `<div class="lesson-tree-entry-row${isActive ? ' is-active-row' : ''}"
      ${file.deletedAt && !trashChild ? `data-trash-select-entry="${file.id}"` : ''}>
      ${renderEntryCheckbox(file, trashChild)}
      <button class="lesson-tree-file-button${isActive ? ' is-active' : ''}" type="button"
        ${trashChild || file.deletedAt ? 'disabled aria-disabled="true"' : `data-file-id="${file.id}"`}>
        <svg class="lesson-tree-file-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path d="M3.75 1.5A1.75 1.75 0 0 0 2 3.25v9.5c0 .966.784 1.75 1.75 1.75h8.5A1.75 1.75 0 0 0 14 12.75V5.56a1.75 1.75 0 0 0-.513-1.237L10.677 1.513A1.75 1.75 0 0 0 9.44 1H3.75zm5.5 1.06c.133.035.255.103.354.202l2.634 2.634a.75.75 0 0 1 .202.354H9.75a.5.5 0 0 1-.5-.5V2.56z"/>
        </svg>
        <span class="lesson-tree-file-copy">
          <span class="lesson-tree-file-name" data-exercise-full-name="${escapedName}" title="${escapedName}">${escapedDisplayName}</span>
          ${deletedMeta}
        </span>
      </button>
      ${renderEntryMenu(file, false, trashChild)}
    </div>`;
  }

  function renderFolder(entry, trashChild = false) {
    const escapedName = escapeHtml(entry.name);
    const escapedDisplayName = escapeHtml(shortenEntryLabel(entry.name));
    const inTrash = trashChild || Boolean(entry.deletedAt);
    const isCollapsed = inTrash ? !expandedTrashFolderIds.has(entry.id) : !expandedFolderIds.has(entry.id);
    const isSelected = entry.id === currentSelectionId;
    const isRoot = isRootFolder(entry);
    const deletedMeta = entry.deletedAt
      ? `<span class="lesson-tree-entry-meta">削除: ${formatDeletedAt(entry.deletedAt)}</span>`
      : '';
    const trashMatchPaths = entry.deletedAt && trashSearchTerm.trim()
      ? [...getAllFiles([entry]), ...getAllFolders([entry])]
          .filter((child) => child.id !== entry.id
            && child.name.toLocaleLowerCase('ja').includes(trashSearchTerm.trim().toLocaleLowerCase('ja')))
          .map((child) => child.path)
      : [];
    const units = (entry.children || []).filter((child) => child.deletedAt);
    const trashMatchingUnits = trashSearchTerm.trim()
      ? units.filter((unit) => {
          const term = trashSearchTerm.trim().toLocaleLowerCase('ja');
          return [unit, ...getAllFiles([unit]), ...getAllFolders([unit])]
            .some((child) => child.name.toLocaleLowerCase('ja').includes(term));
        })
      : units;
    const visibleTrashUnits = trashMatchingUnits.slice(0, trashDisplayLimit);
    const children = isRecycleBin(entry)
      ? visibleTrashUnits
      : (entry.children || []);
    const rootCheckbox = isRoot ? renderEntryCheckbox(entry) : renderEntryCheckbox(entry, trashChild);
    return `<div class="lesson-tree-folder${isRecycleBin(entry) ? ' lesson-trash' : ''}">
      <div class="lesson-tree-folder-header${isSelected ? ' is-selected-folder' : ''}"
        ${entry.deletedAt && !trashChild ? `data-trash-select-entry="${entry.id}"` : ''}>
        <div class="lesson-tree-folder-title">
          <button class="lesson-tree-folder-toggle${isCollapsed ? ' is-collapsed' : ''}" type="button"
            data-toggle-folder="${entry.id}" aria-expanded="${isCollapsed ? 'false' : 'true'}"
            aria-label="${escapedName} を${isCollapsed ? '展開' : '折りたたみ'}">
            <span class="lesson-tree-folder-toggle-icon">▾</span>
          </button>
          ${rootCheckbox}
          <svg class="lesson-tree-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path d="${isRecycleBin(entry) ? 'M6 1h4l1 2h3v2H2V3h3l1-2zm-3 5h10l-1 9H4L3 6z' : 'M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z'}"/>
          </svg>
          <span class="lesson-tree-folder-name" tabindex="0" data-exercise-full-name="${escapedName}"
            title="${escapedName}" ${!trashChild && !entry.deletedAt ? `data-select-folder="${entry.id}"` : ''}>${escapedDisplayName}</span>
          ${isRecycleBin(entry) ? `<span class="lesson-trash-count">${units.length}</span>` : ''}
          ${deletedMeta}
          ${trashMatchPaths.length ? `<span class="lesson-trash-match">一致: ${trashMatchPaths.map(escapeHtml).join('、')}</span>` : ''}
        </div>
        ${renderEntryMenu(entry, isRoot, trashChild)}
      </div>
      <div class="lesson-tree-folder-children${isCollapsed ? ' is-collapsed' : ''}">
        ${isRecycleBin(entry) ? renderTrashControls(entry, units) : ''}
        ${!children.length && (isRecycleBin(entry) || entry.deletedAt || trashChild)
          ? `<p class="lesson-trash-empty">${isRecycleBin(entry) ? 'ごみ箱は空です' : 'このフォルダは空です'}</p>`
          : children.map((child) => {
              const isNestedTrashChild = trashChild
                || ((Boolean(entry.deletedAt) || isRecycleBin(entry)) && !child.deletedAt);
              return isFolder(child)
                ? renderFolder(child, isNestedTrashChild)
                : renderFileButton(child, isNestedTrashChild);
            }).join('')}
        ${isRecycleBin(entry) && trashMatchingUnits.length > visibleTrashUnits.length
          ? `<button class="btn btn-sm btn-link lesson-trash-more" type="button" data-trash-show-more>さらに表示（残り ${trashMatchingUnits.length - visibleTrashUnits.length} 件）</button>`
          : ''}
      </div>
    </div>`;
  }

  function renderFileTree(entries) {
    if (!lessonFileTree) {
      return;
    }

    window.PPEExplorerMenus.clear(lessonFileTree);
    clearNameTooltips(lessonFileTree);
    lessonFileTree.innerHTML = entries.map((entry) => isFolder(entry) ? renderFolder(entry) : renderFileButton(entry)).join('');
    window.PPEExplorerMenus.bind(lessonFileTree);
    lessonFileTree.querySelectorAll('[data-indeterminate="true"]').forEach((checkbox) => {
      checkbox.indeterminate = true;
    });
    renderSearchResults();
    updateExplorerStatus();
  }

  function appendFolder(name, parentFolderId = null) {
    const folderName = name.trim();
    if (folderName.includes('.')) {
      setMessage('フォルダ名にドット（.）は使用できません。', 'warning');
      return false;
    }
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
    if (!subtreeFitsPath({ type: 'folder', name: folderName, children: [] },
      `${parentFolder?.path || ROOT_FOLDER_NAME}/${folderName}`)) {
      setMessage(`フォルダのパスは ${MAX_ENTRY_PATH_LENGTH} 文字以内で指定してください。`, 'warning');
      return false;
    }

    const folderEntry = {
      id: createId('folder'),
      type: 'folder',
      name: folderName,
      updatedAt: new Date().toISOString(),
      children: []
    };

    if (parentFolder && isFolder(parentFolder)) {
      parentFolder.children.push(folderEntry);
      parentFolder.updatedAt = folderEntry.updatedAt;
    } else {
      insertEntryAtRoot(folderEntry);
    }

    refreshState();
    renderFileTree(lessonState);
    return true;
  }

  function appendFile(name, parentFolderId = null) {
    const fileName = /\.py$/i.test(name) ? name : `${name}.py`;
    if ([...fileName].length > MAX_ENTRY_NAME_LENGTH) {
      setMessage(`ファイル名は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。`, 'warning');
      return false;
    }
    const defaultParent = findEntryById(lessonState, currentFileId)?.parentFolder || lessonState.find((entry) => isFolder(entry) && !isRecycleBin(entry)) || null;
    const parentFolder = parentFolderId
      ? findEntryById(lessonState, parentFolderId)?.entry
      : defaultParent;

    if (hasNameConflict(parentFolder, fileName)) {
      setMessage(`「${fileName}」はこの場所にすでに存在します。別の名前を入力してください。`, 'warning');
      return false;
    }
    if (!subtreeFitsPath({ type: 'file', name: fileName }, `${parentFolder?.path || ROOT_FOLDER_NAME}/${fileName}`)) {
      setMessage(`ファイルのパスは ${MAX_ENTRY_PATH_LENGTH} 文字以内で指定してください。`, 'warning');
      return false;
    }

    const newFile = {
      id: createId('file'),
      type: 'file',
      name: fileName,
      updatedAt: new Date().toISOString(),
      content: 'print("new lesson file")'
    };

    if (parentFolder && isFolder(parentFolder)) {
      parentFolder.children.push(newFile);
      parentFolder.updatedAt = newFile.updatedAt;
    } else {
      insertEntryAtRoot(newFile);
    }

    currentFileId = newFile.id;
    currentSelectionId = parentFolder?.id || ROOT_FOLDER_ID;
    refreshState();
    renderFileTree(lessonState);
    updateActiveFileUI();
    return true;
  }

  function moveEntry(entryId, destinationPath) {
    const found = findEntryById(lessonState, entryId);
    if (!found) {
      return false;
    }

    const { entry, parentEntries } = found;
    const sourceFolder = found.parentFolder;
    const currentIndex = parentEntries.findIndex((item) => item.id === entry.id);
    if (currentIndex === -1) {
      return false;
    }

    parentEntries.splice(currentIndex, 1);
    const movedAt = new Date().toISOString();
    if (sourceFolder) sourceFolder.updatedAt = movedAt;

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
      rootFolder.updatedAt = movedAt;
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
    destinationFolder.updatedAt = movedAt;
    destinationFolder.children.push(entry);
    refreshState();
    return true;
  }

  async function deleteEntry(entryId) {
    return trashEntries([entryId]);
  }

  async function trashEntries(entryIds) {
    const entries = getNormalizedSelection(new Set(entryIds));
    const recycleBin = getRecycleBin();
    if (!entries.length || !recycleBin) return;
    const containsCurrentFile = entries.some((entry) =>
      entry.id === currentFileId || (isFolder(entry) && findEntryById([entry], currentFileId))
    );
    const details = [];
    function appendTrashDetails(entry, depth = 0) {
      if (entry.deletedAt || isRecycleBin(entry)) return;
      details.push(`${depth ? `${'　'.repeat(depth)}└ ` : ''}${isFolder(entry) ? 'フォルダ: ' : 'ファイル: '}${entry.path}`);
      (entry.children || []).forEach((child) => appendTrashDetails(child, depth + 1));
    }
    entries.forEach((entry) => appendTrashDetails(entry));
    if (containsCurrentFile) details.push('編集中の未保存コードと実行結果が失われます。');
    const confirmed = pageFeedback && typeof pageFeedback.danger === 'function'
      ? await pageFeedback.danger({
          title: `選択した ${entries.length} 件をごみ箱へ移動しますか？`,
          kicker: '削除する内容の確認',
          message: '以下のファイルとフォルダをごみ箱へ移します。完全には削除しません。',
          details,
          confirmLabel: '削除',
          cancelLabel: 'キャンセル'
        })
      : false;

    if (!confirmed) {
      return;
    }

    const currentWasDeleted = containsCurrentFile;
    const operatedSelectionSnapshot = entries.map((entry) => ({
      id: entry.id,
      path: entry.path,
      folder: isFolder(entry)
    }));
    const selectedTarget = findEntryById(lessonState, currentSelectionId);
    const targetWillBeDeleted = selectedTarget && entries.some((entry) =>
      selectedTarget.entry.id === entry.id
      || (isFolder(entry) && isDescendantPath(selectedTarget.entry.path, entry.path))
    );
    let fallbackTarget = selectedTarget?.parentFolder || getRootFolder();
    while (fallbackTarget && entries.some((entry) =>
      fallbackTarget.id === entry.id || (isFolder(entry) && isDescendantPath(fallbackTarget.path, entry.path))
    )) {
      fallbackTarget = findEntryById(lessonState, fallbackTarget.id)?.parentFolder || getRootFolder();
    }
    const fallbackTargetId = fallbackTarget?.id || ROOT_FOLDER_ID;
    entries.forEach((entry, index) => {
      const found = findEntryById(lessonState, entry.id);
      if (!found) return;
      const at = found.parentEntries.findIndex((item) => item.id === entry.id);
      if (at < 0) return;
      entry.originalPath = entry.path;
      found.parentEntries.splice(at, 1);
      if (found.parentFolder) found.parentFolder.updatedAt = new Date().toISOString();
      entry.deletedAt = Date.now() + index;
      recycleBin.children.push(entry);
    });
    if (targetWillBeDeleted) currentSelectionId = fallbackTargetId;
    clearOperatedSelection(entries, operatedSelectionSnapshot);
    entries.forEach((entry) => { selectedEntryIds.delete(entry.id); });
    refreshState();
    renderFileTree(lessonState);
    if (currentWasDeleted) {
      ensureCurrentFileSelection();
      updateActiveFileUI();
    } else {
      updateCurrentFilePathUI();
    }
    const deleteMessage = `${entries.length} 件を削除済みフォルダへ移動しました。`;
    setMessage(deleteMessage, 'success');
    if (pageFeedback && typeof pageFeedback.toast === 'function') {
      pageFeedback.toast({
        title: '授業演習',
        message: deleteMessage,
        variant: 'success'
      });
    } else if (typeof feedback.showToast === 'function') {
      feedback.showToast({
        title: '授業演習',
        message: deleteMessage,
        variant: 'success'
      });
    }
  }

  function suggestDuplicateName(entry, targetParent = findEntryById(lessonState, entry.id)?.parentFolder || getRootFolder()) {
    const parent = targetParent;
    const extension = isFile(entry) && /\.py$/i.test(entry.name) ? '.py' : '';
    const stem = extension ? entry.name.slice(0, -extension.length) : entry.name;
    let suffix = 2;
    let candidate = `${stem} (${suffix})${extension}`;
    while (hasNameConflict(parent, candidate)) {
      suffix += 1;
      candidate = `${stem} (${suffix})${extension}`;
    }
    return candidate;
  }

  function cloneSavedEntry(entry, name) {
    const cloned = {
      id: createId(isFolder(entry) ? 'folder' : 'file'),
      type: entry.type,
      name,
      updatedAt: new Date().toISOString()
    };
    if (isFile(entry)) {
      cloned.content = entry.content || '';
      return cloned;
    }
    cloned.children = (entry.children || [])
      .filter((child) => !child.deletedAt && !isRecycleBin(child))
      .map((child) => cloneSavedEntry(child, child.name));
    return cloned;
  }

  async function duplicateEntry(entryId) {
    const found = findEntryById(lessonState, entryId);
    if (!found || found.entry.deletedAt || isRecycleBin(found.entry) || isRootFolder(found.entry)) return;
    const proposedName = suggestDuplicateName(found.entry);
    const name = promptEntryName('保存済みの内容を複製します。新しい名前を入力してください。', isFile(found.entry) ? 'ファイル' : 'フォルダ', proposedName);
    if (!name) return;
    const normalizedName = isFile(found.entry) && !/\.py$/i.test(name) ? `${name}.py` : name;
    if ([...normalizedName].length > MAX_ENTRY_NAME_LENGTH) {
      setMessage(`複製名は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。`, 'warning');
      return;
    }
    if (hasNameConflict(found.parentFolder, normalizedName)) {
      setMessage(`「${normalizedName}」はこの場所にすでに存在します。`, 'warning');
      return;
    }
    if (!subtreeFitsPath(found.entry, `${found.parentFolder?.path || ROOT_FOLDER_NAME}/${normalizedName}`)) {
      setMessage(`複製後のパスは ${MAX_ENTRY_PATH_LENGTH} 文字以内にしてください。`, 'warning');
      return;
    }
    const accepted = pageFeedback
      ? await pageFeedback.confirm({
          title: '保存済み内容を複製しますか？',
          message: '未保存コード・実行結果・実行履歴・配信状態は複製しません。',
          details: [`コピー元: ${found.entry.path}`, `保存先: ${found.parentFolder?.path || ROOT_FOLDER_NAME}`, `新しい名前: ${normalizedName}`],
          confirmLabel: '複製',
          cancelLabel: 'キャンセル'
        })
      : false;
    if (!accepted) return;
    const copy = cloneSavedEntry(found.entry, normalizedName);
    found.parentEntries.push(copy);
    if (found.parentFolder) found.parentFolder.updatedAt = new Date().toISOString();
    refreshState();
    renderFileTree(lessonState);
    setMessage(`「${normalizedName}」を複製しました。`, 'success');
  }

  async function renameEntry(entryId) {
    const found = findEntryById(lessonState, entryId);
    if (!found || !found.parentFolder || found.entry.deletedAt || isRootFolder(found.entry) || isRecycleBin(found.entry)) return;
    const name = promptEntryName(`「${found.entry.name}」の新しい名前を入力してください。`, isFile(found.entry) ? 'ファイル' : 'フォルダ');
    if (!name) return;
    const normalizedName = isFile(found.entry) && !/\.py$/i.test(name) ? `${name}.py` : name;
    if ([...normalizedName].length > MAX_ENTRY_NAME_LENGTH) {
      setMessage(`項目名は ${MAX_ENTRY_NAME_LENGTH} 文字以内で入力してください。`, 'warning');
      return;
    }
    if (normalizedName !== found.entry.name && hasNameConflict(found.parentFolder, normalizedName, found.entry.id)) {
      setMessage(`「${normalizedName}」はこの場所にすでに存在します。`, 'warning');
      return;
    }
    if (!subtreeFitsPath(found.entry, `${found.parentFolder.path}/${normalizedName}`)) {
      setMessage(`変更後のパスは ${MAX_ENTRY_PATH_LENGTH} 文字以内にしてください。`, 'warning');
      return;
    }
    found.entry.name = normalizedName;
    found.entry.updatedAt = new Date().toISOString();
    found.parentFolder.updatedAt = found.entry.updatedAt;
    refreshState();
    renderFileTree(lessonState);
    updateCurrentFilePathUI();
    setMessage(`「${normalizedName}」に名前を変更しました。`, 'success');
  }

  async function restoreEntries(entryIds) {
    const entries = entryIds.map((id) => findEntryById(lessonState, id)?.entry)
      .filter((entry) => entry?.deletedAt && !isRecycleBin(entry))
      .sort((left, right) =>
        (left.originalPath || left.path).split('/').length - (right.originalPath || right.path).split('/').length
        || left.id.localeCompare(right.id));
    if (!entries.length) return;
    const accepted = pageFeedback
      ? await pageFeedback.confirm({
          title: `${entries.length} 件を復元しますか？`,
          message: '元の場所がない場合はルートへ復元します。同名がある場合は別名を指定します。',
          details: entries.map((entry) => `${entry.originalPath || entry.path}（削除単位）`),
          confirmLabel: '復元',
          cancelLabel: 'キャンセル'
        })
      : false;
    if (!accepted) return;

    const restorePlan = [];
    const plannedRestoreNames = new Map();
    for (const entry of entries) {
      const originalPath = entry.originalPath || entry.path;
      const parentPath = originalPath.split('/').slice(0, -1).join('/');
      const selectedParent = entries.find((candidate) =>
        isFolder(candidate) && (candidate.originalPath || candidate.path) === parentPath
      );
      const selectedParentPlan = selectedParent
        ? restorePlan.find((item) => item.entry.id === selectedParent.id)
        : null;
      let destination = selectedParent
        || getAllFolders(lessonState).find((folder) =>
          folder.path === parentPath && !folder.deletedAt && !isEntryInTrash(folder)
        );
      if (!destination || isRecycleBin(destination)) destination = getRootFolder();
      const destinationPath = selectedParentPlan?.finalPath || destination.path;
      let name = entry.name;
      const folderNames = plannedRestoreNames.get(destination.id) || new Set();
      const conflicts = (candidate) => hasNameConflict(destination, candidate, entry.id)
        || folderNames.has(candidate.toLocaleLowerCase('ja'));
      if (conflicts(name)) {
        let suggested = suggestDuplicateName(entry, destination);
        let suffix = 2;
        while (folderNames.has(suggested.toLocaleLowerCase('ja'))) {
          const extension = isFile(entry) && /\.py$/i.test(entry.name) ? '.py' : '';
          const stem = extension ? suggested.slice(0, -extension.length).replace(/ \(\d+\)$/, '') : entry.name;
          suggested = `${stem} (${suffix += 1})${extension}`;
        }
        const proposed = promptEntryName(
          `「${name}」が既にあります。復元後の名前を入力してください。`,
          isFile(entry) ? 'ファイル' : 'フォルダ',
          suggested
        );
        if (!proposed) return;
        name = isFile(entry) && !/\.py$/i.test(proposed) ? `${proposed}.py` : proposed;
        if (!name || [...name].length > MAX_ENTRY_NAME_LENGTH || conflicts(name)) {
          setMessage('復元先の名前が無効か、すでに使われています。', 'warning');
          return;
        }
      }
      if (!subtreeFitsPath(entry, `${destinationPath}/${name}`)) {
        setMessage(`「${name}」の移動後のパスが長すぎます。`, 'warning');
        return;
      }
      folderNames.add(name.toLocaleLowerCase('ja'));
      plannedRestoreNames.set(destination.id, folderNames);
      restorePlan.push({ entry, destination, name, finalPath: `${destinationPath}/${name}` });
    }

    restorePlan.forEach(({ entry, destination, name }) => {
      const found = findEntryById(lessonState, entry.id);
      if (!found) return;
      const index = found.parentEntries.findIndex((item) => item.id === entry.id);
      if (index >= 0) found.parentEntries.splice(index, 1);
      entry.name = name;
      destination.updatedAt = new Date().toISOString();
      delete entry.deletedAt;
      delete entry.originalPath;
      destination.children.push(entry);
      selectedTrashEntryIds.delete(entry.id);
    });
    refreshState();
    renderFileTree(lessonState);
    setMessage(`${restorePlan.length} 件を復元しました。`, 'success');
  }

  function getJstTimestamp() {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Tokyo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23'
    }).formatToParts(new Date()).reduce((result, item) => {
      result[item.type] = item.value;
      return result;
    }, {});
    return `${parts.year}${parts.month}${parts.day}-${parts.hour}${parts.minute}${parts.second}`;
  }

  function startBrowserDownload(blob, filename) {
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  }

  function addEntryToZip(entry, zipFolder, relativePath = '') {
    const path = relativePath ? `${relativePath}/${entry.name}` : entry.name;
    if (isFolder(entry)) {
      zipFolder.folder(path);
      (entry.children || []).filter((child) => !child.deletedAt && !isRecycleBin(child))
        .forEach((child) => addEntryToZip(child, zipFolder, path));
    } else if (isFile(entry)) {
      zipFolder.file(path, entry.content || '');
    }
  }

  async function downloadEntries(entryIds) {
    const uniqueIds = [...new Set(entryIds)];
    const unavailable = uniqueIds.filter((id) => {
      const entry = findEntryById(lessonState, id)?.entry;
      return !entry || isRootFolder(entry) || isRecycleBin(entry) || entry.deletedAt || isEntryInTrash(entry);
    });
    if (unavailable.length) {
      unavailable.forEach((id) => selectedEntryIds.delete(id));
      setMessage(`${unavailable.length} 件は取得できる状態ではなく、選択から外しました。`, 'warning');
      renderFileTree(lessonState);
    }
    const entries = getNormalizedSelection(new Set(uniqueIds.filter((id) => !unavailable.includes(id))));
    if (!entries.length) return;
    if (entries.length === 1 && isFile(entries[0])) {
      const file = entries[0];
      if (file.id === currentFileId && file.content !== getEditorValue()) {
        pageFeedback?.toast({ message: 'エクスプローラからは保存済み内容を取得します。未保存コードは含まれません。', variant: 'info' });
      }
      startBrowserDownload(new Blob([file.content || ''], { type: 'text/x-python;charset=utf-8' }), file.name);
      setMessage(`保存済みファイル「${file.name}」をダウンロードしました。`, 'success');
      return;
    }
    if (typeof JSZip === 'undefined') {
      setMessage('ZIPの作成に失敗しました。時間をおいて再試行してください。', 'danger');
      return;
    }
    const zip = new JSZip();
    let filename;
    if (entries.length === 1 && isFolder(entries[0])) {
      addEntryToZip(entries[0], zip);
      filename = `${entries[0].name}.zip`;
    } else {
      entries.forEach((entry) => {
        const relative = entry.path.startsWith(`${ROOT_FOLDER_NAME}/`)
          ? entry.path.slice(ROOT_FOLDER_NAME.length + 1, entry.path.lastIndexOf('/'))
          : '';
        addEntryToZip(entry, zip, relative);
      });
      filename = entries.some(isFolder)
        ? `${ROOT_FOLDER_NAME}.zip`
        : `${ROOT_FOLDER_NAME}_${getJstTimestamp()}.zip`;
    }
    const blob = await zip.generateAsync({ type: 'blob' });
    startBrowserDownload(blob, filename);
    setMessage(`${entries.length} 件を ${filename} としてダウンロードしました。`, 'success');
  }

  async function moveEntriesToDestination(entryIds, destinationPath) {
    const entries = getNormalizedSelection(new Set(entryIds));
    const destination = destinationPath === ROOT_FOLDER_LABEL
      ? getRootFolder()
      : getAllFolders(lessonState).find((folder) => folder.path === destinationPath && !folder.deletedAt);
    if (!entries.length || !destination) return false;
    const incomingNames = new Set();
    const plannedNames = new Map();
    for (const entry of entries) {
      if (isFolder(entry) && (!canPlaceFolderUnder(destination, entry)
        || isDescendantPath(destination.path, entry.path) || destination.id === entry.id)) {
        setMessage(`「${entry.path}」をこの場所へ移動できません。`, 'warning');
        return false;
      }
      let nextName = entry.name;
      const conflict = () => hasNameConflict(destination, nextName, entry.id)
        || incomingNames.has(nextName.toLocaleLowerCase('ja'));
      if (conflict()) {
        const proposed = promptEntryName(
          `移動先に「${entry.name}」があります。移動後の名前を入力してください。`,
          isFile(entry) ? 'ファイル' : 'フォルダ',
          suggestDuplicateName(entry, destination)
        );
        if (!proposed) return false;
        nextName = isFile(entry) && !/\.py$/i.test(proposed) ? `${proposed}.py` : proposed;
        if ([...nextName].length > MAX_ENTRY_NAME_LENGTH
          || (isFolder(entry) && nextName.includes('.')) || conflict()) {
          setMessage(`「${nextName}」は移動先で使用できません。`, 'warning');
          return false;
        }
      }
      plannedNames.set(entry.id, nextName);
      if (!subtreeFitsPath(entry, `${destination.path}/${nextName}`)) {
        setMessage(`「${nextName}」の移動後のパスが長すぎます。`, 'warning');
        return false;
      }
      incomingNames.add(nextName.toLocaleLowerCase('ja'));
    }
    const accepted = pageFeedback
      ? await pageFeedback.confirm({
          title: `${entries.length} 件を移動しますか？`,
          message: `移動先: ${destinationPath === ROOT_FOLDER_LABEL ? ROOT_FOLDER_NAME : destination.path}`,
          details: entries.map((entry) => {
            const nextName = plannedNames.get(entry.id);
            return nextName === entry.name ? entry.path : `${entry.path} → ${nextName}`;
          }),
          confirmLabel: '移動',
          cancelLabel: 'キャンセル'
        })
      : false;
    if (!accepted) return false;
    const operatedSelectionSnapshot = entries.map((entry) => ({
      id: entry.id,
      path: entry.path,
      folder: isFolder(entry)
    }));
    for (const entry of entries) {
      if (findEntryById(lessonState, entry.id)?.parentFolder?.id === destination.id) continue;
      entry.name = plannedNames.get(entry.id);
      entry.updatedAt = new Date().toISOString();
      const moved = moveEntry(entry.id, destinationPath);
      if (!moved) {
        setMessage('移動を完了できませんでした。選択は保持されています。', 'warning');
        return false;
      }
      if (entry.id === currentFileId) currentSelectionId = destination.id;
    }
    clearOperatedSelection(entries, operatedSelectionSnapshot);
    refreshState();
    ensureCurrentFileSelection();
    renderFileTree(lessonState);
    updateCurrentFilePathUI();
    return true;
  }

  async function handleTreeAction(action, entryId) {
    const found = findEntryById(lessonState, entryId);
    if (!found) {
      return;
    }

    if (action === 'move') {
      openMoveEntryModal(entryId);
    } else if (action === 'trash' || action === 'delete') {
      await deleteEntry(entryId);
    } else if (action === 'rename') {
      await renameEntry(entryId);
    } else if (action === 'duplicate') {
      await duplicateEntry(entryId);
    } else if (action === 'download') {
      await downloadEntries([entryId]);
    } else if (action === 'restore') {
      await restoreEntries([entryId]);
    }
  }

  function runCurrentCode() {
    saveCurrentFile(false);
    const code = getEditorValue();
    const file = getCurrentFile();

    let stdout = '';
    let stderr = '';

    if (/syntaxerror/i.test(code) || /raise\s+SyntaxError/.test(code)) {
      stderr = 'Traceback (most recent call last):\n  File "exercise.py", line 1\nSyntaxError: invalid syntax';
      stdout = '';
    } else if (/input\(/.test(code)) {
      stdout = `${file?.name || 'exercise.py'} を実行しました。\n入力待ちのコードを含むため、ここではサンプル出力を表示しています。`;
    } else if (/print\(/.test(code)) {
      stdout = `${file?.name || 'exercise.py'} を実行しました。\nprint 文を含むコードのため、サンプルの実行結果を表示しています。`;
    } else {
      stdout = `${file?.name || 'exercise.py'} を実行しました。\n出力はありません。`;
    }

    setConsoleValue(outputEditor, outputConsole, stdout || '出力はありません。');
    setConsoleValue(errorEditor, errorConsole, stderr || 'エラーはありません。');
    const hasError = Boolean(stderr.trim());
    if (stderrIndicator) {
      stderrIndicator.hidden = !hasError;
    }
    stderrTab?.setAttribute('aria-label', hasError ? 'エラー（エラーあり）' : 'エラー');
    if (runResultStatus) {
      runResultStatus.textContent = '実行完了';
      runResultStatus.className = 'badge text-bg-success';
    }
    if (runResultTime) {
      runResultTime.textContent = `最終実行: ${nowTimeLabel()}`;
    }
    showOutputPanel('lessonStdoutOutputPanel');

    if (lessonEditorMessage) {
      lessonEditorMessage.textContent = '実行が完了しました。実行結果カードに表示しています。';
    }

  }

  async function switchToFile(fileId) {
    const target = findEntryById(lessonState, fileId)?.entry;
    if (!isFile(target) || target.deletedAt) return false;
    const current = getCurrentFile();
    if (current && current.id !== target.id && current.content !== getEditorValue()) {
      const accepted = pageFeedback
        ? await pageFeedback.confirm({
            title: '未保存の変更があります',
            message: '現在のコードを保存してからファイルを切り替えます。',
            details: [current.path],
            confirmLabel: '保存して切り替える',
            cancelLabel: 'キャンセル'
          })
        : false;
      if (!accepted) return false;
      saveCurrentFile(false);
    }
    currentFileId = target.id;
    currentSelectionId = findEntryById(lessonState, target.id)?.parentFolder?.id || ROOT_FOLDER_ID;
    renderFileTree(lessonState);
    updateActiveFileUI();
    setMessage('ファイルを切り替えました。', 'info');
    return true;
  }

  function handleTreeCheckboxClick(event, input) {
    event.preventDefault();
    event.stopPropagation();
    if (input.hasAttribute('data-select-all')) {
      selectionMode = true;
      const topLevel = getSelectableRootEntries();
      const allSelected = topLevel.length > 0 && topLevel.every((entry) => selectedEntryIds.has(entry.id));
      topLevel.forEach((entry) => toggleSelection(selectedEntryIds, entry.id, !allSelected));
      selectionAnchorId = topLevel[0]?.id || null;
      renderFileTree(lessonState);
      lessonFileTree.querySelector('[data-select-all]')?.focus();
      return;
    }
    const trash = input.hasAttribute('data-check-trash');
    if (trash) trashSelectionMode = true;
    else selectionMode = true;
    const selection = trash ? selectedTrashEntryIds : selectedEntryIds;
    const id = input.getAttribute(trash ? 'data-check-trash' : 'data-check-entry');
    const visibleIds = getVisibleCheckIds(trash);
    const anchorId = trash ? trashSelectionAnchorId : selectionAnchorId;
    if (event.shiftKey && anchorId) {
      applyRangeSelection(selection, anchorId, id, visibleIds, input.checked);
    } else {
      toggleSelection(selection, id, input.checked);
    }
    if (trash) trashSelectionAnchorId = id;
    else selectionAnchorId = id;
    renderFileTree(lessonState);
    lessonFileTree.querySelector(`[${trash ? 'data-check-trash' : 'data-check-entry'}="${id}"]`)?.focus();
  }

  document.addEventListener('click', async (event) => {
    if (!event.target.closest('#lessonFileTree, [data-exercise-menu-overlay]')) return;
    const trashSummary = event.target.closest('[data-trash-selection-tools] > summary');
    if (trashSummary) {
      event.preventDefault();
      trashSelectionToolsOpen = !trashSelectionToolsOpen;
      trashSummary.parentElement.open = trashSelectionToolsOpen;
      return;
    }
    const trashSelectionToggle = event.target.closest('[data-toggle-trash-selection]');
    if (trashSelectionToggle) {
      trashSelectionMode = !trashSelectionMode;
      if (!trashSelectionMode) {
        selectedTrashEntryIds.clear();
        trashSelectionAnchorId = null;
      }
      renderFileTree(lessonState);
      return;
    }

    const checkbox = event.target.closest('[data-check-entry], [data-check-trash], [data-select-all]');
    if (checkbox) {
      handleTreeCheckboxClick(event, checkbox);
      return;
    }

    const toggleButton = event.target.closest('[data-toggle-folder]');
    if (toggleButton) {
      event.preventDefault();
      event.stopPropagation();
      const folderId = toggleButton.getAttribute('data-toggle-folder');
      if (toggleButton.closest('.lesson-trash') && folderId !== RECYCLE_BIN_ID) {
        if (expandedTrashFolderIds.has(folderId)) expandedTrashFolderIds.delete(folderId);
        else expandedTrashFolderIds.add(folderId);
      } else if (expandedFolderIds.has(folderId)) {
        expandedFolderIds.delete(folderId);
      } else {
        expandedFolderIds.add(folderId);
      }
      renderFileTree(lessonState);
      return;
    }

    if (event.target.closest('[data-trash-show-more]')) {
      trashDisplayLimit += 10;
      renderFileTree(lessonState);
      return;
    }

    if (event.target.closest('[data-restore-selected]')) {
      await restoreEntries([...selectedTrashEntryIds]);
      return;
    }

    if (event.target.closest('[data-clear-trash-selection]')) {
      selectedTrashEntryIds.clear();
      trashSelectionAnchorId = null;
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
      if (event.ctrlKey || event.metaKey || event.shiftKey) {
        const id = folderSelector.getAttribute('data-select-folder');
        selectionMode = true;
        if (event.shiftKey && selectionAnchorId) {
          applyRangeSelection(selectedEntryIds, selectionAnchorId, id, getVisibleCheckIds(), true);
        } else {
          toggleSelection(selectedEntryIds, id, !selectedEntryIds.has(id));
        }
        selectionAnchorId = id;
        renderFileTree(lessonState);
        return;
      }
      currentSelectionId = folderSelector.getAttribute('data-select-folder');
      renderFileTree(lessonState);
      setMessage('フォルダを選択しました。新しいファイルやフォルダはこの配下に作成されます。', 'info');
      return;
    }

    const fileButton = event.target.closest('[data-file-id]');
    if (!fileButton) {
      return;
    }

    const id = fileButton.getAttribute('data-file-id');
    if (event.ctrlKey || event.metaKey || event.shiftKey) {
      selectionMode = true;
      if (event.shiftKey && selectionAnchorId) {
        applyRangeSelection(selectedEntryIds, selectionAnchorId, id, getVisibleCheckIds(), true);
      } else {
        toggleSelection(selectedEntryIds, id, !selectedEntryIds.has(id));
      }
      selectionAnchorId = id;
      renderFileTree(lessonState);
      return;
    }
    await switchToFile(id);
  });

  lessonFileTree?.addEventListener('change', (event) => {
    const trashSearch = event.target.closest('#lessonTrashSearch');
    if (trashSearch) {
      trashSearchTerm = trashSearch.value;
      trashDisplayLimit = 10;
      renderFileTree(lessonState);
    }
  });

  lessonFileTree?.addEventListener('input', (event) => {
    const trashSearch = event.target.closest('#lessonTrashSearch');
    if (!trashSearch) return;
    const value = trashSearch.value;
    const cursor = trashSearch.selectionStart;
    trashSearchTerm = value;
    trashDisplayLimit = 10;
    renderFileTree(lessonState);
    const refreshed = lessonFileTree.querySelector('#lessonTrashSearch');
    refreshed?.focus();
    refreshed?.setSelectionRange(cursor, cursor);
  });

  document.querySelector('#lessonSelectSearchResults').addEventListener('click', () => {
    selectionMode = true;
    const term = treeSearchTerm.trim().toLocaleLowerCase('ja');
    getAllActiveEntries()
      .filter((entry) => entry.name.toLocaleLowerCase('ja').includes(term))
      .forEach((entry) => selectedEntryIds.add(entry.id));
    renderFileTree(lessonState);
  });
  document.querySelector('#lessonSelectAll').addEventListener('click', () => {
    getSelectableRootEntries().forEach((entry) => selectedEntryIds.add(entry.id));
    renderFileTree(lessonState);
  });
  searchResults?.addEventListener('click', async (event) => {
    const resultCheckbox = event.target.closest('[data-search-result-check]');
    if (resultCheckbox) {
      event.preventDefault();
      selectionMode = true;
      const id = resultCheckbox.getAttribute('data-search-result-check');
      if (event.shiftKey && selectionAnchorId) {
        applyRangeSelection(selectedEntryIds, selectionAnchorId, id, getVisibleCheckIds(), resultCheckbox.checked);
      } else {
        toggleSelection(selectedEntryIds, id, resultCheckbox.checked);
      }
      selectionAnchorId = id;
      renderFileTree(lessonState);
      searchResults.querySelector(`[data-search-result-check="${id}"]`)?.focus();
      return;
    }
    const result = event.target.closest('[data-search-result-id]');
    if (!result) return;
    const entry = findEntryById(lessonState, result.getAttribute('data-search-result-id'))?.entry;
    if (!entry) return;
    if (!isFolder(entry) && !(await switchToFile(entry.id))) return;
    treeSearchInput.value = '';
    treeSearchTerm = '';
    revealEntry(entry);
    if (isFolder(entry)) {
      currentSelectionId = entry.id;
      renderFileTree(lessonState);
      setMessage('フォルダの場所を表示しました。', 'info');
    }
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
      const name = promptEntryName('作成するファイル名を入力してください。', 'ファイル');
      if (!name) {
        return;
      }

      const targetFolder = getSelectedFolderTarget();
      if (appendFile(name, targetFolder?.id || null)) {
        setMessage(`ファイル「${name}」を追加しました。`, 'success');
      }
    });
  }

  if (uploadButton) {
    uploadButton.addEventListener('click', () => {
      openUploadEntryModal();
    });
  }

  selectionModeButton?.addEventListener('click', () => {
    selectionMode = !selectionMode;
    if (!selectionMode) {
      selectedEntryIds.clear();
      selectionAnchorId = null;
    }
    renderFileTree(lessonState);
  });

  treeSearchInput?.addEventListener('input', () => {
    treeSearchTerm = treeSearchInput.value;
    renderSearchResults();
    updateExplorerStatus();
  });


  treeSortSelect?.addEventListener('change', () => {
    treeSortMode = treeSortSelect.value === 'updated' ? 'updated' : 'name';
    refreshState();
    renderFileTree(lessonState);
  });

  revealActiveFileButton?.addEventListener('click', () => {
    const file = getCurrentFile();
    if (!file || file.deletedAt) return;
    treeSearchTerm = '';
    if (treeSearchInput) treeSearchInput.value = '';
    revealEntry(file);
  });

  collapseAllFoldersButton?.addEventListener('click', () => {
    const recycleBinExpanded = expandedFolderIds.has(RECYCLE_BIN_ID);
    expandedFolderIds.clear();
    expandedFolderIds.add(ROOT_FOLDER_ID);
    if (recycleBinExpanded) expandedFolderIds.add(RECYCLE_BIN_ID);
    renderFileTree(lessonState);
  });

  document.querySelector('#expandAllFoldersButton').addEventListener('click', () => {
    getAllFolders(lessonState).filter((folder) => !isRecycleBin(folder)
      && !folder.deletedAt && !isEntryInTrash(folder))
      .forEach((folder) => expandedFolderIds.add(folder.id));
    expandedFolderIds.add(ROOT_FOLDER_ID);
    renderFileTree(lessonState);
  });

  downloadSelectedButton?.addEventListener('click', async () => {
    try {
      await downloadEntries([...selectedEntryIds]);
    } catch (error) {
      setMessage('選択項目のダウンロードに失敗しました。', 'danger');
    }
  });

  moveSelectedButton?.addEventListener('click', () => {
    const entries = getNormalizedSelection(selectedEntryIds);
    if (!entries.length) return;
    openMoveEntryModal(entries[0].id, entries.map((entry) => entry.id));
  });

  trashSelectedButton?.addEventListener('click', async () => {
    await trashEntries([...selectedEntryIds]);
  });

  clearSelectedButton?.addEventListener('click', () => {
    selectedEntryIds.clear();
    selectionAnchorId = null;
    renderFileTree(lessonState);
  });

  document.addEventListener('keydown', async (event) => {
    const active = document.activeElement;
    const explorer = document.querySelector('.lesson-tree-section');
    if (!explorer?.contains(active) || document.querySelector('.modal.show')) return;
    const isTextControl = active.matches('input, textarea, select, [contenteditable="true"]');
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a' && !isTextControl) {
      event.preventDefault();
      if (active.closest('.lesson-trash')) {
        trashSelectionMode = true;
        getVisibleCheckIds(true).forEach((id) => selectedTrashEntryIds.add(id));
      } else if (treeSearchTerm.trim()) {
        selectionMode = true;
        searchResults?.querySelectorAll('[data-search-result-id]').forEach((result) => {
          selectedEntryIds.add(result.getAttribute('data-search-result-id'));
        });
      } else {
        selectionMode = true;
        getVisibleCheckIds().forEach((id) => selectedEntryIds.add(id));
      }
      renderFileTree(lessonState);
      return;
    }
    if (event.key === 'Escape' && !isTextControl) {
      if (active.closest('.lesson-trash')) {
        selectedTrashEntryIds.clear();
        trashSelectionAnchorId = null;
      } else {
        selectedEntryIds.clear();
        selectionAnchorId = null;
      }
      renderFileTree(lessonState);
      return;
    }
    const folderName = event.target.closest?.('[data-select-folder]');
    if (folderName && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      currentSelectionId = folderName.getAttribute('data-select-folder');
      renderFileTree(lessonState);
    }
  });

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      saveCurrentFile(true);
    });
  }

  if (downloadButton) {
    downloadButton.addEventListener('click', () => {
      downloadCurrentCode();
      setMessage('コードをダウンロードしました。', 'success');
      if (pageFeedback && typeof pageFeedback.toast === 'function') {
        pageFeedback.toast({
          title: '授業演習',
          message: 'コードをダウンロードしました。',
          variant: 'success'
        });
      }
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
  currentSelectionId = ROOT_FOLDER_ID;
  updateExplorerStatus();
  setConsoleValue(outputEditor, outputConsole, '実行待ちです。「実行」を押すと結果を表示します。');
  setConsoleValue(errorEditor, errorConsole, 'エラーはありません。');
});
