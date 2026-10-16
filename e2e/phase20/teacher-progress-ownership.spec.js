const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('progress and saved code remain visible only inside the teacher school', async ({ browser }) => {
  const baseURL = requiredEnv('PPE_P20_BASE_URL');
  const password = requiredEnv('PPE_P20_REVIEW_TEACHER_PASSWORD');
  const owner = await browser.newContext({ baseURL });
  const outsider = await browser.newContext({ baseURL });
  try {
    const ownerPage = await owner.newPage();
    await login(ownerPage, '/teacher/account/login', 'review_teacher', password, '/teacher/home');
    await ownerPage.goto('/teacher/progress');
    const row = ownerPage.locator('[data-progress-row][data-id="review_student"]').first();
    await expect(row).toContainText('Synthetic Review Task');
    const assignmentId = await row.getAttribute('data-assignment-id');
    const studentId = await row.getAttribute('data-student-user-id');
    const query = `assignmentId=${assignmentId}&studentUserId=${studentId}`;
    await row.getByRole('button', { name: '表示', exact: true }).click();
    await expect(ownerPage.locator('#historyDetailModal')).toBeVisible();
    await expect(ownerPage.locator('#historyDetailMeta')).toContainText('review_student');
    await expect(ownerPage.locator('#latestCodeViewer')).toHaveValue(/.+/);
    const savedCode = await ownerPage.locator('#latestCodeViewer').inputValue();
    expect(savedCode).not.toBe('');
    const download = await ownerPage.evaluate(async (url) => {
      const response = await fetch(url);
      return { status: response.status, body: await response.text() };
    }, `/teacher/progress?view=download&${query}`);
    expect(download).toEqual({ status: 200, body: savedCode });
    await ownerPage.reload();
    await ownerPage.locator('[data-progress-row][data-id="review_student"]')
      .first().getByRole('button', { name: '表示', exact: true }).click();
    await expect(ownerPage.locator('#latestCodeViewer')).toHaveValue(savedCode);

    const outsiderPage = await outsider.newPage();
    await assertRejectedLogin(
      outsiderPage, '/teacher/account/login', 'review_outsider', 'invalid-synthetic-password'
    );
    await login(outsiderPage, '/teacher/account/login', 'review_outsider', password, '/teacher/home');
    await outsiderPage.goto('/teacher/progress');
    await expect(outsiderPage.locator('[data-progress-row]')).toHaveCount(0);
    await expect(outsiderPage.locator('#visibleCount')).toHaveText('0');
    for (const view of ['detail', 'download']) {
      const response = await outsiderPage.goto(`/teacher/progress?view=${view}&${query}`);
      expect(response.status()).toBe(404);
      expect(await response.text()).not.toContain(savedCode);
      expect(await response.text()).not.toContain('review_student');
    }
    await outsiderPage.goto('/teacher/progress');
    await expect(outsiderPage.locator('[data-progress-row]')).toHaveCount(0);
  } finally {
    await Promise.all([owner.close(), outsider.close()]);
  }
});
