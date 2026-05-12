// Teacher history page interactions

document.addEventListener('DOMContentLoaded', function() {
  initializeHistoryPage();
});

function initializeHistoryPage() {
  ['filterClass', 'filterSchool', 'filterConsent', 'filterStatus', 'sortBy'].forEach(function(id) {
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

function applyFiltersAndSort() {
  const classFilter = document.getElementById('filterClass')?.value || 'すべて';
  const schoolFilter = document.getElementById('filterSchool')?.value || 'すべて';
  const consentFilter = document.getElementById('filterConsent')?.value || 'すべて';
  const statusFilter = document.getElementById('filterStatus')?.value || 'すべて';
  const sortBy = document.getElementById('sortBy')?.value || 'id';
  const query = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();

  const tbody = document.querySelector('#historyTable tbody');
  if (!tbody) return;

  const rows = Array.from(tbody.querySelectorAll('tr'));

  rows.forEach(function(row) {
    const matchClass = classFilter === 'すべて' || row.dataset.class === classFilter;
    const matchSchool = schoolFilter === 'すべて' || row.dataset.school === schoolFilter;
    const matchConsent = consentFilter === 'すべて' || row.dataset.consent === consentFilter;
    const matchStatus = statusFilter === 'すべて' || row.dataset.status === statusFilter;
    const matchQuery = query.length === 0 ||
      row.dataset.id.toLowerCase().includes(query) ||
      row.dataset.task.toLowerCase().includes(query);

    row.style.display = (matchClass && matchSchool && matchConsent && matchStatus && matchQuery) ? '' : 'none';
  });

  const visibleRows = rows.filter(function(row) { return row.style.display !== 'none'; });
  sortRows(visibleRows, sortBy);
  visibleRows.forEach(function(row) { tbody.appendChild(row); });

  updateSummary(visibleRows);
}

function sortRows(rows, sortBy) {
  rows.sort(function(a, b) {
    if (sortBy === 'elapsedDesc') return toSeconds(b.dataset.elapsed) - toSeconds(a.dataset.elapsed);
    if (sortBy === 'elapsedAsc') return toSeconds(a.dataset.elapsed) - toSeconds(b.dataset.elapsed);
    if (sortBy === 'updatedDesc') return new Date(b.dataset.updated).getTime() - new Date(a.dataset.updated).getTime();
    return a.dataset.id.localeCompare(b.dataset.id, 'ja');
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

function openHistoryDetail(button) {
  const row = button.closest('tr');
  if (!row) return;

  setText('detailId', row.dataset.id);
  setText('detailClass', row.dataset.school + ' / ' + row.dataset.class);
  setText('detailTask', row.dataset.task);
  setText('detailStatus', row.dataset.status + '（' + row.dataset.elapsed + '）');

  const timeline = document.getElementById('detailTimeline');
  if (timeline) {
    timeline.innerHTML = '';
    [
      row.dataset.updated.split(' ')[1] + ' - コード保存',
      '10:18:30 - 実行（成功）',
      '10:14:05 - コード保存'
    ].forEach(function(line) {
      const li = document.createElement('li');
      li.textContent = line;
      timeline.appendChild(li);
    });
  }

  const modalEl = document.getElementById('historyDetailModal');
  if (!modalEl) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function refreshRows() {
  const now = new Date();
  const time = now.toTimeString().slice(0, 8);
  const rows = document.querySelectorAll('#historyTable tbody tr');

  rows.forEach(function(row) {
    if (row.dataset.status === '編集中') {
      row.dataset.updated = now.toISOString().slice(0, 10) + ' ' + time;
      row.cells[6].textContent = time;
    }
  });

  applyFiltersAndSort();
}

function exportHistoryCSV() {
  const rows = Array.from(document.querySelectorAll('#historyTable tbody tr'))
    .filter(function(row) { return row.style.display !== 'none'; });

  const header = ['生徒ID', '学校', 'クラス', '実施中の課題', '状態', '取り組み時間', '最終更新', '研究同意'];
  const body = rows.map(function(row) {
    return [
      row.dataset.id,
      row.dataset.school,
      row.dataset.class,
      row.dataset.task,
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
