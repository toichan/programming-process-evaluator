// Teacher account page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeEventListeners();
});

function initializeEventListeners() {
  const createAccountForm = document.getElementById('createAccountForm');
  const filterClass = document.getElementById('filterClass');
  const filterSchool = document.getElementById('filterSchool');
  const searchInput = document.getElementById('searchInput');

  if (filterClass) {
    filterClass.addEventListener('change', filterAccounts);
  }

  if (filterSchool) {
    filterSchool.addEventListener('change', filterAccounts);
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
  const searchTerm = document.getElementById('searchInput')?.value.toLowerCase() || '';

  const tableBody = document.querySelector('.table tbody');
  if (!tableBody) return;

  const rows = tableBody.querySelectorAll('tr');
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
 * ログアウト
 */
function logout() {
  if (confirm('ログアウトしてもよろしいですか？')) {
    window.location.href = '../account/login.html';
  }
}