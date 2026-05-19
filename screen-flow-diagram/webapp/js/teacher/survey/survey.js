/**
 * Teacher Survey Results List
 * アンケート結果一覧画面の動作制御
 */

// ダミーデータ（survey-format.csv から集約）
const surveyData = [
  {
    responseId: 'SRV-20260519-0001',
    submittedAt: '2026-05-19T09:12:34+09:00',
    studentId: 's001',
    school: '国際中等',
    className: '1年A組',
    systemEvaluation: {
      thinkingScore: 4,
      attitudeScore: 4
    },
    taskCode: 'TASK-001',
    taskTitle: 'じゃんけん判定プログラム',
    difficulty: '初級',
    responses: [
      { recordId: 'REC-20260519-0001-01', questionCode: 'Q1_THINKING', questionLabel: '設問1_思考力・判断力・表現力', score: 4, comment: '条件分岐の整理と入力チェックを自分で修正し、提出時点で意図した判定ができたため。' },
      { recordId: 'REC-20260519-0001-02', questionCode: 'Q1_ATTITUDE', questionLabel: '設問1_主体的に取り組む態度', score: 4, comment: '実行結果を見ながら複数回修正し、途中で止めずに最後まで改善できたため。' },
      { recordId: 'REC-20260519-0001-03', questionCode: 'Q2_PROCESS', questionLabel: '設問2_試行錯誤の過程が評価されることについて', score: 5, comment: '試行錯誤の途中過程が評価されると、失敗も学習として記録されるので納得感がある。' },
      { recordId: 'REC-20260519-0001-04', questionCode: 'Q3_USABILITY', questionLabel: '設問3_システムの操作性', score: 4, comment: '評価画面とログ画面の導線が分かりやすく、全体として操作しやすかった。' }
    ],
    completionStatus: '完了',
    consentStatus: '同意'
  },
  {
    responseId: 'SRV-20260519-0002',
    submittedAt: '2026-05-19T09:25:08+09:00',
    studentId: 's014',
    school: '国際中等',
    className: '1年B組',
    systemEvaluation: {
      thinkingScore: 4,
      attitudeScore: 4
    },
    taskCode: 'TASK-001',
    taskTitle: 'じゃんけん判定プログラム',
    difficulty: '初級',
    responses: [
      { recordId: 'REC-20260519-0002-01', questionCode: 'Q1_THINKING', questionLabel: '設問1_思考力・判断力・表現力', score: 3, comment: '基本的な実装はできたが、変数名や例外的な入力への対応が不十分だと感じたため。' },
      { recordId: 'REC-20260519-0002-02', questionCode: 'Q1_ATTITUDE', questionLabel: '設問1_主体的に取り組む態度', score: 3, comment: '修正は続けたが、エラーの原因特定に時間がかかり、効率は高くなかったため。' },
      { recordId: 'REC-20260519-0002-03', questionCode: 'Q2_PROCESS', questionLabel: '設問2_試行錯誤の過程が評価されることについて', score: 4, comment: '過程の評価があると、結果だけでなく改善の工夫を見てもらえる点がよい。' },
      { recordId: 'REC-20260519-0002-04', questionCode: 'Q3_USABILITY', questionLabel: '設問3_システムの操作性', score: 3, comment: '機能は理解できたが、設問移動ボタンの位置に最初は少し迷った。' }
    ],
    completionStatus: '完了',
    consentStatus: '同意'
  }
];

let filteredData = [...surveyData];

window.addEventListener('DOMContentLoaded', () => {
  initializeControls();
  renderTable();
});

function initializeControls() {
  const changeIds = ['schoolFilter', 'classFilter', 'difficultyFilter', 'completionFilter', 'consentFilter', 'sortBy'];
  const inputIds = ['thinkingExprFilter', 'attitudeExprFilter', 'processExprFilter', 'usabilityExprFilter', 'searchInput'];

  changeIds.forEach((id) => {
    const el = document.querySelector(`#${id}`);
    if (el) {
      el.addEventListener('change', applyFiltersAndSort);
    }
  });

  inputIds.forEach((id) => {
    const el = document.querySelector(`#${id}`);
    if (el) {
      el.addEventListener('input', applyFiltersAndSort);
    }
  });
}

/**
 * テーブルをレンダリング
 */
function renderTable() {
  const tableBody = document.querySelector('#surveyTableBody');
  tableBody.innerHTML = '';
  updateTopStats(filteredData);
  updateSurveySummary(filteredData);

  if (filteredData.length === 0) {
    tableBody.innerHTML = '<tr><td colspan="13" class="text-center text-muted py-4">該当するアンケート結果がありません</td></tr>';
    return;
  }

  filteredData.forEach((survey) => {
    // responses から各スコアを抽出
    const q1ThinkingScore = survey.responses.find(r => r.questionCode === 'Q1_THINKING')?.score || '-';
    const q1AttitudeScore = survey.responses.find(r => r.questionCode === 'Q1_ATTITUDE')?.score || '-';
    const q2ProcessScore = survey.responses.find(r => r.questionCode === 'Q2_PROCESS')?.score || '-';
    const q3UsabilityScore = survey.responses.find(r => r.questionCode === 'Q3_USABILITY')?.score || '-';
    const consentClass = survey.consentStatus === '同意'
      ? 'consent consent-ok'
      : survey.consentStatus === '未同意' || survey.consentStatus === '不同意'
        ? 'consent consent-no'
        : 'consent consent-pending';

    const row = document.createElement('tr');
    row.innerHTML = `
      <td><code>${survey.studentId}</code></td>
      <td>${survey.school || '-'}</td>
      <td>${survey.className || '-'}</td>
      <td>${survey.taskTitle}</td>
      <td><span class="badge bg-info">${survey.difficulty}</span></td>
      <td class="text-center">
        <span class="badge" id="score-thinking-${survey.responseId}">
          ${q1ThinkingScore === '-' ? '-' : q1ThinkingScore}
        </span>
      </td>
      <td class="text-center">
        <span class="badge" id="score-attitude-${survey.responseId}">
          ${q1AttitudeScore === '-' ? '-' : q1AttitudeScore}
        </span>
      </td>
      <td class="text-center">
        <span class="badge" id="score-process-${survey.responseId}">
          ${q2ProcessScore === '-' ? '-' : q2ProcessScore}
        </span>
      </td>
      <td class="text-center">
        <span class="badge" id="score-usability-${survey.responseId}">
          ${q3UsabilityScore === '-' ? '-' : q3UsabilityScore}
        </span>
      </td>
      <td>${formatDateTime(survey.submittedAt)}</td>
      <td><span class="badge bg-success">${survey.completionStatus}</span></td>
      <td><span class="${consentClass}">${survey.consentStatus}</span></td>
      <td>
        <button class="btn btn-sm btn-outline-primary" onclick="viewDetail('${survey.responseId}')">
          表示
        </button>
      </td>
    `;
    tableBody.appendChild(row);

    // スコアバッジにカラーを付与
    updateScoreBadgeColor(`score-thinking-${survey.responseId}`, q1ThinkingScore);
    updateScoreBadgeColor(`score-attitude-${survey.responseId}`, q1AttitudeScore);
    updateScoreBadgeColor(`score-process-${survey.responseId}`, q2ProcessScore);
    updateScoreBadgeColor(`score-usability-${survey.responseId}`, q3UsabilityScore);
  });

  document.querySelector('#totalResponses').textContent = filteredData.length;
}

function updateTopStats(records) {
  const completedCount = records.filter((survey) => survey.completionStatus === '完了').length;
  const consentedCount = records.filter((survey) => survey.consentStatus === '同意').length;

  const completedEl = document.querySelector('#completedResponses');
  if (completedEl) completedEl.textContent = String(completedCount);

  const consentedEl = document.querySelector('#consentedResponses');
  if (consentedEl) consentedEl.textContent = String(consentedCount);
}

function getAverageScore(records, questionCode) {
  const scores = records
    .map((survey) => Number(survey.responses.find((r) => r.questionCode === questionCode)?.score || 0))
    .filter((score) => score > 0);

  if (scores.length === 0) return 0;

  const sum = scores.reduce((acc, score) => acc + score, 0);
  return sum / scores.length;
}

function updateSurveySummary(records) {
  const total = records.length;
  const completed = records.filter((survey) => survey.completionStatus === '完了').length;
  const pending = Math.max(0, total - completed);
  const completionPct = total > 0 ? Math.round((completed / total) * 100) : 0;

  const thinkingAvg = getAverageScore(records, 'Q1_THINKING');
  const attitudeAvg = getAverageScore(records, 'Q1_ATTITUDE');
  const processAvg = getAverageScore(records, 'Q2_PROCESS');
  const usabilityAvg = getAverageScore(records, 'Q3_USABILITY');

  const toPercent = (score) => {
    const percent = (score / 5) * 100;
    return Math.max(0, Math.min(100, percent));
  };

  const summaryTotalEl = document.querySelector('#summaryTotal');
  if (summaryTotalEl) summaryTotalEl.textContent = String(total);

  const summaryPendingEl = document.querySelector('#summaryPending');
  if (summaryPendingEl) summaryPendingEl.textContent = String(pending);

  const summaryCompleteEl = document.querySelector('#summaryComplete');
  if (summaryCompleteEl) summaryCompleteEl.textContent = String(completed);

  const pctEl = document.querySelector('#summaryCompletionPct');
  if (pctEl) pctEl.textContent = `${completionPct}%`;

  const barEl = document.querySelector('#summaryCompletionBar');
  if (barEl) {
    barEl.style.width = `${completionPct}%`;
    barEl.setAttribute('aria-valuenow', String(completionPct));
  }

  const thinkingAvgEl = document.querySelector('#summaryThinkingAvg');
  if (thinkingAvgEl) thinkingAvgEl.textContent = thinkingAvg.toFixed(1);
  const thinkingBarEl = document.querySelector('#summaryThinkingBar');
  if (thinkingBarEl) thinkingBarEl.style.width = `${toPercent(thinkingAvg)}%`;

  const attitudeAvgEl = document.querySelector('#summaryAttitudeAvg');
  if (attitudeAvgEl) attitudeAvgEl.textContent = attitudeAvg.toFixed(1);
  const attitudeBarEl = document.querySelector('#summaryAttitudeBar');
  if (attitudeBarEl) attitudeBarEl.style.width = `${toPercent(attitudeAvg)}%`;

  const processAvgEl = document.querySelector('#summaryProcessAvg');
  if (processAvgEl) processAvgEl.textContent = processAvg.toFixed(1);
  const processBarEl = document.querySelector('#summaryProcessBar');
  if (processBarEl) processBarEl.style.width = `${toPercent(processAvg)}%`;

  const usabilityAvgEl = document.querySelector('#summaryUsabilityAvg');
  if (usabilityAvgEl) usabilityAvgEl.textContent = usabilityAvg.toFixed(1);
  const usabilityBarEl = document.querySelector('#summaryUsabilityBar');
  if (usabilityBarEl) usabilityBarEl.style.width = `${toPercent(usabilityAvg)}%`;
}

function syncSummaryFilters() {
  const summarySchool = document.querySelector('#summarySchool')?.value || '';
  const summaryClass = document.querySelector('#summaryClass')?.value || '';

  const schoolFilterEl = document.querySelector('#schoolFilter');
  if (schoolFilterEl) schoolFilterEl.value = summarySchool;

  const classFilterEl = document.querySelector('#classFilter');
  if (classFilterEl) classFilterEl.value = summaryClass;

  applyFiltersAndSort();
}

/**
 * スコアバッジに色を付与
 */
function updateScoreBadgeColor(elementId, score) {
  const element = document.querySelector(`#${elementId}`);
  if (element && score !== '-') {
    if (score >= 4) {
      element.className = 'badge bg-success';
    } else if (score === 3) {
      element.className = 'badge bg-warning';
    } else {
      element.className = 'badge bg-danger';
    }
  }
}

/**
 * 日時をフォーマット
 */
function formatDateTime(isoString) {
  const date = new Date(isoString);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}`;
}

function parseScoreCondition(expression, value) {
  const expr = String(expression || '').trim();
  if (!expr) return true;

  const parts = expr.split(',').map((item) => item.trim()).filter(Boolean);
  if (parts.length === 0) return true;

  return parts.some((part) => {
    const matched = part.match(/^(<=|>=|=|<|>)\s*(\d+(?:\.\d+)?)$/);
    if (!matched) return false;

    const operator = matched[1];
    const target = Number(matched[2]);

    if (operator === '=') return value === target;
    if (operator === '<') return value < target;
    if (operator === '>') return value > target;
    if (operator === '<=') return value <= target;
    if (operator === '>=') return value >= target;
    return false;
  });
}

/**
 * フィルタを適用
 */
function applyFilters() {
  applyFiltersAndSort();
}

/**
 * ソートを適用
 */
function applySort() {
  applyFiltersAndSort();
}

function applyFiltersAndSort() {
  const schoolFilter = document.querySelector('#schoolFilter')?.value || '';
  const classFilter = document.querySelector('#classFilter')?.value || '';
  const difficultyFilter = document.querySelector('#difficultyFilter')?.value || '';
  const completionFilter = document.querySelector('#completionFilter')?.value || '';
  const consentFilter = document.querySelector('#consentFilter')?.value || '';
  const thinkingExpr = document.querySelector('#thinkingExprFilter')?.value || '';
  const attitudeExpr = document.querySelector('#attitudeExprFilter')?.value || '';
  const processExpr = document.querySelector('#processExprFilter')?.value || '';
  const usabilityExpr = document.querySelector('#usabilityExprFilter')?.value || '';
  const query = (document.querySelector('#searchInput')?.value || '').trim().toLowerCase();
  const summaryTask = document.querySelector('#summaryTask')?.value || '';
  const sortBy = document.querySelector('#sortBy')?.value || 'submittedAt-desc';
  const [sortKey, sortOrder] = sortBy.split('-');

  filteredData = surveyData.filter((survey) => {
    const thinkingScore = Number(survey.responses.find(r => r.questionCode === 'Q1_THINKING')?.score || 0);
    const attitudeScore = Number(survey.responses.find(r => r.questionCode === 'Q1_ATTITUDE')?.score || 0);
    const processScore = Number(survey.responses.find(r => r.questionCode === 'Q2_PROCESS')?.score || 0);
    const usabilityScore = Number(survey.responses.find(r => r.questionCode === 'Q3_USABILITY')?.score || 0);
    const searchable = `${survey.studentId} ${survey.taskTitle}`.toLowerCase();

    const schoolMatch = !schoolFilter || survey.school === schoolFilter;
    const classMatch = !classFilter || survey.className === classFilter;
    const difficultyMatch = !difficultyFilter || survey.difficulty === difficultyFilter;
    const completionMatch = !completionFilter || survey.completionStatus === completionFilter;
    const consentMatch = !consentFilter || survey.consentStatus === consentFilter;
    const thinkingMatch = parseScoreCondition(thinkingExpr, thinkingScore);
    const attitudeMatch = parseScoreCondition(attitudeExpr, attitudeScore);
    const processMatch = parseScoreCondition(processExpr, processScore);
    const usabilityMatch = parseScoreCondition(usabilityExpr, usabilityScore);
    const queryMatch = !query || searchable.includes(query);
    const summaryTaskMatch = !summaryTask || survey.taskTitle === summaryTask;

    return schoolMatch && classMatch && difficultyMatch && completionMatch && consentMatch && thinkingMatch && attitudeMatch && processMatch && usabilityMatch && queryMatch && summaryTaskMatch;
  });

  filteredData.sort((a, b) => {
    let aVal;
    let bVal;

    if (sortKey === 'submittedAt') {
      aVal = new Date(a.submittedAt).getTime();
      bVal = new Date(b.submittedAt).getTime();
    } else if (sortKey === 'studentId') {
      aVal = a.studentId;
      bVal = b.studentId;
    } else if (sortKey === 'thinkingScore') {
      aVal = Number(a.responses.find(r => r.questionCode === 'Q1_THINKING')?.score || 0);
      bVal = Number(b.responses.find(r => r.questionCode === 'Q1_THINKING')?.score || 0);
    } else if (sortKey === 'attitudeScore') {
      aVal = Number(a.responses.find(r => r.questionCode === 'Q1_ATTITUDE')?.score || 0);
      bVal = Number(b.responses.find(r => r.questionCode === 'Q1_ATTITUDE')?.score || 0);
    } else if (sortKey === 'processScore') {
      aVal = Number(a.responses.find(r => r.questionCode === 'Q2_PROCESS')?.score || 0);
      bVal = Number(b.responses.find(r => r.questionCode === 'Q2_PROCESS')?.score || 0);
    } else if (sortKey === 'usabilityScore') {
      aVal = Number(a.responses.find(r => r.questionCode === 'Q3_USABILITY')?.score || 0);
      bVal = Number(b.responses.find(r => r.questionCode === 'Q3_USABILITY')?.score || 0);
    } else {
      aVal = a.studentId;
      bVal = b.studentId;
    }

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return sortOrder === 'asc' ? aVal.localeCompare(bVal, 'ja') : bVal.localeCompare(aVal, 'ja');
    }

    return sortOrder === 'asc' ? aVal - bVal : bVal - aVal;
  });

  renderTable();
}

/**
 * 詳細をモーダルで表示
 */
function viewDetail(responseId) {
  const survey = surveyData.find(s => s.responseId === responseId);
  if (survey) {
    displaySurveyInModal(survey);
  }
}

/**
 * モーダルに詳細情報を表示
 */
function displaySurveyInModal(survey) {
  // ヘッダー情報を設定
  document.querySelector('#modalStudentId').textContent = survey.studentId;
  document.querySelector('#modalTaskTitle').textContent = survey.taskTitle;
  document.querySelector('#modalTaskDifficulty').textContent = survey.difficulty;
  document.querySelector('#modalSubmittedAt').textContent = formatDateTime(survey.submittedAt);

  const q1Thinking = survey.responses.find(r => r.questionCode === 'Q1_THINKING');
  const q1Attitude = survey.responses.find(r => r.questionCode === 'Q1_ATTITUDE');
  const q2Process = survey.responses.find(r => r.questionCode === 'Q2_PROCESS');
  const q3Usability = survey.responses.find(r => r.questionCode === 'Q3_USABILITY');
  const systemThinkingScore = survey.systemEvaluation?.thinkingScore;
  const systemAttitudeScore = survey.systemEvaluation?.attitudeScore;

  // 評価サマリーは表示しない
  const evaluationContainer = document.querySelector('#modalEvaluationSummary');
  evaluationContainer.innerHTML = '';

  // 回答内容を生成（student/surveyの設問構成に準拠）
  const responseContainer = document.querySelector('#modalResponseContent');
  let responseHTML = '';

  if (q1Thinking || q1Attitude) {
    responseHTML += '<div class="response-section"><span class="question-kicker">設問1</span><h3>自己評価</h3>';
    
    if (q1Thinking) {
      const thinkingPercentage = ((q1Thinking.score - 1) / 4) * 100;
      const systemThinkingPercentage = systemThinkingScore != null ? ((systemThinkingScore - 1) / 4) * 100 : 0;
      responseHTML += `
        <div class="response-question-block mt-3">
          <div class="question-subheading">思考力・判断力・表現力</div>
          <div class="small text-muted mb-1">自己評価</div>
          <div class="likert-display mb-2">
            <div class="likert-scale">
              <span>1</span>
              <span>2</span>
              <span>3</span>
              <span>4</span>
              <span>5</span>
            </div>
            <div class="likert-indicator" style="width: 100%;">
              <div class="likert-value" style="left: ${thinkingPercentage}%">${q1Thinking.score}</div>
            </div>
          </div>
          <div class="small text-muted mb-1">システム評価</div>
          <div class="likert-display system-bar">
            <div class="likert-scale">
              <span>1</span>
              <span>2</span>
              <span>3</span>
              <span>4</span>
              <span>5</span>
            </div>
            <div class="likert-indicator" style="width: 100%;">
              <div class="likert-value" style="left: ${systemThinkingPercentage}%">${systemThinkingScore ?? '-'}</div>
            </div>
          </div>
          <div class="response-text-box">
            <p>${q1Thinking.comment}</p>
          </div>
        </div>
      `;
    }
    
    if (q1Attitude) {
      const attitudePercentage = ((q1Attitude.score - 1) / 4) * 100;
      const systemAttitudePercentage = systemAttitudeScore != null ? ((systemAttitudeScore - 1) / 4) * 100 : 0;
      responseHTML += `
        <div class="response-question-block">
          <div class="question-subheading">主体的に取り組む態度</div>
          <div class="small text-muted mb-1">自己評価</div>
          <div class="likert-display mb-2">
            <div class="likert-scale">
              <span>1</span>
              <span>2</span>
              <span>3</span>
              <span>4</span>
              <span>5</span>
            </div>
            <div class="likert-indicator" style="width: 100%;">
              <div class="likert-value" style="left: ${attitudePercentage}%">${q1Attitude.score}</div>
            </div>
          </div>
          <div class="small text-muted mb-1">システム評価</div>
          <div class="likert-display system-bar">
            <div class="likert-scale">
              <span>1</span>
              <span>2</span>
              <span>3</span>
              <span>4</span>
              <span>5</span>
            </div>
            <div class="likert-indicator" style="width: 100%;">
              <div class="likert-value" style="left: ${systemAttitudePercentage}%">${systemAttitudeScore ?? '-'}</div>
            </div>
          </div>
          <div class="response-text-box">
            <p>${q1Attitude.comment}</p>
          </div>
        </div>
      `;
    }
    responseHTML += '</div>';
  }

  // Q2: 試行錯誤プロセス評価
  if (q2Process) {
    const percentage = ((q2Process.score - 1) / 4) * 100;
    responseHTML += `
      <div class="response-section">
        <span class="question-kicker">設問2</span>
        <h3>試行錯誤の過程が評価されることについて</h3>
        <div class="response-question-block mt-3">
          <div class="question-subheading">試行錯誤の過程が評価されることについて</div>
          <div class="likert-display">
            <div class="likert-scale">
              <span>強く否定する</span>
              <span>やや否定する</span>
              <span>中程度</span>
              <span>やや肯定する</span>
              <span>強く肯定する</span>
            </div>
            <div class="likert-indicator" style="width: 100%;">
              <div class="likert-value" style="left: ${percentage}%">${q2Process.score}</div>
            </div>
          </div>
          <div class="response-text-box">
            <p>${q2Process.comment}</p>
          </div>
        </div>
      </div>
    `;
  }

  // Q3: 操作性評価
  if (q3Usability) {
    const usabilityPercentage = ((q3Usability.score - 1) / 4) * 100;
    responseHTML += `
      <div class="response-section">
        <span class="question-kicker">設問3</span>
        <h3>システムの操作性</h3>
        <div class="response-question-block mt-3">
          <div class="question-subheading">システムの操作性</div>
          <div class="likert-display">
            <div class="likert-scale">
              <span>1</span>
              <span>2</span>
              <span>3</span>
              <span>4</span>
              <span>5</span>
            </div>
            <div class="likert-indicator" style="width: 100%;">
              <div class="likert-value" style="left: ${usabilityPercentage}%">${q3Usability.score}</div>
            </div>
          </div>
          <div class="response-text-box">
            <p>${q3Usability.comment}</p>
          </div>
        </div>
      </div>
    `;
  }

  responseContainer.innerHTML = responseHTML;

  // モーダルを表示
  const modal = new bootstrap.Modal(document.querySelector('#surveyDetailModal'));
  modal.show();
}

function refreshSurveys() {
  const now = new Date();
  const date = now.toISOString().slice(0, 10);
  const time = now.toTimeString().slice(0, 8);
  const submittedAt = `${date}T${time}+09:00`;

  surveyData.forEach((survey) => {
    if (survey.completionStatus === '完了') {
      survey.submittedAt = submittedAt;
    }
  });

  applyFiltersAndSort();
}

/**
 * CSVエクスポート
 */
function exportCSV() {
  const headers = [
    '生徒ID',
    '学校',
    'クラス',
    '課題名',
    '難易度',
    '思判表',
    '態度',
    '評価許容',
    '操作性',
    '思判表_記述',
    '態度_記述',
    '評価許容_記述',
    '操作性_記述',
    '回答日時',
    '回答完了状態',
    '研究同意状態'
  ];
  const rows = [];

  filteredData.forEach(survey => {
    const q1Thinking = survey.responses.find(r => r.questionCode === 'Q1_THINKING') || {};
    const q1Attitude = survey.responses.find(r => r.questionCode === 'Q1_ATTITUDE') || {};
    const q2Process = survey.responses.find(r => r.questionCode === 'Q2_PROCESS') || {};
    const q3Usability = survey.responses.find(r => r.questionCode === 'Q3_USABILITY') || {};

    rows.push([
      survey.studentId,
      survey.school || '',
      survey.className || '',
      survey.taskTitle,
      survey.difficulty,
      q1Thinking.score || '',
      q1Attitude.score || '',
      q2Process.score || '',
      q3Usability.score || '',
      q1Thinking.comment || '',
      q1Attitude.comment || '',
      q2Process.comment || '',
      q3Usability.comment || '',
      formatDateTime(survey.submittedAt),
      survey.completionStatus,
      survey.consentStatus
    ]);
  });

  // CSV 生成
  const csvContent = [
    headers.join(','),
    ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
  ].join('\n');

  // ダウンロード
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `survey-results-${new Date().toISOString().split('T')[0]}.csv`;
  link.click();
}
