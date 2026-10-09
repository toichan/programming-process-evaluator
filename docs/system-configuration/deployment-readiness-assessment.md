# デプロイ準備調査: 現行実装・未決事項・着手順

調査日: 2026-10-06（JST）

**2026-10-10 CI追記:** 本書の「workflowなし」は調査時点の記録。
AWS接続なしのCI workflowをローカル実装・検証した。
[CI運用手順](./continuous-integration.md)に対象テスト・skip・検証結果を記載する。
GitHub実行はpush後に確認する。AWS向けCD/OIDC/本番承認は今回未実装。

**2026-10-09追記:** 以下の初回調査表は当時の履歴であり、現在の本番状態ではない。
初回HTTPS公開・V1〜V23・5サービスhealthyの確認は
[本番実測記録](./production-deployment.md#observed-production-record-2026-10-09-jst)、
業務受入は[本番システムテスト計画](./production-preparation-plan.md#本番システムテスト計画2026-10-09設計のみ)、
現在のバックアップ不足は[改善計画](./production-deployment.md#backup-gap-and-improvement-plan)を参照する。
公開済みを研究データ収集許可・全機能受入完了とは扱わない。
標準ルーブリックの自動登録は本書末尾の設計案であり、実装・本番登録は未実施。

## 目的・既存文書との役割分担

機能開発の完了を待たず、卒業研究用の**単一EC2**へ配置するために、人が決める条件と実装・運用準備を整理する。これは本番構成の実装、AWS構築、本番公開の承認、侵入試験の結果ではない。

- 本書: 調査時点のコード・設定に基づく準備状況、判断材料、推奨案、確認質問、着手順を扱う。
- [本番運用条件の決定台帳](./production-operations-decisions.md): AWS・予算・ドメイン・バックアップ・API・担当者等の**決定内容、決定日、決定者、根拠**の正本。本書で回答を推測して埋めない。追加の決定項目も合意後にこの台帳へ集約する。
- [全体手順](./development-and-deployment-flow.md): 開発から公開・運用までの大枠。本書はその公開準備段階を、現行実装に即して具体化する。
- [実装ロードマップ](./implementation-roadmap.md)・[実装契約](./implementation-contract.md)・[機能別計画](./feature-plans/): 機能進捗、仕様合意、過去の検証記録の正本。

既存の決定台帳と目的が重なる「決定記入テンプレート」は新設しない。本書は調査スナップショットであり、以後の機能進捗を二重管理しない。初回調査では既存の仕様・決定台帳・コード・Composeを変更していない。その後の回答で確定した運用条件は、本書と決定台帳へ反映する。

### 調査範囲と限界

Git管理対象・現在の作業ツリーを対象に、文書、Java/JSP/JavaScript、SQL migration、Docker、設定、開発スクリプト、テスト定義を静的調査した。調査時点で既存の未コミット変更があり、**HEADだけではなく作業ツリーの状態**を根拠とする。配備時には採用するcommit/tagを改めて固定する。

`.env` の存在・Git除外方針は確認したが、秘密の実値は読出し・転載しない。AWS/Google Console、実際の本番DB、EC2、DNS、TLS証明書、最新料金・quota・モデル提供状況は未確認。今回、サービスの再起動、DB変更、外部API呼出し、負荷試験、restoreは実施していない。過去のテスト成功は既存文書の記録であり、今回再実行した結果ではない。

### 状態・期限の読み方

状態は排他的ではない。例: 方針は「決定済み」、その設定は「未実装」、実環境での挙動は「要確認」と併記する。

| 状態 | 意味 |
|---|---|
| 決定済み | 仕様・合意記録に方針がある。実装・本番検証の完了とは別 |
| 実装済み | 現行コード・設定に該当処理がある。動的検証・完全性を保証しない |
| 未決定 | 人が決定する値・方針の確定記録がない |
| 未実装 | 調査対象に必要なコード・設定・運用資材がない |
| 要確認 | 資材の存在だけでは判断できない、整合性や実環境検証が残る |

期限は「今すぐ」「AWS構築前」「初回デプロイ前」「本番公開前」「運用開始後でも可」で示す。**初回デプロイは合成データだけのアクセス制限付き検証、公開は生徒・教師の実利用開始**と区別する。研究データ保護とコード実行の隔離は、実利用開始後へ先送りしない。

## 0. 結論・実装根拠・矛盾点

### 現在地

1. Java 21、Gradle/Gretty/Tomcat 9、MySQL、Flyway、認証、生徒の保存・実行・提出、AI評価worker、課題別アンケート、教師課題・プロンプト、管理者の教師管理などに実装がある。
2. 機能開発は未完了。ロードマップ工程12〜18の生徒アカウント管理・演習配信・教師の進捗/提出/評価/アンケート確認、工程19のAI最終受入、工程20の横断検証が残る。
3. ルートComposeはGradle `appRun`、ソースbind mountを使う**開発用**。本番Compose、WARを組み込む本番image、Nginx、TLS、バックアップ・監視・CI/CDは未整備。
4. AWS・DNSの意思決定、秘密情報管理、合成データでの配備・復旧練習は今から進められる。全機能の完成を前提にしない。
5. 次の項目は公開判断のブロッカー: 実行基盤のDocker権限境界、Geminiモデル/API・保持条件、TLS/cookie・認可の横断受入、正式同意文面・研究利用条件、復元可能なバックアップ、想定負荷での容量確認。

### 主要な一次資料

| 領域 | 調査した主要ファイル・役割 |
|---|---|
| 要件・進捗 | [README](../../README.md)、[機能仕様](../function-specification.md)、[文書索引](../README.md)、[ロードマップ](./implementation-roadmap.md)、[実装契約](./implementation-contract.md)、[ローカル開発手順](./implementation-and-local-development.md)、[全体手順](./development-and-deployment-flow.md)、[決定台帳](./production-operations-decisions.md) |
| 構成・研究・費用 | [構成ガイド](./system-strucure-guide.md)、[構成図](../figures/system-figure.puml)、[AIコンテキスト図](../figures/system-context-main-ai-evaluation.puml)、[AI連携設計](../ai-api-integration-design.md)、[費用試算](../server-cost-estimate.md)、[状態ルール](../state-rules/README.md) |
| DB | [DB設計](../database-design/README.md)、[テーブル定義](../database-design/table-definitions.md)、[V1〜V20 SQL](../../src/main/resources/db/migration/)、[DB接続Client](../../src/main/java/lib/mysql/Client.java)、[データソース設定](../../src/main/resources/dataSource.properties) |
| ビルド・コンテナ | [build.gradle](../../build.gradle)、[settings.gradle](../../settings.gradle)、[Compose](../../docker-compose.yml)、[環境変数見本](../../.env.sample)、[Git除外](../../.gitignore)、[Gradle Dockerfile](../../containers/gradle/Dockerfile)、[Tomcat Dockerfile](../../containers/app/Dockerfile)、[MySQL Dockerfile](../../containers/db/Dockerfile) |
| Python実行 | [runner Dockerfile](../../containers/python-runner/Dockerfile)、[runner実装](../../containers/python-runner/runner.py)、[runnerテスト](../../containers/python-runner/test_runner.py)、[Java client](../../src/main/java/control/student/PythonRunnerClient.java)、[エディターControl](../../src/main/java/control/student/StudentEditorControl.java)、[演習Control](../../src/main/java/control/student/StudentExerciseControl.java)、[エディター計画](./feature-plans/student-editor.md) |
| Gemini・非同期処理 | [Gemini client](../../src/main/java/control/evaluation/GeminiEvaluationClient.java)、[通常評価worker](../../src/main/java/control/evaluation/EvaluationWorker.java)、[入力DAO](../../src/main/java/dao/EvaluationWorkerDao.java)、[redactor](../../src/main/java/control/evaluation/EvaluationPrivacyRedactor.java)、[教師AI client](../../src/main/java/control/teacher/TeacherPromptAiClient.java)、[教師prompt Control](../../src/main/java/control/teacher/TeacherPromptControl.java)、[preview DAO](../../src/main/java/dao/ReevaluationPreviewDao.java)、[Lifecycle listener](../../src/main/java/lib/mysql/DataSourceLifecycleListener.java) |
| Web・認証・権限 | [web.xml](../../src/main/webapp/WEB-INF/web.xml)、[認証Filter](../../src/main/java/servlet/auth/AuthenticationFilter.java)、[ログイン](../../src/main/java/servlet/auth/LoginServlet.java)、[CSRF](../../src/main/java/servlet/auth/CsrfTokens.java)、[認証Control](../../src/main/java/control/auth/AuthenticationControl.java)、[PasswordHasher](../../src/main/java/control/auth/PasswordHasher.java)、[教師権限DAO](../../src/main/java/dao/TeacherPermissionDao.java)、[認証計画](./feature-plans/authentication-and-login.md) |
| 検証・補助資料 | [機能別計画とcheckpoint](./feature-plans/)、[エラーレポート](./error-report.md)、[評価/アンケート計画](./feature-plans/student-evaluation-survey.md)、[教師AI計画](./feature-plans/teacher-prompt-ai.md)、[開発専用SQL](../../scripts/dev/)、[Javaテスト](../../src/test/java/) |

調査対象に `.github` のissue/PR templateはあるが、Actions workflowは確認できない。Nginx設定、Tomcat `server.xml` / `context.xml` のリポジトリ独自設定、本番用Compose、バックアップ/restoreスクリプト、CloudWatch/IaC定義も確認できない。Gradle WrapperはGit管理対象になく、Gradle imageの版に依存する。

### 仕様・記録と実装の差分（既存資料は書き換えない）

| チェック項目 | 判定 (OK/要修正/要確認) | 根拠文書 | 差分・懸念 | 修正案 |
|---|---|---|---|---|
| 開発/本番構成の区別 | OK | [README](../../README.md)、[全体手順](./development-and-deployment-flow.md) | 開発Composeをそのまま公開できない旨は明示済み | 別の本番Composeとimageを準備する |
| Geminiモデル | 要修正 | [AI設計](../ai-api-integration-design.md)、[教師prompt Control](../../src/main/java/control/teacher/TeacherPromptControl.java)、[教師AI client](../../src/main/java/control/teacher/TeacherPromptAiClient.java) | 疎通記録は `gemini-3.7-flash`、教師設定の許可値は `gemini-2.5-pro` / `gemini-2.5-flash`。旧Flashの利用不可記録もある | 利用プロジェクトで使えるモデル/APIを確認・合意後、UI/validation/DB設定/テストを統一。今回変更しない |
| Python出力上限 | 要修正 | [機能仕様](../function-specification.md)、[runner](../../containers/python-runner/runner.py) | 仕様は標準出力10,000文字。実装はstdout/stderrそれぞれ32 KiB。ASCIIなら10,000文字を超え得る | 文字数とバイト数の両方の契約を合意し、境界テストを追加する |
| Python同時実行 | 要確認 | [機能仕様](../function-specification.md)、[runner](../../containers/python-runner/runner.py) | 仕様の「実行キュー + 固定数ワーカー」に対し、runnerは共有semaphore 4枠、超過429。待ち行列なし | 授業中の待ち方・公平性・再試行を決定する |
| 同意文書 | 要修正 | [V10](../../src/main/resources/db/migration/V10__register_prototype_consent_document.sql)、[機能仕様](../function-specification.md) | 仮文面がactiveで投入される。「研究目的でのみ利用」と通常学習のための収集・AI評価継続の方針を説明し分ける必要がある | 承認済み正式版を新versionとして登録。旧migrationを書き換えない |
| DB名 | 要修正 | [DB設計入口](../database-design/README.md)、[.env.sample](../../.env.sample) | DB設計の「見本では未設定」と異なり、見本には開発DB名がある。本番名の承認はない | 本番名を別途決定。見本の値を本番の確定値と扱わない |
| 機能進捗の古い記述 | 要確認 | [ロードマップ](./implementation-roadmap.md)、[実装契約](./implementation-contract.md)、[文書索引](../README.md) | ロードマップ冒頭と最新記録は教師管理完了だが、網羅表に未着手が残る。契約IC-007にも旧未着手記述がある | 最新の日時付き記録と現行コードを優先して範囲を確認。公開前に進捗記述を整合する |
| テスト基盤 | 要確認 | [初期DB計画](./feature-plans/initial-database-migration.md)、[build.gradle](../../build.gradle) | 初期記録の `NO-SOURCE` は当時の状態。現在はJUnit・runnerテストがある。DB/APIテストには環境変数によるskip条件がある | 件数だけでなく成功/失敗/skipと対象DB・API条件を記録する |
| 手順の並行準備 | 要確認 | [全体手順](./development-and-deployment-flow.md)、[決定台帳](./production-operations-decisions.md) | 全体図は直列だが、台帳は実装と並行した条件決定を認める。またprototype同期の旧手順は最新ロードマップと異なる | AWS条件決定・構築練習を機能開発と並行。prototype完成をデプロイ条件にしない |

## 1. AWS

現在の仕様は単一EC2、Linux（Ubuntu LTS推奨）、Docker Compose。AWSリソースが作成済みかはコード・文書から判断できない。東京 `ap-northeast-1` はQ4への回答で決定済み（2026-10-06）。`t3.medium` 等のインスタンス候補は既存台帳の**推奨例であって確定値ではない**。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| AWSアカウント・管理者・IAM・MFA | 開発者・研究実施者本人が個人で所有・契約するAWSアカウントを使用。台帳は本番分離・最小権限・MFAを推奨 | IAM/アカウント定義なし。実アカウントの設定は未確認 | 所有・契約形態は決定済み、管理設定は未決定・要確認 | 支払方法/費用負担、管理実行者、緊急アクセス、本番専用アカウント分離 | 学校/大学の個人契約クラウド利用規程、管理可能な人 | 個人所有・契約を前提に必要な研究利用許可を確認。root MFA・通常利用禁止、個人別の最小権限。既存ならIdentity Center等を利用 | 研究利用の許可、終了時の引継ぎと操作追跡 | 今すぐ |
| リージョン | 東京 `ap-northeast-1` を使用（Q4回答） | AWS設定なし | 決定済み・未実装、組織条件は要確認 | 国内保管条件・学校/大学規程との適合 | 倫理/学校規程、利用者所在地、サービス提供・料金 | 決定した東京regionで構築し、規程との適合を確認する | 遅延とAWS側データ所在を明確化。Gemini側の所在は別確認 | AWS構築前 |
| EC2・CPU/メモリ・OS | 単一EC2、Ubuntu LTS推奨 | EC2/AMI設定なし | 方針決定済み・値未決定 | AMI/arch、instance type、vCPU/RAM、変更手順 | 300接続、Python/AI負荷、DB/ビルド余力、budget | 合成負荷で候補を比較。初期は互換性を確認しやすいx86_64候補、必要なら後で見直す | 2vCPU/4GiB等を300人に十分と断定できない | AWS構築前（仮選定）、本番公開前（実測確定） |
| EBS容量・暗号化 | 永続DB、約35GBログの見積 | 名前付きvolumeのみ | 未決定・要確認 | type/GB、暗号化、拡張、ディスク警戒値 | DB索引、提出/演習/AI JSON、binlog、image、log、backup作業域 | 暗号化EBS、volumeの所在と拡張手順を固定。35GBだけで容量を決めない | DB以外と更新中の二重imageも容量を使う | AWS構築前 |
| VPC/subnet・Elastic IP | 詳細未定 | IaCなし | 未決定・未実装 | 既存VPC利用、public subnet、固定IPv4 | 学校のネットワーク方針、DNS、EC2停止予定 | 単一public subnetのEC2、必要ならEIP。既存VPC可。NAT Gateway/ALBは初期構成に不要 | IP変化を避けDNSを安定化、過剰費用を抑える | AWS構築前 |
| Security Group・管理接続 | Session Managerを基本とし、通常はSSHポートを公開しない（Q6回答）。firewall/IP制限の方針 | AWS/host firewall設定なし | 管理接続方針は決定済み・SG等未決定・未実装 | role/Agent/通信経路、緊急復旧、ingress/egress | SSM利用条件、Google/S3等の通信先 | 決定したSession Managerを構築、22は通常非公開。公開ingressは原則80/443 | DB/runner/Docker管理面を外部へ出さない | AWS構築前 |
| instance role | 最小権限の方針 | role定義なし | 未実装・未決定 | SSM、secret取得、S3 backup、監視権限 | 使用するARN/prefix、secret/backup設計 | EC2 roleで対象資源だけ許可。長期AWS鍵を `.env` に置かない、IMDSv2とコンテナからの到達を検証 | 学生コードや不要サービスからの資格情報取得を防ぐ | AWS構築前 |
| 予算/Cost Alert・監視・backup先 | 台帳に未確定項目 | AWS Budgets/CloudWatch/S3なし | 未決定・未実装 | 上限、通知先、S3 bucket、監視範囲 | 運用期間、RPO/RTO、費用承認 | Budgets通知、最小監視、非公開・暗号化したEC2外S3 backup | 通知は課金停止装置ではない。ホスト喪失に備える | AWS構築前（budget）、本番公開前（backup/監視） |

## 2. ドメイン・DNS・HTTPS

根拠: [機能仕様](../function-specification.md)、[全体手順](./development-and-deployment-flow.md)、[決定台帳](./production-operations-decisions.md)。現在の `http://localhost` は開発URLであり本番URLではない。Q5への回答で、本番URLの第一候補は `https://programming-process-evaluator.net` としたが、ドメイン取得可否・実際の公開は未確認。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| 本番URL・独自ドメイン・DNS | 本人管理で新規取得を検討。URL第一候補は `https://programming-process-evaluator.net`、必要な機能が揃えば取得先DNSを使用（Q5回答） | ドメイン/DNS設定なし、取得可否未確認 | 管理者/候補/条件付きDNS方針は決定済み・取得検討中・未実装 | 取得先、取得可否/費用、DNS機能、検証URL、証明書通知先 | 取得可否、研究利用条件、初年度/更新費、DNS機能 | 候補の空き状況・更新費・DNS機能を確認し取得。不要ならRoute 53は追加しない | 管理と費用を最小化 | AWS構築前 |
| EC2との紐付け | Nginx経由 | A/AAAA recordなし | 未実装・未決定 | A record、固定IP、TTL、IPv6方針 | EIP、切替予定 | EIPへA record。IPv6を運用しないならAAAAを作らない | 停止/切替時の到達先を明確にする | 初回デプロイ前 |
| Nginx・TLS証明書 | TLS終端、Let's Encrypt等 | Nginx/証明書設定なし | 方針決定済み・未実装 | Nginx版、ACME方式、連絡メール、更新主体 | domain権限、80番利用可否、学校制約 | Nginxコンテナ + Let's Encrypt、自動更新timer + 更新後reload。証明書/秘密鍵を専用volumeで保持 | 手動期限管理を避ける | 初回デプロイ前 |
| HTTPS強制・proxy設定 | HTTPS、80→443 | redirect/forwarded-header設定なし | 未実装・要確認 | HTTP例外、HTTPS判定、body/timeout上限、cookie | Tomcat接続、Python60秒、教師AI処理時間 | 80はACME等を除きHTTPS redirect、TomcatはNginxからだけ接続。信頼proxyを限定しscheme/IPを正しく伝える | Secure cookie、認証履歴IP、redirect loopを防ぐ | 初回デプロイ前 |
| 更新・期限監視 | 期限・更新失敗は本人へ通知（Q5-6回答）。台帳は更新失敗通知を推奨 | 未整備 | 通知先は決定済み・方式未決定・未実装 | 更新頻度、期限警戒、通知機構 | ACMEの実条件 | 更新dry-run、reload後の証明書確認、外部期限監視から本人へ通知 | timerが動いても更新/reload失敗はあり得る | 本番公開前 |

## 3. Docker / Docker Compose

根拠: [現行Compose](../../docker-compose.yml)、[各Dockerfile](../../containers/)。app/dbの公開は**127.0.0.1限定**であり、全世界公開ではない。一方、EC2用の安全な構成はまだない。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| 開発/本番分離 | ビルド済み成果物で本番起動 | appはGradle `appRun`、repo全体mount | 未実装（本番） | 本番ファイル名、project名、imageタグ | 成果物方式、配置先 | 独立した本番Compose（例 `compose.production.yaml`、未作成）、immutable image。ソース/Gradle cache mountなし | 本番で編集・build・開発再読込しない | 初回デプロイ前 |
| Tomcat/MySQL/Nginx/Python | 4役割を単一EC2で運用 | app/dev、db、runnerの3services。Tomcat Dockerfileは未接続 | 一部実装済み | image版/digest、WAR配置、Nginx、runtime事前pull | Javax Servlet互換、runner境界 | Tomcat9/Java21、MySQLを初期は現行系列、Nginxを追加。版を固定し更新日を管理 | 未固定タグの予期せぬ更新を避ける | 初回デプロイ前 |
| network・port | DB/runnerを非公開 | application/databaseはinternal、appだけoutbound参加 | 一部実装済み・要確認 | Nginx-app専用経路、外向き通信 | DNS/Gemini/SSM/backup/ACME通信 | 外部公開はNginx80/443のみ。Tomcat8080・MySQL3306・runner8090・Docker2375/2376・AJP8009・debug portはhost公開しない | internal networkだけでDocker socket権限は隔離できない | 初回デプロイ前 |
| volume・filesystem | DB永続化 | mysql volume、runner socket mount、tmpfs | 一部実装済み | DB/証明書/backup作業域、owner、project名 | EBS mount、UID、rootless設計 | 永続volume一覧を記録。project名変更で空DBを作らない。アプリは書込不要箇所read-only、秘密は必要先のみ | 永続性・誤初期化・権限漏れを制御 | 初回デプロイ前 |
| restart・healthcheck | 停止検知/再起動 | db `always`、runner `unless-stopped`、両healthcheckあり。appなし | 一部実装済み | 全service policy、起動順、再試行 | OS再起動、DB準備、WAR初期化 | 全service方針を揃え、DB/runner readiness後にapp、最後にNginx。app health追加 | depends_onは運用中障害の回復を保証せず、unhealthyだけでは自動restartされない | 初回デプロイ前 |
| CPU/RAM/PID・ログ上限 | サンドボックス制限、ログrotation | runner親128MiB/0.5CPU/64PID。app/db制限・Compose loggingなし | 一部実装済み・未決定 | app/db/Nginxの割当、log driver/上限 | JVM heap、MySQL buffer、EC2余力 | 計測後に各上限。Docker local/log rotationを親・子containerの両方に適用 | workerが作る子containerはrunner親のcgroup上限に含まれない | 初回デプロイ前（仮上限）、本番公開前（負荷検証） |

## 4. Java / Tomcat / Gradle

設定棚卸し: [build.gradle](../../build.gradle)はJava21、WAR plugin、Gretty3.1.9/Tomcat9、Flyway13.9.0、Gson2.13.1、HikariCP6.3.0、JDBC8.4.0、JUnit5.11.4を指定する。ビルドimageはGradle8.10.2-jdk21、Tomcat imageは9.0-jdk21-temurin。Servlet APIは `javax.servlet` 3.1、web.xmlは4.0。Tomcat10以降のJakartaへの単純置換はしない。

明示的なGradle build profile/runtime profileはない。Gretty開発起動とWAR生成のタスク差、および環境変数で設定を切り替える構成である。`JAVA_OPTS` / `CATALINA_OPTS`、JVM heap、独自Tomcat connector/session設定は未定義。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| Java・Gradle・WAR | Java21、Tomcat9、Gradle | WAR plugin、root project `ROOT`、ビルド資材あり | 実装済み・配備要確認 | 再現可能なbuild、WAR名、artifact hash | JDK/Gradle版、test skip、clean checkout | 固定imageのbuild環境で `gradle clean test war --warning-mode all`。実生成WAR名を確認しROOT.warとしてimageへCOPY | WrapperがGit管理されていないためhost版任せにしない | 初回デプロイ前 |
| Tomcat配置・dev/prod分離 | 本番はWAR配備 | Dockerfileは既定ROOT削除だけ。WAR COPY/CMD独自設定なし、Compose未使用 | 未実装（本番配備） | ROOT context、Tomcat権限、manager削除 | WAR起動・listener・依存関係 | build済みWARを含むimage、必要のないmanager/host-manager/examplesを配備しない | 空Tomcat imageだけではアプリは動かない | 初回デプロイ前 |
| JVM・timezone/locale | 日本語UI、日時利用 | JDBC `serverTimezone=UTC`、一部Clock UTC、他で `LocalDateTime.now()`。heap/TZ統一なし | 一部実装済み・要確認 | heap、container RAM、UTC保存/JST表示、UTF-8/locale | 期限/予約/同意の日時、DB session TZ、thread数 | JVM/DB/hostの時刻基準を明示し、画面変換をテスト。heap以外のnative/thread余力を残す | `serverTimezone=UTC`だけでは全層UTCを保証しない | 初回デプロイ前 |
| health・ログ・停止 | app監視、エラー記録 | `/hello` はJSP表示のみ。JUL/ServletContext.log、listenerでDB/4worker起動停止 | 一部実装済み・未実装（readiness） | liveness/readiness、内部公開、grace period | DB、評価queue、publication worker、停止最大時間 | 安価なlivenessとDB readinessを区別し、Gemini障害でapp全体を落とさない。graceful stopを検証 | `/hello` はDB/worker正常性の証明ではない。評価worker停止joinは最大65秒 | 初回デプロイ前 |

起動依存は現行 `db health / runner health → appRun → Client.initialize → 評価/preview/job/課題公開worker`。Flywayはlistenerに含まれずREADMEの**手動別コマンド**。本番は「DB準備 → migration成功 → app起動」を配備側で保証する。

## 5. MySQL / Flyway / バックアップ

根拠: [migration](../../src/main/resources/db/migration/)、[build.gradle](../../build.gradle)、[MySQL Dockerfile](../../containers/db/Dockerfile)、[DB設計](../database-design/table-definitions.md)、[エラーレポート](./error-report.md)。MySQL serverは8.0、JDBC driverの8.4.0とは別である。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| MySQL版・本番名・user・root | MySQLコンテナ、rootを通常利用しない | MYSQL_USERでapp user作成、appはDB_USER使用。見本は開発名 | 一部実装済み・本番値未決定 | patch版、本番DB名、DML user、migration user、backup user | 最新support状況、routine/trigger権限、既存volume | 役割別資格情報、appにroot/DDL権限を渡さない。root管理限定、DBポート非公開 | 初期image作成userの権限をそのまま最小権限とみなさない | 初回デプロイ前 |
| 接続pool・MySQL設定 | DAO/JDBC | Hikari max10/min2、接続待ち30秒、DB config独自定義なし | 実装済み・要確認 | max_connections/buffer pool/timeout/charset | Web同時要求と4worker、memory、lock | 現行10接続を起点に実測。無条件に300へ増やさない | 接続数を増やしてもクエリ/メモリ問題は解決しない | 本番公開前 |
| Flyway適用 | SQL版管理 | V1〜V20、`cleanDisabled=true`、環境変数で接続。app起動時自動適用なし | 実装済み・手順未整備 | 実行主体/一度だけ実行/承認 | 対象DB、current version、checksum、artifact版 | one-shot migration用build環境で `flywayInfo → flywayValidate → flywayMigrate → flywayValidate`、成功後app | WARコンテナにはGradleがない。migration権限を常駐appから分離 | 初回デプロイ前 |
| 既存DB更新・失敗 | データ保持、破壊変更要確認 | V11 routine/trigger・整合チェック、V19はtasksありでSIGNAL停止 | 要確認（重要） | 旧versionからの更新可否、backfill、失敗復旧 | 復元した実相当DB、所属対応、権限 | 初回は空の本番DBへV1〜V20。既存DBはrestore copyで事前検証。V19でresetを本番に適用しない | デモDB初期化許可は本番に適用不可。MySQL DDLは一括rollbackできない場合がある | 初回デプロイ前・各更新前 |
| backup頻度・RPO/RTO・保持 | 台帳の例は日次+継続log、30日、RPO24h/RTO数時間。未承認 | スクリプト/保存先/検知なし | 未決定・未実装 | 許容損失、復旧時間、日次/授業後/binlog、保持 | 授業日程、研究データ価値、budget、復旧担当 | 最低でも自動日次整合dump + migration前backup + EC2外暗号化S3。日中24h損失が不可なら追加頻度/PITRを設計 | volume永続化はbackupではない。binlogをEC2内に置くだけではホスト喪失を救えない | 今すぐ（RPO/RTO）、本番公開前（実装） |
| restore・restore test | 台帳に復元訓練 | 手順/テストなし | 未実装・未決定 | 対象選択、復旧手順、再開承認 | backup完全性、schema/routine/trigger、秘密設定 | 空の隔離DBへ復元し件数・提出/ログ・同意・権限・AI状態を確認、RTOを測る。本番前と定期/大きな変更後に実施 | dump成功だけでは復旧可能と判断できない | 本番公開前 |
| 研究終了後の削除 | 通常は論理削除、満了時物理削除は別管理 | 一部論理削除あり、全体削除手順なし | 方針決定済み・期限未決定・未実装 | 運用DB/研究抽出/backup/logの各期限、実行者 | 学校/大学規程、同意文書、S3 lifecycle/versioning | 対象別保持表と監査付き削除手順。backupも期限満了/削除対象に含める | 論理削除やEC2停止はデータ廃棄ではない | 本番公開前（方針）、運用開始後でも可（終了時の実行） |

backupはInnoDBの整合性を保つ方式を選び、DDL並行実行を避ける。必要なroutine/trigger/event、Flyway履歴、charsetを含むことをrestoreで確認する。S3 versioningは誤削除防止に有用だが、保持期限・削除義務と整合させる。EBS snapshotは補助として検討できるが、DB整合backup/restoreの代替と決めつけない。

失敗時は公開/app起動を止め、Flyway履歴と実schema・エラーを確認する。適用済みSQLの編集、無条件 `repair`、`clean`、DB初期化でごまかさない。承認済み修復migrationで前進するか、停止中のpre-migration backupへ復元する。`repair` はschemaを復元しない。

## 6. 環境変数・秘密情報

### 現行コードからの棚卸し

`.env.example` はなく、実際の見本は [.env.sample](../../.env.sample)。`.env` は [.gitignore](../../.gitignore) で除外されるが、`.env.production` 等は現行の除外規則では自動的に保護されない。

| 項目/変数 | 読取元・用途 | 現行見本/既定 | 必須性・本番注意 |
|---|---|---|---|
| `PROJECT_NAME` | Composeのworking directory/bind mount | 見本あり | 開発用。本番image方式では原則不要。Compose project名とは区別 |
| `APP_PORT` | Composeのhost公開8080 | 見本あり | 開発用。本番はappポートをhostへ公開しない |
| `DB_HOST` | Client/Flywayの接続 | Compose appでは `db`。Flyway単独の既定はlocalhost | app必須。migrationの実行networkに合わせる |
| `DB_PORT` | Client/Flyway、Composeのhost公開 | app内3306、見本のhost port3307 | app必須。同じ変数名の内外の意味を混同しない |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Client/Flyway、Compose DB初期化 | 見本あり（開発専用） | app必須、password秘密。Flywayは現状同じuserを読むので実行時注入を分ける |
| `DB_ROOT_PASSWORD` | ComposeからMYSQL_ROOT_PASSWORDへ | 見本あり（開発専用） | DB初期化/管理限定。appに渡さない |
| `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | MySQL image初期化 | ComposeでDB変数から変換 | 既存volumeでは変数変更だけで既存DB/user/passwordが更新されるとは限らない |
| `PYTHON_RUNNER_URL` | PythonRunnerClient | Composeで `http://python-runner:8090` | 必須、未設定は初期化エラー。見本には未掲載 |
| `PYTHON_RUNTIME_IMAGE` | runner | `python:3.12-alpine` | worker側設定。公開前に承認image/digestと更新手順を決める |
| `RUNNER_PORT` | runner待受 | `8090` | 内部port。外部公開しない |
| `PYTHONDONTWRITEBYTECODE` | runner/runtime Python | Dockerfile/実行時に1 | 秘密ではない、読取専用実行の補助 |
| `PPE_STUDENT_CODE` | 一時実行containerへrunnerが注入 | requestのsource | 管理用 `.env` 項目ではない。生徒コードなのでinspect/logアクセスを制限 |
| `GEMINI_API_KEY` | GeminiEvaluationClient、Compose | 見本は空、Compose未設定時空 | AIには必要。空でもapp全体を起動禁止にはしないが評価は失敗 |
| `GEMINI_API_SMOKE_TEST` / `GEMINI_API_DIAGNOSTICS` | Gemini client診断・外部APIテスト | 未指定はfalse相当 | 本番では未設定/false。両方trueで限定的なAPIエラーメッセージを追加する |
| `STUDENT_PORTAL_HOST` / `TEACHER_PORTAL_HOST` | 認証Filterの学校/教師ポータル振り分け | Java既定は `student.ppeval.net` / `teacher.ppeval.net`。Composeと `.env.sample` は `.localhost` | 開発Composeは `.localhost`、本番は取得済みの各ホスト名を明示設定する。両方を別ホストにする |

ログイン認証では `/student/**` を `STUDENT_PORTAL_HOST`、`/teacher/**` と `/admin/**` を `TEACHER_PORTAL_HOST` に振り分ける。環境変数は正規ホスト名を設定するもので、DNS/TLS/reverse proxyを構成するものではない。本番proxyは外部Hostを保持してappへ転送し、両ホストのDNSとHTTPS証明書を別途設定する。アプリは誤ったポータルのURLを該当ホストのログイン画面へリダイレクトする。`web.xml` のsession cookieにDomainを設定しないことが重要であり、ブラウザーは同名のJSESSIONIDをホストごとに別々に保存するため、教師と生徒のログインが独立する。セッションcookieのSecure/SameSite、proxy信頼、session timeout、モデルID、API timeout/retry、worker数、Hikari poolサイズに対する**運用環境変数は現行実装にない**。URLはproxy/DNS、sessionはweb.xml/Java、モデルはprompt/DB、他はコード・propertiesに置かれている。存在しない `PRODUCTION_URL` 等を必須設定として捏造しない。

DB/API統合テストには `EVALUATION_DB_TEST`、`CONSENT_DB_TEST`、`SCHOOL_DB_TEST`、`TEACHER_ACCOUNT_DB_TEST`、`EXERCISE_DB_TEST`、`SURVEY_DB_TEST`、`TEACHER_TASK_DB_TEST` とbrowser fixture/保持用flagがある。これらは検証用で、本番appの設定見本へ混ぜない。実行対象DBに制約があるため、production DBでテストを有効にしない。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| `.env` / EC2管理 | ローカル `.env`、本番はParameter Store SecureStringから注入（Q7回答） | Composeで環境変数、Clientは必須placeholder未設定で例外 | store方式は決定済み・一部実装済み・本番未実装 | parameter参照、取得時点、閲覧者、失効/更新、KMS/IAM | 研究利用規程、key管理者、最新料金/機能 | 決定したSecureStringをEC2 roleで取得し必要serviceだけ注入 | 現行Javaはstoreを直接読まない。deploy側に注入処理が必要 | AWS構築前（権限設計）、初回デプロイ前（実装） |
| EC2の一時env file | 台帳は本番env等へ反映 | 権限/配置/清掃手順なし | 未決定・未実装 | ファイル生成/削除、owner、backup除外 | Compose実行user、store取得方式 | repo外の管理者限定ファイル、厳格な権限、shell history/CI logへの値出力禁止 | secret storeを使っても注入後の平文・Docker inspectは保護が必要 | 初回デプロイ前 |
| 見本/Git除外 | 秘密をGitに含めない | `.env.sample` は開発password例、`.env`のみ除外 | 一部実装済み・要確認 | 本番見本の名前/必須項目、除外規則 | 本番Composeの入力契約 | 実値なし見本と説明を作る。DB/runner/key・store参照・非秘密の運用値を実装に合わせて載せる | 見本に現在存在しない運用設定を置いても動かない | 初回デプロイ前 |
| 秘密/鍵更新 | 責任者を決める方針 | rotation automationなし | 未決定・未実装 | DB/Gemini/SSH/TLS鍵の更新・漏洩対応 | 各service再読込/再作成条件 | API key、DB password、TLS/SSH秘密鍵、backup credential、個人データはcommit禁止。更新後の疎通と旧資格失効を確認 | 単にファイルを書き換えても稼働中の環境変数は変わらない | 本番公開前 |

Q7への回答で、本番のGemini key・DBパスワードはParameter Store SecureStringで管理すると決定した。単一EC2でも既存AI設計のsecret store要件に従い、管理者限定 `.env` だけを正式な秘密保管元とはしない。取得後の一時ファイル・環境変数・Docker inspectへのアクセス保護は別途必要。

## 7. Gemini API

根拠: [Gemini client](../../src/main/java/control/evaluation/GeminiEvaluationClient.java)、[評価worker](../../src/main/java/control/evaluation/EvaluationWorker.java)、[教師AI client](../../src/main/java/control/teacher/TeacherPromptAiClient.java)、[AI設計](../ai-api-integration-design.md)。SDKではなくJava HttpClientによるREST `POST https://generativelanguage.googleapis.com/v1beta/interactions`、key header、`Api-Revision: 2026-05-20`、JSON schema指定。外部仕様の現行保証は別途確認する。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| API/モデル/本番key | Gemini、台帳で管理者決定 | モデルはDB/prompt、教師許可値と疎通モデルに差分 | 一部実装済み・未決定・要確認 | project、billing/tier、利用可能モデルと版、key管理者 | Console/公式情報、実APIの合成縦断、精度 | 最新条件を確認し教師設定/評価requestを統一。疎通モデル名を本番確定としない | 一覧掲載・合成成功は本番payloadの成功保証ではない | 今すぐ（管理/予算）、本番公開前（最終受入） |
| timeout・応答上限・retry | 同一1回+簡略1回retry | connect10秒/request60秒、出力4096tokens、受信後1MB検査。最大3attempt、408/429/5xx等retry | 実装済み・不足あり | 上限、待ち方、キャンセル、教師HTTP timeout | 429/503/timeout頻度、API Retry-After | bounded backoff/jitter・Retry-After尊重を実装・検証、教師の同期AI要求も時間見積 | 現行は即時retry、共有rate limitなし。受信後サイズ検査は受信中メモリ上限ではない | 本番公開前 |
| rate limit/quota・同時評価 | 上限と同時利用を照合 | 通常評価/previewなど別worker、教師同期呼出しあり。全体のRPM/TPM制御なし | 未決定・未実装 | 総同時数、queue許容量、再評価規模 | 契約quota、token量、通常+preview+教師処理 | API共有枠を制御、queue長/最古待ち時間を監視。quota変更時に負荷再確認 | 1workerずつでも全機能の合計呼出しは1にならない | 本番公開前 |
| 障害時・他機能継続 | 失敗表示、新評価履歴でretry | 非同期queue/worker、エラー時failed、key空でもclient構築可 | 一部実装済み・継続性要確認 | AI停止時の授業、通知、再開/再試行 | 授業で評価必須か、DB/HTTP余力 | 保存/閲覧/Python実行は継続、AIだけ利用不可と明示。評価完了依存のアンケート等は使えない旨を表示 | 非AI機能を巻き込まない。ただし横断受入は必要 | 本番公開前 |
| 再起動・processing滞留 | 履歴保持、失敗再試行 | 通常評価claimはqueuedのみ。previewには5分経過running回収処理あり | 要確認・不足あり | 強制停止した通常in_progressの回収 | app kill/DB断後の状態、二重評価防止 | 通常評価のlease/回収・再実行を設計し検証。更新時はqueue drain | previewの回収実装を通常評価にもあると推測しない | 本番公開前 |
| 外部保存/個人情報・費用監視 | 送信最小化/非識別化、保持条件要確認 | redactor/研究ID、DBにrequest/response/snapshot保存。clientは `store` 未指定 | 一部実装済み・未決定 | provider保持/学習利用/所在、`store`、token/cost上限 | 有料/無料条件、研究倫理、自由記述、usage出力 | 承認済み条件で必要最小限送信。providerの現行既定を確認、API使用量・費用をConsole+安全な集計で監視 | `store:false` の診断成功と本番要求は別。redactorは全個人情報除去の保証ではない | 本番公開前 |

2026-10-07のQ15回答で、Geminiの契約準備を今から開始する許可を得た。契約完了・実API試験再開・実生徒データ送信の承認とは区別する。

実API受入はロードマップ工程19で再開指示待ち。今回呼び出さない。開発中はモデル/条件の調査・テスト設計を進め、呼出しは許可・費用確認後に合成データのみで行う。過去の合成評価→DB成功記録はあるが、本番モデルの全面変更・正式設定の最終受入完了ではない。

## 8. Pythonコード実行サンドボックス（公開前の必須ゲート）

根拠: [runner](../../containers/python-runner/runner.py)、[Compose](../../docker-compose.yml)、[runner Dockerfile](../../containers/python-runner/Dockerfile)、[Java client](../../src/main/java/control/student/PythonRunnerClient.java)、[エディター計画](./feature-plans/student-editor.md)。Javaは内部HTTPで常駐runnerを呼び、runnerがDocker CLIで**実行ごとの別container**を生成する。PythonコードをJava/runnerプロセス自身で直接実行する構成ではない。

以下の「実装済み」は設定/処理の確認であり、本番EC2における隔離の証明ではない。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| Docker隔離・network | 別環境、外向き遮断 | `docker run --network=none`、生徒containerへsocket/DB/key mountなし | 実装済み・実機要確認 | 使用daemon/kernel/image、隔離基準 | EC2でのDNS/外向き/metadata到達、脆弱性更新 | 本番と同じ基盤でネットワーク拒否・metadata拒否を確認 | DockerはVMと同等のkernel境界ではない | 初回デプロイ前 |
| CPU・無限ループ | CPU4秒、全体60秒 | `--cpus=0.5`、`--ulimit=cpu=4:4`、deadline60秒 | 実装済み・要確認 | 同時実行時のhost余力 | busy loop、子process、短い多連続実行 | 安全な合成試験で終了とapp/DB継続を確認 | ulimit CPUはprocess単位であり全子process合計4秒を保証しない | 本番公開前 |
| memory・PID・fork bomb | 資源制限 | 各子128MiB、swap128MiB、PID32。親128MiB/64PID | 実装済み・要確認 | 上限、daemon/cgroup対応 | OOM、PID増加、親worker余力 | 隔離された試験でmemory/PID上限と確実なcleanupを測る | fork試験を共有開発host/本番へ無制限に流さない | 本番公開前 |
| timeout・対話 | 無通信30秒、全体60秒 | 対話monitorは入力/stdout/stderrの活動で30秒更新。同期実行は全体60秒 | 実装済み・要確認 | 同期経路にも無通信条件を適用するか | `sleep`、入力待ち、途切れたbrowser、image pull遅延 | 対話と同期の契約差を確認、実行imageを事前pull。Java同期timeout65秒/対話要求5秒も検証 | 実行60秒にcleanup/通信が加わる。仕様との差を隠さない | 本番公開前 |
| 出力/入力/source上限 | stdout10,000文字 | stdout/stderr各32KiB、input合計8KiB、source64KiB、request512KiB | 実装済み・仕様不整合 | 文字/byte、stdout+stderr合算、保存/表示上限 | ASCII/多byte/不正UTF-8/大量出力 | 第0節差分を解消して境界テスト。子Docker log側も上限化 | メモリ出力上限だけではdaemonログ容量を完全制限しない | 本番公開前 |
| filesystem・ファイルアクセス | 原則read-only、機能上のfile I/O制約 | `--read-only`、/tmp 8MiB tmpfs(noexec/nosuid/nodev)、fsize1MiB、nofile64 | 実装済み・要確認 | 許すファイル操作、host可視性 | runtime内の読取、/tmp書込、他実行との共有 | host mountなしを維持。file I/O非対応は機能上の案内と区別して確認 | Pythonのopen/importを禁止してはいない。runtime内ファイルは読める | 本番公開前 |
| non-root・capability | 最小権限 | 生徒container UID/GID65532、cap drop ALL/no-new-privileges、Python `-I -S -B` | 実装済み・要確認 | image適合、seccomp/AppArmor | host機能、更新版 | 生徒non-rootを維持、Docker default seccomp等の有効性を確認 | `-I/-S` は安全な言語制限の代替ではない | 初回デプロイ前 |
| runner管理権限・host影響 | 本番daemon境界を別評価 | runner自身はroot、host `/var/run/docker.sock` をmount | 不足・要確認（公開ブロッカー） | 本番の実行専用daemon/OS権限境界 | rootless互換、socket API、他service到達、運用責任 | 単一EC2内でも別OS userの専用rootless Docker等を候補に実証。app/DB用rootful socketを現状のまま渡さない | runnerを侵害された場合、host daemonへの広い権限はDB/secret/EC2へ影響し得る。non-root生徒設定だけでは解決しない | AWS構築前（設計）、本番公開前（実証） |
| コンテナ破棄・孤児 | 実行後破棄 | `--rm`、timeout/cancel等でforce rm、cleanup失敗は503。daemon停止/runner強制終了時の回収なし | 一部実装済み・不足 | 終了後の回収、識別label、監視 | runner kill、daemon断、reboot、rm競合 | 専用実行領域のcontainerだけを識別・期限付き回収。終了/再起動試験 | 正常経路のrm成功だけでは孤児なしを保証しない | 本番公開前 |
| 同時実行・公平性 | queue/固定worker | 同期/対話共有4枠、満杯429、session record32、完了後保持120秒 | 実装済み・queue不足・未決定 | 待機/拒否、1人枠、総枠、過剰再試行 | 授業で同時に実行する人数と所要時間 | 4枠を固定正解とせず負荷試験。ユーザー単位制限・待ち方を実装 | 1人の連続実行で授業の他利用者を圧迫しない | 本番公開前 |
| runner API・親service | 非公開内部通信 | host port公開なし、healthは固定ok、内部API認証なし | 一部実装済み・要確認 | trust boundary、HTTP上限/timeout、認証要否 | app以外から到達するservice、request thread数 | appからのみ到達、必要なら内部認証、要求速度/時間を制御、実行probeを別途用意 | `/health` はDocker daemon/子image実行可能性を確認しない | 初回デプロイ前 |

4子実行の設定上限の合算は512MiB/2CPU相当だが、daemon、親runner、app、MySQL、Nginx、OSの消費を含まないし、実測必要RAMではない。親runnerの128MiB制限で子の総消費が抑えられると誤解しない。

専用rootless daemonでもkernel共有は残る。単一EC2で承認された安全水準を満たせない場合は、生徒コード実行の公開を止め、より強い隔離/専用実行hostを例外として検討する。単一EC2前提のために未検証の境界を「安全」と扱わない。

## 9. セキュリティ・権限境界

本節は配備準備の確認項目であり、全経路の安全性や侵入耐性の認証ではない。未完成の教師機能は認可も未完成である。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| HTTPS/session/cookie | TLS、30分、logout破棄 | HttpOnly、COOKIE tracking、30分、login時session再生成/CSRF rotation。Secure/SameSite明示なし | 一部実装済み・未実装/要確認 | Secure/SameSite値、proxy信頼、session喪失 | HTTPSレスポンスのSet-Cookie、再起動 | Secure/HttpOnly/SameSiteを明示し実HTTPで検証。app再起動で再loginになる運用を告知 | Tomcat既定値任せを本番保証にしない | 本番公開前 |
| CSRF/XSS | token/出力escape | 共通CsrfTokensと各POST呼出し、JSP/JSにescape/textContent利用 | 実装済み（対象経路）・横断要確認 | 全mutation経路、multipart/JSON、AI出力/CSV | 最終endpoint一覧、stored payload、error表示 | 不正token拒否、保存コード/AI文/自由記述のstored XSS回帰、CSV formula対策を確認 | helperがあるだけで全画面対応とは判断不可 | 本番公開前 |
| SQL Injection/PreparedStatement | JDBC prepared statement | 認証・教師権限・主要DAOでparameter binding | 実装済み（対象DAO）・横断要確認 | 動的SQL/検索/sort/exportの安全性 | 最終DAO差分、許可列のwhitelist | 全入力経路でbinding/識別子許可リストを確認 | 一部DAOの存在は将来機能までの安全保証ではない | 本番公開前 |
| RBAC/学校間分離 | 本人限定、教師は許可学校、管理者は運用権限 | Filterのrole、Control/DAOの所有者/学校/機能権限、教師version失効 | 一部実装済み・後続未実装 | 管理者の学習内容閲覧、学校跨ぎ、停止後session | 2校fixture、全機能、GET/POST/CSV/実行 | 生徒間/学校間/教師-管理者を全経路で検証。Filterがadminのteacher URL到達を許してもControlで業務許可しない | UI非表示やroleだけでは学校分離にならない | 本番公開前 |
| password/brute force/rate limit | 8〜32文字、種類条件、5失敗30分lock | PBKDF2 SHA256 60万回/salt16byte、DB lockout、教師資格一時表示/履歴 | 実装済み・rate limit不足 | login/IP/ユーザー別上限、管理者復旧、学校level | 学校IPのNAT共有、CPU負荷、account発行手順 | account lockに加えproxy/appの適切な制限。共有IPで授業全体を遮断しないよう試験 | password hashing自体がCPUを使う。lockoutだけでは大量要求を抑えない | 本番公開前 |
| 管理画面/SG/firewall/不要port | IP制限等追加防御 | 管理者roleあり。Nginx/IP制限/host firewall未実装 | 一部実装済み・未決定 | 管理接続元、管理者本人確認、許可する画面 | 管理者の固定IP、緊急経路 | admin画面のIP/VPN等の追加防御を選定、SG+Dockerを含む到達性検証、不要Tomcat資源除去 | host firewallだけでDocker公開portを制御できると仮定しない | 本番公開前 |
| 個人情報・秘密ログ・依存更新 | 仮名化、秘密非出力 | redactor、safe failure、login/audit記録。保持/依存更新運用なし | 一部実装済み・要確認 | 閲覧権、redaction、保守日程 | IP/UA/login ID/生徒入力/SQL例外、image/library advisory | secret・生徒code・生session ID・API headerを運用ログに記録しない。DB監査は権限/保持管理。公開前に依存/imageのsupportとadvisoryを確認 | 仮名ID/IPも保護対象。ソケット/inspect権限も秘密閲覧につながる | 本番公開前 |

## 10. ログ・監視（研究用途の必要十分な範囲）

現在はJavaのJUL/ServletContext.log、認証/監査のDAO記録がある。DBに `application_error_logs` 等のtableがあることは、全例外がそこへ保存されている証拠ではない。runnerはHTTP access logを抑止しcleanup失敗等をwarningにする。Nginxログはservice自体が未実装。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| Java/Tomcat・Nginx・Docker・MySQL・error | app log rotation、死活監視 | app/dev logs、DB監査。Docker rotation/Nginx/独自Tomcat/MySQL log方針なし | 一部実装済み・未決定 | stdout/fileの正本、保持日、閲覧者 | 障害追跡に必要なID/時刻、個人情報 | 各serviceのerror/access必要項目を最小化、Docker log上限とTomcat file rotationを別管理、MySQL verbose/general logは常時有効にしない | stdout制限だけではTomcat fileはrotationされない。コード/資格情報を漏らさない | 初回デプロイ前 |
| CPU/RAM/disk/DB容量 | 単一hostの維持 | metric/alert設定なし | 未実装・未決定 | 警戒値、通知先、対応時間 | 負荷baseline、EBS余力、DB growth | CloudWatch標準metric + agent等でRAM/disk、DB接続/容量、queue長。利用量に合わせしきい値調整 | EC2標準だけでOS RAM/diskが得られると仮定しない | 本番公開前 |
| service停止・TLS期限・backup失敗 | 台帳に監視/復旧 | 外部監視/通知なし、health一部あり | 未実装・未決定 | 外部死活、失敗/未実行検知、一次対応 | 授業時間、連絡先、backup予定時刻 | 外部HTTPS監視、証明書期限、backup freshness/restore結果、container restart/worker滞留を通知 | 単一hostが停止するとhost内監視だけでは通知できない | 本番公開前 |
| 研究用途の運用水準 | 必要十分、過剰構成を避ける | SRE運用なし | 未決定 | 24h対応か授業時間対応か、保守頻度 | 学校との合意、RPO/RTO | 1通知先+代替担当、授業前点検、日次backup、disk/HTTPS/停止監視を必須。大規模log基盤/常時on-callは初期不要 | 研究規模でもデータ喪失・授業停止の検知は省略しない | 本番公開前 |
| 詳細APM・dashboards | 固定要件なし | 未実装 | 未決定 | 導入効果/費用 | 最小監視で解けない障害 | 必要が生じたら追加。中央log収集は保持量を制限 | 初期運用を複雑化しない | 運用開始後でも可 |

## 11. デプロイ方法・初回フロー

### 選択肢

| 項目 | 現在の仕様/実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由・利点 | 欠点 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| EC2でgit pull/build | 開発Composeなら可能な資材はある。本番手順なし | 未実装（配備） | 採用するcommit、build用権限/余力 | EC2容量、repo権限、build時間 | 合成データの検証環境では選択可。本番では第一候補にしない | 操作が少なく初回の理解が容易 | 本番へソース/Gradle/依存取得を持込、buildがDB/Pythonと競合、再現性/rollback管理が弱い | 初回デプロイ前 |
| ローカル/CIでWAR・image作成して転送 | WAR taskあり、配備imageなし | 一部実装済み | build場所、image archive、checksum | 開発機arch、転送容量、運用頻度 | **初期推奨: 固定Linux build環境でtest/WAR/本番image生成 → image archive等でEC2転送 → 手動Compose** | 単一EC2で理解しやすく、build負荷を本番から除去、旧imageを保持できる | 転送・tag・hashの手動管理が必要。Mac/ARM→EC2のarchに注意 | 初回デプロイ前 |
| Docker Registry（GHCR/ECR等） | Registry連携なし | 未実装・未決定 | registry/private設定、認証、tag/digest | repo公開性、image中の資材、費用 | 更新が多ければ早期にprivate registryへ移行、digest固定 | 転送/履歴を簡単にする | credential/IAM/lifecycleが増える | 初回デプロイ前（採用する場合）、運用開始後でも可（移行） |
| GitHub Actions CI/CD | templateのみ、workflowなし | 未実装・未決定 | CI test/build、承認、配備権限 | branch/tag運用、runner arch、DB testsの隔離 | まずCI test/buildだけ。配備は手動承認、必要なら後で自動化 | 継続確認と成果物再現性に有益 | 全自動migration/公開は誤反映リスク。secret/OIDC権限設計と通知が必要 | 本番公開前（検証再現性）、運用開始後でも可（自動配備） |

推奨はRegistryを必須としない手動image配備から始める。すでにRegistryを運用できる場合は最初から利用してよい。Kubernetes/ECS/RDS/ALBを今回の初期前提にはしない。

### 本番資材整備後に採用するフロー（現時点でそのまま実行できるコマンド集ではない）

```text
承認したGit commit/tag（clean checkout）
  ↓ 固定Gradle8.10.2/Java21のLinux環境
test + WAR生成 + Flyway SQL/設定の同一版保存
  ↓ WARを含むTomcat9 image（EC2のarchに合わせる）
image tag/digest・checksum・migration版を記録
  ↓ archive転送またはprivate Registry pull
EC2へ配置（repo外secretをroleで取得・必要先へ注入）
  ↓ 本番ComposeでDB/runner準備、Python runtime image事前取得
DB readiness → flywayInfo/Validate → Migrate → Validate
  ↓ 成功時のみ
Tomcat起動 → app readiness → Nginx/TLS → Smoke Test
  ↓ 認可・保存・実行・復旧条件を確認して公開承認
運用開始
```

- Flyway SQLはWARだけでなく、version固定したmigration実行資材にも含める。実行は短命のGradle/migration container等からDB networkへ接続し、migration専用userを注入する。
- 初期管理者は [createInitialAdmin task](../../build.gradle) / [bootstrap](../../src/main/java/control/auth/InitialAdminBootstrap.java) で管理された初回処理を行う。標準rubricは同buildの `registerStandardRubric` と正式資料を確認する。
- `seedDemoData`、[開発seed SQL](../../scripts/dev/seed-student-editor-preview.sql)、[開発survey SQL](../../scripts/dev/configure-student-survey-template.sql)は本番初期化の代わりに使わない。V10仮同意文書がactiveのまま公開されないよう、正式版を登録して状態を確認する。
- 初回Smoke Test: HTTPS/redirect/cookie、未認証拒否、3role login、別校/他人生徒拒否、合成課題の保存→再読込、合成Python実行とbusy/timeout、AI key/障害時の失敗表示、アンケートの同意条件、ログ/backup通知。実API試験は別途許可後のみ。
- 現行READMEの `docker compose exec -T app gradle ...` は**開発用app**向け。本番Tomcatへ同コマンドを流用しない。

## 12. 更新・ロールバック

根拠: [全体手順](./development-and-deployment-flow.md)、[決定台帳](./production-operations-decisions.md)、[Flyway SQL](../../src/main/resources/db/migration/)、[worker lifecycle](../../src/main/java/lib/mysql/DataSourceLifecycleListener.java)。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| 新version・version管理 | Git・版付き成果物 | Git/SQL versionあり、release手順なし | 一部実装済み・未決定 | tag、承認、履歴、配置先 | artifact hash、schema version、config変更 | release manifestにcommit/image digest/migration版/設定版（秘密なし）/担当/時刻/検証を記録 | 同じソース名だけでは復旧対象を特定できない | 初回デプロイ前 |
| migration前backup・maintenance | 更新/互換性確認 | 自動backup/maintenanceなし | 未実装・未決定 | 停止窓、書込停止、backup成功ゲート | 授業、API/Python実行中、復旧所要 | 告知→新規操作停止→実行/評価drain→app停止→整合backup→migration→新image→Smoke | migration中の保存/評価とbackup整合性を守る | 初回デプロイ前 |
| appだけのrollback | 直前版へ戻す方針 | 旧image保持/切替資材なし | 未実装・未決定 | 旧appと新schemaの互換性、判断時間 | backwards-compatible migration、旧version test | 旧imageを保持。互換ならimage/configを戻しSmoke、migration履歴は戻さない | image rollbackとDB rollbackは別 | 初回デプロイ前 |
| DB変更を伴うrollback | データ保持 | V migrationのみ、undo自動化なし | 未実装・要確認 | forward fixかbackup restoreか、損失承認 | MySQL DDL状態、backup時点、再開後書込 | expand/contractを優先。restoreが必要なら書込停止、旧appとbackupを一組で復旧、RPO損失を記録 | 旧WARに戻すだけで新schemaは元に戻らない。SQLファイル削除も不可 | 各migration前 |
| downtime・停止中データ | 単一EC2 | maintenance/readiness/drain手順なし。対話sessionはメモリ | 未決定・未実装 | 許容停止、通知、autosave/対話終了、queue回収 | 授業時間、最大処理/終了時間、再login | 短い計画停止を許容する小規模運用。無停止/blue-greenを初期必須にしないが、通常評価滞留を検証 | 実行中対話とin-memory sessionは再起動で失われ得る | 本番公開前 |

更新フローではbackupを**schema変更前**に取り、旧版へ戻せる間は成果物を消さない。新versionで書込再開後のDB restoreは、その後の提出・ログ・同意を失う可能性があるため担当者が独断で行わない。単一EC2で検証用Composeを併設する場合も本番DB/volume/port/secretと分離し、CPU/RAMを奪わない条件で行う。

## 13. 本番データと研究データ

根拠: [機能仕様](../function-specification.md)、[実装契約IC-008/010](./implementation-contract.md)、[DB定義](../database-design/table-definitions.md)、[評価入力DAO](../../src/main/java/dao/EvaluationWorkerDao.java)、[評価/アンケート計画](./feature-plans/student-evaluation-survey.md)。

2026-10-07回答反映: Q13の保持終了目標は2027年3月。月内の具体日・対象別期限・規程との適合は未確定で、運用終了日や全backupの保存期限と同一視しない。Q17は必要最小項目の仮名化・出力時の最新同意確認・撤回後の今後の研究利用からの除外を採用し、既出力の扱いは正式規程に従う。具体的な出力担当・提供先・項目と、Q14の研究利用の了承・正式文面は未確認。

**研究不同意/未確認でも通常学習・AI評価・コードログ収集は継続する合意がある。** 一方、研究集計/外部出力はその時点の最新同意を確認し、撤回前のデータも今後の研究利用から除外する。後から同意へ変更した場合は変更前データも対象とする方針がある。保存時の同意metadataだけで研究出力可否を判断しない。課題別アンケートの現行アクセスは同意済み本人等の条件を持つ。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| コードログ・下書き・提出・演習 | 通常学習記録、提出全版、演習は課題ログと分離 | 対応DB/保存DAO、30秒dirty時記録、実行記録あり | 実装済み（主要範囲）・保持未決定 | 運用上の閲覧/保持、研究抽出対象 | 学校授業期間、研究計画、バックアップ | 本番DBを授業運用の正本、承認研究抽出は別保管・別権限 | 運用DB全量dumpをそのまま研究データにしない | 本番公開前 |
| AI評価・request/response/snapshot | 版固定、根拠追跡 | 通常評価の永続記録、preview専用一時table/30分期限・cleanup | 実装済み・保持/外部条件未決定 | 永続/一時の保持、backupに残る一時data | provider方針、研究再現性、snapshot容量 | 評価版・model/prompt/rubric/evidenceを保持する範囲を承認、previewは期限処理確認 | previewのDB削除後でも旧backupには残り得る | 本番公開前 |
| アンケート | 課題別、同意条件、評価別履歴 | 生徒回答の下書き/提出、一意制約。教師結果/研究出力は後続 | 一部実装済み・出力未実装 | 正式設問、自由記述の保護、抽出/撤回対応 | 学校・大学の研究承認、形式、研究担当者 | 最小項目・仮名出力、同意再確認と自由記述review | user_id付きは完全匿名ではない | 本番公開前 |
| 研究同意・正式版・紙同意 | 回答変更/履歴、撤回の研究除外 | 同意DAO/履歴、V10は仮文面active | 一部実装済み・正式条件未決定 | 文面、対象年齢/保護者、紙と電子の正本、撤回連絡 | 倫理審査、学校運用、保護者同意要否 | 正式version/有効日を承認し登録、通常収集と研究二次利用/外部AIを説明し分ける | 「不同意なら何も記録しない」と誤解させない | 今すぐ（承認開始）、本番公開前（反映） |
| ログイン・監査・credential履歴 | 運用/追跡用、通常削除対象外 | login ID/IP/UA等、操作監査、password hash/履歴あり | 実装済み（対象処理）・保持未決定 | 運用アクセス、研究使用要否、保存期限 | 障害/不正追跡、研究上必要性 | 原則研究抽出から除外、秘密/個人情報の保持を最小化 | 学習成果と運用セキュリティ情報を混同しない | 本番公開前 |
| 匿名/仮名ID・対応情報 | survey研究はusers.user_id、Geminiはresearch_subject_code、login IDと分離 | 内部ID/AI研究ID生成、送信redaction | 方針決定済み・一部実装済み・出力要確認 | 対応表アクセス、再識別リスク、研究提供項目 | 研究分析の結合キー、学校/クラス情報 | Gemini識別子とsurvey仮名IDを混同しない。対応表と運用DBは研究提供先へ渡さない | user_idが運用DBへ対応できるので完全匿名と表記不可 | 本番公開前 |
| 保存期限・終了/撤回後処理 | 保持終了目標は2027年3月（Q13）。必要最小項目の仮名出力・最新同意確認・撤回後の今後の研究除外を採用（Q17）。論理削除/履歴保持、物理削除別手順 | 全対象の終了処理・研究export制御は未整備 | 月単位の保持目標/研究出力方針は決定済み・詳細未決定・未実装 | 運用/研究/ログ/backup別の具体日、出力担当/提供先/列、削除責任、既出力の撤回対応 | 正式研究保持規程、既配布データ、学校/大学の了承 | 規程と目標月を照合し、撤回後の以後集計/出力除外、既出力への対応、終了時の廃棄確認を手順化 | 5ヶ月見積や目標月だけを倫理上の保持承認に転用しない | 本番公開前 |

生徒の自由入力に氏名等を書かないよう案内しても、混入は起こり得る。現行redactorは既知display/login/student ID文字列とemail/phoneパターンの置換であり、任意の氏名・住所等を完全除去するものではない。研究抽出とGemini送信の双方で扱いと検証条件を決める。

## 14. 負荷・容量・受入条件

Q8回答（2026-10-06）: 実生徒向け公開前に300同時接続を確認する（Q8-1）。Python実行要求は同じタイミングに最大100人程度を想定（Q8-2）。授業自体は60分だが、容量見積・負荷試験は120分程度を想定する（Q8-3）。これらは受入・試験条件の決定であり、性能達成・100並列実行・EC2サイズの決定ではない。

### 仕様の固定前提と見積を分ける

| 項目 | 仕様・既存資料の値 | 実装で確認できること | 状態/判断 |
|---|---|---|---|
| 利用規模 | A150人/3ヶ月、B200人/1ヶ月、期間重複。総350、接続ピーク300 | user登録数に応じるDB構造 | 仕様の前提決定済み、性能実測なし |
| 課題評価 | A750+B1,200=1,950件 | 提出版/評価履歴/再評価は追記 | 件数前提。再提出・retry・preview等で呼出し増加 |
| 演習 | A50files/人、計7,500。B未指定 | DBにfile/folder/sourceを保存 | B規模・平均size未決定 |
| code log | 約100MB/生徒、350で約35GBは見積 | 30秒timer、editable/dirty/保存中でない時に保存。実行/手動イベントもある | 35GBは全DB容量や実測値ではない |
| 性能目標 | 画面3秒、実行表示2秒、DB query1秒、autosave3秒 | 上限・pool・各workerあり | 本番相当で計測が必要。長時間対話60秒と「実行表示2秒」の測定対象を区別 |

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| EC2/MySQL/storage | 公開前に300同時接続確認、通常授業60分/試験120分（Q8）。24h/5ヶ月は費用試算の仮定 | 固定instanceなし、pool10、volumeのみ | 試験条件決定済み・サイズ未決定・要確認 | 許容遅延、容量headroom、実運用期間 | CPU/RAM/IOPS/lock/DB growth | 合成300session、120分を想定して認証/一覧/保存/実行/評価併用で計測。DB/backup/更新領域まで集計 | 接続人数と重い操作の同時数は別 | 本番公開前 |
| 30秒log | periodic snapshot | dirty時のみ30秒保存、同期burstの可能性 | 実装済み・負荷要確認 | 授業時間、平均source、変更率、保存同期 | 生徒端末timer、1件bytes、index/row overhead | 300全員毎周期なら平均10保存要求/秒を**試験条件**とする。30秒境界のburstも試験 | 平均10req/sは能力の保証でも実トラフィックの確定値でもない | 初回デプロイ前（条件）、本番公開前（実測） |
| Python同時実行 | queue/worker、短い実行2秒目安。実行要求の一斉集中は最大100人（Q8） | 4枠、CPU0.5/128MiB各子、429 | 要求集中の試験条件決定済み・実行枠/待ち方未決定・要確認 | 平均/最大時間、公平性、待機上限 | 100人同時要求、timeout、busy UX、host負荷 | 1/4/枠超過から100要求へ段階試験、保存/APIとの併用、授業に必要な枠/待ち方を決定 | 要求100人は100container同時起動を決定した意味ではない | 本番公開前 |
| Gemini同時評価 | 課題/preview/再評価 | 別worker+同期教師AI、shared limiterなし | 未決定・要確認 | 提出集中数、queue遅延、token budget | RPM/TPM/RPD、再評価対象、response時間 | 合成providerでqueue/stress、許可後の限定実APIでtoken/time検証 | 実APIへ無断で300件負荷を送らない | 本番公開前 |

容量式の出発点は `編集人数 × 授業秒数/30 × 変更のある周期率 × 平均snapshot bytes` に、手動保存/実行/提出、索引、AI入力複製/応答、演習、ログ、binlog、backup/image一時領域を加える。値を測らずEBS容量・EC2サイズを確定しない。試験は継続時間とp95/最大遅延、失敗率、queue滞留、容量増加を記録し、仕様の閾値そのものを確認する。

## 15. 費用・予算

[既存試算](../server-cost-estimate.md)は2026-07-05作成、5ヶ月・24h稼働、為替160円/USD、インフラ160,000円、予算基準200,000円/上限目安220,000円という**企画上の概算**である。再評価20%、入力12,000/output1,500tokens、2,340callsも仮定。最新のAWS/Gemini契約金額や承認予算として採用しない。

Q2回答（2026-10-06）: 総予算上限は最新試算を確認してから決定する（Q2-1）。既存の20万円/22万円案は承認額とせず、総額・月額配分・通知しきい値・監視手段は未確定。費用警告は本人が受け取り（Q2-2）、停止・縮小も本人が判断し、授業に影響する場合は学校へ連絡する（Q2-3）。通知先・判断者は決定済みとして[決定台帳](./production-operations-decisions.md)へ記録した。

| 項目 | 現在の仕様 | 現在の実装 | 状態 | デプロイ前に決めること | 判断に必要な情報 | 推奨案 | 推奨理由 | いつまでに必要か |
|---|---|---|---|---|---|---|---|---|
| AWS課金 | 5ヶ月/resize運用見積 | AWS billing設定なし | 未決定・最新料金要確認 | 月/期間上限、利用期間、停止/終了 | region/type/hours、EBS GB/IOPS/snapshot、public IPv4/EIP、転送、S3容量/要求、DNS/domain、CloudWatch、secret store、registry | 公式Calculator/料金表を契約時確認し初期/ピーク/終了保存期間を分けて試算。通知閾値と停止判断を承認 | EC2停止でもEBS/EIP/backup等は課金が残り得る | AWS構築前 |
| Gemini課金/quota | Flash/Pro級概算 | clientにtoken/cost集計/予算遮断なし | 未決定・未実装 | model/tier、月上限、教師再評価上限、超過時停止 | 最新入出力単価、実token、retry/preview/生成例/再提出、billing条件 | 通常評価以外の全呼出しを計上、usage/cost監視、API機能のみ止める運用を用意 | 2,340callsは全AI用途を網羅していない | 今すぐ（budget）、本番公開前（監視/手順） |
| 承認/予算アラート | 費用警告は本人が受信、停止・縮小は本人が判断し授業に影響する場合は学校へ連絡。金額は最新試算後に決定 | 通知なし | 通知先・判断者は決定済み、金額/しきい値は未決定・通知未実装 | 支払方法/費用負担、月次/総額/緊急上限、通知しきい値・監視手段 | 個人契約の費用負担、税/為替、研究終了時期 | AWSとGoogleを別枠管理、超過前通知と本人判断の手順を設計 | alertはhard capではなく、billing反映にも遅れがある | AWS構築前 |

本書は外部料金を取得していない。最新単価・無料枠・CPU credit料金・IPv4料金・API上限・サービスsupport期限は要確認。金額を追加で確定しない。

## 公開前ゲートと判断記録

限定検証への初回配備は、AWS予算/権限・secret注入・本番image/Compose・DB migration・TLS・コード実行境界の設計が整い、合成データのみ/アクセス制限ありで開始する。以下は実生徒利用前にすべて満たす。

- sandbox境界・資源/出力/時間/回収・同時実行の受入を、本番相当EC2で完了する。
- HTTPS/proxy/cookie・CSRF/XSS・本人/学校/role境界を最終機能で検証する。
- 正式同意文面、外部AI条件、運用/研究の保持・撤回/終了処理を承認する。
- backup成功・失敗/未実行通知・EC2外保存・restoreを確認しRPO/RTOを満たす。
- 300接続を含む合意負荷で性能/容量を確認し、instance/EBS/各service上限を確定する。
- Geminiモデル/API不整合を解消し、許可後の合成縦断で評価→DB→表示を確認する。
- 初期管理者/学校/授業設定、監視/連絡、更新/rollback手順、release識別情報を用意する。
- 残る機能の公開範囲を承認し、未実装導線を成功したように見せない。

承認・値の決定は[決定台帳](./production-operations-decisions.md)へ、技術差分の合意は[実装契約](./implementation-contract.md)等へ、検証結果は対応する機能/運用手順へ記録する。本書の推奨を決定済みに読み替えない。

## A. 開発者・研究実施者に確認したい質問一覧

以下は未決事項を回収するための質問票であり、回答待ちでも独立した準備は進められる。回答を本書だけに閉じず、決定台帳・正式仕様へ反映する。

Q1の正式決定（2026-10-06、16:04 JST、決定者: 開発者・研究実施者本人）: 本番用には「本人が個人で所有・契約するAWSアカウント」を使用する。本人による訂正と正式反映の指示を根拠に、[決定台帳](./production-operations-decisions.md)へ反映済み。所有・契約形態は決定済みとし、実アカウントの設定、支払方法/費用負担、管理担当者、学校・大学の研究用途での利用許可・引継ぎ条件は別の確認事項として残す。

Q2は1項目ずつ確認する。Q2-1（総予算上限）は「最新試算を確認してから決定する」と回答済み。Q2-2（費用警告）は本人が受信、Q2-3（停止・縮小判断）は本人が行い、授業に影響する場合は学校へ連絡すると決定済み。月額配分、通知しきい値・監視手段、自動停止の有無と具体的な停止条件は、最新試算・運用条件を確認してから決める。

Q3回答（2026-10-06）: 本番での実利用開始目標は2026-10-14（Q3-1）。本人は可能ならより早い開始を希望しているが、公開ゲートを満たすことを前提とし、未達の場合は延期する。初回配備は合成データのみ・アクセス制限付きの検証環境とする（Q3-2）。「なるべく早く」という回答は終了日ではなく実利用開始の希望を指すことを確認済み。初回配備日、研究実施期間、本番運用終了日は未確定として[決定台帳](./production-operations-decisions.md)へ記録した。

Q4回答（2026-10-06）: AWSリージョンは東京 `ap-northeast-1` に決定し、決定台帳へ反映済み。学校・大学の利用規程との適合、外部AI側のデータ所在は別途確認する。

Q5回答（2026-10-06）: 利用できる既存ドメインはなく、新規取得を検討する（Q5-1）。第一候補は `programming-process-evaluator.net`（Q5-2）、取得・更新・DNS変更は本人が管理する（Q5-3）。必要な機能が揃うなら取得先DNSを使用し（Q5-4）、本番URL第一候補は `https://programming-process-evaluator.net` とする（Q5-5）。TLS証明書の期限・更新失敗通知は本人が受け取る（Q5-6）。取得可否、取得先、初年度/更新料金、DNS機能、証明書/監視機構は未確認。候補の選定と取得・公開完了を区別する。

Q6回答（2026-10-06）: EC2管理接続はSession Managerを基本とし、通常はSSHポートを公開しないと決定。EC2 role、SSM Agent、通信経路・緊急復旧は未構築・要確認。

Q7回答（2026-10-06）: 本番のGemini API Key・DBパスワードはParameter Store SecureStringで管理すると決定。EC2 role/KMS/IAM、取得・service別注入処理とrotationは未実装。

Q8回答（2026-10-06）: 公開前に300同時接続確認、Python実行要求の一斉集中は最大100人、通常授業60分・見積/負荷試験120分を試験条件として決定。実行枠/待ち方、1人あたり実行回数、EC2サイズ・性能達成は引き続き確認する。

Q13回答（2026-10-07）: 保持終了の目標を2027年3月とする。月内の具体日、対象データ別の適用範囲、正式規程との適合、削除担当/手順は未確定。運用終了日や全backupの保持期限の決定とは区別する。

Q14（説明補足・了承状況は未確認）: AWSやGeminiの契約ではなく、「生徒のコード・学習記録を収集して卒業研究に使うことについて、学校の了承や大学で必要な手続きを確認できているか」を確認する項目。対象者に説明する正式文面、保護者/紙同意の要否は学校・大学の条件に合わせて別途確認する。

Q15回答（2026-10-07）: Geminiの契約準備は今から開始してよい。契約完了、billing/tier・金額・modelの承認、実API試験の再開や実生徒データの送信許可とは扱わない。条件確認・予算確認を先に進める。

Q17回答（2026-10-07）: 提示した推奨方針に同意。承認された必要最小項目を仮名化して抽出し、出力時に最新同意を確認。撤回後は今後の研究利用・集計・出力から除外し、既出力データへの対応は正式規程に従う。具体的な担当者・提供先・列・規程は未確認。

| 番号 | 具体的な確認質問 | 決める対象・回答に必要な情報 | 期限 |
|---|---|---|---|
| Q1.（所有・契約形態は決定済み） | 本人が個人で所有・契約するAWSアカウントを使用する。管理担当者、費用負担、本番専用アカウント分離と既存アカウント利用の扱いは別途確認する。 | 個人所有・契約は正式決定。引継ぎ、IAM/MFA、請求/運用設定は未確定 | 今すぐ |
| Q2.（一部決定済み） | 総予算上限は最新試算後に決定する。費用警告の受信と停止・縮小判断は本人が行い、授業に影響する場合は学校へ連絡する。AWS/Gemini別の月額配分、通知しきい値・監視手段、停止条件は引き続き確認する。 | 通知先・判断者は決定済み。既存20万/22万円案は未承認、金額は未確定 | AWS構築前 |
| Q3.（一部決定済み） | 実利用開始目標は2026-10-14、可能なら安全条件を満たした上で前倒し。初回は合成データのみ・アクセス制限付きとする。初回配備日、研究実施期間、運用終了日は引き続き確認する。 | 公開ゲート未達なら延期。開始目標と運用期間・終了後保管を分ける | 今すぐ |
| Q4.（region決定済み） | AWSは東京 `ap-northeast-1` を使用する。学校・大学の利用規程・国内保管条件との適合は引き続き確認する。 | region決定済み、倫理/組織条件・外部AI所在は別確認 | AWS構築前 |
| Q5.（一部決定済み） | ドメイン/URL第一候補は `programming-process-evaluator.net` / `https://programming-process-evaluator.net`、本人管理、機能が揃えば取得先DNSを使用。証明書通知も本人。取得可否・取得先・費用・DNS機能・証明書/監視実装は引き続き確認する。 | 候補・方針と取得/公開を区別。URL/DNS/TLS、更新責任 | AWS構築前 |
| Q6.（方式決定済み） | Session Managerを基本とし、通常はSSHポートを公開しない。role/Agent/通信経路と緊急復旧は引き続き確認する。 | 管理経路、SG、IAM、実装未確認 | AWS構築前 |
| Q7.（方式決定済み） | Parameter Store SecureStringを使用する。parameter参照、role/KMS/IAM、取得/注入/rotationと管理者は引き続き確認する。 | store決定済み、注入/運用は未実装 | AWS構築前 |
| Q8.（試験条件決定済み） | 公開前に300同時接続確認、Python実行要求の一斉集中100人、通常授業60分・見積/負荷試験120分とする。1人の実行回数、提出集中、実行枠/待ち方、性能達成は別途確認する。 | EC2/枠/負荷、要求集中と実行並列数を区別 | 初回デプロイ前 |
| Q9. | Python枠が満杯の場合、待ち行列に入れるか、busy表示して再試行するか。待ち時間と1人の同時枠をどう扱うか。 | 公平性、queue、UX | 本番公開前 |
| Q10. | stdout10,000文字の仕様と現行32KiBの差は、文字数とbytesの両方を制限する方針でよいか。stderr/合算上限と同期実行の無通信条件も統一するか。 | 入出力/時間契約 | 本番公開前 |
| Q11. | 単一EC2内の専用rootless等の実行基盤を検証する方針でよいか。安全要件を満たせなければコード実行の公開延期や別実行hostを認めるか。 | sandboxリスク許容/公開ゲート | AWS構築前 |
| Q12. | 学習データを最大どの時間分失ってよいか（RPO）、授業への復旧は何時間以内か（RTO）。日次backupだけでよいか、授業中はもっと短い間隔が必要か。 | backup頻度/binlog、復旧目標 | 今すぐ |
| Q13.（月単位の目標決定済み） | 保持終了目標は2027年3月。運用DB/研究抽出/監査・認証log/backup別の具体日・適用範囲、保存先、閲覧者、削除担当と規程適合は引き続き確認する。 | データ保護/保持/廃棄。運用終了日や全backup保存期限とは別 | 本番公開前 |
| Q14.（了承状況未確認） | 生徒のコード・学習記録を収集して卒業研究に使うことについて、まず学校の了承を得ているか。大学で必要な手続き、対象者への正式説明・保護者/紙同意の要否は別途確認する。 | AWS/Gemini契約ではなく研究利用の了承・同意 | 今すぐ |
| Q15.（契約準備開始は許可済み） | Gemini契約準備は今から開始してよい。Google account/project、tier/料金、利用可能model、quota、保持/学習利用条件と実API試験再開の許可は引き続き確認する。 | 開始許可と契約完了・外部データ送信許可を区別 | 今すぐ（条件確認）、本番公開前（受入） |
| Q16. | Gemini障害/予算上限到達時にAI評価だけを止め、保存・実行・提出を続けてよいか。評価完了を待つ授業/アンケートはどう案内するか。 | degraded運用と再開 | 本番公開前 |
| Q17.（方針決定済み） | 必要最小項目の仮名化、出力時の最新同意確認、撤回後の今後の研究利用からの除外を採用。既出力の扱いは正式規程に従う。担当・列・提供先・規程は引き続き確認する。 | 同意再検証/仮名化/配布管理、出力実装は未完了 | 本番公開前 |
| Q18. | 管理者の学習内容閲覧は必要か。必要なら用途・許可範囲・監査条件は何か。管理画面の接続元制限を実施できるか。 | 管理権限、学校分離、追加防御 | 本番公開前 |
| Q19. | デプロイ実行者・承認者・代替担当者、障害通知先と対応時間帯、授業を避けた停止窓はどうするか。 | 更新/障害/restore体制 | 初回デプロイ前 |
| Q20. | 最初はローカル/固定Linux環境でimageを作り手動転送する方法でよいか。Registry/CIを最初から使う希望・運用経験はあるか。 | 成果物/配備方式、arch | 初回デプロイ前 |
| Q21. | 本番DBは完全な空DBから始めるか、既存データを移すか。移すならどのDB/版/データが対象か。開発デモを本番へ持ち込まない方針でよいか。 | V19前提、migration/データ移行 | 初回デプロイ前 |
| Q22. | 学校Bの演習file数、平均code/file size、授業日数、再提出/再評価/preview回数の見込みは何か。 | 容量/API費の不足前提 | 本番公開前 |

### 残り14項目の一括回答票（2026-10-06）

Q9〜Q22は以下でまとめて回答できる。技術的な方針は「推奨案」、異なる希望はその内容、不明な事実は「未確認」、判断を保留するものは「未定」と記入する。**推奨案は未承認の提案であり、回答前に決定台帳を決定済みへ変更しない。** 研究承認・API契約条件・保持規程は、推奨案への同意だけで確認済みとは扱わない。

| 番号 | 質問・決めること | デプロイに必要な理由 | 主な選択肢・この研究システムの推奨案 | 回答欄 |
|---|---|---|---|---|
| Q9 | Python実行枠が満杯のとき、待機と再試行をどう扱うか。 | 一斉100人の要求を現行4枠だけでは直ちに処理できず、公平性と授業中の待ち方が必要。 | **推奨:** 上限付き待ち行列、1人1実行、待機表示・取消・待機期限。満杯/期限超過は明示的に拒否し再試行を案内。別案は待ち行列なしでbusy表示。枠数・待機秒数は負荷試験後に決定し、2秒表示目標との整合も確認する。 | 未回答 |
| Q10 | 出力上限と、対話/同期実行の時間条件を統一する方針でよいか。 | stdout10,000文字の仕様と各32KiBの実装が不一致。大量出力と長時間実行でhost/DBを圧迫しない条件が必要。 | **推奨:** stdoutは10,000文字と32KiBの両方で制限、stderrも同じ制限、両stream合計64KiB以内。超過は実行終了・切詰め表示。同期/対話とも無通信30秒・全体60秒を検証。CPU4秒の適用単位と子process対策も別途実証。別案は仕様を改訂してbytes上限に統一。 | 未回答 |
| Q11 | 実行基盤の隔離を検証し、安全条件を満たさなければコード実行の公開を延期してよいか。 | rootのrunnerがhost Docker socketを使う現行境界は、生徒containerのnon-root設定だけでは安全と判断できない。 | **推奨:** 単一EC2内の専用rootless daemon等を候補に検証し、資源/権限/孤児回収の条件未達なら公開延期。単一EC2で満たせなければ、費用確認後に別実行host等も検討する。現行socket構成を未検証で公開する案は推奨しない。 | 未回答 |
| Q12 | 許容するデータ損失時間（RPO）と復旧時間（RTO）の目標はどの程度か。 | 60分授業・120分の試験想定で、日次backupだけでは授業中の記録を失う可能性がある。 | **検討用の推奨案:** RPO15分以内・RTO2時間以内を目標に、日次整合backup + 授業中の差分/binlog等のEC2外保存 + migration前backupを設計する。別案はより短いRPO、または損失を明示承認してRPO24時間。値は希望する運用目標であり、restore実測前に達成済みとしない。 | 未回答 |
| Q13 | 運用DB・研究抽出・認証/監査log・backupの保持期間と閲覧者をどう定めるか。 | 費用資料の5ヶ月は正式な研究保持期限ではない。個人契約でもデータ保護・廃棄条件が必要。 | **推奨:** 学校/大学・正式同意の規程を確認し、種類別に期限と閲覧者を設定する。運用上の閲覧は既存の本人/学校/role境界、研究抽出は承認された研究担当者のみ。本人が削除作業を担当する案を検討し、backupも対象とする。月単位の目標だけで対象別の正式期限を確定しない。 | 2026-10-07回答: 保持終了目標は2027年3月。具体日・対象別の範囲/期限・規程適合・閲覧者/削除担当は未確定 |
| Q14 | 生徒のコード・学習記録を収集して卒業研究に使うことについて、学校の了承を得ているか。 | 仮同意文面のまま実生徒利用/研究を開始できると判断しないため。AWS/Gemini契約の許可とは別の確認。 | **推奨:** 学校の了承と大学で必要な手続きを確認し、正式文面で通常学習の収集、研究利用、外部AI送信を説明し分ける。保護者/紙同意の要否と電子記録との正本は組織条件に従って確認する。了承・承認を推奨案への同意で代替しない。 | 2026-10-07: 質問の意味を補足。了承状況は未確認 |
| Q15 | Geminiの契約/利用条件の確認状況と、実API受入を再開する方針はどうするか。 | 教師設定と疎通モデルの不整合、quota・費用・外部データ保持条件が未確認。 | **推奨:** Google account/projectの管理者、billing/tier、利用可能model、quota、保持/学習利用条件を確認し、費用・送信許可後に合成データだけで実API受入を再開する。未確認の間は実生徒データを送らない。回答は確認済み事項と、合成試験の再開可否/希望時期。API Key・password・請求情報の実値は記入しない。 | 2026-10-07回答: 契約準備は今から開始可。契約条件・完了、実API試験再開・実生徒データ送信許可は未確認 |
| Q16 | Gemini障害・予算上限到達時、AIだけを停止して学習機能を継続してよいか。 | 授業を継続する範囲と評価/アンケートの待ち方を明示する必要がある。 | **推奨:** 保存・閲覧・Python実行・提出は継続、AIだけ利用不可を表示する。停止中は無制限の自動retryをせず、復旧後に履歴を保持して評価再開。評価依存のアンケートは待機案内。別案はAI必須の授業機能だけ停止。 | 未回答 |
| Q17 | 研究出力の担当者・提供先・必要項目と、同意撤回後の出力済みデータへの対応は何か。 | 運用DB全量を研究データとして提供せず、最新同意と仮名化を守るため。 | **推奨:** 本人が承認された必要最小項目を抽出、出力時に最新同意を確認し、login ID/IP/資格情報/対応表は除外。撤回後は今後の研究利用・集計・出力から除外し、既出力の削除/再抽出等は正式規程に従う。提供先・研究列・撤回時規程は事実確認が必要。 | 2026-10-07回答: 必要最小項目の仮名化・最新同意確認・撤回後の今後の研究除外を承認。既出力の扱いは正式規程に従う。担当・提供先・列・規程は未確認 |
| Q18 | 管理者による学習内容の閲覧を必要とするか。管理画面の接続制限はどうするか。 | 管理者roleを全学校の学習データ閲覧許可と混同せず、管理入口も追加保護するため。 | **推奨:** 通常の管理者業務では学習内容を閲覧しない。必要な障害調査だけ別途の監査付き権限・手順で許可する。管理入口は限定接続（固定IPがなければSSM tunnel等の方式を検討）。別案は明示承認した運用閲覧権限を追加。 | 未回答 |
| Q19 | デプロイ・障害対応の担当者、対応時間帯と停止窓をどうするか。 | 更新/復旧を誰が判断・実行するかと、授業停止の連絡を事前に明確化するため。 | **推奨:** 本人が実行・承認・一次対応、授業時間外に計画停止、授業前点検と利用時間帯の通知対応。代替担当は確保できる範囲で決め、不在なら不在と記録する。回答は担当者、対応できる時間帯、授業を避けられる曜日/時間、代替担当の有無。 | 未回答 |
| Q20 | 初期の成果物作成と配備は手動image転送を基本としてよいか。 | EC2上のbuild負荷を避け、同じ成果物で更新/rollbackを再現するため。 | **推奨:** 固定Linux/Java21/Gradle環境でtest・WAR・imageを作成し、tag/hashを記録してEC2へ転送、手動Compose配備。CIはtest/buildから導入、Registry/自動配備は必要になれば追加。別案は最初からprivate Registry + CI、またはEC2 build。 | 未回答 |
| Q21 | 本番DBは空DBから開始し、開発デモを持ち込まない方針でよいか。 | 既存tasksがあるとV19が停止し、デモresetを本番へ流用できないため。 | **推奨:** 空DBへV1〜V20、その後正式同意・rubric・学校/account等の承認された初期設定のみを登録する。別案は既存データ移行。ただし対象DB/版/項目とbackfill・restore copyでの予行が必要。 | 未回答 |
| Q22 | 容量・API費を見積もるための追加利用量は、どこまで分かるか。 | 学校B演習数、source size、再提出/再評価/preview回数が不足し、EC2/EBS/API費を確定できないため。 | **推奨:** 分かる実予定を記入し、不明は未定のまま合成負荷で複数条件を比較する。回答欄に学校Bのfiles/人、平均source KB、授業回数、1人の実行回数/授業、再提出/再評価/previewの見込みを分かる範囲で記入。推測を確定利用量にしない。 | 未回答 |

短い回答の記入形式:

```text
Q9: 推奨案
Q10: 推奨案 / 変更点
Q11: 推奨案
Q12: RPO __分、RTO __時間 / 未定
Q13: 規程確認状況、種類別の保持期間・閲覧者 / 未定
Q14: 承認状況、保護者・紙同意の要否 / 未確認
Q15: 契約条件の確認状況、合成実API試験の再開可否・時期
Q16: 推奨案 / 変更点
Q17: 出力担当・提供先・項目・撤回時の規程 / 未定
Q18: 推奨案 / 変更点
Q19: 担当・対応時間帯・停止窓・代替担当の有無
Q20: 推奨案 / 別案
Q21: 推奨案 / 移行したいデータ
Q22: 分かる利用量 / 未定
```

既に保留した予算総額/月額・通知しきい値は、最新試算後に決める方針のまま維持する。運用終了予定、個人契約クラウドの研究利用許可、費用負担/管理担当、本番専用アカウント分離、ドメイン取得先・空き状況/料金等も未確定であり、判明した情報だけ同じ回答に追記できる。回答のない項目は承認・確認済みとみなさない。

## B. 今すぐ開始できる作業

| 作業 | 前提/成果 | 機能完成を待たない理由 |
|---|---|---|
| Q1〜7、12、14〜15の条件確認と決定台帳への記録 | 所有者/予算/region/domain/secret/RPO/倫理/Google条件 | 承認・契約・DNSは時間がかかり、コード完成と独立 |
| 単一EC2の仮サイズ・EBS/SG/SSM/IAM/費用案の設計 | 最新料金を確認、値は仮選定、予算通知を先行 | 最終sizeだけ負荷試験後に確定できる |
| 非公開の合成データ検証環境準備 | budget/アクセス/secret条件承認後 | 配備経路の練習に完成機能は不要 |
| dev/prod Compose分離・WAR image設計 | 固定版、network、volume、非公開port、logging/health契約 | 現行WAR・DB・runnerの役割は明らか |
| 専用実行daemon境界の試作/検証計画 | 合成code、非本番環境、CPU/RAM/cleanup/host保護 | 現行Docker socket設計の公開可否を先に判断すべき |
| HTTPS/DNS/ACME自動更新とproxy設定の練習 | FQDN/権限、限定公開 | 証明書やcookieの確認は機能追加と並行できる |
| secret store/role/注入・除外規則の設計 | 実値をrepoに置かない | key/DB秘密の保護は初回配備から必要 |
| V1〜V20空DB構築・旧版restore copyでmigration予行 | 本番でないDB、専用migration権限 | V19/DDL failureは早期発見できる |
| backup/restore/失敗通知・retentionの試作 | 合成DB→EC2外S3→隔離restore | データ量の最終確定を待たず手順検証可能 |
| image/tag/hash・手動更新/rollback予行と最小CI検討 | 非本番、選定した方式 | 繰返し配備の基盤を先に整えられる |
| 負荷fixture・受入指標・安全な異常系testの設計 | 300接続/Python集中/AI模擬queue等 | 最終性能判定と区別して準備できる |
| 正式同意/保持/撤回/研究出力項目の合意開始 | 学校/大学/研究責任者 | 法務/倫理判断は機能実装より先に着手可能 |

これは準備作業一覧であり、今回それらのAWS資源やコードを実装したという意味ではない。

## C. 開発完了後に行う作業

ここでいう「開発完了」は全開発を一括で待つ意味ではなく、**検証対象の機能・DB/API契約が完成した時点**を指す。

| 作業 | 今は確定できないもの/前提 | 実施時期 |
|---|---|---|
| 教師/管理者を含む全機能のRBAC・学校/本人境界、CSRF/XSS/CSVの横断受入 | 後続画面/DAO/出力が未完成 | 対象実装完了後、本番公開前 |
| 正式課題/プロンプト/ルーブリック/同意/アンケートによるE2E | 設定可能な教師機能、正式資料、実API再開承認 | 工程19〜20、本番公開前 |
| 最終EC2/EBS/JVM/MySQL/Python枠の決定 | 最終schema/query/payload、合成負荷・runtime実測 | 本番公開前 |
| 完成機能版でのupgrade/DB失敗/rollback/再起動queue回収・maintenance受入 | 対象releaseとmigration | 初回実利用前および各更新前 |
| 実利用者登録・学校/クラス権限・初期資格配布 | 生徒account管理等の完成、正式手順・権限確認 | 公開承認後、研究開始前 |
| 研究export・撤回・保持満了/削除の全対象試験 | 教師研究出力機能と正式保持規程 | 対象完成後、本番データ取扱い前 |
| 最終公開Smoke/restore test・運用引継ぎ | 最終image/設定/通知先/backup、全公開ゲート | 本番公開前 |

実生徒データを試作環境へ入れること、仮同意で研究開始すること、予算・許可なしでGemini負荷試験を行うこと、V19通過のために本番tasksを削除することは、開発途中にも完了後にも行わない。

## D. 推奨作業順

| Step | 作業 | 依存関係・完了条件 |
|---|---|---|
| Step 1 | 所有者/予算/日程/研究条件/RPOを確認 | Q1〜4、12〜15。回答が必要なものと技術検証待ちを分け、決定台帳へ担当・期限を登録 |
| Step 2 | domain/DNS・管理接続・secret store・sandbox境界を設計 | Step1の組織条件。Q5〜7、11。単一EC2で満たせる安全条件を先に確認 |
| Step 3 | 本番image/Compose/migration資材・監視/backupの設計を具体化 | Step2のnetwork/権限、Q19〜21。開発Composeと異なる起動/秘密/port契約を明示 |
| Step 4 | AWSに限定検証環境を構築 | Step1 budget/region + Step2接続/権限 + Step3資材。SG/SSM/MFA/Budgets、仮EC2/EBS/非公開S3 |
| Step 5 | 合成DBでFlyway・初期設定・backup/restoreを予行 | Step4 + version固定資材。V1〜V20、V19前提、migration user、復旧/通知を確認 |
| Step 6 | WAR/image配備、Nginx/HTTPS/cookie・health・secret注入を確認 | Step3〜5 + domain/DNS。限定アクセスの初回Smoke、更新/旧image復旧予行 |
| Step 7 | sandbox異常系と同時実行、OS再起動/孤児/host影響を検証 | Step2実行境界 + Step6。安全ゲート未達なら生徒向けコード実行を公開しない |
| Step 8 | 後続機能開発と正式設定を進め、Gemini条件不整合を解消 | Step1研究/API承認。AWS準備とは並行。実API再開許可後、合成評価→DB→表示を受入 |
| Step 9 | 最終負荷/容量・認可/学校分離・E2E・復旧を検証 | Step7〜8 + 対象機能完成。Q8〜10、17〜18、22を反映しEC2/各上限を実測確定 |
| Step 10 | 公開承認・実利用者登録・研究開始 | Step9 + 正式同意/保持/外部AI条件 + 運用連絡/rollback/restoreの確認。公開ゲートを記録 |
| Step 11 | 運用点検・繰返し更新・研究終了処理 | 授業前点検、監視/費用/backup/restore、schema互換更新。保持満了時は研究抽出/backupを含め廃棄確認 |

Step1〜3と機能開発は今から並行可能。Step4〜7は必要な組織承認・安全条件を満たした合成データ検証であり、全機能完成待ちではない。Step9〜10の公開承認を、検証環境へ配置できたことと混同しない。

## E. 標準ルーブリック自動登録の調査・設計案（2026-10-09）

**設計のみ。本番登録・seed実行・コード/SQL変更はしていない。**
対象アプリは `0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`、
運用スクリプトは `de2b0d37fb7ccb6ecae93ce81b2e01d230034e01`。
この領域の調査コードは対象releaseと同じ内容であることをGit差分で確認。
本番のrubrics/evaluationsは読み取り集計でともに0だったが、今後の登録時にも
既存評価・教員作成データが存在する前提で保護する。

### 現行データ・定義・初期化

| 対象 | 確認した実装・一次資料 |
|---|---|
| 定義の正本 | [思考力・判断力・表現力](../rubric/思考力・判断力・表現力_ルーブリック_0805.md)、[主体的に学習に取り組む態度](../rubric/主体的に学習に取り組む態度_ルーブリック_0805.md)。評価文言は転記・創作しない |
| 識別 | [StandardRubric](../../src/main/java/entity/StandardRubric.java): title=`生徒共通標準ルーブリック`、version=`0805-2026-v1`固定、2領域、4+2観点、各5段階。資料見出しは2026.7.30、filenameは0805、DB版は上記。日付から新versionを推測しない |
| 読み込み | [StandardRubricSource](../../src/main/java/control/student/StandardRubricSource.java)がMarkdownをパース。NFCファイル名照合、表/件数/尺度検証あり |
| DB | [V1](../../src/main/resources/db/migration/V1__create_initial_schema.sql): rubrics→rubric_dimensions→rubric_criteria→criterion_levels。title+version、領域code、観点code、段階値の一意制約。標準登録は1/2/6/30行、system author NULL |
| Flyway | [V15](../../src/main/resources/db/migration/V15__allow_system_rubric_author.sql)がauthor NULLを許可。[V17](../../src/main/resources/db/migration/V17__support_teacher_prompt_workflow.sql)は登録済み標準版を一部NULL下書き課題へ関連付けるのみ。定義をINSERTするmigrationではない |
| 登録 | [registerStandardRubric](../../build.gradle)→[Bootstrap](../../src/main/java/control/student/StandardRubricBootstrap.java)→[DAO](../../src/main/java/dao/StandardRubricDao.java)。単一transactionで登録。一致時はno-op、不一致/状態違いは例外・rollback、上書きしない |
| 現在のdeploy | [deploy-release.sh](../../scripts/production/deploy-release.sh)にrubric stageなし。運用手順でmigration後の手動tools taskを案内。Flyway完了とseed完了は独立 |
| app起動 | [LifecycleListener](../../src/main/java/lib/mysql/DataSourceLifecycleListener.java)はDB/worker初期化のみ、標準登録しない。[tools Dockerfile](../../containers/production/Dockerfile)には正本Markdownを同梱 |
| 開発seed | [DemoDataBootstrap](../../src/main/java/control/dev/DemoDataBootstrap.java)も標準を登録するが、ローカルDB限定・合成利用者等作成用。本番では利用禁止 |
| 教師側利用 | [TeacherTaskDao](../../src/main/java/dao/TeacherTaskDao.java)は登録済み標準を新規課題に要求。[TeacherPromptDao](../../src/main/java/dao/TeacherPromptDao.java)は権限内の下書きを標準へ関連付ける。手動seed自体はtasksを更新しない |
| UI | [StudentRubricControl](../../src/main/java/control/student/StudentRubricControl.java)はDBの固定標準を表示。現仕様は共通標準で、教師の任意ルーブリック編集UIは未実装/対象外。DB上の任意rubric行はそれでも保護対象 |

既存registerはtitle+versionをFOR UPDATEで検査するが、DB一意制約だけで同時起動時の
両方の成功まで保証するものではない。存在しない行の競合/deadlock等は明示失敗とし、
同時seedはdeployment lockで排除する。DB再起動・repair等で解決しない。
既存行のauthor、criterion_code/description/weight、尺度min/max/label等は現在の
`find()`モデルに全て含まれないため、「一致」はDB全属性の完全一致保証ではない。
新設計ではowner/provenanceと意味に影響する全属性を比較し、既存の教員作成行を
標準扱いで取り込まない。これは調査で得た改善要件であり、今回の修正ではない。

### 過去評価との関連と版不変の必要性

- tasksとevaluationsはrubric_idを保持。evaluation_scoresはcriterion_id/
  dimension_id/criterion_level_idを参照し、結果にはmetric名・説明等も保存される。
  行をin-place更新すると、過去結果の表示・根拠の意味が変わり得る。
- [EvaluationQueueDao](../../src/main/java/dao/EvaluationQueueDao.java)は評価要求時に
  rubric IDとversion、prompt/modelを固定する。失敗再試行も元評価のrubricを使う。
- [EvaluationWorkerDao](../../src/main/java/dao/EvaluationWorkerDao.java)はworker実行時に
  DB定義を読み、ルーブリックを含むtask_snapshotを保存する。queue投入時と実行時の
  間に定義変更があるとversion文字列だけでは保護できない。保存snapshotは入力追跡の
  根拠だが、全表示・再試行がsnapshotだけを読む保証ではない。
- 既存コードは標準version定数を1つに限定。新しい版を追加するにはparser/entity、
  DAOのfind/active選択、課題作成/改訂、prompt生成、student表示、評価queue/
  retry/preview/teacher表示を横断して版指定・pinを確認する必要がある。
  定数だけ変更して自動移行する設計は不可。
- **旧版は削除・内容更新しない。** 旧tasks/evaluationsの参照先を一括付け替えない。
  最新既定版と利用可能な旧版を区別する。現queueはactiveを要求するので、旧課題が
  使う版を不用意にarchivedへ変更すると評価開始が止まる。既定の切替とstatus変更は
  同義にしない。再評価は明示的な新評価履歴として保存し旧評価を保持する。
- モデル/外部AIの非決定性があるため再実行で同一点数を保証しない。
  「再現性」は使用定義・prompt/model・入力・応答・根拠を復元でき、
  過去結果の意味が変わらないこととする。

### 方式比較

| 方式 | 長所 | 懸念 | 判断 |
|---|---|---|---|
| Flyway SQL seed | schemaと同じchecksum/履歴、1回適用 | MarkdownからSQLを二重管理しやすい。rollback困難なDDLとデータ投入の境界、author/既存衝突を慎重に扱う必要 | schema追加・制約はFlyway、新しい定義の値は主方式にしない |
| 版管理されたdeploy seed | 正本Markdown/parser/既存DAOを再利用、transaction/no-op、失敗をdeploy stageで記録 | seed版/内容hash/競合/旧課題との参照の設計、journal拡張が必要 | **推奨** |
| app起動時登録 | deploy後の手動忘れを減らす | 複数app同時起動、通常runtimeによる暗黙DB書込、workerとの競合、起動障害とseed障害が混ざる | 採用しない。readiness確認のみ候補 |

### 提案する要件と処理順

追加実装前の提案要件。業務仕様の承認・更新後に実装する。

| ID | 要件 / 受入条件 |
|---|---|
| REQ-016 | 初回migration後・app/worker起動前にrelease内の承認済み定義を登録。1/2/6/30行と内容を検証する |
| REQ-017 | 同一seed版・同一canonical内容hashの再deployはno-opで、rubric IDと関連行ID不変。競合実行は排他または明示停止 |
| REQ-018 | 同一identityに別内容/owner/不正状態/欠損行があれば原因を記録して停止。REPLACE/削除/上書きupsert/「既存なら成功」禁止 |
| REQ-019 | 教員作成・編集rubric、既存tasks/evaluations/scores/入力snapshotは更新・削除しない。参照ID/内容hash不変を実DBで比較 |
| REQ-020 | 標準変更は承認済み新versionの追加。旧版・利用中定義は不変、既定切替は新規課題向けの明示操作。過去の結果が新定義で再解釈されない |
| REQ-021 | transaction失敗は部分登録を残さず、stage/release/seed版/エラー分類を秘密なしで記録。失敗時公開しない、無条件再試行しない |
| REQ-022 | journalのseed完了とDBの内容検証を両方確認。DBcommit後/記録前に中断しても再開で同一内容を確認しno-op。古いformat=2 journalを手動編集せず互換設計する |
| REQ-023 | 全version選択経路とqueueのpin、旧評価表示・明示再評価をテスト。1回目/2回目/旧→新更新で旧評価のID・点数・尺度文言・snapshot不変 |

候補手順: 非破壊DB preflight → 書込/DDL調整と**検証済みbackup** →
schema migration/validate → `seed_started` → release toolsによる
版付きseed + DB再検証 → `seed_complete` → app/worker → readiness/smoke → publish。
新規initialとupdateの双方に適用する。published直後の今回のDBへ登録するのは、
schemaが既に存在するため**別途承認・backup付きの保守作業**とする。
今回の空DB時backup例外を今後へ流用しない。

実装候補はsystem-only stable key、seed version、canonical内容hash、source commit、
DB rubric ID、登録時刻を保存するseed registry/manifestとunique制約。
schemaは新Flywayで追加し、既存SQL/immutable releaseは変更しない。
author NULLだけをowner識別に使わず、既存データの誤認・教師行との衝突を拒否する。
同じ定義を別versionとして重複投入する場合の扱いはレビューで明示する。
承認済み新基準がまだないため、v2の評価基準や新しい点数体系は作らない。

### 検証計画・実装開始条件

既存の[SourceTest](../../src/test/java/control/student/StandardRubricSourceTest.java)、
[DatabaseTest](../../src/test/java/control/student/StandardRubricDatabaseTest.java)を
隔離MySQLで拡張する。これらはDBテスト用の破壊的cleanupがあるため、本番実行禁止。
seed無し初回、同一定義の再実行、故意の不一致・欠損・別owner、同時実行、
commit直前/直後の中断、2版共存、教員custom行、旧/新tasks・評価・pending queueを用意し、
失敗時0部分行、成功時重複0、旧データ/関連ID不変を照合する。
定義変更試験は合成fixtureのみで、承認済み標準の正本は変更しない。

必要な合意: 標準版の新規課題への既定切替方法、既存課題は旧版固定、
再評価の明示承認、seed失敗時の公開停止、バックアップ要件。
この文書の提案は設計レビュー用であり、本番登録や実装の追加承認を待つ。
