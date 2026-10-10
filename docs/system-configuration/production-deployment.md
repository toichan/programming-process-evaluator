# Production deployment and continuous updates

**Initial production deployment completed on 2026-10-09 JST.** The
[observed production record](#observed-production-record-2026-10-09-jst) below
supersedes the earlier preparation status. Infrastructure publication is not
approval to collect research data or to execute the mutation commands in this
runbook. See [preparation results and system-test plan](production-preparation-plan.md).
Production business-flow acceptance and disaster restoration remain unverified.

**2026-10-10 status:** ECR acquisition and the exact-release recovery/rollback
references have now been accepted; application deployment remains unapproved.
See the [current ECR acceptance and reference pins](./ecr-manual-deployment.md#最終reference確認と承認境界2026-10-10-jst).
The earlier preparation/install statuses below are historical, not instructions
to reinstall completed operations. The Stage C record proves initial-state
restoration only, not populated research-data recovery.

## Update hardening (2026-10-10 JST, local only)

This section defines the **new repository contract**, not the version installed
on EC2. The installed `de2b0d37...` deployment operations must not be used for the
next update: they predate S3 receipt verification and the shared database lock.
The application release, installed backup operations and accepted TLS operations
remain unchanged. Production application and operations rollout require separate
approval. No commit/push/merge is performed by this local implementation.

### Required evidence before stopping the application

- Prepare an immutable full-SHA release with `prepare-release.sh`. It records
  seven image IDs, a complete `source.sha256` manifest and READY. Independently
  pin the manifest digest as `PPE_SOURCE_MANIFEST_SHA256`; optionally use a
  separately distributed `PPE_SOURCE_MANIFEST_FILE`. Changed, missing, added or
  non-regular source assets fail verification. A hash is not a signature: obtain
  the digest through the reviewed distribution channel, not from an untrusted
  destination alone.
- Preserve the previous release and all seven image-ID records needed by the
  current rollback/operation contract. Missing legacy records/images are a
  pre-stop blocker, not permission to fabricate records or retag existing images.
  Confirm the actual installed old release's rollback eligibility before rollout.
  Pin `PPE_PREVIOUS_SOURCE_MANIFEST_SHA256` and, for a legacy release without an
  in-release manifest, `PPE_PREVIOUS_SOURCE_MANIFEST_FILE`. Generate that detached
  manifest from the trusted Git archive and compare it with the existing source;
  **do not add files to or rebuild/overwrite the immutable old release**.
- Pin the full 64-character `PPE_RECOVERY_DB_CONTAINER_ID`, exact
  `PPE_RECOVERY_DB_VOLUME_CREATED_AT` and `PPE_DB_RELEASE`. The DB recipe, image,
  Compose config hash, health, project labels and named writable Volume must
  match. Update and rollback never start/recreate/stop the DB or replace a Volume.
  A DB image/recipe change needs a separate reviewed procedure.
- Flyway validate/info and the exact applied version/script/checksum/success
  inventory run before quiesce. Pending migrations are computed from the complete
  version inventory, not merely an applied count. Repeatable/unrecognised/failed
  history or duplicate versions fail closed. This deployment contract currently
  supports only versioned SQL migrations. Flyway `validate` remains the authority
  for comparing applied checksums with source SQL.
- Pin existing private `PPE_BACKUP_OPERATION_ENV` and `PPE_TLS_OPERATION_ENV`
  files. Their project, current release/Compose/DB references and credential,
  TLS and ACME paths must agree with the selected existing runtime.
  Secret-path rotation is outside this procedure.
- Pin `PPE_TLS_OPERATION_WRAPPER` to the actual `certbot.service` wrapper.
  Before adopting these deployment scripts, separately approve deployment of
  the new wrapper with the **shared FD8 TLS operation lock**. Its bytes must
  match the reviewed operations version; production preflight also verifies
  service ExecStart and that Certbot is inactive. Merely putting an unused
  wrapper elsewhere is insufficient. Existing accepted `3c416a2e...` TLS assets
  are immutable; distribute a new independent operations version instead.
  No certificate, Certbot directory, service schedule or timer enablement is
  changed by update/rollback.
- Set `PPE_BACKUP_RECOVERY_REFERENCE` to a private, owned, regular 0600 file:
  `age_recipient=<approved-public-recipient>`, `restore_verified=yes`,
  `custody_reference=<non-secret-offline-custody-and-rehearsal-reference>`.
  This is an operator-reviewed reference to the offline age identity and a
  successful restore rehearsal, **not the identity itself**. Never transfer the
  private identity to EC2. The reference hash and recipient are bound to backup
  evidence; the software cannot independently prove continued offline key custody.

### Mandatory backup and migration gate

The existing S3 backup implementation is reused. Each update receives a random
32-hex deployment ID. Its receipt records the **source (currently published)
release**, target release, full DB ID, named Volume/CreatedAt, baseline history
hash, migration inventory hash, recovery-reference hash and public age recipient.
It also retains data/checksum/manifest keys, exact S3 Version IDs, SHA-256, size,
SSE-KMS and KMS identity. Scheduled receipts retain their existing format-1
compatibility; extra fields are required only for deployment evidence.

`PPE_BACKUP_REQUIRE_REMOTE=no` cannot bypass the update gate. Backup must complete
all uploads, fixed-version HEAD checks, exact-version manifest download/content
comparison and success-metric publication. The private receipt hash is persisted
in format-2 deployment state and an attempt-specific `backup-<ID>/` evidence
directory. A previous receipt is retained under its hash, not discarded. Backup
failure/partial S3 uploads are preserved; they are not silently retried or deleted.
The receipt and DB identity are checked again immediately before migration and
on resume. The actual encrypted data is not downloaded/decrypted during every
deployment; periodic isolated restore rehearsals remain necessary.

If no SQL is pending, migration is skipped only after exact inventory and
checksum validation; a fresh deployment backup is still mandatory. Before the
first mutating Flyway call, `migration_attempted=yes` is synced to state.
A failed or interrupted attempt that has not reached the validated complete
target cannot be automatically retried, even when MySQL committed DDL without
creating a failed Flyway row. Never use automatic `repair`, `clean`, DB restore
or downgrade.

### Failure, operation synchronization and acceptance

- Before migration, a failure after quiesce attempts to restart only the verified
  old app/runner, validates five service health/image IDs and both HTTPS smoke
  checks, and records `apps_restored=yes`. Failure always remains nonzero; a
  failed recovery is explicitly reported. Once old-app writes resume, that
  receipt cannot authorise migration. A reviewed rerun must explicitly set
  `PPE_RESTART_RECOVERED_UPDATE=yes` and creates a **new attempt and backup**,
  preserving the abandoned attempt's state/receipt.
  `recovery_attempted=yes` is persisted before any old-app start. Even failed
  recovery may have resumed some writes, so it also requires a new attempt/
  backup rather than reuse of the completed quiesce stage.
- After migration begins, there is no automatic old-app or DB restoration.
  Inspect the actual schema/history and choose reviewed forward repair or app
  rollback. MySQL DDL is not a transaction-wide rollback. Restoring a DB requires
  isolated restore/validation and a separately approved cutover; new writes
  since the backup require an explicit RPO/data-loss decision.
- App rollback requires `PPE_SCHEMA_POLICY=backward-compatible` and a private
  `PPE_ROLLBACK_COMPATIBILITY_REFERENCE`. It contains `source_release`,
  `target_release`, `source_container`, `history_hash` and `compatible=yes`,
  bound to the **current ordered Flyway history**. That flag represents a human
  compatibility review; the script cannot infer application/schema semantics.
  The prior state and backup evidence are retained. The current DB is not undone.
  Re-running an already completed rollback rechecks DB, operation references,
  five running images/health and HTTPS; an old success marker does not hide drift.
- Before publication, backup and TLS environment release/Compose references,
  pinned DB source/release and backup image are synchronised. Unrelated settings
  and all secret/TLS/ACME paths are preserved. Both private before-copies and a
  journal remain under `operation-config-sync/<attempt-or-context-ID>/`.
  Each rename is atomic; the two-file update is not globally atomic. A second
  rename/durability failure restores both before-copies, reports nonzero and
  preserves evidence (including when rename succeeded but `sync` failed).
  Interrupted/failed recovery journals block further operations for manual
  review. Restore the recorded before-copies or reconcile the selected generation,
  verify current/state/config references, and only then clear the specific
  reviewed stale lock/journal status; never delete deployment state or evidence.
- Database FD9 serialises deployment and scheduled backup. FD8 serialises full
  Certbot wrapper execution and deployment/rollback, preventing stale TLS
  environment readers from reloading during configuration publication. Contention
  fails visibly before renewal or deployment; timers are not disabled/rescheduled.
  Check subsequent timer execution/alarms after maintenance, since a contended
  scheduled job can report a failure and waits for its normal next execution.
- Five service health and running image IDs, preserved DB identity, trusted HTTPS
  `/health` and both login portals, operation references and evidence archival must
  pass before publication. Keep the previous release. Authenticated business
  flows and real LLM acceptance remain separate checks, not implied by smoke.

### Local testing strategy and acceptance scope

Reuse existing shell regressions with edits; add source-manifest, exact migration
inventory and two-environment sync tests. Run Docker/AWS/Certbot/curl contract
mocks inside cached Ubuntu with `--network none`, read-only repository mount and
a private executable tmpfs. Deployment tests call the **actual** backup and
receipt scripts with a shared mocked S3 API, rather than only replacing both
helpers. They cover upload/HEAD/download failures, mandatory remote backup,
unrecorded partial DDL, unsafe resume, image drift, sync failure and reviewed/
incompatible rollback. Linux provides real `flock` and FD inheritance.

Run `bash scripts/production/tests/backup-roundtrip-test.sh` on the local Docker
daemon with unique `ppe-sim-*` / `ppe-restore-*` projects and synthetic secrets.
It creates two isolated MySQL databases, checks encrypted dump/age restore,
additive SQL preservation of user/log/submission/evaluation IDs/content/JSON/FKs,
real partial-DDL persistence, container/Volume identity and nonempty-restore refusal,
then removes **only its own** fixtures. This is a representative five-table
fixture, not the complete V1-V23 business schema or a real future feature migration.
Each future migration still requires its own existing-data rehearsal.

No UI or application code changes are made. Live AWS/S3/KMS permissions, actual
production Flyway/HTTPS/five-container update, full browser/business journeys,
real LLM and host reboot are **unverified in this local phase**. The isolated
real-MySQL and mocked orchestration layers must not be presented as production
end-to-end acceptance.

### Local acceptance record (2026-10-10 JST)

Reproducible entrypoint: `bash scripts/production/tests/deployment-hardening-test.sh`.
It requires a local Unix Docker daemon, the cached images listed in the runner
and local age tools. Optional `PPE_HARDENING_TEST_OUTPUT_DIR` is an existing,
owned, absolute 0700 directory for private logs. A temporary synthetic age
identity is generated outside Git and deleted on exit; no production identity,
credentials, data or AWS endpoint is used.

Final command:

```bash
PPE_HARDENING_TEST_OUTPUT_DIR=/Users/t.toida/.copilot/session-state/d6d17c93-fe8b-4b00-b4d5-ce6bfb916c78/files/deployment-hardening-validation \
  bash scripts/production/tests/deployment-hardening-test.sh
```

| Suite | PASS assertion/scenario groups | Exit | Skip |
|---|---:|---:|---:|
| source-manifest | 6 | 0 | 0 |
| migration-preflight | 4 | 0 | 0 |
| operation-config-sync | 11 | 0 | 0 |
| deployment-state | 3 | 0 | 0 |
| deployment-recovery | 57 | 0 | 0 |
| backup | 2 | 0 | 0 |
| database-lock | 4 | 0 | 0 |
| migration-privilege-cleanup | 4 | 0 | 0 |
| shell-invocation | 4 | 0 | 0 |
| tls-workflow | 35 | 0 | 0 |
| restore | 1 | 0 | 0 |
| backup-systemd | 1 | 0 | 0 |
| deployment-db-config-hash | 2 | 0 | 0 |
| backup-operation-config | 1 | 0 | 0 |
| age-recipient | 1 | 0 | 0 |
| db-admin-binary-mode | 1 | 0 | 0 |
| backup-roundtrip | 3 | 0 | 0 |
| nginx-startup | 4 | 0 | 0 |
| **Total: 18 suites** | **144** | **0** | **0** |

These are printed assertion/scenario **groups**, not JUnit test-case counts;
individual groups contain several rejection checks. The first twelve suites
use isolated Linux contract mocks. The host suites exercise real Compose
parsing, real age encryption/decryption, isolated MySQL backup/restore and DDL
preservation, plus actual isolated Nginx startup/HTTPS/ACME routing. They do not
update a real five-container application release or exercise live AWS.
The initial runner failed because recipient-test arguments were missing.
A subsequent macOS Bash 3 empty-array expansion aborted early and its EXIT
cleanup incorrectly returned zero; it was not accepted as success. Synthetic
key setup, array-free invocation and a completion gate fixed both issues;
the final run reached its completion marker and all 18 suites exited zero.

`git diff --check` passed. Current-run isolated containers/Volumes and temporary
age identities were cleaned; the pre-existing `ppe-sim-update-20261008_database`
Volume was left untouched. Existing unrelated/pre-task documentation changes
were preserved. No commit, push, merge or production change was performed.

## Observed production record (2026-10-09 JST)

Read-only EC2/SSM inspection at approximately 08:42–08:45 JST, supplemented by the
approved deployment verification completed around 08:36 JST. This is a dated
snapshot, not a continuously refreshed inventory. No backup, restore, user/seed
registration, business-flow test, restart or AWS change was performed during this
documentation investigation. SQL read only aggregate counts and configuration.
The DB admin helper creates/removes a temporary private client-options file inside
the DB container; it does not change business records or privileges.

| Item | Confirmed value / limitation |
|---|---|
| AWS | `ap-northeast-1`, AZ `ap-northeast-1a`, EC2 `i-0ffd69e8f390bd396`, running, `t3.medium` |
| Host | Ubuntu 24.04.5 LTS, x86_64; approximately 3832 MiB RAM, no swap |
| IP / management | Public `13.112.85.237`, private `172.31.42.223`; SSM profile `ppe-deployer`, instance Online at deployment preflight |
| EBS | Root `/dev/sda1`, volume `vol-011b21091f0e4acb1`, `DeleteOnTermination=true`. Host `/` is ext4, 19 GiB, approximately 8.7 GiB free. AWS EBS type/encryption/IOPS are **unconfirmed**: `DescribeVolumes` denied |
| IAM / network | Instance profile `ProgrammingProcessEvaluatorEC2Role`; SG `sg-0574dda5308641e71`. SG rules and effective IAM policies **unconfirmed**; no permissions changed |
| IMDS | Tokens required, hop limit 2, endpoint enabled, IPv6 metadata disabled |
| Docker | Engine 29.8.2 / API 1.56; Compose v5.6.0; project `ppe-production` |
| Application release | `0e2bfae5e23bc512f0b7cf844264d4d45a484ecc` under `/var/lib/ppe/releases/<SHA>/source` |
| Operations code | `de2b0d37fb7ccb6ecae93ce81b2e01d230034e01` under `/var/lib/ppe/operations/<SHA>/scripts/production`; not the application release |
| Journal | `/var/lib/ppe/state/deployment.state`: format 2, mode/policy `initial`, phase `published`, baseline 0, target 23, previous empty, updated `2026-10-08T23:35:58Z` (08:35:58 JST) |
| Markers / locks | `current-release` equals application SHA; previous-release and deployment/migration locks absent at completion |
| Journal evidence | SHA-256 `6ccb65ad9779073b43ae9cf21fba4a66716089864347dd3e0fbefc37cdfdaa16`; events `e12c2f06ddc511dfe114b9faa6de54d35a3f1c1b1fcb55891f3ba5d21c1def97` |
| Private evidence | `/var/lib/ppe/operations/<operations-SHA>/evidence`: pre-deploy state/events, asset fingerprints and private deployment/validation logs; **not a DB backup** |
| Secrets | Existing `/var/lib/ppe/secrets/release-<application-SHA>`, six file secrets; contents not inspected/output. Snapshot unchanged at completion |
| TLS / ACME | `/var/lib/ppe/tls-private/current` mounted read-only at `/etc/nginx/tls`; `/var/lib/ppe/acme` mounted read-only at `/var/www/acme` |
| Certificate | Let's Encrypt issuer YE2, SAN student.ppeval.net / teacher.ppeval.net, expires `2027-01-06 07:22:08 UTC`; key match confirmed during deployment |
| DNS / HTTPS | Both DNS IPv4s matched EC2 at preflight. External trusted HTTPS `/health` exact `{"status":"ok"}` and each portal login HTTP 200 confirmed at completion; not re-run as a business test here |
| App data | Aggregate counts at investigation: users/tasks/submissions/code_logs/rubrics/evaluations/survey_responses all 0. No initial admin or standard rubric registered. This is not a guarantee about future writes |
| Backup | `/var/lib/ppe/backups` exists, mode 0700, but no matching backup artifacts found. No configured `operation.env` or loaded PPE backup unit; details below |

#### Running services and persistence

Effective container limits/mounts were checked with filtered inspect output;
environment values and complete container inspection were not dumped.

| Service / container suffix | Short ID | Health / restart count | Memory / CPU limit | Exposure / persistence |
|---|---|---|---|---|
| `db-1` | `cead40118ed8` | healthy / 0 | 512 MiB / 1 CPU | No host DB port; RW named database volume, RO secrets |
| `docker-broker-1` | `dd5f3c4503ef` | healthy / 13 | 128 MiB / 0.5 CPU | Internal 2375, host Docker socket bind; counts include resolved missing-runtime failure |
| `python-runner-1` | `134e92448530` | healthy / 0 | 128 MiB / 0.5 CPU | Internal 8090, RO token secret; no host Docker socket |
| `app-1` | `d232e57a708b` | healthy / 0 | 640 MiB / 1 CPU | Tomcat internal 8080; WAR image, no development source bind |
| `nginx-1` | `27b58ce9cbb1` | healthy / 0 | 64 MiB / 0.5 CPU | `0.0.0.0:80/443`; RO rootfs, RO TLS/ACME mounts |
| `acme-bootstrap-1` | `42e4a7ca5a4f` | Exited(0) | Not a steady service | Remains stopped; not the public proxy |

All five running services use `unless-stopped`. Docker json-file rotation is
configured at 10 MiB × 3 files per container (live app/nginx checked). App rootfs is
writable; Nginx rootfs is read-only. Broker remains a trusted root-equivalent host
component, not VM isolation. All seven commit-tagged release image IDs matched
their immutable `.id` records after deployment; none were rebuilt/retagged.

MySQL is 8.0.44, schema `ppe`, utf8mb4 schema definitions. Actual settings:
max_connections 40, InnoDB buffer pool 134217728 bytes, REPEATABLE-READ,
binary logging ON, log_bin_trust_function_creators OFF, binlog expiration 2592000
seconds. No non-InnoDB tables were returned for `ppe`.
`ppe_app` runtime CRUD and `ppe_migrate` schema DDL separation is defined in
[init-db.sh](../../containers/production/init-db.sh); migrator live grants were
schema ALL and global USAGE, without SUPER. Runtime grants were not re-read here.
Init scripts run only on an empty MySQL data directory; **do not initialize the
existing volume** to apply a configuration change.

Named volume `ppe-production_database`, local driver, created
`2026-10-08T08:32:49Z`, is mounted RW at `/var/lib/mysql`; host storage is
`/var/lib/docker/volumes/ppe-production_database/_data`. Full DB ID:
`cead40118ed8a27ce90a8f745f7b69ed528355149c7412d2c95985b6e47872d8`,
StartedAt `2026-10-08T08:32:49.719977603Z`. These remained unchanged after recovery.
Local volume is **not independent of the EC2/EBS failure domain**.

Networks checked: proxy internal `10.231.77.0/24` (proxy IP `10.231.77.10`);
database internal `172.19.0.0/16`; broker internal `172.20.0.0/16`; execution
internal `172.21.0.0/16`; edge `172.18.0.0/16` and outbound `172.22.0.0/16`
non-internal. These dynamically allocated ranges must be rechecked before changes.

Live Nginx config hash matches the release's
[nginx.conf](../../containers/nginx/nginx.conf); `nginx -t` passed.
TLS 1.2/1.3, HTTP 308 to HTTPS except ACME challenge, unknown host rejection,
root redirect to role login, HSTS, forwarded-header overwrite and app proxy are
configured. Access log is off; warning/error log goes to stderr. Only Nginx
publishes host ports. Proxy timeout is 240 seconds; this is not an AI completion
SLA. The host has `certbot.timer`, but correct renewal of the custom Certbot
directory/hook and expiry alerting are **unverified**. No renewal/dry-run executed.
TLS directory mode was observed 0755 and private Certbot config mode 0700;
private-key file mode/ownership were not re-audited. Do not assume all TLS paths
are 0700 or relax them recursively.

Flyway has exactly 23 successful version rows V1–V23, failed rows 0; additional
offline `flywayValidate flywayInfo` completed successfully at deployment finish.
Schema success does not imply standard seed success: rubrics remain 0.

#### What was deployed, and safe recovery from future failures

1. Empty-DB preflight, pinned DB/container/volume/config/image/journal checks,
   TLS/DNS/port/subnet checks and immutable source comparison preceded deployment.
2. Operations-only archive was verified and placed separately. Initial migration
   completed once; temporary SUPER was revoked. No initial backup was taken under
   the explicitly approved empty-DB exception.
3. Broker failed because `python:3.12-alpine` was absent. The approved official
   runtime was subsequently pulled and checked, then the same journal resumed.
   Flyway history/checksums were validated, not reapplied. App/runner/nginx became
   healthy; Nginx reload and both internal/external TLS smoke succeeded.
4. Python runtime: official `docker.io/library/python:3.12-alpine`, Python
   3.12.15 / Alpine 3.24 / linux-amd64, no declared volumes.
   Index RepoDigest `sha256:1b668429b3511ab407d8e00648891631b0b1a4d7e15e3ca70f38ab5b91ad4ab4`;
   amd64 manifest `sha256:2055081c860db8c842b663415b795b026e7e5cc63b59c5193fcc9997713ea5b8`.
   Nonroot/read-only/network-none constrained Python startup passed. The mutable
   tag is not a future digest-lock policy; automatic provisioning remains a gap.

For new changes, use the [update procedure](#normal-update-and-app-only-rollback)
only after the [backup protection gate](#backup-gap-and-improvement-plan) is met.
The historical empty-DB recovery instructions later in this document are **not
instructions to reset today's published schema**. Do not rerun initial setup,
overwrite this release or reuse the old pre-migration state hash as today's hash.

On a failure, stop mutations, retain journal/event/log evidence, compare pinned
DB/volume/image identity, check Flyway failures and partial DDL, and verify global
migrator grants. Do not blindly retry, clean/repair, delete locks or edit state.
When baseline/complete target is provable and the cause is repaired, approve
same-release recovery separately; post-database-stage guards skip DB lifecycle.
For application regression, app-only rollback needs a compatible previous release;
this initial deployment has **no recorded previous-release fallback**.
For data loss, follow the [isolated restore and cutover policy](#backup-and-restore),
not app rollback or restore over the live DB. No current verified production
backup means recovery from host/volume loss is not yet assured.

## Backup gap and improvement plan

The following inspection describes the **deployed operations commit de2b0d3**,
not the newly edited local scripts. The
[local implementation and console guide](#backup-implementation-and-aws-console-guide-2026-10-09)
below supersedes its proposed 35-day S3 retention with a provisional 30-day rule.
Nothing in this investigation has installed/enabled backup in production.

#### Confirmed implementation versus actual EC2 setup

| Required check | Implementation | Actual production evidence (2026-10-09) |
|---|---|---|
| Save destination | `backup.sh` requires absolute `PPE_BACKUP_DIR`; no default path | `/var/lib/ppe/backups` exists 0700, but configured destination **unconfirmed/unset in discovered configuration**. `/var/lib/ppe/operation.env` absent |
| Existing files | `ppe-<UTC timestamp>-<PID>.sql.age`, sidecar `.sha256`, transient `.tmp` | **0 matching files** in root-filesystem `find / -xdev` and `/var/lib/ppe` search. No contents read. Other mounted filesystems/external stores not exhaustively searched |
| Encryption | `age` recipient encryption, pipeline, no plaintext dump file; backup image | Code prepared, actual recipient/key custody and encryption of a production backup **unverified** |
| Automatic schedule | Repository `ppe-backup.timer`: 02:00 Asia/Tokyo, random delay ≤300s, Persistent=true | PPE service/timer LoadState=not-found, inactive. No backup/ S3 schedule reference found in inspected cron/systemd files; root crontab retrieval returned nonzero (contents **unconfirmed**). `dpkg-db-backup.timer` is OS package metadata, not PPE MySQL |
| Off-host copy | No S3 uploader in backup script/service | No discovered S3 upload config/job. AWS account bucket inventory/policies/remote backups **unconfirmed**, not evidence of account-wide absence |
| Safe file check/download | Metadata, provenance and ciphertext checksum first; controlled transfer | No production file to download. No export/decryption attempted |

The service template uses `/var/lib/ppe/operation.env` and
`/opt/ppe/current/source/scripts/production/backup.sh`; **both paths are absent**.
Actual releases and operations live under `/var/lib/ppe`, so merely enabling the
template would not establish functioning backup. Image existence is not scheduling.
Directory mode 0700 is not evidence of an encrypted filesystem; EBS encryption
could not be confirmed with the current AWS read permissions.

The [DB admin dump](../../containers/production/db-admin.sh) is logical SQL for
all `ppe` tables, views as applicable, routines, triggers and events, with
`--single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF`.
This covers learning logs/submissions/evaluations/users/credentials/survey data
in that schema, **not** MySQL system users/grants, physical volume, TLS, secrets,
release images or binaries. Secret/AES-key continuity and offline disaster
bootstrap are separately required.
InnoDB rows have a consistent snapshot; concurrent DDL is not covered by the
transaction guarantee. Enforce a shared backup/deployment lock and DDL freeze.
There is no backup lock in `backup.sh`; timer/manual/deployment jobs could overlap.

Default retention is 14 days and minimum free space 1024 MiB.
`find ... -mtime +14` removes matching ciphertext and sidecar after successful
dump/hash; age is whole-day rounded (not an exact 14×24h deadline). It is not
version-aware and has no minimum independently verified recovery-point guard.
Failure detection is nonzero exit/pipefail/Docker stderr; no remote success-age
check, alert delivery or restore gate is implemented. A completed encrypted file
is renamed **before** checksum generation, so checksum failure can leave an
artifact not ready for restoration. Nonempty/checksum alone proves neither SQL
completeness nor successful restoration.

Fresh update ordering is still DB Compose up → baseline → app/runner stop →
backup → validate/migrate → broker/app/nginx → smoke → publish. A backup failure
stops migration but does not automatically bring the quiesced app/runner back.
Resume after `backup_complete` skips another backup; it must validate provenance,
age and applicability before reuse. Post-database-stage recovery protection does
not fix normal-update DB recreation risk.

The preparation record reports a **local synthetic** isolated restore of V1–V23
and a marker on 2026-10-08; see [phase 9](production-preparation-plan.md#results).
This is not a current production dump restore test. EC2/volume-loss recovery is
**not demonstrated**: no verified off-host PPE backup is recorded. Local binlogs
(30-day expiration) do not provide off-host PITR by themselves.

#### Safe inspection/obtaining a backup (future approved operation)

Read-only metadata example, on EC2; no dump/restore or contents:

```sh
sudo find /var/lib/ppe/backups -maxdepth 1 -type f \
  \( -name 'ppe-*.sql.age' -o -name 'ppe-*.sql.age.sha256' \) \
  -printf '%f | %s bytes | mode=%m | %TY-%Tm-%TdT%TH:%TM:%TS\n'
```

Select one exact filename/version, verify its sidecar and independently protected
manifest/provenance, size and timestamp in a private directory. Do not `cat` SQL,
decrypt to terminal, paste ciphertext/base64 into chat, print private age keys,
use public ACLs/presigned URLs in logs, or transfer research backups to ordinary
developer machines. If files are moved, the current absolute-path sidecar format
needs controlled filename verification; do not trust arbitrary paths in sidecars.
Once approved off-host storage exists, an authorized restore operator may retrieve
the exact object version to encrypted, access-controlled storage using
`aws s3api get-object --bucket <approved-bucket> --key <exact-key> --version-id
<approved-version> <private-output.sql.age>` and its protected manifest.
This is **not configured or executed now**. SSM interactive access alone is not
a reviewed binary-export mechanism; avoid stdout transfer. Retrieval authorization
is separate from restore permission, and no plaintext file is needed.

#### Prioritized improvements (proposal, not approved settings)

| Priority | Work / acceptance before research use |
|---|---|
| P0 | Decide RPO/RTO/retention/key custodians and owner. Proposed starting point: RPO ≤24h, RTO ≤4h, plus a verified pre-update recovery point; these are **not approved SLA** |
| P0 | Configure absolute backup/recipient/image paths using current operations layout, least privilege, private dirs and verified encryption. Install working timer only after manually approved backup and failure test |
| P0 | Add external storage and confirm upload/checksum/encryption/object-version/provenance. Fail deployment if required local or remote backup validation fails |
| P0 | Fix fresh-update ordering: read-only DB identity/config/health guard first; no DB recreation/start/upgrade until a verified backup protects the existing DB. DB-version changes need separate snapshot/compatibility/cutover plan |
| P0 | Share exclusion lock across backup/deploy/DDL; quiesce writes and drain workers as appropriate. Abort safely on partial backup or space/upload failures; preserve recoverable service state |
| P0 | Prove offline-key decryption and isolated restore with exact Flyway counts/checksums, row IDs/content/relationships, credential-key continuity, submissions/logs/evaluations. Record elapsed time, recovery source/version and RPO/RTO |
| P0 | Synthetic old→new update tests retain rows, old rubric/proposed new rubric, evaluation snapshots, log IDs and references; injected failures leave old data intact. App rollback is not DB rollback |
| P1 | Alert on command/upload failure, missing/stale object, disk pressure and failed restore tests; operator receives/test-acknowledges alert. Daily timer success alone is insufficient |
| P1 | Formal host/volume-loss runbook: incident freeze, select trusted recovery point, replacement host/isolated volume, restore + validate, compatibility/secret checks, approved cutover, preserve original damaged evidence |
| P1 | Retention cleanup only after remote verified points, with deletion-role separation. Scheduled restore drills and optional separately reviewed off-host binlog/PITR if RPO requires it |

#### S3 candidate architecture and cost

Proposed Tokyo private bucket dedicated to PPE backups with public access blocked,
TLS-only bucket policy, versioning, SSE-KMS customer-managed key, and client-side
age ciphertext retained. Object keys include project/release/UTC backup ID;
publish manifest/commit marker only when dump, checksum and uploads are complete.
Consider a separate account/admin boundary and S3 Object Lock after checking that
WORM duration does not contradict research deletion obligations. Neither SSE-KMS
nor versioning replaces client encryption or immutable/protected provenance.

Separate IAM roles:

- EC2 writer: `s3:PutObject` on exact backup prefix; `s3:ListBucket` with prefix
  condition only if upload verification requires it; `s3:GetObject` only if
  HEAD-based verification is used. Multipart uploader additionally needs narrowly
  scoped `s3:AbortMultipartUpload`, `s3:ListMultipartUploadParts` and bucket
  `s3:ListBucketMultipartUploads` as needed.
- KMS writer: `kms:GenerateDataKey`; `kms:Decrypt` if required for multipart
  upload, restricted to the bucket/S3 service encryption context in IAM/key policy.
- Restore operator: exact-prefix `s3:GetObject`/`s3:GetObjectVersion`, conditional
  ListBucket/ListBucketVersions when necessary, KMS Decrypt, offline age identity.
  Do not inject restore credentials or the age private key into app/runner.
  Verify container denial of IMDS; an instance profile is not per-container IAM
  isolation and host/broker compromise can expose its role. Writer has no DeleteObject,
  DeleteObjectVersion, lifecycle/bucket-policy edit or KMS administrative rights.
- Retention administrator controls approved lifecycle/current **and noncurrent**
  version expiry, aborted multipart cleanup and key retention. Keep decrypt keys
  until all approved ciphertext retention ends; accidental KMS/key deletion makes
  a stored backup unusable.

Candidate retention for review: local 14 days, S3 daily 35 days, pre-update points
through acceptance plus 35 days; long-term monthly archives only if separately
approved. Reconcile with the [March 2027 research retention goal](production-operations-decisions.md)
and latest consent/deletion policy; do not silently preserve all data indefinitely.
No automatic archival/deletion settings have been applied.

Cost is storage (ciphertext GiB × versions × duration), PUT/GET/LIST/multipart
requests, KMS key and request charges, retrieval/transfer and monitoring.
Illustration only: a 1-GiB daily full dump × 35 days is roughly 35 GiB before
extra pre-update points/noncurrent versions; not an actual measured dump size.
Begin with S3 Standard for quick recovery, evaluate infrequent-access/Glacier
minimum-age/size/retrieval latency and charges against RTO before transition.
Current Tokyo prices, traffic and budget are **unconfirmed**; estimate with AWS
Pricing Calculator. Same-host backup and EBS DeleteOnTermination=true make this
external recovery point a pre-research blocker, not an optional cost optimization.

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

This standalone example is for approved empty initial/isolated setup, **not
normal updates of the published DB**. New wrappers require the shared absolute
private `PPE_STATE_DIR` for exclusion; use the guarded deployment workflow and
verified backup for updates. Do not use this raw DB `up` command on existing
production containers.

```sh
export PPE_PROJECT=ppe-production
export PPE_STATE_DIR=/var/lib/ppe/state
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
sudo install -d -m 0700 /var/lib/ppe/tls-private/current \
  /var/lib/ppe/certbot/config /var/lib/ppe/certbot/work /var/lib/ppe/certbot/log
sudo install -d -m 0755 /var/lib/ppe/acme
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

Bootstrap prepares only the public ACME webroot with mode 0755 so Nginx UID/GID
101:101 can traverse it through the read-only bind mount. TLS and Certbot
directories are still prepared with mode 0700 and `umask 077`; installed TLS
material continues to follow the existing installation policy below. Challenge
files must be readable (normally 0644), and the `.well-known/acme-challenge`
directories must be traversable (normally 0755). Keep the ACME webroot separate
from private material; do not recursively relax permissions on TLS or Certbot
directories. Host ancestors above the bind source need not be made public:
the container accesses the mounted webroot, not the host path above it.

The Nginx image owns `ENTRYPOINT ["nginx"]`; Compose `command` must contain
arguments only. Normal Nginx inherits `["-g", "daemon off;"]`, while the bootstrap
uses `["-c", "/etc/nginx/acme-bootstrap.conf", "-g", "daemon off;"]`.
Do not remove `nginx` from `compose exec ... nginx -t` or reload commands:
`exec` does not prepend the image entrypoint.
The bootstrap configuration places Nginx temporary paths under the existing
`/tmp` tmpfs, not `/var/cache/nginx`, so it can start with a read-only root
filesystem and the image's nonroot user.

If bootstrap startup or health waiting fails, the script attempts to stop only
the `acme-bootstrap` service, preserves the failing exit status, and reports a
cleanup failure. It does not stop normal Nginx or delete TLS files. Failure to
inspect running Nginx aborts before any service mutation.

Local regression commands:

```sh
bash scripts/production/tests/tls-workflow-test.sh
bash scripts/production/tests/nginx-startup-test.sh
```

The first test uses mocked Docker/Certbot and synthetic certificates for failure
cleanup, existing-deployment guards, bootstrap and renewal. The second requires
local Docker, Compose (with JSON config support), jq, OpenSSL and curl. It builds
a uniquely tagged test image and derives an isolated Compose project from the
production Nginx service definitions, using loopback dynamic ports, a synthetic
upstream and dummy TLS material. It uses the bootstrap script to prepare webroot
permissions and checks UID/GID 101:101 traversal, challenge file reads, read-only
mount behavior and preservation of private directory modes. It checks actual
container commands, bootstrap
health/challenge delivery and normal HTTPS startup/reload. It does not invoke
real Certbot, mount production secrets, or replace release images. A deliberately
broken test-only command reproduces the duplicate-`nginx` startup failure and
checks the bootstrap script's cleanup with a fail-closed Certbot stub. Only its test
containers, network, image and temporary files are removed.

Install renewal with the existing host Certbot timer, the PPE service drop-in,
and a dedicated mode-0600 paths-only environment. Follow the
[TLS renewal installation and approval runbook](./tls-renewal-operation.md);
do not edit the immutable application release or reuse the backup environment.
The wrapper explicitly selects the custom config/work/log directories and
`ppeval` lineage. Its non-renewing preflight is:

```sh
sudo /bin/bash /var/lib/ppe/tls-operations/current/scripts/production/renew-tls.sh \
  /var/lib/ppe/tls-operation.env --check
systemctl list-timers --all | grep -i certbot
```

The hook checks the lineage and both domains, retains a private previous pair,
serializes hook deployments, and tests/reloads Nginx. Ordinary installation
(including partial publication), validation or reload failures restore the
previous pair and attempt a tested reload. Recovery failures remain failures,
are reported explicitly, and retain recovery material. Each file rename is
atomic, but the pair is not a single atomic transaction; SIGKILL/power loss
requires manual inspection and may leave the deployment lock. Do not run the
installer independently/concurrently with the renewal hook. Review Certbot logs,
backup directory permissions and certificate expiry after timer runs.

2026-10-09 Phase 2 local acceptance: the synthetic TLS workflow passed 34 checks
on both macOS and isolated Ubuntu; Ubuntu systemd verified the service/drop-in,
and isolated real Nginx passed 4 checks, including replacement-certificate trust
on both portal hosts without changing the Nginx container ID. Production wiring,
ACME staging dry-run, production hook/reload and reboot acceptance remain
unexecuted and require separate approval. No production or backup settings were
changed for this work.

2026-10-09 23:59 JST Phase 3 placement: the approved fixed main merge
`3c416a2e0a36c0e800f26fc5f472834387cf1c3b` was installed separately from the app
release; five hashes, dedicated paths-only configuration, wrapper `--check`,
systemd verification and daemon-reload passed. The new ExecStart is loaded.
The original enabled/active Certbot timer was recorded, stopped and temporarily
disabled to prevent unapproved renewal (including after reboot). It remains
disabled/inactive pending separately approved dry-run/hook testing and subsequent
restoration. Certificate/key, five runtime containers, Volume, deployment state
and backup settings are unchanged; both HTTPS login pages return trusted 200.
See the [placement record and timer restoration plan](./tls-renewal-operation.md).

2026-10-10 00:04:21–00:04:30 JST final acceptance: one explicitly approved
staging `--dry-run-deploy` passed (exit0), including the real deploy hook,
Nginx configuration validation and reload. Both HTTPS login pages returned
trusted 200; served fingerprints and key/certificate match were verified.
The active certificate expiry is unchanged (2027-01-06 16:22:08 JST), and five
container identities/health plus protected DB/backup/deployment state are unchanged.
Timer resumption remains HOLD: the persistent stamp predates the 12:00 UTC
calendar event, so restart may catch up with random delay and cannot guarantee
no immediate renewal invocation. Per the user's stop condition, the timer is
still disabled/inactive; no stamp/schedule changes or further renewal were made.
Automatic operation is not complete until an approved safe resumption.

2026-10-10 00:08 JST resumption acceptance supersedes the preceding HOLD:
the owner explicitly approved persistent catch-up; enable/start restored
`certbot.timer` to enabled/active(waiting). Its automatic run at 00:08:17–00:08:18
JST returned Result=success/exit0, correctly found the PPE certificate not yet
due, and logged no errors (service warning/error entries0). Next observed
execution is 2026-10-10 17:39:34 JST. Both public HTTPS portals, key/certificate
match, five healthy unchanged containers and protected DB/backup/deployment
state passed. No additional manual renew or dry-run was performed.
TLS automatic renewal configuration is accepted as complete; future live
expiry-triggered renewal and actual host reboot remain operational checks.

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

When the journal has reached `database_complete` or later, repaired deployment
tooling does **not** call Compose `up`, `start` or `restart` for DB on resume.
Before running it, pin `PPE_RECOVERY_DB_CONTAINER_ID` (12–64 hex characters) and
`PPE_RECOVERY_DB_VOLUME_CREATED_AT` from independently reviewed pre-recovery
evidence. Do not simply accept newly discovered identities after a discrepancy.
For the interrupted initial deployment investigated on 2026-10-09 JST:

```sh
export PPE_RECOVERY_DB_CONTAINER_ID=cead40118ed8
export PPE_RECOVERY_DB_VOLUME_CREATED_AT=2026-10-08T08:32:49Z
```

The read-only recovery preflight requires exactly this existing healthy DB,
the target release's image ID, matching Compose DB configuration hash, normal
project/service labels, and a writable named `${PPE_PROJECT}_database` mount at
`/var/lib/mysql`. Volume name, local driver, Compose labels and creation timestamp
must match. Missing/stopped/unhealthy DB, configuration drift, replaced container
or volume, or missing identity pins abort before journal/event changes or SQL.
Do not repair the discrepancy by running Compose `up` or deleting the journal.
Fresh operations and recovery before `database_complete` retain the normal DB
startup path; this no-lifecycle guarantee is specifically for post-database-stage
recovery. Later service stages use `--no-deps`, so they do not start/recreate DB.
Concurrent external Docker administrators or host failures are outside this
script's lock; freeze other maintenance and compare DB identity after completion.

#### Recovering a shell-invocation failure with repaired operational tooling

Host scripts with `#!/usr/bin/env bash` must be invoked with `bash`, not `sh`.
Ubuntu 24.04 uses dash for `/bin/sh`; the previous deploy/migration wrappers
invoked the Bash-only privilege helper through `sh`, which can exit with status
2 at `set -euo pipefail`, before Flyway starts. The POSIX `migrate.sh` wrapper
remains a `sh` script, but explicitly uses Bash for privilege checks/revocation.

Deployment uses migration, privilege, backup and smoke helpers beside the
invoked operational `deploy-release.sh`. The target release still supplies the
verified Compose file, commit-pinned images (including migration tools) and SQL.
This separation permits a reviewed tooling-only repair without editing an
immutable release, retagging images or changing the recorded target. Do not mix
helper versions or use this mechanism to substitute migration SQL/images.

For the interrupted initial release
`0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`, the new fix commit must **not** be
passed as the deployment target while the old journal is incomplete. Use the
following procedure only after operator review/approval; these are manual
production steps, not actions performed by the regression tests:

1. Commit/review the tooling fix through the normal process. On a trusted
   checkout containing that commit, export only its production scripts to a
   **new**, private operational directory, separate from release directories.
   Keep the old release's `source`, `READY`, image tags and image-ID records
   untouched. Example (replace `FIX_COMMIT` with the reviewed full SHA):

   ```sh
   # Run this export in Bash; stop if either archive or extraction fails.
   set -euo pipefail
   FIX_COMMIT=replace-with-reviewed-40-character-fix-commit
   OPS_DIR=/var/lib/ppe/operations/"$FIX_COMMIT"
   test ! -e "$OPS_DIR" || exit 1
   sudo install -d -m 0700 "$OPS_DIR"
   git archive "$FIX_COMMIT" scripts/production |
     sudo tar -x -C "$OPS_DIR"
   ```

2. Preserve the existing `PPE_STATE_DIR` and environment paths, Compose project,
   secret snapshot, TLS/ACME settings, subnet/IP and ports. Verify no concurrent
   deployment/migration process or active lock exists. If a stale lock exists,
   stop for operator investigation rather than automatically deleting it.
   Save a private copy/checksum of the journal and event log for comparison;
   never edit, source or delete `deployment.state`.

3. Confirm the journal has `format=2`, `mode=initial`, the **old** release ID,
   empty `previous`, `policy=initial`, `phase=migration_started`,
   `migration_baseline=0` and `target_migrations=23`. There should be no
   conflicting `current-release`. Confirm the old release has matching
   `commit`/`READY`, seven image IDs matching their recorded files, and exactly
   23 versioned SQL files. Review the effective Compose config and existing DB
   container/volume identity; do not change project or DB configuration.

4. Perform read-only DB checks immediately before resuming:

   ```sh
   RELEASE_DIR=/var/lib/ppe/releases/0e2bfae5e23bc512f0b7cf844264d4d45a484ecc
   COMPOSE_FILE="$RELEASE_DIR/source/compose.production.yml"
   docker compose -p "$PPE_PROJECT" -f "$COMPOSE_FILE" ps db
   docker compose -p "$PPE_PROJECT" -f "$COMPOSE_FILE" \
     exec -T db sh /opt/ppe/db-admin.sh --skip-column-names <<'SQL'
   SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe';
   SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema='ppe' AND table_name='flyway_schema_history';
   SHOW GRANTS FOR 'ppe_migrate'@'%';
   SQL
   ```

   Require DB healthy, both counts zero, schema-scoped migration privileges and
   no global SUPER/ALL PRIVILEGES. Any deviation requires investigation; do not
   reset the DB, repair Flyway or adjust the journal to force a retry.

   Normally take the [encrypted backup](#backup-and-restore) before migration:
   the initial deployment does not run the update backup stage. Use a new private
   backup subdirectory so backup retention cannot delete older recovery evidence.
   Verify its SHA-256, preserve trusted provenance off-host, and prove
   decryption/isolated restore with the offline identity. The dump covers `ppe`,
   not MySQL accounts or the physical volume. Preserve the existing secret
   snapshot and document DB account grants; if a complete host/volume rollback
   is required, obtain a separately approved, encrypted storage snapshot with a
   reviewed MySQL consistency procedure. A live EBS snapshot alone is not a
   tested logical restore.

   On 2026-10-09 JST the operator explicitly waived backup and isolated-restore
   completion as prerequisites **only for this interrupted initial release**,
   provided read-only checks immediately before resuming still show no `ppe`
   tables or research data. Initial data loss is acceptable for this operation;
   database deletion/reset, volume replacement and journal edits are not
   authorized. Prefer retaining every existing asset. Record that skipping backup
   leaves no verified pre-migration recovery point if partial DDL or host failure
   occurs. Stop and reassess if any application data appears. This exception does
   not change normal update backup requirements. Before collecting research data,
   implement and verify backup, restoration and existing-data retention tests.

5. After explicit production/maintenance approval, use the **new operational
   script** with the **old target directory**:

   ```sh
   export PPE_OPERATION_APPROVAL=production-approved
   export PPE_MAINTENANCE_APPROVED=yes
   export PPE_RECOVERY_DB_CONTAINER_ID=cead40118ed8
   export PPE_RECOVERY_DB_VOLUME_CREATED_AT=2026-10-08T08:32:49Z
   bash "$OPS_DIR/scripts/production/deploy-release.sh" initial "$RELEASE_DIR"
   ```

   The existing journal is used, completed stages are skipped, image identities
   are verified and migration runs only from the recorded baseline. The script
   advances the journal normally; retaining it does not mean freezing its phase.
   It checks Flyway checksums and the exact target count before publishing the
   old release ID. The fix commit is the tooling version, not the target
   application version. Use a reviewed commit that includes both the shell fix
   and the DB-preserving recovery preflight; the earlier shell-only fix is not
   sufficient for this no-DB-lifecycle recovery.

6. After success, require `phase=published`, `current-release` equal to the old
   SHA, 23 successful versioned migrations, no failed Flyway records, successful
   Flyway validation, no residual SUPER, healthy services and both portal smoke
   checks. Compare the DB volume identity and immutable release files/image IDs
   with the preflight evidence. Preserve the event log. If interrupted again,
   inspect schema/history and resume only the same target under the existing
   fail-closed rules.

   This is an initial deployment with no previous published application; the
   app-only `rollback-release.sh` is not applicable. Before migration, abort
   without changing DB/state if any prerequisite fails. After migration starts,
   keep the current volume/journal and inspect committed DDL, Flyway history and
   residual privileges. Prefer a reviewed forward recovery; do not auto-retry a
   partial migration. A database rollback requires isolated restoration, integrity
   checks and a separately approved cutover, never an import into the active DB
   or journal reset. Application containment (stopping newly started services)
   and residual SUPER revocation also require explicit operator approval.

Preparing the repaired tooling does not change the DB or Flyway history.
Actually completing an initial deployment necessarily creates schema objects
and Flyway history through the reviewed migrations; recovery cannot complete
while leaving an empty database unchanged. No manual history edits are needed.
Once the old target is published, a new application release may be prepared and
deployed through the normal `update` workflow after migration compatibility
review. Do not switch to it during this interrupted operation.

Local regressions (mock Docker/SQL only; dash is required for shell tests):

```sh
bash scripts/production/tests/shell-invocation-test.sh
bash scripts/production/tests/migration-privilege-cleanup-test.sh
bash scripts/production/tests/deployment-recovery-test.sh
bash scripts/production/tests/deployment-state-test.sh
```

The shell test syntax-checks every production/test script with its declared
interpreter and exercises the real migration wrapper through dash with mocked
Docker. Recovery tests deliberately make old release migration helpers unusable
to prove the new tooling is used, check old release bytes remain unchanged,
reject a different target without changing the journal, prevent duplicate
migration execution, retain partial failed history, and enforce approval checks.
They also assert zero DB lifecycle calls on resume and fail-closed DB identity,
health, configuration and mount checks, retaining journal/event bytes on refusal.

### Normal update and app-only rollback

Build a new immutable release from a committed SHA, review all migrations as
expand/contract or otherwise backward compatible, then outside class hours:

```sh
export PPE_SCHEMA_POLICY=backward-compatible
# Future release's Compose must support the explicit DB pin. Do not edit old releases.
export PPE_DB_RELEASE=<full-sha-owning-the-current-db-image>
bash scripts/production/deploy-release.sh update \
  /var/lib/ppe/releases/<new-full-commit-sha>
```

The deployed de2b0d3 script performs DB Compose `up` before backup and must not
be used for normal updates containing research data. The locally improved script
instead pins/checks the existing healthy DB, image/config/volume without starting
or recreating it; records the baseline, quiesces app/runner, then requires a
versioned, checksum-verified remote backup before validation/migration.
An existing `backup_complete` journal is insufficient without a matching fresh
receipt. The new Compose supports an explicit `PPE_DB_RELEASE` image pin so a new
application SHA does not imply a DB image change (the DB build's revision label
changes its image ID even if MySQL code is identical). The pinned immutable
release manifest/image and DB Dockerfile/init/admin bytes must match, and effective
DB Compose config must still match the live container. A DB image/config upgrade is deliberately refused and requires a separate
backup/compatibility/cutover plan. Read-only guards can precede backup, but no
DB lifecycle or migration write may. No production deployment of this fix occurred.
The guard derives `PPE_DB_SOURCE_DIR` from the pinned immutable release to keep
inactive build-context metadata stable across app releases; it does not accept an
arbitrary build path or edit existing release Compose files.
See the [implementation guide](#backup-implementation-and-aws-console-guide-2026-10-09).
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
private key separately from EC2. The new cloud mode fetches only the public
recipient from Parameter Store in namespace mode or the approved local public
file in direct-parameter mode. Keep encrypted SQL/checksum/manifest in a 0700 directory, preferably on
a separately protected encrypted filesystem. `PPE_BACKUP_MIN_FREE_MIB` defaults to
1024. The new script does not delete local copies or S3 objects; S3 Lifecycle handles
the provisional 30 days and reviewed local cleanup remains an operator task.

```sh
bash scripts/production/backup.sh
```

The new implementation fails on dump/encryption/upload/verification/metric errors,
notifies SNS when its configuration is available, and needs an independent
CloudWatch missing-heartbeat alarm for host/job/configuration failures.
Completion requires ciphertext, checksum and completion manifest uploads with
exact object versions, SHA-256/size/SSE-KMS verification. No automatic restore or
retry occurs. AWS role, bucket, alarms and systemd must be configured and tested
after approval; script existence does not make production backup operational.

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

## Backup implementation and AWS console guide (2026-10-09)

### Prepared-resource follow-up (Phase 1–3, 2026-10-09 09:48 JST request)

**This follow-up is local implementation and read-only investigation only. Stop
after the Phase 3 report. Installation, schedules, backup creation, S3 writes,
AWS policy changes and restoration tests require new explicit approval.**
Existing staged local changes from the earlier engagement were preserved; no
commit or production release edit was made.

Phase 1 used `ppe-deployer` locally and an interactive SSM session on
`i-0ffd69e8f390bd396` with the instance role, without retrieving credentials,
printing SQL/backup content, or running the backup. Results:

| Check | Observed / limitation |
|---|---|
| EC2 / profile | running; profile ARN names `ProgrammingProcessEvaluatorEC2Role`. Role policy details unconfirmed |
| DB container / volume | Same full ID `cead40118ed8a27ce90a8f745f7b69ed528355149c7412d2c95985b6e47872d8`, healthy, unchanged StartedAt/image ID; RW `ppe-production_database`, original creation timestamp |
| App / runner / broker / nginx | All five services healthy; no lifecycle command executed |
| Journal | published, application release unchanged; state/events hashes match the earlier successful deployment evidence |
| Local backups | `/var/lib/ppe/backups` root-owned 0700; no `.sql.age`/sidecar files found under the inspected `/var/lib/ppe` scope. No recipient file found in the filename search; external/offline key custody unconfirmed |
| Scheduling | PPE backup service/timer and freshness timer not-found/inactive, no next run; `/var/lib/ppe/operation.env` and `operations/backup-current` absent |
| Tools | EC2 AWS CLI 2.37.10, bash, flock, OpenSSL, Docker available; age uses the existing pinned backup image, not a newly installed host key/tool |
| SSM bucket parameter | `/programming-process-evaluator/prod/backup-s3-bucket`, String; instance-role read succeeded and privately compared equal to supplied bucket |
| SSM KMS parameter | `/programming-process-evaluator/prod/backup-kms-key-arn`, String; instance-role read succeeded and privately compared equal to supplied key ARN |
| S3 | Bucket metadata/versioning/encryption/lifecycle/public-policy queries denied/unavailable under instance role; local SSO queries explicitly AccessDenied/403. **Existence and actual settings are not independently confirmed** |
| IAM | GetRole/ListRolePolicies/ListAttachedRolePolicies denied locally; role-policy inspection unavailable from EC2. Claimed `PPEProductionS3BackupPolicy` contents/effectiveness unconfirmed |

Approved resource identifiers (user supplied; not independently certified):

- Bucket: `ppe-production-mysql-backups-024378233912-ap-northeast-1-an`
- KMS: `arn:aws:kms:ap-northeast-1:024378233912:key/af2c55b8-109d-4c81-8a94-05def6e56047`
- Proposed S3 location:
  `s3://ppe-production-mysql-backups-024378233912-ap-northeast-1-an/production/mysql/`

The owner approved local adaptation after Phase 1: **reuse these two direct
parameter names**, put S3 prefix, public age-recipient file path and approved SNS
ARN in the private local EnvironmentFile. No extra parameters, keys, notification
destinations or changes to prepared resources are assumed. The older five-parameter
namespace mode remains supported, but cannot be mixed with the direct-name mode.
Missing/invalid recipient or SNS ARN fails before a dump; no encryption or
notification bypass was added.

The updated [EnvironmentFile example](../../containers/production/backup-operation.env.example)
uses `PPE_BACKUP_BUCKET_PARAMETER` and `PPE_BACKUP_KMS_PARAMETER`, not
`PPE_BACKUP_PARAMETER_PREFIX`. Fill the public recipient file and SNS ARN only
after approval; leave private age identity offline. Do not recreate or overwrite
the existing two parameters. Direct mode overrides stale bucket/key environment
values with SSM results, and refuses retrieval failure or mixed/partial names.

#### Local public-recipient setup (2026-10-09)

The owner supplied the production public age recipient. It is stored locally in
`deploy/runtime/backup-age-recipient`, with the existing
`PPE_BACKUP_RECIPIENT_FILE` setting in `deploy/runtime/backup-recipient.env`.
Both files are mode 0600 and excluded by the existing `/deploy/runtime/` rule in
[.gitignore](../../.gitignore). The environment fragment configures only the
recipient path; it is not a complete or approved production configuration.
No new Parameter Store item or alternate encryption scheme is needed.

The backup script now tests empty-input encryption with its configured age image
before any database access. This catches invalid Bech32 checksums that the
existing string-format check alone could not catch. Missing, empty or malformed
files are rejected; no dump or S3 upload is started on recipient failure.
The preflight creates only a disposable, network-disabled age container, not a
DB/application container, and removes its temporary public-recipient file.

Local validation passed with dummy data only:

- [Recipient test](../../scripts/production/tests/age-recipient-test.sh):
  direct-name configuration with mocked SSM, local identity/public-key match,
  real age encryption/decryption, and missing/empty/malformed/checksum rejection.
  Invocation: `bash scripts/production/tests/age-recipient-test.sh
  /absolute/public-recipient-file /absolute/local-identity-file`.
  The existing identity must be outside the repository, owned by the current
  user and mode 0400/0600. It is read, never copied or printed.
- Local age 1.3.2 encryption/decryption passed; the existing backup image's
  age 1.2.1 accepted the public recipient for the actual encryption preflight.
  Encryption by that image followed by decryption with local age 1.3.2 also
  produced byte-for-byte identical dummy data using the matching local identity.
- Network-disabled Linux backup mocks passed, including rejection before any
  DB access and cleanup of temporary recipient files. Shell-invocation and
  migration-wrapper and deployment-recovery regression tests passed.

The private identity remains solely in the owner's existing local key-custody
location; it is not a backup service setting. Production application is still
approval-gated: install the **public** file at the already planned
`/var/lib/ppe/config/backup-age-recipient` and retain that path in the production
EnvironmentFile only after explicit approval. Nothing was installed on EC2;
AWS, MySQL, volumes, Flyway, production containers and systemd were unchanged.
Matching the supplied key locally does not replace an isolated full-schema
backup restoration test or establish an offline duplicate-key custody policy.

Before production approval, the owner must confirm in the S3 console:
Versioning **Enabled**, SSE-KMS exact key, Bucket Key policy compatibility, public
access blocked, lifecycle **Enabled** with matching prefix/all-backup coverage,
current expiration 30 days and noncurrent-version handling. A default encryption
setting alone does not prove that an explicitly uploaded object is verifiable or
that the role has needed KMS rights. If a bucket uses Bucket Keys, the policy
context differs from the object-ARN example below: review rather than disabling
an existing setting without approval. A Versioning-disabled bucket is refused by
the script, not silently treated as a backup success.

Compare `PPEProductionS3BackupPolicy` privately against the minimum requirements:
two exact `ssm:GetParameter` ARNs; prefix `s3:PutObject`/`s3:GetObjectVersion`;
KMS GenerateDataKey and Decrypt via S3 (checksum verification needs KMS permissions);
approved SNS Publish and namespace-limited PutMetricData. No DeleteObject or
resource-admin permissions. The generic writer template includes both supported
SSM modes; **remove the five unused namespace ARNs when installing direct mode**
to retain least privilege. Policy attachment and actual permissions cannot be
certified without console evidence/read permissions; do not change IAM to work
around denied metadata reads automatically.

Phase 2 modified only the configuration loader, EnvironmentFile/policy example,
mock regressions and this runbook. Existing dump/age/S3/version/checksum/lock/
receipt/restore guards were reused. New tests cover successful two-name retrieval,
stale local values replaced by SSM, mixed/missing parameter-name refusal,
missing public recipient, missing notification ARN and retrieval failures.
No live AWS write or newly executed real restoration test is part of this follow-up.
The follow-up's network-disabled backup mocks, systemd syntax, actual Linux flock,
deployment recovery and existing secret-fetch regressions all passed. Shell/JSON
syntax, editor diagnostics, local links and staged/unstaged whitespace checks
passed. These do not verify actual S3 writes, KMS authorization, SNS delivery or
full production-schema restoration; those remain approval-gated.

Phase 3 installation/rollback proposal:

1. Reconfirm DB identity/volume/journal/assets and owner-verified AWS settings.
   Confirm offline age key custody, public recipient, SNS subscription/alarms.
2. After explicit approval, publish a reviewed new operations-only commit/archive
   outside immutable app/old operations releases, then prepare root-owned private
   config/public recipient and the unit symlink. Preserve existing files first;
   unexpected pre-existing config stops installation.
3. Obtain separate approval for a **manual read-only DB dump plus local/S3 writes**
   and notification/metric acceptance. No DB SQL writes, container restart or
   Flyway action are needed to install/run backup.
4. After verified remote recovery point and separately approved isolated restore,
   enable daily 02:00 Tokyo and hourly freshness timers. Persistent timer activation
   may immediately run a missed job and needs explicit approval.
5. Rollback means disable/stop **backup/freshness timers only**, let an active dump
   finish unless a reviewed incident requires termination, and restore previous
   operations symlink/backup config/unit files after approval. Never stop application
   or DB services, edit deployment.state, revert SQL, remove backups, delete objects
   or rotate/delete keys. If there was no previous installed backup, leave timers
   disabled and retain new ciphertext/config evidence. An S3 upload may have
   completed even if a later stage failed; inspect exact versions before any retry.

Full execution examples, IAM review, failure behavior, lifecycle semantics,
isolated restoration plan and console steps follow below. Those commands are
proposals only, not automatically authorized by this report.

**Local implementation only. No AWS writes, production dump/restore/deployment,
unit installation or secret changes were performed.** AWS metadata queries confirmed
the running EC2 and its instance profile `ProgrammingProcessEvaluatorEC2Role`.
The current SSO profile denied DescribeParameters, ListBuckets, ListAliases,
ListTopics, DescribeAlarms and GetInstanceProfile. Existing bucket/key/topic/alarm
inventory and effective instance-role policy are **unconfirmed**. Do not infer their
absence; the owner must inspect the console first. Reuse only a compliant private
backup prefix/key/topic; never repurpose a bucket that permits app/public access.

### Implementation contract

- [backup.sh](../../scripts/production/backup.sh) streams InnoDB single-transaction
  `ppe` dump directly to the existing age container, never a plaintext SQL file.
  It refuses non-InnoDB base tables and verifies DB container/project/healthy
  volume identity before and after the dump; it never starts a missing DB.
  It supports cloud mode by default; non-cloud mode is restricted to explicitly
  approved `ppe-sim-*`/`ppe-preparation-*` local tests on a Unix Docker endpoint.
- [backup-common.sh](../../scripts/production/backup-common.sh) fetches either the
  two prepared direct parameters or the older five-parameter namespace individually,
  validates values without sourcing/executing config,
  refuses access-key/profile environment credentials for the writer, verifies
  versioned S3 object checksums and issues bounded, generic notifications.
  Database credential retrieval remains unchanged; existing snapshots are reused.
- Linux [database-lock.sh](../../scripts/production/database-lock.sh) uses
  nonblocking `flock` on private `PPE_STATE_DIR/database-operation.lock`.
  Deploy/rollback/backup/manual migrate/privilege-cleanup share it. Descriptor 9
  is inherited only when its inode matches; children do not unlock the parent.
  Timer contention returns failure, not silent skip. No unconditional retry.
  Existing journal/deployment/migration stale-lock checks remain relevant.
  All operators must use the same state directory; arbitrary manual SQL/old
  immutable scripts do not acquire this new lock and must not run concurrently.
- Data, checksum and completion manifest are uploaded in that order with
  `PutObject --if-none-match '*'`, a unique key and explicit SHA256/SSE-KMS key ARN.
  Each exact version is checked with HeadObject/checksum-mode ENABLED.
  A partial upload leaves local ciphertext and any uploaded orphan objects, but
  no successful receipt; no DeleteObject/cleanup/retry is attempted.
  Single PUT is deliberately limited to **4 GiB per object**. Larger dumps fail
  explicitly and require a separately reviewed multipart implementation.
- Atomic private receipts record release/baseline/UTC time and remote object
  versions/sizes/hashes, source DB ID and public age recipient for key-custodian
  lookup. Old identities must remain recoverable for their retained backups.
  [verify-backup-receipt.sh](../../scripts/production/verify-backup-receipt.sh)
  checks configuration/provenance/freshness (default 26h) and all three remote
  objects, including on deployment resume. It does not claim SQL recoverability.
  A successful CloudWatch metric precedes receipt publication; any publication
  error returns failure. Repeated backup runs use new IDs, not overwrite.
- New systemd templates use `/var/lib/ppe/operations/backup-current/scripts/production`,
  not the nonexistent `/opt/ppe/current/source`. `backup-current` is a separately
  approved operations-only symlink to verified new tooling, **not the app release**.
  Units run as root because host Docker socket access is root-equivalent. They
  use instance-role credentials, no credential/config files, private umask,
  systemd journal output and timeout; installing them is an explicit ongoing-job
  approval, not automatic approval of deployment commands.
- Daily backup keeps the existing 02:00 Asia/Tokyo timer, ≤5m random delay,
  Persistent=true. Hourly [freshness check](../../scripts/production/check-backup-freshness.sh)
  verifies receipt age and remote existence/integrity and publishes FreshBackup.
  Daily failures attempt SNS; host death, missing unit, retrieval failure and SNS
  failure require **independent CloudWatch alarms**, not only the local checker.
- Daily configuration must match `current-release`; mismatch fails visibly rather
  than mislabelling a backup. After future application updates, refresh the
  reviewed operation.env application pointers/backup image, retaining the explicit
  `PPE_DB_RELEASE` of the existing DB, and verify timer configuration.
  DB quiesce failure or backup failure in update does not automatically restart
  app/runner: inspect journal, preserve DB, obtain a reviewed resume/recovery decision.

SNS is used for immediate detailed failure category; CloudWatch metrics/alarms are
used for missing/stale success. CloudWatch Logs alone cannot detect a stopped host
that emits no log. The existing Agent memory/disk settings are not silently modified.
The systemd journal must have bounded retention; verify host journald persistence
before relying on local logs. SNS delivery/subscription and alarm actions need
actual post-approval tests. A monitoring outage is an error, not success.

### Parameter Store registration list

**Prepared-resource direct mode above takes precedence for the current plan:**
the existing `backup-s3-bucket` and `backup-kms-key-arn` are reused without writes.
The following five-parameter list is the supported alternative namespace design,
not a request to recreate parameters. Use one mode only. With direct mode,
`production/mysql`, the public recipient file and SNS ARN are local configuration.

Console region: **Asia Pacific (Tokyo) / ap-northeast-1**. Open Systems Manager →
Application Tools → Parameter Store (navigation labels may vary). Search each
exact name before creating; **do not overwrite an existing parameter**. If present,
verify owner/intended use privately and stop for review on mismatch.
Choose Standard tier, String, plain text input. These five values are configuration
or a **public** encryption recipient, not passwords; SecureString is unnecessary.

| Exact name | Value entered by owner | Where to obtain it |
|---|---|---|
| `/programming-process-evaluator/prod/backup/s3-bucket` | Exact approved bucket name, not ARN/URL | S3 bucket overview |
| `/programming-process-evaluator/prod/backup/s3-prefix` | `production/mysql` (no leading/trailing slash) | Dedicated approved backup prefix; align policies/lifecycle |
| `/programming-process-evaluator/prod/backup/age-recipient` | One `age1…` public recipient | Trusted offline key custodian, `age-keygen -y` on private identity; never enter private identity |
| `/programming-process-evaluator/prod/backup/kms-key-arn` | Full Tokyo customer-managed KMS **key ARN**, not alias | KMS key details |
| `/programming-process-evaluator/prod/backup/sns-topic-arn` | Tokyo Standard SNS topic ARN | SNS topic details |

Region, 02:00 schedule, local absolute directories, immutable release/image pointers,
26h freshness window and metric namespace are local/systemd settings in
[backup-operation.env.example](../../containers/production/backup-operation.env.example);
do not add unnecessary SSM parameters. This file is a review example, not a file
to source or blindly install. Future updates require pointer review.

**Age private identity must never be stored under these parameters, on the normal
EC2 instance, in its backups, in app secrets or in Git.** Keep it in an independently
controlled encrypted offline vault with a separate recovery copy and responsible
custodian. Store no access keys. An EC2 role able to decrypt S3 KMS ciphertext
still cannot decrypt age without this separate identity. A customer KMS key protects
S3 at rest, but is not the age private key.

### Production preparation after owner AWS confirmation (2026-10-09 12:38 JST)

#### Development closeout and operations handoff (2026-10-09 22:37 JST)

Backup development is closed for this work unit by owner instruction. Stages A–E
configuration, the manual encrypted backup, isolated recovery and scheduled
freshness checks are complete. No additional backup implementation is planned
as a prerequisite for the next development task. The records below are dated
evidence; earlier preparation blockers do not override the latest checkpoint.

The following are **post-start operational checks**, not reasons to continue
backup feature development:

| Operational check | Latest evidence / next action |
|---|---|
| First daily automatic backup | Not yet due at the 22:34 checkpoint. Observed schedule: 2026-10-10 02:01:34 JST. Check after completion, initially around 02:10 JST: systemd start/end/exit0, new receipt timestamp, all three exact-version S3 objects, sizes/SHA-256/SSE-KMS/key and local hashes, plus app/MySQL health. |
| CloudWatch alarm recovery to OK | Owner confirmed ActionsEnabled=true / OK during the final checkpoint. Retain this as an owner-console observation, not an API read. Operational follow-up should inspect State reason/history and confirm continued OK with genuine hourly FreshBackup=1, including after the first scheduled backup. |
| Alarm notification path | Direct SNS test delivery was confirmed. Alarm-triggered SNS delivery is not yet verified; confirm on a genuine future transition or a separately approved safe monitoring test. Do not manufacture a backup failure or change alarm thresholds. |

Keep both timers/configuration unchanged. API read permission limitations remain;
use the owner's existing console rather than modifying IAM. If a backup fails,
inspect receipt/local artifacts and known S3 versions before any separately
approved retry; do not delete, restore or unconditionally rerun. Production DB,
Volumes, immutable releases and the local-only age private identity remain
protected. Populated-data/application recovery and scale drills remain future
operational validation, not completed by the initial empty-business-data drill.

Closeout changes are limited to the backup environment example, two regression
test files and this deployment/error documentation. Runtime config/private keys
are not commit inputs. No production actions or additional implementation are
part of this closeout. Subsequent feature work follows the implementation
roadmap independently; it must not modify the fixed backup operations implicitly.

Closeout local regression results: `bash scripts/production/tests/backup-operation-config-test.sh`
passed all five parse-only checks, exit0. The isolated mock command
`docker run --rm --network none --read-only --tmpfs /tmp:rw,exec,nosuid,nodev,size=128m -v "$PWD:/work:ro" -w /work --entrypoint /bin/bash mcr.microsoft.com/playwright:v1.51.1-noble -c 'bash scripts/production/tests/backup-test.sh'`
also passed, exit0, covering backup/config/S3/download/freshness/provenance,
credential rejection and flock inheritance. It used no network or Docker socket
and performed no production backup. Commit, push and merge are owner-managed;
the agent did not stage or commit these changes.

#### Final acceptance checkpoint (2026-10-09 22:34 JST)

- Read-only SSM inspection reconfirmed account/region/instance via IMDS, all eleven fixed operations hashes and corrected config hash/root:root 0600. Both timers are enabled/loaded/active/waiting. No timer, alarm, IAM, S3 configuration or production data changes were made.
- Daily backup has no LastTrigger/start/exit timestamp and no journal entries since timer activation. Its default success/exit0 is not execution evidence. **First scheduled backup remains HOLD (not yet due)**; next observed execution is **2026-10-10 02:01:34 JST**. Inspect after completion, initially around 02:10 JST; do not treat an in-progress run as failure or manually trigger/retry it.
- Automatic freshness service last ran **22:00:07–22:00:13 JST**, Result=success/exit0; retained recent journal entries also show success at 20:01 and 21:01. Next observed freshness schedule **23:00:30 JST**.
- Existing Stage B receipt still identifies the 17:20 recovery point. Read-only receipt validation and checksum-enabled HEAD on all three exact S3 versions passed: sizes, SHA-256 checksums/metadata, Version IDs, SSE `aws:kms` and specified KMS key all match. All three local encrypted artifacts/sidecars match receipt hashes and sizes. This validates the existing manual recovery point, not a new scheduled backup.
- DescribeAlarms remains AccessDenied; no IAM changes/repeated role attempts. The owner confirmed **ActionsEnabled=true / state OK** in the console during this checkpoint. Stored CloudWatch state is owner-verified, not agent API-read. End-to-end alarm-triggered SNS delivery is still not established by the earlier direct SNS test.
- Five production containers healthy; MySQL ID/start timestamp match the protected baseline, database Volume metadata and deployment state/events hashes match previous records. Student and teacher HTTPS login probes each returned 200 at 22:34 JST. No SQL writes, Flyway, manual backup, existing container/Volume modifications or service/timer configuration changes were performed.
- Git review: four tracked modified files plus one untracked config regression test, all backup-specific: environment example, backup mock test, config regression test, deployment evidence and error report. Changes remain unstaged; `git diff --check` and both changed test scripts' syntax checks passed. Runtime config is ignored; no private age identity or credentials were added. No commit/push/staging was performed.
- **Automatic scheduling may continue (GO); final automatic-backup acceptance remains HOLD only for the first scheduled run and its storage verification.** Next acceptance must correlate actual systemd execution/end status with the new receipt timestamp, all three new exact-version S3 objects and local hashes, then verify app/MySQL health. Do not approve a failed or partial upload as success.

#### Current status: Stage D applied / Stage E timers active (2026-10-09 17:53 JST)

This current record supersedes the configuration/notification/timer blockers in
the historical Stage A–D preparation records below. It does not claim the first
daily scheduled backup or the alarm's subsequent OK transition has completed.

- Under separate approval, saved the old backup-only config as `/var/lib/ppe/backup-config-change-20261009-1741/operation.env.before`, then atomically updated `/var/lib/ppe/operation.env` with exactly two additions: `PPE_TLS_DIR=/var/lib/ppe/tls-private/current` and `PPE_ACME_DIR=/var/lib/ppe/acme`. No settings were removed. New config SHA256 `b116321d93fceaacdd20ea47d711bd8df9e555aa1d95f79eb69d0900cb12d26b`, root:root 0600. Compose parsing and unchanged DB selection passed. Immutable operations/release files were not modified.
- Approved manual freshness check at `2026-10-09T08:42:48Z` succeeded, exit0, with FreshBackup=1 submission. One approved SNS test returned MessageId `455054b1-285b-5ae6-b803-37f383142d29`; the owner confirmed receipt. No new backup was executed.
- CloudWatch/SNS readback APIs remain denied to SSO and instance roles. GetMetricData was also denied; integrated browser console access failed with ERR_ABORTED. No IAM changes were made. The owner confirmed BackupSuccess=1, FreshBackup=1, SNS delivery and the specified freshness alarm configuration in the console. EnableAlarmActions was denied for both existing roles; the owner enabled actions in the console, not via the agent.
- At 17:52 JST both timers were still disabled/inactive. Under the owner's subsequent Stage E approval, revalidated IMDS target, eleven fixed payload hashes, corrected config hash/mode, receipt, DB identity, unit copies/no drop-ins, shared-lock availability and five healthy containers. Enabled/started only `ppe-backup-freshness.timer` and `ppe-backup.timer`; both are enabled, active, waiting.
- Actual automatic freshness execution: 17:53:28–17:53:34 JST, systemd service Result=success, ExecMainStatus=0. Journal confirmed a complete versioned backup within the freshness window. This proves scheduled checker execution and successful metric submission, not an independent CloudWatch API readback.
- Next schedules observed immediately after activation: freshness **2026-10-09 18:00:49 JST**, daily backup **2026-10-10 02:01:34 JST**. Daily backup service had no execution start timestamp; default Result=success/exit0 is not evidence that a scheduled backup ran. No manual dump was triggered to manufacture acceptance.
- Protected before/after snapshots of existing container IDs/images/start times/mounts/status, database Volume metadata and deployment state/events hashes matched. Existing receipt revalidation passed. Student/teacher HTTPS login probes returned 200 at 17:53 JST. No production SQL writes, Flyway, existing container/Volume mutation or application deployment occurred.
- Owner console evidence: alarm created 12:26 JST and entered ALARM at 12:28 JST from missing data before monitoring began; ActionsEnabled=true, current state remains ALARM. FreshBackup samples around 17:42 and 17:53 belong to the same hourly period, not two separate healthy periods. With Minimum/3600 seconds/2 of 3/missing=breaching, wait for normal hourly samples and inspect State reason/history. Do not alter thresholds, force alarm state or inject extra metrics to clear it. Future ALARM/OK transitions can now notify SNS; a direct SNS test does not prove end-to-end alarm-action delivery.
- Scheduling configuration is **GO**. Full automatic-operation acceptance remains **HOLD / observation pending** until the alarm transitions to OK on genuine healthy periods and the first daily scheduled backup passes receipt/exact-version validation. First daily run creates new encrypted S3 objects as approved automatic operation; do not automatically retry a failed backup that may have uploaded artifacts. Continue read-only monitoring without additional settings changes.

#### Stage A inactive installation accepted (2026-10-09 16:27 JST)

- Explicit user approval covered SSM transfer, new backup operations/config/public recipient and four inactive units, daemon-reload and read-only acceptance. Fixed operations source: `ff4c1c36b911fc51be4b5915b3afd0b935606223`; archive SHA256: `88c0f55f9308acf392adbd4407dec4f364d34e2aa3206fd376862d9c54121de4`. Application release remains `0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`.
- SSM and IMDS confirmed account `024378233912`, region `ap-northeast-1`, instance `i-0ffd69e8f390bd396`. No existing backup units, destination files or related operation processes were found before installation.
- Installed seven scripts and four source unit copies under `/var/lib/ppe/operations/ff4c1c36b911fc51be4b5915b3afd0b935606223`, root:root 0644 with containing directories 0700. New `backup-current` points to that directory. Four units installed root:root 0644 in `/etc/systemd/system`. New `/var/lib/ppe/operation.env` and `/var/lib/ppe/config/backup-age-recipient` are root:root 0600; config parent is 0700. Existing files were not overwritten.
- Local and remote archive/config/public hashes, all eleven payload hashes, syntax, installed hashes/ownership/modes and pointer passed. `systemd-analyze verify --man=no` succeeded; `systemctl daemon-reload` succeeded. Four units loaded/inactive, two timers disabled, no drop-ins; both services' execution start timestamps were zero.
- Before/after snapshots of all five container IDs/images/start times/mounts/status, Volume metadata, Docker image inventory, deployment.state/events hashes and local backup file inventory were identical. All five containers healthy. Both production HTTPS login endpoints returned 200 after installation.
- No backup, SQL, Flyway, Docker mutation, timer/service start/enable, S3 write, metrics/notification publication or AWS configuration change was performed. S3 object inventory was not independently queried during this acceptance; no upload path was executed. Actual backup/S3/restore acceptance remains outside Stage A and requires separate approval.
- First transfer failed because long base64 input exceeded terminal framing capacity; retained `/var/lib/ppe/backup-stage-a-ff4c1c36-20261009/operations.tar` is a partial 3,071-byte file. Short 76-character lines with paced transmission passed a 51,200-byte dummy local PTY test and fixed-file roundtrips before successful transfer into new `/var/lib/ppe/backup-stage-a-ff4c1c36-20261009-r2`. Both staging directories are retained; cleanup is not approved. See [error report](error-report.md) for failure and resolution evidence.

#### Stage B manual backup storage accepted with monitoring caveat (2026-10-09 17:22 JST)

- An initial invocation failed before DB dump/S3 PUT because the installed EnvironmentFile omitted `PPE_TLS_DIR` and `PPE_ACME_DIR`, which full Compose parsing requires even for `ps db` / `exec db`. After read-only diagnosis, user separately approved one retry. Existing nginx mounts supplied `/var/lib/ppe/tls-private/current` and `/var/lib/ppe/acme` as transient environment variables. No installed file, TLS asset, immutable release or unit was changed. The local EnvironmentFile example now includes these variables; **before timer activation, separately approve a reviewed persistent configuration update**. The current scheduled configuration alone still fails.
- Approved retry started at `2026-10-09T08:20:35Z` (17:20:35 JST), exit0. `--single-transaction` dump and age encryption completed; source identity remained unchanged. Three versioned objects under `s3://ppe-production-mysql-backups-024378233912-ap-northeast-1-an/production/mysql/` passed checksum-enabled HEAD and receipt validation. Local encrypted copy, checksum and manifest remain root-only 0600 in `/var/lib/ppe/backups`; successful receipt is root-only 0600 in `/var/lib/ppe/state/last-successful-backup.receipt`.

| Object basename | Bytes | Version ID |
|---|---:|---|
| `ppe-20261009T082037Z.O3vi1j.sql.age` | 110489 | `5hC9psnT52akVYEJ5U8JsSkeb5kkWI5R` |
| `ppe-20261009T082037Z.O3vi1j.sql.age.sha256` | 102 | `GWJ7kr3cPbZcJ2zKFsaEg4i_leN7uDOh` |
| `ppe-20261009T082037Z.O3vi1j.sql.age.manifest` | 994 | `tlm1am69kdmmyJ7nBYf5Od57MHRS1PDH` |

- All three: SSE `aws:kms`, KMS key `arn:aws:kms:ap-northeast-1:024378233912:key/af2c55b8-109d-4c81-8a94-05def6e56047`, BucketKeyEnabled=true. Ciphertext SHA256 `40eaf6631dc12565aea674137a6ac52f10c9b75578cbeb0744418d4c14334f3d`; checksum-file SHA256 `605e157357691382253f641fb2443be6b9463ec09822f3ed55f488887a4a1102`; manifest SHA256 `81ea609ef9b193ae3bd377447e86f5695d795168acd55296c8f90c70f9f65291`. Local hashes match the receipt and S3 checksums/metadata; age file format checked without displaying payload. Decryption authentication / SQL restoration / restored data integrity remain Stage C, not yet verified.
- `BackupSuccess=1` publication returned success within backup.sh, and receipt installation followed successfully. Independent stored datapoint inspection is **unconfirmed**: both SSO and EC2 role denied `cloudwatch:GetMetricStatistics`. Owner must verify `PPE/Backup / BackupSuccess / Project=ppe-production` around 17:20 JST in the console or authorize a read-only principal. Do not infer a observed datapoint from API submission success. S3 global listing likewise denied `s3:ListBucket`; exact-version HEAD of the three known objects succeeded.
- Before/after protected snapshots matched for five container identities/images/status/start times/mounts, Volume metadata, image inventory and deployment state/events. Final five containers healthy and both HTTPS login endpoints200. EnvironmentFile/public-recipient/journal hashes remain unchanged; both timers disabled/inactive, services inactive. No DB/schema writes, Flyway, existing-container mutation, AWS configuration change, object overwrite/delete, key transfer or restore performed. Short-lived age containers, SQL reads and shared lock/receipt/ciphertext writes were the approved backup workload.
- Stage C isolated restore may be planned for this exact version; actual execution requires separate approval. Stage B ciphertext storage passed, monitoring readback remains open, and timer enablement is blocked pending persistent Compose environment correction and later approval.

#### Stage C isolated recovery accepted (2026-10-09)

- Explicit user approval covered fixed-version retrieval and recovery into a new isolated local MySQL, not production restore. Receipt and checksum-enabled HEAD for the three stored objects were reverified. Data Version `5hC9psnT52akVYEJ5U8JsSkeb5kkWI5R`, 110489 bytes and SHA256 `40eaf6631dc12565aea674137a6ac52f10c9b75578cbeb0744418d4c14334f3d` matched the retained EC2 ciphertext byte-for-byte after download.
- Local host age authenticated the entire file using the existing private identity on the Mac. The private identity was not copied, mounted in Docker or sent to EC2/S3/logs. No plaintext dump was persisted. SQL streamed into dedicated project `ppe-restore-c20261009-1723`, a new `mysql:8.0.44` container with `network_mode: none`, no published ports and a new dedicated Volume. Collision and empty-database guards passed.
- SQL restore exit0 with no stderr; 64 tables restored, CHECK TABLE64/64 OK, 126 foreign-key constraints checked with zero orphan rows. Flyway V1-V23 all success. Full Flyway descriptions/scripts/checksums/status hash `85a685ac3f32343774e08b1bb3353a3b85e52d0c9fd1eeb08426dd40ed382f57` and all-table count report hash `bdb66da5cd8aa7b6b28d9744efa56a03709222f2dca0ae04b0c755645a023abb` matched separate read-only production aggregate queries after restoration. No migration execution or Flyway repair was used.
- Row counts: flyway_schema_history23, consent_document_versions1, student_login_sequence1; remaining61 tables0, including users/tasks/code_logs/submissions/evaluations. This is a verified **initial-state** recovery, not evidence that populated student logs/submissions/evaluations were restored. Representative synthetic populated-data recovery remains to be tested independently before claiming full business-data protection.
- Additional local negative checks: truncated ciphertext failed age authentication, and existing restore-isolated.sh refused a nonempty dedicated database. The dedicated test container/Volume and generated password were removed after acceptance; pre-existing local Docker resources remained unchanged. Encrypted recovery files and metadata-only reports remain private on the local PC.
- Production remained five healthy containers, same DB ID/StartedAt/image, Volume identity, state/events hashes; both HTTPS login endpoints200. Production interactions were read-only SQL aggregates, HEAD/GetObject and SSM output streaming. No production SQL writes, AWS settings/object changes, secret-key transfer, timer activation or alarm activation.
- Stage D preparation is GO, execution remains separately gated. Persistent backup EnvironmentFile correction and stored CloudWatch metric readback are still open; Stage C does not authorize enabling scheduled backup or notifications. Application E2E on the recovered DB, populated-data cases and recovery at production scale remain unverified.

#### Stage D preparation / read-only monitoring review (2026-10-09 17:38 JST)

**Preparation complete; production activation HOLD.** No production setting, unit,
IAM, SNS or alarm was changed, no service was started, no backup or metric/SNS
publication occurred during this review.

Current independently verified state:

- Eleven installed fixed-source payload files still match their checksums. The
  current receipt is fresh and all three exact-version S3 objects verify.
- Four units loaded/inactive, services root with WorkingDirectory `/var/lib/ppe`
  and EnvironmentFile `/var/lib/ppe/operation.env`; both timers disabled.
- Installed config still SHA256
  `c15ba38d9f01666eced45e0b911c09f0bcb7dd9801fb1042976aced62cc72434`
  and lacks `PPE_TLS_DIR` / `PPE_ACME_DIR`. Existing nginx mounts confirm
  `/var/lib/ppe/tls-private/current` and `/var/lib/ppe/acme`.
- Five production containers healthy; both HTTPS login endpoints200.
- SSO and EC2 role both deny `cloudwatch:GetMetricStatistics`,
  `cloudwatch:DescribeAlarms`, `sns:GetTopicAttributes`,
  `sns:ListSubscriptionsByTopic`. Actual BackupSuccess/FreshBackup datapoints,
  current alarm values/state and current subscription state are **unconfirmed**,
  not absent. Previously owner-confirmed configuration below is not a new
  independent verification.

| Monitoring item | Implementation / previously confirmed intended setting |
|---|---|
| Namespace / dimension | `PPE/Backup`, `Project=ppe-production` |
| BackupSuccess | Count1 only after three remote objects and temporary receipt verified; emitted before final receipt rename. Stage B submission succeeded; stored datapoint readback remains unconfirmed |
| FreshBackup | Count1 after valid receipt / 3 exact-version HEADs and age <=93600 seconds (26h); Count0 + SNS attempt + exit1 if receipt verification fails |
| Missing signals | Config/SSM failure produces no metric; metric submission failure exits before later SNS. Checker stopped also yields no signal. Missing-data alarm is required |
| Backup timer | Daily02:00 Asia/Tokyo +0–300s randomized delay, Persistent=true |
| Freshness timer | Hourly +0–60s delay, OnBootSec10min, Persistent=true |
| Intended alarm | `ppe-production-backup-freshness-alarm`, FreshBackup, Minimum, period3600, <1, evaluation3/datapoints2, missing=breaching |
| Intended SNS action | `arn:aws:sns:ap-northeast-1:024378233912:ppe-production-backup-alerts` |
| Last owner confirmation | ActionsEnabled=false, email confirmed/test received; current values unconfirmed because read API denied |

**Persistent config proposal.** Add only these two nonsecret variables to the
backup-specific EnvironmentFile, not application/release/TLS configuration:

```sh
PPE_TLS_DIR=/var/lib/ppe/tls-private/current
PPE_ACME_DIR=/var/lib/ppe/acme
```

Cause: Compose resolves required interpolation for all services even on db-only
`ps`/`exec`; backup-only config template omitted required nginx/acme variables.
Local ignored candidate `deploy/runtime/backup-operation.env` now includes them,
mode0600, SHA256
`b116321d93fceaacdd20ea47d711bd8df9e555aa1d95f79eb69d0900cb12d26b`.
This differs intentionally from Stage A config; it is **not deployed**. The fixed
operations archive/hash remains unchanged. Do not alter the approved Stage A
manifest to pretend the candidate was already installed.

`bash scripts/production/tests/backup-operation-config-test.sh` passed5 checks:
correct read-only TLS/ACME mount paths, unchanged db recipe, missing TLS refusal
and missing ACME refusal. Uses Compose config only, never up/exec/SQL. Extended
Linux backup mocks passed exact BackupSuccess/FreshBackup metric shapes and
missing-receipt Count0/SNS, config/metric failure missing signals, plus existing
S3/SNS/download/locking failures. Mocks ran network-none without Docker socket.

**Least-privilege observer access proposal (not applied).** Prefer a management
observer principal or the appropriate IAM Identity Center permission set assigned
to the operator, not the EC2 backup writer role. Do not directly edit the
AWSReservedSSO-generated role. Exact CLI calls here need only the following;
console discovery might need additional rights and is not included:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadMetricStatisticsInTokyo",
      "Effect": "Allow",
      "Action": "cloudwatch:GetMetricStatistics",
      "Resource": "*",
      "Condition": {"StringEquals": {"aws:RequestedRegion": "ap-northeast-1"}}
    },
    {
      "Sid": "ReadOnlyThisBackupAlarm",
      "Effect": "Allow",
      "Action": "cloudwatch:DescribeAlarms",
      "Resource": "arn:aws:cloudwatch:ap-northeast-1:024378233912:alarm:ppe-production-backup-freshness-alarm",
      "Condition": {"StringEquals": {"aws:RequestedRegion": "ap-northeast-1"}}
    },
    {
      "Sid": "ReadOnlyThisBackupTopic",
      "Effect": "Allow",
      "Action": ["sns:GetTopicAttributes", "sns:ListSubscriptionsByTopic"],
      "Resource": "arn:aws:sns:ap-northeast-1:024378233912:ppe-production-backup-alerts",
      "Condition": {"StringEquals": {"aws:RequestedRegion": "ap-northeast-1"}}
    }
  ]
}
```

Metric statistics do not support a metric ARN resource restriction. `Resource:*`
here permits metric reads across the Tokyo account, **not just PPE/Backup**;
`cloudwatch:namespace` cannot safely be used to claim GetMetricStatistics is
namespace-restricted. If this read breadth is unacceptable, have an authorized
owner run the exact commands and provide only the requested redacted results.
No ListMetrics/GetMetricData, write/enable/delete, Publish or IAM admin action is
needed for this CLI review. DescribeAlarms is scoped to the one metric alarm;
composite alarm inspection is deliberately excluded.
See [CloudWatch permissions reference](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/permissions-reference-cw.html)
and [DescribeAlarms reference](https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_DescribeAlarms.html).

**Future validation and approval units (none executed now):**

1. Approve persistent backup EnvironmentFile replacement only: read/check old
   hash/owner0600, transfer reviewed candidate into fresh private staging, verify
   new hash, retain private rollback copy, atomically replace only operation.env.
   Verify Compose db selection and existing assets with read-only commands.
   No service start/reload or app/TLS modification is necessary. If old hash
   changed or the candidate diff is more than two variables, stop.
2. Owner grants the observer read access above or supplies redacted API results.
   Read BackupSuccess around08:20Z October9 and FreshBackup over the relevant
   current window; metric absence must be distinguished from denial. Describe
   alarm exact values/actions/state; confirm SNS email subscription without
   logging the email address. SNS topic policy must permit CloudWatch alarm
   publication; inspect that policy locally/redacted with GetTopicAttributes
   (this review did not establish that permission).
3. Approve one manual freshness **script** run using the existing receipt, not a
   systemd unit/timer. It may submit FreshBackup1; if remote verification fails it
   may submit0 and send a failure SNS notification. No new backup is created.
   Read back emitted datapoint and require receipt verification, not metric alone.
4. Approve one clearly identified SNS test message to the existing topic and
   confirm email receipt. This verifies SNS delivery but not the CloudWatch path.
   Do not intentionally fail production backup or emit fabricated BackupSuccess.
   Missing/stale/config/metric failures are tested locally with mocks.
5. For an alarm-to-SNS end-to-end test, separately approve a disposable test
   metric/alarm tied to the same topic with explicit cleanup, or owner-approved
   test of the actual alarm. Do not fake FreshBackup0 / SetAlarmState on the
   production alarm merely to get a test email. Such resource changes are not
   presently approved.
6. Approve actual production alarm action enablement only after verifying live
   configuration, topic authorization and healthy FreshBackup samples. Actions
   disabled prevents notification, not evaluation: the alarm may already be
   ALARM due to intentional missing data. Resolve/understand the state before
   enablement; do not assume a one-off SNS test proves alarm delivery.

**Stage E conditions:** reviewed persistent config installed; observer readback,
SNS and alarm delivery accepted; current complete recovery receipt; sufficient
space; no competing deploy/backup; unit hashes/paths/permissions correct;
shared lock effective for participating deployments (legacy ops/manual SQL do
not necessarily participate); approvals for both timer activations and alarm
actions. Persistent timers may catch up immediately on activation, including a
new backup/S3 writes: activation approval must expressly include that workload.
Enable freshness and observe healthy samples before notification enablement;
daily backup timing/catch-up requires a separate agreed window. Confirm next
scheduled times and successful subsequent automatic executions, rather than
considering enabled=accepted. Both timers remain disabled now.

**Detection limits:** 26h age tolerance plus the hourly2-of3 alarm evaluation
means a missed daily backup is not an immediate alert at02:00; exact latency
depends on checker timing, sampling and CloudWatch missing-data evaluation.
Checker shutdown is covered by breaching missing samples once notifications
are enabled. SNS failure and checker-config failure still need independent
alarm delivery. Alarm missing-data behavior is not proof of EC2/software
availability or populated-data recovery coverage.

This section supersedes earlier unconfirmed-resource reports and generic console
examples for this installation. AWS facts below were **confirmed by the owner**,
not independently re-read by this agent. No AWS or production writes were made
while preparing this section.

| Item | Current readiness / source |
|---|---|
| Bucket | Owner confirmed Versioning Enabled, approved SSE-KMS key, **Bucket Key enabled**, all four public-access blocks true |
| Lifecycle | Owner confirmed `production/mysql/`, current expiration 30 days, noncurrent expiration 30 days, incomplete multipart abort 7 days |
| Bucket policy | Owner confirmed absent (`NoSuchBucketPolicy`); this is not a public-access finding by itself. IAM grants apply subject to other controls; TLS/exact-key enforcement is not established by an absent bucket policy |
| KMS / IAM | Owner confirmed key policy permits EC2 role and required S3/KMS/SSM/SNS/CloudWatch grants. Actual PUT/checksum/metric/publish acceptance remains untested |
| SNS | Owner confirmed topic `ppe-production-backup-alerts`, confirmed email subscription and received test email; EC2-origin and CloudWatch-origin delivery remain untested |
| Alarm | Owner confirmed `ppe-production-backup-freshness-alarm`, `PPE/Backup` / `FreshBackup`, `Project=ppe-production`, Minimum, 3600 seconds, LessThanThreshold 1, 2 of 3, missing breaching, approved SNS destination, **ActionsEnabled=false** |
| Local code / key | Prior mock/roundtrip/regression results passed; matching public/identity verified locally. Private identity stays off production and outside Git |
| Production installation | Latest agent read-only inspection at approximately 12:30 JST: DB/volume/journal identities unchanged, five containers healthy, config/operations pointer and inspected backup units absent |
| Recovery acceptance | Full production-schema S3 recovery, performance impact, independent credential/secret continuity and measured RPO/RTO still unverified |

Bucket Key must stay enabled. If an IAM/KMS policy restricts the S3 encryption
context, it must accept the bucket ARN for Bucket Key objects. An object-prefix-only
context condition from the generic writer template is not compatible. Existing
objects without Bucket Keys may require their object ARN context as well. S3 IAM
still limits object operations to `production/mysql/*`; do not weaken that scope
or alter AWS configuration as part of installation.

The generic [lifecycle template](../../containers/production/backup-lifecycle.json)
is **not the live policy**: its noncurrent 1-day / multipart 1-day values must not
be applied over the owner-confirmed 30-day / 7-day policy. With unique keys, current
expiration at day 30 normally makes a version noncurrent; another 30 days can
retain its bytes to approximately day 60 plus asynchronous processing. Current
30-day retention is not physical erasure at day 30. Noncurrent retrieval is not
automatically supported by the download tool's default 30-day age guard.
Do not change retention without a separate owner decision.

#### Installation inputs and approval gates

The ignored local candidate `deploy/runtime/backup-operation.env` contains the
existing two direct SSM names, production paths, release-pinned image and confirmed
SNS ARN. Its destination is root-owned 0600 `/var/lib/ppe/operation.env`.
The ignored local public file `deploy/runtime/backup-age-recipient` is destined
for root-owned 0600 `/var/lib/ppe/config/backup-age-recipient`. The public value is
`age1zflz5wpd9afw53qldnr2w5qs7rtxrsa4efrqt56ctmy0n568ka8qclea72`.
Neither file contains an AWS credential or age identity. Do not source this
production-path candidate to run backup on the local PC.

| Gate | Separately approved operations | Stop / acceptance condition |
|---|---|---|
| A: version and inactive installation | Final review/commit of new operations version; verified archive transfer to new `/var/lib/ppe/operations/<approved-SHA>`; config/public file and four units installed; `backup-current` pointer; `systemctl daemon-reload` only | Recheck DB/image/volume/journal identities, source files and tool/image availability. Refuse unexpected existing files; no timer enable/start, no service run, no DB/app/release changes |
| B: one manual acceptance backup | Exactly one `systemctl start ppe-backup.service`; local encrypted files, read-only DB dump, two transient age containers, S3 writes, metric and possible failure SNS publication | Complete receipt and three versioned objects verified; unchanged DB/volume/images/journal; any unexpected error stops without retry |
| C: isolated recovery | Exact-version S3 GET on independent recovery host; private identity used there only; separate empty DB/volume writes | Full schema/data/history/relationships match; no production restore or production network dependencies |
| D: monitoring acceptance | One freshness-service run, metric publication and possible failure SNS publication; separately approved notification tests and CloudWatch action enablement | EC2 publish and CloudWatch alarm delivery verified, missing-data coverage confirmed; never enable alarm actions implicitly |
| E: automation | Explicit `enable --now` of daily and freshness timers, including potentially immediate Persistent runs and recurring writes | Verify next 02:00 Tokyo run, hourly metrics, notifications and disk headroom; restoration and monitoring acceptance prerequisites met |
| R: rollback if needed | Backup timer suspension and backup-only config/unit/pointer rollback | Preserve application/DB, journal, keys, local ciphertext and S3 versions; active backup status reviewed before changing pointer |

Gate A requires a newly approved operations SHA; the working tree is not yet a
fixed installation artifact. Package scripts/helpers and the four unit definitions
only; do not package `deploy/runtime`, private identities, app secrets or TLS.
Install units inactive during Gate A so the manual test can use systemd's exact
EnvironmentFile and execution environment. Run `systemd-analyze verify` before
installation. Preserve any pre-existing destination files; no overwrite-by-default.

#### Initial manual backup: Gate B only

Run only after Gate A and explicit backup/S3/metric/SNS approval, via the approved
SSM connection. Do not wrap the start command in a retry loop.

```sh
sudo systemctl start ppe-backup.service
sudo systemctl show ppe-backup.service -p Result -p ExecMainStatus -p ActiveState
sudo journalctl -u ppe-backup.service --since '<approved-start-time>' --no-pager
sudo stat -c '%n|%a|%U|%s' /var/lib/ppe/state/last-successful-backup.receipt
```

Review journal privately; share only sanitized results. A successful oneshot may
be inactive after completion. Require `Result=success`, `ExecMainStatus=0`, a
root-owned 0600 receipt, nonempty local age ciphertext/sidecar/manifest, and exact
S3 versions for all three objects, matching size/SHA256/SSE-KMS key. Use receipt
fields privately to select `HeadObject --version-id ... --checksum-mode ENABLED`;
do not print backup payload or download it on production. Record `BackupSuccess=1`,
source container identity, elapsed time, CPU/I/O/disk impact, and unchanged
deployment journal/image/volume identities. Dump may create transient private
MySQL client-options files and consume resources but does not issue DB DDL/DML.

`BackupSuccess=1` is emitted before final receipt placement, so do not accept that
metric alone. Gate D executes `sudo systemctl start ppe-backup-freshness.service`
after receipt acceptance and checks `FreshBackup=1`. Do not fabricate failure
tests by modifying production receipt, permissions, objects or AWS connectivity;
use local mocks and separately approved monitoring tests.

#### Restoration and rollback acceptance

Follow the exact-version download and isolated restoration drill below. Recovery
permissions must cover versioned HEAD/GET and required KMS checksum permissions:
`kms:Decrypt` and `kms:GenerateDataKey` via S3, with Bucket Key-compatible context.
Use an independent protected version inventory and private recovery directory.
Restoration must verify source-era Flyway versions/checksums, tables, routines,
triggers/events, IDs, row contents, FK relationships and historical evaluation
references. Compare a consistent source snapshot/manifest, not live row counts
that may have changed since a single-transaction dump. Independently preserve
application secret/AES-key continuity; the dump does not contain those assets.

If Gate B fails, stop and diagnose; do not delete or retry just because the command
returned failure (objects or even success metrics may already exist).
For approved rollback, first disable/stop **timers only**; stopping a timer does
not terminate an active service. Let a running dump finish, or request explicit
incident approval before interrupting it. Restore prior backup-only pointer,
config and unit files only after no backup is active, then daemon-reload without
reenabling timers. If there was no previous backup installation, leave new units
inactive and retain evidence. Never stop Docker/MySQL/app, undo Flyway, alter
deployment.state, delete backups/versions or rotate keys. CloudWatch action
rollback, if actions were enabled at Gate D, requires separate AWS-change approval.

Preparation validation for this update passed: network-disabled local Linux
backup/config/S3/download/failure mocks, real flock inheritance, inactive systemd
syntax/dependency verification with inert Docker fixture, and mocked isolated
restore refusal tests. Shell parsing, ignored candidate config mode 0600 and
whitespace checks passed. No live AWS acceptance, production dump, real DB restore
or unit activation was performed by these tests.

### Console procedure (generic reference; owner work, not executed)

1. **Inventory first.** In EC2 → Instances → target instance → Security, follow the
   IAM role link. Confirm its actual role name (instance profile name alone is not
   proof). In IAM review existing attached/inline policies and trust relationship.
   Inspect S3, KMS, SNS and CloudWatch in Tokyo. Do not broaden the deployer SSO
   role just to bypass this investigation's denied list permissions.
2. **KMS.** If no suitable key exists, KMS → Customer managed keys → Create key:
   symmetric, Encrypt/decrypt, single-region Tokyo. Choose a descriptive alias
   such as `alias/ppe-backup`; select a distinct human administrator, not EC2.
   Enable rotation if policy allows. Record key ARN privately. Preserve administrator
   access and review key policy delegation to IAM; the writer policy alone works
   only if the key policy permits that account/role. The writer needs GenerateDataKey
   and Decrypt **via S3 only**, with Bucket Key-compatible encryption context.
   Recovery checksum HEAD needs these permissions too, not KMS administration.
   Do not schedule key deletion.
3. **S3.** Reuse a suitable bucket or S3 → Create bucket, general purpose, Tokyo,
   globally unique name, Object Ownership bucket-owner-enforced/ACL disabled,
   all Block Public Access ON, versioning enabled. Default encryption: SSE-KMS,
   approved key. Current prepared bucket has **Bucket Key enabled**; preserve it.
   Review bucket-ARN KMS context instead of applying the generic object-ARN
   policy condition. Prefix isolation remains enforced by S3 IAM.
4. **S3 permissions.** Bucket → Permissions → Bucket policy:
   current bucket has no policy; this preparation does not authorize adding one.
   TLS/exact-key enforcement by resource policy is an optional separately approved
   hardening task, not something to silently install with backup.
   merge the [policy example](../../containers/production/backup-bucket-policy.json)
   after replacing ACCOUNT_ID/BUCKET_NAME/KEY_ID and matching prefix.
   Preserve any existing controls; do not replace a reused bucket policy wholesale.
   Require TLS, explicit aws:kms and exact key. No public access or app credentials.
5. **S3 retention.** Bucket → Management → Create lifecycle rule, prefix
   `production/mysql/`, expire current versions at **30 days**, permanently expire
   noncurrent versions **30 days** after becoming noncurrent; abort incomplete
   multipart after **7 days**, as owner-confirmed. Expired-marker cleanup is
   unconfirmed. The older [JSON template](../../containers/production/backup-lifecycle.json)
   is generic and must not be applied to this installation.
   With unique keys, current expiration normally creates a delete marker on day30,
   then noncurrent physical deletion is approximately day60 plus asynchronous
   processing. This is **not exact 30-day guaranteed physical erasure**.
   Stop if research policy requires a strict deadline. No Object Lock is enabled
   by this proposal; review immutability versus deletion obligations separately.
6. **SNS.** Reuse a suitable Standard topic or SNS → Topics → Create topic →
   Standard, e.g. `ppe-backup-alerts`. Create Email subscription for the owner;
   open confirmation mail and confirm. Record topic ARN. Grant writer Publish only
   to this topic. If customer SNS SSE-KMS is required, its extra key permissions
   must be designed separately.
   For a new topic carrying only generic operational messages, SNS SSE is not
   required by this template (TLS transport is used). If a reused topic already
   has SSE, preserve it and review the extra KMS publisher/alarm permissions;
   do not disable encryption to make a failed test pass.
   Console publish of a generic test message and recipient receipt require
   explicit approval because they are AWS writes.
7. **IAM writer role.** IAM → actual EC2 role → Permissions → Create inline policy →
   JSON. Use [writer policy](../../containers/production/backup-writer-policy.json),
   replace placeholders/prefix, validate, review and add with owner approval.
   For this installation select only the two direct GetParameter ARNs and a
   Bucket Key-compatible KMS condition; do not apply unused namespace grants.
   The runtime requires prefix PutObject/GetObjectVersion,
   narrow KMS operations, SNS Publish and namespace-constrained PutMetricData.
   Explicit delete deny prevents other identity policies from accidentally granting
   recovery-point deletion. No list-buckets, list-prefix, Parameter writes,
   lifecycle edit, key administration, multipart or AccessKey creation is needed.
   HeadObject with version uses GetObjectVersion; this also permits ciphertext
   download, since IAM cannot distinguish HEAD from GET here. Age keeps plaintext
   inaccessible without the offline identity.
8. **Restore role.** Use a separate human SSO/assumable recovery role with only
   `s3:GetObjectVersion` on the same prefix and `kms:Decrypt` /
   `kms:GenerateDataKey` via S3/context for checksum verification.
   The operator selects exact keys/versions from the protected inventory/manifest.
   Add prefix-scoped ListBucket/ListBucketVersions only if console browsing is
   required. Do not grant restore credentials/private age identity to the writer.
   Existing broad role grants and app/broker access to IMDS must be audited;
   an instance role is not per-container isolation.
9. **Parameter Store.** Verify the two existing direct String parameters.
   Do not create the five legacy namespace parameters for this installation.
   Do not change any of the existing six application secret parameters.
10. **CloudWatch alarms.** After approved acceptance publishes metrics, CloudWatch →
    Metrics → All metrics → `PPE/Backup` → Project=`ppe-production`.
    Verify existing `ppe-production-backup-freshness-alarm`: FreshBackup,
    Minimum, period1h, threshold <1, **2 of3** datapoints, missing data
    **breaching**, approved SNS topic, **ActionsEnabled=false** until Gate D.
    It catches stale/failed hourly checks and complete host/job silence.
    Add `ppe-backup-daily-success`: BackupSuccess, Sum, period1day, threshold <1,
    2 of2 datapoints, missing breaching, same SNS. No OK metric should be published
    by a failed backup. Review the initial insufficient-data/alarm transition.
    SNS actions must also be enabled and IAM/topic policy must permit CloudWatch.
11. **Disk/host monitoring.** Confirm existing EC2 reachability and disk alarms.
    Add/approve disk-free threshold with headroom beyond 1 GiB, memory/CPU credits
    as needed. No local pruning is automatic in the new script; growth needs a
    reviewed cleanup procedure after remote points are proven recoverable.

Never paste private keys, backup contents, passwords or credential files into
console logs/chat. Template JSON must contain no placeholder before installation;
the console guide is not permission to install it now.

### Cost and readiness

Provisional retention changed from the earlier proposal to 30 days at owner request.
Budget items: full encrypted dump GiB × retention/versions, S3 PUT/HEAD/GET,
SSE-KMS key/GenerateDataKey/Decrypt requests (including hourly receipt verification),
two custom metrics/two alarms, SNS email/publish and restore data transfer.
Example only: daily 1 GiB × about60 days ≈60 GiB of eventual retained versions
under current/noncurrent 30/30 settings, plus pre-update/orphan objects;
actual encrypted DB size and current Tokyo prices are **unconfirmed**.
Use AWS Pricing Calculator and account budgets; disabling Bucket Keys favors
prefix isolation but increases KMS requests. Start S3 Standard to avoid archive
restore delay/minimum-duration surprises. No AWS resource cost was incurred by
local mocked tests; local Docker CPU/disk cost is separate.

### Approval-gated production installation

1. Review this code, commit/tag a **new operations version**, preserve old application
   release and seven image IDs; do not modify the deployed de2b0d3 directory.
   Recheck healthy DB/volume/current release/journal, available disk, IAM/config.
2. After owner console changes and explicit installation approval, verify an
   operations-only archive, place it in a new private `/var/lib/ppe/operations/<new-ops-SHA>`,
   then atomically point `backup-current` to it. Ensure Linux bash/flock, AWS CLI v2
   supporting checksum-mode and conditional PutObject, OpenSSL, Docker and age image
   exist. Do not rebuild or retag the existing release images.
3. Install a reviewed root-owned 0600 `/var/lib/ppe/operation.env` using the example,
   with exact immutable release/Compose/image/secret/state/backup paths.
   Unit files point only to operations outside the release. Refuse existing files
   with unexpected content; no overwrite-by-default installation script is provided.
4. With explicit **backup-write approval**, run one service manually and verify
   its nonzero/zero status, private receipt, three exact S3 object versions,
   checksums/encryption, `BackupSuccess`, journal and recipient notifications.
   Run the read-only freshness service to publish initial `FreshBackup` before
   selecting that metric in the console.
   Do not proceed just because a `.sql.age` file exists. Upload/metric failure
   requires diagnosis; leave ciphertext intact, no blind retry.
5. With separate isolated-restore approval, complete the drill below and prove
   data/credential-key continuity. Run synthetic update/failure regressions before
   normal update using this tooling. Do not restart production DB to test them.
6. Install both service/timer pairs inactive at Gate A under
   `/etc/systemd/system` and daemon-reload; after recovery/monitoring acceptance
   and Gate E approval, enable/start the two timers.
   Verify `systemctl list-timers`, next 02:00 Tokyo execution, unit exits/journal,
   hourly FreshBackup, notification delivery and missing-heartbeat alarm.
   Persistent=true may cause an immediate missed daily run on activation;
   include that backup write in the approval.
7. Resume/restore remain manual approved operations. Existing legacy journals
   without a matching receipt must stop, not be edited or treated as backed up.
   Agree a fresh protected pre-migration backup under the new lock if schema is
   demonstrably at baseline; partial DDL/history needs separate recovery review.
   If service is already quiesced, keep it so until review; do not restart it blindly.

### S3 download and isolated restoration drill

[download-backup.sh](../../scripts/production/download-backup.sh) retrieves the
exact completion manifest/version, verifies its hash/KMS and referenced object
versions, downloads ciphertext/checksum to a **new** 0700 directory and normalizes
the sidecar basename after validating it. It never decrypts or writes DB.
It uses a 30-day retrieval age by default; older retained points need explicit
policy review. A completion manifest lives next to the backup and is not an
independent signature: retain the trusted version inventory separately.

After retrieval/isolated-write approvals only, on a controlled recovery host:

```sh
# Use the separate recovery SSO role; no AWS access keys and no production Docker context.
export AWS_REGION=ap-northeast-1
export PPE_PROJECT=ppe-production
export PPE_BACKUP_S3_BUCKET=<approved-bucket>
export PPE_BACKUP_S3_PREFIX=production/mysql
export PPE_BACKUP_KMS_KEY_ARN=<exact-key-arn>
bash scripts/production/download-backup.sh <exact-manifest-key> <exact-version> \
  /private/recovery/point-YYYYMMDD
# Prepare a separate empty DB with its own secrets/volume before this command.
export PPE_PROJECT=ppe-restore-YYYYMMDD PPE_RESTORE_APPROVED=isolated-empty-db
export PPE_COMPOSE_FILE=/private/isolated/compose.production.yml
export PPE_SECRETS_DIR=/private/isolated/secrets
export PPE_BACKUP_IMAGE=<verified-age-image>
bash scripts/production/restore-isolated.sh \
  /private/recovery/point-YYYYMMDD/backup.sql.age /private/offline/age-identity
```

The identity must be a regular 0600/0400 file retrieved by its custodian from the
separate vault, used only on the isolated recovery host, mounted read-only into
the transient age container, never printed. Do not transfer it to the production
EC2. The script checks actual Docker DB project/volume identity, emptiness, SHA256,
and full age authentication before streaming plaintext directly into isolated
MySQL. If SQL restore later fails, partial data may remain in **that isolated DB**;
quarantine it, diagnose, and obtain approval for a new empty isolated volume.
Do not call Flyway clean/repair or automatically destroy/retry the failed restore.

Required drill test matrix (synthetic data first; production-derived data only in
an independently approved private recovery environment):

| Check | Preparation/action | Required evidence |
|---|---|---|
| Full logical data | Seed synthetic users, old/new submissions, code_logs, survey/consent, custom/standard rubrics, scores, pending/completed evaluation | Pre-backup ID/content/FK manifest against restored DB; 100% expected rows/IDs/content, no orphan refs |
| Schema integrity | Exact source release migrations, routines/triggers/events | Flyway applied rows/checksums/failures match source; validate only, no reapply |
| Historical meaning | Old evaluation snapshot/rubric/prompt/model and teacher custom row | Old hashes/IDs/scores/scale labels unchanged; no remapping to new standard |
| Encryption/keys | Correct/wrong/missing private identity, modified ciphertext/checksum | Authentication/tamper refusal before DB write; credential AES-key restoration separately verified without output |
| Restore guards | Production project, wrong mount, nonempty isolated DB, bad file modes | Refusal and original DB hashes unchanged |
| Failure handling | Simulated dump/age/PUT/HEAD/checksum/KMS/metric/config failures | Nonzero exit, no success receipt/publication, local ciphertext retained, notification/alarm observed |
| Isolation/update | Synthetic pre-update dump, upgrade/rollback failure in another project | Existing records/Flyway history/volume retained, no DB recreation, partial history refuses resume |
| Disaster/time | Recovery from S3 with original host unavailable | No original EC2 dependency for keys/source/images; measured RPO/RTO and successful read-only application checks |
| Repeatability | Repeat drill into a second newly approved empty isolated DB | Same record/relationship/hash results; never overwrite previous restored DB |

Backup dumps cover `ppe`, not MySQL accounts/grants, images, TLS, secret snapshots
or credential AES key. Disaster bootstrap and secure continuity of those assets
remain necessary. S3 existence/checksum alone is **not** a completed restore drill.

### Local verification record

- [backup-test.sh](../../scripts/production/tests/backup-test.sh): mocked SSM/S3/
  SNS/CloudWatch/Docker, success/partial failures/remote mismatches/versioning,
  download corruption, stale/provenance/credential rejection, actual Linux flock
  exclusion and inherited descriptor, both the five-name and prepared two-name
  SSM configuration modes. No network or AWS calls.
- [restore-test.sh](../../scripts/production/tests/restore-test.sh): isolated restore
  and production/wrong mount/nonempty/tampered ciphertext/wrong identity/private
  file-mode refusal with Docker mocked.
- [backup-systemd-test.sh](../../scripts/production/tests/backup-systemd-test.sh):
  systemd-analyze syntax/dependency validation with inert Docker unit fixture;
  no service installation/start. Initial validation without this fixture reported
  absent docker.service inside the test container, not a production service failure.
- [backup-roundtrip-test.sh](../../scripts/production/tests/backup-roundtrip-test.sh):
  real local MySQL dump and existing age image, fresh `ppe-sim-*` source and
  `ppe-restore-*` target, exact row IDs/code/JSON/old-new rubric references/FKs and
  nonempty second-restore refusal. Test-only volumes/containers were removed.
  This fixture does not represent the full V1–V23 application-schema acceptance.
- Separate real age encrypt/decrypt roundtrip and modified-ciphertext rejection
  passed with a disposable local identity, without outputting it.
- Targeted existing regressions also passed: deployment recovery/state,
  POSIX-to-Bash invocation, migration SUPER cleanup, and secret retrieval.
  Update tests cover missing/invalid remote receipt, preflight refusal without
  journal changes, new app with old DB pin, partial Flyway history, no duplicate
  migration, and unchanged immutable release assets.
- [deployment-db-config-hash-test.sh](../../scripts/production/tests/deployment-db-config-hash-test.sh)
  passed with real local Compose config (no daemon mutations): old DB image/context
  are preserved across new app paths. The installed Compose excludes build context
  from its service hash, so the test separately checks the rendered context.

Linux unit/mock tests use the already installed local
`mcr.microsoft.com/playwright:v1.51.1-noble` image (bash/OpenSSL/flock/systemd-analyze),
read-only repository mount, network none and executable private tmpfs, no Docker
socket. macOS itself has no Linux flock/systemd. Real database roundtrip uses only
its guarded local Docker endpoint; no production context or existing DB is used.

```sh
docker run --rm --network none --read-only \
  --tmpfs /tmp:rw,exec,nosuid,nodev,size=128m \
  -v "$PWD:/work:ro" -w /work --entrypoint /bin/bash \
  mcr.microsoft.com/playwright:v1.51.1-noble -c \
  'bash scripts/production/tests/backup-test.sh &&
   bash scripts/production/tests/restore-test.sh &&
   bash scripts/production/tests/backup-systemd-test.sh &&
   bash scripts/production/tests/database-lock-test.sh &&
   bash scripts/production/tests/deployment-recovery-test.sh &&
   bash scripts/production/tests/deployment-state-test.sh &&
   bash scripts/production/tests/shell-invocation-test.sh &&
   bash scripts/production/tests/migration-privilege-cleanup-test.sh &&
   bash scripts/production/test-fetch-secrets.sh'
bash scripts/production/tests/backup-roundtrip-test.sh
bash scripts/production/tests/deployment-db-config-hash-test.sh
```

All commands above passed in the final local run. Shell parse checks, JSON
template parsing, Compose config validation and `git diff --check` also passed.
The journal test's BSD-only permission assertion was made portable for Linux, and
the standalone privilege test now supplies the required private state directory.

At the time of the local-only tests, AWS storage/permissions, SNS delivery,
monitoring and live scheduling/restoration were unverified. The subsequent
[production checkpoints](#production-preparation-after-owner-aws-confirmation-2026-10-09-1238-jst)
record the actual Stage A–E evidence and remaining post-start operational checks.
Local mocks are not a substitute for those observations.

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
