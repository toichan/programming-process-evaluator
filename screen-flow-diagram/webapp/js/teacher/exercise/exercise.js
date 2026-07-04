document.addEventListener('DOMContentLoaded', function() {
  initializeExerciseCodePage();
});

const pageFeedback = window.PPEFeedback?.createPageFeedback
  ? window.PPEFeedback.createPageFeedback({ title: '授業演習コード確認' })
  : null;

const CONSENT_SORT_ORDER = {
  '不同意': 1,
  '未確認': 2,
  '同意': 3
};

const exerciseStudents = [
  {
    studentId: 's001',
    school: '国際中等',
    className: '1年A組',
    updatedAt: '2026-07-03 16:24:20',
    consent: '同意',
    scope: '授業演習 / ウォームアップ',
    tree: {
      id: 'root-s001',
      type: 'folder',
      name: 's001',
      children: [
        {
          id: 's001-folder-warmup',
          type: 'folder',
          name: 'ウォームアップ',
          children: [
            {
              id: 's001-file-greeting',
              type: 'file',
              name: 'greeting.py',
              path: 'ウォームアップ/greeting.py',
              language: 'Python',
              updatedAt: '2026-07-03 16:24:20',
              note: '名前を入力してあいさつを表示する基本課題です。',
              content: 'name = input("名前を入力してください: ")\nprint(f"こんにちは、{name}さん")'
            },
            {
              id: 's001-file-repeat',
              type: 'file',
              name: 'repeat.py',
              path: 'ウォームアップ/repeat.py',
              language: 'Python',
              updatedAt: '2026-07-03 16:11:10',
              note: 'for文の繰り返し確認用ファイルです。',
              content: 'for count in range(3):\n    print("practice", count + 1)'
            }
          ]
        },
        {
          id: 's001-folder-notes',
          type: 'folder',
          name: '授業メモ',
          children: [
            {
              id: 's001-file-notes',
              type: 'file',
              name: 'notes.py',
              path: '授業メモ/notes.py',
              language: 'Python',
              updatedAt: '2026-07-03 15:48:00',
              note: '授業中に得た気づきを残すメモ用ファイルです。',
              content: '# 今日の気づき\nkeywords = ["input", "for", "if"]\nprint(keywords)'
            }
          ]
        }
      ]
    }
  },
  {
    studentId: 's002',
    school: '国際中等',
    className: '1年A組',
    updatedAt: '2026-07-03 15:02:11',
    consent: '同意',
    scope: '授業演習 / 条件分岐',
    tree: {
      id: 'root-s002',
      type: 'folder',
      name: 's002',
      children: [
        {
          id: 's002-folder-branch',
          type: 'folder',
          name: '条件分岐',
          children: [
            {
              id: 's002-file-score',
              type: 'file',
              name: 'score.py',
              path: '条件分岐/score.py',
              language: 'Python',
              updatedAt: '2026-07-03 15:02:11',
              note: '点数に応じて評価を変える練習です。',
              content: 'score = int(input())\nif score >= 80:\n    print("A")\nelif score >= 60:\n    print("B")\nelse:\n    print("C")'
            }
          ]
        }
      ]
    }
  },
  {
    studentId: 's117',
    school: '附属高校',
    className: '4年2組',
    updatedAt: '2026-07-02 17:31:40',
    consent: '未確認',
    scope: '授業演習 / グラフ探索',
    tree: {
      id: 'root-s117',
      type: 'folder',
      name: 's117',
      children: [
        {
          id: 's117-folder-search',
          type: 'folder',
          name: 'グラフ探索',
          children: [
            {
              id: 's117-file-path',
              type: 'file',
              name: 'path.py',
              path: 'グラフ探索/path.py',
              language: 'Python',
              updatedAt: '2026-07-02 17:31:40',
              note: '経路のたどり方を確認する練習ファイルです。',
              content: 'from collections import deque\n\nstart = input().strip()\ngoal = input().strip()\n\nqueue = deque([start])\nprint(start + "->" + goal)'
            },
            {
              id: 's117-file-data',
              type: 'file',
              name: 'data.py',
              path: 'グラフ探索/data.py',
              language: 'Python',
              updatedAt: '2026-07-02 17:05:00',
              note: '入力データを整理するための補助ファイルです。',
              content: 'edges = [("A", "B"), ("B", "C")]\nprint(edges)'
            }
          ]
        }
      ]
    }
  },
  {
    studentId: 's118',
    school: '附属高校',
    className: '4年4組',
    updatedAt: '2026-07-03 10:01:00',
    consent: '不同意',
    scope: '授業演習 / 集計課題',
    tree: {
      id: 'root-s118',
      type: 'folder',
      name: 's118',
      children: [
        {
          id: 's118-folder-report',
          type: 'folder',
          name: '集計課題',
          children: [
            {
              id: 's118-file-total',
              type: 'file',
              name: 'total.py',
              path: '集計課題/total.py',
              language: 'Python',
              updatedAt: '2026-07-03 10:01:00',
              note: '数値の合計と平均を確認するファイルです。',
              content: 'values = list(map(int, input().split()))\nprint(sum(values))\nprint(sum(values) / len(values))'
            }
          ]
        }
      ]
    }
  }
];

let currentSortBy = 'updatedAtDesc';
let filteredStudents = [];
let currentStudentId = exerciseStudents[0]?.studentId || '';
let currentFileId = '';
let codeViewer = null;
let exerciseDetailModal = null;

function initializeExerciseCodePage() {
  initializeCodeViewer();
  bindFilters();
  bindSorting();
  bindTableEvents();
  applyFiltersAndSort();
}

function initializeCodeViewer() {
  const modalElement = document.getElementById('exerciseDetailModal');
  if (typeof bootstrap !== 'undefined' && modalElement) {
    exerciseDetailModal = bootstrap.Modal.getOrCreateInstance(modalElement);
  }

  const textarea = document.getElementById('exerciseCodeViewer');
  if (textarea && typeof CodeMirror !== 'undefined') {
    codeViewer = CodeMirror.fromTextArea(textarea, {
      mode: 'python',
      lineNumbers: true,
      lineWrapping: false,
      readOnly: true,
      cursorBlinkRate: -1,
      theme: 'material-darker',
      viewportMargin: Infinity
    });
  }
}

function bindFilters() {
  ['filterSchool', 'filterClass', 'filterConsent', 'searchInput'].forEach(function(id) {
    const element = document.getElementById(id);
    if (!element) return;

    const eventName = element.tagName === 'INPUT' ? 'input' : 'change';
    element.addEventListener(eventName, applyFiltersAndSort);
  });
}

function bindSorting() {
  document.querySelectorAll('#exerciseTable thead th.sortable').forEach(function(header) {
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

function bindTableEvents() {
  const tbody = document.getElementById('exerciseTableBody');
  if (!tbody) return;

  tbody.addEventListener('click', function(event) {
    const button = event.target.closest('[data-action="select-student"]');
    if (!button) return;

    const studentId = button.dataset.studentId;
    if (!studentId) return;

    openStudentDetailById(studentId, true);
  });

  const tree = document.getElementById('exerciseTree');
  if (tree) {
    tree.addEventListener('click', function(event) {
      const button = event.target.closest('[data-action="select-file"]');
      if (!button) return;

      const fileId = button.dataset.fileId;
      if (!fileId) return;

      selectFile(fileId);
    });
  }
}

function applyFiltersAndSort() {
  const schoolFilter = getFilterValue('filterSchool');
  const classFilter = getFilterValue('filterClass');
  const consentFilter = getFilterValue('filterConsent');
  const query = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();

  filteredStudents = exerciseStudents.filter(function(student) {
    const matchesSchool = schoolFilter === 'すべて' || student.school === schoolFilter;
    const matchesClass = classFilter === 'すべて' || student.className === classFilter;
    const matchesConsent = consentFilter === 'すべて' || student.consent === consentFilter;
    const matchesQuery = query.length === 0 || student.studentId.toLowerCase().includes(query);

    return matchesSchool && matchesClass && matchesConsent && matchesQuery;
  });

  sortStudents();
  renderTable();
  updateStats();

  if (!filteredStudents.length) {
    renderEmptyDetail();
    return;
  }

  if (!filteredStudents.some(function(student) {
    return student.studentId === currentStudentId;
  })) {
    currentStudentId = filteredStudents[0].studentId;
  }

  openStudentDetailById(currentStudentId, false);
}

function getFilterValue(id) {
  return document.getElementById(id)?.value || 'すべて';
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

function sortStudents() {
  const sortKey = getSortKey(currentSortBy);
  const direction = getSortDirection(currentSortBy);
  const factor = direction === 'asc' ? 1 : -1;

  filteredStudents.sort(function(a, b) {
    if (sortKey === 'fileCount') {
      return (countFiles(a.tree) - countFiles(b.tree)) * factor;
    }

    if (sortKey === 'updatedAt') {
      return (new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime()) * factor;
    }

    if (sortKey === 'consent') {
      const orderA = CONSENT_SORT_ORDER[a.consent] || 99;
      const orderB = CONSENT_SORT_ORDER[b.consent] || 99;
      if (orderA !== orderB) {
        return (orderA - orderB) * factor;
      }
      return a.studentId.localeCompare(b.studentId, 'ja') * factor;
    }

    if (sortKey === 'studentId' || sortKey === 'school' || sortKey === 'className') {
      return (a[sortKey] || '').localeCompare(b[sortKey] || '', 'ja') * factor;
    }

    return a.studentId.localeCompare(b.studentId, 'ja') * factor;
  });
}

function renderTable() {
  const tbody = document.getElementById('exerciseTableBody');
  if (!tbody) return;

  tbody.innerHTML = filteredStudents.map(function(student, index) {
    const selected = student.studentId === currentStudentId ? ' is-active-row' : '';
    return `
      <tr class="exercise-row${selected}" data-index="${index}">
        <td><code>${escapeHtml(student.studentId)}</code></td>
        <td>${escapeHtml(student.school)}</td>
        <td>${escapeHtml(student.className)}</td>
        <td>${countFiles(student.tree)}</td>
        <td>${escapeHtml(formatDateTime(student.updatedAt))}</td>
        <td><span class="consent consent-${consentClass(student.consent)}">${escapeHtml(student.consent)}</span></td>
        <td><button class="btn btn-sm btn-outline-primary" type="button" data-action="select-student" data-student-id="${escapeHtml(student.studentId)}">表示</button></td>
      </tr>
    `;
  }).join('');
}

function updateStats() {
  const visibleCount = filteredStudents.length;
  const visibleFileCount = filteredStudents.reduce(function(total, student) {
    return total + countFiles(student.tree);
  }, 0);
  const latestUpdatedAt = filteredStudents.reduce(function(latest, student) {
    if (!latest) return student.updatedAt;
    return new Date(student.updatedAt) > new Date(latest) ? student.updatedAt : latest;
  }, '');

  setText('visibleCount', String(visibleCount));
  setText('visibleFileCount', String(visibleFileCount));
  setText('latestUpdatedAt', latestUpdatedAt ? formatDateTime(latestUpdatedAt) : '-');
}

function openStudentDetailById(studentId, openModal = false) {
  if (!filteredStudents.length) {
    renderEmptyDetail();
    return;
  }

  const safeIndex = filteredStudents.findIndex(function(student) {
    return student.studentId === studentId;
  });
  const selectedIndex = safeIndex >= 0 ? safeIndex : 0;
  const student = filteredStudents[selectedIndex];
  if (!student) {
    renderEmptyDetail();
    return;
  }

  currentStudentId = student.studentId;

  const firstFile = findFirstFile(student.tree);
  currentFileId = firstFile ? firstFile.id : '';

  renderDetail(student);
  renderTable();

  if (openModal && exerciseDetailModal) {
    exerciseDetailModal.show();
  }
}

function renderDetail(student) {
  const firstFile = findFirstFile(student.tree);
  const selectedFile = findFileById(student.tree, currentFileId) || firstFile;
  const studentMeta = `${student.school} / ${student.className}`;

  setText('detailStudentId', student.studentId);
  setText('detailMeta', student.studentId + ' / ' + studentMeta);
  setText('detailStudentMeta', studentMeta);
  setText('detailFileCountChip', `${countFiles(student.tree)}ファイル`);
  setText('detailFilePath', selectedFile ? selectedFile.path : '-');

  const treeEl = document.getElementById('exerciseTree');
  if (treeEl) {
    treeEl.innerHTML = renderTreeNode(student.tree, selectedFile?.id);
  }

  updateSelectedFileDetail(selectedFile);
}

function renderTreeNode(node, selectedFileId) {
  if (!node) return '';

  if (node.type === 'file') {
    const isSelected = node.id === selectedFileId ? ' is-selected' : '';
    return `
      <button type="button" class="exercise-tree-file${isSelected}" data-action="select-file" data-file-id="${escapeHtml(node.id)}">
        <span class="exercise-tree-file-icon">📄</span>
        <span class="exercise-tree-file-copy">
          <span class="exercise-tree-file-name">${escapeHtml(node.name)}</span>
          <span class="exercise-tree-entry-meta">${escapeHtml(node.path)}</span>
        </span>
      </button>
    `;
  }

  const children = Array.isArray(node.children) ? node.children.map(function(child) {
    return renderTreeNode(child, selectedFileId);
  }).join('') : '';

  const openAttribute = ' open';
  return `
    <details class="exercise-tree-folder"${openAttribute}>
      <summary class="exercise-tree-folder-summary">
        <span class="exercise-tree-folder-icon">📁</span>
        <span class="exercise-tree-folder-name">${escapeHtml(node.name)}</span>
        <span class="exercise-tree-entry-meta">${Array.isArray(node.children) ? node.children.length : 0} 件</span>
      </summary>
      <div class="exercise-tree-folder-children">
        ${children}
      </div>
    </details>
  `;
}

function selectFile(fileId) {
  const student = filteredStudents.find(function(item) {
    return item.studentId === currentStudentId;
  });
  if (!student) return;

  const file = findFileById(student.tree, fileId);
  if (!file) return;

  currentFileId = fileId;
  updateSelectedFileDetail(file);
  renderDetail(student);
}

function refreshExerciseRows() {
  applyFiltersAndSort();

  if (pageFeedback) {
    pageFeedback.toast({
      title: '授業演習コード確認',
      message: '一覧を更新しました。',
      variant: 'success'
    });
  }
}

function exportExerciseCSV() {
  if (!filteredStudents.length) {
    if (pageFeedback) {
      pageFeedback.toast({
        title: '授業演習コード確認',
        message: '出力対象の授業演習データがありません。',
        variant: 'warning'
      });
    }
    return;
  }

  const header = ['生徒ID', '学校', 'クラス', 'ファイル数', '最終更新', '研究同意'];
  const body = filteredStudents.map(function(student) {
    return [
      student.studentId,
      student.school,
      student.className,
      countFiles(student.tree),
      formatDateTime(student.updatedAt),
      student.consent
    ];
  });

  const csv = [header].concat(body)
    .map(function(cols) { return cols.map(escapeCSV).join(','); })
    .join('\r\n');

  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'exercise_list_' + new Date().toISOString().slice(0, 10) + '.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  showExerciseToast('CSVをダウンロードしました。', 'success');
}

async function bulkSaveExerciseFiles() {
  if (!filteredStudents.length) {
    showExerciseToast('ダウンロード対象の授業演習データがありません。', 'warning');
    return;
  }

  if (typeof JSZip === 'undefined') {
    showExerciseToast('ZIPライブラリの読み込みに失敗しました。', 'danger');
    return;
  }

  try {
    const zip = new JSZip();

    filteredStudents.forEach(function(student) {
      const files = collectExerciseFiles(student.tree);
      files.forEach(function(file) {
        const path = student.studentId + '/' + (file.path || file.name || 'untitled.py');
        zip.file(path, file.content || '');
      });
    });

    const zipBlob = await zip.generateAsync({ type: 'blob' });
    triggerDownload(zipBlob, 'exercise_files_' + new Date().toISOString().slice(0, 10) + '.zip', 'application/zip', true);
    showExerciseToast('授業演習ファイルを一括ダウンロードしました。', 'success');
  } catch (error) {
    showExerciseToast('一括ダウンロードに失敗しました。', 'danger');
  }
}

function updateSelectedFileDetail(file) {
  if (!file) {
    setText('detailFileName', '-');
    setText('detailFileUpdatedAt', '-');
    setText('detailFilePath', '-');
    if (codeViewer) {
      codeViewer.setValue('');
      codeViewer.refresh();
    }
    return;
  }

  setText('detailFileName', file.name);
  setText('detailFileUpdatedAt', formatDateTime(file.updatedAt));
  setText('detailFilePath', file.path || '-');

  if (codeViewer) {
    codeViewer.setValue(file.content || '');
    codeViewer.refresh();
  }
}

function saveCurrentExerciseFile() {
  const student = filteredStudents.find(function(item) {
    return item.studentId === currentStudentId;
  });

  if (!student) {
    showExerciseToast('ダウンロード対象の生徒データが見つかりません。', 'warning');
    return;
  }

  const file = findFileById(student.tree, currentFileId) || findFirstFile(student.tree);
  if (!file) {
    showExerciseToast('ダウンロード対象のファイルがありません。', 'warning');
    return;
  }

  const downloadName = student.studentId + '_' + (file.name || 'exercise.py');
  triggerDownload(file.content || '', downloadName, 'text/plain;charset=utf-8;');
  showExerciseToast('最新ファイルをダウンロードしました。', 'success');
}

async function downloadExerciseFolderZip() {
  if (typeof JSZip === 'undefined') {
    showExerciseToast('ZIPライブラリの読み込みに失敗しました。', 'danger');
    return;
  }

  const student = filteredStudents.find(function(item) {
    return item.studentId === currentStudentId;
  });

  if (!student) {
    showExerciseToast('ダウンロード対象の生徒データが見つかりません。', 'warning');
    return;
  }

  const files = collectExerciseFiles(student.tree);
  if (!files.length) {
    showExerciseToast('ダウンロード対象のファイルがありません。', 'warning');
    return;
  }

  try {
    const zip = new JSZip();
    files.forEach(function(file) {
      const path = student.studentId + '/' + (file.path || file.name || 'untitled.py');
      zip.file(path, file.content || '');
    });

    const zipBlob = await zip.generateAsync({ type: 'blob' });
    triggerDownload(zipBlob, student.studentId + '_exercise.zip', 'application/zip', true);
    showExerciseToast('フォルダをZIP形式でダウンロードしました。', 'success');
  } catch (error) {
    showExerciseToast('ZIPダウンロードに失敗しました。', 'danger');
  }
}

function collectExerciseFiles(node) {
  if (!node) return [];

  if (node.type === 'file') {
    return [node];
  }

  return (node.children || []).reduce(function(list, child) {
    return list.concat(collectExerciseFiles(child));
  }, []);
}

function triggerDownload(content, fileName, mimeType, isBlob = false) {
  const blob = isBlob ? content : new Blob([content], { type: mimeType });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(link.href);
}

function showExerciseToast(message, variant) {
  if (pageFeedback) {
    pageFeedback.toast({
      title: '授業演習コード確認',
      message,
      variant
    });
  }
}

function renderEmptyDetail() {
  setText('detailStudentId', '該当データなし');
  setText('detailMeta', 'フィルタ条件を見直してください。');
  setText('detailStudentMeta', '-');
  setText('detailFileCountChip', '-');
  setText('detailFileName', '-');
  setText('detailFileUpdatedAt', '-');
  setText('detailFilePath', '-');

  const treeEl = document.getElementById('exerciseTree');
  if (treeEl) {
    treeEl.innerHTML = '<div class="exercise-tree-empty">表示条件に一致する授業演習データがありません。</div>';
  }

  if (codeViewer) {
    codeViewer.setValue('');
    codeViewer.refresh();
  }

  if (exerciseDetailModal) {
    exerciseDetailModal.hide();
  }
}

function findFirstFile(node) {
  if (!node) return null;

  if (node.type === 'file') {
    return node;
  }

  for (const child of node.children || []) {
    const found = findFirstFile(child);
    if (found) return found;
  }

  return null;
}

function findFileById(node, fileId) {
  if (!node || !fileId) return null;

  if (node.type === 'file' && node.id === fileId) {
    return node;
  }

  for (const child of node.children || []) {
    const found = findFileById(child, fileId);
    if (found) return found;
  }

  return null;
}

function countFiles(node) {
  if (!node) return 0;

  if (node.type === 'file') {
    return 1;
  }

  return (node.children || []).reduce(function(total, child) {
    return total + countFiles(child);
  }, 0);
}

function formatDateTime(value, short = false) {
  if (!value) return '-';

  const date = new Date(value.replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  if (short) {
    return date.toLocaleString('ja-JP', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  return date.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

function consentClass(consent) {
  if (consent === '同意') return 'ok';
  if (consent === '不同意') return 'no';
  return 'pending';
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = value;
  }
}

function escapeCSV(value) {
  const text = String(value ?? '');
  if (text.includes(',') || text.includes('"') || text.includes('\n')) {
    return '"' + text.replace(/"/g, '""') + '"';
  }
  return text;
}
