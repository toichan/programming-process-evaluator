#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

mode=${1:-all}
case "$mode" in all|datetime) ;; *) echo "Usage: sh scripts/testing/teacher-prompt-generation-local.sh [all|datetime]" >&2; exit 2 ;; esac

owner=$$
network=ppe-prompt-generation-test
database=ppe-p20-prompt-generation-db-1
builder=ppe-prompt-generation-builder
runtime=ppe-prompt-generation-runtime
proxy=ppe-prompt-generation-proxy
provider=gemini-mock
browser=ppe-prompt-generation-browser
reports="build/teacher-prompt-generation-validation/run-$owner"
db_name=ppe_teacher_task_test_prompt_generation
password='PromptQueueDummy42!'
for name in "$database" "$builder" "$runtime" "$proxy" "$provider" "$browser"; do
  if docker container inspect "$name" >/dev/null 2>&1; then
    echo "Existing container $name; refusing to replace it." >&2
    exit 1
  fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
  echo "Existing prompt-generation test network; refusing to replace it." >&2
  exit 1
fi
mkdir -p "$reports"
cleanup() {
  if docker container inspect "$runtime" >/dev/null 2>&1; then
    docker logs "$runtime" > "$reports/runtime.log" 2>&1
  fi
  if docker container inspect "$provider" >/dev/null 2>&1; then
    docker logs "$provider" > "$reports/provider.log" 2>&1
  fi
  if docker container inspect "$proxy" >/dev/null 2>&1; then
    docker logs "$proxy" > "$reports/proxy.log" 2>&1
  fi
  for name in "$browser" "$proxy" "$runtime" "$provider" "$builder" "$database"; do
    if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.prompt-generation.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
      docker rm -fv "$name" >/dev/null
    fi
  done
  if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.prompt-generation.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
    docker network rm "$network" >/dev/null
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker network create --label ppe.prompt-generation.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.prompt-generation.owner="$owner" --network "$network" \
  --tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD=prompt-dummy-root \
  -e MYSQL_DATABASE="$db_name" -e MYSQL_USER=prompt_dummy -e MYSQL_PASSWORD=prompt-dummy-password \
  programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null
ready=false
for attempt in $(seq 1 40); do
  if docker exec "$database" sh -c 'MYSQL_PWD=prompt-dummy-root mysql -uroot -Nse "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated MySQL startup failed." >&2; exit 1; fi

result=0
docker run --name "$builder" --label ppe.prompt-generation.owner="$owner" --network "$network" \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" -e DB_USER=root -e DB_PASSWORD=prompt-dummy-root \
  -e TEACHER_TASK_DB_TEST=true -e TEACHER_TASK_BROWSER_FIXTURE=true -e TEACHER_TASK_BROWSER_PASSWORD="$password" \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
  set -eu
  cp -R /source/src/. /workspace/src/
  cp /source/build.gradle /source/settings.gradle /workspace/
  mkdir -p build/phase20-task-date-test-results
  gradle --offline --no-daemon flywayMigrate test \
    --tests control.teacher.TeacherTaskDatabaseTest.extendsExpiredAssignmentAndAddsAClassWithoutChangingExistingHistory \
    --console plain
  cp -R build/test-results/test/. build/phase20-task-date-test-results/
  gradle --offline --no-daemon test \
    --tests control.teacher.TeacherTaskDatabaseTest.createsRetainedSyntheticTeacherFixtureForIsolatedBrowserAcceptance \
    war --console plain
' > "$reports/build.log" 2>&1 || result=$?
docker cp "$builder:/workspace/build/phase20-task-date-test-results/." "$reports/junit-datetime-contract"
docker cp "$builder:/workspace/build/test-results/test/." "$reports/junit-browser-fixture"
if [ "$result" != 0 ]; then cat "$reports/build.log"; exit "$result"; fi
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"
docker cp "$builder:/workspace/build/phase20-prompt-datetime-fixture.properties" "$reports/datetime-fixture.properties"
prompt_task_id=$(sed -n 's/^taskId=//p' "$reports/datetime-fixture.properties")
case "$prompt_task_id" in ''|*[!0-9]*) echo "Invalid synthetic prompt task ID." >&2; exit 1 ;; esac
prompt_version_id=$(sed -n 's/^promptVersionId=//p' "$reports/datetime-fixture.properties")
case "$prompt_version_id" in ''|*[!0-9]*) echo "Invalid synthetic prompt version ID." >&2; exit 1 ;; esac
preview_code=$(sed -n 's/^previewCode=//p' "$reports/datetime-fixture.properties")
case "$preview_code" in ????????-????-????-????-????????????) ;; *) echo "Invalid synthetic preview code." >&2; exit 1 ;; esac
docker run -d --name "$provider" --label ppe.prompt-generation.owner="$owner" --network "$network" \
  --mount "type=bind,src=$(pwd)/e2e/fixtures/prompt-provider.js,dst=/provider.js,readonly" \
  --entrypoint node mcr.microsoft.com/playwright:v1.64.0-noble /provider.js >/dev/null
mkdir "$reports/tls"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -keyout "$reports/tls/key.pem" -out "$reports/tls/cert.pem" \
  -subj /CN=teacher.localhost -addext 'subjectAltName=DNS:teacher.localhost,DNS:student.localhost' \
  > "$reports/tls-generation.log" 2>&1
chmod 0444 "$reports/tls/key.pem" "$reports/tls/cert.pem"
docker run -d --name "$proxy" --label ppe.prompt-generation.owner="$owner" --network "$network" \
  -p 127.0.0.1:18101:8443 --user 101:101 --read-only --tmpfs /tmp:rw,size=8m,uid=101,gid=101,mode=700 \
  --cap-drop ALL --security-opt no-new-privileges \
  --mount "type=bind,src=$(pwd)/e2e/fixtures/prompt-proxy.conf,dst=/etc/nginx/nginx.conf,readonly" \
  --mount "type=bind,src=$(pwd)/$reports/tls,dst=/tls,readonly" \
  --entrypoint nginx nginx:1.28.2-alpine -g 'daemon off;' >/dev/null
proxy_ip=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$proxy")
trusted_proxy_regex=$(printf '%s' "$proxy_ip" | sed 's/\./\\./g')
docker run -d --name "$runtime" --label ppe.prompt-generation.owner="$owner" --network "$network" \
  -e PPE_ENV=simulation -e "CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$trusted_proxy_regex\$" \
  -e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
  -e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" -e DB_USER=prompt_dummy -e DB_PASSWORD=prompt-dummy-password \
  -e GEMINI_API_KEY=synthetic-prompt-acceptance -e GEMINI_API_BASE_URI=http://gemini-mock:8099/ \
  --mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
  --entrypoint sh ppe-app:local -c 'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null
ready=false
for attempt in $(seq 1 60); do
  if [ "$(docker inspect --format '{{.State.Running}}' "$proxy")" != true ]; then
    echo "Isolated HTTPS proxy startup failed." >&2
    docker logs "$proxy" >&2
    exit 1
  fi
  if curl --noproxy '*' -kfsS -o /dev/null --resolve teacher.localhost:18101:127.0.0.1 \
      -w '%{http_code}\n' https://teacher.localhost:18101/teacher/account/login \
      >> "$reports/readiness.log" 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Isolated Tomcat startup failed." >&2; exit 1; fi
docker exec "$provider" node -e '
fetch("http://127.0.0.1:8099/health").then(async response => {
  if (!response.ok || !(await response.json()).ok) process.exitCode = 1;
}).catch(error => { console.error(error.message); process.exitCode = 1; });
'
docker run --name "$browser" --label ppe.prompt-generation.owner="$owner" --network "$network" \
  -e PPE_P20_BASE_URL=https://teacher.localhost:8443 -e PPE_P20_IGNORE_HTTPS_ERRORS=true \
  -e PPE_P20_HOST_RESOLVER_RULES="MAP teacher.localhost $proxy, MAP student.localhost $proxy" \
  -e PPE_P20_PROMPT_PASSWORD="$password" \
  -e PPE_P20_PROMPT_TASK_ID="$prompt_task_id" -e PPE_P20_PROMPT_VERSION_ID="$prompt_version_id" \
  -e PPE_P20_REEVALUATION_PREVIEW_CODE="$preview_code" \
  -e PROMPT_GENERATION_MODE="$mode" \
  --mount "type=bind,src=$(pwd),dst=/source,readonly" \
  --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c '
  cd /source/e2e
  if [ "$PROMPT_GENERATION_MODE" = datetime ]; then
    exec node node_modules/@playwright/test/cli.js test phase20/teacher-datetime-contract.spec.js \
      --output=/tmp/ppe-prompt-generation-results
  else
    exec node node_modules/@playwright/test/cli.js test \
      phase20/teacher-datetime-contract.spec.js phase20/prompt-generation-queue.spec.js --workers=1 \
      --output=/tmp/ppe-prompt-generation-results
  fi
' > "$reports/browser.log" 2>&1 || result=$?
cat "$reports/browser.log"
docker cp "$browser:/tmp/ppe-prompt-generation-results/." "$reports/browser"
if [ "$mode" = all ]; then
docker exec "$provider" node -e '
fetch("http://127.0.0.1:8099/stats").then(async response => {
  const stats = await response.json();
  console.log(JSON.stringify(stats));
  if (!response.ok || stats.generations !== 2
      || stats.requests.map(request => request.stage).join(",") !== "fluctuations,examples") {
    process.exitCode = 1;
  }
}).catch(error => { console.error(error.message); process.exitCode = 1; });
' > "$reports/provider-stats.json" || result=$?
if [ "$result" = 0 ]; then
  docker exec "$database" sh -c 'MYSQL_PWD=prompt-dummy-root mysql -uroot -N "$MYSQL_DATABASE" -e "
    SELECT p.prompt_status, p.fluctuation_generation_status, p.evaluation_examples_status,
           e.example_status, JSON_LENGTH(e.example_output, '\''$.simulated_results'\'')
    FROM prompt_versions p JOIN evaluation_examples e USING (prompt_version_id)
    WHERE p.common_prompt LIKE '\''Synthetic queue acceptance:%'\'';
  "' > "$reports/db-readback.tsv"
  if ! grep -qx "$(printf 'configured\tcompleted\tcompleted\tactive\t2')" "$reports/db-readback.tsv"; then
    echo "Unexpected persisted prompt-generation state." >&2
    result=1
  fi
fi
fi
printf 'Prompt-generation exit=%s; reports: %s\n' "$result" "$reports"
exit "$result"
