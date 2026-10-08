document.addEventListener('DOMContentLoaded', function() {
  const form = document.getElementById('taskCreateForm');
  if (!form) return;

  const feedback = window.PPEFeedback.createPageFeedback({ title: '課題編集' });
  const formTaskId = Number(form.querySelector('[name="taskId"]').value);
  const scheduleValues = new Map();

  document.querySelectorAll('.task-state-form').forEach(function(operationForm) {
    operationForm.addEventListener('submit', async function(event) {
      event.preventDefault();
      const confirmed = await feedback.confirm({
        title: operationForm.dataset.confirmTitle,
        message: operationForm.dataset.confirmMessage,
        confirmLabel: operationForm.dataset.confirmLabel || '実行する',
        cancelLabel: '戻る',
        variant: operationForm.querySelector('[name="action"]').value === 'deleteTask' ? 'danger' : 'primary'
      });
      if (!confirmed) return;
      const button = operationForm.querySelector('button[type="submit"]');
      button.disabled = true;
      HTMLFormElement.prototype.submit.call(operationForm);
    });
  });

  function initializeCodeEditor(textarea, mode) {
    if (!textarea || typeof window.CodeMirror === 'undefined') return null;
    const editor = window.CodeMirror.fromTextArea(textarea, {
      mode: mode,
      lineNumbers: mode === 'python',
      lineWrapping: true,
      theme: 'material-darker',
      indentUnit: 4,
      tabSize: 4,
      viewportMargin: Infinity
    });
    editor.getWrapperElement().classList.add('task-form-code-mirror', mode === 'python' ? 'is-python' : 'is-shell');
    editor.on('change', function() {
      textarea.value = editor.getValue();
      updatePreview();
    });
    return editor;
  }

  function currentValue(name) {
    const field = form.elements.namedItem(name);
    return field && typeof field.value === 'string' ? field.value : '';
  }

  function addTestCaseRow(testCase) {
    const row = document.createElement('div');
    row.className = 'test-case-row';
    const id = document.createElement('input');
    id.type = 'hidden';
    id.name = 'testCaseIds';
    id.value = testCase ? String(testCase.id || 0) : '0';
    row.append(id);
    const title = document.createElement('input');
    title.type = 'hidden';
    title.name = 'testCaseTitles';
    title.value = testCase ? testCase.title || '' : '';
    row.append(title);
    const input = createField('入力', 'textarea', 'testCaseInputs', testCase ? testCase.input : '');
    input.querySelector('textarea').rows = 1;
    input.querySelector('textarea').classList.add('code-editor-input', 'test-case-io');
    row.append(input);
    const output = createField('出力', 'textarea', 'testCaseOutputs', testCase ? testCase.output : '');
    output.querySelector('textarea').rows = 1;
    output.querySelector('textarea').classList.add('code-editor-input', 'test-case-io');
    row.append(output);
    row.append(createRemoveButton('テストケースを削除'));
    document.getElementById('testCaseList').append(row);
    row.querySelectorAll('.test-case-io').forEach(function(textarea) {
      initializeCodeEditor(textarea, 'shell');
    });
    updatePreview();
  }

  function addHintRow(hint) {
    const row = document.createElement('div');
    row.className = 'hint-row';
    const id = document.createElement('input');
    id.type = 'hidden';
    id.name = 'hintIds';
    id.value = '0';
    row.append(id);
    row.append(createField('コマンド名', 'input', 'hintTitles', hint ? hint.title : ''));
    row.append(createField('説明', 'textarea', 'hintContents', hint ? hint.content : ''));
    row.append(createField('使用方法', 'input', 'hintUsageSyntaxes', hint ? hint.syntax : ''));
    const codeField = createField('サンプルコード', 'textarea', 'hintCodes', hint ? hint.code : '');
    codeField.querySelector('textarea').classList.add('hint-code');
    row.append(codeField);
    row.append(createRemoveButton('ヒントを削除'));
    document.getElementById('hintList').append(row);
    initializeCodeEditor(codeField.querySelector('textarea'), 'python');
    updatePreview();
  }

  function createField(labelText, tagName, name, value) {
    const field = document.createElement('div');
    field.className = 'field-block';
    const label = document.createElement('label');
    label.className = 'mini-label';
    label.textContent = labelText;
    const input = document.createElement(tagName);
    input.className = 'form-control';
    input.name = name;
    if (tagName === 'textarea') input.rows = 3;
    input.value = value || '';
    input.addEventListener('input', updatePreview);
    field.append(label, input);
    return field;
  }

  function createRemoveButton(label) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm btn-outline-danger remove-row';
    button.textContent = '削除';
    button.setAttribute('aria-label', label);
    button.addEventListener('click', function() {
      button.closest('.test-case-row, .hint-row').remove();
      updatePreview();
    });
    return button;
  }

  function selectedClasses() {
    return Array.from(form.querySelectorAll('input[name="classTargets"]:checked'));
  }

  function refreshSchoolDropdown() {
    const selected = Array.from(form.querySelectorAll('input[name="schoolTargets"]:checked'))
      .map(function(option) { return option.nextElementSibling.textContent.trim(); });
    document.getElementById('schoolDropdownButton').textContent = selected.length ? selected.join('、') : '学校を選択';
  }

  function refreshClassDropdown() {
    const selected = selectedClasses()
      .map(function(option) { return option.nextElementSibling.textContent.trim(); });
    document.getElementById('classDropdownButton').textContent = selected.length ? selected.join('、') : 'クラスを選択';
  }

  function refreshClassOptions() {
    const selectedSchools = new Set(Array.from(form.querySelectorAll('input[name="schoolTargets"]:checked'))
      .map(function(input) { return input.value; }));
    form.querySelectorAll('.class-option').forEach(function(option) {
      const classInput = option.querySelector('input[name="classTargets"]');
      const visible = selectedSchools.has(option.dataset.schoolId);
      option.hidden = !visible;
      classInput.disabled = !visible;
      if (!visible) classInput.checked = false;
    });
    refreshClassDropdown();
    refreshClassScheduleRows();
  }

  function refreshClassScheduleRows() {
    const container = document.getElementById('classScheduleList');
    const saved = new Map();
    container.querySelectorAll('.class-schedule-row').forEach(function(row) {
      const values = {
        assignmentId: row.querySelector('[name="assignmentIds"]').value,
        publishAt: row.querySelector('.class-schedule-publish').value,
        publishImmediate: row.querySelector('.class-schedule-publish-immediate').checked,
        dueAt: row.querySelector('.class-schedule-deadline').value,
        dueNone: row.querySelector('.class-schedule-deadline-none').checked
      };
      saved.set(row.dataset.classId, values);
      scheduleValues.set(row.dataset.classId, values);
    });
    container.replaceChildren();
    const classes = selectedClasses();
    if (!classes.length) {
      const empty = document.createElement('p');
      empty.className = 'class-schedule-empty mb-0 text-muted';
      empty.textContent = 'クラスを選択すると、クラスごとの設定欄が表示されます。';
      container.append(empty);
      return;
    }

    classes.forEach(function(classInput, index) {
      const row = document.createElement('div');
      row.className = 'class-schedule-row';
      row.dataset.classId = classInput.value;
      const name = document.createElement('strong');
      name.className = 'class-schedule-name';
      name.textContent = classInput.nextElementSibling.textContent.trim();
      const assignmentId = document.createElement('input');
      assignmentId.type = 'hidden';
      assignmentId.name = 'assignmentIds';
      const publishAt = document.createElement('input');
      publishAt.type = 'datetime-local';
      publishAt.step = '1';
      publishAt.className = 'form-control class-schedule-publish';
      publishAt.id = 'schedulePublish-' + classInput.value;
      publishAt.setAttribute('aria-label', name.textContent + ' 公開日時');
      const publishAtValue = document.createElement('input');
      publishAtValue.type = 'hidden';
      publishAtValue.name = 'publishAts';
      const publishImmediate = document.createElement('input');
      publishImmediate.type = 'checkbox';
      publishImmediate.className = 'form-check-input class-schedule-publish-immediate';
      publishImmediate.id = 'schedulePublishImmediate-' + classInput.value;
      publishImmediate.name = 'publishImmediateClassIds';
      publishImmediate.value = classInput.value;
      const publishImmediateLabel = document.createElement('label');
      publishImmediateLabel.className = 'form-check-label';
      publishImmediateLabel.htmlFor = publishImmediate.id;
      publishImmediateLabel.textContent = '即時公開';
      const publishImmediateBlock = document.createElement('div');
      publishImmediateBlock.className = 'form-check mt-2';
      publishImmediateBlock.append(publishImmediate, publishImmediateLabel);
      const dueAt = document.createElement('input');
      dueAt.type = 'datetime-local';
      dueAt.step = '1';
      dueAt.className = 'form-control class-schedule-deadline';
      dueAt.id = 'scheduleDeadline-' + classInput.value;
      dueAt.setAttribute('aria-label', name.textContent + ' 提出期限');
      const dueAtValue = document.createElement('input');
      dueAtValue.type = 'hidden';
      dueAtValue.name = 'dueAts';
      const dueNone = document.createElement('input');
      dueNone.type = 'checkbox';
      dueNone.className = 'form-check-input class-schedule-deadline-none';
      dueNone.id = 'scheduleDeadlineNone-' + classInput.value;
      dueNone.name = 'dueNoneClassIds';
      dueNone.value = classInput.value;
      const dueNoneLabel = document.createElement('label');
      dueNoneLabel.className = 'form-check-label';
      dueNoneLabel.htmlFor = dueNone.id;
      dueNoneLabel.textContent = '提出期限なし';
      const dueNoneBlock = document.createElement('div');
      dueNoneBlock.className = 'form-check mt-2';
      dueNoneBlock.append(dueNone, dueNoneLabel);
      const initial = scheduleValues.get(classInput.value) || saved.get(classInput.value) || {
        assignmentId: classInput.dataset.assignmentId || '0',
        publishAt: classInput.dataset.publishAt || '',
        publishImmediate: classInput.dataset.publishImmediate === 'true',
        dueAt: classInput.dataset.dueAt || '',
        dueNone: classInput.dataset.dueNone === 'true'
      };
      assignmentId.value = initial.assignmentId;
      setDateValue(publishAt, initial.publishAt);
      publishImmediate.checked = initial.publishImmediate === true;
      const updatePublishValue = function() {
        if (publishImmediate.checked) {
          publishAt.value = '';
        }
        publishAt.disabled = publishImmediate.checked;
        publishAtValue.value = publishImmediate.checked ? '' : publishAt.value;
      };
      publishImmediate.addEventListener('change', updatePublishValue);
      publishAt.addEventListener('input', updatePublishValue);
      publishAt.addEventListener('change', updatePublishValue);
      updatePublishValue();
      setDateValue(dueAt, initial.dueAt);
      dueNone.checked = initial.dueNone === true;
      const updateDueValue = function() {
        if (dueNone.checked) {
          dueAt.value = '';
        }
        dueAt.disabled = dueNone.checked;
        dueAtValue.value = dueNone.checked ? '' : dueAt.value;
      };
      dueNone.addEventListener('change', updateDueValue);
      dueAt.addEventListener('input', updateDueValue);
      dueAt.addEventListener('change', updateDueValue);
      updateDueValue();
      const publishBlock = scheduleField('公開日時', publishAt);
      publishBlock.append(publishImmediateBlock, publishAtValue);
      const dueBlock = scheduleField('提出期限', dueAt);
      dueBlock.append(dueNoneBlock, dueAtValue);
      row.append(name, assignmentId, publishBlock, dueBlock);
      container.append(row);
    });
  }

  function scheduleField(labelText, control) {
    const field = document.createElement('div');
    field.className = 'field-block class-schedule-field';
    const caption = document.createElement('label');
    caption.className = 'mini-label';
    caption.htmlFor = control.id;
    caption.textContent = labelText;
    field.append(caption, control);
    return field;
  }

  function setDateValue(field, value) {
    if (value && !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(value)) {
      field.type = 'text';
      field.placeholder = 'YYYY-MM-DDTHH:MM';
    }
    field.value = value || '';
  }

  function updatePreview() {
    const name = currentValue('taskName');
    const description = currentValue('description');
    const theme = currentValue('theme');
    const difficulty = currentValue('difficulty');
    document.getElementById('previewName').textContent = name || '課題名未設定';
    document.getElementById('previewEditorName').textContent = name || '課題名未設定';
    document.getElementById('previewDescription').textContent = description || '説明未設定';
    document.getElementById('previewEditorDescription').textContent = description || '説明未設定';
    document.getElementById('previewTheme').textContent = theme || '―';
    const levelLabels = { beginner: '初級', intermediate: '中級', advanced: '上級', none: '難易度なし' };
    const level = document.getElementById('previewLevel');
    level.textContent = levelLabels[difficulty] || '';
    level.classList.toggle('d-none', !levelLabels[difficulty]);
    const card = document.getElementById('previewCard');
    card.classList.remove('task-card-beginner', 'task-card-intermediate', 'task-card-advanced');
    card.classList.add('task-card-' + (difficulty === 'advanced' ? 'advanced' : difficulty === 'intermediate' ? 'intermediate' : 'beginner'));

    const features = currentValue('features').split(';').map(function(item) { return item.trim(); }).filter(Boolean);
    const featureList = document.getElementById('previewEditorFeatures');
    featureList.replaceChildren();
    (features.length ? features : ['未設定']).forEach(function(feature) {
      const item = document.createElement('li');
      item.textContent = feature;
      featureList.append(item);
    });
    document.getElementById('previewEditorConstraint').textContent = currentValue('inputConstraints') || '未設定';
    const rules = currentValue('creationRules').split(/\r?\n|;/).map(function(item) { return item.trim(); }).filter(Boolean);
    const rulesBlock = document.getElementById('previewEditorRulesBlock');
    rulesBlock.hidden = !rules.length;
    const rulesList = document.getElementById('previewEditorRules');
    rulesList.replaceChildren();
    rules.forEach(function(rule) {
      const item = document.createElement('li');
      item.textContent = rule;
      rulesList.append(item);
    });
    renderPreviewCases();
    renderPreviewHints();
  }

  function renderPreviewCases() {
    const container = document.getElementById('previewEditorCases');
    container.replaceChildren();
    const rows = Array.from(document.querySelectorAll('#testCaseList .test-case-row'));
    if (!rows.length) {
      container.textContent = '未設定';
      return;
    }
    rows.forEach(function(row) {
      const card = document.createElement('div');
      card.className = 'preview-io-case io-case-card';
      const inputLabel = document.createElement('div');
      inputLabel.className = 'io-case-label';
      inputLabel.textContent = '入力';
      const input = document.createElement('pre');
      input.textContent = row.querySelector('[name="testCaseInputs"]').value;
      const outputLabel = document.createElement('div');
      outputLabel.className = 'io-case-label';
      outputLabel.textContent = '出力';
      const output = document.createElement('pre');
      output.textContent = row.querySelector('[name="testCaseOutputs"]').value;
      card.append(inputLabel, input, outputLabel, output);
      container.append(card);
    });
  }

  function renderPreviewHints() {
    const container = document.getElementById('previewHintCards');
    container.replaceChildren();
    document.querySelectorAll('#hintList .hint-row').forEach(function(row) {
      const card = document.createElement('div');
      card.className = 'hint-card';
      const title = document.createElement('div');
      title.className = 'info-label';
      title.textContent = row.querySelector('[name="hintTitles"]').value || '未設定';
      const description = document.createElement('p');
      description.textContent = row.querySelector('[name="hintContents"]').value || 'ヒント内容未設定';
      card.append(title, description);
      const syntaxValue = row.querySelector('[name="hintUsageSyntaxes"]').value;
      if (syntaxValue) {
        const syntax = document.createElement('pre');
        syntax.textContent = syntaxValue;
        card.append(syntax);
      }
      const codeValue = row.querySelector('[name="hintCodes"]').value;
      if (codeValue) {
        const code = document.createElement('pre');
        code.textContent = codeValue;
        card.append(code);
      }
      container.append(card);
    });
    if (!container.children.length) {
      const card = document.createElement('div');
      card.className = 'hint-card';
      card.textContent = 'ヒント内容未設定';
      container.append(card);
    }
  }

  document.getElementById('addTestCaseButton').addEventListener('click', function() { addTestCaseRow(); });
  document.getElementById('addHintButton').addEventListener('click', function() { addHintRow(); });
  document.getElementById('schoolCheckboxGroup').addEventListener('change', function() {
    refreshSchoolDropdown();
    refreshClassOptions();
  });
  document.getElementById('classCheckboxGroup').addEventListener('change', function() {
    refreshClassDropdown();
    refreshClassScheduleRows();
  });
  document.getElementById('taskCreateForm').addEventListener('input', updatePreview);
  document.getElementById('taskCreateForm').addEventListener('change', updatePreview);

  const saveDraftButton = document.getElementById('saveDraftButton');
  const publishTaskButton = document.getElementById('publishTaskButton');
  const createRevisionButton = document.getElementById('createRevisionButton');
  let publicationConfirmed = false;
  if (saveDraftButton) {
    saveDraftButton.addEventListener('click', function() {
      document.getElementById('taskAction').value = formTaskId > 0 ? 'updateDraft' : 'createDraft';
    });
  }
  form.addEventListener('submit', async function(event) {
    if (event.submitter === publishTaskButton && !publicationConfirmed) {
      event.preventDefault();
      const confirmed = await feedback.confirm({
        title: '課題を保存して公開しますか？',
        message: '対象クラス、公開日時、提出期限、期限後の提出方針と評価プロンプトを確認してください。',
        confirmLabel: '公開する',
        cancelLabel: '戻って確認する',
        variant: 'warning'
      });
      if (!confirmed) return;
      publicationConfirmed = true;
      form.requestSubmit(publishTaskButton);
      return;
    }
    if (event.submitter === createRevisionButton) {
      document.getElementById('taskAction').value = 'createRevision';
      createRevisionButton.disabled = true;
      createRevisionButton.textContent = '改訂案を作成中…';
      return;
    }
    const publishing = event.submitter === publishTaskButton;
    document.getElementById('taskAction').value = publishing
      ? 'publishTask'
      : formTaskId > 0 ? 'updateDraft' : 'createDraft';
    const button = publishing ? publishTaskButton : saveDraftButton;
    if (button) {
      button.disabled = true;
      button.textContent = publishing ? '公開処理中…' : '保存中…';
    }
  });

  document.getElementById('resetFormButton').addEventListener('click', async function() {
    const confirmed = await feedback.confirm({
      title: '入力内容をリセットしますか？',
      message: 'フォームの入力内容を初期状態へ戻します。',
      confirmLabel: 'リセットする',
      cancelLabel: '戻る',
      variant: 'warning'
    });
    if (!confirmed) return;
    window.location.assign(formTaskId > 0
      ? window.location.pathname + '?taskId=' + encodeURIComponent(String(formTaskId))
      : window.location.pathname);
  });

  document.getElementById('openHintLibraryButton').addEventListener('click', function() {
    window.bootstrap.Modal.getOrCreateInstance(document.getElementById('hintLibraryModal')).show();
  });
  document.getElementById('importHintsButton').addEventListener('click', function() {
    document.querySelectorAll('.reusable-hint:checked').forEach(function(checkbox) {
      addHintRow({
        title: checkbox.dataset.title,
        content: checkbox.dataset.content,
        syntax: checkbox.dataset.syntax,
        code: checkbox.dataset.code
      });
      checkbox.checked = false;
    });
    window.bootstrap.Modal.getOrCreateInstance(document.getElementById('hintLibraryModal')).hide();
  });

  document.querySelectorAll('#testCaseList .remove-row, #hintList .remove-row').forEach(function(button) {
    button.addEventListener('click', function() {
      button.closest('.test-case-row, .hint-row').remove();
      updatePreview();
    });
  });
  document.querySelectorAll('#testCaseList input, #testCaseList textarea, #hintList input, #hintList textarea')
    .forEach(function(field) { field.addEventListener('input', updatePreview); });

  const initialCode = document.getElementById('initialCodeInput');
  if (initialCode) {
    initializeCodeEditor(initialCode, 'python');
  }
  document.querySelectorAll('#testCaseList .test-case-io').forEach(function(textarea) {
    initializeCodeEditor(textarea, 'shell');
  });
  if (formTaskId === 0 && !document.querySelector('#testCaseList .test-case-row')) {
    addTestCaseRow();
    addTestCaseRow();
  }
  refreshSchoolDropdown();
  refreshClassOptions();
  updatePreview();

  const params = new URLSearchParams(window.location.search);
  if (params.get('view') === 'history') {
    window.bootstrap.Modal.getOrCreateInstance(document.getElementById('taskAuditDetailModal')).show();
  }
  if (document.getElementById('taskSaveNotice')) {
    feedback.toast({ message: '課題を保存しました。', variant: 'success', delay: 2500 });
  }
  const taskStateNotice = document.getElementById('taskStateNotice');
  if (taskStateNotice) {
    feedback.toast({ message: taskStateNotice.textContent.trim(), variant: 'success', delay: 2500 });
    taskStateNotice.hidden = true;
  }
});
