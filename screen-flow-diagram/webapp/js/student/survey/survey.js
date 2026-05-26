window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const feedback = window.PPEFeedback || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const questionnaireSection = document.querySelector('.questionnaire-section');
  const steps = Array.from(document.querySelectorAll('.question-step'));
  const progressItems = Array.from(document.querySelectorAll('.step-progress-item'));
  const stepIndicator = document.querySelector('#stepIndicator');
  const prevStepButton = document.querySelector('#prevStepButton');
  const nextStepButton = document.querySelector('#nextStepButton');
  const submitSurveyButton = document.querySelector('#submitSurveyButton');
  const saveSurveyButton = document.querySelector('#saveSurveyButton');
  const stepAutoFillHint = document.querySelector('#stepAutoFillHint');
  const rubricTabs = document.querySelectorAll('[data-rubric-target]');
  const surveyForm = document.querySelector('.survey-form');
  const pageFeedback = feedback.createPageFeedback({
    title: 'アンケート',
    alertTarget: () => questionnaireSection || surveyForm
  });

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  let activeStep = 0;
  let step3InitialState = null;
  let step4InitialState = null;

  function getCheckedValue(name) {
    const checked = document.querySelector('input[name="' + name + '"]:checked');
    return checked ? checked.value : '';
  }

  function setCheckedValue(name, value) {
    if (!value) return;
    const target = document.querySelector('input[name="' + name + '"][value="' + value + '"]');
    if (target) target.checked = true;
  }

  function collectStep3State() {
    return {
      processOpinion: getCheckedValue('processOpinion'),
      processReason: document.querySelector('#processReason')?.value || ''
    };
  }

  function collectStep4State() {
    return {
      usabilityScore: getCheckedValue('usabilityScore'),
      usabilityComment: document.querySelector('#usabilityComment')?.value || ''
    };
  }

  function hasStep3Changed() {
    if (!step3InitialState) return false;
    return JSON.stringify(collectStep3State()) !== JSON.stringify(step3InitialState);
  }

  function hasStep4Changed() {
    if (!step4InitialState) return false;
    return JSON.stringify(collectStep4State()) !== JSON.stringify(step4InitialState);
  }

  function updateAutoFillHint() {
    if (!stepAutoFillHint) return;

    if (activeStep === 2) {
      stepAutoFillHint.textContent = hasStep3Changed()
        ? '回答内容に変化があります。内容を確認して次の設問へ進んでください。'
        : '';
      return;
    }

    if (activeStep === 3) {
      stepAutoFillHint.textContent = hasStep4Changed()
        ? '回答内容に変化があります。内容を確認して送信してください。'
        : '';
      return;
    }

    stepAutoFillHint.textContent = '';
  }

  function collectSurveyDraft() {
    return {
      activeStep,
      thinkingSelfScore: getCheckedValue('thinkingSelfScore'),
      thinkingSelfReason: document.querySelector('#thinkingSelfReason')?.value || '',
      thinkingValidity: getCheckedValue('thinkingValidity'),
      thinkingValidityReason: document.querySelector('#thinkingValidityReason')?.value || '',
      attitudeSelfScore: getCheckedValue('attitudeSelfScore'),
      attitudeSelfReason: document.querySelector('#attitudeSelfReason')?.value || '',
      attitudeValidity: getCheckedValue('attitudeValidity'),
      attitudeValidityReason: document.querySelector('#attitudeValidityReason')?.value || '',
      processOpinion: getCheckedValue('processOpinion'),
      processReason: document.querySelector('#processReason')?.value || '',
      usabilityScore: getCheckedValue('usabilityScore'),
      usabilityComment: document.querySelector('#usabilityComment')?.value || ''
    };
  }

  function applySurveyDraft(draft) {
    if (!draft || typeof draft !== 'object') return;

    setCheckedValue('thinkingSelfScore', draft.thinkingSelfScore);
    setCheckedValue('thinkingValidity', draft.thinkingValidity);
    setCheckedValue('attitudeSelfScore', draft.attitudeSelfScore);
    setCheckedValue('attitudeValidity', draft.attitudeValidity);
    setCheckedValue('processOpinion', draft.processOpinion);
    setCheckedValue('usabilityScore', draft.usabilityScore);

    const setValue = (selector, value) => {
      const el = document.querySelector(selector);
      if (el && typeof value === 'string') el.value = value;
    };

    setValue('#thinkingSelfReason', draft.thinkingSelfReason);
    setValue('#thinkingValidityReason', draft.thinkingValidityReason);
    setValue('#attitudeSelfReason', draft.attitudeSelfReason);
    setValue('#attitudeValidityReason', draft.attitudeValidityReason);
    setValue('#processReason', draft.processReason);
    setValue('#usabilityComment', draft.usabilityComment);

    if (Number.isInteger(draft.activeStep)) {
      activeStep = Math.max(0, Math.min(steps.length - 1, draft.activeStep));
    }
  }

  function applyDefaultAutoFilledAnswers() {
    if (getCheckedValue('processOpinion')) return;
    setCheckedValue('processOpinion', '3');
    const processReason = document.querySelector('#processReason');
    if (processReason && !processReason.value.trim()) {
      processReason.value = '試行錯誤が記録されること自体に大きな抵抗はないが、評価にどう反映されるかは気になる。';
    }

    if (!getCheckedValue('usabilityScore')) {
      setCheckedValue('usabilityScore', '4');
    }
    const usabilityComment = document.querySelector('#usabilityComment');
    if (usabilityComment && !usabilityComment.value.trim()) {
      usabilityComment.value = '画面遷移は分かりやすく、全体として操作しやすかった。';
    }
  }

  function renderSteps() {
    steps.forEach((step, index) => {
      step.classList.toggle('is-active', index === activeStep);
    });

    progressItems.forEach((item, index) => {
      item.classList.toggle('is-active', index <= activeStep);
    });

    if (stepIndicator) {
      stepIndicator.textContent = `${activeStep + 1} / ${steps.length}`;
    }

    if (prevStepButton) {
      prevStepButton.disabled = activeStep === 0;
    }

    if (nextStepButton) {
      nextStepButton.classList.toggle('d-none', activeStep === steps.length - 1);
    }

    if (submitSurveyButton) {
      submitSurveyButton.classList.toggle('d-none', activeStep !== steps.length - 1);
    }

    updateAutoFillHint();
  }

  if (prevStepButton) {
    prevStepButton.addEventListener('click', () => {
      activeStep = Math.max(0, activeStep - 1);
      renderSteps();
    });
  }

  if (nextStepButton) {
    nextStepButton.addEventListener('click', () => {
      activeStep = Math.min(steps.length - 1, activeStep + 1);
      renderSteps();
    });
  }

  rubricTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const targetId = tab.getAttribute('data-rubric-target');

      rubricTabs.forEach((button) => button.classList.remove('is-active'));
      document.querySelectorAll('.sidebar-section .rubric-panel').forEach((panel) => panel.classList.remove('is-active'));

      tab.classList.add('is-active');
      document.getElementById(targetId)?.classList.add('is-active');
    });
  });

  if (saveSurveyButton) {
    saveSurveyButton.addEventListener('click', () => {
      try {
        const draft = collectSurveyDraft();
        localStorage.setItem('studentSurveyDraft', JSON.stringify(draft));
        pageFeedback.clearInlineAlert();
        pageFeedback.toast({
          title: 'アンケート',
          message: '入力内容を保存しました。',
          variant: 'success'
        });
      } catch (_error) {
        pageFeedback.inlineAlert('保存に失敗しました。時間をおいて再度お試しください。', 'danger');
      }
    });
  }

  if (surveyForm) {
    surveyForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      pageFeedback.clearInlineAlert();

      const confirmed = await pageFeedback.confirm({
        title: 'アンケートを送信しますか？',
        message: '次のデータを送信します。',
        detailTitle: '',
        details: [
          '入力したアンケート回答',
          '回答日時'
        ],
        confirmLabel: '送信する',
        cancelLabel: '戻る',
        variant: 'success'
      });

      if (!confirmed) {
        return;
      }

      window.sessionStorage.setItem('ppe-home-message-title', 'アンケート');
      window.sessionStorage.setItem('ppe-home-message', 'アンケートを送信しました。');
      window.sessionStorage.setItem('ppe-home-message-time', new Date().toLocaleString('ja-JP', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      }));
      localStorage.removeItem('studentSurveyDraft');
      window.location.href = '../home/home.html';
    });
  }

  try {
    const rawDraft = localStorage.getItem('studentSurveyDraft');
    if (rawDraft) {
      applySurveyDraft(JSON.parse(rawDraft));
    }
  } catch (_error) {
    // Ignore invalid draft data
  }

  applyDefaultAutoFilledAnswers();
  step3InitialState = collectStep3State();
  step4InitialState = collectStep4State();

  renderSteps();
});