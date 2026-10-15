const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

async function browserGet(page, path) {
  return page.evaluate(async (url) => {
    const response = await fetch(url, { credentials: 'same-origin' });
    return { status: response.status, body: await response.text() };
  }, path);
}

test('other-school teacher cannot view or export survey responses outside their school', async ({ browser }) => {
  const baseURL = requiredEnv('PPE_P20_BASE_URL');
  const password = requiredEnv('PPE_P20_REVIEW_TEACHER_PASSWORD');
  const ownerContext = await browser.newContext({ baseURL });
  const outsiderContext = await browser.newContext({ baseURL });
  const ownerPage = await ownerContext.newPage();
  const outsiderPage = await outsiderContext.newPage();

  try {
    await login(ownerPage, '/teacher/account/login', 'review_teacher', password, '/teacher/home');
    const ownerList = await browserGet(ownerPage, '/teacher/surveys?view=list');
    expect(ownerList.status).toBe(200);
    const responses = JSON.parse(ownerList.body);
    expect(responses).toHaveLength(2);
    const target = responses.find((row) => row.completionStatus === 'submitted');
    expect(target).toBeTruthy();

    const ownerListResponse = ownerPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/surveys' && address.searchParams.get('view') === 'list';
    });
    const ownerPageResponse = await ownerPage.goto('/teacher/surveys');
    expect(ownerPageResponse?.status()).toBe(200);
    await ownerListResponse;
    await expect(ownerPage.locator('#totalResponses')).toHaveText('2');
    await expect(ownerPage.locator('#surveyTableBody')).toContainText('review_student');
    await ownerPage.locator(`#surveyTableBody button[data-response-id="${target.responseId}"]`).click();
    await expect(ownerPage.locator('#surveyDetailModal')).toBeVisible();
    await expect(ownerPage.locator('#modalResponseContent')).toContainText('<img src=x onerror=alert(1)>長い回答');

    await assertRejectedLogin(
      outsiderPage,
      '/teacher/account/login',
      'review_outsider',
      'invalid-synthetic-password'
    );
    await login(outsiderPage, '/teacher/account/login', 'review_outsider', password, '/teacher/home');

    const outsiderListResponse = outsiderPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/surveys' && address.searchParams.get('view') === 'list';
    });
    const outsiderPageResponse = await outsiderPage.goto('/teacher/surveys');
    expect(outsiderPageResponse?.status()).toBe(200);
    await outsiderListResponse;
    await expect(outsiderPage.locator('#teacherSurveyReview')).toBeVisible();
    await expect(outsiderPage.locator('#totalResponses')).toHaveText('0');
    await expect(outsiderPage.locator('#summaryTotal')).toHaveText('0');
    await expect(outsiderPage.locator('#surveyTableBody')).toContainText('表示対象のアンケート回答がありません。');
    const renderedRows = await outsiderPage.locator('#surveyTableBody').innerText();
    expect(renderedRows).not.toContain('review_student');
    expect(renderedRows).not.toContain('長い回答');

    const outsiderList = await browserGet(outsiderPage, '/teacher/surveys?view=list');
    expect(outsiderList.status).toBe(200);
    expect(JSON.parse(outsiderList.body)).toEqual([]);

    const detailPath = `/teacher/surveys?view=detail&responseId=${target.responseId}`;
    const directDetail = await outsiderPage.goto(detailPath);
    expect(directDetail?.status()).toBe(404);
    const detail = await browserGet(outsiderPage, detailPath);
    expect(detail.status).toBe(404);
    expect(detail.body).not.toContain('review_student');
    expect(detail.body).not.toContain('長い回答');

    for (const view of ['csv', 'text']) {
      const exportResponse = await browserGet(outsiderPage, `/teacher/surveys?view=${view}`);
      expect(exportResponse.status, view).toBe(200);
      expect(exportResponse.body, view).not.toContain('REC-standard');
      expect(exportResponse.body, view).not.toContain('SR-standard');
      expect(exportResponse.body, view).not.toContain('review_student');
      expect(exportResponse.body, view).not.toContain('長い回答');
      expect(exportResponse.body, view).not.toContain('Synthetic reason');
    }
  } finally {
    await Promise.all([ownerContext.close(), outsiderContext.close()]);
  }
});
