const { test, expect } = require('@playwright/test');
const { login, requiredEnv } = require('./helpers');

test('overlapping saves from two editor contexts persist one winner and reject the stale write', async ({ browser }) => {
  test.setTimeout(60_000);
  const studentLogin = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  const assignmentId = requiredEnv('PPE_P20_ASSIGNMENT_ID');
  const firstContext = await browser.newContext();
  const secondContext = await browser.newContext();
  const firstPage = await firstContext.newPage();
  const secondPage = await secondContext.newPage();
  const interceptedRoutes = [];
  let releaseRequests;
  const bothRequestsReady = new Promise(resolve => {
    releaseRequests = resolve;
  });

  try {
    await login(firstPage, '/student/account/login', studentLogin, studentPassword, '/student/home');
    await login(secondPage, '/student/account/login', studentLogin, studentPassword, '/student/home');
    const editorPath = `/student/editor?assignmentId=${encodeURIComponent(assignmentId)}`;
    const firstEditorResponse = await firstPage.goto(editorPath);
    const secondEditorResponse = await secondPage.goto(editorPath);
    expect(firstEditorResponse.status(), await firstEditorResponse.text()).toBe(200);
    expect(secondEditorResponse.status(), await secondEditorResponse.text()).toBe(200);

    const firstEditor = firstPage.locator('#studentEditorPage');
    const secondEditor = secondPage.locator('#studentEditorPage');
    await expect(firstEditor).toHaveAttribute('data-editable', 'true');
    await expect(secondEditor).toHaveAttribute('data-editable', 'true');
    const baseVersion = await firstEditor.getAttribute('data-draft-updated-at');
    expect(await secondEditor.getAttribute('data-draft-updated-at')).toBe(baseVersion);

    const saveCodes = [
      `print("phase20-concurrent-a-${Date.now()}")`,
      `print("phase20-concurrent-b-${Date.now()}")`
    ];
    const pages = [firstPage, secondPage];
    await Promise.all(pages.map(page => page.route('**/student/editor/draft', async route => {
      interceptedRoutes.push(route);
      if (interceptedRoutes.length === 2) {
        const readyRoutes = interceptedRoutes.splice(0);
        releaseRequests();
        await Promise.all(readyRoutes.map(readyRoute => readyRoute.continue()));
      }
      await bothRequestsReady;
    })));

    const launchAt = Date.now() + 500;
    const saves = await Promise.all(pages.map((page, index) => page.evaluate(async ({
      assignmentId: currentAssignmentId,
      code,
      launchAt: scheduledAt
    }) => {
      await new Promise(resolve => setTimeout(resolve, Math.max(0, scheduledAt - Date.now())));
      const startedAt = Date.now();
      const response = await fetch('/student/editor/draft', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
        body: new URLSearchParams({
          assignmentId: currentAssignmentId,
          code,
          csrfToken: document.querySelector('#csrfToken').value,
          draftUpdatedAt: document.querySelector('#studentEditorPage').dataset.draftUpdatedAt,
          eventType: 'manual_save'
        })
      });
      return {
        status: response.status,
        body: await response.json(),
        startedAt,
        completedAt: Date.now(),
        code
      };
    }, { assignmentId, code: saveCodes[index], launchAt })));

    expect(Math.abs(saves[0].startedAt - saves[1].startedAt)).toBeLessThanOrEqual(1_000);
    expect(Math.max(saves[0].startedAt, saves[1].startedAt))
      .toBeLessThan(Math.min(saves[0].completedAt, saves[1].completedAt));
    expect(saves.map(save => save.status).sort()).toEqual([200, 409]);

    const winner = saves.find(save => save.status === 200);
    const rejected = saves.find(save => save.status === 409);
    expect(winner.body.status).toBe('saved');
    expect(rejected.body.errorCode).toBe('draft_conflict');
    expect(rejected.body.message).toMatch(/他の画面で下書きが更新されています/);

    await Promise.all([firstPage.reload(), secondPage.reload()]);
    for (const page of pages) {
      await expect(page.locator('#codeEditor')).toHaveValue(winner.code);
    }
  } finally {
    await firstContext.close();
    await secondContext.close();
  }
});
