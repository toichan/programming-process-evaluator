/**
 * Teacher Survey Results List
 * アンケート結果一覧画面の動作制御
 */

// ダミーデータ（survey-format.csv から集約）
const surveyData = [
  {
    recordId: 'REC-20260519-0001',
    responseId: 'SRV-20260519-0001',
    submittedAt: '2026-05-19T09:12:34+09:00',
    studentId: 's001',
    school: '国際中等',
    className: '1年A組',
    taskCode: 'TASK-001',
    taskTitle: 'じゃんけん判定プログラム',
    difficulty: '初級',
    systemEvaluation: { thinkingScore: 4, attitudeScore: 4 },
    q1ThinkingScore: 4,
    q1ThinkingReason: '条件分岐の整理と入力チェックを自分で修正し、提出時点で意図した判定ができたため。',
    q1ThinkingValidity: 4,
    q1ThinkingValidityReason: '実行ログを見るとエラーからの修正過程が反映されており、評価は概ね妥当だと感じた。',
    q2AttitudeScore: 4,
    q2AttitudeReason: '実行結果を見ながら複数回修正し、途中で止めずに最後まで改善できたため。',
    q2AttitudeValidity: 4,
    q2AttitudeValidityReason: '保存回数や修正回数も考慮されており、取り組みの実態に近い評価だと感じた。',
    q3ProcessResistanceScore: 4,
    q3ProcessResistanceReason: '記録自体には抵抗は少ないが、評価への使われ方は少し気になる。',
    q4UsabilityScore: 4,
    q4UsabilityComment: '評価画面とログ画面の導線が分かりやすく、全体として操作しやすかった。',
    completionStatus: '完了',
    consentStatus: '同意'
  },
  {
    recordId: 'REC-20260519-0002',
    responseId: 'SRV-20260519-0002',
    submittedAt: '2026-05-19T09:25:08+09:00',
    studentId: 's014',
    school: '国際中等',
    className: '1年B組',
    taskCode: 'TASK-001',
    taskTitle: 'じゃんけん判定プログラム',
    difficulty: '初級',
    systemEvaluation: { thinkingScore: 4, attitudeScore: 4 },
    q1ThinkingScore: 3,
    q1ThinkingReason: '基本的な実装はできたが、変数名や例外的な入力への対応が不十分だと感じたため。',
    q1ThinkingValidity: 3,
    q1ThinkingValidityReason: '結果はおおむね妥当だが、途中で試した改善案が十分に反映されていないように思った。',
    q2AttitudeScore: 3,
    q2AttitudeReason: '修正は続けたが、エラーの原因特定に時間がかかり、効率は高くなかったため。',
    q2AttitudeValidity: 3,
    q2AttitudeValidityReason: '粘り強さは評価されている一方で、学習意欲の面は少し低く見積もられていると感じた。',
    q3ProcessResistanceScore: 3,
    q3ProcessResistanceReason: '記録されることは理解できるが、常に見られている感覚が少しある。',
    q4UsabilityScore: 3,
    q4UsabilityComment: '機能は理解できたが、設問移動ボタンの位置に最初は少し迷った。',
    completionStatus: '下書き',
    consentStatus: '同意'
  },
  {
    recordId: 'REC-20260519-0003',
    responseId: 'SRV-20260519-0003',
    submittedAt: '2026-05-19T10:03:11+09:00',
    studentId: 's118',
    school: '附属高校',
    className: '4年2組',
    taskCode: 'TASK-004',
    taskTitle: '経路探索',
    difficulty: '上級',
    systemEvaluation: { thinkingScore: 5, attitudeScore: 4 },
    q1ThinkingScore: 5,
    q1ThinkingReason: '複数の探索手順を比較し、実行結果を見ながら最適な実装に改善できたため。',
    q1ThinkingValidity: 5,
    q1ThinkingValidityReason: 'コード修正の根拠と結果が一致しており、評価結果に強く納得している。',
    q2AttitudeScore: 4,
    q2AttitudeReason: '難しい課題でも実行と修正を継続できたため、高めに自己評価した。',
    q2AttitudeValidity: 4,
    q2AttitudeValidityReason: '試行回数や改善の履歴が評価に反映されていて妥当だと思う。',
    q3ProcessResistanceScore: 5,
    q3ProcessResistanceReason: '過程の記録・評価にはほとんど抵抗を感じない。',
    q4UsabilityScore: 5,
    q4UsabilityComment: '画面構成が分かりやすく、操作に迷うことがほぼなかった。',
    completionStatus: '完了',
    consentStatus: '同意'
  },
  {
    recordId: 'REC-20260519-0004',
    responseId: 'SRV-20260519-0004',
    submittedAt: '2026-05-19T10:21:57+09:00',
    studentId: 's203',
    school: '附属高校',
    className: '4年4組',
    taskCode: 'TASK-003',
    taskTitle: '家計簿集計',
    difficulty: '中級',
    systemEvaluation: { thinkingScore: 2, attitudeScore: 2 },
    q1ThinkingScore: 2,
    q1ThinkingReason: '合計計算の分岐でミスが多く、安定した結果にできなかったため。',
    q1ThinkingValidity: 2,
    q1ThinkingValidityReason: '最終結果だけでなく途中の工夫も見てほしいので、現在の評価はやや厳しく感じる。',
    q2AttitudeScore: 2,
    q2AttitudeReason: '途中で詰まって手が止まる時間が長く、改善の継続が十分でなかった。',
    q2AttitudeValidity: 2,
    q2AttitudeValidityReason: '修正回数は記録されているが、原因分析に費やした時間は反映されにくいと感じた。',
    q3ProcessResistanceScore: 2,
    q3ProcessResistanceReason: '記録されることにやや抵抗を感じる。',
    q4UsabilityScore: 2,
    q4UsabilityComment: '画面遷移は分かるが、入力欄の説明が不足していて使いにくさを感じた。',
    completionStatus: '下書き',
    consentStatus: '同意'
  }
];

function showSurveyToast(message, variant) {
  const feedback = window.PPEFeedback;
  if (feedback && typeof feedback.toast === 'function') {
    feedback.toast({
      title: 'アンケート結果確認',
      message,
      variant
    });
  }
}

let filteredData = [...surveyData];
let currentSortBy = 'submittedAtDesc';

function getDifficultyBadgeClass(difficulty) {
  if (difficulty === '初級') return 'difficulty-beginner';
  if (difficulty === '中級') return 'difficulty-intermediate';
  if (difficulty === '上級') return 'difficulty-advanced';
  return 'bg-secondary';
}

window.addEventListener('DOMContentLoaded', () => {
  initializeControls();
  initializeHeaderSorting();
  applyFiltersAndSort();
});

function initializeControls() {
  const changeIds = ['schoolFilter', 'classFilter', 'taskFilter', 'difficultyFilter', 'completionFilter', 'consentFilter'];
  const inputIds = ['thinkingExprFilter', 'thinkingValidityExprFilter', 'attitudeExprFilter', 'attitudeValidityExprFilter', 'resistanceExprFilter', 'usabilityExprFilter', 'searchInput'];

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

function initializeHeaderSorting() {
  const headers = document.querySelectorAll('#surveyTable thead th.sortable');
  headers.forEach((header) => {
    header.addEventListener('click', () => {
      const sortKey = header.dataset.sortKey;
      if (!sortKey) return;

      const currentKey = getSortKey(currentSortBy);
      const currentDirection = getSortDirection(currentSortBy);
      const nextDirection = currentKey === sortKey && currentDirection === 'asc' ? 'Desc' : 'Asc';
      currentSortBy = `${sortKey}${nextDirection}`;

      applyFiltersAndSort();
    });
  });
}

function updateHeaderSortIndicator(sortBy) {
  const activeKey = getSortKey(sortBy);
  const activeDirection = getSortDirection(sortBy);
  const headers = document.querySelectorAll('#surveyTable thead th.sortable');

  headers.forEach((header) => {
    header.classList.remove('sorted-asc', 'sorted-desc');
    header.removeAttribute('aria-sort');

    if (header.dataset.sortKey === activeKey) {
      header.classList.add(activeDirection === 'asc' ? 'sorted-asc' : 'sorted-desc');
      header.setAttribute('aria-sort', activeDirection === 'asc' ? 'ascending' : 'descending');
    }
  });
}

function getSortKey(sortBy) {
  if (sortBy.endsWith('Desc')) return sortBy.slice(0, -4);
  if (sortBy.endsWith('Asc')) return sortBy.slice(0, -3);
  return sortBy;
}

function getSortDirection(sortBy) {
  return sortBy.endsWith('Desc') ? 'desc' : 'asc';
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
    tableBody.innerHTML = '<tr><td colspan="15" class="text-center text-muted py-4">該当するアンケート結果がありません</td></tr>';
    return;
  }

  filteredData.forEach((survey) => {
    const consentClass = survey.consentStatus === '同意'
      ? 'consent consent-ok'
      : survey.consentStatus === '未同意' || survey.consentStatus === '不同意'
        ? 'consent consent-no'
        : 'consent consent-pending';
    const completionBadgeClass = survey.completionStatus === '完了' ? 'bg-success' : 'text-bg-secondary';
    const difficultyBadgeClass = getDifficultyBadgeClass(survey.difficulty);

    const row = document.createElement('tr');
    row.innerHTML = `
      <td><code>${survey.studentId}</code></td>
      <td>${survey.school || '-'}</td>
      <td>${survey.className || '-'}</td>
      <td>${survey.taskTitle}</td>
      <td><span class="badge ${difficultyBadgeClass}">${survey.difficulty}</span></td>
      <td class="text-center">${survey.q1ThinkingValidity ?? '-'}</td>
      <td class="text-center">${survey.q1ThinkingScore ?? '-'}</td>
      <td class="text-center">${survey.q2AttitudeValidity ?? '-'}</td>
      <td class="text-center">${survey.q2AttitudeScore ?? '-'}</td>
      <td class="text-center">${survey.q3ProcessResistanceScore ?? '-'}</td>
      <td class="text-center">${survey.q4UsabilityScore ?? '-'}</td>
      <td>${formatDateTime(survey.submittedAt)}</td>
      <td><span class="badge ${completionBadgeClass}">${survey.completionStatus}</span></td>
      <td><span class="${consentClass}">${survey.consentStatus}</span></td>
      <td>
        <button class="btn btn-sm btn-outline-primary" onclick="viewDetail('${survey.responseId}')">
          表示
        </button>
      </td>
    `;
    tableBody.appendChild(row);
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



function updateSurveySummary(records) {
  const total = records.length;
  const completed = records.filter((survey) => survey.completionStatus === '完了').length;
  const pending = records.filter((survey) => survey.completionStatus === '下書き').length;
  const completionPct = total > 0 ? Math.round((completed / total) * 100) : 0;

  const avg = (field) => {
    const scores = records.map(s => Number(s[field] || 0)).filter(v => v > 0);
    if (scores.length === 0) return 0;
    return scores.reduce((a, b) => a + b, 0) / scores.length;
  };

  const toPercent = (score) => Math.max(0, Math.min(100, (score / 5) * 100));

  const set = (id, val) => {
    const el = document.querySelector(`#${id}`);
    if (el) el.textContent = val;
  };
  const setBar = (id, pct) => {
    const el = document.querySelector(`#${id}`);
    if (el) el.style.width = `${pct}%`;
  };

  set('summaryTotal', String(total));
  set('summaryPending', String(pending));
  set('summaryComplete', String(completed));
  set('summaryCompletionPct', `${completionPct}%`);
  const barEl = document.querySelector('#summaryCompletionBar');
  if (barEl) {
    barEl.style.width = `${completionPct}%`;
    barEl.setAttribute('aria-valuenow', String(completionPct));
  }

  const thinkingValidityAvg = avg('q1ThinkingValidity');
  set('summaryThinkingValidityAvg', thinkingValidityAvg.toFixed(1));
  setBar('summaryThinkingValidityBar', toPercent(thinkingValidityAvg));

  const attitudeValidityAvg = avg('q2AttitudeValidity');
  set('summaryAttitudeValidityAvg', attitudeValidityAvg.toFixed(1));
  setBar('summaryAttitudeValidityBar', toPercent(attitudeValidityAvg));

  const resistanceAvg = avg('q3ProcessResistanceScore');
  set('summaryResistanceAvg', resistanceAvg.toFixed(1));
  setBar('summaryResistanceBar', toPercent(resistanceAvg));

  const usabilityAvg = avg('q4UsabilityScore');
  set('summaryUsabilityAvg', usabilityAvg.toFixed(1));
  setBar('summaryUsabilityBar', toPercent(usabilityAvg));
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
  const taskFilter = document.querySelector('#taskFilter')?.value || '';
  const difficultyFilter = document.querySelector('#difficultyFilter')?.value || '';
  const completionFilter = document.querySelector('#completionFilter')?.value || '';
  const consentFilter = document.querySelector('#consentFilter')?.value || '';
  const thinkingExpr = document.querySelector('#thinkingExprFilter')?.value || '';
  const thinkingValidityExpr = document.querySelector('#thinkingValidityExprFilter')?.value || '';
  const attitudeExpr = document.querySelector('#attitudeExprFilter')?.value || '';
  const attitudeValidityExpr = document.querySelector('#attitudeValidityExprFilter')?.value || '';
  const resistanceExpr = document.querySelector('#resistanceExprFilter')?.value || '';
  const usabilityExpr = document.querySelector('#usabilityExprFilter')?.value || '';
  const query = (document.querySelector('#searchInput')?.value || '').trim().toLowerCase();
  const summaryTask = document.querySelector('#summaryTask')?.value || '';
  const sortBy = currentSortBy;
  const sortKey = getSortKey(sortBy);
  const sortOrder = getSortDirection(sortBy);

  filteredData = surveyData.filter((survey) => {
    const searchable = `${survey.studentId} ${survey.taskTitle}`.toLowerCase();

    return (
      (!schoolFilter || survey.school === schoolFilter) &&
      (!classFilter || survey.className === classFilter) &&
      (!taskFilter || survey.taskTitle === taskFilter) &&
      (!difficultyFilter || survey.difficulty === difficultyFilter) &&
      (!completionFilter || survey.completionStatus === completionFilter) &&
      (!consentFilter || survey.consentStatus === consentFilter) &&
      parseScoreCondition(thinkingExpr, Number(survey.q1ThinkingScore || 0)) &&
      parseScoreCondition(thinkingValidityExpr, Number(survey.q1ThinkingValidity || 0)) &&
      parseScoreCondition(attitudeExpr, Number(survey.q2AttitudeScore || 0)) &&
      parseScoreCondition(attitudeValidityExpr, Number(survey.q2AttitudeValidity || 0)) &&
      parseScoreCondition(resistanceExpr, Number(survey.q3ProcessResistanceScore || 0)) &&
      parseScoreCondition(usabilityExpr, Number(survey.q4UsabilityScore || 0)) &&
      (!query || searchable.includes(query)) &&
      (!summaryTask || survey.taskTitle === summaryTask)
    );
  });

  const fieldMap = {
    studentId: 'studentId',
    school: 'school',
    className: 'className',
    taskTitle: 'taskTitle',
    difficulty: 'difficulty',
    thinkingScore: 'q1ThinkingScore',
    thinkingValidity: 'q1ThinkingValidity',
    attitudeScore: 'q2AttitudeScore',
    attitudeValidity: 'q2AttitudeValidity',
    resistanceScore: 'q3ProcessResistanceScore',
    usabilityScore: 'q4UsabilityScore',
    completionStatus: 'completionStatus',
    consentStatus: 'consentStatus'
  };

  const difficultyOrder = { 初級: 1, 中級: 2, 上級: 3 };
  const completionOrder = { 下書き: 1, 完了: 2 };
  const consentOrder = { 未同意: 1, 不同意: 1, 未確認: 2, 同意: 3 };

  filteredData.sort((a, b) => {
    let aVal;
    let bVal;

    if (sortKey === 'submittedAt') {
      aVal = new Date(a.submittedAt).getTime();
      bVal = new Date(b.submittedAt).getTime();
    } else if (sortKey === 'difficulty') {
      aVal = difficultyOrder[a.difficulty] || 99;
      bVal = difficultyOrder[b.difficulty] || 99;
    } else if (sortKey === 'completionStatus') {
      aVal = completionOrder[a.completionStatus] || 99;
      bVal = completionOrder[b.completionStatus] || 99;
    } else if (sortKey === 'consentStatus') {
      aVal = consentOrder[a.consentStatus] || 99;
      bVal = consentOrder[b.consentStatus] || 99;
    } else if (fieldMap[sortKey]) {
      aVal = a[fieldMap[sortKey]];
      bVal = b[fieldMap[sortKey]];
    } else {
      aVal = a.studentId;
      bVal = b.studentId;
    }

    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return sortOrder === 'asc' ? aVal - bVal : bVal - aVal;
    }

    if (fieldMap[sortKey] && /Score|Validity|Resistance|Usability/.test(sortKey)) {
      const aNum = Number(aVal || 0);
      const bNum = Number(bVal || 0);
      return sortOrder === 'asc' ? aNum - bNum : bNum - aNum;
    }

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return sortOrder === 'asc' ? aVal.localeCompare(bVal, 'ja') : bVal.localeCompare(aVal, 'ja');
    }

    return sortOrder === 'asc'
      ? String(aVal || '').localeCompare(String(bVal || ''), 'ja')
      : String(bVal || '').localeCompare(String(aVal || ''), 'ja');
  });

  updateHeaderSortIndicator(sortBy);
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
  const detailMeta = document.querySelector('#surveyDetailMeta');
  if (detailMeta) {
    detailMeta.textContent = survey.studentId + ' / ' + (survey.school || '-') + ' ' + (survey.className || '-')
      + ' / ' + survey.taskTitle + '（' + survey.difficulty + '） / 回答: ' + formatDateTime(survey.submittedAt);
  }

  const systemThinkingScore = survey.systemEvaluation?.thinkingScore;
  const systemAttitudeScore = survey.systemEvaluation?.attitudeScore;

  const scoreBar = (score, labels = ['1', '2', '3', '4', '5'], extraClass = '') => {
    const max = labels.length;
    const pct = score != null ? ((score - 1) / (max - 1)) * 100 : 0;
    return `
      <div class="likert-display ${extraClass}">
        <div class="likert-scale">${labels.map((label) => `<span>${label}</span>`).join('')}</div>
        <div class="likert-indicator" style="width: 100%;">
          <div class="likert-value" style="left: ${pct}%">${score ?? '-'}</div>
        </div>
      </div>`;
  };

  const validityLabels = ['妥当でない', 'あまり妥当でない', 'どちらともいえない', 'おおむね妥当', '妥当'];

  const textBox = (label, text) =>
    text ? `<div class="response-question-block"><div class="question-subheading">${label}</div><div class="response-text-box"><p>${text}</p></div></div>` : '';

  // 回答内容
  const responseContainer = document.querySelector('#modalResponseContent');
  responseContainer.innerHTML = `
    <div class="response-section mb-4">
      <span class="question-kicker">設問1</span>
      <h3>思考力・判断力・表現力</h3>
      <p class="text-muted mt-2 mb-0">システムが出した評価が妥当かどうかを回答したうえで、思考力・判断力・表現力について自分ではどのように評価するかを 5 段階で選び、それぞれの理由を記述した回答です。</p>
      <div class="response-question-block">
        <div class="question-subheading">システムの評価は妥当でしたか？</div>
        ${scoreBar(survey.q1ThinkingValidity, validityLabels)}
      </div>
      ${textBox('その理由', survey.q1ThinkingValidityReason)}
      <div class="response-question-block mt-3">
        <div class="question-subheading">思考力・判断力・表現力の自己評価</div>
        ${scoreBar(survey.q1ThinkingScore)}
      </div>
      <div class="response-question-block">
        <div class="question-subheading">システム評価</div>
        ${scoreBar(systemThinkingScore, ['1', '2', '3', '4', '5'], 'system-bar')}
      </div>
      ${textBox('その理由', survey.q1ThinkingReason)}
    </div>

    <div class="response-section mb-4">
      <span class="question-kicker">設問2</span>
      <h3>主体的に取り組む態度</h3>
      <p class="text-muted mt-2 mb-0">システムが出した評価が妥当かどうかを回答したうえで、主体的に取り組む態度について自分ではどのように評価するかを 5 段階で選び、それぞれの理由を記述した回答です。</p>
      <div class="response-question-block">
        <div class="question-subheading">システムの評価は妥当でしたか？</div>
        ${scoreBar(survey.q2AttitudeValidity, validityLabels)}
      </div>
      ${textBox('その理由', survey.q2AttitudeValidityReason)}
      <div class="response-question-block mt-3">
        <div class="question-subheading">主体的に取り組む態度の自己評価</div>
        ${scoreBar(survey.q2AttitudeScore)}
      </div>
      <div class="response-question-block">
        <div class="question-subheading">システム評価</div>
        ${scoreBar(systemAttitudeScore, ['1', '2', '3', '4', '5'], 'system-bar')}
      </div>
      ${textBox('その理由', survey.q2AttitudeReason)}
    </div>

    <div class="response-section mb-4">
      <span class="question-kicker">設問3</span>
      <h3>AIによって試行錯誤の過程が評価されることへの抵抗感</h3>
      <p class="text-muted mt-2 mb-0">すでに回答済みの場合は自動入力されています。回答内容に変化があれば修正し、変化がなければそのまま次の設問に進んでください。</p>
      <div class="response-question-block mt-3">
        <div class="question-subheading">あなたは、AIによって試行錯誤の過程が評価されることに抵抗を感じますか？</div>
        <div class="likert-display">
          <div class="likert-scale">
            <span>とても感じる</span><span>やや感じる</span><span>どちらともいえない</span><span>あまり感じない</span><span>全く感じない</span>
          </div>
          <div class="likert-indicator" style="width: 100%;">
            <div class="likert-value" style="left: ${survey.q3ProcessResistanceScore != null ? ((survey.q3ProcessResistanceScore - 1) / 4) * 100 : 0}%">${survey.q3ProcessResistanceScore ?? '-'}</div>
          </div>
        </div>
      </div>
      ${textBox('その理由', survey.q3ProcessResistanceReason)}
    </div>

    <div class="response-section mb-4">
      <span class="question-kicker">設問4</span>
      <h3>システムの操作性</h3>
      <p class="text-muted mt-2 mb-0">すでに回答済みの場合は自動入力されています。回答内容に変化があれば修正し、特に変化がなければそのまま送信してください。</p>
      <div class="response-question-block mt-3">
        <div class="question-subheading">エディター、評価画面、画面遷移などを含めて、システム全体の操作性を評価してください</div>
        <div class="likert-display">
          <div class="likert-scale">
            <span>とても使いにくい</span><span>やや使いにくい</span><span>どちらともいえない</span><span>やや使いやすい</span><span>とても使いやすい</span>
          </div>
          <div class="likert-indicator" style="width: 100%;">
            <div class="likert-value" style="left: ${survey.q4UsabilityScore != null ? ((survey.q4UsabilityScore - 1) / 4) * 100 : 0}%">${survey.q4UsabilityScore ?? '-'}</div>
          </div>
        </div>
      </div>
      ${textBox('操作性に関するコメント', survey.q4UsabilityComment)}
    </div>
  `;

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
    'recordId', 'responseId', 'submittedAt', 'studentId', 'taskCode', 'taskTitle', 'difficulty',
    'q1ThinkingValidity', 'q1ThinkingValidityReason', 'q1ThinkingScore', 'q1ThinkingReason',
    'q2AttitudeValidity', 'q2AttitudeValidityReason', 'q2AttitudeScore', 'q2AttitudeReason',
    'q3ProcessResistanceScore', 'q3ProcessResistanceReason',
    'q4UsabilityScore', 'q4UsabilityComment',
    '回答完了状態', '研究同意状態'
  ];
  const rows = [];

  filteredData.forEach(survey => {
    rows.push([
      survey.recordId || '',
      survey.responseId,
      survey.submittedAt,
      survey.studentId,
      survey.taskCode || '',
      survey.taskTitle,
      survey.difficulty,
      survey.q1ThinkingValidity ?? '',
      survey.q1ThinkingValidityReason || '',
      survey.q1ThinkingScore ?? '',
      survey.q1ThinkingReason || '',
      survey.q2AttitudeValidity ?? '',
      survey.q2AttitudeValidityReason || '',
      survey.q2AttitudeScore ?? '',
      survey.q2AttitudeReason || '',
      survey.q3ProcessResistanceScore ?? '',
      survey.q3ProcessResistanceReason || '',
      survey.q4UsabilityScore ?? '',
      survey.q4UsabilityComment || '',
      survey.completionStatus,
      survey.consentStatus
    ]);
  });

  // CSV 生成
  const csvContent = [
    headers.join(','),
    ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
  ].join('\r\n');

  // ダウンロード
  const blob = new Blob(['\uFEFF', csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `survey-results-${new Date().toISOString().split('T')[0]}.csv`;
  link.click();

  showSurveyToast('CSVをダウンロードしました。', 'success');
}
