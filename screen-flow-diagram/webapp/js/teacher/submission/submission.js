// Teacher submission review page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeSubmissionPage();
});

const pageFeedback = window.PPEFeedback.createPageFeedback({ title: '提出課題確認' });

const LEVEL_SORT_ORDER = {
  '初級': 1,
  '中級': 2,
  '上級': 3
};

const CONSENT_SORT_ORDER = {
  '不同意': 1,
  '未確認': 2,
  '同意': 3
};

const submissions = [
  {
    studentId: 's001',
    school: '国際中等',
    className: '1年A組',
    taskTitle: 'じゃんけん判定',
    level: '初級',
    submittedAt: '2026-07-03 09:12:11',
    consent: '同意',
    submittedCode: 'player = input()\ncomputer = "グー"\n\nif player == computer:\n    print("あいこ")\nelif player == "パー":\n    print("あなたの勝ち")\nelse:\n    print("あなたの負け")',
    workingCode: '',
    ioChecks: [
      { input: 'パー', expected: 'あなたの勝ち', actual: 'あなたの勝ち' },
      { input: 'チョキ', expected: 'あなたの負け', actual: 'あなたの負け' },
      { input: 'グー', expected: 'あいこ', actual: 'あいこ' }
    ],
    operationLogs: []
  },
  {
    studentId: 's002',
    school: '国際中等',
    className: '1年A組',
    taskTitle: '在庫管理',
    level: '中級',
    submittedAt: '2026-07-03 08:54:20',
    consent: '同意',
    submittedCode: 'stock = int(input())\nused = int(input())\n\nrest = stock - used\nprint(rest)',
    workingCode: '',
    ioChecks: [
      { input: '10\\n3', expected: '7', actual: '7' },
      { input: '4\\n9', expected: '-5', actual: '-4' },
      { input: '100\\n20', expected: '80', actual: '80' }
    ],
    operationLogs: []
  },
  {
    studentId: 's117',
    school: '附属高校',
    className: '4年2組',
    taskTitle: '経路探索',
    level: '上級',
    submittedAt: '2026-07-02 17:31:40',
    consent: '未確認',
    submittedCode: 'from collections import deque\n\nstart = input().strip()\ngoal = input().strip()\n\nprint(start + "->" + goal)',
    workingCode: '',
    ioChecks: [
      { input: 'A\\nC', expected: 'A->B->C', actual: 'A->C' },
      { input: 'X\\nX', expected: 'X', actual: 'X' }
    ],
    operationLogs: []
  },
  {
    studentId: 's118',
    school: '附属高校',
    className: '4年4組',
    taskTitle: '在庫管理',
    level: '中級',
    submittedAt: '2026-07-03 10:01:00',
    consent: '不同意',
    submittedCode: 'items = list(map(int, input().split()))\nprint(sum(items))',
    workingCode: '',
    ioChecks: [
      { input: '1 2 3', expected: '6', actual: '6' },
      { input: '10 20', expected: '30', actual: '30' }
    ],
    operationLogs: []
  }
];

let currentSortBy = 'submittedAtDesc';
let filteredRows = [];
let detailIndex = -1;
let detailModal = null;
let submittedCodeEditor = null;
let workingCodeEditor = null;

function initializeSubmissionPage() {
  detailModal = bootstrap.Modal.getOrCreateInstance(document.getElementById('submissionDetailModal'));

  initializeCodeMirrors();
  bindTableSortEvents();
  bindFilterEvents();
  bindDetailEvents();

  applyFiltersAndSort();
}

function initializeCodeMirrors() {
  const submittedCodeView = document.getElementById('submittedCodeView');
  const workCodeEditorElement = document.getElementById('workCodeEditor');

  if (submittedCodeView && typeof CodeMirror !== 'undefined') {
    submittedCodeEditor = CodeMirror.fromTextArea(submittedCodeView, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker'
    });
  }

  if (workCodeEditorElement && typeof CodeMirror !== 'undefined') {
    workingCodeEditor = CodeMirror.fromTextArea(workCodeEditorElement, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      theme: 'material-darker',
      indentUnit: 4,
      tabSize: 4
    });
  }
}

function bindTableSortEvents() {
  const headers = document.querySelectorAll('#submissionTable thead th.sortable');
  headers.forEach(function(header) {
    header.addEventListener('click', function() {
      const sortKey = header.dataset.sortKey;
      if (!sortKey) return;

      const currentKey = getSortKey(currentSortBy);
      const currentDirection = getSortDirection(currentSortBy);
      const nextDirection = currentKey === sortKey && currentDirection === 'asc' ? 'Desc' : 'Asc';

      currentSortBy = sortKey + nextDirection;
      applyFiltersAndSort();
    });
  });
}

function bindFilterEvents() {
  ['filterSchool', 'filterClass', 'filterTask', 'filterLevel', 'filterConsent'].forEach(function(id) {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', applyFiltersAndSort);
    }
  });

  const searchInput = document.getElementById('searchInput');
  if (searchInput) {
    searchInput.addEventListener('input', applyFiltersAndSort);
  }
}

function bindDetailEvents() {
  const tableBody = document.getElementById('submissionTableBody');
  if (tableBody) {
    tableBody.addEventListener('click', function(event) {
      const button = event.target.closest('[data-action="open-detail"]');
      if (!button) return;

      const index = Number(button.dataset.index);
      if (Number.isNaN(index)) return;

      openDetailAt(index);
    });
  }

  const runWorkCodeButton = document.getElementById('runWorkCodeButton');
  if (runWorkCodeButton) {
    runWorkCodeButton.addEventListener('click', runWorkingCode);
  }

  const detailPrevButton = document.getElementById('detailPrevButton');
  if (detailPrevButton) {
    detailPrevButton.addEventListener('click', function() {
      moveDetail(-1);
    });
  }

  const detailNextButton = document.getElementById('detailNextButton');
  if (detailNextButton) {
    detailNextButton.addEventListener('click', function() {
      moveDetail(1);
    });
  }

  const modalElement = document.getElementById('submissionDetailModal');
  if (modalElement) {
    modalElement.addEventListener('hidden.bs.modal', function() {
      persistWorkingCode();
      detailIndex = -1;
    });
  }
}

function getSortKey(sortBy) {
  if (sortBy.endsWith('Desc')) {
    return sortBy.slice(0, -4);
  }
  if (sortBy.endsWith('Asc')) {
    return sortBy.slice(0, -3);
  }
  return sortBy;
}

function getSortDirection(sortBy) {
  return sortBy.endsWith('Desc') ? 'desc' : 'asc';
}

function applyFiltersAndSort() {
  const schoolFilter = getFilterValue('filterSchool');
  const classFilter = getFilterValue('filterClass');
  const taskFilter = getFilterValue('filterTask');
  const levelFilter = getFilterValue('filterLevel');
  const consentFilter = getFilterValue('filterConsent');
  const query = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();

  filteredRows = submissions.filter(function(item) {
    const matchesSchool = schoolFilter === 'すべて' || item.school === schoolFilter;
    const matchesClass = classFilter === 'すべて' || item.className === classFilter;
    const matchesTask = taskFilter === 'すべて' || item.taskTitle === taskFilter;
    const matchesLevel = levelFilter === 'すべて' || item.level === levelFilter;
    const matchesConsent = consentFilter === 'すべて' || item.consent === consentFilter;
    const matchesQuery = query.length === 0
      || item.studentId.toLowerCase().includes(query)
      || item.taskTitle.toLowerCase().includes(query);

    return matchesSchool && matchesClass && matchesTask && matchesLevel && matchesConsent && matchesQuery;
  });

  sortFilteredRows();
  renderTable();
  updateSummaryStats();
}

function getFilterValue(id) {
  return document.getElementById(id)?.value || 'すべて';
}

function sortFilteredRows() {
  const sortKey = getSortKey(currentSortBy);
  const direction = getSortDirection(currentSortBy);
  const factor = direction === 'asc' ? 1 : -1;

  filteredRows.sort(function(a, b) {
    if (sortKey === 'submittedAt') {
      return (new Date(a.submittedAt).getTime() - new Date(b.submittedAt).getTime()) * factor;
    }

    if (sortKey === 'matchRate') {
      return (getMatchRate(a) - getMatchRate(b)) * factor;
    }

    if (sortKey === 'level') {
      return ((LEVEL_SORT_ORDER[a.level] || 99) - (LEVEL_SORT_ORDER[b.level] || 99)) * factor;
    }

    if (sortKey === 'consent') {
      return ((CONSENT_SORT_ORDER[a.consent] || 99) - (CONSENT_SORT_ORDER[b.consent] || 99)) * factor;
    }

    const textA = String(a[sortKey] || '');
    const textB = String(b[sortKey] || '');
    return textA.localeCompare(textB, 'ja') * factor;
  });

  updateSortIndicators();
}

function updateSortIndicators() {
  const activeKey = getSortKey(currentSortBy);
  const activeDirection = getSortDirection(currentSortBy);

  document.querySelectorAll('#submissionTable thead th.sortable').forEach(function(header) {
    header.classList.remove('sorted-asc', 'sorted-desc');
    header.removeAttribute('aria-sort');

    if (header.dataset.sortKey === activeKey) {
      header.classList.add(activeDirection === 'asc' ? 'sorted-asc' : 'sorted-desc');
      header.setAttribute('aria-sort', activeDirection === 'asc' ? 'ascending' : 'descending');
    }
  });
}

function renderTable() {
  const tbody = document.getElementById('submissionTableBody');
  if (!tbody) return;

  if (filteredRows.length === 0) {
    tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">条件に一致する提出はありません。</td></tr>';
    return;
  }

  tbody.innerHTML = filteredRows.map(function(item, index) {
    const summary = summarizeIoChecks(item);
    const levelClass = getLevelClass(item.level);
    const consentClass = getConsentClass(item.consent);
    const matchClass = summary.failedCount === 0 ? 'is-pass' : 'is-partial';

    return '<tr>'
      + '<td><code>' + escapeHtml(item.studentId) + '</code></td>'
      + '<td>' + escapeHtml(item.school) + '</td>'
      + '<td>' + escapeHtml(item.className) + '</td>'
      + '<td>' + escapeHtml(item.taskTitle) + '</td>'
      + '<td><span class="badge ' + levelClass + '">' + escapeHtml(item.level) + '</span></td>'
      + '<td><span class="match-pill ' + matchClass + '">' + summary.passedCount + '/' + summary.totalCount + '件一致</span></td>'
      + '<td>' + escapeHtml(item.submittedAt) + '</td>'
      + '<td><span class="consent ' + consentClass + '">' + escapeHtml(item.consent) + '</span></td>'
      + '<td><button type="button" class="btn btn-sm btn-outline-primary" data-action="open-detail" data-index="' + index + '">表示</button></td>'
      + '</tr>';
  }).join('');
}

function updateSummaryStats() {
  const visibleCount = filteredRows.length;
  let fullMatchCount = 0;
  let totalRate = 0;

  filteredRows.forEach(function(item) {
    const summary = summarizeIoChecks(item);
    const rate = summary.totalCount === 0 ? 0 : (summary.passedCount / summary.totalCount);
    totalRate += rate;
    if (summary.failedCount === 0 && summary.totalCount > 0) {
      fullMatchCount += 1;
    }
  });

  const averageRate = visibleCount === 0 ? 0 : Math.round((totalRate / visibleCount) * 100);

  setText('visibleCount', String(visibleCount));
  setText('fullMatchCount', String(fullMatchCount));
  setText('avgMatchRate', averageRate + '%');
}

function openDetailAt(index) {
  if (index < 0 || index >= filteredRows.length) {
    return;
  }

  persistWorkingCode();
  detailIndex = index;
  renderDetail();
  detailModal.show();
}

function moveDetail(delta) {
  if (detailIndex < 0) {
    return;
  }

  const nextIndex = detailIndex + delta;
  if (nextIndex < 0 || nextIndex >= filteredRows.length) {
    return;
  }

  persistWorkingCode();
  detailIndex = nextIndex;
  renderDetail();
}

function renderDetail() {
  const item = filteredRows[detailIndex];
  if (!item) {
    return;
  }

  const summary = summarizeIoChecks(item);
  setText('detailMeta', item.studentId + ' / ' + item.school + ' ' + item.className + ' / ' + item.taskTitle + '（' + item.level + '） / 提出: ' + item.submittedAt);
  setText('detailPagerText', (detailIndex + 1) + ' / ' + filteredRows.length);
  setText('workCodeOutput', '未実行です。');

  if (submittedCodeEditor) {
    submittedCodeEditor.setValue(item.submittedCode);
    submittedCodeEditor.refresh();
  }

  const workCode = item.workingCode || item.submittedCode;
  if (workingCodeEditor) {
    workingCodeEditor.setValue(workCode);
    workingCodeEditor.refresh();
  }

  renderIoResultTable(item.ioChecks);
  updateMatchSummary(summary);
  updateNavButtons();
}

function renderIoResultTable(ioChecks) {
  const tableBody = document.getElementById('ioResultTableBody');
  if (!tableBody) return;

  if (!ioChecks || ioChecks.length === 0) {
    tableBody.innerHTML = '<tr><td colspan="4" class="text-muted text-center">入出力チェック結果はありません。</td></tr>';
    return;
  }

  tableBody.innerHTML = ioChecks.map(function(row) {
    const isPass = normalizeText(row.actual) === normalizeText(row.expected);
    const rowClass = isPass ? 'is-pass' : 'is-fail';
    const mark = isPass ? '○' : '×';

    return '<tr class="' + rowClass + '">'
      + '<td>' + escapeHtml(row.input) + '</td>'
      + '<td>' + escapeHtml(row.expected) + '</td>'
      + '<td>' + escapeHtml(row.actual) + '</td>'
      + '<td class="text-center"><span class="result-mark">' + mark + '</span></td>'
      + '</tr>';
  }).join('');
}

function runWorkingCode() {
  if (detailIndex < 0 || detailIndex >= filteredRows.length) {
    return;
  }

  const item = filteredRows[detailIndex];
  const code = workingCodeEditor ? workingCodeEditor.getValue() : '';
  const output = simulateExecution(code);
  setText('workCodeOutput', output);

  pageFeedback.toast({
    title: '提出課題確認',
    message: '確認用コードを実行しました。',
    variant: 'success'
  });
}

function downloadSubmittedCodeFile() {
  if (detailIndex < 0 || detailIndex >= filteredRows.length) {
    pageFeedback.toast({
      title: '提出課題確認',
      message: '先に詳細を表示してください。',
      variant: 'warning'
    });
    return;
  }

  const item = filteredRows[detailIndex];
  const taskSegment = String(item.taskTitle || 'task').replace(/[\\/:*?"<>|\s]+/g, '_');
  const filename = item.studentId + '_' + taskSegment + '.py';
  const content = item.submittedCode || '';

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(link.href);

  pageFeedback.toast({
    title: '提出課題確認',
    message: '提出コードをダウンロードしました。',
    variant: 'success'
  });
}

function refreshSubmissionRows() {
  applyFiltersAndSort();

  pageFeedback.toast({
    title: '提出課題確認',
    message: '一覧を更新しました。',
    variant: 'success'
  });
}

function exportSubmissionCSV() {
  if (!filteredRows.length) {
    pageFeedback.toast({
      title: '提出課題確認',
      message: '出力対象の提出データがありません。',
      variant: 'warning'
    });
    return;
  }

  const header = ['生徒ID', '学校', 'クラス', '課題名', '難易度', '一致件数', '提出日時', '研究同意'];
  const body = filteredRows.map(function(item) {
    const summary = summarizeIoChecks(item);
    return [
      item.studentId,
      item.school,
      item.className,
      item.taskTitle,
      item.level,
      summary.passedCount + '/' + summary.totalCount,
      item.submittedAt,
      item.consent
    ];
  });

  const csv = [header].concat(body)
    .map(function(cols) { return cols.map(escapeCSV).join(','); })
    .join('\r\n');

  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'submission_list_' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  pageFeedback.toast({
    title: '提出課題確認',
    message: 'CSVをダウンロードしました。',
    variant: 'success'
  });
}

async function bulkSaveSubmissionFiles() {
  if (!filteredRows.length) {
    pageFeedback.toast({
      title: '提出課題確認',
      message: 'ダウンロード対象の提出データがありません。',
      variant: 'warning'
    });
    return;
  }

  if (typeof JSZip === 'undefined') {
    pageFeedback.toast({
      title: '提出課題確認',
      message: 'ZIPライブラリの読み込みに失敗しました。',
      variant: 'danger'
    });
    return;
  }

  try {
    const zip = new JSZip();
    filteredRows.forEach(function(item) {
      const taskSegment = String(item.taskTitle || 'task').replace(/[\\/:*?"<>|\s]+/g, '_');
      const fileName = item.studentId + '_' + taskSegment + '.py';
      zip.file('submissions/' + fileName, item.submittedCode || '');
    });

    const blob = await zip.generateAsync({ type: 'blob' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'submission_files_' + new Date().toISOString().slice(0, 10) + '.zip';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);

    pageFeedback.toast({
      title: '提出課題確認',
      message: '提出ファイルを一括ダウンロードしました。',
      variant: 'success'
    });
  } catch (error) {
    pageFeedback.toast({
      title: '提出課題確認',
      message: '一括ダウンロードに失敗しました。',
      variant: 'danger'
    });
  }
}

function persistWorkingCode() {
  if (detailIndex < 0 || detailIndex >= filteredRows.length || !workingCodeEditor) {
    return;
  }

  filteredRows[detailIndex].workingCode = workingCodeEditor.getValue();
}

function simulateExecution(code) {
  const includesInput = code.includes('input(');
  if (includesInput) {
    return '$ python main.py\\n入力待ちの処理があります。\\nテスト入力: パー\\n\\n実行結果:\\nあなたの勝ち';
  }

  return '$ python main.py\\n実行しました。\\n\\n標準出力:\\n(ダミー実行結果)';
}

function updateMatchSummary(summary) {
  const summaryEl = document.getElementById('ioMatchSummary');
  if (!summaryEl) return;

  summaryEl.textContent = summary.passedCount + '/' + summary.totalCount + '件一致';
  summaryEl.classList.remove('is-pass', 'is-partial');
  summaryEl.classList.add(summary.failedCount === 0 ? 'is-pass' : 'is-partial');
}

function summarizeIoChecks(item) {
  const checks = item.ioChecks || [];
  const passedCount = checks.filter(function(row) {
    return normalizeText(row.actual) === normalizeText(row.expected);
  }).length;

  return {
    totalCount: checks.length,
    passedCount,
    failedCount: checks.length - passedCount
  };
}

function getMatchRate(item) {
  const summary = summarizeIoChecks(item);
  if (summary.totalCount === 0) {
    return 0;
  }
  return summary.passedCount / summary.totalCount;
}

function getLevelClass(level) {
  if (level === '初級') return 'difficulty-beginner';
  if (level === '中級') return 'difficulty-intermediate';
  return 'difficulty-advanced';
}

function getConsentClass(consent) {
  if (consent === '同意') return 'consent-ok';
  if (consent === '未確認') return 'consent-pending';
  return 'consent-no';
}

function updateNavButtons() {
  const prevButton = document.getElementById('detailPrevButton');
  const nextButton = document.getElementById('detailNextButton');

  if (prevButton) {
    prevButton.disabled = detailIndex <= 0;
  }

  if (nextButton) {
    nextButton.disabled = detailIndex >= filteredRows.length - 1;
  }
}

function normalizeText(value) {
  return String(value || '').replace(/\r\n/g, '\n').trim();
}

function setText(id, text) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = text;
  }
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function escapeCSV(value) {
  const text = String(value ?? '');
  if (text.includes(',') || text.includes('"') || text.includes('\n')) {
    return '"' + text.replace(/"/g, '""') + '"';
  }
  return text;
}
