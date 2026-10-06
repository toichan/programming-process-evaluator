# 教師課題の公開・予約公開 実装計画

## 目的と範囲

- 利用者に提供する動作: 権限を持つ教師が保存済み課題を明示的に公開し、即時またはクラス別の指定日時に生徒が参照できる。公開後は提出期限に応じて割当状態を遷移させる。
- 今回実装する範囲:
  1. 課題下書きの「保存・公開」。標準ルーブリックと適用済みプロンプト版、対象クラス、公開日時・提出期限をサーバー側で検証し、課題本体と割当を一括して公開状態へ遷移させる。
  2. アプリ内定期処理による予約割当の公開と提出期限到来後の `expired` 遷移。1分間隔でDB時刻を基準に処理し、重複実行・再起動後の追いつきを許容する。
  3. 期限切れ後の提出を `late_submission_policy` で判定する。`allow` は未提出者の初回提出のみ許可し、`deny` と再提出はいずれも拒否する。
  4. 操作、状態、監査記録の専用テストとアプリ起動・終了ライフサイクルの確認。
- 今回対象外:
  - 公開済み課題の新改訂作成・再公開、学習開始後の別課題系列への複製、期限延長・対象クラス追加の管理UI。
  - Gemini実API、実提出・実利用者データ、共有8080/共有DB。
  - 画面遷移図・画面プロトタイプの変更。既存画面のレイアウトを維持し、課題本実装だけに操作状態を追加する。

## 必読資料

- [機能仕様書](../../function-specification.md)「課題編集機能」および改版履歴第127版
- [実装ロードマップ](../implementation-roadmap.md)工程10
- [課題状態ルール](../../state-rules/teacher/task-state-rules.md)
- [生徒エディター状態ルール](../../state-rules/student/editor-state-rules.md)
- [DBテーブル定義](../../database-design/table-definitions.md) `tasks`, `task_class_assignments`, `task_participations`, `submissions`
- [実装契約](../implementation-contract.md) IC-001, IC-002, IC-003, IC-007
- [実装手順](../implementation-and-local-development.md) §5.4, §5.6
- [教師課題S1計画](./teacher-task-draft.md)、[教師課題S3計画](./teacher-task-s3.md)、教師課題/生徒Editorの既存Servlet・Control・DAO・テスト

## 前提・未決事項

- 合意済み前提:
  - 課題本体とクラス別割当の公開状態を分離する。
  - 将来公開日時の予約割当はアプリ内定期処理が1分間隔で処理し、指定日時から最大約1分以内に反映する。
  - 期限到来後の割当は `expired` にする。`allow` は初回提出のみ許可し、再提出は期限後に認めない。
  - 処理時刻はDB時刻を正本とする。定期処理を複数回実行しても二重遷移・履歴の破壊を起こさず、アプリ再起動後は期限到来済みの対象を再確認する。
  - 課題公開は課題管理機能権限・有効な教師・作成者・所属学校・学校内クラスをサーバー側で検証し、状態とversionを再検証する。
  - 公開条件として標準ルーブリックと有効な設定済みプロンプト版を要求する。Gemini実API呼び出しは行わない。
- 実装契約上の関連 ID: IC-001（課題状態）、IC-002（参加状態）、IC-003（提出状態）、IC-007（プロンプト版）。
- 未決事項・影響:
  - 公開後の改訂は、同一系列の改訂作成・旧割当保持・プロンプト再設定を含む独立スライスとして続ける。学習開始後の新課題系列への複製、期限延長、対象クラス追加は今回実装しない。編集画面から公開課題の編集を成功させる擬似動作は設けない。

## ユースケースとデータ契約

| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 下書き保存・公開 | 有効な教師、課題管理機能権限、作成者、課題所属校/クラスの有効な権限 | CSRF、request token、task id/version、課題フォーム | 課題と関連項目、標準ルーブリック、適用プロンプト版、学校/クラス権限 | 1 transactionで課題フォーム更新、即時/予約割当状態、監査行を保存 | task `draft -> published`; assignment `not_published -> published/scheduled` | 完了後に一覧へ戻り状態を再読込。条件不成立・権限外・競合・DB障害は成功表示なし |
| 予約公開処理 | system worker | なし。DB時刻 | due `scheduled` assignments, parent task状態/削除状態/公開条件 | 条件一致割当のみ状態更新。system監査記録を保存する方式は既存監査契約に沿う | `scheduled -> published` | 失敗をログに記録し次周期に再試行。成功は生徒一覧/Editorの再読込に反映 |
| 期限切れ処理 | system worker | なし。DB時刻 | due `published` assignments, parent task状態 | 状態を `expired` に更新 | `published -> expired` | 冪等。既存提出/評価行は変更しない |
| 期限後初回提出 | 生徒本人、利用可能な割当 | 提出チェックticket/提出request | 割当期限状態・提出方針・参加/最新提出・課題公開状態 | 既存の提出transactionを維持 | expired + allow + 初回なら提出受理。deny/再提出なら拒否 | 拒否理由を明示し提出履歴を作らない |

## 実装タスク

1. [x] 公開操作を入力検証・CSRF・version競合つきで追加し、保存と公開を同一transactionにする。
2. [x] DAOに公開可否検証、assignment状態更新、状態監査記録、期限到来の冪等遷移を追加する。workerは親課題から割当の順でロックする。
3. [x] workerを `DataSourceLifecycleListener` の起動/停止へ接続し、1分周期・起動直後の回収・例外時ログを実装する。
4. [x] 生徒課題一覧/Editor取得を、期限切れかつ `allow` かつ未提出の初回利用者に限って継続できるようにする。提出確定時はDBロック下で再検証する。
5. [x] 教師JSPに既存の課題フォーム/一覧へ公開操作状態とクラス別公開状態を反映し、shared feedbackを利用する。
6. [x] unit/servletテストと全体ビルドを完了。専用合成DBにV1〜V19を適用してvalidateし、公開workerの並行実行・監査、期限切れ後の初回提出/再提出拒否、deny時のアクセス拒否を含むDB統合テスト20件が成功した。
7. [x] 隔離認証ブラウザーで合成教師が下書きを予約公開し、確認ダイアログ、完了通知、一覧の「公開予約」を確認。秒を含む保存日時が既定stepで無効になる問題を発見し、日時入力を秒精度 (`step=1`) に修正して送信可能であることを確認した。
8. [x] ブラウザーのJavaScriptエンジンで配信された `task.js` をparser検証した。
9. [x] 実workerの期限切れ処理、期限前の非遷移、期限到来時の遷移、二重処理防止、および期限切れ後の提出/評価/コードログ保持を専用runtime/DBで確認した。起動直後・再起動後の予約回収と稼働中の次周期処理も別途確認済み。
10. [x] 稼働中workerの期限到来から状態遷移までを専用runtimeで計測し、60秒以内であることを確認した。
11. [x] 公開後改訂・学習開始後複製・期限延長/対象追加を次の独立スライスとしてロードマップに残し、既存仕様との照合対象を記録する。改訂機能自体は本スライスに含めない。

## 実装状況

- 公開・予約公開・期限切れ・期限後初回提出のコードスライスを実装済み。公開後改訂と学習開始後の別系列複製は未実装。
- focused JUnit tests、`TeacherTaskPublicationWorkerTest`、全体 `gradle build` は成功。詳細なコマンドは [エラーレポート](../error-report.md) を参照。
- 専用合成DBのV1〜V19 migration/validate、期限前・境界到来・重複処理のDB統合テスト、期限切れ後の初回提出制約と履歴保持、予約公開フォームおよび課題改訂の隔離認証ブラウザー受入、ブラウザーJavaScript parser確認は成功。実worker runtimeで期限超過後の `published -> expired` とsystem監査1件を確認し、提出・評価・コードログ各1件が維持された。別の専用runtime計測では期限到来からworker遷移の初回観測まで21.646秒で、60秒以内だった。

## 変更予定ファイル

- 作成: `src/main/java/control/teacher/TeacherTaskPublicationWorker.java` と対応するworker/DAO/entity tests。
- 変更: `TeacherTaskControl`, `TeacherTaskDao`, `TeacherTaskServlet`, `TeacherTaskForm`, 教師課題JSP/JS/CSS、`DataSourceLifecycleListener`、`StudentDao`, `StudentEditorDao`, `StudentEditorControl` と関連テスト。
- DB migration: 必要が確認された場合に限り追加する。既存カラムの状態値・版フィールドで実現できる場合は不要なmigrationを加えない。
- プロトタイプから再利用する表示資産: 現行課題フォーム、公開期間/期限入力、shared teacher UI/feedback。プロトタイプの固定データ・擬似動作は移植しない。
- プロトタイプから除去する仮データ・擬似動作: 本実装に現在含めない。

## 検証計画

- ビルド / テストコマンド: `docker compose exec -T app gradle test --tests 'control.teacher.TeacherTask*Test' --tests 'control.student.StudentEditorControlTest' --no-daemon --console=plain --warning-mode all`; 専用DBが明示的に確認できる場合のみ `TEACHER_TASK_DB_TEST=true` で `TeacherTaskDatabaseTest` を実行する。最後に `docker compose exec -T app gradle build --warning-mode all`。
- 正常系: 即時公開、将来予約、期限なし、deadline allow/deny、DB再読込。
- 状態遷移・操作可否: draft publish、scheduled publish、published expired、retry/restart catch-up、論理削除・下書き・旧revisionを誤公開しない。
- 権限境界・直接 URL: 未ログイン、生徒、admin、別教師、機能/学校/クラス権限なし、偽造ID、権限取り消し。
- DB保存後の再読込: 課題/割当状態、公開日時、期限、監査、既存提出/評価履歴を別Connectionから確認。
- 異常系: missing rubric/prompt, empty class assignment, invalid schedule, stale version, simultaneous workers, SQL failure/rollback.
- 未実施時に残る未確認事項: Docker/MySQL/ブラウザーが使えない場合はその層の受入を未確認として報告する。共有DB接続・migration・再起動は禁止。

## 完了条件

- [ ] 機能仕様・課題/Editor状態ルールとDB状態値に合致する
- [ ] 業務データはDBを正本として読み書きする
- [ ] Control/DAOでロール・学校/クラス範囲・状態・versionを再検証する
- [x] 予約処理と期限処理が専用DB受入で冪等と確認される
- [x] 稼働中workerの期限到来後の処理遅延が最大約1分以内であると壁時計計測で確認される
- [x] 期限後 `allow` が専用DB受入で未提出者の初回だけ許可され、再提出は拒否されると確認する
- [x] 専用DB/runtime受入で提出/評価/ログ履歴を上書き・削除しないと確認する
- [ ] 仮データ・擬似成功処理・秘密情報を残さない
- [x] 実行した確認結果と未確認事項を記録する
