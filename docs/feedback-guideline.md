# feedback.js 利用ルール

このプロトタイプでは、確認・通知 UI は student / teacher の shared feedback を基準に統一する。

## 使い分け

- toast: 保存完了、生成完了、軽い更新完了など、操作後に短く消えてよい通知
- inline alert: 未入力、入力不足、保存失敗、送信失敗など、その場で修正が必要な警告
- confirm dialog: 送信、リセット、実行開始など、実行前に確認したい操作
- danger dialog: 削除、取り消せない更新、一括反映など、影響範囲が大きい操作

## toast の色ルール

- success: 保存完了、送信完了、生成完了、読込完了、一覧更新完了などの完了通知
- warning: 入力不足、課題未選択、事前条件不足、対象データなしなどの注意喚起
- primary や info は原則使わない。新規追加時は success / warning のどちらかに寄せる

## 文言ルール

- confirm dialog は「何を実行するか」を本文で先に示す
- 詳細ボックスには「何が記録・更新・削除されるか」を箇条書きで置く
- 必要な場合だけ、実行後の変化を 1 項目追加する
- success toast は短くする。理由説明を長く書かない

## 実装ルール

- 新規画面では native alert / confirm を直接使わない
- 画面ごとの通知タイトルや inline alert の出し先は createPageFeedback() でまとめる
- 画面側では shared feedback の再実装をせず、文言と details のみを定義する
- fallback の alert / confirm は shared feedback 内に閉じ込め、画面側へ広げない

## 実装例

- 送信確認の見本: [screen-flow-diagram/webapp/js/student/survey/survey.js](screen-flow-diagram/webapp/js/student/survey/survey.js)
- 同意確認の見本: [screen-flow-diagram/webapp/js/student/survey/consent.js](screen-flow-diagram/webapp/js/student/survey/consent.js)
- 削除確認の見本: [screen-flow-diagram/webapp/js/teacher/account/account.js](screen-flow-diagram/webapp/js/teacher/account/account.js)
- リセット確認の見本: [screen-flow-diagram/webapp/js/teacher/task/task.js](screen-flow-diagram/webapp/js/teacher/task/task.js)
- 入力不足の inline alert の見本: [screen-flow-diagram/webapp/js/teacher/account/account.js](screen-flow-diagram/webapp/js/teacher/account/account.js)
- 軽い完了 toast の見本: [screen-flow-diagram/webapp/js/student/editor/editor.js](screen-flow-diagram/webapp/js/student/editor/editor.js)
