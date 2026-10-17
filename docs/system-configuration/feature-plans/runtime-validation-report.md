# Runtime Validation Report

**Generated**: 2026-10-08 / **Latest update**: 2026-10-17 17:41 JST（R-03/R-05再実行、ローカルCI相当確認、利用者受入）

**Target**: `/Users/t.toida/programming-process-evaluator` — 工程20と関連機能（工程14以降の履歴を保持）

## R-03 ローカル確定設定の全量seed再現検証（2026-10-17）

REQ-INITIAL-180/181に対応する保全・隔離再現を実装し、完了した。本番の初期化/同期/deployは行っていない。
[実装契約の境界](../implementation-contract.md#r-03-ローカル全量seedの検証境界2026-10-17)に従い、
[InitialConfigurationSeed](../../../src/main/java/control/dev/InitialConfigurationSeed.java)と
[ローカルrunner](../../../scripts/testing/initial-configuration-local.sh)を追加した。

- 保全元はCompose project `programming-process-evaluator` / service `db`の既存DB。
  exportと直後のverifyはread-only transactionで実行し、既存資格情報・設定・鍵を更新しない。
- 対象25テーブル: users、schools、classrooms、student_profiles、student_class_memberships、
  teacher_school_permissions、teacher_feature_permissions、student_teacher_credentials、
  student_login_sequence、rubrics、rubric_dimensions、rubric_criteria、criterion_levels、
  tasks、task_features、task_test_cases、task_hints、task_class_assignments、
  school_common_prompt_versions、prompt_versions、prompt_fluctuation_items、evaluation_examples、
  surveys、survey_questions、survey_question_options。削除/無効状態も含む設定の内容・ID・参照を保持する。
- 保全内容にはusers 21（active students 12 / teachers 3 / admin 1、他はdeleted）、
  schools 2、classrooms 3、memberships 15、学校権限7、機能権限38、rubric 1/2/6/30、
  tasks 11、割当11、学校共通prompt 1、task prompts 5、揺らぎ12、評価例4、
  surveys 2、questions 12、options 60が含まれる。提出・評価結果・回答・同意履歴・研究識別子は含めない。
- 非公開ファイルはGit管理外の0700ディレクトリ内に0600で新規作成し、既存ファイル/公開権限/リンクを拒否。
  SHA-256で内容破損を検出する。認証hash/暗号化資格情報を含むため内容・hash・値をログやレポートに出さない。
  暗号化鍵はコピー/再生成しない。これは署名された本番deploy資材ではなく、ローカル保全用の機密資材。
- applyはsimulationの`ppe_initial_seed_`接頭辞DBに限定。空DBへの投入はFKを有効にした単一transactionで実施し、
  usersの自己参照、tasksの循環参照、student_profilesのINSERT triggerによる初期状態設定を考慮して、
  最後に全内容と参照を照合してcommitする。一致時はno-op、不一致/欠損/既存業務履歴/並行実行は拒否。
- 旧DATETIMEの保存表現は無変換で再現する。UTCだったと仮定せず、本番の旧日時移行・予約worker起動は別ゲート。
  同意文書本文はseed対象外で、migration由来のV10仮文面を正式承認済みと扱わない。

### 実行手順と結果

1. リポジトリ外に自分専用のディレクトリを作り、mode 0700にする。既存の秘密ファイルを上書きしない。
2. `PPE_INITIAL_SEED_PRIVATE_DIR=/absolute/private-directory sh scripts/testing/initial-configuration-local.sh`
   を実行する。16:57 JST再実行も**exit 0**、reportはローカルの`build/initial-configuration-validation/run-61294`。
3. 内部で`gradle --offline --no-daemon test --tests control.dev.InitialConfigurationSeedTest`を実行:
   **4 passed / 0 failed/errors/skipped**（再実行も同数）。ファイル0600、上書き/公開権限/リンク拒否、
   checksum不一致、対象外テーブル、識別子/行形状違反を確認。
4. `gradle --offline --no-daemon test --tests control.dev.InitialConfigurationSeedDatabaseTest --rerun-tasks`:
   **1 passed / 0 failed/errors/skipped**（再実行も同数）。全量投入、後半option挿入での故障注入→全体rollback、
   再実行no-op、並行lock拒否、既存login履歴の拒否、内容不一致時の状態不変、再接続後の一致を確認。
5. `gradle --offline --no-daemon war`: **BUILD SUCCESSFUL**。
   CLIの`initialConfiguration --args="apply <private-file>"`と`verify`も成功。
   同じファイルを別の新しい空DBへ`flywayMigrate flywayValidate`後にapply/verifyし、
   **2つの独立DBで全25テーブルの内容・ID・参照関係一致**を確認。
6. `sh -n scripts/testing/initial-configuration-local.sh`、`git diff --check`: **exit 0**。
   専用container/networkを削除し、非公開保全ファイルだけを指定した私有ディレクトリへ残す。

## R-05 利用者受入の記録（2026-10-17）

| 項目 | 結果 |
|---|---|
| R-03後の代表合成フロー再検証 | 16:57 JST再実行: `sh scripts/testing/teacher-learning-flow-local.sh` exit 0。JUnit **7 passed / 0 failed/errors/skipped**、Playwright **3 passed / 0 failed**。report: `build/learning-flow-validation/run-61293` |
| 確認した範囲 | 教師設定/公開、合成生徒の提出・評価、survey保存/提出、教師一覧/詳細/CSV、再起動後read-back、375px幅 |
| 全量seedとブラウザーの区別 | R-03は確定ローカル設定を全量DB照合。R-05 runnerは独立した合成fixtureによる代表フローで、確定全量設定を人間が操作したE2Eとは扱わない |
| 利用者への確認 | 16:57 JSTの再実行結果と合成試験の範囲を提示して確認 |
| 利用者の回答 | **承認**: この合成フロー範囲の技術的受入として記録することを明示承認（2026-10-17） |
| 判定 | **限定範囲の技術的利用者受入済み**。確定設定全量の手動E2E、採点品質、Gemini実API、正式同意、本番反映は含まない |
| 別ゲート | 本人の手動E2E、採点品質/実Gemini、正式同意、R-06のレビュー/CI/ECR、R-07本番反映承認 |

受入範囲は上表に限定し、未確認事項を別ゲートとして保持する。
デプロイはR-06完了後、対象・差分・復帰条件を提示して**個別の明示承認を得るまで実行しない**。

<a id="r-06r-07候補・復帰条件スナップショット2026-10-17-1657-jst"></a>
## R-06/R-07の候補・復帰条件スナップショット（2026-10-17 17:41 JST）

これは現時点の準備状況であり、R-06合格・R-07承認・本番反映の記録ではない。

| 項目 | 現時点の確認 |
|---|---|
| 対象環境 | 本番の生徒 `https://student.ppeval.net`、教師 `https://teacher.ppeval.net`。直近の公開基準はproduction record記載のmain `41228ca...` / 固定OCI digest。 |
| 候補ソース | 現在のbranch `code`、HEAD `8d7cf17adbf449b9981682c1298460932d14118c`。これは候補release SHAではなく、作業ツリーは未commit。 |
| 差分 | `git status --short`で118件。追跡済み差分の集計は104 files、1491 insertions / 292 deletions、未追跡15 filesを含み、コンパイル済みclassの削除もある。教師/生徒のServlet・DAO・Entity、日時境界、 seed/HTTP/browser試験、画面資産、仕様/運用文書にまたがるため、差分範囲を確定したレビューが未実施。migration directoryの変更は現時点のstatusで見つかっていないが、candidate SHAによる確定確認は未実施。 |
| R-06不足 | ローカルCI相当テストは下記のとおり通過。GitHub PR/main reviewと必須CI、source SHAとECR固定digestの対応付け、candidate imageを使った本番相当のservice/HTTPS/Runner/保存・再読込/restart/DB保持検証は未実施。従ってローカル検証だけでR-06を完了扱いしない。 |
| 変更レビュー | 2回の範囲限定レビューで確認対象に重大な問題なし。seed/R-05主要コード、時刻処理/build構成、残りのテスト/scripts/UIの一部を確認。運用文書の過去記録全件および追跡済み`bin/main/*.class`削除は未レビューで、全118 pathのレビュー完了ではない。 |
| 復帰の基本条件 | 反映後にhealth/login、認可、保存/再読込、Runner、重要状態遷移の異常が出た場合は、変更を停止し証跡を保全する。DB/Volumeを保持したまま、schema互換性と固定imageを確認したうえで直前releaseへapplication rollbackする。自動down migration、DB初期化/復元、旧V34専用referenceの別schema流用はしない。部分DDLまたはデータ不整合がある場合は即時rollbackを独断で行わず、対象schemaに一致するfresh backup/referenceを確認し、個別判断を得る。 |
| 判定 | **R-06未完了 / R-07未承認**。source SHA、PR/CI、固定image、候補向け復帰参照が確定するまで本番作業へ進まない。 |

R-07開始前には、候補release SHA/image digest、実際のDB/migration差分、停止・失敗時対応、更新用fresh backupの固定Version/hash/KMS/鮮度とschema対応を確定し、上記復帰条件を再提示して個別の明示承認を得る。

### 2026-10-17 17:41 JST ローカルCI相当検証追補

| 検証 | 結果 |
|---|---|
| Gradle full build/test | networkなしの`ppe-tools:local`上で`gradle --offline --no-daemon build --warning-mode all --console plain`を実行。**BUILD SUCCESSFUL**。JUnit 153 XML / 576 tests: **385 passed / 0 failed / 0 errors / 191 skipped**。skipは実DB/HTTP/browser/real API環境を要する非CI opt-in範囲であり、成功へ算入しない |
| CI shell syntax/mock suites | `bash scripts/ci/check-shell-syntax.sh`、`reporting-test.sh`、`runner-gates-test.sh`、`ecr-release-test.sh`: shell **84 files passed**、ECR test **40 negative scenarios**、ECR acquisition **11 rejection scenarios**を含めすべてPASS（mockのみ） |
| Python services | `python3 -B -m unittest discover -s containers/python-runner -p test_runner.py`: **24 passed**。docker-broker同コマンド: **10 passed** |
| Deployment regressions | `bash scripts/ci/run-shell-tests.sh <new-private-temp-directory>`: **20 suites passed / 0 skipped**。既存のlocal CI imageをread-onlyで利用し、tagの上書きなし。AWS/production accessなし |
| 対象外 | GitHub上の必須CI、PR/main review、ECR publish、final-image本番相当受入は未実行。R-07の本番反映は未承認・未実施 |

### GitHub candidate follow-up

- User approval was obtained before starting remote changes. Commit `483de49fdce558366335bece152461209f37aa98` was created on `release/r03-r05-r06-20261017` and pushed to `origin`. It contains 117 paths; the two pre-existing generated `.class` deletions remain unstaged and excluded.
- Push triggered CI run [39039122038](https://github.com/toichan/programming-process-evaluator/actions/runs/39039122038), observed in progress. Do not report it passed until completed.
- No PR exists yet. The integrated browser is signed out, and no GitHub PR-creation operation is available in the current toolset. The pushed branch's GitHub create-PR page is [Create pull request](https://github.com/toichan/programming-process-evaluator/pull/new/release/r03-r05-r06-20261017). The repository template is [`.github/pull_request_template.md`](../../../.github/pull_request_template.md); preserve its headings `概要`, `変更内容`, `補足`. Suggested body: summarize R-03 private full-seed rehearsal and R-05 synthetic-flow acceptance; list exact local commands/results from this report; state that ECR publication and production deployment are not included.
- ECR publication was not started. It requires a separate approval with the exact successful main SHA and image-publication scope after PR/main CI and review; production deployment remains a further separately approved operation.

## 2026-10-17 23:46 JST R-06 full review and correction follow-up

The previous GitHub candidate follow-up is historical. Main CI run
[`39040399054`](https://github.com/toichan/programming-process-evaluator/actions/runs/39040399054)
completed successfully for main SHA `3522282622d22b7ca52d2fc0589061af3df22254`;
both Java build/tests and Shell deployment regressions succeeded.

A complete review of the release change set from prior production main
`41228ca58509ffbce02345d439caf950997c499c` to that merge SHA found one medium
issue: the teacher task edit form formatted existing publication/deadline values
only to minutes, silently discarding seconds. This could alter timestamps on an
otherwise unrelated edit or make valid values in the same minute equal.
`TeacherTaskInput.ClassAssignmentInput` now uses the converted `LocalDateTime`
string representation, retaining nonzero seconds while leaving zero-second
values in the existing minute-only representation. A regression test checks
JST display and UTC round-trip for both timestamps.

The exact main SHA above does not contain this follow-up fix. The regression
test failed before the fix, and after the fix the full offline Gradle build
passed: 153 suites / 577 tests, **386 passed / 0 failed / 0 errors / 191
skipped**. Skip cases are not counted as passes. The fix and this follow-up are
local and uncommitted; a new reviewed PR/main SHA and successful CI are required
before publishing any ECR candidate. ECR publication, EC2 image acquisition,
production preflight/migration/deployment, and production acceptance have not
started. No production resource or database was changed.

## 2026-10-17 11:11 JST R-03〜R-05 合成縦断検証の再検証結果

以下は11:11時点の履歴。R-03全量seedとR-05の最新判定は上記の追補を正本とする。

R-01のローカル確定版棚卸しと本番read-only照合は完了。管理者fingerprintと標準rubric内容は一致し、
アカウント・学校/クラス・権限・survey・prompt/evaluationには差分があった。
本番へのwrite/deploy/restart、実データ取得、Gemini実API要求はしていない。
具体的な集計は[本番R-01照合記録](../production-deployment.md#r-01-初期設定の棚卸しと本番read-only照合2026-10-17)を参照。

隔離検証の最終run `sh scripts/testing/teacher-learning-flow-local.sh` は **exit 0**。
R-03関連では標準rubricの初回/再実行と不一致内容の拒否が成功（1/1）。survey template SQLは設問6件・
選択肢30件を投入し、再実行を`Target survey must not already have questions or responses.`で拒否した。
故障注入によるoption挿入失敗も検出され、transaction rollback後の設問/選択肢は0件だった。
ただし、空DBからローカル確定版の全アカウント・学校/クラス・権限・prompt/evaluation参照を
再現するseed一式は未確立のためR-03は部分完了のまま。

R-04の隔離MySQL/HTTPS/runtime試験はJUnit **7 passed / 0 failed / 0 errors / 0 skipped**:
fixture 1、rubric conflict 1、teacher flow 2、student flow 1、survey flow 1、restart read-back 1。
stale prompt versionのHTTP 409と状態/監査記録の不変、教師設定から合成評価例の保存・読戻し、
生徒の同意・提出・評価・survey、再起動後の読戻しを確認した。
DB保存件数はrubric 1、公開課題1、完了評価1、active survey 1、設問6、選択肢30、
submitted response 1、answer 6。合成providerは3 generation、fluctuations/examples/evaluation各1段階。

R-05のPlaywrightは **3 passed / 0 failed**。生徒の評価表示/再読込、生徒surveyの保存・再読込と375px幅、
教師の回答一覧/詳細/CSV export/再読込を検証した。ブラウザーは外部Bootstrap CDNの取得のためtest時のみ
Docker bridgeにも接続した。アプリ/DB/runtime/providerは引き続きinternal network内で、送信したのは合成データのみ。
host Node.jsは未導入だが、Playwright containerでbrowser tierを実行できた。

```yaml
environment:
  docker: AVAILABLE — disposable MySQL/Tomcat/provider services; cleanup verified
  node: UNAVAILABLE — host node command absent
  playwright: AVAILABLE — cached container; Bootstrap CDN reachable from browser-only bridge
  test_data: SYNTHETIC — no real student/research data
startup: PASS — isolated authenticated HTTPS flow
integration: PASS — 7 passed, 0 failed, 0 errors, 0 skipped; MySQL, HTTP, restart read-back
e2e: PASS — 3 passed, 0 failed; student evaluation/survey and teacher survey
overall: PASS — targeted isolated R-04/R-05 technical acceptance; R-03 full seed and R-05 user acceptance remain open
production: UNCHANGED — no production write/deploy/restart; no real Gemini request
```

初回runner/browserの失敗、原因、修正および再検証は[エラーレポート](../error-report.md)に記録した。
今回の対象テスト成功を、R-03全量seed・R-05の利用者受入・実Gemini採点品質・本番受入の成功とは扱わない。

## 2026-10-17 H-04 時刻契約の読み取り調査

ユーザーが選択したmetadata/source調査を00:05〜00:07 JSTに完了した。
**調査と時刻/デモ履歴の方針合意は完了 / UTC保存・日本時間入出力の実装・移行は未完了**であり、
下記のデプロイPASSを取り消したり、新しい業務E2Eを実行したと扱ったりしない。

- 同日ユーザー確認で[第181版の日時方針](../../function-specification.md#第181版-日時の統一方針)と、デモ履歴は継承不要・確定済み初期設定の保全/再投入検証後に再作成可能とする方針を合意した。本番初期化/更新・資格情報変更は対象/手順提示後の別承認。コード・設定・DB・既存鍵は変更しておらず、新しいtest結果はない。
- 対象はcurrent `41228ca...` / publishedの既存EC2/DB。hostとcontainerはUTC、
  実行中JVMは`Etc/UTC`、JDBCは`serverTimezone=UTC`。
  MySQL systemはUTC、global/sessionはSYSTEM、実app pool3接続も同値、現在offset0。
- hostの同期項目は初回selectorが空出力だったため、取得済み項目の全量再実行はせず
  当該項目だけ補完した。`Timezone=Etc/UTC` / `NTP=yes` / `NTPSynchronized=yes`を確認。
- 画面のtimezoneなし入力、DATETIME/LocalDateTime、直接format、ブラウザー既定zoneの
  表示が混在し、UTC/JST統一の受入条件を満たしたとは判定しない。
  実レコードの日時ずれや利用者端末のzoneは未測定。
- metadata読取exit 0、OS補完読取exit 0、配布mainと調査sourceの一致確認exit 0。
  本番5サービスのID/image/開始時刻/restart/health、config/current/state、DB/Volumeを保持。
  business操作/worker手動実行/新規fixture/API要求/設定変更はない。
- 今回はtests/build/browserを実行していない。既存の公開/期限/通知/配信テストは
  対応コードと過去の成功証跡を参照し、相対日時のテストをcross-timezone保証へ拡大しない。

具体的な値・コード根拠・次の停止条件は
[H-04調査結果](../production-deployment.md#h-04-調査結果2026-10-17-00050007-jst)を正本とする。
秘密値・業務レコード内容は取得/出力していない。

## 2026-10-16 最小デプロイの最終image gate

### 本番更新と受入の最終判定（20:40:36 JST）

**最小デプロイ: PASS / 完了。** 承認済みmain `41228ca58509ffbce02345d439caf950997c499c`
を18:06:18 JSTにpublished確認。V34だけを適用し、既存DB image/container/Volumeを保持した。
更新開始からpublished確認の4分51秒は実停止時間の測定値ではない。
配布元CI/OCI、固定backupの3 S3 Version/size/hash/KMS、時刻・保持情報は
[本番運用記録](../production-deployment.md#2026-10-16-最小デプロイの進捗)を正本とする。

| 実行・検証 | 結果 | Exit |
|---|---|---|
| 既存`deploy-release.sh update`（承認後1回） | current41228ca / published / V34、5healthy、DB/Volume保持 | 0 |
| 最終toolsによるprivate Java fixtureのoffline `javac` | compile成功、ネットワークなし | 0 |
| 本番Runnerの`RuntimeTest.test_submission_check_suppresses_only_input_prompt` | 1 test / 2 subcases、通常prompt維持・提出前のみ抑制、failure/error/skip 0 | 0 |
| `bash run-production-browser.sh active` | 実HTTPS/Chromium、教師認証・admin 403、生徒保存→reload→実行→履歴reload、2 passed / failed 0 / skipped 0（19.6秒） | 0 |
| private `ProductionSmokeFixture.java verify` / `disable` | 所有コード・成功実行各1、既存account集約hash不変、合成2accountだけsuspended/version増分 | 各0 |
| `bash run-production-browser.sh disabled` | 元teacher/student cookieによる旧session失効と正しいpasswordでの再login拒否、2 passed / failed 0 / skipped 0（6.9秒） | 0 |
| `final-production-readback.sh`（修正版） | 固定4image＋旧DB、5healthy/restart 0、V34/failed 0/trust 0、運用config、fresh backupの3固定Version/remote manifest、両health/login、3timer/直近service、重大log0、queue/preview0 | 0 |
| 限定cleanupとread-only再確認 | EC2の2password不在、既存DB secret維持、専用containerなし。ローカル6秘密file不在、予期しない秘密値コピー0 | 最終各0 |

browserは既存認証helperと保存フローを最小編集で再利用した3 spec
（teacher、persistence、disabledを2roleで実行）と専用configを用いた。
trace/screenshotは無効、結果log/JSONはsecretを除外したprivate証拠のみ保持する。
合成2accountは停止済み。監査・演習・実行履歴は合成データとして残した。
Java fixture準備のUTF-8/学校trigger、最終検証器のbootstrap/containerd identity、
cleanup後のファイル名確認の失敗と成功した再検証は
[エラーレポート](../error-report.md)へ保持した。初回の非0を成功へ読み替えていない。

```yaml
environment:
  docker: AVAILABLE — native EC2 Linux/amd64; local Docker 29.8.1 for browser
  node: AVAILABLE — cached Playwright container Node v24.21.0
  playwright: AVAILABLE — v1.64.0 / Chromium, certificate verification enabled
  infra-tier: PRIMARY — actual production MySQL and fixed final images
  browser-tier: PRIMARY — Playwright on the two public HTTPS domains
startup: PASS — approved existing update, five healthy, restart_count: 0
integration: PASS — exit_code: 0, runner_passed: 1, failed: 0, skipped: 0; owned DB readback and existing-account preservation passed
e2e: PASS — exit_code: 0, passed: 4, failed: 0, skipped: 0, flaky: 0; auth, authorization, persistence, execution, suspended-session boundaries
overall: PASS — minimum production deployment, scoped acceptance and synthetic-secret cleanup complete
```

新たなGemini呼出し、既存password/鍵/IAM変更、rollback/DB復元は行っていない。
独立offline鍵保管、本番完全DR、正式な採点品質/手動E2E、全OSは別ゲートとして残す。
次回の定期backup自動実行の結果は、その実行後に通常運用として確認する。

### 修正版の配布・受入と本番前点検（16:38 JST）

PR #61の修正source `8d7cf17...`と統合main
`41228ca58509ffbce02345d439caf950997c499c`のtree一致を確認した。
保護されたmainの必須CI `38862737820` attempt 1は2job成功。
新設したPython unit stepの成功も確認後、ECR公開`38863619749` attempt 1を1回実行した。
固定OCIは`sha256:6d2d1967be3187a8ae81539fbeee3bbe424cc83e88977f895c4472f546d53919`、
source manifestは`9c23717062b6ede6504a3adfcd421ec41dc832687a4a6e0e32dd30bf7850eb65`。
report ZIPのdigest/6 regular files、7imageのmanifest/config/source/platform/必要権限を照合した。

| 検証 | 実測 | Exit |
|---|---|---|
| Python unit | Runner 24＋broker 10件、failure/error/skip 0 | 0 |
| 修正最終imageの実Runner | 認証/サイズ、通常promptと提出前抑制、実行/出力/隔離 3/3件 | 0 |
| 修正最終imageのブラウザー | 既存3件＋保存/再読込/実行/履歴の新規1件、4/4成功（55.3秒） | 0 |
| DB保持 | 保存1/成功実行1、V34/failed 0/trust 0。app/nginx再作成後のSQLとDB ID/image/開始時刻/Volume一致 | 0 |
| EC2への取得 | 固定7imageとREADY、旧5サービス/DB/Volume/current/state/運用config不変、両HTTPS health 200 | 0 |
| native Linux/amd64 | 最終Runner/brokerで`test_production_runtime.py`全5件成功、skip 0、71.960秒。対話/同時実行/timeoutも含む | 再確認0（初回観測wrapperは1） |
| native health/nginx | 元の3秒probeでhealthy。最終nginxの既存TLS読取/config/起動/health成功、native nginx error 0 | 再確認0 |
| 本番Flyway | 最終toolsで`flywayValidate flywayInfo`成功。適用33、pendingはV34だけ。DDL未実行 | 0 |
| バックアップ/復帰準備 | scheduled receiptの固定S3 3 Version/サイズ/SHA256/KMS/鮮度、既存private recovery reference一致。復帰referenceはCURRENT別のV34成功履歴に限定してローカル準備 | 0 |

最初のbrowser起動は旧専用container名の衝突でexit 125。所有labelとexitedを確認して1個だけ除去し、
同じfixtureからUI以降を再実行した。native test自体の成功と、外側SSM wrapperの観測不良を区別する。
前点検のstdin消費による途中exit 0とnative側EXIT trap上書きを修正し、自己test、前点検全量、
native既存証拠/資源cleanup確認を終了statusとmarkerの両方で受け入れた。
native brokerのpolicy拒否warningは12件、Traceback 0。許可済み実行5testは成功し、policyは緩和していない。
詳細な失敗履歴・制限は[エラーレポート](../error-report.md)を参照する。

ローカル/EC2の専用検証container/network/Volume、専用token、ローカル合成Secrets/TLSは清掃済み。
既存の共有開発3container、暗号化鍵、本番の既存5サービスは維持した。

```yaml
environment:
  docker: AVAILABLE — local Docker 29.8.1 plus native EC2 daemon
  node: AVAILABLE — cached Playwright container Node v24.21.0
  playwright: AVAILABLE — v1.64.0 / Chromium
  infra-tier: PRIMARY — real MySQL, seven final ECR images, native runner/broker follow-up
  browser-tier: PRIMARY — Playwright, four multi-step flows
startup: PASS — five local services; native original three-second runner/broker probes
integration: PASS — exit_code: 0, local 3/3 and native 5/5, skipped: 0
e2e: PASS — exit_code: 0, passed: 4, failed: 0, persisted code/execution and DB-preserving recreation
overall: PASS — final-image and preflight scope only; production approval was pending at 16:38
```

16:38時点では本番current a6/V33は未変更だった。その後、fresh backup、V34適用、
合成認証確認と復帰条件の範囲への明示承認を得て、上記の既存updateと本番受入を完了した。
下記acf版の失敗を取り消して再利用したものではない。

### 先行候補acfcd60の失敗とソース修正の履歴

工程20の既存受入と、本番配布する最終imageの受入は分ける。
PR #60のmain `acfcd60...`はpush/main CI `38855211776`成功後、
ECR公開`38856040632` attempt 1に成功した。
report ZIPの固定SHA256と6ファイル集合、OCI digest
`sha256:e92c55c2af5092b4d4b15be4ea01a51ad41afb7a28083c090c820d1c586eeff7`、
7/7imageのregistry digest/config ID/source/platform/必要ファイル権限を照合した。
隔離起動では合成Secrets/TLSと専用MySQLだけを使用し、外部AIのoutboundを外した。

```yaml
environment:
  docker: AVAILABLE — Docker Desktop 29.8.1 / overlay2
  node: AVAILABLE — existing Playwright container Node v24.21.0
  playwright: AVAILABLE — cached v1.64.0 / Chromium, no package installation
  infra-tier: PRIMARY — real MySQL and seven fixed ECR images, amd64 emulation
  browser-tier: PRIMARY — existing three Playwright files reused unchanged plus one new persistence flow
startup: PASS — five healthy after emulation-only broker/runner probe deadline adjustment
integration: FAIL — exit_code: 1, Python execution rejected by broker fixed-command contract
e2e: FAIL — exit_code: 1, passed: 3, failed: 1, saved code reload passed; execution failed
overall: FAIL — acfcd60 candidate must not be deployed
```

broker/runner probeの3秒→15秒と初回GETの15秒→90秒はローカル検証のみの差異。
本番の元の時間閾値、ARM companion Python、エミュレーションのAIO警告はnative確認に残す。
新規testのPOST待機条件誤りを修正した後、最終image内でRunnerの新wrapperとbrokerの旧allowlist
の不一致を再現した。詳細な失敗・診断・再検証は[エラーレポート](../error-report.md)を正本とする。

修正sourceの検証:

- `docker run --rm --network none --mount type=bind,src=$PWD/containers,dst=/workspace/containers,readonly python:3.12-alpine sh -ec 'python3 -B -m unittest discover -s /workspace/containers/python-runner -p test_runner.py; python3 -B -m unittest discover -s /workspace/containers/docker-broker -p test_broker.py'`: exit 0、24+10件成功、skip 0。修正前はbroker 10件中2 errorsで再現。
- 専用Runner内の`python3 -B /dev/stdin RuntimeTest.test_authentication_and_size_limits RuntimeTest.test_submission_check_suppresses_only_input_prompt RuntimeTest.test_normal_error_output_and_filesystem_isolation < containers/python-runner/test_production_runtime.py`: exit 0、3/3成功。ローカル修正broker＋公開済みRunnerの実通信であり、モックではない。
- 既存`ppe-ci-shell:local`で`bash scripts/production/tests/deployment-recovery-test.sh`: exit 0、60 assertion groups。migration後のnginx前後の失敗とold→old復帰を追加。状態機械のmock回帰であり実本番rollbackではない。

この修正直後には新SHAの統合CI・再公開・固定image受入、本番Flyway validate/info、
fresh backupと更新直前承認が残っていたが、その後の完了結果は本節冒頭を参照する。
機能ソースの修正でDB/画面/状態遷移や隔離制限を変更しない。

## 2026-10-16 工程20の最終受入

**P20-01〜P20-10: PASS / 完了。** [受入条件と全Servlet対応表](./phase-20-cross-feature-validation.md)を正本とする。
後段の08時台の`overall: FAIL`は、当時の実行群に候補Gemini診断FAILを含む履歴であり、
その失敗自体は取り消さない。今回の判定は工程20の元の受入条件に限定する。

### Smoke Test Verdict

```yaml
build_command: "gradle --offline --no-daemon clean build --warning-mode all --console plain"
build_working_directory: "/workspace (repository-root source/configuration copy in isolated tools container)"
covers_all_modules: yes
returncode: 0
install_command: n/a
install_returncode: n/a
install_reason: "Single Gradle Java WAR project; existing offline cache, no dependency changes"
environment: "JDK 21 / Gradle 8.10.2 / real isolated MySQL"
junit: {suites: 147, tests: 561, passed: 373, failed: 0, errors: 0, skipped: 188}
```

実行selectorやtest除外でbuildを縮小していない。最終実行は`PPE_RESTORE_DB_TEST=true`、
`DB_NAME=ppe`、専用復元hostを使用し、課題/演習DB等の他opt-inは標準の無効状態。
373 passedには復元DBの1件を含む。最終buildの独立exit fileは0。
前のbuild成功後に追加したFlyway commandが失敗した中間runとは区別する。
Java sourceの最終変更はこのbuildより前で、以後は記録・文書のみ。

### Test Results

| Command / step | Exit | Passed / failed / skipped | 実測と証跡 |
| --- | --- | --- | --- |
| 上記のroot全体clean build | 0 | 373 / 0 / 188、error 0 | WAR成功。`definitive-final-build.log` / `full-build-exit-code.txt` / `verified-junit/` |
| `gradle --offline --no-daemon test --tests control.teacher.TeacherTaskDatabaseTest.periodicSnapshotPersistsAndAStaleEditorVersionCannotOverwriteIt --tests control.teacher.TeacherTaskDatabaseTest.duplicateSubmissionIsIdempotentAndResubmissionPersistsTheNextRevision --console plain` | 0 | 2 / 0 / 0 | 新規空schema→V1〜V34、実runner health200。`editor-opt-in-junit/` |
| `gradle --offline --no-daemon test --tests control.student.StudentExerciseSchemaTest --console plain` | 0 | 1 / 0 / 0 | 別の新規空schema、legacy文脈/参照/version。`exercise-schema-opt-in-junit/` |
| `gradle --offline --no-daemon test --tests control.operations.BackupRestoreDatabaseTest --console plain`（V33/V34後各1回） | 各0 | 各1 / 0 / 0 | 実Control/DAOの進捗一覧/詳細、全表fingerprint不変 |
| `gradle --offline --no-daemon flywayMigrate flywayValidate --console plain`（隔離復元DBのみ） | 0 | N/A（migration task） | V33→V34、67表、業務件数保持、成功履歴34/失敗0、trust0 |
| 既存最終UI受入（下段の5 runner） | 各0 | browser計8 / 0 / 0 | review3、exercise3、prompt1、distribution1。今回重複再実行せず既存証跡へ対応付け |
| `/usr/bin/ruby <session>/oct16-phase20-closeout/notification-drill.rb` | 0 | N/A（実配送） | CloudWatch実評価action成功、既存SNS Delivered1/Failed0、一時alarm削除、本番alarm OK |
| `/usr/bin/ruby <session>/oct16-phase20-closeout/cleanup.rb` | 0 | N/A（後処理） | 専用資源残存0、合成secret削除、共有4サービス不変、両login200 |

証跡の基点はsessionの`files/oct16-phase20-closeout/`。
共通認証テストは29 Servlet / 71 route / 保護66 routeのfilter GET/POST境界を検証し、
各業務の学校/所有/状態/競合は既存DB/HTTP/browser証跡へ対応付けた。
全action×parameter×roleを実HTTPで総当たりしたとはしない。
188 skipの個別照合はXML timestampを用い、171件に非skip成功証跡、1件は不採用2.5 ProのFAIL、
16件は成功XML未発見（任意API7、retained browser fixture補助9）。
これらを今回の373成功へ加算したり、skipを網羅済みと読み替えたりしない。

### Environment and final scoped verdict

```text
infra-tier: PRIMARY — real disposable MySQL; root Gradle WAR build; no H2/infra fallback
browser-tier: PRIMARY — official Playwright 1.64.0 image / Chromium, authenticated application
startup: PASS — existing HTTP/HTTPS acceptance; final shared teacher/student login each HTTP 200
integration: PASS — final 373 passed / 188 skipped / 0 failure / 0 error; focused results above
e2e: PASS — eight final local UI cases plus P20-required prior journeys mapped to evidence
operations-addendum: PASS — isolated populated restore, application Control/DAO readback, SNS delivery
cleanup: PASS — 2026-10-16 12:38:37 JST; owned resources removed, shared services unchanged
overall: PASS — P20-01..P20-10 scope only
candidate-diagnostic: FAIL — excluded Gemini 2.5 Pro remains HTTP 404 / exit 1; no retry or model change
```

中間の誤role期待、migration user 1045、V34 pending、runner Health属性probeの失敗は
[エラーレポート](../error-report.md#2026-10-16-12時台-jst-復旧検証の差分解消と最終回帰)に残し、最終成功と区別する。
復元先Tomcat/browser、本番切替/完全DR/RTO、独立offline鍵保管、人間のメール開封、
正式採点品質/実運用全体E2E、全OS/native dialog、UTC/JST全体契約、ECR release/hardeningは別ゲート。
復元・通知の正確な承認/対象/限界は[運用受入記録](../production-deployment.md#2026-10-16-追加承認による復元通知試験)を参照する。

## 2026-10-16 ローカル課題解決の再受入

本節は08時台と09:36 JSTまでの履歴。最新判定は冒頭の工程20最終受入を参照する。

| Command / step | Exit code | Passed / failed / skipped | Scope |
|---|---:|---|---|
| 隔離Gradle `gradle --offline --no-daemon clean build --warning-mode all --console plain` | 0 | 371 / 0 / 188（146 suites、559 tests） | JDK 21 / Gradle 8.10.2、WAR成功 |
| `sh scripts/testing/teacher-submission-review-local.sh acceptance` | 0 | JUnit 26 / 0 / 2、HTTP 1 / 0 / 0、browser 3 / 0 / 0 | 進捗/提出/評価の学校境界、保存コード、評価詳細の非表示項目/3フィルター |
| `sh scripts/testing/teacher-survey-review-local.sh integration` | 0 | JUnit 7 / 0 / 1、HTTP 1 / 0 / 0 | review後のsurvey seed追加、再実行、同意/学校/出力/監査境界 |
| `sh scripts/testing/student-exercise-layout-local.sh` | 0 | fixture 1 / 0 / 0、browser 3 / 0 / 0 | 390px/320px行配置、未保存logout取消/Escape/二段階取消、focus・再操作、重複submit、確定logoutと失効 |
| `sh scripts/testing/teacher-prompt-generation-local.sh` | 0 | fixture 1 / 0 / 0、browser 1 / 0 / 0 | 専用TLS、合成provider2要求、生成polling、離脱/再訪、完全なログ/JSON、保存後DB読戻し |
| `sh scripts/testing/teacher-distribution-ui-local.sh` | 0 | fixture 1 / 0 / 0、browser 1 / 0 / 0 | 履歴から開く三角/回転/展開・折畳、1280px/390px、peer更新・owner読戻し・他校拒否 |
| `bash scripts/production/tests/nginx-startup-test.sh` | 0 | 7 PASS checks | source config 0600から構築しimage内0644を非rootで読取、TLS・reload・再作成。本番digestではない |
| 承認済みGemini限定診断（下記） | 1 | 3 / 1 / 0 | 診断helper2単体＋モデル一覧GET成功、2.5 Pro要求は404 |

最終browser結果は8論理ケースで、中間失敗・同じcaseの再実行を加算していない。標準buildの188 skip、focused runのgated runtime skip、過去の個別opt-in成功を混同しない。

### Gemini診断の再現可能な選択条件

`GEMINI_API_DIAGNOSTIC_TEST=true`、`GEMINI_API_SMOKE_TEST=true`、`GEMINI_API_OPERATION_CANDIDATE_TEST=true`と設定済みキーをDockerの非表示envで渡し、次のGradle commandを1回実行した。実生徒データは使用せず、候補生成要求は1件、自動retryなし。キーは記録しない。

```sh
gradle --offline --no-daemon test \
  --tests control.evaluation.GeminiApiDiagnosticSupportTest \
  --tests control.evaluation.GeminiOperationalCandidateTest.readsAvailableModelNamesWithoutAGenerationRequest \
  --tests control.evaluation.GeminiOperationalCandidateTest.validatesProCandidateWithOneStructuredRequestAndReadback \
  --console plain
```

Result: exit 1、4 tests、3 passed / 1 failed。`POST https://generativelanguage.googleapis.com/v1beta/interactions`、`gemini-2.5-pro`、`store=false`、HTTP 404、provider code `not_found`。providerは「This model models/gemini-2.5-pro is no longer available to new users. Please update your code to use models/gemini-3.1-pro-preview for the latest features and improvements.」と返した。request ID / Retry-Afterは取得できず、捏造しない。モデル一覧には名前があるが利用可能性とは同義ではない。既定/選択肢/fallbackは変更していない。

停止済みcontainerへ`exec`せず`docker cp`でJUnit/診断JSONを回収した。証跡は`build/error-resolution-validation/gemini/`。過去のDB smokeのretry attempt数は引き続き不明で、補完だけの追加APIは実行しない。

### Environment, evidence and verdict

```text
environment:
  docker: AVAILABLE — existing functional daemon, dedicated disposable MySQL/Tomcat/proxy containers
  node: AVAILABLE in official Playwright 1.64.0 image; host Node remains absent
  playwright: AVAILABLE — bundled Chromium executed all eight final browser cases
  infra-tier: PRIMARY(Docker-based) — real isolated MySQL; no infra fallback
  browser-tier: PRIMARY(Playwright) — authenticated production implementation, synthetic fixtures
startup: PASS — exit_code: 0, HTTPS prompt login and HTTP fixture logins responded before browser execution
integration: PASS for local app — exit_code: 0, full build passed: 371, failed: 0, skipped: 188; separate focused DB/HTTP counts above
e2e: PASS — exit_code: 0 for each final runner, passed: 8, failed: 0, Chromium; other OS/native dialogs unverified
overall: FAIL — the separately requested Gemini candidate diagnostic still exits 1; local implementation acceptance is green but production/full-matrix/quality sign-off remains pending
```

Evidence: final build `build/error-resolution-validation/final/`; review `build/teacher-submission-review-validation/acceptance/run-77628/`; survey `build/teacher-survey-review-validation/integration/`; exercise `build/student-exercise-layout-validation/`; prompt `build/teacher-prompt-generation-validation/run-77003/`（DBは`configured/completed/completed/active/2`、合成provider2要求）; distribution `build/teacher-distribution-ui-validation/run-76997/`。

08:25 JSTの別ゲート追補: ユーザーは本番のrelease/digest・backup receipt/S3 Version/監視値に限定した読み取り調査を承認した。ただし`aws sts get-caller-identity --profile ppe-deployer --region ap-northeast-1`はSSO token失効でexit 255。本番の実測に進めておらず、再認証待ち。ローカル受入の成功とは別の阻害要因であり、本番未確認を解除しない。詳細は[エラーレポート](../error-report.md#2026-10-16-0825-jst-本番の読み取り専用調査を承認)を参照。

### 08:38〜08:47 JST 本番読取の追補（認証待ちは解消）

| Command / observation | Exit code | Result / limitation |
|---|---:|---|
| `aws sts get-caller-identity --profile ppe-deployer --region ap-northeast-1`、対象EC2/SSM情報 | 0 | 承認先account/instance、running/Onlineを確認 |
| 同profileの`cloudwatch describe-alarms` | 254 | AccessDenied、現在のalarmは未確認 |
| 同profileの`cloudwatch get-metric-statistics --namespace PPE/Backup --metric-name FreshBackup --dimensions Name=Project,Value=ppe-production`（直近24h、period3600） | 254 | AccessDenied、保存metric値は未確認 |
| SSM `send-command` | API拒否 | command IDなし。外側wrapperの末尾cat exit 0はAPI成功ではない |
| 同profileのinteractive SSM metadata probe初回 | 1 | 観測結果は回収したが、PTY wrapperの終了code捕捉がtimeout |
| EXIT trap補正後の同profile interactive SSM backup probe | 0 | current/receipt hash不変、最新backupの3固定S3 Version/local暗号化hash/size/SSE-KMS照合成功、5サービスhealthy、nginx0644の非root読取成功 |
| 両domain `curl --fail --max-time 15 https://{student,teacher}.ppeval.net/health` | 各0 | 各HTTP 200、DB/業務flowの試験ではない |

本番currentは`a6e90edf31c5369ea71c2e10efc78d37461423d6`、10月13日更新済みのreleaseだった。今回は変更していない。scheduled backupは10月10〜16日JSTの7開始/7完了、最新02:03:01〜02:03:15 JST、receipt02:03:04 JST、freshness直近08:00:13〜08:00:19 JST。CloudWatch独立readback・業務DB復元・鍵custody・将来のECR release gateは残る。候補Gemini診断の既存FAILも解除しない。

再現payloadと出力はsession `files/oct16-error-resolution/production-readonly-{metadata,backup}.{sh,log}`、`production-readonly-session.exp`に保全した。固定Version/サイズの正本は[本番調査記録](../production-deployment.md#2026-10-16-本番読み取り調査)。実行は`AWS_PAGER='' AWS_MAX_ATTEMPTS=1 /usr/bin/expect <session>/production-readonly-session.exp <session>/production-readonly-backup.sh <session>/production-readonly-backup.log`、exit 0。新しいアプリtestやbuildの再実行ではなく、承認済みの運用メタデータ受入である。

### 09:36 JST 独立した保存監視値の受入

ユーザーが`ppe-infra-admin`による読取を別承認。同一account/東京をSTSで確認後、対象alarmの`describe-alarms`、FreshBackup直近24hの`get-metric-statistics`（period3600、Minimum/Maximum/SampleCount）、BackupSuccess直近7dの同API（period86400、Sum/SampleCount）を各1回実行、全てexit 0。alarmはOK/ActionsEnabled=true、承認済み評価条件・SNS action ARNと一致。FreshBackupは24連続hourly区間の全てでmin=max=1/sample1、BackupSuccessは7区間で合計13成功サンプル。ローカルJSON条件/連続性検査もexit 0。

取得JSONはsession `files/oct16-error-resolution/production-cloudwatch-{alarm,freshness,success,summary}.json`。集計bucket時刻は実行時刻ではなく、13サンプルは13回のscheduled実行という意味でもない。独立監視readbackの未確認は解除するが、以前のAPI拒否/候補Gemini診断FAILは履歴として保持し、alarm起点の実配送・業務DB復旧/鍵custody・工程20全量sign-offは未完了。設定変更/通知試験は行っていない。

中間のテスト操作・TLS proxy構成ミスと修正履歴は[エラーレポート](../error-report.md#2026-10-16-0736-jst-未解決課題の受入テスト初回実行)に保持した。8 browser casesは既存Playwright資産を再利用/拡張し、3 specを追加したもので、新しいテストframeworkは導入していない。テストの座標測定は同一DOM評価で行い、1px基準を緩めていない。

## 2026-10-08 Summary（工程14の履歴）

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

**Generated**: 2026-10-16 JST (P20-03/P20-06/P20-09 follow-ups)
**Target**: `/Users/t.toida/programming-process-evaluator` — 工程20「機能横断のローカル検証」

### Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Latest isolated P20-03 exercise-review Tomcat runtime was served on port 18090 during authenticated browser acceptance; migration and WAR build completed |
| Focused MySQL integration | PASS | 0 | Previous focused suites plus P20-04 fixture/revision tests; latest authenticated `TeacherExerciseRuntimeTest`: 1 test passed, 0 skipped/failures/errors |
| Latest full Gradle clean build | PASS | 0 | 2026-10-14, isolated `gradle:8.10.2-jdk21` container; 144 suites, 552 tests, 369 passed, 0 failures, 0 errors, 183 skipped; Playwright spec is validated separately |
| Browser E2E | PASS (scope-limited) | 0 | Playwright 1.64.0/Chromium; 29 test executions passed across 23 fixture-run invocations, including two simultaneous editor-save races and teacher review ownership acceptance |

**Overall**: NEEDS_SIGNOFF — the final executed checks passed, including immediate/scheduled publication journeys, N166 deny navigation, teacher review/export pages, P20-03 authorization and live revocation of progress/review/survey-result permissions, P20-06 submit/resubmit, admin teacher-account lifecycle and stale-version rejection, and the latest survey/exercise-code school-ownership browser slices. Other opt-in cases and the full cross-feature journey/role-boundary matrix remain unverified.

### Environment and verdict

```text
environment:
  docker: AVAILABLE — `docker info` succeeded; final build ran in a disposable Gradle container with the workspace mounted read-only
  node: UNAVAILABLE on host PATH; AVAILABLE in official Playwright Docker image `mcr.microsoft.com/playwright:v1.64.0-noble`
  playwright: AVAILABLE — `@playwright/test` 1.64.0 installed in an ephemeral container from the e2e lockfile; bundled Chromium launched successfully
  infra-tier: PRIMARY(Docker-based) — dedicated MySQL, app, and browser runner; P20-03, P20-04, P20-06, and P20-09 schemas/projects were isolated and cleaned after verification
  browser-tier: PRIMARY(Playwright) — twenty-nine test executions passed across twenty-three separately provisioned fixture-run invocations; not a single combined run
startup: PASS — latest isolated exercise-review Tomcat runtime served the owner/other-school teacher browser acceptance at port 18090; Gradle/WAR startup path succeeded.
integration: PASS — exit_code: 0 for `sh scripts/testing/teacher-exercise-review-local.sh browser`; authenticated `TeacherExerciseRuntimeTest` passed: 1, failed: 0, skipped: 0; scope: dedicated MySQL and Tomcat, exercise-code ownership/access-denial boundaries and restoration of the owner fixture after permission revocation.
e2e: PASS (scope-limited) — exit_code: 0 for the latest P20-03 exercise-code school-ownership browser run; passed: 1, failed: 0; twenty-nine cumulative Playwright test executions across twenty-three fixture-run invocations cover authentication, 30-second autosave/read-back, sequential stale-save HTTP 409/read-back, overlapping requests from two editor contexts with exactly one HTTP 200 and one HTTP 409, winner persistence/read-back in both contexts, publication/notification paths, initial submission/resubmission, admin teacher-account creation/lifecycle, stale-version HTTP 400 with unchanged account version and failed audit, representative portal-route permission/denial boundaries for student, teacher, and admin sessions, revision notification, submission/evaluation/survey/exercise-code school scope with empty/out-of-scope exports; gaps: remaining cross-feature journey/role-boundary matrix, non-Chromium/OS validation. The latest concurrency test passed twice after its isolated runtime added the required Python runner configuration.
build: PASS — exit_code: 0; Gradle 8.10.2/JDK 21, 144 suites, 552 tests, 369 passed, 183 skipped, 0 failures/errors; latest full build evidence is recorded in the P20-05 acceptance section below (Playwright spec is validated separately). Existing Gradle `WarPluginConvention` deprecation warning remains.
overall: NEEDS_SIGNOFF — the latest P20-03 exercise-code ownership slice passed, but other test-plan opt-in cases and the full journey/browser matrix remain unverified; do not treat skipped or unverified cases as passed
```

### Test evidence

- P20-03 survey permission HTTP command: `sh scripts/testing/teacher-survey-review-local.sh integration` — exit 0; dedicated MySQL migration, WAR build, and authenticated `TeacherSurveyRuntimeTest` succeeded (1 passed, 0 skipped/failures/errors). After revoking `survey-results` in the authenticated session, page/list/detail/CSV/TXT routes returned HTTP 403 without synthetic response/student identifiers; `/teacher/submissions` remained HTTP 200 under its separate permission. MySQL emitted the known nonfatal SQL State `42000` / Error Code `1305` warning while dropping a nonexistent routine; migration passed. The runner removed its dedicated resources.

- P20-06 simultaneous editor-save race: `sh scripts/testing/student-editor-concurrency-local.sh` — two final invocations each exited 0; each built the WAR after V1–V34 migration/validation and ran the retained browser fixture JUnit 1 passed, 0 failed/errors/skipped, then Playwright 1 passed, 0 failed. Two independent browser contexts held the same draft timestamp. A Playwright route barrier released both manual-save POSTs only after both had arrived; measured request intervals overlapped. Exactly one request returned HTTP 200 with `status=saved`, the other HTTP 409 with `errorCode=draft_conflict`; both contexts reloaded the winner's code. Disposable MySQL/Tomcat/Python runner/Gradle/browser containers and network were removed and port 18099 freed after each run. The initial run omitted the configured Python runner and failed on editor GET with `PYTHON_RUNNER_URL must be configured`; adding the standard isolated runner fixed it. See [error report](../error-report.md#工程20-p20-06-同時保存競合browser受入runner構成ミスと修正2026-10-15).
- P20-03 representative role-boundary browser command: `sh scripts/testing/teacher-account-revocation-local.sh` — the latest run exited 0; V1–V34 migration/validation and WAR packaging succeeded; retained admin fixture JUnit 1 passed, 0 failed/errors/skipped; Playwright 1 passed, 0 failed. The admin-created synthetic teacher authenticated after required password change, reached `/teacher/task` (HTTP 200), then the same session was placed on `student.localhost` and `GET /student/home` returned HTTP 403. Admin-session checks confirmed `GET /student/home` and, in the latest run, `GET /teacher/task` on their respective portal hosts both returned HTTP 403; `/admin/teachers` remained HTTP 200 and the existing teacher lifecycle flow completed. This confirms the tested role gates, not every endpoint. The latest runner removed its disposable resources. MySQL emitted the known nonfatal SQL State `42000` / Error Code `1305` for dropping a nonexistent routine; migration and validation passed.
- P20-03 progress/review permission HTTP command: `sh scripts/testing/teacher-submission-review-local.sh integration` — latest run exit 0; dedicated MySQL migration and fixture creation succeeded, WAR built, and authenticated `TeacherReviewRuntimeTest` passed 1/1 with 0 skipped/failures/errors. The permitted teacher received HTTP 200 for progress page/detail/download. A teacher with `task-progress` enabled but access only to a different school received an empty progress page and HTTP 404 for direct detail/download. Revoking the owner's `task-progress` permission returned HTTP 403 for page/detail/download; restoring it and revoking school access yielded an empty HTTP 200 page and HTTP 404 for detail/download. Revoking `evaluation-review` returned HTTP 403 for evaluation page, list, detail, file, logs, CSV, ZIP, and logs-ZIP without student identity/submitted code; `/teacher/submissions` remained HTTP 200. Revoking `submission-review` returned HTTP 403 for submission page, list, detail, file, CSV, ZIP, and preview POST; response bodies contained neither student identity nor code, and preview did not increase `code_logs` or `code_executions`. Test report: 1 passed, 0 skipped/failures/errors. An initial run failed because the test assumed the fixture's latest code was a fixed submitted string; the contract gives precedence to the latest saved draft. The assertion now compares against the authorized detail response. Dedicated containers/network were removed. See [error report](../error-report.md).
- Final clean build command (2026-10-14 JST): `docker run --rm --network bridge --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --workdir /home/gradle/project -e DB_HOST=127.0.0.1 -e DB_PORT=1 -e DB_NAME=ppe_ci_no_database -e DB_USER=synthetic_ci -e DB_PASSWORD=synthetic-ci-not-a-real-secret -e PPE_ENV=simulation -e AWS_EC2_METADATA_DISABLED=true -e AWS_SHARED_CREDENTIALS_FILE=/dev/null -e AWS_CONFIG_FILE=/dev/null gradle:8.10.2-jdk21 bash -lc 'mkdir -p /home/gradle/project/docs /home/gradle/project/scripts/ci && cp -R /source/src /home/gradle/project/ && cp /source/build.gradle /source/settings.gradle /home/gradle/project/ && cp -R /source/docs/rubric /home/gradle/project/docs/ && cp /source/scripts/ci/summarize-junit.sh /home/gradle/project/scripts/ci/ && gradle --no-daemon --console=plain clean build --warning-mode all && bash /home/gradle/project/scripts/ci/summarize-junit.sh /home/gradle/project/build/test-results/test'` — exit 0, `BUILD SUCCESSFUL`; JUnit XML summary: 144 suites, 551 tests, 368 passed, 0 failures, 0 errors, 183 skipped. Source was mounted read-only; clean ran only in the disposable container copy, preserving the existing host `build/` outputs. WAR produced successfully. Skip cases were not counted as passed.
- P20-05 teacher review browser command: `npm --prefix e2e run test:phase20 -- phase20/expired-deny-notification.spec.js phase20/teacher-review-mobile.spec.js` — exit 0, 3 passed, 0 failed. The teacher review spec checks all four screens, CSV/TXT downloads, survey export identity masking, and no document-level horizontal overflow at 390px. Associated isolated runtime HTTP and MySQL evidence checks exact export/list correspondence, authorization boundaries, and persistence; see [phase-20 cross-feature validation](./phase-20-cross-feature-validation.md).
- P20-03 student-account management opt-in command: `docker compose -p ppe-p20-student-accounts-20261014 run --rm --no-deps -e STUDENT_ACCOUNT_DB_TEST=true --entrypoint gradle app test --tests control.teacher.StudentAccountManagementDatabaseTest --no-daemon --console=plain --warning-mode all` with dedicated runtime-only synthetic DB credentials and a valid 256-bit Base64 credential key — exit 0; 2 tests, 2 passed, 0 skipped/failures/errors. Fresh DB migrate/validate exited 0 at V34; DB read-back confirmed all test fixture tables empty and `log_bin_trust_function_creators=0`; dedicated container/volume/network removal verified. The first run failed because the synthetic encryption key was not Base64; this environment error and successful retry are recorded in [error report](../error-report.md).
- Latest full-suite command, including the P20-09 admin browser fixture: `docker compose -p ppe-p20-final-20261014 run --rm --no-deps --entrypoint gradle app --no-daemon --console=plain clean build --warning-mode all` (dedicated synthetic Compose environment) — exit 0; `BUILD SUCCESSFUL`; JUnit XML totals: 144 suites, 550 tests, 368 passed, 0 failures, 0 errors, 182 skipped. The skipped opt-in cases are not counted as passed. The existing Gradle `WarPluginConvention` deprecation warning remains.
- P20-03 teacher self-account opt-in command: `docker compose -p ppe-p20-self-account-20261014 run --rm --no-deps -e TEACHER_SELF_DB_TEST=true --entrypoint gradle app test --tests control.teacher.TeacherSelfAccountDatabaseTest --no-daemon --console=plain --warning-mode all` with dedicated synthetic DB environment values supplied on the Compose invocation — exit 0; 4 tests, 4 passed, 0 skipped/failures/errors. Fresh database migration/validation exited 0 at V34; post-test DB read-back confirmed cleanup and `log_bin_trust_function_creators=0`. Initial setup/verification command errors and their correction are recorded in [error report](../error-report.md#工程20-p20-03-教师セルフアカウントdb受入の初回設定2026-10-14-08420902-jst).
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
- 2026-10-14 P20-06 completion: fresh Compose project `ppe-p20-editor-submit-20261014`, isolated schema `ppe_teacher_task_test_p20_editor_20261014`, app port 18094. Empty-DB `flywayMigrate flywayValidate` exited 0 at V34; `log_bin_trust_function_creators` was set to 1 only during migration and restored to 0 before integration/browser tests. `duplicateSubmissionIsIdempotentAndResubmissionPersistsTheNextRevision` exited 0 (1 passed, 0 failed/errors/skipped); `createsRetainedEditorFixtureForPhase20BrowserAcceptance` exited 0 (1 passed). The first regression run exited 1 during test cleanup because V34's `school_common_prompt_versions` FK was not included in fixture cleanup; test teardown now deletes only rows for the synthetic fixture schools before deleting those schools. The same regression passed on a freshly recreated isolated V34 schema.
- Browser test source: `e2e/phase20/editor-submit-resubmission.spec.js`. Runner: official `mcr.microsoft.com/playwright:v1.64.0-noble` image; host `node` was not available, so `npm ci` used the checked-in e2e lockfile in a temporary Docker volume (exit 0, 3 packages added). The browser runner was connected to the isolated app; a secure `student.localhost` origin and a runtime-only Chromium host-resolver mapping were used.
- Successful invocation: `docker exec` supplied the runtime-only synthetic login/password and dedicated app-IP resolver setting as environment variables, then ran `./node_modules/.bin/playwright test --config=playwright.config.js phase20/editor-submit-resubmission.spec.js` in the temporary runner — exit 0, 1 passed. The UI path verified login rejection/success, 1/1 pre-submit check, evaluation view for revision 1, start-resubmission action, revision 2 submission, and revision 2 evaluation view. MySQL read-back confirmed revisions 1 and 2 are both `accepted` and contain their respective synthetic code.
- Test-runner diagnostics and retries: attempt 1 exited 1 because `app` is a Chromium HSTS-preloaded hostname and the browser upgraded its HTTP route; attempt 2 used an isolated-only hostname but Chromium could not resolve Web Crypto `crypto.randomUUID()` because the origin was not secure; attempt 3 reached the editor but the runner's internal-only network blocked the CodeMirror CDN. The final test uses a `.localhost` secure origin with an explicit container-IP resolver mapping and CDN egress; the app and DB stayed on the dedicated project. These were corrected test-environment failures, not accepted product defects.
- Safety boundary: before any student submit was sent, the app was stopped and recreated with `GEMINI_API_KEY` empty; subsequent checks confirmed it remained empty. Both synthetic evaluation requests therefore ended `failed` due to absent provider credentials; evaluation completion was not part of this E2E acceptance. No Gemini request, actual student data, or production URL was used.
- After evidence/read-back, the dedicated app, DB, Python runner, browser container, project networks, and project volumes were removed. The shared app/DB on port 8080 were not restarted or modified by this P20-06 run.
- 2026-10-14 P20-09 admin-management acceptance: new isolated project `ppe-p20-admin-management-20261014`, DB `ppe_teacher_test_p20_admin_20261014`, app port 18095. Fresh migration and validation exited 0 at V34; MySQL `log_bin_trust_function_creators` was returned to 0 before test use. `TeacherAccountDatabaseTest.createsRetainedAdminManagementBrowserFixture` exited 0, 1 passed; the fixture used the fixed synthetic admin ID and stored only the synthetic school ID in a generated fixture file. `admin-teacher-management.spec.js` ran via official `mcr.microsoft.com/playwright:v1.64.0-noble`; host Node.js was unavailable, but the bundled Node/npm/Chromium were used. Final Playwright invocation in the runner exited 0, 1 passed. It rejected invalid admin credentials, logged in as `admin`, created a teacher assigned to the fixture school with only `task-management` enabled, displayed the generated password once and cleared it on close, reloaded the administration list to verify the record and permissions, and reported no document/body horizontal overflow at 375px. MySQL read-back confirmed the created account is active, the school permission and task-management feature are enabled, and password storage uses the hash format.
- 2026-10-15 P20-09 stale-version follow-up: `sh scripts/testing/teacher-account-revocation-local.sh` — final exit 0. In the isolated V1–V34 schema, the fixture JUnit test passed (1/1), the WAR built, isolated Tomcat readiness succeeded, and Playwright 1.64.0/Chromium passed 1/1. A stale-version suspend POST returned HTTP 400 with the updated-version conflict message; reload confirmed the synthetic account remained active and its version unchanged. The subsequent current-version suspend succeeded and the audit displayed exactly one failed row whose action cell was exactly `停止`. The first Playwright invocation exited 1 (0 passed/1 failed) because a partial-text locator matched both `停止` and `停止解除`; exact action-cell matching corrected this test-only assertion, and the second invocation passed. The runner removed owned containers/network/WAR/fixture; host port 18098 was free afterward. No shared DB, real-user data, or external AI was used.
- 2026-10-15 P20-03 student-to-teacher/admin boundary: the final `sh scripts/testing/student-exercise-layout-local.sh` invocation exited 0; isolated fixture/migration/WAR/app setup succeeded and Playwright 1.64.0/Chromium passed 2/2. Invalid student credentials were rejected; valid synthetic login reached `/student/home`. The test reused that session ID on the configured teacher portal host and confirmed `GET` `/teacher/task` and `GET` `/admin/teachers` both returned HTTP 403, exercising role authorization rather than the cross-portal redirect. The same invocation also passed the existing folder-row interaction. The dedicated runner cleaned its containers/network and port 18091. A non-fatal MySQL `42000/1305` message reported a routine absent in the fresh schema during migration; the build and test runner completed successfully.
- 2026-10-15 P20-04 started-task revision rejection: isolated schema `ppe_teacher_task_test_started_revision`; `gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate test --tests control.teacher.TeacherTaskDatabaseTest.refusesRevisionWhenAnyStudentHasStartedThePublishedTask --rerun-tasks --warning-mode all` — exit 0; 1 test passed, 0 failed/errors/skipped. The published task and assignment remained published after an in-progress student fixture caused same-series revision creation to be rejected. A fresh-schema MySQL `42000/1305` message about an absent routine and the existing Gradle 9 deprecation warning were non-fatal. The one-off runner's EXIT cleanup check had an escaping error and left its dedicated DB/runner/network temporarily present; ownership labels were inspected and only those verified resources removed. No shared service was contacted or modified.
- P20-09 environment/test authoring retries: the first runner invocation exited 1 because e2e files were copied to the runner root, making `../../build/...` resolve to `/build`; the fixture was then placed under `/runner/build` and source under `/runner/e2e`, matching the repository layout. An attempted direct `docker cp` between containers failed because Docker does not support container-to-container copy; copying via the workspace fixture path succeeded. The first actual UI retry exited 1 because a locator matched all nine feature checkboxes in strict mode; the test now unchecks them individually. The next run completed creation but failed an assertion expecting `課題管理` where the UI's correct label is `課題編集`; the assertion was corrected. Each failed run's created teacher remained only in this disposable DB, later removed with the dedicated volume. An initial host readiness probe during Gradle dependency resolution returned curl exit 52 / HTTP 000; once Tomcat reported started, the in-container portal returned HTTP 200. These were harness/setup/expectation failures, not product failures.
- Admin run environment and safety: Docker was available; host `node --version` returned `command not found`, while the Playwright Docker image provided Node.js and Chromium. `npm ci` from the checked-in lockfile succeeded (3 packages added; 0 vulnerabilities). No external AI/Gemini call, real student data, production URL, or shared port-8080 application was used. The dedicated database, runner, app, volumes and networks were removed after DB read-back; shared services were preserved.
- P20-09 teacher authorization follow-up: dedicated Compose project `ppe-p20-authz-20261014`, schema `ppe_teacher_test_p20authz_20261014`, app port 18096. `teacher.localhost` readiness returned HTTP 200; `log_bin_trust_function_creators=0`. The retained synthetic admin fixture test had already passed (1 passed, exit 0). A pre-final `node --check phase20/admin-teacher-management.spec.js` exited 0, and the final `./node_modules/.bin/playwright test --config=playwright.config.js phase20/admin-teacher-management.spec.js` exited 0 with 1 passed. The flow created a teacher with only `task-management`, changed the initial password in a separate browser context, reauthenticated, saw `/teacher/task` return 200 and `/teacher/submissions` return 403. DB read-back confirmed active status, `teacher_must_change_password=0`, enabled fixture-school access, `task-management=1`, `submission-review=0`, and only one enabled feature. No Gemini API, real user data, production site, or shared port-8080 service was used. Setup/test failures and recoveries are recorded in [error report](../error-report.md); the isolated environment was cleaned after read-back.
- Final clean-build project cleanup: `docker compose -p ppe-p20-final-20261014 down -v` removed its MySQL/Gradle-cache volumes and application/database/outbound networks. Follow-up inventory found 0 project containers, 0 project volumes, and 0 project networks. The pre-existing shared app on port 8080 remained running.

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
- 2026-10-14 05:49 JST follow-up: the authenticated synthetic assignment 17 editor page on local `student.localhost:8080` was inspected at a 390px viewport. The pre-submit check remained 2/2 and `document`/`body` widths both measured 390px (no horizontal overflow). No submit was made. A subsequent GET to `/student/home` redirected to `/student/account/login`; student-home browser acceptance and the actual submit/resubmit UI flow remain unverified because the session was no longer authenticated. No DB writes, live API calls, or production-site access occurred; see the [error report](../error-report.md#工程20-p20-06-生徒エディター再追試2026-10-14-0549-jst).

### P20-04 revised publication browser acceptance (2026-10-14)

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Isolated app on port 18095; student and teacher login routes each returned HTTP 200 |
| Migration | PASS | 0 | Empty isolated MySQL V1–V34 migration and validation |
| Fixture + revision integration tests | PASS | 0 | Dedicated fixture 1/1 passed; immediate revision, synthetic prompt generation, scheduled revision/worker suite 3/3 passed; 0 skips/failures/errors |
| Browser E2E | PASS | 0 | Official Playwright 1.64.0 container; invalid/valid teacher and student login, replaced task status, revised notification, new assignment editor, and read receipt |
| Database read-back | PASS | 0 | Old assignment archived; revision 2 and new assignment published; exactly one revision notification remained and `read_at` was non-null |

**Overall**: PASS for the P20-04 revision/notification slice. The test used synthetic users/prompt only and made no Gemini API call. Host Node.js was unavailable; Playwright ran in its official Docker image. Dependencies were installed on an outbound-capable Docker network, then the browser container joined the isolated app network with Chromium resolver rules. The initial opt-in skip, two network-routing attempts, internal-network npm DNS failure, and two Gradle cache-lock attempts were recovered; see the [error report](../error-report.md#工程20-p20-04-改訂再公開通知ブラウザー受入2026-10-14-1052-jst).

Executed commands and results:

- `docker compose --env-file /dev/null -p ppe-p20-revision-20261015 run --rm --no-deps app gradle flywayMigrate flywayValidate --no-daemon --console=plain --warning-mode all` — exit 0, V1–V34 migrated/validated.
- Focused Gradle `TeacherTaskDatabaseTest` fixture — exit 0, 1 passed, 0 skipped/failures/errors. Revision integration selectors — exit 0, 3 passed, 0 skipped/failures/errors, with dedicated `GRADLE_USER_HOME` and `--project-cache-dir`.
- `docker run --rm --network ppe-p20-revision-20261015_application ... mcr.microsoft.com/playwright:v1.64.0-noble ./node_modules/.bin/playwright test --config=playwright.config.js phase20/revision-publication-notification.spec.js --workers=1` — exit 0, 1 passed, 0 failed.
- Final MySQL read-back confirmed revision/assignment states, notification count 1, and read receipt; `log_bin_trust_function_creators=0`. Dedicated Compose project/volumes/networks and fixture were then removed. The repository-wide Phase 20 status remains `PARTIAL / NEEDS_SIGNOFF`.

### P20-05 current-survey and task-preview isolated browser acceptance (2026-10-14)

#### Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Isolated app on host port 18097 served the unauthenticated login page; fixture-backed teacher and student login succeeded |
| Integration Tests | PASS | 0 | Prior full clean build: 144 suites / 552 tests, 369 passed, 183 skipped, 0 failures/errors; isolated fixture installer: 1 passed, 0 skipped/failures/errors |
| Browser E2E | PASS | 0 | `current-survey-settings.spec.js`: 2 passed, 0 failed; survey save/submit/read-back, consent boundary, teacher task status/action, mixed-case hint, CodeMirror preview, and responsive widths |

**Overall for this acceptance batch**: PASS for the tested P20-05 journeys. **Phase 20 overall** remains `PARTIAL / NEEDS_SIGNOFF`; this batch does not close the other planned journeys or prior opt-in skips.

#### Environment and verdict

```text
environment:
  docker: AVAILABLE — dedicated Compose containers started and were removed after the run
  node: AVAILABLE — Playwright 1.64.0 Docker image was used; no host Node.js assumption was needed
  playwright: AVAILABLE — official Playwright 1.64.0 image ran the spec
  infra-tier: PRIMARY(Docker-based) — isolated MySQL schema, application, and browser runner
  browser-tier: PRIMARY(Playwright) — authenticated browser journeys against the isolated app
startup: PASS — isolated app on host port 18097; login and authenticated routes rendered
integration: PASS — exit_code: 0, passed: 369 full-build tests plus 1 fixture test, failed: 0, skipped: 183 in the full build and 0 in the fixture test; scope: full clean build and isolated survey fixture
e2e: PASS — exit_code: 0, passed: 2, failed: 0; flows: current survey display/draft save/submit/read-only result and teacher task/hint preview at 375px and 1280px; boundaries: unauthenticated access and withdrawn-consent student; gaps: none within these flows
overall: NEEDS_SIGNOFF — this batch passed, but Phase 20 retains untested journeys, opt-in skips, and cross-browser/OS gaps
```

#### Test evidence

- A clean build in a disposable Gradle 8.10.2 / JDK 21 container completed before this browser batch: 144 suites, 552 tests, 369 passed, 183 skipped, 0 failures/errors. Skipped tests are not counted as passed.
- The dedicated MySQL schema `ppe_teacher_review_test_surveyacc` was migrated and validated through V1–V34. Only the active survey definition (survey ID 4, 6 questions, 30 options) was copied from the shared database; the copy was attached to synthetic task 1. No shared survey/response data was changed, and no existing response or real student data was copied.
- The opt-in `StudentSurveyAcceptanceFixtureTest` completed 1 test, 1 passed, 0 skipped/failures/errors, exit 0. It installed isolated synthetic teacher/student/task fixtures and the survey association; the browser journey then created and persisted the response.
- Final browser command, run in the official Playwright 1.64.0 image:

  ```sh
  npm run test:phase20 -- phase20/current-survey-settings.spec.js
  ```

  Result: exit 0, 2 passed, 0 failed. One flow checked unauthenticated login behavior, invalid credentials, current survey labels and all six questions, draft save/read-back, submission, read-only display, and HTTP 403 for a different synthetic student with withdrawn consent. The other checked task-list prompt state/action, mixed-case hint title `MiXeD Hint`, Python CodeMirror preview without line numbers, and no document horizontal overflow at 375px or 1280px.
- MySQL read-back verified 1 submitted response with all 6 questions answered using value 4, no response for the withdrawn-consent student, and the survey's 6 questions/30 options retained. No actual student data was used.
- Dedicated Compose project `ppe-p20-survey-acceptance-20261014`, application/browser/runner containers, schema, volumes, and networks were removed after testing. The shared app remained available and its unauthenticated root continued to return the expected HTTP 302 authentication redirect. No deployment or Gemini API call occurred.

#### Issues found and fixed

| # | Severity | Description | Resolution |
|---|---|---|---|
| 1 | LOW | Initial MySQL read-only probes omitted the configured DB password, attempted to read a GLOBAL-only setting at SESSION scope, and referenced nonexistent survey/response columns | Reused the isolated container's configured connection, queried `@@GLOBAL`, and corrected queries to actual schema fields; subsequent inspection/read-back succeeded without writes |
| 2 | LOW | The pre-existing full `TeacherReviewDatabaseTest` attempted to create a failure-injection trigger using the application DB account, which did not have the required trigger privilege | Replaced it for browser seeding with a focused guarded fixture installer; did not grant extra database privileges |
| 3 | LOW | A fresh Compose startup retained the default DB name rather than creating the requested custom schema; the first fixture connection timed out because the target schema was missing | Explicitly created and migrated only the named isolated test schema, then granted the app account access only to that schema; fixture test passed |
| 4 | LOW | Initial browser assertion expected unauthenticated HTTP 403, but the application intentionally returned HTTP 200 with the student login page | Updated the assertion to validate the rendered login page and status; final browser run passed |
| 5 | LOW | Playwright's `toBeDisabled()` matcher did not recognize the disabled state as expected on a fieldset | Asserted the disabled attribute and an actual disabled child radio input; final browser run passed |

The setup/probe errors and corrections are also retained in the [error report](../error-report.md). No production-source changes or product defects were introduced by this test batch.

### P20-03 ownership and P20-09 exercise-folder pointer acceptance (2026-10-14)

#### Environment and verdict

```text
environment:
  docker: AVAILABLE — dedicated, script-owned test containers/network; all removed after the run
  node: UNAVAILABLE on host — the official Playwright 1.64.0 Docker image supplied Node.js/npm
  playwright: AVAILABLE — official Playwright 1.64.0 image with Chromium
  infra-tier: PRIMARY(Docker-based) — isolated tmpfs MySQL and isolated Tomcat runtime
  browser-tier: PRIMARY(Playwright) — browser login and real pointer interactions against that runtime
startup: PASS — isolated app deployed the test WAR; synthetic login and exercise page rendered
integration: PASS — exit_code: 0, passed: 1 MySQL + 1 authenticated HTTP test, failed: 0, skipped: 0; WAR built
e2e: PASS — exit_code: 0, passed: 1, failed: 0; rejected/successful synthetic login, folder-row blank-area toggle, folder-name toggle, reload and persisted tree; no business-data mutation from toggles
overall: NEEDS_SIGNOFF — this ownership/UI slice passed; other Phase 20 journeys and browser/OS combinations remain unverified
```

#### Test evidence

- `sh scripts/testing/teacher-exercise-review-local.sh browser` — exit 0. The script started an isolated `ppe-exercise-review-test` network, tmpfs MySQL database `ppe_exercise_review_test`, test runtime on host port 18090, and ran `flywayMigrate`, the focused MySQL test, WAR build, and authenticated HTTP test. Results: `TeacherExerciseDatabaseTest.currentSavedTreesScopeDownloadsCsvAndRevocation` 1/1 passed; `TeacherExerciseRuntimeTest.authenticatedHttpReadExportsErrorsAndAuditFailure` 1/1 passed; 0 skip/failure/error.
- The focused MySQL/HTTP checks exercise school/class ownership, denied school/feature permission, revoked permission, inactive membership/school/student, consent and CSV restrictions, read-only behavior, and HTTP error/audit failure paths for teacher exercise-code review. These results do not establish authorization coverage for all application features.
- The official Playwright 1.64.0 Docker image installed the existing `e2e` lockfile dependencies in a disposable `/tmp` directory (`npm ci`: 3 packages added, 0 vulnerabilities). The test command inside the isolated browser container was:

  ```sh
  ./node_modules/.bin/playwright test --config=playwright.config.js phase20/exercise-folder-row-toggle.spec.js --workers=1
  ```

  Result: exit 0, 1 passed, 0 failed. It rejected invalid synthetic credentials, logged in the synthetic student, clicked the folder header's non-control lower-right padding with a real browser mouse event, confirmed expansion/child visibility and collapse/child hiding, toggled through the folder-name button, then reloaded and confirmed the persisted folder/file tree. No non-GET request was issued by the folder toggles.
- The integrated browser was also used to confirm label-button and blank-row click behavior against the same isolated runtime. No task/file content or account data was changed.
- Stopping the dedicated browser run executed its cleanup trap. All four script-owned containers and its network were confirmed absent; the browser runner and temporary dependency directory were removed. Shared app/DB containers remained running and were not used for writes.

#### Issues found and fixed

| # | Severity | Description | Resolution |
|---|---|---|---|
| 1 | LOW | Initial Playwright container invocation used a relative entrypoint without setting the container working directory and exited 127 (`./node_modules/.bin/playwright` not found) | Re-ran with `--workdir /work`; the selected spec passed 1/1, exit 0 |
| 2 | LOW | Integrated-browser login click timed out because the submit button was below the 639px viewport; an initial broad accessible-name locator also matched the login-help button | Opened a fresh browser context, focused the submit button through keyboard navigation and submitted; synthetic login succeeded. No application defect was found |
| 3 | LOW | Integrated-browser scrolling waits timed out on element stability; a login-role locator first caused a strict-mode ambiguity | Used a fresh context and measured/selected the actual row padding; subsequent pointer interactions and the automated Playwright test passed |

The login/runner invocation failures and recovery are also recorded in the [error report](../error-report.md). Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`.

### 2026-10-14 P20-03 peer distribution-template ownership acceptance

```text
environment:
  docker: AVAILABLE — isolated, script-owned tmpfs MySQL/Tomcat containers and network; removed after the run
  node: UNAVAILABLE on host — the official Playwright 1.64.0 Docker image supplied Node.js/npm
  playwright: AVAILABLE — Playwright 1.64.0 with Chromium in the isolated browser container
  infra-tier: PRIMARY(Docker-based) — isolated MySQL and application runtime
  browser-tier: PRIMARY(Playwright) — authenticated browser flows against the isolated runtime
startup: PASS — test WAR deployed; teacher login and distribution pages responded
integration: PASS — exit_code: 0, passed: 1, failed: 0, skipped: 0; focused MySQL fixture, Flyway migrate/validate, and WAR build
e2e: PASS — exit_code: 0, passed: 1, failed: 0; invalid/successful login, same-school peer template listing/edit/save/reload, owner read-back, foreign-school listing exclusion and direct-URL HTTP 403
overall: NEEDS_SIGNOFF — this ownership slice passed; Phase 20 still has other unverified role/ownership journeys and browser/OS combinations
```

#### Test evidence

- Focused Gradle command: `gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate test --tests control.teacher.TeacherDistributionDatabaseTest.createsRetainedPeerTemplateForPhase20BrowserAcceptance war --rerun-tasks` — exit 0. The single retained synthetic fixture test ran successfully; migrations and WAR packaging succeeded.
- Playwright command: `./node_modules/.bin/playwright test --config=playwright.config.js phase20/teacher-distribution-peer-edit.spec.js --workers=1` — exit 0, 1 passed, 0 failed. Same-school peer edits to template name/code persisted across reload and were visible to the owner. The historical distribution record correctly retained its original name snapshot.
- Foreign-school teacher did not see the template in the list; navigating to its direct URL returned HTTP 403. Invalid credentials were rejected.
- The first three browser attempts failed on test assumptions (login landing path, immutable distribution snapshot versus editable template, and host-resolver configuration being browser-specific); the corrected final run passed. These are recorded as resolved validation-harness issues in the [error report](../error-report.md).
- Dedicated test containers/network, tmpfs database, temporary WAR, and browser dependencies were cleaned up. Shared application/database were not used for writes. No production source was changed, no external AI was called, and no deployment or commit was performed.

Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`; the new acceptance covers only same-school sharing/editing and cross-school direct-access denial for distribution templates.

### 2026-10-14 P20-03 peer task-draft ownership acceptance

```text
environment:
  docker: AVAILABLE — dedicated tmpfs MySQL, Tomcat, Gradle, and Playwright containers on an isolated network; all removed after the run
  node: UNAVAILABLE on host — official Playwright 1.64.0 image supplied Node.js/npm
  playwright: AVAILABLE — Playwright 1.64.0 with Chromium
  infra-tier: PRIMARY(Docker-based) — isolated MySQL and application runtime
  browser-tier: PRIMARY(Playwright) — authenticated teacher flows against the isolated runtime
startup: PASS — isolated WAR deployed and teacher login route returned HTTP 200
integration: PASS — exit_code: 0; fixture setup 1 passed, same-school DAO authorization tests 2 passed, Flyway migrate/validate and WAR packaging passed; no test failures
e2e: PASS — exit_code: 0, passed: 1, failed: 0; invalid peer login, same-school peer draft edit/save, owner read-back, foreign-school list exclusion and direct-URL HTTP 403
overall: NEEDS_SIGNOFF — this task ownership slice passed; other Phase 20 ownership boundaries and opt-in cases remain unverified
```

#### Test evidence

- Dedicated MySQL V1–V34 migration/validation and retained fixture command: `gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate test --tests control.teacher.TeacherTaskDatabaseTest.createsRetainedPeerDraftFixtureForPhase20BrowserAcceptance war --rerun-tasks` with the fixture flag scoped to that command — exit 0; fixture 1 passed and WAR built.
- Playwright command: `./node_modules/.bin/playwright test --config=playwright.config.js phase20/teacher-task-peer-draft-ownership.spec.js --workers=1` — exit 0, 1 passed, 0 failed. Same-school peer edited and saved the draft; owner reloaded and observed the changed title. Foreign-school teacher saw no task row and direct GET returned HTTP 403.
- Existing `sameSchoolTaskManagerCanLoadAnotherTeachersDraft` and `sameSchoolTaskManagerCanEditDeleteAndRestoreAnotherTeachersTask` ran in a separate Gradle invocation with retention disabled — exit 0, 2 passed.
- The first combined invocation incorrectly enabled fixture retention for all selected tests; a retained `teacher-demo` account conflicted with the next test setup and caused exit 1. The fixture toggle was then restricted to the fixture-producing invocation, with DAO tests run separately. No product defect was indicated; details are in the [error report](../error-report.md#工程20-p20-03-教師の課題下書き所有境界browser受入2026-10-14-1810-jst).
- All isolated containers/network and tmpfs database were cleaned. No shared app/database writes, external AI, real data, production changes, or deployment.

Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`.

## Phase 20 — Same-school teacher without task-management access (2026-10-14 18:33 JST)

### Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Dedicated Tomcat on port 18102; teacher login route returned HTTP 200 |
| Integration | PASS | 0 | V1–V34 migration/validation, focused retained-fixture test, and WAR generation succeeded |
| Browser E2E | PASS | 0 | 1 Playwright test; same-school permitted peer edit/read-back, foreign-school denial, and same-school missing-feature denial |

**Overall**: NEEDS_SIGNOFF — this single feature-permission boundary passed; other Phase 20 routes, opt-in suites, and browser/OS combinations remain unverified.

### Environment and verdict

```text
environment:
  docker: AVAILABLE — isolated Compose MySQL and Tomcat
  node: UNAVAILABLE on host — Playwright ran in mcr.microsoft.com/playwright:v1.64.0-noble
  playwright: AVAILABLE — npm ci succeeded; 0 vulnerabilities
  infra-tier: PRIMARY(Docker-based) — fresh isolated MySQL schema
  browser-tier: PRIMARY(Playwright) — Chromium against the isolated authenticated app
startup: PASS — isolated app; teacher login route HTTP 200
integration: PASS — exit_code: 0, passed: 1 focused fixture test, failed: 0, skipped: 0; V1–V34 migrate/validate and WAR packaging passed
e2e: PASS — exit_code: 0, passed: 1, failed: 0; authorized peer draft update/read-back, foreign-school direct GET 403, same-school teacher without task-management direct GET 403
overall: NEEDS_SIGNOFF — the tested feature boundary passed, but Phase 20 coverage is incomplete
```

### Evidence

- `docker compose -p ppe-p20-permission-20261014 run --rm --no-deps app gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate` — exit 0 after resetting only the disposable schema and enabling the migration-only MySQL routine setting.
- `docker compose -p ppe-p20-permission-20261014 run --rm --no-deps -e TEACHER_TASK_DB_TEST=true -e TEACHER_TASK_PEER_DRAFT_BROWSER_FIXTURE=true app gradle --offline --no-daemon --console=plain test --tests control.teacher.TeacherTaskDatabaseTest.createsRetainedPeerDraftFixtureForPhase20BrowserAcceptance war` — exit 0; focused fixture test and WAR packaging succeeded. The test-only password environment value is intentionally omitted.
- `./node_modules/.bin/playwright test --config=playwright.config.js phase20/teacher-task-peer-draft-ownership.spec.js --workers=1` inside the Playwright 1.64.0 container — exit 0, 1 passed, 0 failed. The no-task-management account authenticated through its enabled prompt-design permission, then received HTTP 403 from the direct task URL; DB read-back showed same-school access enabled and `task-management=0`.
- MySQL `log_bin_trust_function_creators` was `0` after migration and during/after the browser run. Dedicated containers, volumes, network, and fixture properties were removed (0 remaining). Shared teacher login remained HTTP 200.
- Initial offline plugin resolution and online Plugin Portal TLS failures, followed by the first-migration MySQL Error 1419 and schema recovery, are retained in the [error report](../error-report.md#工程20-p20-03-課題管理権限のない同一校教師による直接アクセス拒否2026-10-14-1833-jst).

## Phase 20 — Existing teacher session invalidation after administrator suspension (2026-10-15 01:33 JST)

### Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Isolated Tomcat on host port 18098 served the teacher login page |
| Integration | PASS | 0 | V1–V34 migration/validation, retained browser fixture, and WAR generation succeeded; 1 suite / 1 test passed |
| Browser E2E | PASS | 0 | 1 Playwright test; teacher route allowed before suspension, admin suspension saved, existing teacher session redirected to login |

**Overall**: NEEDS_SIGNOFF — the suspension/session boundary passed; remaining Phase 20 routes, opt-in cases, and browser/OS coverage are still incomplete.

### Environment and verdict

```text
environment:
  docker: AVAILABLE — docker info succeeded; disposable MySQL and Tomcat containers were used
  node: UNAVAILABLE on host — Playwright ran in mcr.microsoft.com/playwright:v1.64.0-noble
  playwright: AVAILABLE — bundled Chromium launched successfully
  infra-tier: PRIMARY(Docker-based) — isolated tmpfs MySQL schema ppe_teacher_test_p20revocation
  browser-tier: PRIMARY(Playwright) — authenticated browser flow against isolated Tomcat
startup: PASS — `sh scripts/testing/teacher-account-revocation-local.sh`; teacher login readiness HTTP 200 on host port 18098
integration: PASS — exit_code: 0, passed: 1, failed: 0, skipped: 0; V1–V34 migrate/validate, retained fixture test, and WAR packaging
e2e: PASS — exit_code: 0, passed: 1, failed: 0; allowed teacher route HTTP 200, administrator suspension POST HTTP 200, suspended state on reloaded account list, existing teacher session redirected to login
overall: NEEDS_SIGNOFF — the tested session boundary passed, but Phase 20 remains incomplete
```

### Evidence

- `sh scripts/testing/teacher-account-revocation-local.sh` — exit 0. Gradle 8.10.2/JDK 21 ran `flywayMigrate flywayValidate`, `TeacherAccountDatabaseTest.createsRetainedAdminManagementBrowserFixture`, WAR packaging, and the JUnit summary: 1 suite, 1 test, 1 passed, 0 failures/errors/skips.
- Playwright 1.64.0/Chromium ran `phase20/admin-teacher-management.spec.js` — exit 0, 1 passed. The synthetic teacher first accessed `/teacher/task` successfully; the administrator suspended that teacher; subsequent access through the same browser context was redirected to `/teacher/account/login`.
- Initial fixture-copy and browser container DNS/port failures, plus the stale admin-list state corrected by reloading before the suspension action, are documented in the [error report](../error-report.md#工程20-p20-03-管理者による教師停止後のセッション失効2026-10-15-0133-jst). Host `node --check` was unavailable (Node.js is not installed on host); the Playwright container successfully parsed and ran the spec.
- Disposable MySQL/Tomcat/builder/browser containers, network, WAR, and fixture properties were removed. Shared application/DB and other running services were not changed. No external AI or real user data was used.

## Phase 20 — Teacher account state and administrator-route authorization acceptance (2026-10-15 01:56 JST)

### Summary

| Step | Status | Exit Code | Details |
| --- | --- | ---: | --- |
| Startup | PASS | 0 | Isolated Tomcat on host port 18098 served the teacher login page |
| Integration | PASS | 0 | V1–V34 migration/validation, retained browser fixture, and WAR generation succeeded; 1 test passed |
| Browser E2E | PASS | 0 | 1 Playwright test; account lifecycle, deleted-account reactivation denial/audit, and teacher denial from admin GET/POST routes plus no-create verification |

**Overall**: NEEDS_SIGNOFF — this teacher-account lifecycle boundary passed; the broader Phase 20 journey and role-boundary matrix remains incomplete.

### Environment and verdict

```text
environment:
  docker: AVAILABLE — disposable MySQL, builder, Tomcat, and Playwright containers
  node: UNAVAILABLE on host — Playwright ran in mcr.microsoft.com/playwright:v1.64.0-noble
  playwright: AVAILABLE — bundled Chromium launched successfully
  infra-tier: PRIMARY(Docker-based) — isolated tmpfs MySQL schema ppe_teacher_test_p20revocation
  browser-tier: PRIMARY(Playwright) — authenticated lifecycle flow against isolated Tomcat
startup: PASS — `sh scripts/testing/teacher-account-revocation-local.sh`; teacher login readiness HTTP 200 on host port 18098
integration: PASS — exit_code: 0, passed: 1, failed: 0, skipped: 0; V1–V34 migrate/validate, retained fixture test, and WAR packaging
e2e: PASS — exit_code: 0, passed: 1, failed: 0; lifecycle assertions passed, teacher GET /admin/teachers and CSRF-valid POST /admin/teachers returned HTTP 403, attempted teacher creation was absent from admin list
overall: NEEDS_SIGNOFF — tested account lifecycle passed, but Phase 20 remains incomplete
```

### Evidence

- `sh scripts/testing/teacher-account-revocation-local.sh` — final exit 0; Gradle 8.10.2/JDK 21, V1–V34 migration/validation, fixture JUnit 1 passed, WAR packaged; Playwright 1.64.0/Chromium 1 passed, 0 failed. Earlier test-only failures and corrections: the audit-row locator initially matched both successful/failed reactivation rows and was narrowed; the teacher admin-POST denial initially attempted to parse Tomcat's HTML 403 as JSON and was changed to inspect status/text. The final run also verified the attempted account creation was absent from the admin list.
- The teacher accessed `/teacher/task` before suspension (HTTP 200). Administrator suspension returned HTTP 200; the account row showed `停止中`, existing session redirected to login, and suspended-account login was rejected. Reactivation returned HTTP 200; login and `/teacher/task` access succeeded. Logical deletion returned HTTP 200; the row showed `削除済み`, account operation buttons were absent, existing session redirected to login, deleted-account login was rejected, a direct confirmed/CSRF-valid activate request returned HTTP 400 with a deleted-account error, and operation history showed deletion `成功` plus reactivation `失敗`. The active teacher then received HTTP 403 on direct admin console GET and a valid-CSRF account-creation POST; permitted `/teacher/task` remained HTTP 200, ungranted `/teacher/submissions` returned HTTP 403, and the attempted account did not appear in the administrator list.
- Cleanup verified all dedicated containers/network, WAR, and fixture properties absent; port 18098 was free. Shared teacher login remained HTTP 200; no shared DB/application changes or external AI calls.
- Fresh-schema migration printed MySQL SQL State `42000` / Error Code `1305` for a routine that did not exist during a drop step; migrations/validation and the command completed successfully. The initial Playwright failure and its test-locator correction are also recorded in the [error report](../error-report.md).
## 2026-10-15 工程20 opt-in検証追補

### Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| URL/portal HTTP runtime | PASS | 0 | 専用MySQL/Tomcat上で`ApplicationUrlRuntimeTest` 5 passed、0 failed/skipped |
| TeacherTask MySQL integration | PASS (suite) | 0 | 45 tests: 35 passed、10 skipped、0 failed/errors。fixture/ブラウザー専用ケースのskipはpassに含めない |
| Gemini diagnostics + synthetic smoke | PASS (scope-limited) | 0 | 選択7 tests: 6 passed、1 skipped（primary recheckの個別gateは無効）。合成入力のみ |
| Gemini operational candidates | FAIL | 1 | 4 tests: 3 passed、1 failed。Flash Lite/current Pro structured generation passed; `gemini-2.5-pro` structured generation returned HTTP 404 |
| Gemini DB-backed evaluation smoke | PASS | 0 | 専用MySQL上の`EvaluationDatabaseTest.savesLiveGeminiResultOnlyWhenExplicitlyEnabled`: 1 passed、0 failed/errors/skipped。合成評価の完了とDB保存を確認 |

**Overall for this validation batch**: FAIL — `GeminiOperationalCandidateTest` exited 1; 3 tests passed and 1 failed without a waiver. The other listed test commands passed within their recorded scopes.

**Phase 20 overall**: PARTIAL / NEEDS_SIGNOFF — the candidate failure is unresolved, and the full major journey/role-ownership matrix, remaining opt-in fixture/browser paths, and other browser/OS coverage are incomplete.

### Environment and verdict

```text
environment:
  docker: AVAILABLE — URL routing, TeacherTask, and Gemini DB test each used disposable isolated MySQL/container resources
  node: UNAVAILABLE on host; not required for these JUnit HTTP/API tests
  playwright: NOT RUN in this batch; prior isolated Playwright evidence remains separately scoped above
  infra-tier: PRIMARY(Docker-based) — dedicated tmpfs MySQL; no shared DB/app was used
  browser-tier: NOT APPLICABLE to these tests; ApplicationUrlRuntimeTest uses real HTTP through an isolated Tomcat
startup: PASS — isolated Tomcat served the teacher login page before the 5-test URL runtime suite; DB-only suites migrated fresh schemas
integration: FAIL — exit_code: 1 for GeminiOperationalCandidateTest (3 passed, 1 failed); TeacherTask separately passed with exit_code: 0 (35 passed, 10 skipped), Gemini DB smoke passed with exit_code: 0 (1 passed)
e2e: UNVERIFIED — no browser E2E was part of this batch; previous browser coverage is separate and does not imply full matrix coverage
overall: FAIL — the candidate suite exited non-zero with no waiver; Phase 20 remains PARTIAL / NEEDS_SIGNOFF because broader sign-off conditions also remain
```

### Commands and evidence

- `sh -n scripts/testing/application-url-routing-local.sh` — exit 0.
- `sh scripts/testing/application-url-routing-local.sh` — exit 0; V1–V34 migration and WAR build succeeded; `ApplicationUrlRuntimeTest` 5 passed. Initial run had 5 failures because the test's static `HttpClient` set a restricted `Host` header before the required JDK property was set. Adding the property in the test class static initializer resolved the setup issue; the final run passed.
- TeacherTask suite used a fresh schema matching `ppe_teacher_task_test_*` with `TEACHER_TASK_DB_TEST=true`, `GEMINI_API_SMOKE_TEST=false`, and a non-routable placeholder `PYTHON_RUNNER_URL` solely to construct the unused runner client. Gradle `flywayMigrate test --tests control.teacher.TeacherTaskDatabaseTest` — final exit 0, 45 tests, 35 passed, 10 skipped. Initial execution had 2 failures because `PYTHON_RUNNER_URL` was absent; the two affected methods only perform DB persistence and did not call the runner. The first ad-hoc readiness probe also had a shell-quoting error and did not start Gradle; it was corrected before the test run.
- Gemini real API diagnostics/smoke used only the synthetic test payloads in the selected test classes, model `gemini-3.1-pro-preview`, and the configured API key from `.env`; the key itself was never displayed or copied into reports. `GEMINI_API_DIAGNOSTIC_TEST=true` and `GEMINI_API_SMOKE_TEST=true`; model-comparison, primary-recheck, and operational-candidate gates were explicitly false. Selected tests: `GeminiMinimalApiDiagnosticTest`, `GeminiClassicApiDiagnosticTest`, `GeminiStructuredApiDiagnosticTest`, `TeacherPromptApiDiagnosticTest`, and `GeminiEvaluationSmokeTest`. Gradle test exit 0. The four diagnostic generation calls were within the helper's four-call limit; there was one separate synthetic evaluation smoke generation and one model-metadata GET.
- Gemini DB-backed smoke used a separate fresh `ppe_evaluation_test_*` schema and `EVALUATION_DB_TEST=true`, `GEMINI_API_SMOKE_TEST=true`; `EvaluationDatabaseTest.savesLiveGeminiResultOnlyWhenExplicitlyEnabled` — exit 0, 1 passed, 0 failed/errors/skipped. The test asserted the worker completed and persisted validated evaluation results. Worker retry policy allows up to three attempts; the attempt count was not retained by this run's copied evidence, so no exact aggregate request total is claimed for this DB-backed call.
- `GeminiOperationalCandidateTest` ran with `GEMINI_API_OPERATION_CANDIDATE_TEST=true` — Gradle test exit 1, 4 tests completed, 3 passed, 1 failed. One model-list GET succeeded; structured generation/read-back succeeded for `gemini-3.5-flash-lite` and `gemini-3.1-pro-preview`, while `gemini-2.5-pro` returned HTTP 404. The list-model response contained `models/gemini-2.5-pro`; this confirms catalog visibility only, not that its structured generation endpoint is usable. The attempt used one generation call for each of the three candidates; application defaults/catalog were not changed. The retained evidence does not preserve the full Docker wrapper invocation, so it is not reconstructed here.
- The diagnostic test process passed, but its stopped-container test XML/diagnostic output could not be copied because the runner attempted `docker exec` after Gradle had exited. The test output confirmed Gradle exit 0; selected test counts above are from the selected cases and their explicit opt-in gate. The diagnostic call was not repeated solely to recover output, avoiding additional billable API traffic.
- The candidate-suite XML retained the HTTP 404 and status/model observations. The saved artifact does not include the provider error code, response request ID, Retry-After, or response body; these are unavailable from retained evidence and intentionally not inferred. The endpoint was `https://generativelanguage.googleapis.com/v1beta/interactions`; no API key or response body was recorded.
- Fresh MySQL migrations emitted the known nonfatal missing-routine drop warning (SQL State `42000`, Error Code `1305`); migration/build/tests still succeeded. Existing Gradle 9 deprecation warnings were also present.

### Remaining gaps

- The initially listed URL-routing, TeacherTask DB, and Gemini smoke items were executed. The operational candidate test exposed one failed model; model-comparison and primary-recheck tests have separate prior evidence in the teacher AI plan and were not repeated in this batch.
- Other historical DB/HTTP/browser opt-ins are documented in their feature plans; remaining Phase 20 work is the unverified full route/ownership matrix and cross-browser/OS coverage.
- `ApplicationUrlRuntimeTest` gained a static `jdk.httpclient.allowRestrictedHeaders=host` property initialization before its static clients are created.
- Detailed initial-run failures and runner corrections are recorded in [error-report.md](../error-report.md). The overall Phase 20 verdict remains `PARTIAL / NEEDS_SIGNOFF`.

## 2026-10-15 P20-03 授業演習コード確認権限取消追試

- Command: `sh scripts/testing/teacher-exercise-review-local.sh integration` — exit 0. The runner migrated the disposable MySQL schema, built the WAR, and ran the focused authenticated HTTP test.
- Evidence: `TeacherExerciseRuntimeTest.authenticatedHttpReadExportsErrorsAndAuditFailure()` — 1 passed, 0 failed, 0 errors, 0 skipped. The test revoked `exercise-code-review` in the already-authenticated session and verified HTTP 403 for the exercise page, list, detail, individual download, ZIP, bulk, and CSV routes. Denials contained neither the synthetic student login nor saved code, the independently permitted submission-review page remained HTTP 200, and the export audit count did not increase after revocation.
- The same integration test also verifies the separate school-ownership boundary: a teacher with the feature permission but only another school's assignment sees an empty list and receives HTTP 404 for the same target's detail, individual download, and ZIP routes, while the fixture owner can read those same target records.
- Survey ownership follow-up: `sh scripts/testing/teacher-survey-review-local.sh integration` — exit 0; `TeacherSurveyRuntimeTest` 1 passed, 0 failed/errors/skipped. The existing isolated fixture's other-school-only teacher with `survey-results` permission sees an empty JSON list, receives HTTP 404 for the target response detail, and gets CSV/TXT exports without the target response/record codes, student login ID, or unique synthetic answer content.
- Evaluation ownership follow-up: `sh scripts/testing/teacher-submission-review-local.sh integration` — final exit 0; `TeacherReviewRuntimeTest` 1 passed, 0 failed/errors/skipped. For the other-school-only teacher with `evaluation-review`, the list is empty; target detail/file/logs return HTTP 404; CSV contains no synthetic student/code and records a successful zero-row export audit; ZIP and logs-ZIP return HTTP 400 with no target data. Three earlier test-side failures (duplicate local variable, expected ZIP status, and audit baseline count) were corrected and documented in [error-report.md](../error-report.md).
- Submission ownership follow-up, same run: the other-school teacher's `submission-review` list is empty, target detail/file return HTTP 404, CSV contains no target student/code and records a zero-row export, and ZIP returns HTTP 400 without target data. `sh scripts/testing/teacher-submission-review-local.sh integration` — exit 0; `TeacherReviewRuntimeTest` 1 passed, 0 failed/errors/skipped (HTTP integration report timestamp 2026-10-15T04:44:49).
- Submission ownership browser follow-up: the isolated `sh scripts/testing/teacher-submission-review-local.sh browser` environment built the WAR, passed the authenticated HTTP `TeacherReviewRuntimeTest` (1 passed, 0 failed/errors/skipped; final HTTP report timestamp 2026-10-15T05:25:04), and served the teacher login page on host port 18089. Playwright 1.64.0/Chromium ran `phase20/teacher-submission-ownership.spec.js` — final exit 0, 1 passed, 0 failed. The owner saw synthetic submissions; the other-school-only teacher saw an empty rendered list and disabled exports, with direct detail/file navigation/fetch returning 404, empty CSV returning 200 without target identity/code, and ZIP returning 400 without target data.
- Exact Playwright command: `docker run --rm --network ppe-submission-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-teacher-review-runtime, MAP student.localhost ppe-teacher-review-runtime' -e PPE_P20_REVIEW_TEACHER_PASSWORD='ReviewDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-submission-ownership.spec.js --workers=1 --output=/tmp/ppe-submission-ownership-results'`. Docker daemon and bundled Chromium were available; host Node.js was unavailable, so the primary Playwright tier ran in the official image. Browser fixture containers/network were removed after the run.
- Initial browser attempts failed due to fixture state residue and a late XHR wait; both test-side causes were corrected and the final run passed. The browser spec passed twice after the corrections. See [error-report.md](../error-report.md). This adds one accepted Playwright test; the current aggregate is 23 successful test executions / 20 browser fixture-run invocations (25 isolated fixture batches including intermediate failures).
- Environment: Docker-based isolated MySQL/Tomcat runner; temporary containers/network were removed. The fresh migration printed the known nonfatal missing-routine drop warning (SQL State `42000`, Error Code `1305`); Gradle also printed its existing Gradle 9 deprecation warning. Build and test completed successfully.
- Verdict: Integration PASS — exit_code: 0, passed: 1, failed: 0, skipped: 0; focused Browser E2E PASS — exit_code: 0, passed: 1, failed: 0. Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`; the broader route/ownership matrix and cross-browser/OS coverage remain open.

## Phase 20 — Evaluation review school-ownership browser acceptance (2026-10-15)

- Command: `sh scripts/testing/teacher-submission-review-local.sh browser` — isolated build, authenticated HTTP integration and browser fixture startup succeeded. `TeacherReviewRuntimeTest` passed 1, failed 0, skipped 0 (HTTP report timestamp `2026-10-15T14:29:24`).
- Playwright command: `docker run --rm --network ppe-submission-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-teacher-review-runtime, MAP student.localhost ppe-teacher-review-runtime' -e PPE_P20_REVIEW_TEACHER_PASSWORD='ReviewDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-submission-ownership.spec.js --workers=1 --output=/tmp/ppe-review-ownership-results'` — exit 0, 2 passed, 0 failed. Existing submission ownership test and new evaluation ownership test both passed.
- Owner teacher saw reviewable synthetic evaluations and opened an evaluation detail modal. The other-school-only teacher saw an empty rendered list and summary, disabled CSV/ZIP/logs-ZIP controls, empty list API, HTTP 404 for direct detail/file/logs navigation and browser fetch, HTTP 200 for an empty CSV without target identity/code, and HTTP 400 for ZIP/logs-ZIP without target data.
- Docker daemon was available; host Node.js was unavailable. The official Playwright v1.64.0 image supplied Node.js and Chromium. The dedicated tmpfs MySQL/Tomcat, browser container, and network were removed after verification. No shared application/database, real user data, or external AI was used.
- The combined browser invocation adds two successful test executions, including one repeat of the already accepted submission-ownership test, and one fixture-run invocation. Aggregate: 25 successful Playwright test executions / 21 browser fixture-run invocations; 26 isolated fixture batches including intermediate failures. Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`.

## Phase 20 — Survey result school-ownership browser acceptance (2026-10-15)

- Command: `sh scripts/testing/teacher-survey-review-local.sh browser` — isolated migration, WAR build, and `TeacherSurveyRuntimeTest` succeeded; JUnit 1 passed, 0 failed/errors/skipped, Gradle exit 0. Tomcat was available at `teacher.localhost:18091`.
- Playwright command: `docker run --rm --network ppe-survey-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-survey-review-runtime, MAP student.localhost ppe-survey-review-runtime' -e PPE_P20_REVIEW_TEACHER_PASSWORD='ReviewDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-submission-ownership.spec.js phase20/teacher-survey-ownership.spec.js --workers=1 --output=/tmp/ppe-survey-ownership-results'` — exit 0, 3 passed, 0 failed. This includes two repeated submission/evaluation checks and the new survey ownership test.
- Owner teacher saw two synthetic survey responses and opened the detail containing the unique synthetic answer. The other-school teacher saw a zero-count page/summary and empty list; direct detail navigation/fetch returned HTTP 404. CSV/TXT returned HTTP 200 without response/record codes, student login ID, or synthetic answer/reason.
- Environment: Docker daemon available; host Node.js unavailable; official Playwright v1.64.0 image provided Node.js and Chromium. After the browser test, the long-running browser-mode harness was stopped with SIGINT (harness exit 130); its cleanup trap removed the dedicated tmpfs MySQL/Tomcat/browser containers and network. No shared DB, real user data, or external AI was used. The HTTP runtime test temporarily revokes the owner permission; the test now restores it and asserts restoration so the same disposable fixture supports browser positive-controls.
- The invocation adds one accepted logical test case and three successful test executions; aggregate: 28 successful Playwright test executions / 22 browser fixture-run invocations / 27 isolated fixture batches including intermediate failures. Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`; other role/ownership journeys and browser/OS combinations remain open.

## Phase 20 — Exercise-code review school-ownership browser acceptance (2026-10-16)

- Initial command `sh scripts/testing/teacher-exercise-review-local.sh browser` exited 1 during test compilation because the new fixture-restoration assertion referred to a statement variable outside its scope. After giving the restoration its own try-with-resources statement, the same command succeeded: migration/WAR/runtime test passed, `TeacherExerciseRuntimeTest` 1 passed, 0 failed/errors/skipped, exit 0. The runtime was available at `teacher.localhost:18090`.
- Initial Playwright command below exited 1 because the test expected the UI to show `hello.py` content immediately, while the UI correctly selected the first file (`main.py`) and showed `print(2)`. The test expectation was corrected to the initial UI state; no product code changed. The final invocation passed 1 test, 0 failed, exit 0.
- Playwright command: `docker run --rm --network ppe-exercise-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-exercise-review-runtime, MAP student.localhost ppe-exercise-review-runtime' -e PPE_P20_EXERCISE_TEACHER_PASSWORD='ExerciseDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-exercise-ownership.spec.js --workers=1 --output=/tmp/ppe-exercise-ownership-results'` — final exit 0, 1 passed, 0 failed.
- Owner teacher saw four synthetic exercise rows, opened the `ex_saved` detail, and saw its code in the UI. The other-school-only teacher saw no rows/files, an empty list API, disabled CSV/bulk controls, HTTP 404 for direct detail/download/ZIP, an empty CSV with HTTP 200, and HTTP 400 for bulk with no target data.
- Environment: Docker daemon available; host Node.js unavailable; official Playwright v1.64.0 image supplied Node.js and Chromium. After the browser test, the long-running browser-mode harness was stopped with SIGINT (harness exit 130); its cleanup trap removed the dedicated tmpfs MySQL/Tomcat/browser containers and network, and no matching resources remained. Aggregate after this addition: 25 accepted logical cases, 29 successful Playwright executions / 23 successful browser fixture-run invocations / 30 isolated fixture batches including intermediate failures. Phase 20 remains `PARTIAL / NEEDS_SIGNOFF`.
