const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('student can read and reload the submitted synthetic survey', async ({ page }) => {
  const assignmentId = requiredEnv('PPE_LEARNING_FLOW_ASSIGNMENT_ID');
  const evaluationId = requiredEnv('PPE_LEARNING_FLOW_EVALUATION_ID');
  const surveyId = requiredEnv('PPE_LEARNING_FLOW_SURVEY_ID');
  const surveyPath = `/student/survey?assignmentId=${assignmentId}&evaluationId=${evaluationId}&surveyId=${surveyId}`;
  const loginId = requiredEnv('PPE_LEARNING_FLOW_STUDENT_LOGIN');
  const password = requiredEnv('PPE_LEARNING_FLOW_STUDENT_PASSWORD');

  await page.goto(surveyPath);
  await expect(page.getByRole('heading', { name: '生徒ログイン', level: 1 })).toBeVisible();
  await assertRejectedLogin(page, '/student/account/login', loginId, 'invalid-synthetic-password');
  await login(page, '/student/account/login', loginId, password, '/student/home');

  await page.goto(surveyPath);
  await expect(page.getByRole('heading', { name: 'アンケート', level: 1 })).toBeVisible();
  await expect(page.locator('.survey-form')).toHaveAttribute('data-read-only', 'true');
  await expect(page.locator('#surveyAnswers')).toHaveAttribute('disabled', '');
  await expect(page.locator('#surveyAnswers input[type="radio"][value="4"]:checked')).toHaveCount(6);
  await expect(page.locator('#surveyAnswers article.survey-question')).toHaveCount(6);
  await page.reload();
  await expect(page.locator('#surveyAnswers input[type="radio"][value="4"]:checked')).toHaveCount(6);

  await page.setViewportSize({ width: 375, height: 812 });
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
    overflow: Array.from(document.querySelectorAll('body *'))
      .map(element => ({ tag: element.tagName, className: element.className, id: element.id,
        right: Math.round(element.getBoundingClientRect().right) }))
      .filter(element => element.right > document.documentElement.clientWidth)
      .slice(0, 12)
  }));
  expect(dimensions.scrollWidth, JSON.stringify(dimensions)).toBeLessThanOrEqual(dimensions.clientWidth);
});
