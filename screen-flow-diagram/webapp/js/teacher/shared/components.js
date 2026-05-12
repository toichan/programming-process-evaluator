/**
 * Teacher Shared Components
 * サイドメニュー、ヘッダーを動的に生成
 */

/**
 * ログアウト
 */
function logout() {
  if (confirm('ログアウトしてもよろしいですか？')) {
    window.location.href = '../account/login.html';
  }
}

/**
 * 教師共有コンポーネント（サイドメニュー、ヘッダー）を生成・挿入
 */
function loadTeacherComponents() {
  const sidebarHTML = `
    <nav class="teacher-sidebar bg-light border-end">
      <div class="sidebar-header">
        <h3 class="sidebar-title">メニュー</h3>
      </div>
      <ul class="sidebar-menu">
        <li class="sidebar-menu-item" data-page="account">
          <a href="../account/account.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>
            </svg>
            <span>アカウント管理</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="history">
          <a href="../history/history.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"/>
            </svg>
            <span>学習履歴確認</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="evaluation">
          <a href="../evaluation/evaluation.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
            <span>評価確認</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="survey">
          <a href="../survey/survey.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 9h4v2h-4v-2zm0 4h4v2h-4v-2zM8 14h4v2H8v-2zm0-4h4v2H8v-2z"/>
            </svg>
            <span>アンケート結果確認</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="task">
          <a href="../task/task.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>
            </svg>
            <span>課題・プロンプト修正</span>
          </a>
        </li>
      </ul>
      <div class="sidebar-footer">
        <a href="../account/login.html" class="btn btn-outline-secondary w-100">ログアウト</a>
      </div>
    </nav>
  `;

  const headerHTML = `
    <header class="teacher-header bg-white border-bottom">
      <div class="container-fluid h-100 d-flex align-items-center justify-content-between px-4">
        <div class="header-logo d-flex align-items-center">
          <h2 class="mb-0">Programming Process Evaluator</h2>
        </div>
        <div class="header-actions d-flex align-items-center gap-3">
          <div class="teacher-info">
            <div class="teacher-info-item">
              <svg class="teacher-info-icon" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
              </svg>
              <span class="teacher-info-text">ID: t001</span>
            </div>
            <div class="teacher-info-item">
              <svg class="teacher-info-icon" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L2 7v10c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-10-5zm0 17.5c-3.87 0-7-2.64-7-5.9v-7.68l7-3.5 7 3.5v7.68c0 3.26-3.13 5.9-7 5.9z"/>
              </svg>
              <span class="teacher-info-text">学校：all</span>
            </div>
          </div>
          <button class="btn btn-sm btn-outline-secondary" onclick="logout()">ログアウト</button>
        </div>
      </div>
    </header>
  `;

  // body に flex クラスを追加
  document.body.classList.add('d-flex', 'min-vh-100');

  // body の最初にサイドメニューを挿入
  document.body.insertAdjacentHTML('afterbegin', sidebarHTML);
  
  // main 要素の最初にヘッダーを挿入
  const main = document.querySelector('main');
  if (main) {
    main.insertAdjacentHTML('afterbegin', headerHTML);
  }

  setActiveSidebarMenu();
}

function setActiveSidebarMenu() {
  const path = window.location.pathname;
  const pageMap = {
    '/teacher/account/account.html': 'account',
    '/teacher/history/history.html': 'history',
    '/teacher/evaluation/evaluation.html': 'evaluation',
    '/teacher/survey/survey.html': 'survey',
    '/teacher/task/task.html': 'task'
  };

  let activePage = 'account';
  Object.keys(pageMap).forEach(key => {
    if (path.includes(key)) {
      activePage = pageMap[key];
    }
  });

  document.querySelectorAll('.sidebar-menu-item').forEach(item => {
    const page = item.getAttribute('data-page');
    item.classList.toggle('active', page === activePage);
  });
}

// DOM が読み込まれたら実行
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', loadTeacherComponents);
} else {
  loadTeacherComponents();
}

