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
| IAM追加hardening | EC2 ECR読取は8repo/東京の最小案。既存policy包括改訂なし | 既存SSM/S3/KMS等が漏えい時に悪用される | 実経路/使用権限を調査しrole分離/条件を評価 | 高 | 1–2日 |
| OCI署名/provenance | 固定digest・CI証跡を検証するが署名なし | ECR writer/main管理者侵害時に正当性を独立証明できない | 署名・発行者verification/鍵管理方式を選択 | 中 | 1–3日 |
| 高度manifest検証 | 既存schema/7件/source/rawdigest/config/ID照合を再利用 | duplicate JSON keyや高度なparser差、オンラインCI再照合なし | 共通validator拡張と実fixture互換性確認 | 中 | 4–8h |
| tar厳密安全性 | path/重複entry/link/特殊fileの基本拒否のみ | parser差、PAX/サイズ/展開資源上限の高度な攻撃は未網羅 | 構造parser・展開上限/攻撃fixture。review済みwriter信頼も再評価 | 中 | 1–2日 |
| ECR lifecycle | IMMUTABLE、policyなし | 無制限保持で容量/料金増加 | current/rollbackの保護基準を確定して別承認で導入 | 中 | 4–8h |
| 長期保持/削除 | current/rollback削除禁止。自動GCなし | 容量枯渇、必要release削除の運用ミス | image/OCI/source一体の保持inventoryと削除dry-run | 中 | 1–2日 |
| 追加自動化 | manual dispatch＋人間の取得/deploy、Actions→EC2なし | 人間の選択・操作ミス | 受入後に限定preflight補助のみ検討。自動deployは別承認 | 低 | 1–3日 |
| セキュリティ回帰拡充 | producer/adapter mockと既存Shell/Java回帰 | 本番firewall/socket/IMDS境界の証明ではない | 隔離ホストで実ネットワーク/権限境界test | 高 | 1–3日 |
| Python sandbox runtime | 7 PPE imageとは別のpython base。過去にID固定で受入、現在EC2状態未検証 | 未配置/異IDだと演習実行不能。可変upstream tag | 本番preflightで既存指定ID確認。将来は独立digest管理/保持 | 高（受入前確認） | 確認1–2h、管理4–8h |
| 新operations配布 | repository safety gateは更新済み。EC2 installed revisionとの一致は未確認 | 古いscriptはbackup/shared lockを満たさない | 既存runbookで独立immutable operationsを配布・実測 | 高（deploy前必須） | 2–4h＋承認待ち |
| DB復旧訓練 | receipt/Version ID/暗号化/互換性gateを維持 | image rollbackでDDLは戻らない。鍵custody/restore実効性 | 隔離DB restoreと業務データ再読込を定期実施 | 高 | 半日–1日 |
| 取得中の容量/排他 | adapter lock/private staging、失敗時cache/tag保持 | local build/他操作との競合、disk枯渇 | 初回は人間が容量/並列不在確認。将来共通取得lock/容量閾値 | 中 | 2–4h |

現時点のsource reviewではstudent childからhost資格情報への確定したexploitは見つからなかった。
これは本番hostの安全証明ではない。実firewall、override、mount、環境変数、パッチ状態は未検証。
ECR権限追加は読取りだけでも、既存SSM/backup権限を含むroleの漏えい影響を消さない。
