# ドキュメント案内

このページは、仕様・実装・進捗を記録する文書の入口です。内容を重複して書き直さず、各テーマの正本を参照してください。

## まず読む

| 目的 | 正本・入口 |
|---|---|
| 開発環境の起動と日常操作 | [リポジトリREADME](../README.md)、[AGENTS.md](../AGENTS.md) |
| 機能要件・制約・優先度 | [機能仕様書](./function-specification.md) |
| 画面と画面間の導線 | [画面遷移図](./screen-flow-diagram.md) |
| 画面デザイン・操作イメージ | [画面プロトタイプ](../screen-flow-diagram/webapp/) |
| 現在の実装順と進捗 | [実装ロードマップ](./system-configuration/implementation-roadmap.md) |
| 機能別の作業結果・検証記録 | [機能別実装計画](./system-configuration/feature-plans/) |

## 開発・設計

| 文書 | 役割 |
|---|---|
| [開発からデプロイまでの全体手順](./system-configuration/development-and-deployment-flow.md) | 要件確認から本番運用までの段階と本番公開前の未決事項 |
| [実装とローカル開発の詳細手順](./system-configuration/implementation-and-local-development.md) | 実装規約、設計資料の使い分け、ローカル開発ループ |
| [実装ロードマップ](./system-configuration/implementation-roadmap.md) | 機能実装の順序と現在の進捗。各機能の詳細な検証記録は機能別計画を参照 |
| [実装契約](./system-configuration/implementation-contract.md) | 仕様・状態・DBの対応、未合意事項、決定履歴 |
| [機能別実装計画テンプレート](./system-configuration/feature-plan-template.md) | 新しい機能作業の計画ひな型 |
| [システム構成ガイド](./system-configuration/system-strucure-guide.md) | Java層、主要ディレクトリ、ローカル構成 |
| [状態ルール一覧](./state-rules/README.md) | 状態名・遷移・操作可否の設計資料 |
| [DB設計](./database-design/README.md) | テーブル設計・項目定義 |
| [クラス図一覧](./class-diagram/README.md) | 責務とデータ関係の図 |

## 機能別・補助資料

- [Gemini API連携設計](./ai-api-integration-design.md): API要求、匿名化、失敗時処理、現在の疎通状況。
- [フィードバックUIガイド](./feedback-guideline.md): 共通の通知・確認UI。
- [入出力フォーマット](./format/): 課題、テストケース、ヒント、アンケート、評価データの形式。
- [評価ルーブリック](./rubric/): 評価観点の詳細。
- [サーバ維持費・AI API費の試算](./server-cost-estimate.md): 仮定に基づく試算。現在の確定価格としては扱わず、公開前に再確認する。
- [本番運用条件の決定記録](./system-configuration/production-operations-decisions.md): AWS・バックアップ・費用・運用など、公開前に人が決定する項目。

### 外部資料のスナップショット

[Google AI Studio Get Started.md](./system-configuration/Google%20AI%20Studio%20Get%20Started.md)はローカル参照用の外部ドキュメントコピーです。取得日時・上流版が記録されていないため、最新仕様の根拠にはせず、[Google Gemini API公式Get started](https://ai.google.dev/gemini-api/docs/quickstart)を優先してください。

## 文書の維持ルール

- 機能要件は機能仕様書、状態は状態ルール、永続化はDB設計、実装合意は実装契約を正本とする。
- 進捗の概要は実装ロードマップに置き、詳細な作業・テスト結果は対応する機能別実装計画に記録する。
- 画面プロトタイプや実装ファイルの存在だけで、本実装の完了とは判断しない。完了条件と検証結果を対応する機能別計画で確認する。
- 実装済み文書は監査可能な作業記録として残す。未使用の初期メモや、現状と一致しない計画を正本として参照しない。
