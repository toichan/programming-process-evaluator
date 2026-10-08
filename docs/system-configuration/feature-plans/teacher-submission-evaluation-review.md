# 教師の提出・自動評価確認 実装計画

## 改版履歴
| 日付 | 内容 |
|---|---|
| 2026-10-08 | 工程16/17の承認済み参照機能を実装する計画。AWS操作、既存開発DB、実Gemini評価、手動採点・再評価は対象外 |
| 2026-10-08 | 本実装、専用MySQL読み戻し、認証HTTP/runner、代表ブラウザー操作を検証。残る本番TLS・実AI縦断・利用者手動受入を区別 |

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

学校/クラス/課題ID、難易度、同意、評価段階、検索、ソートをサーバーでも検証する。詳細はクライアントの生徒IDを受けず、提出→参加→割当→クラス→学校から範囲を導出する。学校単位が権限境界、クラスはフィルター。非在籍、停止/削除生徒、非アクティブクラス、旧改訂/削除課題、終了割当も保持履歴として参照する。同意だけで授業上の閲覧を遮断しない。

## 実装タスク・完了条件
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
- 一覧の入力は任意の正の`schoolId / classroomId / taskId`、`difficulty=beginner|intermediate|advanced|none`、`consent=agreed|not_agreed|unconfirmed`、`level=1..5`、100文字以内の`search`、`sort=student|task|submitted|match|level|evaluated`、`direction=asc|desc`。`not_agreed`は最新同意の不同意/撤回に対応し、未確認とは区別する。
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

## 未確認・対象外

- 本番TLS/Nginx経由の教師操作、cookie/HTTPS条件、AWS/DNS/IAM/SSM、実デプロイは親工程。ここでは変更/実行していない。
- 生徒の新規提出→実Gemini評価→教師確認の全縦断、AI採点品質・quota・費用・実生徒データは今回未実行。既存自動評価worker/queueの動作変更や再評価/手動採点UIを追加していない。
- CSVのOS標準ダウンロード/保存、実端末・全ブラウザー/画面幅での利用者手動受入、大規模履歴の性能、CSV監査書込障害の実DB故障注入は未確認。
