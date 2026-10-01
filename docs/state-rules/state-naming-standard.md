# 状態命名標準

## 1. 目的

この文書は、状態の設計において名前の意味がぶれないようにするための正式な命名規約を定義する。

> 重要: `status` という名前だけで状態を表さず、役割ごとに状態名を分ける。

## 2. 基本方針

### 2-1. 状態は役割ごとに分ける

1. 保存状態
   - コードやフォームの保存状態を表す
   - 例: `draft`, `saved`, `submitted`
2. 学習状態
   - 学習の進行状態を表す
   - 例: `not_started`, `in_progress`, `completed`
3. 公開/配信状態
   - 課題や配信の公開状態を表す
   - 例: `draft`, `published`, `requires_update`, `distributed`

### 2-2. 1つの変数に複数の意味を混ぜない

次のような命名は避ける。

- `status` のみで保存状態・学習状態・公開状態を全部表す
- `taskStatus` に保存状態まで含めてしまう
- `studentStatus` のように意味が広すぎる名前を使う

## 3. 推奨命名規約

### 3-1. 保存状態

- `saveStatus`
- 値:
  - `draft`: 未保存または編集中
  - `saved`: 保存済み
  - `submitted`: 提出済み
  - `resubmission_allowed`: 再提出可
  - `resubmission_forbidden`: 再提出不可

### 3-2. 学習状態

- `learningStatus`
- 値:
  - `not_started`: 未着手
  - `in_progress`: 進行中
  - `completed`: 完了
  - `needs_revision`: 再修正が必要

### 3-3. 課題公開状態

- `publicationStatus`
- 値:
  - `draft`: 下書き
  - `published`: 公開中
  - `requires_update`: 要更新
  - `archived`: 論理削除または非表示

### 3-4. 配信状態

- `distributionStatus`
- 値:
  - `not_distributed`: 未配信
  - `scheduled`: 配信予定
  - `in_progress`: 配信中
  - `completed`: 配信完了
  - `stopped`: 停止中

### 3-5. 評価状態

- `evaluationStatus`
- 値:
  - `not_started`: 未評価
  - `in_progress`: 評価中
  - `completed`: 評価済み
  - `needs_revision`: 再評価対象

## 4. 正書法のルール

- 変数名は意味が明確な英語を使う
- UI 表示ラベルと内部状態名は対応させる
- 例: `submitted` は UI の「提出済み」に対応させる
- 画面ごとの表記ゆれは禁止する
  - 例: `draft` と `下書き` を混在させない

## 5. 例外ルール

### 5-1. 同じ意味を別名で書かない

次は避ける。

- `draft` と `not_submitted` を同じ概念で使う
- `published` と `open` を同じ意味で使う
- `saved` と `submitted` を同じ状態にまとめる

### 5-2. 役割が違う場合は別変数にする

例:

- `saveStatus`: 保存状態
- `learningStatus`: 学習状態
- `publicationStatus`: 課題公開状態
- `distributionStatus`: 配信状態

これらはすべて独立に扱う。

## 6. 実装時の運用ルール

- API や Servlet の引数では、曖昧な `status` は使わない
- DB カラムでも、意味が明確な名前を使う
- 画面では表示ラベルだけを変えても、状態名は変えない
- 画面表示とデータ状態がズレないようにする

## 7. 実装向けの最小整合ルール

1. 保存状態は `saveStatus` を使う
2. 学習状態は `learningStatus` を使う
3. 公開状態は `publicationStatus` を使う
4. 配信状態は `distributionStatus` を使う
5. 抽象的な `status` は使わない

## 8. 参照先

- [README.md](README.md): 状態ルール一覧の入口
- [implementation-state-table.md](implementation-state-table.md): 実装用の状態と遷移表
- [student/editor-state-rules.md](student/editor-state-rules.md): 学生側の状態定義
- [teacher/task-state-rules.md](teacher/task-state-rules.md): 教師側の状態定義
