#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
repo=$(cd "$(dirname "$0")/../../.." && pwd)
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
[[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || exit 2
docker info >/dev/null
project="ppe-sim-runner-$(date +%s)-$$"
image="$project:test"
root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-runner-startup.XXXXXX")
ready=no
dc() { docker compose --env-file /dev/null -p "$project" -f "$root/compose.json" "$@"; }
cleanup() {
    local status=$?
    trap - EXIT
    if [[ "$ready" = yes ]]; then
        if (( status != 0 )); then dc logs --no-color >&2 || true; fi
        dc down --timeout 5 || status=1
    fi
    if docker image inspect "$image" >/dev/null 2>&1; then docker image rm "$image" >/dev/null || status=1; fi
    rm -rf "$root"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
mkdir "$root/context"
cp "$repo/containers/python-runner/Dockerfile" "$repo/containers/python-runner/runner.py" "$root/context/"
chmod 0600 "$root/context/runner.py"
printf 'synthetic-runner-startup-token\n' > "$root/token"
chmod 0644 "$root/token"
docker build --tag "$image" "$root/context"
PPE_SECRETS_DIR="$root" PPE_TLS_DIR="$root/tls" PPE_ACME_DIR="$root/acme" \
    docker compose --env-file /dev/null -f "$repo/compose.production.yml" config --format json |
    jq --arg image "$image" --arg token "$root/token" --arg project "$project" '
        {
            services: {
                "python-runner": (.services["python-runner"] | del(.build, .depends_on) |
                    .image = $image | .restart = "no" | .networks = {isolated: {}} |
                    .environment.PYTHON_BROKER_NAMESPACE = $project |
                    .ports = [{target: 8090, host_ip: "127.0.0.1", protocol: "tcp"}] |
                    .healthcheck.interval = "1s")
            },
            secrets: {runner_token: {file: $token}},
            networks: {isolated: {}}
        }' > "$root/compose.json"
ready=yes
dc up -d --no-deps --wait --wait-timeout 45 python-runner
container=$(dc ps -q python-runner)
docker inspect "$container" | jq -e '.[0] |
    .Config.User == "65532:65532" and .HostConfig.ReadonlyRootfs == true and
    .State.Health.Status == "healthy" and .RestartCount == 0' >/dev/null
dc exec -T python-runner sh -ec '
    test "$(id -u):$(id -g)" = 65532:65532
    test -r /runner/runner.py
    test "$(stat -c %a /runner/runner.py)" = 644
    test "$(stat -c %a /runner)" = 755
'
port=$(docker inspect "$container" | jq -r '.[0].NetworkSettings.Ports["8090/tcp"][0].HostPort')
[[ "$port" =~ ^[0-9]+$ ]] || { echo "Runner test has no published loopback port." >&2; exit 1; }
curl -fsS --max-time 10 "http://127.0.0.1:$port/health" > "$root/health.json"
jq -e '.status == "ok"' "$root/health.json" >/dev/null
echo "PASS: restrictive 0600 build input becomes readable 0644; production UID65532 read-only runner starts healthy"
code=$(curl -sS --max-time 10 -o "$root/rejected.json" -w '%{http_code}' \
    -H 'Content-Type: application/json' --data '{"source":"print(1)","standardInput":""}' \
    "http://127.0.0.1:$port/execute")
[[ "$code" = 401 ]]
echo "PASS: healthy production runner still rejects unauthenticated execution"
dc stop python-runner >/dev/null
dc up -d --no-deps --wait --wait-timeout 45 python-runner
docker inspect "$container" | jq -e '.[0].State.Health.Status == "healthy"' >/dev/null
echo "PASS: nonroot runner restart remains healthy with the same production restrictions"
