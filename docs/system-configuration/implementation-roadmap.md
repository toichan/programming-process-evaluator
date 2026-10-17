# 実装ロードマップ: ローカル開発から機能実装まで

この文書は、[段階2の実装とローカル開発の詳細手順](./implementation-and-local-development.md)を、実際に作業する順番へ分解したロードマップです。「何を先に準備し、どのファイルを作成・変更し、何を確認したら次へ進むか」を機能スライスごとに示します。

開発の中心は `src/main` の本実装です。機能仕様などの関連文書を更新・整合した後、プロトタイプ更新を挟まず本実装へ進みます。[画面遷移プロトタイプ](../../screen-flow-diagram/webapp/)は既存UIの参考として残し、今後の機能追加では更新しません。本実装との画面・導線・機能の差異は許容し、プロトタイプとの一致を完了条件にしません。仮データや擬似動作は本実装に持ち込まず、業務データは MySQL を正本とし、Servlet → Control → DAO → DB の流れで読み書きします。

設計間で状態・DB の対応が未合意の場合は、[実装契約](./implementation-contract.md)で差分を確認し、合意してから該当工程へ進みます。機能単位の計画には[機能別実装計画テンプレート](./feature-plan-template.md)を使います。

## 作業の進め方

### 第165〜169版: 生徒通知の段階導入（2026-10-13、N169実装・browser受入完了）

- 初回6種類と通知クリックによる関連場所への遷移をユーザー承認済み。[機能仕様書の通知要件](../function-specification.md#共通ヘッダーの通知表示)を正とする。既存完了工程を再実装せず、次の順に通知基盤とイベントを接続する。
- 表示方式もユーザー承認済み: 生徒ヘッダーのベルから最近の通知を表示し、「すべて見る」で専用通知一覧へ遷移する。各通知から関連場所を開く。
- 既読方式もユーザー承認済み: ベル/一覧の閲覧だけでは既読にせず、個別クリックで当該通知を既読にする。一覧に本人の通知を「すべて既読にする」操作を設ける。状態更新は認証付きPOST/CSRFで行う。
- 保持期間もユーザー承認済み: 通知作成から90日。期限後は既読/未読とも表示対象外・削除対象とし、関連する業務データは削除しない。
- 期限接近方式もユーザー承認済み: 提出期限24時間前に1回。公開から期限まで24時間未満の課題では発生させず、延長後の追加リマインドはしない。最新状態を確認し、期限超過後の遅延リマインドは送らない。
- [x] N165-00 通知基盤: 一覧UI、未読/既読、保持期間、更新タイミング、対象者・遷移・DB/API契約を合意する。本人別DB正本、重複防止、認可、関連場所への遷移、未保存離脱確認を実装・検証する。
- [x] N165-01 評価完了: 初回/再提出/失敗再試行の成功後に本人へ通知し、対象提出版の評価結果へ遷移する。教師プロンプト変更の再評価は除外する。
- [x] N165-02 評価失敗: 本人へ失敗と明示再試行の導線を示す。API費用を伴う自動再試行を追加しない。
- [x] N165-03 課題公開: 即時/予約/追加クラスの実公開後に対象生徒へ通知し、対象課題へ遷移する。下書き・予約設定時には公開通知しない。
- [x] N165-04 期限接近: 時刻・頻度合意後、未提出の有効な期限付き課題だけを対象にする。提出・期限延長・無効化を再確認して不要な通知を抑止する。
- [x] N165-05 期限延長: 変更されたクラスの対象者へ新期限を通知する。期限切れからの受付再開を区別する。
- [x] N165-06 演習配布: 本人の領域への反映成功後に通知し、本人の配布先を開く。部分失敗・予約・同一配信再試行で未配布者への成功通知や重複通知を作らない。
- 後続: 期限超過 → 課題改訂再公開 → アンケート回答可能 → 回答途中の案内。N168の実装・専用DB検証・V32 local適用は完了。教師による再評価完了通知は既存非通知合意を維持し、別途判断する。
- 作業単位: `src/main` のEntity/DAO/Control/Servlet、共通生徒ヘッダーと通知UI、各イベントの確定処理、対象テスト。正確な対象ファイル・列・APIはN165-00で決め、未合意のDB設計は実装しない。
- 検証条件: イベント確定後の保存/再読込、本人別未読/既読、他人生徒・他所属・無効対象の境界、再試行の重複防止、対象提出版/課題/配布先への認証HTTP遷移、未保存取消保持、通常幅/狭幅の本実装ブラウザー受入。対象テスト・WARを優先し、共有基盤の変更時に回帰検証する。
- ローカル8080限定。本番ppeval.net・プロトタイプ・デプロイ/バックアップ設定・資格情報・外部AIは操作しない。DB変更は設計合意後の追加migrationを対象確認・検証して適用し、既存migrationを変更しない。
- N166-01進捗（期限超過通知）: 第166版機能仕様、V31、Entity/DAO/Control、DB/Controlテスト、DB定義・実装契約を反映。専用MySQLでV1〜V31 migrate/validate、期限超過DB統合テスト、全体回帰512件（374成功、138 skip、失敗/エラー0）、WARが成功。local8080にV31適用・validation後、appのみ再起動。認証済み生徒browserで通知一覧に期限超過通知が表示され、allow案内を確認してクリックし、対象エディターへ遷移・既読化されたことをDBで確認。期限後提出denyも工程20のPlaywrightで通知から生徒homeへの遷移、直接editor URLのHTTP 404を確認済み。
- N167-01進捗（課題改訂再公開通知、2026-10-13受入完了）: 既存`task_published`を再利用し、新revisionの即時再公開/予約公開worker確定で改訂版と分かるタイトル・本文を設定する。既存の本人×新assignment重複排除と新revision editor遷移を維持し、migrationは追加しない。専用MySQLの即時・予約統合テストと全体回帰513件（375成功、138 skip、失敗/エラー0）、WAR成功を確認。改訂課題での揺らぎ・評価例生成が404となる不具合を修正し、関連52件（47成功、5 skip、失敗/エラー0）、WAR/`flywayValidate`も成功。local8080の認証browserで合成taskId=18/version=4のGemini生成・教師対応・評価例保存・prompt適用・改訂再公開を確認した。DB上で旧task 17=`requires_update`/旧assignment=`archived`、新task 18/新assignment=`published`を確認。有効な在籍生徒5人に改訂通知が各1件作られ、生徒browserで改訂版のタイトル/本文、通知クリック後の新assignment editor遷移、DB既読化を確認した。実生徒データ・提出・編集ログは送信していない。評価例の旧inactive行一括active化リスクは当時未修正だったが、工程20で修正・回帰確認済み。
- N165-06 local8080ブラウザー追補（2026-10-12）: 教師ログイン後、合成Pythonファイルを「デモ学校 / 1年 A組」へ即時配信し、履歴の「配信完了」を確認。DBでは対象targetが`distributed`、対象生徒5人に`exercise_distributed`通知が各1件（合計5件）作成された。認証済み生徒browserで通知一覧上の通知表示、クリック後`/student/exercise?exerciseId=17`への遷移、演習領域`N165-06-notification-test`の表示、戻った通知一覧での既読化と未読数2→1を確認し、教師配信から生徒通知UXまで受入完了。
- N167-01 browser受入準備（2026-10-12〜13、当時の状況）: N167通知の個別browser受入用に合成課題taskId=17/version=3を準備した。既存公開課題からの独立系列コピーはHTTP 400「複製元に現在適用中のプロンプトがありません。」となり作成されなかった。ユーザーが追加課金後にGemini利用を許可したため、taskId=17の揺らぎ・評価例生成、prompt適用、初回公開まで実施した。初回公開通知5件は改訂通知ではない。続いてtaskId=17を親とする改訂taskId=18/revision 2/version 4を作成し、揺らぎ生成POSTがHTTP 404となったため生成経路を修正した。404原因・回帰テスト・当時の検証結果は[エラーレポート](./error-report.md)を参照。以後のGemini生成、改訂再公開、生徒通知browser受入は上記N167-01進捗と[公開後課題改訂 実装計画](./feature-plans/teacher-task-revision.md)に記録済み。
- N168-01 アンケート回答可能通知（2026-10-12）: 評価完了と同一トランザクションで、最新の研究協力同意・有効な課題別アンケートと設問・対象課題/評価へのアクセスを確認して通知し、教師主導再評価および過去評価への遡及通知は除外する実装を追加。
  - 対象テストコマンド: `docker compose exec -T -e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 -e DB_NAME=ppe_teacher_review_test_n16801 -e DB_USER=root -e DB_PASSWORD=[一時テスト値のため省略] -e TEACHER_REVIEW_DB_TEST=true app gradle test --tests entity.StudentNotificationTest --tests control.student.StudentNotificationControlTest --tests control.student.StudentNotificationDatabaseTest --tests servlet.student.StudentNotificationServletTest --warning-mode all`。exit 0、54成功・0失敗・0エラー・0skip。
  - 隔離MySQLでV1〜V32をmigrateし、`flywayInfo`で全migration success/V32、`flywayValidate`成功。`docker compose exec -T app gradle war --warning-mode all`もexit 0。local開発DBへV32を適用し`flywayValidate`成功。試験用DBコンテナは検証後に削除した。
  - 2026-10-13追補: 認証済みbrowserで新規`survey_available`通知を確認。s008の合成評価3/割当13に結び付く既存survey4は既に回答済みだったため、回答を変更せず、受入専用の合成survey/question/evaluation_requestと通知を一時作成し、DAOの通知作成条件に沿って対象を用意した。通知一覧で未読表示、クリック後の`/student/survey?assignmentId=13&evaluationId=3&surveyId=6`遷移、設問と未回答状態の表示、未読数3→2、戻った一覧で既読表示を確認した。回答は保存・提出していない。確認後、一時survey/question/request/notificationを削除し、既存survey4の回答1件が維持されたことを確認。Gemini APIは未使用。これは通知・画面遷移のbrowser受入であり、実際の評価完了トランザクション自体はN168のDBテスト54件で別途検証済み。
- N169-01 アンケート回答途中の案内（2026-10-13）: 下書き保存から24時間後も未提出の回答に対し、最新同意・課題/所属・評価・アンケート/設問の有効性を確認して一度だけ案内するworkerを追加。既存survey通知の本人認可付き遷移先を再利用し、回答を変更しない。専用MySQLにV1〜V33を適用し`flywayValidate`成功。対象58件（Entity 6、Control 21、DB統合19、Worker 1、Servlet 11）は失敗/エラー/skip 0で成功し、WAR build成功。local開発DBもV33適用・validation成功。認証済みbrowserでは合成の24時間超過・未提出回答から通知が生成され、通知一覧への表示、クリック後の正規アンケートURLへの遷移、保存済み回答の再表示、回答状態が`in_progress`のまま、通知の既読化を確認した。受入用の合成survey/回答/通知は確認後に削除し、専用テストDBコンテナも削除した。
- N167-01 Gemini生成の再開準備（2026-10-13）: ユーザー許可に基づき、合成課題taskId=17/version=3の揺らぎ項目生成を1回実行したが、Interactions APIがHTTP 429を返し生成状態は`failed`。課題は未公開、通知なし。実生徒データは送信していない。quota/Retry-After状況の確認まで追加要求を保留する。詳細は[エラーレポート](./error-report.md#n167-02-gemini揺らぎ生成のhttp-429-2026-10-13-0110-jst)を参照。
- 現在の次の着手（2026-10-15 01:33 JST）: 工程20で即時/予約公開、改訂通知、提出/再提出、教師確認画面と出力、管理者教師作成、同一校peerの編集と他校/機能権限拒否を認証browser/専用MySQLで確認済み。今回、課題編集へ認証済みの合成教師を管理者が停止した後、停止状態が再読込され、同じ教師sessionでの保護URLアクセスがloginへ戻ることを専用DB/Playwrightで確認（Gradle 1/1、browser 1/1成功）。Playwrightの既存16 test declarationsは各専用fixtureで個別受入済み。最新clean buildの件数・環境依存skipは工程20計画を参照し、skipは合格扱いしない。次はP20-03の未確認feature-route/学校・所有範囲/停止・削除境界と残るopt-in試験を計画表に沿って継続し、未確認を合格扱いせずP20-10最終回帰・清掃を更新する。他ブラウザー/OS、OS標準ダイアログ、Gemini実API、本番受入は未確認で、工程20は`PARTIAL / NEEDS_SIGNOFF`を維持する。詳細は[工程20横断テスト計画](./feature-plans/phase-20-cross-feature-validation.md)と[Runtime Validation Report](./feature-plans/runtime-validation-report.md)を参照。期限延長・対象クラス追加は完了済みの工程10スライスであり、工程20では必要な回帰範囲だけを確認する。

#### 第166版 第1バッチ: 課題期限超過通知（N166-01、実装・DB/ローカル/browser受入済み）

- 範囲: `published`から`expired`への実遷移時に、有効な在籍アカウントの未提出生徒へ期限後提出可否を案内する通知を作成する。実遷移・監査・通知は同一transaction。期限延長後に再公開され、その後再度期限切れになった場合は別イベント。既存期限切れのbackfillなし。
- DB/遷移: V31で`assignment_expired`と必須課題割当参照を追加。allowなら期限後提出可能な課題エディターへ、denyなら本人・割当状態をサーバー検証して生徒ホームへ遷移する。
- 検証: 専用MySQLでV1〜V31 migrate/validate成功。`expirationTransitionNotifiesActiveUnsubmittedStudentsWithLatePolicyGuidance`成功。全体回帰512件（374成功、138 skip、失敗/エラー0）とWAR成功。local8080にV31適用/validation後appを再起動し、host別redirect後のlogin routeはHTTP 200。認証browserで通知一覧の期限超過・allow文面・クリック後の対象editor表示を確認し、該当通知のread_at更新もDBで確認。
- 当時の制約/未確認（更新）: 実装時点では期限後提出denyの遷移はControl/Servletテストのみだったが、工程20で期限切れ`DENY`通知から生徒homeへの遷移とeditor直接URLのHTTP 404をPlaywrightで確認済み。N165-06配布通知は後続の工程13認証browser受入で確認済み。本番・プロトタイプ・外部AI変更なし。

#### 第167版 第1バッチ: 課題改訂再公開通知（N167-01、実装・テスト済み）

- 範囲: 同一課題系列の新revisionが実際に公開されたとき、対象クラスの有効生徒へ改訂版と分かる通知を作成する。通知から新revisionの課題エディターへ移動する。
- 通知契約: 既存`task_published`を用い、`supersedes_task_id`の有無で初回公開と改訂再公開の表示文面を分ける。即時公開では再公開のtransaction内、予約公開ではworkerが`scheduled`→`published`へ遷移したtransaction内で作成。予約設定・改訂案作成時は通知しない。既存の本人×assignment UNIQUEを利用し、migration/backfillなし。
- 検証: 専用MySQLで既存V1〜V31 migrate/validate、即時改訂通知および予約公開前は未通知/worker公開後は1件のみとなるDB統合テスト、全体回帰513件（375成功、138 skip、失敗/エラー0）、WARが成功。local8080の開発アプリは変更コード反映後にTomcat startedを確認。
- 当時の未確認事項（後続受入で解消）: 個別の改訂再公開browser受入は未実施で、taskId=17は未公開だった。その後ユーザーのGemini利用許可を得てtaskId=18改訂の生成・公開と通知browser受入を完了している。既存課題の適用済みpromptがない場合のコピーは当時HTTP 400で不成立。通知のリンク解決分岐は追加せず、既存`task_published`の認可済みエディター遷移を再利用する。本番・プロトタイプは変更していない。

#### 第165版 第6バッチ: 演習配布通知（N165-06、2026-10-12、専用DB/全体回帰・8080反映済み）

- 範囲: 生徒本人の配布演習領域・項目作成と同一transactionで配布成功通知を保存する。即時配信・予約workerのどちらも対象。失敗/予約設定中には作らず、同一配信再試行や既配布対象のskipでは重複させない。導入前配布のbackfillは行わない。
- DB/参照: V30で`exercise_distributed`、必須`distribution_target_id`/`student_exercise_id`参照、CASCADEなしFK、本人×配信target dedupe用生成列/UNIQUEを追加。評価/課題通知の参照形は変更しない。
- 遷移: 既存通知一覧/ベル/CSRF POSTを再利用する。通知open時に有効本人、本人が所有する未削除・非archiveの配布演習領域、配布targetとの対応を再検証して`/student/exercise?exerciseId=...`へ303する。遷移用の新規API/UIは追加しない。
- 対象テスト: 即時配布と再配信それぞれ1件、同一operation再試行で件数不増、予約worker完了後に通知1件、DB実体から通知を開いて演習へ移動し既読化することを専用`TeacherDistributionDatabaseTest`で確認。Control境界・不正/利用不可参照・Servlet 303 redirectの単体テストも追加。
- 検証: `docker exec programming-process-evaluator-app-live gradle --offline --no-daemon --project-cache-dir=/tmp/n16506-cache -I /tmp/oct11-v158-demo.init.gradle test --tests control.student.StudentNotificationControlTest --tests entity.StudentNotificationTest --tests servlet.student.StudentNotificationServletTest --tests servlet.student.StudentNotificationUiContractTest war`: 成功。専用MySQL上の`gradle --offline --no-daemon flywayMigrate flywayValidate test --tests control.teacher.TeacherDistributionDatabaseTest war --console plain`: 成功（全migration/validation、配信DB統合テスト、WAR）。全体`test war`は509件（341成功、168 skip、失敗/エラー0）。Skipは環境変数/隔離DB/runner/API条件を必要とするテスト。
- local8080反映: DB schema version 29からV30へ`flywayMigrate flywayValidate`で更新。実行前後で既存通知5件と配信target状態を確認し、過去配布通知はbackfillせず通知件数を維持。appのみ再起動後、生徒/教師ログインrouteは200。起動直後のHTTP接続待ち/404は起動中に限る一過性で、Tomcat started後に両route成功。共有DBの内容や他コンテナは削除・変更していない。
- ブラウザー: 2026-10-12に教師ログイン後、合成ファイルを1年A組へ即時配信し、画面の履歴「配信完了」とDB上のtarget `distributed` / 5件の`exercise_distributed`通知を確認。共有生徒ブラウザーから通知一覧へ移動するとログイン画面へredirectされたため、認証後の一覧表示・クリック・演習遷移は未確認。画面UIはこのバッチで変更していない。専用DB上の通知クリック遷移は検証済み。
- 外部APIは呼び出していない。本番ppeval.net・画面遷移プロトタイプ・バックアップ/デプロイ設定・資格情報は操作していない。

#### 第165版 第5バッチ: 期限延長・受付再開（N165-05、2026-10-12、専用受入・8080反映済み）

- 範囲: 教師によるクラス別提出期限の延長操作（既存期限切れからの受付再開を含む）確定時点で、対象クラスの有効な生徒へ新期限を通知する。提出済み/未提出を問わず対象クラスの在籍生徒全員に送る（期限延長後の追加の期限接近通知は行わない設計のため、延長の事実そのものを通知する）。公開・クラス追加・期限接近とは別イベントとして扱い、延長の都度、新しい通知を作成する（重複排除しない）。
- 対象: V29の`deadline_extended`型追加・必須参照（割当ID必須/評価ID・提出ID必須NULL）CHECK拡張、`StudentNotificationDao.INSERT_DEADLINE_EXTENDED`/`insertDeadlineExtended(Connection, long)`（在籍`membership_status='active'`かつアカウント`account_status='active'`・未削除の生徒のみを正本JOINで抽出）、`TeacherTaskDao.extendAssignmentDeadline`からの通知挿入呼び出し、`StudentNotificationControl.resolveRedirectPath`への`TYPE_DEADLINE_EXTENDED`分岐追加、対象DBテスト。他のN165バッチと異なり、同一学生・同一割当への重複を防ぐdedupe生成列/UNIQUE制約は意図的に設けない（複数回の延長をそれぞれ独立した通知として許容する設計）。既存一覧/ベル/POST離脱guard/生徒用エディターへの遷移UIを再利用する。
- 状態/DB: 期限延長の確定（`due_at`更新）と通知挿入を同一トランザクションで行う。評価ID/提出IDは必ずNULL、割当IDは必須。対象生徒の抽出条件は`student_class_memberships.membership_status='active' AND users.account_status='active' AND users.deleted_at IS NULL`で、ソフト削除済み（退会処理済み）アカウントは正しく除外される。
- API: 既存の個別既読POST/303・一覧・ベルを再利用し、新規APIは追加しない。
- 検証: `StudentNotificationDatabaseTest.deadlineExtendedNotifiesOnlyActiveClassmatesAndAllowsRepeatExtensions`を含む対象テストがisolated DB環境で成功、および全体`gradle test war`でリグレッションなしを確認。開発中に`membership_status`のENUM値に無効値`'withdrawn'`を使っていたテストのバグを発見し、正しいENUM値`'inactive'`へ修正して再検証し成功した。
- ローカル反映: 8080へV29適用（`flywayMigrate`成功）・WAR再ビルド・appのみ再起動、起動ログにエラーなしを確認。教師アカウント（`test-teacher`）でクラス別提出期限延長モーダルから実際に期限を2回延長し（`2026-11-10T01:44`→`2026-12-01T09:00`→`2026-12-15T10:00`）、1回目の延長でclassroom在籍の有効アカウント5名（削除済み2名は除外）へ`deadline_extended`通知が作成されること、2回目の延長でも重複排除されず別途5件の新規通知が作成されること（計10件）をDBで確認した。生徒アカウント（`s001`）でログインし、ホーム画面の未読通知カウント・提出期限表示の更新、通知一覧ページでの通知内容表示、通知クリックで対象課題の生徒用エディター（`/student/editor?assignmentId=13`）へ正しく遷移すること、遷移後に当該通知が既読化（`read_at`設定）されることを確認した。検証用に変更した提出期限・作成した通知行は確認後に元の値（`2026-11-10 01:44:59`、通知0件）へ復元済み（本番検証データとして永続化していない）。本番・プロトタイプ・外部AIは操作していない。

#### 第165版 第4バッチ: 期限接近（N165-04、2026-10-12、専用受入・8080反映済み）

- 範囲: 公開から24時間以上経過した有効な期限付き割当のうち、提出・評価待ち・完了のいずれでもない学生への期限24時間前リマインドのみを対象。公開直後（24時間未満経過）の割当、提出済み/評価待ち/完了済み学生、無効化・取消済み割当は対象外。延長後の重複リマインドはしない。
- 対象: V28の`due_soon`型追加・必須参照（割当ID必須/評価ID・提出ID必須NULL）CHECK拡張・dedupe生成列`due_soon_dedupe_key`/専用UNIQUE、`StudentNotificationDao.insertDueSoonReminders(Connection)`（LEFT JOINで対象学生を正本から抽出し`ON DUPLICATE KEY UPDATE`で重複防止）、新規`StudentDueSoonReminderWorker`（60秒ポーリング、バックグラウンドスレッドで定期実行）、`StudentNotificationControl`の遷移先解決（既存の課題公開通知と同じ生徒用エディターへの遷移を再利用）、対象DB/Control/Workerテスト。既存一覧/ベル/POST離脱guardのUIを再利用する。
- 状態/DB: `task_class_assignments`の`due_at`と`updated_at`（公開/最終更新時刻の代替指標）をTIMESTAMPDIFFで比較し、「現在時刻から期限までの残り時間が24時間以内」かつ「公開/更新から24時間以上経過」の両条件を満たす行のみ対象にする。対象学生は`task_participations.progress_status NOT IN ('submitted','awaiting_evaluation','completed')`またはレコード未作成（未着手）に限定し、LEFT JOINで学生の在籍（`membership_status='active'`）・アカウント（`account_status='active'`）の有効性も検証する。評価ID/提出IDは必ずNULL、割当IDは必須とし、`due_soon_dedupe_key`の専用UNIQUEで同一学生・同一割当への重複挿入を防止する（ON DUPLICATE KEY UPDATEで無視）。`task_class_assignments.updated_at`にDEFAULTがないため、legacyな既存割当（`updated_at IS NULL`）は自動的に対象外となる既知の挙動を確認済み。
- API: 既存の個別既読POST/303・一覧・ベルを再利用し、新規APIは追加しない。Workerはユーザー操作とは独立したバックグラウンドポーリングのみで、HTTPエンドポイントは追加しない。
- 検証: `./gradlew --offline --no-daemon test --tests 'control.student.StudentNotificationDatabaseTest' --tests 'control.student.StudentNotificationControlTest' --tests 'worker.student.StudentDueSoonReminderWorkerTest' war`の専用DB/isolated環境での対象テスト、および全体`gradle test war`でリグレッションなしを確認。開発中に`dueSoonRemindersExcludeSubmittedAndOutOfWindowAssignments`テストが失敗したが、原因はDAOロジックではなく、同一classroom内の複数学生を`student_user_id`でスコープせずCOUNTしていたテストのアサーション側の欠陥と判明し、修正後に全件成功した（DAOの除外条件・LEFT JOIN・TIMESTAMPDIFF判定は当初から正しく機能していた）。
- ローカル反映: isolated DB環境でのテスト全件成功後、8080へV28適用（`flywayMigrate`成功）・WAR再ビルド・appのみ再起動、HTTPリダイレクトチェーン200到達・起動ログにエラーなしを確認。既存のpublished割当（`updated_at IS NULL`のlegacy行）はdue_soon対象外のため、ブラウザー受入用に専用デモ学生（`demo-progress-20261008-1`、in_progress/not_started/needs_action状態の3名）の割当`due_at`/`updated_at`を一時的にdue_soonウィンドウ内へ調整し、Worker実行後に対象3名（not_started/in_progress/needs_action）へのみ通知が作成され、submitted/completed状態の学生には作成されないことを確認した。通知クリックで対象課題の生徒用エディターへ遷移すること、一覧での既読化、ヘッダーベルの未読数反映も確認した。検証用に変更したパスワード・`due_at`/`updated_at`・作成した通知行は確認後に元の値へ復元済み（本番検証データとして永続化していない）。本番・プロトタイプ・外部AIは操作していない。

#### 第165版 第3バッチ: 課題公開（N165-03、2026-10-12、専用受入済み・8080反映待ち）

- 範囲: 即時公開、追加クラスへの即時公開、予約公開workerの実公開確定時点のみ対象クラスの有効な生徒へ通知。下書き保存・予約設定・教師preview・配信差分の個別案内は対象外。
- 対象: V27の`task_published`型追加・必須参照CHECK・dedupe生成列/専用UNIQUE、`StudentNotificationDao.insertTaskPublished`、`TeacherTaskDao`（即時公開・クラス追加）と`TeacherTaskPublicationDao`（予約公開worker）の3呼出箇所、`StudentNotificationControl`の遷移先解決、対象DB/Control/認証HTTPテスト。既存一覧/ベル/POST離脱guardのUIを再利用する。
- 状態/DB: 公開確定と通知保存を同一トランザクションで行う。対象は該当割当の`membership_status='active'`かつ`account_status='active'`・未削除の生徒全員。評価ID/提出IDは必ずNULL、割当IDは必須とし、既存UNIQUE`(student_user_id, notification_type, evaluation_id)`では防げない重複を`task_published_dedupe_key`の専用UNIQUEで防止する（ON DUPLICATE KEY UPDATEで無視）。同一トランザクションで対象クラスの期限切れ通知を清掃する。
- API: 既存の個別既読POST/303を再利用し、遷移先は対象課題の生徒用エディターへ変更する（`StudentEditorDao`経由で対象割当の利用可否を検証）。新規APIは追加しない。
- 検証: `./gradlew --offline --no-daemon test --tests 'servlet.student.StudentNotification*' --tests 'control.student.StudentNotificationControl*' --tests 'entity.StudentNotification*' war` 等の対象テストに加え、フルハーネス`UI160_RUNTIME_TEST=true UI_VALIDATION_REPORTS=build/ui165-published-acceptance sh scripts/testing/evaluation-survey-ui-local.sh`で全体499件（単体、失敗・error0/skip150）、認証HTTP6件（失敗・error0/skip3）成功を確認。`StudentNotificationRuntimeTest.taskPublishedNoticeRedirectsToTheEditor()`含め全成功。
- 既知の回帰と修正: フルハーネス初回実行で`StudentNotificationControl`の既定コンストラクタが`StudentEditorControl`経由で`PythonRunnerClient`（`PYTHON_RUNNER_URL`必須）へ不要に依存しており、全生徒画面の`StudentNavigationFilter`にまで波及してテスト3件が失敗した。`StudentEditorDao`を直接使う設計へ書き換え、依存を除去して再検証し全件成功を確認した。詳細は[エラーレポート](./error-report.md)を参照。
- ローカル反映: 専用DB/認証HTTPでの受入は完了。8080へのV27適用とappのみ再起動、ブラウザーでの通知クリック→エディター遷移の確認は未実施（次回作業で実施）。本番/プロトタイプ/デプロイ設定/外部AIは操作しない。

#### 第165版 第2バッチ: 評価失敗（N165-02、2026-10-12、専用受入・8080反映済み）

- 範囲: 通常workerの最終失敗・中断復旧から本人へ失敗通知。教師preview/再評価、既存失敗backfill、追加の自動再試行、外部AI実呼出しは対象外。
- 対象: V26の種別/必須参照CHECK拡張、通知Entity/DAO/Control、`EvaluationWorkerDao.fail`/中断復旧、対象DB/Control/認証HTTPテスト。既存一覧/ベル/POST離脱guard/遷移先の評価再試行UIを再利用する。
- 状態/DB: failed確定・監査・通知を同一transaction。student要求のinitial、または明示再試行（reevaluation/当該評価のstudent成功retry_requested監査）、かつreevaluation jobなしだけを正本JOINで作成し、種別/本人/評価IDで重複防止。通知保存失敗なら状態・監査もrollback。成功済み再試行後も古い通知を履歴として保持する。完了通知も同じ明示再試行条件に対応する。
- API: 個別POSTの既存認可/90日/CSRFを再利用し、対象提出版の評価画面へ303。クリックだけで再試行せず、画面の最新状態と既存明示再試行可否判定に従う。
- 検証: 通常失敗/中断/遅延worker拒否/重複/再試行履歴/rollback/教師非通知の専用DB、保存再読込・本人境界・認証HTTP、失敗通知から評価画面へのブラウザー遷移。受入後、local8080にV26追加・appのみ反映する。本番/プロトタイプは不変。
- 結果: `UI160_RUNTIME_TEST=true UI_VALIDATION_REPORTS=build/ui165-failure-acceptance sh scripts/testing/evaluation-survey-ui-local.sh`は全体494件（346成功/148条件付きskip/失敗・error0）、認証HTTP5件成功、通知DB14件成功。最終の明示再試行判別修正後、通知対象`test --tests 'servlet.student.StudentNotification*' --tests 'entity.StudentNotification*' --tests control.student.StudentNotificationControlTest war`は26件（25成功/実HTTPskip1）/WAR成功、最終通知認証HTTP1件成功。`EVALUATION_DB_TEST=true`の別の空の専用評価DBで`flywayMigrate test --tests control.evaluation.EvaluationDatabaseTest`は13件（12成功/実Gemini1skip）。失敗通知保存エラー時の通常失敗/中断復旧のrollback、古い提出の失敗・遅延worker拒否、明示再試行ごとの失敗通知と再試行成功の完了通知を検証。
- Chromium受入: 専用合成生徒で1440/375pxの失敗通知文言・未読・折返し/横溢れなし、クリックから提出版v1の失敗評価画面・既存明示再試行ボタンへ遷移、再読込後の既読保持4検証成功。クリックでAPI/再試行要求を実行しない。8080 V25/評価queue0を確認後V26のみ追加適用し、appのみ再起動・両ログイン200・通知認証302・通知0（backfillなし）を確認。8080のユーザー認証後手動確認、全ブラウザー/OS、実Geminiは未確認。既存IDEのGson解決診断はGradle/実Tomcat結果と区別し、IDE設定は変更しない。
- 専用検証コンテナ/ネットワークを所有範囲で清掃し、8080 app・既存runner/DB・他プロジェクトDBの稼働と両ログイン200、通知JS配信hash一致を再確認。ソース変更の破棄・commitはしていない。

#### 第165版 第1バッチ: 通知基盤・評価完了（2026-10-12、専用受入・8080反映済み）

- 作業単位はN165-00/01。N165-02〜06は後続バッチとし、本バッチで未提供のイベントを通知済みと表示しない。既存評価の一括通知/backfillは行わず、導入後に確定する評価を対象にする。
- 対象ファイル: 通知Entity/DAO/Control/Servlet、`StudentNavigationFilter`、`EvaluationWorkerDao`の通常評価完了、V25追加migration、生徒共通ヘッダー/共通資源読込、通知一覧JSP/CSS/JS、対象単体/DB/HTTP/UI契約テスト。既存評価・生徒本人の保存/提出・再評価jobの契約は維持する。
- REQ-NOTIFICATION-165 → N165-00（DB正本・本人別通知・未読/既読・90日保持・重複防止）/N165-01（評価確定と通知の原子性）。REQ-NOTIFICATION-LINK-165 → N165-00（認証付き個別POSTから対象提出版への遷移・未保存離脱確認）。
- DB: `student_notifications`を追加し、本人・通知種別・評価/提出/割当の参照を持つ。任意URLやコード本文・AI応答・資格情報を通知へ保存しない。評価完了と通知作成は同じトランザクションとし、失敗時は両方をロールバックする。既存migrationは変更しない。
- API: GET `/student/notifications?page=1` は新しい順に20件/ページで状態を変更しない。共通ヘッダーは画面読込時に最新5件/未読数を取得し、本バッチでは背景pollingを設けない。POST `/student/notifications/open` は本人/保持期限/遷移先を検証して個別既読後に303で対象提出版の評価画面へ移動する。POST `/student/notifications/read-all` は本人の保持期間内通知を既読にして303で一覧へ戻る。POSTはCSRF必須。不正入力400、無権限403、通知不在/他人/期限切れ404、関連対象利用不可409、DB障害503を明示する。
- 保持: DB時刻で作成から90日の境界を判定し、期限後はGET一覧/件数/個別操作から除外する。期限切れ行の物理清掃は書込側で通知行だけを対象に行い、閲覧GETでは削除しない。90日後に元イベントを再通知しない。
- 検証: 対象テスト/WAR、専用合成MySQLでV25適用・保存再読込・原子性/重複/90日境界/ページング/他人拒否/非通知再評価、認証HTTPでGET無更新・POST/CSRF・評価版への遷移、通常幅/狭幅ブラウザーでベル/一覧/既読/未保存取消を受け入れる。受入後に8080用DBへ追加migrationを適用・appのみ再起動する。本番等には操作しない。
- 検証結果: `UI160_RUNTIME_TEST=true UI_VALIDATION_REPORTS=build/ui165-acceptance sh scripts/testing/evaluation-survey-ui-local.sh`で全体487件（342成功/145条件付きskip/失敗・error0）、認証HTTP5件成功、通知DB13件成功。最後の保持期限競合対策2件追加後、`gradle --offline --no-daemon test --tests 'servlet.student.StudentNotification*' --tests 'entity.StudentNotification*' --tests control.student.StudentNotificationControlTest war --console plain`は25件（24成功/実HTTP条件付きskip1）、最終WARの通知認証HTTP1件も成功。別の空の専用評価DBで`EVALUATION_DB_TEST=true`の`flywayMigrate test --tests control.evaluation.EvaluationDatabaseTest`を実行し、通常評価・通知原子性/重複/教師再評価非通知8件成功、実Gemini1件skip。共通guard独立8検証も成功済み。
- ブラウザー受入: 専用Tomcat/合成生徒をChromiumで操作し、1440/375pxの一覧・ベル・UTF-8文言・ページ横溢れなし・狭幅の静的dropdown、再提出編集開始後の未保存コード取消保持/未読維持、確定クリックの対象提出版遷移/個別既読、既読通知の再訪を確認。統合ブラウザー切断は代替Chromiumで補完した。
- ローカル反映: 8080のアプリbind mount/専用Compose DB/V24/処理中評価0を確認し、`docker exec programming-process-evaluator-app-live gradle --offline --no-daemon flywayMigrate --console plain`成功でV25のみ追加。appのみ再起動し、両portalログイン200、通知未認証302、配信JSとソースのSHA256一致を確認。既存評価のbackfill・仮通知追加はせず通知0件は正常。本番・プロトタイプ・資格情報・外部AIは不変。8080でのユーザー自身による認証後確認、全ブラウザー/OS互換性、全体タイムゾーン統一は未確認であり、専用受入と区別する。
- 後始末: 本バッチと委任先が作成した専用コンテナ/ネットワークを指定名・所有範囲で清掃し、8080 app・既存runner/DB・他プロジェクトDBの継続稼働と両ログイン200を再確認。ソース変更を破棄・commitしていない。

### 第164版: 生徒4画面のホーム導線・未保存確認（2026-10-11、ローカル実装・受入済み）

- 第163版のアンケート末尾非表示をユーザー依頼で変更する。[第164版計画](./feature-plans/student-evaluation-survey.md)に沿い、4画面の共通ボタンと未保存時の離脱/取消を検証する。ローカル8080のみ、DB/schema/本番/プロトタイプ/バックアップは不変。
- 最終対象12件/WAR成功、初回専用全体450件（307成功/143条件付きskip/失敗・error0）と認証HTTP4件成功。共有guard独立ブラウザー12検証成功。実画面で未保存コード/回答の取消保持・破棄home、保存後home/再読込、回答修正取消復元・再提出後home/再読込、送信前入力の離脱保護・ログアウト取消保持を確認した。
- 4画面1440/375pxのDOM幅/中央配置/横溢れなしを確認。評価/演習の通常幅、アンケート/エディターの狭幅末尾ボタンを新本実装タブで目視確認。既存タブの描画制約・既存feedback focus警告・全OS標準ダイアログ互換性未確認は[エラーレポート](./error-report.md)へ記録する。
- 8080appのみ再起動し、生徒/教師login200・変更JS5本のSHA256一致・`git diff --check`成功。通常DB/schema・本人回答/資格情報/同意・本番/プロトタイプ/バックアップは変更しない。検証の保存/再読込は専用合成データのみで実施した。
- 受入後、所有者75243の専用container/networkを清掃し、共有app/DB/runner/docotsubuを維持。清掃後も両portal login200を確認した。

### 第163版: アンケート・進捗状態・課題一覧の設定UI（2026-10-11、ローカル実装・受入済み）

- T163-SURVEY: アンケート末尾のホームへ戻るボタン削除（提出後homeは維持）。
- T163-PROGRESS: 状態バッジを他教師画面型へ統一（状態判定/集計不変）。
- T163-MODAL: 課題一覧の期限延長・クラス追加設定をモーダルへ移す。[期限・クラス設定計画](./feature-plans/teacher-task-deadline-and-class-changes.md)の保存/認可契約を維持する。
- 最終対象37件/WAR成功（exit0）。専用全体447件（305成功/142条件付きskip/失敗・error 0）、既存認証HTTP4件成功。UTF-8修正後の追加UI契約/認証HTTP4件も成功し、認証/CSRF/権限拒否/古いversion409/期限延長/クラス追加/再読込を確認した。
- 最新WARを専用18092の合成教師/生徒browserで受け入れた。モーダルの日本語・期限延長保存再読込・クラス追加・Escape/起動元フォーカス復帰を確認。1440/375pxでモーダル800/359px、狭幅は縦並び/モーダル内横溢れなし。進捗の評価待ち/完了バッジ、表内横スクロール/ページ横溢れなし、アンケート末尾のホームボタンなしを確認。
- 8080appのみ再起動して両portal login200と変更asset3本のSHA256一致を確認した。通常DBのschema/データ・migration・本番・プロトタイプ・バックアップ設定・資格情報は変更していない。統合ブラウザーはDOMクリック/native formを併用し、全物理操作/全端末互換性とは区別する。
- 受入後に所有ラベル付き専用環境を停止・清掃し、共有app/DB/runner/docotsubuを維持した。清掃後も両portal login200、`git diff --check`成功を確認。

### 第162版: 生徒アンケート文言・回答修正（2026-10-11、ローカル実装・受入済み）

- 固定タイトル/妥当性ラベル/説明・提出後home・確認モードからの明示修正を実装した。作業単位と検証条件は[第162版計画](./feature-plans/student-evaluation-survey.md)を参照。
- V24の旧回答snapshot履歴テーブルを専用DBで検証後、ローカル8080用DBに適用済み。既存回答3件を維持し、本番/プロトタイプ/バックアップ/既存資格情報は不変。旧版の画面閲覧は追加せず、教師集計は現在回答のみ。
- 最終対象15件成功、全体444件（302成功/142条件付きskip/失敗・error 0）、認証HTTP4件成功、WAR成功。専用18092の最新WARで初回提出/修正再提出後home、版2/理由再読込、取消復元、有効設定のみ修正可能を確認した。1440/375pxの確認カード表示、狭幅のページ横溢れなし/理由欄180pxを確認。8080のlogin200と配信資源一致を確認し、既存利用者の回答は操作していない。

### 第161版: 生徒管理の利用状態・アンケートデザイン（2026-10-11、ローカル実装・受入済み）

- T161-ACCOUNT/REQ-ACCOUNT-161: 状態列を他教師画面型の丸形バッジへ変更。利用中は緑、停止中は赤、削除済みは灰。操作/状態/DB契約は維持する。
- T161-SURVEY/REQ-SURVEY-161: プロトタイプと背景/文字/カード/選択肢/理由欄/ボタン/狭幅を比較して本実装へ反映する。設問と必須条件はユーザー承認済みで変更しない。サーバー保存/認可・履歴・参照専用・共有確認を維持する。
- 対象: account JS/CSS/JSP、survey JSP/CSS、asset版と関連テスト。DB/APIの変更・新規migrationは不要。ローカル8080以外とプロトタイプは変更しない。
- 検証: 関連契約/単体テストとWAR、認証HTTPで保存/再読込・境界維持、認証ブラウザーで1440/375pxの両画面と代表正常操作、8080配信一致。専用合成データだけを使い、既存利用者の回答・資格情報を操作しない。
- 結果: 全体442件中300成功/142条件付きskip/失敗0、初回認証HTTP4件と修正後HTTP3件成功/skip0。最終ローカル対象18件成功/skip0とWAR成功。アンケートは通常幅5列/狭幅1列、妥当性カード88px/自己評価65.6px/理由欄180px、途中保存・再読込・4段階移動・共有確認後提出・参照専用を確認し、専用DBに6回答/6理由を保存した。
- 状態列は合成生徒の停止/削除/フィルターで緑/赤/灰の表示と既存操作を確認。375pxでページ幅375px、表1618px/枠317pxの局所横スクロールを確認した。統合ブラウザーの非表示ページ制約によりDOMクリック/native form/スクロールと描画確認を併用し、全物理クリック・全端末互換性の保証とは区別する。
- 8080のapp-liveのみ再起動し、生徒/教師login200と変更CSS/JSのSHA256一致を確認。認証付き保存・状態操作は専用18092/合成DBで検証し、8080の既存利用者の回答/資格情報は操作していない。本番/プロトタイプ/schema/バックアップ設定は不変。
- 所有ラベルを確認して第161版の専用検証container/networkを清掃済み。共有app/DB/runnerと他プロジェクトDBは維持し、清掃後も8080login200を確認した。

### 第160版: 生徒管理・アンケート・配信状態・課題ヘッダー（2026-10-11、ローカル実装・受入済み）

- REQ-ACCOUNT-160: 既存パスワード列をマスク/確認不可付きへ整え、レベル/初回変更のチップと他教師画面型の研究同意表示を実装する。確認API・監査、レベル2のPW再設定、停止/削除を維持する。
- REQ-SURVEY-160: プロトタイプの画面/段階式回答と実データ契約を比較し、正規リンクと画面を整える。下書き保存/再読込/提出/参照専用と認可/同意を検証する。
- REQ-DISTRIBUTION-160: 白文字だけになっている配信状態バッジの背景/文字色を状態別に定義し、詳細も同じ表現にする。状態遷移や予約設定は変更しない。
- REQ-HEADER-160: JSP forward時に本人の公開課題を取得し、課題カードと3操作/状態を全生徒画面へ表示する。固定の「実装後」案内を除く。未評価等は無効操作として明示する。
- 対象ファイルは各画面のJSP/CSS/JS、StudentTaskShortcut、StudentNavigationFilter、必要なServlet/テスト。DB/APIは既存契約を使用し、schema/migration・本番・バックアップ・プロトタイプ・本人資格情報/同意は変更しない。
- 検証: Gradle単体/契約テストとWAR、専用MySQL/認証HTTP、通常1440px/狭375pxのブラウザーで4画面を受入。資格情報をスクリーンショット/ログに出さない。最終8080の配信ソース一致と専用資源清掃を確認する。
- 最終専用`UI160_RUNTIME_TEST=true UI_VALIDATION_REPORTS=build/ui160-final sh scripts/testing/evaluation-survey-ui-local.sh`は全体440件（298成功/142条件付きskip/失敗0）、WARと認証HTTP4件（skip0）成功。ローカル最終対象17件とWARも成功。
- 認証ブラウザーで課題カードの3導線、アンケートの段階必須チェック・前後移動・下書き保存/再読込・共有確認提出/参照専用、生徒管理のマスク/確認/閉じた後の消去・PW再設定画面、配信完了/クラス配信済みを確認した。ブラウザー提出結果はDBへ6回答/6理由を保存し再読込済み。
- 1440/375pxで課題メニューの領域内表示、ページ全体の横溢れなし、教師表の局所横スクロールを確認。矢印のCSS文字化けはUnicode escapeとasset版で解消した。
- 8080のappだけ再起動し、生徒/教師login200、変更asset8本のソースSHA256一致、template版160を確認。本番/プロトタイプ/schema/migration/バックアップ設定とs008本人の資格情報/同意/回答は未変更。
- hidden統合ブラウザーではDOMクリック/native formによる正常操作を確認し、全物理クリック・全端末互換性とは区別する。専用合成アカウントを使用し、s008本人の代理回答は行っていない。専用資源の清掃結果は受入記録に残す。
- 所有ラベルを確認して専用DB/Tomcat/runner/networkと対象テストcontainerを清掃済み。共有8080/DB/runner、他プロジェクトDBは維持し、清掃後も8080の生徒login200を確認した。

### 第159版: 表幅・評価表示・アンケート確認（2026-10-11、ローカル実装・受入済み）

- T159-TABLE/REQ-TABLE-159: 共通教師表の最小幅を表示領域100%とし、一覧/モーダルの通常・狭幅で検証。
- T159-EVALUATION/REQ-EVALUATION-159: 既存プロトタイプを参照し、生徒/教師の評価・編集ログUIを実データへ接続して統一。比較元の「プロンプト」は従来文脈から「プロトタイプ」と解釈。プロトタイプ自体は変更しない。
- T159-SURVEY/REQ-SURVEY-159: ローカルs008は現在同意済みだが合成課題にsurveyが未設定。既存教師デモのsurveyは結果確認用のclosedで、回答用ではない。新たな合成設問だけをs008課題へ追加し、不可理由/編集ログ導線を修正する。
- DB/schema/migration・本番/バックアップ設定、既存同意/資格情報は維持。外部AI/コード配信（見出しだけで修正内容なし）は対象外。
- 対象テスト・実DB保存/再読込・認証HTTP・通常/狭幅ブラウザー、ローカル8080反映と専用fixture清掃を完了条件とする。
- 最終専用`sh scripts/testing/evaluation-survey-ui-local.sh`の全体420件は281成功/139条件付きskip/失敗0、
  WARと認証survey HTTP1件は成功。ローカル対象契約11件、developmentデモ3件、専用survey DB4件も成功。
- 1440/375pxで短いモーダル表の領域充填・長い一覧の局所横スクロール、
  生徒/教師評価と編集ログ・根拠logId選択・理由フィルター・アンケート保存/再読込/提出を受入。
  ローカル8080再起動後login200と配信JS/CSSのソース一致を確認した。
- s008回答用survey4を6設問/30選択肢で追加し、本人回答は0件のまま残す。
  本人ログイン受入、全物理クリック/全端末互換性、正式な設問登録UIは今回の確認範囲と区別する。
  詳細は[評価受入記録](./feature-plans/teacher-submission-evaluation-review.md#第159版の受入記録)と
  [アンケート受入記録](./feature-plans/student-evaluation-survey.md#第159版アンケート受入)。

### 第158版: 詳細対話実行と閲覧用デモ（2026-10-11、ローカル実装・受入済み）

- 進捗/演習詳細へ作業コピーの対話実行・入力・停止を追加し、既存提出確認と実runner/認可/session解放を共有する。
- 演習一覧の枠内縦スクロールを廃止し、局所横スクロールは維持する。
- 教師用評価/編集ログデモは前回のローカルデータを確認し、
  s008には独立した合成課題/完了提出/評価/編集ログを追加する。既存資格情報・同意は維持。
- ユーザーの再確認どおり開発8080だけへ反映。本番/schema/バックアップ/プロトタイプ変更、
  Gemini呼出し、修正内容のないコード配信は対象外。
- 対象は[進捗計画](./feature-plans/teacher-task-progress.md)、[演習確認計画](./feature-plans/teacher-exercise-review.md)、
  [提出・評価デモ計画](./feature-plans/teacher-submission-evaluation-review.md)へ追跡する。
- 全体411件（272成功/139条件付きskip）、専用DB/runner25件・HTTP3件成功、
  最終対象8件（7成功/1条件付きskip）とWAR成功。両詳細の複数入力・停止・閉じる、
  演習ファイル切替・表示設定、1440/375pxと原本不変を受入。
  詳細は[共通受入記録](./feature-plans/teacher-exercise-review.md#第158版の共通受入記録2026-10-11-jst)。
- s008課題/提出/評価/編集ログはDB投入・再読込・冪等性まで完了。
  s008本人の初回同意と正規ログイン後の画面確認は未確認（代理同意・資格情報変更なし）。
  hiddenブラウザーの全物理クリック、長大一覧、全端末互換性も未確認として区別する。

### デプロイ後のUI改善（2026-10-11、ローカル実装・受入済み）

- 第157版に従い、生徒ログアウト確認、教師一覧の局所横スクロール、
  管理者を含むID表示、ソースコード編集ログ / Source Code Edit Logs 表記を統一。
- 授業演習詳細は生徒演習と共通のツリー表示・表示設定・選択取得/解除を実装。
  追加/移動/削除・生徒設定への書込は追加しない。
- 進捗詳細は取得完了後にコードを設定してモーダルを開く。非同期取得自体は必要で、
  表示後の空エディターとfade後の再描画待ちを取り除く。
- ローカル開発DBの既存「進捗デモ専用（仮データ）」へ評価2件・回答2件/設問回答12件を追加。
  対象はログインロック済み合成生徒4/5で、既存同意・資格情報・schemaは変更しない。
  本番DBへは投入していない。詳細手順は[提出・評価確認計画](./feature-plans/teacher-submission-evaluation-review.md)。
- 提出コピーは生徒共通terminalの実runner対話入力・停止へ接続し、前後/閉じる時に実行を解放。
  実DB・HTTPで認可/CSRFと業務データ不変、ブラウザーで複数入力/停止/前後/閉じるを確認。
- 全体405件（270成功/135条件付きskip/失敗0）とWAR、デモDAO2件成功、
  最終狭幅修正の対象8件成功/skip0。教師一覧と変更詳細の1440/375px受入を実施。
  進捗modalの狭幅横溢れも修正し、局所表スクロールとコード初期表示を確認。
- 8080へ最終反映・login200・配信CSS一致。既存資格情報、schema/migration、
  本番・バックアップ設定・プロトタイプは維持。本番への反映と本番デモ投入は未実施。
  hiddenブラウザーの厳密first paint、全物理クリック/OSダイアログは未確認。
  正確な手順/件数/受入範囲は[提出・評価確認計画](./feature-plans/teacher-submission-evaluation-review.md)と
  [演習確認計画](./feature-plans/teacher-exercise-review.md)を参照する。

- 上から順番に進めます。前提が満たされない場合は、後続機能へ進まずブロッカーを解消します。
- 各機能に着手するとき、その機能に対応する[機能仕様書](../function-specification.md)、該当する[状態ルール](../state-rules/README.md)、[DB 設計書](../database-design/README.md)・[テーブル定義](../database-design/table-definitions.md)を必ず読み直します。状態ルールは全機能で必須です。画面遷移図・プロトタイプは必要に応じて既存UIを把握する参考にしますが、参照・更新を必須としません。
- クラス図、[AI 連携設計書](../ai-api-integration-design.md)などの技術契約は該当機能の設計に使います。プロトタイプは画面設計の参考に限り、仕様・本実装との同期対象ではありません。
- 既存設計に不整合がある場合は、コードで勝手に補完しません。影響を示して確認し、合意した変更を仕様・状態・DB 定義に反映してから実装します。
- 関連文書の更新と整合確認が終わり、仕様上の未決判断がなければ、そのまま本実装へ進みます。画面遷移図・プロトタイプの更新やプロトタイプとの一致確認を実装の前提条件にしません。利用者に影響する仕様判断が必要な場合だけ確認を取り、合意を文書へ反映してから実装します。
- 1つの機能を「DB変更 → DAO → Control → Servlet → JSP / JavaScript → 動作確認」まで通してから次に進みます。
- `screen-flow-diagram` は既存の参考用資産として保ち、本実装の JSP / CSS / JavaScript は `src/main/webapp` に直接実装します。両者の差異は許容し、画面ファイルの移植を必須としません。

## 現在の実装状況（2026-10-17）

この概要は工程の現在位置を示します。完了判定の根拠・コマンド・未確認事項はリンク先の機能別計画の最終記録を参照してください。過去の日付付き部分判定を現在の未完了件数として再計上しません。コードや画面が存在するだけで機能完了とは判断しません。

| 工程 | 状況 | 根拠・次に残ること |
|---|---|---|
| 0. 状態・データ設計の実装契約 | 一部合意済み | 未決・実装前確認事項は[実装契約](./implementation-contract.md)に残す。対象機能の未合意事項は着手前に解消する |
| 1. 開発コンテナとビルド | 完了 | 起動手順は[README](../../README.md)、構成整合の記録は[システム構成整合計画](./feature-plans/system-structure-alignment.md)を参照 |
| 2. DBとマイグレーション | 完了（初期スキーマ） | [初期DBマイグレーション計画](./feature-plans/initial-database-migration.md)。将来の変更もFlywayで管理する |
| 3. DB接続基盤と利用者DAO | 完了 | [DB接続基盤・利用者DAO計画](./feature-plans/database-connection-and-user-dao.md) |
| 4. Web共通基盤 | 完了 | [Web共通基盤計画](./feature-plans/web-common-foundation.md) |
| 5. 初期利用者・認証 | 実装・P20認可境界受入済み。本番の合成認証/停止後失効も確認済み | [認証・ログイン計画](./feature-plans/authentication-and-login.md)、[本番受入](./production-deployment.md#2026-10-16-最小デプロイの進捗)。実運用アカウントの初期構成確認と、本番の合成教師/生徒による受入は区別する |
| 6. 生徒ホーム・同意・アカウント | 実装済み | [生徒ホーム・同意・アカウント計画](./feature-plans/student-home-consent-account.md)。演習・教師画面等の実装完了を意味しない |
| 7. Python実行・生徒エディター | 主要実装・P20対象ケース受入済み。本番の保存/実行/履歴読戻しも成功 | [生徒エディター計画](./feature-plans/student-editor.md)、[P20最終対応表](./feature-plans/phase-20-cross-feature-validation.md)。30秒保存・同時保存の一勝一競合・冪等提出・再提出は完了。全ブラウザー/OSや全並行編集パターンまでの保証ではない |
| 8. 評価・コードログ・アンケート | 主要実装・合成データの縦断受入済み。設定内容はローカル確定版を採用済み | [評価・コードログ・アンケート計画](./feature-plans/student-evaluation-survey.md)。実Pro生成/適用/公開→合成生徒の実Python解答/提出→実Pro評価・DB読戻し→アンケート保存/提出を確認。設問登録UIは未実装だが追加を受入の前提にしない。確定設定の投入手順・本番反映・利用者手動E2E・採点品質/運用条件は別 |
| 9. 授業演習 | T001〜T051完了 | [授業演習計画](./feature-plans/student-exercise.md)の最新記録を正とする。実DB/HTTP/runner/ブラウザー検証と通常Chromeでの単体.py保存確認まで実施済み。OS標準フォルダ選択・ZIP保存ダイアログおよび全ブラウザー/OS組合せは未確認。教師配信・進捗制御との接続は工程13/14とP20を参照 |
| 10. 教師の課題編集・公開・改訂 | 下書き編集・削除/復元、公開・予約公開・期限処理、学習開始前の同一系列改訂、学習開始後の独立系列コピー、期限延長・対象クラス追加を実装。専用MySQL統合テストと認証browser受入済み。期限切れ割当の再開、既存提出履歴保持、追加割当の認可/冪等性を確認。AI実API受入は工程19、全体の時刻契約はH-04で別管理 | [教師課題下書き計画](./feature-plans/teacher-task-draft.md)、[S3計画](./feature-plans/teacher-task-s3.md)、[公開計画](./feature-plans/teacher-task-publication.md)、[公開後改訂計画](./feature-plans/teacher-task-revision.md)、[期限延長・クラス追加計画](./feature-plans/teacher-task-deadline-and-class-changes.md)、[教師プロンプト計画](./feature-plans/teacher-prompt-ai.md)、[課題状態ルール](../state-rules/teacher/task-state-rules.md) |
| 11. 管理者の教師アカウント管理・学校管理 | 実装・受入済み | [認証計画の工程11記録](./feature-plans/authentication-and-login.md#工程11教師アカウント管理追加バッチ2026-10-06)。2タブ管理画面、教師作成/学校・9機能権限/停止・解除/論理削除/再設定/履歴/CSVをDB接続。資格情報の一時表示と教師セッション失効を検証済み |
| 12. 教師の生徒アカウント管理 | 実装・受入済み（2026-10-07） | [生徒アカウント管理計画](./feature-plans/teacher-student-accounts.md)。権限内の一括作成/新規クラス、個別自動生成する8文字初期資格情報、再設定、一覧/CSV/履歴、状態変更・選択削除と生徒セッション失効を接続 |
| 12a. 教師本人のアカウント管理 | 実装・検証済み（2026-10-07） | [認証計画の工程12a完了記録](./feature-plans/authentication-and-login.md#工程12aの完了記録2026-10-07)。本人情報、初期/再設定後の必須変更・通常変更、全旧セッション失効を8080へ接続。関連する生徒一覧1行表示・権限不足通知も反映 |
| 13. 授業演習コード配信 | 実装・ブラウザー受入済み（2026-10-07） | [コード配信計画](./feature-plans/teacher-exercise-distribution.md)。単一校制約、演習構成UI、実ファイルアップロード、一括移動、即時配信、予約配信と実ワーカー完了、履歴の表示/編集/複製/予約変更/停止/再開を確認済み |
| 14. 課題進捗確認 | 実装・テスト・認証ブラウザー受入済み（2026-10-08） | [課題進捗確認計画](./feature-plans/teacher-task-progress.md)。専用MySQL統合テストと認証ブラウザーで空状態・実データ行・詳細履歴・検索/絞込/ソート/更新/CSV/BOM/担当外詳細を確認。実ファイルのdownloadイベントはブラウザー環境から通知されず、CSV Blob内容を検証 |
| 15. 授業演習コード確認 | 実装・テスト・認証ブラウザー受入済み（2026-10-08） | [最終受入](./feature-plans/teacher-exercise-review.md#最終受入2026-10-08)。検索後クリック・空フォルダ取得・通常/中間/狭幅を確認。全体252成功/130skip/失敗0、実DB/HTTP各1成功。8080反映済み。OS保存ダイアログ・本番/全端末受入は別 |
| 16. 提出課題確認 | プロトタイプ再現・DB/認証HTTP/通常・狭幅ブラウザー受入済み（2026-10-08、第155版） | [提出画面最終受入](./feature-plans/teacher-submission-evaluation-review.md#提出画面のプロトタイプ再現最終受入2026-10-08第155版)。3集計/9列sort/全画面詳細/左右CodeMirror/単体・ZIP・監査CSVを実DBへ接続。全体259成功/132skip/失敗0、8080反映。本番TLS/OS保存完了は別 |
| 17. 評価確認 | プロトタイプ再現・DB/認証HTTP/通常・狭幅ブラウザー受入済み（2026-10-08、第156版） | [評価画面最終受入](./feature-plans/teacher-submission-evaluation-review.md#評価画面のプロトタイプ再現最終受入2026-10-08第156版)。3カード/独立サマリー・分布/条件式/10列・9sort/全画面詳細/理由・ログ/JSON・ZIP・監査CSVを実DBへ接続。全体260成功/132skip/失敗0、8080反映済み。実AI・再評価の受入は工程19と区別 |
| 18. アンケート結果確認 | 実装・DB/認証HTTP・認証ブラウザー受入済み（2026-10-08） | [工程18最終受入](#工程18最終受入2026-10-08)。プロトタイプの集計/条件式/一覧/詳細、最新同意/割当学校認可、仮名化CSV/TXTを接続。全体257成功/132skip/失敗0、8080反映済み |
| 19. AI連携の最終実装・受入 | 実装・合成実API縦断/認証画面確認済み。アプリ本体はmain41228caとして本番反映済み | [教師プロンプト計画](./feature-plans/teacher-prompt-ai.md)、[評価計画](./feature-plans/student-evaluation-survey.md)、[AI連携設計](../ai-api-integration-design.md)。教師生成・preview・確定と通常評価の実API→DBは成功済みだが、10月16日本番受入で追加AI要求はしていない。本番quota/費用・品質校正・実データ送信・負荷試験は別ゲート |
| 20. 機能横断検証 | **P20-01〜10 PASS / 完了（2026-10-16）** | [最終対応表](./feature-plans/phase-20-cross-feature-validation.md)と[最終runtime受入](./feature-plans/runtime-validation-report.md#2026-10-16-工程20の最終受入)を正本とする。147 suites / 561 tests、373 passed / 188 skipped、failure/error 0、exit 0。29 Servlet / 71 routeの認証契約と業務DB/HTTP/browser証跡を対応付け済み。skipを成功数に含めず、元の合格条件の完了とH-01〜04等の別ゲートを区別する |

ここでいう「実装済み」は対象計画に記録された範囲に限ります。教師ログイン基盤の存在は教師向け業務機能の完成を意味せず、Gemini構造化出力が一部の合成要求で成功したことも本番評価経路の完了を意味しません。

### 残作業: テストから次回本番反映まで（2026-10-17整理）

**現在位置:** アプリmain `41228ca...`の最小デプロイは10月16日18:06 JSTに完了し、
合成認証・保存・実Python実行・再読込・停止後session失効と清掃もPASS。
「これから初回デプロイする」計画には戻さない。以後は設定の再現性と残る差分を整え、
必要な変更だけをテストして次回本番反映する。UTC/JST変更は現在の作業ツリーで実装・部分検証中だが、
未レビュー・未commitのため、次回候補として確定または本番反映済みとは扱わない。

初期設定の内容は[第180版](../function-specification.md#第180版-db初期化後の初期設定の採用基準)で
**研究同意文書以外はローカル確定版を採用済み**。
その内容が本番でもすべて一致していることや、空DBから再現できることは別の確認である。
以下は残作業の整理であり、本番変更・データコピー・DB初期化・追加API要求の実行承認ではない。

#### A. 次回本番反映へ向ける作業順

| ID | 残作業 | 状態・前提 | 完了条件 / 停止条件 |
|---|---|---|---|
| R-01 | 確定済みローカル初期設定の棚卸しと本番のread-only差分確認 | 完了（2026-10-17）: ローカル確定版を棚卸しし、本番をread-onlyで照合。管理者fingerprintと標準ルーブリック内容は一致。アカウント・学校/クラス・権限・survey・prompt/評価設定には差分あり。本番への書込みなし | 差分と集計値は[本番初期設定のR-01照合記録](./production-deployment.md#r-01-初期設定の棚卸しと本番read-only照合2026-10-17)を参照。初期投入手順・空DBからの再現性はR-03で別に確認する |
| R-02 | H-04の時刻契約と既存DATETIMEの扱いを合意する | 完了: UTC保存/比較・Asia/Tokyo入出力、本番デモ履歴は初期設定保全/再投入検証後に再作成可能。一括9時間補正はしない | [第181版](../function-specification.md#第181版-日時の統一方針)を正本に実装経路を照合する。日付のみ/期間/コード入出力本文を時刻変換対象と混同しない。本番初期化/更新・資格情報変更は手順提示後の別承認 |
| R-03 | 再現可能な初期投入手順と時刻差分の実装修正 | 全量seed再現検証完了（2026-10-17）: 確定ローカル25設定テーブルを非公開0600ファイルへ保全。同じファイルから独立した空DB2つへmigration後に再現し、全列内容・ID・参照関係一致、同内容no-op、不一致/並行/履歴混入拒否、途中失敗rollback、再接続後read-backを確認。単体4/4、MySQL統合1/1、WAR成功 | [全量seed手順・結果](./feature-plans/runtime-validation-report.md#r-03-ローカル確定設定の全量seed再現検証2026-10-17)。本番用seedの実行許可ではなく、投入先は`ppe_initial_seed_`隔離DB限定。旧DATETIMEは無変換で保全しており、その意味の確定や本番移行を保証しない。本番の対象/投入範囲/時刻/資格情報の扱いはR-06後のR-07承認時に提示する |
| R-04 | 変更対象の単体・MySQL統合・実HTTP試験 | 16:57 JST再実行完了: `sh scripts/testing/teacher-learning-flow-local.sh` exit 0。隔離JUnit 7/7 passed、0 failed/errors/skipped。Playwright 3/3 passed、0 failed（375px画面幅を含む）。stale prompt versionはHTTP 409かつ状態/監査記録不変。以前の全Gradle build 571 testsは381 passed/0 failed/0 errors/190 skippedで、全suiteのskipは別途残る | 対象別証跡と初回失敗/修正履歴は[runtime validation report](./feature-plans/runtime-validation-report.md)と[エラーレポート](./error-report.md)。全体Gradle suiteやskipされた任意DBケースの結果を今回の7件へ合算しない |
| R-05 | 確定設定による代表ブラウザー縦断と利用者受入 | 16:57 JST再実行の合成代表フローについて、利用者が**限定範囲の技術的受入を明示承認**。JUnit 7/7、Playwright 3/3。R-03全量seed照合とは別の合成fixture試験であり、確定設定全量の人間による手動E2E、採点品質、実Gemini、正式同意、本番反映は含まない | [R-05受入記録](./feature-plans/runtime-validation-report.md#r-05-利用者受入の記録2026-10-17)。限定範囲は受入済み。未確認事項と本番更新は別ゲートとして維持する |
| R-06 | 次回候補のレビュー・CI・ECR最終image受入 | ローカルCI相当は17:41 JST再検証済み。範囲限定reviewで重大指摘なしだが未レビュー部分あり。`release/r03-r05-r06-20261017` のcommit `483de49...` をpushし、GitHub CI run `39039122038` は確認時in progress。PR作成はbrowser未認証で未完了。ECR候補image未公開 | [R-06/R-07候補・復帰条件スナップショット](./feature-plans/runtime-validation-report.md#r-06r-07候補復帰条件スナップショット2026-10-17-1657-jst)と[GitHub candidate follow-up](./feature-plans/runtime-validation-report.md#github-candidate-follow-up)を参照。PR作成/ review / CI完了後、候補commit/imageの対応を固定し、本番相当image受入を行う。ECR publicationは対象SHAを提示して別途承認を得てから開始。本番反映はR-07の個別明示承認まで行わない |
| R-07 | 本番投入前確認と明示承認による反映 | 未実施。R-06成功後、対象・差分・復帰条件を提示した個別承認が必要 | 対象instance/current/DB、運用helperと候補sourceの一致、容量、fresh backupの固定Version/hash/KMS/鮮度、migration可否と復帰referenceを確認する。変更差分・停止/失敗時対応・投入データ範囲を提示して承認後に実行。旧V34専用referenceを異なるschemaへ流用せず、DB初期化や既存資格情報上書きを暗黙に含めない |
| R-08 | 反映後の本番受入・清掃・記録 | 未実施。R-07の反映後のみ | 固定release/schema/health、両HTTPS、合成認証/認可/保存・実行・再読込、重要な失効と変更した設定/時刻操作を確認し、投入内容をDBから照合する。合成アカウント停止・一時secret/専用資源清掃、backup/timer/監視を確認し、結果と未確認を残す。旧releaseの成功証跡だけで新変更を受入済みにしない |

#### B. 並行して閉じる運用・利用開始の別ゲート

| 項目 | 残っていること | 完了条件・境界 |
|---|---|---|
| H-01 次回定期backup | 更新後の新release設定での自動完了を未観測 | 00:05 JSTの観測では10月17日02:03:45 JST予定。実行後にsystemd、receiptのrelease/DB参照、固定S3 Versions/hash/KMS/鮮度をread-only確認する。今回の整理では実行結果を取得しておらず、手動backupや長時間待機はしない |
| H-01 鍵/通知/復旧 | 独立offline鍵custody、人間のメール開封、復元先Tomcat/browser、本番完全DR・切替・RTO/RPO | 所有者確認と復元先受入を分ける。既存Mac鍵による復号/固定backupの隔離DB復元/実Control・DAO読戻し/SNS配送は完了済み。本番DB上書き・鍵コピー・切替は別計画/承認 |
| H-02 評価品質と実運用規模 | 利用者の手動E2E、期待評価との校正、同時利用/queue、quota/予算・容量 | R-05の技術E2Eとは別。採点者・期待結果・負荷モデル・測定区間を定める。画面3秒/実行結果2秒の目安、DB1秒/ログ保存3秒等は[既存性能要件](../function-specification.md#パフォーマンスperformance)を参照し、新しいSLAを独断で追加しない。実APIは合成データだけとし、要求数・費用上限・停止条件を先に承認する |
| H-03 対応ブラウザー/OS | Chrome/Firefox/Safari/Edge、対象OS、OS標準のファイル選択/保存操作 | [既存対応環境](../function-specification.md#対応環境)の最小320 CSS px、通常/狭幅、ページと局所スクロールを確認する。ChromiumだけやPlaywright WebKitを実Safari/全OS受入と扱わず、対応範囲を自動で狭めない。全並行編集パターンの総当たりをP20の新しい完了条件にはしない |
| 正式な研究同意/IC-008/010 | 正式文面はユーザーが後日提供し、それまで正式化と研究利用開始を保留。紙の運用、学校の承認、研究出力の管理、保持満了/終了時手順等も別 | [同意要件](../function-specification.md#研究協力同意確認機能優先度低)と[実装契約](./implementation-contract.md#研究同意文書と研究利用の開始2026-10-17確認)に従う。実装/合成検証は先行可だが、V10仮文面を正式版とせず、実データ外部送信や追加API要求の承認を兼ねない。IC-010の全体削除を自動実装/実行しない |

#### C. 後続の基盤整備

[既存負債](./ecr-deployment-debt.md)のIMDS/socket/SSM/IAM、署名、保持/GC、
可変Python runtime管理、最終image gateの継続自動化、復旧手順改善を別計画で維持する。
優先度「高」の残リスクを解消済み・安全証明済みとは扱わず、重大な露出が実測されたら
既存方針に従い最小対応と承認判断を提示する。IAM/firewall/鍵/daemonは自動変更しない。
自動本番デプロイは引き続き採用しない。
T-01のfixture混在一般化、T-03のGradle/logging警告、任意Pages等も別の整備であり、
成功済みP20や現releaseを一律に未完了へ戻す理由にしない。

#### 検証・判定の扱い

- 既存JUnit/機能別MySQL・実HTTP runner/Playwrightを再利用し、テストのために確定設定を変更しない。
  実行時はDocker・Node.js/browserの利用可否を独立に確認する。
  ホストNode.js未導入だけで、実績のあるPlaywright container利用まで不可と判断しない。
- 変更対象の最小テストを先行し、時刻等の共通基盤変更や次回候補の節目には全体回帰を行う。
  既存の全体コマンドは`gradle --offline --no-daemon clean build --warning-mode all --console plain`。
  opt-in DB/HTTP/browserは機能別runnerの環境条件と分離schemaを使い、通常buildのskipで代替しない。
- 実行command/exit code/成功・失敗・skip、browser範囲、保存後読戻し、cleanup、未確認を記録する。
  失敗は[エラーレポート](./error-report.md)へ残し、成功済みの過去履歴と今回結果を分ける。
- **今回の整理で新しいtests/build/browser/本番readbackは実行していない。**
  内容の確認には既存証跡を使い、文書変更の`git diff --check`だけを今回の検証とする。

### 機能仕様書との網羅対応（2026-10-06再照合）

| 利用者 | 機能仕様書の機能 | 優先度 | ロードマップ | 現状 |
|---|---|---|---:|---|
| 教師 | アカウント情報管理（生徒アカウント管理） | 低 | 12 | 実装・専用DB/認証browser受入済み |
| 教師 | 教師本人のアカウント管理 | 中 | 12a | 実装・検証済み。業務権限とは独立した本人情報、初期/再設定後の必須変更・通常変更 |
| 教師 | 課題進捗確認 | 高 | 14 | 実装・専用MySQL/認証ブラウザー受入済み。実ファイルのdownloadイベント確認はブラウザー環境制約 |
| 教師 | 授業演習コード確認 | 高 | 15 | 実装・DB/認証HTTP・ブラウザー受入済み。範囲と未確認事項は[工程15最終受入](./feature-plans/teacher-exercise-review.md#最終受入2026-10-08)を参照 |
| 教師 | 提出課題確認 | 高 | 16 | 第155版でプロトタイプ構成/操作を再現し通常/狭幅受入済み。実行コピー・元提出・監査CSVを分離。詳細は[最終受入](./feature-plans/teacher-submission-evaluation-review.md#提出画面のプロトタイプ再現最終受入2026-10-08第155版) |
| 教師 | 評価確認 | 高 | 17 | 第156版でプロトタイプ構成/操作を再現し通常/狭幅受入済み。保存済み評価/履歴/理由/ログとJSON・ZIP・最新同意監査CSV。詳細は[最終受入](./feature-plans/teacher-submission-evaluation-review.md#評価画面のプロトタイプ再現最終受入2026-10-08第156版)。手動採点は対象外 |
| 教師 | アンケート結果確認 | 低 | 18 | 実装・受入済み。[工程18最終受入](#工程18最終受入2026-10-08)。回答別履歴・下書き、6条件式/全列sort、仮名化出力と監査を検証 |
| 教師 | 課題編集 | ― | 10 | 下書き/学校所属/削除・復元/公開・期限処理/改訂/独立系列コピー/期限延長/対象クラス追加を実装し、専用DB統合テストと認証browser受入済み |
| 教師 | プロンプト設計 | 中 | 10、AI連携は19 | 基本編集/版管理と合成provider受入は完了。Gemini実API受入は延期 |
| 教師 | コード配信（授業演習コード配信） | 中 | 13 | 配信処理・画面再現・単一校制約を実装し、専用DB検証と認証browser受入済み。[コード配信計画](./feature-plans/teacher-exercise-distribution.md)を参照 |
| 管理者 | 教師アカウント管理 | 中 | 11 | 実装・受入済み。管理者IDは小文字adminに固定 |
| 管理者 | 学校管理 | 中 | 11 | 学校登録・名称/レベル管理・管理者2タブ統合は実装済み |

この対応表は機能仕様書の対象機能を漏れなくロードマップへ割り当てるための索引であり、詳細要件・受入条件はリンク先仕様と各工程を正本とする。

### 次の着手と意図的な保留（2026-10-16 JST更新）

- **正式同意文面の提供待ち（2026-10-17合意）:** ユーザーが後日提供する。それまで文書の正式化と研究利用開始は保留するが、設定保全・時刻修正・合成データの実装/検証は先行する。通常学習/同意状態の既存条件を変えず、本番変更や追加API要求は別承認とする。

- **H-04方針合意（2026-10-17）:** ユーザーがUTC保存/比較・日本時間（Asia/Tokyo）入出力を選択。本番デモ履歴は継承不要とし、確定済み初期設定の保全/再投入検証を先に行った上で再作成可能とする。[第181版](../function-specification.md#第181版-日時の統一方針)、実装契約、DB共通基準等へ反映済み。実装・移行は未完了。本番初期化/更新・資格情報変更は対象/手順提示後の別承認で、鍵は維持する。下記00:07の「方針判断待ち」は調査時点の履歴。

- **初期設定の確定（2026-10-17ユーザー確認）:** 研究同意文書を除き、現行ローカルの初期設定を確定版として採用する。[第180版の採用基準](../function-specification.md#第180版-db初期化後の初期設定の採用基準)と[本番運用の整理](./production-deployment.md#db初期化後の初期設定2026-10-17ユーザー確認)に記録した。設定内容の再合意待ちは解消し、投入手順・本番反映・利用者手動E2E/採点品質は別に残す。今回はDB初期化・データコピー・資格情報変更・追加API要求を行っていない。正式同意文書とH-04の時刻契約等の別の未決事項は、この設定採用合意では解消しない。

- **最小デプロイ完了（20:40:36 JST最終確認）:** PR #61のmain `41228ca...`、CI `38862737820`・ECR `38863619749`、固定7imageの隔離/native受入後、明示承認を得て既存updateを1回実行した。18:06:18 JSTにpublished確認、V34のみ適用、既存DB image/container/Volume維持。本番Runner 1 testと公開HTTPS browser正常2件＋停止後2件は全成功/exit 0/skip 0。所有コード・成功実行のDB読戻し、既存account不変、合成2account停止/旧session失効を確認。fresh backupの固定3 S3 Version/KMS/hash/鮮度、5healthy/restart 0、config/3timer/重大log0を確認し、今回のpassword/cookie/生成payloadと専用資源は限定清掃済み。次は通常運用で次回定期backupの自動完了を観測する。独立鍵保管・正式品質/手動E2E・全OS・本番完全DRは別ゲートであり、自動着手しない。[運用記録](./production-deployment.md#2026-10-16-最小デプロイの進捗)、[runtime判定](./feature-plans/runtime-validation-report.md#2026-10-16-最小デプロイの最終image-gate)を正本とする。
- **デプロイ後の整理（2026-10-16）:** ユーザー選択により、定期backupを手動で前倒しせず、実運用開始前の保留事項と開始条件を[受入候補一覧](./production-deployment.md#デプロイ後の受入候補と開始条件)へ整理した。続く質問UIでH-04の時刻/予約・期限処理のread-only調査を選択済み。実設定と日時変換を照合し、設定変更・業務更新・追加API要求は行わない。独立鍵保管・研究利用条件/IC-010・正式評価E2E/品質・負荷/費用・対応OS・完全DRを別判定として維持する。既存要件の変更は行わず、機能仕様書の改版は不要。
- **H-04調査完了（2026-10-17 00:07 JST）:** OS/JVM/DBと実pool3件は実効UTC、NTP同期正常。日本時間への明示変換がない入力/保存/表示経路を確認し、全体統一は未対応として残した。metadata読取と補完読取は各exit 0、サービス/config/DB/Volume不変。テスト再実行・業務操作・追加APIはなし。次はUTC保存/日本時間入出力等の方針と既存データの扱いを合意してから修正計画へ進む。timezone設定だけの変更や一括9時間補正は行わない。[詳細と根拠](./production-deployment.md#h-04-調査結果2026-10-17-00050007-jst)を参照。次回定期backupは10月17日02:03:45 JST予定で、引き続き自動実行後の観測待ち。
- **08:47 JST調査追補:** SSO再認証・対象確認後、本番current `a6e90edf...`/5サービスhealthy・nginx0644の非root読取・両health200を確認した。定期backupの7回正常完了と最新receipt/3固定S3 Version/local暗号化hashの一致はreadonly検証exit 0。現在の本番障害継続/初回定期実行の観測待ちは解除し、独立CloudWatch API読取のAccessDenied、業務DB復旧・鍵custody、将来ECR release gateは残す。次は監視値の所有者確認または別承認の既存読取経路を選ぶ。再deploy/backup再実行/IAM変更/復元は行わない。詳細は[本番調査記録](./production-deployment.md#2026-10-16-本番読み取り調査)を参照。
- **09:36 JST監視追補:** 別承認の`ppe-infra-admin`で同一accountの独立監視readbackに成功。alarm OK/ActionsEnabled=true、保存FreshBackupの24連続hourly区間全て正常を確認し、監視API読取の未確認を解除した。IAM/alarm設定変更・通知試験なし。alarm起点の実配送と業務DB復旧/鍵custodyは別ゲート。
- **工程20完了・現在の次作業:** P20-01〜10と全29 Servlet/71 routeの証跡対応を完了。最終全体buildは147 suites / 561 tests、373 passed / 188 skipped、failure/error 0、exit 0。独立した専用schemaの課題2件/演習schema1件、固定backupの隔離復元と実Control/DAO読戻し、承認済みSNS実配送1/失敗0も成功し、12:38 JSTの専用資源清掃・共有不変/両login200を確認。工程20の元の受入範囲は**PASS / 完了**。次は研究/運用開始前の別ゲートの対象と優先順を利用者と決める（独立鍵保管、正式品質/手動E2E、対象OS、時刻契約など）。本番切替・実データ送信・将来ECR releaseは自動着手しない。2.5 Pro候補診断のexit 1は保持し、既定/選択肢の変更・追加Gemini要求は不要。下記の日付付き`PARTIAL`は当時の履歴。[最終対応表](./feature-plans/phase-20-cross-feature-validation.md)、[課題台帳](./error-report.md)、[Runtime Validation Report](./feature-plans/runtime-validation-report.md)、[運用受入](./production-deployment.md#2026-10-16-追加承認による復元通知試験)を正本とする。
- **2026-10-14 14:22 JST追補:** 現行survey ID 4の設問/選択肢定義を変えず、専用DBの合成生徒で認証ブラウザー表示・下書き保存/再読込・提出後参照・同意撤回済み別生徒の403を確認。Playwright 2/2成功、専用DB再読込は6回答/6設問で一致。課題一覧の「設定済み」列とプロンプト操作、混在大小文字のヒントCodeMirrorプレビュー、375px/1280px横overflowなしも確認。専用資源は清掃し、共有8080/DBは維持。工程20全体は他未確認範囲が残るため`PARTIAL / NEEDS_SIGNOFF`。詳細は[横断検証計画](./feature-plans/phase-20-cross-feature-validation.md)と[Runtime Validation Report](./feature-plans/runtime-validation-report.md)を参照。
- **2026-10-14追試:** 教師演習コードの担当校/クラス・機能権限・状態/同意境界を専用MySQL/HTTPで再確認し、各1 test passed、WAR成功。生徒向け授業演習では実ブラウザーpointerでフォルダ行余白から展開/折りたたみできることを新しいPlaywright test（1/1 passed）で確認し、再読込後のツリー維持とtoggle時にデータ更新がないことも確認。専用環境清掃後、共有app/DBの稼働を維持。P20-03の全機能網羅など未確認範囲が残るため`PARTIAL / NEEDS_SIGNOFF`を継続する。詳細は[横断検証計画](./feature-plans/phase-20-cross-feature-validation.md)、[Runtime Validation Report](./feature-plans/runtime-validation-report.md)、[フォルダ行ブラウザーテスト](../../e2e/phase20/exercise-folder-row-toggle.spec.js)を参照。
- 工程9「授業演習」はT001〜T051が完了している。過去の第95〜104版記録にあるT030/T031未完了や「次は演習のこの機能」といった記述は当時点の履歴であり、現在の状態として扱わない。詳細・最終検証の範囲と限界は[授業演習計画](./feature-plans/student-exercise.md)を参照する。
- **工程10 S1・S2/T014受入完了**: [初回スライス計画](./feature-plans/teacher-task-draft.md)のT001〜T015と、[教師プロンプト計画](./feature-plans/teacher-prompt-ai.md)のS2/T014を参照。再評価job履歴一覧、履歴からの詳細表示、対象生徒別の提出revision・状態・評価点を隔離認証browserで確認済み。専用V1〜V18 DB test、全体JUnit 204件（135 pass / 0 fail / 69 skip）とWAR生成も成功。証跡は[エラーレポート](./error-report.md)と[S2実装チェックポイント](./feature-plans/checkpoints/teacher-prompt-ai/tasks-to-impl.yaml)を参照。共有8080/DBは変更していない。
- **工程10 S3完了**: [S3計画](./feature-plans/teacher-task-s3.md)に従い、必須の単一学校所属（V19、フォーム〜DAO）と、同一学校の課題管理権限教師による論理削除・下書き復元を実装。編集は作成者に限定し、管理者操作は含めない。空の専用DBとプロジェクト専用Compose DBへV1〜V19を適用し、専用DB統合testと認証browserで削除・復元を確認した。プロジェクト専用Compose DBを再構築し、新しい合成デモを投入済み。migration自体はデータ削除をせず、共有DB/他DBは変更していない。
- **工程10 公開・改訂・独立コピー・期限/クラス変更実装と受入完了**: 公開・予約公開・期限処理は[教師課題公開計画](./feature-plans/teacher-task-publication.md)、学習開始前の同系列改訂と学習開始後の独立コピーは[公開後改訂計画](./feature-plans/teacher-task-revision.md)、期限延長と対象クラス追加は[専用計画](./feature-plans/teacher-task-deadline-and-class-changes.md)を参照する。専用MySQL統合テスト31件（27成功/4 browser-fixture skip/0失敗）で期限切れ割当の再開、既存提出履歴保持、stale version拒否、重複/他校クラス拒否、監査・冪等再送、archivedを含む過去割当クラスを追加候補から除外する履歴取得を確認した。認証browserで期限延長・クラス追加と成功通知を確認した。専用Compose環境は検証後に削除済み。AI実API確認は工程19へ延期する。
- **工程11・12・12a・13完了**: 管理者の教師/学校管理、[生徒アカウント管理](./feature-plans/teacher-student-accounts.md)、[教師本人のアカウント管理](./feature-plans/authentication-and-login.md#工程12aの完了記録2026-10-07)、および[工程13コード配信](./feature-plans/teacher-exercise-distribution.md)は実装・検証・認証browser受入済み。工程13について過去に記載した「受入中」「履歴操作などの受入待ち」は当時の記録であり、現在状態ではない。
- **課題配信と授業演習コード配信の区別**: 課題の公開/改訂は工程10の課題状態操作、授業演習コード配信は工程13の演習フォルダ/ファイルの配布であり、別機能として管理する。
- **AI稼働確認の再開・合成受入**: 2026-10-08の明示指示とモデル/時間条件の承認により、Pro高思考の教師縦断・通常評価の実API/DB確認と認証画面確認を実施した。既定は3.1 Pro Preview、既存版は元のモデルを保持する。実提出/コードログを使う検証、採点品質校正、本番quota/費用・負荷試験は未実施。詳細は[教師プロンプト計画](./feature-plans/teacher-prompt-ai.md)の最新受入記録を参照する。
- OS標準のフォルダ選択/ZIP保存ダイアログと全ブラウザー/OS組合せは未確認。通常Chromeの単体.py保存と内容一致は利用者確認済み。教師工程へ進む承認をこれらの検証完了とは扱わず、演習計画の残事項として保持する。
- 教師工程前デバッグは、[計画T001〜T014](./feature-plans/pre-teacher-debugging.md)の修正・再検証・ローカル反映・専用環境清掃・記録まで完了し、報告済み。対象5件の未解決ブロッカーはない。同計画と[エラーレポート](./error-report.md)は完了済みの作業記録として保持する。
- 工程8「評価・アンケート」全体の実設定・ユーザー手動E2Eは、教師向け画面完成後、設定が可能になってから再開する。今回の既知バグに必要な専用合成DB・認証HTTP・回答保存確認だけを先行し、T006全体やその他の未確認事項を完了扱いにしない。
- 工程7「生徒エディター」の30秒スナップショットと二つのbrowser context間のstale編集拒否は工程20で実時間Playwright確認済み。重複提出の冪等性・提出後の再提出確定の全経路は[エディター計画](./feature-plans/student-editor.md)の未確認事項として保持する。教師側の課題編集・配信・提出閲覧との接続に影響する項目は、そのスライスの回帰確認へ組み込む。
- 生徒/教師ID統一とパスワード変更UX・更新後遷移・認証情報履歴の保存は本実装へ反映済み。根拠・検証・未確認事項は[生徒ホーム等の計画](./feature-plans/student-home-consent-account.md)を参照する。
- 管理者の2タブ統合・モーダル中心UIは既存デザインに沿って本実装へ接続済み。`/admin/home`は`/admin/teachers`へリダイレクトし、`/admin/schools`の独立APIは維持する。プロトタイプは変更していない。
- **公開URLの横断整理（2026-10-08）**: [URL整理方針](./url-routing-policy.md)に従い、24 Servletのmappingと全JSP業務URLを確認。本人account・教師のstudents・管理者のteachers・生徒password/consentを正規化し、旧URL7種は認証後にGET/HEAD302、POST等307で誘導する。全体JUnit343件（223成功/120ゲートskip/失敗0）とWAR、専用合成MySQL/実アプリの認証HTTP5件、教師/生徒/管理者の画面・JSON読込を確認した。8080へ反映して両login200、host別rootと保護URLの認証redirect、実class定数を確認済み。専用DB/grant/containerは削除し、課題10/11のmetadata hashと18081の稼働を保持。全ブラウザー・CLIのポインタ操作保証は対象外とし、統合ブラウザーの一部click timeoutと代替確認は[エラーレポート](./error-report.md)に記録した。

### 工程10着手時の準備記録（履歴）

以下は工程10開始時点の準備記録であり、現在の着手順ではない。現状と次作業は本書冒頭の工程一覧・「次の着手と意図的な保留」を正とする。

1. [AGENTS.md](../../AGENTS.md)の教師工程引継ぎと作業ツリーの未コミット変更を確認する。完了済みの基盤・演習・デバッグを作り直さない。
2. 工程10の機能仕様・状態ルール・DB/API契約と、既存の認証・DAO・Control・DBを確認する。学校/クラスの権限、課題状態、適用版、配信状態を初回スライスの前提として確認する。既存UIの把握に必要な場合のみ課題/プロンプト/配信プロトタイプを参照し、変更・同期は行わない。
3. [教師課題下書き計画](./feature-plans/teacher-task-draft.md)と[最終受入レポート](./feature-plans/checkpoints/teacher-task-draft/batch-report-T014-T015.yaml)を確認する。S1のT001〜T015は完了。残る正式評価/アンケートE2Eは工程8の保留として扱う。
4. S2（ルーブリック/プロンプト準備・AI生成）とT017/T018実装、T030境界テスト、T031 job status/poll browser受入は完了。preview/確定redirect受入にはtest seamの設計判断が残る。[計画](./feature-plans/teacher-prompt-ai.md)と[実装チェックポイント](./feature-plans/checkpoints/teacher-prompt-ai/tasks-to-impl.yaml)に従い、共有8080への反映は別途明示依頼がある場合のみ行う。Gemini実API/実提出送信は別途明示承認なしで行わない。画面遷移図/プロトタイプは必要性が明確でない限り変更しない。

## ロードマップ一覧

| 順 | 作業 | 完了後に得られるもの |
|---|---|---|
| 0 | 状態・データ設計の実装契約を確定 | 画面状態と DB 値をつなぐ合意済みの対応表 |
| 1 | ローカル開発コンテナとビルドを整える | 反復可能な Gradle / Tomcat / MySQL 開発環境 |
| 2 | DB とマイグレーションを作る | 空 DB から作成できる合意済みスキーマ |
| 3 | DB 接続基盤と DAO を作る | 設定外部化された接続、共通 DAO パターン |
| 4 | Web 共通基盤と画面資産を整える | JSP 配置、共通レイアウト、エラー・通知処理 |
| 5 | ユーザー初期データと認証・ログインを作る | 生徒・教師の DB 認証と権限付きセッション |
| 6 | 生徒の同意・課題一覧・アカウントを作る | DB から取得する生徒トップ導線 |
| 7 | Python 実行と生徒エディターを作る | 安全な実行、保存、コードログ、提出 |
| 8 | 評価・コードログ・アンケートを作る | 評価結果確認までの生徒フロー |
| 9 | 授業演習を作る | 課題モードと分離した演習・ファイル保存 |
| 10 | 教師の課題・プロンプト・配信を作る | 教師の作成・公開・配信フロー |
| 11 | 教師の進捗・提出・評価・CSV を作る | DB に基づく教師の確認・運用機能 |
| 12 | 管理者の教師アカウント管理を作る | 管理者専用の教師アカウント管理・権限制御・監査機能 |
| 13 | 機能横断のローカル検証を行う | 再現可能な検証結果と段階2完了判定 |

---

## 0. 状態・データ設計の実装契約を確定する

**前提:** 機能仕様書、DB 設計書、クラス図、状態ルール、画面遷移図が用意されていること。

**主な参照資料:** [状態ルール一覧](../state-rules/README.md)、[状態命名標準](../state-rules/state-naming-standard.md)、[実装用状態表](../state-rules/implementation-state-table.md)、[DB 設計書](../database-design/README.md)、[テーブル定義](../database-design/table-definitions.md)、[クラス図一覧](../class-diagram/README.md)、[機能仕様書](../function-specification.md)。

**作業:**

1. `saveStatus`、`learningStatus`、`publicationStatus`、`distributionStatus`、`evaluationStatus` を別概念として整理し、各画面・操作で必要な状態を洗い出します。
2. 状態ルールの状態名・イベント・遷移条件・操作可否を、DB テーブル / カラム / 保存値へ対応付けます。
3. [実装契約の要確認事項](./implementation-contract.md#資料間の要確認事項)を現行資料と照合します。既に定義された保存先・状態値と、未決の遷移・運用条件を分け、関係者に確認して仕様へ反映します。
   - 課題公開状態と削除・復元の遷移条件
   - 課題編集と生徒ごとの保存・学習・進捗状態の責務、同期条件
   - 提出・評価の状態語彙と画面表示、履歴の対応
   - 授業演習・アンケートの既存保存先と状態ルール・制約の対応
   - プロンプトの版管理・適用版選択と現行テーブル定義との対応
4. 確認結果を「画面状態、DB 状態値、変更イベント、許可操作、失敗時の扱い」が分かる実装契約として記録します。

**成果物:** [実装契約](./implementation-contract.md)の機能別対応表を更新し、合意事項を記録。必要に応じて機能仕様・状態ルール・DB 定義も更新。

**確認・完了条件:** 画面のボタン制御、Servlet / Control の遷移条件、DB に保存する状態値が一意に対応し、矛盾・未決定事項が実装ブロッカーとして残っていないこと。

---

## 1. ローカル開発コンテナとビルドを整える

**前提:** 作業ブランチを用意し、現状の Gradle・Tomcat・MySQL の構成を把握していること。

**主な参照資料:** [AGENTS.md](../../AGENTS.md)、[README.md](../../README.md)、[build.gradle](../../build.gradle)、[docker-compose.yml](../../docker-compose.yml)、[.env.sample](../../.env.sample)、[containers/](../../containers/)、[システム構成ガイド](./system-strucure-guide.md)。

**作成・変更対象の候補:**

- `docker-compose.yml`
- `.env.sample` とローカル専用 `.env`
- `containers/app/Dockerfile`（WAR 単体実行用。ローカル開発 Compose は Gretty を使用）
- `containers/db/Dockerfile`
- `containers/gradle/Dockerfile`
- `build.gradle`

**作業:**

1. Gretty アプリと DB の役割・接続先を整理します。固定 root パスワード、Java 側接続設定との DB 名不一致を解消します。
2. DB のユーザー名・パスワード・DB 名・ポート等をローカル設定から渡し、ソースコードや追跡対象ファイルに秘密値を置かない構成にします。開発用 root 接続とアプリ接続用ユーザーを分離します。
3. Java 21 toolchain、Gradle、Gretty（Tomcat 9）の Servlet/JSP 実行環境と WAR ビルドを整合させ、依存解決できることを確認します。
4. 起動順序だけで DB の準備完了を仮定せず、接続リトライまたは health check を使ってアプリの起動時エラーを扱います。
5. ローカル開発環境に必要なポートだけを公開し、DB ポートを外部ネットワークに不用意に公開しないようにします。

**確認:**

- ローカル Compose のビルド・起動・停止・再起動が再現できる。
- Gradle で WAR が生成され、Gretty（Tomcat 9）の起動ログにデプロイエラーがない。
- Java アプリから `dataSource.properties` と環境変数で設定された MySQL へ HikariCP 経由で接続できる。
- `.env`、パスワード、API キーが Git の追跡対象でない。

**完了条件:** 開発者が同じ手順で環境を再構築でき、アプリと DB の設定差異がないこと。本番向け Compose はこの段階では作らず、ローカル用と分離します。

---

## 2. DB とマイグレーションを作る

**前提:** 作業0で状態値・テーブル責務・未決事項を確認済みであること。

**主な参照資料:** [DB 設計書](../database-design/README.md)、[テーブル定義](../database-design/table-definitions.md)、[クラス図](../class-diagram/)、[状態ルール](../state-rules/README.md)、[CSV / JSON フォーマット](../format/)。

**作成・変更対象の候補:**

- `build.gradle`（Flyway 依存と実行方法）
- `src/main/resources/db/migration/V1__...sql` 等の Flyway SQL
- 開発専用 seed SQL または seed 用データ

**作業:**

1. 合意済み DB 定義に沿って、ユーザー、クラス、課題、提出、コードログ、評価、同意、配信、監査ログ等のテーブルと外部キーを作成します。
2. 状態値と削除方法、NULL 可否、一意制約、参照整合性、時刻の扱いを定義します。論理削除は仕様で対象とするデータに適用し、物理削除と混同しません。
3. 演習ファイル・演習進捗、アンケート回答等、保存先が必要と確認されたデータのスキーマを追加します。
4. コードログの検索・時系列表示・評価参照に必要なインデックスを設計します。
5. 空 DB へ Flyway を適用し、スキーマが作成されることを確認します。
6. 開発用初期データは本番データから分離します。パスワードはハッシュとして扱い、サンプル個人情報を含めないようにします。

**確認:**

- 空 DB からマイグレーションを順番に適用できる。
- 同じマイグレーションが二重適用されない。
- 外部キー、一意制約、状態値の制約が期待どおりに働く。
- seed を適用しても、本番データ・秘密情報を含まない。

**完了条件:** 新しいローカル DB をゼロから構築でき、合意済み状態対応表とスキーマに差がないこと。

---

## 3. DB 接続基盤と DAO を作る

**前提:** 作業1の接続設定と作業2のスキーマが使えること。

**主な参照資料:** [システム構成ガイド](./system-strucure-guide.md)、[DB 設計書](../database-design/README.md)、[機能仕様書](../function-specification.md)の JDBC + DAO 方針。

**作成・変更対象の候補:**

- `src/main/java/config/` または `src/main/java/lib/mysql/` の設定・接続管理
- `src/main/java/dao/`（または既存規約に沿った DAO パッケージ）
- 各機能の Entity / DTO
- DB アクセスのテスト

**作業:**

1. DB 接続情報を環境設定から読み込み、接続失敗を例外として呼び出し元へ伝えます。既存の `Client` のように接続エラーを握りつぶして `null` を返す接続方法は業務処理へ使いません。
2. `Connection`、`PreparedStatement`、`ResultSet` を try-with-resources 等で必ず閉じます。
3. DAO は SQL と永続化に責務を限定し、入力検証・権限判定・業務状態遷移を置きません。
4. まず認証に必要な `UserDao` と DB 接続を作り、取得・認証用データ検索ができることを確認します。その後、課題、提出、ログ、評価など機能ごとに DAO を追加します。
5. 更新が複数テーブルに及ぶユースケースでは、Control がトランザクション境界を管理できる形にします。
6. SQL は `PreparedStatement` を使い、入力値を SQL 文字列へ連結しません。

**確認:**

- DAO が DB から取得したデータの型・NULL・文字コードを正しく扱う。
- 接続失敗、SQL 制約違反、対象データなしを成功値に見せかけない。
- DAO 単位で正常系・異常系を再現できる確認手順がある。

**完了条件:** 接続設定を変えずに複数 DAO から DB を利用でき、リソース漏れや失敗の隠蔽がないこと。

---

## 4. Web 共通基盤と画面資産を整える

**前提:** ローカルビルドと Servlet/JSP の動作、DAO の DB 接続が確認済みであること。

**主な参照資料:** [画面遷移図](../screen-flow-diagram.md)、[画面遷移プロトタイプ](../../screen-flow-diagram/webapp/)、[フィードバックUIガイド](../feedback-guideline.md)、[状態ルール](../state-rules/README.md)。

**作成・変更対象の候補:**

- `src/main/webapp/WEB-INF/` の JSP 画面
- `src/main/webapp/css/`、`src/main/webapp/js/`、画像等の静的資産
- 共通テンプレート JSP、Servlet、Filter、エラー画面
- `web.xml` または Servlet annotation 設定

**作業:**

1. 画面遷移図と実際のプロトタイプを照らし、画面ID、URL、ロール、遷移元・遷移先を整理します。
2. HTML のレイアウト・スタイル・必要なクライアント側操作を本実装の JSP / 静的資産へ移します。
3. 画面内の固定ユーザー名、課題一覧、評価値、ログ、クラス、アンケート等を除去し、JSP には Servlet が用意した表示用データを渡します。
4. プロトタイプの JavaScript にあるデモ用保存・ログイン・状態変更・削除・評価結果を外し、実 Servlet endpoint へ接続する箇所だけ残します。
5. 共通レイアウト、認証前後の遷移、エラーページを用意します。
6. 通知や確認 UI は[フィードバックUIガイド](../feedback-guideline.md)の shared feedback を使用します。

**確認:** JSP に仮データがなくてもレイアウトが崩れず、Servlet が渡す実データを表示できる。リンクやフォームの送信先が実際のサーバー側ルートと一致している。

**完了条件:** 画面資産と業務ロジックが分離され、画面のサンプルデータ・デモ動作が本実装に残っていないこと。

---

## 5. ユーザー初期データ、認証・ログインを作る

**前提:** `users` スキーマ、`UserDao`、JSP 共通基盤ができていること。

**主な参照資料:** [機能仕様書](../function-specification.md)のアカウント・認証要件、[ログイン画面遷移](../screen-flow-diagram.md)、[状態ルール](../state-rules/README.md)、[ユーザーテーブル定義](../database-design/table-definitions.md)。

**作成・変更対象の候補:**

- `UserDao` / `AuthenticationControl`、認証 Filter / Servlet
- `src/main/webapp/WEB-INF/student/account/login.jsp`
- `src/main/webapp/WEB-INF/teacher/account/login.jsp`
- 生徒・教師ログイン画面の CSS / JavaScript
- 開発専用の初期ユーザー作成手順

**作業:**

1. ログイン ID でユーザーを取得し、パスワードハッシュを検証します。平文パスワードを保存・ログ出力しません。
2. ロール（student / teacher / admin）、有効状態、論理削除、初回パスワード変更要件を認証処理に反映します。
3. 成功時にセッションを発行し、セッション固定化を防ぐ対策とログアウト時の完全な無効化を行います。
4. 認証必須画面を Filter 等で保護し、画面を直接 URL 指定した場合もサーバー側でロールを判定します。
5. 生徒・教師ログインフォームをプロトタイプから移し、成功後の行き先を仕様の導線に合わせます。
6. 開発用ユーザーは専用の seed / 初期化手順で作ります。実利用者の個人情報や本番パスワードをコード・テストデータへ含めません。
7. セキュリティレベル2の初回・リセット後パスワード変更と、レベル1の変更制限を実装します。

**確認:** 正しい / 誤った認証、無効アカウント、セッション期限切れ、ログアウト後アクセス、ロール違い、初回パスワード変更必須を確認する。生徒で教師画面へ URL 直アクセスしても拒否される。

**完了条件:** 認証・ロール・所属範囲の判定が DB とサーバー側で行われ、画面側だけの仮ログインがないこと。

---

## 6. 生徒の同意・課題一覧・アカウントを作る

**前提:** 認証・Filter が機能し、実装契約に状態・DB対応があること。

**主な参照資料:** [機能仕様書](../function-specification.md)の生徒向け機能、[画面遷移図](../screen-flow-diagram.md)、[学生共通状態](../state-rules/student/state-rules-overview.md)、[同意状態](../state-rules/student/survey-state-rules.md)、同意・課題の DB 定義。

**作成・変更対象の候補:**

- 同意、課題一覧、アカウントの DAO / Control / Servlet
- `student/survey/consent`、`student/home/home`、`student/account/account` の JSP
- 対応する CSS / JavaScript

**作業:**

1. 同意状況を DB から読み込み、未回答 / 同意 / 非同意の状態に応じてアクセス・導線を制御します。
2. ログインユーザー本人のアカウント情報を DB から取得します。
3. 生徒本人に配信された公開中課題のみを DB から検索し、課題状態・学習状態を表示します。
4. HTML に書かれた課題・ユーザーの仮データを除去し、DB にデータがない場合は適切な空状態を表示します。

**確認:** 別生徒の ID を URL / リクエストで指定しても情報が見えず、同意前の制限と対象課題の絞り込みが正しく働く。

**完了条件:** ログインから同意・ホーム・アカウントまで、表示データが DB に基づいていること。

---

## 7. Python 実行と生徒エディターを作る

**前提:** 提出・保存・ログの状態対応と必要な DB スキーマが確定していること。同時に、コード実行環境の安全性を検証できること。

**主な参照資料:** [機能仕様書](../function-specification.md)のエディター・ログ・コード実行要件、[エディター状態ルール](../state-rules/student/editor-state-rules.md)、[実装用状態表](../state-rules/implementation-state-table.md)、[学習フロー図](../class-diagram/01-core-learning-flow.puml)、[テストケース形式](../format/task-testcase-format.csv)。

**作成・変更対象の候補:**

- 課題・提出・コードログ DAO / Control / Servlet
- Python 実行要求を扱う実行サービスと制限付きコンテナ設定
- `student/editor/editor.jsp`、CSS / JavaScript
- `src/main/resources` 等の設定・実行環境資産

**作業:**

1. 課題詳細と提出済みコードを DB から取得し、本人の対象課題であることを検証します。
2. エディター入力を未保存・保存済みとして区別し、状態ルールに基づいて保存・提出ボタンを制御します。
3. 課題モードの30秒スナップショット、手動保存、実行、提出イベントを仕様どおり DB に記録します。
4. コード実行はサーバー側で入力を検証してから独立した Docker サンドボックスへ渡します。ネットワーク無効化、CPU / メモリ制限、タイムアウト、出力上限、ファイルアクセス制限を設けます。
5. 提出時に想定入出力を確認し、不一致の結果を表示したうえで提出を継続できるようにします。
6. 提出の重複、提出後編集、再提出可否をサーバー側で判定し、操作と状態をトランザクションで保存します。
7. 未保存のまま離脱する場合の確認は shared feedback を使い、保存成功通知は DB 保存成功後にだけ表示します。

**確認:** 保存後の再読込、30秒ログ、実行イベント、失敗コード、タイムアウト、出力上限、ネットワーク遮断、提出後の編集拒否 / 再提出可否を確認する。

**完了条件:** エディターが DB と安全な実行基盤に接続され、ブラウザー内だけの保存・実行モックが残っていないこと。

---

## 8. 評価・コードログ・アンケートを作る

**前提:** 提出、コードログ、同意の保存と評価状態の対応ができていること。

**主な参照資料:** [機能仕様書](../function-specification.md)の評価・コードログ・アンケート要件、[学生評価状態ルール](../state-rules/student/evaluation-state-rules.md)、[学生アンケート状態ルール](../state-rules/student/survey-state-rules.md)、[AI API連携設計書](../ai-api-integration-design.md)、[評価クラス図](../class-diagram/02-evaluation-rubric.puml)、[AI・同意・ログ図](../class-diagram/04-ai-consent-log.puml)、[評価形式](../format/evaluation-format.json)、[ルーブリック資料](../rubric/)。

**作成・変更対象の候補:**

- Evaluation / CodeLog / Survey DAO・Control・Servlet
- Gemini 接続アダプター、匿名化・JSON 検証処理
- `student/evaluation`、`student/evaluation/log`、`student/survey` の JSP / CSS / JavaScript

**作業:**

1. 評価待ち・評価中・完了・失敗の状態を DB から取得し、状態ルールどおりに表示します。
2. AI 呼び出しの匿名化、最小データ送信、版情報と根拠ログ ID の記録、JSON スキーマ検証、規定の再試行と失敗表示を実装します。
3. 評価スコア・理由・根拠・コードログを DB から読み、権限を確認して JSP に表示します。
4. アンケートの未回答・進行中・提出済みを同意状態と混ぜずに保存し、必須項目と再編集可否を検証します。

**確認:** 同意状態に関わらず通常の AI 評価が行われ、同意なしのアンケート利用は拒否される、AI 失敗が成功扱いにならない、他ユーザーの評価が取得できない、アンケート再送信でデータが重複・上書きされないことを確認する。

**完了条件:** 評価・ログ・アンケートの全表示と状態遷移が永続化データから復元できること。

---

## 9. 授業演習を作る

**前提:** 演習用データの保存先と演習状態が DB 設計へ反映され、機能仕様との整合が取れていること。

**主な参照資料:** [機能仕様書](../function-specification.md)の授業演習機能、[演習状態ルール](../state-rules/student/exercise-state-rules.md)、[DB 設計書](../database-design/README.md)、[画面遷移図](../screen-flow-diagram.md)。

**作成・変更対象の候補:** 演習・ファイル DAO / Control / Servlet、`student/exercise` の JSP / CSS / JavaScript、Flyway migration。

**作業と確認:**

1. 演習一覧、フォルダ・ファイルの作成・取得・更新・削除を DB の所有者スコープ付きで実装する。
2. 各ファイルの最終保存コードを永続化し、演習状態（未開始・進行中・一時保存・完了等）を状態契約に従って扱う。
3. Python 実行は課題エディターと同じ安全なサンドボックス基盤を再利用する。
4. 課題モードとデータを混在させず、授業演習に30秒ごとのコードログを作らないことを確認する。

**完了条件:** ログインをやり直しても演習ファイルと状態を復元でき、コード履歴が課題モードへ誤って記録されないこと。

---

## 10. 教師の課題編集・公開・改訂を完了する

**現在の状態:** 下書き作成/編集、必須の単一学校所属、学校内クラス割当、論理削除/下書き復元、公開/予約公開、学習開始前の同系列改訂/再公開、学習開始後の独立系列コピー、期限延長、対象クラス追加は実装・専用DB/認証browser受入済み。教師プロンプトの合成およびGemini実API受入も工程19で実施済み。各範囲の記録は[課題公開計画](./feature-plans/teacher-task-publication.md)、[公開後改訂計画](./feature-plans/teacher-task-revision.md)、[期限延長・クラス変更計画](./feature-plans/teacher-task-deadline-and-class-changes.md)、[教師プロンプト計画](./feature-plans/teacher-prompt-ai.md)を参照する。

**参照資料:** [機能仕様](../function-specification.md)「課題編集」、[課題状態ルール](../state-rules/teacher/task-state-rules.md)、[実装契約](./implementation-contract.md)、[テーブル定義](../database-design/table-definitions.md)、[教師課題S3計画](./feature-plans/teacher-task-s3.md)、[公開計画](./feature-plans/teacher-task-publication.md)、[公開後改訂計画](./feature-plans/teacher-task-revision.md)。

**合意済み事項（2026-10-06）:** 課題全体とクラス別割当の公開状態を分離する。アプリ内定期処理を1分間隔で実行し、予約公開・提出期限到来をDB時刻で判定する。遷移は予定時刻/期限から最大約1分以内。期限切れ後も `late_submission_policy=allow` なら未提出者の初回提出のみ許可し、期限後再提出は禁止する。

**作業:** 公開条件（共通標準ルーブリック・現在適用プロンプト版を含む）、クラス別公開日時・提出期限、公開/予約/要更新/期限切れ遷移、改訂時の提出・評価履歴保持を照合する。公開/予約処理、期限判定、同系列改訂、学習開始後の独立系列コピー、期限延長、対象クラス追加は実装・受入済み。詳細な証跡は上記機能別計画を参照する。工程20では必要な回帰範囲を横断テスト計画に従って確認する。

**完了条件:** 課題状態とクラス別公開状態が仕様どおり永続化され、生徒側アクセス/期限判定と一致すること。

---

## 11. 管理者の教師アカウント管理を実装する

**現在の状態:** 実装・受入済み。教師管理/学校管理の2タブと作成・編集・資格情報・履歴モーダルを本実装へ接続した。初期/再設定パスワードは発行直後のみ表示・コピーし、DBにはハッシュだけを保存する。

**参照資料:** [機能仕様](../function-specification.md)「教師アカウント管理」「学校管理」、[アカウント状態ルール](../state-rules/admin/account-state-rules.md)、[認証計画](./feature-plans/authentication-and-login.md)、[実装契約 IC-009](./implementation-contract.md)、[管理者画面プロトタイプ](../../screen-flow-diagram/webapp/WEB-INF/admin/management.html)。

**実施結果:** 管理者限定の一覧/検索/詳細・作成・停止/解除・論理削除・パスワード再設定・学校/独立した9機能権限・ログイン/操作履歴・CSVを実装した。V20の更新版により競合を拒否し、更新後の教師セッションを失効させる。学校権限を解除した教師は自作下書きでもプロンプト読込/再評価previewを利用できない。資格情報・履歴保持・ロール/CSRF境界・学校管理回帰・375px表示を検証した。コマンド・件数・環境制約は[認証計画](./feature-plans/authentication-and-login.md#工程11教師アカウント管理追加バッチ2026-10-06)と[エラーレポート](./error-report.md)を参照する。

**完了条件:** 管理操作・認可・権限・監査がDBと再読込に反映され、資格情報が合意済みの安全要件を満たすこと。

---

## 12. 教師の生徒アカウント管理を実装する

**現在の状態:** 2026-10-07実装・受入完了。[実装計画と実行結果](./feature-plans/teacher-student-accounts.md)を参照。V21、専用DB統合テスト2件、259件の最終回帰（168成功/91 gate skip/失敗0）、WAR、8080の認証/CSV/状態変更/履歴と375px表示を確認した。専用DBテストの実行は最終回帰前のDBゲート有効runで成功している。

**参照資料:** [機能仕様](../function-specification.md)「アカウント情報管理機能」、[認証計画](./feature-plans/authentication-and-login.md)、[DBテーブル定義](../database-design/table-definitions.md)。

**作業範囲:** 担当学校/クラス内での生徒一括作成とID採番、学校設定のセキュリティレベル適用、初期パスワード/レベル2リセットと安全な一時表示、一覧/詳細、学校・クラスfilter、CSV、同意/初回変更状態、操作監査を実装する。全要求で学校・クラス権限をサーバー側検証する。

**解消済みゲート:** ロック解除Control/APIと機能・学校権限を接続した。認証正本はハッシュを維持し、教師確認が仕様上必要な初期/リセット資格情報だけを暗号化保存する。レベル2本人変更後は除去する。ユーザー不在時の自動実行判断と鍵の要件は[実装契約](./implementation-contract.md)へ記録済み。

**完了条件:** 作成した生徒がログインでき、権限外の学校/クラスを指定できず、資格情報とCSVが合意済みの安全要件を満たすこと。

---

## 12a. 教師本人のアカウント管理を実装する

**現在の状態:** 2026-10-07にT-SELF-001〜005を実装・検証し、V22と画面/APIを8080へ反映済み。全体270件中181成功・89明示ゲートskip・失敗0、対象DB6件成功、認証HTTP/ブラウザー受入を確認した。正確なコマンド・確認範囲・未確認事項は[認証計画の完了記録](./feature-plans/authentication-and-login.md#工程12aの完了記録2026-10-07)を参照する。工程13・14も実装・受入済みであり、工程15も後続工程として完了済み。現在の次作業は工程20。

**参照資料:** [機能仕様](../function-specification.md)「教師本人のアカウント管理機能」REQ-001〜003、[認証計画](./feature-plans/authentication-and-login.md)、[アカウント状態ルール](../state-rules/admin/account-state-rules.md)、[DBテーブル定義](../database-design/table-definitions.md#users)。

**作業範囲:** 本人情報（ID・状態・学校・業務機能権限）の参照、共通ヘッダーのアカウント導線、現在/新規/確認パスワード入力、初期/再設定後の必須変更と通常の任意変更、履歴/監査、全セッション失効と再ログインを実装する。業務権限がない教師にも提供し、学校・機能権限の自己変更は導入しない。第135版で従来の強制変更不要方針を変更した。既存プロトタイプの本人画面を参考に本実装へ接続する。

**画面仕上げ（2026-10-07）:** 管理者画面はプロトタイプに合わせて製品名ブランドヘッダーを表示しない。教師本人のアカウント情報・パスワード変更画面はフェードインを行わず、教師・管理者のログアウトはいずれも共通確認ダイアログで確定する。キャンセルではログアウトしない。

**実装前ゲート:** 生徒向け既存パスワード入力・検証・共有feedbackを再利用できる範囲と、教師の更新版/セッション失効・資格情報履歴を確認する。自己変更・管理者再設定/停止/権限変更の競合、本人認可・CSRF・失敗時rollbackを状態/DB契約へ反映する。プロトタイプは変更しない。

**完了条件:** 権限なし教師も自身の情報と任意変更を利用でき、他者の変更は拒否されること。成功後は新パスワードのみ有効で全旧セッションが失効し、失敗時は既存資格情報を維持すること。新旧パスワードが履歴・ログ・CSVへ出ず、監査と再ログイン通知が一致すること。

---

## 13. 授業演習コード配信を実装する

**現在の状態:** 配信処理、画面再現、単一校制約は専用MySQL検証と認証browser受入まで完了済み（2026-10-07）。課題の公開/改訂とは別機能であり、詳細な実装・検証記録は[コード配信計画](./feature-plans/teacher-exercise-distribution.md)を正とする。

**参照資料:** [機能仕様](../function-specification.md)「コード配信機能」、[配信状態ルール](../state-rules/teacher/distribution-state-rules.md)、[授業演習計画](./feature-plans/student-exercise.md)、DB設計/クラス図。

**前提・作業範囲:** 工程11/12で教師・学校/クラス権限と対象生徒が整い、配信履歴と予約実行の責務が確定していること。フォルダ/ファイルテンプレートと初期コード、複数クラス、クラス別予約・日時変更、配信状態、停止/再開/再配信、既存コードを保持する別フォルダ方式、履歴/差分を実装する。

**完了条件:** 配信が対象演習領域へ正しく反映され、再配信で過去フォルダ・コードを保持し、予約・状態・履歴がDBから復元でき、二重適用や担当外配信が拒否されること。

---

## 14. 課題進捗確認を実装する

**現在の状態:** 実装・専用MySQL統合テスト・認証ブラウザー受入済み（2026-10-08）。共有8080の空一覧と専用テストDBを用いた実データ行/詳細モーダルの両方で受入確認した。検索/絞込/ソート/手動・自動更新/条件保持/CSV内容/BOM/担当外詳細の拒否を確認。ブラウザーが実ファイルのdownloadイベントを通知しない制約は計画書へ記録した。

**参照資料:** [機能仕様](../function-specification.md)「課題進捗確認機能」、[教師進捗状態ルール](../state-rules/teacher/progress-state-rules.md)。

**作業範囲:** 担当クラス別一覧、未着手/編集中/提出済み/評価待ち/完了/要対応判定、難易度、取り組み時間、最終更新、研究同意状態、filter/sort、20秒自動更新、フィルター反映CSVを実装する。公開・参加・提出・評価の状態を混同しない。画面構成と操作は既存プロトタイプを基準とする。2026-10-08のユーザー指示で、最終保存下書き・最新提出版の表示・ダウンロードと詳細モーダルのプロトタイプ整合を追加作業に含める。

**前提:** 工程10の公開/割当、工程12の生徒/クラス、工程13の演習配信状態。

**完了条件:** 集計がDB状態と一致し、担当外の生徒・課題を含まないこと。実データ行・詳細履歴・CSV内容は専用DBとブラウザーで確認済み。統合ブラウザーが実ファイルのdownloadイベントを通知しなかったため、実ファイル保存イベントのみ未確認として計画に記録した。

---

## 15. 授業演習コード確認を実装する

**参照資料:** [機能仕様](../function-specification.md)「授業演習コード確認機能」、[授業演習計画](./feature-plans/student-exercise.md)。

**作業範囲:** 担当学校/クラスの生徒一覧、最終更新日時・ファイル数、フォルダ/ファイルツリー、最終保存コードを参照専用で表示する。30秒スナップショット履歴は対象外。研究同意状態/filterも反映する。

**完了条件:** 表示内容が最新保存データと一致し、担当外情報が漏れず、画面操作で生徒データを更新しないこと。

**現在の状態:** 実装・受入済み。正確な検証コマンドと限界は[最終受入](./feature-plans/teacher-exercise-review.md#最終受入2026-10-08)を参照する。工程16〜18の参照機能も実装・検証済みのため、次は工程20の機能横断検証。

---

## 16. 提出課題確認を実装する

**参照資料:** [機能仕様](../function-specification.md)「提出課題確認機能」、提出/評価DB設計、[フィードバックUIガイド](../feedback-guideline.md)。

**作業範囲:** 学校/クラス/課題/難易度/同意状態filter、提出snapshotと入出力チェック結果、詳細モーダル、filterを保持する前後移動を実装する。コード編集/実行は作業用複製だけで行い、元の提出データを変更しない。必要な操作監査を保存する。

**完了条件:** 提出時snapshotとチェック結果が一貫し、担当外データが漏れず、作業用コードが提出へ保存されないこと。

**第155版再現受入:** ユーザーの明示指示により、プロトタイプの3カード・6filter・9列/8sort・全画面詳細/左右editor・単体/一括ZIP/CSVを補完し、実DB/APIへ接続した。[最終受入](./feature-plans/teacher-submission-evaluation-review.md#提出画面のプロトタイプ再現最終受入2026-10-08第155版)を参照。権限・過去割当・提出版・CSV最新同意/監査・一時実行不変性は維持。評価画面の見た目は変更していない。

---

## 17. 評価確認を実装する

**参照資料:** [機能仕様](../function-specification.md)「評価確認機能」、[教師評価状態ルール](../state-rules/teacher/evaluation-state-rules.md)、工程8の評価/コードログ計画。

**作業範囲:** 既存評価一覧、filter/sort、観点別点数/理由、評価時点のprompt/rubric版、コードログ時系列、CSVを実装する。評価履歴を上書きしない。Gemini実API、新規AI評価/再評価の運用確認は工程19まで延期する。

**完了条件:** 評価・版・提出・ログがDB上で一致し、所属/同意境界と履歴保持を確認すること。

**第156版再現受入:** ユーザーの明示指示により評価専用JSP/CSS/JSを分離し、プロトタイプの一覧/独立サマリー/全画面詳細/ログ操作を実DBへ接続した。[最終受入](./feature-plans/teacher-submission-evaluation-review.md#評価画面のプロトタイプ再現最終受入2026-10-08第156版)に差分・必要な例外・検証コマンドを記録。ダミー点/理由/未評価数は使わず、固定版・全履歴・現在権限・最新同意/監査・読取専用を維持。提出画面とプロトタイプは変更していない。次は工程20の機能横断検証。

---

## 18. アンケート結果確認を実装する

**参照資料:** [機能仕様](../function-specification.md)「アンケート結果確認機能」、[評価・アンケート実装計画](./feature-plans/student-evaluation-survey.md)、[アンケートCSV形式](../format/survey-format.csv)。

**作業範囲:** 回答一覧、クラスfilter、同意/回答状態、課題/難易度/日時、回答詳細、仕様に沿うCSVを実装する。全体アンケートは対象外のまま維持する。

**完了条件:** 回答・同意・所属境界がDBと一致し、CSV列/文字コード/個人情報の扱いを確認すること。

### 工程18の実装・受入タスク（第154版）

| タスク | 要件 | 対象・完了条件 |
|---|---|---|
| T-SVR-001 | REQ-SVR-001 認可と同意 | 現在教師/機能/割当学校・最新同意をDAOで検証。担当外/撤回後詳細404、機能失効403。履歴の所属と回答不変 |
| T-SVR-002 | REQ-SVR-002 保存回答と評価 | DTO/DAOで回答・設問/選択肢・固定evaluationの2次元点数を取得。標準6項目と追加設問を区別、下書き/提出/0件を再現 |
| T-SVR-003 | REQ-SVR-003 出力 | filter/全列sort/6条件式をサーバーでも検証。内部user IDのCSV/記述出力、UTF8/BOM/CRLF/数式対策、監査失敗503 |
| T-SVR-004 | REQ-SVR-004 プロトタイプ画面 | 3集計カード、サマリー/棒グラフ、学校/クラス/課題同期、検索/全filter/ソート、更新、詳細/理由/システム評価を再現。共通template/feedback、安全DOM、通常/狭幅 |
| T-SVR-005 | REQ-SVR-005 実環境受入 | 対象unit→隔離MySQL/認証HTTP→認証ブラウザー→全体/WAR→8080接続・最終記録と清掃 |

**API/実装方針:** GET `/teacher/surveys`（JSP）、`view=list|detail|csv|text`。detailは正の`responseId`、filterは`schoolId/classroomId/taskId/difficulty/completion/consent/search`、標準6項目の条件式と`sort/direction`。不正/重複query400、担当外404、DB/監査503、GETは回答・評価・コードログを変更しない。Servlet → Control → DAO → DBを使用し、migrationと固定回答seedは製品へ追加しない。プロトタイプ自体は変更しない。研究用CSV/TXTはログインIDを含めない。

**状態:** T-SVR-001〜005実装・受入済み。関連正本は[機能仕様第154版](../function-specification.md#アンケート結果確認機能優先度低)、[状態ルール](../state-rules/student/survey-state-rules.md)、[合意済み識別子](./feature-plans/survey-implementation-open-questions.md)、[DB定義](../database-design/table-definitions.md)、[プロトタイプ](../../screen-flow-diagram/webapp/WEB-INF/teacher/survey/survey.html)。教師向けアンケート/設問登録UIは追加していない。

### 工程18最終受入（2026-10-08）

- 本実装は[`TeacherSurveyServlet`](../../src/main/java/servlet/teacher/TeacherSurveyServlet.java) → [`TeacherSurveyControl`](../../src/main/java/control/teacher/TeacherSurveyControl.java) → [`TeacherSurveyDao`](../../src/main/java/dao/TeacherSurveyDao.java) → 既存MySQL。migration/製品の回答seedは追加していない。教師メニューを`/teacher/surveys`へ接続し、28 Servletの正規URL契約も更新した。
- 現在の教師/機能/学校権限、回答→評価→提出→割当学校・課題・本人の一致、最新同意を毎回検証。同意撤回後は全参照/出力から除外、再同意後は保持済み回答が再び対象。転籍・閉じたsurvey・終了済み割当の履歴を保持。読む/更新する操作で回答/評価/提出/コードログは不変。
- 標準6数値は明示question_codeを使用し、未知設問を推測配置しない。任意のtext/multiple_choice・inactive選択肢のラベル・理由を詳細/追加CSV列へ保持。欠損値は「-」、下書きは開始日時、提出済みは提出日時。研究出力の内部user IDは仮名化であり完全匿名ではない。自由記述の個人情報は利用者の点検が必要。
- `sh scripts/testing/teacher-survey-review-local.sh unit` → exit0、389件中257成功/132明示ゲートskip/失敗・error0、WAR成功。`sh scripts/testing/teacher-survey-review-local.sh integration` → exit0、空DB Flyway適用・対象7件中6成功/1 runtimeゲートskip、別プロセスの認証HTTP1件成功。実DB/HTTP成功と通常実行のskipを区別する。fixtureは合成データのみ、既存の安全guard/owner label/tmpfs/cleanupを再利用。
- 認証HTTPで全14列sortと一覧/CSV順序一致、6条件式、正規JSP/JSON/CSV/TXT 200、未認証302・誤ロール/機能失効403・担当外404・不正/重複/overflow400・POST405・監査失敗503、撤回/再同意を確認。DBでは閉じたsurvey/非在籍/終了割当、異なる本人/評価/課題の除外、現在の教師/学校/機能失効と参照不変性を検証した。
- 統合ブラウザーでプロトタイプと同じ1440pxのhero/summary列幅、3カード、50%完了率・欠損除外平均3.0、学校/クラス同期、検索→通常詳細click、条件エラー通知/出力無効、下書き/同意filter、キーボードsort、4回連続詳細開閉とfocus復帰、追加回答のHTML不活性・選択肢ラベル、更新不変、CSV/TXTのHTTP200/Blob開始を確認。詳細の通常/375px、一覧の1440/1024/768/375pxでページ全体の横溢れなし、表だけ局所スクロール。共通template/feedbackを使い、共通CSSとプロトタイプは変更していない。
- 8080の当該appだけ再起動し、新Servlet class・正規JS/CSS配信とsource hash一致、host別teacher/student login各200、未認証survey302を確認。認証後業務受入は専用18091で実施し、8080の既存教師の資格情報変更/再seedはしていない。検証用container/networkを全て清掃し、共有app/DB/runner/他プロジェクトDBは維持。commit・本番deploy・外部AI呼出はなし。
- 未確認はOS保存完了/保存ダイアログ・全OS/ブラウザー組合せ・本番負荷/TLS・利用者による実運用受入。統合ブラウザーにはCLI E2E suiteのexit codeはない。エディターの既存Gson解決診断とGradle deprecation警告を実ビルド成功と区別し、詳細は[エラーレポート](./error-report.md)へ記録。

---

## 19. AI連携の最終実装・受入を行う

**現在の状態:** 2026-10-08、本人承認のGemini 3.1 Pro Previewを新規既定として、thinking_level:high・store:false・出力上限4096tokens、1要求180秒・1工程12分・最大3試行、中断復旧15分へ整合した。Lite既定採用は品質優先の指示で撤回し、3.7/3.8は明示選択、既存版/評価履歴のモデルは不変。専用合成DBで教師の揺らぎ→評価例→preview→明示確定/評価保存/再読込が2run成功し、通常評価も実API200→DB成功。認証browserで既定モデル・Preview表示・生成/保存/再表示・未認証/CSRF拒否・旧適用版/完了job保持を確認した。教師回帰は34成功/4診断gate skip、最終評価回帰は35成功/12外部gate skip（各exit0、失敗0）。WAR生成と8080の当該appだけの再起動が成功し、教師/生徒ログイン各200。課題10/11の主要metadata hashは不変。今回の代替調査・受入は生成POST25（200:19/503:5/404:1/timeout:0）、直前の原因調査を含む累計42（200:22/503:16/404:1/timeout:3）、metadata/model-list GET計2は別集計。過去のprovider失敗は履歴として残す。採点品質の人手校正、契約quota/費用上限・実データ送信許可、大人数同時実行とpreview30分TTLの負荷検証は未完了。統合browserにはCLI suiteのexit codeがなく、CI E2Eの合格とは扱わない。詳細は[教師プロンプト計画](./feature-plans/teacher-prompt-ai.md#pro高思考の最終受入2026-10-08)を参照する。

**参照資料:** [教師プロンプト設計・AI生成計画](./feature-plans/teacher-prompt-ai.md)「AI稼働確認の現状と延期作業」、[工程8評価計画](./feature-plans/student-evaluation-survey.md)、[AI連携設計](../ai-api-integration-design.md)。

**再開ゲート:** ユーザーの再開指示後、provider設定/資格情報の安全な注入/model/費用上限を確認する。合成課題・提出・コードログのみで疎通と縦断受入を行い、timeout/rate limit/不正応答/部分失敗/再試行/二重確定/旧評価保持を確認する。実提出・実生徒データの送信は合成受入と分け、データ送信・同意・費用への別途明示承認なしに実施しない。

**完了条件:** AI要求から評価保存/再読込までを確認し、失敗を成功扱いせず、実行条件・費用・未確認範囲を記録すること。

---

## 20. 機能横断のローカル検証を行う

**前提:** 工程10〜19が完了。AI試験は承認範囲と隔離環境を遵守する。

**2026-10-16 最終判定: PASS / 完了。** [P20-01〜10の最終対応表](./feature-plans/phase-20-cross-feature-validation.md)に、全29 Servlet/71 routeの共通認証契約と既存の業務DB/HTTP/Chromium受入を対応付けた。最終root clean buildは147 suites / 561 tests（373 passed、188 skipped、failure/error 0）、exit 0、WAR成功。最後のUI変更後の8 browserケースも成功済み。188 skipは171件の非skip成功証跡、1件の不採用候補診断FAIL、16件の任意API/保持fixture補助に分類し、成功数へ算入しない。追加承認のSNS実配送1/失敗0と固定backupの隔離業務復旧・V34互換更新・実Control/DAO読戻しを受入し、専用資源清掃と共有不変/HTTP200まで確認した。これは全入力組合せ/OS/本番完全DR/正式採点品質の保証ではない。2.5 Pro診断FAILと運用上の別ゲートは[課題台帳](./error-report.md#現在の未解決未確認事項2026-10-16-更新)に保持し、成功したことにしない。下記の10月15日以前の部分判定は履歴。exact command/終了code/skip/失敗経緯は[最終runtime受入](./feature-plans/runtime-validation-report.md#2026-10-16-工程20の最終受入)、運用対象は[復元・通知記録](./production-deployment.md#2026-10-16-追加承認による復元通知試験)を参照する。

**確認手順:** 空DB migration/seedから教師・管理者・生徒の主要導線、認可境界、状態遷移、DB再読込、重複送信、異常系、CSV、狭幅UIを通して確認する。AI確認はmock/合成/実APIのどの層かを明示し、未確認を合格に含めない。実コマンド、結果、browser証跡、残課題を記録する。

**2026-10-15時点の状態（履歴）:** 部分実施。直近全体clean buildはGradle 8.10.2/JDK 21でexit 0、JUnit 144 suites / 552 tests（369 passed、0 failures、0 errors、183 skipped）、WAR成功。複数領域の専用MySQL統合試験に加え、Node.js v24.21.0/Playwright 1.64.0で認証/管理者境界、30秒自動保存後の再読込、二つのbrowser context間のstale保存拒否、N166 deny通知からhomeへの遷移/直接editor拒否、教師の進捗/提出/評価/アンケート画面・出力、390px表示、教師の即時公開から対象生徒のeditor到達、予約公開workerから実公開・通知・対象editor・既読化、P20-03教師権限/所有範囲のDB境界と認証後の管理者URL拒否、現行アンケート設定の合成生徒browser保存/読戻し、教師課題一覧/ヒントプレビューを確認した（Playwright 2 tests passed）。続けてP20-09では管理者による古い教師更新versionの停止要求がHTTP 400で拒否され、アカウント状態/versionを維持して失敗監査に記録されることを確認（Playwright 1 test passed、初回のlocator assertion失敗は修正）。P20-03では認証済み生徒sessionによる教師workspaceおよび管理者専用routeの直接GETがHTTP 403となることも確認（フォルダ行UIと合わせPlaywright 2 tests passed）。P20-04では学習開始済み生徒がいる公開課題の同一系列改訂拒否を専用MySQLで再検証（1 test passed、0 skipped/failures/errors）。2026-10-15にURL/portal HTTP runtime 5/5成功、TeacherTask MySQL suite 45件中35成功・10 skip・失敗0、承認済み合成Gemini API診断/評価DB smokeも実行した。運用候補モデルsuiteは4件中3成功/1失敗（`gemini-2.5-pro` structured generation HTTP 404）。既定モデル/アプリ設定は変更していない。今回を含む専用Compose/DB/schema/volume/networkは清掃し、binary-log trust設定は`OFF`を確認済み。全主要導線/権限境界のブラウザー網羅、候補モデルHTTP 404の扱い、他ブラウザー/OS等は未確認のため完了扱いしない。判定は`PARTIAL / NEEDS_SIGNOFF`。詳細なコマンド・pass/skip数・範囲は[工程20横断テスト計画](./feature-plans/phase-20-cross-feature-validation.md)と[Runtime Validation Report](./feature-plans/runtime-validation-report.md)に記録。

- **2026-10-14 18:33 JST追補:** P20-03/P20-09で、同一校かつ学校アクセス有効でも`task-management`未付与の教師が課題編集の直接URLでHTTP 403となることを、空DBからのV1–V34 migration・認証browser・DB read-backで確認。許可peerの編集/owner再読込と他校拒否も同じPlaywright testで再確認した。専用環境は清掃済み。工程20の他feature route、対象opt-in試験、全ブラウザー/OSなどは引き続き未確認で、全体判定は`PARTIAL / NEEDS_SIGNOFF`。[詳細な証跡](./feature-plans/phase-20-cross-feature-validation.md)を参照。
- **2026-10-15 P20-09追補:** 隔離管理者ブラウザー受入で、古いversionを付けた教師停止要求がHTTP 400となり、再読込後も「利用中」かつversion不変、正しい「停止」失敗監査が記録されることを確認。最新versionによる停止操作は続けて成功した。Playwright 1 passed、runner exit 0。初回は監査行locatorが「停止解除」も拾ったテスト側の誤りを修正し、再実行成功。専用環境は清掃済み。全体は`PARTIAL / NEEDS_SIGNOFF`を維持。[詳細な証跡](./feature-plans/phase-20-cross-feature-validation.md)を参照。
- **2026-10-15 P20-03追補:** 合成生徒の誤認証拒否・正常ログインを確認後、同一session IDで教師portal hostの`/teacher/task`および`/admin/teachers`を要求し、両方HTTP 403を確認。portal host間redirectではなく認証filterのrole boundaryを検証した。既存の狭幅フォルダ行受入と同じ隔離runner内でPlaywright 2 passed、exit 0。専用環境は清掃済み。全体は`PARTIAL / NEEDS_SIGNOFF`を維持。[詳細な証跡](./feature-plans/phase-20-cross-feature-validation.md)を参照。
- **2026-10-15 P20-03代表ロール境界追補:** 管理者fixtureから作成した合成教師でteacher portalに認証し、`/teacher/task`はHTTP 200、同じJSESSIONIDを`student.localhost`に適用した`GET /student/home`はHTTP 403となることを専用Playwright runnerで確認（JUnit fixture 1/1、Playwright 1/1、exit 0）。続く受入では管理者sessionから`student.localhost`の`/student/home`と、今回追加した`teacher.localhost`の`/teacher/task`がいずれもHTTP 403となり、`/admin/teachers`はHTTP 200のまま教師管理操作が継続できることを確認した（JUnit fixture 1/1、Playwright 1/1、exit 0）。P20-03の生徒→教師/管理者拒否と合わせ、代表routeのロール許可・拒否境界を確認した。専用環境は清掃済み。全route網羅ではなく、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持。[詳細な証跡](./feature-plans/phase-20-cross-feature-validation.md)を参照。
- **2026-10-15 P20-06同時保存競合追補:** `sh scripts/testing/student-editor-concurrency-local.sh`を2回実行し、各runのfixture JUnit 1/1とPlaywright 1/1がexit 0。同じdraft timestampの二つのeditor contextからPlaywright barrierでPOSTを同時解放し、要求処理時間のoverlap、HTTP 200/409の一勝一競合、双方の再読込後に成功側コードだけが残ることを確認した。初回は専用runtimeにPython runner URLがなくGET 500となったが、ログで構成不足を特定し、隔離runner追加後の2回の実行は成功。専用環境/port 18099は清掃済み。P20-06指定ケースは合格したが、工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持。[詳細な証跡](./feature-plans/phase-20-cross-feature-validation.md)を参照。
- **2026-10-15 opt-in検証追補:** `ApplicationUrlRuntimeTest`は隔離Tomcat/MySQLで5/5成功。`TeacherTaskDatabaseTest`は新規隔離schemaで45件中35 passed/10 skipped/0 failed。ユーザー承認に基づき、実生徒情報を含まない合成入力でGemini diagnostics/smokeを実行し、Gradle exit 0。さらに隔離MySQL上の`EvaluationDatabaseTest.savesLiveGeminiResultOnlyWhenExplicitlyEnabled`は1/1成功し、評価workerの完了・検証済み結果のDB保存を確認した。運用候補suiteは`gemini-3.5-flash-lite`/`gemini-3.1-pro-preview`のstructured generationが成功し、`gemini-2.5-pro`はHTTP 404で失敗。Host header制約、Task suiteの未設定runner URL、readiness probeのshell quoting、診断report回収時の停止container対応は修正/記録済み。主要導線全量、全ロール/所有範囲、他ブラウザー/OSは未確認。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。[工程20横断テスト計画](./feature-plans/phase-20-cross-feature-validation.md)と[Runtime Validation Report](./feature-plans/runtime-validation-report.md)を参照。
- **2026-10-15 P20-03進捗所有範囲追補:** `sh scripts/testing/teacher-submission-review-local.sh integration`で専用MySQL/Tomcatの認証HTTP runtime test 1/1成功、Gradle exit 0。他校のみ担当する別教師は`task-progress`権限を持っていても、対象一覧に行がなく、対象生徒の進捗詳細/コードdownload直接GETがHTTP 404となることを確認。許可教師による対象データの一覧・詳細・コード読戻しは200。詳細は[工程20横断テスト計画](./feature-plans/phase-20-cross-feature-validation.md)に記録。初回テストの期待値修正と再検証は[エラーレポート](./error-report.md)参照。別の代表所有範囲は未確認のため工程20は`PARTIAL / NEEDS_SIGNOFF`。
- **2026-10-15 P20-03進捗機能/担当校アクセス取消追補:** 専用認証HTTP試験中、担当教師の`task-progress`権限取消後は進捗画面・詳細・downloadがHTTP 403となり、再有効化後に対象校アクセスを取り消すと画面は空のHTTP 200、詳細/downloadはHTTP 404となることを確認。いずれの拒否応答にも生徒識別子/コードは含まれず、独立した提出確認画面はHTTP 200を維持。`TeacherReviewRuntimeTest` 1/1 passed、skip/failure/error 0、exit 0。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03教師確認機能権限取消追補:** 同じ認証HTTP試験で`evaluation-review`を取り消すと評価確認画面・一覧・詳細・個別file/log・CSV・ZIP/logs-ZIP、`submission-review`を取り消すと提出確認画面・一覧・詳細・file・CSV・ZIP・preview POSTがHTTP 403となり、拒否応答に生徒識別子/提出コードを含まないことを確認。previewによる`code_logs`/`code_executions`の増加もなし。`TeacherReviewRuntimeTest` 1/1 passed、skip/failure/error 0、exit 0。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03アンケート結果確認権限取消追補:** `sh scripts/testing/teacher-survey-review-local.sh integration`で専用MySQL/Tomcatの認証HTTP test 1/1成功。教師の`survey-results`権限を同一session中に取り消し、画面・一覧・回答詳細・CSV/TXTをHTTP 403で遮断。拒否応答に合成回答ID/生徒IDを含まず、独立権限の提出確認画面はHTTP 200。exit 0、0 skip/failure/error。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03授業演習コード確認/学校スコープ追補:** `sh scripts/testing/teacher-exercise-review-local.sh integration`で専用MySQL/Tomcatの`TeacherExerciseRuntimeTest` 1/1 passed、0 skip/failure/error、exit 0。認証済みsession内で`exercise-code-review`を取り消した後、演習コード画面・一覧・詳細・個別download・ZIP・bulk・CSVの7 routeがHTTP 403となり、生徒ID/保存コードを返さず、export監査記録も増えないことを確認。独立して許可された提出確認画面はHTTP 200を維持した。さらに同じ機能権限を持つ他校担当教師は一覧が空、対象生徒のdetail・個別download・ZIPの直接GETがHTTP 404となる一方、担当教師は同対象を正常に読めることを確認。隔離資源清掃済み。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。[詳細な証跡](./feature-plans/phase-20-cross-feature-validation.md)を参照。
- **2026-10-15 P20-03アンケート結果の学校スコープ追補:** `sh scripts/testing/teacher-survey-review-local.sh integration`で専用MySQL/Tomcatの認証HTTP試験を実行し、`TeacherSurveyRuntimeTest` 1/1 passed、0 skip/failure/error、exit 0。`survey-results`権限を持つ他校担当教師の一覧が空、対象回答の詳細GETがHTTP 404となり、CSV/TXT exportにも対象回答/record code・生徒login ID・固有回答本文が含まれないことを確認。専用資源は清掃済み。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03評価結果の学校スコープ追補:** `sh scripts/testing/teacher-submission-review-local.sh integration`の専用認証HTTPで`TeacherReviewRuntimeTest` 1/1 passed、0 skip/failure/error、exit 0。他校担当教師の評価一覧は空、対象detail/file/logsはHTTP 404、CSVは対象データなしで成功・0行監査、ZIP/logs-ZIPは対象なしのHTTP 400となることを確認した。初回3件のtest-side assertion/build失敗と修正は[エラーレポート](./error-report.md)に記録。専用資源は清掃済み。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03提出確認の学校スコープ追試:** `sh scripts/testing/teacher-submission-review-local.sh integration` exit 0。`TeacherReviewRuntimeTest` 1/1 passed、0 skip/failure/error（HTTP report timestamp `2026-10-15T04:44:49`）。他校担当教師の提出一覧は空、対象detail/fileはHTTP 404、CSVは生徒ID/提出コードなしでHTTP 200および0行監査、ZIPは対象なしのHTTP 400となることを確認。専用資源はrunner終了時に清掃。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03提出確認の学校スコープbrowser受入:** `e2e/phase20/teacher-submission-ownership.spec.js`を隔離Playwright 1.64.0/Chromiumで実行し、最終1 passed、0 failed、exit 0。ownerで合成提出の存在をpositive-control確認し、別校教師では画面一覧が空、出力ボタンdisabled、detail/file direct URL/fetchは404、CSVは対象データなしの200、ZIPは400となることをbrowserで確認した。owner権限のfixture復帰後listが3件であることもruntime JUnitでassert済み。初回fixture残留・XHR wait失敗と修正を[エラーレポート](./error-report.md)に記録。隔離container/network清掃済み。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03評価確認の学校スコープbrowser受入:** 同じ隔離fixtureで`teacher-submission-ownership.spec.js`を実行し、提出境界と評価境界の2 testsがともにpassed、0 failed、Playwright exit 0。owner教師には合成評価が表示されdetail modalを開ける一方、別校教師には評価一覧/summaryが表示されず、detail/file/logs直接navigation/fetchは404、対象なしCSVは200、ZIP/logs-ZIPは400、出力コントロールはdisabledであることを確認した。`TeacherReviewRuntimeTest` 1/1 passed。詳細と集計は[横断検証計画](./feature-plans/phase-20-cross-feature-validation.md)、[Runtime Validation Report](./feature-plans/runtime-validation-report.md)を参照。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。
- **2026-10-15 P20-03アンケート結果の学校スコープbrowser受入:** `sh scripts/testing/teacher-survey-review-local.sh browser`の専用MySQL/Tomcatで`TeacherSurveyRuntimeTest` 1/1 passed、0 skip/failure/error、exit 0。Playwright 1.64.0/Chromiumは3/3 passed、exit 0。owner教師には合成回答2件と固有回答詳細を表示し、他校教師では一覧/summaryが0、list APIが空、対象detailの直接navigation/fetchが404、CSV/TXTは200でも回答ID/record ID/生徒ID/固有回答なしを確認した。所有境界1件を追加し、累計24論理case、28 successful executions、22 browser fixture-run invocations、27 fixture batches（中間失敗を含む）。専用資源は清掃済み。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。[詳細](./feature-plans/runtime-validation-report.md)
- **2026-10-16 P20-03授業演習コード確認の学校スコープbrowser受入:** `sh scripts/testing/teacher-exercise-review-local.sh browser`の専用MySQL/Tomcatで`TeacherExerciseRuntimeTest` 1/1 passed、0 skip/failure/error、exit 0。Playwright 1.64.0/Chromiumは1/1 passed、exit 0。owner教師の合成演習詳細/コード表示、他校教師の一覧0・詳細/download/ZIP 404・CSV空200・bulk空400・出力ボタンdisabledを確認した。初回compile/assertion失敗は[エラーレポート](./error-report.md)に記録。累計25論理case、29 successful executions、23 browser fixture-run invocations、30 fixture batches（中間失敗を含む）。専用資源清掃済み。工程20全体は`PARTIAL / NEEDS_SIGNOFF`を維持する。[詳細](./feature-plans/runtime-validation-report.md)
**段階2完了条件:** [詳細手順の完了条件](./implementation-and-local-development.md)を満たし、ローカル構築と主要機能/異常系を別の開発者が再現できること。

## 次に別手順書へ分ける領域

このロードマップは段階2の実装順を示すものです。AWS のアカウント設定、EC2 / ネットワーク構築、DNS、Nginx、TLS、バックアップと復旧の具体的なコマンドは含めません。それらは[開発からデプロイまでの全体手順](./development-and-deployment-flow.md)の段階4以降として、AWS デプロイ手順書を別途作成します。

## 通知機能・ヘッダーの整理（2026-10-04）

ユーザー指示により、教師向け機能完成後まで保留していた棚卸しを前倒しで実施した。通知一覧を作らず準備中表示のままとする方針も確認済み。以下に照合結果と残る作業を示す。

### 通知機能の棚卸し

- **生徒本実装**: 共通ヘッダーのベルは「通知（準備中）」の非操作表示で、件数・通知一覧・既読状態はない。
- **生徒プロトタイプ**: 監査時点では未読件数「2」と「未提出」「再評価」の固定サンプルを表示し、固定リンクを持っていた。ユーザー決定に合わせ、準備中の非操作表示へ変更し、固定通知用CSSも除去した。
- **教師プロトタイプ**: 共通ヘッダーに通知UIはなく、教師IDの`localStorage` fallbackと固定学校名を表示する。実データとの連動ではない。
- **仕様**: 一時的な共通feedback（保存完了・エラー等）は通知一覧とは別機能。第165版で生徒通知の初回6種類と段階導入を承認し、本書冒頭に実装順を追加した。基盤仕様合意・受入までは準備中表示を維持する。教師の永続通知一覧と教師プロンプト変更の再評価完了通知は対象外。
- **通知の今後**: 第165版N165-00でロール/所属別イベント、DB正本、未読/既読、保持期間、遷移先、更新タイミングを合意する。現在の固定サンプルは実通知として扱わない。

### ヘッダーの差分・課題

- **生徒本実装**: ホーム、授業演習、課題、研究同意、ルーブリック、ヘルプ、準備中の通知、ユーザーメニューを表示する。課題メニュー内の個別操作は未実装案内。課題件数バッジは通知件数ではなく公開課題数で、Home/Exercise/Editor/Evaluation/Survey各Servletが設定し、Account/Consent/Rubric等の共通ナビゲーションでは未設定のため画面により表示が変わる。ユーザーメニューの生徒ID・DB由来のクラス/学校表示は今回追加済み。
- **生徒プロトタイプ**: 課題・同意・ルーブリック・ヘルプ・ユーザー情報を表示する。通知は本実装に合わせて準備中表示へ変更した。固定ID/クラス/学校等のサンプル情報は実DB状態ではないため、動作仕様の正本にしない。
- **教師本実装**: 教師向け業務共通ヘッダー・ナビゲーションは未実装。現在の教師ホームは認証確認用ページで、管理者ホームとも共用される。
- **教師プロトタイプ**: サイドナビ、開閉ヘッダー、教師ID/学校、ログアウトを表示する。教師IDは`localStorage`の`t001` fallback、学校名は固定表示で、ページ群のリンク先も機能プロトタイプである。教師向け通知UIはない。画面遷移図の「アカウント情報管理」とプロトタイプの「生徒アカウント管理」で入口名も一致していないため、教師業務機能の導線確定時に用語と初期画面を統一する。
- **次の整理/実装順**: 教師向け機能（工程10・11）の画面と権限が固まった後、ロールごとのナビゲーション項目・利用可能範囲・現在位置/戻り導線・アカウント情報の表示元を確定する。通知契約をそのナビゲーション設計に組み込み、320px以上の狭幅、折りたたみ、ドロップダウンの画面内収まり、キーボード操作と共通フィードバックを一括検証する。教師ヘッダーはlocalStorageや固定データを本実装へ持ち込まず、認証済み利用者/DBから表示する。

通知一覧を作らない方針を機能仕様・画面遷移図・プロトタイプへ反映した。2026-10-05のユーザー確認で再評価完了時に生徒へ永続通知しないことを定め、機能仕様・教師プロンプト計画・状態ルールへ反映した。教師にはjob完了を表示し、生徒は次回評価画面を開いたとき最新評価を確認する。教師業務ヘッダーは教師機能の画面/権限が整う前に先行構築しない。
