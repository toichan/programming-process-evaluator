# 公開後課題改訂 実装計画

## 目的と範囲

- 利用者に提供する動作: 教師が学習開始前の公開課題から同じ課題系列の改訂案を作り、新しい評価プロンプトを設定して再公開できる。
- 今回実装した範囲:
  1. 公開課題から次の `revision_number` の改訂を作成し、旧版の課題・割当を再公開まで維持する。
  2. 学習開始済みの参加者がいる場合、同一系列の改訂作成・編集・再公開をサーバー側で拒否する。
  3. 新改訂は旧版の標準ルーブリック設定を引き継ぎ、評価プロンプト適用版を持たず、未公開の新規クラス割当を作る。
  4. 改訂案を編集し、新しい評価プロンプトを設定してから再公開する。成功時に旧割当を `archived`、旧課題を `requires_update` にする。
  5. 教師課題一覧・編集画面に改訂作成、改訂案編集、プロンプト設定、改訂再公開の導線を接続する。
- 今回対象外:
  - 学習開始後の新しい課題系列への複製、提出期限延長、対象クラス追加。
  - Gemini実API、実生徒データ、共有DB/8080、画面遷移プロトタイプの変更。
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
- [ ] 期限切れ境界、既存提出/評価/コードログ履歴のruntime保持、学習開始後の別課題系列複製、期限延長/対象追加は未確認。共有8080/DB、Gemini実API、実生徒データは使用していない。

### 2026-10-06 隔離runtime受入追補

- 専用Compose project `ppe-task-revision-acceptance`、schema `ppe_teacher_task_test_revision_20261006`、HTTP port `18082` を使用。認証教師・課題・プロンプトはいずれも合成fixture。
- Chromiumで教師ログイン、公開確認、改訂案作成、新プロンプト版保存、改訂公開を確認した。プロンプト版は実Geminiを呼ばず、合成内容で保存後、隔離DBだけで設定済み状態にして再公開経路を確認した。
- 実workerは、停止中に期限到来させた予約割当をアプリ再起動直後に公開し、別の合成確認ではアプリ稼働中の次周期で公開した。監査件数も確認した。稼働中ケースの確認スクリプトは最大90秒待機であり、壁時計の正確な経過時間は計測していない。
- 配信された `/js/teacher/task/task.js` は HTTP 200、18,141 bytes。Chromiumの `new Function()` で構文コンパイル成功し、公開確認dialogなどのイベント動作も確認した。Node.js CLIは環境にない。
- 初回migration時にV11 routine作成がMySQL error 1419となり、部分適用後のFlyway再実行も失敗したため、この専用Compose projectのDB volumeだけを再作成した。専用DBに限り起動時設定後にV1〜V19 migration/validate成功。詳細は[エラーレポート](../error-report.md)を参照。
- 作業後に専用Compose project/volume/networkを削除し、HTTP port `18082` のlistener不在を確認。共有8080/DBは使用していない。

## 未完了・次の作業

- 学習開始後の別課題系列への複製は、機能仕様書第128版に基づく独立した非公開下書き作成として実装し、専用MySQL統合テストでコピー元の保持・学習開始前の拒否・冪等性を確認した。対象クラス/公開日時は引き継がず、現在適用中のプロンプト本文だけを新課題所有の未適用下書きにする。独立コピーの本番UIブラウザー受入は未確認。
- 期限切れ境界、提出・評価・コードログ履歴の保持、workerの期限到来から60秒以内の遷移は専用runtimeで確認済み。残る課題管理の次候補は期限延長・対象クラス追加であり、別スライスとして仕様・画面導線と照合してから実施する。
