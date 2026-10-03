document.addEventListener('DOMContentLoaded', () => {
  const schools = [
    { id: '1', name: '国際中等', code: 'sample-school-a', level: '1', locked: true },
    { id: '2', name: '附属高校', code: 'sample-school-b', level: '2', locked: false }
  ];
  const form = document.getElementById('schoolPreviewForm');
  const button = document.getElementById('schoolSaveButton');
  const modalElement = document.getElementById('schoolEditModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const schoolFeedback = window.PPEFeedback.createPageFeedback({ title: '学校管理', alertTarget: '#schoolFeedback' });
  let selected = null;
  let pending = false;

  function render() {
    const body = document.getElementById('schoolTableBody');
    body.replaceChildren();
    for (const school of schools) {
      const row = document.createElement('tr');
      for (const value of [school.name, school.code, `レベル${school.level}`,
        school.locked ? '生徒登録済み・レベル変更不可' : 'レベル変更可']) {
        const cell = document.createElement('td');
        cell.className = 'text-break';
        cell.textContent = value;
        row.appendChild(cell);
      }
      const cell = document.createElement('td');
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'btn btn-outline-primary btn-sm';
      edit.textContent = '詳細・編集';
      edit.addEventListener('click', () => open(school));
      cell.appendChild(edit);
      row.appendChild(cell);
      body.appendChild(row);
    }
  }

  function open(school) {
    selected = school;
    form.reset();
    document.getElementById('schoolEditTitle').textContent = school ? '学校詳細・編集' : '学校登録';
    const code = document.getElementById('selectedSchoolCode');
    code.hidden = !school;
    code.textContent = school ? '学校コード: ' + school.code : '';
    form.elements.name.value = school?.name || '';
    form.elements.securityLevel.value = school?.level || '';
    form.elements.securityLevel.disabled = Boolean(school?.locked);
    form.elements.securityLevel.hidden = Boolean(school?.locked);
    const locked = document.getElementById('lockedSchoolLevel');
    locked.hidden = !school?.locked;
    locked.textContent = school?.locked ? `レベル${school.level}（生徒登録済み・変更不可）` : '';
    button.textContent = school ? '変更を保存する' : '登録する';
    modal.show();
  }

  document.getElementById('createSchoolButton').addEventListener('click', () => open(null));
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (pending || !form.reportValidity()) return;
    pending = true;
    button.disabled = true;
    const name = form.elements.name.value.trim();
    const level = selected?.locked ? selected.level : form.elements.securityLevel.value;
    if (!name) {
      schoolFeedback.toast({ message: '学校名を入力してください。', variant: 'warning' });
      pending = false;
      button.disabled = false;
      return;
    }
    try {
      await new Promise(resolve => {
        modalElement.addEventListener('hidden.bs.modal', resolve, { once: true });
        modal.hide();
      });
      const confirmed = await schoolFeedback.confirm({
        title: selected ? '学校情報を変更しますか？' : '学校を登録しますか？',
        message: 'サンプルをこの画面内で更新します。DBには保存されません。',
        details: [`学校名: ${name}`, `セキュリティレベル: レベル${level}`,
          selected?.locked ? '生徒登録済みのため、学校名だけ変更します。' : '生徒を登録した後はレベルを変更できません。'],
        confirmLabel: selected ? '保存する' : '登録する',
        cancelLabel: 'キャンセル'
      });
      if (!confirmed) {
        modal.show();
        return;
      }
      if (selected) {
        selected.name = name;
        selected.level = level;
      } else {
        const id = String(schools.length + 1);
        schools.push({ id, name, code: `sample-school-${id}`, level, locked: false });
      }
      render();
      schoolFeedback.toast({ message: '学校情報を更新しました（画面内サンプルのみ）。', variant: 'success' });
    } finally {
      pending = false;
      button.disabled = false;
    }
  });

  render();
  const parameters = new URLSearchParams(location.search);
  if (parameters.get('tab') === 'schools' || parameters.has('schoolId')) {
    bootstrap.Tab.getOrCreateInstance(document.getElementById('schools-tab')).show();
    if (parameters.has('schoolId')) {
      const school = schools.find(item => item.id === parameters.get('schoolId'));
      if (school) open(school);
      else schoolFeedback.inlineAlert('指定されたサンプル学校はありません。', 'warning');
    }
  }
});
