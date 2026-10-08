# 教師の提出・自動評価確認 実装計画

## 改版履歴
| 日付 | 内容 |
|---|---|
| 2026-10-08 | 工程16/17の承認済み参照機能を実装する計画。AWS操作、既存開発DB、実Gemini評価、手動採点・再評価は対象外 |
| 2026-10-08 | 本実装、専用MySQL読み戻し、認証HTTP/runner、代表ブラウザー操作を検証。残る本番TLS・実AI縦断・利用者手動受入を区別 |
| 2026-10-08 | 明示されたプロトタイプ再現に従い提出画面だけを補完。集計/9列ソート/全画面詳細/左右CodeMirror/単体・ZIP・CSVを追加し、評価画面と参照不変性を維持 |

## 目的と参照資料
- [機能仕様](../../function-specification.md)の「提出課題確認機能」「評価確認機能」を本実装へ接続する。仕様本文の変更・新規採点操作は行わない。
- [実装契約](../implementation-contract.md) IC-003/004/008、過去履歴の閲覧条件、最新同意、CSV監査を正とする。
- [教師評価](../../state-rules/teacher/evaluation-state-rules.md)、[教師進捗](../../state-rules/teacher/progress-state-rules.md)、[提出](../../state-rules/student/editor-state-rules.md)、[DB定義](../../database-design/table-definitions.md)、[feedback](../../feedback-guideline.md)を参照。
- 汎用教師評価状態の「編集・確定」操作は今回の自動評価の参照機能に追加しない。

## データ・URL契約
| 操作 | URL/入力 | 権限・保存 |
|---|---|---|
| 提出一覧/詳細 | GET `/teacher/submissions`、`view=detail&submissionId` | `submission-review`。教師本人と現在の学校権限をDB検証 |
| 一時実行 | POST 同URL、`action=preview`、`submissionId`、`code`、`standardInput`、`csrfToken` | 実行前後に同じ対象の権限を再検証。認証済みPythonRunnerClientと既存入力上限を使用。提出・下書き・評価・実行ログに保存しない |
| 評価一覧/詳細 | GET `/teacher/evaluations`、`view=detail&submissionId`、任意`evaluationId` | `evaluation-review`。提出に属する自動評価履歴だけ。最新履歴を初期表示し、過去評価を選択可能 |
| 評価CSV | GET 同URL、`view=csv`、一覧と同じフィルター/ソート | 最新同意が`agreed`の行のみ。教師向け一覧と同じログインIDを含むため研究用匿名化出力ではない。監査成功保存後に応答。秘密・コード・理由本文は監査へ含めない |
| 提出CSV | GET `/teacher/submissions?view=csv` | 一覧の絞込/全列sortと同じ、最新同意agreedのみ、commit済み監査必須 |
| 提出単体/一括ZIP | GET 同URL、`view=file&submissionId` / `view=zip`＋一覧条件 | 現在のsubmission-review/学校権限、保存済み提出コードのみ。提出ID/版を含む安全なファイル名。ZIPは32 MiB上限・空対象拒否 |

### プロトタイプ差分補完の作業単位

1. 集計3カード、6即時フィルター・学校/クラス同期、9列/全見出しsort、更新/CSV/ZIP toolbarを復元する。
2. 全画面詳細、I/O表の○/×、左右CodeMirror、単体取得と中央ページ数/前後操作を接続する。保存状態や模擬実行は移植せず実runnerと一時コピーを維持する。
3. CSV/ZIP/単体をControl/DAO認可へ接続し、フィルター/出力の順序一致、権限失効/不同意CSV除外、重複版、業務記録不変性をテストする。
4. 専用合成DB/認証HTTP、通常/狭幅ブラウザー、評価画面非退行を確認し、8080へ反映する。新しいmigration・製品seed・プロトタイプ編集はしない。

学校/クラス/課題ID、難易度、同意、評価段階、検索、ソートをサーバーでも検証する。詳細はクライアントの生徒IDを受けず、提出→参加→割当→クラス→学校から範囲を導出する。学校単位が権限境界、クラスはフィルター。非在籍、停止/削除生徒、非アクティブクラス、旧改訂/削除課題、終了割当も保持履歴として参照する。同意だけで授業上の閲覧を遮断しない。

## 実装タスク・完了条件
### 評価画面の第156版差分補完

- T-EVF-001: 3カード・独立サマリー/分布・8filter・10列/9sortを実データから復元。条件式は既存[アンケートfilter](../../../src/main/java/entity/TeacherSurveyFilter.java)の検証/比較を再利用する。
- T-EVF-002: 全画面詳細、2観点カード・項目・理由・ログ閲覧/前後・履歴/前回リンクを復元。固定ダミー点/理由/未評価数は移植しない。
- T-EVF-003: 評価/ログJSON・一括ZIPを現在認可へ接続し、CSV条件/順序/監査と保存不変性を維持する。
- T-EVF-004: clean回帰、専用合成DB/認証HTTP、通常/狭幅ブラウザー、提出画面非退行、8080反映・所有環境清掃。

上記は機能仕様第156版「プロトタイプ再現・取得」に対応する。対象は評価専用資産と既存Review層の関連箇所。DB/schema・外部AI・本番deployは変更しない。

- [x] T001 DAO/DTO: 提出全版、不変I/Oスナップショット、評価全版・固定rubric/prompt・コードログ
- [x] T002 Control: 現在の教師/機能/学校境界、フィルター/ソート、CSV監査、一時実行不変性
- [x] T003 Servlet/JSP/JS: 共通テンプレート/feedback、一覧、提出modal/前後、評価詳細/理由/時間選択、メニュー
- [x] T004 最小unit/contract、専用dummy DB readback/権限拒否、認証HTTP/ブラウザー（下記の範囲）

DBマイグレーション・汎用demo seedは不要。専用fixtureは`ppe_teacher_review_test_*` DBと明示的なフラグ/ホストを検証し、それ以外では書き込まない。既存DemoDataBootstrap/LearningFlowSupportのガードは変更しない。実施結果・未確認を追記し、未実行を成功扱いしない。

## 実装ファイル

新規:

- `src/main/java/entity/TeacherReviewRow.java`
- `src/main/java/entity/TeacherReviewFilter.java`
- `src/main/java/entity/TeacherReviewDetail.java`
- `src/main/java/dao/TeacherReviewDao.java`
- `src/main/java/control/teacher/TeacherReviewControl.java`
- `src/main/java/servlet/teacher/TeacherReviewServlet.java`
- `src/main/webapp/WEB-INF/teacher/review/review.jsp`
- `src/main/webapp/css/teacher/review/review.css`
- `src/main/webapp/js/teacher/review/review.js`
- `src/test/java/control/teacher/TeacherReviewControlTest.java`
- `src/test/java/control/teacher/TeacherReviewFixture.java`
- `src/test/java/control/teacher/TeacherReviewDatabaseTest.java`
- `src/test/java/control/student/PythonRunnerPreviewTest.java`
- `src/test/java/servlet/teacher/TeacherReviewServletTest.java`
- `src/test/java/servlet/teacher/TeacherReviewRuntimeTest.java`
- `scripts/testing/teacher-review-local.sh`
- 本計画

既存ファイルの関連箇所のみ変更:

- `src/main/java/dao/TeacherPermissionDao.java`: 既存の教師/機能権限判定へ確認機能を追加。
- `src/main/java/dao/StudentEvaluationDao.java`: 評価結果マッピングだけを同じDAOパッケージから再利用可能に変更。生徒本人の所有範囲SQLは変更しない。
- `src/main/java/control/student/PythonRunnerClient.java`: 標準入力付き`executePreview`を追加。既存の認証・制約と、入力なし呼出しの互換性を維持。
- `src/main/webapp/WEB-INF/teacher/shared/navigation.jspf`: 権限付きの提出/評価リンク。
- `src/main/webapp/WEB-INF/teacher/home.jsp`: 確認機能だけを持つ教師にも正しい利用可能案内。
- `src/test/java/servlet/auth/ApplicationUrlContractTest.java`: `/health`を含む既存25 Servletに1 Servlet（2 URL）を追加した26件のinventory。
- [実装ロードマップ](../implementation-roadmap.md): 工程16/17の実装・検証状態。

機能仕様の本文、プロトタイプ、DB設計/マイグレーション、親作業の本番Compose・Nginx・broker・運用スクリプトは本機能では変更しない。

## 入力・表示・出力の補足

- `view=list`はJSON一覧、`view=detail`はJSON詳細、`view`なしは共通JSP。評価詳細のページURLは`/teacher/evaluations?submissionId=...&evaluationId=...`。
- 一覧の入力は任意の正の`schoolId / classroomId / taskId`、`difficulty=beginner|intermediate|advanced|none`、`consent=agreed|not_agreed|unconfirmed`、`level=1..5`、100文字以内の`search`、`sort=student|school|class|task|difficulty|match|submitted|consent|level|evaluated`、`direction=asc|desc`。提出画面は先頭8観点、評価画面の既存sortも維持する。`not_agreed`は最新同意の不同意/撤回に対応し、未確認とは区別する。
- 提出一覧は提出全版、評価一覧は各提出の自動評価全履歴（失敗/再試行を含む）と未評価提出。手動採点履歴・操作は対象外。詳細の初期値は作成日時/ID順の最新評価。評価中は同じ参加の前回完了結果へのリンクも表示する。
- 一時実行はコード64 KiB・標準入力8 KiB・フォーム本文256 KiB以下。既存runnerの時間/出力上限を維持する。実行中はmodalの移動/閉じる操作を抑止し、元の提出スナップショットと作業用コピーを分離する。
- コードログは提出に明示関連付けされた行と、直前提出から選択提出までの未関連付けログを読む。時点選択で保存済みスナップショットを切り替える。
- CSVはBOM付きUTF-8、既存`CsvCells`による引用/数式対策を使用する。生徒ログインID・学校/クラス/課題・提出版・評価履歴/結果・同意/履歴区分を含む。コード・理由本文・パスワード・研究用対応IDを含めない。`evaluation-review / csv_export / success`と件数を同一トランザクションで監査保存し、コミットできない場合は出力しない。

## 実施した検証（2026-10-08）

| コマンド/方法 | 結果・確認範囲 |
|---|---|
| `sh -n scripts/testing/teacher-review-local.sh` | 成功 |
| `sh scripts/testing/teacher-review-local.sh unit` | Java21/offline Gradleでコンパイル・WAR成功。22テストのうち21成功・専用DB読戻し1件は明示skip（この実行をDB検証成功とは扱わない） |
| `sh scripts/testing/teacher-review-local.sh database final_20261008` | **22成功・失敗0・skip0**、WAR生成。新規専用DBに変更なしのV1–V23を適用し、fixture→DB再読込、提出/I/O、次元/観点/理由、2時点ログ、旧/削除履歴、固定版、失敗/再試行、最新撤回のCSV除外・監査、生徒所有境界、学校/機能/ロール拒否、実行前後の権限取り消しと不変性を検証 |
| `sh scripts/testing/teacher-review-local.sh runtime final_20261008` | **1成功・失敗0・skip0**。独立Tomcatへの実ログインHTTP、JSP、一覧/詳細/CSV、監査件数、CSRF/サイズ拒否、学校/機能/生徒/管理者拒否、認証付き実Python preview、元コード/実行ログ不変を検証。実Gemini要求なし |
| 専用アプリの認証ブラウザー | メニュー、3件の提出一覧/ID昇順、○/×の入力/期待/実際出力、作業用コピー変更と標準入力付き実runner結果、実行中の移動/閉じる抑止、前後切替/先頭・末尾の移動不可/コピー・結果リセット、容量違反のshared feedback、評価一覧・同意絞込・CSV本文、2次元/観点別理由、固定版、評価中の前回完了結果/失敗履歴切替、コード時点切替、理由中の`<script>`がテキストのままであることを確認 |
| レイアウト | 共通教師テンプレートで通常幅の表示を確認。1280px/390pxのDOM測定で詳細画面のページ全体の横溢れなし。390pxの一覧はページ幅375px・表領域327px/内容1000pxで、表だけが`overflow-x:auto`となることを確認。ブラウザーツールの画像は元の表示幅を保つため、実端末の狭幅目視は未確認 |

Java21の`ppe-tools:local`にソースを読み取り専用bindし、使い捨てbuilder内へ内容をコピーして`--offline --no-daemon --rerun-tasks`で実行した。unit/DB実行は上記7クラスを`--tests`で選択、runtime実行は`servlet.teacher.TeacherReviewRuntimeTest`だけを選択する。結果XMLは`build/teacher-review-validation/{unit,database,runtime}`、WARは`build/teacher-review-validation/teacher-review.war`（いずれもローカル検証用、git対象外）。

初期の限定権限Flyway適用はV11のtrigger作成権限で失敗したため、グローバルDB設定/SUPER権限を変更せず、ローカル検証DBだけに既存のファイル認証でSQLを適用した。初期fixture/コピー設定の失敗は修正後にfresh DBと`--rerun-tasks`で再検証し、上表は最終成功実行を示す。IDEではGsonの依存解決エラーが表示されたが、Java21コンテナでの実コンパイルとWAR内JSP実行は成功した。

ブラウザーツールの背面ページではアニメーションの安定待機が停止するため、代表ボタンは実DOMのclickイベント、selectは通常のchangeイベントで操作した。これを利用者による通常マウス操作の全面受入とは扱わない。

## 専用fixture/HTTPハーネス

- `database <unique_lowercase_suffix>`: `ppe_teacher_review_test_<suffix>`を新規作成。既存DBの再利用/上書き、親プロジェクト以外のDBホストを拒否する。fixture自身も`TEACHER_REVIEW_DB_TEST=true`、固定ホスト、DB名、実接続catalog、初期users=0を検証する。
- `runtime <existing_fixture_suffix>`: 固定名`ppe-teacher-review-runtime`の起動、明示的な`ppe.teacher-review.test=true`ラベル、同じ専用DB/ホスト設定を確認し、`TEACHER_REVIEW_RUNTIME_TEST=true`・`http://ppe-teacher-review-runtime:8080`だけへHTTPテストする。fixture再投入はしない。
- fixtureログインは`review_teacher / review_outsider / review_denied / review_student / review_withdrawn / admin`、パスワードは全て合成の`ReviewDummy42!`。合成SQLユーザー`ppe_teacher_review_dummy`の権限は専用DBだけ。実アカウント・生徒データ・API鍵は使用しない。
- 既存ローカル専用DBコンテナ`ppe-preparation-20261008-db-1`と、認証付き専用runnerだけを接続先として使用。親の`ppe` DB、開発Compose/DB/volumeには書き込まない。

HTTP検証は親アプリとは別の`ppe-app:local`から作ったTomcatで実行し、`127.0.0.1:18089`だけへ公開した。`PPE_ENV=development`でのコアHTTP検証であり、production HTTPSゲートの検証ではない。再確認用の起動条件:

```sh
docker network create ppe-teacher-review-http
docker run -d --name ppe-teacher-review-runtime --label ppe.teacher-review.test=true \
  --network ppe-teacher-review-http -p 127.0.0.1:18089:8080 \
  --mount "type=bind,src=$(pwd)/build/teacher-review-validation/teacher-review.war,dst=/usr/local/tomcat/webapps/ROOT.war,readonly" \
  --mount "type=bind,src=$(pwd)/deploy/runtime/local/secrets/runner-token,dst=/run/secrets/runner_token,readonly" \
  -e PPE_ENV=development -e 'CATALINA_OPTS=-DPPE_TRUSTED_PROXY_REGEX=127\.0\.0\.1' \
  -e DB_HOST=ppe-preparation-20261008-db-1 -e DB_PORT=3306 \
  -e DB_NAME=ppe_teacher_review_test_final_20261008 \
  -e DB_USER=ppe_teacher_review_dummy -e DB_PASSWORD=DummyReviewDb42! \
  -e STUDENT_PORTAL_HOST=student.localhost -e TEACHER_PORTAL_HOST=teacher.localhost \
  -e PYTHON_RUNNER_URL=http://ppe-preparation-20261008-python-runner-1:8090 \
  -e PYTHON_RUNNER_TOKEN_FILE=/run/secrets/runner_token \
  --entrypoint catalina.sh ppe-app:local run
docker network connect ppe-preparation-20261008_database ppe-teacher-review-runtime
docker network connect ppe-preparation-20261008_execution ppe-teacher-review-runtime
curl --fail --silent --retry 12 --retry-delay 2 --retry-connrefused \
  -H 'Host: teacher.localhost:18089' http://127.0.0.1:18089/teacher/account/login >/dev/null
sh scripts/testing/teacher-review-local.sh runtime final_20261008
```

既存の同名container/networkがある場合は、その由来を確認せず置換しない。HTTP検証用container/networkとbuilderは終了時に除去し、DB/volumeは削除しない。最終fixtureは`ppe_teacher_review_test_final_20261008`を保持。初期試行の`ppe_teacher_review_test_20261008`（V11途中）、`ppe_teacher_review_test_runtime_20261008`、`ppe_teacher_review_test_verified_20261008`も専用DBとして保持する。

## 提出画面のプロトタイプ再現・最終受入（2026-10-08、第155版）

評価確認の画面資産は変更せず、提出用JSP/CSS/JSを分離した。既存の合成fixture、Control/DAO認可・一時runnerを再利用し、プロトタイプ自体とDB schemaは変更していない。

| チェック項目 | 判定 (OK/要修正/要確認) | 根拠文書 | 差分・懸念 | 修正案・結果 |
|---|---|---|---|---|
| 一覧上部/集計 | OK | [プロトタイプ](../../../screen-flow-diagram/webapp/WEB-INF/teacher/submission/submission.html) | 旧実装はタイトル/説明だけで3カードがなかった | 表示件数/全件一致/平均一致率、kicker/説明/配色/余白を実データから復元 |
| フィルター/操作 | OK | 同プロトタイプ、[仕様第155版](../../function-specification.md#提出課題確認機能優先度高) | 旧実装は検索後にsubmit、sort用select、CSV/ZIP操作なし | 6即時filter、学校/クラス同期、更新/一括ZIP/CSV toolbarを復元 |
| 一覧表 | OK | 同プロトタイプ | 学校/クラス・課題/難易度を合成表示、見出しsort/色付き表示なし | 9列、8見出しのクリック/Enter/Spaceで昇降順、バッジ/一致pill/同意色/「表示」。提出版と履歴は課題欄に補足 |
| 詳細 | OK | 同プロトタイプ | 旧実装はmodal-xl、縦並びのpre/textareaとケース別テキスト | 全画面、入出力4列表・○/×、左右CodeMirror、260px/暗色、単体取得、中央ページ数と前後操作を復元 |
| 実行/取得 | OK | [実装契約](../implementation-contract.md)、仕様第155版 | プロトタイプのダミー実行や固定コード、再提出のファイル名重複は移植不可 | 認証runnerで標準入力付き一時実行。コピーは画面内のみ保持。単体/ZIPは提出ID・版付き保存済みコード、CSVは最新同意agreed/commit済み監査のみ |
| レスポンシブ/共通UI | OK | [feedback](../../feedback-guideline.md) | 中間1024pxで長い教師/学校名のheaderがはみ出した | 画面専用CSSでheader折返し、通常3カード/狭幅1列・詳細左右/狭幅縦並び、表だけ局所scroll。共通CSS不変 |

実装資産は[提出JSP](../../../src/main/webapp/WEB-INF/teacher/submission/submission.jsp)、[CSS](../../../src/main/webapp/css/teacher/submission/submission.css)、[JS](../../../src/main/webapp/js/teacher/submission/submission.js)。GETの`view=file/zip/csv`を[Servlet](../../../src/main/java/servlet/teacher/TeacherReviewServlet.java)へ接続し、[Control](../../../src/main/java/control/teacher/TeacherReviewControl.java)と[DAO](../../../src/main/java/dao/TeacherReviewDao.java)で現在のsubmission-review/学校範囲を再検証する。ZIPは各保存コードの合計と圧縮後サイズ、CSVはUTF-8サイズの32 MiB上限を検証する。単体/ZIPは授業上の閲覧と同じく同意だけで遮断しない。CSVは生徒ログインID・学校/クラスを含む教師確認用であり研究用匿名化出力ではない。

| 実行コマンド/方法 | 最新結果 |
|---|---|
| `sh -n scripts/testing/teacher-submission-review-local.sh` | 成功 |
| `sh scripts/testing/teacher-submission-review-local.sh unit` | exit0。Java21/offline Gradleのclean後、全391件中259成功/132明示gate skip/失敗・error0、WAR成功。`build/teacher-submission-review-validation/unit/run-16337` |
| `sh scripts/testing/teacher-submission-review-local.sh browser` | clean後、空の専用MySQL migration・対象25件中24成功/1 runtime gate skip。別認証HTTP1成功。`browser/run-14295`。18089を保持して画面受入後、停止trapで所有container/networkを全て清掃（保持コマンド全体をexit0とは扱わない） |
| DB/認証HTTP | 単体Python内容、3提出を重複名なしで保存するZIP、全8 sort×昇降順の一覧/CSV順序、最新不同意除外、空ZIP400/担当外file404/機能失効403、監査書込障害時のCSV拒否、32 MiBちょうど/1byte超の境界unit、提出/評価/ログ/実行記録不変を確認 |
| 通常1440px | プロトタイプ/本実装ともhero `619.633px 516.367px`、filter4列各269.5px、詳細左右各680px・editor高さ260px、detail背景rgb(248,250,252)/出力rgb(17,24,39)。元コードreadOnly、コピー編集→入力付き実runner成功→前後移動でコピー/入力保持・元コード不変 |
| 狭幅/中間 | 一覧1440/1024/768/375pxでdocument幅=viewport幅。表は必要時だけ局所980px scroll。375px詳細はmodal/body client/scroll各375px、detail1列311px、editor277pxで横溢れなし |
| ブラウザー操作 | 検索直後の通常click、0件・不同意filterの出力disabled、学校/クラス、全8sort×昇降順の画面/server順序一致、keyboard sort、○/×とHTML風入力不活性、単体/CSV/ZIP HTTP200・Blob開始、単体取得後のcloseと4回連続focus復帰、模擬503更新で旧データclear/通知→実DB再取得復旧を確認。記録したpageerror0。意図的503のconsole network errorは異常系の証拠 |
| 評価画面非退行 | 従来review.css/review.js、4評価履歴、固定版/結果/理由/ログの詳細表示を維持。評価画面のプロトタイプ再現は今回対象外 |
| 開発8080反映 | 当該appだけ再起動。host別teacher/student login各200、未認証submissions302、配信CSS/JSとsource SHA256一致。既存教師の資格情報変更やDB再seedなし。認証後業務操作は専用18089で確認 |

新スクリプトはtmpfs DBと所有label/固定名拒否、実行別report、cleanビルドを使用する。古いイメージ/reportに残るXMLの合算を今回の成功件数に含めない。旧スクリプトの当時の記録は保持するが、最新の再現手順には新スクリプトを使う。

残る確認はOS保存ダイアログ/保存完了、全OS・ブラウザー、本番TLS/負荷/実利用者手動受入。統合ブラウザーにはCLI E2E suiteのexit codeはない。外部AI・本番deploy・commitは行っていない。次は工程20の機能横断検証。

## 評価画面のプロトタイプ再現・最終受入（2026-10-08、第156版）

T-EVF-001〜004を完了。評価専用の[JSP](../../../src/main/webapp/WEB-INF/teacher/evaluation/evaluation.jsp)、[CSS](../../../src/main/webapp/css/teacher/evaluation/evaluation.css)、[JS](../../../src/main/webapp/js/teacher/evaluation/evaluation.js)を分離し、既存Review層へ接続した。提出画面・共通資産・プロトタイプ・DB schema/製品seedは変更していない。

| チェック項目 | 判定 (OK/要修正/要確認) | 根拠文書 | 差分・懸念 | 修正案・結果 |
|---|---|---|---|---|
| 一覧構成 | OK | [評価プロトタイプ](../../../screen-flow-diagram/webapp/WEB-INF/teacher/evaluation/evaluation.html) | 旧汎用一覧は3カード・独立サマリー/2観点分布がなかった | 実データの件数/欠損除外平均、学校/クラス/課題別サマリー、完了率を復元。固定ダミー未評価数は移植しない |
| filter/表/出力 | OK | 同プロトタイプ、[仕様第156版](../../function-specification.md#評価確認機能優先度高) | submit式検索・sort select、観点条件式/JSON/ZIPなし | 8即時filter、10列/9見出しsort、更新/一括結果/一括ログ/CSVを復元。条件式は既存アンケートfilterの検証/比較を再利用 |
| 全画面詳細 | OK | 同プロトタイプ、[状態ルール](../../state-rules/teacher/evaluation-state-rules.md) | 旧実装は別sectionの縦テキスト | 生徒評価風hero、2観点カード、項目スコア/理由、観点別filter、上部/下部ログ導線を復元。履歴/前回完了・固定版は実際の保存値を補足 |
| ログ | OK | 同プロトタイプ | 時点/前後・コード差分強調/理由sidebarがなかった | 保存ログのコード/実行入出力/エラー、時点・前後・一覧/理由の切替と観点filterを接続。同位置の前行との差を強調し、架空の実行ID/根拠を追加しない |
| データ・取得境界 | OK | [実装契約](../implementation-contract.md)、仕様第156版 | ダミーJSON/件数や画面だけの認可は不可 | JSON/ZIPは現在の機能/割当学校認可、保存値のみ。CSVだけ最新同意agreedとcommit済み監査。提出/評価/コードログ不変をDBで確認 |
| 共通UI/レスポンシブ | OK | [feedback指針](../../feedback-guideline.md) | 旧詳細のcontainer/余白がプロトタイプと不一致、中間header横溢れ | 専用CSS内で再現し共通CSSは不変。1440/1024/768/375pxで一覧/詳細ともページ全体の横溢れなし。表だけ局所scroll |

意図的な相違は、実際の評価状態/履歴/固定版を示す補足、最新同意CSVの説明、保存データがない箇所の未評価/欠損表示。採点や再評価を新設せず、プロトタイプの固定6回保存/5回実行/点数・理由はコピーしない。JSONは既存の保存詳細DTO（ログ単体は`row/logs/submittedCode`）、ZIPは提出ID/版/評価ID付き名で重複を防ぐ。単体JSONとZIP/CSVのUTF-8内容を32 MiBで制限する。ZIPは圧縮前合計・圧縮後の双方を検証し、空対象は拒否する。

| 実行コマンド/方法 | 最新結果 |
|---|---|
| `sh -n scripts/testing/teacher-submission-review-local.sh` | 成功。提出検証用スクリプトを再利用し、別の検証基盤は増設していない |
| 下記clean全体ビルド | exit0。全392件中260成功/132明示gate skip/失敗・error0、WAR成功。最新XML/WARは`build/evaluation-review-validation/final-full` |
| `sh scripts/testing/teacher-submission-review-local.sh browser` | 空tmpfs MySQL migration、対象26件中25成功/1 runtime gate skip、別認証HTTP1成功。最新`browser/run-24914`。保持コマンド全体をexit0と扱わず、受入後stop/trapで所有環境を清掃 |
| DB/HTTP | 全9sort×昇降順と一覧/CSV順序、条件式/欠損、評価/ログJSON・ZIP内容とunique名、所属/機能失効、保存不変、監査障害CSV拒否、空ZIP400/担当外404/失効403、不正条件400、32 MiB境界unitを確認 |
| 1440px幾何・目視 | 一覧hero `619.633px 516.367px`、filter各269.5px。詳細hero1296px、grid `761.594px 428.406px`、score panel各595pxでプロトタイプと一致。カード/配色/余白は詳細なcomputed styleも照合し、一覧・詳細・ログをスクリーンショットで目視 |
| 通常/狭幅 | 一覧/詳細1440/1024/768/375pxでdocument幅=viewport。375pxログでもmodal body幅375px。横長表だけ局所scroll、狭幅詳細は1列へ切替 |
| ブラウザー操作 | 4履歴/完了2/未完了2/完了率50%、欠損除外平均4.0。9見出し×昇降順の画面/server順序一致・keyboard sort、条件式/不正通知・0件/不同意出力制御、一覧と独立したサマリー、履歴の評価中/失敗/前回完了を確認 |
| 詳細/ログ/取得 | 上部/下部ログ導線、時点/前後とsidebar/観点filter、HTML風理由の不活性、単体JSON2種・ZIP2種/CSV HTTP200とBlob開始、4回連続modal開閉/focus復帰を確認。正常操作で記録したpageerror/console warningは0 |
| 異常系 | 模擬503更新で旧一覧/集計clear・出力無効/通知→実DB再取得復旧。履歴の遅延503が後から届いても新しい詳細を消さないことを確認。意図的503のnetwork console errorは異常系の証拠 |
| 提出画面非退行 | 3提出・専用資産・1440px全画面詳細・左右2つのCodeMirrorと保存コード表示を認証ブラウザーで再確認。提出JSP/CSS/JSは今回変更なし |
| 開発8080 | 当該appだけ再起動、正規teacher/student login各200、未認証evaluation302。配信評価CSS/JSとsource SHA256一致。既存資格情報の変更/DB再seedなし。認証後業務操作は専用18089 |

全体ビルドの実行コマンド（Java21/Gradle8.10.2の既存offline image）:

```sh
docker run --name ppe-evaluation-review-full-regression \
  --label ppe.evaluation-review.validation=final \
  --mount "type=bind,src=/Users/t.toida/programming-process-evaluator,dst=/source,readonly" \
  --entrypoint sh ppe-tools:local -c \
  'cp -R /source/src/. /workspace/src/; cp /source/build.gradle /source/settings.gradle /workspace/; gradle --offline --no-daemon clean test war --rerun-tasks'
```

終了containerからXML/WARを保存後、当該containerを除去した。専用browser環境は所有label24914の5container/networkだけを停止・清掃し、共有app/DB/runner/別プロジェクトDBを維持した。最後のJS競合ガード補完後もclean全体/WARを再実行し、専用runtimeへの配信hashと遅延応答を再検証した。

未確認はOS保存ダイアログ/保存完了・全OS/ブラウザー・本番TLS/負荷・実利用者手動受入。大件数ZIPの性能検証は未実施。統合ブラウザーにはCLI E2E suiteのexit codeはない。エディターの既存Gson解決/古いControl診断はGradleの実compile成功と区別する。外部AI・本番deploy・commitなし。次は工程20の機能横断検証。

## 未確認・対象外（元の実装受入時点）

- 本番TLS/Nginx経由の教師操作、cookie/HTTPS条件、AWS/DNS/IAM/SSM、実デプロイは親工程。ここでは変更/実行していない。
- 生徒の新規提出→実Gemini評価→教師確認の全縦断、AI採点品質・quota・費用・実生徒データは今回未実行。既存自動評価worker/queueの動作変更や再評価/手動採点UIを追加していない。
- CSVのOS標準ダウンロード/保存、実端末・全ブラウザー/画面幅での利用者手動受入、大規模履歴の性能は未確認。提出CSVの監査書込障害は第155版の最終受入で確認済み。評価CSVの障害注入はこの記録の対象外。
