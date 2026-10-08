#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$scripts/../.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-tls-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT

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
        ;;
esac
exit 0
DOCKER
cat > "$test_root/bin/certbot" <<'CERTBOT'
#!/bin/sh
set -eu
printf 'certbot\n' >> "$PPE_TEST_TRACE"
config=
while [ "$#" -gt 0 ]; do
    if [ "$1" = --config-dir ]; then config=$2; shift 2; else shift; fi
done
[ "${PPE_TEST_FAIL_CERTBOT:-0}" != 1 ] || exit 1
mkdir -p "$config/live/ppeval"
cp "$PPE_TEST_CERT" "$config/live/ppeval/fullchain.pem"
cp "$PPE_TEST_KEY" "$config/live/ppeval/privkey.pem"
CERTBOT
chmod 0700 "$test_root/bin/docker" "$test_root/bin/certbot"

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
