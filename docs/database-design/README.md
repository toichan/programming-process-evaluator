# データベース設計書

作成日：2026/10/01
版数：1
作成者：樋田巧

## 改版履歴

|日付|版数|編集者|変更点|
|-|-|-|-|
|2026/10/01|初版|樋田|- クラス図を基にデータベース設計を整理 |

### データベース名

未確定（`.env` の `DB_NAME` で設定。`.env.sample` では未設定）

### 目的

本設計書は、プログラミング学習支援システムのデータモデルを定義する。
学習ログ、提出物、評価結果、教師による課題管理、同意情報、AI評価リクエストを MySQL で保持し、システム全体の状態管理と監査性を確保する。

現状の `screen-flow-diagram` は画面遷移プロトタイプであり、ログイン/監査履歴や実行結果の一部は固定データまたはブラウザー内保存で動作する。以下のDB定義は永続化先の設計であり、DBへの実書込みが実装済みであることを示すものではない。

### 主な対象テーブル
- users / student_profiles / schools / classrooms / student_class_memberships
- teacher_school_permissions / teacher_feature_permissions / password_reset_records / credential_history
- tasks / task_features / task_class_assignments / task_participations / task_activity_sessions
- task_test_cases / task_hints / submissions / code_logs / code_executions / code_execution_test_results
- prompt_versions / prompt_fluctuation_items / evaluation_examples
- rubrics / rubric_dimensions / rubric_criteria / criterion_levels
- evaluations / evaluation_dimension_results / evaluation_scores / evaluation_reasons / evaluation_reason_details / evaluation_evidence / evaluation_feedback / reevaluation_jobs
- distribution_templates / distribution_template_files / distributions / distribution_targets / distribution_histories
- student_exercises / student_exercise_entries / consent_document_versions / consent_records
- surveys / survey_questions / survey_question_options / survey_responses / survey_answers
- evaluation_requests / evaluation_responses / evaluation_input_snapshots
- login_history / credential_history / audit_logs / application_error_logs

### 参照資料

- [docs/class-diagram/README.md](../class-diagram/README.md)
- [docs/class-diagram/01-core-learning-flow.puml](../class-diagram/01-core-learning-flow.puml)
- [docs/class-diagram/02-evaluation-rubric.puml](../class-diagram/02-evaluation-rubric.puml)
- [docs/class-diagram/03-teacher-task-distribution.puml](../class-diagram/03-teacher-task-distribution.puml)
- [docs/class-diagram/04-ai-consent-log.puml](../class-diagram/04-ai-consent-log.puml)
- [docs/class-diagram/05-overall-summary.puml](../class-diagram/05-overall-summary.puml)
- [docs/format/task-format.csv](../format/task-format.csv)
- [docs/format/task-testcase-format.csv](../format/task-testcase-format.csv)
- [docs/format/hint-format.csv](../format/hint-format.csv)
- [docs/format/survey-format.csv](../format/survey-format.csv)
- [docs/format/evaluation-format.json](../format/evaluation-format.json)

### 詳細定義

詳細なテーブル定義は [table-definitions.md](table-definitions.md) に分離してある。
