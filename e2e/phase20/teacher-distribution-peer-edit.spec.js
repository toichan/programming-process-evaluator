const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('same-school teacher can edit a peer template while another school is denied direct access', async ({ browser, page }) => {
  const teacherBaseUrl = requiredEnv('PPE_P20_TEACHER_BASE_URL');
  const templateId = requiredEnv('PPE_P20_DISTRIBUTION_TEMPLATE_ID');
  const password = requiredEnv('PPE_P20_DISTRIBUTION_PASSWORD');
  const peerLogin = requiredEnv('PPE_P20_DISTRIBUTION_PEER_LOGIN');
  const ownerLogin = requiredEnv('PPE_P20_DISTRIBUTION_OWNER_LOGIN');
  const foreignLogin = requiredEnv('PPE_P20_DISTRIBUTION_FOREIGN_LOGIN');

  await assertRejectedLogin(page, '/teacher/account/login', peerLogin, 'invalid-synthetic-password');
  await login(page, '/teacher/account/login', peerLogin, password, '/teacher/home');
  await page.goto('/teacher/distribution');
  const peerRow = page.locator(
    `#distributionHistoryBody tr[data-history-row]:has([data-edit-template="${templateId}"])`
  ).first();
  await expect(peerRow).toContainText('P20 Peer Shared Template');
  await peerRow.locator(`[data-edit-template="${templateId}"]`).click();
  await expect(page.locator('#distributionTemplateId')).toHaveValue(templateId);
  await expect(page.locator('#distributionName')).toHaveValue('P20 Peer Shared Template');
  const editor = page.locator('#distributionExercisePage .CodeMirror');
  await expect(editor).toBeVisible();
  await expect(editor).toContainText("print('original peer template')");

  const folder = page.locator('#distributionExercisePage .lesson-tree-folder-header')
    .filter({ hasText: 'week1' });
  const toggle = folder.locator('.distribution-tree-folder-toggle');
  const icon = toggle.locator('.distribution-tree-folder-toggle-icon');
  const file = page.locator('#distributionExercisePage .lesson-tree-file-button').filter({ hasText: 'main.py' });
  for (const width of [1280, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(icon).toHaveText('▾');
    await expect(icon).toHaveCSS('transform', 'none');
    await expect(file).toBeVisible();
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-label', '展開 week1');
    await expect(icon).toHaveCSS('transform', 'matrix(0, -1, 1, 0, 0, 0)');
    await expect(file).toBeHidden();
    await toggle.press('Enter');
    await expect(toggle).toHaveAttribute('aria-label', '折りたたむ week1');
    await expect(icon).toHaveCSS('transform', 'none');
    await expect(file).toBeVisible();
  }
  await page.screenshot({ path: test.info().outputPath('distribution-icons-narrow.png'), fullPage: true });
  await page.setViewportSize({ width: 1280, height: 900 });

  await page.locator('#distributionName').fill('P20 Peer Template Updated');
  await editor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText("print('saved by same-school peer')");
  await page.locator('button[data-submit-action="saveDraft"]').click();
  await expect(page.locator('#distributionNotice'))
    .toHaveAttribute('data-message', '下書きを保存しました。');
  await page.reload();
  await expect(page.locator('#distributionHistoryBody')).toContainText('P20 Peer Template Updated');

  const ownerContext = await browser.newContext({ baseURL: teacherBaseUrl });
  try {
    const ownerPage = await ownerContext.newPage();
    await login(ownerPage, '/teacher/account/login', ownerLogin, password, '/teacher/home');
    await ownerPage.goto('/teacher/distribution');
    const ownerRow = ownerPage.locator(
      `#distributionHistoryBody tr[data-history-row]:has([data-edit-template="${templateId}"])`
    ).first();
    await expect(ownerRow).toContainText('P20 Peer Shared Template');
    await ownerRow.locator(`[data-edit-template="${templateId}"]`).click();
    await expect(ownerPage.locator('#distributionName')).toHaveValue('P20 Peer Template Updated');
    const ownerEditor = ownerPage.locator('#distributionExercisePage .CodeMirror');
    await expect(ownerEditor).toContainText("print('saved by same-school peer')");
  } finally {
    await ownerContext.close();
  }

  const foreignContext = await browser.newContext({ baseURL: teacherBaseUrl });
  try {
    const foreignPage = await foreignContext.newPage();
    await assertRejectedLogin(foreignPage, '/teacher/account/login', foreignLogin, 'invalid-synthetic-password');
    await login(foreignPage, '/teacher/account/login', foreignLogin, password, '/teacher/home');
    await foreignPage.goto('/teacher/distribution');
    await expect(foreignPage.locator('#distributionHistoryBody'))
      .not.toContainText('P20 Peer Template Updated');
    const directResponse = await foreignPage.goto(
      `/teacher/distribution?view=template&id=${encodeURIComponent(templateId)}`
    );
    expect(directResponse.status()).toBe(403);
  } finally {
    await foreignContext.close();
  }
});
