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

### 生徒管理の資格情報キー

教師が確認可能な生徒の初期・再設定用パスワードは、認証用ハッシュとは別にAES-256-GCMで暗号化保存します。初回だけ`openssl rand -base64 32`で生成した値を、Git対象外の`.env`の`STUDENT_CREDENTIAL_KEY`へ設定し、`docker compose up -d app`で環境変数を反映してください。鍵未設定・破損時は作成・確認・資格情報付きCSVが明示エラーになります。生徒本人が変更したレベル2のパスワードは教師に表示されません。

鍵はDBと分離して安全にバックアップしてください。DBを保持したまま鍵を再生成すると既存資格情報を復号できなくなります。鍵変更には旧鍵での復号・新鍵での再暗号化が必要です。本番では外部のシークレット管理から注入し、CSVは資格情報を含むため配布後も適切に扱ってください。

## 停止

```sh
docker compose down
```

DB データは名前付きボリュームに保持されます。DB データも削除する場合は、対象を確認したうえで `docker compose down -v` を実行してください。

## ドキュメント

仕様、設計、現在の実装状況、機能別の検証記録は[docs/README.md](docs/README.md)から参照できます。
