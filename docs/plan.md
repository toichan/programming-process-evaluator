## Plan: 全仕様を対象としたBootstrap画面遷移図プロトタイプ

TL;DR: 仕様書の全機能を対象に、Bootstrapベースの画面遷移図プロトタイプを `screen-flow-diagram/webapp/WEB-INF/template/template.html` と `screen-flow-diagram/webapp/css/template/template.css` に実装する。まずフォルダ構成を決め、次に優先度の高い画面から順番に作成する。

**Steps**
1. 対象範囲を確定する
   - 仕様書の全機能を対象とする
   - 生徒向け機能と教員向け機能の両方を含める
   - 画面遷移図として、遷移の流れと主要画面を静的に表現する

2. 使用ファイルを固定する
   - `screen-flow-diagram/webapp/WEB-INF/template/template.html` を画面遷移図のHTMLテンプレートに使う
   - `screen-flow-diagram/webapp/css/template/template.css` を共通スタイルに使う
   - 実装は `screen-flow-diagram` 配下で行う
   - BootstrapはHTMLテンプレート内でCDN読み込みする

3. フォルダ構成を決める
   - `screen-flow-diagram/webapp/WEB-INF/template/template.html` ：プロトタイプ本体
   - `screen-flow-diagram/webapp/css/template/template.css` ：デザインシステムとレイアウトCSS
   - 画面要素を整理するため、HTML内はセクションベースにする
   - 将来の実装を想定し、`student/` と `teacher/` フォルダは画面分類のための余地として維持する

4. 画面構成と遷移を整理する
   - 生徒フロー
     - ログイン画面
     - 研究協力同意確認画面
     - 課題選択画面
     - エディター画面
     - ルーブリック評価/コードログ確認画面
     - アンケート画面
   - 教師フロー
     - ログイン画面
     - アカウント情報管理画面
     - 課題作成画面
     - 学習履歴確認画面
     - 評価確認画面
     - アンケート結果確認画面
   - 画面遷移図として、Bootstrapのナビタブやカードで画面間の流れを可視化する

5. デザインシステムを決める
   - 色味：落ち着いたブルー系をベースに、アクセントにオレンジ/グリーンを使用
   - フォント：日本語対応の読みやすいWebフォントを利用（例: Noto Sans JP またはシステムフォント）
   - ボタン：プライマリ/セカンダリ/警告/成功の4種類を定義
   - カード/パネル：画面要素を整理するための共通カードスタイルを定義
   - バッジ/ステータス：課題難易度や同意状態などの視認性を高める

6. 画面作成の優先順位
   - フェーズ1（最優先）
     1. 学生ログイン画面
     2. 研究協力同意確認画面
     3. 課題選択画面
     4. エディター画面
     5. 評価表示画面
   - フェーズ2（次優先）
     1. 教師ログイン画面
     2. アカウント情報管理画面
     3. 課題作成画面
     4. 学習履歴確認画面
     5. 評価確認画面
     6. アンケート結果確認画面
   - フェーズ3（補完）
     - ルーブリック表示/コマンド仕様表示などのモーダル要素
     - アンケート画面（生徒向け）

7. 実装前の確認事項
   - HTML構造をセクション化して画面遷移のイメージをわかりやすくする
   - CSSは `template.css` に集約し、Bootstrapの上書きや拡張を行う
   - ポップアップやモーダル表示はJavaScriptを使って実装する
   - 画面遷移図としての用途を重視し、動的動作は最小限に留める

8. 検証とレビュー
   - ブラウザで `template.html` を開き、画面遷移の全体像が確認できること
   - 各画面セクションが仕様要件と対応していること
   - CSSで色味・ボタン・タイポグラフィが統一されていること

**Relevant files**
- `screen-flow-diagram/webapp/WEB-INF/template/template.html`
- `screen-flow-diagram/webapp/css/template/template.css`

**Verification**
1. `template.html` に全仕様の主要画面セクションが網羅されていること
2. `template.css` でデザインシステムが定義され、Bootstrapの見た目を整えていること
3. 画面遷移図として、生徒フローと教員フローの双方が視覚的に辿れること

**Decisions**
- 今回は「画面遷移図プロトタイプ」なので、動作よりも画面構成と設計の可視化を優先する
- 全仕様を対象とするが、実装は優先度高からフェーズ分けして進める
- template.html/template.css を中心にフォルダ構成を固め、その後に個別画面を作成する
