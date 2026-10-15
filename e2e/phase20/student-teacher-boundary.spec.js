const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('authenticated student session is denied the teacher workspace', async ({ page }) => {
  const studentLogin = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');

  await assertRejectedLogin(page, '/student/account/login', studentLogin, 'invalid-synthetic-password');
  await login(page, '/student/account/login', studentLogin, studentPassword, '/student/home');

  const studentSession = (await page.context().cookies())
    .find(cookie => cookie.name === 'JSESSIONID');
  expect(studentSession).toBeTruthy();
  await page.context().addCookies([{
    name: studentSession.name,
    value: studentSession.value,
    url: 'http://teacher.localhost:8080'
  }]);

  for (const route of ['/teacher/task', '/admin/teachers']) {
    const deniedResponse = await page.goto(`http://teacher.localhost:8080${route}`);
    expect(deniedResponse.status()).toBe(403);
  }
});
