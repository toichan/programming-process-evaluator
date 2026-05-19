// Teacher evaluation page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeEvaluationPage();
});

function initializeEvaluationPage() {
  setupRubricBackToEvaluation();
  setupEvaluationSummary();
  setupTableFiltersAndSort();
  applyFiltersAndSort();
}

function setupTableFiltersAndSort() {
  const changeIds = ['filterSchool', 'filterClass', 'filterLevel', 'filterConsent', 'sortBy'];
  changeIds.forEach(function(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('change', applyFiltersAndSort);
  });

  const inputIds = ['filterThinkingExpr', 'filterAttitudeExpr', 'searchInput'];
  inputIds.forEach(function(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('input', applyFiltersAndSort);
  });
}

function parseScoreCondition(expression, value) {
  const expr = String(expression || '').trim();
  if (!expr) return true;

  const parts = expr.split(',').map(function(item) { return item.trim(); }).filter(Boolean);
  if (parts.length === 0) return true;

  return parts.some(function(part) {
    const m = part.match(/^(<=|>=|=|<|>)\s*(\d+(?:\.\d+)?)$/);
    if (!m) return false;
    const op = m[1];
    const num = Number(m[2]);
    if (op === '=') return value === num;
    if (op === '<') return value < num;
    if (op === '>') return value > num;
    if (op === '<=') return value <= num;
    if (op === '>=') return value >= num;
    return false;
  });
}

function applyFiltersAndSort() {
  const tableBody = document.querySelector('#evaluationTable tbody');
  if (!tableBody) return;

  const rows = Array.from(tableBody.querySelectorAll('tr'));
  if (rows.length === 0) {
    updateSummary([]);
    return;
  }

  const school = (document.getElementById('filterSchool') || {}).value || 'すべて';
  const className = (document.getElementById('filterClass') || {}).value || 'すべて';
  const level = (document.getElementById('filterLevel') || {}).value || 'すべて';
  const consent = (document.getElementById('filterConsent') || {}).value || 'すべて';
  const thinkingExpr = (document.getElementById('filterThinkingExpr') || {}).value || '';
  const attitudeExpr = (document.getElementById('filterAttitudeExpr') || {}).value || '';
  const search = String((document.getElementById('searchInput') || {}).value || '').trim().toLowerCase();
  const sortBy = (document.getElementById('sortBy') || {}).value || 'id';

  rows.forEach(function(row) {
    const schoolOk = school === 'すべて' || row.dataset.school === school;
    const classOk = className === 'すべて' || row.dataset.class === className;
    const levelOk = level === 'すべて' || row.dataset.level === level;
    const consentOk = consent === 'すべて' || row.dataset.consent === consent;

    const thinkingScore = Number(row.dataset.thinking);
    const attitudeScore = Number(row.dataset.attitude);
    const thinkingOk = parseScoreCondition(thinkingExpr, thinkingScore);
    const attitudeOk = parseScoreCondition(attitudeExpr, attitudeScore);

    const searchable = (String(row.dataset.id) + ' ' + String(row.dataset.task)).toLowerCase();
    const searchOk = !search || searchable.includes(search);

    const visible = schoolOk && classOk && levelOk && consentOk && thinkingOk && attitudeOk && searchOk;
    row.style.display = visible ? '' : 'none';
  });

  sortRows(rows, sortBy);
  rows.forEach(function(row) {
    tableBody.appendChild(row);
  });

  const visibleRows = rows.filter(function(row) { return row.style.display !== 'none'; });
  updateSummary(visibleRows);
}

function setupEvaluationSummary() {
  const ids = ['summarySchool', 'summaryClass', 'summaryTask'];
  ids.forEach(function(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('change', updateEvaluationSummary);
  });
  updateEvaluationSummary();
}

function updateEvaluationSummary() {
  const rows = Array.from(document.querySelectorAll('#evaluationTable tbody tr'));
  if (rows.length === 0) return;

  const school = (document.getElementById('summarySchool') || {}).value || 'すべて';
  const className = (document.getElementById('summaryClass') || {}).value || 'すべて';
  const task = (document.getElementById('summaryTask') || {}).value || 'すべて';

  const filtered = rows.filter(function(row) {
    const schoolOk = school === 'すべて' || row.dataset.school === school;
    const classOk = className === 'すべて' || row.dataset.class === className;
    const taskOk = task === 'すべて' || row.dataset.task === task;
    return schoolOk && classOk && taskOk;
  });

  const evaluated = filtered.length;
  const unevaluated = Math.max(1, Math.round(evaluated * 0.35));
  const total = evaluated + unevaluated;
  const completionRate = total > 0 ? Math.round((evaluated / total) * 100) : 0;
  const avgThinking = evaluated > 0
    ? (filtered.reduce(function(acc, row) { return acc + Number(row.dataset.thinking); }, 0) / evaluated).toFixed(1)
    : '0.0';
  const avgAttitude = evaluated > 0
    ? (filtered.reduce(function(acc, row) { return acc + Number(row.dataset.attitude); }, 0) / evaluated).toFixed(1)
    : '0.0';

  const thinkingBuckets = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  const attitudeBuckets = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  filtered.forEach(function(row) {
    const thinkingRounded = Math.max(1, Math.min(5, Math.round(Number(row.dataset.thinking) || 0)));
    const attitudeRounded = Math.max(1, Math.min(5, Math.round(Number(row.dataset.attitude) || 0)));
    thinkingBuckets[thinkingRounded] += 1;
    attitudeBuckets[attitudeRounded] += 1;
  });

  setText('summaryTotal', String(total));
  setText('summaryEvaluated', String(evaluated));
  setText('summaryUnevaluated', String(unevaluated));
  setText('summaryAverageThinking', String(avgThinking));
  setText('summaryAverageAttitude', String(avgAttitude));
  setText('completionPct', String(completionRate) + '%');

  [1, 2, 3, 4, 5].forEach(function(score) {
    const thinkingCount = thinkingBuckets[score];
    const attitudeCount = attitudeBuckets[score];
    const thinkingPct = evaluated > 0 ? Math.round((thinkingCount / evaluated) * 100) : 0;
    const attitudePct = evaluated > 0 ? Math.round((attitudeCount / evaluated) * 100) : 0;

    setText('thinkingCount' + String(score), String(thinkingCount));
    setText('attitudeCount' + String(score), String(attitudeCount));

    const thinkingBar = document.getElementById('thinkingBar' + String(score));
    if (thinkingBar) {
      thinkingBar.style.width = String(thinkingPct) + '%';
    }

    const attitudeBar = document.getElementById('attitudeBar' + String(score));
    if (attitudeBar) {
      attitudeBar.style.width = String(attitudePct) + '%';
    }
  });

  const bar = document.getElementById('completionBar');
  if (bar) {
    bar.style.width = String(completionRate) + '%';
    bar.setAttribute('aria-valuenow', String(completionRate));
  }
}

function setupRubricBackToEvaluation() {
  const evaluationModalEl = document.getElementById('evaluationDetailModal');
  const rubricModalEl = document.getElementById('teacherRubricModal');
  const rubricBackButton = document.getElementById('teacherRubricBackButton');
  if (!evaluationModalEl || !rubricModalEl || !window.bootstrap) return;
  if (!rubricBackButton) return;

  rubricBackButton.addEventListener('click', function() {
    const rubricModal = bootstrap.Modal.getOrCreateInstance(rubricModalEl);
    const evaluationModal = bootstrap.Modal.getOrCreateInstance(evaluationModalEl);
    rubricModal.hide();
    evaluationModal.show();
  });
}
// openEvaluationDetailをグローバル公開
window.openEvaluationDetail = openEvaluationDetail;
function sortRows(rows, sortBy) {
  rows.sort(function(a, b) {
    if (sortBy === 'idDesc') return b.dataset.id.localeCompare(a.dataset.id, 'ja');
    if (sortBy === 'evaluatedDesc') return new Date(b.dataset.evaluated).getTime() - new Date(a.dataset.evaluated).getTime();
    if (sortBy === 'evaluatedAsc') return new Date(a.dataset.evaluated).getTime() - new Date(b.dataset.evaluated).getTime();
    if (sortBy === 'thinkingDesc') return Number(b.dataset.thinking) - Number(a.dataset.thinking);
    if (sortBy === 'thinkingAsc') return Number(a.dataset.thinking) - Number(b.dataset.thinking);
    if (sortBy === 'attitudeDesc') return Number(b.dataset.attitude) - Number(a.dataset.attitude);
    if (sortBy === 'attitudeAsc') return Number(a.dataset.attitude) - Number(b.dataset.attitude);
    return a.dataset.id.localeCompare(b.dataset.id, 'ja');
  });
}

function updateSummary(visibleRows) {
  const count = visibleRows.length;
  const totalThinking = visibleRows.reduce(function(acc, row) {
    return acc + Number(row.dataset.thinking);
  }, 0);
  const totalAttitude = visibleRows.reduce(function(acc, row) {
    return acc + Number(row.dataset.attitude);
  }, 0);
  const avgThinking = count > 0 ? (totalThinking / count).toFixed(1) : '0.0';
  const avgAttitude = count > 0 ? (totalAttitude / count).toFixed(1) : '0.0';

  setText('visibleCount', String(count));
  setText('avgThinkingTop', avgThinking);
  setText('avgAttitudeTop', avgAttitude);
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function toJstIso(datetimeText) {
  if (!datetimeText) {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    const hh = String(now.getHours()).padStart(2, '0');
    const mm = String(now.getMinutes()).padStart(2, '0');
    const ss = String(now.getSeconds()).padStart(2, '0');
    return y + '-' + m + '-' + d + 'T' + hh + ':' + mm + ':' + ss + '+09:00';
  }
  return datetimeText.replace(' ', 'T') + '+09:00';
}

function toJpDateTimeLabel(datetimeText) {
  if (!datetimeText) return '-';
  const parts = datetimeText.split(' ');
  if (parts.length !== 2) return datetimeText;
  const datePart = parts[0].split('-');
  const timePart = parts[1].slice(0, 5);
  if (datePart.length !== 3) return datetimeText;
  return datePart[0] + '/' + datePart[1] + '/' + datePart[2] + ' ' + timePart;
}

function toDecimalScore(value) {
  return (Number(value) || 0).toFixed(1);
}

function toGradeScore(value) {
  const rounded = Math.round(Number(value) || 0);
  return Math.max(1, Math.min(5, rounded));
}

function downloadJsonFile(payload, fileName) {
  const jsonText = JSON.stringify(payload, null, '\t');
  const blob = new Blob([jsonText], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

function buildEvaluationJsonPayload(data) {
  const evaluatedIso = toJstIso(data.evaluatedAt);
  const evaluatedLabel = toJpDateTimeLabel(data.evaluatedAt);
  const thinkingScore = toGradeScore(data.thinking);
  const attitudeScore = toGradeScore(data.attitude);
  const generatedAt = toJstIso('');
  const evalDate = (data.evaluatedAt || '').replace(/[-: ]/g, '').slice(0, 12) || '000000000000';
  const safeStudentId = String(data.studentId || 'unknown');

  return {
    formatVersion: '1.0.0',
    locale: 'ja-JP',
    generatedAt: generatedAt,
    evaluation: {
      evaluationId: 'eval-' + evalDate + '-' + safeStudentId + '-459',
      student: {
        studentId: safeStudentId
      },
      task: {
        taskId: 459,
        taskCode: 'TASK-001',
        title: data.task,
        difficulty: data.difficulty
      },
      period: {
        finalEvaluatedAt: evaluatedIso
      },
      counts: {
        autoSaveCount: 6,
        executionCount: 5
      },
      hero: {
        kicker: 'Student Evaluation',
        title: '評価結果',
        description: 'コードログと実行履歴をもとに、2つの観点で自動評価したサンプル表示です。各観点の総合点と、根拠になったログの要約を確認できます。',
        metaLabel: '対象課題',
        metaSubtext: 'TASK-001 / ' + data.difficulty + ' / 最終評価 ' + evaluatedLabel,
        actions: {
          logUrl: './log.html',
          surveyUrl: '../survey/survey.html'
        }
      },
      overall: {
        scale: {
          min: 1,
          max: 5,
          label: '5段階評価'
        },
        dimensions: [
          {
            key: 'thinking',
            label: '思考力・判断力・表現力',
            score: thinkingScore,
            scoreText: toDecimalScore(data.thinking),
            summaryTitle: '基礎は理解しているが改善の余地がある',
            summaryDescription: '基本的な処理の流れは実装できていますが、文法エラーの再発や型の扱いなどで不安定な箇所が見られます。'
          },
          {
            key: 'attitude',
            label: '主体的に学習に取り組む態度',
            score: attitudeScore,
            scoreText: toDecimalScore(data.attitude),
            summaryTitle: '改善を続けながら課題に向き合っている',
            summaryDescription: '保存・実行・修正の流れが複数回確認でき、エラーや出力結果を見ながら改善しています。'
          }
        ]
      },
      metricBreakdown: [
        {
          metricKey: 'grammar_debugging',
          dimension: 'thinking',
          dimensionLabel: '思考・判断・表現',
          title: '文法デバッグ能力',
          score: thinkingScore,
          description: '初歩的な文法エラーが複数回見られる一方、最終的にはコンパイルエラーを解消できています。'
        },
        {
          metricKey: 'logic_debugging',
          dimension: 'thinking',
          dimensionLabel: '思考・判断・表現',
          title: '論理デバッグ能力',
          score: thinkingScore,
          description: '基本的な処理の流れは理解できていますが、出力誤りの原因特定が不十分な箇所があります。'
        },
        {
          metricKey: 'algorithm_design',
          dimension: 'thinking',
          dimensionLabel: '思考・判断・表現',
          title: 'アルゴリズムの設計と実装',
          score: thinkingScore,
          description: '必要な変数定義と反復構造は実装できていますが、終了条件や型の使い分けに改善余地があります。'
        },
        {
          metricKey: 'readability',
          dimension: 'thinking',
          dimensionLabel: '思考・判断・表現',
          title: 'コードの可読性',
          score: thinkingScore,
          description: 'インデントは概ね揃っていますが、命名やコメント不足により読みやすさの改善が必要です。'
        },
        {
          metricKey: 'persistence',
          dimension: 'attitude',
          dimensionLabel: '態度',
          title: '課題への粘り強さ',
          score: attitudeScore,
          description: '複数回の保存と実行を通して、途中で止めずに解決まで進めています。'
        },
        {
          metricKey: 'motivation',
          dimension: 'attitude',
          dimensionLabel: '態度',
          title: '課題解決への意欲',
          score: attitudeScore,
          description: '実行結果を受けてコード改善を続けており、理解しようとする姿勢が見られます。'
        }
      ],
      reasonFilters: [
        { key: 'all', label: 'すべて' },
        { key: 'thinking', label: '思考・判断・表現' },
        { key: 'attitude', label: '態度' }
      ],
      reasons: [
        {
          reasonId: 'reason-grammar-debugging',
          category: 'thinking',
          categoryLabel: '思考・判断・表現',
          title: '文法デバッグ能力',
          score: thinkingScore,
          body: 'コンパイルエラーは複数回発生していますが、最終的には解消できています。初歩的な文法エラーの再発が見られるため、現時点ではレベル3相当と判断します。',
          details: [
            {
              detailId: 'grammar-1',
              text: 'コンパイルエラーが複数回発生している。',
              evidence: [{ type: 'compile', label: 'compile_id', ids: [185602, 185603, 185604, 185610, 185614] }]
            },
            { detailId: 'grammar-2', text: '変数の宣言漏れ、printf や scanf の format 指定子の誤りなど、初歩的な文法エラーが見られる。', evidence: [] },
            { detailId: 'grammar-3', text: 'エラーメッセージを参考に修正を試みているが、float 型を scanf で "%d" として読み込む、printf で "%d" で出力するなど、完全に解消できていない箇所がある。', evidence: [] },
            { detailId: 'grammar-4', text: 'ただし最終的にはコンパイルエラーを解消しており、時間をかければ文法エラーを解決できると判断できる。', evidence: [] }
          ]
        },
        {
          reasonId: 'reason-logic-debugging',
          category: 'thinking',
          categoryLabel: '思考・判断・表現',
          title: '論理デバッグ能力',
          score: thinkingScore,
          body: '基本的な処理の流れは理解して修正できていますが、最終出力の誤り原因を特定しきれていないため、レベル3相当と判断します。',
          details: [
            { detailId: 'logic-1', text: 'while 文の条件設定、合計と平均の計算など、基本的な処理の流れは理解できている。', evidence: [] },
            { detailId: 'logic-2', text: '初期値の設定（例: x = 0.0）など、細かい部分で間違いが見られる。', evidence: [] },
            { detailId: 'logic-3', text: '最終的には、正の値が入力されている間は合計と平均を計算し続ける課題をクリアしている。', evidence: [] },
            { detailId: 'logic-4', text: 'ただし、最後の出力結果がおかしい（合計に x の値が出力されるなど）箇所があり、その原因を特定しきれていない。', evidence: [] }
          ]
        },
        {
          reasonId: 'reason-algorithm-design',
          category: 'thinking',
          categoryLabel: '思考・判断・表現',
          title: 'アルゴリズムの設計と実装',
          score: thinkingScore,
          body: '課題の基本構造は実装できていますが、終了条件の厳密さや型の使い分けに課題が残るため、レベル3相当と判断します。',
          details: [
            { detailId: 'algorithm-1', text: '課題内容を理解し、必要な変数（x, s, i）を定義し、while 文で入力を繰り返す基本構造は実装できている。', evidence: [] },
            { detailId: 'algorithm-2', text: '入力値が負になるまで繰り返す条件として x >= 0.0 を使っているが、厳密な終了判定としては改善余地がある（例: scanf の戻り値確認）。', evidence: [] },
            { detailId: 'algorithm-3', text: 'float と int の使い分けがあいまいな部分があり、意図した計算結果が得られていない箇所がある。', evidence: [] }
          ]
        },
        {
          reasonId: 'reason-readability',
          category: 'thinking',
          categoryLabel: '思考・判断・表現',
          title: 'コードの可読性',
          score: thinkingScore,
          body: '可読性の基本は満たしていますが、命名とコメントに改善余地があるため、レベル3相当と判断します。',
          details: [
            { detailId: 'readability-1', text: 'インデントは概ね揃っているが、スペースの使い方が一貫していない部分がある。', evidence: [] },
            { detailId: 'readability-2', text: '変数名が x, s, i など抽象的で、sum や average のような具体名の方が望ましい。', evidence: [] },
            { detailId: 'readability-3', text: 'コメントがなく、コードの意図や処理の流れの説明が不足している。', evidence: [] }
          ]
        },
        {
          reasonId: 'reason-persistence',
          category: 'attitude',
          categoryLabel: '態度',
          title: '課題への粘り強さ',
          score: attitudeScore,
          body: '保存と実行を複数回繰り返しながら、構文エラーと出力誤りの両方を修正しています。途中で作業が止まらず、最終的な正解出力まで到達しているため、粘り強く取り組めていると判断できます。',
          evidence: [
            { type: 'count', label: '保存回数', value: 6, unit: '回' },
            { type: 'count', label: '実行回数', value: 5, unit: '回' }
          ]
        },
        {
          reasonId: 'reason-motivation',
          category: 'attitude',
          categoryLabel: '態度',
          title: '課題解決への意欲',
          score: attitudeScore,
          body: '実行結果を確認した直後に条件式と出力文を修正した履歴があり、エラーや結果を無視せず改善につなげています。コードをより正確にしようとする姿勢が見られます。',
          evidence: []
        }
      ]
    }
  };
}

function buildLogJsonPayload(data, logEntries) {
  return logEntries.map(function(entry, index) {
    return {
      id: Number(entry.id),
      student_id: Number(String(data.studentId).replace(/\D/g, '')) || 0,
      task_id: 459,
      content: entry.content,
      submitted_at: index === logEntries.length - 1 ? entry.createdAt : null,
      remaining_work: Math.max(0, 316 - index * 28),
      remaining_proportion: index === 0 ? -10.0 : -6.0,
      auto_save: index === logEntries.length - 1 ? 0 : 1,
      created_at: entry.createdAt
    };
  });
}

function buildCompilesJsonPayload(logEntries) {
  return logEntries.map(function(entry, index) {
    const compileId = 186427 + index;
    const sourceCodeId = String(entry.id);
    let errorMessage = '';
    if (index === 0) {
      errorMessage = "./tmp/source_" + sourceCodeId + "_tmp.c: In function 'main':\\n./tmp/source_" + sourceCodeId + "_tmp.c:7:3: error: 'i' undeclared (first use in this function)\\n   i=0;\\n   ^\\n";
    } else if (index === 1) {
      errorMessage = "./tmp/source_" + sourceCodeId + "_tmp.c: In function 'main':\\n./tmp/source_" + sourceCodeId + "_tmp.c:13:24: warning: format '%d' expects argument of type 'int', but argument 2 has type 'double' [-Wformat=]\\n   printf(\"合計値は%d\\\\n\",s);\\n                        ^\\n";
    }

    return {
      id: String(compileId),
      error_message: errorMessage,
      created_at: entry.createdAt,
      source_code_id: sourceCodeId
    };
  });
}

function buildExecutionsJsonPayload(logEntries) {
  const baseTime = logEntries.length > 0 ? logEntries[logEntries.length - 1].createdAt : '';
  return [
    {
      id: '144477',
      compile_id: '171595',
      input: '1\\n2\\n3',
      output: '最大値は3\\n最小値は1\\n',
      created_at: baseTime
    },
    {
      id: '144478',
      compile_id: '171595',
      input: '5\\n5\\n4',
      output: '最大値は5\\n最小値は4\\n',
      created_at: baseTime
    },
    {
      id: '144479',
      compile_id: '171599',
      input: '5\\n5\\n4',
      output: '最大値は5\\n最小値は4\\n',
      created_at: baseTime
    }
  ];
}

function renderPrimitive(value) {
  if (value === null) return '<span class="json-value json-null">null</span>';
  if (typeof value === 'boolean') return '<span class="json-value json-boolean">' + value + '</span>';
  if (typeof value === 'number') return '<span class="json-value json-number">' + value + '</span>';
  return '<span class="json-value json-string">' + escapeHtml(value) + '</span>';
}

function renderJsonReadable(value) {
  if (value === null || typeof value !== 'object') {
    return renderPrimitive(value);
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return '<div class="json-empty">(空配列)</div>';
    }

    const items = value.map(function(item) {
      return '<li class="json-array-item">' + renderJsonReadable(item) + '</li>';
    }).join('');
    return '<ol class="json-array">' + items + '</ol>';
  }

  const keys = Object.keys(value);
  if (keys.length === 0) {
    return '<div class="json-empty">(空オブジェクト)</div>';
  }

  const rows = keys.map(function(key) {
    return '<div class="json-row"><div class="json-key">' + escapeHtml(key) + '</div><div class="json-content">' + renderJsonReadable(value[key]) + '</div></div>';
  }).join('');
  return '<div class="json-object">' + rows + '</div>';
}

function openEvaluationDetail(button) {
  const row = button.closest('tr');
  if (!row) return;


  // student/evaluation.htmlの構造・クラス・内容をほぼそのまま再現
  const container = document.getElementById('teacherEvaluationDetailContent');
  if (container) {
    const data = {
      studentId: row.dataset.id,
      school: row.dataset.school,
      class: row.dataset.class,
      task: row.dataset.task,
      difficulty: row.dataset.level,
      evaluatedAt: row.dataset.evaluated,
      thinking: Number(row.dataset.thinking),
      attitude: Number(row.dataset.attitude),
      overall: Number(row.dataset.overall),
      consent: row.dataset.consent
    };

    const downloadButton = document.getElementById('downloadEvaluationJsonButton');
    if (downloadButton) {
      downloadButton.onclick = function() {
        const payload = buildEvaluationJsonPayload(data);
        const fileName = 'evaluation-' + String(data.studentId) + '-' + String(data.task).replace(/\s+/g, '-') + '.json';
        downloadJsonFile(payload, fileName);
      };
    }

    const downloadLogsButton = document.getElementById('downloadLogsJsonButton');
    if (downloadLogsButton) {
      downloadLogsButton.onclick = function() {
        const logPayload = buildLogJsonPayload(data, logEntries);
        const compilesPayload = buildCompilesJsonPayload(logEntries);
        const executionsPayload = buildExecutionsJsonPayload(logEntries);

        downloadJsonFile(logPayload, 'log.json');
        downloadJsonFile(compilesPayload, 'compiles.json');
        downloadJsonFile(executionsPayload, 'executions.json');
      };
    }

    // student用CSSをモーダル内にインジェクト
    const studentCssPath = '../../../css/student/evaluation/evaluation.css';
    const studentLogCssPath = '../../../css/student/evaluation/log.css';
    const logEntries = [
      {
        id: 931327,
        createdAt: '2022-04-21 12:56:54',
        content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n}'
      },
      {
        id: 931335,
        createdAt: '2022-04-21 12:57:57',
        content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n  scanf("%d%d%d",&a,&b,&c);\n  if(a>=b && a>=c){\n    printf("最大値は%d\\n",a);\n  }\n}'
      },
      {
        id: 931348,
        createdAt: '2022-04-21 12:59:28',
        content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n  scanf("%d%d%d",&a,&b,&c);\n  if(a>=b && a>=c){\n    printf("最大値は%d\\n",a);\n    if(b>=c){\n      printf("最小値は%d\\n",c);\n    }else{\n      printf("最小値は%d\\n",b);\n    }\n  }\n}'
      },
      {
        id: 931372,
        createdAt: '2022-04-21 13:01:28',
        content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n  scanf("%d%d%d",&a,&b,&c);\n  if(a>=b && a>=c){\n    printf("最大値は%d\\n",a);\n  }else if(b>=c && b>=a){\n    printf("最大値は%d\\n",b);\n  }else{\n    printf("最大値は%d\\n",c);\n  }\n}'
      }
    ];

    const renderLogView = function() {
      const reasonData = {
        reasonFilters: [
          { key: 'all', label: 'すべて' },
          { key: 'thinking', label: '思考・判断・表現' },
          { key: 'attitude', label: '態度' }
        ],
        reasons: [
          {
            category: 'thinking',
            categoryLabel: '思考・判断・表現',
            title: '文法デバッグ能力',
            score: 3,
            body: 'コンパイルエラーは複数回発生していますが、最終的には解消できています。初歩的な文法エラーの再発が見られるため、現時点ではレベル3相当と判断します。',
            details: [
              {
                text: 'コンパイルエラーが複数回発生している。',
                evidence: [{ label: 'compile_id', ids: [185602, 185603, 185604, 185610, 185614] }]
              },
              { text: '変数の宣言漏れ、printf や scanf の format 指定子の誤りなど、初歩的な文法エラーが見られる。', evidence: [] },
              { text: 'エラーメッセージを参考に修正を試みているが、float 型を scanf で "%d" として読み込む、printf で "%d" で出力するなど、完全に解消できていない箇所がある。', evidence: [] },
              { text: 'ただし最終的にはコンパイルエラーを解消しており、時間をかければ文法エラーを解決できると判断できる。', evidence: [] }
            ]
          },
          {
            category: 'thinking',
            categoryLabel: '思考・判断・表現',
            title: '論理デバッグ能力',
            score: 3,
            body: '基本的な処理の流れは理解して修正できていますが、最終出力の誤り原因を特定しきれていないため、レベル3相当と判断します。',
            details: [
              { text: 'while 文の条件設定、合計と平均の計算など、基本的な処理の流れは理解できている。', evidence: [] },
              { text: '初期値の設定（例: x = 0.0）など、細かい部分で間違いが見られる。', evidence: [] },
              { text: '最終的には、正の値が入力されている間は合計と平均を計算し続ける課題をクリアしている。', evidence: [] },
              { text: 'ただし、最後の出力結果がおかしい（合計に x の値が出力されるなど）箇所があり、その原因を特定しきれていない。', evidence: [] }
            ]
          },
          {
            category: 'thinking',
            categoryLabel: '思考・判断・表現',
            title: 'アルゴリズムの設計と実装',
            score: 3,
            body: '課題の基本構造は実装できていますが、終了条件の厳密さや型の使い分けに課題が残るため、レベル3相当と判断します。',
            details: [
              { text: '課題内容を理解し、必要な変数（x, s, i）を定義し、while 文で入力を繰り返す基本構造は実装できている。', evidence: [] },
              { text: '入力値が負になるまで繰り返す条件として x >= 0.0 を使っているが、厳密な終了判定としては改善余地がある（例: scanf の戻り値確認）。', evidence: [] },
              { text: 'float と int の使い分けがあいまいな部分があり、意図した計算結果が得られていない箇所がある。', evidence: [] }
            ]
          },
          {
            category: 'thinking',
            categoryLabel: '思考・判断・表現',
            title: 'コードの可読性',
            score: 3,
            body: '可読性の基本は満たしていますが、命名とコメントに改善余地があるため、レベル3相当と判断します。',
            details: [
              { text: 'インデントは概ね揃っているが、スペースの使い方が一貫していない部分がある。', evidence: [] },
              { text: '変数名が x, s, i など抽象的で、sum や average のような具体名の方が望ましい。', evidence: [] },
              { text: 'コメントがなく、コードの意図や処理の流れの説明が不足している。', evidence: [] }
            ]
          },
          {
            category: 'attitude',
            categoryLabel: '態度',
            title: '課題への粘り強さ',
            score: 4,
            body: '保存と実行を複数回繰り返しながら、構文エラーと出力誤りの両方を修正しています。途中で作業が止まらず、最終的な正解出力まで到達しているため、粘り強く取り組めていると判断できます。',
            evidence: [
              { label: '保存回数', value: 6, unit: '回' },
              { label: '実行回数', value: 5, unit: '回' }
            ]
          },
          {
            category: 'attitude',
            categoryLabel: '態度',
            title: '課題解決への意欲',
            score: 4,
            body: '実行結果を確認した直後に条件式と出力文を修正した履歴があり、エラーや結果を無視せず改善につなげています。コードをより正確にしようとする姿勢が見られます。',
            evidence: []
          }
        ]
      };

      const logs = logEntries.map(function(entry, index) {
        return {
          id: entry.id,
          student_id: data.studentId,
          task_id: 459,
          content: entry.content,
          submitted_at: index === logEntries.length - 1 ? entry.createdAt : null,
          remaining_work: Math.max(0, 450 - index * 80),
          remaining_proportion: index === 0 ? -38 : -20 + index,
          auto_save: index === logEntries.length - 1 ? 0 : 1,
          created_at: entry.createdAt,
          execution_id: 171311 + index
        };
      });

      container.innerHTML = `
        <link rel="stylesheet" href="${studentLogCssPath}">
        <div class="container">
          <section class="sample-section log-hero mb-4">
            <div class="hero-copy">
              <span class="hero-kicker">Student Log</span>
              <h1 class="section-title mb-3">コードログ</h1>
              <p class="hero-description mb-0">自動保存されたコードの履歴を、差分を強調しながら時系列で確認できます。前後の状態を見比べて、試行錯誤の流れを振り返るためのページです。</p>
              <div class="hero-action-row mt-4">
                <button class="btn btn-warning btn-lg" type="button" data-action="back-to-evaluation">評価結果に戻る</button>
              </div>
            </div>
            <div class="hero-meta-card">
              <div class="meta-label">対象課題</div>
              <div class="meta-value">${data.task}</div>
              <div class="meta-subtext">TASK-001 / ${data.difficulty} / 自動保存 30秒間隔</div>
              <div class="hero-status-list mt-3">
                <div class="hero-status-item">
                  <span class="hero-status-name">保存回数</span>
                  <span class="hero-status-pill">6回</span>
                </div>
                <div class="hero-status-item">
                  <span class="hero-status-name">実行回数</span>
                  <span class="hero-status-pill accent">5回</span>
                </div>
              </div>
            </div>
          </section>

          <div class="row g-4 align-items-start">
            <div class="col-xl-8">
              <section class="sample-section log-viewer-section">
                <div class="section-heading-row mb-3">
                  <div>
                    <h2 class="card-title mb-2">差分ビューアー</h2>
                    <p class="text-muted mb-0">各保存時点のコードを時系列で確認できます。</p>
                  </div>
                </div>

                <div class="viewer-controls">
                  <button id="teacherPrevCodeButton" class="btn btn-outline-secondary" type="button">← 前へ</button>
                  <div class="viewer-step-indicator"><span id="teacherStepIndicator">1 / ${logs.length}</span></div>
                  <button id="teacherNextCodeButton" class="btn btn-primary" type="button">次へ →</button>
                </div>

                <div class="viewer-id-row mt-3">
                  <span id="teacherSourceCodeId" class="viewer-id-pill">ソースコードID: -</span>
                  <span id="teacherExecutionId" class="viewer-id-pill">実行ID: -</span>
                </div>

                <div class="code-shell mt-4">
                  <pre id="teacherCodeBlock" class="code-block"></pre>
                </div>

                <div class="viewer-meta-row mt-3">
                  <p id="teacherTimestamp" class="timestamp mb-0"></p>
                  <p id="teacherBoundaryMessage" class="boundary-message mb-0"></p>
                </div>
              </section>
            </div>

            <div class="col-xl-4">
              <div class="sticky-xl-top sidebar-stack">
                <section class="sample-section sidebar-section">
                  <div class="sidebar-switcher mb-3" role="tablist" aria-label="side panel switcher">
                    <button class="sidebar-switch-button is-active" type="button" data-sidebar-panel="timeline">ログ一覧</button>
                    <button class="sidebar-switch-button" type="button" data-sidebar-panel="reasons">評価の理由</button>
                  </div>

                  <div id="teacherTimelinePanel" class="sidebar-panel is-active">
                    <div class="section-heading-row mb-3">
                      <div>
                        <h2 class="card-title mb-2">ログ一覧</h2>
                        <p class="text-muted mb-0">各保存時点の概要を確認できます。</p>
                      </div>
                    </div>
                    <div class="log-timeline-scroll"><div id="teacherLogTimeline" class="log-timeline"></div></div>
                  </div>

                  <div id="teacherReasonsPanel" class="sidebar-panel">
                    <div class="section-heading-row mb-3">
                      <div>
                        <h2 class="card-title mb-2">評価の理由</h2>
                        <p class="text-muted mb-0">評価基準ごとの根拠を確認できます。</p>
                      </div>
                    </div>
                    <div id="teacherReasonFilterGroup" class="reason-filter-group mb-3" role="tablist" aria-label="reason category filter"></div>
                    <div class="log-timeline-scroll"><div id="teacherReasonList" class="reason-list"></div></div>
                  </div>
                </section>
              </div>
            </div>
          </div>
        </div>
      `;

      const codeBlock = container.querySelector('#teacherCodeBlock');
      const timestamp = container.querySelector('#teacherTimestamp');
      const boundaryMessage = container.querySelector('#teacherBoundaryMessage');
      const stepIndicator = container.querySelector('#teacherStepIndicator');
      const sourceCodeId = container.querySelector('#teacherSourceCodeId');
      const executionId = container.querySelector('#teacherExecutionId');
      const prevCodeButton = container.querySelector('#teacherPrevCodeButton');
      const nextCodeButton = container.querySelector('#teacherNextCodeButton');
      const logTimeline = container.querySelector('#teacherLogTimeline');
      const sidebarSwitchButtons = container.querySelectorAll('[data-sidebar-panel]');
      const timelinePanel = container.querySelector('#teacherTimelinePanel');
      const reasonsPanel = container.querySelector('#teacherReasonsPanel');
      const reasonFilterGroup = container.querySelector('#teacherReasonFilterGroup');
      const reasonList = container.querySelector('#teacherReasonList');
      const backButton = container.querySelector('[data-action="back-to-evaluation"]');

      let index = 0;
      let currentReasonFilter = 'all';

      function escapeHTML(text) {
        return String(text)
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#039;');
      }

      function getLogLabel(log) {
        if (log.submitted_at) return '手動提出';
        return log.auto_save ? '自動保存' : '手動保存';
      }

      function getLogPreview(log) {
        const parts = [];
        parts.push('ID: ' + log.id);
        parts.push('残作業量: ' + log.remaining_work);
        if (log.remaining_proportion !== null && log.remaining_proportion !== undefined) {
          parts.push('進捗差分: ' + log.remaining_proportion);
        }
        return parts.join(' / ');
      }

      function splitLines(text) {
        return text.match(/.*(?:\n|$)/g).filter(function(line) { return line.length > 0; });
      }

      function normalizeLineText(text) {
        return text.endsWith('\n') ? text.slice(0, -1) : text;
      }

      function renderCodeLines(lines) {
        return lines.map(function(line, lineIndex) {
          const safeText = escapeHTML(normalizeLineText(line)) || '&nbsp;';
          return '<span class="code-line"><span class="line-number">' + String(lineIndex + 1) + '</span><span class="line-content">' + safeText + '</span></span>';
        }).join('');
      }

      function renderEvidenceItems(evidenceItems) {
        if (!Array.isArray(evidenceItems) || evidenceItems.length === 0) return '';
        const chips = evidenceItems.flatMap(function(item) {
          if (Array.isArray(item.ids)) {
            return item.ids.map(function(id) {
              return '<span class="evidence-pill">' + escapeHTML(item.label || 'ID') + ': ' + escapeHTML(String(id)) + '</span>';
            });
          }
          if (item.id !== undefined && item.id !== null) {
            return ['<span class="evidence-pill">' + escapeHTML(item.label || 'ID') + ': ' + escapeHTML(String(item.id)) + '</span>'];
          }
          if (item.value !== undefined && item.value !== null) {
            const unit = item.unit ? String(item.unit) : '';
            return ['<span class="evidence-pill">' + escapeHTML(item.label || '値') + ': ' + escapeHTML(String(item.value)) + escapeHTML(unit) + '</span>'];
          }
          return [];
        }).join('');
        return chips ? '<div class="evidence-row">' + chips + '</div>' : '';
      }

      function renderReasonCard(reason) {
        const details = Array.isArray(reason.details) ? reason.details : [];
        const detailHTML = details.length > 0
          ? '<ul class="reason-detail-list">' + details.map(function(detail) {
            const evidenceHTML = renderEvidenceItems(detail.evidence);
            return '<li><span>' + escapeHTML(detail.text || '') + '</span>' + evidenceHTML + '</li>';
          }).join('') + '</ul>'
          : '';
        const reasonEvidenceHTML = renderEvidenceItems(reason.evidence);
        return '<article class="reason-card" data-category="' + escapeHTML(reason.category || '') + '">' +
          '<div class="reason-title-row"><h3>' + escapeHTML(reason.title || '-') + ' : ' + escapeHTML(String(reason.score || '-')) + '</h3><span class="reason-chip">' + escapeHTML(reason.categoryLabel || '') + '</span></div>' +
          '<p class="reason-body">' + escapeHTML(reason.body || '') + '</p>' +
          detailHTML +
          reasonEvidenceHTML +
        '</article>';
      }

      function updateReasonList() {
        if (!reasonList) return;
        const filteredReasons = reasonData.reasons.filter(function(reason) {
          return currentReasonFilter === 'all' || reason.category === currentReasonFilter;
        });
        reasonList.innerHTML = filteredReasons.map(renderReasonCard).join('');
      }

      function createReasonFilters() {
        if (!reasonFilterGroup) return;
        reasonFilterGroup.innerHTML = '';
        reasonData.reasonFilters.forEach(function(filter, filterIndex) {
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'reason-filter' + (filterIndex === 0 ? ' is-active' : '');
          button.textContent = filter.label;
          button.setAttribute('data-reason-filter', filter.key);
          button.addEventListener('click', function() {
            currentReasonFilter = filter.key;
            reasonFilterGroup.querySelectorAll('.reason-filter').forEach(function(element) {
              element.classList.toggle('is-active', element === button);
            });
            updateReasonList();
          });
          reasonFilterGroup.appendChild(button);
        });
        if (reasonData.reasonFilters.length > 0) {
          currentReasonFilter = reasonData.reasonFilters[0].key;
        }
      }

      function switchSidebarPanel(targetPanel) {
        const showTimeline = targetPanel === 'timeline';
        if (timelinePanel) timelinePanel.classList.toggle('is-active', showTimeline);
        if (reasonsPanel) reasonsPanel.classList.toggle('is-active', !showTimeline);
        sidebarSwitchButtons.forEach(function(button) {
          button.classList.toggle('is-active', button.getAttribute('data-sidebar-panel') === targetPanel);
        });
      }

      function updateButtons() {
        if (prevCodeButton) prevCodeButton.disabled = index === 0;
        if (nextCodeButton) nextCodeButton.disabled = index === logs.length - 1;
      }

      function updateTimeline() {
        const items = logTimeline ? logTimeline.querySelectorAll('.timeline-item') : [];
        items.forEach(function(item, itemIndex) {
          item.classList.toggle('is-active', itemIndex === index);
        });
      }

      function renderCode(currentIndex) {
        const currentLog = logs[currentIndex];
        if (!currentLog || !codeBlock || !timestamp || !boundaryMessage || !stepIndicator) return;

        codeBlock.innerHTML = renderCodeLines(splitLines(currentLog.content));
        if (sourceCodeId) sourceCodeId.textContent = 'ソースコードID: ' + String(currentLog.id || '-');
        if (executionId) executionId.textContent = '実行ID: ' + String(currentLog.execution_id || '-');

        timestamp.textContent = '保存日時: ' + currentLog.created_at;
        stepIndicator.textContent = String(currentIndex + 1) + ' / ' + String(logs.length);

        if (currentIndex === 0) {
          boundaryMessage.textContent = '最初の変更です。';
        } else if (currentIndex === logs.length - 1) {
          boundaryMessage.textContent = '最後の変更です。';
        } else {
          boundaryMessage.textContent = '';
        }

        updateButtons();
        updateTimeline();
      }

      function createTimeline() {
        if (!logTimeline) return;
        logTimeline.innerHTML = '';
        logs.forEach(function(log, logIndex) {
          const item = document.createElement('button');
          item.type = 'button';
          item.className = 'timeline-item';
          item.innerHTML =
            '<div class="timeline-time">' + log.created_at + '</div>' +
            '<div class="timeline-label">' + getLogLabel(log) + '</div>' +
            '<div class="timeline-preview">' + escapeHTML(getLogPreview(log)) + '</div>';
          item.addEventListener('click', function() {
            index = logIndex;
            renderCode(index);
          });
          logTimeline.appendChild(item);
        });
      }

      if (prevCodeButton) {
        prevCodeButton.addEventListener('click', function() {
          if (index > 0) {
            index -= 1;
            renderCode(index);
          }
        });
      }

      if (nextCodeButton) {
        nextCodeButton.addEventListener('click', function() {
          if (index < logs.length - 1) {
            index += 1;
            renderCode(index);
          }
        });
      }

      sidebarSwitchButtons.forEach(function(button) {
        button.addEventListener('click', function() {
          const targetPanel = button.getAttribute('data-sidebar-panel') || 'timeline';
          switchSidebarPanel(targetPanel);
        });
      });

      if (backButton) {
        backButton.addEventListener('click', function() {
          renderEvaluationView();
        });
      }

      createTimeline();
      createReasonFilters();
      updateReasonList();
      switchSidebarPanel('timeline');
      renderCode(index);
    };

    const renderEvaluationView = function() {
      container.innerHTML = `
      <link rel="stylesheet" href="${studentCssPath}">
      <div class="container">
        <section class="sample-section evaluation-hero mb-4">
          <div class="hero-copy">
            <span class="hero-kicker">Student Evaluation</span>
            <h1 class="section-title mb-3">評価結果</h1>
            <p class="hero-description mb-0">
              コードログと実行履歴をもとに、2つの観点で自動評価したサンプル表示です。各観点の総合点と、根拠になったログの要約を確認できます。
            </p>
            <div class="hero-action-row mt-4">
              <a class="btn btn-warning btn-lg hero-log-button" href="#" data-action="open-log">ログを見る</a>
            </div>
          </div>
          <div class="hero-meta-card">
            <div class="meta-label">対象課題</div>
            <div class="meta-value">${data.task}</div>
            <div class="meta-subtext">ID: ${data.studentId} / ${data.school} ${data.class} / 評価日時: ${data.evaluatedAt}</div>
            <div class="hero-score-row">
              <div class="hero-score-pill thinking">思考力・判断力・表現力 ${data.thinking.toFixed(1)}</div>
              <div class="hero-score-pill attitude">主体的に学習に取り組む態度 ${data.attitude.toFixed(1)}</div>
            </div>
          </div>
        </section>
        <section class="sample-section overview-section mb-4">
          <div class="section-heading-row">
            <div>
              <h2 class="card-title mb-2">総合評価</h2>
              <p class="text-muted mb-0">2つの観点ごとに、ルーブリックをもとにした総合点を表示します。</p>
            </div>
            <span class="score-caption">5段階評価</span>
          </div>
          <div class="row g-4 mt-1">
            <div class="col-lg-6">
              <article class="score-panel thinking-panel">
                <div class="panel-kicker">思考力・判断力・表現力</div>
                <div class="panel-score-row">
                  <div class="panel-score">${data.thinking}</div>
                  <div>
                    <h3 class="panel-title">基礎は理解しているが改善の余地がある</h3>
                    <p class="panel-description mb-0">基本的な処理の流れは実装できていますが、文法エラーの再発や型の扱いなどで不安定な箇所が見られます。</p>
                  </div>
                </div>
                <div class="score-meter" aria-hidden="true">
                  <span class="is-filled"></span><span class="is-filled"></span><span class="is-filled"></span><span></span><span></span>
                </div>
              </article>
            </div>
            <div class="col-lg-6">
              <article class="score-panel attitude-panel">
                <div class="panel-kicker">主体的に学習に取り組む態度</div>
                <div class="panel-score-row">
                  <div class="panel-score">${data.attitude}</div>
                  <div>
                    <h3 class="panel-title">改善を続けながら課題に向き合っている</h3>
                    <p class="panel-description mb-0">保存・実行・修正の流れが複数回確認でき、エラーや出力結果を見ながら改善しています。</p>
                  </div>
                </div>
                <div class="score-meter" aria-hidden="true">
                  <span class="is-filled"></span><span class="is-filled"></span><span class="is-filled"></span><span class="is-filled"></span><span></span>
                </div>
              </article>
            </div>
          </div>
        </section>
        <div class="row g-4 align-items-start">
          <div class="col-12">
            <section class="sample-section breakdown-section mb-4">
              <div class="section-heading-row">
                <div>
                  <h2 class="card-title mb-2">観点別スコア</h2>
                  <p class="text-muted mb-0">細かな評価項目ごとの点数です。</p>
                </div>
              </div>
              <div class="breakdown-grid">
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${data.thinking}</span></div><h3>文法デバッグ能力</h3><p class="mb-0">初歩的な文法エラーが複数回見られる一方、最終的にはコンパイルエラーを解消できています。</p></article>
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${data.thinking}</span></div><h3>論理デバッグ能力</h3><p class="mb-0">基本的な処理の流れは理解できていますが、出力誤りの原因特定が不十分な箇所があります。</p></article>
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${data.thinking}</span></div><h3>アルゴリズムの設計と実装</h3><p class="mb-0">必要な変数定義と反復構造は実装できていますが、終了条件や型の使い分けに改善余地があります。</p></article>
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${data.thinking}</span></div><h3>コードの可読性</h3><p class="mb-0">インデントは概ね揃っていますが、命名やコメント不足により読みやすさの改善が必要です。</p></article>
                <article class="metric-card accent-card"><div class="metric-header"><span class="metric-group">態度</span><span class="metric-score">${data.attitude}</span></div><h3>課題への粘り強さ</h3><p class="mb-0">複数回の保存と実行を通して、途中で止めずに解決まで進めています。</p></article>
                <article class="metric-card accent-card"><div class="metric-header"><span class="metric-group">態度</span><span class="metric-score">${data.attitude}</span></div><h3>課題解決への意欲</h3><p class="mb-0">実行結果を受けてコード改善を続けており、理解しようとする姿勢が見られます。</p></article>
              </div>
            </section>
            <section class="sample-section reasons-section">
              <div class="section-heading-row reasons-heading">
                <div>
                  <h2 class="card-title mb-2">評価の理由</h2>
                  <p class="text-muted mb-0">各項目の点数に至った具体的な根拠です。</p>
                </div>
                <div class="reason-filter-group" role="tablist" aria-label="reason category filter">
                  <button class="reason-filter is-active" type="button" data-filter="all">すべて</button>
                  <button class="reason-filter" type="button" data-filter="thinking">思考・判断・表現</button>
                  <button class="reason-filter" type="button" data-filter="attitude">態度</button>
                </div>
              </div>
              <div class="reason-list">
                <article class="reason-card" data-category="thinking">
                  <div class="reason-title-row"><h3>文法デバッグ能力: ${data.thinking}</h3><span class="reason-chip">思考・判断・表現</span></div>
                  <p class="mb-0">コンパイルエラーは複数回発生していますが、最終的には解消できています。初歩的な文法エラーの再発が見られるため、現時点ではレベル3相当と判断します。</p>
                  <ul class="reason-detail-list"><li><p class="mb-0">コンパイルエラーが複数回発生している。</p></li><li><p class="mb-0">変数の宣言漏れ、printf や scanf の format 指定子の誤りなど、初歩的な文法エラーが見られる。</p></li><li><p class="mb-0">エラーメッセージを参考に修正を試みているが、float 型を scanf で "%d" として読み込む、printf で "%d" で出力するなど、完全に解消できていない箇所がある。</p></li><li><p class="mb-0">ただし最終的にはコンパイルエラーを解消しており、時間をかければ文法エラーを解決できると判断できる。</p></li></ul>
                </article>
                <article class="reason-card" data-category="thinking">
                  <div class="reason-title-row"><h3>論理デバッグ能力: ${data.thinking}</h3><span class="reason-chip">思考・判断・表現</span></div>
                  <p class="mb-0">基本的な処理の流れは理解して修正できていますが、最終出力の誤り原因を特定しきれていないため、レベル3相当と判断します。</p>
                  <ul class="reason-detail-list"><li><p class="mb-0">while 文の条件設定、合計と平均の計算など、基本的な処理の流れは理解できている。</p></li><li><p class="mb-0">初期値の設定（例: x = 0.0）など、細かい部分で間違いが見られる。</p></li><li><p class="mb-0">最終的には、正の値が入力されている間は合計と平均を計算し続ける課題をクリアしている。</p></li><li><p class="mb-0">ただし、最後の出力結果がおかしい（合計に x の値が出力されるなど）箇所があり、その原因を特定しきれていない。</p></li></ul>
                </article>
                <article class="reason-card" data-category="thinking">
                  <div class="reason-title-row"><h3>アルゴリズムの設計と実装: ${data.thinking}</h3><span class="reason-chip">思考・判断・表現</span></div>
                  <p class="mb-0">課題の基本構造は実装できていますが、終了条件の厳密さや型の使い分けに課題が残るため、レベル3相当と判断します。</p>
                  <ul class="reason-detail-list"><li><p class="mb-0">課題内容を理解し、必要な変数（x, s, i）を定義し、while 文で入力を繰り返す基本構造は実装できている。</p></li><li><p class="mb-0">入力値が負になるまで繰り返す条件として x &gt;= 0.0 を使っているが、厳密な終了判定としては改善余地がある（例: scanf の戻り値確認）。</p></li><li><p class="mb-0">float と int の使い分けがあいまいな部分があり、意図した計算結果が得られていない箇所がある。</p></li></ul>
                </article>
                <article class="reason-card" data-category="thinking">
                  <div class="reason-title-row"><h3>コードの可読性: ${data.thinking}</h3><span class="reason-chip">思考・判断・表現</span></div>
                  <p class="mb-0">可読性の基本は満たしていますが、命名とコメントに改善余地があるため、レベル3相当と判断します。</p>
                  <ul class="reason-detail-list"><li><p class="mb-0">インデントは概ね揃っているが、スペースの使い方が一貫していない部分がある。</p></li><li><p class="mb-0">変数名が x, s, i など抽象的で、sum や average のような具体名の方が望ましい。</p></li><li><p class="mb-0">コメントがなく、コードの意図や処理の流れの説明が不足している。</p></li></ul>
                </article>
                <article class="reason-card accent-reason" data-category="attitude">
                  <div class="reason-title-row"><h3>課題への粘り強さ: ${data.attitude}</h3><span class="reason-chip accent-chip">態度</span></div>
                  <p class="mb-0">保存と実行を複数回繰り返しながら、構文エラーと出力誤りの両方を修正しています。途中で作業が止まらず、最終的な正解出力まで到達しているため、粘り強く取り組めていると判断できます。</p>
                  <div class="evidence-row"><span class="evidence-pill accent-evidence">保存回数: 6回</span><span class="evidence-pill accent-evidence">実行回数: 5回</span></div>
                </article>
                <article class="reason-card accent-reason" data-category="attitude">
                  <div class="reason-title-row"><h3>課題解決への意欲: ${data.attitude}</h3><span class="reason-chip accent-chip">態度</span></div>
                  <p class="mb-0">実行結果を確認した直後に条件式と出力文を修正した履歴があり、エラーや結果を無視せず改善につなげています。コードをより正確にしようとする姿勢が見られます。</p>
                </article>
              </div>
            </section>
            <div class="d-flex justify-content-center flex-wrap gap-3 mt-4">
              <a class="btn btn-warning btn-lg px-5" href="#" data-action="open-log">ログを見る</a>
            </div>
          </div>
        </div>
      </div>
    `;

      // student用のフィルタUIやボタンの動作も再現
      const reasonFilters = container.querySelectorAll('[data-filter]');
      const reasonCards = container.querySelectorAll('.reason-card');
      reasonFilters.forEach((filterButton) => {
        filterButton.addEventListener('click', () => {
          const filter = filterButton.getAttribute('data-filter');
          reasonFilters.forEach((item) => item.classList.remove('is-active'));
          filterButton.classList.add('is-active');
          reasonCards.forEach((card) => {
            const category = card.getAttribute('data-category');
            const hidden = filter !== 'all' && category !== filter;
            card.classList.toggle('is-hidden', hidden);
          });
        });
      });

      const openLogButtons = container.querySelectorAll('[data-action="open-log"]');
      openLogButtons.forEach((logButton) => {
        logButton.addEventListener('click', function(event) {
          event.preventDefault();
          renderLogView();
        });
      });
    };

    renderEvaluationView();
  }

  const modalEl = document.getElementById('evaluationDetailModal');
  if (!modalEl) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function setBar(id, width) {
  const bar = document.getElementById(id);
  if (!bar) return;
  const value = Math.max(0, Math.min(100, width));
  bar.style.width = value.toFixed(0) + '%';
}

function updateScoreMeter(meterId, score) {
  const meter = document.getElementById(meterId);
  if (!meter) return;
  const spans = meter.querySelectorAll('span');
  const filledCount = Math.min(Math.max(score, 1), 5);
  spans.forEach(function(span, index) {
    if (index < filledCount) {
      span.classList.add('is-filled');
    } else {
      span.classList.remove('is-filled');
    }
  });
}

function handleReasonFilter(event) {
  const filterValue = event.target.getAttribute('data-filter');
  const reasonCards = document.querySelectorAll('#detailReasonList article');
  
  // Update active button
  document.querySelectorAll('.reason-filter-group button').forEach(function(btn) {
    btn.classList.remove('is-active');
  });
  event.target.classList.add('is-active');
  
  // Filter reason cards
  reasonCards.forEach(function(card) {
    if (filterValue === 'all' || card.getAttribute('data-category') === filterValue) {
      card.style.display = '';
    } else {
      card.style.display = 'none';
    }
  });
}

function buildReasonText(task, thinking, attitude, overall) {
  const thinkingMsg = getThinkingReason(thinking);
  const attitudeMsg = getAttitudeReason(attitude);

  return '課題「' + task + '」の評価結果です。総合評価は ' + overall.toFixed(1) + ' です。' + thinkingMsg + attitudeMsg;
}

function clampScore(score) {
  const rounded = Math.round(Number(score) || 0);
  return Math.max(1, Math.min(5, rounded));
}

function getThinkingSummary(score) {
  if (score >= 4) {
    return {
      title: '安定して解法を組み立てられている',
      description: '文法修正と論理修正を重ねて、正しい出力まで到達しています。命名や構造も大きく崩れていません。'
    };
  }
  if (score >= 3) {
    return {
      title: '基本的な解法は構築できている',
      description: '課題要件は概ね満たしていますが、条件分岐の整理や実装の一貫性に改善余地があります。'
    };
  }
  return {
    title: '解法の再整理が必要',
    description: '構文と論理の両面で再確認が必要です。要件を分解して段階的に実装することで改善が見込めます。'
  };
}

function getAttitudeSummary(score) {
  if (score >= 4) {
    return {
      title: '改善を続けながら課題に向き合っている',
      description: '保存・実行・修正の流れが複数回確認でき、エラーや出力結果を見ながら改善しています。'
    };
  }
  if (score >= 3) {
    return {
      title: '一定の試行を継続できている',
      description: '改善行動は見られますが、検証観点の網羅性や振り返りの深さにばらつきがあります。'
    };
  }
  return {
    title: '取り組みの継続性に課題がある',
    description: '保存・実行・修正のサイクルが少なく、試行錯誤の量を増やすことで改善が期待できます。'
  };
}

function buildDetailEvaluationData(row) {
  const thinking = Number(row.dataset.thinking);
  const attitude = Number(row.dataset.attitude);
  const overall = Number(row.dataset.overall);

  const thinkingScore = clampScore(thinking);
  const attitudeScore = clampScore(attitude);

  const thinkingSummary = getThinkingSummary(thinking);
  const attitudeSummary = getAttitudeSummary(attitude);

  return {
    thinkingSummaryTitle: thinkingSummary.title,
    thinkingSummaryDescription: thinkingSummary.description,
    attitudeSummaryTitle: attitudeSummary.title,
    attitudeSummaryDescription: attitudeSummary.description,
    overallComment: buildReasonText(row.dataset.task, thinking, attitude, overall),

    grammarScore: thinkingScore,
    grammarReason: 'エラーメッセージを読み取り、構文エラーを解消できています。',
    logicScore: thinkingScore,
    logicReason: '条件分岐の誤りを実行結果から見つけ、修正しています。',
    algorithmScore: thinkingScore,
    algorithmReason: '課題要件に沿って判定手順を整理し、実装できています。',
    readabilityScore: thinkingScore,
    readabilityReason: 'インデントや分岐構造が読みやすく、コード全体の可読性が保たれています。',

    persistenceScore: attitudeScore,
    persistenceReason: '保存と実行を繰り返し、途中で止めずに解決まで到達しています。',
    motivationScore: attitudeScore,
    motivationReason: '実行結果を受けて改善を続ける姿勢が確認できます。'
  };
}

function getThinkingReason(thinking) {
  return thinking >= 4
    ? '問題分解と条件設計が明確で、意図の説明可能性が高いです。'
    : thinking >= 3
      ? '基本構成は満たしていますが、分岐条件の整理に改善余地があります。'
      : '実装方針の一貫性が弱く、条件分岐の再設計が必要です。';
}

function getAttitudeReason(attitude) {
  return attitude >= 4
    ? '試行錯誤のログが十分に確認でき、改善サイクルが継続しています。'
    : attitude >= 3
      ? '一定の試行は見られますが、振り返りと修正の頻度にばらつきがあります。'
      : '修正の回数が少なく、検証プロセスが不足しています。';
}

function buildEvaluationTimeline(evaluatedDateTime) {
  const evaluatedTime = (evaluatedDateTime || '').split(' ')[1] || '';
  return [
    evaluatedTime + ' - 評価計算を実行',
    '10:18:30 - 実行結果とコードログを突合',
    '10:14:05 - コード変更履歴から特徴量を抽出'
  ];
}

function refreshEvaluations() {
  const now = new Date();
  const time = now.toTimeString().slice(0, 8);
  const rows = document.querySelectorAll('#evaluationTable tbody tr');

  rows.forEach(function(row) {
    if (Number(row.dataset.overall) >= 4.0) {
      row.dataset.evaluated = now.toISOString().slice(0, 10) + ' ' + time;
      row.cells[8].textContent = time;
    }
  });

  applyFiltersAndSort();
  updateEvaluationSummary();
}

function exportEvaluationCSV() {
  const rows = Array.from(document.querySelectorAll('#evaluationTable tbody tr'))
    .filter(function(row) { return row.style.display !== 'none'; });

  const header = [
    '生徒ID',
    '学校',
    'クラス',
    '課題',
    '難易度',
    '思考力・判断力・表現力',
    '主体的に学習に取り組む態度',
    '総合評価',
    '評価日時',
    '研究同意',
    '思考力総合タイトル',
    '思考力総合説明',
    '主体性総合タイトル',
    '主体性総合説明',
    '総合評価コメント',
    '文法デバッグ能力スコア',
    '文法デバッグ能力理由',
    '論理デバッグ能力スコア',
    '論理デバッグ能力理由',
    'アルゴリズムの設計と実装スコア',
    'アルゴリズムの設計と実装理由',
    'コードの可読性スコア',
    'コードの可読性理由',
    '課題への粘り強さスコア',
    '課題への粘り強さ理由',
    '課題解決への意欲スコア',
    '課題解決への意欲理由',
    'コードログ1',
    'コードログ2',
    'コードログ3'
  ];
  const body = rows.map(function(row) {
    const detail = buildDetailEvaluationData(row);
    const timeline = buildEvaluationTimeline(row.dataset.evaluated);

    return [
      row.dataset.id,
      row.dataset.school,
      row.dataset.class,
      row.dataset.task,
      row.dataset.level,
      row.dataset.thinking,
      row.dataset.attitude,
      row.dataset.overall,
      row.dataset.evaluated,
      row.dataset.consent,
      detail.thinkingSummaryTitle,
      detail.thinkingSummaryDescription,
      detail.attitudeSummaryTitle,
      detail.attitudeSummaryDescription,
      detail.overallComment,
      detail.grammarScore,
      detail.grammarReason,
      detail.logicScore,
      detail.logicReason,
      detail.algorithmScore,
      detail.algorithmReason,
      detail.readabilityScore,
      detail.readabilityReason,
      detail.persistenceScore,
      detail.persistenceReason,
      detail.motivationScore,
      detail.motivationReason,
      timeline[0],
      timeline[1],
      timeline[2]
    ];
  });

  const csv = [header].concat(body)
    .map(function(cols) { return cols.map(escapeCSV).join(','); })
    .join('\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'evaluation_result_' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

function escapeCSV(value) {
  const text = String(value ?? '');
  if (text.includes(',') || text.includes('"') || text.includes('\n')) {
    return '"' + text.replace(/"/g, '""') + '"';
  }
  return text;
}
