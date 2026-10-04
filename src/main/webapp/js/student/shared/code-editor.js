window.PPECodeEditor = {
  defaults() {
    return { fontSizePx: 16, lineWrapping: false, indentWidth: 4, theme: 'dark' };
  },
  readPreferences(data, onError) {
    const preferences = {
      fontSizePx: Number(data.editorFontSizePx),
      lineWrapping: data.editorLineWrapping === 'true',
      indentWidth: Number(data.editorIndentWidth),
      theme: data.editorTheme
    };
    try {
      this.validatePreferences(preferences);
      return preferences;
    } catch (error) {
      onError('保存済み設定を読み取れません。初期設定で表示しています。');
      return this.defaults();
    }
  },
  validatePreferences(preferences) {
    if (!Number.isInteger(preferences.fontSizePx) || preferences.fontSizePx < 10
      || preferences.fontSizePx > 24 || typeof preferences.lineWrapping !== 'boolean'
      || ![2, 4].includes(preferences.indentWidth)
      || !['dark', 'light', 'high_contrast'].includes(preferences.theme)) {
      throw new Error('エディター設定の値が正しくありません。');
    }
  },
  applyPreferences(editor, preferences) {
    this.validatePreferences(preferences);
    const themes = { dark: 'material-darker', light: 'default', high_contrast: 'ppe-high-contrast' };
    editor.setOption('lineWrapping', preferences.lineWrapping);
    editor.setOption('indentUnit', preferences.indentWidth);
    editor.setOption('tabSize', preferences.indentWidth);
    editor.setOption('theme', themes[preferences.theme]);
    editor.getWrapperElement().style.fontSize = `${preferences.fontSizePx}px`;
    editor.refresh();
  },
  create({ textarea, preferences, options = {} }) {
    this.validatePreferences(preferences);
    const editor = CodeMirror.fromTextArea(textarea, {
      mode: 'python', lineNumbers: true, ...options
    });
    this.applyPreferences(editor, preferences);
    return editor;
  }
};
