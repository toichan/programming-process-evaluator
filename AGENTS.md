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

## Handoff: 開発再開時に参照する資料と順序（2026-10-03 時点）

詳細な機能仕様を本書へ複製せず、[docs/README.md](docs/README.md)を文書索引、[実装ロードマップ](docs/system-configuration/implementation-roadmap.md)を工程と進捗の正本として使う。

### 作業開始時に確認する資料

1. [実装ロードマップ](docs/system-configuration/implementation-roadmap.md): 現在の工程、依存関係、完了条件を確認する。
2. [実装とローカル開発の詳細手順](docs/system-configuration/implementation-and-local-development.md): 層の責務、実装ルール、ローカル開発ループを確認する。
3. 対象工程の[機能仕様書](docs/function-specification.md)、[画面遷移図](docs/screen-flow-diagram.md)、対象画面の`screen-flow-diagram/webapp`資産を確認する。
4. 対象機能の[状態ルール](docs/state-rules/README.md)、[DB設計・テーブル定義](docs/database-design/README.md)、関連[クラス図](docs/class-diagram/README.md)を確認する。
5. [実装契約](docs/system-configuration/implementation-contract.md)で関連ICの未合意事項を確認し、未合意事項が実装方式に影響する場合は決め打ちせず、差分と選択肢を提示する。
6. 既存の[機能別実装計画](docs/system-configuration/feature-plans/)を確認し、未完了タスク・検証結果を引き継ぐ。新規作業では[機能別実装計画テンプレート](docs/system-configuration/feature-plan-template.md)を使う。

### ロードマップの現在位置と次の順序

- 完了済みの基盤工程: 1〜4。認証工程5、生徒ホーム等の工程6は実装済み。詳しい根拠・制約はロードマップと各機能別計画を参照する。
- **工程9「授業演習」は[機能別計画](docs/system-configuration/feature-plans/student-exercise.md)のT001〜T017を完了、初回スライスPASS、V12適用済み**。T012で検出したrunner cleanup/UTF-8出力境界/入力拒否理由を修正し、実制限・障害復旧・認可/再ログイン・課題提出回帰を再確認、T013引継ぎまで完了。次は工程10「教師の課題・プロンプト・配信」。演習の進捗制御/未実装追加操作/配信等は後続範囲。工程8「評価・コードログ・アンケート」は主要実装済みだが、設定・認証済みE2E・ユーザー手動確認が残る。ユーザー指示でこれらを保留して開発を先へ進めるため、工程8を完了扱いにせず[同計画](docs/system-configuration/feature-plans/student-evaluation-survey.md)の残件を引き継ぐ。
- 2026-10-03追加指示により工程12の学校管理を先行実装。管理者ホームから学校一覧・登録・編集へ進む。学校単位のレベル設定と、生徒登録後の変更禁止が合意済み。根拠・検証は[認証計画の学校管理追加バッチ](docs/system-configuration/feature-plans/authentication-and-login.md)を参照。教師アカウント管理・クラス/生徒作成UIの実装完了を意味しない。
- 次の開発順は9「授業演習」 → 10「教師の課題・プロンプト・配信」 → 11「教師の進捗・提出・評価・CSV」 → 12「管理者の教師アカウント管理・画面統合」 → 13「機能横断検証」。工程7・8の未確認事項は各計画に残し、必要な設定が整った時点で再開する。
- 工程5で実装済みなのは認証・管理者ロール・初期管理者作成手段等であり、管理者用の教師アカウント管理業務画面は未実装。工程12として別途実装・検証する。
- 段階2の機能横断検証完了後、本番公開作業へ進む場合は[開発からデプロイまでの全体手順](docs/system-configuration/development-and-deployment-flow.md)と[本番運用条件の確認テンプレート](docs/system-configuration/production-operations-decisions.md)を使う。ローカルComposeを本番へ流用しない。

### 保留事項

- 管理者画面の統合（2026-10-03ユーザー指示）: 教師管理／学校管理の2タブと作成・編集・履歴モーダルへの変更は**プロトタイプのみ反映済み**。入口は[管理者画面](screen-flow-diagram/webapp/WEB-INF/admin/management.html)、詳細は[画面遷移図](docs/screen-flow-diagram.md)を参照。不要な旧ホーム・学校管理ページは削除済み。**本実装への反映は今は行わず、ロードマップ工程12で再開する**。それまでは管理者UI調整をプロトタイプ専用資産で行い、`src/main`・DB・ローカルアプリを変更しない。先行実装済みの管理者ホーム・学校管理とプロトタイプの差は意図的な保留であり、整合のために自動反映しない。
- ユーザーへのリマインド（2026-10-03）: **評価確認機能とアンケート機能は、後でユーザーが手動確認する**。現在はプロンプト未設定のため評価生成を含む確認を保留し、開発は先へ進める。必要なプロンプト・アンケート設定が整った時点で、提出確定後の「評価の確認」への遷移、評価表示、アンケートへの導線・保存・送信を確認するようユーザーへリマインドする。確認結果は[評価・コードログ・アンケート計画](docs/system-configuration/feature-plans/student-evaluation-survey.md)へ記録し、確認待ちを検証完了扱いにしない。
  - 2026-10-04ユーザー確認: 評価・アンケートの確認再開は**教師向け画面が完成し、必要な設定を行えるようになってから**とする。直近は授業演習のユーザー手動確認を優先し、指摘への対応中は工程10へ自動で進まない。
  - 第95〜97版の追加T018〜T024は実装・検証済み。次はユーザーの授業演習手動確認を継続する。移動機能は次回対象で未実装のため、本バッチの完了と混同しない。根拠と未確認事項は[授業演習計画Plan 3.7](docs/system-configuration/feature-plans/student-exercise.md)を参照する。
  - 第98版は方針承認済み。T025〜T029のごみ箱基盤・単一ルート/明示確認による既存データ統合を実装・移行検証・ローカル反映済み。次はT030のアップロード同名解決、T031の最終確認へ進む。通常の領域選択は非表示、複数移行元がある場合だけ確認前の切替と統合確認を残す。第98版全体の完了と報告しない。可視ブラウザーの通常クリックは検証済みだが、利用者自身の画面とOSファイル選択の再確認は残る。詳細は[授業演習計画](docs/system-configuration/feature-plans/student-exercise.md)が正本。
- IC-010の保存期間満了時処理は、ユーザーが再開を指示するまで着手しない。
- 第99版T032〜T034は承認済み・実装/検証/ローカル反映済み。ごみ箱の名前/階層確認、全文tooltip、一括ZIP名のルート名統一を追加した。次工程は引き続きT030/T031。受入結果とOS保存の手動確認残件は[授業演習計画Plan3.12](docs/system-configuration/feature-plans/student-exercise.md)を参照する。
- 第100版エクスプローラ拡張はT035〜T042と既存T030/T031まで実装・検証・ローカル反映済み。上記の各版の未実装記録よりこの最新状況を優先する。次はユーザーの授業演習手動確認を継続し、工程10へ自動で進まない。詳細・証跡・OS操作等の未確認事項は[授業演習計画Plan3.13〜3.18](docs/system-configuration/feature-plans/student-exercise.md)を正本とする。
- 第101版の移動先ツリー/補助操作折りたたみ/選択モード/名前幅改善もT043〜T045で反映・検証済み。異なる階層の選択はユーザー承認により維持する。次は引き続きユーザー手動確認。詳細は[授業演習計画Plan3.19/3.20](docs/system-configuration/feature-plans/student-exercise.md)を参照。
- 第102版T046/T047も反映・検証済み。初期配置は追加open、表示設定/選択操作closeを優先し、チェックで選択操作を自動展開しない。全展開/折りたたみ・小型upload・検索位置と検証証跡は[授業演習計画Plan3.21](docs/system-configuration/feature-plans/student-exercise.md)参照。引き続き利用者のデザイン/手動確認を継続する。
- 第103版T048/T049も反映・検証済み。作成は1行文字の縦2段＋中央upload、操作アイコン/枠なし、検索解除は入力内×。選択モード中は0件でも操作表示。親folder配下は含まれるチェック済み・個別解除不可表示で明示IDは増やさない。最新の仕様/検証は[授業演習計画Plan3.22](docs/system-configuration/feature-plans/student-exercise.md)参照。
- 第104版T050/T051も反映・検証済み。選択操作は同幅2列、件数補足なし、確認確定は操作名/削除だけ赤、削除確認は有効全配下を表示する。POST単位/ごみ箱/取消dirtyは維持。[授業演習計画Plan3.23](docs/system-configuration/feature-plans/student-exercise.md)参照。ユーザー手動確認を継続する。
- 実装・検証の最新状況はこのHandoffへ細かく複製せず、ロードマップと対象の機能別実装計画だけを更新する。
- 2026-10-04ユーザー指示: 授業演習の不具合修正と最終確認を行い、**教師向け工程の手前で停止する**。通常Chromeの単体.py保存・内容一致は利用者確認済み。最新結果と検証限界は[授業演習計画](docs/system-configuration/feature-plans/student-exercise.md)を参照し、工程10へ自動で進まない。

### 再開時に最初に行うこと

1. [ロードマップ工程10](docs/system-configuration/implementation-roadmap.md)、対象の機能仕様/教師画面プロトタイプ・状態ルール・DB設計と[実装契約](docs/system-configuration/implementation-contract.md)を確認し、教師の課題/プロンプト/配信の機能別計画を作成する。未合意事項が影響する場合は着手前に確認する。
2. [授業演習計画](docs/system-configuration/feature-plans/student-exercise.md)のT012修正後の最終判定とT013を引き継ぐ。T001〜T017/V12を作り直さず、初回スライス完了を教師配信や演習進捗制御全体の完了とみなさない。修正前のFAIL履歴は保持し、最終13件成功と区別する。ネイティブ保存ダイアログの利用者操作、本番全資源認証等の未確認事項も同計画で保持する。
3. 計画に沿ってDB → DAO → Control → Servlet → JSP/JavaScript → 保存後の再読込・権限境界・ブラウザー検証の順で進める。授業演習では課題用の30秒コードログを記録しない。画面はプロトタイプに従い、授業演習の結果カードは課題本実装と共通の対話ターミナルを維持する。
4. 管理者統合の本実装、実管理者アカウント作成、IC-010には自動着手しない。管理者統合は工程12かつユーザーの「ローカル環境へ反映」指示後に再開する。
