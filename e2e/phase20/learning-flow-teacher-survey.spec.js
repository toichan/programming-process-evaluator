const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('teacher can read and export the persisted synthetic survey response', async ({ browser }) => {
  const context = await browser.newContext({
    baseURL: requiredEnv('PPE_LEARNING_FLOW_TEACHER_BASE_URL'),
    acceptDownloads: true
  });
  const page = await context.newPage();
  const loginId = requiredEnv('PPE_LEARNING_FLOW_TEACHER_LOGIN');
  const password = requiredEnv('PPE_LEARNING_FLOW_TEACHER_PASSWORD');

  try {
    await page.goto('/teacher/surveys');
    await expect(page.getByRole('heading', { name: '教師ログイン', level: 1 })).toBeVisible();
    await assertRejectedLogin(page, '/teacher/account/login', loginId, 'invalid-synthetic-password');
    await login(page, '/teacher/account/login', loginId, password, '/teacher/students');

    const uiListPromise = page.waitForResponse(response =>
      new URL(response.url()).pathname.endsWith('/teacher/surveys')
      && new URL(response.url()).searchParams.get('view') === 'list');
    await page.goto('/teacher/surveys');
    await expect(page.locator('#teacherSurveyReview')).toBeVisible();
    const uiListResponse = await uiListPromise;
    const uiRows = await uiListResponse.json();
    const listResponse = await page.request.get('/teacher/surveys?view=list');
    const rows = await listResponse.json();
    expect(rows, `Expected one visible synthetic survey response, received: ${JSON.stringify(rows)}`).toHaveLength(1);
    expect(uiRows, `UI list returned ${JSON.stringify(uiRows)}; alert=${await page.locator('#surveyAlert').textContent()}`).toHaveLength(1);
    await expect(page.locator('#totalResponses')).toHaveText('1');
    await expect(page.locator('#completedResponses')).toHaveText('1');
    const response = page.locator('#surveyTableBody button[data-response-id]');
    await expect(response).toHaveCount(1);
    await response.click();
    await expect(page.locator('#surveyDetailModal')).toBeVisible();
    await expect(page.locator('#modalResponseContent')).toContainText('思考力等の評価は妥当でしたか');
    await page.locator('#surveyDetailModal [data-bs-dismiss="modal"]').last().click();
    await expect(page.locator('#surveyDetailModal')).toBeHidden();

    const downloadPromise = page.waitForEvent('download');
    await page.locator('#csvSurvey').click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/survey-results.*\.csv/);

    await page.reload();
    await expect(page.locator('#totalResponses')).toHaveText('1');
    await expect(page.locator('#completedResponses')).toHaveText('1');
  } finally {
    await context.close();
  }
});
