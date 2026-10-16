const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv, setEditorCode } = require('./helpers');

test('stale second editor cannot overwrite the first editor persisted draft', async ({ browser }) => {
  const studentLogin = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  const assignmentId = requiredEnv('PPE_P20_ASSIGNMENT_ID');
  const firstContext = await browser.newContext();
  const secondContext = await browser.newContext();
  const firstPage = await firstContext.newPage();
  const secondPage = await secondContext.newPage();

  try {
    await assertRejectedLogin(firstPage, '/student/account/login', studentLogin, 'invalid-synthetic-password');
    await login(firstPage, '/student/account/login', studentLogin, studentPassword, '/student/home');
    await login(secondPage, '/student/account/login', studentLogin, studentPassword, '/student/home');
    const editorPath = `/student/editor?assignmentId=${encodeURIComponent(assignmentId)}`;
    await firstPage.goto(editorPath);
    await secondPage.goto(editorPath);

    const firstSource = `print("phase20-first-${Date.now()}")`;
    await setEditorCode(firstPage, firstSource);
    const firstSave = firstPage.waitForResponse((response) =>
      response.url().includes('/student/editor/draft')
        && response.request().postData()?.includes('manual_save')
    );
    await firstPage.locator('#saveButton').click();
    expect((await firstSave).ok()).toBe(true);

    const staleSource = `print("phase20-stale-${Date.now()}")`;
    await setEditorCode(secondPage, staleSource);
    const staleSave = secondPage.waitForResponse((response) =>
      response.url().includes('/student/editor/draft')
        && response.request().postData()?.includes('manual_save')
    );
    await secondPage.locator('#saveButton').click();
    const staleResponse = await staleSave;
    expect(staleResponse.status()).toBe(409);
    await expect(secondPage.locator('#editorFeedback')).toContainText(/競合|再読み込み|最新/);

    await firstPage.reload();
    await expect.poll(() => firstPage.locator('#codeEditor').inputValue()).toBe(firstSource);
  } finally {
    await firstContext.close();
    await secondContext.close();
  }
});
