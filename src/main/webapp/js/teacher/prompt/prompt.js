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
});
