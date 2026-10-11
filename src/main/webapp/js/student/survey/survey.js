window.addEventListener('DOMContentLoaded', () => {
	const steps = Array.from(document.querySelectorAll('.question-step'));
	const indicator = document.querySelector('#stepIndicator');
	const progress = document.querySelector('#stepProgress');
	const previous = document.querySelector('#prevStepButton');
	const next = document.querySelector('#nextStepButton');
	const form = document.querySelector('.survey-form');
	const submit = document.querySelector('#submitSurveyButton');
	const answerFields = document.querySelector('#surveyAnswers');
	const edit = document.querySelector('#editSurveyButton');
	const cancelEdit = document.querySelector('#cancelSurveyEditButton');
	let readOnly = form?.dataset.readOnly === 'true';
	const feedback = window.PPEFeedback.createPageFeedback({
		title: 'アンケート',
		alertTarget: () => document.querySelector('.questionnaire-section')
	});
	if (steps.length === 0 || !indicator || !progress || !previous || !next) return;

	const answerSnapshot = () => JSON.stringify(Array.from(answerFields.querySelectorAll('input, textarea, select'))
		.map((field) => [field.name, field.value, field.checked ?? null]));
	const initialAnswers = answerSnapshot();
	const navigation = window.PPEUnsavedNavigation.bind({
		isDirty: () => !readOnly && (form.dataset.unsavedResponse === 'true' || answerSnapshot() !== initialAnswers),
		feedback,
		message: '保存していないアンケート回答を破棄して移動しますか？'
	});
	let active = 0;
	let submitConfirmed = false;
	const progressItems = steps.map((step, index) => {
		step.hidden = index !== active;
		step.classList.toggle('is-active', index === active);
		const item = document.createElement('span');
		item.className = 'step-progress-item';
		progress.append(item);
		return item;
	});
	progress.style.setProperty('--step-count', String(steps.length));

	const requiredBlocks = (step) => Array.from(step.querySelectorAll('[data-required="true"]'));
	const isValid = (block) => {
		const controls = Array.from(block.querySelectorAll('input, textarea, select'));
		const radios = controls.filter((control) => control.type === 'radio');
		if (radios.length > 0) return radios.some((radio) => radio.checked);
		const checkboxes = controls.filter((control) => control.type === 'checkbox');
		if (checkboxes.length > 0) return checkboxes.some((checkbox) => checkbox.checked);
		const valueField = controls.find((control) => control.type !== 'hidden');
		return valueField ? valueField.value.trim().length > 0 : false;
	};

	const setInvalid = (block, invalid) => {
		block.classList.toggle('is-invalid', invalid);
		block.querySelectorAll('input, textarea, select').forEach((control) => {
			if (invalid) control.setAttribute('aria-invalid', 'true');
			else control.removeAttribute('aria-invalid');
		});
	};

	const validateStep = (step) => {
		const invalid = requiredBlocks(step).filter((block) => !isValid(block));
		step.querySelectorAll('.question-block.is-invalid').forEach((block) => setInvalid(block, false));
		invalid.forEach((block) => setInvalid(block, true));
		return invalid;
	};

	const update = () => {
		steps.forEach((step, index) => {
			step.hidden = index !== active;
			step.classList.toggle('is-active', index === active);
		});
		progressItems.forEach((item, index) => item.classList.toggle('is-active', index <= active));
		indicator.textContent = `${active + 1} / ${steps.length}`;
		previous.disabled = active === 0;
		next.hidden = active === steps.length - 1;
		if (submit) submit.hidden = readOnly || active !== steps.length - 1;
	};

	const focusStep = (step) => {
		const target = step.querySelector('input, textarea, select') || step.querySelector('h3');
		target?.focus();
	};

	const showValidationError = (invalidBlocks) => {
		feedback.inlineAlert('必須項目を入力してから次へ進んでください。', 'danger');
		invalidBlocks[0]?.querySelector('input, textarea, select')?.focus();
	};

	previous.addEventListener('click', () => {
		if (active > 0) {
			active -= 1;
			update();
			focusStep(steps[active]);
		}
	});

	next.addEventListener('click', () => {
		const invalid = readOnly ? [] : validateStep(steps[active]);
		if (invalid.length > 0) {
			showValidationError(invalid);
			return;
		}
		feedback.clearInlineAlert();
		if (active < steps.length - 1) {
			active += 1;
			update();
			focusStep(steps[active]);
		}
	});

	edit?.addEventListener('click', () => {
		readOnly = false;
		answerFields.disabled = false;
		edit.hidden = true;
		cancelEdit.hidden = false;
		update();
		focusStep(steps[active]);
	});
	cancelEdit?.addEventListener('click', async () => {
		if (!(await navigation.confirmLeave({
			message: '修正中の回答を破棄して、提出済みの回答に戻しますか？',
			confirmLabel: '修正を破棄する'
		}))) return;
		form.reset();
		readOnly = true;
		answerFields.disabled = true;
		edit.hidden = false;
		cancelEdit.hidden = true;
		feedback.clearInlineAlert();
		steps.forEach((step) => step.querySelectorAll('.is-invalid').forEach((block) => setInvalid(block, false)));
		update();
	});

	form?.addEventListener('input', (event) => {
		if (!(event.target instanceof Element)) return;
		const block = event.target.closest('[data-required="true"]');
		if (block?.classList.contains('is-invalid') && isValid(block)) {
			setInvalid(block, false);
			if (!form.querySelector('.question-block.is-invalid')) feedback.clearInlineAlert();
		}
	});

	form?.addEventListener('change', (event) => {
		if (!(event.target instanceof Element)) return;
		const block = event.target.closest('[data-required="true"]');
		if (block?.classList.contains('is-invalid') && isValid(block)) {
			setInvalid(block, false);
			if (!form.querySelector('.question-block.is-invalid')) feedback.clearInlineAlert();
		}
	});

	form?.addEventListener('submit', async (event) => {
		if (event.submitter?.value === 'draft') {
			navigation.allowNavigation();
			return;
		}
		if (submitConfirmed) {
			submitConfirmed = false;
			navigation.allowNavigation();
			return;
		}
		event.preventDefault();
		if (readOnly) return;

		for (let index = 0; index < steps.length; index += 1) {
			const invalid = validateStep(steps[index]);
			if (invalid.length > 0) {
				active = index;
				update();
				showValidationError(invalid);
				return;
			}
		}
		feedback.clearInlineAlert();

		try {
			const confirmed = await feedback.confirm({
				title: submit?.value === 'revise' ? '修正した回答を再提出しますか？' : 'アンケートを提出しますか？',
				message: submit?.value === 'revise' ? '修正前の回答を履歴に残して、回答を更新します。' : '入力した回答を提出します。',
				detailTitle: '送信内容',
				details: ['アンケート回答', '回答日時'],
				confirmLabel: '提出する',
				cancelLabel: '戻る',
				variant: 'success'
			});
			if (confirmed && submit instanceof HTMLButtonElement) {
				submitConfirmed = true;
				form.requestSubmit(submit);
			}
		} catch (error) {
			feedback.inlineAlert('提出確認を表示できませんでした。画面を再読み込みして、もう一度お試しください。', 'danger');
			throw error;
		}
	});

	update();
});
