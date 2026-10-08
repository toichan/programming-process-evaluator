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

## 作業記録の区分（2026-10-04）

進捗の正本は[実装ロードマップ](./system-configuration/implementation-roadmap.md)です。完了済み文書は削除・移動せず、根拠を調べるための記録として残します。以下の完了範囲を超えて、後続機能や未確認事項まで完了と扱わないでください。

### 完了済みの作業記録

| 対象 | 記録・完了範囲 |
|---|---|
| 工程1〜4の基盤 | [構成整合](./system-configuration/feature-plans/system-structure-alignment.md)、[初期DBマイグレーション](./system-configuration/feature-plans/initial-database-migration.md)、[DB接続・利用者DAO](./system-configuration/feature-plans/database-connection-and-user-dao.md)、[Web共通基盤](./system-configuration/feature-plans/web-common-foundation.md)。後続のスキーマ追加・業務画面移植を含まない |
| 生徒演習 | [授業演習計画](./system-configuration/feature-plans/student-exercise.md)T001〜T051。教師配信・進捗制御・教師/管理者の完全削除は後続範囲。OS標準ダイアログ等の未確認事項は同計画に保持 |
| 教師工程前デバッグ | [デバッグ計画](./system-configuration/feature-plans/pre-teacher-debugging.md)T001〜T014と[エラーレポート](./system-configuration/error-report.md)。既知5件は修正・再検証・反映・清掃済み。過去の失敗分類は履歴であり、現在の未解決バグではない |

### 実装済み範囲・受入保留を確認する記録

| 対象 | 記録・残る範囲 |
|---|---|
| 認証・学校管理 | [認証計画](./system-configuration/feature-plans/authentication-and-login.md)。教師業務機能・教師アカウント管理・管理者画面統合・本番認証条件の完了を意味しない |
| 生徒ホーム・同意・アカウント | [生徒ホーム等の計画](./system-configuration/feature-plans/student-home-consent-account.md)。ID統一・認証情報履歴等も記録済み。個別の未確認事項を保持 |
| 生徒エディター | [エディター計画](./system-configuration/feature-plans/student-editor.md)。主要実装済み、30秒記録・競合・重複提出・再提出等の受入残件あり |
| 評価・コードログ・課題別アンケート | [評価・アンケート計画](./system-configuration/feature-plans/student-evaluation-survey.md)。主要実装と既知バグ検証は済み、正式設定・ユーザー手動E2Eは教師画面完成後。全体アンケートはシステム対象外 |

### 次工程・後続範囲

次は[ロードマップの工程10](./system-configuration/implementation-roadmap.md)です。[教師課題下書きの初回スライス計画](./system-configuration/feature-plans/teacher-task-draft.md)のS1・T001〜T015は完了しました。専用DBの受入でV1〜V16と権限/状態/競合/rollback、認証済みHTTP回帰、全テスト180件（成功126、失敗0、skip54）とWAR生成を確認済みです。その後、ユーザーの確認用に8080の開発DBもV16とし、合成テスト教師アカウントを追加、教師ホームと課題編集を8080で表示確認しました。認証情報は計画文書やソースに保存していません。次はS2（ルーブリック/プロンプト準備・AI生成）の計画具体化です。詳細は[最終受入レポート](./system-configuration/feature-plans/checkpoints/teacher-task-draft/batch-report-T014-T015.yaml)、8080環境更新の詳細は[エラーレポート](./system-configuration/error-report.md)と[AGENTS.mdの引継ぎ](../AGENTS.md)を参照してください。画面遷移図・プロトタイプは変更していません。正式な評価/アンケートE2E、工程11〜13は後続の保留としてロードマップに保持します。

## 機能別・補助資料

- [Gemini API連携設計](./ai-api-integration-design.md): API要求、匿名化、失敗時処理、現在の疎通状況。
- [フィードバックUIガイド](./feedback-guideline.md): 共通の通知・確認UI。
- [入出力フォーマット](./format/): 課題、テストケース、ヒント、アンケート、評価データの形式。
- [評価ルーブリック](./rubric/): 評価観点の詳細。
- [サーバ維持費・AI API費の試算](./server-cost-estimate.md): 仮定に基づく試算。現在の確定価格としては扱わず、公開前に再確認する。
- [本番運用条件の確認テンプレート](./system-configuration/production-operations-decisions.md): AWS・バックアップ・費用・運用など、公開前に人が決定する項目。
- [デプロイ準備調査](./system-configuration/deployment-readiness-assessment.md): 現行コード・設定の実装根拠、公開前の不足事項、確認質問、機能開発と並行できる準備順。決定内容の正本は上記の本番運用条件。

### 外部資料のスナップショット

[Google AI Studio Get Started.md](./system-configuration/Google%20AI%20Studio%20Get%20Started.md)はローカル参照用の外部ドキュメントコピーです。取得日時・上流版が記録されていないため、最新仕様の根拠にはせず、[Google Gemini API公式Get started](https://ai.google.dev/gemini-api/docs/quickstart)を優先してください。

## 文書の維持ルール

- 機能要件は機能仕様書、状態は状態ルール、永続化はDB設計、実装合意は実装契約を正本とする。
- 進捗の概要は実装ロードマップに置き、詳細な作業・テスト結果は対応する機能別実装計画に記録する。
- 画面プロトタイプや実装ファイルの存在だけで、本実装の完了とは判断しない。完了条件と検証結果を対応する機能別計画で確認する。
- 実装済み文書は監査可能な作業記録として残す。未使用の初期メモや、現状と一致しない計画を正本として参照しない。
