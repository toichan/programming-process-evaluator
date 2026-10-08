document.addEventListener('DOMContentLoaded', initializeProgressPage);

const progressFeedback = window.PPEFeedback.createPageFeedback({ title: '課題進捗確認機能' });
const PROGRESS_FILTER_STORAGE_KEY = 'teacher-progress-filters';
const STATUS_SORT_ORDER = {
  '未着手': 1,
  '編集中': 2,
  '提出済み': 3,
  '評価待ち': 4,
  '完了': 5,
  '要対応': 6
};
const CONSENT_SORT_ORDER = {
  '不同意': 1,
  '未確認': 2,
  '同意': 3
};
let currentSortBy = 'updatedDesc';
let summaryDonutChart = null;
let detailModalOpen = false;
let latestCodeEditor = null;
let detailRequestSequence = 0;
let codeDownloadEndpoint = null;
let detailTrigger = null;

function initializeProgressPage() {
  const rows = getRows();
  populateFilterOptions(rows);
  restoreFilterState();
  initializeSummaryChart();
  initializeSorting();
  initializeEvents();
  const textarea = document.getElementById('latestCodeViewer');
  if (textarea && window.CodeMirror) {
    latestCodeEditor = window.CodeMirror.fromTextArea(textarea, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker'
    });
  }
  applyFiltersAndSort();
  scheduleRefresh();
}

function getRows() {
  return Array.from(document.querySelectorAll('#historyTable tbody tr[data-progress-row]'));
}

function populateFilterOptions(rows) {
  appendOptions('filterSchool', uniqueOptions(rows, 'schoolId', 'school'));
  appendOptions('summarySchool', uniqueOptions(rows, 'schoolId', 'school'));
  appendOptions('filterClass', uniqueOptions(rows, 'classId', 'class', 'school'));
  appendOptions('summaryClass', uniqueOptions(rows, 'classId', 'class', 'school'));
  appendOptions('filterTask', uniqueOptions(rows, 'taskId', 'task'));
  appendOptions('summaryTask', uniqueOptions(rows, 'taskId', 'task'));
  appendOptions('filterLevel', uniqueOptions(rows, 'level', 'level'));
  appendOptions('filterStatus', uniqueOptions(rows, 'status', 'status'));
}

function uniqueOptions(rows, valueKey, labelKey, parentKey) {
  const options = new Map();
  rows.forEach(function(row) {
    const value = row.dataset[valueKey] || '';
    const label = row.dataset[labelKey] || '';
    if (!value || !label) return;
    const parent = parentKey ? row.dataset[parentKey] || '' : '';
    options.set(value, { value: value, label: parent ? parent + ' / ' + label : label });
  });
  return Array.from(options.values()).sort(function(a, b) {
    return a.label.localeCompare(b.label, 'ja');
  });
}

function appendOptions(selectId, options) {
  const select = document.getElementById(selectId);
  if (!select) return;
  options.forEach(function(option) {
    const element = document.createElement('option');
    element.value = option.value;
    element.textContent = option.label;
    select.appendChild(element);
  });
}

function initializeEvents() {
  ['filterClass', 'filterSchool', 'filterConsent', 'filterStatus', 'filterLevel', 'filterTask',
    'summarySchool', 'summaryClass', 'summaryTask'].forEach(function(id) {
    const element = document.getElementById(id);
    if (!element) return;
    element.addEventListener('change', function() {
      saveFilterState();
      applyFiltersAndSort();
    });
  });

  const search = document.getElementById('searchInput');
  if (search) {
    search.addEventListener('input', function() {
      saveFilterState();
      applyFiltersAndSort();
    });
  }

  document.getElementById('refreshProgress')?.addEventListener('click', refreshRows);
  document.getElementById('exportProgressCsv')?.addEventListener('click', exportHistoryCSV);
  document.getElementById('downloadLatestCode')?.addEventListener('click', downloadLatestCode);
  document.querySelector('#historyTable tbody')?.addEventListener('click', function(event) {
    const button = event.target.closest('[data-open-progress-detail]');
    if (button) openHistoryDetail(button);
  });

  const modal = document.getElementById('historyDetailModal');
  modal?.addEventListener('show.bs.modal', function() { detailModalOpen = true; });
  modal?.addEventListener('shown.bs.modal', function() { latestCodeEditor?.refresh(); });
  modal?.addEventListener('hide.bs.modal', function() {
    if (modal.contains(document.activeElement)) document.activeElement.blur();
  });
  modal?.addEventListener('hidden.bs.modal', function() {
    detailModalOpen = false;
    detailRequestSequence += 1;
    detailTrigger?.focus();
  });
}

function initializeSorting() {
  document.querySelectorAll('#historyTable thead th.sortable').forEach(function(header) {
    header.addEventListener('click', function() {
      const sortKey = header.dataset.sortKey;
      if (!sortKey) return;
      const activeKey = getSortKey(currentSortBy);
      const direction = activeKey === sortKey && getSortDirection(currentSortBy) === 'asc' ? 'Desc' : 'Asc';
      currentSortBy = sortKey + direction;
      saveFilterState();
      applyFiltersAndSort();
    });
  });
}

function applyFiltersAndSort() {
  const schoolId = selectedValue('filterSchool');
  const classId = selectedValue('filterClass');
  const taskId = selectedValue('filterTask');
  const level = selectedValue('filterLevel');
  const status = selectedValue('filterStatus');
  const consent = selectedValue('filterConsent');
  const query = (document.getElementById('searchInput')?.value || '').trim().toLocaleLowerCase('ja');
  const visibleRows = [];
  const allRows = getRows();

  allRows.forEach(function(row) {
    const matches = (schoolId === 'すべて' || row.dataset.schoolId === schoolId)
      && (classId === 'すべて' || row.dataset.classId === classId)
      && (taskId === 'すべて' || row.dataset.taskId === taskId)
      && (level === 'すべて' || row.dataset.level === level)
      && (status === 'すべて' || row.dataset.status === status)
      && (consent === 'すべて' || row.dataset.consent === consent)
      && (!query || row.dataset.id.toLocaleLowerCase('ja').includes(query)
        || row.dataset.task.toLocaleLowerCase('ja').includes(query));
    row.hidden = !matches;
    if (matches) visibleRows.push(row);
  });

  const sortKey = getSortKey(currentSortBy);
  const multiplier = getSortDirection(currentSortBy) === 'desc' ? -1 : 1;
  visibleRows.sort(function(a, b) {
    return compareRows(a, b, sortKey) * multiplier;
  });
  const tbody = document.querySelector('#historyTable tbody');
  visibleRows.forEach(function(row) { tbody.appendChild(row); });
  updateHeaderSortIndicator();
  updateVisibleSummary(visibleRows);
  updateSummaryChart();

  const noMatches = document.getElementById('emptyFilterMessage');
  if (noMatches) noMatches.hidden = visibleRows.length > 0 || allRows.length === 0;
}

function selectedValue(id) {
  return document.getElementById(id)?.value || 'すべて';
}

function compareRows(a, b, key) {
  if (key === 'elapsed') {
    return Number(a.dataset.durationSeconds) - Number(b.dataset.durationSeconds);
  }
  if (key === 'updated') {
    return (a.dataset.updated || '').localeCompare(b.dataset.updated || '');
  }
  if (key === 'status') {
    return (STATUS_SORT_ORDER[a.dataset.status] || 99) - (STATUS_SORT_ORDER[b.dataset.status] || 99);
  }
  if (key === 'consent') {
    return (CONSENT_SORT_ORDER[a.dataset.consent] || 99) - (CONSENT_SORT_ORDER[b.dataset.consent] || 99);
  }
  const aValue = key === 'id' ? a.dataset.id : key === 'school' ? a.dataset.school
    : key === 'class' ? a.dataset.class : key === 'task' ? a.dataset.task : a.dataset.level;
  const bValue = key === 'id' ? b.dataset.id : key === 'school' ? b.dataset.school
    : key === 'class' ? b.dataset.class : key === 'task' ? b.dataset.task : b.dataset.level;
  return (aValue || '').localeCompare(bValue || '', 'ja');
}

function getSortKey(sortBy) {
  return sortBy.endsWith('Desc') ? sortBy.slice(0, -4) : sortBy.endsWith('Asc') ? sortBy.slice(0, -3) : sortBy;
}

function getSortDirection(sortBy) {
  return sortBy.endsWith('Desc') ? 'desc' : 'asc';
}

function updateHeaderSortIndicator() {
  const activeKey = getSortKey(currentSortBy);
  const direction = getSortDirection(currentSortBy);
  document.querySelectorAll('#historyTable thead th.sortable').forEach(function(header) {
    header.classList.remove('sorted-asc', 'sorted-desc');
    header.removeAttribute('aria-sort');
    if (header.dataset.sortKey === activeKey) {
      header.classList.add(direction === 'asc' ? 'sorted-asc' : 'sorted-desc');
      header.setAttribute('aria-sort', direction === 'asc' ? 'ascending' : 'descending');
    }
  });
}

function updateVisibleSummary(rows) {
  const counts = countStatuses(rows);
  setText('visibleCount', String(rows.length));
  setText('activeCount', String(counts.active));
  setText('submittedCount', String(counts.submitted));
}

function countStatuses(rows) {
  let todo = 0;
  let active = 0;
  let submitted = 0;
  rows.forEach(function(row) {
    if (row.dataset.status === '未着手') todo += 1;
    else if (row.dataset.status === '編集中' || row.dataset.status === '要対応') active += 1;
    else submitted += 1;
  });
  return { todo: todo, active: active, submitted: submitted };
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value;
}

function initializeSummaryChart() {
  const canvas = document.getElementById('summaryDonut');
  if (!canvas || typeof Chart === 'undefined') return;
  summaryDonutChart = new Chart(canvas, {
    type: 'doughnut',
    data: {
      labels: ['未着手', '編集中', '提出済み'],
      datasets: [{
        data: [0, 0, 0],
        backgroundColor: ['#94a3b8', '#f59e0b', '#22c55e'],
        borderWidth: 3,
        borderColor: '#fff',
        hoverOffset: 6
      }]
    },
    options: {
      cutout: '62%',
      animation: { duration: 250 },
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            font: { family: "'Noto Sans JP', sans-serif", size: 12 },
            padding: 14,
            usePointStyle: true,
            pointStyleWidth: 10
          }
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const total = context.dataset.data.reduce(function(sum, value) { return sum + value; }, 0);
              const percent = total ? Math.round((context.parsed / total) * 100) : 0;
              return ' ' + context.label + ': ' + context.parsed + '件 (' + percent + '%)';
            }
          }
        }
      }
    }
  });
}

function updateSummaryChart() {
  const schoolId = selectedValue('summarySchool');
  const classId = selectedValue('summaryClass');
  const taskId = selectedValue('summaryTask');
  const rows = getRows().filter(function(row) {
    return (schoolId === 'すべて' || row.dataset.schoolId === schoolId)
      && (classId === 'すべて' || row.dataset.classId === classId)
      && (taskId === 'すべて' || row.dataset.taskId === taskId);
  });
  const counts = countStatuses(rows);
  setText('summaryTodo', String(counts.todo));
  setText('summaryActive', String(counts.active));
  setText('summaryDone', String(counts.submitted));

  const total = rows.length;
  const completionPercent = total ? Math.round((counts.submitted / total) * 100) : 0;
  setText('completionPct', completionPercent + '%');
  const bar = document.getElementById('completionBar');
  if (bar) {
    bar.style.width = completionPercent + '%';
    bar.setAttribute('aria-valuenow', String(completionPercent));
  }
  if (summaryDonutChart) {
    summaryDonutChart.data.datasets[0].data = [counts.todo, counts.active, counts.submitted];
    summaryDonutChart.update();
  }
}

async function openHistoryDetail(button) {
  const row = button.closest('tr[data-progress-row]');
  if (!row) return;
  const sequence = ++detailRequestSequence;
  detailTrigger = button;
  codeDownloadEndpoint = null;
  document.getElementById('downloadLatestCode').disabled = true;
  setLatestCode('');
  setText('detailCodeDescription', '保存済み・提出済みのコードを読み込みます。');
  detailModalOpen = true;
  setText('historyDetailMeta', row.dataset.id + ' / ' + row.dataset.school + ' ' + row.dataset.class
    + ' / ' + row.dataset.task + '（' + row.dataset.level + '） / 更新: '
    + (row.dataset.updated || '-'));
  setText('detailCodeUpdatedAt', '更新: -');
  const timeline = document.getElementById('detailTimelineTableBody');
  renderActivityMessage(timeline, '履歴を読み込みます。');

  const root = document.getElementById('teacherProgress');
  const endpoint = new URL(root.dataset.detailEndpoint, window.location.origin);
  endpoint.searchParams.set('view', 'detail');
  endpoint.searchParams.set('assignmentId', row.dataset.assignmentId);
  endpoint.searchParams.set('studentUserId', row.dataset.studentUserId);
  const modal = document.getElementById('historyDetailModal');
  if (modal && window.bootstrap?.Modal) {
    bootstrap.Modal.getOrCreateInstance(modal).show();
  }

  try {
    const response = await fetch(endpoint, {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) throw new Error('詳細を取得できませんでした (HTTP ' + response.status + ')');
    const detail = await response.json();
    if (sequence !== detailRequestSequence) return;
    renderActivities(timeline, detail.activities || []);
    if (detail.latestCode) {
      setLatestCode(detail.latestCode.code);
      setText('detailCodeUpdatedAt', '更新: ' + detail.latestCode.updatedAt);
      setText('detailCodeDescription', detail.latestCode.source === 'submission'
        ? '提出済み: 最新の提出版のコードを確認できます。'
        : '保存下書き: 最終保存時点のコードを確認できます。');
      endpoint.searchParams.set('view', 'download');
      codeDownloadEndpoint = endpoint;
      document.getElementById('downloadLatestCode').disabled = false;
    } else {
      setText('detailCodeDescription', '保存済み・提出済みのコードはありません。');
    }
  } catch (failure) {
    if (sequence !== detailRequestSequence) return;
    setText('detailCodeDescription', 'コードを読み込めませんでした。画面を更新して再度お試しください。');
    renderActivityMessage(timeline, '履歴を読み込めませんでした。画面を更新して再度お試しください。');
    progressFeedback.toast({
      title: '課題進捗確認機能',
      message: failure.message || '詳細を取得できませんでした。',
      variant: 'danger'
    });
  }

}

function setLatestCode(code) {
  const textarea = document.getElementById('latestCodeViewer');
  if (textarea) textarea.value = code;
  if (latestCodeEditor) {
    latestCodeEditor.setValue(code);
    latestCodeEditor.refresh();
  }
}

async function downloadLatestCode() {
  if (!codeDownloadEndpoint) return;
  const endpoint = new URL(codeDownloadEndpoint);
  const sequence = detailRequestSequence;
  const button = document.getElementById('downloadLatestCode');
  button.disabled = true;
  try {
    const response = await fetch(endpoint, { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('コードをダウンロードできませんでした (HTTP ' + response.status + ')');
    if (!(response.headers.get('Content-Type') || '').startsWith('text/plain')) {
      throw new Error('ログイン状態を確認して、もう一度お試しください。');
    }
    const blob = await response.blob();
    if (sequence !== detailRequestSequence) return;
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'student-' + endpoint.searchParams.get('studentUserId')
      + '_assignment-' + endpoint.searchParams.get('assignmentId') + '_latest_code.py';
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(function() { URL.revokeObjectURL(objectUrl); }, 1000);
  } catch (failure) {
    if (sequence !== detailRequestSequence) return;
    progressFeedback.toast({ message: failure.message, variant: 'danger' });
  } finally {
    if (sequence === detailRequestSequence) button.disabled = !codeDownloadEndpoint;
  }
}

function renderActivityMessage(tbody, message) {
  if (!tbody) return;
  tbody.replaceChildren();
  const row = document.createElement('tr');
  const cell = document.createElement('td');
  cell.colSpan = 4;
  cell.className = 'text-center text-muted py-3';
  cell.textContent = message;
  row.appendChild(cell);
  tbody.appendChild(row);
}

function renderActivities(tbody, activities) {
  if (!tbody) return;
  tbody.replaceChildren();
  if (!activities.length) {
    renderActivityMessage(tbody, '学習活動の履歴はありません。');
    return;
  }

  activities.forEach(function(activity) {
    const row = document.createElement('tr');
    const values = [activity.occurredAt, activity.type, activity.result, activity.note];
    values.forEach(function(value, index) {
      const cell = document.createElement('td');
      if (index === 1) {
        const badge = document.createElement('span');
        badge.className = 'badge ' + activityBadgeClass(activity);
        badge.textContent = value;
        cell.appendChild(badge);
      } else {
        cell.textContent = value || '-';
      }
      row.appendChild(cell);
    });
    tbody.appendChild(row);
  });
}

function activityBadgeClass(activity) {
  if (activity.type === '実行') {
    return activity.result === '成功' ? 'bg-success-subtle text-success-emphasis'
      : activity.result === '失敗' || activity.result === '時間切れ'
        ? 'bg-danger-subtle text-danger-emphasis' : 'bg-warning-subtle text-warning-emphasis';
  }
  if (activity.type === '評価') {
    return activity.result === '要対応' ? 'bg-danger-subtle text-danger-emphasis'
      : 'bg-info-subtle text-info-emphasis';
  }
  return 'bg-secondary-subtle text-secondary-emphasis';
}

function refreshRows() {
  if (detailModalOpen) return;
  saveFilterState();
  window.location.reload();
}

function scheduleRefresh() {
  const root = document.getElementById('teacherProgress');
  if (!root) return;
  const interval = Number(root.dataset.refreshInterval) || 20000;
  window.setTimeout(function() {
    if (!detailModalOpen && document.visibilityState === 'visible') {
      refreshRows();
      return;
    }
    scheduleRefresh();
  }, interval);
}

function saveFilterState() {
  const state = {
    search: document.getElementById('searchInput')?.value || '',
    sortBy: currentSortBy
  };
  ['filterSchool', 'filterClass', 'filterTask', 'filterLevel', 'filterStatus', 'filterConsent',
    'summarySchool', 'summaryClass', 'summaryTask'].forEach(function(id) {
    state[id] = selectedValue(id);
  });
  sessionStorage.setItem(PROGRESS_FILTER_STORAGE_KEY, JSON.stringify(state));
}

function restoreFilterState() {
  const serialized = sessionStorage.getItem(PROGRESS_FILTER_STORAGE_KEY);
  if (!serialized) return;
  try {
    const state = JSON.parse(serialized);
    currentSortBy = typeof state.sortBy === 'string' ? state.sortBy : currentSortBy;
    const search = document.getElementById('searchInput');
    if (search && typeof state.search === 'string') search.value = state.search;
    Object.keys(state).forEach(function(id) {
      const element = document.getElementById(id);
      if (element && Array.from(element.options).some(function(option) { return option.value === state[id]; })) {
        element.value = state[id];
      }
    });
  } catch (failure) {
    sessionStorage.removeItem(PROGRESS_FILTER_STORAGE_KEY);
  }
}

function exportHistoryCSV() {
  const rows = getRows().filter(function(row) { return !row.hidden; });
  const header = ['生徒ID', '学校', 'クラス', '課題', '難易度', '状態', '取り組み時間', '最終更新', '研究同意'];
  const body = rows.map(function(row) {
    return [row.dataset.id, row.dataset.school, row.dataset.class, row.dataset.task, row.dataset.level,
      row.dataset.status, row.dataset.elapsed, row.dataset.updated || '', row.dataset.consent];
  });
  const csv = [header].concat(body)
    .map(function(columns) { return columns.map(escapeCSV).join(','); })
    .join('\r\n');
  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'learning_history_' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(function() { URL.revokeObjectURL(url); }, 1000);
  progressFeedback.toast({
    title: '課題進捗確認機能',
    message: 'CSVをダウンロードしました。',
    variant: 'success'
  });
}

function escapeCSV(value) {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}
