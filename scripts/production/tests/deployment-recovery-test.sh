#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$scripts/../.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-deploy-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
chmod 0700 "$test_root"
trap 'echo "Deployment regression failed at line $LINENO." >&2; for log in "$test_root/"*.log; do [[ ! -f "$log" ]] || tail -n 8 "$log" >&2; done' ERR
release="$test_root/releases/1111111111111111111111111111111111111111"
mkdir -p "$release/source/src/main/resources/db/migration" "$release/source/scripts/production/tests" \
    "$release/source/containers/production"
cp "$repo/compose.production.yml" "$release/source/compose.production.yml"
for recipe in Dockerfile.db init-db.sh db-admin.sh; do
    cp "$repo/containers/production/$recipe" "$release/source/containers/production/$recipe"
done
cp "$scripts/"*.sh "$release/source/scripts/production/"
cp "$scripts/tests/"*.sh "$release/source/scripts/production/tests/"
for helper in ensure-migration-privileges.sh migrate.sh; do
    printf '#!/usr/bin/env bash\necho "Immutable release helper must not run during tooling recovery." >&2\nexit 88\n' \
        > "$release/source/scripts/production/$helper"
done
for version in $(seq 1 23); do
    printf '%s\n' '-- test-only migration inventory' \
        > "$release/source/src/main/resources/db/migration/V${version}__test.sql"
done
printf '%s\n' '1111111111111111111111111111111111111111' > "$release/commit"
printf '%s\n' '1111111111111111111111111111111111111111' > "$release/READY"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    printf 'sha256:%064d\n' 1 > "$release/$image.id"
done
new_release="$test_root/releases/2222222222222222222222222222222222222222"
cp -R "$release" "$new_release"
printf '%s\n' '2222222222222222222222222222222222222222' > "$new_release/commit"
cp "$new_release/commit" "$new_release/READY"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    printf 'sha256:%064d\n' 2 > "$new_release/$image.id"
done
previous_release="$test_root/releases/3333333333333333333333333333333333333333"
cp -R "$release" "$previous_release"
printf '%s\n' '3333333333333333333333333333333333333333' > "$previous_release/commit"
cp "$previous_release/commit" "$previous_release/READY"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    printf 'sha256:%064d\n' 3 > "$previous_release/$image.id"
done
source "$scripts/source-manifest.sh"
for fixture in "$release" "$new_release" "$previous_release"; do
    generate_source_manifest "$fixture/source" > "$fixture/source.sha256"
done
bad_manifest_release="$test_root/releases/4444444444444444444444444444444444444444"
cp -R "$release" "$bad_manifest_release"
printf '%s\n' '4444444444444444444444444444444444444444' > "$bad_manifest_release/commit"
printf '%s\n' 'ffffffffffffffffffffffffffffffffffffffff' > "$bad_manifest_release/READY"
bad_recipe_release="$test_root/releases/5555555555555555555555555555555555555555"
cp -R "$release" "$bad_recipe_release"
printf '%s\n' '5555555555555555555555555555555555555555' > "$bad_recipe_release/commit"
cp "$bad_recipe_release/commit" "$bad_recipe_release/READY"
printf '%s\n' '# pinned-only DB recipe drift' >> "$bad_recipe_release/source/containers/production/db-admin.sh"
find "$release" -type f -exec shasum -a 256 '{}' \; > "$test_root/release-before.sha256"

mkdir -p "$test_root/bin"
test_bash=$(command -v bash)
export PPE_TEST_REAL_MV="$(command -v mv)"
test_real_flock=yes
if ! command -v flock >/dev/null 2>&1; then
    test_real_flock=no
    cat > "$test_root/bin/flock" <<'FLOCK'
#!/bin/sh
[ "$1" = -n ] && [ "$2" = 9 ] || exit 2
exit 0
FLOCK
    chmod 0700 "$test_root/bin/flock"
fi
cat > "$test_root/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
all="$*"
printf '%s\n' "$all" >> "$PPE_FAKE_DOCKER_TRACE"
if [[ "$all" = compose* ]]; then
    [[ "${PPE_BIND_ADDRESS:-}" = 127.0.0.1 && "${PPE_HTTP_PORT:-}" = 18180 \
        && "${PPE_HTTPS_PORT:-}" = 18443 ]] || {
        echo "Compose lost the reviewed TLS bind/port settings." >&2; exit 89;
    }
fi
case "$all" in
    *"up -d --no-deps --wait --wait-timeout 100 python-runner app")
        [[ "${PPE_FAKE_OLD_APP_FAIL:-no}" != yes || "$PPE_RELEASE" != 3333333333333333333333333333333333333333 ]] || exit 1
        ;;
    "context inspect "*) printf 'unix:///var/run/docker.sock\n'; exit 0 ;;
    "image inspect "*)
        image=${*: -1}; name=${image%%:*}; image_release=${image##*:}
        image_release_dir="$(dirname "$PPE_FAKE_RELEASE")/$image_release"
        cat "$image_release_dir/$name.id"
        exit 0
        ;;
    "volume inspect "*)
        if [[ "${PPE_FAKE_DB_CONDITION:-valid}" = missing-volume ]]; then exit 1; fi
        volume_project=$PPE_PROJECT
        [[ "${PPE_FAKE_DB_CONDITION:-valid}" != wrong-volume-label ]] || volume_project=ppe-other
        created=2026-10-08T08:32:49Z
        [[ "${PPE_FAKE_DB_CONDITION:-valid}" != replaced-volume ]] || created=2026-10-09T00:00:00Z
        printf '%s_database|local|%s|database|%s\n' "$PPE_PROJECT" "$volume_project" "$created"
        exit 0
        ;;
    "inspect "*)
        if [[ "$all" = *'{{.Id}}|'* ]]; then
            printf '%064d|true|healthy|%s|db|volume:%s_database:true;|2026-10-08T08:32:49Z\n' 1 "$PPE_PROJECT" "$PPE_PROJECT"
            exit 0
        fi
        if [[ "$all" = *'{{.Image}}'* && "$all" != *'{{.State.Running}}'* ]]; then
            [[ "${PPE_FAKE_RUNNING_IMAGE:-valid}" != wrong ]] || { echo sha256:wrong; exit 0; }
            selected_release=$PPE_RELEASE
            [[ "${*: -1}" != "$(printf '%064d' 1)" ]] || selected_release=${PPE_FAKE_DB_RELEASE:-${PPE_FAKE_RELEASE##*/}}
            printf 'sha256:%064d\n' "${selected_release:0:1}"; exit 0
        fi
        if [[ "$all" = *'{{.State.Running}}|{{if .State.Health}}'* && "$all" != *'{{.Image}}'* ]]; then
            printf 'true|healthy\n'; exit 0
        fi
        running=true; health=healthy; db_project=$PPE_PROJECT; service=db; oneoff=False
        image_release_dir="$(dirname "$PPE_FAKE_RELEASE")/${PPE_FAKE_DB_RELEASE:-${PPE_FAKE_RELEASE##*/}}"
        image=$(cat "$image_release_dir/ppe-db.id")
        config_hash=$(printf '%064d' 1)
        mount="volume:${PPE_PROJECT}_database:true;"
        case "${PPE_FAKE_DB_CONDITION:-valid}" in
            stopped) running=false ;;
            unhealthy) health=unhealthy ;;
            no-healthcheck) health="" ;;
            wrong-image) image=sha256:wrong ;;
            wrong-project) db_project=ppe-other ;;
            wrong-service) service=app ;;
            oneoff) oneoff=True ;;
            config-drift) config_hash=$(printf '%064d' 2) ;;
            wrong-volume) mount="volume:other_database:true;" ;;
            bind-mount) mount="bind::true;" ;;
            readonly-volume) mount="volume:${PPE_PROJECT}_database:false;" ;;
        esac
        printf '%s|%s|%s|%s|%s|%s|%s|%s\n' \
            "$running" "$health" "$image" "$db_project" "$service" "$oneoff" "$config_hash" "$mount"
        exit 0
        ;;
esac
if [[ "$all" = "run --rm -i --network none"* ]]; then
    cat >/dev/null
    printf 'synthetic-encrypted-payload\n'
    exit 0
fi
if [[ "$all" = *"exec -T db sh /opt/ppe/db-admin.sh"* ]]; then
    if [[ "$all" = *'--dump' ]]; then printf 'synthetic-not-real-SQL\n'; exit 0; fi
    query=$(cat)
    case "$query" in
        *"engine IS NOT NULL"*) printf '0\n' ;;
        *"FROM information_schema.tables"*)
            if [[ -f "$PPE_FAKE_DB_STATE" ]]; then printf '1\n'; else printf '0\n'; fi
            ;;
        *"FROM ppe.flyway_schema_history"*)
            if [[ -f "$PPE_FAKE_DB_STATE" ]]; then
                read -r applied failed < "$PPE_FAKE_DB_STATE"
                if [[ "$query" = *"SELECT CONCAT("* ]]; then
                    for version in $(seq 1 "$applied"); do printf '%s|V%s__test.sql|123|1\n' "$version" "$version"; done
                    [[ "$failed" = 0 ]] || printf '24|V24__test.sql|123|0\n'
                else
                    printf '%s %s %s\n' "$applied" "$failed" "$applied"
                fi
            else
                printf '0 0 0\n'
            fi
            ;;
        *"SHOW GRANTS FOR"*) printf "USAGE ON *.*\n" ;;
    esac
    exit 0
fi
case "$all" in
    *"ps --all -q db")
        case "${PPE_FAKE_DB_CONDITION:-valid}" in
            missing) ;;
            multiple) printf '%064d\n%064d\n' 1 2 ;;
            replaced-container) printf '%064d\n' 2 ;;
            *) printf '%064d\n' 1 ;;
        esac
        ;;
    *"config --hash db")
        source_dir=$(dirname "$PPE_COMPOSE_FILE")
        if [[ -n "${PPE_DB_RELEASE:-}" && "$PPE_DB_RELEASE" != "$PPE_RELEASE" ]]; then
            source_dir="$(dirname "$(dirname "$source_dir")")/$PPE_DB_RELEASE/source"
        fi
        [[ "${PPE_DB_SOURCE_DIR:-}" = "$source_dir" ]] || {
            echo "DB build context was not derived from the verified release." >&2
            exit 95
        }
        printf 'db %064d\n' 1
        ;;
    *"run --rm --no-deps migrate gradle"*)
        printf 'Flyway validation successful.\n'
        ;;
    *"run --rm --no-deps migrate"*)
        if [[ "${PPE_FAKE_MIGRATION_MODE:-success}" = partial-unrecorded ]]; then
            echo "Injected DDL failure without a Flyway record." >&2
            exit 1
        fi
        if [[ "${PPE_FAKE_MIGRATION_MODE:-success}" = partial ]]; then
            printf '3 1\n' > "$PPE_FAKE_DB_STATE"
            echo "Injected partial DDL and failed Flyway record." >&2
            exit 1
        fi
        printf '%s 0\n' "$PPE_FAKE_TARGET_MIGRATIONS" > "$PPE_FAKE_DB_STATE"
        ;;
    *"exec -T nginx nginx -t"*) ;;
    *"ps --status running -q nginx"*) ;;
    *"ps --all -q "*) printf '%064d\n' 2 ;;
    "compose "*) ;;
esac
exit 0
DOCKER
cat > "$test_root/bin/curl" <<'CURL'
#!/bin/sh
case " $* " in
    *" -w "*) printf '200' ;;
    *) printf '{"status":"ok"}' ;;
esac
CURL
chmod 0700 "$test_root/bin/docker" "$test_root/bin/curl"
cat > "$test_root/bin/mv" <<'MV'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${PPE_FAKE_FAIL_TLS_INSTALL:-no}" = yes && "$*" = *'.ppe-operation-tls.'* \
    && "${*: -1}" = "${PPE_TLS_OPERATION_ENV:-}" ]]; then
    echo "Injected TLS operation environment rename failure." >&2
    exit 1
fi
exec "$PPE_TEST_REAL_MV" "$@"
MV
chmod 0700 "$test_root/bin/mv"
dash=$(command -v dash) || { echo "dash is required for deployment recovery tests." >&2; exit 2; }
cat > "$test_root/bin/sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ -f "${1:-}" ]] && head -n 1 "$1" | grep -q 'bash'; then
    echo "Bash script incorrectly invoked through sh: $1" >&2
    exit 92
fi
exec "$PPE_TEST_DASH" "$@"
SH
chmod 0700 "$test_root/bin/sh"
export PPE_TEST_DASH="$dash"
printf '#!%s\n' "$test_bash" > "$test_root/bin/bash"
cat >> "$test_root/bin/bash" <<'BASH'
#!/usr/bin/env bash
set -Eeuo pipefail
case "${1:-}" in
    "$PPE_TEST_BACKUP_SCRIPT")
        if [[ "${PPE_TEST_USE_REAL_BACKUP:-no}" = yes ]]; then
            printf 'backup\n' >> "$PPE_FAKE_DEPLOY_TRACE"
            exec "$PPE_TEST_BASH" "$@"
        fi
        [[ "${PPE_BACKUP_REQUIRE_REMOTE:-}" = yes \
            && "${PPE_BACKUP_RECEIPT_FILE:-}" = "$PPE_STATE_DIR/deployment-backup.receipt" \
            && "${PPE_BACKUP_RELEASE:-}" =~ ^[0-9a-f]{40}$ \
            && "${PPE_BACKUP_BASELINE:-}" =~ ^[0-9]+$ ]] || {
            echo "Backup did not receive deployment provenance and the inherited lock." >&2
            exit 93
        }
        perl -e 'open my $fd, ">&=9" or exit 1; my @fd = stat($fd); my @path = stat($ARGV[0]); exit !(@fd && @path && $fd[0] == $path[0] && $fd[1] == $path[1])' \
            "$PPE_STATE_DIR/database-operation.lock" || {
            echo "Deployment did not inherit FD9 for the shared database lock." >&2
            exit 93
        }
        printf '%s|%s\n' "$PPE_BACKUP_RELEASE" "$PPE_BACKUP_BASELINE" > "$PPE_BACKUP_RECEIPT_FILE"
        printf 'backup\n' >> "$PPE_FAKE_DEPLOY_TRACE"
        exit 0
        ;;
    "$PPE_TEST_RECEIPT_VALIDATOR")
        if [[ "${PPE_TEST_USE_REAL_BACKUP:-no}" = yes ]]; then
            printf 'receipt-validated\n' >> "$PPE_FAKE_DEPLOY_TRACE"
            exec "$PPE_TEST_BASH" "$@"
        fi
        [[ "$2" = "$PPE_BACKUP_RECEIPT_FILE" && -f "$2" \
            && "$(cat "$2")" = "$PPE_BACKUP_RELEASE|$PPE_BACKUP_BASELINE" ]] || {
            echo "Missing or mismatched deployment backup receipt." >&2
            exit 94
        }
        printf 'receipt-validated\n' >> "$PPE_FAKE_DEPLOY_TRACE"
        exit 0
        ;;
esac
exec "$PPE_TEST_BASH" "$@"
BASH
chmod 0700 "$test_root/bin/bash"
export PPE_TEST_BASH="$test_bash"
export PPE_TEST_REAL_FLOCK="$test_real_flock"
export PATH="$test_root/bin:$PATH"
export PPE_FAKE_DOCKER_TRACE="$test_root/docker-trace"
export PPE_FAKE_DEPLOY_TRACE="$test_root/deploy-trace"
export PPE_TEST_BACKUP_SCRIPT="$scripts/backup.sh"
export PPE_TEST_RECEIPT_VALIDATOR="$scripts/verify-backup-receipt.sh"
cat > "$test_root/bin/aws" <<'AWS'
#!/usr/bin/env bash
case "$*" in
    *'/s3-bucket '*) echo ppe-test-backup ;;
    *'/s3-prefix '*) echo production/mysql ;;
    *'/age-recipient '*) printf 'age1'; printf 'a%.0s' {1..58}; printf '\n' ;;
    *'/kms-key-arn '*) echo arn:aws:kms:ap-northeast-1:123456789012:key/11111111-1111-1111-1111-111111111111 ;;
    *'/sns-topic-arn '*) echo arn:aws:sns:ap-northeast-1:123456789012:ppe-backup ;;
    *) exit 1 ;;
esac
AWS
chmod 0700 "$test_root/bin/aws"
export PPE_FAKE_RELEASE="$release"
export PPE_FAKE_TARGET_MIGRATIONS=23
export PPE_FAKE_MIGRATION_MODE=success

run_deploy() {
    local project=$1 point=${2:-} mode=${3:-initial} policy=${4:-initial} target_release=${5:-$release}
    export PPE_PROJECT=$project
    export PPE_OPERATION_APPROVAL=local-test
    export PPE_MAINTENANCE_APPROVED=yes
    export PPE_SCHEMA_POLICY=$policy
    export PPE_STATE_DIR="$test_root/$project/state"
    export PPE_OPERATION_DIR="$PPE_STATE_DIR"
    export PPE_SECRETS_DIR="$test_root/secrets"
    export PPE_TLS_DIR="$test_root/tls"
    export PPE_ACME_DIR="$test_root/acme"
    export PPE_PROXY_SUBNET=10.239.10.0/24
    export PPE_PROXY_IP=10.239.10.10
    export PPE_TRUSTED_PROXY_REGEX='10[.]239[.]10[.]10'
    export PPE_BIND_ADDRESS=127.0.0.1
    export PPE_HTTP_PORT=18180
    export PPE_HTTPS_PORT=18443
    export PPE_SMOKE_CA_FILE="$test_root/dummy-ca.pem"
    export PPE_COMPOSE_FILE="$release/source/compose.production.yml"
    export PPE_DB_SOURCE_DIR="$test_root/untrusted-caller-source"
    export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
    export PPE_DEPLOYMENT_TEST_FAIL_AT=$point
    export PPE_SOURCE_MANIFEST_SHA256="$(source_hash "$target_release/source.sha256")"
    export PPE_TLS_OPERATION_WRAPPER="$scripts/renew-tls.sh"
    export PPE_PREVIOUS_SOURCE_MANIFEST_SHA256="$(source_hash "$previous_release/source.sha256")"
    export PPE_BACKUP_PARAMETER_PREFIX=/programming-process-evaluator/prod/backup
    export PPE_BACKUP_DIR="$test_root/backups" PPE_BACKUP_MIN_FREE_MIB=1
    export PPE_RECOVERY_DB_CONTAINER_ID
    PPE_RECOVERY_DB_CONTAINER_ID=$(printf '%064d' 1)
    export PPE_RECOVERY_DB_VOLUME_CREATED_AT=2026-10-08T08:32:49Z
    if [[ "${PPE_FAKE_DB_CONDITION:-valid}" = missing-pins ]]; then
        unset PPE_RECOVERY_DB_CONTAINER_ID PPE_RECOVERY_DB_VOLUME_CREATED_AT
    fi
    mkdir -p "$PPE_STATE_DIR" "$PPE_SECRETS_DIR" "$PPE_TLS_DIR" "$PPE_ACME_DIR" \
        "$test_root/certbot-config" "$test_root/certbot-work" "$test_root/certbot-logs" "$test_root/backups"
    : > "$PPE_SMOKE_CA_FILE"
    chmod 0700 "$PPE_STATE_DIR"
    printf 'age1%s\n' "$(printf 'a%.0s' {1..58})" > "$test_root/recipient"
    export PPE_BACKUP_RECOVERY_REFERENCE="$PPE_STATE_DIR/recovery-reference"
    if [[ ! -f "$PPE_BACKUP_RECOVERY_REFERENCE" ]]; then
        printf 'age_recipient=age1%s\nrestore_verified=yes\ncustody_reference=synthetic-offline-custody\n' \
            "$(printf 'a%.0s' {1..58})" > "$PPE_BACKUP_RECOVERY_REFERENCE"
    fi
    export PPE_BACKUP_OPERATION_ENV="$PPE_STATE_DIR/backup-operation.env"
    export PPE_TLS_OPERATION_ENV="$PPE_STATE_DIR/tls-operation.env"
    for environment in "$PPE_BACKUP_OPERATION_ENV" "$PPE_TLS_OPERATION_ENV"; do
        if [[ ! -f "$environment" ]]; then
            printf 'PPE_PROJECT=%s\nPPE_RELEASE=%s\nPPE_DB_RELEASE=%s\nPPE_DB_SOURCE_DIR=%s\nPPE_COMPOSE_FILE=%s\nPPE_SECRETS_DIR=%s\nPPE_TLS_DIR=%s\nPPE_ACME_DIR=%s\n' \
                "$PPE_PROJECT" "${previous_release##*/}" "${release##*/}" "$release/source" \
                "$previous_release/source/compose.production.yml" "$PPE_SECRETS_DIR" "$PPE_TLS_DIR" "$PPE_ACME_DIR" > "$environment"
            if [[ "$environment" = "$PPE_BACKUP_OPERATION_ENV" ]]; then
                printf 'PPE_BACKUP_IMAGE=ppe-backup:%s\n' "${previous_release##*/}" >> "$environment"
                printf 'PPE_STATE_DIR=%s\nPPE_BACKUP_DIR=%s\nPPE_BACKUP_PARAMETER_PREFIX=/programming-process-evaluator/prod/backup\nAWS_REGION=ap-northeast-1\n' \
                    "$PPE_STATE_DIR" "$test_root/backups" >> "$environment"
                printf 'PPE_BACKUP_RECIPIENT_FILE=%s\n' "$test_root/recipient" >> "$environment"
            else
                printf 'PPE_CERTBOT_CONFIG_DIR=%s\nPPE_CERTBOT_WORK_DIR=%s\nPPE_CERTBOT_LOG_DIR=%s\n' \
                    "$test_root/certbot-config" "$test_root/certbot-work" "$test_root/certbot-logs" >> "$environment"
                printf 'PPE_BIND_ADDRESS=127.0.0.1\nPPE_HTTP_PORT=18180\nPPE_HTTPS_PORT=18443\n' >> "$environment"
            fi
        fi
    done
    unset PPE_BIND_ADDRESS PPE_HTTP_PORT PPE_HTTPS_PORT
    if [[ "${PPE_TEST_LOCKED:-no}" = yes ]]; then
        perl -e '
            use Fcntl qw(LOCK_EX);
            my ($lock_path, $log_path, @command) = @ARGV;
            open my $lock, ">>", $lock_path or die $!;
            flock($lock, LOCK_EX) or die $!;
            my $pid = fork();
            die $! unless defined $pid;
            if (!$pid) {
                close $lock;
                open STDOUT, ">", $log_path or die $!;
                open STDERR, ">&STDOUT" or die $!;
                exec @command;
                die $!;
            }
            waitpid($pid, 0);
            exit($? >> 8);
        ' "$PPE_STATE_DIR/database-operation.lock" "$test_root/lock-contention.log" \
            "$PPE_TEST_BASH" "$scripts/deploy-release.sh" "$mode" "$target_release"
    else
        bash "$scripts/deploy-release.sh" "$mode" "$target_release"
    fi
}

if [[ "$PPE_TEST_REAL_FLOCK" = yes ]]; then
    project=ppe-sim-shared-lock
    : > "$PPE_FAKE_DOCKER_TRACE"
    if PPE_TEST_LOCKED=yes run_deploy "$project" "" initial > "$test_root/lock-contention.outer.log" 2>&1; then
        echo "FAIL: deployment proceeded while the shared database lock was held" >&2; exit 1
    fi
    grep -q 'Another backup/deployment owns the database operation lock' "$test_root/lock-contention.log"
    [[ ! -d "$PPE_STATE_DIR/deployment.lock" ]]
    if grep -qv '^context inspect ' "$PPE_FAKE_DOCKER_TRACE"; then
        echo "FAIL: contended deployment invoked Docker beyond the local endpoint approval check" >&2; exit 1
    fi
    [[ "$(stat -Lc '%a' "$PPE_STATE_DIR/database-operation.lock")" = 600 ]]
    echo "PASS: deployment rejects real OS lock contention before any release or Compose operation"
else
    echo "SKIP: real flock contention test (flock is not installed); deployment lock inheritance is exercised by the backup mock"
fi

for point in before-migration after-migration before-app before-nginx before-smoke before-publish; do
    project="ppe-sim-${point}"
    : > "$PPE_FAKE_DOCKER_TRACE"
    if run_deploy "$project" "$point" >"$test_root/$project.first.log" 2>&1; then
        echo "FAIL: failpoint $point unexpectedly succeeded" >&2
        exit 1
    fi
    [[ -f "$test_root/$project/state/deployment.state" ]]
    [[ ! -f "$test_root/$project/state/current-release" ]]
    if [[ "$point" = before-migration ]]; then
        [[ ! -f "$test_root/$project/database.state" ]]
        grep -qx 'format=2' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'mode=initial' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'previous=' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'policy=initial' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'phase=migration_started' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'migration_baseline=0' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'target_migrations=23' "$PPE_STATE_DIR/deployment.state"
    else
        [[ -f "$test_root/$project/database.state" ]]
    fi
    : > "$PPE_FAKE_DOCKER_TRACE"
    if run_deploy "$project" "" >"$test_root/$project.resume.log" 2>&1; then
        [[ "$(cat "$test_root/$project/state/current-release")" = \
            '1111111111111111111111111111111111111111' ]]
        [[ "$(sed -n 's/^phase=//p' "$test_root/$project/state/deployment.state")" = published ]]
    else
        cat "$test_root/$project.resume.log" >&2
        echo "FAIL: same-release recovery did not complete after $point" >&2
        exit 1
    fi
    echo "PASS: same-release recovery after $point preserves DB state and publishes only after smoke"
    expected_runs=0
    [[ "$point" != before-migration ]] || expected_runs=1
    [[ "$(grep -c 'run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE" || true)" = "$expected_runs" ]]
    grep -q 'ps --all -q db' "$PPE_FAKE_DOCKER_TRACE"
    grep -q 'volume inspect ' "$PPE_FAKE_DOCKER_TRACE"
    if grep -Eq ' (up|start|restart|stop|rm|create) (.* )?db$|volume (create|rm)|image (build|rm|tag)' "$PPE_FAKE_DOCKER_TRACE"; then
        echo "FAIL: recovery changed the existing DB lifecycle, volume or release images" >&2; exit 1
    fi
done
echo "PASS: repaired operational tooling resumes an immutable old release under dash without running migration twice"

for condition in missing-pins missing multiple replaced-container stopped unhealthy no-healthcheck wrong-image wrong-project \
    wrong-service oneoff config-drift wrong-volume bind-mount readonly-volume missing-volume wrong-volume-label replaced-volume; do
    project="ppe-sim-db-$condition"
    if run_deploy "$project" before-migration > "$test_root/$project.first.log" 2>&1; then
        echo "FAIL: DB recovery setup unexpectedly completed" >&2; exit 1
    fi
    cp "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
    cp "$PPE_STATE_DIR/deployment-events.log" "$test_root/events-before"
    : > "$PPE_FAKE_DOCKER_TRACE"
    if PPE_FAKE_DB_CONDITION=$condition run_deploy "$project" "" > "$test_root/$project.resume.log" 2>&1; then
        echo "FAIL: recovery accepted DB condition $condition" >&2; exit 1
    fi
    cmp -s "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
    cmp -s "$PPE_STATE_DIR/deployment-events.log" "$test_root/events-before"
    [[ ! -f "$PPE_FAKE_DB_STATE" && ! -f "$PPE_STATE_DIR/current-release" ]]
    [[ ! -d "$PPE_STATE_DIR/deployment.lock" ]]
    if grep -Eq 'exec |run |up |start |stop |restart |down |volume (create|rm)' "$PPE_FAKE_DOCKER_TRACE"; then
        echo "FAIL: rejected DB condition $condition caused mutation" >&2; exit 1
    fi
    echo "PASS: DB $condition fails closed without changing journal, events, schema or services"
done

project=ppe-sim-update-recovery
if run_deploy "$project" before-migration > "$test_root/update.first.log" 2>&1; then
    echo "FAIL: update recovery setup unexpectedly completed" >&2; exit 1
fi
(
    source "$scripts/deployment-state.sh"
    deployment_mode=update
    deployment_release=1111111111111111111111111111111111111111
    deployment_previous=3333333333333333333333333333333333333333
    deployment_policy=backward-compatible
    migration_baseline=0
    target_migrations=23
    write_release_file "$PPE_STATE_DIR/current-release" "$deployment_previous"
    printf '%s|%s\n' "$deployment_previous" "$migration_baseline" > "$PPE_STATE_DIR/deployment-backup.receipt"
    deployment_id=11111111111111111111111111111111
    source "$scripts/migration-preflight.sh"
    database_table_count() { echo 0; }
    inspect_migration_inventory >/dev/null
    baseline_history_hash=$migration_history_hash
    baseline_inventory_hash=$migration_inventory_hash
    deployment_receipt_hash=$(source_hash "$PPE_STATE_DIR/deployment-backup.receipt")
    recovery_reference_hash=$(source_hash "$PPE_BACKUP_RECOVERY_REFERENCE")
    migration_attempted=no
    write_state migration_started
    mkdir -m 700 "$PPE_STATE_DIR/backup-$deployment_id"
    cp "$PPE_STATE_DIR/deployment-backup.receipt" "$PPE_STATE_DIR/backup-$deployment_id/receipt"
    printf '%s\n' "$deployment_receipt_hash" > "$PPE_STATE_DIR/backup-$deployment_id/receipt.sha256"
)
: > "$PPE_FAKE_DOCKER_TRACE"
if ! PPE_SCHEMA_POLICY=backward-compatible PPE_DEPLOYMENT_TEST_FAIL_AT="" \
    bash "$scripts/deploy-release.sh" update "$release" > "$test_root/update.resume.log" 2>&1; then
    cat "$test_root/update.resume.log" >&2
    echo "FAIL: interrupted update could not resume" >&2; exit 1
fi
grep -qx 'phase=published' "$PPE_STATE_DIR/deployment.state"
grep -qx '3333333333333333333333333333333333333333' "$PPE_STATE_DIR/previous-release"
[[ "$(grep -c 'run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE")" = 1 ]]
if grep -Eq ' (up|start|restart|stop|rm|create) (.* )?db$|volume (create|rm)|stop app python-runner' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: update recovery changed DB lifecycle or repeated completed quiesce" >&2; exit 1
fi
echo "PASS: interrupted update retains the existing DB and skips completed quiesce/backup stages"

project=ppe-sim-update-fresh
export PPE_PROJECT=$project
export PPE_STATE_DIR="$test_root/$project/state"
export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
mkdir -p "$PPE_STATE_DIR"
chmod 0700 "$PPE_STATE_DIR"
printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
printf '1 0\n' > "$PPE_FAKE_DB_STATE"
: > "$PPE_FAKE_DOCKER_TRACE"
: > "$PPE_FAKE_DEPLOY_TRACE"
if run_deploy "$project" before-migration update backward-compatible \
    > "$test_root/$project.first.log" 2>&1; then
    echo "FAIL: fresh update failpoint unexpectedly succeeded" >&2; exit 1
fi
[[ -f "$PPE_STATE_DIR/deployment-backup.receipt" ]]
grep -qx '3333333333333333333333333333333333333333|1' "$PPE_STATE_DIR/deployment-backup.receipt"
grep -q '^backup$' "$PPE_FAKE_DEPLOY_TRACE"
grep -q '^receipt-validated$' "$PPE_FAKE_DEPLOY_TRACE"
[[ "$(sed -n 's/^phase=//p' "$PPE_STATE_DIR/deployment.state")" = migration_started ]]
grep -q 'Injected local deployment failure at before-migration' "$test_root/$project.first.log"
if grep -Eq ' (up|start|restart|rm|create) (.* )?db$|volume (create|rm)' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: a fresh update invoked DB lifecycle or volume mutation" >&2; exit 1
fi
echo "PASS: fresh update verifies pinned DB read-only, requires remote backup receipt before migration, and never starts DB"

project=ppe-sim-update-pinned-db
export PPE_PROJECT=$project
export PPE_STATE_DIR="$test_root/$project/state"
export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
export PPE_DB_RELEASE=1111111111111111111111111111111111111111
export PPE_FAKE_DB_RELEASE=$PPE_DB_RELEASE
mkdir -p "$PPE_STATE_DIR"
chmod 0700 "$PPE_STATE_DIR"
printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
printf '1 0\n' > "$PPE_FAKE_DB_STATE"
: > "$PPE_FAKE_DOCKER_TRACE"
if run_deploy "$project" before-migration update backward-compatible "$new_release" \
    > "$test_root/$project.log" 2>&1; then
    echo "FAIL: pinned-DB app update failpoint unexpectedly succeeded" >&2; exit 1
fi
[[ "$(sed -n 's/^phase=//p' "$PPE_STATE_DIR/deployment.state")" = migration_started ]]
grep -q 'Verified the pinned healthy DB container' "$test_root/$project.log"
grep -q 'ps --all -q db' "$PPE_FAKE_DOCKER_TRACE"
if grep -Eq ' (up|start|restart|rm|create) (.* )?db$|volume (create|rm)' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: pinned-DB app update changed DB lifecycle or volume" >&2; exit 1
fi
echo "PASS: new app release proceeds when explicit older DB release pin has matching manifest, image and recipe"
if ! PPE_RESTART_RECOVERED_UPDATE=yes run_deploy "$project" "" update backward-compatible "$new_release" \
    > "$test_root/$project.resume.log" 2>&1; then
    cat "$test_root/$project.resume.log" >&2
    echo "FAIL: app update with pinned database could not complete" >&2; exit 1
fi
grep -qx 'phase=published' "$PPE_STATE_DIR/deployment.state"
grep -qx '2222222222222222222222222222222222222222' "$PPE_STATE_DIR/current-release"
if grep -Eq ' (up|start|restart|rm|create) (.* )?db$|volume (create|rm)' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: pinned database resume changed DB lifecycle or volume" >&2; exit 1
fi
echo "PASS: pinned DB app update resumes to publication with the verified old build context"
unset PPE_DB_RELEASE PPE_FAKE_DB_RELEASE

for condition in wrong-image config-drift replaced-container replaced-volume; do
    project="ppe-sim-update-preflight-$condition"
    export PPE_PROJECT=$project
    export PPE_STATE_DIR="$test_root/$project/state"
    export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
    mkdir -p "$PPE_STATE_DIR"
    chmod 0700 "$PPE_STATE_DIR"
    printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
    printf '1 0\n' > "$PPE_FAKE_DB_STATE"
    : > "$PPE_FAKE_DOCKER_TRACE"
    if PPE_FAKE_DB_CONDITION=$condition run_deploy "$project" "" update backward-compatible \
        > "$test_root/$project.log" 2>&1; then
        echo "FAIL: fresh update accepted unsafe DB condition $condition" >&2; exit 1
    fi
    [[ ! -f "$PPE_STATE_DIR/deployment.state" && ! -f "$PPE_STATE_DIR/deployment-events.log" ]]
    [[ ! -d "$PPE_STATE_DIR/deployment.lock" ]]
    if grep -Eq 'exec |run |up |start |stop |restart |down |volume (create|rm)' "$PPE_FAKE_DOCKER_TRACE"; then
        echo "FAIL: fresh update DB preflight $condition caused a mutation" >&2; exit 1
    fi
    echo "PASS: fresh update rejects pinned DB $condition before journal or service mutation"
done

for pin_case in missing wrong-manifest recipe-drift; do
    project="ppe-sim-update-invalid-db-pin-$pin_case"
    export PPE_PROJECT=$project
    export PPE_STATE_DIR="$test_root/$project/state"
    export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
    mkdir -p "$PPE_STATE_DIR"
    chmod 0700 "$PPE_STATE_DIR"
    printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
    printf '1 0\n' > "$PPE_FAKE_DB_STATE"
    case "$pin_case" in
        missing) pin_sha=6666666666666666666666666666666666666666 ;;
        wrong-manifest) pin_sha=4444444444444444444444444444444444444444 ;;
        recipe-drift) pin_sha=5555555555555555555555555555555555555555 ;;
    esac
    : > "$PPE_FAKE_DOCKER_TRACE"
    if PPE_DB_RELEASE=$pin_sha PPE_FAKE_DB_RELEASE=$pin_sha \
        run_deploy "$project" "" update backward-compatible "$new_release" \
        > "$test_root/$project.log" 2>&1; then
        echo "FAIL: update accepted invalid DB pin case $pin_case" >&2; exit 1
    fi
    [[ ! -f "$PPE_STATE_DIR/deployment.state" && ! -f "$PPE_STATE_DIR/deployment-events.log" ]]
    case "$pin_case" in
        missing|wrong-manifest) grep -q 'Pinned database release is missing or its READY/commit manifest is invalid' "$test_root/$project.log" ;;
        recipe-drift) grep -q 'Pinned database release recipe differs from target' "$test_root/$project.log" ;;
    esac
    if grep -Eq 'exec |run |up |start |stop |restart |down |volume (create|rm)' "$PPE_FAKE_DOCKER_TRACE"; then
        echo "FAIL: invalid DB pin $pin_case caused a database/service mutation" >&2; exit 1
    fi
    echo "PASS: invalid DB pin $pin_case fails before journal writes or service/DB lifecycle actions"
done

project=ppe-sim-update-missing-receipt
export PPE_PROJECT=$project
export PPE_STATE_DIR="$test_root/$project/state"
export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
mkdir -p "$PPE_STATE_DIR"
chmod 0700 "$PPE_STATE_DIR"
printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
printf '1 0\n' > "$PPE_FAKE_DB_STATE"
(
    source "$scripts/deployment-state.sh"
    deployment_mode=update
    deployment_release=1111111111111111111111111111111111111111
    deployment_previous=3333333333333333333333333333333333333333
    deployment_policy=backward-compatible
    migration_baseline=1
    target_migrations=23
    write_state migration_started
)
cp "$PPE_STATE_DIR/deployment.state" "$test_root/$project.state-before"
: > "$PPE_FAKE_DOCKER_TRACE"
if run_deploy "$project" "" update backward-compatible > "$test_root/$project.resume.log" 2>&1; then
    echo "FAIL: old format-2 update without a backup receipt was accepted" >&2; exit 1
fi
cmp -s "$PPE_STATE_DIR/deployment.state" "$test_root/$project.state-before"
grep -Eq 'matching attempt/recovery evidence|receipt hash is missing' "$test_root/$project.resume.log"
if grep -Eq 'up |start |stop |restart |down |volume (create|rm)|run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: missing-receipt recovery mutated services or database" >&2; exit 1
fi
echo "PASS: format-2 update resume without a validated backup receipt fails closed before mutation"

project=ppe-sim-other-release
if run_deploy "$project" before-migration > "$test_root/other-release.first.log" 2>&1; then
    echo "FAIL: setup failpoint unexpectedly succeeded" >&2; exit 1
fi
cp "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
other="$test_root/releases/7777777777777777777777777777777777777777"
mkdir "$other"
ln -s "$release/source" "$other/source"
printf '%s\n' '7777777777777777777777777777777777777777' > "$other/commit"
cp "$other/commit" "$other/READY"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    cp "$release/$image.id" "$other/$image.id"
done
: > "$PPE_FAKE_DOCKER_TRACE"
if bash "$scripts/deploy-release.sh" initial "$other" > "$test_root/other-release.log" 2>&1; then
    echo "FAIL: incomplete deployment switched releases" >&2; exit 1
fi
grep -q 'refusing to switch' "$test_root/other-release.log"
cmp -s "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
[[ ! -f "$PPE_FAKE_DB_STATE" ]]
if grep -Eq 'exec |run |up |stop |down ' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: refused release switch mutated services or database" >&2; exit 1
fi
echo "PASS: changing target releases is refused without modifying the recorded state or database"

: > "$PPE_FAKE_DOCKER_TRACE"
if PPE_OPERATION_APPROVAL=none bash "$scripts/deploy-release.sh" initial "$release" \
    > "$test_root/approval.log" 2>&1; then
    echo "FAIL: deployment without operation approval was accepted" >&2; exit 1
fi
[[ ! -s "$PPE_FAKE_DOCKER_TRACE" ]]
grep -q 'approval is required' "$test_root/approval.log"
if PPE_MAINTENANCE_APPROVED=no bash "$scripts/deploy-release.sh" initial "$release" \
    > "$test_root/maintenance.log" 2>&1; then
    echo "FAIL: deployment without maintenance approval was accepted" >&2; exit 1
fi
grep -q 'Approve a maintenance window' "$test_root/maintenance.log"
cmp -s "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
[[ ! -d "$PPE_STATE_DIR/deployment.lock" ]]
echo "PASS: operation and maintenance approval checks reject recovery without mutating the journal"

project=ppe-sim-mid-migration
export PPE_FAKE_MIGRATION_MODE=partial
if run_deploy "$project" after-migration >"$test_root/$project.first.log" 2>&1; then
    echo "FAIL: partial migration unexpectedly succeeded" >&2
    exit 1
fi
export PPE_FAKE_MIGRATION_MODE=success
if run_deploy "$project" "" >"$test_root/$project.resume.log" 2>&1; then
    echo "FAIL: partial Flyway history was retried automatically" >&2
    exit 1
fi
[[ "$(cat "$test_root/$project/database.state")" = '3 1' ]]
[[ ! -f "$test_root/$project/state/current-release" ]]
grep -Eq 'manual review|automatic migration retry is forbidden' \
    "$test_root/$project.resume.log" || {
        cat "$test_root/$project.resume.log" >&2
        echo "FAIL: partial Flyway history did not fail closed" >&2
        exit 1
    }
echo "PASS: partial migration remains intact and resume fails closed for manual recovery"

mkdir -m 700 "$test_root/cloud-mock"
PPE_BACKUP_MOCK_EXPORT_DIR="$test_root/cloud-mock" "$PPE_TEST_BASH" "$scripts/tests/backup-test.sh"
cp "$test_root/cloud-mock/aws" "$test_root/bin/aws"
export PPE_TEST_USE_REAL_BACKUP=yes
unset PPE_DB_RELEASE PPE_FAKE_DB_RELEASE
for condition in success data head corrupt-download recovery-failure; do
    project="ppe-sim-cloud-$condition"
    export PPE_PROJECT=$project PPE_STATE_DIR="$test_root/$project/state"
    export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
    mkdir -p "$PPE_STATE_DIR" "$test_root/$project/remote"
    chmod 0700 "$PPE_STATE_DIR"
    export MOCK_ROOT="$test_root/$project"
    printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
    printf '1 0\n' > "$PPE_FAKE_DB_STATE"
    : > "$PPE_FAKE_DOCKER_TRACE"
    : > "$PPE_FAKE_DEPLOY_TRACE"
    export PPE_FAKE_OLD_APP_FAIL=no
    failure_stage=$condition
    if [[ "$condition" = recovery-failure ]]; then PPE_FAKE_OLD_APP_FAIL=yes; failure_stage=data; fi
    if FAIL_STAGE="$failure_stage" PPE_BACKUP_REQUIRE_REMOTE=no run_deploy "$project" "" update backward-compatible > "$test_root/$project.log" 2>&1; then
        [[ "$condition" = success ]] || { echo "FAIL: S3 failure accepted" >&2; exit 1; }
        grep -qx 'phase=published' "$PPE_STATE_DIR/deployment.state"
        grep -q '^receipt_hash=[a-f0-9]\{64\}$' "$PPE_STATE_DIR/deployment.state"
        grep -q '^deployment_id=[a-f0-9]\{32\}$' "$PPE_STATE_DIR/deployment.state"
        echo "PASS: real deployment/backup/receipt chain reaches publication only after exact-version S3 evidence"
    else
        [[ "$condition" != success ]] || { cat "$test_root/$project.log" >&2; exit 1; }
        [[ "$(cat "$PPE_FAKE_DB_STATE")" = '1 0' ]]
        if [[ "$condition" = recovery-failure ]]; then
            grep -q 'Old application recovery failed' "$test_root/$project.log"
            grep -qx 'recovery_attempted=yes' "$PPE_STATE_DIR/deployment.state"
            grep -qx 'apps_restored=no' "$PPE_STATE_DIR/deployment.state"
            state_before=$(source_hash "$PPE_STATE_DIR/deployment.state")
            if run_deploy "$project" "" update backward-compatible > "$test_root/$project.retry.log" 2>&1; then exit 1; fi
            [[ "$state_before" = "$(source_hash "$PPE_STATE_DIR/deployment.state")" ]]
        else
            grep -qx 'apps_restored=yes' "$PPE_STATE_DIR/deployment.state"
        fi
        [[ "$(cat "$PPE_STATE_DIR/current-release")" = '3333333333333333333333333333333333333333' ]]
        if grep -q 'run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE"; then exit 1; fi
        if [[ "$condition" = recovery-failure ]]; then
            echo "PASS: S3 failure prevents migration; failed app recovery stays explicit and blocks blind retry"
        else
            echo "PASS: S3 $condition failure prevents migration and restores old app while retaining evidence"
        fi
        if [[ "$condition" = data ]]; then
            old_attempt=$(sed -n 's/^deployment_id=//p' "$PPE_STATE_DIR/deployment.state")
            state_before=$(source_hash "$PPE_STATE_DIR/deployment.state")
            if run_deploy "$project" "" update backward-compatible > "$test_root/$project.retry.log" 2>&1; then
                echo "FAIL: restored app allowed unreviewed backup reuse" >&2; exit 1
            fi
            [[ "$state_before" = "$(source_hash "$PPE_STATE_DIR/deployment.state")" ]]
            PPE_RESTART_RECOVERED_UPDATE=yes FAIL_STAGE=success run_deploy "$project" "" update backward-compatible \
                > "$test_root/$project.new-attempt.log" 2>&1
            grep -qx 'phase=published' "$PPE_STATE_DIR/deployment.state"
            [[ "$(sed -n 's/^deployment_id=//p' "$PPE_STATE_DIR/deployment.state")" != "$old_attempt" ]]
            [[ "$(source_hash "$PPE_STATE_DIR/recovered-$old_attempt.state")" = "$state_before" ]]
            echo "PASS: restored-app retry requires approval and a fresh attempt/backup while retaining abandoned state"
        fi
    fi
    if grep -Eq 'synthetic-not-real-SQL|synthetic-encrypted-payload' "$test_root/$project.log"; then
        echo "FAIL: database/encrypted payload was logged." >&2; exit 1
    fi
done
unset PPE_FAKE_OLD_APP_FAIL

for condition in partial-unrecorded partial sync image; do
    project="ppe-sim-post-backup-$condition"
    export PPE_PROJECT=$project PPE_STATE_DIR="$test_root/$project/state"
    export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
    mkdir -p "$PPE_STATE_DIR" "$test_root/$project/remote"
    chmod 0700 "$PPE_STATE_DIR"
    export MOCK_ROOT="$test_root/$project"
    printf '%s\n' '3333333333333333333333333333333333333333' > "$PPE_STATE_DIR/current-release"
    printf '1 0\n' > "$PPE_FAKE_DB_STATE"
    : > "$PPE_FAKE_DOCKER_TRACE"
    export PPE_FAKE_MIGRATION_MODE=success PPE_FAKE_RUNNING_IMAGE=valid PPE_FAKE_FAIL_TLS_INSTALL=no
    case "$condition" in
        partial*) PPE_FAKE_MIGRATION_MODE=$condition ;;
        sync) PPE_FAKE_FAIL_TLS_INSTALL=yes ;;
        image) PPE_FAKE_RUNNING_IMAGE=wrong ;;
    esac
    if run_deploy "$project" "" update backward-compatible > "$test_root/$project.log" 2>&1; then
        echo "FAIL: unsafe post-backup condition accepted: $condition" >&2; exit 1
    fi
    [[ "$(cat "$PPE_STATE_DIR/current-release")" = '3333333333333333333333333333333333333333' ]]
    [[ "$(sed -n 's/^phase=//p' "$PPE_STATE_DIR/deployment.state")" != published ]]
    [[ "$(sed -n 's/^apps_restored=//p' "$PPE_STATE_DIR/deployment.state")" != yes ]]
    if [[ "$condition" = partial* ]]; then
        state_before=$(source_hash "$PPE_STATE_DIR/deployment.state")
        : > "$PPE_FAKE_DOCKER_TRACE"
        PPE_FAKE_MIGRATION_MODE=success
        if run_deploy "$project" "" update backward-compatible > "$test_root/$project.resume.log" 2>&1; then exit 1; fi
        [[ "$state_before" = "$(source_hash "$PPE_STATE_DIR/deployment.state")" ]]
        if grep -Eq 'stop app|run --rm --no-deps migrate$|up -d' "$PPE_FAKE_DOCKER_TRACE"; then exit 1; fi
    fi
    echo "PASS: $condition failure preserves evidence and forbids completion/unsafe recovery"
done
unset PPE_FAKE_RUNNING_IMAGE PPE_FAKE_FAIL_TLS_INSTALL
export PPE_FAKE_MIGRATION_MODE=success

project=ppe-sim-cloud-success
export PPE_PROJECT=$project PPE_STATE_DIR="$test_root/$project/state"
export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
export PPE_BACKUP_OPERATION_ENV="$PPE_STATE_DIR/backup-operation.env" PPE_TLS_OPERATION_ENV="$PPE_STATE_DIR/tls-operation.env"
export PPE_DB_RELEASE="${release##*/}" PPE_FAKE_DB_RELEASE="${release##*/}"
export PPE_SOURCE_MANIFEST_SHA256="$(source_hash "$previous_release/source.sha256")"
export PPE_ROLLBACK_COMPATIBILITY_REFERENCE="$PPE_STATE_DIR/rollback-review"
history_hash=$(for version in $(seq 1 23); do printf '%s|V%s__test.sql|123|1\n' "$version" "$version"; done | openssl dgst -sha256 | awk '{print $NF}')
printf 'source_release=%s\ntarget_release=%s\nsource_container=%s\nhistory_hash=%s\ncompatible=no\n' \
    "${release##*/}" "${previous_release##*/}" "$PPE_RECOVERY_DB_CONTAINER_ID" "$history_hash" > "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE"
state_before=$(source_hash "$PPE_STATE_DIR/deployment.state")
: > "$PPE_FAKE_DOCKER_TRACE"
if PPE_SCHEMA_POLICY=requires-downtime bash "$scripts/rollback-release.sh" "$previous_release" > "$test_root/rollback-policy.log" 2>&1; then exit 1; fi
if PPE_SCHEMA_POLICY=backward-compatible bash "$scripts/rollback-release.sh" "$previous_release" > "$test_root/rollback-review.log" 2>&1; then exit 1; fi
[[ "$state_before" = "$(source_hash "$PPE_STATE_DIR/deployment.state")" ]]
if grep -Eq 'stop app|up -d|run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE"; then exit 1; fi
echo "PASS: incompatible or unreviewed rollback is refused before services stop"
sed 's/compatible=no/compatible=yes/' "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE" > "$test_root/accepted-review"
cp "$test_root/accepted-review" "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE"
if ! PPE_SCHEMA_POLICY=backward-compatible bash "$scripts/rollback-release.sh" "$previous_release" > "$test_root/rollback-success.log" 2>&1; then
    cat "$test_root/rollback-success.log" >&2; exit 1
fi
grep -qx "${previous_release##*/}" "$PPE_STATE_DIR/current-release"
grep -qx "PPE_RELEASE=${previous_release##*/}" "$PPE_BACKUP_OPERATION_ENV"
grep -qx "PPE_RELEASE=${previous_release##*/}" "$PPE_TLS_OPERATION_ENV"
[[ "$(cat "$PPE_FAKE_DB_STATE")" = '23 0' ]]
if grep -Eq 'run --rm --no-deps migrate$| (up|stop|rm|restart) (.* )?db$' "$PPE_FAKE_DOCKER_TRACE"; then exit 1; fi
echo "PASS: reviewed rollback restores prior app/config references and preserves the current DB"
state_before=$(source_hash "$PPE_STATE_DIR/deployment.state")
: > "$PPE_FAKE_DOCKER_TRACE"
PPE_SCHEMA_POLICY=backward-compatible bash "$scripts/rollback-release.sh" "$previous_release" \
    > "$test_root/rollback-repeat.log" 2>&1
[[ "$state_before" = "$(source_hash "$PPE_STATE_DIR/deployment.state")" ]]
if grep -Eq 'stop app|up -d|run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE"; then exit 1; fi
echo "PASS: repeated completed rollback verifies runtime without restarting services"
if PPE_FAKE_RUNNING_IMAGE=wrong PPE_SCHEMA_POLICY=backward-compatible \
    bash "$scripts/rollback-release.sh" "$previous_release" > "$test_root/rollback-repeat-drift.log" 2>&1; then
    echo "FAIL: repeated rollback concealed running-image drift" >&2; exit 1
fi
[[ "$state_before" = "$(source_hash "$PPE_STATE_DIR/deployment.state")" ]]
echo "PASS: repeated completed rollback refuses running-image drift"

project=ppe-sim-app-started-recovery
export PPE_PROJECT=$project PPE_STATE_DIR="$test_root/$project/state"
export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
mkdir -p "$PPE_STATE_DIR" "$test_root/$project/remote"
chmod 0700 "$PPE_STATE_DIR"
printf '%s\n' "${previous_release##*/}" > "$PPE_STATE_DIR/current-release"
printf '23 0\n' > "$PPE_FAKE_DB_STATE"
export MOCK_ROOT="$test_root/$project"
unset PPE_FAKE_RUNNING_IMAGE PPE_FAKE_FAIL_TLS_INSTALL
if run_deploy "$project" before-app update backward-compatible > "$test_root/app-started-failure.log" 2>&1; then
    echo "FAIL: app-started failpoint did not stop update" >&2; exit 1
fi
grep -qx phase=app_started "$PPE_STATE_DIR/deployment.state"
grep -qx migration_attempted=no "$PPE_STATE_DIR/deployment.state"
grep -qx "${previous_release##*/}" "$PPE_STATE_DIR/current-release"
export PPE_SOURCE_MANIFEST_SHA256="$(source_hash "$previous_release/source.sha256")"
export PPE_ROLLBACK_COMPATIBILITY_REFERENCE="$PPE_STATE_DIR/current-schema-review"
printf 'source_release=%s\ntarget_release=%s\nsource_container=%s\nhistory_hash=%s\ncompatible=yes\n' \
    "${previous_release##*/}" "${previous_release##*/}" "$PPE_RECOVERY_DB_CONTAINER_ID" \
    "$history_hash" > "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE"
unset PPE_BIND_ADDRESS PPE_HTTP_PORT PPE_HTTPS_PORT PPE_DEPLOYMENT_TEST_FAIL_AT
: > "$PPE_FAKE_DOCKER_TRACE"
PPE_SCHEMA_POLICY=backward-compatible bash "$scripts/rollback-release.sh" "$previous_release" \
    > "$test_root/app-started-recovery.log" 2>&1
grep -qx phase=published "$PPE_STATE_DIR/deployment.state"
grep -qx mode=rollback "$PPE_STATE_DIR/deployment.state"
grep -qx "${previous_release##*/}" "$PPE_STATE_DIR/current-release"
[[ "$(cat "$PPE_FAKE_DB_STATE")" = '23 0' ]]
[[ -n "$(find "$PPE_STATE_DIR" -name 'failed-update-*.state' -print)" ]]
if grep -Eq 'run --rm --no-deps migrate$| (up|stop|rm|restart) (.* )?db$' "$PPE_FAKE_DOCKER_TRACE"; then exit 1; fi
echo "PASS: app_started/no-migration failed update recovers old broker/runner/app/nginx using TLS network pins and current-schema review without DB lifecycle"
unset PPE_TEST_USE_REAL_BACKUP FAIL_STAGE PPE_DB_RELEASE PPE_FAKE_DB_RELEASE

find "$release" -type f -exec shasum -a 256 '{}' \; > "$test_root/release-after.sha256"
cmp -s "$test_root/release-before.sha256" "$test_root/release-after.sha256"
echo "PASS: old release scripts, SQL, Compose, READY and image-ID records remain byte-for-byte unchanged"
echo "PASS: test harness used no Docker daemon resources, development data, or production secrets"
