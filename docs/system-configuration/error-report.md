# 実装・検証エラーレポート

秘密情報・実際の生徒データ・研究データは記載しない。解消後も履歴を保持する。

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
