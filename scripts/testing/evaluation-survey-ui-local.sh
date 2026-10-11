#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
reports="${UI_VALIDATION_REPORTS:-build/evaluation-survey-ui-validation}"
mkdir -p "$reports"
owner=$$
network=ppe-159-network
database=ppe-159-db
builder=ppe-159-builder
runtime=ppe-159-runtime
http=ppe-159-http
runner=ppe-159-runner
for name in "$database" "$builder" "$runtime" "$http" "$runner"; do
  if docker container inspect "$name" >/dev/null 2>&1; then echo "Container $name exists; refusing to replace it." >&2; exit 1; fi
done
if docker network inspect "$network" >/dev/null 2>&1; then echo "Network exists; refusing to replace it." >&2; exit 1; fi
cleanup() {
  for name in "$http" "$runtime" "$builder" "$runner" "$database"; do
    if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.req159.owner"}}' 2>/dev/null || true)" = "$owner" ]; then docker rm -fv "$name" >/dev/null; fi
  done
  if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.req159.owner"}}' 2>/dev/null || true)" = "$owner" ]; then docker network rm "$network" >/dev/null; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker network create --label ppe.req159.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.req159.owner="$owner" --network "$network" \
  --network-alias ppe-preparation-20261008-db-1 \
  -e MYSQL_ROOT_PASSWORD=ui-synthetic-root -e MYSQL_DATABASE=ppe_teacher_review_test_ui159 \
  programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
ready=false
for attempt in $(seq 1 40); do
  if docker exec "$database" sh -c 'MYSQL_PWD=ui-synthetic-root mysql -uroot -e "SELECT 1"' >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated database unavailable." >&2; exit 1; fi
build_result=0
docker run --name "$builder" --label ppe.req159.owner="$owner" --network "$network" \
  -e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_ui159 \
  -e DB_USER=root -e DB_PASSWORD=ui-synthetic-root -e TEACHER_REVIEW_DB_TEST=true \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  cp -R /source/src/. /workspace/src/ && cp /source/build.gradle /source/settings.gradle /workspace/ &&
  gradle --offline --no-daemon flywayMigrate test war --console plain
  ' > "$reports/build.log" 2>&1 || build_result=$?
docker cp "$builder:/workspace/build/test-results/test/." "$reports/unit"
if [ "$build_result" -ne 0 ]; then echo "Build/tests failed; see $reports/build.log." >&2; exit "$build_result"; fi
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"
docker run -d --name "$runner" --label ppe.req159.owner="$owner" --network "$network" \
  --read-only --tmpfs /tmp:size=8m,mode=1777 --cap-drop ALL --security-opt no-new-privileges:true \
  -e RUNNER_PORT=8090 programming-process-evaluator-python-runner >/dev/null
docker run -d --name "$runtime" --label ppe.req159.owner="$owner" --network "$network" -p 127.0.0.1:18092:8080 \
  -e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
  -e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
  -e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_ui159 \
  -e DB_USER=root -e DB_PASSWORD=ui-synthetic-root \
  -e STUDENT_CREDENTIAL_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  -e PYTHON_RUNNER_URL=http://ppe-159-runner:8090 \
  --mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
  --entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null
ready=false
for attempt in $(seq 1 40); do
  if curl -fsS -H 'Host: student.localhost:18092' http://127.0.0.1:18092/student/account/login >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated Tomcat unavailable." >&2; exit 1; fi
curl -fsS -H 'Host: student.localhost:18092' http://127.0.0.1:18092/student/account/login >/dev/null
docker exec "$runtime" curl -fsS http://ppe-159-runner:8090/health > "$reports/runner-health.json"
http_result=0
docker run --name "$http" --label ppe.req159.owner="$owner" --network "$network" \
  -e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_ui159 \
  -e DB_USER=root -e DB_PASSWORD=ui-synthetic-root -e TEACHER_REVIEW_DB_TEST=true \
  -e EVALUATION_SURVEY_RUNTIME_TEST=true -e EVALUATION_SURVEY_HTTP_BASE=http://ppe-159-runtime:8080 \
  -e UI160_RUNTIME_TEST="${UI160_RUNTIME_TEST:-false}" \
  -e STUDENT_CREDENTIAL_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  cp -R /source/src/. /workspace/src/ && cp /source/build.gradle /source/settings.gradle /workspace/ &&
  gradle --offline --no-daemon test --tests servlet.student.StudentEvaluationSurveyRuntimeTest --console plain &&
  cp -R build/test-results/test /tmp/survey-http-results &&
  gradle --offline --no-daemon test --tests servlet.student.StudentTaskMenuRuntimeTest \
    --tests servlet.student.SurveyFourStepRuntimeTest --tests servlet.teacher.LocalUi160RuntimeTest \
    --tests servlet.student.StudentNotificationRuntimeTest --console plain &&
  cp /tmp/survey-http-results/TEST-*.xml build/test-results/test/
  ' > "$reports/http.log" 2>&1 || http_result=$?
docker cp "$http:/workspace/build/test-results/test/." "$reports/http"
docker logs "$runtime" > "$reports/runtime.log" 2>&1
docker cp "$runtime:/usr/local/tomcat/logs/." "$reports/tomcat"
if [ "$http_result" -ne 0 ]; then echo "HTTP validation failed; see $reports/http.log." >&2; exit "$http_result"; fi
echo "Isolated evaluation/survey acceptance ready on18092; synthetic review_teacher/review_student use ReviewDummy42!."
echo "Tests passed. Stop this script to clean only its owned resources."
while :; do sleep 1; done
