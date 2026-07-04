const feedback = window.PPEFeedback || {};
const pageFeedback = feedback.createPageFeedback ? feedback.createPageFeedback({ title: 'コード配信' }) : null;
const DISTRIBUTION_STATUS_SCHEDULED = '配信予約';
const DISTRIBUTION_STATUS_DISTRIBUTED = '配信済み';
const DISTRIBUTION_TREE_ROOT_ID = 'distribution-tree-root';
const DISTRIBUTION_MAX_ENTRY_NAME_LENGTH = 100;
const DISTRIBUTION_ROOT_LABEL = 'root';
const DISTRIBUTION_ROOT_NONE_TOKEN = '--/--';

const distributionTreeState = {
  root: createDistributionFolderNode('(root)', DISTRIBUTION_TREE_ROOT_ID),
  selectedFolderId: DISTRIBUTION_TREE_ROOT_ID,
  currentFileId: ''
};

const distributionEditorState = {
  dirty: false
};

const collapsedDistributionFolderIds = new Set();

let distributionRunResultModal = null;
let distributionUploadEntryModal = null;
let distributionMoveEntryModal = null;
let pendingDistributionUploadFolderId = DISTRIBUTION_TREE_ROOT_ID;
let pendingDistributionUploadItems = [];
let pendingDistributionUploadSourceMode = '';
let pendingDistributionUploadExcludedCount = 0;
let pendingDistributionMoveEntryId = '';
let pendingDistributionMoveDestinationId = '';
let openedDistributionTreeMenuCount = 0;
const DISTRIBUTION_UPLOAD_PREVIEW_LIMIT = 12;

const distributionState = {
  templates: [
    {
      id: 'TPL-001',
      name: '条件分岐セット',
      root: 'lesson-01',
      files: ['warmup/input_sample.py', 'warmup/if_branch.py', 'notes/class_memo.py'],
      schools: ['国際中等'],
      classes: ['1年A組', '1年B組'],
      classSchedules: [
        { className: '1年A組', immediate: false, scheduleAt: '2026-07-05T09:00' },
        { className: '1年B組', immediate: true, scheduleAt: '' }
      ],
      status: '公開',
      updatedAt: '2026-07-04 09:10'
    },
    {
      id: 'TPL-002',
      name: 'グラフ探索セット',
      root: 'lesson-04',
      files: ['task/main.py', 'task/graph_data.txt'],
      schools: ['附属高校'],
      classes: ['4年2組'],
      classSchedules: [
        { className: '4年2組', immediate: false, scheduleAt: '2026-07-05T13:30' }
      ],
      status: '下書き',
      updatedAt: '2026-07-03 14:30'
    }
  ],
  history: [
    {
      distributedAt: '2026-07-04 09:10',
      target: '国際中等 / 1年A組, 1年B組',
      templateId: 'TPL-001',
      template: '条件分岐セット',
      operator: 't001',
      status: DISTRIBUTION_STATUS_DISTRIBUTED
    },
    {
      distributedAt: '2026-07-03 14:30',
      target: '附属高校 / 4年2組',
      templateId: 'TPL-002',
      template: 'グラフ探索セット',
      operator: 't001',
      status: DISTRIBUTION_STATUS_SCHEDULED
    }
  ],
  editTemplateId: null
};

document.addEventListener('DOMContentLoaded', function() {
  initializeDistributionPage();
});

function initializeDistributionPage() {
  if (typeof bootstrap !== 'undefined') {
    const uploadEntryModalElement = document.getElementById('distributionUploadEntryModal');
    if (uploadEntryModalElement) {
      distributionUploadEntryModal = bootstrap.Modal.getOrCreateInstance(uploadEntryModalElement);
    }

    const moveEntryModalElement = document.getElementById('distributionMoveEntryModal');
    if (moveEntryModalElement) {
      distributionMoveEntryModal = bootstrap.Modal.getOrCreateInstance(moveEntryModalElement);
    }

    const runResultModalElement = document.getElementById('distributionRunResultModal');
    if (runResultModalElement) {
      distributionRunResultModal = bootstrap.Modal.getOrCreateInstance(runResultModalElement);
    }
  }

  initializeDistributionStructureEditor();
  bindFormEvents();
  bindActionButtons();
  updateSchoolDropdownLabel();
  updateClassDropdownLabel();
  refreshClassScheduleRows();
  renderHistoryTable();
  updateStats();
  syncPreview();
}

function bindFormEvents() {
  const form = document.getElementById('distributionForm');
  if (form) {
    form.addEventListener('submit', function(event) {
      event.preventDefault();
      upsertTemplate('保存');
    });
  }

  ['templateName', 'templateRoot']
    .forEach(function(id) {
      const element = document.getElementById(id);
      if (!element) {
        return;
      }
      element.addEventListener('input', syncPreview);
      element.addEventListener('change', syncPreview);
    });

  document.querySelectorAll('input[name="distributionSchoolTargets"]').forEach(function(element) {
    element.addEventListener('change', function() {
      updateSchoolDropdownLabel();
      syncPreview();
    });
  });

  document.querySelectorAll('input[name="distributionClassTargets"]').forEach(function(element) {
    element.addEventListener('change', function() {
      updateClassDropdownLabel();
      refreshClassScheduleRows();
      syncPreview();
    });
  });
}

function bindActionButtons() {
  const addFolderButton = document.getElementById('distributionAddFolderButton');
  if (addFolderButton) {
    addFolderButton.addEventListener('click', function() {
      addDistributionFolder();
    });
  }

  const addFileButton = document.getElementById('distributionAddFileButton');
  if (addFileButton) {
    addFileButton.addEventListener('click', function() {
      addDistributionFile();
    });
  }

  const uploadButton = document.getElementById('distributionUploadButton');
  if (uploadButton) {
    uploadButton.addEventListener('click', function() {
      openDistributionUploadEntryModal();
    });
  }

  const uploadSelectFilesButton = document.getElementById('distributionUploadSelectFilesButton');
  const uploadSourceFilesInput = document.getElementById('distributionUploadSourceFile');
  if (uploadSelectFilesButton && uploadSourceFilesInput) {
    uploadSelectFilesButton.addEventListener('click', function() {
      uploadSourceFilesInput.click();
    });
  }

  const uploadSelectFolderButton = document.getElementById('distributionUploadSelectFolderButton');
  const uploadSourceDirectoryInput = document.getElementById('distributionUploadSourceDirectory');
  if (uploadSelectFolderButton && uploadSourceDirectoryInput) {
    uploadSelectFolderButton.addEventListener('click', function() {
      uploadSourceDirectoryInput.click();
    });
  }

  if (uploadSourceFilesInput) {
    uploadSourceFilesInput.addEventListener('change', function() {
      if (uploadSourceDirectoryInput) {
        uploadSourceDirectoryInput.value = '';
      }
      setPendingDistributionUploadItems(uploadSourceFilesInput.files, 'files');
    });
  }

  if (uploadSourceDirectoryInput) {
    uploadSourceDirectoryInput.addEventListener('change', function() {
      if (uploadSourceFilesInput) {
        uploadSourceFilesInput.value = '';
      }
      setPendingDistributionUploadItems(uploadSourceDirectoryInput.files, 'folder');
    });
  }

  const treeContainer = document.getElementById('distributionTemplateTree');
  if (treeContainer) {
    treeContainer.addEventListener('shown.bs.dropdown', function() {
      openedDistributionTreeMenuCount += 1;
      treeContainer.classList.add('is-menu-open');
    });

    treeContainer.addEventListener('hidden.bs.dropdown', function() {
      openedDistributionTreeMenuCount = Math.max(0, openedDistributionTreeMenuCount - 1);
      if (openedDistributionTreeMenuCount === 0) {
        treeContainer.classList.remove('is-menu-open');
      }
    });

    treeContainer.addEventListener('click', function(event) {
      const toggleButton = event.target.closest('[data-toggle-folder]');
      if (toggleButton) {
        event.preventDefault();
        event.stopPropagation();
        const folderId = toggleButton.getAttribute('data-toggle-folder') || '';
        if (!folderId) {
          return;
        }
        if (collapsedDistributionFolderIds.has(folderId)) {
          collapsedDistributionFolderIds.delete(folderId);
        } else {
          collapsedDistributionFolderIds.add(folderId);
        }
        renderDistributionStructureTree();
        return;
      }

      const selectButton = event.target.closest('[data-tree-select-folder]');
      if (selectButton) {
        distributionTreeState.selectedFolderId = selectButton.getAttribute('data-tree-select-folder') || DISTRIBUTION_TREE_ROOT_ID;
        renderDistributionStructureTree();
        return;
      }

      const selectFileButton = event.target.closest('[data-tree-select-file]');
      if (selectFileButton) {
        distributionTreeState.currentFileId = selectFileButton.getAttribute('data-tree-select-file') || '';
        distributionEditorState.dirty = false;
        updateDistributionFileEditor();
        renderDistributionStructureTree();
        return;
      }

      const deleteButton = event.target.closest('[data-tree-delete-entry]');
      if (deleteButton) {
        const entryId = deleteButton.getAttribute('data-tree-delete-entry') || '';
        confirmAndRemoveDistributionEntry(entryId);
        return;
      }

      const moveButton = event.target.closest('[data-tree-move-entry]');
      if (moveButton) {
        const entryId = moveButton.getAttribute('data-tree-move-entry') || '';
        openDistributionMoveModal(entryId);
        return;
      }

      const addFolderMenuButton = event.target.closest('[data-tree-add-folder]');
      if (addFolderMenuButton) {
        const folderId = addFolderMenuButton.getAttribute('data-tree-add-folder') || '';
        addDistributionFolderToTarget(folderId);
        return;
      }

      const addFileMenuButton = event.target.closest('[data-tree-add-file]');
      if (addFileMenuButton) {
        const folderId = addFileMenuButton.getAttribute('data-tree-add-file') || '';
        addDistributionFileToTarget(folderId);
      }
    });
  }

  const uploadEntryModalElement = document.getElementById('distributionUploadEntryModal');
  if (uploadEntryModalElement) {
    uploadEntryModalElement.addEventListener('hidden.bs.modal', function() {
      resetDistributionUploadState();
    });

    uploadEntryModalElement.addEventListener('click', function(event) {
      const folderButton = event.target.closest('[data-upload-folder-id]');
      if (!folderButton) {
        return;
      }

      pendingDistributionUploadFolderId = folderButton.getAttribute('data-upload-folder-id') || DISTRIBUTION_TREE_ROOT_ID;
      renderDistributionUploadFolderTree();
      updateDistributionUploadConfirmState();
    });
  }

  const uploadConfirmButton = document.getElementById('distributionUploadEntryConfirmButton');
  if (uploadConfirmButton) {
    uploadConfirmButton.addEventListener('click', async function() {
      try {
        await importDistributionEntriesFromPending();
      } catch (error) {
        toast('アップロードに失敗しました。', 'danger');
      }
    });
  }

  const moveEntryModalElement = document.getElementById('distributionMoveEntryModal');
  if (moveEntryModalElement) {
    moveEntryModalElement.addEventListener('hidden.bs.modal', function() {
      closeDistributionMoveModal();
    });

    moveEntryModalElement.addEventListener('click', function(event) {
      const rootButton = event.target.closest('[data-move-root-target]');
      if (rootButton && pendingDistributionMoveEntryId) {
        pendingDistributionMoveDestinationId = DISTRIBUTION_TREE_ROOT_ID;
        renderDistributionMoveModal(pendingDistributionMoveEntryId);
        return;
      }

      const targetButton = event.target.closest('[data-move-target-id]');
      if (!targetButton || !pendingDistributionMoveEntryId) {
        return;
      }

      pendingDistributionMoveDestinationId = targetButton.getAttribute('data-move-target-id') || '';
      renderDistributionMoveModal(pendingDistributionMoveEntryId);
    });
  }

  const moveConfirmButton = document.getElementById('distributionMoveEntryConfirmButton');
  if (moveConfirmButton) {
    moveConfirmButton.addEventListener('click', function() {
      if (!pendingDistributionMoveEntryId || !pendingDistributionMoveDestinationId) {
        return;
      }

      const moved = moveDistributionEntry(pendingDistributionMoveEntryId, pendingDistributionMoveDestinationId);
      if (!moved) {
        toast('移動に失敗しました。', 'warning');
        return;
      }

      if (distributionMoveEntryModal) {
        distributionMoveEntryModal.hide();
      }
    });
  }

  const saveFileButton = document.getElementById('distributionSaveFileButton');
  if (saveFileButton) {
    saveFileButton.addEventListener('click', function() {
      applyDistributionFileEditor();
    });
  }

  const downloadFileButton = document.getElementById('distributionDownloadFileButton');
  if (downloadFileButton) {
    downloadFileButton.addEventListener('click', function() {
      downloadCurrentDistributionFile();
    });
  }

  const downloadAllButton = document.getElementById('distributionDownloadAllButton');
  if (downloadAllButton) {
    downloadAllButton.addEventListener('click', async function() {
      await downloadAllDistributionFiles();
    });
  }

  const runFileButton = document.getElementById('distributionRunFileButton');
  if (runFileButton) {
    runFileButton.addEventListener('click', function() {
      runDistributionCurrentCode();
    });
  }

  const fileEditor = document.getElementById('distributionFileEditor');
  if (fileEditor) {
    fileEditor.addEventListener('input', function() {
      distributionEditorState.dirty = true;
      updateDistributionEditorMessage('保存状態: 未反映の変更があります。');
    });
  }

  const saveTemplateButton = document.getElementById('saveTemplateButton');
  if (saveTemplateButton) {
    saveTemplateButton.addEventListener('click', function() {
      upsertTemplate('下書き');
    });
  }

  const resetFormButton = document.getElementById('resetFormButton');
  if (resetFormButton) {
    resetFormButton.addEventListener('click', function() {
      resetForm();
      toast('入力内容をリセットしました。', 'success');
    });
  }

  const downloadHistoryCsvButton = document.getElementById('downloadHistoryCsvButton');
  if (downloadHistoryCsvButton) {
    downloadHistoryCsvButton.addEventListener('click', function() {
      toast('配信履歴CSVを出力しました。（モック）', 'success');
    });
  }
}

function initializeDistributionStructureEditor() {
  distributionTreeState.root = createDistributionFolderNode('(root)', DISTRIBUTION_TREE_ROOT_ID);
  distributionTreeState.selectedFolderId = DISTRIBUTION_TREE_ROOT_ID;
  distributionTreeState.currentFileId = '';
  renderDistributionStructureTree();
  syncTemplateFilesFromTree();
  updateDistributionFileEditor();
}

function createDistributionFolderNode(name, id) {
  return {
    id: id || generateDistributionTreeId('dir'),
    type: 'folder',
    name: name,
    children: []
  };
}

function createDistributionFileNode(name, id) {
  return {
    id: id || generateDistributionTreeId('file'),
    type: 'file',
    name: name,
    content: buildDefaultDistributionFileContent(name)
  };
}

function buildDefaultDistributionFileContent(name) {
  const safeName = String(name || 'main.py');
  if (/\.py$/i.test(safeName)) {
    return '# ' + safeName + '\nprint("hello")\n';
  }
  return '# ' + safeName + '\n';
}

function generateDistributionTreeId(prefix) {
  return prefix + '-' + String(Date.now()) + '-' + String(Math.floor(Math.random() * 100000));
}

function findDistributionTreeNode(node, targetId, parent) {
  if (!node) {
    return null;
  }

  if (node.id === targetId) {
    return {
      node: node,
      parent: parent || null
    };
  }

  if (node.type !== 'folder' || !Array.isArray(node.children)) {
    return null;
  }

  for (let i = 0; i < node.children.length; i += 1) {
    const found = findDistributionTreeNode(node.children[i], targetId, node);
    if (found) {
      return found;
    }
  }

  return null;
}

function getSelectedDistributionFolder() {
  const selectedId = distributionTreeState.selectedFolderId || DISTRIBUTION_TREE_ROOT_ID;
  const found = findDistributionTreeNode(distributionTreeState.root, selectedId, null);
  if (found && found.node && found.node.type === 'folder') {
    return found.node;
  }

  distributionTreeState.selectedFolderId = DISTRIBUTION_TREE_ROOT_ID;
  return distributionTreeState.root;
}

function getCurrentDistributionFileNode() {
  if (!distributionTreeState.currentFileId) {
    return null;
  }

  const found = findDistributionTreeNode(distributionTreeState.root, distributionTreeState.currentFileId, null);
  if (!found || !found.node || found.node.type !== 'file') {
    return null;
  }

  return found.node;
}

function ensureCurrentDistributionFile() {
  const current = getCurrentDistributionFileNode();
  if (current) {
    return;
  }

  const files = [];
  collectDistributionFileNodes(distributionTreeState.root, files);
  distributionTreeState.currentFileId = files[0] ? files[0].id : '';
}

function collectDistributionFileNodes(node, collector) {
  if (!node) {
    return;
  }

  if (node.type === 'file') {
    collector.push(node);
    return;
  }

  if (node.type !== 'folder' || !Array.isArray(node.children)) {
    return;
  }

  node.children.forEach(function(child) {
    collectDistributionFileNodes(child, collector);
  });
}

function updateDistributionEditorMessage(message) {
  const element = document.getElementById('distributionEditorMessage');
  if (element) {
    element.textContent = message;
  }
}

function getDistributionNodePath(nodeId) {
  if (!nodeId) {
    return '-';
  }

  const parts = [];
  let cursor = findDistributionTreeNode(distributionTreeState.root, nodeId, null);
  while (cursor && cursor.node && cursor.node.id !== DISTRIBUTION_TREE_ROOT_ID) {
    parts.unshift(cursor.node.name);
    cursor = cursor.parent ? findDistributionTreeNode(distributionTreeState.root, cursor.parent.id, null) : null;
  }

  return parts.length ? parts.join('/') : '-';
}

function updateDistributionFileEditor() {
  const editor = document.getElementById('distributionFileEditor');
  const pathBadge = document.getElementById('distributionCurrentPathBadge');
  const fileTab = document.getElementById('distributionCurrentFileTab');
  if (!editor || !pathBadge || !fileTab) {
    return;
  }

  ensureCurrentDistributionFile();
  const fileNode = getCurrentDistributionFileNode();
  if (!fileNode) {
    editor.value = '';
    editor.disabled = true;
    pathBadge.textContent = 'Path: -';
    fileTab.textContent = 'ファイル未選択';
    updateDistributionEditorMessage('ファイルを追加すると内容を編集できます。');
    return;
  }

  editor.disabled = false;
  editor.value = fileNode.content || '';
  pathBadge.textContent = 'Path: ' + getDistributionNodePath(fileNode.id);
  fileTab.textContent = fileNode.name;
  updateDistributionEditorMessage('保存状態: 反映済みです。');
}

function applyDistributionFileEditor() {
  const editor = document.getElementById('distributionFileEditor');
  const fileNode = getCurrentDistributionFileNode();
  if (!editor || !fileNode) {
    toast('編集対象のファイルを選択してください。', 'warning');
    return;
  }

  fileNode.content = editor.value;
  distributionEditorState.dirty = false;
  updateDistributionEditorMessage('保存状態: 反映済みです。');
  toast('ファイル内容を反映しました。', 'success');
}

function buildDistributionFolderPath(folderId) {
  if (!folderId || folderId === DISTRIBUTION_TREE_ROOT_ID) {
    return 'ルート';
  }

  const parts = [];
  let cursorId = folderId;
  while (cursorId && cursorId !== DISTRIBUTION_TREE_ROOT_ID) {
    const found = findDistributionTreeNode(distributionTreeState.root, cursorId, null);
    if (!found || !found.node) {
      break;
    }
    parts.unshift(found.node.name);
    cursorId = found.parent ? found.parent.id : DISTRIBUTION_TREE_ROOT_ID;
  }

  return parts.length ? parts.join('/') : 'ルート';
}

function updateDistributionStructureTargetLabel() {
  const targetLabel = document.getElementById('distributionStructureTarget');
  if (!targetLabel) {
    return;
  }

  targetLabel.textContent = '追加先: ' + buildDistributionFolderPath(distributionTreeState.selectedFolderId);
}

function promptDistributionEntryName(message, entryLabel) {
  const value = window.prompt(message + '\n' + entryLabel + 'は ' + String(DISTRIBUTION_MAX_ENTRY_NAME_LENGTH) + ' 文字以内で入力してください。');
  if (value === null) {
    return null;
  }

  const normalized = value.trim();
  if (!normalized) {
    toast(entryLabel + '名を入力してください。', 'warning');
    return '';
  }

  if (normalized.length > DISTRIBUTION_MAX_ENTRY_NAME_LENGTH) {
    toast(entryLabel + '名は ' + String(DISTRIBUTION_MAX_ENTRY_NAME_LENGTH) + ' 文字以内で入力してください。', 'warning');
    return '';
  }

  return normalized;
}

function addDistributionFolder() {
  addDistributionFolderToTarget(distributionTreeState.selectedFolderId || DISTRIBUTION_TREE_ROOT_ID);
}

function addDistributionFolderToTarget(folderId) {
  const folderName = promptDistributionEntryName('追加するフォルダ名を入力してください。', 'フォルダ');
  if (!folderName) {
    return;
  }

  const normalized = folderName.trim();
  if (!normalized) {
    toast('フォルダ名を入力してください。', 'warning');
    return;
  }

  const targetFound = findDistributionTreeNode(distributionTreeState.root, folderId, null);
  const targetFolder = targetFound && targetFound.node && targetFound.node.type === 'folder'
    ? targetFound.node
    : getSelectedDistributionFolder();
  if (targetFolder.children.some(function(child) { return child.name === normalized; })) {
    toast('同じ名前の項目が既に存在します。', 'warning');
    return;
  }

  distributionTreeState.selectedFolderId = targetFolder.id;
  targetFolder.children.push(createDistributionFolderNode(normalized));
  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();
  syncPreview();
}

function addDistributionFile() {
  addDistributionFileToTarget(distributionTreeState.selectedFolderId || DISTRIBUTION_TREE_ROOT_ID);
}

function addDistributionFileToTarget(folderId) {
  const fileName = promptDistributionEntryName('追加するファイル名を入力してください。\n拡張子 .py は自動的に追加されます。', 'ファイル');
  if (!fileName) {
    return;
  }

  const normalized = /\.py$/i.test(fileName) ? fileName : fileName + '.py';

  const targetFound = findDistributionTreeNode(distributionTreeState.root, folderId, null);
  const targetFolder = targetFound && targetFound.node && targetFound.node.type === 'folder'
    ? targetFound.node
    : getSelectedDistributionFolder();
  if (targetFolder.children.some(function(child) { return child.name === normalized; })) {
    toast('同じ名前の項目が既に存在します。', 'warning');
    return;
  }

  distributionTreeState.selectedFolderId = targetFolder.id;
  const newFile = createDistributionFileNode(normalized);
  targetFolder.children.push(newFile);
  distributionTreeState.currentFileId = newFile.id;
  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();
  syncPreview();
}

function ensureDistributionFolderChild(parent, folderName) {
  let folder = (Array.isArray(parent.children) ? parent.children : []).find(function(child) {
    return child.type === 'folder' && child.name === folderName;
  });

  if (!folder) {
    folder = createDistributionFolderNode(folderName);
    parent.children.push(folder);
  }

  return folder;
}

function isValidDistributionUploadTarget(folder) {
  return !!folder && folder.type === 'folder';
}

function resetDistributionUploadState() {
  pendingDistributionUploadFolderId = distributionTreeState.selectedFolderId || DISTRIBUTION_TREE_ROOT_ID;
  pendingDistributionUploadItems = [];
  pendingDistributionUploadSourceMode = '';
  pendingDistributionUploadExcludedCount = 0;

  const sourceFileInput = document.getElementById('distributionUploadSourceFile');
  if (sourceFileInput) {
    sourceFileInput.value = '';
  }

  const sourceDirectoryInput = document.getElementById('distributionUploadSourceDirectory');
  if (sourceDirectoryInput) {
    sourceDirectoryInput.value = '';
  }

  const uploadFileName = document.getElementById('distributionUploadFileName');
  if (uploadFileName) {
    uploadFileName.textContent = '未選択';
  }

  const previewList = document.getElementById('distributionUploadFilePreviewList');
  if (previewList) {
    previewList.innerHTML = '<li class="distribution-upload-preview-item is-empty">選択したファイル名がここに表示されます。</li>';
  }

  updateDistributionUploadConfirmState();
}

function setPendingDistributionUploadItems(files, mode) {
  const selectedItems = Array.from(files || []).filter(function(file) {
    return file && file.name;
  });

  pendingDistributionUploadItems = selectedItems.filter(function(file) {
    const rawPath = String(file.webkitRelativePath || file.name || '').trim();
    return /\.py$/i.test(rawPath);
  });
  pendingDistributionUploadExcludedCount = Math.max(0, selectedItems.length - pendingDistributionUploadItems.length);
  pendingDistributionUploadSourceMode = pendingDistributionUploadItems.length ? mode : '';

  const uploadFileName = document.getElementById('distributionUploadFileName');
  if (uploadFileName) {
    if (!pendingDistributionUploadItems.length) {
      uploadFileName.textContent = pendingDistributionUploadExcludedCount > 0 ? '.py ファイルが選択されていません' : '未選択';
    } else if (mode === 'folder') {
      const firstPath = pendingDistributionUploadItems[0].webkitRelativePath || pendingDistributionUploadItems[0].name;
      const rootName = firstPath.split('/').filter(function(item) { return item; })[0] || 'フォルダ';
      uploadFileName.textContent = rootName + '（' + String(pendingDistributionUploadItems.length) + '件の .py）';
    } else if (pendingDistributionUploadItems.length === 1) {
      uploadFileName.textContent = pendingDistributionUploadItems[0].name;
    } else {
      uploadFileName.textContent = String(pendingDistributionUploadItems.length) + '件の .py ファイルを選択中';
    }

    if (pendingDistributionUploadExcludedCount > 0 && pendingDistributionUploadItems.length > 0) {
      uploadFileName.textContent += '（.py以外 ' + String(pendingDistributionUploadExcludedCount) + '件は除外）';
    }
  }

  const previewList = document.getElementById('distributionUploadFilePreviewList');
  if (previewList) {
    if (!pendingDistributionUploadItems.length) {
      previewList.innerHTML = '<li class="distribution-upload-preview-item is-empty">選択したファイル名がここに表示されます。</li>';
    } else {
      const previewItems = pendingDistributionUploadItems.slice(0, DISTRIBUTION_UPLOAD_PREVIEW_LIMIT).map(function(file) {
        const rawPath = pendingDistributionUploadSourceMode === 'folder'
          ? (file.webkitRelativePath || file.name)
          : file.name;
        return '<li class="distribution-upload-preview-item">' + escapeHtml(rawPath) + '</li>';
      });

      const remaining = pendingDistributionUploadItems.length - previewItems.length;
      if (remaining > 0) {
        previewItems.push('<li class="distribution-upload-preview-item is-empty">...ほか ' + String(remaining) + ' 件</li>');
      }

      if (pendingDistributionUploadExcludedCount > 0) {
        previewItems.push('<li class="distribution-upload-preview-item is-empty">.py以外 ' + String(pendingDistributionUploadExcludedCount) + ' 件はアップロード対象外です。</li>');
      }

      previewList.innerHTML = previewItems.join('');
    }
  }

  updateDistributionUploadConfirmState();
}

function renderDistributionUploadFolderTreeNode(entry, isRootNode) {
  if (!isValidDistributionUploadTarget(entry)) {
    return '';
  }

  const rootMode = !!isRootNode;
  const isSelected = entry.id === pendingDistributionUploadFolderId;
  const childFolders = (entry.children || []).filter(function(child) {
    return child.type === 'folder';
  });
  const escapedName = escapeHtml(rootMode ? getDistributionRootDisplayName() : entry.name);
  const folderIcon = '<svg class="distribution-move-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">'
    + '<path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>'
    + '</svg>';

  return '<div class="distribution-move-folder' + (rootMode ? ' is-root-node' : '') + (isSelected ? ' is-selected-target' : '') + '">'
    + '<button class="distribution-move-folder-button" type="button" data-upload-folder-id="' + escapeHtml(entry.id) + '">'
    +   '<div class="distribution-move-folder-main">'
    +     folderIcon
    +     '<span class="distribution-move-folder-name">' + escapedName + '</span>'
    +   '</div>'
    + '</button>'
    + (childFolders.length > 0
      ? '<div class="distribution-move-children">' + childFolders.map(function(child) { return renderDistributionUploadFolderTreeNode(child, false); }).join('') + '</div>'
      : '')
    + '</div>';
}

function renderDistributionUploadFolderTree() {
  const container = document.getElementById('distributionUploadFolderTree');
  if (!container) {
    return;
  }

  const root = distributionTreeState.root;
  if (!root || root.type !== 'folder') {
    container.innerHTML = '';
    pendingDistributionUploadFolderId = '';
    return;
  }

  const selectedFound = findDistributionTreeNode(distributionTreeState.root, pendingDistributionUploadFolderId, null);
  if (!selectedFound || !selectedFound.node || selectedFound.node.type !== 'folder') {
    pendingDistributionUploadFolderId = DISTRIBUTION_TREE_ROOT_ID;
  }

  container.innerHTML = renderDistributionUploadFolderTreeNode(root, true);
}

function updateDistributionUploadConfirmState() {
  const button = document.getElementById('distributionUploadEntryConfirmButton');
  if (!button) {
    return;
  }

  const hasFiles = pendingDistributionUploadItems.length > 0;
  const hasTarget = !!pendingDistributionUploadFolderId;
  button.disabled = !(hasFiles && hasTarget);
}

function openDistributionUploadEntryModal() {
  resetDistributionUploadState();
  renderDistributionUploadFolderTree();
  updateDistributionUploadConfirmState();

  if (distributionUploadEntryModal) {
    distributionUploadEntryModal.show();
    return;
  }

  toast('アップロードモーダルを表示できませんでした。', 'warning');
}

async function importDistributionEntriesFromPending() {
  if (!pendingDistributionUploadItems.length) {
    toast('アップロードするファイルを選択してください。', 'warning');
    return;
  }

  const foundTarget = findDistributionTreeNode(distributionTreeState.root, pendingDistributionUploadFolderId, null);
  const targetFolder = foundTarget && foundTarget.node && foundTarget.node.type === 'folder'
    ? foundTarget.node
    : null;
  if (!targetFolder) {
    toast('追加先フォルダを選択してください。', 'warning');
    return;
  }

  await importDistributionEntries(pendingDistributionUploadItems, pendingDistributionUploadSourceMode, targetFolder);

  if (distributionUploadEntryModal) {
    distributionUploadEntryModal.hide();
  }
}

function isDescendantDistributionNode(ancestorNode, targetId) {
  if (!ancestorNode || !targetId) {
    return false;
  }

  if (ancestorNode.id === targetId) {
    return true;
  }

  if (ancestorNode.type !== 'folder' || !Array.isArray(ancestorNode.children)) {
    return false;
  }

  return ancestorNode.children.some(function(child) {
    return isDescendantDistributionNode(child, targetId);
  });
}

function collectDistributionMoveTargets(entryToMove) {
  const folders = [];
  collectDistributionFolderNodes(distributionTreeState.root, folders);
  return folders.filter(function(folder) {
    if (folder.id === entryToMove.id) {
      return false;
    }

    if (entryToMove.type === 'folder' && isDescendantDistributionNode(entryToMove, folder.id)) {
      return false;
    }

    return true;
  });
}

function collectDistributionFolderNodes(node, collector) {
  if (!node) {
    return;
  }

  if (node.type === 'folder') {
    collector.push(node);
  }

  if (node.type !== 'folder' || !Array.isArray(node.children)) {
    return;
  }

  node.children.forEach(function(child) {
    collectDistributionFolderNodes(child, collector);
  });
}

function renderDistributionMoveFolderTree(entry, validTargetIds, isRootNode) {
  if (!entry || entry.type !== 'folder') {
    return '';
  }

  const rootMode = !!isRootNode;
  const childFolders = (entry.children || []).filter(function(child) {
    return child.type === 'folder' && validTargetIds.has(child.id);
  });
  const escapedName = escapeHtml(rootMode ? getDistributionRootDisplayName() : entry.name);
  const isSelected = pendingDistributionMoveDestinationId === entry.id;
  const folderIcon = '<svg class="distribution-move-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">'
    + '<path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>'
    + '</svg>';

  return '<div class="distribution-move-folder' + (rootMode ? ' is-root-node' : '') + (isSelected ? ' is-selected-target' : '') + '">'
    + '<button class="distribution-move-folder-button" type="button" '
    + (rootMode ? 'data-move-root-target="true"' : 'data-move-target-id="' + escapeHtml(entry.id) + '"') + '>'
    +   '<div class="distribution-move-folder-main">'
    +     folderIcon
    +     '<span class="distribution-move-folder-name">' + escapedName + '</span>'
    +   '</div>'
    + '</button>'
    + (childFolders.length > 0
      ? '<div class="distribution-move-children">' + childFolders.map(function(child) { return renderDistributionMoveFolderTree(child, validTargetIds, false); }).join('') + '</div>'
      : '')
    + '</div>';
}

function renderDistributionMoveModal(entryId) {
  const found = findDistributionTreeNode(distributionTreeState.root, entryId, null);
  if (!found || !found.node) {
    return;
  }

  const moveEntry = found.node;
  const validTargets = collectDistributionMoveTargets(moveEntry);
  const validTargetIds = new Set(validTargets.map(function(folder) { return folder.id; }));

  const currentPathElement = document.getElementById('distributionMoveEntryCurrentPath');
  if (currentPathElement) {
    currentPathElement.textContent = '現在: ' + getDistributionNodePath(moveEntry.id);
  }

  const treeElement = document.getElementById('distributionMoveEntryTree');
  if (treeElement) {
    treeElement.innerHTML = renderDistributionMoveFolderTree(distributionTreeState.root, validTargetIds, true);
  }

  const emptyElement = document.getElementById('distributionMoveEntryEmpty');
  if (emptyElement) {
    emptyElement.classList.toggle('d-none', validTargetIds.size > 0);
  }

  const confirmButton = document.getElementById('distributionMoveEntryConfirmButton');
  if (confirmButton) {
    confirmButton.disabled = !pendingDistributionMoveDestinationId;
  }
}

function openDistributionMoveModal(entryId) {
  const found = findDistributionTreeNode(distributionTreeState.root, entryId, null);
  if (!found || !found.node) {
    return;
  }

  pendingDistributionMoveEntryId = entryId;
  pendingDistributionMoveDestinationId = '';
  renderDistributionMoveModal(entryId);

  if (distributionMoveEntryModal) {
    distributionMoveEntryModal.show();
    return;
  }

  toast('移動モーダルを表示できませんでした。', 'warning');
}

function closeDistributionMoveModal() {
  pendingDistributionMoveEntryId = '';
  pendingDistributionMoveDestinationId = '';

  const treeElement = document.getElementById('distributionMoveEntryTree');
  if (treeElement) {
    treeElement.innerHTML = '';
  }
}

function moveDistributionEntry(entryId, destinationFolderId) {
  const found = findDistributionTreeNode(distributionTreeState.root, entryId, null);
  if (!found || !found.parent || !Array.isArray(found.parent.children)) {
    return false;
  }

  const destinationFound = findDistributionTreeNode(distributionTreeState.root, destinationFolderId, null);
  if (!destinationFound || !destinationFound.node || destinationFound.node.type !== 'folder') {
    return false;
  }

  const destinationFolder = destinationFound.node;
  const movingEntry = found.node;

  if (movingEntry.type === 'folder' && isDescendantDistributionNode(movingEntry, destinationFolder.id)) {
    return false;
  }

  if ((destinationFolder.children || []).some(function(child) { return child.name === movingEntry.name; })) {
    toast('同名の項目が移動先に存在します。', 'warning');
    return false;
  }

  found.parent.children = found.parent.children.filter(function(child) {
    return child.id !== movingEntry.id;
  });
  destinationFolder.children.push(movingEntry);

  distributionTreeState.selectedFolderId = destinationFolder.id;
  if (movingEntry.type === 'file') {
    distributionTreeState.currentFileId = movingEntry.id;
  }

  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();
  syncPreview();
  toast('項目を移動しました。', 'success');
  return true;
}

async function confirmAndRemoveDistributionEntry(entryId) {
  if (!entryId || entryId === DISTRIBUTION_TREE_ROOT_ID) {
    return;
  }

  const found = findDistributionTreeNode(distributionTreeState.root, entryId, null);
  if (!found || !found.node) {
    return;
  }

  let confirmed = false;
  if (pageFeedback && typeof pageFeedback.danger === 'function') {
    confirmed = await pageFeedback.danger({
      title: '削除しますか？',
      message: '選択した項目をテンプレート構成から削除します。',
      detailTitle: '',
      details: [getDistributionNodePath(entryId)],
      confirmLabel: '削除する',
      cancelLabel: '戻る'
    });
  } else {
    confirmed = window.confirm('選択した項目を削除しますか？');
  }

  if (!confirmed) {
    return;
  }

  removeDistributionEntry(entryId);
  toast('項目を削除しました。', 'success');
}

function removeDistributionEntry(entryId) {
  if (!entryId || entryId === DISTRIBUTION_TREE_ROOT_ID) {
    return;
  }

  const found = findDistributionTreeNode(distributionTreeState.root, entryId, null);
  if (!found || !found.parent || !Array.isArray(found.parent.children)) {
    return;
  }

  found.parent.children = found.parent.children.filter(function(child) {
    return child.id !== entryId;
  });

  if (distributionTreeState.currentFileId === entryId) {
    distributionTreeState.currentFileId = '';
  }

  const selectedFound = findDistributionTreeNode(distributionTreeState.root, distributionTreeState.selectedFolderId, null);
  if (!selectedFound || selectedFound.node.type !== 'folder') {
    distributionTreeState.selectedFolderId = DISTRIBUTION_TREE_ROOT_ID;
  }

  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();
  syncPreview();
}

function renderDistributionStructureTree() {
  const container = document.getElementById('distributionTemplateTree');
  if (!container) {
    return;
  }

  const rootChildren = Array.isArray(distributionTreeState.root.children)
    ? distributionTreeState.root.children
    : [];

  if (!rootChildren.length) {
    container.innerHTML = '<div class="distribution-tree-empty">配信構成が未設定です。フォルダ・ファイルを追加してください。</div>';
    updateDistributionStructureTargetLabel();
    updateDistributionFileEditor();
    return;
  }

  container.innerHTML = rootChildren.map(function(child) {
    return renderDistributionTreeNode(child);
  }).join('');
  updateDistributionStructureTargetLabel();
}

function renderDistributionTreeNode(entry) {
  const isFolder = entry.type === 'folder';
  const isFile = entry.type === 'file';
  const selectedFolderClass = isFolder && distributionTreeState.selectedFolderId === entry.id ? ' is-selected-folder' : '';
  const activeFileClass = isFile && distributionTreeState.currentFileId === entry.id ? ' is-active' : '';
  const actionMenuItems = isFolder
    ? '<li><button class="dropdown-item distribution-tree-menu-item" type="button" data-tree-add-folder="' + escapeHtml(entry.id) + '">新しいフォルダ</button></li>'
      + '<li><button class="dropdown-item distribution-tree-menu-item" type="button" data-tree-add-file="' + escapeHtml(entry.id) + '">新しいファイル</button></li>'
      + '<li><button class="dropdown-item distribution-tree-menu-item" type="button" data-tree-move-entry="' + escapeHtml(entry.id) + '">移動</button></li>'
      + '<li><button class="dropdown-item distribution-tree-menu-item text-danger" type="button" data-tree-delete-entry="' + escapeHtml(entry.id) + '">削除</button></li>'
    : '<li><button class="dropdown-item distribution-tree-menu-item" type="button" data-tree-move-entry="' + escapeHtml(entry.id) + '">移動</button></li>'
      + '<li><button class="dropdown-item distribution-tree-menu-item text-danger" type="button" data-tree-delete-entry="' + escapeHtml(entry.id) + '">削除</button></li>';

  const actionMenu = '<div class="dropdown distribution-tree-entry-menu">'
    + '<button class="btn btn-outline-secondary btn-sm distribution-tree-menu-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="操作メニュー">⋮</button>'
    + '<ul class="dropdown-menu dropdown-menu-end distribution-tree-menu-dropdown">'
    +   actionMenuItems
    + '</ul>'
    + '</div>';
  const selectAttr = isFolder
    ? ' data-tree-select-folder="' + escapeHtml(entry.id) + '"'
    : ' data-tree-select-file="' + escapeHtml(entry.id) + '"';

  if (isFile) {
    return '<div class="distribution-tree-entry-row">'
      + '<button type="button" class="distribution-tree-file-button' + activeFileClass + '"' + selectAttr + '>'
      +   '<svg class="distribution-tree-file-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">'
      +     '<path d="M3.75 1.5A1.75 1.75 0 0 0 2 3.25v9.5c0 .966.784 1.75 1.75 1.75h8.5A1.75 1.75 0 0 0 14 12.75V5.56a1.75 1.75 0 0 0-.513-1.237L10.677 1.513A1.75 1.75 0 0 0 9.44 1H3.75zm5.5 1.06c.133.035.255.103.354.202l2.634 2.634a.75.75 0 0 1 .202.354H9.75a.5.5 0 0 1-.5-.5V2.56z"/>'
      +   '</svg>'
      +   '<span class="distribution-tree-file-copy"><span class="distribution-tree-file-name">' + escapeHtml(entry.name) + '</span></span>'
      + '</button>'
      + actionMenu
      + '</div>';
  }

  const isCollapsed = collapsedDistributionFolderIds.has(entry.id);
  const childrenClass = isCollapsed ? 'distribution-tree-folder-children is-collapsed' : 'distribution-tree-folder-children';
  const children = Array.isArray(entry.children) && entry.children.length
    ? '<div class="' + childrenClass + '">' + entry.children.map(function(child) { return renderDistributionTreeNode(child); }).join('') + '</div>'
    : '<div class="distribution-tree-folder-children is-empty"></div>';

  return '<div class="distribution-tree-folder">'
    + '<div class="distribution-tree-folder-header' + selectedFolderClass + '">'
    +   '<div class="distribution-tree-folder-title"' + selectAttr + ' role="button" tabindex="0" aria-label="' + escapeHtml(entry.name) + ' を選択">'
    +     '<button class="distribution-tree-folder-toggle' + (isCollapsed ? ' is-collapsed' : '') + '" type="button" data-toggle-folder="' + escapeHtml(entry.id) + '" aria-expanded="' + (isCollapsed ? 'false' : 'true') + '" aria-label="' + escapeHtml(entry.name) + ' を' + (isCollapsed ? '展開' : '折りたたみ') + '">'
    +       '<span class="distribution-tree-folder-toggle-icon">▾</span>'
    +     '</button>'
    +     '<svg class="distribution-tree-folder-icon" width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">'
    +       '<path d="M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z"/>'
    +     '</svg>'
    +     '<span class="distribution-tree-folder-name">' + escapeHtml(entry.name) + '</span>'
    +   '</div>'
    +   actionMenu
    + '</div>'
    + children
    + '</div>';
}

async function importDistributionEntries(fileList, mode, targetFolderNode) {
  const files = Array.from(fileList || []);
  if (!files.length) {
    return;
  }

  const targetFolder = targetFolderNode && targetFolderNode.type === 'folder'
    ? targetFolderNode
    : getSelectedDistributionFolder();
  let addedCount = 0;
  let skippedCount = 0;
  let firstAddedFileId = '';

  for (const file of files) {
    const rawPath = mode === 'folder'
      ? (file.webkitRelativePath || file.name)
      : file.name;
    const baseSegments = String(rawPath || '').split('/').filter(function(item) { return item; });
    if (!baseSegments.length) {
      skippedCount += 1;
      continue;
    }

    const segments = mode === 'folder' && baseSegments.length > 1
      ? baseSegments.slice(1)
      : baseSegments;
    if (!segments.length) {
      skippedCount += 1;
      continue;
    }

    let cursor = targetFolder;
    const folderSegments = segments.slice(0, -1);
    for (const folderName of folderSegments) {
      cursor = ensureDistributionFolderChild(cursor, folderName);
    }

    const fileName = segments[segments.length - 1];
    if (!/\.py$/i.test(fileName)) {
      skippedCount += 1;
      continue;
    }

    const duplicate = (Array.isArray(cursor.children) ? cursor.children : []).find(function(child) {
      return child.type === 'file' && child.name === fileName;
    });
    if (duplicate) {
      skippedCount += 1;
      continue;
    }

    const content = await file.text();
    const newFile = createDistributionFileNode(fileName);
    newFile.content = content;
    cursor.children.push(newFile);
    if (!firstAddedFileId) {
      firstAddedFileId = newFile.id;
    }
    addedCount += 1;
  }

  if (!addedCount) {
    toast('.py ファイルを追加できませんでした。', 'warning');
    return;
  }

  distributionTreeState.currentFileId = firstAddedFileId || distributionTreeState.currentFileId;
  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();
  syncPreview();
  const suffix = skippedCount > 0 ? '（' + String(skippedCount) + '件は未追加）' : '';
  toast(String(addedCount) + '件の .py ファイルを取り込みました。' + suffix, 'success');
}

function downloadCurrentDistributionFile() {
  applyDistributionFileEditorSilently();
  const fileNode = getCurrentDistributionFileNode();
  if (!fileNode) {
    toast('ダウンロードするファイルを選択してください。', 'warning');
    return;
  }

  const blob = new Blob([fileNode.content || ''], { type: 'text/x-python;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileNode.name || 'template.py';
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
  toast('ファイルをダウンロードしました。', 'success');
}

function appendDistributionNodeToZip(node, zipFolder, counter) {
  if (!node) {
    return;
  }

  if (node.type === 'file') {
    zipFolder.file(node.name, node.content || '');
    counter.count += 1;
    return;
  }

  if (node.type !== 'folder' || !Array.isArray(node.children)) {
    return;
  }

  const nextZipFolder = node.id === DISTRIBUTION_TREE_ROOT_ID
    ? zipFolder
    : zipFolder.folder(node.name);

  node.children.forEach(function(child) {
    appendDistributionNodeToZip(child, nextZipFolder, counter);
  });
}

async function downloadAllDistributionFiles() {
  applyDistributionFileEditorSilently();

  if (typeof JSZip === 'undefined') {
    toast('一括ダウンロードの準備に失敗しました。', 'warning');
    return;
  }

  const zip = new JSZip();
  const rootName = normalizeDistributionRootName(getValue('templateRoot'));
  const rootFolder = rootName ? zip.folder(rootName) : zip;
  const counter = { count: 0 };
  appendDistributionNodeToZip(distributionTreeState.root, rootFolder, counter);

  if (counter.count === 0) {
    toast('ダウンロード対象のファイルがありません。', 'warning');
    return;
  }

  const blob = await zip.generateAsync({ type: 'blob' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = (rootName || 'distribution-template') + '.zip';
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
  toast('テンプレートを一括ダウンロードしました。', 'success');
}

function runDistributionCurrentCode() {
  applyDistributionFileEditorSilently();
  const fileNode = getCurrentDistributionFileNode();
  if (!fileNode) {
    toast('実行するファイルを選択してください。', 'warning');
    return;
  }

  const source = String(fileNode.content || '');
  let stdout = '';
  let stderr = 'エラーはありません。';

  if (/syntaxerror/i.test(source) || /raise\s+SyntaxError/.test(source)) {
    stderr = 'Traceback (most recent call last):\n  File "' + fileNode.name + '", line 1\nSyntaxError: invalid syntax';
  } else if (/input\(/.test(source)) {
    stdout = fileNode.name + ' を実行しました。\ninput を含むため、サンプル結果を表示しています。';
  } else if (/print\(/.test(source)) {
    stdout = fileNode.name + ' を実行しました。\nprint 文のサンプル結果を表示しています。';
  } else {
    stdout = fileNode.name + ' を実行しました。\n標準出力はありません。';
  }

  setText('distributionRunStdout', stdout || '標準出力はありません。');
  setText('distributionRunStderr', stderr);
  updateDistributionEditorMessage('実行が完了しました。結果を確認できます。');

  if (distributionRunResultModal) {
    distributionRunResultModal.show();
  }
}

function flattenDistributionFilesFromTree() {
  const lines = [];

  function walk(node, prefix) {
    if (!node) {
      return;
    }

    if (node.type === 'file') {
      lines.push(prefix ? prefix + '/' + node.name : node.name);
      return;
    }

    if (node.type !== 'folder' || !Array.isArray(node.children)) {
      return;
    }

    node.children.forEach(function(child) {
      if (node.id === DISTRIBUTION_TREE_ROOT_ID) {
        walk(child, '');
        return;
      }

      const nextPrefix = prefix ? prefix + '/' + node.name : node.name;
      walk(child, nextPrefix);
    });
  }

  walk(distributionTreeState.root, '');
  return lines;
}

function syncTemplateFilesFromTree() {
  const hiddenField = document.getElementById('templateFiles');
  if (!hiddenField) {
    return;
  }

  hiddenField.value = flattenDistributionFilesFromTree().join('\n');
}

function cloneDistributionNode(node, forceRootId) {
  const cloned = {
    id: forceRootId || generateDistributionTreeId(node.type === 'folder' ? 'dir' : 'file'),
    type: node.type,
    name: node.name
  };

  if (node.type === 'file') {
    cloned.content = node.content || buildDefaultDistributionFileContent(node.name);
    return cloned;
  }

  cloned.children = (Array.isArray(node.children) ? node.children : []).map(function(child) {
    return cloneDistributionNode(child);
  });
  return cloned;
}

function cloneDistributionStructureRoot() {
  return cloneDistributionNode(distributionTreeState.root, DISTRIBUTION_TREE_ROOT_ID);
}

function buildDistributionTreeFromTemplate(template) {
  if (template && template.structure && template.structure.type === 'folder') {
    return cloneDistributionNode(template.structure, DISTRIBUTION_TREE_ROOT_ID);
  }
  return buildDistributionTreeFromFileLines(template && template.files ? template.files : []);
}

function applyDistributionFileEditorSilently() {
  const editor = document.getElementById('distributionFileEditor');
  const fileNode = getCurrentDistributionFileNode();
  if (!editor || !fileNode) {
    return;
  }

  fileNode.content = editor.value;
  distributionEditorState.dirty = false;
}

function buildDistributionTreeFromFileLines(files) {
  const root = createDistributionFolderNode('(root)', DISTRIBUTION_TREE_ROOT_ID);
  (Array.isArray(files) ? files : []).forEach(function(path) {
    const normalizedPath = String(path || '').trim();
    if (!normalizedPath) {
      return;
    }

    const segments = normalizedPath.split('/').filter(function(item) { return item; });
    if (!segments.length) {
      return;
    }

    let cursor = root;
    for (let i = 0; i < segments.length; i += 1) {
      const segment = segments[i];
      const isLast = i === segments.length - 1;
      if (isLast) {
        if (!cursor.children.some(function(child) { return child.type === 'file' && child.name === segment; })) {
          cursor.children.push(createDistributionFileNode(segment));
        }
        return;
      }

      let folder = cursor.children.find(function(child) {
        return child.type === 'folder' && child.name === segment;
      });
      if (!folder) {
        folder = createDistributionFolderNode(segment);
        cursor.children.push(folder);
      }
      cursor = folder;
    }
  });

  return root;
}

function upsertTemplate(actionType) {
  applyDistributionFileEditorSilently();
  const payload = collectFormValues();

  if (!payload.name) {
    toast('テンプレート名を入力してください。', 'danger');
    return;
  }

  if (actionType !== '下書き' && payload.classes.length === 0) {
    toast('配信対象のクラスを選択してください。', 'danger');
    return;
  }

  if (actionType !== '下書き' && hasEmptyClassSchedule(payload.classSchedules)) {
    toast('クラスごとの配信日時を設定してください。', 'danger');
    return;
  }

  let targetTemplate = null;
  if (distributionState.editTemplateId) {
    targetTemplate = distributionState.templates.find(function(item) {
      return item.id === distributionState.editTemplateId;
    });
  }

  if (!targetTemplate) {
    targetTemplate = {
      id: generateTemplateId()
    };
    distributionState.templates.unshift(targetTemplate);
  }

  const latestHistory = findLatestHistoryByTemplateId(targetTemplate.id);
  if (actionType !== '下書き' && latestHistory && !isHistoryEditable(latestHistory)) {
    toast('配信済みの履歴は修正できません。新規テンプレートで配信してください。', 'warning');
    return;
  }

  targetTemplate.name = payload.name;
  targetTemplate.root = normalizeDistributionRootName(payload.root);
  targetTemplate.files = payload.files;
  targetTemplate.structure = cloneDistributionStructureRoot();
  targetTemplate.schools = payload.schools;
  targetTemplate.classes = payload.classes;
  targetTemplate.classSchedules = payload.classSchedules;
  targetTemplate.updatedAt = formatDateTime(new Date());

  if (actionType === '下書き') {
    targetTemplate.status = '下書き';
    toast('テンプレートを下書き保存しました。', 'success');
  } else {
    targetTemplate.status = '公開';
    const historyStatus = resolveHistoryStatus(payload.classSchedules);
    upsertHistory(payload, historyStatus, targetTemplate.id);
    if (historyStatus === DISTRIBUTION_STATUS_DISTRIBUTED) {
      toast('配信しました。配信済みは取り消しできません。', 'success');
    } else {
      toast('配信予約を保存しました。配信前は編集できます。', 'success');
    }
  }

  distributionState.editTemplateId = null;
  renderHistoryTable();
  updateStats();
  resetForm();
}

function collectFormValues() {
  const rawRoot = getValue('templateRoot');
  const normalizedRoot = normalizeDistributionRootName(rawRoot);
  return {
    schools: getCheckedValues('distributionSchoolTargets'),
    classes: getCheckedValues('distributionClassTargets'),
    classSchedules: collectClassSchedules(),
    name: getValue('templateName'),
    root: normalizedRoot,
    files: parseFiles(getValue('templateFiles'))
  };
}

function renderHistoryTable() {
  const body = document.getElementById('distributionHistoryBody');
  if (!body) {
    return;
  }

  body.innerHTML = distributionState.history.map(function(item, index) {
    const editable = isHistoryEditable(item);
    const buttonClass = editable ? 'btn-outline-primary' : 'btn-outline-secondary';
    const buttonDisabled = editable ? '' : ' disabled';
    const buttonLabel = '編集';
    return '<tr>'
      + '<td>' + escapeHtml(item.distributedAt) + '</td>'
      + '<td>' + escapeHtml(item.target) + '</td>'
      + '<td>' + escapeHtml(item.template) + '</td>'
      + '<td>' + escapeHtml(item.operator) + '</td>'
      + '<td>' + escapeHtml(item.status) + '</td>'
      + '<td>'
      +   '<div class="row-actions">'
      +     '<button type="button" class="btn btn-sm ' + buttonClass + ' history-edit" data-history-index="' + String(index) + '"' + buttonDisabled + '>' + buttonLabel + '</button>'
      +     '<button type="button" class="btn btn-sm btn-outline-secondary history-duplicate" data-history-index="' + String(index) + '">複製</button>'
      +   '</div>'
      + '</td>'
      + '</tr>';
  }).join('');

  body.querySelectorAll('.history-edit').forEach(function(button) {
    button.addEventListener('click', function() {
      const historyIndex = Number(button.getAttribute('data-history-index'));
      startEditFromHistory(historyIndex);
    });
  });

  body.querySelectorAll('.history-duplicate').forEach(function(button) {
    button.addEventListener('click', function() {
      const historyIndex = Number(button.getAttribute('data-history-index'));
      startDuplicateFromHistory(historyIndex);
    });
  });
}

function upsertHistory(payload, status, templateId) {
  const existingIndex = distributionState.history.findIndex(function(item) {
    return item.templateId === templateId && isHistoryEditable(item);
  });

  const targetText = joinTargets(payload.schools, payload.classes);
  const newItem = {
    distributedAt: formatDateTime(new Date()),
    target: targetText,
    templateId: templateId || '',
    template: payload.name,
    operator: 't001',
    status: status
  };

  if (existingIndex >= 0) {
    distributionState.history[existingIndex] = newItem;
  } else {
    distributionState.history.unshift(newItem);
  }
}

function startEditTemplate(id) {
  const template = distributionState.templates.find(function(item) {
    return item.id === id;
  });

  if (!template) {
    return;
  }

  distributionState.editTemplateId = id;

  setCheckedValues('distributionSchoolTargets', template.schools);
  setCheckedValues('distributionClassTargets', template.classes);
  refreshClassScheduleRows(template.classSchedules);
  setValue('templateName', template.name || '');
  setValue('templateRoot', template.root || '');
  distributionTreeState.root = buildDistributionTreeFromTemplate(template);
  distributionTreeState.selectedFolderId = DISTRIBUTION_TREE_ROOT_ID;
  distributionTreeState.currentFileId = '';
  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();

  updateSchoolDropdownLabel();
  updateClassDropdownLabel();
  syncPreview();
  toast('テンプレートを編集モードで読み込みました。', 'success');
}

function startEditFromHistory(historyIndex) {
  if (!Number.isFinite(historyIndex) || historyIndex < 0 || historyIndex >= distributionState.history.length) {
    toast('履歴データの参照に失敗しました。', 'warning');
    return;
  }

  const historyItem = distributionState.history[historyIndex];
  if (!isHistoryEditable(historyItem)) {
    toast('配信済みの履歴は編集できません。', 'warning');
    return;
  }

  const templateId = historyItem.templateId || '';
  const templateName = historyItem.template || '';
  let targetTemplate = null;

  if (templateId) {
    targetTemplate = distributionState.templates.find(function(item) {
      return item.id === templateId;
    }) || null;
  }

  if (!targetTemplate && templateName) {
    targetTemplate = distributionState.templates.find(function(item) {
      return item.name === templateName;
    }) || null;
  }

  if (!targetTemplate) {
    toast('編集対象のテンプレートが見つかりません。', 'warning');
    return;
  }

  startEditTemplate(targetTemplate.id);
}

function startDuplicateFromHistory(historyIndex) {
  if (!Number.isFinite(historyIndex) || historyIndex < 0 || historyIndex >= distributionState.history.length) {
    toast('履歴データの参照に失敗しました。', 'warning');
    return;
  }

  const historyItem = distributionState.history[historyIndex];
  const templateId = historyItem.templateId || '';
  const templateName = historyItem.template || '';

  let sourceTemplate = null;
  if (templateId) {
    sourceTemplate = distributionState.templates.find(function(item) {
      return item.id === templateId;
    }) || null;
  }

  if (!sourceTemplate && templateName) {
    sourceTemplate = distributionState.templates.find(function(item) {
      return item.name === templateName;
    }) || null;
  }

  if (!sourceTemplate) {
    toast('複製元のテンプレートが見つかりません。', 'warning');
    return;
  }

  distributionState.editTemplateId = null;
  setCheckedValues('distributionSchoolTargets', sourceTemplate.schools);
  setCheckedValues('distributionClassTargets', sourceTemplate.classes);
  refreshClassScheduleRows(sourceTemplate.classSchedules);
  setValue('templateName', (sourceTemplate.name || 'テンプレート') + '（複製）');
  setValue('templateRoot', sourceTemplate.root || '');
  distributionTreeState.root = buildDistributionTreeFromTemplate(sourceTemplate);
  distributionTreeState.selectedFolderId = DISTRIBUTION_TREE_ROOT_ID;
  distributionTreeState.currentFileId = '';
  syncTemplateFilesFromTree();
  ensureCurrentDistributionFile();
  renderDistributionStructureTree();
  updateDistributionFileEditor();

  updateSchoolDropdownLabel();
  updateClassDropdownLabel();
  syncPreview();
  toast('履歴からテンプレートを複製しました。', 'success');
}

function updateSchoolDropdownLabel() {
  const button = document.getElementById('distributionSchoolDropdownButton');
  if (!button) {
    return;
  }

  const selected = getCheckedValues('distributionSchoolTargets');
  if (selected.length === 0) {
    button.textContent = '学校を選択';
  } else if (selected.length <= 2) {
    button.textContent = selected.join(', ');
  } else {
    button.textContent = selected.length + '件選択中';
  }
}

function updateClassDropdownLabel() {
  const button = document.getElementById('distributionClassDropdownButton');
  if (!button) {
    return;
  }

  const selected = getCheckedValues('distributionClassTargets');
  if (selected.length === 0) {
    button.textContent = 'クラスを選択';
  } else if (selected.length <= 2) {
    button.textContent = selected.join(', ');
  } else {
    button.textContent = selected.length + '件選択中';
  }
}

function refreshClassScheduleRows(prefillSchedules) {
  const container = document.getElementById('distributionClassScheduleList');
  if (!container) {
    return;
  }

  const selectedClasses = getCheckedValues('distributionClassTargets');
  const currentMap = toClassScheduleMap(collectClassSchedules());
  const prefillMap = toClassScheduleMap(prefillSchedules);

  container.innerHTML = '';

  if (selectedClasses.length === 0) {
    container.innerHTML = '<p class="class-schedule-empty mb-0 text-muted">クラスを選択すると、クラスごとに配信日時（即時配信可）を設定できます。</p>';
    return;
  }

  selectedClasses.forEach(function(className) {
    const seed = prefillMap[className] || currentMap[className] || { immediate: false, scheduleAt: '' };
    const safeClass = escapeHtml(className);
    const row = document.createElement('div');
    row.className = 'class-schedule-row distribution-class-schedule-row';
    row.setAttribute('data-class-name', className);
    row.innerHTML =
      '<div class="class-schedule-name">' + safeClass + '</div>'
      + '<div class="class-schedule-field">'
      +   '<label class="mini-label" for="distributionSchedule-' + safeClass + '">配信日時</label>'
      +   '<input id="distributionSchedule-' + safeClass + '" class="form-control distribution-class-schedule" type="datetime-local" value="' + escapeHtml(seed.scheduleAt || '') + '">'
      +   '<div class="form-check mt-2">'
      +     '<input class="form-check-input distribution-class-immediate" type="checkbox" id="distributionImmediate-' + safeClass + '"' + (seed.immediate ? ' checked' : '') + '>'
      +     '<label class="form-check-label" for="distributionImmediate-' + safeClass + '">即時配信</label>'
      +   '</div>'
      + '</div>';
    container.appendChild(row);

    const immediateInput = row.querySelector('.distribution-class-immediate');
    const scheduleInput = row.querySelector('.distribution-class-schedule');
    if (immediateInput && scheduleInput) {
      const applyScheduleMode = function() {
        const immediate = immediateInput.checked;
        scheduleInput.disabled = immediate;
        if (immediate) {
          scheduleInput.value = '';
        }
        syncPreview();
      };
      immediateInput.addEventListener('change', applyScheduleMode);
      scheduleInput.addEventListener('input', syncPreview);
      scheduleInput.addEventListener('change', syncPreview);
      applyScheduleMode();
    }
  });
}

function collectClassSchedules() {
  return Array.from(document.querySelectorAll('#distributionClassScheduleList .distribution-class-schedule-row')).map(function(row) {
    const className = row.getAttribute('data-class-name') || '';
    const immediateInput = row.querySelector('.distribution-class-immediate');
    const scheduleInput = row.querySelector('.distribution-class-schedule');
    return {
      className: className,
      immediate: !!(immediateInput && immediateInput.checked),
      scheduleAt: scheduleInput && typeof scheduleInput.value === 'string' ? scheduleInput.value.trim() : ''
    };
  });
}

function toClassScheduleMap(schedules) {
  return (Array.isArray(schedules) ? schedules : []).reduce(function(map, item) {
    if (!item || !item.className) {
      return map;
    }

    map[item.className] = {
      immediate: !!item.immediate,
      scheduleAt: item.scheduleAt || ''
    };
    return map;
  }, {});
}

function hasEmptyClassSchedule(classSchedules) {
  if (!Array.isArray(classSchedules) || classSchedules.length === 0) {
    return true;
  }

  return classSchedules.some(function(item) {
    if (item.immediate) {
      return false;
    }
    return !item.scheduleAt;
  });
}

function resetForm() {
  distributionState.editTemplateId = null;
  setValue('templateName', '');
  setValue('templateRoot', '');
  distributionTreeState.root = createDistributionFolderNode('(root)', DISTRIBUTION_TREE_ROOT_ID);
  distributionTreeState.selectedFolderId = DISTRIBUTION_TREE_ROOT_ID;
  distributionTreeState.currentFileId = '';
  syncTemplateFilesFromTree();
  renderDistributionStructureTree();
  updateDistributionFileEditor();
  setCheckedValues('distributionSchoolTargets', []);
  setCheckedValues('distributionClassTargets', []);
  updateSchoolDropdownLabel();
  updateClassDropdownLabel();
  refreshClassScheduleRows();
  syncPreview();
}

function syncPreview() {
  const payload = collectFormValues();
  setText('previewTemplateName', payload.name || '未設定');
  setText('previewTargets', joinTargets(payload.schools, payload.classes));
  setText('previewSchedule', formatClassSchedules(payload.classSchedules));
  renderPreviewFiles(payload.files);
}

function formatClassSchedules(classSchedules) {
  if (!Array.isArray(classSchedules) || classSchedules.length === 0) {
    return 'クラス別日時未設定';
  }

  const ready = classSchedules.filter(function(item) {
    return item.immediate || item.scheduleAt;
  }).map(function(item) {
    if (item.immediate) {
      return item.className + ': 即時配信';
    }
    return item.className + ': ' + item.scheduleAt.replace('T', ' ');
  });

  if (ready.length === 0) {
    return 'クラス別日時未設定';
  }

  if (ready.length <= 2) {
    return ready.join(' / ');
  }

  return ready.slice(0, 2).join(' / ') + ' ...';
}

function renderPreviewFiles(files) {
  const list = document.getElementById('previewFileList');
  if (!list) {
    return;
  }

  if (!files.length) {
    list.innerHTML = '<li class="text-muted">ファイル未設定</li>';
    return;
  }

  list.innerHTML = files.map(function(file) {
    return '<li>' + escapeHtml(file) + '</li>';
  }).join('');
}

function updateStats() {
  setText('templateCount', String(distributionState.templates.length));

  const scheduled = distributionState.history.filter(function(item) {
    return item.status === DISTRIBUTION_STATUS_SCHEDULED;
  }).length;
  setText('scheduledCount', String(scheduled));

  setText('historyCount', String(distributionState.history.length));
}

function generateTemplateId() {
  const nextNumber = distributionState.templates.length + 1;
  return 'TPL-' + String(nextNumber).padStart(3, '0');
}

function renderStatusBadge(status) {
  const isActive = status === '公開';
  const className = isActive ? 'is-active' : 'is-draft';
  return '<span class="badge-status ' + className + '">' + escapeHtml(status) + '</span>';
}

function joinTargets(schools, classes) {
  const schoolText = schools.length ? schools.join(', ') : '学校未選択';
  const classText = classes.length ? classes.join(', ') : 'クラス未選択';
  return schoolText + ' / ' + classText;
}

function parseFiles(rawValue) {
  if (!rawValue) {
    return [];
  }

  return rawValue.split('\n').map(function(line) {
    return line.trim();
  }).filter(function(line) {
    return line.length > 0;
  });
}

function getCheckedValues(name) {
  return Array.from(document.querySelectorAll('input[name="' + name + '"]:checked')).map(function(element) {
    return element.value;
  });
}

function setCheckedValues(name, values) {
  const selected = new Set(Array.isArray(values) ? values : []);
  document.querySelectorAll('input[name="' + name + '"]').forEach(function(element) {
    element.checked = selected.has(element.value);
  });
}

function getValue(id) {
  const element = document.getElementById(id);
  if (!element || typeof element.value !== 'string') {
    return '';
  }
  return element.value.trim();
}

function normalizeDistributionRootName(value) {
  const normalized = String(value || '').trim();
  if (!normalized || normalized === DISTRIBUTION_ROOT_NONE_TOKEN) {
    return '';
  }
  return normalized;
}

function getDistributionRootDisplayName() {
  const normalized = normalizeDistributionRootName(getValue('templateRoot'));
  return normalized || DISTRIBUTION_ROOT_LABEL;
}

function setValue(id, value) {
  const element = document.getElementById(id);
  if (!element || typeof element.value !== 'string') {
    return;
  }
  element.value = value;
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value;
  }
}

function formatDateTime(date) {
  const yyyy = String(date.getFullYear());
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mi = String(date.getMinutes()).padStart(2, '0');
  return yyyy + '-' + mm + '-' + dd + ' ' + hh + ':' + mi;
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function toast(message, variant) {
  if (pageFeedback && typeof pageFeedback.toast === 'function') {
    pageFeedback.toast({ message: message, variant: variant || 'success', delay: 2200 });
    return;
  }

  if (typeof feedback.toast === 'function') {
    feedback.toast({ title: 'コード配信', message: message, variant: variant || 'success' });
  }
}

function resolveHistoryStatus(classSchedules) {
  const hasImmediate = (Array.isArray(classSchedules) ? classSchedules : []).some(function(item) {
    return !!item.immediate;
  });
  return hasImmediate ? DISTRIBUTION_STATUS_DISTRIBUTED : DISTRIBUTION_STATUS_SCHEDULED;
}

function isHistoryEditable(historyItem) {
  const status = historyItem && historyItem.status ? historyItem.status : '';
  return status !== DISTRIBUTION_STATUS_DISTRIBUTED && status !== '完了';
}

function findLatestHistoryByTemplateId(templateId) {
  if (!templateId) {
    return null;
  }

  return distributionState.history.find(function(item) {
    return item.templateId === templateId;
  }) || null;
}
