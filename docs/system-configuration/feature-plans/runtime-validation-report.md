# Runtime Validation Report

**Generated**: 2026-10-08  
**Target**: `/Users/t.toida/programming-process-evaluator` — 工程14「課題進捗確認」

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Existing Compose app responded; isolated fixture-backed app returned HTTP 200 on its teacher login route and rendered the authenticated progress page |
| Integration Tests | PASS | 0 | 8 focused status, DAO, and MySQL-backed integration tests; no failures |
| Full Gradle Tests | PASS | 0 | 69 suites, 284 tests, 99 skipped, 0 failures/errors |
| Browser E2E | PASS | 0 | Empty state and populated fixture journeys, filters, sorting, details, activity timeline, CSV payload, refresh, and access boundary checked |

**Overall**: PASS — implementation and tested user journeys succeeded. The integrated browser did not expose a native download event; the generated CSV Blob content, BOM, filtered row, and success feedback were verified.

## Test Assets

| File | Scope |
|---|---|
| `src/test/java/entity/TeacherProgressStatusTest.java` | State derivation and display labels |
| `src/test/java/dao/TeacherProgressDaoTest.java` | Query mapping and DAO behavior |
| `src/test/java/control/teacher/TeacherProgressDatabaseTest.java` | Isolated MySQL fixture, list/detail/activity read, and authorization boundaries |

No legacy browser E2E suite was present; the prototype was used as the UI reference.

## Environment

```text
environment:
  docker: AVAILABLE — docker info succeeded; Compose MySQL used for isolated integration tests
  node: UNAVAILABLE — which node returned no executable
  playwright: UNAVAILABLE — Node.js prerequisite is absent; npx Playwright installation was not applicable
  infra-tier: PRIMARY(Docker-based) — dedicated MySQL schema; no infrastructure fallback
  browser-tier: PRIMARY(Integrated browser) — authenticated browser acceptance per feature plan
startup: PASS — Compose app was already running; fixture-backed temporary app responded HTTP 200 at /teacher/account/login
integration: PASS — exit_code: 0, passed: 8, failed: 0, skipped: 0, scope: focused progress status/DAO/MySQL tests, gaps: none in the tested server-side paths
e2e: PASS — exit_code: 0 (browser actions completed), passed: populated list/detail/activity, summary, filters, sort, refresh persistence, CSV payload/BOM, empty state, unauthorized detail 404; native download event was not surfaced by the browser harness
overall: PASS — implementation and tested user journeys succeeded; native download event capture remains a tooling limitation
```

## Commands and Evidence

Full suite:

```sh
JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test --no-daemon --console=plain --warning-mode all
```

Result: exit code `0`; 69 suites, 284 tests, 99 skipped, 0 failures, 0 errors.

Focused MySQL-backed suite (run against `ppe_teacher_progress_test_t021`, created for this validation only):

```sh
docker compose exec -T \
  -e DB_NAME=ppe_teacher_progress_test_t021 \
  -e TEACHER_PROGRESS_DB_TEST=true \
  app gradle test \
  --tests entity.TeacherProgressStatusTest \
  --tests dao.TeacherProgressDaoTest \
  --tests control.teacher.TeacherProgressDatabaseTest \
  --project-cache-dir=/tmp/ppe-progress-test-cache \
  --no-daemon --console=plain --warning-mode all
```

Result: exit code `0`; 8 tests completed without failures. The isolated schema was dropped afterward. MySQL `log_bin_trust_function_creators` was restored to `0`.

Browser acceptance used an authenticated teacher in the running app and a temporary fixture-backed app. The fixture showed one assigned student, task, and manual-save activity. Search/status/consent filters narrowed the row, CSV output contained the matching record and UTF-8 BOM bytes `EF BB BF`, update retained search/consent values, and the detail endpoint returned 404 for an out-of-scope record. The automatic 20-second refresh was exercised with the browser visibility state set to visible because the integrated browser otherwise reported `visibilityState=hidden`.

## Issues Found and Fixed

| # | Severity | Description | Status |
|---|---|---|---|
| 1 | HIGH | MySQL rejected the `row_number` alias in the latest-evaluation query | Fixed by renaming the alias to `evaluation_rank`; focused MySQL tests pass |
| 2 | HIGH | JSP EL could not resolve record accessors on populated progress rows, causing HTTP 500 | Fixed by adding JavaBean getters to `TeacherProgressRow`; populated list and detail modal verified in browser |
| 3 | LOW | Empty database state showed both the no-data row and a no-filter-match message | Fixed; verified only the no-data row is visible |

## Known Gaps

- The browser harness did not surface a native download event. The CSV Blob, filtered data row, content type, UTF-8 BOM, and success feedback were checked directly.
- Node.js/Playwright automation was unavailable. Browser rendering and interaction were instead validated through the integrated browser tool.
- VS Code Problems still reports `com.google.gson.Gson` as unresolved in the servlet, although Gradle compilation/tests and the browser detail JSON response succeed; the editor's Gradle classpath view appears stale.
- Viewing/downloading unsubmitted draft code remains out of scope per the feature specification; its UI is disabled with an explanatory message.
