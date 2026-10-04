window.PPEEditorSettings = {
  bind({ root, preferences: initialPreferences, preferencesUrl, csrfToken, onChange, onError }) {
    window.PPECodeEditor.validatePreferences(initialPreferences);
    let preferences = { ...initialPreferences };
    const font = root.querySelector('#editorFontSize');
    const fontValue = root.querySelector('#editorFontSizeValue');
    const wrapping = root.querySelector('#editorLineWrapping');
    const indent = root.querySelector('#editorIndentWidth');
    const theme = root.querySelector('#editorTheme');
    const status = root.querySelector('#editorPreferencesStatus');
    let timer = null;
    let sequence = 0;
    let queue = Promise.resolve();
    function apply() {
      font.value = String(preferences.fontSizePx);
      fontValue.textContent = `${preferences.fontSizePx}px`;
      wrapping.checked = preferences.lineWrapping;
      indent.value = String(preferences.indentWidth);
      theme.value = preferences.theme;
    }
    function persist() {
      const current = ++sequence;
      const snapshot = { ...preferences };
      const save = async () => {
        try {
          const response = await fetch(preferencesUrl, {
            method: 'POST', credentials: 'same-origin', cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: new URLSearchParams({
              csrfToken,
              fontSizePx: String(snapshot.fontSizePx), lineWrapping: String(snapshot.lineWrapping),
              indentWidth: String(snapshot.indentWidth), theme: snapshot.theme
            })
          });
          if (!(response.headers.get('content-type') || '').includes('application/json')) {
            throw new Error('設定の保存時に予期しない応答が返されました。');
          }
          const result = await response.json();
          if (!response.ok) throw new Error(result.message || '設定を保存できませんでした。');
          if (current === sequence) status.textContent = '設定を保存しました。別の端末でも使用できます。';
        } catch (error) {
          if (current === sequence) {
            status.textContent = '設定を保存できませんでした。';
            onError(error.message);
          }
        }
      };
      queue = queue.then(save, save);
    }
    function changed() {
      window.PPECodeEditor.validatePreferences(preferences);
      onChange({ ...preferences });
      apply();
      status.textContent = '設定を保存しています…';
      window.clearTimeout(timer);
      timer = window.setTimeout(() => { timer = null; persist(); }, 300);
    }
    font.addEventListener('input', () => { preferences.fontSizePx = Number(font.value); changed(); });
    wrapping.addEventListener('change', () => { preferences.lineWrapping = wrapping.checked; changed(); });
    indent.addEventListener('change', () => { preferences.indentWidth = Number(indent.value); changed(); });
    theme.addEventListener('change', () => { preferences.theme = theme.value; changed(); });
    root.querySelector('#resetEditorPreferences').addEventListener('click', () => {
      preferences = window.PPECodeEditor.defaults(); changed();
    });
    root.addEventListener('hide.bs.modal', () => {
      if (timer !== null) { window.clearTimeout(timer); timer = null; persist(); }
    });
    apply();
  }
};
