document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('teacherReview');
  if (!root) return;
  const form = document.getElementById('evaluationFilters');
  const feedback = window.PPEFeedback.createPageFeedback({ title: '評価確認', alertTarget: '#reviewAlert' });
  const detailFeedback = window.PPEFeedback.createPageFeedback({ title: '評価詳細', alertTarget: '#detailAlert' });
  const modalElement = document.getElementById('evaluationDetailModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const query = new URLSearchParams(location.search);
  let allRows = [], rows = [], selected = null, sort = query.get('sort') || 'evaluated',
    direction = query.get('direction') || 'desc', loading = false, downloading = false, detailSequence = 0, trigger = null, logIndex = 0;
  const get = id => document.getElementById(id);
  const set = (id, value) => { get(id).textContent = value; };
  const node = (tag, text, className) => {
    const result = document.createElement(tag);
    if (text != null) result.textContent = String(text);
    if (className) result.className = className;
    return result;
  };
  const status = value => ({ not_started: '評価待ち', in_progress: '評価中', completed: '評価完了', failed: '評価に失敗', needs_revision: '要修正' }[value] || value);
  const consent = value => ({ agreed: '同意', declined: '不同意', withdrawn: '不同意（撤回）', unconfirmed: '未確認' }[value] || '未確認');
  const difficulty = value => ({ beginner: '初級', intermediate: '中級', advanced: '上級' }[value] || '未設定');
  function values() {
    const params = new URLSearchParams(new FormData(form));
    for (const [key, value] of [...params]) if (!value) params.delete(key);
    params.set('sort', sort); params.set('direction', direction);
    if (query.has('level')) params.set('level', query.get('level'));
    return params;
  }
  function url(extra = {}, params = values()) {
    const address = new URL(root.dataset.endpoint, location.origin);
    params.forEach((value, key) => address.searchParams.set(key, value));
    Object.entries(extra).forEach(([key, value]) => { if (value != null) address.searchParams.set(key, value); });
    return address;
  }
  function errorMessage(response) {
    return response.status === 403 ? '権限がありません。画面を再読み込みしてください。'
      : response.status === 404 ? '対象の履歴を参照できません。'
      : response.status === 413 ? '取得対象は32 MiB以下に絞り込んでください。'
      : response.status === 400 ? '取得対象や絞り込み条件を確認してください。'
      : '読み込み・取得に失敗しました。時間をおいて再試行してください。';
  }
  async function json(address) {
    const response = await fetch(address, { credentials: 'same-origin' });
    if (!response.ok || !response.headers.get('Content-Type')?.includes('application/json')) throw new Error(errorMessage(response));
    return response.json();
  }
  function mean(list, key) {
    const scores = list.map(row => row[key]).filter(value => value != null);
    return scores.length ? (scores.reduce((sum, value) => sum + value, 0) / scores.length).toFixed(1) : '-';
  }
  function condition(expression, score) {
    if (!expression.trim()) return true;
    if (expression.length > 100) throw new Error('条件式は100文字以内にしてください。');
    const parts = expression.split(',').map(part => {
      const match = part.trim().match(/^(<=|>=|=|<|>)\s*(\d+(?:\.\d+)?)$/);
      if (!match || Number(match[2]) < 1 || Number(match[2]) > 5) throw new Error('条件式は =4, >3 のように1〜5の数値で指定してください。');
      return match;
    });
    if (score == null) return false;
    return parts.some(([, op, text]) => {
      const value = Number(text);
      return op === '=' ? score === value : op === '<' ? score < value : op === '>' ? score > value : op === '<=' ? score <= value : score >= value;
    });
  }
  function syncClasses(school, classroom) {
    const chosen = classroom.value;
    classroom.replaceChildren(node('option', 'すべて'));
    classroom.firstChild.value = '';
    const classes = new Map(allRows.filter(row => !school.value || String(row.schoolId) === school.value).map(row => [String(row.classroomId), row.className]));
    classes.forEach((text, value) => { const option = node('option', text); option.value = value; classroom.append(option); });
    classroom.value = classes.has(chosen) ? chosen : '';
  }
  function options() {
    for (const [key, label, summary] of [['schoolId', 'schoolName', 'summarySchool'], ['taskId', 'taskName', 'summaryTask']]) {
      for (const select of [form.elements[key], get(summary)]) {
        const chosen = select.value;
        select.replaceChildren(node('option', 'すべて')); select.firstChild.value = '';
        new Map(allRows.map(row => [String(row[key]), row[label]])).forEach((text, value) => {
          const option = node('option', text); option.value = value; select.append(option);
        });
        select.value = chosen;
      }
    }
    syncClasses(form.elements.schoolId, form.elements.classroomId);
    syncClasses(get('summarySchool'), get('summaryClass'));
  }
  function summary() {
    const list = allRows.filter(row => (!get('summarySchool').value || String(row.schoolId) === get('summarySchool').value)
      && (!get('summaryClass').value || String(row.classroomId) === get('summaryClass').value)
      && (!get('summaryTask').value || String(row.taskId) === get('summaryTask').value));
    const complete = list.filter(row => row.evaluationStatus === 'completed');
    set('summaryTotal', list.length); set('summaryEvaluated', complete.length); set('summaryUnevaluated', list.length - complete.length);
    const pct = list.length ? Math.round(100 * complete.length / list.length) : 0;
    set('completionPct', `${pct}%`); get('completionBar').style.width = `${pct}%`; get('completionBar').setAttribute('aria-valuenow', pct);
    for (const metric of ['thinking', 'attitude']) {
      const key = `${metric}Score`, scores = complete.filter(row => row[key] != null);
      set(`${metric}Average`, mean(complete, key));
      for (let i = 1; i <= 5; i++) {
        const count = scores.filter(row => Math.round(row[key]) === i).length;
        set(`${metric}Count${i}`, count);
        get(`${metric}Bar${i}`).style.width = `${scores.length ? count / scores.length * 100 : 0}%`;
      }
    }
  }
  function sortValue(row) {
    const keys = { student: 'studentLoginId', school: 'schoolName', class: 'className', task: 'taskName', thinking: 'thinkingScore', attitude: 'attitudeScore', level: 'overallScore', evaluated: 'evaluatedAt', submitted: 'submittedAt' };
    if (sort === 'difficulty') return ({ beginner: 1, intermediate: 2, advanced: 3 }[row.difficulty] || 99);
    if (sort === 'consent') return row.consent === 'agreed' ? 3 : row.consent === 'unconfirmed' ? 2 : 1;
    if (sort === 'match') return Math.max(0, row.totalCases ? row.matchedCases / row.totalCases * 100 : 0);
    return row[keys[sort]] ?? (['thinking', 'attitude', 'level'].includes(sort) ? -1 : '');
  }
  function exportButtons() {
    get('bulkResults').disabled = get('bulkLogs').disabled = loading || downloading || !rows.length;
    get('exportCsv').disabled = loading || downloading || !rows.some(row => row.consent === 'agreed');
    get('refreshEvaluations').disabled = loading || downloading;
    get('downloadEvaluation').disabled = get('downloadLogs').disabled = downloading || !selected;
  }
  function render() {
    const params = values(), search = (params.get('search') || '').trim().toLowerCase();
    condition(form.elements.thinking.value, null); condition(form.elements.attitude.value, null);
    rows = allRows.filter(row => ['schoolId', 'classroomId', 'taskId'].every(key => !params.get(key) || String(row[key]) === params.get(key))
      && (!params.get('difficulty') || row.difficulty === params.get('difficulty'))
      && (!params.get('consent') || (params.get('consent') === 'not_agreed' ? ['declined', 'withdrawn'].includes(row.consent) : row.consent === params.get('consent')))
      && (!params.get('level') || row.overallScore != null && Math.round(row.overallScore) === Number(params.get('level')))
      && `${row.studentLoginId} ${row.taskName}`.toLowerCase().includes(search)
      && condition(form.elements.thinking.value, row.thinkingScore) && condition(form.elements.attitude.value, row.attitudeScore))
      .sort((a, b) => {
        const x = sortValue(a), y = sortValue(b), cmp = x < y ? -1 : x > y ? 1 : 0;
        return (direction === 'desc' ? -cmp : cmp) || a.submissionId - b.submissionId || (a.evaluationId || 0) - (b.evaluationId || 0);
      });
    get('evaluationRows').replaceChildren();
    rows.forEach(row => {
      const tr = node('tr'), student = node('td'), link = node('button', null, 'btn btn-link p-0');
      link.type = 'button'; link.append(node('code', row.studentLoginId)); link.addEventListener('click', () => open(row, link)); student.append(link);
      const task = node('td', row.taskName);
      task.append(node('small', `第${row.revision}版 / ${row.evaluationId == null ? '未評価' : `評価${row.evaluationId}${row.latestEvaluation ? '（最新）' : '（過去）'}`} / ${row.historyLabel}`, 'd-block text-muted'));
      const level = node('td'); level.append(node('span', difficulty(row.difficulty), `badge difficulty-${row.difficulty || 'none'}`));
      const date = node('td', row.evaluatedAt || '未完了'); date.append(node('small', status(row.evaluationStatus), 'd-block text-muted'));
      const agreed = node('td'); agreed.append(node('span', consent(row.consent), `consent ${row.consent === 'agreed' ? 'consent-ok' : row.consent === 'unconfirmed' ? 'consent-pending' : 'consent-no'}`));
      const action = node('td'), button = node('button', '表示', 'btn btn-sm btn-outline-primary');
      button.type = 'button'; button.addEventListener('click', () => open(row, button)); action.append(button);
      tr.append(student, node('td', row.schoolName), node('td', row.className), task, level,
        node('td', row.thinkingScore ?? '—'), node('td', row.attitudeScore ?? '—'), date, agreed, action);
      get('evaluationRows').append(tr);
    });
    if (!rows.length) { const tr = node('tr'), td = node('td', '該当する評価履歴はありません。', 'text-center text-muted'); td.colSpan = 10; tr.append(td); get('evaluationRows').append(tr); }
    set('visibleCount', rows.length); set('avgThinkingTop', mean(rows, 'thinkingScore')); set('avgAttitudeTop', mean(rows, 'attitudeScore'));
    get('evaluationTable').querySelectorAll('[data-sort]').forEach(header => {
      header.classList.toggle('sorted-asc', header.dataset.sort === sort && direction === 'asc');
      header.classList.toggle('sorted-desc', header.dataset.sort === sort && direction === 'desc');
      header.setAttribute('aria-sort', header.dataset.sort === sort ? direction === 'asc' ? 'ascending' : 'descending' : 'none');
    });
    exportButtons();
  }
  function apply() {
    try { render(); get('reviewAlert').replaceChildren(); history.replaceState(null, '', url()); }
    catch (error) {
      rows = []; get('evaluationRows').replaceChildren(); set('visibleCount', 0); set('avgThinkingTop', '-'); set('avgAttitudeTop', '-');
      exportButtons(); feedback.inlineAlert(error.message, 'warning');
    }
  }
  async function refresh(initial = false) {
    if (loading) return;
    loading = true; exportButtons();
    try {
      allRows = await json(url({ view: 'list' }, new URLSearchParams()));
      options();
      if (initial) { for (const control of form.elements) if (control.name && query.has(control.name)) control.value = query.get(control.name); syncClasses(form.elements.schoolId, form.elements.classroomId); }
      summary(); apply();
    } catch (error) {
      allRows = []; rows = []; options(); summary(); get('evaluationRows').replaceChildren();
      set('visibleCount', 0); set('avgThinkingTop', '-'); set('avgAttitudeTop', '-');
      feedback.inlineAlert(error.message, 'warning');
    } finally { loading = false; exportButtons(); }
  }
  function renderReasons(reasons, filtersId, listId) {
    const filters = get(filtersId), target = get(listId);
    filters.replaceChildren();
    function show(category) {
      target.replaceChildren();
      reasons.filter(reason => category === '' || reason.dimensionLabel === category).forEach(reason => {
        const attitude = reason.dimensionLabel === '主体的に学習に取り組む態度';
        const card = node('article', null, `reason-card${attitude ? ' accent-reason' : ''}`);
        card.dataset.category = attitude ? 'attitude' : 'thinking';
        card.append(node('h3', reason.title), node('p', reason.body));
        const list = node('ul', null, 'reason-detail-list');
        (reason.details || []).forEach(text => list.append(node('li', typeof text === 'string' ? text : JSON.stringify(text))));
        card.append(list); target.append(card);
      });
      if (!target.childElementCount) target.append(node('p', '保存された評価理由はありません。'));
    }
    const categories = new Map([['', 'すべて']]);
    reasons.forEach(reason => { if (reason.dimensionLabel) categories.set(reason.dimensionLabel,
      reason.dimensionLabel === '主体的に学習に取り組む態度' ? '態度' :
        reason.dimensionLabel === '思考力・判断力・表現力' ? '思考・判断・表現' : reason.dimensionLabel); });
    categories.forEach((label, category) => {
      const button = node('button', label, `reason-filter${category === '' ? ' is-active' : ''}`); button.type = 'button';
      button.setAttribute('aria-pressed', String(category === ''));
      button.addEventListener('click', () => {
        filters.querySelectorAll('button').forEach(item => {
          item.classList.toggle('is-active', item === button); item.setAttribute('aria-pressed', String(item === button));
        });
        show(category);
      }); filters.append(button);
    });
    show('');
  }
  function result(detail) {
    const evaluation = detail.evaluation;
    get('evaluationResults').replaceChildren(); get('evaluationMetrics').replaceChildren(); get('evaluationReasons').replaceChildren(); get('reasonFilters').replaceChildren();
    set('feedbackSummary', evaluation?.feedbackSummary || '評価結果はまだありません。');
    set('processAnalysis', evaluation?.processAnalysis || '');
    get('detailScorePills').replaceChildren();
    (evaluation?.dimensions || []).forEach(dimension => {
      const attitude = dimension.label === '主体的に学習に取り組む態度';
      const card = node('article', null, `score-panel ${attitude ? 'attitude-panel' : 'thinking-panel'}`);
      card.append(node('div', dimension.label, 'panel-kicker'));
      const row = node('div', null, 'panel-score-row'), description = node('div');
      description.append(node('h3', dimension.summaryTitle, 'panel-title'), node('p', dimension.summaryDescription, 'panel-description mb-0'));
      row.append(node('div', dimension.score ?? '—', 'panel-score'), description); card.append(row);
      const meter = node('div', null, 'score-meter'); meter.setAttribute('aria-label', `5段階中 ${dimension.score ?? '未評価'}`);
      for (let i = 1; i <= 5; i++) meter.append(node('span', null, i <= Math.round(dimension.score || 0) ? 'is-filled' : ''));
      card.append(meter); get('evaluationResults').append(card);
      get('detailScorePills').append(node('div', `${dimension.label} ${dimension.score ?? '—'}`, `hero-score-pill ${attitude ? 'attitude' : 'thinking'}`));
    });
    (evaluation?.scores || []).forEach(metric => {
      const card = node('article', null, `metric-card${metric.dimensionLabel === '主体的に学習に取り組む態度' ? ' accent-card' : ''}`), header = node('div', null, 'metric-header');
      header.append(node('span', metric.dimensionLabel, 'metric-group'), node('span', metric.score ?? '—', 'metric-score'));
      card.append(header, node('h3', metric.title), node('p', metric.description), node('p', metric.rationale)); get('evaluationMetrics').append(card);
    });
    renderReasons(evaluation?.reasons || [], 'reasonFilters', 'evaluationReasons');
  }
  async function open(row, source) {
    if (downloading) return;
    const sequence = ++detailSequence;
    selected = null; exportButtons(); trigger = source || trigger;
    get('detailAlert').replaceChildren();
    try {
      const detail = await json(url({ view: 'detail', submissionId: row.submissionId, evaluationId: row.evaluationId }));
      if (sequence !== detailSequence) return;
      selected = detail;
      set('evaluationDetailMeta', `${detail.row.studentLoginId} / ${detail.row.schoolName} / ${detail.row.className} / 第${detail.row.revision}版 / ${detail.row.historyLabel}`);
      set('detailTask', detail.row.taskName);
      set('detailTaskMetadata', `${difficulty(detail.row.difficulty)} / 実施期間 ${detail.publishedAt || '未設定'} ～ ${detail.dueAt || '期限なし'} / 評価日時 ${detail.row.evaluatedAt || '未完了'}`);
      set('evaluationState', `${status(detail.row.evaluationStatus)} / 固定ルーブリック版 ${detail.evaluation?.rubricVersion || '未設定'} / 固定プロンプト版 ${detail.evaluation?.promptVersion || '未設定'}（変更不可）`);
      const versions = get('evaluationVersions'); versions.replaceChildren();
      detail.evaluations.forEach(version => {
        const option = node('option', `評価${version.evaluationId} / ${status(version.status)} / ${version.createdAt} / ${version.kind}`); option.value = version.evaluationId; option.selected = version.evaluationId === detail.row.evaluationId; versions.append(option);
      });
      versions.disabled = !detail.evaluations.length;
      versions.onchange = () => open({ submissionId: detail.row.submissionId, evaluationId: versions.value });
      get('previousEvaluation').replaceChildren();
      if (detail.previousCompletedEvaluation) {
        const old = detail.previousCompletedEvaluation, button = node('button', `前回評価を確認（提出第${old.revision}版 / 評価${old.evaluationId}）`, 'btn btn-outline-primary mb-3');
        button.type = 'button'; button.addEventListener('click', () => open({ submissionId: old.submissionId, evaluationId: old.evaluationId })); get('previousEvaluation').append(button);
      }
      result(detail); logs(detail); showLogs(false);
      modal.show();
      history.replaceState(null, '', url({ submissionId: detail.row.submissionId, evaluationId: detail.row.evaluationId }));
    } catch (error) {
      if (sequence !== detailSequence) return;
      get('teacherEvaluationDetailContent').hidden = true; get('evaluationLogView').hidden = true;
      (modalElement.classList.contains('show') ? detailFeedback : feedback).inlineAlert(error.message, 'warning');
    } finally { exportButtons(); }
  }
  function logs(detail) {
    set('logTask', detail.row.taskName); set('logTaskMetadata', `課題ID ${detail.row.taskId} / ${difficulty(detail.row.difficulty)} / 提出第${detail.row.revision}版`);
    set('logSaveCount', `${detail.logs.length}回`); set('logRunCount', `${detail.logs.filter(log => log.executionStatus).length}件`);
    renderReasons(detail.evaluation?.reasons || [], 'logReasonFilters', 'logReasons');
    showSidebar(false);
    get('codeLogTimes').replaceChildren(); get('logTimeline').replaceChildren();
    detail.logs.forEach((log, i) => {
      const option = node('option', `${log.observedAt} / ${log.eventType}`); option.value = i; get('codeLogTimes').append(option);
      const button = node('button', `${log.observedAt} / ${log.eventType}`, 'btn btn-outline-secondary timeline-item'); button.type = 'button';
      button.addEventListener('click', () => { logIndex = i; renderLog(); }); get('logTimeline').append(button);
    });
    get('codeLogTimes').disabled = !detail.logs.length; logIndex = 0;
    set('evaluationSubmission', detail.code); renderLog();
  }
  function renderLog() {
    const logs = selected?.logs || [], log = logs[logIndex], old = logs[logIndex - 1]?.snapshot?.split('\n') || [];
    get('codeLogTimes').value = logIndex; set('logStep', logs.length ? `${logIndex + 1} / ${logs.length}` : '0 / 0');
    get('previousLog').disabled = logIndex <= 0; get('nextLog').disabled = logIndex >= logs.length - 1;
    set('codeLogState', log ? `${log.observedAt} / ${log.eventType} / ソースコードID ${log.logId} / ${log.executionStatus || '実行なし'}` : 'この提出版のコードログはありません。');
    get('codeLogSnapshot').replaceChildren();
    if (log) (log.snapshot || '').split('\n').forEach((text, i) => {
      const line = node('span', null, `code-line${logIndex > 0 && old[i] !== text ? ' changed' : ''}`); line.append(node('span', i + 1, 'line-number'), node('span', text || ' ')); get('codeLogSnapshot').append(line);
    });
    set('logExecution', log?.executionStatus ? `${log.executionStatus}\n標準入力\n${log.standardInput || ''}\n標準出力\n${log.standardOutput || ''}\n標準エラー\n${log.standardError || ''}` : 'この時点の実行記録はありません。');
    get('logTimeline').querySelectorAll('button').forEach((button, i) => button.classList.toggle('btn-primary', i === logIndex));
  }
  function showLogs(show) { get('teacherEvaluationDetailContent').hidden = show; get('evaluationLogView').hidden = !show; }
  function showSidebar(reasons) {
    get('timelinePanel').hidden = reasons; get('logReasonsPanel').hidden = !reasons;
    get('showLogTimeline').classList.toggle('is-active', !reasons); get('showLogReasons').classList.toggle('is-active', reasons);
  }
  async function download(view, detail = false) {
    if (loading || downloading || detail && !selected) return;
    downloading = true; exportButtons();
    try {
      const extra = { view };
      if (detail) { extra.submissionId = selected.row.submissionId; extra.evaluationId = selected.row.evaluationId; }
      const response = await fetch(url(extra), { credentials: 'same-origin' });
      const type = response.headers.get('Content-Type') || '', expected = view.endsWith('zip') ? 'application/zip' : view === 'csv' ? 'text/csv' : 'application/json';
      if (!response.ok || !type.includes(expected)) throw new Error(errorMessage(response));
      const blob = await response.blob(), address = URL.createObjectURL(blob), link = node('a');
      const disposition = response.headers.get('Content-Disposition') || '';
      link.download = disposition.includes("filename*=UTF-8''") ? decodeURIComponent(disposition.split("filename*=UTF-8''")[1]) : (disposition.match(/filename="([^"]+)"/)?.[1] || 'evaluation.json');
      link.href = address; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(address), 1000);
      (detail ? detailFeedback : feedback).toast({ message: 'ダウンロードを開始しました。', variant: 'success' });
    } catch (error) { (detail ? detailFeedback : feedback).inlineAlert(error.message, 'warning'); }
    finally { downloading = false; exportButtons(); }
  }
  form.addEventListener('submit', event => { event.preventDefault(); apply(); });
  form.addEventListener('input', apply);
  form.addEventListener('change', event => { if (event.target.name === 'schoolId') syncClasses(form.elements.schoolId, form.elements.classroomId); apply(); });
  get('summarySchool').addEventListener('change', () => { syncClasses(get('summarySchool'), get('summaryClass')); summary(); });
  ['summaryClass', 'summaryTask'].forEach(id => get(id).addEventListener('change', summary));
  get('evaluationTable').querySelectorAll('[data-sort]').forEach(header => {
    const change = () => { direction = header.dataset.sort === sort && direction === 'asc' ? 'desc' : 'asc'; sort = header.dataset.sort; apply(); };
    header.addEventListener('click', change); header.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); change(); } });
  });
  get('refreshEvaluations').addEventListener('click', () => refresh());
  get('bulkResults').addEventListener('click', () => download('zip'));
  get('bulkLogs').addEventListener('click', () => download('logs-zip'));
  get('exportCsv').addEventListener('click', () => download('csv'));
  get('downloadEvaluation').addEventListener('click', () => download('file', true));
  get('downloadLogs').addEventListener('click', () => download('logs', true));
  ['openLogs', 'openLogsBottom'].forEach(id => get(id).addEventListener('click', () => showLogs(true)));
  get('backToEvaluation').addEventListener('click', () => showLogs(false));
  get('showLogTimeline').addEventListener('click', () => showSidebar(false)); get('showLogReasons').addEventListener('click', () => showSidebar(true));
  get('codeLogTimes').addEventListener('change', () => { logIndex = Number(get('codeLogTimes').value); renderLog(); });
  get('previousLog').addEventListener('click', () => { if (logIndex > 0) { logIndex--; renderLog(); } });
  get('nextLog').addEventListener('click', () => { if (logIndex < selected.logs.length - 1) { logIndex++; renderLog(); } });
  modalElement.addEventListener('hidden.bs.modal', () => { detailSequence++; selected = null; exportButtons(); history.replaceState(null, '', url()); if (trigger?.isConnected) trigger.focus(); });
  modalElement.addEventListener('hide.bs.modal', () => { if (modalElement.contains(document.activeElement)) document.activeElement.blur(); });
  (async () => { await refresh(true); if (root.dataset.selectedSubmission) await open({ submissionId: root.dataset.selectedSubmission, evaluationId: root.dataset.selectedEvaluation || null }); })();
});
