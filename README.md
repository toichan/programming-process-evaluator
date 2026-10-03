# Programming Process Evaluator

Java 21 / Servlet / JSP の開発環境を Docker Compose で起動します。アプリケーション処理は Control、データアクセスは DAO に配置し、開発時の Servlet コンテナーは Gretty 経由で起動します。

## 初回セットアップ

1. `.env.sample` を `.env` にコピーします。
2. `.env` の `PROJECT_NAME` をワークスペース名にし、ローカル開発用の DB パスワードを必要に応じて変更します。`.env` は Git に追加しないでください。
3. 以下を実行します。

```sh
docker compose up --build -d
```

Gradle / Gretty と MySQL が起動します。アプリケーションは Gretty の `appRun` タスクで動作し、MySQL の準備完了後に開始します。
初回起動後、別のターミナルから DB マイグレーションを適用してください。

```sh
docker compose exec -T app gradle flywayMigrate
```

## 開発・確認

- アプリケーション: `http://localhost:${APP_PORT}/hello`
- アプリログ: `docker compose logs -f app`
- ビルド: `docker compose exec -T app gradle build --warning-mode all`
- DB マイグレーションの再適用: `docker compose exec -T app gradle flywayMigrate`
- 継続ビルドではなく Gretty の開発サーバーを使用します。ソース変更は Gretty の開発時再読込で確認してください。

MySQL 接続プールは HikariCP を使用します。接続設定のテンプレートは `src/main/resources/dataSource.properties` にあり、接続先・認証情報の値は `.env` から Compose 経由で環境変数として渡されます。パスワード等を設定ファイルやソースコードに書かないでください。

## 停止

```sh
docker compose down
```

DB データは名前付きボリュームに保持されます。DB データも削除する場合は、対象を確認したうえで `docker compose down -v` を実行してください。

## ドキュメント

仕様、設計、現在の実装状況、機能別の検証記録は[docs/README.md](docs/README.md)から参照できます。
