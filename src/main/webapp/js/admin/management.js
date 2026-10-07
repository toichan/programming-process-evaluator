window.addEventListener('DOMContentLoaded', () => {
  const consoleRoot = document.querySelector('#adminConsole');
  const endpoint = consoleRoot.dataset.endpoint;
  const feedback = window.PPEFeedback.createPageFeedback({ title: '管理者画面', alertTarget: '#adminFeedback' });
  const teacherForm = document.querySelector('#teacherForm');
  const schoolForm = document.querySelector('#schoolForm');
  const teacherElement = document.querySelector('#teacherEditModal');
  const schoolElement = document.querySelector('#schoolEditModal');
  const credentialElement = document.querySelector('#credentialModal');
  const teacherModal = bootstrap.Modal.getOrCreateInstance(teacherElement);
  const schoolModal = bootstrap.Modal.getOrCreateInstance(schoolElement);
  const credentialModal = bootstrap.Modal.getOrCreateInstance(credentialElement);
  [teacherElement, schoolElement, credentialElement, document.querySelector('#teacherAuditModal')].forEach(element => {
    element.addEventListener('hide.bs.modal', () => {
      if (element.contains(document.activeElement)) document.activeElement.blur();
    });
  });
  let pending = false;
  let credentialsIssued = false;

  const actions = {
    create: '教師アカウントを作成', permissions: '教師の権限を変更',
    reset: 'パスワードを再設定', suspend: '教師アカウントを停止',
    activate: '教師アカウントの停止を解除', delete: '教師アカウントを削除', school: '学校情報を保存'
  };
  const historyLabels = {
    create: '作成', permissions: '権限変更', reset: 'パスワード再設定', suspend: '停止',
    activate: '停止解除', delete: '削除', login: 'ログイン', logout: 'ログアウト'
  };
  async function readJson(url, options) {
    const response = await fetch(url, { ...options, cache: 'no-store', credentials: 'same-origin' });
    if (response.redirected) throw new Error('ログイン状態を確認し、画面を読み込み直してください。');
    if (!response.headers.get('Content-Type')?.includes('application/json')) {
      throw new Error('読み込みに失敗しました。画面を読み込み直してください。');
    }
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || '操作に失敗しました。');
    return result;
  }
  function showError(error) {
    feedback.inlineAlert(error.message, 'danger');
    feedback.toast({ message: error.message, variant: 'danger' });
  }
  function reload(tab) {
    const url = new URL(endpoint, location.origin);
    if (tab) url.searchParams.set('tab', tab);
    url.searchParams.set('notice', 'saved');
    location.replace(url);
  }
  function hideModal(element) {
    return new Promise((resolve) => {
      if (!element.classList.contains('show')) { resolve(); return; }
      element.addEventListener('hidden.bs.modal', resolve, { once: true });
      bootstrap.Modal.getOrCreateInstance(element).hide();
    });
  }
  async function perform(data, label, sourceModal) {
    if (pending) return;
    pending = true;
    const buttons = document.querySelectorAll('#adminConsole button, .modal button');
    buttons.forEach(button => { button.disabled = true; });
    try {
      const action = data.get('action');
      const confirmed = await feedback.confirm({
        title: `${actions[action]}しますか？`,
        message: action === 'delete' ? '削除後も履歴は残ります。削除済みアカウントは復元できません。'
          : action === 'reset' ? '現在のパスワードは使えなくなり、既存のログインは失効します。'
            : action === 'school' ? '学校情報を保存します。生徒登録後はレベルを変更できません。'
              : '設定内容をDBに保存します。既存の教師ログインにも変更が反映されます。',
        details: [{ label: action === 'school' ? '学校名' : '教師ID', text: label }],
        confirmLabel: action === 'delete' ? '削除する' : '実行する', cancelLabel: 'キャンセル'
      });
      if (!confirmed) return;
      data.set('csrfToken', consoleRoot.dataset.csrf);
      data.set('changeConfirmed', 'yes');
      const result = await readJson(endpoint, { method: 'POST', body: new URLSearchParams(data) });
      if (result.password) {
        credentialsIssued = true;
        document.querySelector('#credentialId').value = label;
        document.querySelector('#credentialPassword').value = result.password;
        result.password = '';
        if (sourceModal) await hideModal(sourceModal);
        credentialModal.show();
      } else {
        reload(action === 'school' ? 'schools' : null);
      }
    } catch (error) {
      showError(error);
    } finally {
      pending = false;
      buttons.forEach(button => { button.disabled = false; });
    }
  }
  document.querySelector('#createTeacherButton').addEventListener('click', () => {
    teacherForm.elements.action.value = 'create';
    teacherForm.reset();
    teacherForm.elements.userId.value = '';
    teacherForm.elements.version.value = '';
    teacherForm.elements.loginId.readOnly = false;
    document.querySelector('#teacherEditTitle').textContent = '教師アカウント作成';
    document.querySelector('#teacherEditorKicker').textContent = 'Create Account';
    teacherModal.show();
  });
  teacherForm.addEventListener('reset', event => {
    if (teacherForm.elements.action.value === 'permissions') {
      event.preventDefault();
      populateTeacher(JSON.parse(teacherForm.dataset.initial));
    }
  });
  function populateTeacher(account) {
    teacherForm.elements.action.value = 'permissions';
    teacherForm.elements.userId.value = account.userId;
    teacherForm.elements.version.value = account.version;
    teacherForm.elements.loginId.value = account.loginId;
    teacherForm.elements.loginId.readOnly = true;
    teacherForm.querySelectorAll('[name="schoolIds"]').forEach(input => { input.checked = account.schools.includes(Number(input.value)); });
    teacherForm.querySelectorAll('[name="features"]').forEach(input => { input.checked = account.features.includes(input.value); });
  }
  teacherForm.addEventListener('submit', event => {
    event.preventDefault();
    if (teacherForm.reportValidity()) perform(new FormData(teacherForm), teacherForm.elements.loginId.value, teacherElement);
  });
  consoleRoot.addEventListener('click', async event => {
    const button = event.target.closest('button[data-action], button[data-history], button[data-school-edit]');
    if (!button || pending) return;
    const row = button.closest('tr');
    try {
      if (button.hasAttribute('data-history')) {
        const url = new URL(endpoint, location.origin);
        url.searchParams.set('view', 'history');
        url.searchParams.set('kind', button.dataset.history);
        if (row) url.searchParams.set('userId', row.dataset.userId);
        const result = await readJson(url);
        const body = document.querySelector('#historyRows');
        body.replaceChildren();
        for (const entry of result.history) {
          const tr = document.createElement('tr');
          [entry.occurredAt, entry.actor, entry.teacher, historyLabels[entry.action] || entry.action,
            entry.result === 'success' ? '成功' : '失敗', entry.detail].forEach(value => {
            const td = document.createElement('td'); td.textContent = value; tr.append(td);
          });
          body.append(tr);
        }
        if (!result.history.length) {
          const tr = document.createElement('tr'); const td = document.createElement('td');
          td.colSpan = 6; td.textContent = '履歴はありません。'; tr.append(td); body.append(tr);
        }
        document.querySelector('#teacherAuditTitle').textContent = button.dataset.history === 'login' ? 'ログイン履歴' : '操作・削除履歴';
        bootstrap.Modal.getOrCreateInstance(document.querySelector('#teacherAuditModal')).show();
      } else if (button.hasAttribute('data-school-edit')) {
        openSchool(row.dataset);
      } else if (button.dataset.action === 'edit') {
        const url = new URL(endpoint, location.origin);
        url.searchParams.set('view', 'teacher'); url.searchParams.set('userId', row.dataset.userId);
        const account = await readJson(url);
        teacherForm.dataset.initial = JSON.stringify(account);
        populateTeacher(account);
        document.querySelector('#teacherEditTitle').textContent = '教師アカウント編集';
        document.querySelector('#teacherEditorKicker').textContent = 'Edit Account';
        teacherModal.show();
      } else {
        const data = new FormData();
        data.set('action', button.dataset.action); data.set('userId', row.dataset.userId); data.set('version', row.dataset.version);
        await perform(data, row.dataset.loginId);
      }
    } catch (error) { showError(error); }
  });
  function openSchool(data) {
    schoolForm.reset();
    schoolForm.elements.schoolId.value = data?.schoolId || '0';
    schoolForm.elements.version.value = data?.version || '0';
    schoolForm.elements.name.value = data?.name || '';
    schoolForm.elements.securityLevel.value = data?.level || '';
    schoolForm.elements.securityLevel.disabled = data?.locked === 'true';
    document.querySelector('#schoolEditTitle').textContent = data ? '学校詳細・編集' : '学校登録';
    document.querySelector('#schoolPolicyNotice').textContent = data?.locked === 'true'
      ? '生徒登録済みのため、セキュリティレベルは変更できません。学校名は変更できます。'
      : '一度でも生徒を登録するとレベルは変更できません。学校名は変更できます。';
    schoolModal.show();
  }
  document.querySelector('#createSchoolButton').addEventListener('click', () => openSchool());
  schoolForm.addEventListener('submit', event => {
    event.preventDefault();
    if (!schoolForm.reportValidity()) return;
    const data = new FormData(schoolForm);
    data.set('securityLevel', schoolForm.elements.securityLevel.value);
    perform(data, schoolForm.elements.name.value, schoolElement);
  });
  document.querySelector('#copyCredentials').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(`教師ID: ${document.querySelector('#credentialId').value}\nパスワード: ${document.querySelector('#credentialPassword').value}`);
      feedback.toast({ message: 'ログイン情報をコピーしました。', variant: 'success' });
    } catch (error) {
      feedback.toast({ message: 'コピーできませんでした。表示されているログイン情報を手動で控えてください。', variant: 'warning' });
    }
  });
  function clearCredentials() {
    document.querySelector('#credentialId').value = '';
    document.querySelector('#credentialPassword').value = '';
  }
  credentialElement.addEventListener('hide.bs.modal', clearCredentials);
  credentialElement.addEventListener('hidden.bs.modal', () => { if (credentialsIssued) reload(); });
  window.addEventListener('pagehide', clearCredentials);
  window.addEventListener('pageshow', event => { if (event.persisted) { clearCredentials(); location.reload(); } });
  if (new URLSearchParams(location.search).get('tab') === 'schools') {
    bootstrap.Tab.getOrCreateInstance(document.querySelector('#schools-tab')).show();
  }
  if (new URLSearchParams(location.search).get('notice') === 'saved') {
    feedback.inlineAlert('管理情報を保存しました。', 'success');
  }
});
