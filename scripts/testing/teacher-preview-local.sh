#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
reports=build/teacher-preview-validation
mkdir -p "$reports"
network=ppe-req158-network
database=ppe-exercise-review-db
builder=ppe-req158-builder
runtime=ppe-exercise-review-runtime
runner=ppe-req158-runner
http=ppe-req158-http
owner=$$
for name in "$database" "$builder" "$runtime" "$runner" "$http"; do
  if docker container inspect "$name" >/dev/null 2>&1; then echo "Container $name exists; refusing to replace it." >&2; exit 1; fi
done
if docker network inspect "$network" >/dev/null 2>&1; then echo "Network exists; refusing to replace it." >&2; exit 1; fi
cleanup() {
  for name in "$http" "$runtime" "$runner" "$builder" "$database"; do
    if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.req158.owner"}}' 2>/dev/null || true)" = "$owner" ]; then docker rm -fv "$name" >/dev/null; fi
  done
  if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.req158.owner"}}' 2>/dev/null || true)" = "$owner" ]; then docker network rm "$network" >/dev/null; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker network create --label ppe.req158.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.req158.owner="$owner" --network "$network" \
  -e MYSQL_ROOT_PASSWORD=exercise-dummy-root -e MYSQL_DATABASE=ppe_exercise_review_test \
  -e MYSQL_USER=exercise_dummy -e MYSQL_PASSWORD=exercise-dummy-password \
  programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
ready=false
for attempt in $(seq 1 40); do
  if docker exec "$database" sh -c 'MYSQL_PWD=exercise-dummy-root mysql -uroot -e "SELECT 1 FROM information_schema.schemata WHERE schema_name='\''ppe_exercise_review_test'\''" | grep -q "^1$"' >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated MySQL unavailable." >&2; exit 1; fi
docker run -d --name "$runner" --label ppe.req158.owner="$owner" --network "$network" \
  -e PYTHON_RUNNER_TOKEN=req158-synthetic-token \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
  --cap-drop ALL --security-opt no-new-privileges programming-process-evaluator-python-runner >/dev/null
docker run --name "$builder" --label ppe.req158.owner="$owner" --network "$network" \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_exercise_review_test -e DB_USER=root -e DB_PASSWORD=exercise-dummy-root \
  -e TEACHER_EXERCISE_DB_TEST=true -e TEACHER_SUBMISSION_RUNNER_TEST=true \
  -e PYTHON_RUNNER_URL="http://$runner:8090" -e PYTHON_RUNNER_TOKEN=req158-synthetic-token \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  cp -R /source/src/. /workspace/src/ && cp /source/build.gradle /source/settings.gradle /workspace/ &&
  gradle --offline --no-daemon flywayMigrate test --tests control.teacher.TeacherExerciseDatabaseTest \
    --tests control.teacher.TeacherPreviewExecutionTest --tests control.teacher.TeacherSubmissionExecutionRegistryTest \
    --tests control.teacher.TeacherExerciseControlTest --tests control.teacher.TeacherReviewControlTest \
    --tests servlet.teacher.TeacherExerciseServletTest --tests servlet.teacher.TeacherReviewServletTest \
    --tests servlet.teacher.TeacherPreviewUiContractTest war --console plain
  ' > "$reports/build.log" 2>&1
docker cp "$builder:/workspace/build/test-results/test/." "$reports/unit"
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"
docker run -d --name "$runtime" --label ppe.req158.owner="$owner" --network "$network" -p 127.0.0.1:18090:8080 \
  -e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
  -e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_exercise_review_test -e DB_USER=exercise_dummy -e DB_PASSWORD=exercise-dummy-password \
  -e PYTHON_RUNNER_URL="http://$runner:8090" -e PYTHON_RUNNER_TOKEN=req158-synthetic-token \
  --mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
  --entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null
ready=false
for attempt in $(seq 1 40); do
  if curl -fsS -H 'Host: teacher.localhost:18090' http://127.0.0.1:18090/teacher/account/login >/dev/null; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated Tomcat unavailable." >&2; exit 1; fi
http_result=0
docker run --name "$http" --label ppe.req158.owner="$owner" --network "$network" \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_exercise_review_test -e DB_USER=root -e DB_PASSWORD=exercise-dummy-root \
  -e TEACHER_EXERCISE_DB_TEST=true -e TEACHER_EXERCISE_RUNTIME_TEST=true -e TEACHER_EXERCISE_HTTP_BASE="http://$runtime:8080" \
  -e TEACHER_PREVIEW_RUNTIME_TEST=true \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  cp -R /source/src/. /workspace/src/ && cp /source/build.gradle /source/settings.gradle /workspace/ &&
  gradle --offline --no-daemon test --tests servlet.teacher.TeacherExerciseRuntimeTest --tests servlet.teacher.TeacherPreviewRuntimeTest --console plain
  ' > "$reports/http.log" 2>&1 || http_result=$?
docker cp "$http:/workspace/build/test-results/test/." "$reports/http"
docker logs "$runtime" > "$reports/runtime.log" 2>&1
if [ "$http_result" -ne 0 ]; then
  echo "Preview HTTP validation failed; reports $reports." >&2
  exit "$http_result"
fi
echo "Synthetic fixture ready: http://teacher.localhost:18090/teacher/account/login (ex_teacher / ExerciseDummy42!)."
echo "Preview unit+runner+DB+HTTP tests passed; reports $reports. Stop this script to clean its owned containers."
while :; do sleep 1; done
