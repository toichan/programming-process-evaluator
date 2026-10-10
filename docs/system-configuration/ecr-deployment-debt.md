# 半手動デプロイ後の技術的負債

2026-10-10 JSTの方針変更で次を今回の必須実装から外した。
**解決済みではない**。Phase 2は小さなadapterと既存安全gateで完成させる。
2026-10-11の優先順位と権限障害後の再デプロイ前提条件は
[以下の改善保留記録](#ecrビルド経路の権限障害と改善保留2026-10-11-jst)を正とする。
以下の見積もりは単独作業の目安であり、機能開発再開を止める追加工程の指示ではない。
重大な露出が実測された場合は最小回避策を示して停止・承認判断する。

| 項目 | 現在の状況 | 残るリスク | 推奨対応 | 優先度 | 将来の作業量 |
|---|---|---|---|---|---|
| IMDSアクセス制限 | IMDSv2 required/hop2。childはnetwork none、host firewallは未検証 | IMDSv2だけではcontainer credential取得を防げない | 実構成確認、host/bridge経路blockと回帰 | 高 | 4–8h |
| Docker Broker権限 | brokerのみsocket mount。要求allowlist/child非root/cap dropを実装 | broker侵害はhost daemon相当の権限へつながる | API境界/allowlist再評価、必要操作のみのproxy/isolation検討 | 高 | 1–3日 |
| Docker socket対策 | app/childへ直接mountなし。host実mount未検証 | misconfiguration/横移動、broker compromise | 実mount/daemon設定確認、socket proxy/rootless適合性検討 | 高 | 1–2日 |
| SSM Parameter Store縮小 | AmazonSSMManagedInstanceCoreにGetParameter/GetParameters *。狭いinline Allowでは打ち消せない | role漏えい時の秘密情報読取範囲 | agent必要性を実測、managed policy代替/explicit deny設計を別承認 | 高 | 4–8h |
| IAM追加hardening | EC2 ECR読取は承認済み8repo/東京の最小policyを適用済み。既存policy包括改訂なし | 既存SSM/S3/KMS等が漏えい時に悪用される | 実経路/使用権限を調査しrole分離/条件を評価 | 高 | 1–2日 |
| OCI署名/provenance | 固定digest・CI証跡を検証するが署名なし | ECR writer/main管理者侵害時に正当性を独立証明できない | 署名・発行者verification/鍵管理方式を選択 | 中 | 1–3日 |
| 高度manifest検証 | 既存schema/7件/source/rawdigest/config/ID照合を再利用 | duplicate JSON keyや高度なparser差、オンラインCI再照合なし | 共通validator拡張と実fixture互換性確認 | 中 | 4–8h |
| tar厳密安全性 | path/重複entry/link/特殊fileの基本拒否のみ | parser差、PAX/サイズ/展開資源上限の高度な攻撃は未網羅 | 構造parser・展開上限/攻撃fixture。review済みwriter信頼も再評価 | 中 | 1–2日 |
| ECR lifecycle | IMMUTABLE、policyなし | 無制限保持で容量/料金増加 | current/rollbackの保護基準を確定して別承認で導入 | 中 | 4–8h |
| 長期保持/削除 | current/rollback削除禁止。自動GCなし | 容量枯渇、必要release削除の運用ミス | image/OCI/source一体の保持inventoryと削除dry-run | 中 | 1–2日 |
| 追加自動化 | manual dispatch＋人間の取得/deploy、Actions→EC2なし | 人間の選択・操作ミス | 限定preflight/最終image検証補助のみ検討。現方針では自動本番deployは禁止 | 低 | 1–3日 |
| セキュリティ回帰拡充 | producer/adapter mockと既存Shell/Java回帰 | 本番firewall/socket/IMDS境界の証明ではない | 隔離ホストで実ネットワーク/権限境界test | 高 | 1–3日 |
| Python sandbox runtime | 2026-10-10 EC2のpython:3.12-alpine/linux amd64実IDとbroker指定を確認。7 PPE imageとは別 | 可変upstream tag。更新後の実演習フローは未検証 | デプロイ後の演習受入。将来は独立digest管理/保持 | 高（受入前確認） | 確認1–2h、管理4–8h |
| 新operations配布 | 86936306...を独立配置しECR取得受入済み。TLS wrapper byte同一、active pointer未変更 | デプロイ時に旧operationsを誤選択するリスク | 新operationsを明示指定し既存gateを維持 | 高（deploy前必須） | 確認30分＋承認待ち |
| DB復旧訓練 | receipt/Version ID/暗号化/互換性gateを維持 | image rollbackでDDLは戻らない。鍵custody/restore実効性 | 隔離DB restoreと業務データ再読込を定期実施 | 高 | 半日–1日 |
| 取得中の容量/排他 | adapter lock/private staging、失敗時cache/tag保持 | local build/他操作との競合、disk枯渇 | 初回は人間が容量/並列不在確認。将来共通取得lock/容量閾値 | 中 | 2–4h |
| Docker store portability | Docker29/containerdは固定Descriptor＋local config bytes照合で実受入済み。標準host socket/moby/ctrを要求 | 非標準socket/namespace・将来daemon変更では取得拒否。圧縮/展開双方の保存で容量増 | daemon変更時に互換テスト。必要になった場合のみsocket対応を追加し、既存store切替/restartで回避しない | 中 | 2–4h |
| 復号鍵の独立保管 | 既存Mac鍵で固定バックアップの再復号認証成功。人間が今回の復旧reference根拠として承認。独立offlineコピーは未確認 | Mac紛失/故障と鍵喪失が重なるとage復号不能。S3/KMSだけでは復旧できない | Phase 2後、所有者が独立した安全な鍵保管と利用可能性を確認。秘密鍵をEC2/Gitに置かない | 高 | 1–2h＋所有者確認 |
| GitHub Pages関連 | 既存CI文書ではPages/Copilotを独立workflowとして区分。今回Pages設定・公開状態・失敗原因は調査していない | Java/Shell CI成功とPages公開成功を混同する可能性 | 必要時に既存設定/履歴を別途確認。今回新workflow・公開設定変更はしない | 低（P2保留） | 未見積もり |

現時点のsource reviewではstudent childからhost資格情報への確定したexploitは見つからなかった。
これは本番hostの安全証明ではない。実firewall、override、mount、環境変数、パッチ状態は未検証。
ECR権限追加は読取りだけでも、既存SSM/backup権限を含むroleの漏えい影響を消さない。

## ECRビルド経路の権限障害と改善保留（2026-10-11 JST）

### 優先順位・実施時期

| 優先度 | 作業 | 現在の方針 |
|---|---|---|
| P0 | 本番環境の復旧・安定稼働 | 最優先。復旧は別の明示承認と既存安全手順で実施する |
| P1 | 卒業研究に必要な機能追加・改善 | 安定稼働を優先した上で進める |
| P2 | ECRデプロイ基盤の改善・技術的負債解消 | 下記A〜Dの実装を保留。研究の進捗と本番更新の必要性から後日判断する |

表の既存項目の「高/中/低」は各負債の重要度であり、このP0/P1/P2の実施順を変更しない。
今回の整理は**ドキュメントのみ**。Dockerfile、CI/CD、復旧処理の変更を指示・実施するものではない。
Phase 2完了も意味しない。03:25 JSTの確認ではnginx障害による外部HTTPS停止中だった。
現在の稼働状態は今回再確認しておらず、その後の復旧成功を推測して記録しない。
その後の別承認による07:11 JSTの[旧release復旧記録](./error-report.md#2026-10-11-0711-jst-ef55失敗から旧releaseへの緊急復旧成功)を確認済み。
2026-10-11最終整理ではAWSへ再接続せず、この復旧成功とECR移行未完了を区別して保持する。
Phase 2開発は一時終了し、改善未完了のまま機能開発を優先する。

**次に本番へ新releaseを反映する場合の最低前提条件**は、
対象releaseの実際の最終イメージを固定digestで起動検証することと、
当該新旧release・CURRENT DB履歴に適合した安全な復旧手順を確認すること。
このrelease単位の条件はP2全体の実装保留によって省略しない。
検証失敗・未確認のreleaseはデプロイ可能と判断せず、人間の明示承認による手動実行を維持する。

### 障害の事実と原因の確度

初回はEC2上でローカルビルドしたイメージで稼働した。その後GitHub Actionsでビルドし、
ECR経由でEC2へ取得する方式に移行して、次の権限障害が発生した。
実行時刻・固定release・復旧・backupの詳細は
[デプロイrunbook](./ecr-manual-deployment.md)と[エラーレポート](./error-report.md)を参照し、
ここでは横断的な原因と未対応事項を管理する。

| 対象release | 発生/失敗段階（JST） | 旧releaseへの公開復旧確認 | 残ること |
|---|---|---|---|
| `2f48c898f7cb9ebdf00809f1b83488a6f1b9811f` | 2026-10-10 21:12の失敗記録、app_started（Runner読込拒否） | 同日21:18:43。復旧時の公開bind問題も別承認の最小復帰で解消 | Runner修正済みだが当該immutable releaseは修正/再利用しない |
| `043d875d760a080b8cff552b1f81ee8d47e5dc31` | 同日22:09:23〜22:12:31のupdate、app_started | 同日22:18:59 | app修正済み、当該immutable releaseは保持し再利用しない |
| `ef55edb79cae870d0c1238ae05273c9f34651a0e` | 2026-10-11 03:20:47〜03:24:03のupdate、nginx_started | 同日07:11:37外部確認（rollback完了07:11:23） | nginx直接原因の確定/修正とECR最終image受入は未完了 |

各復旧は旧`0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`を使用し、
DB・Volume・Flyway履歴を保持した。表の時刻はログ/確認時刻であり、
連続監視で測定した厳密な停止時間ではない。過去の失敗・復旧記録は削除しない。

| 障害 | 対象・実行UID | 確認した事実 | 修正・残る確認 |
|---|---|---|---|
| 1回目：Runner起動失敗 | `/runner/runner.py`、UID65532 | root所有0600のファイルを読めずPermission denied | PR #56で権限修正済み。後続updateではRunner起動成功 |
| 2回目：app起動失敗 | `/usr/local/bin/ppe-app`、UID10001 | root所有0600。修正前Dockerfileと0600入力から同じPermission denied・exit2を実Dockerで再現。親ディレクトリ0755も確認 | PR #57でlauncher/Tomcat設定のCOPY権限を明示。後続ef55 updateでapp起動成功 |
| 3回目：nginx起動失敗 | `/etc/nginx/nginx.conf`、UID101 | ef55 updateで`open() "/etc/nginx/nginx.conf" failed (13: Permission denied)`。nginx restarting/unhealthy、両HTTPS接続拒否。公開bind0.0.0.0:80/443は維持 | 未修正。実最終ファイルmode/owner、親権限、mount、Dockerfile/ビルド経路を確認し直接原因を確定する |

**主要因の仮説:** ECR公開時のソース展開で使うumask077とDockerfileの通常COPYによる
入力modeの継承が、非rootプロセスの読込拒否につながる。
ビルド経路が異なるため、EC2ローカルビルドとECR公開ビルドで権限が変化し得る。
appの再現結果はこの機序を裏付けるが、**3件すべての同一直接原因が実証済みとはしない**。
特にnginxの実ファイル属性・親ディレクトリ・mountを調査せず「root0600が確定」とは記載しない。

**検証の限界:** PR #57の隔離Compose試験はローカルビルドの起動成功を確認したもの。
0600入力による再現範囲はapp/toolsで、nginxのビルドcontextは通常のものだった。
ローカル再ビルドの成功、digest/source/Image IDの照合、READYは、
ECRに公開された最終イメージが本番相当構成で正常起動することの証明ではない。
今回のnginx障害はこの未確認範囲を残している。

### 将来の改善項目（すべて実装保留）

| ID | 改善 | 状態 | 完了判断に必要な証拠 |
|---|---|---|---|
| ECR-PERM-A | 全7イメージの権限監査 | 未着手（個別Runner/app修正は済み、全量監査ではない） | 各最終digestと実UID/GID、必要ファイル/親権限/mountの監査結果、適切な権限での実起動 |
| ECR-PERM-B | ECR最終イメージの隔離統合テスト | 未実施（ローカルCompose試験とは別） | digest/Image ID照合と本番相当5サービス、HTTPS、Runner、restart、DB保全の実行証拠 |
| ECR-PERM-C | 公開後・本番前のGitHub Actions検証 | 未実装 | 最終イメージ検証失敗でデプロイ可能判定を拒否し、手動承認を維持する回帰結果 |
| ECR-PERM-D | 失敗時の安全な復旧手順 | 改善未実装（既存手順の適用確認は別） | migration有無と各失敗段階の復旧/停止、state整合、DB保全、外部HTTPS復旧の回帰結果 |

#### 改善A：全Dockerイメージの権限監査

- 対象はapp、tools、db、runner、broker、nginx、backupの7イメージ。
  Dockerfile USERだけでなく、本番Compose overrideを含む実行UID/GIDを確認する。
- 起動スクリプト、設定ファイル、実行ファイルと各親ディレクトリのowner/mode、
  bind mount/Volumeによる上書き・アクセスへの影響を監査する。
- ファイルの用途に応じCOPY --chmod等で権限を明示する方針を検討する。
  一律0644にはしない。直接実行するファイルのexecute bit、
  `sh`等が読むスクリプトのread権限、秘密ファイルの限定アクセスを個別判断する。
- root起動、過剰な権限付与、セキュリティ設定の緩和で回避しない。

#### 改善B：ECR最終イメージの統合テスト

- ローカル再ビルドではなく、ECRから固定digestで取得した実際の最終イメージを使用する。
  OCI release/image digest、source、Image IDを照合する。
  Docker29/containerdのmanifest digestとconfig IDは区別し、既存照合方式を維持する。
- 隔離環境で、本番同等の非root UID、起動コマンド、環境変数の構造、
  サービス間依存関係とmount構成を再現する。本番Secrets/研究データは使わず合成データとテスト用Secretsを使う。
- 稼働5サービス（DB/broker/Runner/app/nginx）のhealth、nginx起動と公開bind、
  生徒/教師HTTPS・ログイン画面表示、Runnerの正常動作、restart回数を確認する。
  tools/backupは常駐5サービスと区別し、必要な用途別非破壊検証を行う。
- 隔離DBの保存・再読込とcontainer/Volume保持を確認し、
  成功・失敗・skip・未確認範囲を記録する。ビルド成功だけでは受入完了としない。

#### 改善C：GitHub Actionsの検証強化

- ECR公開後・本番デプロイ前に改善Bの最終イメージ検証を実施できる仕組みを検討する。
  公開成功/READYと起動受入の結果を区別し、検証失敗のreleaseをデプロイ可能と扱わない。
- 既存必須CI/ブランチ保護は緩和しない。本番反映は引き続き人間の明示承認による手動操作。
  **Actions実行・ECR公開を契機とする自動本番デプロイは禁止する。**

#### 改善D：失敗時の復旧手順改善

- [deploy-release.sh](../../scripts/production/deploy-release.sh)、
  [rollback-release.sh](../../scripts/production/rollback-release.sh)、
  [deployment-state.sh](../../scripts/production/deployment-state.sh)の条件を調査する。
- 今回はnginx_startedが既存自動復帰条件（phase index<=40）の範囲外で、
  apps_restored=no/recovery_attempted=noのまま外部HTTPSが停止した。
  この事実は自動復帰条件を無条件に拡大してよい根拠にはしない。
- nginx起動失敗時の安全な旧サービス復帰、current-release/state/実Image IDの整合、
  未publish時のCURRENT旧→target旧referenceと新→旧referenceの違い、
  DB migration実施/未実施/部分失敗による条件を検討する。
- DB・Volume保全、復旧失敗時の停止・証跡保持、外部HTTPS/health/login復旧確認を含める。
  image rollbackでDB schemaが戻ったと判断しない。実履歴に一致するreviewを必要とする。
- 自動復帰を導入する場合もDB復元・破壊的変更を伴わない範囲に限定し、
  隔離環境で十分な失敗経路テストと別途承認を要する。今回導入しない。
