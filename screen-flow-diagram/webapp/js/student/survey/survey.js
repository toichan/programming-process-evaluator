window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const steps = Array.from(document.querySelectorAll('.question-step'));
  const progressItems = Array.from(document.querySelectorAll('.step-progress-item'));
  const stepIndicator = document.querySelector('#stepIndicator');
  const prevStepButton = document.querySelector('#prevStepButton');
  const nextStepButton = document.querySelector('#nextStepButton');
  const submitSurveyButton = document.querySelector('#submitSurveyButton');
  const rubricTabs = document.querySelectorAll('[data-rubric-target]');

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  let activeStep = 0;

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

  renderSteps();
});