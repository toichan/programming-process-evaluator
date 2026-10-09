#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

repo=$(cd "$(dirname "$0")/../../.." && pwd)
for tool in docker jq openssl curl; do
    command -v "$tool" >/dev/null || { echo "Required test tool is missing: $tool" >&2; exit 2; }
done
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
[[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || {
    echo "Nginx regression tests require a local Unix Docker endpoint." >&2
    exit 2
}
docker info >/dev/null

project="ppe-sim-nginx-$(date +%s)-$$"
image="$project:test"
if docker image inspect "$image" >/dev/null 2>&1; then
    echo "Test image already exists; refusing to overwrite it." >&2
    exit 2
fi
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-nginx-startup.XXXXXX")
compose_ready=0
image_created=0
dc() { docker compose --env-file /dev/null -p "$project" -f "$test_root/compose.json" --profile acme-bootstrap "$@"; }
cleanup() {
    local status=$?
    trap - EXIT
    if (( compose_ready )); then
        if (( status != 0 )); then dc logs --no-color >&2 || true; fi
        if ! dc down --timeout 5; then
            echo "Failed to remove test project: $project" >&2
            status=1
        fi
    fi
    if (( image_created )); then
        if ! docker image rm "$image" >/dev/null; then
            echo "Failed to remove test image: $image" >&2
            status=1
        fi
    fi
    rm -rf "$test_root"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

mkdir -p "$test_root/tls" "$test_root/acme/.well-known/acme-challenge"
chmod 0755 "$test_root/acme/.well-known" "$test_root/acme/.well-known/acme-challenge"
printf 'synthetic-acme-challenge\n' > "$test_root/acme/.well-known/acme-challenge/startup-test"
chmod 0644 "$test_root/acme/.well-known/acme-challenge/startup-test"
openssl req -x509 -newkey rsa:2048 -nodes -days 3 \
    -keyout "$test_root/dummy.key" -out "$test_root/dummy.crt" \
    -subj /CN=student.ppeval.net \
    -addext 'subjectAltName=DNS:student.ppeval.net,DNS:teacher.ppeval.net' >/dev/null 2>&1
cat > "$test_root/upstream.conf" <<'NGINX'
pid /tmp/nginx.pid;
error_log /dev/stderr warn;
events {}
http {
    access_log off;
    client_body_temp_path /tmp/client_body;
    proxy_temp_path /tmp/proxy;
    fastcgi_temp_path /tmp/fastcgi;
    uwsgi_temp_path /tmp/uwsgi;
    scgi_temp_path /tmp/scgi;
    server {
        listen 8080;
        location / { return 200 "synthetic-upstream\n"; }
    }
}
NGINX
chmod 0444 "$test_root/upstream.conf"

# Resolve the real service definitions without reading the repository's .env or mounting secrets.
export PPE_RELEASE="$project" PPE_BIND_ADDRESS=127.0.0.1
export PPE_TLS_DIR="$test_root/tls" PPE_ACME_DIR="$test_root/acme" PPE_SECRETS_DIR="$test_root/unused-secrets"
docker compose --env-file /dev/null -p "$project" -f "$repo/compose.production.yml" \
    --profile acme-bootstrap config --format json |
    jq --arg image "$image" --arg upstream "$test_root/upstream.conf" '
        {
            services: {
                nginx: (.services.nginx | del(.build, .depends_on) |
                    .image = $image | .restart = "no" | .networks = {edge: {}} |
                    .ports = [
                        {target: 80, host_ip: "127.0.0.1", protocol: "tcp"},
                        {target: 443, host_ip: "127.0.0.1", protocol: "tcp"}
                    ]),
                "acme-bootstrap": (.services["acme-bootstrap"] | del(.build) |
                    .image = $image | .networks = {edge: {}} |
                    .ports = [{target: 80, host_ip: "127.0.0.1", protocol: "tcp"}]),
                upstream: {
                    image: $image,
                    command: ["-c", "/test/upstream.conf", "-g", "daemon off;"],
                    volumes: [{type: "bind", source: $upstream, target: "/test/upstream.conf", read_only: true}],
                    networks: {edge: {aliases: ["app"]}},
                    read_only: true,
                    tmpfs: ["/tmp:size=8m,uid=101,gid=101,mode=700"],
                    cap_drop: ["ALL"],
                    security_opt: ["no-new-privileges:true"]
                }
            },
            networks: {edge: {}}
        }' > "$test_root/compose.json"
compose_ready=1
dc config --quiet
image_created=1
docker build --tag "$image" "$repo/containers/nginx"
docker image inspect "$image" |
    jq -e '.[0].Config | .Entrypoint == ["nginx"] and .Cmd == ["-g", "daemon off;"]' >/dev/null

assert_command() {
    local service=$1 expected=$2 container
    container=$(dc ps -a -q "$service")
    [[ -n "$container" ]]
    docker inspect "$container" |
        jq -e --argjson expected "$expected" '.[0] | .Path == "nginx" and .Args == $expected' >/dev/null
}

mkdir "$test_root/bin"
native_docker=$(command -v docker)
cat > "$test_root/bin/docker" <<'DOCKER'
#!/bin/sh
printf '%s\n' "$*" >> "$PPE_TEST_DOCKER_TRACE"
exec "$PPE_TEST_REAL_DOCKER" "$@"
DOCKER
cat > "$test_root/bin/certbot" <<'CERTBOT'
#!/bin/sh
printf 'unexpected certificate request\n' > "$PPE_TEST_CERTBOT_CALLED"
exit 99
CERTBOT
chmod 0700 "$test_root/bin/docker" "$test_root/bin/certbot"
jq '.services["acme-bootstrap"].command = ["nginx", "-c", "/etc/nginx/acme-bootstrap.conf", "-g", "daemon off;"]' \
    "$test_root/compose.json" > "$test_root/broken-compose.json"
status=0
PATH="$test_root/bin:$PATH" PPE_PROJECT="$project" PPE_COMPOSE_FILE="$test_root/broken-compose.json" \
    PPE_OPERATION_APPROVAL=local-test PPE_ACME_TEST_MODE=mock PPE_TLS_BOOTSTRAP_APPROVED=yes \
    PPE_ACME_EMAIL=operator@example.invalid PPE_CERTBOT_CONFIG_DIR="$test_root/certbot-config" \
    PPE_CERTBOT_WORK_DIR="$test_root/certbot-work" PPE_CERTBOT_LOG_DIR="$test_root/certbot-log" \
    PPE_TEST_CERTBOT_CALLED="$test_root/certbot-called" PPE_TEST_REAL_DOCKER="$native_docker" \
    PPE_TEST_DOCKER_TRACE="$test_root/docker-trace" \
    bash "$repo/scripts/production/tls-bootstrap.sh" > "$test_root/failed-startup.log" 2>&1 || status=$?
[[ "$status" != 0 && ! -e "$test_root/certbot-called" ]]
grep -q -- '--profile acme-bootstrap stop acme-bootstrap$' "$test_root/docker-trace"
container=$(dc ps -a -q acme-bootstrap)
[[ -n "$container" ]]
[[ "$(docker inspect --format '{{.State.Running}}' "$container")" = false ]]
dc logs --no-color acme-bootstrap > "$test_root/failed-container.log"
grep -q 'invalid option: "nginx"' "$test_root/failed-container.log"
[[ ! -e "$test_root/tls/fullchain.pem" && ! -e "$test_root/tls/privkey.pem" ]]
[[ -z "$(dc ps -a -q nginx)" ]]
echo "PASS: real duplicate-command failure stops bootstrap, skips Certbot, preserves TLS absence, and does not start normal Nginx"

[[ -z "$(find "$test_root/acme" -prune ! -perm 0755 -print)" ]]
for directory in "$test_root/tls" "$test_root/certbot-config" "$test_root/certbot-work" "$test_root/certbot-log" "$test_root"; do
    [[ -z "$(find "$directory" -prune ! -perm 0700 -print)" ]]
done
dc up -d --no-deps --wait --wait-timeout 30 acme-bootstrap
assert_command acme-bootstrap '["-c", "/etc/nginx/acme-bootstrap.conf", "-g", "daemon off;"]'
dc exec -T acme-bootstrap nginx -t -c /etc/nginx/acme-bootstrap.conf
dc exec -T acme-bootstrap sh -ec '
    identity=$(id -u):$(id -g)
    test "$identity" = 101:101 || { echo "Unexpected Nginx identity: $identity" >&2; exit 1; }
    for directory in /var/www/acme /var/www/acme/.well-known /var/www/acme/.well-known/acme-challenge; do
        mode=$(stat -c %a "$directory")
        test -x "$directory" && test "$mode" = 755 || {
            echo "Unexpected ACME directory access: $directory (mode $mode)" >&2
            exit 1
        }
        if (printf "must-not-write\n" > "$directory/.ppe-write-test") 2>/dev/null; then
            echo "ACME directory unexpectedly permits writes: $directory" >&2
            exit 1
        fi
    done
    file=/var/www/acme/.well-known/acme-challenge/startup-test
    mode=$(stat -c %a "$file")
    test -r "$file" && test "$mode" = 644 &&
        test "$(cat "$file")" = synthetic-acme-challenge || {
            echo "Unexpected ACME challenge access or contents (mode $mode)" >&2
            exit 1
        }
    if (printf "must-not-write\n" >> "$file") 2>/dev/null; then
        echo "ACME challenge unexpectedly permits writes" >&2
        exit 1
    fi
'
http_address=$(dc port acme-bootstrap 80)
for role in student teacher; do
    response=$(curl --noproxy '*' -fsS --max-time 5 -H "Host: $role.ppeval.net" \
        "http://$http_address/.well-known/acme-challenge/startup-test")
    [[ "$response" = synthetic-acme-challenge ]]
    [[ "$(curl --noproxy '*' -sS --max-time 5 -o /dev/null -w '%{http_code}' \
        -H "Host: $role.ppeval.net" "http://$http_address/")" = 404 ]]
done
echo "PASS: bootstrap-prepared permissions allow UID/GID 101:101 to read challenges and serve both hosts without write access"
dc stop acme-bootstrap

cp "$test_root/dummy.crt" "$test_root/tls/fullchain.pem"
cp "$test_root/dummy.key" "$test_root/tls/privkey.pem"
chmod 0755 "$test_root/tls"
chmod 0444 "$test_root/tls/fullchain.pem" "$test_root/tls/privkey.pem"
dc up -d --no-deps upstream
dc up -d --no-deps --wait --wait-timeout 30 nginx
assert_command nginx '["-g", "daemon off;"]'
dc exec -T nginx nginx -t
dc exec -T nginx nginx -s reload
http_address=$(dc port nginx 80)
https_address=$(dc port nginx 443)
https_port=${https_address##*:}
for role in student teacher; do
    [[ "$(curl --noproxy '*' -sS --max-time 5 -o /dev/null -w '%{http_code}' \
        -H "Host: $role.ppeval.net" "http://$http_address/")" = 308 ]]
    response=$(curl --noproxy '*' -kfsS --max-time 5 \
        --resolve "$role.ppeval.net:$https_port:127.0.0.1" "https://$role.ppeval.net:$https_port/health")
    [[ "$response" = synthetic-upstream ]]
done
echo "PASS: real normal Nginx retains its default command, becomes healthy, redirects HTTP, proxies HTTPS, and reloads"

lineage="$test_root/certbot-config/live/ppeval"
mkdir -p "$lineage"
openssl req -x509 -newkey rsa:2048 -nodes -days 3 \
    -keyout "$lineage/privkey.pem" -out "$lineage/fullchain.pem" \
    -subj /CN=student.ppeval.net \
    -addext 'subjectAltName=DNS:student.ppeval.net,DNS:teacher.ppeval.net' >/dev/null 2>&1
before=$(dc ps -q nginx)
PPE_PROJECT="$project" PPE_COMPOSE_FILE="$test_root/compose.json" \
    PPE_CERTBOT_CONFIG_DIR="$test_root/certbot-config" RENEWED_LINEAGE="$lineage" \
    RENEWED_DOMAINS='student.ppeval.net teacher.ppeval.net' \
    bash "$repo/scripts/production/tls-renew-hook.sh"
[[ "$before" = "$(dc ps -q nginx)" ]]
cmp -s "$lineage/fullchain.pem" "$test_root/tls/fullchain.pem"
cmp -s "$lineage/privkey.pem" "$test_root/tls/privkey.pem"
[[ -z "$(find "$test_root/tls/privkey.pem" -prune ! -perm 0444 -print)" ]]
for role in student teacher; do
    verified=0
    for attempt in 1 2 3 4 5; do
        if response=$(curl --noproxy '*' --cacert "$lineage/fullchain.pem" -fsS --max-time 5 \
            --resolve "$role.ppeval.net:$https_port:127.0.0.1" \
            "https://$role.ppeval.net:$https_port/health" 2>/dev/null); then
            [[ "$response" = synthetic-upstream ]]
            verified=1
            break
        fi
        sleep 1
    done
    [[ "$verified" = 1 ]] || { echo "Renewed synthetic certificate was not served." >&2; exit 1; }
done
backup_count=0
for backup in "$test_root"/.ppe-tls-before-renew.*; do
    [[ -d "$backup" ]] || continue
    [[ -z "$(find "$backup" -prune ! -perm 0700 -print)" ]]
    cmp -s "$backup/fullchain.pem" "$test_root/dummy.crt"
    cmp -s "$backup/privkey.pem" "$test_root/dummy.key"
    backup_count=$((backup_count + 1))
done
[[ "$backup_count" = 1 ]]
echo "PASS: real deploy hook serves the renewed certificate on both hosts without recreating Nginx and retains the private old pair"
