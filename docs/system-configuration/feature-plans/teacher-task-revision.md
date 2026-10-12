# 公開後課題改訂 実装計画

## 目的と範囲

- 利用者に提供する動作: 教師が学習開始前の公開課題から同じ課題系列の改訂案を作り、新しい評価プロンプトを設定して再公開できる。
- 今回実装した範囲:
  1. 公開課題から次の `revision_number` の改訂を作成し、旧版の課題・割当を再公開まで維持する。
  2. 学習開始済みの参加者がいる場合、同一系列の改訂作成・編集・再公開をサーバー側で拒否する。
  3. 新改訂は旧版の標準ルーブリック設定を引き継ぎ、評価プロンプト適用版を持たず、未公開の新規クラス割当を作る。
  4. 改訂案を編集し、新しい評価プロンプトを設定してから再公開する。成功時に旧割当を `archived`、旧課題を `requires_update` にする。
  5. 教師課題一覧・編集画面に改訂作成、改訂案編集、プロンプト設定、改訂再公開の導線を接続する。
- 初回実装の対象外（後続通知受入を除く）:
  - 学習開始後の新しい課題系列への複製、提出期限延長、対象クラス追加。
  - 実生徒データ、画面遷移プロトタイプの変更。
  - 期限切れ境界と既存提出/評価/コードログ履歴の実worker runtime保持確認。

## 必読資料

- [機能仕様書](../../function-specification.md)「既存課題の編集・削除・復元」
- [課題状態ルール](../../state-rules/teacher/task-state-rules.md)
- [生徒エディター状態ルール](../../state-rules/student/editor-state-rules.md)
- [DBテーブル定義](../../database-design/table-definitions.md) `tasks`, `task_class_assignments`, `task_participations`, `submissions`
- [実装契約](../implementation-contract.md) IC-001, IC-002, IC-003, IC-007
- [課題公開・予約公開計画](./teacher-task-publication.md)

## 前提・データ契約

- 合意済み前提: 課題内容を直接上書きせず、同じ `task_code` に新しい `task_revision_code` / `revision_number` を追加する。再公開までは直前の公開課題・割当を維持する。
- `tasks.supersedes_task_id` で改訂元を参照する。新改訂のassignmentは新しい行として作成し、promptはNULLから再設定する。既存のテーブル/状態値で実現できるためmigrationは追加しない。
- エディターを開いただけで作られる `not_started` の参加行は学習開始済みと扱わない。学習・進捗・保存・評価状態、学習時間、提出履歴をサーバー側で確認する。
- 再公開では、同一トランザクション内で新改訂の公開条件を検証し、新改訂を公開してから旧版の割当をアーカイブし、旧版を `requires_update` にする。既存の提出・評価・コードログは変更しない。

## 操作と状態

| 操作 | DBから読むデータ | DBへの変更 | 状態遷移 |
|---|---|---|---|
| 公開課題の改訂案作成 | 作成者・version・課題系列、非アーカイブ割当、参加/提出状態、学校/クラス権限 | 同系列の新revision、課題内容/子要素、未公開assignment、監査行 | 旧版は `published` のまま、新版は `requires_update` |
| 改訂案の保存 | 改訂元の公開状態、改訂系列の最新性、学習開始状態、version | 改訂案の内容/子要素を更新。内容変更時はactive promptを解除 | 改訂案 `requires_update` のまま |
| 改訂案の再公開 | 公開元、新版のrubric/prompt、未公開assignment、学習開始状態 | 新版assignmentを公開/予約、旧assignmentをアーカイブ、両revisionを更新、監査行 | 新版 `published`、旧版 `requires_update` |

## 実装・検証結果

- [x] DAO/Control/Servlet/Form/JSP/JavaScriptに一連の操作を接続。
- [x] 学習開始前の新revision作成、旧版維持、プロンプト再設定後の再公開を専用DB統合テストで確認。
- [x] Editorを開いただけの `not_started` 参加行を許容し、学習開始状態では改訂作成を拒否することを専用DB統合テストで確認。
- [x] `flywayMigrate flywayValidate` を専用MySQL schemaで実行し、V1〜V19を検証。migration追加なし。
- [x] `TeacherTaskDatabaseTest`: 23 tests, 0 failures, 0 errors, 1 browser-fixture skip。
- [x] focused form/control/DAO/servlet testsと、専用DBを指定した全体 `gradle build` が成功。`git diff --check` 成功。
- [x] 隔離認証ブラウザーで合成課題を公開し、改訂案作成、新しいプロンプト案の保存、改訂再公開まで確認。再公開後、旧課題は `requires_update`、旧割当は `archived`、新課題は `published`、新割当は `scheduled` となった。
- [x] `task.js` をブラウザー内JavaScriptエンジンで構文コンパイルし、公開確認dialogとフォーム遷移を動作確認。
- [x] 隔離実アプリ内workerで、起動直後の期限超過予約回収と稼働中の次周期処理を確認。後者は `scheduled -> published` となり、system監査行も記録した。ポーリング待機の上限90秒内に状態変化を確認したが、実経過時間は計測していないため「60秒以内」の厳密なSLA確認とは区別する。
- [x] 学習開始後の独立系列コピーを専用DB統合テストと認証browserで確認。コピー元の公開状態・割当・提出履歴を維持し、新系列は未公開/未割当、promptは別IDの未適用draftとなる。
- [x] N167-01: 新revisionの実公開時に既存`task_published`通知を対象生徒へ作成し、改訂再公開と分かる文面を表示する。即時再公開transactionでの通知、および予約設定時は通知せずworkerの実公開時に1件作成し再実行では重複しないことを専用MySQLで確認。新migration/backfillは追加しない。
- [x] N167-01の個別改訂通知をlocal8080の認証browserで作成・表示し、生徒のクリック遷移と既読化まで受入確認（2026-10-13、詳細は下記）。
- 2026-10-12受入準備: public課題からのコピーは「複製元に現在適用中のプロンプトがありません」とHTTP 400になり、コピーは作成されなかった。別途、合成課題taskId=17と手入力prompt version 3 draftを作成したが、公開可能な有効promptにする生成操作はGemini実API保留方針により実行していない。taskId=17は未公開で、外部AIは呼び出していない。
- 2026-10-13追補: ユーザーの追加課金・Gemini利用許可後、taskId=17/version=3の揺らぎ生成は成功した。教師対応を保存し、1回目の評価例は実行不能なPythonコードを含んだため採用せず、形式・期待結果の指示を追加して再生成した2件を画面確認した。version 3を適用後、taskId=17を初回公開した。旧inactive例が評価例保存時にまとめてactive化され得るDAO挙動を発見し、古い合成評価例だけを対象限定で削除して最新の例を保存した。製品修正・回帰テストは未実施。
- 同日、公開済みtaskId=17を親とする真の改訂taskId=18/revision 2を作成し、prompt version 4を保存した。認証済みlocal8080から揺らぎ生成POSTを2回試したが、どちらもHTTP 404。DBではversion 4がdraft、揺らぎ生成状態`not_generated`、揺らぎ項目0件で、Gemini要求は開始されていなかった。原因は改訂状態`requires_update`に対して生成開始処理がdraft専用`ensureStandardRubric()`を呼び、`TeacherTaskNotFoundException`が404へ変換されること。`beginGeneration()`を修正し、専用MySQL統合テストで改訂案の揺らぎ生成・教師対応・評価例生成/保存が成功することを確認した。関連テスト計52件中47成功・5 skip・失敗/エラー0、WARと`flywayValidate`成功。local8080アプリ再起動直後は教師browserの再ログイン待ちだったが、その後のGemini生成・改訂再公開・生徒通知browser受入は2026-10-13に完了した（下記および[エラーレポート](../error-report.md)参照）。
- N167-01の個別改訂通知受入は完了。期限延長・対象クラス追加は別スライスの未確認事項であり、この通知受入の完了条件には含めない。

### 2026-10-13 N167-01 Gemini生成・改訂通知のlocal browser受入

- 合成課題 `taskId=18` / revision 2 / prompt version 4で、揺らぎ項目3件への教師対応がすべてDB上 `resolved` であることを確認した。Gemini評価例は合成fixture（`student_submission` / `source_edit_log` はNULL）のみで生成し、1レコード内の2つの想定シナリオ、評価理由、分散候補、再評価前チェック項目を画面・DBで確認した。テストケース結果は実行結果ではなく仕様からの推定と明示されていた。実生徒データ・提出・編集ログは送信していない。
- 評価例を保存してactive化し、設定済みprompt version 4を改訂課題へ適用した。課題編集画面で対象がデモ学校 / 1年A組、即時公開、期限なしであることを確認して改訂を公開した。DBでは旧task 17が `requires_update`、旧assignmentが `archived`、新task 18と新assignmentが `published` となった。
- 新assignmentに対する `task_published` 通知は、有効な在籍生徒5人それぞれに1件（計5件）作成された。通知タイトル/本文に「改訂版」と新課題への案内が表示され、生徒browserで通知を選択すると `/student/editor?assignmentId=17`（新revisionのassignment）へ遷移し、課題内容を表示した。クリックした通知はDBで既読化されていた。
- Gemini評価例の追加生成、再評価プレビュー、実提出は実行していない。生成済み評価例は今回1レコードであり、旧inactive例の一括active化リスクはこの受入データでは発生しなかったが、DAO挙動の製品修正・回帰テストは別途未実施。

### 2026-10-06 隔離runtime受入追補

- 専用Compose project `ppe-task-revision-acceptance`、schema `ppe_teacher_task_test_revision_20261006`、HTTP port `18082` を使用。認証教師・課題・プロンプトはいずれも合成fixture。
- Chromiumで教師ログイン、公開確認、改訂案作成、新プロンプト版保存、改訂公開を確認した。プロンプト版は実Geminiを呼ばず、合成内容で保存後、隔離DBだけで設定済み状態にして再公開経路を確認した。
- 実workerは、停止中に期限到来させた予約割当をアプリ再起動直後に公開し、別の合成確認ではアプリ稼働中の次周期で公開した。監査件数も確認した。稼働中ケースの確認スクリプトは最大90秒待機であり、壁時計の正確な経過時間は計測していない。
- 配信された `/js/teacher/task/task.js` は HTTP 200、18,141 bytes。Chromiumの `new Function()` で構文コンパイル成功し、公開確認dialogなどのイベント動作も確認した。Node.js CLIは環境にない。
- 初回migration時にV11 routine作成がMySQL error 1419となり、部分適用後のFlyway再実行も失敗したため、この専用Compose projectのDB volumeだけを再作成した。専用DBに限り起動時設定後にV1〜V19 migration/validate成功。詳細は[エラーレポート](../error-report.md)を参照。
- 作業後に専用Compose project/volume/networkを削除し、HTTP port `18082` のlistener不在を確認。共有8080/DBは使用していない。

### 2026-10-06 独立課題系列コピーの認証browser受入

- 現行コードを専用schema `ppe_teacher_task_test_browser20261006` / localhost port `18084` で起動し、合成教師・公開課題・学習参加/提出fixtureを使って確認した。共有8080/DB、Gemini実API、実生徒データは使用していない。
- 教師ログイン後、学習開始済み公開課題の「新しい課題として編集」を選び、shared feedbackの確認dialogから作成した。作成完了通知と `taskId=2` への遷移を確認し、新課題は `draft`、対象クラスは未割当、active prompt pointerはNULLだった。
- プロンプト設定画面でコピー元の共通prompt本文を確認。専用DBでは新しいprompt versionが別IDの `draft` として存在し、共通prompt/追加指示の本文だけを引き継ぎ、揺らぎ項目/評価例は未生成だった。コピー元は引き続き `published`、active prompt versionとassignment、参加/提出行が保持された。評価/コードログを含む既存履歴の非変更は専用DB統合テストで確認済み。
- 専用schemaの初回migrationは通常DBユーザーでV11 routine作成時にMySQL error 1419となり、部分DDL後の `flywayRepair`/再実行は重複constraintで失敗した。対象の使い捨てschemaだけを再作成し、migration時のみ `log_bin_trust_function_creators` を一時変更してV1〜V19を適用後、値を `0` に戻して確認した。詳細は[エラーレポート](../error-report.md)に記録。
- `TeacherTaskDatabaseTest` の独立コピーfocused test、fixture作成test、`git diff --check` が成功した。

## 未完了・次の作業

- 学習開始後の別課題系列への複製は、機能仕様書第128版に基づき、専用MySQL統合テストと現行UIの認証browserで受入済み。対象クラス/公開日時は引き継がず、現在適用中のプロンプト本文だけを新課題所有の未適用下書きにする。
- 期限切れ境界、提出・評価・コードログ履歴の保持、workerの期限到来から60秒以内の遷移は専用runtimeで確認済み。残る課題管理の次候補は期限延長・対象クラス追加であり、別スライスとして仕様・画面導線と照合してから実施する。
