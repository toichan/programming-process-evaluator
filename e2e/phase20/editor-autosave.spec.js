const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv, setEditorCode } = require('./helpers');

test('student draft is periodically saved after thirty seconds and survives reload', async ({ page }) => {
  const studentLogin = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  const assignmentId = requiredEnv('PPE_P20_ASSIGNMENT_ID');

  await assertRejectedLogin(page, '/student/account/login', studentLogin, 'invalid-synthetic-password');
  await login(page, '/student/account/login', studentLogin, studentPassword, '/student/home');
  await page.goto(`/student/editor?assignmentId=${encodeURIComponent(assignmentId)}`);
  await expect(page.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'true');

  const source = `print("phase20-periodic-${Date.now()}")`;
  const periodicSave = page.waitForResponse((response) =>
    response.url().includes('/student/editor/draft')
      && response.request().postData()?.includes('periodic_snapshot')
      && response.ok(),
    { timeout: 45_000 }
  );
  await setEditorCode(page, source);
  await periodicSave;
  await expect(page.locator('#editorMessage')).toContainText('保存状態: 保存済み');

  await page.reload();
  await expect.poll(() => page.locator('#codeEditor').inputValue()).toBe(source);
});
