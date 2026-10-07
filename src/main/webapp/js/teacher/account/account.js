window.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('#studentAccountConsole');
  const endpoint = root.dataset.endpoint;
  const feedback = window.PPEFeedback.createPageFeedback({ title: '生徒アカウント管理', alertTarget: '#accountFeedback' });
  const filters = document.querySelector('#accountFilters');
  const createForm = document.querySelector('#createAccountForm');
  const resetForm = document.querySelector('#resetPasswordForm');
  const rows = document.querySelector('#accountRows');
  const createElement = document.querySelector('#createAccountModal');
  const resetElement = document.querySelector('#resetPasswordModal');
  const credentialElement = document.querySelector('#studentCredentialModal');
  const detailElement = document.querySelector('#accountDetailModal');
  const statusLabels = { active: '利用中', suspended: '停止中', deleted: '削除済み' };
  const consentLabels = { agreed: '同意', declined: '不同意', withdrawn: '撤回', unconfirmed: '未確認' };
  const actionLabels = { create: '作成', reset: '再設定', reveal: 'パスワード確認', unlock: 'ロック解除', suspend: '停止', activate: '停止解除', delete: '削除', csv: 'CSV出力', login: 'ログイン', logout: 'ログアウト', password_change: 'パスワード変更', password_reset: 'パスワード再設定', initial_credential_issued: '初期資格情報発行', first_password_changed: '初回変更', password_changed: 'パスワード変更' };
  let accounts = [];
  let options = [];
  let pending = false;
  let resetTarget = null;
  let loadNumber = 0;
  let sortKey = 'loginId';
  let sortDirection = 1;
  const openingModals = new WeakSet();

  const modal = element => bootstrap.Modal.getOrCreateInstance(element);
  const showError = error => { feedback.inlineAlert(error.message, 'danger'); feedback.toast({ message: error.message, variant: 'warning' }); };
  const firstLogin = account => account.securityLevel === 1 ? '対象外' : account.mustChangePassword || account.firstLoginStatus !== 'completed' ? '未完了' : '完了';
  const className = item => [item.gradeName, item.name || item.classroomName].filter(Boolean).join(' ');
  const classNames = account => account.classrooms.map(className).join(' / ');
  const date = value => value.replace('T', ' ');
  function resetPasswordForm(form) {
    form.reset();
    form.querySelectorAll('[data-toggle-password]').forEach(button => {
      document.getElementById(button.dataset.togglePassword).type = 'password';
      button.textContent = '表示';
    });
  }
  function node(tag, text, classNameValue) {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (classNameValue) element.className = classNameValue;
    return element;
  }
  async function readJson(url, init) {
    const response = await fetch(url, { ...init, credentials: 'same-origin', cache: 'no-store' });
    if (response.redirected) throw new Error('ログイン状態を確認し、画面を読み込み直してください。');
    if (!response.headers.get('Content-Type')?.includes('application/json')) throw new Error('読み込めませんでした。画面を読み込み直してください。');
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || '操作に失敗しました。');
    return result;
  }
  function listUrl(view) {
    const url = new URL(endpoint, location.origin);
    for (const [key, value] of new FormData(filters)) if (value) url.searchParams.set(key, value);
    url.searchParams.set('view', view);
    return url;
  }
  function selectOptions(select, choices, label, value) {
    const previous = select.value;
    select.replaceChildren(new Option(label, ''));
    for (const choice of choices) select.add(new Option(choice.label, String(choice.value)));
    if ([...select.options].some(option => option.value === (value ?? previous))) select.value = value ?? previous;
  }
  function schools() { return [...new Map(options.map(item => [item.schoolId, item])).values()]; }
  function classes(schoolId) { return options.filter(item => item.classroomId && (!schoolId || item.schoolId === Number(schoolId))); }
  function updateClassFilter() {
    selectOptions(filters.elements.classroomId, classes(filters.elements.schoolId.value).map(item => ({ label: `${item.schoolName} / ${className(item)}`, value: item.classroomId })), 'すべて');
  }
  function updateCreateClasses() {
    const school = options.find(item => item.schoolId === Number(createForm.elements.schoolId.value));
    selectOptions(createForm.elements.classroomId, classes(createForm.elements.schoolId.value).map(item => ({ label: className(item), value: item.classroomId })), 'クラスを選択', '');
    if (school) createForm.elements.classroomId.add(new Option('新しいクラスを作成', '0'));
    document.querySelector('#schoolSecurityLevel').value = school ? `レベル${school.securityLevel}` : '学校を選択してください';
    updateNewClass();
  }
  function updateNewClass() {
    const isNew = createForm.elements.classroomId.value === '0';
    document.querySelector('#newClassFields').hidden = !isNew;
    createForm.elements.classroomName.required = isNew;
    createForm.elements.classroomName.disabled = !isNew;
  }
  async function load() {
    const number = ++loadNumber;
    document.querySelector('#listStatus').textContent = '読み込み中…';
    try {
      const result = await readJson(listUrl('list'));
      if (number !== loadNumber) return;
      accounts = result.accounts; options = result.options;
      document.querySelector('#schoolCount').textContent = `${schools().length}校`;
      document.querySelector('#classCount').textContent = `${classes().length}組`;
      document.querySelector('#studentCount').textContent = result.total;
      selectOptions(filters.elements.schoolId, schools().map(item => ({ label: item.schoolName, value: item.schoolId })), 'すべて');
      updateClassFilter();
      selectOptions(createForm.elements.schoolId, schools().map(item => ({ label: item.schoolName, value: item.schoolId })), '学校を選択');
      document.querySelector('#createAccountButton').disabled = schools().length === 0;
      const exportLink = document.querySelector('#exportAccounts');
      exportLink.href = listUrl('csv'); exportLink.classList.remove('disabled'); exportLink.setAttribute('aria-disabled', 'false');
      render();
    } catch (error) {
      if (number !== loadNumber) return;
      document.querySelector('#listStatus').textContent = '一覧の読み込みに失敗しました。';
      showError(error);
    }
  }
  function button(action, label, account, variant = 'outline-secondary') {
    const element = node('button', label, `btn btn-${variant} btn-sm`);
    element.type = 'button'; element.dataset.action = action; element.dataset.id = account.userId;
    return element;
  }
  function render() {
    rows.replaceChildren();
    const sorted = [...accounts].sort((a, b) => sortDirection * String(a[sortKey]).localeCompare(String(b[sortKey]), 'ja', { numeric: true }));
    for (const account of sorted) {
      const tr = node('tr'); tr.dataset.userId = account.userId;
      const selection = node('td');
      const checkbox = node('input', undefined, 'form-check-input account-select');
      checkbox.type = 'checkbox'; checkbox.value = account.userId; checkbox.disabled = account.status === 'deleted';
      checkbox.setAttribute('aria-label', `${account.loginId}を選択`); selection.append(checkbox); tr.append(selection);
      const id = node('td'); id.append(node('code', account.loginId)); tr.append(id);
      const password = node('td');
      if (account.passwordAvailable) password.append(button('reveal', '確認', account));
      else password.textContent = '確認不可';
      tr.append(password);
      [`レベル${account.securityLevel}`, account.schoolName, classNames(account), firstLogin(account), consentLabels[account.consent], statusLabels[account.status], date(account.createdAt)].forEach(text => tr.append(node('td', text)));
      const actions = node('td'); const group = node('div', undefined, 'account-actions');
      if (account.status !== 'deleted') {
        if (account.securityLevel === 2) group.append(button('reset', 'PW再設定', account));
        if (account.loginLocked) group.append(button('unlock', 'ロック解除', account));
        group.append(button(account.status === 'active' ? 'suspend' : 'activate', account.status === 'active' ? '停止' : '停止解除', account));
        group.append(button('delete', '削除', account, 'outline-danger'));
      }
      actions.append(group); tr.append(actions);
      const detail = node('td'); detail.append(button('detail', '詳細', account)); tr.append(detail);
      rows.append(tr);
    }
    if (!accounts.length) { const tr = node('tr'); const td = node('td', '該当する生徒アカウントはありません。'); td.colSpan = 12; tr.append(td); rows.append(tr); }
    document.querySelector('#listStatus').textContent = `${accounts.length}件を表示`;
    document.querySelector('#selectAllAccounts').checked = false;
    updateSelection();
  }
  function selected() { return [...rows.querySelectorAll('.account-select:checked')].map(item => accounts.find(account => account.userId === Number(item.value))); }
  function updateSelection() {
    const enabled = [...rows.querySelectorAll('.account-select:not(:disabled)')];
    const chosen = selected().length;
    const all = document.querySelector('#selectAllAccounts');
    all.disabled = enabled.length === 0;
    all.checked = chosen > 0 && chosen === enabled.length; all.indeterminate = chosen > 0 && chosen < enabled.length;
    document.querySelector('#bulkDeleteButton').disabled = chosen === 0;
  }
  async function hide(element) {
    if (!element) return;
    if (openingModals.has(element)) await new Promise(ready => element.addEventListener('shown.bs.modal', ready, { once: true }));
    if (!element.classList.contains('show')) return;
    return new Promise(resolve => {
      element.addEventListener('hidden.bs.modal', resolve, { once: true }); modal(element).hide();
    });
  }
  function credential(text) {
    document.querySelector('#credentialText').value = text;
    document.querySelector('#copyCredentialStatus').textContent = '';
    modal(credentialElement).show();
  }
  async function perform(action, targets, data = new FormData(), source) {
    if (pending) return;
    pending = true;
    const initialPassword = data.get('password');
    const saved = [...root.querySelectorAll('button')].map(button => [button, button.disabled]);
    saved.forEach(([button]) => { button.disabled = true; });
    try {
      await hide(source);
      const confirmed = await feedback.confirm({
        title: `${actionLabels[action]}しますか？`,
        message: action === 'delete' ? '論理削除します。復元はできませんが、学習記録と履歴は保持します。'
          : action === 'reset' ? '現在のパスワードと既存のログインは失効します。次回は生徒本人の変更が必要です。'
            : action === 'reveal' ? '教師が確認可能な資格情報を表示し、操作を記録します。'
              : action === 'create' ? '指定した人数の生徒アカウントと所属を作成します。'
                : 'アカウント情報を更新します。既存のログインは失効します。',
        details: [{ label: '対象', text: action === 'create' ? `${data.get('count')}人` : targets.map(item => item.loginId).join('、') }],
        confirmLabel: action === 'delete' ? '削除する' : '実行する', cancelLabel: 'キャンセル'
      });
      if (!confirmed) { if (source) modal(source).show(); return; }
      data.set('action', action); data.set('csrfToken', root.dataset.csrf); data.set('changeConfirmed', 'yes');
      for (const target of targets) { data.append('userId', target.userId); data.append('version', target.version); }
      const result = await readJson(endpoint, { method: 'POST', body: new URLSearchParams(data) });
      createForm.elements.password.value = ''; resetForm.elements.password.value = '';
      if (result.password) { credential(`${targets[0].loginId}\t${result.password}`); result.password = ''; }
      else if (action === 'create') credential(result.ids.map(id => `${id}\t${initialPassword}`).join('\n'));
      else if (action === 'reset') credential(`${targets[0].loginId}\t${initialPassword}`);
      feedback.toast({ message: result.message || '確認しました。', variant: 'success' });
      await load();
    } catch (error) { showError(error); if (source) modal(source).show(); }
    finally { pending = false; saved.forEach(([button, disabled]) => { if (button.isConnected) button.disabled = disabled; }); updateSelection(); }
  }
  function historySection(title, entries) {
    const section = node('section', undefined, 'account-detail-section'); section.append(node('h3', title, 'fs-6'));
    const wrap = node('div', undefined, 'table-responsive');
    const table = node('table', undefined, 'table table-sm');
    const head = node('thead'); const header = node('tr');
    ['日時', '操作', '結果', '実行者', '詳細'].forEach(text => header.append(node('th', text))); head.append(header); table.append(head);
    const body = node('tbody');
    for (const entry of entries) {
      const tr = node('tr');
      [date(entry.occurredAt), actionLabels[entry.action] || entry.action, entry.result === 'success' ? '成功' : '失敗', entry.actor || '—', entry.detail].forEach(text => tr.append(node('td', text)));
      body.append(tr);
    }
    if (!entries.length) { const tr = node('tr'); const td = node('td', '履歴はありません。'); td.colSpan = 5; tr.append(td); body.append(tr); }
    table.append(body); wrap.append(table); section.append(wrap); return section;
  }
  root.addEventListener('click', async event => {
    const target = event.target.closest('button[data-action]');
    if (!target || pending) return;
    const account = accounts.find(item => item.userId === Number(target.dataset.id));
    if (!account) return;
    try {
      if (target.dataset.action === 'reset') {
        resetTarget = account; resetPasswordForm(resetForm); document.querySelector('#resetStudentId').textContent = account.loginId; modal(resetElement).show();
      } else if (target.dataset.action === 'detail') {
        const url = new URL(endpoint, location.origin); url.searchParams.set('view', 'detail'); url.searchParams.set('userId', account.userId);
        const result = await readJson(url); const current = result.account; const body = document.querySelector('#accountDetailBody');
        body.replaceChildren(node('p', `${current.loginId} / ${current.schoolName} / ${classNames(current)}`),
          node('p', `レベル${current.securityLevel} / 初回変更: ${firstLogin(current)} / 研究同意: ${consentLabels[current.consent]} / ${statusLabels[current.status]}`),
          node('p', `作成者: ${current.createdBy || '—'} / 最終更新者: ${current.updatedBy || '—'}`),
          historySection('ログイン履歴', result.login), historySection('パスワード変更履歴', result.credentials), historySection('操作履歴', result.operations));
        modal(detailElement).show();
      } else await perform(target.dataset.action, [account]);
    } catch (error) { showError(error); }
  });
  document.querySelector('#createAccountButton').addEventListener('click', () => { resetPasswordForm(createForm); updateCreateClasses(); modal(createElement).show(); });
  createForm.elements.schoolId.addEventListener('change', updateCreateClasses);
  createForm.elements.classroomId.addEventListener('change', updateNewClass);
  createForm.addEventListener('submit', event => { event.preventDefault(); if (createForm.reportValidity()) perform('create', [], new FormData(createForm), createElement); });
  resetForm.addEventListener('submit', event => { event.preventDefault(); if (resetForm.reportValidity() && resetTarget) perform('reset', [resetTarget], new FormData(resetForm), resetElement); });
  document.querySelector('#bulkDeleteButton').addEventListener('click', () => perform('delete', selected()));
  rows.addEventListener('change', updateSelection);
  document.querySelector('#selectAllAccounts').addEventListener('change', event => { rows.querySelectorAll('.account-select:not(:disabled)').forEach(item => { item.checked = event.target.checked; }); updateSelection(); });
  filters.addEventListener('submit', event => { event.preventDefault(); load(); });
  filters.addEventListener('change', event => { if (event.target.name === 'schoolId') { filters.elements.classroomId.value = ''; updateClassFilter(); } load(); });
  let searchTimer;
  filters.elements.q.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(load, 200); });
  document.querySelectorAll('[data-sort]').forEach(button => button.addEventListener('click', () => { sortDirection = sortKey === button.dataset.sort ? -sortDirection : 1; sortKey = button.dataset.sort; render(); }));
  document.querySelectorAll('[data-toggle-password]').forEach(button => button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.togglePassword); input.type = input.type === 'password' ? 'text' : 'password'; button.textContent = input.type === 'password' ? '表示' : '非表示';
  }));
  document.querySelector('#copyCredentialButton').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(document.querySelector('#credentialText').value); document.querySelector('#copyCredentialStatus').textContent = 'コピーしました。'; }
    catch (error) { document.querySelector('#copyCredentialStatus').textContent = 'コピーできませんでした。表示欄から選択してコピーしてください。'; showError(new Error('クリップボードへのコピーに失敗しました。')); }
  });
  [createElement, resetElement, credentialElement, detailElement].forEach(element => {
    element.addEventListener('show.bs.modal', () => openingModals.add(element));
    element.addEventListener('shown.bs.modal', () => openingModals.delete(element));
    element.addEventListener('hide.bs.modal', () => { if (element.contains(document.activeElement)) document.activeElement.blur(); });
  });
  credentialElement.addEventListener('hidden.bs.modal', () => { document.querySelector('#credentialText').value = ''; document.querySelector('#copyCredentialStatus').textContent = ''; });
  createElement.addEventListener('hidden.bs.modal', () => { if (!pending) createForm.elements.password.value = ''; });
  resetElement.addEventListener('hidden.bs.modal', () => { if (!pending) resetForm.elements.password.value = ''; });
  window.addEventListener('pagehide', () => { document.querySelector('#credentialText').value = ''; createForm.elements.password.value = ''; resetForm.elements.password.value = ''; });
  load();
});
