# 生徒ホーム・同意・アカウント 実装計画

現在の状態（2026-10-04整理）: **工程6の対象範囲は実装済み**。ID統一・パスワード変更UX・認証情報履歴等の追加結果は以下に保持する。個別の未確認事項を完了へ変更せず、次の着手対象は[実装ロードマップ](../implementation-roadmap.md)の教師工程とする。

## 目的と範囲
- 利用者に提供する動作: ログインした生徒本人のアカウント・所属情報と、在籍クラスへ公開されている課題および進捗を表示し、研究同意の初回回答を記録する。
- 今回実装する範囲: 生徒ホーム、アカウント情報、同意確認の表示と初回回答、DBに基づく表示状態、CSRF・本人スコープ検証。
- 今回対象外: コードエディター、授業演習、評価・アンケート回答、教師によるアカウント登録・同意文書管理。

## 必読資料
- 機能仕様書: `docs/function-specification.md` のアカウント、研究協力同意、課題提示機能
- 画面遷移図 / プロトタイプ: `docs/screen-flow-diagram.md`、生徒のhome/account/consent画面
- 状態ルール: `docs/state-rules/student/state-rules-overview.md`、`docs/state-rules/student/survey-state-rules.md`
- DB 設計・テーブル定義: `student_profiles`、`student_class_memberships`、`schools`、`classrooms`、`tasks`、`task_class_assignments`、`task_participations`、`consent_document_versions`、`consent_records`
- その他: `docs/system-configuration/implementation-contract.md` IC-006、ロードマップ6

## 前提・未決事項
- 2026-10-03引継ぎ時の検証記録補足: 以下の過去コマンドに指定された`control.auth.AuthenticationControlTest`は存在せず、複数セレクター実行の成功はそのテストの実施を証明しない。認証の実在テストは`PasswordPolicyTest`と`PasswordHasherTest`であり、更新後遷移の項に記載した両テスト指定の検証は成功済み。最新の全体検証は56件中44件成功・12件DB/実APIゲートskip・失敗0。今回の引継ぎ文書更新ではビルド・テスト・DB状態確認を再実行していない。
- 2026-10-03更新後遷移（仕様第89版）: 更新前の強制変更要否で、初回/リセット後はホーム、任意変更はアカウント情報へ固定パスで遷移。セッションの成功フラグを遷移先で消費し、共通feedbackのsuccess toastを1回表示する（クエリー値だけで成功通知は出さない）。画面説明・プロトタイプの通常/required=1遷移も一致させた。
- 関連修正: パスワード変更の成功履歴をcredential_historyへ更新と同じトランザクションで保存し、アカウント情報でDBの最新履歴を表示。過去の未記録履歴の捏造・バックフィルは行わない。
- 検証: `docker compose exec -T app gradle test --tests control.auth.PasswordPolicyTest --tests control.auth.PasswordHasherTest war --warning-mode all`成功、`docker compose exec -T app gradle test build --warning-mode all`成功。合成アカウントで強制変更/通常変更の成功POST後の正確なURL、両画面のsuccess toastと再読込時の非再表示、本人ID付き初回/通常履歴のDB再表示を確認。一時fixtureは削除済み。実行中Servlet反映のためappサービスを再起動し、HTTP200を確認した。
- 2026-10-03ボタン配置フォローアップ（仕様第88版）: 前回の画面固有font-weight:600/余白指定を外し、共通テンプレートのボタンへ統一。左（モバイルでは上）を戻る、右（下）を更新とし、戻れない説明は戻るボタンと同じグループ内の直下に配置。以前の16px/600/48pxの確認記録は過去の結果であり、現行は共通値を採用する。
- フォローアップ検証: `docker compose exec -T app gradle war --warning-mode all`と`git diff --check`成功。合成アカウントの実JSPを1280px/375pxで確認し、両ボタンのfont-family・16px・太さ500・余白12px/24px・角丸12pxが一致、順序・説明位置・disabled・横はみ出しなしを検証。プロトタイプの強制変更表示でも同じ配置/共通文字を確認。合成ユーザー/学校/ログイン履歴は削除済み。既存テストアカウントの状態やパスワードは変更していない。
- 2026-10-03パスワード変更フォーム改善: REQ-PW-001 ログインと同じ表示切替/ボタンデザイン、REQ-PW-002 入力中の要件チェックと具体的不足理由/更新可否、REQ-PW-003 Javaの最終検証維持と強制変更中の戻る無効化。T-PW-001 仕様第87版の承認 → T-PW-002 画面遷移文書/プロトタイプ/本実装 → T-PW-003 サーバー・ブラウザー・ビルド確認まで完了。
- パスワード改善の実装: 表示切替は共通password-visibility.jsを利用し、目/斜線付き目と入力欄ごとの操作名・aria-pressedを同期。ボタンの文字16px/太さ600/高さ48pxを統一。要件カードは5項目のチェックリストと達成数、不足理由を表示し、未達成では更新ボタンを無効にする。強制変更中は戻るボタンが実際にdisabledで説明付き、任意変更中はリンクを有効にする。
- プロトタイプと本実装のチェック・表示切替・新デザインは本実装側の同じJS/CSSを参照し、判定の重複・乖離を避ける。プロトタイプは通常変更、`password.html?required=1`で強制変更表示を確認できる。静的確認サーバーはリポジトリルートを公開する（webapp単独公開では共用資産を参照できない）。既存の共有テンプレート・feedbackによる確認を維持する。
- パスワード改善の検証: `docker compose exec -T app gradle test --tests control.auth.PasswordPolicyTest --tests control.auth.AuthenticationControlTest war --warning-mode all`成功。`docker compose exec -T app gradle test build --warning-mode all`成功（56件中44件成功、12件はDB/実APIゲートskip、失敗0）。長さ8/32/33文字、ASCII128文字の許可範囲/文字種、複数違反の理由と値を出力しないことをJavaで確認。ブラウザーで9種類の入力、有効/無効・3欄のアイコン切替・ログイン画面回帰、ASCII分類、375pxの横はみ出しなしを確認。
- サーバー検証: 合成アカウントでクライアント検証を回避したPOSTの要件不足/確認不一致/現在パスワード誤り/再利用を具体的理由で拒否し、CSRF不正403も確認。257文字の同一確認値も「一致しない」ではなく長さ違反を表示。生HTMLの更新ボタンはJSなしでも有効、直接POSTの正常更新・新パスワードでの再ログイン成功を確認（JavaScript自体を無効化したブラウザーE2Eは未実施）。強制変更解除をDBで確認し、任意変更で戻る導線が有効になることも確認。test-student/test-student-level2のパスワードや状態は変更せず、一時合成データ/サーバーは除去。
- 2026-10-03利用者ID統一バッチ: REQ-ID-001 生徒IDはusers.login_idでDB取得しナビ/登録情報/本人履歴に統一、REQ-ID-002 教師はログインIDを表示し別の表示名を正本にしない、REQ-ID-003 内部user_id・研究用識別子・既存認証/匿名化を維持。T-ID-001 文書整合 → T-ID-002 DAO/Entity/画面とテスト → T-ID-003 認証済みUI・専用DB・ビルド検証。教師業務一覧・CSV・生徒/教師作成UIは未実装のため、今回完成とはしない。
- 利用者ID統一バッチの結果: T-ID-001〜003完了。StudentDaoはlogin_idをstudent_idとして取得し、StudentAccountDetails・登録情報・本人履歴に使用。ナビの別表示名を除去し、教師ホームと教師プロトタイプもログインIDに一致させた。users.login_id/user_id、既存パスワード、研究用識別子は未変更。旧コード/名前は互換保持し、EvaluationWorkerDaoの匿名化除去対象も維持した。
- 利用者ID統一の検証: `docker compose exec -T app gradle test --tests entity.StudentAccountDetailsTest --tests control.auth.AuthenticationControlTest --tests control.evaluation.EvaluationWorkerTest war --warning-mode all`成功。専用DBへDB_NAME・SCHOOL_DB_TESTを指定した`gradle test --tests control.admin.SchoolDatabaseTest --tests entity.StudentAccountDetailsTest --rerun-tasks --warning-mode all`は5件成功（64文字のlogin_idと異なる旧コード/表示名のDB取得・本人履歴を含む）。`docker compose exec -T app gradle test build --warning-mode all`は54件中42件成功/12件ゲートskip/失敗0。`git diff --check`成功。
- 合成生徒/教師の認証済みUIで、アカウントGET200・右上/登録情報/本人履歴の同一ID・教師履歴/教師ホームの同一ID・旧コード/名前の非表示、教師による生徒ページアクセス403を確認。教師プロトタイプはセッションIDに一致し、HTML風のIDも文字列として表示することを確認。既存test-studentのID・旧コード・有効所属はDB確認のみで、パスワード変更や追加seedなし。一時fixture・専用DB/権限・サーバーは除去。研究出力のE2E、教師登録/CSVの業務UI、延期中の評価/アンケート手動確認は今回対象外。
- 生徒アカウント表示修正の検証: WARビルド・git diff --check成功。両レベルの合成生徒で認証済みJSPのレベル名非表示・変更導線を確認し、所属追加後のDB値が登録情報へ表示されることを確認。合成fixtureの初回所属不足は[エラーレポート](../error-report.md)へ記録。
- 2026-10-03生徒アカウント表示修正: 生徒向けのレベル名称・番号を除去し、変更可否のみ案内する。レベル1は指定文言・灰色disabledボタン・初回変更「不要」。登録情報は既存のServlet→Control→StudentDaoでDB取得済み。学校・学年・クラスの有効所属があり、追加seedは不要。利用者ID統一後は登録情報もusers.login_idから生徒IDを取得し、旧生徒コードは表示しない。
- 2026-10-03手動確認対応: ホームの課題件数は1件の場合だけ「1 Task」、0件・複数件は「0 Tasks」「2 Tasks」等と表示する。
- 2026-10-03表示改善: 本実装の同意変更ダイアログを回答の変更・研究利用・学習/アンケートへの影響・記録内容の4項目に整理。共通feedbackの構造化detailsで見出しと研究利用の範囲を太字表示する。同意方針・保存処理・確認/キャンセル動作は変更しない。
- 表示改善の検証: 合成フォームで見出し4件・太字5件、再同意/撤回文言、キャンセル送信0件・確定送信1件、従来の文字列detailsとHTMLエスケープを確認。375px幅で横方向のはみ出しなし。初回検証失敗と既存フォーカス警告は[エラーレポート](../error-report.md)へ記録。
- 既存の機能仕様、状態ルール、IC-006で確定した方針を実装し、新たな業務ルールは追加しない。
- 同意文書は`consent_document_versions`の有効版を表示する。2026-10-03のユーザー指示により、[画面遷移図の同意文書](../../../screen-flow-diagram/webapp/WEB-INF/student/survey/consent.html)を[Flyway V10](../../../src/main/resources/db/migration/V10__register_prototype_consent_document.sql)で登録する。正式な承認文面ではないため、タイトル・本文で仮文面と明示する。有効版がない場合は回答送信を無効にし、管理者による設定が必要である旨を表示する。
- 2026-10-03合意: 回答済みでも研究同意画面から変更できる。共通確認ダイアログで変更前後・影響を確認し、同意撤回・再同意を履歴に追記する。現在の回答IDを使って古い画面からの変更を拒否し、同じ回答の送信では履歴を増やさない。通常学習は制限しない。
- 同意変更の確認: `docker compose exec -T app gradle test build --warning-mode all`成功。専用一時DBで`CONSENT_DB_TEST=true`と`DB_NAME=ppe_consent_test_...`を指定し、`gradle flywayMigrate test --tests control.student.ConsentDatabaseTest --tests entity.ConsentDecisionTest --rerun-tasks --warning-mode all`成功（4件）。撤回日時・再同意・履歴追記・同状態の重複抑止・確認なし拒否・古い画面/異なる文書版の拒否を検証。実データを変更しないブラウザー用フォームで、共通ダイアログのキャンセル後送信0件、確定後1件を確認。認証済みJSP画面からの操作全体は手動確認待ち。共通モーダルのブラウザー警告は[エラーレポート](../error-report.md)参照。
- 2026-10-03手動確認対応: ホームの未回答案内を「研究協力への同意確認が未回答です。課題を行う前に回答してください。」、ボタンを「回答する」に変更。学習へのアクセス制限は追加しない。V10の仮文面登録後、`docker compose exec -T app gradle build flywayMigrate flywayValidate --warning-mode all`と`git diff --check`成功。UTF-8指定のMySQLクライアントで有効版・日本語タイトル・任意協力の本文を再読込確認。認証済み画面はユーザーの手動確認待ち。
- ホームの同意済み常設通知は削除し、回答保存後の完了通知と重複表示しない。同意状態の確認は同意画面で行う。
- 2026-10-03追加合意: 同意へ変更する確認ダイアログに「同意変更前のデータを含め、研究に利用されます。」を追加。不同意・撤回期間に保存したデータも今後の研究利用対象とする方針を、機能仕様・状態ルール・IC-006へ反映。工程11の研究用出力では最新同意状態で対象を判定する。
- 2026-10-03文言・方針変更: 撤回確認を「同意を取り消すと、これまでのデータも含め、今後の研究には使われません。」に変更。アンケートに回答できなくなることは別項目で説明する。撤回前のデータも今後の研究利用から除外する合意を機能仕様・状態ルール・IC-006へ反映。研究用の集計・出力は工程11で未実装のため、実装時に最新同意状態による除外を検証する。
- エディター、演習、アンケート回答画面は後続工程。未実装endpointへのリンクは表示しない。

## ユースケースとデータ契約
| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| 生徒ホーム表示 | ログイン中の生徒本人 | なし | 本人の同意状態、activeな所属、公開中課題、本人の`task_participations` | なし | 未着手は参加行なしを`not_started`として表示 | 課題なし・所属なしは空状態。別生徒のデータを返さない |
| アカウント表示 | ログイン中の生徒本人 | なし | 本人の生徒ID、security level、初回変更状態、activeな学校・クラス所属 | なし | DBの現在値を表示 | 所属がない場合は未登録と表示。認証秘密は表示しない |
| 同意画面表示 | ログイン中の生徒本人 | なし | 有効な同意文書版、本人の最新同意記録 | なし | 未回答は選択可、回答済みは参照表示 | 有効文書なしは送信不可。既回答は重複回答不可 |
| 初回同意回答 | ログイン中の生徒本人 | CSRF token、文書版ID、説明確認、agree/decline | 最新同意記録、有効文書版、本人アカウント | `consent_records`へ追記 | unconfirmed → agreed / declined | ホームへ戻り完了通知。重複・期限切れ版・不正値は拒否し状態を変更しない |

## 実装タスク
1. [x] 既存仕様・契約を確認。対象要件は記載済みのため機能仕様の改版は不要。
2. [x] Entity / DAO: 本人の所属・アカウント・公開課題・状態、有効同意版・最新回答を取得する。
3. [x] Control: 認証ユーザーIDを唯一の本人スコープとして使い、初回回答を排他制御下で追記する。
4. [x] Servlet: home/account/consentのGETと同意POSTを実装し、CSRF・入力・重複回答・DBエラーを処理する。
5. [x] JSP / CSS: 対象画面のプロトタイプのレイアウト・項目・導線を反映し、DB値、空状態、既回答状態を表示する。仮データ・未実装endpointへのリンクを残さない。
6. [x] 認可・エラー処理: 別ユーザーIDをリクエストから受け取らず、同意状態を研究利用・アンケート以外の学習制限に使わない。
7. [x] テスト・手動確認: 既存単体テストと一時DB fixtureによるHTTP/DB・本人境界・重複送信・空状態を確認する。

## 変更予定ファイル
- 作成: 生徒用DAO / Control / entity / Servlet、home/account/consent JSP、共通生徒ナビゲーション
- 変更: `AuthHomeServlet`（教師routeのみ維持し、生徒homeをDBベースへ置換）、`AGENTS.md`
- プロトタイプから再利用する表示資産: home/account/consentのレイアウトと項目構成
- プロトタイプから除去する仮データ・擬似動作: 固定生徒・学校・課題・状態、JavaScriptのみの同意保存、未実装画面へのリンク

## 検証計画
- ビルド / テストコマンド: `docker compose exec -T app gradle test --rerun-tasks --warning-mode all`（5件成功）、`docker compose exec -T app gradle build --warning-mode all`（成功）
- 正常系: home/account/consent GET、同意・不同意回答、DB再読込を一時fixtureで確認。別生徒専用課題・予約課題が表示されず、公開中の本人課題だけ表示されること、説明文HTMLのエスケープを確認。
- 状態遷移・操作可否: 未回答、同意済み、不同意、文書なしを確認。重複POSTで履歴が増えず、不同意でもホームを利用できることを確認。
- 権限境界・直接 URL: 未認証時302、教師から生徒アカウントrouteへのアクセス403。Servletに任意のuser IDを渡す入力はない。
- DB保存後の再読込: 同意・不同意の状態、文書版、回答日時をDBに保存し、ホーム / 同意画面の表示と照合。
- 異常系: CSRFなし、不正decision、文書版変更後、重複送信、DB障害時のエラーを確認する。
- 実施結果: 一時DBに作成した生徒2名・学校/クラス・課題・同意文書で表示と保存を確認。別利用者へのデータ漏えいなし、公開前課題の除外、HTMLエスケープ、同意・不同意の保存と二重送信抑止を確認した。別fixtureでは所属・課題がない場合の空状態と有効な同意文書がない場合の送信不可表示を確認した。教師権限境界も確認。fixture、ログイン履歴、同意履歴、課題、学校、クラスが残っていないことをDBで確認した。
- 未実施の場合に残る未確認事項: 承認済み同意文書の実データ登録、同意撤回の操作導線、後続のeditor/exercise/survey endpoint。未認証時302、CSRFなし/不正入力の個別E2Eはこの工程では未実施。

## 画面設計反映のフォローアップ（2026-10-02）

- 対象: 生徒ログイン、ホーム、アカウント情報、パスワード変更、研究協力同意。
- `screen-flow-diagram/webapp/WEB-INF/student/` の対応画面と `screen-flow-diagram/webapp/css/student/` の共通・画面別CSSを基準に、ヒーロー領域、カード、項目、フォーム、ナビゲーション、フッターを本実装へ反映。画面遷移やDBフォームに必要な変更以外は元の構成を維持した。
- プロトタイプの固定サンプル値は移植せず、課題・進捗・評価・アンケート状態、アカウント所属、認証情報履歴、同意文書・回答をDBから表示する。
- editor / exercise / evaluation / survey の本実装されていない操作は、見た目上のリンクや成功動作を付けず、遷移できないことを明示して無効表示する。
- 検証: 既存テストとGradle build成功。仮の生徒・学校・課題・参加状態・アンケート・認証情報履歴・同意文書を一時登録し、HTTP 200で画面表示されること、課題/履歴/同意文書がfixture値どおり表示されること、HTMLエスケープ、CSRF付き同意POSTがDBに保存されることを確認。関連CSSはすべてHTTP 200。教師ログインもHTTP 200。仮fixtureはすべて削除済み。
- 追加修正: 共通生徒ヘッダーのブランド表記・メニュー順・課題ドロップダウン・ルーブリック・ヘルプ・通知・ユーザーメニューの配置をプロトタイプに合わせた。実装のない授業演習・ルーブリック・通知は無効または準備中とし、サンプル通知・課題データを表示しない。生徒IDは認証中ユーザーのlogin_idから取得し、別表示名は表示しない。ナビゲーション断片をUTF-8で解釈する。
- 追加検証: `docker compose exec -T app gradle test build --warning-mode all` 成功。ブラウザーで幅445pxの折りたたみメニューと幅1280pxの横並びヘッダーを確認し、メニュー・ユーザードロップダウンの日本語表示とログインIDを確認した。
- この確認は今回対象の生徒画面のみ。画面遷移図にあるエディター、授業演習、評価、アンケート、教師向け画面など全画面の本実装完了を意味しない。

## 完了条件
- [x] 機能仕様・画面導線に合致する
- [x] 関連する状態ルールと DB 状態値に合致する
- [x] 業務データを DB から読み書きする
- [x] サーバー側でロール・所有範囲・状態遷移を検証する
- [x] 仮データ・擬似成功処理・秘密情報を残していない
- [x] 実行した確認結果と未確認事項を記録した
