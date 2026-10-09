# TLS証明書自動更新: 配置・受入手順

## 状態と承認境界

2026-10-09のPhase 2はローカル実装・試験のみ。本番適用は未実施。
既存のCertbot 2.9.0、`ppeval` lineage、`certbot.timer`を継続する。
初回発行スクリプトは再実行しない。AWSリソース・IAM・本番DB・
immutable application release・バックアップ設定を変更しない。

本番適用には、専用運用資産と環境ファイル・service drop-inの配置、
`daemon-reload`、既存timerが修正serviceを自動実行することへの承認が必要。
ACME staging通信を伴うdry-run、配信用コピー更新とNginx reloadは別途承認が必要。
timerの再設定・再起動、Nginx停止・再起動、EC2再起動はこの手順に含めない。

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
drop-inを読み込むと既存active timerの次回実行から新しい更新経路が動く。
この自動実行の影響も配置承認の対象として説明する。

## 限定更新試験（外部通信・reload別承認後）

最初はhookを実行しないACME staging dry-runを1回:

```sh
sudo /bin/bash /var/lib/ppe/tls-operations/current/scripts/production/renew-tls.sh \
  /var/lib/ppe/tls-operation.env --dry-run
```

成功後、active証明書の配信用コピー更新・reloadを伴う試験を1回:

```sh
sudo /bin/bash /var/lib/ppe/tls-operations/current/scripts/production/renew-tls.sh \
  /var/lib/ppe/tls-operation.env --dry-run-deploy
```

失敗はexit code・安全なエラーメッセージを記録し、原因不明のまま反復しない。
hook対象lineage/SANと配信fingerprintを照合し、両ドメインのHTTPS信頼検証、
HTTP redirect、Nginx/container health、container ID/StartedAt、
DB/Volume/stateが不変であることを確認する。設定永続化の確認は実再起動試験とは
区別する。実EC2再起動は今回の承認範囲外。

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
