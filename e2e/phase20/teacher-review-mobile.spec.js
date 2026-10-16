const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const { login, requiredEnv } = require('./helpers');

const teacherPages = [
  { path: '/teacher/progress', root: '#teacherProgress', heading: '課題進捗確認機能', count: '#visibleCount' },
  { path: '/teacher/submissions', root: '#teacherReview', heading: '提出課題確認', count: '#visibleCount' },
  { path: '/teacher/evaluations', root: '#teacherReview', heading: '評価確認', count: '#visibleCount' },
  { path: '/teacher/surveys', root: '#teacherSurveyReview', heading: 'アンケート結果確認', count: '#totalResponses' }
];

const exportsByPath = {
  '/teacher/submissions': [
    { button: '#submissionCsv', view: 'csv', contentType: 'text/csv' }
  ],
  '/teacher/evaluations': [
    { button: '#exportCsv', view: 'csv', contentType: 'text/csv' }
  ],
  '/teacher/surveys': [
    { button: '#csvSurvey', view: 'csv', contentType: 'text/csv', anonymized: true },
    { button: '#textSurvey', view: 'text', contentType: 'text/plain', anonymized: true }
  ]
};

async function assertExport(page, screenPath, item) {
  const responsePromise = page.waitForResponse(response => {
    const address = new URL(response.url());
    return address.pathname === screenPath
      && address.searchParams.get('view') === item.view;
  });
  const downloadPromise = page.waitForEvent('download');
  await page.locator(item.button).click();
  const [response, download] = await Promise.all([responsePromise, downloadPromise]);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain(item.contentType);
  expect(await download.failure()).toBeNull();
  const filePath = await download.path();
  expect(filePath).toBeTruthy();
  const body = fs.readFileSync(filePath);
  expect(body.length, `${screenPath}?view=${item.view} export body`).toBeGreaterThan(0);
  if (item.anonymized) {
    expect(body.toString('utf8')).not.toContain('review_student');
  }
}

test('teacher review screens render and export synthetic data across feature boundaries', async ({ page }) => {
  await login(
    page,
    '/teacher/account/login',
    requiredEnv('PPE_P20_REVIEW_TEACHER_LOGIN'),
    requiredEnv('PPE_P20_REVIEW_TEACHER_PASSWORD'),
    '/teacher/home'
  );

  for (const screen of teacherPages) {
    await page.goto(new URL(screen.path, page.url()).toString());
    await expect(page.locator(screen.root)).toBeVisible();
    await expect(page.getByRole('heading', { name: screen.heading })).toBeVisible();
    await expect(page.locator(screen.count)).not.toHaveText('0');
    for (const exportItem of exportsByPath[screen.path] || []) {
      await assertExport(page, screen.path, exportItem);
    }
  }
});

test('teacher review screens have no page-level horizontal overflow at narrow width', async ({ page }) => {
  await login(
    page,
    '/teacher/account/login',
    requiredEnv('PPE_P20_REVIEW_TEACHER_LOGIN'),
    requiredEnv('PPE_P20_REVIEW_TEACHER_PASSWORD'),
    '/teacher/home'
  );
  await page.setViewportSize({ width: 390, height: 844 });

  for (const screen of teacherPages) {
    await page.goto(new URL(screen.path, page.url()).toString());
    await expect(page.locator(screen.root)).toBeVisible();
    await expect(page.getByRole('heading', { name: screen.heading })).toBeVisible();
    const layout = await page.evaluate(() => ({
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth
    }));
    expect(layout.documentWidth, `${screen.path} page-level overflow`).toBeLessThanOrEqual(layout.viewportWidth + 1);
  }
});
