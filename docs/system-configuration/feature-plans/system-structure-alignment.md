# システム構成ガイド整合計画

状態（2026-10-04整理）: **工程1の完了済み作業記録**。以下は構成整合時の計画・検証履歴であり、再実施の指示ではない。現在の着手対象は[実装ロードマップ](../implementation-roadmap.md)を参照する。

## 目的
- 更新されたシステム構成ガイドを目標構成として採用し、上流仕様、開発手順、実装パッケージ、ビルド・開発環境の用語と設定を一致させる。
- Servlet は HTTP 入出力、Control はアプリケーション処理とトランザクション、DAO は SQL と永続化に責務を限定する。
- Java 21、Gradle、Gretty / Tomcat 9、MySQL、HikariCP を再現可能な構成にする。
- DB 接続情報は `dataSource.properties` を設定の基準とし、機密値は環境変数から解決して Git に含めない。

## 作業順
1. [x] 上流の機能仕様と画面遷移資料の層名を更新し、改版履歴を追加する。
2. [x] システム構成ガイドを実際の配置・設定資材に合わせ、層の責務・名称を統一する。
3. [x] 開発・実装手順、ロードマップ、実装契約、機能別計画、AGENTS.md の用語と手順を更新する。
4. [x] Java パッケージを `servlet/` と `control/` に統一し、認証処理を Control に配置する。
5. [x] Java 21、Gretty、HikariCP、外部化したデータソース設定をビルド・Compose・アプリ接続に実装する。
6. [x] README に初回セットアップ、起動、ビルド、DB マイグレーション、停止方法を記す。
7. [x] ビルド、Gretty 起動、HTTP、MySQL 接続、Flyway 検証を行い、未確認事項を計画へ記録する。

## 主な変更対象
- 上流資料: `docs/function-specification.md`、`docs/screen-flow-diagram.md`
- 構成・手順: `docs/system-configuration/system-strucure-guide.md`、`development-and-deployment-flow.md`、`implementation-and-local-development.md`、`implementation-roadmap.md`、`implementation-contract.md`、機能計画、`AGENTS.md`
- 実装: `src/main/java/servlet/`、`src/main/java/control/`、`src/main/java/lib/mysql/Client.java`、`src/main/resources/dataSource.properties`
- 環境: `build.gradle`、`containers/gradle/Dockerfile`、`docker-compose.yml`、`.env.sample`、`README.md`

## 完了条件
- [x] 上流仕様から実装手順・コードまで `Servlet → Control → DAO → MySQL` と各パッケージが一致する。
- [x] Java 21 / Gradle / Gretty / HikariCP を使ったローカル開発を起動・再現できる。
- [x] DB 接続先・認証情報の解決元と秘密値の Git 除外が明確である。
- [x] ビルド、HTTP 応答、DB 接続、Flyway 検証が成功し、結果が記録されている。

## 検証結果
- `docker compose config --quiet`: 成功。
- `docker compose up --build -d`: 成功。Gretty が Tomcat 9.0.118 を起動し、HikariCP 経由で MySQL への接続を初期化。
- `docker compose exec -T app java -version`: OpenJDK 21.0.5。
- `docker compose exec -T app gradle --version`: Gradle 8.10.2。
- `docker compose exec -T app gradle build --warning-mode all`: 成功。`test` は `NO-SOURCE`。Gretty が Gradle の `WarPluginConvention` 非推奨警告を出すが、ビルドは成功。
- `docker compose exec -T app gradle flywayMigrate`: 成功。
- `docker compose exec -T app gradle flywayValidate`: 成功。
- `curl --silent --show-error --output /dev/null --write-out 'HTTP %{http_code}\n' http://localhost:8080/hello`: HTTP 200。
- `curl --silent --show-error --output /dev/null --write-out 'HTTP %{http_code}\n' http://localhost:8080/unknown`: HTTP 404۔
- `git check-ignore -v .env`: `.env` が `.gitignore` により除外されることを確認。
- 残る未確認事項: 自動テストは未整備。VS Code の Problems ではホスト側の Gradle 依存同期が済んでおらず HikariCP 型が未解決と表示されたが、Java 21 コンテナー内の Gradle ビルドは成功。Gradle 9 対応前に Gretty の `WarPluginConvention` 非推奨警告を解消する必要がある。
