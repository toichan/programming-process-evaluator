# 状態ルール一覧

## 目的

このディレクトリは、画面ごとの状態設計を整理し、実装前の状態定義・遷移・操作制御をまとめるための場所です。

## 目次

### 公式定義

- [state-naming-standard.md](state-naming-standard.md): 状態名の正規命名規約と役割ごとの使い分け

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

### 管理者向け

- [admin/account-state-rules.md](admin/account-state-rules.md): アカウント状態・セキュリティレベル・ログイン可否ルール

### 実装用

- [implementation-state-table.md](implementation-state-table.md): 分野別状態ルールと実装契約への索引。独立した状態定義ではない

## 運用ルール

- 状態は「保存状態」「学習状態」「公開/配信状態」を分離して扱う。
- 画面ごとの表示ラベルと状態名は揃える。
- `status` という汎用名ではなく、`saveStatus` / `learningStatus` / `publicationStatus` / `distributionStatus` のように役割ごとに命名する。
- 操作可否は状態に紐づけて定義する。
- 実装前に、状態を変える条件と遷移先を明文化する。

## 使いどころ

- 画面設計のレビュー時
- 実装前の状態確認
- UI 上のボタンやラベルの定義確認
- 例外処理とバリデーションの整理
