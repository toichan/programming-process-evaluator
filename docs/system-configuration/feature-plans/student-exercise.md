# 生徒の授業演習 実装計画

作成日: 2026-10-03  
最新状況（2026-10-04整理）: **T001〜T051は実装・検証済みの作業記録**。第104版後続の単体ダウンロードMIME不一致も修正・ローカル反映済み。ディスク上の実ファイル/フォルダ取込、生成ファイル内容、認証HTTPによるディスク保存/読戻し、保存・移動・削除復元・実行・再ログインを確認。通常Chromeで単体.pyの保存と内容一致を利用者も確認済み。OS-native folder chooser/ZIP保存・全browser/OS互換性は未確認として分離する。教師配信・演習進捗制御・教師/管理者の完全削除は後続範囲。

2026-10-04ユーザー指示で次は[実装ロードマップ](../implementation-roadmap.md)の工程10へ進む。以下の初回スライスの範囲や、各バッチ時点の「未実装」「次はT030/T031」「教師工程前で停止」等は履歴であり、現在の着手指示ではない。完了済みタスクやV12等を作り直さない。

## 当初の目的と範囲（初回スライス）

- 利用者に提供する動作: 生徒本人が課題とは独立した演習領域でフォルダ/ファイルを作成し、Pythonコードを編集・保存・実行し、ごみ箱へ移動/復元できる。再ログイン後も最終保存内容を復元する。
- 今回実装する範囲: ロードマップ工程9の初回スライス。自由演習の一覧/ツリー、本人スコープ、最終コード保存、実行記録のDB保存、既存アカウント別エディター設定、ホーム/共通ナビゲーションの導線。
- 今回対象外: 完了操作・期限・要再確認の条件/導線、教師配信・再配信、教師/管理者の完全削除後対応、名前変更・移動。生徒向け完全削除操作もこのスライスには含めない。ダウンロードは第94版、アップロードは第96版追加承認で初回対象へ変更し、他の設計済み追加操作は準備中無効表示とする。
- 課題提出・コードログ・AI評価・アンケートを演習操作から生成しない。保留中の工程8確認、管理者画面統合、実管理者アカウント作成、IC-010の保持期間満了処理にも着手しない。

## 必読資料

- 機能仕様: [仕様第92版](../../function-specification.md)「授業演習機能」、アカウント別エディター設定、研究同意と学習継続。
- 画面: [画面遷移図](../../screen-flow-diagram.md)、[演習HTML](../../../screen-flow-diagram/webapp/WEB-INF/student/exercise/exercise.html)、[演習CSS](../../../screen-flow-diagram/webapp/css/student/exercise/exercise.css)、[演習JavaScript](../../../screen-flow-diagram/webapp/js/student/exercise/exercise.js)。
- 状態: [演習状態ルール](../../state-rules/student/exercise-state-rules.md)、[状態命名標準](../../state-rules/state-naming-standard.md)、[実装用状態表](../../state-rules/implementation-state-table.md)。
- データ: [DB定義](../../database-design/table-definitions.md#student_exercises)、同書のstudent_exercise_entries/code_executions、[V1](../../../src/main/resources/db/migration/V1__create_initial_schema.sql)、[全体クラス図](../../class-diagram/05-overall-summary.puml)。
- 原則: [AGENTS.md](../../../AGENTS.md)、[実装手順](../implementation-and-local-development.md)、[システム構成ガイド](../system-strucure-guide.md)、[feedbackガイド](../../feedback-guideline.md)、[実装契約](../implementation-contract.md) IC-005/IC-009。

## 前提・合意と計画ゲート

- 2026-10-03ユーザー確認: 自由編集を先に実装し、進捗制御は教師配信工程で定義する。実行結果は演習ファイル参照と専用用途を追加してDB保存する。追加ファイル操作は後で仕様化する。
- 計画専用のconstitution.mdは存在しない。ユーザー確認により、既存のAGENTS.md・実装手順・仕様/設計資料を原則/設計入力として使用する。独立した原則文書や新規アーキテクチャ成果物は作らない。
- DB定義の実行用途/ファイル参照/領域版番号追加はV12で適用済み。既存マイグレーションは変更していない。
- 実装開始時に入力契約を確認する。プロトタイプの名前100文字・深さ5階層・固定ルートs001を無条件に引き継がない。名前/パスはDB上限と安全なパス要素を検証し、任意のOSファイルパスとして扱わない。
- 保存/ツリー更新の競合を時刻の精度だけで判定しない。T001で領域単位の版番号方式を確認済み。DAOで競合を拒否し、画面で入力を保持することを確認済み。
- 計画文書はリポジトリの機能別計画形式を使用する。要件IDは既存仕様への対応用に本書で付番したもので、別の機能仕様を新設するものではない。

## Technical Context / 開発原則の確認

- 2026-10-03仕様第93版の追加承認が、以下の同期実行/結果タブに関する旧記録より優先する。T014: 授業演習設計の色/配置/ツリー/作成・編集部品を復元し、結果カードは課題本実装と共通の対話ターミナルへ変更。T015: 所有者/演習/ファイル限定のsession開始/取得/入力/停止と完了監視・重複防止を実装し、HTTP/実DB/ブラウザーで検証する。対応要件はREQ-EX-001,003,005,006,007,008,009。T012の未確認全条件とT013は別途残す。
- 新契約: POST runはexerciseId/entryId/code/CSRFを受けInteractiveExecutionUpdateを返す。GET sessionはexerciseId/entryId/sessionId/after、POST session/inputは同対象とline/CSRF、POST session/cancelは同対象/CSRF。完了時executionIdを返し、停止は既存DB状態failed+execution_cancelledで保存して画面では実行停止と表示する。同期Controlは回帰用に維持するがWebは対話方式を使用する。結果カードの描画/意匠は共通部品、セッション対象検証/DB記録は演習専用とする。

- Java 21 / javax Servlet / JSP / Gradle / Tomcat 9 / MySQL 8.4 / Flyway。Servlet → Control → DAO → DBの既存責務分担を維持する。
- [PythonRunnerClient](../../../src/main/java/control/student/PythonRunnerClient.java)の既存execute経路と隔離runnerを再利用する。[StudentEditorControl](../../../src/main/java/control/student/StudentEditorControl.java)の課題参加・提出・ログを記録する処理は呼ばない。授業演習の結果カードは既存仕様の入出力/エラー切替を維持し、対話型ターミナルへの変更は今回行わない。
- [StudentEditorPreferencesControl](../../../src/main/java/control/student/StudentEditorPreferencesControl.java)と既存設定APIを再利用し、新しい保存先やlocalStorage正本を作らない。
- 認可・入力・CSRF・状態条件をサーバーで検証し、DB/runner障害は成功通知へ変換しない。画面の確認/通知は共通feedback、レイアウトは共通テンプレートを使う。
- 適用ガイドライン: フレームワーク移行ではないため変換ガイドラインは適用しない。上記のリポジトリ原則を使用し、新しいフレームワーク/依存関係は追加しない。

## T001 確定した入力・応答契約（2026-10-03）

- 仕様第92版で承認済み。初回作成はexerciseIdなし・parentEntryIdなし・expectedVersion=0を受け付け、本人users行のロック後に既定自由領域がないことを確認して領域と項目を一括作成する。既定自由領域は本人の有効なstudent_created領域の先頭ID。既存領域があれば初回要求を409で拒否する。領域の初期version=0、初回項目作成で1とする。GETで作成しない。
- 通常作成: exerciseId・expectedVersion・type（folder/file）・name、任意のparentEntryId。save/trash/restore: exerciseId・entryId・expectedVersion、saveのみcode。run: exerciseId・entryId・code・standardInput。すべてPOSTとCSRFトークンを必須とする。IDは正の整数、期待版は0以上で、欠落や不正値を暗黙の0へ置き換えない。
- 領域版はBIGINTの0以上。変更時は領域行をロックして版/所有者/状態を照合し、変更と版増加を同一トランザクションで行う。ファイル独自の版列は追加しない。runは期待版を要求せず保存しない。
- 名前は入力を保持し、空白だけは拒否する。1〜255コードポイント、`/`・`\`・制御文字・`.`/`..`・不正なサロゲートを拒否し、先頭末尾の空白を勝手に除去しない。親からサーバーが構成したパスは1000コードポイント以内。コードUTF-8で64 KiB、標準入力8 KiB以内、NUL/不正サロゲートは禁止。追加の階層数制限は設けない。
- 成功応答: createは201で`{status:"created",exerciseId,entryId,version}`、save/trash/restoreは200で`{status,exerciseId,entryId,version}`、runは200で`{status:"executed",executionId,result}`。resultは既存PythonExecutionResultの出力/エラー/終了状態。GETは共通テンプレートのHTML。版/実行IDはDBで確定した値だけ返す。
- 失敗応答は既存JSON API同様の`{errorCode,message}`。入力不正400、権限/CSRF403、本人に利用可能な対象なし404、版/パス競合409、入力サイズ413、DB/runner障害503を使用する。未認証/強制変更中の遷移は既存Filterに従う。競合の成功再試行やサンプル補完はせず、画面が入力を保持して再読込を案内する。
- 初回領域の名称は「授業演習」、origin=student_created、expires_at=NULL、状態in_progress/unsaved。配信由来領域は別IDで維持する。既存同名領域を名前だけで統合しない。

## ユースケースとデータ契約

URLは既存生徒ルートに合わせた予定。最終的な入力/応答形式はT001で固定し、画面とServletの両方へ反映する。

| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| GET /student/exercise | 生徒本人。研究同意は利用条件にしない | 演習/選択項目ID | 本人の領域・ツリー・最終保存内容・設定・演習実行結果 | なし。GETで領域を作らない | なし | 空状態を表示。仮フォルダ/コードは表示しない |
| POST /student/exercise/create | 本人の編集可能な領域 | 親ID、folder/file、名前、CSRF、版 | 所有者・親階層・既存パス | 初回明示操作で生徒作成領域と項目をトランザクション作成 | not_started→in_progress | 作成後DB再読込。重複/不正な親/競合は理由を表示 |
| POST /student/exercise/save | 本人の有効なfile | 項目ID、コード、CSRF、期待版 | ファイル・領域・階層・版 | current_contentと版/更新日時、領域の保存状態 | in_progress→temporarily_saved。保存状態はsaved | DB保存成功後のみtoast。失敗/競合では入力保持 |
| POST /student/exercise/trash / restore | 本人の項目 | 対象ID、CSRF、期待版 | 所有者・祖先・子孫・ごみ箱状態 | 対象のentry_status/trashed_atと版。物理削除しない | active↔trashed | 影響を共通確認で表示。復元先が無効なら理由を表示 |
| POST /student/exercise/run | 本人の編集可能なfile | 項目ID、実行コード、標準入力、CSRF | 所有者・有効な領域/祖先/file | code_executionsのみ。student_exercise、演習項目/実行者参照、入力/コード/結果 | 実行状態のみ。保存内容・進捗を実行だけで更新しない | 出力/エラー/制限結果を表示。DB記録失敗は明示する |
| 既存エディター設定API | 生徒本人 | 文字サイズ・折返し・インデント・配色・CSRF | user_editor_preferences | 既存保存先へ更新 | 演習状態とは独立 | 両エディターで同じ値を再取得。保存失敗を表示 |

- IDはセッション本人を正本とし、入力された所有者IDを信用しない。親項目は同一演習領域のfolderであること、祖先を含め有効であることを検証する。
- DBのパス一意性はごみ箱項目も含む。衝突時に上書き・別名化せず明示する。階層パスはサーバーで組み立て、絶対パス・区切りを含む名前・`.`/`..`・制御文字等を拒否する。
- フォルダのごみ箱移動では祖先状態から子孫を操作不可/非表示にし、既に個別にごみ箱へ移した子をフォルダ復元で勝手に復元しない。外部キーで同一所有範囲を保証できない部分はDAOで検証する。
- 編集差分はファイル単位で画面管理し、切替・離脱時に未保存確認を行う。周期保存/30秒スナップショットを起動しない。画面の未保存値をDBの保存済み表示で消さない。
- runnerへ渡すコード/入力上限は既存課題経路の64 KiB/8 KiBと同じ。ネットワーク遮断・CPU/メモリ/時間/出力制限を弱めない。既存経路のJava検証を再利用/必要な範囲だけ抽出する。

## Implementation Steps / 実装タスク

### Phase 1: 契約・DB・入力基盤

**Plan 1.1 / REQ-EX-001〜009:** 仕様と入力/状態/応答契約を固定し、新規migrationと型付き入力/表示モデルを用意する。

- [x] T001 [Plan:1.1] 本書・docs/database-design/table-definitions.md・docs/system-configuration/implementation-contract.mdに版番号、入力制約、状態イベント、初回領域作成と実行応答の詳細を確定する。未合意の仕様変更が必要なら先にユーザー確認を得る。
- [x] T002 [Plan:1.1] src/main/resources/db/migration/V12__support_student_exercise_execution.sqlでexercise_entry_id参照・student_exercise用途・領域版列/索引を追加。既存実行用途と既存行を維持し、適用済みV1/V11を変更せず、専用DB検証後ローカル適用済み。
- [x] T003 [Plan:1.1] src/main/java/entity/StudentExercisePage.java・StudentExerciseEntry.java・StudentExerciseInput.java・ExerciseSaveResult.javaに型付き契約/入力検証を作成し、src/test/java/entity/StudentExerciseInputTest.javaで境界・不正名/種別を確認する。

### Phase 2: 所有者限定の保存・ツリー

**Plan 2.1 / REQ-EX-001,002,003,004,007,008:** DAOで対象/祖先の所有範囲と競合を検証し、Controlで状態とトランザクションを統合する。

- [x] T004 [Plan:2.1] src/main/java/dao/StudentExerciseDao.javaに本人限定の一覧/ツリー/ファイル取得、明示操作時の初回領域作成、フォルダ/ファイル作成、版付き保存を実装する。初回同時要求・パス重複・別領域親・更新競合を明示的に扱う。
- [x] T005 [Plan:2.1] src/main/java/dao/StudentExerciseDao.javaにごみ箱/復元を実装する。祖先の状態・既に削除された子・無効な復元先を検証し、実行履歴や他生徒/課題のデータを変更しない。
- [x] T006 [Plan:2.1] src/main/java/control/student/StudentExerciseControl.javaに生徒ロール・編集可能状態・入力/所有者境界・保存/削除/復元処理を実装する。完全削除や未合意の進捗操作をAPIに追加しない。

### Phase 3: 実行・画面・導線

**Plan 3.1 / REQ-EX-001,005,007,008:** 課題の記録処理と分離した演習実行を接続する。

- [x] T007 [Plan:3.1] src/main/java/control/student/StudentExerciseControl.javaとsrc/main/java/dao/StudentExerciseDao.javaから既存PythonRunnerClientを利用し、本人の有効なfileだけを実行して専用用途でDB記録する。入力検証と同期実行の計測を課題エディター/提出チェックと共有し、既存入力条件を維持する。Webでの課題/演習の操作回帰はT012に残す。
- [x] T008 [Plan:2.1,3.1] src/main/java/servlet/student/StudentExerciseServlet.javaにGET/各POSTを接続する。既存Servletと同じ@WebServletを使うためweb.xmlへの重複登録は行わない。CSRF、認証ロール、対象なし/拒否/競合/障害の応答を既存パターンで実装し、成功と失敗を混同しない。

**Plan 3.2 / REQ-EX-003,004,005,006,008,009:** 共通レイアウトと実DBデータを使った演習画面/設定/ホーム導線を実装する。

- [x] T009 [Plan:3.2] src/main/webapp/WEB-INF/student/exercise/exercise.jsp・src/main/webapp/css/student/exercise/exercise.css・src/main/webapp/js/student/exercise/exercise.jsを作成する。ツリー/編集/結果カード/ごみ箱復元・共通feedback・未保存確認を接続し、固定s001/サンプルコード/擬似CRUD/未対象操作を持ち込まない。
- [x] T010 [Plan:3.2] 同演習JavaScriptを既存StudentEditorPreferencesServlet/APIと設定保存先へ接続する。文字サイズ10〜24px・折返し・2/4スペース・3配色・初期値復帰・高さ自動拡張を維持し、課題エディターと双方の再読込で一致を確認する。
- [x] T011 [Plan:3.2] src/main/webapp/WEB-INF/student/home.jsp・src/main/webapp/WEB-INF/student/shared/navigation.jspfの授業演習を実URLへ接続し、準備中の説明を更新する。保存/認可/表示の確認前に導線だけを有効化しない。

### 第93版承認済み追加バッチ（Plan 3.3 / T011後に実施）

- [x] T014 [Plan:3.3 / REQ-EX-003,004,005,006,008,009] 授業演習プロトタイプのCSS/ツリーSVG/配置/折りたたみ/操作メニュー/設定/560px・420px編集面を本実装へ反映する。実行結果のみ課題本実装と同じ共通JSP/CSSを使い、対話入力/停止/状態/時刻/自動スクロールを接続する。未対象操作を追加しない。
- [x] T015 [Plan:3.3 / REQ-EX-001,003,005,007,008] 演習専用sessionの認可・実行・入力・停止・完了監視/一度だけ保存と最新保存結果再読込を実装し、実DB/HTTP/ブラウザーで確認する。課題業務データを呼び出さない。T012全条件の完了とは別に扱う。

追加依存: T011→T014→T015。以下T012/T013の最終判定はT015を前提とする。仕様承認・API方針はTechnical Contextを参照する。

### 第94版承認済み追加バッチ（Plan 3.4）

- [x] T016 [Plan:3.4 / REQ-EX-001,002,003,004,007,008,009] 単体ダウンロード（選択ファイルの未保存コード含むUTF-8）と本人/選択領域限定の保存済みZIPを追加する。ZIP内のフォルダ/空フォルダを保持し、ごみ箱/完全削除/無効祖先を除く。参照可能な閲覧専用領域も対象。GET `/student/exercise/download?exerciseId=...`は200 application/zip、対象なし400 download_empty、他人/無効領域404、停止アカウント403、DB障害503。保存/領域版/課題データを変更しない。ユニット/実DB/認証済みHTTPと実ダウンロード内容で検証する。
- [x] T017 [Plan:3.4 / REQ-EX-002,004,006,008,009] 設計に沿って上部4ボタン/編集3ボタン/フォルダ追加メニューを復元し、移動・アップロードは準備中無効表示とする。未実装モーダルは開かない。desktop/narrowで順序/配置/操作と共通feedbackを再確認する。

依存: T015→T016→T017→T012→T013。第94版は旧対象外ダウンロードと非表示方針に優先する。他の追加操作は動作未実装のまま。今回の配置の欠落と前回の完了表現を修正する。

### 第95版承認済み追加バッチ（Plan 3.5、2026-10-04）

- [x] T018 [Plan:3.5 / REQ-EX-002,003,008,009] 生徒のログインIDを画面の固定ルートとしてツリー/作成先/Pathへ表示する。空状態でも表示し、DB項目/相対パス/ZIP構造を増やさない。ルートメニューは配下の作成のみ。選択が入れ子のファイルでもルートメニューからの追加先は必ず最上位にする。
- [x] T019 [Plan:3.5 / REQ-EX-002,003,008] 新規ファイルの名前をサーバー側で`.py`自動追加し、`.py`/`.PY`を保持する。元入力の不正名を拒否し、追加後のUnicode文字数/パス長と既存の重複拒否を適用する。作成モーダルで案内し、プロトタイプの末尾判定も大文字小文字を区別しない。既存項目やフォルダは改名しない。

追加前提: T017および第95版のユーザー承認。T018/T019は別の実装対象であり、互いを前提としない。

検証:
- TDD: `docker compose exec -T app gradle test --tests entity.StudentExerciseInputTest --no-daemon --console=plain`は9件中2件失敗（拡張子未追加を再現）。修正後の`docker compose exec -T app gradle test --tests entity.StudentExerciseInputTest --tests entity.ExerciseDownloadTest --tests servlet.student.ExerciseFormTest war appRestart --no-daemon --console=plain --warning-mode all`は**16件成功、失敗/skip 0、WAR/reload成功**。testツールではJavaを検出しないため、既存Gradleを利用した。
- 既存の専用環境`exercise-runtime-validation/setup.py`を再利用し、実MySQL/runnerのDAO・schema・client・download・input・formを**37件成功、失敗/skip 0**。拡張子なし`hello`の保存結果`練習/hello.py`、`TEST`と既存`test.py`の正規化後重複拒否も回帰テストに追加した。
- 専用アプリの認証済みブラウザーで、空状態の実IDルート/作成のみのメニュー、`hello`作成→`hello.py`、`.PY`の非二重追加、`sample.txt`→`sample.txt.py`、重複409時の名前保持と共通feedback、不正入力/追加後253文字の400、入れ子選択からルート直下への作成、折りたたみ/展開を確認。1280px/375pxとも横overflowなし。共通テンプレート/feedbackを維持する。
- DBには表示ルートのフォルダ行やIDプレフィックスを作らず、ZIPには`hello.py`/`upper.PY`/`練習/`/`練習/sample.txt.py`を保持した。専用DBの合成項目だけを既存の拡張子なし`legacy`として再現し、GET/画面/ZIPでも改名しないことを確認した。
- 専用appのGradle共有cache PID41ロックによりローカルreloadの一度の試行が失敗。所有する専用環境を後片付けして再試行し成功。reload直後の一時404は再試行でログインHTTP200を確認した。ロックファイル削除や他プロセス停止は行っていない。
- 合成データを専用DBだけから削除し、主要7テーブル0件、専用container/network/tmpfsの除去、コピーしたapplicationの除去を確認した。ユーザーのアカウント/DBは変更していない。ユーザー手動確認を継続し、工程10へは自動移行しない。

### 第96版追加バッチ（Plan 3.6、2026-10-04）

- [x] T020 [Plan:3.6 / REQ-EX-002,008] 新規フォルダ名のドット禁止をサーバーと作成案内へ反映。既存名は保持し、既存フォルダ配下の操作を維持する。
- [x] T021 [Plan:3.6 / REQ-EX-001,002,003,007,008] 複数.py/フォルダのアップロードをUTF-8/64 KiB/100ファイル/合計1 MiBで検証し、所有者/領域版/同名・ごみ箱衝突を確認する。DAOの共通作成処理で構造を維持し、一つのトランザクション/一回の版更新で保存。POST `/student/exercise/upload`は201、入力400/本文超過413/競合409/権限403/対象なし404/DB障害503。
- [x] T022 [Plan:3.6 / REQ-EX-002,003,008,009] GET `/student/exercise/tree`で認可済みの最新ツリーと版を取得し、作成/アップロード後は画面内更新。新規ファイル選択だけ未保存確認、フォルダ/アップロードでは未保存コードと結果を維持。ごみ箱アイコン/件数/空状態とルート表記、不要な説明文除去を設計・本実装へ反映する。

前提は第96版承認とT019。T021はT020を前提とし、T022のアップロードUIはT021を前提とする。既存作成/保存/認可/ダウンロード/実行の振る舞いを回帰検証する。

実装・検証結果:
- `ExerciseUpload`でパス/名前/コード/件数/合計を検証し、`ExerciseUploadForm`でbounded formとBase64/厳格UTF-8デコードを行う。本文上限はuploadだけ4 MiB（最大1 MiBのコードとパスをURLエンコードする分を含む）とし、既存経路は256 KiBを維持する。DAOの領域作成/項目挿入を共通化し、全体commitと一回のversion更新に接続した。`ExerciseTree`は日時や実行結果を含まない明示DTOで、本人の項目/版/編集可否を取得する。
- TDDでは未実装のUpload型でcompileTestJavaが失敗。フォルダ名の新制約がZIPの既存パス検証にも適用される回帰を検出し、既存名を変更しない共通安全名検証へ分離した。追加テストの配置誤りも修正後に再検証済み。
- 最終専用コマンドは`docker compose -f <session>/files/exercise-runtime-validation/compose.yaml exec -T app gradle --gradle-user-home /tmp/ppe-exercise-gradle-96 --offline test --rerun-tasks --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests control.student.PythonRunnerClientTest --tests entity.ExerciseDownloadTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseUploadTest --tests servlet.student.ExerciseUploadFormTest --tests servlet.student.ExerciseFormTest war --no-daemon --console=plain --warning-mode all`。**43件成功/失敗0/skip0、WAR成功**。途中で共有Gradle journalのロック待ちに失敗したため、専用container内の既存依存cacheコピーでoffline再実行した。共有lock/他プロセスは変更していない。HTTP fixtureが残った再試行はDB空状態前提で21件失敗し、専用DBだけを空にして最終43件を成功させた。
- `<session>/files/exercise-runtime-validation`で`python3 -B -m unittest -v test_upload`は**1ケース成功、失敗0**。認証済みGET tree200/POST upload201、不正UTF-8/パス/ドット/101件/合計超過の拒否、CSRF403/他人404/版409、途中の衝突で項目と版の不変、1ファイル65536 bytes・合計1048576 bytes・100ファイルのちょうど上限で成功を確認。閲覧専用の拒否は既存契約の400で、当初の409期待を修正した。
- ブラウザーではFolder→File→Folderの作成と複数File/フォルダアップロードの前後で同じページの識別子とnavigation件数が維持され、再読込/redirectなし。フォルダ/アップロードでは未保存コード・選択ファイル・実行結果を維持、新規ファイルの確認キャンセルでは入力/コードを保持、承認時は新規ファイルを選択した。共通確認と作成モーダルを重ねず、モーダル閉鎖後のフォーカスを戻す。
- File/FileListを使った取込UIで日本語コード/.PY/除外件数/追加先選択、選択フォルダ名を含む階層保持を確認。ファイル選択のOSネイティブダイアログ操作自体は未確認。ごみ箱SVG/件数1→復元後0/空状態と復元機能、1280px/375pxの横overflowなし、狭幅uploadモーダルの画像を確認。共通テンプレート/feedbackを維持し、実行結果カードは変更していない。
- **注入**tree503では「保存成功・表示更新失敗・再送せず再読込」を区別して通知し、更新操作を止め、再読込で保存済みフォルダを取得できた。これを実DB障害テストとは扱わない。作成直後に他画面で版が進んだ場合も、編集中コードに新しい版を黙って適用せず更新失敗として案内する。
- ローカル`docker compose exec -T app gradle test --tests entity.StudentExerciseInputTest --tests entity.ExerciseUploadTest --tests servlet.student.ExerciseUploadFormTest --tests servlet.student.ExerciseFormTest --tests entity.ExerciseDownloadTest war appRestart --no-daemon --console=plain --warning-mode all`は**20件成功/失敗0/skip0、WAR/reload成功**。ログインHTTP200、未認証treeは認証filterの302。`git diff --check`成功。専用DB主要7表0件、container/network/tmpfs・専用cache・applicationコピーを除去済み。ユーザーのDB/アカウントは変更しない。

### 第97版追加バッチ（Plan 3.7、2026-10-04）

- [x] T023 [REQ-EX-002,006,008,009] 具体的な禁止文字案内、入力右側.py、長い名前/Pathの収容、downloadAll1行、Python3.12と青緑の保存状態文言、プロトタイプ階層型アップロード先を反映する。名前制約/255文字/二重拡張子防止は維持する。
- [x] T024 [REQ-EX-001,002,003,005,008,009] 認可済みGET treeに選択ファイルの最新コード/保存済み結果を追加し、項目クリック/戻る/進むで再読込せず切替。folderは編集コードと結果を保持して作成先を変更、別fileだけ未保存確認、同一fileは再取得しない。失敗/取消/実行中は旧状態保持。保存版と選択結果の一貫性を検証する。

前提は第97版承認、T022。T023/T024は同じJSへ順番に適用する。移動はユーザー指示により次回、名前変更/教師配信/進捗/完全削除対応も対象外のまま。

#### 実装・検証結果

- ファイル名欄の右側に`.py`を表示し、入力済み`.py`/`.PY`とフォルダ名では隠す。禁止文字を具体的に示し、技術用語・「追加後」・自動追加の説明文を除去した。サーバーの制約・既存名・255文字上限は変更していない。
- ツリー/タブ/追加先の長い名前は省略表示し、titleには全文を保持、Pathは折り返す。一括ダウンロードは1行。言語はPython3.12、保存文言は課題エディターと揃え、演習の青緑色を使う。アップロード先はプロトタイプと同じルート/ローカル名/入れ子カード/縦線/選択強調を使用し、深さ固定の再帰ではなく反復で描画する。
- 選択コード/結果/版は既存の本人限定loadPageから取得する。GETはDB非更新。実行日時は文字列DTOで返し、Java21でGsonがjava.timeを直接反射しない。初期表示と切替後の保存済み結果表示を共通化し、結果なしのファイルは以前の出力を残さない。保存済み入力の交互順序は復元不能と明示し、セッションは再開しない。
- ブラウザーで同一file再クリックのGET0回、folder選択の未保存コード/結果保持、別fileの確認取消/承認、戻る取消/補償と戻る/進む成功、取得503注入で旧状態保持、実行中の選択/履歴移動禁止、閲覧専用切替の編集/保存/実行禁止を確認。切替後保存は取得した版で成功した。作成先folderへの`.PY`新規作成で二重拡張子なし、ドットfolder拒否、folder作成と選択先への実アップロード後も同じdocument/コード/結果を保持した。503注入は実DB障害試験と区別する。
- 本実装・プロトタイプの1280/900/375pxで横overflowなし。一括ダウンロードの文字Rangeは全幅で1行。本実装の255文字のfile/folderは幅内省略表示し、全文Pathと操作メニューを収容。プロトタイプも長い名前をDOMへ設定して確認し、初回にgrid子の最小幅によるはみ出しを検出、grid列/row最小幅を修正して再確認済み。初回ブラウザー試験の誤ったselectorも修正し、空集合を成功判定せず実際の名前要素/メニューを計測した。
- `python3 <session>/files/exercise-runtime-validation/setup.py`成功。専用DBで`flywayMigrate flywayValidate`と、`gradle test --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests control.student.PythonRunnerClientTest --tests entity.ExerciseDownloadTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseUploadTest --tests entity.ExerciseTreeTest --tests servlet.student.ExerciseUploadFormTest --tests servlet.student.ExerciseFormTest war --console=plain --warning-mode all`は**45件成功/失敗0/skip0、WAR成功**。
- 同ディレクトリの`python3 -m unittest -v test_selection`は**1件成功/失敗0**。本人選択の最新コード/版/日時/保存結果、結果なし、GET非更新、別人/存在しない/ごみ箱404、未認証302、閲覧専用200を実HTTPで確認。初回は対話実行の応答shapeとstatusを誤って期待したため失敗し、既存契約のupdate/sessionIdとsucceededに修正後、pollと実DB保存を含めて成功した。
- ローカル`docker compose exec -T app gradle test --tests entity.ExerciseTreeTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseDownloadTest --tests servlet.student.ExerciseFormTest --tests servlet.student.ExerciseUploadFormTest --tests entity.ExerciseUploadTest war --no-daemon --console=plain --warning-mode all`は**22件成功/失敗0/skip0**。同selectorの`war appRestart`も成功（変更なしのテストはUP-TO-DATE）。ログインHTTP200。両画面のJS構文、プロトタイプの共通template/feedback、Python3.12/青緑色を確認。
- 未確認: OSネイティブのファイル選択ダイアログの利用者操作。File/FileListと実アップロード保存は確認済み。本バッチで移動/名前変更/進捗制御/教師配信/完全削除は追加しない。プロトタイプの名前作成は既存の簡易promptを維持し、本実装の名前欄は共通モーダル内のsuffix付き入力とする。
- 後片付け: 専用DB名を照合して合成データを削除し、主要7表0件を確認。専用container/network/tmpfsとapplicationコピー/合成ブラウザーfixture/一時Python cacheを除去、プロトタイプ検証serverも停止した。共有Gradle cache・利用者DB/アカウントは変更していない。

### 第98版追加バッチ（Plan 3.8〜3.11、2026-10-04承認済み）

#### 原則・適用ガイドライン

既存AGENTS/実装手順/共通feedbackを継続使用し、フレームワーク移行の変換ガイドラインは適用しない。状態/DB/クラス図は必読資料を使用し、新しい独立アーキテクチャ文書は追加しない。既存の領域・項目・実行IDを破壊する移行、旧Flywayの書換え、未合意の完全削除/進捗制御は行わない。

#### 作業単位

- [x] T025 [Plan:3.8] [REQ-EX-010〜015] docs/function-specification.md第98版と本計画で単一ルート/非上書き/名前再利用を合意し、画面遷移・状態/契約資料へ追跡する。仕様承認を実装完了と扱わない。
- [x] T026 [Plan:3.8] [REQ-EX-011] src/main/webapp/WEB-INF/student/exercise/exercise.jsp、同js/cssとStudentExerciseInput.java、対応prototypeで名前欄/エラーの具体例を統一し、file→folder切替のsuffixと深い階層/長い名前を本実装で再確認する。GET配信資産とブラウザー読込済み資産を区別する。
- [x] T027 [Plan:3.9] [REQ-EX-012,013] StudentExerciseDao/Control/ServletとStudentExerciseDatabaseTestへ名前変更/移動のトランザクションを追加。所有者/版/対象/自己・子孫/名前/全子孫パス/重複/状態を検証し、ID/コード/実行関連と個別ごみ箱を保持する。No-opは版を変更しない。
- [x] T028 [Plan:3.9] [REQ-EX-011,012,013] exercise.jsp/js/cssへ設計済み名前変更/階層移動モーダルを接続。未保存コードと結果を保持した同document更新、衝突の別名入力と取消、実行中無効、更新成功/表示失敗区別を確認する。
- [x] T029 [Plan:3.10] [REQ-EX-010,015] DB定義/新規FlywayとStudentExerciseDao/Controlへ通常名前空間と削除単位を分離する保存方式を追加。生徒の単一ルートへ統合し、複数既存領域の衝突は確認なしに統合しない。ごみ箱名再利用、親まとめ復元、先に削除した子の保持、同名別名復元/不在親の明示選択を実DBで検証する。旧migrationやユーザーデータを直接書換えず適用前に移行fixtureで確認する。
  - ごみ箱基盤（REQ-EX-015）に続き単一ルート/明示確認による複数領域統合（REQ-EX-010）も実装・検証・ローカル反映済み。結果は下記追加バッチを参照する。T030/T031は未完了。
- [x] T030 [Plan:3.11] [REQ-EX-014] ExerciseUpload/UploadForm/Dao/Control/Servletとexercise.jsへ同名プレビュー・file別名/skip・folder明示結合/別名/skip subtreeを接続。保存時再確認、上限/大小文字/同一送信内重複/全skip/途中失敗/版競合を明示し、既存コードを上書きしない。
- [x] T031 [Plan:3.8〜3.11] [REQ-EX-010〜015] 専用DB/HTTP/browserで仕様の組合せを確認。既存CRUD/upload/download/runnerの関連回帰、単一ルート全対象ZIP、保存コード/実行記録不変、255文字/深い階層/1280・900・375pxと全モーダル入口を検証。実施/未確認/失敗を本計画・report/checkpointへ記録し専用環境を除去する。

依存: T025→T026/T027/T029、T027→T028、T029→T030、T026/T028/T029/T030→T031。T027/T028は同一領域内の汎用操作として先行できるが、T029完了まではごみ箱内の名前再利用や単一ルート完成を報告しない。同じJS/DAOは並行編集せず順番に進める。未保存コードに新版だけを割り当てる競合隠蔽は禁止する。

#### 要件追跡

| REQ ID | Plan Items | 実装証跡 |
|---|---|---|
| REQ-EX-010 | 3.10 | 単一ルート・既存データ統合・全対象ZIPの専用DB/HTTP検証 |
| REQ-EX-011 | 3.8,3.9 | 名前案内、suffix、長い名前/Pathとモーダルの実測 |
| REQ-EX-012 | 3.9 | 名前変更のDAO/Control/Servlet/共通modalと本人/版/階層検証 |
| REQ-EX-013 | 3.9 | 移動の同対象と自己/子孫/パス/衝突/未保存保持検証 |
| REQ-EX-014 | 3.11 | アップロード組合せ/明示解決/一括保存/非上書き検証 |
| REQ-EX-015 | 3.10 | ごみ箱削除単位/名前再利用/親復元/別名/親不在検証 |

### 第99版追加バッチ（Plan 3.12、2026-10-04ユーザー承認済み）

- [x] T032 [Plan:3.12] [REQ-EX-016] 通常の展開UIでごみ箱の削除単位内の名前/階層/空フォルダを表示する。独立削除は別項目、件数は削除単位のままとし、配下へ編集/実行/個別復元を追加しない。
- [x] T033 [Plan:3.12] [REQ-EX-017] 通常ツリー/ごみ箱/タブ/追加先・移動先・復元先へ全文tooltipを接続。非有効項目も名前へホバー/フォーカスでき、深い階層/255文字/1280・900・375pxを確認する。表示を入れ替える際はtooltipを破棄する。
- [x] T034 [Plan:3.12] [REQ-EX-018] 一括ZIP保存名を生徒IDルート名.zipへ統一し、画面blob名・直接取得Content-Disposition・旧ID取得とプロトタイプを揃える。保存コード/ZIP構成/版を維持し、UTF-8と引用符・不正名前のheader試験を追加する。

前提: T029と第99版承認。T032/T033は同じJSへ順に適用し、T034の新規試験を先行する。既存T030の非上書きupload衝突解決とT031全体受入は引き続き未完了。T031では第99版との組合せも確認する。

#### 検証結果

- 本実装はtrashRootEntryIdが一致する子だけを反復描画し、削除単位の親にだけ復元menu/元の場所/削除日時を置く。API再取得時の数値IDと初期datasetの文字列IDの両方で同じ階層を表示する。操作不可の項目はaria-disabledで操作を通知し、名前のfocus/hoverは許可する。通常ツリーの選択/変更と実行結果カードは維持した。
- 全文は既存Bootstrap Tooltipのhover/focusを利用し、名前・タブ・階層選択先を対象とする。本文はtextとして扱い、狭幅で折り返す。既存titleだけを実装済み根拠にせず、可視tooltipの全文一致まで確認した。ツリー/選択先の置換前にインスタンスをdisposeし、古い表示を残さない。
- ZIPは画面の`${rootName}.zip`と、ログインIDから生成したサーバーのfilename/UTF-8 filename*を一致させる。引用符をescapeし、制御文字/不正パス名を既存名前制約で拒否する。ZIP内部は保存済み有効項目だけでルートprefixなし。プロトタイプの余分なprefix/日時も除去して本実装へ揃えた。
- TDD: 新規archiveName/contentDisposition試験は未実装メソッドでcompileTestJava失敗。実装後、`python3 -B <session>/files/exercise-runtime-validation/setup.py`の専用関連Java/WARは**56成功/失敗0/error0/skip0**。populated V12→V14移行も成功。DB設計・migrationの追加変更はない。
- `python3 -B -m unittest -v test_trash_display test_unification test_trash test_organize`は専用環境で**4成功/失敗0（12.745秒）**。新規試験は階層/独立削除/実ZIP名/header/保存コード/版不変を確認し、既存統合・旧ID取得・復元・名前変更/移動も回帰確認した。
- 可視本実装: 削除親の子フォルダ/255文字file/空フォルダと独立削除を表示し、子の操作menuなし・削除単位件数2を確認。実hover/keyboard focusのtooltip全文一致、フォルダ開閉、タブ/追加先の全文表示を確認。復元後も同document・選択ID・dirtyコードを保持し、独立削除だけ残った。1280/900/375pxで255文字は省略され、document幅=viewport、tooltipはviewport内に収まった。
- ブラウザーの通常downloadクリックで生成されたZIP blobは1642bytes/ZIP signature一致、anchor.downloadは合成ログインID.zipで一致。統合ブラウザーのdownload eventは受け取れずtimeoutしたため、OS保存dialog/実際のディスク保存名の確認とはしない。実HTTP取得したZIP内容とheaderは別途上記試験で検証済み。省略されたごみ箱項目の通常click試験はaria-disabledで待機するため、強制pointer clickで非操作/dirty保持を確認し、復元/展開等の有効な操作は通常pointerを使った。
- プロトタイプは共通template/feedbackと既存配色を維持し、名前tooltip/子の非編集表示/ZIP名を反映した。専用originでJS構文と画面描画、hover/focus tooltipを確認。初回request APIの未対応と初回focus待機timeoutは、ページ内fetchと安定後の再focusで検証し直した。
- ローカル`docker compose exec -T app gradle test --tests entity.ExerciseDownloadTest --tests entity.ExerciseTreeTest --tests entity.StudentExerciseInputTest --tests servlet.student.ExerciseFormTest --tests servlet.student.ExerciseUploadFormTest --tests entity.ExerciseUploadTest war appRestart --no-daemon --console=plain --warning-mode all`は**23成功/失敗0/error0/skip0、WAR/reload成功、login200**。画面資産はv99-trash。利用者の元のホバー表示とOS保存名は手動再確認を残す。
- 清掃: 専用teardown成功、7表0件・container/network/tmpfs・所有application/専用cacheを除去し、prototype専用serverも停止、検証ページはabout:blank。共有cache/実利用者データは維持した。session report/checkpointへ34件中32完了/2保留を記録する。
- 2026-10-04表示順の再指摘: ごみ箱の子コンテナを元の場所/削除日時より前へ移し、一覧の「配下もまとめて復元します」を除去した。復元確認の説明と削除単位/復元処理は不変。プロトタイプの既存フォルダ名→子の順序は変更不要。本実装assetをv99-trash-layoutへ更新。`docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`成功。配信済みJSを使ったブラウザー合成DOM検証で、フォルダ見出し→子→元の場所/日時の順序、子表示、展開/折りたたみ、一覧補足除去、復元説明保持とJS構文を確認した。DB/利用者アカウントは変更せず、実利用者データでの再確認とは区別する。
- 2026-10-04初期折りたたみ追加: 本実装/プロトタイプとも、ごみ箱内の親/子フォルダだけ初期折りたたみとし、通常ツリーと別の展開状態を保持する。画面内再描画では開閉を保持し、再読込で初期化する。assetはv99-trash-collapsed。上記WAR/appRestartコマンド成功。配信JSの合成DOM試験で親/子初期折りたたみ・再描画後の開閉保持・初期化・通常ルート展開不変を確認（利用者DB変更なし）。復元不能親の明示選択/親ごみ箱の場合の先行復元は既存のまま。次はT030アップロードの同名解決、その後T031へ進められる。

### 第100版エクスプローラ追加計画（Plan 3.13〜3.18、2026-10-04ユーザー承認）

基本方針は[仕様第100版](../../function-specification.md)と[画面遷移](../../screen-flow-diagram.md)。作成/取込は常設上部、選択操作はチェック対象、3点メニューはその項目の単体操作。単体/複数でDAO/検証規則を共有する。ドラッグ移動/完全削除/教師配信は追加しない。現在のJava/Servlet/DTO/DB/Bootstrapと所有者・期待版・shared feedbackを再利用し、新しい保存基盤や依存は導入しない。フレームワーク移行ではないため移行guidelineは適用しない。既存制約と未保存editor保持を前提とする。

#### 作業単位

- [x] T035 [Plan:3.13] [REQ-EX-019〜027] docs/function-specification.md/本計画/画面遷移/契約/状態資料へ確認済みの配置・日時付きZIP・初期折りたたみを追跡する。設計承認を全機能完成と報告しない。
- [x] T036 [Plan:3.13] [REQ-EX-026] screen-flow-diagram/webapp/js/student/exercise/exercise.jsとsrc/main/webapp/js/student/exercise/exercise.jsの通常フォルダを初期折りたたみに変更し、ルート/既存ごみ箱は別状態で維持する。JSP資産URLを更新し、展開/再描画/再読込を検証する。「すべて折りたたむ」「編集中位置」はT037。
- [x] T037 [Plan:3.14] [REQ-EX-019,020,024,026,027] prototype HTML/JS/CSS→exercise.jsp/js/cssの順で作成先/操作バー/checkbox/単体menu/検索/名前・更新順/位置表示/全折りたたみ/keyboardを反映する。ExerciseTree.javaとJSPに既存updatedAtの文字列を追加し、架空日時を使わない。操作0件/隠れ選択/正規化親子と既存単体操作・dirty保持を確認する。未接続一括操作を成功表示しない。
- [x] T038 [Plan:3.15] [REQ-EX-020,021] StudentExerciseDao/Control/Servlet、型付き一括入力/結果とStudentExerciseDatabaseTestへ選択正規化・全体移動/ごみ箱のトランザクションを追加する。期待版を一回だけ更新し、親子二重処理・項目同士の同名・自己/子孫・全No-op・不正ID/所有者/途中失敗を検証。単体menuとtoolbarから同じ処理へ接続する。
- [x] T039 [Plan:3.16] [REQ-EX-020,022] ExerciseDownload/Control/Servletとexercise.jsへ選択取得を接続する。1file text/1folder ZIP/複数file日時ZIP/複数含folderルートZIP、相対構造/空folder/旧ID/UTF8 header/不正対象全体拒否を実取得で検証する。日時はサーバー生成のAsia/Tokyo yyyyMMdd-HHmmssとし、画面はContent-Dispositionの正規名を使用する。編集カードの未保存取得は維持する。
- [x] T040 [Plan:3.17] [REQ-EX-020,023,024,027] prototype→本実装のごみ箱に10件ずつ表示/全件検索/選択と専用復元barを追加する。StudentExerciseDao/Control/Servletへ親から順の複数削除単位復元と明示別名/親不在destinationを接続し、親別名の子への反映/独立削除/全体rollback/dirty保持/版一回を検証する。
- [x] T041 [Plan:3.18] [REQ-EX-025] StudentExerciseDao/Control/Servletとexercise.jsへ複製preview/確認/一括作成を追加する。別名候補はDB照合順序、保存コード/有効子だけコピー、新ID/生徒作成sourceを使い、配信target/進捗/実行履歴を転記しない。元項目/配信FK/実行関連不変・名前/子孫パス上限・空folder・失敗rollbackを専用DBで検証する。
- [x] T042 [Plan:3.13〜3.18] [REQ-EX-019〜027] T030のアップロード非上書き解決を含め専用DB/認証HTTP/可視browserで全追加機能・単体/複数の共通性を検証する。全取得形・10/11/21件ごみ箱・親子重複/親不在・検索外選択・キー除外・1280/900/375・255文字/1000パス上限と既存CRUD/runnerを回帰確認する。report/checkpoint/正本docsへ実施/失敗/限界を記録し所有資源を清掃する。

依存: T035→T036/T037/T038/T039/T040/T041、T036→T037、T037/T038→一括UI完了、T037/T039→選択取得UI完了、T037/T040→ごみ箱UI完了、T037/T041→複製UI完了、T030/T036〜T041→T042。既存T031はT042の検証と共に完了判定する。同じJS/DAOは並行編集しない。対象の一部だけを実装して全体完成と報告しない。

#### Requirement Mapping

| REQ ID | Plan Items | Tasks / 実装証跡 |
|---|---|---|
| REQ-EX-019 | 3.13,3.14 | T035,T037; prototype/exercise.jspの役割分担 |
| REQ-EX-020 | 3.14〜3.17 | T037〜T040; checkbox/サーバー親子正規化 |
| REQ-EX-021 | 3.15 | T038; 一括DAO/期待版/失敗rollback/共通UI |
| REQ-EX-022 | 3.16 | T039; 実取得text/ZIP/header/日本時間 |
| REQ-EX-023 | 3.17 | T040; 全件検索/10件追加/親子一括復元 |
| REQ-EX-024 | 3.14,3.17 | T037,T040; 文字列updatedAt/安定sort/検索 |
| REQ-EX-025 | 3.18 | T041; 複製新ID/保存内容/元source・履歴維持 |
| REQ-EX-026 | 3.13,3.14 | T036,T037; 初期折りたたみ/位置/全折りたたみ |
| REQ-EX-027 | 3.14,3.17 | T037,T040; checkbox/範囲/入力欄を奪わないkey |

T042は全要件の組合せ証跡。承認済みREQ-EX-014は既存T030へ追跡を維持する。

#### 最初の適用結果（T035/T036）

- ユーザー修正を反映し、3点メニューの単体移動/ダウンロード/ごみ箱を維持する契約と、複数fileだけ日本時間日時付きZIPを確定した。通常フォルダ初期折りたたみはproto/本実装とも適用済み。ルート名と直下を残し、通常/ごみ箱の開閉状態を分離し、画面内再描画でも開閉を保持する。資産URLはv100-folders。
- `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`は成功、ローカルlogin200。配信JSの合成DOMで通常親/子初期折りたたみ、ルート展開、開閉後の再描画維持、ごみ箱の初期状態維持を検証。prototype専用originも初期ルート/配下折りたたみとJS構文を確認し、所有serverを停止、pageをabout:blankへ戻した。利用者DB/アカウントは変更せず、今回の確認は実データE2Eではない。
- 第100版全体は実装中。T037〜T042、既存T030/T031は未完了で、選択取得の日時付きZIPや新しい操作バーが既に使えるとは報告しない。初期折りたたみの反映と全機能の実装を区別する。

#### 第104版確認改善計画（2026-10-04ユーザー承認）

- Plan3.23: REQ-EX-035/036に従い、prototype→本実装へ操作別文言/確定button/削除全配下を反映する。
- [x] T050 [Plan:3.23] [REQ-EX-035,036] 件数補足除去、同名説明、操作名確定と色リセット、削除の有効全配下一覧を両UIへ反映。
- [x] T051 [Plan:3.23] [REQ-EX-035,036] 有効/削除済み/空folder/親子正規化/大量/長名・3幅/取消dirty/色切替と配信を確認する。
- 依存: T050→T051。Requirement Mapping: REQ-EX-035/036→Plan3.23→T050/T051。API/DB/依存は不変。

#### 第104版確認改善結果（2026-10-04）

- 教師工程前の後続確認: 単体ダウンロードのMIME不一致を修正し、単体menu/選択1件・folder/複数ZIP・未保存/保存済み内容の違いとエラー時保持を確認。実ディスクfile/folderをfile inputへ設定して取込、認証HTTPでdisk保存/読戻しとZIP構造、保存後再読込/移動dirty保持/配下削除復元/実行後logout-login復元も成功。専用Java64/HTTP10/強化した単体MIME1件、WAR/restart/配信一致/清掃成功。通常Chromeの単体.py保存・内容一致は利用者確認済み。OS-native folder chooser/ZIP保存・全browser/OS認証とは区別し、教師工程へは進まない。詳細・検証限界は[エラーレポート](../error-report.md)参照。
- 後続の三点メニュー切り取り不具合は、表示中だけbody直下へ移す共通形helperとfixed配置で本実装/prototypeを修正。ツリーの横scrollは維持し、閉じる際の復帰/再描画時の破棄/移設後のクリック委譲/矢印とEscape操作を接続した。3幅で全メニュー項目のクリック可能性とviewport収容、移動/削除確認・取消、単一表示/再描画/外クリック/focusを確認。通常グレー/primary青/削除赤の色区分は変更しない。専用Java64/HTTP10、local WAR/restart/login200/helper・JS・CSS配信一致と所有環境清掃を確認。バグ修正であり追加機能版/タスク数は増やさない。別件の単体download MIME不一致と検証詳細は[エラーレポート](../error-report.md)を参照。
- 選択件数の配下補足を除去し、移動先の同名項目がある場合の「下の名前欄」への変更を具体的に説明する。復元/複製も操作先と入力欄を明示した。確定は移動/削除/復元/複製、削除だけbtn-danger、他の再利用ではbtn-primaryへリセット。実UIで削除→移動/復元/複製のラベル/色と説明を確認した。
- 削除確認は有効全配下をul階層で初期表示、空folderも含め、親子正規化後の各対象の配下を重複させない。専用fixtureで2対象・合計31項目、深い階層/空folder/251文字名/25子の最後まで表示、先に削除済みの子を除外。1280/900/375pxでmodal内縦scroll・横overflowなし。未保存警告と取消後コード/選択保持を確認。単体の赤「削除」から実ごみ箱移動→通常色「復元」から実復元成功、対象外のdirtyコードは維持した。
- prototypeは既存shared feedbackのdetailsへ全配下パスを階層順で追加し、赤「削除」と完全削除でない説明を確認。danger helperの既定「取り消しできない操作」が矛盾するため、この用途だけkickerを「削除する内容の確認」に指定した。共通feedback自体は変更しない。3幅のmodal収容/取消、移動button名、構文も確認した。既存shared feedbackのhide時aria-hidden/focus警告は残る。
- 専用`python3 <session>/files/exercise-runtime-validation/setup.py`成功、移行fixture/Java64成功・failure/error/skip0・WAR/login200。`python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は10成功。runTestsツールはJavaテストを検出できず、成功件数は既存Gradle XMLから取得した実結果である。
- `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`成功、local login200、v104-confirmのJS/CSS配信byte/SHA-256一致、`git diff --check`成功。新API/DB/依存・実アカウント操作なし。専用合成行/主要7表0、専用container/network/tmpfsと所有server/copy app/cache/bytecodeを除去、browser about:blank。OS-native操作・全browser互換性・教師/評価/アンケートは保留。

#### 第103版識別・選択範囲改善計画（2026-10-04ユーザー承認）

- Plan3.22: REQ-EX-033/034に従い、prototype→本実装へ操作アイコン/作成1行/中央upload/検索×/枠なし操作/モード中全表示/親選択の配下可視化を反映する。新API/DB/依存なし。
- [x] T048 [Plan:3.22] [REQ-EX-033,034] 配置と選択範囲表示を両UIへ実装し、選択元IDと親子正規化/明示子選択保持を維持する。
- [x] T049 [Plan:3.22] [REQ-EX-033,034] 実/試作browserの3幅、アイコン中央/作成1行/検索×/0件操作/親子チェック/取消dirty、WAR/配信・片付けを検証する。
- 依存: T048→T049。Requirement Mapping: REQ-EX-033/034→Plan3.22→T048/T049。

#### 第103版識別・選択範囲改善結果（2026-10-04）

- 実UI/prototypeの操作へ同じ線画SVGのアイコンを付け、文字ラベル/アクセシブル名を維持。作成は左の縦2段で各文字を1行、右側uploadは44pxのbutton内中央へ配置した。1280/900/375pxで文字1行・縦配置・ページoverflowなし、upload中心誤差0pxを測定。実スクリーンショットも確認した。
- 続く配置指示により、選択終了/全選択を上段2列、検索結果選択は独立行、件数の下のdownload/moveとtrash/clearも同幅2列・親幅いっぱいに揃えた。prototypeにも全選択buttonを同じ位置へ追加し実2件選択を確認。両UIの1280/900/375pxで同段/同幅（丸め差1px未満）/左右端一致/内外overflowなし、実スクリーンショットを確認。hoverによる1pxの上下ずれは選択button群だけtransformなしにして抑制。ごみ箱の復元/解除も2列。最終配信は第104版へ含める。
- 検索の独立解除buttonとlistenerを撤去し、入力内のnative×クリックで空文字/検索状態非表示/一覧復帰を実UIとprototypeで確認。375pxのprototypeでは入力がviewport外だった初回クリックを有効試験とせず、scrollIntoView後のnative×クリックで空入力を確認した。キーボード全削除/input更新も維持。
- 選択モード中は0件でも通常/ごみ箱の操作と件数を表示、対象なしの操作をdisabledにする。欄自体の初期close/非自動展開は維持。色付き囲みcard/枠を撤去し、他の操作群と同様の余白/ボタンを使用する。border0/透明背景を実測し、危険操作は赤と文字で識別できる。
- 選択folderの配下は親に含まれるチェック済み・disabled表示とし、titleで親を外して個別選択する方法を説明する。配下IDを自動追加しないため親子重複を作らず、既存normalTargets/サーバー正規化は維持。親＋明示子の移動確認は1件、取消/dirtyを保持。親解除後は先に明示選択した子だけ残り、親だけを選んだ場合は解除後に子のチェックも消える。展開後/検索中にも親範囲を反映。ごみ箱子は個別復元不可のまま。rootのmixed表示も選択更新時に同期した。
- 専用`python3 <session>/files/exercise-runtime-validation/setup.py`成功、移行fixture・Java64成功/failure/error/skip0・WAR/login200。`python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は10成功。実/prototype JS構文成功。375pxで一部展開button内のoverflowを検出し、余白/文字サイズを調整・同幅でoverflow0へ再試験した。shared template/feedbackと既存確認画面を維持する。
- `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`成功、local login200とv103-controls配信JS/CSSのbyte/SHA-256一致。`git diff --check`成功。API/DB/依存・実アカウント変更なし。専用合成行/主要7表0、専用container/network/tmpfs削除、所有server停止、コピーapp/cache/bytecode除去、browser about:blank。OS-native chooser/save・全ブラウザーのnative検索×互換性は未認定、教師/評価/アンケートは保留。

#### 第102版配置調整計画（2026-10-04ユーザー承認）

- Plan3.21: 第102版REQ-EX-031/032を正本として、prototype→本実装へ同じ配置を反映する。新API/DB/依存変更なし。選択状態/dirty/実行結果/版は保持する。
- [x] T046 [Plan:3.21] [REQ-EX-031,032] 追加open、小型upload、入力直下検索状態、展開/折りたたみ対、次行の編集中表示、通常/ごみ箱の選択折りたたみ・件数・取消focusをprototypeと実UIへ接続する。
- [x] T047 [Plan:3.21] [REQ-EX-031,032] 初期状態/検索/全展開/全折りたたみ/選択保持・非自動展開/取消focus/tooltipと1280/900/375pxを可視browserで検証し、WAR/restart/配信一致・後片付け・結果を記録する。
- 依存: T046→T047。Requirement Mapping: REQ-EX-031/032→Plan3.21→T046/T047。

#### 第102版配置調整結果（2026-10-04）

- 本実装/prototypeとも追加open、表示設定/選択操作close。uploadは右端44pxのアイコンbuttonでtooltip/aria-labelを付け、1280/900/375pxでファイル作成と同じ行・右側に収まりページoverflowなし。展開/折りたたみは同じ行、編集中表示は次行に配置。実左側スクリーンショットも確認した。検索本文はinput直下、閉じた表示設定には「検索中」の短い状態を示す。
- 全展開/全折りたたみの実可視項目数は本実装7→5（root/直下だけ）、prototypeの可視fileは3→0。ごみ箱の展開状態と検索/選択/dirtyを維持する。選択関連を通常/ごみ箱のdetails内へまとめ、閉じる/再描画でモードやチェックを解除しない。通常2件、Ctrl/⌘click1件、ごみ箱1件で欄は閉じたまま見出し件数が更新。移動取消は閉じた見出しへfocus復帰、チェック2件と未保存コードを保持した。prototypeでも操作buttonがdetails内・閉じると不可視、取消focusと検索結果選択を確認。ごみ箱欄の再描画でもopen/modeを維持する。
- 専用`python3 <session>/files/exercise-runtime-validation/setup.py`成功: 移行fixture、Java64成功/failure/error/skip0、WAR/login200。`python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は10成功。実/prototype JS構文、uploadのfocus説明、prototypeのEnterから既存upload modalを開けることを確認。shared template/feedbackを維持し、OS-native picker/saveは未確認。
- prototypeの折りたたみを追加した際、閉じタグの位置により操作buttonが欄の外へ出た。DOM包含と不可視性の試験で修正・再検証した。検索結果選択buttonも新しい選択欄へ移し、listenerを新位置へ接続・実選択1件を確認。詳細は[エラーレポート](../error-report.md)参照。
- `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`成功、local login200、v102-layoutのJS/CSSはソースとのbyte/SHA-256一致。`git diff --check`成功。API/DB/依存変更と実アカウント操作なし。専用合成行/主要7表0、container/network/tmpfs削除、所有server停止、コピーapp/cache/bytecode除去、browser about:blankまで完了。共有cacheは変更しない。教師/評価/アンケートは保留のまま。

#### 第101版UI改善計画（2026-10-04ユーザー承認）

- [x] T043 [Plan:3.19] [REQ-EX-028] prototype→exercise.jsp/js/cssで移動先/明示復元先へ共通フォルダツリーを接続する。展開と選択を分離、選択パス/自己・配下除外/取消保持/サーバー契約維持を確認する。
- [x] T044 [Plan:3.20] [REQ-EX-029,030] prototype→exercise.jsp/js/cssへ初期折りたたみ追加/表示設定、通常/ごみ箱の選択モードと対象あり自動バー、控えめな行間/余白/indent/menuを反映する。検索状態表示、修飾キーと異階層選択、0件/終了/取消/dirty保持を確認する。
- [x] T045 [Plan:3.19,3.20] [REQ-EX-028〜030] 専用合成browserで初期/操作展開/移動先/復元先/検索/長名/深階層と1280/900/375、共有template/feedbackを検証し、関連build/reload/ローカル配信資産一致、後片付けと結果を記録する。

依存はT043/T044→T045。同階層制限/新API/DB変更は追加しない。

#### 第101版UI改善結果（2026-10-04）

- 「追加」「表示設定」は初期折りたたみ、通常/ごみ箱のcheckboxは「選択」モードで表示する。Ctrl/⌘clickでも自動的に選択モードへ入り、対象ありの操作バーだけ自動表示する。0件時はバーを畳みモードは維持し、「選択を終了」でチェック/anchorを消して通常表示へ戻る。検索/更新日時順は表示設定を閉じても状態を要約表示する。異なる階層の選択と単体menuは維持した。
- 行のgap/余白/indentとメニュー枠を圧縮し、展開操作はfolderだけに残す。通常時はcheckbox分の幅を除き、三点menuはhover専用にせず常時発見可能。1280px実画面の長名表示幅は通常192.70px/選択中170.30pxで、通常時に**22.40px**を回収した。横はみ出しなしを1280/900/375pxで確認し、255文字は省略表示とhover/focusの全文表示を維持する。画面全体/Explorerは既存template/緑系配色を使い、左側のスクリーンショットも確認した。
- 移動先は共通`renderFolderTargets`で有効folder/rootだけを階層表示し、別の矢印で開閉、名前で選択、選択パスを表示する。対象自身/全対象配下は除外し、初期未選択では確定不可。実選択後に有効化されることと、単体・folder配下の実移動を確認。未保存コード/同document/編集位置を保持する。元の親が完全削除された合成ケースでは同じtreeで復元先を選び、実復元成功を確認した。アップロードtreeの選択/矢印のSpace開閉とfocus復帰も確認した。
- 専用`python3 <session>/files/exercise-runtime-validation/setup.py`はV12→V14移行fixture・Java**64成功/failure/error/skip0**・WAR・login200。`python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は**認証HTTP10成功**。サーバー/API/DB/名前制約は変更せず、JS/JSP/CSSとprototypeの改善だけ。Java用runTestsツールでの代替結果ではなく、既存Gradleの実XMLを集計した。
- 可視browserで初期補助2群close/チェック0/操作バー非表示/DOM ID重複なし、選択0→1→0/終了、⌘click/Space/⌘A/Escape、検索外の異階層2件と全対象パス、取消の入力/チェック保持、検索+更新順要約、255文字全文、3幅と移動modalの横はみ出しなしを確認した。prototypeも初期fold/mode0→2→0/終了/3幅と移動先パスを確認。proto/本実装ともJS構文確認成功。shared feedbackと既存確認dialogを維持し、alert/confirmの再実装なし。
- 移動確定ボタンの再有効化listenerがsubmit内部へ登録されていたため、初回選択でも無効のままだった。初期化へ移して、未選択disabled→選択enabled→実確定成功を再試験。prototypeの移動先パスのルート二重表示も修正・可視再確認した。[エラーレポート](../error-report.md)に履歴を残す。
- `docker compose exec -T app gradle war appRestart --no-daemon --console=plain --warning-mode all`は成功、local login200、配信JS/CSSのSHA-256が編集済みソースと一致。資産URLはv101-design。利用者アカウント/DB変更なし、新migration/依存追加なし。
- 専用DBの合成行削除/主要7表0件、専用container/network/tmpfs削除、application/専用cache/bytecode除去、所有server停止、共有pageはabout:blank。共有cache/実アカウントは清掃対象にしない。OS-native chooser/save・全OS修飾キー差の未確認と教師/評価/アンケート保留は従来どおり。prototype旧modalのhide時aria-hidden/focus警告は継続し、本実装のhide前blur/focus復帰は維持する。

#### 第100版追加バッチ結果（T030/T031/T037〜T042、2026-10-04）

- 上記「最初の適用結果」は初期折りたたみだけの時点記録。本バッチで未接続だった選択操作・検索/並び替え・複製・ごみ箱段階表示/複数復元・アップロード同名解決まで実装し、ローカルへ反映した。作成/取込は上部、チェックした対象は操作バー、クリックした単体は3点メニューとし、名前変更/複製/移動/ダウンロード/ごみ箱を維持する。チェックと編集/追加先は分離し、通常フォルダ/ごみ箱は初期折りたたみ。ドラッグ移動・完全削除は追加しない。
- APIはServlet→Control→DAOへ接続。POSTは既存form-urlencoded/CSRF/本文上限を使い、`exerciseId/expectedVersion`を再照合する。`batch-move/trash/restore`は`items`のJSON配列（数値`entryId`、任意`name`、restoreの任意`parentEntryId`）を受ける。moveの共有`parentEntryId`、restoreの項目別`parentEntryId`は空文字が明示ルート、省略は元の親。結果は`{status,exerciseId,version,affectedCount}`で架空entryIdを返さない。親子整理後の別名だけを指定し、変更は全件成功またはrollback、一回の版更新、全No-opは版不変。選択した別削除単位の親を先に復元する。
- POST `duplicate-preview`は`entryId/expectedVersion`を受け、`{items:[{entryId,path,type,suggestedName}]}`を返す。POST `duplicate`は単体`entryId/name/expectedVersion`で保存内容だけを新IDへ複製する。GET previewのCSV `entryIds`と旧単体APIも維持。配下の有効項目/空フォルダを含め、独立ごみ箱/実行履歴/配信targetをコピーしない。GET treeとJSPは実`updatedAt`を文字列で返し、架空日時で補完しない。
- GET `download?exerciseId=...&entryIds=...`は親子を整理してから1file text/1folder ZIP/複数file日時ZIP/複数含folderルートZIPを決める。省略時の旧全ルート取得を維持し、UI選択0件は暗黙取得しない。名前はserverのUTF-8 Content-Dispositionを採用し、HTTP試験では実ZIP構造/空folder/不正対象全体拒否と、取得直前〜直後のAsia/Tokyo時刻内に秒精度のZIP名が収まることを確認した。
- POST `upload-preview`は既存`files/parentEntryId/expectedVersion`を受け、`{conflicts:[{path,uploadType,existingType,suggestedName,actions}]}`を返す。POST `upload`へ`resolutions=[{path,action:"merge"|"rename"|"skip",name?}]`を追加。フォルダ結合は明示、別名/skipは配下へ適用し、未解決/新たな衝突/版競合は拒否する。結果は`{status:"uploaded"|"skipped",exerciseId,version,addedFileCount,skippedFileCount}`、全skipは書込/版変更なし。画面は確認と確定を分け、取消/入力失敗で選択ファイル/解決入力を保持する。
- 専用MySQL/tmpfs・実runnerでV12→V14の既存階層/コード/配信元保存を再確認。最終`docker compose -f <session>/files/exercise-runtime-validation/compose.yaml exec -T app gradle test --tests 'control.student.StudentExercise*Test' --tests control.student.PythonRunnerClientTest --tests 'entity.Exercise*Test' --tests entity.StudentExerciseInputTest --tests 'servlet.student.Exercise*Test' war --no-daemon --console=plain --warning-mode all`は**69成功・failure/error/skip0、WAR成功**。DB試験は必須の空合成DBで実施し、利用者DBへ実行していない。
- `python3 -m unittest -v test_explorer test_organize test_trash test_unification test_trash_display`は**認証HTTP10件成功**。親子整理/No-op/全体rollback、所有者/CSRF/readonly/古い版、別名親と独立した子の復元、保存コード複製/新ID/履歴不生成、明示merge/rename/skip/全skip/上書き拒否、旧API/統合/ごみ箱/ZIPを確認した。既存の名前255文字/子孫1000文字・upload 64KiB/100件/1MiB制約を維持する。
- 可視browserでは同document/未保存保持の移動、コピー候補確認/実確定、ファイル競合の別名取込、選択downloadの日時付きanchor名、単体menuと他チェックの独立/取消保持を確認。ごみ箱21件→10/20/21件表示、10件同時復元後の残り11件、閉じた配下の検索一致/子にチェックなしを確認した。検索で異なる親の同名パスを表示、非表示チェック件数、⌘click/Shift範囲/⌘A/Escapeと検索入力除外、255文字全文tooltip、1280/900/375px横はみ出しなしを確認。prototypeも共有template/feedback・構文/全選択/3幅を確認した。
- 表示通信だけを失敗させた作成では実POSTが確定し、再送禁止警告/保存・移動無効/未保存コード保持を確認。Tooltipの表示遷移中に再描画するとBootstrapの非同期callbackが破棄済みinstanceへアクセスしたため、proto/本実装ともtooltipだけ`animation:false`にし、255文字hover→checkbox再描画を再確認した。途中の不具合/試験fixture誤りは[エラーレポート](../error-report.md)へ残す。
- ローカル`docker compose exec -T app gradle test --tests 'entity.Exercise*Test' --tests entity.StudentExerciseInputTest --tests 'servlet.student.Exercise*Test' --tests control.student.PythonRunnerClientTest war appRestart --no-daemon --console=plain --warning-mode all`は**32成功・failure/error/skip0、WAR/restart成功**、login HTTP200。JSP資産はv100-explorer。新migration/依存追加/実利用者の変更なし。
- 清掃: 専用DB名照合後に全合成行を削除し主要7表0件、専用container/network/tmpfsとapplication/cacheコピーを除去。所有static server停止、共有pageはabout:blank。共有Gradle cache/実アカウントは変更しない。
- **限界/後続**: OSネイティブのファイル選択・保存ダイアログ/実ディスク保存、全OSの修飾キー差、実利用者の手動確認は未確認。取込は合成File入力、取得は実HTTP ZIP/headerとbrowserの生成anchorで検証し、OS確認済みとは扱わない。教師配信/完了・期限・要再確認・教師/管理者の完全削除対応、評価/アンケート、運用資源制限認証は引き続き後続。今回のExplorer完成と生徒向け全体/教師機能完成は区別する。

#### 第98版T029単一ルートの追加バッチ結果（2026-10-04）

- 通常画面は生徒IDの一つのルートとし、領域選択を非表示にした。複数の未統合領域がある場合だけ一時的な移行元切替と「統合内容を確認」を表示する。旧データを黙って統合・上書き・削除しない。プレビューは移行元/元パス/別名を含む新パス/項目総数を提示し、確認取消ではDBを変更しない。静的プロトタイプの通常デザインを変更せず、旧データ用modalだけ既存Bootstrap/feedbackで追加した。
- V14は統合先参照と項目の元領域参照を追加・補完するだけで、Flyway自体は内容を移動しない。確認後のDAOは本人の領域範囲をロックし、DB照合順序（大小文字/アクセント含む）で重複を判定し、Python拡張子の表記を保持した` (2)`等の別名を決定する。255文字/子孫を含む1000文字パス、階層到達可能性、閲覧専用理由を検証し、全体を一つのトランザクションで確定する。既存ドット付きフォルダはそのまま保持し、衝突別名を新規名前制約内で作れない場合は元の名前修正を案内して拒否する。名前/パスを切り詰めない。
- 項目ID/親子構成/保存コード/削除単位/実行記録FKと元領域/配信対象FKを保持する。元領域の状態・配信情報を自由編集用に変更しない。統合後のGET/downloadは本人の旧IDを現行ルートへ解決し、旧IDへの変更要求は拒否する。実行記録保存も同じ項目IDへ解決する。統合中に既知の対話実行が残る場合は拒否する。利用可能な配信領域がある場合は別の自由領域を新設しない。
- 確認tokenに加え、現在編集中の領域ID/期待版を再照合する。最新previewを取得しても古いdirty editorへ新版を割り当てない。統合成功時は同document・選択ファイル・未保存コード・保存基準・実行結果を保持してtree/Path/URL/ID/版を更新し、一時移行欄を隠す。書込後のtree表示失敗は既存の「再送せず表示を回復」の保護を使う。
- TDDは未実装preview/unifyによるcompileTestJava失敗を確認。`python3 -B <session>/files/exercise-runtime-validation/setup.py`で専用環境のpopulated V12→V14移行を実施し、業務JSON一致・削除単位・7項目の元領域補完/未統合印・通常名前再利用を確認した。最初のJava試験は統合結果に架空entryId=0を返したため失敗し、領域ID/版だけの専用DTOへ修正した。
- 最終専用Java: `docker compose -f <session>/files/exercise-runtime-validation/compose.yaml exec -T app gradle test --rerun-tasks --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests control.student.PythonRunnerClientTest --tests entity.ExerciseDownloadTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseUploadTest --tests entity.ExerciseTreeTest --tests servlet.student.ExerciseUploadFormTest --tests servlet.student.ExerciseFormTest war appRestart --offline --no-daemon --console=plain --warning-mode all`は**55成功/失敗0/error0/skip0、WAR/reload成功**。現在編集中の版確認も最終再実行に含む。新規4DB試験で統合後の保存/復元/ZIP/alias、readonly/版/token拒否、照合順序/別名候補衝突/名前上限/子孫のみパス超過、旧ドット付き/空領域/配信のみルートを検証した。
- 専用HTTP: readiness200後の`python3 -B -m unittest -v test_unification test_trash test_organize`は**3成功/失敗0（10.798秒）**。GET unification200/POST unify200、CSRF403/不正400/他人404/版・token409、元IDGET/download200/旧ID更新404を確認。実MySQL triggerで項目移動途中にFKエラーを注入し、503/全体JSON不変/token不変を確認してtriggerを除去した。実配信対象FK付きの合成sourceを使い、配信元維持と課題業務表へ記録しないことを確認した。
- 可視ブラウザーは通常pointer/Bootstrap fadeでpreview取消、古い編集中版の409（dirtyコード保持/確認無効）、再確認後の成功を検証。同document・選択ID・未保存`print(99)`・保存済み出力を保持し、Path/URLの現行ルート化/移行欄非表示/独立ごみ箱保持を確認した。続く通常保存はDBの新版/コード/元領域関連まで一致。1280/900/375pxでdocument幅=viewport幅、単一生徒IDルート、移行欄非表示、保存コード保持を確認した。既存saveは診断用data-versionを更新しないためそのwaitはtimeout、実DBと保存済みUIで検証し直し、無関係な診断属性修正はしない。
- ローカル: `docker compose exec -T app gradle flywayMigrate flywayValidate test --tests entity.ExerciseTreeTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseDownloadTest --tests servlet.student.ExerciseFormTest --tests servlet.student.ExerciseUploadFormTest --tests entity.ExerciseUploadTest war appRestart --no-daemon --console=plain --warning-mode all`は**22成功/失敗0/error0/skip0、WAR/reload成功、login200**。`docker compose exec -T app gradle flywayInfo --no-daemon --console=plain`も成功しschema14/全migration Successを確認。実利用者の複数領域は確認前のまま残す。
- 共有Gradle lockは削除/プロセス停止せず、専用application内だけにlockを除いた既存cacheをseedし、GRADLE_USER_HOMEとoffline実行を分離した。依存追加はない。利用者の元の表示問題/OSファイル選択、統合成功後のtree通信失敗の新規ブラウザー注入、全modal/新uploadの組合せ最終確認は未実施。T030/T031を続け、教師画面・進捗制御・完全削除へ自動で進まない。
- 清掃: `python3 -B <session>/files/exercise-runtime-validation/teardown.py`成功。専用DBの7表0件、専用container/network/tmpfs除去、所有するapplicationコピー/専用cache除去、検証ページabout:blankを確認した。利用者アカウント/保存コードと共有cacheは清掃しない。report/checkpointはT029完了、31件中29完了/2保留へ更新し、YAMLと件数整合を確認した。

#### 第98版の先行バッチ結果（T025〜T028）

- 名前変更/移動の操作メニューと共通モーダルを本実装に接続した。移動先はuploadと共通の反復型フォルダツリーを使用し、自己/子孫は候補から除外、サーバーでも拒否する。フォルダ移動時は新しい名前を入力でき、衝突では入力を残す。未保存コード/保存基準/実行結果/IDを維持したままPath/タブ/ツリーを更新し、変更なしは版を増やさない。
- DAOは本人/状態/期待版を既存トランザクションで検証し、対象配下（独立してごみ箱にある項目も含む）のパスを先に検証してから一括更新する。実行記録とのID関連、保存済みコード、個別ごみ箱状態を維持する。根のパスだけでなく、子孫だけが1000文字超になる移動も全体拒否した。旧ごみ箱の名前予約はT029まで残るため、新しい名前再利用は提供済みと扱わない。
- 長い名前のgrid列/子要素を収容し、深い階層では子ツリーの最小幅を残して横スクロールをツリー内部に限定した。専用実データの16階層・連続英字255文字と、日本語255文字file/folderで省略表示とメニュー幅を測定。1280/900/375pxでページ横overflowなし、深い名前幅134/216/134px（いずれも省略）、メニュー幅35pxを保持した。プロトタイプにも同じ階層収容を反映した。
- フォルダsuffixは初期HTMLからhidden/d-none、file→folder切替でも両方の状態を更新する。フォルダ案内/ドット拒否は「使えない文字：. / \\」「例：『授業1』『練習用』」へ変更。資産URLをこの演習画面だけ第98版へ更新し、旧URLで読み込んだCSS/JSの保持を避ける。利用者の旧表示がキャッシュ由来だったとは断定しない。
- TDD: 未実装rename/moveによるcompileTestJava失敗を確認後に実装。初回専用Java48成功。最終コマンドは`docker compose -f <session>/files/exercise-runtime-validation/compose.yaml exec -T app gradle test --rerun-tasks --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests control.student.PythonRunnerClientTest --tests entity.ExerciseDownloadTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseUploadTest --tests entity.ExerciseTreeTest --tests servlet.student.ExerciseUploadFormTest --tests servlet.student.ExerciseFormTest war --no-daemon --console=plain --warning-mode all`、**48成功/失敗0/skip0、WAR成功**。HTTP fixture作成後の最終DB試験では専用DBだけを空にして空状態前提を守った。
- `python3 -B -m unittest -v test_organize`は最終**1成功/失敗0**。POST rename/move200、親/子孫/不正文字/種類400、他人404/CSRF403/古い版409、同名409、No-op版不変、明示ルート移動、閲覧専用400、ごみ箱404を確認。初回のブラウザーroot移動400は空文字のoptionalId変換が原因で、明示ルート入力だけnullへ変換し、mutableなroot移動をHTTP試験へ追加して成功した。reload直後の準備前HTTP失敗はHTTP200待ち後に再試験した。
- ブラウザーではfile名前変更/移動、親folderルート移動/名前変更後の子Path更新、未保存コードと保存済み出力の保持、同document識別子、folderモーダルsuffix非表示とドット拒否、同名409の入力/版保持を確認した。ただし統合ブラウザーはvisibilityState=hiddenで、クリック安定待ち・Bootstrap fade完了が進まずタイムアウトした。DOMイベントで操作し、検証ページだけfadeを外して状態/実HTTP保存を確認した。通常の可視画面でのクリック/モーダルアニメーションを確認したことにはしない。ユーザーの元の表示問題の手動再確認も必要。
- ローカル`docker compose exec -T app gradle test --tests entity.ExerciseTreeTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseDownloadTest --tests servlet.student.ExerciseFormTest --tests servlet.student.ExerciseUploadFormTest --tests entity.ExerciseUploadTest war appRestart --no-daemon --console=plain --warning-mode all`は**22成功/失敗0/skip0、WAR/reload成功、login200**。DBのマイグレーション追加/利用者データ更新は本先行バッチで行わない。専用DB主要7表0件後にcontainer/network/tmpfsを除去、applicationコピー/合成fixtureも除去する。
- T029〜T031は未完了。単一ルート化、upload衝突解決、ごみ箱内名前再利用/同名別名復元を実装済みと報告しない。次は保存方式/移行の専用fixtureから開始する。今回の仕様承認と先行バッチの成功を第98版全体の完了とみなさない。

#### 第98版のごみ箱基盤バッチ結果（T029のREQ-EX-015部分）

- V13を追加し、`trash_root_entry_id`と通常項目のみの`active_path_hash`へ一意制約を分離した。ごみ箱先頭と当時有効な子孫は同じ削除単位となり、先に個別削除した項目は独立単位を維持する。通常の作成/upload/名前変更/移動はごみ箱内の名前を再利用できる。既存照合順序での有効項目同名は禁止し、上書き/暗黙の復元・結合はしない。
- 既存V12データ（親ごみ箱/非表示active子/先行して個別削除したfolderと子/完全削除祖先下のactive子/有効file/旧ドットfolder/配信由来領域/実行記録）を専用DBへ投入してV13を適用した。ID/親/名前/パス/コード/状態/元日時/領域所有者/配信元/版/実行関連のJSON照合は完全一致。削除単位と通常索引からの除外、同名の新規親と子の挿入を実際のDBで確認した。旧migrationは変更しない。根から辿れない階層は新列追加前のguardで拒否する。領域統合はこのmigrationでは行わない。
- restoreは元の場所・任意の別名を受け取り、元の親が復元不能の場合のみ明示的な別folder/空文字root指定を許可する。親がごみ箱なら親を先に復元するよう通知し、親も自動復元しない。全子孫パスを先に検証してから元の親子関係を維持して更新し、同じ削除単位の項目だけを戻す。独立ごみ箱/完全削除/保存コード/実行記録を保持し、版は一回だけ増やす。取消・同名・不正名・不正対象・版競合・子孫1000文字超過では全体不変を検証した。
  - 元の親の完全削除状態に加え、専用fixtureだけで親行自体の欠落も再現した。復元可能な削除単位の子孫を取得時の祖先整合エラーで隠さず、明示root指定でfolderとchildをまとめて復元し、ID/親子/パスを保持できることを同DB試験で確認した。通常のFK制約や利用者DBは緩めない。
- 本実装は既存の名前変更/移動モーダルと階層選択を復元にも共有する。ごみ箱の件数は削除単位数、元の場所/削除日時/親復元案内を表示する。復元と無関係な項目のごみ箱移動は同documentのままコード/結果を保持する。編集中項目を含む削除は、一つの共通feedback確認内に未保存コード消失の警告を含め、取消では保持、成功時のみ編集選択を解除する。連続した2確認のfade競合を避け、画面独自のconfirmは追加しない。名前衝突`name_conflict`は別名入力の案内、版衝突`exercise_conflict`は再読込の案内と区別した。
- TDDは未実装restore引数/削除単位DTOのcompileTestJava失敗から開始。`python3 -B <session>/files/exercise-runtime-validation/setup.py`の最終再実行は populated V12→V13 fixture、専用関連**51成功/失敗0/skip0、WAR・HTTP200**。最終Javaコマンドは`docker compose -f <session>/files/exercise-runtime-validation/compose.yaml exec -T app gradle test --rerun-tasks --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests control.student.PythonRunnerClientTest --tests entity.ExerciseDownloadTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseUploadTest --tests entity.ExerciseTreeTest --tests servlet.student.ExerciseUploadFormTest --tests servlet.student.ExerciseFormTest war appRestart --no-daemon --console=plain --warning-mode all`で同じ**51/0/0**。HTTP合成fixture投入前に専用DBだけを空にして空状態前提を守った。
- `python3 -B -m unittest -v test_trash test_organize`は最終**2成功/失敗0**。trash/restore/rename/move200、作成201、名前衝突409/name_conflict、旧版409/exercise_conflict、不正名/元の親ごみ箱/復元先未指定400、他人/非有効親404、CSRF403、コード/実行ID保持を確認した。HTTP試験は既存runnerによる実行/DB保存も通す。
- 今回は共有ブラウザーの可視ページで通常ポインターclick/fillとBootstrap fadeを変更せず検証できた。復元衝突後の入力/版保持、別名親まとめ復元、独立子のごみ箱残留、元の親復元不能時の明示root復元が成功。dirtyコード/保存済み結果/同documentを保持してfile名前変更/移動と親folder名前変更、file→folderモーダルsuffix非表示を確認した。取消ではdirty保持、編集中file削除の警告確認後は選択/編集解除・保存/実行無効化、無関係folder削除はdirty/結果保持も確認した。先行バッチのポインター未確認はこれらの経路について解消したが、利用者自身の元の画面やOSファイル選択は別の未確認事項である。
- 更新後の日本語folder255文字/連続英字file255文字の名前幅は1280/900/375pxでそれぞれ136/476/207pxと145/485/172px。内容幅4080/2364pxに対し全てellipsis、操作ボタン35.2px、document幅=viewport幅を確認した。16階層の先行検証は上記記録のまま保持する。
- ローカル`docker compose exec -T app gradle flywayMigrate flywayValidate test --tests entity.ExerciseTreeTest --tests entity.StudentExerciseInputTest --tests entity.ExerciseDownloadTest --tests servlet.student.ExerciseFormTest --tests servlet.student.ExerciseUploadFormTest --tests entity.ExerciseUploadTest war appRestart --no-daemon --console=plain --warning-mode all`はV13適用/validate・**22成功/失敗0/skip0、WAR/reload成功**。最終UI修正後も同test selectorの`war appRestart`成功（testはUP-TO-DATE）、XML22/0/0・login HTTP200を確認した。
  - 親行欠落の追加検証後も最終専用51/0/0・HTTP2成功を再確認。最後のローカル反映は共有Gradle fileContent cache lockで一度失敗したため、所有する専用環境を終了してから同コマンドを再実行し22/0/0・WAR/reloadを成功させた。共有lockを削除せず他プロセスを停止しない。後片付けでは専用DB主要7表0件、専用containers/network/tmpfs除去、applicationコピー除去、検証ページabout:blankを確認した。利用者アカウント/コードと共有cacheは清掃しない。
- T029は単一ルート/保存先統合を残すため未完了。T030の選択式upload衝突解決、T031の全追加範囲最終検証も未完了。ごみ箱の部分完了を生徒向け機能全体の完成と扱わない。完全削除/進捗制御/教師配信は追加しない。

### Phase 4: 検証と引継ぎ

**Plan 4.1 / REQ-EX-001〜009:** 正常系・境界・課題データ非更新を実際のDB/ブラウザーで確認し、結果を永続記録する。

- [x] T012 [Plan:4.1] src/test/java/control/student/StudentExerciseDatabaseTest.javaを既存の専用DB/明示ゲート方式に合わせて作成し、所有者・競合・ごみ箱/復元・実行保存と課題データ非更新を検証する。下記ブラウザー/runner検証を実施し、失敗/skip/未確認を分けて本書へ記録する。
- [x] T013 [Plan:4.1] docs/system-configuration/implementation-roadmap.md・docs/screen-flow-diagram.md・本書・AGENTS.mdを実際の結果で更新する。進捗制御/追加操作/教師配信は未実装として残す。

依存関係: T001→T002/T003、T002/T003→T004→T005→T006→T007→T008→T009→T010→T011→T012→T013。同じDAO/Controlを変更する作業は順番に行う。T001が解消できない設計差分を発見した場合、後続は開始しない。

## Requirement Mapping

| REQ ID | 既存仕様への対応・受入条件 | Plan Items | Implementation Evidence（予定） |
|---|---|---|---|
| REQ-EX-001 | 本人生徒だけが所有領域/項目/実行記録を取得・更新でき、同意状態で学習を妨げない | 1.1,2.1,3.1,3.6,3.7,4.1 | StudentExerciseDao/Control/Servlet、認証済み境界確認 |
| REQ-EX-002 | フォルダ/ファイル作成・アップロードとツリーのDB再読込。生徒ID表示ルートと新規.py自動追加。配信由来でも所有者は生徒 | 1.1,2.1,3.5,3.6,3.7,4.1 | StudentExerciseInput/Upload/Dao/Servlet、親/正規化後パス制約・ルート表示確認 |
| REQ-EX-003 | 最新保存内容を復元し、未保存差分・競合を失わない。GET/実行で保存しない | 1.1,2.1,3.2,3.5,3.6,3.7,4.1 | ExerciseSaveResult/Tree、版付き保存、画面内更新/再ログイン・既存名維持確認 |
| REQ-EX-004 | ごみ箱移動と本人復元。祖先/子状態を守り物理削除しない | 1.1,2.1,3.2,4.1 | StudentExerciseDao/Control、削除/復元確認 |
| REQ-EX-005 | 隔離Python実行と演習ファイルに紐づくDB結果。入出力/エラーを表示 | 1.1,3.1,3.2,3.7,4.1 | 新規migration、runCode、code_executions再読込 |
| REQ-EX-006 | 両エディターでアカウント別設定を共有し、所定の表示/高さを維持 | 1.1,3.2,3.7,4.1 | 既存Preferences API、exercise.js、双方再読込 |
| REQ-EX-007 | 課題提出/参加/30秒ログ/評価へ演習データを書き込まない | 1.1,2.1,3.1,3.6,4.1 | 演習専用DAO、課題テーブル不変確認 |
| REQ-EX-008 | 共通feedbackで成功/失敗を明示し、CSRF/状態/入力をサーバー検証 | 1.1,2.1,3.1,3.2,3.5,3.6,3.7,4.1 | Servlet/JSP、共通feedback、正規化後境界/異常系確認 |
| REQ-EX-009 | ホーム/共通ヘッダーから実演習へ遷移。空状態と未対象操作を正直に表示 | 1.1,3.2,3.5,3.6,3.7,4.1 | home.jsp/navigation.jspf/exercise.jsp、空状態の生徒IDルート/ごみ箱/導線確認 |

## 第93版: 設計復元・対話型ターミナルの実装結果（2026-10-03）

- **T014/T015完了**。非結果部分はプロトタイプの対象CSSを抽出し、青緑/青のbody/hero/作成ボタン/実行ボタン、Path/ファイルタブ、SVG付きツリー/折りたたみ/省略メニュー、設定ギア/言語表示、560px・420pxの編集面を復元した。演習領域選択/空状態/ごみ箱は実DB用の追加。操作メニューがカードに切られないようツリーだけoverflowをvisibleにした。未対象の名前変更/移動/upload/downloadは追加しない。結果カードは旧プロトタイプではなく、課題本実装から抽出した共通`execution-terminal.jspf`/`execution-terminal.css`を両画面で使う。業務JSと記録先は分離した。
- WebのPOST `/student/exercise/run`は事前stdinなしのsession開始。GET `/student/exercise/session`、POST `/student/exercise/session/input`と`/cancel`を追加した。結果は`{update: InteractiveExecutionUpdate, executionId?: number}`で、未完了時はexecutionIdを省略する。inputは`{status:"accepted"}`、cancelは`{status:"cancel_requested"}`。POSTはCSRFとbounded form、sessionは本人/演習/項目に限定し、入力/取得/停止でも現在のDBアカウント/領域/祖先/項目を再検証する。完了時は対象を再検証し、synchronizedなsessionで一度だけ保存する。ブラウザー取得停止後も90秒まで監視する。runnerの入力合計上限拒否は413、終了済み入力は409、session消失は404として理由を明示する。
- 最新保存結果は標準入力/出力/エラーと記録日時を表示する。イベントの実行時の交互順序はDBにないため、復元不能である旨を表示し、入力sessionは再開しない。停止はDBの`failed`+`execution_cancelled`を「実行停止」と表示する。runnerのunavailableを成功/保存済みに捏造しない。
- TDDでは未実装session API/型と構造化runnerエラーでcompileTestJavaが期待どおり失敗。最終コマンド: `docker compose exec -T -e DB_NAME=ppe_exercise_test_b1016b19_20261003_terminal -e EXERCISE_DB_TEST=true app gradle test --tests control.student.PythonRunnerClientTest --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests servlet.student.ExerciseFormTest --tests entity.StudentExerciseInputTest --tests entity.PythonExecutionInputTest --tests entity.EditorPreferencesTest --tests 'control.auth.*Test' --tests control.evaluation.EvaluationWorkerTest war --warning-mode all`。**49件成功・失敗0・skip0、WAR成功**。新sessionの本人/別ファイル/別領域、停止中アカウント/初回変更/閲覧専用/ごみ箱祖先、実runner入出力/停止、監視保存/重複防止、コード/版/課題テーブル不変、runnerエラー理由保持を検証した。`git diff --check`成功。
- 合成アカウントの実HTTPではpage/create/save/trash/restore/run/session/input/cancelの2xx、session操作の他人403、CSRF403、1行制約400/入力413/cursor400、終端JSON/実DB一回保存/再読込を確認した。変更後JavaScriptはブラウザーの`new Function`で構文確認（hostにnodeなし）。
- ブラウザーでは演習の日本語対話入力と`>>>`の青表示、stderrの赤表示/ValueError、停止/保存状態不変、再読込で保存済み表示と入力無効、ツリー折りたたみ/操作メニュー、作成エラー時の名前保持、共通設定の13px保存と課題側反映、課題側実対話実行/保存の回帰を確認した。1280pxはツリー356px/編集736px・editor560px、900pxでhero1列、375pxはeditor420px/縦toolbar/横overflowなし。共通テンプレート・共通feedbackを利用する。
- 503検証は区別する: 実停止時にrunnerからHTTP503を1回観測（cleanup原因は未特定）。構造化理由を通知し、terminalのunavailableでは取得失敗を表示して監視再試行ループを終了する実装を追加した。再検証の実停止/DB保存は成功。別の**注入**`runner_cleanup_failed`503では未保存入力保持/取得失敗表示と、その後の実runner完了をサーバー監視がDB保存したことを確認した。注入を実サービス故障検証に数えない。
- T012に残す: 実時間/出力上限、サービス停止/DB障害、全認証/閲覧状態のHTTPと再ログイン・提出確定までの回帰、および観測したcleanup障害の発生原因調査。T013はこれらの最終判定後。管理者統合/工程8保留/IC-010は変更しない。
- 後片付け: 合成学校26/利用者71,72/課題と割当・演習・実行履歴・設定・ログを作成IDだけ削除し、各対象の残存0件を確認。課題作成者への参照を先に削除する順序へ修正して完了した。専用DB内の利用者/領域/実行0件を確認後、DBとgrantを除去し、双方0件を確認した。ブラウザーはログアウトし、検証用script/stateを削除した。

## 第94版: ダウンロードと不足していた配置の修正（2026-10-03）

- 前回の「設計復元完了」は、旧初回範囲による非表示を前提としており、設計の上部4ボタン/編集3ボタン/フォルダ追加メニューが欠けていた。ユーザー指摘を受け再比較し、第94版の追加範囲と未実装準備中表示を承認後に修正した。単体は未保存コードも対象、一括は保存済み内容のみという使い分けを確認済み。名前変更・移動・アップロードの動作は追加しない。
- `ExerciseDownload`でUTF-8 ZIPを標準Javaライブラリから生成し、Servletへストリーム出力する。本人アカウント/領域の参照検証後、有効項目と有効祖先のみを抽出する。ZIPの相対パスを事前検証し、空フォルダを保持する。DB/版/課題記録を更新しない。単体は実エディターのBlobと実ファイル名で取得し、保存操作を呼ばない。新規依存やDB変更はない。
- 最終コマンド: `docker compose exec -T -e DB_NAME=ppe_exercise_test_b1016b19_20261003_download -e EXERCISE_DB_TEST=true app gradle test --tests entity.ExerciseDownloadTest --tests control.student.StudentExerciseDatabaseTest --tests control.student.PythonRunnerClientTest --tests servlet.student.ExerciseFormTest --tests entity.StudentExerciseInputTest war --warning-mode all`。**34件成功、失敗0・skip0、WAR成功**。ZIP2/演習DB18/runner1/form5/入力8。これは今回の最小関連セットであり、前回49件の回帰記録に追加する。`git diff --check`成功。
- 実HTTPのGET downloadで200 ZIP/attachment・日本語名と保存済みUTF-8内容/空フォルダ/ごみ箱祖先除外、本人以外404、無効ID400、空領域400 download_empty、停止アカウント403、閲覧専用200を確認した。取得前後の版・実行/課題記録件数は不変。hot reloadが失敗して旧ServletのエラーMIMEが残ったため、`docker compose exec -T app gradle appRestart --console=plain --warning-mode all`を実行し、ログインHTTP200と現在のServletのJSONエラー/ZIPを再確認した。
- ブラウザーで単体の生成Blobが`print('未保存ダウンロード')`/`日本語.py`であること、一括の生成Blobが`exercise-4.zip`で、その**実Blobのバイト列**をZIPとして解析し`空/`・`練習/`・`練習/日本語.py`だけ、コードは保存済み内容であることを確認した。保存状態は未保存のまま。統合ブラウザーではPlaywright downloadイベントが発火しなかったため、Blob/ファイル名・リンク起動と共通通知までを検証した。OS保存先への書込み/保存ダイアログは未確認であり、取得ファイルの保存完了を主張しない。
- 1280pxは作成ボタン2個の1行目/準備中uploadと一括の2行目を座標で確認し、編集上部はダウンロード→保存→実行の同一行。375pxは上部2列2行/編集3ボタン縦並び、editor420px、横overflowなし。900pxはhero1列/toolbaroverflowなし。フォルダのファイル追加から作成先がそのフォルダになること、移動/uploadが無効のままであることを確認。共通テンプレート/feedbackを利用し、注入503の一括失敗でもコード/未保存状態を保持した。構文確認はブラウザーの`new Function`で成功。
- T012/T013と前回runner cleanup原因調査は継続して未完了。実サービス停止/DB障害・最終提出回帰は今回も未実施。
- 後片付け: 合成学校27/利用者73,74の設定/認証履歴/演習項目/領域を作成IDだけ削除し、対象利用者/学校/領域の残存0を確認した。専用DB内の利用者/領域/実行0件を確認後、DB/grantを削除し双方0件を確認した。ブラウザー検証用捕捉処理を解除してログアウトし、検証用script/stateは削除した。

### Chromeでの実ダウンロード・保存と表示の追加確認（2026-10-03）

- 統合ブラウザーのイベント取得制約を避け、インストール済みChrome 154.0.8037.97をPlaywrightの独立したheadlessコンテキストで起動した。通常のボタンクリック→downloadイベント→failureなし→ブラウザーが保存した一時ファイルの存在/内容→指定したセッション内保存先への`save_as`と再読込まで成功。日常利用のChromeプロファイルやDownloadsには触れていない。
- 単体`日本語.py`の実保存ファイルは未保存の日本語コードとUTF-8バイト列まで一致。一括`exercise-<id>.zip`の実保存ファイルは`練習/`・`練習/日本語.py`・`空/`だけで、コードはDB保存済み内容、日本語/空フォルダ/構造を保持し、ごみ箱の親子を含まず、ZIP整合検査も成功した。ダウンロード前後の版/領域状態/DBコード/本人の実行数/課題参加数は不変、画面の未保存状態も維持した。
- 初回スクリーンショットで、ツリーの固定3rem高ボタンから折り返し文字がはみ出していた。色/2列2行/最小3rem/文言を維持し、高さをauto・行内をstretchへ変更した。1280/900/375pxで各ボタンの実テキスト領域がボタン内に収まり、行内の高さが同じで、横overflowがないことを測定した。編集上部のダウンロード→保存→実行、desktop560px/mobile420pxも再確認した。共通テンプレート/feedbackは変更せず、準備中uploadは無効のまま。
- **OS上の実ファイル書込みは確認済み**。headless実行のため、利用者がmacOSのネイティブ保存ダイアログで任意の保存先を選ぶ操作は未確認。この手動操作と、以前未確認だったディスク書込みを区別する。追加の`docker compose exec -T app gradle war --warning-mode all`は成功。CSSのみの修正であり、既存の34件/49件のテスト記録を更新件数として水増ししない。T012/T013・runner cleanup原因調査は未完了のまま。
- 後片付け: 各試行の合成学校28〜30/利用者75〜77をそれぞれ作成ID指定で削除し、利用者/学校/演習/実行の残存0件を確認した。最後の専用ブラウザーはログアウト/終了。検証用ダウンロード・画像・script/state・Python仮想環境は削除した。

## 検証計画

### T012追加検証（2026-10-03 22:56〜23:21 JST）

- **最終判定FAIL、T012/T013は未完了**。本実装コードは変更せず、独立したアプリコピー・tmpfs MySQL・実Docker runner・Chromeで、3つの新規実行可能テストファイルの9フローを実行した。最終`python -m unittest discover -s <session>/files/exercise-runtime-validation -p 'test_*.py' -v`は**exit1、7成功/2失敗/skip0、278.845秒**。再現用テスト/compose/setup/teardownとruntime-validation-reportはセッション成果物に保持する。
- 専用環境のV1〜V12移行/validateと正規Gradle `test war`はexit0。**81成功/失敗0/skip12**。skipは既存の評価DB6/Gemini smoke1/学校DB4/同意DB1の明示ゲートによるもので、その機能の検証成功ではない。Docker利用可、host Nodeなし。Python Playwright1.63.0とインストール済みChrome154を独立コンテキストで使用し、ブラウザー検証を省略しなかった。
- 成功: 未認証GET/POSTのログインへの302、不正パスワード/CSRF、教師403、レベル2の強制変更中GET/POST/downloadの変更画面への302、本人以外404、停止アカウント403。日本語コードを保存→ログアウト→再ログイン→HTML/DB読戻しと版の復元を確認。completed/expired/needs_reviewは参照200・作成/保存/ごみ箱/復元/実行400 invalid_request、archivedは404で、拒否前後の版不変を確認した。条件未定義の状態を自由編集へ変更しない。
- 成功: **専用runnerの実停止**でrun503 execution_unavailable・共通feedback・コード/未保存状態保持・操作再有効化を確認。再起動後のhealth/DNS復旧を待ち実行成功。**専用DBユーザーの実権限喪失と既存接続終了**でsave503 storage_unavailable・コード/版不変を確認し、権限復旧後の保存/再読込に成功。応答のモック注入ではない。ただし物理DB停止や通信blackholeの検証ではなく、DBアクセス不能として範囲を限定する。
- 成功: 課題画面の保存→DB/再読込、対話入力/出力、実テスト1/1一致→提出確定→提出コード/テスト結果のDB読戻し→評価画面200。演習の版/状態/コード/実行数は不変。評価生成・アンケートの保留解除ではない。8回の実停止/他人拒否と実行停止の一回保存、stderr32KiB上限/切詰めと一回保存も成功。活動を継続するコードの全体60秒制限はexecution_timeout/timed_outで保存され、測定した実行時間は61195〜61217msだった。
- **失敗1・原因特定済み**: 実30秒無通信の終了時にrunner_cleanup_failed503となり、timed_out結果を保存できなかった。実`docker rm --force`のstderrは`removal of container ... is already in progress`、rc1、0.039945秒/再現0.071689秒。`docker run --rm`の自動削除と明示削除が競合し、runnerが「削除処理中」を障害にしている。2秒タイムアウトではなく、残留コンテナも0。修正は削除応答の無条件成功扱いではなく、時間制限内に実際の消失を確認する方式を検討する。
- **失敗2・境界差を確認**: 日本語stdoutを生バイト32768で切ると文字途中の末尾をU+FFFDへ置換し、返却文字列とDB保存値をUTF-8へ戻したサイズが32769になる。生バイトの捕捉上限は守っているが、返却/保存値にも32KiBを要求する追加境界テストは失敗。切詰め時の文字境界と不正UTF-8処理の方針/実装を解決し、テストを緩めて成功扱いしない。
- 次: 上記2件を修正/再検証した後、CPU4秒・入力合計上限等の残る実制限/セッション状態境界を確認してT012最終判定、続いてT013。過去の49件/34件成功は今回の実E2E失敗を打ち消さない。検証側のstdin/TTY、MySQL表示文字コード、既存HTTP契約の期待値、runner起動待ち、専用DBのgrant形式の修正履歴は[エラーレポート](../error-report.md)参照。
- 後片付け: DB名を厳密検証した専用DBだけから合成行を削除し、users/schools/演習領域/項目/実行/tasks/submissionsの0件を確認。専用3コンテナ/ネットワーク・tmpfs DB、アプリコピー/ビルド、一時venvを除去。ブラウザーは独立contextを閉じ、既存localhost8080はHTTP200・既存利用者や共有DB/runnerには変更なし。管理者統合・工程8保留・IC-010は維持した。

### T012修正後の最終判定・T013引継ぎ（2026-10-03 23:25以降）

- **初回スライスPASS、T012/T013完了**。上の7成功/2失敗は修正前の履歴として保持する。共有runnerの同期/対話双方でcleanupと出力捕捉を共通化した。既存の時間/CPU/生バイト上限、隔離設定、業務記録先・画面構成は変えない。
- cleanupは削除競合だけで失敗を無視せず、元の2秒期限内で`docker inspect`による対象コンテナの消失を確認する。残存/inspect異常/daemon不可用/期限超過はログ付き失敗。実CLIは小文字`no such object`を返すため大小文字を正規化して判定する。最終実試験でも自動削除との競合rc1を0.063145秒で観測し、その後0.025551秒のinspectで消失を確認、503なしで結果を保存した。
- 出力は共通BoundedOutputで生バイトと表示テキストのUTF-8バイトをそれぞれ32KiB以内に制限し、文字途中は含めない。上限で切れた文字は補完せず、元々の不正UTF-8/実EOFの不完全文字は従来どおりU+FFFDへ置換した上で同じ上限を守る。対話イベント/最終snapshot/DB保存が同じ文字列になる。日本語実出力は`あ`10922文字=32766バイト、stderrは32768バイトで、一度だけ保存された。
- 入力合計検証でPythonRunnerClientがrunnerの409/413を一般IOExceptionにしていた欠落もTDDで修正。構造化理由を保持し、演習側の既存413 input_too_large/409 execution_not_running通知へ接続した。8KiBちょうど（改行込み）の実入力→次の入力413→実終了後入力409と、DB入力8192バイト/一回保存を確認。課題側の業務状態/保存処理は変更しない。
- 検証コマンド: `cd containers/python-runner && python3 -B -m unittest test_runner -v`は**17成功/失敗0/skip0・exit0**。先行テストで旧出力境界/未実装cleanupの失敗、実CLI小文字の再現失敗を確認してから修正した。既存のテストプロセスpipe未closeのResourceWarningは残り、成功判定と区別して記録する。
- 専用composeでmigration用rootの`gradle flywayMigrate flywayValidate`を先に実行し、通常アプリDBユーザーの`app gradle test --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests control.student.PythonRunnerClientTest --tests entity.ExerciseDownloadTest --tests entity.StudentExerciseInputTest --tests servlet.student.ExerciseFormTest war --console=plain --warning-mode all`を分けて実行した。**36成功/失敗0/skip0、WAR成功・exit0**。V1〜V12を専用DBへ移行し、既存利用者DBは変更しない。
- 最終`<session>/files/exercise-runtime-validation/browser-venv/bin/python -B -m unittest discover -s <session>/files/exercise-runtime-validation -p 'test_*.py' -v`は**13成功/失敗0/skip0・exit0、276.678秒**。前回9フローにCPU4秒（実CPUサンプル3秒台→exit137、約8秒wall）、入力合計、ネットワーク/ルートへの書込み不可、実行中の現在アカウント/領域/ごみ箱/他人/教師/未認証のHTTP再認可を追加。30秒無通信はinput_timeout/timed_outで約31253ms、全体60秒はexecution_timeout/timed_outで約61秒、実停止8回とも一回保存を確認した。
- ブラウザーの31秒編集でPOSTが発生せず、DBコードと課題ログ等が不変であることを追加確認。実runner停止・専用DB権限喪失/接続終了の503通知/未保存保持/復旧後の保存、再ログイン復元、課題側の対話実行/保存/入出力チェック/提出確定/DB読戻しも再確認した。共通テンプレート/feedbackと前回のレスポンシブ確認は維持。注入ではなく実アクセス不能だが、物理DB停止/通信blackhole・全資源枯渇の認証ではない。
- ローカル反映: `docker compose up -d --build --no-deps python-runner`でrunnerだけ再ビルドしhealth200。専用環境と共有Gradleキャッシュのロック競合があったため、専用環境を終了してから`docker compose exec -T app gradle test --tests control.student.PythonRunnerClientTest war appRestart --no-daemon --console=plain --warning-mode all`を実行し、**2成功・WAR/restart成功・exit0**、ログイン200を確認。ローカルrunnerでも実日本語出力32766バイト/イベント一致/終了と実行コンテナ残存0を確認した。
- 後片付け: 専用DB名を照合し全合成行を削除、7主要表0件確認後、専用3コンテナ/ネットワーク/tmpfs DBを除去。アプリコピー/ビルド/一時venvも除去。再現テスト・compose・setup/teardown・報告はセッション成果物に保持。既存ユーザー/学校/データの変更や新しいmigrationはない。
- **後続範囲**: 次はロードマップ工程10の教師課題/プロンプト/配信。演習の完了/期限/要再確認の生成、名前変更/移動/upload/完全削除、教師配信/再配信、全体の本番資源制限認証は今回の完了範囲ではない。macOSネイティブ保存ダイアログの利用者操作は手動確認待ち。工程7/8の各残件・評価/アンケート手動確認・管理者統合の工程12保留・IC-010保留は引き継ぐ。

- appType: server-rendered HTML。既存のGradle/JUnit、専用MySQL、隔離Python runner、共有ブラウザーツールを使う。計画作成時点では以下を実行していない。
- 正常系: 空領域→明示作成→編集→保存→再ログイン/再読込で同じ内容。フォルダごみ箱移動/復元、子の独立削除状態維持、コード実行の成功/構文エラーとDB結果再読込。
- 状態: 未保存切替/離脱、実行だけでは保存しない、競合拒否で入力保持、未定義のcompleted/expired/needs_reviewの編集拒否。GETがDB更新しないこと。
- 境界: 生徒2名で領域/項目/親/実行参照の入替、教師/未ログイン/強制パスワード変更中のアクセス、CSRF不正、folderをfileとして実行する要求を拒否する。
- 入力: DB境界の名前/パス、同照合順序パス重複、ごみ箱パス衝突、別領域親、不正パス、コード64 KiB/入力8 KiBの境界、UTF-8サイズを確認する。
- 分離: 作成/保存/実行後にcode_logs・submissions・task_participations・evaluationsの対象データが増加/更新していないこと、30秒以上編集しても定期ログが発生しないこと。
- runner/障害: タイムアウト・出力上限・ネットワーク遮断、runner不可用・DB保存失敗で成功扱いしない。既存課題実行/提出経路の回帰を確認する。
- 表示: 共通テンプレート/feedback、入出力/エラー切替、設定共有、1280px/375pxで横はみ出しなし、未対象のアップロード等を有効表示しない。
- 予定コマンド: `docker compose exec -T app gradle test --tests entity.StudentExerciseInputTest --tests control.student.StudentExerciseDatabaseTest war --warning-mode all`。DBテストは実装する専用DB名/明示ゲートを指定した実行に限る。通常実行のskipをDB確認済みと扱わない。まず対象検証を行い、共有処理変更で必要な場合のみ関連既存テスト/全体buildへ広げる。
- データ: 合成生徒2名・専用DB/演習/ファイルだけを作成し、作成したIDとDB/権限だけを後片付けする。既存test-student等の状態/パスワードは変えない。
- 環境不足: 専用DB/runnerが使えない場合はコンパイル・単体テストまでとし、DB/隔離実行を未確認として記録する。ブラウザー不可ならHTTP/DBを確認し、見た目・未保存確認は未確認に残す。代理確認でE2E完了にしない。

## 完了条件

- [x] T001〜T017とREQ-EX-001〜009の証拠が揃い、初回対象の仕様/状態/DB/画面が一致する。
- [x] 生徒本人の保存・実行・ごみ箱/復元がDB再読込と再ログイン後にも一致する。
- [x] 所有者境界・入力・競合・CSRF・障害を検証し、失敗を成功として表示しない。
- [x] 課題の定期コードログ・提出・評価を生成せず、共通設定/既存課題経路を壊さない。
- [x] 検証結果・後片付け・未確認事項・後続範囲を記録する。初回スライス完了を教師配信や授業演習全体の完了とは扱わない。

## 計画作成時の確認結果（2026-10-03）

- `git diff --check`: 成功。
- Python標準ライブラリによる構造/参照確認: 9要件、T001〜T013の連番、5計画項目へのタスク対応、計画内の参照先存在、セッション内の追跡checkpointとの要件/タスクID一致を確認。
- 未実施: Javaビルド/テスト、新規マイグレーション/DB適用、認証済み演習画面/runnerの実行確認。本実装前の計画・文書確認だけであり、上記タスク/完了条件は未完了。

## 初回基盤バッチの検証結果（2026-10-03）

- T001〜T003完了、T004〜T013は未実装。新規エンドポイントはまだ作成していないため、演習のHTTP/ブラウザー確認は未実施。上記の計画作成時の未実施記録とは時点を区別する。
- TDD: 型の作成前に`StudentExerciseInputTest`を実行し、未実装型によるコンパイル失敗を確認。初回のパス境界テストは親要素自体が255文字超だったためfixtureを修正。DB fixtureは学校必須のV11制約に従って修正した。
- 専用DBの移行: アプリユーザーでは既存V11のトリガー作成権限で失敗。今回作成した専用DBだけを再作成し、管理資格情報を標準入力経由で受け渡して`gradle flywayMigrate flywayValidate --warning-mode all`成功。権限/全体設定を緩和せず、テストはアプリユーザーで実施。
- `docker compose exec -T -e DB_NAME=ppe_exercise_test_b1016b19_20261003_v12 -e EXERCISE_DB_TEST=true app gradle test --tests entity.StudentExerciseInputTest --tests control.student.StudentExerciseSchemaTest war --warning-mode all`: 成功、9件成功・失敗0・skip0。UTF-8サイズ/Unicode名/パス上限/型/状態、旧4用途、演習用途の参照必須/FK、version初期値/更新/負値拒否、ごみ箱移動後の実行記録保持を確認。
- `docker compose exec -T app gradle flywayMigrate flywayValidate --warning-mode all`: ローカルDBで成功。V12成功行を確認し、利用者2件・演習領域0件・実行記録24件は移行前後で不変。既存実行のexercise_entry_idはすべてNULL。
- `docker compose exec -T app gradle test --tests entity.StudentExerciseInputTest --tests 'control.auth.*Test' --tests control.evaluation.EvaluationWorkerTest war --warning-mode all`: 成功、19件成功・失敗0・skip0。既知のWarPluginConvention非推奨警告のみ。
- 合成fixtureはトランザクションrollbackで利用者/演習/実行0件を確認。専用DBとそのスキーマだけの一時grantは削除/取り消し済み。既存テストアカウントの状態・パスワードは変更していない。
- 残件: T004の本人限定DAO/競合処理から再開する。DBのCHECK/FKだけではファイル種別・所有者・祖先状態を検証できないため、DAOで必ず追加検証する。UI/runner/認証済みE2Eを基盤テストの成功で完了扱いにしない。

## 保存・ツリーバッチの検証結果（2026-10-03、T004〜T006）

- 上記基盤バッチに続き、本人限定DAO/Controlを実装。アクティブ生徒・強制変更要否をDBでも検証し、本人行/領域行のロック、期待版、親項目の領域/種別/祖先状態を照合する。領域/項目/版を同一トランザクションで更新し、失敗はrollbackする。
- 初回領域作成の同時要求と同じ版の同時保存は、それぞれ1件のみ成功、他方は明示的なExerciseConflictExceptionで拒否する。別生徒/別領域の対象はExerciseNotFoundExceptionで区別し、Webでの404/409変換はT008で実装する。
- フォルダ移動時は対象フォルダだけをtrashedにし、子の個別状態を保持する。祖先がごみ箱/完全削除なら子の編集/参照を拒否し、復元は有効な親がある場合だけ許可する。完全削除項目とその配下は表示対象から除外する。異常な親/循環構造はSQLExceptionとして明示し、権限外の親を追跡しない。
- エディター設定は既存DAOを使う。同一トランザクション接続で取得できるoverloadを追加し、領域読込中に接続を二重取得しない。既存の単独取得メソッド/保存先は維持する。
- TDD初回: `docker compose exec -T app gradle test --tests control.student.StudentExerciseDatabaseTest --warning-mode all`は新規未実装型によるcompileTestJava失敗（期待した未実装確認）。DAO/Control実装後は成功。
- 最終コマンド: `docker compose exec -T -e DB_NAME=ppe_exercise_test_b1016b19_20261003_dao -e EXERCISE_DB_TEST=true app gradle test --tests entity.StudentExerciseInputTest --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests entity.EditorPreferencesTest --tests 'control.auth.*Test' --tests control.evaluation.EvaluationWorkerTest war --warning-mode all`。**34件成功・失敗0・skip0、WAR成功**（DAO10、スキーマ1、入力8、設定4、認証7、評価4）。
- 専用MySQLで空状態GET相当の非更新、日本語保存/再読込/パス、設定DB読込、別生徒/別領域/ファイル親拒否、名前照合順序の衝突（ごみ箱を含む）、古い版拒否、同時作成/保存、祖先非有効、個別ごみ箱状態維持、削除後の実行履歴保持、完了/期限切れ/要再確認の編集拒否、停止アカウント/強制変更中拒否、パス1000/1001境界・失敗後の版不変、破損階層の明示拒否を確認。課題参加/コードログ/提出/評価を作らないことも確認した。
- 合成データは作成IDだけを後片付けし、利用者・領域・項目・実行0件を確認。専用DB/スキーマgrantを削除/取り消し済み。ローカルDBの利用者データやパスワードは変更していない。
- 次はT007の実行処理。T008〜T011のHTTP/画面/導線、T012のrunner・認証済みブラウザー/E2E検証は未実施。T012はDAO側テストを先に作成しただけで完了扱いにしない。

## 実行・コード共有バッチの検証結果（2026-10-03、T007）

- ユーザー指示に従い、既存課題エディターの実行経路を参照し、PythonExecutionInputのUTF-8サイズ/NUL/未指定検証とPythonRunnerClient.executeTimedのrunner通信/経過時間計測を共有。課題エディターの同期実行・提出時の入出力チェックも同じ計測を使用する。対話型セッション・課題の保存/ログ/提出処理は変更しない。
- 授業演習は独自の不正サロゲート検証を維持する。既存課題入力の64 KiB/8 KiB・NUL拒否・エラー文言・従来Unicode許容条件は共有前と同じ。新しい依存関係やrunner設定は追加していない。
- 演習実行は入力/本人/領域状態/有効なfile/祖先を検証した後、DBトランザクションを終了してrunnerへ渡す。実行中にDBロックを保持せず、結果保存時に本人/領域/項目を再検証する。削除や停止等で対象が無効になった場合は明示的に失敗し、保存成功を返さない。
- 記録はcode_executionsのstudent_exercise用途だけに限定し、演習ファイル/実行者/実行時コード/入力/出力/エラー/切詰めフラグ/経過時間を保持。ファイル保存内容/領域version・保存状態、課題参加・30秒ログ・提出・評価は変更しない。runCodeは永続化済みexecutionIdとPythonExecutionResultを返す。画面への結果再読込は後続のWeb実装で接続する。
- TDDの初回は未実装runCode/実行結果型に加え、テストメソッドをヘルパー内に挿入した配置誤りでcompileTestJava失敗。共通executeTimedも最初に既存メソッド内へ入ってcompileJava失敗したため、両方をクラス直下へ移動後に再検証して解消。詳細はエラーレポート参照。
- 最終コマンド: `docker compose exec -T -e DB_NAME=ppe_exercise_test_b1016b19_20261003_run -e EXERCISE_DB_TEST=true app gradle test --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests entity.StudentExerciseInputTest --tests entity.PythonExecutionInputTest --tests entity.EditorPreferencesTest --tests 'control.auth.*Test' --tests control.evaluation.EvaluationWorkerTest war --warning-mode all`。**40件成功・失敗0・skip0、WAR成功**。
- 実隔離runnerで合成コードの日本語入出力とValueErrorを実行し、DB参照・用途・出力/入力/コード・時間と保存内容/版の不変を確認。実行前の別所有者/folder/祖先ごみ箱/過長コード拒否、runner障害の伝播、実行中ごみ箱移動後の保存拒否も確認。timeout/切詰めフラグ保存と未知の実行状態での保存失敗は注入結果によるテストであり、実runnerのtimeout/出力上限試験とは区別する。
- 合成データは作成IDのみ削除し、利用者/領域/実行0件を確認。専用DB/スキーマgrantも削除/取り消し済み。ローカルDBの利用者データは変更していない。
- 次はT008のServlet接続、続いてT009〜T011の演習画面/設定/導線。既存課題と演習の認証済みブラウザー回帰・HTTPの結果形・実runnerの制限/障害E2EはT012で実施し、現時点では未確認。

## Web・画面接続バッチの検証結果（2026-10-03、T008〜T011）

- StudentExerciseServletのGET/create/save/trash/restore/runを既存の@WebServlet方式で登録。web.xmlへの重複登録はしない。POSTはフォーム本文を256 KiBまで実際に読み込み、Content-Length未指定でも制限する。UTF-8不正・重複パラメーター・曖昧なID/欠落版を拒否し、CSRF/生徒認証と確定済み400/403/404/409/413/503を扱う。共通入力検証のサイズ超過は専用のIllegalArgumentException派生型にし、演習では413、既存課題の400扱いと文言は維持する。
- JSPは共通テンプレート/feedbackとDBデータを使用。階層表示・領域/ファイル選択・作成モーダル・保存・入力付き同期実行・入出力/エラー切替・ごみ箱/本人復元を接続。名前変更/移動/アップロード/ダウンロード/完全削除や進捗操作は表示しない。保存失敗/競合では入力を保持し、ファイル切替・共通リンク・ログアウトでは未保存確認を行う。実行中に保存済みにはしない。
- 本人・対象の有効性を確認して最新実行結果をDBから取得し、ファイル再読込で出力/エラー/終了状態/切詰めフラグを表示する。実行結果を課題ログから取得したり、仮データで補ったりしない。
- editor-settings.jspfとeditor-settings.jsを抽出し、課題/演習双方が既存Preferences APIと同じアカウント設定を使用。文字サイズ10〜24px、折返し、2/4スペース、3配色、初期設定復帰は共通化。範囲入力は保存済みの奇数pxも再現できるようstep=1とし、サーバーの既存範囲に合わせる。エディターCSSも再利用する。
- TDD初回は未実装ExerciseForm/サイズ例外でcompileTestJava失敗。最終テスト: `docker compose exec -T -e DB_NAME=ppe_exercise_test_b1016b19_20261003_web -e EXERCISE_DB_TEST=true app gradle test --tests control.student.StudentExerciseDatabaseTest --tests control.student.StudentExerciseSchemaTest --tests servlet.student.ExerciseFormTest --tests entity.StudentExerciseInputTest --tests entity.PythonExecutionInputTest --tests entity.EditorPreferencesTest --tests 'control.auth.*Test' --tests control.evaluation.EvaluationWorkerTest war --warning-mode all`。**45件成功・失敗0・skip0、WAR成功**。最終Web資産もWARへ再梱包した。
- 合成生徒2名/教師でHTTP検証: GET 200・初回領域非作成、create 201、save/trash/restore/run 200と正確なJSONキー、CSRF/教師403、別所有者/ごみ箱祖先404、初回重複/版競合409、欠落版400、64 KiB/8 KiB超過413。実runnerの日本語入出力→DB保存→HTML再読込、実行による版/保存コード不変、課題ログ/提出/参加/評価件数不変を確認。
- ブラウザー確認: 作成の入力不正400でモーダル/名前保持→修正して成功、保存→再読込、未保存確認のキャンセル、実409でコード保持、日本語実行/ValueErrorとエラータブ、ブラウザーで注入した503でコード保持、ごみ箱/復元。101行入力で高さ620px→2787pxに自動拡張。演習で13px/折返し/2スペース/高コントラストを保存→再読込→課題画面に反映、課題の初期設定復帰→演習で16px/暗い背景の反映を確認した。課題側の保存・対話実行も成功。検証後にホームと共通ヘッダーの実リンクを有効化し、ホームからの遷移を確認。
- 検証専用の学校/生徒/教師/課題/履歴は作成IDのみ削除し、課題テーブル件数が検証前と同じことを確認。専用テストDBの利用者/領域/実行0件を確認してDB削除・スキーマgrant取消済み。共有ブラウザーは合成アカウントからログアウト済み。既存利用者/パスワード/管理者UIは変更していない。
- **次はT012の残件**: 実runnerのtimeout/出力制限、実DB/runner障害によるHTTP503、未認証/強制変更中/閲覧専用状態のHTTP、再ログイン復元、課題提出までの回帰、狭幅表示。503のUI確認はブラウザーの注入応答であり、実サービス障害確認と混同しない。これらとT013の最終引継ぎまで工程9は完了扱いにしない。

## 共通部品の責務整理（2026-10-03、ユーザー承認）

- 共通の技術部品だけ共有し、課題/演習の業務処理は分離する。入力制約・Python runner通信/計測・アカウント設定は共通。保存/競合/状態/認可/DB記録は各Control/DAO、課題ログ/提出/評価は課題専用、ツリー/ごみ箱は演習専用とする。両Control間の呼び出しは追加しない。
- [code-editor.js](../../../src/main/webapp/js/student/shared/code-editor.js)はエディター生成・設定の検証/適用のみを担当し、設定フォーム・API・画面IDへ依存しない。[editor-settings.js](../../../src/main/webapp/js/student/shared/editor-settings.js)は渡されたフォーム要素内の入力と既存設定APIへの保存だけを担当する。変更値はcallbackで呼び出し元へ返し、画面側がエディターへ適用する。画面固有のreadOnlyなどは画面側で指定する。
- [共通CSS](../../../src/main/webapp/css/student/shared/code-editor.css)へファイルタブ・コード欄・CodeMirror配色・高さ/狭幅対応を移設。課題/演習はそれぞれ共通CSSを読み込み、授業演習が課題CSS全体を読み込む依存を除去した。ヒーロー/ツールバー/結果カード等の画面レイアウトは各画面CSSに保持する。共通コード欄の既存規則は文字列比較で不変を確認した。
- 検証: `docker compose exec -T app gradle test --tests entity.EditorPreferencesTest --tests entity.PythonExecutionInputTest war --warning-mode all`成功（6件成功・失敗/skip0、WAR成功）。ブラウザーで実CodeMirrorと共通部品を使用した独立fixtureを検証し、13px/折返し/2スペース/高コントラスト・readOnly、設定不正の通知と初期値、入力連続変更のdebounce、閉じる時の即時保存、初期値復帰、注入保存失敗の明示/次の保存成功、逐次リクエストを確認。
- 両画面CSSを独立に読み込み、1280px幅で高さ620px・101行2787px・高コントラスト背景、375px幅で高さ460px・ヒーロー1列/ツールバー縦配置を確認。両画面/共通JSの構文とJSP読み込み順も確認。最初の注入失敗確認は短い固定待機による検証側タイムアウトを結果条件待ちへ変更して成功した。fixtureは撤去し、DB/アカウント/業務処理は変更していない。
- 今回は構造の整理であり、認証済み業務E2Eを再実施した結果ではない。T012の実制限/実障害/状態/再ログイン/提出回帰と、実画面全体の狭幅確認は引き続き残す。
