# 教師プロンプト設計・AI生成 S2 実装計画

作成日: 2026-10-04 JST。更新: 2026-10-05 JST。状態: **合意済み範囲の実装中。課題プロンプト/標準rubric/合成AI生成/教師画面を実装し、JavaテストとWAR生成は成功。専用DB/HTTP/JSPブラウザー受入は未実施。評価差分プレビュー・通知・再評価対象固定が未定義のため全体再評価は保留。**

本計画は工程10 S2のみを扱う。教師課題下書きS1は[教師課題下書き計画](./teacher-task-draft.md)に記録する。S3（公開・改訂・削除/復元）、S4（演習コード配信）、工程11（教師の進捗・提出・評価・CSV）は対象外。

## 目的と範囲

- 権限を持つ教師が自分の課題を選び、課題に紐づくプロンプト版を編集・保存し、AIによる揺らぎ項目と評価例を確認して、DBから再表示できるようにする。
- 揺らぎ項目、生成状態、評価例、プロンプト版をサーバー側で検証し、DBへ保存する。ブラウザー内の固定サンプルや`localStorage`を業務データの正本にしない。
- 全課題はDBに登録済みの共通標準ルーブリックを評価用ルーブリックとして使う。課題ごとの選択・作成・編集は行わず、同じactive標準版を課題の`rubric_id`へ関連付ける。
- 課題内容の編集は課題編集画面を正本とする。プロンプト設計画面では課題情報を参照・AI入力へ使用し、task内容は更新しない。
- 「課題プロンプトを保存」は未適用の案として保存し、現在適用版は切り替えない。「設定して再評価する」の明示確認で対象生徒・評価差分を確認した後、新版を現在適用版へ切り替え、全生徒の最新提出を再評価する。旧評価は上書きしない。
- S2は課題選択、プロンプト下書き、揺らぎ項目生成、教師対応記述、課題別追加指示、評価例生成・保存・履歴表示、適用版切替、全体再評価ジョブ/進捗を含む。課題の公開・予約公開はS3、演習コード配信はS4とする。
- AI評価例の生成には合成データのみを使用する。実在する生徒の提出・コードログ・研究データをS2の外部API検証へ送信しない。Gemini実APIのスモークテストは、合成入力と費用・資格情報の利用確認が揃った場合に限る。
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
| 現在適用版・全体再評価 | ユーザー方針合意 D-02、既存ルールとの統合が必要 | 2026-10-04ユーザー回答、IC-007、プロンプト状態ルール、課題状態ルール | 保存は案、「設定して再評価する」の確認後に同一課題のactive版を切替え全生徒を再評価する。課題状態ルールの「開始後はpromptを含む影響項目変更禁止」は改版113で限定例外を追加 | 切替とjob作成を整合させ、全員の最新提出を新しい評価行で再評価。旧評価は不変。通知経路と評価差分プレビュー算出は既存設計との照合事項 |
| プロンプト内の課題内容編集 | 合意済み D-03 | 2026-10-04ユーザー回答、機能仕様「共通プロンプト」、プロトタイプStep 1、S1課題編集 | 課題編集画面が正本。プロンプト画面では参照・AI入力のみ | prompt routeはtask内容を更新しない。仕様は改版113で記録 |
| 生成例の入力データ | 既定方針 | AI設計§3.3、AGENTS外部API条件 | AI設計は匿名化提出サンプルを記載する | S2の試験と評価例は完全合成データのみ。実在データ送信を追加する場合は別途確認・合意する |
| 画面と監査 | 要修正 | 教師共通追記、feedbackガイド | 固定教師ID、sample履歴、ブラウザー保存がある | `AuthenticatedUser`、所有範囲、`audit_logs`とshared feedbackに接続する |

### 合意済みの決定事項

| ID | 決定 | 反映先 |
|---|---|---|
| D-01 | 全課題にDB登録済みの共通標準ルーブリックを使う。課題別の選択・複製・編集は設けない | 機能仕様改版113、Plan 2.1、課題作成時の関連付け |
| D-02 | prompt保存は案とし、「設定して再評価する」の確認後にactive版を切替え、全生徒の最新提出を再評価する。旧評価は新規評価行を追加して保持 | 機能仕様改版113、課題/プロンプト状態ルール、Plan 5.1 |
| D-03 | 課題内容の正本は課題編集画面。プロンプト設計では参照・AI入力のみ | 機能仕様改版113、Plan 2.2 |

### S2着手前に残る実装契約照合

- プロンプト状態ルールの「評価差分プレビュー」は算出方法・データ源が未定義。既存の評価例を使う範囲を照合し、全生徒分の事前AI評価など外部呼出しを暗黙に追加しない。
- 機能仕様は再評価完了を各生徒へ通知するとしているが、改版109で通知一覧/サンプルを導入しない方針が決まり、現行schemaにも生徒向け通知イベント/配信先がない。既存の明示済み通知機構が見つからない場合、画面トーストを生徒向け通知の代用にせず、通知チャネルを確認する。
- これは確定済み3方針を変更する質問ではなく、実装機構の照合事項である。解決するまで全体再評価完了や生徒通知済みを表示しない。

## ユースケースとデータ契約

| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 課題選択・現在版表示 | `task-management`利用権限を持つ教師。自分が作成した未削除課題 | `taskId` | 課題、作成者、課題入力、適用ルーブリック、現在適用版、版履歴、権限 | 必要時、rubric未設定の対象draftに共通標準版を関連付け | なし | 不存在/別所有/削除済みは同じnot-found応答。権限拒否403、標準rubric不在/不整合は明示エラー |
| プロンプト下書き保存 | 同上 | task ID、期待version、モデルID、共通プロンプト、追加指示、CSRF | 課題と認可、当該課題の各prompt versionと生成済み子データ | `prompt_versions`、必要時`audit_logs` | 新規draft作成または未適用draftの同一版更新。適用済み/履歴版は不変 | 成功後DB再読込。入力不正400、version競合409、権限拒否403、保存障害503 |
| 揺らぎ項目生成 | 同上 | prompt version ID、確定済みプロンプト、課題情報 | 課題・プロンプト版・既存生成状態 | 開始/終了状態、揺らぎ項目、監査記録 | `not_generated/failed → in_progress → completed/failed` | 生成中/結果/再試行可能エラーを明示。API失敗を成功扱いしない |
| 揺らぎへの対応記述保存 | 同上 | 項目ID、方針、resolution state、順序、追加指示 | 課題・prompt版・項目 | `prompt_fluctuation_items`、prompt下書き、監査記録 | `pending → resolved/not_applicable`。未確定は未解決として残す | 保存後再読込、未解決数を明示 |
| 評価例の生成/保存 | 同上 | 確定ルール、完全合成の入力サンプル | 課題、共通標準rubric、選択prompt版、揺らぎ/解決 | `evaluation_examples`、生成状態、監査記録 | `not_generated/failed → in_progress → completed/failed` | JSON schema検証後のみ表示/保存。失敗は明示し再試行可能 |
| 再評価対象プレビュー | 同上。許可学校権限を再検証 | task ID、prompt version ID | 評価条件、対象クラス/参加者、各生徒の最新提出、既存評価、評価例 | なし | なし | 対象生徒/提出/現評価と新ルール例を表示。プレビューだけでactive版・評価を変更しない |
| 全体再評価の確定 | 同上。課題の全対象学校権限 | task ID、prompt version ID、期待version、明示確認、CSRF | 対象提出スナップショット、権限、課題version、共通rubric、prompt/評価例完了状態 | active prompt切替、`reevaluation_jobs`、対象提出固定情報、監査を一貫して保存 | 新版をactiveにし、jobを`queued`へ | 確定後のみ進捗表示。競合/権限/準備不足では切替せず既存activeと評価を維持 |
| 再評価処理/完了 | job作成時に対象として固定された生徒提出。教師は許可範囲内で状態参照 | job ID | 固定対象、提出/ログ snapshot、rubric、prompt、現評価 | 対象ごとに新しい`evaluations`行を追加し、job状態/件数/進捗を更新。通知結果も記録 | `queued → in_progress → completed/failed` | 部分失敗を隠さず表示。完了通知は合意済みチャネルがある場合のみ送信 |
| 履歴表示 | 所有課題の教師 | task ID/version ID/job ID | 課題の版、生成状態、再評価job/評価行、作成者、audit | なし | なし | 過去版はread-only。旧評価と新評価を別履歴として識別。未登録/部分失敗を明示 |

AIリクエストはDBロックを保持したまま送信しない。最初に状態を`in_progress`へコミットし、許可されたプロンプト/必要最小限の課題情報と合成サンプルのみを外部送信し、応答のスキーマ・件数・識別子を検証してから結果と完了状態を保存する。失敗時は生成状態を`failed`へ戻し、監査情報と再試行可能なエラーを記録する。APIキー、提出コード、個人識別情報、例外に含まれる秘密情報は画面・ログに含めない。

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
- **Description**: active共通rubric、taskのactive prompt version、2次元rubric、configured/versioned prompt、揺らぎ/評価例の`completed`という評価queue条件を維持する。教師が対象と差分プレビュー後に明示確定した場合のみ、同じtransactionでprompt active版切替・再評価job作成・対象生徒ごとの最新提出固定を行う。現schemaにはjob対象固定テーブルがないため、migrationで対象snapshotを永続化する契約が必要か検証する。job workerは固定提出を評価し、新しい`evaluations`行と進捗/部分失敗を記録し、旧評価/提出を変更しない。通常評価は開始時点の版を維持する。既存通知チャネルが確認できる場合のみ完了通知し、通知経路がなければ通知済み/完了を偽装せず該当処理をblockedとする。queue/公開境界と準備不足時のactive版不変も検証する。

##### Plan 5.2: DB/HTTP/ブラウザーを通した正常系・拒否系を受け入れる

- **Requirements**: REQ-101〜109
- **Design inputs**: S1受入チェック、`TeacherPermissionDao`、shared feedback、AI integration design。
- **Description**: 合成の教師/学校/課題/提出だけを持つ専用DBでmigration/統合テスト、全Gradleテスト、WAR、HTTP認証/認可、prompt保存後再読込、AI応答検証、履歴/監査rollback、失敗/再試行、再評価jobの対象固定・二重起動防止・部分失敗/進捗を検証する。ブラウザーでプロトタイプとの外観、正常/エラー/空状態、明示確認前にactive版が変わらないこと、320px以上のレスポンシブ、アクセシビリティを確認する。合成payloadのGemini実API呼出しは明示的な費用/資格情報確認後だけ実施し、実行結果/未確認事項を計画とerror-reportへ記録する。

## Task Breakdown

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
- [ ] T014 [Plan:4.2,5.1] `src/main/java/entity/TeacherPromptPage.java`、`src/main/java/dao/TeacherPromptDao.java`で評価例・prompt版履歴・再評価job/結果・監査表示に必要な読込DTOとread-only過去版参照を実装する。

### Phase 5: UI統合と受入

- [x] T015 [Plan:4.2] `src/main/webapp/WEB-INF/teacher/prompt/prompt.jsp`、`src/main/webapp/css/teacher/prompt/prompt.css`、`src/main/webapp/js/teacher/prompt/prompt.js`を追加し、プロトタイプのStep 1〜3/履歴の外観を保ってDB/API接続する。固定サンプル、`localStorage`保存、成功形のタイマー動作は除去する。
- [x] T016 [Plan:4.2,5.1] `src/main/java/servlet/teacher/TeacherPromptServlet.java`と`src/main/webapp/WEB-INF/template/page-start.jspf`、`page-end.jspf`へページ表示・route・shared feedbackを統合し、対象/評価差分preview、明示確認、job progress/historyを接続する。準備不足またはT001で未解決となった操作は成功形にせず明示的に無効化する。
- [ ] T017 [Plan:5.1] `src/main/java/control/teacher/TeacherPromptControl.java`、`src/main/java/dao/TeacherPromptDao.java`、新規`src/main/java/dao/ReevaluationJobDao.java`へ、対象/評価差分のプレビューと明示確認を経たactive版切替・`reevaluation_jobs`作成・対象提出snapshot固定を原子的に実装する。競合、準備不足、権限変更時はactive版を切り替えない。初回適用/対象ゼロ件時の扱いはT001で確認した契約に従い、未定義なら独自に決めずblockedにする。
- [ ] T018 [Plan:5.1] 新規`src/main/java/control/evaluation/ReevaluationWorker.java`と`src/main/java/dao/ReevaluationJobDao.java`へ、固定済み最新提出の再評価、新規評価行の追記、進捗・部分失敗・再試行状態の記録を実装する。旧評価・提出を変更せず、通常評価は開始時のprompt/rubric版を維持する。完了通知はT001で確認した既存チャネルに限る。
- [ ] T019 [Plan:2.1,3.1,3.2,4.1,4.2,5.1] `src/test/java/control/teacher/TeacherPromptControlTest.java`、`src/test/java/dao/TeacherPromptDaoTest.java`、`src/test/java/servlet/teacher/TeacherPromptServletTest.java`、専用DB統合テストへ所有境界・rubric割当・競合・状態遷移・transaction rollback・schema不正/再試行・合成fixture・active切替・対象snapshot・旧評価不変・job部分失敗のテストを追加する。
- [ ] T020 [Plan:5.2] Gradle全テスト/WAR、専用MySQL migration/DB受入、認証HTTP、合成データブラウザー検証を実施し、`docs/system-configuration/error-report.md`と本計画へ正確なコマンド/結果/未確認事項を記録する。

## Project Structure

```text
src/main/java/
  control/teacher/
    TeacherPromptControl.java
    TeacherPromptAiClient.java
    TeacherPromptExampleFactory.java
  control/evaluation/
    ReevaluationWorker.java
  dao/
    TeacherPromptDao.java
    ReevaluationJobDao.java
  entity/
    TeacherPromptVersion.java
    TeacherPromptPage.java
src/main/webapp/
  WEB-INF/teacher/prompt/prompt.jsp
  css/teacher/prompt/prompt.css
  js/teacher/prompt/prompt.js
src/main/resources/db/migration/
  V17__support_teacher_prompt_workflow.sql  # schema差分が立証された場合のみ
src/test/java/
  control/teacher/TeacherPromptControlTest.java
  dao/TeacherPromptDaoTest.java
  servlet/teacher/TeacherPromptServletTest.java
```

上記は計画時点の候補名。既存の配置・命名に合わない場合は同じ責務を保って実装前に調整する。実装時のmigration番号は最新の採番状況を確認する。

## 検証戦略・完了条件

- **アプリ種別**: server-rendered HTML + Servlet + 外部AI。
- **重要journey**: (1) 教師が自分のdraft taskを選び共通標準rubricとprompt draftを確認/保存/再読込、(2) 揺らぎ項目を生成して状態/項目/失敗を確認、(3) 教師対応と追加指示を保存し再読込、(4) 合成サンプルから評価例を生成/検証/保存して履歴表示、(5) 対象生徒・差分プレビューと明示確認後にのみactive版を切り替え、固定提出を全体再評価して履歴/進捗を確認、(6) 他教師/学生/admin/別task/旧version/不正rubricの拒否。
- **Primary stack**: JUnit/Gradle、専用MySQL、Tomcat HTTP、ブラウザー。Docker/MySQLが使用できない場合はDB受入をPASSとせず未確認として報告する。
- **Test data**: 合成教師・学校・クラス・課題・提出サンプルのみ。各受入は独立した専用schemaを使用し、共有開発DBの初期化・migrationを禁止。
- **API**: 単体テストはHTTP mock。実APIスモークは合成ペイロードだけ、設定済みsecretの値を一切出力せず、ユーザーの費用・認証設定確認後に限定実行する。
- **Acceptance**: 状態・項目・評価例がDBへ保存され再読込で一致する。新規課題と既存draftのrubricは同じDB登録済み標準版となる。prompt保存だけではactive版/評価が変わらず、明示確認後に対象提出が固定され、jobごとの新規評価履歴・進捗・部分失敗が追跡できる。旧評価/提出を変更しない。別所有/旧版/無効権限の操作は拒否。AI schema不正/API障害が完了状態にならない。auditと業務保存の一貫性を確認し、prototypeの画面要素・テキスト・導線を不必要に変えない。
- **未実施時の扱い**: 実API未確認ならmock/専用DBでの結果と区別し、AI接続済み・評価準備完了とは報告しない。通知一覧を新設しない方針のため、プレビュー算出方法/通知チャネルの既存契約が見つからなければ該当機能はblockedとし、未決の契約を実装したとみなさない。S2実装済みをもってqueue readinessまたはS3公開可能とみなさない。
- **開始条件**: D-01〜D-03はユーザー確認済み。AutoPilot開始後、専用DBを用意し、T001でプレビュー/通知/対象固定/初回適用の既存契約を確認する。Gemini実APIスモークのみ別途費用・secret使用許可を要する。

## Requirement Mapping

| REQ ID | 既存仕様・機能範囲 | Plan Items | Implementation Evidence |
|---|---|---|---|
| REQ-101 | 教師が自分の課題を選択しpromptを操作する（教師共通追記、課題選択） | 1.1, 2.1, 2.2, 5.2 | TeacherPromptControl/Servlet、他所有・他ロール拒否テスト |
| REQ-102 | 課題別共通promptと追加評価指示を保存する（機能仕様「プロンプト設計」） | 2.1, 2.2, 3.2 | TeacherPromptVersion/Dao、DB再読込 |
| REQ-103 | 課題情報とpromptから揺らぎ項目を生成・検証する（AI設計§3.2） | 3.1 | TeacherPromptAiClient、schema/失敗/再試行テスト |
| REQ-104 | 揺らぎごとの教師方針と追加評価指示を統合する（機能仕様「揺らぎ項目への対応記述」） | 3.2 | TeacherPromptControl、item resolution state、統合ルール |
| REQ-105 | 保存前に合成例から評価結果・variance alertを確認する（AI設計§3.3） | 4.1, 4.2 | TeacherPromptExampleFactory/AI client、evaluation_examples、DB再読込 |
| REQ-106 | prompt版・作成者/更新者・監査履歴と不変性を保つ（IC-007、教師共通追記） | 2.1, 2.2, 3.2, 4.2 | prompt version履歴、audit_logs、旧版read-only/rollback tests |
| REQ-107 | プロトタイプ外観・表示/操作を維持しshared feedbackを利用する | 2.2, 4.2, 5.2 | prompt.jsp/CSS/JS、ブラウザー受入、320px/keyboard確認 |
| REQ-108 | 共通標準rubricとactive promptが評価queueへ接続され、明示確定後の全体再評価で履歴を保持する（IC-004/007） | 1.1, 2.1, 5.1 | rubric関連付け、EvaluationQueueDao接続、active切替/job対象固定/追記評価/履歴不変テスト |
| REQ-109 | 権限、状態、secret、PII、外部API失敗、DB transactionを安全に扱う（AGENTS、AI連携設計） | 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 4.1, 5.1, 5.2 | auth/CSRF/transaction/API/schema tests、専用DB・error report |

要件9件を計画項目へ対応付けた。上記の全taskは計画項目を参照し、S2以外の公開・配信機能は要求範囲に含めない。D-01〜D-03はユーザー確認済み。通知チャネルと評価差分プレビューの実装契約をAutoPilot着手時に確認し、既存設計がなければ該当機能を停止して追加確認する。
