# Production deployment and continuous updates

**In preparation; not a production deployment approval.** See the
[phase results](production-preparation-plan.md). AWS resources, real student data
and real Gemini requests have not been exercised.

## Boundaries

- Development continues with the unchanged [development Compose](../../docker-compose.yml).
  Production uses [production Compose](../../compose.production.yml); never reuse
  development project names, DB volumes, accounts or environment files.
- Keep the production project `ppe-production` and its `database` volume stable
  across releases. Do not use `down -v`, `docker volume prune`, Flyway clean or
  automatic destructive database restoration.
- Build/test images before the maintenance window; pin release images to a full
  Git commit. Updates are outside classes, with a declared maintenance window.
  Persistent volumes and secrets are outside the release checkout.
- The configured steady-service memory limits total 1,472 MiB (app 640, MySQL
  512, runner 128, broker 128, Nginx 64), before up to four 128 MiB Python
  children, the OS, Docker and filesystem cache. Migration can use another 768 MiB
  while MySQL is running. The 1 GiB t3.micro is not suitable; choose capacity
  before EC2 rollout. These are configured limits, not measured EC2 usage, and
  there is no 300-student capacity claim.
- The broker alone has the host Docker socket. It is an explicitly trusted,
  root-equivalent component, not a VM sandbox. Runner has no socket or host ports;
  students receive network-none, nonroot, read-only children with enforced limits.

## Required secrets (approval before AWS creation)

SecureString names under `/programming-process-evaluator/prod/`:

| Name | Purpose | Status |
|---|---|---|
| `gemini-api-key` | Official Gemini API | User reports existing |
| `db-password` | `ppe_app` runtime CRUD only | User reports existing |
| `db-root-password` | Local DB administration only | Additional parameter |
| `db-migration-password` | Separate `ppe_migrate` schema account | Additional parameter |
| `student-credential-key` | 32 random bytes, standard base64, AES-GCM | Additional parameter |
| `runner-token` | At least 32 random printable non-whitespace characters | Additional parameter |

Database secrets must be at least 20 ASCII printable characters. Do not reuse
credentials. Preserve the AES key across updates: replacing it without a planned
rotation would prevent decrypting stored credentials. MySQL init runs only on an
empty volume; changing a Parameter does **not** change existing DB account passwords.
Credential rotation requires separate coordinated DB/app maintenance.

Extend the instance role's `ssm:GetParameter` resource list to these exact parameter
ARNs in `ap-northeast-1`; add narrowly scoped `kms:Decrypt` only if a customer-managed
KMS key is used. Do not give the app containers AWS credentials or SSM permissions.
Create parameters via an approved administrator/console without putting values
in Git, CLI argv, shell history, Compose or logs.

After approval, on EC2 (fetch is read-only, no Parameter mutation):

```sh
sudo install -d -m 0700 /var/lib/ppe /var/lib/ppe/secrets
sudo sh scripts/production/fetch-secrets.sh /var/lib/ppe/secrets/release-YYYYMMDD
export PPE_SECRETS_DIR=/var/lib/ppe/secrets/release-YYYYMMDD
```

The [fetch script](../../scripts/production/fetch-secrets.sh) publishes only a complete,
validated new snapshot and refuses overwriting an existing one. On failure there is
no usable partial directory. Parent directories are 0700; mounted files are 0444 for
nonroot container access, protected from host users by the private parent. Production
snapshots are never copied to a developer machine.

## Migration and recovery

```sh
export PPE_PROJECT=ppe-production
export PPE_COMPOSE_FILE=compose.production.yml
docker compose -p "$PPE_PROJECT" -f "$PPE_COMPOSE_FILE" up -d --wait db
sh scripts/production/migrate.sh
docker compose -p "$PPE_PROJECT" -f "$PPE_COMPOSE_FILE" run --rm --no-deps migrate \
  gradle --offline --no-daemon flywayValidate flywayInfo
```

MySQL binary logging remains enabled; `log_bin_trust_function_creators` remains 0.
V11 is not rewritten. The migration wrapper grants SUPER temporarily to the dedicated
migrator and revokes it on normal exit/errors/signals. Runtime never has SUPER/DDL.
After a forced kill/power loss, revoke explicitly before continuing:

```sh
printf "REVOKE SUPER ON *.* FROM 'ppe_migrate'@'%%';\n" |
  docker compose -p ppe-production -f compose.production.yml exec -T db sh /opt/ppe/db-admin.sh
```

Do not retry a partly applied migration blindly: MySQL DDL may commit before failure.
Inspect Flyway history and the schema, retain the encrypted pre-update backup, and
choose an reviewed forward fix or isolated restoration. Applied migration checksums
are immutable. Do not mark unsuccessful migration as successful with blind repair.

## Nginx and first TLS certificate

Only Nginx publishes ports. Defaults are **loopback**; exposing `0.0.0.0:80/443`,
Security Group changes, DNS changes and certificate issuance require explicit approval.
The proxy subnet/IP and app trusted proxy regex must match; trust only the proxy's
address, never arbitrary forwarded headers or the whole private network.

The selected bootstrap method is HTTP-01. It needs both DNS names to resolve to
the EC2 public address, inbound TCP/80 during issuance, a working outbound
connection to the ACME service, and Certbot installed on the host. DNS-01 via
Route 53 would avoid inbound port 80 but requires a DNS plugin and narrowly scoped
`route53:ChangeResourceRecordSets` permissions; it is not configured here.

Create the private host directories and set the initial release/Compose environment.
Do not start normal Nginx before a real certificate is installed:

```sh
sudo apt-get update
sudo apt-get install -y certbot
sudo install -d -m 0700 /var/lib/ppe/tls-private/current /var/lib/ppe/acme \
  /var/lib/ppe/certbot/config /var/lib/ppe/certbot/work /var/lib/ppe/certbot/log
export PPE_PROJECT=ppe-production
export PPE_COMPOSE_FILE=/var/lib/ppe/releases/<commit>/source/compose.production.yml
export PPE_RELEASE=<40-character-commit>
export PPE_SECRETS_DIR=/var/lib/ppe/secrets/<secret-snapshot>
export PPE_TLS_DIR=/var/lib/ppe/tls-private/current
export PPE_ACME_DIR=/var/lib/ppe/acme
export PPE_CERTBOT_CONFIG_DIR=/var/lib/ppe/certbot/config
export PPE_CERTBOT_WORK_DIR=/var/lib/ppe/certbot/work
export PPE_CERTBOT_LOG_DIR=/var/lib/ppe/certbot/log
export PPE_ACME_EMAIL=ops@example.com # replace with the monitored operations address
export PPE_BIND_ADDRESS=0.0.0.0
export PPE_TLS_BOOTSTRAP_APPROVED=yes
bash /var/lib/ppe/releases/<commit>/source/scripts/production/tls-bootstrap.sh
```

The bootstrap refuses an existing TLS deployment, requires explicit approval, starts
the HTTP-only ACME challenge service, requests one certificate containing both
domains, validates certificate SANs/key match/expiry, installs it, then runs
`nginx -t` and reload. A mock bootstrap and renewal hook were exercised locally;
public issuance and `certbot renew --dry-run` remain unverified and require approval.
After issuance, set `PPE_BIND_ADDRESS=0.0.0.0` only when the EC2 Security Group and
operator approval permit ports 80/443. Nginx redirects HTTP to HTTPS.

Install renewal with the host Certbot timer and a deploy hook. Export the same
`PPE_PROJECT`, `PPE_COMPOSE_FILE`, `PPE_TLS_DIR`, and `PPE_CERTBOT_CONFIG_DIR`
environment in a root-readable, mode-0600 environment file that contains paths
only, not secret values. Configure:

```sh
certbot renew --deploy-hook \
  '/var/lib/ppe/releases/<commit>/source/scripts/production/tls-renew-hook.sh'
systemctl list-timers --all | grep -i certbot
```

The hook checks both renewed domain names, installs atomically, tests Nginx, and
reloads it; on ordinary validation/reload failure it restores the previous
certificate/key and attempts another tested reload. Review Certbot logs and
certificate expiry after every timer run. SIGKILL/power loss during host file
replacement still requires manual inspection.

The TLS directory is 0755/files 0444 for the nonroot proxy; its host parent is 0700.
Do not place it under a public/shared parent. The locally generated dummy self-signed
certificate is only for verification and must never be used for public rollout.
Access logs are disabled to avoid identifiers/query leakage; bounded error/container
logs remain. Request limit is 4 MiB to preserve existing exercise-upload constraints.

## Verification completed locally

- Production WAR on Java 21/Tomcat 9 and DB-backed `/health`.
- Fresh MySQL V1–V23, `flywayValidate`, migration rerun; runtime CREATE denied.
- Synthetic compatible V24 update from a V23 database; exact release resumed after
  a pre-migration validation interruption, migration history validated, and smoke
  test passed before release publication.
- Distinct pre-V24 app image rollback after the V24 update; the V24 history row and
  synthetic compatibility data remained present, old app health/login smoke passed,
  and no DB restore was performed.
- App-first startup while the disposable MySQL service was stopped, followed by
  MySQL recovery: Hikari/listener startup retried transient connection failures,
  the app returned healthy, and the V24 row/history remained present.
- Real broker-constrained synchronous/interactive Python, error/timeout/concurrency,
  network/file isolation and output limit.
- Parameter retrieval with a **fake AWS CLI**, complete publish and failclosed failure.
- Nginx config check, TLS, student/teacher routing, redirect and Secure/HttpOnly/Lax
  cookies using dummy certificates. Direct insecure portal requests and untrusted
  forwarded-header spoofing are rejected.
- Initial-admin pseudo-TTY registration, duplicate rejection, authorized teacher
  login and first-login password change were exercised in the local simulation.
- Encrypted backup restored into a separate empty test DB; Flyway history, triggers
  and seeded record counts were checked.
- JUnit: 372 total, 244 passed, 0 failed, 128 skipped. The skipped tests are
  opt-in DB/browser-fixture/API diagnostics, including billable Gemini API checks;
  their skip tags do not establish those paths as passing.

The first update attempt exposed two production-script defects before the migration
ran: Flyway validation treated the newly pending V24 as invalid, and the completed
release marker stored a directory path instead of the commit ID. The validation now
ignores only `*:pending` while checking applied checksums (the workflow separately
requires exact target version/count); publish stores the release commit. The real
local update/resume and app-only rollback were then run successfully.

The same test found that MySQL emitted grant names with backticks, while residual
`SUPER` detection expected single quotes. The migration user retained global `SUPER`
after V24 until the corrected checker normalized quote styles and revoked it. A
regression test covers backtick/single-quote grants and fails closed on global
`ALL PRIVILEGES`; local DB grants were rechecked after cleanup. The application
user has only schema CRUD; the migrator retains schema-scoped DDL rights.

Docker restart policies do not guarantee Compose dependency health ordering after a
host reboot. The app data source now tolerates transient MySQL connection failures
during Tomcat initialization and waits for MySQL before starting background workers.
Non-connection SQL failures (for example invalid credentials) still fail startup;
the app health alarm must be checked rather than treating a running container as
ready.

## Release/update procedure

### Commit-pinned release build

Do not prepare a release from uncommitted working-tree files. After the user has
committed and reviewed the intended tree, record its full SHA and run from a clone
that contains that commit:

```sh
COMMIT=replace-with-the-reviewed-40-character-commit-sha
git fetch --no-tags origin "$COMMIT"
bash scripts/production/prepare-release.sh "$COMMIT" /var/lib/ppe/releases
```

The script archives the selected commit, builds all seven images, checks embedded
image revision labels, writes immutable image IDs and creates a `READY` marker.
Never overwrite a release directory or retag its images. The current worktree has
not yet been committed; the final commit-pinned release must therefore be prepared
and revalidated after the user completes Git operations.

### Initial release

1. Select an EC2 size as in [capacity planning](#capacity-planning); do not deploy
   the full stack to the current 1 GiB t3.micro.
2. Confirm the instance role can read each required SecureString in `ap-northeast-1`.
   Run `fetch-secrets.sh` into a new private snapshot; never copy values into shell
   history or a `.env` file. The fetch requires six values, including four additional
   parameters beyond the two the operator reported as existing.
3. Prepare TLS directories and run the approved HTTP-01 bootstrap above. Confirm
   both DNS names resolve and the public challenge is reachable first.
4. Set project, secret, TLS, ACME, proxy subnet/IP and bind/port environment; confirm
   `docker compose ... config --quiet` succeeds and only Nginx binds 80/443.
5. Run the initial release using its absolute immutable directory:

   ```sh
   bash scripts/production/deploy-release.sh initial \
     /var/lib/ppe/releases/<full-commit-sha>
   ```

   The command records durable stages, runs migration with a separate account,
   starts broker/runner/app/Nginx, executes smoke checks, then publishes the commit
   marker. On failure preserve the DB and journal; follow [recovery](#interrupted-deployment).
6. From an interactive Session Manager terminal, create the administrator. The
   launcher requires a real TTY and reads the password without echo or argv:

   ```sh
   bash scripts/production/create-initial-admin.sh
   ```

   Verify the initial password-change screen and change it immediately. Duplicate
   bootstrap is refused.
7. Register the reviewed standard rubric with the migration/tools image (never
   enable the local-only `seedDemoData` task in production):

   ```sh
   docker compose -p ppe-production -f "$PPE_COMPOSE_FILE" \
     run --rm --no-deps migrate gradle --offline --no-daemon registerStandardRubric
   ```

8. Create test school/teacher/student accounts and a test task through the authorized
   application workflow, using only synthetic data; this is a manual step until a
   reviewed production-safe demo-seed workflow and full browser E2E are available.
9. Run the [smoke script](../../scripts/production/smoke.sh), verify both portals,
   login/role restrictions, database health, Python sandbox runtime and mocked AI
   failure handling. Do not send a real Gemini request during this initial check.

### Interrupted deployment

State is kept in the private `PPE_STATE_DIR` (0700); the atomic journal/event file
records release ID, previous release, policy, phase, baseline and target versions.
An exclusive lock prevents concurrent deploy/rollback. Retry only the same release
directory with the same schema policy:

```sh
RELEASE_DIR=/var/lib/ppe/releases/replace-with-the-same-40-character-commit-sha
bash scripts/production/deploy-release.sh initial "$RELEASE_DIR"
# For an interrupted update, use update instead of initial:
bash scripts/production/deploy-release.sh update "$RELEASE_DIR"
```

Before resuming, verify the active marker, state file, `ppe.flyway_schema_history`
and checksums. A failed Flyway record or partial state between baseline and exact
target is intentionally fail-closed. Do not retry, `flyway repair`, drop tables,
delete the volume or restore into the active DB. Retain the encrypted pre-update
backup and have a DBA inspect the DDL/history; choose a reviewed forward fix or an
explicitly approved isolated restore/cutover. A process killed by SIGKILL or power
loss can leave the migrator `SUPER` privilege; before any migration, run
`ensure-migration-privileges.sh` and verify `SHOW GRANTS`. The script detects
MySQL's backtick and single-quote grant formats and refuses unknown global grants.

### Normal update and app-only rollback

Build a new immutable release from a committed SHA, review all migrations as
expand/contract or otherwise backward compatible, then outside class hours:

```sh
export PPE_SCHEMA_POLICY=backward-compatible
bash scripts/production/deploy-release.sh update \
  /var/lib/ppe/releases/<new-full-commit-sha>
```

The update stops app/runner, creates an encrypted backup, validates applied
checksums, migrates, restarts services, smoke-tests and publishes only after success.
For rollback, pass the selected previously built release directory:

```sh
bash scripts/production/rollback-release.sh \
  /var/lib/ppe/releases/<previous-full-commit-sha>
```

Rollback changes application images only; it does not roll back schema or data.
Use only when the old application is compatible with the current schema. The tested
V24 case retained its schema row and test data after returning to a distinct V23 app.
Do not use an app rollback to recover from destructive schema/data changes.

### Backup and restore

Generate an `age` identity on a trusted offline admin workstation and store its
private key separately from EC2. Install only its public recipient string in a
0600 host file; set `PPE_BACKUP_RECIPIENT_FILE`. Keep the encrypted DB backup and
SHA-256 sidecar in a 0700 directory on a separate encrypted filesystem, with
`PPE_BACKUP_RETENTION_DAYS` (default 14) and `PPE_BACKUP_MIN_FREE_MIB` (default
1024). The script fails before dump if space is insufficient and does not print
database contents.

```sh
bash scripts/production/backup.sh
```

The script's backup-failure notification is currently the command's nonzero status
and bounded Docker stderr; schedule it from systemd/cron with an operator-visible
failure alert. Local backup alone is not off-host disaster recovery. After approval,
copy `.sql.age` and `.sha256` to a separately administered S3 bucket with versioning,
SSE-KMS, restrictive bucket policy and lifecycle retention. Grant only the required
prefix `s3:PutObject`/`s3:GetObject` and KMS use to the instance role; do not grant
delete. Confirm object encryption/versioning and restore an object into a fresh
isolated DB regularly.

`restore-isolated.sh` accepts only an explicitly approved empty `ppe-restore-*`
project. It verifies SHA-256 and age decryption before writing, and refuses a
nonempty DB. The SHA-256 sidecar detects accidental corruption only; because it
is stored beside the backup, it does not authenticate the backup or prove who
created it. Before production cutover, verify provenance through an independently
protected signature or trusted off-host control; do not treat a matching checksum
alone as authorization to restore. This is not a production cutover tool. A
production disaster restore must be a separate approved runbook with outage
declaration, data-loss/RPO review, point-in-time source selection, restore to a
replacement isolated volume, integrity checks and explicit cutover; restoring over
the active DB is not automated.

## Capacity planning

| Scenario | Current assessment | Candidate to load-test, not a guarantee |
|---|---|---|
| A: one administrator | t3.micro may start only selected components, but 1 GiB is below the configured complete stack | t3.small/medium for isolated admin checks only |
| B: 5–10 testers | 1 GiB is insufficient; 4 Python child slots can contend | 4–8 GiB, at least 2 vCPU; begin at 8 GiB if all services are on one host |
| C: 30–40 students | runner accepts at most 4 children; others queue/reject per runner policy | 8–16 GiB and 4+ vCPU, then measure queue latency; consider isolating DB/executor |
| D: about 100 concurrent users | no benchmark; single EC2, DB and 4-child runner are bottlenecks | do not promise capacity; redesign/scale runner and database after load tests |
| E: about 300 concurrent users | no evidence of support | not approved; architecture and load testing required before sizing |

For a small single-host pilot, compare an 8 GiB general-purpose instance such as
`m7i.large` with a 16 GiB/4-vCPU candidate such as `m7i.xlarge`; instance family,
regional availability and current price must be checked in the AWS console. The
4-GiB `t3.medium` is a cheaper admin-only candidate, not recommended for a complete
classroom stack. T3 CPU credits can deplete under sustained compilation or Python
load. Estimate monthly cost with AWS Pricing Calculator using ap-northeast-1,
instance hours, EBS GiB/type/IOPS, public IPv4, snapshots, S3/KMS, data transfer and
CloudWatch ingestion; include stopping nonproduction instances. No price or
performance has been independently measured in AWS.

Collect `docker stats`, `free -m`, `vmstat 1`, `iostat -xz 1`, runner queue/rejection
counts, response latency and MySQL connections under synthetic workloads. In the
current configuration app has 640 MiB/1 CPU (`-Xmx384m`), MySQL 512 MiB/1 CPU
(128 MiB InnoDB buffer pool, max 40 connections), runner 128 MiB/0.5 CPU, broker
128 MiB/0.5 CPU, Nginx 64 MiB/0.5 CPU; migration tools can use 768 MiB. Broker limits
four Python children, each to 128 MiB/0.5 CPU, 32 PIDs, 64 KiB source, 256 KiB
request, 1 MiB file size and 70-second lifetime. These are ceilings, not sizing
measurements. Monitor OOM events and leave headroom for the kernel/cache; do not
raise limits or child concurrency without a measured capacity review.

One post-rollback idle snapshot on macOS Docker Desktop read app 175 MiB, MySQL
427 MiB (83% of its 512 MiB limit), runner 14 MiB, broker 14 MiB and Nginx 7 MiB.
The instantaneous broker CPU sample was 19%; it is not a sustained benchmark.
This is a local virtualization snapshot with no student workload or Python child
execution and must not be used as an EC2 forecast. It does show that even idle
MySQL can approach its configured memory ceiling on this test stack.

Load-test in stages A–E with a production-equivalent isolated stack and generated
dummy accounts: ramp login/task page reads, then synchronized Python executions,
then submissions/mock evaluations; test mixed peak and sustained duration, runner
failure and DB restart. Record p50/p95/p99 latency, error/queue rate, CPU steal/credit
balance, memory/OOM, disk and DB connection saturation. Acceptance thresholds must
be agreed before each class rollout. The current four-child runner is a hard
throughput constraint and EC2 memory alone will not remove it.

## Monitoring and manual AWS setup

The local CloudWatch Agent file at
[`cloudwatch-agent.json`](../../containers/production/cloudwatch-agent.json)
collects memory-used percent and root-disk-used percent every 60 seconds. CPU and
EC2 status checks are available from the platform; this file does not collect
Docker health, app logs, backup status or TLS expiry. After approval:

1. Install the CloudWatch Agent for Ubuntu and apply the checked-in config with
   `amazon-cloudwatch-agent-ctl`; verify agent status and `CWAgent` memory/disk
   metrics in `ap-northeast-1`.
2. Preserve the reported EC2 status-check and CPU alarms; add alarms for memory,
   root disk, CPU credit balance/usage (if T family), and instance reachability.
3. Route alarms to an operator-confirmed SNS topic. Add an alert for backup command
   failure/missing daily object, TLS expiry threshold, unhealthy app/DB/runner, and
   repeated Python broker failures. Container health is currently local Compose
   health; it is not automatically a CloudWatch metric.
4. Configure bounded Docker `json-file` rotation (10 MiB × 3 per container) and
   review `docker compose ps`, `docker compose logs --tail=200`, disk use and the
   service restart procedure in the maintenance runbook. Nginx access logs are
   disabled to avoid query/user identifiers; keep error logs bounded.

AWS instance role, real Parameter Store reads, DNS, Security Group, public TLS,
CloudWatch Agent/alarms/SNS, off-host backup bucket and AWS instance sizing remain
manual verification. The config file and fake SSM tests do not prove AWS IAM or
service behavior.

## Remaining release gates

AWS-only first boot, IAM/SSM retrieval, Route 53 resolution, public HTTP-01 issuance,
CloudWatch, off-host S3 backup/restore, and the final committed release remain
unverified. Full browser E2E was not run (Node.js/Playwright are unavailable in the
validation environment); application flows beyond the administrator login and
smoke checks require a manual dummy student/teacher journey. The local production
database migration/update/rollback path is verified with synthetic V24. Global
`SUPER` is now cleaned up on normal migration exit and detected on next migration
after forced termination; SIGKILL itself cannot run a trap, so the documented
pre-migration privilege check is mandatory.

Real Gemini model availability/price/quota/API contract, personal data in free text
or source code, study consent/withdrawal and school authorization policy require
manual review before real-student use. `store=false` and identifier redaction are
not general anonymization guarantees. Real student data and real Gemini API requests
were not used.
