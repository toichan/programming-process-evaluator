# Production preparation plan

Date: 2026-10-08. Baseline: `521c974d37637d502769391f5ec89fac98b40420`.
This is the execution/checkpoint record for the explicitly requested twelve phases.
No AWS changes, public listeners, real student data, or real Gemini calls are authorized.
Development Compose and its existing database/volumes must remain untouched.

## Technical context and constraints

Java 21, Tomcat 9 (`javax.servlet`), Gradle 8.10.2, MySQL 8, Flyway V1–V23.
The [implementation contract](implementation-contract.md), [state rules](../state-rules/),
[function specification](../function-specification.md), and
[database definitions](../database-design/table-definitions.md) remain authoritative.
This is deployment preparation, not a framework rewrite; no separate architecture
artifact or constitution is assumed. User goals 1–15 are the requirements below.

Approved security decision: remove the host socket from the runner; place it behind
a dedicated, default-deny Docker API broker enforcing child-container configuration.
The broker remains a privileged trust boundary sharing the host kernel.
No generic Docker socket proxy with unrestricted container creation is acceptable.

Continuous development requirement (2026-10-08): releases must be built from an
explicit commit, not a dirty checkout; persistent DB volume/project identity must
remain stable across releases. Updates require a pre-migration encrypted backup,
schema compatibility review, health/smoke gates and an app-only previous-release
rollback. Applied migrations are immutable; expand/contract changes and DB restore
are separate from image rollback. Production secrets never become development
fixtures. No updates during class; maintenance approval is explicit.

## Implementation steps and tasks

| Phase / plan item | Task | Requirement coverage | Acceptance / status |
|---|---|---|---|
| 1.1 | T001 Confirm HEAD, clean tree, blockers and approved design | REQ-001–015 | Confirmed; prior blockers still present |
| 2.1 | T002 Add production Tomcat image and `compose.production.yml` | REQ-001,002,003,012 | Build, Compose validation, no source mounts |
| 3.1 | T003 Separate migration/runtime DB grants and test fresh migration | REQ-004,005 | V1–latest, validate, rerun; runtime DDL denied |
| 4.1 | T004 Add authenticated runner and constrained Docker broker | REQ-006 | Real executions plus forbidden Docker operations rejected |
| 5.1 | T005 Add atomic Parameter Store file-secret preparation | REQ-008 | Missing/invalid secret fails; no secret values logged |
| 6.1 | T006 Add TLS Nginx, trusted proxy, cookie configuration | REQ-009,010,011 | Config check; local dummy certificate, host routing |
| 7.1 | T007 Verify authorization, CSRF, session and privacy boundaries | REQ-007,011 | Targeted regressions and real HTTP checks |
| 8.1 | T008 Guard synthetic fixture and complete teacher core functions | REQ-014 | Approved specification; student/teacher end-to-end |
| 9.1 | T009 Encrypt backup, retention, isolated restore and update rollback | REQ-013,015 | Restore and release switch without volume loss |
| 10.1 | T010 Add operations/monitoring instructions and health checks | REQ-012 | Health, restart, logging; AWS actions only documented |
| 11.1 | T011 Automate isolated production simulation | REQ-001–014 | Tomcat/Nginx/MySQL/runner/mock; persistence and recovery |
| 12.1 | T012 Finish first-deploy/update scripts, EC2 runbook and readiness report | REQ-015 | Exact commit, backup, migration compatibility, smoke, rollback |

Tasks run in phase order. A failed gate is not silently marked complete.
Progress/results are appended here at each phase boundary.

## Testing strategy

Server-rendered HTML; primary stack is isolated Docker Compose with real Tomcat,
MySQL, Nginx and constrained Python execution; Gemini is a local HTTP mock.
Use uniquely named projects, dummy file secrets, loopback-only local proxy ports,
and disposable volumes. Never run test fixtures against development or production DBs.
Unit tests cover error/authorization/configuration cases; HTTP and browser checks
cover login, saving, execution, submission and teacher readback.
No SQLite/H2 substitute for MySQL trigger or Flyway acceptance.
Tests skipped for missing infrastructure are recorded as unverified, not passed.

## Requirement mapping

| ID | Goal | Plan items | Evidence |
|---|---|---|---|
| REQ-001 | Production container startup | 2.1,11.1 | production Compose |
| REQ-002 | WAR on Tomcat, no Gradle runtime | 2.1,11.1 | app Dockerfile, HTTP |
| REQ-003 | Persistent MySQL | 2.1,11.1 | DB volume, restart test |
| REQ-004 | Fresh Flyway | 3.1,11.1 | migration test |
| REQ-005 | Least-privilege DB runtime | 3.1 | init grants, DDL rejection |
| REQ-006 | Isolated Python | 4.1,11.1 | broker/runner tests |
| REQ-007 | Safe Gemini use | 7.1,11.1 | mock failure and privacy tests |
| REQ-008 | Safe secrets | 5.1 | file loading and fetch tests |
| REQ-009 | Nginx proxy | 6.1,11.1 | Nginx config and HTTP |
| REQ-010 | Two portal hosts | 6.1,11.1 | host routing checks |
| REQ-011 | HTTPS | 6.1,7.1 | TLS, redirect and cookie checks |
| REQ-012 | Health/restart/logging | 2.1,10.1,11.1 | Compose and runbook |
| REQ-013 | Backup/restore | 9.1,11.1 | encrypted backup and restored rows |
| REQ-014 | Dummy core workflow | 8.1,11.1 | isolated fixture and E2E |
| REQ-015 | EC2 first deploy and repeatable updates | 9.1,11.1,12.1 | release/update/rollback scripts and runbook |

## Results

- Phase 1: clean HEAD `521c974`; B1–B5 still present. Dedicated broker design approved.
- Baseline security: root socket blast radius, unauthenticated internal runner,
  Secure cookie missing. Existing RBAC/CSRF and Gemini `store:false` are preserved.
- Phase 2: production image built successfully; full Gradle suite passed
  (358 tests, 126 gated skips). Compose syntax validated. New health URL added to
  the explicit URL inventory. Runtime image contains WAR, not Gradle/source mounts.
- Phase 3 design: schema-local migrator grants; SUPER is temporarily granted only
  around migrations and revoked on exit. Binary logging and trust-function settings
  remain unchanged. V11 is not edited, preserving applied migration checksums.
  Runtime user receives SELECT/INSERT/UPDATE/DELETE only. If the migration process is
  forcibly killed, explicitly run the documented SUPER revocation before proceeding.
- Phase 3: real MySQL 8.0.44 fresh V1–V23, validate and rerun passed.
  Binary logging remained on and trust-function-creators remained 0. SUPER was
  revoked. Runtime SELECT passed; CREATE TABLE was denied with Error 1142.
  Official MySQL init scripts must be sourced (not executable) and must not enable
  nounset in the parent's helper functions; the production DB image enforces this.
  Health checks wait for completed account initialization and the TCP listener.
- Production WAR startup: Tomcat health returned 200; login cookie had
  Secure/HttpOnly/SameSite=Lax. Exact proxy IP trust is configurable to avoid
  collisions with existing Docker networks; no development networks were changed.
- Phase 4: constrained broker and authenticated nonroot runner operate with real
  Docker children. Policy tests reject host binds, extra capabilities/devices,
  alternative networks/images/commands, limits changes and foreign containers.
  Runtime tests passed normal Python, errors, interactive input, cancellation,
  four concurrent executions/fifth rejection, network/read-only filesystem checks,
  32 KiB output cap and 60-second wall timeout. No runner socket mount or host port.
  Docker wait headers must be forwarded before reading completion to avoid CLI
  startup deadlock; cleanup inspect must be container-only to avoid image fallback.
  Broker remains root-equivalent if its own implementation is compromised; this
  is risk reduction, not VM isolation or proof against kernel vulnerabilities.
- Phase 5: atomic, locked Parameter Store snapshot retrieval tested with a fake AWS
  CLI: six files published, failed retrieval left no partial snapshot, overwrite
  rejected, no values printed. App secret entrypoint validates format and fails
  closed. Added parameters/IAM/key continuity requirements documented; no AWS call.
- Phase 6: Nginx 1.28.2 readonly/nonroot image built and config checked. Local
  self-signed TLS, both portal hosts, HTTP redirect, cookie flags and DB health
  passed. Temp paths must all target writable tmpfs. Certificate installation and
  approved renewal/reload procedure are prepared; real ACME remains unverified.
- Phase 7: Java suite now 362 tests, 126 environment-gated skips; successful build.
  Production/simulation HTTPS gate preserves development HTTP behavior. Real proxy
  requests succeeded; direct insecure portal and forged forwarded-header request
  returned 403. Local Gemini endpoint override requires isolated simulation and
  a synthetic key; official production endpoint cannot be redirected by configuration.
- Scope update: user deferred functional additions. The in-flight teacher implementation
  was asked to stop expansion and preserve/report existing edits. Remaining preparation
  is infrastructure/operations only; unavailable application features are not fabricated
  or silently treated as accepted. Full functional journey is separate from infrastructure
  smoke acceptance until the application scope is approved.
- Phase 9: real age-encrypted MySQL backup restored into new
  `ppe-restore-validation-20261008` volume. All 23 migration rows and a synthetic
  persistence marker survived. A second restore into this nonempty DB was refused.
  Source DB container recreation retained its volume/data. No plaintext dump persisted;
  decrypt authentication is verified before streaming SQL to an empty isolated DB.
- Phase 10: all five production-simulation services healthy; no sandbox orphan remained.
  Log rotation/restart policies are configured. Memory/disk-only CloudWatch Agent config
  and a daily encrypted-backup systemd timer prepared (not installed or enabled in AWS).
  Certificate-trust smoke passed with the local certificate as explicit CA, without -k.
- Phase 12 in progress: commit-pinned archive/image manifest, approval/maintenance locks,
  backup-before-update, app-only rollback and smoke scripts created. Syntax and mutation
  rejection tested. Full real update/migration/rollback simulation remains a required gate.
