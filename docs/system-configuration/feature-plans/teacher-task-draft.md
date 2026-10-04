# 教師の課題・プロンプト・配信: 初回スライス実装計画

作成日: 2026-10-04 JST。状態: **S1・T001〜T015完了。次はS2（ルーブリック/プロンプト準備・AI生成）の計画具体化**。

進捗の正本は[実装ロードマップ](../implementation-roadmap.md)。本書は工程10の最初の縦切りを対象とし、工程10全体の実装完了を意味しない。

## 目的と範囲

- 利用者に提供する動作: 権限を持つ教師が課題の下書きを作成し、内容・対象クラス・公開予定を保存し、一覧から再読込して編集を続けられる。
- 初回スライス（S1）: **自分の課題下書きの一覧・新規作成・編集・プレビュー・保存・再読込・変更履歴表示**。DB → DAO → Control → Servlet → JSP/JavaScript → 検証までを通す。
- 含める入力: プロトタイプの課題名、難易度、テーマ、説明、実装機能、入力制約、作成時のルール、初期コード、テストケース、ヒント、学校/クラス、クラス別公開日時・期限、期限後提出方針。
- 今回対象外: 公開・予約公開の実行、公開中編集/改訂/複製、論理削除/復元、ルーブリック作成・適用、プロンプト作成・AI生成・適用/再評価、演習コード配信、アンケート設定、工程11〜13、管理者画面統合。未実装操作は理由付きで利用不可とし、成功するデモ処理を置かない。
- 既存仕様の機能追加・変更ではなく、合意済み機能の段階実装である。新たな仕様判断が必要になった場合は[AGENTS.md](../../../AGENTS.md)の文書確認手順に戻り、仕様をコードだけで変更しない。

## 必読資料

| 分類 | 正本・対象箇所 |
|---|---|
| 引継ぎ・工程 | [AGENTS.md](../../../AGENTS.md)「教師向け機能の開始」、[ロードマップ](../implementation-roadmap.md)工程10、[実装手順](../implementation-and-local-development.md) §4・5.4・5.6 |
| 要件・画面 | [機能仕様書](../../function-specification.md)「教師向け共通追記」「課題編集機能」「プロンプト設計機能」「コード配信機能」、[画面遷移図](../../screen-flow-diagram.md)「教師向け画面構成」 |
| 状態 | [課題状態](../../state-rules/teacher/task-state-rules.md)、[プロンプト状態](../../state-rules/teacher/prompt-state-rules.md)、[配信状態](../../state-rules/teacher/distribution-state-rules.md)、[状態索引](../../state-rules/implementation-state-table.md)、[演習状態](../../state-rules/student/exercise-state-rules.md) |
| データ・設計 | [テーブル定義](../../database-design/table-definitions.md)、[教師クラス図](../../class-diagram/03-teacher-task-distribution.puml)、[実装契約](../implementation-contract.md) IC-001/005/007/009、[AI連携設計](../../ai-api-integration-design.md) |
| 入出力 | [課題形式](../../format/task-format.csv)、[テストケース形式](../../format/task-testcase-format.csv)、[ヒント形式](../../format/hint-format.csv)。例示レコードは業務初期データとして登録しない |
| プロトタイプ | [課題HTML](../../../screen-flow-diagram/webapp/WEB-INF/teacher/task/task.html)・[JS](../../../screen-flow-diagram/webapp/js/teacher/task/task.js)・[CSS](../../../screen-flow-diagram/webapp/css/teacher/task/task.css)、[プロンプトHTML](../../../screen-flow-diagram/webapp/WEB-INF/teacher/prompt/prompt.html)・[JS](../../../screen-flow-diagram/webapp/js/teacher/prompt/prompt.js)、[配信HTML](../../../screen-flow-diagram/webapp/WEB-INF/teacher/distribution/distribution.html)・[JS](../../../screen-flow-diagram/webapp/js/teacher/distribution/distribution.js) |
| 共通UI | [教師共通JS](../../../screen-flow-diagram/webapp/js/teacher/shared/components.js)・[CSS](../../../screen-flow-diagram/webapp/css/teacher/shared/components.css)、[共通テンプレート](../../../screen-flow-diagram/webapp/WEB-INF/template/template.html)、[feedbackガイド](../../feedback-guideline.md) |
| 既存の保留 | [生徒エディター計画](./student-editor.md)、[評価・アンケート計画](./student-evaluation-survey.md)、[授業演習計画](./student-exercise.md)。既知バグの修正済み記録を再オープンしない |

## 工程10資料と既存実装の照合

今回の根拠はリポジトリの文書・ソースである。稼働DBの権限行・課題行・Flyway適用履歴や、現在のブラウザー動作は確認していない。開始時の `git status --short` は出力なし（作業ツリーはクリーン）で、引継ぎ文の「未コミット変更が残る」は現在の状態と異なる。破棄・commit・環境初期化はしていない。

| チェック項目 | 判定 (OK/要修正/要確認) | 根拠文書 | 差分・懸念 | 修正案 |
|---|---|---|---|---|
| 教師業務機能の現在位置 | OK | [ロードマップ](../implementation-roadmap.md)、[課題編集プロトタイプ](../../../screen-flow-diagram/webapp/WEB-INF/teacher/task/task.html) | S1の課題下書き画面を実装済み。教師ホームは設けず、アカウント管理は未実装 | 認証は再実装せずS1を追加する |
| ロール・学校・作成者境界 | 要修正 | [実装契約](../implementation-contract.md)、[認証Filter](../../../src/main/java/servlet/auth/AuthenticationFilter.java)、[認証DAO](../../../src/main/java/dao/AuthenticationDao.java) | Filterは教師領域へadminも通す。既存の学校/機能認可は生徒アカウント管理用で、課題の所有者認可はない | 課題Controlでactive教師・機能権限・作成者・学校権限を毎回検証。既存admin導線は維持 |
| 課題と子項目の保存先 | OK | [テーブル定義](../../database-design/table-definitions.md)、[V1](../../../src/main/resources/db/migration/V1__create_initial_schema.sql) | 課題、実装機能、テストケース、ヒント、クラス割当は存在する | 既存テーブルを使用。初期スキーマの作り直しはしない |
| 課題保存の競合 | 要修正 | IC-001、[V1](../../../src/main/resources/db/migration/V1__create_initial_schema.sql)、[学校Control](../../../src/main/java/control/admin/SchoolControl.java) | `tasks.updated_at` は秒精度・NULL可。学校/演習には独立versionの先例があるが課題にはない | 新規migrationで課題の保存versionを追加。改訂番号とは分離する |
| 下書きの非公開 | OK | [課題状態](../../state-rules/teacher/task-state-rules.md)、[生徒ホームDAO](../../../src/main/java/dao/StudentDao.java)、[エディターDAO](../../../src/main/java/dao/StudentEditorDao.java) | 生徒側は課題と割当の公開状態を両方確認している | 下書きは割当も`not_published`。日時到来だけで公開しないことを回帰確認 |
| 課題改訂・適用版の保持 | 要修正 | IC-001/007、[テーブル定義](../../database-design/table-definitions.md) | 改訂参照・版参照はあるが教師の業務処理はない | S1では不変。公開・改訂・適用版切替は後続の独立スライスで実装 |
| AI評価への接続条件 | 要確認 | [評価キューDAO](../../../src/main/java/dao/EvaluationQueueDao.java)、[AI設計](../../ai-api-integration-design.md) | 評価受付はactiveルーブリック・同じ課題の適用プロンプト・生成2段階completed・評価次元2件を要求する。IDを設定するだけでは接続できない | S2で生成・検証・保存まで実装し、その後公開へ進む。S1は未設定を明示 |
| 画面と監査の永続化 | 要修正 | [課題プロトタイプJS](../../../screen-flow-diagram/webapp/js/teacher/task/task.js)、[教師共通JS](../../../screen-flow-diagram/webapp/js/teacher/shared/components.js)、[feedbackガイド](../../feedback-guideline.md) | 固定教師ID/学校/課題、DOM更新だけの保存、localStorage監査がある | 見た目を再利用し、内容はDB/認証情報へ接続。shared feedbackは本実装のものを使用 |
| 配信と生徒演習の接続 | 要確認 | IC-005、[配信状態](../../state-rules/teacher/distribution-state-rules.md)、[演習状態](../../state-rules/student/exercise-state-rules.md) | 教師配信は未実装。課題公開を前提とする記述と、独立した授業演習配信のモデルに差がある | S1から分離。S4前に課題との依存と演習反映ルールを確定する |

## 技術前提・実装原則

- Java 21、javax Servlet 3.1/JSP/JSTL、Tomcat 9（Gretty）、Gradle、MySQL、Flyway、JUnit 5。根拠: [build.gradle](../../../build.gradle)、[docker-compose.yml](../../../docker-compose.yml)。
- Servletは入力解析・CSRF・HTTP応答、Controlは認可・状態検証・トランザクション、DAOは渡されたConnectionでSQLを実行する。[学校管理](../../../src/main/java/control/admin/SchoolControl.java)のパターンを再利用する。
- MySQLを正本とし、業務更新と成功監査は同一トランザクション。失敗時はrollbackし、秘密情報を除いてログ/履歴へ記録する。監査保存失敗を無視して成功させない。
- 既存認証、[CSRF](../../../src/main/java/servlet/auth/CsrfTokens.java)、[共通feedback](../../../src/main/webapp/js/shared/feedback.js)、接続基盤を利用する。標準ルーブリック表示と課題の評価用ルーブリック割当を混同しない。
- 新フレームワーク、通知一覧、クラス単位の教師権限、実運用の仮設定は追加しない。Struts→Spring等の移行ガイドは技術/作業が一致せず非適用。
- 計画スキルの原則チェック: 既存設計の消費、要件対応、タスク分解、権限/状態検証を満たす。独立したconstitution・topology・knowledge graphはリポジトリに存在せず、[AGENTS.md](../../../AGENTS.md)と上記設計を根拠にする。本件は移行ではない。
- 保存先と見出しは[リポジトリの計画テンプレート](../feature-plan-template.md)を優先する。要件・タスクのトレース資料は本計画専用の[チェックポイント](./checkpoints/teacher-task-draft/)に置く。

## 前提・未決事項

### S1で採用する実装詳細

1. 機能コードは既存`account-management`の命名に合わせ`task-management`とする。権限行なし/無効は拒否。プロトタイプの日本語機能名を認可コードにしない。
2. S1の一覧・詳細・更新は自分が作成した下書きだけ。作成者と異なる教師による更新は拒否する。学校は現在のenabled権限が正本で、クラスはその配下の選択対象。対象クラスがある場合、選択/保存済みの**全学校**への権限を要求し、部分一致で他校の情報を漏らさない。学校未指定の下書きは作成者境界で保護する。
3. 新規割当先はactive学校/クラスから選ぶ。権限喪失時は操作を拒否し、既存課題や履歴を削除しない。停止学校/クラスへの新規割当を拒否し、保存済み対象が停止している場合は理由付き表示と対象再選択を要求する。
4. 下書きの最小入力は課題名。未入力の説明は空文字、未選択の難易度はNULL、明示選択の「なし」は`none`で区別する。未選択クラス・未設定プロンプト/ルーブリックは未完成状態として保存可能。公開基準の不足を下書き保存の成功と混同しない。
5. テストの入出力や初期コードは改行・空白を保持する。入力例なし/出力なしもあり得るため、空文字を一律に不正としない。追加途中の子項目も下書きとして保持し、公開時の完全性検証はS3に残す。文字数/UTF-8容量はDB型に合わせ検証し、NUL等の格納不適合とPythonの構文記号を混同しない。
6. `language='Python'`を保存し、生徒エディターの`LOWER(language)='python'`条件へ合わせる。プロトタイプ/CSVの表示名`Python 3.x`をそのままDBへ渡さない。
7. 新規課題はサーバーでUUID由来の`TASK-...`系列コード、改訂1、`TASK-...-v1`を採番する。S1の下書き更新では改訂を増やさず保存versionだけを増やす。クライアントの日時/コード/教師IDを信用しない。
8. 保存時は`save_status='draft'`、`publication_status='draft'`、クラス別は`not_published`。予定日時は設定値としてのみ保存する。旧`resubmission_policy`は初回INSERTの互換値`allow`だけを与え、設定UI・可否判定・更新には使わない。
9. `rubric_id / rubric_version / active_prompt_version_id`はS1の変更入力に含めない。画面では設定状態をDBから読み、未設定は未設定と表示する。ヒントの他課題参照は自分の権限内下書きからのみ取得し、固定ライブラリは移植しない。
10. POST/Redirect/GETを使用し、DBのcommit後だけ成功表示する。新規作成にはセッションに紐づく作成tokenで二重送信を防ぎ、既存更新は期待versionで競合を拒否する。競合・保存失敗では入力を保持し、再読込/再試行方法を示す。

以上は初回の段階実装範囲であり、他教師の課題閲覧や公開後の運用権限全体を新しく定義するものではない。

### 後続スライス前に確認する事項（S1の着手は妨げない）

| 対象 | 確認すべき差分 | 確認時点 |
|---|---|---|
| プロンプト初回適用/再評価 | [プロンプト状態](../../state-rules/teacher/prompt-state-rules.md) §8は学習開始後の適用版変更を新課題へ限定する一方、§8-2と[機能仕様](../../function-specification.md)は全体再評価確定時の現在適用版切替を規定。初回適用に再評価対象がない場合の確定イベントも明示されていない | S2計画前に、既存提出の版固定を維持する適用/再評価条件を一問ずつ確認。既存の版不変・履歴保持の合意は巻き戻さない |
| ルーブリック設定UI | 課題作成要件には初期ルーブリック/プロンプト設定があるが、課題プロトタイプの作成フォームに対応入力がない | S2で既存プロンプト画面との接続を照合し、必要な導線変更は文書→プロトタイプ→本実装の順で確認 |
| 演習配信 | [配信状態](../../state-rules/teacher/distribution-state-rules.md) §7の「課題未公開なら配信不可」に対し、[コード配信要件](../../function-specification.md)は授業演習への独立配信、[配信テーブル](../../database-design/table-definitions.md#distributions)はtask参照なし | S4前に依存関係を確認。演習の完了/期限/要再確認条件もこの時点で定義 |
| 実設定・実利用者 | 管理者の教師/権限UIは工程12。実DBの教師権限・クラス存在は今回未確認 | S1は専用合成fixtureで検証。実教師の作成・権限付与を無断実行せず、実運用で不足する設定はブロッカーとして報告 |

## ユースケースとデータ契約

| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 下書き一覧/詳細 | active教師、task-management、自分の下書き、全対象校権限 | status、taskId | users、機能/学校権限、tasks、割当/学校/クラス、子項目、設定状態 | なし | なし | 空一覧を明示。存在しない/範囲外IDは詳細を漏らさない応答 |
| 新規下書き保存 | 同上、選択クラス全件の学校権限 | 課題項目、子項目、予定、CSRF、作成token | 権限、学校/クラス | tasks、features、test_cases、hints、assignments、audit_logs | 新規→draft、割当not_published | commit後に一覧/編集へ戻る。途中失敗は全体rollback |
| 下書き更新 | 同上、作成者一致、旧/新対象校全件 | taskId、期待version、全入力、CSRF | 対象課題/子項目/割当、現在の認可 | 下書き更新、子項目追加/更新/無効化、割当変更、version+1、監査 | draft→draft | 競合409、入力不正400、権限拒否403、DB障害503。入力と再読込導線を保持 |
| プレビュー | 本人が入力中の課題 | 現在フォーム | 必要な権限内ヒントのみ | なし | なし | 実行/保存/公開ではないことを明示。XSSを防いで描画 |
| 操作履歴表示 | 課題詳細と同じ認可 | taskId | audit_logs、実行者loginId | なし | なし | 日時・実行者・操作・変更項目・成功/失敗を表示。履歴なしも明示 |
| 範囲外操作 | 全ロール | publish/delete/restore等の未提供action、偽造ID | 必要な認可/状態のみ | 業務データ変更なし。安全な拒否記録のみ | なし | UIを理由付き無効化、サーバーでも拒否。見た目だけの成功は返さない |

## 実装手順とタスク分解

`REQ-001〜009`は本計画内だけの追跡ID。機能仕様書にIDはないため、既存節との対応を末尾に記録する。仕様書へ新しい要件を追加したことを意味しない。

### Phase 1: 着手条件（Plan 1.1 / REQ-001, REQ-003, REQ-009）

- [x] T001 [Plan:1.1] `docs/system-configuration/feature-plans/teacher-task-draft.md`へ開始時の作業ツリー・専用検証環境・Flyway現在版・権限fixture準備状況を記録する。既存環境/DBを初期化しない。

#### T001 実施記録（2026-10-04 18:37 JST）

- 作業ツリーには本計画作成時の`AGENTS.md`、`docs/README.md`、ロードマップ、エラーレポートの変更と、教師計画/チェックポイントがある。いずれも今回までの計画作業で作成した文書差分。別の作業者の予期しない変更は見つからず、破棄・revert・commitしていない。
- `.env`と`.env.sample`は存在するが、値は読み取っていない。起動済みのapp/db/python-runnerは応答し、DBサービスはhealthy。`docker compose ps --all`に専用テストDBサービスはない。稼働中DBをテスト用に転用しない。
- DB変更を伴わない`docker compose exec -T app gradle flywayInfo --no-daemon --console=plain --warning-mode all`が成功し、接続先schemaはversion 15。適用済みV1〜V15は全てSuccess。V16はリポジトリにまだ存在せず、次の採番候補。`flywayInfo`のみ実行し、migration適用・DB初期化はしていない。
- 既存の専用DBテストは`SCHOOL_DB_TEST`/`ppe_school_test_...`、`EVALUATION_DB_TEST`/`ppe_evaluation_test_...`、`EXERCISE_DB_TEST`/`ppe_exercise_test_...`のfail-closed gateを使う。教師課題用fixture/gate、専用DB、`task-management`教師権限fixtureは未準備。実アカウント・権限・課題データを照会/変更していない。
- 判定: T001の現状確認と記録は完了。T002以降のmigration適用・DBテストを始める前に、独立した`ppe_teacher_task_test_...`専用DBと合成の教師・学校/クラス・権限fixtureを用意し、その接続先を照合する必要がある。環境が用意できなければ、文書/コード作業とDB統合検証を区別して報告する。

### Phase 2: DB・認可・データ層

#### Plan 2.1: 保存versionを追加（REQ-005）

- [x] T002 [Plan:2.1] `src/main/resources/db/migration/V16__support_teacher_task_drafts.sql`で`tasks.version BIGINT NOT NULL DEFAULT 1`と正数CHECKを追加し、`docs/database-design/table-definitions.md`と`docs/class-diagram/03-teacher-task-distribution.puml`を更新する。着手時にV16が使用済みなら次の未使用番号へ変更する。既存改訂・割当・評価参照は変更しない。

#### T002 実施記録（2026-10-04 18:39 JST）

- V15までのFlyway履歴を確認済みの開発DBに対し、新しい`V16__support_teacher_task_drafts.sql`を追加した。`tasks.version`を`BIGINT NOT NULL DEFAULT 1`とし、`CHECK (version >= 1)`を設定する。既存行には既定値1が入り、改訂番号・割当・評価参照には触れない。
- DB定義に列の用途とV16追加を記録し、教師クラス図のTaskへversionを追加した。`version`はフォーム更新競合用で、`revision_number`とは別の値として扱う。
- V16はリポジトリ未使用であることを確認して採番した。専用教師検証DBがまだないためmigrationは**どのDBにも適用していない**。実DB上のデフォルト値・CHECK強制の確認は専用合成DBを用意した後に行う。
- 検証: `docker compose exec -T app gradle flywayValidate --no-daemon --console=plain --warning-mode all` と `git diff --check` の結果を下記「T002検証結果」に記録する。

#### T002検証結果

- `docker compose exec -T app gradle flywayValidate --no-daemon --console=plain --warning-mode all`: 失敗。V16追加後、既存schema version 15に未適用migration 16があるため、Flywayが`Detected resolved migration not applied to database: 16.`でvalidationを拒否した。これは未適用migrationを含む共有開発DBへ適用しない安全制約と整合する。失敗詳細は[エラーレポート](../error-report.md)を参照。`flywayInfo`でpending表示・schema version 15を読み取り確認し、migrationは適用しない。
- 静的確認: V16が`tasks.version BIGINT NOT NULL DEFAULT 1`と`version >= 1`のみを追加し、DB定義/クラス図と対応することを確認。
- T013で使い捨ての専用MySQLへV1〜V16を適用し、V16の既定versionとCHECKを確認した。共有開発DBはschema version 15のままで、V16は適用していない。

#### Plan 2.2: 型付き入力・認可・SQL（REQ-001, REQ-002, REQ-003, REQ-005, REQ-006）

- [x] T003 [US1] [Plan:2.2] `src/main/java/entity/TeacherTaskInput.java`、`TeacherTaskPage.java`、`TeacherTaskDetails.java`、`TeacherTaskAuditEntry.java`に型付き課題/子項目/予定/表示DTOを定義する。既存テストケース・ヒント表示型を再利用できる箇所は再利用する。
- [x] T004 [US1] [Plan:2.2] `src/main/java/dao/TeacherPermissionDao.java`を作成し、active教師・機能権限・学校権限と学校/クラスの対応をConnection内で取得/再検証する。更新時は認可行をロックし、途中の権限変更と同時確定しない。

#### T003/T004 実施記録（2026-10-04 18:41 JST）

- T003: `TeacherTaskInput`はDB難易度/期限後提出方針の型を持ち、子リストを防御的コピーする。既存`EditorTestCase`/`EditorHint`を使い、課題詳細・一覧ページ・監査・学校/クラス/再利用ヒント候補の表示DTOを追加した。表示用データは記号・空白を正規化せず保持する。
- T004: `TeacherPermissionDao`はトランザクション必須を確認したうえでactive教師をロックし、`task-management`機能権限とenabled学校権限を再検証する。選択肢はactive学校/クラスに限定し、認可行を`FOR UPDATE`で保持し、重複した学校権限は学校IDで重複排除する。管理者は教師として認可しない。
- 単体テスト: `docker compose exec -T app gradle test --tests 'entity.TeacherTaskInputTest' --tests 'dao.TeacherPermissionDaoTest' --no-daemon --console=plain --warning-mode all` 成功。新規JUnitテスト6件、失敗0。Java本体/テストのcompileも実行された。`git diff --check`成功。
- Pylance等の別テスト呼出しは新規Javaテストを検出できず「No tests found」を返したため、リポジトリ標準のGradleテスト実行へ切り替えて上記成功を確認した。詳細は[エラーレポート](../error-report.md)に記録。
- 未確認: 専用教師テストDBがないためDAOのSQL/ロックをMySQL上で実行していない。既存教師機能管理も未実装のため、fixture上の権限変更競合・active/disabled組合せはT013/T014で専用合成DBを用いて検証する。T003/T004はコード実装完了だが、S1のDB統合・受入完了を意味しない。
- [x] T005 [US1] [Plan:2.2] `src/main/java/dao/TeacherTaskDao.java`へ権限内の一覧/詳細/ヒント参照、行ロック付き更新、子項目・割当保存、監査保存/読込を追加する。子IDは必ず対象課題との所属を検証し、公開・旧改訂・削除済み課題を下書きとして更新しない。

#### T005 実施記録（2026-10-04）

- `TeacherTaskDao`に作成者で絞った下書き一覧/詳細、再利用ヒント、入力全項目の読込、新規INSERT、課題versionと課題行ロックによる更新、子項目の所属確認と論理無効化、未公開クラス割当の保存/履歴化、成功監査の記録/取得を実装した。読み取り・更新対象から公開済み課題と割当、削除済み課題を除外する。
- 子行追加を同じ保存内で誤って無効化しないよう更新前の既存ID集合だけを無効化対象にし、新規行もフォーム順序で保存する。解除済み割当は編集フォームへ再表示せず、履歴を残したまま再割当時には新しい行を作る。
- 詳細読込SQLに課題本文列を含め、再読込時にタイトル等が欠落しないよう修正した。空の機能リストでは不要なbatch SQLを発行しない。
- `TeacherTaskDaoTest`を追加し、トランザクション必須、識別子ガード、初期version付きINSERTとgenerated keyを確認した。`docker compose exec -T app gradle test --tests 'dao.TeacherTaskDaoTest' --tests 'dao.TeacherPermissionDaoTest' --tests 'entity.TeacherTaskInputTest' --no-daemon --console=plain --warning-mode all` 成功（10件、失敗0、skip 0）。`docker compose exec -T app gradle compileJava --rerun-tasks --no-daemon --console=plain --warning-mode all` と`git diff --check`も成功。
- 検証境界: 専用教師テストDB未準備のため、一覧/詳細・子項目更新・楽観ロック・監査SQLをMySQL上では実行していない。V16も共有DBへ適用していない。これらはT013/T014の合成DB統合検証に残し、S1受入完了とは扱わない。
- 実装結果とテスト証跡は[バッチレポート](./checkpoints/teacher-task-draft/batch-report.yaml)と[実装チェックポイント](./checkpoints/teacher-task-draft/tasks-to-impl.yaml)に記録。

### Phase 3: 課題下書きの縦切り（US1）

#### Plan 3.1: Controlの保存契約（REQ-001〜006）

- [x] T006 [US1] [Plan:3.1] `src/main/java/control/teacher/TeacherTaskControl.java`に一覧/詳細/保存/履歴取得を実装する。業務更新と成功監査を一括commitし、古いversion・作成者相違・全対象校の認可不足・下書き以外を拒否する。失敗監査はrollback後の別トランザクションで安全に記録し、その記録失敗もログへ明示する。

#### T006 実施記録（2026-10-04）

- `TeacherTaskControl`で教師ロールを限定し、一覧/詳細/ヒント/監査の読込を権限確認付きConnection内で実行。割当済みクラスの現在の有効権限も読み込み時に確認する。
- 新規・更新をひとつのDBトランザクションで実施し、作成者/下書き/version条件の検証、旧/新対象クラスの権限再確認、業務更新と成功監査の同時commitを接続した。失敗時はrollback後に別Connectionで最小限の失敗監査を試み、監査自体が失敗した場合は元エラーに抑制例外を付けてSEVEREログへ出す。commit後のclose例外は失敗監査として記録しない。
- 監査詳細には課題本文・コードを複製せず、変更カテゴリとversionのみを保存する。期限/内容の詳細入力検証とセッション連動の新規作成tokenはT007/T008で続ける。
- `TeacherTaskControlTest`で管理者拒否と無効versionの事前拒否を確認し、`TeacherPermissionDaoTest`にクラスIDガードを追加。`docker compose exec -T app gradle test --tests 'control.teacher.TeacherTaskControlTest' --tests 'dao.TeacherTaskDaoTest' --tests 'dao.TeacherPermissionDaoTest' --tests 'entity.TeacherTaskInputTest' --no-daemon --console=plain --warning-mode all` 成功（13件、失敗0、skip 0）。`git diff --check`成功。
- 専用DB未準備のため認可JOIN/FOR UPDATE、トランザクションrollback、成功/失敗監査のMySQL統合動作は未確認。T013/T014に残す。
- [x] T007 [US1] [Plan:3.1] 同Controlで予定/期限、DB型容量、子項目の型/順序/所属、対象校/クラスを検証する。新規作成tokenは同一セッションの並行POSTを直列化して成功IDを保持し、同じ要求を再送しても二重作成しない。トランザクション終了後の応答障害でもDB成功を失敗扱いして再INSERTしない。

#### T007 実施記録（2026-10-04）

- `TeacherTaskInputValidator`で課題名の必須/255文字、TEXT列のUTF-8 65,535 byte上限、NUL/不正surrogate、子ID重複/負数、クラス割当重複、期限と公開予定の前後関係を検証する。未入力説明・テスト入出力・ヒント名/本文は空文字にし、表示用文字列・コード・改行をtrimせず、子項目の順序はフォーム順に正規化する。
- `TeacherTaskCreateRegistry`はHttpSession属性として使う同期registryを実装する。token発行・未知/期限切れtoken拒否・同一session内の同token処理直列化・成功ID保持を行う。作成トランザクションcommitの応答が不確かな場合に備え、DAOは成功監査のrequest_idから既作成課題を回復する。更新のversion競合は専用例外へ分離した。
- `TeacherTaskInputValidatorTest`と`TeacherTaskCreateRegistryTest`で上限、正規化、無効文字、日時、重複要求、並行要求、失敗後再試行を確認。統合実行はT008のフォーム/Servletテストと合わせて下記B04へ記録する。

#### Plan 3.2: Servlet・フォーム解析（REQ-001, REQ-002, REQ-003, REQ-005, REQ-008）

- [x] T008 [US1] [Plan:3.2] `src/main/java/servlet/teacher/TeacherTaskForm.java`、`TeacherTaskServlet.java`を作成し、`/teacher/task`のGET（一覧・選択・履歴）とPOST（createDraft/updateDraft）を接続する。UTF-8、CSRF、許可action、HTTP状態と入力保持、PRG、エラーログを実装する。管理者は新業務Controlで拒否する。

#### T008 実施記録（2026-10-04）

- `/teacher/task` GET/POSTを追加し、認証Filterの教師identity、既存CSRF token、Controllerの権限/保存処理、POST/Redirect/GETを接続した。新規保存はsession registryを通し、管理者/不明操作/CSRF不正を拒否し、競合409・入力不正400・未存在404・DB障害503を扱う。エラー再表示では送信値をrequestへ保持する。
- `TeacherTaskForm`は4 MiB上限、厳密UTF-8、許可field、scalar重複、配列長、ID/日時/actionを検証し、繰返しテストケース/ヒント/クラス割当を型付き入力へ変換する。初回フォームJSPはT009で追加するため、現時点でGET/認証HTTP/ブラウザー動作は未確認。
- `/teacher/task`は画面遷移用ServletでREST APIではない。認証HTTP・JSP描画・DB保存のprobeは専用DBとT009の画面が用意された後、T013/T014で行う。shared DB V15は維持し、V16を適用していない。
- B04の27件の対象JUnit、強制production compile、`git diff --check`はいずれも成功。詳細は[バッチレポート](./checkpoints/teacher-task-draft/batch-report-T007-T008.yaml)を参照。

#### Plan 3.3: プロトタイプ準拠画面（REQ-002, REQ-006, REQ-007, REQ-008）

- [x] T009 [US1] [Plan:3.3] `src/main/webapp/WEB-INF/teacher/task/task.jsp`、`src/main/webapp/css/teacher/task/task.css`、`src/main/webapp/js/teacher/task/task.js`へ課題プロトタイプの構成・動的入力・プレビュー・一覧/履歴モーダルを移植する。学校/クラス/件数/教師ID/ヒントは実データへ置換し、入力/出力/初期コードの空白と改行を保持する。

#### T009 実施記録（2026-10-04）

- 本実装の課題画面にプロトタイプのヒーロー統計・課題フォーム・右側プレビュー・下書き一覧・ヒント候補/監査履歴モーダルの構成を移植した。課題・ヒント・学校・クラス・件数・作成者・履歴はDB/認証ユーザーから表示し、固定サンプル行や固定教師IDは持ち込んでいない。画面遷移図とプロトタイプのファイルは変更していない。
- 作成/更新フォームをT008の`TeacherTaskForm`名に接続し、CodeMirror、複数学校/クラス、クラスごとの日時、テストケース/ヒントの追加削除、プレビュー、保存時のPRG、CSRF、入力値保持を接続した。公開・削除・復元・プロンプト操作は成功表示や疑似処理を作らず、後続対応であることを明示して無効化した。
- Tomcat 9 JSP ELから表示するため、表示対象recordへJavaBeans getterを加えた。複数担当校のクラス選択を可能にするため、Controlは教師の認可範囲内の学校に属するクラス候補を取得する。CodeMirrorは教師画面に限定して共通テンプレートから読み込み、既存生徒画面の読込経路は変更していない。
- `docker compose exec -T app gradle test war --no-daemon --console=plain --warning-mode all` 成功。JUnit XMLは合計163件（成功109、失敗0、skip54）。WAR内にJSP/CSS/JSが含まれることを確認。ブラウザーでは `/teacher/task` 未認証時のログイン転送、CSS/JSのHTTP 200、共有feedback依存を注入した画面JS確認（初期プレビュー/テストケース2件/実行時例外0）を確認した。
- 認証済みJSP表示・ブラウザー操作とMySQL保存・再読込は後続T013/T014で実施した。検証証跡は[B05バッチレポート](./checkpoints/teacher-task-draft/batch-report-T009.yaml)と[B07バッチレポート](./checkpoints/teacher-task-draft/batch-report-T011-T013.yaml)を参照。
- [x] T010 [US1] [Plan:3.3] 同JSP/JSで保存完了はサーバー成功時だけ表示する。未実装の公開/削除/復元/プロンプト操作は説明付きで利用不可にし、固定課題・擬似公開・固定ヒント・localStorage業務保存を除去する。監査詳細はDBから取得し、作成者/更新者はloginId表示とuser_id保存を分離する。

#### T010 実施記録（2026-10-04）

- DB保存成功後にServletがsessionへ一回限りの通知を置き、PRG後にServletが消費した場合だけ完了通知を表示する。URL queryは成功判定に使わない。JSP/JSに公開・削除・復元の疑似成功経路、固定課題/教師/ヒント、localStorage利用はなく、公開/プロンプト操作は未対応であることを示して無効化した。
- 下書き一覧は認証ユーザーのDB結果だけを描画し、作成者/最終更新者はloginId、永続化/監査actorはuser_idのまま分離した。履歴モーダルはControlから取得したDB監査行を表示する。
- `docker compose exec -T app gradle test war --rerun-tasks --no-daemon --console=plain --warning-mode all` 成功（JUnit XMLは合計165件、成功111、失敗0、skip54）。session通知の一回限り消費テストが成功。固定サンプル・localStorage・画面内擬似成功処理・URL query成功判定を対象コードで検索し、該当なし。共有開発DBは変更していない。
- このバッチ時点では認証済み操作とDB再読込は未確認だったが、後続T013/T014で使い捨て専用DBにより一部確認した。残る受入範囲は[B07バッチレポート](./checkpoints/teacher-task-draft/batch-report-T011-T013.yaml)を参照。

#### Plan 3.4: 教師共通UI・既存導線（REQ-001, REQ-007, REQ-008）

- [x] T011 [US1] [Plan:3.4] `src/main/webapp/WEB-INF/teacher/shared/navigation.jspf`、`src/main/webapp/css/teacher/shared/components.css`、`src/main/webapp/js/teacher/shared/navigation.js`を追加する。教師プロトタイプのヘッダー/ナビ/モバイル操作を使い、認証済みID・DB学校名・権限を表示する。本実装のshared feedbackへ統合し、通知一覧や仮セッション/監査を移植しない。
- [x] T012 [US1] [Plan:3.4] `src/main/webapp/WEB-INF/template/page-start.jspf`、`page-end.jspf`に教師用分岐・CSS/CodeMirror/JS読込を追加し、`src/main/java/servlet/auth/AuthHomeServlet.java`と`src/main/webapp/WEB-INF/teacher/home.jsp`から権限がある教師に課題編集導線を提供する。既存生徒テンプレート・管理者ホーム/学校管理は維持する。

#### T011/T012 実施記録（2026-10-04）

- 教師共有ナビは認証ユーザー情報とDBから取得したactive担当校・`task-management`権限を表示し、権限がない場合は課題編集導線を利用不可にする。画面遷移図・プロトタイプは変更していない。
- 教師のみ共通テンプレートにサイドバー/ヘッダー/フッター、教師CSS、CodeMirror、ナビJSを接続。生徒テンプレートと管理者ホーム/学校管理の経路を維持した。
- 実ブラウザーで認証済み教師ホーム、学校名/権限、課題編集への導線、モバイルナビ開閉・Escape・フォーカス復帰を確認した。受入で発見した認証Filterと課題Servletのrequest属性名不一致による403も修正し、回帰テストを追加した。

### Phase 4: 検証と記録

#### Plan 4.1: 初回スライスの受入（REQ-001〜009）

- [x] T013 [Plan:4.1] `src/test/java/control/teacher/TeacherTaskDatabaseTest.java`、`src/test/java/entity/TeacherTaskInputTest.java`、`src/test/java/servlet/teacher/TeacherTaskFormTest.java`に専用合成DB/入力検証を追加する。DBテストは`TEACHER_TASK_DB_TEST=true`かつ`DB_NAME=ppe_teacher_task_test_...`限定で、fixture作成行のみ清掃する。
- [x] T014 [Plan:4.1] 下記コマンドと認証HTTP/ブラウザーの受入シナリオを実行する。skipは成功の根拠にせず、DB保存・再読込・認可境界・競合・非公開・既存導線を確認し、結果を本計画へ記録する。

#### T013 実施記録（2026-10-04）

- Fail-closed `TeacherTaskDatabaseTest`は専用DB名、`TEACHER_TASK_DB_TEST=true`、接続先catalog、Flyway V16を確認してから合成fixtureを作成する。作成・別Connectionからの再読込・更新version/監査・権限なし/権限取消を検証し、テスト生成行だけを清掃する。
- 使い捨ての専用MySQLにV1〜V16を適用してDBテスト2件を実行。接続先DB/containerは検証後に削除し、共有開発DBはschema version 15のまま維持した。
- Java 21を明示し、専用DB環境で`gradle test war --rerun-tasks --no-daemon --console=plain --warning-mode all`を実行。JUnit XMLは合計178件（成功124、失敗0、skip54）、WAR生成成功。専用DB統合テストは8件成功。

#### T014 実施途中の記録（2026-10-04）

- 合成教師でログインし、教師ホームと課題編集JSPを実ブラウザーで表示。課題名・説明・テストケース・学校/クラス・期限後提出方針を保存し、PRG後の成功通知、再読込後の値保持、通知が再表示されないことを確認した。
- 実JSP検証で共通ナビの文字コード/taglib重複、保存POSTの認証属性不一致、エラー再表示時のnull-unboxing NPEを発見して修正。モバイルナビの閉じ操作とフォーカス復帰も確認した。
- DBで複数クラス・機能/テストケース/ヒントの更新、同version同時更新の一方だけcommit、子ID所属エラー時とMySQL障害時に親/子の変更がrollbackされること、全割当が`not_published`であること、学生DAOが未公開割当を返さないこと、別教師所有課題/未許可校割当の拒否を確認した。
- HTTP smokeで未認証GET `/student/home`→302 `/student/account/login`、`/admin/schools`と`/teacher/home`→302 `/teacher/account/login`、`/student/account/login`→200を確認した。既存画面の未認証ガード確認であり、認証済み画面描画/操作の回帰確認とは区別する。
- 当時点の未完了: すべてのendpoint/actionごとの全ロール・偽造ID認可マトリクス、既存全画面の認証済みHTTP/ブラウザー回帰。後続のT014完了記録で解消状況と限界を示す。

#### T014 完了記録（2026-10-04）

- T014で追加したDB受入10件はすべて成功。認可マトリクスは、未認証リダイレクト、生徒/adminの教師画面GET/POST拒否、機能権限なし/無効、停止教師、学校権限取消、別教師による課題/監査閲覧、正当/偽造・複数校混在school/class/task/child ID、同version競合、rollbackを確認した。
- 使い捨てMySQLにV1〜V16を適用・validateし、合成教師/admin/生徒で認証HTTPを実行。教師ホーム/課題編集、管理者ホーム/学校管理、生徒ホーム/公開済み合成課題のエディター/標準ルーブリック/授業演習が期待どおり応答した。未存在のtask/editorは404、存在しないsurveyは403で拒否されることも確認した。
- ブラウザーで教師ホームと課題フォームを表示し、生徒ホームと既存エディター画面を確認。幅390pxで教師ナビを開き、`aria-expanded=true`と認可済みメニュー表示を確認した。既存のS1保存・PRG・再読込/成功通知の確認も維持する。
- `TEACHER_TASK_DB_TEST=true gradle test war --rerun-tasks --no-daemon --console=plain --warning-mode all`は成功。JUnit XMLは合計180件（成功126、失敗0、skip54）、WAR生成成功。専用DB統合テストは10件（成功10、skip0）。
- 合成データと一時Webアプリを含む使い捨て環境を削除。共有DBはV15のままでV16未適用。画面遷移図/プロトタイプは変更していない。
- 受入範囲の限界: 有効な生徒評価/アンケートデータは作らず、存在しないsurveyの拒否のみを確認した。正式な評価設定とユーザー手動の提出→評価→アンケートE2EはS1外として保持する。54件のskipも受入根拠に含めない。
- 詳細は[バッチレポートB08](./checkpoints/teacher-task-draft/batch-report-T014-T015.yaml)を参照。

#### 8080でのユーザー確認準備（2026-10-04）

- ユーザーが8080で教師画面を確認できるよう、明示依頼に基づき開発DBへV16を適用し、Flyway validateを実行した。既存データを削除する操作は行っていない。
- 開発DBに合成テスト教師、専用の合成学校/クラスと課題管理権限を追加した。テスト教師は新規課題の下書きを作成できる。資格情報は会話でユーザーへ直接伝え、ソース/計画文書へは記録しない。
- 稼働中8080アプリを再起動して最新コードを反映し、ブラウザーでテスト教師ログイン、教師ホーム、課題編集フォームと合成学校表示を確認した。新規課題の保存・再読込をユーザーが行える状態。
- 追加詳細は[開発環境更新記録](../error-report.md)を参照する。画面遷移図/プロトタイプは変更していない。

#### Plan 4.2: 次スライスへ引き継ぐ（REQ-009）

- [x] T015 [Plan:4.2] `docs/system-configuration/feature-plans/teacher-task-draft.md`、`docs/system-configuration/implementation-roadmap.md`を実施結果と未確認事項で更新する。S1の未確認範囲を区別し、S2の未決事項を次の確認対象として引き継ぐ。

### 依存と実行順

T001 → T002 → T003/T004 → T005 → T006/T007 → T008 → T009/T010 → T011/T012 → T013/T014 → T015。T003とT004は契約確定後に別ファイルで並行可能だが、他は同一ファイル編集または呼出契約の依存があるため、無理に分割しない。T013のテスト作成は各層の実装に合わせて進め、T014の統合確認はWeb接続後に実施する。

## 変更予定ファイル

- 作成: T002〜T005、T006、T008〜T009、T011、T013に列挙したmigration、Java、JSP/CSS/JS、テスト。追加ディレクトリは既存の層分類に合わせ`control/teacher`、`servlet/teacher`、`WEB-INF/teacher/task`、`teacher/shared`とする。
- 変更: T002のDB/クラス設計、T012の共通テンプレート/認証ホーム、T015の進捗・検証記録。
- プロトタイプから再利用: 課題フォーム・プレビュー・一覧・履歴モーダル、教師ヘッダー/ナビ、課題/共通CSS、入力行追加とCodeMirror表示処理。
- 除去/置換: 固定学校/クラス/課題/教師、乱数による画面内だけの課題生成、DOMのみの保存/件数加算、localStorage認証/監査、未実装の公開成功通知。
- 生徒DAO・評価worker・既存migration・生徒標準ルーブリックはS1の変更対象にしない。回帰でS1に起因する問題が見つかった場合だけ関連箇所を修正する。

## 検証計画

### 実装時のコマンド

```sh
docker compose exec -T app gradle flywayValidate --no-daemon --console=plain --warning-mode all
docker compose exec -T app gradle test --tests 'entity.TeacherTaskInputTest' --tests 'servlet.teacher.TeacherTaskFormTest' --no-daemon --console=plain --warning-mode all
docker compose exec -T app gradle build --warning-mode all
```

- migration適用と`TeacherTaskDatabaseTest`は、**別途用意した専用合成DBへの接続を確認した実行環境**で`gradle flywayMigrate flywayValidate`、`TEACHER_TASK_DB_TEST=true gradle test --tests 'control.teacher.TeacherTaskDatabaseTest'`を実行する。通常appのDB名をそのまま使わない。接続先・移行権限は値を公開せず確認する。
- 通常開発DBへのmigrationは、専用DBでの既存行保持を検証してから行う。共有DBの初期化、Flyway clean、全データseed、既存権限の自動付与はしない。
- JSPはJavaビルドだけでは受入完了にならない。認証済み実HTTPでJSPコンパイル/表示を確認し、ブラウザーで操作する。

### 受入シナリオ

| # | 確認内容 | PASS条件 |
|---|---|---|
| 1 | 最小下書き→保存→再読込 | 課題名だけでも保存できる。設定不足は未設定表示、task改訂1/保存version1、公開なし |
| 2 | 全項目・複数クラス・子項目の更新 | 別Connectionから再読込して値/改行/順序/作成者/更新者一致。更新versionだけ増加。子項目の削除は無効化し他課題を変更しない |
| 3 | 非公開・予約日時 | 過去/現在/将来の予定値でも下書きは生徒ホームに出ず、assignment直接URLから編集/提出不可。既存公開課題は従来どおり表示 |
| 4 | 認可境界 | 未ログイン、生徒、admin、別教師、機能無効/権限なし、権限取り消し、複数校の一校だけ権限あり、偽造school/class/task/child IDを拒否し内容を漏らさない |
| 5 | 競合・二重送信 | 同じ期待versionの2要求は一方だけ成功。新規token再送は同じ課題を指し追加行を作らない。公開/削除済みへ状態変更された課題の古いフォーム保存を拒否 |
| 6 | 途中失敗・監査 | 子項目/割当/監査の保存失敗は全体rollback。失敗ログと安全な失敗履歴が残り成功toastなし。履歴閲覧にも同じ認可を適用 |
| 7 | UIと入力 | プロトタイプ準拠、保存後編集継続、権限内ヒント参照、入力保持、XSS対策、shared feedback、取消/Escape/フォーカス復帰、狭幅ナビ。未実装操作は明示してサーバーでも拒否 |
| 8 | 既存機能回帰 | 生徒ホーム/エディター、標準ルーブリック、評価/課題別アンケート、演習、管理者ホーム/学校管理の表示・導線を維持。S1で新しい提出/評価/演習配信を生成しない |

### 環境と未実施時の扱い

- 対象はサーバー生成HTML。MySQL/Flyway付き専用環境、認証HTTP、ブラウザーの実操作を主検証とする。外部APIはS1では呼ばない。
- Docker/MySQLが使えない場合、入力単体テスト/静的確認までに限定し、トランザクション・SQL/制約・認可/競合は未確認とする。別DBやモックでMySQL受入を代替しない。
- ブラウザーが使えない場合、HTTP確認までとし、レイアウト・CodeMirror・モーダル・フォーカスは未確認とする。
- 合成ユーザー/学校/クラス/権限/課題はUUID等で識別し、作成した行だけを依存順に清掃する。実教師・生徒・研究データは使わない。
- 評価・アンケートの正式設定とユーザー手動E2Eは引き続き教師画面完成後。S1の回帰確認で工程7/8の保留全体を完了扱いにしない。

## 完了条件

- [x] S1の入力・画面導線が仕様とプロトタイプに対応し、未実装範囲を明示している。
- [x] 課題と割当の状態が下書き/非公開のまま保存され、日時到来やGETで公開されない。
- [x] 業務値・子項目・対象クラス・監査がDB保存後の再読込でも一致する。
- [x] Controlで現在の教師状態・作成者・機能/全対象校権限・期待versionを検証する。
- [x] 公開改訂・使用済み版・生徒の提出/評価/演習・過去履歴を変更しない。
- [x] 二重作成・部分保存・擬似成功・固定業務データ・秘密情報を残さない。
- [x] 最小テスト、DB、認証HTTP、ブラウザー、回帰確認の結果/skip/未確認を記録した。

## 要件対応（Requirement Mapping）

| REQ ID | 既存要件・範囲 | Plan Items | 実装完了の証拠 |
|---|---|---|---|
| REQ-001 | 教師機能権限・学校範囲・他教師更新拒否（機能仕様の教師共通、工程10） | 1.1, 2.2, 3.1, 3.2, 3.4, 4.1 | TeacherPermissionDao、TeacherTaskControl、拒否/取り消しテスト、権限に対応した導線 |
| REQ-002 | 課題の項目・テストケース・ヒント・予定の保存と再読込（課題編集機能） | 2.2, 3.1, 3.2, 3.3, 4.1 | TeacherTaskInput/Dao、task.jsp、DB再読込の値/順序一致 |
| REQ-003 | 下書き保存は非公開・予約公開も開始しない（課題状態 §3・6） | 1.1, 2.2, 3.1, 3.2, 4.1 | tasks/assignments状態、生徒一覧と直接URLの非公開確認 |
| REQ-004 | 公開改訂・課題系列・適用版・学習履歴の不変性（IC-001/007） | 3.1, 4.1 | TeacherTaskControlの下書き限定、公開/旧版の更新拒否、履歴不変確認 |
| REQ-005 | 保存競合を拒否し再読込を促す（課題編集機能、IC-001） | 2.1, 2.2, 3.1, 3.2, 4.1 | 新規migration、期待version照合、競合/二重要求のDB検証 |
| REQ-006 | 作成者/更新者・変更履歴・結果の記録/表示（教師共通追記） | 2.2, 3.1, 3.3, 4.1 | audit_logs、TeacherTaskAuditEntry、履歴モーダルと監査失敗rollback |
| REQ-007 | プロトタイプ準拠・プレビュー・shared feedback（画面遷移図、feedbackガイド） | 3.3, 3.4, 4.1 | task.jsp/CSS/JS、教師共通ナビ、ブラウザー確認 |
| REQ-008 | サーバー入力検証・CSRF・エスケープ・明示的エラー（実装手順 §5.6） | 3.2, 3.3, 3.4, 4.1 | TeacherTaskForm/Servlet、共通feedback、入力不正/失敗/未実装action確認 |
| REQ-009 | 保存後再読込・権限/状態境界・実施結果の記録（工程10完了条件、AGENTS） | 1.1, 4.1, 4.2 | 専用DB/認証HTTP/ブラウザー結果、本計画、ロードマップ、エラーレポート |

要件9件、Plan項目9件、タスク15件。[要件→計画](./checkpoints/teacher-task-draft/spec-to-plan.yaml)と[計画→タスク](./checkpoints/teacher-task-draft/plan-to-tasks.yaml)はS1の対応だけを扱い、工程10全体の網羅率として解釈しない。

## 計画作成時の確認結果

- `git --no-pager diff --check`: 成功。
- Ruby標準YAMLと明示的UTF-8で、要件9件/計画9項目/タスク15件の対応、チェックポイントの構文・上流参照、計画と引継ぎ資料のローカルリンク247件、末尾空白なしを確認した。
- 最初のRuby確認はUS-ASCII既定値で失敗した。`-EUTF-8:UTF-8`を指定して再成功。製品コード/DBには影響せず、[エラーレポート](../error-report.md)へ記録した。
- 今回は文書のみ。実装、migration、ビルド/単体テスト、稼働DB照合、認証HTTP/ブラウザー検証、外部API呼出し、commitは行っていない。

## 次にすること

1. S1のT001〜T015は完了。追加修正が必要になった場合も、共有DBは使わず専用合成環境で検証する。
2. 次はS2（ルーブリック/プロンプト準備・AI生成）の計画を具体化する。S3（公開・改訂・削除/復元）とS4（演習コード配信）は別スライスとして順に扱う。
3. プロンプト/配信と必要な正式設定が揃った後に、工程8の提出→評価→課題別アンケートのユーザー手動E2Eを再開する。今回の回帰確認を工程7/8全体の完了とは扱わない。

## 8080教師画面の遷移・外観整合（2026-10-04）

- 教師向けホーム画面と旧 `/teacher/home` route は削除し、アカウント管理機能が未実装の間は教師ログイン後に利用可能な課題編集画面へ遷移する。アカウント管理機能の実装後は画面遷移図どおり同機能へ遷移する。管理者ホームは `/admin/home` として分離する。
- 教師共通ナビのSVGアイコン・メニュー順・ヘッダー、教師ログインの外観、課題画面の余白とルーブリック導線をプロトタイプに合わせた。ルーブリックJSP fragmentはUTF-8を明示し、日本語表示を保つ。課題フォームの現行スライス（下書き作成/編集）を維持し、未実装の公開・削除・復元を擬似的に有効化していない。デザイン変更は本実装に必要な重なり順・レスポンシブ対応に限り、画面遷移図・プロトタイプ自体は変更していない。
- 8080で課題編集画面、ルーブリックの表示、旧教師ホームURLの削除を確認。画面操作・モバイル表示とテスト/WARの結果は同日付のエラーレポートに記録する。
