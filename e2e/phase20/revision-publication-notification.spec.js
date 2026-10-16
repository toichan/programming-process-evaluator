const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

function revisionPublicationFixture() {
  const fixturePath = path.resolve(
    __dirname,
    '../../build/phase20-revision-notification-browser-fixture.properties'
  );
  const values = Object.fromEntries(fs.readFileSync(fixturePath, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=');
      return [line.slice(0, separator), line.slice(separator + 1)];
    }));
  for (const key of [
    'sourceTaskId',
    'oldAssignmentId',
    'revisionTaskId',
    'assignmentId',
    'revisionTitle',
    'studentLoginId'
  ]) {
    if (!values[key]) throw new Error(`Revision publication fixture is missing ${key}.`);
  }
  return values;
}

test('revised publication notifies the student and opens the new revision', async ({ browser }) => {
  test.setTimeout(120_000);
  const fixture = revisionPublicationFixture();
  const teacherBaseUrl = requiredEnv('PPE_P20_TEACHER_BASE_URL').replace(/\/$/, '');
  const teacherContext = await browser.newContext();
  const studentContext = await browser.newContext();
  const teacherPage = await teacherContext.newPage();
  const studentPage = await studentContext.newPage();

  try {
    await assertRejectedLogin(
      teacherPage,
      `${teacherBaseUrl}/teacher/account/login`,
      'teacher-demo',
      'invalid-synthetic-password');
    await login(
      teacherPage,
      `${teacherBaseUrl}/teacher/account/login`,
      'teacher-demo',
      requiredEnv('PPE_P20_REVISION_TEACHER_PASSWORD'),
      '/teacher/task');

    const originalTask = teacherPage.locator('#taskTable tbody tr')
      .filter({ hasText: 'Phase 20 revision notification source' });
    const revisedTask = teacherPage.locator('#taskTable tbody tr')
      .filter({ hasText: fixture.revisionTitle });
    await expect(originalTask).toHaveCount(1);
    await expect(originalTask).toContainText('要更新');
    await expect(revisedTask).toHaveCount(1);
    await expect(revisedTask).toContainText('公開中');

    await assertRejectedLogin(
      studentPage,
      '/student/account/login',
      fixture.studentLoginId,
      'invalid-synthetic-password');
    await login(
      studentPage,
      '/student/account/login',
      fixture.studentLoginId,
      requiredEnv('PPE_P20_REVISION_STUDENT_PASSWORD'),
      '/student/home');
    await studentPage.goto('/student/notifications');

    const revisionNotification = studentPage.locator('.notification-list [data-notification-open]')
      .filter({ hasText: fixture.revisionTitle })
      .filter({ hasText: '改訂版' });
    await expect(revisionNotification).toHaveCount(1);
    await expect(revisionNotification).toContainText('未読');
    await expect(revisionNotification).toContainText('新しい改訂版');
    await revisionNotification.locator('button[type="submit"]').click();
    await expect(studentPage).toHaveURL(
      new RegExp(`/student/editor\\?assignmentId=${fixture.assignmentId}(?:$|&)`));
    await expect(studentPage.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'true');
    await expect(studentPage.locator('#studentEditorPage')).toContainText(fixture.revisionTitle);

    await studentPage.goto('/student/notifications');
    const readRevisionNotification = studentPage.locator('.notification-list [data-notification-open]')
      .filter({ hasText: fixture.revisionTitle })
      .filter({ hasText: '改訂版' });
    await expect(readRevisionNotification).toHaveCount(1);
    await expect(readRevisionNotification).toContainText('既読');
  } finally {
    await Promise.all([teacherContext.close(), studentContext.close()]);
  }
});
