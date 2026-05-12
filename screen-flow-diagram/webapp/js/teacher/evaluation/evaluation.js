// Teacher evaluation page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeEvaluationPage();
});

function initializeEvaluationPage() {
  ['filterSchool', 'filterClass', 'filterLevel', 'filterConsent', 'sortBy'].forEach(function(id) {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', applyFiltersAndSort);
    }
  });

  const searchInput = document.getElementById('searchInput');
  if (searchInput) {
    searchInput.addEventListener('input', applyFiltersAndSort);
  }

  ['filterThinkingExpr', 'filterAttitudeExpr'].forEach(function(id) {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('input', applyFiltersAndSort);
    }
  });

  applyStudentIdFromQuery();
  applyFiltersAndSort();
}

function applyStudentIdFromQuery() {
  const params = new URLSearchParams(window.location.search);
  const studentId = params.get('studentId');
  if (!studentId) return;

  const searchInput = document.getElementById('searchInput');
  if (searchInput) {
    searchInput.value = studentId;
  }
}

function applyFiltersAndSort() {
  const schoolFilter = document.getElementById('filterSchool')?.value || 'すべて';
  const classFilter = document.getElementById('filterClass')?.value || 'すべて';
  const levelFilter = document.getElementById('filterLevel')?.value || 'すべて';
  const consentFilter = document.getElementById('filterConsent')?.value || 'すべて';
  const thinkingExpr = (document.getElementById('filterThinkingExpr')?.value || '').trim();
  const attitudeExpr = (document.getElementById('filterAttitudeExpr')?.value || '').trim();
  const sortBy = document.getElementById('sortBy')?.value || 'id';
  const query = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();

  const tbody = document.querySelector('#evaluationTable tbody');
  if (!tbody) return;

  const rows = Array.from(tbody.querySelectorAll('tr'));

  rows.forEach(function(row) {
    const thinking = Number(row.dataset.thinking);
    const attitude = Number(row.dataset.attitude);
    const matchSchool = schoolFilter === 'すべて' || row.dataset.school === schoolFilter;
    const matchClass = classFilter === 'すべて' || row.dataset.class === classFilter;
    const matchLevel = levelFilter === 'すべて' || row.dataset.level === levelFilter;
    const matchConsent = consentFilter === 'すべて' || row.dataset.consent === consentFilter;
    const matchThinking = matchesNumericExpr(thinking, thinkingExpr);
    const matchAttitude = matchesNumericExpr(attitude, attitudeExpr);
    const matchQuery = query.length === 0 ||
      row.dataset.id.toLowerCase().includes(query) ||
      row.dataset.task.toLowerCase().includes(query);

    row.style.display = (matchSchool && matchClass && matchLevel && matchConsent && matchThinking && matchAttitude && matchQuery) ? '' : 'none';
  });

  const visibleRows = rows.filter(function(row) { return row.style.display !== 'none'; });
  sortRows(visibleRows, sortBy);
  visibleRows.forEach(function(row) { tbody.appendChild(row); });

  updateSummary(visibleRows);
}

function matchesNumericExpr(value, expr) {
  if (!expr) return true;

  const match = expr.match(/^\s*(<=|>=|=|<|>)?\s*(-?\d+(?:\.\d+)?)\s*$/);
  if (!match) return true;

  const operator = match[1] || '=';
  const target = Number(match[2]);

  if (operator === '=') return value === target;
  if (operator === '>') return value > target;
  if (operator === '<') return value < target;
  if (operator === '>=') return value >= target;
  if (operator === '<=') return value <= target;
  return true;
}

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
  const totalOverall = visibleRows.reduce(function(acc, row) {
    return acc + Number(row.dataset.overall);
  }, 0);
  const avg = count > 0 ? (totalOverall / count).toFixed(1) : '0.0';
  const highCount = visibleRows.filter(function(row) { return Number(row.dataset.overall) >= 4.0; }).length;

  setText('visibleCount', String(count));
  setText('avgOverall', avg);
  setText('highScoreCount', String(highCount));
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function openEvaluationDetail(button) {
  const row = button.closest('tr');
  if (!row) return;

  setText('detailId', row.dataset.id);
  setText('detailClass', row.dataset.school + ' / ' + row.dataset.class);
  setText('detailTask', row.dataset.task + ' / ' + row.dataset.level);
  setText('detailEvaluated', row.dataset.evaluated);

  const thinking = Number(row.dataset.thinking);
  const attitude = Number(row.dataset.attitude);
  const overall = Number(row.dataset.overall);

  // Set hero information
  setText('heroTaskName', row.dataset.task.split(': ')[1] || row.dataset.task);
  setText('heroMeta', 'TASK-001 / ' + row.dataset.level + ' / 最終評価 ' + row.dataset.evaluated);
  setText('heroThinkingScore', '思考力・判断力・表現力 ' + thinking.toFixed(1));
  setText('heroAttitudeScore', '主体的に学習に取り組む態度 ' + attitude.toFixed(1));

  const detailData = buildDetailEvaluationData(row);
  setText('detailThinkingSummaryScore', String(Math.round(thinking)));
  setText('detailThinkingSummaryTitle', detailData.thinkingSummaryTitle);
  setText('detailThinkingSummaryDescription', detailData.thinkingSummaryDescription);
  setText('detailAttitudeSummaryScore', String(Math.round(attitude)));
  setText('detailAttitudeSummaryTitle', detailData.attitudeSummaryTitle);
  setText('detailAttitudeSummaryDescription', detailData.attitudeSummaryDescription);

  // Update score meters
  updateScoreMeter('detailThinkingMeter', Math.round(thinking));
  updateScoreMeter('detailAttitudeMeter', Math.round(attitude));

  // Build breakdown-grid (観点別スコア)
  const breakdownGrid = document.getElementById('detailBreakdownGrid');
  if (breakdownGrid) {
    breakdownGrid.innerHTML = '';
    const breakdownMetrics = [
      { title: '文法デバッグ能力', group: '思考・判断・表現', score: detailData.grammarScore, description: detailData.grammarReason },
      { title: '論理デバッグ能力', group: '思考・判断・表現', score: detailData.logicScore, description: detailData.logicReason },
      { title: 'アルゴリズムの設計と実装', group: '思考・判断・表現', score: detailData.algorithmScore, description: detailData.algorithmReason },
      { title: 'コードの可読性', group: '思考・判断・表現', score: detailData.readabilityScore, description: detailData.readabilityReason },
      { title: '課題への粘り強さ', group: '態度', score: detailData.persistenceScore, description: detailData.persistenceReason, isAccent: true },
      { title: '課題解決への意欲', group: '態度', score: detailData.motivationScore, description: detailData.motivationReason, isAccent: true }
    ];
    
    breakdownMetrics.forEach(function(metric) {
      const article = document.createElement('article');
      article.className = 'metric-card' + (metric.isAccent ? ' accent-card' : '');
      article.innerHTML = '<div class="metric-header"><span class="metric-group">' + metric.group + '</span><span class="metric-score">' + metric.score + '</span></div><h3>' + metric.title + '</h3><p class="mb-0">' + metric.description + '</p>';
      breakdownGrid.appendChild(article);
    });
  }

  // Build reason-list (評価の理由)
  const reasonList = document.getElementById('detailReasonList');
  if (reasonList) {
    reasonList.innerHTML = '';
    const reasons = [
      { title: '文法デバッグ能力: ' + detailData.grammarScore, category: 'thinking', text: detailData.grammarReason },
      { title: '論理デバッグ能力: ' + detailData.logicScore, category: 'thinking', text: detailData.logicReason },
      { title: 'アルゴリズムの設計と実装: ' + detailData.algorithmScore, category: 'thinking', text: detailData.algorithmReason },
      { title: 'コードの可読性: ' + detailData.readabilityScore, category: 'thinking', text: detailData.readabilityReason },
      { title: '課題への粘り強さ: ' + detailData.persistenceScore, category: 'attitude', text: detailData.persistenceReason, isAccent: true },
      { title: '課題解決への意欲: ' + detailData.motivationScore, category: 'attitude', text: detailData.motivationReason, isAccent: true }
    ];
    
    reasons.forEach(function(reason) {
      const article = document.createElement('article');
      article.className = 'reason-card' + (reason.isAccent ? ' accent-reason' : '');
      article.setAttribute('data-category', reason.category);
      const chipClass = reason.isAccent ? ' accent-chip' : '';
      const categoryLabel = reason.category === 'thinking' ? '思考・判断・表現' : '態度';
      article.innerHTML = '<div class="reason-title-row"><h3>' + reason.title + '</h3><span class="reason-chip' + chipClass + '">' + categoryLabel + '</span></div><p class="mb-0">' + reason.text + '</p>';
      reasonList.appendChild(article);
    });
    
    // Set up reason filter functionality
    const reasonFilters = document.querySelectorAll('.reason-filter-group button[data-filter]');
    reasonFilters.forEach(function(btn) {
      btn.removeEventListener('click', handleReasonFilter);
      btn.addEventListener('click', handleReasonFilter);
    });
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
