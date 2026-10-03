# Web 共通基盤と画面資産 実装計画

## 目的と範囲
- 利用者に提供する動作: 共通レイアウトと通知資産を後続画面から利用でき、未定義URLやサーバーエラーで内部情報を表示しない。
- 今回実装する範囲: JSP共通フラグメント、共通CSSと shared feedback、404 / 500画面、既存 `/hello` のJSP転送先を整備する。
- 今回対象外: プロトタイプ24画面の移植、認証・認可、業務画面・データ表示、画面固有のJavaScript。各画面は対応する機能工程で移植する。

## 必読資料
- 機能仕様書: `docs/function-specification.md`（共通UIと対応環境）
- 画面遷移図 / プロトタイプ: `docs/screen-flow-diagram.md`、`screen-flow-diagram/webapp/WEB-INF/template/template.html`
- 状態ルール: 今回は業務状態を変更しないため対象外
- DB 設計・テーブル定義: DB変更なし
- クラス図 / その他: `docs/feedback-guideline.md`、`docs/system-configuration/implementation-roadmap.md` 工程4

## 前提・未決事項
- 合意済み前提: 既存画面の共通デザイン・feedback資産を再利用し、仮データやデモ処理は本実装へ移さない。
- 実装契約上の関連 ID: なし。認証を伴う画面遷移はロードマップ5以降で実装する。
- 未決事項・影響・確認が必要な相手: なし。ユーザー指定により共通基盤のみ先行し、各画面の移植は後続工程に分離する。

## ユースケースとデータ契約
| 操作 | ロール・所有範囲 | 入力 | DBから読むデータ | DBへの変更 | 状態遷移 | 成功・失敗時の表示 |
|---|---|---|---|---|---|---|
| `/hello` 共通基盤確認 | 認証前の開発確認 | なし | なし | なし | なし | 共通レイアウトを表示 |
| 存在しないURLへアクセス | 全利用者 | URL | なし | なし | なし | 共通404画面 |
| JSP / Servlet 内部エラー | 全利用者 | リクエスト | なし | なし | なし | 内部情報を含まない共通500画面 |

## 実装タスク
1. [x] DB migration / 初期データ: 変更なし。
2. [x] Entity / DTO / DAO: 変更なし。
3. [x] Control とトランザクション: 変更なし。
4. [x] Servlet / Filter / 入力検証: `/hello` のJSP転送先とWebエラー画面を設定する。
5. [x] JSP / CSS / JavaScript: 共通JSPフラグメントと画面資産を整備する。
6. [x] 認可・監査・エラー処理: 404 / 500の表示で内部例外情報や仮データを出さない。
7. [x] テスト・手動確認: WARビルド、`/hello`、404、共通CSS / JavaScript配信を確認する。

## 変更予定ファイル
- 作成: `src/main/webapp/WEB-INF/template/page-start.jspf`、`page-end.jspf`、`WEB-INF/error/404.jsp`、`500.jsp`、`WEB-INF/hello/hello.jsp`、`WEB-INF/web.xml`、`css/template/template.css`、`css/shared/feedback.css`、`js/shared/feedback.js`
- 変更: `src/main/java/servlet/HelloWorldServlet.java`
- 削除: 0バイトの `src/main/webapp/WEB-INF/template/template.html`
- プロトタイプから再利用する表示資産: 共通テンプレートCSSと student shared feedback CSS / JavaScript、Bootstrap 5.3.8 CDNのSRI付き参照
- プロトタイプから除去する仮データ・擬似動作: テンプレート内のユーザー名・通知数・課題一覧などは移植しない。feedbackは共通APIのみを読み込み、画面固有動作は追加しない。

## 検証計画
- ビルド / テストコマンド: `docker compose exec -T app gradle build --warning-mode all`
- 正常系: `/hello` が共通JSPを表示し、共有CSS / JavaScriptがHTTP 200で取得できる。
- 状態遷移・操作可否: 業務状態変更なし。
- 権限境界・直接 URL: 認証前共通エラー画面のみ。業務画面は追加せず、ロードマップ5以降で確認する。
- DB保存後の再読込: DB変更なし。
- 異常系: 未定義URLが404、内部エラーが500共通画面となり、スタックトレースや例外詳細が表示されない。
- 未実施の場合に残る未確認事項: 画面ごとの導線・認可と画面固有feedbackは各画面移植時に確認する。

## 完了条件
- [x] 共通基盤の見た目・依存資産がプロトタイプと整合し、仮ユーザーデータを含まない。
- [x] 404 / 500画面が内部情報を漏らさない。
- [x] shared feedbackを共通配置し、ページ側で再実装しない。
- [x] 各業務画面の仮データやデモ動作を移植しない。
- [x] 実行した確認結果と未確認事項を記録する。

## 実施結果
- `docker compose exec -T gradle gradle build --rerun-tasks --warning-mode all`: 成功。`test` は `NO-SOURCE`。
- `/hello`: HTTP 200、JSP共通レイアウトと日本語UTF-8表示を確認。不要なJSESSIONIDを発行しない。
- 未定義URL: HTTP 404と共通404画面。存在しないJSPを一時作成して500を発生させ、HTTP 500で共通エラー文言のみを返し例外文字列を隠すことを確認。テスト用JSPは削除し、最終ビルド後は404となることを確認。
- `/css/template/template.css`、`/css/shared/feedback.css`、`/js/shared/feedback.js`: すべてHTTP 200。
- ブラウザーで共通画面を表示し、Bootstrapと`PPEFeedback`の読み込み、toast表示を確認。
- `git diff --check`: 成功。
- 未確認事項: 業務画面の移植、画面固有の導線・認可・feedbackは対応する後続工程で確認する。
