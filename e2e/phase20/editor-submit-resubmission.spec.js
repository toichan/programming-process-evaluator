const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv, setEditorCode } = require('./helpers');

test('student submits and resubmits through the editor with persisted revisions', async ({ page }) => {
  const studentLogin = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  const assignmentId = requiredEnv('PPE_P20_ASSIGNMENT_ID');
  const editorPath = `/student/editor?assignmentId=${encodeURIComponent(assignmentId)}`;
  await assertRejectedLogin(page, '/student/account/login', studentLogin, 'invalid-synthetic-password');
  await login(page, '/student/account/login', studentLogin, studentPassword, '/student/home');

  await page.goto(editorPath);
  await expect(page.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'true');
  await setEditorCode(page, 'print("test output")\n# phase20 revision one');
  await page.locator('#submitButton').click();
  await expect(page.locator('#submitCheckModal')).toBeVisible();
  await expect(page.locator('#submitCheckSummary')).toContainText('1/1');
  await page.locator('#confirmSubmitAfterCheck').click();
  await expect(page).toHaveURL(new RegExp(`/student/evaluation\\?assignmentId=${assignmentId}&submissionId=\\d+`));
  await expect(page.locator('.meta-subtext')).toContainText('提出版 v1');

  await page.goto(editorPath);
  await expect(page.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'false');
  const startResubmission = page.locator('#startResubmissionButton');
  await expect(startResubmission).toBeVisible();
  await Promise.all([page.waitForNavigation(), startResubmission.click()]);
  await expect(page.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'true');

  await setEditorCode(page, 'print("test output")\n# phase20 revision two');
  await page.locator('#submitButton').click();
  await expect(page.locator('#submitCheckModal')).toBeVisible();
  await expect(page.locator('#submitCheckSummary')).toContainText('1/1');
  await page.locator('#confirmSubmitAfterCheck').click();
  await expect(page).toHaveURL(new RegExp(`/student/evaluation\\?assignmentId=${assignmentId}&submissionId=\\d+`));
  await expect(page.locator('.meta-subtext')).toContainText('提出版 v2');

  await page.goto(editorPath);
  await expect(page.locator('#studentEditorPage')).toHaveAttribute('data-editable', 'false');
  await expect(page.locator('.hero-status-card')).toContainText('第2版');
});
