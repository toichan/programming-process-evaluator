const { expect } = require('@playwright/test');

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Required browser-test environment variable is missing: ${name}`);
  }
  return value;
}

async function assertRejectedLogin(page, loginPath, loginId, password) {
  await page.goto(loginPath);
  await page.locator('input[name="loginId"]').fill(loginId);
  await page.locator('input[name="password"]').fill(password);
  await page.locator('form button[type="submit"]').click();
  await expect(page.locator('[data-auth-error]')).toHaveAttribute('data-auth-error', /.+/);
}

async function login(page, loginPath, loginId, password, expectedPath) {
  await page.goto(loginPath);
  await page.locator('input[name="loginId"]').fill(loginId);
  await page.locator('input[name="password"]').fill(password);
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(new RegExp(`${expectedPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?:$|\\?)`));
}

async function setEditorCode(page, source) {
  const editor = page.locator('#studentEditorPage .editor-shell .CodeMirror');
  await expect(editor).toHaveCount(1);
  await expect(editor).toBeVisible();
  await editor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText(source);
}

module.exports = {
  assertRejectedLogin,
  login,
  requiredEnv,
  setEditorCode
};
