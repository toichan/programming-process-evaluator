# 課題進捗確認 実装計画

## 目的と範囲
- 利用者に提供する動作: 教師が担当学校・クラスの課題別進捗を、検索・絞り込み・並べ替え・詳細・CSVで確認する。
- 今回実装する範囲: プロトタイプの構成と操作を保ち、DBの担当範囲内の割当・在籍・参加・提出・評価・活動ログ・最新研究同意から表示を構成する。画面データは20秒間隔で再取得する。
- 今回対象外: 進捗データの更新操作、評価/提出操作。2026-10-08の追加指示により未提出下書きの閲覧・ダウンロードを実装対象に含める。

## 必読資料
- 機能仕様書: [課題進捗確認機能](../../function-specification.md)
- 状態ルール: [教師向け進捗状態ルール](../../state-rules/teacher/progress-state-rules.md)
- DB 設計・テーブル定義: [table-definitions.md](../../database-design/table-definitions.md)
- クラス図 / その他: [実装契約](../implementation-contract.md)、既存の教師権限/認証実装
- 画面遷移図 / プロトタイプ: [progress.html](../../../screen-flow-diagram/webapp/WEB-INF/teacher/progress/progress.html)、[progress.js](../../../screen-flow-diagram/webapp/js/teacher/progress/progress.js)、[progress.css](../../../screen-flow-diagram/webapp/css/teacher/progress/progress.css)。参照専用で編集しない。

## 前提・未決事項
- 合意済み前提:
  - 教師の学校・クラスアクセスと `task-progress` 機能権限の両方をサーバーで検証する。
  - 対象は公開済み/公開終了割当と、それ以前の学習記録が残る割当。未公開/将来公開の割当は表示しない。
  - 在籍中の生徒のみ一覧に含める。参加記録がなければ「未着手」、期限超過で未着手/進行中なら「要対応」とする。
  - `progress_status` を基本とし、最新提出版の最新評価が `failed` / `needs_revision` なら「要対応」を優先する。評価済み提出は「完了」、評価中は「評価待ち」として、提出と評価を混同しない。
  - 研究同意は最新 `consent_records` を表示し、`declined` / `withdrawn` はUIの「不同意」にまとめる。学習・評価アクセスは同意で制限しない。
  - プロトタイプの最新コード枠・CodeMirror・履歴カードを再現する。最新コードは参加記録の最終保存下書きを優先し、提出確定後または下書きなしの場合は最新提出版を表示する。表示元を明示し、ダウンロードも詳細と同じ認可を再実施する。
- 実装契約上の関連 ID: なし。既存DB列だけを利用し、migrationを追加しない。
- 未決事項・影響・確認が必要な相手: なし。追加指示に従い既存の下書き保存列を利用する。

## ユースケースとデータ契約
| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 一覧表示/再取得 | `task-progress` が有効な教師、許可された学校のactiveクラス | なし | 割当、active在籍、参加、最新提出/評価、最新同意 | なし | なし | 状態付き行と集計。未認証/権限外は拒否、DB障害はサービスエラー |
| 検索・絞り込み・並べ替え | 同上（取得済み行のみ） | 学校、クラス、課題、難易度、状態、同意、ID/課題検索、sort key | 画面内の一覧 | なし | なし | 一覧・集計を即時更新 |
| 詳細表示 | 同上、当該課題クラスも許可範囲内 | assignment ID、生徒内部ID | 対象参加、コード活動/実行/提出/評価履歴 | なし | なし | 詳細モーダル。未着手は活動なしを表示 |
| CSV出力 | 同上 | 現在表示中の絞り込み結果 | 画面内の一覧 | なし | なし | BOM付きUTF-8 CSVを保存 |

## 実装タスク
1. [x] DB migration / 初期データ: 既存DB列のみ。追加なし。
2. [x] Entity / DTO / DAO: 進捗一覧・詳細と活動履歴の型付き読取を実装。
3. [x] Control とトランザクション: 教師ロール、機能権限、学校/クラス範囲を検証。
4. [x] Servlet / 入力検証: 一覧・詳細JSON・直接URLの認証、識別子検証、CSRF不要のGET限定。
5. [x] JSP / CSS / JavaScript: プロトタイプを基準に実データ、filter/sort、集計、詳細、CSV、20秒再取得を接続。
6. [x] 認可・監査・エラー処理: 読取専用。担当外・無効クラスを拒否し、失敗を明示。
7. [x] テスト・手動確認: Gradle、専用MySQL統合テスト、認証済みブラウザーで受入確認。

## 変更予定ファイル
- 作成: `src/main/java/entity/TeacherProgressRow.java`, `TeacherProgressActivity.java`, `TeacherProgressDetail.java`, `TeacherProgressDao.java`, `TeacherProgressControl.java`, `src/main/java/servlet/teacher/TeacherProgressServlet.java`, `src/main/webapp/WEB-INF/teacher/progress/progress.jsp`, `src/main/webapp/css/teacher/progress/progress.css`, `src/main/webapp/js/teacher/progress/progress.js`, focused tests.
- 変更: `src/main/java/dao/TeacherPermissionDao.java`, `src/main/webapp/WEB-INF/teacher/shared/navigation.jspf`, teacher navigation summary if needed, this plan, implementation roadmap.
- 本実装の画面/UI変更: プロトタイプのレイアウト/文言/操作を再現。固定データをDBデータへ置換し、実DB状態に必要な「評価待ち」「完了」「要対応」を扱う。最新コードの表示と保存、モーダルのCSS適用範囲を修正する。プロトタイプ自体は変更しない。

## 追加作業（2026-10-08、ユーザー指示）

- T-PROG-CODE-001（REQ-PROG-CODE-001）: 最終保存下書き・最新提出版のDTO/DAO/Controlと認可付きGETダウンロード。参加記録なし・下書きなし・最新提出版・再提出編集中の下書き優先・空文字コード・権限外をテストする。
- T-PROG-CODE-002（REQ-PROG-CODE-002）: プロトタイプ準拠の詳細モーダル、読取専用CodeMirror、更新日時と保存ボタン、失敗時の共通feedback。通常幅・狭幅・コード取得・保存の実HTTPとブラウザーで確認する。
- T-PROG-CODE-003（REQ-PROG-CODE-003）: 課題編集画面の仮のナビゲーション権限を廃し、他教師画面と同じ実権限読取に統一する。実装済み機能の導線を実画面で確認する。

## 検証計画
- ビルド / テストコマンド: `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test --no-daemon --console=plain --warning-mode all`
- 正常系: DBにある担当クラス/課題/生徒を一覧化し、詳細・活動履歴・CSVと更新後の再読込が一致する。
- 状態遷移・操作可否: 未着手、進行中、提出済み、評価待ち、完了、要対応をDB状態から表示する。GET以外の状態変更は提供しない。
- 権限境界・直接 URL: task-progress無効、別学校/別クラス、生徒ロール、無効アカウントを拒否。
- DB保存後の再読込: 既存データを参照し、表示がリロード後も同じDB状態に一致することを確認する（本機能は読み取り専用）。
- 異常系: 不正ID、権限外詳細、空の一覧、DB障害、外部CDN未読込を確認する。
- 実行した環境確認: Docker daemon は利用可能、Node.js は未導入。Playwright は利用できないため、要求された画面確認は認証済み統合ブラウザーツールで実施した。JDK 21 は `JAVA_HOME` を指定してGradleを実行した。

## Testing Strategy
- appType: server-rendered HTML (JSP) with client-side filtering and modal details.
- Critical user journeys:
  1. 教師が進捗画面を開き、担当クラスの実データと集計を確認する。
  2. 学校/クラス/課題/状態/同意条件で絞り込み、CSVに同じ行だけが含まれる。
  3. 一覧から詳細を開き、活動/実行/評価履歴が参加記録にひもづく。
  4. 再取得で変更されたDB状態が表示され、検索条件とsortが維持される。
  5. 権限のない教師/学校/クラスへの直接URLが拒否される。
- primaryValidationStack: Gradle unit + MySQL-backed integration tests (Docker available) + authenticated browser acceptance.
- fallbackMatrix:
  - infra-tier: Docker/MySQL available; no fallback planned.
  - browser-tier: Integrated browser tool for requested visual/interaction acceptance. Node.js is unavailable, so Playwright cannot run; no browser-rendering fallback is considered equivalent.
- Environment requirements: JDK 21, Gradle 8.14, Docker/MySQL, running web app, authenticated browser.
- knownGaps: Node.js/Playwright automation unavailable; JDK 21 is installed but not auto-detected by Gradle, so `JAVA_HOME` must be set.
- Test data strategy: existing DB integration-test fixture conventions; isolate test IDs and clean inserted rows, never depend on pre-existing demo rows for assertions.
- Acceptance criteria: correct data/aggregation/state mapping, no unauthorized data, persistence across refresh, filter/sort/export consistency, JSP renders without errors.
- Validation review expectations: report exact commands and exits, check absence of fixed sample IDs/code/history, verify status mappings and authorization in DAO and detail endpoint.
- Unit/integration test infrastructure: existing Gradle/JUnit 5 and MySQL-backed test setup; no dependencies/schema changes. Use seeded UUID-named test entities with cleanup. Browser validation runs against the running app via the integrated browser tool; do not replace it with static file inspection.

## 検証結果（2026-10-08）
- `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test --no-daemon --console=plain --warning-mode all` — exit 0。69 suites、284 tests、99 skipped、失敗/エラー0。
- 専用DBをCompose MySQLに一時作成し、Flyway migration後に `TeacherProgressStatusTest`、`TeacherProgressDaoTest`、`TeacherProgressDatabaseTest` を実行 — exit 0、8 tests。担当範囲一覧、活動詳細、時間表示、機能権限失効、担当学校の無効化、生徒ロール、不正IDを確認。テストDBは削除し、`log_bin_trust_function_creators` は0へ復元した。
- MySQL実行で評価履歴SQLの別名が構文エラーになる不具合を検出し、予約語と衝突しない `evaluation_rank` に修正。JSP ELがJava recordのアクセサーを解決できず実データ行で500になる問題も検出し、JSP互換getterを追加。テストfixtureの学生コードも列長32文字以内へ修正。
- 認証済み `/teacher/progress` を統合ブラウザーで確認。共有8080の空一覧に加え、専用テストDBに合成レコードを作成した一時アプリで実データ行・詳細モーダル・手動保存履歴・サマリー集計を確認。検索/状態/同意フィルター、絞込後CSVのヘッダーと1行、UTF-8 BOM、更新後の検索/同意条件保持、担当外詳細404、空状態メッセージ、画面JavaScript例外なしを確認した。一時アプリ/テストDBは停止・削除済み。
- 20秒自動更新はブラウザーがバックグラウンド扱い（`visibilityState=hidden`）のため通常待機では発火しなかった。可視状態を模擬して更新1回を確認し、手動「更新」も確認済み。ブラウザーのダウンロードイベント自体は統合ブラウザーから通知されなかったが、CSV Blobの実データ内容・BOM・成功通知を確認した。
- 初回受入時点では未提出コードの取得・ダウンロードは未実装だった。2026-10-08の追加指示への対応は下記の記録を参照。

## 保存コード・提出コード・モーダル・メニューの追加受入（2026-10-08）

- T-PROG-CODE-001〜003を完了。参加記録の保存下書きを優先し、学習状態が提出確定済み、または下書きなしの場合は最新提出版を取得する。読取専用の詳細JSONと `GET /teacher/progress?view=download&assignmentId=…&studentUserId=…` は同じ担当範囲・機能権限検証を通す。提出・評価・同意・コードログの更新は行わない。
- CodeMirrorのPython色分け・行番号・読取専用表示、保存/提出元と日時、`.py`保存を接続。モーダルが画面本体の外にあるため適用されていなかったCSSをモーダルIDに限定して修正し、プロトタイプのカード、ヘッダー背景、コード領域、履歴テーブルを再現した。400px以下だけは見出しとボタンを折り返し、狭幅で文字が分断される問題を防ぐ。プロトタイプ自体は変更していない。
- 課題編集Servletに残っていた仮の `TeacherNavigationSummary` を共通の実権限取得へ置換。権限のある実装済み5機能をリンク表示する。未実装の授業演習コード確認・提出課題確認・評価確認・アンケート結果確認は利用可能に見せない。
- 最終自動確認: `docker compose run --rm --no-deps -e DB_NAME=ppe_teacher_progress_test_code_20261008 -e TEACHER_PROGRESS_DB_TEST=true -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home app gradle --offline --project-cache-dir=/home/gradle/.gradle/progress-code-db-project test --tests '*TeacherProgress*Test' --tests '*TeacherTaskServletTest' war --no-daemon --warning-mode all` は16件成功、skip/失敗/エラー0、WAR成功。専用DB4件には下書きなし、保存コード、最新提出版の選択、再提出時の下書き優先、空文字の保存、HTMLを含むコード、機能権限取消、学校権限取消を含む。
- 専用DBへのFlyway初回実行はV11のトリガー作成権限不足で失敗した。共有MySQLのグローバル権限や設定は変更せず、専用DBだけを作り直して、適用済みの開発DBからスキーマのみを複製して検証した。検証後は専用DBのみ削除済み。この結果は新規環境へのmigration成功を示すものではない。
- 認証ブラウザー: デモ生徒の下書き表示、最新提出版の表示、ダウンロードボタンによる200応答・UTF-8コード本文・attachmentのPythonファイル名、未着手の空表示・保存不可、一覧からの詳細切替を確認。存在しない対象へのダウンロードは404、不正IDは400。1400px幅では800pxモーダル、読取専用CodeMirror高さ240pxを確認し、390px幅ではモーダル/本文の横溢れなしを確認した。課題編集から生徒アカウント管理とコード配信へ200で遷移し、進捗確認リンクも利用可能になった。
- `git diff --check` は成功。DAO/Control/コードDTO/JSのエディター診断はエラーなし。ServletではエディターのGson依存解決エラーが残るが、コンテナGradleのコンパイル・WARと実HTTPは成功した。OSの保存ダイアログ・実ファイル保存先は未確認。
- 8080へ反映済み。再起動および開発サーバーの自動再読込後はユーザーに再ログインを依頼して確認した。デモ用データは保持し、他の検証環境は停止していない。

## 完了条件
- [x] 承認済み機能仕様および関連する状態・DB/API契約に合致する
- [x] 関連する状態ルールと DB 状態値に合致する
- [x] 業務データを DB から読み取り、画面は参照専用とする
- [x] サーバー側でロール・所有範囲・状態遷移を検証する
- [x] 仮データ・擬似成功処理・秘密情報を残していない
- [x] 実行した確認結果と未確認事項を記録した
