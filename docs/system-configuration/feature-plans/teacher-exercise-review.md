# 授業演習コード確認 実装計画

## 改版履歴
| 日付 | 内容 |
|---|---|
| 2026-10-08 | 第153版とユーザーのプロトタイプ再現指示に基づく工程15の実装・受入計画 |
| 2026-10-08 | ユーザーの中断依頼により作業を停止。実装差分・検証済み範囲・再開時の残件を記録 |
| 2026-10-08 | 再開後に検索直後の詳細表示失敗の原因を特定・修正。ユーザーの再中断依頼により、修正後の受入前に停止 |
| 2026-10-08 | 再開後の最終受入を完了。検索後クリック・空フォルダ取得・中間幅表示を確認し、全体回帰/実DB/認証HTTPと8080反映を記録 |

## 正本
- [機能仕様](../../function-specification.md)「授業演習コード確認」、[実装契約](../implementation-contract.md) IC-005/006/008。
- [演習状態](../../state-rules/student/exercise-state-rules.md)、[DB定義](../../database-design/table-definitions.md)、[feedback](../../feedback-guideline.md)。
- [プロトタイプ](../../../screen-flow-diagram/webapp/WEB-INF/teacher/exercise/exercise.html)、同CSS/JSの一覧・全画面modal・ツリー・CodeMirror・取得操作を再現。固定データ/仮更新だけ置き換える。

## 実装と要件対応
| タスク | 要件 | 完了条件 |
|---|---|---|
| T001 | REQ-EXR-001 現在所属と認可 | DAO/Controlでロール/機能/学校・プロフィール/クラス所属を検証。担当外は404、失効は403 |
| T002 | REQ-EXR-002 保存データ参照 | 一覧集計と詳細が有効な最終保存項目に一致。統合済み/ごみ箱/削除祖先を除外、未作成0件、複数領域保持 |
| T003 | REQ-EXR-003 取得 | 単体/ZIP/一括/CSVを現在権限から生成。パス検証、重複なし、CSV最新同意/監査、業務データ不変 |
| T004 | REQ-EXR-004 画面 | 共通template/feedback、プロトタイプ同等UI、フィルター/ソート/更新、全画面詳細、読取専用コード、取得、空/障害表示 |
| T005 | REQ-EXR-005 受入 | 最小build/test、隔離DB/HTTP、通常/狭幅ブラウザー、8080接続、検証用runtime清掃 |

## API・状態・制限
- GET `/teacher/exercises`: JSP。`view=list|detail|csv|download|zip|bulk`。
- 詳細/単体/ZIP: 正の`studentId`と`classroomId`、単体は`entryId`。一覧/CSV/一括: `schoolId`, `classroomId`, `consent`, `search`, `sort`, `direction`。
- 機能権限は`exercise-code-review`。クラス権限表を新設しない。現在の学校権限を毎回検証する。
- 参照/ダウンロードでは業務テーブルを変更せずCSV監査のみ保存。DB migrationなし。
- ZIPは1要求32 MiB/10,000項目まで。超過は413、黙って省略しない。
- 未統合領域が複数なら`領域-ID`仮想フォルダで区別。通常の単一ルートは生徒ID直下に元パスを保持。
- CSSの色/余白/カード/全画面modalはプロトタイプを踏襲。狭幅の溢れ、キーボード操作、ロード中/失敗時表示は本実装に必要な補完。

## 検証結果
**状態: 実装・テスト・認証ブラウザー受入済み（2026-10-08）。8080へ反映済み。本番デプロイ、OS保存ダイアログ、全端末互換性はこの完了判定に含めない。以下の中断記録は当時の履歴であり、現在の残件ではない。**

### 最終受入（2026-10-08）
- 再開時HEADは`94bb049`。検索後クリックの修正と検証スクリプトはユーザーのコミットに含まれていた。デプロイ関連のユーザー変更は巻き戻していない。
- 検索欄の入力直後に「表示」を通常クリックし、未作成・保存済み・不同意の詳細を4回連続で開閉できた。`hidden.bs.modal`完了後に表示ボタンへフォーカスが復帰した。空状態では取得ボタンが無効、不同意でも授業参照は利用可能。
- ファイル選択、保存コード表示、CodeMirrorへのキー入力で値が変わらないことを確認。クラス/同意filter、Enterキーによるソート、更新後のfilter保持、一括取得も確認した。
- 空フォルダのみの合成生徒はファイル数0でも一括取得が有効、詳細の単体取得は無効・ZIPは有効、一括応答は200（148 bytes）。JDBCのrollback付きDBテストでも`entryCount=1`とフォルダのアーカイブ対象を確認した。
- 通常クリックによる単体200（34 bytes）・ZIP200（847 bytes）・CSV200を確認。CSVは同意行のみで、不同意行を含まない。ZIP内容・パス・重複排除・CSV監査/失敗時503・参照前後の業務データ不変は自動HTTP/DBテストで検証した。
- 中間幅では共通ヘッダーの固定横並びと集計カードの最小幅が横溢れを起こしていた。この画面専用のbody classに限定してヘッダーを折り返し、集計カードの縮小/折返しと中間幅のhero縦積みを補完した。色・カード・機能・プロトタイプ自体は変更していない。
- 最終CSSの一覧を1440/1199/1024/858/769/375pxで確認し、全てdocumentのclient幅とscroll幅が一致。詳細も1440/858/375pxで一致。狭幅一覧は表の局所横スクロールを維持した。画面画像も確認したが、統合ブラウザーの撮影時viewport再設定があるため、画像名を1440pxの証拠とは扱わずDOM実測を正とする。

| 最終確認 | 正確なコマンド | 結果 |
|---|---|---|
| 全体回帰・WAR | `sh scripts/testing/teacher-exercise-review-local.sh unit` | exit 0。382件中252成功 / 130条件付きskip / 失敗・error 0。WAR成功 |
| 実DB・認証HTTP・WAR | `sh scripts/testing/teacher-exercise-review-local.sh integration` | exit 0。DB1件・HTTP1件成功、skip/失敗0。追加の空フォルダテストと専用body classを含む最終版 |
| 認証ブラウザー環境 | `sh scripts/testing/teacher-exercise-review-local.sh browser` | DB/HTTP成功後に18090を保持。終了時に所有コンテナ/ネットワークのみ清掃 |
| 8080反映 | `docker restart programming-process-evaluator-app-live` | 成功。Grettyで専用body classを含むServletの再コンパイルを確認。教師/生徒ログイン200、未認証の演習画面302 |
| 最終assets | `curl -fsS http://teacher.localhost:8080/css/teacher/exercise/exercise.css \| cmp - src/main/webapp/css/teacher/exercise/exercise.css` と同様のJS照合 | exit 0。最終ソースと配信内容が一致 |

- JUnit/WAR/runtimeログはローカルの`build/teacher-exercise-review-validation/unit/`・`integration/`へ保存。工程/要件の完了チェックポイントとbrowser検証記録はセッション成果物へ保存した。
- 認証後の業務操作受入は合成DBの18090で実施。8080では再起動・最終class/assets・未認証導線を確認し、既存利用者の資格情報や共有DBは書き換えていない。8080既存教師による再ログイン後の操作は未実施。
- OS標準保存ダイアログ/ダウンロード完了イベント、全OS/ブラウザー、大人数同時取得・本番TLSでの利用者受入は未確認。130件の条件付きskipは実DB/外部サービスの成功と扱わない。エディターの既存Gson解決診断と、成功したGradleコンパイルを区別する。

### 再開・再中断の記録（2026-10-08 17時台）
- 再開時のHEADは`3b0bd72`。前回までの授業演習確認の差分はユーザーにより`7016eb2`へコミット済みで、その後のデプロイ修正も確認した。これらは変更・巻き戻していない。
- `sh scripts/testing/teacher-exercise-review-local.sh integration`を通し実行し、実DB1件・認証HTTP1件・WAR生成が成功（exit 0）。専用環境の自動清掃も確認した。証跡はローカルの`build/teacher-exercise-review-validation/integration/`。
- 同スクリプトへ`browser`モードを追加。専用DB/認証HTTPを確認後、18090に合成データの画面を開き、スクリプトの停止で所有コンテナ/ネットワークを清掃できる。`browser`モードの起動・DB/HTTP成功・停止清掃を実施した。
- **残っていた詳細表示失敗の原因を実ブラウザーで特定した。** 検索欄に`ex_empty`を入力してから「表示」を押すと、`mousedown` → 検索欄の`change` → `mouseup`となり、`click`が発生しなかった。検索欄のフォーカス離脱時に`change`ハンドラーが一覧を再生成し、押下中のボタンを置き換えていた。modalの非同期応答ではなく、検索後のクリック消失がこの再現の原因だった。
- [画面JS](../../../src/main/webapp/js/teacher/exercise/exercise.js)を修正し、`input`は検索欄だけ、`change`はselectだけで再描画するよう分離した。変更は未コミット。**この修正後のブラウザー再現テスト・回帰テストは、再中断依頼のため未実施。成功とは扱わない。**
- 8080への反映・再起動・DB変更は行っていない。一時検証スクリプトを停止して専用コンテナ/ネットワークが残っていないこと、8080教師ログインHTTP 200を確認した。
- 次は修正後のJSを用い、検索入力直後の通常クリックで空の詳細が一度で開くこと、保存済み詳細を閉じた後の連続切り替え、フォーカス復帰を確認する。その後に最終回帰・画面受入・8080反映へ進む。

### 実装済みの差分
- `TeacherExerciseDao` / `TeacherExerciseControl` / `TeacherExerciseServlet` と一覧・詳細DTOを追加し、`/teacher/exercises` を教師共通メニューへ接続。
- プロトタイプを変更せず、一覧・6列ソート・学校/クラス/同意/生徒ID検索・全画面詳細・フォルダツリー・読取専用CodeMirror・取得操作を本実装へ接続。
- 単体/ZIP/一括/CSVを実DBから生成。未作成生徒の一括取得スキップ、複数所属の重複排除、複数未統合領域の区別、空フォルダ保持、最新同意とCSV監査を実装。
- `ExerciseDownload` の既存4引数コンストラクターは維持。教師ZIPの追加ルート分だけパス上限へ加算し、元の保存パスは従来の1000文字制限で検証。単体取得の接頭辞で255文字を超える場合は元のファイル名を使用。
- `LocalDateTime` のJSONアダプター、一覧の`entryCount`（空フォルダだけの場合も一括取得可能）、日時のプロトタイプ形式、選択行、modal終了時のフォーカス復帰を追加。
- CSVと画面の並び順一致のため文字列比較をUTF-16の辞書順へ統一。画面の構成・色・操作は維持し、監査/匿名化の説明、失敗通知、アクセシビリティを補完。
- 対象の単体・実DB・認証HTTPテストと、隔離検証用の[実行スクリプト](../../../scripts/testing/teacher-exercise-review-local.sh)を追加。スクリプトは構文確認だけ実施し、通し実行は未実施。

### 実行済みの確認
以下のGradleコマンドはJava 21の隔離コンテナ内で実行した。共有DB・本番データ・外部AIは使用していない。

| 確認 | コマンド / 操作 | 結果 |
|---|---|---|
| 初回対象テスト・WAR | `gradle --offline --no-daemon test --tests control.teacher.TeacherExerciseControlTest --tests servlet.teacher.TeacherExerciseServletTest --tests servlet.auth.ApplicationUrlContractTest --tests dao.TeacherPermissionDaoTest war --rerun-tasks` | 成功。ただし以後に変更あり |
| 全体回帰・WAR | `gradle --offline --no-daemon test war --rerun-tasks` | 途中版で382件: 252成功 / 130条件付きskip / 失敗0。最後のDTO/JS変更後の全体再実行は未実施 |
| 最新実DB・WAR | `gradle --offline --no-daemon flywayMigrate test --tests control.teacher.TeacherExerciseDatabaseTest war --rerun-tasks` | 1テスト成功。学校/機能/所属/停止境界、複数領域、削除/ごみ箱祖先除外、同意撤回後の授業閲覧・取得、CSV同意制限、保存内容/版/ログ等の不変を確認 |
| 最新認証HTTP | `gradle --offline --no-daemon test --tests servlet.teacher.TeacherExerciseRuntimeTest --rerun-tasks` | 1テスト成功。JSP/list/detail/単体/ZIP/一括/CSV、400/403/404/405、監査失敗503とCSV本文を返さないことを確認 |
| 実ブラウザー | 合成教師ログイン→共通メニュー→一覧→詳細→別ファイル選択、フィルター、ソート、更新、CSV、単体/ZIPボタン | 保存コードの表示、読取専用設定、HTTP 200の実コード/ZIP/CSV応答を確認。統合ブラウザーの保存イベント待機はタイムアウトし、OS保存ダイアログは未確認 |
| 狭幅表示 | 375px modal、一覧の局所横スクロール | modal幅/scroll幅375px、一覧はページ幅/scroll幅360pxで一致、表だけ294px/760px。ページ全体の横溢れなし |
| プロトタイプ参照 | HTML/CSS/JS、実ブラウザーの一覧・詳細 | 構成/文言/操作と比較。ブラウザーのviewportが390pxへ戻るため、保存したprototype画像を1440px証跡とは扱わない |

### 再開時の残件
以下は中断時の引継ぎ項目。最終受入で1〜5を処理済み。現在の未確認事項は上記の最終受入末尾を参照する。
1. **最優先: 検索後の詳細表示修正を検証。** 再開時に検索欄の`change`による押下中ボタンの置き換えを特定し、イベント処理を修正済み。修正後の通常クリック・連続切り替えは未確認。`hide.bs.modal` / `hidden.bs.modal`、`modalClosing`、非同期応答の世代管理も含めて最終受入を行う。前回の単独空状態/取得ボタン無効化/hidden完了後フォーカス復帰は確認済み。
2. 空フォルダだけの生徒の一括取得可否、更新後の選択行/フォーカス、取得とmodal終了の競合、通常幅/狭幅を最終版で再確認する。
3. 最新の全体回帰（スクリプト`unit`）を再実行し、最終版の`integration`を実行する。再開時の`integration`は修正前のJSで成功している。条件付きskipを合格扱いしない。
4. 必要最小限で8080へ反映し、現在のDB・教師権限を用いた接続確認を行う。共有データを検証用に書き換えない。
5. T004/T005受入後にこの計画・ロードマップと完了チェックポイントを更新する。現時点で完了報告・コミット・デプロイはしない。

### 停止・環境整理
- 今回作成した`ppe-exercise-review-*`の検証コンテナ6個と専用ネットワークを、所有ラベル確認後に削除。検証DBはtmpfsだけで作成し、共有のDBボリュームは使用/削除していない。
- 最新DB/HTTP結果・途中版全体回帰XML・runtimeログ・検証WARはセッションの作業記録に保存した。最後のUI待機タイムアウトも未解決事項として保持。
- 8080開発app、既存DB、Python runner、他プロジェクトのDBは維持。停止後の教師ログインHTTP 200を確認した。
- 再開時は未コミット差分と他作業の変更を先に確認し、既存の差分を上書き・巻き戻さない。削除済みの18090検証環境は再作成が必要。
