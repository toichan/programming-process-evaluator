# システム構成・フォルダ構成ガイド

この文書では、このリポジトリの Web アプリケーションと開発環境の構成を説明します。機能の追加・変更時は、各層の責務と既存の配置に合わせて実装してください。

## 1. システム全体構成

```text
利用者のブラウザー
      │ HTTP / HTTPS
      ▼
サーブレットコンテナー（開発時は Gretty）
      │
      ▼
Web アプリケーション
      ├── Servlet: HTTP リクエストの受付、入力の読み取り、応答
      ├── Control: アプリケーション処理の実行・調整
      ├── DAO: MySQL へのデータアクセス
      ├── Entity / modelUtil: データや処理で共有する型・補助クラス
      └── Web 資源: WEB-INF 内の画面資源、CSS、画像など
                 │
                 ▼
               MySQL
```

### 各層の責務

| 層・パッケージ | 責務 |
|---|---|
| `servlet/` | `HttpServlet` を使って URL ごとの HTTP リクエストを受け付ける。リクエスト値を読み取り、対応する Control を呼び出し、画面表示・転送・リダイレクトなどの応答を行う |
| `control/` | 画面や HTTP の詳細から処理を分け、アプリケーションの処理を進める。必要に応じて DAO を呼び出し、Servlet に結果を返す |
| `dao/` | SQL の実行やデータベース接続など、データベースとの入出力を担う |
| `entity/` | アプリケーションで扱うデータを表すクラスを配置する |
| `modelUtil/` | 複数の処理で共有するモデル関連の補助クラスが必要になった場合に配置する（現時点では未作成） |
| `src/main/webapp/` | HTML などの Web 資源を配置する。`WEB-INF/` 内の資源は直接公開せず、CSS や画像などは用途に応じたディレクトリに分ける |

Servlet と Control は役割が異なります。Servlet は HTTP の受付と応答を担当し、Control はアプリケーション処理を担当します。データベース固有の処理は DAO に置き、Servlet に SQL や業務処理を集中させないでください。

## 2. フォルダ構成

このリポジトリの主な配置は次のとおりです。

```text
.
├── docs/                           # 開発・セットアップ関連文書
├── containers/                     # Gradle / MySQL の開発用コンテナー
├── docker-compose.yml              # ローカル開発サービス
├── .env.sample                     # ローカル設定のひな型（秘密値は開発専用）
├── src/
│   └── main/
│       ├── java/
│       │   ├── control/            # アプリケーション処理
│       │   ├── dao/                # データベースアクセス
│       │   ├── entity/             # データを表すクラス
│       │   └── servlet/            # HTTP リクエストと応答
│       ├── resources/
│       │   └── dataSource.properties # HikariCP 設定。接続秘密値は環境変数から解決
│       └── webapp/
│           ├── WEB-INF/            # 直接公開しない Web 資源
│           ├── css/                # CSS
│           └── img/                # 画像
├── build.gradle                    # Gradle のビルド・依存関係設定
├── settings.gradle                 # Gradle プロジェクト設定
├── README.md                       # 開発環境の利用方法
└── .gitignore                      # Git 管理から除外するファイル
```

各 Java パッケージは `src/main/java/` 以下に置き、宣言する package 名とディレクトリ名を一致させます。未実装のパッケージには `.gitkeep` が置かれている場合があります。

画面資源は `src/main/webapp/` 以下に置きます。サーバー側から利用する資源は `WEB-INF/` に配置し、ブラウザーから直接参照させる CSS や画像などは `css/`、`img/` などに分けます。

## 3. リクエスト処理の流れ

```text
ブラウザー
  → Servlet（リクエスト受付・入力の読み取り）
  → Control（アプリケーション処理）
  → DAO（SQL・データベースアクセス）
  → MySQL
  ← 処理結果を Control、Servlet 経由で画面・応答へ反映
```

Servlet は `HttpServlet` を継承し、`@WebServlet` などで URL と対応付けます。GET や POST の処理で入力を読み取り、Control に処理を依頼します。Control は必要な DAO を呼び出し、処理結果を Servlet に返します。DAO で発生したデータベース関連の例外は、DAO の例外型などを使って呼び出し元へ伝えます。

## 4. 実行・開発環境

| 構成要素 | このシステムでの役割 |
|---|---|
| Java | アプリケーションの実装・実行環境。Gradle の toolchain では Java 21 を指定する |
| Javax Servlet / JSP | Web リクエスト処理と JSP を含む Web アプリケーションの API（Servlet 3.1） |
| Gretty | Gradle から Apache Tomcat 9 を起動し、開発中のアプリケーションを実行する |
| Apache Tomcat | Servlet/JSP アプリケーションの開発・配備先 |
| MySQL | アプリケーションデータを保存するデータベース |
| HikariCP | DAO が利用する JDBC データソース・接続プール |
| 開発コンテナー | Gradle / Gretty と MySQL を Compose で起動する |

開発時は Docker Compose で Gradle / Gretty と MySQL を起動します。アプリケーションは Gradle の `appRun` タスクで起動し、接続プールとデータソース設定は `src/main/resources/dataSource.properties` で管理します。接続先と認証情報の実値は `.env` の環境変数から読み込み、Git 管理対象や公開文書に記載しないでください。

## 5. 主要な設定・セットアップ資材

| ファイル・配置 | 役割 |
|---|---|
| `build.gradle` | Java toolchain、Gretty、Servlet/JSP、JSTL、HikariCP、MySQL JDBC ドライバーなどのビルド・依存関係を定義する |
| `settings.gradle` | Gradle プロジェクト名などを定義する |
| `docker-compose.yml`、`containers/` | ローカル開発用の Gradle / Gretty / MySQL コンテナーを定義する |
| `.env.sample` / `.env` | ローカル環境の設定ひな型 / 実値（`.env` は Git 管理外） |
| `src/main/resources/dataSource.properties` | アプリケーションのデータソース設定を定義する |
| `src/main/resources/db/migration/` | Flyway によるデータベース初期化・更新 SQL を置く |
| `README.md` | 開発コンテナーの利用、アプリケーションの起動、DB 操作方法を案内する |

## 6. 変更時の指針

- HTTP 固有の処理は Servlet、アプリケーション処理は Control、SQL と DB 接続は DAO に分ける。
- データの受け渡しには `entity/` のクラスを使い、複数の処理で共有する補助型は必要に応じて共通パッケージへ整理する。
- 接続設定、開発環境、初期化 SQL はそれぞれの既存配置に合わせて変更する。
- 新しいパッケージや設定ファイルを追加したときは、この文書と関連する README・開発手順も実装に合わせて更新する。
- 環境ごとの接続先や秘密情報を管理するときは、開発用設定と本番用設定を区別し、秘密の実値を Git 管理対象へ追加しない。
