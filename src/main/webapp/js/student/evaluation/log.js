(() => {
	const timeline = document.getElementById("logTimeline");
	if (!timeline) return;

	const items = Array.from(timeline.querySelectorAll(".timeline-item"));
	const logEntries = Array.from(document.querySelectorAll("#logData .log-data-entry"));
	const codeBlock = document.getElementById("codeBlock");
	const outputPanel = document.getElementById("executionOutput");
	const previousButton = document.getElementById("prevCodeButton");
	const nextButton = document.getElementById("nextCodeButton");
	const indicator = document.getElementById("stepIndicator");
	const boundaryMessage = document.getElementById("boundaryMessage");
	const requestedEvidenceStatus = document.getElementById("requestedEvidenceStatus");
	const reasonFilters = Array.from(document.querySelectorAll("[data-reason-filter]"));
	const reasonCards = Array.from(document.querySelectorAll("#reasonsPanel .reason-card[data-category]"));
	const requestedLogId = new URLSearchParams(window.location.search).get("logId");
	let selectedIndex = items.length - 1;

	const value = (entry, selector) => entry.querySelector(selector)?.textContent ?? "";

	function appendCodeLine(number, content, kind) {
		const line = document.createElement("span");
		line.className = `code-line${kind ? ` ${kind}` : ""}`;
		const lineNumber = document.createElement("span");
		lineNumber.className = "line-number";
		lineNumber.textContent = number === null ? "" : String(number);
		const lineContent = document.createElement("span");
		lineContent.className = "line-content";
		lineContent.textContent = content || " ";
		line.append(lineNumber, lineContent);
		codeBlock.append(line);
	}

	function diffLines(previous, current) {
		const oldLines = previous.split("\n");
		const newLines = current.split("\n");
		if (oldLines.length * newLines.length > 40000) {
			return null;
		}
		const table = Array.from({ length: oldLines.length + 1 }, () => new Uint16Array(newLines.length + 1));
		for (let oldIndex = oldLines.length - 1; oldIndex >= 0; oldIndex -= 1) {
			for (let newIndex = newLines.length - 1; newIndex >= 0; newIndex -= 1) {
				table[oldIndex][newIndex] = oldLines[oldIndex] === newLines[newIndex]
					? table[oldIndex + 1][newIndex + 1] + 1
					: Math.max(table[oldIndex + 1][newIndex], table[oldIndex][newIndex + 1]);
			}
		}

		const result = [];
		let oldIndex = 0;
		let newIndex = 0;
		while (oldIndex < oldLines.length || newIndex < newLines.length) {
			if (oldIndex < oldLines.length && newIndex < newLines.length
					&& oldLines[oldIndex] === newLines[newIndex]) {
				result.push({ text: newLines[newIndex], kind: "", number: newIndex + 1 });
				oldIndex += 1;
				newIndex += 1;
			} else if (oldIndex < oldLines.length
					&& (newIndex === newLines.length
						|| table[oldIndex + 1][newIndex] >= table[oldIndex][newIndex + 1])) {
				result.push({ text: oldLines[oldIndex], kind: "removed", number: null });
				oldIndex += 1;
			} else {
				result.push({ text: newLines[newIndex], kind: "added", number: newIndex + 1 });
				newIndex += 1;
			}
		}
		return result;
	}

	function renderOutput(entry) {
		outputPanel.replaceChildren();
		const status = entry.dataset.executionStatus;
		if (!status) {
			const message = document.createElement("p");
			message.className = "text-muted mb-0";
			message.textContent = "選択したソースコード編集ログに実行記録はありません。";
			outputPanel.append(message);
			return;
		}

		const statusLine = document.createElement("p");
		statusLine.className = "execution-output-status";
		statusLine.textContent = `実行状態: ${status}`;
		outputPanel.append(statusLine);
		[
			["stdin", "標準入力", ".log-standard-input"],
			["stdout", "標準出力", ".log-standard-output"],
			["stderr", "標準エラー", ".log-standard-error"],
		].forEach(([stream, label, selector]) => {
			const section = document.createElement("section");
			section.className = `execution-stream execution-stream-${stream}`;
			const heading = document.createElement("h4");
			heading.className = "execution-stream-heading";
			heading.textContent = label;
			const pre = document.createElement("pre");
			pre.className = "execution-stream-content";
			pre.textContent = value(entry, selector) || "（データなし）";
			section.append(heading, pre);
			outputPanel.append(section);
		});
	}

	function render(index) {
		selectedIndex = index;
		const entry = logEntries[index];
		const item = items[index];
		if (!entry || !item) return;

		items.forEach((timelineItem, itemIndex) => {
			const selected = itemIndex === index;
			timelineItem.classList.toggle("is-active", selected);
			timelineItem.setAttribute("aria-pressed", String(selected));
		});

		const snapshot = value(entry, ".log-snapshot");
		const previous = [...logEntries.slice(0, index)]
			.reverse()
			.find(candidate => value(candidate, ".log-snapshot") !== "");
		const previousSnapshot = previous ? value(previous, ".log-snapshot") : null;
		const changes = previousSnapshot === null ? null : diffLines(previousSnapshot, snapshot);
		codeBlock.replaceChildren();
		if (snapshot === "") {
			const message = document.createElement("span");
			message.textContent = "このソースコード編集ログにはコードスナップショットがありません。";
			codeBlock.append(message);
		} else if (changes) {
			changes.forEach(line => appendCodeLine(line.number, line.text, line.kind));
		} else {
			snapshot.split("\n").forEach((line, lineIndex) => appendCodeLine(lineIndex + 1, line, ""));
		}

		indicator.textContent = `${index + 1} / ${items.length}`;
		document.getElementById("sourceCodeId").textContent = `ソースコード編集ログID: ${entry.dataset.logId}`;
		document.getElementById("executionId").textContent = entry.dataset.executionId
			? `実行ID: ${entry.dataset.executionId}`
			: "実行ID: -";
		document.getElementById("timestamp").textContent =
			`${entry.dataset.observedAt} / ${entry.dataset.eventLabel}`;
		boundaryMessage.textContent = previousSnapshot === null
			? "この提出版で最初に記録されたコードです。"
			: changes === null
				? "コード量が大きいため差分を省略し、保存時点のコードを表示しています。"
				: "";
		renderOutput(entry);
		previousButton.disabled = index === 0;
		nextButton.disabled = index === items.length - 1;
	}

	if (items.length > 0) {
		items.forEach((item, index) => item.addEventListener("click", () => render(index)));
		previousButton.addEventListener("click", () => {
			if (selectedIndex > 0) render(selectedIndex - 1);
		});
		nextButton.addEventListener("click", () => {
			if (selectedIndex < items.length - 1) render(selectedIndex + 1);
		});
	}
	reasonFilters.forEach(button => {
		button.addEventListener("click", () => {
			const category = button.dataset.reasonFilter;
			reasonFilters.forEach(filter => {
				const selected = filter === button;
				filter.classList.toggle("is-active", selected);
				filter.setAttribute("aria-pressed", String(selected));
			});
			reasonCards.forEach(card => {
				const hidden = category !== "all" && card.dataset.category !== category;
				card.classList.toggle("is-hidden", hidden);
				card.setAttribute("aria-hidden", String(hidden));
			});
		});
	});
	document.querySelectorAll("[data-sidebar-panel]").forEach(button => {
		button.addEventListener("click", () => {
			document.querySelectorAll("[data-sidebar-panel]").forEach(tab => {
				tab.classList.toggle("is-active", tab === button);
				tab.setAttribute("aria-selected", String(tab === button));
			});
			document.querySelectorAll(".sidebar-panel").forEach(panel => {
				panel.classList.toggle("is-active", panel.id === `${button.dataset.sidebarPanel}Panel`);
			});
		});
	});

	if (items.length > 0) render(selectedIndex);
	if (requestedLogId !== null && requestedEvidenceStatus) {
		const requestedIndex = logEntries.findIndex(entry => entry.dataset.logId === requestedLogId);
		if (requestedIndex >= 0 && requestedIndex < items.length) {
			render(requestedIndex);
			requestedEvidenceStatus.textContent = `保存済み根拠のソースコード編集ログID ${requestedLogId} を表示しています。`;
		} else {
			requestedEvidenceStatus.textContent = "指定されたログはこの提出版の認可済み一覧にありません。別の提出版やログを推測して表示していません。";
		}
	}
})();
