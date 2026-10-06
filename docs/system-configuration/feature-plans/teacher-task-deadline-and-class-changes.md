# 公開課題の期限延長・対象クラス追加 実装計画

## 目的と範囲

公開済み課題について、既存対象クラスの提出期限を不利益なく延長し、同じ学校の未割当クラスを独立した公開条件で追加できるようにする。課題内容・評価条件・既存提出/評価/コードログ履歴は変更しない。

## 必読資料

- [機能仕様書](../../function-specification.md)「課題編集機能」第129版
- [課題状態ルール](../../state-rules/teacher/task-state-rules.md)
- [DBテーブル定義](../../database-design/table-definitions.md) `tasks`, `task_class_assignments`, `task_participations`, `submissions`
- [実装契約](../implementation-contract.md) IC-001, IC-002, IC-003
- [課題公開計画](./teacher-task-publication.md)
- [公開後改訂計画](./teacher-task-revision.md)
- [実装手順](../implementation-and-local-development.md)

## データ・認可契約

- 両操作は課題が `published` で論理削除されていないこと、教師が有効で課題管理権限および課題所属学校への有効な権限を持つことをトランザクション内で確認する。クラス追加では追加対象クラスの学校一致とクラス権限も再確認する。作成者本人への限定は設けない。
- 操作ごとにrequest tokenを使い、成功済みtokenの再送では操作を重複実行しない。課題versionを楽観ロックし、成功ごとに1増やす。変更と成功監査は同一トランザクションで確定する。
- 期限延長は既存の `scheduled` / `published` / `expired` 割当に対して `due_at` のみを変更する。NULL（期限なし）は変更不可。新期限はDB現在時刻と現在の有限期限の両方より後にする。`expired` は期限延長成功時に `published` へ戻し、`scheduled` は維持する。公開日時、期限後提出方針、提出・評価履歴は変更しない。
- 対象クラス追加は過去に同じ課題の割当行がない同一学校のクラスに限定する。公開日時はNULL（即時）またはDB現在時刻より後、有限の提出期限はDB現在時刻および公開日時より後とする。新しいassignmentの状態は即時公開なら `published`、将来公開なら `scheduled`。期限後提出方針は教師が新規割当ごとに選択する。
- 失敗時は変更をロールバックし、成功表示を行わない。拒否条件・競合をControlで検証し、操作失敗は既存の監査/エラーログ方針に従う。

## 操作と画面

| 操作 | 入力 | 変更 | 成功後 |
|---|---|---|---|
| クラス別期限延長 | 課題ID/version、assignment ID、延長後期限、CSRF、request token | 選択割当の `due_at`、必要な場合は `expired -> published`、課題version、成功監査 | 完了通知後に同じ課題を再読込 |
| 対象クラス追加 | 課題ID/version、同一学校の未割当classroom ID、公開日時、提出期限、期限後提出方針、CSRF、request token | 新しいassignment行、課題version、成功監査 | 完了通知後に同じ課題を再読込 |

画面操作は既存のshared feedbackを使い、確認なしに既存クラスを外したり条件を上書きしたりしない。画面遷移プロトタイプは変更しない。

## 実装・検証タスク

1. [x] 機能仕様・状態ルール・DB定義・課題公開/改訂計画を照合し、期限なし・期限切れ再開・追加割当条件を明文化。
2. [x] Control/DAOで認可、version、状態、期限単調性、クラス重複、transaction、audit/idempotencyを実装。
3. [x] ServletでPOST操作を厳格にparseし、CSRF・状態通知・HTTPエラーを接続。
4. [x] 教師課題画面にクラス別期限延長とクラス追加フォームを接続し、shared feedbackを使用。
5. [x] focused testsおよび専用MySQL統合testsを追加・実行。期限短縮、期限切れ再開、別学校/既割当クラス、冪等再送、履歴保持、期限切れassignmentのstale version拒否を確認。期限なし・version競合の拒否条件もControl/DAOで検証。
6. [x] 合成教師・課題の隔離fixtureで、認証browserから期限延長とクラス追加を実施。Gemini実API/実利用者データは不使用。
7. [x] 追加割当候補からarchivedを含む全過去割当クラスを除外する履歴データを画面へ接続し、専用DB統合testで確認。
8. [x] `git diff --check` と変更記録を確認し、ロードマップ/AGENTS/本計画を更新。

## 検証記録

- `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home PATH=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home/bin:$PATH gradle test --no-daemon` — `BUILD SUCCESSFUL`。追加したServlet parser/Control guardのJUnitを含む。
- 専用MySQL `ppe_teacher_task_test_deadline_archived` — Flyway V1〜V19適用・検証後、`TeacherTaskDatabaseTest` は31 tests / 27 passed / 0 failed / 4 browser-fixture skipped。期限切れ割当の再開、提出履歴保持、期限短縮/stale version拒否/重複/他校クラス拒否、対象クラス追加、監査、同一request token再送、archived割当クラスの候補除外用履歴を確認。
- 同じ専用Compose projectでV1〜V19を適用し、ブラウザーで合成教師としてログイン。期限延長後の期限値と完了通知、対象クラス追加後の割当表示・完了通知を確認した。操作後、追加フォームは既割当クラスを候補から除外した。
- `curl -sS -o /dev/null -w 'teacher task HTTP %{http_code}\n' http://127.0.0.1:18082/teacher/task` — 未認証で期待どおり `302`（教師ログインへredirect）。認証後の画面表示・POST・redirectはbrowserで確認。
- Java 21 focused Control/Servlet/DB-test compilation command と `git diff --check` — 成功。ホストに `node` がないため `node --check` は実行不可。変更したtask.jsはブラウザー上で読み込まれ、feedback toastを含む操作が実行された。最終コード差分ではServletの画面候補用履歴データも追加し、DB統合testで取得を確認した。
- 初回の専用DB migrationはV11 routine作成時のMySQL error 1419で停止した。専用Compose project/schemaのみ破棄して作り直し、migration時だけ `log_bin_trust_function_creators=1` を設定してV1〜V19を適用。検証後、専用Compose project、MySQL/Gradle volume、networkを削除した。共有DB/Composeは変更していない。
