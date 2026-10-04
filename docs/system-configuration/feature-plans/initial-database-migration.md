# 初期DBマイグレーション 実装計画

状態（2026-10-04整理）: **工程2の初期スキーマ構築は完了済み**。以下はその作業記録であり、既存DBの初期化・再構築の指示ではない。後続機能のスキーマ変更は新しいマイグレーションで管理する。現在の着手対象は[実装ロードマップ](../implementation-roadmap.md)を参照する。

## 目的と範囲
- 目的: 合意済みのテーブル定義から、空のローカル MySQL を再現可能に構築する。
- 今回実装する範囲: Flyway の実行設定、全テーブル・制約・必要な索引を含む初期マイグレーション、空DBへの適用確認。
- 今回対象外: 開発用ユーザー等の seed、業務機能の DAO / Control、保存期間満了時の処理（IC-010）。

## 必読資料
- [実装ロードマップ](../implementation-roadmap.md) の工程2
- [ローカル開発手順](../implementation-and-local-development.md)
- [実装契約](../implementation-contract.md)
- [DB設計書](../../database-design/README.md)
- [テーブル定義](../../database-design/table-definitions.md)
- 関連する状態ルール: 各テーブル定義と実装契約が参照する状態ルール

## 前提・未決事項
- 合意済み前提: テーブル定義をスキーマの正本とし、外部キーに連鎖物理削除を設定しない。
- 実装契約上の関連 ID: IC-007、IC-008 は合意済みで、`tasks.active_prompt_version_id` と `research_subject_identifiers` を含める。
- IC-010 の保存期間満了時の匿名化・物理削除は保留中のため、このマイグレーションで処理を決めない。
- テーブル定義に型・制約の記載がない部分は推測で追加せず、根拠のある制約だけを実装する。
- 1000文字のパス一意制約は、MySQL の索引長制限を超えないよう照合順序ウェイトの生成ハッシュ列で実装する。

## 実装タスク
1. [x] Gradle から Flyway を実行できるようにし、接続情報は環境変数から渡す。
2. [x] テーブル定義の全テーブル、主キー、一意制約、外部キーをマイグレーションに反映する。
3. [x] テーブル定義に記載された推奨索引を反映する。
4. [x] 空DBへ適用し、テーブル数・制約・再適用不可（Flyway管理）を確認する。

## 変更予定ファイル
- 作成: `src/main/resources/db/migration/V1__create_initial_schema.sql`、`V2__enforce_account_constraints.sql`
- 作成: `docs/system-configuration/feature-plans/initial-database-migration.md`
- 変更: `build.gradle`、`docs/system-configuration/implementation-roadmap.md`、`AGENTS.md`

## 検証計画
- ビルド: `docker compose exec -T gradle gradle build --warning-mode all` — 成功。
- マイグレーション: `docker compose exec -T gradle gradle flywayMigrate --warning-mode all` — V1・V2適用後の再実行も成功。
- 検証: `docker compose exec -T gradle gradle flywayValidate flywayInfo --warning-mode all` — 成功、V1・V2とも `Success`。
- ローカル環境: `docker compose build`、`docker compose up -d`、`docker compose ps` — 成功、DB healthy。
- JDBC: `docker compose exec -T app jshell --class-path '/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*'` から `Client.createConnection()` — 成功。
- 画面確認: `curl --silent --show-error --output /tmp/ppe-hello.html --write-out 'HTTP %{http_code}\n' http://localhost:8080/hello` — 404。Servlet が参照する `/WEB-INF/hello/index.jsp` が未作成のため（本計画の対象外）。
- 正常系: 初期スキーマ適用後にテーブル、外部キー、一意制約、索引が存在する。
- 再実行: Flyway が適用済みバージョンを再適用しない。
- 空DB: 既存データを削除せず、新規の専用DBへ適用して検証する。
- DB検証: 56テーブル、105外部キー、26個の非主キー一意インデックスを確認。長いパスの照合順序対応一意制約と、V2の管理者数・生徒セキュリティレベル制約も個別に検証する。
- 未実施の場合に残る未確認事項: 業務レコード間のデータ整合性は、seed 導入後のテストで確認する。自動テストは定義されていない（Gradle `test` は `NO-SOURCE`）。

## 完了条件
- [x] 空DBからFlywayで全スキーマを構築できる。
- [x] IC-007・IC-008 の合意済み設計が含まれる。
- [x] 保存期間満了処理を推測で実装していない。
- [x] マイグレーションとビルドの確認結果、残る制約を報告する。
