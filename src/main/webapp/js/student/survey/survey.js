window.addEventListener('DOMContentLoaded', () => {
	const steps = Array.from(document.querySelectorAll('.question-step'));
	const indicator = document.querySelector('#stepIndicator');
	const progress = document.querySelector('#stepProgress');
	const previous = document.querySelector('#prevStepButton');
	const next = document.querySelector('#nextStepButton');
	if (steps.length === 0 || !indicator || !progress || !previous || !next) return;

	let active = 0;
	progress.style.setProperty('--step-count', String(steps.length));
	steps.forEach((step, index) => {
		if (index !== active) step.classList.add('is-hidden');
		const item = document.createElement('span');
		item.className = `step-progress-item${index === active ? ' is-active' : ''}`;
		progress.append(item);
	});

	const update = () => {
		steps.forEach((step, index) => step.classList.toggle('is-hidden', index !== active));
		Array.from(progress.children).forEach((item, index) => item.classList.toggle('is-active', index === active));
		indicator.textContent = `${active + 1} / ${steps.length}`;
		previous.disabled = active === 0;
		next.hidden = active === steps.length - 1;
	};

	previous.addEventListener('click', () => {
		if (active > 0) {
			active -= 1;
			update();
		}
	});
	next.addEventListener('click', () => {
		if (active < steps.length - 1) {
			active += 1;
			update();
			steps[active].querySelector('input, textarea')?.focus();
		}
	});
	update();
});
