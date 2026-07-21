# 生成AI API連携設計書

## 1. 目的
本書は、システムが生成AI APIへアクセスする場面における、送信プロンプトと添付データを定義する。

対象は次の3場面とする。

1. 課題評価（評価生成 + 評価理由生成 + 試行錯誤プロセス分析）
2. 揺らぎ項目抽出（プロンプト設計支援）
3. 評価例生成（再評価前シミュレーション）

注記:
- 全体再評価は新しい推論種別ではなく、上記で確定した評価ルールを対象データに一括適用する実行フェーズとして扱う。

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
- 学習者識別には匿名化済みIDのみを用いる。

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
