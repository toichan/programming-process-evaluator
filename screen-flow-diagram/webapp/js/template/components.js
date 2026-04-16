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
                  <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14zm0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16z"/>
                  <path d="M8 4a.5.5 0 0 1 .5.5v3h3a.5.5 0 0 1 0 1h-3v3a.5.5 0 0 1-1 0v-3h-3a.5.5 0 0 1 0-1h3v-3A.5.5 0 0 1 8 4z"/>
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
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item text-center text-primary fw-semibold" href="#">全ての通知を見る</a></li>
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
                <span class="d-none d-lg-inline">山田太郎</span>
              </a>
              <ul class="dropdown-menu dropdown-menu-end border-0 shadow-lg" aria-labelledby="userDropdown">
                <li class="dropdown-header fw-semibold">生徒: 山田太郎</li>
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