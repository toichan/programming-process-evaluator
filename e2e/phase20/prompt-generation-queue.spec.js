const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('queued prompt generations persist production-shaped synthetic examples across navigation', async ({ page }) => {
  test.setTimeout(90_000);
  const password = requiredEnv('PPE_P20_PROMPT_PASSWORD');
  await assertRejectedLogin(page, '/teacher/account/login', 'teacher-demo', 'invalid-synthetic-password');
  await login(page, '/teacher/account/login', 'teacher-demo', password, '/teacher/task');
  await page.goto('/teacher/prompt');
  const taskOption = page.locator('#taskSelect option').filter({ hasText: 'Browser publication acceptance' });
  const taskId = await taskOption.getAttribute('value');
  const timestampTaskId = requiredEnv('PPE_P20_PROMPT_TASK_ID');
  await page.goto(`/teacher/prompt?taskId=${encodeURIComponent(timestampTaskId)}`
    + `&promptVersionId=${encodeURIComponent(requiredEnv('PPE_P20_PROMPT_VERSION_ID'))}`
    + `&previewCode=${encodeURIComponent(requiredEnv('PPE_P20_REEVALUATION_PREVIEW_CODE'))}`);
  await expect(page.locator('body')).toContainText('有効期限: 2026-10-18T15:00');
  await expect(page.locator('#reevaluationHistoryTable')).toContainText('2026-10-16T16:00');
  await page.goto('/teacher/prompt');
  await page.locator('#taskSelect').selectOption(taskId);
  await page.getByRole('button', { name: '最新版で新しい下書き' }).click();
  await expect(page.locator('#evaluationPromptInput')).not.toHaveAttribute('readonly');
  await page.locator('#modelSelect').selectOption('gemini-3.1-pro-preview');
  const promptEditor = page.locator('#promptDraftForm .CodeMirror');
  await promptEditor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText('Synthetic queue acceptance: use only the supplied synthetic evidence.');
  const statusResponses = [];
  page.on('response', response => {
    if (new URL(response.url()).searchParams.get('action') === 'generationStatus') {
      statusResponses.push(response);
    }
  });

  await page.locator('button[value="generateFluctuations"]').click();
  await page.locator('#ppe-feedback-modal [data-feedback-confirm]').click();
  await expect(page.locator('#fluctuationProgress')).toBeVisible();
  await expect.poll(() => statusResponses.length).toBeGreaterThan(0);
  expect((await statusResponses[0].json()).fluctuationStatus).toBe('in_progress');
  const versionUrl = page.url();
  await page.goto('/teacher/task');
  await page.goto(versionUrl);
  await expect(page.locator('#fluctuationStatus')).toContainText('生成済み', { timeout: 20_000 });
  await expect(page.locator('.fluctuation-title')).toHaveText('合成の境界値');
  await expect(promptEditor).toContainText('Synthetic queue acceptance');
  await page.locator('[name="resolutionStatuses"]').selectOption('resolved');
  await page.locator('[name="teacherResolutions"]').fill('課題仕様の境界値だけで判断する。');
  await page.locator('button[value="saveResolutions"]').click();
  await expect(page.locator('[name="resolutionStatuses"]')).toHaveValue('resolved');
  await expect(page.locator('button[value="generateEvaluationExamples"]')).toBeEnabled();
  await page.locator('button[value="generateEvaluationExamples"]').click();
  await page.locator('#ppe-feedback-modal [data-feedback-confirm]').click();
  await expect(page.locator('#evaluationExamplesStatus')).toContainText('生成中');
  await page.goto('/teacher/task');
  await page.goto(versionUrl);
  await expect(page.locator('#evaluationExamplesStatus')).toContainText('生成済み', { timeout: 20_000 });
  await expect(page.locator('.evaluation-example-item')).toHaveCount(2);
  const firstExample = page.locator('.evaluation-example-item').first();
  await firstExample.getByText('評価例の詳細', { exact: true }).click();
  await expect(firstExample).toContainText('本番形式の合成コード編集ログ');
  await firstExample.getByText('ログ 2', { exact: true }).click();
  await expect(firstExample).toContainText('"standard_input": "synthetic input"');
  await expect(firstExample).toContainText('"standard_output": "synthetic output"');
  await firstExample.getByText('本番形式の評価結果JSONをすべて表示', { exact: true }).click();
  for (const key of ['scores', 'reasons', 'process_analysis', 'confidence', 'evidence_refs', 'warnings']) {
    await expect(firstExample).toContainText(`"${key}"`);
  }
  await page.locator('button[value="saveEvaluationExamples"]').click();
  await page.goto('/teacher/task');
  await page.goto(versionUrl);
  await expect(page.locator('.evaluation-example-item')).toHaveCount(2);
  await expect(page.locator('.evaluation-example-item .badge').first()).toHaveText('active');
  await expect(page.locator('.fluctuation-title')).toHaveText('合成の境界値');
});
