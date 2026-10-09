#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$scripts/../.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-tls-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
export PPE_TEST_REAL_MV
PPE_TEST_REAL_MV=$(command -v mv)

mkdir -p "$test_root/bin" "$test_root/compose" "$test_root/config/live/ppeval"
chmod 0700 "$test_root"
openssl req -x509 -newkey rsa:2048 -nodes -days 3 \
    -keyout "$test_root/old.key" -out "$test_root/old.crt" \
    -subj /CN=student.ppeval.net \
    -addext 'subjectAltName=DNS:student.ppeval.net,DNS:teacher.ppeval.net' \
    >/dev/null 2>&1
openssl req -x509 -newkey rsa:2048 -nodes -days 3 \
    -keyout "$test_root/new.key" -out "$test_root/new.crt" \
    -subj /CN=student.ppeval.net \
    -addext 'subjectAltName=DNS:student.ppeval.net,DNS:teacher.ppeval.net' \
    >/dev/null 2>&1

cat > "$test_root/bin/docker" <<'DOCKER'
#!/bin/sh
printf '%s\n' "$*" >> "$PPE_TEST_TRACE"
case " $* " in
    *" ps --status running -q nginx "*)
        [ "${PPE_TEST_FAIL_PS:-0}" != 1 ] || exit 23
        if [ "${PPE_TEST_NGINX_RUNNING:-0}" = 1 ]; then printf 'existing-nginx\n'; fi
        exit 0
        ;;
    *" up -d --no-deps --wait --wait-timeout 30 acme-bootstrap "*)
        [ -z "$(find "$PPE_ACME_DIR" -prune ! -perm 0755 -print)" ] || {
            echo "FAIL: ACME webroot must be 0755 before Nginx starts" >&2
            exit 1
        }
        for directory in "$PPE_TLS_DIR" "$PPE_CERTBOT_CONFIG_DIR" "$PPE_CERTBOT_WORK_DIR" "$PPE_CERTBOT_LOG_DIR"; do
            [ -z "$(find "$directory" -prune ! -perm 0700 -print)" ] || {
                echo "FAIL: TLS and Certbot directories must remain 0700" >&2
                exit 1
            }
        done
        [ "${PPE_TEST_FAIL_BOOTSTRAP_UP:-0}" != 1 ] || exit 17
        ;;
    *" stop acme-bootstrap "*)
        [ "${PPE_TEST_FAIL_BOOTSTRAP_STOP:-0}" != 1 ] || exit 19
        ;;
    *" exec -T nginx nginx -t "*)
        [ "${PPE_TEST_FAIL_NGINX_TEST:-0}" != 1 ] || exit 1
        if [ "${PPE_TEST_FAIL_FIRST_TEST:-0}" = 1 ] && [ ! -f "$PPE_TEST_TRACE.test-failed" ]; then
            touch "$PPE_TEST_TRACE.test-failed"
            exit 31
        fi
        ;;
    *" exec -T nginx nginx -s reload "*)
        [ "${PPE_TEST_FAIL_RELOAD:-0}" != 1 ] || exit 33
        if [ "${PPE_TEST_FAIL_FIRST_RELOAD:-0}" = 1 ] && [ ! -f "$PPE_TEST_TRACE.reload-failed" ]; then
            touch "$PPE_TEST_TRACE.reload-failed"
            exit 32
        fi
        ;;
    *" config --quiet "*)
        [ "${PPE_TEST_FAIL_CONFIG:-0}" != 1 ] || exit 34
        ;;
esac
exit 0
DOCKER
cat > "$test_root/bin/certbot" <<'CERTBOT'
#!/bin/sh
set -eu
printf 'certbot\n' >> "$PPE_TEST_TRACE"
printf '%s\n' "$@" > "$PPE_TEST_TRACE.certbot-arguments"
config=
while [ "$#" -gt 0 ]; do
    if [ "$1" = --config-dir ]; then config=$2; shift 2; else shift; fi
done
[ "${PPE_TEST_FAIL_CERTBOT:-0}" != 1 ] || exit 1
mkdir -p "$config/live/ppeval"
cp "$PPE_TEST_CERT" "$config/live/ppeval/fullchain.pem"
cp "$PPE_TEST_KEY" "$config/live/ppeval/privkey.pem"
CERTBOT
cat > "$test_root/bin/mv" <<'MV'
#!/bin/sh
if [ "${PPE_TEST_FAIL_KEY_MOVE:-0}" = 1 ]; then
    case " $* " in
        *"/.key.new "*)
            cmp -s "$PPE_TEST_CERT" "$PPE_TLS_DIR/fullchain.pem" || exit 99
            printf 'partial-pair-published\n' >> "$PPE_TEST_TRACE"
            echo "Synthetic private-key publication failure." >&2
            exit 35
            ;;
    esac
fi
if [ "${PPE_TEST_FAIL_RESTORE_MOVE:-0}" = 1 ]; then
    case " $* " in *".restore."*) echo "Synthetic recovery rename failure." >&2; exit 36 ;; esac
fi
exec "$PPE_TEST_REAL_MV" "$@"
MV
chmod 0700 "$test_root/bin/docker" "$test_root/bin/certbot" "$test_root/bin/mv"

export PATH="$test_root/bin:$PATH"
export PPE_TEST_TRACE="$test_root/trace"
export PPE_PROJECT=ppe-sim-tls
export PPE_OPERATION_APPROVAL=local-test
export PPE_ACME_TEST_MODE=mock
export PPE_TLS_BOOTSTRAP_APPROVED=yes
export PPE_ACME_EMAIL=operator@example.invalid
export PPE_COMPOSE_FILE="$repo/compose.production.yml"
export PPE_TLS_DIR="$test_root/tls/current"
export PPE_ACME_DIR="$test_root/acme"
export PPE_CERTBOT_CONFIG_DIR="$test_root/config"
export PPE_CERTBOT_WORK_DIR="$test_root/work"
export PPE_CERTBOT_LOG_DIR="$test_root/log"
export PPE_TEST_CERT="$test_root/old.crt"
export PPE_TEST_KEY="$test_root/old.key"
mkdir -p "$test_root/tls"
chmod 0700 "$test_root/tls"
mkdir -m 0700 "$PPE_ACME_DIR"
: > "$PPE_TEST_TRACE"

bash "$scripts/tls-bootstrap.sh" >/dev/null
cmp -s "$test_root/old.crt" "$PPE_TLS_DIR/fullchain.pem"
cmp -s "$test_root/old.key" "$PPE_TLS_DIR/privkey.pem"
grep -q -- '--profile acme-bootstrap up' "$PPE_TEST_TRACE"
grep -q -- '--profile acme-bootstrap stop' "$PPE_TEST_TRACE"
grep -q 'exec -T nginx nginx -t' "$PPE_TEST_TRACE"
grep -q 'exec -T nginx nginx -s reload' "$PPE_TEST_TRACE"
echo "PASS: first certificate bootstrap installs matching material and validates/reloads HTTPS Nginx"
[[ -z "$(find "$PPE_ACME_DIR" -prune ! -perm 0755 -print)" ]]
for directory in "$PPE_CERTBOT_CONFIG_DIR" "$PPE_CERTBOT_WORK_DIR" "$PPE_CERTBOT_LOG_DIR" "$test_root/tls"; do
    [[ -z "$(find "$directory" -prune ! -perm 0700 -print)" ]]
done
[[ -z "$(find "$PPE_CERTBOT_CONFIG_DIR/live/ppeval/privkey.pem" -prune ! -perm 0600 -print)" ]]
echo "PASS: an existing 0700 ACME webroot becomes 0755 while private directories and the Certbot key remain private"

assert_bootstrap_only_cleanup() {
    grep -q -- '--profile acme-bootstrap stop acme-bootstrap$' "$PPE_TEST_TRACE"
    if grep -Eq 'certbot|exec | up .* nginx$| stop nginx$| down| rm ' "$PPE_TEST_TRACE"; then
        echo "FAIL: failed bootstrap touched normal Nginx or continued certificate issuance" >&2
        exit 1
    fi
    [[ ! -e "$PPE_TLS_DIR/fullchain.pem" && ! -e "$PPE_TLS_DIR/privkey.pem" ]]
}

installed_tls=$PPE_TLS_DIR
export PPE_TLS_DIR="$test_root/tls/startup-failure"
: > "$PPE_TEST_TRACE"
status=0
PPE_TEST_FAIL_BOOTSTRAP_UP=1 bash "$scripts/tls-bootstrap.sh" > "$test_root/startup.log" 2>&1 || status=$?
[[ "$status" = 17 ]]
assert_bootstrap_only_cleanup
echo "PASS: partial bootstrap startup failure stops only bootstrap and preserves the original exit status"

: > "$PPE_TEST_TRACE"
status=0
PPE_TEST_FAIL_BOOTSTRAP_UP=1 PPE_TEST_FAIL_BOOTSTRAP_STOP=1 \
    bash "$scripts/tls-bootstrap.sh" > "$test_root/cleanup.log" 2>&1 || status=$?
[[ "$status" = 17 ]]
assert_bootstrap_only_cleanup
grep -q 'Failed to stop ACME bootstrap' "$test_root/cleanup.log"
echo "PASS: cleanup failure is reported without masking the startup failure"

: > "$PPE_TEST_TRACE"
status=0
PPE_TEST_FAIL_CERTBOT=1 bash "$scripts/tls-bootstrap.sh" > "$test_root/certbot.log" 2>&1 || status=$?
[[ "$status" = 1 ]]
grep -q -- '--profile acme-bootstrap stop acme-bootstrap$' "$PPE_TEST_TRACE"
if grep -Eq 'exec | up .* nginx$| stop nginx$| down| rm ' "$PPE_TEST_TRACE"; then
    echo "FAIL: failed certificate issuance touched normal Nginx" >&2
    exit 1
fi
[[ ! -e "$PPE_TLS_DIR/fullchain.pem" && ! -e "$PPE_TLS_DIR/privkey.pem" ]]
echo "PASS: failed certificate issuance stops bootstrap without installing TLS or starting HTTPS"

: > "$PPE_TEST_TRACE"
status=0
PPE_TEST_NGINX_RUNNING=1 bash "$scripts/tls-bootstrap.sh" > "$test_root/running.log" 2>&1 || status=$?
[[ "$status" = 1 ]]
[[ "$(wc -l < "$PPE_TEST_TRACE" | tr -d ' ')" = 1 ]]
grep -q 'ps --status running -q nginx$' "$PPE_TEST_TRACE"
echo "PASS: existing normal Nginx prevents bootstrap without any container mutation"

: > "$PPE_TEST_TRACE"
status=0
PPE_TEST_FAIL_PS=1 bash "$scripts/tls-bootstrap.sh" > "$test_root/ps.log" 2>&1 || status=$?
[[ "$status" = 23 ]]
[[ "$(wc -l < "$PPE_TEST_TRACE" | tr -d ' ')" = 1 ]]
echo "PASS: failure to inspect normal Nginx aborts before starting or stopping containers"

export PPE_TLS_DIR="$installed_tls"
: > "$PPE_TEST_TRACE"
status=0
bash "$scripts/tls-bootstrap.sh" > "$test_root/existing-tls.log" 2>&1 || status=$?
[[ "$status" = 2 && ! -s "$PPE_TEST_TRACE" ]]
cmp -s "$test_root/old.crt" "$PPE_TLS_DIR/fullchain.pem"
cmp -s "$test_root/old.key" "$PPE_TLS_DIR/privkey.pem"
echo "PASS: existing TLS material prevents bootstrap and remains unchanged"

export RENEWED_LINEAGE="$test_root/config/live/ppeval"
export RENEWED_DOMAINS='student.ppeval.net teacher.ppeval.net'
export PPE_TEST_CERT="$test_root/new.crt"
export PPE_TEST_KEY="$test_root/new.key"
cp "$PPE_TEST_CERT" "$RENEWED_LINEAGE/fullchain.pem"
cp "$PPE_TEST_KEY" "$RENEWED_LINEAGE/privkey.pem"
PPE_TEST_FAIL_NGINX_TEST=1
export PPE_TEST_FAIL_NGINX_TEST
if bash "$scripts/tls-renew-hook.sh" >/dev/null 2>&1; then
    echo "FAIL: renewal validation failure unexpectedly succeeded" >&2
    exit 1
fi
cmp -s "$test_root/old.crt" "$PPE_TLS_DIR/fullchain.pem"
cmp -s "$test_root/old.key" "$PPE_TLS_DIR/privkey.pem"
echo "PASS: failed renewal validation restores the previous certificate and key"
[[ -d "$test_root/tls/.ppe-tls-renew-lock" ]]
rmdir "$test_root/tls/.ppe-tls-renew-lock"

unset PPE_TEST_FAIL_NGINX_TEST
: > "$PPE_TEST_TRACE"
if ! bash "$scripts/tls-renew-hook.sh" > "$test_root/renew.log" 2>&1; then
    cat "$test_root/renew.log" >&2
    exit 1
fi
cmp -s "$test_root/new.crt" "$PPE_TLS_DIR/fullchain.pem" || {
    cat "$test_root/renew.log" >&2
    printf 'expected certificate sha256: ' >&2
    shasum -a 256 "$test_root/new.crt" | awk '{print $1}' >&2
    printf 'installed certificate sha256: ' >&2
    shasum -a 256 "$PPE_TLS_DIR/fullchain.pem" | awk '{print $1}' >&2
    echo "FAIL: valid renewal did not publish the expected certificate." >&2
    exit 1
}
cmp -s "$test_root/new.key" "$PPE_TLS_DIR/privkey.pem" || {
    echo "FAIL: valid renewal did not publish the expected key." >&2
    exit 1
}
grep -q 'exec -T nginx nginx -s reload' "$PPE_TEST_TRACE" || {
    echo "FAIL: valid renewal did not reload Nginx." >&2
    exit 1
}
echo "PASS: valid certificate renewal validates and reloads Nginx"

if env PPE_ACME_TEST_MODE=mock PPE_OPERATION_APPROVAL=none \
    PPE_TLS_DIR="$test_root/tls/guard-check" \
    bash "$scripts/tls-bootstrap.sh" >/dev/null 2>&1; then
    echo "FAIL: mock bootstrap without the local-test guard unexpectedly succeeded" >&2
    exit 1
fi
echo "PASS: mock ACME mode refuses projects without the local-test approval gate"

reset_renewal() {
    chmod 0600 "$PPE_TLS_DIR/fullchain.pem" "$PPE_TLS_DIR/privkey.pem"
    cp "$test_root/old.crt" "$PPE_TLS_DIR/fullchain.pem"
    cp "$test_root/old.key" "$PPE_TLS_DIR/privkey.pem"
    chmod 0444 "$PPE_TLS_DIR/fullchain.pem" "$PPE_TLS_DIR/privkey.pem"
    : > "$PPE_TEST_TRACE"
    rm -f "$PPE_TEST_TRACE.test-failed" "$PPE_TEST_TRACE.reload-failed" "$PPE_TEST_TRACE.certbot-arguments"
}
assert_old_pair() {
    cmp -s "$test_root/old.crt" "$PPE_TLS_DIR/fullchain.pem"
    cmp -s "$test_root/old.key" "$PPE_TLS_DIR/privkey.pem"
    [[ ! -d "$test_root/tls/.ppe-tls-renew-lock" ]]
}
for fault in PPE_TEST_FAIL_KEY_MOVE PPE_TEST_FAIL_FIRST_TEST PPE_TEST_FAIL_FIRST_RELOAD PPE_TEST_FAIL_RELOAD; do
    reset_renewal
    status=0
    env "$fault=1" bash "$scripts/tls-renew-hook.sh" > "$test_root/$fault.log" 2>&1 || status=$?
    [[ "$status" != 0 ]]
    grep -q 'Previous certificate pair retained in private backup:' "$test_root/$fault.log"
    if [[ "$fault" = PPE_TEST_FAIL_RELOAD ]]; then
        grep -q 'TLS recovery is incomplete' "$test_root/$fault.log"
        grep -q 'TLS renewal lock retained' "$test_root/$fault.log"
        [[ -d "$test_root/tls/.ppe-tls-renew-lock" ]]
        rmdir "$test_root/tls/.ppe-tls-renew-lock"
    else
        grep -q 'Previous TLS certificate restored' "$test_root/$fault.log"
    fi
    assert_old_pair
    if [[ "$fault" = PPE_TEST_FAIL_KEY_MOVE ]]; then grep -q partial-pair-published "$PPE_TEST_TRACE"; fi
    echo "PASS: $fault preserves failure status, restores the old pair and retains recovery material"
done

reset_renewal
status=0
PPE_TEST_FAIL_FIRST_RELOAD=1 PPE_TEST_FAIL_RESTORE_MOVE=1 \
    bash "$scripts/tls-renew-hook.sh" > "$test_root/recovery-failure.log" 2>&1 || status=$?
[[ "$status" = 32 && -d "$test_root/tls/.ppe-tls-renew-lock" ]]
grep -q 'Previous certificate file restoration failed' "$test_root/recovery-failure.log"
grep -q 'TLS recovery is incomplete' "$test_root/recovery-failure.log"
backup=$(sed -n 's/^Previous certificate pair retained in private backup: //p' "$test_root/recovery-failure.log")
cmp -s "$test_root/old.crt" "$backup/fullchain.pem"
cmp -s "$test_root/old.key" "$backup/privkey.pem"
status=0
bash "$scripts/tls-renew-hook.sh" > "$test_root/recovery-blocked.log" 2>&1 || status=$?
[[ "$status" != 0 ]]
grep -q 'already in progress' "$test_root/recovery-blocked.log"
rmdir "$test_root/tls/.ppe-tls-renew-lock"
echo "PASS: recovery copy failure retains the old private pair, preserves the original exit and blocks further deployment"

for invalid in lineage domains missing-key mismatched-key concurrent; do
    reset_renewal
    status=0
    case "$invalid" in
        lineage)
            RENEWED_LINEAGE="$test_root/config/live/unrelated" bash "$scripts/tls-renew-hook.sh" > "$test_root/input.log" 2>&1 || status=$?
            ;;
        domains)
            RENEWED_DOMAINS=student.ppeval.net bash "$scripts/tls-renew-hook.sh" > "$test_root/input.log" 2>&1 || status=$?
            ;;
        missing-key)
            "$PPE_TEST_REAL_MV" "$RENEWED_LINEAGE/privkey.pem" "$test_root/saved-key"
            bash "$scripts/tls-renew-hook.sh" > "$test_root/input.log" 2>&1 || status=$?
            "$PPE_TEST_REAL_MV" "$test_root/saved-key" "$RENEWED_LINEAGE/privkey.pem"
            ;;
        mismatched-key)
            cp "$test_root/old.key" "$RENEWED_LINEAGE/privkey.pem"
            bash "$scripts/tls-renew-hook.sh" > "$test_root/input.log" 2>&1 || status=$?
            cp "$test_root/new.key" "$RENEWED_LINEAGE/privkey.pem"
            ;;
        concurrent)
            mkdir "$test_root/tls/.ppe-tls-renew-lock"
            bash "$scripts/tls-renew-hook.sh" > "$test_root/input.log" 2>&1 || status=$?
            rmdir "$test_root/tls/.ppe-tls-renew-lock"
            ;;
    esac
    [[ "$status" != 0 ]]
    assert_old_pair
    if [[ "$invalid" = lineage || "$invalid" = domains || "$invalid" = concurrent ]]; then
        [[ ! -s "$PPE_TEST_TRACE" ]]
    fi
    echo "PASS: renewal rejects $invalid without changing the serving pair"
done

mkdir -p "$test_root/secrets" "$test_root/db-source" "$test_root/config/renewal"
printf 'synthetic renewal configuration\n' > "$test_root/config/renewal/ppeval.conf"
cat > "$test_root/tls.env" <<ENV
PPE_PROJECT=ppe-sim-tls
PPE_RELEASE=0000000000000000000000000000000000000000
PPE_DB_RELEASE=1111111111111111111111111111111111111111
PPE_DB_SOURCE_DIR=$test_root/db-source
PPE_COMPOSE_FILE=$repo/compose.production.yml
PPE_SECRETS_DIR=$test_root/secrets
PPE_TLS_DIR=$test_root/tls/current
PPE_ACME_DIR=$test_root/acme
PPE_CERTBOT_CONFIG_DIR=$test_root/config
PPE_CERTBOT_WORK_DIR=$test_root/work
PPE_CERTBOT_LOG_DIR=$test_root/log
ENV
chmod 0600 "$test_root/tls.env"
for mode in --check renew --dry-run --dry-run-deploy; do
    reset_renewal
    bash "$scripts/renew-tls.sh" "$test_root/tls.env" "$mode" > "$test_root/wrapper.log" 2>&1
    grep -q 'config --quiet' "$PPE_TEST_TRACE"
    if [[ "$mode" = --check ]]; then
        [[ ! -e "$PPE_TEST_TRACE.certbot-arguments" ]]
    else
        for argument in --config-dir --work-dir --logs-dir --cert-name --deploy-hook --non-interactive; do
            grep -qx -- "$argument" "$PPE_TEST_TRACE.certbot-arguments"
        done
        grep -qx "$test_root/config" "$PPE_TEST_TRACE.certbot-arguments"
        grep -qx "$test_root/work" "$PPE_TEST_TRACE.certbot-arguments"
        grep -qx "$test_root/log" "$PPE_TEST_TRACE.certbot-arguments"
        grep -qx "/bin/bash $scripts/tls-renew-hook.sh" "$PPE_TEST_TRACE.certbot-arguments"
        if [[ "$mode" = --dry-run* ]]; then grep -qx -- --dry-run "$PPE_TEST_TRACE.certbot-arguments"; fi
        if [[ "$mode" = --dry-run-deploy ]]; then
            grep -qx -- --run-deploy-hooks "$PPE_TEST_TRACE.certbot-arguments"
        else
            ! grep -qx -- --run-deploy-hooks "$PPE_TEST_TRACE.certbot-arguments"
        fi
    fi
    echo "PASS: wrapper $mode parses Compose and supplies explicit directories with correctly gated hook testing"
done

for invalid in mode permissions missing duplicate secret shell missing-lineage compose-failure; do
    reset_renewal
    cp "$test_root/tls.env" "$test_root/invalid.env"
    chmod 0600 "$test_root/invalid.env"
    mode=--check
    case "$invalid" in
        mode) mode=--force-renewal ;;
        permissions) chmod 0644 "$test_root/invalid.env" ;;
        missing) sed '/^PPE_ACME_DIR=/d' "$test_root/tls.env" > "$test_root/invalid.env" ;;
        duplicate) printf 'PPE_PROJECT=ppe-sim-tls\n' >> "$test_root/invalid.env" ;;
        secret) printf 'DB_PASSWORD=synthetic-secret-never-log\n' >> "$test_root/invalid.env" ;;
        shell) printf 'PPE_PROJECT=$(touch %s)\n' "$test_root/should-not-exist" >> "$test_root/invalid.env" ;;
        missing-lineage) rm "$test_root/config/renewal/ppeval.conf" ;;
        compose-failure) export PPE_TEST_FAIL_CONFIG=1 ;;
    esac
    status=0
    bash "$scripts/renew-tls.sh" "$test_root/invalid.env" "$mode" > "$test_root/wrapper-$invalid.log" 2>&1 || status=$?
    unset PPE_TEST_FAIL_CONFIG
    printf 'synthetic renewal configuration\n' > "$test_root/config/renewal/ppeval.conf"
    [[ "$status" != 0 && ! -e "$PPE_TEST_TRACE.certbot-arguments" && ! -e "$test_root/should-not-exist" ]]
    echo "PASS: wrapper rejects $invalid before Certbot runs"
done
if grep -F -f "$test_root/old.key" -f "$test_root/new.key" "$test_root/"*.log >/dev/null \
    || grep -F 'synthetic-secret-never-log' "$test_root/"*.log >/dev/null; then
    echo "FAIL: TLS test logs contain private key material or a secret value." >&2
    exit 1
fi
echo "PASS: success and failure logs contain no synthetic private-key material or secret value"
