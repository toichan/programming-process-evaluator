const http = require('node:http');

let generations = 0;
const requests = [];

function examples(input) {
  return {
    simulated_results: input.synthetic_submission_samples.map((sample, index) => {
      const logId = index * 10 + 1;
      const code = "print('synthetic example')";
      return {
        sample_id: sample.sample_id,
        synthetic_code: code,
        scenario: sample.description,
        predicted_testcase_result: '合成された推定。実行していません。',
        synthetic_logs: [
          { log_id: logId, event_type: 'manual_save', observed_at: '2026-10-16T10:00:00', snapshot: code },
          {
            log_id: logId + 1, event_type: 'edit', observed_at: '2026-10-16T10:00:30',
            snapshot: code, execution_id: logId, execution_status: 'succeeded',
            error_type: '', standard_input: 'synthetic input', standard_output: 'synthetic output',
            standard_error: ''
          }
        ],
        evaluation: {
          scores: { thinking_expression_level: 3, proactive_attitude_level: 4 },
          reasons: {
            thinking_expression_reason: '合成ログに基づく思考の理由',
            proactive_attitude_reason: '合成ログに基づく態度の理由'
          },
          process_analysis: {
            pattern_label: 'iterative_refinement',
            turning_points: [{ log_id: logId, summary: '合成の保存記録' }],
            stagnation_points: [], teacher_support_suggestions: ['合成記録だけを参照する']
          },
          confidence: 0.8, evidence_refs: [`log_${logId}`, `run_${logId}`],
          warnings: ['合成結果であり実行済みの事実ではありません。']
        }
      };
    }),
    variance_alerts: [], pre_reeval_checklist: ['合成データであることを確認']
  };
}

http.createServer(async (request, response) => {
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (request.method === 'GET' && request.url === '/health') {
    response.end('{"ok":true}');
    return;
  }
  if (request.method === 'GET' && request.url === '/stats') {
    response.end(JSON.stringify({ generations, requests }));
    return;
  }
  if (request.method !== 'POST' || request.url !== '/v1beta/interactions'
      || request.headers['x-goog-api-key'] !== 'synthetic-prompt-acceptance') {
    response.writeHead(403).end('{"error":{"message":"Only synthetic prompt requests are allowed"}}');
    return;
  }
  try {
    let body = '';
    for await (const chunk of request) body += chunk;
    const payload = JSON.parse(body);
    const input = JSON.parse(payload.input);
    if (!payload.response_format?.schema || payload.store !== false
        || !input.task_context || !input.standard_rubric || !input.evaluation_prompt) {
      throw new Error('Incomplete synthetic request');
    }
    generations++;
    const stage = input.synthetic_submission_samples ? 'examples' : 'fluctuations';
    requests.push({ stage, model: payload.model });
    const output = stage === 'examples' ? examples(input) : {
      ambiguity_items: [{
        title: '合成の境界値', ambiguity_reason: '合成例の解釈を固定する',
        risk_level: 'medium', clarification_question: '境界値をどう扱いますか',
        recommended_rule_text: '課題仕様に従って判定する'
      }]
    };
    setTimeout(() => response.end(JSON.stringify({ output_text: JSON.stringify(output) })), 6000);
  } catch (failure) {
    console.error('Synthetic provider rejected a request:', failure.message);
    response.writeHead(400).end('{"error":{"message":"Invalid synthetic request"}}');
  }
}).listen(8099, '0.0.0.0', () => console.log('Synthetic prompt provider listening on 8099'));
