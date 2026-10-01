# 状態ルール一覧

## 目的

このディレクトリは、画面ごとの状態設計を整理し、実装前の状態定義・遷移・操作制御をまとめるための場所です。

## 目次

### 学生向け

- [student/state-rules-overview.md](student/state-rules-overview.md): 学生向けの共通状態定義と画面ごとの表示差分
- [student/editor-state-rules.md](student/editor-state-rules.md): エディターの保存状態・提出状態・再提出制御
- [student/evaluation-state-rules.md](student/evaluation-state-rules.md): 評価結果の状態と表示制御
- [student/exercise-state-rules.md](student/exercise-state-rules.md): 演習の進行状況とファイル管理ルール
- [student/survey-state-rules.md](student/survey-state-rules.md): アンケート・同意確認の進捗と提出可否

### 教師向け

- [teacher/task-state-rules.md](teacher/task-state-rules.md): 課題の公開状態と論理削除ルール
- [teacher/distribution-state-rules.md](teacher/distribution-state-rules.md): 配信の全体状態とクラス別状態
- [teacher/prompt-state-rules.md](teacher/prompt-state-rules.md): プロンプト設計の保存・適用・版管理ルール
- [teacher/evaluation-state-rules.md](teacher/evaluation-state-rules.md): 評価の進行と再評価ルール
- [teacher/progress-state-rules.md](teacher/progress-state-rules.md): 学習進捗の集計と要対応状態

### 実装用

- [implementation-state-table.md](implementation-state-table.md): 実装に直結する状態、遷移、ボタン制御の一覧

## 運用ルール

- 状態は「保存状態」と「学習/公開状態」を分離して扱う。
- 画面ごとの表示ラベルと状態名は揃える。
- 操作可否は状態に紐づけて定義する。
- 実装前に、状態を変える条件と遷移先を明文化する。

## 使いどころ

- 画面設計のレビュー時
- 実装前の状態確認
- UI 上のボタンやラベルの定義確認
- 例外処理とバリデーションの整理
