# 生徒の評価・コードログ・アンケート実装計画

現在の状態（2026-10-04整理）: **工程8の主要実装と標準ルーブリック閲覧は実装済み、全体受入は保留**。アンケートGET503・空コードログの既知バグは[教師工程前デバッグ計画](./pre-teacher-debugging.md)で解消・検証・反映済み。専用合成DBの認証HTTP・ブラウザーによる確認と、正式設定・ユーザー手動E2Eを区別する。後者は教師画面完成後に再開する。次の着手対象は[実装ロードマップ](../implementation-roadmap.md)の工程10であり、以下の古い次工程指示を再実施しない。

## 目的と範囲

- 利用者に提供する動作: 生徒が提出版ごとの評価状態・評価結果・根拠ログを本人の学習履歴から確認し、対象かつ同意済みの場合に限り、システム評価を参照しながらアンケートへ回答できる。
- 今回実装する範囲: ロードマップ工程8。評価状態と版履歴の取得、本人限定の評価・コードログ画面、AI評価リクエストの固定入力・匿名化・検証・失敗記録、課題別アンケート（その課題の完了済み評価を提示）の対象判定・回答保存・再読込。
- 今回対象外: 教師向け課題・プロンプト・ルーブリック作成画面、教師の評価確認・CSV出力、授業演習。必要なルーブリック・プロンプト設定が存在しない提出に対して、評価を捏造して補うこともしない。
- システム対象外（2026-10-04ユーザー指示）: 全体アンケートは本システム内では実装しない。課題別アンケートのみを対象とし、全体アンケートを後続タスク・残作業として扱わない。仕様は[機能仕様書第105版](../../function-specification.md)を参照。

## 必読資料

- 機能仕様書: [function-specification.md](../../function-specification.md) の生徒ホーム、評価表示、コードログ、アンケート機能
- 画面遷移図 / プロトタイプ: [evaluation.html](../../../screen-flow-diagram/webapp/WEB-INF/student/evaluation/evaluation.html)、[log.html](../../../screen-flow-diagram/webapp/WEB-INF/student/evaluation/log.html)、[survey.html](../../../screen-flow-diagram/webapp/WEB-INF/student/survey/survey.html)、各画面のCSS・JavaScript、[screen-flow-diagram.md](../../screen-flow-diagram.md)
- 状態ルール: [evaluation-state-rules.md](../../state-rules/student/evaluation-state-rules.md)、[survey-state-rules.md](../../state-rules/student/survey-state-rules.md)、[editor-state-rules.md](../../state-rules/student/editor-state-rules.md)
- DB 設計・テーブル定義: [table-definitions.md](../../database-design/table-definitions.md)、[V1 schema](../../../src/main/resources/db/migration/V1__create_initial_schema.sql) と既存マイグレーション
- クラス図 / AI連携設計 / その他: [ai-api-integration-design.md](../../ai-api-integration-design.md)、[evaluation-rubric.puml](../../class-diagram/02-evaluation-rubric.puml)、[ai-consent-log.puml](../../class-diagram/04-ai-consent-log.puml)、[implementation-contract.md](../implementation-contract.md) IC-004、IC-006、IC-008

## 前提・未決事項
- 2026-10-04ユーザー確認: 評価・アンケート全体の実設定・ユーザー手動確認は教師向け画面完成後、必要な課題/プロンプト/アンケート設定を行える段階で再開する。設定が整った時点で提出→評価表示→アンケート導線・下書き/送信/再読込の確認をリマインドする。
- 現在の検証範囲: 合成データによる実API→DB保存・再読込と、既知バグ修正後の専用合成DB・認証HTTP・実画面の表示/権限境界/回答保存は確認済み。これだけでT004〜T006全体・工程8の完了条件を満たしたとは扱わず、正式設定とユーザー手動E2Eは保留する。

- 合意済み前提: `evaluations` が提出版ごとの評価状態の正本であり、`task_participations.evaluation_status` は同じトランザクション内で同期する表示用状態。AI評価とコードログ収集は研究同意状態を問わず実行する。研究識別子のみをAIへ送信し、同意状態はリクエスト時点の記録に使う。アンケート対象・アクセス可否は同意状態と分離して判定する。
- 実装契約上の関連 ID: IC-004（評価状態と履歴）、IC-006（同意・アンケート履歴）、IC-008（AI送信前の匿名化と研究ID）
- 現行スキーマの前提: `tasks.rubric_id` と `tasks.active_prompt_version_id` が設定された課題だけを評価対象にする。評価開始時に `submission_id`、`rubric_id`、`prompt_version_id` と入力スナップショットを固定する。これらが未設定の提出には評価結果を生成せず、準備不足が明示される状態を返す。
- ローカル確認用のじゃんけん課題には評価用ルーブリック・プロンプトがなく、AI API キーも開発設定に含まれていない。これらは仮の結果やキーで補わず、外部APIなしのテストと未設定時の明示エラーを検証する。
- API の認証情報は環境変数からのみ取得し、ログ・画面・ソースへ露出しない。モデル識別子を設定化し、未設定時は評価処理を成功に見せない。
- 実装契約・現行DB定義に別途確認が必要な破壊的変更が見つかった場合は、当該箇所を実装前に分離して協議する。既存のスキーマ定義内で完結する範囲を優先する。

## ユースケースとデータ契約

| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 評価画面を開く | ログイン中の生徒本人 | 提出IDまたは参加ID | 本人の提出版、当該提出版の評価履歴、課題・割当状態 | なし | なし | 最新提出版の評価状態を主表示。過去評価は提出版を明示して併記 |
| AI評価を開始 / 再試行 | システム。対象は本人の固定済み提出版 | 評価種別、評価ID | 提出・課題・有効なルーブリック版・プロンプト版・コードログ・実行・テスト結果・同意状態・研究ID | 評価、要求、固定入力、応答、スコア、理由、根拠、表示用状態 | `not_started` / 失敗から新しい評価履歴を作り `in_progress`、成功で `completed`、失敗で `failed` | 同じ提出版の履歴を上書きしない。失敗は再試行可能なエラーとして表示 |
| コードログを閲覧 | ログイン中の生徒本人 | 提出ID、ログID | 当該提出に紐づく本人のコードログ・コード実行・入出力検証・評価根拠 | なし | なし | 時系列、選択時点、前後差分を表示。履歴なしは明示的な空状態 |
| アンケートを開く | ログイン中の同意済み本人 | 対象タスク・提出・評価ID | 同意状態、active task survey / questions / options、本人の回答履歴、完了したシステム評価 | 未回答時のGETではDB状態を変更しない。保存済み回答中なら下書きを復元し、同じ評価への提出済み回答があれば読み取り専用で表示 | `not_answered → in_progress → submitted` | 同意なし・撤回・非対象・評価未完了・アンケート未設定を区別して案内。全体surveyは対象外 |
| アンケートを提出 | ログイン中の同意済み本人 | 必須回答、対象評価、CSRF | 同一 `(student_user_id, survey_id, evaluation_id)` の回答状態、active設問・必須条件・同意状態 | 初回下書き保存で回答行を作成し、同じ対象の既存行を一意に保持。別評価IDなら新しい履歴を作成 | `in_progress → submitted` | 必須・値範囲をサーバーで検証。同じ対象への重複送信は既存結果を返し、新履歴を作らない。提出済み内容は再編集不可 |

## 実装タスク

### 生徒共通ルーブリック閲覧追加（第106版、2026-10-04承認）

- Plan R1 / REQ-RUB-001: 指定0805資料の2次元/6観点/30説明を既存DBへ不変版として登録する。既存課題/評価の設定は変更しない。
- Plan R2 / REQ-RUB-002: 全生徒画面の共通ヘッダーから同一標準版をDB取得し、prototypeの全画面modal/2表/降順levelを再利用する。標準版であることを明示する。
- Plan R3 / REQ-RUB-003: 未登録/不完全/取得失敗をshared feedbackで通知し、認証/role/初回password制約と未保存editorを維持する。
- [x] T007 [Plan:R1 / REQ-RUB-001] Markdown取込と既存4テーブルへの登録/再登録照合、DB取得モデル/DAO/control/GETを実装・試験する。
- [x] T008 [Plan:R2,R3 / REQ-RUB-002,003] 共通navigation/modal/JSを接続し、標準版説明だけprototypeにも反映する。
- [x] T009 [Plan:R1,R2,R3 / REQ-RUB-001,002,003] 専用環境で30説明一致・登録冪等/失敗・認証・3幅・dirty/focus/取消/取得障害を確認する。
- [x] T010 [Plan:R1,R2,R3 / REQ-RUB-001,002,003] ローカル標準版登録・配信照合、清掃と結果記録を完了する。教師工程や評価再実行へは進まない。
- 依存: T007→T008→T009→T010。既存原則/設計はAGENTS.md、既存DB定義とクラス図、feedbackガイドを利用する。新DBテーブル/依存は追加しない。
- Requirement Mapping: REQ-RUB-001→R1→T007/T009/T010、REQ-RUB-002→R2→T008/T009/T010、REQ-RUB-003→R3→T008/T009/T010。

#### T007〜T010 検証結果（2026-10-04）

- システム標準として作成者NULLを許容する方針を追加確認し、V15を適用。実アカウントの作成・ログイン・変更は行っていない。登録手順は[DB定義](../../database-design/table-definitions.md#rubrics)を参照する。
- 専用環境の `python3 setup.py` はFlyway V15、移行fixture、選択Java69件（失敗/エラー/skip各0）、WAR、login200成功。追加5件は `StandardRubricSourceTest` / `StandardRubricDatabaseTest`。NULL作成者、同一版の再実行、異なる内容の拒否、不完全データの拒否と課題数保持を確認した。
- 専用環境の `python3 rubric_runtime_check.py` は匿名302/教師403/初回password302/未登録503/登録後200/no-store成功。一次資料→DB→APIの30説明が完全一致。別の既存版を割り当てた合成課題・評価とその作成者が、再登録前後で不変であることも確認した。
- `python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display` は10件成功。
- 実ブラウザーのホーム・アカウント・研究同意・授業演習・課題エディター・評価・コードログで共通ボタン→30説明の表示を確認。後半3画面は専用DBの合成データを用い、評価生成やアンケート回答の正常系を完了扱いにはしていない。
- 全生徒JSPの共通navigation/page-end接続を確認。アンケート画面は既存DAOの `evaluation_feedback` 列名不一致によりGET503となり、モーダルの実画面確認は未達。コードログの空状態にも既存JSのnull参照を検出し、合成ログ行がある場合は表示を確認した。これらの別件は下記T006と[エラーレポート](../error-report.md)へ引き継ぎ、今回のルーブリック変更で修正していない。
- 実画面の1280/900/375pxで30説明/5→1/2表/全画面modal/ページ横overflowなし、Escape/focus復帰、未保存コード保持を確認。375pxでは既存ナビゲーションを展開して起動。prototypeの30説明も完全一致し、既存共通CSSと表の構成を再利用した。
- 503 JSON/不完全JSON/HTML応答の注入で代替の表を表示せずshared feedback＋再試行を確認。再試行後のEscape/focusも確認。遅延応答中のclose→reopenでは旧応答を破棄した。注入試験を実DB障害試験とは扱わない。
- ローカル `docker compose exec -T app gradle flywayMigrate flywayValidate registerStandardRubric --no-daemon --console=plain --warning-mode all` 成功、`registerStandardRubric` の再実行は内容一致・無変更。標準版に割り当てられた課題/評価は各0で、表示のために割当を追加していない。
- ローカル `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all` 成功、login200、配信 `rubric.js?v=106` のbyte一致。Node CLIがないためsyntaxはブラウザーで両scriptを構文解析し成功。専用合成行/主要7表0、container/network/所有copy/cache/serverを除去し、検証page解放を確認した。
- ルーブリック追加バッチ時点ではT004〜T006を保留し、教師工程の手前で停止していた。現在は教師工程への移行が承認済みで、T004〜T006全体の受入保留だけを維持する。

#### 教師工程前デバッグによる既知バグの解消（2026-10-04）

- 上記T007〜T010検証時に残ったアンケートGET503とコードログ0件時のJS例外は、[教師工程前デバッグ計画](./pre-teacher-debugging.md)で修正・再検証済み。
- アンケートは専用DBの合成完了評価・active surveyで修正前503を再現し、修正後GET200・評価フィードバック・下書き/送信/再読込・重複回答防止・同意/所有者/非対象のアクセス拒否を確認した。追加DAOテスト4件と回答入力テスト6件は失敗/skip各0。実ブラウザーでも提出済み回答とフィードバックの表示を確認した。
- コードログは0/1/3件の実画面で例外なし、サイドバー切替、前後の無効状態、タイムライン選択と追加/削除差分を確認した。1件の合成実行記録では標準入力/出力/エラーも表示された。
- これら既知バグの修正完了と、T004〜T006全体の完了は別。教師が設定する実プロンプト・正式なアンケート設定・ユーザー手動E2Eは、教師画面完成後の保留を維持する。標準ルーブリックの未登録/再試行検証を今回再実施したとは扱わない。

1. [x] T001 評価・ログ画面を既存レイアウトに沿ってDBデータへ接続する。本人の提出版と評価状態以外を表示しない。
2. [x] T002 提出確定から評価待ちを作成し、DB上の評価ワーカーで版固定・匿名化・AI要求・リトライ・JSON検証を実装する。失敗時も元提出と過去評価を変更しない。合成データによる実API→DB保存・再読込を専用DBで確認済み。認証済み画面・実運用設定の確認はT006に残す。
3. [x] T003 評価結果の全詳細とコードログ時系列・前後比較を本人限定で表示する。評価中は最新結果と前回結果を混同しない。
4. [ ] T004 同意・課題別active survey・完了評価・研究IDを検証し、アンケート回答を保存する。同一評価への重複履歴を作らず、別評価の回答履歴と過去回答の参照を可能にする。画面遷移図の6項目と選択肢を標準設定SQLに定義し、理由欄の必須/任意をDBで検証する。既存DBにsurveyがないため適用対象の作成待ち
5. [ ] T005 生徒ホーム導線、状態表示、評価・アンケート間リンクを整合させる。実装済み、ブラウザー確認待ち
6. [ ] T006 正常系・アクセス境界・再試行・回答重複・API未設定のテストとローカルブラウザー確認を行う。

### T004 / T005 未決事項と部分進捗

- 決定済みの回答識別子・対象・開始・履歴方針は[アンケート実装の確認事項](./survey-implementation-open-questions.md)に記録し、機能仕様・状態ルール・DB定義にも反映済み。
- 課題別アンケートの標準設問は画面遷移図の学生アンケートに合わせ、回答6項目と選択肢を[survey template SQL](../../../scripts/dev/configure-student-survey-template.sql)に定義する。新規の空のdraftアンケートに適用する。理由欄の表示名と必須条件は`survey_questions.reason_prompt_text`および`survey_questions.reason_required`で管理する。
- 同意が未確認・不同意・撤回の場合にアンケート状態を「対象なし」とし、サーバー側アクセスも拒否する。生徒ホームと評価画面から対象のsurveyへ遷移する導線を追加した。
- `StudentSurveyDao` は本人・現行同意・active課題別survey・完了した本人のシステムevaluationをサーバー側で検証し、回答行・回答項目をトランザクションで保存する。下書き再開、読み取り専用の提出済み回答、別評価/旧surveyの回答履歴、CSRF、必須・選択肢・文字数検証を実装。
- Flyway V8で `(student_user_id, survey_id, evaluation_id)` および `(survey_response_id, question_id)` の一意制約を追加し、ローカルDBへ適用済み。
- ローカルDBにactive surveyが無く、認証済み評価・アンケート画面の正常系を実データで確認できていない。確認状況と前提は[アンケート実装の確認事項](./survey-implementation-open-questions.md)を参照。

### T002 実装進捗

- 実装済み: 提出確定トランザクションから評価要求を登録し、提出・ルーブリック・プロンプト版を固定する。DBキューを単一バックグラウンドワーカーで処理し、匿名化済み入力スナップショット、Gemini応答、評価結果、失敗状態を保存する。
- 実装済み: Gemini APIキーは `GEMINI_API_KEY` 環境変数からのみ読み、未設定時は再試行せず失敗として記録する。通常ペイロードで初回と同一ペイロードの再試行を行い、最後に簡略ペイロードで再試行する。AI出力を検証し、失敗時は既存評価を上書きせず再試行用に新しい評価履歴を作成する。
- 検証済み: Gemini Interactions APIのHTTP要求・認証ヘッダー・JSON応答解析・安全なエラー分類をローカルHTTPモックでテスト。`response_format` による構造化出力を要求し、アプリケーション側でも評価応答スキーマを検証する。
- 2026-10-03追記: 保存処理の例外をAI出力不正として再API要求してしまう経路を修正。保存時の実行時例外も失敗として記録し、retry_countを減らさない。各API試行の失敗分類・HTTP statusを秘密情報なしで記録する。
- 2026-10-03追記: [EvaluationDatabaseTest](../../../src/test/java/control/evaluation/EvaluationDatabaseTest.java)を追加。`EVALUATION_DB_TEST=true`かつ`DB_NAME=ppe_evaluation_test_...`の専用DBでのみ実行し、ユーザー・学校・課題・提出・ルーブリック・プロンプトをすべて合成する。テストが作成した行だけを後片付けする。実APIテストは追加で`GEMINI_API_SMOKE_TEST=true`を要する（最大3要求、課金あり）。
- 最新の確認: 2026-10-03 17:30〜17:31 JST、合成課題のDBキュー→入力スナップショット→実Gemini (`gemini-3.7-flash`, Interactions API)→出力検証→評価結果・理由保存→再読込を確認。単独実行と結合テスト実行の2回とも成功。API方式・`store`既定値・モデル全面変更は行っていない。
- 最新の残件: 認証済みブラウザー導線・他生徒のアクセス境界・教師が設定する実運用プロンプトの確認はT006。Google Console上のtier/quota/billing/APIキー制限・データ保持方針の確認は引き続き必要。以下は今回の成功以前の診断記録であり、現在の結合テスト結果とは区別する。
- 過去の診断（現在の未成功判定ではない）: 2026-10-02のクレジット購入後、Interactions APIへの合成入力はHTTP 400となった（詳細未取得）。2026-10-03にAPIキーがコンテナへ設定済みであることを値を表示せず確認。段階診断では `gemini-3.7-flash` の最小テキスト要求AがHTTP 200、JSON MIMEのみのBと最小schemaのCは503、本番schemaのみのDは200、`max_output_tokens:4096` を加えたD2と評価指示・本番相当の合成データを加えたE/F/Gは503となった。同一bodyのBは5回中4回503・1回200で、503には `Retry-After: 30` が含まれたが、schemaや入力サイズ等の単一原因は確定できない。詳細は[AI API連携設計の段階診断記録](../../ai-api-integration-design.md)を参照。ListModelsはHTTP 200で対象モデルを返したが、Interactions APIの生成容量・設定の保証ではない。実データは送信していない。当時未検証だった合成課題のDB保存・再読込は上記の結合テストで確認済み。実運用設定・認証済み正常系・Console確認は引き続き残件。
Interactions APIへの合成入力はHTTP 400となった（詳細未取得）。2026-10-03にAPIキーがコンテナへ設定済みであることを値を表示せず確認。段階診断では `gemini-3.7-flash` の最小テキスト要求AがHTTP 200、JSON MIMEのみのBと最小schemaのCは503、本番schemaのみのDは200、`max_output_tokens:4096` を加えたD2と評価指示・本番相当の合成データを加えたE/F/Gは503となった。同一bodyのBは5回中4回503・1回200で、503には `Retry-After: 30` が含まれたが、schemaや入力サイズ等の単一原因は確定できない。詳細と制約は[AI API連携設計の段階診断記録](../../ai-api-integration-design.md)を参照。ListModelsはHTTP 200で対象モデルを返したが、Interactions APIの生成容量・設定の保証ではない。実データは送信していない。実際の課題プロンプト・DBへの結果保存・失敗・再試行、認証済み画面導線は未検証。Google側のtier/quota/billing/APIキー制限・データ保持方針もConsoleで要確認。これらが済むまでT002は完了扱いにしない。

## 要件対応

| 要件 | 受入条件 | タスク |
|---|---|---|
| 評価状態・提出履歴表示 | 最新提出版の状態を主表示し、過去版は対象版を識別できる。評価結果がないときに架空の点数を表示しない | T001, T003 |
| AI評価生成 | 提出版・ルーブリック版・プロンプト版・研究IDを固定し、匿名化後にJSON APIを呼ぶ。版ごとの状態同期、規定リトライ、失敗履歴を保持する | T002 |
| 評価根拠・コードログ | 本人の提出に関係するスナップショット、実行、差分、評価根拠を時系列で参照できる | T003 |
| アンケート | 同意状態・対象設定・必須条件をサーバーで検証し、回答を再読込できる。既提出の回答は上書きしない | T004 |
| 画面導線・権限 | 画面遷移図の操作がDB状態と同期し、他生徒の識別子による直接アクセスを拒否する | T001, T003, T004, T005 |
| 検証 | ビルド・自動テストと実DB再読込・認可境界・該当画面のブラウザー確認結果を記録する | T006 |

## 変更予定ファイル

- 作成: Evaluation / CodeLog / Survey Entity・DAO・Control・Servlet、バックグラウンド評価処理とGemini接続アダプター、評価・ログ・回答JSP/CSS/JavaScript、焦点を絞ったテスト。
- 変更: `StudentEditorDao` / `StudentEditorControl` / `StudentEditorServlet`（提出後の評価開始連携）、`StudentDao` / `StudentHomeServlet` / `home.jsp`（評価・アンケート導線とDB状態）、`web.xml`、`docker-compose.yml`、`.env.sample`、DB定義書。
- プロトタイプから再利用する表示資産: 評価のスコア・理由・履歴画面、コード差分ビューアー、段階表示アンケート、既存共通テンプレートと shared feedback。
- プロトタイプから除去する仮データ・擬似動作: HTML固定の評価点、サンプルログ、JSのみの採点・回答完了。アンケート回答を実装する前に、既存プロトタイプの設問定義をDBのactive surveyへ移し、回答は実DBから再表示する。

## 検証計画

- ビルド / テストコマンド: `docker compose exec -T app gradle test build --warning-mode all`、`docker compose config --quiet`、`git diff --check`
- 正常系: 提出版ごとの評価待ち・評価中・完了結果、2観点および詳細、ログ根拠、前回結果の表示、同意済みアンケートの開始・提出・再読込。
- 状態遷移・操作可否: 評価待ち・評価中・完了・失敗・要修正、失敗後の新評価履歴、アンケート未回答・進行中・提出済み・非対象。
- 権限境界・直接 URL: 別生徒の提出・評価・ログ・アンケートへのアクセスを拒否し、論理削除・旧改訂・終了割当は過去履歴の読み取りのみ許可する。
- DB保存後の再読込: 評価・要求・スナップショット・応答・根拠・アンケート回答をDBから再構成する。
- 異常系: AIキー未設定、タイムアウト、HTTPエラー、不正JSON、JSONスキーマ不一致、同一ペイロード再試行、簡略ペイロード再試行、同時処理、重複回答、必須項目欠落、同意撤回。
- 未実施の場合に残る未確認事項: 外部AI APIを呼ぶ実資格情報を用いた課題評価、実プロンプト・ルーブリック適用、同意済みアンケートの実回答。キー未設定の環境ではスタブテストと明示的な未設定状態までを検証する。

## 実装済み範囲の検証記録

- `docker compose exec -T app gradle test --tests 'control.evaluation.*Test' build --warning-mode all`: 成功。明示的な実行フラグのないDB・実APIテストはskipする。
- `docker compose exec -T -e DB_NAME=ppe_evaluation_test_b1016b19_20261003 -e EVALUATION_DB_TEST=true app gradle flywayMigrate test --tests control.evaluation.EvaluationDatabaseTest --rerun-tasks --warning-mode all`: 成功（専用DBをV9まで構築、合成fixtureの保存・再試行を検証）。
- `docker compose exec -T -e DB_NAME=ppe_evaluation_test_b1016b19_20261003 -e EVALUATION_DB_TEST=true -e GEMINI_API_SMOKE_TEST=true app gradle test --tests control.evaluation.EvaluationDatabaseTest --tests control.evaluation.EvaluationWorkerTest --tests control.evaluation.GeminiEvaluationClientTest --rerun-tasks --warning-mode all`: 成功（17件、失敗0、skip0）。異常系・古い提出の表示状態非上書き・重複完了rollbackを含む。専用DBはセッション中に作成した一時DBで、実行後に除去する。同名のDBを再利用せず、新しい専用DBを作成してmigration後に実行する。
- 保存失敗の回帰テスト初回失敗と修正記録は[エラーレポート](../error-report.md)を参照。
- 同意状態別のアンケート状態マスキングを追加し、`docker compose exec -T app gradle test --tests entity.StudentTaskSummaryTest build --warning-mode all` でテスト・ビルド成功。
- `docker compose exec -T -e GEMINI_API_SMOKE_TEST=true -e GEMINI_API_DIAGNOSTICS=true app gradle test --tests control.evaluation.GeminiEvaluationSmokeTest --rerun-tasks --warning-mode all`: 2026-10-03 04:26 UTC、合成データによるGemini評価スモークテスト1回成功（Gradle全体9秒）。この結果はクライアントの構造化出力応答検証までで、評価workerからDBへの保存・再読込は未検証。
- `docker compose exec -T app gradle test build --rerun-tasks --warning-mode all`: 成功。
- `git diff --check`: 成功。
- 評価ワーカー追加後の `docker compose exec -T app gradle test build --warning-mode all`: 成功。再試行回数、簡略ペイロード、APIキー未設定、不正応答、匿名化、出力スキーマを対象とした単体テストを含む。
- Gemini HTTPクライアントのローカルHTTPモックテスト: 要求パス・キーをヘッダーだけに含めること・構造化JSON応答・HTTP失敗の秘匿・402等の再試行分類を確認。
- Gemini APIモデル一覧・詳細取得: 成功。`gemini-2.5-flash` は当該キーでは新規利用不可との応答。`gemini-3.8-flash` は詳細取得を確認。
- 過去のInteractions API合成データ診断: HTTP 400/503で未成功だった。APIキーおよび応答本文全体は記録していない。その後の合成課題による実API→DB保存・再読込は上記の専用DB結合テストで成功しており、この過去記録を現在の未成功判定には使わない。
- ローカルDBの評価設定済み課題数: 0。評価対象提出の外部送信は行っていない。
- `docker compose config --quiet`: 成功。Geminiキーを任意の環境変数として受け渡すCompose設定を確認。
- 未認証の `GET /student/evaluation?assignmentId=1` とCSRFなしの `POST /student/evaluation/retry` はどちらもログイン画面へのリダイレクト（302）。認証済みセッションがないため、本人所有境界および実際の再試行は未確認。
- ブラウザーで `/student/home` から提出IDを含む評価画面へ遷移し、評価設定がない提出を「評価設定待ち」として表示することを確認。点数は生成・表示されない。
- ブラウザーで本人の提出IDを使ったコードログ画面を確認。DBに保存された3件のコードログ、前後差分、実行状態、標準入力・標準出力を表示し、時系列の選択が動作することを確認。
- 未確認: 他生徒の提出IDを用いた認可境界、評価中の前回評価表示、完了・要修正評価データの認証済み画面表示・再試行。評価ワーカーのDB保存は今回の専用DBテストで確認済み。アンケート回答は実装済みだが、認証済み画面と回答保存のE2E確認は未完了。
- `node --check` は実行環境に Node.js がなく実施できず。コードログ画面をブラウザーで操作してJavaScriptの時系列切り替えを確認。

## 完了条件

- [ ] 画面導線と表示を既存プロトタイプから大きく変えず、固定評価・ログ・回答を残さない。
- [ ] 評価を提出版・プロンプト版・ルーブリック版へ固定し、状態履歴と表示用状態を同時に更新する。
- [ ] AIへ送る個人識別情報を匿名化し、秘密情報を露出せず、失敗・不正応答を成功扱いしない。
- [ ] 生徒本人の全提出・評価・根拠ログ履歴を認可付きで表示する。
- [ ] 同意済みかつ対象の生徒だけがアンケートを開始・提出でき、同じ評価への提出済み回答は読み取り専用で再表示され、別評価への回答は履歴として保持される。
- [ ] 自動テスト、DB再読込、権限境界、ブラウザー確認結果と残課題を記録する。
