#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

owner=$$
network=ppe-teacher-revocation-test
builder=ppe-teacher-revocation-builder
database=ppe-teacher-revocation-db
runtime=ppe-teacher-revocation-runtime
browser=ppe-teacher-revocation-browser
db_name=ppe_teacher_test_p20revocation
port=18098
password=${PPE_P20_ADMIN_PASSWORD:-SyntheticAdmin42!}
war_path=build/teacher-account-revocation-validation/ROOT.war
fixture_path=build/phase20-admin-management-browser-fixture.properties

for name in "$builder" "$database" "$runtime" "$browser"; do
	if docker container inspect "$name" >/dev/null 2>&1; then
		echo "Test container $name already exists; refusing to replace it." >&2
		exit 1
	fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
	echo "Test network $network already exists; refusing to replace it." >&2
	exit 1
fi
if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
	echo "Test port $port is already in use; refusing to continue." >&2
	exit 1
fi

cleanup() {
	for name in "$browser" "$runtime" "$builder" "$database"; do
		if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.teacher-revocation.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
			docker rm -fv "$name" >/dev/null
		fi
	done
	if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.teacher-revocation.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
		docker network rm "$network" >/dev/null
	fi
	if [ -f "$war_path" ]; then rm -f "$war_path"; fi
	if [ -f "$fixture_path" ]; then rm -f "$fixture_path"; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -p "$(dirname "$war_path")"
docker network create --label ppe.teacher-revocation.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.teacher-revocation.owner="$owner" --network "$network" \
	--tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=revocation-dummy-root \
	-e MYSQL_DATABASE="$db_name" -e MYSQL_USER=revocation_dummy -e MYSQL_PASSWORD=revocation-dummy-password \
	programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null

ready=false
for attempt in $(seq 1 30); do
	if docker exec "$database" sh -c "MYSQL_PWD=revocation-dummy-root mysql -uroot -e \"SELECT 1 FROM information_schema.schemata WHERE schema_name='$db_name'\" | grep -q '^1$'" >/dev/null 2>&1; then
		ready=true
		break
	fi
	sleep 1
done
if [ "$ready" != true ]; then
	echo "Isolated MySQL startup failed." >&2
	docker logs --tail 30 "$database"
	exit 1
fi

docker run --name "$builder" --label ppe.teacher-revocation.owner="$owner" --network "$network" \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=root -e DB_PASSWORD=revocation-dummy-root \
	-e TEACHER_ACCOUNT_DB_TEST=true -e TEACHER_ACCOUNT_BROWSER_FIXTURE=true \
	-e PPE_P20_ADMIN_PASSWORD="$password" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate test \
		--tests control.admin.TeacherAccountDatabaseTest.createsRetainedAdminManagementBrowserFixture \
		war --rerun-tasks
	bash /source/scripts/ci/summarize-junit.sh /workspace/build/test-results/test
'
docker cp "$builder:/workspace/$fixture_path" "$fixture_path"
docker cp "$builder:/workspace/build/libs/ROOT.war" "$war_path"

docker run -d --name "$runtime" --label ppe.teacher-revocation.owner="$owner" \
	--network "$network" -p "127.0.0.1:$port:8080" \
	-e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
	-e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=revocation_dummy -e DB_PASSWORD=revocation-dummy-password \
	--mount "type=bind,src=$(pwd)/$war_path,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
	--entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null

ready=false
for attempt in $(seq 1 60); do
	if curl --silent --fail --output /dev/null -H 'Host: teacher.localhost' "http://127.0.0.1:$port/teacher/account/login"; then
		ready=true
		break
	fi
	sleep 1
done
if [ "$ready" != true ]; then
	echo "Isolated Tomcat did not serve the teacher login page." >&2
	docker logs --tail 80 "$runtime"
	exit 1
fi

docker run --name "$browser" --label ppe.teacher-revocation.owner="$owner" --network "$network" \
	-e PPE_P20_BASE_URL=http://teacher.localhost:8080 \
	-e PPE_P20_HOST_RESOLVER_RULES="MAP teacher.localhost $runtime, MAP student.localhost $runtime" \
	-e PPE_P20_ADMIN_PASSWORD="$password" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" \
	--entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c '
	cd /source/e2e
	exec node node_modules/@playwright/test/cli.js test phase20/admin-teacher-management.spec.js \
		--output=/tmp/ppe-teacher-account-revocation-results
'
