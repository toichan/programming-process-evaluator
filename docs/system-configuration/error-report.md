# 実装・検証エラーレポート

秘密情報・実際の生徒データ・研究データは記載しない。解消後も履歴を保持する。

## 2026-10-17 R-03全量seedの調査SQL・JUnit集計エラーと解消

- 発生日時: 2026-10-17 JST。同日の調査・再検証中。各失敗の開始時刻は保存されていないため不明とし、run識別子で区別する。
- 対象/期待結果: 既存local DBをread-onlyで棚卸しし、確定設定25テーブルの非公開保全と、隔離空DBへの全量投入/不変/rollbackを検証する。本番、他projectのDB、実APIは対象外。
- 調査中の失敗: local件数集計SQLのquote不整合でMySQL 1064、surveyを状態別集計する補完queryが`ONLY_FULL_GROUP_BY`違反1055となった。いずれもSELECTのみでDB変更なし。複雑なCONCAT/非集約列を除き、対象tableのCOUNTと状態別COUNTへ分割してexit 0を得た。パスワード引数のMySQL警告が出た読取コマンドは、後続で`MYSQL_PWD`による非表示注入に変更した。秘密値は出力していない。
- 実装runnerの失敗: `initial-configuration-local.sh`のrun-56310は単体4件・DB統合1件が成功した後、JUnit集計に親ディレクトリを渡し、`No JUnit XML reports found.`でexit 1。既存集計helperは直下XMLのみを対象とするため、unit/databaseそれぞれのディレクトリを渡すよう修正した。テスト自体の失敗やseed破損ではないが、runner全体を成功扱いしない。
- 文書更新の失敗: error-reportの見出しを誤認したpatch hunkだけが不一致で停止した。先行3文書への適用は保持し、失敗した見出し範囲だけ確認して追記を再実行した。ソース変更・他者変更のrevertは行わない。
- 最終再検証: `PPE_INITIAL_SEED_PRIVATE_DIR=<private-dir> sh scripts/testing/initial-configuration-local.sh`のrun-57489はexit 0。単体4 passed、MySQL統合1 passed、失敗/error/skipはいずれも0。WAR成功。故障注入後0部分行、同一seedのno-op、不一致/並行/履歴混入拒否、再接続read-backに加え、同一保全ファイルを独立した空DB2つへmigration後にapply/verifyして全25テーブル一致を確認。export後の元DB verifyも成功し、元DBはread-onlyのみ。
- R-05追試: `sh scripts/testing/teacher-learning-flow-local.sh`のrun-56660はexit 0、JUnit 7 passed/0 failed/errors/skipped、Playwright 3 passed/0 failed。これは合成fixtureの技術試験であり、本人による手動E2E/採点品質や確定設定全量の画面利用の確認とは区別する。
- 残件/境界: 利用者受入の確認を依頼したがユーザー不在で回答未取得。受入済みとは記録しない。保全ファイルは非公開0600で保持し、値やhashをGit/レポートへ出さない。実装はlocal/simulation・隔離DB限定であり、本番投入、旧日時の意味確定、正式同意、R-06とR-07の承認は別ゲート。専用container/networkを清掃した。

## 2026-10-17 09:56 JST R-01照合とR-03〜R-05隔離検証の失敗・保留

- 対象・期待結果: 確定済みlocal初期設定の棚卸しとread-only production比較、隔離MySQLでの初期投入/再実行/rollback、合成教師→生徒→評価→survey→教師のHTTP/DB/browser縦断を確認する。本番書込み・deploy・restart、実データ、Gemini実APIは対象外。
- R-01読取時の一時失敗: 最初のDBアクセスで誤ったsecret取得方法を使用、SSM commandのquote不一致、取得wrapperのcompletion sentinel欠落、local集計SQLのambiguous columnが発生した。誤りを除き各確認をread-onlyで再実行、最終照合queryはexit 0。認証値・生徒/研究データは出力せず、本番状態は変更していない。
- runner準備の失敗と修正: Docker bind mount指定誤りは明示的な`type=bind,src,dst`へ修正。`--internal` networkのpublished portはhost loopbackから到達できなかったため、builder container内のHTTPS proxy経由readinessへ変更。simulationのHTTPは403となり、隔離生成証明書付きTLS proxyと一時truststoreを追加。absolute HTTPS redirectをrelative-onlyと比較していたtest helperはURI path比較へ修正。これらの修正後に隔離Tomcatはreadyとなった。
- 非同期prompt生成: 初回testはqueue登録直後に`completed`をassertし、DBが`in_progress`のため失敗。30秒上限・100ms間隔のDB pollingへ変更し、合成providerの2段生成まで完了することを確認。evaluation-example生成時の400はtestがフォームhiddenの`aiModel`/`commonPrompt`を送っていなかったことが原因で追加した。次の409は既生成の揺らぎ候補の後にtestだけがadditional instructionを変更し、draft保存が候補をinvalidateしたため。testを同じinstruction値で進めるよう修正後、教師draft/prompt/合成評価例/read-back testは1/1成功した。
- 未公開prompt適用の未解決: `sh scripts/testing/teacher-learning-flow-local.sh`はexit 1。`TeacherLearningFlowRuntimeTest`は2件中1 passed/1 failed。stale `expectedRowVersion=999`のPOSTがtest期待409に対して302を返した。`TeacherPromptServlet`の適用actionはPOST値ではなく再読込したcurrent row versionを`applyUnpublishedPrompt`へ渡す実装になっている。画面にはexpected row versionを送信するhidden fieldが存在するため、 stale表示からの適用を拒否する契約との不一致が疑われる。runtime testerとして本番コードは変更していない。担当実装で契約確認・修正し、 stale POSTの409および状態不変を再検証するまでR-04/R-05はFAIL/未完了。
- survey SQL: `sh scripts/testing/teacher-learning-flow-local.sh survey-sql`の初回は`DROP TRIGGER`でERROR 1360（trigger does not exist）となったため、cleanupを`DROP TRIGGER IF EXISTS`に変更。次回はtemplate初回適用・設問6件/選択肢30件・既存設問ありのfail-closed再実行（ERROR 1644）は通過したが、rollback用triggerの作成後に`information_schema.triggers`で0件となり、失敗注入へ進まずexit 1。rollback behaviorは未検証。原因は確定していない。triggerの存在/DB scopeと失敗注入方法を、隔離DBで改めて確認する。
- 検証境界: fixture test 1/1、rubric mismatch拒否test 1/1、教師の合成prompt flow 1/1は成功。全教師test commandは1 failureでexit 1。survey rollback、student evaluation/survey、restart read-back、Playwright browser stageは未到達/未実行。host Docker daemonはavailable、host Node.jsはunavailable。Playwright containerはrunnerに組み込まれているが、このrunnerはbrowser stage前に停止した。real provider/API要求は0、本番write/deploy/restartは0。
- 再検証結果・残件: 2026-10-17 09:56 JST時点で`sh scripts/testing/teacher-learning-flow-local.sh survey-sql`を3回試し、いずれもexit 1（最初はtrigger cleanup、以後はfailure-injection trigger未確認）。上記の回数制限に従い同一failure-injectionを追加再試行せず、implementation ownerの調査とtrigger方式の再設計を待つ。調査用`.r01-readonly-probe.sh`は削除した。隔離runnerのtrap cleanupは各runに設定されている。詳細verdictは[runtime validation report](./feature-plans/runtime-validation-report.md#2026-10-17-1111-jst-r-03r-05-合成縦断検証の再検証結果)と[roadmap R-01〜R-05](./implementation-roadmap.md#残作業-テストから次回本番反映まで2026-10-17整理)を参照する。

## 2026-10-17 05:37 JST UTC/JST候補レビューで見つかった初期画面の時刻表示漏れ

- 対象・期待結果: UTC保存済み日時を持つページを初期HTTP表示した際、生徒演習の実行/ごみ箱日時、教師promptの再評価プレビュー有効期限/履歴日時がAsia/Tokyoで表示され、演習ツリーAPI・他の日時表示と一致すること。
- 指摘: 読み取り専用候補レビューで2点を検出。`exercise.jsp`の初期bootstrapはUTCの`executedAt`/`trashedAt`/`updatedAt`をそのままdata属性へ渡し、JavaScriptがそのまま表示していた。`prompt.jsp`はUTCの`expiresAt`/`requestedAt`を直接出力していた。いずれも初期ページ表示だけ9時間早くなり、`ExerciseTree`など後続応答のJST表示と不一致となる。
- 対応: Entityにnull安全なJST表示getterを追加し、両JSPを表示getter参照に変更。回帰テストでUTC時刻の変換値を固定して検証。生徒の隔離fixtureに既知UTCの実行/ごみ箱時刻を、教師の隔離fixtureに既知UTCのプレビュー期限/再評価履歴をseedし、Playwrightで描画済み画面の値を確認するよう追加した。promptの時刻fixtureはprompt queueの既存生成対象とは別の合成課題に隔離し、試験フローの独立性を維持した。
- 初回失敗と修正: 1) `TeacherTaskDatabaseTest`のfixtureにファイル出力を追加後、初回コンパイルで`IOException`未宣言となりexit 1。fixtureテストのthrows宣言へIOExceptionを加えた。2) 最初の生徒browser試行では選択演習/entry IDを渡さずHTTP 400、続く試行はブラウザーcontainer外の`page.request`が`student.localhost`を解決できず失敗。ページ内DOMの実IDを使うよう直し、成功。3) 最初のprompt試行でpreviewを既存生成対象課題にseedしたため、後続の既存UIフローが404。fixtureを別の合成課題へ分離し修正。4) 一度、fixture IDを既存課題IDと誤比較しPlaywright assertion失敗。比較を除きfixture用途別IDとして扱うよう修正。これらはいずれも検証fixture/テスト導線の問題で、製品コードの別障害ではない。
- 再検証: `sh scripts/testing/student-exercise-layout-local.sh` exit 0。DB fixture 1件成功、Playwright 3件成功/失敗0。initial JSPから選択済みファイルを開き、既知UTC `2026-10-04 06:00`の実行を`2026-10-04 15:00`、UTC `2026-10-04 07:00`の削除日時を`2026-10-04 16:00`と表示することを確認。`sh scripts/testing/teacher-prompt-generation-local.sh all` exit 0。課題DBテスト2件成功、Playwright 2件成功/失敗0。実prompt.jsp上のUTC preview期限`2026-10-18 06:00`が`2026-10-18T15:00`、履歴日時UTC`2026-10-16 07:00`が`2026-10-16T16:00`となり、従来のprompt generation/課題日時browserフローも成功。外部AIではなく合成providerを使用。
- 全体検証: `docker compose run --rm --no-deps app gradle build --warning-mode all --no-daemon` exit 0。XML集計151ファイル、571 tests、381 passed、0 failed、0 errors、190 skipped。skipは成功として数えない。3 runnerの`sh -n`と`git diff --check`もexit 0。隔離browser用container/networkの残存なし。Compose orphan `programming-process-evaluator-app-live`とGradle `WarPluginConvention` deprecation警告は既存状態として保持し、変更していない。
- 未確認/境界: 変更一式は作業ツリーにあり、未commit・未PR・未本番反映。全縦断利用者受入、初期設定の全量再現、本番更新/backup/復帰・本番browser受入は別ゲートのまま。skip 190件を合格とは扱わない。

## 2026-10-17 05:05 JST 配信予約日時ブラウザー受入fixtureの版不一致と解消

- 対象・期待結果: UTC保存/JST表示の配信予約について、隔離MySQL/Tomcat/Playwrightで予約日時を表示し、変更確認後の保存・再読込まで検証する。合成データのみを使い、本番・共有DBには触れない。
- 初回実行: `sh scripts/testing/teacher-distribution-ui-local.sh`。migration/validateおよび配信DB統合テストは成功したが、browser fixture生成テストが1件失敗し、スクリプトはexit 1。例外は`IllegalArgumentException: テンプレートが更新またはアーカイブされています。再読み込みしてください。`。即時配信後にテンプレート版が進んだのに、fixture追加予約で古いversionを渡していた。
- 影響・対応: 製品コードやDBの問題ではなく、追加した合成fixtureの版取得漏れ。fixture生成前に現在のテンプレートversionを再読込し、そのversionで予約を作るようテストを修正した。失敗した隔離環境はスクリプトのcleanupで破棄され、共有/本番データの変更はない。
- 再検証: 2回目の同スクリプトはexit 0。専用DBの予約配信統合テスト1件とbrowser fixtureテスト1件が成功し、Playwright 1件成功/失敗0。教師ログイン・同一校教師の編集・別校拒否に加え、予約日時のJST初期表示、2030-05-01 12:30への変更、確認後の保存と画面再読込後の値を確認した。隔離container/networkの残存なし。
- 全体回帰: `docker compose run --rm --no-deps app gradle test --no-daemon` exit 0、150 XML files、569 tests、379 passed、0 failures、0 errors、190 skipped。skipは成功に含めない。`git diff --check`と`sh -n scripts/testing/teacher-distribution-ui-local.sh`もexit 0。Composeの既存orphan container `programming-process-evaluator-app-live`警告とGradle 9.0向けdeprecation warningが出たが、いずれも変更・削除していない。
- 追加build: `docker compose run --rm --no-deps app gradle build --warning-mode all --no-daemon` exit 0、WARを含むGradle `build`成功。`WarPluginConvention`のGradle 9.0 deprecationと同じorphan container警告が出たが、既存設定・containerは変更していない。
- 未確認: 本番反映、初期設定の全量再現性、R-05の全縦断/利用者受入、skipした任意DB系ケースはこの検証に含まれない。UTC/JST変更セットは未レビュー・未commitであり、本番反映承認や完了を意味しない。
- 追補 05:10 JST: `sh scripts/testing/teacher-prompt-generation-local.sh all`もexit 0。隔離MySQLの`extendsExpiredAssignmentAndAddsAClassWithoutChangingExistingHistory`とbrowser fixture生成が各1件成功し、Playwrightのprompt queueと課題日時入力/保存/再読込が2件成功。後者では同一のJST入力値が画面再読込後も保たれることを確認した。プロンプト生成はモックproviderであり、実外部APIは呼び出していない。隔離container/networkの残存なし。fixture作成runの`flywayMigrate`は、既存procedure dropのMySQL 1305通知を出したが、migration/test/WARは成功し、`flywayValidate`の対象範囲は別のdistribution runnerで成功を確認した。

## 2026-10-17 03:22 JST UTC/JST変更後の全体テスト失敗と解消

- 対象・期待結果: UTC保存/JST入力・表示への変更後、`docker compose run --rm --no-deps app gradle test --no-daemon`で全ユニット・DB関連テストを実行し、変更された日時契約を含むテストが成功する。
- 初回実行・実際: 567件中3件失敗、189件skip。`ExerciseTreeTest`の2件はUTC値を画面表示として期待し、`TeacherTaskServletTest`の1件はJST入力値をUTC保存値として期待していた。実装の変換契約とテストの旧期待値が一致していなかった。
- 対応: 演習表示の期待値をJSTへ、課題期限入力の期待値をUTCへ更新した。追加確認で教師演習詳細APIの日時シリアライズ、課題監査詳細、および配信日時変更履歴もUTC値をそのまま利用者へ返す箇所を見つけ、JST表示に統一した。配信履歴DBテストの期待値も同じ表示契約へ更新した。
- 再検証: 2026-10-17 03:21 JSTに上記の全体Gradleテストを再実行し、BUILD SUCCESSFUL。XML集計は148ファイル、567 tests、0 failures、0 errors、189 skipped。03:22 JSTの`git diff --check`も成功。
- 影響・未確認: skipは成功件数に含めない。Composeの`app`サービスは停止しており、当該runは`--no-deps`でDBを使わず、実DB統合テスト・migration・ブラウザー受入はこの実行では確認していない。Gradle 9.0互換性に関する既存deprecation warningが出ている。テスト初回失敗の正確な実行時刻はその実行ログに残っていないため、本記録の日時は再検証結果の記録時刻とする。

## 2026-10-17 01:28 JST ローカル初期設定インベントリの未適用V34テーブル参照

- 対象・期待結果: Docker ComposeのローカルMySQLで確定初期設定の件数・版をread-only集計し、V34共通プロンプト設定の存在も確認する。データ/schema変更や秘密値の出力はしない。
- 実行・実際: 二つの集計コマンドが`school_common_prompt_versions`を直接参照し、MySQL `ERROR 1146 (42S02)`、exit 1。先行SELECT結果ではロール別users、schools/classes/memberships、rubric、surveyの各件数が出力されたが、コマンド全体を成功扱いしない。
- 影響・原因: 現在のComposeローカルDBに`school_common_prompt_versions`表がなく、V34未適用であることと整合する。2回目も`information_schema`で存在確認した後に限った照会へ分割せず、存在しない表を再参照したため、同じ調査欠落を繰り返した。DBのデータ変更は発生していない。
- 対応・再検証: まず`flyway_schema_history`と`information_schema.tables`だけを問い合わせ、適用済みschemaを確定する。その後は存在が確認できた表だけを別クエリで集計する。V34を必要に応じて適用する場合は、現在の合成データ・対象schemaを再確認し、読取調査と分けて記録する。秘密値・パスワード・回答本文は取得しない。

## 2026-10-17 00:05〜00:07 JST H-04 時刻契約の読み取り調査

- 対象・期待結果: ユーザーが選択したread-only範囲で、main `41228ca...`のOS/実行中JVM/DB/接続/日時変換を照合し、設定値と未確認範囲を根拠付きで記録する。修正・restart・業務データ更新・外部AI・worker手動起動は行わない。
- 手順・結果: private `h04-production-clock-readback.sh`を既存SSM wrapperで実行、exit 0。host/container UTC、実行中JVM `Etc/UTC`、JDBC `serverTimezone=UTC`、MySQL global/session `SYSTEM` / system `UTC` / offset0、実pool接続3/3件のtimezone、対象日時列のDATETIME、定期backupの明示`Asia/Tokyo`を取得した。本番サービス/config/state/DB/Volumeを保持。
- 検証器の不足と対応: `timedatectl show --property=Timezone,NTPSynchronized,NTP,TimeUSec`が当該hostでは空出力/exit 0となった。これを同期確認の成功とは扱わず、複数の`--property`指定と必須keyの存在検証で当該OS項目だけ補完し、exit 0。00:07:01 JSTに`Timezone=Etc/UTC` / `NTP=yes` / `NTPSynchronized=yes`を確認した。
- 判定・影響: 時計同期障害は確認されない。一方、timezoneを持たない画面入力/LocalDateTime/DATETIMEと直接format、JVM nowとDB nowの混在があり、全体のUTC保存/日本時間入出力の統一は未対応。実レコードの予定日時や利用者端末のtimezoneは取得せず、実予約が何時間ずれているかの断定はしていない。
- 未解決・次の対応: UTC保存/日本時間入出力を推奨案として記録したが、方針・影響範囲・既存データの扱いを先に合意する。timezoneだけの変更、一括9時間補正、実装/本番再反映は自動着手しない。詳細・コード根拠は[H-04調査結果](./production-deployment.md#h-04-調査結果2026-10-17-00050007-jst)を参照する。今回のデプロイPASSは維持し、機能テスト/buildの新規成功件数は計上しない。
- 同日ユーザー確認追補: UTC保存/比較・日本時間（Asia/Tokyo）の入力/表示と、本番のデモ履歴は継承不要・確定済み初期設定の保全/再投入検証後に再作成可能とする方針を合意。[第181版](../function-specification.md#第181版-日時の統一方針)等へ反映した。上記の方針未合意は調査時点の履歴。本番の初期化/更新・資格情報変更の実行は対象/手順提示後に別承認とし、コード/設定/保存値/既存鍵は変更していない。

## 2026-10-16 15:07〜15:09 JST 最終ECRイメージの隔離起動初回

- 対象・手順: ECR run `38856040632`の最終7imageをdigest指定でPullし、config Image ID/revision/linux amd64と必要ファイルの権限監査は7/7成功。専用project `ppe-ecr-acceptance-20261016`、合成Secrets/TLS、空の専用MySQLで本番Composeを隔離起動した。
- 期待結果と実際: `run-runtime.sh`はbrokerのhealth待ちでexit 1。DBはhealthy、broker自体はrunningだがhealth probeが3秒timeoutを5回記録した。migration・fixture・ブラウザーは未実行。失敗ログと専用資源を保持して診断した。
- 原因・対応: 同じbrokerへ同じPython/urllib probeを手動実行すると`OK`、exit 0、実測5.17秒。Mac ARM上の最終amd64イメージのエミュレーションでは、Python起動込みの3秒制限を満たさない。アプリの不具合とは断定せず、ローカル受入だけbroker/runnerのprobe timeoutを15秒へ変更して再試行する。本番Compose・最終image・CPU/メモリー制約・probe内容は変更しない。
- 未確認事項: native amd64本番での元の3秒health thresholdは更新前後に別確認する。隔離起動の成功を元の時間閾値の合格とは扱わない。MySQLのNative AIO非対応警告もエミュレーション環境に記録され、フォールバック後にhealthyとなった。
- 2回目はV1〜V34のmigrate/validate/info、合成fixture、最終5サービスのhealthyまで成功。続く初回HTTPS login取得が15秒timeout（curl exit 28）となり、ブラウザーは未実行。診断時の同URLはHTTP 200、TLS 0.075秒、全体0.105秒で取得できた。初回JSP生成・エミュレーション負荷の影響が考えられるが直接原因は断定しない。検証側の初回GETだけ90秒上限・proxy不使用を明示し、同じDB/fixtureを保持してUI検証から再開する。image・本番のtimeout設定は変更しない。
- nginxにはエミュレーション上の`io_setup() failed (38: Function not implemented)`も記録された。プロセスとHTTPS応答は稼働しているが、この警告のないnative環境と同等とは扱わず、本番更新前後のnative確認に残す。
- UI初回は既存3件（教師認証/権限拒否、演習行配置、ログアウト取消focus）が成功、新規保存/実行試験1件が90秒timeoutで失敗、exit 1。保存自体は成功していたが、新規testの`waitForResponse`が操作名をPOST bodyで探しており、実契約の`/student/exercise/save` URLを見ていなかった。待機条件を正確なPOST pathへ修正し、同じ専用データの保存再読込・実行試験だけ再実行する。初回失敗のreport/traceを保持し、既存3件を無変更で再実装しない。
- 保存test修正後は保存/再読込が成功したが、Python実行が`Docker broker policy denied the request`、Runner HTTP 503 `runner_unavailable`で失敗し、test 0 passed / 1 failed、exit 1。最終image内のbrokerへ、拒否されたfield名のみを記録して同じpolicyを再throwする一時診断を付け、合成コードで再現したところ`required_mismatches=["Cmd"]`、missing/unknown/default不一致0となった。
- 根本原因: Runnerの`EXECUTION_WRAPPER`は入力prompt非表示対応を含む新しい固定commandだが、brokerの`COMMAND`は旧wrapperのまま。通常実行も厳密allowlistで拒否される。さらにprompt非表示実行ではRunnerが追加する`PPE_SUPPRESS_INPUT_PROMPT=1`もbrokerのEnv2件限定に対応していない。policyを緩めず固定command/限定flagの契約と両者の回帰testを修正する必要がある。**acfcd60版は本番反映不可（最終runtime FAIL）**。実image受入で検出したため本番は旧a6版を維持し、無条件retryや失敗したreleaseの再利用はしない。
- 修正前のcross-contract unittestは10件中8 passed / 2 errors、exit 1で通常実行と提出前チェックの両方を再現した。VS Code test toolではPython testが検出されなかったため、既存Python 3.12 image/標準unittestを使用。修正後の最初の`test_*.py` selectorは、秘密tokenを必要とする別の実環境専用4件までunit環境で選択し、24 passed / 4 errors（`PYTHON_RUNNER_TOKEN_FILE`未設定）、exit 1となった。unitは既存`test_runner.py`/`test_broker.py`を明示し、実環境専用は合成tokenを設定した専用Runnerで別実行する。skipやsecretの偽造で成功扱いしない。
- 修正後の正確なunit selectorはRunner 24/24、broker 10/10成功、skip/failure/error 0、exit 0。Pylanceによる変更Python 3ファイルの構文確認も成功。修正brokerのローカルamd64 imageと公開済み最終Runnerを専用環境で接続し、`test_production_runtime.py`の認証/サイズ、通常prompt維持と提出前prompt抑制、正常実行/実エラー/出力上限/非root/CPU/メモリー/ネットワーク/ファイルシステム隔離の3件を実行し、3/3成功、exit 0（6.568秒）。これはソース修正の受入であり、新しい固定ECR imageの受入ではない。
- nginx失敗復帰の追加回帰: `deployment-recovery-test.sh`は60 assertion groups成功、exit 0。migration後の`before-nginx`/`after-nginx`で`nginx_started`、自動復帰なし、old→oldのCURRENT履歴referenceを使った明示rollback、DB lifecycle/Volume変更なし、config同期を確認。Docker/AWSをfake化した状態機械回帰であり、本番完全DRや実rollbackの再実行ではない。
- 16:05 JST、修正版main `41228ca...`のCI `38862737820`、ECR公開`38863619749`、7image固定照合後の隔離受入で、古い専用browser containerの名前が残り`docker run`がexit 125。新しいDBのV1〜V34、5healthy、合成実Runner 3/3件、両HTTPS login 200までは成功したがbrowserは未実行。旧browserはCompose project labelがない別`docker run`資源のため`compose down --remove-orphans`の対象外だった。ID `95cd11fe8fbd...`がexited、専用acceptance label一致と確認し、この1個だけを削除して同じ新DB/fixtureのUI段階から再開する。初回失敗logは保持し、全体を成功扱いしない。本番は未変更。
- browser名残の除去後は同じ新DB/fixtureから4/4件成功、exit 0。保存1件/成功実行1件、V34/failed migration 0/`log_bin_trust_function_creators=0`、app/nginx再作成前後のSQLとDB ID/image/StartedAt/Volume一致を確認。固定7imageはEC2にも取得してREADY作成済み。取得前後の既存5サービスID/開始時刻/restart/health、DB Volume、current/state、backup/TLS config hashは一致。本番サービス切替・migrationは行っていない。
- 16:32 JST、private SSM実行wrapperの完了観測に2件の欠陥を確認。readinessの`docker compose run`が親Bashのheredoc stdinを消費し、inventory後で処理が終わったのにwrapperはexit 0を出した。予定したbackup/復帰確認等のmarkerがないため前点検合格とは扱わない。またnative専用5test（71.960秒、5/5成功）と元3秒health、nginx TLS設定/起動/health、専用資源cleanupは成功したが、内側のEXIT trap解除がwrapperの完了markerまで消し、SSMが終了待ちのまま残った。payloadをstdin `/dev/null`のsubshellへ隔離し、外側の終了status/trapを維持する修正を行う。元証拠は残して自己test・前点検再実行・native既存結果の確認を行う。
- 終了待ちの自分のSSM sessionだけを`ppe-deployer`で`terminate-session`しようとしたが`AccessDeniedException`。IAMやroleを変更して回避しない。元wrapperの上限時のexit処理を待つ。nativeのbrokerには限定policy拒否warning 12件があるがTraceback 0、実実行/対話/同時実行/timeout等5testは成功。ローカルのBrokenPipe 1件（Traceback行2件）と区別して記録し、policyを緩和しない。
- wrapperをstdin `/dev/null`のsubshell実行に直し、自己testでstdin非公開と内側EXIT trap/外側status両立を確認、exit 0。前点検v2は最終toolsのFlyway validate/info、適用33/pending V34のみ、固定S3 3 Version/サイズ/SHA256/KMS/鮮度（52,308秒）、既存recovery reference、DB/Volumeと運用config/state維持を全marker/exit 0で確認した。nativeの初回監視は上限時exit 1として記録し、修正版wrapperで既存5testの`OK`、元3秒probe、nginx設定/起動log、5旧サービス/DB/Volume/config/state一致、専用container/child/network/token cleanupを再確認してexit 0。core testを無駄に再実行して観測失敗を隠していない。
- 16:38 JST、ローカルの専用browser/5サービス/network/Volume、今回だけのbroker source確認image、3検証rootの既知の合成Secrets/TLSを対象限定で削除。共有開発3container・ECR公開資産・暗号化鍵は維持。本番はa6/V33のままで、更新直前の明示承認待ち。
- 本番更新承認後の最終guardはAWS SSO token expired/refresh failedで接続前にexit 1。17:48 JSTに既存`ppe-deployer`のbrowser認証を開始したが当該CLI待機はexit 255となった。ユーザーの「認証完了」後、別途STSで既定account/roleを確認し、guard v2でcurrent a6/V33・failed 0、外部評価queue/preview 0、active school 1、fixture衝突0、旧manifest/運用config/既存backup receiptの安全なarchive可否までexit 0で再確認した。SSO失敗を認証成功と読み替えず、旧本番を止める前に解消した。
- 2026-10-16 JST（元terminalは時分未記録、19:59 JSTに記録）、41228ca/V34の切替成功後、合成受入fixtureを準備するprivate Ruby生成器が、日本語の既存フォルダ名を含むJava sourceをUS-ASCIIとして走査して`invalid byte sequence in US-ASCII`、exit 1。SSM接続より前に停止し、この失敗で本番DBへのfixture追加はない。生成済みの合成passwordを再生成せず、UTF-8を明示し、既存0600/0700資産だけを検証してrenderを再開した。Java fixtureは最終tools imageのoffline javac、browser 4ファイルは既存Nodeの構文確認に先行成功済み。
- 2026-10-16 20:15:57 JST終了の再開処理で、UTF-8修正と既存private資産照合後のrenderは成功。本番fixture createは2合成accountのtransaction commit後、演習準備で`Active student authentication is required`、exit 1となった。学校方針を適用するV11のINSERT triggerが明示入力より優先して初回password変更必須にしたため。既存学校・ユーザー・passwordは変更せず、保存済みfixture ID/login・既存account集約hash・演習未作成を検証し、当該合成profileだけを初回処理完了へ更新して演習準備から再開した。アプリsource/migrationの不具合ではなく、private fixtureの前提漏れとして記録する。アカウントの再作成・秘密値の再生成はしていない。
- 同fixtureの再開はoffline compile、ID/login/hash/未作成検証、実演習準備、本番Runnerのprompt維持/提出前抑制1 testでexit 0。公開HTTPSのPlaywrightは正常2/2件（19.6秒）と停止後2/2件（6.9秒）、各exit 0、failed/skip 0。所有コード・成功実行のDB読戻し、既存account集約hash不変と合成2accountの停止もexit 0で確認した。
- 2026-10-16 20:34 JST、最終read-only inventoryが「project全containerが5件」という検証側の過剰条件でexit 1。実際には稼働中5サービスが全てhealthyで、8日前に正常終了した既存`acme-bootstrap`が第6のcontainerとして残っていた。対象一覧を読取確認し、この既知の終了0 bootstrapを明示区別する。bootstrapを削除せず、core5サービスのimage/health/restart・DB/Volume・backup等の検証を再実行する。
- 続く20:35 JSTのinventoryはnginx image identityでexit 1。本番containerd storeの`.Image`は公開manifest digestであり、検証器がconfig digestと比較していた。実際の4サービスは固定release JSONのmanifest digestと全て一致し、restart 0/healthy。既存`prepare-ecr-release.sh`もこのstore差を区別して取得時にconfig blobまで照合済みである。最終検証は当該daemonでの正しいmanifest identityを使い、既存DBは以前の固定imageを維持する。imageの再取得・タグ変更・サービス再作成は行わない。
- 20:38:10 JST、修正した最終read-only確認はexit 0。5サービスの正しい固定image/healthy/restart 0、DB/Volume維持、V34/failed 0/trust 0、queue/preview 0、合成2停止、運用config同期、fresh deployment backupの固定3 S3 Version/size/hash/KMS/remote manifest、両HTTPS health/login、3timer enabled/active/waiting、直近3service success/0、4サービス重大log 0を確認した。
- 20:39 JSTのEC2秘密資産cleanupは2合成passwordの削除後、既存DB secretの存在確認で実ファイル名`db-password`を誤って`db_password`と指定してexit 1。削除対象は専用work内の2fileだけで既存secretには操作していない。後続は再削除せず、正しい既存secretと2file不在・専用container不在のread-only確認で受け入れる。ローカル6秘密fileの限定削除はexit 0、予期しない秘密値コピー0。
- 20:40:36 JSTのEC2 read-only再確認はexit 0、2password不在、既存DB secret維持、専用container不在、DB ID不変。清掃の途中exit 1を隠さず、最終確認の成功をもって今回の最小デプロイ/合成受入/清掃を完了とする。既存accountのpassword・鍵/IAMは変更せず、追加Gemini要求・rollback・DB復元は行っていない。独立offline鍵保管、正式品質/手動E2E、全OS、本番完全DRは別ゲート。

### 2026-10-16 15:12 JST 本番read-onlyのoperations探索範囲

- `production-release-assets-readonly.sh`は現行a6版operationsの配下に` scripts/ci`もあることを要求し、`find: scripts/ci: No such file or directory`、exit 1で停止した。現行a6版の配布は本番操作用の` scripts/production`のみであり、ECR取得用一式が配置済みの869版とは構成が異なる。
- 本番サービス・DB・設定は変更していない。a6版はdeploy/rollback用のbytes、869版はECR取得用のbytesを分けて確認するよう読取scriptを修正した。どちらもローカルの固定リリースsourceと照合するまで実行に採用しない。
- 再読取はexit 0。ローカル比較の初回は、installed a6版の追加`deploy-verbose.sh`がrepositoryに存在しないためENOENTで停止した。この追加wrapperは実行しない。必要な正規helper群の存在とbytes一致を個別に確認し、追加ファイルの存在を正規helperの改変や照合成功に読み替えない。
- helper全量比較では869版のdeploy/rollback/config同期3件が現行sourceと不一致で停止。用途を分離し、a6版の正規28helperは一致、869版のECR取得entry/source/CI共通3件も一致を確認した。deploy/rollbackはa6版を明示指定し、869版の古いdeploy/rollback/config同期と追加verbose wrapperは使わない。ECR取得helperはこれらの古い3件をsource/実行しないことを読んで確認した。

## 2026-10-16 14:35〜15:03 JST ECR公開の観測中断とローカルDocker停止

- 対象・手順: 固定main `acfcd60c9eb9800275057ac24a0f5bc6d2da7114`のpush/main CI `38855211776`成功後、14:29:25 JSTに既存ECR workflowを1回だけ起動した（HTTP 204、run `38856040632` attempt 1）。本番操作・migrationは行っていない。
- 期待結果と実際: 待機helperは14:35:38 JSTまでrunを`in_progress`と観測した後、`GitHub API transport failed`でexit 1。これは公開job自体の失敗判定ではないため再dispatchしなかった。15:02:58〜15:03:12 JSTに同runをGETで再確認し、14:36:57 JSTにcompleted/success、build/publishの両job・固定SHA・attempt 1の一致を確認した。通信失敗の直接原因は未確定。
- ローカル受入の前提確認: 15:03 JSTの`docker ps`はdaemon接続不可でexit 1。`docker context ls`と`docker desktop status`で既存Desktopの停止を確認し、`docker desktop start`はexit 0。15:04 JSTの`docker info`はserver 29.8.1/overlay2で応答し、既存Runner/DB等3containerの稼働を再確認した。共有8080のappはExited(255)であり、今回の専用受入に不要なため勝手に再起動しない。最終image受入は後続で行う。共有container/Volumeの削除、prune、daemon設定変更はしていない。
- 影響範囲・未解決事項: 公開の観測とローカル検証環境のみ。本番更新は未実行。公開成功を最終imageの起動受入や本番デプロイ成功とは扱わない。

## 2026-10-16 14:10 JST 最小デプロイ準備のGitHub保護確認

- 対象・手順: リリース候補PR #60の統合前に、GitHub APIで`main`の保護条件を確認した。`GET /repos/toichan/programming-process-evaluator/branches/main`はHTTP 200、`protected=true`。
- 期待結果と実際: 続く従来の`GET /branches/main/protection`は保護設定のHTTP 200を想定したが、HTTP 404、`Branch not protected`、helper exit 1となった。これを保護なし・統合可能とは扱わず停止し、適用中のruleset用APIへ切り替えた。
- 対応・再検証: 14:10:38 JSTの`GET /repos/toichan/programming-process-evaluator/rules/branches/main`はHTTP 200、exit 0。PR必須、厳密な最新baseへの追従、Java/Shellの必須2check、thread解決、非fast-forward/削除禁止を確認した。従来APIの404だけでruleset保護を判定できないため、今後も有効ruleを確認する。
- 影響範囲・未解決事項: 読取経路の不一致のみで解消。保護設定の緩和・bypass・本番変更は行っていない。元の`code`は内容差分なしで`main`をmergeして通常pushし、PR側の必須check成功を待つ。認証情報は記録していない。
- 14:20 JSTの統合直前guard: `main`が元の`7122540...`と一致することを要求したところ、既にPR #60が14:19:52 JSTに`toichan`によりmerge済みで、`acfcd60c9eb9800275057ac24a0f5bc6d2da7114`となっておりhelperはexit 1で停止した。こちらからmerge PUTは送信していない。通常fetch後、統合先・PR元`47ce55d...`・受入済み元`ee3c110...`のGit treeが全て`f59883460252b33327c033bb4b4e38173668f0aa`と一致し、差分0であることを確認。別変更の混入ではないため、この統合済みmain SHAのpush/main CIを待つ。保護の回避・上書き・二重mergeは行わない。

## 2026-10-16 07:36 JST 未解決課題の受入テスト初回実行

- 対象: V-01〜V-04、F-02、T-01/T-02。共有8080と既存DB・他projectのcontainerは変更せず、専用MySQL/Tomcat/Playwrightを使用。
- 修正前の隔離Gradle `clean build --warning-mode all` はexit 0、145 suites / 556 tests（369 passed、187 skipped、failures/errors 0）。前回記録の552件とは異なる現行ソースの基準線であり、skipは成功扱いしない。
- `sh scripts/testing/teacher-submission-review-local.sh acceptance` はexit 0、Playwright 3/3成功。進捗の学校境界とコード再読込、提出/評価の既存学校境界、評価詳細の理由3フィルターと削除対象セクションの非表示を確認した。
- `bash scripts/production/tests/nginx-startup-test.sh` はexit 0、7 PASS。既に存在する`chmod 0644`修正を、mode 0600の元configから構築したローカル固定image ID `sha256:7a4a0aa409bfae47337d71a6db22e4bc6093e9c0eafb01006b923076bad33cac`で検証。UID/GID 101:101の読取、TLS proxy、reload、ローカルdeploy/rollback再作成を確認。本番ECRの固定digest受入・再deployとは区別する。
- 失敗1: `sh scripts/testing/student-exercise-layout-local.sh` はexit 1、Playwright 2 passed / 1 failed。追加したログアウト試験が閉じたユーザーメニュー内のbuttonを直接clickし、50秒timeoutとなった。これはテスト操作の欠落であり、現時点ではfocus不具合の再現証拠ではない。通常のメニュー展開操作を追加して再実行する。
- 失敗2: `sh scripts/testing/teacher-prompt-generation-local.sh` はexit 1、専用Tomcatのreadiness判定に失敗しbrowserは未実行。WAR起動は成功しているが、`PPE_ENV=simulation`ではHTTPS必須のため直接HTTPのprobeが拒否された。simulationの制約は緩和せず、専用TLS proxyと正確なtrusted proxy IPを構成して再実行する。
- 失敗時のJUnit XML、browser trace、runtime logは停止containerから`docker cp`で回収できた。実行ログは`build/error-resolution-validation/`、各runnerの個別報告directoryに保存。上記2件の再検証結果は後続へ追記する。
- ユーザーは今回の課題解決に伴うGemini呼出しも許可した。再診断は実生徒データを含まない合成候補要求だけとし、既定モデル・採用・fallbackは変更しない。

### 2026-10-16 07:40〜07:48 JST 再診断・取消フォーカスの再現

- Gemini: ユーザーの許可を受け、モデル一覧のGETと`gemini-2.5-pro`への合成Interactions structured要求1件（自動retryなし、`store=false`）を実行。`GeminiApiDiagnosticSupportTest`2件と一覧GETは成功、候補要求はHTTP 404、全4件中3 passed / 1 failed、exit 1。`provider_code=not_found`、応答は「This model models/gemini-2.5-pro is no longer available to new users. Please update your code to use models/gemini-3.1-pro-preview for the latest features and improvements.」。モデル一覧には名前が残っているが、この認証情報でのInteractions利用可否を意味しない。request ID / Retry-Afterは応答に無いため補完しない。停止containerからJUnitと診断JSONを`build/error-resolution-validation/gemini/`へ回収できた。
- F-01の原因は候補モデルの利用制約と判明。既存のUI候補・既定に2.5 Proは含まれていないため本実装のモデル・fallbackは変更しない。診断の失敗を成功/skipへ変更せず、2.5 Proを採用可能な候補として扱わない。再採用を依頼する場合はアクセス資格・提供状況から再判断する。
- 演習の2回目: `sh scripts/testing/student-exercise-layout-local.sh` はexit 1、1 passed / 2 failed。メニューを通常展開した結果、取消後にユーザーメニューの起動元へfocusが戻らないことを再現した。submit直後のbutton無効化で共通確認が起動元を取得できず、メニュー閉鎖後のfallbackも主領域へ移る構成だった。重複submitの排他を維持しつつ無効化を確定後へ移し、shared feedbackで同じdropdownの可視トグルへ復帰させる。
- 同じ演習runで既存の行配置試験も失敗（中心位置の差1.2548828125px、期待1px以下）。viewport変更直後に別々のbrowser呼出しで各座標を測っていたため、同一DOM評価内の測定と条件到達待ちへ変更する。閾値1pxは緩めず、再検証する。
- プロンプトの2回目: `sh scripts/testing/teacher-prompt-generation-local.sh` はexit 1、browser未実行。非root/read-onlyの専用nginxで`/var/cache/nginx/fastcgi_temp`の作成が拒否された。通常proxyだけでなくfastcgi/uwsgi/scgiの一時directoryも専用`/tmp`へ指定し、proxyの生存確認とHTTP status証跡を追加する。アプリのHTTPS必須条件は維持する。

### 2026-10-16 07:52 JST テスト手順の追補とsurvey受入

- 演習3回目はexit 1、1 passed / 2 failed。取消のfocus復帰は先へ進んだが、Escapeをモーダルの表示アニメーション中に送ったため閉鎖判定に失敗した。Bootstrapのfocus trapがモーダルへfocusを移すまで待ってからキーボード操作を行う。座標の同時測定では非表示dropdown内の5ボタンも取得していたため、可視の3操作へ限定する。閾値1pxと3操作の検査は維持する。
- プロンプト3回目はHTTPS経由のlogin表示まで成功し、browserを実行。fixture教師は許可された機能に応じ`/teacher/task`へ遷移するが、新試験が`/teacher/home`を期待して失敗した（1 failed、exit 1）。実装のログイン先契約に合わせて期待と一時離脱先を課題一覧へ修正する。
- `sh scripts/testing/teacher-survey-review-local.sh integration` はexit 0。既存review fixtureへのsurvey追加、再seed時の非重複、不完全fixtureの拒否、既存の同意・学校境界・export・実HTTPの試験が成功した。surveyとreviewの全suiteを無条件に同居させる契約へは拡張せず、schema分離を維持する。
- 07:54〜07:56 JST: 演習4回目はexit 0、fixture JUnit 1/1、Playwright 3/3成功。取消・Escape・二段階確認の取消でコード/セッション保持と可視メニュートグルへのfocus復帰を確認し、キーボードによる再操作、重複submit抑止、確定ログアウト/失効後の保護画面拒否も成功。狭幅の行配置は1px以下の元条件で成功した。今回の代表フローでは`aria-hidden`/focus警告なし。
- 同時実行したプロンプト4回目はexit 1、browser 1 failed。揺らぎ生成・polling・離脱/再訪・表示までは成功したが、教師対応の入力後に必要な「揺らぎ項目指示を保存」を押さず、無効な評価例生成buttonを待って90秒timeoutとなった。既存の保存→生成契約にテストを合わせ、保存後の再読込とbutton有効化を検査して再実行する。合成providerの呼出しは揺らぎ1件のみで、実APIへの接続はない。

## 2026-10-16 08:09 JST ローカル修正の最終受入

- `sh scripts/testing/teacher-prompt-generation-local.sh`: exit 0、fixture JUnit 1/1、Playwright 1/1。専用HTTPS proxy、simulation制約下の合成provider、揺らぎ/評価例の各非同期生成、状態polling、課題一覧への離脱/再訪、完全な合成ログ/評価JSON表示、明示保存後の再読込を確認した。providerは合成2要求のみ。DB read-backは`configured / completed / completed / active / 2`で一致した。証跡は`build/teacher-prompt-generation-validation/run-77003/`。
- `sh scripts/testing/teacher-distribution-ui-local.sh`: exit 0、fixture JUnit 1/1、Playwright 1/1。配信履歴からテンプレートを開き、1280px/390pxで三角記号`▾`、折畳時の-90度回転、展開/折畳とキーボード操作を確認。同一校peerによる更新とownerの再読込、履歴snapshot保持、別校の直接URL拒否も成功。証跡は`build/teacher-distribution-ui-validation/run-76997/`。
- 共通feedback変更後の`sh scripts/testing/teacher-submission-review-local.sh acceptance`: exit 0、JUnit 9 suites / 28 tests（26 passed、2 skipped）、認証HTTP 1/1、Playwright 3/3成功。証跡は`build/teacher-submission-review-validation/acceptance/run-77628/`。
- surveyの正確な件数はJUnit 5 suites / 8 tests（7 passed、1 gated runtime skip）、別の認証HTTP 1/1、exit 0。演習はfixture 1/1、Playwright 3/3、exit 0。今回の最終browser受入は合計8論理ケースであり、中間の失敗runや再実行を成功件数に加算していない。
- 修正後の隔離`gradle --offline --no-daemon clean build --warning-mode all --console plain`: exit 0、146 suites / 559 tests（371 passed、188 skipped、failures/errors 0）、WAR成功。`build/error-resolution-validation/final/`にJUnitとbuild logを保全。標準buildのskipは外部環境の未実行であり、今回/過去に個別受入したケース以外を合格に含めない。
- 変更したJS/Javaのeditor診断はエラーなし。`sh -n`（変更した4 runner）、`bash -n`（nginx起動試験）、`git diff --check`はexit 0。共有8080、共有DB、共有Python runner、他project DBは停止・再作成せず維持した。プロトタイプ、DB migration、アプリ既定モデル、本番は変更していない。
- 本番固定digest、バックアップ/復旧、正式品質E2E、OS-native、UTC/JST契約、全route/action対応表と依存互換性の残件は未完了のまま。候補2.5 Pro診断のexit 1も保持し、工程20全体をPASSとはしない。
- 08:19 JST文書・集計補正: 最終skip一覧のsuite名抽出が`hostname`属性まで一致していたため、XMLファイル名由来へ変更し、46 suites / 188 skippedと再確認した。JUnit件数・成功判定自体は変化なし。追記文書1行の末尾空白で`git diff --check`がexit 2となったため除去し、再検査exit 0。いずれもアプリの試験失敗ではない。証跡回収済みの停止container 3個と、既知の合成TLS鍵/証明書8ファイルのみを限定清掃した。共有4 containerの稼働を維持し、対象Java/JS 6ファイルのeditor診断エラー0、shell構文検査もexit 0。

## 2026-10-16 08:25 JST 本番の読み取り専用調査を承認

ユーザーが、AWS・本番環境の稼働release/image digest、バックアップ実行記録・S3固定Version・監視値を照合する読み取り専用調査を明示承認した。deploy、バックアップ再実行、IAM変更、DB復元、業務データの内容取得は含めない。結果は後続へ記録し、調査許可を変更/復元の許可へ拡張しない。

- 同日08:25 JST、`AWS_PAGER='' aws sts get-caller-identity --profile ppe-deployer --region ap-northeast-1 --query '{Account:Account,Arn:Arn}' --output json`はexit 255、`Error when retrieving token from sso: Token has expired and refresh failed`。期待したAWS identityを取得できず、本番のinstance/release/receipt/S3/metric読取には進めていない。ローカルのAWS CLIとSSM pluginは存在するが、有効なSSOセッションの再認証が必要。
- IAM拡張、別管理profileへの切替、SSH経路への迂回、秘密値の取得、backup/deploy/復元の実行は行っていない。F-02/H-01は読み取り調査の承認待ちから**SSO再認証待ち**へ移る。再認証後はまず同じidentity確認を再実行し、対象account/instanceを確認してから読取を続ける。
- その後、ユーザーはSSOログイン開始も承認。`aws sso login --profile ppe-deployer`を開始したが、120秒待機後もブラウザーでの本人認証を待っている。ログイン完了・本番調査完了とは扱わず、認証待ちとして引き継ぐ。認証URL/一時コードや資格情報は本書へ保存しない。

## 2026-10-16 本番読取の再開・権限制約と受入結果

- 08:38 JST、ユーザーの認証完了後に同profile/東京のSTS identityを再確認しexit 0。account `024378233912`、EC2 `i-0ffd69e8f390bd396`のrunning/SSM Onlineも確認した。以前開始した認証待ちCLIだけを停止し、SSO資格情報は変更・表示していない。
- `cloudwatch describe-alarms`と`cloudwatch get-metric-statistics`（PPE/Backup / FreshBackup / Project=ppe-production / 直近24時間）は既存roleで各1回AccessDenied、各exit 254。期待した保存監視値を読めず、未確認を維持する。IAM変更・別管理profileへの切替はしていない。
- `ssm send-command`もAccessDeniedでcommand IDなし。複合wrapperの末尾`cat`がexit 0となったが、これはAPI成功ではない。API単独のexit codeは回収できておらず、拒否・未送信として記録する。同roleの既存interactive StartSession経路で読み取りpayloadだけを実行した。
- 08:41〜08:44 JSTの最初のPTY wrapperは、metadataの観測完了後に外側のexit markerを取得できずtimeout/exit 1。payload完了表示だけでwrapperを成功扱いしない。以後はpayload内のEXIT trapで実codeを返し、pagerも明示無効化した。
- 08:46 JSTの最終readonly probeはexit 0。現行currentは`a6e90edf31c5369ea71c2e10efc78d37461423d6`、state published、5サービスhealthy/restarts 0。nginx image `sha256:c0826b1d4675a8815eaa4c42e82b48d9a6416ab3c3d50de70c7ca7836269f046`の設定は0644でUID/GID 101:101の読取成功。両domain `/health`は200。10月11日の旧release記録は過去時点であり、現行本番の読取拒否継続とは扱わない。
- 定期backupはretained journalで10月10〜16日の7開始/7完了・失敗entry 0。最新は16日02:03:01〜02:03:15 JST、success/exit 0とtimer LastTriggerが一致。latest receipt作成02:03:04 JST、current release一致、age 24,208秒で93,600秒以内。3固定Versionのサイズ・SHA-256・checksum・SSE-KMS/keyとローカル暗号化artifact/sidecarが全項目一致し、検証前後のreceipt hash/currentも不変だった。
- 今回はread-onlyのみで、backup再実行・deploy/restart・IAM変更・SQL/復元・S3 body取得・秘密値/業務データ取得は行っていない。独立した監視API読取、業務データ入り復旧、鍵custody、将来のECR固定release受入は未完了。詳細なVersion ID・時刻・境界は[本番調査記録](./production-deployment.md#2026-10-16-本番読み取り調査)を正本とする。

## 2026-10-16 管理プロファイルの監視読取を承認

09:32 JST、ユーザーは、既存`ppe-infra-admin`を使い、同一本番accountの対象アラームと保存メトリクスを読み取ることを明示承認した。09:35 JSTのSTS identityはaccount `024378233912`の`PPE-Infrastructure-Admin` roleで一致、exit 0。IAM・アラーム設定・サービス状態の変更、通知試験は含めない。以下に承認後の読取結果を記録する。

- 09:36 JST、`cloudwatch describe-alarms --alarm-names ppe-production-backup-freshness-alarm`、FreshBackup直近24hとBackupSuccess直近7dの`get-metric-statistics`を同profile/東京で各1回実行し、全てexit 0。アラームはOK/ActionsEnabled=true、承認済み評価条件とSNS action ARNに一致。
- 保存FreshBackupは24連続hourly区間、各SampleCount=1、Minimum=Maximum=1。BackupSuccessは7集計区間すべてSum>0、合計13サンプル。APIは実行種別を区別しないため13回の定期実行とは扱わず、scheduledの根拠は先のsystemd 7回とする。bucket時刻や最終alarm状態遷移時刻も、service実行/今回の読取時刻と混同しない。
- JSON条件/連続性のローカル検査もexit 0。これによりH-01の独立監視readbackは解消。以前の権限エラーはroleを改変せず履歴として残す。alarm起点の実SNS配送、業務DB復旧/アプリ読戻し、鍵custody、将来release受入は未検証。詳細は[本番調査記録](./production-deployment.md#0936-jst-管理プロファイルによる独立監視readback)を参照。
- 記録更新後の`git diff --check`とsession YAMLの構文/集計整合検査はexit 0。VS Code Problemsには文書lint指摘が残る（190件中先頭50件のみ表示、履歴節のMD038/MD060等）。本番調査記録・ロードマップ・ECR負債・2つのYAMLは診断なしだが、全対象のlint cleanとは扱わない。今回の読取/JSON検査の失敗ではなく、履歴文書の無関係な整形は行っていない。アプリコード変更のない本追補で全build/browserの重複実行はしていない。

## 2026-10-16 11:44 JST 工程20終結作業・復旧と通知試験を承認

ユーザーが「次の作業」「復旧、通知試験」を明示承認した。工程20のP20-01〜10と既存証跡を対応付け、対象外だった運用/OS/実採点品質を未検証のまま別ゲートとして分離する。復旧は既存Mac鍵と固定S3 Versionを用いるローカル隔離DBで実施し、稼働DBは上書きしない。通知は既存SNS宛の明示された合成試験と一時的な専用metric/alarmを使い、実FreshBackup/既存alarmのstateや閾値を偽装しない。IAM・recipient変更、実データの外部AI送信は承認に含めない。対象accountはSTSで`024378233912`と再確認、exit 0。

- 初期診断のDocker image表示は`.RepoTags`に`join`を使い、Docker 29の`[]interface{}`と合わずtemplate error。利用可能性の確認方法を`json .RepoTags`へ修正する。同じ診断の`du`も存在しない最終WAR候補パスを含みexit 1。既存review WARの存在を確認し、復旧には最新sourceからのWARを別途生成する。サービスやDBの変更・消失ではない。Mac空きは11GiBで、追加image pull/全体再buildを重複させず既存cacheと小さい専用資源を使う。
- 同日11時台JSTの固定backup復旧準備: `download-backup.sh`で10月16日02:03 JSTの3固定S3 Versionを取得・検証しexit 0。続くhost `age --decrypt ... > /dev/null`は`no identity matched any of the recipients`、exit 1。候補のプロジェクト内既存鍵は0600/本人所有だが、この本番recipientに対応しない。鍵の再生成/書換え/転送、平文dump保存、復元先/稼働DBへの書込みはしていない。正しい本番用age identityの所在確認が必要。暗号化artifactはsessionのprivate directoryに保持し、正しい鍵で認証できるまで実復元は開始しない。
- ユーザーが本番用identityの既存パスを指定。12:15:28 JST、0600/本人所有を確認し、指定鍵による固定ciphertext全体のhost age認証はexit 0。候補鍵の不一致は正しい鍵の指定で解消した。鍵はMacだけに置き、本文の取得/表示・Dockerへのmount・EC2への転送や再生成は行っていない。復元はこの認証済み固定artifactから専用DBへstreamする。
- 通知試験は11:49:46〜11:52:18 JST、専用`PPE/BackupAcceptance` metricと一時alarmでOK→ALARMを実評価。11:51:50 JSTのCloudWatch action Succeeded、既存SNSのDelivered=1/Failed=0を確認、最終exit 0。一時alarm削除後も本番alarmはOK。IAM/topic/recipient・実FreshBackup/本番alarmの設定は変更していない。人間の受信箱での開封確認とは区別する。AWSは個別metric削除APIを持たないため合成datapointは通常retentionで失効し、以降の送信や継続alarmはない。
- 12時台JST、全登録routeの共通認証filter契約を追加した初回buildは561件中1失敗/189 skip、exit 1。テストが「管理者→teacher領域をfilterだけで403」と誤期待していた。実契約はstaff領域をadmin/teacher共通で通し、教師専用handler/Controlでadminを拒否する二段階（既存の実HTTP試験で教師課題403受入済み）。新しい共通filter契約はその責務に合わせ、teacher領域のadminをfilter拒否の対象にしない。製品認可を緩めずテスト側のみ修正した。初回XMLとlogをsessionへ保持し、下記の最終全体buildで再成功を確認した。

## 2026-10-16 12時台 JST 復旧検証の差分解消と最終回帰

以下の失敗は同日12時台JSTの実行。個別ログに分秒の時刻がないものは補完しない。
対象は専用project `ppe-restore-phase20-20261016`のみで、本番DBへの書込みはない。
期待結果は固定backupの復元、現行migrationとの整合、実Control/DAO読戻し、最後の全体回帰成功。

| 手順・失敗 | 原因・影響 | 対応と再検証 |
| --- | --- | --- |
| 最初の全体build後の`flywayValidate`、1045/Access denied、exit 1 | private wrapperが`ppe_migration`という存在しないmigration user名を指定。先行build成功とwrapper全体の失敗を区別する | 実ユーザーmetadataを確認し、既存の正しい`ppe_migrate`を使用。credentialの変更/表示はせず、後述のmigrate/validateはexit 0 |
| app権限での`flywayValidate`、`Detected resolved migration not applied to database: 34.`、exit 1 | 固定dumpはV33で、現行sourceにはV34がある。checksum破損ではなくpending差分。成功を期待したvalidateはこの時点では失敗 | ignore/repairで隠さず、専用復元DBだけへ`flywayMigrate flywayValidate`を正しいmigration権限で実行しexit 0。66→67表、成功履歴33→34、失敗0。users23/tasks2/submissions4/evaluations4/code_logs108を保持 |
| 専用runnerの`.State.Health.Status` inspect probe、exit 1 | image自体にHealth属性がなく、共有構成のhealthはCompose側定義だった。未起動の証拠ではなく、誤ったreadiness確認方法 | private helperを実`GET /health`へ変更。HTTP 200、`{"status":"ok"}`、curl exit 0を確認してから対象テストを実行 |
| 新規filter testのadmin拒否誤期待、全体build exit 1 | staff共通filterと教師専用handler/Controlの責務差。製品の認可不具合ではない | 契約どおりの期待へ修正し、最終147 suites / 561 testsのbuildで失敗/エラー0。保護66 routeと共有logoutをinventoryへ記録 |

- V33復元直後とV34互換更新後に`BackupRestoreDatabaseTest`を各1件実行し、各1 passed / 0 failed/errors/skipped、exit 0。実`TeacherProgressControl`→DAOで復元進捗の一覧/詳細を読み、未認証拒否と全表全行fingerprintの読取前後不変を確認。V33/V34間のschema差自体は不変とはしていない。元のage鍵・稼働DBは変更していない。
- 過去opt-in XMLの照合で成功証跡が見つからなかったsnapshot/stale保存と冪等提出/再提出の2件、演習schema1件を、新規空schemaへV1〜V34を適用/validateしたうえで再実行。2/2と1/1、skip/failure/error 0、exit 0。global function-creator trustは0へ復元し、最終件数も確認した。
- definitiveな最終コマンド`gradle --offline --no-daemon clean build --warning-mode all --console plain`は独立exit file 0、147 suites / 561 tests、373 passed / 188 skipped、failure/error 0、WAR成功。復元DBのopt-in 1件を有効化した全体実行であり、全DB flagを有効にした実行ではない。
- 188 skipをXML timestampで個別照合し、171件は別実行の非skip成功XML、1件は不採用2.5 Pro診断FAIL、16件は非skip成功XML未発見（任意API7、保持browser fixture補助9）。中間失敗を消したり、未確認16件を成功へ換算したりしない。業務受入と補助fixture/候補診断の責務は[最終対応表](./feature-plans/phase-20-cross-feature-validation.md)で区別した。
- 12:38:37 JST、今回所有の8 test containersと復元ComposeのDB/container/volume/networkを清掃し残存0。合成secret3ファイルと一時WARを削除。共有4サービスのID/image/StartedAtは前後同一かつrunning、共有教師/生徒loginは各HTTP 200。元の本番age鍵、暗号化backup、証跡は維持した。
- 通知のAction Succeeded/Delivered1/Failed0と隔離復旧の詳細は[運用受入記録](./production-deployment.md#2026-10-16-追加承認による復元通知試験)に集約。独立offline鍵custody、人間のメール開封、復元Tomcat/browser、本番完全DR/切替は未確認のままとする。
- 13時台JSTの文書リンク検査は初回Ruby既定US-ASCIIで`invalid byte sequence in US-ASCII`となりexit 1。`-EUTF-8:UTF-8`を明示して解消した後、工程20計画の既存相対リンク3件の参照先欠落を検出。親階層への参照へ修正した。続くad-hoc検査はhost Rubyにない`filter_map`でexit 1となり、互換性のある走査を使うsession専用検査へ置き換えた。製品/DB/外部APIへの影響はない。
- 13:21:10 JST、`/usr/bin/ruby -EUTF-8:UTF-8 <session>/oct16-phase20-closeout/verify-closeout.rb`はexit 0。641条件で最終/個別JUnitの実値とtimestamp、188 skipのケース別根拠、P20全10件のcheckpoint、配送/清掃JSON、参照ファイル、`git diff --check`を照合し、失敗0・リンク欠落0。結果を`closeout-integrity.json`へ保存した。
- 最後のeditor診断はJava2 testファイルに「project classpath外でsyntaxのみ解析」の環境通知がある。editorでtype/compileまで確認できたとはせず、実Gradleのcompile/test成功を根拠とする。文書のProblemsは191件中先頭50件のみ表示され、履歴節のMD038/MD060等を確認。全repo lint cleanとはしない。今回追加した受入条件を満たすために無関係な履歴表の全量整形は行わない。

## 現在の未解決・未確認事項（2026-10-16 更新）

ローカル再受入・合成Gemini診断と本番読取に加え、追加承認の実SNS配送、固定backupの隔離復元/V34互換更新/実Control読戻し、12:38 JSTまでの最終回帰・清掃とP20対応付けを反映した。**工程20のP20-01〜10はPASS / 完了**。その後、別の明示承認により18:06 JSTにmain `41228ca...`/V34へ本番更新し、20:40 JSTに最小デプロイ/合成受入/清掃まで完了した。既存DB container/Volumeと既存accountを維持し、実生徒データを外部送信していない。[実装ロードマップ](./implementation-roadmap.md#次の着手と意図的な保留2026-10-16-jst更新)、[最終受入表](./feature-plans/phase-20-cross-feature-validation.md)、[Runtime Validation Report](./feature-plans/runtime-validation-report.md)、[デプロイ後の受入候補](./production-deployment.md#デプロイ後の受入候補と開始条件)を併せて参照する。

本文には時系列順でない追補や、後続で解消した「未解決」の記載がある。履歴は書き換えず、現在の着手判断にはこの一覧と各計画の最新記録を使う。「未確認」は不具合確定を意味せず、「別ゲート・保留」は工程20で無条件に実行する指示ではない。以下のIDは課題群の整理用で、失敗テスト件数やskip件数ではない。

### 失敗が残る項目

| ID | 現状・影響 | 次の対応・停止条件 | 根拠 |
| --- | --- | --- | --- |
| F-01 | **原因確定・現行アプリへの変更不要。** 承認済みの合成再診断もHTTP 404、`provider_code=not_found`。APIは2.5 Proが新規利用者に提供されない旨を返した。再診断4件中3成功/1失敗、exit 1を保持 | 既定/選択肢に2.5 Proは元々含まれていないため、現行設定・fallbackを変更しない。モデル一覧の掲載だけで採用可能とせず、再採用する場合はアクセス資格から別判断する。新たな再試行は不要 | [安全な再診断](#2026-10-16-07400748-jst-再診断取消フォーカスの再現)、[元の404](#合成運用候補-gemini-25-pro-structured-generation-のhttp-4042026-10-15-1122-jst) |
| F-02 | **解消。** 旧a6版のnginx読取/稼働確認後、固定7image・native受入・明示承認を経てmain `41228ca...`/V34へ更新し、公開HTTPSの合成browser4件と5サービスhealthy/restart 0を最終確認済み | 失敗したef55を再受入したわけではない。本releaseは完了として扱い、将来releaseでは固定CI/source/image照合・migration・復帰referenceを毎回確認する。解消済み障害を再オープンしない | [今回の本番受入](./production-deployment.md#2026-10-16-最小デプロイの進捗)、[元の障害](#2026-10-11-0324-jst-ef55本番updateのnginx起動失敗) |

### 工程20で閉じる検証・確認

| ID | 区分・現状 | 次の対応・完了条件 | 根拠 |
| --- | --- | --- | --- |
| V-01 | **解消。** P20-01〜10と全29 Servlet/71 routeを共通filter・業務DB/HTTP/browser証跡へ対応付けた。最終561 tests中373 passed/188 skipped、failure/error 0、exit 0。必要なeditor2件/schema1件も再成功 | 188 skipは171件に個別成功証跡、1件候補診断FAIL、16件任意API/保持fixture補助として説明。全action×入力×roleの実HTTP総当たりではなく、元のP20合格条件に対する完了 | [最終回帰](#2026-10-16-12時台-jst-復旧検証の差分解消と最終回帰)、[P20最終対応表](./feature-plans/phase-20-cross-feature-validation.md) |
| V-02 | **解消。** 現行の非同期生成を合成provider/専用MySQL/HTTPS/認証browserで受入。polling、離脱/再訪、完全な合成ログ/評価JSON、明示保存後のDB再読込を確認 | runnerはexit 0、fixture 1/1、browser 1/1。実API疎通・実データ採点品質とは別。再受入は関連変更時だけ行う | [最終受入](#2026-10-16-0809-jst-ローカル修正の最終受入)、[runner](../../scripts/testing/teacher-prompt-generation-local.sh) |
| V-03 | **解消。** 履歴から開く配信ツリーを1280px/390pxで展開/折畳・回転・キーボード受入。評価詳細の非表示項目・理由3フィルターと操作も受入 | 配信browser 1/1、教師確認browser 3/3、各exit 0。prototypeは変更しない | [最終受入](#2026-10-16-0809-jst-ローカル修正の最終受入)、[配信runner](../../scripts/testing/teacher-distribution-ui-local.sh) |
| V-04 | **修正・代表フロー受入完了。** ログアウト確認前のbutton無効化を確定後へ移し、shared feedbackで可視dropdownトグルへfocus復帰。取消/Escape/二段階取消のコード保持と再操作、重複submit抑止、確定logout/失効を確認 | 演習browser 3/3、exit 0、対象操作の`aria-hidden`/focus警告なし。全OS/全画面の保証とはしない | [再現・修正](#2026-10-16-07400748-jst-再診断取消フォーカスの再現)、[最終受入](#2026-10-16-0809-jst-ローカル修正の最終受入) |

### 別ゲート・承認または運用/手動確認が必要な事項

| ID | 現状 | 次の対応・境界 | 根拠 |
| --- | --- | --- | --- |
| H-01 | **実SNS配送・隔離業務復旧と本番更新時backupまで受入。** 定期7回/固定Version/独立監視、合成alarm実評価→SNS Delivered1/Failed0、固定backup復元/V34互換更新/実Control・DAO読戻し、今回のfresh backupの固定3 Version/KMS/hash/remote manifestを確認、最終exit 0。専用資源清掃済み | 残るのは新releaseの次回定期実行、独立offline鍵custody、人間の受信箱開封、復元先Tomcat/browser・本番完全DR/切替/RTO/RPO。Mac鍵による復号成功を別媒体保管とせず、本番DB上書きや再試験を自動実行しない | [追加承認の運用受入](./production-deployment.md#2026-10-16-追加承認による復元通知試験)、[デプロイ後の開始条件](./production-deployment.md#デプロイ後の受入候補と開始条件)、[鍵保管等の負債](./ecr-deployment-debt.md) |
| H-02 | **正式な評価・アンケート全体の手動E2E/採点品質校正。** 10月17日の確認で同意書以外の初期設定はローカル確定版を採用済み。現行6設問の合成回答保存・再読込は受入済みだが、投入手順・本番反映・全体受入、品質・大人数queue・予算/quota・本番SLAの確認は別 | 工程8/19の残ゲートとして扱い、設定内容を再び未確定扱いにしない。合成API smoke成功を採点品質保証とせず、実提出/編集ログの送信・同意/契約・費用は別途承認条件を満たしてから進める | [初期設定の採用基準](../function-specification.md#第180版-db初期化後の初期設定の採用基準)、[現行アンケート受入](./feature-plans/phase-20-cross-feature-validation.md#現行アンケート設定を前提とする部分検証2026-10-14)、[AI計画](./feature-plans/teacher-prompt-ai.md)、[ロードマップの保留](./implementation-roadmap.md#次の着手と意図的な保留2026-10-16-jst更新) |
| H-03 | **他ブラウザー/OS・OS標準ダイアログ。** Chromiumの対象フロー、単体/ZIPの取得内容等に成功証跡はあるが、全組合せやOS-nativeの選択/保存操作は未確認 | 対象環境を定めた受入として別管理する。自動download/Blob確認とOSダイアログ操作を区別し、工程20の対象外を無条件に追加して完了条件を拡張しない | [工程20の対象外・別ゲート](./feature-plans/phase-20-cross-feature-validation.md#対象外別ゲート)、[Chrome取得確認](#2026-10-03-2253-jst-授業演習のchrome実ダウンロード表示追加確認) |
| H-04 | **時刻/デモ履歴の方針合意・実装/移行は未完了。** UTC保存/比較・Asia/Tokyo入出力、デモ履歴は初期設定保全/再投入検証後に再作成可能と合意済み | R-01の設定保全/照合とR-03以降の修正・テストへ進む。一括9時間補正はせず、本番初期化/更新・資格情報変更は手順提示後の別承認。方針合意を統一PASSと扱わない | [合意済み日時方針](../function-specification.md#第181版-日時の統一方針)、[最新の時刻調査](./production-deployment.md#h-04-調査結果2026-10-17-00050007-jst)、[第165版の時刻記録](#2026-10-12-第165版-通知基盤評価完了) |

### 検証基盤・非阻害の残件

| ID | 現状 | 扱い・次の対応 | 根拠 |
| --- | --- | --- | --- |
| T-01 | **部分改善・運用上の分離を維持。** 既存review fixtureがあってもsurveyを追加でき、再seedの非重複と不完全fixture拒否を新テストで確認。survey DB 7 passed/1 skipped、認証HTTP 1/1、exit 0 | review/notification/surveyの全suite無条件同居までは保証しない。機能別runnerの空schemaと独立したGradle workspaceを使う。全suite混在の一般化は別の基盤作業として残す | [survey受入](#2026-10-16-0752-jst-テスト手順の追補とsurvey受入)、[以前の混在失敗](#2026-10-13-1010-jst-工程20の統合試験環境実行順の失敗と再検証) |
| T-02 | **今回の欠落は解消・以後の回収を改善。** 承認済み再診断のprovider code/messageとJUnitを、失敗終了済みcontainerから`docker cp`で取得。新しいUI runnerも失敗時のtrace/runtime logを保全 | 秘密情報を除去し、取得できないrequest ID等は捏造しない。以前のDB smoke retry attempt数は遡及確定できないため不明のまま保持し、回収だけのために追加の実APIを呼ばない | [安全な再診断](#2026-10-16-07400748-jst-再診断取消フォーカスの再現)、[診断helper](../../src/test/java/control/evaluation/GeminiApiDiagnosticSupport.java) |
| T-03 | **非阻害の警告整備は残る。** 現行clean buildでもGradle `WarPluginConvention`、runtimeでSLF4J binding、fresh MySQLでroutine dropの1305警告を確認。変更したJava/JSのeditor診断はエラーなし | 依存更新やno-op loggerによる警告隠蔽は行っていない。Gretty/Gradle互換性、logging方針、任意Docker pluginとPython pipe警告の現行再現確認を基盤作業として残す。過去警告すべての再発とは断定しない | [最終受入](#2026-10-16-0809-jst-ローカル修正の最終受入)、[過去のrunner警告](#2026-10-03-23252344-jst-t012のrunner修正最終再検証) |

### 未解決として再計上しない履歴

| 過去の記載 | 現在の扱い・後続証跡 |
| --- | --- |
| runner cleanup競合・UTF-8出力32KiB超過 | [T012最終再検証](#2026-10-03-23252344-jst-t012のrunner修正最終再検証)で修正・実制限/DB再読込を確認済み |
| 古いinactive評価例の一括active化 | [工程20 DAO回帰](./feature-plans/phase-20-cross-feature-validation.md#既知dao不具合の再現修正回帰2026-10-13完了)で修正・専用DB再検証済み |
| 改訂課題の生成開始がアプリ内404、揺らぎ生成429 | [N167-03の修正・認証browser受入](#n167-03-改訂課題のgemini揺らぎ生成がhttp-4042026-10-13-01580204-jst)と[N167-02の承認後再検証](#n167-02-gemini揺らぎ生成のhttp-4292026-10-13-0110-jst)を参照。F-01の候補モデルAPI 404とは別事象 |
| Node.js未導入でPlaywright不可、通常pointer未受入 | ホストNode.jsは未導入だが公式Playwright imageで実行済み。フォルダ行pointerも受入済み。[工程20の現状](./feature-plans/phase-20-cross-feature-validation.md#現在のテスト状況)を参照。ホスト導入は継続の必須条件ではない |
| 30秒保存・同時保存競合・重複提出・再提出が未確認 | [P20-06](./feature-plans/phase-20-cross-feature-validation.md#p20-06-生徒エディター未確認ケース完了)で指定4ケースは完了。旧assignment 17の409を起こした更新者/厳密な時系列は未特定だが、現行コードでのチェック成功と意図した競合拒否の検証は別に記録済み。再発根拠なしに機能障害として再オープンしない |
| 教師課題S1/T014・S2 preview/job履歴の未受入 | [ロードマップの完了記録](./implementation-roadmap.md#次の着手と意図的な保留2026-10-16-jst更新)を参照。V-02の後日の非同期生成/スキーマ変更は今回の別受入記録を参照 |
| V11権限不足、Host header拒否、runner URL不足、最近の所有範囲testのfixture/assertion失敗 | 対象を限定した環境・test修正後の成功を各履歴に記録済み。最新の所有範囲browser受入は成功しており、同じ初回失敗を残件へ再計上しない |
| 本番update失敗後の「サービス停止継続」、段階B/Dの「再実行・timer承認待ち」 | 後続の[旧release復旧成功](#2026-10-11-0711-jst-ef55失敗から旧releaseへの緊急復旧成功)と[backup最終checkpoint](./production-deployment.md#final-acceptance-checkpoint-2026-10-09-2234-jst)で当時の状態は更新されている。F-02/H-01の未解決部分だけを保持する |

### 次の着手

工程20の元のP20-01〜10は完了し、V-01〜04は対象範囲で解消・受入済み。次は研究/運用開始前に必要なH系別ゲートの対象と優先順を利用者と決める。特に鍵の独立保管、正式品質/手動E2E、対象OS、時刻契約を区別し、本番切替/実データ送信/ECR改善へ自動着手しない。F-01の不採用候補診断FAILは残すが、現行アプリのモデル変更や同じAPI再試行は不要。

## 工程20 授業演習コード所有範囲browser試験の期待値・fixture修正（2026-10-16 00:17–00:26 JST）

- 対象/期待: 合成MySQL/Tomcat fixtureでowner教師が授業演習コードを表示でき、他校のみ担当する教師には一覧・detail・download・ZIP・CSV/bulkから対象コードが漏れないことをbrowserで確認する。
- 初回compile結果: `sh scripts/testing/teacher-exercise-review-local.sh browser`は`TeacherExerciseRuntimeTest.java`のfixture権限復元で`cannot find symbol: variable s`となり、Gradle exit 1。SQL `Statement s`は前のtryブロック内の局所変数で、復元assertから参照できなかった。復元SQLに専用try-with-resources `Statement`を作成して修正。
- 初回Playwright結果: `phase20/teacher-exercise-ownership.spec.js`は1 failed、exit 1。owner詳細モーダルで`hello.py`の`print('保存済み')`を期待したが、実UIは一覧最初の`main.py`を既定選択し、`#exerciseCodeViewer`に`print(2)`を表示していた。事前にdetail APIで`hello.py`が存在することは確認済みで、製品不具合ではなくUIの初期選択をテスト期待値に反映。
- Browser command: `docker run --rm --network ppe-exercise-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-exercise-review-runtime, MAP student.localhost ppe-exercise-review-runtime' -e PPE_P20_EXERCISE_TEACHER_PASSWORD='ExerciseDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-exercise-ownership.spec.js --workers=1 --output=/tmp/ppe-exercise-ownership-results'`。
- 再検証: `sh scripts/testing/teacher-exercise-review-local.sh browser` — migration、WAR、`TeacherExerciseRuntimeTest` 1 passed、0 failed/errors/skipped、exit 0。Playwright 1.64.0/Chromiumの同spec再実行は1 passed、0 failed、exit 0。ownerの4行/コード表示、他校教師の空一覧・HTTP 404 detail/download/ZIP・空CSV 200・bulk 400を確認。
- 影響/後処理: production source・共有DB・実利用データは変更なし。合成fixture専用MySQL/Tomcatのみ利用し、終了後に専用container/networkが残っていないことを確認。権限復元はbrowser fixtureを再利用可能にするテストコード内処理。Node.jsはhostに未導入のため公式Playwright image内のNode/Chromiumを使用。

## 工程20 提出確認の学校スコープbrowser fixture復帰漏れ（2026-10-15 13:47 JST）

- 対象/期待: Playwrightでowner教師が合成提出を確認でき、別校のみ担当する教師は同じ提出を一覧・詳細・file・CSV・ZIPから取得できないことを確認する。
- 初回結果: Playwright 1件を実行し、ownerログインは成功したが、ownerの提出一覧fetchがHTTP 403となりexit 1。失敗箇所はテスト側のowner positive-controlであり、他校教師境界のassertionには未到達。
- 原因/対応: 同runで先行する認証HTTP runtime testが権限取消・学校アクセス取消を行い、合成DBにowner教師の拒否状態を残していた。browser test専用DBではあるが、次のbrowser fixtureとして再利用する際にbaselineへ戻す処理がなかった。runtime testの検証終了時に専用fixtureのowner学校アクセスと3機能権限を復帰し、再読込listが3件となることも同test内で確認する。
- 2回目: fixture復帰後のPlaywrightが50秒でtimeout、exit 1。失敗原因は他校教師画面遷移後に一覧XHRを待ち始め、既に完了したresponseを取り逃がしていたこと。画面遷移を開始する前にresponse waitを登録するよう修正した。
- 再検証: `sh scripts/testing/teacher-submission-review-local.sh browser`を実行し、runtime integration `TeacherReviewRuntimeTest` 1 passed、0 failed/errors/skipped（report timestamp `2026-10-15T04:55:41`）。Playwright command `docker run --rm --network ppe-submission-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-teacher-review-runtime, MAP student.localhost ppe-teacher-review-runtime' -e PPE_P20_REVIEW_TEACHER_PASSWORD='ReviewDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-submission-ownership.spec.js --workers=1 --output=/tmp/ppe-submission-ownership-results'` — exit 0、1 passed、0 failed。ownerでfixture提出の存在を確認し、他校教師は画面/listが空、detail/file direct URL/fetchは404、CSVは200で対象情報なし、ZIPは400で対象情報なし。
- 影響/後処理: 合成レビューfixtureとtmpfs MySQL/Tomcatのみ。実利用DB/アカウント、外部API、production sourceは変更なし。runtime test、Playwright spec、検証記録を変更。専用container/networkは停止後に清掃され、残存なしを確認。

## 工程20 評価確認の他校所有範囲テスト期待値修正（2026-10-15 13:37–13:42 JST）

- 対象/期待: `sh scripts/testing/teacher-submission-review-local.sh integration`で、評価確認権限はあるが他校のみ担当する合成教師について、対象評価の一覧・直接取得・CSV/ZIP exportに対象校のデータが混入しないことを認証HTTPで検証する。
- 初回実行: test compileで`TeacherReviewRuntimeTest.java`の局所変数`archive`が同一method内に重複しGradle exit 1。runnerは失敗後の`docker cp`も試み、停止containerからtest resultsをコピーできない旨を出力した。重複名を`outsiderArchive`へ変更した。
- 2回目: migration/WAR build成功後、認証HTTP testが1 failure、Gradle exit 1。other-school教師のZIP取得対象がない場合にHTTP 200を期待したが、既存契約どおり`TeacherReviewControl.evaluationZip`は空結果をHTTP 400にするため失敗。実装不具合ではなくtest expectationの誤り。ZIP/logs-ZIPをHTTP 400とし、応答に生徒ID/コードがないことを検証するよう変更した。
- 3回目: 認証HTTP testは1 failure、Gradle exit 1。CSVの空結果export監査`rows=0`の総数を固定で1と期待したが、先行する教師のfilter付きCSV exportにも0行監査があり、実際は2件だった。testは監査件数の基準を確認前に取るように変更し、他校教師の空CSVがその件数を1増加させること、およびゼロ行の監査detailを記録することを検証した。アプリ不具合ではない。
- 最終再検証: 同じコマンドを再実行し、migration/WAR build成功、`TeacherReviewRuntimeTest` 1 passed、0 failed/errors/skipped、Gradle exit 0。担当教師が同対象を読める既存確認に加え、他校教師の一覧は空、評価detail/file/logsは404、CSVは対象情報なし、ZIP/logs-ZIPは400かつ対象情報なし、CSV監査はrows=0となることを確認した。
- 影響/後処理: production sourceは変更していない。合成データと専用tmpfs MySQL/Tomcatのみ使用し、共有DB/app、実利用データ、外部AIは不使用。最終実行後に専用container/networkはcleanup済み。JDK/Gradleの既存警告とfresh migration時のMySQL 1305 missing-routine warningは残るが、最終build/testは成功。

## 工程20 進捗コードdownload受入のfixture期待値不一致（2026-10-15 11:52–11:59 JST）

- 対象/期待: `sh scripts/testing/teacher-submission-review-local.sh integration`で、許可教師は進捗詳細と最新コードを読み取れ、他校にのみ担当範囲を持つ教師は一覧に対象を表示せず、詳細/コードdownloadの直接GETがHTTP 404となることを合成データで検証する。
- 初回結果: migration、fixture/JUnit/WAR buildは成功。認証HTTP `TeacherReviewRuntimeTest`は1件失敗、Gradle exit 1。`progressDownload.body()`が固定値`print(input())`を含むというassertionが失敗し、テストは他校教師の拒否確認まで到達していなかった。アプリ応答はHTTP 200だった。
- 原因: 課題進捗の仕様では保存済み下書きが最新提出版より優先される。synthetic fixtureの最新コードは`print(input())`ではなく別の保存済みスナップショットであり、テスト側の固定期待値がfixtureのデータ契約と不一致だった。アプリ不具合ではない。
- 対応/再検証: downloadの内容を同一対象に対して許可教師が取得したdetail JSONの`latestCode.code`と照合するよう変更。再度同じコマンドを実行し、migration/build成功、`TeacherReviewRuntimeTest` 1 passed / 0 skipped / 0 failed / 0 errors、Gradle exit 0。許可教師の一覧/詳細/download 200と他校教師の一覧からの除外・詳細/download 404を全て確認した。
- 影響/後処理: 実アプリ/DBのコード変更なし。合成データのみ使用し、外部AI/APIや共有8080/開発DBは使用していない。テストrunnerが専用MySQL/container/networkを清掃。残る工程20範囲は本計画とruntime reportに記載。

## 工程20 opt-in検証 runner/環境の修正（2026-10-15）

### ApplicationUrlRuntimeTest のHost header制約

- 対象/期待: 専用Tomcat/MySQL上で`ApplicationUrlRuntimeTest`のhost別root、認証/role境界、旧URL/POST誘導を5件すべて実HTTPで確認する。
- 初回実行: `sh scripts/testing/application-url-routing-local.sh` — exit 1、5 tests completed / 5 failed。各テストは`IllegalArgumentException`となり、失敗位置はstatic `Browser` clientを使う各HTTP要求だった。
- 原因/影響: Java `HttpClient`は制限された`Host` request headerを既定で拒否する。テスト側はstatic field初期化時にclientを作るため、テストmethod内でpropertyを設定するのでは遅い。実アプリのHTTP応答やDB処理の失敗ではない。
- 対応/再検証: `ApplicationUrlRuntimeTest`のstatic client宣言より前に`jdk.httpclient.allowRestrictedHeaders=host`を設定するstatic initializerを追加。再実行で隔離migration/WAR build成功、5 tests passed、0 failed/skipped、Gradle exit 0。
- 未解決事項: 本試験は隔離HTTP suiteの範囲に限る。全browser/OS組合せの確認ではない。

### TeacherTaskDatabaseTest のrunner設定不足とreadiness probe quoting

- 初回の専用DB起動確認: ad-hoc readiness queryのshell quotingが過剰でschema名を判定できず、runnerは`Isolated MySQL startup failed`でexit 1。Gradleは開始していない。MySQL自体は起動し、schemaを作成していた。
- 対応: shell内SQLの文字列を単純な`information_schema` queryと結果完全一致で検証するようコマンドを変更。次の実行ではmigration/testが実際に開始された。
- 初回suite結果: `TeacherTaskDatabaseTest` 45 tests completed、2 failed、10 skipped、Gradle exit 1。失敗は`periodicSnapshotPersistsAndAStaleEditorVersionCannotOverwriteIt`と`duplicateSubmissionIsIdempotentAndResubmissionPersistsTheNextRevision`。JUnit XMLで両方の原因が`IllegalStateException: PYTHON_RUNNER_URL must be configured`と確認された。
- 原因/影響: 対象DB testは保存/提出フローのみだが、`StudentEditorControl`のdefault constructorがrunner client設定を要求する。test runnerにURL環境変数がなかったためで、実際のrunner呼出し/アプリ不具合を示さない。
- 対応/再検証: 隔離Gradle containerへ未使用のtest-only `PYTHON_RUNNER_URL`と合成tokenを設定。再実行は45 tests、35 passed、10 skipped、0 failed/errors、exit 0。Geminiは無効で、外部API呼出しなし。10 skipをpassに含めていない。

### Gemini opt-in test起動・report回収時のrunner構成問題

- 初回起動コマンド: shell nested quotingの誤りで、意図したDocker `-c` scriptが渡らず、実際のopt-in testは起動しなかった。誤ってhost Gradleが起動し、hostにJava 21 toolchainがないためdependency resolutionで失敗した。Gradle exit 1に続き、outer shellはclosing quoteを解釈できずexit 127。Gemini API requestは0件で、`.env`の値は出力していない。
- 対応/最終診断: shell scriptを直接実行する形に直し、`.env`から`GEMINI_API_KEY`のみを権限600の一時env fileへ抽出して、隔離Docker test containerへ渡した。合成入力、`gemini-3.1-pro-preview`、通常diagnostic上限4 generation、別smoke 1 generationの範囲で選択test群はGradle exit 0。model comparison/候補モデル/primary recheck gateは無効。
- report回収: Gradle完了後にDocker containerは停止状態となるが、回収条件判定で`docker exec`を使用したため「container is not running」が2回出力され、test XML/diagnostic observationをローカルへコピーできなかった。終了コードを誤って0から変更することはなく、API test自体はexit 0。reportを取得するための追加billable requestは避け、test outputとtest sourceの選択件数だけを記録した。
- Gemini DB-backed smoke: 別の新規専用MySQLで`EvaluationDatabaseTest.savesLiveGeminiResultOnlyWhenExplicitlyEnabled`は1 passed、0 skipped/failures/errors、exit 0。評価worker完了と評価結果のDB read-backに成功。実データ送信なし。内部retry attemptの正確な回数は保存結果に含めていない。
- 影響/残件: runner quoting/Java toolchain失敗はdiagnostic testを実行しておらず、APIへ要求していない。実行したAPI testは合成内容のみ。API keyは出力・error reportへ記録していない。

### 合成運用候補 `gemini-2.5-pro` structured generation のHTTP 404（2026-10-15 11:22 JST）

- 対象/手順: `GeminiOperationalCandidateTest`を専用Docker Gradle containerで実行。`GEMINI_API_OPERATION_CANDIDATE_TEST=true`。合成payloadのみを使用し、候補モデルごとに1回のstructured generationを送信した。
- 期待結果: `gemini-3.5-flash-lite`、`gemini-2.5-pro`、`gemini-3.1-pro-preview`のstructured responseがJSON schemaに合致し、保存後のread-backも一致する。
- 実際の結果: 4 tests completed、3 passed、1 failed、Gradle exit 1。`gemini-3.5-flash-lite`と`gemini-3.1-pro-preview`はstructured output/read-back成功。利用可能モデル一覧GETもHTTP 200で、応答に`models/gemini-2.5-pro`を含んだ。一方、`gemini-2.5-pro`のstructured generationはHTTP 404、`retryable=false`。同モデルへの生成試行は1回で停止。
- API情報/記録限界: endpoint `https://generativelanguage.googleapis.com/v1beta/interactions`、model `gemini-2.5-pro`、HTTP 404。保存された安全な例外は`Gemini request failed with HTTP status 404.`。provider error code、response body/message、request ID、Retry-Afterは保存済みtest artifactに含まれず、証跡からは不明。response bodyやAPI credentialは表示/保存していない。
- 影響/対応: 診断のみで、既定モデル、`GeminiModelCatalog`、prompt設定、利用者/DB状態は変更していない。既存の[AI連携設計](../ai-api-integration-design.md)には同じAPI keyでは`gemini-2.5-pro`が新規利用者に利用不可との過去記録があり、今回の404はproject/keyの利用不可と整合する。ただし今回のprovider response bodyは保存されておらず、厳密な原因は確定できない。モデル一覧への掲載だけでは当該endpointでの生成利用可能性を保証しない。candidate採用/fallback判断は保留し、追加の有料API再試行は行わない。
- 後処理: test containerはrunner cleanupで削除。追加API呼出し・再試行はしていない。test結果は[Runtime Validation Report](./feature-plans/runtime-validation-report.md)に記録。

## 工程20 P20-06 同時保存競合browser受入runner構成ミスと修正（2026-10-15）

- 対象/期待: 同じ生徒・割当・`draftUpdatedAt`を持つ二つのeditor contextから同時に下書き保存し、一方だけを成功させ、他方をHTTP 409にして、DB再読込後も成功側コードを維持する。
- 初回失敗: `sh scripts/testing/student-editor-concurrency-local.sh` — Playwright exit 1。editor GETがHTTP 500となり、最初の並行GET実行では続くcontextが404となった。Playwrightを順次GETへ変えても最初のeditor GETは500。Tomcat logで`java.lang.IllegalStateException: PYTHON_RUNNER_URL must be configured`を特定。runnerがStudentEditorControl必須の設定と通常の隔離Python runnerを起動しておらず、アプリservletの初期化に失敗していたテスト環境構成ミス。
- 修正と再検証: 専用network内の制限付き`programming-process-evaluator-python-runner` containerを起動し、隔離Tomcatへdummy URL/tokenを設定。runnerはコード実行には使用せずeditor save経路のみを試験した。その後同じコマンドを2回実行し、各runでV1–V34 migrate/validate・WAR build成功、fixture JUnit 1 passed/0 failed/errors/skipped、Playwright 1 passed/0 failed、exit 0。各contextは同一初期timestampを持ち、Playwright route barrierで両POSTを揃えて同時解放。要求処理intervalのoverlap、HTTP 200一件とHTTP 409一件、保存成功側だけが両contextの再読込後に表示されることを確認。
- 非致命出力/後処理: fresh schema migrationで不存在routine dropのMySQL SQL State `42000` / Error Code `1305`と既存Gradle deprecation warningが出たが、migration/validation/build/testは成功。runner終了後、専用MySQL/Tomcat/Python runner/builder/browser containers、network、WAR/fixture logを除去し、port 18099解放を確認。共有DB/app、実データ、外部AIは使用していない。

## 工程20 P20-03 教師セッションから生徒専用routeへの直接アクセス拒否（2026-10-15）

- 対象/期待: 課題管理権限を持つ合成教師が、student portal上の生徒専用routeを同じ認証sessionで直接要求した場合にHTTP 403で拒否されること。
- 検証: `sh scripts/testing/teacher-account-revocation-local.sh` — exit 0。V1–V34 migration/validation、管理者fixture生成JUnit 1 test passed、WAR生成、Playwright 1 test passed / 0 failed。教師はパスワード変更後に`/teacher/task`へHTTP 200で到達し、その`JSESSIONID`を`student.localhost`に適用した`GET /student/home`はHTTP 403。runner終了後、専用container/network/WAR/fixtureは削除され、port 18098は解放された。
- 非致命出力: fresh schema migration時に存在しないroutine dropのMySQL SQL State `42000` / Error Code `1305`が再度表示された。migration/validationと全テストは成功し、製品不具合は確認されていない。Gradle 9互換性に関する既存deprecation warningも出力された。

## 工程20 P20-04 学習開始後の改訂拒否DB受入と一時環境清掃（2026-10-15 06:46 JST）

- 対象/期待: 学習状態が`in_progress`の生徒がいる公開課題について、同一系列の改訂作成が拒否され、元課題と割当の公開状態が維持されること。
- 検証: 新規の専用tmpfs MySQL schema `ppe_teacher_task_test_started_revision`で、Gradle 8.10.2/JDK 21から`flywayMigrate flywayValidate`および`TEACHER_TASK_DB_TEST=true`下の`TeacherTaskDatabaseTest.refusesRevisionWhenAnyStudentHasStartedThePublishedTask`を実行。exit 0、JUnit 1 test passed、0 failed、0 errors、0 skipped。テストは合成教師・課題・クラス・生徒を作成し、学習開始状態で改訂を試し、拒否例外と課題/割当の`published`維持を確認する。
- 検証中の非致命出力: fresh schema migration時に存在しないroutine dropのMySQL SQL State `42000` / Error Code `1305`が表示されたが、migrationとvalidateは成功。既存のGradle `WarPluginConvention` deprecation warningも出力された。
- 一時環境清掃の不備と対応: 一回限りrunnerのEXIT trapでcontainer/networkのownership label lookupが引用符誤りとなり、専用DBとnetworkが一時的に残った。`docker inspect`でrunner/DB/networkすべてのowner labelが同一の`87136`であることを確認後、当該runの2 containersとnetworkだけを削除し、再確認で全て不在となった。共有環境は操作していない。

## 工程20 P20-03 生徒セッションから教師/管理者専用routeへの直接アクセス拒否（2026-10-15 06:42 JST）

- 対象/期待: 合成生徒が認証後に教師workspaceおよび管理者専用画面へ直接アクセスしても、ロールに基づき拒否されること。
- 検証: `sh scripts/testing/student-exercise-layout-local.sh` — exit 0。専用tmpfs MySQLへのmigration、Gradle WAR build、隔離Tomcat readinessが成功。Playwright 1.64.0/Chromiumは2 tests passed、0 failed: 既存の狭幅フォルダ行操作と、生徒からの直接アクセス境界を確認。誤資格情報は拒否され、有効な合成student loginで`/student/home`へ到達後、同じsession IDを`teacher.localhost`へ設定して`GET` `/teacher/task`と`GET` `/admin/teachers`を要求し、どちらもHTTP 403を確認した。異なるportal hostへのredirectではなく、認証filterのrole判定を実際に通す構成。
- 出力/影響: 空schema migration中、存在しないroutine dropのMySQL SQL State `42000` / Error Code `1305`が出力されたが、migration/build/testsは失敗せず完了。Gradle 9互換性に関する既存deprecation warningも出力された。アプリの不具合・修正はなし。
- 後処理: runner所有のbuilder/database/runtime/browser containerとnetworkを削除し、port 18091解放を確認。共有DB/アプリ、実利用者データ、外部AIは使用していない。

## 工程20 P20-09 管理者更新のstale version拒否browser受入（2026-10-15 06:26 JST）

- 対象/期待: 管理者の教師管理画面で古い更新versionを使った停止要求を拒否し、教師状態とversionを変えず、失敗操作を監査記録へ残す。最新versionでの停止操作は成功すること。
- 初回browser実行の問題と対応: `sh scripts/testing/teacher-account-revocation-local.sh`はPlaywright assertionでexit 1（0 passed、1 failed）。検証対象のHTTP 400拒否と状態不変は通過したが、失敗監査locatorが操作名「停止」の部分一致で「停止解除」の失敗行も数え、2行を検出した。locatorを操作列の「停止」完全一致に修正した。
- 最終検証: 同コマンド再実行はexit 0。V1–V34 migration/validation、`createsRetainedAdminManagementBrowserFixture` 1 test passed、WAR生成、隔離Tomcat readinessが成功。Playwright 1.64.0/Chromiumは1 test passed、0 failed。古いversionの停止POSTはHTTP 400とversion競合メッセージを返し、再読込後も状態「利用中」・version据置を確認。続く最新versionの停止は成功し、監査に正確な「停止」失敗行が1件存在した。
- 後処理/影響: 専用tmpfs MySQL/Tomcat/builder/browser container、network、WAR、fixtureをrunnerが削除。port 18098は解放済み。共有DB/アプリへの変更、実利用データ、外部AI呼び出しはなし。初回はテストlocatorの誤りであり、製品不具合は確認されていない。

## 工程20 P20-03 教師から管理者専用routeへの直接アクセス拒否（2026-10-15 01:56 JST）

- 対象/期待: 課題編集権限を持つ教師として認証した状態でも、管理者専用教師管理画面GETと教師作成POSTは拒否され、アカウント作成などのDB変更が発生しないこと。
- 初回browser実行の問題と対応: 教師sessionのCSRF tokenを添えて教師作成POSTを送信したところ、管理者専用routeでHTTP 403となったが、Tomcatの403応答はHTMLだった。テストがJSONとしてparseしようとして`SyntaxError: Unexpected token '<'`でexit 1になった（1 failed）。GET 403とPOST 403をstatusで検証し、POSTの応答本文はtextとして確認する形へテストを修正した。
- 最終検証: `sh scripts/testing/teacher-account-revocation-local.sh` — exit 0。Playwright 1.64.0/Chromium 1 test passed、0 failed。認証教師の`GET /admin/teachers`はHTTP 403、同じ有効な教師session/CSRFを使った`POST /admin/teachers`もHTTP 403。管理者側一覧を再読込し、試行した合成教師アカウントが作成されていないことを確認した。同教師の課題画面はHTTP 200、許可されていない教師提出画面はHTTP 403のまま。
- 後処理/影響: 専用tmpfs MySQL/Tomcat/container/network、WAR、fixtureを削除し、port 18098解放、共有教師login HTTP 200を確認。共有DB/アプリへの書込み、外部AI呼び出し、実利用データはなし。空schema migration時に存在しないroutine削除のMySQL SQL State `42000` / Error Code `1305`は出力されたが、migrate/validateは成功した。

## 工程20 P20-03 教師アカウント停止解除・論理削除のbrowser受入（2026-10-15 01:49 JST）

- 対象/期待: 合成教師を管理者が停止し、停止中はログインを拒否する。管理者が停止解除した後は再ログインと許可routeへのアクセスが成功し、論理削除後はアカウントが削除済みとして残り、操作ボタンが消え、ログインが拒否され、削除操作の監査履歴が成功として保持されること。
- 検証中の出力: 空の専用schemaへのmigration中、存在しないroutineを削除する旨のMySQLメッセージ（SQL State `42000`, Error Code `1305`）が出力された。`flywayMigrate`と`flywayValidate`は後続成功し、テスト全体も失敗なしで完了した。migration障害ではなく初期schemaに対象routineがないことを示す出力として記録する。
- 初回browser実行の失敗と対応: 削除済み教師に対する有効なCSRF/確認情報付き`activate` POSTは期待どおりHTTP 400で拒否され、エラー本文も削除済みを示した。しかし履歴には先に成功した停止解除も存在するため、テストの「停止解除」だけのlocatorが2行に一致してstrict mode violationとなり、Playwrightはexit 1（0 passed/1 failed）。拒否の検証結果自体は通過しており、locatorを「停止解除」かつ「失敗」の行へ限定して修正した。
- 最終検証: `sh scripts/testing/teacher-account-revocation-local.sh` — exit 0。Gradle 8.10.2/JDK 21でV1–V34 migrate/validate、fixture生成JUnit 1 test passed、WAR生成が成功。Playwright 1.64.0/Chromiumは1 test passed、0 failed。停止POST・停止解除POST・論理削除POSTは全てHTTP 200。停止後と削除後の教師routeはログイン画面へ遷移し、停止解除後は再ログイン後の課題画面がHTTP 200。削除後の一覧表示は「削除済み」、状態操作ボタンは0件、削除後の再有効化POSTはHTTP 400、操作履歴は削除「成功」と再有効化「失敗」をそれぞれ表示した。
- 後処理/影響: 専用MySQL/Tomcat/builder/browser containerとnetwork、WAR、fixtureを削除し、port 18098解放を確認。共有教師ログインはHTTP 200で、共有DB/アプリは変更なし。合成データのみで外部AI呼び出しなし。他の全route・他ブラウザー/OS・本番環境は未確認。

### 授業演習フォルダ行の狭幅表示修正（2026-10-15 01:11 JST）

- 対象/期待: 生徒の授業演習画面で、狭幅時にもフォルダの開閉、名称、操作メニューが折り返されず一行に並ぶこと。
- 実際の事象: 共有ブラウザーページ`student.localhost:18090/student/exercise`の再読込は`net::ERR_CONNECTION_REFUSED`。`lsof`でも18090のlistenerは確認できず、当該隔離アプリは停止済みだった。共有8080はlogin HTTP 200だが、未認証のexercise GETはloginへ302となるため、既存認証・DBデータには触れず実画面の再受入は行わなかった。
- 対応/代替検証: 767px以下で`.lesson-tree-folder-header`をcolumnにしていた規則をrow/centerへ修正し、stylesheetのcache-busterを更新。既存のフォルダ行Playwrightテストに390px/320pxのflex方向、行高、toggle/name/menuの垂直位置一致を追加。Playwright 1.64.0 Chromiumで実stylesheetを使ったDOMレイアウト検証は390px/320pxとも`row`、高さ43.2px、操作要素の同一行配置で成功。続けて専用tmpfs MySQL/Tomcatと使い捨て生徒fixtureを用意し、認証後の実画面を1280pxから390px/320pxへ変更して確認。390px/320pxの一行表示・高さ上限・操作部の整列、行余白クリックとボタンの開閉、reload後の初期状態、非GETリクエストなしをPlaywrightで確認し、1件成功。コマンド`sh scripts/testing/student-exercise-layout-local.sh`はGradle migration/対象JUnit/WARビルドとPlaywrightを含めexit 0。テストspecの`node --check`、CSS/JSP/specのProblems、`git diff --check`も成功。
- 再検証/制約: 停止していた18090ではなく、専用port 18091・一時DB・合成アカウントを使用して実アプリ受入を完了。検証後、専用コンテナ/networkを清掃し、共有8080/DBは変更していない。画面HTML/JSP・Java本体の動作変更はなく、実利用者アカウント・本番環境・他ブラウザーは未確認。

### 工程20 P20-03 管理者による教師停止後のセッション失効（2026-10-15 01:33 JST）

- 対象/期待: 合成教師が課題編集へ認証済みで到達した状態で、管理者が当該アカウントを停止する。停止状態が保存され、既存教師セッションから保護URLへアクセスできずログイン画面へ戻ること。
- 初回実行の問題と対応:
  - Playwright runnerからfixture propertiesが見つからずexit 1。fixtureはGradle builder container内に生成されるため、実行scriptでworkspace mount先へ明示的にcopyするよう修正。
  - 続くブラウザー接続は`net::ERR_CONNECTION_REFUSED`でexit 1。browser containerからTomcatへ接続するhost-resolver ruleが不足していたため追加し、さらに公開host portではなくcontainer内port 8080をbase URLに設定した。
  - 停止操作後、一覧行が「利用中」のままで受入assertionが失敗。教師の初回パスワード変更後に管理者一覧を再読込して最新versionを取得し、停止POSTのHTTP 200を明示assertionにしたところ、停止状態の再読込と既存教師sessionの失効を確認できた。製品コードの不具合は再現していない。
  - ホストの`node --check e2e/phase20/admin-teacher-management.spec.js`はNode.js未導入のためexit 127。Playwright container内で同じspecを実行し、最終成功した。
- 最終再検証: `sh scripts/testing/teacher-account-revocation-local.sh` — exit 0。専用MySQLへmigration/validation、対象JUnit fixture、WAR生成が成功。Playwright 1.64.0/Chromiumは1 test passed、0 failed。停止前の`/teacher/task`はHTTP 200、管理者の停止POSTはHTTP 200、再読込後のアカウント状態は「停止中」、既存教師sessionで同じ保護URLへ戻るとlogin URLへ遷移した。外部AI・実利用データなし。
- 後処理/影響: 専用tmpfs DB/container/network、fixture propertiesとWARはすべて削除済み。共有8080/DBおよび既存serviceは変更していない。対象は停止後の既存教師session境界であり、他routeの全ロール/所有範囲網羅、本番・他ブラウザー/OSは未確認。

### 工程20 P20-03 課題管理権限のない同一校教師による直接アクセス拒否（2026-10-14 18:33 JST）

- 対象/期待: 専用DB上の同一校・有効アカウントの教師に`task-management`を付与しないfixtureを作成し、認証後に課題編集の直接URLがHTTP 403となることを確認する。同一校で許可されたpeerの編集・保存・owner read-backと、他校教師の拒否も同時に回帰確認する。
- 検証中エラーと対応:
  - 最初のGradle offline実行は新規cacheにFlyway pluginがなく失敗。online retryはGradle Plugin Portal artifactのTLS handshake失敗。既存依存cacheを専用Gradle cache volumeへ複製し、以後のoffline実行を成功させた。共有app/DBは変更していない。
  - 空DBへの初回migrationはV11のroutine作成でMySQL Error 1419（binary logging有効・runtime userにSUPERなし）となった。専用DBだけ`log_bin_trust_function_creators=1`として再試行したが、失敗migration履歴によりFlyway validationが失敗したため、当該synthetic schemaを破棄・再作成し、同設定でV1–V34を再適用・validateした。migration完了直後に値を`0`へ戻した。
- 再検証: fixture生成を含むGradle testとWAR生成はexit 0。Playwright `teacher-task-peer-draft-ownership.spec.js`はexit 0、1 passed、0 failed。課題管理権限を持つ同一校peerは下書きを編集・保存できownerから再読込できた。他校教師と課題管理権限のない同一校教師は直接GETでHTTP 403。DB read-backでも後者の同一校アクセスはenabled、`task-management`は0。共有アプリログインHTTP 200、MySQL binary-log trust値0を確認。
- 後処理/影響: 専用Compose projectのcontainer/volume/networkは削除され残存各0、fixture propertiesも削除した。外部AI、実データ、本番、共有DBへの書込みなし。失敗はテスト環境の初期cache/TLSと専用MySQLのmigration設定に起因し、製品コードの失敗ではない。

### 工程20 P20-03 教師の課題下書き所有境界browser受入（2026-10-14 18:10 JST）

- 対象/手順: 専用tmpfs MySQLと合成教師のみを用意し、同一校peer教師による他教師の下書き編集、および他校教師による直接URL拒否をPlaywrightで確認する。
- 検証中エラー: 初回Gradleコマンドではfixture保持用環境変数をGradle全体へ渡したままfixture生成テストと通常DBテストを同一実行に含めた。fixtureテストは`teacher-demo`を残し、通常テストも同じ固定login IDを使おうとしてSQL integrity constraint violationとなりexit 1（3 tests completed、1 failed）。
- 原因/対応: これはテスト起動環境の誤設定で、製品コードの失敗ではない。fixture保持フラグをfixture生成test invocationのみに限定し、同一校DBテストは保持フラグなしの別Gradle invocationへ分離した。
- 再検証: Flyway migrate/validate、retained fixture 1 test、WAR生成はexit 0。Playwright `teacher-task-peer-draft-ownership.spec.js`は1 passed、0 failed、exit 0。同一校peerが下書き名を編集・保存してownerから再読込でき、他校teacherの一覧には現れず直接GETは403となった。別Gradle invocationで既存DB認可テスト2件もpassed、0 failed、exit 0。
- 後処理: 専用tmpfs DB/app/builder/browser containerとnetworkを削除し、残存container/networkなし。Gemini/実データ/共有DBなし。専用環境でのみ書込み。

### 工程20 P20-03 同一校教師による他教師の配信テンプレート編集受入（2026-10-14 16:37 JST）

- 対象/手順: 合成教師・生徒のみをseedした専用tmpfs MySQLと隔離Tomcatを起動し、`TeacherDistributionDatabaseTest.createsRetainedPeerTemplateForPhase20BrowserAcceptance`で同一校共有テンプレートを用意した後、Playwrightで同一校教師による編集と他校教師の直接URL拒否を確認する。
- 期待結果: 認証後の教師ホームからコード配信画面へ移動し、同一校教師は他教師の配信済みテンプレートを表示・編集できる。保存内容は再読込後も残り、別校教師は一覧およびテンプレートIDを指定したGETから拒否される。
- 実際の事象/検証中エラー:
  - 初回`./node_modules/.bin/playwright test --config=playwright.config.js phase20/teacher-distribution-peer-edit.spec.js --workers=1`はexit 1。ログイン後を`/teacher/distribution`と期待したが、実際の教師ログイン先は`/teacher/home`だった。
  - 次の実行では同一校教師の一覧・編集・保存・再読込が通った後、所有教師の配信履歴にも更新後テンプレート名が表示されるとするassertがexit 1。配信履歴には配信時点のスナップショット名が正しく残り、最新テンプレート名と混同していた。
  - 3回目は一覧・編集・保存・再読込・別校教師の一覧非表示まで成功したが、Chromium外のPlaywright `APIRequestContext`はChromeのhost-resolver ruleを使わず、`teacher.localhost`の名前解決で失敗した。
- 対応/再検証:
  - ログイン成功後に実際の既定遷移`/teacher/home`をassertし、その後`/teacher/distribution`を開くよう修正。
  - 履歴は不変の配信時スナップショット名をassertし、編集操作からロードしたテンプレートでは更新名・編集内容をassertするよう修正した。
  - Chromium自身のhost-resolver ruleを使う`page.goto`で直接URLを開き、HTTP statusを検証する形へ変更した。
  - 最終Gradleコマンド`gradle --offline --no-daemon --console=plain flywayMigrate flywayValidate test --tests control.teacher.TeacherDistributionDatabaseTest.createsRetainedPeerTemplateForPhase20BrowserAcceptance war --rerun-tasks`はexit 0。専用MySQL migration/validation、fixture test 1件、WAR生成が成功した。
  - 最終Playwrightコマンド`./node_modules/.bin/playwright test --config=playwright.config.js phase20/teacher-distribution-peer-edit.spec.js --workers=1`はexit 0、1 passed、0 failed。誤資格情報拒否、同一校教師の共有テンプレート表示・編集・保存・再読込、所有教師による変更後内容の再読込、別校教師の一覧非表示と直接GET HTTP 403を確認した。
- 影響/原因: E2E期待値の誤り、およびPlaywright APIRequestContextとChromiumのDNS設定が独立している点をテスト実行時に考慮していなかった。V19 migrationで対象procedureが存在しないMySQL noteは出たがFlyway migration/validationは成功し、失敗原因ではない。製品コードや共有サービス/DBは変更していない。
- 後処理/残件: 専用tmpfs MySQL、builder/runtime container、network、一時WARおよびPlaywright依存を削除し、専用container/networkが残っていないことを確認。共有app/DBは稼働維持。配信履歴の既存レコード名は配信時snapshotとして維持される。工程20全体は引き続き`PARTIAL / NEEDS_SIGNOFF`。

### 工程20 P20-03/P20-09 教師演習コード所有境界とフォルダ行pointer受入（2026-10-14 JST）

- 対象/手順: [隔離検証スクリプト](../../scripts/testing/teacher-exercise-review-local.sh)のbrowserモードで専用tmpfs MySQL、合成fixture、専用HTTP appを起動し、教師演習コードのDB/HTTP権限テストと生徒向けフォルダ行pointer試験を実施。Playwright依存は公式1.64.0 image内で一時ディレクトリへ導入した。共有app/DBは読み取り・書き込み先にしていない。
- 期待結果: 担当範囲と権限の許可/拒否、同意/状態境界、読取操作のデータ不変を確認する。生徒がボタンや三角以外のフォルダ行余白を通常pointerで押して展開/折りたたみでき、子ファイルを閲覧できる。検証後、専用container/networkだけを清掃する。
- 実際の事象/検証時エラー:
  - 最初のPlaywright container起動で作業ディレクトリを指定しなかったため、entrypoint `./node_modules/.bin/playwright`を既定ディレクトリから解決できずexit 127。
  - Integrated browserのlogin button clickは、buttonが639px viewportの下端より約55px下にあったため、visible/stable待ちでtimeout。`getByRole('button', {name:'ログイン'})`はヘルプボタンにも一致しstrict-mode violationとなった。exact locatorに直した後もscroll/stability waitがtimeoutした。
  - isolation DBでV19 migration実行時に`DROP PROCEDURE IF EXISTS`対象の既存procedureがない旨のMySQL noteが表示されたが、Flyway migration/validationとGradle taskは成功し、DB/HTTPテストも失敗なし。
- 対応/再検証:
  - 1回目のPlaywright失敗は`--workdir /work`を加えて再実行。`exercise-folder-row-toggle.spec.js`はexit 0、1 passed。ログイン失敗→合成ログイン、folder rowの非interactiveな余白を実pointer clickし展開/折りたたみ、フォルダ名buttonによる展開、reload後のDB由来folder/file表示を確認。folder toggleでは非GET requestが発生せず、データ更新なし。
  - ブラウザー手動受入では新しいcontextを開き、tab navigationでlogin submitを行って認証に成功。フォルダ名とrow paddingを実clickして開閉動作を確認。timeoutはbrowser harness上のviewport/安定待ちで、製品機能の失敗ではない。
  - `sh scripts/testing/teacher-exercise-review-local.sh browser`はexit 0。`TeacherExerciseDatabaseTest.currentSavedTreesScopeDownloadsCsvAndRevocation` 1 passed、`TeacherExerciseRuntimeTest.authenticatedHttpReadExportsErrorsAndAuditFailure` 1 passed、WAR生成成功。
- 影響/原因: Playwright runnerの作業ディレクトリ指定漏れと、統合browserのviewport/操作安定待ち。修正・代替操作後は全自動試験成功し、製品不具合は検出されなかった。権限受入は教師演習コード確認機能の範囲に限定し、全機能横断の認可完了とはしない。
- 後処理/残件: 専用container/network 4件/1件が全て消えたことを確認。Playwright runnerおよび一時npm依存も終了/削除。共有app/DBは稼働維持。P20-03の全機能/所有境界、他browser/OS等は未確認で、工程20は`PARTIAL / NEEDS_SIGNOFF`のまま。

### 工程20 P20-05現行アンケート設定と課題プレビューの隔離受入（2026-10-14 14:26 JST記録）

- 対象/手順: 専用Compose project `ppe-p20-survey-acceptance-20261014`、DB `ppe_teacher_review_test_surveyacc`、app port 18097で現行アンケート設定と教師課題一覧/ヒントプレビューを受入。共有DBからはsurvey ID 4の設定を読み取り、専用DBへ設問/選択肢定義のみ複製。合成教師/生徒でブラウザー受入とDB保存後の再読込を実行した。Gemini API・実利用データは使用していない。
- 期待結果: 現行設定を変更せず、生徒が設問を表示・下書き保存・提出・再読込できる。撤回済み同意の別生徒は拒否される。教師画面ではプロンプト状態/操作が適切に分離され、ヒント名の大小文字とCodeMirror表示が保たれる。
- 実際の事象/検証中エラー:
  - 初期のread-only MySQL probeは接続passwordを指定せずaccess denied。`log_bin_trust_function_creators`をSESSION scopeで読むprobeも失敗し、当該値がGLOBAL-onlyと判明した。またsurvey表の`created_by_user_id`と回答表の`task_class_assignment_id`を選んだ照会は実在しない列名で失敗した。いずれもSELECT/調査中の失敗で、DB書込み前に停止した。
  - 既存の`TeacherReviewDatabaseTest`はfixture作成時にfailure-injection triggerをCREATEし、アプリDBユーザーに必要なtrigger権限がなく失敗した。続く初期fixture試行ではComposeのDB名が既定値のままで、接続先の専用DB schemaが存在せずconnection timeoutとなった。
  - 最初のブラウザー期待値は未認証ページをHTTP 403としたが、実際はHTTP 200の生徒login画面だった。Playwrightではdisabled fieldsetの`toBeDisabled()` matcherも実際の要素状態と一致しなかった。
- 対応/再検証:
  - MySQL照会にはコンテナ内設定済み接続を使用し、変数を`@@GLOBAL`から取得、SELECT列を実スキーマに合わせて修正。以後のread-backではsurvey定義6設問/30選択肢、提出response 1件/回答6件、撤回済み生徒のresponse 0件を確認した。
  - trigger作成権限を追加せず、専用DBに限るguarded `StudentSurveyAcceptanceFixtureTest`へ切替。target schemaを明示作成/migrateし、アプリユーザーにはそのschemaだけを許可。fixture installerは1 passed、0 skipped/failures/errors、exit 0。
  - 未認証時はlogin画面とHTTP 200をassertし、fieldset属性と子radioのdisabled状態をassertするようブラウザーテストを修正。最終コマンド`npm run test:phase20 -- phase20/current-survey-settings.spec.js`は公式Playwright 1.64.0 Docker image内でexit 0、2 passed、0 failed。
  - 認証生徒の現行survey表示、Q3/Q4表示文言、下書き保存/再読込、提出後readonly表示、同意撤回済み別生徒のHTTP 403を確認。合成教師は課題一覧の状態セル/プロンプト操作、混在大小文字ヒント名、Python CodeMirrorプレビュー、375px/1280pxで横溢れなしを確認。詳細な範囲/件数は[Runtime Validation Report](./feature-plans/runtime-validation-report.md)に記録。
- 影響/原因: DB認証/初期schema指定、旧fixture testの権限前提、HTTP login contractとPlaywright matcherの期待違い。アプリ不具合ではなく、試験手順とテスト期待値を修正した。共有DB/共有appを変更せず、本番反映・外部API呼出しもなし。
- 後処理/残件: 専用DB、Playwright/runner/app container、volume/networkを削除。共有appのlogin routingはHTTP 302で応答することを確認。受入対象は成功したが、工程20の残りのopt-in試験/主要導線、他ブラウザー/OS等は未確認のままで、全体判定は`PARTIAL / NEEDS_SIGNOFF`。

### 課題ヒントプレビュー/課題一覧・現行アンケート設定のローカル検証（2026-10-14 12:15–12:32 JST）

- 対象/手順: 課題編集のヒントPythonプレビュー、課題一覧のプロンプト状態/操作列、現行アンケート設定を確認。JUnitはGradle 8.10.2/JDK 21のDocker環境で実行し、ブラウザーは専用Compose project `ppe-p20-task-preview`、合成DB `ppe_teacher_task_test_taskpreview`、app port 18096を使用。共有8080/DBおよび外部AIは試験対象にせず、アンケートDB設定は読み取りのみ。
- 期待結果: 課題ヒントのタイトル大小文字を維持し、Pythonコードを表示領域内のCodeMirrorとして描画する。ヒント/テストコードに行番号を表示しない。課題一覧の状態は「設定済み/未設定」のみで示し、プロンプト設定操作を操作列へ分離する。現行アンケート設定を変更せず、設問/選択肢表示契約を検証する。
- 実際の事象/検証時エラー:
  - ホストの`gradle test --tests servlet.teacher.TeacherTaskPromptRevision162ViewTest --tests servlet.student.StudentSurveyUiContractTest --no-daemon --console=plain`はexit 1。Gradle toolchainがJava 21を要求したがホストに一致するJDKがなく、テスト実行前に停止した。
  - Docker内の初回対象テストは8 tests中1 failed。テストが動的にJavaScriptで設定する`hint-title-label` classをJSP内で検索していたため、実装ではなく契約テストの期待先が誤っていた。
  - 隔離MySQLの初回migrationはV11でexit 1、MySQL error 1419。binary logging有効時に`log_bin_trust_function_creators`が無効のままtrigger作成へ進んだ。V11が部分適用されたため、その専用Compose project/volumeのみ破棄して空DBからやり直した。
  - ブラウザースクリーンショット取得は対象ヒント領域とその要素参照の2回ともツール側で失敗した。ブラウザー自動操作によるDOM/描画寸法検証は実行できたが、画像証跡は取得できていない。
- 対応/再検証:
  - テストを実際にclassを付与する`task.js`側で検証するよう修正。`docker compose run --rm -T app gradle test --tests servlet.teacher.TeacherTaskPromptRevision162ViewTest --tests servlet.student.StudentSurveyUiContractTest --tests entity.StudentSurveyOptionLabelTest --no-daemon --console=plain`はexit 0。13 tests（4+4+5）、passed 13、failed 0、skipped 0。
  - 隔離DBのbinary logging用設定をmigration中だけ有効化してV1以降を`flywayMigrate flywayValidate`し、成功後に値を0へ戻した。migration/validate exit 0。合成教師fixture作成テストは1 passed、0 skipped/failures/errors、exit 0。
  - 専用appのlogin HTTP 200、合成教師ログイン後の課題編集をブラウザーで確認。混在大小文字のタイトル`mY HintCase`がそのまま表示され、入力/プレビュー双方の行番号要素は0件、プレビューCodeMirrorの高さは36px。狭幅375pxでプレビューカード225×150px、ページ幅も375pxで横溢れなし。
  - 課題一覧では状態が独立セルに「設定済み」と表示され、「プロンプト設定」は操作セルに存在し、準備済み合成課題の公開操作が有効。現行ローカルDBの有効surveyは1件（ID 4）、6設問/30選択肢であることをread-only queryで確認。設定/回答データは変更していない。
  - `ppe-p20-task-preview`のapp/DB/runner、専用volume/networkを`docker compose -p ppe-p20-task-preview down -v --remove-orphans`で清掃し、共有Compose serviceは維持した。
- 影響/原因: ホストJDK不在、テストアサーション誤り、隔離DB初回migration設定、およびブラウザーツールの画像取得制約。JDK依存はプロジェクト標準Docker環境へ切替、テストとmigration設定は修正後に再実行成功。実装不具合は確認されていない。
- 残件: 課題編集UIの通常幅/狭幅ブラウザーDOM受入は成功。画像証跡と生徒向けアンケートの認証browserでの回答/保存再読込は未確認で、工程20全体は`PARTIAL / NEEDS_SIGNOFF`のまま維持する。

### 工程20 P20-04 改訂再公開通知ブラウザー受入（2026-10-14 10:52 JST）

- 対象/手順: 専用Compose project `ppe-p20-revision-20261015`、空の合成MySQL `ppe_teacher_task_test_rev20261015`、app port 18095を使用。V1–V34をmigration/validateし、合成教師・生徒・公開済み旧課題・設定済み合成promptの改訂版を作成。Gemini実API・実利用データ・共有8080/DBは使用していない。
- 期待結果: 改訂の再公開で旧課題/割当を正しく置換し、生徒へ新revisionを対象とする改訂通知を1件だけ作成。認証後の通知クリックで新revision editorへ遷移し、通知を既読化する。DB再読込でも状態が保持される。
- 実際の事象/検証時エラー:
  - 初回fixture実行はGradle exit 0だったが、opt-in環境変数をCompose `run`へ渡さずJUnitで1 test skippedとなり、fixtureは生成されなかった。再実行もGradleのup-to-date判定でtestを実行しなかった。
  - 1回目のPlaywright実行はhost-gateway経由でloopback bindされたHTTP portへ接続し、`ERR_CONNECTION_REFUSED`でexit 1。専用internal networkで`npm ci`を実行した試行も、networkがexternal outboundを持たないため`registry.npmjs.org`の名前解決エラー `EAI_AGAIN` でexit 1。`*.localhost`がChromium内でloopbackとして解決されるため、内部network接続時にresolver ruleを設定しない再試行も接続拒否となった。
  - 追加のGradle revision test実行は、稼働中Gretty appとGradle user/project cacheを共有してjournal/fileHashes lock timeoutとなり、2回exit 1。最初の再試行では`GRADLE_USER_HOME`だけを分けたが、project cache lockが残った。
- 対応/再検証:
  - opt-in変数はCompose `run -e`で明示し、`--rerun-tasks`を指定。fixture作成testは1 test、1 passed、0 skipped/failures/errors、exit 0。
  - `npm ci`をoutbound可能なPlaywright image containerで実行（3 packages追加、0 vulnerabilities）。browser containerは専用Compose internal networkへ接続し、Chromium `PPE_P20_HOST_RESOLVER_RULES`でstudent/teacher portalをapp IPへ向けた。Playwrightは1 test、1 passed、0 failed、exit 0。誤資格情報拒否、教師ログイン後の旧課題`要更新`/新版`公開中`、生徒ログイン、改訂通知の未読表示、通知から新版editorへ遷移、改訂タイトル表示、既読表示を確認。
  - 別のGradle user homeに加えて`--project-cache-dir=/tmp/p20-revision-project-cache`を指定し、改訂/即時再公開、合成providerによる改訂prompt生成、予約改訂workerの3 DB testsを再実行。3 passed、0 skipped/failures/errors、exit 0。
  - DB再読込で旧task revision 1=`requires_update`、旧assignment=`archived`、新task revision 2=`published`、`supersedes_task_id`が旧taskを参照、新assignment=`published`を確認。新assignment向け`task_published`通知は1件で、ブラウザー操作後`read_at`が設定済み。MySQL `log_bin_trust_function_creators=0`を確認。
- 影響/原因: すべて試験環境のenv転送、network routing、共有Gradle cache競合に限定。アプリの不具合ではなく、修正後の専用DB/Playwright受入は成功。
- 後処理/残件: 専用Compose container/volume/networkと一時fixtureを削除し、共有サービスを維持した。P20-04の改訂版通知縦断範囲は合格。P20-03/05/09の残る横断範囲、別browser/OS、本番受入は別途未確認。

### 工程20 P20-03 教師の生徒アカウント管理DB受入（2026-10-14 09:27 JST）

- 対象/手順: 専用Compose project `ppe-p20-student-accounts-20261014` と空の合成MySQL `ppe_student_accounts_test_p20_20261014` でV1–V34を適用し、`StudentAccountManagementDatabaseTest`を実行。
- 期待結果: 両セキュリティレベルの作成、個別資格情報、本人ログイン/パスワード変更、権限・状態・版境界、並行採番とrollbackを検証し、専用環境だけを使う。
- 実際の事象/検証時エラー: 初回suiteはexit 1、2 tests failed。`TeacherCredentialCipher.configured`が`Student credential key is invalid`を返し、causeは`Illegal base64 character 2d`。テスト環境へ合成鍵をBase64ではなくハイフンを含む通常文字列で渡していた。失敗時点でテストfixtureは作成前またはteardown済みで、DBは変更されていない。
- 対応/再検証: 32-byte合成鍵をBase64形式でruntime-onlyに設定し直した。再実行はexit 0、2 tests、2 passed、0 skipped/failures/errors。DBでV34、`log_bin_trust_function_creators=0`、`users`/`schools`/`classrooms`/`student_profiles`/`student_teacher_credentials`/`audit_logs`各0件を確認。専用container/volume/networkはdown後に残存各0。共有8080サービスは稼働維持。
- 影響/原因: 実装ではなく試験用環境変数の形式誤り。修正後、両レベルの発行資格情報と本人変更、再設定・再利用拒否、学校/クラス所有範囲、状態変更によるsession失効、競合採番、失敗時rollbackを実MySQLで確認した。
- 残件: 当該P20-03 DB範囲は合格。認証HTTP/browserの全ロール横断と工程20の残りのopt-in/業務導線は未確認。

### 工程20 P20-03 教師セルフアカウントDB受入の初回設定（2026-10-14 08:42–09:02 JST）

- 対象/手順: 専用Compose project `ppe-p20-self-account-20261014` と空の合成MySQL `ppe_teacher_self_test_p20_20261014` でV1–V34を適用し、`TeacherSelfAccountDatabaseTest`を実行。
- 期待結果: 専用DBへ接続してmigration/validateを完了し、教師自身の初回・任意パスワード変更、入力拒否、失効/権限境界、並行更新を4件すべて検証する。試験終了後に合成データと専用環境を除去する。
- 実際の事象/検証時エラー:
  - 初回`flywayMigrate flywayValidate`はexit 1、SQLState `28000` / MySQL error 1045: `Access denied for user 'app'@'172.25.0.3' (using password: YES)`。`docker compose up -d db`の呼出しにだけ環境変数を渡し、後続の独立shell呼出しではComposeが既定値を補間していた。migrationはDB接続前に失敗し、テスト本体・業務データ変更には到達していない。
  - 設定確認の初回SQLは`flyway_schema_history`を照会した際に`ERROR 1046 (3D000): No database selected`。先行する読み取りでtrust設定値`0`を確認した後の失敗であり、DBへの更新はない。
- 対応/再検証:
  - 各Compose呼出しへ専用のsynthetic DB名・ユーザー・資格情報を明示してmigrationを再実行。`flywayMigrate flywayValidate`はexit 0、`BUILD SUCCESSFUL`、V34まで適用・validate成功。MySQL `log_bin_trust_function_creators`はmigration中だけ1とし、完了後0へ復元。
  - 確認SQLでtrust値0、migration V34、`users`件数0を確認。`TeacherSelfAccountDatabaseTest`はexit 0、4 tests、4 passed、0 skipped/failures/errors。初回必須パスワード変更、任意変更、管理者リセット/再設定、無効入力とwrite failureのrollback、古いversion/停止済み/偽装roleの拒否、同時更新で一方のみ成功を確認。
  - 試験後のDB確認はtrust値0、`users`/`credential_history`/`password_reset_records`/`audit_logs`各0件。`docker compose down -v`後は専用container/volume/network各0。共有8080サービスは稼働維持。実データ・Gemini実APIは不使用。
- 影響/原因: Compose環境変数のプロセススコープとMySQL確認queryのDB選択漏れに限定され、アプリ/DB schemaの不具合ではない。接続設定の修正とDB名指定後にすべて再検証成功。
- 残件: 当該教師セルフアカウントDB範囲は合格。P20-03の全ロールHTTP/browser横断と工程20の残るopt-in範囲は未確認。

### 工程20 P20-09 管理者教師管理ブラウザー受入（2026-10-14）

- 対象/手順: 専用Compose/空MySQLで管理者fixtureを作り、Playwrightから管理画面へログインし、合成教師アカウント作成・学校/機能権限の保存・再読込を確認。
- 期待結果: 無効資格情報は拒否され、固定ID `admin` の認証後のみ管理操作が可能。作成した教師は指定校と選択した機能権限でDBへ保存される。発行資格情報は一度だけ画面表示され、閉じた後に消去される。375px幅で横はみ出しなし。
- 実際の事象/検証時エラー:
  - 起動直後のホストreadiness probeはGradle依存解決中にcurl exit 52 / HTTP 000（empty reply）。Tomcat起動後のコンテナ内リクエストはHTTP 200。
  - Playwright runnerへテスト一式をリポジトリルート扱いで配置した初回実行はfixture相対パス`/build/...properties`がなくexit 1。コンテナ間`docker cp`を試したが未対応でexit 1となった。
  - runner階層修正後の1回目はfeature checkbox全件に対するlocatorが9要素に一致しstrict-mode errorでexit 1。2回目は作成・再読込まで成功したが、画面表示名のassertを`課題管理`と誤記しexit 1（実表示は`課題編集`）。
- 対応/再検証:
  - runner内に`e2e/`と`build/`の階層を再現し、workspaceの生成fixture fileをrunnerへコピー。選択feature checkboxを1件ずつ操作し、画面上の仕様ラベルに合うassertへ修正。
  - `TeacherAccountDatabaseTest.createsRetainedAdminManagementBrowserFixture`はexit 0、1 passed。最終Playwright `admin-teacher-management.spec.js`はexit 0、1 passed。誤admin password拒否、admin login、教師作成、学校・`task-management`権限、資格情報の一度限り表示・消去、再読込後の表示、375px document/body幅を確認。DB read-backでactive/学校/機能権限/password hashを確認した。
  - 最初の失敗後に作成された合成教師レコードも含め、専用DB/volumeを破棄。共有8080アプリ、共有DB、本番サイト、外部AIには接続していない。秘密値は記録していない。
- 影響/原因: 失敗はPlaywright runner配置、locator設計、期待ラベルの誤りに限定される。認証/管理処理の最終受入に失敗はなく、再実行で期待状態とDB永続化を確認。
- 残件: 管理者の教師作成導線はこの合成データ・Chromium・375px範囲で受入済み。全管理操作、他ブラウザー/OS、工程20の全横断導線は未確認。

### 工程20 P20-09 管理者作成教師の認証・権限境界browser追試（2026-10-14 11:58 JST）

- 対象/手順: 専用Compose project `ppe-p20-authz-20261014`、合成DB `ppe_teacher_test_p20authz_20261014`、app port 18096を使用。V1〜V34適用済みDBで、管理者が作成した教師の認証後アクセスをPlaywrightで確認した。
- 期待結果: 発行資格情報で初回ログイン・必須パスワード変更後、付与済み`task-management`の課題編集はHTTP 200、未付与`submission-review`の提出確認はHTTP 403となり、DB再読込でも学校・権限が一致する。
- 実際の事象/検証時エラー:
  - 1回目はspecをrunnerのrootへ置いたためPlaywrightがテストを検出できずexit 1。phase20へ移動後は`./helpers`未配置でmodule解決に失敗しexit 1。次の実行ではfixtureの相対パス`/runner/build/...properties`がなくexit 1。spec/共通helper/生成build fixtureの階層をrunner内で再現して解消した。
  - 初回ブラウザー試験は120秒timeout。管理者ページと同じBrowserContextを使ったため管理者cookieが残り、教師login URLがログインフォームではなく管理者セッションへ遷移していた。独立BrowserContextに分離した後、初回ログインは必須パスワード変更画面へ正しく遷移したため、home直行を期待した試験が失敗した。変更後の教師は有効な`task-management`権限に基づき`/teacher/task`へ遷移したため、homeを期待するassertも不一致となった。
  - readiness確認で`localhost`を使った際はhost別portal routingによるHTTP 302だった。教師host `teacher.localhost`へ修正後HTTP 200。DB確認でroot資格情報を誤指定した初回照会はMySQL 1045で失敗したが、DB変更はなく、コンテナー内のruntime-only設定を使う照会で再確認した。
- 対応/再検証: specで教師用の独立BrowserContextを作成し、必須パスワード変更と再ログインを実施する流れに変更。テストは`node --check`成功、Playwright最終実行exit 0・1 passed。課題編集ページはHTTP 200、未付与の提出確認ページはHTTP 403。DB read-backで最新の合成教師はactive、`teacher_must_change_password=0`、fixture学校のaccess enabled、`task-management` enabled、`submission-review` disabled、enabled feature数1を確認。DBの`log_bin_trust_function_creators=0`、教師login readiness HTTP 200も再確認した。
- 影響/原因: 初期失敗はrunner階層・fixture mount、管理者/教師のcookie分離、初回パスワード更新と権限別landing routeのテスト前提に起因し、製品コードの不具合ではない。専用DB・合成資格情報のみで試験し、Gemini実API、実利用データ、共有port 8080、本番サイトは使用していない。
- 後処理/残件: DB read-back後、`docker compose -p ppe-p20-authz-20261014 -f /tmp/ppe-p20-authz-20261014/docker-compose.yml down -v --remove-orphans`はexit 0で専用container/volume/networkを削除し、残存数各0を確認。一時作業コピー`/tmp/ppe-p20-authz-20261014`も削除した。Composeは一時コピーの`.env`未指定に関する空値警告を出したが、固定project名の対象資源はすべて削除され、共有資源への影響はない。確認した1つの教師権限組合せをもって全feature route・学校/所有範囲の網羅とはせず、工程20は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### 工程20 P20-06 合成生徒の提出・再提出E2E（2026-10-14）

- 対象/手順: 新しい専用Compose project/schemaにV34を適用し、重複提出・revision 2再提出のMySQL regressionと、Playwrightでの認証→提出前チェック→revision 1提出→再提出開始→revision 2提出を検証。
- 期待結果: 同一冪等キーの再送は同じ提出を返し、再提出は別のsubmission idでrevision 2として保存される。画面は各確定後に対応する評価確認へ進む。実AI・実利用者データは使わない。
- 実際の事象/検証時エラー:
  - 初回`duplicateSubmissionIsIdempotentAndResubmissionPersistsTheNextRevision`はexit 1。test teardownが`schools`を削除するとき、V34の`school_common_prompt_versions`参照FKにより`SQLIntegrityConstraintViolationException`となった。提出処理のassertは通過し、後片付けで失敗。
  - 初回Playwrightは`app` hostnameがChromium HSTS対象のためHTTP起動が`ERR_SSL_PROTOCOL_ERROR`。次の実行は分離したDNS名からCDNへ到達できずCodeMirrorが初期化されなかった。CDNを読めるrunnerでも、insecure originではブラウザーの`crypto.randomUUID`が提供されず提出確認エラーとなった。
  - 読取専用DB確認の初回SQLはdatabase未選択、続くqueryでは存在しない`evaluation_requests.created_at`をORDER BYに指定して失敗。どちらもデータ変更前の確認queryで、DB状態は変更されなかった。
- 対応/再検証:
  - `TeacherTaskDatabaseTest.removeOnlySyntheticRows`のcleanupに、fixture学校に属する`school_common_prompt_versions`だけを削除する処理を追加。専用DBを作り直してV34 migration/validate後に同じregressionを再実行し、exit 0・1 passed。ブラウザーfixture testもexit 0・1 passed。
  - ブラウザーは専用appで`GEMINI_API_KEY`を空にしてから実行。`.localhost` secure originへ切替え、Playwright runnerだけにappのcontainer-IP resolver mappingを設定、CodeMirror用CDN取得を可能にした。`editor-submit-resubmission.spec.js`はexit 0・1 passed。DB read-backでrevision 1と2がともに`accepted`、各コードが保存済み。
  - 最終DB確認は専用schemaを指定し、`log_bin_trust_function_creators=0`とFlyway V34を確認。評価requestはAPIキー未設定のためfailedとなったが、外部Gemini呼び出しなし。専用環境終了後、専用container/volume/networkを削除し共有8080/DBを維持。
- 影響/原因: FK違反はテストfixture teardownの新migration追従漏れであり、製品提出/再提出処理の失敗ではない。Playwrightの初回失敗はHSTS、テストrunnerの外向きネットワーク制限、secure-context要件による環境設定不整合。SQL確認エラーもquery作成ミスであり、データは変更していない。
- 残件: P20-06のMySQL冪等性・revision 2・実UI提出再提出は合格。AI評価の完了、別browser/OS、工程20の残るP20-09主要導線と横断範囲は別途未確認。

### 工程20 P20-06 生徒エディター再追試（2026-10-14 05:49 JST）

- 対象/手順: local8080上の合成assignment 17を表示していた認証済みstudent editorを390px幅で確認。提出前I/Oチェック結果とdocument/bodyの横幅を確認後、生徒ホームをGETした。
- 期待結果: 390pxでも横はみ出しなく提出前チェックが表示される。認証が維持されていれば、生徒ホームと提出後の再提出導線も継続確認する。
- 実際の事象/検証時エラー: editor画面ではI/Oチェック2/2一致、document/bodyとも390pxで横はみ出しなし。その後`/student/home`への遷移は`/student/account/login`へredirectされ、認証済みホーム表示を確認できなかった。HTTP statusの個別計測はしていない。
- 対応/再検証: 認証情報がない状態でログインを試行せず、最終提出・再提出も行わなかった。提出前確認までのbrowser証跡としてのみ記録し、実UI提出/再提出受入を未確認のままとした。
- 影響/残件: 合成データを含めDB書込み・提出状態変更なし。実API/外部AI呼び出し、本番サイト接続、プロトタイプ変更なし。P20-06の自動DBテストでは同一冪等キーとrevision 2を検証済みだが、認証済みUIによる実提出・再提出は未確認。

### プロンプト版管理仕様と古い画面契約テストの不一致（2026-10-14）

- 対象/手順: エディター、通知、課題編集、共通プロンプト版管理の関連テストを実行。初回 `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/ppe-gradle-related-ui app gradle test --tests control.student.StudentEditorControlTest --tests entity.StudentEditorPageTest --tests servlet.student.StudentHomeNavigationContractTest --tests servlet.student.StudentNotificationUiContractTest --tests control.teacher.TeacherTaskControlTest --tests control.teacher.TeacherTaskInputValidatorTest --tests servlet.teacher.TeacherTaskServletTest --tests servlet.teacher.TeacherTaskFormTest --tests servlet.teacher.TeacherTaskPromptRevision162ViewTest --no-daemon` は48件中1件失敗した。
- 期待結果: 課題間の共通プロンプトはコピー用ボタンではなく学校単位の版履歴として管理し、最新版を新しい課題プロンプトの既定値とする。編集、課題固有の適用版、評価履歴は分離する。
- 実際の事象/検証時エラー: `TeacherTaskPromptRevision162ViewTest` が、現行仕様で廃止された「他課題から下書きへコピー」画面の文字列を期待して失敗（`TeacherTaskPromptRevision162ViewTest.java:46`）。実装画面は学校共通プロンプト版履歴を表示し、コピー操作は提供していなかった。その他47件は成功。
- 対応/再検証: テストを現行の第172/177版仕様へ更新し、学校共通版履歴/latest既定値とpreview再発見を検証。上記の同じコマンドを再実行しBUILD SUCCESSFUL、全48件成功、失敗/エラー/skip 0。仕様に反してコピーUIを再導入していない。
- 影響/残件: 製品コードの実行障害ではなく、検証コードに残った旧仕様の期待値。DB・外部API・本番サイト・実利用者データへの操作なし。

### コード配信フォルダ展開アイコンの統一（2026-10-14）

- 対象/手順: コード配信ツリーのトグルは展開用の二重山形SVGを使っていた一方、画面CSSは下向き三角用の専用クラスを定義していたため、演習ツリーの表示と異なっていた。distribution JavaScript/JSPとUI契約テストを更新し、`servlet.teacher.AccountDistributionUiContractTest`を実行した。
- 期待結果: フォルダの開閉状態に応じて共通の三角アイコンが回転し、ボタンのアクセシブル名と開閉動作を維持する。
- 実際の事象/検証時エラー: 変更前はJSが`lesson-tree-folder-toggle`と`controlIcon('expand')`を設定しており、当画面のCSSが定義する`distribution-tree-folder-toggle`/三角表示と一致していなかった。対象テスト実行では失敗なし。`git diff --check`は実行済み。
- 対応/再検証: CSSの既存仕様に合わせて専用クラスと`▾`アイコンを設定し、画面アセット版を171へ更新。展開状態に応じた`is-collapsed`、aria-label、既存イベント処理は維持した。`docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/ppe-gradle-ui-acceptance app gradle test --tests servlet.teacher.AccountDistributionUiContractTest --tests servlet.student.EvaluationPrototypeUiContractTest --tests servlet.teacher.TeacherReviewServletTest --no-daemon` はBUILD SUCCESSFUL。対象15件（各クラス5件）、15 passed、失敗/エラー/skip 0。`git diff --check`も成功。
- 影響/残件: 配信履歴を含む認証済み画面のブラウザー確認は未実施。外部API・DB更新・プロトタイプ変更はない。

### 教師評価詳細のプロトタイプ外表示を除去（2026-10-14 05:25 JST）

- 対象/手順: 教師評価詳細のプロトタイプと現行JSP/JavaScriptを照合し、総合フィードバック文・process analysis文を表示しないようにし、理由フィルターを全件/2観点に固定した。`servlet.student.EvaluationPrototypeUiContractTest`と`servlet.teacher.TeacherReviewServletTest`を実行した。
- 期待結果: 実データの2観点・観点別スコア・理由・根拠を保持し、プロトタイプにない文章と「その他」理由フィルターを表示しない。
- 実際の事象/検証時エラー: 初回コンパイル/テスト失敗なし。GradleはBUILD SUCCESSFUL、対象10件（5+5）全て成功、失敗/エラー/skip 0。`git diff --check`も成功。
- 対応/再検証: JSPから`feedbackSummary`/`processAnalysis`表示先を除き、JavaScriptの表示更新を削除。理由フィルターは「すべて」「思考・判断・表現」「態度」の3項目に限定。プロトタイプ側は変更していない。DB/API/外部AIは使用していない。
- 影響/残件: 認証済み教師browserでの画面受入は未実施。既存8080アプリのstudent browserをhomeへ移動した際、sessionが失効してログイン画面へredirectされたため、UI表示を成功扱いしない。

### 生徒アンケート評価提示の範囲調整（2026-10-14 05:09 JST）

- 対象/手順: アンケート画面の「システムが出した評価」をプロトタイプと照合し、評価表示から余分な評価全体フィードバック文を除外。設問3・4の表示ラベルとDB保存値の分離を確認するため、StudentSurveyOptionLabelTest、StudentSurveyUiContractTest、関連するprompt/distribution UI・制御テストをGradleで実行した。
- 期待結果: 評価カードには当該評価の2観点の実スコア・観点別説明だけを表示し、設問3・4はDBの選択値を維持してプロトタイプ文言を表示する。テストが全てコンパイル・成功する。
- 実際の事象/検証時エラー: 初回`compileTestJava`で、UI契約テストに追加した`assertFalse`のstatic importがなくコンパイルに失敗した。製品コードの失敗やDB/APIエラーではない。
- 対応/再検証: `StudentSurveyUiContractTest`へstatic importを追加。`docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/ppe-gradle-prompt-queue app gradle test --tests entity.StudentSurveyOptionLabelTest --tests servlet.student.StudentSurveyUiContractTest --tests servlet.student.SurveyFourStepRuntimeTest --tests control.teacher.TeacherPromptAiClientTest --tests control.teacher.TeacherPromptGenerationQueueTest --tests entity.TeacherPromptVersionTest --tests servlet.teacher.TeacherPromptServletTest --tests servlet.teacher.AccountDistributionUiContractTest --tests control.teacher.TeacherDistributionDatabaseTest --no-daemon`はBUILD SUCCESSFUL。対象31件のうち28 passed、0 failed、0 errors、3 skipped。skipはSurveyFourStepRuntimeTest 1件と、専用DB設定がないためTeacherDistributionDatabaseTest 2件。`git diff --check`も成功した。
- 影響/残件: 実DBのアンケート選択肢・回答履歴は変更していない。認証ブラウザーでの画面受入とDB付きdistributionテストは未実施。初回コンパイル失敗は修正済みで、ブラウザー確認を成功扱いしない。

### プロンプト設計の本番形式合成評価例（2026-10-14 02:01 JST）

- 対象/手順: 合成評価例のログ・評価スキーマ変更に対し、`docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/ppe-gradle-prompt-examples app gradle test --tests control.teacher.TeacherPromptAiClientTest --tests entity.TeacherPromptVersionTest --no-daemon`を実行。外部AI・実利用者データ・本番サイトは使用していない。
- 期待結果: 本番評価入力と同じコード編集ログと評価応答を含む合成例がコンパイルされ、対象テストを通過する。
- 実際の事象/検証時エラー: 1回目は `TeacherPromptControl.java` でメソッド宣言が別メソッド内に入った既存作業ツリーの構文不整合によりcompileJavaが失敗。配置を修正した2回目は `TeacherPromptServlet.java` がJavaBeans getterをrecord accessorとして呼び出しており、2つのメソッドを解決できずcompileJavaが失敗した。
- 対応/再検証: `copyPromptFromTask` と `duplicatePromptToLatestDraft` を同じクラス階層の独立メソッドに戻し、Servletを既存JavaBeans getter呼び出しに修正。上記の同じGradleコマンドを再実行し、compileJava/compileTestJavaを含めBUILD SUCCESSFUL、対象2テストクラス成功、失敗0で完了した。関連コードは検証可能なローカルソースのみ変更した。
- 影響/残件: 最初の2回はいずれもビルド前のコンパイル失敗であり、DB変更・外部API呼び出しはない。今回変更した合成ログ・評価スキーマのDB永続化とブラウザー表示は未検証。IDE診断上のGson解決表示はGradle成功と区別し、IDE設定は変更していない。
- 非同期キュー追加後の再検証: `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/ppe-gradle-prompt-queue app gradle test --tests control.teacher.TeacherPromptAiClientTest --tests control.teacher.TeacherPromptGenerationQueueTest --tests entity.TeacherPromptVersionTest --tests servlet.teacher.TeacherPromptServletTest --no-daemon` はBUILD SUCCESSFUL。15 passed、0 failed、0 skipped。キューが呼出し元とは別スレッドで処理すること、状態APIの必須パラメーター拒否、本番形式評価JSON/ログの生成と表示を確認。DB・外部API・ブラウザー受入は未実施。

### 工程20 授業演習フォルダ行操作のbrowser受入（2026-10-13 23:25 JST）

- 対象/手順: ローカル8080の認証済み生徒画面で、合成演習`exerciseId=17`、フォルダ`entryId=47`を使用。最初に`http://localhost:8080/student/exercise?exerciseId=17`を開いたところログイン画面へredirectされたため、認証済みタブと同じ`student.localhost` hostで再確認した。
- 期待結果: フォルダ名ボタンとフォルダ行の空き領域から展開・折りたたみでき、既存の選択/path表示を保つ。対話部品の操作では行開閉を発生させない。
- 実際の事象/検証時エラー: `node --check src/main/webapp/js/student/exercise/exercise.js`はexit 127（`node: command not found`）。通常の`clickElement`は10秒でlocatorのstable待ちがtimeoutし、`scrollIntoViewIfNeeded`も約10秒でstable待ちがtimeoutした。要素をスクロールした後の`page.mouse.click`とforce locator clickでも状態変化を確認できず、原因は特定できていない。
- 対応/再検証: JavaScript/JSPのProblems診断はエラー0件、`git diff --check`はexit 0。認証済みbrowser上でfolder-name buttonのclick event dispatch後に`aria-expanded=true`、当該folder path選択、`main.py`表示を確認。folder headerへのdispatchでは`aria-expanded=false`。三角toggleへのdispatchでも`true`/`false`を確認した。checkbox/menu領域へのdispatchではフォルダは開閉せず、試験中の選択件数は0件へ戻した。これらはイベントハンドラと再描画の動作確認であり、通常ポインター操作の受入とは区別する。コードは行開閉を共通化し、ボタン/入力/リンク/メニュー内のクリックは行ハンドラで除外した。確認中のDB/演習データ変更はない。
- 影響/残件: 実ブラウザーの通常ポインター操作は未確認。`node --check`は実行環境にNode.jsが見つからず未実施だが、ブラウザー実行とエディター診断で構文・動作を確認した。工程20の受入完了とは扱わない。

### 工程20 生徒エディター提出前チェックの再確認（2026-10-13 22:54 JST）

- 対象/手順: ローカル8080の合成課題 `assignmentId=17` を再読込し、提出前チェックを再実行した。通常のブラウザークリックとPlaywright locator clickは各10秒でタイムアウトしたため、locatorのclickイベントをdispatchしてから25秒待機した。最終提出ボタンは押していない。
- 期待結果: `input(prompt)`のpromptだけを比較出力から除外し、登録済み2ケースの実出力を正しく比較する。ページ再読込後の空の実行ターミナルが本画面の不具合に該当するかも、仕様・計画と照合する。
- 実際の事象/検証時エラー: 通常clickと強制clickはブラウザーツール上で完了せず、結果モーダルも表示されなかった。locator clickイベントをdispatchした確認では、モーダルに `2/2 件一致`、実出力 `6` / `8` が表示された。再読込直後の実行ターミナルは「まだ実行していません。」だった。
- 影響/原因: clickツールのタイムアウト原因は未特定であり、この追試だけでは通常のポインター操作を受け入れた証拠にしない。提出前チェックは成功し、最終提出・下書き更新は行っていない。提出前チェックはControl上でrunner実行のみを行い、この操作からDBへ提出・実行履歴を記録しない。確認した範囲でDB状態変更はない。
- 対応/再確認: 機能仕様は課題エディターの実行結果を「次の実行まで保持」と定め、再読込時の下書き復元は明記する一方、最新実行結果の再読込復元は求めていない。実装計画のDB再読込条件も保存内容・ログ・提出版・テスト結果であり、実行結果は対象に含めていない。授業演習のファイル切替時に結果を維持する別要件を課題エディターへ適用せず、再読込後の空表示はこの受入範囲では不具合と判定しない。コード変更なし。
- 残件: browser locatorの通常clickを使った再確認、および工程20の他の未確認ブラウザー項目は未完了。最終提出は未実施。

### Rev.171 提出前Python入力プロンプト比較の修正・検証（2026-10-13 22:20 JST）

- 対象/手順: ローカルソースのPython runnerと生徒エディター提出前チェック。合成入力/コードだけでrunner単体・HTTP契約・Java Control・認証済みローカルbrowserを検証した。生徒の提出確定、外部AI、本番サイトは使用していない。
- 期待結果: 提出前チェックだけ`input(prompt)`のprompt文字列を比較用stdoutから除外し、stdinと明示的なプログラム出力は維持する。通常実行と対話実行は従来どおりpromptを表示する。
- 実際の事象/検証時エラー:
  - 初回`/Users/t.toida/programming-process-evaluator/.venv/bin/python -m unittest -v test_runner.py`はexit 1。リポジトリrootからのmodule指定で`test_runner`をimportできなかった。
  - ホストの`gradle test --tests control.student.StudentEditorControlTest --no-daemon`はexit 1。共有`~/.gradle/caches/journal-1`のlock timeoutでbuild開始前に終了（所有PID不明）。
  - 分離Gradle user homeでのホスト再試行はexit 1。プロジェクト要求のJDK 21がホストに存在せず、toolchain downloadも未設定。
  - `docker compose run --rm --no-deps app gradle test --tests control.student.StudentEditorControlTest --no-daemon`はexit 1。共有`gradle-cache` journal lock（owner PID 350、operation unknown）により開始できず、owner processは特定していない。
  - 対象コンテナ確認用の最初の`docker inspect --format`はexit 64。Docker Go templateに`hasPrefix`関数がなく、再実行では選択項目だけを`jq`で抽出した。
- 影響/原因: 最初の失敗はテストコマンドのmodule指定または共有Gradle cache/JDK環境の制約で、実装不具合を示すものではない。誤ったmodule指定はファイル直接実行に直し、Gradleは既存cacheを変更せず一時user homeを使って再実行した。
- 対応/再検証:
  - `/Users/t.toida/programming-process-evaluator/.venv/bin/python containers/python-runner/test_runner.py` — exit 0、24 passed。通常実行でpromptが表示されること、提出前モードではpromptのみ抑制され明示print出力を保つこと、HTTP endpointがboolean optionを既定falseで転送し不正型をHTTP 400で拒否することを確認。
  - `docker compose run --rm --no-deps -e GRADLE_USER_HOME=/tmp/ppe-gradle-editor-check app gradle test --tests control.student.StudentEditorControlTest --no-daemon` — exit 0、1 passed、0 failed、0 skipped。main/test sourceのcompileも成功。
  - 対象はCompose project `programming-process-evaluator`の`app-live`、bind port `127.0.0.1:8080`、DB target `db/programming_process_evaluator`、source mount `/Users/t.toida/programming-process-evaluator`、runner URL `http://python-runner:8090`と確認した。対象runnerだけ再build/recreateしhealthyを確認、source bind mount付き`app-live`だけrestartして生徒login URLがHTTP 200になることを確認した。本番やDB service/volumeは変更していない。
  - 再起動後のブラウザーは一度ログインへredirectされた。ユーザーが合成生徒として再ログインした後、assignment 17の提出前チェックでHTTP応答結果のモーダルに`2/2件一致`（`6`/`8`）を確認し、モーダル内の最終「提出する」は押していない。
  - 通常対話実行でもprompt「入力してください」と入力echo `>>> 1 2 3`、出力`6`を確認した。最初の試行は30秒入力待ちの間に送信操作が間に合わず`input_timeout`となり、再試行ではEnter送信で完了。両実行のexecution logは合成デモDBへ記録されたが、下書き保存・提出状態は変更していない。
- 残件: 以前の409の更新者・厳密な発生時系列は引き続き未特定。今回の現行版での提出前チェックは成功し、最終提出をしていない。

### Rev.170 配信履歴レイアウト・教師画面受入（2026-10-13）

- 対象/手順: ローカル8080の認証済み教師画面で配信履歴詳細を開き、操作履歴表の通常幅/狭幅を確認。プロンプト画面のボタン導線も確認し、UI契約テストとWARを実行した。
- 期待結果: 操作履歴の列が内容幅に応じて折り返され、通常幅では表全体が表示される。プロンプト保存ボタンは指定ラベル・順序になり、関連テストとWAR作成が成功する。
- 実際の事象: 初回の`JAVA_HOME='/Users/t.toida/.jdk/jdk-21.0.10(1)/jdk-21.0.10+7/Contents/Home' gradle --no-daemon test --tests servlet.teacher.AccountDistributionUiContractTest`はexit 1。共有`~/.gradle/caches/journal-1`のロック待ちでGradleを開始できなかった（所有PIDは特定されず）。この失敗ではテストは実行されていない。
- 影響/原因: 共有Gradle user homeを使用した際のキャッシュ競合。アプリ/DBデータ変更、外部API呼び出し、本番アクセスはない。
- 対応/再検証:
  - 操作履歴表に教師共有テーブルCSSの強制`nowrap`/`auto`設定が優先され、pre内JSONの長さでテーブルが2564pxまで拡大することを確認。配信履歴表に限定して固定レイアウト・セル折り返し・740pxのスクロール可能最小幅を適用し、画面アセット版を170へ更新した。通常幅1280pxでは表とスクロール領域がともに1074pxで一致し、全列表示を確認。狭幅606pxでは表740pxを内側スクロール領域434pxで扱い、無制限な横拡大を解消した。
  - プロンプト画面の「教師対応を保存」を「揺らぎ項目指示を保存」に変更し、評価例生成ボタンの右側へ移動。成功通知文言も対応させ、表示名/順序を検査するテストを追加した。
  - `GRADLE_USER_HOME='/Users/t.toida/programming-process-evaluator/.gradle-rev170-final' JAVA_HOME='/Users/t.toida/.jdk/jdk-21.0.10(1)/jdk-21.0.10+7/Contents/Home' gradle --no-daemon test --tests servlet.teacher.TeacherPromptApplicationViewTest --tests servlet.teacher.AccountDistributionUiContractTest --tests servlet.teacher.TeacherExerciseServletTest war`はexit 0、11 passed、0 failed、0 skipped。WAR作成も成功。
  - `git diff --check`は成功。

### Rev.170 生徒画面受入中の提出前チェック競合（2026-10-13）

- 対象/手順: ローカル8080の確認用生徒で`/student/editor?assignmentId=17`を開き、既存の初期コードに対して「提出する」（提出前チェック）を実行した。
- 期待結果: 登録済みテストケースを実行し、標準入力がプログラム出力へ混入せず、想定出力と比較できる。
- 実際の事象: ブラウザーのConsoleにHTTP 409。画面には「他の画面で下書きが更新されています。最新の内容を読み込み直してください。」と表示された。提出前チェックの結果モーダルは開かず、提出は行われていない。エディターの再読込後、コードは変わっていないことを確認し、同じ提出操作は繰り返していない。
- 影響/原因: 下書きの楽観ロック競合。既知の別編集セッションはなく、競合原因は特定できていない。提出・保存による状態変更、本番アクセス、外部API呼び出しはない。
- 対応/再検証:
  - 合成入力`1 2 3`によるエディターの通常実行は成功し、DOM上で入力`>>> 1 2 3`が`terminal-input-echo`要素、プログラム出力`6`が別要素として表示されることを確認した。実行履歴はデモ用DBへ記録されたが、コード保存・提出は行っていない。
  - `GRADLE_USER_HOME='/Users/t.toida/programming-process-evaluator/.gradle-rev170-final' JAVA_HOME='/Users/t.toida/.jdk/jdk-21.0.10(1)/jdk-21.0.10+7/Contents/Home' gradle --no-daemon test --tests control.student.StudentEditorControlTest --tests control.student.StudentNotificationControlTest --tests servlet.student.StudentNotificationServletTest --tests servlet.student.StudentNotificationUiContractTest --tests servlet.student.StudentSurveyUiContractTest --tests servlet.student.EvaluationPrototypeUiContractTest`はexit 0、48 passed、0 failed、0 skipped。
  - 未解決: この合成課題に対する提出前チェックのブラウザー受入。関連ロジックの自動テストは成功しているが、HTTP 409の原因特定と修復後の再検証は未実施。

  ### Rev.170 提出前チェック再調査と入力プロンプトの出力混入（2026-10-13 21:24 JST）

  - 対象/手順: ローカル8080の生徒向け合成課題`assignmentId=17`を認証ブラウザーで確認。元ページの提出前チェック409の調査として、再読込後に更新時刻・コードのdirty状態を読み取り、提出確定はせず「提出する」ボタンからチェックだけを実行した。
  - 期待結果: 読み込んだ現行コードと下書き更新時刻が一致する場合はチェックが実行され、登録済み2ケース（入力`1 2 3`/`10 -2`、出力`6`/`8`）が一致する。
  - 実際の事象:
    - 元のブラウザーページでは`draftUpdatedAt`が空で、コード長61の`input().split()`版が読み込まれていた。再読込後の保存済み状態は`draftUpdatedAt=2026-10-13T12:20:24.362889`（JST 21:20:24）で、コード長71の`input("入力してください")`版だった。この差は古いエディター状態と新しい保存済み状態の競合と整合するが、更新したタブ/利用者および元の409発生時との正確な時系列は特定できていない。
    - 再読込後のチェックPOSTはHTTP 200、JSON応答、2ケース実行だったが、2ケースとも不一致。実際の標準出力は`入力してください6\n`/`入力してください8\n`で、期待出力`6`/`8`と一致しなかった。入力値自体が標準出力へ追記されたのではなく、Python `input(prompt)`のprompt文字列が標準出力へ出力されていた。
    - 最終提出確認ボタンは押していない。下書き保存・提出確定・外部API呼び出しは行っていない。課題とコードは合成デモデータ。
    - セッション失効中の確認ではcheck POSTがHTTP 302となった。ブラウザーの`URLSearchParams`を使った補助採取スクリプトは実行環境で`ReferenceError`になったが、後続の採取でstatus 302を確認。再ログイン後のGETはHTTP 200で、改めて行ったチェックは上記HTTP 200だった。これは認証セッション/採取スクリプトの事象で、現行チェックのJSON応答とは区別する。
  - 影響/原因: 楽観ロックは画面上のコードまたは更新時刻とDBの現行値が一致しない場合に409を返す実装であり、古い画面と後続更新が競合すれば想定された拒否になる。元の409の個別更新者は未特定。別途、現在の自動入出力チェックは`input(prompt)`のpromptを標準出力に含めるため、仕様でpromptを除外すべきかの合意が必要。
  - 対応/再検証: 409の再現目的で同じ古い画面の操作を繰り返さず、再読込後の更新時刻/コードとチェックPOSTのstatus・結果を採取した。出力期待値との不一致を確認したが、実装変更・再検証は未実施。方針確認後、テスト時のみ`input()` promptを除外し、通常実行時のprompt表示は維持する案を検討する。

  ### Rev.170 editor I/O・通知受入のGradle検証（2026-10-13 18:50 JST）

- 対象/手順: editor input/output separation と、削除・閲覧不可課題の生徒通知クリックに関する限定テストを実行した。
- 期待結果: focused Java tests compileし、標準入力が標準出力へ混入しないこと、利用不可の課題通知が同一の安全な結果を返して既読になることを確認する。
- 実際の事象:
  - `./gradlew test --tests 'control.student.StudentEditorControlTest' --tests 'control.student.StudentNotificationControlTest' --tests 'servlet.student.StudentNotificationServletTest' --tests 'servlet.student.StudentNotificationUiContractTest'`はexit 127。repositoryにGradle wrapperがなかった。
  - 同じ`--tests`指定で実行した`gradle test`はexit 1。Gradle toolchainがJava 21を要求したが、環境にはJDK 24のみでtoolchain downloadも未設定だった。
  - JDK 24を使い`--release 21`を指定する一時Gradle init scriptで実行した初回compileはexit 1。focused test用Fake DAOが`final StudentEditorDao`を継承していた。
  - DAOを継承しないloader seamへ修正した後の実行では39件中1件が失敗。既存の期限切れ課題通知テストが、承認要件に反して「既読化しない」従来動作を期待していた。
- JDK 24/`--release 21`での後続compileTestJavaは一度exit 1となり、変更していない`RootServlet`/`TeacherAccountServlet`のsymbolを見つけられないと報告した。両source fileは存在し、main class fileを確認後の再実行は成功した。共有build出力との競合の可能性はあるが、原因は確定していない。
- 影響/原因: いずれも限定テストの実行環境/テストコードまたは新しい既読要件との期待値差であり、データ変更・deploy・外部API呼び出しはない。symbol解決失敗は再現せず、原因未確定。
- 対応/再検証:
  - Fake DAOを`EditorPageLoader`による合成ページ注入へ変更し、期限切れ課題通知テストも安全な共通結果・既読化を期待するよう修正した。
  - `gradle --init-script .gradle-test-init.gradle test --tests 'control.student.StudentEditorControlTest' --tests 'control.student.StudentNotificationControlTest' --tests 'servlet.student.StudentNotificationServletTest' --tests 'servlet.student.StudentNotificationUiContractTest'`をJDK 24/`--release 21`で再実行し、39 passed、0 failed、0 skipped。symbol解決失敗後の再実行も成功した。init scriptは終了時に削除した。

### 工程20 P20-03認証・権限境界受入（2026-10-13 18:16 JST）

- 対象/手順: Compose project `ppe-p20-authz-a1`、専用MySQL schema `ppe_teacher_task_test_authz`、DB port 33393、app port 18093を使用。V1–V33を適用・validateし、教師権限境界のMySQL統合試験、合成教師fixture、教師認証/管理者URL境界のPlaywrightを実施した。
- 期待結果: 空の専用DBへmigration/validationが成功し、アプリが起動する。認証失敗は拒否され、認証済み教師は保護された教師画面へ到達し、管理者機能はHTTP 403で拒否される。試験後に専用資源だけを削除する。
- 実際の事象:
  - 初回の専用app起動はexit 1。Grettyが`tomcat-embed-core-9.0.118.jar`と`ecj-3.26.0.jar`を取得しようとした際、`repo.maven.apache.org: Name or service not known`で依存解決に失敗した。
  - 補助のread-only DB確認コマンドは、誤ったMySQL username指定でexit 1（Access denied）。DB更新処理は実行していない。
- 影響/原因: 起動失敗は専用container内のMaven Central DNS名前解決による依存取得失敗であり、製品コードの不具合を示すものではない。ホストから対象Maven Central artifact endpointへのHEAD要求はHTTP 200となり、container内の一時的な名前解決失敗と判断できた。DB probeの失敗は検証コマンドのusername誤記で、データ変更はない。
- 対応/再検証:
  - 専用MySQLの状態を再確認し、成功migration 33件、`log_bin_trust_function_creators=0`を確認した。
  - `docker compose -p ppe-p20-authz-a1 up -d app`を再実行しexit 0。Tomcat 9.0.118が起動し、教師login URLはHTTP 200を返した。
  - `npm --prefix e2e run test:phase20 -- phase20/authentication.spec.js`はexit 0、1 passed、0 failed。誤資格情報拒否、未認証の保護画面アクセスからloginへの遷移、有効な合成教師login後の保護画面表示、`/admin/teachers`へのHTTP 403を確認した。関連する教師課題権限/停止/所有範囲の専用MySQL試験はexit 0、5 passed、0 skipped/failures/errors。browser fixture testもexit 0、1 passed。
  - `docker compose -p ppe-p20-authz-a1 down -v`で本projectのapp/DB/Python runner、2専用volume、3専用networkを削除し、残存container/volume/networkなしを確認した。共有8080/DB/volume、Gemini実API、実生徒データには触れていない。

### 工程20 予約公開workerから生徒通知までの受入（2026-10-13 13:59 JST）

- 対象/手順: Compose project `ppe-p20-schedpub-a1`、専用MySQL schema `ppe_teacher_task_test_scheduledpub`、port 18092でV1–V33を適用し、合成教師/生徒/予約公開課題を作成。アプリ内workerで公開状態へ遷移させ、生徒の通知一覧から対象editorへ進むPlaywright受入を実施した。
- 期待結果: 空の専用DB migration/validation、合成fixture作成、アプリ起動が成功する。due assignmentのみがworkerにより一度公開され、system auditと対象生徒通知を保存し、認証済み本人の通知操作で対象assignment editorへ遷移・既読化される。
- 実際の事象:
  - 最初の`docker compose -p ppe-p20-schedpub-a1 run --rm app gradle flywayMigrate --no-daemon`はexit 1。V11適用中にMySQL error 1419「You do not have the SUPER privilege and binary logging is enabled」で最初のtrigger作成が拒否された。V1–V10とV11の一部DDL/データ更新は完了し、FlywayにはV11失敗が記録された。
  - 専用DBを作り直した直後の設定probe `docker exec -e MYSQL_PWD=... ppe-p20-schedpub-a1-db-1 mysql -uroot ...`はexit 1、MySQL error 1045 (Access denied)。Composeのテスト値が`export`されず次コマンドへ渡らない状態で起動直後に実行しており、DB containerが既定の`.env`値（`MYSQL_DATABASE=programming_process_evaluator`、`MYSQL_USER=app`）で初期化中だった。専用container/volume内だけの事象で、このschemaへmigrationやfixture/app接続は行っていない。
  - 初回のDB read-back queryはexit 1、`task_class_assignments.published_at`が存在しないためMySQL 1054。実schemaの日時列`publish_at`へ直して再確認した。
  - Node/npm version probeの初回はexit 127 (`node: command not found`)。`npm`実行ファイルの絶対pathだけでは`#!/usr/bin/env node`のNode解決に足りず、同じNode install directoryをPATHに設定して再実行する必要があった。
- 影響/原因: migration error 1419は専用MySQLのbinary logging routine/trigger creator設定であり、製品コードの不具合ではない。V11が部分適用された初回のMySQL volume、および誤ったCompose envの別の専用volumeは、名前を確認した後に破棄・再作成した。いずれも工程20専用project内であり、既存共有volume/8080/DB、本番データ、実生徒データ、Gemini APIには触れていない。`published_at`とNode PATHは検証コマンドの誤りである。
- 対応/再検証:
  - 対象専用volumeだけを削除し、`DB_NAME=ppe_teacher_task_test_scheduledpub` / `DB_USER=ppe_scheduled_test`等をCompose実行環境へexportしてDBを新規作成。container環境を照合してtest-only DBを確認した。
  - 空schemaに対し、専用MySQLで`log_bin_trust_function_creators=1`を一時設定して`docker compose -p ppe-p20-schedpub-a1 run --rm app gradle flywayMigrate flywayValidate --no-daemon`を実行。exit 0、33 migrations成功/validate成功。その後global値を0へ戻してからfixture/appを起動した。
  - fixture testはexit 0、1 passed。アプリ起動後、student login routeはHTTP 200。対象のsynthetic assignmentのみをdueにし、workerによる`scheduled -> published`、`system / publish_scheduled / success`のaudit、`task_published`通知1件をDB再読込で確認。
  - `npm --prefix e2e run test:phase20 -- phase20/scheduled-publication-notification.spec.js`はexit 0、1 passed。認証失敗/成功、通知の一意表示、対象editorへの遷移、既読表示とDB `read_at`を確認した。訂正後のDB read-backもexit 0。
  - 最終状態: テスト後にglobal trust値0、専用schemaとfixtureのみを操作。Gemini APIや実データは使用していない。後処理で専用Compose資源、専用DB volume、生成fixture fileを削除し、共有サービス/volumeは保持した。

### 工程20 課題公開から生徒editorまでのPlaywright受入（2026-10-13 12:59 JST）

- 対象/手順: 専用Compose project `ppe-p20-publish-20261013` と合成専用DB `ppe_teacher_task_test_p20_publication` を作成し、V1–V33を適用。合成教師・生徒・下書き課題をfixtureで作成し、教師の即時公開から生徒が課題を開くまでをPlaywrightで実行した。
- 期待結果: 空の隔離DB migration/validationとアプリ起動が成功し、誤資格情報の拒否、教師の課題公開、DB保存状態、生徒ホームからeditable editorへの遷移を確認する。
- 実際の事象:
  - GradleがGretty/Tomcat依存を初回解決中のreadiness probeはHTTP 000/404となった。初期化後、`student.localhost:18091`と`teacher.localhost:18091`のログイン画面はHTTP 200になった。
  - 最初のPlaywright実行はexit 1。課題行内で`taskId=1`を部分一致するlocatorが、プロンプト設定・編集・履歴の3リンクに一致しstrict-mode violationとなった。
  - locator修正後の実行は50秒のtest timeoutでexit 1。traceでは生徒editorのGETがHTTP 200で応答していたが、テスト全体の期限までに最終確認/cleanupが完了しなかった。アプリ例外は確認されず、どの個別待機が期限の大半を消費したかは未確定。
  - app稼働中の`flywayValidate`はexit 1。GrettyのGradle processがjournal cache lockを保持していたため、別Gradle processがlock timeoutとなった。
  - `flywayValidate`の初回実行もexit 1。アプリのGretty Gradleプロセスが共有Gradle journal lockを保持していたため、別Gradleプロセスがlock timeoutとなった。
- 影響/原因: Playwright locatorとテスト全体timeout、および同一Compose Gradle cacheへの同時アクセスによる環境上の失敗。製品コードの不具合を示す証拠はない。操作は専用DB/Compose project内に限定し、実生徒データ・共有8080/DB・Gemini実APIは使用していない。
- 対応/再検証:
  - 編集リンクを完全一致の`/teacher/task?taskId=<id>`に限定。
  - 当該の複数認証・画面遷移を含むspecだけtimeoutを120秒へ延長し、終了時のbrowser contextを並列closeするよう変更。再実行はexit 0、1 passed、0 failed（3.8秒）。教師の誤資格情報拒否/成功、下書き状態からの即時公開、shared feedback確認、生徒の誤資格情報拒否/成功、課題一覧表示、editorの`data-editable=true`を確認した。
  - timeout後の再試験前に、専用DB内の当該合成課題/割当だけを`draft`/`not_published`へ戻して前提状態を復元した。
  - DB再読込で課題と割当の`published`、`published_at`の記録、および割当の`late_submission_policy=deny`を確認。
  - appを停止してGradle cache lockを解放後に`flywayValidate`を再実行しexit 0。DBの`log_bin_trust_function_creators`は`0`に復元。
  - app停止後の最終clean buildはexit 0、JUnit 141 suites / 527 tests、349 passed、0 failures/errors、178 skipped。skipはopt-in環境なしのケースで、合格件数には含めない。

### 工程20 期限後DENY・教師レビュー画面のブラウザー受入再試行（2026-10-13 JST）

- 対象/手順: Compose project `ppe-p20-20261014`の専用MySQL/アプリでV1–V33を適用し、合成の教師・生徒・期限切れ`DENY`課題/通知をfixtureで作成。アプリ起動後、通知遷移、教師レビュー画面/出力、390px表示のPlaywright試験を実行した。
- 期待結果: 起動完了後に設定済みhostがHTTP 200となり、期限切れ`DENY`通知の遷移/直接URL拒否と教師画面・export・狭幅試験が成功する。
- 実際の事象:
  - Gradleが新規依存を解決中の初回readiness probeは`Empty reply from server`/HTTP 000となった。初期化後、`student.localhost:18090`と`teacher.localhost:18090`のログイン経路はいずれもHTTP 200となった。
  - Playwrightの初回 locator は通知リスト外の同名コントロールも含み複数要素に一致した。教師URLも学生用baseURL基準で生成され、教師hostからの遷移にならなかった。
  - export確認では広いresponse matcherがCSVでなくlist JSONを捕捉した。絞り込み後にCSV取得responseの`Response.text()`が空文字となり、Playwright response bodyだけではdownload内容を検査できなかった。
- 影響/原因: 対象は起動待ちとPlaywrightのlocator/URL/response処理であり、共有8080・共有DB・他Compose projectへの操作なし。実生徒データ/研究データ/Gemini APIも使用していない。事象はいずれも製品コード不具合を示すものではなく、download本文が空となった直接原因は未確定。
- 対応/再検証:
  - 初期化完了後に設定済みhostのHTTP 200を確認してからテストを再開。
  - 通知操作を`.notification-list [data-notification-open]`にscopeし、教師URLは現在ページのorigin基準で構成。CSV responseは厳密な`view` query値で選別し、response本文でなくPlaywrightのdownload eventから取得したファイル内容を検証するよう修正。
  - `npm --prefix e2e run test:phase20 -- phase20/expired-deny-notification.spec.js phase20/teacher-review-mobile.spec.js`はexit 0、3 passed、0 failed。期限後`DENY`通知から生徒homeへ遷移、editor直接URLは404、教師4画面とCSV/text download、survey exportの匿名化、390pxでのdocument横overflowなしを確認。
  - `Phase20BrowserFixtureTest`はexit 0、1 passed。V1–V33適用、`log_bin_trust_function_creators`の`0`復元、専用DB/Compose資源の削除と残存なしを確認。Playwright/E2Eは二つの別fixture環境で各3件実行しており、同一DBでまとめた6件実行ではない。

### 工程20 Playwright準備・初回browser受入の失敗（2026-10-13 11:10–11:25 JST）

- 対象/手順: 新規の隔離Compose環境を作成し、空のMySQL schemaへV1–V33を適用後、browser fixtureとPlaywright受入を実行。
- 期待結果: 専用DBへmigration/validateが成功し、認証・30秒自動保存・複数browser競合の3フローが成功する。
- 実際の事象:
  - 初回`flywayMigrate`はV11の保護trigger作成時にMySQL error 1419（binary logging有効、SUPER privilegeなし）でexit 1。V1–V10は成功し、V11の一部DDLだけが専用空schemaへ適用された。
  - 初期readiness probeを汎用Hostの`127.0.0.1`へ送ると、portal host routingにより302/404となった。これはアプリ故障ではなく、要求Hostが設定値と一致しなかった。
  - Playwright初回実行はexit 1、3 tests failed。教師login後の遷移先を`/teacher/students`と誤想定していた（実際は有効な課題管理権限により`/teacher/task`）。生徒editor helperは複数のCodeMirrorを一意に絞れていなかった。
  - 修正後の中間実行はexit 1、2 passed/1 failed。教師Hostでログイン後、相対admin URLを学生HostのbaseURLから構成してしまい、Host redirect後のHTTP 200を管理者境界の応答と誤認するassertionになっていた。
- 影響/原因: いずれも専用DB/browser fixtureとPlaywrightテスト設定の問題。共有DB/8080、本番データ、実生徒データ、Gemini APIには触れていない。テスト失敗は製品コード不具合の証拠ではない。
- 対応/再検証:
  - 対象専用MySQLだけ`log_bin_trust_function_creators=1`に設定し、V11の部分適用列/制約を空schemaから戻した後、`flywayRepair`とV1–V33の`flywayMigrate flywayValidate`を実施。exit 0、33 migrations成功。global値を`OFF`へ復元。
  - browser fixture専用schema`ppe_teacher_task_test_p20_20261013`で`createsRetainedEditorFixtureForPhase20BrowserAcceptance`を実行。exit 0、1 passed、0 failure/error/skip。
  - Playwrightのbase URLを`student.localhost`へ設定し、教師landing pathを実装済みの`/teacher/task`に合わせ、main editorの`.editor-shell .CodeMirror`へscope。admin URLはログイン後の現在Hostから構成し、教師から管理者画面への応答403を確認。
  - 最終`npm --prefix e2e run test:phase20`はexit 0、3 passed、0 failed。30秒周期snapshot POST成功・再読込後の保存コード、別contextのstale保存HTTP 409と勝者データの再読込を確認。fixture schema、専用Compose services/volumes/networksを削除し、残存なし。MySQL global値`OFF`を再確認。

### 通知画面のブラウザー受入で操作・画像取得が失敗（2026-10-13 JST）

- 対象/手順: 通知一覧とナビゲーション内の通知dropdownを確認するため、狭幅viewportでPlaywrightのlocator clickおよび画面captureを実行。
- 期待結果: 通知UIの表示・幅を確認する。
- 実際の事象: 非表示状態のcollapse内locator clickは可視性待ちでtimeout。続く画面captureは5回の試行後に `Failed to capture screenshot` となった。
- 影響/原因: アプリのHTTP応答・画面遷移・データには影響なし。狭幅viewportでのメニュー開閉状態がbrowser操作対象と一致せず、capture tooling側でも画像取得できなかった。アプリ不具合を示す証拠はない。
- 対応/再検証: viewportを切り替えたうえで画面要素の表示状態を確認し、PlaywrightでDOM上の幅を再計測。390px/606px/1280pxで通知一覧の左右余白、通知メニュー内2件の幅一致を確認し、390pxでdocument横overflowなし。視覚capture自体は未取得。

### N167-02 Gemini揺らぎ生成のHTTP 429（2026-10-13 01:10 JST）

- 対象/手順: ユーザーの許可を得て、local8080の教師画面から合成課題taskId=17/version=3の揺らぎ項目生成を1回実行。送信前に、揺らぎ項目・合成評価例生成ではGemini APIへ送信され得ること、および実生徒提出/個人情報を使わないことを共通プロンプトに明記した。課題情報・評価ルーブリック・合成サンプルのみを送信対象とした。
- 期待結果: Gemini 3.1 Pro Previewから揺らぎ項目を受け取り、確認用の案として下書きに保存する。
- 実際の事象: `POST https://generativelanguage.googleapis.com/v1beta/interactions` がHTTP 429を返し、生成状態は`failed`になった。アプリ例外は`AI retry deadline was exceeded.`、request latencyは851ms。画面にも「AI生成に失敗しました」と表示された。providerのerror code/message、`Retry-After`、provider request IDはアプリログ/監査記録から確認できず、内部監査の相関IDは記録済み。APIキー値は記録していない。
- 影響/原因: 合成課題のprompt下書きは更新済みで、揺らぎ項目は0件、評価例は未生成。課題は未公開のままで、課題割当の公開・通知作成・生徒提出評価・再評価は行っていない。429の具体的なquota/容量理由は不明。
- 対応/再検証: 発生時点では連続呼出しを避け、追加の実API要求を保留した。その後、ユーザーが追加課金とGemini利用を明示許可したため、2026-10-13に合成課題taskId=17/version=3で再試行し、揺らぎ生成は成功した。初回の評価例生成では実行できない形式のPythonコードが1件含まれたため採用せず、実行可能な改行・インデントと想定/実測の区別を追加指示して1回再生成し、2件を画面で確認した。教師対応・評価例保存・version 3適用後、taskId=17は初回公開した。実生徒データは送信していない。後続の改訂課題taskId=18での生成はN167-03の404で止まり、同課題に対するGemini呼び出しは開始されていない。

### N169-01 テスト環境ガードと重複件数の期待値誤り（2026-10-13 JST）

- 対象/手順: N169の隔離MySQLテストを実行し、`StudentNotificationDatabaseTest`で24時間境界・重複排除を検証。DB guard失敗時は統合テスト19件が失敗（exit 1）、後続Entity/Control/Worker単体の再実行は成功（exit 0）、正しいhostでの初回統合テストは重複件数の期待値1件が失敗（47件中1件、exit 1）。
- 期待結果: 指定された隔離fixture環境で全対象テストが成功し、重複worker実行後もDB行が1件である。
- 実際の事象: 初回は`DB_HOST=ppe-preparation-20261013-n169-db`が既存fixture guardの固定許可値`ppe-preparation-20261008-db-1`と異なり、DBテスト19件が環境ガードで失敗した。隔離コンテナをguard指定のネットワークalias/nameで作り直し解消。続いて重複投入テストがJDBCの`ON DUPLICATE KEY UPDATE`のaffected-row戻り値を0と期待したが1となり、対象DBテスト1件が失敗した。
- 影響/原因: 初回は本番/共有開発DBへ接続せず、対象は隔離fixtureの事前条件とテスト期待値のみ。製品コード不具合ではなく、テストコンテナ名の不一致と、MySQL/JDBCの重複upsert戻り件数を実挿入件数と同一視したテスト設計の誤り。
- 対応/再検証: fixtureが要求する専用host aliasを用いた。重複呼び出しの戻り値ではなく保存済み通知のDB件数が1件のままであることを検証するテストへ変更。`docker compose exec -T -e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_n16901 -e DB_USER=root -e DB_PASSWORD=[一時隔離DB値のため省略] -e TEACHER_REVIEW_DB_TEST=true app gradle test --tests entity.StudentNotificationTest --tests control.student.StudentNotificationControlTest --tests control.student.StudentNotificationDatabaseTest --tests control.student.StudentSurveyProgressReminderWorkerTest --tests servlet.student.StudentNotificationServletTest war flywayValidate --warning-mode all`はexit 0。対象58件（Entity 6、Control 21、DB統合19、Worker 1、Servlet 11）がすべて成功、失敗/エラー/skip 0。WARと隔離DBの`flywayValidate`も成功。local DBへのV33適用と`flywayValidate`も成功。

### N169-02 共有ブラウザー参照の失効（2026-10-13 JST、00:59 +0900に記録）

- 対象/手順: N169の認証済みbrowser受入を継続するため、前回の共有ページ参照を再利用し通知一覧を開く。
- 期待結果: ログイン済み生徒の通知一覧を開き、N169通知を受け入れ確認できる。
- 実際の事象: 前回のページ参照は利用不能となり、新しい共有ページはログイン画面を表示した。認証済みページのまま受入を続けられず、一時停止した。
- 影響/原因: browser受入の遅延のみ。アプリコード・DBの既存データに影響はない。ブラウザーセッションが引き継がれなかった理由は特定できていない。
- 対応/再検証: ユーザーが新しい共有ページへログイン後、通知一覧が表示された。合成の24時間超過回答をworkerが通知化し、通知一覧で未読表示、クリック後のアンケート遷移、保存済み回答と`in_progress`状態の維持、既読化を確認した。合成fixtureと専用テストDBコンテナを削除し、通知一覧が元のデモ通知だけに戻ることを確認した。未解決事項なし。

### N167-03 改訂課題のGemini揺らぎ生成がHTTP 404（2026-10-13 01:58–02:04 JST）

- 対象/手順: ユーザーのGemini利用許可後、初回公開済みの合成課題taskId=17を親とする改訂課題taskId=18/revision 2を作成し、draftのprompt version 4を保存。local8080の認証済み教師画面から「確定して揺らぎ項目を生成する」を2回実行した。
- 期待結果: version 4の下書き保存後、Gemini 3.1 Pro Previewで揺らぎ項目の生成を開始し、結果を確認用案として保存する。
- 実際の事象: いずれも`POST /teacher/prompt`がHTTP 404となり、汎用404画面へ遷移した。各要求で下書き保存の成功監査行は作成され、prompt versionの`row_version`は更新されたが、2回目の後も`fluctuation_generation_status=not_generated`、揺らぎ項目0件、生成開始/完了監査なし。したがって改訂課題に対するGemini要求は開始されていない。生徒提出・個人情報は送信していない。
- 影響/原因: taskId=18は`requires_update`、`supersedes_task_id=17`、assignmentは未公開、prompt version 4はdraftのまま。ソース経路を追跡し、`TeacherPromptControl.beginGeneration()`が状態にかかわらず`ensureStandardRubric()`を呼ぶ一方、同処理は`TeacherTaskDao.findDraft()`で`publication_status='draft'`のみ許容することを確認した。改訂案は`requires_update`のため`TeacherTaskNotFoundException`が発生し、`TeacherPromptServlet.doPost()`が404へ変換する。アプリログに当該例外のstack traceはなく、コード経路とDB状態から原因を特定した。
- 対応/再検証: `TeacherPromptControl.beginGeneration()`で認可済み課題を取得し、標準ルーブリック初期化は`draft`の場合だけ実行するよう修正した。`published`/`requires_update`では既存課題に紐づくルーブリックを使う。新しい専用MySQL統合テストは修正前に`TeacherTaskNotFoundException`で失敗し、修正後は改訂案で揺らぎ生成・教師対応保存・評価例生成/保存まで成功した。`TeacherTaskDatabaseTest`、プロンプトControl/AI client、Servlet、画面テスト計52件中47成功・5 skip・失敗/エラー0、WARと`flywayValidate`が成功。local8080アプリを再起動して修正コードをロードし、認証なしのプロンプトURLがログインへ302となることを確認した。その時点では教師browser session失効により実Gemini/ブラウザー受入待ちだったが、再ログイン後の最終受入は下記に記録する。
- 検証環境での解消済みエラー: 最初の共有Compose MySQL上の専用schema作成ではV11のroutine作成がMySQL 1419で失敗し、作成した当該schemaだけを削除した。独立Compose MySQLへ切り替えた初回接続はDocker network経路がなく`Network is unreachable`となったため、使い捨てDBコンテナだけをapp networkへ接続して解消。独立MySQLでもbinlog設定によりV11が1419となったため、その専用コンテナ内だけ`log_bin_trust_function_creators=1`を一時設定してschemaを作り直し、V1〜V33 migrate/validate後に設定を0へ復元した。最初の回帰テストはchecked exception宣言漏れでtest compileに失敗し、`EvaluationProviderException`を宣言して修正。修正前の回帰test失敗は上記の製品不具合を再現したもの。使い捨てDB container/volume/networkは検証後に削除し、共有app DBのデータは変更していない。
- 解消後のlocal browser受入（2026-10-13 JST）: 教師が再ログイン後、合成taskId=18/version=4で揺らぎ生成を実行し、3件すべての教師対応を`resolved`として保存した。Gemini評価例は合成fixtureだけを使い、実提出・編集ログを含まない2つの想定結果を生成した。予測テスト結果の表示、スコア根拠、揺らぎ候補、再評価前チェック項目を確認して保存・active化し、version 4を改訂課題へ適用後に即時再公開した。DBで旧task 17=`requires_update`/旧assignment=`archived`、task 18/新assignment=`published`、有効な在籍生徒5人への改訂通知5件を確認。生徒browserでは改訂版タイトル/本文、クリック後の新assignment editor遷移を確認し、該当通知の`read_at`が設定された。実生徒データ・提出・編集ログは送信していない。Gemini評価例は1 DB record内に2シナリオで、今回の受入では旧inactive例の一括active化は起きなかったが、そのDAOリスクの製品修正・回帰テストは別途未実施。
- 関連する未修正リスク: version 3の評価例再生成時、古いinactive評価例が同じprompt versionに残り、保存処理がそれらまでactive化し得るDAO挙動を確認した。今回の合成データでは不適切な旧例だけを対象限定で削除し、最新例を保存した。製品修正と回帰テストは別途必要。

### N168-01 local browser受入時のHTTP 503（2026-10-12 22:32 JST 記録）

- 対象/手順: N168の変更を検証するため、認証済みlocal8080ブラウザーで生徒ホームを開いた。
- 期待結果: V32適用後の通知/ホーム表示が成功する。
- 実際の事象: ホーム要求がHTTP 503となり、Tomcatログに`Unknown column 'survey_id' in 'field list'`が記録された。
- 影響/原因: local appの開発クラスパスにはV32を含む変更が反映されていた一方、local開発DBはV31のままで、通知DAOの新列参照とschemaが不一致だった。対象はローカル開発環境のみで、アプリから成功したように扱われたデータ操作はない。
- 対応/再検証: V32を専用MySQLで先に適用・統合テストした後、確認対象をlocal開発DBと明示して`docker compose exec -T app gradle flywayMigrate --warning-mode all`を実行。続けて`flywayValidate`成功。認証済みブラウザーで生徒ホームと既存合成アンケートの正規リンクからアンケート画面が表示されることを確認した。通知一覧はV32適用前には表示されたが、適用後の再確認時はログイン画面へリダイレクトされたため未確認。通知生成自体を伴うブラウザー受入も未確認として残す。

### N167-01 local browser fixture creation (2026-10-12 21:17 JST)

- Target/steps: In authenticated local teacher browser, selected the synthetic progress-demo assignment and requested a new independent task-series copy to establish a clean revision-notification fixture.
- Actual result: Tomcat returned HTTP 400: `複製元に現在適用中のプロンプトがありません。`
- Impact/cause: The independent copy was rejected because the source has no currently applied prompt. No copy was created, and the source task, assignments, and learner records were not changed.
- Follow-up: Do not retry copying this source. Use a separately created synthetic draft with an explicitly configured prompt, or stop if browser setup requires an unapproved external AI call. This is distinct from the N167 implementation/regression tests, which passed.

### N167-01 実装中のテストコンパイル失敗（2026-10-12 JST 記録）

- 対象/手順: 課題改訂再公開通知の即時・予約公開DB統合テストを追加し、専用MySQLで対象テストをコンパイル・実行。
- 実際の事象: テスト用helperを既存テストメソッド内へ誤配置し、`compileTestJava`で構文エラー8件が発生した。
- 影響/原因: 専用検証ビルドのみが停止。製品コード、共有DB、local8080への影響はない。
- 対応/再検証: helperをクラス直下の独立メソッドへ移動。対象2テストは未実装状態で改訂通知文面の不一致を検出し、DAO実装後に両テスト成功。続けて全体`gradle test war`が成功し、513件中375件成功、138件skip、失敗/エラー0。skipは成功数へ含めない。

### N166-01 local8080反映（2026-10-12 20:24 JST 記録）

- 対象/手順: 専用MySQLでV1〜V31のmigration・validation、対象DB統合テストと全体回帰後、`docker restart programming-process-evaluator-app-live`でlocal8080アプリのみを再起動。期待結果はTomcat起動とstudent/teacher login routeの応答。
- 実際の事象: Docker UTC logで20:24:44 JSTおよび20:26:59 JSTにGretty `ServiceProtocol$Writer.write`の`java.net.ConnectException: Connection timed out`を記録。その後20:30:43 JSTにTomcat 9.0.118がport 8080で起動。student/teacher account login routeはhost別のHTTP 302後、最終HTTP 200を返した。
- 影響/原因: local開発アプリの起動過渡ログのみ。routeの最終応答は確認できた。Writer接続timeoutの根本原因は特定できていないため、app起動失敗や利用者影響が継続したとは断定しない。
- 対応/再検証: app containerが稼働中であること、Tomcat startedログ、両login routeのredirect先でHTTP 200を確認。ソース変更や追加のDB変更は行っていない。認証済み通知表示・クリックのブラウザー受入は別途未確認。

### N165-06 演習配布通知（2026-10-12 16:39 JST 記録）

- 実装中の初回`compileJava`で`StudentExerciseDao`のメソッド括弧位置と`StudentNotificationControl`のimport不足によりコンパイル失敗。メソッド配置/importを修正し、対象テストとWARを再実行して成功。影響は未反映の作業ツリーのみ。
- 共有Compose DB上の一時テストschemaでは、最初にアプリDBユーザーの作成権限不足、権限を整えた後は既存V11 migrationのMySQL binary logging要件によりFlywayが停止した。既存DBやmigrationを変更して回避せず、専用MySQLコンテナに切り替えた。専用DBで全migration適用・validation、配信DB統合テスト、WARが成功。専用schemaは存在と利用中接続0を確認後に削除。owner labelを確認した専用container/networkと、そのcontainerだけが参照する検証volumeも削除した。local8080 DB/volumeおよび他containerは変更していない。
- local8080のV30適用後のapp再起動時、Gretty `ServiceProtocol$Writer`接続timeoutと起動初期の接続拒否/HTTP 404を観測。Tomcatがport 8080でstartedとなった後は生徒/教師login routeがともにHTTP 200。停止状態や恒常的なroute不通は確認せず、起動過渡の事象として記録する。認証済み学生browser sessionは再起動で失効し、通知一覧への移動はログイン画面へredirectされた。ユーザー認証後の8080通知一覧/実配布操作は未確認で、成功扱いにしない。
- local8080適用時はV30/全migration validation成功、既存通知5件を維持し、旧配布履歴のbackfillは0件。専用DBの実配信transaction/通知遷移テスト、関連テスト、全体Gradle回帰509件（341成功、168 skip、失敗/エラー0）、WARは成功。Skipは条件付きテストであり成功数に含めない。

## 2026-10-12 第165版: 通知基盤・評価完了

### N165-02 評価失敗通知

- 03:49頃 JST、専用V26/full検証の`compileTestJava`が失敗。新しい通知DBテストを既存メソッドの閉じ括弧前に配置してしまったため、メソッドを独立させて修正。DB変更は専用環境だけで、trap清掃済み。対象を再検証する。
- 明示再試行の既存DB実装は`evaluation_kind=reevaluation`で、student要求の`request_payload.trigger=retry`として保存されることを確認した。第1バッチのinitial限定条件では再試行成功通知を作れないため、本バッチの失敗通知と完了通知の共通条件を修正し、教師のpreview/再評価job/任意reevaluationは引き続き除外する。既存の再試行方式は変更せず、失敗後再試行の実DB回帰で検証する。
- 03:53頃 JST、専用評価DBで中断復旧後の再試行成功と複数明示再試行の成功通知件数が0となり失敗。`loadAndPersistInput`がrequest_payloadを正規の評価入力へ置換し、初期triggerが失われることを確認した。永続する当該評価のstudent成功`retry_requested`監査を明示再試行の判別に使用し、payload保存/API契約は変更しない。専用DBと最終WAR/HTTPを再検証する。
- 最終再検証: 通知DB14件・全体494件（346成功/148skip）、対象26件（25成功/実HTTPskip1）/WAR、最終通知認証HTTP1件、専用評価DB13件（12成功/実Gemini1skip）、Chromium4検証成功。通知の失敗挿入を合成triggerで拒否し、失敗状態/中断復旧/監査のrollbackを確認した。IDEは既存の`JsonObject cannot be resolved to a type`等のGson解決診断を表示するが、Gradle compile/test/WARは成功し、IDE設定は本バッチで変更しない。最初の画像は既存sectionアニメーションの途中で見出しが透明だったため、opacity=1を待って撮り直し、通常/狭幅の実文言を目視受入した。
- local8080のV25/処理中要求0を確認後、V26追加migration成功・appのみ再起動。V26正本、両portal200・通知未認証302・backfillなし通知0を確認。実AIは呼ばず、8080でのユーザー認証後確認と全ブラウザー/OS互換性は未確認として残す。

- 作業開始後 JST、VS Code `runTests`が新規`StudentNotificationUiContractTest`を検出できず「No tests found」となった。テスト成功とは扱わず、既存Docker Gradleで対象テストを実行する。製品コード/DBの失敗とは区別する。
- 02:18 JST、専用合成MySQLの全体実行486件で1件失敗/145skip。`TeacherReviewDatabaseTest`が「Fixture requires an empty synthetic-only database」で停止した。新通知DBテストのfixture後始末を調査・修正し、専用環境を清掃後に再実行する。V25は専用DBにのみ適用され、8080/本番には未反映。
- 通知日時は既存評価画面と同じ日時変換に合わせるが、runtime timezoneが固定されていないため「日本時間」と断定するラベルを削除した。システム全体のUTC保存/日本時間表示統一は既存運用上の未確認事項とし、通知機能を理由に本番環境設定を変更しない。
- 再開時 JST、通知DBテストが先に共通fixtureを作成した後、教師評価DBテストが空DB前提で同じfixtureを再作成していたことを確認。専用host/catalogを検証した上で既存の完全な合成fixtureを取得できる共通ヘルパーを追加し、双方を接続した。通常DBを対象にするガードは維持する。V25のFKはCASCADEを除去し、評価完了通知の参照3項目はCHECKで必須とした。修正済みV25を新しい専用DBで再検証する。
- 03:01頃 JST、修正後の全体487件は失敗0で成功したが、通知認証HTTPは「GET never marks read」の期待件数2に対し実数3で失敗した。GETは期限切れ未読行も物理的に保持する契約のため、保持期間内2件と期限切れ1件の計3件が正しい。テストの期待値を修正し、期限切れの表示除外とGET無更新を別々に再検証する。専用環境はtrapで清掃し、通常DB未変更。
- 同日 JST、統合ブラウザーのクライアント切断で操作できなかったため、既存Playwrightイメージによる専用ネットワーク上のChromium受入へ切り替えた。イメージにJSモジュールがなく`MODULE_NOT_FOUND`となり、使い捨てコンテナ内だけに対応版を導入した。1440/375幅の一覧・ベルは受入成功。エディターでは編集ログも含め4つのCodeMirrorがあり、検証selectorが一意でなく停止したため、実編集エディターへ限定して再実行する。停止済みbuilderへの`docker exec`も失敗したため、既存専用builderの再起動による再ビルドへ変更する。通知クリック中の保持期限切れを成功遷移させない再確認と、既読通知の再訪を区別する回帰2件も追加した。本実装の不具合・検証スクリプトの不具合・環境制約を区別し、ブラウザー受入は未完了として維持する。
- 03:18〜03:29 JST、HTTP受入で変更済みのfixtureへ全体DBテストを再実行し、教師評価件数の空/初期fixture前提に反して1件失敗（489件/145skip）。初回の新規専用DB全体487件は成功済みであり、変更対象25件/WARと最終認証HTTP1件を独立再実行して成功した。別の空の専用評価DBで原子性の実DB検証も再実行する。閲覧専用の提出済みエディターをプログラムから書き換えて離脱confirmを期待した検証はtimeoutしたため、正規の「再提出用の編集を開始」操作後の編集可能状態を前提に変更した。DB確認SQLの列名`submission_deadline`は実schemaの`due_at`に訂正（1054、読取のみで変更なし）。
- 合成通知の手動SQLでmysqlクライアントの文字コード指定を省略したため、seedした固定文言のみ文字化けした。`--default-character-set=utf8mb4`で専用合成行を再seedし、実DOMで「評価が完了しました」を一致確認、通常幅/狭幅画像で文言を確認した。狭幅ナビの開閉アニメーション完了も待機し、途中状態を受入証跡にしない。Javaの評価確定通知処理と手動seedを区別する。
- 最終再検証: 487件全体（342成功/145skip）、通知対象25件（24成功/実HTTPskip1）/WAR、通知の最終認証HTTP1件、Chromiumの通常幅/狭幅と取消保持・確定遷移・既読再訪5検証は成功。通常評価の専用DB9件は8成功/実Gemini1件skip。V25を8080 DBへ適用成功、app再起動後に両ログイン200・通知認証302・通知JS配信一致。外部AIは呼び出さず、8080のユーザー認証後手動確認は未確認。IDE `problems`もクライアント切断で取得不能のため、成功したGradle compile/test/WAR・実Tomcat/Chromiumを根拠とする。

### N165-03 課題公開通知

- フルハーネス初回実行で、`servlet.student.StudentNotificationRuntimeTest`を含む3件が失敗した。原因は`StudentNotificationControl`の既定コンストラクタが`StudentEditorControl`経由で`PythonRunnerClient`を生成しており、そのコンストラクタが環境変数`PYTHON_RUNNER_URL`の設定を必須としていたため。課題公開通知の遷移先検証自体には不要な依存であり、`StudentNavigationFilter`を通る全生徒画面のテストにまで波及していた。`StudentEditorDao`を直接利用する設計へ書き換えて不要な依存を除去した。
- `TeacherTaskDatabaseTest`で重複キー違反が一時的に発生したが、調査の結果、検証中に複数の専用fixtureフラグを同時設定した手順ミスによるものであり、実際のハーネス・製品コードの不具合ではないと判断した。該当フラグを単独実行に戻し再現しないことを確認した。
- 最終再検証: `UI160_RUNTIME_TEST=true UI_VALIDATION_REPORTS=build/ui165-published-acceptance sh scripts/testing/evaluation-survey-ui-local.sh`でユニット499件（失敗・error0/skip150）、認証HTTP6件（失敗・error0/skip3）が成功。`StudentNotificationRuntimeTest`の`taskPublishedNoticeRedirectsToTheEditor()`含め全件成功を確認し、専用検証コンテナを`stop_bash`/`trap cleanup`で清掃した。
- ローカル8080へのV27適用、appのみ再起動、ブラウザーでの通知クリック→対象課題エディター遷移の認証後確認はこの時点で未実施（後続作業で実施予定）。本番・プロトタイプ・デプロイ設定・外部AIは操作していない。

## 2026-10-11 第164版: ホーム導線・離脱確認

- 23:13 JST、専用合成アンケートを編集可能にする検証準備で`surveys.is_active`を誤指定し、MySQL1054（Unknown column）となった。変更は実行されていない。実schemaとDAOを確認し、`survey_status='active'`を専用survey2だけに適用した。通常DB/本番は不変。存在しないV5ファイルの検索も実DAO/SHOW COLUMNSへ訂正した。
- 23:15〜23:18 JST、確認ダイアログの非表示アニメーション完了前に次の操作を行い、home待機timeoutした。新guardの重複確認抑止中は移動しないことを確認し、Bootstrap遷移完了後の操作で取消保持/確定home成功。授業演習作成のinput[type=text]は実DOMがtype省略のためtimeoutし、実ラベル「名前」で指定して作成/保存/再読込成功。
- 23:19 JST、合成演習のログアウト取消で既存feedbackモーダルにaria-hidden/focus警告を観測した。取消後のコード保持と保存/再読込/homeは成功。共有feedbackの既存フォーカス復帰の警告は今回の離脱判定とは区別し、全ブラウザー互換性完了とは扱わない。
- 23:20〜23:24 JST、既存共有タブのスクリーンショットがviewport/scrollと不一致・白画像、要素の安定待ちtimeoutとなった。寸法/正常動作はDOMで確認できたが画像を目視証跡に採用しない。新しい本実装タブで評価結果の通常幅、アンケートの狭幅末尾ボタンを正しく表示・目視確認した。プロトタイプは変更していない。
- 23:25 JST、共有guardの独立ブラウザーテストがclick確認のPromise完了前に次の確認を開始し、重複抑止によってassertion失敗した。テストのイベントループ境界を待つよう修正して再検証する。製品側の確認抑止を無効化しない。
- 最終再検証: 共有guard独立12検証・対象JUnit12件/WAR成功、実回答取消復元/再提出home/再読込・コード保存再読込・未保存/送信前入力とログアウト取消保持を確認。iframe内の標準beforeunload表示はブラウザーのuser gesture制約があるため、独立テストはイベントのpreventDefaultを検証し、全OSの実ダイアログ表示確認とは区別する。新本実装タブで4画面の末尾を目視確認し、8080のlogin200/JS5本一致を確認した。

## 2026-10-11 第163版: 課題設定モーダル

- 22:44 JST、専用18092の認証ブラウザーで新規JSP fragmentの固定日本語が文字化けした。動的課題名・フォーム保存は正常、対象37件/WARと認証HTTP4件/追加4件は成功していた。fragment自身のpageEncoding指定不足が原因。UTF-8指定と契約テストを追加し、再ビルド後に日本語表示・保存を再検証する。8080/本番へ未検証版を反映していない。
- 調査時に存在しないTeacherAccess/TeacherReviewAccessファイルを検索指定して失敗。実DAOが使うTeacherPermissionDaoのtask-progress契約を確認した。製品不具合・依存エラーとは区別する。
- WAR回収でコンテナ内作業パスを`/workspace`と誤指定してdocker cpが失敗。実WorkingDirの`/programming-process-evaluator`へ修正して最新版WARを回収・専用runtimeだけ再起動した。製品ビルドは成功していた。統合ブラウザーのactiveタブはviewport/screenshotが一致せず、要素screenshotも安定待ちtimeout。既存の非表示共有タブで通常/狭幅の表示・寸法を確認する方式へ切替した。モーダル日本語と保存再読込は正常に確認できた。
- 22:47〜22:54 JST再検証: UTF-8指定後の対象37件/WAR、追加UI契約/認証HTTP4件がexit0で成功。モーダル日本語、期限延長/クラス追加保存と再読込、Escape/フォーカス復帰、1440/375px表示で文字化けを解消した。8080のみ反映後、両portal200/asset3本一致を確認。IDEの既存Gson解決診断は残るが、Docker Gradlecompile/WARと実TomcatHTTPは成功しており、IDE設定は変更しない。

## 2026-10-11 第162版: アンケート修正

- 初回対象テスト15件中2件失敗。確認モードの段階検証変更に対する旧文字列契約と、JSP ELメソッド参照をプロパティとして抽出するJavaBeans契約が原因。表示選択肢をJavaBean getterへ変更し、確認モードの検証契約を更新して再検証する。製品DB/本番には未反映。
- 専用migration/全体テスト/WARは成功したが、認証HTTPの旧テストがsurvey画面に提出成功通知を期待して1件失敗（exit1）。第162版はhomeへ通知するため、homeでの通知を検証する契約へ更新して再実行する。専用資源はtrapで清掃済み。
- IDEは既存Gson importを解決できない診断を表示した。Docker GradleのcompileJavaは成功しており、IDE classpath問題と製品コンパイル失敗を区別する。依存manifestやIDE設定は変更しない。
- 再検証結果（2026-10-11 22:28〜22:33 JST）: 対象15件成功/skip0、全体444件（302成功/142条件付きskip/失敗・error 0）、認証HTTP4件成功/skip0、WAR成功（exit0）。初回の2契約失敗とHTTP通知期待の失敗は解消した。ローカルflywayMigrateもexit0でV24適用、既存回答3件維持を確認した。
- 最新WARのブラウザーで初回提出/修正再提出→home、版2/理由再読込と取消復元を確認。無効設定の修正ボタン非表示も確認した。統合ブラウザーの要素スクリーンショット取得は失敗し、全体スクリーンショットで通常/狭幅を確認した。取消操作で実DOMにない`#cancelRevisionButton`を指定してtimeoutしたが、正しい`#cancelSurveyEditButton`へ修正して復元成功。製品不具合とは区別し、全物理クリック/全端末互換性は未確認。

## 2026-10-11 第161版: デザイン検証

- VS Code `runTests`は対象2ファイルを検出できず未実施。既存Docker Gradleへ切替し、初回全体442件（300成功/142条件付きskip/失敗0）とWAR、専用認証HTTP4件（skip0）が成功した。
- ブラウザーの`scrollIntoViewIfNeeded`はhiddenページの安定待ちでtimeout。DOMのscrollIntoViewとスクリーンショットで確認する。製品の保存や画面遷移失敗とは扱わない。
- 21:50頃 JST、余白/理由欄修正後の専用HTTP3件再実行でLocalUi160RuntimeTestが既存feature permissionの一意制約違反（SQLIntegrityConstraintViolationException、line45）で失敗。前回の専用fixtureを残した再実行が原因。テストの権限設定をupsert、学校コードをUUIDへ変更して衝突を解消。再実行3件成功/失敗0/skip0。製品DB/schema/資格情報は変更していない。
- 21:51頃 JST、最終HTTPのreport bind元ディレクトリが存在せずDocker起動がexit125。対象ディレクトリを作成して再実行し、3件成功/exit0とXML保存を確認。製品のHTTP失敗とは区別する。
- 21:54〜22:03 JST、統合ブラウザーの強制物理radioクリックが状態を変えず、非表示ページのviewport/スクロール待機が継続した。DOMクリック/native formを用いて下書き・提出・DB6回答/6理由を確認。CSS選択色が白のまま見えたのは非表示ページのtransition進行停止であり、animation終了後に規定の緑背景を確認した。スクリーンショットと寸法確認を併用し、全物理クリック/全端末検証済みとは扱わない。
- 最終対象18件成功/skip0、WAR成功。8080appのみ再起動しlogin200と資源一致を確認。未解決の製品不具合はない。非表示統合ブラウザーの操作制約は残る。

## 2026-10-11 第160版: 検証基盤

- VS Code `runTests`へ新しいJUnit3ファイルを指定したが「No tests found」。この実行ではテスト未実施であり、合格とは扱わない。
- 既存のローカル検証image `ppe-tools:local`内でGradleの正規`test`タスクへ同じ3クラスを指定し、6件成功/失敗0/skip0（exit0）。共有8080・DB・設定は操作していない。
- 20:36 JST、専用DBでの全体`flywayMigrate test war`は新設StudentSurveyUiContractTestの2件で失敗（exit1）。1件は検証imageが本実装srcのみをコピーするためプロトタイプ参照ファイルが存在しないこと、もう1件は正規導線の文字列契約不一致。WAR/画面受入はこの段階で合格とせず、テスト契約を修正して再実行する。所有ラベル付き専用資源はscriptのtrapで清掃済み。
- 20:40 JST、全体テスト/WARは成功したが追加HTTP3件中、課題カードからeditor、標準6設問surveyのGETが500で2件失敗（exit1）。生徒管理・配信のHTTPは成功。stdoutのみでは500の詳細が不足したため、Tomcat内部ログも回収するよう検証scriptを変更し、原因を再現調査する。通常8080や本番へ未検証WARを反映していない。
- 20:42 JST再現で原因を確定。editorは専用TomcatにPYTHON_RUNNER_URLが未設定（検証環境の不足）、surveyはOption recordにJSP ELが要求するJavaBean getterがなくrating設問表示が500（実装不具合）。専用runner設定とOptionのgetterを修正する。いずれもschema/実アカウントの変更なし。
- 20:44 JST、standard6のGET/下書き/提出/再読込と生徒管理/配信HTTPは成功。editorは500解消後404となり、既存教師閲覧専用fixtureのlanguageが`Python 3`で、エディター契約の保存値`Python`と異なることを確認した。fixtureのみ正規値へ修正して再検証する。実DBの課題言語や認可条件は変更しない。
- 20:46以降 JST、修正後の専用全体440件（298成功/142条件付きskip/失敗0）とWAR、認証HTTP4件（skip0）が成功。Option getter不足・runner設定不足・fixture言語不一致はいずれも解消し、rating設問のブラウザー保存/提出とDB6回答/6理由の再読込まで確認した。
- 20:50 JST、ブラウザー教師ログインで待機URLが現在URLにも一致して直後の遷移がPOSTを中断。古いフォームを再送してCSRF403となった。正規GETで新フォームを取得し、実navigation完了を待つ手順へ修正してログイン/生徒管理表示成功。製品のCSRFを緩めていない。
- ブラウザー検証のstatus/flow selectorを実DOMにないclassで指定しtimeout。実DOMのdialog/`.distribution-status-badge`へ修正し配信済み表示を確認した。PW再設定は共有確認ではなく専用入力modalなので、専用dialogの表示/キャンセルを確認した。これらは検証操作の誤りであり製品不具合とは区別する。
- 20:51以降 JST、アンケート提出後のCSS生成矢印が文字化け。CSSの非ASCII literalをUnicode escapeへ変更し、共通template CSSに版160を付けた。再読込で「›」を確認、対象17件/失敗0/skip0とWAR成功。20:57 JST、8080のみ再起動後login200と配信asset8本のSHA256一致・template版160を確認した。

## 2026-10-11 18:02以降 JST: 第159版の確認

- 19:37 JST、最終WARで根拠logIdリンク表示後、統合ブラウザーが旧キャッシュのlog.jsを使用し
  選択が反映されなかった。評価関連3画面のCSS/JSに第159版のqueryを付け、
  再ビルド後に配信スクリプトと実ログ選択を再検証する。
- version付き配信で生徒logId2の選択と対応する保存コードを確認し、旧cacheの影響は解消。
  教師では有効根拠も「認可済み一覧で照合できません」と表示されたため、詳細JSONとJSの
  ID突合せを修正対象とした。保存済み根拠を別のログで補ってはいない。
- 教師の評価理由初期描画でdetail.logsを渡していないのが原因だった。
  理由描画へ認可済み一覧を渡し、対応する契約テストを追加した。IDの型変換や認可を緩めていない。
- 修正後の最終全体420件は281成功/139条件付きskip/失敗0、WARとsurvey実HTTP1件成功。
  19:44 JST、教師根拠ボタンからlogId2/print(0)/1/2の選択を1440/375pxで確認し解消。
  ローカル対象11件とWARも成功。8080だけを再起動しlogin200と変更配信asset4本のソース一致を確認。
- ローカル対象buildは`test war --tests ...`の順序で実行したため、
  warにtestsオプションが渡りexit1（Unknown command-line option '--tests'）。
  `test --tests ... war`へ修正した再試験は成功。DB操作なし。

- ローカルsurvey設定確認の最初のcommandはappコンテナにmysql CLIがなくexit127。
  DBコンテナの既存CLIへ切替し読取成功。次のSELECTは存在しないsurveys.survey_codeを指定し1054。
  既存定義のsurvey_id/title/task_id/statusへ修正して再確認する。どちらも書込なし。
- ローカルs008の最新同意はagreed。前回の未回答記録と異なり、現在は同意をブロッカーとしない。
  合成課題にはsurveysがなく、既存教師結果デモのsurveyはclosed。製品の認可条件を緩めず設定/案内を修正する。
- 18:18 JST以降の専用ブラウザー受入で教師評価URLを単数`/teacher/evaluation`と誤指定し404。
  正規メニューの`/teacher/evaluations`へ修正して一覧・詳細表示を確認した。
  待機selectorを`detailModal`、提出一覧の先頭をreview_studentと誤指定し待機timeout。
  実DOMの`evaluationDetailModal`と対象提出行を指定して再確認した。
  表/差分の計測は非表示判定をhidden属性だけでなくCSS表示、
  差分classを実DOMの`code-line added/removed`で確認し直した。製品表示の不具合ではなく検証selectorの誤り。
- 専用fixture用SQL投入は最初の`docker exec`に`-i`がなくstdinが渡らず未実行。
  `-i`を追加して専用DBだけへ合成survey2/question2を登録し、ブラウザーで保存・再読込・提出を確認した。
- 専用DBで既存V19が存在しないprocedureのDROPを警告1305として出したが、
  flywayMigrate/WAR/全体418件はexit0（279成功/139条件付きskip/失敗0）。
  developmentのdemo3件と専用survey実HTTP1件もexit0。
  ホストNode CLIは未導入。既存計画どおり統合ブラウザーを用い、DOM操作とpointer全面互換性を区別する。
- 18:25 JST、専用schemaで`dao.StudentSurveyDatabaseTest`4件がfixture挿入時に
  `Field 'school_id' doesn't have a default value`で失敗（exit1）。旧fixtureのtasksがV19以降の
  必須school_idを省略していた。製品schemaは変更せず、既存合成学校1をfixtureへ指定して再検証する。
- fixture修正後の同一専用schemaで4件成功/失敗0/skip0、exit0。
  下書き保存・提出後読取専用・重複提出の上書き拒否・同意/他生徒/非有効アンケート/未完了評価境界を再検証した。

## 2026-10-11 JST: 第158版のデモ事前調査・試験

- 16:37 JST、専用18090の認証ブラウザーで演習一覧が「読み込み中」のまま停止。
  `exercise.js`の設定適用でif/else間へpreview設定呼出しを挿入したため
  `Unexpected token 'else'`。呼出しをif/elseの後へ移動し、両エディターへの適用を維持する。
  Java/HTTP試験だけではJavaScript構文を検証できなかったため、全変更JSの構文とブラウザーを再確認する。
- 修正後、関連配信JS9本のparse成功、演習一覧/両詳細の対話実行成功、
  最終対象8件中7成功/1条件付きskipとWAR成功。ローカル8080へ反映・配信JS一致を確認。
- 専用fixture/期待値修正後、DB/runner25件・認証HTTP3件は全成功/skip0。
- ブラウザー操作の子button selector誤り、折りたたまれたファイルのrole検索、
  直前出力とbusy解除前の操作による待機timeoutを経験。DOMの実buttonと
  現在入力可能状態を待つよう検証を修正し、ファイル切替/閉じるを再確認した。
  `page.request`は統合ブラウザーの`Storage.getCookies`非対応で失敗し、
  同一originのfetchへ切り替えてJS検証成功。製品コードの失敗と検証ツール制約を区別する。
- 再開後の専用DB/runner/HTTP検証で、新規進捗fixtureが必須の
  `active_duration_seconds`を省略しSQLException。後続試験も原本未作成のため404。
  製品schemaは変更せずfixtureに0を指定して再検証する。
  初回scriptは失敗時レポート回収前にcleanupしていたため、HTTP失敗時にもXMLを保持するよう修正。
- IDEの新規Servlet helperにGson未解決表示があるが、専用GradleのcompileJava/compileTestJavaとWARは成功。
  IDE依存解決の表示と製品コンパイルの結果を区別する。
- 16:21 JST以降、HTTP試験は稼働中stdoutを最終`standardOutput`だけで待ち失敗。
  対話中出力はeventsへ届くためeventsも検証するよう修正。
  既存境界の期待値も修正（進捗他校403、演習他校404、コード上限413）。
  権限や上限を緩めず、実契約に沿って試験を修正した。
- ローカル8080再起動中のempty reply/一時404はretry後login200を確認。

- 10:25以降の開発DB読取で教師権限列を`permission_status`、`feature_status`と誤記し
  1054。`SELECT *`で実列`is_enabled`を確認して再調査成功。書込なし。
- 対象demo試験初回でcompileJava後のcompileTestJavaが既存多数class未検出で失敗。
  共有build出力の並行Gradle競合を疑い、担当へ出力先分離を連絡。原因と再検証は後記する。
- demo専用build出力へ分離した次のcompileJavaは、並行編集中の
  TeacherExerciseServlet/TeacherProgressControlの括弧配置で失敗。担当へ編集完了の通知を依頼し、
  完了後にまとめて再検証する。demo書込はまだ実行されていない。
- 10:44 JST、修正後の専用出力でdemo対象3件成功（失敗0/skip0）。
  s008デモを同試験内で2回実行し、DB保存・DAO再読込・冪等性とusers/profile/consent不変を確認。
  前述compile失敗は解消。共有build競合は原因未確定で、独立出力による回避を確認した。
- s008資格情報の保存有無を調査したSELECTは存在しない
  `student_profiles.initial_password_ciphertext`を参照して1054。既存DAOに従い
  `student_teacher_credentials.encrypted_password IS NOT NULL`へ修正し成功。秘密の値は表示・変更していない。

## 2026-10-11 JST: UI改善のローカルDB事前調査

- 評価/アンケートの合成データ投入前に、開発DBへSELECTのみを実行した。
- 初回の調査SQLは存在しない `tasks.task_status`、次回は
  `task_class_assignments.assignment_id` を参照し、MySQL1054で失敗した。
  正しい `publication_status` / `task_class_assignment_id` に修正して再実行成功。
- 書込・schema変更・本番/バックアップ設定への操作は発生していない。
- IDEは既存競合済みコンパイル資産に関連して `Client.closeDataSource()` 未定義を表示。
  ソースを無関係に変更せず、Gradleコンパイルで実際の可否を確認する。
- 並行編集途中の最初の対象Gradle試験は、教師提出Servletの括弧配置により
  compileJavaで失敗。担当へ返し、編集完了後にまとめて再検証する。
- 対象再実行はコンパイル/WAR/対象テスト成功。デモ投入初回と同内容の再実行は
  `event_type='auto_save'` が既存ENUMに存在せずSQLExceptionで失敗し、
  全書込をrollback。契約どおり `periodic_snapshot` へ修正し再検証する。
- 修正後のデモ投入と再実行は成功（評価2、回答2/設問回答12、migration23のまま）。
  DAO読戻しテスト追加時にDTOアクセサ名の誤りでcompileTestJava失敗。
  実recordの `taskName()` / `logs()` へ修正して再実行する。
- 次回のDAO読戻しは教師権限検証に必須の明示transactionをテストで開始しておらず
  IllegalStateException。製品DAOの契約を変えず、テストでtransaction開始・rollbackを追加。
- DAO一覧は評価未作成の提出もLEFT JOINで含むため、評価2件期待に対して5行になった。
  テストを評価IDありの合成行に限定。最終2テスト成功/失敗0/skip0、
  評価詳細のソースコード編集ログ、アンケート6回答/2観点のDB再読込を確認。

## 2026-10-11 JST: 専用受入環境・ブラウザー検証のエラーと制約

- 提出専用MySQL8.4のV11でstored function作成が1419（binary logging）により停止。
  専用DBコンテナだけを`--disable-log-bin`で再作成し、migration/DB・実runner試験5件成功。
  製品migration/本番DB/バックアップ設定は変更していない。
- internal-only networkのruntime公開portへ接続できなかった。
  runtimeだけをbridgeにも接続しloopback18091疎通成功。DBはinternal networkを維持。
- 進捗合成fixture初回に`progress_status=editing`で1265。rollback後0行を確認し、
  正しい`in_progress`へ修正して保存・認証詳細200・保存コード表示成功。
  停止済みHTTP-testコンテナへのexec失敗はcurl認証302/詳細200で代替し、試験用cookie/probeを清掃。
- ブラウザー初回loginは古いページのCSRFで403。loginページ再取得後に成功。
  エディターのCodeMirror selector誤り、重複ラベルのstrict selector違反は対象を限定して再実行成功。
  terminal終了ラベルを「終了」で待つ誤りも「実行完了」の実表示で確認した。
- 統合ブラウザーの`document.visibilityState`はhiddenで、通常クリックの安定待ちと
  requestAnimationFrame測定がtimeout。DOM dispatch/requestSubmitとtimer pollingで代替。
  次へ操作自体は2/3へ成功していたがrAF待機だけtimeout、timer pollingで戻る/状態保持/close200を確認。
  初回input待ちの30秒無通信終了は正しい時間切れ表示。再実行の複数入力で正常終了を確認。
  APIRequestのStorage.getCookies未対応はページ遷移でログアウト後の認証失効を確認。
  厳密first paint/物理クリック全操作/OSダイアログを合格扱いしない。
- 最終375px受入で進捗ページ384px横溢れを検出。heroの3×min120px gridと
  詳細表のID指定`overflow:hidden`が原因。縮小可能な列/狭幅1列、header折返し、
  詳細表の局所`overflow-x:auto`へ修正。1440/375pxでページ幅一致・コード表示成功。
- 最終Gradle初回は`test war --tests …`の順序でwarへtestsオプションが渡り、
  `Unknown command-line option '--tests'`で構成失敗。`test --tests … war`へ修正、
  8件成功/失敗・skip0、WAR成功。ソースの問題ではない。
- 8080再起動直後のcurlがempty reply（52）、CSS照合が空応答で失敗。
  起動待ちの`--retry-all-errors`再検証でlogin200と最新progress.css完全一致。
  DB/runnerを再起動せず、共有appだけの起動待ちとして解消。

## 2026-10-10 21:08–21:18 JST: 一回限定のECR本番update失敗・別承認で旧releaseへ復旧

### 再発防止修正のローカル検証（本番変更なし）

- Dockerfileでrunner directory0755/file0644を固定。
  本番Compose UID65532/read-only/security条件の実Docker testを既存Shell CIへ追加。
  入力mode0600でもread/health成功、無認証execute401、再起動後healthy。
- deploy/rollbackの共通TLS network読込を追加し、既存bind/portをprocessへ継承。
  未設定/重複/不正値、呼出側/backupとの不一致は明示拒否。
  実nginxでdeploy/rollback相当の再作成後にHostIp0.0.0.0と両TLS host疎通を確認。
  app_started/no-migrationから既存rollbackがDB lifecycleなしで旧へ戻る回帰を追加。
- local Shell: syntax73files、19suites/157assertion groups、fail0/skip0、
  reporting/runner gates/ECR tests成功。
- Java最初のoffline試験はFlyway13.9.0 plugin cache欠落でexit1。
  ソース変更でなく依存取得不足のため、network有効でCI同一Gradle8.10.2/JDK21
  `gradle --no-daemon clean build --warning-mode all`へ再試行しexit0。
  JUnit107suites/392tests/260passed/132既存opt-in skip、fail0/error0。
- runner新test初回はinternal-only networkでpublish portがnullになりcurl exit3。
  runner自体はhealthyだった。専用test networkとloopback ephemeral portへ修正し
  再実行exit0。test resourcesをcleanup、production network/securityを変更しない。
- Phase 2はまだ未完了。既存失敗releaseを上書きせず、次の承認後に
  修正main SHAから新規ECR releaseを公開・取得して本番受入する。

### 別承認の緊急復旧と再検証

- 2026-10-10 21:14:07 JST、ユーザーが旧releaseサービス復帰を明示承認。
  DB/Volume/migration禁止、非root・既存セキュリティ条件維持。
- 既存rollbackの未完了update経路を調査。current旧のため既存新→旧referenceは不一致。
  実際のcurrent旧→target旧、同一DB/full ID/履歴hash・migration_attempted=no・
  旧7Image IDを照合した現在schema reviewを、別root0600referenceに正直に記録。
  既存reference/journalを書き換えて事前条件を迂回せず、rollback既存証跡退避を利用。
- SSM ba561ee9-c433-4808-95bb-f929a9decb7f、rollback本体exit0、
  21:15:16–21:16:36 JST（80秒）。broker/runner/app/nginxを旧imageへ再作成。
  DBは再作成・再起動せず。5healthy、旧Image IDs、runner65532/app10001を確認。
  postcheckでVolume完全一致、ordered history hash不変、Secrets/全release hash不変。
  state mode=rollback/release=旧/previous=旧/phase=published/current=旧。
- 公開HTTP確認wrapperはcurl exit7、SSMはFailedだがrollback本体失敗ではない。
  EC2/Mac/browserすべて公開接続拒否。local TLS smokeは200。
  原因: operation.envにPPE_BIND_ADDRESSがなく、nginx再作成時にCompose既定127.0.0.1。
  停止前の同nginxは0.0.0.0:80/443、既存TLS envに同公開bind/portsを確認。
- c089ea5c-cae3-4c21-9bfa-bb3b7b37596b: 既存TLS envからbind/portsをprocessだけへ復元、
  共通DB/TLS/deployment locksで排他、旧imageのnginxのみno-deps再作成、exit0。
  新規SG/EC2/security変更なし、operation/TLS envとstateのhash不変、DB/Volume保護再検証。
- 21:18:43 JST公開health/login4件200、21:18:53 JST外部Mac4件200。
  TLS認証を維持、healthはstatus ok。ブラウザーで生徒・教師ログイン画面描画成功。
  旧5サービスhealthy、restart0を確認（nginx最後の再作成も既存verify_five_services成功）。
- maintenance開始21:08:49→公開復旧確認21:18:43: 9分54秒。
  厳密な連続観測による停止時間ではない。サービス復旧済みで停止し、
  新releaseのroot0600問題修正/再deployは未実施。Phase 2は未完了。
- 残課題: 新runner file modeとproduction非root起動検証、operations呼出時の
  公開bind引継ぎ検証。今回これらの実装修正には着手しない。

### 元のupdate失敗時点の記録

- 承認範囲: instance i-0ffd69e8f390bd396、operations86936306...、
  target2f48c898...、previous0e2bfae5...の一回update。再deploy/明示rollback/DB復元は禁止。
- 実行直前: 既存Mac age鍵の固定ciphertext再認証/recipient一致成功。
  reference hash・READY/7IDs・DB/Volume・現在履歴照合成功。
  SSM preflight `21497962-9fdf-4d41-a6f6-c0e98a0c0d4d` success、
  実Flyway validate/info exit0、applied23/pending0。業務アカウントはadmin1件のみ。
- 実update command `48f0e438-90d2-45f6-a6c0-379693c3210e`:
  `bash /var/lib/ppe/operations/86936306ecaf587a84544f45e6bb57d3a49304f4/scripts/production/deploy-release.sh update /var/lib/ppe/releases/2f48c898f7cb9ebdf00809f1b83488a6f1b9811f`。
  12:08:14–12:11:06 UTC（21:08:14–21:11:06 JST）、172秒、exit1。
  待機CLIのMax attempts exceededはcommandがInProgressだったためであり、
  deployを再実行したものではない。最終SSM status Failed。
- 段階: app/runner停止→fresh S3 backup成功→checksum/fixedVersion receipt成功→
  pending0でmigration実行skip→broker更新healthy→app段階でrunner依存性失敗。
- 原因（実測）: `python3: can't open file '/runner/runner.py': [Errno 13] Permission denied`。
  `docker cp ...:/runner/runner.py - | tar -tvf -`でroot:root0600を確認。
  runtime Compose User65532:65532には読めない。image User0:0の起動試験では
  このproduction user override不整合を検出できない。ビルド時mode由来の詳しい追跡は未実施。
- 復旧状況: phase=app_started、migration_attempted=no、apps_restored=no、
  recovery_attempted=no、current-releaseは旧0e2b...。
  組込み復帰はmigration前の早期段階だけで、今回のapp段階では動作していない。
  旧app自動復旧済みと扱わない。明示rollback/再deploy/DB復元は未実行。
- 21:12:08 JST: 新appはCreated/not running、新runnerはrestarting/unhealthy、
  新broker healthy。旧nginx/DBは同じID/起動時刻でhealthy。
  student health/login504、teacher health/login502。停止は継続中で終了時刻未確定。
  quiesce開始21:08:49から21:12:25の観測時点まで216秒のmaintenance進行。
- DB保護: container cead401...とimage/start不変、Volume metadata完全一致。
  ordered Flyway hash d37fe252...不変。migration SQL未実行、DB/Volume削除・復元なし。
  Secrets/全既存release hashes不変、backup/TLS envは旧release参照のまま。
- fresh backup: deployment ID be90333cf86d7e5f55c5deb9e573279b、
  ciphertext111284bytes、dataVersion Gf7SMRlL5BBOxVpoXluksUXx.3X2Jp3Y、
  checksumVersion6Bl.pn3cX1E4PGsR7q.Qm99cEJGk1LuJ、
  manifestVersionQ5ACOsyX1yduU6Ib09h_FXH9cSp.FxZP。
  21:10:00–21:10:20 JSTで完了。receiptを保持し、復号試験は過去固定Versionであり、
  このfresh backupを新たに復元したとは記載しない。
- read-only検査の初回はCreated appにHealth keyがなくGo templateでexit1。
  jqによるmissing-field対応に修正した観測command
  `f8fc6738-d76d-402f-9f76-1a595f9946ba`はsuccess、DB/Volume/HTTPSを確認。
  permission metadata command `152537d9-7922-455b-af55-4d97edfeeb6e` success。
- 対応: 依頼どおり失敗報告で停止。現在のサービス復旧は別途明示承認が必要。
  runtime userをrootに変更する回避やimmutable image/tag上書きは行わない。
  将来修正はproduction非root起動テスト＋Dockerfileの明示file modeをPR/CI経由で行う。
  target変更・再公開・再deployは今回承認に含まれない。
- Phase 2: 未完了。教師機能/preview受入はアプリ停止と教師アカウントなしで未実施。
  private host evidence `/var/lib/ppe/operations-evidence/phase2-update-20261010-2106`。

## 2026-10-10 20:39 JST: EC2取得のSSM環境とDocker29 ID互換性

- 対象: 本番EC2のECR取得のみ。deploy/rollback/migrationは未実行。
- 初回: SSM Run CommandにHOMEがなくadapter line6でexit1。認証前でtarget未作成。
  SSMプロセスにだけHOME=/rootを指定して再実行。永続環境を変更しない。
- 再実行: ECR認証・OCI/source検証・app digest Pullは成功したが、
  `Pulled Image ID mismatch: app`でexit1。release/production tagは作成せずcacheだけ保持。
- 原因: EC2 Docker29.8.2はcontainerd store。inspect Idはmanifest digest
  `sha256:5f93878a...`であり、publisher classic storeのconfig ID
  `sha256:024480db...`と異なる。local containerd config bytesのSHA256は
  期待config IDと実測一致した。データ破損ではなくstorage backendのID契約差。
- 対応: 新しいPRでcontainerd storeを明示判定。固定manifest Descriptor/RepoDigestsに
  加えてlocal config bytesのhash/元configとのbyte一致を必須とする。
  既存releaseのppe-*.idにはdaemon IDを記録し、公開config IDは別記録で維持する。
  Docker daemon/store設定・旧tag・release・DBを変更して回避しない。
- 再検証: classic/containerdの正常系とlocal config改変/descriptor不一致拒否を
  既存mockへ追加。実EC2再受入は修正CI/通常merge後に記録する。
- 影響: サービス/DB/Volume/state/Secretsの変更なし。Pull済みappは保持。
- 解消確認: PR #54/main `86936306ecaf587a84544f45e6bb57d3a49304f4`の
  Java/Shell CI成功後、SSM command `8b83b9f4-a448-44d8-8f23-d27927258862`
  で7/7取得・検証・READY成功。`65ca2918-47a6-4f3a-a8fa-d12f6e5e2f50`で
  既存verify_release/DB no-recreation契約成功。

### 同受入のローカルCLI・SSM配布エラー

- 2026-10-10 20:41 JST: `gh pr create`はhostにgh未導入でexit127。
  既存git credential helperの認証を秘密値非表示で使用し、GitHub REST APIで
  PR #54を作成。認証権限やrepository設定変更なし。
- 2026-10-10 20:46 JST: operations配布SSM command
  `0e79988d-fe0c-4877-9038-3e39c11fe1fa`はBash shebang欠落により
  `set: Illegal option -o pipefail`、exit2。最初のsetで停止し配置変更なし。
  明示Bash shebang付きcommand `12a4ed6d-9b2c-4512-93a4-9beaa9d7190e`で
  新directoryへの配布・archive hash・syntax検証成功。

## 2026-10-10 20:12 JST: ローカルJava21 toolchain未導入

- 対象: 合成CI環境変数を指定した `gradle --no-daemon clean build --warning-mode all`。
- 期待: 既存JUnit/build成功。実結果: host Gradle8.14がJava21 installationを検出できずexit1。
- 原因: macOS hostに要求toolchainがない。コードエラーではない。
- 対応: 既存CIと同じ `gradle:8.10.2-jdk21` imageを`docker run --rm --pull never`で使用し、
  repo mount・合成DB設定・専用一時Gradle homeで同コマンドを再実行。host設定変更なし。
- 再検証: build exit0、107 suites/392 tests/260 passed/0 failures/0 errors/132 skipped。
  opt-in DB/HTTP/browser/APIのskipは未受入のまま。
- 影響: ローカル検証のみ。本番/AWS/IAM変更なし。

## 2026-10-10 20:05 JST: ECR取得fixtureの日本語source path誤拒否

- 対象: `bash scripts/ci/tests/ecr-release-test.sh`、新しい取得adapterの正常系。
- 期待: 正常な既存source archiveを展開し、source manifestを照合する。
- 実結果: producerの40負例は成功したが、adapterがsource展開前にexit1。
- 原因: macOS BSD tarの非UTF-8 localeでは、既存日本語pathをbackslash octalへ
  エスケープして一覧表示する。基本安全チェックがその表記を不正pathとして拒否した。
- 対応: 一覧取得をUTF-8 localeに固定し、traversal/backslash/link拒否は維持。
  正常取得fixtureにも日本語fileを含めて再発を検証する。
- 再検証: producer40負例と取得正常/8負例がexit0。source全hash・既存release形式を照合。
- 影響: mockのみ。本番・AWS・IAM・既存releaseへの変更なし。

## 2026-10-10 19:24 JST: ECRタグpreflightのCLIエラー文字列依存

- 対象: run `38044600086`、10:24:41 UTC。
- 実結果: OIDC認証成功後、`ECR tag absence could not be established: ppe/app`。
  image upload前にexit1。CLIのImageNotFoundException表示形式に依存していた。
  同じtagの読み取り再現で `aws: [ERROR]: An error occurred (ImageNotFoundException)`
  のprefixを確認。元の先頭一致regexはこの形式を認識しなかった。
- 対応: 同じDescribeImages権限によるTAGGED一覧の自動paginationとJSON検証へ変更。
  API成功かつ正しい配列に対象tagがない場合だけ続行。権限・ネットワークエラー、
  不正応答、同一tagは停止し、例外文字列を不在扱いしない。
- 再検証: 既存の他tagは許容し、同一tag/AccessDenied/不正JSONはupload前拒否する
  mock回帰を追加。実AWS空repositoryのJSON形式も読み取り確認。
- 影響: このrunのPushは未実行。本番/IAM/ECR設定は変更なし。

## 2026-10-10 18:59 JST: hosted Docker saveの共有layer参照を誤拒否

- 対象: ECR保存run `38043160459`、09:59:40 UTCの認証前入力検証。
- 期待: 正常なlinux/amd64 Docker save archiveを検証し、OIDC認証へ進む。
- 実結果: exit1、OIDC/Pushは未実行。8リポジトリのimage数はすべて0。
- 原因: 実app manifestは15 layer参照、14 unique blob。空layerの同一digestを
  2回参照する正当なDocker archiveを、Layers配列のunique制約が誤拒否した。
  artifactのZIP SHA検証は成功。HTTP Rangeでarchiveのmanifestを調査した。
- 対応: layer参照の重複だけを許可。archive entryの重複、リンク、traversal、
  参照先ファイルの存在・一意性、config/Image ID検証は維持する。
- 再検証: 同一layerを2回参照する正常fixtureと、重複archive entryを拒否する
  負例を追加。修正PR・main CI後に新しい固定SHAで再実行する。
- 影響: 完成release/部分imageとも未保存。本番・IAM変更なし。

## 2026-10-10 JST: 初回ECR保存前の公開レポートartifact不足

- 対象: PR49マージ後のworkflow静的確認（実ECR保存は未実行）。
- 期待: ECR再取得検証後の公開結果をGitHub artifactにも保存する。
- 実結果: job間転送artifactのみ存在し、公開結果の保存stepが欠落していた。
- 対応: 成功時のみ、6つのレポートファイルを明示選択して7日保存するstepを追加。
  Docker/WAR/source archiveと認証ファイルは公開レポートに含めない。
- 再検証: actionlint、既存mock回帰、修正PRとマージ後のCIで確認する。
- 影響: 初回実Push前に発見。本番・IAM・ECR設定への変更なし。

## 2026-10-10 18:25 JST: PR49 ECR mock試験のshallow checkout

- 対象: CI run `38041320507`、Shell harness検証step、PR49。
- 発生: `2026-10-10T09:25:33Z`（18:25:33 JST）、image準備前。
- 期待: `bash scripts/ci/tests/ecr-release-test.sh`が既定のdepth1 checkoutで成功。
- 実結果: `fatal: ambiguous argument 'HEAD^'`。祖先拒否fixtureが実repositoryの
  親commitを要求し、shallow checkoutでは取得できなかった。初回CIを成功扱いしない。
- 対応: `git commit-tree`で独立した合成親境界を生成し、実履歴への依存を除去。
  depth1/no-checkoutのlocal cloneで有効HEADと合成main外境界の拒否も回帰検証する。
  CI fetch-depth、本番実装、保存契約は変更しない。
- 再検証: `bash scripts/ci/tests/ecr-release-test.sh`は通常checkoutと
  `git clone --depth 1 file://...`の実depth1 checkoutで両方exit0
  （各38負例＋正常系、追加のshallow ancestry回帰成功）。
  `bash scripts/ci/check-shell-syntax.sh`は70件exit0、
  actionlint対象3workflowと`git diff --check`もexit0。
  hosted再実行は親工程で別途確認する。
- 影響: 合成テストのみ。AWS/registry/本番接続なし。

## 2026-10-10 JST: ECR保存のローカル故障注入検証

- 対象: `bash scripts/ci/tests/ecr-release-test.sh`（完全合成fixture、外部接続なし）。
- 期待: remote manifest digest不一致を拒否し、complete OCI markerをpublishしない。
- 実結果: 初回故障注入で`Expected rejection (digest-mismatch): publish`、exit1。
  合成publisherがdigest不一致後も先へ進むことを試験が検出した。
- 対応: 重要なdigest/config/identity/OCI検証とpush/HTTP失敗に明示的な
  exit/returnを追加。`set -e`のみで安全判定を継続させない。
- 再検証: 同じ故障注入を含むmock suiteで負例38件と正常7image/OCI byte
  roundtripがPASS。実AWS・実registry・本番環境への影響なし。
- 追加のtool確認コマンドでは`command -v shellcheck`が非0（未インストール）。
  その後の`&&`検証は実行されなかったため、shellcheckを要求しない独立コマンドで
  actionlint/構文/mockを再実行して成功。shellcheck自体の実行済みとは扱わない。
- 未確認: 実OIDC/ECR/hosted転送/OCI HTTP受入は別承認後。時刻の秒単位は未記録、
  タイムゾーンはJST。故障注入では秘密情報を生成・出力していない。

## 2026-10-10 15:50 JST: 隔離amd64ビルドのbuilder選択

- 対象: Phase 2 Step 1、ローカルCI専用イメージビルド。
- 手順: `bash scripts/ci/build-production-images.sh 5921fabddd0b53df07f4c6f8e1415f7ceaae12e1 5921fabddd0b53df07f4c6f8e1415f7ceaae12e1 <new-private-output>`。
- 期待: 空Docker configでbuildxを使いlinux/amd64をビルド。
- 実結果: exit1。legacy builderへfallbackし、既存arm64 baseのplatform不一致。
  cleanupでも未生成タグを削除しようとしてNo such imageを表示。
- 原因: 空Docker configがDocker Desktopのユーザーplugin探索設定を引き継がない。
- 対応: 公知のsystem pluginを隔離configにsymlinkしbuildxを明示必須化。
  cleanupは存在する固有タグだけを削除。本番Dockerfile・既存タグ・DBへの変更なし。
- 再検証: 修正版で別のprivate outputを使い実ビルド。結果は後続記録。
- 未確認: GitHub hosted runner上のplugin探索と実amd64ビルド。
- 第2回: app/toolsの実amd64ビルドは成功したが、macOS標準Bash 3.2の
  空配列＋nounsetによりdb build前で停止。Shellは異常を表示したが終了statusが0、
  SummaryにPASSなし。この結果を成功として扱わない。
  空target配列を廃止し、非空のcommand配列に統合。さらに完了flagなしの
  EXITを必ず非0にして、部分ビルドの成功誤判定を防止する。
- 再検証結果: 第3回はexit0、7 imageのamd64/revision/ID、実WARとmigration比較、
  JUnit260 pass/0 failure/0 error/132 skip、Summary PASSまで確認。固有タグを削除済み。
  故障注入テストでbuild exit42を保持しFAIL記録・PASSなしを確認する。

## 2026-10-10 07:47 JST: GitHub hosted CI初回受入

- 対象: CI run `38000948714`、commit `95477afa96acd8d7c6651bd114583d1fb943906d`。
- 手順: CI専用ブランチへのpushでJava/Shell jobを実行。
- 期待: 両job成功、18 Shell suites完了、artifact保存。
- 実結果: Java/buildと両artifact保存は成功。Shellは15スイート成功後、
  db-admin-binary-mode-testが`output/arguments: Permission denied`、exit2で停止。
  残り2スイートは未実施。失敗runを受入成功として扱わない。
- 原因: Linux bind mount上の合成MySQL client出力がroot所有/0600で、
  runnerユーザーのgrepから読めない。macOS Dockerの共有方式では再現しなかった。
- 対応: root実行が必要なfixtureは維持し、コンテナ内でテスト出力1ファイルの
  所有者を呼出元UID/GIDへ戻す。本番DB-admin・安全機構・秘密情報扱いは変更なし。
- 影響: disposable hosted runnerと合成fixtureのみ。AWS・本番への接続なし。
- 再検証: ローカル対象テストと構文検証後、追加commitをpushして全CIを再実行する。
- 未確認: 修正版のhosted runner受入結果は後続runで確認する。
- 第2回run `38001241672`: DB client修正は成功、16スイート完了後に
  backup-roundtrip-testのMySQL初期起動がexit2で停止（test wrapper exit1）。
  MySQL entrypointは非root UIDへ切り替えてsecretを読むが、fixtureが0600を使用。
  合成secretは本番注入方式と同じ0444にし、親directoryは0700を維持する。
  同fixture内でroot生成されるage identity/recipientも呼出元所有へ戻す。
  秘密鍵0600、隔離project・非空DB復元拒否などの本番安全条件は不変。
- CI状態確認curlで一時DNS解決失敗（exit6）。GitHub MCP経由でrun結果を取得し、
  本番/AWSの設定変更やCI無条件再実行は行わなかった。
- 第3回run `38030149236`: 合成secret修正により両DB起動は成功。
  age復号でidentity読取permission denied。restoreはrootかつcap-drop ALLなので、
  host所有0600の鍵を読めない。fixtureのidentityは生成元root所有0600を維持する
  （上記のidentity所有者変更は撤回）。cleanupは0700親directoryの所有者がunlinkする。
  restoreの権限・capability制限を緩和せず再検証する。
- 第4回run `38030357685`: age-recipient-testがexit1
  (`Invalid recipient checksum accepted.`)。末尾を常にqへ置換するfixtureは、
  ランダム生成recipientの末尾がqのとき有効な入力のままになる。
  qの場合だけpへ変更し、必ず異なるchecksumになるよう修正する。
  checksum拒否のassertionと本番recipient検証は変更しない。

## 2026-10-10 07:34 JST: CI導入の初回ローカル検証

- 対象: AWSに接続しないJava/Shell CI。既存本番・開発DBの操作なし。
- 手順: Java21/Gradle8.10.2 containerで
  `gradle --no-daemon clean build --warning-mode all`、
  `bash scripts/ci/run-shell-tests.sh <new-private-session-directory>`。
- 期待: Javaの非opt-inテスト成功、Shell回帰18スイート成功。
- 実結果: Gradleは392件中6失敗/132skip、exit1。
  AuthenticationFilterTestのrequest proxyがboolean `isSecure()`にnullを返し、
  現行HTTPS判定でNullPointerException。Shellは2スイート成功後、
  operation-config-sync-testでexit1。
- 原因: CI専用Shell imageにホストUIDのpasswd entryがなく、非root実行時の
  `id -un`を使う所有者検証を満たせない。Java側は既存fixture不足。
- 対応: テストrequestをsecureとして明示し、test-only imageへ実行UIDを登録。
  本番AuthenticationFilter、HTTPS要求、所有者検証、deploy/backup安全条件は不変。
  従来のShell runner既定image/root実行も保持し、CI image選択時だけ非root実行。
- 再検証: 対応後に全Java/Shell回帰を再実行する。結果はCI手順の検証記録へ記載。
- 第2回検証: Javaは107スイート/392件、260成功/0失敗/132skip、build exit0。
  Shellは11スイート成功後、backup-systemd-testで`systemd-analyze: command not found`
  （exit127）。従来Linux imageが提供していたunit構文検証用systemdをCI専用imageに
  追加する。systemdを起動せず、inert fixtureに対するverifyのみ実行する。
- 第3回Shell検証: systemd導入後のtimer定義verifyが`Asia/Tokyo`を解釈できずexit1。
  最小Ubuntu imageにはtzdataが含まれなかった。CI専用imageへtzdataを追加し、
  OnCalendarや本番timer定義は変更しない。
- 最終再検証（07:39〜07:40 JST）: unit verify単独exit0、その後18スイート/
  144 assertion groups、失敗0/skip0、統合wrapper exit0。
  全JUnitは上記260成功/132skip/失敗0を維持。fixture不足・依存不足は解消。
  ローカルDockerの古いCLI plugin警告とGradleのWarPluginConvention非推奨警告は
  検証を妨げない既存警告として残す。GitHub実runnerは未確認。
- 未解決: GitHub hostedでの初回実行はpush後に必要。今回commit/pushなし。

## 2026-10-10 00:20以降 JST: デプロイ補強のローカル実装・回帰

- 対象: production deployment/backup/receipt/TLS参照同期。AWS接続・本番変更なし。
- 手順: cached Ubuntuのnetwork-none/read-only試験環境で既存shell回帰を実行。
  初期baselineは成功。実装中、patchの対象行が存在しないため一部hunkのみ適用された。
  適用済み部分を保持し、残りだけ正しい文脈で適用した。
- fixture不整合: 同期helperの3引数API、必須backup環境変数
  （state/backup directory/public recipient）、新しいattempt/history/receipt証跡、
  初回回復用のattempt別証跡ディレクトリを旧fixtureが満たさず回帰exit1。
  実装の安全条件は緩和せず、合成fixtureを契約に合わせて修正した。
- 対応: exact inventory hashは文字列の想定順ではなく実helperで算出。
  成功マーカー前に証跡保存が完了するようpublish順序も補強。
  ローカルcached imageの調査で存在しない`/opt/ppe`を参照してexit1となったが、
  Dockerfileから実際のtools作業ディレクトリ`/workspace`を確認した。
- 影響: 一時fixtureとローカル作業ツリーのみ。既存本番/開発DB、証明書、
  AWS設定、既存immutable releaseは変更していない。Git commit/push/mergeなし。
- 再検証: deployment回帰、TLS回帰、Shell構文/呼出互換はexit0に復帰。
  隔離実MySQLの暗号化復元・追加DDLデータ保持・部分DDL残存の確認はexit0。
  意図的なS3/rename/DDL失敗は失敗系試験として保持し、実エラーと区別する。
  最終検証範囲・件数はデプロイ手順のlocal acceptance記録に記載する。
- 最終統合runner: 最初は`age-recipient-test.sh`への2引数不足でexit1。
  合成age identity/public recipientを一時生成して渡すよう修正。
  続くmacOS Bash 3実行は空配列とnounsetで`arguments[@]: unbound variable`。
  EXIT cleanupがこの中断を誤ってexit0にしたため、完了扱いにしなかった。
  配列なしの条件分岐と全スイート完了gateを追加し、途中中断は必ず非0にした。
- 最終差分確認で補強: TLS設定のrename成功後のsync失敗でも両before-copyへ
  復旧すること、旧版の7 image記録を停止前に確認すること、完了済みrollbackの
  再実行でも稼働image/health/DB/設定/HTTPSを検証することを追加・確認。
  旧app復旧後の再実行は明示承認・新attempt/backupが必須であることも検証。
- 最終再検証（2026-10-10 00:52以降 JST）:
  `PPE_HARDENING_TEST_OUTPUT_DIR=<owned-private-session-directory> bash scripts/production/tests/deployment-hardening-test.sh`
  は18スイート・144 assertion/scenario groups成功、失敗0、skip0、exit0、
  完了markerあり。`git diff --check`も成功。今回の隔離container/Volumeと合成鍵を
  後片付けし、作業前からある20261008のtest Volumeは削除していない。
- 未確認: 本番配置/更新、実S3権限、フル業務schemaの次回migration、
  実HTTP/ブラウザー、LLM、本番再起動。別途承認・対象版の受入が必要。

## 2026-10-09 23:22以降 JST: TLS更新ローカル回帰テストのfixture権限

- 対象・手順: `bash scripts/production/tests/tls-workflow-test.sh`。合成証明書とDocker/Certbotモックのみを使用、本番接続・ACME通信なし。
- 期待: 各失敗系の前に合成の旧証明書ペアへfixtureを戻す。初回は既存11チェック成功後、非root macOSで0444のテスト用配信ファイルへ`cp`したためPermission denied、exit1。
- 原因・対応: テストfixtureの再準備に必要な書込権限がなかった。テスト用ペアだけ0600に戻してコピーし、直後に0444へ再設定。本番スクリプトの権限要件は緩和しない。
- 影響: 一時テストディレクトリのみ。既存ローカル環境、本番証明書・DB・コンテナへの変更なし。再検証結果はTLS更新手順のローカル検証記録を参照。
- Ubuntuのnetwork-none/read-only追加試験: systemd定義検証はPASS。その後一時領域のmock executableがPermission deniedでexit126。初回`/tmp` tmpfsの実行制限に起因し、モック実行用のテスト専用tmpfsに`exec`を明示して再検証する。本番権限や設定には変更なし。
- 復旧失敗時のlock保持を追加後、macOS/Ubuntuの旧テストはNginx構文検査を常時失敗させたまま次の正常系へ進み、意図どおり残ったlockによりexit1。新しいfail-closed契約とのfixture不整合。旧ペア復旧とlock保持を検証した後、テスト専用lockだけ解除して次の独立ケースへ進むよう修正。復旧失敗・次回配置拒否の専用テストも追加。
- 最終再検証: macOS/Ubuntu各34チェックPASS、exit0。Ubuntuのsystemd定義検証1 PASS、exit0。隔離した実Nginx4チェックPASS、exit0、両hostで合成の新証明書を信頼検証して旧ペア退避とcontainer ID不変も確認。一時資産は当該testのcleanupで削除。残る制約は本番配置・実ACME/dry-run・実hook/reload・自動実行が別承認待ち。既存Docker CLIのmissing-plugin warningsは終了コードや試験結果に影響せず、今回修正しない。

## 2026-10-09 22:34 JST: 最終受入のCloudWatch読取制約と初回定期実行待ち

- `aws --profile ppe-deployer --region ap-northeast-1 cloudwatch describe-alarms --alarm-names ppe-production-backup-freshness-alarm` はDescribeAlarms不足によるAccessDenied（exit254）。アカウント確認は成功。既知の権限制約のためIAM変更や同一roleでの反復をせず、ユーザーのコンソール確認へ切り替え、ActionsEnabled=true・状態OKの回答を得た。
- 読み取り専用SSM検証はREMOTE_EXIT=0。両timer enabled/active(waiting)、鮮度チェック22:00:07〜22:00:13 JST成功。dailyはstart timestamp/履歴がなく、初回予定10月10日02:01:34 JSTのため未実行。実行待ちは障害ではなく、default exit0を実行成功と誤認しない。
- 既存段階Bのreceipt・ローカル3成果物・S3固定3Versionのsize/SHA-256/SSE-KMS/キーは再照合PASS。本番5healthy、MySQL識別情報・Volume・deployment state/events確認、HTTPS各200。新規backup、SQL書込、設定変更は行わない。初回完了後の保存受入が残る。

## 2026-10-09 17:42〜17:53 JST: 段階D適用・監視確認の権限制約と段階E開始

- 承認内の対応: backup専用設定を退避し、TLS/ACMEの2変数だけ永続追加。差分・hash・0600・Compose解析/DB選択を確認。17:42:48 JSTの手動鮮度チェックはexit0、SNSテスト1通はPublish成功・ユーザー受信確認済み。新規backupなし。
- 監視確認エラー: `cloudwatch:GetMetricData`もAccessDenied。AWSコンソールへのintegrated browser接続はERR_ABORTEDで確認できなかった。CLIによる`cloudwatch:EnableAlarmActions`はSSO role・EC2 roleの双方でAccessDenied。IAMは変更せず、同じ拒否操作を反復しない。ユーザー既存コンソールでメトリクス・設定・SNS配信確認後、通知を有効化した。エージェントのAPI読取確認とは区別する。
- ユーザー確認: alarmは12:26 JST作成、12:28 JSTに監視開始前の欠損データによりALARMへ遷移。ActionsEnabled=trueとなったが17:53頃もALARM。17:42/17:53のFreshBackup=1は同一3600秒期間であり、2/3評価の複数正常期間が揃った証拠ではない。正常な毎時送信の蓄積とState reason/historyによるOK遷移の確認を待つ。欠損を誤魔化す送信・閾値変更・状態強制は行わない。
- 段階E再検証: `bash -n stage-e-activate.sh`とSSM実行はexit0（REMOTE_EXIT=0）。両timerを承認範囲でenabled/active(waiting)にした。systemdによる鮮度チェック17:53:28〜17:53:34 JSTはResult=success/exit0。次回鮮度18:00:49 JST、初回daily予定10月10日02:01:34 JST。daily serviceは未実行でありdefault successを受入実績としない。
- 保護確認: 既存5container healthy、ID/image/StartedAt/mounts・DB Volume・deployment state/eventsのsnapshot一致、既存receipt検証PASS、student/teacher HTTPS各200。残作業は実際の正常hourly期間でのalarm OK遷移と初回scheduled backupの保存結果。設定の追加変更や無条件のbackup再試行はしない。

## 2026-10-09 17:38 JST: 段階D監視読み取り権限不足と準備検証

- 読み取り調査: SSO `ppe-deployer` とEC2 instance roleの双方で BackupSuccess/FreshBackupの `cloudwatch:GetMetricStatistics`、対象alarmの `cloudwatch:DescribeAlarms`、対象SNSの `sns:GetTopicAttributes` / `sns:ListSubscriptionsByTopic` がAccessDenied/AuthorizationError。前者/後者それぞれ一度の確認とrole代替を試し、同一エラーの無条件反復はしない。実際のメトリクス、alarm、SNS購読の現在値は未確認。不存在と扱わない。
- 原因・対応: エラー本文は各read actionのidentity-based Allow不足を示す。管理者observer用の最小読取ポリシー案と、所有者によるredacted確認の代替手段を作成。IAM/CloudWatch/SNSの変更・通知送信なし。
- EC2で確認: 固定版11ファイルhash一致、既存receiptとexact-version HEADは成功。4unit loaded/inactive、2timer disabled。環境ファイルのTLS/ACME変数欠落を確認し、既存nginx mountと値を照合。本番ファイルhashは不変、5healthy、両HTTPS200。
- ローカル検証: backup設定candidateへ2変数だけ追加、0600維持。新設定テスト5チェック成功（Compose mount2/DB recipe不変/必須変数欠落2拒否）。Linux network-none backup mocksもexact metric名/dimension/value、receipt欠落0/SNS、config/metric failureで信号欠落を含め成功。AWS実メトリクス・実SNS試験は今回未実施。
- 未解決: 本番永続設定補完・observer readback・SNS/alarm delivery実試験・通知とtimer有効化はそれぞれ別承認待ち。段階Dの準備は完了、自動運用開始はHOLD。

## 2026-10-09: 段階Cのローカル復元検証とテスト不備の修正

- 対象: 固定S3 Version `5hC9psnT52akVYEJ5U8JsSkeb5kkWI5R` のローカル隔離MySQL復元。ciphertext・receipt・S3 HEAD/GetObject照合とhost age全体認証はPASS。本番は読み取り専用で、秘密鍵はローカルPCだけで使用。
- 初回 `bash restore-and-validate.sh` は新規専用MySQL起動後、空DB確認SQLのcommand substitution内で誤ったescaped quoteにより `syntax error near unexpected token '('`、exit1。SQL import前のテストコード不備。修正後、専用project/Volume一致・DB空を再確認して同じ専用空DBだけへresume。復元と整合性検証はexit0、stderr0 bytes、64テーブル/126FK/23Flyway成功。
- nonempty拒否テストの初回はchecksum sidecar未配置のため `Backup, identity or checksum is missing` で拒否された。正しいhashのローカルsidecarを追加し再検証すると `Refusing restore into a nonempty database` を確認。非空DBへのSQL importは発生していない。truncated ciphertextの認証失敗は期待どおりのnegative test。
- Docker daemon利用は正常。既存ローカルCLIのmissing-plugin warningは復元障害ではなく、今回修正/削除していない。
- 復元後の全table件数とFlyway metadata/checksumのreport hashは本番read-only queryと一致。専用テストcontainer/Volume/生成DB passwordと転送一時ログだけ清掃し、既存ローカル資産は維持。本番5healthy、HTTPS2件200、DB/Volume/journal不変。
- 未検証: 業務テーブルは空の初期DBであり、実学習ログ・提出・評価を含む代表データ復元は未実施。定期設定補完とCloudWatch保存datapoint読取確認は段階Bから継続。段階Dの実変更には別承認が必要。

## 2026-10-09 17:17〜17:20 JST: 段階Bの初回バックアップはCompose入力不足で失敗

- 17:20〜17:22 JST再検証: ask_userで「一時環境変数を補って1回再実行する」の明示承認を得た。nginxの既存mountから確認済み2変数を当該実行環境だけへexportし再実行、exit0。暗号化ファイル110,489 bytes・checksum102 bytes・manifest994 bytesが新規S3 Version ID付きで保存され、SSE-KMS/指定キー/Bucket Key/hash/receiptとage形式を照合PASS。既存設定やimmutableファイルは変更せず、永続設定用のローカルexampleだけ2変数を追加。配置済みEnvironmentFileは未補完のためtimer有効化前に別承認で修正が必要。
- 最終保護確認: 5コンテナhealthy、両HTTPS200、DB/Volume/画像inventory/journalのbefore/after snapshot一致、両timer disabled/inactive。復号・復元は未実施。
- 監視の制約: BackupSuccess PutMetricDataは処理内で成功し、receipt保存も完了。ただしSSOとEC2 roleの両方で`cloudwatch:GetMetricStatistics`はAccessDenied、保存されたdatapointの独立読取は未確認。IAM変更・メトリクスの再送なし。S3既知3オブジェクトのexact-version HEADは成功しており、ListBucket不足と区別する。

- 再開時の調査: 16:30頃開始したSSMセッションは結果が返らず、17:16の別SSM読み取り確認でバックアッププロセス・ローカル成果物・receiptなし、5コンテナhealthyを確認。停滞したローカルSSM transportを終了し、再度プロセス・成果物なしを確認。実行スクリプトはS3アップロード前にローカル暗号化ファイルを作成・保持するため、この停滞セッションでバックアップは開始されなかったと判断。接続初期化タイミングが原因の可能性はあるが未確定。transportの接続待機を10秒、echo無効後を3秒へ変更。
- 事前確認の補正: Docker imageに`Config.Volumes`フィールドがなくGo template参照が失敗。読み取り検査だけをjqのoptional-field対応へ修正し、Volumes空・配置済み11ファイルと設定/公開鍵hash・インスタンスロールSSM値・5コンテナ・journal・空き容量の再確認はPASS。運用固定版は変更していない。
- 実行: 17:17:48 JST（08:17:48Z）に固定版 `backup.sh` を実際に1回起動。`database-source`段階でComposeの補間エラー（`PPE_TLS_DIR` / `PPE_ACME_DIR` 未設定）、exit1。DB選択の前に失敗し、dump・age本暗号化・S3 PUT・BackupSuccess送信・receipt作成には到達していない。失敗trapのSNS Publishは試行されたが、配信結果は独立確認していない。
- 原因・検証: backup-operation.envに全Compose解析で必須の2変数が含まれていない。ローカルdummy pathを環境変数として設定した `docker compose --env-file /dev/null -f compose.production.yml config --services` はPASS（コンテナ操作なし）。本番nginxの既存mountを読み取り、TLS=`/var/lib/ppe/tls-private/current`、ACME=`/var/lib/ppe/acme`を確認。読取シェル内だけの一時exportで既存DB IDのCompose選択はPASS。設定・TLS・immutable releaseの変更なし。
- 成果物・保護対象: 失敗後もローカルbackups空、成功receiptなし、DB ID/image/StartedAt・Volume・journal不変、5コンテナhealthy。age公開鍵事前チェック用の一時コンテナと共有lockファイルは承認済みbackup処理として使用された。スキーマ・データ書き込み、Flyway、既存コンテナ再起動/再作成なし。
- 読取権限の制約: SSO profileとEC2 roleの両方でS3 ListObjectsV2は`AccessDenied`（s3:ListBucket不足）。S3全体の既存/不完全成果物一覧は未確認。ただし今回の処理はPUTより前の段階で失敗し、アップロードされるローカル暗号化ファイルも存在しない。IAM変更は実施していない。
- 対応・未解決: 新しいS3成果物を作るbackup再実行はユーザー条件に従い承認待ち。再実行案は既存mountから取得した2変数を当該実行環境へ追加するだけで、既存ファイルを上書きしない。定期運用用の永続設定補完は別途承認が必要。段階B未完了、CloudWatch BackupSuccess・S3保存・復元可能性は未確認。新規バックアップの無条件再実行は行っていない。

## 2026-10-09 16:23 JST: 本番バックアップ段階AのSSM転送で停止

- 16:27 JST解消: ユーザーが承認済み範囲内での安全な再試行を許可したため、残存物を読み取り確認（部分tar 3,071 bytes、0600）。旧stagingは不変とし、新規 `...-20261009-r2` へ転送。76文字のbase64短行化・0.01秒/行の送信と末尾改行を採用し、51,200-byteダミーのローカルPTY往復と実転送4入力のhash往復をPASSした後に再開。最初のローカルPTY検証は一括送信/末尾行欠落のためhash不一致となったが、実転送と同じpaced送信・末尾改行へ合わせて解消した。
- 再検証: EC2側アーカイブ・設定・公開鍵hash、11ファイルhash、配置後hash/mode/owner、systemd verifyとdaemon-reloadが成功（remote exit0）。4unit loaded/inactive、2timer disabled、サービス実行開始timestamp0。保護対象before/after snapshot一致、両HTTPS login200。段階A完了。初回backup/復元/通知/timer起動は未実施。既存stagingや部分tarの削除・上書きなし。

- 対象・手順: SSO再認証後、承認済み段階Aを再開。account `024378233912`、東京の対象EC2のrunning状態を確認。SSM内のIMDS identityもaccount/region/instance一致。事前確認は2回ともPASS。
- 保護対象: 5コンテナhealthy、既存MySQL ID・image・StartedAtとVolumeの名前・作成日時は従前一致。releaseは `0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`、journalはpublishedでstate/events hash一致。4unitはnot-found/inactive、配置先衝突と既存関連プロセスなし、バックアップディレクトリにファイルなし。
- 配布物: 11ファイル・アーカイブ・設定・公開鍵hashはすべて一致。ローカル秘密鍵文字列検査・shell構文確認もPASS。秘密鍵は参照・転送していない。
- 実行・期待: SSM Session Managerのecho無効シェルから、rootの新規専用stagingへbase64転送し、EC2側のSHA256検証後に非稼働配置する。
- 実際: staging作成直後、最初のアーカイブを復号する `base64 --decode` が `base64: invalid input`、remote exit 1で停止。SSM clientはexit0だったがremote exit1を失敗と判定した。
- 変更済み範囲: `/var/lib/ppe/backup-stage-a-ff4c1c36-20261009`（root専用0700）と、その中の部分的な `operations.tar`（umask077）が作成された。失敗後のサイズ・内容は未確認。設定・公開鍵・checksum manifestの転送、展開、運用ファイル/unit配置、symlink、daemon-reloadには到達していない。
- 原因: ローカルのbase64出力の最大行長は68,268 bytes。TTYの長い入力行が転送時に切り詰められた可能性が高いが、remote部分ファイルの調査は未実施であり推測と区別する。
- 対応・再検証: 承認条件に従い即停止。再試行・削除・ロールバック・バックアップ・Docker変更・SQL・Flyway・S3/AWS設定変更なし。今回の転送内容はログへ出力していない。短行base64転送のローカル再現検証、remote残存物の読み取り確認、新しいstagingでの再開について追加承認が必要。配置後受入は未実施。

## 2026-10-09 16:17 JST: 本番バックアップ段階Aの事前確認で停止

- 対象: 固定運用版 `ff4c1c36b911fc51be4b5915b3afd0b935606223` のSSM転送・非稼働配置。ユーザー承認範囲内でAWS対象確認から開始した。
- 実行: `aws --profile ppe-deployer --region ap-northeast-1 --no-cli-pager sts get-caller-identity` と、同profile/regionの `ec2 describe-instances --instance-ids i-0ffd69e8f390bd396`（出力項目はaccount、instance ID、state、AZに限定）。
- 期待結果: accountと対象インスタンスの一致を確認してからSSM事前確認へ進む。
- 実際: 両読み取りAPIが `Error when retrieving token from sso: Token has expired and refresh failed` で失敗。コマンド終了コード255。原因はAWS CLIが報告したSSOトークン失効であり、本番稼働状態は未確認。
- 影響・対応: SSM接続・転送・配置・daemon-reload・バックアップ・AWS設定変更は一切実施せず停止。自動再試行・削除・ロールバックなし。ローカルにはこの失敗記録のみ追記。
- 再検証: 16:20のユーザー再認証連絡後にaccount/EC2/SSM事前確認は成功し、認証失効は解消した。その後の転送失敗と未完了範囲は上記16:23の記録を参照。

## 2026-10-08: 評価確認のプロトタイプ再現・受入

- 初回パッチで新exportメソッドを既存CSV transaction内へ誤挿入しコンパイル失敗。クラス直下へ移して解消した。最終のclean全体392件は260成功/132明示skip/失敗・error0、WAR成功。対象DB26件は25成功/1 runtime gate skip、別認証HTTP1成功。旧XMLを混ぜず実行別reportを集計した。
- 保存DTOには`dimensionCode/category`がないため、初期の配色/理由絞込を実際の`label/dimensionLabel`へ修正。詳細container/カード/余白を専用CSSで調整し、1440pxのhero/grid/scorepanel幅をプロトタイプと照合した。共通CSS、提出専用資産、プロトタイプは不変。
- shared feedbackのtoastは文字列引数ではなく`{message, variant}`に修正。modal close時のfocus警告はhide時blur/hidden時起動buttonへ復帰で解消し、単体取得後と4回連続開閉で確認。最終正常操作のpageerror/console warning0。
- 詳細requestの成功だけでなく失敗にもsequence確認を追加。旧履歴の遅延503が最新成功詳細を隠さないことを合成応答で再現・確認した。503更新時の旧データ/集計clear・通知/出力無効と再取得復旧も確認。意図的な503のnetwork errorは未解決の製品障害として扱わない。
- ブラウザーの検証selectorで存在しない第2学校option/提出tbody名を参照したtimeoutは、現在の合成fixtureと実DOMに合わせて修正して再確認した。プロトタイプfile頁の通常pointerclickは統合ブラウザーのtimeoutがあり、比較用modal幾何は起動関数で確認。本実装は通常pointerで受入した。専用runtimeの静的JS更新後に認証が失効したため合成アカウントで再ログインし、最終JS hash/操作を確認。資格情報変更/DB再seedなし。
- 8080の当該appへ反映し、正規teacher/student login200・未認証evaluation302・配信CSS/JS hash一致を確認。所有検証環境だけ清掃し共有環境を維持。エディターに残る既存Gson依存解決/古いControlメソッド診断とGradle9 deprecation警告は、実compile/WAR成功と区別する。OS保存完了/全端末/本番TLS/負荷/大件数ZIPは未確認。外部AI・本番deploy・commitなし。[評価画面最終受入](./feature-plans/teacher-submission-evaluation-review.md#評価画面のプロトタイプ再現最終受入2026-10-08第156版)を参照。

## 2026-10-08: 提出課題確認のプロトタイプ再現・受入

- 初回パッチで新Controlメソッドを既存CSV transaction内へ誤挿入し、コンパイル失敗。クラス直下へ移動して解消。最終clean compile/test/WARは成功。エディターには既存Gson解決/古いControlメソッド診断が残る場合があるため、実Gradle成功と区別した。
- 当初の対象テストreportコピーにはtools imageに残る過去XMLが混在した。最終スクリプトは`gradle clean`と実行別保存を採用し、古い集計370件は受入件数に使わない。最新対象は25件（24成功/1 runtime gate skip）、別認証HTTP1成功。全体clean回帰は391件（259成功/132明示skip/失敗・error0）。
- 1024pxで共通headerの長い教師ID/学校名と操作群がdocumentを1180pxへ広げていた。提出専用body classで1199px以下のheaderを折り返し、1440/1024/768/375pxでdocument幅=viewport幅を確認。長い実データで「表示」が折り返す点も表button/pillのnowrapで補完。共通CSSとプロトタイプは不変。
- ダウンロード完了後の全表再描画が詳細起動buttonを破棄し、閉じた後のfocusが復帰しなかった。出力button状態だけを更新するよう変更し、単体取得後のcloseと連続4回の開閉で「表示」へfocus復帰を確認。CodeMirrorの元textarea値は自動同期されないため、編集内容の検証は実editor APIで行い、空textareaをコード欠落と読み替えない。
- ブラウザー検証の「閉じる」selectorがmodalと成功toastの2buttonに一致した。modal内へ限定して再検証。プロトタイプfile頁の通常clickには統合ブラウザーの安定性timeoutがあったため、比較用のmodal幾何だけ起動関数で確認した。本実装の詳細は通常pointer clickで受入した。
- `sh scripts/testing/teacher-submission-review-local.sh unit` → exit0、全体259成功/132skip/0失敗、WAR成功。`browser`はclean対象24成功/1skipと認証HTTP1成功後に18089を保持し、通常/狭幅受入後stop/trapで清掃。全8sort×昇降順/一覧CSV順序、単体/ZIP内容・不同意CSV除外、機能/学校境界、監査拒否、32 MiB境界、一時実行不変性を確認。画面/serverの16並びを照合し、模擬503で古い一覧clear/通知後に実DB更新へ復旧した。
- 開発8080の当該appだけ再起動し、teacher/student login200・未認証提出302、配信CSS/JS source hash一致を確認。実DB業務受入は専用合成18089。所有検証container/networkを清掃し共有app/DB/runner/別プロジェクトDBを維持。OS保存完了/全端末/本番TLS/負荷/実利用者手動受入は未確認。外部AI、本番deploy、commitなし。差分と再現コマンドは[提出画面最終受入](./feature-plans/teacher-submission-evaluation-review.md#提出画面のプロトタイプ再現最終受入2026-10-08第155版)を参照。

## 2026-10-08: アンケート結果確認の実装・最終受入

- 初回の検証コードは未採用Mockitoと認証DTOの旧constructorを使ってコンパイル失敗。依存を増やさず、既存方式のJDK Proxyと現行6引数へ修正した。CSVは既存`CsvCells`が全セルを引用するため、誤った非引用期待を修正。初回隔離DB検証自体は成功しており、テスト不備と製品障害を区別した。
- 全体回帰が新Servlet追加でURL棚卸し件数27→28を検出。正規`/teacher/surveys`・JSP/menuリンクを確認し、URL方針と契約テストを更新。修正後は全体389件中257成功/132ゲートskip/失敗・error0、WAR成功。
- 共通CSSの`main .hero-stats`が画面CSSより優先され、375pxでカードが2列になった。画面body classのスコープで3列を維持。狭幅の教師情報が過度に折り返す点も、この画面だけheader actions/情報行を折り返す形へ補完。通常1440pxのhero/summary列幅はプロトタイプと一致、1440/1024/768/375pxでページ幅とviewportが一致、表だけ局所スクロール。プロトタイプ・共通CSSは変更しない。
- Bootstrapの閉じる操作に伴うaria-hidden/focus警告はhideでモーダル内のfocusを外し、hidden後に表示ボタンへ戻す方式で補正。4回連続開閉・focus復帰とページJS error0を確認。検索欄のblur/changeで表示ボタンを消さないよう、検索inputとselect changeを分離した。DB取得失敗時は一覧/集計/詳細の旧データを消して共通inline通知を表示する。
- 最終コマンド`sh scripts/testing/teacher-survey-review-local.sh unit` → exit0、257成功/132明示skip/0失敗、WAR成功。`sh scripts/testing/teacher-survey-review-local.sh integration` → exit0、空DB migration・対象6成功/1 runtime skipと別認証HTTP1成功。全sort/一覧CSV順序・6条件式・不正/重複/ID overflow・同意撤回/再同意・異なる本人/評価/課題・現在教師/機能/学校失効・歴史所属・参照不変・監査拒否を検証した。出力内容を生成しても監査commit成功前には応答しない。
- 合成教師で実画面検索/filter/条件エラー/sort/summary/詳細/追加設問/更新/CSV/TXTを確認。HTML風の保存記述はtextContentで文字として表示し、任意設問やinactive選択肢のラベルも落とさない。研究CSV/TXTは内部user IDを使い、ログインID/氏名/出席番号/学校・クラス名を投影しない。自由記述は完全匿名を保証しないことを画面へ明記した。
- 8080の当該appを再起動し、teacher/student各正しいhostのlogin200、未認証survey302、Servlet classと最新JS/CSS hash一致を確認。認証業務受入は隔離18091で行い、共有利用者の資格情報を変更していない。全所有container/networkを清掃、共有app/DB/runner/他プロジェクトDBは維持した。
- 未確認: OS保存完了/全端末・本番TLS/負荷/実利用者手動受入。Gsonの既存エディターclasspath診断とGradle 9 deprecationは未修正（Gradleの実コンパイルは成功）。統合ブラウザーの撮影ではviewportが再設定されるため、幅の根拠はsetViewportSize直後のDOM計測とし、画像名/撮影サイズと混同しない。詳細は[工程18最終受入](./implementation-roadmap.md#工程18最終受入2026-10-08)。

## 2026-10-08: 授業演習コード確認の最終受入

- 検索入力後の詳細クリック消失を実イベントで特定した。`mousedown`後の検索欄`change`が一覧DOMを置き換え、`click`が発生しなかった。検索の`input`とselectの`change`へ再描画対象を限定し、通常クリックによる4回連続開閉とhidden完了後フォーカス復帰で解消を確認。
- 中間幅のページ横溢れは一覧表ではなく共通ヘッダーの固定横並びと集計カードの最小幅によるもの。この画面専用body classでヘッダー折返し、カード縮小/折返し、hero縦積みを補完。最終一覧1440/1199/1024/858/769/375px、詳細1440/858/375pxのclient幅/scroll幅一致を確認。共通CSSとプロトタイプは変更しない。
- 空フォルダだけの一括取得を画面200とrollback付きDB検証で確認。未作成0件とファイル数0/フォルダ1件を区別する。今回のDB/画面データは全て専用合成データ。
- 検証ツールの制約を製品不具合と区別した。撮影時にviewportが再設定されるため画像名を幅の証拠にしない。Playwright断片内の`URL`参照は未定義エラーとなり、応答URL文字列による照合で再実行して単体/ZIP/CSVが200。遅延routeによる競合模擬はツール待機タイムアウトで未確認、routeを解除して通常フローを再確認した。modal非表示直後の即時focus assertionはhiddenイベント前だったため、hidden完了後のfocusを待つ確認へ修正した。
- 最新`sh scripts/testing/teacher-exercise-review-local.sh unit`は382件中252成功/130条件付きskip/失敗0、WAR成功。`integration`は実DB/認証HTTP各1件成功、exit0。既存のGsonエディター解決診断、Gradle 9非互換のdeprecation警告は対象外として保持し、実コンパイル成功と区別する。
- 8080の当該appのみ再起動し、教師/生徒login200と最終class/CSS/JS配信を確認。所有検証コンテナ/networkを清掃、共有DB/volume/runner/他プロジェクトとユーザーのデプロイ変更は維持。詳細は[工程15最終受入](./feature-plans/teacher-exercise-review.md#最終受入2026-10-08)。

## 2026-10-08: 不同意案内の削除と教師からアンケートまでの縦断受入

- 機能仕様第149/150版に従い、不同意常設バナーだけを削除。未回答案内、保存通知、同意画面の現在回答、学習継続、アンケートの同意条件は維持した。専用合成DBの実HTTP・統合ブラウザーで確認し、8080へ反映した。
- 本実装のブロッカーを検出: 新規課題は再評価対象が0人のため、設定済みプロンプトを適用できず公開できなかった。ユーザー承認後に「未公開課題に適用」を追加。未公開・学習開始前、教師/所有/所属権限、明示確認、CSRF、両版、有効ルーブリックと生成物を検証し、active参照・課題版・監査を同一transactionで保存する。既存の対象0人の再評価契約は維持し、適用時の追加AI/job/評価行は0。
- 版表示の既存bug: prompt statusに存在しない`active`との比較をやめ、課題のactive参照との比較で「適用中」を表示する。アンケートの学習ナビは評価IDを提出IDとして渡していたため、`evaluationSubmissionId`へ修正。評価ID100・提出ID1の実データでリンクと戻り先200を検証した。初回同番のデータだけで成功と判定しない。
- ブラウザーで評価完了後もホームの課題状態だけ「評価待ち」となる不整合を検出。教師側の最新評価判定を`TaskProgressStatus`へ抽出して共有し、生徒の提出後表示へ適用。DBの履歴は書き換えない。再編集/未着手・明示要対応を以前の完了評価で隠さない。赤テストは8件中7成功/1失敗、修正後の全体と再起動後readbackは成功。
- 検証環境の初回起動は共有Gradle journal lockでexit1。共有lockを削除せず、既存cacheをread-onlyで参照してlock/journalを除外した専用cacheへコピーして解消。専用schemaへ正本V1〜V23 SQLをDB所有者で順に適用した。Flyway CLIの成功と同一視せず、共有のglobal設定は変更していない。
- テストの不備を分離して修正: ①`GET /auth/logout`は正しく405になるため正規POST+CSRFへ変更。②初回生徒はDB triggerによりパスワード変更必須となるため、正規変更を経て同意画面へ進むよう修正。③他者の評価は既存の本人検索契約どおりホーム302なので、誤った403期待を修正（アンケート他者403は別途確認）。④native formのCRLFに合わせて課題fixtureを作成し、HTTPのLF作成とブラウザー再送の混在で不要な入力変更判定を発生させない。製品の改行正規化契約は変更していない。⑤JSTLのURL出力に対し`&amp;`限定assertをやめ、同じURLの`&`とHTMLエスケープ表現を区別した。アンケート初回失敗時は回答0を確認し、専用DBの未使用設問fixtureだけを再準備して再実行。評価を再生成/捏造して埋めていない。
- 課題・プロンプト・適用・公開・提出・完了評価は正規HTTP操作で作成。実Pythonで入力3→出力6、提出前チェック1/1、実Pro通常評価の完了/検証済み応答・固定版/入力・観点/理由のDB再読込、回答の下書き保存/提出/再表示/重複提出で不変を確認した。アンケート設問登録UIは未実装のため、課題別設問設定だけを検証DBへ登録した。これを教師による全設定UIの完成とは扱わない。
- 実HTTPの正確なコマンドは `docker exec -e LEARNING_FLOW_HTTP_TEST=true -e LEARNING_FLOW_HTTP_BASE=http://ppe-learning-flow-runtime:8080 ppe-learning-flow-runtime gradle --project-cache-dir=/tmp/ppe-learning-flow-project-cache -I /tmp/ppe-learning-flow.init.gradle test --tests servlet.journey.<対象クラス> --rerun-tasks --no-daemon --console=plain`。`LearningFlowFixtureTest`1件→`registerStandardRubric`→`TeacherLearningFlowRuntimeTest`2件→`StudentLearningFlowRuntimeTest`1件→`SurveyLearningFlowRuntimeTest`1件を順次実行し全成功、各exit0。最終修正後にアプリを再起動して`LearningFlowReadBackRuntimeTest`1件も成功、exit0。ゲート有効の一括実行に順序を委ねない。fixtureは空DB必須、surveyは本人の実completed評価必須。
- 外部ゲート無効の最終全体コマンド: `docker exec ppe-learning-flow-runtime gradle --project-cache-dir=/tmp/ppe-learning-flow-project-cache -I /tmp/ppe-learning-flow.init.gradle test war --rerun-tasks --no-daemon --console=plain` → exit0、358件中232成功/126明示skip/失敗0、WAR成功。ゲートskipを実DB/外部API成功と扱わない。新規Javaのエディター診断は正常、既存Gson参照解決の環境診断は変更していない。
- 今回の実生成POSTは調査中の再実行を含め8回、全てPro Preview/HTTP200。最終の教師生成2回は26,469/26,733ms、通常評価は20,823ms。追加再読込・画面確認では再生成していない。入力は全て合成データで、実生徒データは送っていない。費用/quota/負荷・人手による採点品質校正は別受入。
- 統合ブラウザーで教師のPro/適用中/実生成物、同じ生徒提出の評価/観点/理由、評価ID100→アンケート→提出ID1の戻る導線、提出済み回答の非編集表示、再起動後ホームの完了/評価済み/回答済み、不同意生徒の常設バナー0・課題利用可・アンケート対象外を確認。pointer clickの可視/安定待ちtimeoutはnative form/DOM/実HTTPで補った。ブラウザー用assertの教師landing期待違いと提出後にtextboxを探したtimeoutも記録し、実表示/静的回答と照合した。CLI/全pointer E2E認定ではない。
- 8080反映: `docker exec programming-process-evaluator-app-live gradle --project-cache-dir=/tmp/ppe-learning-live-project-cache war --no-daemon --console=plain --warning-mode all` → exit0。`docker restart --timeout 195 programming-process-evaluator-app-live` → 成功、教師/生徒login200、新しい共通状態判定・初回適用methodの実classを確認。既存課題10/11のmetadata hashは一致、無関係18081のlogin200。共有利用者の資格情報や既存課題は変更していない。
- 清掃済み: 自作18082アプリ、専用schema/追加grant、一時秘密envファイルだけを削除し、schema/grant残件0・ファイル不存在を確認。清掃後も8080両login/18081のlogin200と課題10/11のhash一致を再確認。テストXML・安全なprovider status/所要時間・詳細reportはsession artifactsへ保持した。

## 2026-10-08: 公開URLの横断整理と受入

- [方針](./url-routing-policy.md)と機能仕様第148版に従い、24 Servlet・JSPの業務リンク/フォーム/endpoint、ログイン/強制変更/戻り先を確認し、本人情報と他者管理を区別した。旧URL7種は認証・ロール・セッション版・強制変更制限の後にGET/HEAD302、POST等307で誘導する。プロトタイプ、内部JSP/CSS/JS配置、課題/APIの既存系列は維持した。
- TDDの初回`ApplicationUrlContractTest` / `LoginServletTest`は11件中6成功/5失敗、exit1。旧mapping・JSPの旧リンク・旧landing/強制変更先を検出した意図した赤テスト。修正後の対象認証/アカウントテストと全体回帰は成功した。
- 専用schemaへのapp権限による`flywayMigrate`はV11でMySQL1419（trigger作成にSUPERが必要）となりexit1。自作の空の専用schemaだけを再作成し、DB所有者で正本のV1〜V23 SQLを順に適用した。共有DBや`log_bin_trust_function_creators`等のglobal設定を変更していない。この直接SQL適用をFlyway CLI成功とは扱わない。
- 専用containerをinternal DB networkだけで起動した時はpublishされたportがなくcurl exit7。自作containerだけを既存outbound/application networkへ接続し、18082のlogin200を確認。別containerからのGradle検証は共有journal cacheのPID/lockによりexit1。lockを削除せず、専用アプリcontainer内で検証を実行して解消した。
- 初回の実HTTP5件は4成功/1失敗、exit1。生徒hostから教師URLへの要求は従来契約どおり教師hostのloginへ302となるが、テストが誤って403を期待していた。host違いの302と、教師hostに生徒sessionを提示した時の403を分けてassertし、専用schemaを再作成して再実行。最終5件は全成功/失敗0、exit0。teacher reset/強制変更/失効、旧student password POST307→正規処理→状態/版/資格情報DB再読込、任意変更302→正規account、旧consent POST保存・再読込、不正CSRF403を確認した。
- 実HTTPの正確な実行: `docker exec -e GEMINI_API_SMOKE_TEST=false -e URL_ROUTING_HTTP_TEST=true -e URL_ROUTING_HTTP_BASE=http://ppe-url-routing-runtime:8080 ppe-url-routing-runtime gradle --project-cache-dir=/tmp/ppe-url-test-project-cache --init-script /tmp/ppe-url-routing.init.gradle test --tests servlet.auth.ApplicationUrlRuntimeTest --no-daemon --console=plain` → 成功。専用DB/空users/専用アプリURIをtest側で検証する。init scriptは`jdk.httpclient.allowRestrictedHeaders=host`、並列無効、buildをcontainerの`/tmp`へ分離するだけで、認可やDB処理をmockしない。
- 全体回帰の正確な実行: `docker exec -e GEMINI_API_SMOKE_TEST=false -e URL_ROUTING_HTTP_TEST=false ppe-url-routing-runtime gradle --project-cache-dir=/tmp/ppe-url-test-project-cache --init-script /tmp/ppe-url-routing.init.gradle test war --no-daemon --console=plain` → 成功、343件中223成功/120ゲートskip/失敗0、WAR成功。専用アプリのGeminiキーは空、実APIゲート無効。外部Gemini要求は0。
- 統合ブラウザーで教師login→students・JSの一覧/検索JSON、旧URLのクエリ保持、本人情報/password、生徒login→ナビ研究同意・回答再表示・旧account/passwordから正規画面、管理者login→teachersと学校tabの実DB表示を確認。教師password下部リンクと一部管理者clickは可視/安定待ちでtimeoutし、統合toolのAPIRequestContextは`Storage.getCookies`未対応で失敗した。DOMでhref/可視寸法を確認し、直接GET・実HTTP、native form submit/DOM tab clickで対象画面を確認した。ポインタclick/CLI認証E2Eの全面合格とは扱わず、この制約を保持する。
- エディター診断では既存Gson参照に依存解決エラーが残るが、追加URLクラス・filter/login/rootと追加テストの診断は正常。Gson依存・既存importは変更せず、実際のGradleコンパイル・全体回帰・実JSON/画面読込は成功している。エディターclasspathの復旧は今回のURL修正対象外。
- 8080反映: `docker exec programming-process-evaluator-app-live gradle --project-cache-dir=/tmp/ppe-url-live-project-cache war --no-daemon --console=plain --warning-mode all` → 成功。`docker restart --timeout 195 programming-process-evaluator-app-live` → 成功。両login200、teacher/student hostのroot302先、bare role/新旧保護URLの認証redirect、実classの新URL定数を確認した。Gradle9向け既存WarPluginConvention非推奨警告は保持。
- 自作専用containerはSIGTERM終了（exit143、テスト結果とは別）後に削除、専用schema/grantは0、一時秘密設定も削除。課題10/11のmetadata hashは前後一致、無関係18081のlogin200。共有利用者のpassword・課題・DB状態、APIキー/model/envは変更していない。

## 2026-10-08 06:41〜07:00 JST: Gemini失敗の原因切り分け

- 同じ登録済みキー・3.7 Flash・合成入力で、応答schemaなしの短いInteractions要求、74bytesの最小schema付き要求、教師の揺らぎ生成を各1回比較した。短い要求も60秒上限でtimeout、最小schema付きも60,006msでtimeout、教師要求は503/高需要（49,171ms、入力6,449bytes/schema509bytes）。教師だけの入力サイズ・画面・DB処理を共通の失敗原因とは扱えない。最初の4テストはmetadata取得1成功/生成3失敗、exit 1。
- 同じキーでモデルmetadata GETは200（118ms）。モデル名`models/gemini-3.7-flash`、version `3.7-flash-08-2026`、標準generateContent対応を確認した。公式モデル文書にも3.7 Flashが存在する。APIキーやモデル名の取り違えを主因とする根拠はない。
- 同じモデル・短い合成入力で標準generateContentも1回確認し、503/高需要（47,448ms、1テスト失敗、exit 1）。Interactions固有の保存指定・JSON schema・Api-Revisionだけの問題ではない。標準APIはInteractionを作成しないため診断要求にstoreパラメータはなく、アプリのInteractions要求はstore:falseのまま維持した。
- 本人回答で現行検証キーのプロジェクトは課金設定済み。「無料枠だから」と断定しない。Googleの公開statusはAll Systems Operationalだが、特定モデル/プロジェクトの個別要求の成功保証とは区別する。キーの紐付け・契約quota・残高・実際のpriorityのconsole実査は未実施。
- 本人が「アプリ設定は変えず3.8 Flashへ1回だけ比較」を承認。同じキー・endpoint・revision・29bytes入力・74bytes schema・store:falseを用い、モデルだけを3.8へ変えた最小要求が200（2,953ms）、JSON検証とファイル保存/再読込に成功（1テスト成功、exit 0）。
- 時間帯による差を確認するため、直後の3.7最小schema要求を1回再確認し、200（8,765ms）、同じJSON検証・保存/再読込に成功（1テスト成功、exit 0）。3.7が永久に使用不能、または3.8だけが動くとは結論しない。今回の直接の失敗はGoogleが高需要として返した503と生成応答待ちであり、容量/可用性が時間帯で揺れることを確認した。Google内部の具体的な容量不足理由まではAPI応答から特定できない。教師の完全な実API縦断は依然未完了。
- 今回追加は生成POST6回（200:2、503:2、timeout:2）、metadata GET1回。生成POSTの累計は17回（200:3、503:11、timeout:3）。APIを無制限に再送せず、比較・再確認後に追加生成を停止した。実生徒データは使用していない。
- 診断テストは明示flagでゲートし、別モデル比較/直後再確認には追加flagを要求する。最終の外部無効回帰は8成功/7明示skip/0失敗、exit 0。実APIで失敗した実行を、このローカル回帰成功で帳消しにはしない。
- Gradleの出力を専用containerの`/tmp`へ隔離し、既存8080のclass出力・DB・利用者・課題・モデル設定・envを変更せず、再起動もしていない。測定JSON・合成成功出力・原因切り分けレポートはsession artifactへ保存。正確な実行条件は[AI計画の原因切り分け記録](./feature-plans/teacher-prompt-ai.md#原因切り分け記録2026-10-08-06410700-jst)を参照する。

## 2026-10-08 06:18〜06:36 JST: AI追加確認・生成中エディター修正

- 教師実API縦断の追加1件は、揺らぎ生成の3試行すべて503（14,225/9,038/16,992ms、Retry-Afterによる30秒待機を2回）。JUnit 0成功/1失敗/0skip、exit 1。外部累計11要求（200:1、503:9、timeout:1）。教師縦断の後段は未到達であり、モデル高需要の失敗を成功扱いしない。
- 認証ブラウザーで、合成課題13だけを用いた保存/reload、生成中保存拒否409と行版保持、揺らぎ/評価例の15分超中断→フォーム保存→failed/reload/監査、未認証ログイン遷移、不正CSRF403、共有feedback確認/キャンセルを検証した。合成の評価例復旧fixtureはAI実生成の証拠とは扱わない。外部APIの追加UI呼出しなし。
- 生成中のCodeMirrorが元textareaのreadonlyを継承せず編集可能だった。初回assertionで再現し、readonlyの明示設定後に生成中true/中断下書きfalseと保存拒否/復旧を確認。配信JSの構文/editor検査、WARとdiff checkは成功。既存機能の編集不可仕様の修正で、公開済みプロンプトの変更ではない。
- 初回のブラウザー試験は、同一URLへのPOST redirect完了前のreloadでERR_ABORTED。navigationを先に待つ手順へ修正して成功。不正CSRF検証で`form.action`が同名の入力項目に隠され誤ったURLへ送信され400となったため、`getAttribute('action')`に修正して403を確認した。モーダルの表示アニメーション中の閉じ待ちtimeoutは、表示後にキャンセルを再実行して解消。新しいJSの配信確認とブラウザーcache上の旧JSの実行は区別した。
- 清掃前の監査照会で存在しない`reason`列を使用し失敗したため、実schemaの`detail`/`target_type`/`target_id`で再照会し復旧監査3件を確認した。SQL照会失敗をDB受入成功に数えない。
- 合成課題13はUIから論理削除し、archived/deleted・処理中0をDB確認。既存課題10/11の主要metadata hashは不変。専用schemaのusers/tasks各0を確認し、そのschema/grantだけを削除して残存0を確認した。検証の正確なコマンド・境界は[追加受入記録](./feature-plans/teacher-prompt-ai.md#追加の実api認証ブラウザー確認2026-10-08-06180636-jst)を参照する。

## 2026-10-08 05:48以降 JST: AI信頼性・実API縦断

- 最初の合成smoke1回は503（モデル高需要）で失敗した。共通retryを実装後、通常評価の合成実APIは200（21,586ms）で成功し、DB request成功、2観点の評価、検証済みresponse、input snapshotの保存・再読込を確認した。
- 現行schemaに対する評価DB fixtureの最初の8件は課題の必須`school_id`未設定で失敗した。fixtureを現行schemaへ合わせて再実行し、8件成功/失敗・skip0となった。失敗した最初のDB実行では外部呼出しに到達していない。
- 教師実API縦断テストの最初のコンパイルでprovider wrapperのthrows宣言不足が発生し修正。外部呼出しまで進んだ1回目は揺らぎ生成の3試行がすべて503（4,802/8,297/5,904ms）。2回目も2試行503（2,790/40,058ms）、3試行目は60秒HTTP timeout。いずれも高需要と明示された応答であり、schemaが原因とは断定しない。上限付き再試行後に生成失敗を記録し、成功扱いにはしない。
- 上記までの外部要求は計8回（200:1、503:6、timeout:1）。入力は専用DB上の合成課題/提出/ログのみ。教師の揺らぎ→評価例→preview→確定の実API受入は未完了。自動的な無制限再送や未承認モデルへのfallbackは行わない。
- 教師生成のrubric payloadが観点名だけだったため、DB登録済み標準rubricの尺度値・ラベル・記述も送るよう補完し、契約テストに追加した。通常評価は従来から尺度記述を送っている。

## 2026-10-08 05:36〜05:46 JST: AI再開・モデル/保存設定の整合

- TDD初回14件中6件失敗。3.7 Flashが教師clientで拒否される、旧2.5モデルがControl/clientで許可される、通常/教師生成のHTTP要求にstoreがない差分を再現した。共通モデル定義とstore:falseを実装後、専用DB4件を含む26件が成功（失敗/エラー/skip0）、WAR成功。正確な最終コマンドは[AI再開受入記録](./feature-plans/teacher-prompt-ai.md#t-ai-001002-受入記録)を参照する。
- 汎用runTestsはJavaテストを検出しなかったため既存Gradleを使用した。エディターのGson型未解決は継続するが、実Gradleコンパイル成功とは区別する。
- 8080再起動後のreadiness確認で実在しない `/auth/teacher/login` を使い、待機が失敗した。正しい `/teacher/account/login` はHTTP200であり、Tomcat起動も確認。再ログイン後の画面保存・reload受入は成功した。過去のGradle lockエラーが累積container logに含まれたが、今回の再起動は成功した。
- 共有確認ボタンの誤ったselectorによるtimeoutは、実際のdialogのボタンで解消。今回生成した合成課題12/プロンプト1だけを検証し、課題12はUIから論理削除。既存課題10・進捗デモ11は未削除のまま保持した。
- 専用schemaは現行schema/Flyway履歴だけを複製し、合成DB4件を実行した。空DB migrationの再検証ではない。fixture残存0を確認して専用schema/grantを削除。外部生成APIは呼び出しておらず、実モデル可用性・外部保持の実確認・retry改善は後続に残す。

## 2026-10-07 18:46以降 JST: 個別初期パスワード検証

- 全体のGradle `test war --rerun-tasks --no-daemon --warning-mode all`は成功。生徒資格情報のDBテストは専用Compose projectで2件成功した。
- 最初に共有ローカルDB上へ新設した専用schemaのFlyway V11がMySQL error 1419（binary logging/routine creator権限）で失敗した。作成したschemaと同schemaへの付与権限だけを削除し、既存DBデータは変更していない。以後は独立Compose project/volumeを使い、migration中だけその専用MySQLの`log_bin_trust_function_creators`を1にし、適用後は0へ復元した。
- 独立projectの空Gradle cacheで`--offline`を使ったmigration準備はFlyway Gradle plugin未キャッシュで失敗した。専用projectでオンラインのGradle `help`を実行してpluginを解決した後、全migrationとDBテストが成功した。
- ホストの`node --check src/main/webapp/js/teacher/account/account.js`は`node: command not found`（exit 127）。既存8080のブラウザーで配信中の同JSを取得し、`new Function(source)`による構文検証はPASS（19,457文字）。Dockerは`docker info`で利用可能。ブラウザーの認証済みadmin/teacher UI受入は今回未実施。
- browser toolの`page.request.get`は`Storage.getCookies`未対応で失敗したため、ブラウザーをJS資産URLへ直接開いて構文確認した。アプリ側の障害とは扱わない。独立ComposeのGradle/MySQL container/volumeは検証後に削除し、共有アプリ・DB・volumeは維持する。

## 2026-10-07 17:56以降 JST: 工程12aの再検証・ブラウザー受入

- 17:56の対象19件とWARは成功。専用V1〜V22 DBで全体270件中181成功・89明示ゲートskip・失敗/エラー0。教師本人DB4件と管理者教師DB2件はskip0で成功し、前項の不具合解消を確認した。コマンドと受入範囲は[認証計画の工程12a完了記録](./feature-plans/authentication-and-login.md#工程12aの完了記録2026-10-07)を参照する。
- ホストの`node --check`はNode未導入で実行できなかった。複合コマンド全体のexit 0をNode成功とは扱わず、ブラウザーで変更JS 3本を取得して構文確認し、全件成功を確認した。Nodeのインストールはしていない。
- 再起動直後の8080に一時的なEmpty reply/404があった。起動完了後の再確認は教師ログイン200であり、継続的な障害ではなかった。
- ブラウザーツール側の結果整形で`URL is not defined`が出た。クリック・通知は先に実行されており、アプリ例外ではない。以後は実行側で`page.url()`をそのまま返し、URL解析はページ内で行った。
- 通常クリックの安定待ちとaria-disabled要素の通知待ちでタイムアウトがあった。権限なしホームのマウス/Enter通知は確認済み。権限あり未実装の通知、表示切替、フォーム確認は標準DOMイベントから確認し、アプリ側の例外は観測しなかった。タイムアウトの原因をアプリ不具合と断定せず、通常ポインター操作の網羅的再確認は未実施として残す。
- 確認ダイアログの表示途中にキャンセルした検証では閉じ待ちがタイムアウトした。Bootstrapの表示アニメーション完了後にキャンセルを再実行して成功し、確定後のログイン遷移・成功通知も確認した。入力失敗、CSRF、旧版などの期待された400/403/409は異常なサーバー障害と区別する。
- 合成教師の清掃で「一覧から行が消える」という検証が失敗した。管理者一覧は論理削除後も履歴確認用の行を保持するためであり、実際の削除は成功済みだった。「削除済み」状態・更新版・失効を確認する検証へ修正し、履歴を物理削除して補わなかった。
- 専用DB利用者残存0を確認後に当該schema/grantを除去し、それぞれ残存0とroutine設定0を確認した。通常DBの合成教師は論理削除し、既存確認アカウントのパスワードは維持した。外部API・実生徒データは使用していない。

## 2026-10-07 17:41〜17:54 JST: 工程12aの初回変更・コンパイル・権限テスト

- 初回変更の回帰テストを先に追加し、教師の必須変更が生徒URLへ遷移する不具合を再現した（7件中1件失敗）。教師専用の変更先を追加し、保存済み業務landingが必須変更先を上書きしないよう修正した。
- DAO追加メソッドの挿入位置が既存メソッド内に入り、17:48の対象test/WARでコンパイルエラーになった。メソッド境界を修正した。
- 17:54のコンパイル/WARは成功したが、権限テストのResultSet proxyが列番号を扱わず、追加した機能集合にnullを返した（19件中1件失敗）。DAOを明示的な列名で取得するよう修正した。再検証結果は工程12aの完了記録へ追記する。
- 外部APIは使用していない。SQL履歴には資格情報を記載しない。エディターの依存関係解析が古い診断を表示する場合も、実際のGradleコンパイル結果と区別する。

## 2026-10-07 17:19 JST: 教師ログイン初期画面の優先順位

- ユーザー指示は生徒管理を初期画面とすることだったが、複数の機能権限がある教師では課題編集 → プロンプト設計 → 生徒管理の順で選ばれていた。生徒管理権限ありのケースを最優先へ修正し、権限なしfallbackと管理者/生徒/強制変更の遷移は維持した。権限判定済みの遷移先がない教師のfallbackも教師メニューへ変更した。
- 対象テスト6件成功、失敗/エラー/skip0、WAR生成成功。8080の再ログイン後の生徒管理表示、メニュー制限、課題/プロンプト/管理者URL403、学校filter付きID/PW CSV200を確認した。コマンド・詳細は[認証計画](./feature-plans/authentication-and-login.md#生徒管理実装後の教師初期画面2026-10-07)を参照する。
- 再起動直後のEmpty replyと一時404はretry後に解消し、教師ログイン200。暗号化資格情報・CSVの安全要件は変更せず、外部APIは使用していない。

## 2026-10-07 16:42 JST: 工程12 最終受入と修正

- 失敗監査用helperの挿入時に`requireTeacher`がtransactionメソッド内へ入ったコンパイルエラーを修正し、専用DB2件を含む全体257件（168成功/89 gate skip/失敗・エラー0）とWARを確認した。
- 初回browserは失敗ビルド後のGretty状態で404。正常ビルド・app再作成後に8080へ再ログインして解消した。再作成は資格情報鍵の環境注入も反映するために行い、鍵の再生成はしていない。
- 作成モーダルの表示遷移中にsubmitするとBootstrapのhideが無視され、共有確認待ちが完了しない問題を再現。表示遷移を追跡して`shown.bs.modal`を待ってからhideするよう修正し、レベル2/新規クラス作成と再設定で再検証した。アニメーションを削除して回避していない。再度開いたパスワード欄のtypeがtextのまま残る問題も、フォーム初期化でpasswordへ戻すよう修正した。
- 操作名なしPOSTが500となることをHTTPで再現。immutable listの`contains(null)`がNullPointerExceptionとなることを追加単体テストで再現し、nullを先に拒否するガードを追加した。最終回帰は259件（168成功/91 gate skip/失敗・エラー0）とWAR成功。専用DBを清掃したため最終runではDB2件はskipだが、それ以前のゲート有効runで成功済み。コマンドは[工程12計画](./feature-plans/teacher-student-accounts.md#自動検証)を参照する。
- browser検証では非表示tabのraf待ち、dialog buttonのselector違い、削除後も全状態filterでは5件残ることを3件と誤認した待ちにtimeoutが発生した。timer polling・実際のbutton名/状態へ修正し、共有確認の2人選択/キャンセル、詳細3種履歴、資格情報消去、パスワード再マスク、375px/モーダル359pxを再検証した。セッション切れのJSONリクエストは成功扱いにせず画面へ再ログインした。
- HTTPで一覧/CSV/詳細/資格情報確認/再設定/ロック解除/停止/解除/2人削除、CSRF/権限外403、古い版400を確認。生徒の初回/再設定後変更、レベル1変更拒否、旧セッション失効、本人変更後のCSV空欄と暗号行除去、削除後履歴保持も確認した。
- 専用schema/付与権限の残存0、routine設定0、通常app/DB/runner稼働を確認した。JSP/JS/CSS診断なし。クリップボード許可・全browser/OS・本番TLS/cookie/鍵運用は未確認として残し、外部APIは呼んでいない。
- 最終app再起動直後はEmpty replyが3回発生し、retry後にログイン200。再認証後の画面別名/一覧/詳細/CSVは200、操作名なしPOSTは400を確認した。
- 表示遷移の最初のbackdrop未表示段階では、show class確認を先にするとhide待ちを飛ばす余地があるため、遷移待ちをclass確認より前へ移した。1つの同期イベント内で開く/入力/submitする最速ケースを確認し、backdrop未表示でのsubmit、同時モーダル1件、キャンセル・入力消去が成功した。JSのみの最終差分をWARへ再格納し、格納JSのSHA-256一致を確認した。

## 2026-10-07 15:14 JST: 工程12 初回コンパイル

- VS Codeテスト機能は新規JUnitを検出せず、ComposeのGradleへ切り替えた。
- 初回コンパイルで`studentVersion`のスコープ不整合を検出。教師分岐へ誤って入っていた生徒版取得を分離して修正。
- 次のコンパイルでCSVのクラス表示に参照した`getDisplayName()`が未定義と判明。既存DTOへ学校内の学年/クラスを結合する共有表示メソッドを追加した。再検証は工程12計画へ記録する。
- 初回の独立`/tmp`キャッシュで単体テストは成功したが、依存解決に9分40秒を要した。既存Gradle homeを共有する試行はjournal lock（稼働appのPID253）で失敗。稼働プロセスを停止せず、既存modules cacheを独立した永続Gradle homeへコピーし、offline実行へ切り替えた。V1〜V21の専用DB移行・DB2件を含む対象テストとWARは21秒で成功。
- エディターは新規ServletのGson解決に未解決診断を表示するが、実際のComposeコンパイルは成功。JSP/JavaScript診断はなし。

## 2026-10-07 14:33 JST: 管理者固定IDの仕様差分修正

- 期待結果: 教師ログインの小文字`admin`と正しい資格情報・管理者ロールだけが管理画面へ遷移する。教師本人のアカウント管理は仕様追加のみ。
- 原因: 初期管理者作成が任意IDを受け付け、認証では管理者ロールだけを見ていたため、長いデモIDでも管理画面へ遷移できた。固定ID・予約IDの境界をControl/Filter/教師入力/初期作成に追加し、デモ管理者の内部IDと履歴を維持してログインIDを修正、監査を記録した。
- 初回 `test war --tests ...` は`--tests`がwar taskへ適用されUnknown command-line optionで失敗した。`test --tests ... war`の順に修正し、19件成功・失敗/skip0・WAR成功。エディターtest toolは対象JUnitを見つけず、Compose Java21/Gradleで検証した。
- restart直後のcurlはEmpty replyとなり、retry後200を確認。8080の認証browserでadmin成功・旧ID/大文字違い/誤PW拒否、予約教師ID作成400・通常生徒ログイン/管理URL403を確認した。変更Java/テストの診断0、資格情報は文書・ソースに記載していない。
- 最終`git diff --check`成功。Markdown診断には既存の改行HTML・末尾の箇条書き様式・表の空白等が報告された。今回の追加節に起因する指摘はなく、無関係な既存の整形は変更していない。
- 本番TLS/cookie・実運用アカウントは対象外。教師本人のパスワード変更は未実装のまま、[仕様](../function-specification.md)第131版と[ロードマップ](./implementation-roadmap.md)工程12aへ記録した。

## 2026-10-06 15:12 JST: 教師アカウント管理の実装・検証

- Gradleによる初期の対象テストは成功。VS Codeのテストtoolは新規JUnitを検出せず、エディターのJava診断は既存Gson依存を解決できなかったため、Java21のCompose/Gradleで検証する。
- 失敗監査メソッドを追加するパッチの挿入位置が既存メソッド内部となり、Java compileでillegal start of expression。メソッド境界へ移動して修正した。
- 専用schema `ppe_teacher_test_20261006` のV11 migrationでMySQL error 1419。既存と同じroutine権限制約。部分適用schemaを使い回さず、今回作成したschemaのみ再作成し、一時的なroutine作成設定で移行後に元へ戻す。
- Grettyの現在のappログにscannerの接続エラーがあり、自動reloadを信用せず、通常appを再起動して8080のHTTPと新URLを確認する。
- 統合testのLoginPortal importはAuthenticationControlのネストenumへ修正。学校回帰testは専用DB名ガードが異なるため、教師用DBと分けて実行した。app稼働中はGradleのproject fileHashes lockも共有されるため、`--project-cache-dir=/tmp/teacher-validation-project`を付けて解消した。
- 初回の認証browser作成POSTはFormDataのmultipart形式をServletが解析せずCSRF 403。通常フォームと同じURLSearchParamsによるapplication/x-www-form-urlencodedへ修正した。保護を解除せず、CSRF/確認付きの実POSTを再検証する。
- 再検証: 教師管理/入力/資格情報/Servlet/ログイン/プロンプトのfocused suiteは19件成功。セッション更新版・古いhash・権限変更時のログインロック維持を追加後、教師管理DB test2件を再実行して成功。プロンプトの独立機能権限/学校権限と既存保存・再評価のDB回帰4件、学校管理DB4件＋入力2件も成功。全体 `test war --no-daemon --warning-mode all` は249件中160成功、89件は明示ゲートでskip、失敗/エラー0、WAR成功。詳細な実行コマンドは[認証計画](./feature-plans/authentication-and-login.md#工程11教師アカウント管理追加バッチ2026-10-06)を参照する。
- 8080の認証browserで教師作成・権限編集・再設定と旧PW拒否/新PW成功、停止/解除/削除、変更後のセッション失効・削除後の履歴保持、検索/CSV、学校登録/日本語名とレベル変更・生徒登録済み学校のレベルdisabled、管理者以外403と不正CSRF403を確認した。資格情報はモーダル終了/再読込で消える。375px表示はページ幅375px・作成モーダル359pxで全体の横はみ出しなし。
- 統合browserが`document.visibilityState=hidden`のためクリック安定待ちやモーダルfadeが停止し、検証selectorにも確定ボタン名/履歴modal ID/複数要素一致の誤りがあった。実際のDOMに合わせてselectorを修正し、後半のmodal受入では一時DOMのfadeを除去してbutton/formイベントで検証した。ソースのアニメーションは変更していない。modal終了時のfocus/aria-hidden警告に対して今回のmodalにfocus解除を追加した。アニメーション自体と全browser/OS組合せの最終受入は未確認。
- 最終restart後の初回curlはEmpty replyで、retry後に教師ログイン200・未認証管理URL302を確認。再ログインした管理者でconsole/別名/旧学校URL/詳細/履歴/CSVは200、不正CSRF403。fixture確認SQLに存在しない`school_name`を指定したため1054となったが保存処理には影響せず、学校コード/ID指定へ修正した。
- エディターの既存Gson未解決診断は残る。変更JS/CSS/TeacherSessionControl/DB testの診断は0で、実際のJava21コンパイルは成功。外部APIは呼び出していない。実運用アカウント・本番TLS/cookieは未確認として保持する。
- 後片付け: 今回作成した専用DB3個/付与権限、資格情報を含む一時fixture2ファイル、学校UI fixtureを削除した。残存schema/学校fixtureは0、routine設定0、通常app/DB/runner稼働を確認。合成adminと削除済み教師の履歴はデモ確認用に残した。`git diff --check`成功。

## 2026-10-06 14:49 JST: 課題編集テストケースの高さ修正

- 期待結果: 空欄・1行の入力/出力欄は1行分の高さで表示し、改行で伸び、改行を削除すると縮む。
- 原因: `task.js` はCodeMirrorラッパー自身へ `task-form-code-mirror` と `is-shell` を付与していたが、CSSは `.task-form-code-mirror.is-shell .CodeMirror` という子孫セレクターだった。高さ指定が一致せず、CodeMirror既定の300pxが残った。textareaの `rows="1"` と最小高さの変更だけでは解消しなかった。
- 対応: ラッパー自身を選ぶ `.task-form-code-mirror.is-shell.CodeMirror` に修正し、同じ構造のフォント/高さ/行スタイルのセレクターも整合させた。プロトタイプ・DBは変更していない。
- 再検証: 8080で配信されている実CSSと `initializeCodeEditor` をブラウザーの一時DOMで使用した。空欄36px、3行77.48px、1行へ戻すと36pxとなり、伸縮assertionはPASS。一時DOMは削除した。CSS診断と `git diff --check` も成功。認証済み教師画面での操作全体は未確認。

## 2026-10-06: 公開課題の期限延長・対象クラス追加受入

- 専用MySQLでの初回migrationはV11 routine作成時にMySQL error 1419で失敗した。独立課題コピー受入環境で記録済みの手順に従い、この使い捨てCompose projectだけでmigration時に `log_bin_trust_function_creators=1` を設定し、V1〜V19のmigration/validationを完了した。
- 専用Compose app稼働中の統合test起動は共有Gradle cache journal lock timeoutとなったため、appを起動せず同じ専用DBへtest runnerから接続して成功した。別の再実行ではMySQL設定を0へ戻した後だったため、rollback確認用triggerを作る `rollsBackAllDraftChangesWhenMySqlRejectsHintInsert` が権限エラーとなった。専用DBに限り設定を1へ戻して全suiteを再実行し、31 tests / 27 passed / 0 failures / 4 browser-fixture skippedを確認した。終了後は設定を0へ戻し、専用Compose container・volume・networkを削除した。
- 専用DB統合testで期限延長、期限切れassignmentの再開、既存提出履歴保持、stale version拒否、追加割当の重複/他校拒否・監査・冪等再送を確認した。追加クラス候補のため、archivedを含む全割当履歴を画面データへ渡す処理と履歴取得testを追加した。履歴データの取得はDB testで確認したが、この候補表示だけを対象とするbrowser再確認は未実施。
- 合成教師による認証browser受入では期限延長/クラス追加の成功通知と更新後表示、未認証 `/teacher/task` の302 redirectを確認した。Node.js CLIがホストにないため `node --check` は実行できなかった。変更したJavaScriptはbrowserで読み込まれ、機能操作も実行された。

## 2026-10-06: 独立課題系列コピーのbrowser受入環境

- 専用MySQL schemaの初回 `flywayMigrate` は、通常アプリDBユーザーにV11 routine作成権限がなくMySQL error 1419で失敗した。Flywayのrepair後に同じ部分適用schemaへ再実行すると、MySQL DDLが非transactionalなためV11のcheck constraintが重複して停止した。
- 対応: 作成した使い捨てschemaだけを削除・再作成し、migration時のみMySQL `log_bin_trust_function_creators` を一時的に `1` にしてV1〜V19を適用した。設定を元の `0` に戻し、再確認した。共有アプリDB/schemaは変更していない。
- 再検証: 専用schemaへのmigration、独立コピーfixture test、現行アプリのport `18084` 起動とHTTP応答が成功。browser受入終了後に専用schema/DB user/app containerを削除し、port listenerがないこととMySQL設定が `0` であることを確認した。

## 2026-10-06: 公開後課題改訂の隔離DB受入

- 共有DB/8080を避け、使い捨てのlocalhost限定MySQL (`ppe_teacher_task_test_revision_20261007`, tmpfs) を使用した。Gemini実API・実生徒データは使用せず、Flyway V1〜V19の `flywayMigrate flywayValidate` が成功した。schema追加は不要だった。
- 初回の新DBテスト実行では、新規テストfixtureが有効な公開assignmentを作らずEditor参加状態のassertionに失敗し、もう一方ではself-reference `tasks.supersedes_task_id` を残したままfixture親taskを削除してcleanupが失敗した。テストをassignment付きに修正し、cleanupでテスト所有課題の参照だけを先に解除した後、2つの改訂テスト、`TeacherTaskDatabaseTest` 全体、全体buildを再実行して成功した。
- `TeacherTaskDatabaseTest` は23 tests / 22 passed / 0 failures / 0 errors / 1 browser-fixture skipped。作成直後の旧task/assignment維持、Editorを開いただけの `not_started` 参加の許容、学習開始後の改訂拒否、新prompt設定後の再公開、旧assignmentのarchiveを検証した。
- focused form/control/DAO/servlet testsと、同じ専用DB設定での `gradle build` は `BUILD SUCCESSFUL`。`git diff --check` も成功した。
- 改訂導線の認証browser受入は未実施。`task.js`の `node --check` は `node: command not found` で実行できず、今回変更後のJavaScript parser検証は未確認。依存manifestは変更せずNode.jsも追加導入しなかった。
- 実workerの周期/再起動後回収、期限境界、提出/評価/ログ履歴の保持、学習開始後の別系列複製、期限延長/対象追加は未確認。検証終了後に専用MySQL containerを停止・削除し、共有コンテナ/DBは変更していない。

## 2026-10-06: 課題公開スライス隔離受入

- 専用Compose project `ppe-task-publication-acceptance` と専用schema `ppe_teacher_task_test_20261006_accept01` のみを使用。共有8080/DBには接続・変更せず、Gemini実API・実生徒データも使用しなかった。
- `flywayMigrate flywayValidate` はV1〜V19で成功。`TeacherTaskDatabaseTest` は20件すべて成功し、予約割当の公開/期限切れ、2 workerの重複遷移防止とsystem監査、`allow` の期限後初回提出と再提出拒否、`deny` のEditor/participation拒否を確認した。
- 初回の認証browser操作では「保存・公開」を押しても画面遷移しなかった。原因は、割当の保存日時に秒が含まれる一方で `datetime-local` の既定 `step=60` が分単位であり、`form.checkValidity()` がfalseだったこと。公開/期限日時入力へ `step=1` を指定して秒精度を保持し、fresh assetで再検証した結果、フォームがvalidになり、共通確認ダイアログを経て保存・公開できた。画面の完了通知と一覧の「公開予約」を確認し、専用DBでもtask `published` / assignment `scheduled` / `late_submission_policy=allow` と保存日時を確認した。
- 配信された `task.js` (17,781 characters) をfresh fetchし、ブラウザーJavaScriptエンジンの `new Function(source)` でparser検証した。`step=1` の設定が公開/期限の2入力にあることも確認。`git diff --check` 成功。
- 未確認: 実workerの起動直後/60秒周期の実時間動作と再起動後回収、期限境界、既存提出/評価/ログ履歴の不変性。予約日時が未来であったため、browser操作自体は予約状態への遷移を受け入れ、workerの時間経過後遷移はDB統合テストで確認した。
- 検証後、専用MySQLの `log_bin_trust_function_creators` を `OFF` に復元して確認し、隔離アプリ/runner/DB、専用Gradle/MySQL volume、専用networkのみをCompose project単位で停止・削除した。共有Compose project/volumeは削除対象にしていない。

## 2026-10-06: S3学校所属・削除/復元の最終受入

- 初回の教師課題browser受入でJSP ELが `AuthenticatedUser` recordの `userId` をJavaBeans propertyとして解決できず、responseが途中で失敗した（`PropertyNotFoundException`、`ERR_INCOMPLETE_CHUNKED_ENCODING`）。`TeacherTaskServlet`から数値の `teacherTaskUserId` request attributeを渡し、JSPの所有者判定をその属性へ変更した。修正後の認証browserで課題一覧全体が描画され、削除・削除済み一覧・下書き復元・通常一覧への再表示まで成功した。
- 合成デモクラスの保存名を「1年A組」とした初回表示では、学年名を画面側で付加するため「1年1年A組」と重複した。seed値を「A組」へ修正し、既存の専用デモDB行も同じ合成クラス1件に限定して更新した。再ログイン後の画面は「1年A組」と表示された。
- アプリ稼働中のGradle testは共有Gradle cache journal lock timeoutとなった。`app`を停止して同じDB統合テストを再実行し、成功した。強制Javaコンパイルも `gradle compileJava --rerun-tasks --no-daemon` で成功した。
- 空の使い捨てDBとプロジェクト専用DBのV1〜V19 migration、合成demo seed、教師ログインと通常/削除済み一覧のbrowser受入が成功。プロジェクト専用DBのみを作り直し、volumeや他DBは削除していない。検証後に使い捨てDBを削除し、MySQL `log_bin_trust_function_creators` を0に戻した。デモパスワードはこの記録に含めない。
- **次**: S3は完了。S4配信の要件・状態遷移・受入条件を既存正本と照合し、仕様判断が必要なら実装前に確認する。画面遷移図/prototypeは必要性が明確でない限り変更しない。

## 2026-10-06: 課題公開実装の初回コンパイル失敗

- 実行時刻: 2026-10-06 06:09 JST。対象: 教師課題の公開処理を追加した後のfocused JUnit command `docker compose exec -T app gradle test --tests 'control.teacher.TeacherTaskControlTest' --tests 'control.teacher.TeacherTaskInputValidatorTest' --tests 'dao.TeacherTaskDaoTest' --tests 'servlet.teacher.TeacherTaskFormTest' --tests 'servlet.teacher.TeacherTaskServletTest' --tests 'entity.StudentEditorPageTest' --no-daemon --console=plain --warning-mode all`。
- 期待結果: Javaコンパイル後に指定された単体テストが実行される。
- 実際の結果: `TeacherTaskDao.java`で `PublicationResult` recordの挿入位置が `findSuccessfulTaskRequest` のtryブロック内になり、Java compilerが `illegal start of expression` 等でcompileJavaを停止した。テストは未実行。Gradle 8.10.2の既存WarPluginConvention deprecation warningも出た。
- 影響範囲: 今回編集中の課題公開実装のみ。DBコマンド・migration・アプリ再起動・外部API呼び出しは行っていない。
- 対応: recordをDAOクラス直下のnested recordとして移動し、該当する波括弧を修正した。
- 再検証: 同じfocused commandを再実行し `BUILD SUCCESSFUL`。compileJava/compileTestJavaと6つの指定test selectorを実行し、失敗なし。DBを使うテストselectorは含めていない。
- 未解決事項: 同じ既存Gradle deprecation warningが出るが、今回変更の原因ではない。

## 2026-10-06: JavaScript構文検証ツール未導入

- 実行時刻: 2026-10-06 06:16 JST。対象: 課題公開確認処理を追加した `src/main/webapp/js/teacher/task/task.js`。
- 期待結果: JavaScript parserが構文を検証する。
- 実際の結果: `node --check ...` と代替の `nodejs --check ...` はいずれも `command not found` で実行できなかった。package manifestを変更していないためNode.jsを追加導入していない。
- 影響範囲: JavaScript単独の構文検証のみ未確認。Gradle側ではJava/JSP関連のfocused testが成功した。
- 対応・未解決事項: submitter分岐、shared feedback確認後の `requestSubmit`、保存/公開ボタン状態をソース上で確認した。利用可能なJavaScript parserを使った自動構文確認は保留。

- 追補: 公開workerのDAO状態遷移は親課題を先にロックし、その後に割当をロックする順に統一した。`docker compose exec -T app gradle test --tests control.teacher.TeacherTaskPublicationWorkerTest --warning-mode all --no-daemon --console=plain` は `BUILD SUCCESSFUL`。`git diff --check` も成功。最初に誤って存在しない `dao.TeacherTaskPublicationDaoTest` をselector指定した実行は `No tests found` となったため、DAOの専用DB試験が成功したとは扱わない。専用DB・browser受入は未実施。

## 2026-10-06: T014 再評価履歴表示・隔離受入

- `TeacherPromptDao`/`TeacherPromptControl`から教師所有課題の再評価job履歴をページへ接続し、実行日時・実行者・prompt版・状態・成功/失敗/対象数・進捗を表示。job詳細には対象者ごとの提出revision・状態・安全な失敗理由・総合点・思考/態度点を追加した。画面遷移図/prototypeは変更していない。
- 初回の使い捨てDB testは成功したが、synthetic completed jobが集計4/4に対して詳細targetを1件しか保持しない不整合をブラウザーで発見した。fixture helperが`target_count`を固定4としていたため、jobごとにtarget数を渡せるよう修正し、completed jobを1/1とした。修正途中のDB testはこの固定値が原因で1件失敗し、helperとassertionを一致させて再実行後に成功した。
- 新規tmpfs MySQL `ppe_teacher_task_test_t014` にV1〜V18を適用し、`flywayMigrate flywayValidate`成功。`TEACHER_TASK_DB_TEST=true`とT014専用fixture保持flagを有効にした `gradle test --tests control.teacher.TeacherTaskDatabaseTest.reevaluationJobStatusIsScopedToTeacherAndTaskAndReportsProgress --no-daemon --console=plain` は1 passed / 0 failed / 0 skipped。別の全体 `gradle test war --no-daemon --console=plain` はJUnit 204件、135 passed / 0 failed / 69 skipped、WAR生成成功。Java 21を使用。既存Gradle deprecation warningあり。
- 隔離Tomcatをport 18083、専用schemaだけへ接続して認証browser受入。`/teacher/account/login`はHTTP 200、ログイン後に`/teacher/prompt?taskId=3&promptVersionId=3&jobId=11`を表示。履歴にqueued / in-progress / completed / failedの4件を確認し、completedは1/1・失敗0・進捗100%。「結果を見る」から同job詳細へ遷移し、合成対象の提出版1、完了、総合3.500、思考4.000/5、態度3.000/5を確認。synthetic fixtureのみで実提出は使用していない。
- 検証後、隔離Tomcat/MySQL、専用network/Gradle cache volumeを削除し、browserを`about:blank`へ戻した。共有8080/DB、既存container、Gemini実API、実提出、個人情報には触れていない。
- **次**: T014は完了。S3「公開・改訂・削除/復元」の状態・権限・画面導線を既存仕様と照合し、最初の機能スライスと受入条件を定める。S4配信はS3と分離する。

## 2026-10-05: T030境界テスト・T031 job status受入

- `TeacherPromptServletTest`に教師role、不正`jobId`、`taskId`欠落のGET拒否を追加。`TeacherTaskDatabaseTest`にqueued / in_progress / completed / failed status、completed/failed count、progress、別task/job/owner拒否を追加し、fixture cleanupもjob target/participant/submissionを含めて拡張した。
- 新規tmpfs MySQL containerに`ppe_teacher_task_test_t030` / `ppe_evaluation_test_t030`を作り、両schemaでV1〜V18の`gradle flywayMigrate flywayValidate --no-daemon --console=plain`が成功。DB testsはTeacherTask 13件成功 / 0失敗 / 0 skip、Evaluation 6件成功 / 0失敗 / 1 skip（Gemini live smokeはopt-inせず）。
- 最初の認証HTTP表示でJSP ELがrecordの`status`を解決できず500になる不具合を発見。`ReevaluationJobStatus`へJSP EL用JavaBean getterを追加し、`Introspector`でstatus/progress各プロパティの認識を確かめる回帰testを作成。再実行したV18 DB tests・全suite/WARは成功。
- 新規tmpfs MySQL schema `ppe_teacher_ui_t031` と別の現行source appを使い、合成教師・課題・prompt・queued / in_progress / completed jobだけを投入。統合ブラウザーでsynthetic teacher login後、status表示（job ID、成功/対象数、失敗数、progress）を確認。visible pageでqueued/in_progressは各4.2秒内に2回のGET poll、completedは同時間内0回。`jobId`不正と`taskId`欠落はともにHTTP 400。preview生成・確定POSTは行っていない。
- 最終`gradle test war --no-daemon --console=plain`成功。JUnit 202件、134 passed / 0 failed / 68 skipped、WAR生成成功。Java 21 toolchainがある隔離app containerから実行。既存Gradle deprecation warningあり。
- 検証後、専用app/MySQL containerとbrowser用networkを削除し、ブラウザーを`about:blank`へ戻した。retained port 18081 appは停止せず、共有8080/DBも変更なし。Gemini実API、実提出、実個人情報は使用していない。
- **T031残件**: preview予測結果の画面確認および確定redirectでjob IDが保持されることは未確認。production servletのpreview controlはGemini clientを直接構成し、隔離Tomcatへのfake provider注入経路がないため、実APIを呼ばずに確認するテスト設計は未決。安全なtest seamを追加するか、preview/confirm browser受入を未対応として残すかを決めてから進める。screen-flow図/prototypeは変更なし。

## 2026-10-05: T031 preview表示・確定redirect受入完了

- `ReevaluationPreviewWorker`にrepository/providerを必須とするpublic constructorを設け、合成providerを注入してpreview生成からjob確定までを使い捨てV18 DBで検証する統合testを追加した。fixture保持は`ppe_teacher_task_test_t031`専用の明示設定時だけに限定し、通常はcleanupする。JSP ELがrecord accessorだけでは読めないpreview/targetにJavaBean gettersを追加し、bean introspection回帰testを追加した。
- 専用schema `ppe_teacher_task_test_t031`とcurrent sourceの隔離Tomcat/port 18082で認証browser受入。`/teacher/prompt?taskId=3&promptVersionId=3&previewCode=<synthetic-preview>`を開き、preview target 1人、思考4/5・態度3/5、合成理由が500/JSP errorなく描画された。確認modalの「実行する」で確定し、redirect URL `/teacher/prompt?taskId=3&promptVersionId=3&jobId=9`を確認。再評価jobは完了、成功1/1、失敗0。DBの保存済みprovider responseをmaterializeし、workerによるGemini再呼出しはない。
- 最新全体コマンド `docker exec -w /programming-process-evaluator -e GRADLE_USER_HOME=/tmp/gradle-t031 -e GRADLE_RO_DEP_CACHE=/home/gradle/.gradle/caches -e DB_HOST=ppe-t031-preview-db-20261005 -e DB_PORT=3306 -e DB_NAME=ppe_teacher_task_test_t031 -e DB_USER=root -e TEACHER_TASK_DB_TEST=true -e T031_RETAIN_PREVIEW_FIXTURE=false ppe-t031-preview-app-20261005 gradle test war --no-daemon --console=plain` は成功。JUnit 204件、149 passed / 0 failed / 55 skipped、WAR生成成功。Gemini live smokeは実行していない。
- 初回のhost直接実行 `GRADLE_USER_HOME=/tmp/gradle-t031 GRADLE_RO_DEP_CACHE=/Users/t.toida/programming-process-evaluator/.gradle/caches gradle test war --no-daemon --console=plain` はhostにJava 21 toolchainがなく失敗し、指定したread-only cache pathも存在しなかった。Java 21の隔離containerへ移行した最初の実行は、同じprojectの稼働中`appRun`がGradle `buildOutputCleanup` lockを保持してtimeoutした。一時Tomcatを停止してから同コマンドを再実行し成功した。
- browserの初回routeアクセスはTomcat deployment準備前にHTTP 404となった。ready後のlogin route HTTP 200、匿名prompt route HTTP 302を確認してから認証browser受入を行い、preview表示・confirm redirect/job statusは成功した。初回404は起動待ちのタイミングによるもので、ready後の再試行で解消した。
- 検証終了後、T031専用app/DB containerとbrowser専用networkを削除し、browserを`about:blank`へ戻した。共有8080/DB、既存port 18081 app、Gemini実API、実提出、実個人情報、screen-flow diagram/prototypeには触れていない。
- **残件**: T031の認証HTTP/browser・preview/confirm受入とT019 test coverageは完了。T014の再評価job/個別評価結果履歴表示が未実装。S3/S4はT014の残件を整理するまで保留する。

## 2026-10-05: T017/T018 全体再評価実装・専用検証

- 対象: 専用MySQL `ppe-reevaluation-isolated-mysql` のschema `ppe_evaluation_test_reeval01` と新規schema `ppe_teacher_task_test_reeval01`。認証情報は専用container環境からプロセス環境へ渡し、値をログ/出力しない。共有DB/8080および画面遷移prototypeは変更していない。
- 初回Java compile失敗: `gradle test war --no-daemon --console=plain`で`ReevaluationPreviewControl.java:142`以降に構文エラー。`loadJobStatus`が`loadPreview`のtry/catch内へ誤配置されていた。クラスメソッド位置へ移し、rollback時に元の例外を再throwするよう修正。
- 初回test compile失敗: `TeacherTaskDatabaseTest.java:165`で`initialPage`がスコープ外だった。初期prompt版なしassertionを宣言元のテストへ移した。
- 初回TeacherTask DB試験失敗: `TeacherTaskDao.findTasks`のSQLが`tasks`をalias `t`なしで参照する一方、再利用predicateは` t.publication_status`を参照し、9 testがSQLSyntaxErrorException。`FROM tasks t`と条件/ORDER BYの列修飾を揃えた。公開課題に3版作成されるテストの期待値（`v3`、3 versions）も実データ履歴に合わせて修正した。再実行は12 passed / 0 failed / 0 skipped。
- 専用DB migration: `gradle flywayMigrate flywayValidate --no-daemon --console=plain`を`ppe_teacher_task_test_reeval01`へ実行し成功。V1〜V18適用後、同schemaでTeacherTask DB testsを実行。
- 評価履歴DB test: `gradle test --tests control.evaluation.EvaluationDatabaseTest --rerun-tasks --no-daemon --console=plain`を`EVALUATION_DB_TEST=true`、`ppe_evaluation_test_reeval01`で実行し6 passed / 0 failed / 1 skipped。skipは明示opt-inが必要な課金Gemini live smoke test。確定済みpreview responseをjob target claim経由でmaterializeし、providerを再呼出しせず旧評価を保持するDB testは成功。
- 全体検証: `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test war --no-daemon --console=plain`成功。全JUnit 198件、131 passed / 0 failed / 67 skipped、WAR生成成功。Gradleの既存Gradle 9互換性deprecation warningあり。
- 追加の非成功試行: JDK 21を環境変数へ渡さず起動したDB testはtoolchain不在でGradleが開始できず、JAVA_HOME設定後に再実行成功。最初のMySQL CLI接続probeはpassword未指定でerror 1045となったが、credential値を出力しない接続方法へ切り替え、schemaの準備とDB testsを完了した。
- 未確認: 新job statusを含む認証HTTP/ブラウザー受入、実Gemini API、共有8080での稼働。`node --check src/main/webapp/js/teacher/prompt/prompt.js`はNode.js不在（`node: command not found`）で実施できていない。mock/専用DB結果を外部APIや共有環境の成功とは扱わない。

## 2026-10-05: 教師プロンプトS2の専用DB受入追補

- 対象: 専用Compose project `ppe-s2-validation-20261005`、schema `ppe_teacher_task_test_s2_20261005` のみ。共有開発DBと8080サービスには変更を加えていない。
- migration初回失敗: V11のroutine作成がMySQL error 1419（binary loggingとroutine creator設定）で失敗した。専用DBコンテナだけに`SET GLOBAL log_bin_trust_function_creators = 1`を設定して再試行したところ、最初の失敗で既に作られた`chk_school_security_level`との重複（error 3822）が発生した。Flyway repairだけでは部分適用DDLを戻せないため、専用Compose projectのDB volumeだけを破棄・再作成し、同じ専用コンテナ内で設定後にやり直した。
- migration再検証: `DB_PORT=3307 DB_NAME=ppe_teacher_task_test_s2_20261005 docker compose -p ppe-s2-validation-20261005 run --rm --no-deps app gradle flywayMigrate flywayValidate --no-daemon --console=plain`成功。
- DBテストの誤った初回実行: `TEACHER_TASK_DB_TEST=true`をCompose呼出し元だけに設定した実行は、コンテナへ環境変数が渡らず11件全てskipとなった。`-e TEACHER_TASK_DB_TEST=true`と`--rerun-tasks`を追加して専用DBのfixtureを実際に実行した。
- 実DBで見つかった不具合: `TeacherTaskDao.insertDraft`のtask INSERT列/value位置がずれ、prepared statementでparameter 12未設定となった。SQL内の固定Python値の位置を修正し、登録パラメーターと列を一致させた。
- fixture cleanupで見つかった不具合: prompt版を持つ課題の削除時、`prompt_versions`のFKにより親taskを削除できなかった。専用合成fixtureのprompt監査・評価例・揺らぎ項目を先に削除し、active版参照を解除してからprompt版とtaskを削除するようcleanupを修正した。加えて、新規DBテストに誤って含まれていた別テストのdraft-update assertionsが未定義の`saved`変数を参照していたため、重複する無関係なassertionsを削除した。
- 専用DB受入: `DB_PORT=3307 DB_NAME=ppe_teacher_task_test_s2_20261005 docker compose -p ppe-s2-validation-20261005 run --rm --no-deps -e TEACHER_TASK_DB_TEST=true app gradle test --tests control.teacher.TeacherTaskDatabaseTest --rerun-tasks --no-daemon --console=plain`成功。11件実行、成功11、失敗0、skip 0。V17を含むmigrations適用済みDBでprompt draft/rubric/楽観version競合を含めたfixtureを検証した。
- 全体再検証: `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test war --no-daemon --console=plain`成功。JUnit合計190件（成功125、失敗0、skip65）、WAR生成成功。専用DB統合テスト11件は別コマンドでskipなし実行。Gradle 9互換性に関する既存deprecation warningは残る。
- 追加した`TeacherPromptServletTest`の未認証GET/POST拒否テストを含めて再実行: 同じGradle test/WARコマンド成功。最新JUnit XML合計192件（成功127、失敗0、skip65）、WAR build task成功。HTTP/JSPの認証済み表示・ブラウザー操作を確認した結果ではない。
- 未確認/blocked: 認証済みHTTP/JSP・ブラウザー操作、Gemini実API、8080への反映は未実施。共有開発DBにV17を適用せずappを再起動していないため、8080がS2実装を提供しているとは扱わない。再評価対象固定・差分preview・通知チャネルの既存契約がないため、全体再評価操作は画面上で無効のまま。プロトタイプ資産は変更していない。

## 2026-10-05: 教師プロンプトS2の隔離ブラウザー受入

- 対象: 新規Compose project `ppe-s2-ui-acceptance-20261005`、schema `ppe_teacher_ui_acceptance_20261005`、HTTP port `18081`。既存の`ppe-s2-validation-20261005`と8080共有開発DBは停止・変更せず、テスト専用の合成教師/課題だけを使った。
- 環境: `docker info`成功、Node.js CLIは利用不可。Node依存へ切り替えず、VS Code統合ブラウザーで画面操作した。Gemini実APIは呼び出していない。
- DB準備: 新規DBへの最初の`flywayValidate`はV1〜V17 pendingを理由に失敗したため、`gradle flywayMigrate`後に`gradle flywayValidate`を実行し成功。V1〜V17適用済みであることを確認し、承認済み共通標準rubric `0805-2026-v1`を専用DBへ登録した。
- 画面受入で発見した不具合: taskを選択しても共通prompt欄がreadonly、保存ボタンがdisabledだった。`TeacherPromptServlet.render`は`editableDraft`を計算していたが、JSPが参照する`teacherPromptEditableDraft` request attributeを設定していなかった。属性設定を追加し、初回draft作成可能状態の条件を`isEditableDraft`へまとめてJUnit回帰テストを追加した。JSP/CSS/JS・画面遷移図・プロトタイプのデザイン/機能変更はしていない。
- ブラウザー結果: 合成教師としてログイン成功。`/teacher/prompt`の未選択初期状態、合成draft選択、共通rubric表示、履歴空状態、教師ナビの課題編集/プロンプト設計リンクと`aria-current="page"`を確認。prompt入力・下書き保存が有効になり、保存後は`taskId=1&promptVersionId=1`へredirectした。再読込後もversion `v1`と保存内容が表示された。
- DB結果: `prompt_versions`にversion `v1`、状態`draft`、row_version `1`、合成promptが保存された。保存前後で課題の`active_prompt_version_id`はNULLのまま、`evaluations`件数は0のままで、保存操作がactive版切替や評価履歴作成をしていないことを確認。評価用標準rubricは画面に表示された。
- 境界/UI結果: 他教師所有taskへの認証済みGETはHTTP 404。匿名`GET /teacher/prompt`はHTTP 302で教師loginへ遷移し、loginページは200。prompt/sharedのローカルCSS/JS 7資産はすべてHTTP 200。再評価ボタンはdisabledのまま。1280px desktopと390px narrow viewportでdocument/body横幅のoverflowなし。学生拒否は既存`TeacherPromptControlTest.rejectsNonTeacherBeforeOpeningDatabaseConnection`で確認。
- 最終ビルド: `DB_PORT=3308 DB_NAME=ppe_teacher_ui_acceptance_20261005 APP_PORT=18081 PROJECT_NAME=programming-process-evaluator docker compose -p ppe-s2-ui-acceptance-20261005 run --rm --no-deps app gradle clean test war --no-daemon --console=plain`成功。JUnit XMLは194件、成功129、失敗0、skip65。WAR生成成功。DB-gatedテスト65件はこの全体実行ではskipされ、既存の別専用DBで実行した11件成功のDB統合結果をこの実行件数へ混ぜていない。
- 判定/次: 今回対象の認証後S2画面・下書き保存受入はPASS。共有8080はこの受入環境/修正を反映していない。差分previewの算出元、再評価snapshot、初回/対象ゼロ件、通知契約は未決のままなのでT017/T018はblockedを維持し、合意なしに再評価/S3/S4へ進まない。

## 2026-10-05: 次回AutoPilot向け再評価契約の事前照合

- 対象: S2全体再評価T017/T018の既存仕様・状態ルール・実装schemaの読み合わせ。実装、migration、画面/プロトタイプ変更、共有DB操作は行っていない。
- 既存合意: IC-004/IC-007と教師prompt状態ルールにより、再評価の明示確定時にactive promptを切替えjobを作成し、処理中の通常評価は開始時版を保持する。再評価結果は同じ提出に対する別の`evaluations`行へ追記し、旧評価は保持する。対象は各生徒の最新提出、教師権限は現在の学校権限を再検証する。
- 実装契約の欠落: `reevaluation_jobs`は集約状態/件数列のみで固定対象リストを持たず、`evaluation_input_snapshots`は個々のevaluation requestに結び付く。previewの算出式、提出なし生徒の扱い、初回active版切替、対象ゼロ件の挙動は既存文書から一意に定まらない。
- 通知の不整合: 機能仕様書は再評価完了時の各生徒通知を要求するが、共通ヘッダー仕様は永続通知一覧を置かず非操作アイコンとし、教師引継ぎも通知一覧追加を禁止。実装/schemaに別配送チャネルは見つからない。画面feedbackを配送通知の代用にしない。
- 次回AutoPilotの境界: 上記6点（preview、対象集合、snapshot、初回適用、対象ゼロ件、通知）を一つずつユーザー確認し、関連仕様を同期する契約レビューのみ。コード/schema/画面変更は回答に基づく別バッチの計画を示してから着手する。未合意項目があればT017/T018はblockedのままとする。
- 2026-10-05ユーザー確認: previewには対象生徒ごとの新評価予測を含める。機能仕様第114版、プロンプト状態ルール、実装契約IC-007、本計画へ反映した。予測生成方式（実提出を確定前にAI評価するか、合成評価例等で予測するか）は未決であり、合意前のAI呼出し・実提出送信はしない。
- 2026-10-05ユーザー確認: 予測は確定前にGeminiで各対象生徒の最新提出を評価して生成し、教師が確定した場合は同じ応答を再評価結果に再利用する。キャンセルしてもAPI費用は発生するが、評価行・active版・jobは作成しない。既存AI評価の匿名化経路に従う。機能仕様第115版、状態ルール、実装契約IC-007、AI連携設計、本計画、ロードマップへ反映した。実API呼出しや実提出の送信はまだ行っていない。
- 2026-10-05ユーザー確認: 課題参加者全員をpreviewに表示し、最新提出がない参加者は「最新提出なし・対象外」と明示する。Gemini呼出し・再評価job・job対象件数から除外する。機能仕様第116版、状態ルール、実装契約IC-007、DBテーブル定義、本計画、ロードマップへ反映した。
- 2026-10-05ユーザー確認: preview生成時に対象者・最新提出・評価入力（prompt/rubric版含む）を固定し、確定時に変更があればpreviewを無効化して再生成を求める。再生成時はGemini呼出しに伴う追加費用が発生する。機能仕様第117版、状態ルール、実装契約IC-007、DBテーブル定義、本計画、ロードマップへ反映した。snapshotの永続化方式・保持期間は未決定。
- 2026-10-05ユーザー確認: snapshotとGemini応答は既存DBまたは専用の一時保存領域へ期限付きで保存し、確定・取消・期限切れ時に状態更新または削除する。機能仕様第118版、状態ルール、実装契約IC-007、DBテーブル定義、本計画、ロードマップへ反映した。保持時間・具体的な保存先/schemaは未決定。
- 2026-10-05ユーザー確認: preview snapshotとGemini応答の保持期限はpreview生成から30分。期限切れで削除し、確定不可とする。継続には新しいpreviewを生成する。機能仕様第119版、状態ルール、実装契約IC-007、DBテーブル定義、本計画、ロードマップへ反映した。保存先/schemaとcleanup方式は未設計。
- 既存schema照合: `evaluation_requests.evaluation_id`はNOT NULLであり、関連する`evaluation_responses`と`evaluation_input_snapshots`はevaluation requestを親とする。確定前にevaluation行を作らずpreviewを30分保持する契約を満たす保存方法は現行テーブルだけでは明確でないため、既存構造の変更かpreview専用の一時保存構造かを次に確認する。
- 2026-10-05ユーザー確認: 通常の評価request/response/snapshotを変更せず、確定前preview専用の一時保存構造を追加する。preview保存のために通常evaluation行を先行作成しない。機能仕様第120版、状態ルール、実装契約IC-007、DB定義、本計画、ロードマップへ反映した。具体的なテーブル/列と状態遷移は未設計。
- 2026-10-05ユーザー確認: 全対象者のGemini予測が成功するまでpreviewを確定不可とし、成功済み結果は30分保持、失敗者のみ再試行する。機能仕様第121版、状態ルール、実装契約IC-007、DB定義、本計画、ロードマップへ反映した。再試行上限は既存AI連携の失敗処理に沿って設計する。
- 2026-10-05ユーザー確認: 再評価対象が0人の場合はGemini呼出し・active版切替・評価履歴/job作成を行わず、「再評価対象なし」と表示する。機能仕様第122版、状態ルール、実装契約IC-007、DB定義、本計画、ロードマップへ反映した。
- 2026-10-05ユーザー確認: active promptが未設定でも最新提出対象者がいる場合は通常のpreview/確認/active設定/job作成フローを使い、新評価予測を表示する。旧prompt/評価なしは明示する。機能仕様第123版、状態ルール、実装契約IC-007、本計画、ロードマップへ反映した。
- 2026-10-05ユーザー確認: 生徒への永続通知は追加せず、教師にはjob完了を表示し、生徒には次回評価画面を開いたとき最新評価を表示する。機能仕様第124版、状態ルール、実装契約IC-007、本計画、ロードマップへ反映した。以前の通知要件と通知一覧を作らない方針の不整合は解消済み。
- 2026-10-05ユーザー確認: preview専用の一時保存をpreview単位の親レコード＋対象生徒ごとの子レコードに分離する。機能仕様第125版、状態ルール、実装契約IC-007、DB定義、本計画、ロードマップへ反映した。テーブル名は仮称で、列・制約・cleanup/状態遷移は実装計画で定義する。

## 2026-10-05: 全体再評価V18 schema案・T017/T018計画（実装前停止）

- V18案として`reevaluation_previews`、`reevaluation_preview_targets`、`reevaluation_job_targets`をテーブル定義へ提案し、Plan 5.3〜5.5とT021〜T031へschema、preview worker、原子的確定、評価materialization、cleanup、受入の分解を記載した。
- 本作業は文書計画のみ。migration/Java/JSP/JS/CSS、画面遷移図・prototype、共有8080/DBを変更せず、Gemini実API/実提出も使用していない。V18は未作成。T017/T018は業務契約未解決ではなく、ユーザー依頼が計画までであるため実装未着手としてblockedを維持する。
- 最新提出が`submitted`の間にpreview確定を止める扱いは未合意の実装提案として明示し、実装時に提出確定/lock処理と照合する。Docker daemonは利用可能、Node.js CLIは利用不可。Playwright導入は未試行で、今回の文書作業ではテスト/buildを実行していない。

## 2026-10-05 07:23 JST: 8080教師プロンプト画面の500/404

- 対象: `http://localhost:8080/teacher/prompt` の500、ブラウザーconsoleの404、および教師サイドバー表示。
- 500の原因: 課題/プロンプト版未選択時、`TeacherPromptServlet.render`のnullable `Long`と`long`を混在した条件式がnullをunboxしていた。また同じ初期表示状態で評価例保存可能状態を計算する際に選択版を参照していた。版未選択をnullのまま扱い、評価例操作は選択版が存在する場合のみ有効判定するよう修正。`TeacherPromptServletTest`へnull版ID回帰テストを追加。
- 404の原因/対応: 教師ナビの`aria-current`条件を`<a>`開始タグ途中へJSTL body出力していたため、属性断片が画面テキストへ漏れ、不正なリンクを生成していた。課題編集・プロンプト設計リンクともEL属性値へ変更し、`aria-current`がHTML属性内で完結するよう修正。
- DB/実行反映: 8080共有開発DBがV16であり、課題選択時に参照するプロンプト版の`row_version`等を含むV17がpendingだった。migration内容はprompt版の更新者/更新日時/楽観version列追加と、rubric未設定draftへのactive共通標準rubric関連付けのみであることを確認し、ユーザーの8080画面復旧依頼に必要なため`docker compose exec -T app gradle flywayMigrate --no-daemon --console=plain`で適用。適用後`docker compose exec -T app gradle flywayValidate --no-daemon --console=plain`成功。テーブル/課題を削除せず、プロトタイプには変更なし。
- 反映/検証: ソース修正後に8080 app containerだけを再起動。未認証`GET /teacher/prompt`は期待どおり302で教師ログインへ遷移し、プロンプトCSS/JSは各HTTP 200。再起動後直近ログに当該null-unboxing/JSP例外なし。`JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test war --no-daemon --console=plain`成功（JUnit XML: 192件、成功127、失敗0、skip65、WAR成功）。専用DB統合11件の成功は上記S2受入追補の通り。
- 制約: app再起動により既存のブラウザーsessionは無効になった。認証済みJSP操作を本ターン中に再ログインして確認していない。教師として再ログイン後、`/teacher/prompt`の初期表示と課題選択、ナビリンクを確認すること。Gemini APIは呼び出していない。全体再評価は契約未解決のため引き続き無効。

## 2026-10-05 07:35 JST: 8080再ログイン後受入の再試行

- 対象: 教師ログイン後の`/teacher/prompt`初期表示・課題選択・ナビ。
- 実施: 共有ブラウザーを`/teacher/prompt`へ遷移。未認証のためログイン画面へ移ることを確認。ユーザーへ共有ブラウザーでの再ログインを依頼したが応答がなく、以前通知したテスト資格情報をこの実行環境から取得できないため、認証情報を推測/再設定せず認証済み画面確認は保留。
- 代替runtime確認: `GET /teacher/account/login`=200、`GET /teacher/prompt`=302（教師ログインへ）、`GET /teacher/task`=302（教師ログインへ）、教師ログインCSS・prompt CSS・JSは全て200。開発DBのFlyway最新versionはV17 success。直近アプリログにpromptの500/JSP例外なし。
- 判定: 未認証経路と静的リソースはPASS。認証後JSP描画/課題選択/リンク操作は未検証であり、画面全体のruntime acceptanceは未完了。教師ログイン後に再確認する。

## 2026-10-05 00:39 JST: 教師プロンプトS2の実装・検証

- 対象: `TeacherPromptDao` / `TeacherPromptControl` / Gemini構造化出力 / `/teacher/prompt` とStep 1〜3/履歴画面。
- 初回の検証エラー: `JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test --tests dao.TeacherTaskDaoTest --tests control.teacher.TeacherTaskDatabaseTest` は`compileTestJava`で失敗。変更中の`TeacherTaskDatabaseTest`でrubric集計helperが別helper内に誤配置されていたため、独立メソッドへ移動した。
- 2回目の検証エラー: 同コマンドは`Files.readString`の検査例外を統合fixtureが宣言しておらず失敗。`IOException`をfixture setupから宣言するよう修正した。
- 再検証: 上記のfocusedテストと`JAVA_HOME=/Users/t.toida/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home gradle test war`が成功。JUnit XML合計189件（成功125、失敗0、skip64）、WAR生成成功。skipには専用DB条件で動く統合テストが含まれ、S2用DB統合は未実施。
- 外部API: Gemini実APIは呼び出していない。APIクライアントはモックテストのみ。
- 8080/DB: 8080のCompose app/DBは稼働中。未認証の`/teacher/prompt`は認証filterにより教師ログインへ転送された。V17未適用の共有開発DBにmigrationを適用したりappを再起動したりせず、認証済みJSP/DBの実行確認は行っていない。従って今回の変更が8080へデプロイ済みとは扱わない。
- 未解決: V17を適用する使い捨て専用DBでのmigration/DAO統合、認証済みHTTP/JSPの描画・操作、ブラウザーでのプロトタイプ差分確認。対象提出snapshot/差分preview/通知の契約がない再評価ボタンは成功動作へ接続せず無効化した。

## 2026-10-04 23:34 JST: 教師画面ルーブリック導線・文字コードのブラウザー確認

- 対象/期待結果: 8080の教師課題画面で、プロトタイプどおりルーブリックボタンを操作でき、日本語が正しく表示されること。
- 発見した問題と対応: デスクトップでも教師サイドバーに`z-index:1051`を指定していたため、`z-index:1049`の固定ルーブリックボタンが覆われていた。デスクトップのサイドバーを`z-index:auto`にし、オーバーレイを使うモバイル幅だけ`1051`を維持した。ルーブリックJSP fragmentにUTF-8宣言がなく、ボタン/見出しの日本語が文字化けしていたため`pageEncoding="UTF-8"`を追加した。画面遷移図・プロトタイプは変更していない。
- ブラウザー操作時の制約: 統合ブラウザー上の通常クリック/hoverは要素の安定待ちでtimeoutし、`page.keyboard.press('Escape')`でもナビが閉じなかった。調査ではページが`visibilityState=hidden`、`document.timeline.currentTime=0`で、CSS遷移が進まない状態だった。DOM経由のクリックではルーブリックが開き、閉じる操作でモーダルの`show`状態が解除された。スクリーンショットでUTF-8のルーブリック表示を確認し、Escapeの合成`keydown`でモバイルナビが閉じてトグルへフォーカスが戻ることも確認した。隠しタブ上のクリック/キー入力や遷移完了状態は実利用ブラウザーでの確認に代えられないため、手動の実キー操作は未確認。
- 関連回帰: `http://127.0.0.1:8080/teacher/task`でボタン位置のクリック対象がボタン内にあること、教師ログインでパスワード表示切替が`password`→`text`→`password`となることを確認。
- 再検証: `docker compose exec -T app gradle test war --rerun-tasks --no-daemon --console=plain --warning-mode all`成功。`git diff --check`成功。未解決の実装エラーは確認されていない。実利用ブラウザーでの手動確認は未実施。

## 2026-10-04: 教師課題T010のURLクエリ成功通知

- 対象: 課題下書き保存後のPRG完了通知。
- 発見した問題: `?notice=saved`というクライアント指定可能なqueryだけでJSPが成功表示していたため、実保存のない直接GETでも成功メッセージを表示できた。
- 対応: 保存成功後にServletがsessionへ一回限りの通知を置き、ページデータ読込後にServletが取り出して削除した場合だけ画面へ渡す。成功判定にURL queryを使わない。通知を一回だけ消費するJUnitを追加。
- 再検証: `docker compose exec -T app gradle compileJava test --tests 'servlet.teacher.TeacherTaskServletTest' --no-daemon --console=plain --warning-mode all`成功（2件）。`docker compose exec -T app gradle test war --rerun-tasks --no-daemon --console=plain --warning-mode all`のJUnit XMLは合計165件（成功111、失敗0、skip54）。
- 影響/未解決事項: 誤成功表示は修正済み。認証済みHTTP保存/再読込とsession/DB統合はこの時点では未検証だったが、後続T014で使い捨て専用DBを用いて一部確認した。T014の残受入は未完了。

## 2026-10-04: 教師ナビ・課題画面のHTTP/JSP受入

- 対象: T011〜T014の教師ホーム、`/teacher/task`、共通テンプレートと使い捨てDBのブラウザー受入。
- 発見した問題: `TeacherTaskServlet`が認証Filterの設定する`authenticatedUser`ではなく別の属性キーを参照していたため、認証済み課題画面が403になった。ナビJSP fragmentにはUTF-8 page encodingがなく日本語が文字化けし、親JSPで宣言済みのJSTL taglibを重複宣言していた。エラー再表示時は未選択の`Long`値をprimitiveへunboxしてNPEが発生した。
- 追加発見: 複数クラスとヒントを含む更新を実DBで試すと、`TeacherTaskDao.bindHint`がINSERTのparameter 6を設定しておらず、ヒント追加時に`No value specified for parameter 6`で保存失敗した。
- 対応: Filterと同じrequest属性キーを使用し、fragmentのUTF-8を明示、重複taglib宣言を削除、未選択値をnull-safeに扱う。モバイルナビの閉じ操作ではフォーカスをトグルへ戻す。ヒントINSERTはtask IDと5つのヒント値を正しいparameter位置へbindするよう修正した。
- 再検証: 使い捨て専用MySQLにV1〜V16を適用し、専用DB統合テスト8件と全JUnit/WAR（合計178件、成功124、失敗0、skip54）を実行。複数クラス/子項目更新、同version同時更新、子ID所属エラーとMySQL障害時のtransaction rollback、学生DAOの未公開割当拒否、student/adminの作成拒否、別教師所有課題/未許可校割当拒否を確認。実ブラウザーでログイン、教師ホーム、課題JSP、保存/PRG/再読込、一回限り通知、モバイルナビを確認。検証用MySQL/containerは削除し、共有DBはversion 15のまま。
- HTTP smoke: 未認証GET `/student/home`は302で`/student/account/login`へ、`/admin/schools`と`/teacher/home`は302で`/teacher/account/login`へ転送し、`/student/account/login`は200を返した。認証ガードの確認に限られ、認証済み既存画面の回帰確認ではない。
- 追加の環境制約: 共有MySQLではV11のstored routine作成権限がなくmigrationが失敗したため、DB限定権限の変更も行わず、localhostのみに公開した使い捨てMySQLで検証した。
- 当時点の未解決事項: T014の全endpoint/action認可マトリクスと認証済み既存画面のHTTP/ブラウザー回帰は未完了。後続のT014完了記録に最終結果とS1外の未確認事項を記載した。

## 2026-10-04: 教師課題T014受入マトリクスと既存画面回帰

- 対象: 工程10 S1の教師課題認可/状態/rollbackと、教師・管理者・生徒画面の認証済み回帰。
- 対応/再検証: 合成ユーザー/学校/クラスだけを使う専用MySQLにV1〜V16を適用しvalidate。追加DB受入10件が成功し、全JUnit/WARも成功（180件、成功126、失敗0、skip54）。停止教師、機能/学校権限の無効化、別教師による課題/監査参照、複数校をまたぐ偽造割当、偽造task/child ID、同version競合、transaction rollbackを確認した。
- 認証HTTP: 教師ホーム/課題編集、管理者ホーム/学校管理、生徒ホーム/既存公開課題エディター/標準ルーブリック/授業演習を確認。adminと生徒の教師画面GET/POSTは403、存在しない課題/editorは404、存在しないsurveyは403。実ブラウザーで教師ホーム/課題編集、生徒ホーム/エディター、幅390pxの教師ナビを確認した。
- テストDBの初回migrationはMySQL binary logging下のroutine作成設定によりV11で失敗した。設定変更は当該使い捨てMySQLだけに限定し、専用schemaを作り直した後V1〜V16のmigration/validateと受入を再実行して成功。専用DB/volume/containerを削除し、共有DBはversion 15のまま、V16未適用。
- 未確認/対象外: 有効な評価・アンケートfixtureは作成していないため、生徒の提出→評価→アンケートの正式E2Eは未確認のまま工程8に保持する。54件のskipは受入根拠に含めない。
- 詳細: [T014/T015バッチレポート](./feature-plans/checkpoints/teacher-task-draft/batch-report-T014-T015.yaml)。

## 2026-10-04: 8080で教師課題画面を確認するための開発環境更新

- ユーザー依頼に基づき、`programming_process_evaluator`開発DBへFlyway V16を適用し`flywayValidate`を実行。既存データを削除・初期化する操作は行っていない。
- 認証用の合成テスト教師と、専用の合成学校/クラス、`task-management`機能権限を追加した。パスワードはPBKDF2-SHA256形式のハッシュのみDBに保存。資格情報は会話でユーザーへ案内し、文書/ソースには保存しない。
- 初回seedでMySQL clientへUTF-8を明示しなかったため合成表示名が文字化けした。`--default-character-set=utf8mb4`を指定して合成ユーザー/学校/クラス名を修正し、DB接続後のUTF-8表示を再確認した。
- 8080のTomcatを再起動して作業ツリーの最新コードを読み込み、合成教師のログイン後に`/teacher/home`と`/teacher/task`がHTTP 200、課題フォーム/新規作成領域/合成学校名が表示されることをブラウザーとHTTPで確認した。
- 認証情報を受け取った利用者が作る課題下書きは開発DBに保存される。不要になったアカウント/合成学校データの削除は、ユーザーの確認後に対象IDを限定して行う。

## 2026-10-04: 教師課題T009の初回production compile失敗

- 対象/操作: T009のServletへJSP表示用フォーム状態を追加後、`docker compose exec -T app gradle compileJava --rerun-tasks --no-daemon --console=plain --warning-mode all`を実行。
- 実際の結果: `TeacherTaskServlet`から`TeacherTaskInput`を参照するimportが不足しcompile失敗。
- 対応/再検証: entity importを追加。`docker compose exec -T app gradle compileJava test --tests 'servlet.teacher.TeacherTaskFormTest' --tests 'control.teacher.TeacherTaskInputValidatorTest' --tests 'control.teacher.TeacherTaskCreateRegistryTest' --tests 'control.teacher.TeacherTaskControlTest' --tests 'dao.TeacherTaskDaoTest' --tests 'dao.TeacherPermissionDaoTest' --tests 'entity.TeacherTaskInputTest' --no-daemon --console=plain --warning-mode all`成功。その後、JUnit XML合計163件（成功109、失敗0、skip54）で全テストとWAR生成が成功。
- 影響/未解決事項: 修正済み。Gradle WAR taskはJSPをコンパイルしないため、認証済みHTTPでのJSP描画はこの時点では未確認だったが、後続T014で実環境のJSP描画を確認した。

## 2026-10-04: 教師課題T007/T008の実装中コンパイル・テスト失敗

- 対象: 課題入力検証/作成token registry/ControlおよびServlet/form parserの追加時の局所コンパイル・JUnit確認。
- 初回結果: `TeacherTaskControl`でローカル変数名が重複してproduction compileが失敗。Registryテストでは割込み例外の宣言と成功IDの期待値が実装契約に一致せず失敗。Servlet追加後はフォームbody上限例外の可視性が不足しcompileが失敗した。
- 対応: 重複ローカル名を整理し、Registryテストを実際のchecked-exception/成功ID契約に合わせ、Servletから必要な例外型を参照できる可視性へ変更した。
- 再検証: `docker compose exec -T app gradle test --tests 'servlet.teacher.TeacherTaskFormTest' --tests 'control.teacher.TeacherTaskInputValidatorTest' --tests 'control.teacher.TeacherTaskCreateRegistryTest' --tests 'control.teacher.TeacherTaskControlTest' --tests 'dao.TeacherTaskDaoTest' --tests 'dao.TeacherPermissionDaoTest' --tests 'entity.TeacherTaskInputTest' --no-daemon --console=plain --warning-mode all` 成功（27件、失敗0、skip 0）。`docker compose exec -T app gradle compileJava --rerun-tasks --no-daemon --console=plain --warning-mode all`と`git diff --check`も成功。
- 影響/未解決事項: 失敗はいずれも修正済み。専用教師DB未準備につきV16、MySQL DAO/認可/監査統合、Servlet-JSP認証HTTPは未検証で、T009/T013/T014に残す。

## 2026-10-04: 教師課題T005のDAO単体テスト初回失敗

- 対象/コマンド: `TeacherTaskDaoTest`追加後に `docker compose exec -T app gradle test --tests 'dao.TeacherTaskDaoTest' --tests 'dao.TeacherPermissionDaoTest' --tests 'entity.TeacherTaskInputTest' --no-daemon --console=plain --warning-mode all` を実行。
- 実際の結果: 初回はテスト用JDBC proxyが空の機能リスト保存時の`executeBatch()`を扱えず1件失敗。空リストではDB更新が不要なためDAOを早期returnに変更した後、SQL検査の改行差分を含む未正規化文字列比較が1件失敗。
- 対応/再検証: 空の機能リストでbatch SQLを発行しないよう修正し、INSERT SQL assertion前に空白を正規化。上記Gradleコマンドは10件成功、失敗0、skip 0。強制production compileと`git diff --check`も成功。
- 影響/未解決事項: 共有DBには接続せず、データ変更なし。これはテストfixture/assertionの失敗として解消。実SQL・ロック・V16は専用教師合成DBが未準備のため未確認で、T013/T014に残す。

## 2026-10-04 18:41 JST以降: 教師課題T003/T004のテストツール検出

- 対象/操作: T003/T004の新規Java/JUnitテストを`runTests`ツールで指定ファイル実行。期待結果は対象テストの発見・実行。
- 実際の結果: `No tests found in the files. Ensure the correct absolute paths are passed to the tool.`。テスト内容の失敗ではなく、この実行環境がGradle Javaテストを検出できなかった。
- 影響: 初回テスト呼出しのみ。ソース/DB/APIへの変更なし。
- 対応/再検証: リポジトリ標準のGradle runnerで `docker compose exec -T app gradle test --tests 'entity.TeacherTaskInputTest' --tests 'dao.TeacherPermissionDaoTest' --no-daemon --console=plain --warning-mode all` を実行しBUILD SUCCESSFUL。新規JUnit 6件を実行し、失敗0。`git diff --check`も成功。
- 未解決事項: DAOのSQL・ロック動作は専用教師DBが未準備のため未検証。これはT013/T014へ残し、単体テスト成功と混同しない。

## 2026-10-04 18:39 JST以降: 教師課題version migration検証

- 対象/コマンド: 工程10 T002でV16を追加した後、`docker compose exec -T app gradle flywayValidate --no-daemon --console=plain --warning-mode all`を実行。期待結果はFlywayの適用済みmigration整合確認、DB変更なし。
- 実際の結果: 終了1。`Validate failed: Migrations have failed validation`、`Detected resolved migration not applied to database: 16.`。新規V16はFlywayから認識されたが、接続先共有開発DBはschema version 15のためpending migrationを通常validateが拒否した。DDL実行・migration適用は発生していない。
- 影響: validationコマンドのみ失敗。開発DBに変更なし。migrationソースの文法/制約がDB上で受け入れられるかは未検証。
- 対応: pending migrationを反映する目的でDBへ`migrate`を実行せず、読み取り専用`flywayInfo`でschema 15/V16 pendingを確認する。T002の検証記録に失敗と理由を明記する。
- 未解決事項: 専用の教師合成DB作成後、V16適用・既存行のversion初期値・`CHECK(version >= 1)`違反拒否を確認する。共有DBの通常`flywayValidate`は専用でないV16適用まではpending理由で失敗し続ける。

## 2026-10-04 18:27 JST以降: 工程10計画の文書検証

- 対象/手順: [教師課題下書き計画](./feature-plans/teacher-task-draft.md)の要件/タスク対応・ローカルリンクを、Ruby標準YAMLと正規表現で読み取り確認。期待結果は日本語MarkdownのUTF-8解析と全対応の一致。
- 初回コマンド形式: `ruby -ryaml -e '検証コード'`。実際は`invalid byte sequence in US-ASCII (ArgumentError)`で終了1。実行環境の既定文字コードで日本語本文を解析できず、計画の整合判定まで進まなかった。
- 影響: 文書検証コマンドのみ。製品コード、DB、設定、外部APIへの変更/送信なし。
- 対応/再検証: 同じ検証コードを`ruby -EUTF-8:UTF-8 -ryaml -e '検証コード'`で実行して終了0。要件9件・計画9項目・タスク15件・上流対応、ローカルリンク247件と末尾空白なしを確認。
- 未解決事項: この文字コードエラーは解消。計画の静的確認と教師機能の実装/DB/ブラウザー受入は別で、後者は未着手。

## 2026-10-04 17:32 JST以降: 教師工程前デバッグ完了

[デバッグ計画](./feature-plans/pre-teacher-debugging.md)の5件を修正・再検証・ローカル反映済み。以下の17:23時点の「未修正」は当時の分類であり、現在の未解決事項ではない。専用Composeの合成DB・認証HTTP・ブラウザーで検証し、既存利用者のデータ・パスワード・回答は変更していない。外部AI APIは呼び出していない。

| 対象 | 修正前の再現 | 原因・対応 | 再検証結果 |
|---|---|---|---|
| アンケートGET503 | 同意済み本人・公開割当・完了評価・active surveyでHTTP503。DAO回帰テストも`Column 'evaluation_feedback' not found.`で2件失敗 | SELECTに対応する`feedback_summary`をResultSetから取得。DB列やアクセス条件は変更しない | DAO4件＋回答入力6件成功/skip0。認証HTTPで取得・下書き・送信・再読込・重複防止・同意/所有者/非対象拒否を確認。実画面でフィードバック・提出済み回答を表示 |
| コードログ0件 | ブラウザーで`Cannot read properties of null (reading 'addEventListener')` | ログがある場合だけ前後/タイムラインのイベントを登録。独立したサイドバー初期化は継続 | 0/1/3件で例外0。サイドバー、前後、タイムライン、差分、合成実行の入出力表示・アクセス境界を確認 |
| 404のJSTL | HTTP404本文・ページタイトルに未処理`c:`タグ | エラーJSPに共通テンプレートで必要なtaglibを宣言 | HTTP404維持・未処理タグなし・日本語画面表示・ローカルCSS/JS各200。ログイン/生徒/管理者の通常画面も表示正常 |
| 共通確認のfocus | 通常の確定クリックで内部buttonの`aria-hidden`警告 | hide時に内部focusを解除し、hidden後に起動元または有効な代替へ復帰してPromiseを解決。本実装・生徒/教師プロトタイプを同様に修正 | 各版で取消/確定/Escape/閉じる/Enter・Tab・再表示を検証。起動元の削除/非表示/disabled、次モーダル切替、表示途中の二重確定も確認。同意/学校登録は取消POST0・確定POST1、演習は取消で未保存コード保持。対象の警告なし |
| runner pipe | 既存17テストは成功するがstdout/stderr/stdinにResourceWarning。追加closureテストも失敗 | stream読取担当がcontext managerでclose。対話stdinは入力lock下でcloseして終了状態を公開 | ホストPython3.13・配備先Python3.12とも20件成功・ResourceWarning0。成功/実行例外/timeout/取消/読み取り例外のcloseを検証。実runnerでもUTF-8・対話・制限・idle timeout・復旧が成功 |

### 検証中の失敗と切り分け

- 新しい合成fixtureの初期版ではgetter名、プロフィール列数/学校必須、seed済み同意文書ID、PBKDF2のsalt長が不適合だった。実装の定義・V11・既存seedに合わせて修正し、fixtureの登録/削除まで再検証した。後半の実行ログfixtureで非対応の`event_type='execution'`を指定した失敗も、既存の`manual_save`を維持して修正した。これらを製品の新しい障害とは扱わない。
- 共有ソースの`.gradle/8.10.2/fileHashes`が別containerのGradle（Owner PID41）に使用され、専用環境の再試験が約1分でtimeout。lock削除・他プロセス停止はせず、`--project-cache-dir`で各試験を分離して成功した。
- ブラウザー試験で誤ったセレクター、隠れたタイムラインへのクリック、複数`main`へのstrict locator、不可視tabのstable待ち、未サポートの`Storage.getCookies`が失敗した。対象セレクター/起動ページを直し、通常クリック・shown/hidden完了待ち・ページ内の同一origin fetchで再成功。モーダルのアニメーションを無効化して成功扱いにはしていない。合成の次モーダル自体を閉じた際のBootstrap警告は試験用DOMの後片付けであり、共通確認の復帰・次モーダル表示の検証とは区別する。
- live runnerの入力受付を当初200と期待して試験が停止した。既存契約の202 `accepted:true`に試験側を合わせて全項目再成功。管理者取消後のfocusを学校名だけに限定した試験も、仕様どおり有効な主領域リンクへの復帰を許容して再確認した。
- 関連画面回帰で、起動buttonが確認表示前にdisabledとなる場合、`activeElement`がBODYになって代替focusが選ばれないことを検出。BODY/HTMLを起動元復帰の対象から外し、実同意画面の有効checkbox・学校画面の主領域リンクへの復帰を再検証した。

### 最終確認・残事項

- 通常の`docker compose exec -T app gradle test build --warning-mode all --no-daemon --console=plain`は成功。その後の最終コマンドはproject cacheを分離し、`docker compose exec -T app gradle --project-cache-dir /tmp/pre-teacher-debug-main-cache test build --warning-mode all --no-daemon --console=plain`で成功。全136件中82件成功、54件は専用DB等の明示ゲートでskip、失敗/エラー0。対象DAO4件は専用環境で別途実行済みで、skipを成功件数に加えない。
- 修正後のWAR内JSP/JSはソースbyte一致。app再起動・runner再build/再配備後、login200、処理済み404、配信JSのSHA-256一致とrunner healthを確認。
- 専用単体DBのusers/tasks/evaluations/survey_responses/code_logs/code_executionsは各0。専用app/db/container/networkを削除し、tmpfs上のHTTP/単体DBと合成セッションも破棄。共有DB volumeは保持。今回生成したPython cacheと試験専用project cacheも除去した。
- 検証ページの最後の解放呼出しは`Page not found`となり、個々のタブ閉鎖までは追跡できなかった。検証サーバーは停止済みで、既存ユーザーのタブを閉じる操作は行っていない。
- 対象5件の未解決ブロッカーはなし。Gradle/SLF4Jの既存警告、本番運用、OSダイアログ/全ブラウザー互換性、教師完成後の評価・アンケート全体のユーザー手動確認は従来どおり別の残事項。

## 2026-10-04 17:23 JST以降: デバッグ要否の整理

以下は過去の記録を後続の解消記録と照合した現在の分類。今回の作業は整理と読み取り確認のみで、本実装・DB・設定は変更していない。アンケート/コードログは現行コードの確認、404は未認証の存在しないURLへのHTTP GETで確認した。実データ・外部AI APIは使用していない。

### 未修正・デバッグ対象

| 優先度 | 内容 | 現在の根拠・影響 | 次の確認・修正範囲 |
|---|---|---|---|
| 高 | アンケートGETの503 | [StudentSurveyDao](../../src/main/java/dao/StudentSurveyDao.java)のSELECTは`feedback_summary`を返すが、`findPageTarget`は`evaluation_feedback`を取得する。対象行がある場合に列名不一致で失敗する構造が残る。最新の標準ルーブリック検証でも503を記録 | DB列追加ではなく、SELECT/ResultSetの対応を修正する対象。専用合成DBで画面取得と回答保存・再読込を検証する。[評価・アンケート計画T006](./feature-plans/student-evaluation-survey.md)に引継ぎ済み。機能全体の確認を教員画面完成後に再開する合意とは分けて、既知バグの先行修正可否を確認する |
| 高 | コードログ0件時のJavaScript例外 | [log.jsp](../../src/main/webapp/WEB-INF/student/evaluation/log.jsp)は0件時に前後ボタンを出力しないが、[log.js](../../src/main/webapp/js/student/evaluation/log.js)は`previousButton.addEventListener`等を無条件に呼ぶ。空状態で初期化が中断する構造が残る | 空状態を正常な表示として扱う初期化へ修正し、0/1/複数件で表示・前後移動・サイドバー操作を確認する。T006へ引継ぎ済み |
| 中 | 404画面のJSTL未処理 | `GET /__error_report_review_missing_page`はHTTP404を返すが、本文に`<c:if>`、`<c:choose>`、未処理の`<c:url>`が残る。[404.jsp](../../src/main/webapp/WEB-INF/error/404.jsp)には共通テンプレートで使用するJSTLのtaglib宣言がない | エラーページのJSTL宣言・共通includeを確認し、404を維持したまま本文のタグ処理とCSS/JSのURLが正常になることを検証する |
| 中 | 共通確認モーダルのフォーカス警告 | 同意変更・管理者・授業演習プロトタイプで、内部にフォーカスが残った状態の`aria-hidden`警告を繰り返し記録。[本実装feedback.js](../../src/main/webapp/js/shared/feedback.js)の確認処理にはhide前のフォーカス解除・起動元への明示復帰がない。ただし個別画面の対策・Bootstrap標準動作もあるため、今回すべての画面で再現したとは扱わない | 共通feedbackとプロトタイプの同等部品で、取消・確定・Escape・再表示の通常操作を再現し、必要な共通修正を行う。機能成功の記録とは別のアクセシビリティ残件 |
| 低 | runnerテストの`ResourceWarning` | process pipeの未close警告が残るとの記録。最終runner試験は成功しており、現時点でアプリの実行障害とは確認されていない | テスト側の子プロセス・pipe後片付けを再現確認する。実行コンテナcleanup競合の修正とは別件 |

### デバッグではなく再検証・環境整備の対象

| 内容 | 扱い |
|---|---|
| OS標準フォルダ選択・ZIP保存・ブラウザー/OS互換性 | 未確認の受入検証。通常Chromeの単体`.py`保存は利用者確認済み。統合ブラウザーのdownloadイベントtimeoutだけでアプリ不具合とは断定しない |
| 認証済みの評価・アンケートE2E、教員が設定する実プロンプト等 | [評価・アンケート計画](./feature-plans/student-evaluation-survey.md)の意図的な保留。教員画面完成後に再開する。上記の既知バグは保留中でも未修正として保持する |
| Gradle cache lock・PID namespace・TIME_WAIT・起動直後の404 | 検証環境・起動手順の問題。cache分離、同一container内実行、readiness待ち等で後続成功の記録がある。通常運用で再発する場合に再調査する |
| V11トリガー作成権限（MySQL1419） | 移行用権限の問題。移行時だけ適切な管理権限を使う手順で成功済み。通常アプリユーザーの権限を緩める修正は不要。本番移行時の手順確認は必要 |
| Gradle `WarPluginConvention`非推奨 / SLF4J警告 | ビルド停止ではない警告。Gradle更新前の互換性対応・ログ設定確認として扱い、緊急デバッグ対象とはしない |
| セレクター誤り、fade完了前操作、不可視ページ、汎用runTestsのJava未検出、Node/Playwright不足、fixture/FK/文字コードの誤り | 試験手順・ツール・合成データの問題。修正後の成功記録を参照し、これだけを理由に製品コードを変更しない |

### 解消済みとして履歴保持する主な問題

- runnerのcleanup競合・日本語出力32KiB境界・入力上限エラー理由の欠落: 「T012のrunner修正・最終再検証」で修正と実再検証済み。古い節の「未解決」は当時の状態。
- 単体ダウンロードのMIME不一致: 「単体ダウンロード修正・教師工程前の最終確認」で解消済み。
- 三点メニューの切取り・矢印/Escape操作、移動先選択のlistener配置、二重ルート表示、親チェック状態、Tooltip再描画例外: 各第100〜104版の後続検証で修正済み。
- 評価保存エラーと不正AI出力の混同（ERR-20261003-001）: 例外境界の分離と回帰/専用DB検証で解消済み。今回、実APIの障害が継続しているとは判断しない。
- 学校管理CSS未適用、パスワード変更の履歴欠落、ルーブリック再試行時のフォーカス: 各節の修正後検証を参照。共通feedbackのフォーカス残件とは別。

推奨順序は、アンケート列名不一致 → コードログ空状態 → 404画面 → 共通モーダルのフォーカス → テスト警告・環境整備。対応着手時に合成データで再現・回帰検証し、今回の静的照合だけで修正完了とは扱わない。

## 2026-10-04: 生徒共通標準ルーブリック閲覧

- 指定0805資料をDBから取得してprototypeの全画面modalで表示。固定版の不変登録と2次元/6観点/30説明を確認し、既存課題・評価の割当は変更しない。教師画面・評価再実行・アンケート回答確認は対象外。
- 当初の登録CLIは有効な管理者を必須としていたが、ローカルには教師/管理者が存在しなかった。ユーザー確認後、V15で作成者NULLを許容し、アカウントを作らずシステム標準版として登録する方式へ変更。既存作成者は更新しない。最終Java69件・HTTP10件・API認証境界/未登録503/資料完全一致・冪等登録は成功。
- 専用環境で別containerからGradleを実行するとPID namespace違いのcache lock競合が起きたため、起動中の同じapp container内で `compose exec` を使用した。fixture再実行時の単一admin制約も当初検出したが、最終方式ではadmin作成自体を不要とした。
- ブラウザーはfade完了前のEscape/focus検査でtimeoutになった。shown/hidden完了を待って再確認し、正常に動作。再試行後に隠したretry buttonからfocusが外れる問題は、loading開始時にmodalへfocusを移し、成功後Escape→起動元focusを再検証した。
- 専用環境再構築時は実listenerがないのにTIME_WAITでport bindが失敗。所有portのlistener不在を確認し、検証用probeでSO_REUSEADDRを使って再成功。Node CLI不在は新規依存を追加せず、ブラウザーの構文解析と実表示で代替確認した。ブラウザー試験のURL global不在と375pxの未展開ナビゲーションも、試験手順を正して再成功。
- ローカル割当保持の照合で、mysql CLIの既定latin1により日本語タイトル除外が効かず、新標準版を既存行として数えた。utf8mb4を指定して再照合し、標準版1件/作成者NULL、既存別ルーブリック0件、標準版への課題・評価割当各0を確認。専用DBでは別の既存版/作成者を用意し、登録前後の不変を確認した。
- **別件・未修正**: アンケートの合成対象へのGETは `StudentSurveyDao.findPageTarget` の `evaluation_feedback` 列名不一致で503。コードログが0件の場合は既存log.jsでpreviousButtonのnull参照が起きる。今回変更したAPI/modalとは独立の既存経路であるため修正せず、[評価・アンケート計画T006](./feature-plans/student-evaluation-survey.md)へ引き継ぐ。ログ行があるコードログ画面ではmodal表示成功。アンケートmodalは共通JSP接続のみ確認済みで、実画面確認は未達と区別する。
- 503 JSON/不完全JSON/HTMLを注入した際は明示エラー＋再試行、未保存コード保持、遅延close/reopen時の古い応答破棄を確認。実DB停止の試験ではない。local V15/登録/再登録/WAR/restart/login200/配信byte一致成功。専用合成DB/container/network/copy/cache/serverを清掃し、検証pageはabout:blankに解放。既存Gradle/SLF4J警告は継続。

## 2026-10-04: 単体ダウンロード修正・教師工程前の最終確認

- 三点メニュー修正時に検出した単体取得のMIME不一致を解消。サーバーの既存契約`text/x-python`を画面の許可形式へ追加し、Content-Typeは`;`より前をtrim/小文字化して完全一致で確認する。`text/plain`と`application/zip`も維持し、HTML/JSONなどをファイルとして保存する回避策は使わない。単体menu/選択1件は同じ処理を使用する。画面資産は`104-download-fix`。
- 専用DB/runnerのsetupは移行fixture・Java64件・WAR/login200成功。`python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は10成功。専用HTTPの単体MIME検証を`text/`という広い条件から`text/x-python`完全一致へ強化し、当該1件も再成功。
- 今回はディスク上に作った日本語.pyと日本語フォルダ/子フォルダ/.pyを、ブラウザーの実file inputへ`setInputFiles`で指定した。プレビュー→追加先root選択→確認→実取込成功。以前の合成File/DataTransferだけの試験とは区別する。ただしOS-native chooserを手で操作した確認ではない。
- 実ブラウザーで単体menuと選択1件の取得がエラーなし、日本語filename/UTF-8内容/Blob形式まで一致。フォルダZIPと複数選択の日時付きZIPも生成を確認。保存済み取得は保存コード、エディター取得は未保存コードとなり、dirty/チェックを保持した。注入したHTML成功応答/403 JSON/ファイル名header欠落の3条件はダウンロードを生成せず、具体的エラーとdirty/選択保持を確認。注入応答を実サービス障害試験とは扱わない。
- 保存→実再読込、未保存のまま移動→Path/サーバーツリー/編集コード保持、別folderの削除→配下ごと復元、保存→実runner完了→実ログアウト→再ログインでコード・実行結果の復元まで確認。1280/900/375pxのページ横overflowなし・メニューEscapeも再確認。
- `python3 verify_download_disk.py <synthetic-login> <scope>`で認証HTTPから.py/フォルダZIP/選択ZIPを実ディスクへ保存し、読戻しbyteとZIP全項目/配下内容を照合。異階層の選択ZIPは相対パスを保持する既存契約だったため、試験の「basenameへ平坦化」という誤った期待を修正し、再成功。実装のZIP構造は変更しない。
- 統合ブラウザーの`waitForEvent('download')`はtimeoutとなり、`saveAs`によるOS保存確認はできなかった。代わりに実クリックで生成されたBlob/filenameと、認証HTTP→ディスク→読戻しを検証した。その後、利用者が通常Chromeで三点メニューから.pyを保存し、保存済みコードとの内容一致を確認した（「保存でき、内容も一致した」）。通常Chromeの単体.py保存は利用者確認済みであり、自動試験の成功と区別する。OS-native folder chooser/ZIP保存・全browser/OS互換性は未確認のまま。
- local `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`成功、login200、JS/CSS/helper配信byte一致、`git diff --check`成功。専用合成行/主要7表0、container/network/copy/cacheと試験入出力fileを除去、所有port解放・両検証page about:blank。教師向け工程には進んでいない。既存Gradle非推奨警告は継続。

## 2026-10-04: 第104版三点メニュー表示修正

- メニューが横スクロール用ツリー内に置かれ、`overflow-x: auto`により切り取られていた。z-indexだけでは解消しないため、表示中だけbody直下へ移し、Popperのfixed配置を使用する。閉じる際は元の位置へ戻し、再描画前にhide/disposeする。本実装・prototypeとも共通形のhelperを接続し、prototypeのクリック委譲は移動したメニューも対象とする。
- Bootstrapのキーボード委譲はdocumentのcapture段階で元の親子配置を前提にするため、menu自身のkeydownでは矢印/Escapeが処理されなかった。body直下メニューに限るwindow captureで項目移動/閉じる/元buttonへのfocusを処理し、両UIの3幅で再試験成功。通常のBootstrapメニューには適用しない。
- 実/prototypeの1280/900/375pxで全項目中心の`elementFromPoint`とviewport収容を確認。矢印選択/Escape/focus復帰、別メニューを開いた際の単一表示、再描画後のoverlay除去・再表示・外クリック、移動/削除確認への接続と取消を確認。実画面の通常ダウンロード/移動は`btn-outline-secondary`でグレー、削除系は赤で、Chrome固有の青文字欠落ではない。
- 専用setupの移行fixture/Java64件/WAR/login200、認証HTTP10件は成功。local WAR/restart/login200とhelper/JS/CSSの配信byte一致も成功。合成DB/専用container/network/copy/cache/serverを除去し、所有3port解放とbrowser about:blankを確認。
- **当時の別件・後続で解消**: 単体ファイル取得のブラウザー試験で、サーバーの成功応答が`text/x-python;charset=UTF-8`である一方、既存の`downloadEntries`は`text/plain`/`application/zip`のみを受け入れ、画面に取得失敗を表示した。認証済みの直接HTTPでも200と同MIMEを確認した。メニュー移設では取得ロジックを変更していない。上記「単体ダウンロード修正・教師工程前の最終確認」で不一致を解消し、OS-native手動確認は分離して残した。
- prototypeの既存shared feedback hide時aria-hidden/focus警告、全browser互換性/OS-native操作の未確認は継続。新API/DB/依存・実アカウント操作はない。

## 2026-10-04 10:30〜10:45 JST: 第101版エクスプローラUI改善

- 移動先を未選択必須にした際、確定ボタン更新用change listenerが初期化ではなくsubmit内部に登録され、フォルダを選んでも初回はdisabledのままだった。可視試験でbefore/afterともtrueを検出し、初期化へ移動。再試験はbefore=true/after=false、別名指定の実移動成功・表示エラーなし。
- prototypeの新しい移動先表示が、既にrootを含むpathへ再度rootを付けて` s001/s001/授業メモ `と表示した。既存path契約へ合わせ、可視再試験で` s001/授業メモ `と二重rootなしを確認。位置選択の値/API/DBは変更していない。
- Bootstrap fade完了前の直後focus読込は空IDだったが、hidden完了後のfocusを待つとuploadButtonへ復帰することを確認。prototype旧move modalのhide時aria-hidden/focus警告は残る。本実装のhide前blurは維持し、警告をJS例外や保存失敗と混同しない。
- 専用setupのJava64件/HTTP10件・移行fixture成功。最終WAR/restart成功。可視browserの初期fold/モード/異階層/取消/dirty/移動先/親不在復元/255文字/3幅と名前幅測定は[授業演習計画Plan3.19/3.20](./feature-plans/student-exercise.md)を参照。OS-native操作は今回も未確認。

## 2026-10-04 10:58〜11:10 JST: 第102版配置調整

- prototypeへ選択操作detailsを追加した際、閉じタグ位置により通常操作buttonがdetails外に出た。折りたたみ後のbutton可視性とDOM包含で検出し、通常/ごみ箱の構造を修正。再試験は移動button不可視、details内包含、再展開後の移動取消focus成功。
- 検索結果選択buttonを検索結果一覧から選択欄へ移したため、旧一覧のdelegated listenerでは受けられない。新buttonへ既存選択処理を移し、可視試験で検索結果1件選択を確認。ごみ箱detailsは再描画で状態を保持する。
- prototypeのCSS矢印は文字コード解釈で文字化けを検出したためASCIIのCSS escapeへ変更。既存upload modalを閉じる際のaria-hidden/focus警告は残る。本実装のhide前blur/focus復帰は維持する。OS-native picker/saveは未確認。
- 専用Java64/HTTP10成功、3幅の右端44px upload・対の展開ボタン・折りたたみ選択保持、未保存コードと取消focus、WAR/restart/login200/資産一致を確認。[授業演習計画Plan3.21](./feature-plans/student-exercise.md)を正本とする。

## 2026-10-04 11:14〜11:27 JST: 第103版識別・選択範囲改善

- 線画アイコン追加後、375pxの「すべて折りたたむ」でbutton内overflowを検出。対のbuttonの余白/文字サイズを調整し、同幅でbutton内overflowなし・ページoverflowなしを再確認した。作成文字は縦2段各1行、upload中央誤差0px。
- 親選択の配下を表示更新した際、rootのmixed状態が前の値のまま残ることを可視試験で確認。通常チェック更新と同時にrootのchecked/indeterminateも再計算し、親チェック→解除でmixed=falseを再確認した。
- prototypeのnative検索×試験でクリック後も文字が残った。入力がviewport外にありクリック座標が画面外だったため、scrollIntoView後の実クリックで空入力/検索状態非表示を確認。実画面のnative×もクリック/一覧復帰成功。全ブラウザー互換性の認定ではない。
- 専用Java64/HTTP10、実/prototype構文、親子チェック・明示子保持・移動確認1件とdirty/取消、WAR/restart/login200/配信資産一致は成功。詳細は[授業演習計画Plan3.22](./feature-plans/student-exercise.md)参照。既存Gradle非推奨/OS-native chooser/save未確認は継続。
- 続く選択2列配置の試験ではサブピクセル丸め差0.008pxとhoverの1px移動を区別した。同幅は1px未満の許容で測定し、選択操作群はtransformなしで上下位置を固定。実/prototypeの3幅で左右端/同段/内外overflowなしを確認。

## 2026-10-04 11:32〜11:40 JST: 第104版確認改善

- prototypeの既存danger helperは「取り消しできない操作」を既定表示し、ごみ箱への移動の説明と矛盾した。この呼出しのkickerだけ「削除する内容の確認」へ指定し、赤button/全配下/完全削除ではない文言を再確認。shared feedback hide時のaria-hidden/focus警告は既存残件。
- runTestsツールは既存Javaテストを検出できなかった。専用Gradle setupと実XMLで64成功/failure/error/skip0、認証HTTP10成功を確認し、ツールの未検出を成功として数えない。
- 実UIで全31項目/空folder/深階層/長名/削除済み除外、3幅modal内scroll、削除→移動/復元/複製の色リセット、dirty取消/実削除復元を確認。詳細は[授業演習計画Plan3.23](./feature-plans/student-exercise.md)参照。

## 2026-10-04 09:50〜10:25 JST: 第100版エクスプローラ追加バッチ

- 初回専用`setup.py`のJava64件中2件失敗。upload previewが相対パス全体を単一名前の検証関数へ渡して400になり、選択folder+childのZIP名は展開後項目数で分類してaccount.zipになった。構成要素ごとのパス検証と、親子整理後の選択ルート/ZIP配下の分離で修正。後者修正の再ビルドは再代入変数をlambdaから参照してcompileJava失敗し、確定したfolderNameをキャプチャして解消した。
- 専用DBにHTTP/browserの合成利用者が残った状態でJavaを再実行し、空DB前提の37件が失敗した。DBゲートを緩めず、ブラウザーを解放し、DB名を照合して専用合成行だけを空にした。最終専用69件はfailure/error/skip0、WAR成功。利用者DBを初期化/試験していない。
- HTTP新fixtureはGET treeへ空entryIdを送り400、存在しないexecution_records表を参照、親子整理で除かれる子へ別名を送って400だった。省略契約/実code_executions表/整理後別名規則へ修正。最終`python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は10成功。時刻をregexだけでなく取得前後のAsia/Tokyo秒精度範囲で照合した。
- 実browserで一括JSON内の文字列entryIdをサーバーが400拒否し、複製で単体nameとitems内nameを二重送信して400拒否した。画面はJSON数値IDへ変換し安全整数を検証、単体複製ではitemsを送らないよう修正。プレビューは`items[0].suggestedName`、uploadは`conflicts/resolutions`へ合わせ、可視の再確定で移動/複製/別名取込を成功確認した。失敗時の入力/チェックは保持した。
- Tooltip表示遷移中のcheckbox再描画で、Bootstrap callbackが破棄済みinstanceの_activeTriggerへアクセスしてTypeError。委譲Tooltipをproto/本実装ともanimation:falseへ限定変更し、255文字hover後の再描画/キーボード解除でエラーなしを再確認した。
- browser試験側の存在しないsearch-reset ID、閉じたごみ箱detailsのbutton、非focusableなtreeへ直接press、macOS Ctrl-clickの文脈操作でtimeout/選択未変化があった。実ID/summary展開/checkbox内focus/⌘操作へ修正し、⌘click・Shift範囲・⌘A・Escape・検索欄除外を再確認。共通未保存確認の応答を待つ試験timeoutは、通常pointerで承認後に確定後読込失敗の警告/変更禁止/コード保持を別読込で確認した。
- ブラウザー実行環境はBuffer未提供のためsetInputFilesの合成bufferは実行できず、DataTransfer/Fileによるinput changeで実upload preview/commitを確認した。OSネイティブpicker操作済みとは扱わない。DB清掃後の古い合成sessionによるhome403は別originで新合成fixtureへログインして解消し、共有cookie/実アカウントは変更しない。
- 最終ローカル関連32件はfailure/error/skip0、WAR/restart成功、login200。既知のWarPluginConvention非推奨警告は残る。詳細コマンド/成功範囲/OS保存等の限界は[授業演習計画の第100版追加バッチ結果](./feature-plans/student-exercise.md)を参照する。

## 2026-10-04 JST: 第99版ごみ箱階層・全文表示・ZIP名

- unit toolはJava試験を検出しなかったため既存Gradleへ切替。先行archiveName/contentDisposition試験は未実装メソッドでcompileTestJava失敗、実装後は専用関連56成功/ローカル23成功、WAR/reload成功。
- patchのJSP/CSS context不一致で部分適用が2回発生。成功済みhunkを再適用せず、残りの対象箇所だけ確認・適用した。旧asset URLをv99-trashへ更新。APIのtrashRootEntryIdが数値、初期datasetが文字列である差は比較時に正規化し、再取得後も階層を維持した。
- ごみ箱内の操作不可項目を通常Playwright clickするとaria-disabledによって待機timeout。名前のhover/focus表示は成功済みで、非操作のpointer確認だけforceを使い、階層展開/復元などは通常pointerで検証した。無効項目を有効化して試験を通さない。
- 統合ブラウザーのdownload event待ちがtimeout。通常clickで生成したanchor名/実blob1642bytes/ZIP signatureと、HTTP実取得のheader/ZIP内容/版不変を検証した。OSdialog/ディスク保存の検証とは区別する。
- プロトタイプのpage.requestはStorage.getCookies未対応。ページ内fetchでJS構文を検証した。初回focusのtooltip待ちもtimeoutし、安定後のhoverと別要素へ移った後の再focusで可視表示を確認した。準備中appRestartの一時404はreadiness200を待ってHTTP4件を全て成功させた。
- 詳細コマンド・確認範囲/限界は[授業演習計画Plan3.12](./feature-plans/student-exercise.md)。利用者データ/実アカウントと共有cacheは試験/清掃対象にしない。

## 2026-10-04 JST: 第98版T029単一ルート追加バッチ

- TDDは未実装preview/unifyでcompileTestJava失敗（期待した先行失敗）。初回実装後は`ExerciseSaveResult`へ架空entryId=0を渡して1試験失敗。空領域統合にも正しい応答形を返せる`ExerciseUnificationResult`へ分離し、最終55成功/失敗error/skip0・WAR/reload成功。現在編集中の期待版ガードを追加した後も最終再実行に含めた。
- 専用Gradleは共有journal-cache lock取得でtimeout。読み取りでlive ownerを確認し、lock削除/他daemon停止を行わず、専用application内のGRADLE_USER_HOMEへ既存cacheをlock除外seedしてoffline実行へ分離した。setupへ同じ処理を反映、依存追加はない。
- HTTPの実途中失敗は、専用の一時MySQL triggerで元領域FKを不正値にして注入した。HTTP503と処理前後の全体JSON/token一致を検証し、finallyでtriggerを除去した。最終認証HTTP3件は成功、既存名前変更/移動/復元も回帰確認。現行版を再読込せずpreviewだけ更新していた場合の統合は409にし、dirtyコードへ新版を割り当てない。
- appRestart直後の一時404はreadiness200待ちで解消。可視browserの統合後通常保存で、診断用HTML data-versionが新版になるwaitはtimeoutした。既存save処理はJSの版変数のみ更新するためで、実DBの新版/保存コード/元領域関連とUI「保存済み」は成功。無関係な診断属性を修正せず、正しい保存結果で検証した。
- ローカルmigration/validate/22件/WAR/reload・HTTP200、flywayInfoのschema14/全Successを確認。利用者の実データはmigrationの元領域メタデータ補完だけで、確認なしの統合はしない。実施/未確認と正確なコマンドは[授業演習計画](./feature-plans/student-exercise.md)を参照。

## 2026-10-04 06:55〜07:13 JST: 第98版T029ごみ箱基盤の部分バッチ

- TDD: restore新引数/削除単位DTOを先に追加したDBテストはcompileTestJava失敗。実装後の専用最終関連テストは51成功/失敗skip0・WAR成功。
- 移行fixture初回はstudent_profiles未投入によるFK1452、補正後の後片付けは親子参照FK1451で失敗。移行前後の業務データ一致/削除単位/通常索引検証とは区別する。profile/schoolを投入し、合成fixtureの親参照だけ解除して片付けるよう修正。初回fixture残留で0利用者前提のDB試験が失敗したため、専用DBだけを片付けて再試験、さらに専用環境を新規作成してpopulated V12→V13と51件を全て成功させた。利用者DB/旧migration/共有cacheは修正していない。
- 新HTTP試験のrunner poll戻り値を辞書と誤解しtuple索引エラー。既存helperの`(result,elapsed)`へ修正。復元先fileが完全削除祖先下だったケースはactiveでないため404が正しく、400期待を修正。最終`python3 -B -m unittest -v test_trash test_organize`は2成功/失敗0。新しい不正応答を成功扱いにしたのではなく、既存の所有者/非有効項目契約に合わせた。
- 可視ブラウザー初回は復元buttonのaria-label「授業を復元」と異なる「復元」exact selectorを使用しタイムアウト。正しいaria-labelへ修正後、通常ポインター/Bootstrap fadeの復元・名前変更・移動が成功。名前衝突に再読込を促す旧共通409案内も検出し、型付きNAME理由/name_conflictと版競合exercise_conflictへ分離、別名入力で継続できることをHTTP/ブラウザーで再確認した。
- 編集中file削除で旧「未保存確認→削除確認」を連続表示すると共通feedbackの一つ目hide中に二つ目showが衝突して非表示となった。授業演習では一つの削除確認へ未保存消失警告を統合し、共通feedback自体を再実装/改変しない。取消のコード保持と確認後の選択解除を、通常ポインターで成功確認。共通feedback hide時の既存aria-hidden/focus警告は残り、今回の演習modalはhide前blurの既存処理を維持する。
- 専用DBリセット後、残っていた合成ブラウザーsessionで旧利用者のhomeが403。共有cookieを削除せず、127.0.0.1の専用appで新合成fixtureへログインして以後の可視検証を完了した。appRestart直後の一時404もreadiness HTTP200待ちで解消し、HTTP試験を準備前に開始しない。
- 最後のローカルGradle反映が共有fileContent cache lock取得待ちで失敗（owner PID932/timeout61秒）。複合コマンド全体の最後のcleanupがexit0でも、このGradle失敗は成功扱いにしない。所有する専用環境を終了した後、同test/war/appRestartを単独再実行して成功した。共有lock削除/他プロセス停止は行わない。親行自体の欠落を追加した専用DB最終51件とHTTP2件も成功、ローカル再反映22件とreadinessを別々に確認する。
- 専用移行/HTTP/可視ブラウザーでごみ箱基盤は確認済み。T029の単一ルート統合とT030/T031は未完了。詳細・正確なコマンドは[授業演習計画](./feature-plans/student-exercise.md)へ記録する。

## 2026-10-04 JST: 第98版の名前変更・移動先行バッチ

- TDD: `docker compose exec -T app gradle test --tests control.student.StudentExerciseDatabaseTest --no-daemon --console=plain --warning-mode all`は未実装rename/moveのcompileTestJava失敗（期待した先行失敗）。実装後の専用DB最終試験は48成功/失敗skip0、WAR成功。
- ブラウザーでルートへのmoveが400「対象IDと版番号を正しく指定してください」。明示ルートのparentEntryId空文字を従来optionalIdへ渡したことが原因。moveだけ空文字をnullへ変換し、missing parentは400のまま。mutableなroot移動をHTTP試験へ追加し、1ケース成功。readonly400をroot成功の代用にしない。
- 専用appRestart直後のHTTP試験はloginが200になる前に開始して失敗。readiness200待ち後に再試験して成功。root不具合と起動待ちの試験失敗を区別する。
- 統合ブラウザーの安定待ちクリック/fade完了がタイムアウト。要素のdisabled=false/rect/表示CSSとvisibilityState=hiddenを確認。DOM clickでハンドラーとsuffix更新は起動するが、背景タブでfade後の表示が進まなかった。検証ページのfadeを外し、DOMイベントと実HTTP/DBで名前変更/移動/保持/衝突を確認した。通常の可視UI操作・アニメーションの検証は未確認として残し、検証用の無アニメーション変更は本実装へ入れない。
- 前回の「長い名前/フォルダsuffix確認済み」を利用者再指摘で再オープンした。配信コードに処理があることだけで解消扱いにせず、深い階層の最小幅/内部スクロール、hidden+d-none初期状態と切替、画面資産URLの版更新を実装。専用データ16階層/255文字で再測定。利用者の旧表示の原因をキャッシュと断定していない。詳細と手動確認の残件は[授業演習計画](./feature-plans/student-exercise.md)。

## 2026-10-03 23:25〜23:44 JST: T012のrunner修正・最終再検証

- cleanup競合とUTF-8出力境界は修正/再検証済み。TDDの先行runnerテストは15件中3失敗/5error（旧出力と未実装cleanup）でexit1、修正後は17成功/失敗skip0・exit0。共有BoundedOutputで同期/対話の生バイト/表示UTF-8を32KiB以内、イベント/snapshot/DB一致へ変更。不正UTF-8置換は維持し、上限で切れた末尾文字だけを省く。
- cleanupの最初の実再検証では、inspectが小文字`error: no such object`を返し、旧大文字判定のため503が残った。CLIの実応答を再現する単体テストを失敗させてから大小文字正規化へ修正。最終実試験で削除処理中rc1→inspect消失確認を観測し、503なし・実30秒input_timeoutの一回保存成功。2秒以内に消失しない/daemon異常/inspect異常はログ付き失敗のまま。
- 入力合計の追跡で、Java clientがrunner409/413を一般IOExceptionへ落とす欠落を確認。新テストは2件中1失敗・exit1、構造化理由保持へ修正後2成功。実8192バイト入力/次の入力413/終了後409/DB一回保存を確認。最初の入力DB検証SQLは非集約列とCOUNTを混ぜ1140となり、MAXへ修正。最終13フローは276.678秒・13成功/失敗skip0・exit0。
- 専用DB/Javaは36成功/失敗skip0・WAR成功。初回setupでmigration用rootがtestにも適用されていたため、手順を分離し、最終36件を通常アプリDBユーザーで再実行して成功。rootはV11 triggerを含むmigration/validateのみに使用し、権限を緩めなかった。
- ローカルrunnerを再ビルドしhealth200、実日本語32766バイトとイベント一致を確認。local Gradle test/war/appRestart初回は専用環境と共有するjournal-cache lockを取得できず失敗（ownerPID41、lockは削除しない）。所有している専用環境を終了後、同タスクを--no-daemonで再実行し2成功/WAR/reload成功・exit0、ログイン200。初回複合shellの最後のcurlは成功したが、Gradleの失敗を成功扱いしない。
- 既存runnerテストのprocess pipe未closeに由来するResourceWarningは残る。今回の新規同期経路テストでも表示されたが、テストexit0と区別して記録し、無関係なプロセス管理変更は追加しない。既知Gradle非推奨警告も維持。
- T012/T013は初回範囲で完了。CPU4秒/入力合計/隔離/実行中HTTP認可、31秒編集のログ非生成、実障害復旧、既存課題提出を確認。専用DB名ガード/合成行削除/7主要表0件の後、専用コンテナ/ネットワーク/一時コピー/venvを除去。既存ユーザー/データ・管理者統合/工程8/IC-010は変更なし。詳細・後続範囲は[授業演習計画](./feature-plans/student-exercise.md)参照。

## 2026-10-03 22:56〜23:21 JST: 授業演習T012実制限・障害・提出回帰

- 独立アプリコピー/専用tmpfs MySQL/実runner/Chromeで新規3ファイル9フローを検証。最終unittestはexit1、7成功/2失敗/skip0、278.845秒。T012/T013は未完了、検証バッチで本実装コードは変更していない。正規Gradle test/warはexit0、81成功/失敗0/skip12（評価DB6/Gemini smoke1/学校DB4/同意DB1）で、実E2Eの失敗とは区別する。
- **未解決: cleanup競合**。30秒無通信時、期待したtimed_out保存の代わりにHTTP503 runner_cleanup_failed。実cleanupのrc1/stderr `removal of container ... is already in progress`を0.039945秒と再現0.071689秒で記録。自動削除`--rm`と明示削除の競合を原因と特定。2秒タイムアウトではなく、実行コンテナの残存は0。無条件無視ではなく、期限内の実消失確認と結果保存の再検証が必要。
- **未解決: 日本語出力境界**。stdoutの生バイト捕捉は32768だが、文字途中の末尾をU+FFFDに置換すると返却/DB保存テキストがUTF-8で32769になる。実DBのOCTET_LENGTHも32769を確認。返却/保存値32KiBの追加テストは失敗。文字境界での切詰めと不正UTF-8処理の整理が必要であり、捕捉上限まで破られたと誤記しない。
- 専用runner実停止の503と専用DBの実権限喪失/既存接続終了によるstorage_unavailable503は、共通feedback/未保存入力保持/コードと版不変・復旧後の実行/保存/再読込が成功。DB検証は実アクセス不能であり、物理DB停止/通信blackholeの確認ではない。課題側の保存/対話実行/実チェック/提出確定とDB読戻し、60秒全体制限、stderr32768、8回停止保存は成功。
- 検証側初回のGrettyがstdin EOFで即終了したため、専用composeにstdin_open/ttyを追加しHTTP200確認。host PythonのPlaywright不足は専用venvへ導入（Nodeなし、既存Chrome使用）。MySQL CLIの日本語代替/改行エスケープはutf8mb4/rawへ修正した。readonly400とarchived404の既存契約へ期待値を修正した。runner再起動後のhealth待ち/Java DNS負キャッシュ待ちと同期runner65秒に合わせたHTTP待ちを追加した。専用MySQLイメージのgrantはDB名のunderscoreがエスケープされ、指定形式のREVOKEが1141となったため、専用ユーザーだけの権限喪失/復旧へ切り替えた。これらはアプリ不具合と区別し、最終統合結果に反映した。
- 合成データはDB名を厳密照合した専用DBだけで削除し、7主要表の0件確認後、専用3コンテナ/ネットワークと一時ソースコピー/venvを除去。普段のlocalhost8080はHTTP200、既存ユーザー/共有サービスに変更なし。再現テストとレポートはセッション成果物に保持した。

## 2026-10-03 22:53 JST: 授業演習のChrome実ダウンロード・表示追加確認

- 統合ブラウザーでは取得できなかったdownloadイベントを、インストール済みChromeの独立headlessコンテキストで取得した。単体/ZIPともfailureなし、ブラウザーの一時保存ファイルと指定保存先の存在/実内容を確認。利用者のChromeプロファイル/Downloadsを使わず、合成データのみ使用した。ネイティブ保存ダイアログ操作は未確認であり、ディスク保存成功と区別する。
- host PythonのPlaywright不足で検証コマンドが失敗したため、検証専用venvへ導入。導入後も最初のscript直接実行ではModuleNotFoundErrorとなったが、同じvenvのsys.prefix/sys.pathとpip showはパッケージを確認した。同インタープリターでimportとrunpy.run_pathを明示すると成功。直接実行の原因は未特定であり、アプリの不具合とは扱わない。検証後venvは削除した。
- 初回の行座標の完全一致assertは既存hoverのtranslateY(-1px)で失敗。行の上下関係を維持した上で差2px以内の判定へ変更した。アプリのCSSでhoverを除去する変更は行わなかった。
- 座標検査だけでは見逃した固定3rem高ボタンの折り返し文字のはみ出しを画像で検出。ツリーの高さauto/align-items:stretchへ修正し、1280/900/375pxでテキスト領域のボタン内包含と行内高さ一致、2列2行/編集操作順/編集高/横overflowなしを再検証して成功。実ダウンロードとDB/未保存状態の不変も再検証し、WAR成功。各試行の作成ID指定fixture cleanup/残存0件確認を行った。

## 2026-10-03 22:24〜22:39 JST: 授業演習ダウンロード・不足配置の再確認

- 前回の設計復元では旧初回範囲を理由にダウンロード/upload/移動/フォルダ追加の配置を省いていた。ユーザー指摘で差分を再確認し、第94版の範囲変更承認後に単体・一括ダウンロードと準備中配置/フォルダ追加を反映した。前回の設計復元完了は全操作配置の一致を意味せず、その不足を計画へ記録した。
- テスト先行の初回パッチが既存テストのループ内へ新テストを挿入しcompileTestJava失敗。クラス直下へ移動して、未実装型/APIによる期待した失敗を確認後に実装した。最終34件成功・失敗/skip0、WAR成功。
- ZIPエラーのMIMEを追加後も実HTTPに旧Servletが残り、404本文がJSONだがContent-Typeなし/日本語が`?`になった。Grettyのクラス変更通知にConnectExceptionを確認し、標準`gradle appRestart`でローカルアプリを再読込した。ログイン200と再認証後のZIP200/JSON404・日本語エラー・空領域400/停止アカウント403/閲覧専用200を確認した。DB/python-runnerサービスの再起動や全体設定変更は行わなかった。
- 統合ブラウザーでPlaywrightのdownloadイベント待機がタイムアウト。生成Blobとdownloadリンク名を捕捉し、単体の未保存内容と、一括の実Blobバイト列をZIP解析して保存済み内容/空フォルダ/ごみ箱除外を確認した。OS保存先への書込み・保存ダイアログは未確認であり、保存完了を検証済みと扱わない。注入503の入力保持は別のUI検証として記録した。

## 2026-10-03 22:06〜22:18 JST: 授業演習の設計復元・対話セッション

- TDD初回とrunnerエラー理由保持の初回は未実装型/APIでcompileTestJava失敗。実装後の最終専用DB/関連テスト49件は成功・失敗/skip0、WAR成功。
- 非結果CSS抽出の終端探索が前方にある同名セレクターを使い、workspaceブロックが空となった。ブラウザーで実高さ620pxを検出し、始点以降から終端を探索する抽出へ修正。560px/375幅420px・gear32px・青緑実行ボタンを確認した。
- 共通結果JSP断片にpageEncodingがなく日本語見出し/入力ラベルが文字化けした。UTF-8指定後、両実画面の正常な日本語表示と実対話入力を確認。
- hostのnode構文検査は`node: command not found`で未実施。導入せずブラウザーで実ページ実行と`new Function`構文検査に切替して成功。
- 停止後のGET sessionで実runner HTTP503を観測。dockerの実行コンテナ残留なしを確認したが、cleanupの失敗原因は未特定。runnerの構造化errorCodeを明示する例外を追加し、terminal unavailable/消失時の無意味な再試行を止め、履歴成功を偽装しない。正常停止の再検証とDB保存は成功。原因調査はT012に残す。注入503での失敗表示/入力保持・サーバー完了監視保存の別検証は成功したが、実故障の再現/原因解消とは扱わない。
- 不可視共有ブラウザーのスクロール安定待ち/screenshot待ちとwaitForFunctionの引数位置で検証がタイムアウト。DOM操作とpollingを第3引数へ明示して、実対話/停止/状態/レスポンシブを確認した。synthetic fixtureのbeforeunloadダイアログも検証側で処理。アプリの失敗と検証ツールの失敗を区別した。
- 合成fixture削除の初回は、作成者userより課題を後に削除する順序でSQL失敗。依存する課題/割当を先に削除する順序で再実行し、作成IDの利用者/学校/演習/実行/課題/割当の残存0を確認。既存利用者は変更せず、専用DB/grantも除去し0件を確認した。

## 2026-10-03: 授業演習のWeb・画面接続と設定共有

- TDD初回は未実装ExerciseForm/サイズ例外でcompileTestJava失敗。実装後、専用DB検証と関連回帰の45件は成功・失敗0・skip0、WAR成功。
- 結果型へのパッチは既存のコンストラクターを省略した形を期待して一部のみ適用された。該当型を確認し、未適用部分だけ修正してビルド/DB再読込テストを完了。
- 共有ブラウザーの通常clickは不可視ページのvisible/stable待ちでタイムアウト。requestSubmit/dispatchEventへ切替して実HTTP/UI処理を確認。waitForFunctionの既定描画待ち、期待保存文言/URL globの誤りも検証側で修正し、再読込・課題保存/実行・ごみ箱/復元を確認。アプリの成功失敗とは区別する。
- 実400/409と注入503で入力保持を確認。注入503は実DB/runner障害の検証ではない。実サービスの制限/障害・未確認状態/提出回帰等は計画T012に残す。
- 合成ローカル学校/利用者/課題/履歴を作成IDだけ削除し、課題テーブル件数を復元。専用テストDBの利用者/領域/実行0件を確認後にDB/grantを除去した。

## 2026-10-03: 授業演習実行と既存エディターのコード共有

- TDDの初回は未実装runCode/結果型の確認に加え、新規テストをraceヘルパー内へ挿入した配置誤りでcompileTestJava失敗。ヘルパーを閉じてクラス直下へ移動して解消。
- 共通executeTimedも最初の編集で既存execute内に入りcompileJava失敗。クラス直下へ移動後に再ビルド成功。
- 実隔離runner/専用DBと関連回帰の最終40件は成功・失敗0・skip0。合成入出力/ValueErrorの実行とDB保存、保存内容/版と課題データの不変を確認。専用DB/fixture/grantは除去。HTTP/ブラウザー操作と実timeout/出力上限は未確認。

## 2026-10-03: 授業演習のDAO・保存・ごみ箱/復元

- TDD初回は未実装StudentExerciseControl/競合/対象なし例外によりcompileTestJava失敗。実装後の専用DBテストは成功。
- 設定取得の既存DAOをそのまま呼ぶと領域トランザクション中に接続をもう1本借りるため、同じConnectionを利用するoverloadを追加。元のメソッドは維持し、設定DB取得と関連テストを確認。
- 専用DB移行は既存V11の必要権限に合わせて移行時のみ管理ユーザーを使用し、通常テストはアプリユーザーで実行。最終34件成功・失敗0・skip0、WAR成功。専用データ/DB/grantは除去。演習実行・HTTP/画面の確認は未実施。

## 2026-10-03: 授業演習の初回基盤バッチ

- TDDの初回はStudentExerciseInput等の未実装型によりcompileTestJava失敗。型作成後に解消。
- パス1000文字の境界テストは親パス要素自体が255文字超のfixtureになっていたため失敗。各要素を255文字以内に分割した同じ1000/1001文字境界で再検証し成功。
- 専用DBのアプリユーザー移行はV11のトリガー作成でMySQL 1419（管理権限必須）により停止。今回作成したDBだけを再作成し、移行時のみ管理資格情報を標準入力で受け渡して成功。V11・全体DB設定を変更せず、テストは通常アプリユーザーで実行。
- 初回DB fixtureは学校なし生徒を作りV11の学校必須制約で失敗。合成学校を同一rollbackトランザクション内に追加して再検証し成功。
- 最終入力/スキーマテスト9件と認証/評価関連回帰19件は失敗0・skip0。V12ローカル適用/validate成功。専用fixture/DB/grantは除去。授業演習DAO・Web接続は未実装。

## 2026-10-03: 管理者プロトタイプのタブ統合

- 初期の範囲読取で教師管理HTML末尾の行数を超過。必要な末尾だけを確認して継続。
- ブラウザー検証のcheckbox.checkがvisible/enabled/stable待ちでタイムアウト。チェック状態とchangeイベントをページ内で設定し、編集保存を確認。検証途中で共有ページIDが使用できなくなったため、新しい共有ページで継続した。
- Bootstrapモーダルのshow直後、アニメーション完了前に閉じる/キャンセルを実行すると無視されて待機がタイムアウト。shownイベント／遷移完了状態を待つテストに変更し、作成・確認・キャンセルを再検証。閉じるセレクターが2要素に一致したstrict-mode違反は明示した1要素に絞って解消。
- 新しい編集/作成/履歴モーダルでフォーカスが残ったままaria-hiddenを付ける警告を観測し、ページ内モーダルのhide時に内部フォーカスを解除する処理を追加。共通feedbackモーダルの既知フォーカス警告は継続観測したが、共有資産は今回変更しない。
- 教師作成・編集・検索・履歴、学校変更可否・登録/編集・確認キャンセル後の入力保持、旧URL移動、375pxで横はみ出しなしを確認。学校操作はページ内サンプルのみ、教師操作は従来のlocalStorageのみ。テスト教師と権限変更は復元。本実装・DBは変更していない。

## 2026-10-03: パスワード変更後の遷移・完了通知・履歴

- 存在しないAuthenticationControlTestだけをGradleに指定してテスト未検出で失敗。実在のPasswordPolicyTest/PasswordHasherTestへ切替して成功。以前の複数セレクター付き実行の成功はAuthenticationControlTestの存在・実行を意味しない。共有feedbackの範囲読取も行数超過となり、必要な関数だけを検索して継続。
- 初回の実HTTP検証ではホームへ遷移したが通知スクリプトがなく、通知待ちがタイムアウト。実行中のServlet反映が不完全だった可能性があり、アプリサービス再起動・HTTP200確認後に合成データだけを強制変更状態へ戻して再検証。両遷移と1回の通知を確認。
- 変更成功がlogin_historyだけに保存され、アカウント情報のcredential_historyが空であることを発見。パスワード更新と同じトランザクションで成功履歴を保存するよう修正。DAOの新メソッドを既存メソッド内に誤挿入してcompileJava失敗となったため、正しいクラス直下へ移して再ビルド成功。
- 最終検証では強制変更→ホーム、任意変更→アカウント情報、両通知、DB-backedの初回変更/通常変更履歴、再読込で通知非再表示を確認。全体test build成功。一時ユーザー/学校/履歴は削除し、利用者用テストアカウントは変更していない。

## 2026-10-03: パスワード変更フォーム改善の検証

- 最初の探索で実装フォーム/スクリプトをpasswordという名前で検索し、存在しないパス・プロトタイプ末尾の範囲指定エラーとなった。実際のchange-password.jsp・共通password-visibility.jsと実ファイル行数を確認して継続。アプリ変更失敗ではない。
- 直接POSTでの正常更新後、元の画面からログアウトしたため、成功時にローテーションされたCSRFトークンと古いフォーム値が一致せず403となり、期待URL待ちがタイムアウトした。最新の変更画面をGETして新しいCSRF付きフォームからログアウトすると成功。仕様どおりの拒否であり、CSRF検証は緩和していない。
- 再検証では、過長パスワードの具体的な長さ違反、生HTMLのJSなし送信可否、更新後パスワードでの再ログイン、全入力条件・モバイル表示・既存ログインの表示切替を確認。合成テストユーザー/学校/履歴は除去し、ユーザー用の2テストアカウントは変更していない。

## 2026-10-03: 利用者ID統一の検証ランナー

- 汎用runTestsはJavaテストを検出しなかったため、既存Gradleランナーへ切替。`docker compose exec -T app gradle test --tests entity.StudentAccountDetailsTest war --warning-mode all`は成功。
- 文書の範囲指定とプロトタイプhomeの探索で存在しない範囲・パスを指定した。関連する既存account・共通ナビを確認し、架空のホーム画面は追加していない。
- 認証UI検証のPlaywright clickがvisible/enabled/stable待ちでタイムアウト。ログインフォームのrequestSubmitに切替えて実HTTPログインを確認。
- 検証URLを`/student/account`と誤指定し404、期待値assertが失敗。Servletの正式な`/student/account/account`で再検証して、生徒ID・本人/教師履歴・旧コード/表示名非表示が成功した。404画面の未解釈JSTLとリソース404も観測したが今回のID変更対象外で未修正。
- ブラウザーのpage.request.getはStorage.getCookies未対応で失敗。ページ内fetchへ切替後、検証コードでFetch Response.statusを関数として呼びTypeErrorが発生した。statusプロパティに修正し、教師ログイン・教師ID表示・生徒ページへの403・ログアウトを確認。
- 専用DBの4件とEntityの1件は成功。全体54件中42件成功、12件はDB/実APIゲートでskip、失敗0。テスト用ユーザー・学校・履歴・専用DB/権限は除去し、ローカルプロトタイプサーバーも停止済み。

## 2026-10-03 18:48 JST: 生徒アカウント表示検証用所属の未登録

- 合成生徒の所属INSERTで日本語クラス名を条件にしたが、mysqlクライアントの文字コード指定がなく対象0件となり、ブラウザーの所属表示assertが失敗。実装はDBどおり「所属情報は登録されていません。」と表示していた。
- UTF-8指定で今回の合成生徒だけに所属を追加し、1件を確認。再検証でDBの学校/クラス表示、レベル名非表示、レベル1の灰色disabledボタンと初回変更「不要」、レベル2の有効変更リンクを確認。test-studentの既存所属は正しく登録済みで、変更不要だった。

## 2026-10-03 18:42 JST: 学校管理のページCSS未適用

- ユーザーの表示確認で指摘。学校管理JSPにページCSS指定がなく、共通テンプレートでも生徒画面の分岐内でのみページCSSを読んでいたため、管理者プロトタイプのデザインが未適用だった。
- 対応: 明示指定したページCSSをロールに関係なく読み込むよう修正し、学校管理へ既存管理者デザインの必要部分を適用。
- 再検証: WARビルド・git diff --check成功。合成adminで実JSPのCSSリンク1件・HTTP200、パネル角丸16px・ヘッダーflexのcomputed styleを確認。375px幅で横はみ出しなし。合成adminとログイン履歴は削除済み。

## 2026-10-03 18:33〜18:36 JST: 学校管理の最終再検証

- V11のトリガー作成権限エラーは、移行時のみ管理権限を使用して解消。アプリの通常権限のまま学校DBテスト3件・入力テスト2件が成功。実開発DBへの移行・検証も成功。
- ConsentDatabaseTestの後片付け位置を修正し、専用DB再作成後の1件は成功。通常の全体テスト/ビルドも成功（52件、失敗0、ゲートでskip11件）。
- UTF-8対応後の追加UI検証で、Playwrightのclickが「visible/enabled/stable待ち」で2回、デフォルトwaitForFunctionが1回タイムアウト。描画待機の問題が疑われるが原因は未確定。画面にフォーム/リンク/モーダルがあることを確認し、ページのrequestSubmit・モーダルボタンclick・URL遷移待ちで実HTTP保存処理を継続した。日本語の学校名はDB保存・再表示とも一致し、375px幅でページの横はみ出しがないことを確認。
- 一時DB3個・権限、最終日本語確認の合成admin/学校/監査/ログイン履歴も除去。通常アプリに管理DB資格情報は設定していない。残る既知事項は共通モーダルのフォーカス警告と、今後の教師向け生徒登録UI等。

## 2026-10-03 18:27〜18:30 JST: 学校管理のUI・回帰検証

- UI検証スクリプトのNode側で`URL`グローバルが使えず`ReferenceError: URL is not defined`。学校登録自体は成功していたため再登録せず、作成済み合成学校IDを使って更新検証を継続した。アプリの障害ではない。
- 認証済みブラウザーで合成adminの登録・キャンセル・名前変更・生徒登録前のレベル変更・登録後の変更禁止と名前更新を確認。意図的な古い版・確認なし・レベル改ざんは400、CSRF不正と教師/生徒の管理URLアクセスは403を期待どおり確認。既報の共通モーダル終了時フォーカス警告は残存。
- ConsentDatabaseTestの学校fixture後片付けで、プロフィール削除前に学校を削除する誤ったループ位置により追加FK違反が発生（1件失敗）。テスト本体ではなく後片付けの失敗。学校削除を既存削除ループの後へ移し、専用DBだけを再作成して再検証する。評価DB回帰検証は成功（実APIゲートは無効）。
- 合成UIデータは監査と保存後のDB値を確認した後、今回作成したIDだけを指定して削除。既存学校・生徒のパスワードは変更していない。

## 2026-10-03 18:25 JST: 学校管理V11のトリガー作成権限

- 手順: 専用DB `ppe_school_test_b1016b19_20261003` で通常のアプリDBユーザーにより `gradle flywayMigrate test --tests control.admin.SchoolDatabaseTest --tests entity.SchoolInputTest --rerun-tasks --warning-mode all`。
- 期待・結果: V11移行とテストを期待したが、トリガー作成時にSQL State HY000、MySQL error 1419（バイナリログ有効時のSUPER権限不足）。V11のDDLは部分適用、テスト未実行。実開発DBは未変更。
- 方針: アプリ用ユーザーへのSUPER付与や共有MySQLのグローバル設定変更は行わない。専用テストDBを再作成し、移行時だけDB管理資格情報を使って再検証する。運用では適切な移行用権限を持つユーザーが必要。
- 関連: エディターのテスト用fixture後片付けは追加FKに合わせプロフィール削除後に学校削除する順序へ変更。汎用runTestsはJavaテストを検出せず、既存Gradleランナーへ切り替え、SchoolInputTestは成功。

## 2026-10-03 18:18 JST: 学校管理仕様の文書更新

- 手順・結果: 複数文書へのapply_patchの末尾で、実装契約の見出しが想定と一致せず部分適用となった。仕様・状態・DB・画面遷移の4文書は更新済み、実装契約は未更新。
- 対応: 実装契約の冒頭のみを確認し、未適用の変更を別パッチで反映。既に適用した変更は繰り返さない。コード・DBには影響なし。

## 2026-10-03 17:58 JST: 同意ダイアログ表示改善のブラウザー検証

- 対象・手順: 共通feedbackの構造化detailsと同意変更フォームを、実データを送信しない合成フォームで検証。
- 期待・初回結果: キャンセルで送信0件・ボタン再有効化を期待したが、ページ内からclick後に400ms固定待機する検証で`Cancellation failed`。ダイアログが表示されたままだった。原因は未確定で、実装の不具合と断定しない。
- 対応・再検証: ページ再読込で以前の合成フォームのイベント処理を除去し、Playwrightのクリックと状態条件待機に変更。キャンセルで送信0件、確定で送信1件・確認フラグyesを確認。4項目の見出し・研究利用本文の太字、従来の文字列details、HTML文字列のエスケープ、撤回の重要文言、375px幅で横方向のはみ出しがないことを確認。検証後はページを再読込し、合成フォーム・送信差替えを除去。
- 未解決: 既報のモーダル終了時の`aria-hidden`フォーカス警告を再観測。実際に認証した同意画面の操作全体は手動確認待ち。DB変更・外部API呼び出しはなし。

## ERR-20261003-001: 評価保存エラーとAI出力エラーの混同

- 発生日: 2026-10-03（JST、UTC+09:00）。
- 対象: 工程8 T002、評価ワーカーの応答保存・評価完了処理。
- 実行: `docker compose exec -T app gradle test --tests control.evaluation.EvaluationWorkerTest --warning-mode all`
- 期待: 合成された保存時の`IllegalArgumentException`をDB側の失敗として処理し、再API呼び出しを行わず、評価の失敗状態を記録して例外を呼び出し元へ通知する。
- 結果: 回帰テスト`storageRuntimeFailureIsNotRetriedAsInvalidAiOutput`が失敗（4件中1件）。従来コードは保存例外を不正AI応答と同じcatchで処理し、再試行してしまう。
- 影響: 保存障害時に不要なAPI再呼び出しと応答の重複記録が起きる。その他の実行時保存例外では評価が処理中のまま残り得る。
- 対応: AI呼び出し・出力検証とDB保存を別の例外境界に分離する。保存時の実行時例外も失敗状態を記録した上で上位へ通知する。失敗記録時は既存のretry_countを減らさない。
- 再検証: `docker compose exec -T app gradle test --tests 'control.evaluation.*Test' build --warning-mode all` 成功。さらに専用DBで評価ワーカー・HTTPクライアント・DB結合テストの計17件が成功（失敗0、skip0）。保存失敗でAPI再試行しないこと、重複完了時のトランザクションrollbackを確認した。
- 補足: バグ再現のために追加したテストの初回失敗。実API呼び出し・実データ利用はなし。

## 2026-10-03 17:30〜17:31 JST: 実API・DB結合検証

- 実行:
  `docker compose exec -T -e DB_NAME=ppe_evaluation_test_b1016b19_20261003 -e EVALUATION_DB_TEST=true -e GEMINI_API_SMOKE_TEST=true app gradle test --tests control.evaluation.EvaluationDatabaseTest --tests control.evaluation.EvaluationWorkerTest --tests control.evaluation.GeminiEvaluationClientTest --rerun-tasks --warning-mode all`
- 結果: 成功（17件、失敗0、skip0）。Gemini Interactions API、モデル`gemini-3.7-flash`、合成データのみ。実APIの応答検証・DB保存・再読込が成功し、実APIについて失敗やretryは観測されなかった。単独の実API・DB結合検証も先に1回成功している。
- 異常系テストでは、空の出力、合成provider失敗、重複完了を意図的に発生させた。いずれも期待した失敗状態・retry_count・rollbackを確認し、実APIの障害とは区別する。
- 既存のGradle `WarPluginConvention`非推奨警告は出るが、ビルドは成功。今回の変更に起因する警告ではない。
- テスト専用DBで作成した行は検証後に削除し、users/tasks/evaluations/requests/snapshots/responsesが0件であることを確認。既存の開発DB・実際の生徒データは変更していない。
- 後片付け: セッションが作成した専用DBと、そのDBだけに付与した権限を除去。`information_schema.schemata`で残存0件を確認した。
- 最終確認: `docker compose exec -T app gradle test build --warning-mode all`および`git diff --check`成功。実API・DBテストは通常の全体テストでは明示フラグがないためskipする。

## 2026-10-03 17:48 JST: 同意変更ダイアログのブラウザー警告

- 操作: 実際の同意記録を変更しないブラウザー検証用フォームで、共通確認ダイアログをキャンセルして再度確定した。
- 結果: キャンセル後の送信0件、確定後の送信1件・確認済みフラグを確認。ブラウザーはダイアログを閉じる際に、フォーカスが内部に残った要素への`aria-hidden`適用を警告した。
- 影響・対応: 今回の同意変更専用JavaScriptではなく、既存の共通feedback / Bootstrapモーダルのフォーカス管理に関する警告。機能検証は成功したが、アクセシビリティ確認の残件として記録する。警告解消は未確認。

## 2026-10-06 JST: 課題改訂ブラウザー・worker runtime受入

- 対象: 専用Compose project `ppe-task-revision-acceptance`、schema `ppe_teacher_task_test_revision_20261006`、HTTP port `18082`。合成教師・課題・プロンプトのみ使用し、共有8080/DB、Gemini実API、実生徒データには触れていない。
- 初回migration失敗: `flywayMigrate flywayValidate` がV11 routine作成時にMySQL error 1419（binary logging下でroutine creator設定が必要）で失敗。専用DBに `SET GLOBAL log_bin_trust_function_creators=1` を設定して同じschemaを再実行したところ、部分適用DDLによりFlyway validationが失敗した。部分適用schemaへのrepairは行わず、専用Compose projectだけのvolumeを削除・再作成し、DB起動直後に同設定を入れてから実行した結果、V1〜V19 migration/validate成功。
- fixture: `TEACHER_TASK_DB_TEST=true` と `TEACHER_TASK_BROWSER_FIXTURE=true` を渡した `createsRetainedSyntheticTeacherFixtureForIsolatedBrowserAcceptance` は成功。隔離認証browserで合成課題を公開し、改訂案を作成、新しい共通プロンプト版を保存してから改訂を公開した。プロンプトは合成文で保存し、Geminiを呼び出さず専用DB上でconfigured状態を準備した。再公開後は旧task `requires_update` / 旧assignment `archived`、新task `published` / 新assignment `scheduled`。
- JavaScript: `/js/teacher/task/task.js` を隔離アプリからHTTP 200で取得（18,141 bytes）し、Chromium内 `new Function(source)` で構文コンパイル成功。画面上の公開確認dialog、フォームsubmit、redirectも動作確認した。Node.js CLIは未導入のため `node --check` は未実施。
- worker runtime: アプリ停止中に期限到来させた予約assignmentを、再起動後のworkerで `scheduled -> published` に回収した。次に隔離DBの合成assignmentを再度scheduledにセットし、アプリ稼働中の次pollでpublishedへ遷移し、system `publish_scheduled`監査の件数増加を確認した。確認ループは最大90秒待機で、正確な壁時計経過時間を記録していないため、60秒以内の厳密な遅延条件を満たした証拠とはしない。
- 検証手順上の失敗: 最初のDB照合queryが実在しない `assignment_id` 列を指定してMySQL error 1054となった。テーブル定義の `task_class_assignment_id` を使うqueryへ直して状態を確認した。Browserの最初のCodeMirror入力は非表示textareaへの `fill` がtimeoutしたため、表示中editorをクリックしてキーボード入力し、保存成功を確認した。いずれも製品コードの不具合ではない。
- 後片付け: `docker compose -p ppe-task-revision-acceptance down -v` により当該projectのapp/DB/runner、volume、networkを削除し、port 18082にlistenerがないことを確認。共有container/projectは停止・変更していない。
- 未確認: 期限切れ境界と実workerでの既存提出/評価/コードログ保持。DB統合テストでの既存状態検証とruntime確認を混同しない。

## 2026-10-06 JST: 課題期限切れ境界・履歴保持runtime受入

- 対象: 専用Compose project `ppe-task-expiry-runtime`、schema `ppe_teacher_task_test_expiry_20261006`、DB port `13318`、HTTP port `18083`。専用合成データだけを使用し、共有DB/8080、実利用者データ、Gemini実APIには触れていない。
- compile: `docker compose -p ppe-task-expiry-runtime run --rm --no-deps app gradle testClasses --no-daemon --console=plain --warning-mode all` は初回、期限待機helperの `InterruptedException` 宣言漏れで失敗（exit code 1）。テストメソッドに例外宣言を追加し、同コマンドを再実行して成功（exit code 0）。
- migration: 専用MySQLだけに `SET GLOBAL log_bin_trust_function_creators=1` を適用後、`gradle flywayMigrate flywayValidate --no-daemon --console=plain --warning-mode all` が成功（exit code 0）。FlywayはV1〜V19を適用・検証した。routine内でV19補助procedureが未作成とのMySQL noticeが出たが、migration/validateは成功。
- DB境界テスト: `TEACHER_TASK_DB_TEST=true` をコンテナにも渡し、`gradle test --tests control.teacher.TeacherTaskDatabaseTest.publicationWorkerDoesNotExpireBeforeDeadlineAndExpiresAtTheBoundary --no-daemon --console=plain --warning-mode all` を実行して成功（exit code 0）。期限前は `published` を維持し、DB時刻の期限到来後に `expired` へ1回だけ遷移し、二重実行では追加遷移・監査を作らないことを確認した。
- 保持fixture: `TEACHER_TASK_DB_TEST=true` と `TEACHER_TASK_EXPIRY_HISTORY_FIXTURE=true` をコンテナへ明示し、`createsRetainedSyntheticExpiryHistoryFixtureForRuntimeAcceptance` を実行して成功（exit code 0）。期限処理前の合成件数は参加1、提出1、評価1、コードログ1、期限切れ監査0。
- startup/runtime: `appRun` でTomcat 9.0.118がport 8080（host側port 18083）で起動し、`GET http://127.0.0.1:18083/` はHTTP 302を返した。割当期限をDB現在時刻+8秒へ設定し、稼働中workerの次pollで `published -> expired`、system `expire_assignment`監査1件を確認した。期限切れ後も参加1、提出1、評価1、コードログ1のまま変化しなかった。
- 受入範囲: workerによる期限処理と合成履歴保持は確認した。稼働中workerの正確な60秒以内の処理遅延は壁時計計測していないため、SLA条件は未確認。アプリログにworkerのSQL/処理失敗は見られなかった。
- 手順上の失敗: 初回のDB照合コマンドはshell/SQL引用符の誤りでMySQL `Unknown command '\0'`（exit code 1）。文字列をhex literalで指定する照合queryへ切り替え、成功した。製品コードの不具合ではない。
- 後片付け: 受入記録後、専用Compose projectを停止しvolume/networkを削除する。host port 18083と専用MySQL port 13318のlistenerが残っていないことを確認する。

## 2026-10-06 JST: 課題期限処理60秒以内のruntime計測

- 対象: 新規の専用Compose project `ppe-task-expiry-sla`、schema `ppe_teacher_task_test_expirysla_20261006`、DB port `13319`、HTTP port `18084`。V1〜V19を専用DBへ適用・検証し、合成fixtureだけで実workerを起動した。共有DB/8080、実利用者データ、Gemini実APIは使用していない。
- 計測方法: workerの起動時初回処理後に合成割当の `due_at` をDB現在時刻から70秒後に設定。MySQLの `NOW(6)` と期限DATETIMEとの差を250ms間隔の状態pollで観測し、状態が `expired` となった最初の観測時に遅延時間を記録した。
- 結果: 期限 `2026-10-06 02:38:54`（DB時刻）に対し、実workerの `published -> expired` を期限到来から **21.646秒** で初回観測した（60秒以内）。その時点の合成件数はparticipation 1、submission 1、evaluation 1、code_log 1、`expire_assignment`監査1。後続照合でも状態と件数を再確認した。
- 起動: `GET http://127.0.0.1:18084/` はHTTP 302、Tomcat 9.0.118はport 8080（host 18084）で起動。worker SQL/処理失敗ログは確認されなかった。
- 注意: 計測結果はこの専用runtimeの実測値であり、最大遅延の一般保証を単一試行のみで証明するものではない。ただし、60秒固定pollの実周期で当該期限を跨いだ確認として受入条件を満たした。
- 後片付け: 専用Compose projectのapp/DB/runner、volume/networkを削除し、port 18084/13319のlistenerがないことを確認する。

## 2026-10-08 JST: Gemini代替モデル・Pro高思考の運用受入

- 最終結果: 本人承認のGemini 3.1 Pro Preview高思考で教師実API縦断2run、通常評価、認証browserの実生成・保存/再読込が成功。1要求180秒・1工程12分・最大3試行、中断復旧15分へ整合し、8080用WAR/当該appの再起動とteacher/student login各200を確認。詳細コマンドと測定は[教師プロンプト計画](./feature-plans/teacher-prompt-ai.md#pro高思考の最終受入2026-10-08)。
- provider失敗: 初期候補3.8の教師縦断は6要求（200×2/503×4）でpreview未完了、exit1。2.5 Proは最小要求404「新規利用者には利用不可」で再送せず。Proの保持fixture runでも評価例の初回503があったが、承認済みの長い待機/再送内で成功した。過去の3.7高需要/timeoutを最終成功で消さない。
- TDD初回: Pro既定assert、時間上限assertはそれぞれ意図したred後に修正。Lite縦断初回はAPI3件成功後、確定状態をconfiguredと期待したtestとcleanupのFK不足で失敗した。期待をversioned/active/model再読込へ修正し、fixtureに属する評価理由/evidence等も削除して再run成功。Liteの疎通成功を採点品質保証とせず、品質優先の指示で既定採用を撤回。
- 診断/通常回帰の混在: 2.5 Pro診断runは404に加えて、diagnostics=trueで実行した秘密情報非表示の通常単体assert2件が失敗した。診断用文言を通常結果と混ぜた検証条件の問題として分離し、diagnostics=falseの最終評価回帰は35成功/12gate skip/失敗0、exit0。privacyのassertや秘匿処理は弱めていない。
- Gradle競合: 通常評価初回は共用validation-homeのjournal lockで起動前失敗、外部要求0。直列再runは1成功・exit0。browser serverはprivate Gradle homeコピーと専用build/project cacheへ隔離した。
- server起動: 初回18082起動はGrettyのstdin EOFで即終了し、コマンドexit0でもreadiness失敗。stdinを開いて保持する起動へ修正し、running/login200を確認。起動コマンドのexit0だけを稼働成功にしない。
- browser手順: 最初の古いref入力timeout後は現在DOMの合成教師欄から認証成功。新規版フローの初回は実行コンテキストに`URL`がなく失敗、次は既存shared feedbackの無名dialog/「実行する」buttonと不一致でtimeout。selectorsを実DOMへ整合して再run成功。いずれも生成前なので追加API要求0。評価例フローは生成/保存成功後、状態badgeも含むsection全文の一致assertが設定済み遷移で失敗した。生成結果/理由だけを比較し、保存済み版のreloadと旧適用版不変を再確認して成功、再生成は行っていない。
- SQL照合手順: 存在しないjob/task/prompt/preview列の照合queryはMySQL 1054で失敗した。`SHOW COLUMNS`で実列を確認し、`reevaluation_status` / `prompt_status` / `evaluation_examples_status` / `target_status`を使用してDB状態を再確認した。製品処理の500や保存失敗とは区別する。
- 最終教師回帰は保存XMLで34成功/4skip/失敗0（38件、exit0）。以前のメモの41件集計は誤りで、XMLを正とする。評価回帰47件と共通テストが重複するので、独立テスト数として合算しない。既存のGradle非推奨/SLF4J notice、エディターのGson解決問題は実Gradle compile/WAR成功と区別した。
- 生成POSTは今回25（200:19/503:5/404:1）、直前の原因調査を含む累計42（200:22/503:16/404:1/timeout:3）。GET計2は別。実生徒データ・APIキー値・実利用者passwordを出力していない。課題10/11の主要metadata hashは不変。大人数負荷・品質校正・本番予算/quota・実データ送信は未検証/別承認。
- 後片付け: 18082専用appを停止した際、stdin保持pipelineが停止猶予195秒後にexit137となった。受入中のAPI/DB失敗ではなく、隔離server終了時の結果として記録する。listener除去後、今回作成した2schema/そのgrantだけを削除し、残存各0、共有8080/無関係18081の稼働と課題10/11の主要metadata hash不変を再確認した。

## 2026-10-10 21:55–22:04 JST: 修正release取得後の読み取り検証手順の誤り

- 対象: fixed SHA `043d875d760a080b8cff552b1f81ee8d47e5dc31`のECR公開/EC2 READY確認。
  GitHub publication `38053545658`と既存EC2取得adapterはともにexit0で成功。
- ECR確認: `aws ecr describe-images`の手入力tag形式が実workflowと異なり、
  `ImageNotFoundException`（exit254）。既存publisherの
  `sha-SHA-gha-RUN-ATTEMPT`を確認し、正しいtagで8 repositoriesを照合して成功。
  追加dispatch/再Push/上書きはしていない。
- report確認: artifact rootの`release.json`を`jq`で参照してfile not found。
  artifact実構造の`assets/release.json`を既存schema validatorへ渡しexit0。
- source照合: SSM `8cca5bde-23a2-4983-b81b-11493f7ee60d`はtrusted manifest hash未指定でexit1。
  `b25dec48-6da2-4cc4-9b5a-e31b4d218e71`は既存helperへsource directoryを渡したため
  「Release source differs from its complete hash manifest」でexit1。
  helperはrelease directoryを受けるAPIであり、実source改変ではなかった。
  authenticated publication manifestのhashを固定し正しいdirectoryで再実行した
  `c33f3191-e0a4-48e0-ac12-cf000193fd77`はexit0、全source/7 image/旧サービス/DB/Volume/stateを再照合。
- 運用参照調査: 過去の要約にあった未存在`/var/lib/ppe/backup-current`の
  `readlink`/`ls`を使ったSSM `17075d7f-7845-410f-83b4-6d99d23c0ca3`（exit1）、
  `1f68ad5c-106a-40a7-8ea6-33c9f91a5e23`（exit2）は調査途中で停止。
  推測名`ppe-tls-renew.timer`はinactiveだったが、実TLS更新timerではない。
  authoritativeな運用envと全timer一覧へ切り替えた
  `b6253e67-d263-4cc1-a0da-e1dd9ce4c684`はexit0、旧backup/TLS release pinと
  `certbot.timer`の次回/前回scheduleを確認。TLS更新を実行したわけではない。
- 影響: いずれも読み取り検証wrapper/ローカル照合の手順誤り。
  deploy/rollback/migration/コンテナ再起動、DB/Volume/env/Secretsの変更はない。
  失敗結果を受入成功へ読み替えず、修正後の個別実測を記録した。
- 未解決: 新SHA→旧SHAのrollback reference配置と本番update/認証後受入は別承認待ち。
  詳細は[再公開・取得受入](./ecr-manual-deployment.md#修正済みreleaseの再公開ec2取得受入2026-10-10-2204-jst)。

## 2026-10-10 22:12 JST: 修正release updateのapp launcher読み取り拒否

- 対象: `043d875d760a080b8cff552b1f81ee8d47e5dc31`、既存deploy-release.sh update、
  明示承認に基づく1回のみの実行。SSM `587c55c7-d249-4d32-8620-fce6133fc156`。
- 期待: 新5サービスhealthy、DB維持、公開HTTPS、current/state published。
- 実際: 22:09:23–22:12:31 JST（188秒）でexit1、app_started。
  UID10001のappログは
  `sh: 0: cannot open /usr/local/bin/ppe-app: Permission denied`。
  起動launcherへの読み取り拒否。ファイル所有/modeの詳細は未確認であり、
  0600等の具体的modeを今回のログだけから断定しない。
- 影響: 新appはrestarting/unhealthy、生徒・教師loginはHTTP504。
  新RunnerはUID65532でhealthy、broker healthy、旧nginx/DB healthy。
  nginxの公開bindは0.0.0.0:80/443。前回Runner権限障害とlocalhost bind障害は
  今回確認した範囲では再発していない。
- 保全: DB container/起動時刻/image/Volume/Flyway hash、既存release/Secrets不変、
  migration_attempted=no。fresh encrypted S3 backup/固定Version/checksum/receipt成功。
  current marker旧、state新release app_started。自動復帰なし。
- 対応: 読み取り調査のみ。再deploy/明示rollback/DB復元/root起動/権限変更をせず停止。
  AWS waiter初回はMax attempts exceeded（同じcommandがInProgress）だったが、
  新commandでdeployを再実行せず同一commandの最終exit1を確認した。
- 再検証: SSM `dda872b2-83d9-4480-8aed-c7c8eab2bdb4`は保全/HTTP確認exit0、
  `703dcc4d-e2b0-4325-99bb-033f58d6c76e`は安全に抽出したappエラー、
  fresh receipt再照合、公衆bind確認exit0。製品のdeploy失敗を成功扱いしない。
- 未解決: 本番サービス停止継続、旧サービスへの復旧は別承認待ち。
  app修正/再deploy、認証後教師受入・合成previewは未実施。Phase 2未完了。

## 2026-10-10 22:19 JST: 2回目update後の緊急復旧完了

- 承認: 22:15:56 JST、旧releaseのアプリ系サービス復旧のみ。
  修正済みoperationsの既存rollbackを実行した
  SSM `9474f560-b72c-4117-a393-09c35f0d19a4`はexit0、
  22:16:43–22:18:58 JST、135秒。
- 結果: 旧5サービスhealthy・旧Image ID、current/state rollback published一致、
  nginx0.0.0.0:80/443維持。EC2からhealth/login4件HTTP200、Mac両login200、
  browser両ログインフォーム表示。新imageは稼働していない。
- 保全: DB ID/StartedAt/image/Volume/Flyway hash、release/Secrets不変。
  DB操作・migration・復元、IAM変更、root回避、新release修正/再deployなし。
  maintenance開始22:09:58→公開復旧確認22:18:59の記録区間は9分1秒。
- 実行手順エラー: 最初のSSM send-commandはcommentが100文字を超え
  ValidationException（exit254）でAWS側受付前に拒否された。
  commentだけを短縮して送信成功。最初の拒否では復旧commandは実行されていない。
  標準waiterはInProgressでMax attempts exceededとなったが、
  同一commandの完了を確認し、rollbackを重複実行していない。
- 状態: サービス復旧済み。新app launcher Permission deniedは未修正、
  Phase 2未完了。詳細は[復旧記録](./ecr-manual-deployment.md#2回目update後の旧サービス復旧2026-10-10-2219-jst)。

## 2026-10-10 22:22–22:25 JST: app権限修正のローカル回帰

- 原因再現: 修正前mainのsourceを0600で実build。UID10001の実entrypointは
  `cannot open /usr/local/bin/ppe-app: Permission denied`、exit2。
  root:root0600のlauncher/Tomcat2設定、親0755を実imageで確認。
  source umaskを緩めずCOPY側で0644を固定した。
- 初回統合test: 専用local Docker DBのV11 migrationがMySQL1419でexit1。
  production migrate.shが行う一時SUPER grant/revokeをtestが再現していなかった。
  同じgrant/revokeを専用合成DBに限定して追加し、次の実runでV1〜V23適用/
  validate、UID10001 app/UID65532 runner/実5サービスhealthy・両HTTPS formが成功。
  production migration/DBには接続していない。
- 初回test資産名: Compose configの正規化済みnetwork/volume名が
  `ppe-production_*`として残った。初回ログでは新規作成され、cleanupで除去済み。
  次回以降はconfigの明示nameを削除し、専用`ppe-sim-app-*`projectへ限定した。
  localの当該volume/network残存0を確認。EC2のproduction資産とは別daemon。
- Docker Desktopの無効optional CLI plugin警告は既存の非fatal警告。
  実Docker daemon/build/healthは機能している。失敗を成功扱いせず原因と再検証を記録する。

## 2026-10-10 22:39 JST: PR #57の統合test準備漏れ

- 必須Shell CI run38056149799でapp-startup-test exit1。
  Brokerは`A pre-pulled runtime image without volumes is required.`で起動拒否。
  ローカルでは既存python:3.12-alpine cacheがあり見えなかったが、クリーンCIにはない。
- testがproduction Composeのruntime imageを事前取得していなかった。
  正規化ComposeのBroker設定からruntimeを読み、既存Docker pullで明示取得する。
  Broker検証・必須CI・権限は緩和しない。専用test資産は失敗時もcleanup済み。
  再検証は同じ20 suites必須CIで行う。本番には変更していない。

## 2026-10-10 23:09–23:12 JST: ef55 READY追加検証wrapperの修正

- ECR公開run38057866136と取得SSM a766249fは成功、releaseはREADY。
  追加の読み取り検証SSM35fb2a30は`PPE_STATE_DIR: unbound variable`でexit1。
  source-manifest helperが専用証跡directoryを必要とするためwrapperで指定し、
  SSM abd225d2でexit0、7ID/ソース/DB/Volume/履歴/既存資産/backup照合が成功。
- 実image file mode追加調査SSM72f134aaはctrの圧縮layer contentが見つからずexit2。
  Docker29/containerd上ではmanifest/configは残るが、当該圧縮layer blobはない。
  `docker image save`でもmanifest.jsonが参照するlayerを含まず、SSM5f90edb6はexit2。
  boundedなexport一覧調査a5c2c737でmanifest/indexのみのexportを確認した。
  いずれも起動操作ではなくimageの読み取りで、専用一時archiveはcleanup済み。
- 既存のECR Registry helperを使い固定layer digestのblobを読み直し、hash照合後に
  modeを調査する。daemon設定やrunning containerには変更せず、
  検証結果はrunbookへ記録する。失敗した調査をmode検証成功とは扱わない。
- 再検証SSM `c1a642f9-ea4b-4b9a-87e1-4b7e3b0f6996` exit0。
  全layer hash照合後、上位layerのapp launcher/Tomcat設定/runner.pyがroot:root0644、
  app config UID10001と起動コマンドを確認。Registry authと一時layerはcleanup済み。
  [READY受入記録](./ecr-manual-deployment.md#pr-57修正後releaseの公開取得受入2026-10-10-2314-jst)を参照。

## 2026-10-11 03:24 JST: ef55本番updateのnginx起動失敗

- 承認: 2026-10-11T00:11:04+09:00、固定ef55 releaseへのupdate1回。
  AWS SSO認証の期限切れで確認コマンドがexit255となり、update開始前で認証更新を待った。
  認証更新後にaccount・非root読み取り検証成功を確認して進めた。
- preflight SSM02f90bbeはexit0。既存DB/Volume/旧7images、全23SQL一致、
  Flyway validate/info pending0、private recovery reference、専用rollback reference、
  fresh S3 backupを確認。app/Tomcat/Runnerは実10001/65532で読込可能だった。
- update SSM `885ab0ca-fd12-4f02-bb75-77e46d1b2526`を1回のみ実行。
  開始03:20:47、終了03:24:03 JST（196秒）、exit1、stage=nginx。
  期待は5healthy/HTTPS200/current新release。実際は新app/runner/broker healthy、
  新nginx UID101がrestarting/unhealthy、両HTTPSは接続拒否（curl7/HTTP000）。
- 実nginxエラー:
  `open() "/etc/nginx/nginx.conf" failed (13: Permission denied)`。
  公開bindは0.0.0.0:80/443を維持。app UID10001/Runner UID65532は正常。
  nginx Dockerfileの通常COPYがprivate source modeを保持する可能性が高いが、
  当該ファイルowner/modeと親権限の直接調査はまだ行っていない。
  前回統合testの0600入力はapp/tools buildに限定され、nginx buildは通常contextだった。
  nginx側の権限回帰を確認済み・解消済みとは扱わない。
- DB container/StartedAt/ImageとVolume全文・history hash・Secrets・release hashesは不変。
  migration_attempted=no。deployment.stateはupdate/ef55/nginx_started、
  currentは旧0e2bfaeのまま。自動復帰条件はphase index<=40のため今回は対象外。
  apps_restored=no/recovery_attempted=no。再deploy・明示rollback・DB復元は実施しない。
- 停止後のdeployment backupも111284bytesで固定S3 Version/checksum/KMS検証成功。
  data version `RvJAOPqvpQV6Uea81HNGUCZMORwrzV_K`、
  checksum version `KdO1aRbH7NOqvJDFRMNKRRqIlDy2c2l8`、
  manifest version `Fi2eyH3m1QgeZDGfapeyCuFV2vTsPZBO`。
  receipt hash `3fbe0c1cf1dc5fac295459f8a9284b552b486aa6ca236745604f181adc477645`。
- 失敗調査SSM86435120は実証拠を取得後、journal filename誤りでexit2。
  正本deployment-events.logへ修正しSSM3cc73732でexit0、03:25:18 JST時点の状態を確認。
  外部Macからも両HTTPS接続拒否を確認。認証受入・合成preview・教師fixture作成は未実施。
- 必要な復旧は別承認で旧releaseへのapp系rollback。
  現在marker旧→target旧に一致するCURRENT-schema referenceをレビューし、
  既存rollbackの不完全update復帰条件、公開bind、DB pinを確認して使用する。
  ef55→旧referenceをそのまま旧→旧の証拠と偽らない。Phase 2未完了。
- 2026-10-11のドキュメント整理で、横断的な原因仮説と将来の改善A〜Dを
  [技術的負債](./ecr-deployment-debt.md#ecrビルド経路の権限障害と改善保留2026-10-11-jst)へ集約した。
  P0復旧/P1機能追加/P2基盤改善の順で実装保留。nginx直接原因の追加調査・修正は未実施。

### 2026-10-11 07:11 JST: ef55失敗から旧releaseへの緊急復旧成功

- ユーザーの明示承認で、旧`0e2bfae5e23bc512f0b7cf844264d4d45a484ecc`への
  アプリ系復旧のみ実施。AWS認証/account024378233912/東京/指定EC2 SSM Onlineを確認。
- 07:08:27 JSTの事前確認SSM `da91ae53-30b8-4772-bb4b-51dff788a28f` exit0。
  未完了update/nginx_started/migration_attempted=noは前回と一致。
  nginxは同Permission deniedでrestart232回に増加していた。旧7images保持、
  DB container/Image/StartedAt、Volume全文、23Flyway履歴、Secrets/既存資産、
  recovery reference/backup checksum/KMSは一致。新旧23SQLもbyte一致。
- 配置済みef55 operationsのrollback/common/state/config-syncはレビューしたローカルとhash一致。
  既存rollbackのCURRENT旧・target旧・未完了update復帰条件を確認。
  新→旧referenceを流用せず、CURRENT旧→旧の実レビューを
  `/var/lib/ppe/config/rollback-incomplete-ef55-20261011-0706.reference`へ記録。
  hash `029394fc33e62c795244316b637d4c51d66f5b6410624f045a9f2bc61820421d`。
- rollback SSM `bf6a0193-5a1e-472b-9816-99191928c002`を1回のみ実行しexit0。
  開始07:08:52、完了07:11:23 JST（151秒）。
  既存スクリプトがapp/Runner停止、旧broker/Runner/app/nginx再作成、
  nginx設定検査/reload、smoke、state publishを実施。設定ファイル自体は変更しない。
  DB lifecycle/migration/restore、イメージbuild/ECR公開、新release再deployは未実施。
- 事後SSM `d71f0627-b930-4ce8-81d5-82094669ee0b` exit0、07:11:36 JST確認:
  旧5サービスすべてrunning/healthy/restart0、各Image IDが旧release記録と一致。
  app UID10001、Runner UID65532、nginx UID101。bind0.0.0.0:80/443、
  nginx -t成功。新releaseのアプリ系コンテナは旧imageのコンテナへ置換済み。
- DB container `cead40118ed8a27ce90a8f745f7b69ed528355149c7412d2c95985b6e47872d8`、
  Image `77f34eeebbd0f55ecae70cdb7aa45f302bb64b2887ec31efc8d14a2e34597715`、
  StartedAt2026-10-08T08:32:49.719977603Z、Volume全文、履歴hash、
  Secrets/既存release/既存backupは復旧前後で不変。DB restart0。
- current旧、state rollback/旧/previous旧/published/migration_attempted=no。
  失敗update証跡は既存rollbackが保存し、stateを手編集・削除していない。
- 両HTTPS health/loginはEC2側200。外部Macも07:11:37 JSTにlogin200/health status ok。
  共有ブラウザーで両ログイン画面のID/password/form表示を確認。認証操作・業務データ作成はしない。
- 停止時間は連続監視していないため厳密値不明。
  update quiesce開始03:21:21〜外部復旧確認07:11:37 JSTの区間は3時間50分16秒。
  これは保守・障害区間の目安であり、全区間の連続HTTPS停止を実測した値ではない。
- **旧releaseの復旧成功、Phase 2の新release受入は未完了**。
  nginx原因確定/修正、最終ECR image試験、基盤改善は保留し、復旧後は停止する。

### 2026-10-12 21:54 JST: アンケート通知追加の初回Gradle compile失敗（修正済み）

- 対象機能: 第168版 `survey_available` 通知の遷移可否確認を追加中。
- 実行コマンド: `docker compose exec -T app gradle test --tests entity.StudentNotificationTest --tests control.student.StudentNotificationControlTest --warning-mode all`
- 期待結果: 対象Entity/Controlテストがコンパイルされ実行される。
- 実際の結果: `:compileJava FAILED`。`StudentSurveyDao.java:311` の `hasActiveQuestions` が直前メソッド内に誤ってネストされ、`illegal start of expression` 等のコンパイルエラー4件。テスト実行前に停止。
- 影響範囲: コードのビルド確認のみ失敗。アプリ再起動、DB migration、業務データ更新、外部API呼び出しは行っていない。
- 対応: `findPageTarget`の閉じ波括弧を修正し、`hasActiveQuestions`をDAOクラス直下のprivate methodへ移動。
- 再検証: 同じコマンドを再実行し、`:compileJava`・`:compileTestJava`・`:test`成功、`BUILD SUCCESSFUL`（2秒）。Entity/Control対象テストは失敗なし。Gradle 8.10.2の既存WarPluginConvention deprecation warningのみ。

## 2026-10-13 09:26:57 JST: 工程20専用MySQLのV11 migration failure

- 対象/手順: 工程20 P20-02の空DB migration。新規Compose project `ppe-p20-20261013`と専用DB `ppe_phase20_20261013`を作成し、Flyway全migration適用とvalidateを実行。
- 期待結果: 空の隔離MySQLへ現在の全migrationを適用し、Flyway validateが成功する。
- 実際の結果: `docker compose --env-file /dev/null -p ppe-p20-20261013 run --build --rm --no-deps app gradle --no-daemon flywayMigrate flywayValidate --warning-mode all`がexit 1。Flyway V11 `src/main/resources/db/migration/V11__add_school_security_policy.sql` line 46の`protect_school_security_policy` trigger作成時、MySQL error 1419: `You do not have the SUPER privilege and binary logging is enabled (you *might* want to use the less safe log_bin_trust_function_creators variable)`。V11より後のmigrationとvalidateは実行されていない。
- 原因: この使い捨てMySQLでbinary loggingが有効で、アプリmigration userにtrigger/function作成を許す管理権限がないため。既知のMySQL隔離fixture要件と同じ類の制約。コード変更による失敗ではない。
- 影響範囲: 失敗は当該工程20専用DBのみ。共有8080 app-live、既存開発DB、他Compose project、ソースコードのmigrationは変更していない。P20-02は未合格。
- 対応/再検証: 失敗を含む専用DB/volumeだけを作り直し、対象の隔離MySQLでmigration中だけ`log_bin_trust_function_creators=1`にしてV1〜V33を適用・validate。Flyway `flywayInfo`はschema version 33、全migration state `Success`を表示し、各コマンドexit 0。実行後に設定を0へ戻し、その値を確認した。詳しい工程結果は[工程20 P20-02記録](./feature-plans/phase-20-cross-feature-validation.md#p20-02-空の隔離mysql-migration進行中)を参照。
- 未解決事項: 同DBでのアプリ認証成功とDB write/read-backは未実施。

## 2026-10-13 09:35:57 JST: 工程20ブラウザー操作とGradle lock

- 対象/手順: 工程20の専用アプリlogin failure受入と、`ppe_teacher_task_test_phase20`用の追加Flyway migration。
- 期待結果: 認証フォームの無効資格情報を送信し、エラー表示を確認。専用DBへV1〜V33を適用・validate。
- 実際の結果: ログインbuttonの通常clickがvisible/enabled/stable待ちで30秒timeout。DOM計測ではbuttonはenabledでlayout上visible、スクリーンショットでも表示されており、force clickによる送信は成功。ログイン失敗画面に一般化されたエラー文言を確認した。これは合成の未登録IDを用いたため、既存/実利用アカウントには影響なし。Playwright操作toolの通常clickのみ完了できなかった。
- 同じ期間のDB作業では、`docker compose ... run --rm --no-deps -e DB_NAME=ppe_teacher_task_test_phase20 app gradle --no-daemon flywayMigrate flywayValidate --warning-mode all`がexit 1。Gradle user homeの`/home/gradle/.gradle/caches/journal-1` lockを、長時間稼働するappRun daemonが保持し、one-off Gradle processがtimeoutした。Flyway taskの実行前に停止し、migrationは適用されなかった。
- 原因/影響: browser click待機と、専用Compose appRun/test run間のGradle cache共有競合。現時点でアプリコードの障害は示していない。失敗した追加schemaは本工程の使い捨てMySQL内部、共有DB・本番・ソースへの影響なし。
- 対応/再検証: browser clickは画面・DOM状態確認後のforce clickで認証失敗表示を確認した。Gradle試験は長時間稼働中のappRunとcacheを共有せず、`docker compose exec`のtest processに専用`GRADLE_USER_HOME=/tmp/gradle-p20-integration`を指定してmigrationを再実行する。
- 未解決事項: 専用schema migrationとbrowserの通常pointer click成功は未確認。Playwright CLI実行環境はNode.js未導入。

## 2026-10-13 09:38:26 JST: 工程20のopt-in統合試験でtrigger作成が失敗

- 対象/手順: P20-03/P20-04専用教師課題統合テスト`control.teacher.TeacherTaskDatabaseTest`、schema `ppe_teacher_task_test_phase20`。
- 実行: `docker compose --env-file /dev/null -p ppe-p20-20261013 exec -T -e DB_HOST=db -e DB_PORT=3306 -e DB_NAME=ppe_teacher_task_test_phase20 -e DB_USER=ppe_phase20 -e DB_PASSWORD=[専用DB用合成値] -e TEACHER_TASK_DB_TEST=true -e GRADLE_USER_HOME=/tmp/gradle-p20-integration app gradle --no-daemon test --tests control.teacher.TeacherTaskDatabaseTest --rerun-tasks --warning-mode all`
- 期待結果: DB統合テストが専用schemaで実行され、DB状態・rollbackが検証される。
- 実際の結果: exit 1、37 tests completed、1 failed、5 skipped。`rollsBackAllDraftChangesWhenMySqlRejectsHintInsert`が`TeacherTaskDatabaseTest.java:1536`で失敗。原因SQLは`createHintInsertFailureTrigger`（同2948行）でMySQL error 1419 (`You do not have the SUPER privilege and binary logging is enabled`)。他の36件はこのrun内で失敗なし。
- 原因/影響: testは故障注入用triggerを作成するため、専用MySQLの`log_bin_trust_function_creators=1`を追加前提としていた。migration時の一時設定を0へ戻した後に試験を実行したため、trigger作成が拒否された。製品DAO/アプリの失敗を示す証拠ではない。対象は新規隔離schemaと専用Compose DBのみ。
- 対応/再検証: 当該専用MySQLだけでtest実行中は設定を1にし、全suiteを再実行した後に0へ戻す。suiteの最終件数・exit codeを工程20計画へ追記する。
- 未解決事項: 正しいtest環境条件での再実行はこの記録作成時点で未実施。

## 2026-10-13 09:46 JST: 工程20評価例保存で古いinactive行がactive化される

- 対象/手順: `ppe_teacher_task_test_phase20`上の`control.teacher.TeacherTaskDatabaseTest.generatesFluctuationsForRevisionWithoutDraftOnlyRubricInitialization`。評価例を生成した後、1日前作成の合成inactive行を同じprompt versionへ追加し、新規生成例を保存した。
- 期待結果: 今回生成した評価例だけが`active`になり、過去のinactive行は`inactive`のまま残る。
- 実際の結果: 修正前の回帰テストはexit 1。古い行が`active`となり、`TeacherTaskDatabaseTest.java:814`のassertionが失敗した。原因は`TeacherPromptDao.saveEvaluationExamples`がprompt version内の全inactive行を一括でactive化していたこと。
- 影響範囲: 同じprompt versionに残る古い合成評価例まで現在の設定例として有効になり、評価設定・後続評価へ混入する可能性がある。今回の再現データは専用schema内の合成値のみ。共有8080、開発DB、実生徒データ、外部AIには影響していない。
- 対応: DAOの更新対象を、現在読み込んだ生成結果の最新`evaluation_example_id`とprompt versionが一致するinactive行だけに限定した。過去行の一括有効化をしないよう変更。
- 再検証: 修正後の同じ対象テストはexit 0。続けて`control.teacher.TeacherTaskDatabaseTest`全体をexit 0で再実行し、37 tests、32 passed、0 failures、0 errors、5 skipped。skipはopt-in browser fixture等であり合格件数に含めていない。統合試験用MySQLの`log_bin_trust_function_creators`は終了後に0へ戻し、users/tasks/evaluation_examplesは各0件を確認した。詳細は[工程20実行記録](./feature-plans/phase-20-cross-feature-validation.md#p20-03p20-04-専用教師課題mysql統合試験部分完了)を参照。
- 未解決事項: 生徒エディターの時間経過を含む30秒自動記録・複数ブラウザー競合・重複提出・再提出確定、およびブラウザーでの画面確認は別途未実施。

## 2026-10-13 10:10 JST: 工程20の統合試験環境・実行順の失敗と再検証

### TeacherSurveyDatabaseTestを既存fixtureと同じschemaで実行した際の失敗

- 実行: `control.teacher.TeacherReviewDatabaseTest`、`control.teacher.TeacherSurveyDatabaseTest`、`control.student.StudentNotificationDatabaseTest`を同じ`ppe_teacher_review_test_phase20` schemaで一括実行。
- 期待結果: 3 suiteが合成review fixtureを共有して成功する。
- 実際の結果: exit 1、22 tests completed、21 passed、1 failed。`TeacherSurveyDatabaseTest.consentSchoolHistoricalAnswersExportsAndReadImmutability`が`TeacherSurveyDatabaseTest.java:51`で`SecurityException`。先行するreview testがusers fixtureを作るとsurvey `seed()`がusers非空で早期returnし、`survey-results`権限とsurvey固有fixtureを作らなかった。アプリ機能の失敗ではなくfixtureの共有順序前提と判定。
- 対応/再検証: review/notificationとsurveyを別の空schemaへ分離。`TeacherReviewDatabaseTest` 2/2、`StudentNotificationDatabaseTest` 19/19、`TeacherSurveyDatabaseTest` 1/1がそれぞれexit 0。すべて専用MySQL・synthetic fixture。
- 未解決事項: fixture seedの共有schema実行順依存はテスト資産に残る。工程20では分離schemaで再現可能に実行し、既存テスト資産の一般的な再実行性改善は本対応に含めない。

### TeacherReviewDatabaseTestをfixture残存schemaへ再実行した際の失敗

- 実際の結果: 初回のreview/survey/notification混合run後にreviewとnotificationを同じschemaで再実行し、`TeacherReviewDatabaseTest.java:288`のevidence件数assertが失敗。先行runのevidence fixtureが残っており、同suiteを新規DB前提で再実行したため。
- 対応/再検証: review schemaを専用範囲でdrop/recreateし、V1〜V33を再適用・validate後、reviewのみを新規schemaで実行。2 tests、2 passed、0 failures/errors/skips、exit 0。既存開発schema/共有8080には接続していない。

### StudentExerciseDatabaseTestでPython runner DNSが解決しなかった

- 対象/実行: `docker compose --env-file /dev/null -p ppe-p20-20261013 exec -T -e DB_HOST=db -e DB_PORT=3306 -e DB_NAME=ppe_exercise_test_phase20 -e DB_USER=ppe_phase20 -e EXERCISE_DB_TEST=true -e GRADLE_USER_HOME=/tmp/gradle-p20-integration app gradle --no-daemon test --tests control.student.StudentExerciseDatabaseTest --rerun-tasks --warning-mode all`
- 期待結果: 実runnerを伴うstudent exercise DB suiteが成功する。
- 実際の結果: exit 1、36 tests completed、4 failed。`revalidatesInteractiveTargetsAndRejectsCrossFileAccess`、`runsInteractiveExerciseWithOwnerBoundariesAndSinglePersistence`、`runsUnsavedCodeThroughRealRunnerAndPersistsOnlyExerciseExecution`、`monitorsDetachedExecutionAndPersistsCancellation`が`java.net.ConnectException` / `UnresolvedAddressException`。専用Compose projectでappとDBだけを起動し、`python-runner`サービスを起動していなかったため、設定済みrunner hostnameを解決できなかった。
- 対応: `docker compose --env-file /dev/null -p ppe-p20-20261013 up --build -d --wait python-runner`を実行し、サービスがhealthyになることを確認。
- 再検証: 同じStudentExerciseDatabaseTestコマンドを再実行し、36 tests、36 passed、0 failures/errors/skips、exit 0。後に追加した所有者境界・DB再読込テストを含む再実行は37/37 passed、exit 0。失敗中も共有開発runner/DBには接続していない。

### Gradle testを同一worktreeで並列実行したことによるbuild output race

- 対象/実行: `StudentExerciseDatabaseTest`と`StudentNotificationDatabaseTest`のGradle testコマンドを同じ専用app container上で同時実行した。
- 実際の結果: exercise側はexit 1、`:compileJava`時に`NoSuchFileException: /ppe-p20-20261013/build/classes/java/main/control/evaluation/EvaluationResponseValidator.class`。通知側はexit 0、19 tests passed。共有`build/`出力を同時にclean/recompileした実行順raceと判断。
- 対応/再検証: 同一worktreeでGradleを並列実行しないよう直列化。exercise suiteを単独再実行し37 tests、37 passed、0 failures/errors/skips、exit 0。製品コード変更は不要だった。
- 影響/未解決事項: 失敗runはtest taskの再実行前にcompileで停止。環境起因の失敗と初回失敗を記録し、成功runの件数へ合算していない。

### 工程20の最終build初回起動でCompose volume指定が不正（2026-10-13 10:14 JST）

- 対象/実行: 隔離app serviceを停止後、最終clean buildを一時Compose containerで実行。初回コマンドは`docker compose --env-file /dev/null -p ppe-p20-20261013 run --rm --no-deps -e GRADLE_USER_HOME=/tmp/gradle-p20-integration --entrypoint gradle app --no-daemon clean build --warning-mode all`。
- 期待結果: Gradle 8.10.2/JDK 21でclean buildとJUnitを実行する。
- 実際の結果: exit 1。`--env-file /dev/null`がCompose必須補間値（特に`PROJECT_NAME`）を空にし、repository bind mountのdestinationが`/`へ展開されたため、Dockerが`invalid volume specification ... destination can't be '/'`を返した。Gradleは起動せず、ソース/DBへ変更はなかった。
- 対応/再検証: project既定の`.env`補間を使い、`--env-file /dev/null`を外して同じ隔離projectで再実行。`docker compose -p ppe-p20-20261013 run --rm --no-deps -e GRADLE_USER_HOME=/tmp/gradle-p20-integration --entrypoint gradle app --no-daemon clean build --warning-mode all`はexit 0、Gradle 8.10.2/JDK 21、`BUILD SUCCESSFUL`。JUnit 140 suites / 524 tests、349 passed、0 failures、0 errors、175 skipped。WAR/build成功。skipはpassへ含めていない。
- 影響/未解決事項: 初回失敗はCompose実行環境の補間誤りであり、再実行で解消。Gradleの`WarPluginConvention` deprecation warningは継続。Playwrightが実行できず工程20のbrowser受入gapは別途未解決。

### Node.jsのHomebrew導入がCommand Line Tools不足で失敗（2026-10-13 10:38 JST）

- 対象/実行: ユーザー承認後、Playwright受入のため`brew install node`を実行。
- 期待結果: Node.js/npm/npxを導入し、Playwright browser tierを利用可能にする。
- 実際の結果: Homebrew exit 1。Node v26.11.1の依存`mpdecimal`をソースbuild中に`fatal error: 'cstdint' file not found`で停止した。Homebrewは`/usr/local`のIntel構成をTier 3と報告し、Command Line Toolsの更新を推奨した。処理途中で`pkgconf`が導入され、`ca-certificates`も更新されたが、Node.jsは導入されなかった。
- 影響/原因: プロジェクトファイル・Compose環境・DBに影響なし。Homebrew経路が既存のCommand Line Tools不足で依存をbuildできなかった。`sudo rm -rf`やシステムCommand Line Toolsの削除/再導入は実施していない。
- 対応/再検証: Homebrewを介さず、公式nodejs.orgの署名対象チェックサム一覧でARM64 Node v24.21.0 archiveを確認した。ユーザー領域への公式prebuilt導入を試み、SHA-256を検証してから展開する。
- 未解決事項: Node.js/Playwright導入とbrowser受入は継続中。未完了の導入・受入を成功扱いしない。

### R-03〜R-05 合成縦断browser初回失敗と最終再検証（追記 2026-10-17 11:11 JST）

- 対象/期待結果: `sh scripts/testing/teacher-learning-flow-local.sh`で隔離MySQL/Tomcat/合成providerを使い、確定survey template、stale prompt version、student/teacher browser、restart read-backを検証する。各初回失敗runの正確な開始時刻はrunnerに記録されていないため、追記時刻を記し、推測の時刻は付さない。
- 初回失敗と原因:
  - Playwright初回runは学生評価selectorの重複、375px幅で閉じたnavbar dropdownがviewport幅へ含まれる問題、教師survey画面の0件表示で失敗した。selectorを対象理由要素へscopeし、閉状態のdropdownを非表示にした。教師0件表示はDAO条件ではなく、browserがinternal network内でBootstrap CDNを取得できず`bootstrap is not defined`で画面初期化が止まっていた。test専用browser containerのみDocker bridgeにも接続し、Bootstrap CDNからHTTP 200を得るようrunnerを修正した。app/DB/providerはinternal network内に残した。
  - CDN取得後、survey設問の日本語promptが文字化けしてbrowser確認が失敗した。survey SQL fixtureを流すMySQL CLIのclient charsetが未指定だったため、apply/rerun/rollbackすべてに`--default-character-set=utf8mb4`を指定した。
  - 教師surveyのCSV受入は、詳細modalを開いたまま背後のCSVボタンを押し、modalがpointer eventを遮ってtimeoutした。modalを閉じてからexportする実際の操作順へPlaywrightを修正した。`acceptDownloads: true`だけではこのpointer遮蔽は解消しない。
- 影響/対応: いずれも隔離合成検証のbrowser/test fixture・test networkに限定。DB query/権限の製品コード変更、production write/deploy/restart、実学生/研究データ、Gemini実API要求はない。stale promptの本体修正は、submitされた`expectedRowVersion`を検証して409で拒否するようServlet側へ反映し、状態とapply auditの不変もruntime testに含めた。
- 再検証: 2026-10-17 11:11 JSTまでに実行した最終`sh scripts/testing/teacher-learning-flow-local.sh`はexit 0。JUnit 7 passed/0 failed/0 errors/0 skipped、Playwright 3 passed/0 failed。survey SQLは6 questions/30 options、rerun fail-closed、挿入失敗後0 partial rows。DBの最終synthetic countsはrubric 1 / published task 1 / completed evaluation 1 / active survey 1 / questions 6 / options 30 / submitted responses 1 / answers 6。合成provider generation 3件はfluctuations/examples/evaluation。`sh -n scripts/testing/teacher-learning-flow-local.sh`と`git diff --check`もexit 0。Docker container/network cleanupを確認。
- 未解決事項: R-03の空DBからローカル確定設定全量を作るseed一式、R-05の利用者受入、実Gemini/API・採点品質・本番設定投入/反映は未完了。実行エラーのない最終結果を、それらの別ゲートの成功へ読み替えない。
