# Class Diagram Index

このディレクトリには、プロジェクトのドメイン設計を段階的に可視化した PlantUML 図をまとめている。

## 目次

1. [01-core-learning-flow.puml](01-core-learning-flow.puml)  
   学生の課題学習フローの中核となるモデル。`Student`、`Task`、`Submission`、`CodeLog`、`Evaluation` を中心に整理する。

2. [02-evaluation-rubric.puml](02-evaluation-rubric.puml)  
   評価の実行とルーブリック・スコア・根拠の関係を定義する。生成AI評価の仕組みを理解しやすくするための図。

3. [03-teacher-task-distribution.puml](03-teacher-task-distribution.puml)  
   教師向けの課題作成、公開、配信、プロンプト管理の責務を分離した図。

4. [04-ai-consent-log.puml](04-ai-consent-log.puml)  
   操作ログと同意情報を AI 評価入力へ変換する流れを示す。`EvaluationInputBuilder` と `GeminiAdapter` を中心に整理する。

5. [05-overall-summary.puml](05-overall-summary.puml)  
   前四つのスライスを統合した全体像。システム全体の境界と責務を横断的に確認する図。

## 使い方

- 最初に 01 から読むと、学習フローの前提が分かりやすい。
- 02 で評価の基準と根拠の扱いを確認する。
- 03 で教師側の責務を確認する。
- 04 で AI 連携とログの入力整形を確認する。
- 05 で全体を俯瞰する。

## 設計方針

- 1 枚の巨大図ではなく、責務ごとに分割して理解しやすくする。
- 「保存状態」と「学習・公開状態」を分離する設計思想を維持する。
- 実装前にデータの責務境界を明確化することを目的とする。
