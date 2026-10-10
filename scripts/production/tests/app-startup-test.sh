#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
repo=$(cd "$(dirname "$0")/../../.." && pwd)
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
[[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || exit 2
docker info >/dev/null
project="ppe-sim-app-$(date +%s)-$$"
root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-app-startup.XXXXXX")
ready=no
dc() { docker compose --env-file /dev/null -p "$project" -f "$root/compose.json" "$@"; }
cleanup() {
    local status=$?
    trap - EXIT
    if [[ "$ready" = yes ]]; then
        if (( status != 0 )); then dc logs --no-color >&2 || true; fi
        dc down --volumes --timeout 5 || status=1
    fi
    for image in app tools runner broker nginx; do
        if docker image inspect "$project-$image:test" >/dev/null 2>&1; then
            docker image rm "$project-$image:test" >/dev/null || status=1
        fi
    done
    rm -rf "$root"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
mkdir "$root/context" "$root/secrets" "$root/tls" "$root/acme"
git -C "$repo" archive HEAD build.gradle settings.gradle src docs/rubric containers/production |
    tar -xf - -C "$root/context"
cp "$repo/containers/production/Dockerfile" "$root/context/containers/production/Dockerfile"
find "$root/context" -type f -exec chmod 0600 {} \;
test -n "$(find "$root/context/containers/production/secrets-entrypoint.sh" -perm 0600 -print)"
docker build --target app --tag "$project-app:test" -f "$root/context/containers/production/Dockerfile" "$root/context"
docker build --target tools --tag "$project-tools:test" -f "$root/context/containers/production/Dockerfile" "$root/context"
docker run --rm --network none --user 10001:10001 --workdir /tmp --entrypoint sh \
    "$project-tools:test" -ec 'test -r /usr/local/bin/ppe-tools; test "$(stat -c %a /usr/local/bin/ppe-tools)" = 644'
for image in runner broker nginx; do
    case "$image" in runner) directory=python-runner;; broker) directory=docker-broker;; nginx) directory=nginx;; esac
    docker build --tag "$project-$image:test" "$repo/containers/$directory"
done
for name in db-password db-root-password db-migration-password runner-token gemini-api-key; do
    printf 'synthetic-startup-test-%s-not-real-secret\n' "$name" > "$root/secrets/$name"
done
openssl rand -base64 32 > "$root/secrets/student-credential-key"
chmod 0644 "$root/secrets/"*
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
    -keyout "$root/tls/privkey.pem" -out "$root/tls/fullchain.pem" \
    -subj /CN=student.ppeval.net \
    -addext 'subjectAltName=DNS:student.ppeval.net,DNS:teacher.ppeval.net' >/dev/null 2>&1
chmod 0755 "$root/tls" "$root/acme"
chmod 0444 "$root/tls/"*
subnet="10.233.$(( $$ % 200 + 20 ))"
PPE_RELEASE="$project" PPE_SECRETS_DIR="$root/secrets" PPE_TLS_DIR="$root/tls" \
PPE_ACME_DIR="$root/acme" PPE_BIND_ADDRESS=0.0.0.0 \
PPE_PROXY_SUBNET="$subnet.0/24" PPE_PROXY_IP="$subnet.10" \
PPE_TRUSTED_PROXY_REGEX="${subnet//./[.]}.10" \
    docker compose --env-file /dev/null -f "$repo/compose.production.yml" --profile tools config --format json |
    jq --arg project "$project" '
        del(.name) |
        .volumes |= with_entries(.value |= del(.name)) |
        .networks |= with_entries(.value |= del(.name)) |
        .services |= with_entries(select(.key | IN("app","db","migrate","python-runner","docker-broker","nginx"))) |
        .services.app.image = ($project+"-app:test") |
        .services.migrate.image = ($project+"-tools:test") |
        .services.db.image = "ppe-db:local" |
        .services["python-runner"].image = ($project+"-runner:test") |
        .services["docker-broker"].image = ($project+"-broker:test") |
        .services.nginx.image = ($project+"-nginx:test") |
        .services["python-runner"].environment.PYTHON_BROKER_NAMESPACE = $project |
        .services["docker-broker"].environment.PYTHON_BROKER_NAMESPACE = $project |
        .services.nginx.ports = [
            {target:80,host_ip:"0.0.0.0",protocol:"tcp"},
            {target:443,host_ip:"0.0.0.0",protocol:"tcp"}] |
        .services |= with_entries(.value |= del(.build))' > "$root/compose.json"
ready=yes
[[ "$(jq -r '.volumes.database.name // empty' "$root/compose.json")" = "" ]]
dc up -d --wait --wait-timeout 150 db docker-broker python-runner
printf "GRANT SUPER ON *.* TO 'ppe_migrate'@'%%';\n" | dc exec -T db sh /opt/ppe/db-admin.sh
dc run --rm --no-deps migrate gradle --offline --no-daemon flywayMigrate flywayValidate flywayInfo
printf "REVOKE SUPER ON *.* FROM 'ppe_migrate'@'%%';\n" | dc exec -T db sh /opt/ppe/db-admin.sh
dc up -d --wait --wait-timeout 180 app nginx
dc exec -T app sh -ec '
    test "$(id -u):$(id -g)" = 10001:10001
    for file in /usr/local/bin/ppe-app /usr/local/tomcat/conf/context.xml /usr/local/tomcat/conf/server.xml; do
        test -r "$file"; test "$(stat -c %a "$file")" = 644
        test "$(stat -c %u "$file")" = 0
    done
    for directory in /usr /usr/local /usr/local/bin /usr/local/tomcat/conf; do test -x "$directory"; done
    curl -fsS http://127.0.0.1:8080/health
'
docker inspect "$(dc ps -q app)" | jq -e '.[0] |
    .Config.User=="10001:10001" and .Config.Entrypoint==["sh","/usr/local/bin/ppe-app"] and
    .Config.Cmd==["catalina.sh","run"] and .State.Health.Status=="healthy" and
    .RestartCount==0 and ([.Mounts[].Destination]|index("/usr/local/bin/ppe-app")==null)' >/dev/null
dc exec -T python-runner sh -ec 'test "$(id -u)" = 65532; test -r /runner/runner.py'
for service in db docker-broker python-runner app nginx; do
    test "$(docker inspect "$(dc ps -q "$service")" --format '{{.State.Health.Status}}')" = healthy
done
echo "PASS: 0600 source input yields readable root-owned launcher/config; UID10001 app and UID65532 runner plus real DB/broker/nginx are healthy"
port=$(docker inspect "$(dc ps -q nginx)" | jq -r '.[0].NetworkSettings.Ports["443/tcp"][0].HostPort')
for role in student teacher; do
    curl --noproxy '*' --cacert "$root/tls/fullchain.pem" -fsS --max-time 10 \
        --resolve "$role.ppeval.net:$port:127.0.0.1" "https://$role.ppeval.net:$port/health" | jq -e '.status=="ok"' >/dev/null
    curl --noproxy '*' --cacert "$root/tls/fullchain.pem" -fsS --max-time 10 \
        --resolve "$role.ppeval.net:$port:127.0.0.1" \
        "https://$role.ppeval.net:$port/$role/account/login" > "$root/$role.html"
    grep -q 'name="password"' "$root/$role.html"
done
echo "PASS: real application health and both login pages are served through production TLS proxy"
code=$(dc exec -T app curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' --data '{"source":"print(1)","standardInput":""}' \
    http://python-runner:8090/execute)
test "$code" = 401
printf "CREATE TABLE ppe.startup_test (id INT PRIMARY KEY, value VARCHAR(32)); INSERT INTO ppe.startup_test VALUES (1,'synthetic-persisted');\n" |
    dc exec -T db sh /opt/ppe/db-admin.sh
before=$(dc ps -q db)
dc up -d --no-deps --force-recreate --wait --wait-timeout 45 nginx
test "$(dc ps -q db)" = "$before"
docker inspect "$(dc ps -q nginx)" | jq -e '.[0].HostConfig.PortBindings |
    .["80/tcp"][0].HostIp=="0.0.0.0" and .["443/tcp"][0].HostIp=="0.0.0.0"' >/dev/null
test "$(printf 'SELECT value FROM ppe.startup_test WHERE id=1;\n' |
    dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names)" = synthetic-persisted
echo "PASS: application startup and nginx recreation preserve nonroot restrictions, public bind and existing DB"
echo "PASS: unauthenticated runner execution remains denied and synthetic DB row persists across proxy recreation"
