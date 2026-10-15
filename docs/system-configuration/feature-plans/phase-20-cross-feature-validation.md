# 工程20 機能横断テスト計画

## 目的・状態

工程10〜19で実装・個別受入した教師/管理者機能を、認証・権限・状態遷移・永続化の境界を越えて横断確認する。工程7・8等の既知未確認ケースと、評価例保存で発見されたDAOリスクの再現・修正も対象に含める。

この文書は実行計画兼記録である。全体clean buildと個別MySQL統合試験に加え、Playwrightで認証・エディター、N166期限後提出deny、教師確認画面/出力、狭幅表示、教師の即時公開から生徒editor、予約公開workerから通知・editor、認証・権限境界、提出から再提出、管理者による教師アカウント作成、課題改訂通知から新版editor、管理者作成教師の権限境界、現行survey設定受入、フォルダ行pointer操作、同一校教師による他教師所有テンプレートおよび課題下書きの表示・編集と他校教師の直接アクセス拒否、教師アカウントの停止/復帰/削除後の復元拒否、代表portal routeのロール許可・拒否境界、二つのeditor contextからの同時保存競合、提出確認・評価確認・アンケート結果確認・授業演習コード確認の学校所有範囲まで、Playwright 25件を個別に受入済み（30の隔離fixture実行バッチ。中間失敗を含む）。この30バッチはPlaywrightの成功件数とは異なる集計であり、Playwrightは別途29 successful test executions / 23 browser fixture-run invocations（[Runtime Validation Report](./runtime-validation-report.md)）として記録する。さらに2026-10-15、認証HTTP/MySQL fixtureで、進捗・提出確認・評価確認・アンケート結果確認の機能権限失効と進捗の担当校アクセス失効を含む教師確認系アクセス境界を確認した。教師アカウント管理fixtureでは教師から管理者専用画面/作成APIへの直接アクセス拒否に加え、管理者セッションから教師専用`/teacher/task`への直接アクセスがHTTP 403となること、管理者画面は引き続きHTTP 200となること、さらに管理者による古い更新versionの停止要求がHTTP 400で拒否され状態/versionを変えず失敗監査に記録されることを確認した。テスト側の初回失敗も含む実行履歴は[エラーレポート](../error-report.md)に記録した。最新の全体clean buildは144 suites / 552 tests（369 passed、183 skipped、failures/errors 0）、Playwright specはGradle build対象外。skipは未実行として扱う。ただし全主要導線・権限境界のブラウザー網羅、残るopt-in試験、他ブラウザー/OS等が未確認のため、工程20は`PARTIAL / NEEDS_SIGNOFF`である。機能別計画にある過去の成功件数は、その記録された範囲の証跡であって、現行ソース全体の最新ベースラインを代替しない。

## 現在のテスト状況

| 層 | 現在確認できる証跡 | 現在の判定 |
|---|---|---|
| 単体・JUnit | 最新の隔離clean buildは144 suites / 552 tests（369 passed、183 skipped、failures/errors 0）、Gradle clean build exit 0。Playwright specはGradle build対象外 | 全体clean buildは成功。skipはopt-in環境依存であり、対象MySQL suiteで個別実行したケース以外を合格扱いしない |
| MySQL統合 | 機能ごとの専用MySQLテスト、migration/validate、DB保存・再読込を行った記録がある。2026-10-15に`TeacherTaskDatabaseTest` 45 tests（35 passed、10 skipped）、Gemini評価DB smoke 1 test、および教師確認系3権限の失効を含む認証HTTP runtime test 1件を専用MySQLで実行し成功 | 個別機能では実施済み。全機能・現行コードを通した統合完了とは判定できない |
| Gemini synthetic API | 診断/smokeはexit 0（選択7件: 6 passed、1 gated skip）、専用MySQL評価smokeは1/1成功。追加の候補suiteは4件中3 passed/1 failed、exit 1（`gemini-2.5-pro` structured generation HTTP 404） | 候補suiteはFAIL。API一覧への掲載とInteractions structured generationの利用可能性は同義ではない。原因・採用判断は未解決 |
| 認証HTTP | 複数工程で認証後HTTPと権限拒否を確認。進捗の担当校/機能権限失効、提出確認・評価確認・アンケート結果確認の機能権限失効を確認 | 個別範囲は実施済み。横断的なロール/所有範囲の網羅は未確認 |
| ブラウザー | 認証失敗/成功・管理境界、生徒の30秒自動保存後再読込・逐次stale保存拒否・同時save重複期間での一勝一競合、期限後`DENY`通知遷移・editor直接URL拒否、教師4画面/出力・狭幅、教師の即時公開/予約公開workerから対象生徒の通知・editor、教師アカウント状態/所有範囲のDB境界と認証HTTP/browser、提出・再提出、管理者による教師作成と保存後再読込、改訂通知から新版editor、管理者作成教師の初回パスワード変更後の許可/拒否route、現行survey設定、フォルダ行pointer操作、同一校peer教師によるテンプレート/課題下書きの表示・編集および他校教師の直接アクセス拒否、削除済み教師の復元POST拒否と失敗監査、教師から管理者専用画面/作成APIへの拒否、管理者から教師専用routeへの拒否、古いversionによる管理者更新の競合拒否、生徒・教師・管理者の代表portal route間の許可・拒否、提出確認・評価確認・アンケート結果確認・授業演習コード確認の学校所有範囲までをPlaywrightで確認 | Playwright論理テストケース25件を個別受入済み。全体の30隔離fixture実行バッチ（中間失敗を含む）とPlaywrightの29 successful executions / 23 browser fixture-run invocationsは別集計。進捗・提出確認・評価・アンケート・授業演習コード確認の教師所有範囲は認証HTTPでも確認。その他の主要導線・権限境界は網羅していない |
| ブラウザー自動化 | ホストにNode.jsはない。公式Playwright 1.64.0 Docker image内のNode/npmと同梱Chromiumを利用。`e2e` lockfileに基づく`npm ci`とChromium実行に成功 | Playwright primary tierを実行。各fixture batchのexit code 0を記録。未確認フローは合格扱いしない |
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

- 実生徒データ/実提出/コードログのGemini送信と採点品質校正。合成データのみのGemini診断/smokeは2026-10-15にユーザー承認を受けて実施し、結果は本書末尾に記録する。
- 本番AWSへのデプロイ、新release受入、Phase 2、本番データ操作、費用/quota/負荷・災害復旧試験。
- アンケート設問設定UIの新規追加。現在有効なアンケート・設問・選択肢は確定済みのテスト基準として扱い、工程20ではその現行設定を使って表示・回答保存・再読込を検証する。テストのための設定変更や既存回答データの変更は行わない。
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
| P20-05 | 教師の確認系 | 進捗→提出内容→評価→アンケート結果の主要導線、検索/絞込/ソート、対象外教師の拒否、CSV/JSON等の出力。生徒アンケートでは現在有効な設問・選択肢設定を変更せずに表示・回答・保存後再読込を確認 | 表示・集計・出力がDBの合成fixtureと一致。アンケート画面/回答は現行の確定設定に一致し、テストで設定や既存回答を改変しない。仮名化/匿名化要件と担当範囲を守り、対象外データを含めない |
| P20-06 | 生徒エディター未確認ケース | (a) 30秒スナップショット保存とDB再読込、(b) 二つのブラウザーcontextから同じdraft versionで同時保存し楽観ロック競合、(c) 重複提出の冪等性、(d) 提出後の再提出確定とrevision/履歴 | 各ケースに自動化された回帰テストを追加または既存テストを明示的に特定。同時要求のHTTP処理区間が重なり、旧versionを持つ2要求は一方だけ成功、他方が409 conflictとなる。DB再読込が成功側コードと一致し、競合側が勝者データを上書きしない。再送がデータを失わずrevision・状態・履歴が一貫する |
| P20-07 | 期限後提出deny通知 | N166期限切れ通知をdeny設定にし、認証生徒が選択したときの遷移をHTTPとブラウザーで確認。期限延長/別割当/他人のassignmentへの直接アクセスも確認 | denyは提出可能なeditorへ進まず、生徒ホーム等の仕様どおりの画面へ遷移。サーバー側でも本人/割当/期限を再検証し、DBを変更しない |
| P20-08 | 評価例DAO不具合 | 下記の「既知DAO不具合の再現・修正・回帰」に従う | 回帰テストが修正前の誤動作を再現し、修正後は最新生成行のみactive、古いinactive行はinactiveのまま。関連保存/競合テストも成功 |
| P20-09 | ブラウザー横断受入 | 少なくとも教師の課題公開/改訂、管理者の管理操作、対象生徒の通知→課題、権限拒否の4フローを認証状態で確認。画面サイズは対象画面の通常幅と狭幅を確認 | 実画面の表示/操作/遷移とHTTP/DB結果が一致。Node/Playwrightが利用可能なら自動化し、利用不能時は代替範囲・未検証gapを明記 |
| P20-10 | 最終回帰・清掃 | 対象focused testsと標準全体buildを再実行し、専用環境を停止/削除。作業ツリーと共有サービスへの影響を確認 | 終了コード0、失敗/エラー0。残skipと未検証範囲に説明がある。専用fixture/container/schemaが清掃され、共有8080/DBに予期しない変更がない |

### P20-06 二つのeditor contextからの同時保存競合（2026-10-15）

- `sh scripts/testing/student-editor-concurrency-local.sh`を専用tmpfs MySQL・Tomcat・Python runner・Playwright Chromiumで2回実行。各runでV1–V34のmigration/validation、editor fixture JUnit 1 test、WAR buildが成功し、Playwrightは1/1 passed、exit 0。2つの独立browser contextで同じstudent accountを認証し、同じ初期`draftUpdatedAt`を読む。Playwright route barrierは両方のPOSTが到着するまで待ち、両方を解放して同時要求を発生させる。測定したrequest intervalは実際に重複し、近接したstart timeだけでなくoverlap assertionも満たした。
- 両要求は同一の`draftUpdatedAt`と別々の合成コードを送信。結果は順序を固定せず、HTTP 200が1件・HTTP 409が1件となることを確認した。成功応答は`status=saved`、競合応答は`errorCode=draft_conflict`とし、両contextのeditorを再読込して、保存内容が成功要求のコードに一致し競合要求で上書きされないことを検証した。
- 初回runnerでは通常アプリ構成に必要な`PYTHON_RUNNER_URL`/tokenと隔離Python runnerを起動しておらず、editor GETがHTTP 500（以後servletがunavailableとなり別contextは404）でPlaywrightが失敗した。Tomcatログで`PYTHON_RUNNER_URL must be configured`を特定し、専用network内に制限付きPython runnerを追加して再実行した。修正後は2回連続成功。失敗コマンドと修正の詳細は[エラーレポート](../error-report.md#工程20-p20-06-同時保存競合browser受入runner構成ミスと修正2026-10-15)を参照。
- runner後処理でMySQL/Tomcat/Python runner/Gradle/browser containerとnetworkを削除し、WAR/log fixture artifactを除去。専用port 18099は解放済み。実データ、外部AI、共有app/DBは使用していない。fresh schema migrationの存在しないroutine drop `42000/1305`は非致命で、migrate/validate成功。

### 教師アカウント停止・復帰・論理削除の認証browser受入（2026-10-15）

- 専用tmpfs MySQL、専用Tomcat port 18098、合成管理者/教師fixtureを使用し、`sh scripts/testing/teacher-account-revocation-local.sh`で実行。exit 0。V1–V34 migration/validation、fixture生成JUnit 1/1 passed、WAR生成、Playwright 1/1 passed。
- ブラウザーで教師の初回パスワード変更と課題画面HTTP 200を確認。管理者による停止POSTはHTTP 200、再読込後に「停止中」となり、既存sessionの保護routeはloginへredirect、停止中アカウントの再ログインも拒否された。
- 停止解除POSTはHTTP 200。教師は再ログインでき、課題画面HTTP 200を確認。続く論理削除POSTもHTTP 200となり、行は「削除済み」、状態操作ボタンは非表示、教師sessionは失効し、再ログインは拒否された。操作履歴の削除行も「成功」と表示された。
- 論理削除後、管理者セッションから有効なCSRF/確認情報と最新versionを付けた`activate` POSTを直接送信し、HTTP 400と「削除済み」エラーで復元が拒否されることを確認。操作履歴には成功した削除と失敗した復元操作の両方が記録された。初回実行では監査表に過去の成功した停止解除もありlocatorが2行に一致してPlaywright exit 1となったため、locatorを「停止解除」かつ「失敗」に絞り、再実行で1/1 passed。失敗経緯は[エラーレポート](../error-report.md#工程20-p20-03-教師アカウント停止解除論理削除のbrowser受入2026-10-15-0149-jst)を参照。
- runnerのcleanup後に専用container/network、WAR、fixtureが残っていないこと、port 18098解放、共有教師login HTTP 200を確認。共有DB/アプリへの書込みなし。fresh schema migration中に不存在routine削除のMySQL `42000/1305`メッセージは出たが、migrate/validateと全テストは成功。詳細は[エラーレポート](../error-report.md)を参照。
- この受入は教師アカウント状態境界の一経路に限る。P20-03/P20-09の残る全ロール・所有範囲・主要導線、他ブラウザー/OSは未確認で、工程20は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### 教師ロールから管理者専用画面・作成APIへの直接アクセス拒否（2026-10-15）

- 教師として課題画面へ認証後、`GET /admin/teachers`はHTTP 403、同じ教師sessionの有効なCSRF tokenを含めた`POST /admin/teachers`（教師作成）もHTTP 403となった。管理者画面を再読込し、要求した合成アカウントが作成されていないことを確認した。同じ教師の許可route `/teacher/task`はHTTP 200、権限外`/teacher/submissions`はHTTP 403。
- `sh scripts/testing/teacher-account-revocation-local.sh`の最新実行はexit 0。Playwright 1.64.0/Chromium 1 test passed、0 failed。専用fixture/container/network/WARは削除済みで、共有教師loginはHTTP 200。詳細は[エラーレポート](../error-report.md)を参照。
- 初回はTomcatの403 HTML応答をJSONとしてparseしてテストが失敗したため、statusとtext bodyを検証するよう修正した。製品コードの不具合ではない。
- この確認は教師ロールによる管理者専用routeの拒否に限る。全機能の全ロール・所有範囲、他ブラウザー/OSは未確認であり、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### 現行アンケート設定を前提とする部分検証（2026-10-14）

- 機能仕様REQ-SURVEY-179に従い、現在の有効設定を確定したテスト基準とし、設問設定UIの追加やDB上の設問・選択肢・回答の変更は行わない。
- 現行ローカルDBをread-onlyで確認し、有効survey 1件（survey ID 4）、6設問、30選択肢を確認。`StudentSurveyUiContractTest` 4件と`StudentSurveyOptionLabelTest` 5件は成功し、現在のDB値を変更せず動的設問表示契約とQ3/Q4の表示ラベル/保存値分離を検証した。
- 同じバッチで課題編集View契約テスト4件も成功。専用DB/合成教師の実ブラウザーでヒントタイトルの大小文字保持、Python CodeMirrorプレビュー、行番号なし、通常幅/375px表示、課題一覧の状態セルと操作列分離・公開可否を確認した。
- 2026-10-14追加受入: 専用Compose project `ppe-p20-survey-acceptance-20261014`、DB `ppe_teacher_review_test_surveyacc`、app port 18097でV1–V34をmigration/validate。共有DBから有効survey ID 4の定義（survey/6 questions/30 options）のみ読み取り、合成課題へ紐付けた。共有側の設定・回答は変更せず、回答データや実生徒データも複製していない。
- 専用DBの`StudentSurveyAcceptanceFixtureTest`は1 test、1 passed、0 skipped/failures/errors、exit 0。Playwright `current-survey-settings.spec.js`は2 tests、2 passed、0 failed、exit 0。生徒の未認証login画面/無効資格情報、現行6設問・Q3/Q4表示ラベル、下書き保存後の再表示、提出後の参照専用表示、同意撤回状態の別生徒アクセスHTTP 403を確認。DB read-backはsubmitted response 1件、6設問回答、全保存値4、別生徒の回答0件。
- 同ブラウザーbatchで教師の課題一覧に「設定済み」状態セルとプロンプト設定操作が別表示されることを確認。1280px/375pxの両方でページ横溢れなし、混在大小文字のヒント名`MiXeD Hint`とPython CodeMirrorプレビューを確認し、入力/プレビュー双方の行番号は0件。
- 実施時のDB接続権限・schema名誤り、読み取りSQLの列名誤り、未認証statusの誤期待、およびfieldset状態matcherの誤判定は[エラーレポート](../error-report.md)に記録した。修正後のfixture/ブラウザー試験は成功。専用DB/runner/app/browser container/volume/networkは清掃し、共有appは維持した。
- この結果はP20-05の生徒アンケート設定・保存/再読込と対象課題UIの範囲に限る。未実行opt-in試験、工程20全主要導線/所有境界、他ブラウザー/OS、実運用/本番確認は未完了のため、工程20は`PARTIAL / NEEDS_SIGNOFF`を維持する。

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
- 教師セルフアカウントの追加DB受入（2026-10-14）: 専用project `ppe-p20-self-account-20261014` / DB `ppe_teacher_self_test_p20_20261014` の空DBへV1–V34を適用・validateし、`TeacherSelfAccountDatabaseTest` 4 tests、4 passed、0 skipped/failures/errors、exit 0。初回必須変更から任意変更、管理者reset後の再設定、入力拒否/write failure時のrollback、古いversion・停止済み教師・偽装role拒否、同時更新競合を確認。DB read-backでuser/credential history/reset record/audit各0件、trust値0を確認後、専用container/volume/networkを削除し、残存各0。Compose変数の渡し忘れで起きた初回接続失敗とDB未選択の読み取り失敗は修正済みで、詳細は[エラーレポート](../error-report.md)に記録した。
- 教師の生徒アカウント管理DB受入（2026-10-14）: 専用project `ppe-p20-student-accounts-20261014` / DB `ppe_student_accounts_test_p20_20261014` の空DBへV1–V34を適用・validateし、`StudentAccountManagementDatabaseTest` 2 tests、2 passed、0 skipped/failures/errors、exit 0。security level 1/2の一括作成、個別資格情報/認証、初回変更、reset・再利用拒否、権限/学校/クラス境界、session失効、並行採番、失敗時rollbackを確認。DB read-backですべてのfixture関連行0件、trust値0。専用container/volume/networkは削除し残存各0。初回失敗はruntime-only合成暗号鍵のBase64形式誤りであり、修正後成功。詳細は[エラーレポート](../error-report.md)を参照。
- P20-04 学習開始後の同一系列改訂拒否（2026-10-15）: 新規tmpfs MySQLの`ppe_teacher_task_test_started_revision`で`flywayMigrate flywayValidate`を実行後、`TEACHER_TASK_DB_TEST=true`を設定して既存の`TeacherTaskDatabaseTest.refusesRevisionWhenAnyStudentHasStartedThePublishedTask`を単独実行。Gradle 8.10.2/JDK 21、exit 0、1 test passed、0 failed/errors/skipped。合成教師・学校・公開課題・クラス割当・生徒をfixtureとして作り、当該生徒の学習状態を`in_progress`へ更新してから改訂を要求し、拒否例外、元課題と割当の`published`状態維持をassertする。外部AI・共有DB/アプリは使用していない。
- この一回限りのrunnerでは終了trapのownership label lookupが引用符の誤りでcleanupできず、DB container/networkが残った。`docker inspect`でrunのowner label `87136`を確認し、同labelが一致した専用runner/DB containerとnetworkだけを個別に削除した。エラーと清掃結果は[エラーレポート](../error-report.md#工程20-p20-04学習開始後の改訂拒否db受入と一時環境清掃2026-10-15)に記録した。共有DB/アプリには影響なし。

### P20-05 教師の進捗・提出確認・評価・アンケート・通知（DB/HTTP/browser受入済み）

- `TeacherProgressDatabaseTest`: 専用`ppe_teacher_progress_test_phase20`で4 tests、4 passed、0 failures/errors/skips、exit 0。進捗集計・活動詳細・コード閲覧と権限剥奪を実DBで確認。
- `TeacherReviewDatabaseTest`: 新規空schemaで2 tests、2 passed、0 failures/errors/skips、exit 0。提出内容/評価スナップショット/証拠範囲/教師所有権を確認。
- `TeacherSurveyDatabaseTest`: `TeacherReviewFixture`とは独立した空の専用schemaで1 test、1 passed、0 failures/errors/skips、exit 0。回答状態、同意・所属条件、権限、匿名/個人識別可能なexport、read immutabilityを確認。
- `StudentNotificationDatabaseTest`: 専用review fixture DBで19 tests、19 passed、0 failures/errors/skips、exit 0。今回は再オープン時の既読状態・通知件数の再読込assertも追加し、本人所有外/利用不能な評価対象の拒否と既読永続化を確認。
- いずれも合成fixtureのみで外部AIなし。加えて`TeacherReviewRuntimeTest`の隔離認証HTTPで、教師の表示/詳細/提出・評価出力、全sort×昇降順の一覧とCSV行順一致、担当外教師の404、権限なし教師・生徒・管理者の拒否を確認済み。`teacher-review-mobile.spec.js`は進捗/提出/評価/アンケートの4画面、CSV/TXT download、survey exportの識別子非表示、390pxでのページ横overflowなしをPlaywrightで確認した（関連する`expired-deny-notification.spec.js`を合わせて3 tests passed、exit 0）。従ってP20-05のDB/HTTP/browser証跡は揃っている。対象外教師の全機能/全所有境界や全主要導線の網羅はP20-03/P20-09の残課題として扱い、このsliceの合格を工程20全体の完了とはしない。

### P20-06 生徒エディター未確認ケース（完了）

- 教師課題MySQL suiteで30秒snapshot event、DBへのsnapshot本文保存と再読込、stale versionのHTTP相当control拒否、同一冪等キーで既存提出を返す処理、再提出版2の保存と提出を確認した。Playwrightでも実30秒周期のsnapshot POST・保存表示・再読込後のDB由来コード、および二つのbrowser context間のstale保存HTTP 409と勝者データの再読込を確認した。
- `StudentExerciseDatabaseTest`: Python runnerが起動している隔離環境で37 tests、37 passed、0 failures/errors/skips、exit 0。所有者境界の拒否後もDB上の所有者データが不変である新しい回帰ケースを含む。
- 2026-10-13提出前I/O follow-up: ユーザー承認により、提出前チェックだけPython `input(prompt)`のpromptを比較stdoutから除外する実装を追加。通常実行・対話実行のprompt表示と明示的なprint出力は維持する。Python runner unit/API testsは24 passed、Java `StudentEditorControlTest`は1 passed。ローカル8080の`programming-process-evaluator-app-live`（DB `programming_process_evaluator`、port 8080）とrunnerを更新し、認証済みbrowserでassignment 17の提出前チェック2/2一致、通常実行のprompt表示・入力`1 2 3`・出力`6`を確認した。最終提出は行わず、通常実行2回分の合成demo execution logのみ保存された。詳細は[Runtime Validation Report](./runtime-validation-report.md)および[エラーレポート](../error-report.md#rev171-提出前python入力プロンプト比較の修正検証2026-10-13-2220-jst)を参照。
- 2026-10-13再読込後follow-up: 合成課題`assignmentId=17`を再読込後、提出前チェックを再実行し、実出力`6` / `8`、2/2一致を確認した。通常clickと強制clickはbrowser toolでタイムアウトし、locator clickイベントをdispatchする代替操作を使ったため、この追試だけをポインター操作受入の証拠とはしない。最終提出・下書き更新はしていない。再読込後の実行結果表示は機能仕様が課題エディターに要求しておらず、保存結果再読込の計画対象にも含まれないため、今回の工程20不具合とは判定しない。詳細は[エラーレポート](../error-report.md#工程20-生徒エディター提出前チェックの再確認2026-10-13-2254-jst)を参照。
- 2026-10-14 P20-06実提出受入: 新規の`editor-submit-resubmission.spec.js`で、合成生徒の認証失敗/成功、提出前テスト1/1一致、提出確定後の評価画面v1、エディターから再提出編集を開始、2回目の提出確定後の評価画面v2をPlaywrightで確認。最終DB read-backはrevision 1/2とも`accepted`で、submitted codeには異なる合成識別コメントを保持。`TeacherTaskDatabaseTest.duplicateSubmissionIsIdempotentAndResubmissionPersistsTheNextRevision`もV34適用済みの隔離MySQLで1 passed。詳細は[Runtime Validation Report](./runtime-validation-report.md)と[エラーレポート](../error-report.md#工程20-p20-06-合成生徒の提出再提出e2e2026-10-14)を参照。
- Geminiの実API呼び出しは行わず、専用appの`GEMINI_API_KEY`を空にしてから提出操作を実施した。システム生成の評価requestは資格情報がないため`failed`となったが、提出と再提出の状態・revision保存は合格。AI評価結果や実API稼働は本受入範囲外としており、合格扱いしない。
- P20-06の提出・再提出ブラウザー経路とDB冪等境界は受入済み。他ブラウザー/OS・全状態組合せは未確認であり、P20-09や工程20全体の完了を意味しない。

### P20-07 期限後提出拒否・評価/アンケート・通知接続（部分完了）

- `TeacherTaskDatabaseTest.expiredDenyAssignmentDoesNotCreateStudentParticipation`で、期限切れ`DENY`割当を処理した後に対象学生がeditorから除外され、participationが生成されないことを専用MySQLで確認した。
- P20-05の評価・survey・通知DB試験は合格。追加Playwright受入で期限後`DENY`通知のクリックから`/student/home#taskListSection`への遷移とeditor直接URLのHTTP 404を確認した。教師の進捗/提出/評価/アンケート画面と出力、および390pxでの横overflowなしも確認済み。他の教師/生徒主要導線・権限境界は未網羅。

### P20-08 評価例DAO不具合（合格）

- 古いinactive例と今回の生成例が同一prompt versionにある状態をMySQL回帰テストで作り、修正前に古い行がactive化される不具合を再現した。`TeacherPromptDao.saveEvaluationExamples`を最新生成IDだけ更新するよう修正した。
- 修正後の`TeacherTaskDatabaseTest`は39 tests、34 passed、0 failures、0 errors、5 skipped、exit 0。opt-in fixtureのskipはpassへ含めない。エラーと対応は[エラーレポート](../error-report.md)に記録した。

### P20-09 ブラウザー横断受入（主要受入完了・追加網羅未完了）

- ユーザーの選択後、Node.js v24.21.0、npm/npx 11.19.0、Playwright 1.64.0とChromiumを利用可能なユーザー領域へ用意。先行受入時に`e2e/`へ3 specを追加し、その後N166/教師レビュー用に2 spec、即時公開・予約公開受入用に2 specを追加（計7 spec）。fixture/passwordは専用DB・合成アカウントだけで使用。秘密値はレポートへ記録しない。
- 最終コマンド: `npm --prefix e2e run test:phase20`（Node.js v24.21.0をPATHへ追加、教師/生徒fixture・assignment IDは環境変数）。exit 0、3 tests passed、0 failed。認証specで誤資格情報拒否、教師ログインと課題画面遷移、教師から管理者画面へのアクセスがHTTP 403となる境界を確認。autosave specで誤資格情報拒否、ログイン後にコード編集し30秒周期`periodic_snapshot` POST成功、保存表示と再読込後のDB由来コードを確認。concurrency specで二つのbrowser contextを使い、1つ目の保存後のstaleな2つ目の保存がHTTP 409となり、1つ目の保存内容が再読込後も維持されることを確認。
- 初回Playwrightはexit 1で3件失敗（教師landing pathの想定違い1、CodeMirror複数一致2）。修正後の中間実行ではauth 1件がbaseURLの学生Hostへ誤って戻るため失敗し、editor 2件は成功。teacher現在Hostを基準にadmin URLを構築し直した後、上記3件すべて合格。失敗・原因・修正・再検証は[エラーレポート](../error-report.md)に記録。
- 追加した通知・教師レビューspecは、専用DB `ppe_teacher_review_test_phase20` / Compose project `ppe-p20-20261014`で実行。Java fixture test 1件とPlaywright 3 testsはそれぞれexit 0。期限後`DENY`通知遷移、editor直接URL拒否、教師の進捗/提出/評価/アンケート画面、提出・評価・survey CSV/text download、survey exportの匿名化、各画面390pxでのdocument横overflowなしを確認した。CSV本文はPlaywright Response本文ではなく実download fileから検証した。migrationはV1–V33、`log_bin_trust_function_creators`は`0`へ復元し、専用Compose資源・DBを清掃済み。
- follow-up実行: `npm --prefix e2e run test:phase20 -- phase20/expired-deny-notification.spec.js phase20/teacher-review-mobile.spec.js` — exit 0、3 passed、0 failed。詳細は[エラーレポート](../error-report.md)と[runtime validation report](./runtime-validation-report.md)に記録。
- 課題公開のfollow-up: `cd e2e && npx playwright test phase20/teacher-publication.spec.js --config=playwright.config.js`（Node.js v24.21.0、base URL/合成アカウントは環境変数）— 最終実行exit 0、1 passed、0 failed。初回は部分一致locatorが3リンクに一致しexit 1、完全一致hrefへ修正。2回目は全体50秒timeoutでexit 1、Playwright traceではeditor GETがHTTP 200（約18.6秒）だった。spec個別timeoutを120秒にし、browser contextを並列closeする修正後、1 passed（3.8秒）。各失敗・修正・再検証は[エラーレポート](../error-report.md#工程20-課題公開から生徒editorまでのplaywright受入2026-10-13-1259-jst)に記録。
- 予約公開worker・通知のfollow-up: 専用Compose project `ppe-p20-schedpub-a1`、専用DB `ppe_teacher_task_test_scheduledpub`、port 18092を使用。V1–V33のmigration/validationはexit 0。`createsRetainedScheduledPublicationFixtureForPhase20BrowserAcceptance`はexit 0、1 passedでscheduled assignmentと通知0件を確認し、fixtureにはID類のみ記録。アプリ起動後に対象assignmentだけをdueにし、実workerによる`scheduled -> published`、system監査記録と`task_published`通知1件をDBで確認した。`npm --prefix e2e run test:phase20 -- phase20/scheduled-publication-notification.spec.js`はexit 0、1 passed。認証失敗/成功、通知が一度だけ未読表示されること、通知クリック後の同じassignment editor表示、`read_at`永続化を確認した。Gemini API・実データなし。初回V11 migration error 1419、Compose環境変数のスコープ/起動直後DB probe、検証SQLの未知列は[エラーレポート](../error-report.md#工程20-予約公開workerから生徒通知までの受入2026-10-13-1359-jst)に記録。
- 2026-10-13フォルダ行UI follow-up: 機能仕様の`REQ-EXERCISE-170`に合わせ、フォルダ名ボタンと行空き領域から開閉できるようローカル実装を更新。認証済み生徒画面の合成フォルダで、フォルダ名イベント後に展開・path選択・`main.py`表示、行headerイベント後に折りたたみ、三角toggleの開閉、checkbox/menu領域のイベントでは行開閉しないことを確認した。ブラウザーツールのpointer/stable待ちがtimeoutしたため、実pointer操作は未確認。JavaScript/JSP Problems診断はエラー0件、`git diff --check`はexit 0。詳細は[エラーレポート](../error-report.md#工程20-授業演習フォルダ行操作のbrowser受入2026-10-13-2325-jst)を参照。
- 管理者管理操作のfollow-up: 専用Compose project `ppe-p20-admin-management-20261014`、専用DB `ppe_teacher_test_p20_admin_20261014`、app port 18095で検証した。空DBへV1–V34をmigrate/validateし、MySQL function-creator trustを試験前に0へ復元。`TeacherAccountDatabaseTest.createsRetainedAdminManagementBrowserFixture`はexit 0、1 passed。認証済みPlaywright `admin-teacher-management.spec.js`は最終exit 0、1 passed。誤パスワード拒否、固定ID `admin` でのログイン、学校割当と`task-management`権限を選んだ教師アカウント作成、資格情報の一度だけの表示・閉じた後の消去、再読込後のアカウント/権限表示を確認。375px幅でdocument/bodyの横はみ出しなし。DB read-backでもactive account、学校permission、task-management permission、hash済み資格情報を確認した。Gemini APIなし。fixture作成・ブラウザー試験中の不一致と環境設定エラーは[エラーレポート](../error-report.md#工程20-p20-09-管理者教師管理ブラウザー受入2026-10-14)参照。
- 管理者作成教師の認証・権限境界追試: 新規の専用Compose project `ppe-p20-authz-20261014`、DB `ppe_teacher_test_p20authz_20261014`、app port 18096を使用。既存の合成管理者fixtureから教師を作成し、Playwrightで独立BrowserContext、初回必須パスワード変更、再ログイン、許可済み`/teacher/task`のHTTP 200と未付与`/teacher/submissions`のHTTP 403を確認した。実行exit 0、1 passed。DB read-backではactive、`teacher_must_change_password=0`、fixture学校access enabled、`task-management`のみenabled、`submission-review` disabledを確認。V34、`log_bin_trust_function_creators=0`、教師login readiness HTTP 200。起動/runner階層・認証状態・redirect前提の失敗と対処は[エラーレポート](../error-report.md)参照。
- 2026-10-15 P20-03教師停止後の既存セッション失効追試: [teacher-account-revocation-local.sh](../../../scripts/testing/teacher-account-revocation-local.sh)で専用tmpfs MySQL/アプリを起動し、V1–V34 migrate/validate、`TeacherAccountDatabaseTest.createsRetainedAdminManagementBrowserFixture` 1 passed、WAR生成を確認。Playwright `admin-teacher-management.spec.js`は1 passed、exit 0。認証済み教師の許可URL `/teacher/task`がHTTP 200の状態から、管理者の停止POST HTTP 200、再読込後の状態「停止中」、同じ教師sessionでの保護URL再アクセスがlogin URLへ戻ることを確認した。Gradle結果は1 suite / 1 test (1 passed, 0 failed/errors/skipped)。初回のfixture搬送・container hostname/port・更新済み管理画面versionに関するtest harness失敗と修正は[エラーレポート](../error-report.md#工程20-p20-03-管理者による教師停止後のセッション失効2026-10-15-0133-jst)。専用resources/WAR/fixture propertiesは清掃済み。これは停止アカウント境界の一ケースであり、P20-03全機能/全ロール境界の完了を意味しない。
- P20-09の必須4フローは受入済み: 教師の初回公開と対象生徒editor到達は上記`teacher-publication.spec.js`、教師の公開後改訂・再公開は[公開後課題改訂計画](./teacher-task-revision.md)の認証browser記録、管理者の教師アカウント作成/権限保存は上記`admin-teacher-management.spec.js`、対象生徒の通知→課題editor/既読化は`scheduled-publication-notification.spec.js`。権限拒否は`authentication.spec.js`、TeacherTask authorization DB/HTTP/browser slice、および管理者作成教師の課題編集200/提出確認403で確認済み。通常幅/狭幅も各対象画面で確認した。
- 2026-10-14追加のP20-03/P20-09受入: [teacher-exercise-review-local.sh](../../../scripts/testing/teacher-exercise-review-local.sh)のbrowserモードを専用tmpfs MySQL/ネットワークで実行し、`TeacherExerciseDatabaseTest.currentSavedTreesScopeDownloadsCsvAndRevocation` 1 passed、`TeacherExerciseRuntimeTest.authenticatedHttpReadExportsErrorsAndAuditFailure` 1 passed、WAR生成、全てexit 0。教師の担当校/クラス、他校・未付与機能、学校/機能権限の失効、停止済み所属/学校/アカウント、同意境界、読取・CSVの非変更を含む当該機能のDB/HTTP範囲を再確認した。全機能横断のP20-03完了を意味しない。
- 同fixtureの合成生徒で[exercise-folder-row-toggle.spec.js](../../../e2e/phase20/exercise-folder-row-toggle.spec.js)をPlaywright 1.64.0/Chromiumで実行し、1 passed、0 failed、exit 0。誤資格情報の拒否後にログインし、コントロールに重ならないフォルダ行余白の実pointer clickで展開/折りたたみ、フォルダ名ボタンでの展開、DB由来の子ファイル読戻し、クリック操作から非GETリクエストが発生しないことを確認した。これにより以前未確認だった行余白のpointer操作を受入。triangle controlも既存どおり維持される。
- この受入でPlaywright suiteに1 test declarationを追加した。`e2e/phase20/`のsourceには現時点で合計16個の`test()` declarationがあり、計画冒頭の総数16件と一致する。P20-09の受入要件はP20-04〜07の一部ケースと重複するため、独立したP20-09件数は集計せず、上記フローと個別証跡を基準にする。P20-03の全ロール/全機能所有境界、他ブラウザー/OS、OS標準ダイアログなどは引き続き未確認。
- 追加網羅の未確認: 全主要導線/権限境界の網羅、全ブラウザー/OS、OS標準ダイアログ、残るopt-inケース。これはP20-09の最低限受入済みフローと区別して、工程20全体の残課題として保持する。

### P20-10 最終回帰・清掃（部分完了）

- 2026-10-14 最終clean build: 作業ツリーをread-onlyでmountした`gradle:8.10.2-jdk21`の一時container内へ`src/`、Gradle設定、`docs/rubric/`をコピーし、既存のホスト`build/`成果物を削除せずに実行。`gradle --no-daemon --console=plain clean build --warning-mode all` — exit 0、`BUILD SUCCESSFUL`。JUnit XML集計は144 suites / 551 tests、368 passed、0 failures、0 errors、183 skipped。skipはopt-in DB/HTTP/browser/real API環境なしによるものでpassには含めない。WAR生成も成功。`WarPluginConvention`のGradle 9.0 deprecation warningあり。集計scriptもexit 0。Playwrightの12件は各専用fixture環境で個別に受入済みで、このbuildでは再実行していない。
- P20-09管理者ブラウザーfixture追加時のclean build（履歴）: 専用Compose project `ppe-p20-final-20261014`で`docker compose -p ppe-p20-final-20261014 run --rm --no-deps --entrypoint gradle app --no-daemon --console=plain clean build --warning-mode all`を実行。Gradle 8.10.2/JDK 21、exit 0、`BUILD SUCCESSFUL`。JUnit XML集計は144 suites、550 tests、368 passed、0 failures、0 errors、182 skipped。今回の最終clean buildは下記。
- 最終差分検査: `git diff --check`、exit 0。
- 2026-10-13ローカルUI追補: フォルダ名/行の展開・折りたたみを共通処理にし、画面scriptのcache-busterを更新。対象JavaScript/JSPのProblems診断はエラー0件、`git diff --check`はexit 0。`node --check`はNode.jsがPATH上になくexit 127だったため実行できず、browser event dispatchで挙動を追試した。これは直前のclean build後のUI-only変更で、pointer受入は未完了。工程20全体は引き続き`PARTIAL / NEEDS_SIGNOFF`。
- 隔離DBの最終確認: `@@GLOBAL.log_bin_trust_function_creators=0`。`ppe_exercise_test_phase20`、`ppe_phase20_20261013`、`ppe_teacher_distribution_test_phase20`、`ppe_teacher_progress_test_phase20`、`ppe_teacher_review_test_phase20`、`ppe_teacher_review_test_reviewphase20`、`ppe_teacher_review_test_surveyphase20`、`ppe_teacher_task_test_phase20`は専用MySQL内の工程20用schemaとして確認後、削除した。専用Compose project `ppe-p20-20261013`のapp/DB/Python runner、同projectの`gradle-cache`/`mysql` volume、および`ppe-p20-review-phase20` networkを清掃した。既存の`ppe-preparation-20261008_database` volumeと共有サービスは対象外で維持。
- Playwright再開後に追加した`ppe_teacher_task_test_p20_20261013`と`ppe_phase20_20261013`も削除。専用Compose projectのapp/DB/Python runner、project管理の`gradle-cache`/`mysql` volumesと3 networksは`docker compose -p ppe-p20-20261013 down -v`で削除し、残存container/volume/networkがないことを確認。既存共有volume/serviceは対象外。合成専用fixtureのみを操作し、Gemini実API/実データ送信なし。
- 追加の専用Compose project `ppe-p20-20261014`、専用DB `ppe_teacher_review_test_phase20`、fixture properties fileは検証後に削除。MySQLでlatest migration V33、`@@GLOBAL.log_bin_trust_function_creators=0`を確認後、このprojectのapp/db container、project volumes/networkだけを`docker compose -p ppe-p20-20261014 down -v`で削除し、残存なしを確認。共有service/volumeは対象外。
- 課題公開用Compose project `ppe-p20-publish-20261013`ではV1–V33 migration/validation成功、global trust値0、課題/割当の公開状態を確認後、appを停止してclean buildを実行。専用fixture fileはcleanで除去され、Compose projectのapp/db/Python runner、project volumes/networksも検証後に削除した。共有service/volumeは対象外。
- 予約公開用Compose project `ppe-p20-schedpub-a1`ではV1–V33 migration/validation、MySQL `log_bin_trust_function_creators=0`への復元、実workerの遷移/監査/通知1件/既読状態を確認。app、専用DB/volume、Python runner、gradle-cache、network、および合成fixture propertiesを検証後に削除し、共有サービス/volumeは対象外で維持した。
- 認証後のDB再読込、30秒の実時間保存、browser context間競合、N166 deny画面遷移、教師横断画面/出力、390px狭幅、教師による即時公開から対象生徒のeditable editor到達、予約公開workerから通知を経て対象editorへ到達し既読化するまでをPlaywrightで確認済み。標準buildのskip対象178件のopt-in外ケース、他の主要導線/権限境界、別ブラウザー/OS、OS標準ダイアログ、Gemini実API、本番受入は未確認。
- 最新build専用資源の清掃: `docker compose -p ppe-p20-final-20261014 down -v`で同projectのMySQL/Gradle cache volumeとapplication/database/outbound networkを削除。確認結果は残存container 0、volume 0、network 0。共有port 8080の`programming-process-evaluator-app-live`は稼働を維持。
- P20-10の最新clean build/JUnitは144 suites / 551 tests（368 passed、183 skipped、failures/errors 0）、exit 0。管理者作成教師の認証・権限追試はJava sourceを変更しないE2E specのみの変更であり、Playwright最終実行exit 0・1 passedと`git diff --check`を行った。今回の追試を含むPlaywright累計は14件（10環境/バッチ）。VS Code Problemsは`TeacherTaskDatabaseTest.java`をROOT project classpath外として報告し、syntax-only診断である（focused Gradle testではcompile成功）。skipされたopt-inケース、全主要導線/権限境界、他ブラウザー/OS等は未確認のため、工程20全体は`PARTIAL / NEEDS_SIGNOFF`、完了条件未達。最終集計と次の確認範囲は[Runtime Validation Report](./runtime-validation-report.md)を参照。
- 2026-10-14 05:49 JST追試: local8080の合成assignment 17で認証済みエディター画面を390px幅で確認し、提出前I/Oチェックは2/2一致、document/bodyとも390pxで横はみ出しなし。最終提出は行わず、DB・提出状態は変更していない。`/student/home`へのGET後はstudent loginへredirectされ認証セッションを継続できなかったため、生徒ホーム受入と提出/再提出のbrowser縦断は未確認のまま。P20-06は自動DBテストで重複提出・revision 2再提出を検証済みだが、実UI経由の提出・再提出受入が残る。
- 2026-10-14追補: 上記の提出/再提出未確認は、新規の専用合成fixtureを使った認証済みPlaywright受入で解消した。local8080の失効sessionやassignment 17は変更せず、P20-06状態を完了へ更新。専用Compose環境・runner・DB・volume/networkは清掃済みで、残存なしを確認した。
- P20-09管理者ブラウザーfixtureを含む最新ソースで全体clean buildを再実行し、144 suites / 550 tests（368 passed、182 skipped、0 failures/errors）、exit 0を確認した。skipは合格扱いせず、残る工程20の未確認範囲は`NEEDS_SIGNOFF`として保持する。

### P20-04 改訂再公開から生徒通知・新revision editorまでの隔離受入（2026-10-14）

- 専用Compose project `ppe-p20-revision-20261015`、空のMySQL `ppe_teacher_task_test_rev20261015`、app port 18095を使用し、V1–V34の`flywayMigrate flywayValidate`はexit 0。migration中のみ`log_bin_trust_function_creators=1`、試験中/後は0へ戻した。
- `createsRetainedRevisedPublicationFixtureForPhase20BrowserAcceptance`: 1 test、1 passed、0 skipped/failures/errors、exit 0。runtime-onlyの合成資格情報と合成promptを使用。改訂公開要求を同一request tokenで再送しても新版通知は1件。旧task revision 1は`requires_update`、旧assignmentは`archived`、新task revision 2は旧taskを`supersedes_task_id`で参照し`published`、新assignmentは`published`。
- `createsAndPublishesNewRevisionWhilePreservingThePublishedRevisionUntilReplacement`、`generatesFluctuationsForRevisionWithoutDraftOnlyRubricInitialization`、`scheduledRevisionNotifiesStudentsOnlyWhenTheNewRevisionIsPublished`: 3 tests、3 passed、0 skipped/failures/errors、exit 0。予約改訂では予約時点の通知なし、workerによる実公開時の通知と再実行時の重複防止も確認。
- 専用アプリ起動後、教師/生徒login pageは各HTTP 200。Playwright `revision-publication-notification.spec.js`: 1 test passed、exit 0。無効資格情報拒否、教師ログイン後の旧課題「要更新」/新版「公開中」、生徒ログイン、新版通知の未読表示、通知クリックから新assignment editorへの遷移、改訂タイトル表示、通知の既読化を確認した。
- Playwright結果後のMySQL read-backで旧assignment `archived`、新版assignment `published`、新assignment宛の`task_published`通知が1件、`read_at`設定済みを確認。外部AI・実データなし。既存の工程10 browser受入には教師UIから改訂案を作成・設定・再公開した記録もあるため、今回その操作を重複させず、工程20用の合成設定済みfixtureで通知境界を隔離確認した。
- 初回実行におけるCompose opt-in env不足によるJUnit skip、ホストport/Chromium `.localhost` routing、internal networkのnpm registry DNS、Grettyと共用Gradle cache lockの各エラーは修正/回避後に再試験成功。詳細は[エラーレポート](../error-report.md#工程20-p20-04-改訂再公開通知ブラウザー受入2026-10-14-1052-jst)を参照。
- 専用Compose project/container/volume/networkとfixtureを削除し、共有8080/DBを維持。P20-04の課題改訂・通知・生徒遷移条件を受入済み。工程20全体は他段階の未確認範囲が残るため`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 同一校peer教師によるコード配信テンプレート共有・他校拒否（2026-10-14）

- 専用tmpfs MySQL、隔離Tomcat、Playwright Chromiumで検証。Gradleの`flywayMigrate flywayValidate`、`TeacherDistributionDatabaseTest.createsRetainedPeerTemplateForPhase20BrowserAcceptance`（1 test）、WAR生成はexit 0。Playwright `phase20/teacher-distribution-peer-edit.spec.js`もexit 0、1 passed、0 failed。
- 合成owner教師が登録・配信したテンプレートを同一校のpeer教師が一覧から開き、名称/コードを編集して保存し、再読込できることを確認。owner側の再読込でも編集内容が維持された。配信履歴の名称は配信時snapshotのまま変化しないことを併せて確認した。
- 別校教師では共有テンプレートが一覧に現れず、テンプレートIDを指定した直接アクセスもHTTP 403。誤資格情報の拒否と有効な各教師の認証も確認。
- 初回3回のPlaywright失敗は、ログイン後の実際のlanding path、配信履歴snapshotと編集可能テンプレートの区別、APIRequestContextとChromiumのDNS設定差をテスト側で誤認したもの。期待値/アクセス方法を修正した最終実行は成功し、詳細は[エラーレポート](../error-report.md)に記録した。
- 専用container/networkとtmpfs DBは削除し、共有app/DBは変更していない。P20-03の同一校peer共有/他校拒否スライスは受入済みだが、全機能のロール/所有境界、他ブラウザー/OS等は未確認であり、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 同一校peer教師による課題下書き編集・他校拒否（2026-10-14）

- 空の専用tmpfs MySQLへV1–V34を適用・validateし、`TeacherTaskDatabaseTest.createsRetainedPeerDraftFixtureForPhase20BrowserAcceptance` 1 testとWAR生成はexit 0。専用Tomcatの教師loginはHTTP 200で起動確認。
- Playwright `teacher-task-peer-draft-ownership.spec.js`は1 test passed、0 failed、exit 0。peer教師の誤資格情報を拒否した後、同一校の別教師がowner作成の下書きを一覧から開き、名称を更新・保存できた。ownerで再ログイン後にも変更後タイトルを再読込できた。他校教師には下書きが一覧表示されず、直接URLはHTTP 403。
- 既存の`sameSchoolTaskManagerCanLoadAnotherTeachersDraft`と`sameSchoolTaskManagerCanEditDeleteAndRestoreAnotherTeachersTask`は、retained fixture生成とは別のGradle invocationで実施し、2 tests passed、0 failed、exit 0。
- 初回はretained fixture用環境変数をGradleの他DBテストにも継承し、`teacher-demo`重複でexit 1となった。フラグをfixture生成testに限定してDBテストを分離し解消。詳細は[エラーレポート](../error-report.md#工程20-p20-03-教師の課題下書き所有境界browser受入2026-10-14-1810-jst)を参照する。
- 専用container/network/tmpfs DBを削除し、共有8080/DBを変更していない。P20-03では下書き課題とコード配信テンプレートの同一校共有/他校拒否を各確認したが、他機能の全所有境界・未実行opt-in・他ブラウザー/OSは未確認。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 同一校・課題管理権限なし教師の直接アクセス拒否（2026-10-14）

- 既存のpeer下書きfixtureに、同一校所属・有効アカウントだが`task-management`が無効な合成教師を追加した。`teacher-prompt-design`は有効として認証後の`/teacher/prompt`到達を確認できる構成にした。
- 空の専用DBにV1–V34を適用・validateし、fixture生成を含むGradle testとWAR生成はexit 0。Playwright `teacher-task-peer-draft-ownership.spec.js`は1 test passed、0 failed、exit 0。
- 同一校peerの課題管理権限がある場合は下書き編集・保存とownerからの再読込が成功。他校教師と、同一校でも課題管理権限がない教師は課題行を表示できず、後者を含む直接GETはいずれもHTTP 403となった。DB read-backでも同一校permission=`enabled`かつ`task-management=0`を確認した。
- 初回は空のGradle cacheでoffline plugin解決に失敗し、オンライン再試行もGradle Plugin Portal artifactのTLS handshakeで失敗した。既存のGradle依存cacheを専用volumeへ複製して解消した。次のmigrationはMySQL binary logging下のV11 routine作成時にError 1419となり、`log_bin_trust_function_creators=1`をmigration中だけ専用DBへ設定した。失敗migrationの残る専用schemaを破棄・再作成してV1–V34を再適用し、その後の値`0`を確認した。詳細は[エラーレポート](../error-report.md#工程20-p20-03-課題管理権限のない同一校教師による直接アクセス拒否2026-10-14-1833-jst)を参照。
- 検証後に専用container/volume/networkとfixture propertiesを削除し、残存各0を確認。共有教師ログインはHTTP 200。これは課題編集routeの一つの機能権限境界であり、他feature routeや工程20全体の完了を意味しない。

### P20-09 管理者更新のstale version拒否（2026-10-15）

- `sh scripts/testing/teacher-account-revocation-local.sh`で専用tmpfs MySQL、隔離Tomcat、Playwright 1.64.0/Chromiumを起動。空schemaのV1–V34 migrate/validate、管理者ブラウザーfixture生成JUnit 1 test、WAR生成は成功。
- 管理者の教師一覧から最新versionを取得した後、意図的に古いversionを使って合成教師の停止POSTを送信。HTTP 400と「更新されています」の応答を確認し、再読込後も状態は「利用中」、versionも変化しなかった。最新versionでの停止は成功し、操作履歴には正確に「停止」操作の失敗監査が1行記録された。
- 初回Playwright実行は「停止」だけの部分一致locatorが「停止解除」の失敗行も拾い、1 test failedでexit 1。操作列セルの完全一致に修正後、同じrunnerの再実行はexit 0、Playwright 1 passed、0 failed。JUnit fixtureも1 passed、0 skipped/failures/errors。詳細は[エラーレポート](../error-report.md#工程20-p20-09-管理者更新のstale-version拒否browser受入2026-10-15)を参照。
- runner終了後port 18098は解放され、専用container/network/WAR/fixtureは削除済み。共有DB/アプリ、実利用データ、外部AIは使用していない。工程20全体は引き続き`PARTIAL / NEEDS_SIGNOFF`。

### P20-03 生徒セッションから教師専用routeへの直接アクセス拒否（2026-10-15）

- `sh scripts/testing/student-exercise-layout-local.sh`で専用tmpfs MySQL/Tomcatと合成生徒fixtureを用意。空DB migration、WAR生成、隔離アプリ起動、Playwright 1.64.0/Chromiumを同じrunner内で実施。
- 生徒の誤資格情報ログインは拒否され、正しい合成資格情報では`/student/home`へログインできた。生徒portal hostと教師portal hostは別なので、host routing redirectを迂回して認可filterを直接検証するため、同じ合成生徒sessionの`JSESSIONID`をテストbrowser内でteacher.localhostへ設定し、`GET` `/teacher/task`と`/admin/teachers`を実行。どちらもHTTP 403。
- 同じPlaywright invocationで狭幅フォルダ行の既存確認も実行し、2 tests passed、0 failed、exit 0。Playwright前のGradle migration/buildも成功。検証後はrunnerが専用container/networkを清掃した。詳細は[エラーレポート](../error-report.md#工程20-p20-03-生徒セッションから教師専用routeへの直接アクセス拒否2026-10-15)を参照。
- これは認証後のロール判定1 routeの確認であり、他の教師route、browser/OS組合せ、全ロール/所有範囲の網羅とは扱わない。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 教師セッションから生徒専用routeへの直接アクセス拒否（2026-10-15）

- `sh scripts/testing/teacher-account-revocation-local.sh`で専用tmpfs MySQL、隔離Tomcat、Playwright 1.64.0/Chromiumを起動。V1–V34 migration/validation、管理者browser fixture生成JUnit 1 test、WAR生成が成功し、Playwrightは1 test passed、0 failed、exit 0。
- 管理者が作成した課題管理権限付きの合成教師で、初回パスワード変更後に`/teacher/task`へHTTP 200で到達することを確認。その認証済み教師sessionの`JSESSIONID`を`student.localhost`へ設定して`GET /student/home`を要求し、HTTP 403で拒否されることを確認した。portal host routingを通過したうえで、生徒専用routeのロール境界を直接検証した。
- runner終了後に専用container/network/WAR/fixtureが削除され、port 18098が解放された。共有DB/アプリ、実利用データ、外部AIは使用していない。空schema migration時に存在しないroutine dropのMySQL SQL State `42000` / Error Code `1305`が出たが、migrate/validateと全検証は成功。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。詳細は[エラーレポート](../error-report.md#工程20-p20-03-教師セッションから生徒専用routeへの直接アクセス拒否2026-10-15)を参照。

### P20-03 管理者セッションから生徒専用routeへの直接アクセス拒否（2026-10-15）

- `sh scripts/testing/teacher-account-revocation-local.sh`を専用tmpfs MySQL/Tomcat/Playwrightで実行。V1–V34 migrate/validate、管理者fixture JUnit 1 passed、WAR build、Playwright 1 passed、いずれもexit 0。
- 管理者として`/admin/teachers`へ認証後、同じ`JSESSIONID`を`student.localhost`へ設定して`GET /student/home`を行い、HTTP 403を確認。管理者ページへ戻った後も管理画面が有効なことを確認し、続く教師作成・停止/削除の既存browser flowも成功した。
- 専用container/network/WAR/fixtureはrunner cleanup後に残存せず、port 18098も解放。共有DB/アプリ、実利用データ、外部AIは使用していない。このroute境界の成功は工程20全体の完了を意味せず、判定は`PARTIAL / NEEDS_SIGNOFF`。

### P20-03 管理者セッションから教師専用routeへの直接アクセス拒否（2026-10-15）

- `sh scripts/testing/teacher-account-revocation-local.sh`を実行。V1–V34 migration/validation、管理者fixture JUnit 1/1、WAR build、Playwright 1/1がすべてexit 0。
- 管理者として`/admin/teachers`に認証したsessionの`JSESSIONID`を`teacher.localhost`にも適用し、教師専用`GET /teacher/task`がHTTP 403で拒否されることを確認。その後`/admin/teachers`へ戻り、管理者専用画面が引き続きHTTP 200で利用できること、および既存の教師作成・停止・削除browser flowが成功することを確認した。
- 専用fixture/実行環境はrunner終了後に清掃。共有DB/アプリ、実利用データ、外部AIは使用していない。空schema migration中に既知のnonfatal missing-routine drop warning（MySQL 1305）が出たが、migration/validationおよび受入は成功。代表role境界の追加受入であり、全route網羅や工程20完了とは扱わず`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 他校教師・進捗機能権限取消後のアクセス拒否（2026-10-15）

- `sh scripts/testing/teacher-submission-review-local.sh integration`を専用MySQL/Tomcatで実行。migration、fixture生成、WAR buildが成功し、認証HTTP runtime testは1 passed、0 skipped/failed/errors、Gradle exit 0。Playwrightではなく、実アプリのHTTP認証経路による統合受入。
- 同じ合成課題・生徒に対して、担当教師は一覧/詳細/コードdownloadをHTTP 200で読めることを先に確認。他校にのみ有効な`teacher_school_permissions`を持つ別教師にも`task-progress`機能権限を与え、教師自身の進捗画面はHTTP 200かつ対象生徒行なし、対象生徒の`view=detail`と`view=download`の直接GETはともにHTTP 404であることを確認した。download応答にも保存コードが含まれない。機能権限の不足による403ではなく、学校スコープ境界による拒否を検証した。
- 続けて担当教師の`task-progress`権限をDB上で無効化し、同一認証sessionで進捗画面・詳細・downloadを再要求。すべてHTTP 403となり、detail応答に生徒識別子、download応答に許可時に取得したコードが含まれないことを確認した。別権限の`submission-review`画面はHTTP 200を維持し、権限取消が独立機能へ波及しないことも確認した。
- さらに`task-progress`を再有効化して同じ教師の対象校アクセスを`disabled`に変更。同一認証sessionでは進捗画面はHTTP 200だが対象生徒行がなく、detail/downloadの直接GETはHTTP 404となり、許可時のコードも応答に含まれない。`submission-review`画面はHTTP 200を維持し、担当校アクセスの取消も独立機能へ波及しないことを確認した。
- `evaluation-review`権限を認証session中に無効化し、評価確認画面、list、detail、単体file/logs、CSV、ZIP/logs-ZIPの全routeがHTTP 403となること、拒否応答に対象生徒ID/提出コードが含まれないことを確認した。別権限の提出確認画面はHTTP 200を維持した。
- 最後に`submission-review`権限を無効化し、提出確認画面、list/detail/file/CSV/ZIPおよびpreview POSTがHTTP 403となり、拒否応答に生徒識別子/提出コードが含まれず、previewによる`code_logs`/`code_executions`の増加がないことを確認した。無効化後も既に許可された別機能への波及がないことは、上記の状態での画面HTTP 200で確認している。
- 同じ他校担当教師について提出確認系の学校所有範囲も確認した。提出listは空、detail/fileは404、CSVはHTTP 200でも生徒ID/提出コードを含まず成功・0行のCSV監査記録を残し、ZIPは取得対象なしのHTTP 400で対象情報を含まない。
- 初回はdownloadの期待コードを固定文字列にして失敗した。課題進捗の契約どおり保存済み下書きが最新提出コードに優先するため、期待値を許可された教師が取得した`latestCode`と照合するようテストを修正。再実行で成功。担当教師許可・他校所有範囲拒否・進捗機能/担当校アクセス取消・評価/提出確認機能権限取消後の拒否を含む`TeacherReviewRuntimeTest`は1 passed、0 skipped/failed/errors、exit 0。失敗・対応・再検証は[エラーレポート](../error-report.md)に記録した。テスト専用MySQL/container/networkはrunner終了後に削除。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 教師アンケート結果の機能権限取消（2026-10-15）

- `sh scripts/testing/teacher-survey-review-local.sh integration`を実行。専用MySQLへmigrationしWARをbuild後、fixture由来の認証HTTP runtime testを実施。終了コード0、`TeacherSurveyRuntimeTest` 1 passed、0 skipped/failed/errors。migration中のMySQL 1305 missing-routine warningは非致命で、migrationは成功。
- 認証済み教師の`survey-results`権限をDBで無効化した後、同じsessionからアンケート結果画面、JSON一覧、回答詳細、CSV/TXT exportを要求し、すべてHTTP 403となることを確認した。拒否応答に合成回答コード・生徒ID・response codeは含まれない。独立した`submission-review`権限を使う提出確認画面はHTTP 200を維持した。
- 専用MySQL/Tomcat/test container/networkはrunner終了後に削除。実利用データ・外部AI・共有app/DBは使用せず、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **同追試で学校所有範囲を追加確認:** `survey-results`機能権限を持つが別校のみを担当する教師は、認証後のJSON一覧が空、同じ合成回答の詳細直接GETはHTTP 404となり、CSV/TXT exportにもresponse/record code、生徒login ID、固有回答本文が含まれないことを確認した。`TeacherSurveyRuntimeTest` 1 passed、0 skipped/failure/error、コマンドexit 0。

### P20-03 評価結果の学校所有範囲（2026-10-15）

- `sh scripts/testing/teacher-submission-review-local.sh integration`の認証HTTP runtimeで、`evaluation-review`権限を持つが他校のみ担当する教師の評価一覧が空、対象提出への評価detail/file/logs直接GETがHTTP 404となることを追加確認した。
- 同じ教師のCSVはHTTP 200でも合成対象生徒ID/提出コードを含まず、CSV export監査は成功・0行として記録される。通常ZIPとlogs ZIPは取得対象なしのHTTP 400となり、拒否応答に対象データを含まない。機能権限不足による403と異なる学校所有範囲の秘匿を確認する。担当教師の同じ対象評価・CSV/ZIP成功は既存flowで先に確認する。
- 最終実行は専用MySQL/Tomcat runnerで`TeacherReviewRuntimeTest` 1 passed、0 failed/errors/skipped、exit 0。初期の変数重複、空ZIPの期待status、ゼロ行CSV監査件数に関するtest-side assertionsを修正した履歴は[エラーレポート](../error-report.md)に記録。共有DB/アプリ、実利用データ、外部AIは使用せず、隔離資源はcleanup済み。

### P20-03 提出確認の学校所有範囲ブラウザー受入（2026-10-15）

- `e2e/phase20/teacher-submission-ownership.spec.js`を追加し、専用tmpfs MySQL/Tomcat上でPlaywright 1.64.0/Chromiumを実行。owner教師で合成提出が一覧に存在することをpositive-controlとして確認した後、別校のみ担当する教師で一覧0件・CSV/ZIPボタンdisabled・list API空を確認した。提出detail/fileの直接URLとbrowser fetchはいずれもHTTP 404、CSVはHTTP 200で対象生徒ID/コードなし、ZIPはHTTP 400で対象データなし。
- `sh scripts/testing/teacher-submission-review-local.sh browser`でWARと認証HTTP fixtureを起動し、続けてPlaywrightを実行。Playwright test 1 passed、0 failed、exit 0。owner/outsiderは各独立context、ログイン失敗後の正規ログインを確認。runtime integration test後にownerの機能権限と学校アクセスが残留しないようfixtureを復帰し、一覧3件をassertする処理も追加した。
- Playwright command: `docker run --rm --network ppe-submission-review-test -e PPE_P20_BASE_URL=http://teacher.localhost:8080 -e 'PPE_P20_HOST_RESOLVER_RULES=MAP teacher.localhost ppe-teacher-review-runtime, MAP student.localhost ppe-teacher-review-runtime' -e PPE_P20_REVIEW_TEACHER_PASSWORD='ReviewDummy42!' --mount type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly --entrypoint sh mcr.microsoft.com/playwright:v1.64.0-noble -c 'cd /source/e2e && node node_modules/@playwright/test/cli.js test phase20/teacher-submission-ownership.spec.js --workers=1 --output=/tmp/ppe-submission-ownership-results'`
- 初回のowner権限残留とXHR wait開始タイミングのtest側失敗、および修正/再実行は[エラーレポート](../error-report.md)に記録。runtime JUnit 1/1 passed、Playwright 1/1 passed。browser runner停止後に専用container/networkの残存なしを確認。detail/file/exportはbrowser context内のdirect navigation/fetchで検証し、OS/他ブラウザーの検証は未実施。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 評価確認の学校所有範囲ブラウザー受入（2026-10-15）

- `e2e/phase20/teacher-submission-ownership.spec.js`に評価確認のbrowser caseを追加。専用tmpfs MySQL/Tomcat上でowner教師の評価画面に合成結果が表示され、detail modalが開くpositive-controlを確認した。別校のみ担当する教師では画面件数/summaryが0、評価一覧が空、CSV/ZIP/logs-ZIPボタンがdisabledとなる。
- 他校教師から対象評価detail/file/logsへの直接navigationとbrowser fetchはHTTP 404、CSV fetchはHTTP 200でも生徒ID/コードなし、ZIP/logs-ZIPはHTTP 400で対象データを含まないことを確認した。
- `sh scripts/testing/teacher-submission-review-local.sh browser`のruntime `TeacherReviewRuntimeTest`は1 passed、0 failed/errors/skipped、exit 0（HTTP report timestamp `2026-10-15T14:29:24`）。Playwright 1.64.0/Chromiumで`phase20/teacher-submission-ownership.spec.js`を実行し、提出境界と評価境界の2 tests passed、0 failed、exit 0。browser container/networkは清掃済み。
- 詳細なコマンドは[Runtime Validation Report](./runtime-validation-report.md)に記録。実施対象は合成fixtureのChromiumのみであり、他OS/ブラウザーと全教師権限matrixは未確認。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 授業演習コード確認の機能権限取消（2026-10-15）

- `sh scripts/testing/teacher-exercise-review-local.sh integration`を実行。専用MySQLへmigrationしWARをbuild後、`TeacherExerciseRuntimeTest` 1 passed、0 skipped/failed/errors、Gradle exit 0。
- 認証済みteacher sessionで`exercise-code-review`をDB上で無効化し、演習コード画面・一覧・詳細・個別download・ZIP・bulk・CSVの7 routeがHTTP 403となることを確認した。拒否応答には合成生徒login ID・保存コードを含まず、権限取消後にexport監査記録も増えない。独立して許可した`submission-review`の提出確認画面はHTTP 200を維持した。
- さらに学校スコープの境界として、機能権限はあるが別校のみを担当する認証済み教師は一覧が空、対象生徒のdetail・個別download・ZIP直接GETがHTTP 404となることを確認した（同じtargetは許可教師でdetail/download/ZIPが成功するfixture）。これは403の機能権限拒否とは別に対象データの存在と保存コードを隠す境界を検証する。
- 専用MySQL/Tomcat/container/networkはrunner終了後に削除し、残存resourceなしを確認。実利用データ・外部AI・共有app/DBは使用していない。migration中のMySQL 1305 missing-routine warningは非致命。これは当該featureの認証HTTP境界のみであり、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 アンケート結果確認の学校所有範囲ブラウザー受入（2026-10-15）

- `sh scripts/testing/teacher-survey-review-local.sh browser`で専用MySQL/Tomcatを起動し、`TeacherSurveyRuntimeTest` 1 passed、0 failed/errors/skipped、exit 0。ownerの`survey-results`権限がHTTP runtime test内の権限取消後もbrowser acceptanceに使えるよう、fixture権限の復元と件数assertを追加した。
- Playwright 1.64.0/Chromiumでowner教師が合成回答2件を表示し、回答詳細の合成自由記述を開けるpositive-controlを確認。別校のみ担当する教師では一覧・summaryが0件で、空状態表示となり、list APIは空配列。対象回答の直接navigation/fetchはHTTP 404。
- 他校教師のCSV/TXTはHTTP 200の空出力である一方、`REC-standard`/`SR-standard`、生徒login ID、固有の合成回答・理由は含まれないことを確認した。Chromiumで誤認証拒否後の正規ログインも確認。
- 正確なbrowser command、件数、環境、aggregateは[Runtime Validation Report](./runtime-validation-report.md)に記録。Docker daemonはavailable、host Node.jsはunavailableのため公式Playwright 1.64.0 image内のNode.js/Chromiumを使用。専用container/networkは停止後に清掃し、共有DB/実利用データ/外部AIは不使用。これは当該所有境界のみの受入であり、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

### P20-03 授業演習コード確認の学校所有範囲ブラウザー受入（2026-10-16）

- `sh scripts/testing/teacher-exercise-review-local.sh browser`で専用MySQL/Tomcatを起動し、`TeacherExerciseRuntimeTest` 1 passed、0 failed/errors/skipped、exit 0。権限取消後にownerの`exercise-code-review`権限をfixture内で復元し、ownerの一覧4件をassertしてbrowser acceptanceで使える状態にした。
- Playwright 1.64.0/Chromiumでowner教師の4行一覧、`ex_saved`の詳細モーダル、保存コード表示を確認。別校のみ担当する教師では一覧/ファイル数が0、空状態、CSV/一括出力ボタンdisabled、list API空配列を確認した。対象detail/download/ZIPの直接取得はHTTP 404。
- 他校教師のCSVはHTTP 200で対象student/codeなし、一括出力はHTTP 400で対象なし。認証失敗後の正規ログインも確認。browser test初回のcompile失敗とコード選択期待値の修正は[エラーレポート](../error-report.md)に記録した。
- 再検証したPlaywrightは1 passed、0 failed、exit 0。Docker/Chromiumは利用可能、host Node.jsは未導入。専用fixtureは終了後に清掃し、実利用データ/共有DB/外部AIは不使用。当該境界は受入済みだが工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。

## 参照資料

- [実装ロードマップ](./implementation-roadmap.md) 工程20と教師/管理者機能の工程状態
- [CI運用](./continuous-integration.md) 標準build、opt-in環境、skip範囲
- [実装契約](./implementation-contract.md) ロール/データ所有範囲/状態遷移の制約
- [生徒エディター計画](./student-editor.md) 30秒保存・競合・重複提出・再提出
- [教師課題改訂計画](./teacher-task-revision.md) 評価例保存のDAOリスク
- [評価・アンケート計画](./student-evaluation-survey.md) 保存/匿名化/未確認事項
- [課題状態ルール](../../state-rules/teacher/task-state-rules.md)、[配信状態ルール](../../state-rules/teacher/distribution-state-rules.md)、[実装用状態表](../../state-rules/implementation-state-table.md)

## 2026-10-15 残るopt-in試験の実行追補

- `ApplicationUrlRuntimeTest`を隔離MySQL/Tomcatで実行し、5 tests passed、0 failed/skipped、exit 0。初回の5 failuresはテストのstatic `HttpClient`が`Host` headerを許可するJDK propertyの設定前に生成されたためであり、test class static initializerへ設定を移して解消した。
- `TeacherTaskDatabaseTest`をfresh `ppe_teacher_task_test_*` schemaで実行し、45 tests completed、35 passed、10 skipped、0 failures/errors、exit 0。skipはbrowser/fixture等のopt-inケースであり、passに含めない。初回2 failureは、テスト対象のEditor control生成時に必要な`PYTHON_RUNNER_URL`が隔離test runnerに設定されていなかったもの。unused test-only URL/tokenを設定後、全suiteが成功した。
- ユーザー承認に基づき、合成入力限定でGemini実API diagnostics/smokeを実行。`gemini-3.1-pro-preview`のmodel metadata取得、plain/GenerateContent/structured transport、標準ルーブリックを使う教師prompt生成、評価API smokeを確認した。選択7 testsは6 passed、1 skipped（別途gateのprimary recheck）、0 failure、Gradle exit 0。diagnostic helperのgeneration上限4件と別smoke 1件を使用した。別ゲートのmodel comparison/candidate testsは有効化していない。
- 別の専用MySQL schemaで`EvaluationDatabaseTest.savesLiveGeminiResultOnlyWhenExplicitlyEnabled`を実行し、1 passed、0 skipped/failures/errors、exit 0。workerによる合成評価の完了、validated resultとsnapshot等のDB保存を確認。worker内部のretry attempt数は保存していないため、合計API generation回数はこのDB testについて確定値として報告しない。
- 追加の`GeminiOperationalCandidateTest`は個別gateを有効にして実行し、4件中3 passed/1 failed、exit 1。`gemini-3.5-flash-lite`と`gemini-3.1-pro-preview`のstructured output/read-backは成功、`gemini-2.5-pro`は利用可能モデル一覧GETに含まれていた一方、`/v1beta/interactions`へのstructured generationでHTTP 404。既存の[AI連携設計](../../ai-api-integration-design.md)には同じAPI keyで新規利用者に利用不可との過去記録があり、今回の結果と整合するが、保存済み応答にprovider error bodyがなく、同一原因かは確定できない。API keyやresponse bodyは記録していない。既定モデル/アプリ設定は変更せず、追加の有料再試行も行わない。
- 実行した一時MySQL/container/networkは終了後に除去。API keyは`.env`から一時env fileへ抽出してtest containerだけに渡し、表示・レポート記録なし。fresh schemaのnonfatal missing-routine drop warning（MySQL 1305）とGradle deprecation warningはあった。URL routing、TeacherTask、診断/smoke、DB-backed evaluationの各final commandはexit 0。別のcandidate suiteは`gemini-2.5-pro`のHTTP 404によりexit 1。
- 診断test containerはGradle終了時点で停止したため、後処理が`docker exec`を使ってJUnit/diagnostic outputを回収しようとして2件のdaemon errorを出した。test自体はGradle exit 0だが、詳細artifactは保存できなかった。追加のbillable requestを発生させる再実行は行わず、この制約を含むrunner問題は[エラーレポート](../error-report.md)に記録した。
- この追補は今回実行したケースの記録であり、候補モデルsuiteには未解決のHTTP 404がある。残る全opt-in fixture/browser経路、全主要導線/ロール/所有範囲、全ブラウザー/OSも未確認のため、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
