const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

function publicationFixture() {
  const fixturePath = path.resolve(__dirname, '../../build/phase20-publication-browser-fixture.properties');
  const values = Object.fromEntries(fs.readFileSync(fixturePath, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=');
      return [line.slice(0, separator), line.slice(separator + 1)];
    }));
  for (const key of ['taskId', 'assignmentId', 'studentLoginId']) {
    if (!values[key]) throw new Error(`Publication fixture is missing ${key}.`);
  }
  return values;
}

test('teacher publishes a configured draft and the assigned student can open it', async ({ browser }) => {
  test.setTimeout(120_000);
  const fixture = publicationFixture();
  const taskTitle = 'Phase 20 publication browser acceptance';
  const teacherLogin = requiredEnv('PPE_P20_TEACHER_LOGIN');
  const teacherPassword = requiredEnv('PPE_P20_TEACHER_PASSWORD');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  const teacherContext = await browser.newContext();
  const studentContext = await browser.newContext();
  const teacherPage = await teacherContext.newPage();
  const studentPage = await studentContext.newPage();

  try {
    await assertRejectedLogin(teacherPage, '/teacher/account/login', teacherLogin, 'invalid-synthetic-password');
    await login(teacherPage, '/teacher/account/login', teacherLogin, teacherPassword, '/teacher/task');

    const draftRow = teacherPage.locator('#taskTable tbody tr').filter({ hasText: taskTitle });
    await expect(draftRow).toHaveCount(1);
    await expect(draftRow).toContainText('下書き');
    await draftRow.locator(`a[href="/teacher/task?taskId=${fixture.taskId}"]`).click();
    await expect(teacherPage.locator('#taskCreateForm input[name="taskId"]')).toHaveValue(fixture.taskId);

    const publishImmediately = teacherPage.locator('.class-schedule-publish-immediate');
    await expect(publishImmediately).toHaveCount(1);
    await publishImmediately.check();
    await teacherPage.locator('#publishTaskButton').click();
    const confirmPublish = teacherPage.locator('[data-feedback-confirm]');
    await expect(confirmPublish).toBeVisible();
    await confirmPublish.click();

    await expect(teacherPage).toHaveURL(/\/teacher\/task(?:\?|$)/);
    const publishedRow = teacherPage.locator('#taskTable tbody tr').filter({ hasText: taskTitle });
    await expect(publishedRow).toContainText('公開中');

    await assertRejectedLogin(
      studentPage, '/student/account/login', fixture.studentLoginId, 'invalid-synthetic-password');
    await login(studentPage, '/student/account/login', fixture.studentLoginId, studentPassword, '/student/home');
    const studentTask = studentPage.locator('.task-list-card').filter({ hasText: taskTitle });
    await expect(studentTask).toHaveCount(1);
    const editorLink = studentTask.getByRole('link', { name: '課題に取り組む' });
    await expect(editorLink).toHaveAttribute('href', new RegExp(`assignmentId=${fixture.assignmentId}(?:$|&)`));
    await editorLink.click();
    await expect(studentPage).toHaveURL(new RegExp(`/student/editor\\?assignmentId=${fixture.assignmentId}(?:$|&)`));
    await expect(studentPage.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'true');
  } finally {
    await Promise.all([teacherContext.close(), studentContext.close()]);
  }
});
