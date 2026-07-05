// Teacher task page interactions

let editTargetRow = null;
let isEditingInCreateForm = false;
let previewIoEditors = [];
let initialCodeEditor = null;
const feedback = window.PPEFeedback || {};
const pageFeedback = feedback.createPageFeedback({ title: '課題編集' });
const CURRENT_TEACHER_ID = 'toida'; // プロトタイプ用プレースホルダー。本実装ではセッション情報から取得する。

function syncCompactEditorHeight(editor) {
  if (!editor) {
    return;
  }

  const lineCount = Math.max(editor.lineCount(), 1);
  const lineHeight = editor.defaultTextHeight();
  const verticalPadding = 16;
  const borderSize = 2;
  editor.setSize(null, (lineCount * lineHeight) + verticalPadding + borderSize);
}

const reusableHintLibrary = [
  {
    taskId: 'TASK-001',
    hintOrder: 1,
    hintTitle: 'input()',
    hintContent: '文字列を受け取るための関数です。'
  },
  {
    taskId: 'TASK-001',
    hintOrder: 2,
    hintTitle: 'if / elif / else',
    hintContent: '条件によって処理を分岐する構文です。'
  },
  {
    taskId: 'TASK-002',
    hintOrder: 1,
    hintTitle: 'for 文',
    hintContent: '複数の値を順に処理する繰り返し構文です。'
  },
  {
    taskId: 'TASK-003',
    hintOrder: 1,
    hintTitle: '辞書の集計',
    hintContent: 'キーごとに値を合計して分析できます。'
  }
];

const teacherAudit = window.PPETeacherAudit || null;
let taskAuditDetailModal = null;

document.addEventListener('DOMContentLoaded', function() {
  initializeTaskPage();
});

function initializeTaskPage() {
  if (typeof bootstrap !== 'undefined') {
    const taskAuditModalElement = document.getElementById('taskAuditDetailModal');
    if (taskAuditModalElement) {
      taskAuditDetailModal = bootstrap.Modal.getOrCreateInstance(taskAuditModalElement);
    }
  }

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
    resetFormButton.addEventListener('click', async function() {
      const ok = await pageFeedback.confirm({
        title: isEditingInCreateForm ? '入力内容をリセットしますか？' : '作成内容をリセットしますか？',
        message: '次の入力内容をリセットします。',
        detailTitle: '',
        details: isEditingInCreateForm
          ? ['編集中の課題情報', '追加したテストケースとヒント']
          : ['現在フォームに入力している課題情報', '追加したテストケースとヒント'],
        confirmLabel: 'リセットする',
        cancelLabel: '戻る',
        variant: 'warning'
      });
      if (!ok) return;
      resetCreateForm();
      if (isEditingInCreateForm) {
        finishCreateFormEditMode();
      }
    });
  }

  const cancelEditButton = document.getElementById('cancelEditButton');
  if (cancelEditButton) {
    cancelEditButton.addEventListener('click', async function() {
      const ok = await pageFeedback.confirm({
        title: '編集を終了しますか？',
        message: '次の入力内容をリセットします。',
        detailTitle: '',
        details: ['編集中の未保存の課題情報', '新規作成フォームに移る前の編集状態'],
        confirmLabel: '終了する',
        cancelLabel: '戻る',
        variant: 'warning'
      });
      if (!ok) return;
      resetCreateForm();
      finishCreateFormEditMode();
      pageFeedback.toast({ message: '編集を終了しました。', variant: 'success', delay: 2200 });
    });
  }

  const saveDraftButton = document.getElementById('saveDraftButton');
  if (saveDraftButton) {
    saveDraftButton.addEventListener('click', function() {
      if (isEditingInCreateForm) {
        saveCreateFormEditResult('下書き');
        return;
      }
      createTaskRowFromForm('下書き');
      pageFeedback.toast({ message: '下書きを保存しました。', variant: 'success', delay: 2200 });
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
      createTaskRowFromForm('公開');
      pageFeedback.toast({ message: '課題を保存・公開しました。', variant: 'success', delay: 2200 });
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
  refreshClassScheduleRows();
  syncPromptStatusHighlight();

  initializeInitialCodeEditor();

  addTestCaseRow();
  addTestCaseRow();
  addHintRow();
  updatePreview();
}

function initializeCodeMirrorTextarea(textarea, options) {
  if (!textarea || typeof CodeMirror === 'undefined') {
    return null;
  }

  if (textarea._cmEditor) {
    return textarea._cmEditor;
  }

  const editor = CodeMirror.fromTextArea(textarea, options);
  textarea._cmEditor = editor;
  return editor;
}

function initializeInitialCodeEditor() {
  const textarea = document.getElementById('initialCodeInput');
  initialCodeEditor = initializeCodeMirrorTextarea(textarea, {
    mode: 'python',
    lineNumbers: true,
    lineWrapping: true,
    theme: 'material-darker',
    indentUnit: 4,
    tabSize: 4,
    viewportMargin: Infinity
  });

  if (initialCodeEditor) {
    initialCodeEditor.getWrapperElement().classList.add('task-form-code-mirror', 'is-python');
    initialCodeEditor.on('change', updatePreview);
  }
}

function initializeShellEditor(textarea) {
  const editor = initializeCodeMirrorTextarea(textarea, {
    mode: 'shell',
    lineNumbers: false,
    lineWrapping: true,
    theme: 'material-darker',
    viewportMargin: Infinity
  });

  if (editor) {
    editor.getWrapperElement().classList.add('task-form-code-mirror', 'is-shell');
    syncCompactEditorHeight(editor);
    editor.on('change', function(instance) {
      syncCompactEditorHeight(instance);
      updatePreview();
    });
  }

  return editor;
}

function getElementValue(element) {
  if (!element) {
    return '';
  }

  if (element._cmEditor) {
    return element._cmEditor.getValue().trim();
  }

  return typeof element.value === 'string' ? element.value.trim() : '';
}

function bindRowActionButtons() {
  document.querySelectorAll('#taskTable tbody tr').forEach(function(row) {
    bindTaskRowActionButtons(row);
  });
}

function bindTaskRowActionButtons(row) {
  if (!row || row.dataset.historyBound === '1') {
    return;
  }

  const editButton = row.querySelector('.edit-button');
  if (editButton) {
    editButton.addEventListener('click', function() {
      startCreateFormEditMode(row);
    });
  }

  const duplicateButton = row.querySelector('.duplicate-button');
  if (duplicateButton) {
    duplicateButton.addEventListener('click', function() {
      duplicateTaskRow(row);
    });
  }

  const deleteButton = row.querySelector('.delete-button');
  if (deleteButton) {
    deleteButton.addEventListener('click', async function() {
      const taskId = row.dataset.taskId || 'TASK-UNKNOWN';

      const taskName = row.dataset.name || '選択課題';
      const ok = await pageFeedback.confirm({
        title: '課題を削除しますか？',
        message: '次のデータを削除します。',
        detailTitle: '',
        details: ['課題名: ' + taskName, '課題一覧への表示状態'],
        confirmLabel: '削除する',
        cancelLabel: '戻る',
        variant: 'danger'
      });
      if (!ok) return;

      recordTaskAudit('削除', taskId, '課題を論理削除: ' + taskName);

      row.remove();
      pageFeedback.toast({ message: '課題を論理削除しました。', variant: 'success', delay: 2200 });
      applyStatusFilter();
    });
  }

  const historyButton = row.querySelector('.history-button');
  if (historyButton) {
    historyButton.addEventListener('click', function() {
      openTaskAuditDetail(row);
    });
  }

  row.dataset.historyBound = '1';
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
      refreshClassScheduleRows();
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

function initializePreviewCodeMirrors() {
  if (typeof CodeMirror === 'undefined') {
    return;
  }

  previewIoEditors.forEach(function(editor) {
    editor.toTextArea();
  });
  previewIoEditors = [];

  document.querySelectorAll('.preview-io-source').forEach(function(textarea) {
    const editor = CodeMirror.fromTextArea(textarea, {
      mode: 'shell',
      lineNumbers: false,
      lineWrapping: true,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker'
    });
    previewIoEditors.push(editor);
  });

  previewIoEditors.forEach(function(editor) { editor.refresh(); });
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

function getSelectedClassNames() {
  return Array.from(document.querySelectorAll('input[name="classTargets"]:checked'))
    .map(function(el) { return el.value; });
}

function collectClassSchedules() {
  return Array.from(document.querySelectorAll('#classScheduleList .class-schedule-row')).map(function(row) {
    const className = row.getAttribute('data-class-name') || '';
    const publishImmediate = !!(row.querySelector('.class-schedule-publish-immediate') && row.querySelector('.class-schedule-publish-immediate').checked);
    const dueNone = !!(row.querySelector('.class-schedule-deadline-none') && row.querySelector('.class-schedule-deadline-none').checked);
    const publishAt = publishImmediate ? '' : getElementValue(row.querySelector('.class-schedule-publish'));
    const dueAt = dueNone ? '' : getElementValue(row.querySelector('.class-schedule-deadline'));

    return {
      className: className,
      publishImmediate: publishImmediate,
      publishAt: publishAt,
      dueNone: dueNone,
      dueAt: dueAt
    };
  });
}

function toClassScheduleMap(schedules) {
  return (Array.isArray(schedules) ? schedules : []).reduce(function(map, item) {
    if (!item || !item.className) {
      return map;
    }

    map[item.className] = {
      publishImmediate: item.publishImmediate === true || item.publishMode === 'immediate',
      publishAt: item.publishAt || '',
      dueNone: item.dueNone === true,
      dueAt: item.dueAt || ''
    };
    return map;
  }, {});
}

function refreshClassScheduleRows(prefillSchedules) {
  const container = document.getElementById('classScheduleList');
  if (!container) {
    return;
  }

  const selectedClassNames = getSelectedClassNames();
  const currentMap = toClassScheduleMap(collectClassSchedules());
  const prefillMap = toClassScheduleMap(prefillSchedules);

  container.innerHTML = '';

  if (selectedClassNames.length === 0) {
    container.innerHTML = '<p class="class-schedule-empty mb-0 text-muted">クラスを選択すると、公開日時（即時公開可）と提出期限を設定できます。</p>';
    return;
  }

  selectedClassNames.forEach(function(className) {
    const seed = prefillMap[className] || currentMap[className] || { publishImmediate: false, publishAt: '', dueNone: false, dueAt: '' };
    const row = document.createElement('div');
    row.className = 'class-schedule-row';
    row.setAttribute('data-class-name', className);
    row.innerHTML =
      '<div class="class-schedule-name">' + escapeHtml(className) + '</div>' +
      '<div class="class-schedule-field">' +
        '<label class="mini-label" for="schedulePublish-' + escapeHtml(className) + '">公開日時</label>' +
        '<input id="schedulePublish-' + escapeHtml(className) + '" class="form-control class-schedule-publish" type="datetime-local" value="' + escapeHtml(seed.publishAt) + '">' +
        '<div class="form-check mt-2">' +
          '<input class="form-check-input class-schedule-publish-immediate" type="checkbox" id="schedulePublishImmediate-' + escapeHtml(className) + '"' + (seed.publishImmediate ? ' checked' : '') + '>' +
          '<label class="form-check-label" for="schedulePublishImmediate-' + escapeHtml(className) + '">即時公開</label>' +
        '</div>' +
      '</div>' +
      '<div class="class-schedule-field">' +
        '<label class="mini-label" for="scheduleDeadline-' + escapeHtml(className) + '">提出期限</label>' +
        '<input id="scheduleDeadline-' + escapeHtml(className) + '" class="form-control class-schedule-deadline" type="datetime-local" value="' + escapeHtml(seed.dueAt) + '">' +
        '<div class="form-check mt-2">' +
          '<input class="form-check-input class-schedule-deadline-none" type="checkbox" id="scheduleDeadlineNone-' + escapeHtml(className) + '"' + (seed.dueNone ? ' checked' : '') + '>' +
          '<label class="form-check-label" for="scheduleDeadlineNone-' + escapeHtml(className) + '">提出期限なし</label>' +
        '</div>' +
      '</div>';
    container.appendChild(row);

    const publishImmediateEl = row.querySelector('.class-schedule-publish-immediate');
    const publishAtEl = row.querySelector('.class-schedule-publish');
    if (publishImmediateEl && publishAtEl) {
      const applyPublishMode = function() {
        const immediate = publishImmediateEl.checked;
        publishAtEl.disabled = immediate;
        if (immediate) {
          publishAtEl.value = '';
        }
      };
      publishImmediateEl.addEventListener('change', applyPublishMode);
      applyPublishMode();
    }

    const dueNoneEl = row.querySelector('.class-schedule-deadline-none');
    const dueAtEl = row.querySelector('.class-schedule-deadline');
    if (dueNoneEl && dueAtEl) {
      const applyDueMode = function() {
        const noDeadline = dueNoneEl.checked;
        dueAtEl.disabled = noDeadline;
        if (noDeadline) {
          dueAtEl.value = '';
        }
      };
      dueNoneEl.addEventListener('change', applyDueMode);
      applyDueMode();
    }
  });
}

function updatePreview() {
  const name = getValue('taskNameInput');
  const level = normalizeLevelSelection(getValue('levelSelect'));
  const theme = getValue('themeInput');
  const description = getValue('taskDescriptionInput');
  const features = getValue('featureInput');
  const constraint = getValue('taskConstraintInput');

  setText('previewName', name || '課題名未設定');
  const previewLevel = document.getElementById('previewLevel');
  if (previewLevel) {
    if (level) {
      previewLevel.textContent = level;
      previewLevel.classList.remove('d-none');
    } else {
      previewLevel.textContent = '';
      previewLevel.classList.add('d-none');
    }
  }
  setText('previewTheme', theme || '―');
  setText('previewDescription', description || '説明未設定');

  const card = document.getElementById('previewCard');
  if (card) {
    card.classList.remove('task-card-beginner', 'task-card-intermediate', 'task-card-advanced');
    if (level === '初級') card.classList.add('task-card-beginner');
    else if (level === '中級') card.classList.add('task-card-intermediate');
    else if (level === '上級') card.classList.add('task-card-advanced');
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
        const inputVal = getElementValue(row.querySelector('.test-case-input'));
        const outputVal = getElementValue(row.querySelector('.test-case-output'));
        return '<div class="case-card io-case-card">' +
          '<div class="io-case-label">入力</div>' +
          '<textarea class="preview-io-source" spellcheck="false">' + escapeHtml(inputVal || '(未入力)') + '</textarea>' +
          '<div class="io-case-label mt-3">出力</div>' +
          '<textarea class="preview-io-source" spellcheck="false">' + escapeHtml(outputVal || '(未入力)') + '</textarea>' +
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

      return {
        order: Number.isFinite(order) ? order : Number.POSITIVE_INFINITY,
        title: title,
        content: content
      };
    }).sort(function(a, b) {
      return a.order - b.order;
    });

    if (hintItems.length === 0) {
      hintCards.innerHTML =
        '<div class="hint-card">' +
          '<div class="info-label">未設定</div>' +
          '<p>ヒント内容未設定</p>' +
        '</div>';
    } else {
      hintCards.innerHTML = hintItems.map(function(item) {
        return '<div class="hint-card">' +
          '<div class="info-label">' + escapeHtml(item.title || '未設定') + '</div>' +
          '<p>' + escapeHtml(item.content || 'ヒント内容未設定') + '</p>' +
        '</div>';
      }).join('');
    }
  }

  initializePreviewCodeMirrors();
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

  wrapper.appendChild(contentLabel);
  wrapper.appendChild(content);
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
    content
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
      '<td>' + escapeHtml(hint.hintContent) + '</td>';
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
    pageFeedback.toast({ message: '追加するヒントを選択してください。', variant: 'warning', delay: 2200 });
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

  pageFeedback.toast({ message: '選択したヒントを追加しました。', variant: 'success', delay: 2200 });
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
      '<textarea class="form-control shell-editor-input test-case-input" rows="1" placeholder="例: パー"></textarea>' +
    '</div>' +
    '<div class="field-block">' +
      '<label class="mini-label">期待出力</label>' +
      '<textarea class="form-control shell-editor-input test-case-output" rows="1" placeholder="例: あなたの勝ち"></textarea>' +
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

  const inputTextarea = row.querySelector('.test-case-input');
  const outputTextarea = row.querySelector('.test-case-output');

  if (inputTextarea) {
    initializeShellEditor(inputTextarea);
  }

  if (outputTextarea) {
    initializeShellEditor(outputTextarea);
  }

  if (prefill) {
    if (inputTextarea) {
      if (inputTextarea._cmEditor) inputTextarea._cmEditor.setValue(prefill.input || '');
      else inputTextarea.value = prefill.input || '';
    }
    if (outputTextarea) {
      if (outputTextarea._cmEditor) outputTextarea._cmEditor.setValue(prefill.output || '');
      else outputTextarea.value = prefill.output || '';
    }
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
  setValue('initialCodeInput', '');

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
  refreshClassScheduleRows();
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
  setValue('lateSubmissionPolicy', row.dataset.lateSubmissionPolicy || '');

  document.querySelectorAll('input[name="classTargets"]').forEach(function(el) {
    el.checked = classNames.includes(el.value);
  });

  document.querySelectorAll('input[name="schoolTargets"]').forEach(function(el) {
    el.checked = schoolNames.includes(el.value);
  });

  refreshClassScheduleRows(parseJsonArray(row.dataset.classSchedules));

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

  pageFeedback.toast({ message: '作成フォームに編集内容を読み込みました。', variant: 'success', delay: 2200 });
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

  const taskId = editTargetRow.dataset.taskId || 'TASK-UNKNOWN';

  const schoolNames = Array.from(document.querySelectorAll('input[name="schoolTargets"]:checked'))
    .map(function(el) { return el.value; });
  const classNames = Array.from(document.querySelectorAll('input[name="classTargets"]:checked'))
    .map(function(el) { return el.value; });
  const level = getValue('levelSelect');
  const normalizedLevel = normalizeLevelSelection(level);
  const name = getValue('taskNameInput');
  const theme = getValue('themeInput');
  const description = getValue('taskDescriptionInput');
  const features = getValue('featureInput');
  const constraint = getValue('taskConstraintInput');
  const initialCode = getValue('initialCodeInput');
  const classSchedules = collectClassSchedules();
  const lateSubmissionPolicy = getValue('lateSubmissionPolicy');
  const updated = buildNowString();

  const testCases = Array.from(document.querySelectorAll('#testCaseList .test-case-row')).map(function(row) {
    return {
      input: getElementValue(row.querySelector('.test-case-input')),
      output: getElementValue(row.querySelector('.test-case-output'))
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
      hintContent: textareas[0] ? getElementValue(textareas[0]) : ''
    };
  }).filter(function(hint) {
    return hint.hintTitle || hint.hintContent;
  });

  const target = (schoolNames.length > 0 ? schoolNames.join(', ') : '学校未選択') +
    ' / ' + (classNames.length > 0 ? classNames.join(', ') : 'クラス未選択');

  editTargetRow.dataset.name = name;
  editTargetRow.dataset.level = normalizedLevel;
  editTargetRow.dataset.target = target;
  editTargetRow.dataset.status = status;
  editTargetRow.dataset.description = description;
  editTargetRow.dataset.constraint = constraint;
  editTargetRow.dataset.updated = updated;
  editTargetRow.dataset.theme = theme;
  editTargetRow.dataset.features = features;
  editTargetRow.dataset.initialCode = initialCode;
  editTargetRow.dataset.classSchedules = JSON.stringify(classSchedules);
  editTargetRow.dataset.lateSubmissionPolicy = lateSubmissionPolicy;
  editTargetRow.dataset.testCases = JSON.stringify(testCases);
  editTargetRow.dataset.hints = JSON.stringify(hints);

  if (editTargetRow.cells[0]) {
    editTargetRow.cells[0].textContent = name || '課題名未設定';
  }

  if (editTargetRow.cells[1]) {
    editTargetRow.cells[1].innerHTML = buildLevelBadgeHtml(normalizedLevel);
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

  recordTaskAudit('編集', taskId, '課題を更新: ' + (name || '課題名未設定') + ' / 状態: ' + status);

  pageFeedback.toast({ message: '課題を更新しました。', variant: 'success', delay: 2200 });
  applyStatusFilter();
  resetCreateForm();
  finishCreateFormEditMode();
}

function createTaskRowFromForm(status) {
  const name = getValue('taskNameInput');
  if (!name) {
    pageFeedback.toast({ message: '課題名を入力してください。', variant: 'warning', delay: 2200 });
    return;
  }

  const schoolNames = Array.from(document.querySelectorAll('input[name="schoolTargets"]:checked'))
    .map(function(el) { return el.value; });
  const classNames = Array.from(document.querySelectorAll('input[name="classTargets"]:checked'))
    .map(function(el) { return el.value; });
  const level = normalizeLevelSelection(getValue('levelSelect'));
  const taskId = buildNextTaskId();
  const updated = buildNowString();
  const creator = getCurrentTaskCreatorId();
  const target = (schoolNames.length > 0 ? schoolNames.join(', ') : '学校未選択')
    + ' / ' + (classNames.length > 0 ? classNames.join(', ') : 'クラス未選択');

  const row = document.createElement('tr');
  row.dataset.taskId = taskId;
  row.dataset.name = name;
  row.dataset.level = level;
  row.dataset.target = target;
  row.dataset.status = status;
  row.dataset.promptStatus = '未設定';
  row.dataset.description = getValue('taskDescriptionInput');
  row.dataset.constraint = getValue('taskConstraintInput');
  row.dataset.updated = updated;
  row.dataset.creator = creator;
  row.dataset.theme = getValue('themeInput');
  row.dataset.features = getValue('featureInput');
  row.dataset.initialCode = getValue('initialCodeInput');
  row.dataset.classSchedules = JSON.stringify(collectClassSchedules());
  row.dataset.lateSubmissionPolicy = getValue('lateSubmissionPolicy');
  row.dataset.testCases = JSON.stringify([]);
  row.dataset.hints = JSON.stringify([]);

  row.innerHTML = ''
    + '<td>' + escapeHtml(name) + '</td>'
    + '<td>' + buildLevelBadgeHtml(level) + '</td>'
    + '<td>' + escapeHtml(target) + '</td>'
    + '<td>' + (status === '公開'
      ? '<span class="badge text-bg-success">公開</span>'
      : '<span class="badge text-bg-secondary">下書き</span>') + '</td>'
    + '<td><a class="prompt-status-link is-unset" href="../prompt/prompt.html?taskId=' + escapeHtml(taskId) + '">未設定</a></td>'
    + '<td class="updated-at">' + escapeHtml(updated) + '</td>'
    + '<td class="task-creator">' + escapeHtml(creator) + '</td>'
    + '<td>'
    +   '<div class="d-flex gap-2">'
    +     '<button class="btn btn-sm btn-outline-primary edit-button" type="button">編集</button>'
    +     '<button class="btn btn-sm btn-outline-secondary duplicate-button" type="button">複製</button>'
    +     '<button class="btn btn-sm btn-outline-danger delete-button" type="button">削除</button>'
    +   '</div>'
    + '</td>'
    + '<td><button class="btn btn-sm btn-outline-primary history-button" type="button">表示</button></td>';

  const tableBody = document.querySelector('#taskTable tbody');
  if (!tableBody) {
    return;
  }

  tableBody.prepend(row);
  bindTaskRowActionButtons(row);
  recordTaskAudit('作成', taskId, '課題を作成: ' + name + ' / 状態: ' + status);
  applyStatusFilter();
  resetCreateForm();
}

function getCurrentTaskCreatorId() {
  if (teacherAudit && typeof teacherAudit.getCurrentTeacherId === 'function') {
    return teacherAudit.getCurrentTeacherId();
  }
  return 't001';
}

function duplicateTaskRow(sourceRow) {
  if (!sourceRow) {
    return;
  }

  const tableBody = document.querySelector('#taskTable tbody');
  if (!tableBody) {
    return;
  }

  const newTaskId = buildNextTaskId();
  const duplicatedName = (sourceRow.dataset.name || '課題') + '（複製）';
  const updated = buildNowString();

  const newRow = sourceRow.cloneNode(true);
  newRow.dataset.historyBound = '0';
  newRow.dataset.taskId = newTaskId;
  newRow.dataset.name = duplicatedName;
  newRow.dataset.status = '下書き';
  newRow.dataset.promptStatus = '未設定';
  newRow.dataset.updated = updated;

  if (newRow.cells[0]) {
    newRow.cells[0].textContent = duplicatedName;
  }
  if (newRow.cells[3]) {
    newRow.cells[3].innerHTML = '<span class="badge text-bg-secondary">下書き</span>';
  }
  if (newRow.cells[4]) {
    newRow.cells[4].innerHTML = '<a class="prompt-status-link is-unset" href="../prompt/prompt.html?taskId=' + escapeHtml(newTaskId) + '">未設定</a>';
  }

  const updatedCell = newRow.querySelector('.updated-at');
  if (updatedCell) {
    updatedCell.textContent = updated;
  }

  tableBody.prepend(newRow);
  bindTaskRowActionButtons(newRow);
  recordTaskAudit('複製', newTaskId, '課題を複製: ' + duplicatedName);
  pageFeedback.toast({ message: '課題を複製しました。', variant: 'success', delay: 2200 });
  applyStatusFilter();
}

function buildNextTaskId() {
  const ids = Array.from(document.querySelectorAll('#taskTable tbody tr')).map(function(row) {
    const match = String(row.dataset.taskId || '').match(/\d+/);
    return match ? Number(match[0]) : 0;
  });
  const next = (ids.length ? Math.max.apply(null, ids) : 0) + 1;
  return 'TASK-' + String(next).padStart(3, '0');
}

function recordTaskAudit(action, taskId, detail) {
  if (!teacherAudit || typeof teacherAudit.record !== 'function') {
    return;
  }
  teacherAudit.record({
    feature: 'task',
    action: action,
    targetType: 'task',
    targetId: taskId || '',
    result: 'SUCCESS',
    detail: detail || ''
  });
}

function openTaskAuditDetail(row) {
  if (!row) {
    return;
  }

  const taskId = row.dataset.taskId || '';
  const taskName = row.dataset.name || taskId || '課題';
  const targetLabel = document.getElementById('taskAuditDetailTarget');
  const detailBody = document.getElementById('taskAuditDetailBody');
  if (!detailBody) {
    return;
  }

  if (targetLabel) {
    targetLabel.textContent = '対象: ' + taskName + '（' + (taskId || 'ID未設定') + '）';
  }

  let events = [];
  if (teacherAudit && typeof teacherAudit.query === 'function') {
    events = teacherAudit.query({ feature: 'task', targetType: 'task', targetId: taskId });
  }

  if (!events.length) {
    detailBody.innerHTML = '<tr><td colspan="5" class="text-muted">この課題の変更履歴はまだありません。</td></tr>';
  } else {
    detailBody.innerHTML = events.map(function(event) {
      return '<tr>'
        + '<td>' + escapeHtml(event.occurredAt || '-') + '</td>'
        + '<td>' + escapeHtml(event.actorTeacherId || '-') + '</td>'
        + '<td>' + escapeHtml(event.action || '-') + '</td>'
        + '<td>' + escapeHtml(event.result || '-') + '</td>'
        + '<td>' + escapeHtml(event.detail || '-') + '</td>'
        + '</tr>';
    }).join('');
  }

  if (taskAuditDetailModal) {
    taskAuditDetailModal.show();
  }
}

function buildLevelBadgeHtml(level) {
  if (!level) {
    return '';
  }
  if (level === '中級') {
    return '<span class="badge difficulty-intermediate">中級</span>';
  }
  if (level === '上級') {
    return '<span class="badge difficulty-advanced">上級</span>';
  }
  return '<span class="badge difficulty-beginner">初級</span>';
}

function normalizeLevelSelection(level) {
  return level === '__none__' ? '' : level;
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

  syncPromptStatusHighlight();
}

function syncPromptStatusHighlight() {
  document.querySelectorAll('#taskTable tbody tr').forEach(function(row) {
    const isPromptUnset = (row.dataset.promptStatus || '') === '未設定';
    row.classList.toggle('prompt-unset-row', isPromptUnset);

    const promptLink = row.querySelector('.prompt-status-link');
    if (promptLink) {
      promptLink.classList.toggle('is-unset', isPromptUnset);
    }
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

  pageFeedback.toast({ message: '一覧を更新しました。', variant: 'success', delay: 2200 });
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
  return getElementValue(el);
}

function setValue(id, value) {
  const el = document.getElementById(id);
  if (el) {
    if (el._cmEditor) {
      el._cmEditor.setValue(value || '');
      el._cmEditor.refresh();
    } else {
      el.value = value;
    }
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

function saveNewTaskRow(status) {
  const schoolNames = Array.from(document.querySelectorAll('input[name="schoolTargets"]:checked'))
    .map(function(el) { return el.value; });
  const classNames = Array.from(document.querySelectorAll('input[name="classTargets"]:checked'))
    .map(function(el) { return el.value; });
  const level = getValue('levelSelect');
  const normalizedLevel = normalizeLevelSelection(level);
  const name = getValue('taskNameInput');
  const theme = getValue('themeInput');
  const description = getValue('taskDescriptionInput');
  const features = getValue('featureInput');
  const constraint = getValue('taskConstraintInput');
  const initialCode = getValue('initialCodeInput');
  const classSchedules = collectClassSchedules();
  const lateSubmissionPolicy = getValue('lateSubmissionPolicy');
  const updated = buildNowString();

  const testCases = Array.from(document.querySelectorAll('#testCaseList .test-case-row')).map(function(row) {
    return {
      input: getElementValue(row.querySelector('.test-case-input')),
      output: getElementValue(row.querySelector('.test-case-output'))
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
      hintContent: textareas[0] ? getElementValue(textareas[0]) : ''
    };
  }).filter(function(hint) {
    return hint.hintTitle || hint.hintContent;
  });

  const target = (schoolNames.length > 0 ? schoolNames.join(', ') : '学校未選択') +
    ' / ' + (classNames.length > 0 ? classNames.join(', ') : 'クラス未選択');
  const creator = CURRENT_TEACHER_ID;
  const taskId = 'TASK-' + String(Date.now()).slice(-6) + '-' + Math.random().toString(36).slice(2, 6);

  const tr = document.createElement('tr');
  tr.setAttribute('data-task-id', taskId);
  tr.setAttribute('data-name', name);
  tr.setAttribute('data-level', normalizedLevel);
  tr.setAttribute('data-target', target);
  tr.setAttribute('data-status', status);
  tr.setAttribute('data-prompt-status', '未設定');
  tr.setAttribute('data-creator', creator);
  tr.setAttribute('data-description', description);
  tr.setAttribute('data-constraint', constraint);
  tr.setAttribute('data-updated', updated);
  tr.setAttribute('data-theme', theme);
  tr.setAttribute('data-features', features);
  tr.setAttribute('data-initial-code', initialCode);
  tr.setAttribute('data-class-schedules', JSON.stringify(classSchedules));
  tr.setAttribute('data-late-submission-policy', lateSubmissionPolicy);
  tr.setAttribute('data-test-cases', JSON.stringify(testCases));
  tr.setAttribute('data-hints', JSON.stringify(hints));

  const statusBadge = status === '公開'
    ? '<span class="badge text-bg-success">公開</span>'
    : '<span class="badge text-bg-secondary">下書き</span>';

  tr.innerHTML =
    '<td>' + escapeHtml(name || '課題名未設定') + '</td>' +
    '<td>' + buildLevelBadgeHtml(normalizedLevel) + '</td>' +
    '<td>' + escapeHtml(target) + '</td>' +
    '<td>' + statusBadge + '</td>' +
    '<td><a class="prompt-status-link is-unset" href="../prompt/prompt.html?taskId=' + escapeHtml(taskId) + '">未設定</a></td>' +
    '<td class="creator-id">' + escapeHtml(creator) + '</td>' +
    '<td class="updated-at">' + escapeHtml(updated) + '</td>' +
    '<td><div class="d-flex gap-2">' +
      '<button class="btn btn-sm btn-outline-primary edit-button" type="button">編集</button>' +
      '<button class="btn btn-sm btn-outline-danger delete-button" type="button">削除</button>' +
    '</div></td>';

  const tbody = document.querySelector('#taskTable tbody');
  if (tbody) {
    tbody.appendChild(tr);
  }

  bindRowActionButtons();
  syncPromptStatusHighlight();
  applyStatusFilter();

  if (status === '公開') {
    increasePublishedCount();
    pageFeedback.toast({ message: '課題を保存・公開しました。', variant: 'success', delay: 2200 });
  } else {
    increaseDraftCount();
    pageFeedback.toast({ message: '下書きを保存しました。', variant: 'success', delay: 2200 });
  }

  resetCreateForm();
}
