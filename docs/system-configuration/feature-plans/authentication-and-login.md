# 初期利用者・認証・ログイン 実装計画

現在の状態（2026-10-07更新）: **認証・学校管理・工程11/12/12aは実装・検証済み**。以下は初回計画と追加バッチの履歴であり、旧未実装記述や教師の強制変更不要方針を現在の状態として扱わない。第135版で初期/再設定後の教師本人変更を必須化した。本番TLS/cookieや実管理者アカウント等の未確認事項は保持する。

## 工程12a: 教師本人のアカウント管理

仕様のREQ-001（本人情報）、REQ-002（初回必須/任意変更）、REQ-003（認可/履歴/失効）を対象とする。既存の教師profile/passwordプロトタイプと生徒のパスワード部品を利用し、プロトタイプは変更しない。関連状態/DB契約は[状態ルール](../../state-rules/admin/account-state-rules.md)・[DB定義](../../database-design/table-definitions.md#users)を参照する。外部API連携はない。

| タスク | 対応要件 | 実装・受入 |
|---|---|---|
| T-SELF-001 | REQ-001〜003 | V22で変更必須列追加・既存教師移行。教師発行/再設定と認証DTO/Login/Filterを接続。管理者・生徒の既存運用を維持 |
| T-SELF-002 | REQ-001〜003 | 本人限定Control/DAO/Servlet。教師行をロックしてログイン版/画面版照合、照合/条件違反を通知、成功時にハッシュ/必須状態/版/履歴/監査を原子的保存 |
| T-SELF-003 | REQ-001〜003 | 教師共通ヘッダーから本人profile/passwordへ接続。DB上のID/状態/学校/機能権限と入力要件・表示切替・共通確認・再ログイン通知。必須変更中は戻れない |
| T-SELF-004 | 第135版UI追記 | 生徒一覧セル/操作の1行表示と一覧内横スクロール。教師共通メニューの権限なし選択を共通feedbackで通知。権限あり未実装は準備中と区別 |
| T-SELF-005 | 全要件 | 対象テスト/WAR、DBの発行→初回変更→通常変更→再設定、失敗時不変、旧版/停止/他者/CSRF境界、全旧セッション失効、8080と375pxで確認 |

API契約: GET `/teacher/account/profile` とGET/POST `/teacher/account/password`。認証済みの利用中教師本人だけを対象とし、業務権限を要求しない。他者の`userId`指定は400。POSTは現在/新規/確認パスワード、`version`、`csrfToken`、共通確認の`changeConfirmed=yes`を要求する。成功は現在sessionを破棄して教師ログインへ303、匿名sessionに一度限りの成功通知を保存する。不正入力400、ロール/CSRF403、古い版409、DB失敗503。入力・履歴に新旧パスワードやハッシュを残さない。

### 工程12aの完了記録（2026-10-07）

- T-SELF-001〜005完了。通常開発DBへV22を適用し、`flywayValidate`成功。既存教師も次回ログインから本人変更を必須とし、新規発行・管理者再設定も同じ状態へ戻す。管理者自身と生徒レベル1/2の運用は変更していない。
- 対象テスト19件・WAR成功後、次の全体検証を実行して成功した。JUnit XMLは270件中181成功・89明示ゲートskip・失敗/エラー0。教師本人DBテスト4件と既存管理者教師DBテスト2件はすべて成功・skip0。入力失敗時不変、旧版/停止/偽装ロール、同時変更の一方だけ成功、保存失敗rollback、秘密情報を含まない履歴を実MySQLで確認した。

```sh
docker compose run --rm --no-deps -e DB_NAME=ppe_teacher_self_test_20261007 -e TEACHER_SELF_DB_TEST=true -e TEACHER_ACCOUNT_DB_TEST=true -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home app gradle --offline --project-cache-dir=/home/gradle/.gradle/student-management-project flywayMigrate flywayValidate test war --rerun-tasks --no-daemon --warning-mode all
```

- 通常DBへの反映コマンドも成功し、アプリ再起動後に8080の教師ログイン200を確認した。

```sh
docker compose run --rm --no-deps -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home app gradle --offline --project-cache-dir=/home/gradle/.gradle/student-management-project flywayMigrate flywayValidate --no-daemon --warning-mode all
```

- 認証HTTP: 本人profile/passwordのGET 200、CSRF403、他者指定400、旧版409、誤った現在パスワード/条件違反/同値/確認不一致/未確認400と入力値非echo、権限なし業務URL403を確認。必須変更中の通常GET/POST・ログイン先上書きを拒否した。通常変更後は旧パスワードを拒否し、新パスワードだけで既存の権限別ログイン先へ遷移した。
- ブラウザー: 学校・業務権限なし教師の本人情報、初回変更の案内・戻る不可、通常変更の5要件・表示切替・共通確認のキャンセル/確定・再ログイン成功通知を確認。localhostと127.0.0.1で独立した2セッションを作り、本人変更後の他方失効を認証HTTPで確認した。管理者再設定後も必須変更へ戻り、profileへの迂回はできなかった。
- 生徒一覧は1440px/375pxで、全セルの`white-space: nowrap`、操作群の折り返しなし・ボタンの同一行配置、ページ幅がviewport以下となることを実測した。権限なしメニューはマウス/Enterで指定文言を確認。権限あり未実装の「準備中」はDOM起点のクリックで確認した。
- ブラウザーツールの通常クリック/安定待ちにタイムアウトがあったため、一部操作は標準DOMの`click`/`requestSubmit`でイベント処理を確認した。キャンセルはダイアログの表示アニメーション完了後に実行した。通常ブラウザーの全操作・全OS/ブラウザー組合せを手動受入済みとはしない。
- 2026-10-07の画面仕上げで、教師本人のアカウント情報/パスワード変更のフェードインを無効化し、教師・管理者の各ログアウト操作に共通確認ダイアログを追加した。管理者画面はプロトタイプ同様にブランドヘッダーを表示しない。検証: `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home app gradle --offline --project-cache-dir=/home/gradle/.gradle/student-management-project test war --rerun-tasks --no-daemon --warning-mode all` はexit code 0 / `BUILD SUCCESSFUL`。8080上の実`logout-confirmation.js`と共通`PPEFeedback`を合成フォームで確認し、キャンセル時は送信0回・ボタン再有効化、確定時は送信1回を確認した。CSSのcomputed styleは対象画面で`none`、無関係画面で`fadeInUp`。Node.jsは利用不可だが、統合ブラウザーで実JS/CSSを検証した。認証済み管理者画面そのもののブラウザー受入は未実施。`git diff --check`も成功。
- ホストのNodeが利用できなかったため、変更したJavaScript 3ファイルはブラウザーから取得して構文確認し、すべて成功。`git diff --check`成功、新しいControl/Servletと変更JSのエディター診断0を確認。既存Gsonのエディター依存解決と実Gradle結果は区別する。
- 専用DBは利用者残存0を確認してからschema/専用grantを削除し、両方の残存0と`log_bin_trust_function_creators=0`を確認した。通常DBの今回の合成教師は管理者操作で論理削除し、監査履歴を保持した。既存の確認用教師のパスワードは変更せず、共有ページは8080の教師ログインへ戻した。
- 次は工程13「授業演習コード配信」。本番TLS/cookie、実管理者運用、その他明示ゲートのDB/APIテストと機能横断の最終受入は今回の完了範囲に含めない。

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

## 生徒管理実装後の教師初期画面（2026-10-07）

- [機能仕様第134版](../../function-specification.md)に合わせ、教師ログイン後は生徒アカウント管理の権限があれば最優先で表示する。権限なしの場合だけ課題編集 → プロンプト設計 → 教師メニューの順で利用可能な画面へ遷移する。権限判定済みの遷移先がない場合のfallbackも教師メニューとし、管理者/生徒/強制パスワード変更の遷移は維持する。
- 共通メニューの権限なしリンク無効化とサーバー側の認可は維持する。未実装の業務項目は権限の有無によらず利用不可とする。教師アカウント・学校管理は管理者専用で、一般教師からの直接URLも拒否する。
- `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home app gradle --offline --project-cache-dir=/home/gradle/.gradle/student-management-project test --tests servlet.auth.LoginServletTest war --no-daemon --warning-mode all` 成功。6件成功、失敗/エラー/skip0、WAR生成成功。複数権限・生徒管理だけ・生徒管理権限なし・業務権限なし・既存ロール遷移を確認した。
- 通常appを再起動して8080の再ログインから生徒管理へ遷移すること、メニューの生徒管理リンク有効/課題・プロンプト無効、直接URLの課題/プロンプト/管理者画面403を確認した。学校filter付きCSVは200・ダウンロードヘッダー・8列・IDと確認可能PWを含み、別学校の行は含まれない。レベル2本人変更後は確認不可のまま維持する。
