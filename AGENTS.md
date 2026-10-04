# AGENTS.md

このリポジトリで AI コーディングエージェントが最短で生産的に動くための実行指針。

## Project Snapshot

- Java Servlet/JSP + Gradle + Tomcat + MySQL 構成。
- 実装本体は `src/main`、画面遷移の試作は `screen-flow-diagram` を使用。
- 仕様検討ドキュメントは `docs` 配下に集約。

## First Commands

- 初回セットアップ: `.env.sample` を `.env` にコピーし、`PROJECT_NAME` をワークスペース名に合わせる。
- 開発起動: `.env.sample` を `.env` にコピーして値を設定後、`docker compose up --build -d`
- アプリ確認: `http://localhost:${APP_PORT}`（`.env` の `APP_PORT`）
- 開発サーバーは Gradle の Gretty `appRun` タスクで起動する。
- ビルド: `docker compose exec -T app gradle build --warning-mode all`
- DB マイグレーション: `docker compose exec -T app gradle flywayMigrate`

## Key Directories

- 実装 Java: `src/main/java`
- 実装 JSP/CSS: `src/main/webapp`
- 画面遷移図試作: `screen-flow-diagram/webapp`
- 仕様ドキュメント: `docs`

## Repo-Specific Conventions

- 画面ファイル名は `index` を使わず、用途を明示する命名を優先する（例: `history.html`）。
- 仕様確認時は、既存仕様を転記せず参照リンクを使う。
- 画面遷移プロトタイプ作業時はテンプレート資産を優先利用する。
  - 参照: `screen-flow-diagram/webapp/WEB-INF/template`
  - 参照: `screen-flow-diagram/webapp/css/template/template.css`
- 新規の確認・通知 UI は `shared feedback` を使い、画面ごとに alert / confirm を再実装しない。
  - 参照: `docs/feedback-guideline.md`

## Feature Addition Workflow

- 機能追加の指示を受けたら、まず `docs/function-specification.md` を更新し、差分の方針確認が取れるまで `screen-flow-diagram` や実装本体は編集しない。
- `docs/function-specification.md` を編集する場合は、冒頭の改版履歴も同時に更新する。
- 方針確認後は、`docs/screen-flow-diagram.md` → `screen-flow-diagram/webapp` の順で更新し、画面名・導線・用語を仕様書と一致させる。
- 画面遷移プロトタイプは `screen-flow-diagram/webapp`、本実装は `src/main` と役割が分かれている。プロトタイプ変更だけで済む依頼か、実装変更まで求められている依頼かを明示して進める。
- 仕様変更が入力制約、想定入出力、評価条件、同意取得、匿名化要件に触れる場合は、関連節を横断して整合性を確認する。

## Implementation Workflow

- 本実装に着手するときは、`docs/system-configuration/implementation-roadmap.md` の該当工程と `docs/system-configuration/implementation-and-local-development.md` を確認する。
- 対象機能の実装前に、機能仕様書、画面遷移図、関連する `docs/state-rules`、DB 設計書、該当するクラス図・AI 連携設計・画面プロトタイプを確認する。状態ルールは必ず読む。
- `docs/system-configuration/implementation-contract.md` の未合意事項を確認する。対象機能に影響する対応が未合意なら、コードや DB の設計を決め打ちせず、差分と選択肢を提示して確認を得る。
- 作業を始める前に、`docs/system-configuration/feature-plan-template.md` を使って作業単位・対象ファイル・状態 / DB 対応・検証条件を定める。小さな修正では計画を会話内で示せばよく、不要な計画ファイルを追加しない。
- `screen-flow-diagram/webapp` は画面遷移プロトタイプであり、本実装ではない。ただし、画面のレイアウト・構成・項目・文言・導線は本実装の基準として扱う。対象画面の HTML、CSS、JavaScript、共通テンプレートを確認し、原則として既存デザインをそのまま `src/main` に反映する。変更は Servlet/JSP 化、共通部品との統合、アクセシビリティ、レスポンシブ対応など本実装に必要な範囲にとどめ、独自の画面へ作り替えたり、設計済みの項目・導線を省略したりしない。
- プロトタイプにある固定データや JavaScript のみで完結する認証・保存・更新・評価などは見た目だけを移植しない。表示内容と操作は Servlet → Control → DAO → DB に接続し、実データの読込・保存とサーバー側の認可・状態検証を実装する。DB連携が未実装の部分を成功したように見せる擬似動作や固定データで補わず、未実装・利用不可の状態を明示する。
- 業務データは MySQL を正本とし、Servlet → Control → DAO → DB を通して読み書きする。画面の操作可否だけでなく、Control 側でもロール・対象データの所有範囲・状態遷移を検証する。
- 実装後は対象の最小ビルド / テストと、DB 保存後の再読込・権限境界・状態遷移を確認し、実行結果と未確認事項を報告する。

### 外部API連携の実装・検証とエラー記録

- 外部API連携を含む機能は、接続部分を未実装やモックのまま残さず、実際の外部API呼び出しまで実装する。モックによる自動テストに加えて、設定済みの認証情報を使った実APIの疎通・応答検証を行い、DB保存を伴う機能では保存・再読込まで確認する。
- 実APIの検証には完全な合成・ダミーデータのみを使い、実際の生徒データ・研究データは送信しない。APIキーや認証ヘッダー等の秘密情報はログ・標準出力・画面・レポートへ出力しない。
- 実装・ビルド・テスト・実API呼び出しの過程でエラーが発生した場合は、[エラーレポート](docs/system-configuration/error-report.md)へ記録する。ファイルが未作成なら初回のエラー発生時に作成する。発生日時とタイムゾーン、対象機能・実行手順またはコマンド、期待結果、実際のエラー、影響範囲、対応内容、再検証結果、未解決事項を記載する。APIエラーでは秘密情報を除外してモデル・エンドポイント・HTTP status・エラーコード・安全に記録できるメッセージ・request ID・Retry-After等も記録する。
- エラーを成功扱いしたり、結果を捏造して補ったりしない。失敗した検証とブロッカーを明示し、独立して進められる開発は継続する。解消済みのエラーも記録を残し、原因が未確定の場合は事実と推測を分ける。
- API方式・モデル・データ保持方針などの未合意事項は、外部API呼び出しの許可だけで変更しない。既存の仕様・実装契約に従う。

## 仕様整合チェック: 初期チェック項目

ユーザー要求「システムと機能仕様書の整合性チェック」を開始する際は、まず次の項目を確認する。

1. 用語整合

- 同一概念が文書間で同じ用語・定義になっているか。
- 参照: `docs/function-specification.md`

1. 画面遷移整合

- 機能仕様の遷移と画面遷移図の遷移が一致するか。
- 参照: `docs/screen-flow-diagram.md`
- 参照: `screen-flow-diagram/webapp/WEB-INF`

1. 機能網羅性

- 優先度「高/中/低」ごとに、仕様に記載された機能が画面・実装対象に漏れなく対応しているか。
- 参照: `docs/function-specification.md`

1. 入力制約・期待結果の一致

- 課題の入力制限、想定入出力、評価表示条件が矛盾なく記載されているか。
- 参照: `docs/function-specification.md`

1. データ記録と匿名化要件

- 同意、コードログ、評価履歴において匿名化要件と保存項目が矛盾しないか。
- 参照: `docs/function-specification.md`

1. 実装制約との整合

- 現行技術構成（Servlet/JSP、DB、30秒ログ収集想定）で実現不能な記述がないか。
- 参照: `build.gradle`
- 参照: `docker-compose.yml`

1. 優先度と導線整合

- 優先度が高い機能ほど主要導線上に配置されているか。
- 参照: `docs/system-configuration/implementation-roadmap.md`
- 参照: `docs/function-specification.md`

1. 非対応機能の明示

- ファイルI/O非対応などの制約が、画面説明・操作説明と矛盾していないか。
- 参照: `docs/function-specification.md`

## Expected Output Format For Consistency Review

整合性チェック結果は以下の列で一覧化する。

- `チェック項目`
- `判定 (OK/要修正/要確認)`
- `根拠文書`
- `差分・懸念`
- `修正案`

## Validation Notes

- 対象範囲に応じた既存テスト・ビルドを実行し、コマンド・成功 / 失敗・未確認事項を明示する。テスト基盤がない範囲は、再現可能な手動確認手順と制約を記録する。
- 画面プロトタイプの確認では、共通テンプレート適用と `docs/feedback-guideline.md` 準拠を最低限の確認項目に含める。

## Response Format

- 各回答の最後に「次にすること」を記載し、次の作業または必要な確認を具体的に示す。残作業がない場合は、その旨を記載する。

## User Decision Flow

- 上流工程の未決事項は一度にまとめて質問せず、判断が必要なものだけを推奨案と理由を添えて一問ずつ確認する。
- 回答を関連資料へ反映してから、次の未決事項を質問する。
- 既存資料から一意に決められる事項や実装上の詳細は質問せず、整合する案で進める。
- 推奨案に迷う余地が少ない低リスクな事項はそのまま反映し、仕様の意味、利用者の権利、費用、運用負担が変わる判断だけを質問する。

## Link-First Principle

詳細仕様は既存文書を一次情報として参照する。AGENTS.md には実行ルールと観点のみを保持し、仕様本文の重複記載はしない。

## Handoff: 教師向け機能の開始（2026-10-04更新）

詳細な機能仕様を本書へ複製せず、[docs/README.md](docs/README.md)を文書索引、[実装ロードマップ](docs/system-configuration/implementation-roadmap.md)を工程と進捗の正本として使う。

### 作業開始時に確認する資料

1. [実装ロードマップ](docs/system-configuration/implementation-roadmap.md): 「次の着手」と工程10を読む。完了記録を再実装の指示と扱わない。
2. [実装手順](docs/system-configuration/implementation-and-local-development.md)と[実装契約](docs/system-configuration/implementation-contract.md): 層の責務、教師の所有範囲、IC-001/005/007/009等の実装前確認事項を確認する。
3. [機能仕様書](docs/function-specification.md)と[画面遷移図](docs/screen-flow-diagram.md): 教師の課題・プロンプト・ルーブリック・配信と、それに接続する生徒側の要件を確認する。
4. 教師の[課題状態](docs/state-rules/teacher/task-state-rules.md)、[プロンプト状態](docs/state-rules/teacher/prompt-state-rules.md)、[配信状態](docs/state-rules/teacher/distribution-state-rules.md)と[実装用状態表](docs/state-rules/implementation-state-table.md)を読む。授業演習の配信を扱う場合は[演習状態](docs/state-rules/student/exercise-state-rules.md)も確認する。
5. [DBテーブル定義](docs/database-design/table-definitions.md)、[教師クラス図](docs/class-diagram/03-teacher-task-distribution.puml)、[入出力形式](docs/format/)を確認する。プロンプト・評価接続を扱う場合は[AI連携設計](docs/ai-api-integration-design.md)も読む。
6. 教師の[課題画面](screen-flow-diagram/webapp/WEB-INF/teacher/task/task.html)、[プロンプト画面](screen-flow-diagram/webapp/WEB-INF/teacher/prompt/prompt.html)、[配信画面](screen-flow-diagram/webapp/WEB-INF/teacher/distribution/distribution.html)、関連CSS/JS・共通部品と[feedbackガイド](docs/feedback-guideline.md)を確認する。
7. [機能別計画テンプレート](docs/system-configuration/feature-plan-template.md)で工程10の計画を作る。教師工程の機能別計画はまだ作成していない。完了済み計画は[文書索引](docs/README.md)から必要な根拠だけ参照する。

### 現在位置と次の順序

- 2026-10-04ユーザー指示で教師向け機能へ進む。以前の演習確認優先・教師工程前停止の指示は終了したものとして扱う。
- 工程1〜4の基盤、工程5の認証、工程6の生徒ホーム等は実装済み。工程9の生徒演習T001〜T051と教師工程前の既知バグ5件は完了済み。詳細はロードマップから参照し、本書に版別履歴や完了済みタスクを再掲しない。
- 工程7・8は主要実装済みだが残る検証がある。教師の課題・プロンプト・配信、進捗・評価・CSVなどの業務機能は未着手。認証用の教師ホームを業務機能の完成と扱わない。
- 次の順序は工程10 → 11 → 12 → 13。工程12の学校管理だけは先行実装済み。新セッションの最初の成果物は工程10の機能別計画とする。

### 新セッションの最初のステップ

1. `git status --short`で引き継いだ未コミット変更を確認する。直前のデバッグ修正・テスト・文書は作業ツリーに残っている。勝手に破棄・revert・commitせず、既存の開発環境やDBを初期化しない。
2. 上記資料と既存コードを照合し、教師の学校/クラス権限・課題改訂・適用版・配信状態を確認する。不足する業務画面や設定を仮データで補わず、最初の機能スライスと前提を計画に明記する。
3. 工程10の機能別計画を作成する。新たな仕様判断が必要ならユーザーへ確認し、仕様変更は所定の文書確認手順を先に行う。既存の合意を再確認待ちとして巻き戻さない。
4. 計画の最初のスライスからDB → DAO → Control → Servlet → JSP/JavaScript → 保存後の再読込・権限/状態境界・ブラウザー検証の順で進める。この引継ぎ整理自体は文書のみで、教師機能の実装には着手していない。

### 維持する保留・制約

- 評価・アンケート全体の実設定とユーザー手動E2Eは、教師画面完成後に再開する。専用合成データの既知バグ検証と、実運用設定の受入完了を混同しない。詳細はロードマップ工程8から参照する。
- 管理者の2タブ/モーダル統合は[管理者プロトタイプ](screen-flow-diagram/webapp/WEB-INF/admin/management.html)のみ。本実装への統合は工程12かつユーザーのローカル反映指示後。実管理者アカウント作成やIC-010の保存期間満了処理にも自動着手しない。
- 通知一覧は追加しない。教師ヘッダー、ナビゲーションの用語・権限、再評価完了通知要件との整合は、ロードマップ末尾の未完了項目として引き継ぐ。
- OS標準ダイアログ・全ブラウザー/OS互換性、本番運用条件、工程7の残る回帰確認はロードマップに保持する。今回の教師工程への移行を、それらの検証完了とは扱わない。
- 失敗・修正・再検証の履歴はエラーレポートへ残す。解消済みの不具合は再発の根拠がない限り再オープンしない。
