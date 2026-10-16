document.addEventListener('DOMContentLoaded', function() {
  const pageFeedback = window.PPEFeedback.createPageFeedback({ title: 'プロンプト設計' });
  const promptInput = document.getElementById('evaluationPromptInput');
  let promptEditor = null;
  if (promptInput && window.CodeMirror) {
    promptEditor = window.CodeMirror.fromTextArea(promptInput, {
      mode: 'markdown',
      theme: 'material-darker',
      lineNumbers: true,
      lineWrapping: true,
      readOnly: promptInput.readOnly
    });
    promptEditor.setSize(null, '28rem');
  }

  const successNotice = document.getElementById('promptSuccessNotice');
  if (successNotice) {
    pageFeedback.toast({
      message: successNotice.textContent.trim(),
      variant: 'success',
      delay: 2200
    });
  }

  const additionalInstruction = document.getElementById('additionalInstructionInput');
  const draftAdditionalInstruction = document.getElementById('promptAdditionalInstructionForDraft');
  if (additionalInstruction && draftAdditionalInstruction) {
    additionalInstruction.addEventListener('input', function() {
      draftAdditionalInstruction.value = additionalInstruction.value;
    });
  }

  function syncDraftFieldsTo(form) {
    if (!form || form.id === 'promptDraftForm') return;
    if (promptEditor) promptEditor.save();
    const values = {
      aiModel: document.getElementById('modelSelect')?.value || '',
      commonPrompt: promptInput?.value || '',
      additionalInstruction: additionalInstruction?.value || draftAdditionalInstruction?.value || ''
    };
    Object.entries(values).forEach(function(entry) {
      const [name, value] = entry;
      let field = form.elements.namedItem(name);
      if (!field) {
        field = document.createElement('input');
        field.type = 'hidden';
        field.name = name;
        form.append(field);
      }
      field.value = value;
    });
  }

  document.querySelectorAll('form').forEach(function(form) {
    let confirmedAction = null;
    form.addEventListener('submit', function(event) {
      if (promptEditor && form.id === 'promptDraftForm') {
        promptEditor.save();
      }
      syncDraftFieldsTo(form);
      const submitter = event.submitter;
      const action = submitter ? submitter.value : '';
      const reevaluationMessages = {
        applyUnpublishedPrompt: {
          title: '未公開課題へのプロンプト適用',
          message: '確認した設定済みプロンプト版を、この課題の現在適用版にします。',
          details: [
            '未公開かつ学習開始前の課題だけが対象です。課題の公開は別の操作です。',
            '再評価・評価履歴・追加のAI呼び出しは行いません。'
          ],
          confirmText: '適用する'
        },
        startReevaluationPreview: {
          title: '全体再評価プレビュー',
          message: '参加者ごとに最新提出をGeminiへ送信して予測を生成します。',
          details: [
            'API費用はプレビューの確定・取消にかかわらず発生します。',
            '全対象の予測が成功するまで適用版や評価履歴は変更されません。'
          ],
          confirmText: 'プレビューを作成'
        },
        retryReevaluationPreview: {
          title: '失敗対象の再試行',
          message: '失敗した対象者だけをGeminiで再評価します。',
          details: ['成功済みの予測結果は保持されます。'],
          confirmText: '再試行'
        },
        cancelReevaluationPreview: {
          title: 'プレビューの取消',
          message: 'このプレビューを取り消しますか？',
          details: ['適用版・評価履歴・再評価jobは作成されません。予測呼び出しの費用は発生済みです。'],
          confirmText: '取り消す'
        },
        confirmReevaluationPreview: {
          title: '全体再評価の確定',
          message: '確認したプロンプト版を適用し、表示中の予測結果で再評価jobを登録します。',
          details: [
            '対象提出はプレビュー時点の版に固定されています。',
            '既存の評価履歴は上書きせず、新しい評価履歴を追加します。'
          ],
          confirmText: '適用・再評価を確定'
        }
      };
      const reevaluationMessage = reevaluationMessages[action];
      if (reevaluationMessage) {
        if (confirmedAction === action) {
          confirmedAction = null;
          if (action === 'applyUnpublishedPrompt') {
            form.elements.namedItem('applyConfirmed').value = 'yes';
          }
          return;
        }
        event.preventDefault();
        pageFeedback.confirm({
          ...reevaluationMessage,
          cancelText: '戻る',
          variant: action === 'confirmReevaluationPreview' ? 'primary' : 'warning'
        }).then(function(confirmed) {
          if (confirmed) {
            confirmedAction = action;
            form.requestSubmit(submitter);
          }
        });
        return;
      }
      if (!['generateFluctuations', 'generateEvaluationExamples'].includes(action)) {
        return;
      }
      if (confirmedAction === action) {
        confirmedAction = null;
        return;
      }

      event.preventDefault();
      const isFluctuationGeneration = action === 'generateFluctuations';
      pageFeedback.confirm({
        title: isFluctuationGeneration ? '揺らぎ項目の生成' : '評価例の生成',
        message: isFluctuationGeneration
          ? '選択した課題情報とプロンプトをAIへ送信して揺らぎ項目を生成します。'
          : '教師対応を保存し、合成データのみで評価例を生成します。',
        details: [
          '実在する生徒の提出・ソースコード編集ログは送信されません。',
          '生成結果は確認用の案として保存されます。'
        ],
        confirmText: '生成する',
        cancelText: 'キャンセル',
        variant: 'primary'
      }).then(function(confirmed) {
        if (confirmed) {
          confirmedAction = action;
          form.requestSubmit(submitter);
        }
      });
    });
  });

  const draftForm = document.getElementById('promptDraftForm');
  if (draftForm && promptEditor) {
    draftForm.addEventListener('reset', function() {
      window.setTimeout(function() {
        promptEditor.setValue(promptInput.value);
      }, 0);
    });
  }

  const reevaluationPreview = document.getElementById('reevaluationPreviewSection');
  const reevaluationJob = document.getElementById('reevaluationJobSection');
  const fluctuationSection = document.getElementById('fluctuationSection');
  const evaluationExamplesSection = document.getElementById('evaluationExamplesSection');
  const startStatusPolling = function(section, datasetField, responseField, activeStatuses, extraParameter) {
    if (!section || !activeStatuses.includes(section.dataset[datasetField])
        || section.dataset.generationStale === 'true') {
      return;
    }
    const statusUrl = new URL(section.dataset.statusUrl, window.location.href);
    statusUrl.searchParams.set('action', 'generationStatus');
    statusUrl.searchParams.set('taskId', section.dataset.taskId);
    statusUrl.searchParams.set('promptVersionId', section.dataset.promptVersionId);
    if (extraParameter) {
      statusUrl.searchParams.set(extraParameter, section.dataset[extraParameter]);
    }
    const progress = section.querySelector('[role="status"]');

    const pollStatus = function() {
      if (document.visibilityState !== 'visible') {
        window.setTimeout(pollStatus, 2000);
        return;
      }
      fetch(statusUrl, {
        headers: { Accept: 'application/json' },
        credentials: 'same-origin'
      }).then(function(response) {
        if (!response.ok) {
          throw new Error('HTTP ' + response.status);
        }
        return response.json();
      }).then(function(status) {
        if (status.stale || !activeStatuses.includes(status[responseField])) {
          window.location.reload();
          return;
        }
        window.setTimeout(pollStatus, 2000);
      }).catch(function(error) {
        console.error('教師AI処理の状態を取得できませんでした:', error);
        const message = document.createElement('p');
        message.className = 'alert alert-warning prompt-poll-error';
        message.setAttribute('role', 'alert');
        message.textContent = '処理状態を確認できません。接続を確認して、この画面を再読み込みしてください。';
        if (progress) {
          progress.replaceWith(message);
        } else {
          section.prepend(message);
        }
      });
    };
    window.setTimeout(pollStatus, 1500);
  };

  startStatusPolling(reevaluationPreview, 'previewStatus', 'previewStatus', ['generating'], 'previewCode');
  startStatusPolling(reevaluationJob, 'jobStatus', 'jobStatus', ['queued', 'in_progress'], 'jobId');
  startStatusPolling(
    fluctuationSection, 'generationStatus', 'fluctuationStatus', ['in_progress'], null);
  startStatusPolling(
    evaluationExamplesSection, 'generationStatus', 'evaluationExamplesStatus', ['in_progress'], null);
});
