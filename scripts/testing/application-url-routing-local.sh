#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

owner=$$
network=ppe-url-routing-test
builder=ppe-url-routing-builder
database=ppe-url-routing-db
runtime=ppe-url-routing-runtime
http=ppe-url-routing-http-tests
db_name=ppe_url_routing_test_local
reports=build/application-url-routing-validation

for name in "$builder" "$database" "$runtime" "$http"; do
	if docker container inspect "$name" >/dev/null 2>&1; then
		echo "Test container $name already exists; refusing to replace it." >&2
		exit 1
	fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
	echo "Test network $network already exists; refusing to replace it." >&2
	exit 1
fi

cleanup() {
	for name in "$http" "$runtime" "$builder" "$database"; do
		if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.url-routing.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
			docker rm -fv "$name" >/dev/null
		fi
	done
	if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.url-routing.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
		docker network rm "$network" >/dev/null
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -p "$reports"
docker network create --label ppe.url-routing.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.url-routing.owner="$owner" --network "$network" \
	--tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=url-routing-dummy-root \
	-e MYSQL_DATABASE="$db_name" -e MYSQL_USER=url_routing_dummy -e MYSQL_PASSWORD=url-routing-dummy-password \
	programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null

ready=false
for attempt in $(seq 1 40); do
	if docker exec "$database" sh -c "MYSQL_PWD=url-routing-dummy-root mysql -uroot -e \"SELECT 1 FROM information_schema.schemata WHERE schema_name='$db_name'\" | grep -q '^1$'" >/dev/null 2>&1; then
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

docker run --name "$builder" --label ppe.url-routing.owner="$owner" --network "$network" \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=root -e DB_PASSWORD=url-routing-dummy-root \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	gradle --offline --no-daemon flywayMigrate war --console plain
'
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"

docker run -d --name "$runtime" --label ppe.url-routing.owner="$owner" --network "$network" \
	-e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
	-e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=url_routing_dummy -e DB_PASSWORD=url-routing-dummy-password \
	-e STUDENT_CREDENTIAL_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
	--mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
	--entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null

test_status=0
docker run --name "$http" --label ppe.url-routing.owner="$owner" --network "$network" \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=root -e DB_PASSWORD=url-routing-dummy-root \
	-e URL_ROUTING_HTTP_TEST=true -e URL_ROUTING_HTTP_BASE=http://ppe-url-routing-runtime:8080 \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	ready=false
	for attempt in $(seq 1 60); do
		if curl -fsS -H "Host: teacher.localhost:8080" http://ppe-url-routing-runtime:8080/teacher/account/login >/dev/null 2>&1; then
			ready=true
			break
		fi
		sleep 1
	done
	if [ "$ready" != true ]; then
		echo "Isolated Tomcat did not serve the teacher login page." >&2
		exit 1
	fi
	gradle --offline --no-daemon test --tests servlet.auth.ApplicationUrlRuntimeTest --console plain
' || test_status=$?
docker cp "$http:/workspace/build/test-results/test" "$reports/"
docker cp "$http:/workspace/build/reports/tests/test" "$reports/"
docker logs "$runtime" > "$reports/runtime.log" 2>&1
if [ "$test_status" -ne 0 ]; then
	echo "URL-routing runtime tests failed with exit code $test_status; reports and runtime log: $reports" >&2
	exit "$test_status"
fi
echo "URL-routing runtime tests passed; reports: $reports; isolated containers/network will be removed."
