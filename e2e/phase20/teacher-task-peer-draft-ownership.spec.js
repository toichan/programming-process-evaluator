const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

function draftFixture() {
  const fixturePath = path.resolve(__dirname, '../../build/phase20-peer-draft-browser-fixture.properties');
  const values = Object.fromEntries(fs.readFileSync(fixturePath, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=');
      return [line.slice(0, separator), line.slice(separator + 1)];
    }));
  for (const key of [
    'taskId',
    'ownerLoginId',
    'peerLoginId',
    'foreignLoginId',
    'noTaskManagementLoginId'
  ]) {
    if (!values[key]) throw new Error(`Peer-draft fixture is missing ${key}.`);
  }
  return values;
}

test('same-school teacher can edit a peer draft while a foreign-school teacher is denied', async ({ browser }) => {
  const fixture = draftFixture();
  const password = requiredEnv('PPE_P20_TEACHER_PASSWORD');
  const ownerTitle = 'Phase 20 peer draft owner title';
  const updatedTitle = 'Phase 20 peer draft edited by colleague';
  const peerContext = await browser.newContext();
  const ownerContext = await browser.newContext();
  const foreignContext = await browser.newContext();
  const noPermissionContext = await browser.newContext();
  const peerPage = await peerContext.newPage();
  const ownerPage = await ownerContext.newPage();
  const foreignPage = await foreignContext.newPage();
  const noPermissionPage = await noPermissionContext.newPage();

  try {
    await assertRejectedLogin(
      peerPage, '/teacher/account/login', fixture.peerLoginId, 'invalid-synthetic-password');
    await login(peerPage, '/teacher/account/login', fixture.peerLoginId, password, '/teacher/task');

    const peerDraftRow = peerPage.locator('#taskTable tbody tr').filter({ hasText: ownerTitle });
    await expect(peerDraftRow).toHaveCount(1);
    await peerDraftRow.locator(`a[href="/teacher/task?taskId=${fixture.taskId}"]`).click();
    await expect(peerPage.locator('#taskCreateForm input[name="taskId"]')).toHaveValue(fixture.taskId);
    await peerPage.locator('#taskNameInput').fill(updatedTitle);
    await peerPage.locator('#saveDraftButton').click();
    await expect(peerPage.locator('#taskSaveNotice')).toBeVisible();
    await expect(peerPage.locator('#taskNameInput')).toHaveValue(updatedTitle);

    await login(ownerPage, '/teacher/account/login', fixture.ownerLoginId, password, '/teacher/task');
    const ownerDraftRow = ownerPage.locator('#taskTable tbody tr').filter({ hasText: updatedTitle });
    await expect(ownerDraftRow).toHaveCount(1);
    await ownerDraftRow.locator(`a[href="/teacher/task?taskId=${fixture.taskId}"]`).click();
    await expect(ownerPage.locator('#taskNameInput')).toHaveValue(updatedTitle);

    await login(foreignPage, '/teacher/account/login', fixture.foreignLoginId, password, '/teacher/task');
    await expect(foreignPage.locator('#taskTable tbody tr').filter({ hasText: updatedTitle })).toHaveCount(0);
    const deniedResponse = await foreignPage.goto(`/teacher/task?taskId=${fixture.taskId}`);
    expect(deniedResponse?.status()).toBe(403);

    await login(
      noPermissionPage,
      '/teacher/account/login',
      fixture.noTaskManagementLoginId,
      password,
      '/teacher/prompt');
    const featureDeniedResponse = await noPermissionPage.goto(`/teacher/task?taskId=${fixture.taskId}`);
    expect(featureDeniedResponse?.status()).toBe(403);
  } finally {
    await Promise.all([
      peerContext.close(),
      ownerContext.close(),
      foreignContext.close(),
      noPermissionContext.close()
    ]);
  }
});
