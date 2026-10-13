(() => {
	const filters = Array.from(document.querySelectorAll("[data-reason-filter]"));
	const cards = Array.from(document.querySelectorAll(".reason-card[data-category]"));
	if (filters.length === 0 || cards.length === 0) return;

	filters.forEach(button => {
		button.addEventListener("click", () => {
			const category = button.dataset.reasonFilter;
			filters.forEach(filter => {
				const selected = filter === button;
				filter.classList.toggle("is-active", selected);
				filter.setAttribute("aria-pressed", String(selected));
			});
			cards.forEach(card => {
				const hidden = category !== "all" && card.dataset.category !== category;
				card.classList.toggle("is-hidden", hidden);
				card.setAttribute("aria-hidden", String(hidden));
			});
		});
	});
})();
