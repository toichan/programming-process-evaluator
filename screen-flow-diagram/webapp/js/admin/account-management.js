const TEACHER_SESSION_STORAGE_KEY = 'ppeTeacherSession';
const ADMIN_ACCOUNT_STORAGE_KEY = 'ppeAdminTeacherAccounts';
const ADMIN_LOGIN_HISTORY_STORAGE_KEY = 'ppeAdminTeacherLoginHistory';
const ADMIN_DELETE_HISTORY_STORAGE_KEY = 'ppeAdminTeacherDeleteHistory';
const TEACHER_LOGIN_URL = 'https://toichan.github.io/programming-process-evaluator/screen-flow-diagram/webapp/WEB-INF/teacher/account/login.html';
const ALL_SCHOOLS = ['国際中等', '附属高校'];
const ALL_FEATURES = [
  '生徒アカウント管理',
  '課題進捗確認',
  '授業演習コード確認',
  '提出課題確認',
  'コード配信',
  '評価確認',
  'アンケート結果確認',
  '課題編集',
  'プロンプト修正'
];
const feedback = window.PPEFeedback || {};
const pageFeedback = feedback.createPageFeedback
  ? feedback.createPageFeedback({
    title: '教師アカウント管理',
    alertTarget: function() { return document.getElementById('createTeacherForm'); }
  })
  : null;

let passwordModal = null;
let detailModal = null;

document.addEventListener('DOMContentLoaded', function() {
  guardAdminSession();
  initializeModal();
  seedAdminData();
  bindActions();
  renderAll();
});

function guardAdminSession() {
  let session = null;
  try {
    session = JSON.parse(window.localStorage.getItem(TEACHER_SESSION_STORAGE_KEY) || 'null');
  } catch (error) {
    session = null;
  }

  if (!session || session.teacherId !== 'admin') {
    window.location.href = '../teacher/account/login.html';
    return;
  }

  const label = document.getElementById('adminSessionLabel');
  if (label) {
    label.textContent = '管理者: ' + session.teacherId;
  }
}

function initializeModal() {
  if (typeof bootstrap === 'undefined') {
    return;
  }
  const modalElement = document.getElementById('passwordModal');
  if (modalElement) {
    passwordModal = bootstrap.Modal.getOrCreateInstance(modalElement);
  }
  const detailModalElement = document.getElementById('adminAccountDetailModal');
  if (detailModalElement) {
    detailModal = bootstrap.Modal.getOrCreateInstance(detailModalElement);
  }
}

function bindActions() {
  const createForm = document.getElementById('createTeacherForm');
  if (createForm) {
    createForm.addEventListener('submit', function(event) {
      event.preventDefault();
      createTeacherAccount();
    });

    createForm.addEventListener('reset', function() {
      if (pageFeedback) {
        pageFeedback.clearInlineAlert();
      }
    });
  }

  const logoutButton = document.getElementById('adminLogoutButton');
  if (logoutButton) {
    logoutButton.addEventListener('click', async function() {
      const confirmed = pageFeedback
        ? await pageFeedback.confirm({
          title: 'ログアウトの確認',
          message: 'ログアウトしてログイン画面へ移動します。',
          detailTitle: '',
          details: ['現在のログイン状態', '教師アカウント管理画面の表示内容'],
          confirmLabel: 'ログアウトする',
          cancelLabel: '戻る',
          variant: 'warning'
        })
        : true;
      if (!confirmed) {
        return;
      }
      window.localStorage.removeItem(TEACHER_SESSION_STORAGE_KEY);
      window.location.href = '../teacher/account/login.html';
    });
  }

  const refreshButton = document.getElementById('refreshAccountsButton');
  if (refreshButton) {
    refreshButton.addEventListener('click', renderAll);
  }

  const exportCsvButton = document.getElementById('exportAccountsCsvButton');
  if (exportCsvButton) {
    exportCsvButton.addEventListener('click', exportAccountsCsv);
  }

  const copyButton = document.getElementById('passwordModalCopyButton');
  if (copyButton) {
    copyButton.addEventListener('click', copyPasswordModalText);
  }

}

function getFilteredAccounts() {
  return loadAccounts().filter(function(item) {
    return true;
  });
}

function seedAdminData() {
  if (!loadAccounts().length) {
    saveAccounts([
      {
        teacherId: 't001',
        schools: ['国際中等'],
        enabledFeatures: ['生徒アカウント管理', '課題進捗確認', '授業演習コード確認', '提出課題確認', 'コード配信', '評価確認', 'アンケート結果確認', '課題編集', 'プロンプト修正'],
        createdAt: '2026-07-01 09:10:00',
        createdBy: 'admin'
      },
      {
        teacherId: 't002',
        schools: ['附属高校'],
        enabledFeatures: ['課題進捗確認', '提出課題確認'],
        createdAt: '2026-07-02 14:25:00',
        createdBy: 'admin'
      }
    ]);
  }

  if (!loadLoginHistory().length) {
    const seedTeacherIds = loadAccounts().map(function(account) {
      return account.teacherId;
    });
    const loginRows = [];
    seedTeacherIds.forEach(function(teacherId) {
      loginRows.push(
        { occurredAt: '2026-07-05 09:12:40', teacherId: teacherId, action: 'ログイン', result: '成功', ip: '10.0.0.11', errorLog: '' },
        { occurredAt: '2026-07-05 08:43:18', teacherId: teacherId, action: 'ログイン', result: '失敗', ip: '10.0.0.11', errorLog: '認証エラー: パスワード不一致' },
        { occurredAt: '2026-07-04 17:20:01', teacherId: teacherId, action: 'ログアウト', result: '成功', ip: '10.0.0.11', errorLog: '' }
      );
    });
    saveLoginHistory(loginRows);
  }

  if (!loadDeleteHistory().length) {
    const seedTeacherIds = loadAccounts().map(function(account) {
      return account.teacherId;
    });
    const deleteRows = [];
    seedTeacherIds.forEach(function(teacherId) {
      deleteRows.push(
        { occurredAt: '2026-07-03 11:00:10', target: teacherId, actionType: '作成', result: '成功', detail: '教師アカウントを作成' },
        { occurredAt: '2026-07-03 10:40:22', target: teacherId, actionType: '編集', result: '成功', detail: '権限設定を更新' },
        { occurredAt: '2026-07-03 10:15:05', target: teacherId, actionType: '再配信', result: '失敗', detail: '対象課題が見つかりません' }
      );
    });
    saveDeleteHistory(deleteRows);
  }
}

function loadAccounts() {
  return loadArray(ADMIN_ACCOUNT_STORAGE_KEY).map(function(account) {
    const next = Object.assign({}, account);
    const features = Array.isArray(next.enabledFeatures) ? next.enabledFeatures : [];
    next.enabledFeatures = features.map(normalizeFeatureName);
    return next;
  });
}

function normalizeFeatureName(featureName) {
  if (featureName === '生徒アカウント情報管理') {
    return '生徒アカウント管理';
  }
  return featureName;
}

function saveAccounts(accounts) {
  window.localStorage.setItem(ADMIN_ACCOUNT_STORAGE_KEY, JSON.stringify(Array.isArray(accounts) ? accounts : []));
}

function loadLoginHistory() {
  return loadArray(ADMIN_LOGIN_HISTORY_STORAGE_KEY);
}

function saveLoginHistory(rows) {
  window.localStorage.setItem(ADMIN_LOGIN_HISTORY_STORAGE_KEY, JSON.stringify(Array.isArray(rows) ? rows : []));
}

function loadDeleteHistory() {
  return loadArray(ADMIN_DELETE_HISTORY_STORAGE_KEY);
}

function saveDeleteHistory(rows) {
  window.localStorage.setItem(ADMIN_DELETE_HISTORY_STORAGE_KEY, JSON.stringify(Array.isArray(rows) ? rows : []));
}

function loadArray(key) {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(key) || '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function renderAll() {
  renderAccountTable();
  renderLoginHistory();
  renderDeleteHistory();
}

function renderAccountTable() {
  const body = document.getElementById('teacherAccountTableBody');
  if (!body) {
    return;
  }

  const rows = getFilteredAccounts();

  body.innerHTML = rows.map(function(item) {
    const schoolColumns = ALL_SCHOOLS.map(function(school) {
      return '<td class="permission-column-cell">' + renderMatrixCheckbox({
        teacherId: item.teacherId,
        scope: 'school',
        value: school,
        checked: (item.schools || []).includes(school)
      }) + '</td>';
    }).join('');

    const featureColumns = ALL_FEATURES.map(function(feature) {
      return '<td class="permission-column-cell">' + renderMatrixCheckbox({
        teacherId: item.teacherId,
        scope: 'feature',
        value: feature,
        checked: (item.enabledFeatures || []).includes(feature)
      }) + '</td>';
    }).join('');

    return '<tr>'
      + '<td><code class="code-id">' + escapeHtml(item.teacherId) + '</code></td>'
      + schoolColumns
      + featureColumns
      + '<td>' + formatDateTimeTwoLineHtml(item.createdAt || '-') + '</td>'
      + '<td><code class="code-id">' + escapeHtml(item.createdBy || '-') + '</code></td>'
      + '<td>'
      + '<div class="account-action-cell">'
      + '<button class="btn btn-sm btn-primary" type="button" data-action="update" data-id="' + escapeHtml(item.teacherId) + '">更新</button>'
      + '<button class="btn btn-sm btn-outline-warning" type="button" data-action="reset-password" data-id="' + escapeHtml(item.teacherId) + '">PW再設定</button>'
      + '<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete" data-id="' + escapeHtml(item.teacherId) + '">削除</button>'
      + '</div>'
      + '</td>'
        + '<td><button class="btn btn-sm btn-outline-primary" type="button" data-action="detail" data-id="' + escapeHtml(item.teacherId) + '">表示</button></td>'
      + '</tr>';
  }).join('');

  body.querySelectorAll('button[data-action="update"]').forEach(function(button) {
    button.addEventListener('click', function() {
      const row = button.closest('tr');
      updateTeacherAccountPermissions(button.getAttribute('data-id') || '', row);
    });
  });

  body.querySelectorAll('button[data-action="reset-password"]').forEach(function(button) {
    button.addEventListener('click', async function() {
      await resetPassword(button.getAttribute('data-id') || '');
    });
  });

  body.querySelectorAll('button[data-action="delete"]').forEach(function(button) {
    button.addEventListener('click', async function() {
      await deleteTeacherAccount(button.getAttribute('data-id') || '');
    });
  });

  body.querySelectorAll('button[data-action="detail"]').forEach(function(button) {
    button.addEventListener('click', function() {
      openTeacherDetailModal(button.getAttribute('data-id') || '');
    });
  });
}

function openTeacherDetailModal(teacherId) {
  if (!teacherId) {
    return;
  }

  const teacherIdEl = document.getElementById('detailTeacherId');
  const loginBody = document.getElementById('detailLoginHistoryBody');
  const deleteBody = document.getElementById('detailDeleteHistoryBody');
  if (!teacherIdEl || !loginBody || !deleteBody) {
    return;
  }

  teacherIdEl.textContent = '教師ID: ' + teacherId;

  const loginRows = loadLoginHistory().filter(function(row) {
    return row.teacherId === teacherId;
  });
  const deleteRows = loadDeleteHistory().filter(function(row) {
    const target = row.target || row.targetTeacherId || '';
    return target === teacherId;
  });

  loginBody.innerHTML = loginRows.length
    ? loginRows.map(function(row) {
      return '<tr>'
        + '<td>' + escapeHtml(row.occurredAt || '-') + '</td>'
        + '<td>' + escapeHtml(row.action || '-') + '</td>'
        + '<td>' + renderLoginResultBadge(row.result) + '</td>'
        + '<td>' + escapeHtml(row.ip || '-') + '</td>'
        + '<td>' + escapeHtml(row.errorLog || '') + '</td>'
        + '</tr>';
    }).join('')
    : '<tr><td colspan="5" class="text-muted">該当するログイン履歴はありません。</td></tr>';

  deleteBody.innerHTML = deleteRows.length
    ? deleteRows.map(function(row) {
      return '<tr>'
        + '<td>' + escapeHtml(row.occurredAt || '-') + '</td>'
        + '<td><code class="code-id">' + escapeHtml(row.target || row.targetTeacherId || '-') + '</code></td>'
        + '<td>' + escapeHtml(row.actionType || '-') + '</td>'
        + '<td>' + renderDeleteResultBadge(row.result) + '</td>'
        + '<td>' + escapeHtml(row.detail || row.reason || '-') + '</td>'
        + '</tr>';
    }).join('')
    : '<tr><td colspan="5" class="text-muted">該当する削除履歴はありません。</td></tr>';

  if (detailModal) {
    detailModal.show();
  }
}

function formatDateTimeTwoLineHtml(value) {
  const text = String(value || '').trim();
  const matched = text.match(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})$/);
  if (!matched) {
    return escapeHtml(text || '-');
  }
  return '<span class="datetime-two-line"><span>' + escapeHtml(matched[1]) + '</span><span>' + escapeHtml(matched[2]) + '</span></span>';
}

function renderMatrixCheckbox(options) {
  const teacherId = String(options.teacherId || '');
  const scope = String(options.scope || '');
  const value = String(options.value || '');
  const checked = options.checked ? ' checked' : '';

  return '<label class="matrix-check" title="' + escapeHtml(value) + '">'
    + '<input class="form-check-input" type="checkbox" data-role="permission-checkbox" data-scope="' + escapeHtml(scope) + '" data-id="' + escapeHtml(teacherId) + '" data-value="' + escapeHtml(value) + '"' + checked + '>'
    + '</label>';
}

function updateTeacherAccountPermissions(teacherId, row) {
  if (!teacherId || !row) {
    return;
  }

  const selectedSchools = Array.from(row.querySelectorAll('input[data-role="permission-checkbox"][data-scope="school"]:checked')).map(function(input) {
    return input.getAttribute('data-value') || '';
  });
  const selectedFeatures = Array.from(row.querySelectorAll('input[data-role="permission-checkbox"][data-scope="feature"]:checked')).map(function(input) {
    return input.getAttribute('data-value') || '';
  });

  const accounts = loadAccounts();
  const account = accounts.find(function(item) {
    return item.teacherId === teacherId;
  });
  if (!account) {
    return;
  }

  account.schools = selectedSchools;
  account.enabledFeatures = selectedFeatures;
  saveAccounts(accounts);
  if (pageFeedback) {
    pageFeedback.toast({
      message: '教師アカウント ' + teacherId + ' の設定を更新しました。',
      variant: 'success'
    });
  }
}

function createTeacherAccount() {
  const teacherIdInput = document.getElementById('teacherIdInput');
  if (!teacherIdInput) {
    return;
  }

  const teacherId = String(teacherIdInput.value || '').trim();
  if (!teacherId) {
    if (pageFeedback) {
      pageFeedback.inlineAlert('教師IDを入力してください。', 'warning');
    }
    return;
  }

  const accounts = loadAccounts();
  const exists = accounts.some(function(item) { return item.teacherId === teacherId; });
  if (exists) {
    if (pageFeedback) {
      pageFeedback.inlineAlert('同じ教師IDが既に存在します。', 'warning');
    }
    return;
  }

  const schools = getCheckedValues('schoolPermissionGroup');
  const enabledFeatures = getCheckedValues('featurePermissionGroup');
  if (!schools.length) {
    if (pageFeedback) {
      pageFeedback.inlineAlert('閲覧可能な学校を1つ以上選択してください。', 'warning');
    }
    return;
  }
  if (!enabledFeatures.length) {
    if (pageFeedback) {
      pageFeedback.inlineAlert('利用可能な機能を1つ以上選択してください。', 'warning');
    }
    return;
  }
  const password = generatePassword(10);

  accounts.unshift({
    teacherId: teacherId,
    schools: schools,
    enabledFeatures: enabledFeatures,
    createdAt: buildTimestamp(),
    createdBy: 'admin'
  });

  saveAccounts(accounts);
  teacherIdInput.value = '';
  if (pageFeedback) {
    pageFeedback.clearInlineAlert();
  }
  renderAll();
  openPasswordModal(teacherId, password, 'create');
  if (pageFeedback) {
    pageFeedback.toast({
      message: '教師アカウントを作成しました。',
      variant: 'success'
    });
  }
}

async function resetPassword(teacherId) {
  if (!teacherId) {
    return;
  }

  const confirmed = pageFeedback
    ? await pageFeedback.confirm({
      title: 'PW再設定を実行しますか？',
      message: '次の内容で一時パスワードを再発行します。',
      detailTitle: '',
      details: ['教師ID: ' + teacherId, '新しい一時パスワードを発行', '次回ログイン時の変更を必須化'],
      confirmLabel: 'PW再設定する',
      cancelLabel: '戻る',
      variant: 'warning'
    })
    : true;
  if (!confirmed) {
    return;
  }

  const accounts = loadAccounts();
  const account = accounts.find(function(item) { return item.teacherId === teacherId; });
  if (!account) {
    return;
  }

  const password = generatePassword(10);
  openPasswordModal(account.teacherId, password, 'reset');
  if (pageFeedback) {
    pageFeedback.toast({
      message: 'PW再設定を完了しました。',
      variant: 'success'
    });
  }
}

async function deleteTeacherAccount(teacherId) {
  if (!teacherId) {
    return;
  }

  const confirmed = pageFeedback
    ? await pageFeedback.danger({
      title: '教師アカウントを削除しますか？',
      message: '次のデータを削除します。',
      detailTitle: '',
      details: ['教師ID: ' + teacherId, '教師アカウントの設定情報', '削除履歴へ記録'],
      confirmLabel: '削除する',
      cancelLabel: '戻る'
    })
    : false;
  if (!confirmed) {
    return;
  }

  const accounts = loadAccounts();
  const next = accounts.filter(function(item) {
    return item.teacherId !== teacherId;
  });
  saveAccounts(next);

  const deleteHistory = loadDeleteHistory();
  deleteHistory.unshift({
    occurredAt: buildTimestamp(),
    target: teacherId,
    actionType: '削除',
    result: '成功',
    detail: '管理者操作'
  });
  saveDeleteHistory(deleteHistory);

  renderAll();
  if (pageFeedback) {
    pageFeedback.toast({
      message: '教師アカウントを削除しました。',
      variant: 'success'
    });
  }
}

function renderLoginHistory() {
  const body = document.getElementById('loginHistoryBody');
  if (!body) {
    return;
  }

  const rows = loadLoginHistory();
  body.innerHTML = rows.map(function(row) {
    return '<tr>'
      + '<td>' + escapeHtml(row.occurredAt || '-') + '</td>'
      + '<td>' + escapeHtml(row.action || '-') + '</td>'
      + '<td>' + renderLoginResultBadge(row.result) + '</td>'
      + '<td>' + escapeHtml(row.ip || '-') + '</td>'
      + '<td>' + escapeHtml(row.errorLog || '') + '</td>'
      + '</tr>';
  }).join('');
}

function renderLoginResultBadge(result) {
  if (result === '成功') {
    return '<span class="badge text-bg-success">成功</span>';
  }
  if (result === '失敗') {
    return '<span class="badge text-bg-danger">失敗</span>';
  }
  return '<span class="badge text-bg-light">-</span>';
}

function renderDeleteHistory() {
  const body = document.getElementById('deleteHistoryBody');
  if (!body) {
    return;
  }

  const rows = loadDeleteHistory();
  body.innerHTML = rows.map(function(row) {
    return '<tr>'
      + '<td>' + escapeHtml(row.occurredAt || '-') + '</td>'
      + '<td><code class="code-id">' + escapeHtml(row.target || row.targetTeacherId || '-') + '</code></td>'
      + '<td>' + escapeHtml(row.actionType || '-') + '</td>'
      + '<td>' + renderDeleteResultBadge(row.result) + '</td>'
      + '<td>' + escapeHtml(row.detail || row.reason || '-') + '</td>'
      + '</tr>';
  }).join('');
}

function renderDeleteResultBadge(result) {
  if (result === '成功') {
    return '<span class="badge text-bg-success">成功</span>';
  }
  if (result === '失敗') {
    return '<span class="badge text-bg-danger">失敗</span>';
  }
  return '<span class="badge text-bg-light">-</span>';
}

function exportAccountsCsv() {
  const rows = getFilteredAccounts();
  if (!rows.length) {
    if (pageFeedback) {
      pageFeedback.toast({ message: 'CSV出力対象がありません。', variant: 'warning' });
    }
    return;
  }

  const headers = ['教師ID', '国際中等', '附属高校']
    .concat(ALL_FEATURES)
    .concat(['作成日時', '作成者']);

  const csvRows = [headers.join(',')];
  rows.forEach(function(item) {
    const schools = Array.isArray(item.schools) ? item.schools : [];
    const features = Array.isArray(item.enabledFeatures) ? item.enabledFeatures : [];
    const line = [
      toCsvValue(item.teacherId || ''),
      toCsvValue(schools.includes('国際中等') ? '1' : '0'),
      toCsvValue(schools.includes('附属高校') ? '1' : '0')
    ];

    ALL_FEATURES.forEach(function(feature) {
      line.push(toCsvValue(features.includes(feature) ? '1' : '0'));
    });

    line.push(toCsvValue(item.createdAt || ''));
    line.push(toCsvValue(item.createdBy || ''));
    csvRows.push(line.join(','));
  });

  const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const now = new Date();
  const stamp = String(now.getFullYear())
    + String(now.getMonth() + 1).padStart(2, '0')
    + String(now.getDate()).padStart(2, '0')
    + '_' + String(now.getHours()).padStart(2, '0')
    + String(now.getMinutes()).padStart(2, '0');
  link.href = url;
  link.download = 'teacher-accounts-' + stamp + '.csv';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);

  if (pageFeedback) {
    pageFeedback.toast({ message: 'CSVをエクスポートしました。', variant: 'success' });
  }
}

function toCsvValue(value) {
  const text = String(value || '');
  return '"' + text.replace(/"/g, '""') + '"';
}

async function openPasswordModal(teacherId, password, mode) {
  const copyText = document.getElementById('passwordModalCopyText');
  const status = document.getElementById('passwordModalCopyStatus');
  const message = buildAccountGuideText(teacherId, password, mode);

  if (copyText) {
    copyText.textContent = message;
  }
  if (status) {
    status.textContent = '';
  }
  if (passwordModal) {
    passwordModal.show();
  }

  const copied = await copyTextToClipboard(message, copyText);
  if (status) {
    status.textContent = copied
      ? '自動でコピーしました。'
      : '自動コピーに失敗しました。「再コピー」を押してください。';
  }
}

function buildAccountGuideText(teacherId, password, mode) {
  const firstLine = mode === 'reset'
    ? 'パスワードを再設定しました。次のアカウントでログインしてください。'
    : '教師用アカウントを作成しました。次のアカウントでログインしてください。';

  return firstLine + '\n\n'
    + '教師用ID: ' + String(teacherId || '-') + '\n\n'
    + 'パスワード: ' + String(password || '-') + '\n\n'
    + TEACHER_LOGIN_URL;
}

async function copyPasswordModalText() {
  const copyText = document.getElementById('passwordModalCopyText');
  const status = document.getElementById('passwordModalCopyStatus');
  if (!copyText) {
    return;
  }

  const text = copyText.textContent || '';
  const copied = await copyTextToClipboard(text, copyText);

  if (status) {
    status.textContent = copied ? 'コピーしました。' : 'コピーに失敗しました。手動でコピーしてください。';
  }
}

async function copyTextToClipboard(text, fallbackNode) {
  let copied = false;

  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
    } catch (error) {
      copied = false;
    }
  }

  if (!copied && fallbackNode) {
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(fallbackNode);
    selection.removeAllRanges();
    selection.addRange(range);
    copied = document.execCommand('copy');
    selection.removeAllRanges();
  }

  return copied;
}

function getCheckedValues(groupId) {
  const root = document.getElementById(groupId);
  if (!root) {
    return [];
  }
  return Array.from(root.querySelectorAll('input[type="checkbox"]:checked')).map(function(input) {
    return input.value;
  });
}

function generatePassword(length) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%';
  let text = '';
  for (let i = 0; i < length; i += 1) {
    const index = Math.floor(Math.random() * chars.length);
    text += chars[index];
  }
  return text;
}

function buildTimestamp() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const hh = String(now.getHours()).padStart(2, '0');
  const mi = String(now.getMinutes()).padStart(2, '0');
  const ss = String(now.getSeconds()).padStart(2, '0');
  return yyyy + '-' + mm + '-' + dd + ' ' + hh + ':' + mi + ':' + ss;
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
