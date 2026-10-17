#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."

mode=${1:-all}
case "$mode" in
	all|survey-sql) ;;
	*)
		echo "Usage: $0 [all|survey-sql]" >&2
		exit 2
		;;
esac

owner=$$
network=ppe-learning-flow-test
database=ppe-learning-flow-db
builder=ppe-learning-flow-builder
runtime=ppe-learning-flow-runtime
runner=ppe-learning-flow-runner
provider=ppe-learning-flow-provider
proxy=ppe-learning-flow-proxy
browser=ppe-learning-flow-browser
port=18082
db_name=ppe_learning_flow_test_$owner
rubric_db=ppe_exercise_test_$owner
reports="build/learning-flow-validation/run-$owner"
root_password=flow-dummy-root
app_password=flow-dummy-app-password
student_password='SyntheticLearningChanged2026!Aa'
teacher_password='SyntheticLearning2026!Aa'

for name in "$database" "$builder" "$runtime" "$runner" "$provider" "$proxy" "$browser"; do
	if docker container inspect "$name" >/dev/null 2>&1; then
		echo "Existing container $name; refusing to replace it." >&2
		exit 1
	fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
	echo "Existing test network; refusing to replace it." >&2
	exit 1
fi
mkdir -p "$reports"
mkdir -p "$reports/tls"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
	-keyout "$reports/tls/key.pem" -out "$reports/tls/cert.pem" \
	-subj /CN=teacher.localhost \
	-addext 'subjectAltName=DNS:teacher.localhost,DNS:student.localhost' \
	> "$reports/tls-generation.log" 2>&1
chmod 0444 "$reports/tls/key.pem" "$reports/tls/cert.pem"
docker run --rm --network none --user 0:0 \
	--mount "type=bind,src=$(pwd)/$reports/tls,dst=/tls" \
	--entrypoint keytool ppe-tools:local -importcert -noprompt \
	-alias learning-flow-test -file /tls/cert.pem \
	-keystore /tls/truststore.p12 -storetype PKCS12 -storepass learning-flow-test \
	> "$reports/truststore-generation.log" 2>&1
chmod 0444 "$reports/tls/truststore.p12"

cleanup() {
	for name in "$runtime" "$provider" "$proxy"; do
		if docker container inspect "$name" >/dev/null 2>&1; then
			docker logs "$name" > "$reports/$name.log" 2>&1
		fi
	done
	for name in "$browser" "$runtime" "$provider" "$proxy" "$runner" "$builder" "$database"; do
		if [ "$(docker inspect "$name" --format '{{index .Config.Labels "ppe.learning-flow.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
			docker rm -fv "$name" >/dev/null
		fi
	done
	if [ "$(docker network inspect "$network" --format '{{index .Labels "ppe.learning-flow.owner"}}' 2>/dev/null || true)" = "$owner" ]; then
		docker network rm "$network" >/dev/null
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker network create --internal --label ppe.learning-flow.owner="$owner" "$network" >/dev/null
docker run -d --name "$database" --label ppe.learning-flow.owner="$owner" --network "$network" \
	--tmpfs /var/lib/mysql:rw,size=512m -e MYSQL_ROOT_PASSWORD="$root_password" \
	-e MYSQL_DATABASE="$db_name" -e MYSQL_USER=flow_app -e MYSQL_PASSWORD="$app_password" \
	programming-process-evaluator-db --log-bin-trust-function-creators=1 >/dev/null

ready=false
for attempt in $(seq 1 60); do
	if docker exec "$database" sh -c 'MYSQL_PWD=flow-dummy-root mysql -uroot -Nse "SELECT 1"' >/dev/null 2>&1; then
		ready=true
		break
	fi
	sleep 1
done
if [ "$ready" != true ]; then
	echo "Isolated MySQL startup failed." >&2
	docker logs --tail 80 "$database" >&2
	exit 1
fi

docker run -d --name "$builder" --label ppe.learning-flow.owner="$owner" --network "$network" \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=root -e DB_PASSWORD="$root_password" -e PPE_ENV=simulation \
	-e "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/tmp/learning-flow-truststore.p12 -Djavax.net.ssl.trustStorePassword=learning-flow-test -Djdk.httpclient.allowRestrictedHeaders=host" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" \
	--mount "type=bind,src=$(pwd)/$reports/tls/truststore.p12,dst=/tmp/learning-flow-truststore.p12,readonly" \
	--entrypoint sh ppe-tools:local -c '
	set -eu
	cd /workspace
	cp -R /source/src/. /workspace/src/
	cp /source/build.gradle /source/settings.gradle /workspace/
	mkdir -p /workspace/docs
	cp -R /source/docs/rubric /workspace/docs/
	exec sleep infinity
' >/dev/null

run_gradle() {
	name=$1
	shift
	if ! docker exec "$builder" gradle --offline --no-daemon "$@" --console plain \
			> "$reports/$name.log" 2>&1; then
		cat "$reports/$name.log" >&2
		return 1
	fi
}

run_flow_test() {
	name=$1
	test_class=$2
	test_status=0
	docker exec -e LEARNING_FLOW_HTTP_TEST=true \
			-e LEARNING_FLOW_HTTP_BASE=https://teacher.localhost:8443 \
			"$builder" gradle --offline --no-daemon test --tests "$test_class" \
			--rerun-tasks --console plain > "$reports/$name.log" 2>&1 || test_status=$?
	mkdir -p "$reports/junit/$name"
	docker cp "$builder:/workspace/build/test-results/test/." "$reports/junit/$name/"
	if [ "$test_status" -ne 0 ]; then
		cat "$reports/$name.log" >&2
		return "$test_status"
	fi
}

run_gradle flyway-main flywayMigrate
run_gradle rubric-bootstrap registerStandardRubric
run_gradle rubric-bootstrap-repeat registerStandardRubric
run_gradle war war
docker cp "$builder:/workspace/build/libs/ROOT.war" "$reports/ROOT.war"

docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot -e 'CREATE DATABASE \`$rubric_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'"
if ! docker exec -e DB_NAME="$rubric_db" "$builder" gradle --offline --no-daemon \
		flywayMigrate --console plain > "$reports/rubric-conflict-migrate.log" 2>&1; then
	cat "$reports/rubric-conflict-migrate.log" >&2
	exit 1
fi
if ! docker exec -e DB_NAME="$rubric_db" -e EXERCISE_DB_TEST=true "$builder" \
		gradle --offline --no-daemon test \
		--tests control.student.StandardRubricDatabaseTest.registersExactImmutableStandardAndDoesNotAssignTasks \
		--rerun-tasks --console plain > "$reports/rubric-conflict-test.log" 2>&1; then
	cat "$reports/rubric-conflict-test.log" >&2
	exit 1
fi
mkdir -p "$reports/junit/rubric-conflict"
docker cp "$builder:/workspace/build/test-results/test/." "$reports/junit/rubric-conflict/"

docker run -d --name "$runner" --label ppe.learning-flow.owner="$owner" --network "$network" \
	--network-alias python-runner --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
	--read-only --tmpfs /tmp:rw,size=8m,mode=1777 --cap-drop ALL \
	--security-opt no-new-privileges -e PYTHON_RUNTIME_IMAGE=python:3.12-alpine -e RUNNER_PORT=8090 \
	programming-process-evaluator-python-runner:latest >/dev/null
docker run -d --name "$provider" --label ppe.learning-flow.owner="$owner" --network "$network" \
	--network-alias gemini-mock --mount "type=bind,src=$(pwd)/e2e/fixtures/prompt-provider.js,dst=/provider.js,readonly" \
	--entrypoint node mcr.microsoft.com/playwright:v1.64.0-noble /provider.js >/dev/null
docker run -d --name "$proxy" --label ppe.learning-flow.owner="$owner" --network "$network" \
	--network-alias teacher.localhost --network-alias student.localhost \
	--user 101:101 --read-only --tmpfs /tmp:rw,size=8m,uid=101,gid=101,mode=700 \
	--cap-drop ALL --security-opt no-new-privileges \
	--mount "type=bind,src=$(pwd)/scripts/testing/learning-flow-proxy.conf,dst=/etc/nginx/nginx.conf,readonly" \
	--mount "type=bind,src=$(pwd)/$reports/tls,dst=/tls,readonly" \
	--entrypoint nginx nginx:1.28.2-alpine -g 'daemon off;' >/dev/null
proxy_ip=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$proxy")
trusted_proxy_regex=$(printf '%s' "$proxy_ip" | sed 's/\./\\./g')
docker run -d --name "$runtime" --label ppe.learning-flow.owner="$owner" --network "$network" \
	--network-alias ppe-learning-flow-runtime \
	-e PPE_ENV=simulation -e "CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=^$trusted_proxy_regex\$" \
	-e TEACHER_PORTAL_HOST=teacher.localhost -e STUDENT_PORTAL_HOST=student.localhost \
	-e DB_HOST="$database" -e DB_PORT=3306 -e DB_NAME="$db_name" \
	-e DB_USER=flow_app -e DB_PASSWORD="$app_password" \
	-e PYTHON_RUNNER_URL=http://python-runner:8090 \
	-e GEMINI_API_KEY=synthetic-prompt-acceptance -e GEMINI_API_BASE_URI=http://gemini-mock:8099/ \
	-e STUDENT_CREDENTIAL_KEY=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE= \
	--mount "type=bind,src=$(pwd)/$reports/ROOT.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
	--entrypoint sh ppe-app:local -c \
	'rm -rf /usr/local/tomcat/webapps/ROOT; exec catalina.sh run' >/dev/null

ready=false
for attempt in $(seq 1 60); do
	if [ "$(docker inspect --format '{{.State.Running}}' "$runner")" != true ] \
			|| [ "$(docker inspect --format '{{.State.Running}}' "$provider")" != true ] \
			|| [ "$(docker inspect --format '{{.State.Running}}' "$proxy")" != true ] \
			|| [ "$(docker inspect --format '{{.State.Running}}' "$runtime")" != true ]; then
		echo "An isolated learning-flow service exited during startup." >&2
		docker logs --tail 80 "$runtime" >&2
		exit 1
	fi
	if docker exec "$builder" curl --noproxy '*' -kfsS -o /dev/null \
			https://teacher.localhost:8443/teacher/account/login \
			>/dev/null 2>&1; then
		ready=true
		break
	fi
	sleep 1
done
if [ "$ready" != true ]; then
	echo "Isolated Tomcat startup failed." >&2
	docker logs --tail 100 "$runtime" >&2
	exit 1
fi
docker exec "$provider" node -e '
fetch("http://127.0.0.1:8099/health").then(async response => {
  if (!response.ok || !(await response.json()).ok) process.exitCode = 1;
}).catch(error => { console.error(error.message); process.exitCode = 1; });
'
docker exec "$runner" python3 -c \
	'import urllib.request; urllib.request.urlopen("http://127.0.0.1:8090/health", timeout=2)'

run_flow_test fixture servlet.journey.LearningFlowFixtureTest
fixture_task_count=$(docker exec "$database" sh -c \
	'MYSQL_PWD=flow-dummy-root mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM tasks"')
printf '%s\n' "$fixture_task_count" > "$reports/tasks-after-fixture.txt"
if [ "$mode" = survey-sql ]; then
	run_flow_test teacher-flow \
		'servlet.journey.TeacherLearningFlowRuntimeTest.createsTaskConfiguresRealPromptAndSavesExamplesWithReadBack'
else
	run_flow_test teacher-flow servlet.journey.TeacherLearningFlowRuntimeTest
	run_flow_test student-flow servlet.journey.StudentLearningFlowRuntimeTest
fi

task_count=$(docker exec "$database" sh -c \
	'MYSQL_PWD=flow-dummy-root mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM tasks"')
if [ "$task_count" != 1 ]; then
	echo "Expected one synthetic task before survey setup; found $task_count." >&2
	exit 1
fi
task_id=$(docker exec "$database" sh -c \
	'MYSQL_PWD=flow-dummy-root mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT task_id FROM tasks"')
docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot \"\$MYSQL_DATABASE\" -e \"INSERT INTO surveys(task_id,title,survey_status,created_at) VALUES($task_id,'Synthetic learning flow survey','draft',UTC_TIMESTAMP())\""
survey_id=$(docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT survey_id FROM surveys WHERE task_id=$task_id AND title='Synthetic learning flow survey'\"")
{
	printf 'SET @student_survey_id = %s;\n' "$survey_id"
	cat scripts/dev/configure-student-survey-template.sql
} | docker exec -i "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --default-character-set=utf8mb4 \"\$MYSQL_DATABASE\"" \
	> "$reports/survey-template-apply.log" 2>&1

template_counts=$(docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT CONCAT((SELECT COUNT(*) FROM survey_questions WHERE survey_id=$survey_id),':',(SELECT COUNT(*) FROM survey_question_options o JOIN survey_questions q USING(question_id) WHERE q.survey_id=$survey_id))\"")
if [ "$template_counts" != 6:30 ]; then
	echo "Unexpected approved survey template counts: $template_counts." >&2
	exit 1
fi

rerun_status=0
{
	printf 'SET @student_survey_id = %s;\n' "$survey_id"
	cat scripts/dev/configure-student-survey-template.sql
} | docker exec -i "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --default-character-set=utf8mb4 \"\$MYSQL_DATABASE\"" \
	> "$reports/survey-template-rerun.log" 2>&1 || rerun_status=$?
if [ "$rerun_status" -eq 0 ] || ! grep -Fq 'Target survey must not already have questions or responses.' \
		"$reports/survey-template-rerun.log"; then
	echo "Survey template rerun did not fail closed as expected." >&2
	exit 1
fi
if [ "$(docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT CONCAT((SELECT COUNT(*) FROM survey_questions WHERE survey_id=$survey_id),':',(SELECT COUNT(*) FROM survey_question_options o JOIN survey_questions q USING(question_id) WHERE q.survey_id=$survey_id))\"")" \
		!= "$template_counts" ]; then
	echo "Survey template rerun changed persisted question or option counts." >&2
	exit 1
fi

rollback_survey_id=$(docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot \"\$MYSQL_DATABASE\" -Nse \"INSERT INTO surveys(task_id,title,survey_status,created_at) VALUES($task_id,'Synthetic survey rollback probe','draft',UTC_TIMESTAMP()); SELECT LAST_INSERT_ID();\"")
docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --delimiter='//' \"\$MYSQL_DATABASE\" \
	-e \"CREATE TRIGGER fail_synthetic_survey_option BEFORE INSERT ON survey_question_options \
	FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'synthetic survey rollback probe'; END//\"" \
	> "$reports/survey-trigger-create.log" 2>&1
trigger_count=$(docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='fail_synthetic_survey_option'\"")
if [ "$trigger_count" != 1 ]; then
	echo "Expected one survey rollback probe trigger; found $trigger_count." >&2
	cat "$reports/survey-trigger-create.log" >&2
	docker exec "$database" sh -c \
		"MYSQL_PWD=$root_password mysql -uroot --batch \"\$MYSQL_DATABASE\" -e 'SHOW TRIGGERS'" >&2
	exit 1
fi
rollback_status=0
{
	printf 'SET @student_survey_id = %s;\n' "$rollback_survey_id"
	cat scripts/dev/configure-student-survey-template.sql
} | docker exec -i "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --default-character-set=utf8mb4 \"\$MYSQL_DATABASE\"" \
	> "$reports/survey-template-rollback.log" 2>&1 || rollback_status=$?
docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot \"\$MYSQL_DATABASE\" -e 'DROP TRIGGER IF EXISTS fail_synthetic_survey_option'"
if [ "$rollback_status" -eq 0 ] || ! grep -Fq 'synthetic survey rollback probe' \
		"$reports/survey-template-rollback.log"; then
	echo "Survey template failure injection did not fail as expected." >&2
	exit 1
fi
rollback_counts=$(docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT CONCAT((SELECT COUNT(*) FROM survey_questions WHERE survey_id=$rollback_survey_id),':',(SELECT COUNT(*) FROM survey_question_options o JOIN survey_questions q USING(question_id) WHERE q.survey_id=$rollback_survey_id))\"")
if [ "$rollback_counts" != 0:0 ]; then
	echo "Survey template failure left partial rows: $rollback_counts." >&2
	exit 1
fi
docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot \"\$MYSQL_DATABASE\" -e \"UPDATE surveys SET survey_status='active' WHERE survey_id=$survey_id\""

if [ "$mode" = survey-sql ]; then
	echo "Survey template SQL apply, fail-closed rerun, and rollback checks passed."
	exit 0
fi

run_flow_test survey-flow servlet.journey.SurveyLearningFlowRuntimeTest

assignment_id=$(docker exec "$database" sh -c \
	'MYSQL_PWD=flow-dummy-root mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT task_class_assignment_id FROM task_class_assignments"')
submission_id=$(docker exec "$database" sh -c \
	'MYSQL_PWD=flow-dummy-root mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT submission_id FROM evaluations"')
evaluation_id=$(docker exec "$database" sh -c \
	'MYSQL_PWD=flow-dummy-root mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT evaluation_id FROM evaluations"')
docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT COUNT(*) FROM surveys WHERE survey_id=$survey_id AND survey_status='active'\"" \
	| grep -qx 1

docker restart --time 60 "$runtime" >/dev/null
ready=false
for attempt in $(seq 1 60); do
	if docker exec "$builder" curl --noproxy '*' -kfsS -o /dev/null \
			https://teacher.localhost:8443/teacher/account/login \
			>/dev/null 2>&1; then
		ready=true
		break
	fi
	sleep 1
done
if [ "$ready" != true ]; then
	echo "Isolated Tomcat restart read-back check failed." >&2
	docker logs --tail 100 "$runtime" >&2
	exit 1
fi
run_flow_test restart-readback servlet.journey.LearningFlowReadBackRuntimeTest

browser_status=0
docker run -d --name "$browser" --label ppe.learning-flow.owner="$owner" --network "$network" \
	-e PPE_P20_BASE_URL=https://student.localhost:8443 -e PPE_P20_IGNORE_HTTPS_ERRORS=true \
	-e PPE_P20_HOST_RESOLVER_RULES="MAP student.localhost $proxy_ip, MAP teacher.localhost $proxy_ip" \
	-e PPE_LEARNING_FLOW_TEACHER_BASE_URL=https://teacher.localhost:8443 \
	-e PPE_LEARNING_FLOW_STUDENT_LOGIN=synthetic-learning-student \
	-e PPE_LEARNING_FLOW_STUDENT_PASSWORD="$student_password" \
	-e PPE_LEARNING_FLOW_TEACHER_LOGIN=synthetic-learning-teacher \
	-e PPE_LEARNING_FLOW_TEACHER_PASSWORD="$teacher_password" \
	-e PPE_LEARNING_FLOW_ASSIGNMENT_ID="$assignment_id" \
	-e PPE_LEARNING_FLOW_SUBMISSION_ID="$submission_id" \
	-e PPE_LEARNING_FLOW_EVALUATION_ID="$evaluation_id" \
	-e PPE_LEARNING_FLOW_SURVEY_ID="$survey_id" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" \
		--entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'sleep 3600' >/dev/null
docker network connect bridge "$browser"
docker exec "$browser" sh -c '
		cd /source/e2e
		exec node node_modules/@playwright/test/cli.js test \
			phase20/learning-flow-student-evaluation.spec.js \
			phase20/learning-flow-student-survey.spec.js \
			phase20/learning-flow-teacher-survey.spec.js \
		--workers=1 --output=/tmp/ppe-learning-flow-results
' > "$reports/browser.log" 2>&1 || browser_status=$?
cat "$reports/browser.log"
mkdir -p "$reports/browser-results"
docker cp "$browser:/tmp/ppe-learning-flow-results/." "$reports/browser-results/" || browser_status=$?
if [ "$browser_status" -ne 0 ]; then
	exit "$browser_status"
fi

docker exec "$provider" node -e '
fetch("http://127.0.0.1:8099/stats").then(async response => {
  const stats = await response.json();
  const stages = stats.requests.map(request => request.stage).join(",");
  console.log(JSON.stringify({ generations: stats.generations, stages }));
  if (!response.ok || stages !== "fluctuations,examples,evaluation") process.exitCode = 1;
}).catch(error => { console.error(error.message); process.exitCode = 1; });
' > "$reports/provider-stats.json"

docker exec "$database" sh -c \
	"MYSQL_PWD=$root_password mysql -uroot --batch --skip-column-names \"\$MYSQL_DATABASE\" -e \"SELECT CONCAT((SELECT COUNT(*) FROM rubrics),':',(SELECT COUNT(*) FROM tasks WHERE publication_status='published'),':',(SELECT COUNT(*) FROM evaluations WHERE evaluation_status='completed'),':',(SELECT COUNT(*) FROM surveys WHERE survey_status='active'),':',(SELECT COUNT(*) FROM survey_questions WHERE survey_id=$survey_id),':',(SELECT COUNT(*) FROM survey_question_options o JOIN survey_questions q USING(question_id) WHERE q.survey_id=$survey_id),':',(SELECT COUNT(*) FROM survey_responses WHERE survey_id=$survey_id AND response_status='submitted'),':',(SELECT COUNT(*) FROM survey_answers a JOIN survey_responses r USING(survey_response_id) WHERE r.survey_id=$survey_id))\"" \
	> "$reports/persisted-counts.txt"
if ! grep -qx '1:1:1:1:6:30:1:6' "$reports/persisted-counts.txt"; then
	echo "Unexpected synthetic learning-flow persisted counts." >&2
	exit 1
fi

for name in rubric-conflict fixture teacher-flow student-flow survey-flow restart-readback; do
	bash scripts/ci/summarize-junit.sh "$reports/junit/$name"
done > "$reports/junit-summary.txt"
cat "$reports/junit-summary.txt"
printf 'Playwright: 3 passed, 0 failed. Reports: %s\n' "$reports"
