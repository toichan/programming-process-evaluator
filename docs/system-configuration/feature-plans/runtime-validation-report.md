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

---

## 工程20 Runtime Validation Report

**Generated**: 2026-10-13 22:40 JST
**Target**: `/Users/t.toida/programming-process-evaluator` — 工程20「機能横断のローカル検証」

### Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Latest isolated Compose app on port 18093; teacher host returned HTTP 200 after an initial Maven Central DNS failure; V1–V33 migrations and validation passed |
| Focused MySQL integration | PASS | 0 | 113 executions across focused runs: 108 passed, 0 failed/errors, 5 skipped; includes retained immediate-, scheduled-publication, and P20-03 authorization browser fixtures |
| Last full Gradle clean build (before later browser fixture additions) | PASS | 0 | Gradle 8.10.2/JDK 21; 141 suites, 527 tests, 349 passed, 0 failures, 0 errors, 178 skipped; WAR/build successful. Later fixture additions were compiled and selected tests passed in focused runs |
| Browser E2E | PASS (scope-limited) | 0 | Playwright 1.64.0/Chromium; 9 tests passed across five separate fixture environments/batches, including scheduled worker publication, notification, editor navigation, read-state persistence, and P20-03 login/role boundary |

**Overall**: NEEDS_SIGNOFF — all checks actually executed passed; both immediate publication and scheduled worker publication through student notification/editor are verified alongside N166 deny navigation, teacher review/export pages, and the P20-03 teacher authorization slice. Other opt-in cases and the full cross-feature journey/role-boundary matrix remain unverified.

### Environment and verdict

```text
environment:
  docker: AVAILABLE — `docker info` succeeded; latest dedicated Compose project `ppe-p20-authz-a1` used and removed after the test
  node: AVAILABLE — Node.js v24.21.0, npm/npx 11.19.0
  playwright: AVAILABLE — `@playwright/test` 1.64.0; `npx playwright install --with-deps chromium` exit_code: 0; Chromium launched for the browser batches
  infra-tier: PRIMARY(Docker-based) — dedicated MySQL schema, V1–V33 migrations, app, and Python runner; latest schema `ppe_teacher_task_test_authz`
  browser-tier: PRIMARY(Playwright) — nine tests passed across five separately provisioned fixture environments/batches; not a single combined run
startup: PASS — isolated Compose app project `ppe-p20-authz-a1`, port 18093; initial cold start failed on Maven Central DNS resolution, retry started Tomcat and teacher login returned HTTP 200; V1–V33 migration/validation exit_code: 0. MySQL function-creator trust was 0 before cleanup.
integration: PASS — exit_code: 0, passed: 108, failed: 0, skipped: 5 across focused runs; scope: prior focused MySQL suites plus immediate/scheduled publication and P20-03 authorization/browser fixtures; gaps: five skips and other environment-gated cases
e2e: PASS (scope-limited) — exit_code: 0 for each final batch; passed: 9 total, failed: 0; auth/role boundary, 30-second periodic save/read-back, stale-save HTTP 409/read-back, N166 deny notification navigation and direct editor 404, four teacher pages/exports/390px overflow, immediate publication to student editor, scheduled worker publication to one notification, notification-to-assignment editor/read-state persistence, and P20-03 invalid login, authenticated teacher workspace, and HTTP 403 admin boundary; gaps: remaining primary-journey/role-boundary matrix, non-Chromium/OS validation
overall: NEEDS_SIGNOFF — all executed checks passed, but the test plan's remaining opt-in cases and full journey/browser matrix are not verified; do not treat skipped or unverified cases as passed
```

### Test evidence

- Last full-suite command before the scheduled-publication fixture was added: `docker compose -p ppe-p20-publish-20261013 run --rm --no-deps --entrypoint gradle app --no-daemon --console=plain clean build --warning-mode all` (synthetic Compose environment only) — exit 0; JUnit 141 suites, 527 tests, 349 passed, 0 failures/errors, 178 skipped. The later Java fixture addition was compiled and its selected test passed separately; the full suite was not rerun after that addition.
- Fresh browser-fixture schema: `docker compose -p ppe-p20-20261013 run --rm --no-deps app gradle flywayMigrate flywayValidate` — exit 0, V1–V33. The opt-in `TeacherTaskDatabaseTest.createsRetainedEditorFixtureForPhase20BrowserAcceptance` run exited 0 with 1 passed, 0 failed/errors/skips; credential values were synthetic and are intentionally not recorded.
- Browser command: `npm --prefix e2e run test:phase20` with Node.js v24.21.0 on PATH and required fixture values supplied through environment variables — exit 0, 3 passed, 0 failed. Specs cover authentication/authorization, thirty-second autosave and read-back, and concurrent stale-write rejection/read-back.
- Follow-up browser command: `npm --prefix e2e run test:phase20 -- phase20/expired-deny-notification.spec.js phase20/teacher-review-mobile.spec.js` with the dedicated review fixture and credentials supplied only through environment variables — exit 0, 3 passed, 0 failed. These tests ran against a separate Compose project/database from the original three tests; they were not rerun together because their fixtures differ.
- Publication browser fixture: `TeacherTaskDatabaseTest.createsRetainedDraftAndStudentForPhase20PublicationBrowserAcceptance` — exit 0, one selected test passed; fixture properties held only task ID, assignment ID, and synthetic student login ID. No password was written to disk.
- Publication browser command: from `e2e/`, `npx playwright test phase20/teacher-publication.spec.js --config=playwright.config.js`, with base URL and synthetic teacher/student credentials supplied only through environment variables — final exit 0, 1 passed, 0 failed. Teacher published the draft via the UI; the student opened the matching editor and it rendered editable.
- Publication DB read-back confirmed the task and class assignment were `published`, `published_at` was set, and `late_submission_policy` remained `deny`. Fresh schema `flywayMigrate` and retry `flywayValidate` both exited 0; the MySQL function-creator trust setting was 0 before cleanup.
- Scheduled-publication acceptance: Docker and Node.js were independently available; Node.js v24.21.0, npm 11.19.0, Playwright 1.64.0. `npx playwright install --with-deps chromium` exited 0. In dedicated project `ppe-p20-schedpub-a1`, empty DB `ppe_teacher_task_test_scheduledpub` received V1–V33 via `docker compose -p ppe-p20-schedpub-a1 run --rm app gradle flywayMigrate flywayValidate --no-daemon` (exit 0); `log_bin_trust_function_creators` was 1 only during migration and restored to 0 before startup.
- The opt-in Java fixture command `docker compose -p ppe-p20-schedpub-a1 run --rm -e TEACHER_TASK_DB_TEST=true -e TEACHER_TASK_SCHEDULED_PUBLICATION_BROWSER_FIXTURE=true app gradle test --tests 'control.teacher.TeacherTaskDatabaseTest.createsRetainedScheduledPublicationFixtureForPhase20BrowserAcceptance' --no-daemon` (synthetic passwords supplied as environment variables) exited 0: 1 test, 1 passed, 0 skipped/failed/errors. Fixture file contained only task ID, assignment ID, and synthetic login ID; it verified `scheduled` state and zero task-published notifications.
- Isolated app startup command `docker compose -p ppe-p20-schedpub-a1 up -d app`; `curl` to `http://student.localhost:18092/student/account/login` with the configured Host header returned HTTP 200. Only the synthetic assignment was made due. The application worker changed it to `published`, wrote one `publish_scheduled` system-success audit, and created exactly one `task_published` notification for one synthetic student.
- Browser command `npm --prefix e2e run test:phase20 -- phase20/scheduled-publication-notification.spec.js` with Node.js on PATH and synthetic login/base URL supplied through environment variables exited 0: 1 passed, 0 failed (4.0s). The flow verified invalid login rejection, valid login, one unread publication notification, click-through to the matching editable assignment editor, and persistent `read_at` on return to notifications. DB read-back after the browser flow reported `published/published`, notification count 1, distinct student count 1, read notification count 1, and audit action `publish_scheduled` with actor role `system`.
- The initial 8 passing browser tests were run in four independent fixture environments/batches (original 3; review 3; immediate publication 1; scheduled publication 1). The P20-03 authentication/role-boundary test passed separately in a fifth isolated environment; the cumulative count is 9, not a single combined suite. No Gemini API or real student/submission data was used.
- `flywayValidate` initially exited 1 because the still-running app Gradle process held the shared journal cache lock. The app was stopped before retry; the retry exited 0. See [error report](../error-report.md#工程20-課題公開から生徒editorまでのplaywright受入2026-10-13-1259-jst).
- `Phase20BrowserFixtureTest` — exit 0, 1 passed, 0 skipped/failures/errors. The dedicated review schema reported latest migration V33 and `@@GLOBAL.log_bin_trust_function_creators=0` before its Compose project was removed.
- Follow-up browser checks confirmed the expired-`DENY` notification falls back to `/student/home#taskListSection`, direct editor access returns 404, teacher progress/submission/evaluation/survey pages render, exports trigger downloads with expected content, survey downloads omit `review_student`, and all four teacher pages have no document-level horizontal overflow at 390px.
- Configured-host readiness checks: `http://student.localhost:18089/student/account/login` and `http://teacher.localhost:18089/teacher/account/login` — HTTP 200 each. Requests to the generic host did not match the application's portal-host routing and were not treated as application failures.
- `node --check` on the Playwright config/helper/spec files and final `git diff --check` — exit 0.
- The focused suites and detailed per-suite results are recorded in [Phase 20 cross-feature validation](./phase-20-cross-feature-validation.md). They include the evaluation-example DAO regression, editor persistence/ownership, notification read-state, progress, review, survey, and distribution.
- Isolated Phase 20 schemas, Compose services/volumes, and project networks were removed; the pre-existing shared database volume was preserved and `log_bin_trust_function_creators` was verified as `OFF` before cleanup.
- P20-03 authorization slice: isolated project `ppe-p20-authz-a1`, dedicated DB `ppe_teacher_task_test_authz`, ports 33393/18093. Fresh V1–V33 migration/validation succeeded; five `TeacherTaskDatabaseTest` authorization/ownership cases passed (exit 0, 5 passed, 0 failed/errors/skipped), and `createsRetainedSyntheticTeacherFixtureForIsolatedBrowserAcceptance` passed (exit 0, 1 passed). The app initially failed to resolve Maven Central from its container; after dependency resolution recovered, `docker compose -p ppe-p20-authz-a1 up -d app` exited 0 and `http://teacher.localhost:18093/teacher/account/login` returned HTTP 200. `npm --prefix e2e run test:phase20 -- phase20/authentication.spec.js` exited 0, 1 passed: invalid credentials were rejected, unauthenticated access redirected to login, a valid synthetic teacher reached the protected task page, and `/admin/teachers` returned HTTP 403. The project was removed with `docker compose -p ppe-p20-authz-a1 down -v`; no project containers, volumes, or networks remained.
- Gemini API and real student/submission data were not used in Phase 20.

### Local implementation acceptance follow-up (2026-10-13)

- Focused Java command using dedicated Gradle user home `.gradle-rev170-final`: `gradle --no-daemon test --tests servlet.teacher.TeacherPromptApplicationViewTest --tests servlet.teacher.AccountDistributionUiContractTest --tests servlet.teacher.TeacherExerciseServletTest war` — exit 0; 11 passed, 0 failed, 0 skipped; WAR generated.
- Focused student tests using the same dedicated Gradle user home — 48 passed, 0 failed, 0 skipped across editor, notification, survey, and evaluation UI/control suites.
- Browser acceptance on the local implementation covered task editing (preserved title case, semicolon-separated constraints/rules, Python CodeMirror sample, save-to-prompt navigation), task-list publication action, survey question 3/4 text choices, evaluation scores/reason tabs, notification list, notification spacing, editor input/output separation, and the distribution operation-history table at normal and narrow viewport widths. The operation-history table measured 1074px within its 1074px viewport at 1280px; at a 606px viewport it used a 740px table inside a 434px scroll area.
- The editor's synthetic normal-run check rendered terminal input echo and standard output in separate DOM elements. The student pre-submit browser check for assignment 17 instead returned HTTP 409 (stale-draft conflict); no submission was made and the check was not retried. The cause remains undetermined.
- Follow-up read-only page inspection showed that the original loaded page carried an empty draft token and a different code version from the later persisted state (`draftUpdatedAt=2026-10-13T12:20:24.362889`, JST 21:20:24). This is consistent with the optimistic-lock conflict, but the exact writer and its timing relative to the historical HTTP 409 are not established. After reloading the current draft, a check-only browser request returned HTTP 200 but 0/2 cases passed: `input("入力してください")` caused actual stdout `入力してください6\n` / `入力してください8\n` against expected `6` / `8`. The final submission action was not taken. At that point, the project policy for excluding `input(prompt)` output was still awaiting confirmation; the user subsequently approved check-only suppression and the fix was validated as recorded below. See [error report](../error-report.md#rev170-提出前チェック再調査と入力プロンプトの出力混入2026-10-13-2124-jst).
- The user approved excluding `input(prompt)` text only from submission-check comparison. The feature specification/state rules/implementation contract were updated; Java sets the option only for submission checks, and the runner defaults it to false for normal requests and interactive sessions. Runner tests passed 24/24, including real local subprocess and HTTP request-contract coverage; `StudentEditorControlTest` passed 1/1 in the local Gradle container. The local runner was rebuilt/recreated and passed its healthcheck; the confirmed `app-live` source-mounted app was restarted, with student login returning HTTP 200. After the user re-authenticated, the assignment 17 pre-submit check displayed `2/2` matched (`6` and `8`); the final modal submission button was not clicked. Normal interactive execution displayed `入力してください`, echoed `>>> 1 2 3`, and returned `6`. One earlier manual interactive attempt reached the 30-second input timeout before sending; the retry via Enter succeeded. The two execution attempts generated only synthetic demo execution logs; draft/submission state was not changed. Detailed command failures and retries are in the [error report](../error-report.md#rev171-提出前python入力プロンプト比較の修正検証2026-10-13-2220-jst).
- An earlier diagnostic POST during an expired browser session returned HTTP 302 and an HTML/non-JSON response; after the authenticated `/student/home` GET returned HTTP 200, the editor was reloaded and the check-only request returned the JSON HTTP 200 above. A separate inspector snippet failed with `ReferenceError: URLSearchParams is not defined`; it was corrected to inspect the form body without that API. Neither action submitted the assignment.
- The deleted-notification browser fallback remains unverified because the local fixture had no deleted-notification record. A 2026-10-13 follow-up on the local authenticated student exercise page (`exerciseId=17`, synthetic folder `entryId=47`) dispatched clicks to the folder-name button (expanded, selected path, and revealed `main.py`), the folder header (collapsed), and the chevron (expanded/collapsed); checkbox/menu target events did not toggle the folder. This verifies event handling and rerendering, not physical pointer input. Browser click/stability waits timed out and `node --check` was unavailable (`node: command not found`); Problems diagnostics reported no errors and `git diff --check` passed. Physical pointer interaction remains unverified. See the [error report](../error-report.md#工程20-授業演習フォルダ行操作のbrowser受入2026-10-13-2325-jst).
- The previous shared Gradle-cache journal lock prevented the first focused test invocation from starting; rerunning with the dedicated Gradle user home succeeded. The lock incident and HTTP 409 are recorded in the [error report](../error-report.md).

### Issues found and fixed

| # | Severity | Description | Status |
|---|---|---|---|
| 1 | HIGH | Saving generated evaluation examples could activate an older inactive row under the same prompt version | Fixed in `TeacherPromptDao`; reproduced before the fix and covered by a passing MySQL regression test |
| 2 | LOW | First final-build attempt used `/dev/null` for Compose interpolation and produced an invalid bind-mount destination | Environment invocation corrected; the retried clean build passed |
| 3 | LOW | Fresh isolated MySQL rejected V11 trigger creation with error 1419 while binary logging was enabled | Partial DDL was removed from the empty test schema; migration was repaired/retried with the trust setting enabled only on the isolated MySQL, V1–V33 validation passed, and the setting was restored to `OFF` |
| 4 | LOW | Initial Playwright attempts had incorrect teacher landing/portal-origin expectations and a non-unique editor selector | Test configuration and locators were corrected; the original browser suite passed all 3 tests, with no production-code defect indicated |
| 5 | LOW | Follow-up Playwright checks initially matched duplicate notification controls, navigated teacher routes against the student base host, captured a list JSON response instead of the requested CSV, and observed an empty `Response.text()` for a blob download | Selectors were scoped to the notification list, teacher URLs use the current host, response matching uses the exact `view` parameter, and CSV content is checked from the completed download file. All 3 follow-up tests passed; no product defect was evidenced |
| 6 | LOW | Publication browser test's task-link locator matched three `taskId` links | Restricted it to the exact task-edit href; final browser run passed |
| 7 | LOW | The publication browser test exceeded its 50-second whole-test timeout while loading the editor route | Editor GET returned HTTP 200; raised only this multi-login journey timeout to 120 seconds and closed contexts concurrently. Final run passed in 3.8 seconds |
| 8 | LOW | A Flyway validation attempt timed out on a Gradle journal lock held by the live app process | Stopped the isolated app before retry; `flywayValidate` passed. No database or production-code defect |
| 9 | LOW | Initial scheduled-publication schema setup failed at V11 with MySQL error 1419; an early reset probe used default Compose values and returned access denied | Recreated only the named isolated database, exported test Compose settings, enabled `log_bin_trust_function_creators` only for migration, validated V1–V33, then restored it to 0 |
| 10 | LOW | First post-browser DB verification query selected a nonexistent `task_class_assignments.published_at`; an initial version probe also omitted the Node.js directory from PATH | Corrected the DB query to inspect `publish_at`; added the installed Node.js directory to PATH. Corrected checks and browser test passed |
| 11 | LOW | Initial read-only MySQL verification command supplied the wrong test username and returned access denied | Corrected the verification credentials; rechecked migration count and `log_bin_trust_function_creators=0`. No database data was changed |
| 12 | LOW | Initial isolated app startup could not resolve `repo.maven.apache.org` while downloading Tomcat/ECJ dependencies | Host endpoint returned HTTP 200; retry after the transient container DNS failure started Tomcat and the teacher login route returned HTTP 200. The P20-03 Playwright test then passed |
| 13 | LOW | Initial focused Gradle test run could not acquire the shared Gradle journal-cache lock | Retried with dedicated `GRADLE_USER_HOME`; selected UI tests and WAR build passed |
| 14 | MEDIUM | Student pre-submit browser check for assignment 17 returned HTTP 409 because the draft was stale | No submission was made and the check was not retried; cause is undetermined. Preserve as an open browser-acceptance gap and investigate before sign-off |
| 15 | MEDIUM | Submission-check comparison included Python's `input(prompt)` text, so prompt-bearing examples mismatched expected output | User-approved check-only suppression implemented; runner 24/24, Java control 1/1, and local authenticated browser check 2/2 pass. Final assignment submission was not performed |

### Known gaps

- N166 expired-`DENY` browser navigation, teacher progress/review/survey screens and exports, and 390px width on those four teacher pages were verified in the follow-up batch. Full cross-feature narrow-width/role-boundary coverage remains incomplete.
- Other browser/OS combinations, OS-native file dialogs, production behavior, real Gemini API use, and opt-in tests not included in the focused suites were not tested.
- VS Code Problems reports unresolved Gson `JsonObject` symbols in unchanged regions of `TeacherPromptDao`; the Java Gradle build and focused MySQL regression/fixture tests compile and pass, so this is recorded as an editor classpath diagnostic rather than a build failure.
- Final build includes 178 skipped opt-in tests; focused DB suites separately ran the listed cases, while other environment-gated tests remain unverified. Other main user journeys and full role-boundary/browser/OS coverage remain open. See the Phase 20 plan and [error report](../error-report.md).
- Deleted-notification browser fallback, folder-row whole-area interaction, and a recorded execution-I/O log detail view remain unverified in the local UI acceptance. The historical stale-draft HTTP 409 was not reproduced using the same stale page; a different saved draft version was observed on reload. The approved check-only fix now passes focused automated tests and the authenticated local browser check. The original stale-page conflict remains unexplained; the latest check used the current matching draft. The listed unverified browser cases are not considered passed.
- 2026-10-13 22:54 JST follow-up: after reloading local synthetic assignment 17, the pre-submission I/O check returned 2/2 matches (`6`, `8`) with the input prompt excluded from compared output. No final submission or draft change was made. Reloading the page shows an empty execution terminal; this is not counted as a defect because the task-editor requirement preserves output until the next execution, while its documented reload contract covers the saved draft and does not require restoring the latest ordinary execution. The browser tool's normal and forced locator-click attempts timed out; dispatching the click event through the locator produced the result modal, so this follow-up does not independently prove a natural pointer click. See the [error report](../error-report.md#工程20-生徒エディター提出前チェックの再確認2026-10-13-2254-jst).
