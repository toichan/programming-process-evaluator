# テーブル定義

> 主キーは `BIGINT AUTO_INCREMENT`、日時は原則として `DATETIME` とする。外部キー制約は備考欄に参照先を記載する。

## 削除・保持ポリシー

業務データとマスターデータは原則として物理削除せず、テーブル内に保持する。削除方法はデータの責務に応じて次の3種類に分ける。

|区分|対象|削除・保持方法|復元|
|:--|:--|:--|:--|
|復元可能な論理削除|users、tasks、student_exercise_entriesなど、利用者が削除・復元するルートまたは独立データ|`deleted` / `archived` 等の状態と `deleted_at` を正本とする。削除実行者・復元実行者・変更前後は `audit_logs` に記録する。対象自身に `deleted_by_user_id` がある場合は併用する|データ種別ごとの状態ルールに従い、関連データを保持したまま復元する|
|無効化・アーカイブ|schools、classrooms、所属、権限、課題子項目、ルーブリック、配信テンプレート、アンケート定義など|既存の `inactive` / `disabled` / `archived` / `retired` 状態を論理削除または利用停止として扱う。操作は `audit_logs` に記録する|参照整合性と権限を検証し、許可されたものだけ有効状態へ戻す|
|追記型・通常削除不可|提出、コードログ、コード実行、評価、同意、アンケート回答、認証・監査・エラー履歴、AI依頼・応答・入力スナップショット|通常の削除操作を提供せず履歴として保持する。訂正は新しい行や状態遷移で表し、過去行を物理削除・上書きしない|復元操作の対象外。保存期間満了や個人情報対応は別の管理手順で物理削除または匿名化する|

### テーブル別分類

|区分|テーブル|
|:--|:--|
|復元可能な論理削除|users、tasks、student_exercises、student_exercise_entries|
|無効化・アーカイブ|schools、classrooms、student_class_memberships、teacher_school_permissions、teacher_feature_permissions、task_features、task_class_assignments、task_test_cases、task_hints、rubrics、rubric_dimensions、rubric_criteria、criterion_levels、distribution_templates、distribution_template_files、consent_document_versions、surveys、survey_questions、survey_question_options|
|追記型・通常削除不可|password_reset_records、credential_history、task_participations、task_activity_sessions、submissions、code_logs、code_executions、code_execution_test_results、prompt_versions、prompt_fluctuation_items、evaluation_examples、evaluations、evaluation_scores、evaluation_dimension_results、evaluation_reasons、evaluation_reason_details、evaluation_evidence、evaluation_feedback、reevaluation_jobs、distributions、distribution_targets、distribution_histories、consent_records、survey_responses、survey_answers、evaluation_requests、evaluation_responses、evaluation_input_snapshots、login_history、audit_logs、application_error_logs、research_subject_identifiers|

共通ルール:

- 通常の一覧・検索・業務処理では、論理削除・無効・アーカイブ状態を明示的に除外する。削除済み一覧は権限を持つ利用者だけが参照できる。
- 親レコードの論理削除を理由に子レコードを連鎖物理削除しない。外部キーへ `ON DELETE CASCADE` を設定しない。
- 論理削除・復元は業務行の状態更新と `audit_logs` の記録を同一トランザクションで行う。
- 一意制約は論理削除後の識別子再利用方針を個別に決める。ログインID、課題コード、外部IDなど履歴参照に使う識別子は原則再利用しない。
- 物理削除または匿名化を行う管理手順では、対象範囲、理由、実行者、実行日時、関連データへの影響を監査記録へ残す。

## users

生徒・教師・管理者の共通アカウント情報

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|user_id|ユーザID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_type|ユーザ種別|ENUM('student','teacher','admin')||NO||管理者は1アカウントのみ。DBの一意制約で保証|
|login_id|ログインID（利用者向けIDの正本）|VARCHAR(64)||NO|UNIQUE|生徒ではstudent_id、教師ではteacher_idと呼ぶ。同一値を認証・画面・CSVで使用し、ロール別の重複列は追加しない|
|password_hash|パスワードハッシュ|VARCHAR(255)||NO||平文パスワードを保存しない|
|display_name|表示名（生徒では旧互換値）|VARCHAR(100)||NO||生徒は画面表示に使わず、今後の作成処理ではlogin_idを設定して別名を要求しない。教師の利用者向けIDにも使わない。管理者表示名と既存値は保持し、匿名化の除去対象から外さない|
|account_status|アカウント状態|ENUM('active','suspended','deleted')||NO|||
|consecutive_login_failures|連続ログイン失敗回数|TINYINT UNSIGNED||NO||0〜5。成功ログイン・30分ロック満了・教師解除で0に戻す|
|login_locked_until|ログインロック期限|DATETIME||YES||5回連続失敗後、現在時刻から30分。期限満了後は再試行可能|
|created_by_user_id|作成者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|updated_by_user_id|更新者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|deleted_at|削除日時|DATETIME||YES||論理削除|

## student_profiles

生徒固有のアカウント属性

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|user_id|ユーザID|BIGINT|〇|NO|PRIMARY_KEY, FOREIGN_KEY|users.user_id|
|student_code|旧生徒コード（互換用）|VARCHAR(32)||NO|UNIQUE|利用者向けIDの正本ではない。既存データ・関連する匿名化処理の互換性のため保持し、アカウント画面・一覧・CSVには使用しない。将来の登録処理でも独立した生徒IDとして入力させない|
|school_id|所属学校ID|BIGINT||YES|FOREIGN_KEY|schools.school_id。旧所属なしデータのみNULLを許容。所属学校は固定し、新規業務登録では指定する|
|security_level|セキュリティレベル互換値|TINYINT||NO||学校設定をDBトリガーで適用し、個別上書き不可。旧所属なしデータのみ従来値を保持|
|first_login_status|初回ログイン状態|ENUM('not_logged_in','password_change_required','completed')||NO|||
|must_change_password|次回ログイン時変更要否|BOOLEAN||NO||リセット後の変更強制にも利用|

## user_editor_preferences

生徒アカウントごとのエディター表示・入力設定。端末を変更しても同じ設定を使用する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|user_id|ユーザID|BIGINT|〇|NO|PRIMARY_KEY, FOREIGN_KEY|users.user_id|
|font_size_px|コード文字サイズ|TINYINT UNSIGNED||NO|CHECK 10〜24|ピクセル単位|
|line_wrapping|コード折り返し|BOOLEAN||NO|||
|indent_width|インデント幅|TINYINT UNSIGNED||NO|CHECK IN (2,4)|Tabキー入力時のスペース数|
|editor_theme|エディターテーマ|ENUM('dark','light','high_contrast')||NO|||
|updated_at|更新日時|DATETIME(6)||NO|||

## research_subject_identifiers

Gemini送信時に使用する、ログイン用IDとは別の安定した研究用識別子。アンケート研究データでは `users.user_id` をそのまま研究用識別子として使う方針のため、本テーブルをアンケート回答の出力ID変換には使用しない。ログイン用ID（`student_profiles.student_code`）は教師の閲覧範囲内で可視のまま維持する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|research_subject_identifier_id|研究用識別子ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_user_id|生徒ユーザID|BIGINT||NO|FOREIGN_KEY, UNIQUE|student_profiles.user_id。1生徒につき1件|
|research_subject_code|研究用識別子|VARCHAR(64)||NO|UNIQUE|氏名・出席番号・ログイン用IDから推測できない値。生成後は変更しない|
|generated_at|生成日時|DATETIME||NO|||
|generated_by_user_id|生成実行者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id。システム自動生成の場合はNULL|

Geminiへの送信データでは、`student_profiles.student_code` や氏名等の個人識別情報を含めず、本テーブルの `research_subject_code` を学習者識別子として使用する。`evaluation_input_snapshots.anonymized_subject_id` はリクエスト単位の匿名化カラムであり、Gemini送信で継続識別が必要な場合の安定IDとして本テーブルを正本とする。アンケート研究データの識別子方針は `survey_responses.student_user_id` の備考を参照する。

## schools

学校マスタ

学校管理追加（2026-10-03）: `security_level`（TINYINT、1/2、旧未設定校のみNULL）、`security_level_locked`（BOOLEAN、既定FALSE、生徒登録時に不可逆ロック）、`version`（BIGINT、既定1、更新競合検出）を追加する。既存所属の全履歴から学校レベルを移行する。生徒未登録の旧学校は管理者が明示設定するまで登録不可。学校登録は学校コードをシステム発行し、名前・レベルを管理者が指定する。更新と監査は同一トランザクション。学校削除・学校間移動は今回対象外。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|school_id|学校ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|school_code|学校コード|VARCHAR(64)||NO|UNIQUE||
|name|学校名|VARCHAR(200)||NO|||
|school_status|学校状態|ENUM('active','inactive')||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## classrooms

学校に属するクラス

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|classroom_id|クラスID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|school_id|学校ID|BIGINT||NO|FOREIGN_KEY|schools.school_id|
|name|クラス名|VARCHAR(100)||NO|||
|grade_name|学年|VARCHAR(50)||YES|||
|classroom_status|クラス状態|ENUM('active','inactive')||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## student_class_memberships

生徒とクラスの所属履歴

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|membership_id|所属ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_user_id|生徒ユーザID|BIGINT||NO|FOREIGN_KEY|student_profiles.user_id。アンケート研究データではこの `users.user_id` を研究用識別子として使用し、別IDへ置換しない。アプリ内でアカウントへ対応づけ可能な仮名識別子としてアクセスを制限する|
|classroom_id|クラスID|BIGINT||NO|FOREIGN_KEY|classrooms.classroom_id|
|membership_status|所属状態|ENUM('active','inactive')||NO|||
|joined_at|所属開始日時|DATETIME||NO|||
|left_at|所属終了日時|DATETIME||YES|||

## teacher_school_permissions

教師が閲覧・操作できる学校の権限

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|teacher_school_permission_id|学校権限ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|teacher_user_id|教師ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|school_id|学校ID|BIGINT||NO|FOREIGN_KEY|schools.school_id|
|access_status|権限状態|ENUM('enabled','disabled')||NO|||
|updated_by_user_id|更新者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|updated_at|更新日時|DATETIME||NO|||

## teacher_feature_permissions

教師アカウントごとの機能利用可否。機能ごとに1行を持つ。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|teacher_feature_permission_id|機能権限ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|teacher_user_id|教師ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|feature_code|機能コード|VARCHAR(64)||NO|UNIQUE(teacher_user_id, feature_code)|画面機能の固定コード|
|is_enabled|利用可否|BOOLEAN||NO|||
|updated_by_user_id|更新者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|updated_at|更新日時|DATETIME||NO|||

## password_reset_records

管理者/教師が行ったパスワード再設定の履歴。パスワード自体は保存しない。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|password_reset_record_id|再設定履歴ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|target_user_id|対象ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|requested_by_user_id|実行者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|reset_status|再設定状態|ENUM('requested','completed','expired','cancelled')||NO|||
|must_change_at_next_login|次回変更必須|BOOLEAN||NO|||
|expires_at|有効期限|DATETIME||YES||一時認証情報を発行する場合|
|requested_at|実行日時|DATETIME||NO|||
|completed_at|完了日時|DATETIME||YES|||

## credential_history

初期発行・初回変更・通常変更・リセットなどのパスワード操作履歴。パスワードや一時パスワードは記録しない。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|credential_history_id|認証情報履歴ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|target_user_id|対象ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|actor_user_id|実行者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id。本人操作ではNULLまたは本人ID|
|password_reset_record_id|リセット履歴ID|BIGINT||YES|FOREIGN_KEY|password_reset_records.password_reset_record_id|
|action_type|操作種別|ENUM('initial_credential_issued','first_password_changed','password_changed','password_reset')||NO|||
|result_status|実行結果|ENUM('success','failure')||NO|||
|error_code|エラーコード|VARCHAR(64)||YES|||
|error_message|エラー内容|TEXT||YES||機密情報を含めない|
|occurred_at|発生日時|DATETIME||NO|||

## tasks

課題本体。学校/クラス対象は task_class_assignments で管理する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|task_id|課題ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_code|課題系列コード|VARCHAR(64)||NO||format/exportで使うTASK-xxx形式。同じ課題の改訂で共通|
|task_revision_code|課題改訂コード|VARCHAR(80)||NO|UNIQUE|TASK-xxx-vN形式|
|revision_number|改訂番号|INT||NO||系列内で1から採番|
|supersedes_task_id|直前改訂課題ID|BIGINT||YES|FOREIGN_KEY|tasks.task_id。初版はNULL|
|created_by_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|updated_by_user_id|更新者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|rubric_id|ルーブリックID|BIGINT||YES|FOREIGN_KEY|rubrics.rubric_id|
|rubric_version|ルーブリック版|VARCHAR(50)||YES||評価時点の版を evaluations にも固定|
|active_prompt_version_id|現在適用プロンプト版ID|BIGINT||YES|FOREIGN_KEY|prompt_versions.prompt_version_id。課題が現在使用するプロンプト版を1件のみ参照する。全体再評価の実行確定時に切り替える|
|title|課題名|VARCHAR(255)||NO|||
|theme|テーマ|VARCHAR(255)||YES|||
|difficulty|難易度|ENUM('beginner','intermediate','advanced','none')||YES|||
|language|プログラミング言語|VARCHAR(32)||NO||例: Python 3.x|
|description|課題説明|TEXT||NO|||
|input_constraints|入力制約|TEXT||YES|||
|creation_rules|作成時のルール|TEXT||YES|||
|initial_code|初期コード|LONGTEXT||YES|||
|save_status|保存状態|ENUM('draft','saved')||NO||課題編集フォームの保存状態|
|publication_status|公開状態|ENUM('draft','published','requires_update','archived')||NO||論理削除は archived + deleted_at|
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|published_at|公開日時|DATETIME||YES|||
|deleted_at|削除日時|DATETIME||YES|||
|deleted_by_user_id|削除実行者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|

一意制約: `(task_code, revision_number)`

公開中課題の編集では既存行を上書きせず、同じ `task_code` の新しい `revision_number` と `task_revision_code` を作成する。再公開まではクラス割当が直前の公開改訂を参照する。再公開時は旧割当を `archived` にし、新しい課題改訂を参照する割当行を作成する。旧割当行の `task_id` は変更しない。学習開始後に学習条件・評価条件を変更する場合は、同じ系列の改訂ではなく、新しい `task_code` の課題として複製する。

## task_features

課題で実装すべき機能。複数項目は課題ごとに複数行で保持する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|feature_id|実装機能ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|feature_text|実装機能|TEXT||NO|||
|sort_order|表示順|INT||NO|||
|record_status|レコード状態|ENUM('active','inactive')||NO|||

## task_class_assignments

課題の学校/クラス別対象、公開日時、提出期限、期限後初回提出ポリシー

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|task_class_assignment_id|課題割当ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|classroom_id|クラスID|BIGINT||NO|FOREIGN_KEY|classrooms.classroom_id|
|assignment_status|クラス別課題状態|ENUM('not_published','scheduled','published','requires_update','expired','archived')||NO|||
|publish_at|公開予定日時|DATETIME||YES||NULLは即時公開|
|due_at|提出期限|DATETIME||YES||NULLは期限なし|
|late_submission_policy|期限後提出方針|ENUM('allow','deny')||NO|||
|resubmission_policy|再提出方針|ENUM('allow','deny')||NO||旧設定。機能仕様書第70版以降は参照・更新せず、期限内の再提出可否にも使用しない。互換性維持のためDB列は現時点で保持|
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## task_participations

生徒×クラス別課題の学習・保存・評価状態

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|participation_id|学習参加ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_user_id|生徒ユーザID|BIGINT||NO|FOREIGN_KEY|student_profiles.user_id|
|task_class_assignment_id|課題割当ID|BIGINT||NO|FOREIGN_KEY|task_class_assignments.task_class_assignment_id|
|learning_status|学習状態|ENUM('not_started','in_progress','completed','needs_revision')||NO|||
|progress_status|進捗表示状態|ENUM('not_started','in_progress','submitted','awaiting_evaluation','completed','needs_action')||NO||進捗画面の状態。関連データ更新時に同一トランザクションで更新|
|save_status|コード保存状態|ENUM('unsaved','draft','saved')||NO||画面状態。未保存フラグはエディター更新時に送信して保持|
|current_draft_code|現在の編集用下書き|LONGTEXT||YES||初回編集または再提出用。提出版とは分離して更新可能|
|draft_base_submission_id|下書きのコピー元提出ID|BIGINT||YES|FOREIGN_KEY|submissions.submission_id。初回提出用下書きではNULL|
|draft_updated_at|下書き更新日時|DATETIME(6)||YES||下書き保存時の楽観ロックキー。マイクロ秒精度を保持し、クライアントが保持する値と不一致の場合は同時更新として拒否する|
|evaluation_status|評価状態|ENUM('not_started','in_progress','completed','failed','needs_revision')||NO|||
|active_duration_seconds|累積取り組み時間(秒)|BIGINT||NO||task_activity_sessionsから集計し、一覧用に更新|
|last_activity_at|最終活動日時|DATETIME||YES|||
|completed_at|完了日時|DATETIME||YES|||

一意制約: `(student_user_id, task_class_assignment_id)`

## task_activity_sessions

教師進捗画面の取り組み時間を算出する学習セッション履歴

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|activity_session_id|活動セッションID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|participation_id|学習参加ID|BIGINT||NO|FOREIGN_KEY|task_participations.participation_id|
|session_status|セッション状態|ENUM('active','ended','timed_out')||NO|||
|started_at|開始日時|DATETIME||NO|||
|last_active_at|最終活動日時|DATETIME||NO|||
|ended_at|終了日時|DATETIME||YES|||
|active_duration_seconds|実活動秒数|BIGINT||NO|||

## task_test_cases

課題の想定入出力テストケース

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|test_case_id|テストケースID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|title|説明|VARCHAR(255)||YES||正常系/境界値など|
|test_case_input|テストケース入力|TEXT||NO||task-testcase-format.csv準拠|
|test_case_output|期待出力|TEXT||NO||task-testcase-format.csv準拠|
|test_case_order|表示順|INT||NO||task-testcase-format.csv準拠|
|record_status|レコード状態|ENUM('active','inactive')||NO|||

## task_hints

課題のPythonコマンド等のヒント

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|hint_id|ヒントID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|hint_order|表示順|INT||NO||hint-format.csv準拠|
|hint_title|ヒントタイトル|VARCHAR(255)||NO||hint-format.csv準拠|
|hint_content|ヒント本文|TEXT||NO||hint-format.csv準拠|
|usage_syntax|使用方法|TEXT||YES|||
|hint_code|サンプルコード|LONGTEXT||YES||hint-format.csv準拠|
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|record_status|レコード状態|ENUM('active','inactive')||NO|||

## submissions

生徒の提出物。再提出は revision_number を増やした行として保持する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|submission_id|提出ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|participation_id|学習参加ID|BIGINT||NO|FOREIGN_KEY|task_participations.participation_id|
|revision_number|版番号|INT||NO|UNIQUE(participation_id, revision_number)||
|submitted_code|提出コード|LONGTEXT||NO|||
|submission_status|提出状態|ENUM('submitted','accepted','locked')||NO||submitted=受付・チェック処理中、accepted=チェック結果保存済み、locked=評価対象として固定。拒否した操作は行を作成しない|
|submission_request_key|提出要求キー|CHAR(36)||YES|UNIQUE(participation_id, submission_request_key)|短時間の再送を冪等に処理する。既存行はNULL|
|submitted_at|提出日時|DATETIME||NO|||
|review_locked_at|レビュー固定日時|DATETIME||YES|||
|created_at|作成日時|DATETIME||NO|||

状態遷移は `submitted → accepted → locked` とする。想定出力との不一致は `accepted` とし、期限・権限・重複送信等の検証で拒否した操作は `submissions` 行を作成せず、監査・エラーログへ記録する。各提出版は作成後に内容を上書きせず、再提出は新しい `revision_number` の行を作成する。

## code_logs

30秒スナップショット、保存、提出等の編集イベント

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|code_log_id|ログID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|participation_id|学習参加ID|BIGINT||NO|FOREIGN_KEY|task_participations.participation_id|
|submission_id|提出ID|BIGINT||YES|FOREIGN_KEY|submissions.submission_id|
|code_execution_id|実行ID|BIGINT||YES|FOREIGN_KEY|code_executions.execution_id|
|event_type|イベント種別|ENUM('periodic_snapshot','manual_save','submission','edit')||NO||課題モードの定期スナップショットは30秒ごと|
|snapshot_text|コードスナップショット|LONGTEXT||YES|||
|execution_status_at_capture|取得時実行状態|ENUM('not_run','succeeded','failed','timed_out')||YES||直近の実行状態。未実行時はNULL|
|observed_at|発生日時|DATETIME||NO|||
|created_at|記録日時|DATETIME||NO|||

推奨インデックス: `(participation_id, observed_at)`, `(event_type, observed_at)`
演習領域は主キーで識別する。同名領域の許可/禁止は作成元に応じてControl層で検証する。
## code_executions

コード実行とテストケース確認の結果

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|execution_id|実行ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|participation_id|学習参加ID|BIGINT||YES|FOREIGN_KEY|task_participations.participation_id。教師の確認実行ではNULL|
|submission_id|提出ID|BIGINT||YES|FOREIGN_KEY|submissions.submission_id。提出確認/教師確認実行の対象|
|actor_user_id|実行者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id。システム実行ではNULL|
|execution_context|実行用途|ENUM('student_task','submission_check','teacher_review_copy','distribution_template')||NO|||
|source_code|実行コード|LONGTEXT||NO|||
|standard_input|標準入力|LONGTEXT||YES||実行に渡した入力全文|
|execution_status|実行状態|ENUM('queued','running','succeeded','failed','timed_out')||NO|||
|exit_code|終了コード|INT||YES|||
|standard_output|標準出力|LONGTEXT||YES|||
|standard_output_truncated|標準出力切り詰めフラグ|BOOLEAN||NO||上限超過時にTRUE|
|standard_error|標準エラー|LONGTEXT||YES|||
|standard_error_truncated|標準エラー切り詰めフラグ|BOOLEAN||NO||上限超過時にTRUE|
|error_code|エラーコード|VARCHAR(64)||YES||実行基盤またはプログラムの分類コード|
|error_message|エラーメッセージ|TEXT||YES||表示用の安全なメッセージ。機密情報を含めない|
|duration_milliseconds|実行時間(ms)|INT||YES|||
|executed_at|実行日時|DATETIME||NO|||

## code_execution_test_results

実行ごとの入出力テスト結果。提出画面の一致件数・詳細表示をこの行から復元する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|execution_test_result_id|テスト結果ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|execution_id|実行ID|BIGINT||NO|FOREIGN_KEY|code_executions.execution_id|
|test_case_id|テストケースID|BIGINT||YES|FOREIGN_KEY|task_test_cases.test_case_id。自由入力の場合はNULL|
|case_order|ケース順|INT||NO|||
|input_snapshot|実際の入力|LONGTEXT||YES||実行時点の値を保存|
|expected_output_snapshot|期待出力|LONGTEXT||YES||実行時点の値を保存|
|actual_output|実際の出力|LONGTEXT||YES|||
|actual_output_truncated|出力切り詰めフラグ|BOOLEAN||NO|||
|result_status|判定状態|ENUM('matched','mismatched','error','not_run')||NO|||
|error_code|エラーコード|VARCHAR(64)||YES|||
|error_message|エラーメッセージ|TEXT||YES|||
|checked_at|判定日時|DATETIME||NO|||

## rubrics

版管理されたルーブリック

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|rubric_id|ルーブリックID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|created_by_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|title|タイトル|VARCHAR(255)||NO|||
|version|版数|VARCHAR(50)||NO|UNIQUE(title, version)||
|rubric_status|ルーブリック状態|ENUM('draft','active','archived')||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## rubric_dimensions

ルーブリックの総合評価次元（例: 思考力・判断力・表現力、主体的に学習に取り組む態度）

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|dimension_id|評価次元ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|rubric_id|ルーブリックID|BIGINT||NO|FOREIGN_KEY|rubrics.rubric_id|
|dimension_code|次元コード|VARCHAR(64)||NO|UNIQUE(rubric_id, dimension_code)||
|label|次元名|VARCHAR(255)||NO|||
|scale_minimum|最小値|INT||NO|||
|scale_maximum|最大値|INT||NO|||
|scale_label|評価尺度表示|VARCHAR(64)||NO||例: 5段階評価|
|sort_order|表示順|INT||NO|||

## rubric_criteria

ルーブリックの評価観点

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|criterion_id|観点ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|rubric_id|ルーブリックID|BIGINT||NO|FOREIGN_KEY|rubrics.rubric_id|
|dimension_id|評価次元ID|BIGINT||NO|FOREIGN_KEY|rubric_dimensions.dimension_id|
|criterion_code|観点コード|VARCHAR(64)||NO|UNIQUE(rubric_id, criterion_code)||
|name|観点名|VARCHAR(255)||NO|||
|description|説明|TEXT||YES|||
|weight|重み|DECIMAL(6,3)||NO|||
|sort_order|表示順|INT||NO|||

## criterion_levels

評価観点ごとの到達段階と説明

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|criterion_level_id|評価段階ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|criterion_id|観点ID|BIGINT||NO|FOREIGN_KEY|rubric_criteria.criterion_id|
|level_value|段階値|INT||NO|UNIQUE(criterion_id, level_value)|例: 1〜5|
|level_label|段階名|VARCHAR(100)||NO|||
|level_description|段階説明|TEXT||NO|||

## prompt_versions

課題ごとのプロンプト設定・版・生成段階状態

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|prompt_version_id|プロンプト版ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|version|版数|VARCHAR(50)||NO|UNIQUE(task_id, version)||
|ai_model|AIモデル|VARCHAR(100)||NO|||
|common_prompt|共通プロンプト|LONGTEXT||NO|||
|additional_evaluation_instruction|追加評価指示|TEXT||YES|||
|prompt_status|プロンプト状態|ENUM('draft','configured','requires_review','versioned')||NO|||
|fluctuation_generation_status|揺らぎ項目生成状態|ENUM('not_generated','in_progress','completed','failed')||NO|||
|evaluation_examples_status|評価例生成状態|ENUM('not_generated','in_progress','completed','failed')||NO|||
|created_by_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|created_at|作成日時|DATETIME||NO|||

## prompt_fluctuation_items

AIが抽出した揺らぎ項目と教師の対応記述

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|fluctuation_item_id|揺らぎ項目ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|prompt_version_id|プロンプト版ID|BIGINT||NO|FOREIGN_KEY|prompt_versions.prompt_version_id|
|item_text|揺らぎ項目|TEXT||NO|||
|teacher_resolution|教師の評価方針|TEXT||YES|||
|resolution_status|対応状態|ENUM('pending','resolved','not_applicable')||NO|||
|sort_order|表示順|INT||NO|||

## evaluation_examples

プロンプト版の確認用評価例

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_example_id|評価例ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|prompt_version_id|プロンプト版ID|BIGINT||NO|FOREIGN_KEY|prompt_versions.prompt_version_id|
|example_input|入力例|JSON||NO||生成に用いた例示入力|
|example_output|出力例|JSON||NO||生成結果|
|example_status|評価例状態|ENUM('active','inactive')||NO|||
|created_at|作成日時|DATETIME||NO|||

## evaluations

提出ごとの評価履歴

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_id|評価ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_code|評価外部ID|VARCHAR(64)||NO|UNIQUE|evaluation-format.jsonのevaluationId|
|submission_id|提出ID|BIGINT||NO|FOREIGN_KEY|submissions.submission_id|
|reevaluation_job_id|再評価ジョブID|BIGINT||YES|FOREIGN_KEY|reevaluation_jobs.reevaluation_job_id|
|rubric_id|ルーブリックID|BIGINT||NO|FOREIGN_KEY|rubrics.rubric_id|
|prompt_version_id|プロンプト版ID|BIGINT||YES|FOREIGN_KEY|prompt_versions.prompt_version_id|
|evaluation_status|評価状態|ENUM('not_started','in_progress','completed','failed','needs_revision')||NO|||
|evaluation_kind|評価種別|ENUM('initial','reevaluation','manual_review')||NO|||
|format_version|出力形式版|VARCHAR(32)||YES||evaluation-format.jsonのformatVersion|
|locale|ロケール|VARCHAR(16)||YES||例: ja-JP|
|generated_at|生成日時|DATETIME||YES||format生成日時|
|final_evaluated_at|最終評価日時|DATETIME||YES|||
|auto_save_count|自動保存回数|INT||NO|||
|execution_count|実行回数|INT||NO|||
|overall_score|総合点|DECIMAL(6,3)||YES|||
|process_analysis|プロセス分析|LONGTEXT||YES|||
|feedback_summary|総評|TEXT||YES|||
|created_at|作成日時|DATETIME||NO|||
|completed_at|完了日時|DATETIME||YES|||

## evaluation_scores

評価観点ごとの評価値

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_score_id|評価得点ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|criterion_id|観点ID|BIGINT||NO|FOREIGN_KEY|rubric_criteria.criterion_id|
|dimension_id|評価次元ID|BIGINT||NO|FOREIGN_KEY|rubric_dimensions.dimension_id|
|criterion_level_id|評価段階ID|BIGINT||YES|FOREIGN_KEY|criterion_levels.criterion_level_id|
|metric_key|指標コード|VARCHAR(64)||NO|UNIQUE(evaluation_id, metric_key)||
|metric_title|指標名|VARCHAR(255)||NO|||
|score_value|得点|INT||NO|||
|metric_description|指標説明|TEXT||YES||evaluation-format.jsonのmetricBreakdown.description|
|rationale|評価理由|TEXT||YES|||

## evaluation_dimension_results

評価JSONのoverall.dimensions。ルーブリック次元ごとの集約結果。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_dimension_result_id|評価次元結果ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|dimension_id|評価次元ID|BIGINT||NO|FOREIGN_KEY|rubric_dimensions.dimension_id|
|score_value|評価値|DECIMAL(6,3)||NO|||
|score_text|表示評価値|VARCHAR(32)||YES||例: 3.0|
|summary_title|要約タイトル|VARCHAR(255)||YES|||
|summary_description|要約説明|TEXT||YES|||

## evaluation_reasons

評価JSONの理由一覧。次元別スコアとは独立した文章理由。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_reason_id|評価理由ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|reason_code|理由コード|VARCHAR(64)||NO|UNIQUE(evaluation_id, reason_code)||
|dimension_id|評価次元ID|BIGINT||YES|FOREIGN_KEY|rubric_dimensions.dimension_id|
|category_label|分類表示名|VARCHAR(100)||YES|||
|title|理由タイトル|VARCHAR(255)||NO|||
|score_value|関連得点|DECIMAL(6,3)||YES|||
|body|理由本文|LONGTEXT||NO|||
|sort_order|表示順|INT||NO|||

## evaluation_reason_details

評価理由の詳細配列

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|reason_detail_id|理由詳細ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_reason_id|評価理由ID|BIGINT||NO|FOREIGN_KEY|evaluation_reasons.evaluation_reason_id|
|detail_code|詳細コード|VARCHAR(64)||YES|||
|detail_text|詳細本文|TEXT||NO|||
|sort_order|表示順|INT||NO|||

## evaluation_evidence

評価理由の根拠となるログ・コード範囲

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evidence_id|根拠ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_score_id|評価得点ID|BIGINT||YES|FOREIGN_KEY|evaluation_scores.evaluation_score_id|
|evaluation_reason_id|評価理由ID|BIGINT||YES|FOREIGN_KEY|evaluation_reasons.evaluation_reason_id|
|reason_detail_id|理由詳細ID|BIGINT||YES|FOREIGN_KEY|evaluation_reason_details.reason_detail_id|
|code_log_id|コードログID|BIGINT||YES|FOREIGN_KEY|code_logs.code_log_id|
|code_execution_id|実行ID|BIGINT||YES|FOREIGN_KEY|code_executions.execution_id|
|evidence_type|根拠種別|VARCHAR(64)||NO||compile/count/log等|
|evidence_label|根拠表示名|VARCHAR(255)||YES|||
|source_record_type|参照元種別|VARCHAR(64)||YES|||
|source_record_id|参照元ID|VARCHAR(128)||YES||compile_id等の外部ID|
|evidence_value|根拠値|TEXT||YES||count型等の値|
|evidence_unit|根拠単位|VARCHAR(64)||YES||例: 回|
|code_range|コード範囲|VARCHAR(255)||YES|||
|evidence_summary|根拠説明|TEXT||NO|||
|sort_order|表示順|INT||NO|||

## evaluation_feedback

評価結果に付随するフィードバック

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|feedback_id|フィードバックID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|feedback_type|種別|ENUM('positive','warning','improvement','critical')||NO|||
|priority|優先度|ENUM('high','medium','low')||NO|||
|message|内容|TEXT||NO|||
|created_at|作成日時|DATETIME||NO|||

## reevaluation_jobs

教師が起動する課題単位の一括再評価

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|reevaluation_job_id|再評価ジョブID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|prompt_version_id|プロンプト版ID|BIGINT||NO|FOREIGN_KEY|prompt_versions.prompt_version_id|
|requested_by_user_id|実行者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|reevaluation_status|再評価状態|ENUM('queued','in_progress','completed','failed','cancelled')||NO|||
|target_count|対象件数|INT||NO|||
|completed_count|完了件数|INT||NO|||
|progress_percent|進捗率|DECIMAL(5,2)||NO|||
|result_summary|結果概要|JSON||YES|||
|started_at|開始日時|DATETIME||YES|||
|completed_at|完了日時|DATETIME||YES|||

## distribution_templates

教師が作成する配信テンプレート

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|distribution_template_id|配信テンプレートID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|created_by_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|updated_by_user_id|更新者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|template_name|テンプレート名|VARCHAR(255)||NO|||
|root_path|ルートパス|VARCHAR(500)||YES|||
|save_status|保存状態|ENUM('draft','saved')||NO|||
|template_status|テンプレート状態|ENUM('active','archived')||NO|||
|overwrite_policy|再配信時の上書き方針|ENUM('overwrite','append')||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## distribution_template_files

テンプレート内のフォルダ/ファイルと初期内容

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|template_file_id|テンプレート項目ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|distribution_template_id|配信テンプレートID|BIGINT||NO|FOREIGN_KEY|distribution_templates.distribution_template_id|
|path|パス|VARCHAR(1000)||NO|UNIQUE(distribution_template_id, path)||
|entry_type|項目種別|ENUM('folder','file')||NO|||
|initial_content|初期内容|LONGTEXT||YES||ファイル項目のみ|
|entry_status|項目状態|ENUM('active','inactive')||NO|||

一意制約はパス全体に適用する。MySQL InnoDB の索引長制限を超えないよう、マイグレーションでは `utf8mb4_0900_ai_ci` の照合順序ウェイトから生成する SHA-256 列を内部索引に使う。

## distributions

配信実行単位の全体状態

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|distribution_id|配信ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|distribution_template_id|配信テンプレートID|BIGINT||NO|FOREIGN_KEY|distribution_templates.distribution_template_id|
|executed_by_user_id|実行者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|distribution_status|全体配信状態|ENUM('draft','scheduled','in_progress','completed','stopped')||NO||「未配信/下書き」は draft|
|created_at|作成日時|DATETIME||NO|||
|completed_at|完了日時|DATETIME||YES|||

## distribution_targets

配信先クラスごとの予約日時・実行状態

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|distribution_target_id|配信対象ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|distribution_id|配信ID|BIGINT||NO|FOREIGN_KEY|distributions.distribution_id|
|classroom_id|クラスID|BIGINT||NO|FOREIGN_KEY|classrooms.classroom_id|
|target_status|クラス別配信状態|ENUM('not_distributed','scheduled','distributed','stopped')||NO|||
|scheduled_at|配信予定日時|DATETIME||YES||NULLは即時配信|
|distributed_at|実配信日時|DATETIME||YES|||
|execution_result|実行結果|TEXT||YES|||

## distribution_histories

配信テンプレートの作成/編集/削除/配信/再配信履歴

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|distribution_history_id|配信履歴ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|distribution_id|配信ID|BIGINT||NO|FOREIGN_KEY|distributions.distribution_id|
|actor_user_id|実行者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|action_type|操作種別|VARCHAR(64)||NO||作成/編集/削除/配信/再配信|
|change_detail|変更内容|JSON||YES||差分と対象クラスを含む|
|execution_status|実行結果|ENUM('success','failure')||NO|||
|occurred_at|実行日時|DATETIME||NO|||

## student_exercises

生徒の授業演習領域。配信テンプレート外に生徒が作成した演習領域も保持する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|student_exercise_id|生徒演習ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_user_id|生徒ユーザID|BIGINT||NO|FOREIGN_KEY|student_profiles.user_id|
|distribution_target_id|配信対象ID|BIGINT||YES|FOREIGN_KEY|distribution_targets.distribution_target_id。生徒作成領域ではNULL|
|exercise_origin|演習作成元|ENUM('distribution','student_created')||NO|||
|scope_name|演習名/範囲|VARCHAR(255)||NO||例: 授業演習 / ウォームアップ|
|exercise_status|演習状態|ENUM('not_started','in_progress','temporarily_saved','completed','expired','needs_review','archived')||NO||archivedは演習領域の論理削除|
|save_status|保存状態|ENUM('unsaved','saved')||NO|||
|started_at|開始日時|DATETIME||YES|||
|completed_at|完了日時|DATETIME||YES|||
|expires_at|期限日時|DATETIME||YES|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|deleted_at|削除日時|DATETIME||YES||論理削除時に設定|
|deleted_by_user_id|削除実行者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|

演習領域は主キーで識別し、同名の領域も作成可能とする。

## student_exercise_entries

生徒の授業演習ツリー内のフォルダ/ファイルと最新保存内容。30秒の履歴は保存しない。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|exercise_entry_id|演習項目ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_exercise_id|生徒演習ID|BIGINT||NO|FOREIGN_KEY|student_exercises.student_exercise_id|
|parent_entry_id|親項目ID|BIGINT||YES|FOREIGN_KEY|student_exercise_entries.exercise_entry_id。ルートはNULL|
|entry_type|項目種別|ENUM('folder','file')||NO|||
|name|名前|VARCHAR(255)||NO|||
|path|パス|VARCHAR(1000)||NO|UNIQUE(student_exercise_id, path)||
|language|言語|VARCHAR(32)||YES||拡張子から推定できるが表示値を固定する場合は保存|
|description|説明|TEXT||YES||画面データのnote|
|current_content|現在内容|LONGTEXT||YES||file項目の最終保存内容|
|entry_status|項目状態|ENUM('active','trashed','deleted')||NO||ごみ箱からの復元制御を含む|
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|trashed_at|ごみ箱移動日時|DATETIME||YES|||
|deleted_at|削除日時|DATETIME||YES|||

一意制約はパス全体に適用する。MySQL InnoDB の索引長制限を超えないよう、マイグレーションでは `utf8mb4_0900_ai_ci` の照合順序ウェイトから生成する SHA-256 列を内部索引に使う。

## consent_document_versions

同意確認画面に表示する文書本文の固定版。過去の同意を当時の文面で再現する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|consent_document_version_id|同意文書版ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|version_code|版コード|VARCHAR(50)||NO|UNIQUE||
|title|文書タイトル|VARCHAR(255)||NO|||
|body|文書本文|LONGTEXT||NO||同意画面に表示する本文|
|document_status|文書状態|ENUM('draft','active','retired')||NO|||
|effective_at|適用日時|DATETIME||YES|||
|created_by_user_id|作成者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|created_at|作成日時|DATETIME||NO|||

## consent_records

研究同意の状態・同意文書版

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|consent_id|同意ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_id|ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|consent_status|同意状態|ENUM('unconfirmed','agreed','declined','withdrawn')||NO|||
|consent_document_version_id|同意文書版ID|BIGINT||NO|FOREIGN_KEY|consent_document_versions.consent_document_version_id|
|consented_at|同意日時|DATETIME||YES|||
|withdrawn_at|撤回日時|DATETIME||YES|||
|updated_at|更新日時|DATETIME||NO|||

## surveys

アンケート定義

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|survey_id|アンケートID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||YES|FOREIGN_KEY|tasks.task_id。全体アンケートの場合はNULL。工程8の生徒向け回答導線ではNULLのsurveyは対象外|
|title|タイトル|VARCHAR(255)||NO|||
|survey_status|アンケート状態|ENUM('draft','active','closed','archived')||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## survey_questions

アンケートの設問定義

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|question_id|設問ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|survey_id|アンケートID|BIGINT||NO|FOREIGN_KEY|surveys.survey_id|
|question_code|設問コード|VARCHAR(64)||NO|UNIQUE(survey_id, question_code)||
|question_type|設問形式|ENUM('rating','text','single_choice','multiple_choice')||NO|||
|prompt_text|設問文|TEXT||NO|||
|reason_prompt_text|理由欄ラベル|VARCHAR(255)||NO||理由・補足欄に表示するラベル|
|required|必須フラグ|BOOLEAN||NO|||
|reason_required|理由必須フラグ|BOOLEAN||NO||回答値に付随する理由・補足の必須条件|
|sort_order|表示順|INT||NO|||
|question_status|設問状態|ENUM('active','inactive')||NO|||

## survey_question_options

選択式設問・5段階評価などの選択肢

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|option_id|選択肢ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|question_id|設問ID|BIGINT||NO|FOREIGN_KEY|survey_questions.question_id|
|option_code|選択肢コード|VARCHAR(64)||NO|UNIQUE(question_id, option_code)||
|label|表示名|VARCHAR(255)||NO|||
|value|保存値|VARCHAR(255)||NO||数値評価値または選択コード|
|sort_order|表示順|INT||NO|||
|option_status|選択肢状態|ENUM('active','inactive')||NO|||

## survey_responses

生徒ごとのアンケート回答状態

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|survey_response_id|回答ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|response_code|回答コード|VARCHAR(64)||NO|UNIQUE|画面/CSVに出す SRV 系ID|
|record_code|記録コード|VARCHAR(64)||NO|UNIQUE|画面/CSVに出す REC 系ID|
|student_user_id|生徒ユーザID|BIGINT||NO|FOREIGN_KEY|student_profiles.user_id|
|survey_id|アンケートID|BIGINT||NO|FOREIGN_KEY|surveys.survey_id|
|task_id|課題ID|BIGINT||YES|FOREIGN_KEY|tasks.task_id|
|evaluation_id|評価ID|BIGINT||YES|FOREIGN_KEY|evaluations.evaluation_id。画面上のシステム評価参照|
|consent_status_at_submission|提出時同意状態|ENUM('unconfirmed','agreed','declined','withdrawn')||NO||回答時点の状態を保持|
|response_status|回答状態|ENUM('not_answered','in_progress','submitted','not_applicable')||NO|||
|started_at|開始日時|DATETIME||YES|||
|submitted_at|提出日時|DATETIME||YES|||

一意制約: `(student_user_id, survey_id, evaluation_id)`（Flyway V8）。工程8の課題別回答では `evaluation_id` を必須とし、同じ評価への重複回答を防ぐ。`evaluation_id` が異なれば同じ生徒・surveyでも別の回答履歴を保持できる。回答中は既存行から再開し、提出済み行は読み取り専用とする。

## survey_answers

設問単位の回答値・理由

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|survey_answer_id|回答項目ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|survey_response_id|回答ID|BIGINT||NO|FOREIGN_KEY|survey_responses.survey_response_id|
|question_id|設問ID|BIGINT||NO|FOREIGN_KEY|survey_questions.question_id|
|answer_value|回答値|TEXT||YES||評定値・選択値・記述回答|
|answer_reason|回答理由|TEXT||YES|||

一意制約: `(survey_response_id, question_id)`（Flyway V8）。下書きの更新は同一設問の回答行を更新し、別回答行を重複作成しない。

## evaluation_requests

AI評価リクエスト状態と送信情報

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_request_id|評価リクエストID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|model_id|モデルID|VARCHAR(100)||NO|||
|prompt_version|プロンプト版|VARCHAR(50)||NO|||
|rubric_version|ルーブリック版|VARCHAR(50)||NO|||
|actor_role|実行者種別|ENUM('student','teacher','admin','system')||NO|||
|consent_status_at_request|送信時同意状態|ENUM('unconfirmed','agreed','declined','withdrawn')||NO|||
|locale|言語/地域|VARCHAR(16)||YES|||
|request_payload|送信内容|JSON||NO||匿名化・個人情報除去を確認|
|request_status|リクエスト状態|ENUM('queued','in_progress','succeeded','failed')||NO|||
|retry_count|再試行回数|INT||NO|||
|requested_at|依頼日時|DATETIME||NO|||
|completed_at|完了日時|DATETIME||YES|||
|error_detail|エラー詳細|TEXT||YES|||

## evaluation_responses

AI応答の原文・状態

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_response_id|応答ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_request_id|評価リクエストID|BIGINT||NO|FOREIGN_KEY|evaluation_requests.evaluation_request_id|
|raw_response|応答JSON|JSON||NO|||
|response_status|応答状態|ENUM('received','validated','invalid')||NO|||
|confidence|確信度|DECIMAL(5,4)||YES|||
|warnings|警告|JSON||YES|||
|received_at|受信日時|DATETIME||NO|||

## evaluation_input_snapshots

AIへ渡した匿名化済み入力の再現用スナップショット

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|input_snapshot_id|入力スナップショットID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_request_id|評価リクエストID|BIGINT||NO|FOREIGN_KEY|evaluation_requests.evaluation_request_id|
|anonymized_subject_id|匿名化対象ID|VARCHAR(128)||NO||個人識別情報と分離|
|task_snapshot|課題スナップショット|JSON||NO|||
|submission_snapshot|提出物スナップショット|JSON||NO|||
|log_range_start|ログ開始日時|DATETIME||YES|||
|log_range_end|ログ終了日時|DATETIME||YES|||
|created_at|作成日時|DATETIME||NO|||

## login_history

教師/管理者/生徒のログイン・ログアウト履歴

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|login_history_id|ログイン履歴ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_id|ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id。認証失敗で特定不能の場合はNULL|
|attempted_login_id|入力ログインID|VARCHAR(64)||YES||認証失敗時に対象アカウントを特定できるよう保持|
|operation_type|操作種別|ENUM('login','logout','password_change','password_reset')||NO|||
|result_status|実行結果|ENUM('success','failure')||NO|||
|error_code|エラーコード|VARCHAR(64)||YES|||
|error_message|エラーメッセージ|TEXT||YES||教師向け履歴画面に表示できる安全な内容|
|ip_address|IPアドレス|VARCHAR(45)||YES||IPv4/IPv6|
|user_agent|ブラウザ情報|VARCHAR(512)||YES|||
|session_id|セッションID|VARCHAR(128)||YES||セッション識別用のランダムID。セッショントークンは保存しない|
|error_detail|エラー詳細|TEXT||YES||機微情報を含めない|
|occurred_at|発生日時|DATETIME||NO|||

## audit_logs

教師/管理者のデータ変更監査。業務データと分離する。

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|audit_log_id|監査ログID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|actor_user_id|実行者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id。システム処理はNULL可|
|actor_role|実行者種別|ENUM('student','teacher','admin','system')||NO|||
|feature_code|機能コード|VARCHAR(64)||NO||課題編集、配信、プロンプト設計、アカウント管理等|
|target_type|対象種別|VARCHAR(100)||NO|||
|target_id|対象ID|BIGINT||YES||異なるテーブルを対象にするため論理参照|
|action_type|操作種別|VARCHAR(64)||NO||作成/更新/削除/公開/配信/再評価/CSV出力等|
|result_status|実行結果|ENUM('success','failure')||NO|||
|error_code|エラーコード|VARCHAR(64)||YES||失敗時|
|error_message|エラーメッセージ|TEXT||YES||失敗時の安全な表示用メッセージ|
|detail|操作詳細|TEXT||YES||localStorage監査イベントのdetailに対応|
|before_data|変更前|JSON||YES||秘密情報は記録しない|
|after_data|変更後|JSON||YES||秘密情報は記録しない|
|request_id|リクエストID|VARCHAR(128)||YES||関連する操作/エラーとの追跡用|
|occurred_at|発生日時|DATETIME||NO|||

## application_error_logs

ログイン/操作履歴に紐づかないアプリケーション、DB、外部APIなどのエラー記録

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|application_error_log_id|アプリケーションエラーID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_id|ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|request_id|リクエストID|VARCHAR(128)||YES|||
|feature_code|機能コード|VARCHAR(64)||YES|||
|operation_name|処理名|VARCHAR(128)||YES|||
|error_category|エラー分類|ENUM('validation','authentication','authorization','database','execution','external_api','unexpected')||NO|||
|error_code|エラーコード|VARCHAR(64)||YES|||
|user_message|利用者向けメッセージ|TEXT||YES||画面表示文言。秘密値を含めない|
|diagnostic_detail|診断詳細|LONGTEXT||YES||スタック等は秘匿情報を除去し、権限制限して閲覧|
|occurred_at|発生日時|DATETIME||NO|||

## 状態カラム対応

画面状態は次の永続カラムを正本とし、一覧・バッジ用の表示語はここから取得する。

|画面上の状態領域|正本テーブル.カラム|補足|
|:--|:--|:--|
|30秒コードスナップショット|code_logs.event_type, snapshot_text, observed_at, execution_status_at_capture|periodic_snapshotとして課題モードのみ記録|
|コード実行とエラー|code_executions.standard_input, standard_output, standard_error, error_code, error_message, execution_status|学生実行/提出チェック/教師確認実行をexecution_contextで区別|
|実行出力上限による切り詰め|code_executions.standard_output_truncated, standard_error_truncated; code_execution_test_results.actual_output_truncated|表示上限を超えた結果を完全な内容と誤認しない|
|テストケース単位の提出判定|code_execution_test_results.input_snapshot, expected_output_snapshot, actual_output, result_status|教師提出画面の詳細と一致件数を再構築|
|アカウント有効/停止/削除|users.account_status|生徒/教師/管理者共通|
|研究用識別子（Gemini送信用）|research_subject_identifiers.research_subject_code|Gemini送信時に使用。アンケート研究データはusers.user_idを用いる。ログイン用ID(student_profiles.student_code)とは独立に1生徒1件で保持|
|生徒初回ログイン/パスワード変更|student_profiles.first_login_status, must_change_password|平文パスワードは保持しない|
|学校/クラスの有効状態|schools.school_status, classrooms.classroom_status|名称でなく外部キーで所属管理|
|クラス所属|student_class_memberships.membership_status|在籍期間も保存|
|教師の学校・機能権限|teacher_school_permissions.access_status, teacher_feature_permissions.is_enabled|権限は教師×学校/機能の行で保存|
|課題の保存/公開/論理削除|tasks.save_status, publication_status, deleted_at|保存状態と公開状態を分離|
|クラス別の公開/提出期限|task_class_assignments.assignment_status, publish_at, due_at, late_submission_policy|対象クラスごとに保持|
|生徒の課題学習/保存/評価|task_participations.learning_status, progress_status, save_status, evaluation_status|生徒×クラス割当ごと|
|期限後の初回提出|task_class_assignments.late_submission_policy|同じ課題割当の生徒に共通適用|
|再提出可否|提出期限|期限内は一律許可。旧 `resubmission_policy` 列は互換性維持のみ|
|提出状態|submissions.submission_status|提出版ごとに履歴を保持|
|実行状態|code_executions.execution_status|標準出力/標準エラーも保存|
|ログイン試行の表示情報|login_history.attempted_login_id, error_message, ip_address, user_agent, session_id|ユーザーID不明の失敗試行も記録|
|課題テストケース/ヒントの有効状態|task_test_cases.record_status, task_hints.record_status|物理削除せず無効化可能|
|プロンプト状態/生成ステップ|prompt_versions.prompt_status, fluctuation_generation_status, evaluation_examples_status|プロンプト版ごと|
|課題が現在使用するプロンプト版|tasks.active_prompt_version_id|課題につき1件のみ参照。評価開始時点でevaluationsへ固定し、以後の切替の影響を受けない|
|揺らぎ項目の教師対応|prompt_fluctuation_items.resolution_status|項目ごと|
|ルーブリック状態/段階|rubrics.rubric_status, criterion_levels.level_value|評価段階の説明も行で保存|
|評価状態/再評価状態|evaluations.evaluation_status, reevaluation_jobs.reevaluation_status|旧評価結果は上書きしない|
|配信全体/クラス別状態|distributions.distribution_status, distribution_targets.target_status|予約/実配信日時も分離保存|
|テンプレート保存/有効/上書き方針|distribution_templates.save_status, template_status, overwrite_policy|フォルダ/ファイルは別行|
|授業演習領域/ファイル状態|student_exercises.exercise_status, save_status; student_exercise_entries.entry_status|演習全体とツリー内項目の状態を分離|
|取り組み時間|task_activity_sessions.active_duration_seconds|セッション合計から集計|
|演習領域/ツリー|student_exercises.scope_name, exercise_origin; student_exercise_entries.parent_entry_id, entry_type, path, current_content, entry_status|空フォルダ、階層、最終保存内容、ごみ箱を保持|
|同意状態|consent_records.consent_status|同意文書版・同意/撤回日時を保存|
|同意状態/表示文書|consent_records.consent_status, consent_document_version_id; consent_document_versions.body|同意当時の文面を再現|
|アンケート回答状態|survey_responses.response_status|設問定義と設問別回答は別テーブル|
|アンケートの画面ID/システム評価/同意スナップショット|survey_responses.response_code, record_code, evaluation_id, consent_status_at_submission|CSVと詳細画面の表示値を再現|
|アンケート選択肢|survey_question_options.option_code, label, value, sort_order|評定段階と選択肢を行として保持|
|ログイン/パスワード再設定結果|login_history.result_status, password_reset_records.reset_status|監査履歴として追跡|
|パスワード変更履歴|credential_history.action_type, actor_user_id, result_status|秘密値は保存しない|
|管理操作結果|audit_logs.result_status|実行者/対象/操作/変更前後を保存|
|監査エラー詳細|audit_logs.feature_code, error_code, error_message, detail, request_id|教師共通監査UIの表示情報に対応|
|アプリケーションエラー|application_error_logs.error_category, error_code, user_message, diagnostic_detail|画面通知文言と秘匿済み診断情報を分離|
|AI依頼/応答状態|evaluation_requests.request_status, evaluation_responses.response_status|入力スナップショットと送受信データを追跡|

## format資料からの項目対応

`docs/format` は出力/取込形式の列挙として漏れ確認に使い、DBモデルは画面要件・状態ルール・教師/管理者の操作履歴も含めて定義する。

|形式資料|DBの対応先|保持方法|
|:--|:--|:--|
|task-format.csv|tasks, task_features, task_class_assignments|task_id/task_code/task_revision_code/revision_number/title/level/theme/description/constraints/initial_code/languageは課題改訂の列、featuresは子行、school_targets/class_targetsは学校配下のクラス割当、publish_at/status/is_deletedは公開状態と日時/論理削除に分解。外部形式のtask_codeは課題系列コードとして維持する|
|task-testcase-format.csv|task_test_cases|test_case_id/task_id/order/input/outputを列として保持|
|hint-format.csv|task_hints|hint_id/task_id/order/title/content/codeを列として保持|
|survey-format.csv|survey_responses, survey_answers, survey_questions, survey_question_options, evaluations|recordId/responseIdを保持し、設問コードごとに評定/理由/コメントを保存。systemEvaluationはevaluation_idで参照し、提出時の同意状態もスナップショット保存|
|evaluation-format.json: evaluation metadata|evaluations|evaluationId/formatVersion/locale/generatedAt/finalEvaluatedAt/autoSaveCount/executionCountを列として保持し、student/taskはsubmission経由で参照|
|evaluation-format.json: overall.dimensions|rubric_dimensions, evaluation_dimension_results|次元ラベル/尺度と評価ごとのscore/scoreText/summaryを別保存|
|evaluation-format.json: metricBreakdown|rubric_criteria, evaluation_scores|metricKey/title/score/descriptionを指標行として保存|
|evaluation-format.json: reasons/details/evidence|evaluation_reasons, evaluation_reason_details, evaluation_evidence|文章理由、詳細配列、compile/log/count等の根拠IDを親子行として保持|
|evaluation-format.json: hero/reasonFilters|画面表示定義|固定の見出し文言・フィルター選択肢はUI設定。学生/課題/評価の業務データとして複製保存しない|

## DBへ重複保存しない表示状態

- 表示ラベル（例: 「未着手」「評価待ち」）は永続状態値を日本語化して表示する。
- 「未保存」は `task_participations.save_status` とエディターが送信する更新状態から管理する。編集内容そのものがサーバーに届いていない間はDBに記録できないため、画面終了前に更新通知を送る契約が必要。
- 検索・フィルター・ソート条件は画面操作状態であり、利用者の業務状態としては保存しない。
- 進捗の集計値は `task_participations`、`submissions`、`evaluations` の状態から算出し、正本と異なる独立した状態を作らない。

## 画面サンプルデータの永続化対応

以下は `screen-flow-diagram/webapp/WEB-INF` と対応するJavaScriptの画面データを、本実装で読み書きする正本テーブルへ置き換える対応表である。HTML/JavaScript内の固定レコードや `localStorage` はDBの代替にしない。

|画面/サンプルデータ|DBの正本|保存する主なデータ|
|:--|:--|:--|
|生徒ログイン/パスワード/アカウント詳細|users, student_profiles, student_class_memberships, login_history, credential_history|アカウント属性、所属、初回変更状態、成功/失敗ログイン、認証情報操作履歴|
|生徒ホーム/課題一覧/エディター|schools, classrooms, tasks, task_class_assignments, task_participations, task_test_cases, task_hints|課題本文、学校クラス割当、期限、進捗/保存/再提出状態、入出力例、ヒント|
|課題コードログ/実行/提出|code_logs, code_executions, code_execution_test_results, submissions, evaluations|30秒スナップショット、実行I/O/エラー、ケース別期待/実出力/判定、提出版、評価|
|生徒授業演習/教師授業演習確認|student_exercises, student_exercise_entries, code_executions|演習名、フォルダ/ファイル階層、説明、最終保存コード、実行結果。演習は30秒スナップショット対象外|
|同意確認/アンケート|consent_document_versions, consent_records, surveys, survey_questions, survey_question_options, survey_responses, survey_answers|同意文面の版と本文、回答時同意状態、回答ID、システム評価参照、設問/選択肢/評定/記述回答|
|管理者の教師アカウント管理|users, teacher_school_permissions, teacher_feature_permissions, login_history, credential_history, audit_logs|教師アカウント、学校/機能権限、ログイン/削除/権限/認証情報変更履歴|
|教師の進捗/提出/評価確認|task_participations, task_activity_sessions, code_logs, submissions, code_execution_test_results, evaluations, evaluation_scores, evaluation_evidence|状態、取り組み時間、時系列イベント、提出チェック、評価段階と根拠|
|教師の課題編集/プロンプト設計|tasks, task_class_assignments, task_features, task_test_cases, task_hints, prompt_versions, prompt_fluctuation_items, evaluation_examples, reevaluation_jobs, audit_logs|画面フォーム設定、版、生成例、再評価進捗、作成/更新履歴|
|教師のコード配信|distribution_templates, distribution_template_files, distributions, distribution_targets, distribution_histories, audit_logs|テンプレート階層/内容、学校クラス別スケジュール/状態、配信者、変更差分|
|教師のアンケート結果|survey_responses, survey_answers, evaluations, consent_records|回答値/理由、システム評価、回答時同意状態|
|共有エラー/操作通知|audit_logs, application_error_logs, login_history, code_executions|操作失敗、認証失敗、実行エラー、利用者向け文言と秘匿済み診断詳細|

データベースへ保存しないのは、固定ラベル/説明文、入力中だけの未送信フォーム、検索・並び替え条件などのUI状態である。選択肢が学校/クラス/課題などの実データを表す場合は、固定HTML optionではなく対応マスタ/関連テーブルから取得する。
