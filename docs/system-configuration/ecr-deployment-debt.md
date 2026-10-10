# 半手動デプロイ後の技術的負債

2026-10-10 JSTの方針変更で次を今回の必須実装から外した。
**解決済みではない**。Phase 2は小さなadapterと既存安全gateで完成させる。
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
| 追加自動化 | manual dispatch＋人間の取得/deploy、Actions→EC2なし | 人間の選択・操作ミス | 受入後に限定preflight補助のみ検討。自動deployは別承認 | 低 | 1–3日 |
| セキュリティ回帰拡充 | producer/adapter mockと既存Shell/Java回帰 | 本番firewall/socket/IMDS境界の証明ではない | 隔離ホストで実ネットワーク/権限境界test | 高 | 1–3日 |
| Python sandbox runtime | 2026-10-10 EC2のpython:3.12-alpine/linux amd64実IDとbroker指定を確認。7 PPE imageとは別 | 可変upstream tag。更新後の実演習フローは未検証 | デプロイ後の演習受入。将来は独立digest管理/保持 | 高（受入前確認） | 確認1–2h、管理4–8h |
| 新operations配布 | 86936306...を独立配置しECR取得受入済み。TLS wrapper byte同一、active pointer未変更 | デプロイ時に旧operationsを誤選択するリスク | 新operationsを明示指定し既存gateを維持 | 高（deploy前必須） | 確認30分＋承認待ち |
| DB復旧訓練 | receipt/Version ID/暗号化/互換性gateを維持 | image rollbackでDDLは戻らない。鍵custody/restore実効性 | 隔離DB restoreと業務データ再読込を定期実施 | 高 | 半日–1日 |
| 取得中の容量/排他 | adapter lock/private staging、失敗時cache/tag保持 | local build/他操作との競合、disk枯渇 | 初回は人間が容量/並列不在確認。将来共通取得lock/容量閾値 | 中 | 2–4h |
| Docker store portability | Docker29/containerdは固定Descriptor＋local config bytes照合で実受入済み。標準host socket/moby/ctrを要求 | 非標準socket/namespace・将来daemon変更では取得拒否。圧縮/展開双方の保存で容量増 | daemon変更時に互換テスト。必要になった場合のみsocket対応を追加し、既存store切替/restartで回避しない | 中 | 2–4h |
| 復号鍵の独立保管 | 既存Mac鍵で固定バックアップの再復号認証成功。人間が今回の復旧reference根拠として承認。独立offlineコピーは未確認 | Mac紛失/故障と鍵喪失が重なるとage復号不能。S3/KMSだけでは復旧できない | Phase 2後、所有者が独立した安全な鍵保管と利用可能性を確認。秘密鍵をEC2/Gitに置かない | 高 | 1–2h＋所有者確認 |

現時点のsource reviewではstudent childからhost資格情報への確定したexploitは見つからなかった。
これは本番hostの安全証明ではない。実firewall、override、mount、環境変数、パッチ状態は未検証。
ECR権限追加は読取りだけでも、既存SSM/backup権限を含むroleの漏えい影響を消さない。
