#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
mode=${1:-unit}
case "$mode" in unit|integration|browser) ;; *) echo "Usage: sh scripts/testing/teacher-exercise-review-local.sh unit|integration|browser" >&2; exit 2 ;; esac
owner=$$
network=ppe-exercise-review-test
builder=ppe-exercise-review-builder
database=ppe-exercise-review-db
runtime=ppe-exercise-review-runtime
http=ppe-exercise-review-http-tests
reports="build/teacher-exercise-review-validation/$mode"
for name in "$builder" "$database" "$runtime" "$http"; do
	if docker container inspect "$name" >/dev/null 2>&1; then
		echo "Test container $name already exists; refusing to replace it." >&2; exit 1
	fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
	echo "Test network already exists; refusing to replace it." >&2; exit 1
fi
cleanup() {
	for name in "$http" "$runtime" "$builder" "$database"; do
		if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.exercise-review.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
			docker rm -fv "$name" >/dev/null
		fi
	done
	if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.exercise-review.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
		docker network rm "$network" >/dev/null
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$reports"
set --
if [ "$mode" != unit ]; then
	docker network create --label ppe.exercise-review.owner="$owner" "$network" >/dev/null
	docker run -d --name "$database" --label ppe.exercise-review.owner="$owner" --network "$network" \
		--tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=exercise-dummy-root \
		-e MYSQL_DATABASE=ppe_exercise_review_test -e MYSQL_USER=exercise_dummy -e MYSQL_PASSWORD=exercise-dummy-password \
		programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
	ready=false
	for attempt in $(seq 1 30); do
		if docker exec "$database" sh -c 'MYSQL_PWD=exercise-dummy-root mysql -uroot -e "SELECT 1 FROM information_schema.schemata WHERE schema_name='\''ppe_exercise_review_test'\''" | grep -q "^1$"' >/dev/null 2>&1; then
			ready=true; break
		fi
		sleep 1
	done
	if [ "$ready" != true ]; then echo "Isolated MySQL startup failed." >&2; docker logs --tail 30 "$database"; exit 1; fi
	set -- --network "$network" -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_exercise_review_test \
		-e DB_USER=root -e DB_PASSWORD=exercise-dummy-root -e TEACHER_EXERCISE_DB_TEST=true
fi
result=0
docker run --name "$builder" --label ppe.exercise-review.owner="$owner" "$@" \
	-e EXERCISE_TEST_MODE="$mode" --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	if [ "$EXERCISE_TEST_MODE" != unit ]; then
		gradle --offline --no-daemon flywayMigrate test --tests control.teacher.TeacherExerciseDatabaseTest war --rerun-tasks
	else
		gradle --offline --no-daemon test war --rerun-tasks
	fi
' || result=$?
docker cp "$builder:/workspace/build/test-results/test/." "$reports/"
if [ "$result" != 0 ]; then exit "$result"; fi
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"
if [ "$mode" != unit ]; then
	set -- --network "$network"
	if [ "$mode" = browser ]; then set -- "$@" -p 127.0.0.1:18090:8080; fi
	docker run -d --name "$runtime" --label ppe.exercise-review.owner="$owner" "$@" \
		-e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
		-e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
		-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_exercise_review_test \
		-e DB_USER=exercise_dummy -e DB_PASSWORD=exercise-dummy-password \
		--mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
		--entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null
	set -- --network "$network" -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_exercise_review_test \
		-e DB_USER=root -e DB_PASSWORD=exercise-dummy-root -e TEACHER_EXERCISE_DB_TEST=true
	docker run --name "$http" --label ppe.exercise-review.owner="$owner" "$@" \
		-e TEACHER_EXERCISE_RUNTIME_TEST=true -e TEACHER_EXERCISE_HTTP_BASE=http://ppe-exercise-review-runtime:8080 \
		--mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
		cp -R /source/src/. /workspace/src/
		cp /source/build.gradle /source/settings.gradle /workspace/
		gradle --offline --no-daemon test --tests servlet.teacher.TeacherExerciseRuntimeTest --rerun-tasks
	' || result=$?
	mkdir -p "$reports/http"
	docker cp "$http:/workspace/build/test-results/test/." "$reports/http/"
	docker logs "$runtime" > "$reports/runtime.log" 2>&1
fi
if [ "$mode" = browser ] && [ "$result" = 0 ]; then
	echo "Synthetic browser fixture ready at http://teacher.localhost:18090/teacher/account/login"
	echo "Login ex_teacher / ExerciseDummy42!; interrupt this script to remove its isolated environment."
	while :; do sleep 1; done
fi
echo "Exercise-review $mode result=$result; reports: $reports; isolated containers/network will be removed."
exit "$result"
