# 工程20 機能横断テスト計画

## 目的・状態

工程10〜19で実装・個別受入した教師/管理者機能を、認証・権限・状態遷移・永続化の境界を越えて横断確認する。工程7・8等の既知未確認ケースと、評価例保存で発見されたDAOリスクの再現・修正も対象に含める。

この文書は実行計画兼記録である。全体clean buildと個別MySQL統合試験に加え、Node.js/Playwrightで認証・エディター、N166期限後提出deny、教師確認画面/出力、狭幅表示、教師の即時公開から生徒editor、予約公開workerから通知・editor、認証・権限境界の追加入受入までの計9テストを受入済み（5つの別fixture環境・実行バッチ）。ただし全主要導線・権限境界のブラウザー網羅、残るopt-in試験、他ブラウザー/OS等が未確認のため、工程20は`PARTIAL / NEEDS_SIGNOFF`である。機能別計画にある過去の成功件数は、その記録された範囲の証跡であって、現行ソース全体の最新ベースラインを代替しない。

## 現在のテスト状況

| 層 | 現在確認できる証跡 | 現在の判定 |
|---|---|---|
| 単体・JUnit | 今回の予約公開fixture追加前の直近clean buildは141 suites / 527 tests（349 passed、178 skipped、failures/errors 0）、Gradle clean build exit 0。追加fixtureは対象指定で別途実行 | 全体clean buildは成功。skipはopt-in環境依存であり、対象MySQL suiteで個別実行したケース以外を合格扱いしない |
| MySQL統合 | 機能ごとの専用MySQLテスト、migration/validate、DB保存・再読込を行った記録がある | 個別機能では実施済み。全機能・現行コードを通した統合完了とは判定できない |
| 認証HTTP | 複数工程で認証後HTTPと権限拒否を確認した記録がある | 個別範囲は実施済み。横断的なロール/所有範囲の網羅は未確認 |
| ブラウザー | 認証失敗/成功・管理境界、生徒の30秒自動保存後再読込・二つのbrowser context間のstale保存拒否、期限後`DENY`通知遷移・editor直接URL拒否、教師4画面/出力・狭幅、教師の即時公開/予約公開workerから対象生徒の通知・editor、教師アカウント状態/所有範囲のDB境界と認証HTTP/browserをPlaywrightで確認 | 計9 tests合格（5つの別fixture環境・実行バッチ）。他の主要導線・権限境界は網羅していない |
| ブラウザー自動化 | Node.js v24.21.0、npm 11.19.0、Playwright 1.64.0とChromiumが利用可能。`npx playwright install --with-deps chromium` exit 0。E2E spec/configは`e2e/` | Playwright primary tierを実行。各fixture batchのexit code 0を記録。未確認フローは合格扱いしない |
| CIでのopt-in試験 | 標準CIではDB・認証HTTP・ブラウザー・Gemini実API等の環境依存試験が、必要な環境設定なしにskipされる | CIのgreenだけでこれらの試験を合格扱いしない |

したがって、「単体テスト・結合テストはすべて完了済み」という認識は正確ではない。最終変更後の全体clean buildとJUnitは成功し、専用MySQL統合テストも複数領域で成功したが、skipされたopt-inケースと工程20の認証/browser横断受入が残っている。

## 対象・対象外

### 対象

- 教師/管理者/生徒の認証後アクセス、ロール、学校・クラス・担当範囲の認可と直接URL/APIの拒否。
- 教師課題の下書き・公開・予約・期限・改訂・独立系列コピー・期限延長・対象クラス変更、および配信/通知との接続。
- 授業演習コードの配信と、生徒側の受信・履歴・対象範囲。
- 課題進捗、提出内容の確認、評価結果、アンケート結果、CSV等の出力と権限/匿名化境界。
- 生徒エディターの未確認4ケース、N166期限後提出denyの画面遷移、および評価例DAOのinactive行状態不具合。
- 空の専用DBから現在の全migrationを適用した起動・主要導線・保存/再読込。
- 失敗系、状態競合、冪等性、監査記録、関連回帰。

### 対象外・別ゲート

- Gemini実APIの追加呼び出し、実生徒データ/実提出/コードログの送信、採点品質校正。
- 本番AWSへのデプロイ、新release受入、Phase 2、本番データ操作、費用/quota/負荷・災害復旧試験。
- 正式なアンケート設問設定UI。現行仕様REQ-SURVEY-159の対象外であり、工程20で未実装不具合として扱わない。
- OS標準のファイル選択/保存ダイアログや全OS・全ブラウザー組合せ。既存機能別計画の別途未確認事項として保持する。

## 実行環境とデータ方針

- DB/アプリはDockerを用いた隔離環境を主系とする。現在の環境でDocker daemonは利用可能。Dockerとブラウザー自動化の可否は別々に判定する。
- ホストGradle 8.14ではなく、CI/プロジェクトの標準に合わせたGradle 8.10.2・Java 21のコンテナー実行を基準にする。既存の開発手順とCI文書に記載された環境変数・起動方法を使う。
- 共有8080/開発DBを工程20の破壊的な統合試験先にしない。新しいCompose project/DB/schema/portを使い、開始前に対象を照合する。既存のfixture安全ガードを弱めず、実DBデータを試験前提にしない。
- テストデータは合成値だけを使い、各ケースで一意な識別子を用いる。テスト前後に対象schema/containerとfixture数を記録し、後処理後に残存データがないことを確認する。
- DB統合には実MySQLを使う。アプリの認証・画面/API経路を通し、外部AIは呼び出さない。AI境界が必要なケースは決定的なテスト用応答を使い、providerに接続しない。
- PlaywrightはNode.js + Chromiumの可否をinfra tierと独立して試す。Node.jsが利用できるなら導入・実行を試みる。利用不能なら正確なコマンド/エラーを記録し、HTTP/Servlet試験を行いつつ、実ブラウザー表示の未検証gapを残す。Dockerが使えないことをPlaywright省略の理由にしない。
- 起動後はログイン画面/保護画面のreadinessを確認してから試験する。起動失敗時はログを採取して原因を記録し、未起動のアプリに対する後続受入は行わない。

## 実施順序と受入条件

各段階の試験コマンド、終了コード、成功/失敗/skip件数、DB migration範囲、環境識別子、ログ/レポートを実行記録に残す。skipは成功に含めず、opt-in試験を有効化して再実行する。

| ID | 段階 | 試験範囲・主な観点 | 合格条件 |
|---|---|---|---|
| P20-01 | 環境確認・基準線 | 作業ツリーを確認し、`docker compose exec -T app gradle clean build --warning-mode all`で全JUnitをCI相当の環境で実行。環境依存skipを分類 | 終了コード0、失敗/エラー0。pass/skip件数と実行環境を記録。失敗があれば工程20全体を合格とせず、原因と既知不具合との関係を記録 |
| P20-02 | DB・起動 | 空の専用MySQLから現在のFlyway migration全件を適用しvalidate。アプリ起動、教師/生徒/管理者の認証、DB書込み後の再読込 | migrate/validate・build・起動が成功し、合成fixtureの保存/再読込がDBとアプリ応答で一致。既存スキーマを使い回さない |
| P20-03 | 認証・権限 | 各ロールの許可/拒否、教師の担当校/クラス境界、停止/削除/失効状態、未認証・権限不足での直接URL/API、更新競合 | 許可ケースのみ成功し、拒否ケースは非成功レスポンス/共通feedbackとなる。拒否時にDB変更・情報漏えい・監査の誤記録がない |
| P20-04 | 課題・配信の縦断 | 教師が合成課題を作成/変更し、公開または予約公開。workerによる実状態遷移後、生徒が対象通知/一覧から課題を開く。改訂時は旧版履歴と新revisionの関係も確認 | 課題・割当・通知の状態が状態ルールと一致し、通知/画面の対象IDが新revisionに一致。再実行で重複作成せず、DB再読込後も結果が保持される |
| P20-05 | 教師の確認系 | 進捗→提出内容→評価→アンケート結果の主要導線、検索/絞込/ソート、対象外教師の拒否、CSV/JSON等の出力 | 表示・集計・出力がDBの合成fixtureと一致。仮名化/匿名化要件と担当範囲を守り、対象外データを含めない |
| P20-06 | 生徒エディター未確認ケース | (a) 30秒スナップショット保存とDB再読込、(b) 複数ブラウザーの楽観ロック競合、(c) 重複提出の冪等性、(d) 提出後の再提出確定とrevision/履歴 | 各ケースに自動化された回帰テストを追加または既存テストを明示的に特定。競合/再送がデータを失わず、revision・状態・履歴が一貫する |
| P20-07 | 期限後提出deny通知 | N166期限切れ通知をdeny設定にし、認証生徒が選択したときの遷移をHTTPとブラウザーで確認。期限延長/別割当/他人のassignmentへの直接アクセスも確認 | denyは提出可能なeditorへ進まず、生徒ホーム等の仕様どおりの画面へ遷移。サーバー側でも本人/割当/期限を再検証し、DBを変更しない |
| P20-08 | 評価例DAO不具合 | 下記の「既知DAO不具合の再現・修正・回帰」に従う | 回帰テストが修正前の誤動作を再現し、修正後は最新生成行のみactive、古いinactive行はinactiveのまま。関連保存/競合テストも成功 |
| P20-09 | ブラウザー横断受入 | 少なくとも教師の課題公開/改訂、管理者の管理操作、対象生徒の通知→課題、権限拒否の4フローを認証状態で確認。画面サイズは対象画面の通常幅と狭幅を確認 | 実画面の表示/操作/遷移とHTTP/DB結果が一致。Node/Playwrightが利用可能なら自動化し、利用不能時は代替範囲・未検証gapを明記 |
| P20-10 | 最終回帰・清掃 | 対象focused testsと標準全体buildを再実行し、専用環境を停止/削除。作業ツリーと共有サービスへの影響を確認 | 終了コード0、失敗/エラー0。残skipと未検証範囲に説明がある。専用fixture/container/schemaが清掃され、共有8080/DBに予期しない変更がない |

### 主要ユーザージャーニー

1. 教師ログイン → 所有範囲内の課題を公開/改訂 → DB上の割当状態を確認 → 対象生徒が通知/課題一覧から該当revisionを開く。
2. 教師が担当クラスの進捗を確認 → 提出revision/コード/評価詳細を閲覧 → 結果を出力 → 対象外教師には同じ資源を直接要求して拒否される。
3. 生徒がエディターで編集 → 30秒保存を再読込で確認 → 提出 → 重複送信と別ブラウザー競合を確認 → 許可された再提出だけを確定。
4. 管理者が教師/学校を管理 → 権限内の教師が生徒/配信対象を操作 → 権限外操作はHTTP/UI双方で拒否される。
5. 期限切れdeny通知を生徒が選択 → editorへの誤遷移や提出を許さず、画面とサーバーの両方で拒否される。

### 既知DAO不具合の再現・修正・回帰（2026-10-13完了）

`teacher-task-revision.md`に記録された`TeacherPromptDao.saveEvaluationExamples`の状態遷移リスクを工程20で再現し、最新生成行だけをactive化する修正を実施した。修正前は対象prompt version配下のinactive評価例を一括してactive化し、古い生成分まで有効化し得た。

1. 専用MySQLで同一prompt versionの古いinactive行と今回生成したinactive行を作り、回帰テストで修正前の古い行active化を再現した。
2. DAOが最新生成example IDだけをactive化するよう修正し、古い行がinactiveのままであることを同suiteで確認した。
3. focused `TeacherTaskDatabaseTest`は39 tests、34 passed、5 skipped、failures/errors 0、exit 0。実Gemini APIを使わず、合成fixtureを使った。
4. 保存/適用に関する最終clean buildはexit 0。opt-in skipの内訳と他の残作業は本書の実行記録に保持する。

## Playwright/既存テスト資産の扱い

- JUnitを中心とするJavaテスト資産（調査時点で140 test source files）は継続利用し、上記のDB統合に必要なケースを既存のGradleテスト実行経路へ追加する。既存JUnit群を別runnerへ移し替えない。
- リポジトリ内のPlaywright specは確認できていないため、移行対象のlegacy browser testはなし。新規のブラウザー受入はNode.js/Playwrightが実行可能なら工程20のジャーニーに沿って作成する。
- 実行段階では、主要ジャーニーを異にする実行可能統合テストを少なくとも3ファイルで用意する。各対象suiteは合成fixtureの準備→認証/認可境界を含む操作→DB再読込の複数ステップを扱い、正常系・拒否系・永続化を検証し、fixtureを清掃する。既存suiteを再利用して不足分の新しい回帰ケースを追加してよい。すべてcanonical Gradle test/build経路から実行できること。
- ブラウザーの認証情報は環境変数または一時的な合成アカウントで与え、ソース・文書・ログ・テスト成果物へ秘密情報を保存しない。
- Playwrightが利用不能の場合でもMySQL統合とServlet/認証HTTP試験を継続する。代替試験はブラウザー描画の証拠とは扱わず、最終結果を`PARTIAL`または`NEEDS_SIGNOFF`としてgapを列挙する。

## 工程20の完了条件・報告

- P20-01〜P20-10の実施状態を各々`PASS`/`FAIL`/`UNVERIFIED`で記録し、合格基準を満たさない試験を成功扱いしない。
- 実行した正確なコマンド、終了コード、JUnit/DB/browserの成功・失敗・skip数、対象schemaとmigration範囲、アプリ起動/受入結果を記録する。
- 失敗した試験は原因・影響・修正と再検証結果を記録する。テストが検出した製品不具合は、再現テスト→最小修正→回帰テストの順で処置する。
- 未実行・skip・ブラウザー未確認・本番別ゲートを個別に記載し、工程20のPASSと誤認させない。工程20終了後にロードマップへ最終結果と次の別ゲートを反映する。

## 実行記録

### P20-01 環境確認・全体ベースライン（2026-10-13 JST）

- Docker daemon: 利用可能。Node.js CLI: 未導入。Composeの既存サービスには8080のapp-liveと開発DBが存在するため、これらは起動/停止/接続せず、別のGradleコンテナーから実行した。
- コマンド: `docker run --rm --network bridge -v "$PWD:/workspace" -w /workspace -e DB_HOST=127.0.0.1 -e DB_PORT=1 -e DB_NAME=ppe_ci_no_database -e DB_USER=synthetic_ci -e DB_PASSWORD=synthetic-ci-not-a-real-secret -e PPE_ENV=simulation -e AWS_EC2_METADATA_DISABLED=true -e AWS_SHARED_CREDENTIALS_FILE=/dev/null -e AWS_CONFIG_FILE=/dev/null gradle:8.10.2-jdk21 gradle --no-daemon clean build --warning-mode all`
- 結果: exit 0、`BUILD SUCCESSFUL`。JUnit 140 suites、521 tests、349 passed、0 failures、0 errors、172 skipped。WAR/buildも成功した。skipはopt-in DB/HTTP/browser fixture/実API環境がないためで、受入済み件数には含めない。
- 集計コマンド: `docker run --rm --network bridge -v "$PWD:/workspace" -w /workspace gradle:8.10.2-jdk21 bash scripts/ci/summarize-junit.sh build/test-results/test`。exit 0、上記と同じ件数・skip説明。
- 観測事項: Gradle 8.10.2からGradle 9.0で削除予定の`WarPluginConvention` deprecated warning。現時点のビルド失敗ではない。ブラウザー自動化はNode.jsがないため未実施。

### P20-02 空の隔離MySQL migration・起動（部分完了）

- 新規Compose project `ppe-p20-20261013`、専用DB `ppe_phase20_20261013` を作成。稼働確認済みの共有8080 app-live、開発DB、他projectのDBへ接続していない。外部AI credentialは設定せず、Geminiを呼び出していない。
- `docker compose --env-file /dev/null -p ppe-p20-20261013 up --build -d --wait db` はexit 0でDB healthy。
- 初回 `docker compose ... run --build --rm --no-deps app gradle --no-daemon flywayMigrate flywayValidate --warning-mode all` はexit 1。Flyway V11 `src/main/resources/db/migration/V11__add_school_security_policy.sql:46` の学校security policy保護trigger作成がMySQL error 1419（binary logging有効、SUPER privilegeなし）で停止した。失敗は新設した専用DBだけで発生。migration/validate合格とは扱わず、失敗内容と後続対応は[エラーレポート](../error-report.md#2026-10-13-092657-jst-工程20専用mysqlのv11-migration-failure)を参照。
- 失敗した専用Compose project/volumeだけを再作成。移行中だけ`log_bin_trust_function_creators=1`に設定し、空の専用schemaへV1〜V33を適用・validateした。結果はexit 0、Flyway schema version 33、全migration state `Success`。その後同設定を0へ戻した。`flywayInfo`もexit 0で現行version 33を確認。
- 専用app serviceはport 18089で起動しrunning。`/student/account/login`、`/teacher/account/login`がHTTP 200、保護された`/admin/schools`がHTTP 302。統合browserでログイン画面を表示し、存在しない合成IDによる試行で一般化された認証失敗メッセージを確認した。ログイン成功/DB書込み後の再読込はまだ未確認のため、P20-02全体は部分完了。
- 既存認証フォームは表示されたが、通常のbrowser clickが要素のvisible/enabled/stable待ちで30秒timeout。要素の表示・有効状態と画面を確認後、Playwrightのforce clickで送信し、認証失敗画面を確認できた。UI操作toolのtimeoutと回復内容は[エラーレポート](../error-report.md#2026-10-13-093557-jst-工程20ブラウザー操作とgradle-lock)に記録。
- `ppe_teacher_task_test_phase20`専用schemaを作成し、同一app containerで`GRADLE_USER_HOME=/tmp/gradle-p20-integration`を分離後、V1〜V33 migration/validateがexit 0。初回はCompose run test containerがappRunとGradle cacheを共有してlock timeoutしたが、分離設定で解消。
- 追加のレビュー/進捗/アンケート/配信/演習用の空専用schemaでもV1〜V33 migrate/validateがexit 0。各migration中だけbinary logging trustを有効にし、実行後は0へ復元した。
- ブラウザー自動化能力の独立確認: `npx playwright install --with-deps chromium`を実行したがexit 127、`npx: command not found`。Node.js/npm/npxが未導入のためPlaywright CLIによる自動E2Eは利用できない。統合browserではstudent login画面を表示し、一般化された認証失敗表示を確認した。失敗credentialは合成の未登録IDのみ。ブラウザー画像/DOMの確認はできたが、Node.js/Playwright経由の自動化、認証成功、認証後DB write/read-backは未確認。
- Dockerは利用可能なままMySQL統合を継続した。隔離アプリはport 18089でrunning。P20-02はmigration/起動/認証失敗表示まで部分確認、認証成功後の保存・再読込は未確認。
- Playwright受入再開: 初回migrationは新しい専用DBでもV11のtrigger作成がMySQL 1419で失敗。V11の部分適用列/制約を専用空schemaから戻し、当該MySQLだけ`log_bin_trust_function_creators=1`で`flywayRepair`後にV1〜V33を再適用・validateした。結果exit 0、成功migration 33件。global settingは試験前に`OFF`へ復元し、後処理時にも`OFF`を確認。原因・回復は[エラーレポート](../error-report.md)に記録。
- browser fixture用に同じ専用MySQL内へ`ppe_teacher_task_test_p20_20261013`を作成し、V1〜V33の`flywayMigrate flywayValidate` exit 0。`createsRetainedEditorFixtureForPhase20BrowserAcceptance`はexit 0、1 test passed、0 failures/errors/skips。合成教師・生徒、公開課題/割当を作成し、完了後にschemaごと削除した。
- appはDBを明示した専用Compose環境で起動し、`http://student.localhost:18089/student/account/login`と`http://teacher.localhost:18089/teacher/account/login`がHTTP 200。一般Hostの`127.0.0.1`ではportal host routingにより302/404となったが、アプリ不具合ではなく受入用Host設定の誤り。Playwright configの既定base URLを`student.localhost`へ修正した。
- 追加の縦断受入は専用Compose project `ppe-p20-publish-20261013` / DB `ppe_teacher_task_test_p20_publication`で実行。空DBへの`flywayMigrate`と`flywayValidate`は成功し、migration中のみ`log_bin_trust_function_creators=1`、検証後は`0`へ復元。cold Gretty起動中のreadiness probeはHTTP 000/404だったが、初期化後はstudent/teacher loginがHTTP 200となった。`createsRetainedDraftAndStudentForPhase20PublicationBrowserAcceptance`は1件実行され成功し、合成教師・生徒・課題を作成した。

### P20-03/P20-04 専用教師課題・配信MySQL統合試験（部分完了）

- コマンド: `docker compose --env-file /dev/null -p ppe-p20-20261013 exec -T -e DB_HOST=db -e DB_PORT=3306 -e DB_NAME=ppe_teacher_task_test_phase20 -e DB_USER=ppe_phase20 -e DB_PASSWORD=[専用DB用合成値] -e TEACHER_TASK_DB_TEST=true -e GRADLE_USER_HOME=/tmp/gradle-p20-integration app gradle --no-daemon test --tests control.teacher.TeacherTaskDatabaseTest --rerun-tasks --warning-mode all`
- 初回結果: exit 1、37 tests、1 failure、5 skipped。失敗した`rollsBackAllDraftChangesWhenMySqlRejectsHintInsert`はrollback故障注入trigger作成でMySQL error 1419。テスト用MySQLのbinary logging policyによる環境前提で、製品機能のassertに到達する前の失敗。失敗と再実行条件は[エラーレポート](../error-report.md#2026-10-13-093826-jst-工程20のopt-in統合試験でtrigger作成が失敗)を参照する。
- 再試験: 専用MySQLの`log_bin_trust_function_creators=1`をsuite実行中だけ有効にして同suiteを実行し、終了後に0へ戻した。結果はexit 0、37 tests、32 passed、0 failures、0 errors、5 skipped。skipはbrowser fixture等のopt-in環境を用意していない試験であり、passには含めない。
- 評価例の回帰確認: `TeacherPromptDao.saveEvaluationExamples`が古いinactive行までactive化する不具合を、古い合成inactive行を追加する回帰テストで再現。修正前の対象テストはexit 1（AssertionFailedError、inactiveがactiveへ変化）。最新生成行のIDだけをactive化するDAO修正後、対象テストはexit 0で成功し、suite全体も上記件数で再成功した。詳細は[エラーレポート](../error-report.md#2026-10-13-工程20評価例保存で古いinactive行がactive化される)を参照。
- 後処理: `ppe_teacher_task_test_phase20`のusers/tasks/evaluation_examples件数は各0。MySQLの`log_bin_trust_function_creators`は0へ復元済み。P20-03/P20-04のDAO/DB範囲は一部合格だが、全ロールHTTP/browser縦断と教師確認系は未実施のため各段階は未完了。
- 生徒エディター統合ケース: 同suiteに合成課題/参加者を作る2テストを追加。30秒snapshotイベントとしての保存、DB再読込、古いdraft timestampによる上書き拒否、教師ロールによる保存/提出拒否、同一冪等キーの重複提出、提出版2への再提出を確認。39 tests、34 passed、0 failures、0 errors、5 skipped、exit 0。スナップショットを実際に30秒待つタイマー/browser挙動と、二つのブラウザーから同時に送る競合は未確認。
- 配信は`TeacherDistributionDatabaseTest`: 1 test、1 passed、0 failures/errors/skips、exit 0。教師課題suite内の期限後`DENY`では、生徒editor非表示・参加行なしも確認済み。いずれも専用MySQLと合成fixtureを使用。
- 追加したPlaywright `teacher-publication.spec.js`で、教師の誤資格情報拒否と認証成功、下書きの即時公開・shared feedback確認、生徒の誤資格情報拒否と認証成功、ホーム上の対象課題、editorの`data-editable=true`を確認した。HTTP経由の公開後、DB再読込で課題/割当が`published`、`published_at`が設定済み、期限後方針が`deny`のまま保持されることも確認した。詳細はP20-09と[エラーレポート](../error-report.md#工程20-課題公開から生徒editorまでのplaywright受入2026-10-13-1259-jst)を参照する。
- 認証・権限境界の追加受入（2026-10-13）: 専用Compose project `ppe-p20-authz-a1`、専用DB `ppe_teacher_task_test_authz`、app port 18093を使用。空DBへV1–V33を適用・validateし、`log_bin_trust_function_creators`はmigration後に0へ復元した。`TeacherTaskDatabaseTest`の`rejectsTeacherWithoutTaskPermissionAndAfterPermissionRevocation`、`rejectsDraftReadUpdateAndAuditAfterSchoolPermissionIsDisabled`、`rejectsTaskOperationsForAnInactiveTeacherAccount`、`teacherWithSchoolPermissionCannotLoadAnotherTeachersDraft`、`sameSchoolTaskManagerCanDeleteAndRestoreWithoutEditingAnotherTeachersTask`はexit 0、5 passed。認証browser fixture testはexit 0、1 passed。`authentication.spec.js`はexit 0、1 passedで、無効資格情報の拒否、未認証の保護画面からログインへの遷移、有効な教師ログイン後の保護画面、管理者URLのHTTP 403を確認した。初回app起動はMaven Centralの名前解決失敗でexit 1だったが、依存取得が回復した後の再起動でreadyになり、同一の隔離環境でbrowser試験を完了した。migration 33件とtrust値0を再確認してから、専用Compose container/volume/networkを削除した。エラーと回復は[エラーレポート](../error-report.md#工程20-p20-03認証権限境界受入2026-10-13)を参照する。

### P20-05 教師の進捗・提出確認・評価・アンケート・通知（DB範囲 合格）

- `TeacherProgressDatabaseTest`: 専用`ppe_teacher_progress_test_phase20`で4 tests、4 passed、0 failures/errors/skips、exit 0。進捗集計・活動詳細・コード閲覧と権限剥奪を実DBで確認。
- `TeacherReviewDatabaseTest`: 新規空schemaで2 tests、2 passed、0 failures/errors/skips、exit 0。提出内容/評価スナップショット/証拠範囲/教師所有権を確認。
- `TeacherSurveyDatabaseTest`: `TeacherReviewFixture`とは独立した空の専用schemaで1 test、1 passed、0 failures/errors/skips、exit 0。回答状態、同意・所属条件、権限、匿名/個人識別可能なexport、read immutabilityを確認。
- `StudentNotificationDatabaseTest`: 専用review fixture DBで19 tests、19 passed、0 failures/errors/skips、exit 0。今回は再オープン時の既読状態・通知件数の再読込assertも追加し、本人所有外/利用不能な評価対象の拒否と既読永続化を確認。
- いずれも合成fixtureのみで外部AIなし。HTTP/browserでの教師画面表示・CSVダウンロード操作はこのDB試験結果では証明しない。

### P20-06 生徒エディター未確認ケース（部分完了）

- 教師課題MySQL suiteで30秒snapshot event、DBへのsnapshot本文保存と再読込、stale versionのHTTP相当control拒否、同一冪等キーで既存提出を返す処理、再提出版2の保存と提出を確認した。Playwrightでも実30秒周期のsnapshot POST・保存表示・再読込後のDB由来コード、および二つのbrowser context間のstale保存HTTP 409と勝者データの再読込を確認した。
- `StudentExerciseDatabaseTest`: Python runnerが起動している隔離環境で37 tests、37 passed、0 failures/errors/skips、exit 0。所有者境界の拒否後もDB上の所有者データが不変である新しい回帰ケースを含む。
- 2026-10-13提出前I/O follow-up: ユーザー承認により、提出前チェックだけPython `input(prompt)`のpromptを比較stdoutから除外する実装を追加。通常実行・対話実行のprompt表示と明示的なprint出力は維持する。Python runner unit/API testsは24 passed、Java `StudentEditorControlTest`は1 passed。ローカル8080の`programming-process-evaluator-app-live`（DB `programming_process_evaluator`、port 8080）とrunnerを更新し、認証済みbrowserでassignment 17の提出前チェック2/2一致、通常実行のprompt表示・入力`1 2 3`・出力`6`を確認した。最終提出は行わず、通常実行2回分の合成demo execution logのみ保存された。詳細は[Runtime Validation Report](./runtime-validation-report.md)および[エラーレポート](../error-report.md#rev171-提出前python入力プロンプト比較の修正検証2026-10-13-2220-jst)を参照。
- 2026-10-13再読込後follow-up: 合成課題`assignmentId=17`を再読込後、提出前チェックを再実行し、実出力`6` / `8`、2/2一致を確認した。通常clickと強制clickはbrowser toolでタイムアウトし、locator clickイベントをdispatchする代替操作を使ったため、この追試だけをポインター操作受入の証拠とはしない。最終提出・下書き更新はしていない。再読込後の実行結果表示は機能仕様が課題エディターに要求しておらず、保存結果再読込の計画対象にも含まれないため、今回の工程20不具合とは判定しない。詳細は[エラーレポート](../error-report.md#工程20-生徒エディター提出前チェックの再確認2026-10-13-2254-jst)を参照。
- 実際の提出操作の全再提出・冪等境界、全ブラウザー/状態組合せは未確認。API/DAO統合試験や対象外ブラウザーを受入完了とは扱わない。

### P20-07 期限後提出拒否・評価/アンケート・通知接続（部分完了）

- `TeacherTaskDatabaseTest.expiredDenyAssignmentDoesNotCreateStudentParticipation`で、期限切れ`DENY`割当を処理した後に対象学生がeditorから除外され、participationが生成されないことを専用MySQLで確認した。
- P20-05の評価・survey・通知DB試験は合格。追加Playwright受入で期限後`DENY`通知のクリックから`/student/home#taskListSection`への遷移とeditor直接URLのHTTP 404を確認した。教師の進捗/提出/評価/アンケート画面と出力、および390pxでの横overflowなしも確認済み。他の教師/生徒主要導線・権限境界は未網羅。

### P20-08 評価例DAO不具合（合格）

- 古いinactive例と今回の生成例が同一prompt versionにある状態をMySQL回帰テストで作り、修正前に古い行がactive化される不具合を再現した。`TeacherPromptDao.saveEvaluationExamples`を最新生成IDだけ更新するよう修正した。
- 修正後の`TeacherTaskDatabaseTest`は39 tests、34 passed、0 failures、0 errors、5 skipped、exit 0。opt-in fixtureのskipはpassへ含めない。エラーと対応は[エラーレポート](../error-report.md)に記録した。

### P20-09 ブラウザー横断受入（部分完了）

- ユーザーの選択後、Node.js v24.21.0、npm/npx 11.19.0、Playwright 1.64.0とChromiumを利用可能なユーザー領域へ用意。先行受入時に`e2e/`へ3 specを追加し、その後N166/教師レビュー用に2 spec、即時公開・予約公開受入用に2 specを追加（計7 spec）。fixture/passwordは専用DB・合成アカウントだけで使用。秘密値はレポートへ記録しない。
- 最終コマンド: `npm --prefix e2e run test:phase20`（Node.js v24.21.0をPATHへ追加、教師/生徒fixture・assignment IDは環境変数）。exit 0、3 tests passed、0 failed。認証specで誤資格情報拒否、教師ログインと課題画面遷移、教師から管理者画面へのアクセスがHTTP 403となる境界を確認。autosave specで誤資格情報拒否、ログイン後にコード編集し30秒周期`periodic_snapshot` POST成功、保存表示と再読込後のDB由来コードを確認。concurrency specで二つのbrowser contextを使い、1つ目の保存後のstaleな2つ目の保存がHTTP 409となり、1つ目の保存内容が再読込後も維持されることを確認。
- 初回Playwrightはexit 1で3件失敗（教師landing pathの想定違い1、CodeMirror複数一致2）。修正後の中間実行ではauth 1件がbaseURLの学生Hostへ誤って戻るため失敗し、editor 2件は成功。teacher現在Hostを基準にadmin URLを構築し直した後、上記3件すべて合格。失敗・原因・修正・再検証は[エラーレポート](../error-report.md)に記録。
- 追加した通知・教師レビューspecは、専用DB `ppe_teacher_review_test_phase20` / Compose project `ppe-p20-20261014`で実行。Java fixture test 1件とPlaywright 3 testsはそれぞれexit 0。期限後`DENY`通知遷移、editor直接URL拒否、教師の進捗/提出/評価/アンケート画面、提出・評価・survey CSV/text download、survey exportの匿名化、各画面390pxでのdocument横overflowなしを確認した。CSV本文はPlaywright Response本文ではなく実download fileから検証した。migrationはV1–V33、`log_bin_trust_function_creators`は`0`へ復元し、専用Compose資源・DBを清掃済み。
- follow-up実行: `npm --prefix e2e run test:phase20 -- phase20/expired-deny-notification.spec.js phase20/teacher-review-mobile.spec.js` — exit 0、3 passed、0 failed。詳細は[エラーレポート](../error-report.md)と[runtime validation report](./runtime-validation-report.md)に記録。
- 課題公開のfollow-up: `cd e2e && npx playwright test phase20/teacher-publication.spec.js --config=playwright.config.js`（Node.js v24.21.0、base URL/合成アカウントは環境変数）— 最終実行exit 0、1 passed、0 failed。初回は部分一致locatorが3リンクに一致しexit 1、完全一致hrefへ修正。2回目は全体50秒timeoutでexit 1、Playwright traceではeditor GETがHTTP 200（約18.6秒）だった。spec個別timeoutを120秒にし、browser contextを並列closeする修正後、1 passed（3.8秒）。各失敗・修正・再検証は[エラーレポート](../error-report.md#工程20-課題公開から生徒editorまでのplaywright受入2026-10-13-1259-jst)に記録。
- 予約公開worker・通知のfollow-up: 専用Compose project `ppe-p20-schedpub-a1`、専用DB `ppe_teacher_task_test_scheduledpub`、port 18092を使用。V1–V33のmigration/validationはexit 0。`createsRetainedScheduledPublicationFixtureForPhase20BrowserAcceptance`はexit 0、1 passedでscheduled assignmentと通知0件を確認し、fixtureにはID類のみ記録。アプリ起動後に対象assignmentだけをdueにし、実workerによる`scheduled -> published`、system監査記録と`task_published`通知1件をDBで確認した。`npm --prefix e2e run test:phase20 -- phase20/scheduled-publication-notification.spec.js`はexit 0、1 passed。認証失敗/成功、通知が一度だけ未読表示されること、通知クリック後の同じassignment editor表示、`read_at`永続化を確認した。Gemini API・実データなし。初回V11 migration error 1419、Compose環境変数のスコープ/起動直後DB probe、検証SQLの未知列は[エラーレポート](../error-report.md#工程20-予約公開workerから生徒通知までの受入2026-10-13-1359-jst)に記録。
- 2026-10-13フォルダ行UI follow-up: 機能仕様の`REQ-EXERCISE-170`に合わせ、フォルダ名ボタンと行空き領域から開閉できるようローカル実装を更新。認証済み生徒画面の合成フォルダで、フォルダ名イベント後に展開・path選択・`main.py`表示、行headerイベント後に折りたたみ、三角toggleの開閉、checkbox/menu領域のイベントでは行開閉しないことを確認した。ブラウザーツールのpointer/stable待ちがtimeoutしたため、実pointer操作は未確認。JavaScript/JSP Problems診断はエラー0件、`git diff --check`はexit 0。詳細は[エラーレポート](../error-report.md#工程20-授業演習フォルダ行操作のbrowser受入2026-10-13-2325-jst)を参照。
- 先行3テスト、review follow-up 3テスト、即時公開1テスト、予約公開1テスト、認証・権限境界1テストはそれぞれ別のfixture環境/実行バッチであり、9件を同一コマンド/同一DBで実行したとは扱わない。未確認: 教師の課題改訂/予約公開以外の他主要導線、管理者管理操作、全ブラウザー/OS、OS標準ダイアログ。したがってP20-09はPASSではない。

### P20-10 最終回帰・清掃（部分完了）

- 変更後の全体clean build: 専用project `ppe-p20-publish-20261013`でappを停止後、`docker compose run --rm --no-deps --entrypoint gradle app --no-daemon --console=plain clean build --warning-mode all`。Gradle 8.10.2/JDK 21、exit 0、`BUILD SUCCESSFUL`。JUnit集計は141 suites、527 tests、349 passed、0 failures、0 errors、178 skipped。opt-in fixtureを含むskipはpassへ算入しない。WAR/build成功。既存のGradle `WarPluginConvention` deprecation warningあり。
- 最終差分検査: `git diff --check`、exit 0。
- 2026-10-13ローカルUI追補: フォルダ名/行の展開・折りたたみを共通処理にし、画面scriptのcache-busterを更新。対象JavaScript/JSPのProblems診断はエラー0件、`git diff --check`はexit 0。`node --check`はNode.jsがPATH上になくexit 127だったため実行できず、browser event dispatchで挙動を追試した。これは直前のclean build後のUI-only変更で、pointer受入は未完了。工程20全体は引き続き`PARTIAL / NEEDS_SIGNOFF`。
- 隔離DBの最終確認: `@@GLOBAL.log_bin_trust_function_creators=0`。`ppe_exercise_test_phase20`、`ppe_phase20_20261013`、`ppe_teacher_distribution_test_phase20`、`ppe_teacher_progress_test_phase20`、`ppe_teacher_review_test_phase20`、`ppe_teacher_review_test_reviewphase20`、`ppe_teacher_review_test_surveyphase20`、`ppe_teacher_task_test_phase20`は専用MySQL内の工程20用schemaとして確認後、削除した。専用Compose project `ppe-p20-20261013`のapp/DB/Python runner、同projectの`gradle-cache`/`mysql` volume、および`ppe-p20-review-phase20` networkを清掃した。既存の`ppe-preparation-20261008_database` volumeと共有サービスは対象外で維持。
- Playwright再開後に追加した`ppe_teacher_task_test_p20_20261013`と`ppe_phase20_20261013`も削除。専用Compose projectのapp/DB/Python runner、project管理の`gradle-cache`/`mysql` volumesと3 networksは`docker compose -p ppe-p20-20261013 down -v`で削除し、残存container/volume/networkがないことを確認。既存共有volume/serviceは対象外。合成専用fixtureのみを操作し、Gemini実API/実データ送信なし。
- 追加の専用Compose project `ppe-p20-20261014`、専用DB `ppe_teacher_review_test_phase20`、fixture properties fileは検証後に削除。MySQLでlatest migration V33、`@@GLOBAL.log_bin_trust_function_creators=0`を確認後、このprojectのapp/db container、project volumes/networkだけを`docker compose -p ppe-p20-20261014 down -v`で削除し、残存なしを確認。共有service/volumeは対象外。
- 課題公開用Compose project `ppe-p20-publish-20261013`ではV1–V33 migration/validation成功、global trust値0、課題/割当の公開状態を確認後、appを停止してclean buildを実行。専用fixture fileはcleanで除去され、Compose projectのapp/db/Python runner、project volumes/networksも検証後に削除した。共有service/volumeは対象外。
- 予約公開用Compose project `ppe-p20-schedpub-a1`ではV1–V33 migration/validation、MySQL `log_bin_trust_function_creators=0`への復元、実workerの遷移/監査/通知1件/既読状態を確認。app、専用DB/volume、Python runner、gradle-cache、network、および合成fixture propertiesを検証後に削除し、共有サービス/volumeは対象外で維持した。
- 認証後のDB再読込、30秒の実時間保存、browser context間競合、N166 deny画面遷移、教師横断画面/出力、390px狭幅、教師による即時公開から対象生徒のeditable editor到達、予約公開workerから通知を経て対象editorへ到達し既読化するまでをPlaywrightで確認済み。標準buildのskip対象178件のopt-in外ケース、他の主要導線/権限境界、別ブラウザー/OS、OS標準ダイアログ、Gemini実API、本番受入は未確認。
- したがってP20-10の全体clean build・JUnit・専用MySQL試験・累計9件のPlaywright受入（5環境/バッチ）・差分検査・各専用環境清掃は実施済み。ただしP20-09と他の未確認事項を満たさないため工程20全体は`PARTIAL / NEEDS_SIGNOFF`、完了条件未達。最終集計と次の確認範囲は[Runtime Validation Report](./runtime-validation-report.md)を参照。

## 参照資料

- [実装ロードマップ](./implementation-roadmap.md) 工程20と教師/管理者機能の工程状態
- [CI運用](./continuous-integration.md) 標準build、opt-in環境、skip範囲
- [実装契約](./implementation-contract.md) ロール/データ所有範囲/状態遷移の制約
- [生徒エディター計画](./student-editor.md) 30秒保存・競合・重複提出・再提出
- [教師課題改訂計画](./teacher-task-revision.md) 評価例保存のDAOリスク
- [評価・アンケート計画](./student-evaluation-survey.md) 保存/匿名化/未確認事項
- [課題状態ルール](../../state-rules/teacher/task-state-rules.md)、[配信状態ルール](../../state-rules/teacher/distribution-state-rules.md)、[実装用状態表](../../state-rules/implementation-state-table.md)
