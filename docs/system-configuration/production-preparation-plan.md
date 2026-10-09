# Production preparation plan

Date: 2026-10-08. Baseline: `521c974d37637d502769391f5ec89fac98b40420`.
This is the execution/checkpoint record for the explicitly requested twelve phases.
No AWS changes, public listeners, real student data, or real Gemini calls are authorized.
Development Compose and its existing database/volumes must remain untouched.

**2026-10-09 update:** The restrictions/results above describe the original local
preparation engagement. Subsequently approved initial EC2 deployment completed.
Current evidence is maintained in the
[production runbook](production-deployment.md#observed-production-record-2026-10-09-jst).
The following system-test plan is planning only, not new permission to execute
production tests, create accounts, call Gemini or alter services.

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

## 本番システムテスト計画（2026-10-09、設計のみ）

### 対象・調査方法・実装と受入の区別

対象release `0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`、
運用commit `de2b0d37fb7ccb6ecae93ce81b2e01d230034e01`。
Servlet mapping・Control/DAO・JS・Flyway・既存機能計画と対象Gitの差分を照合した。
今回の業務テストは**全件未実施**。既存ローカル受入や08:36頃のinfra smokeは
本番業務受入の代わりにしない。

- 学生の認証/保存/実行/提出/評価/課題別survey、教師の課題/prompt/配信/進捗/
  提出評価/演習確認、管理者の教師/学校/権限管理には対象releaseの実装がある。
  各機能の完全性は本テストで確認する。
- **教師アンケート結果確認は対象releaseに未配備**:
  `TeacherSurveyServlet`等はローカル後続commitにはあるが対象releaseにはない。
  同様に教師提出/評価・演習確認には後続改善差分があるため、最新版のブラウザー
  受入結果をそのまま対象releaseの証拠にしない。
- **正式アンケート設問登録UI、任意ルーブリック作成/編集UI、一般的な
  システム設定管理UIは未実装**。学校security levelの管理は実装済みで別扱い。
  全体アンケートは仕様対象外。初期管理者はCLIで作成し、UIからbootstrapしない。
- 同意文面・設問の正式登録、標準ルーブリック登録、研究条件・AI送信承認は未完了。
  本番users/rubrics/tasksは調査時0なので、既に業務テスト可能とは扱わない。

参照: [機能仕様](../function-specification.md)、[状態ルール](../state-rules/README.md)、
[本番観測](production-deployment.md)、[機能別計画](feature-plans/)、
[認証Filter](../../src/main/java/servlet/auth/AuthenticationFilter.java)、
[エディターJS](../../src/main/webapp/js/student/editor/editor.js)、
[標準seed設計](deployment-readiness-assessment.md#e-標準ルーブリック自動登録の調査設計案2026-10-09)。
仕様本文・ルーブリック尺度はここに複製しない。

### ユーザーシナリオと完了条件

1. P1 学生: 承認したテスト課題/同意/資格があるとき、編集→保存→実行→チェック→
   提出→評価→課題別surveyを行い、再ログイン後も同じ履歴が得られる。
2. P1 教師: 自校権限があるとき、課題/prompt→公開→進捗/提出/評価を確認し、
   別校・別クラス・権限なしでは読出し/変更できない。
3. P1 管理者: 初期資格を安全に準備し、学校と教師の権限を管理し、
   停止/再設定後に旧セッションが無効になる。一般ユーザーは管理操作不可。
4. P1 運用: 隔離環境で障害/更新/restoreを行っても、比較対象の提出・ログ・
   評価・rubric参照と内容が100%保持される。データ復旧とapp rollbackを分離する。

P1の配備済み対象は全件成功、未配備はskip理由付きで公開範囲から除外、
秘密/別利用者データの意図しない露出0、保存確認済みデータの欠損・重複0を要求する。
性能しきい値(p95/エラー率/待機時間)は所有者が試験前に確定し、未決のまま
性能合格を出さない。30秒ログの実装条件は下記S08に従う。

### データ・環境準備と本番実行区分

**推奨は別ホストの本番同等隔離環境が一次試験場**。専用Compose project/volume、
合成file secrets、別DNS/loopbackポート、合成DB、Gemini mockを使う。
本番DB dumpをfixtureに流用しない。本番EC2上で負荷を競合させない。

前提記号:

- `F`: 固定release・V1〜V23・標準定義の一致した隔離fixture、合成データのみ。
- `A`: 隔離のadmin、自校教師T1、権限の異なるT2、別校教師T3、
  学生S1/S2(同意・不同意・撤回を試験ごと変更)。資格は秘匿し文書/成果物へ保存しない。
- `D`: 学校/クラス、公開/下書き/予約/期限切れ課題、入力/outputチェックケース、
  複数版提出/ログ、演習ファイル、prompt/rubric/評価の承認済み合成fixture。
- `Q`: 正式または隔離検証用の課題別survey template。設問UIがないので
  隔離限定のreview済みfixtureで用意。本番用登録は別設計・別承認。
- `B`: 暗号化off-host backup/隔離restore成功と更新保持試験済み、停止窓/
  ロールバック/監視/データ台帳を承認済み。

将来の本番実行可否: `R`=軽微なread-only確認候補、`C`=合成専用アカウント/
データと個別承認後のみ、`X`=本番禁止・隔離で実行、`U`=未実装/未配備で実行不可。
**今回はRも含め業務テストを実行しない。** ログイン、画面GET、実行、CSV閲覧も
監査/参加状態等を更新し得るのでC。Rは「DBへ絶対書き込まない」保証ではなく、
監視用health等の用途に限定し事前確認する。

本番でCを後日実施する場合: 研究者本人承認→backup完了→UIでTEST学校・クラス、
TEST教師/学生・課題を作成→生成された実IDをprivateテスト台帳へ記録。
名前prefixだけでは隔離・研究除外を保証しない。export/集計が学校/クラス/IDで
テストを確実に除外できない場合、**本番でテストデータを作らない**。
研究同意を試験用に付けるだけでも混入するので、対象exportの実装版を確認する。
cleanupは全件DELETE/seedやFK cascadeに頼らず、登録IDに限定した別承認手順とする。
監査・提出・評価を消して帳尻を合わせない。テスト終了後のアカウント停止は
追加承認されたUI操作とし、研究データ/他利用者に触れない。

初期admin準備は[create-initial-admin.sh](../../scripts/production/create-initial-admin.sh)
と[Bootstrap](../../src/main/java/control/auth/InitialAdminBootstrap.java)を使用。
承認後のみ、SSMの実TTYでpassword非echo入力、固定login ID `admin`、
既存adminの有無確認、作成/失敗の確認を行う。引数/env/historyへpasswordを渡さない。
非TTY拒否/重複拒否/不正password試験は隔離で行う。
現コードはadminに学生/教師の必須password変更導線を適用しないため、
「admin作成後に必須変更画面が出る」を期待結果にしない。
本番adminは既存の管理担当者が使用し、2個目のテストadminを作らない。

### 学生テストケース

自動化欄: HTTP=認証HTTP/DB統合、UI=ブラウザー、DB=隔離MySQL比較、
運用=制御されたCLI、手動=人による確認。資格/実データをテストログへ出さない。

| ID | 対象機能 | 前提 | 操作手順 | 期待結果 | 確認方法 | 優先度 | 自動化 | 本番可否 |
|---|---|---|---|---|---|---|---|---|
| S01 | ログイン/ログアウト | F,A、学生資格 | 正しい/誤った資格でlogin→logout→保護URL | 成功のみsession成立、失敗は明示、logout後再認証 | UI/HTTP、session失効、監査分類 | P1 | HTTP/UI | C |
| S02 | 初回変更/停止 | A、security level 2と停止学生 | 初期login→必須変更→旧資格→停止後旧session | 必須変更、旧資格/停止session拒否。他者変更不可 | UI、資格値なしの状態確認 | P1 | HTTP/UI/DB | C |
| S03 | 同意/撤回 | A、承認文面 | 説明確認→同意/不同意→撤回 | 版付き履歴、通常学習継続、survey制約は状態ルール通り | UI/DB、承認文面version | P1 | HTTP/UI | C |
| S04 | 課題一覧/詳細 | A,D、複数クラス | 一覧→公開課題→未割当ID | 自分の公開/割当内容のみ、他者/未公開漏えいなし | UI/API、DB対応 | P1 | HTTP/UI | C |
| S05 | Python編集/設定 | A,D、編集可能 | 日本語コメント/改行編集、設定変更、再表示 | 編集保持、設定はアカウント別、非対応I/Oの説明整合 | UI・本人別DB | P2 | UI | C |
| S06 | 手動保存/再読込 | A,D | 編集→保存→reload→再login | 保存した全文/改行とmanual_save履歴が一致 | DB source hash、UI | P1 | UI/DB | C |
| S07 | 競合・保存失敗 | F,A,D | 2タブ更新/古い版、通信断を模擬 | 競合/未保存を明示、成功と偽らず既存保存を維持 | UI、status/row版、DB hash | P1 | HTTP/UI/DB | X |
| S08 | 30秒自動ログ | A,D、foreground dirty | 編集後30秒待つ、別変更後次周期、変更なし周期 | dirtyかつ保存中でない時periodic_snapshot。変更なし連続記録を要求しない | 30_000msタイマー/時刻、DB event、UI | P1 | UI/DB | C |
| S09 | 編集/実行/提出ログ | A,D | 保存→実行→提出 | 対象利用者/課題・event/source・時刻の対応、演習ログと混同なし | code_logsと関連ID/内容hash | P1 | DB/UI | C |
| S10 | 正常Python実行 | A,D | `print`/計算/標準入力コード実行 | stdoutと終了状態が一致、UI再実行可能 | UI、execution記録、runner終了 | P1 | HTTP/UI | C |
| S11 | stdout/stderr/対話 | A,D | inputへ回答、構文/実行例外、cancel | 入力待ち/終了・stderrを区別、前実行の結果混入なし | UI、exit/出力・DB対応 | P1 | UI | C |
| S12 | 実行制限 | F,A,D | 無限loop/過大出力/入力・並列超過 | 規定上限でtimeout/拒否、子container清掃、host安定 | runner/brokerログとPID/数 | P1 | HTTP/運用 | X |
| S13 | 自動チェック | A,D、正/誤答ケース | check→全一致/一部不一致 | ケース別入出力/結果、提出操作は別確認、勝手に提出しない | UI、test_results参照 | P1 | UI/DB | C |
| S14 | 提出/二重送信 | A,D | 最終確認→submit、重複要求 | 提出source固定、同操作重複なし、評価要求条件一致 | UI/DB、submission/request ID | P1 | HTTP/UI/DB | C |
| S15 | 再提出/期限 | A,D、期限前/期限後 | 再提出→旧履歴表示、期限状態で操作 | 旧提出保持、割当期限/遅延方針通り、別人ID不可 | UI/DB、状態ルール比較 | P1 | HTTP/UI | C |
| S16 | 提出/評価履歴 | A,D、複数版評価 | 本人の各提出/評価を開く、別学生ID | 各version対応、根拠/ログ一致、他者アクセス拒否 | UI/API、rubric/prompt pin | P1 | HTTP/UI/DB | C |
| S17 | 課題別アンケート | A,D,Q、同意/評価条件 | 下書き保存→reload→回答提出→別評価回答 | 未保存を開始扱いにせず、履歴版対応、本人/同意条件遵守 | UI/DB、survey_response ID | P1 | HTTP/UI/DB | C |
| S18 | LLM正常評価 | F,A,D、承認prompt/rubric | mock評価→処理完了→reload | status/点数/根拠/入力snapshot保存、model/prompt/rubric固定 | DB/UI、匿名化payload fixture | P1 | HTTP/DB/mock | X |
| S19 | LLM異常/再試行 | F,D、mock 429/503/不正JSON/中断 | submit→障害→明示retry | 規定再送/上限、failed等明示、旧評価保持、同じ定義pin | worker/request/DB、secretなしlog | P1 | HTTP/DB/mock | X |
| S20 | 合成実Gemini受入 | A,D、予算/外部送信別承認 | 少数の合成提出→実評価を確認 | 完了/根拠妥当、費用/所要記録、PII送信なし | ownerの採点review、DB/UI | P2 | 部分自動+手動 | C、別API承認 |
| S21 | 標準ルーブリック表示 | A、標準seed済み | 生徒rubric→version/尺度確認 | 承認資料と一致、未登録は明示エラー | UI/DBと正本比較 | P1 | UI/DB | C |

S08の厳密な壁時計30秒以内保証はブラウザー非active時のtimer throttlingと通信遅延に
影響される。前景で未保存変更を用意し、各周期の要求と保存を計測する。background/
offlineでは未保存表示と復帰後の保存を隔離試験し、存在しない常時サーバー30秒収集機能を
合格条件にしない。30秒間隔の記録成功だけで全キー入力の逐次ログを保証しない。

### 教員テストケース

| ID | 対象機能 | 前提 | 操作手順 | 期待結果 | 確認方法 | 優先度 | 自動化 | 本番可否 |
|---|---|---|---|---|---|---|---|---|
| T01 | login/logout/必須変更 | F,A、T1/T2 | login→初期変更/通常変更→logout | teacher session、旧資格/旧session失効、権限なしでも本人管理可 | UI/HTTP/DB状態 | P1 | HTTP/UI | C |
| T02 | 課題作成/編集 | A,D、task権限/標準seed | draft作成→入力ケース編集→保存 | 学校/クラス権限と入力制約、再読込一致、validation明示 | UI/DB、対象課題のみ | P1 | HTTP/UI | C |
| T03 | 公開/予約/非公開状態 | A,D、prompt設定済み | draft閲覧→即時/予約公開→期限/非公開操作 | draftは学生に見えず、承認状態遷移のみ。公開後を無条件draftへ戻さない | worker/DB/UI、課題状態ルール | P1 | HTTP/UI/時刻制御 | C、時刻注入はX |
| T04 | 改訂/コピー/期限 | A,D、開始前後課題 | 改訂/独立copy、期限延長、クラス追加 | 学習後は独立系列、旧提出/評価保持、対象追加冪等 | DB ID/content/割当とUI | P1 | HTTP/UI/DB | C |
| T05 | 公開取消/削除復元 | F,A,D | 許可状態のdelete/restore、学習後禁止操作 | 状態ルール通り、学習記録をcascade消去しない | DB前後/監査 | P1 | HTTP/DB | X |
| T06 | 進捗確認 | A,D、学習/提出/評価状態 | filter/sort/詳細/refresh | 自校・対象学生、最新状態/時刻一致、未提出draftコード非公開 | UI/API、DB参照 | P1 | HTTP/UI | C |
| T07 | 提出一覧/履歴 | A,D、複数提出 | 一覧→詳細→旧提出 | 対象release実装の一覧/履歴/権限、最新版UIとの差を記録 | UI/API/DB | P1 | HTTP/UI | C |
| T08 | 学生コード/演習閲覧 | A,D、保存/提出・演習データ | code表示、実装済みdownload/preview | 認可内の対象版、本人source不変、未提出draft制約遵守 | UI/DB hash、download metadata | P1 | HTTP/UI | C |
| T09 | 評価結果/根拠 | A,D、旧/新評価 | evaluations→履歴/根拠 | 点数/尺度/対象提出とpin一致、保存結果を変更しない | UI/DB、入力snapshot | P1 | HTTP/UI/DB | C |
| T10 | 標準rubric確認 | A、seed済み | prompt画面で標準確認 | 承認version/文言一致、任意編集UIなし | UI/DB/正本 | P1 | UI | C |
| T11 | prompt版管理 | A,D、prompt権限 | 保存/複製→生成→適用、旧版参照 | 新version、競合拒否、適用版/旧評価維持 | UI/DB・mock生成 | P1 | HTTP/UI/mock | C、実AI別承認 |
| T12 | 再評価preview/確定 | F,A,D | preview→取消/再試行→別試験で確定 | 取消は本評価不変、確定のみ新評価履歴、旧版保持 | preview/job/evaluation DB比較 | P1 | HTTP/UI/mock | X |
| T13 | 学生アカウント管理 | A、学校/機能権限 | 一括作成→状態/資格再設定→一覧履歴 | 対象校のみ、初期資格の限定表示、旧session失効 | UI/DB、値を保存しない | P1 | UI/HTTP | C |
| T14 | 演習配信 | A,D、配信権限 | upload/保存→即時/予約→停止/再開 | snapshot版/対象/時刻一致、既存学習履歴維持 | UI/worker/DB | P2 | HTTP/UI | C |
| T15 | CSV/download範囲 | F,A,D、不同意/撤回含む | 実装済み出力操作→台帳ID照合 | 最新同意/範囲・仮名化の契約通り、他校/secret混入なし | 隔離出力内容/監査 | P1 | HTTP/DB | X、実研究export未受入 |
| T16 | 教師survey結果 | 後続release/専用fixture | 結果一覧/集計/詳細/export | 配備後に正式契約と同意条件を確認 | 後続機能計画で別受入 | P2 | 将来HTTP/UI | U、現release未配備 |
| T17 | 任意rubric編集 | 将来合意/実装 | 教師custom登録/更新 | 未実装。現標準共通仕様を勝手に拡張しない | 設計承認後の別計画 | P2 | 未定 | U |

### 管理者テストケース

| ID | 対象機能 | 前提 | 操作手順 | 期待結果 | 確認方法 | 優先度 | 自動化 | 本番可否 |
|---|---|---|---|---|---|---|---|---|
| A01 | 初期管理者CLI | F、users空、実TTY | 非echo入力でbootstrap→再実行/非TTY | admin1件、重複/非TTY拒否、資格非出力 | DB件数、終了status、log review | P1 | 部分自動+手動 | X、本番作成は別承認 |
| A02 | 管理者認証 | A、固定admin | teacher login→admin home→logout | adminのみ管理画面、学生/教師は拒否 | UI/HTTP | P1 | HTTP/UI | C |
| A03 | 教師ユーザー管理 | A、admin、合成教師 | create→stop/unstop→reset | 状態反映・監査、旧資格/session失効、平文の再表示禁止 | UI/DB/HTTP | P1 | HTTP/UI | C |
| A04 | 学校/権限管理 | A、2校/権限教師 | 学校と機能権限編集→本人/他校アクセス | 変更範囲に限定、権限外拒否、security level制約 | UI/DB/HTTP | P1 | HTTP/UI | C |
| A05 | 論理削除/復元制約 | F,A,D | 合成教師/学校の許可操作→既存記録照合 | 規定制約・論理削除、提出/ログ/評価不変 | DB/監査/旧session | P1 | HTTP/DB | X |
| A06 | 監査/資格保護 | F,A | 管理操作→履歴/CSV/ログ確認 | actor/result対応、password/API key/token/秘密鍵なし | 隔離ログ・payload検査 | P1 | HTTP/DB+手動 | X |
| A07 | 一般システム設定 | 将来実装 | 全体設定画面から編集 | 未実装。学校設定やhost設定と混同しない | 別設計が必要 | P2 | 未定 | U |
| A08 | 特権/CSRF拒否 | F,A、一般teacher/student | admin URL/POST、欠落/他session token | 非管理者/偽token拒否、DB変更0 | HTTPとDB hash | P1 | HTTP/DB | X |

### 非機能・運用テストケース

| ID | 対象機能 | 前提 | 操作手順 | 期待結果 | 確認方法 | 優先度 | 自動化 | 本番可否 |
|---|---|---|---|---|---|---|---|---|
| N01 | HTTPS/health/login | 公開DNS/有効TLS | 両host health/login、HTTP redirect、SAN/期限確認 | trust成功、health exact JSON、login200/redirect | curl/TLS、証明書metadata | P1 | HTTP/運用 | R、今回実行なし |
| N02 | Cookie/session | F,A | login前後ID、idle30分、logout、別host | fixation防止、失効、Secure/HttpOnly/host-only/SameSiteの契約通り | HTTP/UI、cookie値は非保存 | P1 | HTTP/UI | C |
| N03 | host/role分離 | F,A | 学生→教師/admin、教師→別校、別host同session | 許可redirectまたは403/404、保護データ0 | HTTP/DB、routing契約 | P1 | HTTP | X |
| N04 | CSRF/IDOR/forwarded headers | F,A,D | 他ID/token/偽Host/forwardedを送る | 権限外変更/開示なし、不正transport拒否 | HTTP/DB比較 | P1 | HTTP/DB | X |
| N05 | Python隔離 | F、専用broker | host/volume/network/root書込を試す合成コード/API | network-none/nonroot/read-only、危険設定拒否、別namespace不操作 | broker policy tests/inspect | P1 | 運用/HTTP | X |
| N06 | 実行枠/cleanup | F,D | 4実行＋5件目、cancel/timeout/runner障害 | 上限、待機/拒否を明示、孤児0、host安定 | runner/broker/children数 | P1 | HTTP/運用 | X |
| N07 | コンテナ/DB稼働 | B、監視対象 | health/ID/volume/portsをread-only照合 | 5healthy、DB非公開、image ID不変 | ps/filtered inspect/SQL設定 | P1 | 運用 | R |
| N08 | 保存integrity | F,A,D | 保存/提出/評価/survey→再接続 | source/ID/refs/JSON一致、FK破損/重複0 | 別connectionのDB hash/件数 | P1 | DB/HTTP | C |
| N09 | restart後保持 | F,D、事前manifest | app/DB/hostを個別restart→照合 | volume/全record保持、sessionは再認証、worker復旧の契約通り | 前後ID/hash、DB/health | P1 | 運用/DB | X |
| N10 | update/rollback | F,D,B、2immutable release | backup→更新→app-only rollback→照合 | old data/rubric/snapshot100%保持、schemaを巻戻さない | journal/manifest/Flyway/DB | P1 | 運用/DB | X |
| N11 | backup/restore/失敗通知 | F,D、専用key/S3 | 作成→故意の失敗→隔離restore | 完全性/復号/通知、失敗時deploy停止、旧DB不変更 | private manifest/DB/通知記録 | P1 | 運用/DB+手動 | X |
| N12 | エラー処理/ログ | F、DB/runner/AI mock障害 | 保存/実行/評価中に障害注入 | エラー明示、成功を偽らない、既存records保護、secretなし | HTTP/DB/log分類 | P1 | HTTP/運用 | X |
| N13 | 同時編集/提出 | F,A,D | 同一/別学生の同時保存・submit | conflict/冪等性、他者source混入0 | HTTP/DB IDs/hash | P1 | HTTP/負荷 | X |
| N14 | 同時アクセス性能 | F、別host、予算 | 段階負荷→300接続/100人要求集中、120分 | 合意p95/待機/エラー/容量条件達成。100並列実行を要求しない | metrics/DB/queue/OOM/CPU credits | P1 | 負荷ツール、要準備 | X |
| N15 | 研究/テスト分離 | F,A,D、同意/撤回/TEST IDs | 対象実装の集計/exportを照合 | TEST除外、最新同意、承認最小列、対応表非提供 | 隔離export/manifest | P1 | HTTP/DB+手動 | X |
| N16 | 標準seed反復/版保護 | F、旧評価/custom rubric | 初回→2回→不一致→新seed版→中断 | 重複0、旧尺度/ID/snapshot不変、不一致fail-closed | seed registry/DB前後 | P1 | DB/運用 | U、新設計実装後にX |

### 実行順・証跡・停止条件

1. 隔離環境でA01/A08・N03〜N06とbackup/restore/更新保持のP1を先行。
2. 承認済み標準定義・prompt・正式同意/設問fixtureを用意し、学生→教師→管理者の
   縦断をGemini mockで実施。teacher未配備機能は明示skipにする。
3. 少数合成実APIは予算・送信範囲の個別承認後。機能/バックアップ/データ分離の
   P1成功後にのみ、本番C項目を対象ID・操作一覧付きで再承認する。
4. 負荷・restart・悪性入力・restore・DB障害は本番で行わず、隔離で受入。
5. 研究開始はbackup/restore/update保持、正式同意・外部AI条件、権限、
   負荷/容量、テストデータ除外が確認されてから別承認とする。

各実行はID/実行日時/release/fixture ID/期待vs実測/結果(PASS/FAIL/SKIP)/
担当/秘密を除いた証拠へのprivate参照を記録。画面やCSVに個人情報/資格が含まれる場合
Gitへ保存しない。DB比較は件数だけでなくID・内容hash・FK・旧評価の尺度/pinを比較する。
異常/秘密露出/対象外ID変更/欠損があれば停止し、restore/cleanup/retryを自動実行しない。
性能検査をskipしたとき「300人利用可」と報告しない。
