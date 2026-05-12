window.PPEComponents = {
  header: `
    <nav class="navbar navbar-expand-lg">
      <div class="container-fluid">
        <a class="navbar-brand d-flex align-items-center" href="../menu/home.html">
          <svg class="me-2" width="32" height="32" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
            <path d="M4 4.5C4 3.67 4.67 3 5.5 3h13c.83 0 1.5.67 1.5 1.5v15c0 .83-.67 1.5-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5v-15zM5.5 4a.5.5 0 0 0-.5.5V8h15V4.5a.5.5 0 0 0-.5-.5h-14zM5 9v10.5c0 .28.22.5.5.5h13a.5.5 0 0 0 .5-.5V9H5z"/>
            <path d="M8.75 13.5L7.25 12l1.5-1.5m6.5 3L16.75 12l-1.5-1.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M11 13.5h2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          </svg>
          <div class="brand-text">
            <span class="brand-line d-block">Programming</span>
            <span class="brand-line d-block">Process</span>
            <span class="brand-line d-block">Evaluator</span>
          </div>
        </a>

        <button class="navbar-toggler border-0" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav" aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>

        <div class="collapse navbar-collapse" id="navbarNav">
          <ul class="navbar-nav me-auto">
            <li class="nav-item">
              <a class="nav-link d-flex align-items-center" href="../menu/home.html">
                <svg class="me-1" width="18" height="18" fill="currentColor" viewBox="0 0 16 16">
                  <path d="M8.707 1.5a1 1 0 0 0-1.414 0L.646 8.146a.5.5 0 0 0 .708.708L8 2.207l6.646 6.647a.5.5 0 0 0 .708-.708L13 5.793V2.5a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5v1.293L8.707 1.5Z"/>
                  <path d="m8 3.293 6 6V13.5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 13.5V9.293l6-6Z"/>
                </svg>
                ホーム
              </a>
            </li>
            <li class="nav-item dropdown">
              <a class="nav-link dropdown-toggle d-flex align-items-center" href="#" id="tasksDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                <svg class="me-1" width="18" height="18" fill="currentColor" viewBox="0 0 16 16">
                  <path d="M2 2.75A.75.75 0 0 1 2.75 2h10.5a.75.75 0 0 1 0 1.5H2.75A.75.75 0 0 1 2 2.75zM2 8a.75.75 0 0 1 .75-.75h10.5a.75.75 0 0 1 0 1.5H2.75A.75.75 0 0 1 2 8zm0 5.25a.75.75 0 0 1 .75-.75h10.5a.75.75 0 0 1 0 1.5H2.75A.75.75 0 0 1 2 13.25z"/>
                  <path d="M4 2.75a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0zm0 5.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0zm0 5.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0z"/>
                </svg>
                課題
                <span class="badge bg-warning text-dark ms-1">3</span>
              </a>
              <ul class="dropdown-menu border-0 shadow-lg" aria-labelledby="tasksDropdown">
                <li><a class="dropdown-item d-flex align-items-center" href="../editor/editor.html"><span class="badge bg-success me-2">済</span>変数とデータ型</a></li>
                <li><a class="dropdown-item d-flex align-items-center" href="../editor/editor.html"><span class="badge bg-warning me-2">中</span>条件分岐</a></li>
                <li><a class="dropdown-item d-flex align-items-center" href="../editor/editor.html"><span class="badge bg-danger me-2">未</span>繰り返し処理</a></li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item text-primary fw-semibold" href="../menu/home.html">すべての課題をみる</a></li>
              </ul>
            </li>
            <li class="nav-item">
              <a class="nav-link d-flex align-items-center" href="../survey/consent.html">
                <svg class="me-1" width="18" height="18" fill="currentColor" viewBox="0 0 16 16">
                  <path d="M8 1a3 3 0 0 0-3 3v1H4a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-1V4a3 3 0 0 0-3-3zm-2 4V4a2 2 0 1 1 4 0v1H6zm2 3a.75.75 0 0 1 .75.75v1.5a.75.75 0 0 1-1.5 0v-1.5A.75.75 0 0 1 8 8z"/>
                </svg>
                研究同意
              </a>
            </li>
          </ul>

          <ul class="navbar-nav">
            <li class="nav-item ms-lg-2">
              <button type="button" class="nav-link rubric-trigger" data-bs-toggle="modal" data-bs-target="#rubricModal" aria-label="ルーブリックを開く">
                <svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                  <path d="M4 .5A1.5 1.5 0 0 0 2.5 2v12A1.5 1.5 0 0 0 4 15.5h8a1.5 1.5 0 0 0 1.5-1.5V2A1.5 1.5 0 0 0 12.5.5h-8zM4 1.5h8a.5.5 0 0 1 .5.5v12a.5.5 0 0 1-.5.5H4a.5.5 0 0 1-.5-.5V2a.5.5 0 0 1 .5-.5z"/>
                  <path d="M5 4.5h6v1H5v-1zm0 2.5h6v1H5v-1zm0 2.5h4v1H5v-1z"/>
                </svg>
                ルーブリック
              </button>
            </li>
            <li class="nav-item dropdown">
              <a class="nav-link position-relative" href="#" id="notificationsDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                <svg width="20" height="20" fill="currentColor" viewBox="0 0 16 16">
                  <path d="M8 16a2 2 0 0 0 2-2H6a2 2 0 0 0 2 2z"/>
                  <path d="M8 1.918l-.797.161A4.002 4.002 0 0 0 4 6c0 .628-.134 2.197-.459 3.742-.16.767-.376 1.566-.663 2.258h10.244c-.287-.692-.502-1.49-.663-2.258C12.134 8.197 12 6.628 12 6a4.002 4.002 0 0 0-3.203-3.92L8 1.917zM14.22 12c.223.447.481.801.78 1H1c.299-.199.557-.553.78-1C2.68 10.2 3 6.88 3 6c0-2.42 1.72-4.44 4.005-4.901a1 1 0 1 1 1.99 0A5.002 5.002 0 0 1 13 6c0 .88.32 4.2 1.22 6z"/>
                </svg>
                <span class="notification-badge position-absolute top-0 start-100 translate-middle-y badge rounded-pill bg-danger" style="font-size: 0.6rem;">2<span class="visually-hidden">unread notifications</span></span>
              </a>
              <ul class="dropdown-menu dropdown-menu-end border-0 shadow-lg" aria-labelledby="notificationsDropdown">
                <li class="dropdown-header fw-semibold">通知</li>
                <li><a class="dropdown-item d-flex align-items-center" href="#"><div class="me-2"><span class="badge bg-success">済</span></div><div><div class="fw-semibold">課題完了</div><small class="text-muted">変数とデータ型の課題が評価されました</small></div></a></li>
                <li><a class="dropdown-item d-flex align-items-center" href="#"><div class="me-2"><span class="badge bg-warning">新</span></div><div><div class="fw-semibold">新しい課題</div><small class="text-muted">条件分岐の課題が追加されました</small></div></a></li>
              </ul>
            </li>
            <li class="nav-item dropdown">
              <a class="nav-link dropdown-toggle d-flex align-items-center" href="#" id="userDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                <div class="bg-white rounded-circle d-flex align-items-center justify-content-center me-2" style="width: 32px; height: 32px;">
                  <svg width="16" height="16" fill="var(--bs-primary)" viewBox="0 0 16 16">
                    <path d="M11 6a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"/>
                    <path fill-rule="evenodd" d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8zm8-7a7 7 0 0 0-5.468 11.37C3.242 11.226 4.805 10 8 10s4.757 1.225 5.468 2.37A7 7 0 0 0 8 1z"/>
                  </svg>
                </div>
                <span class="d-none d-lg-inline">st001</span>
              </a>
              <ul class="dropdown-menu dropdown-menu-end border-0 shadow-lg" aria-labelledby="userDropdown">
                <li class="dropdown-header fw-semibold">生徒：st001</li>
                <li><span class="dropdown-item-text text-muted small">学校：国際中等</span></li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item d-flex align-items-center text-danger" href="../account/login.html">
                  <svg class="me-2" width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                    <path fill-rule="evenodd" d="M10 12.5a.5.5 0 0 1-.5.5h-8a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5h8a.5.5 0 0 1 .5.5v2a.5.5 0 0 0 1 0v-2A1.5 1.5 0 0 0 9.5 2h-8A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h8a1.5 1.5 0 0 0 1.5-1.5v-2a.5.5 0 0 0-1 0v2z"/>
                    <path fill-rule="evenodd" d="M15.854 8.354a.5.5 0 0 0 0-.708l-3-3a.5.5 0 0 0-.708 0l-3 3a.5.5 0 0 0 0 .708l3 3a.5.5 0 0 0 .708 0l3-3zM8.646 7l.646.646.646-.646-.646-.646-.646.646z"/>
                  </svg>
                  ログアウト
                </a></li>
              </ul>
            </li>
          </ul>

          <div class="modal fade rubric-modal" id="rubricModal" tabindex="-1" aria-labelledby="rubricModalLabel" aria-hidden="true">
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
                      <h2 class="modal-title h4 mb-1" id="rubricModalLabel">ルーブリック</h2>
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
        </div>
      </div>
    </nav>
  `,

  footer: `
    <footer class="bg-light py-4 mt-5 border-top">
      <div class="container text-center">
        <p class="text-muted mb-0">&copy; 2026 Takumi Toida. All rights reserved.</p>
      </div>
    </footer>
  `
};