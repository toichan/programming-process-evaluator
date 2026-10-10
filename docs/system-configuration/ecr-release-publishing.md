# ECR Private release保存（手動起動・本番反映なし）

## 状態と境界（2026-10-10 JST）

初回実ECR Push・再取得照合は[run 38045471493](https://github.com/toichan/programming-process-evaluator/actions/runs/38045471493)
で成功。source SHA `2f48c898f7cb9ebdf00809f1b83488a6f1b9811f`、
main CI `38045260014`成功後に人間が手動起動した。
7 imageとOCI release digest
`sha256:c74336a48d003c4ebc1a363c0c9219931718b14ceda0d9684226c3266958e78e`
を保存・再照合済み。EC2操作・deploy/migration・IAM変更はこの公開受入では行っていない。
EC2への接続は[半手動runbook](./ecr-manual-deployment.md)へ分離し、本番受入と混同しない。
[半自動方針](./semi-automatic-deployment-policy.md)のStep 3に対応する。
既存[認証なしbuild検証](./production-image-validation.md)のworkflowは変更しない。

保存先はTokyo、account `024378233912` のECR Private。repositoryは
`ppe/app`, `ppe/tools`, `ppe/db`, `ppe/runner`, `ppe/broker`, `ppe/nginx`,
`ppe/backup`, `ppe/releases` の8個だけ。
既存 `arn:aws:iam::024378233912:role/PPEGitHubECRPublisherRole` を使用し、
OIDCのAccount/session ARNを照合する。新しいIAM権限や長期キーは追加しない。
GetAuthorizationTokenだけは全resource、その他は既存8repository限定の
BatchCheckLayerAvailability / InitiateLayerUpload / UploadLayerPart / CompleteLayerUpload /
PutImage / BatchGetImage / GetDownloadUrlForLayer / DescribeImagesを前提とする。
GetCallerIdentityはSTSの本人確認で、EC2/SSM/Secrets/IAM操作はない。

## 起動・検証の順序

別承認のmain登録とCI成功確認後、[workflow](../../.github/workflows/ecr-release-publish.yml)を
**mainからworkflow_dispatch**し、40桁小文字の完全SHAを指定する。自動起動はない。
main以外は両jobをskipしAWS資格情報を取得しない。

1. Ubuntu 24.04 x86_64、native Linux/x86_64 Docker daemon、local Unix endpointを必須化。
2. 完全SHAのcommit実在とorigin/main ancestryを確認する。
3. GitHub APIから`ci.yml`のworkflow IDを取得し、同じSHAのpush/main/
   completed/success runを確認する。両check名を完全一致で要求する:
   `Java build and tests` / `Shell syntax and deployment regressions`。
   同run/attempt/SHAの最新jobが各1個、両方completed/successであることも照合する。
   他workflow、PR run、片方のみ成功、同名重複、取得失敗は拒否する。
4. **AWS認証なしのbuild job**で既存build/commonを再利用。git archiveのsourceから
   7 imageのrevision/platform/ID/WAR/migration/JUnitを既存と同じ条件で検証する。
   `--export-release`だけがDocker saveとレポートを書き出す。成功/失敗とも
   元のランダムlocal tagをcleanupし、既存の3引数実行はexportしない。
5. v4 artifactの同run出力IDで別のfresh publish runnerへ転送（保持1日、圧縮なし）。
   APIのartifact ID/name/digest/expiry/run/SHAに加え、GitHub CLIで取得した**ZIP全bytesの
   SHA256をupload job出力digestと完全一致**させてから展開する。ZIPの閉じた22ファイル一覧と
   重複/不正entryを拒否し、展開後は全20payloadファイルのSHA256/byte数、
   transfer schema/run/attempt/buildId/workflowSha/platform、7 image一覧を照合する。
   GitHub download actionの非fatal digest warningは使用しない。
6. **OIDC前**にCIを再取得・転送証跡と一致確認。source.tarをローカルgit archiveと
   byte比較し、source.sha256を再生成して比較する。Docker saveは非regular assets、
   traversal/絶対path/重複entryを拒否し、config hash/OS/arch/revision/tagを確認して
   loadする。layer descriptorのpathも既知形式だけに限定して実entryと照合する。
   ロード後のImage IDも照合する。buildやtarget側scriptの実行はしない。
7. OIDCで既存publisher roleを3600秒取得。8repositoryの同tagをすべて事前確認し、
   **既存tagが1つでもあれば失敗**。DescribeImagesのTAGGED一覧を自動paginationで
   取得し、成功したJSON応答に対象tagがないことを確認する。
   CLI例外文字列を未存在扱いせず、403/timeout/通信失敗/不正JSON等は拒否する。
8. 7 imageをpush。各imageについてECRが返す**manifest digest**を取得し、
   registryからmanifest raw bytesを再取得してhash照合。そのconfig digestが
   事前検証Image IDと一致することを確認し、config blobも再取得してhash/byte数/
   linux/amd64/revisionを検証する。さらにdigest指定でDocker pullし、layer/config関係と
   pull後Image IDを照合する（内容アドレスcache再利用は可）。index/multi-platformは受入れない。
9. 全7件の検証後にstrict schema 1のrelease.jsonを生成。
   source/archive、source manifest、buildレポート、transfer.json、CI証跡、inspectionと
   release.jsonを1個のtarにし、OCI image manifestのlayerとして`ppe/releases`へ保存。
   OCI config/layerをupload後にdownloadしてdigestと**元ファイルのbytes**を照合する。
   その後にだけrelease manifestのtagをPUTする（complete releaseの唯一のmarker）。
   tag/digest両方でmanifestを再取得し、asset blobも再download・byte比較する。

tagは `sha-<40sha>-gha-<runId>-<attempt>`、buildIdは `gha-<runId>-<attempt>`。
同SHA再buildは別run/attemptで区別。同じtagの完了済み・部分保存済みrerunは拒否する。
push途中の失敗は残ったimage/blobを削除せず、complete markerを作らない。
Summaryの成功は最終再取得の成功後にのみ記録する。
OIDC失敗後のpublish stepは実行されない。認証config/tokenはprivate file/stdinのみで扱い、
argv/log/artifactに出力せず、終了時にlocal config/tagと署名付きdownload URLを削除する。

公開成功時は別途 `ecr-release-report-<run ID>-<attempt>` を7日保存する。
release.json、ci.json、images.tsv、transfer.json、OCI manifest、summaryだけを
明示的に選択し、Docker archive、WAR、source archive、認証設定は含めない。
1日保存のjob間転送artifact（Docker archiveを含む）とは区別する。

## release.json schema 1

[validator](../../scripts/ci/ecr-release-common.sh)は未知key、欠落key、数不足・重複、
SHA/run/attempt/buildId/tag不整合、digest/ID形式違反、repository/platform/revision不整合を拒否する。

| key | 契約 |
|---|---|
| schema / sha / runId / attempt / buildId / tag / platform | schema=1、完全SHA、正整数文字列run/attempt、上記build/tag、linux/amd64 |
| workflowSha | 実行した信頼main workflow/harnessの完全SHA（対象source SHAとは区別） |
| images | app/tools/db/runner/broker/nginx/backup各1件、追加名なし |
| images[].name / repository | 固定7名と`024378233912.dkr.ecr.ap-northeast-1.amazonaws.com/ppe/<name>` |
| images[].digest / digestType | registry manifestのsha256:64hex、digestType=manifest |
| images[].configDigest / imageId | config sha256:64hex、両者同一。**registry digestの代用ではない** |
| images[].revision / platform | release SHA / linux/amd64 |
| source.archiveSha256 / manifestSha256 | source.tar / source.sha256の64hex |
| reports.transferSha256 | transfer.jsonの64hex（全転送ファイルhash/byte数を含む） |
| ci | repository、SHA、workflow path/ID、event=push、branch=main、CI runId/attempt、完全一致の成功check2件 |

Docker image archive自体は短期のjob間転送だけ。長期資産はimageを各repositoryに、
sourceとreportsを`ppe/releases@sha256:<manifest digest>`に保持する。asset tarの
hashとsizeはOCI layer descriptorにある。release.json自身のregistry digestは循環参照させず、
最終Summaryで別に記録する。OCI tarは画像ではなく復旧用データで、runしない。
ORAS等の追加toolは使わず、bash/jq/curl/openssl/tarとDocker/AWS/GitHub CLIを使用する。
ECRのpresigned S3 blob redirectはTokyo S3 bucket hostだけ許可し、別HTTP requestで取得する。
registry認証をS3へ転送せず、redirect連鎖や外部hostは拒否する。

## 信頼根と未確認事項

- **署名付きprovenanceではない**。manifest/hashの信頼はECR writer権限と信頼する
  main workflow、SHA/sourceの対応確認に限定される。public signatures、
  外部署名検証サービス、attestation、自動EC2受入は未承認・未導入。
- artifactのhashは破損/取り違えを拒否するが、信頼するbuilder/main自体の侵害を証明しない。
  buildの既存credential scanは完全な全layer secret scanではない。
- 初回実公開時にIMMUTABLE、lifecycleなしを確認済み。設定変更は運用時に再確認する。preflightとworkflow
  concurrencyは同workflowを直列化するだけで、別writerとのatomic compare-and-setではない。
  tag上書き防止はECR immutabilityを運用前提とし、設定変更は別承認で行う。
- complete markerのPUT前に全image/assetを確認する。ただしPUT成功後のnetwork障害や
  最終確認失敗ではmarkerが存在する可能性がある。成功を報告せず、同tagを再発行せず、
  人間がdigest指定で再照合する。未完了imageの自動削除は行わない。
- 1日artifactは復旧資産ではない。ECR lifecycleは稼働/DB所有/rollback対象および
  release OCI assetsを保護する必要がある。保持容量・転送料金・scan/監視は別の運用受入。
- CIはDB/HTTP/実APIのopt-in skipを許す既存CI契約。実ECR/OIDC、Docker archive
  転送容量、OCI HTTP互換性、blob redirect、EC2のpull/受入はmockで確認済みとは扱わない。
  本番Dockerfiles、deployment scripts、application、migrationsは変更していない。

## ローカル検証

```bash
bash scripts/ci/tests/ecr-release-test.sh
bash scripts/ci/check-shell-syntax.sh
/Users/t.toida/go/bin/actionlint .github/workflows/ecr-release-publish.yml .github/workflows/ci.yml
git diff --check
```

mock試験はrepo配下の固有fixtureをcleanupし、実AWS/GitHub/registry/Docker接続なし。
不正SHA/main外/SHA ancestry、CI失敗/厳密check欠落、転送改変/7件不足/ID欠落、
remote digest/config不一致、途中push、重複/完了済みtag、OIDC拒否、
OCI asset upload/download不一致、ZIP改変/同runでないartifact/余分entry、
CIのworkflow/event/branch/job SHA/attempt不一致、S3以外のredirect拒否を確認する。
正常7件→OCI byte roundtripに加え、合成Tokyo S3 redirectへの別requestでregistry認証を
転送しないことも確認する。実S3接続ではない。
既存CIのShell harness stepに追加し、runner起動回帰追加後の19 deployment suites/zero skipを要求する。

ローカル結果（2026-10-10 JST）:

| コマンド | 結果 |
|---|---|
| `bash scripts/ci/tests/ecr-release-test.sh` | producer負例40件、正常CI/ZIP/source/config/7image/OCI byte roundtrip/S3 redirect成功。追加adapterの結果は[半手動runbook](./ecr-manual-deployment.md)参照（mockのみ） |
| `bash scripts/ci/check-shell-syntax.sh` | exit0、70 Shell files（Bash/dash） |
| `/Users/t.toida/go/bin/actionlint .github/workflows/ecr-release-publish.yml .github/workflows/ci.yml .github/workflows/production-image-build.yml` | exit0、3workflowの構文/式/action入力 |
| `git diff --check` | exit0 |
| `git diff --quiet -- .github/workflows/production-image-build.yml scripts/production containers src/main` | exit0、認証なしworkflow/本番scripts/Dockerfiles/application/migrationsの変更なし |

既存`image-build/image-assets/image-runner/reporting/runner-gates-test.sh`の5本もexit0。
試験本文は変更せず、各testのfixture root行だけをrepo配下の固有directoryへ
置換して`bash -c`で実行・cleanupした（本作業の一時directory制約のため）。
Image assetsは5ケース、image runnerは3ケース。既存3引数builderの失敗/
credential拒否/remote daemon拒否を維持した。
既存`ppe-backup:local`の実`docker image save`もrepo配下に出力し、tar entryのpath/typeと
`blobs/sha256/...`形式configのraw hash=Image IDを照合してexit0、archiveを削除した。
このimageはlinux/arm64であり、native amd64 hosted build/publishの受入ではない。
共有imageの変更やload/pushは行っていない。

上記は初期実装時のローカル記録。後続の実Push/OCI再取得受入は冒頭のrunで成功済み。
adapter追加時には既存18deployment suiteとJava全体回帰も再実行する。

次にすること: [半手動runbook](./ecr-manual-deployment.md)に従いIAM承認と本番受入の
別承認を得る。推奨AIモード: 対話型（承認）／オートパイロット（承認済み検証）
（AWS実行と費用/保持/本番反映の判断は承認待ちで停止）。
