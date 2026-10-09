#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
[[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || {
    echo "Roundtrip test requires a local Unix Docker endpoint." >&2; exit 1;
}
docker image inspect ppe-db:local ppe-backup:local >/dev/null
root=$(mktemp -d)
root=$(cd "$root" && pwd -P)
source_project=ppe-sim-backup-roundtrip-$$
target_project=ppe-restore-backup-roundtrip-$$
export ROUNDTRIP_ROOT="$root"
compose="$root/compose.yml"
started_source=no
started_target=no
cleanup() {
    local status=$?
    trap - EXIT
    if [[ "$started_target" = yes ]]; then
        docker compose -p "$target_project" -f "$compose" down -v >/dev/null || status=1
    fi
    if [[ "$started_source" = yes ]]; then
        docker compose -p "$source_project" -f "$compose" down -v >/dev/null || status=1
    fi
    rm -f "$root/compose.yml" "$root/db_password" "$root/db_root_password" \
        "$root/db_migration_password" "$root/identity" "$root/recipient" \
        "$root/backup.age" "$root/backup.age.sha256" "$root/source.rows" "$root/target.rows"
    rmdir "$root" || status=1
    exit "$status"
}
trap cleanup EXIT
for secret in db_password db_root_password db_migration_password; do
    printf 'synthetic-test-only-1234567890\n' > "$root/$secret"
done
cat > "$compose" <<'YAML'
services:
  db:
    image: ppe-db:local
    environment:
      MYSQL_DATABASE: ppe
      MYSQL_ROOT_HOST: localhost
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/db_root_password
    secrets: [db_root_password, db_password, db_migration_password]
    volumes: [database:/var/lib/mysql]
    networks: [database]
    mem_limit: 512m
    command: ["mysqld", "--innodb-buffer-pool-size=128M"]
    healthcheck:
      test: ["CMD-SHELL", "test -f /var/lib/mysql/.ppe-initialized && mysqladmin --protocol=tcp --host=127.0.0.1 ping --silent"]
      interval: 2s
      timeout: 5s
      retries: 60
volumes:
  database:
networks:
  database:
    internal: true
secrets:
  db_root_password:
    file: ${ROUNDTRIP_ROOT}/db_root_password
  db_password:
    file: ${ROUNDTRIP_ROOT}/db_password
  db_migration_password:
    file: ${ROUNDTRIP_ROOT}/db_migration_password
YAML
for project in "$source_project" "$target_project"; do
    [[ -z "$(docker compose -p "$project" -f "$compose" ps --all -q)" ]] || {
        echo "Refusing to reuse an existing test project." >&2; exit 1;
    }
    if docker volume inspect "${project}_database" >/dev/null 2>&1; then
        echo "Refusing to reuse an existing test volume." >&2; exit 1
    fi
done
started_source=yes
docker compose -p "$source_project" -f "$compose" up -d --no-deps --wait --wait-timeout 150 db >/dev/null
docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh >/dev/null <<'SQL'
CREATE TABLE ppe.rubrics (id BIGINT PRIMARY KEY, version VARCHAR(50) NOT NULL);
CREATE TABLE ppe.users (id BIGINT PRIMARY KEY, role VARCHAR(16) NOT NULL, display_name VARCHAR(100) NOT NULL);
CREATE TABLE ppe.code_logs (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, code TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id));
CREATE TABLE ppe.submissions (id BIGINT PRIMARY KEY, code TEXT NOT NULL);
CREATE TABLE ppe.evaluations (id BIGINT PRIMARY KEY, rubric_id BIGINT NOT NULL, submission_id BIGINT NOT NULL,
 snapshot JSON NOT NULL, FOREIGN KEY (rubric_id) REFERENCES rubrics(id), FOREIGN KEY (submission_id) REFERENCES submissions(id));
INSERT INTO ppe.rubrics VALUES (1,'synthetic-old-v1'),(2,'synthetic-new-v2');
INSERT INTO ppe.submissions VALUES (10,'print("合成データのみ")\nprint("追加行")'),(11,'print("second version")');
INSERT INTO ppe.evaluations VALUES (20,1,10,'{"score":3,"definition":"旧版の合成根拠"}'),(21,2,11,'{"score":4,"definition":"new"}');
INSERT INTO ppe.users VALUES (30,'student','synthetic student'),(31,'teacher','synthetic teacher');
INSERT INTO ppe.code_logs VALUES (40,30,'synthetic edited code');
SQL
query="SELECT id,version FROM ppe.rubrics ORDER BY id; SELECT id,code FROM ppe.submissions ORDER BY id; SELECT id,rubric_id,submission_id,snapshot FROM ppe.evaluations ORDER BY id; SELECT id,role,display_name FROM ppe.users ORDER BY id; SELECT id,user_id,code FROM ppe.code_logs ORDER BY id;"
printf '%s\n' "$query" | docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh > "$root/source.rows"
docker run --rm --network none --read-only --user 0:0 -v "$root:/fixture" \
    --entrypoint /bin/sh ppe-backup:local -c \
    'umask 077; age-keygen -o /fixture/identity 2>/dev/null; age-keygen -y /fixture/identity > /fixture/recipient; chmod 0444 /fixture/recipient'
docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh --dump |
    docker run --rm -i --network none --read-only -v "$root/recipient:/run/recipient:ro" \
        ppe-backup:local -R /run/recipient > "$root/backup.age"
source "$scripts/backup-common.sh"
printf '%s  backup.age\n' "$(backup_hash "$root/backup.age")" > "$root/backup.age.sha256"
started_target=yes
docker compose -p "$target_project" -f "$compose" up -d --no-deps --wait --wait-timeout 150 db >/dev/null
PPE_PROJECT="$target_project" PPE_RESTORE_APPROVED=isolated-empty-db PPE_SECRETS_DIR="$root" \
    PPE_COMPOSE_FILE="$compose" PPE_BACKUP_IMAGE=ppe-backup:local \
    bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity"
printf '%s\n' "$query" | docker compose -p "$target_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh > "$root/target.rows"
cmp "$root/source.rows" "$root/target.rows"
container_before=$(docker compose -p "$source_project" -f "$compose" ps --all -q db)
volume_before=$(docker volume inspect --format '{{.CreatedAt}}' "${source_project}_database")
docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh >/dev/null <<'SQL'
ALTER TABLE ppe.submissions ADD COLUMN review_note VARCHAR(200) NULL;
CREATE TABLE ppe.feature_settings (id BIGINT PRIMARY KEY, setting_value VARCHAR(100) NOT NULL);
INSERT INTO ppe.feature_settings VALUES (1,'synthetic new feature');
SQL
printf '%s\n' "$query" | docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh > "$root/target.rows"
cmp "$root/source.rows" "$root/target.rows"
[[ "$container_before" = "$(docker compose -p "$source_project" -f "$compose" ps --all -q db)" \
    && "$volume_before" = "$(docker volume inspect --format '{{.CreatedAt}}' "${source_project}_database")" ]]
echo "PASS: additive MySQL migration preserves users, logs, submissions, evaluations, IDs and foreign keys without DB recreation"
if docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh >/dev/null 2>&1 <<'SQL'
CREATE TABLE ppe.partial_ddl_marker (id BIGINT PRIMARY KEY);
ALTER TABLE ppe.nonexistent_table ADD COLUMN invalid_value INT;
SQL
then
    echo "FAIL: partial DDL fixture unexpectedly succeeded." >&2; exit 1
fi
marker=$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe' AND table_name='partial_ddl_marker';\n" |
    docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh --skip-column-names)
[[ "$marker" = 1 ]]
printf '%s\n' "$query" | docker compose -p "$source_project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh > "$root/target.rows"
cmp "$root/source.rows" "$root/target.rows"
echo "PASS: real failed MySQL DDL leaves earlier DDL committed; original synthetic rows remain intact"
if PPE_PROJECT="$target_project" PPE_RESTORE_APPROVED=isolated-empty-db PPE_SECRETS_DIR="$root" \
    PPE_COMPOSE_FILE="$compose" PPE_BACKUP_IMAGE=ppe-backup:local \
    bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity" >/dev/null 2>&1; then
    echo "Expected second restore into nonempty DB to fail." >&2; exit 1
fi
echo "Real local MySQL dump/age/isolated restore: exact IDs/content/JSON/FKs and nonempty refusal PASS"
