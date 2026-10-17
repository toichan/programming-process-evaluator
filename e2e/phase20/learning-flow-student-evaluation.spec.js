const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('student can read and reload the persisted synthetic evaluation', async ({ page }) => {
  const assignmentId = requiredEnv('PPE_LEARNING_FLOW_ASSIGNMENT_ID');
  const submissionId = requiredEnv('PPE_LEARNING_FLOW_SUBMISSION_ID');
  const evaluationPath = `/student/evaluation?assignmentId=${assignmentId}&submissionId=${submissionId}`;
  const loginId = requiredEnv('PPE_LEARNING_FLOW_STUDENT_LOGIN');
  const password = requiredEnv('PPE_LEARNING_FLOW_STUDENT_PASSWORD');

  await page.goto(evaluationPath);
  await expect(page.getByRole('heading', { name: '生徒ログイン', level: 1 })).toBeVisible();
  await assertRejectedLogin(page, '/student/account/login', loginId, 'invalid-synthetic-password');
  await login(page, '/student/account/login', loginId, password, '/student/home');

  await page.goto(evaluationPath);
  await expect(page.getByRole('heading', { name: '評価結果', level: 1 })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '学習フロー' })
    .getByText('Synthetic learning flow: double an integer')).toBeVisible();
  await expect(page.getByRole('heading', { name: '思考力・判断力・表現力' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '主体的に学習に取り組む態度' })).toBeVisible();
  await page.reload();
  await expect(page.locator('.reason-body').filter({
    hasText: '合成されたコードと実行記録に基づく理由'
  })).toBeVisible();

  await page.setViewportSize({ width: 375, height: 812 });
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
});
