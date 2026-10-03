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

- 自動テスト基盤は未整備のため、変更後は実行できた確認手順と未確認事項を明示する。
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

## Handoff: 次にやるべきこと（2026-10-02 時点）

- 上流工程（IC-001〜IC-009、配信状態）は全件合意済み。合意内容は `docs/system-configuration/implementation-contract.md` の「合意状況の更新履歴」を参照。
- **IC-010（保存期間満了時の処理）はユーザー指示により保留中**。再開の指示があるまで着手しない。
- ロードマップ1（ローカル開発コンテナとビルド）は Java 21 / Gradle 8.10.2 / Gretty（Tomcat 9）/ HikariCP に整合し、ビルド・起動・DB 接続を再確認済み。
- ロードマップ2（DB とマイグレーション）は完了。`src/main/resources/db/migration/` に初期スキーマとアカウント制約の Flyway マイグレーションを作成・適用済み。IC-007 の `tasks.active_prompt_version_id` と IC-008 の `research_subject_identifiers` も反映済み。
- `src/main` には MySQL 接続基盤、User / Authentication / Student DAO、認証・生徒向け Control、ログイン・ログアウト・生徒ホーム/アカウント/同意 Servlet、ロール / 強制変更 Filter、CSRF 保護、レベル2パスワード変更がある。教師ホームは認証確認用仮ページで、後続工程の教師向け業務画面で置き換える。
- 次の作業は `docs/system-configuration/implementation-roadmap.md` のロードマップ順（0→1→2…）に従う。システム構成ガイドの整合は完了し、認証工程の完了とは区別する。
  1. **ロードマップ 0（状態・データ設計の実装契約を確定する）**: 完了。念のため着手前に本当に未決事項がないか `implementation-contract.md` を再確認する。
  2. **ロードマップ 1（ローカル開発コンテナとビルドを整える）**: 完了。Java 21 / Gradle 8.10.2、Gretty の `appRun` / Tomcat 9、HikariCP の構成で WAR ビルド、HTTP、DB 接続を確認済み。
  3. **ロードマップ 2（DB とマイグレーションを作る）**: 完了。56テーブル、105外部キー等を空のローカルDBへ適用し、再実行・制約・Flyway 検証を確認済み。開発用 seed は未作成。
  4. **ロードマップ 3（DB 接続基盤と DAO）**: 完了。`UserDao.findByLoginId` が利用者・生徒プロフィールを取得し、未登録・SQL障害・プロフィール不整合を区別することをビルドとローカル MySQL で確認済み。自動テストは未整備。
  5. **ロードマップ 4（Web 共通基盤と画面資産）**: 共通 JSP フラグメント、共通CSS / shared feedback、404 / 500 画面を実装・確認済み。ユーザー指定により個別業務画面は各機能工程で移植する。
  6. **ロードマップ 5（ユーザー初期データ、認証・ログイン）**: Control / DAO / Filter / Servlet / ログイン・強制変更画面、30分セッション、CSRF、初期管理者作成タスクを実装。認証確認用仮ホームを追加し、ロードマップ6以降で置き換える。パスワード単体テストと一時DB fixture を使ったログイン、session ID更新、HttpOnly、ログアウト、ロール境界、強制変更、ロックアウトを確認済み。追加で停止 / 削除アカウント・未知IDの拒否、期限切れロックのリセット、教師の所属校・機能権限に基づくロック解除と監査、実セッション30分期限切れを検証し、確認用fixtureと認証・監査履歴が残っていないことを確認した。実管理者アカウントは運用者が実値を対話入力する必要があるため未作成。
  7. **ロードマップ 6（生徒の同意・課題一覧・アカウント）**: 生徒ログイン / ホーム / アカウント / パスワード変更 / 研究同意画面を、`screen-flow-diagram` の既存デザインに沿って本実装へ反映。業務表示はDB連携し、公開中かつ本人の在籍クラス向け課題、進捗・評価・アンケート状態、所属、認証情報履歴、同意文書・回答を取得する。未実装のeditor / exercise / evaluation / survey操作はリンクせず無効表示。公開範囲・状態・同意保存・画面表示を一時DB fixtureで確認し、fixtureを削除済み。正式な承認同意文書は未登録のため、登録されるまでは画面から回答できない。撤回UIは未定義で今回対象外。今回の画面整合は対象となった生徒画面に限り、画面遷移図の全画面完了を意味しない。詳細と検証結果は `docs/system-configuration/feature-plans/student-home-consent-account.md` を参照。
  8. 次はロードマップ7（Python実行と生徒エディター）。初期管理者は実値を追跡ファイルやコマンド引数へ置かず、DBを使う環境で対話式Gradleタスク `docker compose exec app gradle createInitialAdmin` により一度だけ作成する。
  9. IC-010 の保存期間満了時の詳細には、再開指示があるまで着手しない。
