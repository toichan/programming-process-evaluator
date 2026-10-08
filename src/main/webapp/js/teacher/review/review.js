document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('teacherReview');
  if (!root) return;
  const evaluations = root.dataset.evaluations === 'true';
  const endpoint = root.dataset.endpoint;
  const filters = document.getElementById('reviewFilters');
  const feedback = window.PPEFeedback.createPageFeedback({ title: evaluations ? '評価確認' : '提出課題確認', alertTarget: '#reviewAlert' });
  const previewFeedback = window.PPEFeedback.createPageFeedback({ title: '作業用実行', alertTarget: '#previewAlert' });
  let rows = [], index = -1, sequence = 0, busy = false, selected = null;
  const modalElement = document.getElementById('submissionModal');
  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const query = new URLSearchParams(location.search);
  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  }
  const status = value => ({ not_started: '評価待ち', in_progress: '評価中', completed: '評価完了', failed: '評価に失敗', needs_revision: '要修正' }[value] || value);
  const consent = value => ({ agreed: '同意', declined: '未同意', withdrawn: '未同意（撤回）', unconfirmed: '未確認' }[value] || '未確認');
  const difficulty = value => ({ beginner: '初級', intermediate: '中級', advanced: '上級' }[value] || '未設定');
  function params() {
    const values = new URLSearchParams(new FormData(filters));
    for (const [key, value] of [...values]) if (!value) values.delete(key);
    return values;
  }
  function url(extra, values = params()) {
    const result = new URL(endpoint, location.origin);
    for (const [key, value] of values) result.searchParams.set(key, value);
    for (const [key, value] of Object.entries(extra)) if (value != null) result.searchParams.set(key, value);
    return result;
  }
  async function fetchJson(address, options) {
    const response = await fetch(address, { credentials: 'same-origin', ...options });
    if (!response.ok || !response.headers.get('Content-Type')?.includes('application/json')) {
      throw new Error(response.status === 403 ? '権限がありません。画面を再読み込みしてください。' :
        response.status === 404 ? '対象の履歴を参照できません。' :
        response.status === 413 ? 'コードは64 KiB、入力は8 KiB以下にしてください。' : '読み込み・実行に失敗しました。時間をおいて再試行してください。');
    }
    return response.json();
  }
  function notify(error, preview = false) {
    (preview ? previewFeedback : feedback).inlineAlert(error.message, 'warning');
  }
  function score(value) {
    const node = element('div', null, 'review-score');
    node.append(element('span', value == null ? '—' : `${value} / 5`));
    if (value != null) {
      const meter = element('meter');
      meter.min = 1; meter.max = 5; meter.value = value;
      meter.setAttribute('aria-label', `5段階中 ${value}`);
      node.append(meter);
    }
    return node;
  }
  function populateOptions(allRows) {
    for (const [key, label] of [['schoolId', 'schoolName'], ['classroomId', 'className'], ['taskId', 'taskName']]) {
      const select = filters.elements[key], options = new Map();
      allRows.forEach(row => options.set(String(row[key]), key === 'classroomId' ? `${row.schoolName} / ${row[label]}` : row[label]));
      for (const [value, text] of options) {
        const option = element('option', text); option.value = value; select.append(option);
      }
    }
    for (const control of filters.elements) {
      if (!control.name) continue;
      control.value = query.get(control.name) || (control.name === 'sort' ? (evaluations ? 'evaluated' : 'submitted') : control.name === 'direction' ? 'desc' : '');
    }
  }
  async function loadList() {
    rows = await fetchJson(url({ view: 'list' }));
    const body = document.getElementById('reviewRows');
    body.replaceChildren();
    rows.forEach((row, position) => {
      const tr = element('tr');
      const id = element('td');
      if (evaluations) {
        const link = element('a', row.studentLoginId);
        link.href = url({ submissionId: row.submissionId, evaluationId: row.evaluationId });
        id.append(link);
      } else id.textContent = row.studentLoginId;
      tr.append(id, element('td', `${row.schoolName} / ${row.className}`),
        element('td', `${row.taskName} / ${difficulty(row.difficulty)}`),
        element('td', `第${row.revision}版 / ${row.submittedAt}`));
      if (evaluations) {
        [row.thinkingScore, row.attitudeScore, row.overallScore].forEach(value => {
          const td = element('td'); td.append(score(value)); tr.append(td);
        });
        tr.append(element('td', `${status(row.evaluationStatus)} / ${row.evaluatedAt || '未完了'}${row.evaluationId != null ? ` / 評価${row.evaluationId}${row.latestEvaluation ? '（最新）' : '（過去）'}` : ''}`));
      } else tr.append(element('td', row.totalCases ? `${row.matchedCases}/${row.totalCases}件一致` : 'チェック結果なし'));
      tr.append(element('td', consent(row.consent)), element('td', row.historyLabel || '—'));
      const action = element('td');
      if (evaluations) {
        const link = element('a', '詳細', 'btn btn-outline-primary btn-sm');
        link.href = url({ submissionId: row.submissionId, evaluationId: row.evaluationId });
        action.append(link);
      } else {
        const button = element('button', '詳細', 'btn btn-outline-primary btn-sm');
        button.type = 'button'; button.addEventListener('click', () => openSubmission(position)); action.append(button);
      }
      tr.append(action); body.append(tr);
    });
    document.getElementById('reviewCount').textContent = `${rows.length}件`;
    const csv = document.getElementById('reviewCsv');
    if (csv) csv.href = url({ view: 'csv' });
  }
  function navigation() {
    document.getElementById('previousSubmission').disabled = busy || index <= 0;
    document.getElementById('nextSubmission').disabled = busy || index >= rows.length - 1;
    document.getElementById('previewRun').disabled = busy || !selected;
    modalElement.querySelectorAll('[data-bs-dismiss]').forEach(button => { button.disabled = busy; });
  }
  async function openSubmission(position) {
    if (busy || position < 0 || position >= rows.length) return;
    const request = ++sequence;
    selected = null; busy = true; navigation();
    try {
      const detail = await fetchJson(url({ view: 'detail', submissionId: rows[position].submissionId }));
      if (request !== sequence) return;
      index = position; selected = detail;
      document.getElementById('submissionTitle').textContent = `${detail.row.studentLoginId} / ${detail.row.taskName} / 第${detail.row.revision}版`;
      document.getElementById('submissionMetadata').textContent = `${detail.row.schoolName} / ${detail.row.className} / ${detail.row.submittedAt} / ${consent(detail.row.consent)} / ${detail.row.historyLabel}`;
      document.getElementById('submittedSnapshot').textContent = detail.code;
      document.getElementById('previewCode').value = detail.code;
      document.getElementById('previewInput').value = '';
      document.getElementById('previewOutput').textContent = '';
      document.getElementById('previewAlert').replaceChildren();
      document.getElementById('submissionCheckSummary').textContent = detail.checks.length ? `${detail.row.matchedCases}/${detail.row.totalCases}件一致` : '保存されたチェック結果はありません。';
      const checks = document.getElementById('submissionChecks'); checks.replaceChildren();
      detail.checks.forEach(check => {
        const block = element('section');
        block.append(element('h4', `ケース${check.order}: ${check.status === 'matched' ? '○' : check.status === 'not_run' ? '未実行' : '×'} (${check.status})`));
        for (const [label, text] of [['入力', check.input], ['期待する出力', check.expectedOutput], ['実際の出力', check.actualOutput]]) {
          block.append(element('strong', label), element('pre', text || '', 'review-code'));
        }
        if (check.truncated || check.errorCode) block.append(element('p', `${check.truncated ? '出力上限で切り詰め ' : ''}${check.errorCode || ''}`));
        checks.append(block);
      });
      modal.show();
    } catch (error) { notify(error); }
    finally { busy = false; navigation(); }
  }
  document.getElementById('previousSubmission').addEventListener('click', () => openSubmission(index - 1));
  document.getElementById('nextSubmission').addEventListener('click', () => openSubmission(index + 1));
  modalElement.addEventListener('hide.bs.modal', event => { if (busy) event.preventDefault(); });
  modalElement.addEventListener('hidden.bs.modal', () => {
    selected = null; ++sequence;
    ['previewCode', 'previewInput'].forEach(id => { document.getElementById(id).value = ''; });
    ['submittedSnapshot', 'submissionChecks', 'previewOutput'].forEach(id => { document.getElementById(id).replaceChildren(); });
  });
  document.getElementById('previewRun').addEventListener('click', async () => {
    if (!selected || busy) return;
    const code = document.getElementById('previewCode').value, input = document.getElementById('previewInput').value;
    if (new TextEncoder().encode(code).length > 65536 || new TextEncoder().encode(input).length > 8192) {
      notify(new Error('コードは64 KiB、入力は8 KiB以下にしてください。'), true); return;
    }
    busy = true; navigation();
    document.getElementById('previewOutput').textContent = '実行中…';
    try {
      const result = await fetchJson(endpoint, { method: 'POST', body: new URLSearchParams({
        action: 'preview', submissionId: selected.row.submissionId, code, standardInput: input, csrfToken: root.dataset.csrfToken
      }) });
      document.getElementById('previewOutput').textContent = `${result.status} / 終了コード ${result.exitCode ?? '—'}\n標準出力${result.standardOutputTruncated ? '（上限で切り詰め）' : ''}\n${result.standardOutput}\n標準エラー${result.standardErrorTruncated ? '（上限で切り詰め）' : ''}\n${result.standardError}\n${result.errorCode || ''}`;
    } catch (error) { document.getElementById('previewOutput').textContent = ''; notify(error, true); }
    finally { busy = false; navigation(); }
  });
  async function showEvaluation(submissionId, evaluationId) {
    const detail = await fetchJson(url({ view: 'detail', submissionId, evaluationId }));
    document.getElementById('reviewList').hidden = true;
    document.getElementById('evaluationDetail').hidden = false;
    document.getElementById('backToReviews').href = url({});
    document.getElementById('evaluationTitle').textContent = `${detail.row.studentLoginId} / ${detail.row.taskName} / 第${detail.row.revision}版`;
    document.getElementById('evaluationMetadata').textContent = `${difficulty(detail.row.difficulty)} / 実施期間 ${detail.publishedAt || '未設定'} ～ ${detail.dueAt || '期限なし'} / 提出 ${detail.row.submittedAt} / ${detail.row.historyLabel}`;
    document.getElementById('evaluationState').textContent = `${status(detail.row.evaluationStatus)}（提出版ごとの自動評価履歴。変更不可）`;
    const versions = document.getElementById('evaluationVersions'); versions.replaceChildren();
    detail.evaluations.forEach(version => {
      const option = element('option', `評価${version.evaluationId} / ${status(version.status)} / ${version.createdAt} / ${version.kind}`);
      option.value = version.evaluationId; option.selected = version.evaluationId === detail.row.evaluationId; versions.append(option);
    });
    versions.disabled = !detail.evaluations.length;
    versions.onchange = () => { location.href = url({ submissionId, evaluationId: versions.value }); };
    const previous = document.getElementById('previousEvaluation'); previous.replaceChildren();
    if (detail.previousCompletedEvaluation) {
      const old = detail.previousCompletedEvaluation;
      previous.append(element('p', `前回の評価: 提出第${old.revision}版 / 評価${old.evaluationId} / ${old.completedAt}`));
      const link = element('a', '前回評価を確認'); link.href = url({ submissionId: old.submissionId, evaluationId: old.evaluationId }); previous.append(link);
    }
    const results = document.getElementById('evaluationResults'); results.replaceChildren();
    const reasons = document.getElementById('evaluationReasons'); reasons.replaceChildren();
    const evaluation = detail.evaluation;
    if (!evaluation) {
      results.append(element('p', '評価結果はまだありません。'));
    } else {
      results.append(element('p', `固定ルーブリック版: ${evaluation.rubricVersion} / 固定プロンプト版: ${evaluation.promptVersion || '未設定'}`));
      evaluation.dimensions.forEach(dimension => {
        results.append(element('h4', dimension.label), score(dimension.score),
          element('p', dimension.summaryTitle), element('p', dimension.summaryDescription));
      });
      results.append(element('p', evaluation.feedbackSummary));
      reasons.append(element('p', evaluation.processAnalysis));
      evaluation.scores.forEach(metric => {
        reasons.append(element('h4', `${metric.dimensionLabel} / ${metric.title} (${metric.score}/5)`),
          element('p', metric.description), element('p', metric.rationale));
      });
      evaluation.reasons.forEach(reason => {
        reasons.append(element('h4', reason.title), element('p', reason.body));
        const list = element('ul'); reason.details.forEach(text => list.append(element('li', text))); reasons.append(list);
      });
    }
    const times = document.getElementById('codeLogTimes'); times.replaceChildren();
    detail.logs.forEach((log, i) => {
      const option = element('option', `${log.observedAt} / ${log.eventType}`); option.value = i; times.append(option);
    });
    function showLog() {
      const log = detail.logs[Number(times.value)];
      document.getElementById('codeLogState').textContent = log ? `${log.eventType} / ${log.executionStatus || '実行なし'}` : 'この提出版のコードログはありません。';
      document.getElementById('codeLogSnapshot').textContent = log ? log.snapshot || '' : '';
    }
    times.disabled = !detail.logs.length; times.onchange = showLog;
    if (detail.logs.length) times.value = detail.logs.length - 1;
    showLog();
    document.getElementById('evaluationSubmission').textContent = detail.code;
  }
  filters.addEventListener('submit', async event => {
    event.preventDefault();
    try { await loadList(); history.replaceState(null, '', url({})); }
    catch (error) { notify(error); }
  });
  (async () => {
    try {
      populateOptions(await fetchJson(url({ view: 'list' }, new URLSearchParams())));
      if (evaluations && root.dataset.selectedSubmission) await showEvaluation(root.dataset.selectedSubmission, root.dataset.selectedEvaluation || null);
      else await loadList();
    } catch (error) { notify(error); }
  })();
});
