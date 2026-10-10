# 本番Dockerイメージの隔離ビルド検証

## 範囲

今後の保存・本番反映は[半自動デプロイの正式方針](./semi-automatic-deployment-policy.md)に従う。
本書は認証なし・pushなしのStep 1に限定し、ECR/OIDCや本番deployの実装済み記録ではない。

[専用workflow](../../.github/workflows/production-image-build.yml)は
workflow_dispatchのみ。main上のworkflowから、main履歴に到達可能な40桁SHAを指定する。
検証ハーネスはmainから取得し、buildソースは別directoryに指定SHAでcheckoutして
HEAD一致を確認する。実Docker contextも同じSHAのGit archiveから生成する。
AWS、Secrets、packages:write、Registry push、SSM、本番DB接続、deploymentは使わない。
GitHub hosted Ubuntu 24.04でlinux/amd64の7イメージを生成する。
既存[CI](../../.github/workflows/ci.yml)・本番Dockerfile・Compose・migration・運用状態を変更しない。

## ビルドと検証

[build-production-images.sh](../../scripts/ci/build-production-images.sh)はGit archiveの
固定ソースを新規0700ディレクトリへ展開する。既存prepare-releaseは呼ばない。
理由は同scriptが本番互換タグとREADYを生成するため。今回は固有のCIタグを使い、
結果を検証してタグを削除する。既存イメージの上書き・一括pruneはしない。

- app/tools: 本番Dockerfileの各target。Gradle clean build/test、WAR加工を維持。
- db/runner/broker/nginx/backup: 各既存Dockerfile/contextをそのまま使う。
- 全Image ID、revision、linux/amd64、サイズ、個別build時間を検証・記録。
- tools/srcとGit archiveの全srcを比較。SQL欠落・追加・変更を拒否。
- tools WARのweb.xmlは元ソースと一致。app WARは既存secure cookie加工だけを許容。
  解凍後の全WAR内容を比較し、両WARのmigration SQLも元ソースと比較する。
- toolsのJUnit XMLを集計。既存opt-in skipは明示し、失敗/error/全skipを拒否。
- before/after各buildのdf（1 GiB未満で停止）とDocker disk usageを記録。
  dfはhost filesystem。macOSではDocker VMの空き容量と同一ではない。
- ビルド時は空のDocker configとlocal Unix daemonを使い、個人Registry認証を渡さない。
  外部通信は公開base image・apt/apk・Gradle依存取得に限る。
- inspectionは停止コンテナをcreate/cp/removeして実施。app/DBサービスを起動しない。
- 結果Summary、images.tsv、source.sha256、disk.tsv、docker-disk.txtだけを7日保存する。
  イメージ、WAR、ソース、Docker config、inspection全体はartifactとして公開しない。
  失敗は非0終了しSummaryにもFAILを記録。部分buildを成功扱いにしない。

## 秘密情報検査の限界

build環境の代表的な秘密変数を拒否し、Git archive内の危険なファイル名と
アプリbuild入力のprivate key/age identity/AWS keyパターン、image configのcredential変数を確認。
これは完全なsecret scanではない。任意形式の埋込secret、全中間layer、取得依存の悪性挙動、
動的外部通信先まで証明するものではない。本番credentialをrunnerに設定しない。
基底imageは現行version tagのまま。digest固定・署名・Registry保存は後続工程。

## ローカル実行

```bash
bash scripts/ci/tests/image-build-test.sh
bash scripts/ci/tests/image-assets-test.sh
bash scripts/ci/tests/image-runner-test.sh
bash scripts/ci/build-production-images.sh \
  <mainに含まれる40桁SHA> <信頼できるmain-ref> /absolute/new/private/output
```

共有Docker daemonで同時作業に注意する。ビルドcache/base imageは残るが固有CIタグは
成功/失敗とも削除する。容量不足時も他者のcache/container/volumeを削除しない。
生成ソース・inspection・失敗証跡は指定private outputに残る。

## 受入・停止条件

ローカルでgateテスト・actionlint・構文チェックと実amd64ビルドを検証する。
本番Dockerfile/アプリ/migrationの変更が必要なら変更せず停止して報告する。
GitHub hostedの時間・容量・7image・Summary/artifactの受入は、別途commit/pushと
main上のworkflow登録・手動実行の承認後に行う。ローカル成功をhosted成功とは扱わない。
90分timeoutは暫定。実容量・時間はSummaryで実測して調整する。

## 初回ローカル検証（2026-10-10）

対象SHA: `5921fabddd0b53df07f4c6f8e1415f7ceaae12e1`。
Apple Silicon上のDockerでamd64を指定し7種類のビルド・inspect・実WAR/SQL比較成功。
JUnitは107 suites/392 tests、260 pass、failure/error 0、132 skip。
skipは既存のopt-in DB/HTTP/browser/実APIテストであり、本工程での受入対象外。
appの初回buildは174秒、後続7種類の一括検証は48秒。
最終コードで全cacheを利用した再検証は21秒、exit0。
イメージ表示サイズの合計は約2.33 GB（共有layerを重複計上）。
追加imageを生成したrunのhost df差分は約1.15 GB、最終cache runは約70 MB。
Docker VMの容量・既存cacheがあるため、
空のhosted runnerの容量/所要時間を保証する数字ではない。
hosted初回は暫定5〜20分、image/base/cache/inspectionで5〜8 GB程度の余裕を想定し、
実測で見直す。image sizeは圧縮Registry転送量でもない。
Registry保存・hosted実行・本番への転送は未実施。
