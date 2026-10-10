# GitHub Actions CI（AWS接続なし）

## 範囲と安全境界

本番7イメージの手動ビルド検証はCIとは独立した
[成果物検証手順](./production-image-validation.md)を参照する。Registry pushやAWS連携は行わない。

[workflow](../../.github/workflows/ci.yml)はpushと通常のpull_requestで動作する。
GitHub hostedの使い捨てUbuntu 24.04 runnerを使い、contents:readのみ指定する。
checkout credentialは保持しない。OIDC、AWS role、Secrets/Variables、Environment、
SSM、本番デプロイは使わない。pull_request_targetやself-hosted runnerは使わない。
同じPR/refの古いCIはcancelするが、この設定は将来の本番CDに流用しない。

未信頼PRのテストコードも使い捨てrunnerで実行する。Dockerを操作するShell jobは
runner内では強い権限を持つため、本番資格情報・社内network・永続runnerを付けない。
fork PRはGitHubの実行承認方針に従い、承認前にworkflow/テスト変更を確認する。
公開リポジトリのログ・artifactには合成fixtureの結果のみ保存する（7日）。
成果物保存用のGitHub runtime tokenはActionsが提供するもので、AWS認証ではない。

## テスト戦略と既存資産

既存資産をreuse-with-editsする。JavaはJUnit Jupiter、Servlet/JSPアプリのunit/
mocked HTTP等をGradle標準testで実行する。Shellは既存18スイートを再利用する。
業務仕様・DB/API・状態ルール・本番安全条件は変更しない。

| job | 環境・実行 | PASS条件・制約 |
|---|---|---|
| Java build and tests | gradle:8.10.2-jdk21、gradle --no-daemon clean build --warning-mode all | command exit0、JUnit XMLあり、failure/error0。Javaのskip件数は明示する |
| Shell syntax and deployment regressions | Ubuntu runner + age/jq/curl/openssl/flock/dash/perl/Docker Compose、systemd-analyze入り専用Linux image | 構文成功、集計回帰成功、18スイートexit0、skip0、完了marker |

Java toolchainは[build.gradle](../../build.gradle)の21、CI Gradleは本番Dockerfileの
8.10.2と揃える。追跡されたGradle wrapperはない。JUnit標準実行にはMySQL不要。
DB接続はloopbackのport1/合成名を明示し、DB_*を開発.envから読まない。
GEMINI_API_KEY、STUDENT_CREDENTIAL_KEY等の実値は使わない。

### 自動実行するShellスイート

- Linux内でmock/fixture回帰: source-manifest、migration-preflight、
  operation-config-sync、deployment-state、deployment-recovery、backup、
  database-lock、migration-privilege-cleanup、shell-invocation、tls-workflow、
  restore、backup-systemd。
- runner上の実ツール: deployment-db-config-hash、backup-operation-config、
  age-recipient、db-admin-binary-mode、backup-roundtrip、nginx-startup。
- 追加: 全追跡/新規Shellの宣言interpreterによる構文確認、JUnit集計の正常/
  失敗/skip/破損/未生成ケースの回帰、Shell統合結果の件数不足/skip/非0終了/
  完了marker欠落を拒否する回帰。

12スイートはnetwork none/read-onlyのLinux containerで実行。
6スイートはローカルDocker endpointでCompose/imageを確認する。
準備scriptはppe-ci-shell:local、ppe-db:local、ppe-backup:localをbuildする。
Nginx試験は試験固有imageをbuildして削除する。基底imageとapt/apk/Gradle依存の
初回取得は外部通信するが、AWS API・ACME・Gemini実APIは呼ばない。

MySQL roundtripは試験専用project/network/Volumeと合成鍵を作り、
生徒/教師・コードログ・提出・評価の暗号化dump→別DB復元→再読込一致、
追加DDL後の保持、部分DDL失敗の残存、非空DB復元拒否を確認して後片付けする。
backupはAWS mock、TLSは合成証明書/mock Certbotとlocal Nginx。
実S3/KMS/監視/Certbot更新の確認ではない。

既存統合runnerはtest imageを選択可能にし、containerログを呼出user所有で作る。
CI wrapperは18件とzero skipを追加検証する。本番スクリプトは変更しない。

### CIで未実行となるJavaテスト

既存Assumptionsでopt-inするDB、HTTP、browser fixture、Gemini operational
candidateは対応する環境変数を設定しないためskipする。
School/TeacherAccount/TeacherSelf/StudentAccount/TeacherTask/Distribution/Progress/
Review/Survey、StudentExercise/Submission/Evaluation/CodeLog/Survey等は専用schema
が必要。一部は固定test hostやHTTP runtimeも要求する。
これらを汎用CIのために緩和しない。JUnit XML/HTMLとsummaryでskipを確認する。
全JUnitを検出して実行するが、全業務DB/認証HTTP/browser E2Eの受入ではない。
Shellの実MySQL試験はJava DAO統合の代替ではない。

## ローカル検証

AWS接続せず、新規の試験用出力directoryを使う。Shell準備はtest-only local tagsを
buildするので、共有daemon上で同tagの試験が走っていないことを確認する。
Docker endpointはlocal Unixのみ。既存開発Composeをup/downしない。

```bash
bash scripts/ci/check-shell-syntax.sh
bash scripts/ci/tests/reporting-test.sh
bash scripts/ci/tests/runner-gates-test.sh
bash scripts/ci/prepare-shell-tests.sh
bash scripts/ci/run-shell-tests.sh /absolute/new/private/results
```

JavaはJava21/Gradle8.10.2の隔離環境で以下を実行する。

```bash
gradle --no-daemon clean build --warning-mode all
bash scripts/ci/summarize-junit.sh build/test-results/test
```

ホストの.envや本番credentialをその隔離環境へ渡さない。
workflowはactionlintで検証し、初回push後はGitHub実runner上の両jobとartifact/
summary/skipを確認する。ローカルの成功だけでActions実行済みと判定しない。

## 今後

Actionsはfull SHA固定。base imageはversion tag固定でありdigest固定ではない。
cache共有や追加credentialは今回導入しない。main required checksやbranch protection
はGitHub側の別承認作業。Pages/Copilotは独立workflowで、このCIの成功に含めない。
2026-10-10決定の[半自動デプロイ方針](./semi-automatic-deployment-policy.md)に従い、
今後はECR Privateへのartifact保存とOIDC、EC2での人間による成果物受入を設計する。
ActionsからのSSM自動deploy・本番Environment承認・本番rollbackは今回のスコープ外。

## ローカル検証記録（2026-10-10 JST）

| コマンド | 結果 |
|---|---|
| actionlint v1.7.7 .github/workflows/ci.yml | 構文/式/job/action入力検証exit0 |
| bash scripts/ci/check-shell-syntax.sh | 59 Shell files、exit0 |
| bash scripts/ci/tests/reporting-test.sh | 合成JUnit集計正常/skip/失敗/破損/未生成拒否、exit0 |
| bash scripts/ci/tests/runner-gates-test.sh | 18件不足/skip/非0/完了欠落拒否、exit0 |
| bash scripts/ci/prepare-shell-tests.sh | 専用3image準備成功、exit0 |
| bash scripts/ci/run-shell-tests.sh <new-private-session-directory> | 18スイート/144 printed assertion groups、失敗0/skip0、exit0 |
| gradle --no-daemon clean build --warning-mode all | Java21/Gradle8.10.2 container、WAR/build成功、107スイート/392件、260成功/0失敗/132skip、exit0 |
| bash scripts/ci/summarize-junit.sh build/test-results/test | 上記件数を検証、exit0 |
| git diff --check | exit0 |

初回のJava fixture不足とLinux専用imageの依存不足は
[エラーレポート](./error-report.md)へ残し、テスト側だけで解消した。
Java source本体・build.gradle・本番運用scriptの変更はない。
12 Shell suitesはLinux container、6はmacOSホストのlocal Dockerで実行した。
ローカルimage architectureはarm64であり、GitHubのUbuntu amd64実runnerとの
差異は初回push後に確認する。Actions artifact upload/summaryとfork PR承認も未確認。
アプリ/Tomcat起動、認証済み業務E2E、実AWS/ACME/Geminiは対象外・未実施。
試験固有container/Volumeは削除済み。準備したlocal test imageは再実行用に保持した。
