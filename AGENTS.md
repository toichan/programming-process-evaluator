# AGENTS.md

このリポジトリで AI コーディングエージェントが最短で生産的に動くための実行指針。

## Project Snapshot

- Java Servlet/JSP + Gradle + Tomcat + MySQL 構成。
- 実装本体は `src/main`、画面遷移の試作は `screen-flow-diagram` を使用。
- 仕様検討ドキュメントは `docs` 配下に集約。

## First Commands

- 初回セットアップ: `.env.sample` を `.env` にコピーし、`PROJECT_NAME` をワークスペース名に合わせる。
- 開発起動: `docker-compose build && docker-compose up`
- アプリ確認: `http://localhost:${APP_PORT}`（`.env` の `APP_PORT`）
- 継続ビルド: `docker-compose` の `gradle` サービスで `gradle -t build --warning-mode all`

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
- 参照: `docs/plan.md`
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

- 自動テスト基盤は未整備のため、変更後は実行できた確認手順と未確認事項を明示する。
- 画面プロトタイプの確認では、共通テンプレート適用と `docs/feedback-guideline.md` 準拠を最低限の確認項目に含める。

## Link-First Principle

詳細仕様は既存文書を一次情報として参照する。AGENTS.md には実行ルールと観点のみを保持し、仕様本文の重複記載はしない。
