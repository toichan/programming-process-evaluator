window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	const headerPlaceholder = document.querySelector('#header-placeholder');
	const footerPlaceholder = document.querySelector('#footer-placeholder');
	const homeMessage = document.querySelector('#homeMessage');
	const homeMessageTime = document.querySelector('#homeMessageTime');
	const submittedMessage = window.sessionStorage.getItem('ppe-home-message');
	const submittedTime = window.sessionStorage.getItem('ppe-home-message-time');

	const setActionDisabled = (button, disabled) => {
		if (!button) {
			return;
		}

		button.classList.toggle('is-disabled-action', disabled);
		button.setAttribute('aria-disabled', disabled ? 'true' : 'false');
		button.tabIndex = disabled ? -1 : 0;
	};

	const syncTaskActionButtons = () => {
		const taskCards = document.querySelectorAll('.task-list-card');

		taskCards.forEach((card) => {
			const taskStatus = card.querySelector('.task-status-item .status-unstarted, .task-status-item .status-editing, .task-status-item .status-submitted');
			const evaluationStatus = card.querySelector('.task-status-item .status-unrated, .task-status-item .status-rated');
			const surveyStatus = card.querySelector('.task-status-item .status-pending, .task-status-item .status-completed');
			const actionButtons = card.querySelectorAll('.task-button-group .btn');

			const taskButton = actionButtons[0];
			const evaluationButton = actionButtons[1];
			const surveyButton = actionButtons[2];

			const isTaskSubmitted = taskStatus?.classList.contains('status-submitted');
			const isTaskUnstartedOrEditing = taskStatus?.classList.contains('status-unstarted') || taskStatus?.classList.contains('status-editing');
			const isEvaluationUnrated = evaluationStatus?.classList.contains('status-unrated');
			const isSurveyCompleted = surveyStatus?.classList.contains('status-completed');

			setActionDisabled(taskButton, Boolean(isTaskSubmitted));
			setActionDisabled(evaluationButton, Boolean(isTaskUnstartedOrEditing));
			setActionDisabled(surveyButton, Boolean(isEvaluationUnrated || isSurveyCompleted));
		});
	};

	if (headerPlaceholder && header) {
		headerPlaceholder.innerHTML = header;
	}

	if (footerPlaceholder && footer) {
		footerPlaceholder.innerHTML = footer;
	}

	if (homeMessage && submittedMessage) {
		homeMessage.classList.remove('d-none');
		homeMessage.firstElementChild.textContent = submittedMessage;

		if (homeMessageTime && submittedTime) {
			homeMessageTime.textContent = `提出日時: ${submittedTime}`;
		}

		window.sessionStorage.removeItem('ppe-home-message');
		window.sessionStorage.removeItem('ppe-home-message-time');
	}

	// Temporary: keep all action buttons navigable regardless of status.
});