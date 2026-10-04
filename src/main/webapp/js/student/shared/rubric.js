window.addEventListener('DOMContentLoaded', () => {
  const modal = document.getElementById('rubricModal');
  if (!modal) return;
  const content = document.getElementById('rubricContent');
  const loading = document.getElementById('rubricLoading');
  const retry = document.getElementById('rubricRetry');
  const feedback = window.PPEFeedback.createPageFeedback({ title: 'ルーブリック', alertTarget: '#rubricFeedback' });
  let controller = null;
  let trigger = null;

  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  function validate(rubric) {
    if (rubric?.version !== '0805-2026-v1' || !Array.isArray(rubric.dimensions)
      || rubric.dimensions.length !== 2) throw new Error('取得したルーブリックの形式が正しくありません。');
    rubric.dimensions.forEach((dimension, index) => {
      const code = index === 0 ? 'thinking' : 'attitude';
      if (dimension.code !== code || typeof dimension.label !== 'string' || !dimension.label.trim()
        || !Array.isArray(dimension.criteria) || dimension.criteria.length !== (index === 0 ? 4 : 2)) {
        throw new Error('取得したルーブリックの観点が不完全です。');
      }
      dimension.criteria.forEach((criterion) => {
        if (typeof criterion.name !== 'string' || !criterion.name.trim()
          || !Array.isArray(criterion.levels) || criterion.levels.length !== 5) {
          throw new Error('取得したルーブリックの評価基準が不完全です。');
        }
        criterion.levels.forEach((level, i) => {
          if (level.value !== 5 - i || level.label !== `レベル${5 - i}`
            || typeof level.description !== 'string' || !level.description.trim()) {
            throw new Error('取得したルーブリックの段階説明が不完全です。');
          }
        });
      });
    });
  }
  function render(rubric) {
    const sections = rubric.dimensions.map((dimension) => {
      const section = element('section', undefined,
        `sample-section rubric-section rubric-${dimension.code} mb-4`);
      section.append(element('h2', `${dimension.label} ルーブリック`, 'section-title mb-4'));
      const wrapper = element('div', undefined, 'table-responsive');
      const table = element('table', undefined, 'table rubric-table table-bordered align-middle mb-0');
      const head = element('thead');
      const header = element('tr');
      const first = element('th', 'レベル');
      first.scope = 'col'; first.style.width = '10rem'; header.append(first);
      dimension.criteria.forEach((criterion) => {
        const th = element('th', criterion.name); th.scope = 'col'; header.append(th);
      });
      head.append(header); table.append(head);
      const body = element('tbody');
      for (let i = 0; i < 5; i++) {
        const row = element('tr');
        const label = element('th', dimension.criteria[0].levels[i].label);
        label.scope = 'row'; row.append(label);
        dimension.criteria.forEach((criterion) => row.append(element('td', criterion.levels[i].description)));
        body.append(row);
      }
      table.append(body); wrapper.append(table); section.append(wrapper);
      return section;
    });
    content.replaceChildren(...sections);
  }
  async function load() {
    if (document.activeElement === retry) modal.focus({ preventScroll: true });
    controller?.abort();
    const current = new AbortController();
    controller = current;
    content.replaceChildren(); feedback.clearInlineAlert();
    loading.hidden = false; retry.hidden = true;
    try {
      const response = await fetch(modal.dataset.rubricUrl, {
        credentials: 'same-origin', cache: 'no-store', signal: current.signal
      });
      if (!(response.headers.get('content-type') || '').includes('application/json')) {
        throw new Error('ログイン状態を確認してから、もう一度お試しください。');
      }
      const rubric = await response.json();
      if (!response.ok) throw new Error(rubric.message || 'ルーブリックを取得できませんでした。');
      validate(rubric);
      if (!current.signal.aborted) render(rubric);
    } catch (error) {
      if (current.signal.aborted) return;
      feedback.inlineAlert(error.message, 'danger'); retry.hidden = false;
    } finally {
      if (controller === current) loading.hidden = true;
    }
  }
  modal.addEventListener('show.bs.modal', (event) => {
    trigger = event.relatedTarget || document.activeElement;
    load();
  });
  modal.addEventListener('hide.bs.modal', () => {
    controller?.abort();
    if (modal.contains(document.activeElement)) document.activeElement.blur();
  });
  modal.addEventListener('hidden.bs.modal', () => {
    content.replaceChildren(); feedback.clearInlineAlert();
    if (trigger?.isConnected) trigger.focus({ preventScroll: true });
  });
  retry.addEventListener('click', load);
});
