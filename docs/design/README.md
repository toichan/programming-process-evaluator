# design

実装前に整理しておく状態遷移、操作可否、例外処理、責務分担などの詳細設計を管理する。

## ディレクトリ方針

- `teacher/`: 教師向け機能の設計別紙
- `student/`: 生徒向け機能の設計別紙
- `admin/`: 管理者向け機能の設計別紙

## 運用ルール

- `docs/function-specification.md` には要約のみ記載し、詳細ルールは本ディレクトリ配下を参照する。
- 1機能1ファイルを基本とし、肥大化した場合にのみ機能配下のサブディレクトリへ分割する。
- 状態遷移、操作可否、例外処理、サーバ側責務は同じファイル内で管理する。

## 現在の構成

- `teacher/prompt-state-rules.md`
- `teacher/distribution-state-rules.md`
- `teacher/task-state-rules.md`
- `student/README.md`
- `admin/README.md`
