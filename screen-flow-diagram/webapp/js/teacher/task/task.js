// Teacher task page interactions

let editTargetRow = null;
let isEditingInCreateForm = false;

const reusableHintLibrary = [
  {
    taskId: 'TASK-001',
    hintOrder: 1,
    hintTitle: 'input()',
    hintContent: '文字列を受け取るための関数です。',
    hintCodeExample: 'player = input("手を入力してください: ")'
  },
  {
    taskId: 'TASK-001',
    hintOrder: 2,
    hintTitle: 'if / elif / else',
    hintContent: '条件によって処理を分岐する構文です。',
    hintCodeExample: 'if player == "グー":\n    print("...")\nelif player == "パー":\n    print("...")\nelse:\n    print("...")'
  },
  {
    taskId: 'TASK-002',
    hintOrder: 1,
    hintTitle: 'for 文',
    hintContent: '複数の値を順に処理する繰り返し構文です。',
    hintCodeExample: 'total = 0\nfor score in scores:\n    total += score'
  },
  {
    taskId: 'TASK-003',
    hintOrder: 1,
    hintTitle: '辞書の集計',
    hintContent: 'キーごとに値を合計して分析できます。',
    hintCodeExample: 'summary[item] = summary.get(item, 0) + amount'
  }
];

document.addEventListener('DOMContentLoaded', function() {
  initializeTaskPage();
});

function initializeTaskPage() {
  const addTestCaseButton = document.getElementById('addTestCaseButton');
  if (addTestCaseButton) {
    addTestCaseButton.addEventListener('click', addTestCaseRow);
  }

  const addHintButton = document.getElementById('addHintButton');
  if (addHintButton) {
    addHintButton.addEventListener('click', addHintRow);
  }

  const openHintLibraryButton = document.getElementById('openHintLibraryButton');
  if (openHintLibraryButton) {
    openHintLibraryButton.addEventListener('click', openHintLibraryModal);
  }

  const importHintsButton = document.getElementById('importHintsButton');
  if (importHintsButton) {
    importHintsButton.addEventListener('click', importSelectedHints);
  }

  const resetFormButton = document.getElementById('resetFormButton');
  if (resetFormButton) {
    resetFormButton.addEventListener('click', function() {
      const ok = window.confirm(isEditingInCreateForm
        ? '編集中の入力内容をリセットします。よろしいですか？'
        : '入力内容をリセットします。よろしいですか？');
      if (!ok) return;
      resetCreateForm();
      if (isEditingInCreateForm) {
        finishCreateFormEditMode();
      }
    });
  }

  const cancelEditButton = document.getElementById('cancelEditButton');
  if (cancelEditButton) {
    cancelEditButton.addEventListener('click', function() {
      const ok = window.confirm('編集を終了して新規作成モードに戻します。よろしいですか？');
      if (!ok) return;
      resetCreateForm();
      finishCreateFormEditMode();
      showToast('編集を終了しました。');
    });
  }

  const saveDraftButton = document.getElementById('saveDraftButton');
  if (saveDraftButton) {
    saveDraftButton.addEventListener('click', function() {
      if (isEditingInCreateForm) {
        saveCreateFormEditResult('下書き');
        return;
      }
      showToast('下書きを保存しました。');
      increaseDraftCount();
    });
  }

  const taskCreateForm = document.getElementById('taskCreateForm');
  if (taskCreateForm) {
    taskCreateForm.addEventListener('submit', function(event) {
      event.preventDefault();
      if (isEditingInCreateForm) {
        saveCreateFormEditResult('公開');
        return;
      }
      showToast('課題を保存・公開しました。');
      increasePublishedCount();
    });
  }

  const refreshTableButton = document.getElementById('refreshTableButton');
  if (refreshTableButton) {
    refreshTableButton.addEventListener('click', refreshUpdatedAt);
  }

  const filterStatus = document.getElementById('filterStatus');
  if (filterStatus) {
    filterStatus.addEventListener('change', applyStatusFilter);
  }

  bindRowActionButtons();
  bindPreviewEvents();
  renderHintLibrary();
  updateSchoolDropdownLabel();
  updateClassDropdownLabel();

  addTestCaseRow();
  addTestCaseRow();
  addHintRow();
  updatePreview();
}

function bindRowActionButtons() {
  document.querySelectorAll('.edit-button').forEach(function(button) {
    button.addEventListener('click', function() {
      const row = button.closest('tr');
      if (!row) return;
      startCreateFormEditMode(row);
    });
  });

  document.querySelectorAll('.delete-button').forEach(function(button) {
    button.addEventListener('click', function() {
      const row = button.closest('tr');
      if (!row) return;

      const taskName = row.dataset.name || '選択課題';
      const ok = window.confirm('「' + taskName + '」を論理削除します。よろしいですか？');
      if (!ok) return;

      row.remove();
      showToast('課題を論理削除しました。');
      applyStatusFilter();
    });
  });
}

function bindPreviewEvents() {
  const ids = [
    'taskNameInput',
    'levelSelect',
    'themeInput',
    'taskDescriptionInput',
    'featureInput',
    'taskConstraintInput'
  ];

  ids.forEach(function(id) {
    const el = document.getElementById(id);
    if (!el) return;

    el.addEventListener('input', updatePreview);
    el.addEventListener('change', updatePreview);
  });

  document.querySelectorAll('input[name="classTargets"]').forEach(function(el) {
    el.addEventListener('change', function() {
      updateClassDropdownLabel();
      updatePreview();
    });
  });

  document.querySelectorAll('input[name="schoolTargets"]').forEach(function(el) {
    el.addEventListener('change', function() {
      updateSchoolDropdownLabel();
      updatePreview();
    });
  });
}

function updateSchoolDropdownLabel() {
  const button = document.getElementById('schoolDropdownButton');
  if (!button) return;

  const selected = Array.from(document.querySelectorAll('input[name="schoolTargets"]:checked'))
    .map(function(el) { return el.value; });

  if (selected.length === 0) {
    button.textContent = '学校を選択';
    return;
  }

  if (selected.length <= 2) {
    button.textContent = selected.join(', ');
    return;
  }

  button.textContent = selected.length + '件選択中';
}

function updateClassDropdownLabel() {
  const button = document.getElementById('classDropdownButton');
  if (!button) return;

  const selected = Array.from(document.querySelectorAll('input[name="classTargets"]:checked'))
    .map(function(el) { return el.value; });

  if (selected.length === 0) {
    button.textContent = 'クラスを選択';
    return;
  }

  if (selected.length <= 2) {
    button.textContent = selected.join(', ');
    return;
  }

  button.textContent = selected.length + '件選択中';
}

function updatePreview() {
  const name = getValue('taskNameInput');
  const level = getValue('levelSelect');
  const theme = getValue('themeInput');
  const description = getValue('taskDescriptionInput');
  const features = getValue('featureInput');
  const constraint = getValue('taskConstraintInput');

  setText('previewName', name || '課題名未設定');
  setText('previewLevel', level || '―');
  setText('previewTheme', theme || '―');
  setText('previewDescription', description || '説明未設定');

  const card = document.getElementById('previewCard');
  if (card) {
    card.classList.remove('task-card-beginner', 'task-card-intermediate', 'task-card-advanced');
    if (level === '初級') card.classList.add('task-card-beginner');
    else if (level === '中級') card.classList.add('task-card-intermediate');
    else if (level === '上級') card.classList.add('task-card-advanced');
    else card.classList.add('task-card-beginner');
  }

  // エディター課題情報プレビュー
  setText('previewEditorName', name || '課題名未設定');
  setText('previewEditorDescription', description || '説明未設定');

  const featuresList = document.getElementById('previewEditorFeatures');
  if (featuresList) {
    const items = features ? features.split(';').map(function(s) { return s.trim(); }).filter(Boolean) : [];
    if (items.length > 0) {
      featuresList.innerHTML = items.map(function(s) { return '<li>' + escapeHtml(s) + '</li>'; }).join('');
    } else {
      featuresList.innerHTML = '<li>未設定</li>';
    }
  }

  const constraintEl = document.getElementById('previewEditorConstraint');
  if (constraintEl) {
    constraintEl.textContent = constraint || '未設定';
  }

  const casesEl = document.getElementById('previewEditorCases');
  if (casesEl) {
    const rows = document.querySelectorAll('#testCaseList .test-case-row');
    if (rows.length === 0) {
      casesEl.innerHTML = '<p class="mb-0 text-muted">未設定</p>';
    } else {
      casesEl.innerHTML = Array.from(rows).map(function(row) {
        const inputs = row.querySelectorAll('input');
        const inputVal = inputs[0] ? inputs[0].value.trim() : '';
        const outputVal = inputs[1] ? inputs[1].value.trim() : '';
        return '<div class="case-card">' +
          '<strong>入力:</strong> ' + escapeHtml(inputVal || '(未入力)') + '<br>' +
          '<strong>出力:</strong> ' + escapeHtml(outputVal || '(未入力)') +
          '</div>';
      }).join('');
    }
  }

  const hintCards = document.getElementById('previewHintCards');
  if (hintCards) {
    const hintRows = Array.from(document.querySelectorAll('#hintList .test-case-item'));
    const hintItems = hintRows.map(function(row) {
      const orderEl = row.querySelector('.hint-order');
      const titleEl = row.querySelector('.hint-title');
      const textareas = row.querySelectorAll('textarea');

      const order = orderEl ? Number(orderEl.value) : Number.POSITIVE_INFINITY;
      const title = titleEl ? titleEl.value.trim() : '';
      const content = textareas[0] ? textareas[0].value.trim() : '';
      const code = textareas[1] ? textareas[1].value.trim() : '';

      return {
        order: Number.isFinite(order) ? order : Number.POSITIVE_INFINITY,
        title: title,
        content: content,
        code: code
      };
    }).sort(function(a, b) {
      return a.order - b.order;
    });

    if (hintItems.length === 0) {
      hintCards.innerHTML =
        '<div class="hint-card">' +
          '<div class="info-label">未設定</div>' +
          '<p class="mb-2">ヒント内容未設定</p>' +
          '<pre># コード例未設定</pre>' +
        '</div>';
    } else {
      hintCards.innerHTML = hintItems.map(function(item) {
        return '<div class="hint-card">' +
          '<div class="info-label">' + escapeHtml(item.title || '未設定') + '</div>' +
          '<p class="mb-2">' + escapeHtml(item.content || 'ヒント内容未設定') + '</p>' +
          '<pre>' + escapeHtml(item.code || '# コード例未設定') + '</pre>' +
        '</div>';
      }).join('');
    }
  }
}

function addHintRow() {
  const prefill = arguments.length > 0 ? arguments[0] : null;
  const list = document.getElementById('hintList');
  if (!list) return;

  const index = list.querySelectorAll('.test-case-item').length + 1;
  const row = document.createElement('div');
  row.className = 'test-case-item';

  row.innerHTML =
    '<div class="field-block">' +
      '<label class="mini-label">表示順</label>' +
      '<input class="form-control hint-order" type="number" min="1" value="' + index + '" placeholder="例: 1">' +
    '</div>' +
    '<div class="field-block">' +
      '<label class="mini-label">ヒントタイトル</label>' +
      '<input class="form-control hint-title" type="text" placeholder="例: input()">' +
    '</div>' +
    '<button class="btn btn-sm btn-outline-danger" type="button">削除</button>';

  const wrapper = document.createElement('div');
  wrapper.className = 'w-100';

  const contentLabel = document.createElement('label');
  contentLabel.className = 'mini-label mt-2';
  contentLabel.textContent = 'ヒント内容';

  const content = document.createElement('textarea');
  content.className = 'form-control mt-2';
  content.placeholder = '例: 文字列を受け取るための関数です。';
  content.rows = 2;

  const codeLabel = document.createElement('label');
  codeLabel.className = 'mini-label mt-2';
  codeLabel.textContent = 'コード例';

  const code = document.createElement('textarea');
  code.className = 'form-control mt-2 code-editor-input';
  code.placeholder = '例: player = input("手を入力してください: ")';
  code.rows = 2;

  wrapper.appendChild(contentLabel);
  wrapper.appendChild(content);
  wrapper.appendChild(codeLabel);
  wrapper.appendChild(code);
  row.appendChild(wrapper);

  if (prefill) {
    const orderInput = row.querySelector('.hint-order');
    const titleInput = row.querySelector('.hint-title');

    if (orderInput) {
      orderInput.value = String(prefill.hintOrder || index);
    }
    if (titleInput) {
      titleInput.value = prefill.hintTitle || '';
    }

    content.value = prefill.hintContent || '';
    code.value = prefill.hintCodeExample || '';
  }

  const deleteButton = row.querySelector('button');
  if (deleteButton) {
    deleteButton.addEventListener('click', function() {
      row.remove();
      updatePreview();
    });
  }

  const liveInputs = [
    row.querySelector('.hint-order'),
    row.querySelector('.hint-title'),
    content,
    code
  ];

  liveInputs.forEach(function(el) {
    if (!el) return;
    el.addEventListener('input', updatePreview);
    el.addEventListener('change', updatePreview);
  });

  list.appendChild(row);
  updatePreview();
}

function renderHintLibrary() {
  const body = document.getElementById('hintLibraryBody');
  if (!body) return;

  body.innerHTML = '';

  reusableHintLibrary.forEach(function(hint, idx) {
    const tr = document.createElement('tr');
    tr.innerHTML =
      '<td><input class="form-check-input hint-library-check" type="checkbox" data-index="' + idx + '"></td>' +
      '<td><span class="badge text-bg-light">' + escapeHtml(hint.taskId) + '</span></td>' +
      '<td>' + String(hint.hintOrder) + '</td>' +
      '<td>' + escapeHtml(hint.hintTitle) + '</td>' +
      '<td>' + escapeHtml(hint.hintContent) + '</td>' +
      '<td><pre class="hint-library-code">' + escapeHtml(hint.hintCodeExample) + '</pre></td>';
    body.appendChild(tr);
  });
}

function openHintLibraryModal() {
  const modalEl = document.getElementById('hintLibraryModal');
  if (!modalEl) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function importSelectedHints() {
  const checked = Array.from(document.querySelectorAll('.hint-library-check:checked'));

  if (checked.length === 0) {
    showToast('追加するヒントを選択してください。');
    return;
  }

  checked.forEach(function(check) {
    const idx = Number(check.getAttribute('data-index'));
    const hint = reusableHintLibrary[idx];
    if (hint) {
      addHintRow(hint);
    }
    check.checked = false;
  });

  const modalEl = document.getElementById('hintLibraryModal');
  if (modalEl) {
    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    modal.hide();
  }

  showToast('選択したヒントを追加しました。');
}

function addTestCaseRow() {
  const prefill = arguments.length > 0 ? arguments[0] : null;
  const list = document.getElementById('testCaseList');
  if (!list) return;

  const index = list.querySelectorAll('.test-case-row').length + 1;
  const row = document.createElement('div');
  row.className = 'test-case-row';

  row.innerHTML =
    '<div class="test-case-head">テストケース' + index + '</div>' +
    '<div class="field-block">' +
      '<label class="mini-label">入力値</label>' +
      '<input class="form-control" type="text" placeholder="例: パー">' +
    '</div>' +
    '<div class="field-block">' +
      '<label class="mini-label">期待出力</label>' +
      '<input class="form-control" type="text" placeholder="例: あなたの勝ち">' +
    '</div>' +
    '<button class="btn btn-sm btn-outline-danger" type="button">削除</button>';

  const deleteButton = row.querySelector('button');
  if (deleteButton) {
    deleteButton.addEventListener('click', function() {
      row.remove();
      renumberTestCases();
      updatePreview();
    });
  }

  list.appendChild(row);

  // テストケース入力変更時にプレビュー更新
  row.querySelectorAll('input').forEach(function(input) {
    input.addEventListener('input', updatePreview);
  });

  if (prefill) {
    const inputs = row.querySelectorAll('input');
    if (inputs[0]) inputs[0].value = prefill.input || '';
    if (inputs[1]) inputs[1].value = prefill.output || '';
  }

  updatePreview();
}

function renumberTestCases() {
  document.querySelectorAll('#testCaseList .test-case-row .test-case-head').forEach(function(head, index) {
    head.textContent = 'テストケース' + String(index + 1);
  });
}

function resetCreateForm() {
  const form = document.getElementById('taskCreateForm');
  if (!form) return;

  form.reset();

  const testCaseList = document.getElementById('testCaseList');
  if (testCaseList) {
    testCaseList.innerHTML = '';
    addTestCaseRow();
    addTestCaseRow();
  }

  const list = document.getElementById('hintList');
  if (list) {
    list.innerHTML = '';
    addHintRow();
  }

  updateClassDropdownLabel();
  updateSchoolDropdownLabel();
  updatePreview();
}

function startCreateFormEditMode(row) {
  editTargetRow = row;
  isEditingInCreateForm = true;

  const target = (row.dataset.target || '').split('/').map(function(item) {
    return item.trim();
  }).filter(Boolean);
  const schoolNames = target.length > 0
    ? target[0].split(',').map(function(item) { return item.trim(); }).filter(Boolean)
    : [];
  const classNames = target.length > 1
    ? target.slice(1).join('/').split(',').map(function(item) { return item.trim(); }).filter(Boolean)
    : [];

  setValue('levelSelect', row.dataset.level || '');
  setValue('taskNameInput', row.dataset.name || '');
  setValue('themeInput', row.dataset.theme || '');
  setValue('taskDescriptionInput', row.dataset.description || '');
  setValue('featureInput', row.dataset.features || '');
  setValue('taskConstraintInput', row.dataset.constraint || '');
  setValue('initialCodeInput', row.dataset.initialCode || '');
  setValue('publishDateInput', row.dataset.publishDate || '');

  document.querySelectorAll('input[name="classTargets"]').forEach(function(el) {
    el.checked = classNames.includes(el.value);
  });

  document.querySelectorAll('input[name="schoolTargets"]').forEach(function(el) {
    el.checked = schoolNames.includes(el.value);
  });

  const testCases = parseJsonArray(row.dataset.testCases);
  const testCaseList = document.getElementById('testCaseList');
  if (testCaseList) {
    testCaseList.innerHTML = '';
    if (testCases.length > 0) {
      testCases.forEach(function(tc) {
        addTestCaseRow(tc);
      });
    } else {
      addTestCaseRow();
      addTestCaseRow();
    }
  }

  const hints = parseJsonArray(row.dataset.hints);
  const hintList = document.getElementById('hintList');
  if (hintList) {
    hintList.innerHTML = '';
    if (hints.length > 0) {
      hints.forEach(function(hint) {
        addHintRow(hint);
      });
    } else {
      addHintRow();
    }
  }

  applyCreateFormEditModeUi();
  updateSchoolDropdownLabel();
  updateClassDropdownLabel();
  updatePreview();

  const form = document.getElementById('taskCreateForm');
  if (form) {
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  showToast('作成フォームに編集内容を読み込みました。');
}

function finishCreateFormEditMode() {
  editTargetRow = null;
  isEditingInCreateForm = false;
  applyCreateFormEditModeUi();
}

function applyCreateFormEditModeUi() {
  const heading = document.getElementById('createSectionHeading');
  if (heading) {
    heading.textContent = isEditingInCreateForm ? '既存課題の編集' : '新規課題の作成';
  }

  const publishTaskButton = document.getElementById('publishTaskButton');
  if (publishTaskButton) {
    publishTaskButton.textContent = isEditingInCreateForm ? '編集内容を保存（公開）' : '保存・公開';
  }

  const saveDraftButton = document.getElementById('saveDraftButton');
  if (saveDraftButton) {
    saveDraftButton.textContent = isEditingInCreateForm ? '編集内容を保存（下書き）' : '下書き保存';
  }

  const cancelEditButton = document.getElementById('cancelEditButton');
  if (cancelEditButton) {
    cancelEditButton.classList.toggle('d-none', !isEditingInCreateForm);
  }
}

function saveCreateFormEditResult(status) {
  if (!editTargetRow) return;

  const schoolNames = Array.from(document.querySelectorAll('input[name="schoolTargets"]:checked'))
    .map(function(el) { return el.value; });
  const classNames = Array.from(document.querySelectorAll('input[name="classTargets"]:checked'))
    .map(function(el) { return el.value; });
  const level = getValue('levelSelect');
  const name = getValue('taskNameInput');
  const theme = getValue('themeInput');
  const description = getValue('taskDescriptionInput');
  const features = getValue('featureInput');
  const constraint = getValue('taskConstraintInput');
  const initialCode = getValue('initialCodeInput');
  const publishDate = getValue('publishDateInput');
  const updated = buildNowString();

  const testCases = Array.from(document.querySelectorAll('#testCaseList .test-case-row')).map(function(row) {
    const inputs = row.querySelectorAll('input');
    return {
      input: inputs[0] ? inputs[0].value.trim() : '',
      output: inputs[1] ? inputs[1].value.trim() : ''
    };
  }).filter(function(tc) {
    return tc.input || tc.output;
  });

  const hints = Array.from(document.querySelectorAll('#hintList .test-case-item')).map(function(row) {
    const order = row.querySelector('.hint-order');
    const title = row.querySelector('.hint-title');
    const textareas = row.querySelectorAll('textarea');
    return {
      hintOrder: order ? Number(order.value) || 1 : 1,
      hintTitle: title ? title.value.trim() : '',
      hintContent: textareas[0] ? textareas[0].value.trim() : '',
      hintCodeExample: textareas[1] ? textareas[1].value.trim() : ''
    };
  }).filter(function(hint) {
    return hint.hintTitle || hint.hintContent || hint.hintCodeExample;
  });

  const target = (schoolNames.length > 0 ? schoolNames.join(', ') : '学校未選択') +
    ' / ' + (classNames.length > 0 ? classNames.join(', ') : 'クラス未選択');

  editTargetRow.dataset.name = name;
  editTargetRow.dataset.level = level;
  editTargetRow.dataset.target = target;
  editTargetRow.dataset.status = status;
  editTargetRow.dataset.description = description;
  editTargetRow.dataset.constraint = constraint;
  editTargetRow.dataset.updated = updated;
  editTargetRow.dataset.theme = theme;
  editTargetRow.dataset.features = features;
  editTargetRow.dataset.initialCode = initialCode;
  editTargetRow.dataset.publishDate = publishDate;
  editTargetRow.dataset.testCases = JSON.stringify(testCases);
  editTargetRow.dataset.hints = JSON.stringify(hints);

  if (editTargetRow.cells[0]) {
    editTargetRow.cells[0].textContent = name || '課題名未設定';
  }

  if (editTargetRow.cells[1]) {
    editTargetRow.cells[1].innerHTML = buildLevelBadgeHtml(level);
  }

  if (editTargetRow.cells[2]) {
    editTargetRow.cells[2].textContent = target;
  }

  if (editTargetRow.cells[3]) {
    editTargetRow.cells[3].innerHTML = status === '公開'
      ? '<span class="badge text-bg-success">公開</span>'
      : '<span class="badge text-bg-secondary">下書き</span>';
  }

  const updatedAt = editTargetRow.querySelector('.updated-at');
  if (updatedAt) {
    updatedAt.textContent = updated;
  }

  showToast('課題を更新しました。');
  applyStatusFilter();
  resetCreateForm();
  finishCreateFormEditMode();
}

function buildLevelBadgeHtml(level) {
  if (level === '中級') {
    return '<span class="badge difficulty-intermediate">中級</span>';
  }
  if (level === '上級') {
    return '<span class="badge difficulty-advanced">上級</span>';
  }
  return '<span class="badge difficulty-beginner">初級</span>';
}

function parseJsonArray(text) {
  if (!text) return [];
  try {
    const value = JSON.parse(text);
    return Array.isArray(value) ? value : [];
  } catch (e) {
    return [];
  }
}

function applyStatusFilter() {
  const status = getValue('filterStatus') || '';

  document.querySelectorAll('#taskTable tbody tr').forEach(function(row) {
    const rowStatus = row.dataset.status || '';
    row.style.display = (!status || status === rowStatus) ? '' : 'none';
  });
}

function refreshUpdatedAt() {
  const rows = document.querySelectorAll('#taskTable tbody tr');
  const now = buildNowString();

  rows.forEach(function(row) {
    if ((row.dataset.status || '') === '下書き') {
      row.dataset.updated = now;
      const updatedAt = row.querySelector('.updated-at');
      if (updatedAt) {
        updatedAt.textContent = now;
      }
    }
  });

  showToast('一覧を更新しました。');
}

function showToast(message) {
  setText('taskToastBody', message);

  const toastEl = document.getElementById('taskToast');
  if (!toastEl) return;

  const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 2000 });
  toast.show();
}

function increasePublishedCount() {
  const el = document.getElementById('publishedCount');
  if (!el) return;

  const current = Number(el.textContent) || 0;
  el.textContent = String(current + 1);
}

function increaseDraftCount() {
  const el = document.getElementById('draftCount');
  if (!el) return;

  const current = Number(el.textContent) || 0;
  el.textContent = String(current + 1);
}

function buildNowString() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const hh = String(now.getHours()).padStart(2, '0');
  const mi = String(now.getMinutes()).padStart(2, '0');
  const ss = String(now.getSeconds()).padStart(2, '0');
  return yyyy + '-' + mm + '-' + dd + ' ' + hh + ':' + mi + ':' + ss;
}

function getValue(id) {
  const el = document.getElementById(id);
  return el ? el.value.trim() : '';
}

function setValue(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.value = value;
  }
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = value;
  }
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
