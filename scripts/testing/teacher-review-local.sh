#!/bin/sh
set -eu

# Only local, task-created dummy databases; database mode requires a fresh database.
cd "$(dirname "$0")/../.."
mode=${1:-unit}
suffix=${2:-}
case "$mode" in unit|database|runtime) ;; *) echo "Usage: sh scripts/testing/teacher-review-local.sh unit|database|runtime [lowercase_suffix]" >&2; exit 2 ;; esac
container="ppe-teacher-review-validation-${mode}"
reports="build/teacher-review-validation/$mode"
mkdir -p "$reports"
if docker container inspect "$container" >/dev/null 2>&1; then
	echo "Named test container already exists; inspect it rather than replacing it." >&2
	exit 1
fi
if [ "$mode" != unit ]; then
	case "$suffix" in ''|*[!a-z0-9_]*) echo "A unique lowercase database suffix is required." >&2; exit 2 ;; esac
	test "${#suffix}" -le 30
	test "$(docker inspect ppe-preparation-20261008-db-1 --format '{{index .Config.Labels "com.docker.compose.project"}}')" = ppe-preparation-20261008
	database="ppe_teacher_review_test_${suffix}"
	if [ "$mode" = runtime ]; then
		test "$(docker inspect ppe-teacher-review-runtime --format '{{.State.Running}}')" = true
		test "$(docker inspect ppe-teacher-review-runtime --format '{{index .Config.Labels "ppe.teacher-review.test"}}')" = true
		docker inspect ppe-teacher-review-runtime --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -Fxq "DB_NAME=$database"
		docker inspect ppe-teacher-review-runtime --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -Fxq 'DB_HOST=ppe-preparation-20261008-db-1'
	else
	docker exec -i ppe-preparation-20261008-db-1 sh -c '
		MYSQL_PWD=$(cat /run/secrets/db_root_password); export MYSQL_PWD
		mysql -uroot
	' <<SQL
CREATE DATABASE \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'ppe_teacher_review_dummy'@'%' IDENTIFIED BY 'DummyReviewDb42!';
GRANT ALL PRIVILEGES ON \`$database\`.* TO 'ppe_teacher_review_dummy'@'%';
SQL
	for version in $(seq 1 23); do cat src/main/resources/db/migration/V"${version}"__*.sql; done |
		docker exec -i ppe-preparation-20261008-db-1 sh -c '
			MYSQL_PWD=$(cat /run/secrets/db_root_password); export MYSQL_PWD
			exec mysql -uroot "$1"
		' fixture "$database"
	fi
	set -- --network ppe-preparation-20261008_database -e TEACHER_REVIEW_DB_TEST=true \
		-e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 -e DB_NAME="$database" \
		-e DB_USER=ppe_teacher_review_dummy -e DB_PASSWORD=DummyReviewDb42!
	if [ "$mode" = runtime ]; then
		set -- "$@" -e TEACHER_REVIEW_RUNTIME_TEST=true \
			-e TEACHER_REVIEW_HTTP_BASE=http://ppe-teacher-review-runtime:8080
	fi
else
	set --
fi
result=0
docker run --name "$container" "$@" -e TEACHER_REVIEW_VALIDATION_MODE="$mode" \
	--mount "type=bind,src=$(pwd),dst=/source,readonly" --entrypoint sh ppe-tools:local -c '
		cp -R /source/src/. /workspace/src/
		cp /source/build.gradle /source/settings.gradle /workspace/
		if [ "$TEACHER_REVIEW_VALIDATION_MODE" = runtime ]; then
			gradle --offline --no-daemon test --tests servlet.teacher.TeacherReviewRuntimeTest --rerun-tasks
		else
			gradle --offline --no-daemon test \
			--tests control.teacher.TeacherReviewControlTest \
			--tests control.teacher.TeacherReviewDatabaseTest \
			--tests servlet.teacher.TeacherReviewServletTest \
			--tests servlet.auth.ApplicationUrlContractTest \
			--tests dao.TeacherPermissionDaoTest \
			--tests control.student.PythonRunnerClientTest \
			--tests control.student.PythonRunnerPreviewTest war --rerun-tasks
		fi
	' || result=$?
docker cp "$container:/workspace/build/test-results/test/." "$reports/"
if [ "$mode" != runtime ]; then
	docker cp "$container:/workspace/build/libs/ROOT.war" build/teacher-review-validation/teacher-review.war || result=1
fi
docker rm "$container" >/dev/null
echo "Test result=$result; reports: $reports; WAR: build/teacher-review-validation/teacher-review.war; database fixture retained."
exit "$result"
