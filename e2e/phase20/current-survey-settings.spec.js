const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

test('current active survey settings render and persist for a synthetic student', async ({ browser, page }) => {
  const assignmentId = requiredEnv('PPE_P20_SURVEY_ASSIGNMENT_ID');
  const evaluationId = requiredEnv('PPE_P20_SURVEY_EVALUATION_ID');
  const surveyId = requiredEnv('PPE_P20_SURVEY_ID');
  const surveyPath = `/student/survey?assignmentId=${assignmentId}&evaluationId=${evaluationId}&surveyId=${surveyId}`;
  const loginId = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const password = requiredEnv('PPE_P20_STUDENT_PASSWORD');

  const unauthenticated = await page.goto(surveyPath);
  expect(unauthenticated.status()).toBe(200);
  await expect(page.getByRole('heading', { name: '生徒ログイン', level: 1 })).toBeVisible();
  await assertRejectedLogin(page, '/student/account/login', loginId, 'invalid-synthetic-password');
  await login(page, '/student/account/login', loginId, password, '/student/home');

  await page.goto(surveyPath);
  await expect(page.getByRole('heading', { name: 'アンケート', level: 1 })).toBeVisible();
  await expect(page.locator('.question-step')).toHaveCount(4);
  const answers = page.locator('#surveyAnswers');
  for (const prompt of [
    '思考力等の評価は妥当でしたか',
    '自分の思考力等の得点',
    '学習態度の評価は妥当でしたか',
    '自分の学習態度の得点',
    '取り組み過程の記録への抵抗感',
    'システムの使いやすさ'
  ]) {
    await expect(answers).toContainText(prompt);
  }
  for (const label of [
    'とても感じる',
    'やや感じる',
    'どちらともいえない',
    'あまり感じない',
    '全く感じない',
    'とても使いにくい',
    'やや使いにくい',
    'やや使いやすい',
    'とても使いやすい'
  ]) {
    await expect(answers).toContainText(label);
  }

  const steps = page.locator('.question-step');
  for (let index = 0; index < await steps.count(); index += 1) {
    const step = steps.nth(index);
    const questions = step.locator('article.survey-question');
    for (let questionIndex = 0; questionIndex < await questions.count(); questionIndex += 1) {
      await questions.nth(questionIndex).locator('input[type="radio"][value="4"]').check();
    }
    if (index < await steps.count() - 1) {
      await page.getByRole('button', { name: '次の設問へ' }).click();
    }
  }

  await page.getByRole('button', { name: '下書きを保存する' }).click();
  await expect(page.getByText('下書きを保存しました')).toBeVisible();
  await expect(page.locator('#surveyAnswers input[type="radio"][value="4"]:checked')).toHaveCount(6);

  for (let index = 0; index < 3; index += 1) {
    await page.getByRole('button', { name: '次の設問へ' }).click();
  }
  const submit = page.getByRole('button', { name: 'アンケートを提出' });
  await expect(submit).toBeVisible();
  await submit.click();
  await page.locator('[data-feedback-confirm]').click();
  await expect(page).toHaveURL(/\/student\/home$/);

  await page.goto(surveyPath);
  await expect(page.locator('.survey-form')).toHaveAttribute('data-read-only', 'true');
  await expect(page.locator('#surveyAnswers')).toHaveAttribute('disabled', '');
  await expect(page.locator('#surveyAnswers input[type="radio"]').first()).toBeDisabled();
  await expect(page.locator('#editSurveyButton')).toBeVisible();
  await expect(page.locator('#surveyAnswers input[type="radio"][value="4"]:checked')).toHaveCount(6);

  const otherContext = await browser.newContext({ baseURL: requiredEnv('PPE_P20_BASE_URL') });
  try {
    const otherStudent = await otherContext.newPage();
    await login(otherStudent, '/student/account/login', 'review_withdrawn', password, '/student/home');
    const denied = await otherStudent.goto(surveyPath);
    expect(denied.status()).toBe(403);
  } finally {
    await otherContext.close();
  }
});

test('teacher task list and hint preview remain legible on desktop and mobile widths', async ({ browser }) => {
  const taskId = requiredEnv('PPE_P20_SURVEY_TASK_ID');
  const teacherContext = await browser.newContext({ baseURL: requiredEnv('PPE_P20_TEACHER_BASE_URL') });
  const teacherPage = await teacherContext.newPage();
  try {
    await login(
      teacherPage,
      '/teacher/account/login',
      requiredEnv('PPE_P20_TEACHER_LOGIN'),
      requiredEnv('PPE_P20_TEACHER_PASSWORD'),
      '/teacher/task'
    );

    const taskRow = teacherPage.locator('#taskTable tbody tr').filter({ hasText: 'Synthetic Review Task' });
    await expect(taskRow).toHaveCount(1);
    await expect(taskRow.locator('.prompt-status-cell')).toHaveText('設定済み');
    await expect(taskRow.locator('a[href^="/teacher/prompt"]')).toBeVisible();

    await teacherPage.setViewportSize({ width: 375, height: 812 });
    let dimensions = await teacherPage.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth
    }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);

    await teacherPage.goto(`/teacher/task?taskId=${taskId}`);
    await expect(teacherPage.locator('#taskNameInput')).toHaveValue('Synthetic Review Task');
    await expect(teacherPage.locator('#hintList .hint-row')).toHaveCount(1);
    await expect(teacherPage.locator('#hintList [name="hintTitles"]')).toHaveValue('MiXeD Hint');
    await expect(teacherPage.locator('#previewHintCards .hint-title-label')).toHaveText('MiXeD Hint');
    const previewEditor = teacherPage.locator('#previewHintCards .CodeMirror');
    await expect(previewEditor).toHaveCount(1);
    await expect(previewEditor).toBeVisible();
    await expect(teacherPage.locator('#hintList .CodeMirror-linenumbers')).toHaveCount(0);
    await expect(teacherPage.locator('#previewHintCards .CodeMirror-linenumbers')).toHaveCount(0);
    await expect(teacherPage.locator('#previewHintCards .CodeMirror-code')).toContainText('print');

    dimensions = await teacherPage.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth
    }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
    await teacherPage.setViewportSize({ width: 1280, height: 900 });
    dimensions = await teacherPage.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth
    }));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
  } finally {
    await teacherContext.close();
  }
});
