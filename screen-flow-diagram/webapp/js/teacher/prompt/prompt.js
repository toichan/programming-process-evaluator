// Teacher prompt page interactions

const pageFeedback = window.PPEFeedback.createPageFeedback({ title: 'プロンプト修正' });

const fluctuationTemplateByTask = {
  'TASK-001': [
    {
      title: '評価観点の重み付けの揺らぎ',
      level: '中',
      description: '変数命名の良さと条件分岐の網羅性のどちらを優先するかで、評価段階が変わる可能性があります。',
      example: '例：変数名が「a, b, c」の場合、命名基準により★2〜★3で評価が分かれる。'
    },
    {
      title: '試行錯誤の解釈差',
      level: '高',
      description: 'エラー修正の繰り返しを改善行動とみなすか、設計不足とみなすかで評価が揺れる可能性があります。',
      example: '例：コンパイルエラー20回以上は「試行錯誤」と判定し−1点、または「学習過程」と判定し加点なし。'
    }
  ],
  'TASK-002': [
    {
      title: '例外入力の扱い基準',
      level: '高',
      description: '不正入力時の再入力処理を必須とするかで、評価判定が分かれる可能性があります。',
      example: '例：「−5」を入力した際、プログラムが停止した場合、★1か★2か。'
    },
    {
      title: '処理効率と可読性のバランス',
      level: '中',
      description: '短いコードを高評価とするか、説明的なコードを高評価とするかの解釈差が生じます。',
      example: '例：1行のコード vs 5行のコード、どちらを評価対象とするか。'
    }
  ],
  'TASK-003': [
    {
      title: '探索アルゴリズム選択の妥当性',
      level: '高',
      description: '最短経路の正しさを重視するか、計算量の最適化を重視するかで評価が揺れる可能性があります。',
      example: '例：線形探索（O(n)）で正解した場合、二分探索（O(log n)）との比較による評価差。'
    },
    {
      title: 'コメント記述量の評価',
      level: '低',
      description: 'コメントの多さを可読性向上とみなすか、冗長とみなすかで評価差が出る可能性があります。',
      example: '例：コメント0行（コード実行のみ）vs コメント10行（コードと同等）の比較。'
    }
  ]
};

const PROMPT_VERSION_STORAGE_KEY = 'teacherPromptVersionStore';
const STEP2_VERSION_STORAGE_KEY = 'teacherPromptStep2VersionStore';
const EVALUATION_EXAMPLES_STORAGE_KEY = 'teacherEvaluationExamplesStore';
const STEP3_VERSION_STORAGE_KEY = 'teacherPromptStep3VersionStore';
let commonPromptEditor = null;

const COMMON_PROMPT_TEMPLATE = `# Persona

- あなたは大学の教授です。
- 大学1年生に向けて開設されているプログラミングⅠの授業で、学生の課題の評価を行なっています。

# Task

- 添付されたルーブリックを基準に、ソースコードのログ、コンパイルエラー、実行入出力を評価してください。
- ログの中にコピペを検出した際には、「コピペの検出とその詳細」に疑われる部分を記載してください。ない場合は「なし」と記載してください。
- 評価は各観点を数値で出すとともに、その理由を記述してください。

# Context

- ルーブリックは「6.2_ルーブリック完成.md」を使用してください。
- ソースコードのログは「sorce_codes_task(task_id)_(student_id).json」です。
- コンパイルエラーは「compiles_task(task_id)_(student_id).json」です。
- 実行入出力（実行時に入力したものと出力されたもの）は「cexcutions_task(task_id)_(student_id).json」です。
- 課題内容については、「task(task_id)_content.md」を参照してください。
- 公平かつ客観的で再現性のある評価を行なってください。
- 文法エラーと論理エラーの定義は次のとおりです。
  - 文法エラー：コンパイルをした際にエラーとして処理されるもの。
  - 論理エラー：コンパイルではエラーとして処理されないが、課題に対して期待する出力とは異なっているもの。
- 書き途中であると推測されるものについては、文法エラーに含まないでください。
- 評価の理由は具体的に評価部分を示しながら記述してください。
- 評価の理由について、曖昧な表現は避けてください。`;

document.addEventListener('DOMContentLoaded', function() {
  initializePromptPage();
});

function initializePromptPage() {
  initializeCommonPromptEditor();
  ensureCommonPromptTemplate();

  const form = document.getElementById('commonPromptForm');
  const generateButton = document.getElementById('generateFluctuationButton');
  const savePromptButton = document.getElementById('savePromptButton');
  const runReevaluationButton = document.getElementById('runReevaluationButton');
  const resetStep2Button = document.getElementById('resetStep2Button');
  const confirmReevaluationStep3Button = document.getElementById('confirmReevaluationStep3Button');
  const saveEvaluationExamplesButton = document.getElementById('saveEvaluationExamplesButton');
  const executeReevaluationFromModalButton = document.getElementById('executeReevaluationFromModalButton');
  const resetButton = document.getElementById('resetPromptFormButton');
  const refreshButton = document.getElementById('refreshHistoryButton');
  const taskSelect = document.getElementById('taskSelect');
  const versionSelect = document.getElementById('versionSelect');
  const versionSelectStep2 = document.getElementById('versionSelectStep2');

  if (taskSelect) {
    taskSelect.addEventListener('change', onTaskChanged);
  }

  if (versionSelect) {
    versionSelect.addEventListener('change', onVersionChanged);
  }

  if (versionSelectStep2) {
    versionSelectStep2.addEventListener('change', onStep2VersionChanged);
  }

  if (form) {
    form.addEventListener('submit', function(event) {
      event.preventDefault();
      const taskId = getTaskId();
      if (!taskId) {
        pageFeedback.toast({ message: '課題を選択してください。', variant: 'warning', delay: 1800 });
        return;
      }

      const saved = saveNewVersion(taskId);
      pageFeedback.toast({ message: '共通プロンプトを保存しました。', variant: 'success', delay: 1800 });
      updateLastUpdated();
      appendHistoryRow({
        taskId: taskId,
        step1Version: saved.version,
        step2Version: '-',
        step3Version: '-',
        reevaluation: '未実施'
      });
    });
  }

  if (generateButton) {
    generateButton.addEventListener('click', generateFluctuationItems);
  }

  if (savePromptButton) {
    savePromptButton.addEventListener('click', function() {
      if (!hasGeneratedFluctuations()) {
        pageFeedback.toast({ message: '先に揺らぎ項目を生成してください。', variant: 'warning', delay: 1800 });
        return;
      }

      runReevaluation();
    });
  }

  if (runReevaluationButton) {
    runReevaluationButton.addEventListener('click', function() {
      if (!hasGeneratedFluctuations()) {
        pageFeedback.toast({ message: '先に揺らぎ項目を生成してください。', variant: 'warning', delay: 1800 });
        return;
      }

      const taskId = getTaskId();
      if (!taskId) {
        pageFeedback.toast({ message: '課題を選択してください。', variant: 'warning', delay: 1800 });
        return;
      }

      const saved = saveStep2Version(taskId);
      pageFeedback.toast({ message: '課題プロンプトを保存しました。', variant: 'success', delay: 1800 });
      updateLastUpdated();
      appendHistoryRow({
        taskId: taskId,
        step1Version: getLatestVersionNumber(taskId),
        step2Version: saved.version,
        step3Version: '-',
        reevaluation: '未実施'
      });
    });
  }

  if (resetStep2Button) {
    resetStep2Button.addEventListener('click', resetStep2Inputs);
  }

  if (confirmReevaluationStep3Button) {
    confirmReevaluationStep3Button.addEventListener('click', function() {
      if (!hasGeneratedFluctuations()) {
        pageFeedback.toast({ message: '先にSTEP2を作成してください。', variant: 'warning', delay: 1800 });
        return;
      }
      openReevaluationPreviewModal();
    });
  }

  if (executeReevaluationFromModalButton) {
    executeReevaluationFromModalButton.addEventListener('click', function() {
      const modalEl = document.getElementById('reevaluationPreviewModal');
      if (modalEl) {
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.hide();
      }
      runReevaluation();
    });
  }

  if (saveEvaluationExamplesButton) {
    saveEvaluationExamplesButton.addEventListener('click', saveStep3Examples);
  }

  if (resetButton) {
    resetButton.addEventListener('click', resetPromptForm);
  }

  if (refreshButton) {
    refreshButton.addEventListener('click', function() {
      pageFeedback.toast({ message: '履歴を更新しました。', variant: 'success', delay: 1800 });
      updateLastUpdated();
    });
  }

  applyInitialTaskSelection();
  renderVersionOptions(getTaskId());
  renderStep2VersionOptions(getTaskId());
  renderEvaluationExamples(getTaskId());
}

function onTaskChanged() {
  const taskId = getTaskId();
  syncTaskSelectionInUrl(taskId);
  renderVersionOptions(taskId);

  if (!taskId) {
    setValue('evaluationPromptInput', COMMON_PROMPT_TEMPLATE);
    renderEvaluationExamples('');
    return;
  }

  setValue('evaluationPromptInput', COMMON_PROMPT_TEMPLATE);

  if (!hasAnyVersion(taskId)) {
    applyCurrentFormToDraft(taskId);
  } else {
    const latest = getLatestVersionRecord(taskId);
    if (latest) {
      applyVersionRecord(latest);
    }
  }

  renderEvaluationExamples(taskId);
}

function applyInitialTaskSelection() {
  const taskSelect = document.getElementById('taskSelect');
  if (!taskSelect) return;

  const params = new URLSearchParams(window.location.search);
  const taskId = params.get('taskId') || '';
  if (!taskId) return;

  const hasOption = Array.from(taskSelect.options).some(function(option) {
    return option.value === taskId;
  });
  if (!hasOption) return;

  taskSelect.value = taskId;
  onTaskChanged();
}

function syncTaskSelectionInUrl(taskId) {
  if (!window.history || !window.history.replaceState) return;

  const url = new URL(window.location.href);
  if (taskId) {
    url.searchParams.set('taskId', taskId);
  } else {
    url.searchParams.delete('taskId');
  }

  window.history.replaceState({}, '', url.toString());
}

function onVersionChanged() {
  const taskId = getTaskId();
  const versionSelect = document.getElementById('versionSelect');
  if (!taskId || !versionSelect || !versionSelect.value) return;

  const versionNumber = Number(versionSelect.value);
  const record = getVersionRecord(taskId, versionNumber);
  if (!record) return;

  applyVersionRecord(record);
  pageFeedback.toast({ message: 'ver.' + versionNumber + ' のプロンプトを読み込みました。', variant: 'success', delay: 1800 });
  renderStep2VersionOptions(taskId);
}

function onStep2VersionChanged() {
  const taskId = getTaskId();
  const versionSelect = document.getElementById('versionSelectStep2');
  if (!taskId || !versionSelect || !versionSelect.value) return;

  const versionNumber = Number(versionSelect.value);
  const record = getStep2VersionRecord(taskId, versionNumber);
  if (!record) return;

  applyStep2VersionRecord(record);
  pageFeedback.toast({ message: 'ver.' + versionNumber + ' のSTEP2設定を読み込みました。', variant: 'success', delay: 1800 });
}

function generateFluctuationItems() {
  const taskId = getTaskId();
  if (!taskId) {
    pageFeedback.toast({ message: '課題を選択してください。', variant: 'warning', delay: 1800 });
    return;
  }

  const list = fluctuationTemplateByTask[taskId] || [];
  const fluctuationList = document.getElementById('fluctuationList');
  const fluctuationEmpty = document.getElementById('fluctuationEmpty');
  const savePromptButton = document.getElementById('savePromptButton');
  const runReevaluationButton = document.getElementById('runReevaluationButton');
  const resetStep2Button = document.getElementById('resetStep2Button');
  const fluctuationStatus = document.getElementById('fluctuationStatus');

  if (!fluctuationList || !fluctuationEmpty || !fluctuationStatus) return;

  fluctuationList.innerHTML = list.map(function(item, index) {
    const exampleHtml = item.example
      ? '<p class="fluctuation-example"><strong>例：</strong>' + escapeHtml(item.example.replace(/^例：/, '')) + '</p>'
      : '';
    return '' +
      '<article class="fluctuation-item">' +
        '<div class="fluctuation-head">' +
          '<h3 class="fluctuation-title">' + (index + 1) + '. ' + escapeHtml(item.title) + '</h3>' +
          '<span class="fluctuation-meta">揺らぎ: ' + escapeHtml(item.level) + '</span>' +
        '</div>' +
        '<p class="fluctuation-description">' + escapeHtml(item.description) + '</p>' +
        exampleHtml +
        '<label class="form-label">評価方針の詳細化</label>' +
        '<textarea class="form-control fluctuation-response" rows="3" placeholder="この揺らぎ項目をどのように評価するか記述"></textarea>' +
      '</article>';
  }).join('');

  fluctuationEmpty.classList.add('d-none');
  fluctuationList.classList.remove('d-none');
  fluctuationStatus.textContent = '生成済み';
  fluctuationStatus.className = 'badge text-bg-success';

  if (savePromptButton) savePromptButton.disabled = false;
  if (runReevaluationButton) runReevaluationButton.disabled = false;
  if (resetStep2Button) resetStep2Button.disabled = false;

  pageFeedback.toast({ message: '揺らぎ項目を生成しました。', variant: 'success', delay: 1800 });
}

function runReevaluation() {
  if (!hasGeneratedFluctuations()) {
    pageFeedback.toast({ message: '先に揺らぎ項目を生成してください。', variant: 'warning', delay: 1800 });
    return;
  }

  const wrap = document.getElementById('reevaluationProgressWrap');
  const bar = document.getElementById('reevaluationProgressBar');
  const runButton = document.getElementById('runReevaluationButton');
  const pendingCountEl = document.getElementById('pendingCount');

  if (!wrap || !bar) return;

  wrap.classList.remove('d-none');
  if (runButton) runButton.disabled = true;

  let progress = 0;
  bar.style.width = '0%';
  bar.textContent = '0%';

  const timer = window.setInterval(function() {
    progress += 10;
    bar.style.width = progress + '%';
    bar.textContent = progress + '%';

    if (progress >= 100) {
      window.clearInterval(timer);
      if (runButton) runButton.disabled = false;

      if (pendingCountEl) {
        const current = Number(pendingCountEl.textContent || '0');
        pendingCountEl.textContent = String(Math.max(0, current - 1));
      }

      appendHistoryRow({
        taskId: getTaskId(),
        step1Version: getLatestVersionNumber(getTaskId()),
        step2Version: getLatestStep2VersionNumber(getTaskId()),
        step3Version: incrementStep3Version(getTaskId()),
        reevaluation: '実施済み'
      });
      updateLastUpdated();

      const taskId = getTaskId();
      if (taskId) {
        const sampleExamples = [
          {
            id: 1,
            scores: { '思判表': 4, '態度': 3 },
            scoreReasons: [
              {
                title: '変数命名と構造化',
                score: 4,
                body: '変数命名が適切で、処理の流れが明確です。条件分岐の処理も正確に実装されています。',
                evidence: ['変数名が分かりやすく命名されている', '条件分岐が適切に実装されている']
              },
              {
                title: 'コード品質と可読性',
                score: 4,
                body: 'インデントが統一され、全体的に読みやすいコードになっています。コメントの追加でさらに改善できます。',
                evidence: ['インデントが統一されている', 'コード全体が構造化されている']
              },
              {
                title: '課題への粘り強さ',
                score: 3,
                body: 'エラーを複数回修正しながら最終的に目標を達成しています。',
                evidence: ['コンパイルエラーを5回以上修正している']
              }
            ]
          },
          {
            id: 2,
            scores: { '思判表': 3, '態度': 4 },
            scoreReasons: [
              {
                title: '基本的なプログラミング理解',
                score: 3,
                body: '基本的な処理は理解できていますが、高度な最適化には至っていません。',
                evidence: ['基本的な制御構造を理解している', 'エラーハンドリングは未実装']
              },
              {
                title: 'デバッグ能力',
                score: 3,
                body: 'エラーの原因を段階的に特定できていますが、修正に時間がかかっています。',
                evidence: ['エラーメッセージから原因を推測している']
              },
              {
                title: '学習への意欲',
                score: 4,
                body: '何度も試行錯誤を繰り返し、問題解決に向けて粘り強く取り組んでいます。',
                evidence: ['保存回数が多い（8回）', '実行回数が多い（6回）']
              }
            ]
          }
        ];
        saveEvaluationExamples(taskId, sampleExamples);
        renderEvaluationExamples(taskId);
      }

      pageFeedback.toast({ message: '評価例の生成が完了しました。', variant: 'success', delay: 1800 });
    }
  }, 180);
}

async function resetPromptForm() {
  const ok = await pageFeedback.confirm({
    title: '入力内容をリセットしますか？',
    message: '次の入力内容をリセットします。',
    detailTitle: '',
    details: [
      'Step 1 から Step 3 の未保存の入力内容',
      '追加評価指示や生成前の編集内容'
    ],
    confirmLabel: 'リセットする',
    cancelLabel: '戻る',
    variant: 'warning'
  });
  if (!ok) return;

  const form = document.getElementById('commonPromptForm');
  const fluctuationList = document.getElementById('fluctuationList');
  const fluctuationEmpty = document.getElementById('fluctuationEmpty');
  const additionalInstructionInput = document.getElementById('additionalInstructionInput');
  const savePromptButton = document.getElementById('savePromptButton');
  const runReevaluationButton = document.getElementById('runReevaluationButton');
  const resetStep2Button = document.getElementById('resetStep2Button');
  const fluctuationStatus = document.getElementById('fluctuationStatus');
  const wrap = document.getElementById('reevaluationProgressWrap');

  if (form) form.reset();
  setValue('evaluationPromptInput', COMMON_PROMPT_TEMPLATE);
  if (additionalInstructionInput) additionalInstructionInput.value = '';

  renderVersionOptions(getTaskId());
  renderStep2VersionOptions(getTaskId());

  if (fluctuationList) {
    fluctuationList.innerHTML = '';
    fluctuationList.classList.add('d-none');
  }
  if (fluctuationEmpty) fluctuationEmpty.classList.remove('d-none');

  if (savePromptButton) savePromptButton.disabled = true;
  if (runReevaluationButton) runReevaluationButton.disabled = true;
  if (resetStep2Button) resetStep2Button.disabled = true;

  if (fluctuationStatus) {
    fluctuationStatus.textContent = '未生成';
    fluctuationStatus.className = 'badge text-bg-light border';
  }

  if (wrap) wrap.classList.add('d-none');

  pageFeedback.toast({ message: '入力内容をリセットしました。', variant: 'success', delay: 1800 });
}

async function resetStep2Inputs() {
  const hasStep2 = hasGeneratedFluctuations();
  if (!hasStep2) {
    pageFeedback.toast({ message: '先に揺らぎ項目を生成してください。', variant: 'warning', delay: 1800 });
    return;
  }

  const ok = await pageFeedback.confirm({
    title: 'STEP2 の入力内容をリセットしますか？',
    message: '次の入力内容をリセットします。',
    detailTitle: '',
    details: [
      '追加評価指示',
      '揺らぎ項目への回答内容'
    ],
    confirmLabel: 'リセットする',
    cancelLabel: '戻る',
    variant: 'warning'
  });
  if (!ok) return;

  const additionalInstructionInput = document.getElementById('additionalInstructionInput');
  const responseFields = Array.from(document.querySelectorAll('.fluctuation-response'));

  if (additionalInstructionInput) additionalInstructionInput.value = '';
  responseFields.forEach(function(field) {
    field.value = '';
  });

  pageFeedback.toast({ message: 'STEP2の入力内容をリセットしました。', variant: 'success', delay: 1800 });
}

function renderVersionOptions(taskId) {
  const versionSelect = document.getElementById('versionSelect');
  if (!versionSelect) return;

  if (!taskId) {
    versionSelect.innerHTML = '<option value="">課題を選択してください</option>';
    versionSelect.disabled = true;
    return;
  }

  const versions = getVersionList(taskId);
  if (versions.length === 0) {
    versionSelect.innerHTML = '<option value="">未保存</option>';
    versionSelect.disabled = true;
    return;
  }

  versionSelect.innerHTML = versions.map(function(record) {
    return '<option value="' + record.version + '">ver.' + record.version + '</option>';
  }).join('');

  const latest = versions[versions.length - 1];
  versionSelect.value = String(latest.version);
  versionSelect.disabled = false;
}

function saveNewVersion(taskId) {
  const store = loadVersionStore();
  const list = store[taskId] || [];
  const nextVersion = list.length === 0 ? 1 : list[list.length - 1].version + 1;

  const record = {
    version: nextVersion,
    model: getValue('modelSelect'),
    prompt: getValue('evaluationPromptInput'),
    additionalInstruction: getValue('additionalInstructionInput'),
    savedAt: nowAsDisplayDate()
  };

  list.push(record);
  store[taskId] = list;
  saveVersionStore(store);

  renderVersionOptions(taskId);
  const versionSelect = document.getElementById('versionSelect');
  if (versionSelect) versionSelect.value = String(nextVersion);

  return record;
}

function applyVersionRecord(record) {
  setValue('modelSelect', record.model || '');
  setValue('evaluationPromptInput', record.prompt || '');
  setValue('additionalInstructionInput', record.additionalInstruction || '');
}

function applyCurrentFormToDraft(taskId) {
  if (!taskId) return;
  const existing = getLatestVersionRecord(taskId);
  if (existing) return;
}

function hasAnyVersion(taskId) {
  return getVersionList(taskId).length > 0;
}

function getVersionList(taskId) {
  const store = loadVersionStore();
  const list = store[taskId] || [];
  return list.slice().sort(function(a, b) { return a.version - b.version; });
}

function getLatestVersionRecord(taskId) {
  const versions = getVersionList(taskId);
  return versions.length ? versions[versions.length - 1] : null;
}

function getVersionRecord(taskId, versionNumber) {
  const versions = getVersionList(taskId);
  return versions.find(function(record) { return record.version === versionNumber; }) || null;
}

function renderStep2VersionOptions(taskId) {
  const versionSelect = document.getElementById('versionSelectStep2');
  if (!versionSelect) return;

  if (!taskId) {
    versionSelect.innerHTML = '<option value="">課題を選択してください</option>';
    versionSelect.disabled = true;
    return;
  }

  const versions = getStep2VersionList(taskId);
  if (versions.length === 0) {
    versionSelect.innerHTML = '<option value="">未保存</option>';
    versionSelect.disabled = true;
    return;
  }

  versionSelect.innerHTML = versions.map(function(record) {
    return '<option value="' + record.version + '">ver.' + record.version + '</option>';
  }).join('');

  const latest = versions[versions.length - 1];
  versionSelect.value = String(latest.version);
  versionSelect.disabled = false;
}

function saveStep2Version(taskId) {
  const store = loadStep2VersionStore();
  const list = store[taskId] || [];
  const nextVersion = list.length === 0 ? 1 : list[list.length - 1].version + 1;

  const responses = Array.from(document.querySelectorAll('.fluctuation-response')).map(function(el) {
    return el.value || '';
  });

  const record = {
    version: nextVersion,
    additionalInstruction: getValue('additionalInstructionInput'),
    responses: responses,
    savedAt: nowAsDisplayDate()
  };

  list.push(record);
  store[taskId] = list;
  saveStep2VersionStore(store);

  renderStep2VersionOptions(taskId);
  const versionSelect = document.getElementById('versionSelectStep2');
  if (versionSelect) versionSelect.value = String(nextVersion);

  return record;
}

function applyStep2VersionRecord(record) {
  setValue('additionalInstructionInput', record.additionalInstruction || '');

  if (!Array.isArray(record.responses) || record.responses.length === 0) {
    return;
  }

  const taskId = getTaskId();
  if (!taskId) return;

  generateFluctuationItems();
  const fields = Array.from(document.querySelectorAll('.fluctuation-response'));
  fields.forEach(function(field, idx) {
    field.value = record.responses[idx] || '';
  });
}

function hasAnyStep2Version(taskId) {
  return getStep2VersionList(taskId).length > 0;
}

function getStep2VersionList(taskId) {
  const store = loadStep2VersionStore();
  const list = store[taskId] || [];
  return list.slice().sort(function(a, b) { return a.version - b.version; });
}

function getLatestStep2VersionRecord(taskId) {
  const versions = getStep2VersionList(taskId);
  return versions.length ? versions[versions.length - 1] : null;
}

function getStep2VersionRecord(taskId, versionNumber) {
  const versions = getStep2VersionList(taskId);
  return versions.find(function(record) { return record.version === versionNumber; }) || null;
}

function loadStep2VersionStore() {
  try {
    const raw = window.localStorage.getItem(STEP2_VERSION_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (error) {
    return {};
  }
}

function saveStep2VersionStore(store) {
  window.localStorage.setItem(STEP2_VERSION_STORAGE_KEY, JSON.stringify(store));
}

function loadStep3VersionStore() {
  try {
    const raw = window.localStorage.getItem(STEP3_VERSION_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (error) {
    return {};
  }
}

function saveStep3VersionStore(store) {
  window.localStorage.setItem(STEP3_VERSION_STORAGE_KEY, JSON.stringify(store));
}

function loadVersionStore() {
  try {
    const raw = window.localStorage.getItem(PROMPT_VERSION_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (error) {
    return {};
  }
}

function saveVersionStore(store) {
  window.localStorage.setItem(PROMPT_VERSION_STORAGE_KEY, JSON.stringify(store));
}

function initializeCommonPromptEditor() {
  const textarea = document.getElementById('evaluationPromptInput');
  if (!textarea) return;
  if (typeof CodeMirror === 'undefined') return;

  commonPromptEditor = CodeMirror.fromTextArea(textarea, {
    mode: 'markdown',
    lineNumbers: true,
    lineWrapping: true,
    theme: 'material-darker'
  });
}

function ensureCommonPromptTemplate() {
  const current = getValue('evaluationPromptInput');
  if (current && current.trim().length > 0) return;
  setValue('evaluationPromptInput', COMMON_PROMPT_TEMPLATE);
}

function getValue(id) {
  if (id === 'evaluationPromptInput' && commonPromptEditor) {
    return commonPromptEditor.getValue();
  }
  const el = document.getElementById(id);
  return el ? el.value : '';
}



function setValue(id, value) {
  if (id === 'evaluationPromptInput' && commonPromptEditor) {
    commonPromptEditor.setValue(value || '');
    commonPromptEditor.refresh();
    return;
  }
  const el = document.getElementById(id);
  if (!el) return;
  el.value = value;
}

function appendHistoryRow(entry) {
  const table = document.querySelector('#historyTable tbody');
  if (!table) return;

  const date = nowAsDisplayDate();
  const reevaluationBadge = entry.reevaluation === '実施済み'
    ? '<span class="badge text-bg-success">実施済み</span>'
    : '<span class="badge text-bg-secondary">未実施</span>';

  const row = document.createElement('tr');
  row.innerHTML = '' +
    '<td>' + escapeHtml(date) + '</td>' +
    '<td>' + escapeHtml(String(toTaskIdNumber(entry.taskId))) + '</td>' +
    '<td>t001</td>' +
    '<td>' + escapeHtml(String(entry.step1Version || '-')) + '</td>' +
    '<td>' + escapeHtml(String(entry.step2Version || '-')) + '</td>' +
    '<td>' + escapeHtml(String(entry.step3Version || '-')) + '</td>' +
    '<td>' + reevaluationBadge + '</td>';

  table.prepend(row);
}

function updateLastUpdated() {
  const el = document.getElementById('lastUpdatedAt');
  if (!el) return;
  el.textContent = nowAsDisplayDate().slice(0, 10);
}

function hasGeneratedFluctuations() {
  const list = document.getElementById('fluctuationList');
  return Boolean(list && !list.classList.contains('d-none') && list.children.length > 0);
}

function getTaskId() {
  const select = document.getElementById('taskSelect');
  return select ? select.value : '';
}

function toTaskIdNumber(taskId) {
  const match = String(taskId || '').match(/\d+/);
  if (!match) return '-';
  return Number(match[0]);
}

function getLatestVersionNumber(taskId) {
  const latest = getLatestVersionRecord(taskId);
  return latest ? latest.version : '-';
}

function getLatestStep2VersionNumber(taskId) {
  const latest = getLatestStep2VersionRecord(taskId);
  return latest ? latest.version : '-';
}

function incrementStep3Version(taskId) {
  if (!taskId) return '-';
  const store = loadStep3VersionStore();
  const current = Number(store[taskId] || 0);
  const next = current + 1;
  store[taskId] = next;
  saveStep3VersionStore(store);
  return next;
}

function nowAsDisplayDate() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  const hh = String(now.getHours()).padStart(2, '0');
  const mm = String(now.getMinutes()).padStart(2, '0');
  const ss = String(now.getSeconds()).padStart(2, '0');
  return y + '-' + m + '-' + d + ' ' + hh + ':' + mm + ':' + ss;
}

function renderEvaluationExamples(taskId) {
  const list = document.getElementById('evaluationExamplesList');
  const empty = document.getElementById('evaluationExamplesEmpty');
  const status = document.getElementById('evaluationExamplesStatus');

  if (!list || !empty || !status) return;

  if (!taskId) {
    list.innerHTML = '';
    list.classList.add('d-none');
    empty.classList.remove('d-none');
    status.textContent = '未生成';
    status.className = 'badge text-bg-light border';
    setStep3ActionButtonsEnabled(false);
    return;
  }

  const examples = getEvaluationExamplesForTask(taskId);
  if (!examples || examples.length === 0) {
    list.innerHTML = '';
    list.classList.add('d-none');
    empty.classList.remove('d-none');
    status.textContent = '未生成';
    status.className = 'badge text-bg-light border';
    setStep3ActionButtonsEnabled(false);
    return;
  }

  list.innerHTML = examples.map(function(example, index) {
    const scoresHtml = example.scores ? Object.entries(example.scores).map(function(entry) {
      return '<div class="score-item">' +
        '<span class="score-label">' + escapeHtml(entry[0]) + ':</span>' +
        '<span class="score-value">' + escapeHtml(String(entry[1])) + '/5</span>' +
        '</div>';
    }).join('') : '';

    return '<div class="evaluation-example-item">' +
      '<button class="btn btn-sm btn-outline-primary example-button" type="button" data-example-id="' + index + '">' +
        '評価例' + (index + 1) +
      '</button>' +
      '<div class="example-scores">' +
        scoresHtml +
      '</div>' +
    '</div>';
  }).join('');

  empty.classList.add('d-none');
  list.classList.remove('d-none');
  status.textContent = '生成済み';
  status.className = 'badge text-bg-success';
  setStep3ActionButtonsEnabled(true);

  // ボタンにクリックイベントを追加
  const buttons = list.querySelectorAll('.example-button');
  buttons.forEach(function(button) {
    button.addEventListener('click', function() {
      const exampleId = Number(this.dataset.exampleId);
      openEvaluationExampleDetail(taskId, exampleId);
    });
  });
}

function setStep3ActionButtonsEnabled(enabled) {
  const confirmButton = document.getElementById('confirmReevaluationStep3Button');
  const saveButton = document.getElementById('saveEvaluationExamplesButton');
  if (confirmButton) confirmButton.disabled = !enabled;
  if (saveButton) saveButton.disabled = !enabled;
}

function getEvaluationExamplesForTask(taskId) {
  try {
    const store = window.localStorage.getItem(EVALUATION_EXAMPLES_STORAGE_KEY);
    if (!store) return [];
    const parsed = JSON.parse(store);
    return parsed && Array.isArray(parsed[taskId]) ? parsed[taskId] : [];
  } catch (error) {
    return [];
  }
}

function saveEvaluationExamples(taskId, examples) {
  try {
    let store = {};
    const raw = window.localStorage.getItem(EVALUATION_EXAMPLES_STORAGE_KEY);
    if (raw) {
      store = JSON.parse(raw);
    }
    store[taskId] = Array.isArray(examples) ? examples : [];
    window.localStorage.setItem(EVALUATION_EXAMPLES_STORAGE_KEY, JSON.stringify(store));
  } catch (error) {
    console.error('評価例の保存に失敗しました:', error);
  }
}

function saveStep3Examples() {
  const taskId = getTaskId();
  if (!taskId) {
    pageFeedback.toast({ message: '課題を選択してください。', variant: 'warning', delay: 1800 });
    return;
  }

  const examples = getEvaluationExamplesForTask(taskId);
  if (!examples || examples.length === 0) {
    pageFeedback.toast({ message: '保存する評価例がありません。', variant: 'warning', delay: 1800 });
    return;
  }

  saveEvaluationExamples(taskId, examples);
  appendHistoryRow({
    taskId: taskId,
    step1Version: getLatestVersionNumber(taskId),
    step2Version: getLatestStep2VersionNumber(taskId),
    step3Version: incrementStep3Version(taskId),
    reevaluation: '未実施'
  });
  updateLastUpdated();
  pageFeedback.toast({ message: '評価例を保存しました。', variant: 'success', delay: 1800 });
}

function openReevaluationPreviewModal() {
  const taskId = getTaskId();
  if (!taskId) {
    pageFeedback.toast({ message: '課題を選択してください。', variant: 'warning', delay: 1800 });
    return;
  }

  const modalEl = document.getElementById('reevaluationPreviewModal');
  const taskLabel = document.getElementById('reevaluationPreviewTaskLabel');
  const tbody = document.getElementById('reevaluationPreviewTableBody');
  if (!modalEl || !tbody) return;

  const rows = buildReevaluationPreviewRows(taskId);
  if (taskLabel) {
    taskLabel.textContent = '(課題ID: ' + toTaskIdNumber(taskId) + ')';
  }

  if (rows.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">対象の生徒がいません。</td></tr>';
  } else {
    tbody.innerHTML = rows.map(function(row) {
      const statusBadge = row.status === '評価済み'
        ? '<span class="badge text-bg-success">評価済み</span>'
        : '<span class="badge text-bg-secondary">未評価</span>';
      const diffClass = row.diff > 0 ? 'text-success fw-semibold' : (row.diff < 0 ? 'text-danger fw-semibold' : 'text-body');
      const diffLabel = row.diff > 0 ? '+' + row.diff : String(row.diff);
      return '' +
        '<tr>' +
          '<td>' + escapeHtml(row.studentId) + '</td>' +
          '<td>' + escapeHtml(String(row.newScore)) + '</td>' +
          '<td class="' + diffClass + '">' + escapeHtml(diffLabel) + '</td>' +
          '<td>' + statusBadge + '</td>' +
        '</tr>';
    }).join('');
  }

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function buildReevaluationPreviewRows(taskId) {
  const examples = getEvaluationExamplesForTask(taskId);
  const baseNewScore = getBaseNewScore(examples);
  const students = getSubmittedStudentEntries(taskId);

  return students.map(function(student, index) {
    const previousScore = student.status === '評価済み' ? student.previousScore : null;
    const adjustment = index % 3 === 0 ? 1 : (index % 3 === 1 ? 0 : -1);
    const newScore = clampScore(baseNewScore + adjustment);
    const diff = previousScore === null ? newScore : newScore - previousScore;

    return {
      studentId: student.studentId,
      newScore: newScore,
      diff: diff,
      status: student.status
    };
  });
}

function getBaseNewScore(examples) {
  if (!Array.isArray(examples) || examples.length === 0) return 3;

  const total = examples.reduce(function(sum, example) {
    if (!example || !example.scores) return sum + 3;
    const values = Object.values(example.scores).map(function(value) { return Number(value) || 0; });
    if (values.length === 0) return sum + 3;
    const average = values.reduce(function(vSum, value) { return vSum + value; }, 0) / values.length;
    return sum + average;
  }, 0);

  return clampScore(Math.round(total / examples.length));
}

function getSubmittedStudentEntries(taskId) {
  const taskNumber = toTaskIdNumber(taskId);
  const map = {
    1: [
      { studentId: '1001', status: '評価済み', previousScore: 3 },
      { studentId: '1002', status: '評価済み', previousScore: 4 },
      { studentId: '1003', status: '未評価', previousScore: null },
      { studentId: '1004', status: '評価済み', previousScore: 2 },
      { studentId: '1005', status: '未評価', previousScore: null }
    ],
    2: [
      { studentId: '2001', status: '評価済み', previousScore: 3 },
      { studentId: '2002', status: '未評価', previousScore: null },
      { studentId: '2003', status: '評価済み', previousScore: 4 }
    ],
    3: [
      { studentId: '3001', status: '評価済み', previousScore: 2 },
      { studentId: '3002', status: '未評価', previousScore: null },
      { studentId: '3003', status: '評価済み', previousScore: 3 }
    ]
  };
  return map[taskNumber] || [];
}

function clampScore(value) {
  return Math.max(1, Math.min(5, Number(value) || 3));
}

function openEvaluationExampleDetail(taskId, exampleId) {
  const examples = getEvaluationExamplesForTask(taskId);
  if (!examples || !examples[exampleId]) {
    pageFeedback.toast({ message: '評価例が見つかりません。', variant: 'warning', delay: 1800 });
    return;
  }

  const example = examples[exampleId];
  const container = document.getElementById('evaluationExampleDetailContent');
  if (!container) return;

  const data = {
    studentId: 's001',
    school: '国際中等',
    class: '1年A組',
    task: taskId,
    difficulty: '初級',
    evaluatedAt: '2026-05-19 10:35:12',
    thinking: example.scores ? example.scores['思考力・判断力・表現力'] || 3 : 3,
    attitude: example.scores ? example.scores['主体的に学習に取り組む態度'] || 4 : 4,
    overall: 3.5,
    consent: '同意'
  };

  const studentCssPath = '../../../css/student/evaluation/evaluation.css';
  const studentLogCssPath = '../../../css/student/evaluation/log.css';
  
  const logEntries = [
    {
      id: 931327,
      createdAt: '2022-04-21 12:56:54',
      content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n}'
    },
    {
      id: 931335,
      createdAt: '2022-04-21 12:57:57',
      content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n  scanf("%d%d%d",&a,&b,&c);\n  if(a>=b && a>=c){\n    printf("最大値は%d\\n",a);\n  }\n}'
    },
    {
      id: 931348,
      createdAt: '2022-04-21 12:59:28',
      content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n  scanf("%d%d%d",&a,&b,&c);\n  if(a>=b && a>=c){\n    printf("最大値は%d\\n",a);\n    if(b>=c){\n      printf("最小値は%d\\n",c);\n    }else{\n      printf("最小値は%d\\n",b);\n    }\n  }\n}'
    },
    {
      id: 931372,
      createdAt: '2022-04-21 13:01:28',
      content: '#include<stdio.h>\nint main(){\n  int a,b,c;\n  scanf("%d%d%d",&a,&b,&c);\n  if(a>=b && a>=c){\n    printf("最大値は%d\\n",a);\n  }else if(b>=c && b>=a){\n    printf("最大値は%d\\n",b);\n  }else{\n    printf("最大値は%d\\n",c);\n  }\n}'
    }
  ];

  const renderLogView = function() {
    const reasonData = {
      reasonFilters: [
        { key: 'all', label: 'すべて' },
        { key: 'thinking', label: '思考・判断・表現' },
        { key: 'attitude', label: '態度' }
      ],
      reasons: example.scoreReasons || []
    };

    const logs = logEntries.map(function(entry, index) {
      return {
        id: entry.id,
        student_id: data.studentId,
        task_id: 459,
        content: entry.content,
        submitted_at: index === logEntries.length - 1 ? entry.createdAt : null,
        remaining_work: Math.max(0, 450 - index * 80),
        remaining_proportion: index === 0 ? -38 : -20 + index,
        auto_save: index === logEntries.length - 1 ? 0 : 1,
        created_at: entry.createdAt,
        execution_id: 171311 + index
      };
    });

    container.innerHTML = `
      <link rel="stylesheet" href="${studentLogCssPath}">
      <div class="container">
        <section class="sample-section log-hero mb-4">
          <div class="hero-copy">
            <span class="hero-kicker">Student Log</span>
            <h1 class="section-title mb-3">コードログ</h1>
            <p class="hero-description mb-0">自動保存されたコードの履歴を、差分を強調しながら時系列で確認できます。前後の状態を見比べて、試行錯誤の流れを振り返るためのページです。</p>
            <div class="hero-action-row mt-4">
              <button class="btn btn-warning btn-lg" type="button" data-action="back-to-evaluation">評価結果に戻る</button>
            </div>
          </div>
          <div class="hero-meta-card">
            <div class="meta-label">対象課題</div>
            <div class="meta-value">${escapeHtml(data.task)}</div>
            <div class="meta-subtext">TASK-001 / ${data.difficulty} / 自動保存 30秒間隔</div>
            <div class="hero-status-list mt-3">
              <div class="hero-status-item">
                <span class="hero-status-name">保存回数</span>
                <span class="hero-status-pill">6回</span>
              </div>
              <div class="hero-status-item">
                <span class="hero-status-name">実行回数</span>
                <span class="hero-status-pill accent">5回</span>
              </div>
            </div>
          </div>
        </section>

        <div class="row g-4 align-items-start">
          <div class="col-xl-8">
            <section class="sample-section log-viewer-section">
              <div class="section-heading-row mb-3">
                <div>
                  <h2 class="card-title mb-2">差分ビューアー</h2>
                  <p class="text-muted mb-0">各保存時点のコードを時系列で確認できます。</p>
                </div>
              </div>

              <div class="viewer-controls">
                <button id="promptPrevCodeButton" class="btn btn-outline-secondary" type="button">← 前へ</button>
                <div class="viewer-step-indicator"><span id="promptStepIndicator">1 / ${logs.length}</span></div>
                <button id="promptNextCodeButton" class="btn btn-primary" type="button">次へ →</button>
              </div>

              <div class="viewer-id-row mt-3">
                <span id="promptSourceCodeId" class="viewer-id-pill">ソースコードID: -</span>
                <span id="promptExecutionId" class="viewer-id-pill">実行ID: -</span>
              </div>

              <div class="code-shell mt-4">
                <pre id="promptCodeBlock" class="code-block"></pre>
              </div>

              <div class="viewer-meta-row mt-3">
                <p id="promptTimestamp" class="timestamp mb-0"></p>
                <p id="promptBoundaryMessage" class="boundary-message mb-0"></p>
              </div>
            </section>
          </div>

          <div class="col-xl-4">
            <div class="sticky-xl-top sidebar-stack">
              <section class="sample-section sidebar-section">
                <div class="sidebar-switcher mb-3" role="tablist" aria-label="side panel switcher">
                  <button class="sidebar-switch-button is-active" type="button" data-sidebar-panel="timeline">ログ一覧</button>
                  <button class="sidebar-switch-button" type="button" data-sidebar-panel="reasons">評価の理由</button>
                </div>

                <div id="promptTimelinePanel" class="sidebar-panel is-active">
                  <div class="section-heading-row mb-3">
                    <div>
                      <h2 class="card-title mb-2">ログ一覧</h2>
                      <p class="text-muted mb-0">各保存時点の概要を確認できます。</p>
                    </div>
                  </div>
                  <div class="log-timeline-scroll"><div id="promptLogTimeline" class="log-timeline"></div></div>
                </div>

                <div id="promptReasonsPanel" class="sidebar-panel">
                  <div class="section-heading-row mb-3">
                    <div>
                      <h2 class="card-title mb-2">評価の理由</h2>
                      <p class="text-muted mb-0">評価基準ごとの根拠を確認できます。</p>
                    </div>
                  </div>
                  <div id="promptReasonFilterGroup" class="reason-filter-group mb-3" role="tablist" aria-label="reason category filter"></div>
                  <div class="log-timeline-scroll"><div id="promptReasonList" class="reason-list"></div></div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </div>
    `;

    const codeBlock = container.querySelector('#promptCodeBlock');
    const timestamp = container.querySelector('#promptTimestamp');
    const boundaryMessage = container.querySelector('#promptBoundaryMessage');
    const stepIndicator = container.querySelector('#promptStepIndicator');
    const sourceCodeId = container.querySelector('#promptSourceCodeId');
    const executionId = container.querySelector('#promptExecutionId');
    const prevCodeButton = container.querySelector('#promptPrevCodeButton');
    const nextCodeButton = container.querySelector('#promptNextCodeButton');
    const logTimeline = container.querySelector('#promptLogTimeline');
    const sidebarSwitchButtons = container.querySelectorAll('[data-sidebar-panel]');
    const timelinePanel = container.querySelector('#promptTimelinePanel');
    const reasonsPanel = container.querySelector('#promptReasonsPanel');
    const reasonFilterGroup = container.querySelector('#promptReasonFilterGroup');
    const reasonList = container.querySelector('#promptReasonList');
    const backButton = container.querySelector('[data-action="back-to-evaluation"]');

    let index = 0;
    let currentReasonFilter = 'all';

    function escapeHTML(text) {
      return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

    function getLogLabel(log) {
      if (log.submitted_at) return '手動提出';
      return log.auto_save ? '自動保存' : '手動保存';
    }

    function getLogPreview(log) {
      const parts = [];
      parts.push('ID: ' + log.id);
      parts.push('残作業量: ' + log.remaining_work);
      if (log.remaining_proportion !== null && log.remaining_proportion !== undefined) {
        parts.push('進捗差分: ' + log.remaining_proportion);
      }
      return parts.join(' / ');
    }

    function splitLines(text) {
      return text.match(/.*(?:\n|$)/g).filter(function(line) { return line.length > 0; });
    }

    function normalizeLineText(text) {
      return text.endsWith('\n') ? text.slice(0, -1) : text;
    }

    function renderCodeLines(lines) {
      return lines.map(function(line, lineIndex) {
        const safeText = escapeHTML(normalizeLineText(line)) || '&nbsp;';
        return '<span class="code-line"><span class="line-number">' + String(lineIndex + 1) + '</span><span class="line-content">' + safeText + '</span></span>';
      }).join('');
    }

    function renderEvidenceItems(evidenceItems) {
      if (!Array.isArray(evidenceItems) || evidenceItems.length === 0) return '';
      const chips = evidenceItems.flatMap(function(item) {
        if (Array.isArray(item.ids)) {
          return item.ids.map(function(id) {
            return '<span class="evidence-pill">' + escapeHTML(item.label || 'ID') + ': ' + escapeHTML(String(id)) + '</span>';
          });
        }
        if (item.id !== undefined && item.id !== null) {
          return ['<span class="evidence-pill">' + escapeHTML(item.label || 'ID') + ': ' + escapeHTML(String(item.id)) + '</span>'];
        }
        if (item.value !== undefined && item.value !== null) {
          const unit = item.unit ? String(item.unit) : '';
          return ['<span class="evidence-pill">' + escapeHTML(item.label || '値') + ': ' + escapeHTML(String(item.value)) + escapeHTML(unit) + '</span>'];
        }
        return [];
      }).join('');
      return chips ? '<div class="evidence-row">' + chips + '</div>' : '';
    }

    function renderReasonCard(reason) {
      const details = Array.isArray(reason.details) ? reason.details : [];
      const detailHTML = details.length > 0
        ? '<ul class="reason-detail-list">' + details.map(function(detail) {
          const evidenceHTML = renderEvidenceItems(detail.evidence);
          return '<li><span>' + escapeHTML(detail.text || '') + '</span>' + evidenceHTML + '</li>';
        }).join('') + '</ul>'
        : '';
      const reasonEvidenceHTML = renderEvidenceItems(reason.evidence);
      return '<article class="reason-card" data-category="' + escapeHTML(reason.category || '') + '">' +
        '<div class="reason-title-row"><h3>' + escapeHTML(reason.title || '-') + ' : ' + escapeHTML(String(reason.score || '-')) + '</h3><span class="reason-chip">' + escapeHTML(reason.categoryLabel || '') + '</span></div>' +
        '<p class="reason-body">' + escapeHTML(reason.body || '') + '</p>' +
        detailHTML +
        reasonEvidenceHTML +
      '</article>';
    }

    function updateReasonList() {
      if (!reasonList) return;
      const filteredReasons = reasonData.reasons.filter(function(reason) {
        return currentReasonFilter === 'all' || reason.category === currentReasonFilter;
      });
      reasonList.innerHTML = filteredReasons.map(renderReasonCard).join('');
    }

    function createReasonFilters() {
      if (!reasonFilterGroup) return;
      reasonFilterGroup.innerHTML = '';
      reasonData.reasonFilters.forEach(function(filter, filterIndex) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'reason-filter' + (filterIndex === 0 ? ' is-active' : '');
        button.textContent = filter.label;
        button.setAttribute('data-reason-filter', filter.key);
        button.addEventListener('click', function() {
          currentReasonFilter = filter.key;
          reasonFilterGroup.querySelectorAll('.reason-filter').forEach(function(element) {
            element.classList.toggle('is-active', element === button);
          });
          updateReasonList();
        });
        reasonFilterGroup.appendChild(button);
      });
      if (reasonData.reasonFilters.length > 0) {
        currentReasonFilter = reasonData.reasonFilters[0].key;
      }
    }

    function switchSidebarPanel(targetPanel) {
      const showTimeline = targetPanel === 'timeline';
      if (timelinePanel) timelinePanel.classList.toggle('is-active', showTimeline);
      if (reasonsPanel) reasonsPanel.classList.toggle('is-active', !showTimeline);
      sidebarSwitchButtons.forEach(function(button) {
        button.classList.toggle('is-active', button.getAttribute('data-sidebar-panel') === targetPanel);
      });
    }

    function updateButtons() {
      if (prevCodeButton) prevCodeButton.disabled = index === 0;
      if (nextCodeButton) nextCodeButton.disabled = index === logs.length - 1;
    }

    function updateTimeline() {
      const items = logTimeline ? logTimeline.querySelectorAll('.timeline-item') : [];
      items.forEach(function(item, itemIndex) {
        item.classList.toggle('is-active', itemIndex === index);
      });
    }

    function renderCode(currentIndex) {
      const currentLog = logs[currentIndex];
      if (!currentLog || !codeBlock || !timestamp || !boundaryMessage || !stepIndicator) return;

      codeBlock.innerHTML = renderCodeLines(splitLines(currentLog.content));
      if (sourceCodeId) sourceCodeId.textContent = 'ソースコードID: ' + String(currentLog.id || '-');
      if (executionId) executionId.textContent = '実行ID: ' + String(currentLog.execution_id || '-');

      timestamp.textContent = '保存日時: ' + currentLog.created_at;
      stepIndicator.textContent = String(currentIndex + 1) + ' / ' + String(logs.length);

      if (currentIndex === 0) {
        boundaryMessage.textContent = '最初の変更です。';
      } else if (currentIndex === logs.length - 1) {
        boundaryMessage.textContent = '最後の変更です。';
      } else {
        boundaryMessage.textContent = '';
      }

      updateButtons();
      updateTimeline();
    }

    function createTimeline() {
      if (!logTimeline) return;
      logTimeline.innerHTML = '';
      logs.forEach(function(log, logIndex) {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'timeline-item';
        item.innerHTML =
          '<div class="timeline-time">' + log.created_at + '</div>' +
          '<div class="timeline-label">' + getLogLabel(log) + '</div>' +
          '<div class="timeline-preview">' + escapeHTML(getLogPreview(log)) + '</div>';
        item.addEventListener('click', function() {
          index = logIndex;
          renderCode(index);
        });
        logTimeline.appendChild(item);
      });
    }

    if (prevCodeButton) {
      prevCodeButton.addEventListener('click', function() {
        if (index > 0) {
          index -= 1;
          renderCode(index);
        }
      });
    }

    if (nextCodeButton) {
      nextCodeButton.addEventListener('click', function() {
        if (index < logs.length - 1) {
          index += 1;
          renderCode(index);
        }
      });
    }

    sidebarSwitchButtons.forEach(function(button) {
      button.addEventListener('click', function() {
        const targetPanel = button.getAttribute('data-sidebar-panel') || 'timeline';
        switchSidebarPanel(targetPanel);
      });
    });

    if (backButton) {
      backButton.addEventListener('click', function() {
        renderEvaluationView();
      });
    }

    createTimeline();
    createReasonFilters();
    updateReasonList();
    switchSidebarPanel('timeline');
    renderCode(index);
  };

  const renderEvaluationView = function() {
    container.innerHTML = `
      <link rel="stylesheet" href="${studentCssPath}">
      <div class="container">
        <section class="sample-section evaluation-hero mb-4">
          <div class="hero-copy">
            <span class="hero-kicker">Student Evaluation</span>
            <h1 class="section-title mb-3">評価結果</h1>
            <p class="hero-description mb-0">
              コードログと実行履歴をもとに、2つの観点で自動評価したサンプル表示です。各観点の総合点と、根拠になったログの要約を確認できます。
            </p>
            <div class="hero-action-row mt-4">
              <button class="btn btn-warning btn-lg hero-log-button" type="button" data-action="open-log">ログを見る</button>
            </div>
          </div>
          <div class="hero-meta-card">
            <div class="meta-label">対象課題</div>
            <div class="meta-value">${escapeHtml(data.task)}</div>
            <div class="meta-subtext">ID: ${data.studentId} / ${data.school} ${data.class} / 評価日時: ${data.evaluatedAt}</div>
            <div class="hero-score-row">
              <div class="hero-score-pill thinking">思考力・判断力・表現力 ${data.thinking.toFixed(1)}</div>
              <div class="hero-score-pill attitude">主体的に学習に取り組む態度 ${data.attitude.toFixed(1)}</div>
            </div>
          </div>
        </section>
        <section class="sample-section overview-section mb-4">
          <div class="section-heading-row">
            <div>
              <h2 class="card-title mb-2">総合評価</h2>
              <p class="text-muted mb-0">2つの観点ごとに、ルーブリックをもとにした総合点を表示します。</p>
            </div>
            <span class="score-caption">5段階評価</span>
          </div>
          <div class="row g-4 mt-1">
            <div class="col-lg-6">
              <article class="score-panel thinking-panel">
                <div class="panel-kicker">思考力・判断力・表現力</div>
                <div class="panel-score-row">
                  <div class="panel-score">${Math.round(data.thinking)}</div>
                  <div>
                    <h3 class="panel-title">基礎は理解しているが改善の余地がある</h3>
                    <p class="panel-description mb-0">基本的な処理の流れは実装できていますが、文法エラーの再発や型の扱いなどで不安定な箇所が見られます。</p>
                  </div>
                </div>
                <div class="score-meter" aria-hidden="true">
                  <span class="is-filled"></span><span class="is-filled"></span><span class="is-filled"></span><span></span><span></span>
                </div>
              </article>
            </div>
            <div class="col-lg-6">
              <article class="score-panel attitude-panel">
                <div class="panel-kicker">主体的に学習に取り組む態度</div>
                <div class="panel-score-row">
                  <div class="panel-score">${Math.round(data.attitude)}</div>
                  <div>
                    <h3 class="panel-title">改善を続けながら課題に向き合っている</h3>
                    <p class="panel-description mb-0">保存・実行・修正の流れが複数回確認でき、エラーや出力結果を見ながら改善しています。</p>
                  </div>
                </div>
                <div class="score-meter" aria-hidden="true">
                  <span class="is-filled"></span><span class="is-filled"></span><span class="is-filled"></span><span class="is-filled"></span><span></span>
                </div>
              </article>
            </div>
          </div>
        </section>
        <div class="row g-4 align-items-start">
          <div class="col-12">
            <section class="sample-section breakdown-section mb-4">
              <div class="section-heading-row">
                <div>
                  <h2 class="card-title mb-2">観点別スコア</h2>
                  <p class="text-muted mb-0">細かな評価項目ごとの点数です。</p>
                </div>
              </div>
              <div class="breakdown-grid">
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${Math.round(data.thinking)}</span></div><h3>文法デバッグ能力</h3><p class="mb-0">初歩的な文法エラーが複数回見られる一方、最終的にはコンパイルエラーを解消できています。</p></article>
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${Math.round(data.thinking)}</span></div><h3>論理デバッグ能力</h3><p class="mb-0">基本的な処理の流れは理解できていますが、出力誤りの原因特定が不十分な箇所があります。</p></article>
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${Math.round(data.thinking)}</span></div><h3>アルゴリズムの設計と実装</h3><p class="mb-0">必要な変数定義と反復構造は実装できていますが、終了条件や型の使い分けに改善余地があります。</p></article>
                <article class="metric-card"><div class="metric-header"><span class="metric-group">思考・判断・表現</span><span class="metric-score">${Math.round(data.thinking)}</span></div><h3>コードの可読性</h3><p class="mb-0">インデントは概ね揃っていますが、命名やコメント不足により読みやすさの改善が必要です。</p></article>
                <article class="metric-card accent-card"><div class="metric-header"><span class="metric-group">態度</span><span class="metric-score">${Math.round(data.attitude)}</span></div><h3>課題への粘り強さ</h3><p class="mb-0">複数回の保存と実行を通して、途中で止めずに解決まで進めています。</p></article>
                <article class="metric-card accent-card"><div class="metric-header"><span class="metric-group">態度</span><span class="metric-score">${Math.round(data.attitude)}</span></div><h3>課題解決への意欲</h3><p class="mb-0">実行結果を受けてコード改善を続けており、理解しようとする姿勢が見られます。</p></article>
              </div>
            </section>
            <section class="sample-section reasons-section">
              <div class="section-heading-row reasons-heading">
                <div>
                  <h2 class="card-title mb-2">評価の理由</h2>
                  <p class="text-muted mb-0">各項目の点数に至った具体的な根拠です。</p>
                </div>
                <div class="reason-filter-group" role="tablist" aria-label="reason category filter">
                  <button class="reason-filter is-active" type="button" data-filter="all">すべて</button>
                  <button class="reason-filter" type="button" data-filter="thinking">思考・判断・表現</button>
                  <button class="reason-filter" type="button" data-filter="attitude">態度</button>
                </div>
              </div>
              <div class="reason-list">
                ${(example.scoreReasons || []).map(function(reason) {
                  const details = reason.details || [];
                  const detailHTML = details.length > 0
                    ? '<ul class="reason-detail-list">' + details.map(function(detail) {
                      return '<li><p class="mb-0">' + escapeHtml(detail.text || detail) + '</p></li>';
                    }).join('') + '</ul>'
                    : '';
                  return '<article class="reason-card" data-category="' + escapeHtml(reason.category || 'all') + '">' +
                    '<div class="reason-title-row"><h3>' + escapeHtml(reason.title) + ': ' + reason.score + '</h3><span class="reason-chip">' + escapeHtml(reason.category || '評価') + '</span></div>' +
                    '<p class="mb-0">' + escapeHtml(reason.body) + '</p>' +
                    detailHTML +
                  '</article>';
                }).join('')}
              </div>
            </section>
            <div class="d-flex justify-content-center flex-wrap gap-3 mt-4">
              <button class="btn btn-warning btn-lg px-5" type="button" data-action="open-log">ログを見る</button>
            </div>
          </div>
        </div>
      </div>
    `;

    const reasonFilters = container.querySelectorAll('[data-filter]');
    const reasonCards = container.querySelectorAll('.reason-card');
    reasonFilters.forEach((filterButton) => {
      filterButton.addEventListener('click', () => {
        const filter = filterButton.getAttribute('data-filter');
        reasonFilters.forEach((item) => item.classList.remove('is-active'));
        filterButton.classList.add('is-active');
        reasonCards.forEach((card) => {
          const category = card.getAttribute('data-category');
          const hidden = filter !== 'all' && category !== filter;
          card.classList.toggle('is-hidden', hidden);
        });
      });
    });

    const openLogButtons = container.querySelectorAll('[data-action="open-log"]');
    openLogButtons.forEach((logButton) => {
      logButton.addEventListener('click', function(event) {
        event.preventDefault();
        renderLogView();
      });
    });
  };

  renderEvaluationView();

  const modalEl = document.getElementById('evaluationExampleDetailModal');
  if (!modalEl) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
