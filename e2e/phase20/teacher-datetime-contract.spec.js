const { test, expect } = require('@playwright/test');
const { login, requiredEnv } = require('./helpers');

test('teacher task schedule inputs remain Japan time after saving and reloading', async ({ page }) => {
  const password = requiredEnv('PPE_P20_PROMPT_PASSWORD');
  await login(page, '/teacher/account/login', 'teacher-demo', password, '/teacher/task');

  const taskRow = page.locator('#taskTable tbody tr').filter({ hasText: 'Browser publication acceptance' });
  await expect(taskRow).toHaveCount(1);
  const taskUrl = await taskRow.getByRole('link', { name: '編集', exact: true }).getAttribute('href');
  await page.goto(taskUrl);

  const schedule = page.locator('#classScheduleList .class-schedule-row').first();
  await expect(schedule).toBeVisible();
  const publishAt = schedule.locator('.class-schedule-publish');
  const dueAt = schedule.locator('.class-schedule-deadline');
  await publishAt.fill('2030-05-01T09:15');
  await dueAt.fill('2030-05-01T12:30');
  await page.locator('#saveDraftButton').click();
  await expect(page.locator('#taskSaveNotice')).toBeVisible();

  await page.goto(taskUrl);
  const reloadedSchedule = page.locator('#classScheduleList .class-schedule-row').first();
  await expect(reloadedSchedule.locator('.class-schedule-publish')).toHaveValue('2030-05-01T09:15');
  await expect(reloadedSchedule.locator('.class-schedule-deadline')).toHaveValue('2030-05-01T12:30');
});
