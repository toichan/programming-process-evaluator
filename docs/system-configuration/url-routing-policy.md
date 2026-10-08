# 公開URLの整理方針

更新: 2026-10-08。対象: Servlet/JSP本実装。プロトタイプは参照専用。

## 命名・変更範囲

1. `/ロール/対象・機能[/下位機能・操作]`を基本とし、小文字・必要な場合はハイフンを使う。JSPファイル名や内部ディレクトリをそのまま公開URLへ転記しない。
2. `account`はログイン中の本人、`students` / `teachers` / `schools`は管理する対象の集合とする。本人情報と他者管理を同じ曖昧な名前で表さない。
3. 既存の機能画面名（`task` / `prompt` / `exercise` / `evaluation`等）は維持する。全URLをREST形式・複数形へ一律変換したり、既存POSTのaction契約を作り直したりしない。
4. 本人パスワード画面は両ロールとも`account/password`へ統一。研究同意はアンケートへの従属を外す。
5. ロール別host、host-only session cookie、CSRF、機能/学校権限、版/状態検証は変更しない。URLが短くなっても認可範囲は広げない。
6. 正規リンク・フォーム・fetch用endpoint・ログイン先・強制変更先・成功後redirectは新URLへ統一。クエリ名/値とDBデータ・公開状態は変更しない。

## 変更対応

| 機能 | 正規URL | 互換性のため誘導する旧URL |
|---|---|---|
| 教師の生徒アカウント管理 | `/teacher/students` | `/teacher/account/account`、`/teacher/accounts` |
| 生徒本人のアカウント情報 | `/student/account` | `/student/account/account` |
| 教師本人のアカウント情報 | `/teacher/account` | `/teacher/account/profile` |
| 生徒本人のパスワード変更 | `/student/account/password` | `/student/account/change-password` |
| 研究同意 | `/student/consent` | `/student/survey/consent` |
| 管理者の教師管理・学校タブ | `/admin/teachers` | `/admin/management` |

旧URLは無期限の別正本にはせず、固定の互換マップにだけ保持する。認証・ロール・セッション失効・強制変更制限を通したうえで、GET/HEADは302、POST等は307で正規URLへ誘導する。307でフォーム本文・メソッドを保持し、更新は正規URLで1回だけ実行する。最終処理のCSRF/機能/学校権限チェックは従来どおり必要。生徒の旧強制変更URLも同じ正規password画面として判定し、本文を失う302やredirect loopを起こさない。

filter/対象ID/表示操作等のクエリはそのまま保持し、redirect先のパスは固定マップで決める。任意の戻り先URLを入力から受け付けない。古いsession内の教師初期画面も正規化して既存の許可リストで検証する。

## 本実装のURL棚卸し

24の`@WebServlet`と、以下の画面/API系列を対象に確認する。未実装教師機能のために架空URLを追加しない。

| 分類 | 正規URL・系列 | 扱い |
|---|---|---|
| ポータル入口 | `/` | teacher hostは教師login、student hostは生徒login。既存の汎用hostの既定は生徒loginを維持 |
| ロール入口 | `/student`、`/teacher`、`/admin`（末尾`/`も可） | 認証/ロールを検証後、各`/home`へ。強制変更中はpassword画面が優先 |
| ログイン | `/student/account/login`、`/teacher/account/login` | 維持。管理者は教師ポータルから認証 |
| ログアウト | `/auth/logout` | 維持、CSRF付きPOSTのみ。未認証時の入口も現在hostに合わせる |
| 生徒ホーム・本人情報 | `/student/home`、`/student/account`、`/student/account/password` | home維持、本人情報/変更URL正規化 |
| 課題エディター | `/student/editor`、`draft`、`run`、`session`、`session/input`、`session/cancel`、`submission/check`、`submission/submit`、`resubmission`、`preferences` | 既存の画面/API系列を維持 |
| 授業演習 | `/student/exercise`とその操作API | tree、create、save、run、session/input/cancel、upload/upload-preview、trash/restore、rename/move、batch-move/trash/restore、duplicate/duplicate-preview、unification/unify、downloadを維持 |
| 評価 | `/student/evaluation`、`log`、`retry` | 既存の画面/API系列を維持 |
| アンケート・同意 | `/student/survey`、`/student/consent` | survey維持、同意の階層を独立 |
| ルーブリック | `/student/rubric` | 維持 |
| 教師業務 | `/teacher/home`、`students`、`task`、`prompt`、`progress`、`distribution` | studentsだけ正規化、既存の対象ID/job/filter/API契約は維持 |
| 教師本人情報 | `/teacher/account`、`/teacher/account/password` | 本人情報URLを短縮、password維持 |
| 管理者 | `/admin/home`、`/admin/teachers`、`/admin/schools` | 管理画面をteachersへ統一、home/schools維持。schoolsは既存の独立学校API |
| 開発用サンプル | `/hello` | 既存サンプルとして維持。業務メニューには追加しない |
| 内部view・静的資産 | `/WEB-INF/...`、`/css/...`、`/js/...` | 公開業務URLとは別。内部viewはServletからforwardし、配置の一律改名はしない |

## 検証・完了条件

- 全Servlet mappingの重複、新旧URL対応、JSPの業務リンク/フォーム/endpointにmapping漏れや旧URLがないことを自動確認する。
- context path・クエリ保持、GET/HEAD302、POST307、任意redirect不可、正規URLはredirectしないことを検証する。
- 未認証、誤ロール、ホスト違い、bare role入口、教師の旧landing、強制変更先を確認する。強制変更・CSRFや機能権限を緩めない。
- 対象単体テスト/WARと共通認証基盤の全体回帰を実行。実HTTP/認証browserで教師の生徒管理導線と旧URL誘導、本人情報・生徒の初回変更/同意・管理者を専用合成データで確認する。
- 共有DBの既存課題・利用者資格情報は変更しない。Geminiは呼び出さない。検証結果・未確認範囲を記録し、8080へ反映して稼働を確認する。

## 2026-10-08の実施結果

- 方針・機能仕様第148版・認証/アカウント契約と本実装を更新。旧URLは固定マップだけに保持し、内部viewと静的資産は維持した。
- 全体回帰は343件中223成功・120件ゲートskip・失敗0、WAR成功。明示ゲート付きの`ApplicationUrlRuntimeTest`は、空の専用DBと専用アプリで5成功・失敗0。通常実行では同テストもskipし、共有DBをseedしない。
- 認証HTTPで旧URL/クエリ/307、強制変更・失効、旧password本文を新URLで処理した際のDB版/資格情報/状態、任意変更先、同意の保存/再読込、未認証・ホスト・ロール・不正CSRF拒否を確認した。認証ブラウザーでも教師の管理JSON/一覧と本人情報、生徒の同意/本人情報/変更画面、管理者2タブを実DBから確認した。
- 8080のアプリをビルド・再起動し、両login200、host別root、保護URLのログイン誘導と実classの新URL定数を確認。認証後の書込受入は専用環境で行い、8080の既存利用者を再設定・seedしていない。
- 専用schema/grant/containerと一時秘密設定を削除。共有課題10/11のmetadata hash、無関係な18081の稼働を保持。Geminiへの外部要求は0。
- 統合ブラウザーの一部ポインタclickが可視/安定待ちでtimeoutした。教師の下部passwordリンクはhref・直接GET・実HTTPで確認し、管理者はnative form submit/DOM tab clickで画面・実データを確認した。これを全ポインタ操作やCLI/全ブラウザーの認証E2E合格とは扱わない。経緯・正確なコマンドと結果は[エラーレポート](./error-report.md)を参照。
