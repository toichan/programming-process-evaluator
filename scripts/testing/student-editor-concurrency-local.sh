#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

owner=$$
network=ppe-editor-concurrency-test
builder=ppe-editor-concurrency-builder
database=ppe-editor-concurrency-db
runtime=ppe-editor-concurrency-runtime
browser=ppe-editor-concurrency-browser
python_runner=ppe-editor-concurrency-python-runner
db_name=ppe_teacher_task_test_p20_concurrency
port=18099
teacher_password="P20Teacher_${owner}_42!"
student_password="P20Student_${owner}_42!"
reports=build/phase20-editor-concurrency-validation
war_path=$reports/ROOT.war

for name in "$builder" "$database" "$runtime" "$browser" "$python_runner"; do
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
	for name in "$browser" "$runtime" "$python_runner" "$builder" "$database"; do
		if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.editor-concurrency.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
			docker rm -fv "$name" >/dev/null
		fi
	done
	if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.editor-concurrency.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
		docker network rm "$network" >/dev/null
	fi
	rm -f "$war_path" "$reports/fixture.log"
	rmdir "$reports" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -p "$reports"
docker network create --label ppe.editor-concurrency.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.editor-concurrency.owner="$owner" --network "$network" \
	--tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=concurrency-dummy-root \
	-e MYSQL_DATABASE="$db_name" -e MYSQL_USER=concurrency_dummy -e MYSQL_PASSWORD=concurrency-dummy-password \
	programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null

ready=false
for attempt in $(seq 1 30); do
	if docker exec "$database" sh -c "MYSQL_PWD=concurrency-dummy-root mysql -uroot -e \"SELECT 1 FROM information_schema.schemata WHERE schema_name='$db_name'\" | grep -q '^1$'" >/dev/null 2>&1; then
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

fixture_result=0
docker run --name "$builder" --label ppe.editor-concurrency.owner="$owner" --network "$network" \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=root -e DB_PASSWORD=concurrency-dummy-root \
	-e TEACHER_TASK_DB_TEST=true -e TEACHER_TASK_EDITOR_BROWSER_FIXTURE=true \
	-e TEACHER_TASK_BROWSER_PASSWORD="$teacher_password" \
	-e TEACHER_TASK_STUDENT_BROWSER_PASSWORD="$student_password" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate test \
		--tests control.teacher.TeacherTaskDatabaseTest.createsRetainedEditorFixtureForPhase20BrowserAcceptance \
		war --rerun-tasks
	bash /source/scripts/ci/summarize-junit.sh /workspace/build/test-results/test
' >"$reports/fixture.log" 2>&1 || fixture_result=$?
cat "$reports/fixture.log"
if [ "$fixture_result" -ne 0 ]; then
	exit "$fixture_result"
fi

fixture=$(docker exec "$database" sh -c "MYSQL_PWD=concurrency-dummy-root mysql -N -B -uroot '$db_name' -e \"
	SELECT u.login_id, ca.task_class_assignment_id
	FROM users u
	JOIN student_class_memberships scm ON scm.student_user_id = u.user_id
	  AND scm.membership_status = 'active'
	JOIN task_class_assignments ca ON ca.classroom_id = scm.classroom_id
	JOIN tasks t ON t.task_id = ca.task_id
	WHERE u.user_type = 'student' AND u.account_status = 'active'
	  AND t.title = 'Phase 20 editor browser acceptance'
	LIMIT 1
\"")
student_login=$(printf '%s\n' "$fixture" | cut -f1)
assignment_id=$(printf '%s\n' "$fixture" | cut -f2)
if [ -z "$student_login" ] || [ -z "$assignment_id" ]; then
	echo "Could not resolve the synthetic editor fixture from its dedicated database." >&2
	exit 1
fi
printf 'Synthetic editor fixture ready: student=%s assignmentId=%s\n' "$student_login" "$assignment_id"
docker cp "$builder:/workspace/build/libs/ROOT.war" "$war_path"

docker run -d --name "$python_runner" --label ppe.editor-concurrency.owner="$owner" \
	--network "$network" -e PYTHON_RUNNER_TOKEN=concurrency-dummy-runner-token \
	--mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
	--read-only --tmpfs /tmp:rw,size=8m --cap-drop ALL --security-opt no-new-privileges \
	programming-process-evaluator-python-runner >/dev/null

docker run -d --name "$runtime" --label ppe.editor-concurrency.owner="$owner" \
	--network "$network" -p "127.0.0.1:$port:8080" \
	-e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
	-e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=concurrency_dummy -e DB_PASSWORD=concurrency-dummy-password \
	-e PYTHON_RUNNER_URL="http://$python_runner:8090" \
	-e PYTHON_RUNNER_TOKEN=concurrency-dummy-runner-token \
	--mount "type=bind,src=$(pwd)/$war_path,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
	--entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null

ready=false
for attempt in $(seq 1 60); do
	if curl --silent --fail --output /dev/null -H 'Host: student.localhost' "http://127.0.0.1:$port/student/account/login"; then
		ready=true
		break
	fi
	sleep 1
done
if [ "$ready" != true ]; then
	echo "Isolated Tomcat did not serve the student login page." >&2
	docker logs --tail 80 "$runtime"
	exit 1
fi

browser_result=0
docker run --name "$browser" --label ppe.editor-concurrency.owner="$owner" --network "$network" \
	-e PPE_P20_BASE_URL=http://student.localhost:8080 \
	-e PPE_P20_HOST_RESOLVER_RULES="MAP student.localhost $runtime" \
	-e PPE_P20_STUDENT_LOGIN="$student_login" -e PPE_P20_STUDENT_PASSWORD="$student_password" \
	-e PPE_P20_ASSIGNMENT_ID="$assignment_id" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" \
	--entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c '
	cd /source/e2e
	exec node node_modules/@playwright/test/cli.js test phase20/editor-simultaneous-save.spec.js \
		--output=/tmp/ppe-editor-concurrency-results
' || browser_result=$?
if [ "$browser_result" -ne 0 ]; then
	docker logs --tail 120 "$runtime" >&2
	docker exec "$runtime" sh -c 'for file in /usr/local/tomcat/logs/*; do echo "--- $file"; tail -n 120 "$file"; done' >&2
	exit "$browser_result"
fi
