# 実装時の状態ルール索引

以前このファイルには学生エディター・教師課題等の状態表をまとめていましたが、機能仕様と専用の状態ルールが更新されたため、同じ状態定義を重複保持しないよう一覧を廃止しました。

## 正本

| 対象 | 状態定義 |
|---|---|
| 状態名と状態の役割 | [状態命名標準](./state-naming-standard.md) |
| 生徒共通 | [生徒状態ルール概要](./student/state-rules-overview.md) |
| 生徒エディター | [エディター状態ルール](./student/editor-state-rules.md) |
| 生徒評価 | [評価状態ルール](./student/evaluation-state-rules.md) |
| 授業演習 | [演習状態ルール](./student/exercise-state-rules.md) |
| 同意・アンケート | [アンケート状態ルール](./student/survey-state-rules.md) |
| 教師課題・配信・プロンプト | [状態ルール一覧](./README.md#教師向け)から対象文書を選択 |
| 管理者アカウント | [アカウント状態ルール](./admin/account-state-rules.md) |

## 実装時の照合

- 画面上の状態・遷移・操作条件は、対象機能の専用状態ルールを参照する。
- 永続化する状態値とテーブル・カラムの対応、未合意事項は[実装契約](../system-configuration/implementation-contract.md)で確認する。
- 状態ルールとDB設計に差がある場合、コードで推測して埋めず、必要な仕様・DB設計の合意後に実装する。
- この索引自体は状態値・遷移条件を新たに定義しない。
