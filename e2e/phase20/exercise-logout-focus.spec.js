const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('exercise logout cancellations retain code, session and keyboard focus without aria warnings', async ({ page }) => {
  const username = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const password = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  const warnings = [];
  page.on('console', message => {
    if (/aria-hidden|Blocked.*focus|focus.*hidden/i.test(message.text())) {
      warnings.push(message.text());
    }
  });
  await assertRejectedLogin(page, '/student/account/login', username, 'invalid-synthetic-password');
  await login(page, '/student/account/login', username, password, '/student/home');
  await page.goto('/student/exercise');
  await page.getByRole('button', { name: '基礎', exact: true }).click();
  await page.getByRole('button', { name: 'hello.py', exact: true }).click();
  const editor = page.locator('#studentExercisePage .CodeMirror');
  await expect(editor).toContainText("print('layout fixture')");
  await editor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText("print('unsaved logout acceptance')");
  const mutations = [];
  page.on('request', request => {
    if (request.method() === 'POST') mutations.push(new URL(request.url()).pathname);
  });
  const logout = page.locator('form[action$="/auth/logout"] button[type="submit"]');
  const userMenu = page.locator('#studentUserDropdown');
  const dialog = page.locator('#ppe-feedback-modal');
  await userMenu.click();
  await logout.click();
  await expect(dialog).toContainText('未保存の変更');
  await logout.locator('..').evaluate(form => form.requestSubmit());
  await expect(page.locator('.modal.show')).toHaveCount(1);
  await dialog.locator('[data-feedback-cancel]').click();
  await expect(dialog).toBeHidden();
  await expect(userMenu).toBeFocused();
  await expect(editor).toContainText("print('unsaved logout acceptance')");

  await page.keyboard.press('Enter');
  await expect(logout).toBeVisible();
  await logout.focus();
  await page.keyboard.press('Enter');
  await expect(dialog).toBeVisible();
  await expect(dialog).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(userMenu).toBeFocused();

  await page.keyboard.press('Enter');
  await expect(logout).toBeVisible();
  await logout.focus();
  await page.keyboard.press('Enter');
  await expect(dialog).toContainText('未保存の変更');
  await dialog.locator('[data-feedback-confirm]').click();
  await expect(dialog).toContainText('ログアウトしますか？');
  await dialog.locator('[data-feedback-cancel]').click();
  await expect(dialog).toBeHidden();
  await expect(userMenu).toBeFocused();
  await expect(editor).toContainText("print('unsaved logout acceptance')");
  expect(mutations).toEqual([]);
  expect(warnings).toEqual([]);

  page.once('dialog', nativeDialog => nativeDialog.accept());
  await page.reload();
  await expect(page.locator('#studentExercisePage')).toBeVisible();
  await expect(editor).toContainText("print('layout fixture')");
  await expect(editor).not.toContainText('unsaved logout acceptance');
  await userMenu.click();
  await logout.click();
  await expect(dialog).toContainText('ログアウトしますか？');
  await dialog.locator('[data-feedback-confirm]').click();
  await expect(page).toHaveURL(/\/student\/account\/login(?:$|\?)/);
  await page.goto('/student/exercise');
  await expect(page).toHaveURL(/\/student\/account\/login(?:$|\?)/);
  expect(mutations).toEqual(['/auth/logout']);
  expect(warnings).toEqual([]);
});
