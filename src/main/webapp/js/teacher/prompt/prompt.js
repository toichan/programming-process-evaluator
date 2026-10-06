document.addEventListener('DOMContentLoaded', function() {
  const pageFeedback = window.PPEFeedback.createPageFeedback({ title: 'プロンプト設計' });
  const promptInput = document.getElementById('evaluationPromptInput');
  let promptEditor = null;
  if (promptInput && window.CodeMirror) {
    promptEditor = window.CodeMirror.fromTextArea(promptInput, {
      mode: 'markdown',
      theme: 'material-darker',
      lineNumbers: true,
      lineWrapping: true
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

  document.querySelectorAll('form').forEach(function(form) {
    let confirmedAction = null;
    form.addEventListener('submit', function(event) {
      if (promptEditor && form.id === 'promptDraftForm') {
        promptEditor.save();
      }
      const submitter = event.submitter;
      const action = submitter ? submitter.value : '';
      const reevaluationMessages = {
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
          '実在する生徒の提出・コードログは送信されません。',
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
  if (reevaluationPreview && reevaluationPreview.dataset.previewStatus === 'generating') {
    window.setTimeout(function() {
      if (document.visibilityState === 'visible') {
        window.location.reload();
      }
    }, 1800);
  }
  const reevaluationJob = document.getElementById('reevaluationJobSection');
  if (reevaluationJob && ['queued', 'in_progress'].includes(reevaluationJob.dataset.jobStatus)) {
    window.setTimeout(function() {
      if (document.visibilityState === 'visible') {
        window.location.reload();
      }
    }, 1800);
  }
});
