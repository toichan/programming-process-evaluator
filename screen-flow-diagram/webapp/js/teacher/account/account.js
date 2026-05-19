// Teacher account page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeEventListeners();
});

function initializeEventListeners() {
  const createAccountForm = document.getElementById('createAccountForm');
  const filterClass = document.getElementById('filterClass');
  const filterSchool = document.getElementById('filterSchool');
  const sortBy = document.getElementById('sortBy');
  const searchInput = document.getElementById('searchInput');

  if (filterClass) {
    filterClass.addEventListener('change', filterAccounts);
  }

  if (filterSchool) {
    filterSchool.addEventListener('change', filterAccounts);
  }

  if (sortBy) {
    sortBy.addEventListener('change', filterAccounts);
  }

  if (searchInput) {
    searchInput.addEventListener('input', filterAccounts);
  }

  if (createAccountForm) {
    createAccountForm.addEventListener('submit', function(e) {
      e.preventDefault();
    });
  }
}

/**
 * テーブルをフィルタリング
 */
function filterAccounts() {
  const classFilter = document.getElementById('filterClass')?.value || '';
  const schoolFilter = document.getElementById('filterSchool')?.value || '';
  const sortBy = document.getElementById('sortBy')?.value || 'idAsc';
  const searchTerm = document.getElementById('searchInput')?.value.toLowerCase() || '';

  const tableBody = document.querySelector('.table tbody');
  if (!tableBody) return;

  const rows = Array.from(tableBody.querySelectorAll('tr'));
  rows.forEach(row => {
    let show = true;

    // クラスフィルタ
    if (classFilter && classFilter !== 'すべて') {
      const classCell = row.cells[3]?.textContent || '';
      if (!classCell.includes(classFilter)) {
        show = false;
      }
    }

    // 学校フィルタ
    if (schoolFilter && schoolFilter !== 'すべて') {
      const schoolCell = row.cells[2]?.textContent || '';
      if (!schoolCell.includes(schoolFilter)) {
        show = false;
      }
    }

    // 検索フィルタ（ID、パスワード列を含む）
    if (searchTerm) {
      const id = row.cells[0]?.textContent.toLowerCase() || '';
      const school = row.cells[2]?.textContent.toLowerCase() || '';
      if (!id.includes(searchTerm) && !school.includes(searchTerm)) {
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
}

function sortAccountRows(rows, sortBy) {
  rows.sort(function(a, b) {
    const idA = a.cells[0]?.textContent.trim() || '';
    const idB = b.cells[0]?.textContent.trim() || '';
    const createdA = a.cells[4]?.textContent.trim() || '';
    const createdB = b.cells[4]?.textContent.trim() || '';

    if (sortBy === 'idDesc') {
      return idB.localeCompare(idA, 'ja');
    }
    if (sortBy === 'createdDesc') {
      return new Date(createdB).getTime() - new Date(createdA).getTime();
    }
    if (sortBy === 'createdAsc') {
      return new Date(createdA).getTime() - new Date(createdB).getTime();
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

  if (!schoolSelect?.value || !classSelect?.value || !accountCount?.value) {
    alert('すべてのフィールドを入力してください');
    return;
  }

  const school = schoolSelect.value;
  const classValue = classSelect.value;
  const count = parseInt(accountCount.value);

  // 新規アカウント情報をコンソールに出力（プロトタイプのため）
  console.log(`新規作成: 学校="${school}", クラス="${classValue}", 人数=${count}`);

  // モーダルを閉じる
  const modal = bootstrap.Modal.getInstance(document.getElementById('createAccountModal'));
  if (modal) {
    modal.hide();
  }

  // フォームをリセット
  document.getElementById('createAccountForm')?.reset();

  // 成功メッセージを表示（アクチュアルな実装ではバックエンドに送信）
  alert(`${count}件のアカウントを作成しました。`);
}

/**
 * パスワード表示/非表示の切り替え
 */
function togglePassword(button) {
  const passwordCell = button.closest('.password-cell');
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
function deleteAccount(button) {
  const row = button.closest('tr');
  const id = row.cells[0]?.textContent || 'Unknown';

  if (confirm(`ID "${id}" のアカウントを削除してもよろしいですか？`)) {
    row.style.opacity = '0.5';
    button.disabled = true;

    // デモ用：削除処理をシミュレート
    setTimeout(() => {
      row.remove();
    }, 300);
  }
}

/**
 * CSVにエクスポート
 */
function exportCSV() {
  const table = document.querySelector('.table');
  if (!table) return;

  const rows = [];
  const headerRow = [];

  // ヘッダー
  table.querySelectorAll('thead th').forEach(th => {
    headerRow.push(th.textContent.trim());
  });
  rows.push(headerRow);

  // データ行
  table.querySelectorAll('tbody tr').forEach(tr => {
    const cells = [];
    for (let i = 0; i < 5; i++) {
      cells.push(tr.cells[i]?.textContent.trim() || '');
    }
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

  const studentId = row.cells[0]?.textContent.trim() || '-';
  const school = row.cells[2]?.textContent.trim() || '-';
  const className = row.cells[3]?.textContent.trim() || '-';

  const history = getLoginHistoryByStudentId(studentId);

  const idEl = document.getElementById('detailStudentId');
  const schoolEl = document.getElementById('detailSchool');
  const classEl = document.getElementById('detailClass');
  const bodyEl = document.getElementById('loginHistoryTableBody');
  const modalEl = document.getElementById('accountDetailModal');

  if (!bodyEl || !modalEl) return;

  if (idEl) idEl.textContent = studentId;
  if (schoolEl) schoolEl.textContent = school;
  if (classEl) classEl.textContent = className;

  bodyEl.innerHTML = history.map(function(entry) {
    return '<tr>' +
      '<td>' + entry.datetime + '</td>' +
      '<td><span class="badge ' + (entry.success ? 'text-bg-success' : 'text-bg-danger') + '">' + (entry.success ? '成功' : '失敗') + '</span></td>' +
      '<td><code>' + entry.ip + '</code></td>' +
      '<td>' + entry.device + '</td>' +
    '</tr>';
  }).join('');

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function getLoginHistoryByStudentId(studentId) {
  const histories = {
    s001: [
      { datetime: '2026-05-18 08:01:22', success: true, ip: '10.12.1.44', device: 'Windows / Chrome 136' },
      { datetime: '2026-05-17 17:36:10', success: true, ip: '10.12.1.44', device: 'Windows / Chrome 136' },
      { datetime: '2026-05-17 07:58:43', success: false, ip: '10.12.1.44', device: 'Windows / Chrome 136' }
    ],
    s002: [
      { datetime: '2026-05-18 08:04:07', success: true, ip: '10.12.1.45', device: 'iPadOS / Safari 18' },
      { datetime: '2026-05-17 16:42:21', success: true, ip: '10.12.1.45', device: 'iPadOS / Safari 18' },
      { datetime: '2026-05-16 09:03:02', success: true, ip: '10.12.1.45', device: 'iPadOS / Safari 18' }
    ],
    s003: [
      { datetime: '2026-05-18 08:09:52', success: true, ip: '10.44.2.88', device: 'Chromebook / ChromeOS 132' },
      { datetime: '2026-05-17 18:02:39', success: false, ip: '10.44.2.88', device: 'Chromebook / ChromeOS 132' },
      { datetime: '2026-05-17 08:00:30', success: true, ip: '10.44.2.88', device: 'Chromebook / ChromeOS 132' }
    ]
  };

  return histories[studentId] || [
    { datetime: '2026-05-18 08:00:00', success: true, ip: '10.0.0.1', device: 'Unknown Device' }
  ];
}

/**
 * ログアウト
 */
function logout() {
  if (confirm('ログアウトしてもよろしいですか？')) {
    window.location.href = '../account/login.html';
  }
}