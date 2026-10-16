const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const { login, requiredEnv } = require('./helpers');

test('expired deny notification returns the student home and the editor URL stays unavailable', async ({ page }) => {
  const studentLogin = requiredEnv('PPE_P20_EXPIRED_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_EXPIRED_STUDENT_PASSWORD');
  const fixtureProperties = fs.readFileSync(
    path.resolve(__dirname, '../../build/phase20-browser-fixture.properties'),
    'utf8'
  );
  const assignmentId = fixtureProperties.match(/^assignmentId=(\d+)$/m)?.[1];
  expect(assignmentId).toBeTruthy();

  await login(page, '/student/account/login', studentLogin, studentPassword, '/student/home');
  await page.goto('/student/notifications');
  const notification = page.locator('.notification-list [data-notification-open]')
    .filter({ hasText: '提出期限を過ぎました' });
  await expect(notification).toHaveCount(1);
  await expect(notification).toContainText('期限後の提出はできません');

  await notification.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/student\/home#taskListSection$/);
  await expect(page.locator('#taskListSection')).toBeVisible();

  const editorResponse = await page.goto(
    `/student/editor?assignmentId=${encodeURIComponent(assignmentId)}`
  );
  expect(editorResponse.status()).toBe(404);
});
