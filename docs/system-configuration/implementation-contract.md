# 実装契約: 状態・データ・画面の対応

この文書は、既存の機能仕様・状態ルール・クラス図・DB 設計を、本実装の Servlet / Service / DAO / JSP で一貫して扱うための確認記録です。個別資料の内容を置き換えず、資料間の対応と合意状況を記録します。

## 使い方

- 実装前に、対象機能に関係する行を確認します。
- 確定していない行を実装者が推測で確定してはいけません。影響、選択肢、推奨案を提示し、仕様責任者の確認後に更新します。
- `確定` にした場合は、根拠資料と確認日を記録し、必要に応じて機能仕様・状態ルール・DB 設計書も更新します。
- 新しい状態やカラムをコードだけに追加しません。仕様と DB 設計の合意後にマイグレーションを作成します。
- 機能ごとの作業前計画は[機能別実装計画テンプレート](./feature-plan-template.md)で作成します。ロードマップの詳細は[実装ロードマップ](./implementation-roadmap.md)を参照してください。

## 状態概念の対応

| 概念 | 正規の役割名 | 代表的な対象 | 主な根拠 | 合意状況 |
|---|---|---|---|---|
| 保存状態 | `saveStatus` | 生徒の課題コード、フォーム入力 | [状態命名標準](../state-rules/state-naming-standard.md)、[エディター状態ルール](../state-rules/student/editor-state-rules.md) | 要確認: `draft` / `saved` / `submitted` と提出状態の境界 |
| 学習状態 | `learningStatus` | 生徒ごとの課題・演習の進捗 | [状態命名標準](../state-rules/state-naming-standard.md)、[学生共通状態](../state-rules/student/state-rules-overview.md)、[教師進捗状態](../state-rules/teacher/progress-state-rules.md) | 要確認: `tasks.learning_status` と生徒ごとの進捗の保存場所 |
| 課題公開状態 | `publicationStatus` | 教師が作成する課題 | [課題状態ルール](../state-rules/teacher/task-state-rules.md) | 要確認: `private` / `published` / `archived` と `draft` / `published` / `requires_update` / `archived` の対応 |
| 配信状態 | `distributionStatus` | 課題配信全体・対象クラス | [配信状態ルール](../state-rules/teacher/distribution-state-rules.md) | 要確認: 全体状態とクラス別状態の保存値・遷移 |
| 評価状態 | `evaluationStatus` | AI / 教師による評価処理 | [学生評価状態](../state-rules/student/evaluation-state-rules.md)、[教師評価状態](../state-rules/teacher/evaluation-state-rules.md) | 要確認: 評価待ち・未評価・評価中・完了・失敗・要修正・再評価対象の対応 |
| プロンプト状態 | `promptStatus` | 教師が設計するプロンプトの版 | [プロンプト状態ルール](../state-rules/teacher/prompt-state-rules.md) | 要確認: `approved` 等の DB 値と設定済み・版管理済み状態、履歴の持ち方 |
| 同意状態 | `consentStatus` | 生徒の研究協力同意 | [アンケート状態ルール](../state-rules/student/survey-state-rules.md)、[AI 連携設計書](../ai-api-integration-design.md) | 要確認: 同意・不同意・撤回・未確認の画面遷移と AI 実行可否 |
| 演習状態 | `exerciseStatus` | 授業演習の進捗 | [演習状態ルール](../state-rules/student/exercise-state-rules.md) | 要確認: 永続化先が現行テーブル定義にない |
| アンケート状態 | `surveyStatus` | アンケート回答 | [アンケート状態ルール](../state-rules/student/survey-state-rules.md) | 要確認: 回答保存先が現行テーブル定義にない |

## 資料間の要確認事項

以下は既存資料を確認して見つかった不一致・不足の候補です。ここでは値を決めず、合意されるまで未確定として扱います。

| ID | 対象 | 確認できた差異・不足 | 実装前に決めること | 状況 |
|---|---|---|---|---|
| IC-001 | 課題状態 | [課題状態ルール](../state-rules/teacher/task-state-rules.md)は「下書き / 公開中 / 要更新 / 論理削除」を定義する一方、[`tasks.publication_status`](../database-design/table-definitions.md) は `private / published / archived` | 正規状態値、旧値との対応、削除・復元方法 | 要確認 |
| IC-002 | 課題と生徒進捗 | [`tasks`](../database-design/table-definitions.md) に `save_status` / `learning_status` があるが、課題自体の状態と生徒個人の状態が区別しにくい | 生徒単位で状態を持つべき対象とテーブル・キー | 要確認 |
| IC-003 | 提出状態 | [`submissions.submission_status`](../database-design/table-definitions.md) の `draft / submitted / re_submittable / locked` と、状態命名標準・[実装用状態表](../state-rules/implementation-state-table.md) の保存状態・学習状態が一致しない | 提出状態と保存状態の分離、再提出許可を表す正規値 | 要確認 |
| IC-004 | 評価状態 | [`evaluations.evaluation_status`](../database-design/table-definitions.md) の `pending / in_progress / completed / failed / revision_requested` と学生・教師の画面状態の語彙が異なる | 内部状態、UI 表示ラベル、再評価履歴の保存方法 | 要確認 |
| IC-005 | 演習データ | [授業演習状態ルール](../state-rules/student/exercise-state-rules.md)はフォルダ・ファイル・進捗を定義するが、現行[テーブル定義](../database-design/table-definitions.md)に対応する保存先が見当たらない | 演習、ファイル、所有者、学習状態を保存するモデル | 要確認 |
| IC-006 | アンケート回答 | [アンケート状態ルール](../state-rules/student/survey-state-rules.md)と機能仕様書に回答保存・提出があるが、現行テーブル定義に回答テーブルが見当たらない | 回答、設問、提出時刻、再編集可否、履歴の保存方式 | 要確認 |
| IC-007 | プロンプト版管理 | [プロンプト状態ルール](../state-rules/teacher/prompt-state-rules.md)は既存版を上書きせず履歴を保持するが、現行 `prompts` 定義の版・状態・有効フラグだけで履歴をどう表すか不明 | 版の一意性、適用版、下書き、履歴・参照関係 | 要確認 |
| IC-008 | 匿名化と所属情報 | 機能仕様書は生徒の完全匿名性を求める一方、教師は学校・クラス所属範囲で閲覧する必要がある | 認証用ユーザー識別子と研究分析データの分離・参照方式 | 要確認 |
| IC-009 | アカウント項目 | 機能仕様書にセキュリティレベル・初回パスワード変更状態があるが、[`users`](../database-design/table-definitions.md) の項目に見当たらない | 保存項目と初期パスワード / リセットの運用 | 要確認 |

## 機能別実装契約の記録欄

対象機能に着手するとき、下表を複製して対象機能について埋めます。小規模変更なら[機能別実装計画テンプレート](./feature-plan-template.md)に同じ内容を記録しても構いません。

| 項目 | 決定内容 |
|---|---|
| 機能・画面 | 要記入 |
| 参照する機能仕様の節 | 要記入 |
| 画面遷移・URL・ロール | 要記入 |
| 関連する状態ルールと状態名 | 要記入 |
| DB テーブル / カラム / 正規値 | 要記入 |
| DB から読み取るデータと範囲条件 | 要記入 |
| 操作入力・サーバー側検証 | 要記入 |
| Service が許可する状態遷移 | 要記入 |
| 権限・所有者・学校 / クラスの境界 | 要記入 |
| エラー・空状態・再試行時の動作 | 要記入 |
| 合意者・合意日・変更した設計資料 | 要記入 |
| 状況 | 未着手 / 要確認 / 合意済み / 実装・検証済み |

## 合意状況の更新履歴

| 日付 / 記録 | ID / 対象 | 決定・変更内容 | 反映した資料 | 状況 |
|---|---|---|---|---|
| 初版 | 全体 | 既存資料間で確認が必要な対応候補を登録。値の推測確定は行っていない | 状態ルール、テーブル定義 | 要確認 |
