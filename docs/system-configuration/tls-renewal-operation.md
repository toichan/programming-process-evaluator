# TLS証明書自動更新: 配置・受入手順

## 状態と承認境界

### 2026-10-10: app更新との連携補強（ローカルのみ）

デプロイ/rollback時にTLS専用環境のrelease・Compose・DB参照を同期するため、
リポジトリ版wrapperは配信ディレクトリの親にある
`.ppe-tls-operation.lock`をFD8で取得し、環境読込からCertbot終了まで保持する。
デプロイ側も同じlockを保持する。競合時はCertbot/ACME/hookを起動せず非0終了する。
既存hookの`.ppe-tls-renew-lock`と併用し、証明書配置の復旧契約は維持する。

これは本番配置済み`3c416a2e...`の受入記録を変更するものではない。
そのimmutable資産を上書きせず、新しい固定版へwrapperを配置し、
serviceの実際のExecStart・wrapper hash・環境パスを確認する別途承認が必要。
デプロイのproduction preflightは未対応wrapperや稼働中Certbotを拒否する。
Certbotのconfig/work/log、証明書・秘密鍵、ACME webroot、timer scheduleは変更しない。
環境同期・途中失敗時の復旧は[デプロイ補強契約](production-deployment.md#update-hardening-2026-10-10-jst-local-only)を参照。

2026-10-09のPhase 2はローカル実装・試験済み。Phase 3の本番配置・
非稼働設定検証は同日23:59 JSTに完了。2026-10-10 00:04 JSTに承認済みの
hook付きstaging dry-run 1回と本番hook/reload受入に成功。
00:07 JSTの追加承認後、00:08 JSTにtimerをenabled/activeへ復帰。
追いつき実行exit0と次回予定を確認し、自動更新設定の受入完了。
既存のCertbot 2.9.0、`ppeval` lineage、`certbot.timer`を継続する。
初回発行スクリプトは再実行しない。AWSリソース・IAM・本番DB・
immutable application release・バックアップ設定を変更しない。

本番適用には、専用運用資産と環境ファイル・service drop-inの配置、
`daemon-reload`、既存timerが修正serviceを自動実行することへの承認が必要。
ACME staging通信を伴うdry-run、配信用コピー更新とNginx reloadは別途承認が必要。
2026-10-09 23:56 JSTの承認により、実更新試験まで予期せぬ自動起動を防ぐため
timerを一時停止・無効化した。元のenabled/activeを記録し、
試験成功後に明示承認のもと復帰する。timerのschedule編集、
Nginx停止・再起動、EC2再起動はこの手順に含めない。

## Phase 3配置記録（2026-10-09 23:59〜2026-10-10 00:00 JST）

- 対象: account `024378233912`、region `ap-northeast-1`、
  instance `i-0ffd69e8f390bd396`をIMDSで直前再確認。
- 配布元: main/#44 merge `3c416a2e0a36c0e800f26fc5f472834387cf1c3b`。
  Phase 2の5配布ファイル・手順・テストと一致、作業前worktree clean。
- 配布archive SHA-256:
  `9f73584e4dfbb1e373c8338f6ef45c835cc49522fb6bad1120a17678f5f8b6e2`。
  SSM対話session経由で転送し、EC2でもarchiveと5ファイルを照合PASS。
  証明書・秘密鍵・実設定はarchiveへ含めず、S3転送なし。
- 固定配置先: `/var/lib/ppe/tls-operations/3c416a2e0a36c0e800f26fc5f472834387cf1c3b`。
  root:root、directories 0700、files 0600。
  `current`は当該固定版への新規symlink。既存app releaseは変更しない。
- 専用設定: `/var/lib/ppe/tls-operation.env`、root:root 0600。
  SHA-256 `aefef315760963173dda5ef7e353be3c23035f7510d3bba6706c103fb6dfc158`。
  app/DB releaseともに`0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`。
  既存secret snapshotのパスだけを参照し、secretの値を含めない。
- drop-in: `/etc/systemd/system/certbot.service.d/ppe-renewal.conf`、
  root:root 0644、SHA-256
  `533a1c7a126d78523504842fffc78365bf69124e2540bb0183d2ef3ceeb269af`。
  専用設定/drop-inは同一filesystemのstagingからhard linkで新規配置。
  既存ファイルの上書きなし。
- private退避・記録:
  `/var/lib/ppe/tls-stage3-3c416a2e-20261010-placement/before`。
  root:root 0700、systemd元定義・renewal設定・配信ペア・timer/runtime記録は0400。
  秘密鍵はEC2内だけでコピー、表示・外部転送なし。元ファイルとのcmp PASS。
- 実行結果: wrapper `--check`、`systemd-analyze verify --man=no certbot.service`、
  `systemctl daemon-reload` PASS。SSM本処理と独立した読取再検証はいずれも
  `REMOTE_EXIT=0`。新ExecStartは`/bin/bash`から固定運用版wrapper/専用設定を呼ぶ。
  serviceはinactive/dead。renew/hook/reloadは未実行。
- timerは作業前enabled/active(waiting)、予定2026-10-10 04:30:24 JST、
  `Persistent=yes`。stop/disable後はdisabled/inactive(dead)で次回予定なし。
  schedule/unit本体を編集せず、再起動してもtimerが自動再開しない状態で待機。
- 保護確認: 前後5コンテナのID/image/StartedAt/health/mounts、DB Volume、
  deployment state、Compose、backup設定hashとbackup timers状態が一致。
  配信証明書・秘密鍵、Certbot renewal設定、vendor service/timer本文もcmp一致。
  配置開始後のcertbot.service journal entriesは0、Certbot process/hook lockなし。
  student/teacher正式login各HTTPS200・信頼検証成功。期限2027-01-06 16:22:08 JST不変。
- 残り: ACME staging + active証明書hook反映を1回の`--dry-run-deploy`で
  検証する別承認と、成功後のtimer復帰承認。通常renew/実再起動試験は未実施。

## 最終検証記録（2026-10-10 00:04 JST）

- 00:02 JSTのユーザー承認に従い、直前のaccount/instance/region、
  5ファイルhash・専用設定・drop-in、5healthy、timer disabled/inactive、
  既存配信ペアとprivate退避一致、lockなしを確認。
- `renew-tls.sh /var/lib/ppe/tls-operation.env --dry-run-deploy`を
  **00:04:21〜00:04:30 JSTに1回のみ**実行、exit0。
  `all simulated renewals succeeded`とhookの構文検査/reload成功メッセージを確認。
  Certbot logで`acme-staging-v02.api.letsencrypt.org`の使用を読取確認。
  本番ACMEによる実発行・更新、追加dry-runは行わない。
- 実行記録は配置staging内の`dry-run-once`（0700）、`output.log`はroot:root 0600。
  attemptディレクトリを新規作成する方式で、同じ実行スクリプトの再実行を拒否。
  ログ全量・秘密情報は表示しない。
- hook退避: `/var/lib/ppe/tls-private/.ppe-tls-before-renew.7QE7o9`。
  directory 0700、退避秘密鍵0400。配置時の退避・更新前ペアとのcmp一致。
  dry-runはactive証明書を再配信し、staging証明書は配置しない。
- student/teacher正式login各HTTPS200・信頼検証成功。
  公開HTTPSで配信されたfingerprintは配信ファイルと一致。
  SHA-256 fingerprint
  `FF:11:34:28:29:DA:BD:A0:CF:07:DA:3C:01:DC:7E:F5:74:C9:53:84:8E:48:51:7E:B2:60:8A:30:2D:84:83:5A`、
  期限2027-01-06 16:22:08 JSTは不変。証明書/秘密鍵の公開鍵一致。
- runtime snapshot前後一致: 5healthy、container ID/image/StartedAt/mounts、
  DB Volume、deployment state、Compose、backup設定hash・backup timers状態。
  Nginx停止・再起動・再作成、DB/アプリ/backup設定変更なし。
- 本試験と独立した読取確認はいずれも`REMOTE_EXIT=0`。
- **timer復帰HOLD**: 永続stampは2026-10-09 08:14:23 UTC（17:14:23 JST）、
  calendarは00/12 UTC、`Persistent=true`、`RandomizedDelaySec=43200`。
  stampより後の12:00 UTC（21:00 JST）のcalendar eventが経過しているため、
  再開するとcatch-upの対象になり得る。以前の04:30:24 JST予定が保持される保証や、
  最初の起動が即時にならない保証はない。ユーザーの「予期せぬ即時起動の
  可能性があるなら再開しない」条件に従い、enable/startは実行しない。
- calendarのみの次回基準は2026-10-10 00:00 UTC（09:00 JST）だが、
  停止中のため**有効な次回実行予定はない**。基準時刻と実行予定を混同しない。
- 残作業: 想定内のcatch-up通常renew実行を含むtimer再開の追加承認を得るか、
  別承認のもとcatch-up回避方法を検討する。stampを書換え/削除しない、
  Persistent/scheduleも勝手に変更しない。実更新テストは成功したが
  自動運用再開は未完了。試験を再実行する必要はない。

## timer復帰・自動運用受入（2026-10-10 00:08 JST）

- ユーザーは00:07 JSTにPersistent catch-upを了承し、timer復帰を承認。
  対象IMDS、5ファイルhash、専用設定/drop-in、ExecStart、renewal設定、
  配信ペア/退避、両HTTPS、5healthy、lockなしを直前に確認。
- `systemctl enable certbot.timer`、`systemctl start certbot.timer`を各1回実行。
  手動renew、追加dry-run、schedule/stamp変更、Nginx停止・再起動なし。
- timerによる追いつき実行: **00:08:17〜00:08:18 JST**、
  `Result=success`、`ExecMainStatus=0`（exit0）。
  `ExecMainCode=1`はsystemdのCLD_EXITEDでありexit1ではない。
- 当該Certbot実行ログは証明書が`not yet due for renewal`であることを確認。
  当該ログsectionにERROR/CRITICAL/Tracebackなし、当該service journalの
  warning/error entriesは0。秘密情報を含むログ全量は表示しない。
  本実行では期限前のため証明書更新・hook再実行を行わない。
- 最終timerは**enabled/active(waiting)**、
  次回予定 **2026-10-10 17:39:34 JST（08:39:34 UTC）**。
  ランダム遅延を含む観測値であり、将来の各回の固定実行時刻ではない。
- 両正式login HTTPS200、trust検証成功、公開fingerprintと配信ファイル一致。
  証明書・秘密鍵の公開鍵一致、Certbot管理証明書と配信証明書一致、
  配置前ペアとのcmp一致。期限2027-01-06 16:22:08 JST不変。
- 5コンテナhealthy、ID/image/StartedAt/mounts、DB Volume、
  deployment state、Compose、backup設定hash・backup timers状態は前後一致。
  復帰と独立したログ読取確認の両方で`REMOTE_EXIT=0`。
- **受入判定: TLS自動更新設定は完了**。正しいlineageの自動処理、
  staging実更新試験、本番hook/reload、HTTPS、永続unit有効化を確認。
  将来の本番ACMEでの期限到来時実更新と実EC2再起動は今回実施していない。
  運用ではtimer/service結果、証明書期限、private退避容量を継続確認する。

## 資産・契約

- [renew-tls.sh](../../scripts/production/renew-tls.sh): 環境検証、Compose解析、更新。
- [tls-operation.env.example](../../scripts/production/tls-operation.env.example): パスのみの専用設定。
- [ppe-renewal.conf](../../scripts/production/systemd/certbot.service.d/ppe-renewal.conf): 標準serviceのExecStartを置換。
- [tls-renew-hook.sh](../../scripts/production/tls-renew-hook.sh): 退避・配置・検査・reload・復旧。
- [install-tls.sh](../../scripts/production/install-tls.sh): SAN、有効期限、公開鍵一致、権限、配置検証。

専用設定は実行ユーザー所有の0600通常ファイル。本番ではroot:root。
`KEY=value`、コメント行、空行のみ。引用符、shell展開、inline comment、
秘密の値、未許可変数、重複、未置換SHAを拒否する。内容をsourceしない。
必須値はファイルから取得し、既存シェルの同名変数で不足を補わない。
Composeは固定release・DB release/sourceと既存secret snapshotの**パス**を使用。
DockerはローカルUnix socketに限定する。Composeは`config --quiet`と
既存Nginxへの`exec -T`だけで、`up`/build/stop/restart/migrationを行わない。

`--check`は更新もhookも実行しない。`--dry-run`はACME検証を行うが、
deploy hookは実行しない。`--dry-run-deploy`は`--run-deploy-hooks`を追加する。
Certbot 2.9.0のこのモードでは検証成功後のhookに実際のactive lineageを渡す。
staging証明書を配信用コピーへ配置してはいけない。対象ホストの
`certbot --help all`で対応オプションを読み取り確認してから使用する。

## 本番適用前に確定する情報

1. AWS profile `ppe-deployer`、account `024378233912`、
   region `ap-northeast-1`、instance `i-0ffd69e8f390bd396`を再確認。
2. 既存5コンテナhealthy、release/DB image pin、secret snapshotパス、
   NginxのTLS/ACME mount、deployment state、Volume、公開HTTPSを記録。
   秘密ファイル、Docker環境全量、非redactedログは表示しない。
3. `systemctl cat/show certbot.service certbot.timer`と既存drop-inを確認。
   想定外のoverride、実行中service、同名配置先、運用lockがあれば停止。
4. ユーザーがreview/commitした固定SHAと配布manifestを確定する。
   未コミット資産や既存アプリreleaseを直接本番で実行しない。

## ローカル配布準備（固定SHA確定後）

以下は将来の実行例。SHA・出力先を確定してから実行する。
リポジトリ外の新規専用ディレクトリを使用する。秘密鍵・証明書・実設定は
アーカイブへ入れない。commit/push/mergeはユーザーが実施する。

```sh
set -eu
TLS_OPERATIONS_SHA=REPLACE_APPROVED_40_CHARACTER_SHA
git cat-file -e "$TLS_OPERATIONS_SHA^{commit}"
OUT="$HOME/ppe-tls-distribution-$TLS_OPERATIONS_SHA"
mkdir -m 0700 "$OUT"
git archive --format=tar "$TLS_OPERATIONS_SHA" \
  scripts/production/renew-tls.sh \
  scripts/production/tls-renew-hook.sh \
  scripts/production/install-tls.sh \
  scripts/production/tls-operation.env.example \
  scripts/production/systemd/certbot.service.d/ppe-renewal.conf \
  > "$OUT/tls-operations.tar"
mkdir -m 0700 "$OUT/extracted"
tar -xf "$OUT/tls-operations.tar" -C "$OUT/extracted"
(
  cd "$OUT/extracted"
  find scripts -type f -exec shasum -a 256 {} \; | sort
) > "$OUT/SHA256SUMS"
shasum -a 256 "$OUT/tls-operations.tar" > "$OUT/archive.sha256"
printf '%s\n' "$TLS_OPERATIONS_SHA" > "$OUT/source-commit.txt"
```

アーカイブは上記5ファイルだけと照合し、全hashを再検証する。
専用環境ファイルは別途テンプレートから作り、**再確認した本番パスだけ**
記入し0600にする。実secret値や秘密鍵はコピー・転送しない。
SSMの既存転送方式で新規root-private stagingに配布し、
転送payloadを端末ログへ表示しない。S3一時uploadは不要。
転送方式・staging・各hashを実行直前に確定する。

## 本番配置（別承認後のみ）

1. 固定版を新規`/var/lib/ppe/tls-operations/<TLS_OPERATIONS_SHA>`へ展開。
   root:root、directory 0700、script 0600。許可リスト、SHA-256一致を検証。
2. 新規`/var/lib/ppe/tls-operations/current`をその固定版へのsymlinkとして作る。
   既存pathがあれば上書きせず停止。wrapperは物理的な固定パスのhookを呼ぶ。
3. stagingで検証済みの専用設定を
   `/var/lib/ppe/tls-operation.env`へroot:root 0600で配置。
   backup専用`operation.env`とアプリ設定には触れない。
4. `/etc/systemd/system/certbot.service.d/ppe-renewal.conf`へdrop-inを配置。
   同名が既存なら停止。unit本文・timerは編集しない。
5. root-private記録ディレクトリに、変更前のservice/timer定義、
   配布hash、設定hash・権限、runtime識別情報を保存。秘密の値は出力しない。

設定・drop-inの最終配置にはstagingからのhard link（`ln`）など、
既存ファイルを置換しない操作を使う。同一filesystemであることを確認する。
`install`の無条件上書きや`ln -sf`は使わない。
途中失敗時は配置済み範囲を記録し、勝手に削除しない。

配置後のコマンド（root実行、本番承認後）:

```sh
/bin/bash /var/lib/ppe/tls-operations/current/scripts/production/renew-tls.sh \
  /var/lib/ppe/tls-operation.env --check
systemd-analyze verify certbot.service
systemctl daemon-reload
systemctl show certbot.service -p FragmentPath -p DropInPaths -p ExecStart
systemctl is-enabled certbot.timer
systemctl is-active certbot.timer
systemctl list-timers --all certbot.timer
```

`--check`はDocker設定解析のみ。service startを行わない。
実更新試験前はtimerを停止・無効化しておく。drop-inを読み込み、
後でtimerを復帰すると次回から新しい更新経路が動く。
復帰時に`Persistent=true`による未実行分の即時/遅延起動があり得るので、
復帰自体を実更新の承認範囲として説明する。

## 限定更新試験（外部通信・reload別承認後）

2026-10-09の依頼はdry-run 1回。実行直前に別承認を得て、
ACME staging検証とactive証明書の配信用コピー更新・reloadを
同一のhook付きdry-run 1回で検証する。通常dry-runを重ねて実行しない。

```sh
sudo /bin/bash /var/lib/ppe/tls-operations/current/scripts/production/renew-tls.sh \
  /var/lib/ppe/tls-operation.env --dry-run-deploy
```

失敗はexit code・安全なエラーメッセージを記録し、原因不明のまま反復しない。
hook対象lineage/SANと配信fingerprintを照合し、両ドメインのHTTPS信頼検証、
HTTP redirect、Nginx/container health、container ID/StartedAt、
DB/Volume/stateが不変であることを確認する。設定永続化の確認は実再起動試験とは
区別する。実EC2再起動は今回の承認範囲外。

試験成功後、両HTTPS/health/配信fingerprint/退避と保護情報を確認し、
復帰承認を得て元のenabled/activeへ戻す:

```sh
sudo systemctl enable certbot.timer
sudo systemctl start certbot.timer
systemctl is-enabled certbot.timer
systemctl is-active certbot.timer
systemctl list-timers --all certbot.timer
```

2026-10-10 00:08 JSTに追加承認のもと実行済み（上記復帰記録参照）。
失敗時はtimer disabled/inactiveを維持し、秘密情報を除外して原因を調査する。

## 復旧・保護

- hookは`tls-private/.ppe-tls-renew-lock`で同時hookを拒否し、
  `.ppe-tls-before-renew.*`を0700、退避ペアを0400で保持する。
  成功時も退避を削除しない。秘密鍵を含むので外部へ配布しない。
  容量・権限を点検し、古い退避の削除は別の承認・保存方針に従う。
- installerが途中で失敗しても、hookは旧ペアを各ファイルの一時コピー→renameで
  復旧する。その後`nginx -t`→reload。元の処理の非0終了を保持する。
  復旧コピー・構文検査・reloadが失敗すれば明示して手動確認へ移る。
  この場合はlockも保持して後続hookの配置を拒否する。正常復旧を確認するまで
  lockを解除しない。
- Certbotのarchive/liveを削除・巻き戻ししない。配信用コピーの復旧と
  Certbot lineageの更新状態は別。更新済みlineageを再配信する場合は、
  状態確認後に明示承認を得る（次のrenewが期限判定でhookを実行しない場合がある）。
- ファイル単位renameはatomicだが、ペア全体はatomicではない。
  SIGKILL/電源断はtrapを実行しない。lock・退避・配信ペア・稼働中Nginxを調査し、
  正しい退避ペアが確認できるまでlock除去やreloadを行わない。
- 配線だけ戻す場合、今回追加したdrop-inをroot-private退避先へ移し
  `daemon-reload`する。timerは元のまま維持。事前からある別drop-inは変更しない。
  専用設定・固定版・symlinkも削除しない。旧serviceに戻すとPPE更新が再び
  未配線になるため恒久解決とは扱わない。rollback自体も承認後に実施する。
- 既存の直接installer操作との競合は避ける。certbot自体のlockとhook lockに
  加えて、すべての手動TLS配置を運用上直列化する。

## ローカル検証記録

2026-10-09 JST、合成証明書のみ。実Let's Encrypt呼出しなし。

| コマンド | 結果 | 範囲 |
|---|---|---|
| `bash scripts/production/tests/tls-workflow-test.sh` | exit0、34 PASS / 0 FAIL / 0 skip（macOSとUbuntuそれぞれ） | bootstrap既存回帰、正常更新、対象外lineage、SAN/鍵/設定不備、配置途中・構文検査・reload失敗、復旧コピー失敗時のlock保持と後続拒否、private backup保持、同時hook拒否、wrapper各mode、秘密値非出力 |
| `bash scripts/production/tests/nginx-startup-test.sh` | exit0、4 PASS / 0 FAIL / 0 skip | 一意project・動的loopback port・合成upstreamで本物Nginxを起動。両hostで新証明書をCAとしてHTTPS検証、container ID不変、旧ペア退避一致、test資産だけ清掃 |
| `bash -n scripts/production/renew-tls.sh scripts/production/tls-renew-hook.sh scripts/production/tests/tls-workflow-test.sh scripts/production/tests/nginx-startup-test.sh` | exit0 | shell構文 |
| Ubuntu container内の`systemd-analyze verify --man=no /tmp/units/certbot.service` | exit0、1 PASS | Phase 1で確認した標準ExecStart/Type/PrivateTmpに実drop-inを組み合わせた定義検証。実serviceは起動しない |

Dockerはlocal Unix endpointで利用可。hostのNode.js/systemd-analyze/shellcheckは
未導入。ブラウザー画面・業務機能は変更していないのでブラウザーE2EやJava全体
buildは対象外。systemd定義はcached Ubuntu containerで追加検証済み。本番unitとの
結合、実ACME、実hook/reload、自動timer実行、再起動後の実動作は未確認。

Ubuntu再現コマンド（既存cached imageを使用、network none、repo read-only、
書込・mock実行は専用tmpfsのみ）:

```sh
docker run --rm --network none --read-only \
  --tmpfs /tmp:rw,exec,nosuid,nodev,size=32m \
  --mount "type=bind,source=$PWD,target=/repo,readonly" \
  --entrypoint /bin/bash mcr.microsoft.com/playwright:v1.51.1-noble -c '
    set -eu
    mkdir -p /tmp/units/certbot.service.d
    printf "[Unit]\nDescription=Certbot baseline from Phase 1\n[Service]\nType=oneshot\nExecStart=/usr/bin/certbot -q renew --no-random-sleep-on-renew\nPrivateTmp=true\n" > /tmp/units/certbot.service
    cp /repo/scripts/production/systemd/certbot.service.d/ppe-renewal.conf /tmp/units/certbot.service.d/
    systemd-analyze verify --man=no /tmp/units/certbot.service
    echo "PASS: Ubuntu systemd verifies baseline plus PPE drop-in"
    TMPDIR=/tmp bash /repo/scripts/production/tests/tls-workflow-test.sh
  '
```
