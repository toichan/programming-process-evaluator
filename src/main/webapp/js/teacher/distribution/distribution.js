(function() {
  'use strict';

  const form = document.getElementById('distributionForm');
  if (!form) return;

  const feedback = window.PPEFeedback.createPageFeedback({
    title: 'コード配信',
    alertTarget: '#distributionFeedback'
  });
  const createFeedback = window.PPEFeedback.createPageFeedback({
    title: '項目作成',
    alertTarget: '#createEntryFeedback'
  });
  const uploadFeedback = window.PPEFeedback.createPageFeedback({
    title: 'アップロード',
    alertTarget: '#uploadFeedback'
  });
  const editorTextarea = document.getElementById('codeEditor');
  const itemTree = document.getElementById('exerciseTree');
  const classSelect = document.getElementById('distributionSchoolSelect');
  const classOptions = Array.from(document.querySelectorAll('.distribution-class-option'));
  const classDropdownButton = document.getElementById('distributionClassDropdownButton');
  const scheduleHost = document.getElementById('distributionClassScheduleList');
  const itemsJson = document.getElementById('distributionItemsJson');
  const targetsJson = document.getElementById('distributionTargetsJson');
  const actionInput = document.getElementById('distributionAction');
  const templateIdInput = document.getElementById('distributionTemplateId');
  const versionInput = document.getElementById('distributionExpectedVersion');
  const requestTokenInput = document.getElementById('distributionRequestToken');
  const items = new Map();
  const scheduleValues = new Map();
  const immediateValues = new Map();
  let uploadFilesSelected = [];
  let uploadDestination = '';
  let uploadPreview = null;
  let selectedFolder = '';
  let currentFile = '';
  let pendingEntryType = 'file';
  let pendingEntryAction = 'create';
  let batchAction = '';
  let batchPaths = [];
  let selectionMode = false;
  let editor = null;
  let suppressEditorChange = false;
  const selectedPaths = new Set();
  const expandedFolders = new Set();
  const controlIcons = {
    folder: '<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h5l2 2h8A1.5 1.5 0 0 1 21 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5z"/>',
    file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v5h5M9 13h6M9 17h6"/>',
    upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6"/>',
    expand: '<path d="m7 10 5 5 5-5M7 4l5 5 5-5"/>',
    collapse: '<path d="m7 14 5-5 5 5M7 20l5-5 5 5"/>',
    reveal: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
    download: '<path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4"/>',
    save: '<path d="M4 4h14l2 2v14H4z"/><path d="M8 4v6h8V4M8 20v-6h8v6"/>',
    run: '<path d="m8 5 11 7-11 7z"/>',
    move: '<path d="M12 3v18m-9-9h18M12 3l-3 3m3-3 3 3m-3 15-3-3m3 3 3-3M3 12l3-3m-3 3 3 3m15-3-3-3m3 3-3 3"/>',
    trash: '<path d="M4 7h16M10 11v6m4-6v6M6 7l1 14h10l1-14M9 7V4h6v3"/>',
    clear: '<path d="m6 6 12 12M18 6 6 18"/>'
  };

  function createExplorerEntryIcon(type) {
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    icon.setAttribute('class', type === 'folder' ? 'lesson-tree-folder-icon' : 'lesson-tree-file-icon');
    icon.setAttribute('width', '18');
    icon.setAttribute('height', '18');
    icon.setAttribute('viewBox', '0 0 16 16');
    icon.setAttribute('fill', 'currentColor');
    icon.setAttribute('aria-hidden', 'true');
    const shape = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    shape.setAttribute('d', type === 'folder'
      ? 'M.75 3A1.75 1.75 0 0 1 2.5 1.25h3.379c.464 0 .908.184 1.237.513l.72.72c.141.14.331.22.53.22H13.5A1.75 1.75 0 0 1 15.25 4.5v7A1.75 1.75 0 0 1 13.5 13.25h-11A1.75 1.75 0 0 1 .75 11.5V3z'
      : 'M3.75 1.5A1.75 1.75 0 0 0 2 3.25v9.5c0 .966.784 1.75 1.75 1.75h8.5A1.75 1.75 0 0 0 14 12.75V5.56a1.75 1.75 0 0 0-.513-1.237L10.677 1.513A1.75 1.75 0 0 0 9.44 1H3.75zm5.5 1.06c.133.035.255.103.354.202l2.634 2.634a.75.75 0 0 1 .202.354H9.75a.5.5 0 0 1-.5-.5V2.56z');
    icon.appendChild(shape);
    return icon;
  }
  let sortKey = 'datetime';
  let sortDirection = 'desc';

  function newRequestToken() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
    if (!window.crypto || typeof window.crypto.getRandomValues !== 'function') {
      throw new Error('安全な送信情報を生成できません。ブラウザーを更新して再度お試しください。');
    }
    const bytes = window.crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
    return hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16)
      + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
  }

  function selectedClassInputs() {
    return classOptions.flatMap(option => {
      const checkbox = option.querySelector('input[type="checkbox"]');
      return checkbox && checkbox.checked ? [checkbox] : [];
    });
  }

  function updateClassOptions() {
    const schoolId = classSelect.value;
    let visibleCount = 0;
    classOptions.forEach(option => {
      const visible = !!schoolId && option.dataset.schoolId === schoolId;
      option.hidden = !visible;
      if (visible) visibleCount += 1;
      if (!visible) {
        const checkbox = option.querySelector('input[type="checkbox"]');
        if (checkbox) checkbox.checked = false;
      }
    });
    document.getElementById('distributionNoClasses').hidden = !schoolId || visibleCount > 0;
    classDropdownButton.disabled = !schoolId || visibleCount === 0;
    if (!schoolId) classDropdownButton.textContent = '学校を選択';
    else if (visibleCount === 0) classDropdownButton.textContent = '選択できるクラスがありません';
    updateClassDropdownLabel();
    renderSchedules();
  }

  function updateClassDropdownLabel() {
    const count = selectedClassInputs().length;
    classDropdownButton.textContent = count > 0 ? count + 'クラスを選択中' : 'クラスを選択';
  }

  function renderSchedules() {
    const selected = selectedClassInputs();
    scheduleHost.replaceChildren();
    selected.forEach(checkbox => {
      const option = checkbox.closest('.distribution-class-option');
      const row = document.createElement('div');
      row.className = 'class-schedule-row';
      const name = document.createElement('span');
      name.className = 'class-schedule-name';
      name.textContent = option.querySelector('label').textContent.trim();
      const immediateLabel = document.createElement('label');
      immediateLabel.className = 'form-check form-check-inline class-schedule-immediate';
      const immediate = document.createElement('input');
      immediate.className = 'form-check-input';
      immediate.type = 'checkbox';
      immediate.id = 'immediate-' + checkbox.dataset.classroomId;
      immediate.checked = immediateValues.get(checkbox.dataset.classroomId) !== false;
      immediate.addEventListener('change', () => {
        immediateValues.set(checkbox.dataset.classroomId, immediate.checked);
        input.disabled = immediate.checked;
      });
      const immediateText = document.createElement('span');
      immediateText.className = 'form-check-label';
      immediateText.textContent = '即時配信';
      immediateLabel.append(immediate, immediateText);
      const label = document.createElement('label');
      label.className = 'visually-hidden';
      label.htmlFor = 'schedule-' + checkbox.dataset.classroomId;
      label.textContent = name.textContent + 'の配信日時';
      const input = document.createElement('input');
      input.className = 'form-control form-control-sm class-schedule-input';
      input.type = 'datetime-local';
      input.id = label.htmlFor;
      input.dataset.classroomId = checkbox.dataset.classroomId;
      input.value = scheduleValues.get(checkbox.dataset.classroomId) || '';
      input.disabled = immediate.checked;
      input.addEventListener('input', () => scheduleValues.set(input.dataset.classroomId, input.value));
      row.append(name, immediateLabel, label, input);
      scheduleHost.appendChild(row);
    });
  }

  function readItemsJson() {
    try {
      const parsed = JSON.parse(itemsJson.value || '[]');
      if (!Array.isArray(parsed)) throw new Error('array required');
      parsed.forEach(item => {
        if (item && typeof item.path === 'string' && (item.type === 'folder' || item.type === 'file')) {
          items.set(item.path, {
            path: item.path,
            type: item.type,
            content: item.type === 'file' ? (item.content || '') : null,
            updatedAt: Number(item.updatedAt) || 0
          });
          expandParents(item.path);
        }
      });
    } catch (error) {
      feedback.inlineAlert('テンプレート項目を復元できませんでした。内容を再入力してください。', 'danger');
    }
  }

  function restoreTargets() {
    try {
      const targets = JSON.parse(targetsJson.value || '[]');
      if (!Array.isArray(targets)) throw new Error('array required');
      const byId = new Map(classOptions.map(option => {
        const checkbox = option.querySelector('input[type="checkbox"]');
        return [checkbox.dataset.classroomId, { checkbox, option }];
      }));
      targets.forEach(target => {
        const entry = byId.get(String(target.classroomId));
        if (!entry) return;
        classSelect.value = entry.option.dataset.schoolId;
        entry.checkbox.checked = true;
        if (target.scheduledAt) {
          scheduleValues.set(String(target.classroomId), target.scheduledAt);
          immediateValues.set(String(target.classroomId), false);
        } else {
          immediateValues.set(String(target.classroomId), target.immediate !== false);
        }
      });
    } catch (error) {
      feedback.inlineAlert('配信先を復元できませんでした。クラスを選び直してください。', 'danger');
    }
  }

  function pathSegments(path) {
    return path.split('/').filter(Boolean);
  }

  function expandParents(path) {
    const segments = pathSegments(path);
    segments.slice(0, -1).forEach((_, index) => expandedFolders.add(segments.slice(0, index + 1).join('/')));
  }

  function createTree() {
    const root = { name: document.getElementById('distributionRootName').value || 'root', path: '', type: 'folder', children: new Map() };
    Array.from(items.values()).forEach(item => {
      const segments = pathSegments(item.path);
      let node = root;
      let path = '';
      segments.forEach((segment, index) => {
        path = path ? path + '/' + segment : segment;
        let child = node.children.get(segment);
        if (!child) {
          child = { name: segment, path, type: index === segments.length - 1 ? item.type : 'folder', children: new Map() };
          node.children.set(segment, child);
        }
        if (index === segments.length - 1) child.type = item.type;
        node = child;
      });
    });
    return root;
  }

  function controlIcon(name) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', 'lesson-control-icon');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.8');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');
    svg.innerHTML = controlIcons[name] || '';
    return svg;
  }

  document.querySelectorAll('[data-control-icon]').forEach(button => {
    const icon = controlIcon(button.dataset.controlIcon);
    if (button.dataset.controlIcon === 'upload') {
      button.replaceChildren(icon);
      return;
    }
    const label = document.createElement('span');
    label.className = 'lesson-control-label';
    label.textContent = button.textContent.trim();
    button.replaceChildren(icon, label);
  });

  function addEntryMenu(row, path) {
    const menu = document.createElement('div');
    menu.className = 'dropdown lesson-tree-entry-menu';
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'btn btn-sm btn-outline-secondary lesson-tree-menu-toggle';
    toggle.dataset.bsToggle = 'dropdown';
    toggle.dataset.entryMenu = 'true';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-label', '項目の操作');
    toggle.textContent = '⋮';
    const list = document.createElement('div');
    list.className = 'dropdown-menu dropdown-menu-end lesson-tree-menu-dropdown';
    [['rename', '名前を変更'], ['duplicate', '複製'], ['move', '移動'], ['download', 'ダウンロード'], ['delete', '完全削除']]
      .forEach(([action, label]) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'dropdown-item lesson-tree-menu-item' + (action === 'delete' ? ' text-danger' : '');
        button.dataset.entryAction = action;
        button.dataset.path = path;
        button.textContent = label;
        button.addEventListener('click', () => {
          if (previewRunning) return;
          try {
            if (action === 'rename') openRenameDialog(path);
            else if (action === 'duplicate') openBatchDialog('duplicate', [path]);
            else if (action === 'move') openBatchDialog('move', [path]);
            else if (action === 'download') downloadEntry(path);
            else if (action === 'delete') openBatchDialog('delete', [path]);
          } catch (error) {
            feedback.inlineAlert(error.message, 'warning');
          }
        });
        list.appendChild(button);
      });
    menu.append(toggle, list);
    row.appendChild(menu);
  }

  function treeNodeMatches(node, query) {
    if (!query) return true;
    if (node.name.toLocaleLowerCase().includes(query)) return true;
    return Array.from(node.children.values()).some(child => treeNodeMatches(child, query));
  }

  function sortedChildren(node) {
    const sort = document.getElementById('exerciseSort').value;
    return Array.from(node.children.values()).sort((left, right) => {
      if (left.type !== right.type) return left.type === 'folder' ? -1 : 1;
      if (sort === 'updated') {
        return (items.get(right.path)?.updatedAt || 0) - (items.get(left.path)?.updatedAt || 0)
          || left.name.localeCompare(right.name, 'ja');
      }
      return left.name.localeCompare(right.name, 'ja');
    });
  }

  function addSelectionCheckbox(parent, path) {
    if (!selectionMode) return;
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'lesson-entry-check';
    checkbox.checked = selectedPaths.has(path);
    checkbox.setAttribute('aria-label', path + 'を選択');
    checkbox.addEventListener('click', event => event.stopPropagation());
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) selectedPaths.add(path);
      else selectedPaths.delete(path);
      updateSelectionControls();
    });
    parent.appendChild(checkbox);
  }

  function renderTreeNode(node, parent, query) {
    sortedChildren(node).filter(child => treeNodeMatches(child, query)).forEach(child => {
      if (child.type === 'folder') {
        const folder = document.createElement('div');
        folder.className = 'lesson-tree-folder';
        const header = document.createElement('div');
        header.className = 'lesson-tree-folder-header' + (selectedFolder === child.path ? ' is-selected-folder' : '');
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'lesson-tree-folder-toggle' + (expandedFolders.has(child.path) ? '' : ' is-collapsed');
        toggle.setAttribute('aria-label', (expandedFolders.has(child.path) ? '折りたたむ' : '展開') + ' ' + child.name);
        toggle.appendChild(controlIcon('expand'));
        toggle.addEventListener('click', () => {
          if (expandedFolders.has(child.path)) expandedFolders.delete(child.path);
          else expandedFolders.add(child.path);
          renderTree();
        });
        const title = document.createElement('div');
        title.setAttribute('role', 'button');
        title.tabIndex = 0;
        title.className = 'lesson-tree-folder-title';
        title.addEventListener('click', () => {
          selectedFolder = child.path;
          currentFile = '';
          renderTree();
          updateEditor();
        });
        title.addEventListener('keydown', event => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            title.click();
          }
        });
        addSelectionCheckbox(header, child.path);
        const icon = createExplorerEntryIcon('folder');
        const name = document.createElement('span');
        name.className = 'lesson-tree-folder-name';
        name.textContent = child.name;
        title.append(icon, name);
        header.append(toggle, title);
        addEntryMenu(header, child.path);
        folder.appendChild(header);
        if (child.children.size > 0) {
          const children = document.createElement('div');
          children.className = 'lesson-tree-folder-children' + (expandedFolders.has(child.path) || query ? '' : ' is-collapsed');
          renderTreeNode(child, children, query);
          folder.appendChild(children);
        }
        parent.appendChild(folder);
        return;
      }
      const file = document.createElement('div');
      file.className = 'lesson-tree-file';
      const row = document.createElement('div');
      row.className = 'lesson-tree-entry-row' + (selectedPaths.has(child.path) ? ' is-selected-row' : '');
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'lesson-tree-file-button' + (currentFile === child.path ? ' is-active' : '');
      button.setAttribute('aria-current', currentFile === child.path ? 'true' : 'false');
      addSelectionCheckbox(row, child.path);
      const icon = createExplorerEntryIcon('file');
      const copy = document.createElement('span');
      copy.className = 'lesson-tree-file-copy';
      const name = document.createElement('span');
      name.className = 'lesson-tree-file-name';
      name.textContent = child.name;
      copy.appendChild(name);
      button.append(icon, copy);
      button.addEventListener('click', () => {
        selectedFolder = pathSegments(child.path).slice(0, -1).join('/');
        expandParents(child.path);
        currentFile = child.path;
        renderTree();
        updateEditor();
      });
      row.appendChild(button);
      addEntryMenu(row, child.path);
      file.appendChild(row);
      parent.appendChild(file);
    });
  }

  function renderTree() {
    window.PPEExplorerMenus.clear(itemTree);
    itemTree.replaceChildren();
    const root = createTree();
    const query = document.getElementById('exerciseSearch').value.trim().toLocaleLowerCase();
    const empty = document.getElementById('emptyExercise');
    empty.hidden = items.size > 0;
    const rootButton = document.createElement('button');
    rootButton.type = 'button';
    rootButton.className = 'btn btn-sm btn-outline-secondary mb-2';
    rootButton.append(createExplorerEntryIcon('folder'), document.createTextNode(root.name));
    rootButton.addEventListener('click', () => {
      selectedFolder = '';
      currentFile = '';
      renderTree();
      updateEditor();
    });
    itemTree.appendChild(rootButton);
    renderTreeNode(root, itemTree, query);
    window.PPEExplorerMenus.bind(itemTree);
    const visibleFiles = Array.from(items.values()).filter(item => item.type === 'file'
      && (!query || item.path.toLocaleLowerCase().includes(query))).length;
    const searchStatus = document.getElementById('exerciseSearchStatus');
    searchStatus.hidden = !query;
    searchStatus.textContent = query ? visibleFiles + '件のファイルが見つかりました。' : '';
    document.getElementById('exerciseSelectResults').hidden = !query;
    const displayStates = [];
    if (query) displayStates.push('検索中');
    if (document.getElementById('exerciseSort').value === 'updated') displayStates.push('更新日時順');
    document.getElementById('exerciseDisplayStatus').textContent = displayStates.length
      ? ' · ' + displayStates.join(' / ') : '';
    document.getElementById('exerciseCreationTarget').textContent = '追加先: ' + (selectedFolder || root.name);
    syncItemsJson();
    updateSelectionControls();
  }

  function updateSelectionControls() {
    const bar = document.getElementById('exerciseSelectionBar');
    const selectionPanel = document.getElementById('exerciseSelectionPanel');
    const count = selectedPaths.size;
    document.getElementById('exerciseSelectMode').setAttribute('aria-pressed', String(selectionMode));
    document.getElementById('exerciseSelectMode').textContent = selectionMode ? '選択中' : '選択';
    document.getElementById('exerciseSelectionFinish').hidden = !selectionMode;
    document.getElementById('exerciseSelectionTools').hidden = !selectionMode;
    bar.hidden = !selectionMode;
    itemTree.classList.toggle('is-selection-mode', selectionMode);
    document.getElementById('exerciseSelectionCount').textContent = '選択' + count + '件';
    document.getElementById('exerciseSelectionStatus').textContent = selectionMode ? ' / ' + count + '件' : '';
    selectionPanel.open = selectionMode || selectionPanel.open;
    document.getElementById('downloadAllButton').disabled = previewRunning || count === 0;
    document.getElementById('exerciseMoveSelected').disabled = previewRunning || count === 0;
    document.getElementById('exerciseDeleteSelected').disabled = previewRunning || count === 0;
    document.getElementById('exerciseSelectMode').disabled = previewRunning;
    document.getElementById('exerciseSelectAll').disabled = previewRunning || count === 0;
    document.getElementById('exerciseSelectResults').disabled = previewRunning || count === 0;
    document.getElementById('exerciseSelectionFinish').disabled = previewRunning;
    document.getElementById('exerciseClearSelected').disabled = previewRunning || count === 0;
    document.querySelectorAll('#exerciseTree button, #exerciseTree input').forEach(button => {
      button.disabled = previewRunning;
    });
  }

  function updateEditor() {
    const item = items.get(currentFile);
    const rootName = document.getElementById('distributionRootName').value || 'root';
    const selectedPath = currentFile || selectedFolder;
    editorTextarea.disabled = !item;
    document.getElementById('saveButton').disabled = !item || previewRunning;
    document.getElementById('downloadButton').disabled = !item || previewRunning;
    document.getElementById('runButton').disabled = !item || !/\.py$/i.test(item.path) || previewRunning;
    document.getElementById('distributionCurrentFileTab').textContent = item
      ? item.path.split('/').pop() : 'ファイルを選択してください';
    document.getElementById('currentPath').textContent = item
      ? rootName + '/' + item.path : selectedPath ? rootName + '/' + selectedPath : rootName;
    if (editor) {
      const code = item ? item.content || '' : '';
      if (editor.getValue() !== code) {
        suppressEditorChange = true;
        try {
          editor.setValue(code);
        } finally {
          suppressEditorChange = false;
        }
      }
      editor.setOption('readOnly', !item || previewRunning);
      editor.refresh();
    } else {
      editorTextarea.value = item ? item.content || '' : '';
      editorTextarea.readOnly = previewRunning;
    }
    document.getElementById('exerciseEditorMessage').textContent = item
      ? '配信テンプレートへの変更は「保存」または配信操作で反映されます。'
      : 'ツリーからファイルを選択してください。';
  }

  function syncItemsJson() {
    itemsJson.value = JSON.stringify(Array.from(items.values()));
  }

  function collectTargets() {
    return selectedClassInputs().map(checkbox => ({
      classroomId: Number(checkbox.dataset.classroomId),
      immediate: immediateValues.get(checkbox.dataset.classroomId) !== false,
      scheduledAt: immediateValues.get(checkbox.dataset.classroomId) === false
        ? scheduleValues.get(checkbox.dataset.classroomId) || null : null
    }));
  }

  function collectItems() {
    const current = items.get(currentFile);
    if (current && editor) current.content = editor.getValue();
    syncItemsJson();
    return Array.from(items.values());
  }

  function fillEntryParents(select, selectedPath) {
    select.replaceChildren();
    allFolderPaths().sort((left, right) => left.localeCompare(right, 'ja')).forEach(path => {
      select.add(new Option(path || (document.getElementById('distributionRootName').value || 'root'), path));
    });
    select.value = selectedPath;
  }

  function updateEntryNameExtension() {
    const name = document.getElementById('entryName').value;
    const extension = document.getElementById('entryNameExtension');
    const show = pendingEntryType === 'file'
      && (pendingEntryAction === 'create' || pendingEntryAction === 'rename') && !/\.py$/i.test(name);
    extension.hidden = !show;
    extension.classList.toggle('d-none', !show);
    document.getElementById('entryName').setAttribute('aria-describedby',
      show ? 'entryNameHelp entryNameExtension' : 'entryNameHelp');
  }

  function renderUploadFolders() {
    const tree = document.getElementById('uploadFolderTree');
    tree.replaceChildren();
    const nodes = new Map(allFolderPaths().map(path => [path, { path, children: [] }]));
    nodes.forEach(node => {
      if (!node.path) return;
      const parent = nodes.get(pathSegments(node.path).slice(0, -1).join('/'));
      if (parent) parent.children.push(node);
    });
    const appendFolder = (node, parent) => {
      node.children.sort((left, right) =>
        left.path.localeCompare(right.path, 'ja', { numeric: true }));
      const wrapper = document.createElement('div');
      wrapper.className = 'move-entry-folder';
      if (!node.path) wrapper.classList.add('is-root-node');
      if (node.path === uploadDestination) wrapper.classList.add('is-selected-target');
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'move-entry-folder-button';
      button.setAttribute('aria-pressed', String(node.path === uploadDestination));
      button.title = node.path || (document.getElementById('distributionRootName').value || 'root');
      const main = document.createElement('span');
      main.className = 'move-entry-folder-main';
      const icon = controlIcon('folder');
      icon.classList.add('move-entry-folder-icon');
      const name = document.createElement('span');
      name.className = 'move-entry-folder-name';
      name.textContent = node.path ? pathSegments(node.path).pop()
        : (document.getElementById('distributionRootName').value || 'root');
      main.append(icon, name);
      button.appendChild(main);
      button.addEventListener('click', () => {
        uploadDestination = node.path;
        resetUploadPreview();
        renderUploadFolders();
      });
      wrapper.appendChild(button);
      if (node.children.length) {
        const children = document.createElement('div');
        children.className = 'move-entry-children';
        node.children.forEach(child => appendFolder(child, children));
        wrapper.appendChild(children);
      }
      parent.appendChild(wrapper);
    };
    const root = nodes.get('');
    if (root) appendFolder(root, tree);
  }

  function renderUploadSelection(input, directory) {
    const all = Array.from(input.files || []);
    uploadFilesSelected = all.filter(file => /\.py$/i.test(file.name));
    resetUploadPreview();
    const excluded = all.length - uploadFilesSelected.length;
    document.getElementById('uploadFileName').textContent = all.length
      ? uploadFilesSelected.length + '件の.py' + (excluded ? '（.py以外 ' + excluded + '件は除外）' : '')
      : '未選択';
    const preview = document.getElementById('uploadFilePreviewList');
    preview.replaceChildren(...uploadFilesSelected.map(file => {
      const item = document.createElement('li');
      item.textContent = directory ? file.webkitRelativePath : file.name;
      return item;
    }));
    document.getElementById('uploadEntryConfirmButton').disabled = uploadFilesSelected.length === 0;
  }

  function resetUploadPreview() {
    uploadPreview = null;
    document.getElementById('uploadResolutionPanel').hidden = true;
    document.getElementById('uploadResolutionItems').replaceChildren();
    document.getElementById('uploadEntryConfirmButton').textContent = '追加内容を確認';
  }

  function renderUploadConflicts(candidates) {
    const panel = document.getElementById('uploadResolutionPanel');
    const container = document.getElementById('uploadResolutionItems');
    container.replaceChildren();
    const conflicts = candidates.filter(candidate => candidate.conflict);
    if (conflicts.length === 0) {
      container.textContent = candidates.length + 'ファイルを追加します。同名の項目はありません。';
    } else {
      conflicts.forEach(candidate => {
        const row = document.createElement('div');
        row.className = 'lesson-batch-item';
        const label = document.createElement('label');
        label.textContent = candidate.path + '（同名または追加先に競合する項目があります）';
        const select = document.createElement('select');
        select.className = 'form-select';
        select.dataset.uploadAction = String(candidate.index);
        select.add(new Option('解決方法を選んでください', ''));
        if (!candidate.blocked) select.add(new Option('別の名前で追加', 'rename'));
        select.add(new Option('追加しない', 'skip'));
        const name = document.createElement('input');
        name.type = 'text';
        name.className = 'form-control mt-2';
        name.dataset.uploadName = String(candidate.index);
        name.value = uniqueCopyPath(candidate.path).split('/').pop();
        name.hidden = true;
        name.setAttribute('aria-label', candidate.path + 'の別名');
        select.addEventListener('change', () => { name.hidden = select.value !== 'rename'; });
        label.appendChild(select);
        row.append(label, name);
        container.appendChild(row);
      });
    }
    panel.hidden = false;
    document.getElementById('uploadEntryConfirmButton').textContent = '確認して追加';
  }

  function openEntryDialog(type) {
    pendingEntryType = type;
    pendingEntryAction = 'create';
    document.getElementById('createEntryTitle').textContent = type === 'folder' ? '新しいフォルダ' : '新しいファイル';
    document.getElementById('entryNameHelp').textContent = type === 'file'
      ? '1〜255文字。使えない文字：/（スラッシュ）、\\（バックスラッシュ）、改行、タブなど。空白だけの名前も使えません。「.」だけ・「..」だけの名前は使えません。'
      : '1〜255文字で入力してください。使えない文字：. / \\ 。名前の途中で改行したり、タブを入れたりしないでください。空白だけの名前も使えません。';
    const parent = document.getElementById('parentEntry');
    fillEntryParents(parent, selectedFolder);
    parent.disabled = false;
    createFeedback.clearInlineAlert();
    const name = document.getElementById('entryName');
    name.value = '';
    updateEntryNameExtension();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('createEntryModal')).show();
    window.setTimeout(() => name.focus(), 150);
  }

  function openRenameDialog(path) {
    const item = items.get(path);
    const isFolder = item?.type === 'folder' || hasDescendants(path);
    pendingEntryType = isFolder ? 'folder' : 'file';
    pendingEntryAction = 'rename';
    document.getElementById('createEntryTitle').textContent = '名前を変更';
    document.getElementById('entryNameHelp').textContent = '親フォルダ内の名前だけを変更します。フォルダ内の項目も一緒に移動します。';
    const parent = document.getElementById('parentEntry');
    fillEntryParents(parent, pathSegments(path).slice(0, -1).join('/'));
    parent.disabled = true;
    createFeedback.clearInlineAlert();
    document.getElementById('entryName').value = path.split('/').pop();
    document.getElementById('entryName').dataset.renamePath = path;
    updateEntryNameExtension();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('createEntryModal')).show();
    window.setTimeout(() => document.getElementById('entryName').focus(), 150);
  }

  function hasDescendants(path) {
    const prefix = path + '/';
    return Array.from(items.keys()).some(existing => existing.startsWith(prefix));
  }

  function allFolderPaths() {
    const folders = new Set(['']);
    items.forEach(item => {
      const segments = pathSegments(item.path);
      if (item.type === 'folder') folders.add(item.path);
      segments.slice(0, -1).forEach((_, index) => folders.add(segments.slice(0, index + 1).join('/')));
    });
    return Array.from(folders);
  }

  function topLevelPaths(paths) {
    return Array.from(new Set(paths)).filter(path => !Array.from(new Set(paths)).some(other =>
      other !== path && path.startsWith(other + '/')));
  }

  function pathExists(path) {
    const normalized = path.toLocaleLowerCase();
    return Array.from(items.keys()).some(existing => {
      const existingPath = existing.toLocaleLowerCase();
      return existingPath === normalized || existingPath.startsWith(normalized + '/');
    });
  }

  function assertNoPathCollisions(replacements, removedPaths) {
    const removed = new Set(removedPaths.map(path => path.toLocaleLowerCase()));
    replacements.forEach(item => {
      if (item.type !== 'folder') return;
      const prefix = item.path.toLocaleLowerCase() + '/';
      const existingChild = Array.from(items.keys()).some(path =>
        path.toLocaleLowerCase().startsWith(prefix) && !removed.has(path.toLocaleLowerCase()));
      if (existingChild) throw new Error('移動先に同名のフォルダまたはファイルがあります。');
    });
    const combined = [
      ...Array.from(items.values()).filter(item => !removed.has(item.path.toLocaleLowerCase())),
      ...replacements
    ];
    const byPath = new Map();
    combined.forEach(item => {
      const target = item.path.toLocaleLowerCase();
      if (byPath.has(target)) throw new Error('操作後に同じパスの項目が重複します。');
      byPath.set(target, item);
    });
    combined.forEach(item => {
      const segments = pathSegments(item.path);
      for (let index = 1; index < segments.length; index += 1) {
        const ancestor = byPath.get(segments.slice(0, index).join('/').toLocaleLowerCase());
        if (ancestor?.type === 'file') throw new Error('ファイルの配下に項目を作成できません。');
      }
      if (item.type === 'file' && combined.some(other =>
        other.path.toLocaleLowerCase().startsWith(item.path.toLocaleLowerCase() + '/'))) {
        throw new Error('フォルダ内に項目があるため、ファイルには変更できません。');
      }
    });
  }

  function renamePath(oldPath, nextName) {
    const parent = pathSegments(oldPath).slice(0, -1).join('/');
    const nextPath = [parent, nextName].filter(Boolean).join('/');
    if (nextPath === oldPath) return true;
    if (nextPath === oldPath || nextPath.startsWith(oldPath + '/')) {
      throw new Error('フォルダを自身またはその配下へ移動できません。');
    }
    const moving = Array.from(items.values())
      .filter(item => item.path === oldPath || item.path.startsWith(oldPath + '/'));
    const replacements = moving
      .map(item => ({ ...item, path: nextPath + item.path.slice(oldPath.length) }));
    assertNoPathCollisions(replacements, moving.map(item => item.path));
    Array.from(items.keys()).filter(path => path === oldPath || path.startsWith(oldPath + '/')).forEach(path => items.delete(path));
    replacements.forEach(item => items.set(item.path, item));
    if (currentFile === oldPath || currentFile.startsWith(oldPath + '/')) currentFile = nextPath + currentFile.slice(oldPath.length);
    if (selectedFolder === oldPath || selectedFolder.startsWith(oldPath + '/')) selectedFolder = nextPath + selectedFolder.slice(oldPath.length);
    const renamedSelections = Array.from(selectedPaths, path =>
      path === oldPath || path.startsWith(oldPath + '/') ? nextPath + path.slice(oldPath.length) : path);
    selectedPaths.clear();
    renamedSelections.forEach(path => selectedPaths.add(path));
    const wasExpanded = Array.from(expandedFolders).filter(path => path === oldPath || path.startsWith(oldPath + '/'));
    wasExpanded.forEach(path => expandedFolders.delete(path));
    wasExpanded.forEach(path => expandedFolders.add(nextPath + path.slice(oldPath.length)));
    return true;
  }

  function createEntry() {
    const nameInput = document.getElementById('entryName');
    let name = nameInput.value.trim();
    if (pendingEntryType === 'file' && !/\.py$/i.test(name)) name += '.py';
    if (!name || name.length > 255 || /[/\\\r\n\t]/.test(name) || name === '.' || name === '..'
      || (pendingEntryType === 'folder' && name.includes('.'))) {
      createFeedback.inlineAlert('名前を確認してください。使用できない文字または文字数の上限に達しています。', 'warning');
      return;
    }
    try {
      if (pendingEntryAction === 'rename') {
        renamePath(nameInput.dataset.renamePath, name);
        delete nameInput.dataset.renamePath;
      } else {
        const parentPath = document.getElementById('parentEntry').value;
        const path = [parentPath, name].filter(Boolean).join('/');
        if (pathExists(path)) throw new Error('同じ場所に同名の項目があります。');
        const content = pendingEntryType === 'file' && /\.py$/i.test(name)
          ? '# ' + name + '\nprint("hello")\n' : null;
        const newItem = { path, type: pendingEntryType, content, updatedAt: Date.now() };
        assertNoPathCollisions([newItem], []);
        items.set(path, newItem);
        if (pendingEntryType === 'file') {
          currentFile = path;
          selectedFolder = pathSegments(path).slice(0, -1).join('/');
          expandParents(path);
        } else {
          selectedFolder = path;
          expandedFolders.add(path);
        }
      }
      bootstrap.Modal.getOrCreateInstance(document.getElementById('createEntryModal')).hide();
      renderTree();
      updateEditor();
    } catch (exception) {
      createFeedback.inlineAlert(exception.message, 'warning');
    }
  }

  function openBatchDialog(action, paths) {
    batchAction = action;
    batchPaths = topLevelPaths(paths);
    if (action === 'delete') {
      performBatchAction().catch(error => feedback.inlineAlert(error.message, 'danger'));
      return;
    }
    const labels = { move: '移動', delete: '完全削除', duplicate: '複製' };
    document.getElementById('batchOperationTitle').textContent = labels[action] || '選択項目の操作';
    document.getElementById('batchOperationDescription').textContent = batchPaths.length + '件の項目を' + labels[action] + 'します。';
    const list = document.getElementById('batchOperationItems');
    list.replaceChildren();
    batchPaths.forEach(path => {
      const entry = document.createElement('li');
      entry.textContent = path;
      list.appendChild(entry);
    });
    document.getElementById('batchOperationError').textContent = '';
    const destinationGroup = document.getElementById('batchDestinationGroup');
    destinationGroup.hidden = action !== 'move';
    const confirmButton = document.getElementById('batchOperationConfirm');
    confirmButton.textContent = action === 'delete' ? '完全に削除' : labels[action] || '実行';
    confirmButton.classList.toggle('btn-danger', action === 'delete');
    confirmButton.classList.toggle('btn-primary', action !== 'delete');
    if (action === 'move') {
      const destination = document.getElementById('batchDestination');
      destination.replaceChildren();
      allFolderPaths().sort((a, b) => a.localeCompare(b, 'ja')).forEach(path => {
        if (batchPaths.some(source => path === source || path.startsWith(source + '/'))) return;
        destination.add(new Option(path || (document.getElementById('distributionRootName').value || 'root'), path));
      });
    }
    bootstrap.Modal.getOrCreateInstance(document.getElementById('batchOperationModal')).show();
  }

  async function performBatchAction() {
    if (batchAction === 'delete') {
      const confirmed = await feedback.danger({
        title: '項目を完全に削除しますか？',
        message: batchPaths.join('、') + 'とフォルダ内のすべての項目を、現在のテンプレートから完全に削除します。この操作は取り消せません。保存済み配信履歴と配信後の生徒データには影響しません。',
        confirmLabel: '完全に削除',
        cancelLabel: '戻る'
      });
      if (!confirmed) return;
      batchPaths.forEach(path => Array.from(items.keys()).filter(existing =>
        existing === path || existing.startsWith(path + '/')).forEach(existing => items.delete(existing)));
      if (batchPaths.some(path => currentFile === path || currentFile.startsWith(path + '/'))) currentFile = '';
      if (batchPaths.some(path => selectedFolder === path || selectedFolder.startsWith(path + '/'))) selectedFolder = '';
      batchPaths.forEach(path => selectedPaths.delete(path));
    } else if (batchAction === 'duplicate') {
      batchPaths.forEach(path => {
        const copyRoot = uniqueCopyPath(path);
        const cloned = Array.from(items.values()).filter(item => item.path === path || item.path.startsWith(path + '/'))
          .map(item => ({ ...item, path: copyRoot + item.path.slice(path.length), updatedAt: Date.now() }));
        cloned.forEach(item => items.set(item.path, item));
      });
    } else if (batchAction === 'move') {
      const destination = document.getElementById('batchDestination').value;
      const replacements = [];
      batchPaths.forEach(path => {
        const nextRoot = [destination, path.split('/').pop()].filter(Boolean).join('/');
        const sourceIsFolder = items.get(path)?.type === 'folder' || hasDescendants(path);
        if (sourceIsFolder && nextRoot.startsWith(path + '/')) {
          throw new Error('フォルダを自身またはその配下へ移動できません。');
        }
        Array.from(items.values()).filter(item => item.path === path || item.path.startsWith(path + '/'))
          .forEach(item => replacements.push({ oldPath: item.path, item: { ...item, path: nextRoot + item.path.slice(path.length) } }));
      });
      assertNoPathCollisions(replacements.map(entry => entry.item), replacements.map(entry => entry.oldPath));
      replacements.forEach(entry => items.delete(entry.oldPath));
      replacements.forEach(entry => items.set(entry.item.path, entry.item));
      const moved = new Map(replacements.map(entry => [entry.oldPath, entry.item.path]));
      if (moved.has(currentFile)) currentFile = moved.get(currentFile);
      if (moved.has(selectedFolder)) selectedFolder = moved.get(selectedFolder);
    }
    selectedPaths.clear();
    if (batchAction !== 'delete') {
      bootstrap.Modal.getOrCreateInstance(document.getElementById('batchOperationModal')).hide();
    }
    renderTree();
    updateEditor();
  }

  function uniqueCopyPath(path) {
    const name = path.split('/').pop();
    const parent = pathSegments(path).slice(0, -1).join('/');
    const extensionIndex = name.lastIndexOf('.');
    const stem = extensionIndex > 0 ? name.slice(0, extensionIndex) : name;
    const extension = extensionIndex > 0 ? name.slice(extensionIndex) : '';
    let index = 1;
    let candidate;
    do {
      candidate = [parent, stem + '（コピー' + (index > 1 ? ' ' + index : '') + '）' + extension].filter(Boolean).join('/');
      index += 1;
    } while (pathExists(candidate));
    return candidate;
  }

  function restoreFormData() {
    readItemsJson();
    restoreTargets();
    updateClassOptions();
    if (items.size > 0) {
      currentFile = Array.from(items.values()).find(item => item.type === 'file')?.path || '';
    }
    renderTree();
    updateEditor();
  }

  async function loadTemplate(templateId, duplicate) {
    const response = await fetch('distribution?view=template&id=' + encodeURIComponent(templateId), {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) throw new Error('テンプレートを読み込めませんでした。画面を更新して再度お試しください。');
    const template = await response.json();
    form.reset();
    classSelect.value = '';
    scheduleValues.clear();
    immediateValues.clear();
    templateIdInput.value = duplicate ? '0' : String(template.templateId);
    versionInput.value = duplicate ? '0' : String(template.version);
    requestTokenInput.value = newRequestToken();
    actionInput.value = 'saveDraft';
    document.getElementById('distributionName').value = duplicate ? template.name + '（コピー）' : template.name;
    document.getElementById('distributionRootName').value = template.rootName;
    items.clear();
    template.items.forEach(item => items.set(item.path, {
      path: item.path, type: item.type.toLowerCase(),
      content: item.type.toLowerCase() === 'file' ? item.content : null, updatedAt: 0
    }));
    currentFile = Array.from(items.values()).find(item => item.type === 'file')?.path || '';
    selectedFolder = '';
    selectedPaths.clear();
    expandedFolders.clear();
    items.forEach(item => expandParents(item.path));
    updateClassOptions();
    renderTree();
    updateEditor();
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    document.getElementById('distributionName').focus({ preventScroll: true });
  }

  function downloadBlob(filename, blob) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function downloadCurrentFile() {
    const item = items.get(currentFile);
    if (!item) return;
    item.content = editor.getValue();
    downloadBlob(item.path.split('/').pop(), new Blob([item.content], { type: 'text/x-python;charset=utf-8' }));
  }

  function crc32(bytes) {
    let crc = 0xffffffff;
    bytes.forEach(byte => {
      crc ^= byte;
      for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    });
    return (crc ^ 0xffffffff) >>> 0;
  }

  function zipBytes(selectedItems = Array.from(items.values())) {
    const encoder = new TextEncoder();
    const fileEntries = selectedItems.filter(item => item.type === 'file');
    const folderPaths = new Set(['']);
    selectedItems.forEach(item => {
      const segments = pathSegments(item.path);
      if (item.type === 'folder') folderPaths.add(item.path);
      segments.slice(0, -1).forEach((segment, index) => folderPaths.add(segments.slice(0, index + 1).join('/')));
    });
    if (selectedItems.length === 0) {
      throw new Error('ダウンロードする項目がありません。');
    }
    const rootName = document.getElementById('distributionRootName').value || 'root';
    const locals = [];
    const centrals = [];
    let offset = 0;
    const now = new Date();
    const dosTime = (now.getHours() << 11) | (now.getMinutes() << 5) | Math.floor(now.getSeconds() / 2);
    const dosDate = ((now.getFullYear() - 1980) << 9) | ((now.getMonth() + 1) << 5) | now.getDate();
    const zipEntries = [
      ...Array.from(folderPaths).map(path => ({ path: rootName + (path ? '/' + path : '') + '/', content: '', type: 'folder' })),
      ...fileEntries.map(item => ({ path: rootName + '/' + item.path, content: item.content || '', type: 'file' }))
    ];
    zipEntries.forEach(item => {
      const name = encoder.encode(item.path);
      const data = encoder.encode(item.content);
      const crc = crc32(data);
      const local = new Uint8Array(30 + name.length);
      const lv = new DataView(local.buffer);
      lv.setUint32(0, 0x04034b50, true); lv.setUint16(4, 20, true); lv.setUint16(6, 0x0800, true);
      lv.setUint16(8, 0, true); lv.setUint16(10, dosTime, true); lv.setUint16(12, dosDate, true);
      lv.setUint32(14, crc, true); lv.setUint32(18, data.length, true); lv.setUint32(22, data.length, true);
      lv.setUint16(26, name.length, true); lv.setUint16(28, 0, true); local.set(name, 30);
      locals.push(local, data);

      const central = new Uint8Array(46 + name.length);
      const cv = new DataView(central.buffer);
      cv.setUint32(0, 0x02014b50, true); cv.setUint16(4, 20, true); cv.setUint16(6, 20, true);
      cv.setUint16(8, 0x0800, true); cv.setUint16(10, 0, true); cv.setUint16(12, dosTime, true);
      cv.setUint16(14, dosDate, true); cv.setUint32(16, crc, true); cv.setUint32(20, data.length, true);
      cv.setUint32(24, data.length, true); cv.setUint16(28, name.length, true); cv.setUint16(30, 0, true);
      cv.setUint16(32, 0, true); cv.setUint16(34, 0, true); cv.setUint32(36, 0, true);
      cv.setUint32(42, offset, true); central.set(name, 46);
      centrals.push(central);
      offset += local.length + data.length;
    });
    const centralSize = centrals.reduce((sum, entry) => sum + entry.length, 0);
    const end = new Uint8Array(22);
    const ev = new DataView(end.buffer);
    ev.setUint32(0, 0x06054b50, true); ev.setUint16(8, zipEntries.length, true); ev.setUint16(10, zipEntries.length, true);
    ev.setUint32(12, centralSize, true); ev.setUint32(16, offset, true);
    ev.setUint16(4, 0, true);
    return new Blob([...locals, ...centrals, end], { type: 'application/zip' });
  }

  function entriesForPaths(paths) {
    const roots = topLevelPaths(paths);
    return Array.from(items.values()).filter(item => roots.some(path =>
      item.path === path || item.path.startsWith(path + '/')));
  }

  function downloadEntry(path) {
    const item = items.get(path);
    if (item?.type === 'file') {
      if (path === currentFile) item.content = editor.getValue();
      downloadBlob(item.path.split('/').pop(), new Blob([item.content || ''], { type: 'text/x-python;charset=utf-8' }));
      return;
    }
    const selected = entriesForPaths([path]);
    downloadBlob((path.split('/').pop() || 'exercise') + '.zip', zipBytes(selected));
  }

  function downloadSelectedEntries() {
    const selected = entriesForPaths(Array.from(selectedPaths));
    downloadBlob((document.getElementById('distributionRootName').value || 'root') + '.zip', zipBytes(selected));
  }

  async function prepareUpload(files, destination) {
    if (!files.length) throw new Error('アップロードする.pyファイルを選択してください。');
    if (files.length > 100 || files.some(file => file.size > 65536)
      || files.reduce((sum, file) => sum + file.size, 0) > 1048576) {
      throw new Error('1ファイル64 KiB・100ファイル・合計1 MiB以内にしてください。');
    }
    const candidates = [];
    const pathsSeen = new Set();
    for (const [index, file] of files.entries()) {
      const relative = (file.webkitRelativePath || file.name).split('/').filter(Boolean);
      if (relative.some(segment => segment === '.' || segment === '..' || /[\\\r\n\t]/.test(segment))) {
        throw new Error(file.name + '：ファイル名またはパスが無効です。');
      }
      const path = [destination, ...relative].filter(Boolean).join('/');
      const bytes = new Uint8Array(await file.arrayBuffer());
      let content;
      try {
        content = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
      } catch (error) {
        throw new Error(path + '：UTF-8のファイルを選択してください。');
      }
      const segments = pathSegments(path);
      const blocked = segments.slice(0, -1).some((_, ancestorIndex) => {
        const ancestorPath = segments.slice(0, ancestorIndex + 1).join('/');
        return items.get(ancestorPath)?.type === 'file';
      });
      const normalized = path.toLocaleLowerCase();
      const conflict = blocked || pathExists(path) || pathsSeen.has(normalized);
      pathsSeen.add(normalized);
      candidates.push({
        index, path, content, type: 'file', updatedAt: Date.now(),
        conflict, blocked
      });
    }
    return { destination, candidates };
  }

  function commitUpload(preview) {
    const additions = [];
    const skipped = [];
    for (const candidate of preview.candidates) {
      let path = candidate.path;
      if (candidate.conflict) {
        const action = document.querySelector('[data-upload-action="' + candidate.index + '"]')?.value;
        if (!action) throw new Error('同名項目の処理を選択してください。');
        if (action === 'skip') {
          skipped.push(candidate.path);
          continue;
        }
        const name = document.querySelector('[data-upload-name="' + candidate.index + '"]').value.trim();
        if (!name || /[/\\\r\n\t]/.test(name) || !/\.py$/i.test(name)) {
          throw new Error('別名は有効な.pyファイル名で入力してください。');
        }
        path = [pathSegments(candidate.path).slice(0, -1).join('/'), name].filter(Boolean).join('/');
      }
      additions.push({ path, type: 'file', content: candidate.content, updatedAt: Date.now() });
    }
    assertNoPathCollisions(additions, []);
    additions.forEach(item => {
      items.set(item.path, item);
      pathSegments(item.path).slice(0, -1).forEach((_, index) =>
        expandedFolders.add(pathSegments(item.path).slice(0, index + 1).join('/')));
    });
    if (additions.length) currentFile = additions[additions.length - 1].path;
    renderTree();
    updateEditor();
    if (skipped.length) {
      feedback.toast({
        message: additions.length + '件を追加しました。追加しない項目: ' + skipped.join('、'),
        variant: 'warning'
      });
    } else {
      feedback.toast({ message: additions.length + '件のファイルを追加しました。', variant: 'success' });
    }
    return additions.length;
  }

  function statusLabel(status) {
    return ({ draft: '下書き', scheduled: '配信予定', in_progress: '配信中', completed: '配信完了', stopped: '停止' })[status] || status;
  }

  function historyRows() {
    return Array.from(document.querySelectorAll('#distributionHistoryBody [data-history-row]'));
  }

  function populateHistoryFilters() {
    const fields = [
      ['distributionHistoryFilterSchool', 'school'],
      ['distributionHistoryFilterClass', 'class'],
      ['distributionHistoryFilterOperator', 'operator']
    ];
    fields.forEach(([id, key]) => {
      const select = document.getElementById(id);
      const previous = select.value;
      const values = new Set();
      historyRows().forEach(row => (row.dataset[key] || '').split(',').map(value => value.trim()).filter(Boolean).forEach(value => values.add(value)));
      select.replaceChildren(new Option('すべて', ''));
      Array.from(values).sort((a, b) => a.localeCompare(b, 'ja')).forEach(value => select.add(new Option(value, value)));
      if (Array.from(select.options).some(option => option.value === previous)) select.value = previous;
    });
  }

  function applyHistoryFilters() {
    const query = document.getElementById('distributionHistorySearch').value.trim().toLocaleLowerCase();
    const school = document.getElementById('distributionHistoryFilterSchool').value;
    const classroom = document.getElementById('distributionHistoryFilterClass').value;
    const status = document.getElementById('distributionHistoryFilterStatus').value;
    const operator = document.getElementById('distributionHistoryFilterOperator').value;
    historyRows().forEach(row => {
      const match = (!query || row.textContent.toLocaleLowerCase().includes(query))
        && (!school || (row.dataset.school || '').split(', ').includes(school))
        && (!classroom || (row.dataset.class || '').split(', ').includes(classroom))
        && (!status || row.dataset.status === status)
        && (!operator || row.dataset.operator === operator);
      row.hidden = !match;
    });
    sortHistoryRows();
  }

  function sortHistoryRows() {
    const compare = (left, right) => (left.dataset[sortKey] || '').localeCompare(right.dataset[sortKey] || '', 'ja', { numeric: true })
      * (sortDirection === 'asc' ? 1 : -1);
    const body = document.getElementById('distributionHistoryBody');
    historyRows().sort(compare).forEach(row => body.appendChild(row));
    document.querySelectorAll('#distributionHistoryTable thead th.sortable').forEach(header => {
      header.classList.remove('sorted-asc', 'sorted-desc');
      header.removeAttribute('aria-sort');
      if (header.dataset.sortKey === sortKey) {
        header.classList.add(sortDirection === 'asc' ? 'sorted-asc' : 'sorted-desc');
        header.setAttribute('aria-sort', sortDirection === 'asc' ? 'ascending' : 'descending');
      }
    });
  }

  function exportHistoryCsv() {
    const rows = historyRows().filter(row => !row.hidden);
    const values = [['作成日時', 'テンプレート', '学校', 'クラス', '状態', '作成者'],
      ...rows.map(row => [row.dataset.date, row.dataset.template, row.dataset.school, row.dataset.class,
        statusLabel(row.dataset.status), row.dataset.operator])];
    const csv = '\ufeff' + values.map(line => line.map(value => {
      const text = String(value || '');
      return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
    }).join(',')).join('\r\n');
    const date = new Date().toISOString().slice(0, 10);
    downloadBlob('distribution_history_' + date + '.csv', new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  }

  let previewSessionId = '';
  let previewCursor = 0;
  let pollTimer = 0;
  let previewPollFailures = 0;
  let runResultTruncated = false;
  let previewRunning = false;
  let previewInputSending = false;

  function setPreviewRunning(running) {
    previewRunning = running;
    document.querySelectorAll('.lesson-tree-actions button, .lesson-folder-controls button, #exerciseSelectionPanel button, #distributionDownloadAllButton')
      .forEach(button => { button.disabled = running; });
    renderTree();
    updateEditor();
  }

  function appendPreviewEvent(text, stream) {
    if (!text) return;
    const span = document.createElement('span');
    span.className = stream === 'stdin' || stream === 'input' ? 'terminal-input-echo'
      : stream === 'stderr' ? 'terminal-stderr' : stream === 'system' ? 'terminal-system-message' : '';
    span.textContent = stream === 'stdin' || stream === 'input' ? '>>> ' + text
      : stream === 'stderr' ? '[エラー] ' + text : text;
    const terminal = document.getElementById('terminalOutput');
    terminal.appendChild(span);
    terminal.scrollTop = terminal.scrollHeight;
  }

  async function previewRequest(action, values = {}) {
    const body = new URLSearchParams({
      action,
      csrfToken: form.querySelector('[name="csrfToken"]').value,
      ...values
    });
    const response = await fetch(form.getAttribute('action'), {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', Accept: 'application/json' },
      body
    });
    let result = {};
    try {
      result = await response.json();
    } catch (error) {
      if (response.ok) throw new Error('実行サービスから正しい応答を受け取れませんでした。');
    }
    if (!response.ok) {
      const error = new Error(result.message || (response.status === 503
        ? 'コード実行サービスを利用できません。時間をおいて再度お試しください。'
        : 'コードを実行できませんでした。画面を更新して再度お試しください。'));
      error.statusCode = response.status;
      error.errorCode = result.errorCode;
      throw error;
    }
    return result;
  }

  function updateRunResult(update) {
    if (!update) throw new Error('実行結果を受け取れませんでした。');
    previewSessionId = update.sessionId || previewSessionId;
    const previousCursor = previewCursor;
    previewCursor = Number(update.nextCursor) || previewCursor;
    const status = document.getElementById('runResultStatus');
    const running = update.status === 'running';
    const succeeded = update.status === 'succeeded';
    const cancelled = update.status === 'cancelled' || update.errorCode === 'execution_cancelled';
    const labels = { running: '実行中', succeeded: '実行完了', cancelled: '実行停止', failed: '実行エラー', timed_out: '時間切れ' };
    status.textContent = labels[cancelled ? 'cancelled' : update.status] || update.status || '実行エラー';
    status.className = 'badge ' + (running ? 'text-bg-primary' : succeeded ? 'text-bg-success' : 'text-bg-danger');
    const terminal = document.getElementById('terminalOutput');
    const newEvents = (update.events || []).filter(event => event.id > previousCursor);
    if (newEvents.length && terminal.dataset.placeholder === 'true') {
      terminal.replaceChildren();
      terminal.dataset.placeholder = 'false';
    }
    newEvents.forEach(event => appendPreviewEvent(event.text, event.stream));
    if (!runResultTruncated && (update.standardOutputTruncated || update.standardErrorTruncated)) {
      runResultTruncated = true;
      appendPreviewEvent('\n出力を省略しています。\n', 'system');
    }
    if (!terminal.textContent) {
      terminal.textContent = running ? '実行中です。出力を待っています。' : '出力はありません。';
      terminal.dataset.placeholder = 'true';
    }
    document.getElementById('runResultTime').textContent = running ? '実行中…'
      : '最終実行: ' + new Date().toLocaleTimeString('ja-JP');
    document.getElementById('terminalInput').disabled = !running || previewInputSending;
    document.getElementById('terminalSendButton').disabled = !running || previewInputSending;
    document.getElementById('terminalCancelButton').hidden = !running;
    document.getElementById('terminalCancelButton').disabled = !running;
    if (running !== previewRunning) setPreviewRunning(running);
    document.getElementById('runButton').disabled = running || !/\.py$/i.test(currentFile) || !items.has(currentFile);
    if (running && previewSessionId) {
      window.clearTimeout(pollTimer);
      pollTimer = window.setTimeout(pollPreview, 250);
    } else if (!running) {
      if (update.errorCode === 'input_timeout') {
        appendPreviewEvent('\n30秒間入力がなかったため停止しました。\n', 'system');
      } else if (update.status === 'timed_out') {
        appendPreviewEvent('\n実行時間の上限に達したため停止しました。\n', 'system');
      } else if (cancelled) {
        appendPreviewEvent('\n実行を停止しました。\n', 'system');
      } else if (update.errorCode) {
        appendPreviewEvent('\n' + update.errorCode + '\n', 'system');
      }
      if (!terminal.textContent) terminal.textContent = '出力はありません。';
      window.clearTimeout(pollTimer);
      previewSessionId = '';
    }
  }

  async function pollPreview() {
    if (!previewSessionId) return;
    try {
      const result = await previewRequest('pollPreview', {
        sessionId: previewSessionId,
        cursor: String(previewCursor)
      });
      previewPollFailures = 0;
      feedback.clearInlineAlert();
      updateRunResult(result.update);
    } catch (error) {
      if ([400, 403, 404].includes(error.statusCode)
        || ['runner_cleanup_failed', 'runner_unavailable'].includes(error.errorCode)) {
        previewSessionId = '';
        setPreviewRunning(false);
        document.getElementById('runResultStatus').textContent = '実行状態の取得失敗';
        document.getElementById('runResultStatus').className = 'badge text-bg-danger';
        feedback.inlineAlert(error.message, 'danger');
        return;
      }
      document.getElementById('runResultStatus').textContent = '結果取得を再試行中';
      document.getElementById('runResultStatus').className = 'badge text-bg-warning';
      feedback.inlineAlert(error.message + ' 再取得を試みます。', 'danger');
      if (++previewPollFailures >= 90) {
        appendPreviewEvent('\n実行状態を確認できません。画面を再読み込みして状態を確認してください。\n', 'system');
        previewSessionId = '';
        setPreviewRunning(false);
        document.getElementById('runResultStatus').textContent = '実行状態の取得失敗';
        document.getElementById('runResultStatus').className = 'badge text-bg-danger';
        return;
      }
      pollTimer = window.setTimeout(pollPreview, 1000);
    }
  }

  async function runCurrentFile() {
    const item = items.get(currentFile);
    if (!item || !editor) return;
    item.content = editor.getValue();
    window.clearTimeout(pollTimer);
    previewSessionId = '';
    previewCursor = 0;
    previewPollFailures = 0;
    runResultTruncated = false;
    feedback.clearInlineAlert();
    setPreviewRunning(true);
    document.getElementById('runResultStatus').textContent = '起動中';
    document.getElementById('runResultStatus').className = 'badge text-bg-light border';
    document.getElementById('terminalOutput').replaceChildren();
    document.getElementById('terminalOutput').dataset.placeholder = 'false';
    document.getElementById('runResultTime').textContent = 'コードを実行しています。';
    document.getElementById('terminalInput').value = '';
    document.getElementById('terminalInput').disabled = true;
    document.getElementById('terminalSendButton').disabled = true;
    document.getElementById('terminalCancelButton').hidden = true;
    document.querySelector('.execution-result-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
    try {
      const result = await previewRequest('runPreview', { code: item.content });
      updateRunResult(result.update);
      if (document.getElementById('terminalInput').disabled === false) {
        document.getElementById('terminalInput').focus({ preventScroll: true });
      }
    } catch (error) {
      document.getElementById('runResultStatus').textContent = '実行できません';
      document.getElementById('runResultStatus').className = 'badge text-bg-danger';
      document.getElementById('runResultTime').textContent = error.message;
      previewSessionId = '';
      setPreviewRunning(false);
      feedback.inlineAlert(error.message, 'danger');
    }
  }

  async function sendPreviewInput(event) {
    event.preventDefault();
    if (!previewSessionId || previewInputSending) return;
    const input = document.getElementById('terminalInput');
    const line = input.value;
    previewInputSending = true;
    document.getElementById('terminalInput').disabled = true;
    document.getElementById('terminalSendButton').disabled = true;
    try {
      await previewRequest('sendPreviewInput', { sessionId: previewSessionId, line });
      input.value = '';
    } catch (error) {
      feedback.inlineAlert(error.message, 'danger');
    } finally {
      previewInputSending = false;
      if (previewRunning) {
        document.getElementById('terminalInput').disabled = false;
        document.getElementById('terminalSendButton').disabled = false;
        input.focus({ preventScroll: true });
      }
    }
  }

  async function cancelPreview() {
    if (!previewSessionId) return;
    document.getElementById('terminalCancelButton').disabled = true;
    try {
      await previewRequest('cancelPreview', { sessionId: previewSessionId });
    } catch (error) {
      feedback.inlineAlert(error.message, 'danger');
    } finally {
      document.getElementById('terminalCancelButton').disabled = !previewRunning;
    }
  }

  function attachActionConfirmation(actionForm) {
    const action = actionForm.dataset.confirm || actionForm.dataset.targetAction;
    const isResume = action === 'resume';
    const isReschedule = action === 'reschedule';
    const message = isReschedule
      ? { title: '配信日時を変更しますか？', message: '予約中のクラスの配信日時を更新します。', confirmLabel: '日時を変更する' }
      : isResume
        ? { title: '配信を再開しますか？', message: '停止中のクラスを再開します。', confirmLabel: '再開する' }
        : { title: '配信を停止しますか？', message: '未配信・予約中のクラスを停止します。配信済みのコードは保持されます。', confirmLabel: '停止する', danger: true };
    const confirmation = message.danger ? feedback.danger : feedback.confirm;
    confirmation({
      title: message.title,
      message: message.message,
      confirmLabel: message.confirmLabel,
      cancelLabel: '戻る'
    }).then(confirmed => {
      if (confirmed) HTMLFormElement.prototype.submit.call(actionForm);
    });
  }

  document.getElementById('distributionSchoolSelect').addEventListener('change', () => {
    classOptions.forEach(option => {
      const checkbox = option.querySelector('input[type="checkbox"]');
      checkbox.checked = false;
    });
    updateClassOptions();
  });
  classOptions.forEach(option => option.querySelector('input[type="checkbox"]').addEventListener('change', () => {
    updateClassDropdownLabel();
    renderSchedules();
  }));

  document.getElementById('newFolderButton').addEventListener('click', () => openEntryDialog('folder'));
  document.getElementById('newFileButton').addEventListener('click', () => openEntryDialog('file'));
  document.getElementById('createEntryForm').addEventListener('submit', event => {
    event.preventDefault();
    createEntry();
  });
  document.getElementById('entryName').addEventListener('input', updateEntryNameExtension);
  document.getElementById('createEntryModal').addEventListener('hidden.bs.modal', () => {
    delete document.getElementById('entryName').dataset.renamePath;
    createFeedback.clearInlineAlert();
  });
  document.getElementById('uploadButton').addEventListener('click', () => {
    uploadDestination = selectedFolder;
    uploadFilesSelected = [];
    resetUploadPreview();
    document.getElementById('uploadSourceFile').value = '';
    document.getElementById('uploadSourceDirectory').value = '';
    document.getElementById('uploadFileName').textContent = '未選択';
    document.getElementById('uploadFilePreviewList').replaceChildren();
    document.getElementById('uploadEntryConfirmButton').disabled = true;
    uploadFeedback.clearInlineAlert();
    renderUploadFolders();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('uploadModal')).show();
  });
  for (const [buttonId, inputId, directory] of [
    ['uploadSelectFilesButton', 'uploadSourceFile', false],
    ['uploadSelectFolderButton', 'uploadSourceDirectory', true]
  ]) {
    const input = document.getElementById(inputId);
    document.getElementById(buttonId).addEventListener('click', () => input.click());
    input.addEventListener('change', () => renderUploadSelection(input, directory));
  }
  document.getElementById('uploadEntryConfirmButton').addEventListener('click', async () => {
    uploadFeedback.clearInlineAlert();
    try {
      if (!uploadPreview) {
        uploadPreview = await prepareUpload(uploadFilesSelected, uploadDestination);
        renderUploadConflicts(uploadPreview.candidates);
        return;
      }
      commitUpload(uploadPreview);
      bootstrap.Modal.getOrCreateInstance(document.getElementById('uploadModal')).hide();
    } catch (exception) {
      uploadFeedback.inlineAlert(exception.message, 'warning');
    }
  });
  document.getElementById('uploadModal').addEventListener('hidden.bs.modal', () => uploadFeedback.clearInlineAlert());
  document.getElementById('exerciseSort').addEventListener('change', renderTree);
  document.getElementById('exerciseSearch').addEventListener('input', renderTree);
  document.getElementById('exerciseExpandAll').addEventListener('click', () => {
    allFolderPaths().forEach(path => { if (path) expandedFolders.add(path); });
    renderTree();
  });
  document.getElementById('exerciseCollapseAll').addEventListener('click', () => {
    expandedFolders.clear();
    renderTree();
  });
  document.getElementById('exerciseRevealFile').addEventListener('click', () => {
    if (!currentFile) return;
    pathSegments(currentFile).slice(0, -1).forEach((_, index) => expandedFolders.add(pathSegments(currentFile).slice(0, index + 1).join('/')));
    document.getElementById('exerciseSearch').value = '';
    renderTree();
    const active = itemTree.querySelector('.lesson-tree-file-button.is-active');
    active?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  });
  document.getElementById('exerciseSelectMode').addEventListener('click', () => {
    selectionMode = !selectionMode;
    if (!selectionMode) selectedPaths.clear();
    renderTree();
  });
  document.getElementById('exerciseSelectionFinish').addEventListener('click', () => {
    selectionMode = false;
    selectedPaths.clear();
    renderTree();
  });
  document.getElementById('exerciseSelectAll').addEventListener('click', () => {
    items.forEach(item => selectedPaths.add(item.path));
    renderTree();
  });
  document.getElementById('exerciseSelectResults').addEventListener('click', () => {
    const query = document.getElementById('exerciseSearch').value.trim().toLocaleLowerCase();
    items.forEach(item => {
      if (item.path.toLocaleLowerCase().includes(query)) selectedPaths.add(item.path);
    });
    renderTree();
  });
  document.getElementById('exerciseMoveSelected').addEventListener('click', () => openBatchDialog('move', Array.from(selectedPaths)));
  document.getElementById('exerciseDeleteSelected').addEventListener('click', () => openBatchDialog('delete', Array.from(selectedPaths)));
  document.getElementById('exerciseClearSelected').addEventListener('click', () => {
    selectedPaths.clear();
    renderTree();
  });
  document.getElementById('batchOperationConfirm').addEventListener('click', async () => {
    try {
      await performBatchAction();
    } catch (error) {
      document.getElementById('batchOperationError').textContent = error.message;
    }
  });
  document.getElementById('saveButton').addEventListener('click', () => {
    if (!items.has(currentFile) || previewRunning) return;
    const draftButton = form.querySelector('[data-submit-action="saveDraft"]');
    if (!draftButton) {
      feedback.inlineAlert('下書き保存を開始できません。画面を更新して再度お試しください。', 'danger');
      return;
    }
    form.requestSubmit(draftButton);
  });
  document.getElementById('downloadButton').addEventListener('click', downloadCurrentFile);
  document.getElementById('distributionDownloadAllButton').addEventListener('click', () => {
    try {
      collectItems();
      downloadBlob((document.getElementById('distributionRootName').value || 'root') + '.zip', zipBytes());
    } catch (error) {
      feedback.inlineAlert(error.message, 'warning');
    }
  });
  document.getElementById('downloadAllButton').addEventListener('click', () => {
    try {
      collectItems();
      downloadSelectedEntries();
    } catch (error) {
      feedback.inlineAlert(error.message, 'warning');
    }
  });
  document.getElementById('runButton').addEventListener('click', runCurrentFile);
  const terminalInputForm = document.getElementById('terminalInputForm');
  if (terminalInputForm.tagName === 'FORM') {
    terminalInputForm.addEventListener('submit', sendPreviewInput);
  } else {
    document.getElementById('terminalSendButton').addEventListener('click', sendPreviewInput);
    document.getElementById('terminalInput').addEventListener('keydown', event => {
      if (event.key === 'Enter') sendPreviewInput(event);
    });
  }
  document.getElementById('terminalCancelButton').addEventListener('click', cancelPreview);
  editor = window.PPECodeEditor.create({
    textarea: editorTextarea,
    preferences: window.PPECodeEditor.defaults(),
    options: { lineNumbers: true, theme: 'material-darker', lineWrapping: false }
  });
  editor.on('change', () => {
    if (suppressEditorChange) return;
    const item = items.get(currentFile);
    if (!item) return;
    item.content = editor.getValue();
    item.updatedAt = Date.now();
    document.getElementById('exerciseEditorMessage').textContent = '未保存の変更があります。';
    syncItemsJson();
  });

  document.getElementById('distributionRootName').addEventListener('input', () => {
    updateEditor();
    renderTree();
  });

  document.querySelectorAll('[data-submit-action]').forEach(button => {
    button.addEventListener('click', () => { actionInput.value = button.dataset.submitAction; });
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (event.submitter?.dataset.submitAction) actionInput.value = event.submitter.dataset.submitAction;
    const formItems = collectItems();
    const targets = collectTargets();
    if (formItems.length === 0) {
      feedback.inlineAlert('フォルダまたはファイルを1件以上追加してください。', 'warning');
      return;
    }
    if (actionInput.value === 'distribute' && targets.length === 0) {
      feedback.inlineAlert('配信先のクラスを選択してください。', 'warning');
      return;
    }
    if (actionInput.value === 'distribute' && !classSelect.value) {
      feedback.inlineAlert('配信する学校を1校選択してください。', 'warning');
      return;
    }
    if (actionInput.value === 'distribute' && targets.some(target => !target.immediate && !target.scheduledAt)) {
      feedback.inlineAlert('即時配信にしないクラスには、配信日時を設定してください。', 'warning');
      return;
    }
    itemsJson.value = JSON.stringify(formItems);
    targetsJson.value = JSON.stringify(targets);
    const encodedLength = new URLSearchParams(new FormData(form)).toString().length;
    if (encodedLength > 4 * 1024 * 1024) {
      feedback.inlineAlert('送信内容が上限を超えています。ファイル数またはコード量を減らしてください。', 'warning');
      return;
    }
    if (actionInput.value === 'distribute') {
      const confirmed = await feedback.confirm({
        title: 'コードを配信しますか？',
        message: '選択した学校のクラスに配信します。再配信でも生徒の既存コードは上書きされません。',
        confirmLabel: '配信する',
        cancelLabel: '戻る'
      });
      if (!confirmed) return;
    }
    const submitter = form.querySelector('[data-submit-action="' + actionInput.value + '"]');
    if (submitter) submitter.disabled = true;
    HTMLFormElement.prototype.submit.call(form);
  });

  document.getElementById('distributionNewTemplate').addEventListener('click', () => {
    form.reset();
    classSelect.value = '';
    scheduleValues.clear();
    immediateValues.clear();
    items.clear();
    selectedFolder = '';
    currentFile = '';
    selectedPaths.clear();
    expandedFolders.clear();
    selectionMode = false;
    previewSessionId = '';
    previewCursor = 0;
    window.clearTimeout(pollTimer);
    templateIdInput.value = '0';
    versionInput.value = '0';
    requestTokenInput.value = newRequestToken();
    actionInput.value = 'saveDraft';
    itemsJson.value = '[]';
    targetsJson.value = '[]';
    updateClassOptions();
    renderTree();
    updateEditor();
    document.getElementById('runResultStatus').textContent = '実行待ち';
    document.getElementById('runResultStatus').className = 'badge text-bg-light border';
    document.getElementById('terminalOutput').textContent = 'まだ実行していません。';
    document.getElementById('runResultTime').textContent = '実行すると結果がここに表示されます。';
  });

  document.querySelectorAll('[data-edit-template],[data-duplicate-template]').forEach(button => {
    button.addEventListener('click', async () => {
      try {
        await loadTemplate(button.dataset.editTemplate || button.dataset.duplicateTemplate, Boolean(button.dataset.duplicateTemplate));
      } catch (error) {
        feedback.inlineAlert(error.message, 'danger');
      }
    });
  });

  document.getElementById('distributionHistorySearch').addEventListener('input', applyHistoryFilters);
  ['distributionHistoryFilterSchool', 'distributionHistoryFilterClass', 'distributionHistoryFilterStatus', 'distributionHistoryFilterOperator']
    .forEach(id => document.getElementById(id).addEventListener('change', applyHistoryFilters));
  document.getElementById('distributionHistoryRefreshButton').addEventListener('click', () => window.location.reload());
  document.getElementById('downloadHistoryCsvButton').addEventListener('click', exportHistoryCsv);
  document.querySelectorAll('#distributionHistoryTable thead th.sortable').forEach(header => {
    header.tabIndex = 0;
    header.setAttribute('role', 'button');
    const sort = () => {
      const next = header.dataset.sortKey;
      sortDirection = next === sortKey && sortDirection === 'asc' ? 'desc' : 'asc';
      sortKey = next;
      sortHistoryRows();
    };
    header.addEventListener('click', sort);
    header.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); sort(); }
    });
  });

  document.getElementById('distributionHistoryBody').addEventListener('click', event => {
    const button = event.target.closest('.distribution-history-detail');
    if (!button) return;
    const template = document.getElementById(button.dataset.detailTemplate);
    if (!template) return;
    document.getElementById('distributionHistoryDetailBody').replaceChildren(template.content.cloneNode(true));
    bootstrap.Modal.getOrCreateInstance(document.getElementById('distributionHistoryDetailModal')).show();
  });

  document.addEventListener('submit', event => {
    const actionForm = event.target.closest('form[data-confirm],form[data-target-action]');
    if (!actionForm || actionForm.dataset.confirmed === 'true') return;
    event.preventDefault();
    attachActionConfirmation(actionForm);
  });

  restoreFormData();
  populateHistoryFilters();
  applyHistoryFilters();
  const notice = document.getElementById('distributionNotice');
  if (notice && notice.dataset.message) {
    feedback.toast({ message: notice.dataset.message, variant: 'success', delay: 3000 });
  }
})();
