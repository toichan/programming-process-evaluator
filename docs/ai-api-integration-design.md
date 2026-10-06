# 生成AI API連携設計書

## 1. 目的
本書は、システムが生成AI APIへアクセスする場面における、送信プロンプトと添付データを定義する。

対象は次の3場面とする。

1. 課題評価（評価生成 + 評価理由生成 + 試行錯誤プロセス分析）
2. 揺らぎ項目抽出（プロンプト設計支援）
3. 評価例生成（再評価前シミュレーション）

注記:
- 全体再評価は新しい推論種別ではなく、上記で確定した評価ルールを対象データに一括適用する実行フェーズとして扱う。ただし教師確認用previewでは、対象生徒ごとに最新提出を先行評価し、教師が確定した場合は同じ応答を再評価結果へ再利用する。キャンセル時は評価履歴へ保存しないが、外部API費用は発生する。
- この先行評価も通常の課題評価と同じ匿名化、必要最小限の添付データ、応答検証、失敗処理に従う。直接識別子は送信せず、Gemini送信向け研究用識別子を使用する。

## 2. 共通設計

### 2.1 共通メタデータ
すべてのAIリクエストに次を付与する。

- request_id: 呼び出し追跡ID
- requested_at: リクエスト時刻（ISO 8601）
- model_id: モデル識別子
- model_version: モデル版
- feature_name: 呼び出し機能名
- task_id: 課題ID
- prompt_version: プロンプト版
- rubric_version: ルーブリック版
- actor_role: student または teacher または batch
- consent_status: agreed または disagreed または unknown
- locale: ja-JP

### 2.2 匿名化方針
- 氏名、出席番号、自由記述中の個人識別情報は送信前に除去または置換する。
- 学習者識別には、ログイン用ID（`student_profiles.student_code`）ではなく、`research_subject_identifiers.research_subject_code` を用いる。この識別子は氏名・出席番号・ログイン用IDから推測できない値とし、生成後は変更しない。
- 同意状態（`consent_status`）は呼び出し時の記録用メタデータであり、AI評価・コードログ収集の実行可否を左右しない。研究データとしての集計・外部エクスポートの対象を絞り込む用途にのみ用いる。

### 2.3 出力形式
- 応答は必ずJSONとする。
- JSONスキーマ検証に失敗した場合は再試行する。
- 応答文を画面表示する場合も、JSON内の所定フィールドから描画する。

### 2.4 失敗時処理
1. 同一ペイロードで1回再試行
2. 不要フィールドを削減した簡略ペイロードで1回再試行
3. 失敗時はユーザーに再実行可能な状態を提示し、監査ログに記録

### 2.5 データ添付方針
- 生成AIには、機能実行に必要なデータのみを添付して送信する。
- 不要な履歴や重複データは送信しない。
- 応答の根拠として使用したログIDを保存し、追跡可能にする。

### 2.6 Gemini API キーの設定と疎通確認

1. API 管理者が [Google AI Studio の API キー管理画面](https://aistudio.google.com/apikey)で、利用する Google アカウント・プロジェクトの API キーを発行し、Gemini API の利用条件、データ取扱い、料金、レート制限を確認する。
2. ローカル疎通確認では、キーをリポジトリルートの Git 管理外 `.env` の `GEMINI_API_KEY` に設定する。値をチャット、ソースコード、`.env.sample`、ログ、スクリーンショット、コミットへ含めない。`.env` が存在しない場合は `.env.sample` をコピーして作成する。
3. アプリコンテナを再作成して環境変数を渡し、値を表示せず設定済みかだけ確認する。

```sh
docker compose up -d --force-recreate app
docker compose exec -T app sh -c 'if [ -n "$GEMINI_API_KEY" ]; then echo "GEMINI_API_KEY is set"; else echo "GEMINI_API_KEY is missing"; exit 1; fi'
```

4. 実データを使う前に、実在する氏名・ログイン ID・提出コード・コードログを含まない合成入力だけで疎通を確認する。疎通確認時も API 利用料が発生し得るため、管理者が予算・利用上限を確認する。
5. キー未設定または無効時は評価を成功扱いにせず、評価要求を失敗として記録する。API キーはリクエストヘッダーで送信し、URL・アプリログ・例外メッセージへ出力しない。
6. ローカル `.env` は本番用の秘密情報管理手段ではない。本番環境では AWS Secrets Manager 等の組織承認済みシークレットストアから実行時に注入し、アクセス権・ローテーション・失効担当者を定める。本番公開前に [本番運用条件](./system-configuration/production-operations-decisions.md) の API 上限・費用アラート・キー管理者を決定する。

疎通テストは通常の単体テストではスキップされ、明示的に指定した場合だけ1回の外部 API 呼び出しを行う。評価クライアントは、Google AI Studio の Get Started ガイドに合わせて `POST /v1beta/interactions` を使用し、`x-goog-api-key` と `Api-Revision: 2026-05-20` をヘッダーに指定する。現在の疎通テストモデルは `gemini-3.7-flash` とし、[Gemini 3.7 Flash のモデル情報](https://ai.google.dev/gemini-api/docs/models/gemini-3.7-flash)でモデルIDを確認した。モデル一覧・詳細に存在することは、特定プロジェクトでの生成枠の利用可否を保証しない。利用可能なモデルはプロジェクトごとに異なるため、[Gemini API モデル一覧](https://ai.google.dev/gemini-api/docs/models)で確認し、実行前にプロジェクトの利用可否・料金・上限を確認する。旧候補 `gemini-2.5-flash` はこのAPIキーでは「新規利用者には利用不可」と返されたため、疎通モデルに使用しない。

Interactions API の入力には評価指示を `system_instruction`、匿名化済み評価データを `input` として分けて渡す。`response_format` でJSON MIMEタイプと出力スキーマを指定し、REST応答の `steps` から `model_output` のテキストだけを取り出して、アプリケーション側でも評価スキーマを検証する。

旧 `generateContent` エンドポイントはHTTP 402で拒否された。2026-10-02のクレジット購入後にInteractions APIへ合成入力を再送したところ、最初はHTTP 400だったため、ガイドの例に合わせて構造化出力スキーマを基本型・必須項目のみに簡素化した。2026-10-03の診断付き合成スモークテストでは、`gemini-3.8-flash` に続き `gemini-3.7-flash` もHTTP 503で拒否され、どちらも「currently experiencing high demand」とのメッセージを受信した。続いて、キーを `x-goog-api-key` ヘッダーで送るListModels要求はHTTP 200で、2ページにわたり候補一覧を確認した。`gemini-3.7-flash`、`gemini-3.8-flash`、`gemini-2.5-pro`、`gemini-3.5-flash-lite` 等は一覧に含まれたが、`gemini-3.8-flash-lite` は一覧に含まれなかった。ただし `supportedGenerationMethods` は一覧モデルで `generateContent` を示すだけで、Interactions APIの生成利用可否を保証しない。さらに `gemini-3.7-flash` に `input` と `store:false` のみを指定した最小Interactions要求はHTTP 200となり、`OK` の生成応答を取得した。応答ヘッダーには `x-request-id`、`x-goog-request-id`、`traceparent` は見当たらなかった。よってキー・ネットワーク・最小Interactions要求は動作しているが、先行する503が一時的な混雑だったか、構造化出力等の追加パラメーターとの組み合わせによるかは未確定。実データは送信していない。

#### 2026-10-03 段階診断（合成データ）

同一の `gemini-3.7-flash`、Interactions API (`POST /v1beta/interactions`)、`x-goog-api-key`、`Api-Revision: 2026-05-20` で、A〜Gの入力を変えて実行した。全要求に `Accept: application/json` も付与した。A〜Fでは `store:false`、Gでは本番クライアントと同じく `store` を省略した。外部APIへの要求は各ケース1回で、最初に503となったBだけ合計5回まで確認した。各要求は合成データのみを使用し、APIキーはヘッダーから出力・記録していない。

| Test | 追加した条件 | HTTP | latency | 結果 |
|---|---|---:|---:|---|
| A | 短いテキスト入力、構造化出力なし | 200 | 3.379秒 | `OK` |
| B | JSON MIME typeのみ、schemaなし | 503 | 1.907秒 | `service_unavailable`。`Retry-After: 30` |
| C | 最小の整数 `score` schema | 503 | 4.043秒 | `service_unavailable`。`Retry-After: 30` |
| D | 本番JSON Schema、短い入力 | 200 | 8.779秒 | JSON評価オブジェクトを生成 |
| D2 | Dに `generation_config.max_output_tokens: 4096` を追加 | 503 | 3.227秒 | `service_unavailable`。`Retry-After: 30` |
| E | 本番schema・generation config・評価指示、短い合成評価データ | 503 | 7.027秒 | `service_unavailable`。`Retry-After: 30` |
| F | 本番相当構造の完全な合成評価ペイロード、`store:false` | 503 | 8.394秒 | `service_unavailable`。`Retry-After: 30` |
| G | Fと同一の合成ペイロードで `store` のみ省略 | 503 | 15.217秒 | `service_unavailable`。`Retry-After: 30` |

Bを同一bodyで4回再試行した結果は、Attempt 2=503 (13.608秒)、Attempt 3=503 (7.063秒)、Attempt 4=503 (2.251秒)、Attempt 5=200 (2.346秒、JSON `score: 1`)。したがってBは5回中4回503・1回200であり、同一条件で常に失敗するわけではない。503応答は `error.code=service_unavailable`、`error.status` はなし、messageはモデルが高需要のため後で再試行するよう案内する内容だった。全503応答で `Retry-After: 30` が返った。観測した応答ヘッダーにrequest ID / trace IDはなかった。

診断要求は `Retry-After: 30` を受信した一方、Bの再試行間隔はレスポンス時間込みで概ね19〜22秒となり、各回30秒を満たしていない。再試行系列は最大5回という上限内だが、間隔の遵守は不十分であり、今後同条件を再試験する場合は `Retry-After` を優先する。全ケースに `Accept: application/json` を付けたため、Acceptヘッダーを送らない本番クライアントとの完全なHTTPヘッダー一致は未確認。またF/Gは実データを含まない本番相当形の合成ペイロードであり、実際の生徒評価ペイロードによる試験ではない。

観測上、200から503へ変わった最初のケースはBだが、これはパラメーターが原因だと確定する境界ではない。後続のより複雑な本番schemaを使うDは200である一方、D2は503となり、同一のBも5回中に503と200の両方を返した。この非単調な結果は「schemaが複雑だから失敗」「max_output_tokensが原因」「本番相当の入力サイズが原因」のいずれも裏付けない。時間変動・モデル側の一時的な処理状況と各設定の影響を切り分けられておらず、503のmessageだけでGoogle側capacityを確定もできない。

現時点の証拠:

- **APIキー・モデル一覧**: キーがコンテナに設定されていることは値を表示せず確認済み。ListModelsはHTTP 200で `gemini-3.7-flash` を含む一覧を返した。これはInteractionsでの生成権限・容量・Structured Output可用性の証明ではない。
- **Interactions API**: AがHTTP 200で、API方式そのものが全面的に利用不能ではない。A〜Gは同一endpoint/method/auth/revisionを使い、入力条件で結果が変化した。ただし `Accept` ヘッダーは本番と一致するか未確認。
- **Structured Output / schema**: B/C/E/F/Gで503、Dではproduction schemaで200。schemaの有無や複雑さのみを原因とする仮説を確定できない。
- **max_output_tokens**: Dは指定なしで200、D2は4096指定で503だが、それぞれ1回ずつの異なる時間帯の試験であり、因果関係は未確認。
- **ペイロードサイズ**: 短い入力のD/Eおよび合成の大きなFの結果が混在しており、サイズ起因は確定できない。
- **モデル固有 / Googleサービスcapacity**: 全段階を3.7 Flashで行ったため、別モデルとの比較は今回していない。高需要messageと同一条件での503/200混在は一時的な処理変動と整合するが、単独では根本原因の証明にならない。
- **quota / billing / key restrictions**: 今回のInteractionsエラー本文にquota・billing・APIキー制限を明示する兆候は見られず、最小要求とBの成功も確認した。ただしConsole設定は取得できないため、人間による確認が必要。

本番評価要求は `GeminiEvaluationClient` が `system_instruction`、匿名化評価JSONの `input`、`response_format` のschema、`generation_config.max_output_tokens: 4096` を送信し、`store` は指定しない。以前の最小疎通テストは `store:false` を指定していた。A〜Fの段階診断も `store:false`、GはFと同じbodyからその指定だけを外したが、F/Gはどちらも503だったため、この1組だけではstore設定の因果関係は判断できない。研究・生徒データを扱う前に、保存方針を確認してから本番クライアントのstore指定を判断する。

Google AI Studio / Google Cloud Consoleで人間が確認する項目:

1. APIキーが想定プロジェクトに属し、Generative Language APIへのキー制限・IP制限・API制限に矛盾がないこと。
2. AI Studioの利用枠 / tier、当月利用量、費用上限・請求状態。
3. 対象プロジェクトのGemini APIのrate limit / quotaと、発生時刻付近の利用状況・エラー。
4. 研究データ利用時に適用されるデータ利用・保持方針、およびInteractionsの `store` 既定値を許容できるか。

本番workerは最大3試行で、同じ処理内に指数backoff・jitterなしで再試行する。408、429、500以上を再試行対象とし、503も対象になる。最終試行では一部のログsnapshot / 入出力情報を簡略化するため、試行別の観測にworker retryを通すと初回の失敗情報が追いにくい。今回はworkerを介さず直接HTTPで診断した。改善案として、診断では試行ごとのstatusを記録し、再試行を無効にする。本番運用ではRetry-Afterを優先し、それがない場合は上限付き指数backoff＋full jitterを用い、408/429/500/502/503/504など一時障害に限定して最大3回程度までとする。400系の恒久的な入力・認証エラーは再試行せず、総処理deadlineを超えないようにする。これは提案であり、この診断では本番retry実装を変更していない。

詳細確認が必要なときは、実データではなく合成スモークテストに限り、`GEMINI_API_SMOKE_TEST=true` と `GEMINI_API_DIAGNOSTICS=true` を同時に設定する。診断出力はプロバイダーの `error.message` のみ（最大500文字）で、APIキーを伏せる。通常実行ではプロバイダー応答本文を表示・保存しない。503は短時間の再試行で解決し得るため、連続呼び出しは避け、間隔を置いて再確認する。成功した生成応答が確認できるまではAI評価完了として扱わない。

```sh
docker compose exec -T -e GEMINI_API_SMOKE_TEST=true app gradle test \
  --tests 'control.evaluation.GeminiEvaluationSmokeTest' \
  --warning-mode all
```

## 3. 場面別設計

## 3.1 課題評価

### 3.1.1 目的
次を1回の推論で同時に生成する。

- 2観点5段階の評価
- 観点別評価理由
- 試行錯誤プロセス分析（転換点、停滞点、支援案）

### 3.1.2 呼び出しトリガー
- 生徒提出確定時
- 教師が評価詳細を表示した時（未生成または再生成要求時）
- 全体再評価バッチ実行時

### 3.1.3 添付データ
- task
  - task_title
  - task_description
  - constraints
  - testcases
- rubric
  - viewpoint_definitions（2観点）
  - level_definitions（1-5）
- timeline_logs
  - snapshots（30秒間隔）
  - save_events
  - run_events（success, error_type, stdout, stderr）
- submission
  - submitted_code
  - submitted_at
  - testcase_check_result
- prompt_evaluation_settings
  - common_prompt
  - ambiguity_item_instructions
  - evaluation_examples

### 3.1.4 送信プロンプトテンプレート
System:
あなたは高校情報科の評価者です。入力された課題情報、ルーブリック、コードログを根拠に、2観点を5段階で評価してください。推測ではなくログ根拠を優先し、根拠のない断定を禁止します。出力は指定JSONのみとします。

User:
次のデータを課題評価してください。

- task: {{task_json}}
- rubric: {{rubric_json}}
- timeline_logs: {{timeline_logs_json}}
- submission: {{submission_json}}
- prompt_evaluation_settings: {{prompt_evaluation_settings_json}}

出力要件:
- scores.thinking_expression_level: 1-5
- scores.proactive_attitude_level: 1-5
- reasons.thinking_expression_reason: 文字列
- reasons.proactive_attitude_reason: 文字列
- process_analysis.pattern_label: 文字列
- process_analysis.turning_points: 配列
- process_analysis.stagnation_points: 配列
- process_analysis.teacher_support_suggestions: 配列（最大3件）
- confidence: 0.0-1.0
- evidence_refs: 根拠ログID配列
- warnings: 配列

`turning_points[].log_id` と `stagnation_points[].log_id` は、送信した `timeline_logs[].log_id` の値だけを参照する。`evidence_refs[]` はコードログIDまたは、そのログに含まれるコード実行IDを参照できる。数値または `log_` 接頭辞付き表記はコードログ、`run_` 接頭辞付き表記はコード実行として保存時に実DBのIDへ対応付ける。未知のIDや重複した根拠IDを含む応答は不正として再試行する。

### 3.1.5 応答JSON例
{
  "scores": {
    "thinking_expression_level": 4,
    "proactive_attitude_level": 3
  },
  "reasons": {
    "thinking_expression_reason": "条件分岐の再設計で誤判定が解消され、要件適合性が向上したため。",
    "proactive_attitude_reason": "実行失敗後に複数回の検証を行っているが、検証観点が一部に偏っているため。"
  },
  "process_analysis": {
    "pattern_label": "iterative_refinement",
    "turning_points": [
      { "log_id": "log_018", "summary": "入力処理を関数化し不具合が減少" }
    ],
    "stagnation_points": [
      { "log_id": "log_011", "summary": "同一エラーを3回反復" }
    ],
    "teacher_support_suggestions": [
      "境界値テスト観点を先に固定する",
      "失敗原因を1行コメントで仮説化する"
    ]
  },
  "confidence": 0.81,
  "evidence_refs": ["log_011", "log_018", "run_024"],
  "warnings": []
}

## 3.2 揺らぎ項目抽出

### 3.2.1 目的
評価者間で解釈が分かれやすい評価項目を抽出し、明確化ルール案を提示する。

### 3.2.2 呼び出しトリガー
- 教師がプロンプト設計画面で「確定して揺らぎ項目を生成」を実行した時

### 3.2.3 添付データ
- task
  - task_description
  - constraints
  - expected_outputs
- rubric
  - viewpoint_definitions
  - level_definitions
- evaluation_prompt_draft
- anonymized_evaluation_samples

### 3.2.4 送信プロンプトテンプレート
System:
あなたは評価基準レビュー担当です。評価の解釈が分かれやすい箇所を抽出し、曖昧性の理由と明確化ルール案を提示してください。出力は指定JSONのみとします。

User:
次の入力をもとに揺らぎ項目を抽出してください。

- task: {{task_json}}
- rubric: {{rubric_json}}
- evaluation_prompt_draft: {{prompt_json}}
- anonymized_evaluation_samples: {{samples_json}}

出力要件:
- ambiguity_items: 配列
- item.title
- item.ambiguity_reason
- item.risk_level（high, medium, low）
- item.clarification_question
- item.recommended_rule_text

### 3.2.5 応答JSON例
{
  "ambiguity_items": [
    {
      "title": "試行錯誤回数の十分性",
      "ambiguity_reason": "回数基準がなく、少数回で高評価となる可能性がある。",
      "risk_level": "high",
      "clarification_question": "同一失敗の反復を試行錯誤に含めるか。",
      "recommended_rule_text": "同一原因エラーの連続は1試行として計上し、異なる仮説変更を伴う修正のみ加点対象とする。"
    }
  ]
}

## 3.3 評価例生成

### 3.3.1 目的
保存前の評価ルールでサンプル採点を行い、判定の偏りや不安定点を確認する。

### 3.3.2 呼び出しトリガー
- 教師がプロンプト設計画面で「評価例を生成」を実行した時

### 3.3.3 添付データ
- merged_evaluation_rule
  - evaluation_prompt
  - ambiguity_resolutions
  - additional_instructions
- anonymized_submission_samples
  - sample_id
  - code
  - run_summary
  - testcase_result

### 3.3.4 送信プロンプトテンプレート
System:
あなたは評価シミュレーターです。与えられた評価ルールを適用して複数サンプルを採点し、採点理由と判定が割れやすいポイントを示してください。出力は指定JSONのみとします。

User:
次の入力で評価例を生成してください。

- merged_evaluation_rule: {{rule_json}}
- anonymized_submission_samples: {{samples_json}}

出力要件:
- simulated_results: 配列
- simulated_results[].sample_id
- simulated_results[].scores（2観点）
- simulated_results[].reasons（2観点）
- variance_alerts: 配列
- pre_reeval_checklist: 配列

### 3.3.5 応答JSON例
{
  "simulated_results": [
    {
      "sample_id": "smp_001",
      "scores": {
        "thinking_expression_level": 3,
        "proactive_attitude_level": 4
      },
      "reasons": {
        "thinking_expression_reason": "要件は満たすが境界条件テストが不足。",
        "proactive_attitude_reason": "失敗要因を切り分ける修正が段階的に実施されている。"
      }
    }
  ],
  "variance_alerts": [
    "出力整形ミスを重大減点するかで評価差が拡大"
  ],
  "pre_reeval_checklist": [
    "軽微な表示差分の扱いを固定する",
    "同点時の判定優先ルールを設定する"
  ]
}

## 4. 全体再評価フェーズ

### 4.1 位置づけ
- 新規推論ではなく、3.2と3.3で確定した評価ルールの一括適用。

### 4.2 必須記録
- reeval_job_id
- prompt_version_from
- prompt_version_to
- target_count
- success_count
- failure_count
- diff_summary
- executed_by
- executed_at

## 5. API I/O最小スキーマ

### 5.1 Request
- meta
- context
- inputs
- output_schema

### 5.2 Response
- status
- result
- confidence
- warnings
- trace

推奨拡張フィールド:
- used_evidence_ids
- unresolved_ambiguities

## 6. 実装チェックリスト

1. 送信前匿名化が必ず実行される
2. prompt_versionとrubric_versionを保存する
3. JSONスキーマ検証を通過しない応答を破棄する
4. リトライと失敗通知が実装されている
5. request_idと操作ユーザーIDを監査ログに保存する
6. 全体再評価で旧新差分を保存する
7. 応答で使用した根拠ログIDを保存する
