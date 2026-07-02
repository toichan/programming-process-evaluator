// Teacher account page interactions

let currentSortBy = 'idAsc';
const feedback = window.PPEFeedback || {};
const pageFeedback = feedback.createPageFeedback({
  title: 'アカウント管理',
  alertTarget: () => document.getElementById('createAccountForm')
});

document.addEventListener('DOMContentLoaded', function() {
  initializeEventListeners();
});

function initializeEventListeners() {
  const createAccountForm = document.getElementById('createAccountForm');
  const filterClass = document.getElementById('filterClass');
  const filterSchool = document.getElementById('filterSchool');
  const filterSecurityLevel = document.getElementById('filterSecurityLevel');
  const filterFirstLogin = document.getElementById('filterFirstLogin');
  const filterConsent = document.getElementById('filterConsent');
  const searchInput = document.getElementById('searchInput');

  initializeHeaderSorting();

  if (filterClass) {
    filterClass.addEventListener('change', filterAccounts);
  }

  if (filterSchool) {
    filterSchool.addEventListener('change', filterAccounts);
  }

  if (filterSecurityLevel) {
    filterSecurityLevel.addEventListener('change', filterAccounts);
  }

  if (filterFirstLogin) {
    filterFirstLogin.addEventListener('change', filterAccounts);
  }

  if (filterConsent) {
    filterConsent.addEventListener('change', filterAccounts);
  }

  if (searchInput) {
    searchInput.addEventListener('input', filterAccounts);
  }

  if (createAccountForm) {
    createAccountForm.addEventListener('submit', function(e) {
      e.preventDefault();
    });
  }
  updateHeaderSortIndicator();
}

function initializeHeaderSorting() {
  const headers = document.querySelectorAll('.table thead th.sortable');
  headers.forEach(function(header) {
    header.addEventListener('click', function() {
      const key = header.dataset.sortKey;
      if (!key) return;

      const isSameKey = currentSortBy.startsWith(key);
      if (!isSameKey) {
        currentSortBy = key + 'Asc';
      } else {
        currentSortBy = currentSortBy.endsWith('Asc') ? key + 'Desc' : key + 'Asc';
      }

      filterAccounts();
    });
  });
}

function updateHeaderSortIndicator() {
  const headers = document.querySelectorAll('.table thead th.sortable');
  headers.forEach(function(header) {
    const key = header.dataset.sortKey;
    header.classList.remove('sorted-asc', 'sorted-desc');
    header.removeAttribute('aria-sort');

    if (currentSortBy === key + 'Asc') {
      header.classList.add('sorted-asc');
      header.setAttribute('aria-sort', 'ascending');
    }
    if (currentSortBy === key + 'Desc') {
      header.classList.add('sorted-desc');
      header.setAttribute('aria-sort', 'descending');
    }
  });
}

/**
 * テーブルをフィルタリング
 */
function filterAccounts() {
  const classFilter = document.getElementById('filterClass')?.value || '';
  const schoolFilter = document.getElementById('filterSchool')?.value || '';
  const securityFilter = document.getElementById('filterSecurityLevel')?.value || '';
  const firstLoginFilter = document.getElementById('filterFirstLogin')?.value || '';
  const consentFilter = document.getElementById('filterConsent')?.value || '';
  const sortBy = currentSortBy;
  const searchTerm = document.getElementById('searchInput')?.value.toLowerCase() || '';

  const tableBody = document.querySelector('.table tbody');
  if (!tableBody) return;

  const rows = Array.from(tableBody.querySelectorAll('tr'));
  rows.forEach(row => {
    let show = true;

    const idCell = row.querySelector('.cell-id')?.textContent || '';
    const passwordCell = row.querySelector('.cell-password')?.textContent || '';
    const securityCell = row.querySelector('.cell-security')?.textContent || '';
    const schoolCell = row.querySelector('.cell-school')?.textContent || '';
    const classCell = row.querySelector('.cell-class')?.textContent || '';
    const firstLoginCell = row.querySelector('.cell-first-login')?.textContent || '';
    const consentCell = row.querySelector('.cell-consent')?.textContent || '';

    // クラスフィルタ
    if (classFilter && classFilter !== 'すべて') {
      if (!classCell.includes(classFilter)) {
        show = false;
      }
    }

    // 学校フィルタ
    if (schoolFilter && schoolFilter !== 'すべて') {
      if (!schoolCell.includes(schoolFilter)) {
        show = false;
      }
    }

    // セキュリティレベルフィルタ
    if (securityFilter && securityFilter !== 'すべて') {
      if (!securityCell.includes(securityFilter)) {
        show = false;
      }
    }

    // 初回パスワード変更フィルタ
    if (firstLoginFilter && firstLoginFilter !== 'すべて') {
      if (!firstLoginCell.includes(firstLoginFilter)) {
        show = false;
      }
    }

    // 研究同意フィルタ
    if (consentFilter && consentFilter !== 'すべて') {
      if (!consentCell.includes(consentFilter)) {
        show = false;
      }
    }

    // 検索フィルタ（ID、学校列を含む）
    if (searchTerm) {
      const id = idCell.toLowerCase();
      const password = passwordCell.toLowerCase();
      const school = schoolCell.toLowerCase();
      const firstLogin = firstLoginCell.toLowerCase();
      const consent = consentCell.toLowerCase();
      if (!id.includes(searchTerm) && !password.includes(searchTerm) && !school.includes(searchTerm) && !firstLogin.includes(searchTerm) && !consent.includes(searchTerm)) {
        show = false;
      }
    }

    row.style.display = show ? '' : 'none';
  });

  const visibleRows = rows.filter(function(row) {
    return row.style.display !== 'none';
  });

  sortAccountRows(visibleRows, sortBy);
  visibleRows.forEach(function(row) {
    tableBody.appendChild(row);
  });

  updateHeaderSortIndicator();
}

function sortAccountRows(rows, sortBy) {
  rows.sort(function(a, b) {
    const idA = a.querySelector('.cell-id')?.textContent.trim() || '';
    const idB = b.querySelector('.cell-id')?.textContent.trim() || '';
    const securityA = a.querySelector('.cell-security')?.textContent.trim() || '';
    const securityB = b.querySelector('.cell-security')?.textContent.trim() || '';
    const schoolA = a.querySelector('.cell-school')?.textContent.trim() || '';
    const schoolB = b.querySelector('.cell-school')?.textContent.trim() || '';
    const classA = a.querySelector('.cell-class')?.textContent.trim() || '';
    const classB = b.querySelector('.cell-class')?.textContent.trim() || '';
    const firstLoginA = a.querySelector('.cell-first-login')?.textContent.trim() || '';
    const firstLoginB = b.querySelector('.cell-first-login')?.textContent.trim() || '';
    const consentA = a.querySelector('.cell-consent')?.textContent.trim() || '';
    const consentB = b.querySelector('.cell-consent')?.textContent.trim() || '';
    const createdA = a.querySelector('.cell-created')?.textContent.trim() || '';
    const createdB = b.querySelector('.cell-created')?.textContent.trim() || '';
    const levelOrder = { 'レベル1': 1, 'レベル2': 2 };
    const firstLoginOrder = { '完了': 1, '未完了': 2, '対象外': 3 };
    const consentOrder = { '同意': 1, '不同意': 2, '未確認': 3 };

    if (sortBy === 'securityAsc') {
      return (levelOrder[securityA] || 99) - (levelOrder[securityB] || 99);
    }
    if (sortBy === 'securityDesc') {
      return (levelOrder[securityB] || 99) - (levelOrder[securityA] || 99);
    }
    if (sortBy === 'schoolAsc') {
      return schoolA.localeCompare(schoolB, 'ja');
    }
    if (sortBy === 'schoolDesc') {
      return schoolB.localeCompare(schoolA, 'ja');
    }
    if (sortBy === 'classAsc') {
      return classA.localeCompare(classB, 'ja');
    }
    if (sortBy === 'classDesc') {
      return classB.localeCompare(classA, 'ja');
    }
    if (sortBy === 'idDesc') {
      return idB.localeCompare(idA, 'ja');
    }
    if (sortBy === 'createdDesc') {
      return new Date(createdB).getTime() - new Date(createdA).getTime();
    }
    if (sortBy === 'createdAsc') {
      return new Date(createdA).getTime() - new Date(createdB).getTime();
    }
    if (sortBy === 'firstLoginAsc') {
      return (firstLoginOrder[firstLoginA] || 99) - (firstLoginOrder[firstLoginB] || 99);
    }
    if (sortBy === 'firstLoginDesc') {
      return (firstLoginOrder[firstLoginB] || 99) - (firstLoginOrder[firstLoginA] || 99);
    }
    if (sortBy === 'consentAsc') {
      return (consentOrder[consentA] || 99) - (consentOrder[consentB] || 99);
    }
    if (sortBy === 'consentDesc') {
      return (consentOrder[consentB] || 99) - (consentOrder[consentA] || 99);
    }
    if (sortBy === 'idAsc') {
      return idA.localeCompare(idB, 'ja');
    }
    return idA.localeCompare(idB, 'ja');
  });
}

/**
 * 新規アカウントを作成
 */
function createAccounts() {
  const schoolSelect = document.getElementById('schoolSelect');
  const classSelect = document.getElementById('classSelect');
  const accountCount = document.getElementById('accountCount');
  const securityLevelSelect = document.getElementById('securityLevelSelect');
  const initialPassword = document.getElementById('initialPassword');

  function validatePassword(value) {
    const checks = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/];
    const count = checks.reduce((sum, pattern) => sum + (pattern.test(value) ? 1 : 0), 0);
    return value.length >= 8 && value.length <= 32 && count >= 3;
  }

  if (!schoolSelect?.value || !classSelect?.value || !accountCount?.value || !securityLevelSelect?.value || !initialPassword?.value) {
    pageFeedback.inlineAlert('学校、クラス、作成件数、セキュリティレベル、初期パスワードを入力してください。', 'warning');
    return;
  }

  if (!validatePassword(initialPassword.value)) {
    pageFeedback.inlineAlert('初期パスワードが要件を満たしていません。', 'warning');
    return;
  }

  pageFeedback.clearInlineAlert();

  const school = schoolSelect.value;
  const classValue = classSelect.value;
  const count = parseInt(accountCount.value);
  const securityLevel = securityLevelSelect.value;

  // 新規アカウント情報をコンソールに出力（プロトタイプのため）
  console.log(`新規作成: 学校="${school}", クラス="${classValue}", 人数=${count}, セキュリティ=${securityLevel}`);

  // モーダルを閉じる
  const modal = bootstrap.Modal.getInstance(document.getElementById('createAccountModal'));
  if (modal) {
    modal.hide();
  }

  // フォームをリセット
  document.getElementById('createAccountForm')?.reset();

  // 成功メッセージを表示（アクチュアルな実装ではバックエンドに送信）
  pageFeedback.toast({
    title: 'アカウント管理',
    message: `${count}件のレベル${securityLevel}アカウントを作成しました。`,
    variant: 'success'
  });
}

/**
 * パスワード表示/非表示の切り替え
 */
function togglePassword(button) {
  const passwordCell = button.closest('.password-cell');
  if (button?.disabled || passwordCell?.dataset.passwordScope === '確認不可') return;
  const passwordMask = passwordCell?.querySelector('.password-mask');
  const eyeIcon = button?.querySelector('.eye-icon');

  if (!passwordMask || !eyeIcon) return;

  // 現在の表示状態を判定（マスク表示 = ••••••••）
  const isCurrentlyMasked = passwordMask.textContent === '••••••••';

  if (isCurrentlyMasked) {
    // パスワード表示
    const actualPassword = passwordMask.dataset.password || '••••••••';
    passwordMask.textContent = actualPassword;
    eyeIcon.classList.add('active');
  } else {
    // パスワード非表示（マスク表示）
    passwordMask.textContent = '••••••••';
    eyeIcon.classList.remove('active');
  }
}

/**
 * アカウントを削除
 */
async function deleteAccount(button) {
  const row = button.closest('tr');
  const id = row.cells[0]?.textContent || 'Unknown';

  const confirmed = await pageFeedback.confirm({
    title: 'アカウントを削除しますか？',
    message: '次のデータを削除します。',
    detailTitle: '',
    details: [`生徒ID: ${id}`, 'アカウント一覧の表示情報'],
    confirmLabel: '削除する',
    cancelLabel: '戻る',
    variant: 'danger'
  });

  if (!confirmed) {
    return;
  }

  row.style.opacity = '0.5';
  button.disabled = true;

  // デモ用：削除処理をシミュレート
  setTimeout(() => {
    row.remove();
    pageFeedback.toast({
      title: 'アカウント管理',
      message: `ID ${id} のアカウントを削除しました。`,
      variant: 'success'
    });
  }, 300);
}

/**
 * CSVにエクスポート
 */
function exportCSV() {
  const table = document.querySelector('.table');
  if (!table) return;

  const rows = [];
  const headerRow = ['ID', 'パスワード', 'セキュリティレベル', '学校', 'クラス', '初回パスワード変更', '研究同意', '作成日時'];
  rows.push(headerRow);

  // データ行
  table.querySelectorAll('tbody tr').forEach(tr => {
    const cells = [];
    cells.push(tr.querySelector('.cell-id')?.textContent.trim() || '');
    const passwordScope = tr.querySelector('.password-cell')?.dataset.passwordScope || '';
    const passwordValue = passwordScope === '確認不可'
      ? '確認不可'
      : (tr.querySelector('.password-mask')?.dataset.password || '');
    cells.push(passwordValue);
    cells.push(tr.querySelector('.cell-security')?.textContent.trim() || '');
    cells.push(tr.querySelector('.cell-school')?.textContent.trim() || '');
    cells.push(tr.querySelector('.cell-class')?.textContent.trim() || '');
    cells.push(tr.querySelector('.cell-first-login')?.textContent.trim() || '');
    cells.push(tr.querySelector('.cell-consent')?.textContent.trim() || '');
    cells.push(tr.querySelector('.cell-created')?.textContent.trim() || '');
    rows.push(cells);
  });

  // CSV形式に変換
  const csv = rows.map(row =>
    row.map(cell => {
      // カンマ、改行を含む場合はダブルクォートで囲む
      if (cell.includes(',') || cell.includes('\n') || cell.includes('"')) {
        return '"' + cell.replace(/"/g, '""') + '"';
      }
      return cell;
    }).join(',')
  ).join('\n');

  // ダウンロード
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);

  link.setAttribute('href', url);
  link.setAttribute('download', `accounts_${new Date().toISOString().split('T')[0]}.csv`);
  link.style.visibility = 'hidden';

  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/**
 * アカウント詳細（ログイン履歴）を表示
 */
function openAccountDetail(button) {
  const row = button.closest('tr');
  if (!row) return;

  const studentId = row.querySelector('.cell-id')?.textContent.trim() || '-';
  const security = row.querySelector('.cell-security')?.textContent.trim() || '-';
  const school = row.querySelector('.cell-school')?.textContent.trim() || '-';
  const className = row.querySelector('.cell-class')?.textContent.trim() || '-';
  const firstLogin = row.querySelector('.cell-first-login')?.textContent.trim() || '-';
  const consent = row.querySelector('.cell-consent')?.textContent.trim() || '-';

  const loginHistory = getLoginHistory(studentId);
  const passwordHistory = getPasswordHistory(studentId);

  const idEl = document.getElementById('detailStudentId');
  const securityEl = document.getElementById('detailSecurity');
  const schoolEl = document.getElementById('detailSchool');
  const classEl = document.getElementById('detailClass');
  const firstLoginEl = document.getElementById('detailFirstLogin');
  const consentEl = document.getElementById('detailConsent');
  const loginBodyEl = document.getElementById('loginHistoryTableBody');
  const passwordBodyEl = document.getElementById('passwordHistoryTableBody');
  const modalEl = document.getElementById('accountDetailModal');

  if (!loginBodyEl || !passwordBodyEl || !modalEl) return;

  if (idEl) idEl.textContent = studentId;
  if (securityEl) securityEl.textContent = security;
  if (schoolEl) schoolEl.textContent = school;
  if (classEl) classEl.textContent = className;
  if (firstLoginEl) firstLoginEl.textContent = firstLogin;
  if (consentEl) consentEl.textContent = consent;

  loginBodyEl.innerHTML = loginHistory.map(function(entry) {
    const badgeClass = getHistoryActionBadgeClass(entry.action, entry.result);
    return '<tr>' +
      '<td>' + entry.datetime + '</td>' +
      '<td><span class="badge account-history-badge ' + badgeClass + '">' + entry.action + '</span></td>' +
      '<td>' + entry.result + '</td>' +
      '<td>' + (entry.errorLog || '') + '</td>' +
    '</tr>';
  }).join('');

  passwordBodyEl.innerHTML = passwordHistory.map(function(entry) {
    const badgeClass = getHistoryActionBadgeClass(entry.action);
    return '<tr>' +
      '<td>' + entry.datetime + '</td>' +
      '<td><span class="badge account-history-badge ' + badgeClass + '">' + entry.action + '</span></td>' +
      '<td>' + entry.actor + '</td>' +
    '</tr>';
  }).join('');

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function getHistoryActionBadgeClass(action, result) {
  if (action.includes('ログイン') && result === '成功') {
    return 'is-success';
  }
  if (action.includes('ログイン') && result === '失敗') {
    return 'is-danger';
  }
  if (action.includes('初回変更完了')) {
    return 'is-success';
  }
  if (action.includes('初期作成')) {
    return 'is-created';
  }
  if (action.includes('リセット')) {
    return 'is-warning';
  }
  if (action.includes('ログアウト')) {
    return 'is-neutral';
  }
  return 'is-default';
}

function getLoginHistory(studentId) {
  const histories = {
    s001: [
      { datetime: '2026-05-18 08:01:30', action: 'ログイン', result: '成功', errorLog: '' },
      { datetime: '2026-05-12 08:05:14', action: 'ログアウト', result: '成功', errorLog: '' }
    ],
    s002: [
      { datetime: '2026-05-18 08:04:30', action: 'ログイン', result: '失敗', errorLog: '認証エラー: パスワード不一致' },
      { datetime: '2026-05-18 08:07:02', action: 'ログイン', result: '成功', errorLog: '' }
    ],
    s003: [
      { datetime: '2026-05-11 09:13:40', action: 'ログイン', result: '成功', errorLog: '' }
    ]
  };

  return histories[studentId] || [
    { datetime: '2026-05-18 08:00:00', action: 'ログイン', result: '成功', errorLog: '' }
  ];
}

function getPasswordHistory(studentId) {
  const histories = {
    s001: [
      { datetime: '2026-05-10 10:30:00', action: '初期作成', actor: '教師（toida）' },
      { datetime: '2026-05-18 08:01:22', action: '初回変更完了', actor: '生徒（s001）' }
    ],
    s002: [
      { datetime: '2026-05-10 10:30:00', action: '初期作成', actor: '教師（toida）' },
      { datetime: '2026-05-18 08:04:07', action: 'リセット', actor: '教師（佐藤）' }
    ],
    s003: [
      { datetime: '2026-05-10 10:35:00', action: '初期作成', actor: '教師（鈴木）' }
    ]
  };

  return histories[studentId] || [
    { datetime: '2026-05-18 08:00:00', action: '初期作成', actor: '教師（未設定）' }
  ];
}

async function resetPassword(button) {
  const row = button.closest('tr');
  if (!row) return;

  const securityLevel = row.querySelector('.cell-security')?.textContent.trim() || '';
  const studentId = row.querySelector('.cell-id')?.textContent.trim() || '';
  if (securityLevel !== 'レベル2') {
    pageFeedback.inlineAlert('パスワードリセットはレベル2の生徒のみ実行できます。', 'warning');
    return;
  }

  const confirmed = await pageFeedback.confirm({
    title: 'パスワードをリセットしますか？',
    message: '次の内容で一時パスワードを再発行します。',
    detailTitle: '',
    details: [`生徒ID: ${studentId}`, '一時パスワードを新しく設定', '次回ログイン時にパスワード変更を必須化'],
    confirmLabel: 'リセットする',
    cancelLabel: '戻る',
    variant: 'warning'
  });

  if (!confirmed) {
    return;
  }

  const tempPassword = 'RtY78!Ui';
  const passwordCell = row.querySelector('.password-cell');
  const passwordContent = row.querySelector('.password-content');
  const passwordMask = row.querySelector('.password-mask');
  const firstLoginCell = row.querySelector('.cell-first-login .status-pill');
  let passwordToggle = row.querySelector('.password-toggle');

  if (passwordMask) {
    passwordMask.dataset.password = tempPassword;
    passwordMask.textContent = '••••••••';
    passwordMask.classList.remove('is-unavailable');
  }
  if (!passwordToggle && passwordContent) {
    const toggleButton = document.createElement('button');
    toggleButton.className = 'btn btn-sm btn-icon password-toggle';
    toggleButton.type = 'button';
    toggleButton.title = 'パスワード表示';
    toggleButton.setAttribute('onclick', 'togglePassword(this)');
    toggleButton.innerHTML = '<svg class="eye-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>';
    passwordContent.appendChild(toggleButton);
    passwordToggle = toggleButton;
  }
  if (passwordToggle) {
    passwordToggle.disabled = false;
    passwordToggle.title = 'パスワード表示';
  }
  if (firstLoginCell) {
    firstLoginCell.textContent = '未完了';
    firstLoginCell.className = 'status-pill status-pending';
  }
  if (passwordCell) {
    passwordCell.dataset.passwordScope = 'リセット用';
  }

  pageFeedback.toast({
    title: 'アカウント管理',
    message: `${studentId} の一時パスワードを再発行しました。`,
    variant: 'success'
  });
}

/**
 * ログアウト
 */
async function logout() {
  const confirmed = await pageFeedback.confirm({
    title: 'ログアウトしますか？',
    message: '次の操作を実行します。',
    detailTitle: '',
    details: ['現在のログイン状態', 'アカウント管理画面の表示内容'],
    confirmLabel: 'ログアウトする',
    cancelLabel: '戻る',
    variant: 'warning'
  });

  if (confirmed) {
    window.location.href = '../account/login.html';
  }
}
