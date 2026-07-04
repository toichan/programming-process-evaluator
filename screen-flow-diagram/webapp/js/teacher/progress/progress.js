// Teacher history page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeHistoryPage();
});
const pageFeedback = window.PPEFeedback.createPageFeedback({ title: '課題進捗確認機能' });

let currentSortBy = 'updatedDesc';
let latestCodeEditor = null;

const STATUS_SORT_ORDER = {
  '未着手': 1,
  '編集中': 2,
  '提出済み': 3
};

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

function initializeHistoryPage() {
  initializeCodeViewer();
  syncUpdatedDateDisplay();
  initializeHeaderSorting();

  ['filterClass', 'filterSchool', 'filterConsent', 'filterStatus', 'filterLevel', 'filterTask'].forEach(function(id) {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', applyFiltersAndSort);
    }
  });

  const searchInput = document.getElementById('searchInput');
  if (searchInput) {
    searchInput.addEventListener('input', applyFiltersAndSort);
  }

  initSummaryChart();
  applyFiltersAndSort();
}

function initializeCodeViewer() {
  const textarea = document.getElementById('latestCodeViewer');
  if (!textarea || typeof CodeMirror === 'undefined') return;

  latestCodeEditor = CodeMirror.fromTextArea(textarea, {
    mode: 'python',
    lineNumbers: true,
    lineWrapping: false,
    readOnly: true,
    cursorBlinkRate: -1,
    theme: 'material-darker'
  });
}

function initializeHeaderSorting() {
  const headers = document.querySelectorAll('#historyTable thead th.sortable');
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

function updateHeaderSortIndicator(sortBy) {
  const activeKey = getSortKey(sortBy);
  const activeDirection = getSortDirection(sortBy);
  const headers = document.querySelectorAll('#historyTable thead th.sortable');
  headers.forEach(function(header) {
    header.classList.remove('sorted-asc', 'sorted-desc');
    header.removeAttribute('aria-sort');

    const key = header.dataset.sortKey;
    if (key === activeKey) {
      header.classList.add(activeDirection === 'asc' ? 'sorted-asc' : 'sorted-desc');
      header.setAttribute('aria-sort', activeDirection === 'asc' ? 'ascending' : 'descending');
    }
  });
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

function syncUpdatedDateDisplay() {
  const rows = document.querySelectorAll('#historyTable tbody tr');
  rows.forEach(function(row) {
    if (row.cells[7]) {
      row.cells[7].textContent = row.dataset.updated || '';
    }
  });
}

function applyFiltersAndSort() {
  const classFilter = document.getElementById('filterClass')?.value || 'すべて';
  const schoolFilter = document.getElementById('filterSchool')?.value || 'すべて';
  const consentFilter = document.getElementById('filterConsent')?.value || 'すべて';
  const statusFilter = document.getElementById('filterStatus')?.value || 'すべて';
  const levelFilter = document.getElementById('filterLevel')?.value || 'すべて';
  const taskFilter = document.getElementById('filterTask')?.value || 'すべて';
  const sortBy = currentSortBy;
  const query = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();

  const tbody = document.querySelector('#historyTable tbody');
  if (!tbody) return;

  const rows = Array.from(tbody.querySelectorAll('tr'));

  rows.forEach(function(row) {
    const matchClass = classFilter === 'すべて' || row.dataset.class === classFilter;
    const matchSchool = schoolFilter === 'すべて' || row.dataset.school === schoolFilter;
    const matchConsent = consentFilter === 'すべて' || row.dataset.consent === consentFilter;
    const matchStatus = statusFilter === 'すべて' || row.dataset.status === statusFilter;
    const matchLevel = levelFilter === 'すべて' || row.dataset.level === levelFilter;
    const matchTask = taskFilter === 'すべて' || row.dataset.task === taskFilter;
    const matchQuery = query.length === 0 ||
      row.dataset.id.toLowerCase().includes(query) ||
      row.dataset.task.toLowerCase().includes(query);

    row.style.display = (matchClass && matchSchool && matchConsent && matchStatus && matchLevel && matchTask && matchQuery) ? '' : 'none';
  });

  const visibleRows = rows.filter(function(row) { return row.style.display !== 'none'; });
  sortRows(visibleRows, sortBy);
  visibleRows.forEach(function(row) { tbody.appendChild(row); });
  updateHeaderSortIndicator(sortBy);

  updateSummary(visibleRows);
}

function sortRows(rows, sortBy) {
  const sortKey = getSortKey(sortBy);
  const sortDirection = getSortDirection(sortBy);
  const multiplier = sortDirection === 'desc' ? -1 : 1;

  rows.sort(function(a, b) {
    if (sortKey === 'elapsed') {
      return (toSeconds(a.dataset.elapsed) - toSeconds(b.dataset.elapsed)) * multiplier;
    }

    if (sortKey === 'updated') {
      return (new Date(a.dataset.updated).getTime() - new Date(b.dataset.updated).getTime()) * multiplier;
    }

    if (sortKey === 'status') {
      const orderA = STATUS_SORT_ORDER[a.dataset.status] || 99;
      const orderB = STATUS_SORT_ORDER[b.dataset.status] || 99;
      if (orderA !== orderB) {
        return (orderA - orderB) * multiplier;
      }
      return a.dataset.id.localeCompare(b.dataset.id, 'ja') * multiplier;
    }

    if (sortKey === 'level') {
      const orderA = LEVEL_SORT_ORDER[a.dataset.level] || 99;
      const orderB = LEVEL_SORT_ORDER[b.dataset.level] || 99;
      if (orderA !== orderB) {
        return (orderA - orderB) * multiplier;
      }
      return a.dataset.id.localeCompare(b.dataset.id, 'ja') * multiplier;
    }

    if (sortKey === 'consent') {
      const orderA = CONSENT_SORT_ORDER[a.dataset.consent] || 99;
      const orderB = CONSENT_SORT_ORDER[b.dataset.consent] || 99;
      if (orderA !== orderB) {
        return (orderA - orderB) * multiplier;
      }
      return a.dataset.id.localeCompare(b.dataset.id, 'ja') * multiplier;
    }

    if (sortKey === 'school' || sortKey === 'class' || sortKey === 'task' || sortKey === 'id') {
      return (a.dataset[sortKey] || '').localeCompare(b.dataset[sortKey] || '', 'ja') * multiplier;
    }

    return a.dataset.id.localeCompare(b.dataset.id, 'ja') * multiplier;
  });
}

function toSeconds(hhmmss) {
  const parts = hhmmss.split(':').map(function(v) { return Number(v) || 0; });
  return parts[0] * 3600 + parts[1] * 60 + parts[2];
}

function updateSummary(visibleRows) {
  let active = 0;
  let submitted = 0;

  visibleRows.forEach(function(row) {
    if (row.dataset.status === '編集中') active += 1;
    if (row.dataset.status === '提出済み') submitted += 1;
  });

  setText('visibleCount', String(visibleRows.length));
  setText('activeCount', String(active));
  setText('submittedCount', String(submitted));
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

const ACTIVITY_HISTORY_BY_STUDENT = {
  s001: [
    { datetime: '2026-05-12 10:24:20', type: 'save' },
    { datetime: '2026-05-12 10:22:41', type: 'run', success: true },
    { datetime: '2026-05-12 10:21:10', type: 'save' },
    { datetime: '2026-05-12 10:18:30', type: 'run', success: true },
    { datetime: '2026-05-12 10:14:05', type: 'save' }
  ],
  s002: [
    { datetime: '2026-05-12 10:10:02', type: 'save' },
    { datetime: '2026-05-12 10:08:55', type: 'run', success: true },
    { datetime: '2026-05-12 10:05:43', type: 'save' },
    { datetime: '2026-05-12 10:02:20', type: 'run', success: false },
    { datetime: '2026-05-12 09:59:12', type: 'save' }
  ],
  s117: [
    { datetime: '2026-05-11 16:32:18', type: 'save' },
    { datetime: '2026-05-11 16:28:09', type: 'save' },
    { datetime: '2026-05-11 16:23:40', type: 'run', success: false },
    { datetime: '2026-05-11 16:21:02', type: 'save' }
  ],
  s003: [
    { datetime: '2026-05-11 14:20:00', type: 'save' },
    { datetime: '2026-05-11 14:17:39', type: 'run', success: true },
    { datetime: '2026-05-11 14:15:12', type: 'save' },
    { datetime: '2026-05-11 14:10:44', type: 'run', success: true },
    { datetime: '2026-05-11 14:08:31', type: 'save' }
  ],
  s004: [
    { datetime: '2026-05-11 09:00:00', type: 'save' },
    { datetime: '2026-05-11 08:58:16', type: 'save' }
  ],
  s005: [
    { datetime: '2026-05-12 09:55:11', type: 'save' },
    { datetime: '2026-05-12 09:53:54', type: 'run', success: true },
    { datetime: '2026-05-12 09:51:27', type: 'save' },
    { datetime: '2026-05-12 09:49:02', type: 'run', success: false },
    { datetime: '2026-05-12 09:47:10', type: 'save' }
  ],
  s006: [
    { datetime: '2026-05-11 09:00:00', type: 'save' },
    { datetime: '2026-05-11 08:56:42', type: 'save' }
  ],
  s118: [
    { datetime: '2026-05-12 08:40:00', type: 'save' },
    { datetime: '2026-05-12 08:37:18', type: 'run', success: true },
    { datetime: '2026-05-12 08:34:02', type: 'save' },
    { datetime: '2026-05-12 08:28:16', type: 'run', success: true },
    { datetime: '2026-05-12 08:24:48', type: 'save' }
  ],
  s119: [
    { datetime: '2026-05-12 10:15:00', type: 'save' },
    { datetime: '2026-05-12 10:12:21', type: 'run', success: true },
    { datetime: '2026-05-12 10:10:58', type: 'save' },
    { datetime: '2026-05-12 10:07:06', type: 'run', success: false },
    { datetime: '2026-05-12 10:04:29', type: 'save' }
  ],
  s203: [
    { datetime: '2026-05-12 10:25:04', type: 'save' },
    { datetime: '2026-05-12 10:23:41', type: 'run', success: true },
    { datetime: '2026-05-12 10:21:10', type: 'save' },
    { datetime: '2026-05-12 10:18:57', type: 'run', success: false },
    { datetime: '2026-05-12 10:16:12', type: 'save' }
  ],
  s204: [
    { datetime: '2026-05-11 15:30:00', type: 'save' },
    { datetime: '2026-05-11 15:26:47', type: 'run', success: true },
    { datetime: '2026-05-11 15:24:08', type: 'save' },
    { datetime: '2026-05-11 15:20:55', type: 'run', success: true },
    { datetime: '2026-05-11 15:18:14', type: 'save' }
  ],
  s205: [
    { datetime: '2026-05-11 09:00:00', type: 'save' },
    { datetime: '2026-05-11 08:54:22', type: 'save' }
  ]
};

const LATEST_CODE_BY_STUDENT = {
  s001: {
    fileName: 'janken.py',
    updatedAt: '2026-05-12 10:24:20',
    code: 'player = input().strip()\ncomputer = "グー"\n\nif player == computer:\n    print("あいこ")\nelif player == "パー":\n    print("あなたの勝ち")\nelse:\n    print("あなたの負け")'
  },
  s002: {
    fileName: 'stock.py',
    updatedAt: '2026-05-12 10:10:02',
    code: 'stock = int(input())\nused = int(input())\n\nrest = stock - used\nprint(rest)'
  },
  s117: {
    fileName: 'route.py',
    updatedAt: '2026-05-11 16:32:18',
    code: 'from collections import deque\n\nstart = input().strip()\ngoal = input().strip()\n\nqueue = deque([start])\nvisited = {start}\n\nwhile queue:\n    node = queue.popleft()\n    if node == goal:\n        break\n\nprint(start + "->" + goal)'
  },
  s003: {
    fileName: 'janken.py',
    updatedAt: '2026-05-11 14:20:00',
    code: 'you = input().strip()\npc = "チョキ"\n\nif you == pc:\n    print("あいこ")\nelif (you == "グー" and pc == "チョキ") or (you == "チョキ" and pc == "パー") or (you == "パー" and pc == "グー"):\n    print("あなたの勝ち")\nelse:\n    print("あなたの負け")'
  },
  s004: {
    fileName: 'janken.py',
    updatedAt: '2026-05-11 09:00:00',
    code: '# まだ実装途中\nplayer = input()\nprint(player)'
  },
  s005: {
    fileName: 'janken.py',
    updatedAt: '2026-05-12 09:55:11',
    code: 'hand = input().strip()\npc = "パー"\n\nif hand == pc:\n    print("あいこ")\nelif hand == "チョキ":\n    print("あなたの勝ち")\nelse:\n    print("あなたの負け")'
  },
  s006: {
    fileName: 'janken.py',
    updatedAt: '2026-05-11 09:00:00',
    code: '# 未着手\n'
  },
  s118: {
    fileName: 'route.py',
    updatedAt: '2026-05-12 08:40:00',
    code: 'n, m = map(int, input().split())\nedges = []\nfor _ in range(m):\n    a, b = map(int, input().split())\n    edges.append((a, b))\n\nprint(len(edges))'
  },
  s119: {
    fileName: 'route.py',
    updatedAt: '2026-05-12 10:15:00',
    code: 'graph = {"A": ["B", "C"], "B": ["D"], "C": ["D"], "D": []}\nstart = input().strip()\ngoal = input().strip()\n\nprint(start + "->" + goal)'
  },
  s203: {
    fileName: 'budget.py',
    updatedAt: '2026-05-12 10:25:04',
    code: 'values = list(map(int, input().split()))\n\nprint(sum(values))\nprint(max(values))\nprint(min(values))'
  },
  s204: {
    fileName: 'budget.py',
    updatedAt: '2026-05-11 15:30:00',
    code: 'items = list(map(int, input().split()))\n\ntotal = sum(items)\navg = total / len(items)\nprint(total)\nprint(round(avg, 2))'
  },
  s205: {
    fileName: 'budget.py',
    updatedAt: '2026-05-11 09:00:00',
    code: '# 未着手\n'
  }
};

function getLatestCode(studentId, fallbackUpdatedAt) {
  const latest = LATEST_CODE_BY_STUDENT[studentId];
  if (latest) return latest;

  return {
    fileName: '-',
    updatedAt: fallbackUpdatedAt || '-',
    code: '# 最新コードがありません'
  };
}

function getActivityHistory(studentId, updatedAt) {
  const history = ACTIVITY_HISTORY_BY_STUDENT[studentId];
  if (history && history.length > 0) {
    return history;
  }

  return [
    { datetime: updatedAt || '2026-05-12 10:00:00', type: 'save' }
  ];
}

function formatActivity(entry) {
  const time = entry.datetime.split(' ')[1] || entry.datetime;
  if (entry.type === 'run') {
    return time + ' - 実行（' + (entry.success ? '成功' : '失敗') + '）';
  }
  return time + ' - 手動保存';
}

function getActivityTypeLabel(entry) {
  return entry.type === 'run' ? '実行' : '手動保存';
}

function getActivityResult(entry) {
  if (entry.type === 'run') {
    return entry.success ? '成功' : '失敗';
  }
  return '完了';
}

function getActivityNote(entry) {
  if (entry.type === 'run') {
    return entry.success ? '入出力チェック実行' : '実行エラーあり';
  }
  return 'コード保存スナップショット';
}

function exportDetailActivityCSV() {
  const studentId = document.getElementById('detailId')?.textContent?.trim() || '';
  if (!studentId || studentId === '-') {
    pageFeedback.toast({
      title: '課題進捗確認機能',
      message: '先に生徒の詳細を表示してください。',
      variant: 'warning'
    });
    return;
  }

  const detailRow = document.querySelector('#historyTable tbody tr[data-id="' + studentId + '"]');
  const updatedAt = detailRow?.dataset.updated || '';
  const history = getActivityHistory(studentId, updatedAt);
  const school = detailRow?.dataset.school || '';
  const className = detailRow?.dataset.class || '';
  const task = detailRow?.dataset.task || '';

  const header = ['生徒ID', '学校', 'クラス', '課題', '日時', '種別', '結果'];
  const body = history.map(function(entry) {
    return [
      studentId,
      school,
      className,
      task,
      entry.datetime,
      entry.type === 'run' ? '実行' : '手動保存',
      entry.type === 'run' ? (entry.success ? '成功' : '失敗') : ''
    ];
  });

  const csv = [header].concat(body)
    .map(function(cols) { return cols.map(escapeCSV).join(','); })
    .join('\r\n');

  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'activity_history_' + studentId + '_' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  pageFeedback.toast({
    title: '課題進捗確認機能',
    message: 'CSVをダウンロードしました。',
    variant: 'success'
  });
}

function exportLatestCodeFile() {
  const studentId = document.getElementById('detailId')?.textContent?.trim() || '';
  if (!studentId || studentId === '-') {
    pageFeedback.toast({
      title: '課題進捗確認機能',
      message: '先に生徒の詳細を表示してください。',
      variant: 'warning'
    });
    return;
  }

  const detailRow = document.querySelector('#historyTable tbody tr[data-id="' + studentId + '"]');
  const latestCode = getLatestCode(studentId, detailRow?.dataset.updated || '');
  const fileName = latestCode.fileName && latestCode.fileName !== '-' ? latestCode.fileName : 'latest_code.py';
  const code = latestCode.code || '# 最新コードがありません';

  const blob = new Blob([code], { type: 'text/plain;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = studentId + '_' + fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

function openHistoryDetail(button) {
  const row = button.closest('tr');
  if (!row) return;

  setText('detailId', row.dataset.id);
  setText('detailClass', row.dataset.school + ' / ' + row.dataset.class);
  setText('detailTask', row.dataset.task + ' / ' + row.dataset.level);
  setText('detailStatus', row.dataset.status + '（' + row.dataset.elapsed + '）');
  setText('historyDetailMeta', row.dataset.id + ' / '
    + row.dataset.school + ' ' + row.dataset.class + ' / '
    + row.dataset.task + '（' + row.dataset.level + '） / 更新: '
    + (row.dataset.updated || '-'));

  const timelineTableBody = document.getElementById('detailTimelineTableBody');
  if (timelineTableBody) {
    const history = getActivityHistory(row.dataset.id, row.dataset.updated);
    timelineTableBody.innerHTML = history.map(function(entry) {
      const typeLabel = getActivityTypeLabel(entry);
      const result = getActivityResult(entry);
      const note = getActivityNote(entry);
      const badgeClass = entry.type === 'run'
        ? (entry.success ? 'bg-success-subtle text-success-emphasis' : 'bg-danger-subtle text-danger-emphasis')
        : 'bg-secondary-subtle text-secondary-emphasis';

      return '<tr>'
        + '<td>' + entry.datetime + '</td>'
        + '<td><span class="badge ' + badgeClass + '">' + typeLabel + '</span></td>'
        + '<td>' + result + '</td>'
        + '<td>' + note + '</td>'
        + '</tr>';
    }).join('');
  }

  const latestCode = getLatestCode(row.dataset.id, row.dataset.updated);
  setText('detailCodeUpdatedAt', '更新: ' + (latestCode.updatedAt || '-'));
  if (latestCodeEditor) {
    latestCodeEditor.setValue(latestCode.code || '');
    latestCodeEditor.refresh();
  }

  const modalEl = document.getElementById('historyDetailModal');
  if (!modalEl) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function refreshRows() {
  const now = new Date();
  const time = now.toTimeString().slice(0, 8);
  const date = now.toISOString().slice(0, 10);
  const datetime = date + ' ' + time;
  const rows = document.querySelectorAll('#historyTable tbody tr');

  rows.forEach(function(row) {
    if (row.dataset.status === '編集中') {
      row.dataset.updated = datetime;
      if (row.cells[7]) {
        row.cells[7].textContent = datetime;
      }
    }
  });

  applyFiltersAndSort();
}

function exportHistoryCSV() {
  const rows = Array.from(document.querySelectorAll('#historyTable tbody tr'))
    .filter(function(row) { return row.style.display !== 'none'; });

  const header = ['生徒ID', '学校', 'クラス', '課題', '難易度', '状態', '取り組み時間', '最終更新', '研究同意'];
  const body = rows.map(function(row) {
    return [
      row.dataset.id,
      row.dataset.school,
      row.dataset.class,
      row.dataset.task,
      row.dataset.level,
      row.dataset.status,
      row.dataset.elapsed,
      row.dataset.updated,
      row.dataset.consent
    ];
  });

  const csv = [header].concat(body)
    .map(function(cols) { return cols.map(escapeCSV).join(','); })
    .join('\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'learning_history_' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  pageFeedback.toast({
    title: '課題進捗確認機能',
    message: 'CSVをダウンロードしました。',
    variant: 'success'
  });
}

function escapeCSV(value) {
  if (value.includes(',') || value.includes('"') || value.includes('\n')) {
    return '"' + value.replace(/"/g, '""') + '"';
  }
  return value;
}

// ── 進捗サマリーチャート ──

let summaryDonutChart = null;

function initSummaryChart() {
  const ctx = document.getElementById('summaryDonut');
  if (!ctx || typeof Chart === 'undefined') return;

  summaryDonutChart = new Chart(ctx, {
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
      animation: { duration: 500 },
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
              const total = context.dataset.data.reduce(function(a, b) { return a + b; }, 0);
              const pct = total > 0 ? Math.round((context.parsed / total) * 100) : 0;
              return ' ' + context.label + ': ' + context.parsed + '人 (' + pct + '%)';
            }
          }
        }
      }
    }
  });

  updateSummaryChart();
}

function updateSummaryChart() {
  const selSchool = document.getElementById('summarySchool')?.value || 'すべて';
  const selClass = document.getElementById('summaryClass')?.value || 'すべて';
  const selTask  = document.getElementById('summaryTask')?.value  || 'すべて';

  const rows = Array.from(document.querySelectorAll('#historyTable tbody tr'));
  const filtered = rows.filter(function(row) {
    const matchSchool = selSchool === 'すべて' || row.dataset.school === selSchool;
    const matchClass = selClass === 'すべて' || row.dataset.class === selClass;
    const matchTask  = selTask  === 'すべて' || row.dataset.task  === selTask;
    return matchSchool && matchClass && matchTask;
  });

  let todo = 0, active = 0, done = 0;
  filtered.forEach(function(row) {
    if      (row.dataset.status === '未着手')   todo++;
    else if (row.dataset.status === '編集中')   active++;
    else if (row.dataset.status === '提出済み') done++;
  });

  setText('summaryTodo',   String(todo));
  setText('summaryActive', String(active));
  setText('summaryDone',   String(done));

  const total = todo + active + done;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;
  setText('completionPct', pct + '%');

  const bar = document.getElementById('completionBar');
  if (bar) {
    bar.style.width = pct + '%';
    bar.setAttribute('aria-valuenow', String(pct));
  }

  if (summaryDonutChart) {
    summaryDonutChart.data.datasets[0].data = [todo, active, done];
    summaryDonutChart.update();
  }
}
