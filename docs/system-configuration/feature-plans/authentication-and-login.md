# 初期利用者・認証・ログイン 実装計画

現在の状態（2026-10-07更新）: **認証・学校管理・工程11の教師アカウント管理と管理画面統合は実装・検証済み**。管理者の固定ID `admin` への修正は末尾の記録を参照する。以下は初回計画と追加バッチの履歴であり、旧未実装記述を現在の状態として扱わない。次は[実装ロードマップ](../implementation-roadmap.md)工程12の生徒アカウント管理。教師本人のアカウント管理は工程12aへ仕様のみ追加し未実装。本番TLS/cookieや実管理者アカウント等の未確認事項は保持する。

## 目的と範囲
- 利用者に提供する動作: 生徒・教師・管理者がDB上の認証情報でログインし、規定状態の利用者だけがセッションを取得できる。
- 今回実装する範囲: 生徒 / 教師・管理者ログイン、ログアウト、30分セッション、セッション固定化対策、ロール保護、レベル2の初回パスワード変更、失敗履歴、5回連続失敗時の30分ロック、監査付きロック解除Control/API、および初期管理者作成手順。業務画面の完成までは、ログイン後にロール別の仮ページを表示する。
- 当初の対象外: 学校・クラス・生徒の一括登録、管理者向けアカウント管理画面、ログイン後の各業務画面。2026-10-03追加バッチでは学校管理のみ対象へ追加する。認証後の移動先は後続工程の画面追加に合わせる。

## 必読資料
- 機能仕様書: `docs/function-specification.md` のアカウント機能、認証・認可・セッション要件
- 画面遷移図 / プロトタイプ: `docs/screen-flow-diagram.md`、生徒 / 教師ログイン画面
- 状態ルール: `docs/state-rules/admin/account-state-rules.md`
- DB 設計・テーブル定義: `docs/database-design/README.md`、`users`、`student_profiles`、`login_history`、`audit_logs`
- その他: `docs/system-configuration/implementation-contract.md` IC-009、ロードマップ5

## 前提・未決事項
- 2026-10-03管理者プロトタイプ整理: [管理者画面](../../../screen-flow-diagram/webapp/WEB-INF/admin/management.html)へ教師管理/学校管理の2タブを統合し、作成・編集・履歴をモーダル化。旧ホーム・独立学校管理のプロトタイプページを削除し、教師ログインの管理者遷移先を更新済み。本実装は管理者ホーム→学校管理のまま、教師管理・権限UIは未実装。統合はロードマップ工程12かつユーザーのローカル反映指示まで保留し、今回の整理では`src/main`・DBを変更していない。
- プロトタイプ確認: 新URLへの管理者ログイン遷移、2タブの存在、`?tab=schools`の直接表示、旧HTML参照が残っていないこと、`git diff --check`成功を確認。統合UIの検索・作成/編集・履歴・学校ロック表示・確認キャンセル・375px表示は[画面遷移図](../../screen-flow-diagram.md)の記録を参照。CSV・削除・パスワード再設定の全操作回帰は未実施。これらは本実装のDB検証ではない。
- 2026-10-03学校管理CSS修正: ページCSS未指定と共通テンプレートの生徒限定読込を修正。学校管理は既存adminプロトタイプの背景・見出し・パネル・管理者ラベルのCSSを必要部分のみ再利用する。ページCSSはロールに関係なく明示指定時に読み込む。
- 2026-10-03学校管理の導線改善: 管理者のホームは管理メニューとして表示し、学校管理への入口を説明・右矢印付きリンクに変更。サブページはパンくず・明示的な戻るリンク、詳細は一覧への戻るリンクを設ける。通常教師の仮ホーム・保存処理・ロール境界は変更しない。
- 学校管理追加バッチ（2026-10-03）: REQ-SCHOOL-001 admin限定の学校一覧・登録・名前更新、REQ-SCHOOL-002 学校単位のレベル適用と生徒登録後の不可逆ロック、REQ-SCHOOL-003 監査・CSRF・競合検証を実装する。教師の学校アクセス権限管理・クラス/生徒作成UI・学校削除は今回対象外。
- 追加タスク: T-SCHOOL-001 仕様/状態/DB/画面遷移整合 → T-SCHOOL-002 V11移行・学校DAO/Controlと入力テスト → T-SCHOOL-003 admin Servlet/JSP/共有確認UI・導線 → T-SCHOOL-004 専用DB・ブラウザー・ビルド検証。001は全要件、002は001/002/003、003は001/003、004は全要件に対応する。移行は既存所属から一意に学校・レベルを決定できることを事前確認し、所属なし旧データは保持する。
- 学校管理追加バッチの結果: T-SCHOOL-001〜004完了。V11を専用DBで検証後、ローカル開発DBへ適用し`flywayValidate`成功。移行時のみ管理権限を使用し、通常処理はアプリユーザーで検証。認証と生徒アカウント表示は所属学校のレベルを読み、プロフィール互換値の個別改変・学校変更・学校レベルのロック解除はDBでも拒否する。
- 検証コマンド・結果: 専用DBで`SCHOOL_DB_TEST=true gradle test --tests control.admin.SchoolDatabaseTest --tests entity.SchoolInputTest --rerun-tasks --warning-mode all`成功（5件、失敗0、skip0）。別の専用DBで`EVALUATION_DB_TEST=true gradle test --tests control.evaluation.EvaluationDatabaseTest --rerun-tasks --warning-mode all`成功（5件成功、実API1件skip）、`CONSENT_DB_TEST=true gradle test --tests control.student.ConsentDatabaseTest --rerun-tasks --warning-mode all`成功（1件）。通常環境で`docker compose exec -T app gradle test build --warning-mode all`成功（52件中41件成功・明示ゲートの11件skip）、最終ServletのUTF-8対応後の`docker compose exec -T app gradle war --warning-mode all`と`git diff --check`も成功。
- 認証済みUI/HTTP検証: 合成adminによる一覧・登録・詳細・名前変更・生徒登録前のレベル変更・登録後の変更禁止、キャンセル、保存後のDB再読込・監査を確認。古い版/確認なし/レベル改ざんは400、CSRF不正と教師/生徒の管理URLは403、未ログインは302。日本語学校名の保存・再表示、375px幅でページ全体の横方向はみ出しなしを確認。教師の生徒作成プロトタイプでも学校別レベルの参照専用表示を確認。共有テンプレート・共通feedback使用。
- 後片付け・残件: 合成UI利用者・学校・ログを今回作成したIDだけで削除し、専用DB3個と専用付与権限を除去。教師のクラス/生徒作成・学校権限設定UIは未実装で、今後は学校IDの指定とサーバーでの学校権限検証を必須とする。学校間移動・学校削除は対象外。共通モーダルの既存フォーカス警告、移行権限エラー、検証スクリプト・fixture後片付けの失敗と解消は[エラーレポート](../error-report.md)参照。
- 合意済み前提: パスワードはPBKDF2-HMAC-SHA256で照合する（ユーザー選択）。新規 / 変更パスワードは機能仕様書第49版の「英大文字・英小文字・数字・記号の4種類中3種類以上、8〜32文字」を満たす。
- 実装契約上の関連 ID: IC-009。停止 / 削除アカウントは拒否し、利用者向け理由は共通化して内部履歴では理由を区別する。
- 合意済みDB方針: `users.consecutive_login_failures` と `users.login_locked_until` をV3マイグレーションで追加し、教師解除を `audit_logs` に記録する。ロック解除の操作画面はユーザー / アカウント管理工程で追加し、この工程ではControl/APIまでを実装する。教師の解除は、有効な教師アカウントに加え、`account-management` 機能権限と対象生徒の在籍校への有効な学校権限を要求する。

## ユースケースとデータ契約
| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 生徒ログイン | 生徒 | login ID、password、CSRF token | `users`、`student_profiles`、失敗 / ロック状態 | `login_history`、失敗回数・ロック期限 | active・未ロックなら認証。レベル2の初回状態が未完了なら強制変更画面へ | 成功時は強制変更または認証確認用仮ホームへ。失敗時は資格情報を特定しない共通文言 |
| 教師 / 管理者ログイン | 教師・管理者 | login ID、password、CSRF token | `users`、失敗 / ロック状態 | `login_history`、失敗回数・ロック期限 | active・未ロックなら認証 | 成功時は認証確認用仮ホームへ |
| ログアウト | 認証済み利用者本人 | CSRF token | 現行セッション | `login_history` | セッションを完全に破棄 | ログイン画面へ戻る |
| 強制変更中のアクセス | レベル2で変更未完了の生徒 | URL / request | セッションと生徒プロフィール | なし | パスワード変更とログアウト以外を拒否 | 強制変更画面へ誘導 |
| 教師によるロック解除 | 許可された教師 | 対象利用者 | 対象アカウント | ロック状態と `audit_logs` | ロック解除 | 成否を共通feedbackで表示。操作履歴を残す |

## 実装タスク
1. [x] DB migration / 初期データ: V3マイグレーションを確認し、初期管理者を対話式に一度だけ作るGradleタスクを追加する。
2. [x] Entity / DTO / DAO: 認証ユーザー、ログイン履歴、連続失敗 / ロック状態の読み書きを用意する。
3. [x] Control とトランザクション: PBKDF2照合、ロール / account status / 強制変更条件、ロック判定、履歴記録を実装する。
4. [x] Servlet / Filter / 入力検証: 生徒 / 教師ログイン、ログアウト、認証・ロール・強制変更Filter、CSRF検証を実装する。
5. [x] JSP / CSS / JavaScript: 生徒 / 教師ログイン画面、強制変更画面、ロール別の認証確認用仮ホームを追加する。
6. [x] 認可・監査・エラー処理: 停止 / 削除 / 未知ID / 誤パスワードの外部文言を共通化し、内部理由と教師の解除操作を記録する。教師解除Controlでは有効なactor role・機能権限・対象校を再検証する。
7. [x] テスト・手動確認: パスワード要件 / ハッシュ、教師ログイン、セッション固定化対策、ロール境界、ログアウト、レベル2強制変更、5回失敗ロック、CSRF拒否を自動テストおよび一時DB fixtureで確認する。

## 変更予定ファイル
- 作成: 認証Servlet、ログイン / 強制変更JSP、ロール別仮ホーム、共有認証feedback JavaScript、初期管理者作成クラス、パスワード単体テスト
- 変更: `AuthenticationFilter`、`web.xml` セッション設定、Gradle のテスト・初期管理者タスク
- プロトタイプから再利用する表示資産: 生徒 / 教師ログイン画面の構成、パスワード表示切替、共通レイアウトとshared feedback
- プロトタイプから除去する仮データ・擬似動作: placeholder ID / パスワード、デモログイン、固定ユーザー名、機能未実装リンク

## 検証計画
- ビルド / テストコマンド: `docker compose exec -T app gradle build --warning-mode all`
- 正常系: 生徒 / 教師 / 管理者の正しい資格情報、レベル1通常ログイン、レベル2強制変更遷移を確認する。
- 状態遷移・操作可否: active / suspended / deleted、連続失敗・30分ロック・教師解除、初回パスワード変更を確認する。
- 権限境界・直接 URL: 未認証、他ロール、強制変更中の通常画面アクセスを拒否し、CSRFのない更新POSTを拒否する。
- DB保存後の再読込: ログイン履歴、失敗回数 / ロック期限、パスワードハッシュ、初回状態を確認する。
- 異常系: 未知ID、誤パスワード、停止 / 削除、接続障害、入力不正を区別し、パスワード・hashをログや画面へ出さない。
- 実施結果: `docker compose exec -T app gradle test --tests 'control.auth.*Test' --rerun-tasks --warning-mode all`（5件成功）と `docker compose exec -T app gradle build --warning-mode all` は成功。ログイン画面GETは200、認証必須画面は未認証時302、CSRFなしログインPOSTは403。使い捨ての教師・生徒fixtureでログイン、session ID更新、HttpOnly cookie、教師→生徒画面403、CSRF付きログアウト後のセッション無効化、レベル2強制変更とDB状態更新、5回連続失敗の30分ロックと正しいパスワードのロック中拒否を確認した。追加で、停止 / 削除アカウントと未知IDが同一の外部メッセージで拒否されること、期限切れロック後に正常ログインすると失敗回数とロック状態がリセットされること、教師のアカウント管理権限・対象校の有効権限によるロック解除と監査記録、および権限なし教師・別校生徒の拒否を一時DB fixtureで確認した。テスト後にfixture、認証履歴、監査履歴が残っていないことを確認した。認証単体テストは再実行も成功。
- 未確認事項: 実管理者アカウントでのログインは未実施。初期管理者作成Gradleタスクは登録済みだが、利用者が実値を対話入力するため今回はアカウント作成していない。TLS終端・本番cookie設定、業務画面における所属範囲認可は後続工程で確認する。
- 実セッション期限確認: 認証済み生徒のCookieを保持し、通信せず30分超待機した後に `/student/home` へアクセス。ログイン画面への302リダイレクトを確認した。確認用利用者、プロフィール、ログイン履歴は削除済み。

## 完了条件
- [x] 平文パスワードを永続化・ログ出力せず、salt付きPBKDF2-HMAC-SHA256を使用する。
- [x] 認証結果、ロール、アカウント状態、強制変更状態をサーバー側で検証する。
- [x] セッション固定化を防ぎ、30分で失効し、ログアウトで完全破棄する。
- [x] ログイン失敗・ロック・ロック解除を仕様どおり記録し、外部メッセージからアカウントの有無を推測できない。
- [x] 入力制約を機能仕様書・状態ルールと一致させる。
- [x] 実行した確認結果と未確認事項を記録する。

## 工程11：教師アカウント管理追加バッチ（2026-10-06）

仕様は[機能仕様書第130版](../../function-specification.md)、状態は[アカウント状態ルール](../../state-rules/admin/account-state-rules.md)、データ契約は[テーブル定義](../../database-design/table-definitions.md#users)を正本とする。初期/再設定パスワードの発行直後のみの表示・コピーはユーザー承認済み。教師の次回変更必須という旧プロトタイプ文言は廃止済み仕様と矛盾するため採用しない。

### 要件・タスク対応

| 要件ID | 仕様の参照範囲 | タスク | 実装・検証 |
|---|---|---|---|
| REQ-TEACHER-001 | 教師アカウント管理：作成・状態・再設定 | T001 仕様整合、T002 DB/Control | V20、TeacherAccountInput/Dao/Control。資格情報はハッシュのみ保存、削除非復元・履歴保持・失敗rollbackをDB検証 |
| REQ-TEACHER-002 | 学校/機能権限・認証認可 | T002 DB/Control | 9機能権限、課題/プロンプト権限の分離、学校認可、TeacherSessionControlとFilterの更新版照合。既存ログイン失効・再設定競合・既存ロック維持を検証 |
| REQ-TEACHER-003 | 一覧/検索・CSV・ログイン/操作履歴・管理画面 | T003 Servlet/JSP/JS | `/admin/management`と`/admin/teachers`、既存2タブ/モーダル、共有template/feedback。学校管理は既存SchoolControlを再利用 |
| REQ-TEACHER-004 | 保存後再読込・状態/認可/資格情報 | T004 回帰/HTTP/browser・記録 | 下記の自動テストと8080の認証browser受入 |

### 検証結果

- 全体: `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/gradle-teacher-validation app gradle --project-cache-dir=/tmp/teacher-validation-project test war --no-daemon --warning-mode all` 成功。249件中160成功、89件はDB/実API/browser fixture等の明示ゲートでskip、失敗/エラー0。WAR生成成功。
- 教師管理: `docker compose run --rm --no-deps -e DB_NAME=ppe_teacher_test_20261006 -e TEACHER_ACCOUNT_DB_TEST=true -e GRADLE_USER_HOME=/tmp/gradle-teacher-validation app gradle --project-cache-dir=/tmp/teacher-validation-project test --tests control.admin.TeacherAccountDatabaseTest --tests entity.TeacherAccountInputTest --tests control.auth.PasswordGeneratorTest --tests servlet.admin.TeacherAccountServletTest --tests control.admin.TeacherAccountControlTest --tests servlet.auth.LoginServletTest --tests 'control.teacher.TeacherPrompt*Test' --tests 'control.teacher.ReevaluationPreview*Test' --tests control.teacher.TeacherNavigationControlTest --no-daemon` 成功（19件成功、失敗/skip0）。セッションstamp/古いhashによる再認証拒否を追加後、同DBで `test --tests control.admin.TeacherAccountDatabaseTest --no-daemon` を再実行し2件成功。
- プロンプト学校認可: `docker compose run --rm --no-deps -e DB_NAME=ppe_teacher_task_test_admin20261006 -e TEACHER_TASK_DB_TEST=true -e GRADLE_USER_HOME=/tmp/gradle-teacher-validation app gradle --project-cache-dir=/tmp/teacher-validation-project flywayMigrate test --tests control.teacher.TeacherTaskDatabaseTest.promptPermissionIsIndependentButStillRequiresTheTaskSchool --tests control.teacher.TeacherTaskDatabaseTest.promptDraftUsesSharedRubricAndOptimisticVersioning --tests control.teacher.TeacherTaskDatabaseTest.reevaluationJobStatusIsScopedToTeacherAndTaskAndReportsProgress --tests control.teacher.TeacherTaskDatabaseTest.publishedTaskAllowsASeparatePromptDraftWithoutChangingTheActiveVersion --no-daemon` 成功（4件成功、失敗/skip0）。V1〜V20適用成功。
- 学校回帰: `docker compose run --rm --no-deps -e DB_NAME=ppe_school_test_teacher20261006 -e SCHOOL_DB_TEST=true -e GRADLE_USER_HOME=/tmp/gradle-teacher-validation app gradle --project-cache-dir=/tmp/teacher-validation-project test --tests control.admin.SchoolDatabaseTest --tests entity.SchoolInputTest --no-daemon` 成功（6件成功、うちDB4件、失敗/skip0）。
- 8080認証browser: 管理者作成/ログイン、教師作成・学校/権限保存、資格情報モーダル終了/再読込後の消去、再設定と旧PW拒否/新PW成功、停止/解除/削除と既存セッション失効、削除後の履歴保持、検索/CSV・CSRF403・教師/生徒の管理URL403を確認。課題編集OFF/プロンプトONの教師は課題403・プロンプト200。
- 学校タブから登録・日本語名/レベル変更をDB保存後の再表示で確認。生徒登録済み学校のレベルdisabledと既存DBによる改ざん拒否を確認。375pxでページ幅375px、作成モーダル幅359pxでページ全体の横はみ出しなし。
- ブラウザーの背景タブのクリック/アニメーション待機が停止するため、後半のモーダル検証は一時DOMのfadeクラスを除去し、実button/formのイベントと保存・表示を検証した。アニメーション自体の最終再確認と全ブラウザー/OS組合せは未実施。変更はブラウザーの再読込で消え、ソースには入れていない。
- エディターは既存Gson依存を解決できずJava診断が残るが、Compose Java21でコンパイル/テスト/WARが成功。変更JS/CSSの診断は0。外部APIは呼び出していない。本番TLS/cookie・実運用アカウントでの受入は対象外。

T001〜T004完了。今回の専用DB3個/付与権限・一時fixtureファイル・学校UI検証fixtureを削除し、routine設定0・8080/app/DB/runner稼働を確認した。合成管理者は利用者確認用に残し、削除検証済み教師は履歴付きで保持する。プロトタイプは変更せず、本実装へ反映した。次は工程12の生徒アカウント管理。

## 管理者の固定ID修正（2026-10-07）

- ユーザー再確認により管理者IDは小文字の`admin`に固定。任意IDのadminロールを認証していた実装を修正し、教師ログインでは入力ID・保存済みIDの両方が正確に`admin`であること、正しいパスワード・利用中状態・管理者ロールを要求する。管理URLもロールと固定IDを検証する。
- `admin`の大文字/小文字違いは教師IDとして登録できない。初期管理者作成タスクはID入力を廃止して固定IDを使い、表示名・パスワードのみ従来どおり対話入力する。
- 通常デモDBの既存管理者を固定IDへ修正した。内部user_idと関連履歴・パスワードハッシュを維持し、ID修正を監査へ記録。管理者を追加して1アカウント制約を破ることはしない。資格情報の実値はこの文書に記載しない。
- 検証コマンド: `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/gradle-teacher-validation app gradle --project-cache-dir=/tmp/teacher-validation-project test --tests 'control.auth.*Test' --tests entity.TeacherAccountInputTest --tests servlet.auth.LoginServletTest --tests servlet.admin.TeacherAccountServletTest war --no-daemon --warning-mode all` 成功（19件成功、失敗/skip0、WAR成功）。変更Java/テストのエディター診断0。
- restart後の8080の認証browserで正しい`admin`資格情報から統合管理画面へ遷移し、管理者ラベルも`admin`であることを確認。旧デモID・`Admin`・`ADMIN`・誤パスワードを拒否。管理画面POSTで予約ID3種類の教師作成は400、正常生徒ログインと管理URL403も確認した。
- 教師本人の任意パスワード変更は[機能仕様書第131版](../../function-specification.md)・ロードマップ工程12aへ追加しただけで、今回は画面や変更処理を実装していない。管理者再設定後の強制変更不要という既存方針は維持する。
