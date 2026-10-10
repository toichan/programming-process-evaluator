# ECRイメージによる半手動デプロイ

## 範囲と現在位置

2026-10-10 JSTの方針変更により、大規模なvalidator/receipt基盤や包括的hardeningは
[残課題](./ecr-deployment-debt.md)へ移し、既存releaseへ接続する小さなadapterを使用する。
ActionsからのEC2操作・自動deployは追加しない。

ECR公開の実受入はsource SHA `2f48c898f7cb9ebdf00809f1b83488a6f1b9811f`、
[run 38045471493](https://github.com/toichan/programming-process-evaluator/actions/runs/38045471493)
で成功した。OCI release digestは
`sha256:c74336a48d003c4ebc1a363c0c9219931718b14ceda0d9684226c3266958e78e`。
7 imageのregistry manifest/config/Pull後ID、OCI資産の再取得を確認済み。
**EC2 Pull権限の適用、EC2での取得、本番更新・ヘルスチェックは未承認・未実施**。
この文書を本番受入済みの記録として扱わない。

## Phase A/B: 人間が選択したreleaseの取得と検証

[prepare-ecr-release.sh](../../scripts/production/prepare-ecr-release.sh)は、
SHA・OCI digest・専用release rootの3引数を要求する。ホストのinstance roleで認証し、
privateな一時Docker/curl設定のみを使用する。長期キー・profile・container credentialsは拒否する。
アカウント/roleとnative Linux amd64/local Unix Docker daemonを確認する。

1. `ppe/releases@<digest>`をRegistry APIで取得し、raw bytesのSHA256を照合する。
2. OCI configと単一tar layerをdigest/sizeで検証する。基本的なpath traversal、
   重複entry、リンク・特殊ファイルを拒否して専用一時領域へ展開する。
3. 既存schema validatorを使用する。CI証跡は正しいrepo/push/main/成功2check。
   発行run/attempt/workflow SHAは選択digest内の値を明示的にvalidatorへ渡す。
   **これは署名による発行者確認やGitHub CIのオンライン再検証ではない**。
4. source archive/hash manifestを照合し、展開後の全source hashを再生成して比較する。
   選択sourceからscriptを実行せず、レビュー済みoperations側のhelperを使用する。
5. 7 imageのraw manifest digest・config digest/size/revision/platformを検証し、
   `repository@sha256:...`でPullする。Pull後Image IDをmanifestと照合する。
   classic storeでは`.Id`がconfig digest。Docker 29のcontainerd storeでは`.Id`が
   manifest digestのため、Descriptor/RepoDigestsを固定digestと照合し、
   `ctr --address /run/containerd/containerd.sock --namespace moby content get <config digest>`
   のlocal config bytesのhashと元ECR config bytesを照合する。
   標準host socket/namespaceと既存ctrを要求し、daemon設定変更やID照合の省略は行わない。
6. 全7件成功後、`ppe-<name>:<SHA>`の既存tagを全件確認する。
   同じSHAで異なるIDなら拒否する。同一IDのtagは変更しない。
7. 不足するlocal tagを作成し、IDを再照合する。既存release directoryは必ず拒否する。
   新規directoryを排他的に作成し、`source/`、`source.sha256`、`commit`、
   7個の`ppe-*.id`と参考用`ecr-release.json`/`ecr-release.digest`を配置する。
   `ppe-*.id`は既存deploy/rollbackが比較する**daemon local ID**。
   `ecr-image-identities.tsv`にregistry digest/config Image ID/daemon IDを別列で記録し、
   公開manifestのconfig Image IDをmanifest digestへ書き換えない。
   **READYは最後に作成**する。複雑なreceipt形式は追加しない。

取得によりDocker cache/layersと新規local tag/releaseが増えるが、running container、
DB/Volume、deployment.state、現在のCompose参照は変更しない。
`operation-common.sh`をsourceせず、Compose up/down、restart、migration、deployを呼ばない。
adapterとlocal build/他deployは同時実行しない。専用lockはadapter間の競合防止であり、
既存deployのDB/TLS lockを代替しない。

### 承認後にEC2ホストで実行するコマンド

以下は**実行予定例であり、まだ実行していない**。操作ユーザー、root、operations versionを
事前に確認する。既存rootの権限を無断変更しない。専用0700 directoryがなければ作成を
承認した作業範囲に含める。

```bash
# レビュー済み新operationsのscript。old release内のscriptは使わない。
OPS=/var/lib/ppe/operations/<REVIEWED_OPERATIONS_SHA>
SHA=2f48c898f7cb9ebdf00809f1b83488a6f1b9811f
DIGEST=sha256:c74336a48d003c4ebc1a363c0c9219931718b14ceda0d9684226c3266958e78e
RELEASE_ROOT=/var/lib/ppe/releases
HOME=/root PPE_ECR_ACQUISITION_APPROVAL=yes \
  bash "$OPS/scripts/production/prepare-ecr-release.sh" "$SHA" "$DIGEST" "$RELEASE_ROOT"
```

同SHAの既存releaseがある場合は上書きせず停止する。実内容を確認し、
検証専用の別rootを使うか別途公開した新しいSHAを選ぶ。新しいimageを不要に再公開しない。
現在/rollback対象のtagを消して再試行してはいけない。

## EC2への最小IAM追加案（適用前に別承認）

既存`ProgrammingProcessEvaluatorEC2Role`へ以下のinline policyだけを追加する。
既存SSM/backup等のpolicyを編集せず、AWS managed ECR policyは使用しない。

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "TokyoECRAuthentication",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*",
      "Condition": {"StringEquals": {"aws:RequestedRegion": "ap-northeast-1"}}
    },
    {
      "Sid": "ReadSelectedPPERepositories",
      "Effect": "Allow",
      "Action": ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"],
      "Resource": [
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/app",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/tools",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/db",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/runner",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/broker",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/nginx",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/backup",
        "arn:aws:ecr:ap-northeast-1:024378233912:repository/ppe/releases"
      ],
      "Condition": {"StringEquals": {"aws:RequestedRegion": "ap-northeast-1"}}
    }
  ]
}
```

Registry GET/digest Docker Pullに必要な認証・manifest・blob読み取りだけ。
BatchCheckLayerAvailabilityは通常のPullでは不要。DescribeImages/ListImages、Push、
repository設定/削除、image削除、upstream import、新たなSSM/S3/KMS/IAM操作は付与しない。
STS本人確認は追加Allowを必要としない。ローカルでIAMを操作する際は必ず
`--profile ppe-infra-admin --region ap-northeast-1`を指定する。
EC2内では別途承認後にinstance roleを使うためprofileを指定しない。

## Phase C: 別承認後の本番デプロイ

取得成功はdeploy許可ではない。既存[本番runbook](./production-deployment.md)を正とし、
次を実測してから既存`deploy-release.sh update`を人間が明示実行する。

- instance/port/DB/Volume、current/previous release、journalと新operations revision。
  old `de2b0d37...` operationsは次回updateに使用しない。
- source manifest pin、全7tag/ID、既存DBを所有するrelease/recipe/container ID/
  Volume CreatedAt。新releaseのdb imageが異なっても既存DBを置換しない。
- S3 backup・restore custody reference・固定Version ID receipt、
  TLS wrapper/environment/shared lockの既存gate。未適用なら別承認のoperations配布が必要。
  gateを緩和してPhase 2を完了させない。
- Flyway validate/info・履歴・pending inventory。DB schema変更は適用内容をレビューする。
- 生徒Python用の別runtime imageが既存brokerの指定IDで利用できること。
  7イメージのrunnerは実行制御サービスであり、Python sandbox base imageを含まない。
- 授業外のmaintenance、旧アプリと更新後DBの互換性確認、rollback対象7image保持。

既存runbookの承認済みprivate環境・pinを設定した上での呼出し形式:

```bash
# 取得と別の承認後のみ。必要なPPE_*値は既存runbookに従い、推測しない。
PPE_OPERATION_APPROVAL=production-approved PPE_MAINTENANCE_APPROVED=yes \
  bash "$OPS/scripts/production/deploy-release.sh" update "$RELEASE_ROOT/$SHA"
```

新release sourceに古いoperationsが含まれていても、そのscriptをdeployに使わない。
health確認は既存deploy内の5service健康/ID照合とsmokeに加えて外部HTTPSを実施する。
成功後はstate/current releaseと実image、DB container/Volumeが一致・維持されたことを確認する。
認証後の代表操作を必要に応じて確認する。未確認項目を合格扱いしない。

## 失敗とロールバック

| 失敗 | 対応 |
|---|---|
| 認証・network・digest/schema/ID不一致・不足 | 取得は失敗。deployせず、原因を調査する。IAM拡大や不正manifestの許容で回避しない |
| Pull途中 | 成功分のimage/cacheは保持。READYなし、サービス/DBは未変更。容量と原因を確認して同digestを限定再試行 |
| tag作成途中 | できたtagは残す。全件verified releaseではない。異IDtagは上書きせず、同IDtagのみ再利用可能 |
| directory配置中の停止 | 新規directoryにREADYがなければ未完成。既存releaseと区別し、手動レビュー後のみその未完成directoryを退避する。自動削除しない |
| stale `.ecr-acquire.lock` | 生きた処理がないことを確認して手動解除。並列実行や無条件lock削除はしない |
| backup/preflight失敗 | 既存deployの停止前gateで拒否。必要な配布/バックアップ条件を整え、別承認境界を守る |
| migration開始後失敗 | deployment.state/DBを保持。自動repair/clean/再migration/DB restoreは禁止。既存復旧runbookで判断 |
| deploy後health失敗 | 更新後DBとの互換性を確認し、既存rollbackを明示実行。image rollbackとDB復元を混同しない |
| ECR障害 | 検証済みlocal image/旧releaseがあれば既存rollbackを使える。なければ待機。local buildは別SHA/rootで既存方式を維持し、衝突tagを上書きしない |

rollbackは既存`rollback-release.sh OLD_RELEASE`を使用し、
`PPE_SCHEMA_POLICY=backward-compatible`と**現在のDB**に対するreview済みcompatibility reference、
source manifest pin・backup/TLS gateを満たす。DB downgrade/自動復元は導入しない。
過去OCI digestからの再取得も同adapterを使えるが、存在する旧releaseは変更しない。

## 検証と完了条件

### 2障害の最小修正と再deploy前提（2026-10-10）

- runner Dockerfileはコピー後のdirectory0755/file0644を明示する。
  CI checkout/転送/umask由来で入力runner.pyが0600でも、production UID65532が読める。
  Composeの非root/read-only/cap drop/token条件は変更しない。
- deploy/rollbackは最初のCompose config前に、既存TLS operation envから
  PPE_BIND_ADDRESS/PPE_HTTP_PORT/PPE_HTTPS_PORTを固定allowlist parserで読込む。
  呼出側/backup envに指定がなければ既存TLS値を継承する。指定済みで不一致なら拒否。
  TLS側の欠落/重複/不正値も拒否し、localhostへの黙示fallbackは使わない。
  envをsource/evalせず、ファイルや他の設定を書換えない。
- 本番の既存TLS設定0.0.0.0:80/443を再利用する。localhostを意図した隔離環境も、
  TLS envに明示された値を使う。公開設定をコードへhardcodeしない。
- regression: 0600入力から実Docker build→UID65532/read-only起動/health/401拒否/再起動、
  実nginx再作成deploy/rollback相当→公開bind保持/両ホストTLS疎通、
  app_started/no-migration失敗→現在schema reviewで既存rollback→旧サービス・履歴維持。
- この修正は既存immutable image/releaseを修正しない。旧2f48...を再deployしない。
  次回は修正を含むmain固定SHAの必須CI成功→新しい一意releaseをECR公開・再取得し、
  新operations・source/registry/config/daemon identity・pending0・fresh backup・
  更新後schemaのrollback referenceを再確認する。公開/EC2配置/再deployは別途承認。
- 組込み自動復帰は従来どおりmigration前の早期段階限定。app_started時の自動復帰を
  今回拡大せず、現在DB互換性を確認して明示承認のrollbackを使う。

### 本番update実行結果（2026-10-10 21:12 JST、失敗・停止）

**Phase 2は未完了。今回の一回限定updateはexit1。別承認の緊急rollbackで21:18:43 JSTに旧releaseの公開サービス復旧を確認した。**
詳細は[失敗・復旧状態の記録](./error-report.md)を参照。
承認済みoperations86936306.../target2f48...を一度のみ実行した。
preflight/Flyway validate/info・pending0・fresh S3 backup/receipt照合は成功。
SQL migrationは実行せず、DB/container/Volume/historyは維持した。
新runnerの`/runner/runner.py`がroot-owned0600であり、production User65532が読めず、
runner unhealthy→新app未起動→app_startedで停止。旧nginxはhealthyでもサイトは502/504。
current release/backup/TLS envは旧release、runtimeは混在しており公開完了ではない。
組込み旧app復帰はこの段階で未実行。再deploy/明示rollback/DB復元は行っていない。
fresh backupとstate・immutable image/旧releaseを保持。
当初の停止状態は以下の緊急復旧で解消した。新releaseの修正・再公開・再deployは未着手。

### 緊急復旧受入（2026-10-10 21:18 JST）

- 21:14:07 JSTのユーザー承認に基づき旧release0e2bfae5...へ復帰。
  既存`rollback-release.sh`のincomplete update経路を使用。
  現在marker旧→target旧の実DB履歴照合referenceを別root0600ファイルに記録し、
  既存新→旧referenceを改変せず使用条件の違いを維持した。
- SSM `ba561ee9-c433-4808-95bb-f929a9decb7f`:
  rollback本体exit0、21:15:16–21:16:36 JST（80秒）。
  5サービスの旧Image ID/healthy、非root runner/app、DB/container/start/Volume/history不変、
  Secrets/immutable release hash不変、state rollback/published/current旧を確認。
  失敗update journalは既存rollbackの退避証跡で保持。
- wrapperの公開HTTP確認はexit7。原因はoperation.envにbind指定がなく、
  nginxを既定127.0.0.1で再作成したこと。local TLS smokeは200でも公開接続は拒否。
  停止前のnginx27b58...は0.0.0.0:80/443、既存tls-operation.envも同設定。
- SSM `c089ea5c-cae3-4c21-9bfa-bb3b7b37596b`:
  既存TLS envからbind/portだけをprocessへ設定し、共通DB/TLS/deployment locksを保持して
  `dc up -d --no-deps --wait --wait-timeout 60 nginx`のみ実行、exit0。
  既存の公開bindへ戻したもので、新規の公開範囲拡大やSG変更ではない。
  operation.env/tls-operation.env/state/Secrets/全release hash不変。
- 21:18:43 JSTホストから公開health/login全4件200、health本文status ok。
  21:18:53 JST外部Macでも全4件200。TLS検証を無効化せず、
  生徒/教師ログインフォームのブラウザー描画も確認。
- 停止処理開始21:08:49から公開復旧確認21:18:43まで9分54秒。
  連続monitorはないため厳密な障害開始/終了ではなくmaintenance開始→復旧確認時間。
- 新release反映は取り消され、旧release5サービスへ復帰。DB削除/復元/migration、
  IAM/EC2/Secrets変更なし、旧/new releaseとbackupを保持。
  復旧をもって停止。Phase 2成功とは扱わず、新release修正・再deployは次指示待ち。

### 本番EC2取得受入（2026-10-10 JST）

- PR #53と最小ECR read IAM適用済み。Docker29/containerd ID互換修正は
  [PR #54](https://github.com/toichan/programming-process-evaluator/pull/54)で通常merge。
  operations SHA `86936306ecaf587a84544f45e6bb57d3a49304f4`、
  [main CI](https://github.com/toichan/programming-process-evaluator/actions/runs/38049532721)成功。
- 対象EC2 `i-0ffd69e8f390bd396`へSSMで接続。新operationsは独立directoryに配置し、
  active operations/TLS/backup pointerを変更していない。
- source `2f48c898f7cb9ebdf00809f1b83488a6f1b9811f`、
  OCI `sha256:c74336a48d003c4ebc1a363c0c9219931718b14ceda0d9684226c3266958e78e`を取得。
  7/7固定digest Pull・config bytes hash/byte一致・platform/revisionを検証。
  source manifest hash `9989fc3ca412f304359e900bc6ec2442b7debc687a34092c7d0ee27e78ba5095`。
  新release READYと既存`verify_release`実関数の読込成功。
- 既存DBの実`verify_existing_database`成功。container/image/config hash/Volume
  CreatedAtは既存値を維持可能。旧/新migration filesはbyte同一。
  DB 64 tables、V1–V23成功、failed0。Flyway validate/infoの実container実行は
  今回行わず、デプロイ承認後の既存gateへ残す。
- 既存7rollback imageは旧release IDと一致。backup receiptの固定S3 Versionと
  checksum/freshness検証成功。TLS wrapperは新operationsとbyte同一、
  5service healthy、両HTTPS 302、証明書期限2027-01-06 UTC。
- 取得のSSM command `8b83b9f4-a448-44d8-8f23-d27927258862` success。
  READY/DB互換性確認 `65ca2918-47a6-4f3a-a8fa-d12f6e5e2f50` success。
  private host evidenceは`/var/lib/ppe/operations-evidence/ecr-acquisition-8b0e8fe-20261010`。
- **本番デプロイ未実行・未承認**。取得受入時点ではrecovery/rollback referenceが
  未確認だった。その後の最小確認と人間の個別承認は次節に記録する。
  receipt検証成功を復号/DB復元試験成功と読み替えない。
- 想定停止はupdate開始後のapp/runner停止～backup/検証/health間。
  仮に5–15分のmaintenanceを確保するが未実測であり保証値ではない。
  pending SQLは静的差分上なし。DB自動rollbackは禁止。
- 最終比較 command `bc163eb6-e4dc-4f16-901a-d43d515b19ac` success:
  既存container IDs/images/StartedAt/restart count、Volume metadata、
  state/Secrets/TLS/既存release/environment hashes、DB schema履歴が取得前と一致。
  稼働5service healthy、両HTTPS 302。disk 19G/used10G/free8.4G（55%）。
  DB全行checksumは取得しておらず、通常アプリからの書込みを含む全データ不変の証明ではない。
  作業コマンドからのDB操作はread-only SQLのみ。
  本受入は20:34–20:50 JST、約16分（CI待機と互換修正を含む）。

### 最終reference確認と承認境界（2026-10-10 JST）

- 既存[Stage C実復元記録](./production-deployment.md#stage-c-isolated-recovery-accepted-2026-10-09)
  とprivate local記録を確認。固定S3 data Version
  `5hC9psnT52akVYEJ5U8JsSkeb5kkWI5R`、ciphertext SHA256
  `40eaf6631dc12565aea674137a6ac52f10c9b75578cbeb0744418d4c14334f3d`。
  実隔離MySQLに64テーブル復元、CHECK TABLE64/64、FK126件孤児0、
  V1–V23成功が記録されている。大部分の業務テーブルは空だった。
- 過去runnerが参照した既存Mac age identityの所有者/0600権限を確認。
  同一ciphertextを`age --decrypt ... > /dev/null`で再認証しexit0。
  導出した公開recipientは過去receiptと現在EC2 recipientに一致。
  秘密鍵を表示/転送せず、平文保存・新DB restoreは行っていない。
  AWS KMS key Enabled、S3 Versioning Enabled/SSE-KMSを読み取り確認したが、
  **KMSだけでは別レイヤーのage暗号文を復号できない**。
- 人間は「既存Mac鍵＋実復元証跡」を今回のrecovery referenceの根拠として承認。
  Mac独立のoffline予備コピーは未確認で、その不備を解決済みとはしない。
  新規root-owned0600 regular file:
  `/var/lib/ppe/config/recovery-reference-20261010`、
  SHA256 `505b05e736901aca40a97367d1ff7ba4e5384b78b1657517e5395b8a5a942bcf`。
  `restore_verified=yes`は過去の実初期状態restoreを参照する。
  `independent_offline_duplicate_verified=no`、`populated_business_data_restore_verified=no`
  も明記。鍵そのものはEC2に配置していない。
- 旧/新migration bytes同一。追加処理は提出物/評価/アンケートの参照・export、
  保存しないPython preview、既存audit_logsへのexport監査記録が中心。
  schema変更・既存データ変換なし。人間がこの差分と現在履歴に限定した互換性を承認。
  新規root-owned0600 file:
  `/var/lib/ppe/config/rollback-2f48c898-to-0e2bfae5-20261010.reference`、
  SHA256 `378f4d64545ee0462662cff16b7a79872f46063c48adadfe6ae59f1b002d81b3`。
  source=新2f48...、target=旧0e2b...、DB ID=既存cead...、
  現在ordered version/script/checksum/success hash=
  `d37fe2524686316e388419148694b57b50fc36927ffe89ec1c5e9620e4729475`。
  デプロイ後の履歴が変わればreferenceは無効で再レビューする。DB downgrade許可ではない。
- 既存referenceがあると偽らず、上記2件を個別承認後に新規作成した。
  SSM command `2f5abda4-12f8-4f28-9584-1bc5d6b62069` success。
  env/release/state/Volumeを書き換えず、実行時の変数で指定する。
- 旧READY/7image IDと新READY/source/7image IDを再確認。
  旧sourceの既存detached manifest
  `/var/lib/ppe/operations-evidence/phase5-step-a-7087770d-20261010/old-release-source.sha256`
  の独立local hashは
  `d3c55dd1114e974d390f8e10637d13d20a043611f608b859f38dd227d8ba04d3`。
  同じ内容のobserved manifestを既存verify_source_manifestで検証成功。
  旧immutable release内へmanifestを追加していない。

#### 次の本番承認で許可を求める範囲

単一EC2/上記sourceとoperationsに限る一回の`update`、授業外maintenance、
既存app/runner停止、fresh S3 backup、Flyway validate/infoとpending0時のmigration skip、
app系更新、health/HTTPSとDB/Volume維持確認、既存backup/TLS参照の同期。
pending SQL・履歴/ID/reference drift・backup失敗があれば独断で続行しない。
変更後の代表的な認証UI/合成Python preview確認も含める。
Gemini実API呼出し、IAM/EC2設定変更、DB復元/削除、Volume削除は含めない。
rollbackは別途明示承認と現在履歴照合を要求する。

実行予定コマンド（**未実行。本番承認後のみ**、root Bash/SSM）:

```bash
set -Eeuo pipefail
set +x
export HOME=/root DOCKER_HOST=unix:///var/run/docker.sock
# Existing reviewed key=value config: export data, never eval/source it.
while IFS= read -r line; do
  [[ "$line" =~ ^[A-Z][A-Z0-9_]*= ]] || continue
  export "$line"
done < /var/lib/ppe/operation.env
export PPE_BACKUP_OPERATION_ENV=/var/lib/ppe/operation.env
export PPE_TLS_OPERATION_ENV=/var/lib/ppe/tls-operation.env
export PPE_TLS_OPERATION_WRAPPER=/var/lib/ppe/tls-operations/current/scripts/production/renew-tls.sh
export PPE_DB_RELEASE=0e2bfae5e23bc512f0b7cf844264d4d45a484ecc
export PPE_RECOVERY_DB_CONTAINER_ID=cead40118ed8a27ce90a8f745f7b69ed528355149c7412d2c95985b6e47872d8
export PPE_RECOVERY_DB_VOLUME_CREATED_AT=2026-10-08T08:32:49Z
export PPE_SOURCE_MANIFEST_SHA256=9989fc3ca412f304359e900bc6ec2442b7debc687a34092c7d0ee27e78ba5095
export PPE_PREVIOUS_SOURCE_MANIFEST_FILE=/var/lib/ppe/operations-evidence/phase5-step-a-7087770d-20261010/old-release-source.sha256
export PPE_PREVIOUS_SOURCE_MANIFEST_SHA256=d3c55dd1114e974d390f8e10637d13d20a043611f608b859f38dd227d8ba04d3
export PPE_BACKUP_RECOVERY_REFERENCE=/var/lib/ppe/config/recovery-reference-20261010
export PPE_ROLLBACK_COMPATIBILITY_REFERENCE=/var/lib/ppe/config/rollback-2f48c898-to-0e2bfae5-20261010.reference
printf '%s\n' \
  '505b05e736901aca40a97367d1ff7ba4e5384b78b1657517e5395b8a5a942bcf  /var/lib/ppe/config/recovery-reference-20261010' \
  '378f4d64545ee0462662cff16b7a79872f46063c48adadfe6ae59f1b002d81b3  /var/lib/ppe/config/rollback-2f48c898-to-0e2bfae5-20261010.reference' |
  sha256sum --check --status
export PPE_SCHEMA_POLICY=backward-compatible
export PPE_OPERATION_APPROVAL=production-approved PPE_MAINTENANCE_APPROVED=yes
bash /var/lib/ppe/operations/86936306ecaf587a84544f45e6bb57d3a49304f4/scripts/production/deploy-release.sh \
  update /var/lib/ppe/releases/2f48c898f7cb9ebdf00809f1b83488a6f1b9811f
```

想定maintenanceは5–15分の仮枠で未実測。health失敗時も成功扱いせずstateを保持。
pre-migration失敗では既存scriptの旧app復帰処理、migration開始後は自動復元なし。
DB/schemaが互換なら明示承認後の既存app rollbackを選ぶ。
切替後の確認は5service健康/ID、外部HTTPS、current/state/env一致、DB container/Volume、
Flyway履歴、認証・変更画面・合成preview。OS/全ブラウザー/実Gemini受入は完了扱いしない。

既存`bash scripts/ci/tests/ecr-release-test.sh`へ取得fixtureを追加する。
producerのOCI資産から既存READY/7ID/source形式までの正常フロー、固定digest不一致、
Pull後ID不一致、不足、tag衝突、途中Pull失敗、既存release再実行拒否、リンク拒否、
認証失敗、承認なし拒否をmockで確認する。同一IDtagの再利用と既存`verify_release`の
実関数による読込も確認する。既存Java/Shell CIとdeploy/rollback回帰は維持する。
mockはAWS疎通・稼働中サービスhealth・DB保存の実受入を代替しない。

Phase 2完了は、必須CI成功・通常PR merge・承認したIAM適用・
承認したEC2取得/本番deploy・health/DB維持/rollback準備確認がそろった時点。
完了後は包括的基盤整備を続けず、卒業研究システムの機能開発へ戻る。

### ローカル検証記録（2026-10-10 JST）

| コマンド | 結果 |
|---|---|
| `bash scripts/ci/check-shell-syntax.sh` | exit0、72 files（Bash/dash） |
| `bash scripts/ci/tests/ecr-release-test.sh` | exit0、producer40負例、adapter正常/同一IDtag再利用/既存verify_release読込＋9負例、skip0（mock） |
| `bash scripts/ci/run-shell-tests.sh /Users/t.toida/.copilot/session-state/d6d17c93-fe8b-4b00-b4d5-ce6bfb916c78/files/ecr-adapter-shell-results-20261010` | exit0、既存18 suites/144 assertion groups、fail0/skip0。DB維持/復旧/rollback関連回帰を含む |
| `gradle --no-daemon clean build --warning-mode all`（host） | exit1、Java21 toolchainなし。[エラー履歴](./error-report.md) |
| 下記CI-pinned containerの同build | exit0、WAR/build成功 |
| `bash scripts/ci/summarize-junit.sh build/test-results/test` | exit0、107 suites/392 tests/260 passed/0 failures/0 errors/132 skipped |
| `/Users/t.toida/go/bin/actionlint .github/workflows/ecr-release-publish.yml .github/workflows/ci.yml .github/workflows/production-image-build.yml` | exit0 |
| `git diff --check` | exit0 |

Java再検証の実コマンド（ローカル、合成設定のみ）:

```bash
docker run --rm --pull never \
  --mount type=bind,source=/Users/t.toida/programming-process-evaluator,target=/work \
  --workdir /work --user 501:20 --env GRADLE_USER_HOME=/tmp/gradle-home \
  --env DB_HOST=127.0.0.1 --env DB_PORT=1 --env DB_NAME=ppe_ci_no_database \
  --env DB_USER=synthetic_ci --env DB_PASSWORD=synthetic-ci-not-a-real-secret \
  --env PPE_ENV=simulation --env AWS_EC2_METADATA_DISABLED=true \
  gradle:8.10.2-jdk21 gradle --no-daemon clean build --warning-mode all
```

さらに初回実公開済みrelease.jsonを新しい明示context引数で既存validatorへ渡し、
schema互換性をexit0で確認した。これはEC2での実Pull検証ではない。

### 残る工程（見積もり・承認待ち時間を除く）

| 工程 | 内容 | 目安・停止条件 |
|---|---|---|
| 1 | 小さなadapter・既存回帰・runbook/負債文書・PR/CI/通常merge | 本変更。CI成功後のみmerge |
| 2 | 上記最小IAM案の確認・適用・再取得/allow/deny検証 | 30–60分。人間のIAM承認前停止 |
| 3 | 対象host/新operations/DB/backup/TLS/runtime確認とECR取得受入 | 1–2時間。EC2操作承認前停止。未適用operationsがあれば範囲を提示 |
| 4 | 既存deploy実行・health/DB維持/rollback準備確認 | 30–90分。maintenance/deploy承認前停止。不具合修正や移行は別見積もり |

見積もりの短縮を理由に未完了gateを飛ばさない。本番サービス停止・更新とDB migrationは
その具体的な対象・変更・復旧方法を提示してから承認を得る。
