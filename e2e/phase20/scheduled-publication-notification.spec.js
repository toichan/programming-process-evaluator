const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

function scheduledPublicationFixture() {
  const fixturePath = path.resolve(
    __dirname,
    '../../build/phase20-scheduled-publication-browser-fixture.properties'
  );
  const values = Object.fromEntries(fs.readFileSync(fixturePath, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=');
      return [line.slice(0, separator), line.slice(separator + 1)];
    }));
  for (const key of ['taskId', 'assignmentId', 'studentLoginId']) {
    if (!values[key]) throw new Error(`Scheduled publication fixture is missing ${key}.`);
  }
  return values;
}

test('scheduled publication creates a student notification that opens the assigned editor', async ({ page }) => {
  test.setTimeout(120_000);
  const fixture = scheduledPublicationFixture();
  const studentPassword = requiredEnv('PPE_P20_SCHEDULED_STUDENT_PASSWORD');
  const taskTitle = 'Phase 20 scheduled publication notification acceptance';

  await assertRejectedLogin(
    page, '/student/account/login', fixture.studentLoginId, 'invalid-synthetic-password');
  await login(
    page, '/student/account/login', fixture.studentLoginId, studentPassword, '/student/home');
  await page.goto('/student/notifications');

  const notification = page.locator('.notification-list [data-notification-open]')
    .filter({ hasText: taskTitle });
  await expect(notification).toHaveCount(1, { timeout: 90_000 });
  await expect(notification).toContainText('が公開されました');
  await expect(notification).toContainText('未読');
  await expect(notification.locator('input[name="notificationId"]')).toHaveAttribute('value', /\d+/);

  await notification.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(
    new RegExp(`/student/editor\\?assignmentId=${fixture.assignmentId}(?:$|&)`));
  await expect(page.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'true');

  await page.goto('/student/notifications');
  const readNotification = page.locator('.notification-list [data-notification-open]')
    .filter({ hasText: taskTitle });
  await expect(readNotification).toHaveCount(1);
  await expect(readNotification).toContainText('既読');
});
