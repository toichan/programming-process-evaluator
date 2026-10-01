# データベース設計書

作成日：2026/10/01
版数：1
作成者：樋田巧

## 改版履歴

|日付|版数|編集者|変更点|
|-|-|-|-|
|2026/10/01|初版|樋田|- クラス図を基にデータベース設計を整理 |

### データベース名

programming_process_evaluator_db

### 目的

本設計書は、プログラミング学習支援システムのデータモデルを定義する。
学習ログ、提出物、評価結果、教師による課題管理、同意情報、AI評価リクエストを MySQL で保持し、システム全体の状態管理と監査性を確保する。

### 主な対象テーブル

- users
- tasks
- prompts
- rubrics
- rubric_criteria
- submissions
- code_logs
- evaluations
- evaluation_scores
- evaluation_evidence
- evaluation_feedback
- distributions
- distribution_targets
- consent_records
- audit_logs

### 参照資料

- [docs/class-diagram/README.md](../class-diagram/README.md)
- [docs/class-diagram/01-core-learning-flow.puml](../class-diagram/01-core-learning-flow.puml)
- [docs/class-diagram/02-evaluation-rubric.puml](../class-diagram/02-evaluation-rubric.puml)
- [docs/class-diagram/03-teacher-task-distribution.puml](../class-diagram/03-teacher-task-distribution.puml)
- [docs/class-diagram/04-ai-consent-log.puml](../class-diagram/04-ai-consent-log.puml)

### 詳細定義

詳細なテーブル定義は [table-definitions.md](table-definitions.md) に分離してある。
