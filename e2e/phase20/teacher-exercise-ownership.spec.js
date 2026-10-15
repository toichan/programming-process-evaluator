const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

async function browserGet(page, path) {
  return page.evaluate(async (url) => {
    const response = await fetch(url, { credentials: 'same-origin' });
    return { status: response.status, body: await response.text() };
  }, path);
}

test('other-school teacher cannot view or export exercise code outside their school', async ({ browser }) => {
  const baseURL = requiredEnv('PPE_P20_BASE_URL');
  const password = requiredEnv('PPE_P20_EXERCISE_TEACHER_PASSWORD');
  const ownerContext = await browser.newContext({ baseURL });
  const outsiderContext = await browser.newContext({ baseURL });
  const ownerPage = await ownerContext.newPage();
  const outsiderPage = await outsiderContext.newPage();

  try {
    await login(ownerPage, '/teacher/account/login', 'ex_teacher', password, '/teacher/home');
    const ownerList = await browserGet(ownerPage, '/teacher/exercises?view=list');
    expect(ownerList.status).toBe(200);
    const rows = JSON.parse(ownerList.body);
    expect(rows).toHaveLength(4);
    const target = rows.find((row) => row.studentLoginId === 'ex_saved');
    expect(target).toBeTruthy();
    const targetQuery = `studentId=${target.studentId}&classroomId=${target.classroomId}`;
    const ownerDetail = await browserGet(ownerPage, `/teacher/exercises?view=detail&${targetQuery}`);
    expect(ownerDetail.status).toBe(200);
    const entry = JSON.parse(ownerDetail.body).scopes.flatMap((scope) => scope.entries)
      .find((item) => item.type === 'FILE' && item.content.includes("print('保存済み')"));
    expect(entry).toBeTruthy();

    const ownerListResponse = ownerPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/exercises' && address.searchParams.get('view') === 'list';
    });
    const ownerPageResponse = await ownerPage.goto('/teacher/exercises');
    expect(ownerPageResponse?.status()).toBe(200);
    await ownerListResponse;
    await expect(ownerPage.locator('#visibleCount')).toHaveText('4');
    await ownerPage.locator(
      `#exerciseTableBody button[data-exercise-student="${target.studentId}"][data-exercise-classroom="${target.classroomId}"]`
    ).click();
    await expect(ownerPage.locator('#exerciseDetailModal')).toBeVisible();
    await expect(ownerPage.locator('#detailStudentId')).toHaveText('ex_saved');
    await expect(ownerPage.locator('#exerciseCodeViewer')).toHaveValue(/print\(2\)/);

    await assertRejectedLogin(
      outsiderPage,
      '/teacher/account/login',
      'ex_outsider',
      'invalid-synthetic-password'
    );
    await login(outsiderPage, '/teacher/account/login', 'ex_outsider', password, '/teacher/home');
    const outsiderListResponse = outsiderPage.waitForResponse((response) => {
      const address = new URL(response.url());
      return address.pathname === '/teacher/exercises' && address.searchParams.get('view') === 'list';
    });
    const outsiderPageResponse = await outsiderPage.goto('/teacher/exercises');
    expect(outsiderPageResponse?.status()).toBe(200);
    await outsiderListResponse;
    await expect(outsiderPage.locator('#visibleCount')).toHaveText('0');
    await expect(outsiderPage.locator('#visibleFileCount')).toHaveText('0');
    await expect(outsiderPage.locator('#exerciseTableBody')).toContainText('表示対象の授業演習データがありません。');
    await expect(outsiderPage.locator('#bulkExercise')).toBeDisabled();
    await expect(outsiderPage.locator('#csvExercise')).toBeDisabled();

    const outsiderList = await browserGet(outsiderPage, '/teacher/exercises?view=list');
    expect(outsiderList.status).toBe(200);
    expect(JSON.parse(outsiderList.body)).toEqual([]);

    const detailPath = `/teacher/exercises?view=detail&${targetQuery}`;
    const directDetail = await outsiderPage.goto(detailPath);
    expect(directDetail?.status()).toBe(404);
    const detail = await browserGet(outsiderPage, detailPath);
    expect(detail.status).toBe(404);
    expect(detail.body).not.toContain('ex_saved');
    expect(detail.body).not.toContain("print('保存済み')");

    const downloadPath = `/teacher/exercises?view=download&${targetQuery}&entryId=${entry.entryId}`;
    const download = await browserGet(outsiderPage, downloadPath);
    expect(download.status).toBe(404);
    expect(download.body).not.toContain("print('保存済み')");
    const zip = await browserGet(outsiderPage, `/teacher/exercises?view=zip&${targetQuery}`);
    expect(zip.status).toBe(404);
    expect(zip.body).not.toContain("print('保存済み')");

    const csv = await browserGet(outsiderPage, '/teacher/exercises?view=csv');
    expect(csv.status).toBe(200);
    expect(csv.body).not.toContain('ex_saved');
    expect(csv.body).not.toContain("print('保存済み')");
    expect(csv.body).not.toContain('hello.py');
    const bulk = await browserGet(outsiderPage, '/teacher/exercises?view=bulk');
    expect(bulk.status).toBe(400);
    expect(bulk.body).not.toContain('ex_saved');
    expect(bulk.body).not.toContain("print('保存済み')");
    expect(bulk.body).not.toContain('hello.py');
  } finally {
    await Promise.all([ownerContext.close(), outsiderContext.close()]);
  }
});
