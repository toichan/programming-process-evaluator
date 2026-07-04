/**
 * Teacher Shared Components
 * サイドメニュー、ヘッダーを動的に生成
 */

/**
 * ログアウト
 */
async function logout() {
  const feedback = window.PPEFeedback || {};
  const confirmed = typeof feedback.showConfirmDialog === 'function'
    ? await feedback.showConfirmDialog({
      title: 'ログアウトの確認',
      message: 'ログアウトしてログイン画面へ移動します。',
      detailTitle: '',
      details: ['現在のログイン状態', '表示中の教師画面'],
      confirmLabel: 'ログアウトする',
      cancelLabel: '戻る',
      variant: 'primary'
    })
    : window.confirm('ログアウトの確認\nログアウトしてログイン画面へ移動します。');

  if (confirmed) {
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
              <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
            </svg>
            <span>生徒アカウント管理</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="progress">
          <a href="../progress/progress.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"/>
            </svg>
            <span>課題進捗確認機能</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="exercise">
          <a href="../exercise/exercise.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M8.59 16.59L4 12l4.59-4.59L10 8.82 6.83 12 10 15.18l-1.41 1.41zm6.82 0L14 15.18 17.17 12 14 8.82l1.41-1.41L20 12l-4.59 4.59z"/>
              <path d="M13.83 4h1.85l-5.5 16h-1.85l5.5-16z"/>
            </svg>
            <span>授業演習コード確認</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="distribution">
          <a href="../distribution/distribution.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 5a2 2 0 0 1 2-2h5.17a2 2 0 0 1 1.41.59l.83.82A2 2 0 0 0 13.83 5H19a2 2 0 0 1 2 2v2H3V5zm0 5h18v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-9zm9 2l-4 4h3v3h2v-3h3l-4-4z"/>
            </svg>
            <span>コード配信</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="submission">
          <a href="../submission/submission.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM7 10h6v2H7v-2zm0 3h10v2H7v-2z"/>
              <path d="M14.5 5l-2.5 2.5L9.5 5H14.5z"/>
            </svg>
            <span>提出課題確認</span>
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
              <path d="M19 3h-4.18C14.4 1.84 13.3 1 12 1s-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7-1c.55 0 1 .45 1 1h-2c0-.55.45-1 1-1zm7 17H5V5h14v14zm-2-10H7v2h10V9zm0 4H7v2h7v-2z"/>
            </svg>
            <span>課題編集</span>
          </a>
        </li>
        <li class="sidebar-menu-item" data-page="prompt">
          <a href="../prompt/prompt.html" class="sidebar-menu-link">
            <svg class="sidebar-icon" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zm14.71-9.04c.39-.39.39-1.02 0-1.41l-2.5-2.5a.996.996 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.99-1.83z"/>
            </svg>
            <span>プロンプト修正</span>
          </a>
        </li>
      </ul>
    </nav>
  `;

  const headerHTML = `
    <header class="teacher-header bg-white border-bottom">
      <div class="container-fluid h-100 d-flex align-items-center justify-content-between px-4">
        <div class="header-logo d-flex align-items-center">
          <svg class="header-logo-icon" width="32" height="32" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <path d="M4 4.5C4 3.67 4.67 3 5.5 3h13c.83 0 1.5.67 1.5 1.5v15c0 .83-.67 1.5-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5v-15zM5.5 4a.5.5 0 0 0-.5.5V8h15V4.5a.5.5 0 0 0-.5-.5h-14zM5 9v10.5c0 .28.22.5.5.5h13a.5.5 0 0 0 .5-.5V9H5z"/>
            <path d="M8.75 13.5L7.25 12l1.5-1.5m6.5 3L16.75 12l-1.5-1.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M11 13.5h2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          </svg>
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
                <path d="M5 3v18h14V3H5zm6 2h2v2h-2V5zM7 9h2v2H7V9zm4 0h2v2h-2V9zm4 0h2v2h-2V9zM7 13h2v2H7v-2zm4 0h2v2h-2v-2zm4 0h2v2h-2v-2zM10 17h4v4h-4v-4z"/>
              </svg>
              <span class="teacher-info-text">学校: all</span>
            </div>
          </div>
          <button class="btn btn-sm btn-outline-secondary" onclick="logout()">ログアウト</button>
        </div>
      </div>
    </header>
  `;

  const rubricButtonHTML = `
    <button
      type="button"
      class="teacher-rubric-fab"
      data-bs-toggle="modal"
      data-bs-target="#teacherRubricModal"
      aria-label="ルーブリックを開く"
    >
      <svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
        <path d="M4 .5A1.5 1.5 0 0 0 2.5 2v12A1.5 1.5 0 0 0 4 15.5h8a1.5 1.5 0 0 0 1.5-1.5V2A1.5 1.5 0 0 0 12.5.5h-8zM4 1.5h8a.5.5 0 0 1 .5.5v12a.5.5 0 0 1-.5.5H4a.5.5 0 0 1-.5-.5V2a.5.5 0 0 1 .5-.5z"/>
        <path d="M5 4.5h6v1H5v-1zm0 2.5h6v1H5v-1zm0 2.5h4v1H5v-1z"/>
      </svg>
      <span>ルーブリック</span>
    </button>
  `;

  const rubricModalHTML = `
    <div class="modal fade rubric-modal" id="teacherRubricModal" tabindex="-1" aria-labelledby="teacherRubricModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-fullscreen modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <div class="rubric-header-brand">
              <div class="rubric-header-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M4 .5A1.5 1.5 0 0 0 2.5 2v12A1.5 1.5 0 0 0 4 15.5h8a1.5 1.5 0 0 0 1.5-1.5V2A1.5 1.5 0 0 0 12.5.5h-8zM4 1.5h8a.5.5 0 0 1 .5.5v12a.5.5 0 0 1-.5.5H4a.5.5 0 0 1-.5-.5V2a.5.5 0 0 1 .5-.5z"/>
                  <path d="M5 4.5h6v1H5v-1zm0 2.5h6v1H5v-1zm0 2.5h4v1H5v-1z"/>
                </svg>
              </div>
              <div class="rubric-header-copy">
                <div class="rubric-header-kicker">Evaluation Criteria</div>
                <h2 class="modal-title h4 mb-1" id="teacherRubricModalLabel">ルーブリック</h2>
              </div>
            </div>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
          </div>
          <div class="modal-body py-4">
            <div class="container-fluid">
              <section class="sample-section rubric-section rubric-thinking mb-4">
                <h2 class="section-title mb-4">思考力・判断力・表現力 ルーブリック</h2>
                <div class="table-responsive">
                  <table class="table rubric-table table-bordered align-middle mb-0">
                    <thead>
                      <tr>
                        <th style="width: 10rem;">レベル</th>
                        <th>文法デバッグ能力</th>
                        <th>論理デバッグ能力</th>
                        <th>アルゴリズムの設計と実装</th>
                        <th>コードの可読性</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <th scope="row">レベル5</th>
                        <td><ul class="mb-0"><li>エラーメッセージを適切かつ正確に解釈できる。</li><li>文法エラーを迅速に解決できる。</li></ul></td>
                        <td><ul class="mb-0"><li>効果的なテストを実施し、論理エラー箇所を解決する。</li><li>論理エラーを迅速・正確に特定し、冗長性を含まずにコードを実装できる。</li></ul></td>
                        <td><ul class="mb-0"><li>課題のアルゴリズムを完全に設計し、効率性・正確性の高い解法を編み出せる。</li><li>課題の特性に合わせてデータ構造を設計・実装できる。</li></ul></td>
                        <td><ul class="mb-0"><li>厳格な命名規則により変数・関数が命名されている。</li><li>インデント・スペース・改行の入れ方が一貫しており読みやすい。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル4</th>
                        <td><ul class="mb-0"><li>エラーメッセージを適切に解釈できる。</li><li>文法エラーを解決するのに時間がかかる。</li></ul></td>
                        <td><ul class="mb-0"><li>効果的なテストを実施し、論理エラー箇所を探り当てる。</li><li>修正が速く、方法も適切。</li></ul></td>
                        <td><ul class="mb-0"><li>課題のアルゴリズムを主要な構成要素を過不足なく設計し、解法を編み出せる。</li><li>適切なデータ構造を選択し、実装できる。</li></ul></td>
                        <td><ul class="mb-0"><li>明確な命名規則により変数・関数が命名されている。</li><li>インデント・スペース・改行の入れ方に一部ばらつきはあるが、全体として整っており読みやすい。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル3</th>
                        <td><ul class="mb-0"><li>エラーメッセージの読み取りに時間がかかる。</li><li>文法エラーを時間はかかるが解決できる。</li></ul></td>
                        <td><ul class="mb-0"><li>テストケースにより論理エラーを把握できる。</li><li>修正に多少の時間を要する場合がある。</li></ul></td>
                        <td><ul class="mb-0"><li>課題のアルゴリズムを設計し、正しく実装できる。</li><li>基本的なデータ構造の使用はできる。</li></ul></td>
                        <td><ul class="mb-0"><li>命名規則に一部改善の余地があるが、理解は可能。</li><li>インデント・スペース・改行に軽微な乱れが見られるが、全体として読みやすさは維持されている。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル2</th>
                        <td><ul class="mb-0"><li>エラーメッセージの解釈に苦労する。</li><li>文法エラーを解決する過程で試行錯誤が多い。</li></ul></td>
                        <td><ul class="mb-0"><li>論理エラーの原因特定に試行錯誤が多く、非効率的。</li><li>修正が部分的にしか行えないこともある。</li></ul></td>
                        <td><ul class="mb-0"><li>部分的な設計にとどまり、実装が不十分または非効率。</li><li>データ構造の基本的な使い分けができていない。</li></ul></td>
                        <td><ul class="mb-0"><li>命名規則が統一されておらず可読性が低い。</li><li>インデント・スペース・改行が不十分でわかりにくい部分が多い。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル1</th>
                        <td><ul class="mb-0"><li>文法エラーを解決できていない。</li></ul></td>
                        <td><ul class="mb-0"><li>論理エラーを特定できないため、修正できない。</li></ul></td>
                        <td><ul class="mb-0"><li>課題に沿わない実装をしている。</li><li>データ構造の選択が不適切で非効率。</li></ul></td>
                        <td><ul class="mb-0"><li>命名規則の一貫性がなく理解困難。</li><li>インデント・スペース・改行がほぼ無く可読性が著しく低い。</li></ul></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>

              <section class="sample-section rubric-section rubric-attitude">
                <h2 class="section-title mb-4">主体的に学習に取り組む態度 ルーブリック</h2>
                <div class="table-responsive">
                  <table class="table rubric-table table-bordered align-middle mb-0">
                    <thead>
                      <tr>
                        <th style="width: 10rem;">レベル</th>
                        <th>課題への粘り強さ</th>
                        <th>課題解決への意欲</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <th scope="row">レベル5</th>
                        <td><ul class="mb-0"><li>自力で多くの実行を繰り返しながら課題に取り組み、困難な問題も最後まで諦めずに解決しようと努力している。</li><li>解決策を複数検討し、効果的なものを選択しようとしている。</li></ul></td>
                        <td><ul class="mb-0"><li>エラーメッセージや実行結果を分析し、問題の本質を理解しようと努めている。</li><li>効率性や可読性を高めるためのコードの改善を積極的に行っている。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル4</th>
                        <td><ul class="mb-0"><li>自力で課題に粘り強く取り組み、困難な問題も解決に向けて努力している。</li><li>解決策をいくつか検討し、より良いものを選択しようとしている。</li></ul></td>
                        <td><ul class="mb-0"><li>エラーメッセージや実行結果を分析し、問題の理解に努めている。</li><li>コードの改善への工夫が見られる。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル3</th>
                        <td><ul class="mb-0"><li>指示された課題に自力で取り組み、問題解決に向けて努力している。</li><li>問題解決のために、いくつかの解決策を検討している。</li></ul></td>
                        <td><ul class="mb-0"><li>エラーメッセージや実行結果から、問題点を把握しようとしている。</li><li>コードの改善が見られる。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル2</th>
                        <td><ul class="mb-0"><li>課題に自力で取り組むものの、途中で諦めてしまうことが多い。</li><li>解決策を検討するものの、表面的な修正にとどまっている。</li></ul></td>
                        <td><ul class="mb-0"><li>エラーメッセージを正しく理解できてない。</li><li>コードの改善に取り組んでいる。</li></ul></td>
                      </tr>
                      <tr>
                        <th scope="row">レベル1</th>
                        <td><ul class="mb-0"><li>課題にほとんど取り組もうとしない。</li><li>解決策を検討せず、安易に人に聞いたり、答えを写したりする。</li></ul></td>
                        <td><ul class="mb-0"><li>エラーメッセージを無視する、または意味を理解しようとしない。</li><li>コードの改善がほとんど見られない。</li></ul></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
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

  if (!document.querySelector('.teacher-rubric-fab')) {
    document.body.insertAdjacentHTML('beforeend', rubricButtonHTML);
  }

  if (!document.getElementById('teacherRubricModal')) {
    document.body.insertAdjacentHTML('beforeend', rubricModalHTML);
  }

  setActiveSidebarMenu();
}

function setActiveSidebarMenu() {
  const path = window.location.pathname;
  const pageMap = {
    '/teacher/account/account.html': 'account',
    '/teacher/progress/progress.html': 'progress',
    '/teacher/exercise/exercise.html': 'exercise',
    '/teacher/distribution/distribution.html': 'distribution',
    '/teacher/submission/submission.html': 'submission',
    '/teacher/evaluation/evaluation.html': 'evaluation',
    '/teacher/survey/survey.html': 'survey',
    '/teacher/task/task.html': 'task',
    '/teacher/prompt/prompt.html': 'prompt'
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

