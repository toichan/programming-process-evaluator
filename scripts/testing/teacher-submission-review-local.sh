#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
mode=${1:-unit}
case "$mode" in unit|integration|browser) ;; *) echo "Usage: sh scripts/testing/teacher-submission-review-local.sh unit|integration|browser" >&2; exit 2 ;; esac
owner=$$
network=ppe-submission-review-test
builder=ppe-submission-review-builder
database=ppe-preparation-20261008-db-1
runtime=ppe-teacher-review-runtime
runner=ppe-submission-review-runner
http=ppe-submission-review-http-tests
reports="build/teacher-submission-review-validation/$mode/run-$owner"
for name in "$builder" "$database" "$runtime" "$runner" "$http"; do
  if docker container inspect "$name" >/dev/null 2>&1; then echo "Existing container $name; refusing to replace it." >&2; exit 1; fi
done
if docker network inspect "$network" >/dev/null 2>&1; then echo "Existing test network; refusing to replace it." >&2; exit 1; fi
cleanup() {
  for name in "$http" "$runtime" "$runner" "$builder" "$database"; do
    if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.submission-review.owner"}}' 2>/dev/null || true)" = "$owner" ]; then docker rm -fv "$name" >/dev/null; fi
  done
  if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.submission-review.owner"}}' 2>/dev/null || true)" = "$owner" ]; then docker network rm "$network" >/dev/null; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$reports"
set --
if [ "$mode" != unit ]; then
  docker network create --label ppe.submission-review.owner="$owner" "$network" >/dev/null
  docker run -d --name "$database" --label ppe.submission-review.owner="$owner" --network "$network" \
    --tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=submission-dummy-root \
    -e MYSQL_DATABASE=ppe_teacher_review_test_submission -e MYSQL_USER=submission_dummy -e MYSQL_PASSWORD=submission-dummy-password \
    programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
  ready=false
  for attempt in $(seq 1 40); do
    if docker exec "$database" sh -c 'MYSQL_PWD=submission-dummy-root mysql -uroot -e "SELECT 1 FROM information_schema.schemata WHERE schema_name='\''ppe_teacher_review_test_submission'\''" | grep -q "^1$"' >/dev/null 2>&1; then ready=true; break; fi
    sleep 1
  done
  if [ "$ready" != true ]; then echo "Isolated MySQL startup failed." >&2; exit 1; fi
  set -- --network "$network" -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_submission \
    -e DB_USER=root -e DB_PASSWORD=submission-dummy-root -e TEACHER_REVIEW_DB_TEST=true
fi
result=0
docker run --name "$builder" --label ppe.submission-review.owner="$owner" "$@" \
  -e SUBMISSION_TEST_MODE="$mode" --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  cp -R /source/src/. /workspace/src/
  cp /source/build.gradle /source/settings.gradle /workspace/
  if [ "$SUBMISSION_TEST_MODE" = unit ]; then
    gradle --offline --no-daemon clean test war --rerun-tasks
  else
    gradle --offline --no-daemon clean flywayMigrate
    gradle --offline --no-daemon test --tests "*TeacherReview*" --tests servlet.auth.ApplicationUrlContractTest \
      --tests dao.TeacherPermissionDaoTest --tests "*PythonRunner*Test" war --rerun-tasks
  fi
' || result=$?
docker cp "$builder:/workspace/build/test-results/test/." "$reports/"
if [ "$result" != 0 ]; then exit "$result"; fi
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"
if [ "$mode" != unit ]; then
  docker run -d --name "$runner" --label ppe.submission-review.owner="$owner" --network "$network" \
    -e PYTHON_RUNNER_TOKEN=submission-dummy-runner-token \
    --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
    --read-only --tmpfs /tmp:rw,size=8m --cap-drop ALL --security-opt no-new-privileges \
    programming-process-evaluator-python-runner >/dev/null
  set -- --network "$network"
  if [ "$mode" = browser ]; then set -- "$@" -p 127.0.0.1:18089:8080; fi
  docker run -d --name "$runtime" --label ppe.submission-review.owner="$owner" "$@" \
    -e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
    -e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
    -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_submission \
    -e DB_USER=submission_dummy -e DB_PASSWORD=submission-dummy-password \
    -e PYTHON_RUNNER_URL="http://$runner:8090" -e PYTHON_RUNNER_TOKEN=submission-dummy-runner-token \
    --mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
    --entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null
  docker run --name "$http" --label ppe.submission-review.owner="$owner" --network "$network" \
    -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_submission -e DB_USER=root -e DB_PASSWORD=submission-dummy-root \
    -e TEACHER_REVIEW_DB_TEST=true -e TEACHER_REVIEW_RUNTIME_TEST=true -e TEACHER_REVIEW_HTTP_BASE=http://ppe-teacher-review-runtime:8080 \
    --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
    cp -R /source/src/. /workspace/src/; cp /source/build.gradle /source/settings.gradle /workspace/
    gradle --offline --no-daemon clean test --tests servlet.teacher.TeacherReviewRuntimeTest --rerun-tasks
  ' || result=$?
  mkdir -p "$reports/http"; docker cp "$http:/workspace/build/test-results/test/." "$reports/http/"
  docker logs "$runtime" > "$reports/runtime.log" 2>&1
fi
if [ "$mode" = browser ] && [ "$result" = 0 ]; then
  echo "Synthetic browser fixture ready: http://teacher.localhost:18089/teacher/account/login"
  echo "Login review_teacher / ReviewDummy42!; interrupt to remove owned environment."
  while :; do sleep 1; done
fi
echo "Submission-review $mode result=$result; reports: $reports; isolated environment will be removed."
exit "$result"
