const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

async function browserGet(page, path) {
  return page.evaluate(async (url) => {
    const response = await fetch(url, { credentials: 'same-origin' });
    return { status: response.status, body: await response.text() };
  }, path);
}

test('other-school teacher cannot view or export submissions outside their school', async ({ browser }) => {
  const baseURL = requiredEnv('PPE_P20_BASE_URL');
  const password = requiredEnv('PPE_P20_REVIEW_TEACHER_PASSWORD');
  const ownerContext = await browser.newContext({ baseURL });
  const outsiderContext = await browser.newContext({ baseURL });
  const ownerPage = await ownerContext.newPage();
  const outsiderPage = await outsiderContext.newPage();

  try {
    await login(ownerPage, '/teacher/account/login', 'review_teacher', password, '/teacher/home');
    const ownerList = await browserGet(ownerPage, '/teacher/submissions?view=list');
    expect(ownerList.status).toBe(200);
    const submissions = JSON.parse(ownerList.body);
    expect(submissions.length).toBeGreaterThan(0);
    const submissionId = submissions[0].submissionId;

    const ownerSubmission = await ownerPage.goto(`/teacher/submissions?submissionId=${submissionId}`);
    expect(ownerSubmission?.status()).toBe(200);
    await expect(ownerPage.locator('#teacherReview')).toBeVisible();
    await expect(ownerPage.locator('#visibleCount')).not.toHaveText('0');

    await assertRejectedLogin(
      outsiderPage,
      '/teacher/account/login',
      'review_outsider',
      'invalid-synthetic-password'
    );
    await login(outsiderPage, '/teacher/account/login', 'review_outsider', password, '/teacher/home');

    const outsiderListResponse = outsiderPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/submissions'
        && address.searchParams.get('view') === 'list';
    });
    const outsiderPageResponse = await outsiderPage.goto('/teacher/submissions');
    expect(outsiderPageResponse?.status()).toBe(200);
    await outsiderListResponse;
    await expect(outsiderPage.locator('#teacherReview')).toBeVisible();
    await expect(outsiderPage.getByRole('heading', { name: '提出課題確認' })).toBeVisible();
    await expect(outsiderPage.locator('#visibleCount')).toHaveText('0');
    await expect(outsiderPage.locator('#submissionCsv')).toBeDisabled();
    await expect(outsiderPage.locator('#submissionZip')).toBeDisabled();
    const visibleRows = await outsiderPage.locator('#submissionTableBody').innerText();
    expect(visibleRows).not.toContain('review_student');
    expect(visibleRows).not.toContain('print(input())');

    const outsiderList = await browserGet(outsiderPage, '/teacher/submissions?view=list');
    expect(outsiderList.status).toBe(200);
    expect(JSON.parse(outsiderList.body)).toEqual([]);

    for (const view of ['detail', 'file']) {
      const path = `/teacher/submissions?view=${view}&submissionId=${submissionId}`;
      const directURL = await outsiderPage.goto(path);
      expect(directURL?.status(), `${view} direct URL`).toBe(404);
      const response = await browserGet(outsiderPage, path);
      expect(response.status, `${view} browser fetch`).toBe(404);
      expect(response.body).not.toContain('review_student');
      expect(response.body).not.toContain('print(input())');
    }

    const csv = await browserGet(outsiderPage, '/teacher/submissions?view=csv');
    expect(csv.status).toBe(200);
    expect(csv.body).not.toContain('review_student');
    expect(csv.body).not.toContain('print(input())');

    const zip = await browserGet(outsiderPage, '/teacher/submissions?view=zip');
    expect(zip.status).toBe(400);
    expect(zip.body).not.toContain('review_student');
    expect(zip.body).not.toContain('print(input())');
  } finally {
    await Promise.all([ownerContext.close(), outsiderContext.close()]);
  }
});

test('other-school teacher cannot view or export evaluations outside their school', async ({ browser }) => {
  const baseURL = requiredEnv('PPE_P20_BASE_URL');
  const password = requiredEnv('PPE_P20_REVIEW_TEACHER_PASSWORD');
  const ownerContext = await browser.newContext({ baseURL });
  const outsiderContext = await browser.newContext({ baseURL });
  const ownerPage = await ownerContext.newPage();
  const outsiderPage = await outsiderContext.newPage();

  try {
    await login(ownerPage, '/teacher/account/login', 'review_teacher', password, '/teacher/home');
    const ownerList = await browserGet(ownerPage, '/teacher/evaluations?view=list');
    expect(ownerList.status).toBe(200);
    const evaluations = JSON.parse(ownerList.body);
    const target = evaluations.find((row) => row.evaluationId != null);
    expect(target).toBeTruthy();

    const ownerListResponse = ownerPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/evaluations'
        && address.searchParams.get('view') === 'list';
    });
    const ownerPageResponse = await ownerPage.goto('/teacher/evaluations');
    expect(ownerPageResponse?.status()).toBe(200);
    await ownerListResponse;
    await expect(ownerPage.locator('#teacherReview')).toBeVisible();
    await expect(ownerPage.locator('#visibleCount')).not.toHaveText('0');
    await expect(ownerPage.locator('#evaluationRows tr').first()).toBeVisible();
    await ownerPage.locator('#evaluationRows button', { hasText: '表示' }).first().click();
    await expect(ownerPage.locator('#evaluationDetailModal')).toBeVisible();
    await expect(ownerPage.locator('#detailTask')).not.toBeEmpty();

    await assertRejectedLogin(
      outsiderPage,
      '/teacher/account/login',
      'review_outsider',
      'invalid-synthetic-password'
    );
    await login(outsiderPage, '/teacher/account/login', 'review_outsider', password, '/teacher/home');

    const outsiderListResponse = outsiderPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/evaluations'
        && address.searchParams.get('view') === 'list';
    });
    const outsiderPageResponse = await outsiderPage.goto('/teacher/evaluations');
    expect(outsiderPageResponse?.status()).toBe(200);
    await outsiderListResponse;
    await expect(outsiderPage.locator('#teacherReview')).toBeVisible();
    await expect(outsiderPage.getByRole('heading', { name: '評価確認' })).toBeVisible();
    await expect(outsiderPage.locator('#visibleCount')).toHaveText('0');
    await expect(outsiderPage.locator('#summaryTotal')).toHaveText('0');
    await expect(outsiderPage.locator('#evaluationRows')).toContainText('該当する評価履歴はありません。');
    for (const button of ['#exportCsv', '#bulkResults', '#bulkLogs']) {
      await expect(outsiderPage.locator(button)).toBeDisabled();
    }

    const outsiderList = await browserGet(outsiderPage, '/teacher/evaluations?view=list');
    expect(outsiderList.status).toBe(200);
    expect(JSON.parse(outsiderList.body)).toEqual([]);

    const detailPath = `/teacher/evaluations?view=detail&submissionId=${target.submissionId}&evaluationId=${target.evaluationId}`;
    for (const view of ['detail', 'file', 'logs']) {
      const path = view === 'detail'
        ? detailPath
        : `/teacher/evaluations?view=${view}&submissionId=${target.submissionId}&evaluationId=${target.evaluationId}`;
      const directURL = await outsiderPage.goto(path);
      expect(directURL?.status(), `${view} direct URL`).toBe(404);
      const response = await browserGet(outsiderPage, path);
      expect(response.status, `${view} browser fetch`).toBe(404);
      expect(response.body).not.toContain('review_student');
      expect(response.body).not.toContain('print(input())');
    }

    const csv = await browserGet(outsiderPage, '/teacher/evaluations?view=csv');
    expect(csv.status).toBe(200);
    expect(csv.body).not.toContain('review_student');
    expect(csv.body).not.toContain('print(input())');

    for (const view of ['zip', 'logs-zip']) {
      const response = await browserGet(outsiderPage, `/teacher/evaluations?view=${view}`);
      expect(response.status, view).toBe(400);
      expect(response.body, view).not.toContain('review_student');
      expect(response.body, view).not.toContain('print(input())');
    }
  } finally {
    await Promise.all([ownerContext.close(), outsiderContext.close()]);
  }
});
