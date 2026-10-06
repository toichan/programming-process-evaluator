# 教師プロンプト設計・AI生成 S2 実装計画

作成日: 2026-10-04 JST。更新: 2026-10-06 JST。状態: **S2/T014実装・合成データによる専用DB/ブラウザー受入完了。Gemini実APIを使った稼働確認はユーザー判断で後続へ延期。実提出・Gemini実APIによる本番相当確認・共有8080/DBへの反映は未実施。画面遷移図とprototypeは未変更。**

本計画は工程10 S2のみを扱う。教師課題下書きS1は[教師課題下書き計画](./teacher-task-draft.md)に記録する。S3（公開・改訂・削除/復元）、S4（演習コード配信）、工程11（教師の進捗・提出・評価・CSV）は対象外。

## 目的と範囲

- 権限を持つ教師が自分の課題を選び、課題に紐づくプロンプト版を編集・保存し、AIによる揺らぎ項目と評価例を確認して、DBから再表示できるようにする。
- 揺らぎ項目、生成状態、評価例、プロンプト版をサーバー側で検証し、DBへ保存する。ブラウザー内の固定サンプルや`localStorage`を業務データの正本にしない。
- 全課題はDBに登録済みの共通標準ルーブリックを評価用ルーブリックとして使う。課題ごとの選択・作成・編集は行わず、同じactive標準版を課題の`rubric_id`へ関連付ける。
- 課題内容の編集は課題編集画面を正本とする。プロンプト設計画面では課題情報を参照・AI入力へ使用し、task内容は更新しない。
- 「課題プロンプトを保存」は未適用の案として保存し、現在適用版は切り替えない。「設定して再評価する」の明示確認で対象生徒・評価差分を確認した後、新版を現在適用版へ切り替え、全生徒の最新提出を再評価する。対象生徒ごとの予測は確定前にGeminiで生成し、教師が確定した場合は同じ応答を新しい評価履歴へ再利用する。キャンセル時も確定前呼出しのAPI費用は発生するが、評価行・active版・jobは作成・変更しない。旧評価は上書きしない。
- S2は課題選択、プロンプト下書き、揺らぎ項目生成、教師対応記述、課題別追加指示、評価例生成・保存・履歴表示、適用版切替、全体再評価ジョブ/進捗を含む。課題の公開・予約公開はS3、演習コード配信はS4とする。
- AI評価例生成、開発/受入/APIスモークテストには合成データのみを使用する。運用機能の確定前previewは、明示合意済みの通り実際の対象提出/コードログを既存の匿名化経路でGeminiへ送信する。preview機能を有効にする実装・運用は本計画の実装/受入後に限り、今回の計画作成では外部APIを呼び出さない。
- 上記の合成データ限定は開発・受入・疎通検証の規則であり、ユーザー確認済みの本番機能契約（確定前に対象生徒の最新提出をGemini評価する）とは区別する。契約レビュー中に実提出を送信したり実APIを呼び出したりはしない。
- 画面遷移図・プロトタイプのデザイン、項目、導線は変更しない。既存の課題編集と教師共通UIに接続し、プロトタイプの「設定して再評価する」を実際の認可・プレビュー・再評価ジョブへ接続する。
- 以下の`REQ-101〜109`は本計画内の追跡IDであり、機能仕様書に新しい要件を追加するものではない。機能仕様にREQ-IDがないため、既存節を対応先として記録する。

## 必読資料

| 分類 | 根拠 |
|---|---|
| 工程・引継ぎ | [AGENTS.md](../../../AGENTS.md)、[実装ロードマップ](../implementation-roadmap.md)工程10、[実装・ローカル開発手順](../implementation-and-local-development.md)、[S1計画](./teacher-task-draft.md) |
| 要件・画面 | [機能仕様書](../../function-specification.md)「教師向け共通追記」「課題編集機能」「プロンプト設計機能」「評価機能」、[画面遷移図](../../screen-flow-diagram.md)教師向け画面構成 |
| 状態・契約 | [プロンプト状態ルール](../../state-rules/teacher/prompt-state-rules.md)、[課題状態ルール](../../state-rules/teacher/task-state-rules.md)、[実装契約 IC-001〜IC-010](../implementation-contract.md) |
| AI連携 | [生成AI API連携設計](../../ai-api-integration-design.md) §2.2〜2.4、§3.2〜3.3、§4〜6。エラー・費用・匿名化条件を変更しない |
| DB・設計 | [テーブル定義](../../database-design/table-definitions.md) `tasks`、`rubrics`、`rubric_dimensions`、`rubric_criteria`、`criterion_levels`、`prompt_versions`、`prompt_fluctuation_items`、`evaluation_examples`、`evaluations`、`audit_logs`、[教師クラス図](../../class-diagram/03-teacher-task-distribution.puml) |
| プロトタイプ | [プロンプト画面HTML](../../../screen-flow-diagram/webapp/WEB-INF/teacher/prompt/prompt.html)、[プロンプト画面JavaScript](../../../screen-flow-diagram/webapp/js/teacher/prompt/prompt.js)、[プロンプト画面CSS](../../../screen-flow-diagram/webapp/css/teacher/prompt/prompt.css)、[課題画面HTML](../../../screen-flow-diagram/webapp/WEB-INF/teacher/task/task.html)、[共通feedbackガイド](../../feedback-guideline.md) |
| 既存実装 | [TeacherTaskControl](../../../src/main/java/control/teacher/TeacherTaskControl.java)、[TeacherTaskDao](../../../src/main/java/dao/TeacherTaskDao.java)、[EvaluationQueueDao](../../../src/main/java/dao/EvaluationQueueDao.java)、[GeminiEvaluationClient](../../../src/main/java/control/evaluation/GeminiEvaluationClient.java)、[StandardRubricDao](../../../src/main/java/dao/StandardRubricDao.java) |

別個の`constitution.md`、project topology、アーキテクチャ調査のartifactはリポジトリ内で確認できなかった。既存の状態ルール、DB定義、クラス図、実装と[AGENTS.md](../../../AGENTS.md)を設計根拠とし、欠けたartifactの内容を推測で補わない。機能仕様に`NEEDS CLARIFICATION`またはREQ-IDはなく、本計画の追跡IDはS1計画と同じローカル方式を採用する。

## 現状照合と着手ゲート

| チェック項目 | 判定 | 根拠 | 確認できた差分 | 実装前の扱い |
|---|---|---|---|---|
| プロンプト画面 | 要修正 | プロトタイプHTML/JS、ロードマップS2 | 固定課題・モデル、`localStorage`、遅延後に合成揺らぎ候補を表示する処理。S2の本実装はない | プロトタイプの構成・語彙を維持し、実データ/実APIに置換する |
| 課題別プロンプト版 | 要修正 | IC-007、状態ルール、`prompt_versions` | DB表はあるが教師向けCRUD・版履歴・状態遷移は未実装 | 適用版を直接変更せず、未適用下書きをトランザクションで保存する |
| 評価用ルーブリック | 合意済み D-01 | 2026-10-04ユーザー回答、DBのactive共通標準版、`tasks.rubric_id`、`EvaluationQueueDao` | 全課題でDB登録済み共通標準ルーブリックを使い、課題ごとの選択/作成を設けない。S1課題は`rubric_id`がNULL | 課題作成時にactive標準版を参照し、既存の未削除draftでNULLのものだけ同じ版へ関連付ける。標準版がない/不整合なら明示エラー |
| 現在適用版・全体再評価 | ユーザー方針合意 D-02、2026-10-05契約判断と同期 | 2026-10-04/05ユーザー回答、機能仕様改版113〜124、IC-007、prompt/task state rules | 明示確認後に対象の最新提出を新規評価行で再評価し、旧評価は不変。最新提出なしはpreviewに対象外表示し、jobから除外。課題状態ルールの限定例外は改版113 | preview作成時snapshot、予測応答再利用、ゼロ件/初回適用、部分失敗、通知なしを合意済み契約どおり実装。通知経路は追加しない |
| プロンプト内の課題内容編集 | 合意済み D-03 | 2026-10-04ユーザー回答、機能仕様「共通プロンプト」、プロトタイプStep 1、S1課題編集 | 課題編集画面が正本。プロンプト画面では参照・AI入力のみ | prompt routeはtask内容を更新しない。仕様は改版113で記録 |
| 生成例の入力データ | 既定方針 | AI設計§3.3、AGENTS外部API条件 | AI設計は匿名化提出サンプルを記載する | S2の試験と評価例は完全合成データのみ。実在データ送信を追加する場合は別途確認・合意する |
| 画面と監査 | 要修正 | 教師共通追記、feedbackガイド | 固定教師ID、sample履歴、ブラウザー保存がある | `AuthenticatedUser`、所有範囲、`audit_logs`とshared feedbackに接続する |

### 合意済みの決定事項

| ID | 決定 | 反映先 |
|---|---|---|
| D-01 | 全課題にDB登録済みの共通標準ルーブリックを使う。課題別の選択・複製・編集は設けない | 機能仕様改版113、Plan 2.1、課題作成時の関連付け |
| D-02 | prompt保存は案とし、確認後にactive版を切替え、最新提出がある対象者を新規評価行で再評価する。旧評価は保持し、完了時に生徒への永続通知は行わない | 機能仕様改版113〜124、課題/プロンプト状態ルール、Plan 5.1 |
| D-03 | 課題内容の正本は課題編集画面。プロンプト設計では参照・AI入力のみ | 機能仕様改版113、Plan 2.2 |

### 全体再評価契約と実装設計の状態

- previewの予測方式・対象集合・snapshot時点/保存の方向・30分期限・部分失敗・初回適用・対象ゼロ件・通知方針は、2026-10-05のユーザー確認に基づき機能仕様/状態ルール/DB定義へ反映済み。
- [DBテーブル定義](../../database-design/table-definitions.md)のpreview親・target子・job target 3表は [V18 migration](../../../src/main/resources/db/migration/V18__support_reevaluation_preview_staging.sql) として実装済み。確定/cleanupの実装と検証結果はPlan 5.3〜5.5の実施記録を参照する。
- 最新提出行が`submitted`の間は古い版へfallbackせず、その参加者を「提出受付中・確定不可」と表示しpreview全体の確定を止める安全側の処理を実装した。開始/確定時に参加者行をlockして提出処理との競合を避ける。
- 再評価完了時は生徒への永続通知を送らない。教師にjob完了を表示し、生徒は次回評価画面を開いた際に最新評価を確認する。通知一覧や画面toastを永続通知として追加しない。

## ユースケースとデータ契約

| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 課題選択・現在版表示 | `task-management`利用権限を持つ教師。自分が作成した未削除課題 | `taskId` | 課題、作成者、課題入力、適用ルーブリック、現在適用版、版履歴、権限 | 必要時、rubric未設定の対象draftに共通標準版を関連付け | なし | 不存在/別所有/削除済みは同じnot-found応答。権限拒否403、標準rubric不在/不整合は明示エラー |
| プロンプト下書き保存 | 同上 | task ID、期待version、モデルID、共通プロンプト、追加指示、CSRF | 課題と認可、当該課題の各prompt versionと生成済み子データ | `prompt_versions`、必要時`audit_logs` | 新規draft作成または未適用draftの同一版更新。適用済み/履歴版は不変 | 成功後DB再読込。入力不正400、version競合409、権限拒否403、保存障害503 |
| 揺らぎ項目生成 | 同上 | prompt version ID、確定済みプロンプト、課題情報 | 課題・プロンプト版・既存生成状態 | 開始/終了状態、揺らぎ項目、監査記録 | `not_generated/failed → in_progress → completed/failed` | 生成中/結果/再試行可能エラーを明示。API失敗を成功扱いしない |
| 揺らぎへの対応記述保存 | 同上 | 項目ID、方針、resolution state、順序、追加指示 | 課題・prompt版・項目 | `prompt_fluctuation_items`、prompt下書き、監査記録 | `pending → resolved/not_applicable`。未確定は未解決として残す | 保存後再読込、未解決数を明示 |
| 評価例の生成/保存 | 同上 | 確定ルール、完全合成の入力サンプル | 課題、共通標準rubric、選択prompt版、揺らぎ/解決 | `evaluation_examples`、生成状態、監査記録 | `not_generated/failed → in_progress → completed/failed` | JSON schema検証後のみ表示/保存。失敗は明示し再試行可能 |
| 再評価対象プレビュー | 同上。許可学校権限を再検証 | task ID、prompt version ID | 全参加者、最新提出、既存評価、prompt/rubric、固定評価入力 | preview専用一時保存構造へ対象snapshotとGemini応答を期限付き保存 | `generating → ready/failed/stale/expired`（詳細schemaは別途設計） | 全参加者を表示し、提出なしは対象外。提出のある対象者ごとにGemini予測を表示。全予測成功までは確定不可。previewだけでactive版/evaluation行/jobを変更しない |
| 全体再評価の確定 | 同上。課題の全対象学校権限 | task ID、preview ID、期待version、明示確認、CSRF | previewの固定snapshot/予測応答、権限、課題/提出/prompt/rubric version | active prompt切替、`reevaluation_jobs`、新規評価行、監査を一貫して保存 | 新版をactiveにし、jobを`queued`へ | 30分以内・snapshot一致・全予測成功時のみ確定。競合/権限/準備不足ではpreviewを無効化し、active/評価/jobを変更しない |
| 再評価処理/完了 | job作成時に対象として固定された生徒提出。教師は許可範囲内で状態参照 | job ID | 固定対象、提出/ログ snapshot、rubric、prompt、現評価 | 対象ごとに新しい`evaluations`行を追加し、job状態/件数/進捗を更新 | `queued → in_progress → completed/failed` | 部分失敗を隠さず表示。教師にはjob完了を表示し、生徒には次回評価画面で最新評価を表示。永続通知は送らない |
| 履歴表示 | 所有課題の教師 | task ID/version ID/job ID | 課題の版、生成状態、再評価job/評価行、作成者、audit | なし | なし | 過去版はread-only。旧評価と新評価を別履歴として識別。未登録/部分失敗を明示 |

AIリクエストはDBロックを保持したまま送信しない。揺らぎ項目/評価例生成や開発・受入検証には合成サンプルのみを用いる。一方、ユーザー確認済みの再評価previewでは各対象生徒の提出に基づく評価入力をGeminiへ送り、その匿名化済みsnapshotと応答をpreview専用領域へ一時保存する。応答のスキーマ・識別子を検証し、失敗対象のみ期限内に再試行する。APIキー、直接識別子、例外に含まれる秘密情報は画面・ログに含めない。

## 実装方針と計画項目

### Technical Context

- **Language/Platform**: Java 21、Servlet/JSP、Tomcat 9/Gretty、Gradle、MySQL、Flyway。
- **Architecture**: Servlet → Control → DAO → MySQL。外部生成はサーバー側AI clientから実行し、ブラウザーへAPIキーを渡さない。
- **Existing assets**: `prompt_versions`、`prompt_fluctuation_items`、`evaluation_examples`、`tasks.active_prompt_version_id`、`tasks.rubric_id`は初期schemaにある。S2では現行schemaとmigration履歴を確認し、具体的な不足を立証した場合にだけ追加migrationを行う。
- **External API**: 既存のGemini設定・HTTP実装・JSON検証・エラー方針を再利用する。固定モデル候補をUIへ埋め込まず、サーバー許可リスト/設定と現行接続設計に合わせる。
- **Testing**: Gradle/JUnit、既存のMySQL専用統合テストパターン、認証HTTPとブラウザー操作。共有開発DBはテスト用に初期化・migrationしない。
- **Dependencies**: 外部SDKを追加せず、既存依存関係を優先する。

### Constitution Check

別個のconstitution文書はないため、[AGENTS.md](../../../AGENTS.md)の方針で確認する。

| 原則 | 判定 | 計画への反映 |
|---|---|---|
| プロトタイプを必要なく変更しない | PASS | 見た目/項目/導線は維持。機能境界の変更が必要なら文書とプロトタイプの確認を先行 |
| DBを正本とし、Servlet→Control→DAOで処理 | PASS | prompt、生成結果、履歴をDBへ保存し、再読込で検証 |
| サーバーでロール・所有範囲・状態遷移を検証 | PASS | 全操作でactive教師、機能権限、課題所有、版と状態を再検証 |
| AI失敗・不正応答を明示し、成功形にしない | PASS | タイムアウト、JSON/schema不正、API失敗を別状態として表示・記録 |
| 合成データのみで実API疎通確認 | PASS | 実生徒データを外部送信しない。費用確認を含む実呼出しは別確認 |
| テスト/実装エラーを記録 | PASS | 発生したビルド・テスト・外部APIエラーをerror-reportへ記録 |

### Applied Guidelines

適用可能な移行ガイドラインは確認されなかった。通常の機能追加として既存のServlet/JSP・JDBC・Bootstrap・shared feedbackの規約を使う。

### Implementation Steps

#### Phase 1: 着手ゲート・基盤

##### Plan 1.1: 合意済み方針を実装契約へ落とし込む（REQ-108, REQ-109）

- **Requirements**: REQ-108, REQ-109
- **Design inputs**: [実装契約IC-007](../implementation-contract.md)、[課題/プロンプト状態ルール](../../state-rules/teacher/prompt-state-rules.md)、[機能仕様書](../../function-specification.md)、本計画の合意済み決定事項D-01〜03。
- **Description**: 合意済み3方針を変更せず実装する。着手時に評価差分プレビューの既存データ/算出方法、生徒通知の既存チャネル、再評価jobの対象提出固定方法、初回適用/対象ゼロ件時の契約、active標準ルーブリックのschema上の識別方法を資料・実装と照合する。既存契約がない項目は独自仕様を作らず、該当機能をblockedとしてユーザー確認を得る。画面遷移図・プロトタイプは変更しない。

##### Plan 1.2: 専用DB・Gemini検証条件と既存schemaを確認する（REQ-109）

- **Requirements**: REQ-109
- **Design inputs**: [S1受入計画](./teacher-task-draft.md)、[テーブル定義](../../database-design/table-definitions.md)、[AI連携設計](../../ai-api-integration-design.md)。
- **Description**: 稼働DBを初期化せず、専用合成MySQL schemaでのmigration/統合テスト条件を確立する。appのGemini設定有無はsecret値を表示せず確認する。契約が決まるまでは外部APIを呼ばない。既存S2テーブル/制約との相違を一覧化し、必要がなければmigrationを増やさない。

#### Phase 2: 課題プロンプトの下書き・履歴（REQ-101, REQ-102, REQ-106）

##### Plan 2.1: 型付きpromptモデル・DAO・状態/認可を実装する

- **Requirements**: REQ-101, REQ-102, REQ-106
- **Design inputs**: IC-007、教師権限DAO、`prompt_versions`/`prompt_fluctuation_items`/`evaluation_examples`定義、S1の競合・監査パターン。
- **Description**: 課題所有・利用権限を検証する型付きprompt DTO、DAO、Controlを追加する。新規課題にはDB登録済みactive共通標準rubricを自動設定し、既存S1未削除draftの`rubric_id`がNULLなら同じ標準版へ関連付ける。教師によるrubric選択/作成/編集は設けず、標準版の欠落・不整合を明示エラーにする。未適用draftは同じ版で更新可能、適用版/過去版は上書き禁止とする。親promptと子項目/評価例の保存・audit記録を同一transactionにし、schema不足が立証された場合のみmigrationを計画する。

##### Plan 2.2: 教師Servletで課題別promptを読込・保存する

- **Requirements**: REQ-101, REQ-102, REQ-106, REQ-107, REQ-109
- **Design inputs**: Prompt prototype Step 1/History、教師認証filter、S1の`TeacherTaskServlet`。
- **Description**: 選択課題と既存prompt版をDBから取得し、CSRF・request size・文字数・model allowlist・task owner・draft stateを検証して保存する。課題内容は課題編集画面から読み取るだけで、prompt routeから`tasks`の内容を更新しない。作成者/更新者/日時/操作結果を記録し、過去版を読取専用にする。非所有、公開、削除、存在しないIDに対する読み書きを拒否する。

#### Phase 3: AI揺らぎ項目・教師対応（REQ-103, REQ-104, REQ-109）

##### Plan 3.1: 揺らぎ項目生成clientと状態遷移を実装する

- **Requirements**: REQ-103, REQ-109
- **Design inputs**: [AI連携設計§3.2](../../ai-api-integration-design.md)、GeminiEvaluationClient、prompt state enums。
- **Description**: 課題情報と確定promptから最小ペイロードを作り、secretをサーバー側で読み、既存Interactions API transport/JSON処理を再利用する。`in_progress`を別transactionで保存してから呼び出し、API待機中にDB row lockを保持しない。JSON schema、必須fields、件数、長さを検証し、妥当な応答だけ保存する。AI設計書にある同一payload/簡略payloadの各1 retry上限を守り、最終失敗は`failed`・監査・shared feedbackへ反映する。

##### Plan 3.2: 揺らぎ項目への教師対応と課題別指示を保存する

- **Requirements**: REQ-104, REQ-102, REQ-106, REQ-109
- **Design inputs**: `prompt_fluctuation_items` schema、Prompt prototype Step 2、状態ルール。
- **Description**: 生成項目を項目ID/順序つきで表示し、方針と`resolved/not_applicable/pending`をサーバー検証して保存する。追加評価指示をプロンプト版の正規フィールドへ保存し、統合評価ルールを決定論的に構築する。未解決項目を成功・完了表示で隠さない。

#### Phase 4: 合成評価例生成・永続化（REQ-105, REQ-109）

##### Plan 4.1: 評価例の合成入力、生成、検証、保存を実装する

- **Requirements**: REQ-105, REQ-109
- **Design inputs**: [AI連携設計§3.3](../../ai-api-integration-design.md)、`evaluation_examples` schema、評価workerのstructured JSON検証。
- **Description**: 学生提出やコードログを読み出さず、仕様で受入条件を定義した合成コード/実行結果サンプルを用いる。rubricの観点数・尺度へ応答を対応付け、sample ID、scores、reasons、variance alerts、pre-reevaluation checklistをschema検証する。検証済みJSONのみDBに保存し、入力/出力を版履歴と結びつける。適用ルーブリック未解決の場合はAPI呼出しをブロックする。

##### Plan 4.2: 評価例・prompt版履歴と詳細を表示する

- **Requirements**: REQ-105, REQ-106, REQ-107
- **Design inputs**: Prompt prototype Step 3/History、教師共通UIとshared feedback。
- **Description**: DBから生成状態、評価例、作成者/日時、prompt versionを再読込して表示する。古いversionの詳細はread-onlyとし、ロールバックは既存版を上書きせず新しいdraftとして作る設計を維持する。画面の文字化け、XSS、モバイル幅、キーボード操作を防ぐ。

#### Phase 5: 統合受入（REQ-101〜109）

##### Plan 5.1: 共通rubric・適用切替・全体再評価を評価queueへ接続する

- **Requirements**: REQ-108, REQ-109
- **Design inputs**: [EvaluationQueueDao](../../../src/main/java/dao/EvaluationQueueDao.java)、IC-004/007、task/prompt state rules、`reevaluation_jobs` schema。
- **Description**: active共通rubric、taskのactive prompt version、2次元rubric、configured/versioned prompt、揺らぎ/評価例の`completed`という評価queue条件を維持する。教師が対象と差分previewを確認した後に明示確定した場合のみ、同じtransactionでprompt active版切替・再評価job作成・preview済み結果を新しい評価行へ再利用する。preview専用staging schemaは別途設計し、対象snapshotとGemini応答をpreviewから確定へ安全に引き継ぐ。job workerは固定提出を評価し、進捗/部分失敗を記録、旧評価/提出を変更しない。通常評価は開始時点の版を維持する。全対象のpreview予測成功前は確定不可、対象ゼロ件はactive版/jobとも変更しない。生徒へ永続通知は送らず、教師にjob完了を表示し、生徒の次回評価画面で最新結果を表示する。queue/公開境界と準備不足時のactive版不変も検証する。

##### Plan 5.2: DB/HTTP/ブラウザーを通した正常系・拒否系を受け入れる

- **Requirements**: REQ-101〜109
- **Design inputs**: S1受入チェック、`TeacherPermissionDao`、shared feedback、AI integration design。
- **Description**: 合成の教師/学校/課題/提出だけを持つ専用DBでmigration/統合テスト、全Gradleテスト、WAR、HTTP認証/認可、prompt保存後再読込、AI応答検証、履歴/監査rollback、失敗/再試行、再評価jobの対象固定・二重起動防止・部分失敗/進捗を検証する。ブラウザーでプロトタイプとの外観、正常/エラー/空状態、明示確認前にactive版が変わらないこと、320px以上のレスポンシブ、アクセシビリティを確認する。合成payloadのGemini実API呼出しは明示的な費用/資格情報確認後だけ実施し、実行結果/未確認事項を計画とerror-reportへ記録する。

##### Plan 5.3: preview staging schemaと予測生成（実装済み）

- **Requirements**: REQ-108, REQ-109
- **Design inputs**: [V18 schema](../../database-design/table-definitions.md#reevaluation_previews)、[prompt状態ルール](../../state-rules/teacher/prompt-state-rules.md)、[AI連携設計](../../ai-api-integration-design.md)、`EvaluationWorkerDao`の入力snapshot/匿名化/response validation。
- **Description**: V18でpreview親・参加者別target・確定後job targetを追加。全参加者/最新提出をpreview時点で固定し、accepted/lockedのみGemini対象、提出なしは対象外。submitted中は古い版へfallbackせず確定を止め、参加者行lockでsubmit処理との競合を避ける。共通評価入力builderで匿名化入力を作りfingerprintを保存する。`ReevaluationPreviewWorker`が予測成功/失敗を個別保存し、全対象成功後だけready、失敗者のみ再試行、30分期限を適用する。API費用の注意を画面に表示し、確定前は通常evaluationテーブルを使用しない。

##### Plan 5.4: 確定transaction・評価materialization・期限cleanupを接続する

- **Requirements**: REQ-108, REQ-109
- **Design inputs**: V18 schema、`EvaluationQueueDao`、`EvaluationWorkerDao.complete`、`EvaluationWorker`、`DataSourceLifecycleListener`、IC-004/007。
- **Description**: 教師/学校権限とpreview所有者を毎回検証する。確定ではpreview状態・期限・課題/prompt/rubric version・対象集合/input fingerprintをロック下で再検証し、active版更新・job・job targetコピー・auditを単一transactionとする。対象0件、stale、expired、未完了予測、権限失効ではactive/evaluation/jobを変更しない。確定後workerは保存済みGemini応答のみを使い、新規evaluation/request/response/input snapshot/score/reason/evidenceをappend-onlyで作る。既存評価の完了永続化処理をconnection-scoped helperへ抽出し、通常評価の動作/版固定を維持する。job targetは保存完了後にpayload/raw response/resultを消去し、evaluation ID/状態のみ保持する。取消/期限切れ/確定済み一時データは、既存の起動停止ライフサイクルに結びつくcleanup workerで削除する。

##### Plan 5.5: 再評価契約を専用DB・HTTP・ブラウザーで受け入れる

- **Requirements**: REQ-101, REQ-107, REQ-108, REQ-109
- **Design inputs**: 既存S1/S2テスト、[V18 schema](../../database-design/table-definitions.md#reevaluation_previews)、prototype維持方針、[error-report](../error-report.md)。
- **Description**: 親/子/job target一意性・FK・期限cleanup・ロールバック、同時確定/二重クリック、再提出やtask設定変更のfingerprint mismatch、予測の部分失敗/成功結果保持/失敗者のみ再試行、成功応答のjobへの一度だけの移送、Gemini再呼出しなしをmock/専用MySQLで検証する。専用環境のHTTP/ブラウザーで生成→状態確認→再試行→明示確定/取消→job進捗/完了→生徒評価再読込を確認する。画面遷移図/プロトタイプは変更せず本実装だけ既存意匠に合わせる。実提出のGemini送信を含む運用利用は本計画外で、明示承認なしには実行しない。

## Task Breakdown

T001〜T020はS2全体の実装チェックポイントが追跡する20件。T021〜T031の実装・専用DB/unit検証・T030/T031のHTTP/ブラウザー受入は完了。T014のprompt版・評価例・再評価job/対象者別結果履歴も実装し、専用DBと認証ブラウザーで受け入れた。

### Phase 1: 着手ゲート・基盤

- [x] T001 [Plan:1.1] `docs/system-configuration/implementation-contract.md`、prompt/task state rules、DB schema、通知実装を照合し、合意済みD-01〜D-03を変更せず、評価差分プレビュー・通知チャネル・再評価対象固定・初回適用/対象ゼロ件時の既存契約を特定する。既存契約がない項目は実装をblockedにして確認し、プロトタイプ/画面遷移図を変更しない。
- [x] T002 [P] [Plan:1.2] `docs/database-design/table-definitions.md`、Flyway migration、S1専用DBテスト手順を照合し、S2専用合成DB fail-closed gateを用意する。共有DBを初期化・migrationしない。
- [x] T003 [P] [Plan:1.2] `src/main/java/control/evaluation/GeminiEvaluationClient.java`とAI設定を確認し、秘密値を出力せず接続設定・タイムアウト・既存JSON検証の再利用箇所を特定する。外部APIは呼び出さない。

### Phase 2: 課題プロンプトの下書き・履歴

- [x] T004 [Plan:2.1] `src/main/java/entity/TeacherPromptVersion.java`と関連DTOに、prompt version、揺らぎ項目、教師対応、評価例の型・不変条件を実装する。
- [x] T005 [Plan:2.1] `src/main/java/dao/TeacherPromptDao.java`を追加し、認可済み課題の版読込、下書き保存、生成状態、項目/評価例、履歴、監査をtransaction対応で実装する。
- [x] T006 [Plan:2.1,5.1] 必要な場合だけ`src/main/resources/db/migration/V17__support_teacher_prompt_and_reevaluation_workflow.sql`（着手時に最新migration番号へ調整）を追加し、既存schemaにないprompt競合制御/再評価対象固定に必要な最小構造を追加してDB定義/クラス図を更新する。`reevaluation_jobs`が既存なことを前提に再利用し、schemaで要件を満たす場合migrationは作らない。
- [x] T007 [Plan:2.1,2.2] `src/main/java/control/teacher/TeacherPromptControl.java`と既存`TeacherTaskControl`/`TeacherTaskDao`を接続し、TeacherPermissionDao、task owner、draft-only scope、prompt version/state、競合、監査失敗rollbackを検証する。新規課題へactive共通標準rubricを設定し、既存の未削除draftでNULLの`rubric_id`を同じ版へ関連付け、教師向け個別選択UIは追加しない。
- [x] T008 [Plan:2.2] `src/main/java/servlet/teacher/TeacherPromptServlet.java`を追加し、`/teacher/prompt`で課題別読込・下書き保存・履歴表示をCSRFおよびサーバー側入力検証付きで接続する。課題情報は参照/AI入力にのみ使い、課題内容を更新しない。

### Phase 3: 揺らぎ項目と教師対応

- [x] T009 [Plan:3.1] `src/main/java/control/teacher/TeacherPromptAiClient.java`を追加し、既存Gemini transportを再利用して揺らぎ項目API request/response schema、model allowlist、timeout、再試行、secret保護を実装する。
- [x] T010 [Plan:3.1] `src/main/java/control/teacher/TeacherPromptControl.java`と`src/main/java/dao/TeacherPromptDao.java`へ揺らぎ生成の開始/終了状態、外部呼出し間のtransaction境界、JSON検証、失敗監査を接続する。
- [x] T011 [Plan:3.2] `src/main/java/control/teacher/TeacherPromptControl.java`と`src/main/java/dao/TeacherPromptDao.java`で教師対応、対応状態、追加評価指示、prompt rule mergeの入力検証・保存・再読込を実装する。

### Phase 4: 評価例生成

- [x] T012 [Plan:4.1] `src/main/java/control/teacher/TeacherPromptExampleFactory.java`で実データを使わない評価例用合成入力を作成し、評価rubricの次元/尺度へ対応するfixtureを定義する。
- [x] T013 [Plan:4.1] `src/main/java/control/teacher/TeacherPromptControl.java`、`TeacherPromptAiClient.java`、`TeacherPromptDao.java`で評価例生成、状態管理、schema/ID/score検証、検証後保存、再試行可能な失敗を実装する。
- [x] T014 [Plan:4.2,5.1] `TeacherPromptPage`/`TeacherPromptDao`へ再評価job履歴を接続し、job日時・実行者・prompt版・状態・件数・進捗と対象者ごとの提出版・状態・安全な失敗理由・総合/次元評価点を既存prompt画面で表示する。既存のprompt版履歴・評価例履歴とread-only過去版参照は維持する。専用DBのstatus/result queryと、隔離Tomcatで履歴一覧から詳細への遷移を検証する。

### Phase 5: UI統合と受入

- [x] T015 [Plan:4.2] `src/main/webapp/WEB-INF/teacher/prompt/prompt.jsp`、`src/main/webapp/css/teacher/prompt/prompt.css`、`src/main/webapp/js/teacher/prompt/prompt.js`を追加し、プロトタイプのStep 1〜3/履歴の外観を保ってDB/API接続する。固定サンプル、`localStorage`保存、成功形のタイマー動作は除去する。
- [x] T016 [Plan:4.2,5.1] `src/main/java/servlet/teacher/TeacherPromptServlet.java`と`src/main/webapp/WEB-INF/template/page-start.jspf`、`page-end.jspf`へページ表示・route・shared feedbackを統合し、対象/評価差分preview、明示確認、job progress/historyを接続する。準備不足またはT001で未解決となった操作は成功形にせず明示的に無効化する。
- [x] T017 [Plan:5.1,5.3,5.4] 全体再評価preview・明示確定・active版切替を実装。T021〜T027で30分期限、fingerprint、失敗対象retry、submitted受付中guard、取消時の非変更、原子的確定を実装・検証。
- [x] T018 [Plan:5.1,5.4] 確定済みjob targetの評価materialization・worker・cleanup・進捗表示を実装。T028〜T029で保存済みGemini responseを一度だけ評価履歴へ反映し、Gemini再呼出しなしで進捗を追跡する。
- [x] T019 [Plan:2.1,3.1,3.2,4.1,4.2,5.1] prompt権限/state/AI、preview/job worker、専用DB persistence testsと`jobId`付きGETの認証/owner境界を検証した。T030/T031のHTTP/browser受入と最新全体suite/WARも成功。結果は本書末尾のT031受入記録と[チェックポイント](./checkpoints/teacher-prompt-ai/tasks-to-impl.yaml)を参照。
- [x] T020 [Plan:5.2] Gradle全テスト/WAR、専用MySQL migration/DB受入、認証HTTP、合成データブラウザー検証を実施し、`docs/system-configuration/error-report.md`と本計画へ正確なコマンド/結果/未確認事項を記録する。

#### T017/T018 実装分解と実施状況

- [x] T021 [Plan:5.3] `docs/database-design/table-definitions.md`の`reevaluation_previews` / `reevaluation_preview_targets` / `reevaluation_job_targets`を確定し、`src/main/resources/db/migration/V18__support_reevaluation_preview_staging.sql`を作成。FK、CASCADE、unique/index、30分期限と全state値を専用MySQL schemaで検証。
- [x] T022 [P] [Plan:5.3] `src/main/java/entity/ReevaluationPreview.java`と`src/main/java/entity/ReevaluationJobStatus.java`を追加。target状態はDAO recordで型付けし、状態/JSON/ID nullabilityを表現。
- [x] T023 [Plan:5.3] `src/main/java/control/evaluation/EvaluationInputBuilder.java`へ`EvaluationWorkerDao`の課題/rubric/prompt/提出/ログ/実行結果payload構築、研究用識別子、PII redaction、known evidence ID抽出を共通化。通常評価の既存テストを維持しpreviewも同じbuilderを使用。
- [x] T024 [Plan:5.3] `src/main/java/dao/ReevaluationPreviewDao.java`と`src/main/java/control/teacher/ReevaluationPreviewControl.java`で、全参加者/最新確定提出snapshot、task/prompt/rubric/scope fingerprint、transaction保存、認可済み読込、CAS状態更新を実装。`submitted`中は古いrevisionへfallbackせず確定を拒否し、参加者行lockで提出との競合を抑止。
- [x] T025 [Plan:5.3] `src/main/java/control/evaluation/ReevaluationPreviewWorker.java`で既存providerをDB lock外から呼び、既存response validatorを使ってtargetごとのpayload/raw/validated resultを保存。全対象成功時だけready、失敗対象のみretryとし、worker unit testsを追加。
- [x] T026 [Plan:5.3,5.4] `src/main/java/servlet/teacher/TeacherPromptServlet.java`、`src/main/webapp/WEB-INF/teacher/prompt/prompt.jsp`、`src/main/webapp/js/teacher/prompt/prompt.js`を既存画面へ接続。preview操作/結果とjob状態/進捗を表示し、queued/in_progress中だけpollする。既存CSSとプロトタイプの外観/導線は変更していない。
- [x] T027 [Plan:5.1,5.4] `src/main/java/control/teacher/ReevaluationPreviewControl.java`で権限、preview owner/state/expiry、task/prompt/rubric/participant/submission/input fingerprintを検証してlock。active prompt切替、job/job targetコピー、audit、preview確定/子削除を1 transactionで行い、二重confirmと失敗rollbackを防止。
- [x] T028 [Plan:5.4] `src/main/java/dao/EvaluationWorkerDao.java`の評価保存処理をconnection-scopedに共用し、`src/main/java/control/evaluation/ReevaluationJobWorker.java`と`src/main/java/dao/ReevaluationJobDao.java`でtarget claim、通常evaluation/request/response/input snapshot作成、保存済み結果のmaterialization、進捗/失敗を実装。Gemini providerは呼び直さない。
- [x] T029 [Plan:5.4] `src/main/java/dao/ReevaluationPreviewDao.java`と`src/main/java/dao/ReevaluationPreviewWorkRepository.java`で期限切れ/取消preview cleanupを実装し、`src/main/java/control/evaluation/ReevaluationPreviewWorker.java`および`DataSourceLifecycleListener.java`でworker start/stopを接続。
- [x] T030 [Plan:5.5] `TeacherPromptServletTest`で`jobId`付きGETの教師role、不正ID、task ID欠落を検証し、V18専用DBの`TeacherTaskDatabaseTest`でqueued/in_progress/completed/failedの状態・completed/failed件数・progressとtask/job/owner境界を検証する。教師DB test 13件成功、`EvaluationDatabaseTest` 6件成功/1 Gemini live smoke skip。T031 browser表示中に見つかったJSP EL/record property解決500もJavaBean gettersと`Introspector`回帰testで修正。
- [x] T031 [Plan:5.5] V18の使い捨てschemaと現行sourceの隔離Tomcatで、保存済みの合成provider previewを認証browserから再読込し、preview対象1人、思考4/5・態度3/5、合成理由がJSP errorなく描画されることを確認。確認modalから確定するとredirect URLに`taskId=3`、`promptVersionId=3`、`jobId=9`が保持され、画面に完了1/1成功・0失敗が表示された。job workerは保存済み応答をmaterializeし、provider/Geminiを再呼出ししていない。最新`gradle test war --no-daemon --console=plain`成功、JUnit 204件（149 passed / 0 failed / 55 skipped）、WAR生成成功。共有8080/DB、Gemini実API、実提出/個人情報、画面遷移図/prototypeは使用・変更なし。隔離app/DB/browser network削除、browser `about:blank`を確認。詳細は[error report](../error-report.md)。

## Project Structure

```text
src/main/java/
  control/teacher/
    TeacherPromptControl.java
    ReevaluationPreviewWorker.java
    TeacherPromptAiClient.java
    TeacherPromptExampleFactory.java
  control/evaluation/
    EvaluationInputBuilder.java
    EvaluationResultPersister.java
    ReevaluationWorker.java
    ReevaluationPreviewCleanupWorker.java
  dao/
    TeacherPromptDao.java
    ReevaluationPreviewDao.java
    ReevaluationJobDao.java
  entity/
    TeacherPromptVersion.java
    TeacherPromptPage.java
    ReevaluationPreview.java
    ReevaluationPreviewTarget.java
    ReevaluationJobTarget.java
src/main/webapp/
  WEB-INF/teacher/prompt/prompt.jsp
  css/teacher/prompt/prompt.css
  js/teacher/prompt/prompt.js
src/main/resources/db/migration/
  V17__support_teacher_prompt_workflow.sql  # already applied
  V18__support_reevaluation_preview_staging.sql  # proposed; not created
src/test/java/
  control/teacher/TeacherPromptControlTest.java
  dao/TeacherPromptDaoTest.java
  servlet/teacher/TeacherPromptServletTest.java
```

上記は計画時点の候補名。既存の配置・命名に合わない場合は同じ責務を保って実装前に調整する。実装時のmigration番号は最新の採番状況を確認する。

## 検証戦略・完了条件

- **アプリ種別**: server-rendered HTML + Servlet + 外部AI。
- **重要journey**: (1) 教師が自分のdraft taskを選び共通標準rubricとprompt draftを確認/保存/再読込、(2) 揺らぎ項目を生成して状態/項目/失敗を確認、(3) 教師対応と追加指示を保存し再読込、(4) 合成サンプルから評価例を生成/検証/保存して履歴表示、(5) 全参加者をpreviewし最新提出がある生徒のGemini予測を確認、失敗生徒だけ再試行して明示確定、(6) job完了/部分失敗を教師が確認し、生徒の評価画面に新履歴が表示、(7) no-submission/processing/stale/expired/zero-target/first-apply、他教師/学生/admin/別task/旧version/不正rubricの拒否。
- **Primary stack**: JUnit/Gradle、専用MySQL、Tomcat HTTP、ブラウザー。V1〜V18 migration/validateとT017/T018向け専用DBテストを成功確認済み。2026-10-05に専用HTTP port 18081で認証後JSP/課題選択/下書き保存/再読込を受入し、初回下書き編集属性の欠落を修正した。共有8080への反映とは区別する。job進捗画面の認証HTTP/ブラウザー受入は未実施。
- **Test data**: 合成教師・学校・クラス・課題・提出サンプルのみ。各受入は独立した専用schemaを使用し、共有開発DBの初期化・migrationを禁止。
- **Primary validation stack**: JUnit/Gradle; Docker Composeの専用MySQL schemaによるV1〜V18 migration/統合テスト; Tomcat HTTP auth/CSRF; 実ブラウザー受入。Geminiはmockを使い、live APIは別途明示許可がない限り使用しない。
- **Primary validation stack**: JUnit 5/Gradle unit・DAO・Servlet tests、Docker Compose専用MySQLでV1〜V18 migration/DB integration、認証HTTP/CSRF integration、Node.js + Playwright ChromiumでJSP/JS browser E2E。Geminiは注入可能なfake/mock providerを使用し、live APIは別途明示許可がない限り使用しない。着手時の実測はDocker daemon available、Node.js unavailable。PlaywrightはNode.js/Chromium capabilityを実装時に独立して再確認する。既存のJUnit test assetsはunit/DB/Servlet testsで、既存Playwright/Selenium/Cypress suiteは見つかっていない。
- **Fallback matrix**: `infra-tier` primaryはDocker MySQL、fallbackはなし（MySQL JSON/FK/locking挙動が必須）。Docker不可ならmigration/DB競合検証は未確認として停止し、unit testだけで受入PASSにしない。`browser-tier` primaryはPlaywright。Node/Playwright導入が特定の技術的理由で不可の場合だけ、JUnit Servlet/HTTP integrationへfallbackし、JSP描画・JS状態・responsive/keyboardが未検証であると記録する。VS Code統合ブラウザーの手動操作は利用可能なら追加証拠となるが、runnable Playwright suiteの代替とはしない。DockerとNode/browser capabilityは独立に判定する。
- **Environment requirements**: Java 21/Gradle、機能実装と同じV18 migrationを適用するDocker daemon/MySQL、Tomcat HTTP、Node.js、Playwright Chromium。外部networkが必要なbrowser install/API依存取得の可否も記録する。実装時に`docker info`、`node --version`、Playwright browser installを別々に試し、一方の欠落を他方のfallback理由にしない。
- **Known gaps**: Docker MySQL不可時はmigration、JSON、row locking、unique/FK制約の本番相当性が未検証。Node/Playwright不可時はHTTP fallbackではJSP/静的資産/実ブラウザー挙動の検証が欠落する。Gemini live未実施時は外部provider接続/運用費用を検証していない。現時点ではNode.js unavailableのため、Playwright installationは未試行。
- **Test data strategy**: 合成教師/学校/クラス/課題/提出のみ。各testに一意task/participationをseedし、使い捨て専用schemaを用いてrollbackまたは専用schemaごと破棄する。接続先schema/project/portをassertし、共有8080/DBには決して接続しない。
- **External dependency strategy**: MySQLは専用Docker instanceを使う。Geminiはfake providerで成功/不正応答/timeoutを固定し、通常テストから外部通信を行わない。JSP/Servletは実Tomcatの専用HTTP portを使い、API keyは渡さない。schema migration/FK/transaction競合は組込みDBへ置き換えない。
- **Test infrastructure**: JUnit 5/既存Gradle `test` taskをcanonical commandとし、`gradle test war --no-daemon --console=plain`を使う。preview worker unit testsおよび専用schemaのpreview/materialization・published-task prompt DB testsを追加済み。各実装taskは該当module testsのpass/fail/skipとexit codeを記録し、DB-gated/live API testsは全体suiteとは別に実行する。
- **Acceptance**: (1) 参加者全員/no-submission/最新提出/first-apply/zero-target preview、(2) provider mock call count・部分retry・成功応答再利用、(3) owner/権限/stale/expiry/二重confirm/transaction rollbackとactive版/job不変、(4) job materialization・idempotency・旧評価不変・通常evaluation worker回帰、(5) 教師の生成/再試行/確定/取消と生徒の評価再読込を受け入れる。API failureやdata mismatchをsuccessにしない。`submitted`中の確定停止と参加者lockは実装済み。job進捗の認証HTTP/ブラウザーjourneyは未確認。
- **Validation review expectations**: implementation tasksごとにmodule unit/DB testsを実行し、正確なcommand/exit code/pass/fail/skipを記録。migration, HTTP, browser evidenceを別々に提示し、fallback理由とcoverage gapを各能力軸ごとに記録する。Gemini費用・実データ送信はこの検証計画に含めず別承認とする。
- **適用ガイドライン**: 適用可能なStruts/Spring等のframework migration guidelineはない（Servlet/JSP/MySQL既存構成の追加機能実装で、framework migrationではない）。既存のControl→DAO、connection transaction、worker lifecycle、shared feedback、prototype-first fidelityを踏襲する。
- **未実施時の扱い**: 実API未確認ならmock/専用DBでの結果と区別し、AI接続済み・評価準備完了とは報告しない。通知一覧は新設しない。S2実装済みをもってqueue readinessまたはS3公開可能とみなさない。
- **実装開始条件・現在の残件**: D-01〜D-03と全体再評価契約に沿うV18およびworker/preview/job UIは実装済み。認証HTTP/ブラウザーでjob進捗を受け入れるまでは、全体再評価を本番利用可能とは扱わない。Gemini実API/実提出送信は運用上の明示承認を別途得る。

## AutoPilot着手パッケージ実行記録（2026-10-05）

### バッチ名と目的

**S2 acceptance completion / contract gate**。新しい再評価機能やS3/S4を実装するバッチではない。既存のS2実装を分離環境でログインからブラウザー受入し、実装完了判定に必要な残件を閉じる。既存の画面遷移図・プロトタイプは参照専用とし、変更しない。

### 着手時に共有済みの状態

- `src/main`にはprompt CRUD、生成状態、教師対応、合成評価例、Servlet/JSP画面、共通rubric関連付けがある。V17 migrationは専用DBと8080開発DBで成功済み。
- `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test war --no-daemon --console=plain`は着手時点で成功（192件、成功127、失敗0、skip65）。その後の専用受入後クリーンビルドは194件、成功129、失敗0、skip65。
- `/teacher/prompt`未認証時302、`/teacher/account/login` HTTP 200、prompt CSS/JS HTTP 200。Null版ID例外とteacher navigation属性破損は修正済み。着手時は認証後未確認だったが、今回の専用DB/browser受入で認証後Step 1も確認済み。
- 8080共有開発DBはV17まで進んでいる。読み書きを伴うブラウザー受入で共有DBを使わず、別Compose project/schemaの合成教師・学校・クラス・課題だけを使う。共有DBの初期化・fixture投入・テストデータ削除は禁止。
- Docker daemonは利用可能。直近環境ではNode.js CLIが利用不可だった。ブラウザーtierは利用可能なVS Code統合ブラウザーで実施し、Node/Playwright利用を前提に固定しない。

### AutoPilot実行順

1. 着手時に`git status`と本計画・[roadmap](../implementation-roadmap.md)・[prompt/task state rules](../../state-rules/teacher/prompt-state-rules.md)・[implementation contract](../implementation-contract.md)を読み直し、未コミット変更を保持する。
2. 専用Compose projectと専用MySQL schemaを作り、V1〜V17を適用する。合成のログイン可能な教師アカウント（`PasswordHasher`でhashしたtest-only password）、学校/クラス権限、draft taskを同じ専用DBにseedする。平文credentialはソース、fixture、ログ、レポートに保存しない。shared 8080 DBへの接続先取り違えをassertでfail-closedにする。
3. ブラウザーで教師login → `/teacher/prompt`初期表示（課題未選択）→ 合成draft選択 → 共通rubric/版履歴表示 → prompt draft保存 → redirect後reloadでのDB再読込まで確認する。教師ナビの「課題編集」「プロンプト設計」リンク、`aria-current`、404資産の有無、error/empty state、少なくともdesktopと狭幅表示を確認する。
4. 同じ専用環境で他所有task/非教師の拒否と未認証遷移を確認する。保存前後でactive prompt IDと既存評価行が変わらないことをDBで検証する。全体再評価ボタンはdisabledのままとし、Gemini実APIは費用・資格情報利用確認がない限り呼ばない。
5. UI受入で発見した実装不具合だけを最小修正し、関連JUnit回帰テスト→全Gradle test/WAR→専用DB再受入を再実行する。受入記録を[error-report](../error-report.md)、本計画、[tasks-to-impl checkpoint](./checkpoints/teacher-prompt-ai/tasks-to-impl.yaml)へ反映する。
6. 2026-10-05の一問ずつの確認により、preview方式・対象集合・snapshot時点/一時保存方針/30分期限・部分失敗・初回適用・対象ゼロ件・通知方針を決定し、機能仕様第124版等へ反映した。残るpreview staging schema/cleanupとjob履歴の設計を確認し、すべての実装契約が整った後にT017/T018の実装計画を別途提示する。

### 実施結果

- 専用Compose project `ppe-s2-ui-acceptance-20261005`、schema `ppe_teacher_ui_acceptance_20261005`、port `18081`でV1〜V17を適用・validateし、共通rubricを登録した。共有8080 DBはこの受入では読み書きしていない。
- ログイン→初期表示→draft選択→rubric/履歴→下書き保存→redirect/reloadで再読込をブラウザー確認。入力欄readonly/保存ボタンdisabledの原因は、Servletが算出した編集可否をJSP request attributeへ設定していなかったこと。属性設定とJUnit条件回帰テストを追加した。
- DBでprompt `v1`/`draft`/row_version 1の保存を確認し、active promptはNULL、既存評価件数は0のまま。別教師所有taskは認証済み要求で404、匿名routeは302、ローカルCSS/JS 7資産は200。desktop 1280px / narrow 390pxで横overflowなし、再評価ボタンは無効のまま。
- クリーン実行 `DB_PORT=3308 DB_NAME=ppe_teacher_ui_acceptance_20261005 APP_PORT=18081 PROJECT_NAME=programming-process-evaluator docker compose -p ppe-s2-ui-acceptance-20261005 run --rm --no-deps app gradle clean test war --no-daemon --console=plain`は成功（JUnit 194件、成功129、失敗0、skip65、WAR成功）。学生拒否は既存control testで確認。Gemini実APIは未呼出し。
- 当時の結果は専用受入サービスに対するもので、共有8080への反映ではない。画面遷移図・prototypeのファイルは変更していない。T017/T018とjob/result historyはこの時点では実装未着手だった。後続の実装結果は末尾の最新記録を参照する。

## 全体再評価契約確認・計画更新記録（2026-10-05）

### バッチ境界

この記録は全体再評価契約の確認結果と、2026-10-05当時の計画-only作業の停止境界を残す。後続の実装結果は本書末尾の最新実施記録に記載する。

### 既存資料で固定されていること

- D-01〜D-03は既合意で変更しない。全課題で共通標準rubricを用い、課題内容の編集は課題編集画面だけで行う。
- IC-004/IC-007と[プロンプト状態ルール](../../state-rules/teacher/prompt-state-rules.md): 教師が対象者/評価差分を確認し再評価を確定したときだけactive版を切替え、job作成と一貫させる。失敗時は従来active版を維持する。評価は提出・rubric・prompt版を固定し、再評価は旧評価を上書きせず新しい履歴を追記する。通常評価の処理中は開始時の版を維持する。
- 対象は各対象生徒の最新提出とすることが状態ルールに記載されている。教師の閲覧/操作は対象学校に対する現在の権限を再検証する。
- 2026-10-05のユーザー確認により、全対象者をpreview表示し、最新提出のある対象者は確定前にGemini評価して個別予測を表示する。合成データのみを使うAI評価例生成/開発検証と、運用機能の予測評価は区別する。
- [DBテーブル定義](../../database-design/table-definitions.md)の`reevaluation_jobs`は件数・状態等のjob集約列のみ。固定提出の子行/スナップショット対応はない。`evaluation_input_snapshots`は`evaluation_request_id`に結び付き、job対象集合の固定を表現するものではない。
- 2026-10-05のユーザー確認により、再評価完了時に生徒への永続通知は送らず、教師にjob完了を表示し、生徒には次回評価画面を開いたとき最新評価を表示する。通知一覧や一時的feedbackは配送通知として扱わない。

### 確認済み全体再評価契約

1. **previewの予測方式（確認済み）**: 2026-10-05ユーザー回答により、確定前に各対象生徒の最新提出をGeminiで評価し、previewに表示する。教師が確定した場合は同じ応答を評価結果として再利用し、キャンセル時は評価行・active版・jobを変更しない（確定前呼出しの費用は発生済み）。個人直接識別子を除く既存AI評価経路を使う。契約確認段階では実APIを呼ばず、受入テストには合成データのみを使う。
2. **対象集合（確認済み）**: 2026-10-05ユーザー回答により、課題参加者全員をpreviewに表示し、最新提出がない参加者は「最新提出なし・対象外」と明示する。Gemini呼出し・再評価job・job対象件数は最新提出がある参加者に限定する。
3. **固定snapshot（業務契約・schema実装済み）**: preview生成時に対象者・最新提出・評価入力（prompt/rubric版を含む）を固定し、確定時に差異があればpreviewを無効化して再生成を求める。再生成はGemini/API費用を伴う。snapshotと応答はV18のpreview専用一時保存構造へ30分期限付きで保存し、通常のevaluation request/response/snapshotやevaluation行とは分離する。確定前に通常評価行を作らない。期限切れ後は確定不可としてcleanupする。対象全員分の予測が成功するまで確定不可とし、失敗者のみ再試行して成功済み応答を保持する。実装内容はPlan 5.3〜5.5および [テーブル定義](../../database-design/table-definitions.md) に記載する。
4. **初回適用（確認済み）**: 2026-10-05ユーザー回答により、`tasks.active_prompt_version_id`がNULLでも最新提出のある参加者がいる場合は通常と同じpreview→確認→active版設定/job作成フローとする。新評価予測を表示し、旧prompt/評価がない場合は比較不能と明示する。対象ゼロ件の場合は前項の無操作ルールに従う。
5. **対象ゼロ件（確認済み）**: 2026-10-05ユーザー回答により、Gemini呼出し・active版切替・評価履歴/再評価job作成を行わず「再評価対象なし」と明示する。
6. **完了通知（確認済み）**: 2026-10-05ユーザー回答により、生徒への永続通知を行わない。教師にはjob完了を表示し、生徒には次回評価画面を開いたとき最新評価を表示する。通知一覧は追加せず、一時的な共通feedbackを配送通知の代用にしない。

### 完了条件・停止条件

- previewに個人別の新評価予測を含め、確定前にGemini評価し、確定後に応答を再利用する業務方針はユーザー確認済み。schemaとworker設計は提案として記録し、承認済みmigrationや実装済み機能と誤認させない。
- 最新提出が受付処理中の場合は古いrevisionへfallbackせず確定を停止し、preview開始/確定時に参加者行をlockする実装とした。
- 当時はschema案と実装計画までが作業範囲だった。後続の実装は本書の最新実施記録およびチェックポイントへ反映済みであり、job進捗のHTTP/browser受入は引き続き未完了。

### AutoPilot停止条件

- テストが専用DB以外へ接続する、既存の認証情報を取得できず共有DBへログインしようとする、または共有fixtureを変更する可能性がある。
- 認証後の画面表示が失敗し、原因がコード/DB/権限のいずれかに特定できない。
- 実装時に提案schema/cleanupの方式が既存DB制約や評価transactionと両立しない場合、T017/T018を止め、具体的な根拠と差分を計画へ反映してから続ける。
- Gemini実API利用が必要になり、費用またはcredential利用の明示確認がない。

## Requirement Mapping

| REQ ID | 既存仕様・機能範囲 | Plan Items | Implementation Evidence |
|---|---|---|---|
| REQ-101 | 教師が自分の課題を選択しpromptを操作する（教師共通追記、課題選択） | 1.1, 2.1, 2.2, 5.2, 5.5 | TeacherPromptControl/Servlet、他所有・他ロール拒否テスト |
| REQ-102 | 課題別共通promptと追加評価指示を保存する（機能仕様「プロンプト設計」） | 2.1, 2.2, 3.2 | TeacherPromptVersion/Dao、DB再読込 |
| REQ-103 | 課題情報とpromptから揺らぎ項目を生成・検証する（AI設計§3.2） | 3.1 | TeacherPromptAiClient、schema/失敗/再試行テスト |
| REQ-104 | 揺らぎごとの教師方針と追加評価指示を統合する（機能仕様「揺らぎ項目への対応記述」） | 3.2 | TeacherPromptControl、item resolution state、統合ルール |
| REQ-105 | 保存前に合成例から評価結果・variance alertを確認する（AI設計§3.3） | 4.1, 4.2 | TeacherPromptExampleFactory/AI client、evaluation_examples、DB再読込 |
| REQ-106 | prompt版・作成者/更新者・監査履歴と不変性を保つ（IC-007、教師共通追記） | 2.1, 2.2, 3.2, 4.2 | prompt version履歴、audit_logs、旧版read-only/rollback tests |
| REQ-107 | プロトタイプ外観・表示/操作を維持しshared feedbackを利用する | 2.2, 4.2, 5.2, 5.5 | prompt.jsp/CSS/JS、ブラウザー受入、320px/keyboard確認 |
| REQ-108 | 共通標準rubricとactive promptが評価queueへ接続され、明示確定後の全体再評価で履歴を保持する（IC-004/007） | 1.1, 2.1, 5.1, 5.3, 5.4, 5.5 | rubric関連付け、EvaluationQueueDao接続、preview staging、active切替/job target materialization/追記評価/履歴不変テスト |
| REQ-109 | 権限、状態、secret、PII、外部API失敗、DB transactionを安全に扱う（AGENTS、AI連携設計） | 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 4.1, 5.1, 5.2, 5.3, 5.4, 5.5 | auth/CSRF/transaction/API/schema tests、専用DB・HTTP・browser evidence |

要件9件を計画項目へ対応付けた。上記の全taskは計画項目を参照し、S2以外の公開・配信機能は要求範囲に含めない。D-01〜D-03および全体再評価previewの業務契約はユーザー確認済み。V18 schema、cleanup/state遷移は実装済みであり、画面通知一覧は追加しない。

## T017/T018 実装・検証記録（2026-10-05）

- T017 preview staging、対象/入力fingerprint、mock可能な予測worker、失敗targetのみのretry、submitted受付中guard、明示確定時のactive版/job/audit transactionを実装した。
- T018 保存済みprovider応答から通常の評価履歴を追加するworker、DB claim/progress、再評価状態のprompt画面表示とqueued/in_progress時のpollingを実装した。通常の評価行・旧評価は上書きしない。
- 専用schemaでV1〜V18のmigration/validateに成功。`EvaluationDatabaseTest`は6 passed / 0 failed / 1 skipped（billable Gemini live smoke test）、`TeacherTaskDatabaseTest`は12 passed / 0 failed / 0 skipped。
- `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test war --no-daemon --console=plain`成功。JUnit 198件、131 passed / 0 failed / 67 skipped、WAR生成成功。既存Gradle deprecation warningsあり。
- 初回のcompile/test/DB testで見つかったメソッド配置、test assertion、task-list SQL aliasの問題を修正し、上記の最終結果で再検証した。詳細は[error report](../error-report.md)に記録する。
- job status表示の認証HTTP・browser受入、課題/参加者データを使った画面全体のGemini予測経路、Gemini実API、共有8080への反映は未確認。画面遷移図と`screen-flow-diagram`は変更していない。

### 当時の次作業（後続記録で完了）

この記録時点ではT030/T031の認証・所有境界testと隔離browser受入が次の作業だった。後続の結果は下記「T031 合成provider preview・確定受入記録」を参照する。

## T031 合成provider preview・確定受入記録（2026-10-05）

- `ReevaluationPreviewWorker`の必須provider注入constructorを公開し、DB統合テストから合成providerを渡してpreview予測と確定処理を検証した。JSP ELがrecord accessorを解決できないpreview/targetでJavaBean getterとintrospection回帰testを追加した。
- 専用V18 schemaに保存したpreviewを隔離Tomcatの認証browserで表示し、対象1人、思考4/5・態度3/5と合成理由を確認した。shared feedbackの確認modalから確定後、redirect URLに`jobId=9`が保持され、画面表示は完了、成功1/1、失敗0。workerは保存済み合成応答をmaterializeし、Gemini providerを再呼出ししていない。
- `docker exec -w /programming-process-evaluator ... gradle test war --no-daemon --console=plain`成功。Java 21を含む隔離container内で実行し、JUnit 204件、149 passed / 0 failed / 55 skipped、WAR生成成功。外側の作業機で直接Gradleを起動した試行はJava 21 toolchain不在で失敗した。Tomcat起動中の再試行は同一project `.gradle/buildOutputCleanup` lock timeoutとなったため、一時Tomcatを停止して再実行し成功した。
- 起動直後のbrowserアクセスはdeployment完了前にHTTP 404となったが、Tomcat ready後のlogin/prompt routeを再確認してHTTP 200/認証redirectとなり、受入を継続した。Gemini実API、実提出、個人情報、共有8080/DBは使用せず、画面遷移図/prototypeも変更していない。
- 専用app/DB containerとbrowser専用networkを削除し、browserを`about:blank`へ戻した。詳細な失敗/再試行コマンドと結果は[error report](../error-report.md)に記録する。

### 次に行うこと

T014は完了。次はS3「公開・改訂・削除/復元」の着手条件と既存状態ルール・画面導線を照合し、S4配信より先にS3の小さな機能スライスを定める。画面遷移図/prototypeは必要性が明確でない限り変更せず、共有8080/DBや実データに触れない。

**推奨AIモード: 対話型** — 公開後の改訂・削除/復元の権限と状態遷移は仕様上の判断を含むため、実装前に範囲と受入条件を確認する。

## AI稼働確認の現状と延期作業 (2026-10-06)

ユーザー判断により、プロンプト設計機能のGemini実APIを使う確認は後続へ延期する。この節を現在のAI稼働確認状態の正本とし、上記の当時の次作業記録は後続のS3実装・受入記録で更新された履歴として扱う。再開指示があるまではGemini実APIを呼び出さず、実提出/コードログを外部へ送信しない。

### 確認済み

- プロンプトの作成・下書き保存・版管理、課題別評価例の保存/再表示、適用版切替、再評価preview/job/履歴表示を実装済み。
- prompt saveの認証ブラウザー受入を専用DBで実施。保存後reloadでのDB再読込、active版が変化しないこと、既存評価行に影響しないこと、別教師所有課題の拒否を確認した。
- 全体再評価preview〜確定の経路は合成providerと合成fixtureで専用V18 DB/隔離Tomcatの認証ブラウザー受入を実施。preview結果・明示confirm・jobへのredirect・合成応答のmaterializeを確認し、workerがGemini providerを再呼出ししないことを確認した。
- prompt/job status・job履歴/対象者別結果の専用DB testと認証ブラウザー受入、および計画記録上の全体Gradle test/WARは成功。最新の根拠は本計画のT014実施記録と[実装・検証エラーレポート](../error-report.md)。

### 未確認・後続作業

1. 実行環境のGemini provider設定、資格情報の安全な注入、利用可能なmodel/API、費用上限と実行時間上限を読み取り専用で確認する。秘密値を文書・ログへ出力しない。
2. ユーザーの別途承認を得た後、実提出を使わず、合成課題・合成提出・合成コードログだけでGemini実APIへの最小疎通を行い、要求/応答schema、匿名化済み入力、timeout・rate limit・provider error時の明示的失敗を確認する。
3. 実APIを使う合成データの縦断受入を行う。AI評価例生成、対象ゼロ/複数、部分失敗と再試行、previewの保存/期限/認可、confirm後のjob・評価履歴保存、重複confirm、旧評価保持、画面再読込を確認し、実API結果とmock/provider-test結果を区別して記録する。
4. 実生徒データ/実提出を利用する本番相当試験は、この合成受入とは別ゲートとする。ユーザーの明示承認、同意・匿名化・データ送信先・費用を作業直前に確認する。承認なしに実データをAI providerへ送らない。
5. 結果に応じて本計画、[工程8評価計画](./student-evaluation-survey.md)、[実装ロードマップ](../implementation-roadmap.md)、[エラーレポート](../error-report.md)を更新する。未実施・失敗を完了扱いにしない。

延期は実装済みのS2機能や合成provider受入を取り消すものではないが、「Gemini実APIが利用可能」「本番運用可能」「実データで評価確認済み」とは扱わない。再開時期は未定。工程10の当面の次作業はロードマップに記載するS4配信の要件整理であり、AI確認はユーザーが再開を指示するまで着手しない。

**次に行うこと:** AI稼働確認は保留のまま、S4配信の既存仕様・状態ルール・DB定義を照合し、実装前の対象範囲と受入条件を整理する。

**推奨AIモード: 対話型** — AI実API利用・費用・実データ送信の判断が必要な再開時は、実行前にユーザーと確認する。
