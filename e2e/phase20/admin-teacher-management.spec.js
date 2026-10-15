const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

function adminFixture() {
  const fixturePath = path.resolve(__dirname, '../../build/phase20-admin-management-browser-fixture.properties');
  const values = Object.fromEntries(fs.readFileSync(fixturePath, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=');
      return [line.slice(0, separator), line.slice(separator + 1)];
    }));
  if (!values.schoolId) throw new Error('Admin fixture is missing schoolId.');
  return values;
}

test('admin manages teacher lifecycle and prevents access after suspension and deletion', async ({ page }) => {
  test.setTimeout(120_000);
  const fixture = adminFixture();
  const adminPassword = requiredEnv('PPE_P20_ADMIN_PASSWORD');
  const teacherLoginId = `p20-admin-created-teacher-${Date.now()}`;

  await assertRejectedLogin(page, '/teacher/account/login', 'admin', 'invalid-synthetic-password');
  await login(page, '/teacher/account/login', 'admin', adminPassword, '/admin/teachers');
  await expect(page.locator('#adminConsole')).toContainText('管理者: admin');

  const adminSession = (await page.context().cookies())
    .find(cookie => cookie.name === 'JSESSIONID');
  expect(adminSession).toBeTruthy();
  await page.context().addCookies([{
    name: adminSession.name,
    value: adminSession.value,
    url: 'http://student.localhost:8080'
  }]);
  const deniedStudentRoute = await page.goto('http://student.localhost:8080/student/home');
  expect(deniedStudentRoute.status()).toBe(403);
  await page.context().addCookies([{
    name: adminSession.name,
    value: adminSession.value,
    url: 'http://teacher.localhost:8080'
  }]);
  const deniedTeacherRoute = await page.goto('http://teacher.localhost:8080/teacher/task');
  expect(deniedTeacherRoute.status()).toBe(403);
  await page.goto('http://teacher.localhost:8080/admin/teachers');
  await expect(page.locator('#adminConsole')).toContainText('管理者: admin');

  await page.locator('#createTeacherButton').click();
  const editor = page.locator('#teacherEditModal');
  await expect(editor).toBeVisible();
  await editor.locator('input[name="loginId"]').fill(teacherLoginId);
  await editor.locator(`input[name="schoolIds"][value="${fixture.schoolId}"]`).check();
  const features = editor.locator('input[name="features"]');
  for (let index = 0; index < await features.count(); index += 1) {
    await features.nth(index).uncheck();
  }
  await editor.locator('input[name="features"][value="task-management"]').check();
  await page.setViewportSize({ width: 375, height: 812 });
  await editor.locator('button[type="submit"]').click();

  const confirmation = page.locator('[data-feedback-confirm]');
  await expect(confirmation).toBeVisible();
  await confirmation.click();

  const credentials = page.locator('#credentialModal');
  await expect(credentials).toBeVisible();
  await expect(credentials.locator('#credentialId')).toHaveValue(teacherLoginId);
  await expect(credentials.locator('#credentialPassword')).toHaveValue(/\S+/);
  const teacherPassword = await credentials.locator('#credentialPassword').inputValue();
  await credentials.locator('[data-bs-dismiss="modal"]').last().click();

  const teacherRow = page.locator('#teacherAccountTable tbody tr').filter({ hasText: teacherLoginId });
  await expect(teacherRow).toHaveCount(1);
  await expect(teacherRow).toContainText('P20 synthetic admin school');
  await expect(teacherRow).toContainText('課題編集');
  await expect(page.locator('#credentialPassword')).toHaveValue('');

  const widths = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth
  }));
  expect(widths.document).toBeLessThanOrEqual(widths.viewport);
  expect(widths.body).toBeLessThanOrEqual(widths.viewport);

  const teacherContext = await page.context().browser().newContext();
  const teacherPage = await teacherContext.newPage();
  await login(teacherPage, '/teacher/account/login', teacherLoginId, teacherPassword, '/teacher/account/password');
  const changedTeacherPassword = 'P20TeacherChange42!';
  await teacherPage.locator('#currentPassword').fill(teacherPassword);
  await teacherPage.locator('#newPassword').fill(changedTeacherPassword);
  await teacherPage.locator('#confirmPassword').fill(changedTeacherPassword);
  await teacherPage.locator('#savePasswordButton').click();
  const passwordConfirmation = teacherPage.locator('[data-feedback-confirm]');
  await expect(passwordConfirmation).toBeVisible();
  await passwordConfirmation.click();
  await expect(teacherPage).toHaveURL(/\/teacher\/account\/login(?:$|\?)/);

  await login(teacherPage, '/teacher/account/login', teacherLoginId, changedTeacherPassword, '/teacher/task');
  const taskPage = await teacherPage.goto('/teacher/task');
  expect(taskPage.status()).toBe(200);
  await expect(teacherPage.getByRole('heading', { name: '課題編集' })).toBeVisible();

  const teacherSession = (await teacherContext.cookies())
    .find(cookie => cookie.name === 'JSESSIONID');
  expect(teacherSession).toBeTruthy();
  await teacherContext.addCookies([{
    name: teacherSession.name,
    value: teacherSession.value,
    url: 'http://student.localhost:8080'
  }]);
  const deniedStudentPage = await teacherPage.goto('http://student.localhost:8080/student/home');
  expect(deniedStudentPage.status()).toBe(403);

  const adminPageResponse = await teacherPage.goto('/admin/teachers');
  expect(adminPageResponse.status()).toBe(403);
  await teacherPage.goto('/teacher/task');
  const teacherCsrfToken = await teacherPage.locator('input[name="csrfToken"]').first().inputValue();
  const deniedAdminMutation = await teacherPage.evaluate(async ({ csrfToken }) => {
    const response = await fetch('/admin/teachers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams({
        action: 'create',
        loginId: 'p20-forbidden-admin-create',
        csrfToken,
        changeConfirmed: 'yes'
      })
    });
    return { status: response.status, body: await response.text() };
  }, { csrfToken: teacherCsrfToken });
  expect(deniedAdminMutation.status).toBe(403);
  expect(deniedAdminMutation.body).toContain('403');
  await page.reload();
  await expect(page.locator('#teacherAccountTable tbody tr').filter({ hasText: 'p20-forbidden-admin-create' }))
    .toHaveCount(0);

  const deniedPage = await teacherPage.goto('/teacher/submissions');
  expect(deniedPage.status()).toBe(403);

  await page.reload();
  const refreshedTeacherRow = page.locator('#teacherAccountTable tbody tr').filter({ hasText: teacherLoginId });
  await expect(refreshedTeacherRow).toContainText('利用中');
  const currentVersion = Number(await refreshedTeacherRow.getAttribute('data-version'));
  const staleVersionRequest = await page.evaluate(async ({ userId, version, csrfToken }) => {
    const response = await fetch('/admin/teachers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams({
        action: 'suspend',
        userId,
        version: String(version),
        csrfToken,
        changeConfirmed: 'yes'
      })
    });
    return { status: response.status, body: await response.json() };
  }, {
    userId: await refreshedTeacherRow.getAttribute('data-user-id'),
    version: currentVersion - 1,
    csrfToken: await page.locator('#adminConsole').getAttribute('data-csrf')
  });
  expect(staleVersionRequest.status).toBe(400);
  expect(staleVersionRequest.body.error).toContain('更新されています');
  await page.reload();
  const afterStaleVersionRow = page.locator('#teacherAccountTable tbody tr').filter({ hasText: teacherLoginId });
  await expect(afterStaleVersionRow).toContainText('利用中');
  await expect(afterStaleVersionRow).toHaveAttribute('data-version', String(currentVersion));

  const suspendResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/admin/teachers');
  await afterStaleVersionRow.locator('button[data-action="suspend"]').click();
  const suspendConfirmation = page.locator('[data-feedback-confirm]');
  await expect(suspendConfirmation).toBeVisible();
  await suspendConfirmation.click();
  const suspendResponse = await suspendResponsePromise;
  expect(suspendResponse.status()).toBe(200);
  await expect(afterStaleVersionRow).toContainText('停止中');

  await teacherPage.goto('/teacher/task');
  await expect(teacherPage).toHaveURL(/\/teacher\/account\/login(?:$|\?)/);
  await assertRejectedLogin(teacherPage, '/teacher/account/login', teacherLoginId, changedTeacherPassword);

  const activateResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/admin/teachers');
  await refreshedTeacherRow.locator('button[data-action="activate"]').click();
  const activateConfirmation = page.locator('[data-feedback-confirm]');
  await expect(activateConfirmation).toBeVisible();
  await activateConfirmation.click();
  const activateResponse = await activateResponsePromise;
  expect(activateResponse.status()).toBe(200);
  await expect(refreshedTeacherRow).toContainText('利用中');

  await login(teacherPage, '/teacher/account/login', teacherLoginId, changedTeacherPassword, '/teacher/task');
  const restoredTaskPage = await teacherPage.goto('/teacher/task');
  expect(restoredTaskPage.status()).toBe(200);

  const deleteResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/admin/teachers');
  await refreshedTeacherRow.locator('button[data-action="delete"]').click();
  const deleteConfirmation = page.locator('[data-feedback-confirm]');
  await expect(deleteConfirmation).toBeVisible();
  await deleteConfirmation.click();
  const deleteResponse = await deleteResponsePromise;
  expect(deleteResponse.status()).toBe(200);
  await expect(refreshedTeacherRow).toContainText('削除済み');
  await expect(refreshedTeacherRow.locator('button[data-action]')).toHaveCount(0);

  const deletedAccountRequest = await page.evaluate(async ({ userId, version, csrfToken }) => {
    const response = await fetch('/admin/teachers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams({
        action: 'activate',
        userId,
        version,
        csrfToken,
        changeConfirmed: 'yes'
      })
    });
    return { status: response.status, body: await response.json() };
  }, {
    userId: await refreshedTeacherRow.getAttribute('data-user-id'),
    version: await refreshedTeacherRow.getAttribute('data-version'),
    csrfToken: await page.locator('#adminConsole').getAttribute('data-csrf')
  });
  expect(deletedAccountRequest.status).toBe(400);
  expect(deletedAccountRequest.body.error).toContain('削除済み');

  await teacherPage.goto('/teacher/task');
  await expect(teacherPage).toHaveURL(/\/teacher\/account\/login(?:$|\?)/);
  await assertRejectedLogin(teacherPage, '/teacher/account/login', teacherLoginId, changedTeacherPassword);

  await refreshedTeacherRow.locator('button[data-history="operations"]').click();
  const deletionAudit = page.locator('#historyRows tr').filter({ hasText: '削除' });
  await expect(deletionAudit).toContainText('成功');
  const staleSuspendAudit = page.locator('#historyRows tr')
    .filter({ has: page.locator('td:nth-child(4)').getByText('停止', { exact: true }) })
    .filter({ hasText: '失敗' });
  await expect(staleSuspendAudit).toHaveCount(1);
  const rejectedReactivationAudit = page.locator('#historyRows tr')
    .filter({ hasText: '停止解除' })
    .filter({ hasText: '失敗' });
  await expect(rejectedReactivationAudit).toHaveCount(1);
  await teacherContext.close();
});
