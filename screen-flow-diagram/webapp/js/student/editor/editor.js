window.addEventListener('DOMContentLoaded', () => {
  const { header, footer } = window.PPEComponents || {};
  const headerPlaceholder = document.querySelector('#header-placeholder');
  const footerPlaceholder = document.querySelector('#footer-placeholder');
  const codeEditor = document.querySelector('#codeEditor');
  const outputConsole = document.querySelector('#outputConsole');
  const editorMessage = document.querySelector('#editorMessage');
  const lastSavedAt = document.querySelector('#lastSavedAt');
  const codeLogList = document.querySelector('#codeLogList');
  const saveButton = document.querySelector('#saveButton');
  const runButton = document.querySelector('#runButton');
  const infoTabs = document.querySelectorAll('[data-panel-target]');

  if (headerPlaceholder && header) {
    headerPlaceholder.innerHTML = header;
  }

  if (footerPlaceholder && footer) {
    footerPlaceholder.innerHTML = footer;
  }

  function nowTimeLabel() {
    return new Date().toLocaleTimeString('ja-JP', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  function prependLog(title, text) {
    if (!codeLogList) {
      return;
    }

    const item = document.createElement('article');
    item.className = 'code-log-item';
    item.innerHTML = `
      <div class="log-time">${nowTimeLabel()}</div>
      <div class="log-body">
        <div class="log-title">${title}</div>
        <p class="log-text mb-0">${text}</p>
      </div>
    `;
    codeLogList.prepend(item);
  }

  function markSaved(prefix) {
    const time = nowTimeLabel();
    if (lastSavedAt) {
      lastSavedAt.textContent = time;
    }
    if (editorMessage) {
      editorMessage.textContent = `${prefix}しました。コードログへ記録しています。`;
    }
  }

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      markSaved('保存');
      prependLog('手動保存', '現在のコードスナップショットを保存しました。');
    });
  }

  if (runButton) {
    runButton.addEventListener('click', () => {
      const hasInput = codeEditor?.value.includes('input(');
      if (outputConsole) {
        outputConsole.textContent = hasInput
          ? '入力待ちの処理が含まれています。\nテスト用入力: パー\n\n実行結果:\nあなたの勝ち'
          : '実行しました。\n\n標準出力:\n(ここに結果が表示されます)';
      }
      if (editorMessage) {
        editorMessage.textContent = '実行が完了しました。エラーがあればこの下に表示されます。';
      }
      prependLog('実行', '実行結果を出力パネルへ反映しました。');
    });
  }

  infoTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const targetId = tab.getAttribute('data-panel-target');
      infoTabs.forEach((button) => button.classList.remove('active'));
      document.querySelectorAll('.info-panel').forEach((panel) => panel.classList.remove('is-active'));
      tab.classList.add('active');
      document.getElementById(targetId)?.classList.add('is-active');
    });
  });

  window.setInterval(() => {
    markSaved('自動保存');
    prependLog('自動保存', '30秒経過によりコードを自動保存しました。');
  }, 30000);
});