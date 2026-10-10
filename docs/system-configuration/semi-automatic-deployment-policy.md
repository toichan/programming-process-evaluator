# 半自動デプロイ方針と成果物ロードマップ

## 決定記録

- 決定日: 2026-10-10 JST。
- 決定者・根拠: 開発者・研究実施者本人の「半自動デプロイ方式の正式採用」指示。
- 状態: **ECR保存は実Push/再取得受入済み。EC2取得adapterを既存releaseへ接続し、
  IAM適用・本番受入は別承認待ち**。詳細は[保存契約](./ecr-release-publishing.md)と
  [半手動runbook](./ecr-manual-deployment.md)。
- 理由: 開発時間、運用コスト、研究上の優先順位を踏まえ、本番反映の判断を人間に残す。
- 本書は今後の成果物生成・配布・本番反映の前提。以前のGHCR推奨案、
  GitHub Actionsからの承認付き自動デプロイ構想より本決定を優先する。
  過去の調査・実績は履歴として残し、設定済み・実行済みと読み替えない。

## 正式な構成と実行境界

```text
PR/main → GitHub Actions Java/Shell CI
               ↓ 対象main SHAのCI成功確認
固定SHA → 7 image build/検証 → OIDC → Amazon ECR Private + release manifest
                                                    ↓
                         人間が対象versionを選び、EC2で明示実行
                         digest指定pull → 成果物受入 → 既存deploy処理
```

本番はAWS EC2のDocker Compose、公開先はstudent.ppeval.net / teacher.ppeval.net。
本番実態と復旧手順は[本番runbook](./production-deployment.md)を参照する。
本決定をAWS接続、本番変更、migration、初回デプロイの実行許可とは扱わない。

### 自動化対象

- PRのJava/Shellテスト、mainの対象SHAに対するCI成功確認。
- テスト済み固定SHAからのapp/tools/db/runner/broker/nginx/backupの7 image生成。
- platform/revision/source/migration/成果物完全性の検証。
- ECR Privateへの非公開保存、commit SHA・build ID・digestと結果の記録。
- 7 imageを一組として識別するrelease manifest生成・検証。
- 成果物生成・保存は最初は手動起動。安全性とhosted受入を確認した後にのみ、
  mainの対象SHAのCI成功を条件とする自動起動へ進む。

### 自動化しない操作

mainの自動merge、保存完了を契機とする本番更新、ActionsからのEC2/DB操作、
無人の本番deploy/migration開始/rollbackは実装しない。
本番反映・復旧開始は必ず人間が明示実行する。
人間が開始した既存deploy script内のバックアップ、migration、状態記録、
安全条件に従う失敗時復旧まで禁止・除去する意味ではない。

Actions→SSM自動deploy、GitHub Environmentsによる本番deploy承認、
Actionsからの本番rollback、main pushによる本番更新、完全無人CDは今回のスコープ外。
SSMによる人間の管理接続は維持する。将来拡張は可能な構成とするが、
未承認の自動deploy権限・実行経路を先行導入しない。

## AWS認証・保存方針

- 保存先は**Amazon ECR Private**。GHCRは今回の採用対象ではない。
- Actionsの認証はOIDC。長期AWSアクセスキーを導入しない。
- trust policyは対象repository/信頼するref等を限定する。PR由来の未信頼コードを
  本番認証情報やECR push権限付きjobへ接続しない。
- roleは対象ECR repositoryへのpush関連権限に限定する。
  ECR認証token取得などrepository ARNに限定できない操作は設計時に区別する。
  EC2更新、SSM実行、DB/本番secret取得、IAM変更権限を与えない。
- EC2 pullは可能な限り既存instance roleを使い、push roleと分離する。
- DB password、Gemini API key、TLS/age秘密鍵等をActionsへ渡さない。
- ECR保存容量・料金を監視し、保持・削除を設計する。
  **稼働release、DB所有release、rollback対象releaseは削除しない**。
  tagの保持だけでなく参照digestとmanifest/source等の復旧資産も保護する。
  日数だけのlifecycle ruleで保護を保証したことにしない。

## 現在の実装との照合

| 対象 | 現在の状態・再利用 | 追加が必要な内容 |
|---|---|---|
| Java/Shell CI | Phase 1完了。詳細は[CI手順](./continuous-integration.md) | 手動保存workflowへ対象SHA/event/workflow/check成功証跡を接続しhosted受入済み |
| 7 image隔離build | [Step 1](./production-image-validation.md)は認証なし経路を維持 | 別[保存経路](./ecr-release-publishing.md)のCI gate/export/Registry digest/release manifestは実Push受入済み |
| source manifest | [source-manifest.sh](../../scripts/production/source-manifest.sh)の全source hashを再利用 | 固定source archiveの取得と独立した信頼済みhash照合 |
| release準備 | [prepare-release.sh](../../scripts/production/prepare-release.sh)はEC2等でbuildし、ローカルtag/IDとREADYを生成 | ECR受入は別経路を追加。pullしたimageを再buildせず同じ契約へ接続 |
| deploy/rollback | 既存[deploy](../../scripts/production/deploy-release.sh)・[rollback](../../scripts/production/rollback-release.sh)を維持 | ECR受入済みreleaseと既存安全gateの統合検証 |
| AWS構成 | 承認済みの既存8ECR repo/publisher roleを使用。このローカル実装ではAWSを再照会していない | tag immutability/保持・費用・EC2 pull権限の運用受入と設定変更は別承認 |

Step 1の検証専用経路はmainの到達可能SHAだけを確認し、CI成功gateは新しい
手動ECR保存workflowに実装する。正確なpush/main CI runとJava/Shell checkを必須とする。
Image ID・revision・サイズ・platform・source hashのレポートを
Registry digestや信頼済みrelease manifestの代用にはしない。
Step 1の認証なし・pushなし・固有tag cleanupの検証経路は保持する。
ECR保存は認証なしbuildとfresh publish runnerに分離し、検証専用経路の境界を崩さない。

## 維持する本番安全契約

[prepare-release.sh](../../scripts/production/prepare-release.sh)、
[deploy-release.sh](../../scripts/production/deploy-release.sh)、
[rollback-release.sh](../../scripts/production/rollback-release.sh)、
[operation-common.sh](../../scripts/production/operation-common.sh)、
[deployment-state.sh](../../scripts/production/deployment-state.sh)、
[source-manifest.sh](../../scripts/production/source-manifest.sh)、
[database-preflight.sh](../../scripts/production/database-preflight.sh)、
[migration-preflight.sh](../../scripts/production/migration-preflight.sh)、
[smoke.sh](../../scripts/production/smoke.sh)を原則維持する。

固定SHA、immutable release、Image ID/source manifest照合、DB container/Volume識別、
S3暗号化backup/receipt検証、Flyway事前検証、DB/TLS/deploy lock、
rollback前のDB互換性、HTTPS/health、deployment stateを省略・弱化しない。
7 imageの新規生成は既存DB container/Volumeの更新許可ではない。
DB所有releaseはアプリ更新先と別に維持する。

Registry digest（manifest/index）とDocker Image ID（config）は別物として記録する。
manifestは各imageのrepository、digest種別、platform、config/Image ID、revisionを
同じcommit SHA/build IDに結び付ける設計とする。必要なsource/migration hashも照合する。
build ID/tag、schema 1、署名なしのECR writer限定信頼は[保存契約](./ecr-release-publishing.md)を正とする。

EC2受入は不完全な7 image集合を拒否し、全照合後にのみreleaseを利用可能にする。
既存本番tag/releaseを上書きしない。同SHAの再buildでIDが変わった場合も
黙って置換せず競合として止める。受入でdeployやmigrationを開始しない。

## 更新後のPhase 2ロードマップ

| 工程 | 状態・内容 | 依存・完了条件 |
|---|---|---|
| Phase 1 | GitHub Actions CI完了 | Java/Shellの受入記録を維持 |
| Step 1 | 認証なし7 image隔離buildのhosted受入済み | 既存run `38034067035`の受入。今回のexport/保存workflowは別のhosted受入が必要 |
| Step 2 | 保存契約/既存8repo・OIDC publisher roleを前提化 | 容量/費用、保持/削除と保護release、tag immutability等の運用受入を別途確認。今回IAM変更なし |
| Step 3 | ECR非公開保存（実Push/再取得受入済み） | run 38045471493、SHA 2f48c898f7cb9ebdf00809f1b83488a6f1b9811f。7 image/OCI保存照合成功 |
| Step 4 | 小さなEC2取得adapter | [半手動runbook](./ecr-manual-deployment.md)。digest pull/ID/source照合→既存release。IAM適用・EC2受入は別承認 |
| Step 5 | 既存回帰の再利用 | 取得mock異常系＋既存Java/Shell/deploy/rollback gate。大規模な新検証基盤は[残課題](./ecr-deployment-debt.md)へ延期 |
| Step 6 | 本番初回手動deploy（未承認・未実施） | Step 5受入、対象version/DB/backup/復旧/停止時間確認後、人間の明示承認・実行 |

既存Step 1のhosted受入を、新しいexport/保存経路の受入と混同しない。
実際の保存・本番受入・deployは依存工程の受入と承認を省略しない。
main CI成功による成果物生成の自動起動は、手動起動での安定確認後に別途導入する。

## 技術的懸念・未確認事項

- CI成功は正確なSHA、信頼するworkflow、mainのevent、成功conclusionと照合する。
  PRのmerge用SHA、他workflow、古い成功の取り違えを拒否する。
- 同一SHAでもbase tag・外部依存により再build結果は変わり得る。
  build ID/digestで区別し、選択済み7 imageを混在させない。
- manifestのhashだけでは発行元を証明しない。保存場所・信頼根・改ざん/取り違え防止、
  7 imageの途中push失敗時の未完了扱いを設計する。
- Actions artifactの7日保存は長期復旧資産の保持には不十分。
  source archive、source manifest、release manifestの保存先・期限・取得権限を別途設計する。
- ECR圧縮layer容量はDocker表示サイズ合計とは異なる。
  region料金、build頻度、保持世代、共有layer、転送・scan費用と監視閾値は未確定。
- 初回公開時に8ECR repositoryのIMMUTABLE/AES256/scanOnPush、
  lifecycle/repository policy未設定・各1imageを実確認済み。将来の変更/容量は別確認。
- Actions OIDC/publisherは実疎通済み。EC2 roleのpull権限適用と
  EC2疎通は別承認・別受入。未確認を不存在・確認済みと推測しない。
- image取得後もmigrationの部分DDL、旧アプリとDB互換性、backup復元制約は残る。
  image rollbackだけでDBを戻せるとは扱わない。

## 今回の変更範囲と次の作業

当初の方針決定は文書化のみ。その後の承認で[手動ECR保存](./ecr-release-publishing.md)の
workflow/scripts/testsと関連文書を実装する。Step 1には任意exportのみ追加し、
既存検証workflow、本番Dockerfiles/application/migrations/deployment scriptsは維持する。
この実装作業ではAWS/IAM/ECR、本番環境の操作、commit/push/mergeは行わない。

2026-10-10 JSTの方針変更: 必要最小限のadapterをPR/必須CI/通常mergeで受入し、
ECR読取りIAM案の承認、その適用後の本番取得/deployの別承認へ進む。
包括的hardening・高度な検証は残課題として保持するが、今回の必須工程へ追加しない。
本番health/DB維持/rollback準備の受入後にPhase 2を完了とし、機能開発へ戻る。
