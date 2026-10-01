# テーブル定義

## users

ユーザに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|user_id|ユーザID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_type|利用者種別|ENUM('student','teacher','admin')||NO||生徒/教師/管理者|
|login_id|ログインID|VARCHAR(64)||NO|UNIQUE||
|password_hash|パスワードハッシュ|VARCHAR(255)||NO||ハッシュ化済み|
|display_name|表示名|VARCHAR(100)||NO|||
|email|メールアドレス|VARCHAR(255)||YES|UNIQUE||
|is_active|利用可否|BOOLEAN||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|deleted_at|削除日時|DATETIME||YES||論理削除用|

## tasks

課題に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|task_id|課題ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|teacher_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|title|課題名|VARCHAR(255)||NO|||
|description|課題説明|TEXT||NO|||
|input_constraints|入力制約|TEXT||YES|||
|expected_output|想定出力|TEXT||YES|||
|save_status|保存状態|ENUM('draft','saved','submitted')||NO|||
|learning_status|学習状態|ENUM('not_started','in_progress','completed')||NO|||
|publication_status|公開状態|ENUM('private','published','archived')||NO|||
|prompt_version|プロンプト版|VARCHAR(50)||NO|||
|rubric_version|ルーブリック版|VARCHAR(50)||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|published_at|公開日時|DATETIME||YES|||
|deleted_at|削除日時|DATETIME||YES|||

## prompts

プロンプトに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|prompt_id|プロンプトID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|teacher_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|task_id|課題ID|BIGINT||YES|FOREIGN_KEY|tasks.task_id|
|title|タイトル|VARCHAR(255)||NO|||
|version|バージョン|VARCHAR(50)||NO|||
|content|本文|TEXT||NO|||
|status|状態|ENUM('draft','approved','needs_review')||NO|||
|is_active|有効フラグ|BOOLEAN||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## rubrics

ルーブリックに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|rubric_id|ルーブリックID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|teacher_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|title|タイトル|VARCHAR(255)||NO|||
|version|バージョン|VARCHAR(50)||NO|||
|description|概要|TEXT||YES|||
|is_active|有効フラグ|BOOLEAN||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## rubric_criteria

ルーブリックの各評価観点に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|criterion_id|観点ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|rubric_id|ルーブリックID|BIGINT||NO|FOREIGN_KEY|rubrics.rubric_id|
|name|観点名|VARCHAR(255)||NO|||
|description|説明|TEXT||YES|||
|weight|重み|INT||NO|||
|sort_order|表示順|INT||NO|||
|created_at|作成日時|DATETIME||NO|||

## submissions

提出物に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|submission_id|提出ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_user_id|学生ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|submitted_code|最終コード|LONGTEXT||NO|||
|submission_status|提出状態|ENUM('draft','submitted','re_submittable','locked')||NO|||
|submitted_at|提出日時|DATETIME||YES|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|review_locked_at|レビュー固定日時|DATETIME||YES|||

## code_logs

コード編集ログに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|code_log_id|ログID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|student_user_id|学生ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|submission_id|提出ID|BIGINT||YES|FOREIGN_KEY|submissions.submission_id|
|log_event_type|イベント種別|ENUM('snapshot','save','run','submit')||NO|||
|snapshot_text|コードスナップショット|LONGTEXT||YES|||
|run_result|実行結果|JSON||YES|||
|observed_at|記録日時|DATETIME||NO|||
|created_at|作成日時|DATETIME||NO|||

## evaluations

評価結果に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_id|評価ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|submission_id|提出ID|BIGINT||NO|FOREIGN_KEY|submissions.submission_id|
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|rubric_id|ルーブリックID|BIGINT||NO|FOREIGN_KEY|rubrics.rubric_id|
|prompt_id|プロンプトID|BIGINT||YES|FOREIGN_KEY|prompts.prompt_id|
|evaluation_status|評価状態|ENUM('pending','in_progress','completed','failed','revision_requested')||NO|||
|overall_score|総合点|INT||YES|||
|process_analysis|プロセス分析|TEXT||YES|||
|feedback_summary|総評|TEXT||YES|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||
|completed_at|完了日時|DATETIME||YES|||

## evaluation_scores

評価の各観点ごとの得点に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evaluation_score_id|得点ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|criterion_id|観点ID|BIGINT||NO|FOREIGN_KEY|rubric_criteria.criterion_id|
|score_value|点数|INT||NO|||
|rationale|理由|TEXT||YES|||
|created_at|作成日時|DATETIME||NO|||

## evaluation_evidence

評価根拠に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|evidence_id|根拠ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|code_section|参照コード範囲|VARCHAR(255)||YES|||
|log_timestamp|ログ時刻|DATETIME||YES|||
|summary|説明|TEXT||NO|||
|created_at|作成日時|DATETIME||NO|||

## evaluation_feedback

評価フィードバックに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|feedback_id|フィードバックID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|evaluation_id|評価ID|BIGINT||NO|FOREIGN_KEY|evaluations.evaluation_id|
|feedback_level|レベル|ENUM('good','warning','improvement','critical')||NO|||
|message|本文|TEXT||NO|||
|priority|優先度|ENUM('high','medium','low')||NO|||
|created_at|作成日時|DATETIME||NO|||

## distributions

課題配信に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|distribution_id|配信ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|task_id|課題ID|BIGINT||NO|FOREIGN_KEY|tasks.task_id|
|teacher_user_id|作成者ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|title|配信名|VARCHAR(255)||NO|||
|distribution_status|配信状態|ENUM('draft','scheduled','active','completed','stopped')||NO|||
|start_at|開始日時|DATETIME||YES|||
|end_at|終了日時|DATETIME||YES|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## distribution_targets

配信先クラスに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|distribution_target_id|配信対象ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|distribution_id|配信ID|BIGINT||NO|FOREIGN_KEY|distributions.distribution_id|
|classroom_id|クラスID|BIGINT||NO|FOREIGN_KEY|classrooms.classroom_id|
|target_status|対象状態|ENUM('pending','active','completed')||NO|||
|created_at|作成日時|DATETIME||NO|||

## classrooms

クラスに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|classroom_id|クラスID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|name|クラス名|VARCHAR(100)||NO|||
|teacher_user_id|担当教師ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## classroom_students

クラスと生徒の所属関係テーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|classroom_student_id|所属ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|classroom_id|クラスID|BIGINT||NO|FOREIGN_KEY|classrooms.classroom_id|
|student_user_id|生徒ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|joined_at|参加日時|DATETIME||NO|||
|left_at|退会日時|DATETIME||YES|||

## consent_records

同意情報に対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|consent_id|同意ID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_id|ユーザID|BIGINT||NO|FOREIGN_KEY|users.user_id|
|consent_status|同意状態|ENUM('not_asked','agreed','declined','withdrawn')||NO|||
|consented_at|同意日時|DATETIME||YES|||
|version|同意書バージョン|VARCHAR(50)||NO|||
|created_at|作成日時|DATETIME||NO|||
|updated_at|更新日時|DATETIME||YES|||

## audit_logs

監査ログに対するテーブル

|フィールド名|和名|型|主キー|NULL|その他制約|備考|
|:--|:--|:--|:--|:--|:--|:--|
|audit_log_id|監査ログID|BIGINT|〇|NO|PRIMARY_KEY, AUTO_INCREMENT||
|user_id|操作者ユーザID|BIGINT||YES|FOREIGN_KEY|users.user_id|
|target_table|対象テーブル|VARCHAR(100)||NO|||
|target_id|対象レコードID|BIGINT||NO|||
|action_type|操作種別|ENUM('create','update','delete','publish','submit','evaluate')||NO|||
|payload|変更内容|JSON||YES|||
|created_at|作成日時|DATETIME||NO|||

## 補足

- `code_logs` は 30 秒単位の編集履歴を保持するため、履歴量が多くなる前提でインデックス設計を行う
- `evaluation_requests` と `evaluation_results` は AI 評価依頼の再現性と監査性のための補助テーブルとして追加可能
- `save_status` と `publication_status` は画面表示用状態と公開状態を分離して管理する
