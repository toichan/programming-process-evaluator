# 実装・検証エラーレポート

秘密情報・実際の生徒データ・研究データは記載しない。解消後も履歴を保持する。

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
