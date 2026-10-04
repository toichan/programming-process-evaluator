# 教師向け機能着手前のデバッグ計画

作成日: 2026-10-04（JST）  
状態: T001〜T014実施済み・修正/再検証/ローカル反映/専用環境清掃済み

## 目的と範囲

教師向け業務機能（ロードマップ工程10・11）へ移る前に、[エラーレポートの整理結果](../error-report.md)に残る5件を再現・修正・再検証する。コードが変更されたことやビルド成功だけでは完了にしない。

今回の計画は既存機能の不具合修正であり、新機能追加・DB構造変更・フレームワーク移行ではない。本書のREQ-DBG番号はデバッグ対象の追跡用で、機能仕様書の新規要件ではない。

| ID | 優先度 | 対象 | 完了の根拠 |
|---|---|---|---|
| REQ-DBG-001 | 高 | アンケート画面の列名不一致による503 | 対象行ありの画面取得、回答保存・再読込、アクセス拒否の確認 |
| REQ-DBG-002 | 高 | コードログ0件時のJavaScript例外 | 0/1/複数件の表示・操作と例外なし |
| REQ-DBG-003 | 中 | 404画面のJSTL未処理 | HTTP404維持、未処理タグなし、有効な資産URL |
| REQ-DBG-004 | 中 | 共通確認モーダルのフォーカス警告 | 通常操作で警告なし、フォーカス復帰、確定・取消の動作不変 |
| REQ-DBG-005 | 低 | runnerテストのprocess pipe未close警告 | 終了・例外経路の後片付けと対象ResourceWarningなし |
| REQ-DBG-006 | 必須 | 教師工程への着手ゲート | 5件の完了証跡、関連計画への反映、利用者への結果報告 |

### 対象外と保留の扱い

- 過去に修正・再検証済みのrunner cleanup競合、UTF-8上限、MIME不一致等は再修正しない。回帰した場合だけ再オープンする。
- Gradle非推奨警告、SLF4J警告、本番移行権限、OS標準ファイル選択・ZIP保存、全ブラウザー/OS互換性は、この5件と別の保守・受入確認事項。
- 評価・アンケート機能全体の実設定・ユーザー手動確認は、従来どおり教師画面完成後に再開する。ただし今回の既知バグの再現・修正検証には、専用合成データによるアンケート取得・保存確認を先行して実施する。T006全体を完了扱いにしない。
- 新しい外部AI API呼出しは不要。今回の再現に実際の生徒・研究データや既存利用者の回答を使わない。

## 必読資料・技術前提

- [AGENTS.md](../../../AGENTS.md)、[実装ロードマップ](../implementation-roadmap.md)、[ローカル開発手順](../implementation-and-local-development.md)、[実装契約](../implementation-contract.md)
- [機能仕様書](../../function-specification.md)、[画面遷移図](../../screen-flow-diagram.md)、[評価・コードログ・アンケート計画](./student-evaluation-survey.md)
- [状態ルール一覧](../../state-rules/README.md)、[テーブル定義](../../database-design/table-definitions.md)、[feedbackガイドライン](../../feedback-guideline.md)
- Java Servlet/JSP、MySQL/Flyway、Gradle、Bootstrap、共有feedback、Python runnerの既存構成を維持する。Servlet → Control → DAO → DBの責務・所有者/同意検証を変更しない。
- 専用constitution・knowledge graph・project topologyはリポジトリ内で見つからない。新たな設計資料を捏造せず、上記の既存仕様・実装規則を一次情報とする。フレームワーク間の変換ガイドラインは今回の修正に適用しない。

## 共通の進め方（Plan 1.1）

1. 着手時点の作業ツリーと配信資産を確認する。他の作業の変更を戻さず、当時のエラーが今も再現するかを確認する。
2. DBを使う試験は、その作業で新規作成した専用DB・合成利用者・課題・評価・アンケートに限定する。既存開発DBを空にしたり、本番相当データを使ったりしない。
3. マイグレーションは専用DBに適用する。V11のトリガーには移行用権限を使い、通常アプリユーザーへSUPER等を付与しない。旧migrationを編集しない。
4. 修正前の再現条件・期待結果・実際の応答/例外を記録し、対象の最小範囲を修正する。
5. 修正後は同じ条件で再検証し、関連する正常系・空状態・拒否・失敗時の入力保持も確認する。
6. ローカル反映後はHTTP readinessと実際に配信されるJSP/JS/CSSを確認する。古いServlet・資産への試験を現行コードの結果にしない。
7. 成功/失敗/skip件数、コマンド、合成データの清掃結果、未確認事項を記録する。環境不足・試験のtimeoutを修正成功に置き換えない。

## デバッグ手順

### Plan 2.1: アンケートGETの503（REQ-DBG-001）

対象: [StudentSurveyDao.java](../../../src/main/java/dao/StudentSurveyDao.java) の `FIND_PAGE_TARGET` / `findPageTarget`。

1. 同意済みの合成生徒に、公開割当・提出・完了評価・有効アンケートを用意する。空DBで対象なしになる確認では列名不一致を再現できない。
2. その生徒で認証し、実際のアンケート導線からGETする。HTTP503と列名不一致のサーバーログを確認する。ログへ資格情報や回答本文を出さない。
3. SELECTの`feedback_summary`とResultSetの`evaluation_feedback`参照を整合させる。架空のDB列追加、空文字での握り潰し、同意/所有者条件の緩和は行わない。
4. GET200と評価フィードバック表示を確認する。合成回答を下書き保存し、実GETで回答を再表示する。送信後の再読込・回答件数を照合し、二重回答が増えないことも確認する。
5. 不同意・別生徒・無効対象では既存の拒否契約を維持する。取得できない条件を成功扱いにしない。

既存の [SurveyAnswerValidatorTest](../../../src/test/java/control/student/SurveyAnswerValidatorTest.java) は回答入力の回帰には使えるが、SQL列名修正の完了証拠にはならない。専用DB・認証HTTPで対象行の取得を必ず確認する。

### Plan 2.2: コードログ空状態（REQ-DBG-002）

対象: [log.jsp](../../../src/main/webapp/WEB-INF/student/evaluation/log.jsp)、[log.js](../../../src/main/webapp/js/student/evaluation/log.js)。

1. 所有者が開ける合成提出版を用意し、コードログ0件の画面を開く。前後ボタンが出力されず、JSがnullへイベント登録する経路を確認する。
2. 空状態のDOMとJS初期化を整合させる。0件だから不要な処理は明示的に分離し、架空のログ・ボタン・広い例外catchで隠さない。サイドバー等の独立した機能の初期化を止めない。
3. 0件: 空状態の案内、共通ヘッダー、存在するサイドバー操作が正常で、コンソール例外がないことを確認する。
4. 1件: 前後操作が無効で、コード・入出力・時刻が正しく表示されることを確認する。
5. 複数件: 前後移動、タイムライン選択、差分、最初/最後の無効状態を確認する。他生徒のログは取得できないことも確認する。

### Plan 2.3: 404画面のJSTL未処理（REQ-DBG-003）

対象: [404.jsp](../../../src/main/webapp/WEB-INF/error/404.jsp)、[page-start.jspf](../../../src/main/webapp/WEB-INF/template/page-start.jspf)、[page-end.jspf](../../../src/main/webapp/WEB-INF/template/page-end.jspf)。

1. 存在しないURLへGETし、HTTP404と本文の未処理`<c:if>` / `<c:choose>` / `<c:url>`を記録する。
2. エラーページのtaglib宣言・includeを確認し、必要な最小範囲で修正する。全画面テンプレートの無関係な組換えやHTTP200への変更は行わない。
3. HTTP404のまま、本文に未処理タグがないことと、日本語案内・CSS/JSのURLが正常なことを確認する。
4. 実ブラウザーでエラー画面を確認し、通常ログイン・生徒ページのテンプレート表示も回帰確認する。HTTP本文だけで表示確認済みとしない。

確認コマンド（読み取り専用、APP_PORTを環境に合わせる）:

```sh
curl -i http://localhost:8080/__pre_teacher_debug_missing_page
```

### Plan 2.4: 共通モーダルのフォーカス（REQ-DBG-004）

対象: [本実装feedback.js](../../../src/main/webapp/js/shared/feedback.js)、[生徒プロトタイプfeedback.js](../../../screen-flow-diagram/webapp/js/student/shared/feedback.js)、[教師プロトタイプfeedback.js](../../../screen-flow-diagram/webapp/js/teacher/shared/feedback.js)。

1. 各共有部品の既存の差とBootstrap標準動作を確認する。個別モーダルで既に対策済みのものと共通feedbackの残件を混同しない。
2. 合成データの確認ダイアログで、取消・確定・Escape・閉じるボタン・連続再表示を通常クリックとキーボードで再現する。shown/hidden完了を待ち、アニメーションを除去して成功した結果を通常操作の証拠にしない。
3. 閉じる際に内部へフォーカスが残る原因を共通部品で対処する。起動元が存在・可視・操作可能なら戻し、画面遷移/要素削除で戻せない場合は適切な有効要素へ移す。`aria-hidden`やfocus trapを無効化して警告を消さない。
4. 取消は送信0件、確定は1件、入力保持、再表示、別モーダルとの切替、Tab移動と起動元復帰を確認する。confirm/dangerのPromise解決や画面遷移の既存契約を維持する。
5. 本実装と生徒/教師プロトタイプで再検証し、関連する同意変更・授業演習確認・管理者確認にも回帰がないことを確認する。

### Plan 2.5: runnerテストのResourceWarning（REQ-DBG-005）

対象: [test_runner.py](../../../containers/python-runner/test_runner.py)、必要な場合のみ [runner.py](../../../containers/python-runner/runner.py)。

1. warningを有効にして既存テストを実行し、未closeのpipeを作った経路と実行条件を確認する。テストの成功と警告の解消を分けて記録する。
2. 子プロセス・stdin/stdout/stderrの所有者を確認し、正常終了・timeout・例外の後片付けを修正する。warningフィルターで無視しない。実処理側が原因と確認できた場合だけrunner本体を修正する。
3. 同じ条件で警告なしを確認し、出力上限・UTF-8境界・cleanup・対話実行の既存検証を壊さないことを確認する。資源の残存も確認する。

Pythonが利用できる検証環境で、runnerディレクトリから実行する:

```sh
cd containers/python-runner
python3 -W always::ResourceWarning -m unittest -v test_runner
```

警告が再現しない場合、同一のPython/起動経路を確認する。再現不能の記録だけで「修正済み」とせず、ソース上の資源ライフサイクルと再実行結果を利用者へ報告する。

## タスクと依存関係

各不具合はT001/T002後に独立して進められるが、基本は優先度順。T013/T014は全対象の検証後に実施する。

- [x] T001 [Plan:1.1] `docs/system-configuration/error-report.md`と本書を読み、現行コード・共有資産・作業ツリーを照合する。
- [x] T002 [Plan:1.1] 専用DB/合成fixture/認証セッションを準備し、環境境界・清掃対象を本書の検証記録に残す。
- [x] T003 [Plan:2.1] `src/main/java/dao/StudentSurveyDao.java`の列名不一致を対象行ありで再現し修正する。
- [x] T004 [Plan:2.1] 専用合成DBと認証HTTPでアンケート取得・保存・送信・再読込・アクセス拒否を検証し記録する。
- [x] T005 [Plan:2.2] `src/main/webapp/js/student/evaluation/log.js`を空状態でも正常に初期化できるよう修正する。
- [x] T006 [Plan:2.2] `src/main/webapp/WEB-INF/student/evaluation/log.jsp`の0/1/複数件画面・移動・差分・アクセス境界を検証する。
- [x] T007 [Plan:2.3] `src/main/webapp/WEB-INF/error/404.jsp`のJSTL処理を再現して修正する。
- [x] T008 [Plan:2.3] 404のHTTP本文・ブラウザー表示・通常画面のテンプレート回帰を確認する。
- [x] T009 [Plan:2.4] 本実装と生徒/教師プロトタイプの共有feedbackのフォーカス問題を再現し、共通部品で修正する。
- [x] T010 [Plan:2.4] 取消/確定/Escape/Tab/再表示・各画面の入力保持・送信件数・起動元復帰を確認する。
- [x] T011 [Plan:2.5] `containers/python-runner/test_runner.py`の警告経路と資源所有者を特定し、必要な後片付けを修正する。
- [x] T012 [Plan:2.5] warning有効のrunnerテストと、修正範囲に応じた実runner回帰を確認する。
- [x] T013 [Plan:3.1] ローカル反映・最終ビルド・対象回帰・専用データ清掃を確認する。
- [x] T014 [Plan:3.1] 本書・`docs/system-configuration/error-report.md`・関連計画・ロードマップへ証跡を反映し、利用者へ完了可否を報告する。

## 最終検証・着手ゲート（Plan 3.1 / REQ-DBG-006）

- Java/表示資産の変更後は最小の関連テストとWARから始める。回答入力の回帰には既存コマンドを使用する:

```sh
docker compose exec -T app gradle test --tests control.student.SurveyAnswerValidatorTest war --warning-mode all
```

- 上記はアンケートの実DB取得・コードログJS・404表示・フォーカスを検証しない。各手順の専用DB/HTTP/ブラウザー検証を別に記録する。追加した回帰テストがあれば実在のクラス名で同時に指定する。
- 全対象の修正後に `docker compose exec -T app gradle test build --warning-mode all` と `git diff --check` を実施する。UP-TO-DATE/ゲートによるskipを、今回の実行成功件数に含めない。
- 本書の5件が修正・再検証済みで、専用環境の清掃と完了報告まで終わる前に工程10・11へ着手しない。未解決/再現不能/環境不足があればブロッカーとして残し、利用者の明示判断なしに解除しない。
- 関連するT006の既知バグだけを完了へ更新し、評価・アンケート全体の実設定/手動E2Eの保留や他の未確認事項は残す。

## 要件対応表

| 要件 | Plan | タスク | 完了証跡 |
|---|---|---|---|
| REQ-DBG-001 | 1.1 / 2.1 | T001〜T004 | DAO・専用DB・認証HTTP・回答再読込 |
| REQ-DBG-002 | 1.1 / 2.2 | T001〜T002 / T005〜T006 | 空/1/複数ログのブラウザー検証 |
| REQ-DBG-003 | 1.1 / 2.3 | T001〜T002 / T007〜T008 | 404本文・資産・実表示 |
| REQ-DBG-004 | 1.1 / 2.4 | T001〜T002 / T009〜T010 | 共有部品と関連画面の通常操作 |
| REQ-DBG-005 | 1.1 / 2.5 | T001〜T002 / T011〜T012 | warning有効試験・資源清掃 |
| REQ-DBG-006 | 1.1 / 3.1 | T001〜T002 / T013〜T014 | 最終検証・記録・報告 |

## 検証記録

### 環境と証跡（2026-10-04 JST）

- 専用Compose project: `ppe-debug-b1016b19`、検証HTTP: `127.0.0.1:18096`。DBは`ppe_debug_test_b1016b19_20261004`（認証HTTP/ブラウザー）と`ppe_debug_test_b1016b19_junit`（自動試験）。新規tmpfs MySQLだけを使用し、通常アプリは専用ユーザー、Flyway移行だけ専用DB管理ユーザーで実行した。
- [追加DAOテスト](../../../src/test/java/dao/StudentSurveyDatabaseTest.java)は`SURVEY_DB_TEST=true`・専用DB名・接続catalog・users0件を必須とし、[合成fixture](../../../src/test/resources/student-survey-fixture.sql)を登録する。各試験後に合成行だけをFK順で除去する。一般の`test`ではこの4件をskipし、専用環境での実行を別に記録する。
- セッション内の`pre-teacher-debugging-20261004/compose.yaml`、`build-location.gradle`、`http-check.py`、`runner-smoke.py`に再検証手順を保存した。共有DB・実利用者・実回答・外部AI APIは使用していない。

| 要件 | 修正・再現 | 確認済みの結果 |
|---|---|---|
| REQ-DBG-001 | 対象行ありのDAO2件失敗と認証GET503を再現。`feedback_summary`へ整合 | DAO4＋入力6件成功/skip0、GET200、フィードバック、下書き・送信・再読込、回答1件保持、他生徒/撤回/closed/未完了評価/不正ID/匿名の拒否。ブラウザーでも回答とフィードバックを表示 |
| REQ-DBG-002 | 0件のnull参照を再現。非空ログの操作だけ分離 | 0/1/3件でJS例外0、サイドバー、最初/最後の前後無効、時系列選択、追加/削除差分。1件では合成実行の標準入力/出力/エラーを表示。他生徒のログ拒否 |
| REQ-DBG-003 | HTTP404・未処理JSTLを再現。taglib宣言を追加 | HTTP404維持、本文に未処理タグなし、ブラウザーで日本語案内と4 stylesheet、ローカルCSS/JS各200。通常ログイン/生徒/管理者画面も表示 |
| REQ-DBG-004 | 確定時のfocus警告を再現。3版の共有部品でhide/hidden/focus/Promiseを修正 | 取消/確定/Escape/閉じる/Enter・Tab・連続表示、起動元と削除/非表示/disabled時のfallback。各版の表示途中二重確定もPromise解決・backdrop0。次モーダルは1個だけ表示。同意/学校は取消POST0・確定POST1・入力保持、演習は取消で未保存コード保持/確定で移動・保存POST0 |
| REQ-DBG-005 | 17件成功でも未close警告があり、追加closure試験も失敗。実処理側のstream所有者を修正 | ホストPython3.13と配備Python3.12で各20件成功・ResourceWarning0。正常/実行例外/timeout/取消/読取例外のclose。live runnerのUTF-8/対話/取消/出力32KiB/入力8KiB/ソース64KiB/30秒idle timeout/復旧を確認 |
| REQ-DBG-006 | 最終ビルド・配信・清掃と文書更新 | 全136件中82件成功・54件skip・失敗/エラー0。WARと配信JSのbyte一致。専用単体DBの主要6表0、専用container/network/DBと試験cacheを清掃。対象5件のブロッカーなし |

### 実行コマンドと結果

以下の`SESSION`は検証資産フォルダ、専用container名は上記projectのappを指す。清掃済みのため同名環境を再作成してから実行する。

```sh
SESSION=/Users/t.toida/.copilot/session-state/b1016b19-0a77-4442-b193-0e43a4568428/files/pre-teacher-debugging-20261004
docker exec -e DB_NAME=ppe_debug_test_b1016b19_junit -e SURVEY_DB_TEST=true \
  ppe-debug-b1016b19-app-1 gradle -I /verification/build-location.gradle \
  --project-cache-dir /tmp/pre-teacher-debug-test-cache \
  test --tests dao.StudentSurveyDatabaseTest --tests control.student.SurveyAnswerValidatorTest \
  war --offline --no-daemon --console=plain --warning-mode all
python3 "$SESSION/http-check.py" install
python3 "$SESSION/http-check.py" reproduce
python3 "$SESSION/http-check.py" verify
```

- DAO＋入力試験は10件成功・skip0、WAR成功。`reproduce`は修正前GET503を確認した記録であり、修正後に503を期待して実行するものではない。`verify`は修正後の認証HTTP/DB/404確認が成功した。合成アカウントのログイン値は検証スクリプト内だけに置き、本書へ資格情報を転記しない。
- 第2の単体DBの作成・Flyway移行は専用MySQL内で実施した。`compose.yaml`が自動作成するのはHTTP用DBだけなので、再実行時も空の単体DBを別途作成・移行し、HTTP fixtureを入れたDBへ空DB前提の試験を流さない。

```sh
(cd containers/python-runner && python3 -W always::ResourceWarning -m unittest -v test_runner)
docker compose exec -T python-runner python3 -W always::ResourceWarning - < containers/python-runner/test_runner.py
docker compose exec -T python-runner python3 - < "$SESSION/runner-smoke.py"
docker compose exec -T app gradle --project-cache-dir /tmp/pre-teacher-debug-main-cache \
  test build --warning-mode all --no-daemon --console=plain
git diff --check
```

- runner単体は各20件成功・warning0。実runner smokeも成功。Java全体は136件中82件成功・54件skipで、対象DAO4件は上記専用環境の別実行で成功した。UP-TO-DATEは新規成功件数に数えない。最終WARの修正JSP/JSとソースがbyte一致。
- 通常の`docker compose exec -T app gradle test build --warning-mode all --no-daemon --console=plain`も初回成功。再試験では別containerとのproject cache lock timeoutが発生したため、lockを消さず専用cacheへ分離した。
- ブラウザーはモーダルのfadeを維持し、shown/hidden完了を待った。教師プロトタイプの不可視tabでstable待ちが停止した試験は、要素座標での実ポインター入力と完了イベント待ちで再確認した。実同意/学校/演習画面は通常のlocatorクリックで送信件数・入力保持を確認した。

### ローカル反映・清掃・着手ゲート

- `docker compose build python-runner`、`docker compose up -d --no-deps python-runner`、`docker compose restart app`を実施。main login200、処理済み404、JS配信SHA-256一致、runner healthとlive実行を確認済み。
- 専用単体DBのusers/tasks/evaluations/survey_responses/code_logs/code_executionsは各0。その後`docker compose -p ppe-debug-b1016b19 -f "$SESSION/compose.yaml" down`で専用app/db/networkとtmpfs上の両DB・合成セッションを破棄した。共有MySQL volume・利用者データは保持した。
- 試験専用project cacheと今回生成したPython cacheを除去。検証ページの最後の解放呼出しは`Page not found`となり、タブ閉鎖までは追跡できていないが、専用サーバー・データは清掃済み。既存ユーザーのタブは閉じない。
- 対象5件は完了。次のユーザー指示で工程10へ進めるが、自動で教師向け機能の実装を開始しない。評価/アンケート全体の実設定・ユーザー手動E2E、OSダイアログ/全互換性、本番条件、既存Gradle/SLF4J警告は今回の完了範囲外で、従来の保留を維持する。
- 検証中のfixture/ツール/環境エラーと修正後の結果は[エラーレポート](../error-report.md)に記録した。コミットは作成していない。
