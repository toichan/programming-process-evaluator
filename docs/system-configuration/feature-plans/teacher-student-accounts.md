# 教師の生徒アカウント管理 実装計画

## 目的と範囲

工程12をServlet → Control → DAO → MySQLへ接続する。既存の[アカウント画面](../../../screen-flow-diagram/webapp/WEB-INF/teacher/account/account.html)を参照し、プロトタイプは変更しない。教師本人管理（12a）、授業配信（13）は対象外。

## 必読資料・前提

- [機能仕様](../../function-specification.md) REQ-STUDENT-001〜004
- [状態ルール](../../state-rules/admin/account-state-rules.md) IC-009
- [DB定義](../../database-design/table-definitions.md)、[実装契約](../implementation-contract.md)、[共通フィードバック](../../feedback-guideline.md)
- 学校・機能権限は既存管理画面で設定済み。クラスのアクセスは学校権限を継承する。
- 確認/CSV要件と認証ハッシュの非可逆性の差分を明示。ユーザー不在時の自動実行判断として、仕様を削らず教師確認可能な資格情報だけ暗号化保存する。

## ユースケースとデータ契約

|操作|認可・入力|DB/状態|結果|
|:--|:--|:--|:--|
|一覧/CSV|教師の機能・学校権限、各filter|所属・同意・初回状態・資格情報|権限内の現在filterだけ。確認不可PWはCSV空欄|
|一括作成|学校/クラス、1〜200人、要件を満たす初期PW|連番・users/profile/membership/研究ID/暗号文/履歴/監査を原子保存|作成したIDを表示|
|確認/詳細|対象ID・学校権限、確認時CSRF|資格情報・ログイン/資格情報/操作履歴|本人PWは非表示、確認監査|
|再設定/状態/一括削除|対象ID・期待版・CSRF・確認|状態と版更新、ロック解除、履歴/監査|成功後一覧再読込、古い版/権限外は全体rollback|

GET `/teacher/students`は画面、`view=list`はfilter済みJSON、`view=detail&userId=...`は履歴、`view=csv`はCSV。POSTは`create/reset/unlock/suspend/activate/delete/reveal`、`changeConfirmed=yes`とCSRF必須。選択削除は繰り返し`userId/version`を同じ順で渡す。再設定はレベル2のみ。

2026-10-08の[URL整理](../url-routing-policy.md)により、公開入口は`/teacher/students`へ統一した。旧`/teacher/account/account` / `/teacher/accounts`は認証後にGET302/POST307で誘導する。以下の完了記録・コマンドの旧URLは当時の検証履歴であり、現在の正規URLではない。

2026-10-07の追加指示により、教師ログイン後は生徒管理権限があればこの画面を最優先とする。権限なしの場合だけ課題編集 → プロンプト設計 → 教師メニューへfallbackする。共通メニューと直接URL/APIの認可は維持する。
追加修正の検証コマンド・6件成功と8080の認証/メニュー/CSV再確認は[認証計画](./authentication-and-login.md#生徒管理実装後の教師初期画面2026-10-07)を参照する。資格情報の暗号化・復号と本人変更後の除去方針は変更していない。

### 初期パスワード自動発行の追加契約（2026-10-07）

- 一括作成時に各生徒へ個別の8文字初期パスワードを自動発行し、教師入力のパスワードを複数アカウントで共用しない。生成値は既存の強度要件を満たし、認証ハッシュと教師確認用暗号文を同じトランザクションで保存する。
- 作成成功時にIDと対応パスワードを一度だけ画面へ返す。教師が画面を閉じた後に再表示せず、必要なら個別再設定で新しい資格情報を発行する。CSV・一時表示の既存動作と、生徒本人が変更した後の確認不可を維持する。
- 共通生成器を8文字化し、教師アカウントのシステム発行初期/再設定パスワードも8文字ちょうどとする。利用者本人が設定する変更用パスワードの8〜32文字要件は変更しない。

## 実装タスク・Requirement Mapping

|タスク|要求|対象・確認|
|:--|:--|:--|
|T001|REQ-STUDENT-001〜004|文書・V21・暗号化設定・入力と暗号テスト|
|T002|REQ-STUDENT-001/002/004|DAO/Control・生徒セッション・DB原子性/認可/状態/資格情報テスト|
|T003|REQ-STUDENT-003/004|Servlet/JSP/JS/CSS・教師メニュー・共有通知・CSV/詳細|
|T004|全要求|WAR、MySQL、8080認証browser、回帰・結果・handoff|

## 検証計画・完了条件

- 入力・暗号・Servlet単体テスト、専用DBで両レベルの作成/本人変更/再設定/停止/削除/権限外/競合/全体rollback/履歴保持を確認する。
- Compose Java21/Gradleでtestとwar、Flyway V21、8080で作成後の生徒ログイン・CSV・確認不可・詳細・選択削除・375px表示を確認する。
- 秘密情報をログ/監査/ソースへ出力しない。鍵未設定・破損は503、認可403、入力/状態/版不正400。エラーは[エラーレポート](../error-report.md)へ記録する。
- 実行結果と未確認項目を記録してからロードマップ・AGENTSの次工程を更新する。

## 実行結果

2026-10-07、T001〜T004を完了。プロトタイプは変更せず、本実装を通常の8080環境へ反映した。

### 自動検証

専用MySQLでV1〜V21を適用し、両レベルの資格情報、本人変更後の確認不可、再設定、認可、古い版、全体ロールバック、並行採番、新規クラス失敗時のロールバック、停止/解除/削除、履歴保持をDBテスト2件で確認した。

```sh
docker compose run --rm --no-deps \
  -e DB_NAME=ppe_student_accounts_test_20261007 \
  -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home \
  -e STUDENT_ACCOUNT_DB_TEST=true \
  app gradle --offline \
  --project-cache-dir=/home/gradle/.gradle/student-management-project \
  test war --rerun-tasks --no-daemon --warning-mode all
```

結果: 257件、168成功、89件は既存の明示ゲートでskip、失敗/エラー0、WAR成功。

その後、操作名なしPOSTの500を再現し、DB接続前に400で拒否するガードと入力選択の回帰テスト2件を追加した。専用DB清掃後の最終回帰は次のとおり。

```sh
docker compose run --rm --no-deps \
  -e GRADLE_USER_HOME=/home/gradle/.gradle/student-validation-home \
  app gradle --offline \
  --project-cache-dir=/home/gradle/.gradle/student-management-project \
  test war --rerun-tasks --no-daemon --warning-mode all
```

結果: 259件、168成功、91件skip（既存89件と専用DBテスト2件の実行ゲート）、失敗/エラー0、WAR成功。専用DB2件の成功証跡は上の実行に含む。

### 8080の認証・画面受入

- 生徒管理のみの機能権限を持つ教師でログイン先とナビを確認。レベル1を2人、レベル2を新規クラスと同時に2人作成し、両レベルの生徒ログインを確認した。
- レベル2は初回変更/再設定後の強制変更からホームへ進む。本人変更後の教師確認不可、CSVのパスワード空欄、暗号化資格情報行の除去をHTTPとMySQLで確認した。レベル1の本人変更POSTは403。
- 学校filter付きCSVは200、8列、対象学校の行のみ。確認API、再設定、ロック解除、停止/解除、2人の選択削除は200。古い版400、CSRFなし403、権限外学校403、生徒/他機能権限からの管理アクセス403を確認した。
- 最終WAR後に通常appを再起動し、認証後の画面別名/一覧/詳細/CSVが200、操作名なしPOSTが400となることを再確認した。
- 詳細画面に作成者、ログイン/資格情報/操作履歴を表示。論理削除後も履歴が残る。選択削除の共有確認に2人の対象が表示され、キャンセルでは変更されない。
- 資格情報はモーダル終了時に消去。作成/再設定から共有確認へ移る際はBootstrapの表示遷移完了を待ち、同時表示を1件に保つ。パスワード欄は再度開くと非表示へ戻る。
- backdrop表示前の最速submitでも遷移完了を先に待つことを同期イベントで再現し、同時モーダル1件、キャンセル後の資格情報消去を確認した。最終JS修正後に同じCompose/Gradle条件で`war --no-daemon --warning-mode all`を再実行して成功し、WAR内のJSとソースのSHA-256一致を確認した。
- 375pxでページ幅375px、モーダル幅359px。一覧テーブルだけが横スクロールし、ページ全体にはみ出さない。JSP/JS/CSSのエディター診断なし、`git diff --check`成功。

### 後片付け・未確認事項

- 専用DBと付与権限を削除し、残存0、MySQLのroutine作成設定0を確認した。通常DBのV21・確認用合成アカウントと資格情報鍵は維持し、app/DB/runnerを稼働状態で残す。秘密情報はGitへ含めない。
- エディターの既存Gson解決問題は実コンパイル成功と区別する。OSのクリップボード許可、全ブラウザー/OS、本番TLS/cookie・鍵保管/運用は今回のローカル受入では未確認。実生徒データ・研究データ・外部AI APIは使用していない。
- 次は[ロードマップ](../implementation-roadmap.md)工程12a「教師本人のアカウント管理」。工程13へは先行しない。
