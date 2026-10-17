#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/../.."
owner=$$
builder=ppe-initial-seed-builder-$owner
database=ppe-initial-seed-db-$owner
network=ppe-initial-seed-net-$owner
reports=build/initial-configuration-validation/run-$owner
source=programming-process-evaluator-db-1
source_network=programming-process-evaluator_database
private_dir=${PPE_INITIAL_SEED_PRIVATE_DIR:?Set PPE_INITIAL_SEED_PRIVATE_DIR to a private directory outside the repository}
case "$private_dir" in
	/*) ;;
	*) echo "Private directory must be absolute." >&2; exit 2 ;;
esac
if [ ! -d "$private_dir" ] || [ "$(stat -f %Lp "$private_dir" 2>/dev/null || stat -c %a "$private_dir")" != 700 ]; then
	echo "Private directory must already exist with mode 0700." >&2
	exit 2
fi
private_dir=$(cd "$private_dir" && pwd -P)
case "$private_dir/" in "$(pwd)/"*) echo "Private snapshot must be outside the repository." >&2; exit 2 ;; esac
snapshot=initial-configuration-$owner.json
if [ "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$source")" != programming-process-evaluator ] \
		|| [ "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' "$source")" != db ]; then
	echo "Canonical local Compose database identity mismatch." >&2; exit 1
fi
mkdir -p "$reports"
cleanup() {
	for name in "$builder" "$database"; do
		if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.initial-seed.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
			docker rm -fv "$name" >/dev/null
		fi
	done
	if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.initial-seed.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
		docker network rm "$network" >/dev/null
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker network create --internal --label ppe.initial-seed.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.initial-seed.owner="$owner" --network "$network" \
	--tmpfs /var/lib/mysql:rw,size=512m \
	-e MYSQL_ROOT_PASSWORD=synthetic-seed-root -e MYSQL_DATABASE=ppe_initial_seed_$owner \
	programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
ready=false
for attempt in $(seq 1 60); do
	if docker exec "$database" sh -c 'MYSQL_PWD=synthetic-seed-root mysql -uroot -Nse "SELECT 1"' >/dev/null 2>&1; then
		ready=true; break
	fi
	sleep 1
done
if [ "$ready" != true ]; then echo "Isolated MySQL startup failed." >&2; exit 1; fi
docker run -d --name "$builder" --label ppe.initial-seed.owner="$owner" --network "$network" \
	--user 0:0 -e PPE_ENV=simulation \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_initial_seed_$owner \
	-e DB_USER=root -e DB_PASSWORD=synthetic-seed-root \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" \
	--mount "type=bind,src=$private_dir,dst=/private" \
	--entrypoint sh ppe-tools:local -c '
	set -eu
	cd /workspace
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	exec sleep infinity' >/dev/null
run_gradle() {
	name=$1; shift
	if ! docker exec "$builder" gradle --offline --no-daemon "$@" --console plain > "$reports/$name.log" 2>&1; then
		echo "Validation failed at $name; inspect the local report (may contain DB exception details)." >&2
		return 1
	fi
}
run_gradle unit test --tests control.dev.InitialConfigurationSeedTest
mkdir -p "$reports/junit/unit"
docker cp "$builder:/workspace/build/test-results/test/." "$reports/junit/unit/"
run_gradle migration flywayMigrate flywayValidate
docker network connect "$source_network" "$builder"
# Pass local credentials through a private file, never argv, terminal output, or repository files.
docker exec "$source" sh -c 'printf "%s\n" "$MYSQL_USER"; printf "%s" "$MYSQL_PASSWORD"' \
	| docker exec -i "$builder" sh -c 'umask 077; cat > /tmp/source-db-credentials'
if ! docker exec -e DB_HOST="$source" -e DB_NAME=programming_process_evaluator "$builder" sh -c '
	set -eu
	{ IFS= read -r DB_USER; DB_PASSWORD=$(cat); } < /tmp/source-db-credentials
	export DB_USER DB_PASSWORD
	gradle --offline --no-daemon initialConfiguration --args="export /private/'"$snapshot"'" --console plain
	gradle --offline --no-daemon initialConfiguration --args="verify /private/'"$snapshot"'" --console plain
' > "$reports/export.log" 2>&1; then
	echo "Configuration export failed; inspect the private local report." >&2; exit 1
fi
docker exec "$builder" rm /tmp/source-db-credentials
docker network disconnect "$source_network" "$builder"
if ! docker exec -e INITIAL_SEED_DB_TEST=true -e INITIAL_SEED_SNAPSHOT=/private/"$snapshot" "$builder" \
	gradle --offline --no-daemon test --tests control.dev.InitialConfigurationSeedDatabaseTest \
	--rerun-tasks --console plain > "$reports/database.log" 2>&1; then
	echo "Configuration DB verification failed; inspect the local report." >&2; exit 1
fi
mkdir -p "$reports/junit/database"
docker cp "$builder:/workspace/build/test-results/test/." "$reports/junit/database/"
run_gradle war war
run_gradle reapply initialConfiguration --args="apply /private/$snapshot"
run_gradle verify initialConfiguration --args="verify /private/$snapshot"
second_database=ppe_initial_seed_second_$owner
docker exec "$database" sh -c \
	"MYSQL_PWD=synthetic-seed-root mysql -uroot -e 'CREATE DATABASE \`$second_database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'"
if ! docker exec -e DB_NAME="$second_database" "$builder" gradle --offline --no-daemon \
	flywayMigrate flywayValidate initialConfiguration --args="apply /private/$snapshot" --console plain \
	> "$reports/second-empty-database.log" 2>&1; then
	echo "Second empty-database reproduction failed; inspect the local report." >&2; exit 1
fi
if ! docker exec -e DB_NAME="$second_database" "$builder" gradle --offline --no-daemon \
	initialConfiguration --args="verify /private/$snapshot" --console plain \
	> "$reports/second-database-verify.log" 2>&1; then
	echo "Second database comparison failed; inspect the local report." >&2; exit 1
fi
docker exec "$builder" sh -c 'chmod 600 /private/'"$snapshot"
bash scripts/ci/summarize-junit.sh "$reports/junit/unit"
bash scripts/ci/summarize-junit.sh "$reports/junit/database"
printf 'Private initial configuration preservation/rehearsal: PASS. Reports: %s\n' "$reports"
printf 'Protected configuration snapshot retained in the selected private directory; never commit or upload it.\n'
