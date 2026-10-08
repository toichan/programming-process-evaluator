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
    *" ps --status running -q nginx "*) exit 0 ;;
    *" exec -T nginx nginx -t "*)
        [ "${PPE_TEST_FAIL_NGINX_TEST:-0}" != 1 ] || exit 1
        ;;
esac
exit 0
DOCKER
cat > "$test_root/bin/certbot" <<'CERTBOT'
#!/bin/sh
set -eu
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
: > "$PPE_TEST_TRACE"

bash "$scripts/tls-bootstrap.sh" >/dev/null
cmp -s "$test_root/old.crt" "$PPE_TLS_DIR/fullchain.pem"
cmp -s "$test_root/old.key" "$PPE_TLS_DIR/privkey.pem"
grep -q -- '--profile acme-bootstrap up' "$PPE_TEST_TRACE"
grep -q -- '--profile acme-bootstrap stop' "$PPE_TEST_TRACE"
grep -q 'exec -T nginx nginx -t' "$PPE_TEST_TRACE"
grep -q 'exec -T nginx nginx -s reload' "$PPE_TEST_TRACE"
echo "PASS: first certificate bootstrap installs matching material and validates/reloads HTTPS Nginx"

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
