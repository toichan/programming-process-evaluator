const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('teacher can log in, reach the protected workspace, and is denied the admin boundary', async ({ page }) => {
  const teacherLogin = requiredEnv('PPE_P20_TEACHER_LOGIN');
  const teacherPassword = requiredEnv('PPE_P20_TEACHER_PASSWORD');

  await assertRejectedLogin(page, '/teacher/account/login', teacherLogin, 'invalid-synthetic-password');
  await page.goto('/teacher/students');
  await expect(page).toHaveURL(/\/teacher\/account\/login$/);

  await login(page, '/teacher/account/login', teacherLogin, teacherPassword, '/teacher/task');
  await expect(page.locator('body')).toContainText(teacherLogin);
  const adminResponse = await page.goto(new URL('/admin/teachers', page.url()).toString());
  expect(adminResponse.status()).toBe(403);
});
