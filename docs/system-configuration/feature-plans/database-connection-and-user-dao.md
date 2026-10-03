# DB接続基盤・利用者DAO 実装計画

## 目的と範囲
- 利用者に提供する動作: 認証処理がDBから利用者と生徒アカウント状態を安全に検索できる基盤を用意する。
- 今回実装する範囲: 環境変数で設定済みのJDBC接続を利用し、`login_id` から利用者・任意の生徒アカウント状態を読み取る `UserDao` を追加する。
- 今回対象外: パスワード照合、ログイン画面・Servlet・Control、ロックアウト、ログイン履歴の記録、セッション管理、DBスキーマ変更。

## 必読資料
- [機能仕様書](../function-specification.md) の JDBC + DAO、認証・認可要件
- [アカウント状態ルール](../../state-rules/admin/account-state-rules.md)
- [実装契約](../implementation-contract.md) の IC-009
- [DB設計書](../../database-design/README.md) と[テーブル定義](../../database-design/table-definitions.md)
- [クラス図](../../class-diagram/01-core-learning-flow.puml)
- [システム構成ガイド](../system-strucure-guide.md) と[ロードマップ](../implementation-roadmap.md) の工程3

## 前提・未決事項
- 合意済み前提: `users` がログインID・パスワードハッシュ・ロール・アカウント状態を持ち、生徒固有状態は `student_profiles` に保持する。
- 実装契約上の関連 ID: IC-009。停止・削除アカウントの拒否判断や記録はControl / 認証工程の責務であり、DAOは状態を返す。
- 未決事項: 今回の範囲に影響する未決事項なし。IC-010の保存期間処理には触れない。

## ユースケースとデータ契約
| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| ログイン候補の利用者検索 | 認証Controlからの内部呼び出し | login_id | `users` と任意の `student_profiles` | なし | なし。active / suspended / deleted をそのまま返す | 0件は空結果、SQL障害や不整合は `SQLException` として呼び出し元へ伝える |

## 実装タスク
1. [x] DB migration / 初期データ: 変更なし。
2. [x] Entity / DTO / DAO: 利用者認証データ型、生徒アカウント状態型、`UserDao.findByLoginId` を追加する。
3. [x] Control とトランザクション: 今回は実装しない。DAOがConnectionを所有し、検索完了時に閉じる。
4. [x] Servlet / Filter / 入力検証: 今回は実装しない。
5. [x] JSP / CSS / JavaScript: 今回は実装しない。
6. [x] 認可・監査・エラー処理: DAOに業務権限判定を置かず、SQLエラーや不正な永続値を成功値に変換しない。
7. [x] テスト・手動確認: Gradleビルド後、ローカルMySQLに一時レコードを作成し、ヒット・未ヒット・生徒状態の取得・一時レコードの後片付けを確認する。

## 変更予定ファイル
- 作成: `src/main/java/entity/UserCredential.java`、`src/main/java/entity/StudentAccountProfile.java`、`src/main/java/dao/UserDao.java`
- 変更: なし（`lib.mysql.Client` の環境変数接続を再利用する）
- プロトタイプから再利用する表示資産: なし。
- プロトタイプから除去する仮データ・擬似動作: なし。

## 検証計画
- ビルド / テストコマンド: `docker compose exec -T app gradle build --warning-mode all`
- 正常系: 既知login_idの利用者情報と生徒状態を取得する。
- 状態遷移・操作可否: DAOでは状態を書き換えず、停止・削除も含めDBの状態をそのまま返す。
- 権限境界・直接 URL: Web層を追加しないため対象外。
- DB保存後の再読込: 一時データをDAOで検索し、検索後は一時データを削除する。
- 異常系: 未登録IDは空結果、SQL接続失敗と不整合データは例外として伝わること。
- 未実施の場合に残る未確認事項: 認証Controlとの統合、ログイン履歴、セッション・権限境界は後続工程で確認する。

## 完了条件
- [x] JDBC接続設定をコードに重複せず、同一設定をDAOから利用できる。
- [x] SQLはPreparedStatementを使用し、Connection / Statement / ResultSetをcloseする。
- [x] 0件・DB障害・状態値不整合を区別し、失敗を握りつぶさない。
- [x] IC-009の状態値をDAO内で勝手に遷移させない。
- [x] 実行した確認結果と未確認事項を記録する。

## 実施結果
- `docker compose exec -T gradle gradle build --warning-mode all`: 成功。ビルド出力は `UP-TO-DATE`、`test` は `NO-SOURCE`。DAO / Entity の class ファイルが生成され、Tomcat のデプロイ先にも `UserDao.class` が存在することを確認。
- Tomcat コンテナの JShell からローカル MySQL を検索: 生徒のロール・有効状態・プロフィール状態と、教師の停止状態・プロフィールなしを取得。利用者なしと SQL インジェクション文字列は空結果。`UserCredential.toString()` にパスワードハッシュが含まれないことを確認。
- 生徒プロフィール欠落データは `SQLException`、DB接続失敗も `SQLException` として伝播することを確認。
- 一時利用者レコードは削除し、DAO検証用 `login_id` の残存件数が 0 であることを確認。
- `git diff --check`: 成功。
- 未確認事項: 自動テストは未整備（`test NO-SOURCE`）。認証Controlとの統合、ログイン履歴、セッション・権限境界は後続工程で確認する。
