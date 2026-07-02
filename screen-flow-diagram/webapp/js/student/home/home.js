window.addEventListener('DOMContentLoaded', () => {
	const { header, footer } = window.PPEComponents || {};
	const feedback = window.PPEFeedback || {};
	const headerPlaceholder = document.querySelector('#header-placeholder');
	const footerPlaceholder = document.querySelector('#footer-placeholder');
	const homeMessage = document.querySelector('#homeMessage');
	const homeMessageTime = document.querySelector('#homeMessageTime');
	const submittedMessage = window.sessionStorage.getItem('ppe-home-message');
	const submittedTime = window.sessionStorage.getItem('ppe-home-message-time');
	const submittedTitle = window.sessionStorage.getItem('ppe-home-message-title');

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

	const syncTaskDeadlines = () => {
		const taskCards = document.querySelectorAll('.task-list-card[data-due-at]');

		const formatDueDateTime = (date) => {
			const month = date.getMonth() + 1;
			const day = date.getDate();
			const hours = String(date.getHours()).padStart(2, '0');
			const minutes = String(date.getMinutes()).padStart(2, '0');
			return `${month}月${day}日 ${hours}:${minutes}`;
		};

		taskCards.forEach((card) => {
			const dueDateRaw = card.getAttribute('data-due-at');
			const dueDateText = card.querySelector('[data-task-deadline-date]');
			const dueDateRemaining = card.querySelector('[data-task-deadline-remaining]');

			if (!dueDateRaw || !dueDateText || !dueDateRemaining) {
				return;
			}

			const dueDate = new Date(dueDateRaw);
			if (Number.isNaN(dueDate.getTime())) {
				return;
			}

			dueDateText.textContent = formatDueDateTime(dueDate);

			const now = new Date();
			const remainingDays = Math.max(0, Math.ceil((dueDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)));
			dueDateRemaining.textContent = `残り${remainingDays}日`;
			dueDateRemaining.classList.remove('is-soon', 'is-urgent');

			if (remainingDays <= 3) {
				dueDateRemaining.classList.add('is-urgent');
			} else if (remainingDays <= 7) {
				dueDateRemaining.classList.add('is-soon');
			}
		});
	};

	if (headerPlaceholder && header) {
		headerPlaceholder.innerHTML = header;
	}

	if (footerPlaceholder && footer) {
		footerPlaceholder.innerHTML = footer;
	}

	if (homeMessage && submittedMessage) {
		if (typeof feedback.showToast === 'function') {
			const toastMessage = submittedTime
				? `${submittedMessage} (${submittedTime})`
				: submittedMessage;
			feedback.showToast({
				title: submittedTitle || 'お知らせ',
				message: toastMessage,
				variant: 'success',
				delay: 3200
			});
		} else {
			homeMessage.classList.remove('d-none');
			homeMessage.firstElementChild.textContent = submittedMessage;

			if (homeMessageTime && submittedTime) {
				homeMessageTime.textContent = `提出日時: ${submittedTime}`;
			}
		}

		window.sessionStorage.removeItem('ppe-home-message');
		window.sessionStorage.removeItem('ppe-home-message-time');
		window.sessionStorage.removeItem('ppe-home-message-title');
	}

	syncTaskDeadlines();
	// Temporary: keep all action buttons navigable regardless of status.
});