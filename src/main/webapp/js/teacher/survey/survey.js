document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('teacherSurveyReview');
  if (!root) return;
  const byId = id => document.getElementById(id);
  const form = byId('surveyFilters');
  const feedback = window.PPEFeedback.createPageFeedback({ title: 'アンケート結果確認', alertTarget: '#surveyAlert' });
  const detailFeedback = window.PPEFeedback.createPageFeedback({ title: 'アンケート結果詳細', alertTarget: '#surveyDetailAlert' });
  const modalElement = byId('surveyDetailModal'), modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const metrics = ['q1ThinkingValidity', 'q1ThinkingScore', 'q2AttitudeValidity', 'q2AttitudeScore', 'q3ProcessResistanceScore', 'q4UsabilityScore'];
  const sortMetrics = ['thinkingValidity', 'thinkingScore', 'attitudeValidity', 'attitudeScore', 'resistanceScore', 'usabilityScore'];
  const difficultyLabel = value => ({ beginner: '初級', intermediate: '中級', advanced: '上級', none: '未設定' }[value]);
  const date = value => value ? value.replace('T', ' ').replace(/-/g, '/') : '-';
  const score = (row, code) => row.answers.find(answer => answer.code === code)?.score ?? null;
  const text = (id, value) => { byId(id).textContent = value; };
  function node(tag, value, className) {
    const element = document.createElement(tag);
    if (value != null) element.textContent = String(value);
    if (className) element.className = className;
    return element;
  }
  let allRows = [], rows = [], sort = 'submittedAt', direction = 'desc', detailSequence = 0, abort = null;
  let busy = false, downloadBusy = false, invalid = false, returnId = null, closing = false;
  function params() {
    const result = new URLSearchParams(new FormData(form));
    result.set('sort', sort); result.set('direction', direction);
    return result;
  }
  function url(view, extra = {}, filters = params()) {
    const address = new URL(root.dataset.endpoint, location.origin);
    address.search = filters.toString(); address.searchParams.set('view', view);
    for (const [key, value] of Object.entries(extra)) address.searchParams.set(key, value);
    return address;
  }
  async function response(address, options = {}) {
    const result = await fetch(address, { credentials: 'same-origin', cache: 'no-store', ...options });
    if (!result.ok || result.redirected) {
      let message = '取得に失敗しました。ログイン状態・権限を確認して再試行してください。';
      if (result.headers.get('Content-Type')?.includes('application/json')) message = (await result.json()).message || message;
      throw new Error(message);
    }
    return result;
  }
  async function json(address, options) {
    const result = await response(address, options);
    if (!result.headers.get('Content-Type')?.includes('application/json')) throw new Error('ログインし直してください。');
    return result.json();
  }
  function populate(select, entries) {
    const previous = select.value;
    select.replaceChildren(new Option('すべて', ''));
    for (const [id, label] of entries) select.add(new Option(label, id));
    if ([...select.options].some(option => option.value === previous)) select.value = previous;
  }
  function scopes() {
    const school = form.elements.schoolId.value;
    populate(form.elements.classroomId, new Map(allRows.filter(r => !school || String(r.schoolId) === school).map(r => [String(r.classroomId), r.className])));
    const classroom = form.elements.classroomId.value;
    populate(form.elements.taskId, new Map(allRows.filter(r => (!school || String(r.schoolId) === school) && (!classroom || String(r.classroomId) === classroom)).map(r => [String(r.taskId), r.taskTitle])));
    for (const [summary, source] of [['summarySchool', 'schoolId'], ['summaryClass', 'classroomId']]) {
      populate(byId(summary), [...form.elements[source].options].filter(o => o.value).map(o => [o.value, o.text]));
      byId(summary).value = form.elements[source].value;
    }
    populate(byId('summaryTask'), [...form.elements.taskId.options].filter(o => o.value).map(o => [o.value, o.text]));
  }
  function conditions() {
    invalid = false;
    const parsed = new Map();
    for (const code of metrics) {
      const field = form.elements[code], value = field.value.trim(), parts = [];
      let valid = true;
      if (value) for (const part of value.split(',')) {
        const match = part.trim().match(/^(<=|>=|=|<|>)\s*(\d+(?:\.\d+)?)$/);
        if (!match || Number(match[2]) < 1 || Number(match[2]) > 5) { valid = false; break; }
        parts.push([match[1], Number(match[2])]);
      }
      field.classList.toggle('is-invalid', !valid);
      field.setAttribute('aria-invalid', String(!valid));
      field.setCustomValidity(valid ? '' : '1～5の値を =, <, >, <=, >= とカンマ区切りで指定してください。');
      invalid ||= !valid; parsed.set(code, parts);
    }
    byId('csvSurvey').disabled = byId('textSurvey').disabled = busy || downloadBusy || invalid;
    if (invalid) throw new Error('評価条件が不正です。1～5の値を =, <, >, <=, >= とカンマ区切りで指定してください。');
    return parsed;
  }
  function matches(parts, value) {
    return !parts.length || (value != null && parts.some(([operator, target]) => ({
      '=': value === target, '<': value < target, '>': value > target, '<=': value <= target, '>=': value >= target
    }[operator])));
  }
  function sortValue(row) {
    const index = sortMetrics.indexOf(sort);
    if (index >= 0) return score(row, metrics[index]);
    if (sort === 'difficulty') return ({ beginner: 1, intermediate: 2, advanced: 3, none: 4 }[row.difficulty]);
    if (sort === 'completionStatus') return row.completionStatus === 'submitted' ? 2 : 1;
    return row[sort];
  }
  function render(clearResults = false) {
    try {
      const parsed = conditions(), filters = Object.fromEntries(params()), query = filters.search.trim().toLowerCase();
      rows = allRows.filter(r => (!filters.schoolId || String(r.schoolId) === filters.schoolId)
        && (!filters.classroomId || String(r.classroomId) === filters.classroomId) && (!filters.taskId || String(r.taskId) === filters.taskId)
        && (!filters.difficulty || r.difficulty === filters.difficulty) && (!filters.completion || r.completionStatus === filters.completion)
        && (!filters.consent || r.consentStatus === filters.consent)
        && (r.studentId.toLowerCase().includes(query) || r.taskTitle.toLowerCase().includes(query))
        && metrics.every(code => matches(parsed.get(code), score(r, code))));
      rows.sort((a, b) => {
        const x = sortValue(a), y = sortValue(b);
        const comparison = x == null ? y == null ? 0 : -1 : y == null ? 1 : x < y ? -1 : x > y ? 1 : 0;
        return comparison * (direction === 'asc' ? 1 : -1) || a.responseId - b.responseId;
      });
      feedback.clearInlineAlert();
    } catch (error) {
      feedback.inlineAlert(error.message, 'warning');
      if (!clearResults) return;
      rows = [];
    }
    const body = byId('surveyTableBody'); body.replaceChildren();
    for (const row of rows) {
      const tr = node('tr'), student = node('td'), idButton = node('button', null, 'btn btn-link p-0');
      idButton.type = 'button'; idButton.append(node('code', row.studentId)); idButton.addEventListener('click', () => openDetail(row));
      student.append(idButton); tr.append(student);
      for (const value of [row.school, row.className, row.taskTitle]) tr.append(node('td', value));
      const difficulty = node('td'); difficulty.append(node('span', difficultyLabel(row.difficulty), `badge difficulty-${row.difficulty}`)); tr.append(difficulty);
      for (const metric of metrics) tr.append(node('td', score(row, metric) ?? '-', 'text-center'));
      tr.append(node('td', date(row.submittedAt)), node('td', row.completionStatus === 'submitted' ? '完了' : '下書き'));
      const consent = node('td'); consent.append(node('span', '同意', 'consent')); tr.append(consent);
      const detail = node('td'), button = node('button', '表示', 'btn btn-sm btn-outline-primary');
      button.type = 'button'; button.dataset.responseId = row.responseId; button.addEventListener('click', () => openDetail(row));
      detail.append(button); tr.append(detail); body.append(tr);
    }
    if (!rows.length) { const tr = node('tr'), cell = node('td', '表示対象のアンケート回答がありません。'); cell.colSpan = 15; tr.append(cell); body.append(tr); }
    const complete = rows.filter(r => r.completionStatus === 'submitted').length;
    const summaryRows = rows.filter(r => !byId('summaryTask').value || String(r.taskId) === byId('summaryTask').value);
    const summaryComplete = summaryRows.filter(r => r.completionStatus === 'submitted').length;
    for (const [id, value] of Object.entries({ totalResponses: rows.length, completedResponses: complete, consentedResponses: rows.length, summaryTotal: summaryRows.length, summaryPending: summaryRows.length - summaryComplete, summaryComplete })) text(id, value);
    const pct = summaryRows.length ? Math.round(summaryComplete / summaryRows.length * 100) : 0;
    text('summaryCompletionPct', `${pct}%`); byId('summaryCompletionBar').style.width = `${pct}%`; byId('summaryCompletionBar').setAttribute('aria-valuenow', String(pct));
    for (const [code, id] of [[metrics[0], 'ThinkingValidity'], [metrics[2], 'AttitudeValidity'], [metrics[4], 'Resistance'], [metrics[5], 'Usability']]) {
      const values = summaryRows.map(r => score(r, code)).filter(value => value != null), average = values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
      text(`summary${id}Avg`, average == null ? '-' : average.toFixed(1)); byId(`summary${id}Bar`).style.width = `${average == null ? 0 : average / 5 * 100}%`;
    }
    for (const header of document.querySelectorAll('[data-sort-key]')) {
      header.classList.toggle('sorted-asc', header.dataset.sortKey === sort && direction === 'asc');
      header.classList.toggle('sorted-desc', header.dataset.sortKey === sort && direction === 'desc');
      header.setAttribute('aria-sort', header.dataset.sortKey === sort ? direction === 'asc' ? 'ascending' : 'descending' : 'none');
    }
  }
  async function refresh() {
    if (busy) return;
    busy = true; byId('refreshSurvey').disabled = true; byId('csvSurvey').disabled = byId('textSurvey').disabled = true;
    try {
      allRows = await json(url('list', {}, new URLSearchParams()));
      populate(form.elements.schoolId, new Map(allRows.map(r => [String(r.schoolId), r.school])));
      scopes(); render();
    } catch (error) {
      allRows = []; scopes(); render(true); abort?.abort(); detailSequence++;
      byId('modalResponseContent').replaceChildren(); text('surveyDetailMeta', '');
      feedback.inlineAlert(error.message, 'warning');
    } finally {
      busy = false; byId('refreshSurvey').disabled = false;
      byId('csvSurvey').disabled = byId('textSurvey').disabled = invalid || downloadBusy;
    }
  }
  function block(label, content) {
    const section = node('div', null, 'response-question-block');
    section.append(node('div', label, 'question-subheading'), content); return section;
  }
  function textBox(value) { const box = node('div', null, 'response-text-box'); box.append(node('p', value || '未回答')); return box; }
  function bar(value, labels = ['1', '2', '3', '4', '5'], system = false) {
    const wrap = node('div', null, `likert-display${system ? ' system-bar' : ''}`), scale = node('div', null, 'likert-scale');
    for (const label of labels) scale.append(node('span', label));
    const indicator = node('div', null, 'likert-indicator'), marker = node('div', value ?? '-', 'likert-value');
    marker.style.left = `${value == null ? 50 : (value - 1) / 4 * 100}%`; indicator.append(marker);
    wrap.append(scale, indicator); return wrap;
  }
  function details(row) {
    text('surveyDetailMeta', `${row.studentId} / ${row.school} ${row.className} / ${row.taskTitle}（${difficultyLabel(row.difficulty)}） / 回答: ${date(row.submittedAt)} / 評価ID: ${row.evaluationId}`);
    const content = byId('modalResponseContent'); content.replaceChildren();
    const validity = ['妥当でない', 'あまり妥当でない', 'どちらともいえない', 'おおむね妥当', '妥当'];
    for (const [number, title, codes, system] of [
      [1, '思考力・判断力・表現力', metrics.slice(0, 2), row.systemThinkingScore],
      [2, '主体的に取り組む態度', metrics.slice(2, 4), row.systemAttitudeScore],
      [3, 'AIによって試行錯誤の過程が評価されることへの抵抗感', [metrics[4]], null],
      [4, 'システムの操作性', [metrics[5]], null]
    ]) {
      const section = node('section', null, 'response-section mb-4');
      section.append(node('span', `設問${number}`, 'question-kicker'), node('h3', title));
      section.append(node('p', number <= 2
        ? `システムが出した評価が妥当かどうかを回答したうえで、${title}について自分ではどのように評価するかを5段階で選び、それぞれの理由を記述した回答です。`
        : '保存済みの回答を表示しています。この画面では変更できません。', 'text-muted mt-2'));
      for (const [index, code] of codes.entries()) {
        const answer = row.answers.find(a => a.code === code);
        const labels = number <= 2 && index === 0 ? validity : number === 3 ? ['とても感じる', 'やや感じる', 'どちらともいえない', 'あまり感じない', '全く感じない']
          : number === 4 ? ['とても使いにくい', 'やや使いにくい', 'どちらともいえない', 'やや使いやすい', 'とても使いやすい'] : ['1', '2', '3', '4', '5'];
        section.append(block(answer?.prompt || (index === 0 && number <= 2 ? 'システムの評価は妥当でしたか？' : '自己評価'), bar(score(row, code), labels)));
        if (index === 1) section.append(block('システム評価', bar(system ?? null, undefined, true)));
        section.append(block(number === 4 ? '操作性に関するコメント' : 'その理由', textBox(answer?.reason)));
      }
      content.append(section);
    }
    for (const answer of row.answers.filter(a => !metrics.includes(a.code))) {
      let values = [answer.value];
      if (answer.type === 'multiple_choice' && answer.value) values = JSON.parse(answer.value);
      const label = values.map(value => answer.options.find(o => o.value === value)?.label ?? value).join(' / ');
      content.append(block(`追加設問: ${answer.prompt}`, textBox(label)), block('理由・補足', textBox(answer.reason)));
    }
  }
  async function openDetail(row) {
    if (closing) return;
    abort?.abort(); abort = new AbortController(); const sequence = ++detailSequence;
    returnId = row.responseId; detailFeedback.clearInlineAlert(); byId('modalResponseContent').replaceChildren(); text('surveyDetailMeta', '読み込み中…'); modal.show();
    try {
      const result = await json(url('detail', { responseId: row.responseId }, new URLSearchParams()), { signal: abort.signal });
      if (sequence === detailSequence) details(result);
    } catch (error) {
      if (error.name !== 'AbortError' && sequence === detailSequence) { text('surveyDetailMeta', ''); detailFeedback.inlineAlert(error.message, 'warning'); }
    }
  }
  async function download(view) {
    if (busy || downloadBusy) return;
    let objectUrl;
    try {
      conditions(); downloadBusy = true; byId('csvSurvey').disabled = byId('textSurvey').disabled = true;
      const result = await response(url(view)), type = result.headers.get('Content-Type') || '';
      if (!type.includes(view === 'csv' ? 'text/csv' : 'text/plain')) throw new Error('出力形式が不正です。ログイン状態を確認してください。');
      objectUrl = URL.createObjectURL(await result.blob());
      const anchor = node('a'); anchor.href = objectUrl; anchor.download = `survey-results.${view === 'csv' ? 'csv' : 'txt'}`;
      document.body.append(anchor); anchor.click(); anchor.remove();
      feedback.toast({ message: 'ダウンロードを開始しました。', variant: 'success' });
    } catch (error) { feedback.inlineAlert(error.message, 'warning'); }
    finally { downloadBusy = false; byId('csvSurvey').disabled = byId('textSurvey').disabled = busy || invalid; if (objectUrl) setTimeout(() => URL.revokeObjectURL(objectUrl), 1000); }
  }
  form.addEventListener('submit', event => event.preventDefault());
  form.addEventListener('input', event => { if (event.target.tagName === 'INPUT') render(); });
  form.addEventListener('change', event => { if (event.target.tagName === 'SELECT') { scopes(); render(); } });
  for (const [summary, source] of [['summarySchool', 'schoolId'], ['summaryClass', 'classroomId']]) {
    byId(summary).addEventListener('change', () => { form.elements[source].value = byId(summary).value; scopes(); render(); });
  }
  byId('summaryTask').addEventListener('change', () => render());
  for (const header of document.querySelectorAll('[data-sort-key]')) {
    header.tabIndex = 0;
    const toggle = () => { direction = sort === header.dataset.sortKey && direction === 'asc' ? 'desc' : 'asc'; sort = header.dataset.sortKey; render(); };
    header.addEventListener('click', toggle); header.addEventListener('keydown', event => { if (['Enter', ' '].includes(event.key)) { event.preventDefault(); toggle(); } });
  }
  modalElement.addEventListener('hide.bs.modal', () => {
    closing = true; abort?.abort(); detailSequence++;
    if (modalElement.contains(document.activeElement) && document.activeElement instanceof HTMLElement) document.activeElement.blur();
  });
  modalElement.addEventListener('hidden.bs.modal', () => { closing = false; byId('modalResponseContent').replaceChildren(); byId('surveyTableBody').querySelector(`[data-response-id="${returnId}"]`)?.focus(); });
  byId('refreshSurvey').addEventListener('click', refresh);
  byId('csvSurvey').addEventListener('click', () => download('csv')); byId('textSurvey').addEventListener('click', () => download('text'));
  refresh();
});
