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
6. 全7件成功後、`ppe-<name>:<SHA>`の既存tagを全件確認する。
   同じSHAで異なるIDなら拒否する。同一IDのtagは変更しない。
7. 不足するlocal tagを作成し、IDを再照合する。既存release directoryは必ず拒否する。
   新規directoryを排他的に作成し、`source/`、`source.sha256`、`commit`、
   7個の`ppe-*.id`と参考用`ecr-release.json`/`ecr-release.digest`を配置する。
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
PPE_ECR_ACQUISITION_APPROVAL=yes \
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
