#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

owner=$$
network=ppe-distribution-ui-test
database=ppe-distribution-ui-db
builder=ppe-distribution-ui-builder
runtime=ppe-distribution-ui-runtime
browser=ppe-distribution-ui-browser
db_name=ppe_teacher_distribution_test_ui
reports="build/teacher-distribution-ui-validation/run-$owner"
for name in "$database" "$builder" "$runtime" "$browser"; do
  if docker container inspect "$name" >/dev/null 2>&1; then
    echo "Existing container $name; refusing to replace it." >&2
    exit 1
  fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
  echo "Existing distribution-ui test network; refusing to replace it." >&2
  exit 1
fi
mkdir -p "$reports"
cleanup() {
  if docker container inspect "$runtime" >/dev/null 2>&1; then
    docker logs "$runtime" > "$reports/runtime.log" 2>&1
  fi
  for name in "$browser" "$runtime" "$builder" "$database"; do
    if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.distribution-ui.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
      docker rm -fv "$name" >/dev/null
    fi
  done
  if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.distribution-ui.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
    docker network rm "$network" >/dev/null
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker network create --label ppe.distribution-ui.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.distribution-ui.owner="$owner" --network "$network" \
  --tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=distribution-dummy-root \
  -e MYSQL_DATABASE="$db_name" -e MYSQL_USER=distribution_dummy -e MYSQL_PASSWORD=distribution-dummy-password \
  programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
ready=false
for attempt in $(seq 1 40); do
  if docker exec "$database" sh -c 'MYSQL_PWD=distribution-dummy-root mysql -uroot -Nse "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated MySQL startup failed." >&2; exit 1; fi
result=0
docker run --name "$builder" --label ppe.distribution-ui.owner="$owner" --network "$network" \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" -e DB_USER=root -e DB_PASSWORD=distribution-dummy-root \
  -e TEACHER_DISTRIBUTION_DB_TEST=true -e TEACHER_DISTRIBUTION_BROWSER_FIXTURE=true \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  set -eu
  cp -R /source/src/. /workspace/src/
  cp /source/build.gradle /source/settings.gradle /workspace/
  mkdir -p build/phase20-distribution-db-test-results build/test-results/test
  gradle --offline --no-daemon flywayMigrate flywayValidate test \
    --tests control.teacher.TeacherDistributionDatabaseTest.savesDistributesAppendsIdempotentlyAndResumesScheduledTargets \
    --console plain
  mkdir -p build/phase20-distribution-db-test-results
  cp -R build/test-results/test/. build/phase20-distribution-db-test-results/
  gradle --offline --no-daemon test \
    --tests control.teacher.TeacherDistributionDatabaseTest.createsRetainedPeerTemplateForPhase20BrowserAcceptance \
    war --console plain
' > "$reports/build.log" 2>&1 || result=$?
docker cp "$builder:/workspace/build/phase20-distribution-db-test-results/." "$reports/junit-db-contract"
docker cp "$builder:/workspace/build/test-results/test/." "$reports/junit-browser-fixture"
if [ "$result" != 0 ]; then cat "$reports/build.log"; exit "$result"; fi
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"
docker cp "$builder:/workspace/build/phase20-distribution-fixture.properties" "$reports/fixture.properties"
template_id=$(sed -n 's/^templateId=//p' "$reports/fixture.properties")
case "$template_id" in ''|*[!0-9]*) echo "Invalid synthetic template ID." >&2; exit 1 ;; esac
distribution_id=$(sed -n 's/^distributionId=//p' "$reports/fixture.properties")
case "$distribution_id" in ''|*[!0-9]*) echo "Invalid synthetic distribution ID." >&2; exit 1 ;; esac
target_id=$(sed -n 's/^targetId=//p' "$reports/fixture.properties")
case "$target_id" in ''|*[!0-9]*) echo "Invalid synthetic distribution target ID." >&2; exit 1 ;; esac
scheduled_at_display=$(sed -n 's/^scheduledAtDisplay=//p' "$reports/fixture.properties")
case "$scheduled_at_display" in ????-??-??T??:??) ;; *) echo "Invalid synthetic scheduled display datetime." >&2; exit 1 ;; esac
docker run -d --name "$runtime" --label ppe.distribution-ui.owner="$owner" --network "$network" \
  -e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$' \
  -e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" -e DB_USER=distribution_dummy -e DB_PASSWORD=distribution-dummy-password \
  --mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
  --entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null
ready=false
for attempt in $(seq 1 60); do
  if docker exec "$runtime" curl -fsS -o /dev/null -H 'Host: teacher.localhost' http://127.0.0.1:8080/teacher/account/login; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated Tomcat startup failed." >&2; exit 1; fi
docker run --name "$browser" --label ppe.distribution-ui.owner="$owner" --network "$network" \
  -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e PPE_P20_TEACHER_BASE_URL=http://teacher.localhost:8080 \
  -e PPE_P20_HOST_RESOLVER_RULES="MAP teacher.localhost $runtime, MAP student.localhost $runtime" \
  -e PPE_P20_DISTRIBUTION_TEMPLATE_ID="$template_id" -e PPE_P20_DISTRIBUTION_PASSWORD='Synthetic12!Aa' \
  -e PPE_P20_DISTRIBUTION_ID="$distribution_id" -e PPE_P20_DISTRIBUTION_TARGET_ID="$target_id" \
  -e PPE_P20_DISTRIBUTION_SCHEDULED_AT_DISPLAY="$scheduled_at_display" \
  -e PPE_P20_DISTRIBUTION_PEER_LOGIN=teacher-distribution-peer \
  -e PPE_P20_DISTRIBUTION_OWNER_LOGIN=teacher-distribution-test \
  -e PPE_P20_DISTRIBUTION_FOREIGN_LOGIN=teacher-distribution-foreign \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" \
  --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c '
  cd /source/e2e
  exec node node_modules/@playwright/test/cli.js test phase20/teacher-distribution-peer-edit.spec.js \
    --output=/tmp/ppe-distribution-ui-results
' > "$reports/browser.log" 2>&1 || result=$?
cat "$reports/browser.log"
docker cp "$browser:/tmp/ppe-distribution-ui-results/." "$reports/browser"
printf 'Distribution-ui exit=%s; reports: %s\n' "$result" "$reports"
exit "$result"
